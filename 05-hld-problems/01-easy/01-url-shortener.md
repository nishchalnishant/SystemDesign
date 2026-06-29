---
module: 05-hld-problems
topic: Easy
status: unread
tags: [05-hld-problems, system-design, easy]
---
# Design URL Shortener

> **Difficulty**: Easy
> **Topics**: Hashing, Base62 Encoding, Database Sharding
> **Time**: 45 minutes
> **Companies**: Google, Amazon, Meta, Microsoft

---

## Clarifying Questions

- "Should I support custom aliases like `short.ly/my-brand`?"
- "Do you need click analytics — or is redirect-only sufficient?"
- "What's the expected scale: daily URL creations and redirect volume?"
- "Should URLs expire, or persist indefinitely by default?"
- "Are there abuse prevention requirements — rate limiting, malware scanning?"

**Stated scope:** 100M URLs/month, 100:1 read:write, analytics required, custom aliases, optional TTL.

---

## Back-of-Envelope

```
Writes:  100M/month ÷ 30 ÷ 86,400 = ~40/sec avg; 120/sec peak
Reads:   40 × 100 = 4,000/sec avg; 12,000/sec peak

Storage per URL: ~2.5 KB (short_code + long_url + metadata)
5-year storage:  100M × 12 × 5 × 2.5 KB = ~15 TB raw; ~90 TB with replication + indexes

Cache:   20% URLs → 80% traffic; 6M hot URLs × 2.5 KB = 15 GB → fits in one Redis node
```

---

## APIs

```
POST /api/v1/urls
  Body:     { long_url, custom_alias?, ttl? }
  Response: { short_url, short_code, expires_at }

GET /{short_code}
  Response: 302 Location: <long_url>        ← NOT 301; 302 keeps analytics working

GET /api/v1/urls/{short_code}/stats
  Response: { clicks, countries, referrers, timeline }

DELETE /api/v1/urls/{short_code}
  Response: 204 No Content (soft-delete → 410 Gone on future redirects)
```

**301 vs 302:** 301 is cached by the browser permanently — no analytics, no destination updates possible. Use 302 whenever analytics is a requirement.

---

## Architecture

```
Client
  → CDN (popular short codes cached at edge; Cache-Control: max-age=86400)
  → Load Balancer
  → App Servers (stateless, auto-scaling)
  → Redis (hot URL cache; LRU eviction; 95% hit rate; TTL 24h)
  → PostgreSQL Shards (source of truth; sharded by hash(short_code) % N)
  → Kafka (async click events → analytics consumer; no latency impact on redirect path)
  → ID Generator (Redis INCR counter → Base62 encoded short codes)
```

**Read path:** App server checks Redis → on miss, query PostgreSQL → populate Redis → return 302.  
**Write path:** Get next counter → Base62 encode → INSERT into PostgreSQL + SET in Redis → return short URL.

---

## Data Model

```sql
CREATE TABLE urls (
    short_code   VARCHAR(7)  PRIMARY KEY,
    long_url     TEXT        NOT NULL,
    user_id      BIGINT,                      -- NULL for anonymous
    created_at   TIMESTAMP   DEFAULT NOW(),
    expires_at   TIMESTAMP,                   -- NULL = never expires
    is_deleted   BOOLEAN     DEFAULT FALSE,
    custom       BOOLEAN     DEFAULT FALSE
);
-- Sharding: hash(short_code) % N routes to one shard
-- All redirect ops use only short_code → no cross-shard queries needed

CREATE TABLE analytics_events (
    short_code   VARCHAR(7),
    clicked_at   TIMESTAMP,
    country      CHAR(2),
    referrer     TEXT,
    ip_hash      TEXT
) PARTITION BY RANGE (clicked_at);
```

---

## Key Design Decisions

**1. Counter + Base62 (not MD5 hash)**  
Counter-based: zero collisions, predictable length, fast. MD5 collides at ~100M URLs and needs retry logic. Base62 (a-zA-Z0-9) is URL-safe — no `+` or `/` like Base64. `62^7 = 3.5 trillion` unique codes.

**2. Custom aliases share the short_code namespace**  
Same `urls` table with `custom=true` flag. UNIQUE constraint on `short_code` prevents collision. Reserved words (`api`, `admin`, `login`) blocklisted at validation.

**3. Expiry: lazy check on redirect + background cleanup**  
On redirect: if `expires_at < NOW()` → return 410 Gone. Background job deletes expired rows asynchronously. Simpler than proactive deletion; no risk of deleting a URL whose TTL was just extended.

**4. 302 not 301**  
Analytics requires every redirect to hit the server. 301 caches in the browser permanently — no recall mechanism, no destination updates. 302 = every click tracked, destination updatable.

---

## Deep Dives

**Distributed ID generation**  
Redis `INCR url_counter` is atomic, sub-millisecond, no collisions. SPOF risk mitigated by Redis Sentinel. Alternative for no coordination: Snowflake IDs (machine ID + sequence = 4096/ms per node, no network call per ID).

