package com.pdf.generator.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import com.pdf.generator.config.PdfRenderProperties;

import tools.jackson.databind.json.JsonMapper;

class JaquarProductDetailedTemplateTest {
    private static final Path DIRECTORY = Path.of("template-store/jaquar-quotation-product-detailed");

    @Test
    @SuppressWarnings("unchecked")
    void mergesOneHundredDetailedRowsFromTheTemplateAndClasspathSamplesAndRendersPdf() throws Exception {
        String template = Files.readString(DIRECTORY.resolve("jaquar-quotation-product-detailed.html"));
        String sampleJson = Files.readString(DIRECTORY.resolve("jaquar-quotation-product-detailed.sample.json"));
        String resourceJson = Files.readString(Path.of(
            "src/main/resources/samples/jaquar-quotation-product-detailed.sample.json"));
        String columnsJson = Files.readString(DIRECTORY.resolve("jaquar-quotation-product-detailed.columns.json"));
        Map<String, Object> sample = JsonMapper.builder().build().readValue(sampleJson, Map.class);
        List<Map<String, Object>> sections = (List<Map<String, Object>>) sample.get("sections");

        assertThat(resourceJson).isEqualTo(sampleJson);
        assertThat(sections).hasSize(5);
        assertThat(sections.stream().mapToInt(section ->
            ((List<Map<String, Object>>) section.get("items")).size()).sum()).isEqualTo(100);

        PdfRenderProperties properties = new PdfRenderProperties();
        HtmlMergeService merger = new HtmlMergeService(new ColumnConfigService(),
            new QuotationImageService(properties, new RemoteResourceLoader(properties)));
        String merged = merger.merge(template, sample, columnsJson);
        var document = Jsoup.parse(merged);

        assertThat(merged).contains("size: A4 landscape", "#09565F").doesNotContain("{{");
        assertThat(document.text())
            .doesNotContain("CUSTOMER & DELIVERY DETAILS", "AUTHORISED DEALER")
            .contains("DETAILED PRODUCT QUOTATION", "Category", "Unit Price", "GST Amt.", "Delivery");
        assertThat(document.select("table.items thead th")).hasSize(5 * 18);
        assertThat(document.select("table.items tbody")).hasSize(100);
        assertThat(document.select("td.image-cell img")).hasSize(100)
            .allSatisfy(image -> assertThat(image.attr("src")).startsWith("data:image/"));

        byte[] pdf = new PdfRenderingService(properties, new RemoteResourceLoader(properties)).render(merged);
        assertThat(pdf).hasSizeGreaterThan(100_000);
        assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }
}
