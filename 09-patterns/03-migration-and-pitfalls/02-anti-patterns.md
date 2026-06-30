---
module: 09-patterns
status: unread
tags: [09-patterns, system-design, patterns]
---
# System Design Anti-Patterns

> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Common architectural mistakes in distributed systems, why they are bad, and how to fix them.
>
> **Key concepts:**
> - Dual Writes: Writing to a DB and publishing an event without coordination. Use the Outbox Pattern instead.
> - Shared Database: Multiple microservices accessing the same database directly, creating tight coupling. Use API-driven data access.
> - Distributed Monolith: Microservices that are so tightly coupled via synchronous RPCs that if one goes down, they all go down. Use asynchronous events where possible.
> - Infinite Retries: Retrying a failed request forever without a backoff strategy, which can cause a self-inflicted DDoS attack (retry storm). Use Exponential Backoff + Jitter.
>
> **Key takeaway:** Senior engineers are defined not just by the patterns they know, but by the anti-patterns they avoid. Recognizing these traps in an interview or architecture review is critical.

> **The most common architectural mistakes in distributed systems and how to avoid them. Knowing what NOT to build is as important as knowing what to build.**

---

## Pattern Mindmap

```
System Design Anti-Patterns
├── Core Problem
│   └── Architectural mistakes that add microservice complexity without gaining independence
├── Anti-Pattern: Distributed Monolith
│   ├── Symptom → services split by technical layer, not business domain
│   ├── Signal → deploy requires coordinating all 8 services simultaneously
│   ├── Root cause → shared DB, synchronous call chains, shared deploy pipeline
│   └── Fix → DDD bounded contexts; each service owns its data exclusively
├── Anti-Pattern: Chatty Services
│   ├── Symptom → one user request triggers 20+ inter-service calls
│   ├── Signal → p99 latency is 3 seconds; all services are fast individually
│   ├── Root cause → fine-grained API design; no aggregation layer
│   └── Fix → BFF (Backend for Frontend), GraphQL, or aggregate service
├── Anti-Pattern: Shared Database
│   ├── Symptom → reporting service can deadlock checkout table
│   ├── Signal → schema change requires coordinating 6 teams
│   ├── Root cause → services share a single DB — the integration point is the schema
│   └── Fix → each service owns its DB; cross-service reads via API or async events
├── Anti-Pattern: God Service
│   ├── Symptom → one service handles auth, billing, email, user profile, and reporting
│   ├── Signal → all PRs touch the same service; one team is the bottleneck
│   ├── Root cause → no domain decomposition; all logic funnels to one place
│   └── Fix → apply SRP at service level; split by bounded context
├── Anti-Pattern: Missing Circuit Breaker
│   ├── Symptom → slow downstream causes thread pool exhaustion; entire app goes down
│   ├── Signal → cascade failure from a single dependency timeout
│   ├── Root cause → unbounded retries + no fail-fast mechanism
│   └── Fix → circuit breaker (Hystrix/Resilience4j), bulkhead, timeout per call
├── When These Appear in Interviews
│   ├── ✓ Interviewer describes a "microservices" system with a shared DB → name it
│   └── ✓ Asked "what went wrong?" in a system review → check for these 5 first
└── Interview Angles
    ├── "How do you detect a distributed monolith?" → can you deploy service A without B?
    ├── "How do you fix shared DB coupling?" → data ownership + async event propagation
    └── "What's a circuit breaker and when do you add one?" → any sync inter-service call
```

---

## 1. The Distributed Monolith

### What Breaks Without Knowing This Anti-Pattern?

A team splits a monolith into 8 services to "modernize." Six months later: any deploy still requires coordinating all 8 services, a crash in the user-profile service takes down the checkout service, and the database is still shared — every service has a connection string pointing to `postgres://shared-db/production`. The system has all the operational cost of microservices and none of the independence benefits. A bug in the reporting service can deadlock the checkout table during peak hours.

**Why the distributed monolith happens**

Teams split on technical boundaries (controllers, models, utilities) rather than business domain boundaries. Services remain coupled because they share: a database, synchronous call chains, or deployment pipelines. The coupling is the monolith; the split was cosmetic. The invariant violated: **a microservice must be independently deployable and independently failure-isolated**. Shared state or synchronous dependency chains break both properties.

