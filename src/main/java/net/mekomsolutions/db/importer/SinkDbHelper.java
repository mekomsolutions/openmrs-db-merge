package net.mekomsolutions.db.importer;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for interacting with a sink database using a JDBC template.
 */
@Component
@Slf4j
public class SinkDbHelper {
	
	protected JdbcTemplate jdbcTemplate;
	
	public SinkDbHelper(@Qualifier("sinkJdbcTemplate") JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}
	
	/**
	 * Retrieves the column value for the row in a database table that matches the given filter
	 * criteria.
	 *
	 * @param table the name of the database table to query
	 * @param columName the name of the column whose value is to be retrieved
	 * @param filterColumnName the name of the column used as a filter criteria
	 * @param filterColumnValue the value against which the filter column is matched
	 * @return the value of the specified column (idColumName) for the row that matches the filter
	 *         criteria
	 */
	public Object getColumnValue(String table, String columName, String filterColumnName, Object filterColumnValue) {
		if (log.isDebugEnabled()) {
			log.debug("Getting {} for row with {} = {} from table {}", columName, filterColumnName, filterColumnValue,
			    table);
		}
		
		String query = String.format("SELECT %s FROM %s WHERE %s = ?", columName, table, filterColumnName);
		try {
			return jdbcTemplate.queryForObject(query, new Object[] { filterColumnValue }, Object.class);
		}
		catch (Exception e) {
			final String message = "Failed to get " + columName + " for row with " + filterColumnName + " "
			        + filterColumnValue + " from table " + table;
			throw new RuntimeException(message, e);
		}
	}
	
}
