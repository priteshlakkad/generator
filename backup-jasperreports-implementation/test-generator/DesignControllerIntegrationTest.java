package com.pdf.generator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.InputStream;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pdf.generator.dto.design.BandDto;
import com.pdf.generator.dto.design.ElementDto;
import com.pdf.generator.dto.design.ReportDesignDto;

@SpringBootTest
class DesignControllerIntegrationTest {

	@TempDir
	static Path templateStore;

	@DynamicPropertySource
	static void overrideTemplateDir(DynamicPropertyRegistry registry) {
		registry.add("pdf.templates.dir", () -> templateStore.toString());
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

	private void uploadSample(String templateType) throws Exception {
		try (InputStream jrxml = getClass().getClassLoader()
				.getResourceAsStream("sample-templates/jaquar-quotation.jrxml")) {
			MockMultipartFile file = new MockMultipartFile("file", templateType + ".jrxml",
				"text/xml", jrxml.readAllBytes());
			mockMvc().perform(multipart("/api/templates/" + templateType).file(file))
				.andExpect(status().isCreated());
		}
	}

	@Test
	void createsBlankTemplateThenAddsElementAndGeneratesPdf() throws Exception {
		mockMvc().perform(post("/api/templates/design-smoke-test/blank")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isCreated());

		mockMvc().perform(post("/api/templates/design-smoke-test/blank")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isConflict());

		MvcResult getResult = mockMvc().perform(get("/api/templates/design-smoke-test/design"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.orientation").value("PORTRAIT"))
			.andReturn();

		ObjectMapper objectMapper = new ObjectMapper();
		ReportDesignDto design = objectMapper.readValue(
			getResult.getResponse().getContentAsString(), ReportDesignDto.class);
		BandDto titleBand = design.getBands().stream()
			.filter(b -> b.getName().equals("title"))
			.findFirst()
			.orElseThrow();

		ElementDto newField = new ElementDto();
		newField.setType("textField");
		newField.setX(0);
		newField.setY(10);
		newField.setWidth(200);
		newField.setHeight(20);
		newField.setExpression("\"hello\"");
		newField.setExpressionClass("java.lang.String");
		titleBand.getElements().add(newField);

		mockMvc().perform(put("/api/templates/design-smoke-test/design")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(design)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.bands[?(@.name == 'title')].elements.length()").value(2));

		mockMvc().perform(delete("/api/templates/design-smoke-test"))
			.andExpect(status().isNoContent());
	}

	@Test
	void editingAndSavingRealTemplateStillGeneratesValidPdf() throws Exception {
		uploadSample("jaquar-quotation-designed");

		MvcResult getResult = mockMvc().perform(get("/api/templates/jaquar-quotation-designed/design"))
			.andExpect(status().isOk())
			.andReturn();
		String designJson = getResult.getResponse().getContentAsString();

		mockMvc().perform(put("/api/templates/jaquar-quotation-designed/design")
				.contentType(MediaType.APPLICATION_JSON)
				.content(designJson))
			.andExpect(status().isOk());

		String requestJson = """
			{
			  "parameters": {
			    "quoteNumber": "Q-560122", "quoteDate": "07/08/2026",
			    "customerAccountCode": "CUS-798033", "customerAccountName": "Rishav Pal",
			    "customerAddress": "Saint Xaviers School Road", "customerContactPerson": "Rishav Pal",
			    "customerCity": "Mumbai", "customerMobile": "9667464958",
			    "customerState": "Maharashtra", "customerEmail": "sanraj@deloitte.com",
			    "totalQuantity": "1", "totalMrp": "INR 3,16,000.00", "totalTaxableAmt": "INR 1,93,771.19",
			    "totalTax": "INR 34,878.81", "netAmt": "INR 2,28,650.00", "amountInWords": "Two Lakh Only",
			    "customerContactName": "Rishav Pal", "customerContactEmail": "sanraj@deloitte.com",
			    "customerContactContactNumber": "9667464958",
			    "salesTeamName": "Arnab Choudhury", "salesTeamEmail": "sanraj@deloitte.com",
			    "salesTeamContactNumber": "9818692508",
			    "createdByName": "Arnab Choudhury", "createdByEmail": "sanraj@deloitte.com",
			    "createdByContactNumber": "9818692508"
			  },
			  "data": [ { "sectionName": "Home - Floor 1 - Section A", "sectionTotal": "INR 12,54,855.00",
			      "slNo": 1, "catNo": "ABT-WHT-FSBTCF2011", "imageUrl": null, "qty": 1,
			      "mrp": "INR 3,16,000.00", "unitPrice": "INR 2,69,000.00", "discPercent": "15.00",
			      "discAmt": "INR 40,350.00", "ratePerUnit": "INR 1,93,771.19", "taxableAmt": "INR 1,93,771.19",
			      "gstPercent": "18.00", "gstAmt": "INR 34,878.81", "totalAmt": "INR 2,28,650.00",
			      "hsnCode": "39229000", "description": "ARTIZE CONFLUENCE 1800 X 800 X 515MM SOL" } ]
			}
			""";

		MvcResult pdfResult = mockMvc()
			.perform(post("/api/pdf/generate/jaquar-quotation-designed")
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestJson))
			.andExpect(status().isOk())
			.andReturn();

		byte[] pdfBytes = pdfResult.getResponse().getContentAsByteArray();
		assertThat(pdfBytes.length).isGreaterThan(0);
		assertThat(new String(pdfBytes, 0, 4)).isEqualTo("%PDF");
	}

	@Test
	void addingFieldAndParameterThenUsingThemInATextFieldGeneratesAPdf() throws Exception {
		mockMvc().perform(post("/api/templates/param-field-smoke/blank")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isCreated());

		mockMvc().perform(post("/api/templates/param-field-smoke/parameters")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"greeting\",\"className\":\"java.lang.String\"}"))
			.andExpect(status().isCreated());

		mockMvc().perform(post("/api/templates/param-field-smoke/fields")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"amount\",\"className\":\"java.lang.Integer\"}"))
			.andExpect(status().isCreated());

		// duplicate name is rejected
		mockMvc().perform(post("/api/templates/param-field-smoke/parameters")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"greeting\",\"className\":\"java.lang.String\"}"))
			.andExpect(status().isBadRequest());

		MvcResult getResult = mockMvc().perform(get("/api/templates/param-field-smoke/design"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.parameters[?(@.name == 'greeting')]").exists())
			.andExpect(jsonPath("$.fields[?(@.name == 'amount')]").exists())
			.andReturn();

		ObjectMapper objectMapper = new ObjectMapper();
		var design = objectMapper.readValue(getResult.getResponse().getContentAsString(), com.pdf.generator.dto.design.ReportDesignDto.class);
		BandDto titleBand = design.getBands().stream().filter(b -> b.getName().equals("title")).findFirst().orElseThrow();

		ElementDto greetingField = new ElementDto();
		greetingField.setType("textField");
		greetingField.setX(0);
		greetingField.setY(0);
		greetingField.setWidth(200);
		greetingField.setHeight(20);
		greetingField.setExpression("$P{greeting}");
		greetingField.setExpressionClass("java.lang.String");
		titleBand.getElements().add(greetingField);

		ElementDto amountField = new ElementDto();
		amountField.setType("textField");
		amountField.setX(0);
		amountField.setY(20);
		amountField.setWidth(200);
		amountField.setHeight(20);
		amountField.setExpression("$F{amount}");
		amountField.setExpressionClass("java.lang.Integer");
		titleBand.getElements().add(amountField);

		mockMvc().perform(put("/api/templates/param-field-smoke/design")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(design)))
			.andExpect(status().isOk());

		MvcResult pdfResult = mockMvc()
			.perform(post("/api/pdf/generate/param-field-smoke")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"parameters\":{\"greeting\":\"Hello\"},\"data\":[{\"amount\":42}]}"))
			.andExpect(status().isOk())
			.andReturn();

		byte[] pdfBytes = pdfResult.getResponse().getContentAsByteArray();
		assertThat(new String(pdfBytes, 0, 4)).isEqualTo("%PDF");

		// removing a parameter still referenced by the saved design fails compile, and leaves it untouched
		mockMvc().perform(delete("/api/templates/param-field-smoke/parameters/greeting"))
			.andExpect(status().isBadRequest());

		mockMvc().perform(get("/api/templates/param-field-smoke/design"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.parameters[?(@.name == 'greeting')]").exists());
	}
}