**The minimal fix**

Identify true domain boundaries (DDD bounded contexts). Each domain owns its data exclusively — no cross-domain DB joins. Cross-domain communication is async events or versioned APIs, never direct DB access. Test independence by asking: "Can I deploy this service and have zero other services break?" If not, you still have a monolith.

---

### What It Is

A system that is split into multiple services (microservices) but remains tightly coupled — every deployment requires coordinating multiple services, and a failure in one cascades to all.

> **Analogy**: Splitting one restaurant into two rooms but having them share the same kitchen, the same cashier, and the same entrance. You've added the cost of two rooms without any of the independence.

### How to Recognize It

```
Signs you have a distributed monolith:
  ✗ Services share a database (any service can write any table)
  ✗ Services deployed together in every release (can't deploy independently)
  ✗ Synchronous chains: A calls B calls C calls D (single failure kills all)
  ✗ Services know each other's internal schemas (tightly coupled data models)
  ✗ One service's bugs crash other services (no fault isolation)
```

### The Fix

```
✓ Each service owns its data (database-per-service pattern)
✓ Services communicate via events (async, not synchronous chains)
✓ Versioned APIs with backward compatibility
✓ Each service deploys independently with its own CI/CD pipeline
✓ Circuit breakers on all cross-service calls
```

---

## 2. Shared Database Anti-Pattern

### What Breaks Without Knowing This Anti-Pattern?

The Inventory team renames the `quantity` column to `stock_count` in their table. Deploy. Immediately, the Order service starts throwing `500 Internal Server Error` — it was querying `inventory.quantity` directly. The outage affects checkout for 40 minutes until the Order service is patched. Later, a performance issue: the Reporting service runs a `GROUP BY` scan over 200M order rows every hour. This degrades Order service write performance because they share the same DB instance.

**Why shared databases fail**

Every service that touches a shared table becomes an implicit dependency of every other service using that table. Schema changes require coordinating all teams simultaneously. A heavy query from one service steals I/O from another. There is no clear owner — any service can corrupt any table. The invariant violated: **data is the responsibility of exactly one service; all other services access it only through that service's API.**

**The minimal fix**

Each service gets its own database (or at minimum its own schema with no cross-schema queries). Needed cross-domain data is either replicated via async events (Kafka `OrderCreated` event → Inventory updates its local read model) or fetched via API call to the owning service. Never share connection strings across service boundaries.

---

### What It Is

Multiple services read and write directly to the same database tables. This is the most common microservices mistake.

```
BAD:
  OrderService:     SELECT * FROM users WHERE id = ?   (User Service's table!)
  InventoryService: UPDATE orders SET status = 'SHIPPED' WHERE id = ?  (Order Service's table!)
  Both share: postgres://shared-db/production
```

### Why It Fails

- **Schema coupling**: If Order Service needs to rename a column, it might break Inventory Service
- **Performance coupling**: Inventory Service's heavy queries degrade Order Service's response time
- **Deployment coupling**: Schema migrations must coordinate all services
- **Data integrity impossible**: Any service can corrupt any other service's data

### The Fix

```
✓ Each service has its own database (or at minimum, its own schema)
✓ Data needed by another service: published as events (Kafka)
✓ Cross-service queries: API calls to the owning service
✓ Denormalize: each service's read model has what it needs locally

OrderService DB (PostgreSQL):   orders, order_items
UserService DB (PostgreSQL):    users, addresses
InventoryService DB (Redis+SQL): inventory_counts

Communication:
  OrderService needs user email → calls GET /users/{id} (API, not DB join)
  InventoryService needs order info → subscribes to OrderCreated Kafka event
```

---

## 3. Synchronous Call Chains

### What Breaks Without Knowing This Anti-Pattern?

The email provider degrades at 3am on Black Friday. Suddenly, `POST /orders` starts timing out at 30 seconds. Why? OrderService calls InventoryService (fast), then NotificationService, which calls EmailService (now 30s). Every in-flight order request blocks a thread waiting for EmailService. OrderService's thread pool exhausts in 2 minutes. Orders are failing for 100% of users — because of an email provider. No one thought email delivery was in the critical path.

