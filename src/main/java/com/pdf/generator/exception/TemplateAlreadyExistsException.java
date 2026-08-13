package com.pdf.generator.exception;

public class TemplateAlreadyExistsException extends RuntimeException {

	public TemplateAlreadyExistsException(String templateType) {
		super("A template already exists for type '" + templateType + "'");
	}
}
