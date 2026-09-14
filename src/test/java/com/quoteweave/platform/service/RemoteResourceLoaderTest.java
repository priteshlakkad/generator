package com.quoteweave.platform.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.quoteweave.platform.config.PdfRenderProperties;
import com.sun.net.httpserver.HttpServer;

class RemoteResourceLoaderTest {

	private static final byte[] BODY = "PRODUCT-IMAGE-BYTES".getBytes(StandardCharsets.UTF_8);

	private HttpServer server;
	private AtomicInteger requestCount;
	private String baseUrl;

	@BeforeEach
	void startServer() throws IOException {
		requestCount = new AtomicInteger();
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/image.png", exchange -> {
			requestCount.incrementAndGet();
			exchange.sendResponseHeaders(200, BODY.length);
			exchange.getResponseBody().write(BODY);
			exchange.close();
		});
		server.createContext("/missing.png", exchange -> {
			requestCount.incrementAndGet();
			exchange.sendResponseHeaders(404, -1);
			exchange.close();
		});
		server.start();
		baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
	}

	@AfterEach
	void stopServer() {
		server.stop(0);
	}

	private RemoteResourceLoader loader(PdfRenderProperties properties) {
		return new RemoteResourceLoader(properties);
	}

	private PdfRenderProperties properties() {
		return new PdfRenderProperties();
	}

	private byte[] read(RemoteResourceLoader loader, String uri) throws IOException {
		try (InputStream in = loader.getUrl(uri).getStream()) {
			return in.readAllBytes();
		}
	}

	@Test
	void fetchesARemoteResource() throws Exception {
		assertThat(read(loader(properties()), baseUrl + "/image.png")).isEqualTo(BODY);
	}

	@Test
	void repeatedUrlsAreFetchedOnlyOnce() throws Exception {
		RemoteResourceLoader loader = loader(properties());

		// The whole point for a 500-row quotation: the same product image on every row.
		for (int i = 0; i < 50; i++) {
			assertThat(read(loader, baseUrl + "/image.png")).isEqualTo(BODY);
		}

		assertThat(requestCount.get()).isEqualTo(1);
	}

	@Test
	void failuresAreCachedSoABrokenUrlIsNotRetriedPerRow() throws Exception {
		RemoteResourceLoader loader = loader(properties());

		for (int i = 0; i < 20; i++) {
			read(loader, baseUrl + "/missing.png");
		}

		assertThat(requestCount.get()).isEqualTo(1);
	}

	@Test
	void aBrokenUrlYieldsAPlaceholderRatherThanThrowing() throws Exception {
		byte[] result = read(loader(properties()), baseUrl + "/missing.png");

		// A PNG signature: the renderer gets a valid image, so the document still renders.
		assertThat(result).isNotEmpty();
		assertThat(java.util.Arrays.copyOf(result, 4)).isEqualTo(new byte[] { (byte) 0x89, 'P', 'N', 'G' });
	}

	@Test
	void unreachableHostYieldsAPlaceholder() throws Exception {
		assertThat(read(loader(properties()), "http://no-such-host.invalid/x.png")).isNotEmpty();
	}

	@Test
	void malformedUriYieldsAPlaceholderInsteadOfFailingTheRender() throws Exception {
		assertThat(read(loader(properties()), "http://[not a uri]/x.png")).isNotEmpty();
		assertThat(read(loader(properties()), "")).isNotEmpty();
	}

	@Test
	void cachingCanBeDisabled() throws Exception {
		PdfRenderProperties properties = properties();
		properties.setCacheSize(0);
		RemoteResourceLoader loader = loader(properties);

		read(loader, baseUrl + "/image.png");
		read(loader, baseUrl + "/image.png");

		assertThat(requestCount.get()).isEqualTo(2);
	}

	@Test
	void resourcesOverTheSizeCapAreSkipped() throws Exception {
		PdfRenderProperties properties = properties();
		properties.setMaxResourceBytes(5);

		byte[] result = read(loader(properties), baseUrl + "/image.png");

		assertThat(result).isNotEqualTo(BODY);
		assertThat(java.util.Arrays.copyOf(result, 4)).isEqualTo(new byte[] { (byte) 0x89, 'P', 'N', 'G' });
	}

	@Test
	void hostsOutsideTheAllowListAreNeverFetched() throws Exception {
		PdfRenderProperties properties = properties();
		properties.setAllowedHosts(List.of("portal.example.com"));

		byte[] result = read(loader(properties), baseUrl + "/image.png");

		assertThat(result).isNotEqualTo(BODY);
		assertThat(requestCount.get()).isZero();
	}

	@Test
	void allowListMatchesSubdomains() throws Exception {
		PdfRenderProperties properties = properties();
		properties.setAllowedHosts(List.of("127.0.0.1"));

		assertThat(read(loader(properties), baseUrl + "/image.png")).isEqualTo(BODY);
	}

	@Test
	void emptyAllowListPermitsEveryHost() throws Exception {
		PdfRenderProperties properties = properties();
		properties.setAllowedHosts(List.of());

		assertThat(read(loader(properties), baseUrl + "/image.png")).isEqualTo(BODY);
	}

	@Test
	void base64DataUrisAreDecodedWithoutNetworkAccess() throws Exception {
		String encoded = java.util.Base64.getEncoder().encodeToString(BODY);

		byte[] result = read(loader(properties()), "data:image/png;base64," + encoded);

		assertThat(result).isEqualTo(BODY);
		assertThat(requestCount.get()).isZero();
	}

	@Test
	void plainDataUrisAreDecoded() throws Exception {
		assertThat(read(loader(properties()), "data:text/plain,hello%20world"))
			.isEqualTo("hello world".getBytes(StandardCharsets.UTF_8));
	}

	@Test
	void malformedDataUriYieldsAPlaceholder() throws Exception {
		assertThat(read(loader(properties()), "data:image/png;base64,!!!not-base64!!!")).isNotEmpty();
	}
}