**Why synchronous chains fail**

In a synchronous chain, every service in the chain must be available and fast for any single request to succeed. Availability compounds multiplicatively: 99.9% × 99.9% × 99.9% × 99.9% = 99.6% (four 9s becomes three 9s). Latency adds up: P99 of the chain is roughly the sum of P99s of each hop. One degraded non-critical dependency (email) can take down a critical path (checkout). The invariant violated: **non-critical work must not be in the critical request path.**

**The minimal fix**

Identify which downstream calls are required for the response and which are side effects. Side effects (email, notifications, analytics, audit logs) go to a queue or event bus and are processed asynchronously. The core request returns to the user as soon as the business-critical writes complete. Downstream failures do not affect the user response.

---

### What It Is

Service A synchronously calls B, which calls C, which calls D. The user's request is blocked waiting for the entire chain.

```
BAD: Client → OrderService → InventoryService → NotificationService → EmailService
     Each hop adds latency. If any service is slow: entire chain is slow.
     If EmailService is down: OrderService returns 500 to the user.
```

### Why It Fails

```
Latency accumulation:
  OrderService:       10ms
  InventoryService:   50ms
  NotificationService: 30ms
  EmailService:       200ms
  Total P99:          290ms + overhead = ~400ms

If EmailService degrades to 2000ms:
  User's order creation takes 2 seconds instead of 400ms
  Threads pile up in OrderService waiting for EmailService
  OrderService thread pool exhausted → all orders fail
  Cascade failure from EmailService → OrderService
```

### The Fix

```
Identify critical path vs non-critical:
  Critical (must complete before returning to user):
    Order validation → Inventory reservation → Payment → Return order confirmation
    These are synchronous (user is waiting)

  Non-critical (can happen after returning to user):
    Send confirmation email → Async (Kafka)
    Update analytics → Async (Kafka)
    Trigger fulfillment → Async (Kafka)

✓ Synchronous only for operations the user must wait for
✓ Async (events/queues) for everything else
✓ Circuit breakers on all synchronous downstream calls
```

---

## 4. God Service / Big Ball of Mud

### What Breaks Without Knowing This Anti-Pattern?

The "Backend Service" started as the API layer. Over 3 years, it accumulated: user auth, product catalog, order processing, payment, notifications, PDF generation, and analytics. It has 120 API endpoints. Deploying a one-line fix to notifications requires running the full 45-minute test suite for everything, because all tests are coupled in one repo. A memory leak in the PDF generation code causes OOM crashes that take down the order processing endpoints. The team of 25 engineers all work in the same service — merge conflicts are constant. Scaling the service for order volume means scaling (and paying for) all the other unrelated functionality.

**Why god services form**

It's the path of least resistance: adding a new feature to the existing service is easier than creating a new one. There's no governance rule stopping it. Over time, the service accumulates cross-cutting concerns and becomes load-bearing for everything. The invariant violated: **a service should have one reason to change — if it changes for many independent reasons, it has too many responsibilities.**

**The minimal fix**

Apply the Single Responsibility Principle at the service level. Each service maps to one bounded context (a cohesive domain that changes for one reason). Use the Strangler Fig pattern to extract capabilities incrementally rather than splitting all at once.

---

### What It Is

A service that does everything — authentication, business logic, reporting, notifications, and more. Starts as a small service, becomes unmaintainable as features accumulate.

### Signs

```
Warning signs:
  ✗ Service has > 10 REST API resource groups
  ✗ Service has > 50 database tables
  ✗ Single deployment takes > 10 minutes to test and deploy
  ✗ Every feature change touches the same service
  ✗ Team of > 15 people all working on the same service
```

### The Fix

Apply Domain-Driven Design (DDD) to identify bounded contexts:
```
Bounded Context 1: User Management (auth, profiles, preferences)
Bounded Context 2: Catalog (products, categories, pricing)
Bounded Context 3: Order Fulfillment (orders, inventory, shipping)
Bounded Context 4: Customer Support (tickets, refunds, escalations)
Bounded Context 5: Analytics (reporting, dashboards, recommendations)

Extract into services following the bounded context boundaries.
Don't split by technical layer (all DAOs, all controllers) — split by business domain.
```

