package net.mekomsolutions.db.importer;

import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.item.adapter.ItemProcessorAdapter;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RowItemProcessor extends ItemProcessorAdapter<Map<String, Object>, Object[]> {
	
	private Table table;
	
	private ForeignKeyMappingFunction foreignKeyMapper;
	
	public RowItemProcessor(Table table, ForeignKeyMappingFunction foreignKeyMapper) {
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
						log.debug("Resolving the row in {} table {} referenced by foreign key {}.{}", refTableName,
						    table.name(), fk.columnName());
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
	
}
