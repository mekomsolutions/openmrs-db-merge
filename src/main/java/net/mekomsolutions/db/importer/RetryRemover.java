package net.mekomsolutions.db.importer;

import static net.mekomsolutions.db.importer.Constants.FAILED_ITEM_TABLE;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import org.apache.commons.lang3.StringUtils;
import org.springframework.batch.core.annotation.AfterChunk;
import org.springframework.batch.core.annotation.AfterWrite;
import org.springframework.batch.core.annotation.BeforeChunk;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.item.Chunk;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * The RetryRemover class is responsible for deleting re-processed rows from the
 * {@link Constants#FAILED_ITEM_TABLE} table.
 */
@Slf4j
@Component
public class RetryRemover {
	
	private static final String DELETE_QUERY = "DELETE FROM " + FAILED_ITEM_TABLE + " WHERE id IN (%s)";
	
	private List<Integer> retryIds = null;
	
	private JdbcTemplate jdbcTemplate;
	
	public RetryRemover(@Qualifier("mgtJdbcTemplate") JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}
	
	@BeforeChunk
	public void beforeChunk(ChunkContext context) {
		if (log.isTraceEnabled()) {
			log.trace("Clearing retryIds from previous chunks");
		}
		
		retryIds = null;
	}
	
	@AfterWrite
	public void afterWrite(Chunk<Future<Retry>> chunk) {
		if (log.isTraceEnabled()) {
			log.trace("Resolving retryIds from chunk of size {}", chunk.size());
		}
		
		//Resume support is currently not supported for extension and mapping tables because they are the ones
		//where id would be null.
		try {
			retryIds = new ArrayList<>(chunk.size());
			for (Future<Retry> future : chunk.getItems()) {
				Retry retry = future.get();
				if (retry != null) {
					retryIds.add(retry.retryId());
				}
			}
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	@AfterChunk
	public void afterChunk(ChunkContext context) {
		if (retryIds != null && retryIds.size() > 0) {
			if (log.isTraceEnabled()) {
				final String plural = retryIds.size() == 1 ? "retry" : "retries";
				log.trace("Removing {} {} from the {} table", retryIds.size(), plural, FAILED_ITEM_TABLE);
			}
			
			String retryIdsString = StringUtils.join(retryIds, ",");
			int deleteCount = jdbcTemplate.update(String.format(DELETE_QUERY, retryIdsString));
			if (log.isDebugEnabled()) {
				final String plural = deleteCount == 1 ? "row" : "rows";
				log.info("Deleted {} re-processed {} from the {} table", deleteCount, plural, FAILED_ITEM_TABLE);
			}
			
			if (deleteCount != retryIds.size()) {
				final int failedCount = retryIds.size() - deleteCount;
				final String plural = failedCount == 1 ? "row" : "rows";
				log.warn("Failed to delete {} {} from the {} table", failedCount, plural, FAILED_ITEM_TABLE);
			}
		}
	}
	
}
