package net.mekomsolutions.db.importer.helpers;

import static net.mekomsolutions.db.importer.Constants.PHANTOM_UUID;
import static net.mekomsolutions.db.importer.MergeUtils.getParentTableName;
import static net.mekomsolutions.db.importer.MergeUtils.isSubclassTable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.mekomsolutions.db.importer.Constants;
import net.mekomsolutions.db.importer.MetadataExtractor;
import net.mekomsolutions.db.importer.Table;

/**
 * Utility class for interacting with a sink database using a JDBC template.
 */
@Component
@Slf4j
public class SinkDbHelper extends BaseDbHelper {
	
	private static final String USER_UNIQUE_COLUMNS = "uniqueCols";
	
	private static final String USER_EXISTS_QUERY = "SELECT COUNT(*) FROM users WHERE LOWER(username) IN (:"
	        + USER_UNIQUE_COLUMNS + ") OR LOWER(system_id) IN (:" + USER_UNIQUE_COLUMNS + ")";
	
	private static final String PARENT_PHANTOM_SUBQUERY = "SELECT %s FROM %s WHERE uuid = '" + PHANTOM_UUID + "'";
	
	private static final String SUBCLASS_PHANTOM_DELETE_QUERY = "DELETE FROM %s WHERE %s = (" + PARENT_PHANTOM_SUBQUERY
	        + ")";
	
	protected JdbcTemplate jdbcTemplate;
	
	protected NamedParameterJdbcTemplate namedParamJdbcTemplate;
	
	@Getter
	private MetadataExtractor metadataExtractor;
	
