package net.mekomsolutions.db.importer;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

/**
 * Custom TestExecutionListener that deletes rows from all tables in the database. Typically, this
 * listener should be configured to run after every test method has executed
 */
public class ResetDbTestExecutionListener extends AbstractTestExecutionListener {
	
	private static final Logger LOG = LoggerFactory.getLogger(ResetDbTestExecutionListener.class);
	
	private static final String DELETE = "DELETE FROM ";
	
	/**
	 * @see AbstractTestExecutionListener#afterTestMethod(TestContext)
	 */
	@Override
	public void afterTestMethod(TestContext testContext) throws Exception {
		LOG.info("Deleting all data from the test databases");
		ApplicationContext ctx = testContext.getApplicationContext();
		/*DataSource ds = ctx.getBean("sourceDataSource", DataSource.class);
		LOG.info("Deleting all data from the source database");
		try (Connection c = ds.getConnection()) {
			deleteAllData(c);
		}*/
		
		DataSource ds = ctx.getBean("sinkDataSource", DataSource.class);
		LOG.info("Deleting all data from the sink database");
		try (Connection c = ds.getConnection()) {
			deleteAllData(c);
		}
		
		ds = ctx.getBean("mgtDataSource", DataSource.class);
		LOG.info("Deleting all data from the failure queue in the mgt database");
		try (Connection c = ds.getConnection(); Statement statement = c.createStatement()) {
			statement.executeUpdate("DELETE FROM " + Constants.FAILED_ITEM_TABLE);
		}
	}
	
	/**
	 * Resets all tables in the test database by deleting all the rows in them
	 *
	 * @param connection JDBC Connection object
	 */
	private void deleteAllData(Connection connection) throws SQLException {
		Statement statement = connection.createStatement();
		try {
			statement.execute(Constants.DISABLE_KEYS);
			for (String tableName : StringUtils.split(TestConstants.TEST_MERGE_TABLES, ",")) {
				String query = DELETE + tableName;
				if (tableName.equals("users"))
					query = query + " WHERE user_id > 2";
				if (tableName.equals("person"))
					query = query + " WHERE person_id > 1";
				statement.executeUpdate(query);
			}
		}
		finally {
			if (statement != null) {
				statement.execute(Constants.ENABLE_KEYS);
				statement.close();
			}
		}
	}
	
	private static List<String> getTableNames(Connection connection) throws SQLException {
		DatabaseMetaData dbmd = connection.getMetaData();
		ResultSet tables = dbmd.getTables(connection.getCatalog(), connection.getSchema(), null, new String[] { "TABLE" });
		List<String> tableNames = new ArrayList();
		while (tables.next()) {
			tableNames.add(tables.getString("TABLE_NAME"));
		}
		return tableNames;
	}
	
}
