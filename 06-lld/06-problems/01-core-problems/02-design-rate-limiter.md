> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design a Rate Limiter — an essential system design and LLD problem focusing on algorithms, concurrency, and time-based state management.
>
> **Key concepts:**
> - Algorithms: Token Bucket (most common, used by AWS), Leaking Bucket (queue-based), Fixed Window, Sliding Window Log, Sliding Window Counter.
> - Core Entities: `RateLimiter` (interface), `TokenBucketLimiter` (implementation), `UserRule` (limits per user/IP).
> - The problem: efficiently tracking requests and dropping those that exceed the limit without locking up the system.
> - Concurrency: Highly concurrent. `allowRequest()` must be thread-safe. Use `AtomicInteger` or explicit locking.
> - Lazy Refill: Instead of a background thread constantly refilling buckets (expensive), refill tokens *on-demand* when the next request arrives by calculating time elapsed since the last request.
>
> **Key takeaway:** If you implement Token Bucket, use lazy refill and `ConcurrentHashMap` for storing buckets per user ID. For Sliding Window, use a thread-safe Deque or Redis Sorted Sets.

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

```java
public boolean allow() {
    synchronized (lock) {
        refill();
        if (tokens >= 1) {
            tokens -= 1;
            return true;
        }
        return false;
    }
}

private void refill() {
    long now = System.nanoTime();
    double elapsedSeconds = (now - lastRefillTime) / 1_000_000_000.0;
    tokens = Math.min(capacity, tokens + elapsedSeconds * refillRate);
    lastRefillTime = now;
}
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

```java
public boolean allow() {
    synchronized (lock) {
        double now = System.nanoTime() / 1_000_000_000.0;
        double cutoff = now - windowSeconds;
        while (!requestLog.isEmpty() && requestLog.peekFirst() < cutoff) {
            requestLog.pollFirst();
        }
        if (requestLog.size() < maxRequests) {
            requestLog.addLast(now);
            return true;
        }
        return false;
    }
}
```

### Core Method: RateLimiter.allow

**Core logic:**
1. Get or create the per-user algorithm instance (with a store-level lock for creation only)
2. Delegate to the algorithm's `allow()` method (which has its own lock)

```java
public boolean allow(String userId) {
    RateLimitAlgorithm algo = getOrCreate(userId);
    return algo.allow();
}

private RateLimitAlgorithm getOrCreate(String userId) {
    RateLimitAlgorithm existing = userStore.get(userId);
    if (existing != null) {
        return existing;
    }
    synchronized (storeLock) {
        // double-check after acquiring lock
        return userStore.computeIfAbsent(userId, id -> algorithmFactory.get());
    }
}
```

### FixedWindow.allow

```java
public boolean allow() {
    synchronized (lock) {
        double now = System.nanoTime() / 1_000_000_000.0;
        if (now - windowStart >= windowSeconds) {
            windowStart = now;
            count = 0;
        }
        if (count < maxRequests) {
            count += 1;
            return true;
        }
        return false;
    }
}
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

```java
public class RedisTokenBucket implements RateLimitAlgorithm {
    private final JedisPool redisPool;
    private final int capacity;
    private final double refillRate;

    private static final String LUA_SCRIPT =
        "local tokens = tonumber(redis.call('GET', KEYS[1]) or ARGV[1]) " +
        "local last = tonumber(redis.call('GET', KEYS[2]) or ARGV[2]) " +
        "local elapsed = tonumber(ARGV[2]) - last " +
        "tokens = math.min(tonumber(ARGV[1]), tokens + elapsed * tonumber(ARGV[3])) " +
        "if tokens >= 1 then " +
        "    tokens = tokens - 1 " +
        "    redis.call('SET', KEYS[1], tokens) " +
        "    redis.call('SET', KEYS[2], ARGV[2]) " +
        "    return 1 " +
        "end " +
        "return 0";

    public RedisTokenBucket(JedisPool redisPool, int capacity, double refillRate) {
        this.redisPool = redisPool;
        this.capacity = capacity;
        this.refillRate = refillRate;
    }

    @Override
    public boolean allow(String userId) {
        String key = "rate:" + userId;
        double now = System.currentTimeMillis() / 1000.0;

        try (Jedis redis = redisPool.getResource()) {
            Object result = redis.eval(
                LUA_SCRIPT,
                List.of(key + ":tokens", key + ":time"),
                List.of(String.valueOf(capacity), String.valueOf(now), String.valueOf(refillRate))
            );
            return ((Long) result) == 1L;
        }
    }
}
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

```java
public class BurstLimitedTokenBucket extends TokenBucket {
    private final int maxBurst;  // max tokens consumable in one burst window
    private int burstCount;

    public BurstLimitedTokenBucket(int capacity, double refillRate, int maxBurst) {
        super(capacity, refillRate);
        this.maxBurst = maxBurst;
    }

