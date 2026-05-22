# Rate Limiting

> **Limit the number of requests a user, API key, or IP can make in a time window to protect availability and fairness.**

---

## File Mindmap

```
Rate Limiting
├── Why It Exists
│   ├── Problem → malicious/buggy client sends 50K req/s; service handles 10K total; other users starved
│   └── Forces → shared infrastructure; one client consuming all capacity = denial of service for others
├── Algorithms
│   ├── Token Bucket
│   │   ├── Bucket holds max N tokens; refilled at rate R tokens/sec
│   │   ├── Request consumes 1 token; no token → reject (429)
│   │   ├── Allows burst up to bucket size N
│   │   └── Use case → API rate limits; most common production algorithm
│   ├── Leaky Bucket
│   │   ├── Requests enter queue; processed at fixed rate (leak rate)
│   │   ├── Queue full → reject
│   │   ├── Smooths traffic spikes; no burst allowed
│   │   └── Use case → traffic shaping; QoS; network egress control
│   ├── Fixed Window Counter
│   │   ├── Count requests in fixed time window (e.g. per minute)
│   │   ├── Counter resets at window boundary
│   │   ├── Simple to implement; O(1) Redis INCR
│   │   └── Cons → boundary burst: 2× limit allowed straddling window edge
│   ├── Sliding Window Log
│   │   ├── Store timestamp of each request; on check, evict entries older than window
│   │   ├── Count remaining = exact rate
│   │   └── Cons → O(n) memory per user (stores all timestamps)
│   └── Sliding Window Counter (Approximate)
│       ├── current_window_count + (prev_window_count × overlap_fraction)
│       ├── O(1) memory; approximate but good enough
│       └── Use case → Redis ZSET implementation; production recommendation
├── Enforcement Dimensions
│   ├── Per user / user ID
│   ├── Per API key
│   ├── Per IP address
│   └── Per endpoint (different limits for /search vs /checkout)
├── Centralized vs Distributed Enforcement
│   ├── Centralized → single Redis; exact counts; bottleneck at very high scale
│   └── Distributed → each instance has local counter; sync periodically; approximate but scalable
├── Redis Implementation (Sliding Window via ZSET)
│   ├── Key = "rate:{user_id}:{window}"
│   ├── ZADD with timestamp as score; ZREMRANGEBYSCORE to evict old; ZCARD to count
│   └── Lua script for atomicity (no race between check and increment)
├── Tiered Limits
│   ├── Free tier → 100 req/min
│   ├── Pro tier → 1000 req/min
│   └── Enterprise → custom; bypass or very high limit
├── Failure Modes
│   ├── Fail open → if rate limit store (Redis) is down, allow all requests
│   │   └── Use when → availability > strict enforcement (most APIs)
│   └── Fail closed → if store down, reject all requests
│       └── Use when → security-critical (auth endpoints, payment)
├── Headers to Return
│   ├── X-RateLimit-Limit → max requests allowed
│   ├── X-RateLimit-Remaining → remaining in current window
│   ├── X-RateLimit-Reset → UTC epoch when window resets
│   └── Retry-After → seconds until client may retry (on 429)
├── Trade-offs
│   ├── Pros → prevents abuse; fairness; protects downstream services
│   └── Cons → complexity; distributed counting has race conditions; legitimate users may be rejected
└── Interview Angles
    ├── "Token bucket vs sliding window?" → TB allows burst; SW is exact; choose by burst tolerance
    ├── "How do you implement rate limiting across N instances?" → centralized Redis with Lua atomic ops
    ├── "Fail open vs fail closed?" → fail open for availability; fail closed for security endpoints
    └── Follow-up: "How do you handle rate limit for distributed clients (same user, multiple IPs)?" → rate limit by user ID not IP
```

---

## Why Rate Limiting Exists

**Question**: A single malicious (or buggy) client sends 50,000 requests/sec to your API. Your service handles 10,000 req/sec total. What prevents this one client from taking down every other user?

**Physical constraint**: Your API server has a fixed thread pool and connection limit. 50,000 concurrent TCP connections will exhaust file descriptors (default OS limit: 65,535). Even if each request is computationally cheap, the connection overhead alone starves legitimate traffic. At 100 bytes per request header, 50,000 req/sec is 5 MB/sec of header parsing before a single byte of your business logic runs.

