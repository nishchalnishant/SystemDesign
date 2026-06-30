## 
> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** The full toolkit of scaling strategies — from vertical/horizontal scaling through database read/write scaling, caching, queues, and async processing.
>
> **Key topics:**
> - Vertical vs horizontal scaling: when each applies, cost curves, when vertical hits its ceiling
> - Database read scaling: read replicas, connection pooling (PgBouncer), consistent read routing
> - Database write scaling: sharding strategies (hash/range/directory), trade-offs and hotspot prevention
> - Caching: which tier (client, CDN, object, DB query, application), eviction policies, stampede prevention
> - Queues and async processing: decouple producers from slow consumers; buffer traffic spikes; retry semantics
> - Stateless services: prerequisite for horizontal app scaling; session state in Redis, not local memory
> - Autoscaling: horizontal pod autoscaler (K8s), CPU/RPS triggers, scale-in protection
>
> **Key takeaway:** Caching is always the first lever; sharding is the last resort — go through read replicas, connection pooling, and query optimization before you shard.

---
module: 03-scaling
status: unread
tags: [03-scaling, system-design, scaling]
---
# Scaling Strategies

> **How to scale systems: horizontal vs vertical, database scaling, replication, partitioning, caching, queues, and async processing.**

---

## File Mindmap

```
Scaling Strategies
├── Why It Exists
│   ├── Problem → single server at 70% CPU, traffic grows 20%/month → need 37,000 req/sec in 12 months
│   └── Physical limit → fastest single machine ~$200k, 2-week provision, single point of failure
├── Vertical vs Horizontal
│   ├── Vertical → bigger machine, simpler ops, hard ceiling, SPOF
│   └── Horizontal → commodity machines, linear cost, requires stateless services + LB
├── Database Read Scaling
│   ├── Read Replicas → async replication from primary, stale reads possible
│   ├── Write still goes to primary → read replicas don't help write bottlenecks
│   └── Connection Pooling → PgBouncer to reduce connection overhead
├── Database Write Scaling
│   ├── Sharding → split data across nodes by shard key
│   ├── Hash sharding → ConsistentHashRouter, even distribution, bad for range queries
│   ├── Range sharding → sequential access patterns, risk of hot shards
│   └── Directory sharding → lookup table, flexible, extra hop per query
├── Consistent Hashing
│   ├── Virtual nodes → each physical node owns multiple ring positions
│   ├── Adding node → only 1/N keys move (not full reshuffle)
│   └── Java: TreeMap<Long, String> ring + MD5 hash
├── Caching Patterns
│   ├── Cache-aside (lazy) → miss: fetch DB + populate cache; stale on write
│   ├── Write-through → write DB + cache simultaneously; consistent, double writes
│   └── Write-behind → write cache, async flush to DB; fast writes, risk data loss on crash
├── CQRS
│   ├── Separate write model (normalized) from read model (denormalized)
│   ├── Event drives projection updates → eventual consistency
│   └── Use when: read shape ≠ write shape, read-heavy, multiple read projections needed
├── Queue-Based Architecture
│   ├── Kafka listener → async processing decouples producer from consumer
│   ├── Absorbs bursts → consumer processes at steady rate
│   └── Trade-off: eventual consistency, harder to debug, at-least-once delivery
├── Async Processing
│   ├── Offload slow work (email, resizing, ML inference) to background workers
│   └── Return 202 Accepted immediately, poll or webhook for result
├── Trade-offs
│   ├── Pro: horizontal scale = near-linear throughput increase
│   ├── Con: distributed state = consistency problems
│   └── Con: operational complexity (service discovery, health checks, LB config)
└── Interview Angles
    ├── "How would you scale this system 10x?" → start with read replicas + caching
    ├── "When would you shard?" → when single-node write throughput is the bottleneck
    └── Follow-up: hot shard problem → salting / composite shard key
```

## Horizontal vs Vertical Scaling

