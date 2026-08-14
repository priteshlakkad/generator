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
- Copy or delete a template

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

## Project structure

```
src/main/java/com/pdf/generator/
  controller/     REST controllers (PdfController, TemplateController)
  service/        HtmlMergeService, PdfRenderingService, TemplateStorageService
  config/         PdfTemplateProperties (pdf.templates.dir binding)
  dto/            API response types (TemplateInfo)
  exception/      Custom exceptions + GlobalExceptionHandler
src/main/resources/
  application.properties
  static/designer/   Built-in visual template designer (HTML/JS + vendored TinyMCE)
template-store/       Stored templates (HTML + sample JSON), created at runtime
samples/              Example request payloads for manual testing
backup-jasperreports-implementation/   Archived prior JasperReports-based implementation, kept for reference only — not used by the running app
```

## Notes

- There is no authentication/authorization on any endpoint — add a security layer before exposing this outside a trusted network.
- Some folders under `template-store/` (e.g. `demo`, `jaquar-quotation`) still contain leftover `.jrxml`/`.jasper` files from a prior JasperReports-based implementation. Only the `.html` and `.sample.json` files in each template folder are read by the current app.
