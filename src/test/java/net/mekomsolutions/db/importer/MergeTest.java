package net.mekomsolutions.db.importer;

import java.sql.Timestamp;
import java.time.LocalDateTime;
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
	
	private static final Timestamp TIMESTAMP = Timestamp.valueOf(LocalDateTime.now());
	
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
	
	private void assertRow(Map<String, Object> sourceRow, Map<String, Object> sinkRow, Table table) {
		Assertions.assertEquals(sourceRow.size(), sinkRow.size(), "Column size mismatch");
		for (Map.Entry<String, Object> e : sourceRow.entrySet()) {
			final String col = e.getKey();
			final String pkCol = table.primaryKeys().get(0);
			Object sinkId = sinkRow.get(pkCol);
			Object sourceValue = e.getValue();
			Object sinkValue = sinkRow.get(col);
			if (e.getKey().equalsIgnoreCase(pkCol)) {
				continue;
			} else if (sourceValue != null && table.getColumn(col).foreignKey() != null) {
				//Database ids will be different so instead compare uuids of the referenced rows.
				ForeignKey fk = table.getColumn(col).foreignKey();
				sourceValue = sourceDbHelper.getUuid(fk.referencedTable(), fk.referencedColumn(), sourceRow.get(col));
				sinkValue = sinkDbHelper.getColumnValue(fk.referencedTable(), "uuid", fk.referencedColumn(),
				    sinkRow.get(col));
			}
			
			if (table.name().equalsIgnoreCase("users") && sourceRow.get(pkCol) == Integer.valueOf(1)) {
				if (col.equalsIgnoreCase("retired")) {
					sourceValue = true;
				} else if (col.equalsIgnoreCase("retired_by")) {
					sourceValue = MergeUtils.getDaemonUserId(sinkDbHelper);
				} else if (col.equalsIgnoreCase("retire_reason")) {
					sourceValue = Constants.RETIRE_REASON;
				} else if (col.equalsIgnoreCase("date_retired")) {
					Timestamp dateRetired = (Timestamp) sinkValue;
					Assertions.assertTrue(dateRetired.after(TIMESTAMP),
					    "Date retired in sink users table for merged admin user should be set to current timestamp");
					continue;
				}
			}
			
			final String msg = "Incorrect value for column: " + col + " for row with " + pkCol + ": " + sinkId
			        + " in sink table: " + table.name();
			Assertions.assertEquals(sourceValue, sinkValue, msg);
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
