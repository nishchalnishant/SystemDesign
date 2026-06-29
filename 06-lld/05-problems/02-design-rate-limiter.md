---
module: 06-lld
topic: Problems
status: unread
tags: [06-lld, lld, rate-limiter, strategy, token-bucket, sliding-window]
---
# Design a Rate Limiter

> **Difficulty**: Medium  
> **Asked at**: Amazon, Google, Meta  
> **Key Patterns**: Strategy (algorithm), per-user bucket isolation, lazy refill

---

## Understanding the Problem

Design a rate limiter that decides whether an incoming request from a user is allowed or rejected, based on a configurable limit (e.g., 100 requests per minute per user), with pluggable algorithm support.

---

## Clarifying Questions

**You**: "Is this per-user, per-IP, or per API key?"  
**Interviewer**: "Per user for now. Make it easy to extend to other keys later."

**You**: "What's the primary algorithm you want?"  
**Interviewer**: "Token Bucket as the default. But make it pluggable — we might want Sliding Window or Fixed Window."

**You**: "What should happen when a request is rejected — return false or throw?"  
**Interviewer**: "Return false. The caller decides whether to retry or return 429."

**You**: "Does the refill happen on a background thread or lazily?"  
**Interviewer**: "Lazy refill is fine — refill tokens when a request comes in, based on elapsed time since the last refill."

**You**: "Is this single-node or distributed across multiple servers?"  
**Interviewer**: "Single-node for now. We can discuss distributed as a follow-up."

**You**: "Can burst traffic be allowed?"  
**Interviewer**: "Yes — that's one of the advantages of Token Bucket. A user can use accumulated tokens in a burst."

---

## Final Requirements

**In scope:**
1. `allow(user_id) -> bool` — primary API
2. Token Bucket as the default algorithm with lazy refill
3. Pluggable algorithm via Strategy pattern
4. Per-user isolation — each user gets their own bucket/window
5. Thread-safe for concurrent requests from the same user

**Out of scope:**
- Distributed rate limiting (Redis, etc.)
- HTTP layer integration (middleware wiring)
- Persistent storage of token state
- Rate limiting by endpoint or method

---

## Core Entities and Relationships

| Entity | Responsibility |
|--------|---------------|
| RateLimiter | Entry point; routes requests to per-user algorithm instance |
| RateLimitAlgorithm | Abstract strategy interface |
| TokenBucket | Implements token refill + consumption |
| SlidingWindowLog | Implements sliding window with request timestamps |
| FixedWindow | Implements fixed time-window counter |
| UserBucketStore | Stores per-user algorithm instances |

RateLimiter holds a factory for creating algorithm instances and a store mapping user_id to their instance. On each `allow(user_id)` call, it retrieves or creates the user's instance and delegates the decision.

---

## Class Design

### RateLimitAlgorithm (abstract)

```
class RateLimitAlgorithm:     # abstract
+ allow() -> bool             # called per request for this user
```

### TokenBucket

| Requirement | What TokenBucket must track |
|-------------|----------------------------|
| Current token count | tokens: float |
| When last refill happened | last_refill_time: float (epoch seconds) |
| Configuration | capacity: int, refill_rate: float (tokens/sec) |
| Thread safety | _lock: threading.Lock |

```
class TokenBucket(RateLimitAlgorithm):
- capacity: int
- refill_rate: float          # tokens added per second
- tokens: float
- last_refill_time: float
- _lock: threading.Lock

+ allow() -> bool
- _refill() -> None           # lazy refill based on elapsed time
```

### SlidingWindowLog

```
class SlidingWindowLog(RateLimitAlgorithm):
- max_requests: int
- window_seconds: float
- request_log: deque[float]   # timestamps of recent requests
- _lock: threading.Lock

+ allow() -> bool
```

### FixedWindow

```
class FixedWindow(RateLimitAlgorithm):
- max_requests: int
- window_seconds: float
- count: int
- window_start: float
- _lock: threading.Lock

+ allow() -> bool
```

### RateLimiter

```
class RateLimiter:
- algorithm_factory: Callable[[], RateLimitAlgorithm]
- user_store: dict[str, RateLimitAlgorithm]
- _store_lock: threading.Lock

+ allow(user_id: str) -> bool
- _get_or_create(user_id: str) -> RateLimitAlgorithm
```

---

## Implementation

### Core Method: TokenBucket.allow

**Core logic:**
1. Acquire lock
2. Compute elapsed time since last refill; add `elapsed * refill_rate` tokens, capped at capacity
3. If tokens >= 1, consume one token, return True
4. Otherwise return False

