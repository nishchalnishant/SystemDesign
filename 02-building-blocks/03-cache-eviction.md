---
module: 02-building-blocks
status: unread
tags: [02-building-blocks, system-design, building-blocks]
---
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

HashMap for O(1) lookup; doubly linked list for O(1) move-to-front and evict-tail.

```python
from collections import OrderedDict

class LRUCache:
    def __init__(self, capacity: int):
        self.cap = capacity
        self.cache = OrderedDict()  # key → value, insertion order = LRU order

    def get(self, key: int) -> int:
        if key not in self.cache:
            return -1
        self.cache.move_to_end(key)  # most recently used
        return self.cache[key]

    def put(self, key: int, value: int) -> None:
        if key in self.cache:
            self.cache.move_to_end(key)
        self.cache[key] = value
        if len(self.cache) > self.cap:
            self.cache.popitem(last=False)  # evict LRU (front)
```

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

## Comparison

| Algorithm | Hit Rate | Scan Resistant | New Item | Used In |
|---|---|---|---|---|
| LRU | Good | ❌ | ✅ | Redis, Memcached |
| LFU | Good (stable workloads) | ✅ | ❌ | Redis 4+ |

**In interviews**: say "LRU for general caches; LFU if the access frequency distribution is stable and Zipfian."

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

## Interview Questions

- **When does LRU fail?** When a full table scan sweeps through millions of unique keys — each accessed once — evicting your entire working set (cache pollution). Fix: use LFU or bypass cache for bulk scans.
- **Which Redis eviction policy?** `allkeys-lru` for general caches; `allkeys-lfu` if the hot set is stable and predictable; `noeviction` for session stores where you'd rather return an error than silently lose data.
- **LRU vs LFU trade-off?** LRU: favors recently accessed items; good when access patterns have temporal locality. LFU: favors frequently accessed items; vulnerable to cache pollution from new cold items.

---

## See Also

- `01-foundations/caching-cdn.md` — Cache strategies (write-through, write-behind, aside)
- `02-building-blocks/caching-layer.md` — Redis, Memcached, distributed caching
- `04-advanced-topics/internals/redis-internals.md` — Redis LRU approximation, key expiry
- `05-hld-problems/02-medium/distributed-cache.md` — Designing a distributed cache
