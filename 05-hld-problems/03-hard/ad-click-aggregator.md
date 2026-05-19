# Design an Ad Click Aggregator

> **Difficulty**: Hard
> **Topics**: Lambda Architecture, Stream Processing, Deduplication, OLAP, Real-time Aggregation
> **Time**: 60 minutes
> **Companies**: Google, Meta, Amazon Ads, Twitter/X, TikTok, The Trade Desk

---

## Problem Statement

Design an ad click tracking and aggregation system that:
- Records every ad click (impression → click → conversion)
- Deduplicates fraudulent/duplicate clicks in real-time
- Aggregates click counts by (ad_id, date, country, device_type) within seconds
- Serves aggregated reporting to advertisers (clicks, spend, CTR, conversions)
- Handles 1M clicks/sec at peak (Black Friday, Super Bowl)
- Provides both real-time dashboards (seconds freshness) and historical reports (exact counts)

---

## Analogy

A stadium box office counting ticket scans for a concert. Each turnstile (click event) must be counted exactly once — people sneaking back through the wrong gate (duplicate clicks) get caught. The box office needs both a real-time count ("how many people are in right now?") and an exact daily report for the promoter ("total attendance was 68,432").

The challenge: 50 gates all scanning simultaneously, some people try to scan twice (bots), and the promoter needs both a live count on a dashboard AND a certified accurate number for their contract.

---

## What Breaks Without This System?

Without a click aggregation pipeline, every advertiser dashboard query runs a `COUNT(*)` over billions of raw click rows in a transactional database — a multi-second full table scan that crushes OLTP performance and makes the database unavailable for writes. Duplicate clicks (network retries, mobile reconnects) get counted as valid clicks, overcharging advertisers and destroying trust. At 1M clicks/sec during peak events, a simple INSERT per click into a relational DB saturates at ~10K writes/sec — 99% of clicks are dropped.

---

## Derive the Architecture

**1 server, INSERT per click into SQL**: Each click event writes a row to `clicks(ad_id, user_id, timestamp, country, device)`. Dashboard queries `COUNT(*)` per ad. Works for ~1K clicks/sec. Breaks when: 1M clicks/sec × 1 KB/row = 1 GB/sec write throughput — far beyond PostgreSQL's ~10 MB/sec sustained write capacity. Fix: don't write individual rows to SQL; ingest clicks into a write-optimized log first.

**Kafka ingestion layer**: Clicks publish to Kafka topics partitioned by ad_id. Kafka handles 1M events/sec across 10+ brokers. Consumers read from Kafka asynchronously. Breaks when: a naive consumer doing `SELECT COUNT WHERE ad_id=X AND window=Y` per click is still O(clicks) per query and doesn't produce the real-time counts advertisers want. Fix: stream processor (Flink/Kafka Streams) aggregates counts in memory over time windows, emitting per-minute totals.

**Stream aggregation with Flink**: Flink reads click events from Kafka, maintains in-memory counters per (ad_id, minute, country, device), and writes aggregated rows to a fast OLAP store every 30 seconds. Latency from click to dashboard: ~30–60 seconds. Handles 1M clicks/sec with 10 Flink workers. Breaks when: the same click arrives twice (mobile reconnect sends the click again) — the counter increments twice, overcharging the advertiser. Fix: deduplicate clicks within a 1-minute window before counting using a distributed bloom filter keyed by (user_id, ad_id, minute).

**Bloom filter deduplication**: Each Flink worker holds a bloom filter for its partition's click events in the last 60 seconds. Duplicate clicks are filtered before hitting the counter with ~0.1% false-positive rate. Handles the duplicate problem at low memory cost. Breaks when: the stream aggregation (approximate, may miss late-arriving events from mobile devices that were offline for 10 minutes) diverges from the certified accurate batch count that advertisers are billed on. Fix: run a parallel batch pipeline (Spark over raw click logs in S3) that produces exact hourly/daily counts for billing.

