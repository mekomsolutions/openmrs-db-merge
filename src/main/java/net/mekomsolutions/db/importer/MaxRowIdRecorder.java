package net.mekomsolutions.db.importer;

import static net.mekomsolutions.db.importer.Constants.STEP_KEY_MAX_PROCESSED_ID;

import org.springframework.batch.core.annotation.AfterChunk;
import org.springframework.batch.core.annotation.AfterWrite;
import org.springframework.batch.core.annotation.BeforeChunk;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.item.Chunk;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MaxRowIdRecorder {
	
	private Object maxProcessedRowId = null;
	
	@BeforeChunk
	public void beforeChunk(ChunkContext context) {
		if (log.isDebugEnabled()) {
			log.trace("Clearing max processed row id from previous chunks");
		}
		
		maxProcessedRowId = null;
	}
	
	@AfterWrite
	public void afterWrite(Chunk<Row> chunk) {
		if (log.isDebugEnabled()) {
			log.trace("Resolving max row id from chunk of size {}", chunk.size());
		}
		
		maxProcessedRowId = chunk.getItems().stream().map(r -> r.id()).max(Integer::compareTo).get();
	}
	
	@AfterChunk
	public void afterChunk(ChunkContext context) {
		if (maxProcessedRowId == null) {
			final StepContext stepContext = context.getStepContext();
			if (log.isDebugEnabled()) {
				log.trace("Saving max row id of {} for table {}", maxProcessedRowId, stepContext.getStepName());
			}
			
			stepContext.getStepExecution().getExecutionContext().put(STEP_KEY_MAX_PROCESSED_ID, maxProcessedRowId);
		}
	}
	
}
