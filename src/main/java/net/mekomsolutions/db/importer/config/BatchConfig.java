package net.mekomsolutions.db.importer.config;

import java.util.Map;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;

@EnableBatchProcessing(dataSourceRef = "batchDataSource", transactionManagerRef = "sourceTxManager")
public class BatchConfig {
	
	@Value("${batch.read.size:1000}")
	private Integer batchReadSize;
	
	@Value("${batch.write.size:50}")
	private Integer batchWriteSize;
	
	@Bean
	public JobLauncher jobLauncher(JobRepository jobRepository) throws Exception {
		TaskExecutorJobLauncher jobLauncher = new TaskExecutorJobLauncher();
		jobLauncher.setJobRepository(jobRepository);
		jobLauncher.afterPropertiesSet();
		//TODO Configured ThreadPoolTaskScheduler
		return jobLauncher;
	}
	
	@Bean
	public Job importJob(JobRepository jobRepository, Step importStep) {
		return new JobBuilder("importJob", jobRepository).start(importStep).build();
	}
	
	@Bean
	public Step importStep(JobRepository jobRepository, PlatformTransactionManager txManager) {
		return new StepBuilder("importStep", jobRepository).<Map<String, Object>, Object[]> chunk(batchWriteSize, txManager)
		        .build();
	}
	
}
