package net.mekomsolutions.db.merge;

import static net.mekomsolutions.db.merge.TestDatabase.TEST_PASSWORD;
import static net.mekomsolutions.db.merge.TestDatabase.TEST_USER;

import javax.sql.DataSource;

import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;

public class TestDataSourceConfig {
	
	@Bean("sinkDataSource")
	public DataSource sinkDataSource() {
		return createDataSource("sink_db");
	}
	
	@Bean("sourceDataSource")
	public DataSource sourceDataSource() {
		return createDataSource("source_db");
	}
	
	@Bean("mgtDataSource")
	public DataSource mgtDataSource() {
		return createDataSource(null);
	}
	
	private DataSource createDataSource(String dbName) {
		final String jdbcUrl = TestDatabase.getJdbcUrl(dbName);
		return DataSourceBuilder.create().url(jdbcUrl).username(TEST_USER).password(TEST_PASSWORD).build();
	}
	
}
