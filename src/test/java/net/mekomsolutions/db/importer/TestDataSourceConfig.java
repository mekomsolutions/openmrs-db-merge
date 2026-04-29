package net.mekomsolutions.db.importer;

import static net.mekomsolutions.db.importer.TestDatabase.TEST_PASSWORD;
import static net.mekomsolutions.db.importer.TestDatabase.TEST_USER;

import javax.sql.DataSource;

import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;

public class TestDataSourceConfig {
	
	@Bean(name = "testDatabase", initMethod = "start", destroyMethod = "shutdown")
	public TestDatabase getTestDatabase() {
		return new TestDatabase();
	}
	
	@Bean("sinkDataSource")
	public DataSource sinkDataSource(TestDatabase testDb) {
		return createDataSource(testDb, "sink_db");
	}
	
	@Bean("sourceDataSource")
	public DataSource sourceDataSource(TestDatabase testDb) {
		return createDataSource(testDb, "source_db");
	}
	
	@Bean("mgtDataSource")
	public DataSource mgtDataSource(TestDatabase testDb) {
		return createDataSource(testDb, null);
	}
	
	private DataSource createDataSource(TestDatabase testDb, String dbName) {
		String jdbcUrl = testDb.getJdbcUrl();
		if (dbName != null) {
			jdbcUrl = jdbcUrl.replace(TestDatabase.MGT_DB_NAME, dbName);
		}
		
		jdbcUrl = jdbcUrl + "?useSSL=false&createDatabaseIfNotExist=true&allowPublicKeyRetrieval=true";
		return DataSourceBuilder.create().url(jdbcUrl).username(TEST_USER).password(TEST_PASSWORD).build();
	}
	
}
