package net.mekomsolutions.db.importer;

public record Column(String columnName, String columnType, boolean isNullable, int columnSize, ForeignKey foreignKey) {
}
