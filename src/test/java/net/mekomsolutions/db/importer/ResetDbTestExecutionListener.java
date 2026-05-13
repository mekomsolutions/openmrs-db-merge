package net.mekomsolutions.db.importer;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
		/*ApplicationContext ctx = testContext.getApplicationContext();
		DataSource dataSource = ctx.getBean(DataSource.class);
		LOG.debug("Deleting all data from the test database");
		try (Connection c = dataSource.getConnection()) {
			deleteAllData(c);
		}*/
		
	}
	
	/**
	 * Resets all tables in the test database by deleting all the rows in them
	 *
	 * @param connection JDBC Connection object
	 */
	private void deleteAllData(Connection connection) throws SQLException {
		List<String> tables = getTableNames(connection);
		Statement statement = connection.createStatement();
		try {
			statement.execute(Constants.DISABLE_KEYS);
			for (String tableName : tables) {
				statement.executeUpdate(DELETE + tableName);
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
		ResultSet tables = dbmd.getTables(null, null, null, new String[] { "TABLE" });
		List<String> tableNames = new ArrayList();
		while (tables.next()) {
			tableNames.add(tables.getString("TABLE_NAME"));
		}
		return tableNames;
	}
	
}
