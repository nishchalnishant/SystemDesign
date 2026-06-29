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
│   └── Python: bisect + dict ring + MD5 hash
├── Caching Patterns
│   ├── Cache-aside (lazy) → miss: fetch DB + populate cache; stale on write
│   ├── Write-through → write DB + cache simultaneously; consistent, double writes
│   └── Write-behind → write cache, async flush to DB; fast writes, risk data loss on crash
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

## 1. Horizontal vs Vertical Scaling

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

## 2. Database Scaling

### Read Scaling

**Question**: Your Postgres primary handles 1,000 reads/sec and 100 writes/sec. Read load triples from a new feature. The primary is at 90% CPU. Writes can't move — the primary must stay. How do you serve 3,000 reads/sec without adding write risk?

**Physical constraint**: A spinning disk does ~100-200 random reads/sec. An SSD does ~10,000. A modern NVMe does ~500,000. But all reads on the same disk share the same physical head / controller. Two concurrent reads are slower than one. At some point adding more reads to a single node degrades all reads.

**Minimal solution**: Add a read replica. The primary writes to its WAL; the replica streams the WAL and applies it. Reads go to the replica. Works until: replica falls behind (replication lag), you have more reads than one replica can serve, or you need guaranteed freshness (the replica might be 500ms stale).

**Generalize**: Multiple read replicas behind a load balancer. Add a cache layer (Redis) in front — targets 90%+ hit rate so most reads never reach the DB at all. Route critical reads (balance checks, post-write reads) to the primary. Accept eventual consistency for non-critical reads.

- **Read replicas**: Primary takes writes; replicas replicate (async or sync); reads go to replicas.
- **Caching**: Cache hot data in front of DB (Redis, Memcached); reduces DB read load.
- **CDN**: For static or cacheable content; offload entirely from DB.

