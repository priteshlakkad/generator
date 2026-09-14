package com.pdf.generator.controller;

import java.util.Locale;
import java.util.UUID;

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

import com.pdf.generator.dto.ReportDataRequest;
import com.pdf.generator.service.PdfGenerationService;

@RestController
@RequestMapping("/api/pdf")
public class PdfController {
	private static final Logger performanceLog = LoggerFactory.getLogger("performance.quotation.jasper");

	private final PdfGenerationService pdfGenerationService;

	public PdfController(PdfGenerationService pdfGenerationService) {
		this.pdfGenerationService = pdfGenerationService;
	}

	@PostMapping("/generate/{templateType}")
	public ResponseEntity<byte[]> generate(@PathVariable String templateType,
			@RequestBody ReportDataRequest request) {
		String generationId = UUID.randomUUID().toString();
		int lineItems = request.getData() == null ? 0 : request.getData().size();
		long startedAt = System.nanoTime();
		try {
			byte[] pdf = pdfGenerationService.generate(templateType, request);
			double totalMs = Math.round((System.nanoTime() - startedAt) / 1_000.0) / 1_000.0;
			performanceLog.info(
				"""

				| Quotation performance | Value |
				|-----------------------|-------|
				| Status                | SUCCESS |
				| Generation ID         | {} |
				| Engine                | jasperreports |
				| Template              | {} |
				| Submitted rows        | {} |
				| Processed rows        | {} |
				| Total                 | {} ms |
				| PDF bytes             | {} |""",
				generationId, metricValue(templateType), lineItems, lineItems,
				String.format(Locale.ROOT, "%.3f", totalMs), pdf.length);

			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_PDF);
			headers.setContentDisposition(
				ContentDisposition.attachment().filename(templateType + ".pdf").build());
			headers.set("X-Generation-Id", generationId);
			headers.set("X-Generation-Time-Ms", String.format(Locale.ROOT, "%.3f", totalMs));
			headers.set("X-Processed-Rows", Integer.toString(lineItems));
			headers.set("Server-Timing", "total;dur=" + String.format(Locale.ROOT, "%.3f", totalMs));

			return ResponseEntity.ok().headers(headers).body(pdf);
		} catch (RuntimeException failure) {
			double totalMs = Math.round((System.nanoTime() - startedAt) / 1_000.0) / 1_000.0;
			performanceLog.warn(
				"""

				| Quotation performance | Value |
				|-----------------------|-------|
				| Status                | FAILURE |
				| Generation ID         | {} |
				| Engine                | jasperreports |
				| Template              | {} |
				| Submitted rows        | {} |
				| Failed stage          | generate |
				| Total                 | {} ms |
				| Error type            | {} |""",
				generationId, metricValue(templateType), lineItems,
				String.format(Locale.ROOT, "%.3f", totalMs), failure.getClass().getSimpleName());
			throw failure;
		}
	}

	private String metricValue(String value) {
		if (value == null || value.isBlank()) return "none";
		String cleaned = value.trim().replaceAll("[\\r\\n\\t =]+", "_").replace('"', '_');
		return cleaned.substring(0, Math.min(cleaned.length(), 160));
	}
}
