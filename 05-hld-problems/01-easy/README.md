> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** 9 easy HLD interview problems — each introducing one or two core concepts in isolation, building sequentially toward more complex designs.
>
> **Problems and core concepts:**
> - URL Shortener: hashing, Base62 encoding, DB sharding for 100M URLs
> - Unique ID Generator: Snowflake ID (timestamp + machine ID + sequence), no central coordination
> - Rate Limiter: token bucket algorithm, Redis INCR + EXPIRE, distributed enforcement
> - Pastebin: object storage (S3), CDN for content delivery, TTL-based expiration
> - Key-Value Store: consistent hashing, LSM tree storage engine, tunable replication
> - Autocomplete: Trie data structure, prefix cache, frequency-ranked suggestions
> - Web Crawler: distributed URL frontier (BFS queue), Bloom filter deduplication, politeness (robots.txt)
> - Booking System: optimistic locking, idempotency keys, overbooking prevention with DB transactions
> - Leaderboard: Redis ZADD/ZRANGE (sorted sets), windowed rankings, score aggregation
>
> **Key takeaway:** Solve these in order — each one introduces concepts the next depends on; time yourself at 45 min each using the HLD template.

---

# Easy — HLD Problems

High-level design problems that introduce one or two core concepts in isolation. Do these first and time yourself at 45 minutes each. Use the [HLD template](../../07-interview-templates/01-frameworks/01-hld-template.md) on every run.

## Recommended Order

Work through these sequentially — each one introduces a concept that later problems depend on.

| Order | Problem | Core Concept Introduced | Prerequisite |
|-------|---------|------------------------|-------------|
| 1 | [URL Shortener](url-shortener.md) | Hashing, Base62, DB sharding | None |
| 2 | [Unique ID Generator](unique-id-generator.md) | Snowflake IDs, distributed ID generation | URL Shortener (sharding) |
| 3 | [Rate Limiter](rate-limiter.md) | Token bucket, Redis atomic ops | Unique ID Generator |
| 4 | [Pastebin](pastebin.md) | Object storage, CDN, TTL | Rate Limiter |
| 5 | [Key-Value Store](key-value-store.md) | Storage engine, LSM tree, replication | Pastebin |
| 6 | [Autocomplete](autocomplete.md) | Trie, prefix caching, ranking pipeline | Key-Value Store |
| 7 | [Web Crawler](web-crawler.md) | Distributed queues, dedup, politeness | All of the above |
| 8 | [Booking System](booking-system.md) | Inventory locking, idempotency, overbooking prevention | Web Crawler |
| 9 | [Leaderboard](leaderboard.md) | Redis sorted sets, windowed ranking, score aggregation | Key-Value Store |

## What Makes an "Easy" Problem

Easy does not mean trivial. It means the problem tests one or two concepts deeply rather than requiring you to orchestrate many subsystems simultaneously. The URL Shortener looks simple until you examine ID uniqueness at 100M URLs/month, cache invalidation semantics, and redirect HTTP semantics (301 vs. 302). The simplicity is in scope, not in depth.

## Cross-References

The building blocks used across all seven problems:

- Caching: [02-building-blocks/caching-layer.md](../../02-building-blocks/02-performance/01-caching-layer.md)
- Rate Limiting: [02-building-blocks/rate-limiting.md](../../02-building-blocks/02-performance/02-rate-limiting.md)
- Sharding: [02-building-blocks/sharding.md](../../02-building-blocks/03-data-partitioning/01-sharding.md)
- Message Brokers (Web Crawler): [02-building-blocks/message-brokers.md](../../02-building-blocks/04-coordination/01-message-brokers.md)
