package net.mekomsolutions.db.importer;

public record Retry(Integer retryId, String rowIdentifier, Table table, Row row) {
}
