package com.pdf.generator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.pdf.generator.config.PdfTemplateProperties;
import com.pdf.generator.dto.TemplateInfo;
import com.pdf.generator.exception.TemplateAlreadyExistsException;
import com.pdf.generator.exception.TemplateNotFoundException;

class TemplateStorageServiceTest {

	@TempDir
	Path tempDir;

	private TemplateStorageService newService() {
		PdfTemplateProperties props = new PdfTemplateProperties();
		props.setDir(tempDir.toString());
		return new TemplateStorageService(props);
	}

	@Test
	void savesAndStoresHtml() throws Exception {
		TemplateStorageService service = newService();

		TemplateInfo info = service.save("invoice", "<h1>{{title}}</h1>");

		assertThat(info.getTemplateType()).isEqualTo("invoice");
		assertThat(Files.exists(tempDir.resolve("invoice/invoice.html"))).isTrue();
		assertThat(Files.readString(tempDir.resolve("invoice/invoice.html"))).isEqualTo("<h1>{{title}}</h1>");
	}

	@Test
	void savingBlankHtmlIsRejected() {
		TemplateStorageService service = newService();

		assertThatThrownBy(() -> service.save("invoice", "   "))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void reuploadOverwritesPreviousTemplate() throws Exception {
		TemplateStorageService service = newService();

		service.save("invoice", "<h1>first</h1>");
		service.save("invoice", "<h1>second</h1>");

		assertThat(Files.readString(tempDir.resolve("invoice/invoice.html"))).isEqualTo("<h1>second</h1>");
		assertThat(service.list()).hasSize(1);
	}

	@Test
	void deleteRemovesTemplate() throws Exception {
		TemplateStorageService service = newService();
		service.save("invoice", "<h1>Hi</h1>");

		service.delete("invoice");

		assertThat(service.list()).isEmpty();
	}

	@Test
	void deletingUnknownTemplateThrowsNotFound() {
		TemplateStorageService service = newService();

		assertThatThrownBy(() -> service.delete("missing"))
			.isInstanceOf(TemplateNotFoundException.class);
	}

	@Test
	void invalidTemplateTypeIsRejected() {
		TemplateStorageService service = newService();

		assertThatThrownBy(() -> service.save("../escape", "<h1>Hi</h1>"))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void createBlankWritesAStarterTemplateAndRejectsDuplicates() throws Exception {
		TemplateStorageService service = newService();

		service.createBlank("invoice");

		assertThat(Files.exists(tempDir.resolve("invoice/invoice.html"))).isTrue();
		assertThatThrownBy(() -> service.createBlank("invoice"))
			.isInstanceOf(TemplateAlreadyExistsException.class);
	}

	@Test
	void loadingUnknownTemplateThrowsNotFound() {
		TemplateStorageService service = newService();

		assertThatThrownBy(() -> service.loadHtml("missing"))
			.isInstanceOf(TemplateNotFoundException.class);
	}

	@Test
	void copyDuplicatesHtmlAndSampleDataUnderNewKey() throws Exception {
		TemplateStorageService service = newService();
		service.save("invoice", "<h1>{{title}}</h1>");
		service.saveSampleData("invoice", "{\"title\":\"Hi\"}");

		service.copy("invoice", "invoice-copy");

		assertThat(Files.readString(tempDir.resolve("invoice-copy/invoice-copy.html"))).isEqualTo("<h1>{{title}}</h1>");
		assertThat(service.loadSampleData("invoice-copy")).isEqualTo("{\"title\":\"Hi\"}");
	}

	@Test
	void copyingWithoutSampleDataOmitsSampleFile() throws Exception {
		TemplateStorageService service = newService();
		service.save("invoice", "<h1>{{title}}</h1>");

		service.copy("invoice", "invoice-copy");

		assertThat(Files.exists(tempDir.resolve("invoice-copy/invoice-copy.sample.json"))).isFalse();
	}

	@Test
	void copyingUnknownSourceThrowsNotFound() {
		TemplateStorageService service = newService();

		assertThatThrownBy(() -> service.copy("missing", "invoice-copy"))
			.isInstanceOf(TemplateNotFoundException.class);
	}

	@Test
	void copyingOntoExistingDestinationThrowsAlreadyExists() {
		TemplateStorageService service = newService();
		service.save("invoice", "<h1>{{title}}</h1>");
		service.save("invoice-copy", "<h1>other</h1>");

		assertThatThrownBy(() -> service.copy("invoice", "invoice-copy"))
			.isInstanceOf(TemplateAlreadyExistsException.class);
	}

	@Test
	void sampleDataRoundTripsAndDefaultsWhenMissing() {
		TemplateStorageService service = newService();
		service.save("invoice", "<h1>{{title}}</h1>");

		assertThat(service.loadSampleData("invoice")).contains("Sample value");

		service.saveSampleData("invoice", "{\"title\":\"Custom\"}");

		assertThat(service.loadSampleData("invoice")).isEqualTo("{\"title\":\"Custom\"}");
	}

	@Test
	void loadingSampleDataForUnknownTemplateThrowsNotFound() {
		TemplateStorageService service = newService();

		assertThatThrownBy(() -> service.loadSampleData("missing"))
			.isInstanceOf(TemplateNotFoundException.class);
	}

	@Test
	void savingSampleDataForUnknownTemplateThrowsNotFound() {
		TemplateStorageService service = newService();

		assertThatThrownBy(() -> service.saveSampleData("missing", "{}"))
			.isInstanceOf(TemplateNotFoundException.class);
	}
}
