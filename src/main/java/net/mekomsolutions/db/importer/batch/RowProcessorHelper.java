package net.mekomsolutions.db.importer.batch;

import static net.mekomsolutions.db.importer.MergeUtils.insertPlaceholderSubclassRow;

import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import net.mekomsolutions.db.importer.Column;
import net.mekomsolutions.db.importer.Constants;
import net.mekomsolutions.db.importer.ForeignKey;
import net.mekomsolutions.db.importer.MergeUtils;
import net.mekomsolutions.db.importer.MetadataExtractor;
import net.mekomsolutions.db.importer.Row;
import net.mekomsolutions.db.importer.Table;
import net.mekomsolutions.db.importer.helpers.MgtDbHelper;
import net.mekomsolutions.db.importer.helpers.SinkDbHelper;
import net.mekomsolutions.db.importer.helpers.SourceDbHelper;

@Slf4j
@Component
public class RowProcessorHelper {
	
	private MetadataExtractor metadataExtractor;
	
	private SourceDbHelper sourceDbHelper;
	
	private SinkDbHelper sinkDbHelper;
	
	private MgtDbHelper mgtDbHelper;
	
	protected OpenMrsMetadataMapper metadataMapper;
	
	public RowProcessorHelper(@Qualifier("sourceExtractor") MetadataExtractor metadataExtractor,
	    SourceDbHelper sourceDbHelper, SinkDbHelper sinkDbHelper, MgtDbHelper mgtDbHelper,
	    OpenMrsMetadataMapper metadataMapper) {
		this.metadataExtractor = metadataExtractor;
		this.sourceDbHelper = sourceDbHelper;
		this.sinkDbHelper = sinkDbHelper;
		this.mgtDbHelper = mgtDbHelper;
		this.metadataMapper = metadataMapper;
	}
	
	public Row process(Table baseTable, Map<String, Object> item, boolean isRetry) {
		String threadName = Thread.currentThread().getName();
		final String threadNamePrefix = (isRetry ? "retry" : "import") + ":";
		try {
			final String key = baseTable.primaryKeys().stream().map(k -> item.get(k).toString())
			        .collect(Collectors.joining(","));
			Thread.currentThread().setName(threadNamePrefix + baseTable.name() + ":" + key);
			if (log.isDebugEnabled()) {
				log.debug("Processing: {}", key);
			}
			
			return doProcess(baseTable, item);
		}
		catch (Throwable t) {
			if (log.isDebugEnabled()) {
				log.error("Error processing row:{} -> msg: {}", item, t);
			}
			
			//TODO Future for a retry update error type and message
			if (!isRetry) {
				MergeUtils.handleFailure(baseTable, item, t, mgtDbHelper);
			}
			
			return null;
		}
		finally {
			Thread.currentThread().setName(threadName);
		}
	}
	
	private Row doProcess(Table table, Map<String, Object> item) {
		if ("users".equalsIgnoreCase(table.name())) {
			if (Constants.DAEMON_USER_UUID.equalsIgnoreCase(item.get("uuid").toString())) {
				//Daemon user is not really a user account, skip it.
				return null;
			}
			
			boolean exists = sinkDbHelper.checkIfUserExists(item.get("username"), item.get("system_id"));
			if (exists) {
				MergeUtils.retireRecord(item, sinkDbHelper);
			}
		}
		
		final Object[] values = createColumnValues(table, item);
		Integer id = null;
		if (table.primaryKeys().size() == 1) {
			String pkColumnName = table.primaryKeys().get(0);
			id = (Integer) item.get(pkColumnName);
		}
		
		return new Row(id, values);
	}
	
	/**
	 * Creates an array of column values to insert into the specified table based on the row data
	 * provided in the map. Each column value is resolved and populated by processing the foreign key
	 * relationships, if applicable, and handling references between tables. This is achieved because
	 * the method recursively calls itself to create column values for any missing referenced rows
	 * missing that needs to be inserted into the sink database.
	 *
	 * @param table the table whose column values are to be created
	 * @param item a map containing key-value pairs where the key is the column name and the value is
	 *            the associated data
	 * @return an array of objects representing the resolved column values
	 */
	protected Object[] createColumnValues(Table table, Map<String, Object> item) {
		Object[] values = new Object[table.insertColumnNames().size()];
		for (int i = 0; i < table.insertColumnNames().size(); i++) {
			final String columnName = table.insertColumnNames().get(i);
			Object value = item.get(columnName);
			if (value != null) {
				Column column = table.getColumn(columnName);
				ForeignKey fk = column.foreignKey();
				if (fk != null) {
					//For a subclass table, use the id mappings of the parent table
					String refTable = fk.referencedTable();
					if (MergeUtils.isSubclassTable(refTable)) {
						String refCol = fk.referencedColumn();
						refTable = metadataExtractor.getTable(refTable, false).getColumn(refCol).foreignKey()
						        .referencedTable();
					}
					
					Object sinkRowId = null;
					if (metadataMapper.hasIdMappings(refTable)) {
						sinkRowId = metadataMapper.getSinkRowId(refTable, value);
					}
					
					//Cached value can be null of the row has not yet been synced e.g. could be in the failure queue.
					if (sinkRowId != null) {
						value = sinkRowId;
					} else {
						value = resolveForeignKeyValue(value, fk, table);
					}
				}
			}
			
			values[i] = value;
		}
		
		return values;
	}
	
