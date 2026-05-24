package net.mekomsolutions.db.importer;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.util.List;

public class Constants {
	
	public static final String JOB_NAME = "mergeJob";
	
	public static final String STEP_KEY_MAX_WRITTEN_ID = Constants.class.getPackageName() + ".maxWrittenRowId";
	
	public static final Date EPOCH_DATE = Date.valueOf(LocalDate.of(1970, Month.JANUARY, 1));
	
	public static final Timestamp EPOCH = Timestamp.valueOf(LocalDateTime.of(1970, Month.JANUARY, 1, 0, 0, 0));
	
	public static final Time MIDNIGHT = Time.valueOf(LocalTime.MIDNIGHT);
	
	public static final String TEMP_STRING = "[TEMP_STRING]";
	
	public static final String TEMP_CHAR = "#";
	
	public static final String PHANTOM_UUID = "[PHANTOM_UUID]";
	
	public static final String DAEMON_USER_UUID = "A4F30A1B-5EB9-11DF-A648-37A07F9C90FB";
	
	public static final List<String> SUBCLASS_TABLES = List.of("patient", "drug_order", "test_order");
	
	public static final String RETIRE_REASON = "Duplicate upon migration";
	
	public static final String FAILED_ITEM_TABLE = "failed_import_item";
	
	public static final Integer ERROR_MSG_COLUMN_SIZE = 2048;
	
	public static final String COMPOSITE_ID_SEPARATOR = "#";
	
	public static final String PROP_RETRY_FAILED_ITEMS = "retry.failed.items";
	
	public static final String PROP_MAX_CONN_POOL_SIZE = "connection.max.pool.size";
	
	public static final String PROP_TEST_MERGE_TABLES = "test.merge.tables";
	
	public static final String DISABLE_KEYS = "SET FOREIGN_KEY_CHECKS=0";
	
	public static final String ENABLE_KEYS = "SET FOREIGN_KEY_CHECKS=1";
	
	public static final List<String> PRIORITY_TABLES = List.of("users", "provider", "person", "patient", "visit",
	    "encounter", "orders", "obs");
	
	public static final List<String> TABLES_WITHOUT_AUTO_INCREMENT = List.of("privilege", "role");
	
	public static final String STEP_NAME_PARENT_OBS = "Parent Obs";
	
	public static final String STEP_NAME_PREVIOUS_OBS = "Previous Obs";
	
	public static final String STEP_NAME_ALL_OBS = "All Obs";
	
	public static final String PARENT_OBS_CLAUSE = "obs_id IN (SELECT obs_group_id FROM obs)";
	
	public static final String PREVIOUS_OBS_CLAUSE = "obs_id IN (SELECT previous_version FROM obs)";
	
}
