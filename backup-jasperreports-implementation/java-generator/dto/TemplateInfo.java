package com.pdf.generator.dto;

public class TemplateInfo {

	private final String templateType;
	private final long jrxmlSizeBytes;
	private final long lastModifiedEpochMillis;

	public TemplateInfo(String templateType, long jrxmlSizeBytes, long lastModifiedEpochMillis) {
		this.templateType = templateType;
		this.jrxmlSizeBytes = jrxmlSizeBytes;
		this.lastModifiedEpochMillis = lastModifiedEpochMillis;
	}

	public String getTemplateType() {
		return templateType;
	}

	public long getJrxmlSizeBytes() {
		return jrxmlSizeBytes;
	}

	public long getLastModifiedEpochMillis() {
		return lastModifiedEpochMillis;
	}
}
