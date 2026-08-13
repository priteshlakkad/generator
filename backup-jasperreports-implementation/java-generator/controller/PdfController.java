package com.pdf.generator.controller;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pdf.generator.dto.ReportDataRequest;
import com.pdf.generator.service.PdfGenerationService;

@RestController
@RequestMapping("/api/pdf")
public class PdfController {

	private final PdfGenerationService pdfGenerationService;

	public PdfController(PdfGenerationService pdfGenerationService) {
		this.pdfGenerationService = pdfGenerationService;
	}

	@PostMapping("/generate/{templateType}")
	public ResponseEntity<byte[]> generate(@PathVariable String templateType,
			@RequestBody ReportDataRequest request) {
		byte[] pdf = pdfGenerationService.generate(templateType, request);

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_PDF);
		headers.setContentDisposition(
			ContentDisposition.attachment().filename(templateType + ".pdf").build());

		return ResponseEntity.ok().headers(headers).body(pdf);
	}
}
