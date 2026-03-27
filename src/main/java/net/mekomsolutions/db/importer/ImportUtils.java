package net.mekomsolutions.db.importer;

import static net.mekomsolutions.db.importer.Constants.PHANTOM_UUID;

import java.sql.Types;
import java.util.List;
import java.util.stream.Collectors;

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
		String uniqueColumn = ImportUtils.isSubclassTable(tableName) ? table.primaryKeys().get(0) : "uuid";
		List<String> insertColumns = table.insertColumnNames();
		String columns = String.join(",", insertColumns);
		String placeholders = insertColumns.stream().map(c -> "?").collect(Collectors.joining(","));
		String updateClause = insertColumns.stream().filter(c -> !c.equals(uniqueColumn)).map(c -> c + " = r." + c)
		        .collect(Collectors.joining(","));
		return String.format("INSERT INTO %s (%s) VALUES (%s) AS r ON DUPLICATE KEY UPDATE " + updateClause, tableName,
		    columns, placeholders);
	}
	
	protected static boolean isSubclassTable(String tableName) {
		return Constants.SUBCLASS_TABLES.contains(tableName);
	}
	
	protected static Object insertPlaceholderRow(ForeignKey fk, String fkTableName, Object uuid,
	                                             MetadataExtractor metadataExtractor, SinkDbHelper sinkDbHelper) {
		
		//TODO Make thread safe to avoid duplication of placeholder row
		final String refTableName = fk.referenceTable();
		if (log.isDebugEnabled()) {
			final String fromColName = fk.columnName();
			final String kind = uuid == null ? "phantom" : "placeholder";
			final Object Uid = uuid == null ? PHANTOM_UUID : uuid;
			log.debug("Inserting {} row into sink table {} with uuid {} referenced by {}.{}", kind, refTableName, Uid,
			    fkTableName, fromColName);
		}
		
		Table refTable = metadataExtractor.getTable(refTableName);
		List<Column> requiredColumns = getRequiredColumns(refTable);
		return sinkDbHelper.insertRow(refTableName, requiredColumns.stream().map(Column::name).toList(),
		    createPlaceholderRow(fk, requiredColumns, uuid, metadataExtractor, sinkDbHelper));
	}
	
	private static Object[] createPlaceholderRow(ForeignKey fk, List<Column> requiredColumns, Object uuid,
	                                             MetadataExtractor metadataExtractor, SinkDbHelper sinkDbHelper) {
		
		final String refTableName = fk.referenceTable();
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
					log.debug("Marking {} row {} in sink table {} as {}", kind, colName, refTableName, colName);
				}
				
				if (Types.BOOLEAN == col.sqlType()) {
					value = true;
				} else if (Types.BIT == col.sqlType()) {
					value = 1;
				} else {
					String msg = "Don't know how handle type: " + col.sqlType() + " for column " + refTableName + "."
					        + colName;
					throw new RuntimeException(msg);
				}
			} else {
				ForeignKey colFk = col.foreignKey();
				if (colFk == null) {
					value = DbUtils.getPlaceHolder(col, refTableName);
				} else if (uuid == null && isUserSelfReference(refTableName, colName)) {
					if (log.isDebugEnabled()) {
						String msg = "Setting {} for phantom row in sink table {} to daemon user id";
						log.debug(msg, colName, refTableName);
					}
					
					value = getDaemonUserId(sinkDbHelper);
				} else {
					value = getPhantomRowId(colFk, refTableName, metadataExtractor, sinkDbHelper);
				}
			}
			
			values[index] = value;
			index++;
		}
		
		return values;
	}
	
	protected static Object getMaxRowId(JobExplorer jobExplorer, JobRepository jobRepository, String tableName) {
		//Traverse all past job instances starting with most recent to find the one where we saved a max row id value.
		List<JobInstance> jobInstances = jobExplorer.getJobInstances(Constants.JOB_NAME, 0, Integer.MAX_VALUE);
		for (JobInstance instance : jobInstances) {
			Object rowId = getMaxRowId(jobRepository, instance, tableName);
			if (rowId != null) {
				return rowId;
			}
		}
		
		return null;
	}
	
	private static Object getMaxRowId(JobRepository jobRepository, JobInstance jobInstance, String tableName) {
		Object rowId = null;
		StepExecution stepExecution = jobRepository.getLastStepExecution(jobInstance, tableName);
		if (stepExecution != null) {
			rowId = stepExecution.getExecutionContext().get(Constants.STEP_KEY_MAX_PROCESSED_ID);
		}
		
		return rowId;
	}
	
	private static Object getPhantomRowId(ForeignKey fk, String fkTableName, MetadataExtractor metadataExtractor,
	                                      SinkDbHelper sinkDbHelper) {
		
		//TODO Make thread safe to avoid duplication of phantom row
		final String refTableName = fk.referenceTable();
		final String refColName = fk.referencedColumn();
		Object phantomRowId = sinkDbHelper.getColumnValue(refTableName, refColName, "UPPER(uuid)", PHANTOM_UUID);
		if (phantomRowId == null) {
			//Insert the phantom row
			phantomRowId = insertPlaceholderRow(fk, fkTableName, null, metadataExtractor, sinkDbHelper);
		}
		
		return phantomRowId;
	}
	
	private static List<Column> getRequiredColumns(Table t) {
		//TODO Cache the required columns for each table or change Table from a record
		boolean isSubclassTable = isSubclassTable(t.name());
		return t.columns().values().stream()
		        .filter(c -> (!t.primaryKeys().contains(c.name()) || isSubclassTable) && !c.nullable()).toList();
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
	
}
