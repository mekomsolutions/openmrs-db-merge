package net.mekomsolutions.db.importer;

import java.util.stream.Stream;

import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.lifecycle.Startables;

public class TestDatabase {
	
	public static final String TEST_USER = "test-user";
	
	public static final String TEST_PASSWORD = "test-pass";
	
	public static final String TEST_DB_NAME = "test-db";
	
	public static final MySQLContainer MYSQL_CONTAINER = new MySQLContainer("mysql:8.2.0");
	
	public String getJdbcUrl() {
		return MYSQL_CONTAINER.getJdbcUrl();
	}
	
	public void start() {
		MYSQL_CONTAINER.withDatabaseName(TEST_DB_NAME);
		MYSQL_CONTAINER.withUsername(TEST_USER);
		MYSQL_CONTAINER.withPassword(TEST_PASSWORD);
		
		Startables.deepStart(Stream.of(MYSQL_CONTAINER)).join();
	}
	
	public void shutdown() {
		MYSQL_CONTAINER.stop();
	}
	
}
