> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** How to build a system you can actually debug at 3 AM — the three observability pillars in depth, plus SLO/SLI/error budgets and the RED/USE methodologies that staff engineers apply to on-call incidents.
>
> **Key topics:**
> - **Three pillars:** metrics, logs, distributed traces — what each tells you and when to use each
> - **RED method** — Rate, Errors, Duration — for service-level health
> - **USE method** — Utilization, Saturation, Errors — for resource-level health
> - **SLI / SLO / SLA / Error Budget** — the contract that governs when to ship vs when to fix
> - **Distributed tracing** — how Trace IDs flow across services, sampling strategies
> - **Structured logging** — why `grep` on plain text doesn't scale
> - **Alerting** — what makes a good alert vs noise
>
> **Key takeaway:** Observability is not a tool you bolt on after launch. It's a design constraint. Every RPC should emit a span. Every background job should emit a metric. Every error should emit a structured log with a correlation ID.

---
module: 04-advanced-topics
status: unread
tags: [04-advanced-topics, system-design, reliability]
---
# Observability and Reliability

---

## The Three Pillars

When a distributed system breaks, three questions must be answerable in minutes:
1. **What** is broken? (Metrics)
2. **Where** in the call chain did it break? (Traces)
3. **Why** did it break? (Logs)

These map to the three observability pillars: **Metrics, Traces, Logs**.

---

## Pillar 1: Metrics

Metrics are numeric time-series: a single number sampled repeatedly over time.

```
http_request_total{service="payments", status="500"} → 42 (at T=14:02)
http_request_total{service="payments", status="500"} → 87 (at T=14:03)
```

### The RED Method (for services)

For every **service** you own, instrument these three metrics:

| Letter | Metric | Example |
|---|---|---|
| **R**ate | Requests per second | `http_requests_total / 60s` |
| **E**rrors | Error rate (%) | `5xx_count / total_count * 100` |
| **D**uration | Latency distribution (p50/p99/p999) | `http_request_duration_seconds` |

Why p99, not mean? The mean hides outliers. If 99% of requests take 10ms but 1% take 10 seconds, the mean might be 110ms — looks fine, but 1% of users see a broken experience. At 10K req/sec, that's 100 users/sec with a broken experience.

### The USE Method (for resources)

For every **resource** (CPU, disk, network, thread pool, connection pool), instrument:

| Letter | Metric | Example |
|---|---|---|
| **U**tilization | % of time the resource is busy | CPU: 85% |
| **S**aturation | Work queued waiting for the resource | Run queue length > 0 |
| **E**rrors | Failed operations on the resource | Disk write errors |

A resource at 100% utilization is saturated — requests queue up and latency spikes. Saturation is the leading indicator of an impending outage; utilization is the lagging one.

### Metric Types

| Type | Description | Example |
|---|---|---|
| Counter | Monotonically increasing | `requests_total` |
| Gauge | Point-in-time value (can go up or down) | `active_connections`, `memory_used_bytes` |
| Histogram | Distribution of values in buckets | `request_duration_seconds_bucket` |
| Summary | Pre-computed quantiles (client-side) | `request_duration_p99` |

**Use histograms over summaries** when possible — histograms can be aggregated across instances; pre-computed summaries can't.

### Tooling

- **Collection:** Prometheus (pull-based), Datadog, Cloudwatch
- **Visualization:** Grafana (queries Prometheus), Datadog dashboards
- **Alerting:** PagerDuty, OpsGenie (receive alerts from Prometheus Alertmanager or Datadog)

---

## Pillar 2: Distributed Traces

In a monolith, a request's entire execution is on one machine — a stack trace suffices. In microservices, one user request fans out across 10+ services. A trace stitches that journey together.

### How Tracing Works

A **Trace** represents one end-to-end request. It contains a tree of **Spans** — one per service call. Every span records:
- Service name
- Operation name
- Start time + duration
- Status (OK / error)
- Tags/attributes (user_id, http.status_code, db.query)
- Parent span ID (to reconstruct the tree)

```
Trace ID: abc123
│
├─ Span: API Gateway          (0ms → 5ms)
│   ├─ Span: Auth Service     (1ms → 3ms)    ← fast
│   └─ Span: Payment Service  (3ms → 4500ms) ← slow!
│       └─ Span: DB Query     (3ms → 4490ms) ← root cause
```

