package com.pdf.generator.controller;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import com.pdf.generator.service.QuotationOutputService;
import com.pdf.generator.service.StoredQuotation;
import com.pdf.generator.service.TemplateStorageService;

@RestController
@RequestMapping("/api/pdf")
public class PdfController {

	private static final Logger log = LoggerFactory.getLogger(PdfController.class);

	private final TemplateStorageService templateStorageService;
	private final HtmlMergeService htmlMergeService;
	private final PdfRenderingService pdfRenderingService;
	private final QuotationOutputService quotationOutputService;

	public PdfController(TemplateStorageService templateStorageService, HtmlMergeService htmlMergeService,
			PdfRenderingService pdfRenderingService, QuotationOutputService quotationOutputService) {
		this.templateStorageService = templateStorageService;
		this.htmlMergeService = htmlMergeService;
		this.pdfRenderingService = pdfRenderingService;
		this.quotationOutputService = quotationOutputService;
	}

	@PostMapping("/generate/{templateType}")
	public ResponseEntity<byte[]> generate(@PathVariable String templateType,
			@RequestBody(required = false) Map<String, Object> data) {
		long startedAt = System.currentTimeMillis();
		String templateHtml = templateStorageService.loadHtml(templateType);
		String columnsJson = templateStorageService.loadColumns(templateType);

		String mergedHtml = htmlMergeService.merge(templateHtml, data, columnsJson);
		long mergedAt = System.currentTimeMillis();

		byte[] pdf = pdfRenderingService.render(mergedHtml);
		StoredQuotation stored = quotationOutputService.store(templateType, data, pdf);
		long finishedAt = System.currentTimeMillis();

		log.info("Generated '{}' in {} ms (merge {} ms, render/store {} ms), {} KB; stored at '{}'",
			templateType, finishedAt - startedAt, mergedAt - startedAt, finishedAt - mergedAt,
			pdf.length / 1024, stored.path());

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_PDF);
		headers.setContentDisposition(ContentDisposition.attachment().filename(stored.fileName()).build());
		headers.set("X-Quotation-Path", stored.relativePath().toString().replace('\\', '/'));

		return ResponseEntity.ok().headers(headers).body(pdf);
	}

	@PostMapping(value = "/preview/{templateType}", produces = MediaType.TEXT_HTML_VALUE)
	public String preview(@PathVariable String templateType, @RequestBody(required = false) Map<String, Object> data) {
		String templateHtml = templateStorageService.loadHtml(templateType);
		String columnsJson = templateStorageService.loadColumns(templateType);
		return htmlMergeService.merge(templateHtml, data, columnsJson);
	}
}