**Minimal solution**: Track request count per client IP in memory. If count exceeds threshold in the window, return 429. Works until: you have multiple app servers (each has its own in-process counter — a client spreads requests across N servers and gets N× the limit), or the server restarts (all counters reset to zero).

**Production generalization**: Centralized counter in Redis with atomic INCR + TTL. All app servers share one counter. The algorithm choice (token bucket, sliding window, fixed window) determines how you handle bursts vs steady-state traffic and how much state you maintain per user.

---

## The Nightclub Bouncer Analogy

A nightclub bouncer stands at the door with a clicker counter. The algorithms map directly:

- **Fixed window**: "We let in 1,000 people total tonight. Counter hits 1,000, door closes." Simple, but a rush at 11:59pm and another at 12:01am lets in 2,000 people in 2 minutes.
- **Sliding window**: "I track only the last 60 seconds. If 1,000 people entered in the last 60 seconds, you wait." No boundary spike.
- **Token bucket**: Everyone gets a bracelet with 10 tokens. Each entry costs 1 token. Tokens refill at 1 per minute up to a max of 10. You can burst in 10 friends right now, but then you must wait for refills. Think of it as a gaming stamina system: 10 stamina, each action costs 1, refills 1/minute.
- **Leaky bucket**: A pipe with a fixed hole at the bottom. Pour water in as fast as you like — it only drips out at a constant rate. Smooths bursts but doesn't let them through.

**Why it exists**: Without limits, a few clients can exhaust capacity or cause cascading failure. Rate limiting keeps the system stable and fair.

---

## 1. Concept Overview

**Rate limiting** enforces a maximum request rate (e.g. 100 req/min per user). It prevents abuse, protects backends from overload, and enforces quotas and cost control.

---

## 2. Core Principles

### Algorithms

| Algorithm | How it works | Pros | Cons |
|-----------|--------------|------|------|
| **Token bucket** | Refill tokens at fixed rate; each request consumes one (or N); reject when 0 | Allows bursts up to bucket size | Slightly more state |
| **Leaky bucket** | Requests enter a "bucket"; processed at fixed rate; reject when full | Smooths traffic | Bursts limited by capacity |
| **Fixed window** | Count requests in current window (e.g. minute); reset at window boundary | Simple | Boundary spike (e.g. 100 at 0:59 + 100 at 1:00) |
| **Sliding window** | Count in last N seconds (or sliding window); more accurate | No boundary spike | More state or approximation |
| **Sliding log** | Store timestamp per request; count in window | Accurate | Memory and cost at high QPS |

### Token Bucket in Detail

This is the most common algorithm in practice (used by AWS API Gateway, Stripe, etc.).

```
  Capacity: 10 tokens  (max burst = 10 requests instantly)
  Refill:    1 token/second

  t=0:  tokens=10, send 10 requests → tokens=0, all pass
  t=1:  tokens=1,  send 1 request  → tokens=0, passes
  t=1:  tokens=0,  send 1 request  → REJECTED (429)
  t=5:  tokens=5,  send 3 requests → tokens=2, all pass
```

### Leaky Bucket in Detail

Regardless of how fast requests arrive, they're processed at a fixed output rate. The bucket fills during a burst. If it overflows, requests are dropped. Useful when you need perfectly smooth output — e.g. sending SMS messages to a carrier that accepts exactly 10/second.

### Sliding Window vs Fixed Window — Mathematical Analysis

**Fixed Window boundary spike — exact numbers**:

```
Limit: 100 requests per 60-second window
Windows: [0:00–0:59], [1:00–1:59], [2:00–2:59], ...

Attack:
  0:59.000 → send 100 requests (fills window 1 entirely)   ✓ allowed
  1:00.001 → send 100 requests (window 2 just started)      ✓ allowed
  → 200 requests in 0.002 seconds: 2× the rate limit

Why this matters: at 1000 req/min limit, an attacker can send 2000 req in
2ms at every window boundary. Your downstream DB sees 2000 QPS for 2ms.
```

**Token Bucket — rate math**:

```
Config: capacity = 100 tokens, refill_rate = 10 tokens/second

Initial state: 100 tokens (full bucket)
t=0:    send 100 requests → 0 tokens remain
t=1s:   10 tokens refilled → can send 10 requests
t=5s:   50 tokens → can send 50 requests
t=10s:  100 tokens (full again)

Burst formula: max burst = bucket_capacity
Sustained rate formula: requests/second ≤ refill_rate
Effective limit: 100 immediate burst + 10/second sustained

For capacity = C, refill_rate = r:
  max_burst = C
  sustained_rate = r
  over_t_seconds = C + r×t (capped at C)
```

**Sliding Window Log — exact but expensive**:

```
Store timestamps of all requests in last N seconds (Redis sorted set).
On each request:
  1. ZREMRANGEBYSCORE window:{key} 0 (now - 60s)  ← remove old entries
  2. ZCARD window:{key}                             ← count recent requests
  3. If count < limit: ZADD window:{key} now now → allow
  4. Else: reject

Memory cost: O(requests_per_window_per_key)
At 1000 req/min: store 1000 timestamps per user → ~16KB per user
At 1M users: 16GB just for rate limit state

Solution for scale: Sliding Window Counter approximation
```

**Sliding Window Counter — approximate but O(1) space**:

```
Two fixed-window counters: current window and previous window.
Approximate count = prev_count × (overlap_fraction) + curr_count

Example at 0:45 (45 seconds into current window):
  prev_window count = 80 (0:00–0:59)
  curr_window count = 30 (1:00–1:59)
  overlap = 15 seconds out of 60 = 0.25 fraction in previous window
  
  estimated_count = 80 × 0.25 + 30 = 50

This approximation has ≤ 1/window_size error rate (< 0.4% error for 60s window).
Used by: Redis-Cell module, Kong rate limiting plugin.
```

**Algorithm Comparison**:

| Algorithm | Boundary Spike | Burst Control | Space | Accuracy |
|---|---|---|---|---|
| Fixed Window | ❌ 2× spike | ❌ No | O(1) | Exact per window |
| Token Bucket | ✅ None | ✅ Configurable | O(1) | Exact |
| Leaky Bucket | ✅ None | ✅ No burst allowed | O(queue_size) | Exact |
| Sliding Window Log | ✅ None | ✅ Configurable | O(N) | Exact |
| Sliding Window Counter | ✅ Mostly | ✅ Mostly | O(1) | ~99.6% |

**Recommendation**:
- User-facing APIs: Token Bucket (allows bursts, easy to tune)
- Financial/billing: Sliding Window Log (exact, no boundary spikes)
- High-scale (millions of keys): Sliding Window Counter (O(1) space, ~exact)
- Smoothing (outbound SMS to carrier): Leaky Bucket (constant output rate)

### Where to Enforce

- **API gateway**: Central place; one policy for all services.
- **Per service**: Fine-grained but duplicated logic.
- **At client**: Optional "client-side" limit to avoid 429s; server still enforces.

### Architecture (centralized)

```
  Clients ─────▶ API Gateway (rate limit check) ─────▶ Backend
                        │
                        ▼
                 Redis / in-memory
                 (counters per key, TTL per window)
```

---

## 3. Real-World Usage

- **API Gateway**: Kong, AWS API Gateway (usage plans), Apigee.
- **Redis**: Store counters with TTL; increment and check in Lua for atomicity.
- **Nginx**: `limit_req_zone` (leaky bucket); `limit_conn_zone` (connection limit).

---

## 4. Trade-offs

| Choice | Pros | Cons |
|--------|------|------|
| **Token bucket** | Allows bursts | More logic than fixed window |
| **Fixed window** | Simple | Boundary double-count |
| **Sliding window** | Fair, no boundary spike | More state |
| **Centralized (gateway)** | Single policy | Gateway must have state or Redis |
| **Distributed** | Works across many gateway nodes | Need shared store (Redis) and consistent key (user/id) |

**When to use**: Any public or partner API; internal APIs that need fairness or cost control.  
**When not**: Fully trusted internal services with no abuse risk (optional).

---

## 5. Failure Scenarios

