# Distributed Locks

> **Coordinate exclusive access to a shared resource across multiple processes or nodes.**

---

## File Mindmap

```
Distributed Locks
├── Why It Exists
│   ├── Problem → 3 app servers; cron fires midnight on all 3; all generate+send daily report; users get 3 emails
│   └── Forces → `synchronized` (Java) only works within one JVM process; doesn't span machines
├── Requirements for a Correct Distributed Lock
│   ├── Mutual Exclusion → only one holder at a time across all nodes
│   ├── Liveness (No Deadlock) → lock must release even if holder crashes (TTL)
│   ├── Safety (No Spurious Release) → only lock holder can release it (token check)
│   └── Fault Tolerance → lock service must handle node failures without becoming unavailable
├── TTL (Auto-Expiry)
│   ├── Lock held with TTL = 30s; if holder crashes, lock auto-releases after TTL
│   ├── Risk → GC pause / slow network causes holder to lose lock while still working
│   └── Fix → lock renewal thread; extend TTL heartbeat while still working
├── Fencing Tokens
│   ├── Each lock acquisition returns monotonically increasing token (e.g. 1, 2, 3…)
│   ├── Holder passes token to storage on every write
│   ├── Storage rejects writes with token lower than last seen
│   └── Prevents stale process from writing after lock was re-acquired by another holder
├── Implementations
│   ├── Redis (Simple)
│   │   ├── SET key value NX PX 30000 → atomic; NX = only set if not exists; PX = TTL ms
│   │   ├── Release → Lua script: check value == client_id before DEL (prevent spurious release)
│   │   └── Renewal → background thread: EXPIRE key 30000 while still holding
│   ├── Redlock (Multi-Node Redis)
│   │   ├── Acquire lock on N/2+1 of N Redis nodes; majority must succeed within TTL
│   │   ├── Controversy → Martin Kleppmann: unsafe under certain clock skews + GC pauses
│   │   └── Use fencing tokens with Redlock if correctness is critical
│   ├── ZooKeeper (Ephemeral Nodes)
│   │   ├── Create ephemeral sequential node; lowest sequence number wins the lock
│   │   ├── Watcher on predecessor node; notified when predecessor deleted
│   │   └── Auto-release on session expiry (ZooKeeper handles cleanup on crash)
│   └── etcd (Lease-based)
│       ├── Grant lease with TTL; create key attached to lease
│       ├── Key auto-deleted when lease expires (holder crash)
│       └── Renew lease via KeepAlive RPC while holding lock
├── Leader Election
│   ├── Special case of distributed lock: only one node acts as leader
│   ├── Pattern → try to acquire lock; winner is leader; releases on graceful shutdown or TTL expiry
│   └── Use case → scheduled jobs, primary replica in custom systems, shard coordinator
├── Trade-offs
│   ├── Pros → mutual exclusion across distributed systems; prevents duplicate work
│   └── Cons → lock service is new SPOF; TTL tuning tricky (too short = spurious; too long = blocking)
├── Failure Scenarios
│   ├── Lock holder crashes → TTL releases lock automatically after expiry
│   ├── Lock service down → fail-closed; no progress; must replicate lock service
│   ├── Clock drift → Redlock unsafe; prefer Raft-based (etcd/ZooKeeper) for correctness
│   └── Spurious release → prevent with unique client ID check before DEL (Lua atomicity)
└── Interview Angles
    ├── "Why not use DB row lock?" → DB lock doesn't span processes; connection death = lock held
    ├── "What is a fencing token and why do you need it?" → prevents stale lock holder from writing after eviction
    ├── "Redis vs ZooKeeper for distributed lock?" → Redis: fast, simple; ZooKeeper: stronger guarantees, session expiry
    └── Follow-up: "What happens if GC pause causes lock to expire while holder is still working?" → fencing token rejects the write
```

---

## Why Distributed Locks Exist

**Question**: Your system runs a scheduled job every night at midnight: generate and email the daily report. You've scaled to 3 app servers for redundancy. At midnight, all 3 servers see the cron trigger. All 3 start generating the report. All 3 send the email. Users receive 3 identical emails. You add a `synchronized` block in Java. Does that fix it?

