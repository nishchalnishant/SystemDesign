> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design a distributed unique ID generator — generating globally unique, time-sortable 64-bit IDs at scale without central coordination.
>
> **Key design decisions:**
> - Snowflake ID (Twitter): 64 bits = 1 sign + 41 timestamp (ms) + 10 machine ID + 12 sequence; ~4096 IDs/ms per machine
> - Alternatives: UUID v4 (random, not sortable, 128 bits), DB auto-increment (single point of failure), segment-based (pre-allocate ranges)
> - Clock skew problem: if system clock goes backward, IDs from same machine could repeat; solution: wait until clock catches up or reject
> - Machine ID assignment: ZooKeeper or etcd for machine registration; each worker registers and gets unique ID on startup
> - Sorting property: Snowflake IDs are monotonically increasing within a machine and roughly ordered across machines → great for pagination
> - High availability: no single coordinator; each machine generates IDs independently; horizontal scaling trivial
> - Custom epoch: set epoch to company founding date to maximize usable timestamp bits (41 bits = ~69 years from epoch)
>
> **Key takeaway:** Snowflake is the industry standard — 41-bit timestamp + 10-bit machine ID + 12-bit sequence = no coordination, sortable by time, 4096 IDs/ms per machine.

---
module: 05-hld-problems
topic: Easy
status: unread
tags: [05-hld-problems, system-design, easy, snowflake, uuid, distributed-id-generation]
---
# Design a Unique ID Generator

> **Difficulty**: Easy | **Asked at**: Amazon, Twitter, Uber, LinkedIn

---

## Problem Statement

Design a distributed unique ID generator that produces globally unique, sortable identifiers at high throughput without a centralized bottleneck. IDs are used as primary keys for database rows, event trace IDs, or order numbers.

---

## Functional Requirements

1. **Uniqueness**: Generated IDs must be globally unique — no two IDs are ever the same
2. **Sortable**: IDs should be roughly time-ordered (IDs generated later sort after IDs generated earlier)
3. **Numeric**: IDs are 64-bit unsigned integers (fits in a BIGINT column)
4. **High throughput**: Generate at least 10,000 IDs/second per machine
5. **Low latency**: ID generation < 1ms

---

## Non-Functional Requirements

- **No single point of failure**: Any node can generate IDs independently without coordination
- **Monotonically increasing (approximately)**: IDs generated at time T should be greater than those at time T-1 (within a machine)
- **No central coordinator**: System must not require a lock or network call to generate each ID
- **Compact**: 64-bit integers fit in database BIGINT; larger IDs waste storage at scale

---

## Core Entities

| Entity | Key Fields |
|--------|-----------|
| `ID Generator Node` | machine_id (10 bits), datacenter_id (5 bits), sequence_counter |
| `Generated ID` | 64-bit integer (timestamp + machine_id + sequence) |

---

## API Design

ID generation is typically a library/SDK call, not an HTTP endpoint. But if exposed as a service:

```http
GET /id
Response 200: { "id": 1701234567890001 }

GET /ids?count=100
Response 200: { "ids": [1701234567890001, 1701234567890002, ...] }
```

In practice: embed the ID generator as a library in each app server — eliminates network hops entirely.

---

## High-Level Design

```
App Server (embedded Snowflake generator)
  │
  ├── Read system clock (milliseconds since epoch)
  ├── Read machine_id (assigned at startup, unique per node)
  ├── Increment sequence counter (reset to 0 each millisecond)
  │
  ▼
64-bit ID = [timestamp 41 bits][datacenter_id 5 bits][machine_id 5 bits][sequence 12 bits]

Multiple machines generate IDs simultaneously — no coordination needed.
machine_id differentiates IDs generated at the same millisecond.
sequence differentiates up to 4,096 IDs within the same millisecond on the same machine.
```

