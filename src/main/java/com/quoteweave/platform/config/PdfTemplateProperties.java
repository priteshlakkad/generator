package com.quoteweave.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "quoteweave.templates")
public class PdfTemplateProperties {

	private String dir = "./template-store";

	public String getDir() {
		return dir;
	}

	public void setDir(String dir) {
		this.dir = dir;
	}
}
