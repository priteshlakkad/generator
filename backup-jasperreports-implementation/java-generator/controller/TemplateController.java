package com.pdf.generator.controller;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.pdf.generator.dto.TemplateInfo;
import com.pdf.generator.service.TemplateStorageService;

@RestController
@RequestMapping("/api/templates")
public class TemplateController {

	private final TemplateStorageService templateStorageService;

	public TemplateController(TemplateStorageService templateStorageService) {
		this.templateStorageService = templateStorageService;
	}

	@PostMapping("/{templateType}")
	public ResponseEntity<TemplateInfo> upload(@PathVariable String templateType,
			@RequestParam("file") MultipartFile file) throws IOException {
		TemplateInfo info = templateStorageService.save(templateType, file.getInputStream());
		return ResponseEntity.status(HttpStatus.CREATED).body(info);
	}

	@GetMapping
	public List<TemplateInfo> list() {
		return templateStorageService.list();
	}

	@DeleteMapping("/{templateType}")
	public ResponseEntity<Void> delete(@PathVariable String templateType) {
		templateStorageService.delete(templateType);
		return ResponseEntity.noContent().build();
	}
}
