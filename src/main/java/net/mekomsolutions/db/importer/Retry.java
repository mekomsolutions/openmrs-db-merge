package net.mekomsolutions.db.importer;

public record Retry(Integer retryId, Table table, String rowIdentifier, Row row) {
}
