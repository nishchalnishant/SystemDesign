> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design an ad click aggregator — high-volume event ingestion with near-real-time aggregation for dashboards and accurate batch aggregation for billing.
>
> **Key design decisions:**
> - Dual pipeline (Lambda Architecture): Speed layer (Flink/Kafka Streams → real-time counts) + Batch layer (Spark → accurate historical counts); merge for read queries
> - Event ingestion: click events → Kafka (partitioned by ad_id); 10K events/sec → scale horizontally; Kafka retains 7 days for reprocessing
> - Deduplication: Bloom filter in Flink for same-session dedup; exact dedup via Redis SETNX with TTL for cross-session; remove bot clicks via fraud filter
> - Aggregation windows: 1-minute tumbling windows in Flink; store in Redis sorted sets for real-time dashboard; Spark hourly/daily for billing-accurate counts
> - Query API: GET /clicks?ad_id=X&start=T1&end=T2&granularity=minute; served from pre-aggregated results store
> - Fault tolerance: Flink checkpoints every 60s; on failure, replay from last checkpoint offset; exactly-once via checkpoint + transactional writes
> - Storage: real-time counts in Redis; historical in Cassandra (ad_id + window start → count); compact with time-series optimization
>
> **Key takeaway:** Lambda architecture is necessary here — real-time Flink for dashboards (low latency, approximate), Spark batch for billing (high accuracy, high latency); don't try to serve both from one pipeline.

---
module: 05-hld-problems
topic: Hard
status: unread
tags: [05-hld-problems, system-design, hard, ads, click-aggregation, streaming, flink, lambda-architecture]
---
# Design an Ad Click Aggregator

> **Difficulty**: Hard | **Asked at**: Google, Meta, Amazon, Twitter

---

## Problem Statement

Design a system that tracks ad clicks, aggregates click counts in near-real-time, and provides both real-time dashboards (clicks per ad per minute) and accurate historical reports (clicks per ad per hour/day). The system must handle at least 10,000 click events per second and provide deduplication, fraud filtering, and query by time window.

---

## Functional Requirements

1. **Click ingestion**: Record each ad click with metadata (ad_id, user_id, timestamp, ip, country)
2. **Real-time aggregation**: Report click counts per ad in the last N minutes (for live dashboards)
3. **Historical reports**: Accurate total clicks per ad per hour/day/campaign
4. **Deduplication**: Filter duplicate clicks (same user clicks same ad within 60 seconds)
5. **Fraud filtering**: Detect and exclude bot clicks (too many clicks from same IP, headless browser patterns)
6. **Query**: `GET /clicks?ad_id=X&from=T1&to=T2&granularity=minute`

---

## Non-Functional Requirements

- **Throughput**: 10,000 clicks/sec sustained; spikes to 100,000/sec
- **Latency**: Real-time data visible in dashboard within 10 seconds of click
- **Accuracy**: Historical reports must be exactly accurate (no loss), real-time can be approximate
- **Durability**: No click event must be lost — raw events stored indefinitely for audit/reprocessing
- **Scale**: 10B clicks/day → 1.16M clicks stored per day × 365 = 4T+ events over lifetime

---

## Core Entities

| Entity | Key Fields |
|--------|-----------|
| `ClickEvent` | click_id, ad_id, campaign_id, user_id, ip_address, user_agent, country, timestamp |
| `AggregatedCount` | ad_id, window_start, window_end, granularity, click_count |
| `Ad` | ad_id, campaign_id, advertiser_id, target_url |
| `DedupKey` | user_id + ad_id (composite, TTL-based) |

---

## API Design

```http
POST /api/v1/clicks
Body: { "ad_id": "a123", "user_id": "u456", "ip": "1.2.3.4", "ua": "...", "timestamp": 1735689600000 }
Response 202: { "click_id": "c789" }

GET /api/v1/clicks/aggregate?ad_id=a123&from=2026-06-29T00:00:00Z&to=2026-06-29T01:00:00Z&granularity=minute
Response 200: {
  "ad_id": "a123",
  "data": [
    { "window": "2026-06-29T00:00:00Z", "clicks": 4200 },
    { "window": "2026-06-29T00:01:00Z", "clicks": 3810 }
  ]
}

GET /api/v1/campaigns/{campaign_id}/report?date=2026-06-29
Response 200: { "total_clicks": 1420000, "unique_users": 890000, "by_country": {...} }
```

---

## High-Level Design

