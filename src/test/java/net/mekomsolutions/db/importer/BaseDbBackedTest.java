package net.mekomsolutions.db.importer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.SqlScriptsTestExecutionListener;

import net.mekomsolutions.db.importer.config.DaoConfig;

/**
 * Base class for all tests that require a spring application context and a test database.
 */
@TestExecutionListeners(value = { ResetDbTestExecutionListener.class, SqlScriptsTestExecutionListener.class })
@Import({ TestDataSourceConfig.class, DaoConfig.class })
@TestPropertySource(properties = "thread.count=10")
@TestPropertySource(properties = "connection.max.pool.size=10")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class BaseDbBackedTest extends BaseContextTest {
	
	@BeforeAll
	public static void setupClass() throws Exception {
		TestDatabase.start();
	}
	
	@AfterAll
	public static void tearDownClass() {
		TestDatabase.shutdown();
	}
	
}
