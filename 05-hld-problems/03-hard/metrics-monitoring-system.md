# Metrics & Monitoring System

> Design a system that ingests 1M+ data points/sec from 10,000 services, stores them for 10+ years, and serves real-time queries and alerts.

---

## Problem Mindmap

```
Metrics & Monitoring System
├── Why It Exists
│   ├── 10,000 services × 100 metrics/sec = 1M points/sec
│   ├── On-call engineer needs: what broke, when, why
│   └── SLA enforcement requires real-time alerting (<1 min lag)
├── Ingestion Layer
│   ├── Push model → agents push to collector (StatsD, Telegraf)
│   │   ├── Pro: works behind NAT/firewall, low scrape latency
│   │   └── Con: hard to detect dead agents
│   └── Pull model → collector scrapes /metrics endpoint (Prometheus)
│       ├── Pro: collector controls rate, dead service is obvious
│       └── Con: requires service discovery, open ports
├── Storage: Time-Series DB
│   ├── Gorilla compression (Facebook) → 1.37 bytes/point vs 16 bytes raw
│   ├── LSM tree writes → sequential disk, high ingest throughput
│   ├── Chunk-based storage → 2h chunks in Prometheus TSDB
│   └── Cardinality → labels explode series count (the #1 scaling problem)
├── Query Engine
│   ├── PromQL / InfluxQL / MetricsQL
│   ├── Range queries → scan across time window
│   ├── Aggregations → sum/avg/rate across label dimensions
│   └── Downsampling → compact old data (1s → 1m → 1h)
├── Alerting Pipeline
│   ├── Rule evaluation → every 15s check threshold
│   ├── Pending → Firing state machine (avoid flapping)
│   ├── Alertmanager → dedup, group, silence, route
│   └── Notifications → PagerDuty / Slack / email
├── Long-Term Storage
│   ├── Thanos → sidecar uploads blocks to S3; global query view
│   ├── Cortex → horizontally scalable Prometheus-as-a-service
│   └── M3DB → Uber's TSDB, column-oriented for compression
└── Scale Numbers
    ├── Prometheus: ~10M active series per instance
    ├── Datadog: 10+ trillion points/day globally
    └── Retention: hot (30d SSD) → warm (1yr HDD) → cold (10yr S3)
```

---

## 1. Why Metrics & Monitoring Exists

**Question**: You deploy 10,000 microservices. One of them starts throwing errors at 3 AM. How do you know which one, what metric crossed a threshold, and what changed 5 minutes before?

**Physical constraint**: 10,000 services × 100 metrics/sec × 8 bytes/point = 8 MB/sec raw data. Over 30 days = ~20 TB. Raw storage doesn't compress well; query over 20 TB for a single dashboard is too slow.

**Minimal solution**: Log every metric to a file, grep when something breaks. Breaks at: files grow unbounded, grep across 10,000 hosts is serial, no alerting, no aggregation, no cross-service correlation.

**Production generalization**: Dedicated time-series database with compression, a pull-based scraping model with service discovery, label-based dimensional data model, a separate alerting engine that evaluates rules continuously, and tiered storage (SSD → HDD → object store) for cost-efficient long-term retention.

---

## 2. Core Concepts

### 2.1 Data Model

A metric is a `(name, labels, timestamp, value)` tuple:

```
http_request_duration_seconds{service="checkout", method="POST", status="200"} 0.243 1716220800
```

- **Metric name**: what is measured
- **Labels (tags)**: dimensions for filtering/grouping — creates a **time series** per unique label combination
- **Cardinality**: total number of distinct time series = `∏(label cardinalities)`. Adding `user_id` label with 10M users creates 10M series → **cardinality explosion**

### 2.2 Metric Types

| Type | Description | Example |
|------|-------------|---------|
| Counter | Monotonically increasing | `http_requests_total` |
| Gauge | Current value, goes up/down | `memory_usage_bytes` |
| Histogram | Buckets of observed values | `request_duration_seconds_bucket{le="0.1"}` |
| Summary | Client-side quantiles | `rpc_duration_seconds{quantile="0.99"}` |

