# Rate Limiting

> **Limit the number of requests a user, API key, or IP can make in a time window to protect availability and fairness.**

---

## 1. Concept Overview

**Rate limiting** enforces a maximum request rate (e.g. 100 req/min per user). It prevents abuse, protects backends from overload, and helps enforce quotas and cost control.

**Why it exists**: Without limits, a few clients can exhaust capacity or cause cascading failure; rate limiting keeps the system stable and fair.

---

## 2. Core Principles

### Algorithms

| Algorithm | How it works | Pros | Cons |
|-----------|--------------|------|------|
| **Token bucket** | Refill tokens at fixed rate; each request consumes one (or N); reject when 0 | Allows bursts up to bucket size | Slightly more state |
| **Leaky bucket** | Requests enter a “bucket”; processed at fixed rate; reject when full | Smooths traffic | Bursts limited by capacity |
| **Fixed window** | Count requests in current window (e.g. minute); reset at window boundary | Simple | Boundary spike (e.g. 100 at 0:59 + 100 at 1:00) |
| **Sliding window** | Count in last N seconds (or sliding window); more accurate | No boundary spike | More state or approximation |
| **Sliding log** | Store timestamp per request; count in window | Accurate | Memory and cost at high QPS |

### Where to enforce

- **API gateway**: Central place; one policy for all services.
- **Per service**: Fine-grained but duplicated logic.
- **At client**: Optional “client-side” limit to avoid 429s; server still enforces.

### Architecture (centralized)

```
  Clients ─────▶ API Gateway (rate limit check) ─────▶ Backend
                        │
                        ▼
                 Redis / in-memory
                 (counters per key)
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
| Bypass (spoofed key)** | Rate limit by authenticated user/id, not just IP; validate auth first |
| Thundering herd after limit lifted | Sliding window or gradual refill to avoid spike at boundary |

---

## 6. Performance Considerations

- **Latency**: In-memory or single Redis call; keep under 1 ms.
- **Throughput**: Counters must handle high QPS; Redis or local cache with periodic sync.
- **Storage**: Key = user/id + window; TTL to avoid unbounded growth.

---

## 7. Implementation Patterns

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
- **Interview**: “We rate limit at the API gateway using a sliding window in Redis keyed by user ID so paid and free tiers get different limits; if Redis is down we fail open and alert so we don’t block all traffic.”