**Question**: Your single server handles 5,000 req/sec at 70% CPU. Traffic grows 20% per month. In 12 months you need ~37,000 req/sec. The fastest single machine you can buy does ~100,000 req/sec — but it costs $200k, takes 2 weeks to provision, and if it dies your entire product is down. What do you do?

**Physical constraint**: A single CPU has a fixed number of cores. A single machine has a fixed memory bus bandwidth. There is a hard physical ceiling on what one box can do — and the price/performance curve bends sharply upward near that ceiling. Beyond ~32 cores and ~512GB RAM, doubling capacity more than doubles cost.

**Minimal solution**: Get a bigger machine (vertical scale). Buy a 64-core, 256GB RAM box. Works until: you hit the machine ceiling, the machine becomes a single point of failure, or provisioning lead time exceeds your growth rate.

**Generalize**: Add more machines behind a load balancer (horizontal scale). Each machine is commodity hardware — cheap, replaceable, independently deployable. The hard problem shifts from "make one box bigger" to "coordinate the fleet": routing, stateless design, data distribution. Every subsequent section in this file is a specialization of that coordination problem.

### Vertical Scaling (Scale Up)

- **What**: Add more CPU, RAM, or disk to the same machine.
- **Pros**: Simple; no distributed systems; no application changes.
- **Cons**: Hard limits (max instance size); single point of failure; often more expensive per unit capacity at high end.
- **When**: Early stage; quick fix; or components that are hard to distribute (e.g. legacy DB).

### Horizontal Scaling (Scale Out)

- **What**: Add more machines (nodes); distribute load and data across them.
- **Pros**: No single ceiling; can use cheaper hardware; fault tolerance (multiple nodes).
- **Cons**: Complexity (distribution, consistency, coordination); operational overhead.
- **When**: Growth beyond one machine; need for HA and elasticity.

### Comparison

| Aspect | Vertical | Horizontal |
|--------|----------|------------|
| **Complexity** | Low | High |
| **Limit** | Single machine max | Theoretically unlimited |
| **Failure** | Single point of failure | N-1 resilience |
| **Cost** | Often nonlinear at high end | Linear with nodes |
| **Application** | Usually no change | May need stateless design, sharding, etc. |

**Interview talking point**: "I'd start vertical — it's cheap and fast. Once I hit the machine ceiling or need HA, I'd move to horizontal scaling and redesign around stateless services so any node can handle any request."

---

## Caching Strategies

**Question**: Your API endpoint reads a product record on every request. The product changes once per hour. You have 50,000 req/sec hitting the DB for reads that return the same data. RAM access is ~100ns. Disk/network-backed DB query is ~1–5ms. That's 10,000–50,000x slower. Why is every read going to disk?

**Physical constraint**: DRAM latency ~100ns. Network round-trip to a DB on the same rack ~0.1ms. NVMe disk read ~0.1ms. A Postgres query doing an index scan with a buffer cache miss ~1–5ms. At 50,000 req/sec all hitting Postgres, that's 50,000 × 5ms = 250 seconds of DB CPU time per second — impossible on a single node. You need a layer that absorbs reads in DRAM.

**Minimal solution**: In-process HashMap. LRU eviction. Fixed TTL. Works until: multiple app server instances have inconsistent caches, a cache miss storms the DB (thundering herd), or a stale cache serves wrong data after a write.

**Generalize**: Redis. Shared across all app instances. Atomic operations for compare-and-swap invalidation. TTL for bounded staleness. Cache-aside (populate on miss) is simplest; write-through (update cache on every write) prevents cold misses but requires atomic DB+cache writes; write-behind (write cache, async to DB) maximizes write throughput but risks data loss.

- **Cache-aside**: App loads DB on miss and fills cache; good for read-heavy, variable access.
- **Write-through**: Write DB + cache; consistent but higher write cost.
- **Write-behind**: Write cache, async to DB; high write throughput; risk of loss.
- **TTL and invalidation**: Balance freshness vs hit rate; invalidate on write for critical data.

