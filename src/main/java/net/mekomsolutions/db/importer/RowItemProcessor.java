package net.mekomsolutions.db.importer;

import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.item.adapter.ItemProcessorAdapter;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RowItemProcessor extends ItemProcessorAdapter<Map<String, Object>, Object[]> {
	
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
						log.debug("Getting the row in the {} source table referenced by {}.{}", refTableName, table.name(),
						    fk.columnName());
					}
					
					final String refColName = fk.referencedColumn();
					Map<String, Object> refRow = sourceDbHelper.getRow(refTableName, refColName, value);
					if (refRow == null) {
						String msg = String.format("Failed to find referenced row in source table %s with %s = %s",
						    refTableName, refColName, value);
						throw new RuntimeException(msg);
					}
					
					if (!refRow.containsKey("uuid")) {
						throw new RuntimeException(String.format("Source table %s has no uuid column", refTableName));
					}
					
					Object refUuid = refRow.get("uuid");
					if (refUuid == null) {
						String msg = String.format("No uuid found for referenced row in source table %s with %s = %s",
						    refTableName, refColName, value);
						throw new RuntimeException(msg);
					}
					
					//TODO Cache foreign key values which can be helpful for larger tables like Obs that may
					//repeatedly reference the same row
					Object sinkValue = sinkDbHelper.getColumnValue(refTableName, refColName, "uuid", refUuid);
					if (sinkValue == null) {
						sinkValue = insertReferencedRow(fk, refRow);
					}
					
					value = sinkValue;
				}
			}
			
			values[i] = value;
		}
		
		return values;
	}
	
	protected Object insertReferencedRow(ForeignKey fk, Map<String, Object> row) {
		final String refTableName = fk.referenceTable();
		if (log.isDebugEnabled()) {
			final String baseColName = fk.columnName();
			log.debug("Inserting a row into table {} referenced by {}.{}", refTableName, baseTable.name(), baseColName);
		}
		
		Table refTable = metadataExtractor.getTable(refTableName);
		Object[] refColumnValues = createColumnValues(refTable, row);
		return sinkDbHelper.insertRow(refTableName, refTable.insertColumnNames(), refColumnValues);
	}
	
}
