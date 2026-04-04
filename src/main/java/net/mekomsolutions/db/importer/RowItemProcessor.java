package net.mekomsolutions.db.importer;

import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.batch.item.adapter.ItemProcessorAdapter;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RowItemProcessor extends ItemProcessorAdapter<Map<String, Object>, Row> {
	
	private Table baseTable;
	
	private MetadataExtractor metadataExtractor;
	
	private SourceDbHelper sourceDbHelper;
	
	private SinkDbHelper sinkDbHelper;
	
	public RowItemProcessor(Table baseTable, MetadataExtractor metadataExtractor, SourceDbHelper sourceDbHelper,
	    SinkDbHelper sinkDbHelper) {
		this.baseTable = baseTable;
		this.metadataExtractor = metadataExtractor;
		this.sourceDbHelper = sourceDbHelper;
		this.sinkDbHelper = sinkDbHelper;
	}
	
	@Override
	public Row process(Map<String, Object> item) throws Exception {
		String threadName = Thread.currentThread().getName();
		try {
			final String key = baseTable.primaryKeys().stream().map(k -> item.get(k).toString())
			        .collect(Collectors.joining(","));
			Thread.currentThread().setName(baseTable.name() + ":" + key);
			if (log.isDebugEnabled()) {
				log.debug("Processing: {}", key);
			}
			
			final Object[] values = createColumnValues(baseTable, item);
			Integer id = null;
			if (baseTable.primaryKeys().size() == 1) {
				String pkColumnName = baseTable.primaryKeys().get(0);
				id = (Integer) item.get(pkColumnName);
			}
			
			return new Row(id, values);
		}
		finally {
			Thread.currentThread().setName(threadName);
		}
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
					value = resolveForeignKeyValue(value, fk, table);
				}
			}
			
			values[i] = value;
		}
		
		return values;
	}
	
	private Object resolveForeignKeyValue(Object value, ForeignKey fk, Table table) {
		String refTableName = fk.referenceTable();
		String refColName = fk.referencedColumn();
		if (ImportUtils.isSubclassTable(refTableName)) {
			Table refTable = metadataExtractor.getTable(refTableName);
			ForeignKey parentFk = refTable.getColumn(refColName).foreignKey();
			refTableName = parentFk.referenceTable();
			refColName = parentFk.referencedColumn();
		}
		
		if (log.isDebugEnabled()) {
			log.debug("Getting row in the {} source table referenced by {}.{}", refTableName, table.name(), fk.columnName());
		}
		
		Object refUuid = sourceDbHelper.getUuid(refTableName, refColName, value);
		if (refUuid == null) {
			String msg = String.format("Failed to find referenced row in source table %s with %s = %s", refTableName,
			    refColName, value);
			throw new RuntimeException(msg);
		}
		
		Object sinkValue;
		//Synchronized block ensures no concurrent inserts of placeholders into a specific table to avoid
		//race conditions and possibly deadlocks.
		//TODO we should possibly allow concurrent inserts of different rows.
		synchronized (fk) {
			//TODO Cache foreign key values which can be helpful for larger tables like Obs that may
			//repeatedly reference the same row
			refUuid = refUuid.toString().toLowerCase(Locale.ENGLISH);
			sinkValue = sinkDbHelper.getColumnValue(refTableName, refColName, "LOWER(uuid)", refUuid);
			if (sinkValue == null) {
				sinkValue = ImportUtils.insertPlaceholderRow(fk, table.name(), refUuid, metadataExtractor, sinkDbHelper);
			}
		}
		
		return sinkValue;
	}
	
}
