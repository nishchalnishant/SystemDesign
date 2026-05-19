# Design a Unique ID Generator

> **Difficulty**: Easy
> **Topics**: Distributed ID Generation, Snowflake, UUID
> **Time**: 30-45 minutes
> **Companies**: Twitter, Discord, Instagram, Amazon

---

## Problem Mindmap

```
Unique ID Generator
├── Problem Constraints
│   ├── Scale → 4096 IDs/ms/node with Snowflake; Twitter peak ~18K tweets/sec
│   ├── Ordering → IDs must be roughly time-sortable (not strictly sequential across nodes)
│   └── Core hardness → globally unique + monotonically increasing + no coordination overhead + clock skew safety
├── Architecture Derivation
│   ├── Step 1 → UUID v4 → globally unique but random (not sortable), 128 bits (too large for DB index)
│   ├── Step 2 → DB auto-increment → sequential but single DB = SPOF; bottleneck at high write rates
│   ├── Step 3 → Ticket server (DB per shard, even/odd increments) → still DB-bound, limited throughput
│   └── Step 4 → Snowflake: 1+41+10+12 bits → no coordination, 64-bit, sortable, 4096 IDs/ms/node
├── Core Components
│   ├── Snowflake ID structure → 1 bit (sign=0) + 41 bits (ms timestamp) + 10 bits (machine ID) + 12 bits (sequence)
│   ├── ZooKeeper → assigns unique 10-bit machine IDs (0..1023) at node startup; prevents machine ID collision
│   ├── Sequence counter → per-node per-millisecond counter; resets to 0 each ms; maxes at 4095 (12 bits)
│   └── Clock skew handler → if current_time < last_time: spin-wait if delta < 5ms; throw exception if > 5ms
├── Data Model
│   ├── No persistent store → IDs generated in-memory; epoch = 2010-01-01 (41 bits = 69 years from epoch)
│   └── Machine registry (ZooKeeper) → node_id (10-bit int) → {hostname, assigned_at, heartbeat_ts}
├── APIs
│   ├── GET /id → {id: 7391523847234} (single 64-bit integer)
│   └── GET /ids?count=N → batch generation; all IDs from same ms bucket or spill to next ms
├── Critical Trade-offs
│   ├── Snowflake vs UUID → Snowflake chosen → 64-bit (fits BIGINT), sortable, no coordination per ID
│   ├── Machine ID assignment → ZooKeeper chosen over static config → dynamic node addition without ops overhead
│   └── Clock dependency → NTP-synced clocks; monotonic clock prevents backward drift within process
├── Failure Scenarios
│   ├── Clock skew forward → IDs still unique but out of order across nodes; acceptable for "roughly sorted"
│   ├── Clock moves backward → spin-wait or refuse; log alert; prevents duplicate IDs
│   └── ZooKeeper down → nodes use cached machine ID; no new nodes can join until ZK recovers
└── Interview Angles
    ├── Twitter → "Design Snowflake" → recite the 1+41+10+12 bit layout and explain each component
    ├── Instagram → "How does Instagram generate photo IDs?" → Snowflake-like with shard ID embedded in 13 bits
    └── Follow-up → "What happens when sequence overflows 4096/ms?" → wait until next millisecond; natural backpressure
```

---

## What Breaks Without This System?

An e-commerce platform uses `AUTO_INCREMENT` primary keys in a single PostgreSQL instance. At 50M orders/day it shards the database across 8 nodes — each node's `AUTO_INCREMENT` now produces overlapping IDs. `ORDER_ID=1` exists in 3 different shards pointing to 3 different orders. Joins are broken. Deduplication is broken. Every downstream service that stores an `order_id` is storing an ambiguous value.

The simpler failure: a recommendation engine generates UUID v4 for every event — random 128-bit identifiers. They're globally unique, but when you range-scan events by time in Cassandra, UUIDs scatter randomly across the cluster because they're not sortable by insertion time. Reading "all events in the last hour" requires a full cluster scan instead of a range read.

