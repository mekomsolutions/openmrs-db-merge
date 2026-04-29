package net.mekomsolutions.db.importer.batch;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.batch.item.adapter.ItemProcessorAdapter;

import lombok.extern.slf4j.Slf4j;
import net.mekomsolutions.db.importer.Constants;
import net.mekomsolutions.db.importer.DbUtils;
import net.mekomsolutions.db.importer.MergeUtils;
import net.mekomsolutions.db.importer.MetadataExtractor;
import net.mekomsolutions.db.importer.Retry;
import net.mekomsolutions.db.importer.Row;
import net.mekomsolutions.db.importer.Table;
import net.mekomsolutions.db.importer.helpers.SourceDbHelper;

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
		final String rowId = (String) item.get("identifier");
		final Table baseTable = metadataExtractor.getTable(baseTableName);
		if (log.isDebugEnabled()) {
			final String idLabel = MergeUtils.getIdentifierLabel(baseTable);
			log.debug("Retrying row in table {} with {} = {}", baseTableName, idLabel, rowId);
		}
		
		List<String> idCols;
		Object[] ids;
		if (baseTable.primaryKeys().size() == 1) {
			idCols = baseTable.primaryKeys();
			ids = new Object[] { DbUtils.convert(rowId, baseTable.getColumn(idCols.get(0)).sqlType()) };
		} else {
			idCols = baseTable.primaryKeys();
			String[] idsArray = StringUtils.split(rowId, Constants.COMPOSITE_ID_SEPARATOR);
			ids = new Object[idCols.size()];
			for (int i = 0; i < idCols.size(); i++) {
				ids[i] = DbUtils.convert(idsArray[i], baseTable.getColumn(idCols.get(i)).sqlType());
			}
		}
		
		Map<String, Object> rowData = sourceDbHelper.getRow(baseTableName, idCols, ids);
		Row row = helper.process(baseTable, rowData, true);
		if (row == null) {
			if (log.isDebugEnabled()) {
				final String idLabel = MergeUtils.getIdentifierLabel(baseTable);
				log.debug("Retry failed for row in table {} with {} = {} associated with retry with id {}", baseTableName,
				    idLabel, rowId, id);
			}
			
			return null;
		}
		
		return new Retry(id, baseTable, rowId, row);
	}
	
}
