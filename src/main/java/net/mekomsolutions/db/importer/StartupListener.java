package net.mekomsolutions.db.importer;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StartupListener {
	
	private JobLauncher jobLauncher;
	
	private Job job;
	
	public StartupListener(JobLauncher jobLauncher, Job job) {
		this.jobLauncher = jobLauncher;
		this.job = job;
	}
	
	@EventListener(classes = { ContextRefreshedEvent.class })
	public void contextRefreshed() throws Exception {
		log.info("Starting the import job to sync tables");
		jobLauncher.run(job, new JobParameters());
	}
	
}