### 2.3 Pull vs Push

| Dimension | Pull (Prometheus) | Push (StatsD/Telegraf) |
|-----------|-------------------|------------------------|
| Dead service detection | Immediate (scrape fails) | Requires heartbeat timeout |
| NAT/firewall traversal | Needs open port | Works everywhere |
| Control over scrape rate | Collector controls | Agent controls |
| Discovery requirement | Yes (SD needed) | No |
| Fan-out writes | No | Yes (agent pushes to N collectors) |

### 2.4 TSDB Compression: Gorilla Algorithm (Facebook, 2015)

**Problem**: 10B data points/min at Facebook, storing 64-bit floats + 64-bit timestamps = 16 bytes each.

**Insight**: Adjacent timestamps differ by a small delta; adjacent values are often close (XOR with previous value has many leading zeros).

**Timestamp encoding**:
- Store first timestamp raw (64 bits)
- Store delta from expected interval (e.g., delta = actual − 60s)
- Encode delta-of-delta with variable-length bits: 0→0bits, 10→7bits, 110→9bits, 1110→12bits, 1111→32bits

**Value encoding (XOR)**:
- XOR current float with previous float
- If XOR == 0: 1 bit ('0')
- Else: encode meaningful bits between leading/trailing zeros

**Result**: 1.37 bytes/point average (vs 16 bytes raw) = **11.7× compression**.

### 2.5 Alerting State Machine

```
INACTIVE ──(condition true for pending_time)──► PENDING
PENDING  ──(condition still true)──────────────► FIRING
FIRING   ──(condition false)───────────────────► INACTIVE
PENDING  ──(condition false before firing)─────► INACTIVE
```

Pending period prevents alert flapping on transient spikes. Typical: 5 minutes pending before firing.

---

## 3. Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        Services Layer                           │
│  [svc-1 :8080/metrics]  [svc-2 :8080/metrics]  [svc-N ...]     │
└──────────────────────┬──────────────────────────────────────────┘
                       │ HTTP scrape (pull) every 15s
┌──────────────────────▼──────────────────────────────────────────┐
│                    Collector Layer                               │
│  Prometheus / Victoria Metrics / Thanos Receiver                │
│  ├── Service Discovery (Consul/K8s) to find targets             │
│  ├── Scrape scheduler (consistent hashing for sharding)         │
│  └── Remote Write → forward to long-term storage                │
└──────────────────────┬──────────────────────────────────────────┘
                       │
         ┌─────────────┴─────────────┐
         ▼                           ▼
┌────────────────┐        ┌──────────────────────┐
│  Local TSDB    │        │  Long-Term Store      │
│  (30d, SSD)   │        │  Thanos / Cortex / M3 │
│  2h chunks     │        │  S3 + index (years)   │
└────────┬───────┘        └──────────┬───────────┘
         │                           │
         └──────────┬────────────────┘
                    ▼
         ┌──────────────────┐
         │   Query Engine   │
         │   PromQL / HTTP  │
         └──────┬───────────┘
                │
    ┌───────────┴────────────┐
    ▼                        ▼
┌────────┐         ┌──────────────────┐
│Grafana │         │  Alert Manager   │
│Dashbrd │         │  (dedup/group/   │
└────────┘         │   route/silence) │
                   └────────┬─────────┘
                            ▼
                  ┌──────────────────┐
                  │  Notifications   │
                  │  PagerDuty/Slack │
                  └──────────────────┘