**Lambda architecture (stream + batch)**: Stream layer (Flink) serves real-time approximate counts for dashboards. Batch layer (Spark on S3 raw logs) produces exact counts for billing, reconciled hourly. Breaks when: a single hot ad_id receives 100K clicks/sec — all events land on one Kafka partition (same ad_id hash), saturating a single consumer thread. Fix: add a random suffix to the Kafka partition key during high-volume bursts (`ad_id + random(0..N)`) to spread writes; aggregate the N partial counters at read time.

---

## Why This Is Hard

1. **Exactly-once counting**: A click might arrive multiple times (network retry, mobile reconnect after losing signal). Counting it twice overcharges the advertiser. Deduplication at 1M events/sec requires distributed bloom filters or exactly-once idempotency windows.
2. **Lambda architecture trade-off**: Real-time aggregation (stream) is fast but approximate. Batch aggregation (Spark/Hive) is accurate but slow (hourly delay). Combining both (Lambda) means maintaining two pipelines that must produce consistent results — operationally complex.
3. **Hotspot handling**: A viral ad can receive 100K clicks/sec on a single ad_id. Aggregating a single counter at 100K writes/sec with high availability requires distributed counter sharding (write to N shards, merge on read).
4. **Time-window aggregation**: "Clicks in the last 5 minutes" requires sliding window computation. Kafka Streams / Flink supports this, but late-arriving events (mobile devices back online after tunnel) can fall into the wrong window.
5. **Click fraud**: Botnets clicking ads to drain advertiser budgets. Detection must be real-time (block fraudulent clicks before counting), not post-hoc.

---

## Critical Requirements

### Functional
- Record click events (ad_id, user_id, timestamp, country, device, IP)
- Deduplicate: same user clicking same ad within 1 minute → count once
- Real-time aggregates: clicks per ad per minute (< 5 second freshness)
- Historical reports: clicks/impressions/CTR by hour/day/month (exact)
- Click fraud detection: IP velocity, bot fingerprint, invalid traffic filtering
- Budget enforcement: stop serving ads when spend limit reached

### Non-Functional
- **Ingest throughput**: 1M events/sec sustained, 5M peak
- **Real-time freshness**: < 5 seconds for dashboard aggregates
- **Historical accuracy**: Exact counts (no approximation) for billing
- **Deduplication window**: 1 minute (clicks within 1 minute are deduped)
- **Data retention**: 3 years (tax/legal requirements for ad spend)

---

## Scale Estimation

```
Clicks: 1M/sec average, 5M/sec peak (holiday campaigns)
Click event size: 200 bytes (ad_id, user_id, timestamp, IP, user_agent, coordinates)
Ingest data rate: 1M × 200B = 200MB/sec, peak 1GB/sec

Storage:
  Raw events: 1M × 200B × 86,400s = 17TB/day
  3 years: 17 × 365 × 3 = 18.6 PB → stored compressed in S3 (~4PB)

Aggregated (OLAP):
  Dimensions: ad_id (10M) × date × country (250) × device (5)
  Metrics: clicks, impressions, conversions, spend
  Pre-aggregated rows: manageable (TB scale, not PB)

Advertisers querying reports:
  100K advertisers × 10 report queries/day = 1M queries/day
  Queries on OLAP DB (ClickHouse): < 1 second for most reports
```

---

## Architecture: Lambda Architecture

```
Lambda Architecture solves the real-time vs accuracy trade-off:

┌────────────────────────────────────────────────────────────────┐
│                    CLICK EVENT INGEST                         │
│   Client → API Gateway → Kafka topic "ad_clicks"             │
└───────────────────────┬────────────────────────────────────────┘
                        │
            ┌───────────┴───────────┐
            ▼                       ▼
   SPEED LAYER                 BATCH LAYER
   (Stream Processing)         (Batch Processing)
   ┌────────────────┐          ┌────────────────────┐
   │ Flink / Kafka  │          │ S3 (raw events)    │
   │ Streams        │          │ + Spark batch job  │
   │                │          │   (hourly/daily)   │
   │ - Deduplicate  │          │                    │
   │ - Aggregate    │          │ - Exact counts     │
   │   (5-sec wins) │          │ - All dimensions   │
   │ - Fraud filter │          │ - Slow (1h delay)  │
   │                │          └────────┬───────────┘
   └───────┬────────┘                   │
           │                            │
           ▼                            ▼
   ┌────────────────┐          ┌────────────────────┐
   │  Real-time DB  │          │ OLAP DB            │
   │  (Redis        │          │ (ClickHouse)       │
   │  sorted sets)  │          │ Historical reports │
   └───────┬────────┘          └────────┬───────────┘
           │                            │
           └────────────┬───────────────┘
                        ▼
               SERVING LAYER
               ┌─────────────┐
               │ Report API  │
               │             │
               │ Recent data │◄── Redis (< 1 hour)
               │ Historical  │◄── ClickHouse (> 1 hour)
               └─────────────┘
                        │
                        ▼
              Advertiser Dashboard
```

