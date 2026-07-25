> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design a distributed rate limiter — enforcing request limits per user/IP/API key across a cluster of servers without double-counting.
>
> **Key design decisions:**
> - Algorithm choice: Token Bucket (allows controlled burst, most common), Sliding Window Counter (accurate, low memory), Leaky Bucket (smooth output)
> - Redis implementation: INCR + EXPIRE for fixed window; Lua script for atomic sliding window; sorted set (ZADD/ZREMRANGEBYSCORE) for sliding window log
> - Distributed consistency: centralized Redis (accurate, single point) vs local counter with async sync (faster, slight over-count allowed)
> - API Gateway vs application layer: prefer gateway (Nginx, Envoy) for centralized enforcement; application layer for fine-grained per-feature control
> - Response headers: X-RateLimit-Limit, X-RateLimit-Remaining, X-RateLimit-Reset, Retry-After on 429
> - Rule configuration: store rules in Redis or config service; hot-reload without restart; different tiers (free/pro/enterprise)
> - Failure mode: if Redis is down → fail open (allow requests) not fail closed (block everything) to maintain availability
>
> **Key takeaway:** Token Bucket in Redis with Lua scripts for atomic operations — centralize the rate limit state in Redis, and fail open if Redis is unavailable.

---
module: 05-hld-problems
topic: Easy
status: unread
tags: [05-hld-problems, system-design, easy, rate-limiting, token-bucket, sliding-window]
---
# Design a Rate Limiter

> **Difficulty**: Easy | **Asked at**: Amazon, Stripe, Cloudflare, Uber

---

## Problem Statement

Design a rate limiter that restricts the number of requests a client can make to an API within a time window. Clients that exceed the limit receive a 429 Too Many Requests response. The rate limiter should support multiple limiting strategies and be deployable as a distributed service.

---

## Functional Requirements

1. **Limit requests**: Reject requests that exceed a configured threshold (e.g., 100 requests/minute per user)
2. **Multiple scopes**: Limit by user_id, API key, IP address, or endpoint
3. **Multiple algorithms**: Support token bucket (steady rate) and sliding window (burst control)
4. **Headers**: Return `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset` in all responses
5. **Rule configuration**: Rules are configurable per-endpoint and per-user-tier without code deploy

---

## Non-Functional Requirements

- **Latency**: Rate limit check must add < 1ms to every request (P99)
- **Scale**: Handle 1M requests/sec across all users globally
- **Availability**: Must not be a single point of failure — if the rate limiter is down, fail open (allow requests) or fail closed (block requests) per configuration
- **Accuracy**: Allow some inaccuracy (< 0.1% over-limit in exchange for < 1ms latency)
- **Distributed**: Works correctly across multiple app server instances

---

## Core Entities

| Entity | Key Fields |
|--------|-----------|
| `RateLimit Rule` | rule_id, scope (user/ip/endpoint), limit, window_seconds, algorithm |
| `Counter` | key (user_id + endpoint + window), count, expires_at |
| `Client` | client_id, tier, api_key |

---

## API Design

Rate limiting is typically a middleware layer, not a standalone REST API. It intercepts every inbound request before it reaches the handler.

```
Request → Rate Limiter Middleware → (allowed) → Handler
                                 → (denied) → 429 Response

Response headers (always):
  X-RateLimit-Limit: 100
  X-RateLimit-Remaining: 42
  X-RateLimit-Reset: 1735689600   (Unix timestamp when window resets)

Response on exceed:
  HTTP 429 Too Many Requests
  Retry-After: 30
```

**Rule config API** (admin-only):
```http
PUT /admin/rules/{rule_id}
Body: { "scope": "user", "limit": 100, "window_seconds": 60, "algorithm": "sliding_window" }
```

---

## High-Level Design

```
Client Request
  │
  ▼
API Gateway / Load Balancer
  │
  ▼
Rate Limiter Middleware (runs on each App Server)
  │
  ├─── Identify client (extract user_id / API key / IP)
  │
  ├─── Lookup rule (local cache of rule config, refreshed every 60s)
  │
  ├─── Redis INCR / EVALSHA (atomic Lua script)
  │     │
  │     ├── allowed → decrement remaining, set headers, pass to handler
  │     └── denied → return 429
  │
  ▼
Application Handler
```