**Physical constraint**: A Java `synchronized` block uses the JVM's monitor on a specific object in heap memory. That memory is local to one JVM process on one machine. Three app servers on three separate machines have three separate heaps with three separate monitors. `synchronized` on one machine has zero effect on the other two — there is no shared memory across process boundaries. Any mutual exclusion mechanism that relies on in-process primitives (locks, semaphores, mutexes) cannot work across a distributed system.

**Minimal solution**: Use a shared external store that all nodes can reach: `SET lock:daily-report unique-token NX PX 60000` in Redis. `NX` means "only set if the key does not already exist." Only the first node to execute this atomically wins and proceeds; the other two see failure and skip. The `PX 60000` (60 second TTL) ensures the lock is released even if the winner crashes mid-job.

**Production generalization**: The minimal Redis solution works for most cases but has edge cases around clock skew, network partitions, and the gap between TTL expiry and the holder still executing (a process pauses in GC for longer than the TTL, loses the lock, and another holder acquires it — now both think they hold it). Fencing tokens (monotonically increasing numbers returned by the lock store) let the guarded resource reject operations from stale lock holders. For critical resources requiring strong safety guarantees, use ZooKeeper or etcd — both are CP systems that use quorum to prevent two nodes from simultaneously believing they hold a lock.

---

## The Coffee Shop Bathroom Key Analogy

A coffee shop has one unisex bathroom and one physical key hanging by the counter. When you take the key, you have exclusive access. Everyone else waits. When you're done, you return the key. There is no ambiguity: if you have the key, you have the lock.

In a distributed system, Redis or ZooKeeper plays the role of the key holder. Instead of a physical key, a process atomically "takes" a record in the store. Every other process that tries to take it simultaneously gets refused.

**Why it exists**: In a single process, a `synchronized` block or mutex is enough. Across multiple JVM instances on different machines, there is no shared memory. You need a shared external store that all nodes can reach and that guarantees mutual exclusion.

---

## 1. Concept Overview

A **distributed lock** allows only one holder at a time across a cluster. It is used for leader election, preventing duplicate processing, or guarding a critical section (e.g. "only one worker should process this shard").

---

## 2. Core Principles

### Requirements

- **Mutual exclusion**: Only one holder at a time. Only one person holds the bathroom key.
- **Liveness**: If the holder crashes without returning the key, the lock must eventually be released. TTL on the Redis key or an ephemeral ZooKeeper node handles this.
- **Safety**: No two nodes believe they hold the lock simultaneously. Requires a consistent store.

### TTL: The Automatic Door Opener

The bathroom key has a timer built in. If you've been inside for 30 seconds and haven't come out, the lock automatically releases — preventing someone passing out inside from blocking the bathroom forever. In Redis this is the `PX` (expiry in milliseconds) on the lock key. Set it to the maximum expected hold time. Too short → premature release while work is in flight. Too long → slow recovery after a crash.

### Fencing Tokens: The Versioned Key

Imagine the key has a version number stamped on it: `v1`. You take the key (v1), go into the bathroom, but accidentally leave the door open for 45 seconds. The TTL fires, the lock auto-releases. Someone else takes the key — now stamped `v2`. You come back with your `v1` key and try to re-enter. The door rejects `v1` because it knows `v2` is current. This prevents a stale lock holder from taking action after its lock has legitimately expired.

In practice: etcd or ZooKeeper return a monotonically increasing token when a lock is granted. The guarded resource checks the token and rejects operations from holders with a lower token number.

### Implementation Patterns

**1. Redis (single or Redlock)**
- `SET key unique_value NX PX ttl` to acquire; delete key to release.
- **Redlock**: Use multiple Redis instances and acquire on majority to tolerate single-node failure.
- **Caveat**: Clock skew and network delays can break safety in edge cases.

**2. ZooKeeper / etcd**
- Create **ephemeral** node (e.g. `/lock/resource-123`). Lowest sequence number wins.
- If holder dies, session ends and node is removed; next waiter gets the lock.
- Strong consistency (CP) so no two nodes see themselves as leader.

