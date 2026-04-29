package net.mekomsolutions.db.importer.batch;

import static java.util.stream.Collectors.groupingBy;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import net.mekomsolutions.db.importer.Retry;
import net.mekomsolutions.db.importer.Row;

/**
 * RetryWriter is an implementation of ItemWriter responsible for writing the retry rows to the sink
 * database tables.
 */
@Slf4j
@Component
public class RetryWriter implements ItemWriter<Retry> {
	
	private StepFactory stepFactory;
	
	public RetryWriter(StepFactory stepFactory) {
		this.stepFactory = stepFactory;
	}
	
	@Override
	public void write(Chunk<? extends Retry> chunk) throws Exception {
		Map<String, List<Retry>> tableRetriesMap = chunk.getItems().stream().collect(groupingBy(r -> r.table().name()));
		for (Map.Entry<String, List<Retry>> e : tableRetriesMap.entrySet()) {
			List<Row> rows = e.getValue().stream().map(Retry::row).collect(Collectors.toList());
			log.info("Re-processing {} failed rows from table {}", rows.size(), e.getKey());
			stepFactory.getBatchWriter(e.getKey()).write(new Chunk<>(rows));
		}
	}
	
}
