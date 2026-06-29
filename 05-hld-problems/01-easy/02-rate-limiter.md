---
module: 05-hld-problems
topic: Easy
status: interview-ready
tags: [05-hld-problems, system-design, easy]
---
# Design a Rate Limiter

> **Difficulty**: Easy
> **Topics**: Token Bucket, Sliding Window, Redis, Distributed Systems
> **Time**: 45 min
> **Companies**: Google, Meta, Amazon

---

## Clarifying Questions

1. "Are we rate limiting per user, per API key, or per IP — or all three with different limits?"
2. "What's the throughput we're protecting — 100 RPS per user? 1M RPS globally?"
3. "Do we need hard limits (reject at threshold) or soft limits (throttle/queue)?"
4. "Should we fail open (allow) or fail closed (deny) when the rate limiter itself is down?"
5. "Do we need to support burst capacity — e.g., 100 req/min steady but allow 200 for 10 seconds?"
6. "Single region or multi-region? If multi-region, is per-region limiting acceptable?"

---

## Back-of-Envelope

```
Assumptions:
- 10M active users, each limited to 100 req/min
- 500K API keys, each limited to 1000 req/min

Redis key per user: "rl:{user_id}" → ~20 bytes key + ~16 bytes value = ~36 bytes
10M users in RAM: 10M × 36B = ~360 MB (trivial)

Peak QPS check: 10M users × 100 req/min / 60 = ~17M req/sec at absolute peak
  → needs Redis Cluster, not a single node

Redis Cluster: 6 nodes × 100K ops/sec = 600K ops/sec realistic
  → shard rate limit keys by user_id across nodes
```

---

## APIs

```
// Middleware call (not a user-facing API)
bool allow(String userId, String endpoint)
  → true = request allowed, false = 429

// Headers returned to client
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 42
X-RateLimit-Reset: 1717200000  // unix epoch when window resets
Retry-After: 37                // seconds (only on 429)
```

---

## Architecture

```
Client
  │
  ▼
Load Balancer
  │
  ▼
API Gateway / Middleware ──────► Redis Cluster (rate limit state)
  │          │                      │
  │          │ (check allow?)       │ EVAL Lua (atomic check+decrement)
  │          └──────────────────────┘
  │
  ├── 200 → Upstream Service
  └── 429 → Return X-RateLimit headers
```

```
Redis Cluster layout:
  - Hash tag on userId: {userId} ensures all keys for a user land on same shard
  - Key: "rl:{userId}:{window_start_sec}"
  - Value: request count (integer)
  - TTL: window size (e.g., 60s)
```

---

## Data Model

```sql
-- Rate limit config (loaded at startup, cached in-process)
CREATE TABLE rate_limit_tiers (
    tier        VARCHAR(20) PRIMARY KEY,  -- 'free', 'premium', 'enterprise'
    requests    INT NOT NULL,             -- limit per window
    window_sec  INT NOT NULL,             -- window size in seconds
    burst_cap   INT                       -- optional burst multiplier
);

-- No per-request DB writes — all hot state lives in Redis
```

---

## Key Design Decisions

**1. Algorithm: Sliding Window Counter, not Fixed Window**
Fixed window has a boundary burst: 100 req at 11:59:59 + 100 req at 12:00:01 = 200 in 2 seconds. Sliding window counter approximates smooth limiting: `count = current_window + prev_window × overlap_fraction`. O(1) memory vs. sliding window log's O(requests). Chosen because it eliminates boundary bursts with negligible approximation error (~0.1%).

**2. Atomic Redis Lua script for check-and-decrement**
A read-then-write race condition: two requests both read `count=99` and both proceed. Fix: single Lua script runs atomically — check and increment happen together. No lock needed; Redis is single-threaded per slot.

```lua
-- Sliding window counter in Lua (atomic)
local key = KEYS[1]
local limit = tonumber(ARGV[1])
local now = tonumber(ARGV[2])
local count = redis.call('INCR', key)
if count == 1 then redis.call('EXPIRE', key, ARGV[3]) end
if count > limit then return 0 else return 1 end
```

**3. Fail open on Redis down**
If Redis is unreachable, allow the request but track consecutive failures via circuit breaker. After N failures, switch to local in-memory counter (per-instance, not coordinated — effective limit becomes `limit × instance_count`). Alert immediately. Rationale: a brief over-limit is better than denying all traffic due to a rate limiter outage.

**4. Token bucket for burst-capable tiers**
Premium accounts need burst support (100 req/min steady, allow 500 in a 10s burst). Sliding window can't express this. Token bucket: `tokens = min(capacity, tokens + refill_rate × elapsed)`. Stored as two Redis fields: `tokens` and `last_refill_ts`. Check is not O(1) but the trade-off is explicit burst control.

---

## Deep Dives

