---
id: hot-keys
tags: [caching, sharding, tail-latency]
confidence: 3
last-rehearsed: 2026-07-21
source: 4. Caching — "Hot keys"
---
# Hot keys

**Claim in one sentence.** A single disproportionately-read key saturates one cache node while the rest of the cluster idles, and sharding cannot fix it because consistent hashing sends one key to one node *by construction*.

## Why it happens

Consistent hashing guarantees a key maps to a deterministic node — that determinism is the whole point, and it's also the failure. A celebrity profile or a flash-sale item concentrates all its traffic on whichever node owns that hash slot. Adding nodes re-slices the ring but the hot key still lands somewhere singular.

This is why "add more cache nodes" is the wrong reflex: the cluster has spare capacity, and none of it is reachable for this key.

## What you say in an interview

> "Sharding won't help here — consistent hashing maps that key to one node by design, so adding nodes leaves it hot. I'd measure first with `redis-cli --hotkeys` or per-key metrics, then split the key across N suffixed replicas and read from a random one. If it's extreme, an L1 in-process cache with a 1–5 second TTL takes the traffic off Redis entirely."

## The three fixes, escalating

**1. Key splitting.** Write the value under N suffixed keys; readers pick one at random.
```
celebrity:123        →  celebrity:123:0 … celebrity:123:9
read: GET celebrity:123:{random(0,9)}
```
Spreads load across 10 nodes. Cost: 10× memory for that key, 10 invalidations per write.

**2. Local (L1) cache.** Each app server holds the value in process memory, short TTL (1–5 s). Requests never reach Redis at all. Cost: staleness bounded by TTL, one copy per server.

**3. Request coalescing.** In-flight requests for the same key wait on one upstream fetch instead of issuing N. See [single-flight-loading](./single-flight-loading.md).

## Probes you should survive

- *"Can't you just add cache nodes?"* → No — that's the defining property of the problem. One key, one node.
- *"How would you detect it?"* → `redis-cli --hotkeys`, per-key metrics, or a sampled key histogram. Naming a fix without measurement is the weak answer.
- *"What does key splitting cost on writes?"* → N invalidations instead of one, and N× memory. Fine for a read-dominated hot key; bad if it's also written often.
- *"Why is the L1 TTL so short?"* → It's the staleness bound. Every server has an independent copy with no invalidation path, so TTL is the only correctness lever.

## Related

[cache-aside-race](./cache-aside-race.md) · [working-set-sizing](./working-set-sizing.md) · [single-flight-loading](./single-flight-loading.md) · [lb-algorithm-selection](../load-balancing/lb-algorithm-selection.md)
