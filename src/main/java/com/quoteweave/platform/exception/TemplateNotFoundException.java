package com.quoteweave.platform.exception;

public class TemplateNotFoundException extends RuntimeException {

	public TemplateNotFoundException(String templateType) {
		super("No template found for type '" + templateType + "'");
	}
}
