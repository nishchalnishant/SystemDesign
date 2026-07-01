---
module: 04-advanced-topics
topic: internals
status: unread
tags: [04-advanced-topics, distributed-systems, stream-processing, batch-processing, flink, spark, kafka-streams]
---
# Stream Processing vs Batch Processing

Two fundamental paradigms for data processing at scale. The right choice depends on latency requirements, data volume, and whether your data arrives continuously or in discrete chunks.

For a deeper look at stream processing internals (watermarks, windows, state backends), see `04-advanced-topics/01-distributed-architecture/05-stream-processing.md`.

---

## The Core Distinction

**Batch processing**: Process a bounded, static dataset. Read everything, compute a result, write it out. Start, finish, done.

**Stream processing**: Process an unbounded, continuously arriving sequence of events. The computation never "finishes" — it runs indefinitely, maintaining state and emitting results continuously.

```
Batch:   [────────────────── dataset ─────────────────────] → run → result
Stream:  ────event──event──event──event──event──────▶ (never ends)
                                                    continuous results
```

---

## Batch Processing

### Model

1. Data lands in a file system (HDFS, S3, GCS)
2. A job reads the entire dataset, processes it, writes output
3. Job completes and exits

### When Data Is Ready

Batch jobs run on a **schedule** (cron) because data doesn't arrive instantaneously. A nightly ETL job might run at 2 AM after all logs for the day have been written.

### Apache Spark

The dominant batch processing framework. Processes data in-memory across a cluster, using a DAG (Directed Acyclic Graph) of transformations.

```python
# PySpark: compute daily revenue by product category
from pyspark.sql import SparkSession
from pyspark.sql.functions import sum, col

spark = SparkSession.builder.appName("daily-revenue").getOrCreate()

orders = spark.read.parquet("s3://data-lake/orders/date=2026-06-30/")
products = spark.read.parquet("s3://data-lake/products/")

result = (
    orders
    .join(products, "product_id")
    .groupBy("category")
    .agg(sum("total_price").alias("revenue"))
    .orderBy(col("revenue").desc())
)

result.write.mode("overwrite").parquet("s3://data-lake/reports/revenue/date=2026-06-30/")
spark.stop()
```

**Execution model**: Spark's driver splits the job into tasks distributed across executor nodes. Each executor processes a partition of data in memory. The shuffle (data redistribution between stages — e.g., during `groupBy`) is the main bottleneck.

### Characteristics

| Property | Batch |
|----------|-------|
| **Data completeness** | Full dataset available at start |
| **Latency** | Minutes to hours |
| **Throughput** | Very high (can read petabytes/hour with enough nodes) |
| **Fault tolerance** | Rerun the failed job or stage (idempotent writes) |
| **Complexity** | Relatively simple: no state management during the job |
| **Cost** | Cheap: spin up cluster, run job, spin down; pay only for run time |

### Batch Use Cases

- **Nightly ETL**: Move raw transactional data from OLTP (PostgreSQL) to OLAP (Snowflake/BigQuery) for analytics
- **ML model training**: Feature engineering + model fit on a historical dataset
- **Monthly billing**: Aggregate all usage records for the month, compute invoice
- **Compliance reports**: Generate regulatory reports on a quarterly basis
- **Historical backfill**: Reprocess all historical data after a bug fix or schema change

---

## Stream Processing

### Model

1. Events arrive in a message broker (Kafka, Kinesis, Pub/Sub)
2. A streaming job reads events in real time as they arrive
3. Maintains state (counts, aggregates, joins) across events
4. Emits results continuously or on trigger (window close)

### Apache Flink

The dominant stream processing framework. Processes events in true real-time with low latency (sub-second to seconds), exactly-once semantics, and rich windowing.

```python
# PyFlink: compute rolling 5-minute revenue per category
from pyflink.datastream import StreamExecutionEnvironment
from pyflink.table import StreamTableEnvironment, EnvironmentSettings

env = StreamExecutionEnvironment.get_execution_environment()
t_env = StreamTableEnvironment.create(env)

t_env.execute_sql("""
  CREATE TABLE orders (
    order_id    STRING,
    category    STRING,
    total_price DOUBLE,
    event_time  TIMESTAMP(3),
    WATERMARK FOR event_time AS event_time - INTERVAL '5' SECOND
  ) WITH (
    'connector' = 'kafka',
    'topic' = 'orders',
    'properties.bootstrap.servers' = 'kafka:9092',
    'format' = 'json'
  )
""")

t_env.execute_sql("""
  CREATE TABLE revenue_sink (
    window_start TIMESTAMP(3),
    window_end   TIMESTAMP(3),
    category     STRING,
    revenue      DOUBLE
  ) WITH (
    'connector' = 'jdbc',
    'url' = 'jdbc:postgresql://postgres:5432/analytics',
    'table-name' = 'category_revenue'
  )
""")

t_env.execute_sql("""
  INSERT INTO revenue_sink
  SELECT
    TUMBLE_START(event_time, INTERVAL '5' MINUTE) AS window_start,
    TUMBLE_END(event_time, INTERVAL '5' MINUTE)   AS window_end,
    category,
    SUM(total_price) AS revenue
  FROM orders
  GROUP BY TUMBLE(event_time, INTERVAL '5' MINUTE), category
""")
```

