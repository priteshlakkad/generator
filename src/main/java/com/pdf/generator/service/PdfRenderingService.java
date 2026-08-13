package com.pdf.generator.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.springframework.stereotype.Service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.pdf.generator.exception.PdfGenerationException;

@Service
public class PdfRenderingService {

	public byte[] render(String mergedHtml) {
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			PdfRendererBuilder builder = new PdfRendererBuilder();
			builder.withHtmlContent(mergedHtml, null);
			builder.toStream(out);
			builder.run();
			return out.toByteArray();
		} catch (IOException e) {
			throw new PdfGenerationException("Failed to render PDF: " + e.getMessage(), e);
		}
	}
}