The trace immediately shows the Payment Service is slow, and the DB query inside it is the culprit.

### Propagation

The first service generates a `Trace-ID` (e.g., a UUID) and passes it downstream as an HTTP header:

```
X-B3-TraceId: abc123def456
X-B3-SpanId: 789xyz
X-B3-ParentSpanId: 456abc
```

Every service extracts the trace context, creates a child span, and passes the updated context to the next hop. All spans with the same `Trace-ID` are assembled into the trace by the tracing backend.

**Standards:** OpenTelemetry (W3C `traceparent` header) is the current standard, replacing Zipkin's B3 and Jaeger's formats.

### Sampling

Tracing 100% of traffic is expensive in storage and processing. Most systems use **head-based sampling**: decide at the first service whether to trace this request (e.g., 1% of traffic). The decision propagates downstream.

**Tail-based sampling** (Jaeger, Honeycomb): collect all spans, then decide which complete traces to store — allowing you to always retain traces for errors or slow requests, even if they're rare. More useful but more complex.

**Rule of thumb:** 1% sampling for P50 traffic patterns, always-on for errors and requests >P99 latency.

### Tooling

- **Instrumentation:** OpenTelemetry SDK (language-native)
- **Collection/Storage:** Jaeger, Zipkin, Honeycomb, Tempo
- **Integrated:** Datadog APM, AWS X-Ray, Google Cloud Trace

---

## Pillar 3: Structured Logs

Logs are immutable records of discrete events. The key upgrade from naive logging is **structure**: instead of free-form text, emit JSON so logs are machine-queryable.

```python
# Unstructured (don't do this at scale)
logger.error(f"Payment failed for user {user_id}: {error}")

# Structured (queryable, correlatable)
logger.error("payment_failed", extra={
    "user_id": user_id,
    "payment_id": payment_id,
    "error_code": error.code,
    "trace_id": current_trace_id(),   # ← key: correlates to traces
    "duration_ms": elapsed,
})
```

**Always include:**
- `trace_id` / `correlation_id` — links log to the distributed trace
- `service` and `version` — which deployment produced this log
- `level` (ERROR/WARN/INFO/DEBUG) — filter signal from noise
- Timestamps in UTC ISO-8601

**Log levels in production:**
- ERROR: something is broken; requires attention
- WARN: degraded state; may become an error
- INFO: significant business events (order placed, payment completed)
- DEBUG: disabled in production; too verbose

### Log Aggregation

In a cluster of 100 services, logs ship to a central store. Pipeline:

```
Service → Log shipper (Fluentd/Filebeat) → Aggregator (Kafka) → Storage (Elasticsearch/Loki) → UI (Kibana/Grafana)
```

**Cardinality trap:** Never put high-cardinality values (user_id, order_id) in metric labels — it creates millions of time series and OOMs Prometheus. Put high-cardinality data in logs and traces, not metrics.

---

## SLI, SLO, SLA, Error Budget

This is the framework that governs reliability decisions at Staff level.

### Definitions

| Term | Definition | Example |
|---|---|---|
| **SLI** (Service Level Indicator) | A quantitative measure of service behavior | % of requests with latency < 200ms |
| **SLO** (Service Level Objective) | A target value for an SLI | SLI ≥ 99.9% over 30 days |
| **SLA** (Service Level Agreement) | A contractual commitment to customers, with penalties | 99.9% uptime or 10% credit |
| **Error Budget** | 1 − SLO = how much failure is allowed | 0.1% = 43.2 min/month downtime |

### How to Set SLOs

Start with SLIs that reflect user happiness. For most services, the most impactful SLIs are:
1. **Availability:** `successful_requests / total_requests`
2. **Latency:** `requests_under_threshold / total_requests` (e.g., threshold = 300ms)
3. **Correctness:** `valid_responses / total_responses` (for data pipelines)

Choose thresholds by measuring actual behavior and setting targets just above current p50–p75 performance — not aspirational numbers. A 99.99% SLO on a service currently running at 99.5% is a lie.

### Error Budget and the Ship vs. Fix Decision

An error budget is the operationalization of reliability: it converts a fuzzy policy ("be reliable") into a concrete number ("we have 43 minutes of downtime budget this month").

**When error budget is healthy:** Ship features aggressively. The budget absorbs incident risk.