**Execution model**: Flink runs as a long-lived cluster. The job graph (sources → operators → sinks) is deployed and runs continuously. Each operator maintains local state in a key-value store (RocksDB) that is periodically checkpointed to durable storage (HDFS/S3) for fault tolerance.

### Characteristics

| Property | Stream |
|----------|--------|
| **Data completeness** | Incomplete by definition (events still arriving) |
| **Latency** | Milliseconds to seconds |
| **Throughput** | High, but limited by state management overhead |
| **Fault tolerance** | Checkpointing: restart from last checkpoint, replay from Kafka |
| **Complexity** | High: windowing, watermarks, late data, state eviction |
| **Cost** | Higher: cluster runs 24/7; even during quiet periods |

### Stream Use Cases

- **Fraud detection**: Score every transaction within 100ms using the user's recent activity
- **Real-time dashboards**: Show the last 60 seconds of requests/sec on an ops dashboard
- **Alerting**: Alert within 30 seconds if error rate exceeds 1%
- **Live leaderboards**: Update game rankings as scores come in
- **Event-driven microservices**: Each service reacts to events from Kafka in near-real-time
- **Surge pricing**: Compute Uber surge multiplier every 60 seconds per geo cell

---

## Micro-Batch: The Middle Ground

Spark Structured Streaming is the most common example: the engine processes incoming data in small batches (every 100ms, every 1 second, every 5 minutes), not one event at a time.

```python
# Spark Structured Streaming: 1-minute micro-batches
from pyspark.sql import SparkSession
from pyspark.sql.functions import window, sum, col

spark = SparkSession.builder.appName("streaming-revenue").getOrCreate()

orders = (
    spark.readStream
    .format("kafka")
    .option("kafka.bootstrap.servers", "kafka:9092")
    .option("subscribe", "orders")
    .load()
)

result = (
    orders
    .groupBy(window(col("event_time"), "5 minutes"), col("category"))
    .agg(sum("total_price").alias("revenue"))
)

(
    result.writeStream
    .outputMode("update")
    .format("console")
    .trigger(processingTime="1 minute")  # micro-batch every 1 minute
    .start()
    .awaitTermination()
)
```

### Characteristics

| Property | Micro-Batch (Spark SS) |
|----------|------------------------|
| **Latency** | Seconds to low-minutes (one trigger interval) |
| **Throughput** | Very high (batch efficiency within each micro-batch) |
| **API** | Same as batch Spark — easy to learn for Spark users |
| **State management** | Built-in stateful aggregation with checkpointing |
| **Use when** | Latency of seconds is acceptable; team knows Spark; high throughput needed |

**When to prefer Flink over Spark SS**: Flink achieves true event-at-a-time processing with sub-second latency. Spark SS has a floor of ~100ms trigger interval. For fraud detection (must respond in < 500ms), use Flink. For a 1-minute rolling dashboard, either works.

---

## Windowing

Windowing defines how to group events in time for aggregations. This only applies to stream and micro-batch processing.

### Tumbling Window

Non-overlapping, fixed-size time buckets. Every event belongs to exactly one window.

```
Window 1: [12:00:00 – 12:04:59]
Window 2: [12:05:00 – 12:09:59]
Window 3: [12:10:00 – 12:14:59]
```

**Use case**: "Revenue per 5-minute period" for a time-series chart. Each window closes and emits a single aggregate.

### Sliding Window

Overlapping windows. Each event may belong to multiple windows.

```
Window 1: [12:00:00 – 12:04:59]
Window 2: [12:01:00 – 12:05:59]
Window 3: [12:02:00 – 12:06:59]
...  (every 1 minute, 5-minute size)
```

**Use case**: "Moving average of requests/sec over the last 5 minutes, updated every minute." More computation cost (events processed multiple times), smoother output.

### Session Window

Gaps-based. A session window opens on the first event and closes after a configured gap of inactivity.

```
User login at 12:00, activity until 12:08, gap > 30 min, login at 13:00 → two sessions
Session 1: [12:00:00 – 12:08:xx]
Session 2: [13:00:00 – 13:xx:xx]
```

**Use case**: User session analytics, fraud detection based on activity bursts.

---

## Watermarks and Late Data

In stream processing, events may arrive late (mobile app sends events when it regains connectivity, network delay). A **watermark** is a heuristic that says "events with timestamp ≤ T have all arrived; windows ending before T can now be closed and emitted."

