package com.pdf.generator.dto;

public class TemplateInfo {

	private final String templateType;
	private final long sizeBytes;
	private final long lastModifiedEpochMillis;

	public TemplateInfo(String templateType, long sizeBytes, long lastModifiedEpochMillis) {
		this.templateType = templateType;
		this.sizeBytes = sizeBytes;
		this.lastModifiedEpochMillis = lastModifiedEpochMillis;
	}

	public String getTemplateType() {
		return templateType;
	}

	public long getSizeBytes() {
		return sizeBytes;
	}

	public long getLastModifiedEpochMillis() {
		return lastModifiedEpochMillis;
	}
}
