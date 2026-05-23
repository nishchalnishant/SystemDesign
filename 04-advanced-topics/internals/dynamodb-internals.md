---
module: 04-advanced-topics
topic: Internals
status: unread
tags: [04-advanced-topics, system-design, internals]
---
# DynamoDB Internals

> DynamoDB is a fully managed key-value and document database that delivers single-digit millisecond performance at any scale by combining consistent hashing, SSD storage, and adaptive capacity.

---

## File Mindmap

```
DynamoDB Internals
├── Why It Exists
│   ├── Problem → Relational DBs at Amazon's scale required massive operational overhead
│   └── Forces → Black Friday 2004: every team's service depended on a central Oracle instance
├── Core Concepts
│   ├── Partition key → determines storage node via consistent hash
│   ├── Sort key → range within partition; enables range queries
│   ├── WCU / RCU → capacity units; 1 WCU = 1 KB write, 1 RCU = 4 KB strongly consistent read
│   ├── LSI → Local Secondary Index; same partition key, different sort key; max 10 GB per partition
│   └── GSI → Global Secondary Index; different partition key; async replication, eventually consistent
├── Strategies / Types
│   ├── On-demand → auto-scale; pay per request → unpredictable traffic
│   └── Provisioned → fixed WCU/RCU; cheaper at steady load → predictable high throughput
├── Trade-offs
│   ├── Pro: Unlimited scale, single-digit ms, no schema migrations
│   └── Con: No joins, no arbitrary queries, access patterns must be modeled upfront
├── Failure Modes
│   └── Hot partition → throttling → use write sharding or cache
└── Interview Angles
    └── Partition key design → the #1 DynamoDB interview topic
```

---

## 1. Why DynamoDB Exists

**Question**: Amazon's 2004 shopping cart was backed by Oracle. During peak load, a single slow query could degrade the entire platform. Database engineers spent most of their time on operational tasks (backups, scaling, replication) instead of product features. A single node failure could take down checkout.

**Physical constraint**: Relational databases serialize writes through a single write leader and use a global lock manager for transactions. This makes horizontal scaling fundamentally hard — you can scale reads (read replicas) but writes hit a single bottleneck. A B+ tree index on a single node tops out at ~10,000-50,000 writes/s before lock contention dominates.

**Minimal solution**: Shard the database by customer ID using modulo hashing. Problem: adding shards requires full data migration, which takes days and causes downtime. Secondary indexes require scatter-gather across all shards.

**Production generalization**: The 2007 Dynamo paper described a design using consistent hashing (no migration on topology change), eventual consistency with vector clocks (no single write lock), and a gossip protocol for cluster membership. AWS DynamoDB (2012) implemented this with a managed control plane, adding strong consistency mode, GSIs, streams, and adaptive capacity.

---

## 2. Core Concepts / How It Works

### Data Model

```
Table: Orders
┌─────────────────────────────────────────────────────────┐
│ PK (Partition Key) │ SK (Sort Key)   │ Attributes        │
│ userId#12345       │ 2024-01-15#ord1 │ total: 59.99, ...│
│ userId#12345       │ 2024-02-01#ord2 │ total: 120.00    │
│ userId#99999       │ 2024-01-20#ord1 │ total: 25.50     │
└─────────────────────────────────────────────────────────┘
  All items for userId#12345 are co-located on the same partition.
  Range queries: SK between '2024-01' and '2024-02' → efficient.
```

### Partitioning Architecture

```
hash(partitionKey) → token → consistent hash ring → storage node
```

Each partition is limited to:
- 3,000 RCU/s of strongly consistent reads, or 12,000 RCU/s of eventually consistent reads
- 1,000 WCU/s
- 10 GB of data

When a partition exceeds these limits, DynamoDB automatically splits it (partition split). This is transparent to the application but is the source of the "hot partition" problem.

