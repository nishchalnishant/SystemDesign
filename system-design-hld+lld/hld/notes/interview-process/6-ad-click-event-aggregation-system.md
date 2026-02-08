# #6 Ad click event aggregation system

Below is a complete, time-boxed, interview-ready answer (1 hour) for designing an Ad Click Event Aggregation system — i.e., a system that ingests millions of ad click events from clients/publishers/SDKs, deduplicates & attributes them to campaigns, computes aggregates (clicks, unique users, CTR, conversions) in real time and batch, and serves dashboards / billing / reporting with strong correctness guarantees.

<br>

Use this script in an interview: speak the minutes noted, cover each section, and show diagrams / trade-offs as you go.

***

## 0 – 5 min — Problem recap, scope & assumptions (Set the stage)

<br>

Start by restating the goal and getting alignment.

<br>

Problem: Build a system that collects ad click events from mobile/web SDKs and ad servers, deduplicates and attributes them to campaigns/ads, computes aggregates (clicks, uniques, CTR, conversions) with low-latency dashboards and accurate billing reports.

<br>

Primary goals:

* High ingestion throughput (millions of events/sec depending on scale).
* Accurate deduplication and attribution (billing-grade).
* Real-time aggregation for dashboards (seconds to 1 minute latency) + batch for daily reporting.
* Fraud detection and tolerant to late/duplicate events.
* Durable storage and retention (raw events for audit).

<br>

Assumptions (example numbers you can change):

* 100M MAU, 5M clicks/sec peak globally (choose smaller if interviewer gives constraints).
* Average event size: 400 bytes (JSON-ish).
* Retention: raw events 30 days hot, 1 year cold; aggregated metrics kept longer.
* SLA: real-time dashboard freshness ≤ 30s; billing reports daily with correctness guarantees.

***

## 5 – 15 min — Functional & Non-Functional Requirements

<br>

Talk through FR and NFR clearly and concisely.

<br>

### Functional Requirements (Must / Should / Nice)

<br>

Must

1. Ingest click events: accept events from publishers, SDKs, and ad servers. Support HTTP/gRPC/SDKs and message formats (JSON/Protobuf).
2. Deduplicate: ensure duplicate clicks (retries, network retries, client retries) are not double-counted.
3. Attribute: map click → campaign, creative, publisher, ad placement, and user/session where applicable.
4. Aggregate metrics in real time: clicks/sec, unique users, CTR, conversions per campaign/ad/geography, with time-windowed metrics (1m, 5m, hourly).
5. Store raw events: durable storage for auditing and reprocessing.
6. Provide APIs / Dashboards: real-time dashboards (near-real-time), and historical OLAP queries for reports.
7. Billing/export: produce daily billing reports with reconciliation data and export for finance.
8. Fraud detection: flag anomalous click patterns and optionally exclude from billing.

<br>

Should

* Provide webhooks/stream for partners.
* Support replay/reprocess from raw events for backfills or corrected attribution logic.

<br>

Nice-to-have

* Real-time anomaly alerting and automatic exclusion rules.
* Multi-touch attribution models.

<br>

### Non-Functional Requirements

* Performance: ingest up to X events/sec (configure per interview); dashboard latency P95 < 30s.
* Scalability: linear scale horizontally (by partitioning).
* Durability: zero data-loss guarantee for accepted events (ACK semantics), durable replication.
* Accuracy: billing-grade correctness – tolerance for <0.1% error in billed counts (specify target).
* Availability: 99.95% for ingestion and near real-time metrics; batch reports SLAs as agreed.
* Consistency: eventual consistency for dashboard metrics; batch reports must be strongly consistent (after reconciliation).
* Security & Privacy: TLS, auth, PII minimization, GDPR-compliant deletion.
* Observability: metrics for ingestion rate, lag, dedupe rate, aggregation errors.

***

## 15 – 25 min — API & Event Schema (External contract)

<br>

Show sample schemas and key APIs.

<br>

### Event schema (example)

```
{
  "event_id": "uuid-v4-or-snowflake",   // client-generated unique id (for dedupe)
  "timestamp": 1620000000000,           // client event time (ms UTC)
  "received_at": 1620000000500,         // optional server
  "publisher_id": "pub_123",
  "campaign_id": "camp_456",
  "creative_id": "crt_789",
  "placement_id": "plc_001",
  "user_id": "anon_user_token",         // optional / hashed
  "session_id": "sess_abc",
  "ip": "1.2.3.4",                      // optional, for geo/fraud
  "user_agent": "...",
  "click_meta": { "lat":.., "lon":.. },
  "conversion_id": "conv_...",          // optional conversion reference
  "signature": "hmac..."                // optional to verify authenticity
}
```

### APIs

