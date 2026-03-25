package net.mekomsolutions.db.importer;

public record Column(String name, int sqlType, boolean nullable, int size, ForeignKey foreignKey) {
	
	@Override
	public boolean equals(Object object) {
		return name.equals(((Column) object).name);
	}
	
	@Override
	public int hashCode() {
		return name.hashCode();
	}
	
}
