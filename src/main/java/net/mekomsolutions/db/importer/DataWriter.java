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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;

import lombok.extern.slf4j.Slf4j;

/**
 * An instance of this class reads data from a database table using a JDBC template.
 */
@Slf4j
public class DataWriter extends DataAccessor {
	
	public DataWriter(@Qualifier("sinkJdbcTemplate") JdbcTemplate jdbcTemplate) {
		super(jdbcTemplate);
	}
	
	/**
	 * Inserts a batch of rows into the specified database table.
	 *
	 * @param table the name of the database table where the rows will be inserted.
	 * @param columnNames a list of column names that correspond to the table's structure.
	 * @param rows a list of maps representing the rows to insert, where each map contains key-value
	 *            pairs corresponding to column names and their respective values.
	 * @return an array of integers representing the number of rows affected for each batch operation.
	 */
	public int[] insertBatch(String table, List<String> columnNames, List<Object[]> rows) {
		if (log.isDebugEnabled()) {
			log.info("Inserting {} rows into table {}", rows.size(), table);
		}
		
		String columns = String.join(",", columnNames);
		String placeholders = columnNames.stream().map(c -> "?").collect(Collectors.joining(","));
		String sql = String.format("INSERT INTO %s (%s) VALUES (%s)", table, columns, placeholders);
		
		return batchUpdate(sql, table, rows, true);
	}
	
	private int[] batchUpdate(String sql, String table, List<Object[]> rows, boolean isInsert) {
		try {
			if (log.isDebugEnabled()) {
				final String op = isInsert ? "insert" : "update";
				log.debug("Executing batch {} sql: {}", op, sql);
			}
			
			int[] updateCounts = jdbcTemplate.batchUpdate(sql, rows);
			int insertCount = Arrays.stream(updateCounts).sum();
			if (log.isDebugEnabled()) {
				final String op = isInsert ? "inserted" : "updated";
				log.debug("{} rows of {} successfully {} into table {}", insertCount, rows.size(), op, table);
			}
			
			return updateCounts;
		}
		catch (Exception e) {
			final String op = isInsert ? "insert" : "update";
			final String msg = String.format("Error occurred performing batch %s into table %s: %s", op, table,
			    e.getMessage());
			log.warn(msg, e);
		}
		
		return new int[] {};
	}
	
}
