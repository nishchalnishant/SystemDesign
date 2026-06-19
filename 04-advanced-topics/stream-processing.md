---
module: 04-advanced-topics
topic: Stream Processing
status: unread
tags: [04-advanced-topics, system-design, stream-processing, flink, kafka]
---
# Stream Processing

## Why Stream Processing?

Batch processing runs every N hours. Stream processing answers the question in real time.

| Use case | Batch answer | Stream answer |
|---|---|---|
| Fraud detection | Report yesterday's fraud | Alert within 500ms of suspicious transaction |
| Recommendation refresh | Retrain daily | Update recommendations as user browses |
| Metrics dashboard | Charts from 1-hour-old data | Live charts with 1-second lag |
| Inventory alerts | Email at end of day | Alert when item drops below threshold |

**Core invariant:** Any analytics that drives an operational decision (block a transaction, send an alert, update a UI) cannot tolerate hours-old data. Stream processing gives you batch-like semantics on unbounded, continuously arriving data.

---

## Processing Models

### Batch vs Micro-Batch vs Streaming

```
Batch:
  collect data over period P → process all at once → output results
  Latency: O(P) — hours to days
  Examples: Hadoop MapReduce, Hive, Spark SQL on historical data

Micro-Batch:
  collect data over small P (1–30 seconds) → process mini-batch → output
  Latency: O(P) — seconds
  Examples: Spark Streaming (old DStream API), Spark Structured Streaming
  Trade-off: simpler fault tolerance (redo a batch); 10–30s latency floor

True Streaming:
  process each event as it arrives (or in tiny groups of similar arrival time)
  Latency: milliseconds
  Examples: Apache Flink, Apache Kafka Streams, Apache Storm
  Trade-off: exactly-once is harder; more complex state management
```

**When to choose which:**

| Need | Model |
|---|---|
| < 1 second latency | True streaming (Flink, Kafka Streams) |
| 1–30 second latency is acceptable, simpler ops | Micro-batch (Spark Structured Streaming) |
| Historical reprocessing or large batch joins | Batch (Spark on S3/HDFS) |
| Same code for both historical + live | Unified model (Flink, Spark Structured Streaming) |

---

## Window Types

Windows are how you group unbounded stream events into finite, processable chunks.

### Tumbling Window

```
Fixed-size, non-overlapping windows. Every event belongs to exactly one window.

Events: e1(t=1), e2(t=3), e3(t=7), e4(t=9), e5(t=12)
Window size: 5 seconds

[0, 5)   → e1, e2
[5, 10)  → e3, e4
[10, 15) → e5

Use cases:
  - "Count page views per 5-minute interval"
  - "Sum revenue per hour"
  - "Average CPU per 1-minute bucket"

Implementation (Flink):
  stream
    .keyBy(event -> event.userId)
    .window(TumblingEventTimeWindows.of(Time.minutes(5)))
    .aggregate(new CountAggregator())
```

### Sliding Window

```
Fixed-size, overlapping windows. An event can belong to multiple windows.
Defined by: window size + slide interval.

Events: e1(t=1), e2(t=3), e3(t=7)
Window size: 10s, slide: 5s

[0, 10)   → e1, e2, e3
[5, 15)   → e3
[10, 20)  → (nothing yet)

Use cases:
  - "Compute 10-minute rolling average, updated every 5 minutes"
  - "Detect if any 10-second window has > 100 failed logins"
  - Moving averages in financial dashboards

Implementation (Flink):
  stream
    .keyBy(event -> event.userId)
    .window(SlidingEventTimeWindows.of(Time.minutes(10), Time.minutes(5)))
    .aggregate(new AverageAggregator())
```

### Session Window

```
Variable-size windows based on activity gaps. Window ends when no event arrives
within gap duration.

Events: e1(t=1), e2(t=3), e3(t=15), e4(t=20), e5(t=40)
Gap timeout: 5 seconds

Session 1: e1(t=1), e2(t=3)          → gap to e3 is 12s > 5s, window closes
Session 2: e3(t=15), e4(t=20)        → gap to e5 is 20s > 5s, window closes
Session 3: e5(t=40)

Use cases:
  - User session analytics: group events by user activity periods
  - Detecting burst patterns (e.g. DDoS bursts)
  - E-commerce: group events in a shopping session for cart analysis

Implementation (Flink):
  stream
    .keyBy(event -> event.userId)
    .window(EventTimeSessionWindows.withGap(Time.seconds(30)))
    .aggregate(new SessionAggregator())
```

### Window Summary

