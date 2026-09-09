package com.pdf.generator.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * On-disk shape of {@code <templateType>.columns.json} and the body of the
 * {@code /api/templates/{templateType}/columns} endpoints.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ColumnConfig(List<ColumnGroup> groups) {

	public ColumnConfig {
		groups = groups == null ? List.of() : List.copyOf(groups);
	}
}
