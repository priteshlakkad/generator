package com.quoteweave.platform.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import com.quoteweave.platform.config.PdfRenderProperties;

import tools.jackson.databind.json.JsonMapper;

class JaquarEnhancedTemplateTest {
    private static final Path DIRECTORY = Path.of("template-store/jaquar-quotation-enhanced");

    @Test
    void mergesJaquarProjectFieldsAndStableSampleImagesAndRendersPdf() throws Exception {
        String template = Files.readString(DIRECTORY.resolve("jaquar-quotation-enhanced.html"), StandardCharsets.UTF_8);
        String sampleJson = Files.readString(DIRECTORY.resolve("jaquar-quotation-enhanced.sample.json"), StandardCharsets.UTF_8);
        String columnsJson = Files.readString(DIRECTORY.resolve("jaquar-quotation-enhanced.columns.json"), StandardCharsets.UTF_8);
        Map<String, Object> sample = JsonMapper.builder().build().readValue(sampleJson, Map.class);

        PdfRenderProperties properties = new PdfRenderProperties();
        HtmlMergeService merger = new HtmlMergeService(new ColumnConfigService(),
            new QuotationImageService(properties, new RemoteResourceLoader(properties)));
        String merged = merger.merge(template, sample, columnsJson);
        var document = Jsoup.parse(merged);

        assertThat(merged).contains("#09565F").doesNotContain("{{");
        assertThat(document.selectFirst("#pageHeader").text())
            .contains("JQ-PRJ-560122", "Rev. 02", "Valid Until 22/08/2026");
        assertThat(document.text()).contains(
            "Aurum Heights Premium Residences",
            "27AAECA1234F1Z5",
            "Bath Concepts India Pvt. Ltd.",
            "COMMERCIAL & FULFILMENT DETAILS",
            "Customer Acceptance");
        assertThat(document.select("table.items tbody")).hasSize(8);
        assertThat(document.select("td.image-cell img")).hasSize(8).allSatisfy(image -> {
            assertThat(image.attr("src")).startsWith("data:image/");
            assertThat(Base64.getDecoder().decode(image.attr("src").substring(image.attr("src").indexOf(',') + 1)))
                .isNotEmpty();
        });

        PdfRenderingService renderer = new PdfRenderingService(properties, new RemoteResourceLoader(properties));
        byte[] pdf = renderer.render(merged);
        assertThat(pdf).hasSizeGreaterThan(20_000);
        assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }
}
