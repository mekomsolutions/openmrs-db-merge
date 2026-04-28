package net.mekomsolutions.db.importer;

import javax.sql.DataSource;

import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;

public class TestDataSourceConfig {
	
	@Bean(name = "testDatabase", initMethod = "start", destroyMethod = "shutdown")
	public TestDatabase getTestDatabase() {
		return new TestDatabase();
	}
	
	@Bean(name = "testDataSource")
	public DataSource getDataSource(TestDatabase db) {
		return DataSourceBuilder.create().url(db.getJdbcUrl()).username(TestDatabase.TEST_USER)
		        .password(TestDatabase.TEST_PASSWORD).build();
	}
	
}
