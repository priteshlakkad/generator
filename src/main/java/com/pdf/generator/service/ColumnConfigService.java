package com.pdf.generator.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import com.pdf.generator.dto.ColumnConfig;
import com.pdf.generator.dto.ColumnDefinition;
import com.pdf.generator.dto.ColumnGroup;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Resolves which columns of a template's data tables are visible, and rewrites the DOM to match.
 *
 * <p>The template HTML is always the source of truth for <em>which</em> columns exist: a table opts
 * in with {@code data-columns="<groupId>"} and marks its {@code <col>}/{@code <th>}/{@code <td>}
 * elements with {@code data-col="<field>"}. A saved {@code <templateType>.columns.json} and the
 * request payload then layer visibility/label/width/align on top, so editing the template can never
 * be silently overridden by a stale config file.
 *
 * <p>Resolution order (last wins): derived from HTML → saved config → request payload.
 */
@Service
public class ColumnConfigService {

	/** Marks a cell that should span every column not taken by its siblings, e.g. a full-width note row. */
	static final String FILL_COLSPAN = "fill";

	private static final String GROUP_ATTR = "data-columns";
	private static final String COL_ATTR = "data-col";
	private static final String COLSPAN_ATTR = "data-colspan";

	private static final Pattern WIDTH_PERCENT = Pattern.compile("width\\s*:\\s*([0-9]*\\.?[0-9]+)\\s*%");
	private static final Set<String> VALID_ALIGNMENTS = Set.of("left", "center", "right");

	private final ObjectMapper objectMapper = JsonMapper.builder()
		.enable(SerializationFeature.INDENT_OUTPUT)
		.build();

	// ---------------------------------------------------------------- resolution

	/** Convenience overload for callers that only hold the raw template HTML (e.g. the designer API). */
	public List<ColumnGroup> effective(String templateHtml, String savedJson, Map<String, Object> payload) {
		return effective(Jsoup.parse(templateHtml == null ? "" : templateHtml), savedJson, payload);
	}

	public List<ColumnGroup> effective(Document doc, String savedJson, Map<String, Object> payload) {
		List<ColumnGroup> groups = deriveFromDocument(doc);
		groups = applySavedConfig(groups, parse(savedJson));
		groups = applyPayloadOverrides(groups, payload);
		return groups;
	}

	/**
	 * Reads the column list straight out of the template markup, so a template with no saved config
	 * still yields a complete, sensible list for the designer UI to render.
	 */
	public List<ColumnGroup> deriveFromDocument(Document doc) {
		List<ColumnGroup> groups = new ArrayList<>();
		for (Element table : doc.select("[" + GROUP_ATTR + "]")) {
			String groupId = table.attr(GROUP_ATTR);
			List<ColumnDefinition> columns = new ArrayList<>();
			for (Element marker : orderedColumnMarkers(table)) {
				String field = marker.attr(COL_ATTR);
				Element header = findHeaderCell(table, field);
				columns.add(new ColumnDefinition(
					field,
					header != null ? header.text().trim() : field,
					parseWidthPercent(marker.attr("style")),
					normalizeAlign(header != null ? header.attr("data-align") : null),
					Boolean.TRUE));
			}
			groups.add(new ColumnGroup(groupId, columns));
		}
		return groups;
	}

	private List<ColumnGroup> applySavedConfig(List<ColumnGroup> derived, ColumnConfig saved) {
		if (saved == null || saved.groups().isEmpty()) {
			return derived;
		}
		Map<String, Map<String, ColumnDefinition>> savedByGroup = new LinkedHashMap<>();
		for (ColumnGroup group : saved.groups()) {
			Map<String, ColumnDefinition> byField = new LinkedHashMap<>();
			for (ColumnDefinition column : group.columns()) {
				if (column.field() != null) {
					byField.put(column.field(), column);
				}
			}
			savedByGroup.put(group.id(), byField);
		}

		List<ColumnGroup> result = new ArrayList<>();
		for (ColumnGroup group : derived) {
			Map<String, ColumnDefinition> overrides = savedByGroup.getOrDefault(group.id(), Map.of());
			List<ColumnDefinition> columns = new ArrayList<>();
			for (ColumnDefinition column : group.columns()) {
				columns.add(column.overriddenBy(overrides.get(column.field())));
			}
			result.add(new ColumnGroup(group.id(), columns));
		}
		return result;
	}

