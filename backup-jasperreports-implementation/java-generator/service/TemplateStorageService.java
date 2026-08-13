package com.pdf.generator.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

import com.pdf.generator.config.PdfTemplateProperties;
import com.pdf.generator.dto.TemplateInfo;
import com.pdf.generator.dto.design.BlankTemplateSpec;
import com.pdf.generator.exception.TemplateAlreadyExistsException;
import com.pdf.generator.exception.TemplateCompileException;
import com.pdf.generator.exception.TemplateNotFoundException;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.design.JRDesignBand;
import net.sf.jasperreports.engine.design.JRDesignStaticText;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.type.HorizontalTextAlignEnum;
import net.sf.jasperreports.engine.type.OrientationEnum;
import net.sf.jasperreports.engine.type.VerticalTextAlignEnum;
import net.sf.jasperreports.engine.util.JRLoader;
import net.sf.jasperreports.engine.util.JRSaver;
import net.sf.jasperreports.engine.xml.JRXmlLoader;
import net.sf.jasperreports.engine.xml.JRXmlWriter;

@Service
public class TemplateStorageService {

	private static final Pattern VALID_TEMPLATE_TYPE = Pattern.compile("^[a-zA-Z0-9_-]+$");

	private final Path storageRoot;

	public TemplateStorageService(PdfTemplateProperties properties) {
		this.storageRoot = Path.of(properties.getDir()).toAbsolutePath().normalize();
		try {
			Files.createDirectories(storageRoot);
		} catch (IOException e) {
			throw new IllegalStateException("Could not create template storage directory: " + storageRoot, e);
		}
	}