**Snowflake breakdown (Twitter's design)**:

| Bits | Field | Range | Notes |
|------|-------|-------|-------|
| 1 | Sign bit | 0 | Always 0 (positive integer) |
| 41 | Timestamp | ~69 years | Milliseconds since custom epoch (e.g., 2024-01-01) |
| 5 | Datacenter ID | 0–31 | 32 datacenters |
| 5 | Machine ID | 0–31 | 32 machines per datacenter |
| 12 | Sequence | 0–4095 | 4,096 IDs/millisecond/machine |

**Total capacity**: 32 × 32 × 4,096 IDs/ms = 4,194,304 IDs/ms globally = 4 billion IDs/second.

---

## Deep Dive 1: Snowflake Algorithm Implementation

```python
import time
import threading

class SnowflakeGenerator:
    EPOCH = 1704067200000          # 2024-01-01 00:00:00 UTC in ms
    MACHINE_ID_BITS = 5
    DATACENTER_ID_BITS = 5
    SEQUENCE_BITS = 12

    MAX_MACHINE_ID = (1 << MACHINE_ID_BITS) - 1      # 31
    MAX_DATACENTER_ID = (1 << DATACENTER_ID_BITS) - 1  # 31
    MAX_SEQUENCE = (1 << SEQUENCE_BITS) - 1            # 4095

    MACHINE_ID_SHIFT = SEQUENCE_BITS                   # 12
    DATACENTER_ID_SHIFT = SEQUENCE_BITS + MACHINE_ID_BITS  # 17
    TIMESTAMP_SHIFT = DATACENTER_ID_SHIFT + DATACENTER_ID_BITS  # 22

    def __init__(self, datacenter_id, machine_id):
        self.datacenter_id = datacenter_id
        self.machine_id = machine_id
        self.sequence = 0
        self.last_timestamp = -1
        self.lock = threading.Lock()

    def next_id(self):
        with self.lock:
            now = self._current_ms()

            if now < self.last_timestamp:
                raise Exception(f"Clock moved backwards by {self.last_timestamp - now}ms")

            if now == self.last_timestamp:
                self.sequence = (self.sequence + 1) & self.MAX_SEQUENCE
                if self.sequence == 0:
                    now = self._wait_next_ms(self.last_timestamp)
            else:
                self.sequence = 0

            self.last_timestamp = now

            return (
                ((now - self.EPOCH) << self.TIMESTAMP_SHIFT) |
                (self.datacenter_id << self.DATACENTER_ID_SHIFT) |
                (self.machine_id << self.MACHINE_ID_SHIFT) |
                self.sequence
            )

    def _current_ms(self):
        return int(time.time() * 1000)

    def _wait_next_ms(self, last_timestamp):
        now = self._current_ms()
        while now <= last_timestamp:
            now = self._current_ms()
        return now
```

**Key edge cases**:
- **Sequence overflow**: If 4,096 IDs are generated in the same millisecond, block until the next millisecond.
- **Clock drift**: NTP can move the clock backward. Detect and raise an exception — never generate duplicate IDs.

---

## Deep Dive 2: Alternative Approaches

**UUID v4**: 128-bit random number (e.g., `550e8400-e29b-41d4-a716-446655440000`).
- ✓ No coordination, universally unique
- ✗ Not sortable, not sequential — causes B-tree index fragmentation (random inserts → page splits)
- ✗ 128 bits vs 64 bits — wastes storage and index space
- Use when: you need globally unique IDs that will be shared across systems (APIs, logs) and don't care about sort order

**UUID v7** (RFC 9562): 128-bit, but with millisecond timestamp in the high bits.
- ✓ Sortable, no coordination, 128-bit
- ✗ Still 128 bits; not a 64-bit integer

**Database AUTO_INCREMENT**: Database assigns sequential IDs.
- ✓ Simple, perfectly sequential
- ✗ Single point of failure; write throughput limited by DB master; leaks business data (competitors can count your rows)

**Ticket server**: A single database with one `AUTO_INCREMENT` table. All generators fetch batches of IDs from it.
- ✓ Simple, works across services
- ✗ Single point of failure; network hop required per batch

**Recommendation**: Snowflake for high-throughput, sortable, 64-bit IDs without coordination. UUID v7 when cross-system sharing requires 128-bit. Auto-increment only for single-node small-scale systems.

---

## Deep Dive 3: Clock Synchronization and Monotonicity

**Problem**: NTP can set the clock backward. Two IDs generated before and after a backward clock jump could have the same timestamp — breaking the uniqueness guarantee on the sequence component.

**Detection**: Compare `current_timestamp` with `last_timestamp` on every ID generation. If `current < last` → clock moved backward.

**Response options**:
1. **Throw exception**: Safest. Caller must retry after the clock stabilizes. May cause brief unavailability.
2. **Wait it out**: Spin until `current_timestamp > last_timestamp`. Works for small drifts (< 1s). Bad for large jumps.
3. **Use sequence headroom**: If the drift is < 1ms, increment the sequence as if still in the same millisecond. Risky — sequence may overflow.
4. **Borrow from future**: Artificially advance the sequence counter by the drift amount. Guarantees uniqueness but reduces future throughput.

**Modern systems**: Use a hardware clock with monotonic guarantees (`CLOCK_MONOTONIC`). NTP adjusts the rate of the monotonic clock (slewing) rather than jumping backward. Most cloud VMs provide `vDSO` monotonic clocks. Backward jumps only happen on virtualization pause/resume — handle via detection and wait.

---

## Interviewer Questions by Level

**Junior**:
- Why can't you use `random()` to generate unique IDs?
- What is the tradeoff between UUID and a 64-bit Snowflake ID?
- Why is it important for IDs to be roughly time-ordered?

**Mid-level**:
- Explain the Snowflake ID bit layout. How many IDs can it generate per second per machine?
- What happens when the sequence counter overflows within a millisecond?
- How do you assign unique machine IDs to each node without coordination?

**Senior**:
- What happens when the system clock moves backward? How does Snowflake handle it?
- How would you modify Snowflake to support 1024 machines instead of 32 per datacenter?
- How do you handle the epoch problem — Snowflake's 41-bit timestamp overflows in 69 years. What's your migration plan?
- Compare Snowflake, ULID, and UUID v7 — when would you use each?
