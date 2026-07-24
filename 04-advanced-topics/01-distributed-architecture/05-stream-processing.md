> [!NOTE]
> ** 5-Minute Summary**
>
> **What this covers:** How companies process millions of data points per second in real-time.
>
> **Key topics:**
> - **Batch Processing:** Waiting until the end of the day, gathering all the data into a giant pile, and processing it all at once. (Like waiting to do laundry until Sunday).
> - **Stream Processing:** Processing data the exact millisecond it arrives, one piece at a time. (Like washing every shirt the exact second you take it off).
> - **The Use Case:** Uber matching you with a driver, or a bank blocking a stolen credit card, *must* happen in real-time (Stream Processing). Generating a monthly sales report can happen at midnight (Batch Processing).
> - **Time Windows:** How do you count "Trending Tweets in the last 5 minutes" if the data never stops flowing? You chop the infinite stream into 5-minute chunks (Windows).
>
> **Key takeaway:** Stream Processing (using tools like Apache Flink or Spark Streaming) is what allows modern apps to feel "live" and reactive, instead of making you wait until tomorrow for the database to update.

---
module: 04-advanced-topics
status: unread
tags: [04-advanced-topics, system-design, stream-processing]
---
# Stream Processing - System Design Guide

> This guide explains how to analyze data in real-time using simple analogies.

---

## Why Should I Care?

Imagine you run a massive clothing factory.

**Option 1 (Batch Processing):** You have your workers sew shirts all day long and throw them into a giant pile in the middle of the room. At exactly midnight, the Quality Control inspector comes in, looks at all 10,000 shirts, finds the broken ones, and creates a report.
- *The Problem:* It is cheap and efficient, but you don't find out the sewing machine is broken until midnight! You ruined 10,000 shirts.

**Option 2 (Stream Processing):** You put the Quality Control inspector at the end of a conveyor belt. The exact second a worker finishes a shirt, it rolls down the belt. The inspector looks at it immediately.
- *The Solution:* If a sewing machine breaks, the inspector catches the bad shirt in 2 seconds, and yells "Stop the machine!" You only ruined 1 shirt.

In System Design, this is the difference between Hadoop (Batch) and Apache Flink (Stream).
If a hacker steals a credit card and starts spending money, the bank cannot wait until a midnight "Batch Job" to run the fraud-detection algorithm. The bank will lose millions of dollars. The bank MUST use Stream Processing to analyze the swipe the exact millisecond it happens, and block the card instantly.

---

## The Infinite Conveyor Belt (Kafka + Flink)

How do you actually build a Stream Processing system? It requires two pieces of technology working perfectly together.

1. **The Conveyor Belt (Apache Kafka):** You need a system that can absorb 1 million events per second (button clicks, GPS locations, credit card swipes) and line them up perfectly without dropping any.
2. **The Inspector (Apache Flink / Spark Streaming):** You need a system that reads the events off the conveyor belt, does complex math on them instantly, and saves the result to a database.

**Real World Example (Uber):**
Your phone sends your GPS location to Kafka every 1 second.
The Uber Stream Processor reads that GPS location, reads the driver's GPS location, calculates the distance, and updates the "ETA: 4 Minutes" text on your screen, all in 50 milliseconds.

---

## ⏱ Windowing (Chopping up Infinity)

Stream processing has one massive mathematical problem.

If you do a Batch Job at midnight, you can ask the database: "How many shirts did we make today?" The database counts 10,000, and gives you the answer. It is a finite pile of data.

But a Stream is an *infinite* conveyor belt that never stops.
If you ask a Stream Processor: "What is the most popular Hashtag on Twitter right now?", it cannot answer. Because it hasn't seen all the tweets yet! More tweets are arriving right now!

To fix this, Stream Processors use **Time Windows**. They chop infinity into tiny boxes.

- **Tumbling Window:** The processor puts a box on the belt for exactly 5 minutes. Every tweet goes in the box. At 5:05 PM, the box closes, it counts the hashtags, and updates the "Trending" page. Then it opens a brand new, empty box for the next 5 minutes.
- **Sliding Window:** The processor updates the "Trending" page every 1 single second, by looking at all tweets from *the last 60 seconds*. (This requires much more RAM, but feels incredibly smooth and "live" to the user).

---

## Interview Questions to Practice

1. **"What is the difference between Batch Processing and Stream Processing?"**
   *Answer:* Batch processing handles large, bounded datasets at rest, typically running on a schedule (e.g., nightly Hadoop jobs). It focuses on high throughput and efficiency. Stream processing handles unbounded, continuous data in motion (e.g., Kafka + Flink). It processes events as they arrive, focusing on real-time analysis and ultra-low latency.
2. **"Why do Stream Processors need 'Windows'?"**
   *Answer:* Because data streams are infinite/unbounded, you cannot perform aggregate functions (like SUM, COUNT, or AVERAGE) on the "entire" dataset. Windows chop the infinite stream into finite, time-based chunks (e.g., "the last 5 minutes") so the processor can calculate an aggregate result and output it.