Without a designed ID system: either IDs collide across shards, or they're random and unsortable, or they require a centralized counter that becomes the bottleneck for every write.

---

## Derive the Architecture

**Step 1 — Single server (trivial)**
`AUTO_INCREMENT` in one DB. Works until the DB is the write bottleneck or you need sharding.

**Step 2 — What breaks at scale?**
- Sharding: `AUTO_INCREMENT` per node → ID collisions across shards.
- Multi-datacenter: coordinating a global sequence counter adds a cross-DC round trip to every write.
- Time-ordering: `AUTO_INCREMENT` is monotonic but not time-correlated — you can't infer creation time from an ID.
- High throughput: a central ticket server (single DB generating IDs) is a bottleneck above ~50K writes/sec and a SPOF.

**Step 3 — What constraints does the solution need?**
1. Globally unique — no collisions across any node, any datacenter.
2. Sortable by time — range queries and pagination work without a separate timestamp column.
3. Decentralized — no single point of failure or coordination bottleneck.
4. High throughput — at least 4,096 IDs/ms per machine without coordination.

**Step 4 — Derive Snowflake from the constraints**
- 64-bit integer (fits in a `BIGINT`, compatible with existing tooling).
- Embed time: 41 bits of milliseconds since epoch → ~69.7 years of range, time-sortable by default.
- Embed machine identity: 10 bits → 1,024 unique machine IDs, no coordination at generation time.
- Sequence within one millisecond: 12 bits → 4,096 IDs per millisecond per machine before the ms ticks over.
- Layout: `[1 unused][41-bit ms timestamp][10-bit machine ID][12-bit sequence]`
- At 10 machines, peak: 10 × 4,096,000 = ~41M IDs/sec. No network call, no lock contention across machines.

**Step 5 — Remaining problems**
- Clock skew: if a machine's clock goes backward, you'd generate duplicate timestamps. Fix: refuse to generate IDs until the clock catches up (`waitNextMillis()`).
- Machine ID assignment: who allocates the 10-bit machine ID? Use ZooKeeper/etcd on startup to claim a unique slot. On death and restart, reclaim the same slot or claim a new one.
- Epoch: hard-coded. Twitter used 2010-11-04. You pick your own epoch to maximize timestamp range for your deployment start date.

---

## Real-Life Analogy

Think of a regional post office network where every branch has its own rubber stamp.

Each stamp block encodes three things: the date (printed on the outside), the branch ID (a two-digit code unique to that location), and a sequence counter (a number that resets every day and increments with each piece of mail). Two letters processed at the same branch on the same day will differ only in their sequence number. Two letters processed at different branches on the same day will differ in the branch code. No branch ever calls another to coordinate — each issues stamps independently, and the global guarantee of uniqueness comes from the structure of the stamp itself, not from any central authority.

This is exactly how Twitter Snowflake works: timestamp in the high bits, machine ID in the middle, sequence counter at the low end. The structure eliminates the need for coordination.

The alternative is a central post office that assigns a sequential number to every single piece of mail in the country. That works fine in a small town. At national scale it becomes a bottleneck, and if the central office is unavailable, the entire postal system halts. This is the ticket server approach.

---

## Why This Is Hard

The problem looks trivial — just call `UUID.randomUUID()`. The real constraints reveal themselves under load:

