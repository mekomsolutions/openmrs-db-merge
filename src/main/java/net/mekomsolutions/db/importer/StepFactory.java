package net.mekomsolutions.db.importer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.Order;
import org.springframework.batch.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.batch.item.function.ConsumerItemWriter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.ColumnMapRowMapper;
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
	
	private JobRepository jobRepository;
	
	private PlatformTransactionManager txManager;
	
	private DataSource sourceDataSource;
	
	private DataSource sinkDataSource;
	
	private ColumnMapRowMapper rowMapper = new ColumnMapRowMapper();
	
	public StepFactory(JobRepository jobRepository, PlatformTransactionManager txManager,
	    @Qualifier("sourceDataSource") DataSource sourceDataSource, @Qualifier("sinkDataSource") DataSource sinkDataSource) {
		this.jobRepository = jobRepository;
		this.txManager = txManager;
		this.sourceDataSource = sourceDataSource;
		this.sinkDataSource = sinkDataSource;
	}
	
	public Step createTableStep(String table) {
		ItemReader<Map<String, Object>> reader = createReader(table);
		ItemProcessor<Map<String, Object>, Object[]> processor = createProcessor(table);
		ItemWriter<Object[]> writer = createWriter(table);
		return new StepBuilder(table, jobRepository).<Map<String, Object>, Object[]> chunk(batchWriteSize, txManager)
		        .reader(reader).processor(processor).writer(writer).build();
	}
	
	public ItemReader<Map<String, Object>> createReader(String table) {
		//TODO Fetch primary key column from database metadata
		JdbcPagingItemReader<Map<String, Object>> reader = new JdbcPagingItemReaderBuilder<Map<String, Object>>()
		        .name(table + "_reader").dataSource(sourceDataSource).selectClause("SELECT *").fromClause("FROM " + table)
		        .sortKeys(Map.of(table + "_id", Order.ASCENDING)).pageSize(batchReadSize).rowMapper(rowMapper).build();
		
		try {
			reader.afterPropertiesSet();
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
		
		return reader;
	}
	
	public ItemProcessor<Map<String, Object>, Object[]> createProcessor(String table) {
		//TODO call processor.afterPropertiesSet();
		return new ItemProcessor<Map<String, Object>, Object[]>() {
			
			@Override
			public Object[] process(Map<String, Object> item) throws Exception {
				System.out.println("Processing: " + item);
				return item.values().toArray();
			}
		};
	}
	
	public ItemWriter<Object[]> createWriter(String table) {
		//TODO call writer.afterPropertiesSet();
		//return new JdbcBatchItemWriterBuilder<Object[]>().dataSource(sinkDataSource).build();
		return new ConsumerItemWriter<>(item -> System.out.println("Writing: " + Arrays.toString(item)));
	}
	
	public List<Step> getSteps() throws IOException {
		log.info("Importing sync tables in file {}", tablesFile);
		BufferedReader br = new BufferedReader(new FileReader(tablesFile));
		String line;
		List<Step> steps = new ArrayList<>();
		while ((line = br.readLine()) != null) {
			steps.add(createTableStep(line.trim()));
		}
		
		return steps;
	}
	
}
