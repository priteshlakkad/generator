package com.pdf.generator.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class HtmlMergeServiceTest {

	private final HtmlMergeService service = new HtmlMergeService();

	@Test
	void substitutesTopLevelTokens() {
		String html = "<h1>{{title}}</h1><p>By {{author}}</p>";

		String merged = service.merge(html, Map.of("title", "My Report", "author", "Jane"));

		assertThat(merged).contains("My Report").contains("By Jane").doesNotContain("{{title}}");
	}

	@Test
	void missingTokenSubstitutesToEmptyString() {
		String merged = service.merge("<p>{{missing}}</p>", Map.of());

		assertThat(merged).doesNotContain("{{missing}}");
	}

	@Test
	void repeatsRowPerItemAndSubstitutesRowFields() {
		String html = """
			<table>
			<tbody>
			<tr data-repeat="items"><td>{{name}}</td><td>{{qty}}</td></tr>
			</tbody>
			</table>
			""";

		Map<String, Object> data = Map.of(
			"items", List.of(
				Map.of("name", "Widget", "qty", 2),
				Map.of("name", "Gadget", "qty", 1)));

		String merged = service.merge(html, data);

		assertThat(merged).contains("Widget").contains("Gadget");
		assertThat(merged).doesNotContain("data-repeat");
		// two data rows, no leftover template row
		int rowCount = merged.split("<tr", -1).length - 1;
		assertThat(rowCount).isEqualTo(2);
	}

	@Test
	void repeatedRowCanStillReferenceTopLevelTokens() {
		String html = """
			<div>{{quoteNumber}}</div>
			<table><tbody>
			<tr data-repeat="items"><td>{{name}} - {{quoteNumber}}</td></tr>
			</tbody></table>
			""";
		Map<String, Object> data = Map.of(
			"quoteNumber", "Q-1",
			"items", List.of(Map.of("name", "Widget")));

		String merged = service.merge(html, data);

		assertThat(merged).contains("Widget - Q-1");
	}

	@Test
	void emptyArrayProducesNoRows() {
		String html = "<table><tbody><tr data-repeat=\"items\"><td>{{name}}</td></tr></tbody></table>";

		String merged = service.merge(html, Map.of("items", List.of()));

		assertThat(merged).doesNotContain("data-repeat").doesNotContain("<tr");
	}

	@Test
	void nestedRepeatResolvesAgainstOwnItemScope() {
		String html = """
			<div data-repeat="sections">
			<h2>{{sectionName}}</h2>
			<table><tbody data-repeat="items">
			<tr><td>{{sectionName}}</td><td>{{catNo}}</td><td>{{quoteNumber}}</td></tr>
			</tbody></table>
			</div>
			""";

		Map<String, Object> data = Map.of(
			"quoteNumber", "Q-1",
			"sections", List.of(
				Map.of("sectionName", "Section A", "items", List.of(
					Map.of("catNo", "CAT-1"),
					Map.of("catNo", "CAT-2"))),
				Map.of("sectionName", "Section B", "items", List.of(
					Map.of("catNo", "CAT-3")))));

		String merged = service.merge(html, data);

		assertThat(merged).doesNotContain("data-repeat").doesNotContain("{{");
		assertThat(merged).contains("Section A").contains("Section B");
		assertThat(merged).contains("CAT-1").contains("CAT-2").contains("CAT-3");
		// each item row must see its own section's name, not a mix of both
		int rowCount = merged.split("<tr", -1).length - 1;
		assertThat(rowCount).isEqualTo(3);
		assertThat(merged.indexOf("Section A")).isLessThan(merged.indexOf("CAT-1"));
		assertThat(merged.indexOf("CAT-2")).isLessThan(merged.indexOf("Section B"));
	}

	@Test
	void scalarListItemsAreAvailableAsValue() {
		String html = "<ul><li data-repeat=\"points\">{{value}}</li></ul>";

		String merged = service.merge(html, Map.of("points", List.of("First", "Second")));

		assertThat(merged).contains("<li>First</li>").contains("<li>Second</li>");
	}

	@Test
	void tokensInsideAttributesAreSubstituted() {
		String html = "<img src=\"{{logoUrl}}\"/>";

		String merged = service.merge(html, Map.of("logoUrl", "https://example.com/logo.png"));

		assertThat(merged).contains("src=\"https://example.com/logo.png\"");
	}
}