**Caching with Bloom filter**  
Redis cache-aside: check `GET short_code` → miss → query DB → `SETEX short_code 86400 long_url`. Bloom filter in app memory rejects nonexistent codes before any I/O — bot traffic sending random 7-char codes is rejected in microseconds.

**Abuse prevention**  
Rate limit creates: `INCR ratelimit:{user_id}` + `EXPIRE 60` — 10/minute for free tier. On creation, check URL against Google Safe Browsing API. Async malware scan for new domains. Flagged URLs → soft-delete → 410 on future redirects.

---

## Failure Scenarios

| Failure | Impact | Mitigation |
|---------|--------|------------|
| Redis down | Redirects hit DB; latency 2ms → 20ms | Fail open to DB; circuit breaker; auto-restart with RDB snapshot |
| DB shard unavailable | Writes to that shard fail; reads from replicas | RDS Multi-AZ automated failover; RTO < 5 min |
| ID generator down | Cannot create new short codes | Pre-buffer 1M IDs per app server; fallback to UUID + DB UNIQUE retry |

---

## Interview Questions Asked

### Google
1. **"How do you generate short codes?"** → Counter-based with Base62 encoding. Tradeoff: hash (deterministic dedup but collisions) vs counter (no collisions, not deduplicated) vs Snowflake (distributed, no coordination).
2. **"What if the database goes down?"** → Read replicas handle reads; automated failover for writes; cached redirects unaffected.

### Amazon
1. **"How do you prevent spam/phishing?"** → Rate limiting, CAPTCHA for anonymous, domain blacklist, malware scanning on creation.
2. **"How do you scale to billions of URLs?"** → Shard by hash(short_code), Redis for hot URLs, read replicas, CDN for static redirects.

### Common Follow-ups
1. **"301 or 302?"** → Ask if analytics is a requirement. If yes, 302. If not, 301 reduces load.
2. **"How do you handle URL expiry?"** → TTL column + lazy 410 on redirect + background sweeper.

---

## Interviewer Follow-Up Questions

**On the core redirect flow:**
- "Your 301 redirect caches in the browser forever. What's the problem and how do you fix it?" → Use 302 (temporary redirect) — browser doesn't cache it, so every click hits your servers. You lose the traffic-reduction benefit of 301 but regain analytics visibility and the ability to update the destination URL. Choose 301 only for permanent, immovable links.
- "How do you handle the redirect under high read load — 100K redirects/second?" → Cache short_code → long_url in Redis (read-through, LRU eviction). At 100K RPS, even a 90% hit rate means 10K DB reads/sec — add a read replica for the remaining 10%. The redirect API is stateless; scale horizontally behind a load balancer.
- "What happens if Redis is down during a redirect?" → Fail open: fall back to DB read. Latency spikes from ~1ms to ~20ms but functionality is preserved. Circuit breaker avoids hammering a recovering Redis.

**On key generation:**
- "Your base62 generator produces the same key twice — how does that happen and how do you prevent it?" → Hash collision: two different long URLs hash to the same 7-char key, OR two concurrent writes race before checking for uniqueness. Fix: unique constraint on `short_code` column in DB; retry with a different salt on constraint violation.
- "Why base62 and not base64?" → Base64 includes `+` and `/` — these are special characters in URLs and require percent-encoding, which makes the shortened link ugly and error-prone. Base62 (a-z, A-Z, 0-9) is URL-safe with no encoding needed.
- "How do you let users choose a custom short code like `bit.ly/my-brand`?" → Add an `is_custom: bool` flag and a user-submitted `short_code`. Validate: length 3–20, alphanumeric only, not in a reserved word blocklist (`api`, `admin`, `login`). Store in the same table; the redirect logic is identical.

**On expiry and deletion:**
- "A URL expires — how do you ensure expired links return 410 Gone, not a stale redirect?" → Store `expires_at` in the `urls` table. On each redirect, check: `if url.expires_at and url.expires_at < now(): return 410`. Lazy expiry on read is simpler than background deletion. Background job cleans up rows asynchronously to reclaim space.
- "How do you handle a user who wants to delete their shortened URL?" → Soft delete: set `deleted_at = now()`. Redirect handler checks `deleted_at is not None → return 410`. Hard delete loses analytics history. Soft delete lets you audit usage and restore if deleted by mistake.

**On analytics:**
- "How do you count clicks without a synchronous DB write on every redirect?" → Publish a `click` event to Kafka on each redirect. A batch consumer aggregates counts (windowed in Flink or a simple counter flush every 10s) and updates the `click_count` in the DB. Redis `INCR clicks:{short_code}` with periodic DB sync is even simpler at smaller scale.
- "How do you detect abuse — someone using your service to shorten malware URLs?" → On URL creation: check the long URL against Google Safe Browsing API and a domain blocklist. Async scan after creation for new domains. Rate limit by IP/account. If a URL is flagged post-creation: soft-delete it, return 410 for any future redirects, log for manual review.

**On scale:**
- "How do you shard the URL table when it outgrows a single DB?" → Shard by `short_code` hash. All operations (read, write, redirect) use only the `short_code` — no cross-shard queries needed. Shard count should be a power of 2 to make re-sharding easier (consistent hashing or doubling). Read replicas per shard handle read traffic.
