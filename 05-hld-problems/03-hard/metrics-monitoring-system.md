> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design a metrics monitoring system (Prometheus + Grafana) — time-series metric collection, storage, query, and alerting for distributed service observability.
>
> **Key design decisions:**
> - Collection: pull model (Prometheus scrapes /metrics endpoint every 15s) vs push model (StatsD, Datadog Agent push to collector); pull easier to discover what's down
> - Metric types: Counter (monotonically increasing, e.g. requests_total), Gauge (can decrease, e.g. memory_usage), Histogram (latency buckets), Summary
> - Time-series storage: custom columnar format; data points sorted by (metric_name, labels, timestamp); compressed with delta + gorilla encoding
> - Cardinality: high-cardinality labels (user_id, request_id) explode storage — enforce label cardinality limits; only low-cardinality labels (env, service, region)
> - Query: PromQL — rate(requests_total[5m]) → compute per-second rate over 5 min; histogram_quantile(0.99, ...) for p99 latency
> - Alerting: alert rules evaluated every 15s against time-series DB; fire when condition holds for >2 min (avoid flapping); route via Alertmanager to PagerDuty/Slack
> - Long-term storage: Thanos or Cortex for multi-region aggregation + object storage retention (S3); Prometheus local storage limited to ~15 days
>
> **Key takeaway:** Cardinality control is the primary operational challenge — unbounded label values (user_id, trace_id) make the time-series DB explode in memory; enforce it at ingestion.

---
module: 05-hld-problems
topic: Hard
status: unread
tags: [05-hld-problems, system-design, hard, monitoring, metrics, prometheus, time-series, alerting]
---
# Design a Metrics Monitoring System (Prometheus + Grafana)

> **Difficulty**: Hard | **Asked at**: Google, Datadog, Netflix, Uber

---

## Problem Statement

Design a metrics monitoring and alerting system. Services emit metrics (counters, gauges, histograms). The system collects and stores metrics at second-level granularity, provides a query language for dashboards, and alerts on-call engineers when metrics breach thresholds.

---

## Functional Requirements

1. **Metric ingestion**: Services push or expose metrics (time series: metric_name + labels + value + timestamp)
2. **Storage**: Store metrics at 10-second resolution for 2 weeks; downsampled to 1-minute for 1 year
3. **Querying**: PromQL-style queries: aggregation (sum, avg, rate), label filtering
4. **Dashboards**: Visualize metric series over time (Grafana integration)
5. **Alerting**: Evaluate alert rules every 30 seconds; notify via PagerDuty/email/Slack on breach
6. **Anomaly detection**: Detect unusual metric values compared to historical baselines

---

## Non-Functional Requirements

- **Scale**: 10,000 services × 500 metrics each = 5M unique time series; 500K data points/sec
- **Query latency**: Dashboard queries < 5s for 24-hour range; < 30s for 30-day range
- **Availability**: 99.99% — if monitoring is down, engineers are flying blind
- **Storage efficiency**: 5M series × 1 point/10s × 2 weeks = 86B data points; must compress efficiently
- **Alert latency**: Alert fires within 60 seconds of threshold breach

---

## Core Entities

| Entity | Key Fields |
|--------|-----------|
| `Metric` | metric_name, labels (key=value map), data_type (counter/gauge/histogram) |
| `Sample` | metric_id, timestamp, value (float64) |
| `AlertRule` | rule_id, name, query, threshold, operator, severity, for_duration, notification_channels |
| `Alert` | alert_id, rule_id, status (pending/firing/resolved), started_at, labels |
| `Dashboard` | dashboard_id, panels[{ title, query, visualization_type }] |

---

## API Design

```http
# Ingestion (Prometheus scrape model)
GET /metrics  (on each service)
Response: text/plain (Prometheus exposition format)
  # HELP http_requests_total Total HTTP requests
  # TYPE http_requests_total counter
  http_requests_total{method="GET",status="200"} 12345 1735689600000

# Push model (for short-lived jobs)
POST /api/v1/push
Body: { "metric": "batch_job_duration_seconds", "labels": {"job": "etl"}, "value": 42.3 }

# Query
GET /api/v1/query_range?query=rate(http_requests_total[5m])&start=2026-06-29T00:00:00Z&end=2026-06-29T01:00:00Z&step=60s
Response 200: { "data": { "resultType": "matrix", "result": [{ "metric": {...}, "values": [[timestamp, value]] }] } }

# Alert rules
POST /api/v1/rules
Body: { "name": "high_error_rate", "query": "rate(errors_total[5m]) > 0.05", "for": "2m", "severity": "critical" }
```