**Edge cases:**
- First request ever — `last_refill_time` initialized at construction time, so elapsed = 0 on first call but bucket starts full
- Very long gap since last request — tokens capped at capacity, not unbounded

```python
def allow(self) -> bool:
    with self._lock:
        self._refill()
        if self.tokens >= 1:
            self.tokens -= 1
            return True
        return False

def _refill(self):
    now = time.monotonic()
    elapsed = now - self.last_refill_time
    self.tokens = min(self.capacity, self.tokens + elapsed * self.refill_rate)
    self.last_refill_time = now
```

### Core Method: SlidingWindowLog.allow

**Core logic:**
1. Acquire lock
2. Remove timestamps from the front of the deque that are older than `now - window_seconds`
3. If length of deque < max_requests, append current timestamp and return True
4. Otherwise return False

**Edge cases:**
- Deque grows to max_requests and no old entries expire — correctly rejects
- Burst at window boundary — sliding window handles this naturally (no reset artifact)

```python
def allow(self) -> bool:
    with self._lock:
        now = time.monotonic()
        cutoff = now - self.window_seconds
        while self.request_log and self.request_log[0] < cutoff:
            self.request_log.popleft()
        if len(self.request_log) < self.max_requests:
            self.request_log.append(now)
            return True
        return False
```

### Core Method: RateLimiter.allow

**Core logic:**
1. Get or create the per-user algorithm instance (with a store-level lock for creation only)
2. Delegate to the algorithm's `allow()` method (which has its own lock)

```python
def allow(self, user_id: str) -> bool:
    algo = self._get_or_create(user_id)
    return algo.allow()

def _get_or_create(self, user_id: str) -> RateLimitAlgorithm:
    if user_id in self.user_store:
        return self.user_store[user_id]
    with self._store_lock:
        # double-check after acquiring lock
        if user_id not in self.user_store:
            self.user_store[user_id] = self.algorithm_factory()
        return self.user_store[user_id]
```

### FixedWindow.allow

```python
def allow(self) -> bool:
    with self._lock:
        now = time.monotonic()
        if now - self.window_start >= self.window_seconds:
            self.window_start = now
            self.count = 0
        if self.count < self.max_requests:
            self.count += 1
            return True
        return False
```

---

## Verification

**Scenario**: Token Bucket with capacity=5, refill_rate=1 token/sec.

1. t=0.0s: Bucket initialized, tokens=5. Request 1 → refill adds 0 (elapsed=0) → tokens=5 → consume → tokens=4 → **allowed**
2. t=0.1s: Request 2 → refill adds 0.1 → tokens=4.1 → consume → 3.1 → **allowed**
3. t=0.2s through t=0.4s: Requests 3, 4, 5 — tokens drop to ~0.5
4. t=0.5s: Request 6 → refill adds 0.1 → tokens~0.6 → **allowed** (just enough)
5. t=0.6s: Request 7 → tokens~0.1 → cannot consume 1 → **rejected**
6. t=1.6s: 1 second passes → refill adds 1.0 → tokens~1.1 → Request 8 → **allowed**

The bucket correctly allows bursts up to capacity, then throttles to refill_rate over time.

---

## Deep Dive & Extensibility

### 1. "How would you make this work across multiple servers (distributed)?"

In a distributed system, each server has its own in-memory state — they don't share token counts. A user hitting server A can send 100 req/min there, and another 100 req/min on server B.

**Solution: centralized state with Redis.**

```python
class RedisTokenBucket(RateLimitAlgorithm):
    def allow(self, user_id: str) -> bool:
        key = f"rate:{user_id}"
        pipe = self.redis.pipeline()
        now = time.time()

        # Lua script for atomic check-and-decrement
        lua_script = """
        local tokens = tonumber(redis.call('GET', KEYS[1]) or ARGV[1])
        local last = tonumber(redis.call('GET', KEYS[2]) or ARGV[2])
        local elapsed = tonumber(ARGV[2]) - last
        tokens = math.min(tonumber(ARGV[1]), tokens + elapsed * tonumber(ARGV[3]))
        if tokens >= 1 then
            tokens = tokens - 1
            redis.call('SET', KEYS[1], tokens)
            redis.call('SET', KEYS[2], ARGV[2])
            return 1
        end
        return 0
        """
        result = self.redis.eval(lua_script, 2, key+":tokens", key+":time",
                                 self.capacity, now, self.refill_rate)
        return result == 1
```

The Lua script runs atomically on Redis, so no race condition between read and write. Downside: one Redis round-trip per request adds ~1ms latency. Use Redis Cluster for high availability.

