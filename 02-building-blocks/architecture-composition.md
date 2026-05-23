---
module: 02-building-blocks
status: unread
tags: [02-building-blocks, system-design, building-blocks]
---
# How Systems Actually Scale: Composing the Building Blocks

Most system design resources teach building blocks in isolation. This guide shows how they snap together into a real architecture — and, critically, *when* each piece earns its place. Every addition below is a response to a specific failure mode. Nothing is added speculatively.

Real companies (Instagram, Uber, Netflix, Airbnb) grew through exactly these stages. The scale numbers are approximations; your trigger conditions depend on your traffic pattern, not a magic user count.

---

## The Single-Server Era (0 → 1K users)

Everything lives on one box.

```
Client
  |
  v
[ Single Server ]
  - Web server (Nginx / Apache)
  - Application code
  - Database (PostgreSQL / MySQL)
```

This is correct. There is no waste, no ops overhead, no network hops between components. Instagram launched on a single server. So did Twitter. So did every company you admire.

**Real-life analogy:** A sole proprietor running a corner shop. One person takes orders, handles the register, manages the inventory ledger, and sweeps the floor. Maximally efficient at small scale.

**What breaks first:** The database. Under write-heavy load, locking on a single-process DB becomes the bottleneck. Alternatively, a long-running app query starves DB connections. You hit CPU or memory limits on the one machine before you hit any architectural limit.

**Trigger to move on:** Response times climbing under moderate load, or you need to deploy app code without taking the database down.

---

## The First Split (1K → 10K users)

Separate the database onto its own machine. Add a read replica.

```
Client
  |
  v
[ App Server ]
  |          \
  v (writes)  v (reads)
[ DB Primary ]  [ DB Read Replica ]
```

**Why this works:** App servers are CPU-bound (compute, business logic). Databases are I/O-bound (disk reads, fsync on writes). They compete for memory on a shared host. Separating them lets each machine be tuned independently — more RAM for DB buffer pools, more CPU cores for the app tier.

The read replica handles read traffic (often 80–90% of queries in a typical web app). The primary handles writes only. This is the first application of the read/write split pattern you will use repeatedly.

**Real-life analogy:** The shop owner hires a dedicated bookkeeper. The bookkeeper maintains the ledger full-time; the owner focuses on customers. They hand off information at defined points rather than one person context-switching between both jobs.

**What breaks next:** Reads still hit the DB even for the same repeated data. A user profile fetched 10,000 times still executes 10,000 SQL queries. The read replica helps with throughput, but it does not eliminate redundant work.

---

## Adding the Cache (10K → 100K users)

Insert Redis between the app tier and the database.

```
Client
  |
  v
[ App Server ]
  |
  v
[ Redis Cache ] -- miss --> [ DB Primary / Replica ]
```

**Cache-aside pattern (the default):** The app checks Redis first. On a hit, return immediately. On a miss, query the DB, populate Redis with a TTL, return to the caller. The app owns cache population. This is the right default because it is simple and tolerates cache failures gracefully — if Redis goes down, you fall back to the DB.

**What to cache:**
- High-read, low-write data: user profiles, product listings, configuration, aggregated counts
- Results of expensive queries: leaderboards, feed rankings, search facets
- Session tokens (these belong in Redis, not in DB)

**What NOT to cache:**
- Data requiring strong consistency (account balances, inventory counts in checkout flows)
- User-specific sensitive data unless you are confident on key isolation (cache poisoning risks)
- Write-heavy data where the cache would be invalidated on every write anyway

**Real-life analogy:** The bookkeeper keeps a notebook of common answers: "What is the price of item X?" The notebook is checked first. Only questions not in the notebook trigger opening the full filing cabinet. The notebook is refreshed periodically or when known to be stale.

**Cache stampede problem:** When a popular cache key expires, hundreds of concurrent requests miss the cache simultaneously and all query the DB at once. Three mitigations:
1. **Probabilistic early expiration:** Refresh the key slightly before its TTL expires, not after.
2. **Mutex/lock on miss:** First request to miss acquires a lock and populates the cache; others wait briefly.
3. **Staggered TTLs:** Add random jitter (e.g., TTL = base ± 10%) so keys don't expire in a wave.

**What breaks next:** All requests still land on one app server. You cannot scale the app tier horizontally because each server holds session state in memory — if a subsequent request hits a different server, the session is gone.

---

## Horizontal Scaling the App (100K → 500K users)

Add a load balancer. Run multiple stateless app server instances.

```
Client
  |
  v
[ Load Balancer ]
  /    |    \
 v     v     v
[App] [App] [App]
  \    |    /
   v   v   v
  [ Redis Cache ]
       |
       v
  [ DB Primary ] --> [ DB Replica(s) ]
```

