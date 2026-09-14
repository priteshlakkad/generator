package com.quoteweave.platform.service;

import static org.assertj.core.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;

import javax.imageio.ImageIO;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class BundledQuotationImagesTest {
	private final HtmlMergeService merge = new HtmlMergeService(new ColumnConfigService());

	@Test
	void embedsLogoAndKnownProductsAsDecodableImagesInTheCorrectRows() throws Exception {
		String template = Files.readString(Path.of("template-store/jaquar-quotation/jaquar-quotation.html"));
		Map<String, Object> data = JsonMapper.builder().build().readValue(
			Files.readString(Path.of("samples/jaquar-quotation-request.json")), Map.class);
		var doc = Jsoup.parse(merge.merge(template, data));
		var header = doc.selectFirst("#pageHeader");
		assertThat(header.text()).contains("Q-560122", "07/08/2026");
		assertThat(header.selectFirst("img").attr("src")).startsWith("data:image/png;base64,");
		for (String code : new String[] {"ABT-WHT-FSBTCF2011", "ABT-WHT-FSBTLX1001", "ABT-WHT-FSBTTA3011", "ABT-WHT-TIAARA190X"}) {
			var image = doc.select("td.image-cell img").stream().filter(img -> img.attr("alt").equals(code)).findFirst().orElseThrow();
			String source = image.attr("src");
			assertThat(source).startsWith("data:image/");
			byte[] bytes = Base64.getDecoder().decode(source.substring(source.indexOf(',') + 1));
			var decoded = ImageIO.read(new ByteArrayInputStream(bytes));
			assertThat(decoded).isNotNull();
			assertThat(decoded.getWidth()).isGreaterThan(100);
			assertThat(image.closest("tr").text()).contains(code);
		}
	}

	@Test
	void rejectsPathsOutsideTheBundledImageDirectoryAndMissingAssets() {
		for (String source : new String[] {"/quotation-assets/../application.properties", "/quotation-assets/%2e%2e/private.png", "/quotation-assets/jaquar/missing.png"}) {
			assertThatThrownBy(() -> merge.merge("<img src=\"{{image}}\">", Map.of("image", source)))
				.isInstanceOf(IllegalArgumentException.class);
		}
	}

	@Test
	void leavesExternalImagesAndDataUrisUnchanged() {
		String url = "https://portal.example.com/product.jpg";
		String html = merge.merge("<img src=\"{{image}}\">", Map.of("image", url));
		assertThat(Jsoup.parse(html).selectFirst("img").attr("src")).isEqualTo(url);
	}
}