| Window Type | Size | Overlap | Event Membership | Best For |
|---|---|---|---|---|
| Tumbling | Fixed | None | Exactly 1 window | Periodic aggregation (hourly counts) |
| Sliding | Fixed | Yes | Multiple windows | Rolling averages, anomaly detection |
| Session | Variable | None | Exactly 1 session | User behavior grouping |

---

## Time Semantics

### Event Time vs Processing Time vs Ingestion Time

```
Event Time:     when the event actually happened (embedded in event payload)
Ingestion Time: when the event arrived at the message broker (Kafka timestamp)
Processing Time: when the stream processor processes the event (wall clock)

Example: mobile app offline for 10 minutes, then syncs 500 events in a burst.
  - Event times span 10 minutes ago to now
  - Processing time: all 500 events arrive NOW (burst)

If using Processing Time:
  All 500 events land in the CURRENT window → skewed counts, wrong aggregation

If using Event Time:
  Events correctly placed in the windows they belong to 10 minutes ago → correct results
  But: you now need to wait for late events before closing a window
```

**Rule:** Always use event time for correctness. Use processing time only when absolute latency is more important than accuracy (e.g., live dashboards where 2-minute-old data is fine).

---

## Watermarks

Watermarks are the mechanism that tells a stream processor: "you have seen enough events — windows with end time before this watermark can be safely computed and closed."

### The Problem Watermarks Solve

```
Events arrive out of order over a network. Event at t=100 may arrive after event at t=105.
How long should we wait before we close the [t=90, t=100) window?

Option 1: Wait forever — correct, but unbounded latency
Option 2: Close immediately — fast, but miss late events
Option 3: Wait W seconds (watermark strategy) — trade-off between completeness and latency
```

### Watermark Strategy

```
Watermark W(t) = max_event_time_seen - allowed_lateness

Example: allowed_lateness = 5 seconds
  Events arrive with max event time = 100
  Watermark = 100 - 5 = 95

  Any window ending before t=95 can now be closed and results emitted.
  Events arriving with event_time < 95 are considered "late."

Flink BoundedOutOfOrdernessWatermarks:
  WatermarkStrategy
    .<MyEvent>forBoundedOutOfOrderness(Duration.ofSeconds(5))
    .withTimestampAssigner((event, ts) -> event.getEventTime())
```

### Late Event Handling

```
Three strategies for events that arrive after the window has already been computed:

1. Drop (default): late events are ignored; window result is final
   Use when: small late fraction, latency matters more than completeness

2. Update (allowed lateness): window stays open for extra duration, recomputes on late events
   Flink: .allowedLateness(Time.seconds(30))
   Emits: initial result + updated result if late events arrive
   Use when: downstream system can handle result updates (idempotent sink)

3. Side output: late events go to a separate stream for later reprocessing
   Flink:
     OutputTag<Event> lateTag = new OutputTag<Event>("late-events"){};
     SingleOutputStreamOperator<Result> main = stream
       .window(...)
       .sideOutputLateData(lateTag)
       .aggregate(...);
     DataStream<Event> lateStream = main.getSideOutput(lateTag);
   Use when: you want late events handled differently (audit log, batch backfill)
```

---

## Exactly-Once Semantics

This is the interview crux for stream processing. "At-least-once" is easy — "exactly-once" requires deliberate design.

### The Three Failure Modes

```
At-most-once:   process event, then ack. Crash before ack → event dropped.
At-least-once:  ack event, then process. Crash mid-process → event reprocessed → duplicates.
Exactly-once:   process event and advance offset atomically → no drops, no duplicates.
```

### Flink's Approach: Distributed Snapshots (Chandy-Lamport)

```
Flink periodically injects "checkpoint barriers" into the event stream.
When an operator receives a barrier, it:
  1. Saves its current state to durable storage (S3/HDFS)
  2. Passes the barrier downstream

When ALL operators have saved state for barrier N:
  The checkpoint is complete. Kafka offsets are committed.

On failure:
  Flink restores all operators to checkpoint N state.
  Kafka offset is rewound to checkpoint N position.
  Replay resumes from that position — no data lost, no duplicates from before checkpoint.
```

```
Kafka:   ---[e1][e2][e3]|barrier|[e4][e5][e6]|barrier|---
                              ↑                      ↑
                         checkpoint N           checkpoint N+1

Failure after N+1 completes → restore to N+1 → replay from N+1 offset
Failure before N+1 completes → restore to N → replay from N offset
```

**Checkpoint interval trade-off:**
- Short interval (1s): fast recovery, high state management overhead
- Long interval (5min): slow recovery, low overhead
- Production default: 1–5 minutes

### Idempotent Sinks

Checkpointing prevents processing an event twice, but what about the output? If a job crashes after writing to the DB but before committing the checkpoint, the write gets replayed.

