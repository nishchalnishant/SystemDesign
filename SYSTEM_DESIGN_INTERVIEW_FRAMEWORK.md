> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** A repeatable 7-phase framework for executing system design interviews at the SDE-3 level — from requirements clarification to deep-dive and failure handling.
>
> **Key topics:**
> - Phase 1 (5–10 min): Clarify functional + non-functional requirements; questions to ask; scope alignment
> - Phase 2 (5 min): Back-of-envelope capacity estimation — QPS, storage, bandwidth formulas
> - Phase 3 (10–15 min): Draw the high-level architecture — components, data flow, API contracts
> - Phase 4–5: Identify bottlenecks + discuss trade-offs (consistency vs availability, latency vs cost)
> - Phase 6 (15–20 min): Deep dive into 1–2 components — API design, data model, scaling details
> - Phase 7 (5–10 min): Horizontal scaling, failover strategies, degradation modes
> - Senior-level signals: trade-off awareness, proactive failure discussion, cost consciousness
>
> **Key takeaway:** Memorize this 7-phase structure — it transforms a vague "design X" prompt into a structured 45-minute performance.

---
module: root
status: unread
tags: [root, system-design, system-design-interview-framew]
---
# System Design Interview Framework

> **How to approach a system design interview** — A step-by-step framework for SDE-3 / Senior Software Engineer interviews

---

## Overview

System design interviews evaluate your ability to **clarify ambiguity**, **reason about scale**, **make trade-offs**, and **design for failure**. This framework gives you a repeatable structure so you spend time on high-value discussion instead of figuring out what to do next.

**Typical duration**: 45–60 minutes  
**Your goals**: Show structured thinking, ask the right questions, drive the conversation, and demonstrate senior-level judgment (trade-offs, cost, operations, resilience).

---

## The Seven Phases

| Phase | Time | Focus |
|-------|------|--------|
| 1. Clarify requirements | 5–10 min | Functional + non-functional; scope and constraints |
| 2. Estimate scale | 5 min | QPS, storage, bandwidth; back-of-envelope |
| 3. High-level architecture | 10–15 min | Components, data flow, boundaries |
| 4. Identify bottlenecks | 5 min | Where the system will break or slow down |
| 5. Discuss trade-offs | 5 min | Consistency vs availability, latency vs cost, etc. |
| 6. Deep dive into components | 15–20 min | 1–2 components in detail (APIs, data model, scaling) |
| 7. Scaling and failure handling | 5–10 min | Horizontal scaling, failover, degradation |

---

## Phase 1: Clarify Requirements

**Why it matters**: Jumping into boxes and arrows before understanding the problem is the most common mistake. Senior engineers align on scope first.

### Functional requirements

- **Core features**: What are the 3–5 must-have features?
- **Users**: Who uses the system (B2C, B2B, internal)?
- **Critical user journeys**: e.g. “User shortens URL → later opens short URL → gets redirected.”
- **Out of scope (for now)**: Explicitly deprioritize (e.g. “No custom aliases in v1”).

**Questions to ask**:
- “What’s in scope for this discussion—MVP or full product?”
- “Who are the main users and what’s the most important flow?”
- “Are there any features we should explicitly leave out?”

### Non-functional requirements

Use a simple checklist so you don’t forget dimensions:

| Dimension | What to clarify |
|-----------|------------------|
| **Performance** | Latency (e.g. P99 &lt; 200 ms), throughput (QPS), tail latency |
| **Availability** | Uptime target (e.g. 99.9%), planned maintenance, multi-region |
| **Scalability** | Growth (users, data, traffic), peak vs average (e.g. 3×) |
| **Consistency** | Strong vs eventual; read-after-write requirements |
| **Durability** | Can we lose data? RPO/RTO if applicable |
| **Security** | Auth, PII, compliance (GDPR, etc.) |
| **Cost** | Any rough budget or “optimize for cost” constraint? |

**Example (URL shortener)**:
- “Redirect latency: P99 &lt; 100 ms.”
- “Availability: 99.99%.”
- “We can accept eventual consistency for redirects; strong consistency for create.”
- “URLs must not be lost once created.”

### Output of this phase

- Short list of **must-have** vs **nice-to-have** features.
- Clear **non-functional targets** (latency, availability, scale, consistency).
- Agreement on **scope** so you don’t over- or under-design.

---

## Phase 2: Estimate Scale

**Why it matters**: Scale drives technology choices (single DB vs sharding, cache vs no cache, sync vs async). Show you think in numbers.

### What to estimate

