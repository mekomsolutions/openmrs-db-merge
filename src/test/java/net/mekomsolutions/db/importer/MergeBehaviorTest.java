package net.mekomsolutions.db.importer;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.jdbc.Sql;

public class MergeBehaviorTest extends BaseMergeTest {
	
	private static final String QUERY_PERSON = "select * from person where person_id > 1";
	
	private static final String QUERY_USER = "select * from users where user_id > 2";
	
	private static final String QUERY_PROVIDER = "select * from provider";
	
	private static final String QUERY_PATIENT = "select * from patient";
	
	private static final String QUERY_VISIT = "select * from visit";
	
	private static final String QUERY_ENC = "select * from encounter";
	
	private static final String QUERY_ORDERS = "select * from orders";
	
	private static final String QUERY_OBS = "select * from obs";
	
	private static final String QUERY_USER_PROPS = "select * from user_property";
	
	private static final String QUERY_USER_ROLES = "select * from user_role";
	
	@Test
	@Sql({ "classpath:users.sql", "classpath:provider.sql", "classpath:patient.sql", "classpath:visit.sql",
	        "classpath:encounter.sql", "classpath:orders.sql", "classpath:obs.sql", "classpath:user_property.sql",
	        "classpath:user_role.sql" })
	public void shouldMergeAllRowsInAllTables() throws Exception {
		//Tables -> person,users,provider,user_role,user_property,patient,visit,encounter,orders,obs
		List<Map<String, Object>> persons = sinkJdbcTemplate.queryForList("select * from person where person_id > 1");
		List<Map<String, Object>> users = sinkJdbcTemplate.queryForList("select * from users where user_id > 2");
		List<Map<String, Object>> patients = sinkJdbcTemplate.queryForList(QUERY_PATIENT);
		List<Map<String, Object>> visits = sinkJdbcTemplate.queryForList(QUERY_VISIT);
		List<Map<String, Object>> encounters = sinkJdbcTemplate.queryForList(QUERY_ENC);
		List<Map<String, Object>> orders = sinkJdbcTemplate.queryForList(QUERY_ORDERS);
		List<Map<String, Object>> obs = sinkJdbcTemplate.queryForList(QUERY_OBS);
		List<Map<String, Object>> userProps = sinkJdbcTemplate.queryForList(QUERY_USER_PROPS);
		List<Map<String, Object>> userRoles = sinkJdbcTemplate.queryForList(QUERY_USER_ROLES);
		List<Map<String, Object>> providers = sinkJdbcTemplate.queryForList(QUERY_PROVIDER);
		Assertions.assertTrue(persons.isEmpty());
		Assertions.assertTrue(users.isEmpty());
		Assertions.assertTrue(providers.isEmpty());
		Assertions.assertTrue(patients.isEmpty());
		Assertions.assertTrue(visits.isEmpty());
		Assertions.assertTrue(encounters.isEmpty());
		Assertions.assertTrue(orders.isEmpty());
		Assertions.assertTrue(obs.isEmpty());
		Assertions.assertTrue(userProps.isEmpty());
		Assertions.assertTrue(userRoles.isEmpty());
		Timestamp startDatetime = Timestamp.valueOf(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
		
		executeJob();
		
		persons = sinkJdbcTemplate.queryForList(QUERY_PERSON);
		users = sinkJdbcTemplate.queryForList(QUERY_USER);
		providers = sinkJdbcTemplate.queryForList(QUERY_PROVIDER);
		patients = sinkJdbcTemplate.queryForList(QUERY_PATIENT);
		visits = sinkJdbcTemplate.queryForList(QUERY_VISIT);
		encounters = sinkJdbcTemplate.queryForList(QUERY_ENC);
		orders = sinkJdbcTemplate.queryForList(QUERY_ORDERS);
		obs = sinkJdbcTemplate.queryForList(QUERY_OBS);
		userProps = sinkJdbcTemplate.queryForList(QUERY_USER_PROPS);
		userRoles = sinkJdbcTemplate.queryForList(QUERY_USER_ROLES);
		//All source DB persons including the row associated to admin and daemon
		Assertions.assertEquals(11, persons.size());
		//All source DB users including admin, excludes daemon
		Assertions.assertEquals(6, users.size());
		final String sourceAdminUuid = "1ea1621d-45a5-11f1-9ef0-0242ac140004";
		Map<String, Object> mergedAdmin = sinkDbHelper.getRow("users", List.of("uuid"), new Object[] { sourceAdminUuid });
		Assertions.assertEquals(true, mergedAdmin.get("retired"));
		Assertions.assertEquals(2, mergedAdmin.get("retired_by"));
		Timestamp dateRetired = (Timestamp) mergedAdmin.get("date_retired");
		Assertions.assertTrue(dateRetired.equals(startDatetime) || dateRetired.after(startDatetime));
		Assertions.assertEquals(Constants.RETIRE_REASON, mergedAdmin.get("retire_reason"));
		Assertions.assertEquals(6, providers.size());
		Assertions.assertEquals(5, patients.size());
		Assertions.assertEquals(5, visits.size());
		Assertions.assertEquals(5, encounters.size());
		Assertions.assertEquals(5, orders.size());
		Assertions.assertEquals(8, obs.size());
		Assertions.assertEquals(5, userProps.size());
		Assertions.assertEquals(5, userRoles.size());
		verifyRows(persons, "person");
		verifyRows(users, "users");
		verifyRows(providers, "provider");
		verifyRows(patients, "patient");
		verifyRows(visits, "visit");
		verifyRows(encounters, "encounter");
		verifyRows(orders, "orders");
		verifyRows(obs, "obs");
		verifyRows(userProps, "user_property");
		verifyRows(userRoles, "user_role");
	}
	
}
