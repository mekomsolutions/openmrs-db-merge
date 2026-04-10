package net.mekomsolutions.db.importer;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

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
		for (Future<Retry> future : items) {
			try {
				Retry retry = future.get();
				if (retry != null) {
					final String sql = ImportUtils.getWriteSql(retry.table());
					ItemWriter<Future<Row>> rowWriter = stepFactory.createWriter(sql, prepStmtParamSetter);
					Future<Row> f = CompletableFuture.supplyAsync(() -> retry.row());
					log.info("Writing retry {} for row: {}", retry.retryId(), retry.row());
					rowWriter.write(new Chunk<>(List.of(f)));
				}
			}
			catch (ExecutionException e) {
				throw new RuntimeException(e);
			}
		}
	}
	
}
