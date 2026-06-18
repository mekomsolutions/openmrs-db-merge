package net.mekomsolutions.db.merge;

public record ForeignKey(String name, String columnName, String referencedTable, String referencedColumn) {
	
	@Override
	public boolean equals(Object object) {
		return name.equals(((ForeignKey) object).name);
	}
	
	@Override
	public int hashCode() {
		return name.hashCode();
	}
	
}
