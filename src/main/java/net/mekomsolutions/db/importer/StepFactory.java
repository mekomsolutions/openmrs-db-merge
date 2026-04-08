package net.mekomsolutions.db.importer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.springframework.batch.core.Step;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.SimpleStepBuilder;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.integration.async.AsyncItemProcessor;
import org.springframework.batch.integration.async.AsyncItemWriter;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.Order;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StepFactory {
	
	@Value("${tables.exclude.file.path}")
	private File excludeTablesFile;
	
	@Value("${batch.read.size:1000}")
	private Integer batchReadSize;
	
	@Value("${batch.write.size:50}")
	private Integer batchWriteSize;
	
	private JobExplorer jobExplorer;
	
	private JobRepository jobRepository;
	
	private PlatformTransactionManager batchTxManager;
	
	private PlatformTransactionManager sinkTxManager;
	
	private DataSource sourceDataSource;
	
	private DataSource sinkDataSource;
	
	private DataSource batchDataSource;
	
	final private ColumnMapRowMapper ROW_MAPPER = new ColumnMapRowMapper();
	
	public StepFactory(JobRepository jobRepository, JobExplorer jobExplorer, PlatformTransactionManager batchTxManager,
	    PlatformTransactionManager sinkTxManager, DataSource sourceDataSource, DataSource sinkDataSource,
	    DataSource batchDataSource) {
		this.jobRepository = jobRepository;
		this.jobExplorer = jobExplorer;
		this.batchTxManager = batchTxManager;
		this.sinkTxManager = sinkTxManager;
		this.sourceDataSource = sourceDataSource;
		this.sinkDataSource = sinkDataSource;
		this.batchDataSource = batchDataSource;
	}
	
	protected Step createTableStep(String tableName, MetadataExtractor metadataExtractor,
	                               ArrayPreparedStatementParamSetter prepStmtParamSetter, SourceDbHelper sourceDbHelper,
	                               SinkDbHelper sinkDbHelper, ImportDbHelper importDbHelper) {
		
		Table table = metadataExtractor.getTable(tableName);
		ItemReader<Map<String, Object>> reader = createReader(tableName, table.primaryKeys(), sourceDataSource, true);
		ItemProcessor<Map<String, Object>, Future<Row>> processor = createProcessor(table, metadataExtractor, sourceDbHelper,
		    sinkDbHelper, importDbHelper);
		final String writeSql = ImportUtils.getWriteSql(table);
		ItemWriter<Future<Row>> writer = createWriter(writeSql, prepStmtParamSetter);
		SimpleStepBuilder<Map<String, Object>, Future<Row>> builder = new StepBuilder(tableName, jobRepository)
		        .chunk(batchWriteSize, sinkTxManager);
		return builder.reader(reader).processor(processor).writer(writer).listener(new MaxRowIdRecorder()).build();
	}
	
	protected ItemReader<Map<String, Object>> createReader(String tableName, List<String> primaryKeys, DataSource dataSource,
	                                                       boolean resumable) {
		
		Map<String, Order> sortKeys = primaryKeys.stream()
		        .collect(Collectors.toMap(Function.identity(), s -> Order.ASCENDING));
		JdbcPagingItemReaderBuilder<Map<String, Object>> builder = new JdbcPagingItemReaderBuilder();
		builder.name(tableName).dataSource(dataSource).selectClause("SELECT *").fromClause("FROM " + tableName)
		        .sortKeys(sortKeys).pageSize(batchReadSize).rowMapper(ROW_MAPPER);
		if (resumable) {
			final Object maxProcessedRowId = ImportUtils.getMaxRowId(jobExplorer, jobRepository, tableName);
			if (maxProcessedRowId != null) {
				log.info("Importing rows from {} table with {} > {}", tableName, primaryKeys.get(0), maxProcessedRowId);
				builder.whereClause(primaryKeys.get(0) + " > " + maxProcessedRowId);
			}
		}
		
		JdbcPagingItemReader<Map<String, Object>> reader = builder.build();
		try {
			reader.afterPropertiesSet();
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
		
		return reader;
	}
	
	protected ItemProcessor<Map<String, Object>, Future<Row>> createProcessor(Table table,
	                                                                          MetadataExtractor metadataExtractor,
	                                                                          SourceDbHelper sourceDbHelper,
	                                                                          SinkDbHelper sinkDbHelper,
	                                                                          ImportDbHelper importDbHelper) {
		
		ItemProcessor<Map<String, Object>, Row> processor = new RowItemProcessor(table, metadataExtractor, sourceDbHelper,
		        sinkDbHelper, importDbHelper);
		AsyncItemProcessor asyncProcessor = new AsyncItemProcessor();
		asyncProcessor.setDelegate(processor);
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		//TODO Make configurable
		executor.setCorePoolSize(32);
		executor.setMaxPoolSize(32);
		executor.initialize();
		asyncProcessor.setTaskExecutor(executor);
		try {
			asyncProcessor.afterPropertiesSet();
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
		
		return asyncProcessor;
	}
	
	protected ItemWriter<Future<Row>> createWriter(String sql, ArrayPreparedStatementParamSetter prepStmtParamSetter) {
		//TODO Disable assertUpdates so that we handle failures somewhere else
		JdbcBatchItemWriter<Row> writer = new JdbcBatchItemWriterBuilder().dataSource(sinkDataSource).sql(sql)
		        .itemPreparedStatementSetter(prepStmtParamSetter).build();
		
		try {
			writer.afterPropertiesSet();
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
		
		AsyncItemWriter<Row> asyncWriter = new AsyncItemWriter();
		asyncWriter.setDelegate(writer);
		try {
			asyncWriter.afterPropertiesSet();
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
		
		return asyncWriter;
	}
	
	public List<Step> getSteps(MetadataExtractor metadataExtractor, ArrayPreparedStatementParamSetter prepStmtParamSetter,
	                           SourceDbHelper sourceDbHelper, SinkDbHelper sinkDbHelper, ImportDbHelper importDbHelper)
	    throws IOException {
		
		log.info("Retrieving exclude tables defined in file {}", excludeTablesFile);
		
		BufferedReader br = new BufferedReader(new FileReader(excludeTablesFile));
		String line;
		Set<String> excludes = new HashSet<>();
		while ((line = br.readLine()) != null) {
			excludes.add(line.trim().toLowerCase(Locale.ENGLISH));
		}
		
		List<String> tables = new ArrayList(metadataExtractor.getTableNames());
		List<Step> steps = new ArrayList<>(tables.size());
		//Skip excluded and empty tables
		tables.stream().filter(t -> !excludes.contains(t) && !sourceDbHelper.isTableEmpty(t)).forEach(t -> steps.add(
		    createTableStep(t, metadataExtractor, prepStmtParamSetter, sourceDbHelper, sinkDbHelper, importDbHelper)));
		
		log.info("Importing {} tables", steps.size());
		
		return steps;
	}
	
}
