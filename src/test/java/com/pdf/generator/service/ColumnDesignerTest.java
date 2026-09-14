package com.pdf.generator.service;

import static org.assertj.core.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import com.pdf.generator.dto.ColumnDefinition;
import com.pdf.generator.dto.ColumnGroup;

class ColumnDesignerTest {
    private final ColumnConfigService columns = new ColumnConfigService();
    private final HtmlMergeService merge = new HtmlMergeService(columns);
    private static final String HTML = """
        <div data-repeat="sections"><table data-columns="items">
        <colgroup><col data-col="name" style="width:70%"><col data-col="qty" style="width:30%"></colgroup>
        <thead><tr><th data-col="name">Name</th><th data-col="qty">Qty</th></tr></thead>
        <tbody data-repeat="items"><tr><td data-col="name">{{name}}</td><td data-col="qty">{{qty}}</td></tr>
        <tr><td data-colspan="fill">{{description}}</td></tr></tbody>
        <tfoot><tr><td data-colspan="fill">TOTAL</td><td colspan="2">{{total}}</td></tr></tfoot>
        </table></div>
        """;
    private static final Map<String, Object> DATA = Map.of("sections", List.of(
        Map.of("total", "123", "items", List.of(Map.of("name", "Tap", "qty", 2, "brand", "Demo Brand", "description", "Brass tap"))),
        Map.of("total", "456", "items", List.of(Map.of("name", "Shower", "qty", 3, "description", "Wall mounted")))));

    @Test void addsReordersAndEscapesCustomColumnsAcrossNestedRepeats() {
        String config = """
            {"groups":[{"id":"items","ordered":true,"columns":[
            {"field":"brand","label":"<Brand>","custom":true,"type":"text","sizing":"auto"},
            {"field":"qty","sizing":"fixed","width":15}, {"field":"name","sizing":"auto"}]}]}
            """;
        var doc = Jsoup.parse(merge.merge(HTML, DATA, config));
        assertThat(doc.select("table")).hasSize(2);
        assertThat(doc.select("table").first().select("th").eachText()).containsExactly("<Brand>", "Qty", "Name");
        assertThat(doc.select("table").first().select("tbody tr").first().children().eachText()).containsExactly("Demo Brand", "2", "Tap");
        assertThat(doc.select("table").last().select("tbody tr").first().child(0).text()).isEmpty();
        assertThat(doc.select("Brand")).isEmpty();
        assertThat(doc.select("table").first().select("col").get(1).attr("style")).contains("width:15%");
        assertThat(doc.select("tbody tr").get(1).child(0).attr("colspan")).isEqualTo("3");
        assertThat(HTML).doesNotContain("{{brand}}");
    }

    @Test void hideRestoreAndSingleColumnTotalsKeepAllContent() {
        String hidden = """
            {"groups":[{"id":"items","columns":[{"field":"qty","visible":false},
            {"field":"brand","custom":true,"visible":false,"sizing":"auto"}]}]}
            """;
        var doc = Jsoup.parse(merge.merge(HTML, DATA, hidden));
        assertThat(doc.select("table").first().select("th").eachText()).containsExactly("Name");
        assertThat(doc.select("tfoot tr").first().children()).hasSize(1);
        assertThat(doc.select("tfoot tr").first().text()).contains("TOTAL", "123");
        assertThat(doc.select("tfoot td").first().attr("colspan")).isEqualTo("1");
        String restored = hidden.replace("\"field\":\"brand\",\"custom\":true,\"visible\":false", "\"field\":\"brand\",\"custom\":true,\"visible\":true");
        assertThat(merge.merge(HTML, DATA, restored)).contains("Demo Brand");
        assertThat(columns.effective(HTML, hidden, DATA).get(0).columns()).extracting(ColumnDefinition::field).contains("brand");
    }

