package net.mekomsolutions.db.importer;

import java.util.List;
import java.util.Map;

public record Table(String name, List<String> primaryKeys, Map<String, Column> columns) {
	
	/**
	 * Gets a Column object matching the specified name.
	 *
	 * @param columnName the name of the column to retrieve
	 * @return the Column object associated with the specified column name.
	 */
	public Column getColumn(String columnName) {
		return columns.get(columnName);
	}
	
}
