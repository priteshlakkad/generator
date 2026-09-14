package com.pdf.generator.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Column preferences. Null sizing preserves the template's proportional sizing. */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ColumnDefinition(String field, String label, Double width, String align, Boolean visible,
        Boolean custom, String type, String sizing, Double resolvedWidth) {

    public ColumnDefinition(String field, String label, Double width, String align, Boolean visible) {
        this(field, label, width, align, visible, null, null, null, null);
    }

    public boolean isVisible() { return visible == null || visible; }
    public boolean customColumn() { return Boolean.TRUE.equals(custom); }

    public ColumnDefinition overriddenBy(ColumnDefinition override) {
        if (override == null) return this;
        return new ColumnDefinition(field,
            override.label != null ? override.label : label,
            override.width != null ? override.width : width,
            override.align != null ? override.align : align,
            override.visible != null ? override.visible : visible,
            custom, override.type != null ? override.type : type,
            override.sizing != null ? override.sizing : sizing, null);
    }

    public ColumnDefinition withVisible(boolean value) {
        return new ColumnDefinition(field, label, width, align, value, custom, type, sizing, null);
    }

    public ColumnDefinition withResolvedWidth(Double value) {
        return new ColumnDefinition(field, label, width, align, visible, custom, type, sizing, value);
    }
}