	/**
	 * Resolves the foreign key value by retrieving the associated reference from the source table,
	 * ensuring the required row exists in the sink table, and managing subclass table relationships if
	 * applicable.
	 * 
	 * <pre>
	 * This method is synchronized to ensure no concurrent inserts of placeholder rows take place to avoid race 
	 * conditions, this would typically occur when 2 threads attempt to insert different rows but themselves or their 
	 * associations reference common row(s) that are not yet merged which would result in unique constraint violations.
	 * </pre>
	 *
	 * @param value The value to resolve as per the foreign key.
	 * @param fk The foreign key object containing metadata about the foreign key relationship.
	 * @param table The table where the foreign key is defined.
	 * @return The resolved value that corresponds to the foreign key as it exists in the sink table.
	 * @throws RuntimeException If the referenced row cannot be found in the source or sink table.
	 */
	private synchronized Object resolveForeignKeyValue(Object value, ForeignKey fk, Table table) {
		//TODO Cache foreign key values which can be helpful for larger tables like Obs that may repeatedly reference 
		//the same associated row e.g patient. Even users table is referenced by multiple columns in other tables.
		String baseRefTableName = fk.referencedTable();
		String baseRefColName = fk.referencedColumn();
		String effectiveRefTableName = fk.referencedTable();
		String effectiveRefColName = fk.referencedColumn();
		boolean isSubclassTable = MergeUtils.isSubclassTable(baseRefTableName);
		if (isSubclassTable) {
			//For subclasses, the uuid is in the parent table
			Table refTable = metadataExtractor.getTable(baseRefTableName, false);
			ForeignKey parentFk = refTable.getColumn(baseRefColName).foreignKey();
			effectiveRefTableName = parentFk.referencedTable();
			effectiveRefColName = parentFk.referencedColumn();
		}
		
		if (log.isDebugEnabled()) {
			final String message = isSubclassTable
			        ? "source " + baseRefTableName + " row joined to " + effectiveRefTableName + " row"
			        : "source " + baseRefTableName + " row";
			log.debug("Getting {} referenced by {}.{}", message, table.name(), fk.columnName());
		}
		
		Object refUuid = sourceDbHelper.getUuid(effectiveRefTableName, effectiveRefColName, value);
		if (refUuid == null) {
			String msg = String.format("Failed to find referenced row in source table %s with %s = %s",
			    effectiveRefTableName, effectiveRefColName, value);
			throw new RuntimeException(msg);
		}
		
		refUuid = refUuid.toString().toLowerCase(Locale.ENGLISH);
		Object sinkValue = sinkDbHelper.getColumnValue(effectiveRefTableName, effectiveRefColName, "LOWER(uuid)", refUuid);
		if (sinkValue == null) {
			if (log.isDebugEnabled()) {
				final String msg = String.format(
				    "Preparing placeholder row to insert into sink table %s with uuid %s referenced by %s.%s",
				    effectiveRefTableName, refUuid, table.name(), fk.columnName());
				log.debug(msg);
			}
			
			sinkValue = MergeUtils.insertPlaceholderRow(fk.referencedTable(), fk.referencedColumn(), refUuid,
			    metadataExtractor, sinkDbHelper);
		} else {
			if (log.isDebugEnabled()) {
				log.debug("Found referenced row in sink table {} with uuid {}, its {} = {}", effectiveRefTableName, refUuid,
				    effectiveRefColName, sinkValue);
			}
			
			if (isSubclassTable) {
				//For subclass table, insert subclass row if it does not exist
				if (!sinkDbHelper.checkIfRowExists(baseRefTableName, baseRefColName, sinkValue)) {
					if (log.isDebugEnabled()) {
						log.debug("Preparing placeholder subclass row to insert into sink table {} for parent row "
						        + "in table {} with uuid {}",
						    baseRefTableName, effectiveRefTableName, refUuid);
					}
					
					insertPlaceholderSubclassRow(baseRefTableName, sinkValue, metadataExtractor, sinkDbHelper);
				} else if (log.isDebugEnabled()) {
					log.debug("Subclass row exists in sink table {} for parent row in table {} with uuid {}",
					    baseRefTableName, effectiveRefTableName, refUuid);
				}
			}
		}
		
		return sinkValue;
	}
	
}
