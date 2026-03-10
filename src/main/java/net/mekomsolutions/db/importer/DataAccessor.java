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
