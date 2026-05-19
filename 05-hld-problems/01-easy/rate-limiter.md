# Design Rate Limiter

> **Difficulty**: Easy
> **Topics**: Token Bucket, Sliding Window, Distributed Systems
> **Time**: 45 minutes
> **Companies**: Google, Amazon, Stripe, Cloudflare

---

## Problem Mindmap

```
Rate Limiter
├── Problem Constraints
│   ├── Scale → 10M users, 8.4M checks/sec peak (100 req/user/day × 10M ÷ 86400 × burst factor)
│   ├── Latency target → < 1ms overhead added to every API request (inline check)
│   └── Core hardness → atomicity of counter check+increment across distributed nodes without per-request DB writes
├── Architecture Derivation
│   ├── Step 1 → In-memory counter per server → doesn't work across multiple app servers (no shared state)
│   ├── Step 2 → Centralized DB counter → too slow (DB write on every request = bottleneck)
│   ├── Step 3 → Redis INCR/EXPIRE → atomic, sub-millisecond, shared across all nodes
│   └── Step 4 → Lua script in Redis for Token Bucket → atomic check+decrement in single round-trip
├── Core Components
│   ├── Redis → shared rate limit counters; INCR+EXPIRE for Fixed Window; Lua for atomic Token Bucket
│   ├── API Gateway / Middleware → intercepts every request before routing; returns 429 on limit exceeded
│   ├── Config Service → rule store (user_id → limit, endpoint → limit); cached locally with 60s TTL
│   └── Multi-DC sync → async gossip between regional Redis clusters; eventual consistency acceptable for limits
├── Data Model
│   ├── Fixed Window → key: "rl:{user_id}:{endpoint}:{window_ts}" → INCR; EXPIRE = window_size
│   ├── Sliding Window → sorted set: ZADD ts score=timestamp; ZREMRANGEBYSCORE; ZCARD for count
│   └── Token Bucket → hash: {tokens: N, last_refill: ts}; Lua script atomically refills + decrements
├── APIs
│   ├── check_and_consume(user_id, endpoint) → allowed: bool, remaining: int, retry_after: int
│   └── GET /admin/limits/{user_id} → current config and current consumption
├── Critical Trade-offs
│   ├── Fixed vs Sliding Window → Sliding chosen for accuracy; Fixed allows 2× burst at window boundary
│   ├── Token Bucket vs Sliding → Token Bucket chosen for bursty API clients; smooths bursts naturally
│   └── Centralized vs Local → Redis centralized chosen; local cache risks stale limits under burst
├── Failure Scenarios
│   ├── Redis down → fail-open (allow requests) or fail-closed (429 all); fail-open preferred for availability
│   ├── Clock skew between nodes → use server-side Redis timestamps; never trust client clock
│   └── Multi-DC → async replication; brief over-limit allowance acceptable vs blocking cross-region on every check
└── Interview Angles
    ├── Stripe → "Design rate limiting for payment API" → per-key token bucket, hard limits for fraud
    ├── Cloudflare → "How do you limit at 1M req/sec?" → local L1 counter + Redis sync every 100ms
    └── Follow-up → "How do you handle distributed token bucket refill?" → Lua script with atomic HGETALL + HMSET
```

---

## What Breaks Without This System

Without rate limiting, any single client can saturate your API. A script sending 10,000 requests/second to `POST /login` costs you nothing to receive but exhausts your database connection pool in seconds. Every legitimate user gets 503 errors. This is not a hypothetical: it is exactly what happened to GitHub in 2018, when a distributed credential-stuffing attack generated millions of login attempts against unauthenticated endpoints.

The physical constraint: your backend can process X requests per second. Any client that exceeds a fair share of X must be throttled without adding meaningful latency (< 1ms) to the remaining clients.

---

## Derive the Architecture

**Start with 1 server + in-memory counter:**
One app server, one HashMap: `userId -> count`. On each request, increment the counter; if it exceeds the limit, return 429. Latency added: < 0.1ms (RAM lookup).

**What breaks when you add a second app server?**
User A sends 50 requests to Server 1 and 50 requests to Server 2. Each server's local counter reads 50 — both servers allow all requests. The user effectively gets 2× the intended limit. The fix: move counter state out of each server into a shared Redis instance.

