package net.mekomsolutions.db.importer.batch;

import static net.mekomsolutions.db.importer.Constants.STEP_KEY_MAX_PROCESSED_ID;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import org.springframework.batch.core.annotation.AfterChunk;
import org.springframework.batch.core.annotation.AfterWrite;
import org.springframework.batch.core.annotation.BeforeChunk;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.item.Chunk;

import lombok.extern.slf4j.Slf4j;
import net.mekomsolutions.db.importer.Row;

@Slf4j
public class MaxRowIdRecorder {
	
	private Object maxProcessedRowId = null;
	
	@BeforeChunk
	public void beforeChunk(ChunkContext context) {
		if (log.isTraceEnabled()) {
			log.trace("Clearing max processed row id from previous chunks");
		}
		
		maxProcessedRowId = null;
	}
	
	@AfterWrite
	public void afterWrite(Chunk<Future<Row>> chunk) {
		if (log.isTraceEnabled()) {
			log.trace("Resolving max row id from chunk of size {}", chunk.size());
		}
		
		//Resume support is currently not supported for extension and mapping tables because they are the ones
		//where id would be null.
		try {
			List<Row> rows = new ArrayList<>(chunk.size());
			for (Future<Row> future : chunk.getItems()) {
				Row row = future.get();
				if (row != null) {
					rows.add(row);
				}
			}
			
			if (rows.size() > 0 && rows.get(0).id() != null) {
				maxProcessedRowId = rows.stream().map(r -> {
					try {
						return r.id();
					}
					catch (Exception e) {
						throw new RuntimeException(e);
					}
				}).max(Integer::compareTo).get();
			}
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	@AfterChunk
	public void afterChunk(ChunkContext context) {
		if (maxProcessedRowId != null) {
			final StepContext stepContext = context.getStepContext();
			if (log.isTraceEnabled()) {
				log.trace("Saving max row id of {} for table {}", maxProcessedRowId, stepContext.getStepName());
			}
			
			stepContext.getStepExecution().getExecutionContext().put(STEP_KEY_MAX_PROCESSED_ID, maxProcessedRowId);
		}
	}
	
}
