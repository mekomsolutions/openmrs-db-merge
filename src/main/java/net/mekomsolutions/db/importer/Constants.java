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
	
	public static final Date EPOCH_DATE = Date.valueOf(LocalDate.of(1970, Month.JANUARY, 1));
	
	public static final Timestamp EPOCH = Timestamp.valueOf(LocalDateTime.of(1970, Month.JANUARY, 1, 0, 0, 0));
	
	public static final Time MIDNIGHT = Time.valueOf(LocalTime.MIDNIGHT);
	
	public static final String TEMP_STRING = "[TEMP_STRING]";
	
	public static final String TEMP_CHAR = "#";
	
	public static final String PHANTOM_UUID = "[PHANTOM_UUID]";
	
	public static final String DAEMON_USER_UUID = "A4F30A1B-5EB9-11DF-A648-37A07F9C90FB";
	
	public static final List<String> SUBCLASS_TABLES = List.of("patient", "drug_order", "test_order");
	
}