    @Override
    public boolean allow() {
        synchronized (lock) {
            refill();
            if (tokens >= 1 && burstCount < maxBurst) {
                tokens -= 1;
                burstCount += 1;
                return true;
            }
            return false;
        }
    }
}
```

Alternatively, cap `capacity` equal to `max_burst` — simpler and achieves the same effect since tokens can never exceed capacity.

### 4. "How would you rate limit by IP, user, and API key simultaneously?"

The key insight: the identifier is just a string key. Make it composable:

```java
public class CompositeRateLimiter {
    // Each entry is a (key extractor, rate limiter) pair
    private final List<Map.Entry<Function<Request, String>, RateLimiter>> limiters;

    public CompositeRateLimiter(List<Map.Entry<Function<Request, String>, RateLimiter>> limiters) {
        this.limiters = limiters;
    }

    public boolean allow(Request request) {
        for (Map.Entry<Function<Request, String>, RateLimiter> entry : limiters) {
            Function<Request, String> extractor = entry.getKey();
            RateLimiter limiter = entry.getValue();
            if (!limiter.allow(extractor.apply(request))) {
                return false;
            }
        }
        return true;
    }
}
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

---

## Concurrency Test Harness

Runnable tests verifying thread-safety invariants of the Token Bucket implementation. No external deps — stdlib only.

```java
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

// ── Minimal Token Bucket implementation (self-contained) ──

class TokenBucket {
    private final int capacity;
    private final double refillRate;   // tokens per second
    double tokens;
    private long lastRefill;
    final Object lock = new Object();

    public TokenBucket(int capacity, double refillRate) {
        this.capacity = capacity;
        this.refillRate = refillRate;
        this.tokens = capacity;
        this.lastRefill = System.nanoTime();
    }

    private void refill() {
        long now = System.nanoTime();
        double elapsedSeconds = (now - lastRefill) / 1_000_000_000.0;
        lastRefill = now;
        tokens = Math.min(capacity, tokens + elapsedSeconds * refillRate);
    }

    public boolean allow() {
        synchronized (lock) {
            refill();
            if (tokens >= 1) {
                tokens -= 1;
                return true;
            }
            return false;
        }
    }
}


class RateLimiter {
    private final int capacity;
    private final double refillRate;
    final Map<String, TokenBucket> store = new HashMap<>();
    private final Object storeLock = new Object();

    public RateLimiter(int capacity, double refillRate) {
        this.capacity = capacity;
        this.refillRate = refillRate;
    }

    TokenBucket getOrCreate(String userId) {
        if (!store.containsKey(userId)) {
            synchronized (storeLock) {
                store.computeIfAbsent(userId, id -> new TokenBucket(capacity, refillRate));  // double-checked
            }
        }
        return store.get(userId);
    }

    public boolean allow(String userId) {
        TokenBucket bucket = getOrCreate(userId);
        return bucket.allow();
    }
}


public class RateLimiterConcurrencyTest {

    // ─────────────────────────────────────────────────────────────
    // TEST 1: Exact capacity enforcement under concurrency
    // 200 threads simultaneously call allow() for the same user
    // whose bucket has capacity=100. Exactly 100 must be allowed.
    // ─────────────────────────────────────────────────────────────
    static void testExactCapacityEnforcement() throws InterruptedException {
        RateLimiter limiter = new RateLimiter(100, 0);  // no refill during test
        List<Integer> allowed = Collections.synchronizedList(new ArrayList<>());
        List<Integer> denied = Collections.synchronizedList(new ArrayList<>());

        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            Thread t = new Thread(() -> {
                boolean result = limiter.allow("user-A");
                (result ? allowed : denied).add(1);
            });
            threads.add(t);
        }
        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();

        if (allowed.size() != 100) throw new AssertionError("Expected 100 allowed, got " + allowed.size());
        if (denied.size() != 100) throw new AssertionError("Expected 100 denied, got " + denied.size());
        // No race: tokens never go negative
        if (limiter.store.get("user-A").tokens < 0) throw new AssertionError("Tokens went negative");
        System.out.println("PASS: testExactCapacityEnforcement");
    }


    // ─────────────────────────────────────────────────────────────
    // TEST 2: Per-user isolation
    // 10 users each fire 200 concurrent requests against a bucket
    // with capacity=100. Each user must see exactly 100 allowed,
    // and no user's count bleeds into another's.
    // ─────────────────────────────────────────────────────────────
    static void testPerUserIsolation() throws InterruptedException {
        RateLimiter limiter = new RateLimiter(100, 0);
        Map<String, List<Integer>> perUserAllowed = new ConcurrentHashMap<>();
        for (int i = 0; i < 10; i++) {
            perUserAllowed.put("user-" + i, Collections.synchronizedList(new ArrayList<>()));
        }

        List<Thread> threads = new ArrayList<>();
        for (String uid : perUserAllowed.keySet()) {
            for (int i = 0; i < 200; i++) {
                Thread t = new Thread(() -> {
                    boolean result = limiter.allow(uid);
                    if (result) {
                        perUserAllowed.get(uid).add(1);
                    }
                });
                threads.add(t);
            }
        }

        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();

        for (Map.Entry<String, List<Integer>> entry : perUserAllowed.entrySet()) {
            if (entry.getValue().size() != 100) {
                throw new AssertionError(entry.getKey() + ": expected 100 allowed, got " + entry.getValue().size());
            }
        }

        System.out.println("PASS: testPerUserIsolation");
    }


    // ─────────────────────────────────────────────────────────────
    // TEST 3: Refill replenishes tokens correctly
    // Start with capacity=10. Drain fully. Wait for refill.
    // After refill, exactly 10 more requests must succeed.
    // ─────────────────────────────────────────────────────────────
    static void testRefillReplenishes() throws InterruptedException {
        RateLimiter limiter = new RateLimiter(10, 10);  // 10 tokens/sec

        // Drain all tokens
        boolean allDrained = true;
        for (int i = 0; i < 10; i++) {
            allDrained &= limiter.allow("user-B");
        }
        if (!allDrained) throw new AssertionError("Could not drain full capacity");

        // Immediately after drain, next request must fail
        if (limiter.allow("user-B")) throw new AssertionError("Expected denial after drain");

        // Wait 1 second for full refill
        Thread.sleep(1100);

        // Now should allow up to 10 again
        int refilledCount = 0;
        for (int i = 0; i < 10; i++) {
            if (limiter.allow("user-B")) refilledCount++;
        }
        if (refilledCount != 10) throw new AssertionError("Expected 10 allowed after refill, got " + refilledCount);

        // 11th must fail (bucket refilled to capacity, not beyond)
        if (limiter.allow("user-B")) throw new AssertionError("Expected denial after second drain");
        System.out.println("PASS: testRefillReplenishes");
    }


    // ─────────────────────────────────────────────────────────────
    // TEST 4: No data race on bucket creation (getOrCreate)
    // 500 threads simultaneously ask for the same new user_id.
    // Only one TokenBucket object must be created (not 500 copies).
    // ─────────────────────────────────────────────────────────────
    static void testSingleBucketPerUser() throws InterruptedException {
        RateLimiter limiter = new RateLimiter(50, 0);
        Set<Integer> bucketIds = Collections.synchronizedSet(new HashSet<>());

        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            Thread t = new Thread(() -> {
                TokenBucket bucket = limiter.getOrCreate("new-user");
                bucketIds.add(System.identityHashCode(bucket));
            });
            threads.add(t);
        }
        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();

        if (bucketIds.size() != 1) {
            throw new AssertionError("Expected 1 bucket, got " + bucketIds.size() + " (race in getOrCreate)");
        }
        System.out.println("PASS: testSingleBucketPerUser");
    }


    public static void main(String[] args) throws InterruptedException {
        testExactCapacityEnforcement();
        testPerUserIsolation();
        testRefillReplenishes();
        testSingleBucketPerUser();
        System.out.println("All concurrency tests passed.");
    }
}
```

