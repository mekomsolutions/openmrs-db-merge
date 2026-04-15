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

import com.zaxxer.hikari.HikariDataSource;

import liquibase.integration.spring.SpringLiquibase;
import net.mekomsolutions.db.importer.ImportUtils;

public class DataSourceConfig {
	
	@Bean
	@ConfigurationProperties(prefix = "spring.datasource.sink")
	public DataSource sinkDataSource() {
		HikariDataSource ds = ((HikariDataSource) DataSourceBuilder.create().build());
		ds.setMaximumPoolSize(ImportUtils.getDefaultThreadCount());
		return ds;
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
		HikariDataSource ds = ((HikariDataSource) DataSourceBuilder.create().build());
		ds.setMaximumPoolSize(ImportUtils.getDefaultThreadCount());
		return ds;
	}
	
	@Bean
	public JdbcTemplate sourceJdbcTemplate(@Qualifier("sourceDataSource") DataSource ds) {
		return new JdbcTemplate(ds);
	}
	
	@Bean
	@ConfigurationProperties(prefix = "spring.datasource.mgt")
	public DataSource mgtDataSource() {
		HikariDataSource ds = ((HikariDataSource) DataSourceBuilder.create().build());
		ds.setMaximumPoolSize(ImportUtils.getDefaultThreadCount());
		return ds;
	}
	
	@Bean
	public JdbcTemplate mgtJdbcTemplate(@Qualifier("mgtDataSource") DataSource ds) {
		return new JdbcTemplate(ds);
	}
	
	@Bean
	public PlatformTransactionManager mgtTxManager(@Qualifier("mgtDataSource") DataSource ds) {
		return new JdbcTransactionManager(ds);
	}
	
	@Bean
	public PlatformTransactionManager sinkTxManager(@Qualifier("sinkDataSource") DataSource ds) {
		return new JdbcTransactionManager(ds);
	}
	
	@Bean
	public SpringLiquibase springLiquibase(@Qualifier("mgtDataSource") DataSource ds) {
		SpringLiquibase liquibase = new SpringLiquibase();
		liquibase.setDataSource(ds);
		liquibase.setChangeLog("liquibase.xml");
		liquibase.setDatabaseChangeLogTable("liquibase_changelog");
		liquibase.setDatabaseChangeLogLockTable("liquibase_changelog_lock");
		return liquibase;
	}
	
}
