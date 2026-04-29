package net.mekomsolutions.db.importer;

import java.sql.Types;

public class DbUtils {
	
	protected static Object getPlaceHolder(Column column, String tableName) {
		int type = column.sqlType();
		Object value;
		if (Types.VARCHAR == type) {
			value = Constants.TEMP_STRING;
			if (column.size() < Constants.TEMP_STRING.length()) {
				value = Constants.TEMP_CHAR.repeat(column.size());
			}
		} else if (Types.INTEGER == type || Types.BIT == type) {
			value = 0;
		} else if (Types.DOUBLE == type) {
			value = 0.0;
		} else if (Types.BOOLEAN == type) {
			value = false;
		} else if (Types.TIME == type) {
			value = Constants.MIDNIGHT;
		} else if (Types.DATE == type) {
			value = Constants.EPOCH_DATE;
		} else if (Types.TIMESTAMP == type) {
			value = Constants.EPOCH;
		} else {
			throw new RuntimeException("Don't know how to generate placeholder value for column " + tableName + "."
			        + column.name() + " of type: " + type);
		}
		
		return value;
	}
	
	/**
	 * Converts a string value to an appropriate object of the specified SQL type.
	 *
	 * @param value the string value to be converted
	 * @param sqlType the target SQL type to which the value should be converted
	 * @return the converted object corresponding to the specified SQL type
	 */
	public static Object convert(String value, int sqlType) {
		Object result;
		if (Types.VARCHAR == sqlType) {
			result = value;
		} else if (Types.INTEGER == sqlType) {
			result = Integer.valueOf(value);
		} else {
			throw new RuntimeException("Don't know how to convert string " + value + " to sql type " + sqlType);
		}
		
		return result;
	}
	
}