---

## 5. N+1 Query Problem

### What Breaks Without Knowing This Anti-Pattern?

An order history page shows 100 orders with user names. Response time in development with 3 test orders: 50ms. Response time in production with real data: 4 seconds. The page makes 1 query to fetch 100 orders, then 100 individual queries (`SELECT * FROM users WHERE id = ?`) — one per order. This isn't visible in unit tests or small datasets. It surfaces only at scale, right when users and stakeholders are watching. Worse: with connection pooling, 100 simultaneous users loading the page generate 10,000 concurrent DB queries, saturating the connection pool and degrading the entire application.

**Why N+1 happens**

ORM lazy loading is the usual culprit: accessing `order.user.name` in a loop looks innocent but triggers a query per iteration. The abstraction hides the database interaction, making it easy to write O(N) query code that looks like O(1). The invariant violated: **the number of queries to the database must not scale with the size of the result set.**

**The minimal fix**

Batch: fetch all needed IDs up front, then `WHERE id IN (...)` in one query. With ORMs, use eager loading (`include` / `join fetch`) to load related entities in the same query. For complex cases, use a JOIN and let the DB do the work of combining tables once, not N times.

---

### What It Is

Loading a list of N items and then making N additional queries to fetch related data for each item.

```python
# BAD — N+1 queries
orders = db.query("SELECT * FROM orders LIMIT 100")   # 1 query
for order in orders:
    user = db.query(f"SELECT * FROM users WHERE id = {order.user_id}")  # 100 queries!
    # Total: 101 database queries for 100 orders
```

### The Fix

```python
# GOOD — 2 queries total
orders = db.query("SELECT * FROM orders LIMIT 100")
user_ids = [o.user_id for o in orders]
users = {u.id: u for u in db.query(f"SELECT * FROM users WHERE id IN ({user_ids})")}

for order in orders:
    user = users[order.user_id]
    # Total: 2 database queries for 100 orders

# OR: JOIN in SQL (when latency permits)
# SELECT o.*, u.name, u.email FROM orders o JOIN users u ON o.user_id = u.id LIMIT 100
```

---

## 6. Missing Idempotency (Unsafe Retries)

### What Breaks Without Knowing This Anti-Pattern?