```java
@Service
public class ProductService {

    private final RedisTemplate<String, Product> cache;
    private final ProductRepository db;
    private static final Duration TTL = Duration.ofMinutes(10);

    // Cache-aside pattern
    public Product getProduct(String productId) {
        String key = "product:" + productId;

        Product cached = cache.opsForValue().get(key);
        if (cached != null) return cached;

        // Cache miss — load from DB and populate cache
        Product product = db.findById(productId)
            .orElseThrow(() -> new NotFoundException(productId));
        cache.opsForValue().set(key, product, TTL);
        return product;
    }

    // Write-through: update both DB and cache together
    public Product updateProduct(String productId, ProductUpdate update) {
        Product product = db.save(applyUpdate(productId, update));
        cache.opsForValue().set("product:" + productId, product, TTL);
        return product;
    }
}
```

**When**: Read-heavy; latency-sensitive; can tolerate staleness or explicit invalidation. See [02-building-blocks/caching-layer.md](../02-building-blocks/caching-layer.md).

---

## CQRS (Command Query Responsibility Segregation)

**Question**: Your order service runs complex joins across 6 tables to serve the "my orders" page. Each read query takes 50ms. You have 100,000 users loading that page per minute. The write model (create/update order) has a clean normalized schema. The read model needs a denormalized projection. Why are you using the same schema for both?

**Physical constraint**: A normalized write schema (3NF) minimizes write amplification — one logical update touches few rows. But reads that need data from 6 tables must JOIN across 6 indexes, which is multiple random disk seeks per query. A denormalized read model precomputes the JOIN and stores one wide row — one index seek per read. The trade-off is write amplification vs read efficiency.

**Minimal solution**: A separate denormalized read table, updated on every write. Works until: the read model update fails (now your read and write models are inconsistent), or you need multiple different read models for different clients.

**Generalize**: CQRS with event-driven projection. The write side emits events on every state change. Event handlers maintain separate read models per use case — each optimized for its query pattern. Consistency is eventual (read models lag by milliseconds). Each side can scale, be stored, and evolve independently.

```java
// Command side — normalized, transactional
@CommandHandler
public void handle(PlaceOrderCommand cmd) {
    Order order = new Order(cmd.getOrderId(), cmd.getUserId(), cmd.getItems());
    orderRepository.save(order);
    eventBus.publish(new OrderPlacedEvent(order));
}

// Query side — denormalized, optimized for reads
@EventHandler
public void on(OrderPlacedEvent event) {
    // Build a denormalized read model for fast queries
    OrderSummary summary = OrderSummary.builder()
        .orderId(event.getOrderId())
        .userName(userService.getName(event.getUserId()))
        .totalItems(event.getItems().size())
        .totalAmount(event.getTotal())
        .status("PLACED")
        .build();
    orderSummaryRepo.save(summary); // Can be a different DB, e.g. Elasticsearch
}

// Read side — fast, no joins needed
@QueryHandler
public List<OrderSummary> handle(GetOrdersByUserQuery query) {
    return orderSummaryRepo.findByUserId(query.getUserId());
}
```

**Trade-off**: Eventual consistency between command and query sides; more complexity; powerful for read-heavy systems with complex query requirements.

---

## Queue-Based Architectures

**Question**: Your order endpoint calls inventory, payment, and notification services synchronously. Each takes ~100ms. Total: 300ms per request, and your API is blocked waiting for all three to succeed. If the notification service is slow (it calls SendGrid, which is flaky), every order creation slows down. Why is user-facing request latency coupled to the availability of a notification service?

**Physical constraint**: CPU is fast (~0.3ns/cycle). Network RTT to the same DC is ~0.1ms. But an external API call (Stripe, SendGrid) might be 100–2000ms. During that time your thread is blocked — it's allocated, consuming stack memory (~1MB), but doing no useful work. At 1,000 concurrent requests each blocked for 500ms on external calls, you need 1,000 threads just to stand still.

