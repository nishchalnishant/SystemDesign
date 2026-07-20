# Sharding in System Design Interviews

> **Source**: [Sharding in System Design Interviews w/ Meta Staff Engineer](https://www.youtube.com/watch?v=1HOVtQ-_fcE&list=PL5q3E8eRUieVFeK1oLahJ8KONkAxDpqk2&index=4)

---

## What is Sharding?

**Sharding** (horizontal partitioning) is a database scaling technique that distributes data across multiple database instances (shards). Each shard holds a subset of the total data.

---

## Why Shard?

- **Scale beyond a single machine**: One DB can't handle all data/traffic
- **Improve performance**: Queries hit smaller datasets
- **Increase throughput**: Distribute read/write load
- **Geographic distribution**: Place data closer to users

---

## Vertical Scaling vs Horizontal Scaling

| Approach | Description | Limit |
|---|---|---|
| **Vertical scaling** | Bigger machine (more CPU, RAM, disk) | Hardware ceiling; expensive |
| **Horizontal scaling (Sharding)** | More machines, distribute data | Virtually unlimited; complex |

---

## Sharding Strategies

### 1. Range-Based Sharding
```
Shard 1: user_id 1 - 1,000,000
Shard 2: user_id 1,000,001 - 2,000,000
Shard 3: user_id 2,000,001 - 3,000,000
```
- **Pros**: Simple to implement; range queries are efficient
- **Cons**: Uneven distribution (hotspots); new users all hit the latest shard

### 2. Hash-Based Sharding
```
shard_id = hash(shard_key) % num_shards
```
- **Pros**: Even data distribution; no hotspots
- **Cons**: Range queries require scatter-gather; resharding is expensive

### 3. Directory-Based Sharding
```
Lookup Table:
  user_123 → Shard A
  user_456 → Shard B
  user_789 → Shard C
```
- **Pros**: Flexible; easy to move data between shards
- **Cons**: Lookup table is a single point of failure; extra hop for every query

### 4. Geographic Sharding
```
Shard US: Users in North America
Shard EU: Users in Europe
Shard AP: Users in Asia-Pacific
```
- **Pros**: Data locality for users; regulatory compliance (GDPR)
- **Cons**: Cross-region queries are slow; uneven distribution

---

## Shard Key Selection

The **shard key** determines which shard a record belongs to. This is the **most critical decision** in sharding.

### Good Shard Key Properties
- **High cardinality**: Many distinct values (e.g., user_id)
- **Even distribution**: Data spread uniformly across shards
- **Query alignment**: Most queries filter by the shard key
- **Stable**: Doesn't change frequently (avoids re-sharding)

### Common Shard Keys
| Entity | Good Shard Key | Why |
|---|---|---|
| Users | `user_id` | Even distribution, most queries are per-user |
| Messages | `conversation_id` | Keeps conversation messages together |
| Orders | `user_id` or `order_id` | Depends on query pattern |
| Posts | `author_id` | Groups author's posts on one shard |

### Bad Shard Key Examples
- **Timestamp**: All recent writes go to one shard (hotspot)
- **Country**: Highly skewed distribution
- **Boolean fields**: Only 2 values, can't distribute

---

## Challenges of Sharding

### 1. Cross-Shard Queries
- Queries that span multiple shards require **scatter-gather**
- Significantly more expensive than single-shard queries
- Solution: Denormalize data, or use secondary indexes

### 2. Cross-Shard Joins
- Joins across shards are extremely expensive
- Solution: **Denormalization** — duplicate data to avoid joins
- Or use an **application-level join**

### 3. Resharding (Rebalancing)
- Adding/removing shards requires moving data
- With hash-based: `hash % N` changes for most keys when N changes
- Solution: **Consistent hashing** (minimizes data movement)

### 4. Referential Integrity
- Foreign keys across shards are not enforced
- Application must maintain consistency
- Use **eventual consistency** patterns

### 5. Hotspots
- Some shards receive disproportionate traffic (e.g., celebrity user)
- Solutions:
  - Add **salt** to shard key (splits hot key across shards)
  - Use **secondary sharding** for hot entities
  - **Rate limiting** on hot shards

### 6. Transactions
- Distributed transactions across shards are complex and slow
- Use **Saga pattern** or **eventual consistency**
- Avoid cross-shard transactions if possible

---

## Sharding vs Replication

| Feature | Sharding | Replication |
|---|---|---|
| **Purpose** | Scale writes + storage | Scale reads + availability |
| **Data** | Each node has subset | Each node has full copy |
| **Writes** | Distributed | Single primary |
| **Reads** | Query specific shard | Any replica |
| **Failure** | Lose subset of data | No data loss (replicas) |

### Best Practice: Combine Both
```
Shard 1 → Primary + Replica 1 + Replica 2
Shard 2 → Primary + Replica 1 + Replica 2
Shard 3 → Primary + Replica 1 + Replica 2
```

---

## Resharding Strategies

### 1. Consistent Hashing
- Minimizes data movement when adding/removing shards
- Only K/N keys need to move (K = total keys, N = number of shards)
- Used by: DynamoDB, Cassandra, consistent hash ring

### 2. Virtual Shards
- Create many more logical shards than physical nodes
- Map multiple virtual shards to each physical node
- Rebalancing = move virtual shards between nodes

### 3. Double-Write Migration
```
Phase 1: Write to both old and new sharding scheme
Phase 2: Migrate existing data
Phase 3: Switch reads to new scheme
Phase 4: Stop writing to old scheme
```

---

## When to Shard

### Consider Sharding When:
- Single DB can't handle the data volume
- Read/write performance is degrading
- Need to scale beyond vertical limits
- Need geographic data distribution

### Avoid Sharding When:
- Data fits on a single machine
- Can solve with read replicas or caching
- Application logic heavily uses cross-entity joins
- The added complexity isn't justified

---

## Interview Tips

1. **Don't shard prematurely** — start with read replicas and caching first
2. **Shard key selection** is the most important decision — discuss trade-offs
3. Always mention **consistent hashing** for resharding scenarios
4. Discuss **hotspot mitigation** strategies
5. Mention that sharding + replication should be used together
6. Address **cross-shard query** challenges and solutions
7. Real-world example: Instagram shards by user_id using PostgreSQL
