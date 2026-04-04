
package net.mekomsolutions.db.importer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class ImportUtilsTest {
	
	@Test
	public void isSubclassTable_shouldReturnTrueForSubclassTable() {
		assertTrue(ImportUtils.isSubclassTable("patient"));
		assertTrue(ImportUtils.isSubclassTable("test_order"));
		assertTrue(ImportUtils.isSubclassTable("drug_order"));
	}
	
	@Test
	public void isSubclassTable_shouldReturnFalseForNonSubclassTable() {
		assertFalse(ImportUtils.isSubclassTable("visit"));
	}
	
	@Test
	public void isExtensionTable_shouldReturnTrueForExtensionTable() {
		Table table = Mockito.mock(Table.class);
		Mockito.when(table.primaryKeys()).thenReturn(List.of("user_id", "property"));
		Mockito.when(table.columnNames()).thenReturn(List.of("user_id", "property", "property_value"));
		assertTrue(ImportUtils.isExtensionTable(table));
	}
	
	@Test
	public void isExtensionTable_shouldReturnFalseIfTableHasMoreThanThreeColumns() {
		Table table = Mockito.mock(Table.class);
		Mockito.when(table.primaryKeys()).thenReturn(List.of("user_id", "property"));
		Mockito.when(table.columnNames()).thenReturn(List.of("user_id", "property", "col_1", "col_2"));
		assertFalse(ImportUtils.isExtensionTable(table));
	}
	
	@Test
	public void isMappingTable_shouldReturnTrueIfTableHasOnlyTwoColumnsThatMakeUpThePrimaryKey() {
		Table table = Mockito.mock(Table.class);
		Mockito.when(table.primaryKeys()).thenReturn(List.of("col_1", "col_2"));
		Mockito.when(table.columnNames()).thenReturn(List.of("col_1", "col_2"));
		assertTrue(ImportUtils.isMappingTable(table));
	}
	
	@Test
	public void isMappingTable_shouldReturnTrueIfTableHasOnlyTwoColumnsThatMakeUpThePrimaryKeyAndAnyExtraColumns() {
		Table table = Mockito.mock(Table.class);
		Mockito.when(table.primaryKeys()).thenReturn(List.of("col_1", "col_2"));
		Mockito.when(table.columnNames()).thenReturn(List.of("col_1", "col_2", "col_3"));
		assertFalse(ImportUtils.isMappingTable(table));
	}
	
}
