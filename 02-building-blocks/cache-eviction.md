# Cache Eviction Policies

## Why Eviction Policy Matters

A cache has finite memory. When it's full and a new item arrives, something must be evicted. The policy determines **which item to remove** — and the wrong choice can collapse your hit rate.

Real-world analogy: Your desk has space for 10 folders. The eviction policy determines which folder you move back to the filing cabinet when a new one arrives. LRU = the one you touched least recently. LFU = the one you've referenced least overall. ARC = adaptive, watches both patterns simultaneously.

**Why the choice matters**: At a 95% hit rate, a 5% improvement to 96% halves the DB load. At 50M requests/day, that's 500K extra DB queries avoided daily.

---

## Baseline: FIFO and Random

**FIFO (First In, First Out)**: Evict the oldest inserted item. Ignores access patterns entirely. Poor hit rate on any workload with temporal locality.

**Random**: Evict a uniformly random item. Surprisingly competitive against FIFO, but both are dominated by recency/frequency-aware policies.

---

## LRU (Least Recently Used)

**Policy**: Evict the item that was accessed least recently.

**Rationale**: Temporal locality — recently accessed items are likely to be accessed again soon.

### Implementation: HashMap + Doubly Linked List

```java
class LRUCache {
    int capacity;
    Map<Integer, Node> map;   // key → node
    Node head, tail;           // dummy sentinels

    // get: O(1) — lookup in map, move node to head
    // put: O(1) — insert at head, evict tail if over capacity
}
```

The doubly linked list maintains access order. `head` = most recently used, `tail` = least recently used.

- **Get**: move accessed node to head
- **Put**: insert at head; if at capacity, remove tail node

Java: `LinkedHashMap(capacity, 0.75f, true)` implements LRU with `removeEldestEntry()`.

### Hit Rate Characteristics

LRU performs well when:
- Working set fits in cache (all frequently accessed items are in the cache)
- Access pattern has temporal locality

LRU performs poorly when:
- **Scan/sweep attacks**: A full table scan reads millions of sequential records, each accessed once, evicting your entire working set. Called "cache pollution."
- **Frequency matters more than recency**: A rare but recently accessed item evicts a frequently used item

---

## LFU (Least Frequently Used)

**Policy**: Evict the item with the lowest access count.

**Rationale**: Frequency locality — frequently accessed items are more valuable than recently accessed ones.

### Implementation: O(1) with Min-Heap or Frequency Buckets

Naive implementation with a heap is O(log n). O(1) LFU uses frequency buckets:

```
freq=1: [key_a, key_b]
freq=2: [key_c]
freq=5: [key_d, key_e]
min_freq = 1
```

On eviction, remove any item from the `min_freq` bucket (LRU within the bucket for tie-breaking). On access, move item from `freq` bucket to `freq+1` bucket; update `min_freq`.

### Hit Rate Characteristics

LFU performs well when:
- Access frequency distribution is stable and skewed (Zipf distribution — most requests hit the top 20% of items)
- Cache is large enough to hold the hot set

LFU performs poorly when:
- **Frequency aging**: An item was popular 6 months ago, gets a high count, and never gets evicted even though it's now irrelevant. LFU has no concept of time decay.
- **New item problem**: A brand-new hot item starts at frequency=1 and gets immediately evicted before it can build up its count.

---

## ARC (Adaptive Replacement Cache)

Invented at IBM (Nimrod Megiddo and Dharmendra Modha, 2003). Used in ZFS, Oracle DB, IBM storage systems.

**Key insight**: Real workloads switch between frequency-dominant and recency-dominant phases. ARC adapts automatically between LRU and LFU behavior by tracking **ghost entries** (metadata of recently evicted items).

### ARC Structure

ARC maintains 4 internal lists:

```
T1: recency cache   — items seen once recently
T2: frequency cache — items seen 2+ times recently
B1: ghost list for T1 (evicted T1 items, key only, no data)
B2: ghost list for T2 (evicted T2 items, key only, no data)

Invariant: |T1| + |T2| ≤ cache size
Parameter: p = target size for T1 (adapts dynamically)
```

### ARC Access Logic

```
Cache hit in T1 or T2:  move to T2 (promote to frequency cache)

Cache miss:
  Hit in B1 (ghost): increase p (recency was too small, grow T1)
                     fetch data, insert into T2
  Hit in B2 (ghost): decrease p (frequency was too small, grow T2)
                     fetch data, insert into T2
  Total miss:         insert into T1
                     if |T1| + |B1| = c: evict from T1 or B1
```

### Why ARC Outperforms LRU and LFU

