---
module: 02-building-blocks
status: unread
tags: [02-building-blocks, system-design, building-blocks]
---
# Sharding

> **Partitioning data across multiple databases or nodes to scale writes and storage beyond a single machine.**

---

## File Mindmap

```
Sharding
├── Why It Exists
│   ├── Problem → single PostgreSQL at 50K writes/sec ceiling; need 500K; vertical max = $100K/month machine
│   └── Forces → single-node DB has CPU / RAM / disk I/O hard ceiling; only option is horizontal partition
├── Shard Key — The Most Important Decision
│   ├── Determines data distribution across shards
│   ├── Bad key → hotspot (one shard gets 90% traffic, others idle)
│   ├── Good key → even spread; no cross-shard joins needed for common queries
│   └── Rule → choose key by access pattern, not schema convenience
├── Sharding Strategies
│   ├── Hash Sharding
│   │   ├── shard = hash(key) % N
│   │   ├── Even distribution; no hotspots for random keys
│   │   └── Cons → range queries span all shards; hard to add N (all keys remap)
│   ├── Range Sharding
│   │   ├── Shard 0: A–F, Shard 1: G–M, Shard 2: N–Z (or numeric ranges)
│   │   ├── Range queries efficient (scan one shard)
│   │   └── Cons → hotspot risk if key space not uniform (e.g. timestamps → all writes to latest shard)
│   ├── Directory Sharding
│   │   ├── Lookup table maps key → shard ID
│   │   ├── Most flexible; arbitrary mapping
│   │   └── Cons → lookup table is a bottleneck and SPOF; must be replicated
│   └── Consistent Hashing
│       ├── Keys and shards placed on a ring; key → clockwise next shard
│       ├── Add/remove shard → only adjacent keys remap (minimize reshuffling)
│       └── Virtual nodes → each physical shard has multiple ring positions for even load
├── Resharding
│   ├── Trigger → shard fills up or load skews
│   ├── Consistent hashing approach → add virtual node on ring; move only adjacent key range
│   ├── Directory approach → update lookup table + migrate data offline
│   └── Hash approach → N+1 modulo change remaps most keys (painful; requires full data migration)
├── Cross-Shard Operations
│   ├── Cross-shard queries → scatter-gather: query all shards, merge results in app layer
│   ├── Cross-shard transactions → avoid if possible; use Saga / 2PC (expensive)
│   └── Recommendation → denormalize to keep related data on same shard (shard by user_id if user data common join)
├── Hotspot Problem
│   ├── Cause → bad shard key (e.g. celebrity user_id, timestamp)
│   ├── Fix → add random suffix to key (user_id_0 … user_id_9); scatter writes; merge reads
│   └── Alternative → cache hot keys at app tier before they reach the shard
├── Trade-offs
│   ├── Pros → horizontal write scale; storage beyond single machine
│   └── Cons → cross-shard joins expensive; resharding painful; transactions complex
└── Interview Angles
    ├── "How do you pick a shard key?" → identify the dominant access pattern; key should co-locate related data
    ├── "What happens when a shard fills up?" → consistent hashing minimizes remapping; directory = update table
    ├── "Hash vs range sharding?" → hash: even distribution; range: efficient range queries but hotspot risk
    └── Follow-up: "How do you handle cross-shard transactions?" → Saga pattern; accept eventual consistency
```

---

## 1. Why Sharding Exists

**Question**: Your single PostgreSQL node handles 50,000 writes/sec. Business needs 500,000. Vertical scaling maxes out at ~$100k/month for a 128-core machine. What's the only option left?

**Physical constraint**: A single disk has one write head. SSDs saturate at ~500MB/s sequential, far less for random writes. A single PostgreSQL instance serializes WAL writes through one file. No matter how much you spend on hardware, one machine has one set of I/O bottlenecks — and the WAL is a single-writer log that cannot be parallelized on one node.