**What breaks with Redis at 8.4M checks/sec (10M users × 1,000 req/hr ÷ 3,600)?**
A single Redis node handles ~1M commands/sec. At 8.4M checks/sec you need a Redis Cluster. Use hash tags `{userId}` to pin each user's key to one shard — this avoids cross-shard coordination for the atomic increment.

**What breaks with a naive `GET count → check → INCR`?**
Two servers read count=99 simultaneously, both decide "allow," both write 100. The limit is violated by exactly 1. Fix: Redis Lua script executes the entire read-check-write atomically in a single Redis command — no race window.

**What breaks with fixed-window counting?**
User sends 100 requests at 12:00:59 (window 1) and 100 at 12:01:01 (window 2). Both windows show 100 — no violation detected, but 200 requests fired in 2 seconds. Fix: sliding window counter interpolates the previous window's count based on elapsed time, eliminating the boundary burst for O(1) memory.

**Resulting architecture:** API Gateway checks Redis Cluster (Lua script for atomic token-bucket decrement). Redis Cluster sharded by `{userId}`. For multi-region: local Redis per DC for sub-1ms latency, with configurable global budget splits per region.

---

## Why This Is Hard

1. **Distributed state**: A rate limiter on a single server is trivial. The hard part is that your API has 100 servers. User requests can hit any of them. Every server needs to see the same counter state — or you'll allow 100× the intended rate (one per server).
2. **Atomicity**: The check-then-increment operation (`get count → check → increment`) must be atomic. In a distributed system, two servers can both read "count=99", both decide "allow", and both write "count=100" — letting 2 requests through when only 1 should pass.
3. **The boundary burst problem**: Fixed windows have a well-known exploit. If the window resets at :00 each minute and the limit is 100, a user can send 100 at :59 and 100 more at :01 — 200 requests in 2 seconds with zero violations.
4. **Low latency requirement**: The rate-limit check adds to every API call's latency. It must be < 1ms or it becomes the bottleneck itself.
5. **Multi-datacenter**: When your service spans regions, do you want per-datacenter limits or global limits? Global limits require cross-datacenter coordination, adding latency.

---

## Requirements

### Functional
- Limit requests per user/API key/IP
- Return 429 (Too Many Requests) when limit exceeded
- Support different time windows (second, minute, hour, day)
- Support different limit tiers (free vs. paid users)

### Non-Functional
- Low latency (< 1ms per check)
- Highly available (99.99%)
- Distributed (works correctly across multiple servers)
- Memory efficient

---

## Capacity Estimation

```
Assume 10M active users, 1,000 requests/user/hour average

Check QPS:
10M users × 1,000 req/hr ÷ 3,600 sec = ~2.8M checks/sec
Peak (3×): 8.4M checks/sec

Redis key storage:
One entry per user: 10M entries × ~100 bytes = 1 GB (trivial)
```

---

## API Design

```http
GET /api/resource
Headers:
  X-API-Key: abc123

Response (Success): 200 OK
Headers:
  X-RateLimit-Limit: 1000
  X-RateLimit-Remaining: 999
  X-RateLimit-Reset: 1675843200

Response (Rate Limited): 429 Too Many Requests
Headers:
  X-RateLimit-Limit: 1000
  X-RateLimit-Remaining: 0
  X-RateLimit-Reset: 1675843200
  Retry-After: 3600
Body:
  {"error": "rate_limit_exceeded", "message": "Try again in 3600 seconds"}
```

**Why expose these headers?** Good API citizenship. Clients that respect `X-RateLimit-Remaining` will back off gracefully rather than hammering until they hit 429. `Retry-After` tells them exactly when to retry.

---

## Algorithms

### 1. Fixed Window Counter

```java
// Simple but has burst problem at window boundaries
public class FixedWindowRateLimiter {
    private int maxRequests;
    private int windowSeconds;
    private RedisClient redis;

    public FixedWindowRateLimiter(int maxRequests, int windowSeconds) {
        this.maxRequests = maxRequests;
        this.windowSeconds = windowSeconds;
        this.redis = new RedisClient();
    }

    public boolean allowRequest(String userId) {
        long currentWindow = System.currentTimeMillis() / 1000 / windowSeconds;
        String key = "rate_limit:" + userId + ":" + currentWindow;
        long count = redis.incr(key);

        if (count == 1) {
            redis.expire(key, windowSeconds);
        }

        return count <= maxRequests;
    }
}
```

