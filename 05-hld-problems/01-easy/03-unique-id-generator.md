---
module: 05-hld-problems
topic: Easy
status: interview-ready
tags: [05-hld-problems, system-design, easy]
---
# Design a Unique ID Generator

> **Difficulty**: Easy
> **Topics**: Snowflake, UUID, Distributed Coordination
> **Time**: 45 min
> **Companies**: Google, Meta, Amazon

---

## Clarifying Questions

1. "What's the required throughput — 1K IDs/sec or 4B IDs/sec globally?"
2. "Must IDs be sortable by time, or just unique?"
3. "64-bit (fits in a BIGINT) or 128-bit acceptable?"
4. "Can we have a central coordinator (ZooKeeper, DB), or must it be fully decentralized?"
5. "Do IDs need to be unpredictable (security), or is sequential-ish fine?"
6. "Cross-datacenter uniqueness required — single region or multi-region?"

---

## Back-of-Envelope

```
Target: 4B IDs/sec globally, 1024 machines

Snowflake math:
  41 bits timestamp → 2^41 ms = ~69.7 years from epoch
  10 bits machine ID → 2^10 = 1024 machines
  12 bits sequence → 2^12 = 4096 IDs/ms/machine

Throughput:
  1 machine: 4096 IDs/ms = ~4M IDs/sec
  1024 machines: 4096 × 1024 = 4B IDs/sec

Storage: 64-bit = 8 bytes per ID
  1B IDs/day × 8 bytes = 8 GB/day (negligible in index context)
```

---

## APIs

```
// Single endpoint; machine-local generation (no network call)
long generateId()
  → 64-bit integer, globally unique, time-sortable

// Admin: assign machine ID at node startup
POST /id-service/register
  { "hostname": "worker-42.us-east-1" }
  → { "machine_id": 312 }
```

---

## Architecture

```
Application Server
  │
  ├── In-Process ID Generator (no network call)
  │     │
  │     ├── Machine ID (assigned at startup via ZooKeeper or DynamoDB)
  │     ├── Millisecond clock
  │     └── Per-ms sequence counter (12-bit, resets each ms)
  │
  └── Generated 64-bit Snowflake ID

ZooKeeper / DynamoDB (startup only, not on hot path):
  - Each node registers hostname → gets back machine_id (0–1023)
  - Ephemeral node: if process dies, machine_id is released
```

```
64-bit Snowflake layout:
  [0][   41 bits timestamp   ][10 bits machine][12 bits seq]
   ^unused     ms since epoch    0-1023          0-4095
```

---

## Data Model

```sql
-- Machine ID registry (read once at startup)
CREATE TABLE machine_registry (
    machine_id   SMALLINT PRIMARY KEY,     -- 0-1023
    hostname     VARCHAR(100) UNIQUE,
    registered_at TIMESTAMP DEFAULT NOW(),
    last_heartbeat TIMESTAMP
);
-- machine_id is the Snowflake machine ID bits
-- heartbeat detects dead nodes so IDs can be reclaimed
```

---

## Key Design Decisions

**1. Snowflake over UUID v4**
UUID v4 is random — no time ordering. B-tree indexes on random UUIDs cause page splits on every insert (write amplification, poor cache locality). Snowflake IDs are monotonically increasing per machine → inserts append to the right edge of the index. Index size stays smaller; range scans by time work naturally. Chosen for any system that stores IDs in a DB index.

**2. ZooKeeper for machine ID assignment (startup only)**
Machine IDs must be unique across all generators. Options:
- ZooKeeper ephemeral sequential node: node creates `/machines/id-` → gets `/machines/id-0042` → machine ID is 42. If the process dies, the ephemeral node is deleted, freeing the ID.
- DynamoDB conditional write: `PutItem(machine_id=X) WHERE attribute_not_exists` — only one writer wins per ID.
- Static config: simplest, requires manual ops to add/remove nodes.
ZooKeeper is robust at scale; only contacted at startup, never on the hot path.

**3. Clock skew handling**
NTP can move the clock backward. If `current_ms < last_issued_ms`:
- Small backward jump (< 5ms): spin-wait until `current_ms >= last_issued_ms`. Blocks ID generation briefly but prevents duplicates.
- Large backward jump (> 5ms): hard-stop and alert. Risk of duplicates is too high to auto-recover; operator must resolve clock.

**4. UUID v7 as coordination-free alternative**
If machine ID coordination is unacceptable (serverless, ephemeral environments): UUID v7 packs a 48-bit ms timestamp into the top bits, then 80 bits of random. Time-sortable, no coordination, globally unique. Downside: 128-bit (vs 64-bit Snowflake) — doubles index size. Choose UUID v7 for cross-org systems or Lambda functions where stable machine identity is impossible.

---

## Deep Dives