**Distributed rate limiting across regions**
Per-region Redis = fast (no cross-region latency) but allows over-limit: user gets 100 req/min in US-East AND 100 in EU-West = 200 globally. Options:
- Budget split: allocate 60% to primary region, 20% to each replica. No cross-region calls.
- Async sync: each region periodically syncs counts to a central store (eventually consistent — can over-allow briefly).
- For most APIs, per-region limits are acceptable and the simplest operationally.

**Multi-dimensional limits**
Rate limit by user AND by endpoint simultaneously:
- Key 1: `rl:{userId}:{window}` → per-user global limit
- Key 2: `rl:{userId}:{endpoint}:{window}` → per-endpoint limit
- Check both keys in one Redis pipeline; reject if either exceeds its limit.

---

## Failure Scenarios

| Failure | Impact | Mitigation |
|---------|--------|------------|
| Redis node down | ~1/N of users lose accurate limiting | Redis Cluster with replication; circuit breaker fails open |
| Clock skew between instances | Window boundaries diverge slightly | NTP sync; accept small approximation error |
| Lua script bug | All requests pass or all denied | Canary deploy; shadow-mode test against prod traffic |
| Hot user key | Single Redis slot overloaded | Hash tags ensure user locality; monitor per-slot ops |
| 429 storm (retry amplification) | Rejected clients retry, amplifying load | `Retry-After` header + exponential backoff in client SDK |

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

---

## Interviewer Follow-Up Questions

**On algorithm choice:**
- "Why token bucket over sliding window log for most APIs?" → Token bucket is O(1) memory per user (just store `tokens` + `last_refill_time`). Sliding window log stores every request timestamp — O(requests per window) memory per user. At 1000 req/min per user and 10M users, sliding window log needs 10B entries in memory. Token bucket scales to any number of users.
- "Fixed window has burst problems at window boundaries — explain and fix it." → At 11:59:59, a user sends 100 requests (full window). At 12:00:00, the window resets — they send another 100. In 2 seconds they've made 200 requests (2× the limit). Sliding window counter fix: `effective_count = current_window_count + previous_window_count × overlap_fraction`. Approximation but eliminates the burst.
- "Leaky bucket vs token bucket — which smooths traffic and which allows bursts?" → Leaky bucket: processes requests at a constant rate — no bursts allowed, strict smoothing (good for downstream rate protection). Token bucket: allows bursting up to the token capacity (good for bursty-but-average-limited APIs). APIs that serve users want token bucket — users expect immediate response after a pause.

**On distributed rate limiting:**
- "Your rate limiter Redis call adds 5ms to every API response. How do you reduce that?" → Lua script combines check + decrement in one round trip (saves RTT). Local token bucket per instance synced to Redis every 100ms — allows brief overages but eliminates per-request Redis call. Pipeline multiple commands. Use Redis cluster geographically close to compute.
- "What happens if two instances both check Redis simultaneously and both see `tokens = 1`?" → Race condition: both decrement, effectively allowing 2 requests for 1 token. Fix: use Redis atomic Lua script — check and decrement are a single atomic operation, no interleaving. `EVAL "if redis.call('GET', KEYS[1]) > 0 then redis.call('DECR', KEYS[1]) return 1 else return 0 end"`.
- "How do you implement a per-user rate limit AND a global rate limit simultaneously?" → Two Redis keys per request: `INCR user:{id}:{window}` and `INCR global:{window}`. If either exceeds its limit, reject. Run both checks in a single pipeline. This is two token buckets in series — request must pass both gates.

**On fairness and edge cases:**
- "A legitimate user is behind a corporate NAT with 500 employees — IP rate limiting blocks everyone. How do you handle it?" → IP rate limiting is a coarse first line of defense. Authenticated endpoints: rate limit by user ID, not IP. Unauthenticated: use IP with a higher limit and CAPTCHA as an escalation path. Offer enterprise accounts an IP allowlist.
- "How do you rate limit a webhook endpoint that receives events from a partner?" → Partner-level rate limit by `X-Partner-ID` header, not IP. Separate quota per partner. Webhook endpoints should queue incoming events (SQS) rather than processing synchronously — this absorbs burst naturally. Rate limiting at the queue consumer level controls processing rate without rejecting partner events.
- "A user gets rate limited due to a bug in your client SDK that sends duplicate requests. How do you handle this operationally?" → First: deduplicate by idempotency key at the API layer — duplicates from the same request don't count against the limit. Second: provide a `Retry-After` header so the SDK knows when to retry. Third: expose rate limit headers (`X-RateLimit-Remaining`) so clients can self-throttle before hitting the limit.

**On monitoring:**
- "What metrics do you alert on for your rate limiter?" → Rate of 429 responses per endpoint (high → limit too tight or attack in progress), Redis latency P99 (spike → rate limiter becoming a bottleneck), Redis memory usage (growing → key expiry not working), per-user 429 rate (spike on one user → possible scraper or bug).
