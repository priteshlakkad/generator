package com.pdf.generator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.pdf.generator.config.PdfTemplateProperties;
import com.pdf.generator.dto.design.BandDto;
import com.pdf.generator.dto.design.ElementDto;
import com.pdf.generator.dto.design.ReportDesignDto;
import com.pdf.generator.exception.TemplateCompileException;

import net.sf.jasperreports.engine.design.JasperDesign;

class ReportDesignServiceTest {

	private final ReportDesignService reportDesignService = new ReportDesignService();

	@TempDir
	Path tempDir;

	private TemplateStorageService newStorageService() {
		PdfTemplateProperties props = new PdfTemplateProperties();
		props.setDir(tempDir.toString());
		return new TemplateStorageService(props);
	}

	private InputStream sampleJrxml() {
		return getClass().getClassLoader().getResourceAsStream("sample-templates/jaquar-quotation.jrxml");
	}

	@Test
	void parsesSampleTemplateIntoEditableAndReadOnlyBands() throws Exception {
		TemplateStorageService storage = newStorageService();
		storage.save("jaquar-quotation", sampleJrxml());

		JasperDesign design = storage.loadDesign("jaquar-quotation");
		ReportDesignDto dto = reportDesignService.toDto("jaquar-quotation", design);

		assertThat(dto.getPageWidth()).isEqualTo(750);
		assertThat(dto.getPageHeight()).isEqualTo(595);
		assertThat(dto.getOrientation()).isEqualTo("LANDSCAPE");

		assertThat(dto.getBands()).extracting(BandDto::getName)
			.contains("title", "pageHeader", "columnHeader", "detail", "pageFooter",
				"groupHeader:sectionGroup", "groupFooter:sectionGroup");

		assertThat(dto.getReadOnlyBands()).extracting(r -> r.getName())
			.contains("groupFooter:reportEndGroup", "background");

		assertThat(dto.getParameters()).extracting(p -> p.getName()).contains("quoteNumber", "companyLogoPath");
		assertThat(dto.getFields()).extracting(f -> f.getName()).contains("imageUrl", "slNo");
	}

	@Test
	void savingUneditedDesignStillCompilesAndPreservesUntouchedGroups() throws Exception {
		TemplateStorageService storage = newStorageService();
		storage.save("jaquar-quotation", sampleJrxml());

		JasperDesign design = storage.loadDesign("jaquar-quotation");
		ReportDesignDto dto = reportDesignService.toDto("jaquar-quotation", design);

		reportDesignService.applyDesign(design, dto);
		storage.saveDesign("jaquar-quotation", design);

		JasperDesign reloaded = storage.loadDesign("jaquar-quotation");
		assertThat(reloaded.getGroupsList()).hasSize(2);
		assertThat(reloaded.getTitle()).isNotNull();
		assertThat(reloaded.getDetailSection().getBands()).hasSize(1);
	}

	@Test
	void addingAndMovingATextFieldRoundTripsThroughSave() throws Exception {
		TemplateStorageService storage = newStorageService();
		storage.save("jaquar-quotation", sampleJrxml());

		JasperDesign design = storage.loadDesign("jaquar-quotation");
		ReportDesignDto dto = reportDesignService.toDto("jaquar-quotation", design);

		BandDto pageFooter = dto.getBands().stream()
			.filter(b -> b.getName().equals("pageFooter"))
			.findFirst()
			.orElseThrow();

		ElementDto newField = new ElementDto();
		newField.setType("textField");
		newField.setX(500);
		newField.setY(0);
		newField.setWidth(100);
		newField.setHeight(12);
		newField.setExpression("$P{quoteNumber}");
		newField.setExpressionClass("java.lang.String");
		pageFooter.getElements().add(newField);

		reportDesignService.applyDesign(design, dto);
		storage.saveDesign("jaquar-quotation", design);

		JasperDesign reloaded = storage.loadDesign("jaquar-quotation");
		ReportDesignDto reloadedDto = reportDesignService.toDto("jaquar-quotation", reloaded);
		BandDto reloadedFooter = reloadedDto.getBands().stream()
			.filter(b -> b.getName().equals("pageFooter"))
			.findFirst()
			.orElseThrow();

		assertThat(reloadedFooter.getElements()).anySatisfy(el -> {
			assertThat(el.getType()).isEqualTo("textField");
			assertThat(el.getExpression()).isEqualTo("$P{quoteNumber}");
			assertThat(el.getX()).isEqualTo(500);
		});
	}