---

## High-Level Design

```
Services (10,000 instances)
  │ Expose /metrics endpoint (Prometheus format)
  │ OR push to Push Gateway (for batch jobs)
  ▼
Scrape Manager (Prometheus-style)
  │ Poll each service /metrics every 15s
  │ Parse exposition format → stream to ingestion pipeline
  ▼
Kafka: metric-samples (partitioned by metric_name hash)
  │
  ▼
Storage Writers
  │ Batch write to TSDB (time-series DB)
  │ Downsampling: 10s → 1min (Flink rolling window)
  ▼
TSDB (Prometheus TSDB or VictoriaMetrics or Thanos)
  │ Hot tier: 2 weeks at 10s resolution (local SSD)
  │ Cold tier: 1 year at 1-min resolution (S3 via Thanos)
  ▼
Query Engine
  │ Parse PromQL → translate to TSDB scans
  │ Grafana calls query engine for dashboard panels
  ▼
Alert Manager
  │ Evaluate alert rules every 30s (query engine)
  │ State: pending → firing → resolved
  │ Route: PagerDuty (critical), Slack (warning), email (info)
```

---

## Deep Dive 1: Time-Series Storage and Compression

**Problem**: 500K data points/sec sustained for 2 weeks = 86B data points. At 16 bytes each (timestamp + float64), that's 1.4 TB uncompressed. How do you store this efficiently?

**Prometheus TSDB compression (Gorilla encoding)**:

**Delta-of-delta encoding for timestamps**:
```
Raw timestamps: 1735689600, 1735689610, 1735689620, 1735689630 (10s apart)
Deltas: 10, 10, 10, 10
Delta-of-deltas: 10, 0, 0, 0  → encode as: initial delta=10, then 0s
Compressed: ~1-2 bits per timestamp instead of 8 bytes (64-bit)
```

**XOR compression for float values**:
```
Raw values: 42.0, 42.1, 41.9, 42.2 (similar floating-point values)
XOR of consecutive values: small number of differing bits
Encode: leading zeros count + significant XOR bits
Result: ~3.5 bytes per sample instead of 8 bytes
```

**Combined**: Gorilla encoding achieves ~1.37 bytes per sample on average, reducing 1.4 TB to ~120 GB for 2 weeks of data. With additional ZSTD compression of chunk files: ~60-80 GB.

**Chunk files**: TSDB stores data in 2-hour chunks per series, in a columnar format (all timestamps together, all values together). Each chunk is written atomically and immutable after sealing. Enables efficient range scans.

> 🎯 **Staff signal:** The compression ratio isn't the point — the point is *why* Gorilla-style encoding works, and it works because monitoring data has two properties a general DB can't assume: timestamps arrive at a near-fixed scrape interval (delta-of-delta collapses to zero bits) and adjacent values change slowly (XOR leaves mostly zero bits). That domain assumption is also the design constraint: the columnar 2-hour immutable chunk is what makes it possible, because you can't delta-encode a column you're randomly updating. So the storage engine is co-designed with the write pattern — append-only, time-ordered, sealed-then-immutable — and that immutability is what lets old chunks ship to S3/Thanos and be memory-mapped instead of loaded. The rejected alternative, a row-store with per-point timestamps, is 5–8× larger and can't be mmap'd for range scans. E5 says "compress the time series"; E6 says "delta-of-delta + XOR exploit fixed-interval, slow-changing data, and they only work because chunks are columnar and immutable — which is also what enables cheap tiering to object storage."

---

## Deep Dive 2: Query Processing at Scale

**Problem**: A Grafana dashboard runs the query `rate(http_requests_total{status="5xx"}[5m])` across 10,000 service instances for the past 24 hours. This requires reading thousands of series across billions of data points.

**PromQL evaluation**:
```
rate(metric[5m]) = (last_value - first_value) / 5m per series
```

1. **Series selection**: Scan TSDB index for all series matching `http_requests_total, status="5xx"`. The TSDB inverted index maps label values to series IDs. 10,000 matching series found in ~10ms.

2. **Chunk loading**: For a 24-hour range, each series has 12 chunks (2h each). 10,000 series × 12 chunks = 120,000 chunks to load. Parallelized across TSDB workers. Each chunk is ~1 KB → 120 MB total read.