**Two strategies:**

```
1. Idempotent writes:
   Use upsert instead of insert: INSERT ... ON CONFLICT DO UPDATE
   Write includes the event ID / offset → replay produces same result
   Limitation: only works if output is deterministic (same input → same output)

2. Two-phase commit (2PC) with transactional sink:
   Flink's TwoPhaseCommitSinkFunction:
     preCommit():  write to sink in a transaction, do NOT commit yet
     commit():     on checkpoint completion, commit the sink transaction
     abort():      on checkpoint failure, roll back the transaction
   Supported by: Kafka sink (transactions), PostgreSQL (JDBC), Iceberg

   Kafka exactly-once producer:
     producer.initTransactions()
     producer.beginTransaction()
     ... produce messages ...
     producer.commitTransaction()  ← only on Flink checkpoint complete
```

### Kafka Streams Exactly-Once

```
Kafka Streams uses a different approach: all in one Kafka transaction.
  - Source offsets + state changelog + output records committed atomically
  - Enable: StreamsConfig.PROCESSING_GUARANTEE_CONFIG = "exactly_once_v2"
  - Overhead: ~20% throughput reduction vs at-least-once
```

---

## State Management in Stream Processing

### Managed State (Flink)

```
ValueState<T>:        single value per key
ListState<T>:         list of values per key
MapState<K, V>:       map per key
ReducingState<T>:     running aggregate (sum, count)
AggregatingState<I,O>: custom accumulator

State is keyed → partitioned across task managers by key
State backends:
  MemoryStateBackend:  fast, limited by JVM heap, not for production
  FsStateBackend:      state in JVM, checkpoint to HDFS/S3
  RocksDBStateBackend: state in RocksDB (off-heap), checkpoint to HDFS/S3
                       Recommended for large state (> a few GB)
```

### State TTL (Time-To-Live)

```
State accumulates unboundedly without TTL — a common production issue.
Always set TTL for keyed state that doesn't need indefinite history.

StateTtlConfig ttl = StateTtlConfig
  .newBuilder(Time.days(7))
  .setUpdateType(StateTtlConfig.UpdateType.OnCreateAndWrite)
  .setStateVisibility(StateTtlConfig.StateVisibility.NeverReturnExpired)
  .build();

ValueStateDescriptor<Long> descriptor =
  new ValueStateDescriptor<>("count", Long.class);
descriptor.enableTimeToLive(ttl);
```

---

## Flink vs Spark Structured Streaming vs Kafka Streams

| Dimension | Apache Flink | Spark Structured Streaming | Kafka Streams |
|---|---|---|---|
| Latency | Milliseconds | Seconds (micro-batch) | Milliseconds |
| State size | Unlimited (RocksDB) | Memory + disk | Limited (RocksDB) |
| Exactly-once | Yes (checkpointing + 2PC) | Yes (micro-batch re-execution) | Yes (Kafka transactions) |
| SQL support | Flink SQL (good) | SparkSQL (excellent) | Limited (KSQL via Confluent) |
| Deployment | Cluster (k8s, YARN) | Spark cluster | Library (no cluster) |
| Unified batch + stream | Yes (DataStream + Table API) | Yes (same API) | No (streams only) |
| Best for | Complex event processing, CEP, large state | Heavy SQL analytics, existing Spark | Simple transformations within Kafka ecosystem |

**Recommendation:**
- Large state, complex logic, millisecond latency → **Flink**
- SQL-heavy analytics on streams + existing Spark infra → **Spark Structured Streaming**
- Simple filter/transform/join in Kafka ecosystem, embedded in Java app → **Kafka Streams**

---

## Common Patterns

### Pattern 1: Stateful Deduplication

```
Problem: same event arrives twice from upstream source (retry, network hiccup)
Solution: Flink keyed state stores "seen event IDs" with TTL

class DeduplicateFunction extends KeyedProcessFunction<String, Event, Event> {
  ValueState<Boolean> seenState;

  void open(...) {
    seenState = getRuntimeContext().getState(
      new ValueStateDescriptor<>("seen", Boolean.class));
  }

  void processElement(Event event, Context ctx, Collector<Event> out) {
    if (seenState.value() == null) {
      seenState.update(true);
      out.collect(event);
    }
    // else: duplicate, drop silently
  }
}
```

### Pattern 2: Stream-Stream Join (Order + Payment matching)

```
Problem: match order events with payment events (arrive at different times)
Solution: Flink interval join with bounded time window

ordersStream
  .keyBy(order -> order.orderId)
  .intervalJoin(paymentsStream.keyBy(payment -> payment.orderId))
  .between(Time.seconds(-5), Time.seconds(30))  // payment arrives 0–30s after order
  .process(new OrderPaymentJoinFunction())

Result: OrderWithPayment for each matched pair within the time bound
```

