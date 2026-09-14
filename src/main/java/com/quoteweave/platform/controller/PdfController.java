package com.quoteweave.platform.controller;

import java.util.List;
import java.util.Locale;
import java.util.Map;
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

import com.quoteweave.platform.service.HtmlMergeService;
import com.quoteweave.platform.service.PdfRenderingService;
import com.quoteweave.platform.service.QuotationOutputService;
import com.quoteweave.platform.service.StoredQuotation;
import com.quoteweave.platform.service.TemplateStorageService;

@RestController
@RequestMapping("/api/pdf")
public class PdfController {

	private static final Logger performanceLog = LoggerFactory.getLogger("performance.quotation");

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
		String generationId = UUID.randomUUID().toString();
		int lineItems = countLineItems(data);
		int processedRows = 0;
		String metricQuoteNumber = quoteNumber(data);
		String stage = "template-load";
		long startedAt = System.nanoTime();
		try {
			String templateHtml = templateStorageService.loadHtml(templateType);
			String columnsJson = templateStorageService.loadColumns(templateType);
			long loadedAt = System.nanoTime();

			stage = "html-merge";
			String mergedHtml = htmlMergeService.merge(templateHtml, data, columnsJson);
			processedRows = lineItems;
			long mergedAt = System.nanoTime();

			stage = "pdf-render";
			byte[] pdf = pdfRenderingService.render(mergedHtml);
			long renderedAt = System.nanoTime();

			stage = "file-store";
			StoredQuotation stored = quotationOutputService.store(templateType, data, pdf);
			long storedAt = System.nanoTime();

			double loadMs = elapsedMillis(startedAt, loadedAt);
			double mergeMs = elapsedMillis(loadedAt, mergedAt);
			double renderMs = elapsedMillis(mergedAt, renderedAt);
			double storeMs = elapsedMillis(renderedAt, storedAt);
			double totalMs = elapsedMillis(startedAt, storedAt);
			performanceLog.info(
				"""

				| Quotation performance | Value |
				|-----------------------|-------|
				| Status                | SUCCESS |
				| Generation ID         | {} |
				| Template              | {} |
				| Quote number          | {} |
				| Submitted rows        | {} |
				| Processed rows        | {} |
				| Template load         | {} |
				| HTML merge            | {} |
				| PDF render            | {} |
				| File store            | {} |
				| Total                 | {} |
				| PDF size              | {} kb |
				| Output path           | {} |""",
				generationId, metricValue(templateType), metricQuoteNumber, lineItems, processedRows,
				formatDuration(loadMs), formatDuration(mergeMs), formatDuration(renderMs),
				formatDuration(storeMs), formatDuration(totalMs), pdf.length/1024,
				metricValue(stored.relativePath()));

			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_PDF);
			headers.setContentDisposition(ContentDisposition.attachment().filename(stored.fileName()).build());
			headers.set("X-Quotation-Path", stored.relativePath().toString().replace('\\', '/'));
			headers.set("X-Generation-Id", generationId);
			headers.set("X-Generation-Time-Ms", formatMillis(totalMs));
			headers.set("X-Processed-Rows", Integer.toString(processedRows));
			headers.set("Server-Timing", serverTiming(loadMs, mergeMs, renderMs, storeMs, totalMs));

			return ResponseEntity.ok().headers(headers).body(pdf);
		} catch (RuntimeException failure) {
			performanceLog.warn(
				"""

				| Quotation performance | Value |
				|-----------------------|-------|
				| Status                | FAILURE |
				| Generation ID         | {} |
				| Engine                | openhtmltopdf |
				| Template              | {} |
				| Quote number          | {} |
				| Submitted rows        | {} |
				| Processed rows        | {} |
				| Failed stage          | {} |
				| Total                 | {} |
				| Error type            | {} |
				| Error message         | {} |""",
				generationId, metricValue(templateType), metricQuoteNumber, lineItems, processedRows,
				stage, formatDuration(elapsedMillis(startedAt, System.nanoTime())),
				failure.getClass().getSimpleName(), metricValue(failure.getMessage()));
			throw failure;
		}
	}

	@PostMapping(value = "/preview/{templateType}", produces = MediaType.TEXT_HTML_VALUE)
	public String preview(@PathVariable String templateType, @RequestBody(required = false) Map<String, Object> data) {
		String templateHtml = templateStorageService.loadHtml(templateType);
		String columnsJson = templateStorageService.loadColumns(templateType);
		return htmlMergeService.merge(templateHtml, data, columnsJson);
	}

	private int countLineItems(Map<String, Object> data) {
		if (data == null) return 0;
		int count = data.get("items") instanceof List<?> items ? items.size() : 0;
		if (data.get("sections") instanceof List<?> sections) {
			for (Object section : sections) {
				if (section instanceof Map<?, ?> values && values.get("items") instanceof List<?> items) {
					count += items.size();
				}
			}
		}
		return count;
	}

	private String quoteNumber(Map<String, Object> data) {
		if (data != null) {
			for (String field : List.of("quoteNumber", "quotationNumber", "quoteNo")) {
				Object value = data.get(field);
				if (value != null && !value.toString().isBlank()) return metricValue(value);
			}
		}
		return "generated";
	}

	private String metricValue(Object value) {
		String text = value == null ? "none" : value.toString().trim()
			.replaceAll("[\\r\\n\\t =]+", "_")
			.replace('"', '_');
		if (text.isBlank()) return "none";
		return text.substring(0, Math.min(text.length(), 160));
	}

	private double elapsedMillis(long startedAt, long finishedAt) {
		return Math.round((finishedAt - startedAt) / 1_000.0) / 1_000.0;
	}

	private String formatMillis(double value) {
		return String.format(Locale.ROOT, "%.3f", value);
	}

	private String formatDuration(double value) {
		return formatMillis(value) + " ms";
	}

	private String serverTiming(double load, double merge, double render, double store, double total) {
		return "template;dur=" + formatMillis(load)
			+ ", merge;dur=" + formatMillis(merge)
			+ ", render;dur=" + formatMillis(render)
			+ ", store;dur=" + formatMillis(store)
			+ ", total;dur=" + formatMillis(total);
	}
}
