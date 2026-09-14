package com.quoteweave.platform.exception;

public class TemplateAlreadyExistsException extends RuntimeException {

	public TemplateAlreadyExistsException(String templateType) {
		super("A template already exists for type '" + templateType + "'");
	}
}