A customer places an order. The POST request times out after 30 seconds. The client library automatically retries. Both the original and the retry succeed. The customer is charged twice and receives two order confirmations. Support ticket volume spikes. Finance has to process hundreds of duplicate refunds. More insidiously: a Kafka consumer crashes after processing a message but before committing the offset. On restart, it replays the same message — creating duplicate orders, duplicate shipments, duplicate emails. These bugs are invisible in testing (which doesn't simulate partial failures) and catastrophic in production.

**Why missing idempotency causes cascading failure**

Networks fail, timeouts occur, and consumers crash — all of these are normal events in distributed systems, not edge cases. Retry logic is correct behavior for reliability. The bug is that the server-side handler wasn't written to be safe under duplicate requests. The invariant violated: **any operation that can be retried or replayed must produce the same result if called multiple times with the same input.**

**The minimal fix**

Assign a unique idempotency key to every state-mutating operation (client-generated UUID). Server stores (key → result) before returning. On duplicate request with same key, return the stored result without re-executing. For Kafka consumers, include the event ID in the DB write (`INSERT ... ON CONFLICT (event_id) DO NOTHING`) so replays are no-ops.

---

### What It Is

Retrying non-idempotent operations when the original outcome is unknown (timeout, network failure).

```
BAD: Client sends POST /payments, times out.
     Client retries POST /payments.
     Both requests succeed → customer charged twice.

BAD: Consumer fails to process Kafka message, offsets not committed.
     Consumer restarts, replays message.
     Order created twice → two fulfillments shipped for one payment.
```

### The Fix

```
All state-mutating operations must be idempotent:

Client side:
  Generate idempotency key (UUID) per operation
  Include in every request: POST /payments (Idempotency-Key: uuid-abc)
  On retry: same idempotency key → server returns same result

Server side:
  Check idempotency key in DB before processing
  If key exists → return cached response (no re-execution)
  Store key + response atomically with the operation (same transaction)

Message consumers:
  Process messages by natural key (e.g. order_id)
  Check: "was order_id already processed?"
  If yes: skip (no-op)
  If no: process + mark as processed (in same transaction)
```

---

## 7. Premature Optimization (Wrong Scaling Solution)

### What Breaks Without Knowing This Anti-Pattern?

A startup with 500 users spends 3 months building a Kafka-based event-driven microservices architecture because "we might have 100M users someday." The ops complexity prevents the team from shipping features. The additional infrastructure surfaces bugs that wouldn't exist in a simpler system. The startup burns runway on infrastructure instead of product and runs out of money at 2,000 users. Meanwhile, competitors with monoliths ship faster and acquire the users. Instagram ran on a single server for its first 13M users — the premature optimizer would have never shipped at all.

**Why premature optimization fails**

Distributed systems, sharding, and event-driven architectures solve real problems — but only at scale. Before those problems exist, they introduce accidental complexity: harder debugging, more failure modes, more operational burden, slower development. The invariant violated: **architectural complexity must be justified by a concrete, present constraint — not a hypothetical future one.**

**The minimal fix**

Verify each assumption before building for it. Start with a single server and a relational DB. Add a read replica when reads are measurably slow. Add caching when the read replica isn't enough. Add sharding when a single write node is saturated. Add async queues when synchronous processing creates measurable bottlenecks. Each step is driven by evidence, not prediction.

---

### What It Is

Adding complex infrastructure (Kafka, microservices, sharding) before the simple solution has been proven to fail.

```
Anti-pattern conversation:
  "We might have 100M users someday, so let's shard from day 1."
  Day 1: 100 users. Ops team spends 3 months on sharding infrastructure.
  Sharding is done. Product team can't ship features because infra is complex.
  Startup runs out of money.

Reality:
  Instagram ran on a single server for its first 13M users.
  Stack Overflow still runs on 9 servers serving millions daily.
  WhatsApp served 450M users with 32 engineers and modest infrastructure.
```

### The Fix

```
Scale progressively — solve the actual problem you have, not the future one:

Phase 1 (0-10K users): Monolith + single DB + single server
Phase 2 (10K-1M): Monolith + separate DB server + read replica + CDN
Phase 3 (1M-10M): Split out high-traffic services + caching layer
Phase 4 (10M+): Microservices where justified + sharding where needed

Rule: Measure first. Add complexity only when you have evidence you need it.
```

---

## 8. Thundering Herd

### What Breaks Without Knowing This Anti-Pattern?

Redis goes down for 90 seconds. The cache is cold when it comes back. All 10,000 requests that have been queuing up hit the database simultaneously. The database handles 2,000 concurrent queries maximum — the other 8,000 are rejected or timeout. The database crashes. Now Redis is healthy but the database is down. The recovery causes a second outage worse than the first. A similar pattern plays out at midnight when all hourly cron jobs from 50 services run simultaneously, hammering the DB with expensive batch queries at the exact same second.

**Why thundering herds form**

Clients with synchronized retry timers, cached values with the same TTL, or scheduled tasks all waking up at the same moment create synchronized bursts. Recovery from failure attracts maximum traffic at the moment when capacity is lowest (the recovering service may have limited warm-up capacity). The invariant violated: **retry and refresh timing must be staggered — synchronized clients amplify failures instead of absorbing them.**

**The minimal fix**

Add jitter to every retry: `sleep(base_delay * 2^attempt + random(0, base_delay))`. Add jitter to cache TTLs: `TTL = base_TTL + random(-10%, +10%)`. For cache stampedes specifically, use probabilistic early expiration (recompute before TTL expires with probability that increases as expiry approaches) or a mutex/lock to allow only one request to recompute while others wait.

---

### What It Is

A large number of clients or workers simultaneously making requests to a recovering service, causing it to crash again.

```
Scenario:
  Cache goes down. 10,000 requests arrive → all miss cache → all hit DB simultaneously
  DB gets 10,000 concurrent queries → DB becomes overloaded → DB also crashes

Scenario 2:
  Service A goes down. 5,000 clients are retrying.
  Service A recovers. All 5,000 clients retry at the same moment → A crashes again.
```

### The Fix

```
1. Exponential backoff + jitter for retries
   wait = min(base × 2^attempt, max_delay) + random(0, jitter_factor × base)
   Spreads retry attempts across time

2. Cache stampede prevention:
   Option A: Mutex/lock — only one request fetches from DB on cache miss;
             others wait for the first request to populate cache
   Option B: Background refresh — proactively refresh cache before TTL expires;
             never have a cold miss on hot keys
   Option C: Probabilistic early expiration — probabilistically extend TTL
             slightly for popular keys

3. Request queue with rate limiting:
   After recovery, accept requests at a controlled rate (not all at once)
   Use a token bucket or leaky bucket at the service entry point
```

---

## 9. Chatty Services (Over-Communication)

### What Breaks Without Knowing This Anti-Pattern?

A user dashboard loads. Behind the scenes, the BFF (backend for frontend) makes 12 sequential API calls to 8 different services to assemble the page. Each call averages 30ms. Total latency: 360ms minimum, but they're partly sequential so real P99 is 800ms. One service — the recommendations service — has a bad deploy and starts responding in 2 seconds. The entire dashboard now takes 2+ seconds for every user. Meanwhile, 12 calls per page load × 1M DAU × 5 page loads/day = 60M inter-service calls/day, each with its own connection overhead, serialization, and TLS handshake cost. The network becomes a bottleneck for what was designed to be a data-fetching problem.

**Why chatty services emerge**

Fine-grained APIs designed for flexibility are called multiple times to build a single response. Services return generic data and rely on the caller to aggregate. When each service owns a narrow slice of data, the caller must make many calls to get a complete view. The invariant violated: **the number of network round trips to serve a request must be bounded and small — ideally 1 or 2.**

**The minimal fix**

Introduce an aggregator API (BFF pattern) that makes parallel calls internally and returns one composed response. Better: design APIs around consumer needs (one endpoint returns everything the dashboard needs). For read-heavy aggregation, materialize the aggregated view at write time into a dedicated read model rather than assembling at query time.

---

### What It Is

Microservices that make dozens of inter-service calls to assemble a single response. Each call adds latency and a failure point.

```
BAD: UserDashboard API makes 12 calls to render one page:
  1. GET /users/{id}
  2. GET /orders?userId={id}&limit=5
  3. GET /recommendations?userId={id}&limit=10
  4. GET /notifications?userId={id}&unread=true
  5. GET /wallet/{userId}/balance
  ... (7 more calls)

Page latency = sum of sequential calls or worst-case parallel latency
One slow service → entire dashboard is slow
```

### The Fix

```
Backend for Frontend (BFF) Pattern:
  Create a dedicated API aggregator per client type (mobile BFF, web BFF)
  BFF makes all 12 internal calls (in parallel) and assembles the response
  Client makes 1 call → BFF makes N parallel calls internally

GraphQL Federation:
  Each service exposes a GraphQL schema slice
  Gateway composes them; client requests exactly the fields needed
  Single round-trip from client; gateway parallelizes fetching

Denormalized read models (CQRS):
  Pre-assemble the dashboard data via event-driven projections
  Single query to read model → single fast response
  Avoids per-request fan-out entirely
```

---

## Quick Reference: Anti-Pattern Checklist

| Anti-Pattern | Warning Sign | Fix |
|-------------|-------------|-----|
| Distributed Monolith | Must deploy all services together | Event-driven, database-per-service |
| Shared Database | Service A writes to Service B's table | API calls or events for cross-service data |
| Sync call chains | Downstream failure crashes upstream | Async for non-critical path |
| God Service | > 10 resource groups in one service | Split by bounded context (DDD) |
| N+1 Queries | N queries inside a loop over N items | Batch query + in-memory join |
| Missing idempotency | Retries cause duplicates | Idempotency keys + consumer dedup |
| Premature optimization | Complex infra at 100 users | Scale progressively, measure first |
| Thundering herd | Retry storm after outage | Backoff + jitter; cache stampede prevention |
| Chatty services | 12 inter-service calls per request | BFF / GraphQL federation / read models |
