> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design a URL shortener (Bitly) — one of the most common system design interview questions; covers hashing, Base62 encoding, and high-read-volume caching.
>
> **Key design decisions:**
> - ID generation: hash (MD5/SHA256, take first 7 chars) vs counter (auto-increment → Base62 encode); counter preferred for uniqueness
> - Base62 encoding: 62^7 = 3.5 trillion combinations; supports custom aliases with collision detection
> - Storage: write once, read many; MySQL/PostgreSQL for metadata; 301 vs 302 redirect (301 = cached at browser, loses analytics; 302 = server always sees request)
> - Caching: hot URLs cached in Redis (90/10 rule — 20% URLs get 80% traffic); cache-aside; TTL = 24h
> - DB schema: {short_code, original_url, user_id, created_at, expires_at, click_count}
> - Scale: 100M URLs, 10B redirects/day → ~115K reads/sec → need Redis caching + read replicas; writes are trivial
> - Analytics: async click counter (Kafka → batch DB write); avoid write-amplification on hot rows
>
> **Key takeaway:** The redirect layer is read-heavy (100:1 read-to-write); cache aggressively in Redis; use 302 (not 301) for accurate analytics.

---
module: 05-hld-problems
topic: Easy
status: unread
tags: [05-hld-problems, system-design, easy, hashing, base62, caching]
---
# Design a URL Shortener (Bitly)

> **Difficulty**: Easy | **Asked at**: Amazon, Google, Meta, Microsoft

---

## Problem Statement

Design a URL shortening service like Bitly. Users submit a long URL and receive a short URL (e.g., `short.ly/abc1234`). When someone visits the short URL, they are redirected to the original long URL.

---

## Functional Requirements

1. **Shorten URL**: Given a long URL, return a unique short URL
2. **Redirect**: GET short URL → 302 redirect to original long URL
3. **Custom aliases**: User can optionally specify the short code (e.g., `short.ly/mycompany`)
4. **Analytics**: Track click count per short URL
5. **Expiration**: Short URLs can have an optional TTL

---

## Non-Functional Requirements

- **Scale**: 100M new URLs/month → 40 writes/sec; 10B redirects/month → 4,000 reads/sec avg, 12,000 peak
- **Latency**: P99 redirect < 10ms (cached); create < 100ms
- **Availability**: 99.99% uptime — no single point of failure
- **Durability**: Short codes must never be lost or reassigned
- **Storage**: ~15 TB over 5 years (100M URLs/month × 12 × 5 × 2.5 KB)

---

## Core Entities

| Entity | Key Fields |
|--------|-----------|
| `URL` | short_code (PK), long_url, user_id, created_at, expires_at, click_count |
| `User` | user_id, api_key, rate_limit_tier |
| `AnalyticsEvent` | short_code, clicked_at, ip_hash, country, referrer |

---

## API Design

```http
POST /api/v1/urls
Body: { "long_url": "https://...", "custom_alias": "mylink", "ttl_days": 365 }
Response 201: { "short_url": "https://short.ly/abc1234", "short_code": "abc1234" }

GET /{short_code}
Response 302: Location: <long_url>   (or 404 if not found / expired)

GET /api/v1/urls/{short_code}/stats
Response 200: { "click_count": 12345, "created_at": "...", "expires_at": "..." }

DELETE /api/v1/urls/{short_code}
Response 204
```

**301 vs 302**: Use 302 (temporary redirect). 301 tells browsers to cache the redirect permanently — every subsequent click goes directly to the destination, bypassing your server entirely and killing analytics. 302 ensures every click hits your server.

---

## High-Level Design

```
Client
  │
  ▼
CDN (CloudFront / Fastly)
  │  ← serves cached redirects for globally hot short codes
  ▼
Load Balancer
  │
  ▼
App Servers (stateless, horizontally scaled)
  │          │
  ▼          ▼
Redis      ID Generator
(hot URL   (Snowflake /
 cache)     ticket server)
  │
  ▼
PostgreSQL (sharded by hash(short_code))
  │
  ▼
Kafka → Analytics Consumer → ClickHouse
```

**Short code generation**: Use a distributed counter (Redis `INCR` or ticket server) to get a globally unique integer, then encode it as Base62 (0-9, a-z, A-Z). 7 Base62 characters = 62^7 = 3.5 trillion unique codes.

**Why not MD5/SHA256?**: Hashing produces a fixed output from the URL content. Two users shortening the same URL would get the same code (acceptable) but different URLs could collide at 7 characters (unacceptable at 100M URLs).