1. **Coordination bottleneck**: Any approach that requires a shared counter (single database, Redis INCR) becomes a write bottleneck and a single point of failure. At 100K ID/sec, a single atomic counter server saturates quickly.
2. **Sortability vs. uniqueness**: UUIDs (v4) are globally unique but random. Inserting random UUIDs as primary keys into a B-tree index causes index fragmentation — every insert can land anywhere in the tree, causing page splits and killing write throughput at scale. You need IDs that are both unique and time-ordered.
3. **Clock skew**: Distributed systems rely on wall clocks for timestamp-prefixed IDs. NTP can move the clock backward. If a server generates ID 1000 at time T and then the clock resets to T-5ms, the next ID could be assigned a timestamp smaller than an already-issued one — breaking the monotonicity guarantee.
4. **64-bit constraint**: Java `long` and most database primary key types are 64 bits. You must pack timestamp, machine identity, and sequence into 64 bits without overflow — and you need to think about when the timestamp portion will wrap around (the "Year 2038 problem" equivalent).
5. **Machine ID assignment**: In a Snowflake scheme, machines need unique IDs. How are they assigned? How do you handle machines coming and going in an auto-scaling group?

---

## Requirements Gathering

### Functional Requirements

**Must-Have:**
1. Generate a globally unique ID on demand
2. IDs must be 64-bit integers (fit in a Java `long`)
3. IDs must be sortable by generation time
4. No single point of failure in the generation path

**Nice-to-Have:**
4. IDs embeddable with shard or datacenter information
5. Human-readable timestamp extraction from an ID
6. Support for multiple datacenters

### Non-Functional Requirements

**Performance:**
- Throughput: 100K IDs/sec per generator node
- Latency: sub-millisecond per ID

**Availability:**
- 99.99% uptime — ID generation must not be a availability risk for upstream services

**Scalability:**
- Horizontal scaling by adding generator nodes, no cross-node coordination at generation time

**Correctness:**
- Strictly unique (no duplicate IDs globally, ever)
- Monotonically non-decreasing within a single generator

---

## Capacity Estimation

```
Snowflake 12-bit sequence = 4,096 IDs per millisecond per machine
Single machine throughput: 4,096 * 1,000 = ~4M IDs/sec

For 100K IDs/sec across the system:
100,000 / 4,096,000 = 0.025 machines needed (single machine handles this easily)

For 10M IDs/sec:
10,000,000 / 4,096,000 = ~3 generator nodes needed

Timestamp lifespan (41-bit milliseconds):
2^41 = 2,199,023,255,552 ms = ~69.7 years
Twitter's epoch: November 4, 2010 (custom epoch)
Overflow year: 2010 + 69.7 = ~2079

Machine ID space (10-bit):
2^10 = 1,024 unique machines

Sequence space per machine per ms (12-bit):
2^12 = 4,096 unique IDs

Total IDs before timestamp overflow:
2^63 = ~9.2 * 10^18 (using signed 64-bit long)
```

---

## API Design

### REST Endpoint

**Generate ID**
```http
GET /api/v1/id

Response: 200 OK
{
  "id": 7329047361234567168,
  "id_str": "7329047361234567168",   // String form for JS (loses precision with 64-bit int)
  "generated_at": "2026-05-13T10:00:00.123Z"
}
```

Note: Always return the ID as both a number and a string. JavaScript's `Number` type is a 64-bit float, which can only safely represent integers up to 2^53. Twitter learned this the hard way — clients that parsed tweet IDs as JS numbers silently truncated them. The `id_str` field was added as a fix.

**Batch Generate (Optional)**
```http
POST /api/v1/ids
Content-Type: application/json

{ "count": 100 }

Response: 200 OK
{ "ids": [7329047361234567168, 7329047361234567169, ...] }
```

---

## High-Level Architecture

```
┌──────────────────────────────┐
│         Client Service       │
│  (Order Service, User Svc)   │
└──────────────┬───────────────┘
               │  GET /api/v1/id
               ▼
┌──────────────────────────────┐
│       Load Balancer          │
└───────┬──────────────┬───────┘
        │              │
        ▼              ▼
┌───────────────┐ ┌───────────────┐
│  ID Generator │ │  ID Generator │   Each node: stateless except
│  Node 1       │ │  Node 2       │   for its machine_id and
│  machine_id=1 │ │  machine_id=2 │   in-process sequence counter
└───────┬───────┘ └───────┬───────┘
        │                 │
        └────────┬────────┘
                 │  Machine ID assignment at startup
                 ▼
┌──────────────────────────────┐
│         ZooKeeper            │   Nodes claim a machine_id
│   (Machine ID Registry)      │   lease on startup; release
│                              │   on shutdown
└──────────────────────────────┘

No cross-node communication during ID generation.
ZooKeeper is consulted ONLY at startup, not per-request.
```