- Ghost lists let ARC learn from mistakes: "I evicted that item and immediately needed it again — I should give more space to that cache type."
- Self-tuning: no manual configuration needed
- Scan-resistant: a one-time sequential scan populates T1, but when T1 is evicted, it appears in B1. Since B1 hits don't increase p, the scan doesn't pollute T2 (the frequency cache).

**Downside**: ARC is patented by IBM. Linux kernel's page cache uses a custom variant (LRU with active/inactive lists). Redis and Memcached use approximated LRU instead.

---

## TinyLFU and the Window TinyLFU (Caffeine)

**Caffeine** is the default Java in-process cache library (used in Spring Boot, Guava Cache successor). Its eviction policy is **Window TinyLFU (W-TinyLFU)** — the state of the art for in-process caches.

### The Problem: Frequency Estimation Without Full History

Maintaining exact access counts for millions of keys requires O(n) memory. TinyLFU solves this with a **Count-Min Sketch** — a probabilistic data structure that estimates frequency with bounded error using O(1) space.

### Count-Min Sketch

```
4 hash functions, each maps key → bucket in a row:

Row 0: [ 0 | 3 | 0 | 7 | 2 | ... ]
Row 1: [ 0 | 0 | 5 | 2 | 0 | ... ]
Row 2: [ 4 | 0 | 0 | 1 | 3 | ... ]
Row 3: [ 0 | 2 | 0 | 6 | 0 | ... ]

Estimated frequency of key X = min(row[0][h0(X)], row[1][h1(X)], row[2][h2(X)], row[3][h3(X)])
```

- Increment: increment the bucket in each row
- Estimate: take the minimum across all rows (reduces hash collision inflation)
- **Error bound**: overestimates by at most ε * N with probability 1 - δ, where ε and δ depend on table width and depth

