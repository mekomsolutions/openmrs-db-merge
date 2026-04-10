package net.mekomsolutions.db.importer;

import java.util.Map;

import org.springframework.batch.item.adapter.ItemProcessorAdapter;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RetryItemProcessor extends ItemProcessorAdapter<Map<String, Object>, Retry> {
	
	private SourceDbHelper sourceDbHelper;
	
	private RowProcessorHelper helper;
	
	private MetadataExtractor metadataExtractor;
	
	public RetryItemProcessor(SourceDbHelper sourceDbHelper, RowProcessorHelper helper,
	    MetadataExtractor metadataExtractor) {
		this.sourceDbHelper = sourceDbHelper;
		this.helper = helper;
		this.metadataExtractor = metadataExtractor;
	}
	
	@Override
	public Retry process(Map<String, Object> item) throws Exception {
		final Integer id = (Integer) item.get("id");
		final String baseTableName = (String) item.get("table_name");
		final String primaryKey = (String) item.get("primary_key");
		final Table baseTable = metadataExtractor.getTable(baseTableName);
		final String primaryKeyCol = baseTable.primaryKeys().get(0);
		if (log.isDebugEnabled()) {
			log.debug("Retrying row in table {} with {} = {}", baseTableName, primaryKeyCol, primaryKey);
		}
		
		Map<String, Object> rowData = sourceDbHelper.getRow(baseTableName, primaryKeyCol, primaryKey);
		Row row = helper.process(baseTable, rowData, false);
		return new Retry(id, baseTable, row);
	}
	
}
