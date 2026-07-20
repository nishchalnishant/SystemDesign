---
id: single-flight-loading
tags: [caching, concurrency, thundering-herd]
confidence: 3
last-rehearsed: 2026-07-21
source: 4. Caching — "Thundering herd / cache stampede"
---
# Single-flight loading

**Claim in one sentence.** When a hot key expires, every concurrent reader misses at once and stampedes the database — the fix is to let exactly one of them do the load and have the rest wait on its result.

## Why it happens

The cache is doing its job right up until the TTL fires. At that instant there is no gradual degradation: N in-flight requests all get a miss simultaneously and all issue the same query. A key served at 10k req/s becomes 10k identical database queries in one tick. The database, sized for the cache-hit steady state, has no headroom for that.

Expiry is the trigger, but so is a cold start, a cache node restart, or an eviction under memory pressure.

## What you say in an interview

> "A hot key expiring causes a stampede — every concurrent reader misses at the same moment and hits the database with the identical query. I'd use single-flight: the first miss takes a lock, loads, and populates; everyone else waits on that one load. Beyond that, jittered TTLs so keys don't expire in lockstep, and probabilistic early refresh so the value gets rebuilt before it's actually gone."

## The three mechanisms

**1. Single-flight / request coalescing.** One loader per key; concurrent callers subscribe to the in-flight result.
```java
// Caffeine does this by construction — get(key, loader) guarantees
// the loader runs once per key even under concurrent access.
cache.get(key, k -> db.load(k));
```
Distributed equivalent: `SET key placeholder NX EX 10` as a mutex; losers poll briefly or serve stale.

**2. TTL jitter.** `ttl = base + random(0, base * 0.1)`. Keys populated together (a deploy, a bulk warm) otherwise expire together. Jitter turns one cliff into a slope.

**3. Probabilistic early expiration.** Each reader independently rolls to refresh slightly before expiry, weighted so the probability rises as the TTL approaches. One reader typically refreshes while the value is still valid, so no one ever sees a miss.

## Trade-offs

| Mechanism | Stops stampede? | Cost |
|---|---|---|
| Single-flight (local) | Per server only | Free; N servers still means N loads |
| Distributed mutex | Yes | A round-trip; lock TTL must exceed load time or it defeats itself |
| TTL jitter | Only synchronized expiry | Free — always do this |
| Probabilistic refresh | Yes, and no one waits | Some wasted early refreshes |
| Serve stale while revalidating | Yes | Bounded staleness during the refresh |

## Probes you should survive

- *"Local single-flight with 50 servers?"* → Reduces 10,000 loads to 50, not to 1. Good enough usually; if not, you need a distributed lock.
- *"What if the lock holder dies mid-load?"* → The lock TTL releases it. That TTL must be longer than a slow load, or a second loader starts while the first is still running.
- *"Is jitter enough on its own?"* → Only for the synchronized-expiry case. A genuinely hot single key still stampedes at its own expiry.
- *"Why serve stale rather than block?"* → Blocking converts a database problem into a latency problem for every waiting request. Stale-while-revalidate keeps p99 flat.

## Related

[hot-keys](./hot-keys.md) · [ttl-expiry-strategies](./ttl-expiry-strategies.md) · [cache-aside-race](./cache-aside-race.md) · [load-shedding-ladder](../scaling/load-shedding-ladder.md)
