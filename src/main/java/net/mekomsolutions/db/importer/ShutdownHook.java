/*
 * Copyright (C) Amiyul LLC - All Rights Reserved
 *
 * This source code is protected under international copyright law. All rights
 * reserved and protected by the copyright holder. However, the generated 
 * bytecode from this source code is free for use.
 *
 * This file is confidential and only available to authorized individuals with the
 * permission of the copyright holder. If you encounter this file and do not have
 * permission, please contact the copyright holder and delete this file.
 */
package net.mekomsolutions.db.importer;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Shutdown hook that detects when the application is shutdown.
 */
@Slf4j
public class ShutdownHook implements Runnable {
	
	@Getter
	private boolean shutdown = false;
	
	private ShutdownHook() {
	}
	
	public static ShutdownHook getInstance() {
		return ShutdownHookHolder.INSTANCE;
	}
	
	@Override
	public void run() {
		try {
			log.info("Shutting down application");
			shutdown = true;
		}
		catch (Throwable t) {}
	}
	
	private static final class ShutdownHookHolder {
		
		private static final ShutdownHook INSTANCE = new ShutdownHook();
		
	}
	
}
