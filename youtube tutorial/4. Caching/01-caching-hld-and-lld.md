# Caching in System Design Interviews

> **Topic from**: [Caching in System Design Interviews w/ Meta Staff Engineer](https://www.youtube.com/watch?v=1NngTUYPdpI) — Hello Interview
>
> **Note**: These notes are written from general knowledge on the topic, not transcribed from the video.

**Prerequisites** — this file assumes the fundamentals and does not repeat them:
- Strategies (cache-aside, write-through, write-behind), eviction policies, Redis vs Memcached → [Basics notes](../1.%20System%20Design%20Basics/03-caching.md)
- Stampede, penetration, avalanche → [Cache systems & Redis](../2.%20System%20Design%20Fundamentals/06-cache-systems-and-redis.md) · [Caching layer](../../02-building-blocks/02-performance/01-caching-layer.md)
- LRU implementation, thread safety, striped locks → [13-design-lru-cache](../../06-lld/05-problems/02-frequent-problems/13-design-lru-cache.md) · [35-design-concurrent-lru-cache](../../06-lld/05-problems/04-advanced-niche/35-design-concurrent-lru-cache.md)

This file covers the three things those files do **not**: sizing math, hot keys, and cache/DB consistency. These are the senior-level probes.

**Related in this directory:**
- [Scalability](../6.%20Scalability/01-scalability-hld-and-lld.md) — why a cache helps the average but not p99, in queueing-theory terms
- [Load Balancing](../5.%20Load%20Balancing/01-load-balancing-hld-and-lld.md) — consistent hashing sends a hot key to one node by construction
- [API Design](../3.%20API%20Design/01-api-design-hld-and-lld.md) — the interface-hiding principle the `Cache<K,V>` contract applies

---

# Part 1 — HLD

## Sizing: the question that separates levels

Most candidates say "add a cache." The follow-up is "how big?" — and it has an arithmetic answer.

**Working set, not total data.** You cache what is actually accessed, which is a small fraction of what you store. Access is typically Zipfian: the top 20% of keys serve ~80% of reads.

```
1B tweets stored, avg 1 KB          → 1 TB total
Daily active set ~5% of tweets      → 50 GB
Top 20% of that serves 80% of reads → 10 GB working set

→ One 16 GB Redis node holds it. Not 1 TB.
```

**Hit rate drives the value.** The point of the cache is DB load reduction, and the relationship is not linear:

| Hit rate | DB load remaining | Meaning |
|---|---|---|
| 80% | 20% | 5× reduction |
| 95% | 5% | 20× reduction |
| 99% | 1% | 100× reduction |

Going 95% → 99% cuts DB load by a further 5×. This is why a marginal amount of extra cache memory is usually cheaper than a database replica — worth saying out loud.

**Latency budget.** Cache hit ~1 ms, DB query ~10–50 ms. At 95% hit rate:

```
avg = 0.95 × 1ms + 0.05 × 30ms = 2.45 ms
```

But **p99 is the DB path**, ~30 ms. A cache improves the average far more than the tail — if the interviewer asks about p99, the cache is not your answer.

## Hot keys

A single key receiving disproportionate traffic — a celebrity's profile, a flash-sale item. It breaks caching because *all* requests for that key hash to one node, so that node saturates while the rest of the cluster idles. Sharding does not help: consistent hashing sends one key to one place by design.

Three fixes, in escalating order:

**1. Key splitting.** Write the value to N replicas under suffixed keys; readers pick one at random.

```
celebrity:123        →  celebrity:123:0 … celebrity:123:9
read: GET celebrity:123:{random(0,9)}
```
Spreads load across 10 nodes. Cost: 10× memory for that key, and 10 keys to invalidate on write.

**2. Local (L1) cache.** Each app server keeps the hot value in process memory with a short TTL (1–5 s). Requests never reach Redis. Cost: staleness bounded by TTL, and each server has its own copy.

**3. Request coalescing.** At the app tier, in-flight requests for the same key wait on one upstream fetch rather than issuing N.

Detection matters as much as the fix: `redis-cli --hotkeys`, or per-key metrics. Saying "I'd measure it, then split the key" beats naming a fix blind.

## Cache and DB consistency

Cache-aside has a race that most candidates miss. Two concurrent operations on the same key:

```
Thread A (read)          Thread B (write)
─────────────────        ─────────────────
GET cache → miss
SELECT db → v1
                         UPDATE db → v2
                         DELETE cache
SET cache = v1           ← stale value written AFTER the delete
```

The cache now holds `v1` forever, and the DB holds `v2`. No TTL expiry has occurred; nothing self-corrects. This is the fundamental cache-aside race.

**Mitigations:**

| Approach | How | Trade-off |
|---|---|---|
| **TTL** | Bounded staleness | Simplest; wrong for a bounded window |
| **Delete, don't update** | Write path deletes; next read repopulates | Shrinks the window, does not close it |
| **Delayed double delete** | Delete, write DB, sleep ~50 ms, delete again | Closes most of the window; hacky |
| **Versioned keys** | `user:123:v7`; bump version on write | Correct — old key is never read again |
| **CDC invalidation** | Debezium tails the binlog → invalidates | Correct and decoupled; real infrastructure cost |

CDC is the strongest answer because invalidation becomes a consequence of the commit rather than a second thing the writer must remember. See [outbox & CDC](../../04-advanced-topics/01-distributed-architecture/07-outbox-cdc-pattern.md).

**Never dual-write** (`UPDATE db; SET cache`). If the second write fails, the cache is wrong indefinitely, and there is no ordering guarantee between concurrent writers.

## Where the cache goes

Each layer has a different invalidation story — that is what makes the choice interesting.

| Layer | Latency | Invalidation | Use for |
|---|---|---|---|
| Browser | 0 ms | Impossible — TTL only | Static assets, hashed filenames |
| CDN | 10–50 ms | Purge API, minutes to propagate | Images, video, public pages |
| API gateway | ~1 ms | Direct | Full responses for identical requests |
| App-local (L1) | ~0.1 ms | Per-server; no coordination | Hot keys, config |
| Distributed (Redis) | ~1 ms | Direct, immediate | Sessions, objects, query results |
| DB buffer pool | ~0.1 ms | Automatic | Free — already happening |

You cannot invalidate a browser cache, which is why static assets get content-hashed filenames (`app.a1b2c3.js`) — the *name* changes, so the old entry is simply never requested again.

## When not to cache

Interviewers respect a candidate who declines to cache with a reason:

- **Write-heavy, rarely-read** — every write invalidates; you pay to maintain entries nobody reads
- **Strict consistency required** — balances, inventory counts, anything with a correctness invariant
- **Uniform random access** — no working set, so hit rate stays near zero
- **Cheap queries** — a 1 ms indexed lookup does not need a 1 ms cache

---

# Part 2 — LLD

The implementation mechanics (hashmap + DLL, striped locks, test harness) are already covered in the linked LLD files. What follows is API/design-level, which those files do not treat.

## The interface hides the policy

```java
public interface Cache<K, V> {
    Optional<V> get(K key);
    void put(K key, V value);
    void invalidate(K key);
}
```

That is the whole contract. LRU vs LFU vs TTL is an implementation detail. If `getEvictionOrder()` or `getAccessCount()` leaks into the interface, you have frozen the policy and lost the ability to swap it — the same hiding principle as [API design](../3.%20API%20Design/01-api-design-hld-and-lld.md).

Eviction policy becomes a strategy, injected:

```java
public class CacheImpl<K, V> implements Cache<K, V> {
    private final EvictionPolicy<K> policy;
    private final int capacity;

    public CacheImpl(int capacity, EvictionPolicy<K> policy) {
        this.capacity = capacity;
        this.policy = Objects.requireNonNull(policy);
    }
}
```

Now LRU→LFU is a constructor argument, not a rewrite.

## Loading cache: put the strategy inside

A cache that only stores is half a design. The common bug is every caller writing the same miss-handling block, each slightly differently.

```java
public interface LoadingCache<K, V> {
    V get(K key, Function<K, V> loader);
}
```

The implementation does get → on miss, load → populate → return, **with single-flight**: concurrent misses on the same key trigger one load, not N. That is stampede prevention as a property of the type rather than a thing callers must remember.

```java
private final ConcurrentHashMap<K, CompletableFuture<V>> inFlight = new ConcurrentHashMap<>();

public V get(K key, Function<K, V> loader) {
    V cached = store.get(key);
    if (cached != null) return cached;

    CompletableFuture<V> future = inFlight.computeIfAbsent(key, k ->
        CompletableFuture.supplyAsync(() -> {
            V value = loader.apply(k);
            store.put(k, value);
            return value;
        })
    );
    try {
        return future.join();
    } finally {
        inFlight.remove(key);
    }
}
```

`computeIfAbsent` is atomic, so exactly one thread creates the future and the rest join it. This is the LLD expression of the HLD stampede problem — worth connecting explicitly if the interviewer moves between levels.

## Null and negative caching

`get` returning `null` is ambiguous: absent, or present-and-null? `Optional<V>` fixes the return type, but the deeper issue is **cache penetration** — repeated lookups of keys that do not exist fall through to the DB every time.

Cache the absence, with a shorter TTL than real entries:

```java
private static final Object NULL_SENTINEL = new Object();
// store NULL_SENTINEL with 30s TTL; real entries get 300s
```

A [Bloom filter](../../02-building-blocks/02-performance/04-bloom-filter.md) in front is the alternative when the key space is large and mostly absent.

## TTL without a timer per entry

A `ScheduledFuture` per entry does not scale — a million entries means a million timers. Two approaches that do:

**Lazy expiry** — store `expiresAt` on the entry, check on read, treat expired as a miss. Cost: expired entries occupy memory until touched.

**Sampling** — background thread checks a random sample periodically, evicting expired ones. This is what Redis does: 20 random keys, delete expired, repeat if >25% were expired.

Redis combines both. Saying that, and explaining why the per-entry timer is wrong, is a strong signal.

---

# Connecting the layers

| HLD concern | LLD expression |
|---|---|
| Cache stampede | Single-flight via `computeIfAbsent` |
| Cache penetration | Null sentinel / Bloom filter |
| Hot key | L1 local cache with short TTL |
| Eviction policy choice | Injected `EvictionPolicy` strategy |
| TTL-bounded staleness | Lazy expiry + sampling |

---

## Common mistakes

| Mistake | Fix |
|---|---|
| "Add a cache" with no sizing | Working set × hit-rate math |
| Assuming a cache fixes p99 | p99 is the miss path — it does not |
| Dual-write (`UPDATE db; SET cache`) | Delete-on-write, or CDC invalidation |
| Ignoring the cache-aside race | Versioned keys or CDC |
| Sharding to fix a hot key | Key splitting or L1 — sharding cannot help |
| Caching write-heavy or strict-consistency data | Decline, with a reason |
| Exposing eviction policy in the interface | Inject as strategy |
| One timer per TTL entry | Lazy expiry + sampling |

---

## Key takeaways

- Size the cache from the **working set**, not total data — and quote the hit-rate → DB-load table
- Hit rate 95% → 99% is another **5× DB load reduction**; usually cheaper than a replica
- A cache improves the **average, not the tail** — p99 remains the miss path
- **Hot keys defeat sharding by construction**; fix with key splitting or an L1 cache
- Cache-aside has a **read/write race that TTL only bounds** — versioned keys or CDC actually close it
- In LLD, the cache interface hides the policy; **single-flight loading** is stampede prevention as a type property
