package net.mekomsolutions.db.importer;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;

@TestPropertySource(properties = "retry.failed.items=true")
public class RetryBehaviorTest extends BaseMergeTest {
	
	private static final String QUERY_VISIT = "select * from visit";
	
	@Test
	@Sql({ "classpath:users.sql", "classpath:provider.sql", "classpath:patient.sql", "classpath:visit.sql" })
	@Sql(value = { "classpath:failed_import_item.sql" }, config = @SqlConfig(dataSource = "mgtDataSource"))
	public void shouldReprocessFailedRowsInTheFailureQueue() throws Exception {
		final int visitCount = sourceJdbcTemplate.queryForList(QUERY_VISIT).size();
		int failureCount = getFailureCount();
		Assertions.assertEquals(3, failureCount);
		List<Map<String, Object>> visits = sinkJdbcTemplate.queryForList(QUERY_VISIT);
		assertTrue(visits.isEmpty());
		
		executeJob();
		
		Assertions.assertEquals(0, getFailureCount());
		visits = sinkJdbcTemplate.queryForList(QUERY_VISIT);
		Assertions.assertEquals(visitCount, visits.size());
		verifyRows(visits, "visit");
		Assertions.assertEquals(5, MergeUtils.getMaxRowId(jobExplorer, jobRepository, "visit"));
	}
	
}
