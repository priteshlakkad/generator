# Quotation generation performance demo

## What is measured

Every successful call to `POST /api/pdf/generate/{templateType}` produces one tabular log event
from the `performance.quotation` logger:

```text
| Quotation performance | Value |
|-----------------------|-------|
| Status                | SUCCESS |
| Engine                | openhtmltopdf |
| Submitted rows        | 100 |
| Processed rows        | 100 |
| Template load         | 0.245 ms |
| HTML merge            | 48.120 ms |
| PDF render            | 802.400 ms |
| File store            | 1.001 ms |
| Total                 | 851.766 ms |
```

The response exposes the same request identity and timings:

- `X-Generation-Id`: correlates the client response with the server log.
- `X-Generation-Time-Ms`: total server-side generation time.
- `X-Processed-Rows`: quotation line items successfully expanded and passed to PDF rendering.
- `Server-Timing`: template load, merge, render, store, and total durations.
- `X-Quotation-Path`: stored output path beneath `quoteweave.output.dir`.

Failures produce one warning event with `status=failure`, `failedStage`, total elapsed time, and the
exception type. `lineItems` is the number submitted; `processedRows` remains `0` if processing fails
before HTML merge completes. Log values are single-line and length-limited. The timings use
`System.nanoTime()` so elapsed measurements are not affected by wall-clock corrections.

## Demonstration

Start the application, then make three to five warm-up requests before recording results:

```bash
curl -sS \
  -D performance-headers.txt \
  -H "Content-Type: application/json" \
  --data-binary @src/main/resources/samples/jaquar-quotation-product-detailed.sample.json \
  http://localhost:8080/api/pdf/generate/jaquar-quotation-product-detailed \
  -o quotation.pdf
```

Show the client:

1. `performance-headers.txt` for request ID and phase timing.
2. The single `performance.quotation` server log line with `lineItems=100`.
3. The generated PDF and the matching file beneath `output/quotations`.
4. Several consecutive calls to distinguish first-call warm-up from steady-state behavior.

Use identical payloads, hardware, JVM settings, warm-up count, and iteration count when comparing
the HTML renderer with the JasperReports backup. Compare median and slowest observed time as well as
PDF size. The backup controller emits `engine=jasperreports`, but currently measures only total
controller-to-service time; it does not split template load, fill, and export phases.

## Positives to present

- One request ID connects the API response, performance log, and stored quotation.
- Phase timings reveal whether template access, merge work, rendering, or disk storage dominates.
- Row count and PDF size put latency in context; a 100-row result is not compared with a
  one-row result as if they were equivalent.
- The active HTML approach does not compile report definitions during generation. Templates remain
  editable HTML and CSS, and the same merge result drives browser preview and PDF output.
- The API remains synchronous and backward compatible: callers still receive the PDF immediately
  while the server retains the same bytes in the output hierarchy.

## Limits and negatives

- A controller timer is useful diagnostic evidence, not a statistically valid benchmark by itself.
- The request body, merged HTML, and PDF are held in memory during a synchronous request. Large or
  concurrent quotations increase heap pressure and request latency.
- Local filesystem persistence is simple for a PoC but does not provide shared storage, retention,
  replication, or cross-instance access.
- Remote image latency and cache state can materially change results. Cold and warm runs must be
  reported separately.
- JVM class loading, font initialization, JIT compilation, garbage collection, and OS file cache
  make the first request unrepresentative of steady state.
- Log lines do not calculate throughput, concurrency limits, percentiles, or service-level targets.
- The JasperReports backup only exposes total time, so phase-by-phase comparisons would currently be
  unfair.

## Future measurements and architecture

1. Add Micrometer timers and counters for success/failure, phase duration, line-item bands, output
   size, and image-cache results; export them through Actuator to Prometheus/Grafana.
2. Run repeatable load tests at 1, 100, 500, and 1,000 rows with concurrency levels representative
   of the customer, reporting throughput plus p50, p95, and p99 latency.
3. Move long-running generation to an asynchronous job API with status polling or callbacks.
4. Replace local files with versioned object storage and define retention and access policies.
5. Add the planned email workflow after persistence, with delivery state tied to the generation ID.
6. Instrument Jasper template load/compile, report fill, and export separately before presenting an
   engine performance comparison as a conclusion.
