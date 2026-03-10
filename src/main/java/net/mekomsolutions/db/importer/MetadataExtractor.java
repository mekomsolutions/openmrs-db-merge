package net.mekomsolutions.db.importer;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class MetadataExtractor {
	
	private JdbcTemplate jdbcTemplate;
	
	@Value("${source.database}")
	private String db;
	
	public MetadataExtractor(@Qualifier("sourceJdbcTemplate") JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}
	
	/**
	 * Gets metadata information for a table and creates a Table object containing the information.
	 *
	 * @param table The name of the table to fetch metadata for.
	 * @return A Table object containing the metadata of the specified table.
	 * @throws SQLException if a database access error occurs.
	 */
	public Table getTable(String table) {
		log.info("Fetching metadata for table: {}", table);
		
		return jdbcTemplate.execute((ConnectionCallback<Table>) c -> {
			List<Column> columns = getColumns(table, c);
			Map<String, Column> nameColMap = columns.stream().collect(Collectors.toMap(Column::columnName, col -> col));
			return new Table(nameColMap);
		});
	}
	
	/**
	 * Retrieves metadata information about all columns in a table, including column name, type, size
	 * nullability and foreign keys.
	 *
	 * @param table The name of the table to fetch metadata for.
	 * @param connection The Connection object.
	 * @return A List of ColumnMetadata objects containing column metadata.
	 * @throws SQLException if a database access error occurs.
	 */
	public List<Column> getColumns(String table, Connection connection) throws SQLException {
		log.debug("Fetching column metadata for table: {}", table);
		
		List<Column> columnMetadataList = new ArrayList<>();
		Map<String, ForeignKey> colAndFkMap = getForeignKeyMetadata(table, connection).stream()
		        .collect(Collectors.toMap(ForeignKey::columnName, fk -> fk));
		
		try (ResultSet rs = connection.getMetaData().getColumns(null, db, table, null)) {
			while (rs.next()) {
				String columnName = rs.getString("COLUMN_NAME");
				String columnType = rs.getString("TYPE_NAME");
				int columnSize = rs.getInt("COLUMN_SIZE");
				boolean isNullable = "YES".equalsIgnoreCase(rs.getString("IS_NULLABLE"));
				ForeignKey fk = colAndFkMap.get(columnName);
				columnMetadataList.add(new Column(columnName, columnType, isNullable, columnSize, fk));
			}
		}
		
		return columnMetadataList;
	}
	
	/**
	 * Retrieves foreign key information for the specific table.
	 *
	 * @param table The name of the table to fetch foreign key information for.
	 * @param connection The Connection object.
	 * @return A List of ForeignKeyInfo objects containing foreign key details.
	 * @throws SQLException if a database access error occurs.
	 */
	public List<ForeignKey> getForeignKeyMetadata(String table, Connection connection) throws SQLException {
		log.info("Fetching foreign keys for table: {}", table);
		
		List<ForeignKey> foreignKeys = new ArrayList<>();
		try (ResultSet rs = connection.getMetaData().getImportedKeys(null, db, table)) {
			while (rs.next()) {
				String columnName = rs.getString("FKCOLUMN_NAME");
				String referenceTable = rs.getString("PKTABLE_NAME");
				String referencedColumn = rs.getString("PKCOLUMN_NAME");
				foreignKeys.add(new ForeignKey(columnName, referenceTable, referencedColumn));
			}
		}
		
		return foreignKeys;
	}
	
}
