package net.mekomsolutions.db.importer;

import java.util.Locale;
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
					String refTableName = fk.referenceTable();
					String refColName = fk.referencedColumn();
					if (ImportUtils.isSubclassTable(refTableName)) {
						Table refTable = metadataExtractor.getTable(refTableName);
						ForeignKey parentFk = refTable.getColumn(refColName).foreignKey();
						refTableName = parentFk.referenceTable();
						refColName = parentFk.referencedColumn();
					}
					
					if (log.isDebugEnabled()) {
						log.debug("Getting row in the {} source table referenced by {}.{}", refTableName, table.name(),
						    fk.columnName());
					}
					
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
						sinkValue = ImportUtils.insertPlaceholderRow(fk, table.name(), refUuid, metadataExtractor,
						    sinkDbHelper);
					}
					
					value = sinkValue;
				}
			}
			
			values[i] = value;
		}
		
		return values;
	}
	
}