---

## Core Components

### 1. Click Ingestion

```
Client-side (browser/mobile SDK):
  Click fires → SDK buffers for 50ms → batch POST to ingest API
  Include: ad_id, placement_id, timestamp (client), user_agent, viewport size, referrer
  Note: client timestamp is unreliable (clock skew, manipulated)
  Server stamps server_timestamp on arrival — use server time for aggregation

Ingest API:
  Stateless, horizontally scalable
  Validates: valid ad_id, not expired, not obviously fraudulent (IP blocklist)
  Idempotency key: hash(ad_id, user_id, client_timestamp) → check bloom filter
  If seen within 1 minute: discard (duplicate)
  Writes to Kafka topic "ad_clicks" (partitioned by ad_id % 1024)

```python
@app.post("/click")
async def record_click(click: ClickEvent):
    # Quick fraud check
    if await is_ip_blocked(click.ip_address):
        return {"status": "rejected", "reason": "blocked_ip"}
    
    # Idempotency check (Redis bloom filter)
    key = f"{click.ad_id}:{click.user_id}:{click.client_ts // 60}"  # 1-min bucket
    if not await bloom_filter.add_if_absent(key):
        return {"status": "deduplicated"}  # Already seen
    
    # Publish to Kafka
    await kafka.produce("ad_clicks", key=click.ad_id, value=click.to_bytes())
    return {"status": "accepted"}
```

### 2. Stream Processing (Flink)

```
Flink job: "click_aggregator"

Input: Kafka topic "ad_clicks"
Output: Real-time aggregates → Redis

Transformations:
  1. Parse + validate each event
  2. Deduplication (stateful): per (ad_id, user_id) → exactly once per 1-min window
  3. Fraud detection: Velocity check — if same IP sends > 100 clicks/min → flag
  4. Enrich: lookup ad metadata (advertiser_id, campaign_id) from cache
  5. Aggregate: tumbling 5-second windows
     Key: (ad_id, country, device_type, 5-second-bucket)
     Value: count, sum_spend
  6. Write to Redis: INCRBY "clicks:{ad_id}:{date}:{country}:{device}" count

Late data handling:
  Flink watermarks: Allow events up to 30 seconds late
  Events arriving > 30s late: discarded from stream (counted in batch layer instead)
  Batch layer (Spark) reprocesses from S3 → corrects late data in ClickHouse
```

### 3. Deduplication

```
Problem: Mobile user clicks ad → loses connectivity → click fires again on reconnect.
         Same user, same ad, 30 seconds apart → should count as ONE click.

Strategy: Idempotency window deduplication

Bloom Filter (fast, probabilistic):
  Redis Bloom Filter: "seen_clicks" with 1% false positive rate
  Key: hash(ad_id, user_id, timestamp_minute)  ← minute-granularity bucket
  On click: if bloom.add(key) returns false → already seen → deduplicate
  Bloom filter TTL: 2 minutes (covers 1-min dedup window + buffer)
  Memory: 1M clicks/sec × 60 sec × 30 bytes/key = 1.8 GB → acceptable

For exact deduplication (billing):
  Batch layer uses Spark deduplication:
  SELECT DISTINCT hash(ad_id, user_id, unix_timestamp / 60) FROM raw_clicks
  Count unique hashes → exact deduplicated click count for invoicing