```
Ad Click (browser pixel / mobile SDK)
  │
  ▼
Click Ingestion API (stateless, horizontally scaled)
  │  → validate, assign click_id, basic fraud pre-filter
  │
  ▼
Kafka topic: raw-clicks (partitioned by ad_id for ordering)
  │
  ├── Stream Path (Flink)
  │     → 1-min tumbling window: count clicks per ad_id
  │     → dedup: Redis SETNX user+ad TTL 60s
  │     → write to: Redis (real-time counts) + ClickHouse (minute aggregates)
  │
  └── Batch Path (Spark, hourly)
        → read from: S3 (raw click archive from Kafka S3 sink)
        → full dedup + fraud re-filter
        → write accurate hourly/daily totals to ClickHouse

Query Service
  → real-time (<10min) → Redis
  → historical → ClickHouse
```

This is the **Lambda Architecture**: stream path for low-latency approximate results, batch path for accurate historical results.

---

## Deep Dive 1: Deduplication at Scale

**Problem**: A user double-clicks an ad, or a network retry causes the click event to arrive twice. At 10,000 clicks/sec, the dedup store receives 10K lookups + 10K writes per second.

**Layer 1: Client-side dedup** — the ad SDK assigns a UUID (`click_id`) to each click. On retry, the same `click_id` is reused. The ingestion API checks `SETNX click:{click_id} 1 EX 3600` in Redis. If key exists → duplicate → return 200 without writing to Kafka.

**Layer 2: User × Ad dedup (stream path)** — within a 60-second window, the same user clicking the same ad counts as one click:
```
key = f"dedup:{user_id}:{ad_id}"
if not redis.SETNX(key, 1, EX=60):
    return  # duplicate
```

**Layer 3: Batch reprocessing (hourly)** — the Spark batch job re-reads raw clicks from S3, performs exact deduplication using a distributed GROUP BY `(user_id, ad_id, floor(timestamp/60000))`. This produces the ground truth. ClickHouse historical tables are overwritten with batch results.

**Bloom filter pre-filter**: Before hitting Redis, check a per-minute Bloom filter `seen_clicks_{minute}`. If the click_id is definitely not in the filter (Bloom says no) → new click, skip Redis. This reduces Redis writes by ~40% (only confirmed-new clicks hit Redis).

> 🎯 **Staff signal:** The framing that signals seniority is that "exactly-once" is impossible in this pipeline, so dedup is *layered defense-in-depth* where each layer is cheap-but-fuzzy and the final layer is exact-but-slow — you don't pick one, you stack them by cost. Client `click_id` catches retries for free; the per-user-per-ad Redis window catches double-clicks; and only the hourly Spark batch `GROUP BY (user, ad, minute-bucket)` produces *billing-grade* ground truth, which is why ClickHouse historical tables get overwritten by the batch result. The Bloom filter is the tell: it's used in its correct asymmetric direction — a "no" is definitive so you skip Redis entirely, a "maybe" falls through to the authoritative Redis check, so its false-positive rate costs a little extra work but never a wrong answer. E5 says "dedupe on click_id in Redis"; E6 says "the real-time path is best-effort dedup — Bloom to shed load in its safe 'definitely-new' direction, Redis windows for common cases — but the batch job is the exact source of truth because you can't bill from a probabilistic dedup, and exactly-once end-to-end isn't achievable anyway."

---

## Deep Dive 2: Real-Time Aggregation with Flink

**Problem**: Advertisers need to see clicks on their ad within 10 seconds — for bid adjustment and campaign monitoring.

**Flink job** (stateful stream processing):
```
source: Kafka raw-clicks (at-least-once)
keyBy: ad_id
window: TumblingEventTimeWindow(60 seconds)
aggregate: SUM(click_count) per ad_id per window
sink: Redis ZADD clicks:{ad_id} {window_start} {count}
      ClickHouse bulk insert every 30s
```

**Event time vs processing time**: Use event `timestamp` field (event time), not arrival time. Flink handles out-of-order events with a watermark of 10 seconds. Events arriving more than 10 seconds late are counted in a side output ("late events") and reconciled in the hourly batch job.

**Flink state**: Flink maintains per-key aggregation state in RocksDB (embedded, durable). On job restart, state is restored from the last checkpoint. Checkpoints every 30 seconds → max 30 seconds of re-processing on failure.

**Redis real-time store**:
```
ZADD clicks:a123 <window_start_epoch> <count>
ZRANGEBYSCORE clicks:a123 <from> <to> WITHSCORES
```
Each window score = click count. Query for last N minutes = ZRANGEBYSCORE with timestamp range. TTL on the key: 7 days (older data served from ClickHouse).

