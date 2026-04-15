package net.mekomsolutions.db.importer.config;

import java.util.List;

import javax.sql.DataSource;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.job.SimpleJob;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.builder.SimpleJobBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import net.mekomsolutions.db.importer.Constants;
import net.mekomsolutions.db.importer.ImportUtils;
import net.mekomsolutions.db.importer.JobListener;
import net.mekomsolutions.db.importer.MetadataExtractor;
import net.mekomsolutions.db.importer.RetryRemover;
import net.mekomsolutions.db.importer.RetryWriter;
import net.mekomsolutions.db.importer.RowPreparedStatementParamSetter;
import net.mekomsolutions.db.importer.RowProcessorHelper;
import net.mekomsolutions.db.importer.SourceDbHelper;
import net.mekomsolutions.db.importer.StepFactory;

@EnableBatchProcessing(dataSourceRef = "mgtDataSource", transactionManagerRef = "mgtTxManager")
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
	                     RowPreparedStatementParamSetter prepStatementParamSetter, SourceDbHelper sourceDbHelper,
	                     RowProcessorHelper processorHelper, RetryWriter retryWriter, RetryRemover retryRemover,
	                     @Qualifier("processorExecutor") TaskExecutor executor, JobListener listener)
	    throws Exception {
		
		JobBuilder jobBuilder = new JobBuilder(Constants.JOB_NAME, jobRepository).preventRestart();
		final List<Step> steps = stepFactory.getSteps(metadataExtractor, prepStatementParamSetter, sourceDbHelper,
		    processorHelper, retryWriter, retryRemover, executor);
		
		SimpleJobBuilder builder = null;
		for (Step step : steps) {
			if (builder == null) {
				builder = jobBuilder.start(step);
				continue;
			}
			
			builder = builder.next(step);
		}
		
		if (!steps.isEmpty()) {
			return builder.listener(listener).build();
		}
		
		//There is nothing to import
		SimpleJob emptyJob = new SimpleJob();
		emptyJob.setJobRepository(jobRepository);
		return emptyJob;
	}
	
	@Bean
	public ThreadPoolTaskExecutor processorExecutor(@Value("${thread.count}") Integer threadCount) {
		if (threadCount == null) {
			threadCount = ImportUtils.getDefaultThreadCount();
		}
		
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(threadCount);
		executor.setMaxPoolSize(threadCount);
		executor.initialize();
		return executor;
	}
	
	@Bean
	public StepFactory stepFactory(JobRepository jobRepo, JobExplorer jobExplorer,
	                               @Qualifier("sinkTxManager") PlatformTransactionManager sinkTxManager,
	                               @Qualifier("sourceDataSource") DataSource sourceDataSource,
	                               @Qualifier("sinkDataSource") DataSource sinkDataSource,
	                               @Qualifier("mgtDataSource") DataSource mgtDataSource) {
		return new StepFactory(jobRepo, jobExplorer, sinkTxManager, sourceDataSource, sinkDataSource, mgtDataSource);
	}
	
}
