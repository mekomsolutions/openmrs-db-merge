package net.mekomsolutions.db.importer;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreatorFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
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
	
	private static final String USER_UNIQUE_COLUMNS = "uniqueCols";
	
	private static final String USER_EXISTS_QUERY = "SELECT COUNT(*) FROM users WHERE username IN (:" + USER_UNIQUE_COLUMNS
	        + ") OR system_id IN (:" + USER_UNIQUE_COLUMNS + ")";
	
	protected JdbcTemplate jdbcTemplate;
	
	protected NamedParameterJdbcTemplate namedParamJdbcTemplate;
	
	protected MetadataExtractor metadataExtractor;
	
	public SinkDbHelper(@Qualifier("sinkJdbcTemplate") JdbcTemplate jdbcTemplate,
	    NamedParameterJdbcTemplate namedParamJdbcTemplate, MetadataExtractor metadataExtractor) {
		this.jdbcTemplate = jdbcTemplate;
		this.namedParamJdbcTemplate = namedParamJdbcTemplate;
		this.metadataExtractor = metadataExtractor;
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
	 * @param tableName the name of the database table where the row will be inserted.
	 * @param columnNames a list of column names that correspond to the table's structure.
	 * @param values an array of objects representing the values for the corresponding columns.
	 * @return the number of rows affected by the insert operation.
	 */
	public Object insertRow(String tableName, List<String> columnNames, Object[] values) {
		if (log.isDebugEnabled()) {
			log.debug("Inserting a row into table {}", tableName);
		}
		
		String columns = String.join(",", columnNames);
		String placeholders = columnNames.stream().map(c -> "?").collect(Collectors.joining(","));
		String sqlTemplate = "INSERT INTO %s (%s) VALUES (%s)";
		boolean isSubclassTable = ImportUtils.isSubclassTable(tableName);
		Object parentRowId = null;
		if (isSubclassTable) {
			//Primary key values for subclass tables are not auto generated but instead FKs to the parent table, 
			//so we need to update the row if it already exists.
			Table table = metadataExtractor.getTable(tableName);
			final String pkColName = table.primaryKeys().get(0);
			String updateCols = columnNames.stream().filter(c -> !c.equals(pkColName)).map(c -> c + " = r." + c)
			        .collect(Collectors.joining(","));
			sqlTemplate += " AS r ON DUPLICATE KEY UPDATE " + updateCols;
			parentRowId = values[columnNames.indexOf(pkColName)];
		}
		
		String sql = String.format(sqlTemplate, tableName, columns, placeholders);
		try {
			PreparedStatementCreatorFactory pscFactory = new PreparedStatementCreatorFactory(sql);
			int insertCount;
			Object rowId;
			//PKs for subclass tables are FKs to the parent table and therefore not auto generated.
			if (!isSubclassTable) {
				pscFactory.setReturnGeneratedKeys(true);
				KeyHolder keyHolder = new GeneratedKeyHolder();
				insertCount = jdbcTemplate.update(pscFactory.newPreparedStatementCreator(values), keyHolder);
				if (keyHolder.getKey() == null) {
					throw new RuntimeException("No auto generated key found after insert");
				} else if (keyHolder.getKeys().size() != 1) {
					throw new RuntimeException(
					        "Invalid auto generated key count after insert " + keyHolder.getKeys().size());
				}
				
				rowId = keyHolder.getKey();
			} else {
				insertCount = jdbcTemplate.update(pscFactory.newPreparedStatementCreator(values));
				rowId = parentRowId;
			}
			
			if (insertCount != 1) {
				throw new RuntimeException("Invalid insert count " + insertCount);
			}
			
			if (log.isTraceEnabled()) {
				log.trace("Successfully inserted {} row into table {}: {}", insertCount, tableName);
			}
			
			return rowId;
		}
		catch (Exception e) {
			final String msg = String.format("Error occurred while inserting a row into table %s: %s", tableName,
			    e.getMessage());
			throw new RuntimeException(msg, e);
		}
	}
	
	/**
	 * Checks whether a user exists in the system using the provided username or system_id. Note that
	 * the username is matched against both the username and system_id columns, same for systemId.
	 *
	 * @param username the username to check
	 * @param systemId the system_id to check
	 * @return true if the user exists in the system, false otherwise.
	 */
	public boolean checkIfUserExists(Object username, Object systemId) {
		if (log.isDebugEnabled()) {
			log.debug("Checking existence of a user exists with username {} or system id {}", username, systemId);
		}
		
		try {
			SqlParameterSource params = new MapSqlParameterSource(USER_UNIQUE_COLUMNS, List.of(username, systemId));
			return namedParamJdbcTemplate.queryForObject(USER_EXISTS_QUERY, params, Integer.class) > 0;
		}
		catch (Exception e) {
			final String message = "Failed to check existence of user with username " + username + " and system id "
			        + systemId;
			throw new RuntimeException(message, e);
		}
	}
}
