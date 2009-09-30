package cspom.variable;

import java.util.List;

public class UnknownDomain implements Domain {

	public static enum Type {
		BOOLEAN, INTEGER, UNKNOWN
	}

	private Type type = Type.UNKNOWN;

	@Override
	public String getName() {
		return null;
	}

	public void setType(Type type) {
		this.type = type;
	}

	public Type getType() {
		return type;
	}

	@Override
	public int getNbValues() {
		throw new UnsupportedOperationException();
	}

	public List<Number> getValues() {
		throw new UnsupportedOperationException();
	}

}
