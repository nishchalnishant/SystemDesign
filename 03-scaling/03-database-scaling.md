> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** The full database scaling staircase — from indexing to sharding — plus the production-grade details on shard-key selection, replication lag, and read-after-write consistency that distinguish a senior answer.
>
> **Key topics:**
> - **The staircase:** Index → Cache → Read Replicas → Sharding (reach for each in order)
> - **Replication lag** — the gap between primary and replica, and when it bites you
> - **Read-after-write consistency** — how to prevent "I just saved my post but it disappeared"
> - **Sharding strategies** — range vs hash vs directory-based, and how to choose
> - **Shard-key selection** — the most consequential architectural decision in a sharded system
> - **Hot partitions** — when one shard gets all the traffic
> - **Resharding** — how to split shards without downtime
>
> **Key takeaway:** Reach for sharding last. But when you do shard, the shard-key decision is permanent and painful to change — get it right the first time.

---
module: 03-scaling
status: unread
tags: [03-scaling, system-design, databases]
---
# Database Scaling

---

## The Staircase

Treat database scaling as a staircase. Each step adds complexity and operational burden. Only climb when the step below is genuinely insufficient.

```
Step 4: Sharding       ← last resort; write volume exceeds single primary
Step 3: Read Replicas  ← read:write ratio > 10:1
Step 2: Caching        ← same data read repeatedly
Step 1: Indexing       ← slow queries on existing data
```

If your first answer in an interview is "I'll shard it," the interviewer hears "I don't know how to solve problems incrementally." Reach for sharding last; interviewers want to see you exhaust indexing, caching, and replication first.

---

## Step 1: Indexing

A missing index causes a full table scan — O(N) instead of O(log N). On a 100M-row table, this is the difference between 1ms and 30 seconds.

**When to add an index:**
- Column appears in `WHERE`, `JOIN ON`, or `ORDER BY`
- Query planner shows `Seq Scan` in `EXPLAIN ANALYZE`

**Trade-off:** Each index slows writes (must update B-tree on insert/update/delete) and consumes disk space. Don't index columns that are never queried.

**Composite index column order matters:** `(user_id, created_at)` accelerates `WHERE user_id = X ORDER BY created_at` but not `WHERE created_at > Y` alone. Put the highest-cardinality equality filter first.

---

## Step 2: Caching

Add Redis or Memcached in front of the DB for read-heavy workloads. See `02-building-blocks/02-performance/01-caching-layer.md` for full strategy (cache-aside, write-through, stampede prevention).

---

## Step 3: Read Replicas

A primary-replica setup replicates writes from primary to N replicas. Route reads to replicas, writes to primary.

```
All writes → Primary
All reads  → Replica 1, Replica 2, Replica 3 (load-balanced)
```

**Typical ratio:** Add replicas when read:write ratio exceeds 10:1. Each replica roughly doubles read capacity.

### Replication Lag

Replication is almost always **asynchronous** by default. The primary commits a write, then ships the WAL (write-ahead log) to replicas in the background. Replicas apply the changes with a delay — typically <1 second in a healthy cluster, but can spike to seconds or minutes under load or network issues.

**Why it matters:** A user writes data and then immediately reads it from a replica. If the replica hasn't caught up yet, the read returns the old value. The user sees their own change disappear.

**How to detect lag:**
```sql
-- PostgreSQL
SELECT now() - pg_last_xact_replay_timestamp() AS replication_lag;

-- MySQL
SHOW SLAVE STATUS\G  -- Seconds_Behind_Master
```

**Semi-synchronous replication (MySQL/PostgreSQL):** The primary waits for at least one replica to acknowledge receipt of the WAL before committing. Not full durability (replica has the log, not yet applied), but reduces the loss window to near-zero.

### Read-After-Write Consistency

Problem: user writes to primary, then reads from replica before replication catches up — sees stale data.

**Fix 1 — Read from primary after writes:** For the user who just wrote, route their next read to the primary. Use a session flag (`last_write_at`) and compare to replica lag.