1. **Traffic**
   - DAU/MAU or requests per day/month.
   - Reads vs writes ratio (e.g. 100:1 for URL shortener).
   - Peak QPS (e.g. 3× average).

2. **Storage**
   - Record size and retention (e.g. 5 years).
   - Total size and growth rate.
   - Replication factor (e.g. 3×).

3. **Bandwidth**
   - In/out per request and total (optional for first pass).

### How to present

- State assumptions clearly: “Assume 100M new URLs per month, 100:1 read:write.”
- Do simple math on the whiteboard:  
  - Writes: 100M / (30 × 86,400) ≈ 40/s → peak ~120/s.  
  - Reads: 40 × 100 = 4,000/s → peak ~12,000/s.
- Round to one significant figure for discussion: “~100 writes/s, ~10K reads/s.”

### Output

- **Read QPS** and **write QPS** (or equivalent).
- **Total storage** and **storage per node** if relevant.
- These numbers justify caching, sharding, and replication later.

---

## Phase 3: High-Level Architecture

**Why it matters**: This is the “picture” the rest of the interview builds on. Keep it simple first; add detail in deep dives.

### Components to consider

- **Clients** (web, mobile, API consumers).
- **Edge / CDN** (static assets, sometimes redirects).
- **Load balancer(s)**.
- **API / application servers** (stateless).
- **Caches** (e.g. Redis).
- **Databases** (primary + replicas, or sharded).
- **Message queues** (async jobs, events).
- **External services** (payments, notifications).

### How to draw

1. Draw **client → LB → app servers → cache → DB** as a first cut.
2. Add **queues** and **workers** if you have async or background work.
3. Label **read vs write path** if they differ.
4. Add **replicas** or **shards** only after you’ve stated the need (e.g. “We’ll need read replicas for 10K reads/s”).

### Data flow

- Describe in one sentence: “User hits short URL → LB → app server → cache; on miss, DB → cache → redirect.”
- Mention **sync vs async**: “Create short URL is synchronous; click analytics are sent to a queue and processed asynchronously.”

### Output

- A **single diagram** with 5–10 boxes and clear flow.
- A **one-paragraph** narrative: “Traffic hits the LB, then stateless API servers. We cache hot URLs; on miss we hit the DB and backfill cache. Writes go to the primary; we’ll add read replicas for scale.”

---

## Phase 4: Identify Bottlenecks

**Why it matters**: Shows you think about limits, not just “happy path.” Senior engineers anticipate failure modes.

### Typical bottlenecks

| Component | Risk | Mitigation (to discuss in Phase 7) |
|-----------|------|-----------------------------------|
| Single DB | Writes and reads saturate one node | Sharding, read replicas |
| Cache | Stampede on miss, or cache down | Cache-aside + TTL, fallback to DB; consider single-flighter |
| API servers | CPU or memory under spike | Horizontal scaling, rate limiting |
| Message queue | Consumer lag, queue depth | More consumers, backpressure, DLQ |
| External API | Latency or rate limits | Timeouts, circuit breaker, cache, queue |

### How to present

- “The main bottlenecks I see: (1) DB write capacity if we grow beyond one node, (2) cache stampede on a viral link, (3) DB as single point of failure.”
- Then briefly: “I’d address (1) with sharding, (2) with TTL and maybe request coalescing, (3) with failover and replicas.”

### Output

- **2–4 concrete bottlenecks** and **one-line mitigations**. You’ll detail them in Phase 6–7.

---

## Phase 5: Discuss Trade-offs

**Why it matters**: SDE-3 is expected to articulate *why* a design is chosen, not just *what* it is.

### Common trade-offs

- **Consistency vs availability**: CP vs AP; strong vs eventual.
- **Latency vs consistency**: Synchronous replication vs async.
- **Cost vs performance**: More cache vs more DB; more replicas vs lower durability.
- **Complexity vs flexibility**: Monolith vs microservices; single DB vs polyglot persistence.
- **Operational complexity**: Self-managed vs managed services (e.g. RDS vs self-hosted Postgres).

### How to present

- “For redirects we’ll use eventual consistency and cache heavily—low latency and high availability matter more than perfect freshness. For creating a short URL we’ll want strong consistency so we don’t hand out duplicates.”
- “We could use 2PC for cross-service transactions, but that’s blocking and complex; I’d prefer a saga with compensating actions and idempotent steps.”

### Output

- **2–3 explicit trade-offs** with a clear “we choose X because Y” statement.

---

## Phase 6: Deep Dive into Components

