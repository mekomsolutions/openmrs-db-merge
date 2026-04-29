package net.mekomsolutions.db.importer.config;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.job.SimpleJob;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.context.annotation.Bean;

import net.mekomsolutions.db.importer.StartupListener;

public class AppConfig {
	
	@Bean
	public StartupListener startupListener(JobLauncher jobLauncher, Job job) {
		return new StartupListener(jobLauncher, (SimpleJob) job);
	}
	
}