---

## Data Flow

### Snowflake ID Generation (per request)

```
1. Client calls GET /api/v1/id

2. Generator node:
   a. currentMs = System.currentTimeMillis() - EPOCH
   b. If currentMs == lastMs:
        sequence++
        If sequence > 4095:
          spin-wait until next millisecond   // sequence exhausted
   c. Else (new millisecond):
        sequence = 0
        lastMs = currentMs
   d. id = (currentMs << 22) | (machineId << 12) | sequence

3. Return id as 64-bit long

No network calls. No locks across nodes. Pure in-process math.
```

### Machine ID Assignment at Startup (ZooKeeper)

```
1. Generator node starts
2. Connects to ZooKeeper cluster
3. Creates ephemeral sequential znode: /snowflake/machines/node-0000000001
4. Reads the assigned sequence number → this is machine_id (0-1023)
5. Registers heartbeat to ZooKeeper (keeps ephemeral node alive)
6. On shutdown: ZooKeeper ephemeral node auto-deletes → machine_id freed

Instagram's alternative: bake the machine_id into the deployment config
(e.g., Kubernetes pod index from StatefulSet). No ZooKeeper needed.
```

---

## Deep Dives

### 1. Snowflake IDs

Twitter open-sourced Snowflake in 2010. The 64-bit layout is:

```
Bit layout of a Snowflake ID:

 63        22 21       12 11       0
  |         |  |         |  |      |
  0 [41 bits] [10 bits]    [12 bits]
  |         |  |         |  |      |
  sign     timestamp  machine_id  sequence
  (unused) (ms since  (0-1023)   (0-4095)
            epoch)
```

```java
public class SnowflakeIdGenerator {

    private static final long EPOCH = 1288834974657L; // Twitter epoch: Nov 4, 2010
    private static final long MACHINE_ID_BITS = 10L;
    private static final long SEQUENCE_BITS = 12L;
    private static final long MAX_MACHINE_ID = ~(-1L << MACHINE_ID_BITS); // 1023
    private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BITS);     // 4095

    private static final long MACHINE_ID_SHIFT = SEQUENCE_BITS;           // 12
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + MACHINE_ID_BITS; // 22

    private final long machineId;
    private long lastTimestamp = -1L;
    private long sequence = 0L;

    public SnowflakeIdGenerator(long machineId) {
        if (machineId > MAX_MACHINE_ID || machineId < 0) {
            throw new IllegalArgumentException("Machine ID out of range: " + machineId);
        }
        this.machineId = machineId;
    }

    public synchronized long nextId() {
        long currentMs = System.currentTimeMillis() - EPOCH;

        if (currentMs < lastTimestamp) {
            // Clock moved backward — NTP adjustment or clock skew
            long drift = lastTimestamp - currentMs;
            if (drift <= 5) {
                // Small drift: spin-wait for clock to catch up
                currentMs = waitUntilNextMillis(lastTimestamp);
            } else {
                // Large drift: cannot safely generate IDs
                throw new IllegalStateException(
                    "Clock moved backward by " + drift + "ms. Refusing to generate ID."
                );
            }
        }

        if (currentMs == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                // Sequence exhausted for this millisecond — wait for next ms
                currentMs = waitUntilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = currentMs;

        return (currentMs << TIMESTAMP_SHIFT)
             | (machineId << MACHINE_ID_SHIFT)
             | sequence;
    }

    private long waitUntilNextMillis(long lastMs) {
        long ms;
        do {
            ms = System.currentTimeMillis() - EPOCH;
        } while (ms <= lastMs);
        return ms;
    }
}
```

