---
module: 07-interview-templates
topic: Amazon HLD Interview Guide
status: unread
tags: [07-interview-templates, amazon, hld, interview]
---
# Amazon HLD Interview — What They Actually Look For

Amazon HLD interviews differ from Google/Meta in emphasis. Understanding the difference shapes how you frame every answer.

---

## What Amazon Weighs Heavily

**1. Operational simplicity over clever architecture**
Amazon runs thousands of services. Interviewers favor designs that are easy to operate, debug, and on-call for — not the most technically impressive. A read replica + Redis cache beats a custom distributed cache every time if it solves the problem.

**2. Failure handling is the design**
Every component must have an answer for: what happens when this fails? If you design a system without mentioning retries, dead letter queues, circuit breakers, or fallbacks, the interviewer will probe until you do. Lead with failure modes proactively.

**3. Cost awareness**
Amazon cares about cost. If you propose synchronous fan-out to 10 services per request, expect "what's the cost of that at scale?" Async via SQS is often preferred precisely because it decouples and can be right-sized independently.

**4. Leadership Principles show up in design choices**
- **Frugality**: "I'd start with a single RDS instance with a read replica before reaching for DynamoDB — we can migrate when we hit the write limit."
- **Bias for Action**: Make a decision, justify it, move on. Don't hedge everything.
- **Dive Deep**: Know one layer below what you propose. If you say DynamoDB, know partition key design. If you say Kafka, know consumer groups and offset commits.
- **Customer Obsession**: Frame requirements from the customer's perspective first — latency, availability, correctness — before talking about infrastructure.

---

## Amazon Service Vocabulary

Use AWS service names. "Object store" is vague; "S3" is precise and signals you've shipped on AWS.

| Need | Say this | Not this |
|------|----------|----------|
| Object/blob storage | S3 | "object store" |
| Message queue (async, decoupled) | SQS | "message queue" |
| Pub/sub fan-out | SNS + SQS | "event bus" |
| Streaming / high-throughput events | Kinesis or Kafka (MSK) | "event stream" |
| Key-value / NoSQL at scale | DynamoDB | "NoSQL database" |
| Relational database | RDS (PostgreSQL/MySQL) or Aurora | "SQL database" |
| Cache | ElastiCache (Redis) | "cache layer" |
| CDN | CloudFront | "CDN" |
| Container orchestration | ECS or EKS | "container platform" |
| Serverless compute | Lambda | "serverless function" |
| Search | OpenSearch (Elasticsearch) | "search engine" |
| API management | API Gateway | "API gateway" |

You don't need to know pricing or config details — just which service fits which need and why.

---

## The Amazon HLD Interview Structure (45–60 min)

```
0–5 min   → Clarify requirements; ask about scale, consistency, availability SLAs
5–10 min  → Capacity estimation (QPS, storage, bandwidth) — Amazon always expects this
10–20 min → High-level architecture: draw the boxes, name the services
20–35 min → Deep dive on the hardest part (Amazon will steer you here)
35–50 min → Failure handling, scaling bottlenecks, trade-offs
50–60 min → Follow-up questions
```

Amazon interviewers will interrupt you to deep-dive earlier than you expect. That's intentional — they want to see how you handle being redirected mid-design.

---

## Capacity Estimation — Do It Every Time

Amazon expects numbers. Without them, your design has no foundation.

**Quick template:**

```
DAU: X million
Requests/sec (avg): DAU × actions_per_day / 86,400
Requests/sec (peak): avg × 3–10×
Storage/day: writes/sec × avg_record_size × 86,400
Storage/year: storage/day × 365
Read/write ratio: state it (e.g., 100:1 for Twitter-like feed)
```

**Numbers to memorize:**
- 1M DAU, 10 req/day each → ~115 QPS average
- 1KB record × 1M writes/day → ~1 GB/day → ~365 GB/year
- p99 target: < 100ms (cache hit), < 500ms (DB read), < 2s (write with replication)

---

## Most Common Amazon HLD Topics

These appear most frequently at SDE-2. Know each cold:

| Topic | Key design decision | Amazon angle |
|-------|--------------------|----|
| **URL shortener** | Hash collision strategy, redirect latency | S3 or DynamoDB for mapping, CloudFront for redirect caching |
| **Rate limiter** | Token bucket vs sliding window, distributed state | Redis + Lua script for atomic decrement; SQS for overflow queuing |
| **Notification system** | Fan-out strategy, delivery guarantees | SNS → SQS per channel; DLQ for failed deliveries |
| **Feed / news feed** | Fan-out on write vs read, celebrity problem | Pre-compute for regular users; pull for celebrities |
| **E-commerce / booking** | Inventory consistency, double-booking prevention | Conditional writes in DynamoDB or SELECT FOR UPDATE in RDS |
| **Unique ID generator** | Monotonic, no SPOF, sortable | Snowflake-style: timestamp + machine ID + sequence |
| **Key-value store** | Consistency vs availability, partitioning | Consistent hashing; quorum reads/writes |

---

## Failure Handling — Answers Amazon Expects

For every stateful operation, have an answer for each:

**"What if the downstream service is down?"**
→ Circuit breaker (stop sending requests when failure rate exceeds threshold). SQS dead letter queue for messages that couldn't be processed. Retry with exponential backoff + jitter.

**"What if a message is processed twice?"**
→ Idempotency key. Consumer checks if event ID was already processed before acting. `INSERT ... ON CONFLICT DO NOTHING` or DynamoDB conditional write.

**"What if the database goes down?"**
→ Read replica promotion (RDS Multi-AZ automatic failover ~60 seconds). Cache serves reads during failover. Writes queue in SQS until primary recovers.

**"What if the cache is cold / Redis goes down?"**
→ Cache-aside fallback to DB. Rate-limit DB requests during recovery to prevent thundering herd. Background job warms cache on restart.

---

## Trade-offs Amazon Expects You to Name

Don't wait to be asked — state the trade-off when you make each choice.

| Choice | Trade-off to state |
|--------|-------------------|
| Async via SQS | Lower coupling, higher throughput — but eventual consistency; can't return result in same request |
| DynamoDB over RDS | Infinite scale, single-digit ms — but no joins, limited query patterns, must design partition key carefully |
| Fan-out on write (feed) | Fast reads — but write amplification for high-follower users; need hybrid for celebrities |
| Redis cache | Fast reads — but cache invalidation complexity; stale data window |
| Microservices split | Independent scale and deploy — but distributed transaction complexity, network overhead |
| SQL with row locking | Strong consistency for bookings — but throughput limited by lock contention at scale |

---

## Red Flags Amazon Interviewers Watch For

- Jumping to architecture without estimating scale first
- No mention of what happens when a component fails
- Using a single database with no replication or backup strategy
- Over-engineering: proposing Kafka + microservices for a system that serves 1K QPS
- Under-engineering: a single server for a "100M DAU" system
- Saying "we can use a cache" without explaining invalidation strategy
- Naming AWS services without knowing one level below (e.g., "DynamoDB" but can't explain partition key choice)
