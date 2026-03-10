/*
 * Copyright (C) Amiyul LLC - All Rights Reserved
 *
 * This source code is protected under international copyright law. All rights
 * reserved and protected by the copyright holder.
 *
 * This file is confidential and only available to authorized individuals with the
 * permission of the copyright holder. If you encounter this file and do not have
 * permission, please contact the copyright holder and delete this file.
 */
package net.mekomsolutions.db.importer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;

import lombok.extern.slf4j.Slf4j;

/**
 * An instance of this class reads data from a database table using a JDBC template.
 */
@Slf4j
public class DataReader extends DataAccessor {
	
	public DataReader(@Qualifier("sourceJdbcTemplate") JdbcTemplate jdbcTemplate) {
		super(jdbcTemplate);
	}
	
	/**
	 * Reads the next batch of rows from the specified database table where the id is greater than a
	 * provided value.
	 *
	 * @param table the name of the database table from which to fetch rows
	 * @param minId the minimum id value; only rows with an id greater than this value will be fetched
	 * @return a list of maps, where each map represents a row with column names as keys and their
	 *         corresponding values as values
	 */
	public List<Map<String, Object>> readNextBatch(String table, int minId) {
		log.info("Fetching next batch of rows from table {} with id greater than: {}", table, minId);
		
		String query = String.format("SELECT * FROM %s WHERE id > ? ORDER BY id ASC LIMIT ?", table);
		List<Map<String, Object>> rows = new ArrayList<>(batchSize);
		
		return jdbcTemplate.query(query, new Object[] { minId, batchSize }, rs -> {
			int columnCount = rs.getMetaData().getColumnCount();
			while (rs.next()) {
				Map<String, Object> row = new LinkedHashMap<>(columnCount);
				for (int i = 1; i <= columnCount; i++) {
					row.put(rs.getMetaData().getColumnName(i), rs.getObject(i));
				}
				
				rows.add(row);
			}
			
			return rows;
		});
	}
	
}
