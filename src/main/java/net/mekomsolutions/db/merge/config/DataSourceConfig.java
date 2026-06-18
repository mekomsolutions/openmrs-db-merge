package net.mekomsolutions.db.merge.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;

import com.zaxxer.hikari.HikariDataSource;

import net.mekomsolutions.db.merge.Constants;
import net.mekomsolutions.db.merge.MergeUtils;

public class DataSourceConfig {
	
	@Value("${" + Constants.PROP_MAX_CONN_POOL_SIZE + "}")
	private Integer maxSize;
	
	@Bean
	@ConfigurationProperties(prefix = "spring.datasource.sink")
	public DataSource sinkDataSource() {
		HikariDataSource ds = ((HikariDataSource) DataSourceBuilder.create().build());
		ds.setMaximumPoolSize(MergeUtils.getMaxConnectionSize(maxSize));
		return ds;
	}
	
	@Bean
	@ConfigurationProperties(prefix = "spring.datasource.source")
	public DataSource sourceDataSource() {
		HikariDataSource ds = ((HikariDataSource) DataSourceBuilder.create().build());
		ds.setMaximumPoolSize(MergeUtils.getMaxConnectionSize(maxSize));
		return ds;
	}
	
	@Bean
	@ConfigurationProperties(prefix = "spring.datasource.mgt")
	public DataSource mgtDataSource() {
		HikariDataSource ds = ((HikariDataSource) DataSourceBuilder.create().build());
		ds.setMaximumPoolSize(MergeUtils.getMaxConnectionSize(maxSize));
		return ds;
	}
	
}
