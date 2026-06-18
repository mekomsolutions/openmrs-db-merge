package net.mekomsolutions.db.merge.batch;

import static net.mekomsolutions.db.merge.Constants.STEP_KEY_MAX_WRITTEN_ID;

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
import net.mekomsolutions.db.merge.Row;
import net.mekomsolutions.db.merge.ShutdownHook;

/**
 * An instance of this class is used to track the maximum table row ID written to the sink DB during
 * a step execution to make table merge resumable. This ensures that only rows beyond the previously
 * recorded maximum ID are merged in subsequent job runs. It interacts with the chunk lifecycle,
 * allowing it to clear state before processing each chunk and update the maximum row ID after
 * writing each chunk, and save the maximum row ID into the step execution context. The maximum
 * written row ID is stored in the context using a predefined key to ensure it can be retrieved
 * later to determine the table offset row id when the job is restarted.
 */
@Slf4j
public class MaxRowIdRecordingListener {
	
	private String tableName;
	
	private Object maxWrittenRowId = null;
	
	public MaxRowIdRecordingListener(String tableName) {
		this.tableName = tableName;
	}
	
	@BeforeChunk
	public void beforeChunk() {
		if (log.isTraceEnabled()) {
			log.trace("Clearing max written row id from previous chunks");
		}
		
		maxWrittenRowId = null;
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
				maxWrittenRowId = rows.stream().map(r -> {
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
		if (!ShutdownHook.getInstance().isShutdown() && maxWrittenRowId != null) {
			final StepContext stepContext = context.getStepContext();
			if (log.isTraceEnabled()) {
				final String stepName = stepContext.getStepName();
				final String name = stepName.equals(tableName) ? "" : "(" + stepName + ")";
				log.trace("Saving max row id of {} for table {}{}", maxWrittenRowId, tableName, name);
			}
			
			stepContext.getStepExecution().getExecutionContext().put(STEP_KEY_MAX_WRITTEN_ID, maxWrittenRowId);
		}
	}
	
}
