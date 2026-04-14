package net.mekomsolutions.db.importer;

import org.springframework.batch.core.annotation.AfterJob;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * This {@code JobListener} is responsible for shutting down the {@link ThreadPoolTaskExecutor} used
 * during the batch job's processing after the job has finished, which effectively shuts down the
 * application.
 */
@Slf4j
@Component
public class JobListener {
	
	private ThreadPoolTaskExecutor executor;
	
	public JobListener(@Qualifier("processorExecutor") ThreadPoolTaskExecutor executor) {
		this.executor = executor;
	}
	
	@AfterJob
	public void afterJob() throws Exception {
		log.info("Shutting down import executor");
		//Shutting down the executor effectively shuts down the application
		executor.shutdown();
	}
	
}
