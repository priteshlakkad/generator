package com.quoteweave.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.quoteweave.platform.config.PdfTemplateProperties;
import com.quoteweave.platform.dto.TemplateInfo;
import com.quoteweave.platform.exception.TemplateAlreadyExistsException;
import com.quoteweave.platform.exception.TemplateNotFoundException;

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

	@Test
	void columnConfigRoundTripsAndIsNullUntilSaved() {
		TemplateStorageService service = newService();
		service.save("invoice", "<h1>{{title}}</h1>");

		// Null rather than a default, so callers fall back to the columns the template declares.
		assertThat(service.loadColumns("invoice")).isNull();

		service.saveColumns("invoice", "{\"groups\":[]}");

		assertThat(service.loadColumns("invoice")).isEqualTo("{\"groups\":[]}");
		assertThat(Files.exists(tempDir.resolve("invoice/invoice.columns.json"))).isTrue();
	}

	@Test
	void savingBlankColumnConfigIsRejected() {
		TemplateStorageService service = newService();
		service.save("invoice", "<h1>{{title}}</h1>");

		assertThatThrownBy(() -> service.saveColumns("invoice", "  "))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void columnConfigForUnknownTemplateThrowsNotFound() {
		TemplateStorageService service = newService();

		assertThatThrownBy(() -> service.loadColumns("missing"))
			.isInstanceOf(TemplateNotFoundException.class);
		assertThatThrownBy(() -> service.saveColumns("missing", "{}"))
			.isInstanceOf(TemplateNotFoundException.class);
	}

	@Test
	void copyCarriesColumnConfigToTheNewTemplate() {
		TemplateStorageService service = newService();
		service.save("invoice", "<h1>{{title}}</h1>");
		service.saveColumns("invoice", "{\"groups\":[{\"id\":\"items\",\"columns\":[]}]}");

		service.copy("invoice", "invoice-copy");

		assertThat(service.loadColumns("invoice-copy"))
			.isEqualTo("{\"groups\":[{\"id\":\"items\",\"columns\":[]}]}");
	}

	@Test
	void copyingWithoutColumnConfigOmitsTheFile() {
		TemplateStorageService service = newService();
		service.save("invoice", "<h1>{{title}}</h1>");

		service.copy("invoice", "invoice-copy");

		assertThat(Files.exists(tempDir.resolve("invoice-copy/invoice-copy.columns.json"))).isFalse();
		assertThat(service.loadColumns("invoice-copy")).isNull();
	}

	@Test
	void deleteRemovesColumnConfigAlongWithTheTemplate() {
		TemplateStorageService service = newService();
		service.save("invoice", "<h1>{{title}}</h1>");
		service.saveColumns("invoice", "{\"groups\":[]}");

		service.delete("invoice");

		assertThat(Files.exists(tempDir.resolve("invoice"))).isFalse();
	}
}