* POST /event/click — ingest single event (sync ack or async).
* POST /events/batch — ingest batch of events (preferred for SDKs).
* GET /metrics?campaign=...\&start=...\&end=...\&granularity=1m — aggregated metrics API (real-time or cached).
* GET /raw-event/{id} — for audit (ACL-protected).
* POST /admin/reprocess — reprocess raw data for corrected logic (internal).

<br>

Ingress semantics: offer at-least-once delivery from client perspective: client retries until ack. System must dedupe.

***

## 25 – 40 min — High-level Architecture & Data Flow

<br>

Draw or describe the data flow from clients to dashboards and store.

```
Clients/SDKs (publishers/ad-servers)
   |
   V
API Gateway / Edge (TLS, Rate-limit, Auth)
   |
   V
Ingest Tier (Stateless collectors)  --> validate & enrich (geo, UA parsing)
   |
   +--> Publish to Message Bus (Kafka / Kinesis / Pulsar)  (partitioned by dedupe key/campaign)
           |
           +--> Real-time Stream Processing (Flink/Beam/Spark Streaming)
           |       - deduplication (stateful window)
           |       - attribution enrichment
           |       - per-key incremental aggregates (sliding windows)
           |       - write aggregates to Serving Store (Redis, Dynamo/Cassandra)
           |       - output events to Fraud Detector & Enrichment sinks
           |
           +--> Raw Event Sink (S3/HDFS/Blob + metadata to catalog)
           |
           +--> OLAP Sink (append to long-term storage or stream -> batch job -> OLAP store)
           |
           +--> Realtime Serving (Materialized views / Clickhouse / Druid) for dashboards & APIs
```

Key components explained

* API Gateway / Edge: TLS termination, auth, client rate-limits, request validation, lightweight signature verification. Reject malicious high-volume clients early.
* Message Bus (Kafka): durable, partitioned stream; critical for buffer/decouple and replay. Partition by (campaign\_id || publisher\_id || hashing(event\_id)) to enable locality.
* Stream Processor: stateful processing per key; dedupe by event\_id or combination (user\_id, timestamp, campaign\_id) within a reasonable time window. Use event-time processing with watermarking to handle late events.
* Deduplication approach:
  * Option A: Use a stateful dedupe store in stream processor (keep event\_ids in time-windowed TTL store e.g., RocksDB state in Flink). This handles at-least-once client semantics.
  * Option B: A down-the-line idempotent store keyed by event\_id (e.g., insert-if-not-exists into DB using conditional writes).
* Attribution:
  * Join click events with campaign metadata and optionally impression events to support view-through attribution.
  * Support multiple attribution models: last-click, last-touch, time-decay, multi-touch modeling (implemented in streaming or batch).
* Aggregates & Serving:
  * Real-time materialized views: incremental aggregates written to a fast read store (ClickHouse / Druid / Cassandra / Redis). Choose ClickHouse or Druid for time-series OLAP with fast rollups.
  * Batch OLAP: write raw events to object store (S3), run nightly Spark jobs to compute authoritative aggregates / billing.
* Fraud detection:
  * Real-time scoring (ML or heuristic) in stream processor to flag suspicious clicks; flagged events routed to separate pipeline for manual review and exclusion.
* Reconciliation / Reprocessing:
  * Ability to replay raw data from Kafka or S3 to recompute aggregates if bug or logic change.
* Exports: billing files generation, data warehouse, partner webhooks.

***

## 40 – 50 min — Core algorithms, correctness, and edge cases

<br>

Explain how dedupe, attribution, windowing, late events, and exactly-once semantics are handled.

<br>

### Deduplication (key ideas)

* Primary dedupe key: event\_id if clients supply unique ids (strongest). If not, fallback to (publisher\_id, user\_id\_hash, timestamp\_bucket, campaign\_id) approximate key.
* Keep dedupe state for a TTL window (e.g., 7 days) using a bounded state store (RocksDB) with compaction.
* For very high volumes where storing all ids not possible, use Bloom filters + tombstones to probabilistically filter duplicates (trade-off false positives). For billing-grade accuracy, prefer exact dedupe (disk-backed state).

<br>

### Event-time processing & watermarks

* Use event-time processing: assign timestamps from event, maintain watermark to handle late arrivals. E.g., allow lateness of 5–15 minutes for near-real-time aggregates; late events are either:
  * Upserted into aggregates with correction; or
  * Emitted to a “corrections” stream for reconciliation in batch.

<br>

### Windowing & Aggregation

* Maintain sliding windows (1m, 5m, 1h) and tumbling windows for daily totals.
* Use incremental combiners (add/subtract) to compute per-window counts and uniques.
* Unique users: for uniques you may use HyperLogLog for approximate counts in real-time; but produce exact uniques in daily batch for billing.

<br>

### Exactly-once vs At-least-once

