package net.mekomsolutions.db.importer.batch;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import net.mekomsolutions.db.importer.Table;
import net.mekomsolutions.db.importer.helpers.SinkDbHelper;
import net.mekomsolutions.db.importer.helpers.SourceDbHelper;

/**
 * An instance of this class is responsible for caching mappings of foreign key row IDs from a
 * source database to their corresponding row IDs in a sink database, specifically. The mapping is
 * initialized for a predefined list of metadata tables during application startup. This ensures
 * that all metadata IDs in the source database can be translated into their respective IDs in the
 * sink database which greatly improves the speed of the merge. And for some carefully selected
 * tables, as they get the merged, their ID mappings also get added.
 */
@Component
@Slf4j
public class ForeignKeyValueMapCache {
	
	//TODO "users", "provider", "person" to be added at runtime as they get merged.
	//TODO Add patient_identifier_type, person_attribute_type etc.
	private static final List<String> METADATA_TABLES = List.of("visit_type", "encounter_type", "order_type", "form",
	    "location", "care_setting", "order_frequency", "drug", "concept", "concept_name");
	
	private static final Map<String, Map<Object, Object>> TABLE_AND_IDS_MAP = new ConcurrentHashMap<>();
	
	private SourceDbHelper sourceDbHelper;
	
	private SinkDbHelper sinkDbHelper;
	
	public ForeignKeyValueMapCache(SourceDbHelper sourceDbHelper, SinkDbHelper sinkDbHelper) {
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
			
			//For non metadata tables, skip any rows that are not yet written to the sink DB.
			Map<Object, Object> idsMap = sourceIdAndUuidMap.entrySet().stream()
			        .filter(e -> sinkUuidAndIdMap.containsKey(e.getKey()))
			        .collect(Collectors.toMap(e -> e.getKey(), e -> sinkUuidAndIdMap.get(e.getValue())));
			
			TABLE_AND_IDS_MAP.put(tableName, idsMap);
		}
		
		log.info("Done initializing source to sink row id mappings for OpenMRS metadata");
	}
	
	public boolean hasMappings(String tableName) {
		return TABLE_AND_IDS_MAP.containsKey(tableName);
	}
	
	public Object getSinkRowId(String tableName, Object sourceId) {
		return TABLE_AND_IDS_MAP.get(tableName).get(sourceId);
	}
	
}
