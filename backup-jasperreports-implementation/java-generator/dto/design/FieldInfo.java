package com.pdf.generator.dto.design;

public class FieldInfo {

	private String name;
	private String className;

	public FieldInfo() {
	}

	public FieldInfo(String name, String className) {
		this.name = name;
		this.className = className;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getClassName() {
		return className;
	}

	public void setClassName(String className) {
		this.className = className;
	}
}