* Stream processors (Flink + Kafka) can provide exactly-once semantics across sink if using two-phase commit / transactional writes and Kafka’s EOS. For OLAP sinks that don’t support transactions, use idempotent upserts with deterministic keys (e.g., (campaign\_id, window\_start)).
* For billing-grade final numbers, rely on batch authoritative job for final reconciliation (nightly job reading raw events from S3) and mark dashboard metrics as “near-real-time (approx)” vs “official”.

<br>

### Attribution Models

* Implement last-click / last-touch in streaming: for each click, assign attribute to campaign\_id based on priority rules. Multi-touch models are heavy—do in batch.
* Maintain per-user last-touch state for short windows (session-level) in stream processor.

<br>

### Fraud detection

* Real-time heuristics: high click rates from single IP/user, impossible geolocation jumps, improbable user-agent patterns.
* ML model scoring in streaming for probability of fraud; maintain thresholds and feedback loop.

***

## 50 – 55 min — Back-of-the-envelope capacity & sizing

<br>

Show sample math (use interviewer numbers if provided).

<br>

Example scale (pick and show math): assume 5M events/sec peak, avg 400 B/event.

1. Ingress bandwidth: 5M × 400 B = 2,000,000,000 B/s = \~2 GB/s ≈ 16 Gbps.
2. Kafka retention and storage:
   * If keeping 24 hours of raw events in Kafka: 2 GB/s × 3600 × 24 ≈ 172,800 GB ≈ 169 TB. (Consider tiering to S3 where Kafka retention is short).
3. S3 raw storage (30 days): 2 GB/s × 86,400 ≈ 172.8 TB/day → 30 days ≈ 5.18 PB raw (compress and possibly pre-aggregate).
   * Clearly, you’d compress (gz/snappy) and avoid keeping full raw for all events for all customers; archive selectively.
4. Processing capacity:
   * Stream processors scaled by partition count and state size. Partition by campaign/publisher to distribute load.
5. Serving store:
   * ClickHouse clusters sized for aggregates: depends on cardinality (campaigns × geos × creatives × hourly windows). Estimate bits per aggregate row and size.

<br>

Optimization strategies:

* Pre-aggregate at collectors where possible (per-publisher batching).
* Use compression and storage tiering (hot real-time vs cold archived).
* Apply sampling for analytics (not billing!).

***

## 55 – 58 min — Reliability, security & operational concerns

<br>

Briefly cover ops & safeguards.

<br>

Reliability & DR

* Multi-AZ Kafka clusters, cross-region replication for regional resiliency.
* Exactly-once in streaming where possible; nightly batch reconciliation to detect drift and correct.
* Backpressure: API gateway throttles per-publisher; collectors buffer to Kafka.

<br>

Security & Privacy

* TLS, mTLS for internal comms, API keys for publishers.
* Validate event signatures to avoid spoofing.
* PII: hash or remove user identifiers; store IPs only if required and with TTL; support GDPR deletion (delete raw & derived data or mark excluded in billing).

<br>

Monitoring & Alerts

* Ingest metrics: events/sec, partitions lag, processing latency, dedupe rate, correction rate, fraud events rate.
* Alerts on Kafka lag, processor backpressure, significant mismatch between real-time and batch numbers.

***

## 58 – 60 min — Trade-offs, evolution & summary (Wrap-up)

<br>

Finish with trade-offs, evolution plan, and a one-paragraph summary.

<br>

### Key trade-offs

* Real-time accuracy vs cost: exact dedupe & exact uniques in real-time is expensive; hybrid approach — approximate real-time + authoritative nightly batch — balances cost and correctness.
* Stateful dedupe memory vs disk: keeping full dedupe state (event\_ids) consumes storage; trade-off with bloom filters (probabilistic).
* Stream processing complexity vs simplicity: Flink (stateful, exactly-once) adds complexity but gives correctness; simpler micro-batch jobs are easier to operate.

<br>

### Evolution path

1. MVP: API gateway → Kafka → Stateless consumers that produce per-campaign counts → ClickHouse for serving; raw events to S3.
2. Phase 2: Introduce stateful stream processing (Flink) for dedupe & attribution; ClickHouse materialized views for real-time dashboards.
3. Phase 3: Add fraud detection ML models in real-time; multi-touch attribution; cross-region replication.
4. Phase 4: Optimize storage (tiering, sampling), advanced attribution & predictive analytics.

<br>

### One-sentence summary

<br>

Build a pipeline with durable ingestion (Kafka), stateful stream processing (dedupe, attribution, fraud scoring), materialized real-time aggregates (ClickHouse/Druid), and authoritative batch reprocessing over raw S3 data for billing-grade reconciliation — balancing low-latency visibility with correctness and cost-efficiency.

***

If you’d like, I can:

* Turn this into a 5–10 slide interview deck (one per time-box section), or
* Produce a detailed diagram (textual or pseudo-graph) for the architecture, or
* Run a capacity calculator if you provide your expected events/sec, event size, and retention targets. Which would you prefer?
