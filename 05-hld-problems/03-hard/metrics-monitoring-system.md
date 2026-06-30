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