**Adaptive Capacity** (2019): DynamoDB allows a partition to temporarily "borrow" unused capacity from other partitions in the same table, up to the table-level provisioned limit. This smooths out moderate hotspots without throttling.

### Storage: B-Tree on SSD

Each partition is stored on a storage node with a B-tree index on the sort key. This is why:
- `Query` with a sort key condition is O(log N + K) — fast
- `Scan` is O(all items in table) — expensive, avoided in production

The storage layer uses the **Paxos** protocol (not the original Dynamo's vector clocks) for replication. DynamoDB replicates each partition to 3 AZs. A write is acknowledged when 2 of 3 replicas commit (quorum write). This gives strong consistency guarantees without vector clock complexity.

### Consistency Models

| Consistency Level       | How it works                                   | Cost    | Staleness risk |
|-------------------------|------------------------------------------------|---------|----------------|
| Eventually consistent   | Read from any replica                          | 0.5 RCU | Yes, up to ~ms |
| Strongly consistent     | Read from the write leader                     | 1 RCU   | No             |
| Transactional (2PC)     | Writes span multiple items using DynamoDB Tx   | 2 WCU   | No             |

### WCU / RCU Calculation

| Operation                | Capacity consumed                             |
|--------------------------|-----------------------------------------------|
| Write 1 KB item          | 1 WCU                                         |
| Write 3 KB item          | 3 WCU                                         |
| Strongly consistent read, 4 KB | 1 RCU                               |
| Strongly consistent read, 6 KB | 2 RCU (round up to next 4 KB)       |
| Eventually consistent read, 4 KB | 0.5 RCU                           |
| Transactional write, 1 KB | 2 WCU                                        |

**Practical calculation**: Table with 1M users, each user profile 2 KB, reading 10,000 profiles/s strongly consistent:
- RCU = ceil(2 KB / 4 KB) = 1 RCU per read
- Provisioned RCU = 10,000/s

### Secondary Indexes

#### LSI (Local Secondary Index)
- Same partition key, different sort key
- Data is co-located with the base table partition → strongly consistent reads possible
- Max 10 GB per partition key value (hard limit — cannot be increased)
- Defined at table creation time only

#### GSI (Global Secondary Index)
- Different partition key (and optional sort key)
- Stored as a separate partition structure internally
- Populated asynchronously → always eventually consistent
- Adds WCU consumption: writes to base table also trigger writes to GSI
- Can be added/removed at any time

```
Base table: PK=userId, SK=orderId
GSI-1:      PK=status, SK=createdAt   ← query all PENDING orders by time
GSI-2:      PK=productId, SK=userId   ← query all orders containing a product
```

### Conditional Writes

DynamoDB supports optimistic locking via condition expressions:

```java
// Only update if version matches (optimistic concurrency)
table.updateItem(UpdateItemEnhancedRequest.builder()
    .item(updatedItem)
    .conditionExpression("version = :expectedVersion")
    .expressionValues(Map.of(":expectedVersion", AttributeValue.fromN("5")))
    .build());
// Throws ConditionalCheckFailedException if version != 5
```

This is the DynamoDB equivalent of `UPDATE ... WHERE version = 5 AND affected_rows = 1`.

---

## 3. Real-World Usage

| Use case               | Partition key design                             | Notes                                   |
|------------------------|--------------------------------------------------|-----------------------------------------|
| Amazon shopping cart   | `customerId`                                     | Original Dynamo paper use case          |
| Session store          | `sessionId`                                      | TTL on items for auto-expiry            |
| Leaderboard            | `gameId` + GSI on score                          | Sort key = score for range queries      |
| IoT time-series        | `deviceId#YYYY-MM` + SK = `timestamp`            | Month bucketing prevents 10 GB LSI cap  |
| Multi-tenant SaaS      | `tenantId#entityType` + SK = `entityId`          | Single-table design                     |
| Distributed locks      | `lockName` + condition `attribute_not_exists(pk)`| TTL for automatic lock expiry           |

**Amazon's scale**: DynamoDB serves millions of requests per second across all AWS services. Prime Day 2023: DynamoDB handled 126 million requests/second peak.

**Single-Table Design**: A pattern where all entity types (users, orders, products) live in one DynamoDB table, distinguished by SK prefix. Eliminates cross-table joins (which DynamoDB doesn't support). Enables co-location of related entities on the same partition.

---

## 4. Trade-offs

| Dimension           | Pro                                                    | Con                                                          |
|---------------------|--------------------------------------------------------|--------------------------------------------------------------|
| Scalability         | Unlimited horizontal scale, no resharding downtime     | Partition key design is a one-time architectural decision     |
| Latency             | Single-digit ms at P99 for key-value lookups           | Complex queries (scans, multiple GSIs) degrade to 10-100 ms  |
| Operations          | Fully managed; no DBA needed                           | Less tuning control vs self-managed (no EXPLAIN, no hints)   |
| Query flexibility   | GSIs allow multiple access patterns                    | No joins, no aggregations, no arbitrary WHERE clauses        |
| Cost                | On-demand mode absorbs traffic spikes                  | Expensive at high sustained throughput vs RDS                |
| Consistency         | Strong consistency available at 2× cost of EC reads    | GSIs are always eventually consistent                        |

**DynamoDB vs PostgreSQL decision framework**:
- DynamoDB: access patterns are known, scale > 10K writes/s, want zero ops overhead
- PostgreSQL: complex queries, need JOINs, ad-hoc analytics, team familiar with SQL

---

## 5. Failure Scenarios

| Scenario                  | Symptom                                          | Mitigation                                                   |
|---------------------------|--------------------------------------------------|--------------------------------------------------------------|
| Hot partition             | `ProvisionedThroughputExceededException` on specific keys | Write sharding: append random suffix to partition key |
| GSI write throttling      | GSI WCU insufficient; base table writes back-pressure | Provision GSI WCU separately; use SQS buffer in front   |
| LSI 10 GB cap hit         | `ItemCollectionSizeLimitExceededException`       | Redesign with GSI; bucket partition key by time or hash      |
| AZ failure                | One of 3 replicas unavailable                    | DynamoDB auto-fails over to remaining 2 AZs, no action needed|
| Conditional write storms  | Thundering herd on contested items               | Exponential backoff with jitter in retry logic               |
| Missing index for access pattern | Full table scan at 1 MB/s max           | Add GSI proactively; use DynamoDB Streams to backfill        |

### Write Sharding for Hot Partitions

```
Hot partition: PK = "FLASH_SALE_PRODUCT_123"
                    ↓
Write sharding: PK = "FLASH_SALE_PRODUCT_123#shard_" + (random % 10)
On read: scatter-gather across shards 0-9, aggregate in application
```

---

## 6. Performance Considerations

**Latency targets**:
- `GetItem` (single key lookup): P50 < 1 ms, P99 < 5 ms
- `Query` (range on sort key, <100 items): P50 < 2 ms, P99 < 10 ms
- `Scan` (full table): throughput-limited at 1 MB/s per request; use parallel scan
- GSI `Query`: same as base table Query but adds ~0-2 ms for GSI routing

**Throughput limits**:
- Per partition: 3,000 RCU/s, 1,000 WCU/s
- Per table: no hard limit (scales with partitions); but pay attention to GSI limits
- On-demand mode: ~40,000 RCU/s and 40,000 WCU/s initial limit, auto-scales beyond

**DynamoDB Accelerator (DAX)**:
- In-memory cache in front of DynamoDB
- Read latency: 300 µs (vs 1-5 ms for DynamoDB direct)
- Write-through cache: writes go to both DAX and DynamoDB
- Adds ~$0.30/hr per node; worth it when reads > 100K/s on same keys

**Item size impact**: DynamoDB charges per KB of item size for reads/writes. Storing 100 KB JSON blobs as single items is expensive. Consider storing large blobs in S3 and keeping only metadata in DynamoDB.

---

## 7. Implementation Patterns

### Java AWS SDK v2 — DynamoDB Enhanced Client

```java
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@DynamoDbBean
public class Order {
    private String userId;      // partition key
    private String orderId;     // sort key
    private String status;
    private int version;        // optimistic locking

    @DynamoDbPartitionKey
    public String getUserId() { return userId; }

    @DynamoDbSortKey
    public String getOrderId() { return orderId; }

    @DynamoDbVersionAttribute
    public int getVersion() { return version; }
    // ... getters/setters
}

public class OrderRepository {
    private final DynamoDbTable<Order> table;

    public OrderRepository(DynamoDbEnhancedClient client) {
        this.table = client.table("Orders", TableSchema.fromBean(Order.class));
    }

    // Key-value lookup: O(1), ~1 ms
    public Optional<Order> findById(String userId, String orderId) {
        Key key = Key.builder()
            .partitionValue(userId)
            .sortValue(orderId)
            .build();
        return Optional.ofNullable(table.getItem(key));
    }

    // Range query: all orders for a user in Jan 2024
    public List<Order> findByUserAndMonth(String userId, String month) {
        QueryConditional condition = QueryConditional
            .sortBetween(
                Key.builder().partitionValue(userId).sortValue(month + "-01").build(),
                Key.builder().partitionValue(userId).sortValue(month + "-31").build()
            );
        return table.query(condition).items().stream().collect(Collectors.toList());
    }

    // Conditional write for optimistic locking (version auto-managed by @DynamoDbVersionAttribute)
    public void updateStatus(Order order, String newStatus) {
        order.setStatus(newStatus);
        table.putItem(order); // throws TransactionConflictException if version mismatch
    }
}
```

### GSI Query Pattern

```java
// GSI: PK=status, SK=createdAt — find all PENDING orders today
DynamoDbIndex<Order> statusIndex = table.index("status-createdAt-index");

QueryConditional condition = QueryConditional
    .sortGreaterThanOrEqualTo(
        Key.builder()
            .partitionValue("PENDING")
            .sortValue("2024-01-15")
            .build()
    );

List<Order> pendingOrders = statusIndex.query(condition)
    .stream()
    .flatMap(page -> page.items().stream())
    .collect(Collectors.toList());
```

### Batch Operations

```java
// BatchGetItem: fetch up to 100 items in one network round trip
List<Key> keys = userIds.stream()
    .map(id -> Key.builder().partitionValue(id).build())
    .collect(Collectors.toList());

// Unprocessed keys are returned if over capacity — handle retries
ReadBatch readBatch = ReadBatch.builder(Order.class)
    .mappedTableResource(table)
    .addGetItem(keys.get(0))
    // ...
    .build();

BatchGetResultPageIterable results = client.batchGetItem(
    BatchGetItemEnhancedRequest.builder().readBatches(readBatch).build()
);
```

---

## Quick Revision

- Partition key determines node via consistent hash; sort key enables range queries within partition
- Partition hard limits: 3,000 RCU/s, 1,000 WCU/s, 10 GB
- 1 WCU = 1 KB write; 1 RCU = 4 KB strongly consistent read; 0.5 RCU = eventually consistent
- LSI: same PK, different SK, co-located, strong consistency, 10 GB cap per PK, defined at creation
- GSI: different PK, separate partition, always eventually consistent, can add/remove anytime
- Adaptive Capacity borrows unused capacity across partitions to handle moderate hotspots
- Hot partition mitigation: write sharding (append random suffix), DAX (cache reads)
- Conditional writes use expression-based optimistic locking; `ConditionalCheckFailedException` = conflict
- Replication: 3 AZs, quorum write (2/3), Paxos protocol (not vector clocks in production DynamoDB)
- Single-table design: all entities in one table, distinguished by SK prefix; avoids cross-table scans

---

## See Also

- [02-building-blocks/consistent-hashing.md](../../02-building-blocks/consistent-hashing.md) — the hashing foundation
- [02-building-blocks/sharding.md](../../02-building-blocks/sharding.md) — partitioning strategies
- [02-building-blocks/caching-layer.md](../../02-building-blocks/caching-layer.md) — DAX as a cache layer
- [04-advanced-topics/internals/cassandra-internals.md](cassandra-internals.md) — similar architecture, open-source
- [04-advanced-topics/distributed-concepts.md](../distributed-concepts.md) — eventual consistency, quorum

---

## Interview Questions Asked

### Conceptual

**Q1: What happens when a DynamoDB partition reaches its 10 GB limit?**

A: DynamoDB performs an automatic partition split. The partition's key range and data are divided between two new partitions, each hosted on different storage nodes. The split is transparent to the application. However, if the hot partition was hot because of a specific partition key value (not data size), splitting does not help — the hot key still maps to one of the two new partitions and will hit its WCU/RCU limit. This is the hot partition problem, and it requires application-level write sharding.

**Q2: Explain the difference between LSI and GSI. When would you choose one over the other?**

A: LSI (Local Secondary Index): same partition key, alternate sort key. Data is stored alongside the base partition, so reads are strongly consistent. Limited to 10 GB per partition key value — hard cap. Must be defined at table creation time. Best for: alternate sort orders within a partition (e.g., sort orders by date AND by amount). GSI (Global Secondary Index): entirely different partition key. Stored as a separate distributed table internally. Populated asynchronously, so always eventually consistent. No storage cap per partition key. Can be added/removed without downtime. Best for: cross-partition access patterns (e.g., find all orders with status=PENDING across all users).

**Q3: What is adaptive capacity in DynamoDB and what problem does it solve?**

A: Adaptive capacity allows a hot partition to temporarily consume more than its allocated 1,000 WCU/s or 3,000 RCU/s share, by borrowing unused capacity from other partitions in the same table — as long as the table-level provisioned capacity is not exceeded. It solves moderate hotspot scenarios where one key gets 2-3× the average traffic, which previously caused throttling even when the overall table had spare capacity. It does not solve extreme hotspots where a single key needs 10× normal capacity — that requires write sharding.

### Comparison / Trade-off

**Q: When would you choose DynamoDB over PostgreSQL for a new service?**

A: Choose DynamoDB when: (1) all access patterns are known upfront (you can design PK/SK/GSI to serve them), (2) scale requirements exceed 10,000 writes/s or need to grow to 100,000+, (3) you want zero operational overhead (no DBA, no vacuuming, no index maintenance), (4) the data model is naturally key-value or document-shaped. Choose PostgreSQL when: (1) you need ad-hoc queries or aggregations, (2) the team is SQL-fluent and access patterns evolve frequently, (3) ACID transactions spanning many rows/tables are required, (4) cost at moderate scale (< 5,000 writes/s) is a concern — RDS is cheaper in that range.

### Scenario / Design

**Q: You're designing a DynamoDB table for a ride-sharing app. You need to: (1) look up a ride by rideId, (2) get all rides for a user sorted by time, (3) get all active rides in a city. Design the table.**

A: 
Base table: `PK=userId`, `SK=createdAt#rideId`. Supports (1) via GetItem(PK=userId, SK prefix = rideId) and (2) via Query(PK=userId) with SK sort. 

For (1) directly: add a GSI with `PK=rideId` — this lets you look up any ride directly.

For (3): add a GSI with `PK=cityId#status` (e.g., `NYC#ACTIVE`), `SK=createdAt`. Query this GSI to get all active rides in NYC sorted by time. Write sharding consideration: if NYC has 10,000 active rides being written simultaneously, `NYC#ACTIVE` becomes a hot GSI partition. Solution: `PK=cityId#status#shard` where shard = `random(0,9)`, then scatter-gather across 10 shards on read with parallel queries.
