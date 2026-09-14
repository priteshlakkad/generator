package com.pdf.generator.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pdf.generator.dto.ColumnConfig;
import com.pdf.generator.service.ColumnConfigService;
import com.pdf.generator.service.HtmlMergeService;
import com.pdf.generator.service.TemplateStorageService;

@RestController
@RequestMapping("/api/templates")
public class ColumnDesignerController {
    private final TemplateStorageService storage;
    private final ColumnConfigService columns;
    private final HtmlMergeService merge;

    public ColumnDesignerController(TemplateStorageService storage, ColumnConfigService columns, HtmlMergeService merge) {
        this.storage = storage;
        this.columns = columns;
        this.merge = merge;
    }

    public record DraftRequest(ColumnConfig config, Map<String, Object> data) {}

    /** A draft is rendered without writing either template HTML or column preferences. */
    @PostMapping("/{templateType}/columns/preview")
    public Map<String, Object> preview(@PathVariable String templateType, @RequestBody DraftRequest request) {
        if (request.config() == null) throw new IllegalArgumentException("Column config is required.");
        String html = storage.loadHtml(templateType);
        String json = columns.toJson(request.config().groups());
        columns.validateForTemplate(html, json);
        Map<String, Object> data = request.data() == null ? Map.of() : request.data();
        List<String> warnings = data.containsKey("hiddenColumns") || data.containsKey("columnVisibility")
            ? List.of("Sample data contains column visibility overrides. These take priority over the selections below.") : List.of();
        return Map.of("config", new ColumnConfig(columns.effective(html, json, data)),
            "html", merge.merge(html, data, json), "metadata", columns.designerMetadata(html, data), "warnings", warnings);
    }
}
