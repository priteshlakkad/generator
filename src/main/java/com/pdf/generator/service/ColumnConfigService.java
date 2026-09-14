package com.pdf.generator.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
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
 * <p>The template HTML defines built-in columns; explicitly custom columns live in config. A table opts
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
		List<ColumnGroup> resolved = new ArrayList<>();
		for (ColumnGroup group : groups) {
			List<Map<String, Object>> samples = new ArrayList<>();
			for (Element table : doc.select("[data-columns]")) {
				if (table.attr("data-columns").equals(group.id())) samples.addAll(ColumnLayout.sampleRows(table, payload));
			}
			resolved.add(ColumnLayout.resolve(group, samples));
		}
		return resolved;
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
		if (saved == null || saved.groups().isEmpty()) return derived;
		Map<String, ColumnGroup> savedByGroup = new LinkedHashMap<>();
		for (ColumnGroup group : saved.groups()) savedByGroup.put(group.id(), group);
		List<ColumnGroup> result = new ArrayList<>();
		for (ColumnGroup group : derived) {
			Map<String, ColumnDefinition> remaining = new LinkedHashMap<>();
			for (ColumnDefinition column : group.columns()) remaining.put(column.field(), column);
			List<ColumnDefinition> columns = new ArrayList<>();
			ColumnGroup overrides = savedByGroup.get(group.id());
			if (overrides != null) {
				for (ColumnDefinition override : overrides.columns()) {
					ColumnDefinition original = remaining.remove(override.field());
					if (original != null) columns.add(original.overriddenBy(override));
					else if (override.customColumn()) columns.add(override.withResolvedWidth(null));
				}
			}
			columns.addAll(remaining.values());
			if (overrides == null || !Boolean.TRUE.equals(overrides.ordered())) {
				Map<String, ColumnDefinition> byField = new LinkedHashMap<>();
				columns.forEach(column -> byField.put(column.field(), column));
				columns = new ArrayList<>();
				for (ColumnDefinition original : group.columns()) columns.add(byField.remove(original.field()));
				columns.addAll(byField.values());
			}
			result.add(new ColumnGroup(group.id(), columns, overrides == null ? null : overrides.ordered()));
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
			result.add(new ColumnGroup(group.id(), columns, group.ordered()));
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

		if (group != null) materializeAndOrder(table, group);
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
		for (Element col : ownedElements(table, "col[data-col]")) {
			ColumnDefinition column = config.get(col.attr(COL_ATTR));
			if (column != null && column.resolvedWidth() != null) {
				col.attr("style", withDeclaration(col.attr("style"), "width", format(column.resolvedWidth()) + "%"));
			}
		}
	}

	/** Insert only into unambiguous marked item/header rows; leave description and total rows intact. */
	private void materializeAndOrder(Element table, ColumnGroup group) {
		List<Element> rows = ColumnLayout.itemRows(table);
		List<Element> headers = ownedElements(table, "tr").stream()
			.filter(row -> row.children().stream().anyMatch(c -> c.tagName().equals("th") && c.hasAttr(COL_ATTR))).toList();
		List<ColumnDefinition> additions = group.columns().stream().filter(ColumnDefinition::customColumn)
			.filter(c -> ownedElements(table, "[data-col]").stream().noneMatch(e -> e.attr(COL_ATTR).equals(c.field()))).toList();
		if (additions.stream().anyMatch(ColumnDefinition::isVisible)) {
			if (rows.isEmpty() || headers.size() != 1 || !simpleRow(headers.get(0)) || rows.stream().anyMatch(row -> !simpleRow(row))) {
				throw new IllegalArgumentException("Table " + group.id() + " needs a single marked header and simple repeating item rows to add columns.");
			}
		}
		Element colgroup = ownedElements(table, "colgroup").stream().findFirst().orElse(null);
		if (colgroup == null) colgroup = table.prependElement("colgroup");
		for (ColumnDefinition column : group.columns()) {
			boolean hasCol = ownedElements(table, "col[data-col]").stream().anyMatch(c -> c.attr(COL_ATTR).equals(column.field()));
			if (!hasCol) colgroup.appendElement("col").attr(COL_ATTR, column.field());
		}
		for (ColumnDefinition column : additions) {
			if (!column.isVisible()) continue;
			for (Element header : headers) header.appendElement("th").attr(COL_ATTR, column.field()).text(column.label() == null ? column.field() : column.label());
			for (Element row : rows) {
				row.appendElement("td").attr(COL_ATTR, column.field()).text("{{" + column.field() + "}}")
					.attr("style", "word-wrap:break-word;white-space:normal;text-align:" + ("number".equals(column.type()) ? "right" : "left"));
			}
		}
		List<Element> parents = new ArrayList<>(ownedElements(table, "tr"));
		parents.addAll(ownedElements(table, "colgroup"));
		for (Element parent : parents) {
			List<Element> marked = parent.children().stream().filter(c -> c.hasAttr(COL_ATTR)).toList();
			List<TextNode> slots = new ArrayList<>();
			for (Element marker : marked) {
				TextNode slot = new TextNode("");
				marker.before(slot);
				marker.remove();
				slots.add(slot);
			}
			List<Element> ordered = new ArrayList<>();
			for (ColumnDefinition column : group.columns()) {
				marked.stream().filter(c -> c.attr(COL_ATTR).equals(column.field())).forEach(ordered::add);
			}
			marked.stream().filter(c -> !ordered.contains(c)).forEach(ordered::add);
			for (int i = 0; i < slots.size(); i++) slots.get(i).replaceWith(ordered.get(i));
		}
	}

	private boolean simpleRow(Element row) {
		return !row.children().isEmpty() && row.children().stream().allMatch(cell -> cell.hasAttr(COL_ATTR)
			&& parsePositiveInt(cell.attr("colspan"), 1) == 1 && parsePositiveInt(cell.attr("rowspan"), 1) == 1);
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
			List<Element> cells = row.children().stream().filter(c -> c.tagName().equals("td") || c.tagName().equals("th")).toList();
			if (cells.size() > visibleColumns) {
				Element first = cells.get(0);
				for (int i = 1; i < cells.size(); i++) {
					first.appendText(" ");
					for (org.jsoup.nodes.Node node : new ArrayList<>(cells.get(i).childNodes())) first.appendChild(node);
					cells.get(i).remove();
				}
				first.attr("colspan", String.valueOf(visibleColumns));
				continue;
			}
			int excess = Math.max(0, claimed + fillCells.size() - visibleColumns);
			for (Element cell : cells) {
				if (fillCells.contains(cell)) continue;
				int span = parsePositiveInt(cell.attr("colspan"), 1);
				int reduction = Math.min(excess, span - 1);
				if (reduction > 0) cell.attr("colspan", String.valueOf(span - reduction));
				claimed -= reduction;
				excess -= reduction;
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
			ColumnConfig config = objectMapper.readValue(json, ColumnConfig.class);
			if (config == null) throw new IllegalArgumentException("Column config must be an object.");
			validate(config);
			return config;
		} catch (JacksonException e) {
			throw new IllegalArgumentException("Column config is not valid JSON: " + e.getMessage(), e);
		}
	}

	public void validateForTemplate(String html, String json) {
		ColumnConfig config = parse(json);
		Set<String> ids = new HashSet<>();
		for (ColumnGroup group : deriveFromDocument(Jsoup.parse(html))) ids.add(group.id());
		if (config != null) for (ColumnGroup group : config.groups()) {
			if (!ids.contains(group.id())) throw new IllegalArgumentException("Unknown table group: " + group.id());
		}
		Document draft = Jsoup.parse(html);
		apply(draft, effective(draft, json, Map.of()));
	}

	private void validate(ColumnConfig config) {
		Set<String> groupIds = new HashSet<>();
		for (ColumnGroup group : config.groups()) {
			if (group.id() == null || group.id().isBlank() || !groupIds.add(group.id())) throw new IllegalArgumentException("Column groups must have unique names.");
			Set<String> fields = new HashSet<>();
			for (ColumnDefinition column : group.columns()) {
				if (column.field() == null || column.field().isBlank() || !fields.add(column.field())) throw new IllegalArgumentException("Columns must have unique data fields in " + group.id() + ".");
				if (column.customColumn() && !column.field().matches("[a-zA-Z_][a-zA-Z0-9_]*")) throw new IllegalArgumentException("New data fields must contain only letters, numbers and underscores, starting with a letter or underscore.");
				if (column.width() != null && (!Double.isFinite(column.width()) || column.width() < 0 || column.width() > 100)) throw new IllegalArgumentException("Column widths must be between 0 and 100%.");
				if ("fixed".equals(column.sizing()) && (column.width() == null || column.width() <= 0)) throw new IllegalArgumentException("A fixed column needs a width greater than 0%.");
				if (column.sizing() != null && !Set.of("auto", "fixed", "proportional").contains(column.sizing())) throw new IllegalArgumentException("Unknown width mode.");
				if (column.type() != null && !Set.of("text", "number").contains(column.type())) throw new IllegalArgumentException("New columns support text or number values.");
				if (column.align() != null && !column.align().isBlank() && normalizeAlign(column.align()) == null) throw new IllegalArgumentException("Unknown column alignment.");
			}
		}
	}

	/** Metadata for the popup. Values come from the same repeat scope used for rendering. */
	public Map<String, Object> designerMetadata(String html, Map<String, Object> data) {
		Map<String, Object> result = new LinkedHashMap<>();
		for (Element table : Jsoup.parse(html).select("[data-columns]")) {
			List<Map<String, Object>> samples = ColumnLayout.sampleRows(table, data);
			Map<String, String> fields = new LinkedHashMap<>();
			for (Map<String, Object> row : samples) row.forEach((key, value) -> {
				if (key.matches("[a-zA-Z_][a-zA-Z0-9_]*") && (value == null || value instanceof String || value instanceof Number || value instanceof Boolean)) {
					fields.putIfAbsent(key, value instanceof Number ? "number" : "text");
				}
			});
			List<Element> headers = ownedElements(table, "tr").stream().filter(row -> row.children().stream().anyMatch(c -> c.tagName().equals("th") && c.hasAttr(COL_ATTR))).toList();
			List<Element> rows = ColumnLayout.itemRows(table);
			boolean canAdd = headers.size() == 1 && simpleRow(headers.get(0)) && !rows.isEmpty() && rows.stream().allMatch(this::simpleRow);
			result.put(table.attr("data-columns"), Map.of("fields", fields, "canAdd", canAdd));
		}
		return result;
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
		List<Element> headers = ownedElements(table, "th[data-col]").stream().filter(cell -> cell.attr(COL_ATTR).equals(field)).toList();
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