**Minimal solution**: Decouple the slow operations. Accept the write, put it in a queue, return 202 Accepted immediately. Workers drain the queue asynchronously. Works until: the queue grows unboundedly (consumer too slow), messages are lost (queue not durable), or downstream failures leave messages stuck.

**Generalize**: Durable message queue (Kafka, SQS). Persistent storage prevents loss. Consumer groups enable parallel processing. Dead-letter queues handle poison messages. Backpressure (returning 503 when queue depth is too high) prevents unbounded queue growth.

- **Decouple producers and consumers**: API responds quickly; workers process asynchronously (emails, notifications, analytics).
- **Load leveling**: Spike in requests → queue absorbs; workers drain at steady rate.
- **Backpressure**: When queue or downstream is overloaded, slow or reject producers (e.g. 503, or backpressure in streams).

```java
// Producer: accept immediately, enqueue for async processing
@PostMapping("/orders")
public ResponseEntity<OrderAck> placeOrder(@RequestBody OrderRequest req) {
    String orderId = UUID.randomUUID().toString();
    // Enqueue — returns instantly; actual processing is async
    orderQueue.send(new OrderMessage(orderId, req));
    return ResponseEntity.accepted()
        .body(new OrderAck(orderId, "Processing — check status at /orders/" + orderId));
}

// Consumer: process at a controlled rate
@KafkaListener(topics = "orders", groupId = "order-processor")
public void processOrder(OrderMessage msg) {
    inventoryService.reserve(msg.getItems());
    paymentService.charge(msg.getUserId(), msg.getTotal());
    notificationService.sendConfirmation(msg.getUserId(), msg.getOrderId());
}
```

**When**: Async processing acceptable; need to absorb spikes or integrate with external systems. See [02-building-blocks/message-brokers.md](../02-building-blocks/message-brokers.md).

---

## Asynchronous Processing

- **Async I/O**: Non-blocking calls; one thread can handle many requests (e.g. Node.js, async/await, Java virtual threads).
- **Async workflows**: Request returns immediately; long-running work in queue + workers; notify when done (webhook, polling, or SSE).
- **Event-driven**: Services emit events; other services react; eventual consistency.

**Trade-off**: Simplicity and strong consistency (sync) vs scalability and resilience (async). Async requires idempotency and retry handling. See [04-advanced-topics/distributed-concepts.md](../04-advanced-topics/distributed-concepts.md) (idempotency, retry, backpressure).

---

## Decision Summary

| Goal | Strategy | Trade-off |
|------|----------|-----------|
| More read capacity | Read replicas, cache | Staleness, replication lag |
| More write capacity | Sharding, async writes | Complexity, eventual consistency |
| More storage | Sharding, archival | Resharding cost, query limits |
| High availability | Replication, multi-AZ | Consistency vs availability (CAP) |
| Lower latency | Cache, CDN, closer regions | Staleness, cost |
| Absorb spikes | Queues, async | Operational complexity, eventual consistency |
| Complex read/write needs | CQRS | Eventual consistency, dual model complexity |

---

## Quick Revision

- **Vertical**: Bigger machine; simple, limited. **Horizontal**: More machines; scalable, more complex.
- **Reads**: Replicas + cache. **Writes**: Sharding + optional async.
- **Replication**: Leader–follower common; sync vs async trade-off.
- **Sharding**: Hash/range/directory; design around shard key.
- **Caching**: Cache-aside common; TTL and invalidation for freshness.
- **Queues**: Decouple and level load; design for at-least-once and idempotency.
- **CQRS**: Separate read and write models; each optimized independently; eventual consistency between them.
- **Interview**: "We scale reads with read replicas and Redis cache; we scale writes by sharding by user_id when we outgrow one DB. We use queues for notifications and analytics so the API stays fast and we absorb traffic spikes."
