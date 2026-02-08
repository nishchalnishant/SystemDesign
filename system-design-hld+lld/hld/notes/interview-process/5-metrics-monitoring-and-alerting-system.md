# #5 Metrics monitoring and alerting system

## Design: Metrics Monitoring & Alerting System

<br>

Below is a production-ready design for a Metrics Monitoring & Alerting System (think Prometheus + Cortex/Thanos + Grafana + Alertmanager at scale). It covers scope, FR/NFR, APIs, architecture, data model and retention, ingestion & sampling, query & dashboards, alerting lifecycle, scaling/back-of-the-envelope math, operational concerns, trade-offs and an interview-ready summary.

***

## 1 — Scope & goals

<br>

Provide a system to:

* Ingest time-series metrics from services (pull or push).
* Store and index metrics efficiently for short/medium/long-term queries.
* Serve dashboards, ad-hoc queries and SLO/SLA reporting.
* Evaluate alerts and route incidents to on-call teams with deduplication and suppression.
* Scale to tens/hundreds of thousands of hosts and millions of series, while controlling cardinality and cost.

<br>

Target use-cases: infrastructure metrics (CPU, mem), application metrics (requests/sec, error rate), service-level indicators (latency histograms), business KPIs.

***

## 2 — Functional requirements

* Metrics ingestion: accept labelled time-series metrics (gauge, counter, histogram, summary). Support pull (Prometheus) and push (Pushgateway / remote write).
* High-frequency scraping: support  scrape intervals from 1s to 1m.
* Durable storage: short-term hot storage for fast queries, long-term cold storage with downsampling.
* Query API: support PromQL-like queries (range and instant).
* Dashboards: render real-time visualizations (Grafana) with templating and sharing.
* Alerting: evaluate rules continuously (thresholds, anomaly, SLO burn rate), support grouping, dedupe, silences, escalations.
* SLO integration: compute error budgets & burn rates from metrics.
* Multi-tenancy & RBAC: namespace isolation and per-tenant quotas.
* Retention policies: configurable retention & downsampling per metric / tenant.
* Exports & integrations: webhooks, PagerDuty, Slack, Opsgenie, email.

***

## 3 — Non-functional requirements

* Latency: queries P95 < 200–500 ms for short-range, dashboard refresh < 1s for small panels.
* Availability: 99.95% for reads & writes during business hours. Graceful degradation acceptable for cold queries.
* Scalability: scale horizontally for ingestion, storage and query; support millions of series.
* Durability: no data loss for committed metrics; configurable replication.
* Cost-efficiency: downsampling & cold storage to control cost.
* Security: TLS, authN/authZ, encrypted-at-rest for sensitive data.
* Observability: internal metrics for ingestion rate, cardinality, query latency, alert eval times.

***

## 4 — High-level architecture

```
Metrics Producers (apps, infra, exporters)
        |
   [Pull: Prometheus scrapers]  [Push: remote_write]
        |                         |
   Prometheus (or sidecar) ------> Write API / Ingest Layer (gateway)
                                       |
                           Ingest Sharders / Rate-limiters
                                       |
                            Message Bus (Kafka / kinesis)  <-- optional
                                       |
                +-----------------------------------------------+
                |           Short-term TSDB cluster            |
                |  (memory+disk, high-cardinality, fast query) |
                +-----------------------------------------------+
                                       |
                Downsampler & Compactor (rollups) -> Cold object store (S3)
                                       |
               Query Layer (Distributed TSDB: Cortex/Thanos/M3DB)
                                       |
                 Grafana / Query API / SLO Service / Alert Manager
                                       |
                          Alertmanager / Notifier (PagerDuty, Slack)
```

Components explained:

* Scrapers / Sidecars: pull exporters or accept push via remote\_write.
* Ingest Gateway: shards writes (by tenant/label), enforces quotas, rejects high-cardinality bursts.
* Short-term TSDB: fast append-only local stores per ingester (e.g., Prometheus TSDB format) for recent data.
* Downsampler / Compactor: aggregates high-resolution to lower-resolution (1s → 1m → 5m) for older windows.
* Long-term storage: object store (S3/GCS) for compressed blocks/segments; partitioned by time & tenant.
* Query Layer: global query frontends, run distributed queries by merging results from short-term and long-term.
* Alerting Engine: evaluate rules (push or pull), manage silences & notification retries.

<br>

Common open-source building blocks: Prometheus + Prometheus remote\_write → Cortex/Thanos/M3DB backend + Grafana + Alertmanager.

***

## 5 — Data model & labels

* Each sample: {metric\_name, labels{service,instance,region,env,...}, timestamp, value}.
* Histograms stored as buckets & exemplars; counters need monotonic handling.
* Important: Labels drive cardinality. Limit free-form labels (e.g., user\_id, request\_id) — those belong in logs/traces.

<br>

Label hygiene best practices included in system (validation, drop rules).

***

## 6 — Ingestion & throttling strategy

* Sharding: hash by (tenant + metric\_name) or label subset to map writes to ingesters.
* Batching: clients should batch remote\_write to amortize overhead.
* Rate limits & quotas: per-tenant sample rate, series cardinality cap, label value length limits. Reject writes or sample/delay with backpressure.
* Spike protection: burst buffers + reject policy for sustained overload.

***

## 7 — Storage, retention & downsampling (with math example)

<br>

#### Example assumptions (to size the system)

* 10,000 hosts (nodes)
* Each host exposes 100 unique time-series
* Scrape interval: 10 seconds
* Average sample size (including labels & overhead): 512 bytes per sample

<br>

Compute samples/sec:

1. Hosts × series = 10,000 × 100 = 1,000,000 series total.
2. Each series scraped every 10s → samples/sec = 1,000,000 / 10 = 100,000 samples/sec.

<br>

Compute bytes/sec:

3\. bytes/sec = samples/sec × bytes\_per\_sample = 100,000 × 512 = 51,200,000 bytes/sec.

<br>

Per day:

4\. bytes/day = 51,200,000 × 86,400 =

* Step: 51,200,000 × 86,400 = 51.2e6 × 86,400 = 4,423,680,000,000 bytes/day.
* That equals ≈ 4.42368 TB/day (decimal TB).

<br>

30-day retention raw:

5\. 4.42368 TB/day × 30 = 132.7104 TB raw.

<br>

After compression/downsampling: assume 5× effective compression + aggressive downsampling for >7 days:

6\. Effective storage ≈ 132.7104 TB / 5 = ≈26.54208 TB for 30-day stored data.

<br>

Notes:

* Real systems often get 3–10× compression depending on metric churn.
* Histograms and exemplars cost more per sample.
* Use hot storage for recent 7–14 days, cold object store for older (downsampled) data.

***

## 8 — Querying & dashboards

* Query frontend: route queries to appropriate ingesters & long-term store; parallelize across time ranges and tenants.
* Caching: cache recent query results (per-panel) and shared subqueries.
* Query ops: time range split (short range served from hot store; long range stitched with downsampled blocks).
* Dashboard templating: Grafana integrated with metric query API.

<br>

Optimization: precompute rollups for heavy business metrics and SLO calculations.

***

## 9 — Alerting system design & lifecycle

<br>

Components:

* Rule definitions: recording rules (for derived metrics) and alerting rules (prometheus-style).
* Evaluation engine: scheduled evaluators that run rules at defined intervals and create or resolve alerts.
* Dedup & group: group incidents by labels (service, region), dedupe similar alerts.
* Silences & maintenance windows: suppress alerts via silences or maintenance mode.
* Routing & escalation: route by label-based receiver mappings; escalate if not acknowledged.
* Noise reduction:
  * Require condition to hold for N evaluation intervals (for flapping avoidance).
  * Use alert inhibitions (suppress less important alerts when a critical one is firing).
  * Use anomaly detection + baseline-based thresholds to reduce static threshold noise.