**Minimal solution**: Put users 0–49% on DB1, users 50–99% on DB2. Writes scale 2×. Works until: the split is uneven (all new signups land in one half if you're splitting by creation date), or you need to re-split (moving 50% of data requires a full migration with downtime).

**Production generalization**: Consistent hashing with virtual nodes ensures even distribution and minimizes data movement on reshard. The shard key choice determines everything — a bad key creates hotspots that defeat the entire point.

---

## 2. Core Principles

### Shard Key Choice

The shard key is the most important decision in your sharding design — it determines how data is distributed and which queries are efficient.

- **Critical**: Queries that don't include the shard key require scatter-gather (all shards) or a separate index.
- **Balance**: Key should distribute data and load evenly (avoid hot shards).
- **Growth**: Prefer keys that don't create hotspots (e.g. avoid "last N" always in one shard).

### Hotspot Problem

All bestsellers live in the Fiction section (floor 3). That floor is always packed while Science (floor 7) sits empty. The fix: split Fiction into Fiction A–M (floor 3) and Fiction N–Z (floor 4). In database terms: split the hot shard, or choose a different shard key (e.g. hash by book title instead of by genre).

### Strategies

| Strategy | How | Pros | Cons |
|----------|-----|------|------|
| **Hash-based** | `shard = hash(key) % N` | Even distribution | Resharding moves many keys; use consistent hash to reduce |
| **Range-based** | Shard 1: A–M, Shard 2: N–Z | Range queries on shard key | Hotspots (e.g. recent data in one shard) |
| **Directory-based** | Lookup table: key → shard | Flexible; move individual keys | Lookup table is bottleneck and SPOF |
| **Consistent hash** | Ring; key → next node clockwise | Adding/removing node moves ~1/N keys | Implementation complexity |

### Resharding Pain and Consistent Hashing

With `shard = hash(key) % 3` → `% 4`, nearly all keys change shards. Consistent hashing solves this: place shards on a ring. Adding a new shard only takes keys from its adjacent neighbor — roughly 1/4 of keys move when going from 3 to 4 shards instead of nearly all of them.

### Architecture

```
  App ──▶ Router (shard key → shard id)
              │
              ├──▶ Shard 1 (DB instance 1, user_id 0–33%)
              ├──▶ Shard 2 (DB instance 2, user_id 33–66%)
              └──▶ Shard 3 (DB instance 3, user_id 66–100%)
```

---

## 3. Real-World Usage

- **PostgreSQL / MySQL**: Application-level sharding (app routes by key); or Citus, Vitess.
- **MongoDB**: Sharding with shard key; config servers hold metadata.
- **DynamoDB**: Partition key (required); optional sort key; automatic sharding.
- **Kafka**: Partitions are shards; key determines partition.

---

## 4. Trade-offs

| Aspect | Pros | Cons |
|--------|------|------|
| **Hash** | Even distribution | No range queries across shards; resharding is costly |
| **Range** | Range queries on shard key | Risk of hot shards |
| **Directory** | Flexible placement | Lookup table scalability and HA |
| **Cross-shard operations** | N/A | Joins and transactions across shards are hard; prefer denormalization or application-level join |

**When to use**: Write or storage exceeds single-node capacity; you can design access patterns around shard key.  
**When not**: Single node suffices; or you need frequent cross-shard transactions (consider alternatives first).

---

## 5. Failure Scenarios

| Scenario | Mitigation |
|----------|------------|
| One shard down | Replicate each shard (primary + replica); failover per shard |
| Hot shard | Choose different shard key; split hot shard (range); or add more replicas for reads |
| Resharding | Double-write during migration; background data move; consistent hashing to minimize moves |
| Cross-shard query | Avoid or limit; use caching/denormalization; or accept scatter-gather cost |

---

## 6. Performance Considerations

- **Latency**: Single-shard queries are fast; scatter-gather increases latency and load.
- **Throughput**: Total write throughput scales with number of shards (if balanced).
- **Resharding**: Expensive; plan for growth so resharding is rare (e.g. consistent hashing with many virtual nodes).

---

## 7. Implementation Patterns

### Application-Level Shard Routing (Java)

```java
@Component
public class ShardRouter {
    private final List<DataSource> shards; // one DataSource per shard

    // Hash-based routing: consistent across all nodes
    public DataSource getShardFor(long userId) {
        int shardIndex = (int) (Math.abs(userId) % shards.size());
        return shards.get(shardIndex);
    }

    // Range-based routing: shard 0 = [0, 1M), shard 1 = [1M, 2M), etc.
    public DataSource getShardByRange(long userId) {
        int shardIndex = (int) (userId / 1_000_000);
        if (shardIndex >= shards.size()) shardIndex = shards.size() - 1;
        return shards.get(shardIndex);
    }
}

// Usage in repository:
public User findById(long userId) {
    DataSource ds = shardRouter.getShardFor(userId);
    return jdbcTemplate(ds).queryForObject(
        "SELECT * FROM users WHERE id = ?", userRowMapper, userId);
}
```

### Consistent Hashing for Resharding

```java
// TreeMap as a consistent hash ring
// Adding a new shard only remaps ~1/N of keys
TreeMap<Long, DataSource> ring = new TreeMap<>();

public void addShard(DataSource ds, int virtualNodes) {
    for (int i = 0; i < virtualNodes; i++) {
        long hash = hash(ds.toString() + "-" + i);
        ring.put(hash, ds);
    }
}

public DataSource getShardFor(String key) {
    long hash = hash(key);
    Map.Entry<Long, DataSource> entry = ring.ceilingEntry(hash);
    if (entry == null) entry = ring.firstEntry(); // wrap around
    return entry.getValue();
}
```

- **Application-managed**: App has shard map; routes queries by shard key; uses connection pool per shard.
- **Proxy**: Proxy (e.g. Vitess, ProxySQL) does routing; app sends same query.
- **Managed (DynamoDB, etc.)**: Choose partition key; system shards automatically.

---

## Quick Revision

- **Purpose**: Scale writes and storage by partitioning data across nodes.
- **Shard key**: Must be in most queries; should distribute evenly; consider growth.
- **Strategies**: Hash (even), range (range queries, hotspot risk), directory (flexible, lookup cost), consistent hash (minimal resharding).
- **Hotspot**: Hot shard = split it or change the key. Consistent hashing = only ~1/N data moves when adding a node.
- **Cross-shard**: Avoid joins/transactions; denormalize or application join.
- **Interview**: "We shard by user_id so each user's data is on one shard and we can scale by adding shards; we use consistent hashing so adding a node only moves about 1/N of the data."

---

## See Also

- **Replication** (works alongside sharding — shard for writes, replicate for reads): [02-building-blocks/replication.md](replication.md)
- **HLD problems where sharding is the core design decision**: [URL Shortener](../05-hld-problems/01-easy/url-shortener.md) (short code sharding), [Distributed Cache](../05-hld-problems/03-hard/distributed-cache.md) (consistent hashing ring), [Distributed Message Queue](../05-hld-problems/03-hard/distributed-message-queue.md) (partition = logical shard)
- **Scaling strategies context**: [03-scaling/scaling-strategies.md](../03-scaling/scaling-strategies.md)

---

## Interview Questions Asked

### Conceptual
1. **"How does consistent hashing minimize data movement when a node is added?"** → Nodes and keys are placed on a virtual ring by hash. Adding a node only takes keys from its immediate clockwise neighbor — approximately 1/N of the data moves, not a full reshuffle. Testing: understanding of why consistent hashing is the default for distributed caches and Kafka-style partitioning.
2. **"What is a cross-shard query and how do you handle it?"** → A query that needs data from multiple shards (e.g., SELECT across all users). Handle by: denormalizing data into one shard, scatter-gather (fan out query to all shards and merge), or maintaining a global secondary index. All options add latency or complexity — avoid cross-shard queries by choosing the right shard key. Testing: operational realism.
3. **"How do you reshard a live database without downtime?"** → Double-write to old and new shard during migration, backfill existing data in batches, then cut reads over with a feature flag, then stop writing to old shard. Alternatively use consistent hashing — only ~1/N data moves per new node. Tools: Vitess for MySQL, Citus for Postgres. Testing: production migration planning.

### Comparison / Trade-off
1. **"Range vs hash sharding — when to use each?"** → Range: natural for time-series or lexicographic scans (e.g., scan all orders from Jan–Mar) — but risks hotspots at range boundaries. Hash: even distribution, avoids hotspots — but range queries require scatter-gather. Use hash for user/account data, range for time-series with careful key design.

### Scenario / Design
1. **"How do you choose a sharding key?"** → Must appear in most queries (else scatter-gather). Should distribute writes evenly (avoid hotspots). Should not require frequent resharding as data grows. Example: shard by `user_id` for social apps (co-locates all user data), by `tenant_id` for SaaS, by `region` for geo-local data. Testing: whether you think about access patterns first.
2. **"What is the hotspot problem and how do you solve it?"** → One shard receives disproportionate traffic (e.g., a celebrity user, a trending hashtag). Solutions: split the hot shard, add a random suffix to the key to spread across sub-shards, cache the hot data in Redis, or use application-level fan-out. Testing: awareness that shard key choice determines long-term health of the system.
