package net.mekomsolutions.db.importer.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import liquibase.integration.spring.SpringLiquibase;

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
	public NamedParameterJdbcTemplate sinkNamedParamJdbcTemplate(@Qualifier("sinkDataSource") DataSource ds) {
		return new NamedParameterJdbcTemplate(ds);
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
	
	@Bean
	public JdbcTemplate batchJdbcTemplate(@Qualifier("batchDataSource") DataSource ds) {
		return new JdbcTemplate(ds);
	}
	
	@Bean
	public PlatformTransactionManager batchTxManager(@Qualifier("batchDataSource") DataSource ds) {
		return new JdbcTransactionManager(ds);
	}
	
	@Bean
	public SpringLiquibase springLiquibase(@Qualifier("batchDataSource") DataSource ds) {
		SpringLiquibase liquibase = new SpringLiquibase();
		liquibase.setDataSource(ds);
		liquibase.setChangeLog("liquibase.xml");
		liquibase.setDatabaseChangeLogTable("liquibase_changelog");
		liquibase.setDatabaseChangeLogLockTable("liquibase_changelog_lock");
		return liquibase;
	}
	
}