> 🎯 **Staff signal:** The distinction that separates a correct answer here is *event time vs processing time*, and it's not pedantry — it's a money bug. Aggregating by arrival time means a mobile client that buffers clicks offline and uploads an hour later lands its clicks in the wrong minute-bucket, so an advertiser's per-minute spend is wrong and un-auditable. Flink keys on the event's own `timestamp` and uses a *watermark* to bound how long a window waits for stragglers — the watermark is the explicit knob on the completeness-vs-latency tradeoff: wait longer and you catch more late events but delay the report; cut it short and stragglers spill to a side output for the batch job to reconcile. That side-output-plus-batch-reconciliation is the lambda-architecture seam: the streaming layer gives fast-approximate, the batch layer gives slow-exact, and they must agree on the same bucketing semantics or they can't be reconciled. E5 says "aggregate clicks in a Flink tumbling window"; E6 says "window on *event time* with a watermark, because processing-time buckets misattribute late/offline clicks and corrupt billing — and late events go to a side output that the batch job folds back into ground truth."

---

## Deep Dive 3: Fraud Detection

**Problem**: Bot farms generate millions of fake clicks to drain advertiser budgets (click fraud). These look like valid HTTP requests with valid `user_id` tokens.

**Fraud signals**:
1. **Click rate per IP**: > 100 clicks/minute from one IP → block IP for 1 hour (`INCRBY click_rate:{ip} 1 EX 60`)
2. **Click rate per user**: > 10 clicks/minute on any ad → flag user, require CAPTCHA
3. **Invalid conversion rate**: Clicks with 0 downstream conversions (page loads) → bot indicator
4. **User-agent fingerprinting**: Headless Chrome, known bot UA strings → reject at ingestion layer
5. **Geographic velocity**: Same user clicking from NY and London within 5 minutes → impossible travel

**Real-time blocking** (Flink CEP — Complex Event Processing):
```
pattern = CEP.pattern()
  .where(lambda e: e.user_id == prev.user_id)
  .within(timedelta(minutes=1))
  .times(10)  # same user, 10 clicks/min
→ trigger: add user_id to Redis blocklist for 1h
```

**Retrospective re-scoring**: Batch job re-scores clicks after the fact using ML model (logistic regression, features: click rate, conversion rate, IP reputation, UA). Mark suspicious clicks as `is_fraud=True` in ClickHouse. Advertiser reports exclude fraud-flagged clicks. Advertisers can appeal, triggering manual review.

> 🎯 **Staff signal:** Fraud detection is a two-tier system with opposite optimization targets, and naming that split is the signal. The real-time tier (CEP rules, per-IP/per-user rate limits) must be cheap and low-latency because it runs on every click in the hot path — so it uses simple deterministic thresholds and bounded-memory sketches, accepting that it's coarse. The batch tier (ML re-scoring) can be expensive and use rich cross-event features (conversion rate, IP reputation, impossible-travel) precisely because it runs *after the fact*, so it optimizes for accuracy over latency. The crucial architectural decision is that fraud is a *retrospective correction*, not a blocking gate: you count the click now and subtract it later once the batch flags it, because blocking synchronously on a fraud verdict would add latency to every click and false-positive-block real users. This is why billing is eventually-consistent and advertisers get credited retroactively — and why the batch ground truth, not the real-time counter, is authoritative for spend. E5 says "rate-limit clicks per IP and block bots"; E6 says "coarse real-time rules on the hot path plus a rich offline ML re-scorer, with fraud handled as retrospective clawback rather than a synchronous block — so detection accuracy doesn't tax click latency and legitimate users aren't blocked on a fast guess."

---

## Deep Dive 4: Approximate Counting — Unique Reach and Heavy Hitters

**Problem**: The report API returns `unique_users` per ad. Exact distinct counting requires storing every `user_id` that clicked each ad. At 10B clicks/day across 1M active ads, a `SET` per ad holding 8-byte user IDs costs hundreds of GB of Redis and grows without bound. Advertisers query reach across arbitrary date ranges, so precomputed daily sets must also be mergeable.

**HyperLogLog for unique reach**: HLL estimates cardinality in fixed 12 KB per counter, regardless of whether the ad got 1,000 or 100M unique viewers, with ~0.81% standard error.

```
PFADD reach:{ad_id}:{date} {user_id}      # on each click, O(1)
PFCOUNT reach:a123:2026-07-22             # estimate for one day
PFMERGE reach:a123:july reach:a123:2026-07-01 ... # union across days
```