3. **Evaluation**: For each time step (every 60s for a 24h range = 1,440 steps), compute rate for each series. Aggregate with `sum()` if needed.

**Thanos for long-range queries**: Historical data (> 2 weeks) lives in S3 (Thanos object store). Thanos Query dispatches queries to both local Prometheus (recent) and Thanos Store (historical), merges results. Long-range queries (30-day) are slower (30s acceptable for dashboard load).

**Recording rules**: Pre-compute expensive aggregations as new metrics:
```yaml
# Pre-compute sum of errors across all services every 30s
- record: job:errors_total:rate5m
  expr: sum by (job) (rate(errors_total[5m]))
```
Dashboard queries hit the pre-computed metric instead of re-aggregating 10,000 series each time.

> 🎯 **Staff signal:** The move that scales query is recognizing that dashboard queries are *known in advance* — a Grafana panel runs the same `sum by (job) (rate(...))` every 30 seconds forever — so the expensive aggregation should be paid once at write time (recording rules), not re-derived on every refresh across thousands of viewers. That inverts the cost model from O(queries × series) to O(series), and it's the same materialized-view logic as any read-heavy system. The second-order insight is the series *index*: `rate(...[5m])` is only fast because the inverted index resolves label matchers to series IDs before touching any samples, so cardinality (Deep Dive 4) is what actually governs query latency — a high-cardinality metric slows every query that touches it, not just storage. E5 says "cache the query results"; E6 says "recording rules materialize the *predictable* aggregations at ingest so N dashboard viewers cost the same as one, and I watch cardinality because the index scan, not the sample read, is the query's floor."

---

## Deep Dive 3: Alerting and Notification Routing

**Problem**: A metric breaches a threshold. The alert must fire, notify the right on-call engineer (not just "someone"), and suppress duplicate notifications during a long outage.

**Alert state machine**:
```
INACTIVE → PENDING (threshold breached) → FIRING (after "for" duration) → RESOLVED
```

The `for: 2m` clause prevents flapping — a metric must breach the threshold continuously for 2 minutes before the alert fires. This suppresses noisy alerts from brief spikes.

**Alert evaluation** (Alert Manager):
```python
def evaluate_rule(rule):
    result = query_engine.instant_query(rule.query)
    if result.value > rule.threshold:
        if rule.state == INACTIVE:
            rule.state = PENDING
            rule.pending_since = now()
        elif rule.state == PENDING and (now() - rule.pending_since) > rule.for_duration:
            rule.state = FIRING
            notify(rule)
    else:
        if rule.state in (PENDING, FIRING):
            rule.state = RESOLVED
            notify_resolved(rule)
```

**Notification routing**: Alert labels (severity, team, service) route to different channels:
```yaml
routes:
  - match: { severity: critical }
    receiver: pagerduty-oncall
  - match: { severity: warning }
    receiver: slack-alerts
  - default:
    receiver: email-ops
```

**Inhibition rules**: If a datacenter goes down (high-severity alert), suppress all lower-severity alerts from that datacenter. Prevents alert storms.

**Silencing**: On-call engineer creates a silence (start_time, end_time, label matchers) during planned maintenance. Matching alerts don't notify.