**The boundary burst exploit:**
Limit is 100/min. Window resets at :00.
- At 12:00:59 → send 100 requests. All pass (window 1).
- At 12:01:01 → send 100 more. All pass (window 2).
- Result: 200 requests in 2 seconds, both windows show "100" — no violation detected.

**When to use:** Edge/WAF layer for simple DoS protection where occasional bursts are acceptable.

---

### 2. Sliding Window Log

```java
public class SlidingWindowLogRateLimiter {
    private int maxRequests;
    private int windowSeconds;
    private RedisClient redis;

    public boolean allowRequest(String userId) {
        String key = "rate_limit:" + userId;
        long now = System.currentTimeMillis() / 1000;
        long windowStart = now - windowSeconds;

        // Remove timestamps outside the window
        redis.zremrangebyscore(key, 0, windowStart);

        // Count requests in window
        long count = redis.zcard(key);

        if (count < maxRequests) {
            redis.zadd(key, now, String.valueOf(now));
            redis.expire(key, windowSeconds);
            return true;
        }

        return false;
    }
}
```

**Pros:** Perfectly accurate — no boundary bursts possible
**Cons:** Memory intensive. Stores a timestamp per request. A user sending 10,000 req/hour has 10,000 entries in Redis. At scale, this is expensive.

**When to use:** Financial APIs, audit-critical scenarios where accuracy is non-negotiable.

---

### 3. Sliding Window Counter

```java
public class SlidingWindowCounterRateLimiter {
    private int maxRequests;
    private int windowSeconds;
    private RedisClient redis;

    public boolean allowRequest(String userId) {
        long currentWindow = System.currentTimeMillis() / 1000 / windowSeconds;
        long previousWindow = currentWindow - 1;

        String currentKey = "rate_limit:" + userId + ":" + currentWindow;
        String prevKey = "rate_limit:" + userId + ":" + previousWindow;

        long currentCount = Long.parseLong(redis.getOrDefault(currentKey, "0"));
        long prevCount = Long.parseLong(redis.getOrDefault(prevKey, "0"));

        // Calculate weighted count based on how far into the current window we are
        long now = System.currentTimeMillis();
        double currentWindowElapsedPos = (double) (now % (windowSeconds * 1000)) / (windowSeconds * 1000);
        double overlapPercentage = 1.0 - currentWindowElapsedPos;

        long estimatedCount = (long) (prevCount * overlapPercentage) + currentCount;

        if (estimatedCount < maxRequests) {
            redis.incr(currentKey);
            redis.expire(currentKey, windowSeconds * 2);
            return true;
        }
        return false;
    }
}
```

**How it works:** At any point in the current window, we estimate how many "previous window" requests would still be within a true sliding window, then add the current window count. The assumption: requests in the previous window were evenly distributed.

**Pros:** O(1) memory (only two integers per user), good accuracy
**Cons:** Assumes uniform distribution in prior window — a burst at the start of the previous window could be slightly under-counted

**When to use:** The sweet spot for most production APIs. Used by Cloudflare for its distributed rate limiting.

---

### 4. Token Bucket (Most Common)

```java
public class TokenBucketRateLimiter {
    private int maxRequests;
    private int windowSeconds;
    private RedisClient redis;

    public boolean allowRequest(String userId) {
        String key = "rate_limit:" + userId;
        Map<String, String> bucket = redis.hgetall(key);

        long now = System.currentTimeMillis() / 1000;
        double lastRefill = Double.parseDouble(bucket.getOrDefault("last_refill", String.valueOf(now)));
        double tokens = Double.parseDouble(bucket.getOrDefault("tokens", String.valueOf(maxRequests)));

        // Refill tokens proportional to elapsed time
        double elapsed = now - lastRefill;
        double tokensToAdd = elapsed * ((double) maxRequests / windowSeconds);
        tokens = Math.min(maxRequests, tokens + tokensToAdd);

        if (tokens >= 1) {
            tokens -= 1;
            Map<String, String> values = new HashMap<>();
            values.put("tokens", String.valueOf(tokens));
            values.put("last_refill", String.valueOf(now));
            redis.hset(key, values);
            redis.expire(key, windowSeconds);
            return true;
        }

        return false;
    }
}
```

**The burst allowance intuition:** If a user has been quiet for 30 seconds on a 10 req/min limit, they've "saved up" 5 tokens. They can fire 5 requests instantaneously. This is intentional and correct — a legitimate user catching up on a bursty task shouldn't be penalized.