	public SinkDbHelper(@Qualifier("sinkJdbcTemplate") JdbcTemplate jdbcTemplate,
	    NamedParameterJdbcTemplate namedParamJdbcTemplate, @Qualifier("sinkExtractor") MetadataExtractor metadataExtractor) {
		super("sink", jdbcTemplate);
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
	 * Retrieves the primary key of a row from a subclass table by performing a join with the parent
	 * table using specified columns and filtering by the given uuid.
	 *
	 * @param uuid the uuid value in the parent table
	 * @param subclassTableName the name of the subclass table
	 * @param subclassPkColumn the primary key column name in the subclass table
	 * @param parentTableName the name of the parent table
	 * @param parentPkColumn the primary key column name in the parent table
	 * @return the ID of the row in the subclass table, or null if not found
	 */
	public Object getSubclassRowId(Object uuid, String subclassTableName, String subclassPkColumn, String parentTableName,
	                               String parentPkColumn) {
		
		String query = String.format("SELECT s.%s FROM %s s INNER JOIN %s p ON s.%s = p.%s WHERE p.uuid = ?",
		    subclassPkColumn, subclassTableName, parentTableName, subclassPkColumn, parentPkColumn);
		try {
			return jdbcTemplate.queryForObject(query, new Object[] { uuid }, Object.class);
		}
		catch (EmptyResultDataAccessException e) {
			return null;
		}
		catch (Exception e) {
			final String message = String.format(
			    "Failed to get row ID from subclass table %s where parent table %s has uuid %s", subclassTableName,
			    parentTableName, uuid);
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
		boolean isSubclassTable = isSubclassTable(tableName);
		Object parentRowId = null;
		if (isSubclassTable) {
			//Primary key values for subclass tables are not auto generated but instead FKs to the parent table, 
			//so we need to update the row if it already exists.
			Table table = metadataExtractor.getTable(tableName, false);
			final String pkColName = table.primaryKeys().get(0);
			String cols = columnNames.stream().filter(c -> !c.equals(pkColName)).map(c -> c + " = VALUES (" + c + ")")
			        .collect(Collectors.joining(","));
			sqlTemplate += " ON DUPLICATE KEY UPDATE " + cols;
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
				throw new RuntimeException("Invalid insert count " + insertCount + " into table " + tableName);
			}
			
			if (log.isTraceEnabled()) {
				log.trace("Successfully inserted {} row into table {}: {}", insertCount, tableName);
			}
			
			return rowId;
		}
		catch (Exception e) {
			final String msg = String.format("Error occurred while inserting a row into table %s", tableName);
			throw new RuntimeException(msg, e);
		}
	}
	
	/**
	 * Checks whether a user exists in the database with the specified username or system_id. Note that
	 * the username is matched against both the username and system_id columns, same for systemId.
	 *
	 * @param username the username to check
	 * @param systemId the system_id to check
	 * @return true if a user exists in the system, false otherwise.
	 */
	public boolean checkIfUserExists(Object username, Object systemId) {
		if (log.isDebugEnabled()) {
			log.debug("Checking existence of a user exists with username {} or system id {}", username, systemId);
		}
		
		try {
			final String usernameLower = username == null ? null : username.toString().toLowerCase();
			final String systemIdLower = systemId.toString().toLowerCase();
			List<String> values;
			if (usernameLower != null) {
				//Admin user has no username by default
				values = List.of(usernameLower, systemIdLower);
			} else {
				values = new ArrayList<>();
				values.add(null);
				values.add(systemIdLower);
			}
			
			SqlParameterSource params = new MapSqlParameterSource(USER_UNIQUE_COLUMNS, values);
			return namedParamJdbcTemplate.queryForObject(USER_EXISTS_QUERY, params, Integer.class) > 0;
		}
		catch (Exception e) {
			final String message = "Failed to check existence of user with username " + username + " and system id "
			        + systemId;
			throw new RuntimeException(message, e);
		}
	}
	
	/**
	 * Checks whether a row exists in the specified table based on the given column value.
	 *
	 * @param tableName the name of the database table to check
	 * @param columnName the name of the column to use for matching
	 * @param columnValue the value to check for in the specified column
	 * @return true if the row exists, false otherwise
	 */
	public boolean checkIfRowExists(String tableName, String columnName, Object columnValue) {
		if (log.isDebugEnabled()) {
			log.debug("Checking existence of a row in table {} where {} = {}", tableName, columnName, columnValue);
		}
		
		String query = String.format("SELECT COUNT(*) FROM %s WHERE %s = ?", tableName, columnName);
		try {
			return jdbcTemplate.queryForObject(query, new Object[] { columnValue }, int.class) > 0;
		}
		catch (Exception e) {
			final String msg = String.format("Failed to check existence of a row in table %s where %s = %s", tableName,
			    columnName, columnValue);
			throw new RuntimeException(msg, e);
		}
	}
	
	/**
	 * Deletes the phantom row from each table.
	 */
	public void deletePhantomRows() {
		if (log.isDebugEnabled()) {
			log.debug("Deleting all phantom rows from all tables");
		}
		
		try {
			jdbcTemplate.execute(Constants.DISABLE_KEYS);
			Map<String, List<String>> parentSubclassMap = metadataExtractor.getTableNames().stream()
			        .filter(t -> isSubclassTable(t))
			        .collect(Collectors.groupingBy(t -> getParentTableName(t, false, metadataExtractor)));
			for (String tableName : metadataExtractor.getTableNames()) {
				//Tables with non auto incrementing primary key, calling metadataExtractor.getTable will fail 
				if (!Constants.TABLES_WITHOUT_AUTO_INCREMENT.contains(tableName)) {
					Table table = metadataExtractor.getTable(tableName, true);
					if (table.getColumn("uuid") == null) {
						continue;
					}
				}
				
				try {
					if (parentSubclassMap.containsKey(tableName)) {
						//First delete the subclass row joined to the phantom row. 
						Table table = metadataExtractor.getTable(tableName, true);
						String pkColName = table.primaryKeys().get(0);
						parentSubclassMap.get(tableName).forEach(subclassTableName -> {
							Table subclassTable = metadataExtractor.getTable(subclassTableName, false);
							String subclassPkColName = subclassTable.primaryKeys().get(0);
							log.info("Deleting joined phantom row from subclass table {}", subclassTableName);
							String q = String.format(SUBCLASS_PHANTOM_DELETE_QUERY, subclassTableName, subclassPkColName,
							    pkColName, tableName);
							int deletes = jdbcTemplate.update(q);
							if (log.isTraceEnabled()) {
								log.trace("Deleted {} phantom rows from subclass table {}", deletes, tableName);
							}
						});
					}
					
					final String query = "DELETE FROM " + tableName + " WHERE uuid = '" + PHANTOM_UUID + "'";
					int deletes = jdbcTemplate.update(query);
					if (log.isTraceEnabled()) {
						log.trace("Deleted {} phantom rows from table {}", deletes, tableName);
					}
				}
				catch (Exception e) {
					final String msg = String.format("Failed to delete phantom row from table %s", tableName);
					throw new RuntimeException(msg, e);
				}
			}
		}
		finally {
			try {
				jdbcTemplate.execute(Constants.ENABLE_KEYS);
			}
			catch (Throwable e) {
				final String msg = "Failed to enable foreign key checks in the sink database, please be sure to "
				        + "re-enabled them manually in the database";
				log.error(msg, e);
			}
		}
	}
}
