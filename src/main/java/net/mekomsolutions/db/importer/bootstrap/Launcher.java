/*
 * Copyright © 2024, Ozone HIS <info@ozone-his.com>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package net.mekomsolutions.db.importer.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

import net.mekomsolutions.db.importer.config.AppConfig;
import net.mekomsolutions.db.importer.config.BatchConfig;
import net.mekomsolutions.db.importer.config.DaoConfig;
import net.mekomsolutions.db.importer.config.DataSourceConfig;

@SpringBootApplication
@Import({ DataSourceConfig.class, DaoConfig.class, AppConfig.class, BatchConfig.class })
public class Launcher {
	
	public static void main(String[] args) {
		SpringApplication.run(Launcher.class, args);
	}
	
}