### 2. "What are the trade-offs between Sliding Window Log and Token Bucket?"

| Dimension | Token Bucket | Sliding Window Log |
|-----------|-------------|-------------------|
| Memory | O(1) per user | O(max_requests) per user — stores each timestamp |
| Burst handling | Allows burst up to capacity | Allows burst up to max_requests |
| Boundary artifact | None (continuous) | None (true sliding) |
| Precision | Approximate (lazy refill) | Exact |
| Implementation | Simple | Moderate |

**Fixed Window** has a boundary artifact: a user can make max_requests at 11:59:59, then max_requests again at 12:00:01 — effectively 2× the limit in 2 seconds. Sliding Window and Token Bucket both avoid this.

Use Token Bucket when you want burst allowance and low memory. Use Sliding Window Log when you need exact enforcement with no boundary artifact and memory is not a concern.

### 3. "How would you handle burst traffic — should bursts be limited?"

Token Bucket naturally handles this: a user who hasn't made requests for 10 seconds accumulates up to `capacity` tokens and can burst them all at once. This is intentional.

To **limit burst size** independently of sustained rate:

```python
class BurstLimitedTokenBucket(TokenBucket):
    def __init__(self, capacity, refill_rate, max_burst):
        super().__init__(capacity, refill_rate)
        self.max_burst = max_burst  # max tokens consumable in one burst window

    def allow(self):
        with self._lock:
            self._refill()
            if self.tokens >= 1 and self._burst_count < self.max_burst:
                self.tokens -= 1
                self._burst_count += 1
                return True
            return False
```

Alternatively, cap `capacity` equal to `max_burst` — simpler and achieves the same effect since tokens can never exceed capacity.

### 4. "How would you rate limit by IP, user, and API key simultaneously?"

The key insight: the identifier is just a string key. Make it composable:

```python
class CompositeRateLimiter:
    def __init__(self, limiters: list[tuple[Callable[[Request], str], RateLimiter]]):
        # Each limiter is (key_extractor, rate_limiter) pair
        self.limiters = limiters

    def allow(self, request: Request) -> bool:
        return all(
            limiter.allow(extractor(request))
            for extractor, limiter in self.limiters
        )
```

Configure three limiters: one keyed by IP (1000/min), one by user_id (100/min), one by API key (500/min). A request must pass all three. This is the same Strategy pattern with composition.

---

## Interviewer Questions by Level

**Junior**: Explain what a rate limiter does and why it's needed. Describe the Token Bucket algorithm — what does a token represent? Implement `allow()` with a simple fixed window counter.

**Mid-level**: Implement Token Bucket with lazy refill. Explain why per-user isolation matters. Use Strategy pattern to plug in different algorithms. Identify the race condition in `_get_or_create` and fix with double-checked locking.

**Senior**: Design distributed rate limiting with Redis and atomic Lua scripts. Compare Token Bucket vs. Sliding Window Log on memory/precision trade-offs. Implement `CompositeRateLimiter` for multi-dimension limiting. Analyze what happens at very high concurrency (lock contention, Redis RTT).

---

## Common Interview Questions

- Q: Why Token Bucket over Fixed Window? A: Fixed Window has a boundary artifact — a user can double their effective rate limit by sending requests at the end and start of consecutive windows. Token Bucket provides smooth rate limiting without this artifact.
- Q: How do you make the check-and-decrement atomic? A: In single-node, a per-user lock ensures the refill + consume sequence is atomic. In distributed, a Redis Lua script runs atomically on the server side.
- Q: What breaks at high concurrency with a global lock? A: All requests to the same user_id queue behind one mutex. This is fine in practice since each user's bucket operation is microseconds. The bigger risk is lock contention when millions of distinct users share the store-level creation lock — solved by double-checked locking.
- Q: Redis vs in-memory — when would you choose each? A: In-memory for single-node services where speed is critical and state loss on restart is acceptable. Redis for distributed systems where rate limit state must be shared across servers and survive restarts.
- Q: How does lazy refill work and what's its downside? A: On each `allow()` call, compute elapsed time since last refill and add proportional tokens. The downside: if the system is idle for hours, the first request will see a full bucket regardless — which is usually the desired behavior (burst for returning users).
- Q: What happens if you don't cap tokens at capacity? A: Tokens accumulate unboundedly. A user inactive for a week could make millions of requests in a burst. Always cap at `capacity`.
- Q: How do you handle user eviction from the store if there are millions of users? A: Use an LRU cache or TTL-based eviction for the user_store. If a user hasn't made a request in 1 hour, evict their entry — next request creates a fresh bucket.
