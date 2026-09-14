package com.pdf.generator.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.pdf.generator.config.QuotationOutputProperties;

class QuotationOutputServiceTest {
    @TempDir
    Path output;

    @Test
    void createsSanitizedDatedFoldersAndNeverOverwritesAQuotation() throws Exception {
        QuotationOutputProperties properties = new QuotationOutputProperties();
        properties.setDir(output.toString());
        Clock clock = Clock.fixed(Instant.parse("2026-09-14T05:45:30.123Z"), ZoneId.of("Asia/Kolkata"));
        QuotationOutputService service = new QuotationOutputService(properties, clock);
        byte[] pdf = "%PDF-sample".getBytes();
        Map<String, Object> data = Map.of("quoteNumber", "../Q/560122", "revisionNumber", "02 / final");

        StoredQuotation first = service.store("jaquar/../../bad", data, pdf);
        StoredQuotation second = service.store("jaquar/../../bad", data, pdf);

        assertThat(first.relativePath().toString().replace('\\', '/')).isEqualTo(
            "2026/09/jaquar-bad/Q-560122/Q-560122-rev-02-final-20260914-111530-123.pdf");
        assertThat(second.fileName()).isEqualTo("Q-560122-rev-02-final-20260914-111530-123-2.pdf");
        assertThat(first.path()).startsWith(output.toAbsolutePath()).isRegularFile();
        assertThat(Files.readAllBytes(first.path())).isEqualTo(pdf);
        assertThat(Files.readAllBytes(second.path())).isEqualTo(pdf);
        try (var paths = Files.walk(output)) {
            assertThat(paths.noneMatch(path -> path.getFileName().toString().endsWith(".tmp"))).isTrue();
        }
    }

    @Test
    void createsATimestampedFallbackWhenThePayloadHasNoQuotationNumber() {
        QuotationOutputProperties properties = new QuotationOutputProperties();
        properties.setDir(output.toString());
        Clock clock = Clock.fixed(Instant.parse("2026-09-14T05:45:30.123Z"), ZoneId.of("Asia/Kolkata"));

        StoredQuotation stored = new QuotationOutputService(properties, clock)
            .store("invoice", Map.of(), "%PDF".getBytes());

        assertThat(stored.relativePath().toString().replace('\\', '/')).isEqualTo(
            "2026/09/invoice/QUOTE-20260914-111530-123/QUOTE-20260914-111530-123.pdf");
    }
}