**3. Database**
- `SELECT ... FOR UPDATE` or "insert unique row" with retry.
- Works but adds DB load and depends on DB availability.

### Architecture (ZooKeeper-style)

```
  Node A ──▶ Create /lock/xyz (ephemeral) ──▶ Leader (has key)
  Node B ──▶ Create /lock/xyz (ephemeral) ──▶ Waits (watches A's node)
  Node C ──▶ Create /lock/xyz (ephemeral) ──▶ Waits

  When A dies → ephemeral node deleted → B or C becomes leader (takes key)
```

---

## 3. Real-World Usage

- **Leader election**: One active instance (e.g. cron runner, consumer group coordinator); ZooKeeper used by Kafka, HBase.
- **Scheduled jobs**: Only one node runs the job at a time (e.g. Redis lock with TTL).
- **Resource guard**: Only one worker processes a given partition or queue.
- **Critical section**: E.g. "single writer to this file" across multiple servers.

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
| Lock store down | No new acquisitions; existing holders may not know; design for "at most one" and idempotent work |
| Split brain | Use CP store (ZooKeeper, etcd) so only one partition can grant locks |

---

## 6. Performance Considerations

- **Latency**: Acquire/release involves network round-trips; use short critical sections.
- **Contention**: High contention increases latency and load on lock store; shard resources or use per-resource locks.
- **TTL**: Too short → premature release; too long → slow recovery after crash. Match to max expected hold time.

---

## 7. Implementation Patterns

### Redis Lock with TTL and Renewal (Java)

```java
public class RedisDistributedLock {
    private final StringRedisTemplate redis;
    private final String lockKey;
    private final String lockValue = UUID.randomUUID().toString(); // unique per holder

    // Acquire: atomic SET NX PX (take key if not already taken)
    public boolean tryAcquire(long ttlMillis) {
        Boolean acquired = redis.opsForValue()
            .setIfAbsent(lockKey, lockValue, Duration.ofMillis(ttlMillis));
        return Boolean.TRUE.equals(acquired);
    }

    // Release: only delete if we are still the holder (fencing: check our value)
    public void release() {
        String script =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "  return redis.call('del', KEYS[1]) " +
            "else return 0 end";
        redis.execute(new DefaultRedisScript<>(script, Long.class),
                      List.of(lockKey), lockValue);
    }

    // Renewal: extend TTL while still holding (prevents premature expiry on long work)
    public boolean renew(long ttlMillis) {
        String script =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "  return redis.call('pexpire', KEYS[1], ARGV[2]) " +
            "else return 0 end";
        Long result = redis.execute(new DefaultRedisScript<>(script, Long.class),
                                    List.of(lockKey), lockValue, String.valueOf(ttlMillis));
        return Long.valueOf(1).equals(result);
    }
}

// Usage
RedisDistributedLock lock = new RedisDistributedLock(redis, "job:daily-report");
if (lock.tryAcquire(30_000)) {
    try {
        reportService.generate();
    } finally {
        lock.release(); // return the key to the counter
    }
} else {
    log.info("Another node is running the job, skipping.");
}
```

- **Try-lock with backoff**: Retry with exponential backoff if lock is busy; avoid thundering herd.
- **Renewal (lease)**: If work can outlast TTL, renew lock periodically in a background thread; stop renewing when done.
- **Fencing tokens**: Store an increasing token with the lock; resource checks token and rejects stale holders.

---

## Quick Revision

- **Use cases**: Leader election, single-active job, guarding shared resource.
- **Redis**: Simple, TTL for liveness; Redlock for multi-node. **ZooKeeper/etcd**: Ephemeral nodes, strong consistency.
- **TTL**: Prevents permanent lock on crash. **Fencing token**: Prevents stale holder acting after expiry.
- **Failure**: TTL or ephemeral to release on crash; prefer CP store for safety; fencing token for critical resources.
- **Interview**: "We use ZooKeeper for leader election so only one instance runs the scheduler; if the leader dies, the ephemeral node goes away and another instance takes over. For less critical cases we use Redis with a short TTL."
