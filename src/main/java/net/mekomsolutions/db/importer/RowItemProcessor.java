package net.mekomsolutions.db.importer;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.item.adapter.ItemProcessorAdapter;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RowItemProcessor extends ItemProcessorAdapter<Map<String, Object>, Object[]> {
	
	private Table table;
	
	private ForeignKeyMapper foreignKeyMapper;
	
	private List<Column> requiredColumns;
	
	private SourceDbHelper sourceDbHelper;
	
	private SinkDbHelper sinkDbHelper;
	
	public RowItemProcessor(Table table, ForeignKeyMapper foreignKeyMapper) {
		this.table = table;
		this.foreignKeyMapper = foreignKeyMapper;
	}
	
	@BeforeStep
	public void beforeStep(StepExecution stepExecution) {
		if (log.isDebugEnabled()) {
			log.debug("Processing table: {}", table.name());
		}
	}
	
	@Override
	public Object[] process(Map<String, Object> item) throws Exception {
		//TODO If an item exists in the sink DB, may be update it, but where did it come from?
		final String pk = item.entrySet().stream().filter(e -> table.primaryKeys().contains(e.getKey()))
		        .map(e -> e.getValue().toString()).collect(Collectors.joining(","));
		if (log.isDebugEnabled()) {
			log.debug("Processing: {}", pk);
		}
		
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
						log.debug("Resolving the row in the {} table referenced by {}.{}", refTableName, table.name(),
						    fk.columnName());
					}
					
					value = foreignKeyMapper.apply(value, fk);
					if (value == null) {
						//TODO Create Placeholder
					}
				}
			}
			
			values[i] = value;
		}
		
		return values;
	}
	
	protected Object insertPlaceholderRow(String tableName, Table table) {
		return sinkDbHelper.insertRow(tableName, requiredColumns.stream().map(Column::name).toList(),
		    createPlaceholderRow(table));
	}
	
	protected Object[] createPlaceholderRow(Table table) {
		if (requiredColumns == null) {
			//TODO Skip auto generated columns e.g. primary key
			requiredColumns = table.columns().values().stream()
			        .filter(c -> !table.primaryKeys().contains(c.name()) && !c.nullable()).toList();
		}
		
		Object[] values = new Object[requiredColumns.size()];
		int index = 0;
		for (Column col : requiredColumns) {
			Object value;
			ForeignKey fk = col.foreignKey();
			if (fk == null) {
				value = DbUtils.getPlaceHolder(col);
			} else {
				value = null;
			}
			
			values[index] = value;
			index++;
		}
		
		return values;
	}
	
}
