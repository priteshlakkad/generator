package com.pdf.generator.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/** Embeds only bundled quotation images, making HTML and PDF independent of host/port/network. */
final class BundledQuotationImages {
	private static final String PREFIX = "/quotation-assets/";
	private static final Pattern IMAGE_PATH = Pattern.compile("/quotation-assets/(?:[A-Za-z0-9_-]+/)*[A-Za-z0-9_-]+\\.(?:png|jpg|jpeg)");
	private static final int MAX_BYTES = 5 * 1024 * 1024;
	private static final Map<String, String> DATA_URIS = new ConcurrentHashMap<>();

	private BundledQuotationImages() {}

	static void inline(Document document) {
		for (Element image : document.select("img[src]")) {
			String path = image.attr("src");
			if (path.startsWith(PREFIX)) image.attr("src", DATA_URIS.computeIfAbsent(path, BundledQuotationImages::load));
		}
	}

	static String dataUri(String path) {
		return DATA_URIS.computeIfAbsent(path, BundledQuotationImages::load);
	}

	private static String load(String path) {
		if (!IMAGE_PATH.matcher(path).matches()) throw new IllegalArgumentException("Invalid bundled quotation image path: " + path);
		try (InputStream stream = BundledQuotationImages.class.getResourceAsStream("/static" + path)) {
			if (stream == null) throw new IllegalArgumentException("Bundled quotation image not found: " + path);
			byte[] bytes = stream.readNBytes(MAX_BYTES + 1);
			if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("Bundled quotation image exceeds 5 MB: " + path);
			String mime = path.endsWith(".png") ? "image/png" : "image/jpeg";
			return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
		} catch (IOException e) {
			throw new IllegalArgumentException("Could not read bundled quotation image: " + path, e);
		}
	}
}
