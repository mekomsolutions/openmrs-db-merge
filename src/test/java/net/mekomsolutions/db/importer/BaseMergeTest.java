package net.mekomsolutions.db.importer;

import java.time.LocalDateTime;

import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.job.SimpleJob;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.SqlConfig;

import lombok.extern.slf4j.Slf4j;
import net.mekomsolutions.db.importer.batch.BatchConfig;

@Import(BatchConfig.class)
@TestPropertySource(properties = "tables.exclude.file.path=classpath:exclude_tables.txt")
@TestPropertySource(properties = "batch.read.size=10")
@TestPropertySource(properties = "batch.write.size=10")
@ComponentScan(basePackages = { "net.mekomsolutions.db.importer.helpers" })
@Slf4j
@SqlConfig(dataSource = "sourceDataSource")
@TestPropertySource(properties = "test.merge.tables=" + TestConstants.TEST_MERGE_TABLES)
public abstract class BaseMergeTest extends BaseDbBackedTest {
	
	@Autowired
	private JobLauncher jobLauncher;
	
	@Autowired
	private SimpleJob job;
	
	protected void executeJob() throws Exception {
		if (job.getStepNames().isEmpty()) {
			log.info("No tables found containing data to import.");
			return;
		}
		
		log.info("Starting the job to import {} tables", job.getStepNames());
		JobParametersBuilder builder = new JobParametersBuilder().addLocalDateTime("timestamp", LocalDateTime.now());
		jobLauncher.run(job, builder.toJobParameters());
	}
	
}