**Why it matters**: Interviewers want to see depth in at least one or two areas: API design, data model, or a specific component (cache, queue, DB).

### What to prepare

1. **API design**
   - REST or RPC; idempotency for writes (e.g. idempotency key).
   - Key endpoints: create short URL, redirect, optional analytics.
   - Status codes and errors (rate limit, not found, conflict).

2. **Data model**
   - Main entities and relationships.
   - Schema (tables or documents): e.g. `short_code` (PK), `long_url`, `user_id`, `created_at`.
   - Indexes: by `short_code` (lookup), by `user_id` (list user’s URLs).
   - Sharding key if you shard (e.g. `short_code`).

3. **One or two components in detail**
   - **Cache**: Strategy (cache-aside), TTL, eviction (LRU), key format, stampede mitigation.
   - **Database**: Sharding strategy (hash vs range), replication (sync vs async), failover.
   - **Queue**: Use case (analytics, notifications), at-least-once vs exactly-once, consumer scaling.

### How to run the deep dive

- “I’ll go deeper on the cache and the database.”
- For cache: “We use cache-aside. Key is `url:{short_code}`. TTL 24 hours. On miss we load from DB and backfill. We use LRU when memory is full.”
- For DB: “We shard by `hash(short_code) % N` so lookups are single-shard. We use read replicas for read scaling; writes go to primary.”

### Output

- **Concrete API** (method, path, body, idempotency).
- **Schema + indexes + sharding key**.
- **Clear behavior** of 1–2 components (cache, DB, or queue).

---

## Phase 7: Scaling and Failure Handling

**Why it matters**: Production systems scale and fail; you need to show you think about both.

### Scaling

- **Horizontal**: More app servers behind LB; more DB shards; more cache nodes; more consumers.
- **Vertical**: Bigger DB/cache instances when it’s simpler (e.g. early stage).
- **Read scaling**: Read replicas, cache, CDN.
- **Write scaling**: Sharding, async writes (queue), batching.

State clearly: “We scale reads with replicas and cache; we scale writes by sharding when we outgrow one DB.”

### Failure handling

- **DB primary down**: Failover to replica; promote replica to primary; use replication.
- **Cache down**: Fall back to DB; accept higher latency; optional stale cache from another region.
- **App server down**: Stateless; LB stops sending traffic; no session state lost.
- **Queue backlog**: Scale consumers; backpressure; DLQ for poison messages; alert on lag.

### Degradation

- “If the recommendation service is down, we show a default list instead of failing the page.”
- “If DB is slow, we might serve stale from cache and surface a short delay message.”

### Output

- **Scaling strategy** in one or two sentences (read vs write, when to shard).
- **2–3 failure scenarios** with clear mitigations (failover, fallback, degradation).

---

## Quick Reference: What to Say When

| Situation | Suggested response |
|-----------|--------------------|
| Unclear scope | “For this exercise, should we focus on MVP or include analytics/custom aliases?” |
| Unclear scale | “What order of magnitude are we designing for—e.g. 1K vs 100K vs 1M DAU?” |
| After drawing HLD | “The main bottlenecks I see are … I’d address them by …” |
| Choosing technology | “I’d use X because … The trade-off is …” |
| Asked “what if X fails?” | “We’d … (failover / fallback / degrade). The impact would be …” |
| Running out of time | “If we had more time, I’d go deeper on … and add …” |

---

## Common Mistakes to Avoid

1. **Designing before clarifying** — Always do requirements and scale first.
2. **Over-complicating the first diagram** — Start with 5–7 boxes; add detail when asked.
3. **Ignoring failure** — Always mention at least one failure mode and mitigation.
4. **No trade-offs** — Explicitly state at least one “we choose A over B because …”.
5. **No numbers** — Do at least rough QPS and storage so your choices are justified.
6. **Monologue** — Ask 2–3 clarifying questions and confirm scope before drawing.

---

## Quick Revision (Interview Day)

- **Clarify**: Functional + non-functional; scope; 2–3 questions.
- **Estimate**: QPS (read/write), storage, state assumptions.
- **HLD**: Client → LB → app → cache → DB (+ queues if needed); one-sentence flow.
- **Bottlenecks**: 2–4 with one-line mitigations.
- **Trade-offs**: 2–3 “we choose X because Y.”
- **Deep dive**: API + schema + 1–2 components (cache, DB, or queue).
- **Scaling**: Horizontal, read vs write, when to shard.
- **Failure**: Failover, fallback, graceful degradation.

Use this framework to drive the interview and to demonstrate structured, senior-level thinking.