**Pros:** Smooth rate limiting, intentionally allows controlled bursts, intuitive model
**Cons:** Slightly more complex state (tokens + last_refill vs. simple counter)

**When to use:** Most APIs. This is what Stripe, AWS, and most major API providers use.

---

### 5. Leaky Bucket (Queue-Based)

```
Incoming requests → [FIFO Queue] → Process at constant rate → Backend

If queue is full → Drop request (429)
```

Unlike Token Bucket which allows bursts, Leaky Bucket enforces strict output rate. A burst of 1,000 requests still gets processed at exactly 100 req/sec.

**When to use:** Payment processing, order queues — anywhere you need constant-rate output to a backend that can't handle bursts.

---

## Architecture

```
Client Request
     ↓
Load Balancer
     ↓
API Gateway (Rate Limiter Check)  ← checks Redis
     ↓
Redis Cluster (Token Bucket State)
     ↓
Backend Service (if allowed)
```

**Where to place the rate limiter:**
- **API Gateway layer**: Ideal. Centralized, before any business logic runs.
- **Application layer**: Useful for business-level limits (e.g., "10 orders/day per user") that the API gateway doesn't know about.
- **CDN/Edge (WAF)**: For IP-based DoS protection, before requests reach your infrastructure at all.

---

## Distributed Rate Limiting

**The core problem:** Multiple servers need shared state and atomic operations.

### Solution 1: Redis Lua Scripts (Atomic Operations)

The check-then-increment is not atomic in Redis by default. Two servers can both read "99 tokens", both allow, and both set "98 tokens" — losing a decrement.

Lua scripts execute atomically within Redis, preventing race conditions without distributed locks:

```lua
-- Token Bucket Lua Script (executes atomically in Redis)
local key = KEYS[1]
local max_tokens = tonumber(ARGV[1])
local refill_rate = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local requested = tonumber(ARGV[4])

local bucket = redis.call('HMGET', key, 'tokens', 'last_refill')
local tokens = tonumber(bucket[1]) or max_tokens
local last_refill = tonumber(bucket[2]) or now

local elapsed = now - last_refill
local tokens_to_add = math.floor(elapsed * refill_rate)
tokens = math.min(max_tokens, tokens + tokens_to_add)

if tokens >= requested then
    redis.call('HMSET', key, 'tokens', tokens - requested, 'last_refill', now)
    redis.call('EXPIRE', key, math.ceil(max_tokens / refill_rate))
    return 1  -- Allowed
else
    return 0  -- Denied
end
```

### Solution 2: Redis Cluster with Hash Tags

Use hash tags `{userId}` to ensure a user's key always routes to the same Redis shard. This avoids cross-shard coordination:

```
Server 1 → Redis Node A (hash slot 0-5460)
Server 2 → Redis Node B (hash slot 5461-10922)
Server 3 → Redis Node C (hash slot 10923-16383)

Key: {user123}:rate_limit → always hashes to same node
```

---

## Multi-Datacenter Rate Limiting

**Challenge:** A user hitting US-East and EU-West simultaneously would bypass per-datacenter limits.

**Option 1: Local limits with per-DC budget**
- Each datacenter gets an 80% budget (e.g., 80 of 100 allowed requests)
- Fast (no cross-DC coordination), slightly over-allows during cross-DC bursts
- Good for: High-throughput, low-criticality APIs

**Option 2: Global Redis (Centralized)**
- All DCs check the same Redis cluster in a single region
- Accurate but adds cross-region latency (~50-150ms depending on distance)
- Good for: Financial APIs, payment limits

**Option 3: Hybrid — local fast-path + async global sync**
- Local Redis for immediate checks (fast)
- Async sync to global Cassandra/Redis every 100ms
- Slight over-permission window of ~100ms is acceptable for most use cases

---

## Failure Scenarios

### Redis Goes Down

**Impact:** Cannot check rate limits
**Options:**
- **Fail open**: Allow all requests (risk of overload, but service stays up)
- **Fail closed**: Return 429 for all requests (safe, but breaks service)
- **Recommendation**: Fail open with aggressive alerting. A brief period of unlimited traffic is better than a full outage. Circuit breaker pattern: after N consecutive Redis failures, degrade to a simple in-memory counter.

### Redis Cluster Rebalance

During node additions/removals, some keys temporarily route to new nodes and appear empty (reset to zero). A small burst is permitted during this window. Use virtual nodes (consistent hashing) to minimize key migration.

---

