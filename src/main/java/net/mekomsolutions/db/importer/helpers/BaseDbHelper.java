package net.mekomsolutions.db.importer.helpers;

import org.springframework.jdbc.core.JdbcTemplate;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class BaseDbHelper {
	
	protected String name;
	
	protected JdbcTemplate jdbcTemplate;
	
	public BaseDbHelper(String name, JdbcTemplate jdbcTemplate) {
		this.name = name;
		this.jdbcTemplate = jdbcTemplate;
	}
	
	/**
	 * Checks if the specified table is empty.
	 *
	 * @param table the name of the database table to check
	 * @return true if the table is empty, false otherwise
	 */
	public boolean isTableEmpty(String table) {
		if (log.isDebugEnabled()) {
			log.debug("Checking if " + name + " table {} is empty", table);
		}
		
		String query = String.format("SELECT COUNT(*) FROM %s", table);
		try {
			return jdbcTemplate.queryForObject(query, Integer.class) == 0;
		}
		catch (Exception e) {
			String msg = "Failed to check if " + name + " table " + table + " is empty";
			throw new RuntimeException(msg, e);
		}
	}
	
}
