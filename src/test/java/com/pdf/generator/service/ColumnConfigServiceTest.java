package com.pdf.generator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import com.pdf.generator.dto.ColumnDefinition;
import com.pdf.generator.dto.ColumnGroup;

class ColumnConfigServiceTest {

	private static final Pattern WIDTH = Pattern.compile("width\\s*:\\s*([0-9.]+)%");

	private final ColumnConfigService service = new ColumnConfigService();
	private final HtmlMergeService mergeService = new HtmlMergeService(service);

	/** A miniature stand-in for the quotation table: colgroup, header, data row, fill rows. */
	private static final String TABLE = """
		<table data-columns="items">
		  <colgroup>
		    <col data-col="slNo" style="width:20%"/>
		    <col data-col="catNo" style="width:30%"/>
		    <col data-col="mrp" style="width:50%"/>
		  </colgroup>
		  <thead><tr>
		    <th data-col="slNo" data-align="center">S. No.</th>
		    <th data-col="catNo" data-align="left">Cat. No</th>
		    <th data-col="mrp" data-align="right">MRP</th>
		  </tr></thead>
		  <tbody data-repeat="items">
		    <tr>
		      <td data-col="slNo">{{slNo}}</td>
		      <td data-col="catNo">{{catNo}}</td>
		      <td data-col="mrp">{{mrp}}</td>
		    </tr>
		    <tr><td class="desc" data-colspan="fill">{{description}}</td></tr>
		  </tbody>
		  <tfoot><tr>
		    <td data-colspan="fill">TOTAL</td>
		    <td>{{sectionTotal}}</td>
		  </tr></tfoot>
		</table>
		""";

	private static final Map<String, Object> ROW = Map.of("items", List.of(
		Map.of("slNo", 1, "catNo", "CAT-1", "mrp", "100", "description", "A thing")));

	// ---------------------------------------------------------------- derivation

	@Test
	void derivesEveryColumnFromTheMarkup() {
		List<ColumnGroup> groups = service.deriveFromDocument(Jsoup.parse(TABLE));

		assertThat(groups).hasSize(1);
		assertThat(groups.get(0).id()).isEqualTo("items");
		assertThat(groups.get(0).columns())
			.extracting(ColumnDefinition::field)
			.containsExactly("slNo", "catNo", "mrp");
		assertThat(groups.get(0).columns())
			.extracting(ColumnDefinition::label)
			.containsExactly("S. No.", "Cat. No", "MRP");
		assertThat(groups.get(0).columns())
			.extracting(ColumnDefinition::align)
			.containsExactly("center", "left", "right");
		assertThat(groups.get(0).columns())
			.extracting(ColumnDefinition::width)
			.containsExactly(20.0, 30.0, 50.0);
	}

	@Test
	void derivesAllThirteenColumnsOfTheShippedQuotationTemplate() throws Exception {
		Path template = Path.of("template-store/jaquar-quotation/jaquar-quotation.html");
		String html = Files.readString(template, StandardCharsets.UTF_8);

		List<ColumnGroup> groups = service.deriveFromDocument(Jsoup.parse(html));

		assertThat(groups).hasSize(1);
		assertThat(groups.get(0).columns()).hasSize(13);
		assertThat(groups.get(0).columns())
			.extracting(ColumnDefinition::field)
			.containsExactly("slNo", "catNo", "imageUrl", "qty", "mrp", "unitPrice", "discPercent",
				"discAmt", "ratePerUnit", "taxableAmt", "gstPercent", "gstAmt", "totalAmt");
	}

	// ---------------------------------------------------------------- hiding

	@Test
	void hidingAColumnRemovesItsColHeaderAndCell() {
		String merged = mergeService.merge(TABLE, ROW, hide("mrp"));

		assertThat(merged).doesNotContain("MRP").doesNotContain(">100<");
		assertThat(merged).contains("Cat. No").contains("CAT-1");

		Document doc = Jsoup.parse(merged);
		assertThat(doc.select("col")).hasSize(2);
		assertThat(doc.select("th")).hasSize(2);
		assertThat(doc.select("tbody tr").first().select("td")).hasSize(2);
	}

