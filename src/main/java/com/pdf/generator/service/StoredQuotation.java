package com.pdf.generator.service;

import java.nio.file.Path;

/** Result of persisting a REST-generated quotation PDF. */
public record StoredQuotation(Path path, Path relativePath, String fileName) {
}
