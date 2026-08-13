package com.pdf.generator.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

@Service
public class HtmlMergeService {

	private static final Pattern TOKEN_PATTERN = Pattern.compile("\\{\\{\\s*(\\w+)\\s*\\}\\}");

	public String merge(String templateHtml, Map<String, Object> data) {
		Document doc = Jsoup.parse(templateHtml == null ? "" : templateHtml);
		Map<String, Object> topLevel = data != null ? data : Map.of();

		expandRepeats(doc, topLevel);
		substituteTokens(doc, topLevel);

		doc.outputSettings()
			.prettyPrint(false)
			.syntax(Document.OutputSettings.Syntax.xml);
		return doc.outerHtml();
	}

	/**
	 * Expands every [data-repeat] element found under root, one nesting level at a time.
	 * Each cloned item is recursed into so that repeats nested inside a repeat (e.g. a
	 * table of grouped sections, each containing its own table of line items) resolve
	 * against that item's own scope rather than the outer/top-level context.
	 */
	private void expandRepeats(Element root, Map<String, Object> context) {
		for (Element repeatEl : outermostRepeatElements(root)) {
			String key = repeatEl.attr("data-repeat");
			Object rawList = context.get(key);
			List<?> items = rawList instanceof List<?> list ? list : List.of();

			for (Object item : items) {
				Element clone = repeatEl.clone();
				clone.removeAttr("data-repeat");
				Map<String, Object> itemContext = mergedContext(context, item);
				expandRepeats(clone, itemContext);
				substituteTokens(clone, itemContext);
				repeatEl.before(clone);
			}
			repeatEl.remove();
		}
	}

	/**
	 * [data-repeat] elements nested inside another [data-repeat] element are left alone here;
	 * they get expanded during the outer element's own recursive expandRepeats call, against
	 * that specific item's scope.
	 */
	private List<Element> outermostRepeatElements(Element root) {
		Elements all = root.select("[data-repeat]");
		List<Element> outer = new ArrayList<>();
		for (Element el : all) {
			boolean nested = false;
			for (Element ancestor : el.parents()) {
				if (ancestor == root) {
					break;
				}
				if (ancestor.hasAttr("data-repeat")) {
					nested = true;
					break;
				}
			}
			if (!nested) {
				outer.add(el);
			}
		}
		return outer;
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> mergedContext(Map<String, Object> topLevel, Object item) {
		Map<String, Object> merged = new HashMap<>(topLevel);
		if (item instanceof Map<?, ?> itemMap) {
			merged.putAll((Map<String, Object>) itemMap);
		} else {
			merged.put("value", item);
		}
		return merged;
	}

	private void substituteTokens(Node root, Map<String, Object> context) {
		root.traverse((node, depth) -> {
			if (node instanceof TextNode textNode) {
				String text = textNode.getWholeText();
				String replaced = replaceTokens(text, context);
				if (!replaced.equals(text)) {
					textNode.text(replaced);
				}
			} else if (node instanceof Element element) {
				for (Attribute attribute : new ArrayList<>(element.attributes().asList())) {
					String value = attribute.getValue();
					String replaced = replaceTokens(value, context);
					if (!replaced.equals(value)) {
						element.attr(attribute.getKey(), replaced);
					}
				}
			}
		});
	}

	private String replaceTokens(String text, Map<String, Object> context) {
		Matcher matcher = TOKEN_PATTERN.matcher(text);
		StringBuilder result = new StringBuilder();
		while (matcher.find()) {
			Object value = context.get(matcher.group(1));
			matcher.appendReplacement(result, Matcher.quoteReplacement(value != null ? String.valueOf(value) : ""));
		}
		matcher.appendTail(result);
		return result.toString();
	}
}