**What each test verifies:**
- `test_exact_capacity_enforcement`: The per-bucket lock prevents two threads from both reading `tokens >= 1` and both decrementing. Without the lock, more than 100 requests can be admitted.
- `test_per_user_isolation`: Each user's bucket is independent; a burst by one user must not consume tokens from another's bucket.
- `test_refill_replenishes`: Lazy refill correctly adds tokens proportional to elapsed time; capacity cap prevents overflow.
- `test_single_bucket_per_user`: Double-checked locking in `_get_or_create` prevents creating duplicate `TokenBucket` instances when hundreds of threads race to create the first bucket for a new user.

---

## Related

**Patterns applied here**

- [Strategy Pattern](../../03-design-patterns/03-behavioral/strategy-pattern.md)

**SOLID focus**: [Open/Closed](../../02-solid-principles/02-open-closed.md) · [Dependency Inversion](../../02-solid-principles/05-dependency-inversion.md)

**Concurrency**: [Concurrency Patterns](../../04-concurrency/concurrency-patterns.md)

**Practice next**

- [Design Notification System](../02-frequent-problems/16-design-notification-system.md)
- [Design a High-Contention Counter](../04-advanced-niche/36-design-high-contention-counter.md)

The counter problem is this one stripped to its contention core.

**Frameworks**: [LLD Template](../../../07-interview-templates/01-frameworks/02-lld-template.md) · [UML Diagrams](../../01-oop-fundamentals/uml-diagrams.md) · [OOP Four Pillars](../../01-oop-fundamentals/four-pillars.md)
