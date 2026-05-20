package net.mekomsolutions.db.importer.batch;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.annotation.AfterJob;
import org.springframework.batch.core.annotation.BeforeJob;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import net.mekomsolutions.db.importer.Constants;
import net.mekomsolutions.db.importer.helpers.MgtDbHelper;
import net.mekomsolutions.db.importer.helpers.SinkDbHelper;

/**
 * This {@code JobListener} is responsible for shutting down the {@link ThreadPoolTaskExecutor} used
 * during the batch job's processing after the job has finished, which effectively shuts down the
 * application.
 */
@Slf4j
@Component
public class JobListener {
	
	private ThreadPoolTaskExecutor executor;
	
	protected MgtDbHelper mgtDbHelper;
	
	protected SinkDbHelper sinkDbHelper;
	
	protected OpenMrsMetadataMapper metadataMapper;
	
	public JobListener(@Qualifier("processorExecutor") ThreadPoolTaskExecutor executor, MgtDbHelper mgtDbHelper,
	    SinkDbHelper sinkDbHelper, OpenMrsMetadataMapper metadataMapper) {
		this.executor = executor;
		this.mgtDbHelper = mgtDbHelper;
		this.sinkDbHelper = sinkDbHelper;
		this.metadataMapper = metadataMapper;
	}
	
	@BeforeJob
	public void beforeJob() {
		log.info("Starting merge job");
		metadataMapper.initialize();
	}
	
	@AfterJob
	public void afterJob(JobExecution jobExecution) {
		if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
			if (mgtDbHelper.isTableEmpty(Constants.FAILED_ITEM_TABLE)) {
				log.info("Merge job completed successfully");
				cleanUp();
			}
		}
		
		log.info("Shutting down import executor");
		//Shutting down the executor effectively shuts down the application
		executor.shutdown();
	}
	
	private void cleanUp() {
		log.info("Cleaning up tables to remove any phantom rows");
		try {
			sinkDbHelper.deletePhantomRows();
		}
		catch (Throwable t) {
			log.error("Error occurred while cleaning up tables", t);
		}
	}
	
}