The property that matters is that **HLL unions are lossless** — `PFMERGE` of 31 daily counters gives the same error bound as a single monthly counter. Exact sets cannot do this without storing the raw IDs, because `|A ∪ B| ≠ |A| + |B|`. This is what makes arbitrary date-range reach queries tractable.

**Cost**: 1M ads × 12 KB × 30 days retained = ~360 GB, versus tens of TB for exact sets. Accuracy is 0.81% — well inside what advertisers tolerate for a reach number, and the requirements already state real-time may be approximate.

**Count-Min Sketch for heavy hitters**: Fraud detection (Deep Dive 3) needs "which IPs are clicking far more than normal" in the streaming path. Tracking a counter per IP is unbounded — IPv6 and botnets make the key space effectively infinite.

CMS is a 2D array of counters, `d` hash functions × `w` counters wide. Increment hashes the key into one counter per row; the estimate is the **minimum** across rows (collisions only ever inflate a counter, so the min is the tightest bound).

```
w = 2000, d = 5  →  10,000 counters × 4 bytes = 40 KB total
increment(ip):  for i in 0..d: C[i][h_i(ip) % w] += 1
estimate(ip):   min(C[i][h_i(ip) % w] for i in 0..d)
```

Error is one-sided — CMS **never undercounts**, it can only overestimate (by at most `ε·N` with probability `1-δ`). For fraud that bias is the safe direction: a heavy hitter can never be missed, only occasionally flagged spuriously — and the flagged candidates then go to the exact per-IP Redis check, which is now bounded to a few thousand suspects instead of every IP.

**Where each fits**:

| Need | Structure | Cost | Error direction |
|------|-----------|------|-----------------|
| Was this click_id seen? | Bloom filter (Deep Dive 1) | ~1 KB/min | Never false negative |
| How many unique users? | HyperLogLog | 12 KB/counter | ±0.81%, two-sided |
| Which IPs are hottest? | Count-Min Sketch | 40 KB total | Never undercounts |
| Exact billable clicks | ClickHouse + batch | TBs | Exact |

**The line to say**: approximate structures serve the real-time path where the requirement explicitly permits approximation; the hourly Spark batch job remains the exact ground truth for billing. Never bill an advertiser from an HLL estimate.

> 🎯 **Staff signal:** The reason to reach for HLL and Count-Min here isn't cleverness — it's that exact counting has a memory cost that grows with the *data*, and at 10B clicks/day that's unbounded, whereas the sketch cost is *fixed regardless of cardinality*. The senior move is matching each problem to a sketch whose error direction is *safe for that specific use*: HLL's ±0.81% two-sided error is fine for a reach number nobody bills on, but you'd never use it for billing; CMS's one-sided never-undercount error is exactly right for fraud, because the failure mode you can tolerate is "flag an innocent IP for a cheap exact recheck," not "miss a real attacker." And the load-bearing property is *mergeability* — HLL unions are lossless, so arbitrary date-range reach queries compose from daily counters without re-scanning raw IDs, which is what makes the whole feature tractable. The universal caveat: approximate on the real-time path where the spec permits it, exact in the batch layer for anything that touches money. E5 says "count uniques with a HashSet"; E6 says "unbounded exact counters are the bug — HLL for reach because its error is tolerable and its unions are lossless for range queries, CMS for fraud because its one-sided error fails safe, and never let an estimate touch billing."

---

## Interviewer Questions by Level

**Junior**:
- What is click deduplication and why is it critical for ad systems?
- Why is Kafka used between the ingestion API and the aggregation pipeline?
- What's the difference between real-time and batch processing?

**Mid-level**:
- Explain the Lambda Architecture. What are the trade-offs between stream and batch paths?
- How does Flink handle out-of-order events and late arrivals?
- How do you implement deduplication at 10,000 events/sec without Redis becoming the bottleneck?

**Senior**:
- A major advertiser claims their click count is wrong. How do you audit and reconcile the stream path vs batch path results?
- Design the fraud detection pipeline — what signals do you use in real-time vs retrospectively?
- How would you handle a 10× traffic spike (100,000 clicks/sec) — where does the system bottleneck first and how do you scale each component?
- How do you ensure exactly-once semantics end-to-end from click ingestion to ClickHouse insert?

---

## Back-of-Envelope Estimation

**Scale inputs (given in NFRs):**
- 10,000 clicks/sec sustained; 100,000/sec spike; 10B clicks/day

