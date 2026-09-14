package com.pdf.generator.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.openhtmltopdf.extend.FSStream;
import com.openhtmltopdf.extend.FSStreamFactory;
import com.pdf.generator.config.PdfRenderProperties;

/**
 * Fetches the images (and other remote resources) a template references, with the guarantees a
 * batch document generator needs:
 *
 * <ul>
 *   <li><b>Cached</b> — a quotation with hundreds of line items usually repeats the same product
 *       images; each URL is fetched at most once and then served from memory.</li>
 *   <li><b>Bounded</b> — connect/read timeouts and a maximum size, so one slow or enormous portal
 *       URL cannot stall or exhaust a render.</li>
 *   <li><b>Non-fatal</b> — a broken URL yields a transparent placeholder and a warning rather than
 *       failing the whole PDF, which matters when the URLs come from request data.</li>
 * </ul>
 *
 * Failures are cached too, so a broken URL repeated across 500 rows costs one attempt, not 500.
 */
@Component
public class RemoteResourceLoader implements FSStreamFactory {

	private static final Logger log = LoggerFactory.getLogger(RemoteResourceLoader.class);

	/** 1x1 fully transparent PNG, stood in for anything that could not be fetched. */
	private static final byte[] TRANSPARENT_PIXEL = Base64.getDecoder().decode(
		"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");

	private final PdfRenderProperties properties;
	private final Map<String, byte[]> cache;

	public RemoteResourceLoader(PdfRenderProperties properties) {
		this.properties = properties;
		int capacity = Math.max(properties.getCacheSize(), 0);
		this.cache = new LinkedHashMap<>(16, 0.75f, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
				return size() > capacity;
			}
		};
	}

	@Override
	public FSStream getUrl(String uri) {
		return new ByteArrayFSStream(load(uri));
	}

	/** Raw bytes for the quotation image fallback pipeline; a failed fetch is distinguishable. */
	byte[] imageBytes(String uri) {
		byte[] bytes = load(uri);
		return java.util.Arrays.equals(bytes, TRANSPARENT_PIXEL) ? null : bytes;
	}

	private byte[] load(String uri) {
		if (uri == null || uri.isBlank()) {
			return TRANSPARENT_PIXEL;
		}
		if (uri.regionMatches(true, 0, "data:", 0, 5)) {
			// Not cached: decoding is cheap and the payload is already in memory.
			return decodeDataUri(uri);
		}

		synchronized (cache) {
			byte[] cached = cache.get(uri);
			if (cached != null) {
				return cached;
			}
		}

		byte[] fetched = fetch(uri);
		if (properties.getCacheSize() > 0) {
			synchronized (cache) {
				cache.put(uri, fetched);
			}
		}
		return fetched;
	}

	private byte[] fetch(String uri) {
		if (!isHostAllowed(uri)) {
			log.warn("Blocked resource from a host that is not in pdf.render.allowed-hosts: {}", uri);
			return TRANSPARENT_PIXEL;
		}
		try {
			URLConnection connection = URI.create(uri).toURL().openConnection();
			connection.setConnectTimeout(properties.getConnectTimeoutMs());
			connection.setReadTimeout(properties.getReadTimeoutMs());
			connection.setRequestProperty("User-Agent", "pdf-generator");

			try (InputStream in = connection.getInputStream()) {
				byte[] body = readAtMost(in, properties.getMaxResourceBytes());
				if (body == null) {
					log.warn("Resource exceeds pdf.render.max-resource-bytes ({} bytes), skipped: {}",
						properties.getMaxResourceBytes(), uri);
					return TRANSPARENT_PIXEL;
				}
				return body;
			} finally {
				if (connection instanceof HttpURLConnection http) {
					http.disconnect();
				}
			}
		} catch (IOException | RuntimeException e) {
			log.warn("Could not load resource {} ({}); using a placeholder", uri, e.toString());
			return TRANSPARENT_PIXEL;
		}
	}

	/** @return the bytes read, or null once the limit is exceeded. */
	private byte[] readAtMost(InputStream in, long limit) throws IOException {
		java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
		byte[] chunk = new byte[8192];
		int read;
		while ((read = in.read(chunk)) != -1) {
			if (buffer.size() + read > limit) {
				return null;
			}
			buffer.write(chunk, 0, read);
		}
		return buffer.toByteArray();
	}

	private boolean isHostAllowed(String uri) {
		if (properties.getAllowedHosts().isEmpty()) {
			return true;
		}
		try {
			String host = URI.create(uri).getHost();
			if (host == null) {
				return false;
			}
			String normalized = host.toLowerCase(Locale.ROOT);
			return properties.getAllowedHosts().stream()
				.map(allowed -> allowed.trim().toLowerCase(Locale.ROOT))
				.anyMatch(allowed -> normalized.equals(allowed) || normalized.endsWith("." + allowed));
		} catch (RuntimeException e) {
			return false;
		}
	}

	private byte[] decodeDataUri(String uri) {
		try {
			int comma = uri.indexOf(',');
			if (comma < 0) {
				return TRANSPARENT_PIXEL;
			}
			String meta = uri.substring(5, comma);
			String payload = uri.substring(comma + 1);
			return meta.toLowerCase(Locale.ROOT).contains(";base64")
				? Base64.getMimeDecoder().decode(payload)
				: URLDecoder.decode(payload, StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8);
		} catch (RuntimeException e) {
			log.warn("Could not decode data URI ({}); using a placeholder", e.toString());
			return TRANSPARENT_PIXEL;
		}
	}

	private record ByteArrayFSStream(byte[] bytes) implements FSStream {

		@Override
		public InputStream getStream() {
			return new ByteArrayInputStream(bytes);
		}

		@Override
		public Reader getReader() {
			return new InputStreamReader(getStream(), StandardCharsets.UTF_8);
		}
	}
}