```

### 4. Hotspot Handling

```
Problem: Campaign goes viral. ad_id_123 receives 200K clicks/sec.
         Single Kafka partition (partitioned by ad_id) gets overwhelmed.

Solution: Write sharding (scatter-gather)
  On write: split hot ad_id across N virtual shards
    ad_id_123_shard_0, ad_id_123_shard_1, ..., ad_id_123_shard_9
    Route each click to shard = hash(user_id) % N

  On read (reporting): sum across all shards
    SELECT SUM(clicks) FROM shards WHERE ad_id = 'ad_id_123' AND date = today

  N selected dynamically:
    Monitor ingest rate per ad_id
    If rate > 10K/sec → escalate shard count
    Implemented as top-N hot ads tracked in Redis sorted set (ZADD by click velocity)
```

### 5. Click Fraud Detection

```
Real-time signals (block within 100ms):
  IP velocity: > 50 clicks/min from same IP → block IP
  User velocity: > 100 clicks/hour from same user_id → invalid traffic
  Geographic impossibility: same user clicks from London + NYC within 5 minutes
  User-agent blocklist: known bot signatures

Near-real-time ML model (applied within 5 seconds, before counting):
  Features: IP reputation, user-agent, click timing pattern, conversion rate history
  Model output: invalid_traffic_score (0-1)
  Threshold: > 0.8 → mark as invalid traffic, don't count in billing

Batch model (refund credits after 24h review):
  Sophisticated pattern detection on full day's traffic
  Bot networks identified by correlated click patterns across IPs
  Advertiser credits issued for identified invalid traffic
```

---

## Aggregation Queries (ClickHouse)

```sql
-- Hourly aggregation table (materialized from raw events)
CREATE TABLE click_agg_hourly (
    ad_id       String,
    hour        DateTime,
    country     String,
    device_type Enum8('mobile'=1, 'desktop'=2, 'tablet'=3, 'unknown'=4),
    clicks      UInt64,
    impressions UInt64,
    conversions UInt64,
    spend_usd   Decimal(18, 6)
) ENGINE = SummingMergeTree()
  PARTITION BY toYYYYMMDD(hour)
  ORDER BY (ad_id, hour, country, device_type);

-- Advertiser report: last 30 days by day
SELECT
    toDate(hour)  AS day,
    SUM(clicks)   AS total_clicks,
    SUM(impressions) AS total_impressions,
    SUM(clicks) / SUM(impressions) AS ctr,
    SUM(spend_usd) AS total_spend
FROM click_agg_hourly
WHERE ad_id = 'ad_123'
  AND hour BETWEEN now() - INTERVAL 30 DAY AND now()
GROUP BY day
ORDER BY day;

-- Query time: < 100ms (ClickHouse columnar storage + sparse indexing)
```

---

## Failure Scenarios

### Kafka Consumer Lag

```
Symptom: Stream processing falls behind; dashboard shows stale data.
Cause: Flink job slow (complex fraud detection) or Kafka partition undersized.

Response:
  Scale out Flink workers (increase parallelism)
  If Kafka partition count insufficient: increase partitions (disruptive; do proactively)
  Temporary: increase freshness SLO from 5s to 30s (notify advertisers)

Data integrity:
  Kafka retains raw events for 72 hours
  Even if stream processing falls behind, batch layer (S3 + Spark) processes all events
  Billing accuracy not affected — only dashboard freshness
```

### Redis Bloom Filter Lost (Cache Flush)

```
Cause: Redis restart or cluster failover
Effect: Bloom filter empty → all clicks treated as new → potential duplicate counting
  for the 1-2 minutes before new bloom filter rebuilds

Mitigation:
  Redis persistence: AOF (Append-Only File) for bloom filter state
  After restart: replay AOF → bloom filter reconstructed in < 30 seconds
  During reconstruction: use 60-second dedup window in DB as fallback
