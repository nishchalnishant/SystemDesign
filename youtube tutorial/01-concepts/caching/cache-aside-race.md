---
id: cache-aside-race
tags: [caching, consistency, concurrency]
confidence: 3
last-rehearsed: 2026-07-21
source: 4. Caching — "Cache and DB consistency"
---
# Cache-aside read/write race

**Claim in one sentence.** Cache-aside has an interleaving where a reader writes a stale value into the cache *after* the writer's invalidation, leaving the cache permanently wrong with no self-correction.

## Why it happens

```
Thread A (read)          Thread B (write)
─────────────────        ─────────────────
GET cache → miss
SELECT db → v1
                         UPDATE db → v2
                         DELETE cache
SET cache = v1           ← stale value written AFTER the delete
```

A read that missed holds `v1` in hand across the whole write. Its `SET` lands after the `DELETE`, so the invalidation is undone by a request that started before the write existed. The cache now holds `v1`, the DB holds `v2`, and **nothing self-corrects** — no TTL has elapsed, no further invalidation is pending.

## What you say in an interview

> "Cache-aside has a race: a reader that missed can write its stale value back after the writer invalidates. TTL only bounds how long you're wrong. To actually close it you need versioned keys, or CDC so invalidation is a consequence of the commit rather than something the writer has to remember."

## Trade-offs

| Fix | Closes the window? | Cost |
|---|---|---|
| **TTL** | No — bounds staleness only | Simplest; wrong answer if correctness matters |
| **Delete, don't update** | No — shrinks it | Nearly free; still racy |
| **Delayed double delete** | Mostly | Hacky; a sleep in the write path |
| **Versioned keys** (`user:123:v7`) | Yes — old key never read again | Key churn, memory until eviction |
| **CDC invalidation** (Debezium → binlog) | Yes | Real infrastructure cost |

**Never dual-write** (`UPDATE db; SET cache`). If the second write fails the cache is wrong indefinitely, and concurrent writers have no ordering guarantee between their two writes.

## Probes you should survive

- *"Does TTL fix this?"* → No. It bounds the staleness window; it does not prevent the stale write.
- *"Why delete instead of update the cache?"* → Update makes the race worse: two writers can land their `SET`s in either order. Delete at least converges on the next read.
- *"Why is CDC stronger than having the writer invalidate?"* → It moves invalidation from "something the writer must remember" to a consequence of the commit itself. Writers can't forget, and it decouples the cache from application code.
- *"What if the delete fails?"* → That's the dual-write problem again. CDC retries from the log; an app-level delete just loses.

## Related

[working-set-sizing](./working-set-sizing.md) · [hot-keys](./hot-keys.md) · [negative-caching](./negative-caching.md) · [outbox & CDC](../../../04-advanced-topics/01-distributed-architecture/07-outbox-cdc-pattern.md)
