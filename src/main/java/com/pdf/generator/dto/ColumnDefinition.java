package com.pdf.generator.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One column of a repeating data table, identified by the {@code data-col} marker it carries in the
 * template HTML. Every property except {@code field} is optional: a null property means "leave
 * whatever the template already says", which is what lets a saved config or a request payload
 * override only the bits it cares about.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ColumnDefinition(String field, String label, Double width, String align, Boolean visible) {

	public boolean isVisible() {
		return visible == null || visible;
	}

	/** A copy of this column with every non-null property of {@code override} applied on top. */
	public ColumnDefinition overriddenBy(ColumnDefinition override) {
		if (override == null) {
			return this;
		}
		return new ColumnDefinition(
			field,
			override.label() != null ? override.label() : label,
			override.width() != null ? override.width() : width,
			override.align() != null ? override.align() : align,
			override.visible() != null ? override.visible() : visible);
	}

	public ColumnDefinition withVisible(boolean value) {
		return new ColumnDefinition(field, label, width, align, value);
	}
}