**Twitter's production deployment:**
- ZooKeeper assigns machine IDs to generator nodes at startup
- Each app server runs its own generator in-process (no network hop per ID)
- No cross-node locking — generators are fully independent once machine_id is assigned
- At Twitter's scale (2010): tens of millions of tweets/day, one generator per server was sufficient

**Timestamp overflow (the "Year 2038 equivalent"):**
41 bits of milliseconds from Twitter's 2010 epoch overflow in 2079. Services that start their own epoch today get 69.7 years from that date. If you need more time, use fewer machine_id or sequence bits. This is a design-time decision — you cannot change the bit layout without migrating all existing IDs.

---

### 2. UUID (v4 and v7)

**UUID v4 — truly random:**

```
Format: 8-4-4-4-12 hex characters
Example: 550e8400-e29b-41d4-a716-446655440000

Generation: 122 bits of random data + 6 version/variant bits
No coordination, no central authority, no clock dependency
Collision probability: negligible (2^122 possible values)
```

```java
import java.util.UUID;
UUID id = UUID.randomUUID();
// "f47ac10b-58cc-4372-a567-0e02b2c3d479"
```

**The B-tree index locality problem:**

When UUID v4 is used as a primary key in PostgreSQL or MySQL (InnoDB), inserts go to random positions in the clustered index. The database constantly reads non-resident pages into the buffer pool to perform inserts. At high write volumes:
- Buffer pool thrash: evicts useful pages to make room for index pages not recently accessed
- Write amplification: random page splits force the engine to rewrite more pages than sequential inserts would
- Benchmarks: UUID v4 PKs are typically 30-50% slower than sequential integer PKs at table sizes above ~10M rows

**UUID v7 — timestamp-prefixed, sortable:**

```
Format: same 128-bit, 8-4-4-4-12 hex
Structure: 48-bit unix_ts_ms | 4-bit version | 12-bit rand_a | 2-bit variant | 62-bit rand_b

Example: 018f3e6a-b4c2-7abc-8def-0123456789ab
         ^---------^
         48-bit timestamp prefix (ms since Unix epoch)
         Monotonically increasing within same millisecond
```

UUID v7 (finalized in RFC 9562, 2024) gives you:
- **Sortability**: UUIDs generated later are lexicographically larger
- **Global uniqueness**: still 74 bits of randomness — no coordination needed
- **Index locality**: sequential inserts cluster together on disk, same performance as integer PKs
- **No machine ID to assign**: unlike Snowflake, no startup coordination required

PostgreSQL 17 added the `gen_random_uuid()` upgrade to v7. MongoDB ObjectId uses a similar timestamp-prefix approach (4-byte timestamp | 5-byte random | 3-byte incrementing counter = 12 bytes).