    @Test void autoWidthsUseRepresentativeContentAndRespectFixedWidth() {
        List<ColumnDefinition> definitions = List.of(
            new ColumnDefinition("qty", "Qty", 10d, null, true, null, "number", "fixed", null),
            new ColumnDefinition("code", "Code", null, null, true, null, "text", "auto", null),
            new ColumnDefinition("description", "Description", null, null, true, null, "text", "auto", null));
        List<Map<String, Object>> samples = new ArrayList<>();
        for (int i = 0; i < 10; i++) samples.add(Map.of("code", "AB", "description", "Long descriptive product information"));
        var group = ColumnLayout.resolve(new ColumnGroup("items", definitions), samples);
        assertThat(group.columns().get(0).resolvedWidth()).isEqualTo(10);
        assertThat(group.columns().get(2).resolvedWidth()).isGreaterThan(group.columns().get(1).resolvedWidth());
        assertThat(group.columns().stream().mapToDouble(ColumnDefinition::resolvedWidth).sum()).isEqualTo(100);
        samples.add(Map.of("code", "x".repeat(10000), "description", "Long descriptive product information"));
        assertThat(ColumnLayout.resolve(new ColumnGroup("items", definitions), samples)).isEqualTo(group);
    }

    @Test void rejectsImpossibleWidthsDuplicateFieldsAndUnsupportedStructures() {
        for (String config : List.of(
            "{\"groups\":[{\"id\":\"items\",\"columns\":[{\"field\":\"name\",\"sizing\":\"fixed\",\"width\":99},{\"field\":\"qty\",\"sizing\":\"auto\"}]}]}",
            "{\"groups\":[{\"id\":\"items\",\"columns\":[{\"field\":\"name\",\"visible\":false},{\"field\":\"qty\",\"visible\":false}]}]}",
            "{\"groups\":[{\"id\":\"items\",\"columns\":[{\"field\":\"name\"},{\"field\":\"name\"}]}]}",
            "{\"groups\":[{\"id\":\"items\",\"columns\":[{\"field\":\"bad-field\",\"custom\":true}]}]}",
            "{\"groups\":[{\"id\":\"items\",\"columns\":[{\"field\":\"qty\",\"sizing\":\"fixed\",\"width\":null}]}]}")) {
            assertThatThrownBy(() -> columns.validateForTemplate(HTML, config)).isInstanceOf(IllegalArgumentException.class);
        }
        String addition = "{\"groups\":[{\"id\":\"items\",\"columns\":[{\"field\":\"brand\",\"custom\":true,\"sizing\":\"auto\"}]}]}";
        assertThatThrownBy(() -> columns.validateForTemplate(HTML.replace("<td data-col=\"name\">", "<td data-col=\"name\" colspan=\"2\">"), addition))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("simple repeating");
    }

    @Test void fieldSuggestionsFollowRepeatScopeAndLegacyPartialOverridesKeepOrder() {
        var metadata = columns.designerMetadata(HTML, DATA);
        assertThat(metadata.toString()).contains("brand=text", "qty=number", "canAdd=true").doesNotContain("sections=");
        var result = columns.effective(HTML, "{\"groups\":[{\"id\":\"items\",\"columns\":[{\"field\":\"qty\",\"label\":\"Quantity\"}]}]}", DATA);
        assertThat(result.get(0).columns()).extracting(ColumnDefinition::field).containsExactly("name", "qty");
        assertThat(result.get(0).columns()).extracting(ColumnDefinition::resolvedWidth).containsExactly(70d, 30d);
    }

    @Test void tablesWithoutColgroupsGetConsistentWidthsAndPayloadCanRestoreCustomColumn() {
        String config = "{\"groups\":[{\"id\":\"items\",\"columns\":[{\"field\":\"brand\",\"custom\":true,\"visible\":false,\"sizing\":\"auto\"}]}]}";
        Map<String, Object> data = new java.util.HashMap<>(DATA);
        data.put("columnVisibility", Map.of("items", Map.of("brand", true)));
        String withoutCols = HTML.replaceAll("(?s)<colgroup>.*?</colgroup>", "");
        var doc = Jsoup.parse(merge.merge(withoutCols, data, config));
        assertThat(doc.select("table").first().select("col")).hasSize(3);
        assertThat(doc.text()).contains("Demo Brand");
        assertThat(doc.select("col")).allSatisfy(col -> assertThat(col.attr("style")).contains("width:"));
    }
}