> 🎯 **Staff signal:** A junior alerting system fires on a threshold; a senior one is engineered around the fact that *the failure that trips your alerts also trips a hundred correlated ones*, and a pager that fires 100 times at 3am is worse than no pager. So the real design is three noise-suppression layers, each solving a distinct failure mode: `for: 2m` kills *temporal* noise (a 5s spike isn't an incident), inhibition kills *causal* noise (if the datacenter is down, suppress every downstream alert it caused — one root-cause page, not the cascade), and silencing kills *known* noise (planned maintenance). The subtle correctness point is that inhibition must be evaluated on alert *state*, not raw metrics, so a resolved root cause automatically un-suppresses its dependents. E5 says "fire when the metric crosses the threshold, dedupe notifications"; E6 says "flapping, alert storms, and maintenance are three different noise sources — `for`, inhibition, and silencing each target one — because on-call trust dies the first time the system pages 50× for a single outage."

---

## Deep Dive 4: Cardinality Control with Sketches

**Problem**: The non-functional requirements assume 5M active series. Cardinality explosion is the single most common way a metrics system dies: one team ships `http_requests_total{user_id="..."}` and 5M series becomes 500M overnight. Every series costs an inverted-index entry plus an open chunk in memory, so the TSDB OOMs — and it does so *during* the incident the metrics were supposed to explain.

Detecting this by exactly counting distinct series per metric name defeats the purpose: the tracking structure would itself hold the exploding key set.

**HyperLogLog per metric name**: On ingest, hash the full label-set of each incoming sample into an HLL keyed by metric name.

```
PFADD cardinality:{metric_name}:{hour} {labelset_hash}
PFCOUNT cardinality:http_requests_total:2026-07-22T14
```

12 KB per metric name per hour, regardless of whether that metric has 100 or 100M distinct label combinations. Alert when `PFCOUNT` crosses a budget (say 100K series for one metric name), and the offending metric is identified *before* it exhausts memory. Because HLLs merge losslessly, hourly counters roll up into daily cardinality trends for capacity planning without retaining raw label sets.

**Count-Min Sketch for the top talkers**: HLL says *how many* series a metric has, not *which* labels are responsible. A CMS keyed on individual label values (`user_id=...`, `pod=...`) surfaces the highest-frequency offenders in ~40 KB:

```
w = 2000, d = 5  → estimate(label_value) = min across d rows
```

CMS never undercounts, so a genuine top talker cannot hide; false positives are harmless because the candidate list is then verified exactly against the index. This turns "cardinality is high" into "`user_id` on `http_requests_total` is responsible" — the difference between an alert and an actionable one.

**Enforcement**: at the ingest gateway, reject or drop samples for a metric name past its series budget, emit `metrics_dropped_total{reason="cardinality"}`, and notify the owning team. Dropping one team's runaway metric is strictly better than losing the whole TSDB — a bounded, attributable failure instead of a total one.

**The line to say**: cardinality limits are only enforceable if you can measure cardinality cheaply, and you cannot measure it exactly without reproducing the very explosion you're guarding against. HLL for the count, CMS for the culprit.

> 🎯 **Staff signal:** The reason to reach for probabilistic sketches here — rather than treating them as a party trick — is that the guard against cardinality explosion cannot itself have unbounded cost, or it becomes the very failure it was meant to prevent. An exact distinct-count keeps the full label set in memory; that's the explosion. HLL gives the count in fixed 12 KB regardless of true cardinality, and CMS gives the top offender in fixed 40 KB, both with bounded, *safe-direction* error (CMS never undercounts, so a real top talker can't hide). This is the general staff pattern: when the monitoring must survive the pathological case, trade exactness for a hard space bound whose error direction you can prove is harmless. E5 says "count distinct series per metric and alert on it"; E6 says "exact counting reproduces the explosion — HLL for a constant-space count, CMS for a constant-space culprit, and I picked CMS specifically because its one-sided error can't let a real offender slip through."

---

## Interviewer Questions by Level

**Junior**:
- What is a metric? What's the difference between a counter and a gauge?
- What is the Prometheus scrape model? How does it collect metrics from services?
- What is an alert threshold? What does "for: 2m" in an alert rule mean?

**Mid-level**:
- How does Prometheus's Gorilla encoding compress time series data? What's the compression ratio?
- What is a recording rule? Why would you use one?
- How does an alert state machine work? What is the difference between PENDING and FIRING?

**Senior**:
- Design the TSDB (time-series database) storage engine — from ingestion to disk layout to query.
- How do you scale Prometheus beyond a single machine? (Thanos, Cortex, VictoriaMetrics)
- Design an anomaly detection system that flags unusual metric values without manually configured thresholds.
- A monitoring outage occurs. How do you design the monitoring system to be self-monitoring — alert if Prometheus itself stops scraping?

---

## Back-of-Envelope Estimation

**Scale inputs (given in NFRs):**
- 1M services; 100 metrics each; 10B data points/day; 1-second granularity; < 30s alert latency

**Metric ingest rate:**
- 1M services × 100 metrics × 1 data point/sec = **100M data points/sec** at 1-second granularity
- Each data point: `{metric_name, tags, timestamp, value}` ≈ 100 bytes
- Raw ingest: 100M × 100 bytes = **~10 GB/sec** — massive; must aggregate before storage

