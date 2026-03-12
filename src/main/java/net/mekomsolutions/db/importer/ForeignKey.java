package net.mekomsolutions.db.importer;

public record ForeignKey(String columnName, String referenceTable, String referencedColumn) {
}
