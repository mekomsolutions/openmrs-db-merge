package net.mekomsolutions.db.importer;

import java.util.function.BiFunction;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class ForeignKeyMapper implements BiFunction<Object, ForeignKey, Object> {
	
	private SourceDbHelper sourceDbHelper;
	
	private SinkDbHelper sinkDbHelper;
	
	public ForeignKeyMapper(SourceDbHelper sourceDbHelper, SinkDbHelper sinkDbHelper) {
		this.sourceDbHelper = sourceDbHelper;
		this.sinkDbHelper = sinkDbHelper;
	}
	
	@Override
	public Object apply(Object value, ForeignKey foreignKey) {
		final String refTableName = foreignKey.referenceTable();
		Object refUuid = sourceDbHelper.getUuid(refTableName, foreignKey.referencedColumn(), value);
		return sinkDbHelper.getColumnValue(refTableName, foreignKey.referencedColumn(), "uuid", refUuid);
	}
	
}
