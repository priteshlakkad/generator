package com.pdf.generator.dto.design;

public class ParameterInfo {

	private String name;
	private String className;

	public ParameterInfo() {
	}

	public ParameterInfo(String name, String className) {
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
