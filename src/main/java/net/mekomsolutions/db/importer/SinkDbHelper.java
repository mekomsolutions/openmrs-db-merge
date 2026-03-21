package net.mekomsolutions.db.importer;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreatorFactory;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
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
			log.debug("Getting {} for row with {} = {} from sink table {}", columName, filterColumnName, filterColumnValue,
			    table);
		}
		
		String query = String.format("SELECT %s FROM %s WHERE %s = ?", columName, table, filterColumnName);
		try {
			return jdbcTemplate.queryForObject(query, new Object[] { filterColumnValue }, Object.class);
		}
		catch (EmptyResultDataAccessException e) {
			return null;
		}
		catch (Exception e) {
			final String message = "Failed to get " + columName + " value for row with " + filterColumnName + " "
			        + filterColumnValue + " from sink table " + table;
			throw new RuntimeException(message, e);
		}
	}
	
	/**
	 * Inserts a single row into the specified database table.
	 *
	 * @param table the name of the database table where the row will be inserted.
	 * @param columnNames a list of column names that correspond to the table's structure.
	 * @param values an array of objects representing the values for the corresponding columns.
	 * @return the number of rows affected by the insert operation.
	 */
	public Object insertRow(String table, List<String> columnNames, Object[] values) {
		if (log.isDebugEnabled()) {
			log.debug("Inserting a row into table {}", table);
		}
		
		String columns = String.join(",", columnNames);
		String placeholders = columnNames.stream().map(c -> "?").collect(Collectors.joining(","));
		String sql = String.format("INSERT INTO %s (%s) VALUES (%s)", table, columns, placeholders);
		
		try {
			PreparedStatementCreatorFactory pscFactory = new PreparedStatementCreatorFactory(sql);
			pscFactory.setReturnGeneratedKeys(true);
			KeyHolder keyHolder = new GeneratedKeyHolder();
			int insertCount = jdbcTemplate.update(pscFactory.newPreparedStatementCreator(values), keyHolder);
			if (insertCount != 1) {
				throw new RuntimeException("Invalid insert count " + insertCount);
			}
			
			if (keyHolder.getKey() == null) {
				throw new RuntimeException("No auto generated key found after insert");
			} else if (keyHolder.getKeys().size() != 1) {
				throw new RuntimeException("Invalid auto generated key count after insert " + keyHolder.getKeys().size());
			}
			
			if (log.isDebugEnabled()) {
				log.debug("Successfully inserted {} row into table {}: {}", insertCount, table);
			}
			
			return keyHolder.getKey();
		}
		catch (Exception e) {
			final String msg = String.format("Error occurred while inserting a row into table %s: %s", table,
			    e.getMessage());
			throw new RuntimeException(msg, e);
		}
	}
	
}
