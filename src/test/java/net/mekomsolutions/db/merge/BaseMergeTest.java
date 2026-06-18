package net.mekomsolutions.db.merge;

import static net.mekomsolutions.db.merge.Constants.FAILED_ITEM_TABLE;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.job.SimpleJob;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.SqlConfig;

import lombok.extern.slf4j.Slf4j;
import net.mekomsolutions.db.merge.batch.BatchConfig;
import net.mekomsolutions.db.merge.batch.ForeignKeyValueMapCache;
import net.mekomsolutions.db.merge.helpers.MgtDbHelper;
import net.mekomsolutions.db.merge.helpers.SinkDbHelper;
import net.mekomsolutions.db.merge.helpers.SourceDbHelper;

@Import(BatchConfig.class)
@TestPropertySource(properties = "tables.exclude.file.path=classpath:exclude_tables.txt")
@TestPropertySource(properties = "batch.read.size=10")
@TestPropertySource(properties = "batch.write.size=10")
@ComponentScan(basePackages = { "net.mekomsolutions.db.merge.helpers" })
@Slf4j
@SqlConfig(dataSource = "sourceDataSource")
@TestPropertySource(properties = "test.merge.tables=" + TestConstants.TEST_MERGE_TABLES)
public abstract class BaseMergeTest extends BaseDbBackedTest {
	
	private static final Timestamp TIMESTAMP = Timestamp.valueOf(LocalDateTime.now());
	
	@Autowired
	private JobLauncher jobLauncher;
	
	@Autowired
	private SimpleJob job;
	
	@Autowired
	protected JobExplorer jobExplorer;
	
	@Autowired
	protected JobRepository jobRepository;
	
	@Autowired
	@Qualifier("sourceJdbcTemplate")
	protected JdbcTemplate sourceJdbcTemplate;
	
	@Autowired
	@Qualifier("sinkJdbcTemplate")
	protected JdbcTemplate sinkJdbcTemplate;
	
	@Autowired
	@Qualifier("mgtJdbcTemplate")
	protected JdbcTemplate mgtJdbcTemplate;
	
	@Autowired
	protected SourceDbHelper sourceDbHelper;
	
	@Autowired
	protected SinkDbHelper sinkDbHelper;
	
	@Autowired
	protected MgtDbHelper mgtDbHelper;
	
	@Autowired
	@Qualifier("sourceExtractor")
	protected MetadataExtractor extractor;
	
	@Autowired
	@Qualifier("processorExecutor")
	private ThreadPoolTaskExecutor executor;
	
	@Autowired
	private ForeignKeyValueMapCache fkValueMapCache;
	
	@BeforeEach
	public void setup() {
		executor.initialize();
		executor.start();
	}
	
	@AfterEach
	public void tearDown() {
		fkValueMapCache.clearCache();
		MergeUtils.clearPhantomIdCache();
		executor.shutdown();
	}
	
	protected void executeJob() throws Exception {
		if (job.getStepNames().isEmpty()) {
			log.info("No tables found containing data to import.");
			return;
		}
		
		log.info("Starting the job to import {} tables", job.getStepNames());
		JobParametersBuilder builder = new JobParametersBuilder().addLocalDateTime("timestamp", LocalDateTime.now());
		jobLauncher.run(job, builder.toJobParameters());
	}
	
	protected Object getSourceReferencedRowId(Object sinkRowId, String primaryKeyColumn, ForeignKey fk) {
		//Get the database id of the referenced row in the source DB
		final Object uuid = sinkDbHelper.getColumnValue(fk.referencedTable(), "uuid", fk.referencedColumn(), sinkRowId);
		Map<String, Object> refSourceRow = sourceDbHelper.getRow(fk.referencedTable(), List.of("uuid"),
		    new Object[] { uuid });
		return refSourceRow.get(primaryKeyColumn);
	}
	