**Fix 2 — Sticky reads:** Route a user's session to a specific replica via consistent hashing on `user_id`. The replica may still lag, but at least it's monotonic (the user never sees data go backward).

**Fix 3 — Read your own writes token:** On write, the primary returns a token (e.g., LSN — log sequence number). The app includes this token in subsequent reads. The replica checks if its applied LSN ≥ the token; if not, it waits or redirects to primary.

```python
# Django example: force primary read after recent writes
from django.db import connections

def get_user_profile(user_id, use_primary=False):
    db = 'default' if use_primary else 'replica'
    return User.objects.using(db).get(id=user_id)
```

---

## Step 4: Sharding

Sharding horizontally partitions data across multiple independent database nodes (shards). Each shard owns a subset of the data and is fully independent — no cross-shard joins.

**When to shard:**
- Write throughput exceeds what a single primary can handle (typically >50-100K writes/sec)
- Dataset size exceeds the storage of a single machine
- Replication lag is too high even with semi-sync

### Sharding Strategies

#### Range-Based Sharding

Divide the key space into contiguous ranges. Assign each range to a shard.

```
Shard 0: user_id  0 –  999,999
Shard 1: user_id  1M –  1,999,999
Shard 2: user_id  2M –  2,999,999
```

- **Pro:** Range queries work efficiently (scan one shard).
- **Con:** Hot partitions — if new user_ids are always at the high end, Shard 2 gets all writes while others sit idle. New users also land on the last shard until resharding.

#### Hash-Based Sharding

Apply a hash function to the key; assign the result modulo N to a shard.

```
shard = hash(user_id) % num_shards
```

- **Pro:** Uniform distribution; eliminates hot partitions from sequential IDs.
- **Con:** Range queries touch all shards (must scatter-gather). Resharding requires rehashing all data (changing N invalidates all assignments).

#### Consistent Hashing

A variant of hash sharding that minimizes data movement when adding/removing shards. Keys and shards are placed on a virtual ring; a key routes to the nearest shard clockwise. Adding a shard only moves ~1/N of the data.

Used by: Amazon DynamoDB, Apache Cassandra, Riak. Standard for systems that need frequent elastic scaling.

#### Directory-Based Sharding

A lookup service (shard map) stores the mapping from key to shard. Clients query the directory to find the correct shard.

```
Client → Directory Service → "user 12345 is on Shard 3"
Client → Shard 3 → data
```

- **Pro:** Maximum flexibility — move any record to any shard by updating the directory. Easy resharding.
- **Con:** Directory service is a SPOF and a latency hop. Must cache aggressively.

### Shard-Key Selection

The shard key is the most consequential architectural decision in a sharded system. It's hard to change without a full rewrite.

**Good shard key properties:**
1. **High cardinality** — enough distinct values to spread data across all shards.
2. **Uniform distribution** — no single value or range dominates.
3. **Access-pattern aligned** — queries should touch one shard, not all.
4. **Stable** — the key shouldn't change after the record is created.

**Common anti-patterns:**

| Anti-pattern | Problem |
|---|---|
| Timestamp as shard key | All writes go to the "current" shard (hot partition) |
| Status field (low cardinality) | Only 3 statuses = only 3 shards, can't scale past 3 |
| User geographic region | Uneven user distribution (US >> all other regions) |
| Auto-increment ID without hashing | Sequential IDs cluster on the last shard |

**Typical good choices:**
- `hash(user_id)` for user-centric systems
- `hash(tenant_id)` for multi-tenant SaaS
- `hash(order_id)` for order processing
- Compound: `(region, hash(user_id))` for geo-partitioned systems

### Hot Partitions

A hot partition occurs when one shard receives disproportionately more traffic than others (write amplification on a celebrity user, a trending item, etc.).

**Detection:** Monitor per-shard write/read throughput. A shard handling 10x average traffic is hot.