**Redis as the counter store**: All app server instances share one Redis cluster. Redis is single-threaded → atomic operations. INCR + EXPIRE in a Lua script runs atomically with no race conditions.

**Rule config**: Stored in a configuration service (e.g., DynamoDB). Each app server caches rules locally with a 60-second TTL. Rule changes propagate within 60s.

---

## Deep Dive 1: Rate Limiting Algorithms

**Token Bucket**:
- Bucket holds up to `capacity` tokens. Tokens refill at `rate` per second.
- Each request consumes 1 token. If bucket is empty → reject.
- Allows short bursts (up to `capacity`) then enforces steady rate.
- Implementation: store `(tokens, last_refill_time)` in Redis. On each request: compute tokens added since last refill, clamp to capacity, subtract 1.

```
tokens = min(capacity, tokens + rate × (now - last_refill_time))
last_refill_time = now
if tokens >= 1: tokens -= 1; allow
else: reject
```

Redis Lua script makes this atomic:
```lua
local tokens = tonumber(redis.call('HGET', key, 'tokens'))
local last = tonumber(redis.call('HGET', key, 'last'))
local now = tonumber(ARGV[1])
local rate = tonumber(ARGV[2])
local capacity = tonumber(ARGV[3])
tokens = math.min(capacity, tokens + rate * (now - last))
redis.call('HSET', key, 'last', now)
if tokens >= 1 then
  redis.call('HSET', key, 'tokens', tokens - 1)
  return 1
else
  redis.call('HSET', key, 'tokens', tokens)
  return 0
end
```

**Fixed Window Counter**:
- Count requests in the current minute (window `floor(now / 60)`). Reset to 0 at each minute boundary.
- Problem: a user can make 100 requests at 11:59 and 100 more at 12:00 → 200 requests in 2 seconds.
- Implementation: Redis key = `ratelimit:{user}:{minute}`. `INCR key; EXPIRE key 60`.

**Sliding Window Log**:
- Store timestamp of each request in a sorted set. Count entries in the past 60 seconds.
- Exact but memory-heavy: 100 requests × ~16 bytes per timestamp = 1.6 KB per user per window.
- Implementation: `ZADD key timestamp timestamp; ZREMRANGEBYSCORE key 0 (now-window); ZCARD key`.

**Sliding Window Counter** (recommended):
- Hybrid: combine current window count + previous window count weighted by overlap.
- `rate ≈ prev_count × (1 - elapsed/window) + curr_count`
- Much cheaper than log — 2 integers per user. Accuracy within 1% of true sliding window.
- Implementation: Two Redis keys per user per window. Fetch both, compute weighted sum.

> 🎯 **Staff signal:** The senior framing is that the sliding-window *counter* is a deliberate approximation of the sliding-window *log* — it stores 2 integers and interpolates instead of storing every timestamp, accepting ~1% error to drop memory from O(requests) to O(1) per user. Name the failure it fixes over fixed-window: the boundary burst, where 100 requests at 11:59:59 and 100 at 12:00:01 sail through as "two windows" despite being 200 in two seconds. Choosing the counter because you can *quantify* the accuracy you're trading (≤1%) for the memory you're saving — rather than reaching for the exact log — is the E5→E6 line.

---

## Deep Dive 2: Distributed Rate Limiting

**Problem**: With 10 app servers, each has its own in-process counter. A user could send 10× the limit — 1 request per server — and all pass.

**Solution: Shared Redis counter**. All servers share one Redis cluster as the authoritative counter. Every rate limit check requires a Redis round-trip.

**Latency budget**: Redis P99 latency in the same datacenter: ~0.3ms. Target budget: < 1ms. Acceptable.

**Redis failure**: What if Redis is unavailable?
- **Fail open**: Allow all requests. Protects revenue, allows abuse.
- **Fail closed**: Reject all requests. Protects system, hurts revenue.
- **Local fallback**: Fall back to in-process counter (per-server). Allows up to N× the limit (N servers), but better than total outage.

For most APIs: fail open + alert on Redis failure. For high-value APIs (payment, auth): fail closed.

> 🎯 **Staff signal:** The move is recognizing that a shared Redis counter makes the rate limiter itself a dependency on your hot path, so the *whole* design question is "what happens when Redis is down?" — and the answer isn't one policy, it's a per-endpoint decision. Fail open for a public read API (abuse is cheaper than an outage), fail closed for `/login` and `/charge` (a rate-limit bypass on auth is a security incident, not a revenue dip), and local per-server fallback as the middle ground that caps blast radius at N× instead of ∞×. Naming that the failure mode of the rate limiter must be chosen against the cost of the thing it protects is the E5→E6 framing.

