package net.mekomsolutions.db.importer.helpers;

import java.util.List;
import java.util.Map;

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
			log.debug("Checking if {} table {} is empty", name, table);
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
	
	/**
	 * Retrieves all rows from the specified database table, returning only the primary key values and
	 * uuids.
	 *
	 * @param tableName the name of the database table to query
	 * @param pkColName the primary key column name
	 * @return a list of rows, where each row is represented as a Map<String, Object>
	 */
	public List<Map<String, Object>> getAllRows(String tableName, String pkColName) {
		if (log.isDebugEnabled()) {
			log.debug("Retrieving {} and uuid for all rows from {} table {}", pkColName, name, tableName);
		}
		
		String query = String.format("SELECT %s,uuid FROM %s", pkColName, tableName);
		try {
			return jdbcTemplate.queryForList(query);
		}
		catch (Exception e) {
			String msg = "Failed to retrieve " + pkColName + " and uuids for rows from " + name + " table " + tableName;
			throw new RuntimeException(msg, e);
		}
	}
	
}
