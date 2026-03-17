package net.mekomsolutions.db.importer;

public record Column(String name, String columnType, boolean isNullable, int columnSize, ForeignKey foreignKey) {
	
	@Override
	public boolean equals(Object object) {
		return name.equalsIgnoreCase(((Column) object).name);
	}
	
	@Override
	public int hashCode() {
		return name.hashCode();
	}
	
}
