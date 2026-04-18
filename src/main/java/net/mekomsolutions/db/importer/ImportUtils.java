package net.mekomsolutions.db.importer;

import static net.mekomsolutions.db.importer.Constants.COMPOSITE_ID_SEPARATOR;
import static net.mekomsolutions.db.importer.Constants.ERROR_MSG_COLUMN_SIZE;
import static net.mekomsolutions.db.importer.Constants.PHANTOM_UUID;

import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.repository.JobRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ImportUtils {
	
	private static Integer daemonUserId;
	
	protected static String getWriteSql(Table table) {
		final String tableName = table.name();
		//Note that for many-to-many mapping tables with exactly 2 columns, the resulting query would be as below
		//INSERT INTO table1 (col1,col2) VALUES (?,?) AS r ON DUPLICATE KEY UPDATE col1 = r.col1,col2 = r.col2
		//The query above actually would have no effect, the alternative would be to skip existing many-to-many table 
		//rows in the RowItemProcessor but I'm guessing that would make the application slower
		//TODO Future try it and compare the execution times
		List<String> uniqueColumns;
		if (isSubclassTable(tableName)) {
			uniqueColumns = List.of(table.primaryKeys().get(0));
		} else if (isExtensionTable(table)) {
			uniqueColumns = table.primaryKeys();
		} else {
			uniqueColumns = List.of("uuid");
		}
		
		List<String> insertColumns = table.insertColumnNames();
		String columns = String.join(",", insertColumns);
		String placeholders = insertColumns.stream().map(c -> "?").collect(Collectors.joining(","));
		String updateClause = insertColumns.stream().filter(c -> !uniqueColumns.contains(c)).map(c -> c + " = r." + c)
		        .collect(Collectors.joining(","));
		return String.format("INSERT INTO %s (%s) VALUES (%s) AS r ON DUPLICATE KEY UPDATE %s", tableName, columns,
		    placeholders, updateClause);
	}
	
	protected static boolean isSubclassTable(String tableName) {
		return Constants.SUBCLASS_TABLES.contains(tableName);
	}
	
	protected static boolean isExtensionTable(Table table) {
		return isExtensionTable(table.primaryKeys(), table.columns().values());
	}
	
	protected static boolean isExtensionTable(List<String> primaryKeys, Collection<Column> columns) {
		return primaryKeys.size() == 2 && columns.size() == 3;
	}
	
	protected static boolean isMappingTable(Table table) {
		return isMappingTable(table.primaryKeys(), table.columns().values());
	}
	
	protected static boolean isMappingTable(List<String> primaryKeys, Collection<Column> columns) {
		return primaryKeys.size() == 2 && columns.size() == 2;
	}
	
	protected static Object insertPlaceholderRow(String tableName, String referencedColumnName, Object uuid,
	                                             MetadataExtractor metadataExtractor, SinkDbHelper sinkDbHelper) {
		
		//TODO Future If we add support for parallel table processing, make this thread safe to avoid duplication of
		//placeholder row
		Table refTable = metadataExtractor.getTable(tableName);
		List<Column> requiredColumns = getRequiredColumns(refTable);
		Object parentId = null;
		boolean isSubclassTable = isSubclassTable(tableName);
		if (isSubclassTable) {
			//For subclass table we first insert into the parent table
			//TODO This code is actually duplicated from RowProcessorHelper, may set it on the Table object
			ForeignKey parentFk = refTable.getColumn(referencedColumnName).foreignKey();
			String parentTableName = parentFk.referencedTable();
			Table parentTable = metadataExtractor.getTable(parentTableName);
			List<Column> parentRequiredColumns = getRequiredColumns(parentTable);
			
			if (log.isDebugEnabled()) {
				log.debug("Inserting parent row into sink table {} with uuid {} for child row in table {}", parentTableName,
				    uuid, tableName);
			}
			
			Object[] parentValues = createPlaceholderRow(parentTableName, parentRequiredColumns, uuid, metadataExtractor,
			    sinkDbHelper);
			parentId = sinkDbHelper.insertRow(parentTableName, parentRequiredColumns.stream().map(Column::name).toList(),
			    parentValues);
		}
		
		Object[] values = createPlaceholderRow(tableName, requiredColumns, uuid, metadataExtractor, sinkDbHelper);
		List<String> columnNames = requiredColumns.stream().map(Column::name).toList();
		if (isSubclassTable) {
			columnNames = new ArrayList<>(columnNames);
			columnNames.add(refTable.primaryKeys().get(0));
			values = ArrayUtils.add(values, parentId);
		}
		
		return sinkDbHelper.insertRow(tableName, columnNames, values);
	}
	
	/**
	 * Inserts a placeholder row into the referenced subclass table based on the provided foreign key
	 * and parentId.
	 *
	 * @param tableName the name of the table to insert the row.
	 * @param parentId the id of the parent row to associate with the placeholder child row
	 * @param metadataExtractor {@link MetadataExtractor} instance
	 * @param sinkDbHelper {@link SinkDbHelper} instance
	 */
	protected static void insertPlaceholderChildRow(String tableName, Object parentId, MetadataExtractor metadataExtractor,
	                                                SinkDbHelper sinkDbHelper) {
		Table table = metadataExtractor.getTable(tableName);
		List<Column> requiredColumns = getRequiredColumns(table);
		List<String> columnNames = new ArrayList<>(requiredColumns.stream().map(Column::name).toList());
		columnNames.add(table.primaryKeys().get(0));
		Object[] values = createPlaceholderRow(tableName, requiredColumns, null, metadataExtractor, sinkDbHelper);
		values = ArrayUtils.add(values, parentId);
		sinkDbHelper.insertRow(tableName, columnNames, values);
	}
	
	private static Object[] createPlaceholderRow(String tableName, List<Column> requiredColumns, Object uuid,
	                                             MetadataExtractor metadataExtractor, SinkDbHelper sinkDbHelper) {
		
		Object[] values = new Object[requiredColumns.size()];
		int index = 0;
		for (Column col : requiredColumns) {
			final String colName = col.name();
			Object value;
			if ("uuid".equals(colName)) {
				if (uuid != null) {
					value = uuid;
				} else {
					//This is a dummy row since uuid is unknown
					value = PHANTOM_UUID;
				}
			} else if ("voided".equals(colName) || "retired".equals(colName)) {
				if (log.isDebugEnabled()) {
					final String kind = uuid == null ? "phantom" : "placeholder";
					log.debug("Marking {} row {} in sink table {} as {}", kind, colName, tableName, colName);
				}
				
				if (Types.BOOLEAN == col.sqlType()) {
					value = true;
				} else if (Types.BIT == col.sqlType()) {
					value = 1;
				} else {
					String msg = "Don't know how handle type: " + col.sqlType() + " for column " + tableName + "." + colName;
					throw new RuntimeException(msg);
				}
			} else {
				ForeignKey colFk = col.foreignKey();
				if (colFk == null) {
					value = DbUtils.getPlaceHolder(col, tableName);
				} else if (uuid == null && isUserSelfReference(tableName, colName)) {
					if (log.isDebugEnabled()) {
						final String msg = "Setting {} for phantom row in sink table {} to daemon user id";
						log.debug(msg, colName, tableName);
					}
					
					//TODO After the row is inserted, switch back to the self reference except for creator field.
					value = getDaemonUserId(sinkDbHelper);
				} else {
					value = getPhantomRowId(colFk, tableName, metadataExtractor, sinkDbHelper);
				}
			}
			
			values[index] = value;
			index++;
		}
		
		return values;
	}
	
	/**
	 * Retrieves the maximum known processed row ID value for a specific table from past job instances.
	 * The method iterates through all job instances associated with a predefined job name, starting
	 * with the most recent, to find the instance where a maximum row ID value is stored.
	 *
	 * @param jobExplorer the JobExplorer instance
	 * @param jobRepository the JobRepository instance
	 * @param tableName the name of the table for which the maximum row ID is being retrieved
	 * @return the maximum processed row ID value for the specified table, or null if no value is found
	 */
	protected static Integer getMaxRowId(JobExplorer jobExplorer, JobRepository jobRepository, String tableName) {
		//Traverse all past job instances starting with most recent to find the one where we saved a max row id value.
		List<JobInstance> jobInstances = jobExplorer.getJobInstances(Constants.JOB_NAME, 0, Integer.MAX_VALUE);
		for (JobInstance instance : jobInstances) {
			Integer rowId = getMaxRowId(jobRepository, instance, tableName);
			if (rowId != null) {
				return rowId;
			}
		}
		
		return null;
	}
	
	protected static void handleFailure(Table table, Map<String, Object> row, Throwable throwable,
	                                    ImportDbHelper importDbHelper) {
		
		final String primaryKey = getIdentifier(table, row);
		Throwable cause = ExceptionUtils.getRootCause(throwable);
		if (cause == null) {
			cause = throwable;
		}
		
		String errMsg = cause.getMessage();
		if (errMsg.length() > ERROR_MSG_COLUMN_SIZE) {
			errMsg = errMsg.substring(0, ERROR_MSG_COLUMN_SIZE);
		}
		
		importDbHelper.saveFailedItem(table.name(), primaryKey, cause.getClass().getName(), errMsg);
	}
	
	private static Integer getMaxRowId(JobRepository jobRepository, JobInstance jobInstance, String tableName) {
		Integer rowId = null;
		StepExecution stepExecution = jobRepository.getLastStepExecution(jobInstance, tableName);
		if (stepExecution != null) {
			rowId = stepExecution.getExecutionContext().get(Constants.STEP_KEY_MAX_PROCESSED_ID, Integer.class, null);
		}
		
		return rowId;
	}
	
	private static Object getPhantomRowId(ForeignKey fk, String referencingTableName, MetadataExtractor metadataExtractor,
	                                      SinkDbHelper sinkDbHelper) {
		
		final String refTableName = fk.referencedTable();
		final String refColName = fk.referencedColumn();
		Object phantomRowId = sinkDbHelper.getColumnValue(refTableName, refColName, "UPPER(uuid)", PHANTOM_UUID);
		if (phantomRowId == null) {
			//We don't want concurrent inserts of phantom row which would result in unique constraint violation on
			//the uuid column
			synchronized (ImportUtils.class) {
				phantomRowId = sinkDbHelper.getColumnValue(refTableName, refColName, "UPPER(uuid)", PHANTOM_UUID);
				if (phantomRowId == null) {
					if (log.isDebugEnabled()) {
						log.debug("Inserting phantom row into sink table {} referenced by {}.{}", refTableName,
						    referencingTableName, fk.columnName());
					}
					
					phantomRowId = insertPlaceholderRow(fk.referencedTable(), fk.referencedColumn(), null, metadataExtractor,
					    sinkDbHelper);
				}
			}
		}
		
		return phantomRowId;
	}
	
	private static List<Column> getRequiredColumns(Table t) {
		//TODO Cache the required columns for each table or change Table from a record
		return t.columns().values().stream().filter(c -> {
			boolean result = !c.nullable() && !c.autoIncrement();
			if (isSubclassTable(t.name())) {
				return result && !t.primaryKeys().contains(c.name());
			}
			
			return result;
		}).toList();
	}
	
	private static boolean isUserSelfReference(String table, String colName) {
		return "users".equals(table)
		        && ("creator".equals(colName) || "changed_by".equals(colName) || "retired_by".equals(colName));
	}
	
	private static Integer getDaemonUserId(SinkDbHelper sinkDbHelper) {
		if (daemonUserId != null) {
			return daemonUserId;
		}
		
		Object id = sinkDbHelper.getColumnValue("users", "user_id", "UPPER(uuid)", Constants.DAEMON_USER_UUID);
		if (id == null) {
			throw new RuntimeException("Daemon user not found in sink database");
		}
		
		daemonUserId = (Integer) id;
		return daemonUserId;
	}
	
	protected static String getIdentifierLabel(Table table) {
		String label;
		if (table.primaryKeys().size() == 1) {
			label = table.primaryKeys().get(0);
		} else {
			label = table.primaryKeys().stream().collect(Collectors.joining(COMPOSITE_ID_SEPARATOR));
		}
		
		return label;
	}
	
	private static String getIdentifier(Table table, Map<String, Object> row) {
		String identifier;
		if (table.primaryKeys().size() == 1) {
			identifier = row.get(table.primaryKeys().get(0)).toString();
		} else {
			identifier = table.primaryKeys().stream().map(k -> row.get(k).toString())
			        .collect(Collectors.joining(COMPOSITE_ID_SEPARATOR));
		}
		
		return identifier;
	}
	
	/**
	 * Marks a record as retried by setting retire related column values.
	 *
	 * @param item the record to be retired, represented as a map of key-value pairs
	 * @param sinkDbHelper an instance of SinkDbHelper used to retrieve the daemon user ID
	 */
	protected static void retireRecord(Map<String, Object> item, SinkDbHelper sinkDbHelper) {
		item.put("retired", true);
		item.put("retired_by", getDaemonUserId(sinkDbHelper));
		item.put("date_retired", LocalDateTime.now());
		item.put("retire_reason", Constants.RETIRE_REASON);
	}
	
	/**
	 * Gets the maximum connection size based on the provided value. If the provided value is null, a
	 * default value is returned based on the thread count.
	 * 
	 * @see #getDefaultThreadCount()
	 * @param maxSize the maximum connection size specified by the user; can be null
	 * @return the maximum connection size, or the default thread count if maxSize is null
	 */
	public static int getMaxConnectionSize(Integer maxSize) {
		return maxSize != null ? maxSize : getDefaultThreadCount();
	}
	
	/**
	 * Calculates and returns the default thread count based on the available processors. The value is
	 * determined as twice the number of available processors on the system.
	 *
	 * @return the default thread count.
	 */
	public static int getDefaultThreadCount() {
		return Runtime.getRuntime().availableProcessors() * 2;
	}
	
}
