package com.quoteweave.platform.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

import com.quoteweave.platform.config.PdfTemplateProperties;
import com.quoteweave.platform.dto.TemplateInfo;
import com.quoteweave.platform.exception.TemplateAlreadyExistsException;
import com.quoteweave.platform.exception.TemplateNotFoundException;

@Service
public class TemplateStorageService {

	private static final Pattern VALID_TEMPLATE_TYPE = Pattern.compile("^[a-zA-Z0-9_-]+$");

	/**
	 * Body-only and free of a {@code <style>} block on purpose: the designer opens templates that
	 * carry their own stylesheet as raw HTML, and a brand-new template should start in the
	 * rich-text editor.
	 */
	private static final String BLANK_TEMPLATE_HTML = """
		<h1 style="font-family: sans-serif; font-size: 20px;">New Template</h1>
		<p style="font-family: sans-serif; font-size: 12px;">Start typing, or use the toolbar to insert fields and tables.</p>
		""";

	private static final String DEFAULT_SAMPLE_JSON = """
		{
		  "title": "Sample value",
		  "items": [
		    { "name": "Widget", "qty": 2, "amount": "$20.00" },
		    { "name": "Gadget", "qty": 1, "amount": "$15.00" }
		  ]
		}
		""";

	private final Path storageRoot;

	public TemplateStorageService(PdfTemplateProperties properties) {
		this.storageRoot = Path.of(properties.getDir()).toAbsolutePath().normalize();
		try {
			Files.createDirectories(storageRoot);
		} catch (IOException e) {
			throw new IllegalStateException("Could not create template storage directory: " + storageRoot, e);
		}
	}

	public TemplateInfo save(String templateType, String html) {
		if (html == null || html.isBlank()) {
			throw new IllegalArgumentException("Template HTML must not be empty");
		}
		String key = validateAndNormalize(templateType);
		Path dir = templateDir(key);
		Path tempHtml = dir.resolve(key + ".html.tmp");
		try {
			Files.createDirectories(dir);
			Files.writeString(tempHtml, html, StandardCharsets.UTF_8);
			Files.move(tempHtml, htmlPath(key), StandardCopyOption.REPLACE_EXISTING);
			return describe(key);
		} catch (IOException e) {
			throw new IllegalStateException("Failed to store template '" + templateType + "'", e);
		} finally {
			try {
				Files.deleteIfExists(tempHtml);
			} catch (IOException ignored) {
				// best-effort cleanup of the staging file
			}
		}
	}

	public TemplateInfo createBlank(String templateType) {
		String key = validateAndNormalize(templateType);
		if (Files.isDirectory(templateDir(key))) {
			throw new TemplateAlreadyExistsException(templateType);
		}
		return save(key, BLANK_TEMPLATE_HTML);
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
					if (Files.exists(htmlPath(key))) {
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

	public String loadHtml(String templateType) {
		String key = validateAndNormalize(templateType);
		Path html = htmlPath(key);
		if (!Files.exists(html)) {
			throw new TemplateNotFoundException(templateType);
		}
		try {
			return Files.readString(html, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new IllegalStateException("Failed to read template '" + templateType + "'", e);
		}
	}

	public TemplateInfo copy(String sourceType, String newTemplateType) {
		String sourceKey = validateAndNormalize(sourceType);
		String newKey = validateAndNormalize(newTemplateType);
		if (!Files.exists(htmlPath(sourceKey))) {
			throw new TemplateNotFoundException(sourceType);
		}
		if (Files.isDirectory(templateDir(newKey))) {
			throw new TemplateAlreadyExistsException(newTemplateType);
		}
		TemplateInfo info = save(newKey, loadHtml(sourceKey));
		if (Files.exists(sampleDataPath(sourceKey))) {
			saveSampleData(newKey, loadSampleData(sourceKey));
		}
		String columns = loadColumns(sourceKey);
		if (columns != null) {
			saveColumns(newKey, columns);
		}
		return info;
	}

	public String loadSampleData(String templateType) {
		String key = validateAndNormalize(templateType);
		if (!Files.exists(htmlPath(key))) {
			throw new TemplateNotFoundException(templateType);
		}
		Path sampleData = sampleDataPath(key);
		if (!Files.exists(sampleData)) {
			return DEFAULT_SAMPLE_JSON;
		}
		try {
			return Files.readString(sampleData, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new IllegalStateException("Failed to read sample data for '" + templateType + "'", e);
		}
	}

	public void saveSampleData(String templateType, String json) {
		if (json == null || json.isBlank()) {
			throw new IllegalArgumentException("Sample data JSON must not be empty");
		}
		String key = validateAndNormalize(templateType);
		if (!Files.exists(htmlPath(key))) {
			throw new TemplateNotFoundException(templateType);
		}
		Path dir = templateDir(key);
		Path tempSampleData = dir.resolve(key + ".sample.json.tmp");
		try {
			Files.writeString(tempSampleData, json, StandardCharsets.UTF_8);
			Files.move(tempSampleData, sampleDataPath(key), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			throw new IllegalStateException("Failed to store sample data for '" + templateType + "'", e);
		} finally {
			try {
				Files.deleteIfExists(tempSampleData);
			} catch (IOException ignored) {
				// best-effort cleanup of the staging file
			}
		}
	}

	/** @return the saved column config, or null when the template has never had one saved. */
	public String loadColumns(String templateType) {
		String key = validateAndNormalize(templateType);
		if (!Files.exists(htmlPath(key))) {
			throw new TemplateNotFoundException(templateType);
		}
		Path columns = columnsPath(key);
		if (!Files.exists(columns)) {
			return null;
		}
		try {
			return Files.readString(columns, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new IllegalStateException("Failed to read column config for '" + templateType + "'", e);
		}
	}

	public void saveColumns(String templateType, String json) {
		if (json == null || json.isBlank()) {
			throw new IllegalArgumentException("Column config JSON must not be empty");
		}
		String key = validateAndNormalize(templateType);
		if (!Files.exists(htmlPath(key))) {
			throw new TemplateNotFoundException(templateType);
		}
		writeAtomically(columnsPath(key), key + ".columns.json.tmp", json,
			"Failed to store column config for '" + templateType + "'");
	}

	/** Stages to a temp file in the same directory and moves it into place, so a reader never sees a partial write. */
	private void writeAtomically(Path target, String tempFileName, String content, String failureMessage) {
		Path temp = target.getParent().resolve(tempFileName);
		try {
			Files.writeString(temp, content, StandardCharsets.UTF_8);
			Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			throw new IllegalStateException(failureMessage, e);
		} finally {
			try {
				Files.deleteIfExists(temp);
			} catch (IOException ignored) {
				// best-effort cleanup of the staging file
			}
		}
	}

	private TemplateInfo describe(String key) {
		Path html = htmlPath(key);
		try {
			return new TemplateInfo(key, Files.size(html), Files.getLastModifiedTime(html).toMillis());
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

	private Path htmlPath(String key) {
		return templateDir(key).resolve(key + ".html");
	}

	private Path sampleDataPath(String key) {
		return templateDir(key).resolve(key + ".sample.json");
	}

	private Path columnsPath(String key) {
		return templateDir(key).resolve(key + ".columns.json");
	}
}
