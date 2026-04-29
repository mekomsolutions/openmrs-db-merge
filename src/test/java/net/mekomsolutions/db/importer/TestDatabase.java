package net.mekomsolutions.db.importer;

import java.util.stream.Stream;

import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.lifecycle.Startables;

public class TestDatabase {
	
	public static final String TEST_USER = "root";
	
	public static final String TEST_PASSWORD = "test";
	
	public static final String MGT_DB_NAME = "mgt_db";
	
	public static final MySQLContainer MYSQL_CONTAINER = new MySQLContainer("mysql:8.2.0");
	
	public Integer getMysqlPort() {
		return MYSQL_CONTAINER.getMappedPort(3306);
	}
	
	public String getJdbcUrl() {
		return MYSQL_CONTAINER.getJdbcUrl();
	}
	
	public void start() {
		MYSQL_CONTAINER.withDatabaseName(MGT_DB_NAME);
		MYSQL_CONTAINER.withEnv("MYSQL_ROOT_PASSWORD", TEST_PASSWORD);
		Startables.deepStart(Stream.of(MYSQL_CONTAINER)).join();
	}
	
	public void shutdown() {
		MYSQL_CONTAINER.stop();
	}
	
}
