package net.mekomsolutions.db.importer.batch;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import net.mekomsolutions.db.importer.Table;
import net.mekomsolutions.db.importer.helpers.SinkDbHelper;
import net.mekomsolutions.db.importer.helpers.SourceDbHelper;

@Component
@Slf4j
public class OpenMrsMetadataMapper {
	
	private static final List<String> METADATA_TABLES = List.of("visit_type", "encounter_type", "order_type");
	
	private static final Map<String, Map<Object, Object>> TABLE_AND_IDS_MAP = new HashMap<>();
	
	private SourceDbHelper sourceDbHelper;
	
	private SinkDbHelper sinkDbHelper;
	
	public OpenMrsMetadataMapper(SourceDbHelper sourceDbHelper, SinkDbHelper sinkDbHelper) {
		this.sourceDbHelper = sourceDbHelper;
		this.sinkDbHelper = sinkDbHelper;
	}
	
	public void initialize() {
		log.info("Initializing source to sink row id mappings for OpenMRS metadata");
		for (String tableName : METADATA_TABLES) {
			Table table = sinkDbHelper.getMetadataExtractor().getTable(tableName, false);
			final String idCol = table.primaryKeys().get(0);
			List<Map<String, Object>> sourceRows = sourceDbHelper.getAllRows(tableName, idCol);
			Map<Object, String> sourceIdAndUuidMap = sourceRows.stream().collect(HashMap::new,
			    (map, row) -> map.put(row.get(idCol), row.get("uuid").toString()), HashMap::putAll);
			
			List<Map<String, Object>> sinkRows = sinkDbHelper.getAllRows(tableName, idCol);
			Map<String, Object> sinkUuidAndIdMap = sinkRows.stream().collect(HashMap::new,
			    (map, row) -> map.put(row.get("uuid").toString(), row.get(idCol)), HashMap::putAll);
			
			Map<Object, Object> idsMap = sourceIdAndUuidMap.entrySet().stream()
			        .collect(Collectors.toMap(e -> e.getKey(), e -> sinkUuidAndIdMap.get(e.getValue())));
			TABLE_AND_IDS_MAP.put(tableName, idsMap);
		}
		
		log.info("Done initializing source to sink row id mappings for OpenMRS metadata");
	}
	
	public boolean hasIdMappings(String tableName) {
		return TABLE_AND_IDS_MAP.containsKey(tableName);
	}
	
	public Object getSinkId(String tableName, Object sourceId) {
		return TABLE_AND_IDS_MAP.get(tableName).get(sourceId);
	}
	
}
