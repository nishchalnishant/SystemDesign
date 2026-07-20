---
id: cache-placement
tags: [caching, cdn, architecture]
confidence: 3
last-rehearsed: 2026-07-21
source: 4. Caching — "Where the cache goes" + "When not to cache"
---
# Cache placement (and when not to cache)

**Claim in one sentence.** Each cache layer is defined by its *invalidation story*, not its latency — and the layers you cannot invalidate are the ones that constrain your design.

## The layers

| Layer | Latency | Invalidation | Use for |
|---|---|---|---|
| Browser | 0 ms | **Impossible** — TTL only | Static assets, hashed filenames |
| CDN | 10–50 ms | Purge API, minutes to propagate | Images, video, public pages |
| API gateway | ~1 ms | Direct | Full responses for identical requests |
| App-local (L1) | ~0.1 ms | Per-server; no coordination | Hot keys, config |
| Distributed (Redis) | ~1 ms | Direct, immediate | Sessions, objects, query results |
| DB buffer pool | ~0.1 ms | Automatic | Free — already happening |

The browser row drives a real design decision: since you *cannot* invalidate it, static assets get content-hashed filenames (`app.a1b2c3.js`). The name changes on every deploy, so the stale entry is never requested again. Invalidation-by-renaming, because invalidation-by-purging isn't available.

## What you say in an interview

> "I'd pick the layer by invalidation, not latency. Browser caching I can never invalidate, so anything there needs a content hash in the filename. CDN purges take minutes, so it's for content that tolerates that. Redis I can invalidate immediately, so that's where anything user-specific or freshness-sensitive goes."

## When not to cache

Declining to cache, with a reason, is a strong signal:

- **Write-heavy, rarely-read** — every write invalidates; you maintain entries nobody reads
- **Strict consistency required** — balances, inventory counts, anything with a correctness invariant
- **Uniform random access** — no working set, so hit rate stays near zero (see [working-set-sizing](./working-set-sizing.md))
- **Already-cheap queries** — a 1 ms indexed lookup does not need a 1 ms cache

## Probes you should survive

- *"How do you invalidate a browser cache?"* → You don't. You change the URL — content-hashed filenames.
- *"Why not cache at every layer?"* → Each layer adds a staleness window and an invalidation path to get wrong. Layers multiply debugging cost.
- *"When would you refuse to add a cache?"* → Write-heavy data, strict-consistency data, or uniform random access where hit rate never materializes.
- *"CDN or Redis for user profile images?"* → CDN — public, large, immutable-ish. Redis for the profile *record*, which is small and changes.

## Related

[working-set-sizing](./working-set-sizing.md) · [hot-keys](./hot-keys.md) · [cache-aside-race](./cache-aside-race.md)
