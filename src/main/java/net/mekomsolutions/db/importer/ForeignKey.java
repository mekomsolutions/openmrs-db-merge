package net.mekomsolutions.db.importer;

public record ForeignKey(String name, String columnName, String referenceTable, String referencedColumn) {
	
	@Override
	public boolean equals(Object object) {
		return name.equalsIgnoreCase(((ForeignKey) object).name);
	}
	
	@Override
	public int hashCode() {
		return name.hashCode();
	}
	
}
