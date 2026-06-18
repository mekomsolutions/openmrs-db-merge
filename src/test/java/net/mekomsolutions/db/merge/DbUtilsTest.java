package net.mekomsolutions.db.merge;

import static java.sql.Types.BLOB;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Types;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class DbUtilsTest {
	
	final String TABLE = "test";
	
	@Test
	public void getPlaceHolder_shouldGenerateValueForVarcharColumn() {
		Column varcharColumn = Mockito.mock(Column.class);
		Mockito.when(varcharColumn.sqlType()).thenReturn(Types.VARCHAR);
		Mockito.when(varcharColumn.size()).thenReturn(Constants.TEMP_STRING.length());
		
		Object result = DbUtils.getPlaceHolder(varcharColumn, TABLE);
		
		assertEquals(Constants.TEMP_STRING, result);
	}
	
	@Test
	public void getPlaceHolder_shouldGenerateValueForVarcharColumnOfLimitedSize() {
		final int size = 2;
		Column varcharColumn = Mockito.mock(Column.class);
		Mockito.when(varcharColumn.sqlType()).thenReturn(Types.VARCHAR);
		Mockito.when(varcharColumn.size()).thenReturn(size);
		
		Object result = DbUtils.getPlaceHolder(varcharColumn, TABLE);
		
		assertEquals(Constants.TEMP_CHAR.repeat(size), result);
	}
	
	@Test
	public void getPlaceHolder_shouldGenerateValueForIntegerColumn() {
		Column integerColumn = Mockito.mock(Column.class);
		Mockito.when(integerColumn.sqlType()).thenReturn(Types.INTEGER);
		
		Object result = DbUtils.getPlaceHolder(integerColumn, TABLE);
		
		assertEquals(0, result);
	}
	
	@Test
	public void getPlaceHolder_shouldGenerateValueForBitColumn() {
		Column integerColumn = Mockito.mock(Column.class);
		Mockito.when(integerColumn.sqlType()).thenReturn(Types.BIT);
		
		Object result = DbUtils.getPlaceHolder(integerColumn, TABLE);
		
		assertEquals(0, result);
	}
	
	@Test
	public void getPlaceHolder_shouldGenerateValueForDoubleColumn() {
		Column doubleColumn = Mockito.mock(Column.class);
		Mockito.when(doubleColumn.sqlType()).thenReturn(Types.DOUBLE);
		
		Object result = DbUtils.getPlaceHolder(doubleColumn, TABLE);
		
		assertEquals(0.0, result);
	}
	
	@Test
	public void getPlaceHolder_shouldGenerateValueForBooleanColumn() {
		Column booleanColumn = Mockito.mock(Column.class);
		Mockito.when(booleanColumn.sqlType()).thenReturn(Types.BOOLEAN);
		
		Object result = DbUtils.getPlaceHolder(booleanColumn, TABLE);
		
		assertEquals(false, result);
	}
	
	@Test
	public void getPlaceHolder_shouldGenerateValueForTimeColumn() {
		Column timeColumn = Mockito.mock(Column.class);
		Mockito.when(timeColumn.sqlType()).thenReturn(Types.TIME);
		
		Object result = DbUtils.getPlaceHolder(timeColumn, TABLE);
		
		assertEquals(Constants.MIDNIGHT, result);
	}
	
	@Test
	public void getPlaceHolder_shouldGenerateValueForDateColumn() {
		Column dateColumn = Mockito.mock(Column.class);
		Mockito.when(dateColumn.sqlType()).thenReturn(Types.DATE);
		
		Object result = DbUtils.getPlaceHolder(dateColumn, TABLE);
		
		assertEquals(Constants.EPOCH_DATE, result);
	}
	
	@Test
	public void getPlaceHolder_shouldGenerateValueForTimestampColumn() {
		Column timestampColumn = Mockito.mock(Column.class);
		Mockito.when(timestampColumn.sqlType()).thenReturn(Types.TIMESTAMP);
		
		Object result = DbUtils.getPlaceHolder(timestampColumn, TABLE);
		
		assertEquals(Constants.EPOCH, result);
	}
	
	@Test
	public void getPlaceHolder_shouldFailForUnSupportedColumnType() {
		final String colName = "test_col";
		Column unknownColumn = Mockito.mock(Column.class);
		Mockito.when(unknownColumn.sqlType()).thenReturn(BLOB);
		Mockito.when(unknownColumn.name()).thenReturn(colName);
		Exception e = assertThrows(RuntimeException.class, () -> DbUtils.getPlaceHolder(unknownColumn, TABLE));
		assertEquals(
		    "Don't know how to generate placeholder value for column " + TABLE + "." + colName + " of type: " + BLOB,
		    e.getMessage());
	}
	
}
