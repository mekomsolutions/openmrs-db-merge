package net.mekomsolutions.db.importer;

import java.util.stream.Stream;

import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.lifecycle.Startables;

public class TestDatabase {
	
	public static final String TEST_USER = "root";
	
	public static final String TEST_PASSWORD = "test";
	
	public static final String MGT_DB_NAME = "mgt_db";
	
	public static final MariaDBContainer DB_CONTAINER = new MariaDBContainer("mariadb:10.3.39");
	
	public Integer getMysqlPort() {
		return DB_CONTAINER.getMappedPort(3306);
	}
	
	public String getJdbcUrl() {
		return DB_CONTAINER.getJdbcUrl();
	}
	
	public void start() {
		DB_CONTAINER.withDatabaseName(MGT_DB_NAME);
		DB_CONTAINER.withEnv("MARIADB_ROOT_PASSWORD", TEST_PASSWORD);
		Startables.deepStart(Stream.of(DB_CONTAINER)).join();
	}
	
	public void shutdown() {
		DB_CONTAINER.stop();
	}
	
}