**Periodic aging**: every `N` accesses, all counters are halved. This prevents stale high-frequency items from dominating forever (solves LFU's frequency aging problem).

### Window TinyLFU Architecture

```
┌─────────────────────────────────────────────────────┐
│  Window Cache (1% of capacity, pure LRU)            │
│  → new items go here first                          │
└────────────────────┬────────────────────────────────┘
                     │ eviction candidate
                     ▼
         TinyLFU Admission Filter
         (compare candidate frequency vs victim frequency)
              │              │
         admit              reject (candidate stays out)
              ▼
┌─────────────────────────────────────────────────────┐
│  Protected Cache (80% of capacity, LRU)             │
│  → items accessed 2+ times                         │
│  → eviction goes to Probationary segment            │
└─────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────┐
│  Probationary Cache (20% of capacity, LRU)          │
│  → items from protected on second access            │
└─────────────────────────────────────────────────────┘
```

**Why the window cache?** New items need a chance to prove themselves. Without it, a brand-new hot item would immediately be compared against an established high-frequency item and lose — the "new item problem."

**Admission filter**: before promoting a candidate from the window cache to the main cache, TinyLFU compares the candidate's estimated frequency against the victim's estimated frequency. Only admit if candidate is more valuable.

### Caffeine Performance

Benchmarks consistently show Caffeine's W-TinyLFU achieving:
- 10-30% higher hit rates than LRU on real-world traces (Wikipedia, Web search, DB traces)
- Near-optimal (within 5% of the theoretical optimal offline algorithm, Bélády's algorithm)

**Why Caffeine instead of Guava Cache**: Guava uses LRU; Caffeine uses W-TinyLFU. Google's own benchmarks showed Caffeine significantly outperforming Guava on production traces.

---

## SLRU (Segmented LRU)

Used by: Memcached, Nginx proxy cache

**Structure**: Two LRU segments — **probationary** and **protected**.

- New items enter probationary segment
- Items accessed again move to protected segment
- Eviction targets probationary segment first

**Why**: Separates "seen once" items from "repeatedly accessed" items. Scan resistance: a full scan pollutes probationary but not protected.

---

## Clock Algorithm (Second Chance)

Used by: OS page cache (Linux), many disk caches.

**Structure**: Circular buffer of pages + 1 reference bit per page. A "clock hand" sweeps.

```
[ Page A (bit=1) ] ← [ Page B (bit=0) ] ← [ Page C (bit=1) ] ← ...
                                ▲
                           clock hand
```

- On access: set reference bit = 1
- On eviction: if bit = 1, clear to 0 and advance. If bit = 0, evict this page.

**O(1) operations, O(1) extra space** — ideal for OS-level page replacement where overhead must be minimal.

---

## Comparison Table

| Algorithm | Hit Rate | Scan Resist | New Item | Memory Overhead | Used In |
|---|---|---|---|---|---|
| LRU | Good | ❌ | ✅ | Low (linked list) | Redis, Memcached |
| LFU | Good for stable | ✅ | ❌ | Low-Medium | Custom, some DBs |
| ARC | Excellent | ✅ | ✅ | Medium (ghost lists) | ZFS, Oracle |
| W-TinyLFU | Near-optimal | ✅ | ✅ | Low (sketch) | Caffeine, Guava |
| SLRU | Good | ✅ | ✅ | Low | Memcached |
| Clock | Fair | ✅ | ✅ | Very Low | OS page cache |

---

## Miss Rate Curves and Cache Sizing

The **miss rate curve** (MRC) shows how miss rate decreases as cache size increases.

```
Miss Rate
  100% ─┐
        │\
        │ \
        │  \─────────────────────
   ~5%  │            "knee"
  ──────┴─────────────────────────
        0        Cache Size
```

The "knee" is where adding more cache gives diminishing returns. Adding cache beyond the knee is wasteful — the working set fits in memory and the cache is large enough to hold it.

**Practical sizing**: profile production traffic for working set size, then size cache to be 2x-3x the working set to stay comfortably above the knee.

---

## Redis Eviction Policies

Redis supports 8 eviction policies (set via `maxmemory-policy`):

| Policy | Description |
|---|---|
| `noeviction` | Return error when full |
| `allkeys-lru` | Approximate LRU across all keys |
| `volatile-lru` | Approximate LRU, only keys with TTL |
| `allkeys-lfu` | Approximate LFU across all keys (Redis 4+) |
| `volatile-lfu` | Approximate LFU, only keys with TTL |
| `allkeys-random` | Random eviction |
| `volatile-random` | Random eviction, only keys with TTL |
| `volatile-ttl` | Evict keys with shortest remaining TTL |

**Redis approximates LRU** — it does not maintain a true LRU linked list (too much memory overhead). Instead, it samples `maxmemory-samples` keys (default: 5) and evicts the one with the oldest LRU clock timestamp.

For most production caches: `allkeys-lru` or `allkeys-lfu` depending on whether your access pattern is recency or frequency dominated.

---

## Interview Deep-Dive Questions

1. **Why does Caffeine use W-TinyLFU instead of LRU, and what problem does the window cache solve?**
   W-TinyLFU uses frequency estimation to make better eviction decisions — it admits an incoming item only if it's more valuable (higher estimated frequency) than the item it would evict. The window cache (1% of capacity) solves the "new item problem": a brand-new item has frequency=0 and would always lose to established items in a pure LFU. The window cache gives new items a brief grace period in pure LRU mode to build up their frequency before they compete for the main cache.

2. **Describe an attack on an LRU cache and how to mitigate it.**
   A sequential full-table scan or large range query reads millions of unique keys, each accessed exactly once. LRU moves each into the cache and evicts the previous hot item. After the scan, the entire cache is filled with cold scan data that will never be accessed again, and all warm data is evicted. Hit rate collapses. Mitigations: (1) use a scan-resistant policy (ARC, W-TinyLFU, SLRU); (2) add a "warming" flag to distinguish cache population from scan traffic; (3) route analytical queries to a separate cache or bypass the cache entirely.

3. **A Memcached instance has `maxmemory-policy = noeviction`. It's 95% full. What happens when the next write arrives?**
   Memcached (unlike Redis) always evicts — it doesn't support `noeviction`. But in Redis with `noeviction`, the next write command returns an `OOM command not allowed when used memory > 'maxmemory'` error. The application must handle this error explicitly. This policy is appropriate for caches where you'd rather return a miss than silently evict important data (e.g., session stores).

4. **How does a Count-Min Sketch estimate frequency? Why does it overestimate rather than underestimate?**
   The sketch maps each key to one bucket per hash function and increments all of them. On query, it takes the minimum across all rows. It can only overestimate: hash collisions cause a bucket to receive increments from multiple keys, inflating the count. But taking the minimum across independent hash rows means the worst-case inflation is bounded by the collision rate. It cannot underestimate because a key's own increments are always included in every row's bucket.

---

## See Also

- `01-foundations/caching-cdn.md` — Cache strategies (write-through, write-behind, aside)
- `02-building-blocks/caching-layer.md` — Redis, Memcached, distributed caching
- `04-advanced-topics/internals/redis-internals.md` — Redis LRU approximation, key expiry
- `05-hld-problems/02-medium/distributed-cache.md` — Designing a distributed cache
