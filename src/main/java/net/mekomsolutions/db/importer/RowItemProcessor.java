package net.mekomsolutions.db.importer;

import static net.mekomsolutions.db.importer.Constants.PHANTOM_UUID;

import java.sql.Types;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.item.adapter.ItemProcessorAdapter;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RowItemProcessor extends ItemProcessorAdapter<Map<String, Object>, Object[]> {
	
	private static Integer daemonUserId;
	
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
	
	@BeforeStep
	public void beforeStep(StepExecution stepExecution) {
		if (log.isDebugEnabled()) {
			log.debug("Processing table: {}", baseTable.name());
		}
	}
	
	@Override
	public Object[] process(Map<String, Object> item) throws Exception {
		//TODO If an item exists in the sink DB, may be update it, but where did it come from?
		final String pk = item.entrySet().stream().filter(e -> baseTable.primaryKeys().contains(e.getKey()))
		        .map(e -> e.getValue().toString()).collect(Collectors.joining(","));
		if (log.isDebugEnabled()) {
			log.debug("Processing: {}", pk);
		}
		
		return createColumnValues(baseTable, item);
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
					final String refTableName = fk.referenceTable();
					if (log.isDebugEnabled()) {
						log.debug("Getting row in the {} source table referenced by {}.{}", refTableName, table.name(),
						    fk.columnName());
					}
					
					final String refColName = fk.referencedColumn();
					Object refUuid = sourceDbHelper.getUuid(refTableName, refColName, value);
					if (refUuid == null) {
						String msg = String.format("Failed to find referenced row in source table %s with %s = %s",
						    refTableName, refColName, value);
						throw new RuntimeException(msg);
					}
					
					//TODO Cache foreign key values which can be helpful for larger tables like Obs that may
					//repeatedly reference the same row
					refUuid = refUuid.toString().toLowerCase(Locale.ENGLISH);
					Object sinkValue = sinkDbHelper.getColumnValue(refTableName, refColName, "LOWER(uuid)", refUuid);
					if (sinkValue == null) {
						sinkValue = insertPlaceholderRow(fk, table.name(), refUuid);
					}
					
					value = sinkValue;
				}
			}
			
			values[i] = value;
		}
		
		return values;
	}
	
	protected Object insertPlaceholderRow(ForeignKey fk, String fkTableName, Object uuid) {
		//TODO Make thread safe to avoid duplication of placeholder row
		final String refTableName = fk.referenceTable();
		if (log.isDebugEnabled()) {
			final String fromColName = fk.columnName();
			final String kind = uuid == null ? "phantom" : "placeholder";
			final Object Uid = uuid == null ? PHANTOM_UUID : uuid;
			log.debug("Inserting {} row into sink table {} with uuid {} referenced by {}.{}", kind, refTableName, Uid,
			    fkTableName, fromColName);
		}
		
		Table refTable = metadataExtractor.getTable(refTableName);
		List<Column> requiredColumns = getRequiredColumns(refTable);
		return sinkDbHelper.insertRow(refTableName, requiredColumns.stream().map(Column::name).toList(),
		    createPlaceholderRow(fk, requiredColumns, uuid));
	}
	
	protected Object[] createPlaceholderRow(ForeignKey fk, List<Column> requiredColumns, Object uuid) {
		final String refTableName = fk.referenceTable();
		Object[] values = new Object[requiredColumns.size()];
		int index = 0;
		for (Column col : requiredColumns) {
			final String colName = col.name();
			Object value;
			if ("uuid".equalsIgnoreCase(colName)) {
				if (uuid != null) {
					value = uuid;
				} else {
					//This is a dummy row since uuid is unknown
					value = PHANTOM_UUID;
				}
			} else if ("voided".equalsIgnoreCase(colName) || "retired".equalsIgnoreCase(colName)) {
				if (log.isDebugEnabled()) {
					final String kind = uuid == null ? "phantom" : "placeholder";
					log.debug("Marking {} row {} in sink table {} as {}", kind, colName, refTableName, colName);
				}
				
				if (Types.BOOLEAN == col.sqlType()) {
					value = true;
				} else if (Types.BIT == col.sqlType()) {
					value = 1;
				} else {
					String msg = "Don't know how handle type: " + col.sqlType() + " for column " + refTableName + "."
					        + colName;
					throw new RuntimeException(msg);
				}
			} else {
				ForeignKey colFk = col.foreignKey();
				if (colFk == null) {
					value = DbUtils.getPlaceHolder(col, refTableName);
				} else if (uuid == null && isUserSelfReference(refTableName, colName)) {
					if (log.isDebugEnabled()) {
						log.debug("Setting {} for phantom row in sink table {} to daemon user id", colName, refTableName);
					}
					
					value = getDaemonUserId();
				} else {
					value = getPhantomRowId(colFk, refTableName);
				}
			}
			
			values[index] = value;
			index++;
		}
		
		return values;
	}
	
	protected Object getPhantomRowId(ForeignKey fk, String fkTableName) {
		//TODO Make thread safe to avoid duplication of phantom row
		final String refTableName = fk.referenceTable();
		final String refColName = fk.referencedColumn();
		Object phantomRowId = sinkDbHelper.getColumnValue(refTableName, refColName, "UPPER(uuid)", PHANTOM_UUID);
		if (phantomRowId == null) {
			//Insert the phantom row
			phantomRowId = insertPlaceholderRow(fk, fkTableName, null);
		}
		
		return phantomRowId;
	}
	
	private List<Column> getRequiredColumns(Table t) {
		//TODO Cache the required columns for each table or change Table from a record
		return t.columns().values().stream().filter(c -> !t.primaryKeys().contains(c.name()) && !c.nullable()).toList();
	}
	
	private Integer getDaemonUserId() {
		if (daemonUserId != null) {
			return daemonUserId;
		}
		
		Object id = sinkDbHelper.getColumnValue("users", "user_id", "UPPER(uuid)", Constants.DAEMON_USER_UUID);
		if (id == null) {
			throw new RuntimeException("Daemon user not found in sink database");
		}
		
		daemonUserId = (Integer) id;
		return daemonUserId;
	}
	
	private boolean isUserSelfReference(String table, String colName) {
		return "users".equalsIgnoreCase(table) && ("creator".equalsIgnoreCase(colName)
		        || "changed_by".equalsIgnoreCase(colName) || "retired_by".equalsIgnoreCase(colName));
	}
	
}
