package net.mekomsolutions.db.merge.batch;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import net.mekomsolutions.db.merge.Table;
import net.mekomsolutions.db.merge.helpers.SinkDbHelper;
import net.mekomsolutions.db.merge.helpers.SourceDbHelper;

/**
 * An instance of this class is responsible for caching mappings of foreign key row IDs from a
 * source database to their corresponding row IDs in a sink database, specifically. The mapping is
 * initialized for a predefined list of fully cached tables during application startup. This ensures
 * that all database IDs in the source database can be translated into their respective IDs in the
 * sink database which greatly improves the speed of the merge.
 * 
 * @see TemporaryCachingListener
 */
@Component
@Slf4j
public class ForeignKeyValueMapCache {
	
	private static final List<String> FULL_TABLES = List.of("visit_type", "encounter_type", "order_type", "form", "location",
	    "care_setting", "order_frequency", "drug", "concept", "concept_name", "patient_identifier_type",
	    "visit_attribute_type", "encounter_role", "person_attribute_type", "role", "privilege");
	
	private static final List<String> TEMP_TABLES = List.of("users", "provider", "person", "visit", "encounter", "orders",
	    "obs");
	
	private static final Map<String, Map<Object, Object>> TABLE_AND_IDS_MAP = new HashMap<>();
	
	private SourceDbHelper sourceDbHelper;
	
	private SinkDbHelper sinkDbHelper;
	
	public ForeignKeyValueMapCache(SourceDbHelper sourceDbHelper, SinkDbHelper sinkDbHelper) {
		this.sourceDbHelper = sourceDbHelper;
		this.sinkDbHelper = sinkDbHelper;
	}
	
	public void initialize() {
		log.info("Initializing source to sink row id mappings for fully cached tables");
		for (String tableName : FULL_TABLES) {
			addIdMappings(tableName);
		}
		
		log.info("Done initializing source to sink row id mappings for fully cached tables");
	}
	
	public boolean isFullyCachedTable(String tableName) {
		return FULL_TABLES.contains(tableName);
	}
	
	public boolean isTemporaryCachedTable(String tableName) {
		return TEMP_TABLES.contains(tableName);
	}
	
	public boolean hasMappings(String tableName) {
		return TABLE_AND_IDS_MAP.containsKey(tableName);
	}
	
	public Object getSinkRowId(String tableName, Object sourceId) {
		return TABLE_AND_IDS_MAP.get(tableName).get(sourceId);
	}
	
	public void clearTemporaryIdMappings() {
		if (log.isDebugEnabled()) {
			log.debug("Clearing temporarily cached rows");
		}
		
		TEMP_TABLES.forEach(t -> TABLE_AND_IDS_MAP.remove(t));
	}
	
	public void addIdMappings(String tableName) {
		log.info("Caching id mappings for {} table", tableName);
		Table table = sinkDbHelper.getMetadataExtractor().getTable(tableName, true);
		final String idCol = table.primaryKeys().get(0);
		List<Map<String, Object>> sourceRows = sourceDbHelper.getAllRows(tableName, idCol);
		List<Map<String, Object>> sinkRows = sinkDbHelper.getAllRows(tableName, idCol);
		addRowIdMappings(tableName, idCol, sourceRows, sinkRows);
		if (log.isDebugEnabled()) {
			log.debug("Done caching id mappings for {} table", tableName);
		}
	}
	
	public void addTempRowIdMappings(String tableName, Object[] sourceRowIds) {
		if (log.isDebugEnabled()) {
			log.debug("Caching {} temporary row id mappings for {} table", sourceRowIds.length, tableName);
		}
		
		Table table = sinkDbHelper.getMetadataExtractor().getTable(tableName, false);
		final String idCol = table.primaryKeys().get(0);
		List<Map<String, Object>> sourceRows = sourceDbHelper.getRows(tableName, idCol, table.primaryKeys().get(0),
		    sourceRowIds);
		Object[] uuids = sourceRows.stream().map(row -> row.get("uuid")).toArray();
		List<Map<String, Object>> sinkRows = sinkDbHelper.getRows(tableName, idCol, "uuid", uuids);
		addRowIdMappings(tableName, idCol, sourceRows, sinkRows);
		if (log.isDebugEnabled()) {
			log.debug("Done caching temporary row id mappings for {} table", tableName);
		}
	}
	
	/**
	 * Clears the cache. This method is currently ONLY used in tests to ensure no stale or outdated data
	 * persists in the map between tests.
	 */
	public void clearCache() {
		clearTemporaryIdMappings();
		TABLE_AND_IDS_MAP.clear();
	}
	
	private void addRowIdMappings(String tableName, String primaryKeyColumn, List<Map<String, Object>> sourceRows,
	                              List<Map<String, Object>> sinkRows) {
		
		Map<Object, String> sourceIdAndUuidMap = sourceRows.stream().collect(HashMap::new,
		    (map, row) -> map.put(row.get(primaryKeyColumn), row.get("uuid").toString()), HashMap::putAll);
		Map<String, Object> sinkUuidAndIdMap = sinkRows.stream().collect(HashMap::new,
		    (map, row) -> map.put(row.get("uuid").toString(), row.get(primaryKeyColumn)), HashMap::putAll);
		//Skip any rows that are not yet written to the sink DB, this typically applies to temporarily cached tables.
		Map<Object, Object> idsMap = sourceIdAndUuidMap.entrySet().stream()
		        .filter(e -> sinkUuidAndIdMap.containsKey(e.getValue()))
		        .collect(Collectors.toMap(e -> e.getKey(), e -> sinkUuidAndIdMap.get(e.getValue())));
		TABLE_AND_IDS_MAP.computeIfAbsent(tableName, m -> new HashMap<>()).putAll(idsMap);
	}
	
}
