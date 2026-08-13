package com.pdf.generator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.pdf.generator.config.PdfTemplateProperties;
import com.pdf.generator.dto.TemplateInfo;
import com.pdf.generator.exception.TemplateCompileException;
import com.pdf.generator.exception.TemplateNotFoundException;

class TemplateStorageServiceTest {

	@TempDir
	Path tempDir;

	private TemplateStorageService newService() {
		PdfTemplateProperties props = new PdfTemplateProperties();
		props.setDir(tempDir.toString());
		return new TemplateStorageService(props);
	}

	private InputStream sampleJrxml() throws Exception {
		return getClass().getClassLoader().getResourceAsStream("sample-templates/jaquar-quotation.jrxml");
	}

	@Test
	void uploadCompilesAndStoresJrxmlAndJasper() throws Exception {
		TemplateStorageService service = newService();

		TemplateInfo info = service.save("jaquar-quotation", sampleJrxml());

		assertThat(info.getTemplateType()).isEqualTo("jaquar-quotation");
		assertThat(Files.exists(tempDir.resolve("jaquar-quotation/jaquar-quotation.jrxml"))).isTrue();
		assertThat(Files.exists(tempDir.resolve("jaquar-quotation/jaquar-quotation.jasper"))).isTrue();
	}

	@Test
	void uploadingInvalidXmlThrowsCompileException() {
		TemplateStorageService service = newService();
		InputStream invalid = new ByteArrayInputStream("<not-a-report>".getBytes());

		assertThatThrownBy(() -> service.save("broken", invalid))
			.isInstanceOf(TemplateCompileException.class);
	}

	@Test
	void reuploadOverwritesPreviousTemplate() throws Exception {
		TemplateStorageService service = newService();

		service.save("jaquar-quotation", sampleJrxml());
		long firstSize = Files.size(tempDir.resolve("jaquar-quotation/jaquar-quotation.jrxml"));

		service.save("jaquar-quotation", sampleJrxml());
		long secondSize = Files.size(tempDir.resolve("jaquar-quotation/jaquar-quotation.jrxml"));

		assertThat(secondSize).isEqualTo(firstSize);
		assertThat(service.list()).hasSize(1);
	}

	@Test
	void deleteRemovesTemplate() throws Exception {
		TemplateStorageService service = newService();
		service.save("jaquar-quotation", sampleJrxml());

		service.delete("jaquar-quotation");

		assertThat(service.list()).isEmpty();
	}

	@Test
	void deletingUnknownTemplateThrowsNotFound() {
		TemplateStorageService service = newService();

		assertThatThrownBy(() -> service.delete("missing"))
			.isInstanceOf(TemplateNotFoundException.class);
	}

	@Test
	void invalidTemplateTypeIsRejected() throws Exception {
		TemplateStorageService service = newService();

		assertThatThrownBy(() -> service.save("../escape", sampleJrxml()))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void failedReuploadDoesNotCorruptThePreviouslyStoredTemplate() throws Exception {
		TemplateStorageService service = newService();
		service.save("jaquar-quotation", sampleJrxml());
		String goodJrxml = Files.readString(tempDir.resolve("jaquar-quotation/jaquar-quotation.jrxml"));

		assertThatThrownBy(() -> service.save("jaquar-quotation", new ByteArrayInputStream("<not-a-report/>".getBytes())))
			.isInstanceOf(TemplateCompileException.class);

		String jrxmlAfterFailedUpload = Files.readString(tempDir.resolve("jaquar-quotation/jaquar-quotation.jrxml"));
		assertThat(jrxmlAfterFailedUpload).isEqualTo(goodJrxml);
	}
}
