package net.mekomsolutions.db.importer;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class MetadataExtractor {
	
	private JdbcTemplate jdbcTemplate;
	
	// Cache to store table name to Table object mappings
	private final Map<String, Table> NAME_AND_TABLE_CACHE = new ConcurrentHashMap<>();
	
	public MetadataExtractor(@Qualifier("sourceJdbcTemplate") JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}
	
	/**
	 * Retrieves a list of all table names from the database.
	 *
	 * @return A List of table names.
	 * @throws SQLException
	 */
	public List<String> getTableNames() {
		if (log.isDebugEnabled()) {
			log.debug("Fetching all tables from the database");
		}
		
		List<String> tableNames = new ArrayList<>();
		jdbcTemplate.execute((ConnectionCallback<Void>) con -> {
			try (ResultSet rs = con.getMetaData().getTables(con.getCatalog(), con.getSchema(), null,
			    new String[] { "TABLE" })) {
				while (rs.next()) {
					tableNames.add(rs.getString("TABLE_NAME").toLowerCase(Locale.ENGLISH));
				}
			}
			return null;
		});
		
		return tableNames;
	}
	
	/**
	 * Gets metadata information for a table and creates a Table object containing the information.
	 *
	 * @param tableName The name of the table to fetch metadata for.
	 * @return A Table object containing the metadata of the specified table.
	 * @throws SQLException if a database access error occurs.
	 */
	public Table getTable(String tableName) {
		if (!NAME_AND_TABLE_CACHE.containsKey(tableName)) {
			//We want exactly one instance of the Table object because  
			//we will need to synchronize on some of its properties.
			synchronized (this) {
				if (!NAME_AND_TABLE_CACHE.containsKey(tableName)) {
					if (log.isDebugEnabled()) {
						log.debug("Fetching metadata for table: {}", tableName);
					}
					
					Table table = jdbcTemplate.execute((ConnectionCallback<Table>) connection -> {
						List<String> keys = getPrimaryKeys(tableName, connection);
						List<Column> columns = getColumns(tableName, connection);
						List<String> columnNames = columns.stream().map(col -> col.name()).toList();
						List<String> insertColumns = columns.stream().filter(c -> !c.autoIncrement()).map(c -> c.name())
						        .toList();
						if (ImportUtils.isSubclassTable(tableName)) {
							final List<String> temp = new ArrayList<>(insertColumns);
							insertColumns = new ArrayList<>(temp.size() + 1);
							insertColumns.add(keys.get(0));
							insertColumns.addAll(temp);
						}
						
						Map<String, Column> nameColMap = columns.stream()
						        .collect(Collectors.toMap(Column::name, col -> col));
						
						return new Table(tableName, keys, columnNames, insertColumns, nameColMap);
					});
					
					if (!ImportUtils.isExtensionTable(table) && !ImportUtils.isMappingTable(table)) {
						if (table.primaryKeys().size() != 1) {
							throw new RuntimeException("Table " + tableName + " has unsupported primary key count "
							        + table.primaryKeys().size());
						}
					}
					
					if (!ImportUtils.isSubclassTable(tableName) && !ImportUtils.isExtensionTable(table)
					        && !ImportUtils.isMappingTable(table) && !table.columnNames().contains("uuid")) {
						//TODO Add support for these tables
						throw new RuntimeException("Table " + tableName + " has no uuid column");
					}
					
					NAME_AND_TABLE_CACHE.put(tableName, table);
				}
			}
		}
		
		return NAME_AND_TABLE_CACHE.get(tableName);
	}
	
	/**
	 * Retrieves metadata information about all columns in a table, including column name, type, size
	 * nullability and foreign keys.
	 *
	 * @param table The name of the table to fetch metadata for.
	 * @param con The Connection object.
	 * @return A List of ColumnMetadata objects containing column metadata.
	 * @throws SQLException if a database access error occurs.
	 */
	public List<Column> getColumns(String table, Connection con) throws SQLException {
		if (log.isDebugEnabled()) {
			log.debug("Fetching column metadata for table: {}", table);
		}
		
		List<Column> columns = new ArrayList<>();
		Map<String, ForeignKey> colAndFkMap = getForeignKeyMetadata(table, con).stream()
		        .collect(Collectors.toMap(ForeignKey::columnName, fk -> fk));
		
		try (ResultSet rs = con.getMetaData().getColumns(con.getCatalog(), con.getSchema(), table, null)) {
			while (rs.next()) {
				String name = rs.getString("COLUMN_NAME").toLowerCase(Locale.ENGLISH);
				int sqlType = rs.getInt("DATA_TYPE");
				int size = rs.getInt("COLUMN_SIZE");
				boolean isAutoIncrement = "YES".equalsIgnoreCase(rs.getString("IS_AUTOINCREMENT"));
				boolean isNullable = "YES".equalsIgnoreCase(rs.getString("IS_NULLABLE"));
				ForeignKey fk = colAndFkMap.get(name);
				columns.add(new Column(name, sqlType, isAutoIncrement, isNullable, size, fk));
			}
		}
		
		return columns;
	}
	
	/**
	 * Retrieves metadata information about the primary keys of a table.
	 *
	 * @param table The name of the table to fetch primary key information for.
	 * @param connection The Connection object.
	 * @return A List of Column objects representing the primary key columns.
	 * @throws SQLException if a database access error occurs.
	 */
	public List<String> getPrimaryKeys(String table, Connection connection) throws SQLException {
		if (log.isDebugEnabled()) {
			log.debug("Fetching primary keys for table: {}", table);
		}
		
		List<String> primaryKeys = new ArrayList<>();
		try (ResultSet rs = connection.getMetaData().getPrimaryKeys(connection.getCatalog(), connection.getSchema(),
		    table)) {
			while (rs.next()) {
				primaryKeys.add(rs.getString("COLUMN_NAME").toLowerCase(Locale.ENGLISH));
			}
		}
		
		if (primaryKeys.size() > 1) {
			Collections.sort(primaryKeys);
		}
		
		return primaryKeys;
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
		if (log.isDebugEnabled()) {
			log.debug("Fetching foreign keys for table: {}", table);
		}
		
		//We use a set because of a bug in the MySQL driver where it returns duplicate FKs
		Set<ForeignKey> foreignKeys = new HashSet<>();
		try (ResultSet rs = connection.getMetaData().getImportedKeys(connection.getCatalog(), connection.getSchema(),
		    table)) {
			while (rs.next()) {
				String name = rs.getString("FK_NAME").toLowerCase(Locale.ENGLISH);
				String columnName = rs.getString("FKCOLUMN_NAME").toLowerCase(Locale.ENGLISH);
				String referenceTable = rs.getString("PKTABLE_NAME").toLowerCase(Locale.ENGLISH);
				String referencedColumn = rs.getString("PKCOLUMN_NAME").toLowerCase(Locale.ENGLISH);
				foreignKeys.add(new ForeignKey(name, columnName, referenceTable, referencedColumn));
			}
		}
		
		return foreignKeys.stream().toList();
	}
	
}
