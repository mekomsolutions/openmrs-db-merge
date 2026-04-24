
package net.mekomsolutions.db.importer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class MergeUtilsTest {
	
	@Test
	public void isSubclassTable_shouldReturnTrueForSubclassTable() {
		assertTrue(MergeUtils.isSubclassTable("patient"));
		assertTrue(MergeUtils.isSubclassTable("test_order"));
		assertTrue(MergeUtils.isSubclassTable("drug_order"));
	}
	
	@Test
	public void isSubclassTable_shouldReturnFalseForNonSubclassTable() {
		assertFalse(MergeUtils.isSubclassTable("visit"));
	}
	
	@Test
	public void isExtensionTable_shouldReturnTrueForExtensionTable() {
		Table table = Mockito.mock(Table.class);
		Column col = Mockito.mock(Column.class);
		when(table.primaryKeys()).thenReturn(List.of("user_id", "property"));
		when(table.columns()).thenReturn(Map.of("user_id", col, "property", col, "property_value", col));
		assertTrue(MergeUtils.isExtensionTable(table));
	}
	
	@Test
	public void isExtensionTable_shouldReturnFalseIfTableHasMoreThanThreeColumns() {
		Table table = Mockito.mock(Table.class);
		Column col = Mockito.mock(Column.class);
		when(table.primaryKeys()).thenReturn(List.of("user_id", "property"));
		when(table.columns()).thenReturn(Map.of("user_id", col, "property", col, "col_1", col, "col_2", col));
		assertFalse(MergeUtils.isExtensionTable(table));
	}
	
	@Test
	public void isMappingTable_shouldReturnTrueIfTableHasOnlyTwoColumnsThatMakeUpThePrimaryKey() {
		Table table = Mockito.mock(Table.class);
		Column col = Mockito.mock(Column.class);
		when(table.primaryKeys()).thenReturn(List.of("col_1", "col_2"));
		when(table.columns()).thenReturn(Map.of("col_1", col, "col_2", col));
		assertTrue(MergeUtils.isMappingTable(table));
	}
	
	@Test
	public void isMappingTable_shouldReturnTrueIfTableHasOnlyTwoColumnsThatMakeUpThePrimaryKeyAndAnyExtraColumns() {
		Table table = Mockito.mock(Table.class);
		Column col = Mockito.mock(Column.class);
		when(table.primaryKeys()).thenReturn(List.of("col_1", "col_2"));
		when(table.columns()).thenReturn(Map.of("col_1", col, "col_2", col, "col_3", col));
		assertFalse(MergeUtils.isMappingTable(table));
	}
	
}
