package net.mekomsolutions.db.importer;

import static org.testcontainers.utility.MountableFile.forClasspathResource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.stream.Stream;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.lifecycle.Startables;

public class TestDatabase {
	
	public static final String TEST_USER = "root";
	
	public static final String TEST_PASSWORD = "test";
	
	public static final String MGT_DB_NAME = "mgt_db";
	
	public static final MariaDBContainer CONTAINER = new MariaDBContainer("mariadb:10.3.39");
	
	public static final String ENTRY_POINT_PATH = "/docker-entrypoint-initdb.d/";
	
	public String getJdbcUrl() {
		return CONTAINER.getJdbcUrl();
	}
	
	public void start() throws Exception {
		CONTAINER.withDatabaseName(MGT_DB_NAME);
		CONTAINER.withEnv("MARIADB_ROOT_PASSWORD", TEST_PASSWORD);
		CONTAINER.withCopyFileToContainer(forClasspathResource("create_dbs.sql"), ENTRY_POINT_PATH + "create_dbs.sql");
		Startables.deepStart(Stream.of(CONTAINER)).join();
		createSchema("sink_db");
		createSchema("source_db");
		runScript("sink_db", "initial_sink.sql");
		runScript("source_db", "initial_source.sql");
	}
	
	public String getJdbcUrl(String dbName) {
		String jdbcUrl = getJdbcUrl();
		if (dbName != null) {
			jdbcUrl = jdbcUrl.replace(TestDatabase.MGT_DB_NAME, dbName);
		}
		
		return jdbcUrl + "?useSSL=false&createDatabaseIfNotExist=true&allowPublicKeyRetrieval=true";
	}
	
	public void shutdown() {
		CONTAINER.stop();
	}
	
	private void createSchema(String dbName) throws Exception {
		runScript(dbName, "schema.sql");
	}
	
	private void runScript(String dbName, String fileName) throws Exception {
		try (Connection c = DriverManager.getConnection(getJdbcUrl(dbName), TEST_USER, TEST_PASSWORD)) {
			ScriptUtils.executeSqlScript(c, new ClassPathResource(fileName));
		}
		
	}
	
}
