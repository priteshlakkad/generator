package com.quoteweave.platform.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.springframework.stereotype.Service;

import com.openhtmltopdf.extend.FSSupplier;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.quoteweave.platform.config.PdfRenderProperties;
import com.quoteweave.platform.exception.PdfGenerationException;

@Service
public class PdfRenderingService {

	/**
	 * Bundled so output does not depend on the host's installed fonts, and because the PDF base-14
	 * fonts have no rupee sign — quotations priced in INR render it as a blank box without this.
	 */
	private static final String FONT_FAMILY = "DejaVu Sans";

	private final PdfRenderProperties properties;
	private final RemoteResourceLoader resourceLoader;

	public PdfRenderingService(PdfRenderProperties properties, RemoteResourceLoader resourceLoader) {
		this.properties = properties;
		this.resourceLoader = resourceLoader;
	}

	public byte[] render(String mergedHtml) {
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			PdfRendererBuilder builder = new PdfRendererBuilder();
			builder.useFastMode();

			String baseUri = properties.getBaseUri();
			builder.withHtmlContent(mergedHtml, baseUri == null || baseUri.isBlank() ? null : baseUri);

			// Every outbound fetch goes through the loader, so caching, timeouts, the size cap and
			// the placeholder-on-failure behaviour apply uniformly.
			builder.useHttpStreamImplementation(resourceLoader);
			builder.useProtocolsStreamImplementation(resourceLoader, "data");

			registerFonts(builder);

			builder.toStream(out);
			builder.run();
			return out.toByteArray();
		} catch (IOException e) {
			throw new PdfGenerationException("Failed to render PDF: " + e.getMessage(), e);
		}
	}

	private void registerFonts(PdfRendererBuilder builder) {
		builder.useFont(classpathFont("/fonts/DejaVuSans.ttf"), FONT_FAMILY, 400, FontStyle.NORMAL, true);
		builder.useFont(classpathFont("/fonts/DejaVuSans-Bold.ttf"), FONT_FAMILY, 700, FontStyle.NORMAL, true);
		builder.useFont(classpathFont("/fonts/DejaVuSans-Oblique.ttf"), FONT_FAMILY, 400, FontStyle.ITALIC, true);
	}

	/** Supplies a fresh stream per call: the renderer may open a font more than once per document. */
	private FSSupplier<InputStream> classpathFont(String resource) {
		return () -> PdfRenderingService.class.getResourceAsStream(resource);
	}
}
