package net.mekomsolutions.db.importer;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;

import org.springframework.batch.item.database.ItemPreparedStatementSetter;
import org.springframework.jdbc.core.SqlTypeValue;
import org.springframework.jdbc.core.StatementCreatorUtils;

public class RetryPreparedStatementParamSetter implements ItemPreparedStatementSetter<Map<String, Object>> {
	
	@Override
	public void setValues(Map<String, Object> item, PreparedStatement ps) throws SQLException {
		System.out.println("Setting params: retry: " + item);
		StatementCreatorUtils.setParameterValue(ps, 1, SqlTypeValue.TYPE_UNKNOWN, item.get("id"));
	}
	
}
