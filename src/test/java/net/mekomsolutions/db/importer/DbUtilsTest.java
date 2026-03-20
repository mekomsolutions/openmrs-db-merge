package net.mekomsolutions.db.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Types;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class DbUtilsTest {
	
	@Test
	public void generateValue_shouldGenerateValueForVarcharColumn() {
		Column varcharColumn = Mockito.mock(Column.class);
		Mockito.when(varcharColumn.sqlType()).thenReturn(Types.VARCHAR);
		
		Object result = DbUtils.generateValue(varcharColumn);
		
		assertEquals(Constants.PH_STRING, result);
	}
	
	@Test
	public void generateValue_shouldGenerateValueForIntegerColumn() {
		Column integerColumn = Mockito.mock(Column.class);
		Mockito.when(integerColumn.sqlType()).thenReturn(Types.INTEGER);
		
		Object result = DbUtils.generateValue(integerColumn);
		
		assertEquals(0, result);
	}
	
	@Test
	public void generateValue_shouldGenerateValueForDoubleColumn() {
		Column doubleColumn = Mockito.mock(Column.class);
		Mockito.when(doubleColumn.sqlType()).thenReturn(Types.DOUBLE);
		
		Object result = DbUtils.generateValue(doubleColumn);
		
		assertEquals(0.0, result);
	}
	
	@Test
	public void generateValue_shouldGenerateValueForBooleanColumn() {
		Column booleanColumn = Mockito.mock(Column.class);
		Mockito.when(booleanColumn.sqlType()).thenReturn(Types.BOOLEAN);
		
		Object result = DbUtils.generateValue(booleanColumn);
		
		assertEquals(false, result);
	}
	
	@Test
	public void generateValue_shouldGenerateValueForTimeColumn() {
		Column timeColumn = Mockito.mock(Column.class);
		Mockito.when(timeColumn.sqlType()).thenReturn(Types.TIME);
		
		Object result = DbUtils.generateValue(timeColumn);
		
		assertEquals(Constants.MIDNIGHT, result);
	}
	
	@Test
	public void generateValue_shouldGenerateValueForDateColumn() {
		Column dateColumn = Mockito.mock(Column.class);
		Mockito.when(dateColumn.sqlType()).thenReturn(Types.DATE);
		
		Object result = DbUtils.generateValue(dateColumn);
		
		assertEquals(Constants.EPOCH_DATE, result);
	}
	
	@Test
	public void generateValue_shouldGenerateValueForTimestampColumn() {
		Column timestampColumn = Mockito.mock(Column.class);
		Mockito.when(timestampColumn.sqlType()).thenReturn(Types.TIMESTAMP);
		
		Object result = DbUtils.generateValue(timestampColumn);
		
		assertEquals(Constants.EPOCH, result);
	}
	
	@Test
	public void generateValue_shouldFailForUnSupportedColumnType() {
		Column unknownColumn = Mockito.mock(Column.class);
		Mockito.when(unknownColumn.sqlType()).thenReturn(Types.BLOB);
		Exception e = assertThrows(RuntimeException.class, () -> DbUtils.generateValue(unknownColumn));
		assertEquals("Don't know how to generate value for column of sql type: " + Types.BLOB, e.getMessage());
	}
	
}
