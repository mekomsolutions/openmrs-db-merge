package net.mekomsolutions.db.importer;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

@Component
public class DbMetadataExtractor {
	
	@Resource
	private DataSource dataSource;
	
	public DbMetadataExtractor(DataSource dataSource) {
		this.dataSource = dataSource;
	}
	
	/**
	 * Retrieves metadata information about all columns in a table, including column name, type, size,
	 * etc.
	 *
	 * @param tableName The name of the table to fetch metadata for.
	 * @return A List of ColumnMetadata objects containing column metadata.
	 * @throws SQLException if a database access error occurs.
	 */
	public List<Column> getColumnMetadata(String tableName) throws SQLException {
		List<Column> columnMetadataList = new ArrayList<>();
		
		try (Connection connection = dataSource.getConnection();
		        ResultSet columns = connection.getMetaData().getColumns(null, null, tableName, null)) {
			
			while (columns.next()) {
				String columnName = columns.getString("COLUMN_NAME");
				String columnType = columns.getString("TYPE_NAME");
				int columnSize = columns.getInt("COLUMN_SIZE");
				boolean isNullable = "YES".equalsIgnoreCase(columns.getString("IS_NULLABLE"));
				columnMetadataList.add(new Column(columnName, columnType, columnSize, isNullable));
			}
		}
		
		return columnMetadataList;
	}
	
	/**
	 * Retrieves foreign key information for a specific table including foreign key column names and
	 * referenced tables/columns.
	 *
	 * @param tableName The name of the table to fetch foreign key information for.
	 * @return A List of ForeignKeyInfo objects containing foreign key details.
	 * @throws SQLException if a database access error occurs.
	 */
	public List<ForeignKey> getForeignKeyMetadata(String tableName) throws SQLException {
		List<ForeignKey> foreignKeys = new ArrayList<>();
		
		try (Connection connection = dataSource.getConnection();
		        ResultSet foreignKeyResultSet = connection.getMetaData().getImportedKeys(null, null, tableName)) {
			
			while (foreignKeyResultSet.next()) {
				String fkColumnName = foreignKeyResultSet.getString("FKCOLUMN_NAME");
				String pkTableName = foreignKeyResultSet.getString("PKTABLE_NAME");
				String pkColumnName = foreignKeyResultSet.getString("PKCOLUMN_NAME");
				foreignKeys.add(new ForeignKey(fkColumnName, pkTableName, pkColumnName));
			}
		}
		
		return foreignKeys;
	}
	
}
