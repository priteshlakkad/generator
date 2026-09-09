package com.pdf.generator.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The columns of one table in a template. The {@code id} matches the table's
 * {@code data-columns} attribute, so a template can carry several independently
 * configurable tables.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ColumnGroup(String id, List<ColumnDefinition> columns) {

	public ColumnGroup {
		columns = columns == null ? List.of() : List.copyOf(columns);
	}
}
