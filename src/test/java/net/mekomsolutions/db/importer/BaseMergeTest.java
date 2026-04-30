package net.mekomsolutions.db.importer;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import net.mekomsolutions.db.importer.batch.BatchConfig;

@Import(BatchConfig.class)
@TestPropertySource(properties = "tables.exclude.file.path=classpath:exclude_tables.txt")
@TestPropertySource(properties = "batch.read.size=10")
@TestPropertySource(properties = "batch.write.size=10")
@ComponentScan(basePackages = { "net.mekomsolutions.db.importer.helpers" })
public abstract class BaseMergeTest extends BaseDbBackedTest {}