**Ingest throughput:**
- Sustained: 10K clicks/sec; spike: 100K clicks/sec (10× surge during campaign launches)
- Each click event: `{click_id, ad_id, user_id, campaign_id, placement_id, ts, ip, user_agent}` ≈ 300 bytes
- Sustained write: 10K × 300 bytes = **3 MB/sec** to Kafka
- Spike write: 100K × 300 bytes = **30 MB/sec** — one Kafka partition handles ~100 MB/sec, so a single topic with 3 partitions absorbs the spike

**Raw event storage:**
- 10B clicks/day × 300 bytes = **~3 TB/day** raw click data
- Retention for audit/reprocessing: 2 years = **~2.2 PB** — stored as Parquet in S3 (Snappy compressed, ~5× ratio) → **~440 TB** physical storage
- Cost at $23/TB/month (S3 Standard): ~$10K/month for cold storage; use S3 Glacier after 90 days → ~$2/TB/month = **~$900/month**

**Aggregation output (pre-aggregated, not raw):**
- Aggregation granularity: clicks per `(ad_id, campaign_id, hour)` 
- 1M active ads × 24 hours = 24M aggregate rows/day, each ~100 bytes = **2.4 GB/day** in the analytics DB
- This is 1,000× smaller than raw — dashboards query aggregates, not raw events

**Real-time dashboard latency:**
- Target: data visible within 10 seconds of click
- Flink streaming job: reads from Kafka, tumbling 1-minute windows per `(ad_id, hour)`, writes partial aggregates to Redis every 10 seconds
- Redis key: `agg:{ad_id}:{hour}` → value: `{clicks, impressions, conversions}` (60 bytes)
- 1M active ads × 60 bytes = **60 MB** in Redis for all live aggregates — trivial

**Deduplication:**
- Click fraud / bot traffic: same click_id arrives multiple times from Kafka consumer retries
- Bloom filter per 5-minute window: 10K clicks/sec × 300 sec = 3M items; at 1% false positive rate → **~4 MB** per window → 12 Bloom filters in memory (1-hour lookback) = **50 MB total**
- True dedup for billing: Redis `SETNX click_id:{id} 1 EX 86400` — 10K/sec × 86,400 sec × 40 bytes/key = **~34 GB** in Redis; use a dedicated dedup Redis instance

**Architecture decisions driven by these numbers:**
- **Kafka as ingest buffer, not direct DB writes**: A 10× spike (100K clicks/sec) against a DB would saturate connections and cause write failures. Kafka absorbs the spike elastically; consumers process at their own pace. The aggregation job doesn't need to keep up with spikes — it catches up when load drops.
- **Two-tier storage (raw + aggregated)**: Dashboards querying 10B raw rows/day is impossible. Pre-aggregate to `(ad_id, hour)` counts in Flink. Raw events go to S3 Parquet for audit and ML reprocessing. Aggregates go to a time-series DB (ClickHouse, TimescaleDB) for fast dashboard queries.
- **Bloom filter for approximate dedup, Redis for exact**: Bloom filter catches ~99% of duplicates in-stream with 50 MB memory. The 1% that slip through are caught by the Redis exact-dedup layer. Two-tier because Redis at 34 GB × 24-hour window is expensive; Bloom filter handles the bulk cheaply.
- **Flink over Spark for real-time aggregation**: 10-second visibility requirement rules out micro-batch (minimum ~30-second latency with Spark SS). Flink event-time processing with 10-second watermarks achieves the 10-second target.

---

## Related

**Concepts used in this design**

- [Stream Processing](../../04-advanced-topics/01-distributed-architecture/05-stream-processing.md)
- [Stream vs Batch](../../04-advanced-topics/03-internals/12-stream-vs-batch.md)
- [Kafka Internals](../../04-advanced-topics/03-internals/03-kafka-internals.md)
- [Bloom Filter](../../02-building-blocks/02-performance/04-bloom-filter.md)
- [Cassandra Internals](../../04-advanced-topics/03-internals/05-cassandra-internals.md)

**Practice next**

- [Metrics Monitoring System](../03-hard/metrics-monitoring-system.md)
- [YouTube](../02-medium/youtube.md)

Metrics monitoring solves the same ingest/aggregate problem for time series.

**Frameworks**: [HLD Template](../../07-interview-templates/01-frameworks/01-hld-template.md) · [Capacity Estimation](../../07-interview-templates/02-cheat-sheets/02-capacity-estimation.md) · [Trade-offs Cheat Sheet](../../07-interview-templates/02-cheat-sheets/01-trade-offs-cheat-sheet.md)
