package com.quoteweave.platform.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jsoup.nodes.Element;

import com.quoteweave.platform.dto.ColumnDefinition;
import com.quoteweave.platform.dto.ColumnGroup;

/** Shared deterministic sizing and sample discovery for the designer, HTML and PDF. */
final class ColumnLayout {
    private ColumnLayout() {}

    /** Follow actual repeat scopes, including sections/items, rather than searching unrelated JSON keys. */
    static List<Map<String, Object>> sampleRows(Element table, Map<String, Object> payload) {
        List<Element> rows = itemRows(table);
        if (rows.isEmpty()) return List.of();
        Element row = rows.get(0);
        List<Element> scope = new ArrayList<>(row.parents());
        Collections.reverse(scope);
        scope.add(row);
        List<Map<String, Object>> contexts = List.of(payload == null ? Map.of() : payload);
        for (Element element : scope) {
            if (!element.hasAttr("data-repeat")) continue;
            List<Map<String, Object>> next = new ArrayList<>();
            for (Map<String, Object> context : contexts) {
                if (context.get(element.attr("data-repeat")) instanceof List<?> items) {
                    for (Object item : items) {
                        if (next.size() >= 100) break;
                        Map<String, Object> merged = new LinkedHashMap<>(context);
                        if (item instanceof Map<?, ?> map) {
                            map.forEach((key, value) -> merged.put(String.valueOf(key), value));
                        } else merged.put("value", item);
                        next.add(merged);
                    }
                }
            }
            contexts = next;
        }
        return contexts;
    }

    static List<Element> itemRows(Element table) {
        return table.select("tr").stream().filter(row -> row.closest("table") == table)
            .filter(row -> row.children().stream().anyMatch(cell -> cell.tagName().equals("td") && cell.hasAttr("data-col")))
            .filter(row -> {
                for (Element parent = row; parent != null; parent = parent.parent()) {
                    if (parent.hasAttr("data-repeat")) return true;
                    if (parent == table) break;
                }
                return false;
            }).toList();
    }

    static ColumnGroup resolve(ColumnGroup group, List<Map<String, Object>> samples) {
        List<ColumnDefinition> visible = group.columns().stream().filter(ColumnDefinition::isVisible).toList();
        if (visible.isEmpty()) throw new IllegalArgumentException("Keep at least one visible column in " + group.id() + ".");
        boolean smart = visible.stream().anyMatch(c -> "auto".equals(c.sizing()) || "fixed".equals(c.sizing()));
        double[] widths = new double[visible.size()];
        if (!smart) {
            double sum = visible.stream().mapToDouble(c -> c.width() == null ? 0 : c.width()).sum();
            for (int i = 0; i < widths.length; i++) {
                widths[i] = sum <= 0 ? 100d / widths.length : (visible.get(i).width() == null ? 0 : visible.get(i).width()) * 100 / sum;
            }
        } else {
            double reserved = 0;
            List<Integer> flexible = new ArrayList<>();
            double[] weights = new double[widths.length];
            double[] preferredMax = new double[widths.length];
            for (int i = 0; i < widths.length; i++) {
                ColumnDefinition column = visible.get(i);
                if ("fixed".equals(column.sizing())) {
                    widths[i] = column.width();
                } else {
                    flexible.add(i);
                    boolean compact = column.field().matches("(?i).*(qty|quantity|slno|percent).*?");
                    boolean numeric = "number".equals(column.type()) || column.field().matches("(?i).*(price|amount|amt|rate|mrp|total).*?")
                        || samples.stream().anyMatch(row -> row.get(column.field()) instanceof Number);
                    widths[i] = compact ? 3 : numeric ? 6 : 5;
                    preferredMax[i] = compact ? 10 : numeric ? 22 : 40;
                    List<Integer> lengths = samples.stream().map(row -> row.get(column.field()))
                        .filter(value -> value != null && !(value instanceof Map) && !(value instanceof List))
                        .map(value -> Math.min(40, String.valueOf(value).length())).sorted().toList();
                    int typical = lengths.isEmpty() ? 8 : lengths.get((int) Math.floor((lengths.size() - 1) * 0.8));
                    int header = Math.min(24, column.label() == null ? column.field().length() : column.label().length());
                    weights[i] = "proportional".equals(column.sizing()) || column.sizing() == null
                        ? Math.max(1, column.width() == null ? 8 : column.width())
                        : Math.max(2, Math.max(header, typical));
                }
                reserved += widths[i];
            }
            if (reserved > 100.00001 || (flexible.isEmpty() && Math.abs(reserved - 100) > 0.00001)) {
                throw new IllegalArgumentException("Columns in " + group.id() + " cannot fit: reduce fixed widths, remove columns, or choose Auto. All-fixed widths must total 100%.");
            }
            double remaining = 100 - reserved;
            List<Integer> active = new ArrayList<>(flexible);
            while (remaining > 0.000001 && !active.isEmpty()) {
                double weight = active.stream().mapToDouble(i -> weights[i]).sum();
                double allocated = 0;
                for (int i : active) {
                    double extra = Math.min(remaining * weights[i] / weight, Math.max(0, preferredMax[i] - widths[i]));
                    widths[i] += extra;
                    allocated += extra;
                }
                remaining -= allocated;
                active.removeIf(i -> widths[i] >= preferredMax[i] - 0.000001);
            }
            // Maxima are preferences: a small number of columns must still fill the page.
            if (remaining > 0.000001 && !flexible.isEmpty()) {
                double weight = flexible.stream().mapToDouble(i -> weights[i]).sum();
                for (int i : flexible) widths[i] += remaining * weights[i] / weight;
            }
        }
        Map<String, Double> resolved = new LinkedHashMap<>();
        int total = 0;
        int adjustment = 0;
        for (int i = 0; i < widths.length; i++) {
            int hundredths = (int) Math.round(widths[i] * 100);
            resolved.put(visible.get(i).field(), hundredths / 100d);
            total += hundredths;
            if (!"fixed".equals(visible.get(i).sizing())) adjustment = i;
        }
        String field = visible.get(adjustment).field();
        resolved.put(field, Math.round((resolved.get(field) + (10000 - total) / 100d) * 100) / 100d);
        return new ColumnGroup(group.id(), group.columns().stream()
            .map(c -> c.withResolvedWidth(resolved.get(c.field()))).toList(), group.ordered());
    }
}
