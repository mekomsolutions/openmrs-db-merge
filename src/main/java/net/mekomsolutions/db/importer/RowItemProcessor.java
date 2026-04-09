package net.mekomsolutions.db.importer;

import java.util.Map;

import org.springframework.batch.item.adapter.ItemProcessorAdapter;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RowItemProcessor extends ItemProcessorAdapter<Map<String, Object>, Row> {
	
	private Table baseTable;
	
	private RowProcessorHelper helper;
	
	public RowItemProcessor(Table baseTable, RowProcessorHelper helper) {
		this.baseTable = baseTable;
		this.helper = helper;
	}
	
	@Override
	public Row process(Map<String, Object> item) throws Exception {
		return helper.process(baseTable, item);
	}
	
}