	@Test
	void failedSaveDoesNotCorruptThePreviouslyStoredJrxml() throws Exception {
		TemplateStorageService storage = newStorageService();
		storage.save("jaquar-quotation", sampleJrxml());
		String goodJrxml = Files.readString(tempDir.resolve("jaquar-quotation/jaquar-quotation.jrxml"));

		JasperDesign design = storage.loadDesign("jaquar-quotation");
		ReportDesignDto dto = reportDesignService.toDto("jaquar-quotation", design);
		BandDto pageFooter = dto.getBands().stream()
			.filter(b -> b.getName().equals("pageFooter"))
			.findFirst()
			.orElseThrow();

		ElementDto brokenField = new ElementDto();
		brokenField.setType("textField");
		brokenField.setX(0);
		brokenField.setY(0);
		brokenField.setWidth(100);
		brokenField.setHeight(12);
		brokenField.setExpression("$P{thisParameterDoesNotExist}");
		brokenField.setExpressionClass("java.lang.String");
		pageFooter.getElements().add(brokenField);

		reportDesignService.applyDesign(design, dto);

		assertThatThrownBy(() -> storage.saveDesign("jaquar-quotation", design))
			.isInstanceOf(TemplateCompileException.class);

		String jrxmlAfterFailedSave = Files.readString(tempDir.resolve("jaquar-quotation/jaquar-quotation.jrxml"));
		assertThat(jrxmlAfterFailedSave).isEqualTo(goodJrxml);
	}

	@Test
	void addedParameterAndFieldCanBeReferencedAndPersist() throws Exception {
		TemplateStorageService storage = newStorageService();
		storage.createBlank("blank-with-params", null);

		JasperDesign design = storage.loadDesign("blank-with-params");
		reportDesignService.addParameter(design, "greeting", "java.lang.String");
		reportDesignService.addField(design, "amount", "java.lang.Integer");
		storage.saveDesign("blank-with-params", design);

		JasperDesign reloaded = storage.loadDesign("blank-with-params");
		ReportDesignDto dto = reportDesignService.toDto("blank-with-params", reloaded);
		assertThat(dto.getParameters()).extracting(p -> p.getName()).contains("greeting");
		assertThat(dto.getFields()).extracting(f -> f.getName()).contains("amount");
	}

	@Test
	void addingDuplicateParameterNameIsRejected() throws Exception {
		TemplateStorageService storage = newStorageService();
		storage.createBlank("blank-dup-param", null);

		JasperDesign design = storage.loadDesign("blank-dup-param");
		reportDesignService.addParameter(design, "greeting", "java.lang.String");

		assertThatThrownBy(() -> reportDesignService.addParameter(design, "greeting", "java.lang.String"))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void removingUnknownParameterIsRejected() throws Exception {
		TemplateStorageService storage = newStorageService();
		storage.createBlank("blank-remove-param", null);
		JasperDesign design = storage.loadDesign("blank-remove-param");

		assertThatThrownBy(() -> reportDesignService.removeParameter(design, "doesNotExist"))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void removingAParameterStillReferencedByAnExpressionFailsCompileOnSave() throws Exception {
		TemplateStorageService storage = newStorageService();
		storage.createBlank("blank-remove-referenced", null);

		JasperDesign design = storage.loadDesign("blank-remove-referenced");
		reportDesignService.addParameter(design, "greeting", "java.lang.String");
		storage.saveDesign("blank-remove-referenced", design);

		JasperDesign reloaded = storage.loadDesign("blank-remove-referenced");
		ReportDesignDto dto = reportDesignService.toDto("blank-remove-referenced", reloaded);
		BandDto title = dto.getBands().stream().filter(b -> b.getName().equals("title")).findFirst().orElseThrow();
		ElementDto field = new ElementDto();
		field.setType("textField");
		field.setX(0);
		field.setY(0);
		field.setWidth(100);
		field.setHeight(12);
		field.setExpression("$P{greeting}");
		field.setExpressionClass("java.lang.String");
		title.getElements().add(field);
		reportDesignService.applyDesign(reloaded, dto);
		storage.saveDesign("blank-remove-referenced", reloaded);

		JasperDesign toMutate = storage.loadDesign("blank-remove-referenced");
		reportDesignService.removeParameter(toMutate, "greeting");

		assertThatThrownBy(() -> storage.saveDesign("blank-remove-referenced", toMutate))
			.isInstanceOf(TemplateCompileException.class);
	}
}
