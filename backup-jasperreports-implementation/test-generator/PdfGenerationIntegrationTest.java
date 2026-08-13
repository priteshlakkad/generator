package com.pdf.generator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.InputStream;
import java.nio.file.Path;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;
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

@SpringBootTest
class PdfGenerationIntegrationTest {

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

	@Test
	void uploadTemplateThenGenerateReturnsPdf() throws Exception {
		try (InputStream jrxml = getClass().getClassLoader()
				.getResourceAsStream("sample-templates/jaquar-quotation.jrxml")) {
			MockMultipartFile file = new MockMultipartFile("file", "jaquar-quotation.jrxml",
				"text/xml", jrxml.readAllBytes());

			mockMvc().perform(multipart("/api/templates/jaquar-quotation").file(file))
				.andExpect(status().isCreated());
		}

		String requestJson = """
			{
			  "parameters": {
			    "quoteNumber": "Q-560122",
			    "quoteDate": "07/08/2026",
			    "customerAccountCode": "CUS-798033",
			    "customerAccountName": "Rishav Pal",
			    "customerAddress": "Saint Xaviers School Road",
			    "customerContactPerson": "Rishav Pal",
			    "customerCity": "Mumbai",
			    "customerMobile": "9667464958",
			    "customerState": "Maharashtra",
			    "customerEmail": "sanraj@deloitte.com",
			    "totalQuantity": "2",
			    "totalMrp": "INR 3,71,500.00",
			    "totalTaxableAmt": "INR 2,27,771.19",
			    "totalTax": "INR 40,998.81",
			    "netAmt": "INR 2,68,770.00",
			    "amountInWords": "Two Lakh Sixty Eight Thousand Seven Hundred Seventy Rupees",
			    "customerContactName": "Rishav Pal",
			    "customerContactEmail": "sanraj@deloitte.com",
			    "customerContactContactNumber": "9667464958",
			    "salesTeamName": "Arnab Choudhury",
			    "salesTeamEmail": "sanraj@deloitte.com",
			    "salesTeamContactNumber": "9818692508",
			    "createdByName": "Arnab Choudhury",
			    "createdByEmail": "sanraj@deloitte.com",
			    "createdByContactNumber": "9818692508"
			  },
			  "data": [
			    {
			      "sectionName": "Home - Floor 1 - Section A",
			      "sectionTotal": "INR 12,54,855.00",
			      "slNo": 1,
			      "catNo": "ABT-WHT-FSBTCF2011",
			      "imageUrl": null,
			      "qty": 1,
			      "mrp": "INR 3,16,000.00",
			      "unitPrice": "INR 2,69,000.00",
			      "discPercent": "15.00",
			      "discAmt": "INR 40,350.00",
			      "ratePerUnit": "INR 1,93,771.19",
			      "taxableAmt": "INR 1,93,771.19",
			      "gstPercent": "18.00",
			      "gstAmt": "INR 34,878.81",
			      "totalAmt": "INR 2,28,650.00",
			      "hsnCode": "39229000",
			      "description": "ARTIZE CONFLUENCE 1800 X 800 X 515MM SOL"
			    },
			    {
			      "sectionName": "Home - Floor 2 - Section B",
			      "sectionTotal": "INR 47,456.35",
			      "slNo": 11,
			      "catNo": "ABT-WHT-TIAARA190X",
			      "imageUrl": null,
			      "qty": 1,
			      "mrp": "INR 55,500.00",
			      "unitPrice": "INR 47,200.00",
			      "discPercent": "15.00",
			      "discAmt": "INR 7,080.00",
			      "ratePerUnit": "INR 34,000.00",
			      "taxableAmt": "INR 34,000.00",
			      "gstPercent": "18.00",
			      "gstAmt": "INR 6,120.00",
			      "totalAmt": "INR 40,120.00",
			      "hsnCode": "39229000",
			      "description": "TIAARA 1900 X 900 X 520MM BUILT-IN BATHT"
			    }
			  ]
			}
			""";

		MvcResult result = mockMvc()
			.perform(post("/api/pdf/generate/jaquar-quotation")
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestJson))
			.andExpect(status().isOk())
			.andReturn();

		byte[] pdfBytes = result.getResponse().getContentAsByteArray();
		assertThat(pdfBytes.length).isGreaterThan(0);
		assertThat(new String(pdfBytes, 0, 4)).isEqualTo("%PDF");
	}
}
