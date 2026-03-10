package net.mekomsolutions.db.importer.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

public class DataSourceConfig {
	
	@Primary
	@Bean(name = "sinkDataSource")
	@ConfigurationProperties(prefix = "spring.datasource.sink")
	public DataSource destinationDataSource() {
		return DataSourceBuilder.create().build();
	}
	
	@Primary
	@Bean(name = "sinkJdbcTemplate")
	public JdbcTemplate primaryJdbcTemplate(@Qualifier("sinkDataSource") DataSource ds) {
		return new JdbcTemplate(ds);
	}
	
	@Bean(name = "sourceDataSource")
	@ConfigurationProperties(prefix = "spring.datasource.source")
	public DataSource secondaryDataSource() {
		return DataSourceBuilder.create().build();
	}
	
	@Bean(name = "sourceJdbcTemplate")
	public JdbcTemplate secondaryJdbcTemplate(@Qualifier("sourceDataSource") DataSource ds) {
		return new JdbcTemplate(ds);
	}
	
}
