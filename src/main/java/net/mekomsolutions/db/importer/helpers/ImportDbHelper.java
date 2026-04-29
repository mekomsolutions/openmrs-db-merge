package net.mekomsolutions.db.importer.helpers;

import static net.mekomsolutions.db.importer.Constants.FAILED_ITEM_TABLE;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreatorFactory;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class ImportDbHelper {
	
	private static final String INSERT_FAILURE_SQL = "INSERT INTO " + FAILED_ITEM_TABLE
	        + " (table_name,identifier,error_type,error_msg) VALUES (?,?,?,?) AS r ON DUPLICATE KEY UPDATE "
	        + "error_type = r.error_type , error_msg = r.error_msg";
	
	protected JdbcTemplate jdbcTemplate;
	
	public ImportDbHelper(@Qualifier("mgtJdbcTemplate") JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}
	
	/**
	 * Inserts a single row into the failure queue table.
	 *
	 * @param tableName the name of the database table where the row will be inserted.
	 * @param primaryKey the primary key value of the failed row
	 * @param errorType the fully qualified java class name of the throws exception
	 * @param errorMessage the error message
	 */
	public void saveFailedItem(String tableName, String primaryKey, String errorType, String errorMessage) {
		if (log.isDebugEnabled()) {
			log.debug("Inserting a row into the {} for row in table {} with primary key {}", FAILED_ITEM_TABLE, tableName,
			    primaryKey);
		}
		
		try {
			PreparedStatementCreatorFactory pscFactory = new PreparedStatementCreatorFactory(INSERT_FAILURE_SQL);
			final Object[] args = new Object[] { tableName, primaryKey, errorType, errorMessage };
			int insertCount = jdbcTemplate.update(pscFactory.newPreparedStatementCreator(args));
			if (insertCount != 1) {
				throw new RuntimeException("Invalid insert count " + insertCount);
			}
			
			if (log.isTraceEnabled()) {
				log.trace("Successfully saved failed item from table {} with primary key {}", tableName, primaryKey);
			}
		}
		catch (Exception e) {
			final String m = String.format("Error occurred while saving failed item from table %s with primary key %s",
			    tableName, primaryKey);
			throw new RuntimeException(m, e);
		}
	}
	
}