* Integration: PagerDuty, Opsgenie, Slack, email, webhooks.
* Audit & incident timeline: record alert history, acknowledgments, responders.

<br>

Alert maturity lifecycle:

1. Create rule with severity & runbook link.
2. Test in staging for X hours.
3. Promote to prod; monitor false-positive rate.
4. Iterate thresholds & add suppression where needed.

***

## 10 — SLOs, SLIs & burn rate integration

* Provide an SLO service that calculates error rates/latency from metrics and computes burn rate. Alerts for:
  * High error budget burn rate
  * SLA breaches
* Use windowed aggregation and burn-rate-based alerts (e.g., 14-day & 1-day windows).

***

## 11 — Multi-tenancy, security & access control

* Tenant isolation: tagged ingest streams and storage partitions by tenant; enforce quotas and per-tenant resource accounting.
* AuthN/AuthZ: OAuth2 / mTLS for write & read access. Grafana with RBAC for dashboards.
* Data encryption: TLS in transit; encrypt sensitive labels/metrics in storage if needed.
* Audit logs: who created rule, who silenced, who viewed dashboard.

***

## 12 — Reliability, failure modes & mitigation

* Hot ingester failure: replicate data to multiple ingesters; use consistent hashing + replication factor for series.
* Controller failure: use consensus-backed metadata (etcd) and multiple replicas.
* Object store outage: serve recent blocks from local cache; read-only mode for dashboards.
* Partition hotspots/cardinality spikes: reject or downsample high-cardinality series; autoscale ingestion.
* Alert storms: implement alert inhibition & burst protection; route to escalation policies.

***

## 13 — Operational concerns & observability of the monitoring system

* Monitor internal metrics: ingestion rate, samples/sec, series count, active series per tenant, disk usage, compaction duration, query latency, alert evaluation latency.
* Run synthetic monitoring and end-to-end tests (scrape exporters in staging, ensure metrics flow to alerts).
* Capacity planning based on samples/sec and retention (see math).
* Regular housekeeping: label hygiene audits, alert quality reviews.

***

## 14 — Trade-offs & choices

* Prometheus-only (single server): simple, great for small clusters, limited scale & HA.
* Prometheus + remote\_write → Cortex/Thanos/M3: better scalability, multi-tenant, long-term retention.
* Push vs Pull: Pull (Prometheus) simplifies target discovery & scraping; push required for ephemeral jobs/k8s short-lived tasks.
* High cardinality: best solved by design & constraints; allows cost control.
* Complex alerts (ML/anomaly): reduce noise but add complexity and false negatives risk.

***

## 15 — Interview talking points / 10-minute pitch

1. Clarify scale & retention targets (samples/sec, retention days, cardinality limits).
2. Propose architecture: Prometheus scrapers + remote\_write → distributed TSDB (Cortex/Thanos) + Grafana + Alertmanager.
3. Explain ingestion/sharding and cardinality protection (quotas, drop rules).
4. Describe storage tiers: hot TSDB + downsample + cold object store, with math showing sample/sec → storage.
5. Walk through alert lifecycle: rule eval → dedupe → route → silence/escalate.
6. Finish with trade-offs & future improvements (AIOps, adaptive sampling, per-tenant cost control).

***

## Appendix — Quick checklist for production rollout

* Enforce label/metric naming standards and training.
* Implement per-tenant quotas and backpressure.
* Provide runbooks and playbooks linked in alert metadata.
* Auto-scale ingesters & query frontends.
* Periodically review alerts for relevance and flapping.
* Add SLO service and integrate burn-rate alerts.

***

If you want, I can now:

* produce a time-boxed 1-hour interview script for presenting this design (what to say every 5–10 minutes), or
* generate the capacity calculator as a small spreadsheet (you give me your scale assumptions), or
* sketch an alert rule template / runbook template you can reuse in interviews.

<br>

Which would you like next?