**When to use UUID v7 over Snowflake:**
- You do not want to manage ZooKeeper or machine ID assignment
- You need IDs that are database-friendly (sortable) but generated anywhere, including client-side
- The 128-bit size is acceptable (vs Snowflake's 64-bit)

---

### 3. Ticket Server (Database Auto-Increment / Flickr Approach)

Flickr built a centralized ID generation service in 2010 using MySQL's `AUTO_INCREMENT` with a dedicated table:

```sql
-- Ticket server DB
CREATE TABLE Tickets64 (
    id BIGINT(20) UNSIGNED NOT NULL AUTO_INCREMENT,
    stub CHAR(1) NOT NULL DEFAULT '',
    PRIMARY KEY (id),
    UNIQUE KEY stub (stub)
) ENGINE=InnoDB;

-- Generate an ID
REPLACE INTO Tickets64 (stub) VALUES ('a');
SELECT LAST_INSERT_ID();
```

Every ID request hits this single table. MySQL's atomic increment guarantees uniqueness.

**Single point of failure problem:**

One ticket server means one failure kills all ID generation. Flickr's solution: two servers with interleaved sequences.

```sql
-- Server A: generates 1, 3, 5, 7, ... (odd)
SET @@auto_increment_increment = 2;
SET @@auto_increment_offset = 1;

-- Server B: generates 2, 4, 6, 8, ... (even)
SET @@auto_increment_increment = 2;
SET @@auto_increment_offset = 2;
```

The client load-balances between A and B. If one is down, the other continues. IDs are not perfectly sequential (A may issue 1, 3, 5 while B issues 2, 6, 10 out of global order) but they are unique.

**Trade-offs:**
- Simple to operate — no custom code, just MySQL
- Guaranteed globally unique (within the two servers)
- Not time-sortable by default
- Throughput capped by the ticket server's write capacity (~tens of thousands per second per server)
- Adding more than two servers requires changing the increment stride — a painful migration

---

### 4. Instagram's Approach (Epoch + Shard + Sequence in 64 bits)

Instagram's ID scheme (2012) generates IDs entirely within PostgreSQL, embedded inside each shard — no external ID service required:

```
64-bit layout:
[41 bits: ms since Instagram epoch Jan 1 2011]
[13 bits: shard_id]
[10 bits: sequence (auto_increment mod 1024)]
```

```sql
CREATE OR REPLACE FUNCTION next_id(OUT result BIGINT) AS $$
DECLARE
    our_epoch BIGINT := 1314220021721;  -- Instagram epoch: Sep 25, 2011
    seq_id    BIGINT;
    now_ms    BIGINT;
    shard_id  INT := 5;  -- hardcoded per shard instance
BEGIN
    SELECT nextval('table_id_seq') % 1024 INTO seq_id;
    SELECT FLOOR(EXTRACT(EPOCH FROM clock_timestamp()) * 1000) INTO now_ms;

    result := (now_ms - our_epoch) << 23;
    result := result | (shard_id << 10);
    result := result | (seq_id);
END;
$$ LANGUAGE PLPGSQL;
```

Each shard generates its own IDs with no cross-shard coordination. The shard_id embedded in the ID also tells you exactly which shard to query for that entity — a nice routing property.

**Advantages over Snowflake:**
- No ZooKeeper dependency
- shard_id is meaningful (you can route queries by ID alone)
- Runs inside PostgreSQL — no extra service to operate

**Disadvantages:**
- Shard IDs must be stable and pre-assigned (cannot auto-scale shard count easily)
- Sequence is per-shard, not per-millisecond-globally, so ordering across shards is only approximate

---

### Trade-off Comparison

| Property | Snowflake | UUID v4 | UUID v7 | Ticket Server | Instagram |
|----------|-----------|---------|---------|---------------|-----------|
| **Globally unique** | Yes | Yes | Yes | Yes | Yes |
| **Time-sortable** | Yes | No | Yes | No | Yes |
| **Coordination at startup** | ZooKeeper | None | None | None | Config |
| **Coordination per request** | None | None | None | DB write | None |
| **64-bit (fits in long)** | Yes | No (128-bit) | No (128-bit) | Yes | Yes |
| **DB index locality** | Yes | No | Yes | Yes | Yes |
| **Single point of failure** | No | No | No | Yes (mitigated) | No |
| **Throughput** | ~4M/ms/node | Unlimited | Unlimited | ~50K/sec | Per-shard |
| **Complexity** | Medium | Low | Low | Low | Medium |
| **Clock skew safe** | Partial | Yes | Partial | Yes | Partial |

**Recommendation by use case:**
- **Default choice (new system)**: Snowflake or UUID v7 — both are time-sorted and scalable
- **Simplest possible**: UUID v7 — no infrastructure, no coordination, sortable
- **Embedded shard routing**: Instagram-style — ID encodes enough to route queries
- **Legacy systems / small scale**: Ticket server — easy to operate, predictable
- **Avoid**: UUID v4 as a database primary key at scale

---

## Failure Scenarios

### Scenario 1: Clock Moves Backward (NTP Adjustment)

**Impact:** Snowflake and Instagram-style generators may produce duplicate or non-monotonic IDs.

**Mitigation:**
- For small backward drift (< 5ms): spin-wait until the clock catches up (see implementation above)
- For large backward drift (> 5ms): refuse to generate IDs and alert on-call — this indicates a serious clock configuration problem
- Use `CLOCK_MONOTONIC` (hardware clock) instead of wall clock where available; `System.currentTimeMillis()` is wall clock and subject to NTP adjustment
- Deploy with `ntp.conf` configured to `tinker panic 0` and `makestep` limited to startup only — prevents large mid-operation adjustments

### Scenario 2: ZooKeeper Unavailable at Generator Startup

**Impact:** New generator nodes cannot obtain a machine_id and cannot start.

**Mitigation:**
- Cache the last-assigned machine_id locally on disk; reuse it if ZooKeeper is unreachable and the node knows it was the last holder
- Use a static machine_id assignment via Kubernetes StatefulSet pod index — eliminates ZooKeeper dependency entirely for containerized deployments
- ZooKeeper for machine_id assignment is startup-time only; running generators are unaffected by ZooKeeper downtime

### Scenario 3: Sequence Exhaustion (High Write Burst)

**Impact:** More than 4,096 IDs requested within a single millisecond from one generator — `waitUntilNextMillis` is called, adding latency.

**Mitigation:**
- At 4,096 IDs/ms per node, exhaustion requires >4M IDs/sec on a single node — far above typical load
- Add generator nodes horizontally; load-balance ID requests across them
- Monitor sequence exhaustion rate as a metric — sustained exhaustion signals the need for more nodes before latency degrades

### Scenario 4: Machine ID Collision (Two Nodes Assigned Same ID)

**Impact:** Two generators producing identical IDs — a correctness violation, not a performance issue.

**Mitigation:**
- ZooKeeper ephemeral znodes prevent this by design (ZooKeeper guarantees exclusive assignment)
- Without ZooKeeper: use static assignment from an authoritative config system (Consul, Kubernetes annotations) and validate uniqueness at deployment time
- Test: on node startup, generate 10 IDs, log the machine_id field, and alert if it conflicts with any currently-running node's known machine_id

---

## Monitoring & Alerts

**Key Metrics:**

```
Generator health:
- IDs generated per second (per node)
- Sequence exhaustion rate (waitUntilNextMillis calls/sec)
- Clock backward drift events (count + max drift in ms)
- Machine ID assignment success / failure at startup

System health:
- ZooKeeper connection status (for Snowflake deployments)
- P99 ID generation latency (target: <1ms)
- Generator node count (alert if < 2)
```

**Alerts:**

```
- P0: Clock backward drift > 5ms on any generator node → Page on-call
      (indicates serious NTP/clock issue; generator refuses to issue IDs)
- P0: Machine ID collision detected → Page on-call
- P1: Generator node count < 2 → Ticket (loss of redundancy)
- P1: Sequence exhaustion rate > 100/sec on any node → Ticket (add nodes)
- P2: P99 ID generation latency > 5ms → Investigate
```

---

## Interview Tips

**Common Questions:**

1. **"Why not just use UUID?"** → UUID v4 is not sortable, which causes B-tree index fragmentation at scale. UUID v7 fixes this — it is timestamp-prefixed and sortable — but is 128 bits rather than 64. Snowflake gives you 64 bits and sortability at the cost of machine ID coordination.

2. **"What happens when the clock goes backward?"** → Spin-wait for small drifts (< 5ms); refuse to generate IDs and alert for large drifts. The root fix is configuring NTP to avoid backward steps during operation (only at startup).

3. **"How do you assign machine IDs without conflicts?"** → ZooKeeper ephemeral nodes — each node creates an ephemeral znode and reads its assigned sequence number. If ZooKeeper is unavailable, you can bake machine IDs into Kubernetes StatefulSet pod indices, which are stable and unique by design.

4. **"When does Snowflake run out of IDs?"** → 41-bit milliseconds from a 2010 epoch overflow in ~2079. Starting a new epoch today buys another 69.7 years. This is a design-time decision — changing the bit layout requires migrating all existing IDs.

5. **"Can you generate IDs without any infrastructure?"** → UUID v7 — pure in-process, no coordination, globally unique, time-sortable. Ideal for services that cannot take on ZooKeeper as a dependency.

6. **"Why does Twitter return both `id` and `id_str` in the API?"** → JavaScript numbers are 64-bit floats with 53 bits of integer precision. A full 64-bit Snowflake ID passed as a JSON number gets silently truncated in JS. `id_str` is the same number as a string to preserve precision.

**Time Allocation:**
- Requirements: 5 min
- Estimation: 5 min
- API: 3 min
- Architecture + Data flow: 7 min
- Deep dives (Snowflake + UUID v7 + Ticket Server + trade-offs): 15 min
- Failure scenarios: 5 min

---

## Interview Questions Asked

### Google
1. **"Walk me through the Snowflake ID bit layout — why 41 bits for the timestamp specifically?"** → Tests whether you know 41 bits gives ~69.7 years from a custom epoch; the interviewer wants to hear you explain the epoch offset (e.g., Twitter chose Nov 2010) and what happens as you approach overflow in ~2079.
2. **"If you could redesign Snowflake today, would you change the bit allocation?"** → Probes trade-off reasoning: fewer machine ID bits = more sequence bits (higher throughput per node) vs. more machine ID bits = more nodes supported. There's no single right answer — state your assumption about expected fleet size.

### Meta
1. **"How do you guarantee monotonically increasing IDs across data centers without a central coordinator?"** → Tests understanding that pure monotonicity across DCs is impossible without coordination; acceptable answer is "monotonic per generator node, loosely ordered globally" — and why that's sufficient for most use cases (feed ordering, pagination).
2. **"We have two data centers and Snowflake nodes in each. A network partition occurs. What happens to ID ordering?"** → Probes partition handling: each DC continues generating locally-monotonic IDs independently; post-partition, IDs interleave. The candidate should explain this is acceptable and why global total order requires a coordination layer (e.g., Spanner's TrueTime).

### Amazon
1. **"Design an ID generator that works without any central coordinator — no ZooKeeper, no database."** → Looking for UUID v7 (time-ordered, no coordination) or a hybrid approach using host IP + PID as implicit machine ID. Key points: coordination-free = no single point of failure, but machine ID uniqueness relies on network uniqueness of IP.
2. **"How would you integrate your ID generator into a Lambda/serverless environment where instances are ephemeral?"** → Serverless has no stable machine identity — can't use StatefulSet indices. Answer: UUID v7 (pure in-process) or a lease-based machine ID with short TTL (e.g., 60s) fetched at cold-start from DynamoDB.

### Common Follow-ups
1. **"What happens when all 1,024 machine IDs are exhausted?"** → New nodes cannot join without decommissioning old ones or expanding the machine ID field. Mitigation: monitor machine ID utilization, provision headroom, or reduce bits allocated to sequence (trade throughput per node for node count).
2. **"UUID vs Snowflake — when would you choose each?"** → UUID v7 when: no infrastructure dependency is acceptable, IDs cross organizational boundaries (external APIs), or global uniqueness without coordination is required. Snowflake when: 64-bit size matters (DB index efficiency), you control all ID consumers, and you need guaranteed sortability with minimal storage overhead.
3. **"What is clock skew and how does it affect your system in practice?"** → NTP corrections can move the clock backward; a generator issuing IDs at t=100 may see t=99 on next call. Mitigation: spin-wait for small drifts (< 5ms), hard-stop and alert for large drifts. The real risk is duplicate IDs if the generator doesn't detect the backward step.
