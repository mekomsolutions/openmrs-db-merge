package net.mekomsolutions.db.importer;

import static net.mekomsolutions.db.importer.Constants.FAILED_ITEM_TABLE;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
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
import org.springframework.batch.item.database.ItemPreparedStatementSetter;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.Order;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.transaction.PlatformTransactionManager;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StepFactory {
	
	private final ColumnMapRowMapper ROW_MAPPER = new ColumnMapRowMapper();
	
	@Value("${tables.exclude.file.path}")
	private File excludeTablesFile;
	
	@Value("${batch.read.size}")
	private Integer batchReadSize;
	
	@Value("${batch.write.size}")
	private Integer batchWriteSize;
	
	@Value("${" + Constants.PROP_RETRY_FAILED_ITEMS + ":false}")
	private boolean retry;
	
	private JobExplorer jobExplorer;
	
	private JobRepository jobRepository;
	
	private PlatformTransactionManager sinkTxManager;
	
	private DataSource sourceDataSource;
	
	private DataSource sinkDataSource;
	
	private DataSource batchDataSource;
	
	private Map<String, JdbcBatchItemWriter<Row>> tableWriterMap;
	
	public StepFactory(JobRepository jobRepository, JobExplorer jobExplorer, PlatformTransactionManager sinkTxManager,
	    DataSource sourceDataSource, DataSource sinkDataSource, DataSource batchDataSource) {
		this.jobRepository = jobRepository;
		this.jobExplorer = jobExplorer;
		this.sinkTxManager = sinkTxManager;
		this.sourceDataSource = sourceDataSource;
		this.sinkDataSource = sinkDataSource;
		this.batchDataSource = batchDataSource;
	}
	
	protected Step createTableStep(String tableName, MetadataExtractor metadataExtractor, RowProcessorHelper processorHelper,
	                               TaskExecutor executor) {
		
		final Table table = metadataExtractor.getTable(tableName);
		ItemReader<Map<String, Object>> reader = createReader(tableName, table.primaryKeys(), sourceDataSource, true);
		ItemProcessor<Map<String, Object>, Row> rowProcessor = new RowItemProcessor(table, processorHelper);
		ItemProcessor<Map<String, Object>, Future<Row>> processor = createAsyncProcessor(rowProcessor, executor);
		ItemWriter<Future<Row>> writer = createRowWriter(getBatchWriter(tableName));
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
	
	protected JdbcBatchItemWriter<Row> getBatchWriter(String tableName) {
		return tableWriterMap.get(tableName);
	}
	
	private <T> ItemWriter<Future<T>> createRowWriter(JdbcBatchItemWriter<T> delegate) {
		try {
			delegate.afterPropertiesSet();
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
		
		return createAsyncWriter(delegate);
	}
	
	private <T> ItemWriter<Future<T>> createAsyncWriter(ItemWriter<T> delegate) {
		AsyncItemWriter<T> asyncWriter = new AsyncItemWriter();
		asyncWriter.setDelegate(delegate);
		try {
			asyncWriter.afterPropertiesSet();
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
		
		return asyncWriter;
	}
	
	public Step createRetryStep(SourceDbHelper sourceDbHelper, RowProcessorHelper processorHelper,
	                            MetadataExtractor metadataExtractor, RetryWriter retryWriter, RetryRemover retryRemover,
	                            TaskExecutor executor) {
		ItemReader<Map<String, Object>> reader = createReader(FAILED_ITEM_TABLE, List.of("id"), batchDataSource, false);
		ItemProcessor<Map<String, Object>, Retry> retryProcessor = new RetryItemProcessor(sourceDbHelper, processorHelper,
		        metadataExtractor);
		ItemProcessor<Map<String, Object>, Future<Retry>> processor = createAsyncProcessor(retryProcessor, executor);
		ItemWriter<Future<Retry>> writer = createAsyncWriter(retryWriter);
		//Note that we still use the sinkTxManager because the row being retried is written to the sink DB and the
		//retry deletion process happens outside this TX i.e. after the chunk is committed.
		SimpleStepBuilder<Map<String, Object>, Future<Retry>> builder = new StepBuilder(FAILED_ITEM_TABLE, jobRepository)
		        .chunk(batchWriteSize, sinkTxManager);
		return builder.reader(reader).processor(processor).writer(writer).listener(retryRemover).build();
	}
	
	public List<Step> getSteps(MetadataExtractor metadataExtractor, RowPreparedStatementParamSetter prepStmtParamSetter,
	                           SourceDbHelper sourceDbHelper, RowProcessorHelper processorHelper, RetryWriter retryWriter,
	                           RetryRemover retryRemover, TaskExecutor executor)
	    throws IOException {
		
		log.info("Retrieving exclude tables defined in file {}", excludeTablesFile);
		
		BufferedReader br = new BufferedReader(new FileReader(excludeTablesFile));
		String line;
		Set<String> excludes = new HashSet<>();
		while ((line = br.readLine()) != null) {
			excludes.add(line.trim().toLowerCase(Locale.ENGLISH));
		}
		
		//Skip excluded and empty tables
		List<String> importTables = metadataExtractor.getTableNames().stream()
		        .filter(t -> !excludes.contains(t) && !sourceDbHelper.isTableEmpty(t)).collect(Collectors.toList());
		log.info("Importing {} tables", importTables.size());
		if (tableWriterMap == null) {
			tableWriterMap = new HashMap<>(importTables.size());
		}
		
		importTables.forEach(t -> {
			tableWriterMap.computeIfAbsent(t, k -> {
				final String sql = ImportUtils.getWriteSql(metadataExtractor.getTable(k));
				return createBatchWriter(sql, prepStmtParamSetter);
			});
		});
		
		List<Step> steps = new ArrayList<>(importTables.size());
		importTables.stream().forEach(t -> {
			steps.add(createTableStep(t, metadataExtractor, processorHelper, executor));
		});
		
		if (retry) {
			Step step = createRetryStep(sourceDbHelper, processorHelper, metadataExtractor, retryWriter, retryRemover,
			    executor);
			steps.add(step);
		}
		
		return steps;
	}
	
	private <T> ItemProcessor<Map<String, Object>, Future<T>> createAsyncProcessor(ItemProcessor<Map<String, Object>, T> delegate,
	                                                                               TaskExecutor executor) {
		
		AsyncItemProcessor asyncProcessor = new AsyncItemProcessor();
		asyncProcessor.setDelegate(delegate);
		asyncProcessor.setTaskExecutor(executor);
		
		try {
			asyncProcessor.afterPropertiesSet();
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
		
		return asyncProcessor;
	}
	
	private JdbcBatchItemWriter<Row> createBatchWriter(String sql, ItemPreparedStatementSetter<?> prepStmtParamSetter) {
		//TODO Disable assertUpdates so that we handle failures somewhere else
		return new JdbcBatchItemWriterBuilder().dataSource(sinkDataSource).sql(sql)
		        .itemPreparedStatementSetter(prepStmtParamSetter).build();
	}
	
}
