package net.mekomsolutions.db.importer.batch;

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
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import net.mekomsolutions.db.importer.Constants;
import net.mekomsolutions.db.importer.MergeUtils;
import net.mekomsolutions.db.importer.MetadataExtractor;
import net.mekomsolutions.db.importer.helpers.SourceDbHelper;

@EnableBatchProcessing(dataSourceRef = "mgtDataSource", transactionManagerRef = "mgtTxManager")
@ComponentScan(basePackageClasses = BatchConfig.class)
public class BatchConfig {
	
	@Value("${" + Constants.PROP_RETRY_FAILED_ITEMS + ":false}")
	private boolean retry;
	
	@Bean
	public JobLauncher jobLauncher(JobRepository jobRepository) throws Exception {
		TaskExecutorJobLauncher jobLauncher = new TaskExecutorJobLauncher();
		jobLauncher.setJobRepository(jobRepository);
		jobLauncher.afterPropertiesSet();
		return jobLauncher;
	}
	
	@Bean
	public MetadataExtractor sourceExtractor(@Qualifier("sourceJdbcTemplate") JdbcTemplate jdbcTemplate) {
		return new MetadataExtractor("source", jdbcTemplate, false);
	}
	
	@Bean
	public MetadataExtractor sinkExtractor(@Qualifier("sinkJdbcTemplate") JdbcTemplate jdbcTemplate) {
		return new MetadataExtractor("sink", jdbcTemplate, true);
	}
	
	@Bean
	public Job importJob(JobRepository jobRepository, StepFactory stepFactory,
	                     @Qualifier("sourceExtractor") MetadataExtractor sourceExtractor,
	                     @Qualifier("sinkExtractor") MetadataExtractor sinkExtractor,
	                     RowPreparedStatementParamSetter prepStatementParamSetter, SourceDbHelper sourceDbHelper,
	                     RowProcessorHelper processorHelper, RetryWriter retryWriter, RetryRemover retryRemover,
	                     @Qualifier("processorExecutor") TaskExecutor executor, JobListener jobListener,
	                     TableStepListener stepListener)
	    throws Exception {
		
		final List<String> tableNames = stepFactory.getTableNames(sourceExtractor, sinkExtractor, prepStatementParamSetter,
		    sourceDbHelper);
		if (tableNames.isEmpty() && !retry) {
			//There is nothing to import
			SimpleJob emptyJob = new SimpleJob();
			emptyJob.setJobRepository(jobRepository);
			return emptyJob;
		}
		
		JobBuilder jobBuilder = new JobBuilder(Constants.JOB_NAME, jobRepository).preventRestart();
		SimpleJobBuilder simpleJobBuilder = null;
		for (String tableName : tableNames) {
			String stepName = tableName;
			boolean isObs = tableName.equals("obs");
			String filterClause = null;
			if (isObs) {
				stepName = Constants.STEP_NAME_PARENT_OBS;
				filterClause = Constants.PARENT_OBS_CLAUSE;
			}
			
			Step step = stepFactory.createTableStep(stepName, tableName, null, filterClause, sourceExtractor,
			    processorHelper, executor, stepListener);
			if (simpleJobBuilder == null) {
				simpleJobBuilder = jobBuilder.start(step);
			} else {
				simpleJobBuilder = simpleJobBuilder.next(step);
			}
			
			if (isObs) {
				stepName = Constants.STEP_NAME_CHILDLESS_OBS;
				filterClause = Constants.CHILDLESS_OBS_CLAUSE;
				Step nextStep = stepFactory.createTableStep(stepName, tableName, Constants.TABLE_ALIAS, filterClause,
				    sourceExtractor, processorHelper, executor, stepListener);
				simpleJobBuilder = simpleJobBuilder.next(nextStep);
			}
		}
		
		if (retry) {
			Step step = stepFactory.createRetryStep(sourceDbHelper, processorHelper, sourceExtractor, retryWriter,
			    retryRemover, executor);
			simpleJobBuilder.next(step);
		}
		
		return simpleJobBuilder.listener(jobListener).build();
	}
	
	@Bean
	public ThreadPoolTaskExecutor processorExecutor(@Value("${thread.count}") Integer threadCount) {
		if (threadCount == null) {
			threadCount = MergeUtils.getDefaultThreadCount();
		}
		
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(threadCount);
		executor.setMaxPoolSize(threadCount);
		executor.initialize();
		return executor;
	}
	
	@Bean
	@DependsOn("springLiquibase")
	public StepFactory stepFactory(JobRepository jobRepo, JobExplorer jobExplorer,
	                               @Qualifier("sinkTxManager") PlatformTransactionManager sinkTxManager,
	                               @Qualifier("sourceDataSource") DataSource sourceDataSource,
	                               @Qualifier("sinkDataSource") DataSource sinkDataSource,
	                               @Qualifier("mgtDataSource") DataSource mgtDataSource) {
		return new StepFactory(jobRepo, jobExplorer, sinkTxManager, sourceDataSource, sinkDataSource, mgtDataSource);
	}
	
}
