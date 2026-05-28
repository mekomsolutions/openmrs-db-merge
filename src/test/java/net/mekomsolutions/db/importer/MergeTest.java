package net.mekomsolutions.db.importer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.jdbc.Sql;

public class MergeTest extends BaseMergeTest {
	
	private static final String QUERY_PERSON = "select * from person where person_id > 1";
	
	private static final String QUERY_USER = "select * from users where user_id > 2";
	
	private static final String QUERY_PROVIDER = "select * from provider";
	
	private static final String PATIENT_SUB_QUERY = "select person_id from person where person_id != 1";
	
	private static final String QUERY_PATIENT = "select * from patient where patient_id in (" + PATIENT_SUB_QUERY + ")";
	
	private static final String QUERY_VISIT = "select * from visit where patient_id in (" + PATIENT_SUB_QUERY + ")";
	
	private static final String QUERY_ENC = "select * from encounter where patient_id in (" + PATIENT_SUB_QUERY + ")";
	
	private static final String QUERY_USER_PROPS = "select * from user_property";
	
	private static final String QUERY_USER_ROLES = "select * from user_role";
	
	private void verifyRow(Map<String, Object> sinkRow, Table table) {
		final String uuid = (String) sinkRow.get("uuid");
		Map<String, Object> sourceRow;
		if (MergeUtils.isSubclassTable(table.name())) {
			final String pkCol = table.primaryKeys().get(0);
			ForeignKey fk = table.getColumn(pkCol).foreignKey();
			final String parentPkCol = extractor.getTable(fk.referencedTable(), false).primaryKeys().get(0);
			Object sourceParentRowId = getSourceReferencedRowId(sinkRow.get(pkCol), parentPkCol, fk);
			sourceRow = sourceDbHelper.getRow(table.name(), List.of(pkCol), new Object[] { sourceParentRowId });
		} else if (MergeUtils.isExtensionTable(table) || MergeUtils.isMappingTable(table)) {
			List<Object> values = new ArrayList<>(table.primaryKeys().size());
			for (String col : table.primaryKeys()) {
				Object value = sinkRow.get(col);
				ForeignKey fk = table.getColumn(col).foreignKey();
				if (fk != null) {
					value = getSourceReferencedRowId(value, col, fk);
				}
				values.add(value);
			}
			sourceRow = sourceDbHelper.getRow(table.name(), table.primaryKeys(), values.toArray());
		} else {
			sourceRow = sourceDbHelper.getRow(table.name(), List.of("uuid"), new Object[] { uuid });
		}
		
		assertRow(sourceRow, sinkRow, table);
	}
	
	private void verifyRows(List<Map<String, Object>> rows, String tableName) {
		Table table = extractor.getTable(tableName, false);
		rows.forEach(row -> verifyRow(row, table));
	}
	
	@Test
	@Sql({ "classpath:users.sql", "classpath:provider.sql", "classpath:patient.sql", "classpath:visit.sql",
	        "classpath:encounter.sql", "classpath:orders.sql", "classpath:obs.sql", "classpath:user_property.sql",
	        "classpath:user_role.sql" })
	public void shouldMergeAllRowsInAllTables() throws Exception {
		//Tables -> person,users,provider,user_role,user_property,patient,visit,encounter,orders,obs
		List<Map<String, Object>> persons = sinkJdbcTemplate.queryForList("select * from person where person_id > 1");
		List<Map<String, Object>> users = sinkJdbcTemplate.queryForList("select * from users where user_id > 2");
		List<Map<String, Object>> patients = sinkJdbcTemplate.queryForList("select * from patient");
		List<Map<String, Object>> visits = sinkJdbcTemplate.queryForList("select * from visit");
		List<Map<String, Object>> encounters = sinkJdbcTemplate.queryForList("select * from encounter");
		List<Map<String, Object>> userProps = sinkJdbcTemplate.queryForList(QUERY_USER_PROPS);
		List<Map<String, Object>> userRoles = sinkJdbcTemplate.queryForList(QUERY_USER_ROLES);
		List<Map<String, Object>> providers = sinkJdbcTemplate.queryForList(QUERY_PROVIDER);
		Assertions.assertTrue(persons.isEmpty());
		Assertions.assertTrue(users.isEmpty());
		Assertions.assertTrue(providers.isEmpty());
		Assertions.assertTrue(patients.isEmpty());
		Assertions.assertTrue(visits.isEmpty());
		Assertions.assertTrue(encounters.isEmpty());
		Assertions.assertTrue(userProps.isEmpty());
		Assertions.assertTrue(userRoles.isEmpty());
		
		executeJob();
		
		persons = sinkJdbcTemplate.queryForList(QUERY_PERSON);
		users = sinkJdbcTemplate.queryForList(QUERY_USER);
		providers = sinkJdbcTemplate.queryForList(QUERY_PROVIDER);
		patients = sinkJdbcTemplate.queryForList(QUERY_PATIENT);
		visits = sinkJdbcTemplate.queryForList(QUERY_VISIT);
		encounters = sinkJdbcTemplate.queryForList(QUERY_ENC);
		userProps = sinkJdbcTemplate.queryForList(QUERY_USER_PROPS);
		userRoles = sinkJdbcTemplate.queryForList(QUERY_USER_ROLES);
		//All source DB persons including the row associated to admin and daemon
		Assertions.assertEquals(11, persons.size());
		//All source DB users including admin, excludes daemon
		Assertions.assertEquals(6, users.size());
		Assertions.assertEquals(6, providers.size());
		Assertions.assertEquals(5, patients.size());
		Assertions.assertEquals(5, visits.size());
		Assertions.assertEquals(5, encounters.size());
		Assertions.assertEquals(5, userProps.size());
		Assertions.assertEquals(5, userRoles.size());
		verifyRows(persons, "person");
		verifyRows(users, "users");
		verifyRows(providers, "provider");
		verifyRows(patients, "patient");
		verifyRows(visits, "visit");
		verifyRows(encounters, "encounter");
		verifyRows(userProps, "user_property");
		verifyRows(userRoles, "user_role");
	}
	
}
