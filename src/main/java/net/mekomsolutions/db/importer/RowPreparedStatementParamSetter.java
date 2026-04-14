package net.mekomsolutions.db.importer;

import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.springframework.batch.item.database.ItemPreparedStatementSetter;
import org.springframework.jdbc.core.SqlTypeValue;
import org.springframework.jdbc.core.StatementCreatorUtils;
import org.springframework.stereotype.Component;

@Component
public class RowPreparedStatementParamSetter implements ItemPreparedStatementSetter<Row> {
	
	@Override
	public void setValues(Row row, PreparedStatement ps) throws SQLException {
		int counter = 1;
		for (Object value : row.values()) {
			//TODO Future Use the column sql type
			StatementCreatorUtils.setParameterValue(ps, counter++, SqlTypeValue.TYPE_UNKNOWN, value);
		}
	}
	
}
