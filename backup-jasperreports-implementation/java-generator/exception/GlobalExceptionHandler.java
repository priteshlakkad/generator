package com.pdf.generator.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(TemplateNotFoundException.class)
	public ResponseEntity<Map<String, String>> handleNotFound(TemplateNotFoundException e) {
		return error(HttpStatus.NOT_FOUND, e.getMessage());
	}

	@ExceptionHandler(TemplateCompileException.class)
	public ResponseEntity<Map<String, String>> handleCompileError(TemplateCompileException e) {
		return error(HttpStatus.BAD_REQUEST, e.getMessage());
	}

	@ExceptionHandler(TemplateAlreadyExistsException.class)
	public ResponseEntity<Map<String, String>> handleAlreadyExists(TemplateAlreadyExistsException e) {
		return error(HttpStatus.CONFLICT, e.getMessage());
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
		return error(HttpStatus.BAD_REQUEST, e.getMessage());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException e) {
		Map<String, String> fieldErrors = new LinkedHashMap<>();
		e.getBindingResult().getFieldErrors()
			.forEach(fe -> fieldErrors.put(fe.getField(), fe.getDefaultMessage()));
		Map<String, String> body = new LinkedHashMap<>();
		body.put("error", "Validation failed");
		body.putAll(fieldErrors);
		return ResponseEntity.badRequest().body(body);
	}

	@ExceptionHandler(PdfGenerationException.class)
	public ResponseEntity<Map<String, String>> handleGenerationError(PdfGenerationException e) {
		return error(HttpStatus.BAD_REQUEST, e.getMessage());
	}

	private ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
		return ResponseEntity.status(status).body(Map.of("error", message));
	}
}
