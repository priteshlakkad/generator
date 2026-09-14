package com.quoteweave.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class TemplateAndPdfIntegrationTest {

	@TempDir
	static Path templateStore;

	@DynamicPropertySource
	static void overrideTemplateDir(DynamicPropertyRegistry registry) {
		registry.add("quoteweave.templates.dir", () -> templateStore.toString());
		registry.add("quoteweave.output.dir", () -> templateStore.resolve("generated").toString());
	}

	@Autowired
	private WebApplicationContext webApplicationContext;

	private MockMvc mockMvc;

	private MockMvc mockMvc() {
		if (mockMvc == null) {
			mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
		}
		return mockMvc;
	}

	@Test
	void createEditGenerateAndDeleteTemplate() throws Exception {
		mockMvc().perform(post("/api/templates/invoice/blank"))
			.andExpect(status().isCreated());

		String html = """
			<h1>Invoice {{quoteNumber}}</h1>
			<p>Customer: {{customerName}}</p>
			<table>
			<tbody>
			<tr data-repeat="items"><td>{{name}}</td><td>{{qty}}</td><td>{{amount}}</td></tr>
			</tbody>
			</table>
			""";

		mockMvc().perform(put("/api/templates/invoice/html")
				.contentType(MediaType.TEXT_PLAIN)
				.content(html))
			.andExpect(status().isOk());

		MvcResult getResult = mockMvc().perform(get("/api/templates/invoice/html"))
			.andExpect(status().isOk())
			.andReturn();
		assertThat(getResult.getResponse().getContentAsString()).contains("data-repeat");

		String requestJson = """
			{
			  "quoteNumber": "Q-1",
			  "customerName": "Acme Corp",
			  "items": [
			    { "name": "Widget", "qty": 2, "amount": "$20" },
			    { "name": "Gadget", "qty": 1, "amount": "$15" }
			  ]
			}
			""";

		MvcResult pdfResult = mockMvc()
			.perform(post("/api/pdf/generate/invoice")
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestJson))
			.andExpect(status().isOk())
			.andReturn();

		byte[] pdfBytes = pdfResult.getResponse().getContentAsByteArray();
		assertThat(pdfBytes.length).isGreaterThan(0);
		assertThat(new String(pdfBytes, 0, 4)).isEqualTo("%PDF");
		String storedPath = pdfResult.getResponse().getHeader("X-Quotation-Path");
		assertThat(storedPath).matches("\\d{4}/\\d{2}/invoice/Q-1/Q-1-\\d{8}-\\d{6}-\\d{3}\\.pdf");
		assertThat(pdfResult.getResponse().getHeader("Content-Disposition")).contains("Q-1-");
		assertThat(pdfResult.getResponse().getHeader("X-Generation-Id")).isNotBlank();
		assertThat(pdfResult.getResponse().getHeader("X-Generation-Time-Ms")).matches("\\d+\\.\\d{3}");
		assertThat(pdfResult.getResponse().getHeader("X-Processed-Rows")).isEqualTo("2");
		assertThat(pdfResult.getResponse().getHeader("Server-Timing"))
			.contains("template;dur=", "merge;dur=", "render;dur=", "store;dur=", "total;dur=");
		assertThat(Files.readAllBytes(templateStore.resolve("generated").resolve(storedPath))).isEqualTo(pdfBytes);

		MvcResult previewResult = mockMvc()
			.perform(post("/api/pdf/preview/invoice")
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestJson))
			.andExpect(status().isOk())
			.andReturn();
		String previewHtml = previewResult.getResponse().getContentAsString();
		assertThat(previewHtml).contains("Acme Corp").contains("Widget").contains("Gadget");

		mockMvc().perform(delete("/api/templates/invoice"))
			.andExpect(status().isNoContent());
	}

	@Test
	void generatingForUnknownTemplateReturnsNotFound() throws Exception {
		mockMvc().perform(post("/api/pdf/generate/does-not-exist")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isNotFound());
	}
}
