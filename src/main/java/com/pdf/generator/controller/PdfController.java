package com.pdf.generator.controller;

import java.util.Map;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pdf.generator.service.HtmlMergeService;
import com.pdf.generator.service.PdfRenderingService;
import com.pdf.generator.service.TemplateStorageService;

@RestController
@RequestMapping("/api/pdf")
public class PdfController {

	private final TemplateStorageService templateStorageService;
	private final HtmlMergeService htmlMergeService;
	private final PdfRenderingService pdfRenderingService;

	public PdfController(TemplateStorageService templateStorageService, HtmlMergeService htmlMergeService,
			PdfRenderingService pdfRenderingService) {
		this.templateStorageService = templateStorageService;
		this.htmlMergeService = htmlMergeService;
		this.pdfRenderingService = pdfRenderingService;
	}

	@PostMapping("/generate/{templateType}")
	public ResponseEntity<byte[]> generate(@PathVariable String templateType,
			@RequestBody(required = false) Map<String, Object> data) {
		String templateHtml = templateStorageService.loadHtml(templateType);
		String mergedHtml = htmlMergeService.merge(templateHtml, data);
		byte[] pdf = pdfRenderingService.render(mergedHtml);

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_PDF);
		headers.setContentDisposition(ContentDisposition.attachment().filename(templateType + ".pdf").build());

		return ResponseEntity.ok().headers(headers).body(pdf);
	}

	@PostMapping(value = "/preview/{templateType}", produces = MediaType.TEXT_HTML_VALUE)
	public String preview(@PathVariable String templateType, @RequestBody(required = false) Map<String, Object> data) {
		String templateHtml = templateStorageService.loadHtml(templateType);
		return htmlMergeService.merge(templateHtml, data);
	}
}