	@Test
	void survivingColumnWidthsAreRescaledToOneHundredPercent() {
		String merged = mergeService.merge(TABLE, ROW, hide("mrp"));

		List<Double> widths = widthsOf(merged);
		assertThat(widths).hasSize(2);
		assertThat(widths.stream().mapToDouble(Double::doubleValue).sum()).isCloseTo(100.0, within());
		// 20:30 keeps its ratio once 50% is freed up.
		assertThat(widths.get(0)).isCloseTo(40.0, within());
		assertThat(widths.get(1)).isCloseTo(60.0, within());
	}

	@Test
	void fillColspanTracksTheVisibleColumnCount() {
		assertThat(descColspan(mergeService.merge(TABLE, ROW, null))).isEqualTo(3);
		assertThat(descColspan(mergeService.merge(TABLE, ROW, hide("mrp")))).isEqualTo(2);
		assertThat(descColspan(mergeService.merge(TABLE, ROW, hide("mrp", "catNo")))).isEqualTo(1);
	}

	@Test
	void fillColspanLeavesRoomForSiblingCellsInTheSameRow() {
		// The totals row is "<fill> | value", so the fill cell must stop one column short.
		String merged = mergeService.merge(TABLE, ROW, null);

		assertThat(colspanOf(merged, "TOTAL")).isEqualTo(2);
		assertThat(colspanOf(mergeService.merge(TABLE, ROW, hide("mrp")), "TOTAL")).isEqualTo(1);
	}

	@Test
	void configuredAlignmentAndLabelAreAppliedToTheRenderedTable() {
		String config = """
			{"groups":[{"id":"items","columns":[
			  {"field":"mrp","label":"List Price","align":"center"}]}]}
			""";

		String merged = mergeService.merge(TABLE, ROW, config);

		assertThat(merged).contains("List Price").doesNotContain(">MRP<");
		assertThat(merged).contains("text-align:center");
	}

	@Test
	void authoringAttributesNeverReachTheOutput() {
		String merged = mergeService.merge(TABLE, ROW, null);

		assertThat(merged)
			.doesNotContain("data-col=")
			.doesNotContain("data-columns=")
			.doesNotContain("data-colspan=")
			.doesNotContain("data-align=");
	}

	// ---------------------------------------------------------------- override precedence

	@Test
	void payloadVisibilityOverridesTheSavedConfig() {
		String savedShowsMrp = """
			{"groups":[{"id":"items","columns":[{"field":"mrp","visible":true}]}]}
			""";
		Map<String, Object> payload = withRow(Map.of("columnVisibility", Map.of("mrp", false)));

		assertThat(mergeService.merge(TABLE, payload, savedShowsMrp)).doesNotContain("MRP");
	}

	@Test
	void payloadCanReEnableAColumnTheSavedConfigHides() {
		String savedHidesMrp = """
			{"groups":[{"id":"items","columns":[{"field":"mrp","visible":false}]}]}
			""";

		assertThat(mergeService.merge(TABLE, ROW, savedHidesMrp)).doesNotContain("MRP");

		Map<String, Object> payload = withRow(Map.of("columnVisibility", Map.of("mrp", true)));
		assertThat(mergeService.merge(TABLE, payload, savedHidesMrp)).contains("MRP");
	}

	@Test
	void hiddenColumnsShortcutAppliesToEveryGroup() {
		Map<String, Object> payload = withRow(Map.of("hiddenColumns", List.of("mrp", "catNo")));

		String merged = mergeService.merge(TABLE, payload, null);

		assertThat(merged).doesNotContain("MRP").doesNotContain("Cat. No");
		assertThat(merged).contains("S. No.");
	}

	@Test
	void scopedVisibilityOnlyAffectsTheNamedGroup() {
		Map<String, Object> payload = withRow(Map.of("columnVisibility",
			Map.of("someOtherTable", Map.of("mrp", false))));

		assertThat(mergeService.merge(TABLE, payload, null)).contains("MRP");

		Map<String, Object> scoped = withRow(Map.of("columnVisibility",
			Map.of("items", Map.of("mrp", false))));
		assertThat(mergeService.merge(TABLE, scoped, null)).doesNotContain("MRP");
	}

