package net.mekomsolutions.db.importer;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * RetryWriter is an implementation of ItemWriter responsible for writing the retry rows to the sink
 * database tables.
 */
@Slf4j
@Component
public class RetryWriter implements ItemWriter<Future<Retry>> {
	
	private StepFactory stepFactory;
	
	private RowPreparedStatementParamSetter prepStmtParamSetter;
	
	public RetryWriter(StepFactory stepFactory, RowPreparedStatementParamSetter prepStmtParamSetter) {
		this.stepFactory = stepFactory;
		this.prepStmtParamSetter = prepStmtParamSetter;
	}
	
	@Override
	public void write(Chunk<? extends Future<Retry>> items) throws Exception {
		for (Future<Retry> f : items) {
			try {
				Retry retry = f.get();
				if (retry != null) {
					final String sql = ImportUtils.getWriteSql(retry.table());
					//TODO Use a batch item writer that takes a list of sql queries unlike JdbcBatchItemWriter
					//which takes a single sql query for a single table
					//TODO Don't create writers from here, instead use a ClassifierCompositeItemWriter
					ItemWriter<Future<Row>> rowWriter = stepFactory.createWriter(sql, prepStmtParamSetter);
					Future<Row> rowFuture = CompletableFuture.completedFuture(retry.row());
					if (log.isDebugEnabled()) {
						final String idLabel = ImportUtils.getIdentifierLabel(retry.table());
						log.debug("Retrying import of row in table {} with {} = {} associated with retry with id {}",
						    retry.table().name(), idLabel, retry.rowIdentifier(), retry.retryId());
					}
					
					rowWriter.write(new Chunk<>(List.of(rowFuture)));
				}
			}
			catch (ExecutionException e) {
				throw new RuntimeException(e);
			}
		}
	}
	
}