### Pattern 3: Aggregation with Late Event Update

```
// Emit preliminary result, then update when late events arrive
stream
  .keyBy(event -> event.productId)
  .window(TumblingEventTimeWindows.of(Time.minutes(1)))
  .allowedLateness(Time.seconds(30))
  .sideOutputLateData(lateTag)
  .aggregate(new RevenueAggregator())
  // First emission: preliminary result at window close
  // Second emission: updated result when late events arrive within 30s
```

---

## Architecture: Real-Time Analytics Pipeline

```
                            ┌──────────────────────────┐
                            │   Event Sources           │
                            │   (apps, services, IoT)   │
                            └────────────┬─────────────┘
                                         │
                            ┌────────────▼─────────────┐
                            │     Apache Kafka          │
                            │   (event backbone)        │
                            │   topics: clicks, orders, │
                            │   payments, errors        │
                            └────────────┬─────────────┘
                                         │
                            ┌────────────▼─────────────┐
                            │    Apache Flink           │
                            │  ┌──────────────────────┐│
                            │  │ Source (Kafka)        ││
                            │  │ ↓                     ││
                            │  │ Watermark Assignment  ││
                            │  │ ↓                     ││
                            │  │ KeyBy (userId)        ││
                            │  │ ↓                     ││
                            │  │ Window (5min tumbling)││
                            │  │ ↓                     ││
                            │  │ Aggregate (count/sum) ││
                            │  │ ↓                     ││
                            │  │ Sink                  ││
                            │  └──────────────────────┘│
                            └────────────┬─────────────┘
                                         │
                    ┌────────────────────┼──────────────────────┐
                    │                    │                       │
         ┌──────────▼──────┐  ┌──────────▼──────┐   ┌──────────▼──────┐
         │   Kafka Sink    │  │  PostgreSQL/     │   │  Elasticsearch  │
         │ (downstream     │  │  Redis Sink      │   │  (metrics,      │
         │  consumers)     │  │  (results DB)    │   │   alerts)       │
         └─────────────────┘  └─────────────────┘   └─────────────────┘
```

---

## Fault Tolerance Summary

| System | Mechanism | Recovery behavior |
|---|---|---|
| Flink | Distributed snapshots (barriers) | Restore from checkpoint + replay |
| Spark Structured Streaming | Micro-batch re-execution | Redo last incomplete micro-batch |
| Kafka Streams | Kafka transaction + RocksDB changelog | Restore changelog topic |

**Interview answer for "how does exactly-once work in Flink":**
1. Checkpoint barriers divide the stream into epochs
2. Each operator saves state at barrier → stored in S3
3. Kafka sink uses 2PC: pre-commit at barrier, commit when checkpoint completes
4. On failure: restore operator state from last checkpoint, rewind Kafka offset to match
5. Sink transactions from incomplete checkpoints are aborted
6. Resume: operator state matches Kafka offset — zero duplicates, zero drops

---

## Decision Framework

```
Do you need < 1 second latency?
  Yes → True streaming (Flink or Kafka Streams)
    Large state (> 10GB) or complex logic? → Flink
    Simple transforms in Kafka ecosystem? → Kafka Streams
  No →
    1–30 second latency acceptable?
      Yes → Spark Structured Streaming (micro-batch, simpler exactly-once, great SQL)
      No  → Batch (Spark, Hive) — latency doesn't matter, maximize throughput
```

---

## Quick Revision

- **Tumbling**: fixed, non-overlapping — periodic counts
- **Sliding**: fixed, overlapping — rolling averages
- **Session**: variable, gap-based — user activity grouping
- **Watermarks**: how far behind event time to wait before closing a window; `W = max_seen_event_time - allowed_lateness`
- **Exactly-once**: Flink checkpoints (barriers → state snapshots) + idempotent or transactional (2PC) sinks
- **Late events**: drop / allowed lateness (recompute) / side output (reprocess separately)
- **Flink vs Spark**: Flink for millisecond + large state; Spark for SQL + batch-stream unification
- **State TTL**: always set or state grows unboundedly

---

## See Also

- `02-building-blocks/message-brokers.md` — Kafka partitioning, consumer groups (stream source)
- `05-hld-problems/03-hard/real-time-analytics.md` — end-to-end pipeline design
- `04-advanced-topics/event-driven-architecture.md` — event sourcing + streaming integration
- `06-lld/05-problems/26-design-high-contention-counter.md` — concurrent counter in single JVM
