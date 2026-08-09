# Caching Concepts

7 single-claim concept files on caching. Each states a claim, gives a line to say verbatim in an interview, and lists the probes that usually follow it.

| File | What it covers |
|------|-----------------|
| [working-set-sizing](./working-set-sizing.md) | Cache the working set, not the dataset; hit-rate to DB-load is non-linear |
| [cache-aside-race](./cache-aside-race.md) | A reader's `SET` can land after a writer's `DELETE` — never dual-write |
| [hot-keys](./hot-keys.md) | Consistent hashing maps one key to one node by construction, so sharding cannot fix a hot key |
| [cache-placement](./cache-placement.md) | Choose the cache layer by how you invalidate it, not by latency |
| [single-flight-loading](./single-flight-loading.md) | Hot-key expiry stampedes the DB; coalesce requests, jitter TTLs, refresh early |
| [negative-caching](./negative-caching.md) | Cache the misses too, or cache penetration walks straight through to the DB |
| [cache-interface-design](./cache-interface-design.md) | `get`/`put`/`invalidate` is the contract; the eviction policy is injected, not exposed |
| [ttl-expiry-strategies](./ttl-expiry-strategies.md) | Lazy expiry means expired keys still hold memory until touched |

See also: [INDEX.md](../INDEX.md) for the mistakes table and cross-cutting threads for this topic.