**Aggregation is the key design lever:**
- Storing raw 1-second data: 100M points/sec × 86,400 sec/day × 100 bytes = **~864 PB/day** — impossible
- Aggregate at ingest to 10-second buckets: 100M/sec → 10M points/10-sec bucket (10× reduction in write rate)
- Aggregate further to 1-minute buckets for long-term storage: 100M/sec → 1.67M points/min (60× reduction)
- After 24 hours: roll up to 1-hour buckets; after 7 days: roll up to 1-day buckets

**Storage after aggregation:**
- 1-minute resolution, 30-day retention: 1.67M points/min × 525,600 min/year = **~880B points/year**
- At 16 bytes per point (timestamp 8B + value 8B, tag stored separately): **~14 TB/year**
- With compression (time-series data compresses 10× with Gorilla/delta-delta encoding): **~1.4 TB/year** — fits on a single Prometheus-scale time-series DB or small VictoriaMetrics cluster

**Alert evaluation:**
- < 30 seconds alert latency: alert rules evaluated against latest data every 10 seconds
- 10,000 alert rules (1M services × 0.01 rules/service avg) evaluated every 10 sec
- Each rule: check the last 2 minutes of data for a metric → 12 data points to evaluate
- 10,000 rules × 12 points = **120,000 data reads** per 10-second evaluation cycle = **12,000 reads/sec**
- In-memory time-series (Prometheus): sub-millisecond per read → 10-second evaluation cycle takes **~0.12 seconds** — well within budget

**Ingest pipeline:**
- 100M data points/sec arrives; must reduce before hitting storage
- Kafka: topic `metrics-raw` with 100 partitions at 1M points/sec per partition = 1 GB/sec per partition — too high
- Better: 1000 partitions, 100M/sec ÷ 1000 = 100K points/sec/partition = 10 MB/sec per partition — workable
- Stream processing (Flink): 10-second tumbling windows → compute min/max/avg/p99 per metric → write 10M aggregated points per 10-sec window (10× reduction)

**Architecture decisions driven by these numbers:**
- **Multi-resolution storage with automatic rollup**: Storing raw 1-second data is 864 PB/day — impossible. The key insight: no one queries 1-second granularity for data older than 1 hour. Prometheus/Thanos/VictoriaMetrics use multi-resolution compaction: keep 1-second for 1 hour, 10-second for 24 hours, 1-minute for 30 days, 1-hour for 1 year. This reduces storage from 864 PB/day to **~1.4 TB/year compressed** — a 224,000× reduction.
- **Push-based collection with Kafka buffering, not pull-based**: Pull-based (Prometheus scraping 1M endpoints/sec) requires the monitoring system to maintain 1M TCP connections and poll each every second. At 1M services, that's 1M concurrent scrape operations — connection overhead alone is prohibitive. Push-based (services emit to Kafka) decouples the collection rate from scraper capacity. Kafka absorbs bursts; Flink aggregators process at their own rate.
- **In-memory recent data for alert evaluation**: The < 30s alert latency requirement means alert queries must be answered in ~1ms (10K rules in 10 seconds = 1ms/rule budget). Hitting a disk-backed time-series DB for each rule evaluation adds 5–50ms I/O per read. Keeping the last 30 minutes of data in RAM (100M points × 30 min × 100 bytes uncompressed ≈ 300 GB — feasible with a dedicated in-memory store) enables sub-millisecond alert evaluation.

---

## Related

**Concepts used in this design**

- [Observability](../../04-advanced-topics/02-system-reliability/01-observability.md)
- [Telemetry & Tracing](../../04-advanced-topics/02-system-reliability/03-telemetry-tracing.md)
- [Stream Processing](../../04-advanced-topics/01-distributed-architecture/05-stream-processing.md)
- [Cassandra Internals](../../04-advanced-topics/03-internals/05-cassandra-internals.md)
- [Stream vs Batch](../../04-advanced-topics/03-internals/12-stream-vs-batch.md)

**Practice next**

- [Ad Click Aggregator](../03-hard/ad-click-aggregator.md)
- [Distributed Job Scheduler](../03-hard/distributed-job-scheduler.md)

The click aggregator shares the approximate-counting toolkit.

**Frameworks**: [HLD Template](../../07-interview-templates/01-frameworks/01-hld-template.md) · [Capacity Estimation](../../07-interview-templates/02-cheat-sheets/02-capacity-estimation.md) · [Trade-offs Cheat Sheet](../../07-interview-templates/02-cheat-sheets/01-trade-offs-cheat-sheet.md)