**Read path**: Client → CDN → (cache hit: 302 response) or (cache miss: App Server → Redis → DB → 302 response). Redis holds 20% of URLs that handle 80% of traffic. TTL aligned with URL expiry.

**Write path**: App Server → ID Generator → Base62 encode → INSERT into PostgreSQL shard → write to Redis.

**Database sharding**: Hash `short_code` to select shard. 4 shards × ~4 TB each handles 5-year storage. Each shard has read replicas.

---

## Deep Dive 1: Unique ID Generation at Scale

**Problem**: Two stateless app servers generating IDs simultaneously must not produce duplicates.

**Option 1 — Redis INCR**: All servers call `INCR counter` on a Redis primary. Redis is single-threaded; INCR is atomic. Single point of failure — mitigated by Redis Sentinel/Cluster. Throughput: ~100K INCR/sec. Sufficient for 40 writes/sec.

**Option 2 — Ticket Server**: A dedicated MySQL/PostgreSQL row with `AUTO_INCREMENT`. Each app server fetches a batch of 1,000 IDs at once, uses them locally. Reduces network round-trips 1,000×. Survives brief ticket server unavailability (in-flight batch). Single table = single shard, but workload is tiny.

**Option 3 — Snowflake IDs**: 64-bit integer = timestamp (41 bits) + machine ID (10 bits) + sequence (12 bits). Generates 4,096 unique IDs/ms/machine without coordination. Encode to Base62 for the short code. K-ordered — recent URLs sort together, simplifying range queries on `created_at`.

**Recommendation**: Ticket server with batch pre-allocation is simplest and sufficient. Snowflake if you need machine-autonomous generation.

---

## Deep Dive 2: Redirect Latency Optimization

**Target**: P99 < 10ms for 12K redirects/sec.

**Layer 1 — CDN**: CloudFront edge nodes cache `GET /{short_code}` → 302 response. Cache-Control: max-age=3600 for non-expiring URLs. Cache miss rate: ~5% for popular URLs (CDN hit rate: 95%). CDN serves the request from the PoP nearest the user, eliminating intercontinental RTT.

**Layer 2 — Redis read-through cache**: App server checks Redis before touching PostgreSQL. Cache key: `url:{short_code}`, value: `long_url`. TTL matches URL expiry. Hot URLs stay in Redis indefinitely (LRU eviction only on memory pressure). 32 GB Redis covers ~12M URL mappings (2.5 KB each).

**Layer 3 — Read replicas**: PostgreSQL read replicas in each region handle the 5% Redis misses. Reads are simple point lookups on the primary key index — O(log n), sub-millisecond at shard size.

**Cache warming on write**: When a URL is created, immediately write to Redis. Avoids a cold miss on the first click (common for viral links shared seconds after creation).

---

## Deep Dive 3: Custom Aliases and Collision Prevention

**Problem**: User-specified custom aliases (e.g., `short.ly/amazon`) share the same namespace as system-generated codes. A generated code could collide with an existing custom alias.

**Solution**: Store all short codes in the same `urls` table with a UNIQUE constraint on `short_code`. 

For custom aliases:
1. Validate format: 3–20 chars, alphanumeric + hyphens only
2. Attempt INSERT with the user's alias as `short_code`
3. If UNIQUE constraint violation → alias taken, return 409 Conflict with a suggestion

For generated codes:
1. Fetch next counter value → Base62 encode → attempt INSERT
2. On collision (astronomically rare with counter-based generation): retry with next counter value

**Reservation system**: For high-value custom aliases (brand names), allow pre-reservation via admin API before a URL is created. Stored as a `reserved_aliases` table. Checked before INSERT.

**Rate limiting custom aliases**: Limit to 10 custom aliases per user per day to prevent namespace squatting.

---

## Interviewer Questions by Level

**Junior**:
- How does Base62 encoding work? Why 7 characters?
- What's the difference between 301 and 302? Which do you use and why?
- How do you handle a short code that doesn't exist?

**Mid-level**:
- Walk me through the write path from "user submits URL" to "short URL returned"
- How does the Redis cache stay consistent when a URL is deleted?
- How would you shard the database? What's the sharding key?
- What happens when Redis is down?

**Senior**:
- How do you guarantee uniqueness of short codes across a multi-region deployment?
- How would you detect and block malicious URLs (phishing, malware)?
- How does the CDN handle URL expiry? (CDN cached 302 pointing to an expired URL)
- Design the analytics pipeline — how do you count 10B clicks/month without impacting redirect latency?
- How would you support vanity URL campaigns with guaranteed availability (a Fortune 500 company's marketing launch)?