**Multi-region**: Users can hit any region. A user in EU and a VPN hop to US could split their requests across two Redis clusters.
- Option 1: Route all requests for a user_id to a single region (sticky routing by hash). Adds latency for users far from their "home" region.
- Option 2: Accept some over-limit across regions (each region enforces limit independently). Simpler, allows 2× over-limit.
- Option 3: Redis global replication (async). Slight inaccuracy due to replication lag. Practical for most APIs.

---

## Deep Dive 3: Rule Configuration and Hot Paths

**Problem**: Different users have different rate limits (free tier: 100/min, pro: 10,000/min, enterprise: unlimited). Endpoint-level rules differ from user-level rules. Checking all rules for each request in Redis is expensive.

**Solution: Hierarchical rule lookup**:
1. Endpoint-specific rule for this user tier (most specific)
2. User-tier default rule
3. Global default rule

Rules are stored in a configuration service and cached locally on each app server (60-second TTL). Cache eliminates the network hop for rule lookup — only the counter check hits Redis.

**Hot key problem**: A single celebrity user with millions of followers may have their rate limit key as a Redis hot key (all servers hit the same key constantly). Mitigation:
- Shard the key: `ratelimit:{user}:{server_id}:{window}`. Each server increments its own shard. Periodically sum shards for global count. Introduces ~1-second lag in global accounting.
- Use Redis Cluster: the key hashes to a slot; that slot is on one node. Evenly distributed users don't create hot spots.

**Allowlist and denylist**: 
- Allowlist: skip rate limit check for internal services, health check IPs.
- Denylist: immediately reject requests from known bad actors without Redis round-trip. Stored in a local bloom filter updated every 60s.

> 🎯 **Staff signal:** The insight is that a *shared* counter creates a single hot Redis key for exactly the users you most need to limit — the celebrity every server checks simultaneously — turning your rate limiter into its own bottleneck. The senior fix is sharding the key by `server_id` (`ratelimit:{user}:{server_id}:{window}`) so each server owns a local counter and you sum shards for the global view, trading ~1s of accounting lag for eliminating the hot key. Pair it with local rule caching so only the counter check — never rule lookup — touches Redis. Seeing that the naive shared-counter design concentrates load precisely where load is highest is the E5→E6 line.

---

## Interviewer Questions by Level

**Junior**:
- What is a rate limiter and why do we need one?
- Explain the token bucket algorithm. What does the bucket represent?
- What HTTP status code does a rate-limited response return?

**Mid-level**:
- What's the difference between token bucket and sliding window? When do you use each?
- Why does a fixed window counter have the boundary problem? How does a sliding window fix it?
- How do you make rate limit checks atomic in Redis?

**Senior**:
- How do you handle rate limiting in a multi-region setup where the same user can hit any region?
- What do you do when Redis is unavailable — fail open or fail closed? How does your answer change based on the API?
- How would you implement rate limiting for a streaming API (WebSocket, gRPC streaming) where the unit is bytes transferred, not requests?
- Design rate limits that are fair under shared resources — how do you prevent one user from starving others when the system is under load?

---

## Related

**Concepts used in this design**

- [Rate Limiting](../../02-building-blocks/02-performance/02-rate-limiting.md)
- [Redis Internals](../../04-advanced-topics/03-internals/04-redis-internals.md)
- [Circuit Breaker](../../02-building-blocks/02-performance/03-circuit-breaker.md)
- [Distributed Locks](../../02-building-blocks/04-coordination/02-distributed-locks.md)

**Practice next**

- [Unique ID Generator](../01-easy/unique-id-generator.md)
- [Notification Service](../02-medium/notification-service.md)

Notification fan-out needs the same token buckets for per-user caps.

**Frameworks**: [HLD Template](../../07-interview-templates/01-frameworks/01-hld-template.md) · [Capacity Estimation](../../07-interview-templates/02-cheat-sheets/02-capacity-estimation.md) · [Trade-offs Cheat Sheet](../../07-interview-templates/02-cheat-sheets/01-trade-offs-cheat-sheet.md)