	/**
	 * Honours two payload shapes:
	 * <ul>
	 *   <li>{@code "columnVisibility": { "items": { "mrp": false } }} — scoped to one group;</li>
	 *   <li>{@code "columnVisibility": { "mrp": false }} and
	 *       {@code "hiddenColumns": ["mrp"]} — applied to every group.</li>
	 * </ul>
	 */
	private List<ColumnGroup> applyPayloadOverrides(List<ColumnGroup> groups, Map<String, Object> payload) {
		if (payload == null || payload.isEmpty()) {
			return groups;
		}

		Map<String, Boolean> globalVisibility = new LinkedHashMap<>();
		Map<String, Map<String, Boolean>> scopedVisibility = new LinkedHashMap<>();

		if (payload.get("columnVisibility") instanceof Map<?, ?> visibility) {
			for (Map.Entry<?, ?> entry : visibility.entrySet()) {
				String key = String.valueOf(entry.getKey());
				if (entry.getValue() instanceof Map<?, ?> perGroup) {
					Map<String, Boolean> fields = new LinkedHashMap<>();
					for (Map.Entry<?, ?> fieldEntry : perGroup.entrySet()) {
						Boolean value = asBoolean(fieldEntry.getValue());
						if (value != null) {
							fields.put(String.valueOf(fieldEntry.getKey()), value);
						}
					}
					scopedVisibility.put(key, fields);
				} else {
					Boolean value = asBoolean(entry.getValue());
					if (value != null) {
						globalVisibility.put(key, value);
					}
				}
			}
		}

		if (payload.get("hiddenColumns") instanceof List<?> hidden) {
			for (Object field : hidden) {
				if (field != null) {
					globalVisibility.put(String.valueOf(field), Boolean.FALSE);
				}
			}
		}

		if (globalVisibility.isEmpty() && scopedVisibility.isEmpty()) {
			return groups;
		}

		List<ColumnGroup> result = new ArrayList<>();
		for (ColumnGroup group : groups) {
			Map<String, Boolean> scoped = scopedVisibility.getOrDefault(group.id(), Map.of());
			List<ColumnDefinition> columns = new ArrayList<>();
			for (ColumnDefinition column : group.columns()) {
				Boolean override = scoped.containsKey(column.field())
					? scoped.get(column.field())
					: globalVisibility.get(column.field());
				columns.add(override == null ? column : column.withVisible(override));
			}
			result.add(new ColumnGroup(group.id(), columns));
		}
		return result;
	}

	private Boolean asBoolean(Object value) {
		if (value instanceof Boolean bool) {
			return bool;
		}
		if (value instanceof String text && (text.equalsIgnoreCase("true") || text.equalsIgnoreCase("false"))) {
			return Boolean.valueOf(text);
		}
		return null;
	}

	// ---------------------------------------------------------------- DOM rewriting

	/**
	 * Drops hidden columns and repairs everything that depended on the column count. Runs before
	 * repeat expansion, so it edits one template row rather than every rendered row.
	 */
	public void apply(Document doc, List<ColumnGroup> groups) {
		Map<String, ColumnGroup> byId = new LinkedHashMap<>();
		for (ColumnGroup group : groups) {
			byId.put(group.id(), group);
		}

		for (Element table : doc.select("[" + GROUP_ATTR + "]")) {
			applyToTable(table, byId.get(table.attr(GROUP_ATTR)));
			table.removeAttr(GROUP_ATTR);
		}

		// Any leftover markers (tables that opted out, or fields absent from the config) are
		// authoring metadata, not presentation — never let them reach the renderer.
		for (Element marked : doc.select("[" + COL_ATTR + "], [" + COLSPAN_ATTR + "], [data-align]")) {
			marked.removeAttr(COL_ATTR).removeAttr(COLSPAN_ATTR).removeAttr("data-align");
		}
	}

