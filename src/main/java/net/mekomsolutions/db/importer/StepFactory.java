package net.mekomsolutions.db.importer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StepFactory {
	
	@Value("${tables.file.path}")
	private File tablesFile;
	
	@Value("${batch.read.size:1000}")
	private Integer batchReadSize;
	
	@Value("${batch.write.size:50}")
	private Integer batchWriteSize;
	
	private JobExplorer jobExplorer;
	
	private JobRepository jobRepository;
	
	private PlatformTransactionManager txManager;
	
	private DataSource sourceDataSource;
	
	private DataSource sinkDataSource;
	
	final private ColumnMapRowMapper ROW_MAPPER = new ColumnMapRowMapper();
	
	public StepFactory(JobRepository jobRepository, JobExplorer jobExplorer, PlatformTransactionManager txManager,
	    @Qualifier("sourceDataSource") DataSource sourceDataSource, @Qualifier("sinkDataSource") DataSource sinkDataSource) {
		this.jobRepository = jobRepository;
		this.jobExplorer = jobExplorer;
		this.txManager = txManager;
		this.sourceDataSource = sourceDataSource;
		this.sinkDataSource = sinkDataSource;
	}
	
	protected Step createTableStep(String tableName, MetadataExtractor metadataExtractor,
	                               ArrayPreparedStatementParamSetter prepStmtParamSetter, SourceDbHelper sourceDbHelper,
	                               SinkDbHelper sinkDbHelper) {
		
		Table table = metadataExtractor.getTable(tableName);
		ItemReader<Map<String, Object>> reader = createReader(table);
		ItemProcessor<Map<String, Object>, Future<Row>> processor = createProcessor(table, metadataExtractor, sourceDbHelper,
		    sinkDbHelper);
		ItemWriter<Future<Row>> writer = createWriter(table, prepStmtParamSetter);
		SimpleStepBuilder<Map<String, Object>, Future<Row>> builder = new StepBuilder(tableName, jobRepository)
		        .chunk(batchWriteSize, txManager);
		return builder.reader(reader).processor(processor).writer(writer).listener(new MaxRowIdRecorder()).build();
	}
	
	protected ItemReader<Map<String, Object>> createReader(Table table) {
		Map<String, Order> sortKeys = table.primaryKeys().stream()
		        .collect(Collectors.toMap(Function.identity(), s -> Order.ASCENDING));
		String name = table.name();
		final JdbcPagingItemReaderBuilder<Map<String, Object>> builder = new JdbcPagingItemReaderBuilder();
		builder.name(name).dataSource(sourceDataSource).selectClause("SELECT *").fromClause("FROM " + name)
		        .sortKeys(sortKeys).pageSize(batchReadSize).rowMapper(ROW_MAPPER);
		final Object maxProcessedRowId = ImportUtils.getMaxRowId(jobExplorer, jobRepository, name);
		if (maxProcessedRowId != null) {
			log.info("Importing rows from {} table with {} > {}", name, table.primaryKeys().get(0), maxProcessedRowId);
			builder.whereClause(table.primaryKeys().get(0) + " > " + maxProcessedRowId);
		}
		
		final JdbcPagingItemReader<Map<String, Object>> reader = builder.build();
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
	                                                                          SinkDbHelper sinkDbHelper) {
		ItemProcessor<Map<String, Object>, Row> processor = new RowItemProcessor(table, metadataExtractor, sourceDbHelper,
		        sinkDbHelper);
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
	
	protected ItemWriter<Future<Row>> createWriter(Table table, ArrayPreparedStatementParamSetter prepStmtParamSetter) {
		//TODO Disable assertUpdates so that we handle failures somewhere else
		final String sql = ImportUtils.getWriteSql(table);
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
	                           SourceDbHelper sourceDbHelper, SinkDbHelper sinkDbHelper)
	    throws IOException {
		
		log.info("Importing sync tables defined in file {}", tablesFile);
		
		BufferedReader br = new BufferedReader(new FileReader(tablesFile));
		String line;
		List<Step> steps = new ArrayList<>();
		while ((line = br.readLine()) != null) {
			final Step step = createTableStep(line.trim().toLowerCase(Locale.ENGLISH), metadataExtractor,
			    prepStmtParamSetter, sourceDbHelper, sinkDbHelper);
			steps.add(step);
		}
		
		return steps;
	}
	
}
