package com.pdf.generator.service;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.pdf.generator.config.QuotationOutputProperties;
import com.pdf.generator.exception.PdfGenerationException;

/** Writes every successfully rendered REST quotation to a predictable, collision-safe path. */
@Service
public class QuotationOutputService {
    private static final DateTimeFormatter YEAR = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MM");
    private static final DateTimeFormatter GENERATED_AT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
    private static final int MAX_COLLISION_ATTEMPTS = 1_000;

    private final Path root;
    private final Clock clock;

    @Autowired
    public QuotationOutputService(QuotationOutputProperties properties) {
        this(properties, Clock.systemDefaultZone());
    }

    QuotationOutputService(QuotationOutputProperties properties, Clock clock) {
        if (properties.getDir() == null || properties.getDir().isBlank()) {
            throw new IllegalArgumentException("pdf.output.dir must not be blank");
        }
        this.root = Path.of(properties.getDir()).toAbsolutePath().normalize();
        this.clock = clock;
    }

    public StoredQuotation store(String templateType, Map<String, Object> data, byte[] pdf) {
        if (pdf == null || pdf.length == 0) {
            throw new IllegalArgumentException("Cannot store an empty quotation PDF");
        }

        ZonedDateTime now = ZonedDateTime.now(clock);
        String template = safeSegment(templateType, "quotation");
        String suppliedQuoteNumber = firstValue(data, "quoteNumber", "quotationNumber", "quoteNo");
        boolean generatedQuoteNumber = suppliedQuoteNumber.isBlank();
        String quoteNumber = generatedQuoteNumber
            ? "QUOTE-" + GENERATED_AT.format(now)
            : safeSegment(suppliedQuoteNumber, "quotation");
        String revision = firstValue(data, "revisionNumber", "revision");
        String revisionPart = revision.isBlank() ? "" : "-rev-" + safeSegment(revision, "1");
        String generatedAtPart = generatedQuoteNumber ? "" : "-" + GENERATED_AT.format(now);
        String baseName = quoteNumber + revisionPart + generatedAtPart;
        Path directory = root.resolve(YEAR.format(now)).resolve(MONTH.format(now))
            .resolve(template).resolve(quoteNumber).normalize();
        if (!directory.startsWith(root)) {
            throw new IllegalArgumentException("Quotation output path resolves outside pdf.output.dir");
        }

        Path temporary = null;
        try {
            Files.createDirectories(directory);
            temporary = Files.createTempFile(directory, ".quotation-", ".tmp");
            Files.write(temporary, pdf, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);

            for (int attempt = 1; attempt <= MAX_COLLISION_ATTEMPTS; attempt++) {
                String suffix = attempt == 1 ? "" : "-" + attempt;
                String fileName = baseName + suffix + ".pdf";
                Path target = directory.resolve(fileName);
                try {
                    Files.move(temporary, target);
                    return new StoredQuotation(target, root.relativize(target), fileName);
                } catch (FileAlreadyExistsException ignored) {
                    // A concurrent request used the same millisecond; retain both with a sequence.
                }
            }
            throw new IOException("Could not allocate a unique quotation filename");
        } catch (IOException e) {
            throw new PdfGenerationException("Failed to store generated quotation: " + e.getMessage(), e);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // The target PDF is already safe; an abandoned temp file should not fail the response.
                }
            }
        }
    }

    private String firstValue(Map<String, Object> data, String... fields) {
        if (data == null) return "";
        for (String field : fields) {
            Object value = data.get(field);
            if (value != null && !value.toString().isBlank()) return value.toString().trim();
        }
        return "";
    }

    private String safeSegment(String value, String fallback) {
        String cleaned = value == null ? "" : value.trim()
            .replaceAll("[^A-Za-z0-9_-]+", "-")
            .replaceAll("-{2,}", "-")
            .replaceAll("^[-_]+|[-_]+$", "");
        if (cleaned.isBlank()) cleaned = fallback;
        return cleaned.substring(0, Math.min(cleaned.length(), 80));
    }
}