```
Events arriving:              t=5, t=3, t=8, t=6, t=12, t=9, ...
Watermark (lag 2 seconds):    after t=8 arrives → watermark = 6
                              → window [0–5] can be emitted now
```

**Late events**: Events that arrive after the watermark has passed their window are either dropped or handled by updating the window (Flink `allowedLateness`). Dropping is simpler; updating is correct but requires downstream consumers to handle updates to already-emitted results.

---

## Decision Guide

```
What is the acceptable latency for results?
│
├── < 1 second → True stream processing (Flink, Kafka Streams)
│
├── 1 second – 1 minute → Micro-batch (Spark Structured Streaming, Flink)
│
├── 1 minute – 1 hour → Micro-batch or light batch
│
└── > 1 hour → Batch (Spark, Hive, Trino)

Is the dataset bounded?
│
├── No (continuous events) → Stream or micro-batch
└── Yes (historical file/table) → Batch

Does correctness require full data completeness?
│
├── Yes (ML training, compliance) → Batch only
└── No (dashboards, alerts, recommendations) → Stream acceptable

Is there existing Spark expertise on the team?
│
├── Yes → Spark Structured Streaming for streaming, Spark batch for batch (consistent API)
└── No → Flink for sub-second, Spark for batch
```

---

## Lambda Architecture (Historical)

Before Spark Structured Streaming and Flink matured, teams ran two separate pipelines:

```
                    ┌─── Batch layer (Hadoop/Spark) ─── batch views ───────┐
Raw events ─────────┤                                                        ├─▶ serving layer → queries
                    └─── Speed layer (Storm/early Spark) ─ real-time views ─┘
```

**Problems with Lambda**:
- Two codebases for the same logic: batch and stream. They drift.
- Results from the two layers must be merged at query time.
- Operational complexity: maintain two completely different clusters.

**Kappa Architecture** replaced it: run only the stream layer. For historical backfill, replay Kafka from offset 0. One codebase, one pipeline.

```
Kafka (long retention, e.g., 30 days)
       │
       └─── Stream processing (Flink) ─▶ serving layer → queries
               ↑
            Replay from offset 0 for historical backfill
```

Most teams building new systems today use Kappa or a variation of it.

---

## Production Reference Points

| System | Paradigm | Framework | Why |
|--------|----------|-----------|-----|
| Uber surge pricing | Stream | Flink | 1-minute windows per H3 cell; sub-minute latency required |
| Netflix data warehouse | Batch | Spark | Daily ETL from event logs to Iceberg tables in S3 |
| LinkedIn feed ranking | Micro-batch | Spark SS | 30-second refresh cadence acceptable |
| Cloudflare DDoS detection | Stream | Kafka Streams | Sub-100ms response to traffic anomalies |
| Stripe fraud detection | Stream | Flink | Must score transaction before auth (< 500ms) |
| Airbnb pricing ML features | Batch | Spark | Daily feature engineering for ML model inputs |
| Twitter trending topics | Micro-batch | Spark SS | 5-minute tumbling windows; trending = top hashtags per window |

---

## Interview Questions to Practice

1. **"When would you use stream processing over batch?"**
   *Use stream processing when the latency requirement is under a minute and results need to be continuously updated — fraud scoring, real-time alerting, live dashboards, event-driven microservices. Use batch when the dataset is bounded, results can wait hours, or correctness requires processing all data (ML training, compliance reports, monthly billing). Most systems need both: stream for operational decisions, batch for historical analysis.*

2. **"A Flink job processes payments. What happens if Flink crashes?"**
   *Flink checkpoints its state (counters, aggregates, window buffers) to durable storage (HDFS/S3) every N seconds. On restart, Flink reads the last checkpoint, restores operator state, and resets Kafka consumer offsets to the position at checkpoint time. Events between the checkpoint and the crash are replayed from Kafka. With exactly-once mode (Kafka transactions + idempotent sinks), no event is counted twice. With at-least-once mode, replayed events may be processed more than once.*

3. **"What is a watermark in stream processing?"**
   *A watermark is a progress marker that tells the stream processor what time "now" is, accounting for event ordering delays. It says "I've seen all events up to time T — it's safe to close and emit any windows ending before T." The watermark typically lags the maximum observed event timestamp by a configurable amount (e.g., 5 seconds), allowing late events to arrive. Events later than the watermark by more than the allowed lateness are dropped or handled separately. Without watermarks, you'd have to wait forever before closing any window.*

4. **"Your team currently runs a Spark batch job nightly. The business now needs results every 5 minutes. What do you recommend?"**
   *Migrate to Spark Structured Streaming with a 5-minute trigger. The API is nearly identical to the batch Spark code the team already knows — `spark.readStream` instead of `spark.read`, `writeStream` instead of `write`, and a trigger interval. This reuses existing Spark expertise and avoids a full migration to Flink. If the team later needs sub-second latency or more sophisticated windowing, migrate to Flink at that point. Don't over-engineer now.*
