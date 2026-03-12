package net.mekomsolutions.db.importer.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

public class DataSourceConfig {
	
	@Bean
	@ConfigurationProperties(prefix = "spring.datasource.sink")
	public DataSource sinkDataSource() {
		return DataSourceBuilder.create().build();
	}
	
	@Bean
	public JdbcTemplate sinkJdbcTemplate(@Qualifier("sinkDataSource") DataSource ds) {
		return new JdbcTemplate(ds);
	}
	
	@Bean
	@ConfigurationProperties(prefix = "spring.datasource.source")
	public DataSource sourceDataSource() {
		return DataSourceBuilder.create().build();
	}
	
	@Bean
	public JdbcTemplate sourceJdbcTemplate(@Qualifier("sourceDataSource") DataSource ds) {
		return new JdbcTemplate(ds);
	}
	
	@Bean
	@ConfigurationProperties(prefix = "spring.datasource.batch")
	public DataSource batchDataSource() {
		return DataSourceBuilder.create().build();
	}
	
}