**Stateless design requirement:** Each app server must be interchangeable. If server A handles request 1 and server B handles request 2 for the same user, they must see the same state. That means no local memory for sessions, no local disk for uploads, no local state of any kind.

**Session management options:**

| Approach | How it works | Trade-off |
|---|---|---|
| Sticky sessions | Load balancer always routes a user to the same server | Simple, but one server failure loses all its sessions; limits load distribution |
| Shared Redis session store | Sessions stored in Redis, any server can read them | Correct approach; adds a Redis dependency but that dependency already exists |

Use shared Redis. Sticky sessions are an anti-pattern that creates the illusion of statelessness while preserving the fragility of a single point of failure.

**Real-life analogy:** A call center with 20 agents vs one person answering all calls. Any agent can handle any caller because the customer record is in the shared CRM (Redis), not in the agent's head.

**What breaks next:** All writes still go to one database primary. As write throughput grows, the primary becomes the bottleneck. Replication lag on replicas increases. You approach the limits of vertical scaling on the DB host.

---

## Sharding the Database (500K → 2M users)

Split data horizontally across multiple database instances.

```
[ App Server ]
     |
     v
[ Shard Router ]
  /    |    \
 v     v     v
[DB0] [DB1] [DB2]   <-- each shard has its own primary + replica(s)
```

Each shard holds a subset of the data. The shard router (often logic inside the app or a middleware layer) determines which shard to query based on a shard key.

**Consistent hashing for shard routing:** Map shard keys onto a hash ring. Each shard owns a range of the ring. Adding or removing a shard rebalances only the adjacent ranges, not the entire dataset. This minimizes data movement during resharding.

