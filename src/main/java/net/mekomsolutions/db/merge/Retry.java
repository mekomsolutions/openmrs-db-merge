package net.mekomsolutions.db.merge;

public record Retry(Integer retryId, Table table, String rowIdentifier, Row row) {
}
