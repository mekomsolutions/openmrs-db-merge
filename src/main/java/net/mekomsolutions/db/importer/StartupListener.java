package net.mekomsolutions.db.importer;

import java.time.LocalDateTime;

import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.job.SimpleJob;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StartupListener {
	
	private JobLauncher jobLauncher;
	
	private SimpleJob job;
	
	public StartupListener(JobLauncher jobLauncher, SimpleJob job) {
		this.jobLauncher = jobLauncher;
		this.job = job;
	}
	
	@EventListener(classes = { ContextRefreshedEvent.class })
	public void contextRefreshed() throws Exception {
		if (job.getStepNames().isEmpty()) {
			log.info("No tables found containing data to import.");
			return;
		}
		
		log.info("Starting the job to import {} tables", job.getStepNames());
		JobParametersBuilder builder = new JobParametersBuilder().addLocalDateTime("timestamp", LocalDateTime.now());
		jobLauncher.run(job, builder.toJobParameters());
	}
	
}