**Sequence overflow within one millisecond**
At 4096 IDs/ms capacity, if a single machine issues the 4097th ID within the same millisecond: sequence wraps to 0 but timestamp hasn't advanced. Solution: spin-wait until the next millisecond. This creates a <1ms latency spike at extreme throughput — acceptable in practice since 4096/ms = 4M/sec per machine.

**Multi-datacenter ordering**
Machines in US-East and EU-West generate IDs simultaneously. Their IDs may interleave by timestamp — that's expected and fine. Pure global monotonicity (total order) requires coordination (Google Spanner's TrueTime). For most use cases (feed pagination, log ordering), per-node monotonicity is sufficient.

---

## Failure Scenarios

| Failure | Impact | Mitigation |
|---------|--------|------------|
| Two machines get same machine_id | Duplicate IDs | Atomic assignment (ZK ephemeral node or DB unique constraint); monitor for duplicates |
| Clock jumps backward | Potential duplicate IDs | Spin-wait <5ms; hard-stop >5ms with alert |
| ZooKeeper down at startup | New nodes can't register | Pre-assign machine IDs via config as fallback |
| Sequence overflow (>4096/ms) | Spin-wait adds latency | Expected; size machines so average is <<4096 IDs/ms |
| Machine ID exhaustion (>1024 nodes) | New nodes can't get an ID | Monitor utilization; expand machine_id bits if needed |

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

---

## Interviewer Follow-Up Questions

**On Snowflake internals:**
- "Snowflake IDs are 64-bit. Break down the bit allocation." → `1 bit` unused (sign) + `41 bits` timestamp (milliseconds since custom epoch, ~69 years) + `10 bits` machine ID (1024 machines) + `12 bits` sequence (4096 IDs/ms/machine). At 1024 machines × 4096/ms = ~4M IDs/ms = 4B/sec globally. Epoch is custom (Twitter used Nov 4, 2010) — choose an epoch close to your service launch to maximize the timestamp range.
- "How does Snowflake handle the sequence number overflowing within a millisecond?" → Sequence rolls over to 0 but the timestamp doesn't change yet — spin-wait until the clock advances to the next millisecond before issuing the next ID. This is transparent to callers but introduces < 1ms latency spike under extreme throughput on a single node.
- "Why is the timestamp the most significant bits in a Snowflake ID?" → So IDs are roughly time-sortable when stored as integers — newer IDs are numerically larger. This means B-tree indexes on the ID column stay hot at the right edge (recent inserts) rather than writing randomly across the whole index, which is better for write performance and range scans.

**On coordination:**
- "How does a new generator node get a machine ID without ZooKeeper?" → Options: statically assign via config (simple, requires manual ops); use DB auto-increment on a `machine_registry` table (node inserts a row on startup, uses the generated row ID); use Redis `INCR machine_id_counter`. ZooKeeper is reliable but operationally heavy — DB or Redis is simpler at most scales.
- "What happens if two generators are assigned the same machine ID?" → They'll generate identical IDs for the same timestamp + sequence combination. This is a correctness disaster. Prevention: the assignment mechanism must be atomic and exclusive (DB unique constraint, ZooKeeper ephemeral node, Redis `SET NX`). Monitor: check for duplicate IDs in a sample of writes — alert if any found.

**On alternatives:**
- "When would you choose UUID v7 over Snowflake for primary keys?" → UUID v7 is time-ordered (timestamp in the top 48 bits) and requires zero coordination — any node generates one independently. Choose it when: you're building a multi-cloud or multi-team system where assigning machine IDs is impractical, or when you're using a DB that natively supports UUID columns efficiently (Postgres). Downside: 128 bits vs 64 bits — larger index, more storage.
- "How does Instagram's approach differ from Twitter's Snowflake?" → Instagram uses Postgres sequences per shard: `schema_id` (23 bits) + `unix_time_ms` mod some period (41 bits) packed into 64 bits, generated inside Postgres via a stored function. No external coordination service required — Postgres itself issues IDs atomically. Tightly coupled to Postgres; Snowflake is DB-agnostic.
- "What are the trade-offs of using ULIDs instead of Snowflake or UUID?" → ULID = 10-char timestamp (millisecond precision) + 16-char random, base32-encoded, 128 bits total. Benefits: URL-safe string, globally unique without coordination, time-sortable. Downsides: larger than 64-bit Snowflake (bigger index), no machine ID (can't determine origin node), random component means slight index fragmentation vs pure Snowflake.

**On failure and clock issues:**
- "Your NTP server is misconfigured and clocks are drifting. What breaks and how?" → Clocks drifting forward: IDs will appear from the future, breaking time-ordering for consumers that depend on monotonic IDs. Clocks jumping backward: generator detects `current_time < last_issued_time`, must stall until time catches up. For large backward jumps (> 5s): alert and refuse to generate IDs until manual intervention — risk of duplicates is too high to auto-recover.
