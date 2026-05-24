package net.mekomsolutions.db.importer.batch;

import java.util.Map;

import org.springframework.batch.item.adapter.ItemProcessorAdapter;

import lombok.extern.slf4j.Slf4j;
import net.mekomsolutions.db.importer.Row;
import net.mekomsolutions.db.importer.Table;

@Slf4j
public class RowItemProcessor extends ItemProcessorAdapter<Map<String, Object>, Row> {
	
	private String stepName;
	
	private Table baseTable;
	
	private RowProcessorHelper helper;
	
	public RowItemProcessor(String stepName, Table baseTable, RowProcessorHelper helper) {
		this.stepName = stepName;
		this.baseTable = baseTable;
		this.helper = helper;
	}
	
	@Override
	public Row process(Map<String, Object> item) {
		return helper.process(stepName, baseTable, item, false);
	}
	
}
