package net.mekomsolutions.db.importer;

import static net.mekomsolutions.db.importer.Constants.FAILED_ITEM_TABLE;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.jdbc.Sql;

public class FailureBehaviorTest extends BaseMergeTest {
	
	private static final String QUERY_VISIT = "select * from visit";
	
	private int getFailureCount() {
		return mgtJdbcTemplate.queryForObject("SELECT count(*) FROM " + FAILED_ITEM_TABLE, Integer.class);
	}
	
	private boolean hasFailure(String tableName, Object identifier) {
		String query = "SELECT count(*) FROM " + FAILED_ITEM_TABLE + " WHERE table_name = '" + tableName
		        + "' AND identifier = '" + identifier + "'";
		return mgtJdbcTemplate.queryForObject(query, Integer.class) > 0;
	}
	
	@Test
	@Sql({ "classpath:users.sql", "classpath:provider.sql", "classpath:patient.sql", "classpath:visit.sql" })
	public void shouldAddFailedRowsToTheFailureQueue() throws Exception {
		List<Map<String, Object>> sourceVisits = sourceJdbcTemplate.queryForList(QUERY_VISIT);
		Assertions.assertFalse(sourceVisits.isEmpty());
		List<Map<String, Object>> sinkVisits = sinkJdbcTemplate.queryForList(QUERY_VISIT);
		assertTrue(sinkVisits.isEmpty());
		//We delete all visit_types to force failures for all visit rows
		sinkJdbcTemplate.update("DELETE FROM visit_type");
		
		executeJob();
		
		sinkVisits = sinkJdbcTemplate.queryForList(QUERY_VISIT);
		assertTrue(sinkVisits.isEmpty());
		Assertions.assertEquals(sourceVisits.size(), getFailureCount());
		sourceVisits.forEach(v -> assertTrue(hasFailure("visit", v.get("visit_id"))));
	}
	
}