```

---

## Monitoring

```
Ingest:
  Click ingest rate (target: matches client-reported click volume ± 1%)
  Deduplication rate (track % deduplicated — sudden spike = potential issue)
  Kafka consumer lag per partition (alert if > 30 seconds)
  Fraud rejection rate (alert if > 5% → possible bot attack underway)

Aggregation:
  Dashboard freshness: time since last update (target < 5 seconds)
  Batch job SLA: Spark hourly job completes within 30 min of hour boundary

Business:
  Budget enforcement latency: time to pause ad after budget exhaustion (target < 10 sec)
  Report query latency P99 (ClickHouse — target < 2 seconds)
```

---

## Interview Talking Points

**Q: "Why Lambda Architecture instead of just streaming?"**
> "Stream processing gives us < 5-second freshness but has two weaknesses: late-arriving events (mobile users reconnecting after 2 hours offline) are dropped or miscounted, and deduplication state is limited to a short window. The batch layer (Spark over S3) processes the complete day's data with no latency constraints — it catches late events and runs full deduplication. Advertisers get exact billing from the batch layer; the stream layer powers the real-time dashboard only. We serve recent data from Redis, historical data from ClickHouse."

**Q: "How do you handle a viral campaign spike to 1M clicks/sec on one ad?"**
> "Hot ad sharding. We detect hot ad_ids by monitoring click velocity in a Redis sorted set. When an ad_id exceeds a threshold (say 10K clicks/sec on one Kafka partition), we dynamically route its clicks to N virtual shards — effectively distributing writes across N Kafka partitions. Each shard aggregates independently in Flink. On the read side, the reporting query SUMs across all shards. The advertiser's dashboard shows the correct total; they're unaware of the sharding."

**Q: "What's the difference between your real-time count and your billing count?"**
> "The real-time count from Flink is approximate — it's fast, shows advertisers their live campaign performance, but may double-count some edge cases (race conditions in distributed dedup, late events). The billing count comes from the batch layer: Spark deduplicates on the full day's raw events in S3 using a deterministic hash key, producing an exact count. Advertisers are billed on the batch count. If there's a discrepancy > 2%, we investigate — it usually indicates a stream processing bug or incomplete data from a source."

---

## Interview Questions Asked

### Google
1. **"Design AdWords click aggregation — how do you count clicks on billions of ads in real-time?"** → Probe: stream processing, deduplication, late data handling, billing accuracy. Hint: Kafka ingests raw clicks; Flink aggregates per (ad_id, window); deduplicate using Bloom filter in Redis; serve real-time counts from Redis, exact billing counts from batch (Spark on S3).

### Meta
1. **"Design real-time ad attribution — how do you tie a conversion back to the ad that caused it?"** → Probe: joining click events with conversion events across time, deduplication, multi-touch attribution. Hint: store click events with user_id + ad_id in a time-windowed store (Redis TTL 7 days); on conversion, look up recent clicks for user_id, apply attribution model (last-click or multi-touch), emit attribution event.

### Common Follow-ups
1. **"How do you deduplicate bot clicks without a database round-trip on every click?"** → Bloom filter in Redis: check before inserting; false positive rate ~1% (acceptable — slightly under-counts, never over-counts for billing); full deduplication in batch layer using SHA-256(user_id+ad_id+timestamp) as dedup key.
2. **"How do you handle late-arriving data in your streaming pipeline?"** → Watermark-based windowing: Flink emits window result only after watermark passes window_end + allowed_lateness (e.g., 2 hours); events arriving after max lateness go to a side output for batch reprocessing; billing uses batch counts which include all late events.
3. **"Lambda vs Kappa architecture — which would you choose?"** → Lambda: stream layer for low-latency dashboard + batch layer for accurate billing; Kappa: Kafka as single source of truth, reprocess by replaying from beginning; choose Lambda when batch and stream have different accuracy requirements (this system), Kappa when a single code path suffices.
4. **"How do you make aggregations queryable in real-time vs batch?"** → Real-time: Flink writes per-window aggregates to Redis (hot path, sub-second freshness); batch: Spark writes finalized hourly/daily aggregates to ClickHouse (analytical queries, exact counts); serve dashboard from Redis for live data, ClickHouse for historical.
