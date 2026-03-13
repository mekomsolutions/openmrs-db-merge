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
package net.mekomsolutions.db.importer.config;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.context.annotation.Bean;

import net.mekomsolutions.db.importer.StartupListener;

public class AppConfig {
	
	@Bean
	public StartupListener startupListener(JobLauncher jobLauncher, Job job) {
		return new StartupListener(jobLauncher, job);
	}
	
}