	protected void assertRow(Map<String, Object> sourceRow, Map<String, Object> sinkRow, Table table) {
		Assertions.assertEquals(sourceRow.size(), sinkRow.size(), "Column size mismatch");
		for (Map.Entry<String, Object> e : sourceRow.entrySet()) {
			final String col = e.getKey();
			final String pkCol = table.primaryKeys().get(0);
			Object sinkId = sinkRow.get(pkCol);
			Object sourceValue = e.getValue();
			Object sinkValue = sinkRow.get(col);
			if (e.getKey().equalsIgnoreCase(pkCol)) {
				//We don't expect database ids to be the same
				continue;
			} else if (sourceValue != null && table.getColumn(col).foreignKey() != null) {
				//Database ids will be different so instead compare uuids of the referenced rows.
				ForeignKey fk = table.getColumn(col).foreignKey();
				if (MergeUtils.isSubclassTable(fk.referencedTable())) {
					Table refTable = extractor.getTable(fk.referencedTable(), false);
					//Uuid is in the parent table, so use the foreign from subclass row to parent row be
					fk = refTable.getColumn(refTable.primaryKeys().get(0)).foreignKey();
				}
				sourceValue = sourceDbHelper.getUuid(fk.referencedTable(), fk.referencedColumn(), sourceRow.get(col));
				sinkValue = sinkDbHelper.getColumnValue(fk.referencedTable(), "uuid", fk.referencedColumn(),
				    sinkRow.get(col));
			}
			
			if (table.name().equalsIgnoreCase("users") && sourceRow.get(pkCol) == Integer.valueOf(1)) {
				if (col.equalsIgnoreCase("retired")) {
					sourceValue = true;
				} else if (col.equalsIgnoreCase("retired_by")) {
					sourceValue = MergeUtils.getDaemonUserId(sinkDbHelper);
				} else if (col.equalsIgnoreCase("retire_reason")) {
					sourceValue = Constants.RETIRE_REASON;
				} else if (col.equalsIgnoreCase("date_retired")) {
					Timestamp dateRetired = (Timestamp) sinkValue;
					Assertions.assertTrue(dateRetired.after(TIMESTAMP),
					    "Date retired in sink users table for merged admin user should be set to current timestamp");
					continue;
				}
			}
			
			final String msg = "Incorrect value for column: " + col + " for row with " + pkCol + ": " + sinkId
			        + " in sink table: " + table.name();
			Assertions.assertEquals(sourceValue, sinkValue, msg);
		}
	}
	
	protected void verifyRow(Map<String, Object> sinkRow, Table table) {
		final String uuid = (String) sinkRow.get("uuid");
		Map<String, Object> sourceRow;
		if (MergeUtils.isSubclassTable(table.name())) {
			final String pkCol = table.primaryKeys().get(0);
			ForeignKey fk = table.getColumn(pkCol).foreignKey();
			final String parentPkCol = extractor.getTable(fk.referencedTable(), false).primaryKeys().get(0);
			Object sourceParentRowId = getSourceReferencedRowId(sinkRow.get(pkCol), parentPkCol, fk);
			sourceRow = sourceDbHelper.getRow(table.name(), List.of(pkCol), new Object[] { sourceParentRowId });
		} else if (MergeUtils.isExtensionTable(table) || MergeUtils.isMappingTable(table)) {
			List<Object> values = new ArrayList<>(table.primaryKeys().size());
			for (String col : table.primaryKeys()) {
				Object value = sinkRow.get(col);
				ForeignKey fk = table.getColumn(col).foreignKey();
				if (fk != null) {
					value = getSourceReferencedRowId(value, col, fk);
				}
				values.add(value);
			}
			sourceRow = sourceDbHelper.getRow(table.name(), table.primaryKeys(), values.toArray());
		} else {
			sourceRow = sourceDbHelper.getRow(table.name(), List.of("uuid"), new Object[] { uuid });
		}
		
		assertRow(sourceRow, sinkRow, table);
	}
	
	protected void verifyRows(List<Map<String, Object>> rows, String tableName) {
		Table table = extractor.getTable(tableName, false);
		rows.forEach(row -> verifyRow(row, table));
	}
	
	protected int getFailureCount() {
		return mgtJdbcTemplate.queryForObject("SELECT count(*) FROM " + FAILED_ITEM_TABLE, Integer.class);
	}
	
	protected boolean hasFailure(String tableName, Object identifier) {
		String query = "SELECT count(*) FROM " + FAILED_ITEM_TABLE + " WHERE table_name = '" + tableName
		        + "' AND identifier = '" + identifier + "'";
		return mgtJdbcTemplate.queryForObject(query, Integer.class) > 0;
	}
	
}
