package net.mekomsolutions.db.importer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * A subclass of this type is used to read or write data from or to the database.
 */
public abstract class DataAccessor {
	
	protected JdbcTemplate jdbcTemplate;
	
	@Value("${batch.size:1000}")
	protected Integer batchSize;
	
	public DataAccessor(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}
	
}