3. **"In Stream Processing, what is the difference between 'Event Time' and 'Processing Time'?"**
   *Answer:* 'Event Time' is the exact timestamp when the event actually occurred on the user's device (e.g., 12:00 PM). 'Processing Time' is the timestamp when the server finally received and processed the event (e.g., 12:05 PM). Because mobile phones can lose signal, events often arrive out of order or delayed. Good stream processors (like Flink) group data using 'Event Time' to ensure accuracy, even if the data arrives late.

---

# 🎯 SDE-3 Deep Dive

Windows + event-vs-processing time is the intro. Seniors are probed on **watermarks and late data, the delivery/consistency guarantees (exactly-once via checkpointing), stateful stream state management, and the architecture debates (Lambda vs Kappa, Flink vs Kafka Streams).**

## Watermarks — how a stream decides a window is "done"

Event-time processing has a hard problem: if events arrive late/out-of-order, *when* do you close a 12:00–12:05 window and emit its result? You can't wait forever. The answer is **watermarks**: a moving assertion "I believe I've now seen all events up to time T." When the watermark passes a window's end, the window fires.

- **Late data** (an event with timestamp < current watermark) is the crux: options are **drop it**, route it to a **side output** for reconciliation, or keep the window open with **allowed lateness** and emit an updated result. Naming this trade is the senior signal.
- Watermarks trade **latency vs completeness**: aggressive watermarks fire fast but risk missing stragglers; conservative ones are accurate but add delay. This is the same completeness/latency tension as everything else in distributed systems.

## Exactly-once — and what it actually means

"Exactly-once" is a myth at the *delivery* layer (two-generals, [`01-distributed-systems.md`](01-distributed-systems.md)). What Flink/Kafka Streams give is **exactly-once *processing* semantics**: at-least-once delivery + idempotent state updates gated by **checkpoints**.

- **Flink's mechanism:** periodic **distributed snapshots** (Chandy–Lamport barriers flow through the dataflow) capture operator state + input offsets atomically. On failure it rewinds *both* state and source offsets to the last consistent checkpoint and replays — so effects land once.
- For end-to-end exactly-once, the **sink must be transactional or idempotent** (two-phase commit to Kafka/DB); otherwise you get exactly-once *state* but at-least-once *output*. This is the part people miss.

## State is the hard part

Aggregations, joins, and windows are **stateful** — the operator must remember counts/buffers across events. At scale that state is huge:

- State lives in an embedded store (Flink's **RocksDB** state backend), keyed and **partitioned by key** so it scales horizontally and is checkpointed to durable storage (S3/HDFS).
- **Stream–stream joins** need bounded state (a time window) or the state grows forever — you can't join two infinite streams without a windowing constraint.
- Rescaling a stateful job requires **redistributing keyed state** across new parallelism — non-trivial, which is why stateful streaming is operationally heavier than stateless.

## The architecture debates

- **Lambda vs Kappa:** *Lambda* runs a batch layer (accurate, slow) alongside a speed layer (fast, approximate) and merges — but you maintain **two codebases**. *Kappa* says: just make the stream layer replayable (reprocess history from Kafka) and drop the batch layer. Kappa wins when your stream engine can reprocess; Lambda persists where batch tooling is entrenched.
- **Flink vs Kafka Streams vs Spark Structured Streaming:** Flink is true event-at-a-time, lowest latency, richest windowing/state; Kafka Streams is a *library* (no cluster) tightly coupled to Kafka, great for simpler per-record apps; Spark Structured Streaming is micro-batch (slightly higher latency, unifies with batch/ML). Match the tool to latency + ops tolerance.
- Feeds naturally from **CDC** ([`03-change-data-capture.md`](03-change-data-capture.md)) and pairs with the **outbox pattern** ([`07-outbox-cdc-pattern.md`](07-outbox-cdc-pattern.md)) as the event source.

## Interview probes you should survive

- *"How does a window know all its events have arrived?"* → It doesn't, exactly — a watermark asserts progress up to time T and fires the window when it passes; late events are dropped, side-outputted, or handled via allowed lateness.
- *"Flink claims exactly-once — how, given delivery can't be exactly-once?"* → Exactly-once *processing*: distributed checkpoints snapshot operator state + source offsets atomically; on failure it rewinds both and replays. End-to-end needs a transactional/idempotent sink.
- *"Where does windowed aggregation state live and how does it scale?"* → In a keyed, partitioned embedded store (e.g., RocksDB), checkpointed to durable storage; it scales by key partitioning.
- *"Lambda vs Kappa — which and why?"* → Kappa (single replayable stream codebase) if your engine can reprocess history; Lambda only where a separate batch layer is already justified — at the cost of dual code paths.
- *"Why can't you naively join two streams?"* → Unbounded state; you must window the join or key state grows without limit.

---

## Applied In

This concept is used by **3 problems** in this repo:

**High-Level Design**

- [Design YouTube](../../05-hld-problems/02-medium/youtube.md)
- [Design an Ad Click Aggregator](../../05-hld-problems/03-hard/ad-click-aggregator.md)
- [Design a Metrics Monitoring System (Prometheus + Grafana)](../../05-hld-problems/03-hard/metrics-monitoring-system.md)