	private void applyToTable(Element table, ColumnGroup group) {
		Map<String, ColumnDefinition> config = new LinkedHashMap<>();
		if (group != null) {
			for (ColumnDefinition column : group.columns()) {
				config.put(column.field(), column);
			}
		}

		for (Element marker : ownedElements(table, "[" + COL_ATTR + "]")) {
			ColumnDefinition column = config.get(marker.attr(COL_ATTR));
			if (column != null && !column.isVisible()) {
				marker.remove();
			} else if (column != null) {
				restyleCell(marker, column);
			}
		}

		normalizeColumnWidths(table, config);
		resolveFillColspans(table);
	}

	private void restyleCell(Element cell, ColumnDefinition column) {
		if (cell.tagName().equals("th") && column.label() != null && !column.label().isBlank()) {
			cell.text(column.label());
		}
		String align = normalizeAlign(column.align());
		if (align != null && (cell.tagName().equals("th") || cell.tagName().equals("td"))) {
			cell.attr("style", withDeclaration(cell.attr("style"), "text-align", align));
		}
	}

	/**
	 * Rescales the surviving {@code <col>} widths back to 100%, so hiding a column widens the rest
	 * instead of leaving the table short.
	 */
	private void normalizeColumnWidths(Element table, Map<String, ColumnDefinition> config) {
		List<Element> cols = ownedElements(table, "col");
		if (cols.isEmpty()) {
			return;
		}

		double[] widths = new double[cols.size()];
		double total = 0;
		for (int i = 0; i < cols.size(); i++) {
			Element col = cols.get(i);
			ColumnDefinition column = config.get(col.attr(COL_ATTR));
			Double configured = column != null ? column.width() : null;
			Double parsed = parseWidthPercent(col.attr("style"));
			double width = configured != null ? configured : (parsed != null ? parsed : 0);
			widths[i] = Math.max(width, 0);
			total += widths[i];
		}

		// No usable widths anywhere: let the columns share the table evenly.
		if (total <= 0) {
			for (int i = 0; i < widths.length; i++) {
				widths[i] = 100d / widths.length;
			}
			total = 100;
		}

		for (int i = 0; i < cols.size(); i++) {
			double scaled = widths[i] * 100d / total;
			cols.get(i).attr("style", withDeclaration(cols.get(i).attr("style"), "width", format(scaled) + "%"));
		}
	}

	/**
	 * Replaces {@code data-colspan="fill"} with the number of columns left over once the row's other
	 * cells have taken their share. This is what keeps full-width note rows and totals rows correct
	 * after columns are hidden, without any hardcoded colspan in the template.
	 */
	private void resolveFillColspans(Element table) {
		int visibleColumns = visibleColumnCount(table);
		for (Element row : ownedElements(table, "tr")) {
			List<Element> fillCells = new ArrayList<>();
			int claimed = 0;
			for (Element cell : row.children()) {
				if (!cell.tagName().equals("td") && !cell.tagName().equals("th")) {
					continue;
				}
				if (FILL_COLSPAN.equalsIgnoreCase(cell.attr(COLSPAN_ATTR))) {
					fillCells.add(cell);
				} else {
					claimed += parsePositiveInt(cell.attr("colspan"), 1);
				}
			}
			if (fillCells.isEmpty()) {
				continue;
			}
			int remaining = Math.max(visibleColumns - claimed, fillCells.size());
			int each = remaining / fillCells.size();
			int leftover = remaining % fillCells.size();
			for (int i = 0; i < fillCells.size(); i++) {
				fillCells.get(i).attr("colspan", String.valueOf(each + (i == 0 ? leftover : 0)));
			}
		}
	}

