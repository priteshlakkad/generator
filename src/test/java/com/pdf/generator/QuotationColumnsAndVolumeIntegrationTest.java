package com.pdf.generator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Exercises the real shipped quotation template end to end: column visibility from both the saved
 * config and the request payload, and a line-item count in the range the service is expected to
 * handle.
 */
@SpringBootTest
class QuotationColumnsAndVolumeIntegrationTest {

	private static final String TEMPLATE = "jaquar-quotation";
	private static final Path SOURCE_DIR = Path.of("template-store", TEMPLATE);

	@TempDir
	static Path templateStore;

	@DynamicPropertySource
	static void overrideTemplateDir(DynamicPropertyRegistry registry) {
		registry.add("pdf.templates.dir", () -> templateStore.toString());
		registry.add("pdf.output.dir", () -> templateStore.resolve("generated").toString());
	}

	@Autowired
	private WebApplicationContext webApplicationContext;

	private MockMvc mockMvc;

	/** Copies the shipped template into the temp store so tests never mutate the repo copy. */
	@BeforeEach
	void installTemplate() throws Exception {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

		Path target = templateStore.resolve(TEMPLATE);
		Files.createDirectories(target);
		for (String suffix : new String[] { ".html", ".sample.json" }) {
			Files.copy(SOURCE_DIR.resolve(TEMPLATE + suffix), target.resolve(TEMPLATE + suffix),
				java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		}
		Files.writeString(target.resolve(TEMPLATE + ".columns.json"), "{\"groups\":[]}");
	}

	private String previewWith(String payload) throws Exception {
		return mockMvc.perform(post("/api/pdf/preview/" + TEMPLATE)
				.contentType(MediaType.APPLICATION_JSON)
				.content(payload))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
	}

	private byte[] generateWith(String payload) throws Exception {
		return mockMvc.perform(post("/api/pdf/generate/" + TEMPLATE)
				.contentType(MediaType.APPLICATION_JSON)
				.content(payload))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsByteArray();
	}

	private String sampleData() throws Exception {
		return Files.readString(SOURCE_DIR.resolve(TEMPLATE + ".sample.json"), StandardCharsets.UTF_8);
	}

	// ---------------------------------------------------------------- columns

	@Test
	void columnsEndpointListsEveryColumnTheTemplateDeclares() throws Exception {
		String json = mockMvc.perform(get("/api/templates/" + TEMPLATE + "/columns"))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();

		assertThat(json).contains("\"items\"").contains("slNo").contains("totalAmt");
		assertThat(countOccurrences(json, "\"field\"")).isEqualTo(13);
	}

	@Test
	void payloadCanHideColumnsForASingleQuotation() throws Exception {
		String withAll = previewWith(sampleData());
		assertThat(withAll).contains(">MRP<").contains("Rate/Unit");

		String payload = withExtraKeys("\"hiddenColumns\": [\"mrp\", \"ratePerUnit\"]");
		String hidden = previewWith(payload);

		assertThat(hidden).doesNotContain(">MRP<").doesNotContain("Rate/Unit");
		// The rest of the table survives and the note row still spans the full width.
		assertThat(hidden).contains("Cat. No").contains("Total Amt.");
		assertThat(hidden).contains("colspan=\"11\"");
	}

	@Test
	void savedColumnConfigAppliesToEveryGenerationAndPayloadStillWins() throws Exception {
		String config = """
			{"groups":[{"id":"items","columns":[{"field":"gstPercent","visible":false}]}]}
			""";
		mockMvc.perform(put("/api/templates/" + TEMPLATE + "/columns")
				.contentType(MediaType.APPLICATION_JSON)
				.content(config))
			.andExpect(status().isOk());

		assertThat(previewWith(sampleData())).doesNotContain("GST %");

		// A request may put a saved-off column back for one quotation.
		String payload = withExtraKeys("\"columnVisibility\": { \"gstPercent\": true }");
		assertThat(previewWith(payload)).contains("GST %");
	}

	@Test
	void malformedColumnConfigIsRejectedAndLeavesTheStoredConfigIntact() throws Exception {
		mockMvc.perform(put("/api/templates/" + TEMPLATE + "/columns")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{ not json"))
			.andExpect(status().isBadRequest());

		// The good config that was installed is still in force.
		assertThat(previewWith(sampleData())).contains(">MRP<");
	}

	@Test
	void authoringAttributesAreStrippedFromRenderedOutput() throws Exception {
		// Asserted against the parsed DOM rather than the raw text, because the template's own
		// HTML comments legitimately mention these attribute names.
		org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(previewWith(sampleData()));

		assertThat(doc.select("[data-col]")).isEmpty();
		assertThat(doc.select("[data-columns]")).isEmpty();
		assertThat(doc.select("[data-colspan]")).isEmpty();
		assertThat(doc.select("[data-align]")).isEmpty();
		assertThat(doc.select("[data-repeat]")).isEmpty();
	}

	@Test
	void columnDraftIsReadOnlyAndMatchesSavedHtmlAndPdf() throws Exception {
		String path = "/api/templates/" + TEMPLATE + "/columns";
		String beforeHtml = Files.readString(templateStore.resolve(TEMPLATE).resolve(TEMPLATE + ".html"));
		String beforeConfig = Files.readString(templateStore.resolve(TEMPLATE).resolve(TEMPLATE + ".columns.json"));
		String config = """
			{"groups":[{"id":"items","ordered":true,"columns":[
			{"field":"hsnCode","label":"HSN Code","custom":true,"type":"text","sizing":"auto"},
			{"field":"slNo","sizing":"fixed","width":4},
			{"field":"mrp","visible":false}]}]}
			""";
		String result = mockMvc.perform(post(path + "/preview").contentType(MediaType.APPLICATION_JSON)
			.content("{\"config\":" + config + ",\"data\":" + sampleData() + "}"))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
		String draftHtml = mapper.readTree(result).get("html").asText();
		assertThat(org.jsoup.Jsoup.parse(draftHtml).select("table.items th").first().text()).isEqualTo("HSN Code");
		assertThat(result).contains("resolvedWidth", "canAdd");
		assertThat(Files.readString(templateStore.resolve(TEMPLATE).resolve(TEMPLATE + ".html"))).isEqualTo(beforeHtml);
		assertThat(Files.readString(templateStore.resolve(TEMPLATE).resolve(TEMPLATE + ".columns.json"))).isEqualTo(beforeConfig);

		mockMvc.perform(put(path).contentType(MediaType.APPLICATION_JSON).content(config)).andExpect(status().isOk());
		assertThat(previewWith(sampleData())).isEqualTo(draftHtml);
		byte[] pdf = generateWith(sampleData());
		try (var document = org.apache.pdfbox.Loader.loadPDF(pdf)) {
			assertThat(new org.apache.pdfbox.text.PDFTextStripper().getText(document)).contains("HSN Code");
		}
	}

	@Test
	void impossibleColumnLayoutIsRejectedWithoutSaving() throws Exception {
		Path configPath = templateStore.resolve(TEMPLATE).resolve(TEMPLATE + ".columns.json");
		String before = Files.readString(configPath);
		String invalid = """
			{"groups":[{"id":"items","columns":[{"field":"slNo","sizing":"fixed","width":100},{"field":"mrp","sizing":"auto"}]}]}
			""";
		mockMvc.perform(put("/api/templates/" + TEMPLATE + "/columns").contentType(MediaType.APPLICATION_JSON).content(invalid))
			.andExpect(status().isBadRequest());
		assertThat(Files.readString(configPath)).isEqualTo(before);
	}

	// ---------------------------------------------------------------- volume & resilience

	@Test
	void generatesAFiveHundredLineQuotation() throws Exception {
		long startedAt = System.currentTimeMillis();

		byte[] pdf = generateWith(payloadWithRows(500));

		long elapsed = System.currentTimeMillis() - startedAt;
		assertThat(new String(pdf, 0, 4, StandardCharsets.UTF_8)).isEqualTo("%PDF");
		// Generous ceiling: this is a smoke test against pathological regressions, not a benchmark.
		assertThat(elapsed).isLessThan(60_000);
		// 500 rows cannot fit on a couple of pages; a small PDF would mean rows were dropped.
		assertThat(pdf.length).isGreaterThan(100_000);
	}

	@Test
	void unreachableImageUrlsStillProduceAValidPdf() throws Exception {
		String payload = sampleData().replace("/quotation-assets/jaquar/jaquar-logo.png",
			"http://no-such-host.invalid/logo.png");

		byte[] pdf = generateWith(payload);

		assertThat(new String(pdf, 0, 4, StandardCharsets.UTF_8)).isEqualTo("%PDF");
	}

	@Test
	void productLinksBecomeRealPdfAnnotations() throws Exception {
		byte[] pdf = generateWith(sampleData());

		// openhtmltopdf compresses object streams, so search the decompressed content.
		assertThat(decompressedText(pdf)).contains("/URI").contains("jaquar.com");
	}

	// ---------------------------------------------------------------- helpers

	/** Inserts extra top-level keys into the sample payload. */
	private String withExtraKeys(String keys) throws Exception {
		String json = sampleData().trim();
		return "{" + keys + "," + json.substring(1);
	}

	private String payloadWithRows(int rows) throws Exception {
		String items = IntStream.rangeClosed(1, rows)
			.mapToObj(i -> """
				{"slNo":%d,"catNo":"CAT-%04d","imageUrl":"","productUrl":"https://example.invalid/p/%d",
				 "qty":1,"mrp":"INR 3,16,000.00","unitPrice":"INR 2,69,000.00","discPercent":"15.00",
				 "discAmt":"INR 40,350.00","ratePerUnit":"INR 1,93,771.19","taxableAmt":"INR 1,93,771.19",
				 "gstPercent":"18.00","gstAmt":"INR 34,878.81","totalAmt":"INR 2,28,650.00",
				 "hsnCode":"39229000","description":"Line item %d"}""".formatted(i, i, i, i))
			.collect(Collectors.joining(","));

		return """
			{"quoteNumber":"Q-BIG","quoteDate":"07/08/2026","accountName":"Load Test",
			 "sections":[{"sectionName":"Bulk","sectionTotal":"INR 1,00,00,000.00","items":[%s]}],
			 "netAmt":"INR 1,00,00,000.00","terms":[]}""".formatted(items);
	}

	private static int countOccurrences(String haystack, String needle) {
		return haystack.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
	}

	/** Concatenates the raw bytes with every inflatable stream, so annotations can be asserted on. */
	private static String decompressedText(byte[] pdf) {
		StringBuilder text = new StringBuilder(new String(pdf, StandardCharsets.ISO_8859_1));
		java.util.regex.Matcher matcher = java.util.regex.Pattern
			.compile("stream\\r?\\n", java.util.regex.Pattern.DOTALL)
			.matcher(new String(pdf, StandardCharsets.ISO_8859_1));
		while (matcher.find()) {
			try {
				java.util.zip.Inflater inflater = new java.util.zip.Inflater();
				inflater.setInput(pdf, matcher.end(), pdf.length - matcher.end());
				byte[] buffer = new byte[65536];
				int n = inflater.inflate(buffer);
				text.append(new String(buffer, 0, n, StandardCharsets.ISO_8859_1));
				inflater.end();
			} catch (java.util.zip.DataFormatException ignored) {
				// Not a deflate stream (or not the start of one) — nothing to add.
			}
		}
		return text.toString();
	}
}
