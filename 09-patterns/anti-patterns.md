# System Design Anti-Patterns

> **The most common architectural mistakes in distributed systems and how to avoid them. Knowing what NOT to build is as important as knowing what to build.**

---

## 1. The Distributed Monolith

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