**Trade-off**: Replicas can lag; cache can be stale. Use "read-your-writes" (route user's reads to primary or same replica) when needed.

```python
# Route reads to replica, writes to primary
class UserRepository:
    def __init__(self, primary_db, replica_db):
        self.primary = primary_db
        self.replica = replica_db

    def update_profile(self, user_id: str, name: str) -> None:
        self.primary.execute("UPDATE users SET name=%s WHERE id=%s", (name, user_id))

    def find_by_id(self, user_id: str) -> dict:
        # Read from replica (may be slightly stale — acceptable for profile reads)
        return self.replica.query_one("SELECT * FROM users WHERE id=%s", (user_id,))
```

---

### Write Scaling

**Question**: Your primary DB takes 2,000 writes/sec. Postgres saturates at ~10,000–50,000 simple writes/sec per node depending on write complexity. You're at 20% of ceiling now, but writes grow with users. At what point does a single primary become the bottleneck, and what do you do when it does?

**Physical constraint**: Every write must hit the WAL (sequential disk write, ~0.1ms) and eventually flush to data pages. A single disk has a fixed IOPS ceiling. At 10,000 writes/sec you're doing 10,000 WAL entries/sec. Beyond a certain point, WAL write serialization becomes the bottleneck regardless of RAM or CPU.

**Minimal solution**: Vertical scale the primary (bigger machine, NVMe SSDs, more RAM for write buffers). Works until you hit the machine ceiling or your table exceeds what one instance can hold without index degradation.

**Generalize**: Shard by a partition key. Each shard holds a fraction of the data and absorbs a fraction of writes. The hard problems are: choosing the shard key (must distribute evenly and align with query patterns), routing (which shard for this key?), and resharding (when one shard fills up, you split it — painful under live traffic). Use consistent hashing to minimize data movement on resize.

- **Sharding**: Partition data by key across multiple DB instances; each shard takes a fraction of writes.
- **Async writes**: Accept write in API, persist to queue, workers write to DB (write-behind); increases write throughput and smooths spikes.
- **Batching**: Group many small writes into fewer large writes.

**Trade-off**: Sharding adds complexity (routing, resharding, cross-shard queries); async writes add eventual consistency and operational complexity.

---

### Storage Scaling

- **Sharding**: More shards → more total storage.
- **Archival**: Move old data to cold storage (e.g. S3, Glacier); keep hot data in primary DB.
- **Compression and encoding**: Reduce size per row; more rows per node.

---

## 3. Replication Strategies

- **Leader–follower**: One primary, N replicas; simple; read scaling and HA. See [02-building-blocks/replication.md](../02-building-blocks/replication.md).
- **Multi-leader**: Multiple primaries (e.g. per region); conflict resolution required.
- **Leaderless (quorum)**: W + R > N for consistency; tunable W, R for latency vs durability.

**When**: Replication for HA and read scaling; multi-leader only when you need writes in multiple regions and can handle conflicts.

---

## 4. Partitioning (Sharding) Strategies

**Question**: You have 100M user rows, growing 10M/month. In 18 months you'll have 280M rows. A single Postgres table at that size still works, but indexes grow proportionally and certain write patterns (especially secondary index updates) start to slow down. When do you shard, and how do you pick the partition key so you don't create a bigger problem than you solved?

**Physical constraint**: A B-tree index node is 8KB. A 280M-row table with 3 indexes has index pages totaling several GB. Fitting them in buffer cache requires proportionally more RAM. Index writes (random I/O to update B-tree pages) scale superlinearly with table size because the tree gets deeper and buffer cache hit rate drops.

**Minimal solution**: `shard = hash(user_id) % N`. Spreads writes evenly. Works until: you add a shard (modulo changes, you must move ~(N-1)/N of all data), or you need range queries across shards (impossible with hash partitioning).

**Generalize**: Consistent hashing. Virtual nodes on a ring mean adding one shard moves ~1/N of data, not (N-1)/N. Directory-based routing (lookup table) adds flexibility at the cost of a lookup bottleneck. Range-based sharding enables range queries but risks hotspots on monotonically increasing keys (e.g. timestamp).

- **Hash-based**: `shard = hash(key) % N`; even distribution; resharding costly (use consistent hashing to reduce moves).
- **Range-based**: Ranges of key (e.g. A–M, N–Z); good for range queries; risk of hotspots.
- **Directory-based**: Lookup table key → shard; flexible but lookup can be bottleneck.

```python
# See 02-building-blocks/consistent-hashing.md for full implementation
# Key idea: bisect.bisect_right(sorted_keys, hash(key)) % len → O(log n) lookup
import hashlib, bisect

class ConsistentHashRouter:
    def __init__(self, virtual_nodes: int = 150):
        self.vnodes = virtual_nodes
        self.ring: dict[int, str] = {}
        self.keys: list[int] = []

    def add_node(self, node: str) -> None:
        for i in range(self.vnodes):
            h = int(hashlib.md5(f"{node}-vnode-{i}".encode()).hexdigest(), 16)
            self.ring[h] = node
            bisect.insort(self.keys, h)

    def get_node(self, key: str) -> str:
        h = int(hashlib.md5(key.encode()).hexdigest(), 16)
        idx = bisect.bisect_right(self.keys, h) % len(self.keys)
        return self.ring[self.keys[idx]]
```

**When**: Write or storage exceeds single node; design access patterns around shard key to avoid cross-shard queries.

---

## 5. Caching Strategies

**Question**: Your API endpoint reads a product record on every request. The product changes once per hour. You have 50,000 req/sec hitting the DB for reads that return the same data. RAM access is ~100ns. Disk/network-backed DB query is ~1–5ms. That's 10,000–50,000x slower. Why is every read going to disk?

**Physical constraint**: DRAM latency ~100ns. Network round-trip to a DB on the same rack ~0.1ms. NVMe disk read ~0.1ms. A Postgres query doing an index scan with a buffer cache miss ~1–5ms. At 50,000 req/sec all hitting Postgres, that's 50,000 × 5ms = 250 seconds of DB CPU time per second — impossible on a single node. You need a layer that absorbs reads in DRAM.

**Minimal solution**: In-process HashMap. LRU eviction. Fixed TTL. Works until: multiple app server instances have inconsistent caches, a cache miss storms the DB (thundering herd), or a stale cache serves wrong data after a write.

**Generalize**: Redis. Shared across all app instances. Atomic operations for compare-and-swap invalidation. TTL for bounded staleness. Cache-aside (populate on miss) is simplest; write-through (update cache on every write) prevents cold misses but requires atomic DB+cache writes; write-behind (write cache, async to DB) maximizes write throughput but risks data loss.

- **Cache-aside**: App loads DB on miss and fills cache; good for read-heavy, variable access.
- **Write-through**: Write DB + cache; consistent but higher write cost.
- **Write-behind**: Write cache, async to DB; high write throughput; risk of loss.
- **TTL and invalidation**: Balance freshness vs hit rate; invalidate on write for critical data.

```python
import redis, json
from typing import Optional

TTL = 600  # 10 minutes

class ProductService:
    def __init__(self, cache: redis.Redis, db):
        self.cache = cache
        self.db = db

    def get_product(self, product_id: str) -> dict:
        key = f"product:{product_id}"
        cached = self.cache.get(key)
        if cached:
            return json.loads(cached)
        product = self.db.find_by_id(product_id)
        self.cache.setex(key, TTL, json.dumps(product))
        return product

    def update_product(self, product_id: str, update: dict) -> dict:
        product = self.db.update(product_id, update)
        self.cache.setex(f"product:{product_id}", TTL, json.dumps(product))
        return product
```

**When**: Read-heavy; latency-sensitive; can tolerate staleness or explicit invalidation. See [01-foundations/caching-cdn.md](../01-foundations/caching-cdn.md) and [02-building-blocks/caching-layer.md](../02-building-blocks/caching-layer.md).

---

## 6. Queue-Based Architectures

**Question**: Your order endpoint calls inventory, payment, and notification services synchronously. Each takes ~100ms. Total: 300ms per request, and your API is blocked waiting for all three to succeed. If the notification service is slow (it calls SendGrid, which is flaky), every order creation slows down. Why is user-facing request latency coupled to the availability of a notification service?

**Physical constraint**: CPU is fast (~0.3ns/cycle). Network RTT to the same DC is ~0.1ms. But an external API call (Stripe, SendGrid) might be 100–2000ms. During that time your thread is blocked — it's allocated, consuming stack memory (~1MB), but doing no useful work. At 1,000 concurrent requests each blocked for 500ms on external calls, you need 1,000 threads just to stand still.

**Minimal solution**: Decouple the slow operations. Accept the write, put it in a queue, return 202 Accepted immediately. Workers drain the queue asynchronously. Works until: the queue grows unboundedly (consumer too slow), messages are lost (queue not durable), or downstream failures leave messages stuck.

**Generalize**: Durable message queue (Kafka, SQS). Persistent storage prevents loss. Consumer groups enable parallel processing. Dead-letter queues handle poison messages. Backpressure (returning 503 when queue depth is too high) prevents unbounded queue growth.

- **Decouple producers and consumers**: API responds quickly; workers process asynchronously (emails, notifications, analytics).
- **Load leveling**: Spike in requests → queue absorbs; workers drain at steady rate.
- **Backpressure**: When queue or downstream is overloaded, slow or reject producers (e.g. 503, or backpressure in streams).

```python
# Producer: accept immediately, enqueue for async processing
from confluent_kafka import Producer
import uuid, json

producer = Producer({"bootstrap.servers": "kafka:9092"})

def place_order(req: dict) -> dict:
    order_id = str(uuid.uuid4())
    producer.produce("orders", key=order_id, value=json.dumps({**req, "order_id": order_id}))
    producer.flush()
    return {"order_id": order_id, "status": "processing"}

# Consumer: process at a controlled rate
from confluent_kafka import Consumer

consumer = Consumer({"bootstrap.servers": "kafka:9092", "group.id": "order-processor"})
consumer.subscribe(["orders"])

def run_consumer():
    while True:
        msg = consumer.poll(1.0)
        if msg and not msg.error():
            data = json.loads(msg.value())
            inventory_service.reserve(data["items"])
            payment_service.charge(data["user_id"], data["total"])
            notification_service.send_confirmation(data["user_id"], data["order_id"])
```

**When**: Async processing acceptable; need to absorb spikes or integrate with external systems. See [02-building-blocks/message-brokers.md](../02-building-blocks/message-brokers.md).

---

## 7. Asynchronous Processing

- **Async I/O**: Non-blocking calls; one thread can handle many requests (e.g. Node.js, async/await, Java virtual threads).
- **Async workflows**: Request returns immediately; long-running work in queue + workers; notify when done (webhook, polling, or SSE).
- **Event-driven**: Services emit events; other services react; eventual consistency.

**Trade-off**: Simplicity and strong consistency (sync) vs scalability and resilience (async). Async requires idempotency and retry handling. See [04-advanced-topics/distributed-concepts.md](../04-advanced-topics/distributed-concepts.md) (idempotency, retry, backpressure).

---

## 8. Decision Summary

| Goal | Strategy | Trade-off |
|------|----------|-----------|
| More read capacity | Read replicas, cache | Staleness, replication lag |
| More write capacity | Sharding, async writes | Complexity, eventual consistency |
| More storage | Sharding, archival | Resharding cost, query limits |
| High availability | Replication, multi-AZ | Consistency vs availability (CAP) |
| Lower latency | Cache, CDN, closer regions | Staleness, cost |
| Absorb spikes | Queues, async | Operational complexity, eventual consistency |

---

## Quick Revision

- **Vertical**: Bigger machine; simple, limited. **Horizontal**: More machines; scalable, more complex.
- **Reads**: Replicas + cache. **Writes**: Sharding + optional async.
- **Replication**: Leader–follower common; sync vs async trade-off.
- **Sharding**: Hash/range/directory; design around shard key.
- **Caching**: Cache-aside common; TTL and invalidation for freshness.
- **Queues**: Decouple and level load; design for at-least-once and idempotency.
- **Interview**: "We scale reads with read replicas and Redis cache; we scale writes by sharding by user_id when we outgrow one DB. We use queues for notifications and analytics so the API stays fast and we absorb traffic spikes."
