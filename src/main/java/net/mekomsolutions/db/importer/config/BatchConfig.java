package net.mekomsolutions.db.importer.config;

import java.util.List;

import javax.sql.DataSource;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.builder.SimpleJobBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;

import net.mekomsolutions.db.importer.ArrayPreparedStatementParamSetter;
import net.mekomsolutions.db.importer.Constants;
import net.mekomsolutions.db.importer.MetadataExtractor;
import net.mekomsolutions.db.importer.SinkDbHelper;
import net.mekomsolutions.db.importer.SourceDbHelper;
import net.mekomsolutions.db.importer.StepFactory;

@EnableBatchProcessing(dataSourceRef = "batchDataSource", transactionManagerRef = "batchTxManager")
public class BatchConfig {
	
	@Bean
	public JobLauncher jobLauncher(JobRepository jobRepository) throws Exception {
		TaskExecutorJobLauncher jobLauncher = new TaskExecutorJobLauncher();
		jobLauncher.setJobRepository(jobRepository);
		jobLauncher.afterPropertiesSet();
		return jobLauncher;
	}
	
	@Bean
	public Job importJob(JobRepository jobRepository, StepFactory stepFactory, MetadataExtractor metadataExtractor,
	                     ArrayPreparedStatementParamSetter prepStatementParamSetter, SourceDbHelper sourceDbHelper,
	                     SinkDbHelper sinkDbHelper)
	    throws Exception {
		
		JobBuilder jobBuilder = new JobBuilder(Constants.JOB_NAME, jobRepository).preventRestart();
		List<Step> steps = stepFactory.getSteps(metadataExtractor, prepStatementParamSetter, sourceDbHelper, sinkDbHelper);
		SimpleJobBuilder builder = null;
		for (Step step : steps) {
			if (builder == null) {
				builder = jobBuilder.start(step);
				continue;
			}
			
			builder = builder.next(step);
		}
		
		return builder.build();
	}
	
	@Bean
	public StepFactory stepFactory(JobRepository jobRepository, JobExplorer jobExplorer,
	                               PlatformTransactionManager txManager,
	                               @Qualifier("sourceDataSource") DataSource sourceDataSource,
	                               @Qualifier("sinkDataSource") DataSource sinkDataSource) {
		return new StepFactory(jobRepository, jobExplorer, txManager, sourceDataSource, sinkDataSource);
	}
	
}
