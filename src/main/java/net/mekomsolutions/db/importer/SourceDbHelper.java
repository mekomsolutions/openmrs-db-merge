package net.mekomsolutions.db.importer;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.EmptyResultDataAccessException;
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
	 * Checks if the specified table is empty.
	 *
	 * @param table the name of the database table to check
	 * @return true if the table is empty, false otherwise
	 */
	public boolean isTableEmpty(String table) {
		if (log.isDebugEnabled()) {
			log.debug("Checking if source table {} is empty", table);
		}
		
		String query = String.format("SELECT COUNT(*) FROM %s", table);
		try {
			return jdbcTemplate.queryForObject(query, Integer.class) == 0;
		}
		catch (Exception e) {
			String msg = "Failed to check if source table " + table + " is empty";
			throw new RuntimeException(msg, e);
		}
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
			log.debug("Getting uuid for row with {} = {} from source table {}", idColumName, id, table);
		}
		
		String query = String.format("SELECT uuid FROM %s WHERE %s = ?", table, idColumName);
		try {
			return jdbcTemplate.queryForObject(query, new Object[] { id }, Object.class);
		}
		catch (Exception e) {
			String msg = "Failed to get uuid for row with " + idColumName + " " + id + " from source table " + table;
			throw new RuntimeException(msg, e);
		}
	}
	
	/**
	 * Retrieves a single row from a database table that matches the specified column values.
	 *
	 * @param tableName the name of the database table to query
	 * @param columnNames the names of the column to match the value against
	 * @param columnValues the values to match in the specified column
	 * @return a map representing the row if found otherwise or null
	 */
	public Map<String, Object> getRow(String tableName, List<String> columnNames, Object[] columnValues) {
		if (log.isDebugEnabled()) {
			log.debug("Fetching row from source table {} where {} = {}", tableName, columnNames, columnValues);
		}
		
		String query = String.format("SELECT * FROM %s WHERE ", tableName);
		query += columnNames.stream().map(c -> c + " = ?").collect(Collectors.joining(" AND "));
		try {
			return jdbcTemplate.queryForMap(query, columnValues);
		}
		catch (EmptyResultDataAccessException e) {
			return null;
		}
		catch (Exception e) {
			List<Object> valueList = List.of(columnValues);
			final String msg = "Failed to fetch row from source table " + tableName + " where " + columnNames + " = "
			        + valueList;
			throw new RuntimeException(msg, e);
		}
	}
}
