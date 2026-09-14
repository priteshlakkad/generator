package com.pdf.generator.service;

import static org.assertj.core.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.pdf.generator.config.PdfRenderProperties;
import com.sun.net.httpserver.HttpServer;

class QuotationImageServiceTest {
	@TempDir Path local;
	private HttpServer server;
	private String url;
	private HtmlMergeService merge;
	private byte[] remoteImage;
	private final AtomicInteger requests = new AtomicInteger();
	private static final String IMAGE = "<img src=\"{{imageUrl}}\" data-image-fallback=\"jaquar\" data-product-code=\"{{catNo}}\" data-content-url=\"{{contentUrl}}\">";

	@BeforeEach void setup() throws Exception {
		remoteImage = png(0x336699);
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			requests.incrementAndGet();
			boolean valid = exchange.getRequestURI().getPath().equals("/image.png");
			byte[] body = valid ? remoteImage : "<html>Not image content</html>".getBytes();
			exchange.sendResponseHeaders(200, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		server.start();
		url = "http://127.0.0.1:" + server.getAddress().getPort();
		PdfRenderProperties properties = new PdfRenderProperties();
		properties.setImagesDir(local.toString());
		merge = new HtmlMergeService(new ColumnConfigService(), new QuotationImageService(properties, new RemoteResourceLoader(properties)));
	}

	@AfterEach void stop() { if (server != null) server.stop(0); }

	@Test void localCatalogueCodeImageWinsAndReplacementIsVisibleWithoutRestart() throws Exception {
		byte[] first = png(0x112233), second = png(0x445566);
		Files.write(local.resolve("TEST-SKU.png"), first);
		Map<String, Object> data = Map.of("catNo", "test-sku", "imageUrl", url + "/image.png");
		assertThat(bytes(merge.merge(IMAGE, data))).isEqualTo(first);
		Files.write(local.resolve("TEST-SKU.png"), second);
		assertThat(bytes(merge.merge(IMAGE, data))).isEqualTo(second);
		assertThat(requests.get()).isZero();
	}

	@Test void invalidLocalFileFallsThroughToRemoteImageAndRemoteFetchIsCached() throws Exception {
		Files.writeString(local.resolve("TEST-SKU.png"), "corrupt image");
		Map<String, Object> data = Map.of("catNo", "TEST-SKU", "imageUrl", url + "/image.png");
		assertThat(bytes(merge.merge(IMAGE + IMAGE, data))).isEqualTo(remoteImage);
		assertThat(bytes(merge.merge(IMAGE, data))).isEqualTo(remoteImage);
		assertThat(requests.get()).isEqualTo(1);
	}

	@Test void contentUrlIsTriedAfterInvalidImageUrl() {
		String result = merge.merge(IMAGE, Map.of("catNo", "UNKNOWN", "imageUrl", url + "/html", "contentUrl", url + "/image.png"));
		assertThat(bytes(result)).isEqualTo(remoteImage);
		assertThat(requests.get()).isEqualTo(2);
	}

	@Test void invalidRemoteContentAndMissingImagesUseJaquarBrand() throws Exception {
		String result = merge.merge(IMAGE, Map.of("catNo", "UNKNOWN", "imageUrl", url + "/html"));
		byte[] logo = Files.readAllBytes(Path.of("src/main/resources/static/quotation-assets/jaquar/jaquar-logo.png"));
		assertThat(bytes(result)).isEqualTo(logo);
		assertThat(Jsoup.parse(result).selectFirst("img").attr("title")).contains("product image unavailable");
		assertThat(bytes(merge.merge(IMAGE, Map.of("catNo", "../private", "imageUrl", "file:///private/secret.png")))).isEqualTo(logo);
		assertThat(bytes(merge.merge(IMAGE, Map.of()))).isEqualTo(logo);
	}

	@Test void bundledExactCodeImageWinsOverContentUrlAndHeaderDefaultsToLogo() throws Exception {
		String result = merge.merge(IMAGE, Map.of("catNo", "ABT-WHT-FSBTCF2011", "imageUrl", url + "/image.png"));
		assertThat(bytes(result)).isEqualTo(Files.readAllBytes(Path.of("src/main/resources/static/quotation-assets/jaquar/ABT-WHT-FSBTCF2011.jpg")));
		String header = merge.merge("<img src=\"{{logoUrl}}\" data-image-fallback=\"jaquar\" data-image-role=\"brand\">", Map.of());
		assertThat(bytes(header)).isEqualTo(Files.readAllBytes(Path.of("src/main/resources/static/quotation-assets/jaquar/jaquar-logo.png")));
		assertThat(requests.get()).isZero();
		assertThat(Jsoup.parse(header).select("[data-image-fallback], [data-product-code], [data-content-url]")).isEmpty();
	}

	private byte[] bytes(String html) {
		String uri = Jsoup.parse(html).selectFirst("img").attr("src");
		assertThat(uri).startsWith("data:image/");
		return Base64.getDecoder().decode(uri.substring(uri.indexOf(',') + 1));
	}

	private byte[] png(int colour) throws Exception {
		BufferedImage image = new BufferedImage(4, 3, BufferedImage.TYPE_INT_RGB);
		image.setRGB(0, 0, colour);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, "png", out);
		return out.toByteArray();
	}
}
