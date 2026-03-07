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

import java.util.Map;

public record Table(Map<String,Column>columns){

/**
 * Gets a Column object matching the specified name.
 *
 * @param columnName the name of the column to retrieve
 * @return the Column object associated with the specified column name.
 */
public Column getColumn(String columnName){return columns.get(columnName);}

}
