# Scaling Strategies

> **How to scale systems: horizontal vs vertical, database scaling, replication, partitioning, caching, queues, and async processing.**

---

## 1. Concept Overview

**Scaling** is increasing a system's capacity to handle more load (traffic, data, or both). Strategies differ by **what** you scale (compute, storage, reads, writes) and **how** (vertical vs horizontal, replication vs sharding, sync vs async).

**Why it matters**: At SDE-3 level you are expected to choose and justify scaling strategies and explain trade-offs (cost, complexity, consistency, operations).

---

## 2. Horizontal vs Vertical Scaling

### Vertical Scaling (Scale Up)

> **Analogy**: Upgrading the engine in a single truck. A bigger engine moves more cargo, and it works — up to a point. There's a physical limit to how powerful one engine can be. Beyond a certain size, the truck becomes exponentially more expensive and still has a single point of failure: if this one truck breaks down, nothing moves.

- **What**: Add more CPU, RAM, or disk to the same machine.
- **Pros**: Simple; no distributed systems; no application changes.
- **Cons**: Hard limits (max instance size); single point of failure; often more expensive per unit capacity at high end.
- **When**: Early stage; quick fix; or components that are hard to distribute (e.g. legacy DB).

---

### Horizontal Scaling (Scale Out)

> **Analogy**: Adding more trucks. Instead of one super-truck, you have a fleet of 1,000 ordinary trucks. No single truck needs to be special. If one breaks down, the other 999 keep running. The challenge shifts from "build a bigger truck" to "coordinate the fleet" — routing, scheduling, ensuring each truck knows what to carry and where to go.

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

## 3. Database Scaling

### Read Scaling

> **Analogy**: A bestselling book. Instead of one copy that everyone fights over, the publisher prints 10 copies. Everyone reads their own copy simultaneously, with no contention. But only the author (the primary) can write new editions. The copies (replicas) receive updates, but with a slight delay — so for a moment, some readers might be on the old edition while others got the new one.

- **Read replicas**: Primary takes writes; replicas replicate (async or sync); reads go to replicas.
- **Caching**: Cache hot data in front of DB (Redis, Memcached); reduces DB read load.
- **CDN**: For static or cacheable content; offload entirely from DB.

**Trade-off**: Replicas can lag; cache can be stale. Use "read-your-writes" (route user's reads to primary or same replica) when needed.

```java
// Route reads to replica, writes to primary
@Service
public class UserRepository {

    @Qualifier("primaryDataSource")
    private final JdbcTemplate primary;

    @Qualifier("replicaDataSource")
    private final JdbcTemplate replica;

    public void updateProfile(String userId, String name) {
        primary.update("UPDATE users SET name=? WHERE id=?", name, userId);
    }

    public User findById(String userId) {
        // Read from replica (may be slightly stale — acceptable for profile reads)
        return replica.queryForObject("SELECT * FROM users WHERE id=?",
            userRowMapper, userId);
    }
}
```

---

### Write Scaling

- **Sharding**: Partition data by key across multiple DB instances; each shard takes a fraction of writes. See [02-building-blocks/sharding.md](../02-building-blocks/sharding.md).
- **Async writes**: Accept write in API, persist to queue, workers write to DB (write-behind); increases write throughput and smooths spikes.
- **Batching**: Group many small writes into fewer large writes (e.g. time or count threshold).

**Trade-off**: Sharding adds complexity (routing, resharding, cross-shard queries); async writes add eventual consistency and operational complexity.

---

### Storage Scaling

- **Sharding**: More shards → more total storage.
- **Archival**: Move old data to cold storage (e.g. S3, Glacier); keep hot data in primary DB.
- **Compression and encoding**: Reduce size per row; more rows per node.

---

## 4. Replication Strategies

- **Leader–follower**: One primary, N replicas; simple; read scaling and HA. See [02-building-blocks/replication.md](../02-building-blocks/replication.md).
- **Multi-leader**: Multiple primaries (e.g. per region); conflict resolution required.
- **Leaderless (quorum)**: W + R > N for consistency; tunable W, R for latency vs durability.

**When**: Replication for HA and read scaling; multi-leader only when you need writes in multiple regions and can handle conflicts.

---

## 5. Partitioning (Sharding) Strategies

- **Hash-based**: `shard = hash(key) % N`; even distribution; resharding costly (use consistent hashing to reduce moves).
- **Range-based**: Ranges of key (e.g. A–M, N–Z); good for range queries; risk of hotspots.
- **Directory-based**: Lookup table key → shard; flexible but lookup can be bottleneck.

```java
// Consistent hashing for shard routing
public class ConsistentHashRouter {
    private final TreeMap<Long, String> ring = new TreeMap<>();
    private static final int VIRTUAL_NODES = 150;

    public void addNode(String node) {
        for (int i = 0; i < VIRTUAL_NODES; i++) {
            long hash = hash(node + "-vnode-" + i);
            ring.put(hash, node);
        }
    }

    public String getNode(String key) {
        if (ring.isEmpty()) throw new IllegalStateException("No nodes");
        long hash = hash(key);
        Map.Entry<Long, String> entry = ring.ceilingEntry(hash);
        // Wrap around the ring if we're past the last node
        return (entry != null ? entry : ring.firstEntry()).getValue();
    }

    private long hash(String key) {
        // Use MurmurHash or SHA-256 in production
        return Math.abs((long) key.hashCode());
    }
}
```

**When**: Write or storage exceeds single node; design access patterns around shard key to avoid cross-shard queries.

---

## 6. Caching Strategies

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

**When**: Read-heavy; latency-sensitive; can tolerate staleness or explicit invalidation. See [01-foundations/caching-cdn.md](../01-foundations/caching-cdn.md) and [02-building-blocks/caching-layer.md](../02-building-blocks/caching-layer.md).

---

## 7. CQRS (Command Query Responsibility Segregation)

> **Analogy**: A library with separate borrowing desks and return desks. The return desk (writes) is optimized for processing returns quickly — it just needs to record what came back. The borrowing desk (reads) is optimized for helping you search the catalog and find books — it maintains a rich index. Each desk is good at one thing. You'd never route a complex catalog search through the return desk just because it's less busy.

**Pattern**: Separate the write model (commands) from the read model (queries). Each can be scaled, optimized, and evolved independently.

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

## 8. Queue-Based Architectures

> **Async processing analogy**: A ticket queue at a government office. You walk in and take a number (the API immediately acknowledges your request). You sit down and wait to be called (async processing begins). The front desk is not blocked while you're being served; it immediately hands out the next number. The waiting room is the queue — it absorbs bursts so the desk never gets mobbed.

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

## 9. Asynchronous Processing

- **Async I/O**: Non-blocking calls; one thread can handle many requests (e.g. Node.js, async/await, Java virtual threads).
- **Async workflows**: Request returns immediately; long-running work in queue + workers; notify when done (webhook, polling, or SSE).
- **Event-driven**: Services emit events; other services react; eventual consistency.

**Trade-off**: Simplicity and strong consistency (sync) vs scalability and resilience (async). Async requires idempotency and retry handling. See [04-advanced-topics/distributed-concepts.md](../04-advanced-topics/distributed-concepts.md) (idempotency, retry, backpressure).

---

## 10. Decision Summary

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
