# QuoteWeave

**Create branded quotations from your data.**

QuoteWeave is a SaaS-ready platform for designing, generating, storing, and delivering professional quotations. It turns HTML templates and JSON data into branded PDF documents using simple `{{token}}` placeholders and `data-repeat` rows — no compiled report definitions required. It ships with a browser-based visual template designer so templates can be authored and tested without redeploying the application.

## How it works

1. An HTML template is stored on disk with merge tokens, e.g. `{{customerName}}`, and a repeatable row marked with `data-repeat="items"`.
2. A JSON payload is posted to the API with the actual data (`{ "customerName": "Acme Corp", "items": [...] }`).
3. The merge engine ([`HtmlMergeService`](src/main/java/com/quoteweave/platform/service/HtmlMergeService.java)) parses the HTML with [jsoup](https://jsoup.org/), expands the repeat rows (one clone per array item, including nested repeats resolved against each item's own scope), and substitutes tokens in text and attributes.
4. The merged HTML is rendered to a PDF byte stream by [`PdfRenderingService`](src/main/java/com/quoteweave/platform/service/PdfRenderingService.java) using [OpenHTMLtoPDF](https://github.com/danfickle/openhtmltopdf).

Templates are stored as plain files (no database):

```
template-store/
  <templateType>/
    <templateType>.html          # the HTML template
    <templateType>.sample.json   # saved sample/test data for that template
    <templateType>.columns.json  # optional: columns, order, visibility and sizing
```

The repository includes three Jaquar examples: `jaquar-quotation` for the original compact
quotation, `jaquar-quotation-enhanced` for a customer-facing project quotation, and
`jaquar-quotation-product-detailed` for a landscape, product-focused BOQ with 18 columns and 100
sample line items. The enhanced templates use [Jaquar green](https://www.brandcolorcode.com/jaquar)
(`#09565F`), the official logo lock-up, commercial and fulfilment fields, approvals, and stable
demonstration images on selected lines. The detailed template intentionally omits the customer,
delivery-address, and authorised-dealer sections; its duplicate classpath sample is stored at
`src/main/resources/samples/jaquar-quotation-product-detailed.sample.json`.

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
java -jar target/quoteweave-platform-0.0.1-SNAPSHOT.jar
```

### Run tests

```bash
./mvnw test
```

## Visual template designer

Open **`http://localhost:8080/designer/index.html`** in a browser to:

- List all stored templates (name, size, last modified)
- Open any template by name or **View Quotation** to see a formatted HTML quotation populated with its saved sample data, without opening the source editor
- **Download PDF** directly from the quotation view; **Refresh** reloads saved sample data and the latest template/column settings
- **Edit Template** opens the template editor; **View Quotation** returns to the saved quotation (save template edits before returning)
- Create a new blank template, which opens directly in the editor
- Edit body-only templates in a WYSIWYG editor (TinyMCE) with toolbar buttons to insert merge fields (`{{field}}`) and repeating data tables (`data-repeat` rows)
- Edit and save the sample JSON data used for previews
- **Save & Preview** — merges the template with the sample data and renders it in an iframe
- **Save & Download PDF** — merges and renders a PDF, downloaded directly from the browser
- **Columns** — opens **Manage Columns** in both the quotation view and editor: add/restore/remove columns, reorder them, edit headings and alignment, and choose Auto or fixed widths. A draft preview shows calculated widths before **Save & Apply** refreshes the quotation
- Copy or delete a template

The quotation view uses the saved sample data for each template; it does not store individual
quotation records. Saving sample data from this view also refreshes the quotation. HTML is shown
as a continuous browser document; use the downloaded PDF to check final pagination and print layout.
Viewing or downloading from this view does not save or modify the template HTML.

Under **Edit Template**, templates that carry their own `<head>` / `<style>` (like the shipped
quotation) open in a **raw HTML source editor** rather than TinyMCE. The rich-text editor cannot represent `@page` rules,
`<colgroup>` widths or print CSS and would strip them on save; source mode round-trips the document
unchanged. Body-only templates still get the WYSIWYG editor.

## Manage Columns

Open a quotation and click **Columns**. Each table has independent settings:

- **Add Column**: choose a scalar field from the sample data or enter a new field name, heading,
  and Text/Number type. New columns use Auto sizing; numbers default to right alignment.
  Fields are resolved from the repeating item's context (including nested `sections` → `items`).
  Missing values are blank. Number type guides sizing/alignment; values are displayed as supplied,
  without calculations or currency conversion.
- **Remove / Restore**: removal hides a column while retaining its definition. At least one column
  must remain visible. The HTML template and sample data are not changed.
- **↑ / ↓**: move a column left or right in the resulting table.
- **Proportional**: preserves legacy width ratios. Existing templates keep this mode until changed.
- **Auto**: estimates space from headings, field types, and representative values from up to 100
  sampled items per table prototype. An 80th-percentile length with a character cap limits the
  influence of long outliers. Compact fields receive smaller minimum widths; text can wrap.
- **Fixed %**: reserves that percentage; remaining space goes to flexible columns. **Auto size all**
  resets every column in the group to Auto. Fixed widths and minimum flexible widths must fit
  within 100%; if every visible column is fixed, their widths must total 100%.
- The draft preview and **actual** percentages update after edits. **Cancel** discards the draft;
  **Save & Apply** validates and saves the configuration for this template. Previewing never writes
  template HTML or column settings. Request-level visibility overrides are flagged in the popup.

The same deterministic layout calculation drives the draft, HTML quotation, and PDF. Widths sum to
100% (to two decimal places); preferred maximum widths can be exceeded when fewer columns need to
fill the table. Auto sizing is an estimate, not a guarantee that arbitrary content fits: verify the
PDF's pagination and print layout. Impossible fixed/minimum width combinations return an actionable
error instead of silently shrinking fixed columns.

Built-in columns remain defined by template HTML. Explicitly added columns are stored with
`custom: true` in the column configuration and materialized during rendering. Adding columns is
supported for tables with one marked header row and simple marked repeating item rows (no merged
cells in those rows). Description and totals rows using `data-colspan="fill"` are adjusted separately;
when only one column remains, total labels and values are combined into that cell. Arbitrary custom
image columns, expressions and calculated values are not supported by this first version.

Example saved configuration (unlisted built-in columns retain their defaults):

```json
{
  "groups": [{
    "id": "items",
    "ordered": true,
    "columns": [
      { "field": "brand", "label": "Brand", "custom": true, "type": "text", "sizing": "auto", "visible": true },
      { "field": "qty", "label": "Qty", "sizing": "fixed", "width": 6, "visible": true },
      { "field": "mrp", "visible": false }
    ]
  }]
}
```

`ordered: true` applies the configuration's column order; legacy partial overrides retain template
order. `resolvedWidth` in API responses is calculated output, not a saved width preference.

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

Hiding a column removes its `<col>`, `<th>` and `<td>`, recalculates the remaining widths,
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
| POST | `/api/pdf/generate/{templateType}` | JSON object (merge data) | `application/pdf` (file download) | Merges, stores, and returns the rendered PDF |
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
| POST | `/api/templates/{templateType}/copy?newTemplateType={name}` | — | `TemplateInfo` (201) | Clone a template (HTML + sample data + column settings) under a new name |
| GET | `/api/templates/{templateType}/sample` | — | JSON | Fetch saved sample data (or a built-in default if none saved) |
| PUT | `/api/templates/{templateType}/sample` | JSON | JSON | Save sample/test merge data for a template |
| GET | `/api/templates/{templateType}/columns` | — | JSON | Effective built-in/custom column config, saved order and calculated widths (without sample data) |
| PUT | `/api/templates/{templateType}/columns` | JSON | JSON | Validate and save column definitions, order, visibility and sizing |
| POST | `/api/templates/{templateType}/columns/preview` | `{ "config": { "groups": [...] }, "data": { ... } }` | JSON | Read-only draft: merged `html`, effective `config` with `resolvedWidth`, field `metadata`, and `warnings` |

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

### Stored quotation output

Every successful `POST /api/pdf/generate/{templateType}` writes the same PDF returned to the
caller beneath `quoteweave.output.dir`. The server generation date, normalized template type, and sanitized
quotation number form the folders:

```text
output/quotations/
  2026/
    09/
      jaquar-quotation-enhanced/
        JQ-PRJ-560122/
          JQ-PRJ-560122-rev-02-20260914-114448-444.pdf
```

The quotation number comes from `quoteNumber`, `quotationNumber`, or `quoteNo`; when none is
provided, the service creates a timestamped `QUOTE-...` identifier. Revision comes from
`revisionNumber` or `revision`. Unsafe filename characters are converted to hyphens, values are
length-limited, and the resolved path must remain under the configured root. A same-millisecond
name collision gets a numeric suffix, so an existing quotation is never overwritten.

The response remains `application/pdf`. Its `Content-Disposition` uses the stored filename and
`X-Quotation-Path` contains the path relative to `quoteweave.output.dir`. Preview calls do not write files.

Generation responses also include `X-Generation-Id`, `X-Generation-Time-Ms`, `X-Processed-Rows`,
and a standard `Server-Timing` header. The `performance.quotation` logger writes one structured
success or failure event per request with submitted line items, successfully processed rows, phase
timings expressed in milliseconds, PDF size, output path, and failed stage. Logs use a two-column
table for easier client demonstrations.
The client demonstration procedure, balanced talking points, limitations, and future metrics are in
[`PERFORMANCE-DEMO.md`](docs/PERFORMANCE-DEMO.md).

### Planned email option

Email delivery is intentionally deferred. A future `POST /api/quotations/email` can accept the
`X-Quotation-Path` returned during generation plus `to`, optional `cc`, `subject`, and `message`.
The implementation should validate that the selected PDF remains beneath `quoteweave.output.dir`, attach
that exact stored file, send through configured SMTP/provider credentials, and record delivery
status and failure details. No email endpoint, provider dependency, or send button exists yet.

## Configuration

Set in [`application.properties`](src/main/resources/application.properties):

| Property | Default | Description |
|---|---|---|
| `quoteweave.templates.dir` | `./template-store` | Filesystem directory where templates are stored |
| `quoteweave.output.dir` | `./output/quotations` | Root for PDFs created through the generation REST endpoint |
| `quoteweave.render.images-dir` | `./image-store` | Optional local catalogue-code image directory for Jaquar quotations |
| `quoteweave.render.base-uri` | *(blank)* | Base URI relative resource paths resolve against, e.g. `https://portal.example.com/`. Blank requires absolute URLs |
| `quoteweave.render.connect-timeout-ms` | `3000` | Connect timeout when fetching a remote image |
| `quoteweave.render.read-timeout-ms` | `5000` | Read timeout when fetching a remote image |
| `quoteweave.render.max-resource-bytes` | `5242880` | Resources larger than this are skipped |
| `quoteweave.render.cache-size` | `256` | Fetched resources held in memory; `0` disables caching |
| `quoteweave.render.allowed-hosts` | *(empty = any)* | Hosts permitted to serve remote resources |

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
> `quoteweave.render.allowed-hosts` to your portal's host so a payload cannot make the service fetch
> arbitrary internal addresses. It is empty (allow-all) by default so existing setups keep working.

### Jaquar quotation images: local → content URL → brand

All three Jaquar quotation templates resolve each product image in this order:

1. **Local image directory** (`quoteweave.render.images-dir`, default `./image-store`): look for the
   uppercase catalogue code with `.png`, `.jpg`, or `.jpeg`, for example
   `image-store/ABT-WHT-FSBTCF2011.jpg`. Adding/replacing a file takes effect on the next preview.
2. **Bundled resources**: the same catalogue-code filename under
   `src/main/resources/static/quotation-assets/jaquar/`. Four verified bathtub images ship with the PoC.
3. **Content URL**: try the item's `imageUrl`, then its optional `contentUrl`. These must return
   image bytes (PNG/JPEG/GIF), not a product web page. URLs use the existing remote cache,
   timeouts, size limit and allowed-host settings. Invalid or unavailable content falls through.
4. **Jaquar brand image**: show the bundled Jaquar logo when no usable product image is available.
   The image's tooltip/alternative text identifies it as a brand fallback.

The header defaults to the Jaquar logo on the left, with the quotation number and date on the right.
To override the local header logo, place `jaquar-logo.png` in `image-store`.
The enhanced sample places verified product photos on four selected lines and uses the Jaquar brand
fallback on the remaining lines. These choices are stored in its sample JSON, so they stay the same
between refreshes and in generated PDFs. Add a catalogue-code image locally or supply its
image/content URL to replace any fallback.

This behavior is opted into by template attributes, so other templates retain their existing image
handling. The shipped quotation uses:

```html
<img src="{{logoUrl}}" alt="Jaquar" data-image-fallback="jaquar" data-image-role="brand"/>
<img src="{{imageUrl}}" alt="{{catNo}}" data-image-fallback="jaquar"
     data-product-code="{{catNo}}" data-content-url="{{contentUrl}}"/>
```

Resolved images are embedded as data URIs in the merged HTML, giving the HTML view, column draft,
and PDF the same images without requiring the browser/PDF renderer to fetch them again. Local
assets work offline. Image resolution attributes are removed from the output. Oversized/corrupt
local or remote images fall through to the next source. Bundled image paths are restricted to the
quotation-assets directory; arbitrary local file URLs are not loaded by this fallback pipeline.

Image provenance and exact product-code matches are recorded in
[`SOURCES.md`](src/main/resources/static/quotation-assets/jaquar/SOURCES.md).

### Fonts

DejaVu Sans is bundled under `src/main/resources/fonts` and registered by `PdfRenderingService`.
The PDF base-14 fonts have no rupee sign, so an INR quotation renders `₹` as a blank box without
it; bundling also keeps output identical regardless of the host's installed fonts.

## Project structure

```
src/main/java/com/quoteweave/platform/
  controller/     REST controllers (PdfController, TemplateController)
  service/        HtmlMergeService, ColumnConfigService, ColumnLayout, PdfRenderingService,
                  RemoteResourceLoader, TemplateStorageService
  config/         PdfTemplateProperties, PdfRenderProperties (quoteweave.* bindings)
  dto/            API types (TemplateInfo, ColumnConfig/ColumnGroup/ColumnDefinition)
  exception/      Custom exceptions + GlobalExceptionHandler
src/main/resources/
  application.properties
  fonts/             Bundled DejaVu Sans (Unicode coverage incl. the rupee sign)
  static/designer/   Built-in visual template designer (HTML/JS + vendored TinyMCE)
template-store/       Stored templates (HTML + sample JSON), created at runtime
samples/              Example request payloads for manual testing
```

## Notes

- There is no authentication/authorization on any endpoint — add a security layer before exposing this outside a trusted network.
- Some folders under `template-store/` (e.g. `demo`, `jaquar-quotation`) still contain leftover `.jrxml`/`.jasper` files from a prior JasperReports-based implementation. Only the `.html` and `.sample.json` files in each template folder are read by the current app.
