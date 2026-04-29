package net.mekomsolutions.db.importer;

import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.SqlScriptsTestExecutionListener;

import net.mekomsolutions.db.importer.config.BatchConfig;
import net.mekomsolutions.db.importer.config.DaoConfig;

/**
 * Base class for all tests that require a spring application context and a test database.
 */
@TestExecutionListeners(value = { ResetDbTestExecutionListener.class, SqlScriptsTestExecutionListener.class })
@Import({ TestDataSourceConfig.class, DaoConfig.class, BatchConfig.class })
@TestPropertySource(properties = "tables.exclude.file.path=classpath:exclude_tables.txt")
@TestPropertySource(properties = "batch.read.size=10")
@TestPropertySource(properties = "batch.write.size=10")
@TestPropertySource(properties = "thread.count=10")
@TestPropertySource(properties = "connection.max.pool.size=10")
public abstract class BaseDbBackedTest extends BaseContextTest {}
