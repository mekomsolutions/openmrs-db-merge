package net.mekomsolutions.db.importer.batch;

import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.annotation.AfterStep;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import net.mekomsolutions.db.importer.MergeUtils;
import net.mekomsolutions.db.importer.MetadataExtractor;
import net.mekomsolutions.db.importer.Table;

/**
 * This {@code org.springframework.batch.core.StepExecutionListener} is responsible for adding
 * source to sink primary key value mappings after a candidate table merge completes.
 */
@Slf4j
@Component
public class TableStepListener {
	
	protected ForeignKeyValueMapCache fkValueMapCache;
	
	protected MetadataExtractor metadataExtractor;
	
	public TableStepListener(ForeignKeyValueMapCache fkValueMapCache,
	    @Qualifier("sourceExtractor") MetadataExtractor metadataExtractor) {
		this.fkValueMapCache = fkValueMapCache;
		this.metadataExtractor = metadataExtractor;
	}
	
	@AfterStep
	public void afterStep(StepExecution stepExecution) {
		String tableName = stepExecution.getStepName();
		if (MergeUtils.isSubclassTable(tableName)) {
			final Table table = metadataExtractor.getTable(tableName, false);
			tableName = table.getColumn(table.primaryKeys().get(0)).foreignKey().referencedTable();
			if (log.isDebugEnabled()) {
				log.debug("Deferring to parent table {} instead of {} for id mappings", tableName,
				    stepExecution.getStepName());
			}
		}
		
		if (fkValueMapCache.isMappingCandidate(tableName)) {
			fkValueMapCache.addMappings(tableName);
		}
	}
	
}
