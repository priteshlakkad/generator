# PDF Generator

A Spring Boot service that turns HTML templates into PDF documents. Templates use simple `{{token}}` placeholders and `data-repeat` rows for tabular/list data — no JasperReports, no compiled report definitions. It ships with a built-in, browser-based visual template designer so templates can be authored and tested without redeploying the app.

## How it works

1. An HTML template is stored on disk with merge tokens, e.g. `{{customerName}}`, and a repeatable row marked with `data-repeat="items"`.
2. A JSON payload is posted to the API with the actual data (`{ "customerName": "Acme Corp", "items": [...] }`).
3. The merge engine ([`HtmlMergeService`](src/main/java/com/pdf/generator/service/HtmlMergeService.java)) parses the HTML with [jsoup](https://jsoup.org/), expands the repeat rows (one clone per array item, including nested repeats resolved against each item's own scope), and substitutes tokens in text and attributes.
4. The merged HTML is rendered to a PDF byte stream by [`PdfRenderingService`](src/main/java/com/pdf/generator/service/PdfRenderingService.java) using [OpenHTMLtoPDF](https://github.com/danfickle/openhtmltopdf).

Templates are stored as plain files (no database):

```
template-store/
  <templateType>/
    <templateType>.html          # the HTML template
    <templateType>.sample.json   # saved sample/test data for that template
    <templateType>.columns.json  # optional: which table columns are visible
```

## Tech stack

- Java 17, Spring Boot 4.1.0 (`spring-boot-starter-webmvc`, `spring-boot-starter-validation`)
- [jsoup](https://jsoup.org/) 1.23.1 — HTML parsing and DOM manipulation for the merge step
- [OpenHTMLtoPDF](https://github.com/danfickle/openhtmltopdf) (`openhtmltopdf-core`, `openhtmltopdf-pdfbox`) 1.1.73 — HTML → PDF rendering
- [TinyMCE](https://www.tiny.cloud/) (vendored, self-hosted under `static/designer/vendor/tinymce`) — rich text editor powering the visual designer
- Maven (wrapper included, no local Maven install required)

## Getting started

### Prerequisites

- JDK 17+
- No database or external services required

### Run locally

```bash
./mvnw spring-boot:run
```

The app starts on `http://localhost:8080` by default. On first run it creates the template storage directory (`./template-store`) if it doesn't already exist.

### Build a jar

```bash
./mvnw clean package
java -jar target/generator-0.0.1-SNAPSHOT.jar
```

### Run tests

```bash
./mvnw test
```

## Visual template designer

Open **`http://localhost:8080/designer/index.html`** in a browser to:

- List all stored templates (name, size, last modified)
- Create a new blank template
- Open a template in a WYSIWYG editor (TinyMCE) with toolbar buttons to insert merge fields (`{{field}}`) and repeating data tables (`data-repeat` rows)
- Edit and save the sample JSON data used for previews
- **Save & Preview** — merges the template with the sample data and renders it in an iframe
- **Save & Download PDF** — merges and renders a PDF, downloaded directly from the browser
- **Columns** — toggle each data-table column on or off, and edit its header label, width and alignment
- Copy or delete a template

Templates that carry their own `<head>` / `<style>` (like the shipped quotation) open in a **raw
HTML source editor** rather than TinyMCE. The rich-text editor cannot represent `@page` rules,
`<colgroup>` widths or print CSS and would strip them on save; source mode round-trips the document
unchanged. Body-only templates still get the WYSIWYG editor.

## Toggleable table columns

A table opts in by marking itself and its cells, so nothing else in the template has to know the
column count:

```html
<table class="items" data-columns="items">
  <colgroup><col data-col="slNo" style="width:4%"/><col data-col="mrp" style="width:9%"/></colgroup>
  <thead><tr>
    <th data-col="slNo" data-align="center">S. No.</th>
    <th data-col="mrp" data-align="right">MRP</th>
  </tr></thead>
  <tbody data-repeat="items">
    <tr><td data-col="slNo">{{slNo}}</td><td data-col="mrp">{{mrp}}</td></tr>
    <!-- takes every column the other cells in this row don't -->
    <tr><td data-colspan="fill">{{description}}</td></tr>
  </tbody>
</table>
```

| Attribute | On | Meaning |
|---|---|---|
| `data-columns="<id>"` | `<table>` | Marks the table as configurable and names its column group |
| `data-col="<field>"` | `<col>`, `<th>`, `<td>` | Ties the element to a column |
| `data-align="left\|center\|right"` | `<th>` | Default alignment for the column |
| `data-colspan="fill"` | `<td>`, `<th>` | Span every column not claimed by sibling cells in that row |

Hiding a column removes its `<col>`, `<th>` and `<td>`, rescales the remaining widths back to 100%,
and recomputes every `fill` colspan — so there are no hardcoded colspans to keep in sync. These
authoring attributes are stripped from the rendered output.

**Visibility is resolved in three layers, last one wins:**

1. the columns the template markup declares (all visible);
2. `<templateType>.columns.json`, saved from the designer's **Columns** panel;
3. the request payload, for a single quotation:

```jsonc
{
  "hiddenColumns": ["mrp", "discPercent"],          // applies to every table
  "columnVisibility": { "gstPercent": false },      // same, per field
  "columnVisibility": { "items": { "mrp": true } }  // scoped to one data-columns group
}
```

Because the payload wins, a portal can re-enable a column its saved config hides.

## REST API

All endpoints return errors as JSON: `{ "error": "<message>" }`, with `404` for missing templates, `409` for name conflicts, and `400` for invalid input or rendering failures.

`templateType` must match `^[a-zA-Z0-9_-]+$` (case-insensitive, normalized to lowercase).

### PDF generation — `/api/pdf`

| Method | Path | Body | Response | Description |
|---|---|---|---|---|
| POST | `/api/pdf/generate/{templateType}` | JSON object (merge data) | `application/pdf` (file download) | Merges the template with the supplied data and returns the rendered PDF |
| POST | `/api/pdf/preview/{templateType}` | JSON object (merge data) | `text/html` | Merges the template with the supplied data and returns the resulting HTML (no PDF rendering) |

### Template management — `/api/templates`

| Method | Path | Body | Response | Description |
|---|---|---|---|---|
| GET | `/api/templates` | — | `TemplateInfo[]` | List all templates |
| POST | `/api/templates/{templateType}` | raw HTML (`text/plain`) | `TemplateInfo` (201) | Upload/overwrite a template's HTML |
| POST | `/api/templates/{templateType}/blank` | — | `TemplateInfo` (201) | Create a new template from a blank boilerplate (409 if it already exists) |
| GET | `/api/templates/{templateType}/html` | — | raw HTML (`text/plain`) | Fetch a template's raw HTML |
| PUT | `/api/templates/{templateType}/html` | raw HTML (`text/plain`) | `TemplateInfo` | Save/overwrite a template's HTML |
| DELETE | `/api/templates/{templateType}` | — | 204 | Delete a template |
| POST | `/api/templates/{templateType}/copy?newTemplateType={name}` | — | `TemplateInfo` (201) | Clone a template (HTML + sample data) under a new name |
| GET | `/api/templates/{templateType}/sample` | — | JSON | Fetch saved sample data (or a built-in default if none saved) |
| PUT | `/api/templates/{templateType}/sample` | JSON | JSON | Save sample/test merge data for a template |
| GET | `/api/templates/{templateType}/columns` | — | JSON | Effective column config: the columns the template declares, with saved overrides applied |
| PUT | `/api/templates/{templateType}/columns` | JSON | JSON | Save the column config (visibility, label, width, alignment) |

### Example

```bash
# Create a blank template
curl -X POST http://localhost:8080/api/templates/invoice/blank

# Save HTML with a merge field and a repeating table
curl -X PUT http://localhost:8080/api/templates/invoice/html \
  -H "Content-Type: text/plain" \
  --data-binary @invoice.html

# Generate a PDF
curl -X POST http://localhost:8080/api/pdf/generate/invoice \
  -H "Content-Type: application/json" \
  -d @samples/jaquar-quotation-request.json \
  -o invoice.pdf
```

## Configuration

Set in [`application.properties`](src/main/resources/application.properties):

| Property | Default | Description |
|---|---|---|
| `pdf.templates.dir` | `./template-store` | Filesystem directory where templates are stored |
| `pdf.render.base-uri` | *(blank)* | Base URI relative resource paths resolve against, e.g. `https://portal.example.com/`. Blank requires absolute URLs |
| `pdf.render.connect-timeout-ms` | `3000` | Connect timeout when fetching a remote image |
| `pdf.render.read-timeout-ms` | `5000` | Read timeout when fetching a remote image |
| `pdf.render.max-resource-bytes` | `5242880` | Resources larger than this are skipped |
| `pdf.render.cache-size` | `256` | Fetched resources held in memory; `0` disables caching |
| `pdf.render.allowed-hosts` | *(empty = any)* | Hosts permitted to serve remote resources |

### Images and links

Images are referenced with a normal `{{token}}` in `src`, and links with one in `href` — the merge
engine substitutes inside attributes, so both work:

```html
<a href="{{productUrl}}"><img src="{{imageUrl}}" alt=""/></a>
```

`<a href>` becomes a real, clickable PDF link annotation (`https:`, `mailto:` and `tel:` all work).

Remote resources are fetched through a loader that **caches each URL** (a 500-row quotation
repeating one product image costs one HTTP request, not 500), applies the timeouts and size cap
above, understands `data:` URIs, and substitutes a transparent placeholder plus a logged warning
when a URL is unreachable — a broken image never fails the PDF.

> **Security:** image URLs usually come from the request payload and are fetched server-side. Set
> `pdf.render.allowed-hosts` to your portal's host so a payload cannot make the service fetch
> arbitrary internal addresses. It is empty (allow-all) by default so existing setups keep working.

### Fonts

DejaVu Sans is bundled under `src/main/resources/fonts` and registered by `PdfRenderingService`.
The PDF base-14 fonts have no rupee sign, so an INR quotation renders `₹` as a blank box without
it; bundling also keeps output identical regardless of the host's installed fonts.

## Project structure

```
src/main/java/com/pdf/generator/
  controller/     REST controllers (PdfController, TemplateController)
  service/        HtmlMergeService, ColumnConfigService, PdfRenderingService,
                  RemoteResourceLoader, TemplateStorageService
  config/         PdfTemplateProperties, PdfRenderProperties (pdf.* bindings)
  dto/            API types (TemplateInfo, ColumnConfig/ColumnGroup/ColumnDefinition)
  exception/      Custom exceptions + GlobalExceptionHandler
src/main/resources/
  application.properties
  fonts/             Bundled DejaVu Sans (Unicode coverage incl. the rupee sign)
  static/designer/   Built-in visual template designer (HTML/JS + vendored TinyMCE)
template-store/       Stored templates (HTML + sample JSON), created at runtime
samples/              Example request payloads for manual testing
backup-jasperreports-implementation/   Archived prior JasperReports-based implementation, kept for reference only — not used by the running app
```

## Notes

- There is no authentication/authorization on any endpoint — add a security layer before exposing this outside a trusted network.
- Some folders under `template-store/` (e.g. `demo`, `jaquar-quotation`) still contain leftover `.jrxml`/`.jasper` files from a prior JasperReports-based implementation. Only the `.html` and `.sample.json` files in each template folder are read by the current app.
