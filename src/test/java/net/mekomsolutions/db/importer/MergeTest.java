package net.mekomsolutions.db.importer;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

public class MergeTest extends BaseMergeTest {
	
	private static final String QUERY_PERSON = "select * from person where person_id > 1 and uuid != '"
	        + Constants.PHANTOM_UUID + "'";
	
	private static final String QUERY_USER = "select * from users where user_id > 2 and uuid != '" + Constants.PHANTOM_UUID
	        + "'";
	
	private static final String PATIENT_SUB_QUERY = "select person_id from person where person_id != 1 and uuid != '"
	        + Constants.PHANTOM_UUID + "'";
	
	private static final String QUERY_PATIENT = "select * from patient where patient_id in (" + PATIENT_SUB_QUERY + ")";
	
	private static final String QUERY_VISIT = "select * from visit where patient_id in (" + PATIENT_SUB_QUERY + ")";
	
	@Autowired
	@Qualifier("sourceJdbcTemplate")
	private JdbcTemplate sourceJdbcTemplate;
	
	@Autowired
	@Qualifier("sinkJdbcTemplate")
	private JdbcTemplate sinkJdbcTemplate;
	
	@Test
	@Sql({ "classpath:users.sql", "classpath:patient.sql", "classpath:visit.sql", "classpath:encounter.sql" })
	public void shouldMergeAllRowsInAllTables() throws Exception {
		//Tables -> person,users,user_role,user_property,patient,visit,encounter
		List<Map<String, Object>> persons = sinkJdbcTemplate.queryForList("select * from person where person_id > 1");
		List<Map<String, Object>> users = sinkJdbcTemplate.queryForList("select * from users where user_id > 2");
		List<Map<String, Object>> patients = sinkJdbcTemplate.queryForList("select * from patient");
		List<Map<String, Object>> visits = sinkJdbcTemplate.queryForList("select * from visit");
		Assertions.assertTrue(persons.isEmpty());
		Assertions.assertTrue(users.isEmpty());
		Assertions.assertTrue(patients.isEmpty());
		Assertions.assertTrue(visits.isEmpty());
		
		executeJob();
		
		persons = sinkJdbcTemplate.queryForList(QUERY_PERSON);
		users = sinkJdbcTemplate.queryForList(QUERY_USER);
		patients = sinkJdbcTemplate.queryForList(QUERY_PATIENT);
		visits = sinkJdbcTemplate.queryForList(QUERY_VISIT);
		//All source DB persons including the row associated to admin and daemon
		Assertions.assertEquals(11, persons.size());
		//All source DB users including admin, excludes daemon
		Assertions.assertEquals(6, users.size());
		Assertions.assertEquals(5, patients.size());
		Assertions.assertEquals(5, visits.size());
		//TODO Compare sink row values with those from the source
	}
	
}