**When error budget is nearly exhausted:** Freeze feature launches. Focus on reliability work (postmortems, infrastructure hardening, test coverage).

```
Monthly error budget for 99.9% SLO = 0.1% × 43,200 min = 43.2 min

Incident on Day 10: 15 min downtime → budget remaining: 28.2 min
Incident on Day 15: 30 min downtime → budget exhausted
→ No feature deployments until next month
```

### Multi-Window, Multi-Burn-Rate Alerts

A simple threshold alert ("alert if error rate > 1%") fires too late (after the budget is gone) or too often (on momentary spikes). 

**Burn rate alerts** measure how fast you're consuming the error budget:

| Burn rate | Meaning | Alert severity |
|---|---|---|
| 1× | Consuming budget at exactly the allowed rate | No alert |
| 5× | 5× budget consumption — exhausted in 6 days | Ticket |
| 14.4× | Budget exhausted in 2 hours | Page on-call immediately |

Use two windows (short + long) to reduce false positives:
- Long window catches sustained degradation.
- Short window catches sudden spikes.
- Alert only if both windows exceed the burn rate threshold.

---

## Alerting Principles

A good alert has three properties:

1. **Actionable:** If you can't do anything about it at 3 AM, don't page for it.
2. **Symptomatic, not causal:** Alert on "users are seeing errors" (SLO breach), not "CPU is high" (may be fine).
3. **Non-noisy:** Alert fatigue kills incident response. If the on-call ignores alerts, your observability is broken.

**Anti-patterns:**
- Alerting on utilization thresholds (CPU > 80%) instead of user-facing symptoms
- Alerting on every exception (most exceptions are handled gracefully)
- Using the same alert threshold for 3 AM and 3 PM (traffic patterns differ)

---

## Designing for Observability

Observability is a first-class design concern, not an afterthought.

**At service design time:**
- Every RPC / API call emits a span with status + latency.
- Every background job emits job start/end metrics + error count.
- Every external dependency call (DB, cache, downstream service) emits a span.
- Every error emits a structured log with trace_id.

**At infrastructure design time:**
- Ship logs from all instances to a central aggregator (Elasticsearch, Loki).
- Expose Prometheus `/metrics` endpoint from every service.
- Run a distributed tracing collector (Jaeger, Tempo) sidecar or as a cluster daemon.
- Set SLOs before launch; build dashboards around them.

---

## Interview Questions to Practice

1. **"Walk me through how you'd debug a sudden spike in 500 errors."**
   *Open the metrics dashboard (RED method): which service has elevated error rate? Open the distributed trace for a failing request: which span returned the error? Open the structured logs for that service during the incident window: what's the error message and stack trace? Resolution usually comes from the logs; traces tell you where to look.*

2. **"What's the difference between an SLO and an SLA?"**
   *An SLO is an internal engineering target (99.9% availability). An SLA is a customer-facing contractual commitment, usually set lower than the SLO (99.5%), so the team has a buffer before compensating customers. SLAs have financial penalties; SLOs drive internal team behavior.*

3. **"Your team ships a feature and your error budget burns 20% in one day. What do you do?"**
   *Immediately halt feature launches until the incident is understood. Run a postmortem — was it a rollout issue, a bad config, or a capacity problem? If the feature caused it, roll back or fix it. If budget is now below 50% for the month, shift team focus from features to reliability (SLO-driven prioritization). Resume shipping once the budget is healthy and the root cause is addressed.*

4. **"How does distributed tracing work when a request hits 5 microservices?"**
   *Service 1 (API gateway) generates a Trace ID and Span ID. It passes them in the outgoing HTTP headers (X-B3-TraceId, or W3C traceparent). Each downstream service extracts the trace context, creates a child span with its own Span ID and the parent's Span ID as parent, performs its work, and emits the span to the tracing collector. The collector assembles all spans with the same Trace ID into a tree. You see the full waterfall of calls with their latencies.*

5. **"What's the cardinality trap in metrics?"**
   *Adding high-cardinality labels (user_id, order_id) to Prometheus metrics creates one time series per label value — potentially millions. Prometheus stores all active time series in memory; too many causes OOM and crashes. Rule: put high-cardinality data in logs and traces (designed for it), not metrics. Metric labels should only contain low-cardinality values: service name, status code, region, method.*
