---
id: negative-caching
tags: [caching, security, availability]
confidence: 3
last-rehearsed: 2026-07-20
source: 4. Caching — "Cache penetration"
---
# Negative caching

**Claim in one sentence.** Keys that don't exist never populate the cache, so every request for a missing key reaches the database — which makes "query for nonexistent IDs" a cheap denial-of-service unless you cache the absence too.

## Why it happens

Cache-aside stores what the database returns. When the database returns nothing, there is nothing to store — so the next identical request misses again. The cache is structurally unable to protect against reads for data that isn't there.

Benign version: a client bug requesting a deleted resource in a loop. Hostile version: an attacker enumerating random IDs, every one of which is a guaranteed cache bypass straight to the database.

## What you say in an interview

> "Missing keys never populate the cache, so requests for nonexistent IDs bypass it entirely — that's cache penetration, and it's an easy DoS vector. I'd cache the negative result with a short TTL, a minute or two, so repeated lookups for the same missing key get absorbed. If the ID space is large and sparse, a Bloom filter in front rejects definitely-absent keys without touching Redis at all."

## The two fixes

**1. Cache the miss.** Store a tombstone with a deliberately short TTL.
```
GET user:999  → miss
SELECT db     → no rows
SET user:999 = "__NULL__" EX 120
```
Short TTL matters: if the row is later created, you're serving a false absence until it expires. Two minutes bounds that. Any write path that creates the row should also delete the tombstone.

**2. Bloom filter.** Maintain a filter of all existing IDs. A negative answer is *definitive* — skip the cache and the database both. A positive answer is probabilistic, so you proceed normally.

Bloom filters cannot delete, so deletions require a periodic rebuild or a counting variant. That maintenance is the real cost, and it's why the tombstone approach wins unless the key space is genuinely huge and sparse.

## Trade-offs

| | Tombstone | Bloom filter |
|---|---|---|
| Handles unbounded ID space | No — memory per distinct miss | Yes — fixed size |
| False-absence risk | Bounded by TTL | None (negatives are exact) |
| Deletion support | Trivial | Requires rebuild or counting BF |
| Complexity | Near zero | Real |

A hostile attacker cycling random IDs defeats tombstones by never repeating a key — that's the case the Bloom filter exists for.

## Probes you should survive

- *"Why such a short TTL on the null?"* → It's a false-absence window. If the row appears, reads are wrong until expiry.
- *"Does the tombstone stop an attacker?"* → Only if they repeat keys. Random enumeration never repeats, so each request is still a fresh miss — hence the Bloom filter.
- *"What's the Bloom filter's failure mode?"* → False positives only, which are harmless here: you just do the normal lookup. False negatives are impossible, which is why the negative answer can be trusted.
- *"How does the tombstone get cleared on create?"* → The write path must delete it. Missing that is the same bug class as [cache-aside-race](./cache-aside-race.md).

## Related

[cache-aside-race](./cache-aside-race.md) · [ttl-expiry-strategies](./ttl-expiry-strategies.md) · [single-flight-loading](./single-flight-loading.md)
