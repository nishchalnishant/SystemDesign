---
id: ttl-expiry-strategies
tags: [caching, redis, eviction]
confidence: 3
last-rehearsed: 2026-07-21
source: 4. Caching — "Eviction and expiry"
---
# TTL, expiry, and eviction

**Claim in one sentence.** Expiry and eviction are different mechanisms — TTL is a correctness bound you choose, eviction is a memory-pressure response the cache imposes on you — and Redis expires lazily, so a key past its TTL still occupies memory until something touches it.

## Expiry is lazy plus sampled

Redis does not scan for expired keys. It removes them two ways:

- **Lazy** — on access. A `GET` of an expired key finds it, deletes it, returns a miss.
- **Active sampling** — the cycle runs at `server.hz` (default 10× per second), sampling 20 keys with a TTL per database; if more than 10% of the sample was expired, it repeats immediately.

  The constants are `ACTIVE_EXPIRE_CYCLE_KEYS_PER_LOOP 20` and `ACTIVE_EXPIRE_CYCLE_ACCEPTABLE_STALE 10`, both scaled by the configured effort level. A separate 25% figure governs the CPU-time budget per slow cycle — easy to conflate with the stale threshold, so don't quote 25% as the repeat trigger.

The consequence: a key nobody reads can sit expired-but-resident indefinitely. Memory usage does not drop the moment a TTL fires. If you size Redis assuming expiry reclaims memory promptly, you undersize it.

## Eviction policies

| Policy | Behavior | When |
|---|---|---|
| `noeviction` | Writes fail with an error | Redis as a datastore, not a cache |
| `allkeys-lru` | Evict least-recently-used, any key | **Default choice for a cache** |
| `allkeys-lfu` | Evict least-*frequently*-used | Stable hot set; resists scan pollution |
| `volatile-lru` | LRU among keys with a TTL only | Mixed cache/persistent in one instance |
| `volatile-ttl` | Evict nearest expiry first | Rarely the right answer |

LFU beats LRU when a one-off bulk scan would otherwise flush your hot set — LRU treats the scan's keys as freshly used, LFU notices they were read once.

Redis LRU is *approximate*: it samples candidates rather than maintaining a true recency list, because exact LRU costs a linked-list update on every access.

## What you say in an interview

> "I'd set TTL from how stale the data is allowed to be, not from a habit — session data can run hours, a price feed maybe seconds. And I'd set `allkeys-lru` with `maxmemory` explicitly, because the default `noeviction` turns memory pressure into write failures. One thing worth knowing: Redis expires lazily, so an expired key still holds memory until it's accessed or sampled."

## Choosing the TTL

The TTL is the staleness budget, and it's answerable from the domain:

- What breaks if this value is N seconds old?
- How often does the underlying data actually change?
- Is there an invalidation path, or is TTL the *only* correctness mechanism? (If it's the only one — as with an [L1 cache](./hot-keys.md) — it must be short.)

Always jitter it. See [single-flight-loading](./single-flight-loading.md).

## Probes you should survive

- *"Why did memory not drop after the TTLs fired?"* → Lazy expiry. Untouched expired keys stay resident until sampled.
- *"LRU or LFU?"* → LFU if a batch job or scan would pollute LRU's recency signal; LRU otherwise, as the simpler default.
- *"What happens at `maxmemory` with `noeviction`?"* → Writes error out. That's correct for a datastore and an outage for a cache — so set the policy deliberately.
- *"Is Redis LRU exact?"* → No, sampled and approximate. Exact LRU would cost bookkeeping on every read.

## Related

[single-flight-loading](./single-flight-loading.md) · [negative-caching](./negative-caching.md) · [working-set-sizing](./working-set-sizing.md) · [cache-placement](./cache-placement.md)
