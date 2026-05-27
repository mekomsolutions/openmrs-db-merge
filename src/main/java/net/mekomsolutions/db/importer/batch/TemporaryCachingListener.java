package net.mekomsolutions.db.importer.batch;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.batch.core.annotation.AfterRead;
import org.springframework.batch.core.annotation.AfterWrite;
import org.springframework.batch.core.annotation.BeforeChunk;
import org.springframework.batch.core.annotation.BeforeProcess;
import org.springframework.beans.factory.annotation.Qualifier;

import lombok.extern.slf4j.Slf4j;
import net.mekomsolutions.db.importer.ForeignKey;
import net.mekomsolutions.db.importer.MergeUtils;
import net.mekomsolutions.db.importer.MetadataExtractor;
import net.mekomsolutions.db.importer.Table;

/**
 * An instance of this class is responsible for loading and temporarily caching all reference rows
 * by the rows in a chunk that is being processed by the item processor to improve speed. The way
 * this works is, before a chunk of rows is processed, all referenced row id by every row in the
 * chunk are read once for each referenced table and added to the cache, after the chunk is written
 * to the sink database, these row ids are all removed from the cache. As of the current spring
 * batch version, the framework provides no call back after a batch is read, so this listener
 * provides a way with a combination of others framework callback annotations.
 *
 * @see ForeignKeyValueMapCache
 */
@Slf4j
public class TemporaryCachingListener {
	
	private String tableName;
	
	protected ForeignKeyValueMapCache cache;
	
	protected MetadataExtractor metadataExtractor;
	
	private List<Map<String, Object>> rows;
	
	private boolean processing;
	
	public TemporaryCachingListener(String tableName, ForeignKeyValueMapCache cache,
	    @Qualifier("sourceExtractor") MetadataExtractor metadataExtractor) {
		this.tableName = tableName;
		this.cache = cache;
		this.metadataExtractor = metadataExtractor;
	}
	
	@BeforeChunk
	public void beforeChunk() {
		if (log.isTraceEnabled()) {
			log.trace("Preparing temporary cache for chunk");
		}
		
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
			//Because tables are serially merged, it's okay to synchronize on the instance.
			synchronized (this) {
				if (!processing) {
					Table table = metadataExtractor.getTable(tableName, false);
					for (String columnName : table.columns().keySet().stream()
					        .filter(c -> table.getColumn(c).foreignKey() != null).toList()) {
						ForeignKey fk = table.getColumn(columnName).foreignKey();
						String refTableName = fk.referencedTable();
						if (cache.isFullyCachedTable(refTableName)) {
							continue;
						}
						
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
						
						if (cache.isTemporaryCachedTable(refTableName)) {
							Object[] ids = rows.stream().map(r -> r.get(columnName)).filter(Objects::nonNull).toArray();
							if (ids.length > 0) {
								if (log.isTraceEnabled()) {
									log.trace("Temporarily caching referenced row ids on {}.{}", tableName, columnName);
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
	public void afterWrite() {
		processing = false;
		rows = null;
		cache.clearTemporaryIdMappings();
	}
	
}
