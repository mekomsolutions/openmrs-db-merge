package net.mekomsolutions.db.importer;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import net.mekomsolutions.db.importer.helpers.SinkDbHelper;
import net.mekomsolutions.db.importer.helpers.SourceDbHelper;

public class MergeTest extends BaseMergeTest {
	
	private static final String QUERY_PERSON = "select * from person where person_id > 1 and uuid != '"
	        + Constants.PHANTOM_UUID + "'";
	
	private static final String QUERY_USER = "select * from users where user_id > 2 and uuid != '" + Constants.PHANTOM_UUID
	        + "'";
	
	private static final String PATIENT_SUB_QUERY = "select person_id from person where person_id != 1 and uuid != '"
	        + Constants.PHANTOM_UUID + "'";
	
	private static final String QUERY_PATIENT = "select * from patient where patient_id in (" + PATIENT_SUB_QUERY + ")";
	
	private static final String QUERY_VISIT = "select * from visit where patient_id in (" + PATIENT_SUB_QUERY + ")";
	
	private static final String QUERY_ENC = "select * from encounter where patient_id in (" + PATIENT_SUB_QUERY + ")";
	
	@Autowired
	@Qualifier("sourceJdbcTemplate")
	private JdbcTemplate sourceJdbcTemplate;
	
	@Autowired
	@Qualifier("sinkJdbcTemplate")
	private JdbcTemplate sinkJdbcTemplate;
	
	@Autowired
	private SourceDbHelper sourceDbHelper;
	
	@Autowired
	private SinkDbHelper sinkDbHelper;
	
	@Autowired
	@Qualifier("sourceExtractor")
	private MetadataExtractor extractor;
	
	private void assertRow(Map<String, Object> expected, Map<String, Object> actual, Table table) {
		Assertions.assertEquals(expected.size(), actual.size(), "Column size mismatch");
		for (Map.Entry<String, Object> e : expected.entrySet()) {
			final String pkCol = table.primaryKeys().get(0);
			Object id = actual.get(pkCol);
			if (e.getKey().equalsIgnoreCase(pkCol)) {
				continue;
			} else if (table.getColumn(e.getKey()).foreignKey() != null) {
				//TODO Compare uuids of reference columns
				continue;
			}
			
			Object expectedValue = e.getValue();
			if (table.name().equalsIgnoreCase("users") && expected.get(pkCol) == Integer.valueOf(1)) {
				if (e.getKey().equalsIgnoreCase("retired")) {
					expectedValue = true;
				} else if (e.getKey().equalsIgnoreCase("retired_by")) {
					expectedValue = MergeUtils.getDaemonUserId(sinkDbHelper);
				} else if (e.getKey().equalsIgnoreCase("retire_reason")) {
					expectedValue = Constants.RETIRE_REASON;
				} else if (e.getKey().equalsIgnoreCase("date_retired")) {
					Assertions.assertNotNull(actual.get(e.getKey()), "Date retired is not set for retired admin user");
					continue;
				}
			}
			
			final String msg = "Incorrect value for column: " + e.getKey() + " for row with " + pkCol + ": " + id
			        + " in table: " + table.name();
			Assertions.assertEquals(expectedValue, actual.get(e.getKey()), msg);
		}
		
	}
	
	private void verifyRow(Map<String, Object> sinkRow, Table table) {
		final String uuid = (String) sinkRow.get("uuid");
		Map<String, Object> sourceRow;
		if (!MergeUtils.isSubclassTable(table.name())) {
			sourceRow = sourceDbHelper.getRow(table.name(), List.of("uuid"), new Object[] { uuid });
		} else {
			final String pkCol = table.primaryKeys().get(0);
			ForeignKey fk = table.getColumn(pkCol).foreignKey();
			Table refTable = extractor.getTable(fk.referencedTable());
			String rowUuid = (String) sinkDbHelper.getColumnValue(fk.referencedTable(), "uuid", fk.referencedColumn(),
			    sinkRow.get(pkCol));
			Map<String, Object> sourceParentRow = sourceDbHelper.getRow(fk.referencedTable(), List.of("uuid"),
			    new Object[] { rowUuid });
			sourceRow = sourceDbHelper.getRow(table.name(), List.of(pkCol),
			    new Object[] { sourceParentRow.get(refTable.primaryKeys().get(0)) });
		}
		
		assertRow(sourceRow, sinkRow, table);
	}
	
	private void verifyRows(List<Map<String, Object>> rows, String tableName) {
		Table table = extractor.getTable(tableName);
		rows.forEach(row -> verifyRow(row, table));
	}
	
	@Test
	@Sql({ "classpath:users.sql", "classpath:patient.sql", "classpath:visit.sql", "classpath:encounter.sql" })
	public void shouldMergeAllRowsInAllTables() throws Exception {
		//Tables -> person,users,user_role,user_property,patient,visit,encounter
		List<Map<String, Object>> persons = sinkJdbcTemplate.queryForList("select * from person where person_id > 1");
		List<Map<String, Object>> users = sinkJdbcTemplate.queryForList("select * from users where user_id > 2");
		List<Map<String, Object>> patients = sinkJdbcTemplate.queryForList("select * from patient");
		List<Map<String, Object>> visits = sinkJdbcTemplate.queryForList("select * from visit");
		List<Map<String, Object>> encounters = sinkJdbcTemplate.queryForList("select * from encounter");
		Assertions.assertTrue(persons.isEmpty());
		Assertions.assertTrue(users.isEmpty());
		Assertions.assertTrue(patients.isEmpty());
		Assertions.assertTrue(visits.isEmpty());
		Assertions.assertTrue(encounters.isEmpty());
		
		executeJob();
		
		persons = sinkJdbcTemplate.queryForList(QUERY_PERSON);
		users = sinkJdbcTemplate.queryForList(QUERY_USER);
		patients = sinkJdbcTemplate.queryForList(QUERY_PATIENT);
		visits = sinkJdbcTemplate.queryForList(QUERY_VISIT);
		encounters = sinkJdbcTemplate.queryForList(QUERY_ENC);
		//All source DB persons including the row associated to admin and daemon
		Assertions.assertEquals(11, persons.size());
		//All source DB users including admin, excludes daemon
		Assertions.assertEquals(6, users.size());
		Assertions.assertEquals(5, patients.size());
		Assertions.assertEquals(5, visits.size());
		Assertions.assertEquals(5, encounters.size());
		verifyRows(persons, "person");
		verifyRows(users, "users");
		verifyRows(patients, "patient");
		verifyRows(visits, "visit");
		verifyRows(encounters, "encounter");
	}
	
}