| Scenario | Mitigation |
|----------|------------|
| Redis (counter store) down | Fail open (allow) or fail closed (reject); prefer fail open with alert |
| Clock skew (distributed) | Use server time for windows; or logical time from Redis |
| Bypass (spoofed key) | Rate limit by authenticated user/id, not just IP; validate auth first |
| Thundering herd after limit lifted | Sliding window or gradual refill to avoid spike at boundary |

---

## 6. Performance Considerations

- **Latency**: In-memory or single Redis call; keep under 1 ms.
- **Throughput**: Counters must handle high QPS; Redis or local cache with periodic sync.
- **Storage**: Key = user/id + window; TTL to avoid unbounded growth.

---

## 7. Implementation Patterns

### Sliding Window in Redis with Lua (Java)

```java
@Component
public class RateLimiter {

    private final StringRedisTemplate redis;

    // Sliding window: ZSET with timestamps as scores
    // Key: "rate_limit:{userId}", Score: timestamp, Member: unique request ID
    public boolean isAllowed(String userId, int maxRequests, long windowSeconds) {
        long now = System.currentTimeMillis();
        long windowStart = now - (windowSeconds * 1000);
        String key = "rate_limit:" + userId;

        String script =
            "redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[1])  " + // remove old
            "local count = redis.call('ZCARD', KEYS[1])               " +
            "if count < tonumber(ARGV[3]) then                        " +
            "  redis.call('ZADD', KEYS[1], ARGV[2], ARGV[4])          " + // add current
            "  redis.call('EXPIRE', KEYS[1], ARGV[5])                 " +
            "  return 1                                                " + // allowed
            "else return 0 end";                                           // rejected

        Long result = redis.execute(
            new DefaultRedisScript<>(script, Long.class),
            List.of(key),
            String.valueOf(windowStart),          // ARGV[1]: window start
            String.valueOf(now),                  // ARGV[2]: current timestamp (score)
            String.valueOf(maxRequests),          // ARGV[3]: limit
            UUID.randomUUID().toString(),         // ARGV[4]: unique member
            String.valueOf(windowSeconds)         // ARGV[5]: key TTL
        );
        return Long.valueOf(1).equals(result);
    }
}

// In gateway filter:
if (!rateLimiter.isAllowed(userId, 100, 60)) {
    response.setStatus(429);
    response.setHeader("Retry-After", "60");
    return;
}
```

### Tiered Limits

```java
// Free tier: 100 req/min, Paid tier: 10,000 req/min
// The bouncer has different wristband colors
int limit = userService.isPaid(userId) ? 10_000 : 100;
boolean allowed = rateLimiter.isAllowed(userId, limit, 60);
```

- **Per user**: Key = `user_id` or `api_key`; fair per customer.
- **Per IP**: Key = `ip`; simple but shared IPs (NAT) affect many users.
- **Hybrid**: Stricter per IP for unauthenticated; per user for authenticated.
- **Tiers**: Different limits for free vs paid (e.g. 100 vs 10,000 req/min).

---

## Quick Revision

- **Token bucket**: Burst-friendly. **Fixed window**: Simple; boundary spike. **Sliding window**: Fair.
- **Where**: API gateway + Redis (or in-memory for single node).
- **Key**: Prefer user/id over IP when authenticated.
- **Failure**: Fail open vs fail closed; Redis HA so counters are available.
- **Interview**: "We rate limit at the API gateway using a sliding window in Redis keyed by user ID so paid and free tiers get different limits; if Redis is down we fail open and alert so we don't block all traffic."

---

## See Also

- **HLD problem applying this**: [05-hld-problems/01-easy/rate-limiter.md](../05-hld-problems/01-easy/rate-limiter.md) — full system design with capacity estimation, architecture diagram, and failure scenarios
- **LLD problem applying this**: [06-lld/05-problems/2-design-rate-limiter.md](../06-lld/05-problems/2-design-rate-limiter.md) — class design, Token Bucket implementation, thread-safety
- **Where it appears in other problems**: URL Shortener (abuse prevention), Payment System (idempotency guard), Notification Service (per-user send limits)
- **API Gateway integration**: [02-building-blocks/api-gateway.md](api-gateway.md)