```

### Sharding Collectors

10M series cannot fit in one Prometheus. Shard by:
1. **Horizontal sharding**: consistent hash on `{job, instance}` → route to shard N
2. **Functional sharding**: infra metrics on shard-1, app metrics on shard-2
3. **Federation**: Prometheus scrapes other Prometheus instances for aggregated view

---

## 4. Real-World Usage

| System | Approach | Scale |
|--------|----------|-------|
| Prometheus + Thanos | Pull-based, TSDB blocks on S3, global query | Standard k8s stack |
| Datadog | Push (DogStatsD agent), proprietary TSDB, SaaS | 10T+ points/day |
| InfluxDB | Push, TSM storage engine, SQL-like Flux query | Self-hosted option |
| Victoria Metrics | Prometheus-compatible, better compression, single binary | 10M series/node |
| M3DB | Distributed TSDB by Uber, tiered storage, Prometheus-compatible | Uber-scale |
| Cortex | Horizontally scalable Prometheus, multi-tenant | Cloud-native |

---

## 5. Trade-offs

| Decision | Option A | Option B | Winner |
|----------|----------|----------|--------|
| Pull vs Push | Pull (Prometheus) | Push (StatsD) | Pull for k8s, Push for IoT/mobile |
| Storage | Local TSDB | Remote S3 (Thanos) | Local for hot, S3 for cold |
| Cardinality control | Drop high-card labels | Quota enforcement | Both needed |
| Retention cost | Keep raw forever | Downsample after 30d | Downsample for cost |
| Multi-tenancy | Separate Prometheus/tenant | Cortex shared cluster | Cortex at scale |

---

## 6. Failure Scenarios

### 6.1 Cardinality Explosion
**Symptom**: Prometheus OOM; query times out; disk fills in hours.
**Cause**: Label with unbounded values (user ID, session ID, trace ID).
**Fix**: Enforce cardinality limits per metric at ingestion. Block labels with >10,000 unique values.

### 6.2 Scrape Target Avalanche
**Symptom**: New Prometheus restarts and simultaneously scrapes 50,000 targets → TCP storm.
**Fix**: Stagger scrape times with jitter; use scrape_timeout < scrape_interval.

### 6.3 Alert Storm During Incident
**Symptom**: One infrastructure failure fires 10,000 alerts.
**Fix**: Alertmanager group_by + group_wait (30s) + group_interval (5m) collapses related alerts into one notification.

### 6.4 TSDB Block Corruption
**Symptom**: Prometheus cannot start after crash; WAL corrupted.
**Fix**: WAL replay on startup; Thanos uploads completed blocks to S3 as durable backup. Lost in-progress window: up to 2h chunk.

---

## 7. Performance Numbers

| Operation | Latency | Notes |
|-----------|---------|-------|
| Prometheus scrape | ~1ms | 15s interval default |
| TSDB write (WAL append) | <1µs | Sequential write |
| Gorilla-compressed point | 1.37 bytes avg | vs 16 bytes raw |
| Simple PromQL query (30d range) | ~200ms | Local TSDB, 10M series |
| Thanos global query (1y range, S3) | 2–10s | Cross-shard, object store |
| Alertmanager evaluation cycle | 15s | Configurable |
| P99 notification delivery | <60s | From breach to PagerDuty |

---

## 8. Java Implementation Sketch

### 8.1 Time-Series Write Path (TSDB append simulation)

```java
public class TimeSeriesDB {
    // WAL: Write-Ahead Log — durability before in-memory write
    private final WriteAheadLog wal;
    // Active chunk map: seriesId → current 2h chunk
    private final ConcurrentHashMap<Long, Chunk> activeChunks;
    private final BlockStore blockStore; // completed 2h blocks