	@Test
	void savedConfigForAColumnThatNoLongerExistsIsIgnored() {
		String stale = """
			{"groups":[{"id":"items","columns":[{"field":"removedLongAgo","visible":false}]}]}
			""";

		List<ColumnGroup> effective = service.effective(TABLE, stale, Map.of());

		assertThat(effective.get(0).columns()).extracting(ColumnDefinition::field)
			.containsExactly("slNo", "catNo", "mrp");
	}

	// ---------------------------------------------------------------- misc

	@Test
	void tablesWithoutColumnMarkersAreLeftAlone() {
		String plain = "<table><tr><td>{{name}}</td></tr></table>";

		assertThat(mergeService.merge(plain, Map.of("name", "Widget"), null)).contains("Widget");
	}

	@Test
	void nestedTableKeepsItsOwnColumnsWhenTheOuterTableHidesOne() {
		String nested = """
			<table data-columns="outer">
			  <colgroup><col data-col="a" style="width:50%"/><col data-col="b" style="width:50%"/></colgroup>
			  <tr>
			    <td data-col="a">A</td>
			    <td data-col="b"><table data-columns="inner">
			      <colgroup><col data-col="b" style="width:100%"/></colgroup>
			      <tr><td data-col="b">INNER</td></tr>
			    </table></td>
			  </tr>
			</table>
			""";

		// "b" is hidden in the outer table only; the inner table declares its own group.
		String config = """
			{"groups":[{"id":"outer","columns":[{"field":"b","visible":false}]}]}
			""";

		String merged = mergeService.merge(nested, Map.of(), config);

		assertThat(merged).contains("A").doesNotContain("INNER");
	}

	@Test
	void malformedConfigIsRejectedWithAClearMessage() {
		assertThatThrownBy(() -> service.parse("{ not json"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("not valid JSON");
	}

	@Test
	void blankConfigParsesToNothingRatherThanFailing() {
		assertThat(service.parse(null)).isNull();
		assertThat(service.parse("  ")).isNull();
	}

	@Test
	void roundTripsThroughJson() {
		List<ColumnGroup> groups = service.deriveFromDocument(Jsoup.parse(TABLE));

		String json = service.toJson(groups);

		assertThat(service.parse(json).groups()).isEqualTo(groups);
	}

	// ---------------------------------------------------------------- helpers

	private static Map<String, Object> withRow(Map<String, Object> extra) {
		Map<String, Object> payload = new java.util.HashMap<>(ROW);
		payload.putAll(extra);
		return payload;
	}

	private static String hide(String... fields) {
		String columns = java.util.Arrays.stream(fields)
			.map(field -> "{\"field\":\"" + field + "\",\"visible\":false}")
			.reduce((a, b) -> a + "," + b)
			.orElse("");
		return "{\"groups\":[{\"id\":\"items\",\"columns\":[" + columns + "]}]}";
	}

	private static int descColspan(String html) {
		Document doc = Jsoup.parse(html);
		return Integer.parseInt(doc.selectFirst("td.desc").attr("colspan"));
	}

	private static int colspanOf(String html, String cellText) {
		for (org.jsoup.nodes.Element cell : Jsoup.parse(html).select("td")) {
			if (cell.text().trim().equals(cellText)) {
				return Integer.parseInt(cell.attr("colspan"));
			}
		}
		throw new AssertionError("No cell with text '" + cellText + "' in: " + html);
	}

	private static List<Double> widthsOf(String html) {
		return Jsoup.parse(html).select("col").stream()
			.map(col -> {
				Matcher matcher = WIDTH.matcher(col.attr("style"));
				return matcher.find() ? Double.valueOf(matcher.group(1)) : null;
			})
			.toList();
	}

	private static org.assertj.core.data.Offset<Double> within() {
		return org.assertj.core.data.Offset.offset(0.05);
	}
}
