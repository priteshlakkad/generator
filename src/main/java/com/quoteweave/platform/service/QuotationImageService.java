package com.quoteweave.platform.service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import com.quoteweave.platform.config.PdfRenderProperties;

/** Opt-in, shared HTML/PDF image resolution: catalogue-code files, content URL, then brand. */
@Service
public class QuotationImageService {
	private static final String ASSETS = "/quotation-assets/jaquar/";
	private static final String[] EXTENSIONS = {"png", "jpg", "jpeg"};
	private final PdfRenderProperties properties;
	private final RemoteResourceLoader remote;

	public QuotationImageService(PdfRenderProperties properties, RemoteResourceLoader remote) {
		this.properties = properties;
		this.remote = remote;
	}

	public void resolve(Document document) {
		// Per-merge cache allows files added/replaced in image-store to take effect on the next preview.
		Map<String, String> cache = new HashMap<>();
		for (Element image : document.select("img[data-image-fallback=jaquar]")) {
			String code = image.attr("data-product-code").trim().toUpperCase(Locale.ROOT);
			boolean header = "brand".equals(image.attr("data-image-role"));
			String local = header ? "jaquar-logo" : code;
			String selected = cachedLocal(local, cache);
			if (selected == null) selected = fromUrl(image.attr("src"), cache);
			if (selected == null) selected = fromUrl(image.attr("data-content-url"), cache);
			if (selected == null) {
				selected = BundledQuotationImages.dataUri(ASSETS + "jaquar-logo.png");
				if (!header) {
					image.attr("alt", "Jaquar — product image unavailable" + (code.isBlank() ? "" : " for " + code));
					image.attr("title", "Jaquar brand image; product image unavailable");
				}
			}
			image.attr("src", selected);
			image.removeAttr("data-product-code").removeAttr("data-content-url")
				.removeAttr("data-image-role").removeAttr("data-image-fallback");
		}
	}

	private String cachedLocal(String name, Map<String, String> cache) {
		String key = "local:" + name;
		if (!cache.containsKey(key)) cache.put(key, localImage(name));
		return cache.get(key);
	}

	private String localImage(String name) {
		if (!name.matches("[A-Za-z0-9_-]{1,120}")) return null;
		String directory = properties.getImagesDir();
		if (directory != null && !directory.isBlank()) {
			for (String extension : EXTENSIONS) {
				try {
					Path file = Path.of(directory).resolve(name + "." + extension);
					if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
						try (InputStream input = Files.newInputStream(file)) {
							String image = asDataUri(input.readNBytes(byteLimit() + 1));
							if (image != null) return image;
						}
					}
				} catch (Exception ignored) { /* Continue to the next source. */ }
			}
		}
		for (String extension : EXTENSIONS) {
			try { return BundledQuotationImages.dataUri(ASSETS + name + "." + extension); }
			catch (IllegalArgumentException ignored) { /* No bundled image for this code. */ }
		}
		return null;
	}

	private String fromUrl(String value, Map<String, String> cache) {
		String url = value.trim();
		if (url.isEmpty()) return null;
		if (cache.containsKey(url)) return cache.get(url);
		String selected = null;
		try {
			if (url.startsWith("/quotation-assets/")) selected = BundledQuotationImages.dataUri(url);
			else {
				URI uri = URI.create(url);
				if (uri.getScheme() == null && properties.getBaseUri() != null && !properties.getBaseUri().isBlank()) {
					uri = URI.create(properties.getBaseUri()).resolve(uri);
				}
				String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
				if (scheme.equals("https") || scheme.equals("http") || scheme.equals("data")) {
					if (!scheme.equals("data") || url.length() <= byteLimit() * 2L) selected = asDataUri(remote.imageBytes(uri.toString()));
				}
			}
		} catch (Exception ignored) { /* Invalid URL or image: use the next source. */ }
		cache.put(url, selected);
		return selected;
	}

	private int byteLimit() {
		return (int) Math.min(5 * 1024 * 1024L, Math.max(1, properties.getMaxResourceBytes()));
	}

	private String asDataUri(byte[] bytes) {
		if (bytes == null || bytes.length == 0 || bytes.length > byteLimit()) return null;
		try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
			var readers = ImageIO.getImageReaders(input);
			if (!readers.hasNext()) return null;
			ImageReader reader = readers.next();
			try {
				reader.setInput(input);
				String format = reader.getFormatName().toLowerCase(Locale.ROOT);
				if (!format.equals("png") && !format.equals("jpeg") && !format.equals("jpg") && !format.equals("gif")) return null;
				if ((long) reader.getWidth(0) * reader.getHeight(0) > 20_000_000) return null;
				if (reader.read(0) == null) return null;
				return "data:image/" + (format.equals("jpg") ? "jpeg" : format) + ";base64," + Base64.getEncoder().encodeToString(bytes);
			} finally { reader.dispose(); }
		} catch (Exception ignored) { return null; }
	}
}
