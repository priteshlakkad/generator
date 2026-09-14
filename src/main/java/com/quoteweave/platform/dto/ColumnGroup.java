package com.quoteweave.platform.dto;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A table's columns. Ordered opts into config order; older partial overrides keep template order. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ColumnGroup(String id, List<ColumnDefinition> columns, Boolean ordered) {
    public ColumnGroup(String id, List<ColumnDefinition> columns) { this(id, columns, null); }
    public ColumnGroup { columns = columns == null ? List.of() : List.copyOf(columns); }
}
