package net.mekomsolutions.db.importer.batch;

import static net.mekomsolutions.db.importer.Constants.STEP_KEY_MAX_PROCESSED_ID;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Future;

import org.springframework.batch.core.annotation.AfterChunk;
import org.springframework.batch.core.annotation.AfterRead;
import org.springframework.batch.core.annotation.AfterWrite;
import org.springframework.batch.core.annotation.BeforeChunk;
import org.springframework.batch.core.annotation.BeforeProcess;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.item.Chunk;

import lombok.extern.slf4j.Slf4j;
import net.mekomsolutions.db.importer.ForeignKey;
import net.mekomsolutions.db.importer.MergeUtils;
import net.mekomsolutions.db.importer.MetadataExtractor;
import net.mekomsolutions.db.importer.Row;
import net.mekomsolutions.db.importer.Table;

@Slf4j
public class MaxRowIdRecorder {
	
	private String tableName;
	
	private ForeignKeyValueMapCache cache;
	
	private MetadataExtractor metadataExtractor;
	
	private Object maxProcessedRowId = null;
	
	private List<Map<String, Object>> rows;
	
	private boolean processing;
	
	public MaxRowIdRecorder(String tableName, ForeignKeyValueMapCache cache, MetadataExtractor metadataExtractor) {
		this.cache = cache;
		this.metadataExtractor = metadataExtractor;
		this.tableName = tableName;
	}
	
	@BeforeChunk
	public void beforeChunk() {
		if (log.isTraceEnabled()) {
			log.trace("Clearing max processed row id from previous chunks");
		}
		
		maxProcessedRowId = null;
		processing = false;
		rows = new ArrayList<>();
	}
	
	@AfterRead
	public void afterRead(Map<String, Object> row) {
		rows.add(row);
	}
	
	@BeforeProcess
	public void beforeProcess() {
		if (!processing) {
			synchronized (this) {
				if (!processing) {
					Table table = metadataExtractor.getTable(tableName, false);
					for (String columnName : table.columns().keySet().stream()
					        .filter(c -> table.getColumn(c).foreignKey() != null).toList()) {
						ForeignKey fk = table.getColumn(columnName).foreignKey();
						String refTableName = fk.referencedTable();
						Table refTable = metadataExtractor.getTable(refTableName, false);
						if (MergeUtils.isSubclassTable(refTableName)) {
							String parentTable = refTable.getColumn(refTable.primaryKeys().get(0)).foreignKey()
							        .referencedTable();
							if (log.isDebugEnabled()) {
								log.debug("Deferring to parent table {} instead of {} for id mappings", parentTable,
								    refTableName);
							}
							
							refTableName = parentTable;
						}
						
						if (!cache.isFullyCachedTable(refTableName) && cache.isTemporaryCachedTable(refTableName)) {
							Object[] ids = rows.stream().map(r -> r.get(columnName)).filter(Objects::nonNull).toArray();
							if (ids.length > 0) {
								if (log.isTraceEnabled()) {
									log.trace("Temporarily caching referenced row ids on {}.{} in {} table", tableName,
									    columnName, refTableName);
								}
								
								cache.addTempRowIdMappings(refTableName, ids);
							}
						}
					}
					
					processing = true;
				}
			}
		}
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
		finally {
			cache.clearTemporaryIdMappings();
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