**Choosing a shard key:**
- High cardinality (many distinct values)
- Evenly distributed (avoid hot shards — e.g., don't shard on country if 60% of users are in the US)
- Aligns with your most common query pattern (shard by `user_id` if most queries are per-user)

**The cross-shard query problem:** A query that spans multiple shards (e.g., "find all orders over $500 across all users") must be fanned out to all shards and the results aggregated. This is expensive. The architecture forces you to design queries that stay within a shard boundary. Reporting and analytics workloads that need cross-shard queries are typically moved to a separate data warehouse (BigQuery, Redshift, Snowflake) via async ETL.

**Real-life analogy:** Splitting a library into multiple buildings by genre. Fiction is in Building A, Non-Fiction in Building B, Periodicals in Building C. Finding a specific book is fast — you know which building to walk into. But a query like "find every book published in 1984 across all genres" requires checking all buildings.

**See also:** [sharding.md](./sharding.md)

---

## Adding a CDN (when static content exceeds ~20% of traffic)

A CDN is a network of edge nodes geographically distributed near your users. It caches static and cacheable responses and serves them from the nearest edge location.

```
Client --> [ CDN Edge Node ] --> (cache miss only) --> [ Origin: Load Balancer → App ]
```

**When to add it:** When static assets (images, JS, CSS, video) make up a significant fraction of traffic, or when you have users in geographies far from your origin data center (high latency).

**When it is premature:** For a small app with a single geographic user base serving mostly dynamic, personalized content. CDN adds cost and cache invalidation complexity without meaningful benefit.

**Origin offload math:** If 70% of your traffic is static content and your CDN achieves a 90% cache hit rate on that static content, your origin only handles:
- 30% (dynamic traffic) + 10% of 70% (CDN misses) = 30% + 7% = **37% of total traffic**
- In practice, well-tuned CDNs hit 95%+ on static content, dropping origin load to ~33.5%

For video platforms (Netflix, YouTube), CDN hit rates on popular content exceed 99%. The origin serves essentially only cache-fill traffic and unpopular long-tail content.

**Real-life analogy:** A chain of convenience stores vs one central warehouse. Customers buy common items (milk, bread, snacks) from the nearest store. The central warehouse only ships items the local store doesn't stock. The warehouse handles a fraction of total transactions.

**See also:** [cdn.md](./cdn.md)

---

## Async Processing with Queues (when writes become slow)

Some work does not need to complete before the user gets a response. Move it off the critical path.

```
Client
  |
  v (HTTP POST)
[ App Server ] --> 202 Accepted --> Client
  |
  v (enqueue)
[ Message Queue ]  (Kafka / SQS / RabbitMQ)
  |
  v (consume)
[ Background Workers ]
  |
  v
[ DB / Storage / External APIs ]
```

**Pattern:** Return `202 Accepted` immediately with a job ID. The client can poll for completion or receive a webhook callback. The work happens asynchronously.

**What fits in a queue:**
- Email and SMS notifications (latency-tolerant, failure-retryable)
- Image and video transcoding (expensive, non-blocking)
- Analytics event ingestion (write-heavy, can tolerate some delay)
- Search index updates (eventual consistency acceptable)
- Webhook delivery to third parties (external I/O, slow and unreliable)
- Fraud detection scoring (compute-heavy, can run post-transaction)

**What does NOT fit in a queue:**
- Anything the user is waiting for in the same request (payment confirmation, authentication)
- Operations requiring immediate consistency (inventory decrement at checkout)
- Any operation where the response *is* the result (search results, data retrieval)

**Real-life analogy:** A restaurant where you order at the counter and receive a buzzer. You walk to the bar. The kitchen processes your order asynchronously. You are not blocking the cashier while your food is prepared. The cashier can take the next order immediately. When your food is ready, the buzzer fires (webhook/callback).

**Uber** uses queues extensively for driver location updates (millions of writes per second). The driver app publishes location events; multiple downstream consumers (ETA calculation, map display, surge pricing) consume independently without blocking each other.

**See also:** [message-brokers.md](./message-brokers.md)

---

## Microservices Split (2M+ users, or when team size forces it)

A monolith is a single deployable unit containing all business logic. A microservices architecture decomposes it into independently deployable services communicating over a network.

**When to split:**
- **Team size forces it (Conway's Law):** If 50 engineers are modifying the same codebase, deployment coordination becomes the bottleneck. Services allow teams to own and deploy independently.
- **Wildly different scaling requirements:** The video transcoding component needs 100 GPU machines; the user profile service needs 3 small instances. In a monolith, you scale everything together.
- **Technology isolation:** A specific component benefits from a different language or runtime (e.g., a recommendation engine in Python/PyTorch, a low-latency trading component in C++).

**When NOT to split:**
- You have fewer than ~50 engineers. The operational overhead of microservices (distributed tracing, service discovery, network failure handling) exceeds the coordination cost of a monolith at small team sizes.
- You have not identified stable service boundaries. Splitting along the wrong seam creates a distributed monolith — all the operational complexity, none of the independence.
- You are still finding product-market fit. A monolith changes faster.

**The cost you are accepting:**
- Distributed transactions (no ACID across services — you need sagas or two-phase commit)
- Network hops add latency and introduce new failure modes (timeouts, partial failures)
- Operational complexity: each service needs its own CI/CD, monitoring, on-call rotation
- API versioning between services becomes a first-class concern

**Real-life analogy:** A company that spun off its accounting department into a separate firm. Every interaction now requires a formal contract (API), an invoice (request/response), and a dispute resolution process (retry logic, dead letter queues). The accounting firm can scale independently and hire specialists. But what used to be an internal function call is now a cross-organizational negotiation.

**Airbnb** ran a monolith until ~2015. The split was driven by team growth and the need for independent deployment, not by a performance problem the monolith couldn't solve.

**See also:** [../04-advanced-topics/microservices.md](../04-advanced-topics/microservices.md)

---

## The Full Picture: 1B-User Reference Architecture

```
                        ┌──────────────────────────────────────────────────────┐
                        │                    CLIENT TIER                        │
                        │   Mobile App / Browser / Third-party API consumers    │
                        └─────────────────────────┬────────────────────────────┘
                                                   │ HTTPS
                                                   ▼
                        ┌──────────────────────────────────────────────────────┐
                        │                      CDN LAYER                        │
                        │   Static assets, cacheable API responses, video       │
                        │   Failure mode: stale content / cache miss storm      │
                        │   Mitigation: TTL tuning, cache-control headers,      │
                        │               origin shield node                      │
                        └─────────────────────────┬────────────────────────────┘
                                                   │ Cache miss only
                                                   ▼
                        ┌──────────────────────────────────────────────────────┐
                        │                    LOAD BALANCER                      │
                        │   L7 (HTTP-aware), health checks, TLS termination     │
                        │   Failure mode: LB itself becomes SPOF                │
                        │   Mitigation: active-active LB pair with anycast IP   │
                        └────────────┬──────────────────────┬───────────────────┘
                                     │                      │
                          ┌──────────▼──────┐    ┌──────────▼──────┐
                          │   App Server 1   │    │   App Server N   │  (stateless, autoscaled)
                          │  (handles reads, │    │                  │
                          │   writes, API)   │    │                  │
                          └────────┬─────────┘    └────────┬─────────┘
                                   │                       │
                                   └──────────┬────────────┘
                                              │
                          ┌───────────────────▼───────────────────────┐
                          │              REDIS CACHE CLUSTER            │
                          │  Sessions, hot reads, rate limit counters,  │
                          │  distributed locks, leaderboards            │
                          │  Failure mode: cache stampede on restart    │
                          │  Mitigation: warm-up scripts, staggered TTL │
                          └───────────────────┬───────────────────────┘
                                              │ cache miss
                                              ▼
                          ┌───────────────────────────────────────────────────────┐
                          │                  DATABASE LAYER                        │
                          │                                                         │
                          │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐   │
                          │  │  Shard 0     │  │  Shard 1     │  │  Shard N     │  │
                          │  │  Primary     │  │  Primary     │  │  Primary     │  │
                          │  │     │        │  │     │        │  │     │        │  │
                          │  │  Replica(s)  │  │  Replica(s)  │  │  Replica(s)  │  │
                          │  └─────────────┘  └─────────────┘  └─────────────┘   │
                          │                                                         │
                          │  Failure mode: hot shard, replication lag              │
                          │  Mitigation: consistent hashing, replica reads         │
                          └───────────────────────────────────────────────────────┘
                                              │
                          (slow/async writes) │
                                              ▼
                          ┌───────────────────────────────────────────────────────┐
                          │               MESSAGE QUEUE                            │
                          │   (Kafka / SQS / RabbitMQ)                            │
                          │   Decouples producers from consumers                   │
                          │   Failure mode: consumer lag, message loss             │
                          │   Mitigation: DLQ, idempotent consumers, at-least-once │
                          └────────────────────┬──────────────────────────────────┘
                                               │
                          ┌────────────────────▼──────────────────────────────────┐
                          │             BACKGROUND WORKERS                          │
                          │   Email, transcoding, analytics, search indexing,       │
                          │   ML inference jobs                                     │
                          │   Failure mode: job stuck / worker crash                │
                          │   Mitigation: visibility timeout, retry with backoff    │
                          └───────────────────────────────────────────────────────┘
```

**Data flow annotations:**

| Arrow | What flows | Why |
|---|---|---|
| Client → CDN | Static assets, cached API responses | Reduce origin load; reduce latency for global users |
| CDN → Load Balancer | Cache misses only | CDN handles the bulk; LB sees a fraction |
| LB → App Servers | HTTP requests, round-robin or least-connections | Distribute load; enable horizontal scaling |
| App → Redis | Session reads/writes, cache reads/writes | Sub-millisecond latency; avoid DB for hot data |
| App → DB | Cache misses, all writes | Source of truth; accessed only when cache cannot serve |
| App → Queue | Async job messages | Decouple slow work from the request path |
| Queue → Workers | Job payloads | Workers consume at their own pace; back-pressure is natural |

---

## How to Use This in an Interview

**The scale ladder:** Always start with the simplest architecture that satisfies the stated requirements. A single server with a database is correct for most starting points. Add complexity only when you can articulate the specific failure it solves.

**The sequence interviewers expect:**
1. Single server
2. Separate DB
3. Read replica (if read-heavy)
4. Cache (if repeated reads or expensive queries)
5. Load balancer + stateless app servers (if traffic demands horizontal scale)
6. Sharding (if write throughput or data volume exceeds single-DB capacity)
7. CDN (if static content is significant or global latency matters)
8. Message queue (if slow writes or background work exists)
9. Microservices (if team scale or wildly different component scaling demands it)

**The interviewer's trap:** "Should we add a cache here?" The wrong answer is "Sure, let's add Redis." The right answer: "What is our read-to-write ratio on this data? What is our tolerance for stale data? If reads vastly outnumber writes and slight staleness is acceptable, cache-aside with a short TTL makes sense. If this is financial data requiring strong consistency, a cache introduces correctness risk that outweighs the performance gain."

Every component addition requires you to state:
1. **What problem it solves** (specific bottleneck or failure mode)
2. **What trade-off it introduces** (operational complexity, consistency window, cost, latency in the failure path)

If you cannot answer both, you are not ready to add the component.

---

## Quick Revision

| Scale | Component added | Trigger condition |
|---|---|---|
| 0 → 1K | Single server | Starting point |
| 1K → 10K | Separate DB + read replica | App/DB resource contention; read-heavy load |
| 10K → 100K | Redis cache | Repeated reads to DB; expensive query results |
| 100K → 500K | Load balancer + stateless app tier | Single app server at CPU/memory limit |
| 500K → 2M | DB sharding | Write throughput or data volume exceeds single primary |
| Any point | CDN | Static/cacheable content >20% of traffic, or global users |
| Any point | Message queue | Writes too slow; background work exists; decoupling needed |
| 2M+ | Microservices | Team size forces independent deployment; wildly different scaling per component |

**The rule that overrides all scale numbers:** Add a component when you can name the specific failure it prevents. Never add it because "that's what Netflix does." Netflix's architecture is the result of solving Netflix's specific failure modes at Netflix's scale. Your failure modes at your scale are different.
