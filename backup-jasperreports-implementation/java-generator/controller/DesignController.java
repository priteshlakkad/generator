package com.pdf.generator.controller;

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
import org.springframework.web.bind.annotation.RestController;

import com.pdf.generator.dto.TemplateInfo;
import com.pdf.generator.dto.design.BlankTemplateSpec;
import com.pdf.generator.dto.design.FieldInfo;
import com.pdf.generator.dto.design.ParameterInfo;
import com.pdf.generator.dto.design.ReportDesignDto;
import com.pdf.generator.service.ReportDesignService;
import com.pdf.generator.service.TemplateStorageService;

import net.sf.jasperreports.engine.design.JasperDesign;

@RestController
@RequestMapping("/api/templates/{templateType}")
public class DesignController {

	private final TemplateStorageService templateStorageService;
	private final ReportDesignService reportDesignService;

	public DesignController(TemplateStorageService templateStorageService, ReportDesignService reportDesignService) {
		this.templateStorageService = templateStorageService;
		this.reportDesignService = reportDesignService;
	}

	@PostMapping("/blank")
	public ResponseEntity<TemplateInfo> createBlank(@PathVariable String templateType,
			@RequestBody(required = false) BlankTemplateSpec spec) {
		TemplateInfo info = templateStorageService.createBlank(templateType, spec);
		return ResponseEntity.status(HttpStatus.CREATED).body(info);
	}

	@GetMapping("/design")
	public ReportDesignDto getDesign(@PathVariable String templateType) {
		JasperDesign design = templateStorageService.loadDesign(templateType);
		return reportDesignService.toDto(templateType, design);
	}

	@PutMapping("/design")
	public ReportDesignDto updateDesign(@PathVariable String templateType, @RequestBody ReportDesignDto patch) {
		JasperDesign design = templateStorageService.loadDesign(templateType);
		reportDesignService.applyDesign(design, patch);
		templateStorageService.saveDesign(templateType, design);

		JasperDesign saved = templateStorageService.loadDesign(templateType);
		return reportDesignService.toDto(templateType, saved);
	}

	@GetMapping(value = "/jrxml", produces = MediaType.TEXT_XML_VALUE)
	public String getJrxmlSource(@PathVariable String templateType) {
		return templateStorageService.loadJrxmlSource(templateType);
	}

	@PostMapping("/parameters")
	public ResponseEntity<ParameterInfo> addParameter(@PathVariable String templateType, @RequestBody ParameterInfo request) {
		JasperDesign design = templateStorageService.loadDesign(templateType);
		reportDesignService.addParameter(design, request.getName(), request.getClassName());
		templateStorageService.saveDesign(templateType, design);
		return ResponseEntity.status(HttpStatus.CREATED).body(request);
	}

	@DeleteMapping("/parameters/{name}")
	public ResponseEntity<Void> removeParameter(@PathVariable String templateType, @PathVariable String name) {
		JasperDesign design = templateStorageService.loadDesign(templateType);
		reportDesignService.removeParameter(design, name);
		templateStorageService.saveDesign(templateType, design);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/fields")
	public ResponseEntity<FieldInfo> addField(@PathVariable String templateType, @RequestBody FieldInfo request) {
		JasperDesign design = templateStorageService.loadDesign(templateType);
		reportDesignService.addField(design, request.getName(), request.getClassName());
		templateStorageService.saveDesign(templateType, design);
		return ResponseEntity.status(HttpStatus.CREATED).body(request);
	}

	@DeleteMapping("/fields/{name}")
	public ResponseEntity<Void> removeField(@PathVariable String templateType, @PathVariable String name) {
		JasperDesign design = templateStorageService.loadDesign(templateType);
		reportDesignService.removeField(design, name);
		templateStorageService.saveDesign(templateType, design);
		return ResponseEntity.noContent().build();
	}
}