**Fixes:**
1. **Add a random suffix to the key:** `celebrity_post_id` → `celebrity_post_id:0`, `:1`, `:2`, `:3`. Spread writes across 4 shards; read from all and merge.
2. **Sub-shard:** Split the hot shard into multiple smaller shards.
3. **Cache hot keys:** Put the hottest keys (Beyoncé's profile) in Redis; bypass the DB entirely.

### Resharding

When you outgrow the current shard count, you need to split shards. The challenge: you can't simply double the shard count without moving data, and you can't take the system offline.

**Double-write / backfill pattern:**
1. Start writing new data to both old and new shard layout.
2. Run a backfill job to copy existing data to new layout.
3. Once backfill is complete and verified, cut reads over to new layout.
4. Stop writing to old layout. Clean up.

**Vitess (MySQL) and Citus (PostgreSQL)** automate resharding by managing the shard map and performing online migrations.

### Cross-Shard Queries

Sharding eliminates cross-shard JOINs. Your query layer must handle this explicitly:

- **Scatter-gather:** Send the query to all N shards in parallel; merge results in the application layer. Works for aggregations; expensive for large N.
- **Denormalization:** Duplicate related data into the same shard so queries can stay local. Increases storage; eliminates scatter-gather.
- **Global secondary indexes:** A separate index structure (separate shard or external store) that maps non-shard-key fields to shard locations. DynamoDB's GSIs work this way.

---

## Comparison: Replication vs Sharding

| | Replication | Sharding |
|---|---|---|
| Solves | Read throughput | Write throughput + storage capacity |
| Data model | Full copy on each node | Partial data on each node |
| Cross-node queries | Easy (any replica has all data) | Requires scatter-gather or denormalization |
| Failure isolation | Replicas are redundant (HA) | Each shard is a SPOF (needs its own replicas) |
| Operational complexity | Low-medium | High |
| When to reach for it | Read:write > 10:1 | Write bottleneck or storage full |

In production, you use **both**: each shard has its own primary and replicas.

---

## Interview Questions to Practice

1. **"Walk me through how you'd scale a database from 0 to 1B users."**
   *Start: single PostgreSQL with good indexes. At 10K DAU: add Redis cache for hot reads. At 100K DAU: add 2–3 read replicas, route reads to replicas. At 10M DAU: introduce sharding by user_id (consistent hashing), each shard gets its own primary + 2 replicas. At 1B: regional sharding (shard by region first, then user_id within region) + global secondary indexes for cross-region lookups.*

2. **"A user updates their profile picture, then refreshes the page and sees the old one. What happened and how do you fix it?"**
   *Replication lag. The write went to the primary, but the read hit a replica that hasn't caught up. Fix: after a write, set a session cookie indicating a recent write. For the next 1–2 seconds, route that user's reads to the primary. Alternatively, use a read-your-writes token (LSN from the primary) that the replica must apply before serving the read.*

3. **"How do you handle a celebrity user with 100M followers causing a hot partition?"**
   *Two approaches: (1) Cache-first — the celebrity's post goes to Redis/CDN and never hits the DB shard directly for reads. (2) Key splitting — append a random suffix (0–9) to the post key on write, fan out to 10 shards; on read, query all 10 and merge. The first approach is simpler and the right default; key splitting is for write hot-spots.*

4. **"Why is timestamp a bad shard key?"**
   *All inserts go to the shard that owns the latest timestamp range. That shard absorbs 100% of write load while all older shards sit idle. Only the most recent shard is hot; you can't use the other shards to absorb write traffic. This is the classic range-based hot partition. Hash the timestamp or use a different key.*

5. **"You need to double your shard count from 4 to 8. How do you do it without downtime?"**
   *Use a double-write / backfill migration: (1) Deploy code that writes to both old (4-shard) and new (8-shard) layout. (2) Run backfill: copy all existing data to new layout, track progress. (3) Once backfill catches up and lag is zero, switch reads to new layout (behind a feature flag). (4) Verify for 24h. (5) Stop writing to old layout. Clean up. Vitess/Citus can automate steps 2–4.*
