package net.mekomsolutions.db.importer.batch;

import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.annotation.AfterStep;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * This {@code org.springframework.batch.core.StepExecutionListener} is responsible for adding
 * source to sink primary key value mappings when some specific tables are synced fully.
 */
@Slf4j
@Component
public class TableStepListener {
	
	@AfterStep
	public void afterStep(StepExecution stepExecution) {
		final String tableName = stepExecution.getStepName();
	}
	
}
