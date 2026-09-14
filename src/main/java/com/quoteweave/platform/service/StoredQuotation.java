package com.quoteweave.platform.service;

import java.nio.file.Path;

/** Result of persisting a REST-generated quotation PDF. */
public record StoredQuotation(Path path, Path relativePath, String fileName) {
}
