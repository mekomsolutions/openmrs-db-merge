package net.mekomsolutions.db.importer;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for interacting with a source database using a JDBC template.
 */
@Component
@Slf4j
public class SourceDbHelper {
	
	protected JdbcTemplate jdbcTemplate;
	
	public SourceDbHelper(@Qualifier("sourceJdbcTemplate") JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}
	
	/**
	 * Retrieves the uuid for the row in a database table that matches the given criteria.
	 *
	 * @param table the name of the database table to query
	 * @param idColumName the name of the column whose value is to be retrieved
	 * @param id the name of the column whose value is to be retrieved
	 * @return the uuid for the row
	 */
	public Object getUuid(String table, Object idColumName, Object id) {
		if (log.isDebugEnabled()) {
			log.debug("Getting uuid for row with {} = {} from table {}", idColumName, id, table);
		}
		
		String query = String.format("SELECT uuid FROM %s WHERE %s = ?", table, idColumName);
		try {
			return jdbcTemplate.queryForObject(query, new Object[] { id }, Object.class);
		}
		catch (Exception e) {
			final String msg = "Failed to get uuid for row with " + idColumName + " " + id + " in table " + table;
			throw new RuntimeException(msg, e);
		}
	}
	
}
