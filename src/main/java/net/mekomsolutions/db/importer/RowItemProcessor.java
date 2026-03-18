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
	
	public RowItemProcessor(Table table) {
		this.table = table;
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
			values[i] = item.get(table.insertColumnNames().get(i));
		}
		
		return values;
	}
	
}
