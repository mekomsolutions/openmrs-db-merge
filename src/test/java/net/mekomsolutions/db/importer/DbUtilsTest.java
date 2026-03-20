package net.mekomsolutions.db.importer;

import static java.sql.Types.BLOB;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Types;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class DbUtilsTest {
	
	@Test
	public void getPlaceHolder_shouldGenerateValueForVarcharColumn() {
		Column varcharColumn = Mockito.mock(Column.class);
		Mockito.when(varcharColumn.sqlType()).thenReturn(Types.VARCHAR);
		
		Object result = DbUtils.getPlaceHolder(varcharColumn);
		
		assertEquals(Constants.PH_STRING, result);
	}
	
	@Test
	public void getPlaceHolder_shouldGenerateValueForIntegerColumn() {
		Column integerColumn = Mockito.mock(Column.class);
		Mockito.when(integerColumn.sqlType()).thenReturn(Types.INTEGER);
		
		Object result = DbUtils.getPlaceHolder(integerColumn);
		
		assertEquals(0, result);
	}
	
	@Test
	public void getPlaceHolder_shouldGenerateValueForDoubleColumn() {
		Column doubleColumn = Mockito.mock(Column.class);
		Mockito.when(doubleColumn.sqlType()).thenReturn(Types.DOUBLE);
		
		Object result = DbUtils.getPlaceHolder(doubleColumn);
		
		assertEquals(0.0, result);
	}
	
	@Test
	public void getPlaceHolder_shouldGenerateValueForBooleanColumn() {
		Column booleanColumn = Mockito.mock(Column.class);
		Mockito.when(booleanColumn.sqlType()).thenReturn(Types.BOOLEAN);
		
		Object result = DbUtils.getPlaceHolder(booleanColumn);
		
		assertEquals(false, result);
	}
	
	@Test
	public void getPlaceHolder_shouldGenerateValueForTimeColumn() {
		Column timeColumn = Mockito.mock(Column.class);
		Mockito.when(timeColumn.sqlType()).thenReturn(Types.TIME);
		
		Object result = DbUtils.getPlaceHolder(timeColumn);
		
		assertEquals(Constants.MIDNIGHT, result);
	}
	
	@Test
	public void getPlaceHolder_shouldGenerateValueForDateColumn() {
		Column dateColumn = Mockito.mock(Column.class);
		Mockito.when(dateColumn.sqlType()).thenReturn(Types.DATE);
		
		Object result = DbUtils.getPlaceHolder(dateColumn);
		
		assertEquals(Constants.EPOCH_DATE, result);
	}
	
	@Test
	public void getPlaceHolder_shouldGenerateValueForTimestampColumn() {
		Column timestampColumn = Mockito.mock(Column.class);
		Mockito.when(timestampColumn.sqlType()).thenReturn(Types.TIMESTAMP);
		
		Object result = DbUtils.getPlaceHolder(timestampColumn);
		
		assertEquals(Constants.EPOCH, result);
	}
	
	@Test
	public void getPlaceHolder_shouldFailForUnSupportedColumnType() {
		Column unknownColumn = Mockito.mock(Column.class);
		Mockito.when(unknownColumn.sqlType()).thenReturn(BLOB);
		Exception e = assertThrows(RuntimeException.class, () -> DbUtils.getPlaceHolder(unknownColumn));
		assertEquals("Don't know how to generate placeholder value for column of sql type: " + BLOB, e.getMessage());
	}
	
}
