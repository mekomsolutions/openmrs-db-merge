package net.mekomsolutions.db.merge;

public record Column(String name, int sqlType, boolean autoIncrement, boolean nullable, int size, ForeignKey foreignKey) {
	
	@Override
	public boolean equals(Object object) {
		return name.equals(((Column) object).name);
	}
	
	@Override
	public int hashCode() {
		return name.hashCode();
	}
	
}