## Trade-offs

| Algorithm | Accuracy | Memory | Burst Handling | Use Case |
|-----------|----------|--------|----------------|----------|
| Fixed Window | Low (boundary burst) | Very Low | No protection | Simple DoS protection at edge |
| Sliding Window Log | Exact | High | No | Financial APIs, strict auditing |
| Sliding Window Counter | High | Very Low | Partial | Recommended general purpose |
| Token Bucket | High | Low | Controlled burst allowed | Most APIs (Stripe, AWS) |
| Leaky Bucket | High | Medium | None (constant output) | Payment queues, traffic shaping |

---

## Interview Tips

**Common Questions:**
- **"How would you rate limit across multiple datacenters?"**
  → Per-DC budgets for performance, global Redis for strict accuracy, hybrid for balance. State your assumption first ("assuming < 100ms cross-DC latency is acceptable...").

- **"How does rate limiting differ for B2B vs B2C?"**
  → B2B (API keys): Higher limits, strict enforcement, detailed analytics, token bucket. B2C (User/IP): DoS protection priority, softer limits, fixed window at edge/WAF acceptable.

- **"What if Redis goes down?"**
  → Fail open with circuit breaker and alerting. Briefly allowing unlimited traffic is less bad than a full service outage.

- **"How do you make the check atomic?"**
  → Redis Lua scripts — they execute as a single atomic operation. No distributed locks needed.

---

## Interview Questions Asked

### Google
1. **"Design a distributed rate limiter for the Google Maps API — 1B requests/day across 50 services."** → Tests end-to-end distributed design: the interviewer wants to see Redis Cluster with consistent hashing for key locality, sliding window counter as the algorithm, and a discussion of per-service vs. per-key namespacing in Redis (`ratelimit:{api_key}:{service}`).
2. **"Your rate limiter adds 2ms latency per request. How do you reduce that?"** → Probes optimization thinking: in-process shadow counter (local decrement, async sync to Redis every 100ms) cuts the synchronous Redis call. Acceptable to serve slightly stale counts in exchange for sub-millisecond overhead.

### Meta
1. **"Compare sliding window log vs. token bucket — which would you use for the Instagram Graph API?"** → Sliding window log is exact but memory-heavy (one entry per request); token bucket allows controlled bursting (good for bursty mobile clients) with O(1) memory. For a social API with bursty patterns, token bucket wins.
2. **"How do you rate limit at the edge (CDN/WAF) vs. at the service level, and when do you do both?"** → Edge rate limiting (Cloudflare, AWS WAF) is coarse-grained, IP-based, stateless per PoP — stops volumetric DDoS before it hits your infrastructure. Service-level is fine-grained (per user/API key), accurate across regions. Both layers are needed: edge for DoS, service for fairness.

### Amazon
1. **"How do you implement rate limiting at the API Gateway layer for AWS customers without adding per-request database calls?"** → Token bucket stored in-memory per gateway instance with periodic sync to DynamoDB for cross-instance coordination. The interviewer is probing the trade-off: local state = fast but slightly inaccurate; centralized state = accurate but adds latency.
2. **"A premium customer needs to burst to 10× their normal limit for 30 seconds. How do you support this?"** → Token bucket with a burst capacity bucket layered on top of the steady-state bucket. Premium tier configuration: `bucket_capacity = 10 × steady_rate`, `refill_rate = steady_rate`. Burst headroom is pre-allocated in the config; no special casing required at runtime.

### Common Follow-ups
1. **"What happens if Redis goes down mid-request?"** → Fail open (allow the request) with a circuit breaker that tracks consecutive Redis failures. After N failures, switch to a local in-memory counter with a short TTL. Alert immediately — local counters are per-instance and don't coordinate, so limits effectively multiply by instance count.
2. **"Should you rate limit by user ID or by IP address?"** → By user ID for authenticated APIs (accurate, survives NAT/proxies, enables per-tier limits). By IP for unauthenticated endpoints (only option pre-auth, but shared IPs at corporate NATs can cause false positives). Best practice: both — IP limit at edge, user ID limit at service.
3. **"How do you handle rate limiting in a multi-tenant SaaS where tenants have different quotas?"** → Store quota config in a fast lookup (Redis Hash or in-memory map). Key the rate limit bucket by `{tenant_id}:{endpoint}`. On each request, fetch quota → check bucket → decrement. Quota changes propagate by updating the config store; no code deploy required.