	private int visibleColumnCount(Element table) {
		List<Element> cols = ownedElements(table, "col");
		if (!cols.isEmpty()) {
			return cols.size();
		}
		int widest = 0;
		for (Element row : ownedElements(table, "tr")) {
			int span = 0;
			for (Element cell : row.children()) {
				if (cell.tagName().equals("td") || cell.tagName().equals("th")) {
					span += FILL_COLSPAN.equalsIgnoreCase(cell.attr(COLSPAN_ATTR))
						? 1
						: parsePositiveInt(cell.attr("colspan"), 1);
				}
			}
			widest = Math.max(widest, span);
		}
		return Math.max(widest, 1);
	}

	// ---------------------------------------------------------------- JSON

	public ColumnConfig parse(String json) {
		if (json == null || json.isBlank()) {
			return null;
		}
		try {
			return objectMapper.readValue(json, ColumnConfig.class);
		} catch (JacksonException e) {
			throw new IllegalArgumentException("Column config is not valid JSON: " + e.getMessage(), e);
		}
	}

	public String toJson(List<ColumnGroup> groups) {
		try {
			return objectMapper.writeValueAsString(new ColumnConfig(groups));
		} catch (JacksonException e) {
			throw new IllegalStateException("Failed to serialize column config", e);
		}
	}

	// ---------------------------------------------------------------- helpers

	/**
	 * Column markers in document order. The {@code <colgroup>} is authoritative when present because
	 * it carries the widths; otherwise the header row defines the order.
	 */
	private List<Element> orderedColumnMarkers(Element table) {
		List<Element> cols = ownedElements(table, "col[" + COL_ATTR + "]");
		if (!cols.isEmpty()) {
			return cols;
		}
		return ownedElements(table, "th[" + COL_ATTR + "]");
	}

	private Element findHeaderCell(Element table, String field) {
		List<Element> headers = ownedElements(table, "th[" + COL_ATTR + "=" + field + "]");
		return headers.isEmpty() ? null : headers.get(0);
	}

	/**
	 * Restricts a selector to elements belonging to this table rather than to a table nested inside
	 * it, so each {@code data-columns} group only ever rewrites its own cells.
	 */
	private List<Element> ownedElements(Element table, String selector) {
		List<Element> owned = new ArrayList<>();
		for (Element element : table.select(selector)) {
			if (nearestTable(element) == table) {
				owned.add(element);
			}
		}
		return owned;
	}

	private Element nearestTable(Element element) {
		for (Element ancestor : element.parents()) {
			if (ancestor.tagName().equals("table")) {
				return ancestor;
			}
		}
		return null;
	}

	private Double parseWidthPercent(String style) {
		if (style == null || style.isBlank()) {
			return null;
		}
		Matcher matcher = WIDTH_PERCENT.matcher(style);
		return matcher.find() ? Double.valueOf(matcher.group(1)) : null;
	}

	/** Replaces (or appends) a single declaration, leaving the rest of the inline style intact. */
	private String withDeclaration(String style, String property, String value) {
		StringBuilder result = new StringBuilder();
		if (style != null) {
			for (String declaration : style.split(";")) {
				String trimmed = declaration.trim();
				if (trimmed.isEmpty()) {
					continue;
				}
				int colon = trimmed.indexOf(':');
				String name = (colon == -1 ? trimmed : trimmed.substring(0, colon)).trim();
				if (name.equalsIgnoreCase(property)) {
					continue;
				}
				result.append(trimmed).append(';');
			}
		}
		return result.append(property).append(':').append(value).toString();
	}

	private String normalizeAlign(String align) {
		if (align == null) {
			return null;
		}
		String normalized = align.trim().toLowerCase(Locale.ROOT);
		return VALID_ALIGNMENTS.contains(normalized) ? normalized : null;
	}

	private int parsePositiveInt(String text, int fallback) {
		try {
			int value = Integer.parseInt(text.trim());
			return value > 0 ? value : fallback;
		} catch (RuntimeException e) {
			return fallback;
		}
	}

	private String format(double value) {
		double rounded = Math.round(value * 100d) / 100d;
		return rounded == Math.rint(rounded)
			? String.valueOf((long) rounded)
			: String.valueOf(rounded);
	}
}
