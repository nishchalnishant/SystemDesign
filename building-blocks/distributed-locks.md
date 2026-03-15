# Distributed Locks

> **Coordinate exclusive access to a shared resource across multiple processes or nodes.**

---

## 1. Concept Overview

A **distributed lock** allows only one holder at a time across a cluster. It is used for leader election, preventing duplicate processing, or guarding a critical section (e.g. “only one worker should process this shard”).

**Why it exists**: In a single process, a mutex is enough. Across many nodes, you need a shared store (e.g. Redis, ZooKeeper, etcd) that guarantees mutual exclusion.

---

## 2. Core Principles

### Requirements

- **Mutual exclusion**: Only one holder at a time.
- **Liveness**: If holder crashes, lock is eventually released (TTL or ephemeral node).
- **Safety**: No two nodes believe they hold the lock simultaneously (consistency of the lock store).

### Implementation patterns

**1. Redis (single or Redlock)**  
- `SET key unique_value NX PX ttl` to acquire; delete key to release.  
- **Redlock**: Use multiple Redis instances and acquire on majority to tolerate single-node failure.  
- **Caveat**: Clock skew and network delays can break safety in edge cases.

**2. ZooKeeper / etcd**  
- Create **ephemeral** node (e.g. `/lock/resource-123`). Lowest sequence number wins.  
- If holder dies, session ends and node is removed; next waiter gets the lock.  
- Strong consistency (CP) so no two nodes see themselves as leader.

**3. Database**  
- `SELECT ... FOR UPDATE` or “insert unique row” with retry.  
- Works but adds DB load and depends on DB availability.

### Architecture (ZooKeeper-style)

```
  Node A ──▶ Create /lock/xyz (ephemeral) ──▶ Leader
  Node B ──▶ Create /lock/xyz (ephemeral) ──▶ Waits (watches A's node)
  Node C ──▶ Create /lock/xyz (ephemeral) ──▶ Waits

  When A dies → ephemeral node deleted → B or C becomes leader
```

---

## 3. Real-World Usage

- **Leader election**: One active instance (e.g. cron runner, consumer group coordinator); ZooKeeper used by Kafka, HBase.
- **Scheduled jobs**: Only one node runs the job at a time (e.g. Redis lock with TTL).
- **Resource guard**: Only one worker processes a given partition or queue.
- **Critical section**: E.g. “single writer to this file” across multiple servers.

---

## 4. Trade-offs

| Approach | Pros | Cons |
|----------|------|------|
| **Redis (single)** | Fast, simple | Single point of failure; no strong consistency guarantee |
| **Redlock** | Tolerates Redis node failure | Complex; still debated for safety under clock/network issues |
| **ZooKeeper / etcd** | Strong consistency; ephemeral nodes | Heavier; more latency; operational complexity |
| **DB** | Uses existing infra | DB as bottleneck; not ideal for high contention |

**When to use**: Leader election, single-active job, or guarding a shared resource across nodes.  
**When not**: Prefer stateless design or message ordering (e.g. single consumer per partition) to avoid lock contention.

---

## 5. Failure Scenarios

| Scenario | Mitigation |
|----------|------------|
| Holder crashes before release | TTL (Redis) or ephemeral node (ZooKeeper) so lock is released |
| Clock skew (Redis) | Prefer ZooKeeper/etcd for critical locks; or use Redlock with caution |
| Lock store down | No new acquisitions; existing holders may not know; design for “at most one” and idempotent work |
| Split brain | Use CP store (ZooKeeper, etcd) so only one partition can grant locks |

---

## 6. Performance Considerations

- **Latency**: Acquire/release involves network round-trips; use short critical sections.
- **Contention**: High contention increases latency and load on lock store; shard resources or use per-resource locks.
- **TTL**: Too short → premature release; too long → slow recovery after crash. Match to max expected hold time.

---

## 7. Implementation Patterns

- **Try-lock with backoff**: Retry with exponential backoff if lock is busy; avoid thundering herd.
- **Renewal (lease)**: If work can outlast TTL, renew lock periodically in a background thread; stop renewing when done.
- **Fencing tokens**: Store (e.g. in etcd) an increasing token with the lock; resource checks token and rejects stale holders (prevents two “leaders” after split-brain).

---

## Quick Revision

- **Use cases**: Leader election, single-active job, guarding shared resource.
- **Redis**: Simple, TTL for liveness; Redlock for multi-node. **ZooKeeper/etcd**: Ephemeral nodes, strong consistency.
- **Failure**: TTL or ephemeral to release on crash; prefer CP store for safety; fencing token for critical resources.
- **Interview**: “We use ZooKeeper for leader election so only one instance runs the scheduler; if the leader dies, the ephemeral node goes away and another instance takes over. For less critical cases we use Redis with a short TTL.”