    public void append(long seriesId, long timestampMs, double value) {
        // 1. Write to WAL first (crash recovery)
        wal.append(seriesId, timestampMs, value);

        // 2. Get or create active chunk for this series
        Chunk chunk = activeChunks.computeIfAbsent(seriesId,
            id -> new Chunk(timestampMs, TWO_HOURS_MS));

        // 3. If chunk full (2h elapsed), seal and start new
        if (chunk.isExpired(timestampMs)) {
            blockStore.seal(seriesId, chunk);   // persist completed block
            chunk = new Chunk(timestampMs, TWO_HOURS_MS);
            activeChunks.put(seriesId, chunk);
        }

        // 4. Gorilla-encode and append to chunk
        chunk.appendGorilla(timestampMs, value);
    }
}
```

### 8.2 Alerting Rule Evaluator

```java
@Scheduled(fixedRate = 15_000) // every 15 seconds
public void evaluateAlertRules() {
    for (AlertRule rule : alertRules) {
        QueryResult result = queryEngine.execute(rule.getPromQL());
        boolean isFiring = result.getValue() > rule.getThreshold();

        AlertState current = alertStateMap.get(rule.getId());
        AlertState next = transition(current, isFiring, Instant.now());
        alertStateMap.put(rule.getId(), next);

        if (next == AlertState.FIRING && current != AlertState.FIRING) {
            alertManager.send(Alert.from(rule, result));
        }
    }
}

private AlertState transition(AlertState current, boolean firing, Instant now) {
    return switch (current) {
        case INACTIVE -> firing ? AlertState.PENDING : AlertState.INACTIVE;
        case PENDING  -> firing
            ? (pendingDuration(now) > rule.getPendingSeconds()
                ? AlertState.FIRING : AlertState.PENDING)
            : AlertState.INACTIVE;
        case FIRING   -> firing ? AlertState.FIRING : AlertState.INACTIVE;
    };
}
```

### 8.3 Cardinality Guard (label validation at ingest)

```java
public class CardinalityGuard {
    // labelName → distinct value count
    private final ConcurrentHashMap<String, AtomicLong> labelCardinality = new ConcurrentHashMap<>();
    private static final long MAX_CARDINALITY = 10_000;

    public boolean allow(String labelName, String labelValue) {
        // HyperLogLog would be more memory-efficient at scale
        long count = labelCardinality
            .computeIfAbsent(labelName, k -> new AtomicLong(0))
            .incrementAndGet();
        if (count > MAX_CARDINALITY) {
            log.warn("Cardinality explosion: label={} count={}", labelName, count);
            return false; // drop this metric
        }
        return true;
    }
}
```

---

## 9. Quick Revision

- **Pull model** (Prometheus): collector scrapes `/metrics`; dead services are immediately detectable
- **Gorilla compression**: delta-of-delta timestamps + XOR values → 1.37 bytes/point
- **Cardinality explosion**: unbounded label (user_id) × metrics count → OOM; enforce limits at ingest
- **2h chunk**: Prometheus TSDB unit; sealed → uploaded to S3 by Thanos sidecar
- **Alert pending period**: prevents flapping; must stay firing for N minutes before notification
- **Thanos**: global query view across multiple Prometheus; blocks stored in S3
- **Cortex/M3**: multi-tenant, horizontally scalable Prometheus replacement for large orgs

---

## 10. See Also

- `04-advanced-topics/distributed-concepts.md` — CAP, eventual consistency
- `02-building-blocks/message-brokers.md` — async pipeline for metric ingestion
- `05-hld-problems/03-hard/distributed-job-scheduler.md` — alert rule evaluation scheduling

---

## 11. Interview Questions Asked

1. **Google/Meta**: "Design a metrics system for 10,000 microservices. How do you handle 1M data points/sec ingestion?"
2. **Stripe**: "How does Prometheus TSDB compression work? Why can't you use a regular relational DB?"
3. **Netflix**: "Pull vs push model for metrics collection — when would you choose each?"
4. **Datadog**: "A metric has a label `user_id`. Why is this a problem? How do you fix it?"
5. **Amazon**: "Your alert fires 5,000 times during a single incident. How does Alertmanager collapse this?"
6. **Uber**: "How do you store 10 years of metrics cost-effectively? Describe the storage tiers."
7. **LinkedIn**: "How do you shard Prometheus when a single instance can't hold all time series?"
8. **Cloudflare**: "How would you design a global view query across 50 regional Prometheus instances?"