	public TemplateInfo save(String templateType, InputStream jrxmlContent) {
		String key = validateAndNormalize(templateType);
		Path dir = templateDir(key);
		Path tempJrxml = dir.resolve(key + ".jrxml.tmp");
		try {
			Files.createDirectories(dir);
			Files.copy(jrxmlContent, tempJrxml, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

			// Compile from the staged copy first so a bad upload never overwrites a previously working template.
			JasperReport compiled = JasperCompileManager.compileReport(tempJrxml.toString());

			Files.move(tempJrxml, jrxmlPath(key), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			JRSaver.saveObject(compiled, jasperPath(key).toString());

			return describe(key);
		} catch (JRException e) {
			throw new TemplateCompileException("Failed to compile template '" + templateType + "': " + e.getMessage(), e);
		} catch (IOException e) {
			throw new IllegalStateException("Failed to store template '" + templateType + "'", e);
		} finally {
			try {
				Files.deleteIfExists(tempJrxml);
			} catch (IOException ignored) {
				// best-effort cleanup of the staging file
			}
		}
	}

	public List<TemplateInfo> list() {
		if (!Files.isDirectory(storageRoot)) {
			return List.of();
		}
		List<TemplateInfo> result = new ArrayList<>();
		try (Stream<Path> dirs = Files.list(storageRoot)) {
			dirs.filter(Files::isDirectory)
				.sorted(Comparator.comparing(p -> p.getFileName().toString()))
				.forEach(dir -> {
					String key = dir.getFileName().toString();
					if (Files.exists(jrxmlPath(key))) {
						result.add(describe(key));
					}
				});
		} catch (IOException e) {
			throw new IllegalStateException("Failed to list templates", e);
		}
		return result;
	}

	public void delete(String templateType) {
		String key = validateAndNormalize(templateType);
		Path dir = templateDir(key);
		if (!Files.isDirectory(dir)) {
			throw new TemplateNotFoundException(templateType);
		}
		try (Stream<Path> walk = Files.walk(dir)) {
			walk.sorted(Comparator.reverseOrder()).forEach(p -> {
				try {
					Files.delete(p);
				} catch (IOException e) {
					throw new IllegalStateException("Failed to delete " + p, e);
				}
			});
		} catch (IOException e) {
			throw new IllegalStateException("Failed to delete template '" + templateType + "'", e);
		}
	}

	public JasperReport loadCompiledReport(String templateType) {
		String key = validateAndNormalize(templateType);
		Path jasper = jasperPath(key);
		Path jrxml = jrxmlPath(key);
		try {
			if (Files.exists(jasper)) {
				return (JasperReport) JRLoader.loadObject(jasper.toFile());
			}
			if (Files.exists(jrxml)) {
				JasperReport compiled = JasperCompileManager.compileReport(jrxml.toString());
				JRSaver.saveObject(compiled, jasper.toString());
				return compiled;
			}
			throw new TemplateNotFoundException(templateType);
		} catch (JRException e) {
			throw new TemplateCompileException("Failed to load/compile template '" + templateType + "': " + e.getMessage(), e);
		}
	}

	public JasperDesign loadDesign(String templateType) {
		String key = validateAndNormalize(templateType);
		Path jrxml = jrxmlPath(key);
		if (!Files.exists(jrxml)) {
			throw new TemplateNotFoundException(templateType);
		}
		try {
			return JRXmlLoader.load(jrxml.toFile());
		} catch (JRException e) {
			throw new TemplateCompileException("Failed to parse template '" + templateType + "': " + e.getMessage(), e);
		}
	}

	public String loadJrxmlSource(String templateType) {
		String key = validateAndNormalize(templateType);
		Path jrxml = jrxmlPath(key);
		if (!Files.exists(jrxml)) {
			throw new TemplateNotFoundException(templateType);
		}
		try {
			return Files.readString(jrxml);
		} catch (IOException e) {
			throw new IllegalStateException("Failed to read template source for '" + templateType + "'", e);
		}
	}

	public TemplateInfo saveDesign(String templateType, JasperDesign design) {
		String key = validateAndNormalize(templateType);
		Path dir = templateDir(key);
		try {
			// Compile the in-memory design first so an invalid edit never overwrites the on-disk jrxml.
			JasperReport compiled = JasperCompileManager.compileReport(design);

			Files.createDirectories(dir);
			JRXmlWriter.writeReport(design, jrxmlPath(key).toString(), "UTF-8");
			JRSaver.saveObject(compiled, jasperPath(key).toString());

			return describe(key);
		} catch (JRException e) {
			throw new TemplateCompileException("Failed to compile template '" + templateType + "': " + e.getMessage(), e);
		} catch (IOException e) {
			throw new IllegalStateException("Failed to store template '" + templateType + "'", e);
		}
	}

	public TemplateInfo createBlank(String templateType, BlankTemplateSpec spec) {
		String key = validateAndNormalize(templateType);
		if (Files.isDirectory(templateDir(key))) {
			throw new TemplateAlreadyExistsException(templateType);
		}

		JasperDesign design = new JasperDesign();
		design.setName(key);

		boolean landscape = "LANDSCAPE".equalsIgnoreCase(spec != null ? spec.getOrientation() : null);
		design.setOrientation(landscape ? OrientationEnum.LANDSCAPE : OrientationEnum.PORTRAIT);
		int pageWidth = spec != null && spec.getPageWidth() != null ? spec.getPageWidth() : 595;
		int pageHeight = spec != null && spec.getPageHeight() != null ? spec.getPageHeight() : 842;
		int leftMargin = spec != null && spec.getLeftMargin() != null ? spec.getLeftMargin() : 20;
		int rightMargin = spec != null && spec.getRightMargin() != null ? spec.getRightMargin() : 20;
		int topMargin = spec != null && spec.getTopMargin() != null ? spec.getTopMargin() : 20;
		int bottomMargin = spec != null && spec.getBottomMargin() != null ? spec.getBottomMargin() : 20;

		design.setPageWidth(pageWidth);
		design.setPageHeight(pageHeight);
		design.setLeftMargin(leftMargin);
		design.setRightMargin(rightMargin);
		design.setTopMargin(topMargin);
		design.setBottomMargin(bottomMargin);
		design.setColumnWidth(pageWidth - leftMargin - rightMargin);

		JRDesignBand titleBand = new JRDesignBand();
		titleBand.setHeight(40);
		JRDesignStaticText placeholder = new JRDesignStaticText(design);
		placeholder.setX(0);
		placeholder.setY(0);
		placeholder.setWidth(design.getColumnWidth());
		placeholder.setHeight(40);
		placeholder.setText(key);
		placeholder.setHorizontalTextAlign(HorizontalTextAlignEnum.CENTER);
		placeholder.setVerticalTextAlign(VerticalTextAlignEnum.MIDDLE);
		titleBand.addElement(placeholder);
		design.setTitle(titleBand);

		return saveDesign(key, design);
	}

	private TemplateInfo describe(String key) {
		Path jrxml = jrxmlPath(key);
		try {
			return new TemplateInfo(key, Files.size(jrxml), Files.getLastModifiedTime(jrxml).toMillis());
		} catch (IOException e) {
			throw new IllegalStateException("Failed to read template metadata for '" + key + "'", e);
		}
	}

	private String validateAndNormalize(String templateType) {
		if (templateType == null || !VALID_TEMPLATE_TYPE.matcher(templateType).matches()) {
			throw new IllegalArgumentException(
				"templateType must contain only letters, numbers, '-' and '_' (got: '" + templateType + "')");
		}
		return templateType.toLowerCase();
	}

	private Path templateDir(String key) {
		return storageRoot.resolve(key);
	}

	private Path jrxmlPath(String key) {
		return templateDir(key).resolve(key + ".jrxml");
	}

	private Path jasperPath(String key) {
		return templateDir(key).resolve(key + ".jasper");
	}
}
