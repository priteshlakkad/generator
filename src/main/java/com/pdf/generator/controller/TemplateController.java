package com.pdf.generator.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pdf.generator.dto.TemplateInfo;
import com.pdf.generator.service.TemplateStorageService;

@RestController
@RequestMapping("/api/templates")
public class TemplateController {

	private final TemplateStorageService templateStorageService;

	public TemplateController(TemplateStorageService templateStorageService) {
		this.templateStorageService = templateStorageService;
	}

	@GetMapping
	public List<TemplateInfo> list() {
		return templateStorageService.list();
	}

	@PostMapping(value = "/{templateType}", consumes = MediaType.TEXT_PLAIN_VALUE)
	public ResponseEntity<TemplateInfo> upload(@PathVariable String templateType, @RequestBody String html) {
		TemplateInfo info = templateStorageService.save(templateType, html);
		return ResponseEntity.status(HttpStatus.CREATED).body(info);
	}

	@PostMapping("/{templateType}/blank")
	public ResponseEntity<TemplateInfo> createBlank(@PathVariable String templateType) {
		TemplateInfo info = templateStorageService.createBlank(templateType);
		return ResponseEntity.status(HttpStatus.CREATED).body(info);
	}

	@GetMapping(value = "/{templateType}/html", produces = MediaType.TEXT_PLAIN_VALUE)
	public String getHtml(@PathVariable String templateType) {
		return templateStorageService.loadHtml(templateType);
	}

	@PutMapping(value = "/{templateType}/html", consumes = MediaType.TEXT_PLAIN_VALUE)
	public TemplateInfo saveHtml(@PathVariable String templateType, @RequestBody String html) {
		return templateStorageService.save(templateType, html);
	}

	@DeleteMapping("/{templateType}")
	public ResponseEntity<Void> delete(@PathVariable String templateType) {
		templateStorageService.delete(templateType);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{templateType}/copy")
	public ResponseEntity<TemplateInfo> copy(@PathVariable String templateType, @RequestParam String newTemplateType) {
		TemplateInfo info = templateStorageService.copy(templateType, newTemplateType);
		return ResponseEntity.status(HttpStatus.CREATED).body(info);
	}

	@GetMapping(value = "/{templateType}/sample", produces = MediaType.APPLICATION_JSON_VALUE)
	public String getSampleData(@PathVariable String templateType) {
		return templateStorageService.loadSampleData(templateType);
	}

	@PutMapping(value = "/{templateType}/sample", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public String saveSampleData(@PathVariable String templateType, @RequestBody String json) {
		templateStorageService.saveSampleData(templateType, json);
		return json;
	}
}
