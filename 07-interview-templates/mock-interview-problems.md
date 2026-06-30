> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** A curated set of 15 practice problems (both HLD and LLD) with constraints, scope guardrails, and model answer skeletons.
>
> **Key concepts:**
> - Realistic Constraints: Instead of just "Design Twitter", it specifies "100M DAU, heavy read bias, celebrity fan-out problem is the primary focus."
> - Time Boxing: Each problem is designed to be practiced with a strict 45-minute timer to simulate real interview pressure.
> - Model Skeletons: Provides the expected architectural diagram or class structure so you can self-evaluate your mock performance.
>
> **Key takeaway:** Passive reading won't pass an interview. You must use these problems to practice talking out loud, drawing on a whiteboard (or digital equivalent), and pacing yourself to finish in 45 minutes.

---
module: 07-interview-templates
topic: Mock Interview Problem Set
status: unread
tags: [07-interview-templates, interview, mock, practice-problems]
---
# Mock Interview Problem Set

> 15 problems with constraints, scope guardrails, and model answer skeletons. Use with the self-assessment rubric. Time yourself to 45 minutes per problem.

---

## How to Use

1. Pick a problem. Set a 45-minute timer.
2. Read only the **Problem Statement** and **Constraints** — do not read the model answer.
3. Complete your design.
4. Score yourself with the rubric in `self-assessment-rubric.md`.
5. Then read the **Key Decisions** and **Common Mistakes** sections to calibrate.

---

## HLD Problems

---

### Problem 1 — Design a URL Shortener (Easy warm-up)

**Problem Statement**
Design a service like bit.ly. Users submit a long URL and receive a short URL. Visiting the short URL redirects to the original.

**Constraints to clarify in your session**
- 100M URLs created per day; 10B redirects per day (100:1 read/write)
- Short URL must be ≤ 8 characters, globally unique
- Redirects must complete in < 10ms P99
- Analytics: track click count per short URL (near-real-time)
- Custom aliases allowed ("bit.ly/my-brand")

**Scope for 45 min**
Focus on: ID generation, redirect path, storage schema. Mention but don't deep-dive: analytics pipeline, custom alias collision handling.

**Key Decisions**

| Decision | Recommendation | Why |
|---|---|---|
| ID generation | Base62 encoding of a distributed ID (Snowflake) | UUID is 36 chars; hash collision risk; Snowflake gives sortable, short, unique IDs |
| Storage | Key-value store (DynamoDB or Redis) for `short_id → long_url` | Pure key lookup, no relational queries needed |
| Redirect | HTTP 301 (permanent, cached by browser) vs 302 (temporary, trackable) | 302 for analytics; 301 to reduce server load if analytics not needed |
| Analytics | Async: log click events to Kafka → consumer aggregates into a counter store | Sync counter update on every redirect adds latency to the hot path |
| Cache | Redis cache for hot short URLs in front of DB | 10B redirects/day = 115K RPS; most will be for a small set of popular URLs |

**Common Mistakes**
- Using MD5/SHA hash of the long URL as the short ID — collision probability and non-incremental
- Forgetting that `301` caches at the browser — analytics will undercount if you use 301
- No cache — 115K RPS hits the DB directly
- Custom aliases: forgetting that `reserved` words (e.g., "admin", "api") must be blocklisted

---

### Problem 2 — Design a Notification System (Medium)

**Problem Statement**
Design a system that sends notifications (push, SMS, email) to users. Notifications are triggered by events in other services (e.g., "new follower", "order shipped").

**Constraints to clarify in your session**
- 10M notifications/day across 3 channels: push (60%), email (30%), SMS (10%)
- Notifications must be delivered within 5 seconds of the triggering event (push/SMS); email within 60 seconds
- Each user has per-channel preferences (opt-in/out)
- Notifications must not be duplicated (no double-send)
- Triggering services are event-driven (they publish events, not direct calls)

**Scope for 45 min**
Focus on: event ingestion, routing logic, deduplication, delivery to each channel provider. Mention but don't deep-dive: preference management UI, delivery analytics.

**Key Decisions**

| Decision | Recommendation | Why |
|---|---|---|
| Event ingestion | Kafka topic per event type | Decouples triggering services; allows replay; fan-out to multiple consumers |
| Routing | Notification Router reads user preferences from DB/cache, routes to channel-specific queues | Preferences change rarely → cache with 60s TTL; hot read path |
| Deduplication | Idempotency key = `(user_id, event_id, channel)`; stored in Redis with TTL 24h | Same event delivered twice to same user = duplicate; idempotency key prevents double-send |
| Delivery workers | One worker pool per channel (push, email, SMS) | Different SLAs and provider APIs; isolate failures via bulkhead |
| Retry | Exponential backoff, DLQ after 3 retries | Provider outages are common; DLQ for manual inspection of persistent failures |

**Common Mistakes**
- Synchronous call chain: `Order Service → Notification Service → Push Provider` — any provider slowness blocks order completion
- No preference check — sending to opted-out users
- Global dedup key instead of per-channel — user opted into email but not push; same event should deliver email but not push
- No rate limiting per user — notification spam if many events fire at once; add per-user rate limit (max 5 notifications/hour)

---

### Problem 3 — Design Twitter/X Feed (Hard)

**Problem Statement**
Design the core feed for a social platform. Users post tweets; followers see those tweets in their home feed in reverse-chronological order.

**Constraints to clarify in your session**
- 300M daily active users; 500M tweets/day
- Median followers: 200; power users (celebrities): up to 100M followers
- Feed must load in < 200ms P99
- Feed is reverse-chronological (no ranking for this version)
- Users can follow/unfollow; feed must reflect changes within 30 seconds

**Scope for 45 min**
Focus on: write path (tweet creation + fan-out), read path (feed fetch), celebrity problem. Mention but don't deep-dive: search indexing, media storage.

**Key Decisions**

| Decision | Recommendation | Why |
|---|---|---|
| Fan-out strategy | Hybrid: push for regular users (< 1M followers), pull for celebrities | Push to 100M followers synchronously = infeasible; pull at read time for celebrities |
| Fan-out store | Redis sorted set per user: `feed:{user_id}` → `[(tweet_id, timestamp)]` | O(log N) insert; O(K) read for top-K tweets; native sorted order |
| Celebrity threshold | 1M followers → pull model | Threshold is tunable; start at 1M, adjust based on latency profile |
| Read merge | Feed service fetches pre-computed feed from Redis + fetches celebrity tweets from tweet store, merges | Merge happens at read time; bounded by number of celebrities the user follows |
| Timeline cap | Keep last 800 tweets per user in Redis | Beyond 800, users don't scroll; trim on write |

**Common Mistakes**
- Pure push model — fan-out to celebrity followers synchronously kills write latency
- Pure pull model — fetching all followers' tweets at read time is O(followers × recency_window)
- Forgetting the merge step — hybrid model requires merging pre-computed feed with live celebrity tweets
- No cap on feed size — unbounded Redis sorted set grows forever

---

### Problem 4 — Design a Ride-Sharing Service (Hard)

**Problem Statement**
Design the core matching system for a ride-sharing app: riders request rides, drivers accept, the system matches them.

**Constraints to clarify in your session**
- 10M rides/day across 50 cities; peak 500 requests/second
- Match must complete in < 5 seconds (time from request to driver assignment)
- Location updates: drivers send GPS every 5 seconds
- Matching: nearest available driver within 5km radius, prefer lowest ETA
- Surge pricing: if demand > supply in an area, increase price multiplier

**Scope for 45 min**
Focus on: driver location store, matching algorithm, ride state machine. Mention but don't deep-dive: payment, ratings, surge pricing formula.

**Key Decisions**

| Decision | Recommendation | Why |
|---|---|---|
| Location store | Redis Geo (geospatial index) per city: `GEOADD drivers:city_id lon lat driver_id` | O(log N) radius search via `GEORADIUS`; in-memory for low latency |
| Location update | Driver → location service → Redis; 5-second TTL per driver (stale if not updated) | TTL auto-removes offline drivers; no explicit delete needed |
| Matching | Pull candidates via `GEORADIUS`, sort by ETA (precomputed or estimated), offer to top-3 sequentially | Sequential offers prevent double-booking; top-3 reduces miss rate |
| Ride state machine | `REQUESTED → MATCHED → ACCEPTED → IN_PROGRESS → COMPLETED` | State stored in DB; transitions validated server-side |
| Offer expiry | Driver has 10 seconds to accept; if not accepted, move to next candidate | Prevents rides hanging on unresponsive drivers |

**Common Mistakes**
- Using a relational DB for location queries — SQL geo queries at 500 RPS are slow
- Offering to all N candidates simultaneously — double-booking risk
- No TTL on driver location — stale locations cause failed matches
- No state machine — allowing invalid transitions (e.g., COMPLETED → IN_PROGRESS)

---

### Problem 5 — Design a Distributed Cache (Medium)

**Problem Statement**
Design a distributed in-memory cache (like Redis Cluster or Memcached). Clients set/get key-value pairs. The cache should scale horizontally and survive single-node failures.

**Constraints to clarify in your session**
- 1M get/set operations per second total
- 99.9% availability; data can be lost on node failure (cache, not persistent store)
- Horizontal scaling: add/remove nodes with minimal disruption
- Maximum value size: 1MB; key length: 256 bytes

**Scope for 45 min**
Focus on: key distribution, node failure handling, client routing. Mention but don't deep-dive: eviction policy internals, persistence.

**Key Decisions**

| Decision | Recommendation | Why |
|---|---|---|
| Key distribution | Consistent hashing with virtual nodes (150 vnodes per node) | Node add/remove remaps only K/N keys; virtual nodes reduce hotspots |
| Client routing | Smart client (consistent hash ring maintained client-side) vs proxy | Smart client: lower latency, no single point of failure; proxy: simpler clients |
| Replication | Async replication to one replica per node | Cache, not DB — losing data on failure is acceptable; replica adds read scale |
| Node failure | Consistent hash ring routes to next node; replica promoted | Data loss for keys on failed node is acceptable; ring auto-heals |
| Rebalancing | Add node: take K/N keys from each existing node; remove: redistribute to remaining | Minimal disruption; background migration |

**Common Mistakes**
- Modulo hashing — adding a node remaps all keys
- No virtual nodes — uneven distribution with small node count
- Synchronous replication — adds write latency; cache doesn't need it
- No connection pooling in clients — 1M ops/sec with new TCP per request = connection exhaustion

---

### Problem 6 — Design a Rate Limiter (Medium)

**Problem Statement**
Design a rate limiting service that can be used by multiple APIs. Each API has different limits (e.g., 1000 req/min per user, 10000 req/min per IP).

**Constraints to clarify in your session**
- Multiple APIs, each with its own limit and window (per-user, per-IP, per-API-key)
- 100K requests/second total across all APIs
- Limits must be enforced across a cluster of API gateway nodes (not per-node)
- Users who exceed limit get 429 with `Retry-After` header
- Limit config changes (e.g., upgrade a user's tier) must take effect within 60 seconds

**Scope for 45 min**
Focus on: algorithm choice, distributed enforcement, config propagation. Mention but don't deep-dive: billing integration, admin UI.

**Key Decisions**

| Decision | Recommendation | Why |
|---|---|---|
| Algorithm | Sliding window counter in Redis | Fixed window has edge bursts; token bucket harder to implement atomically in Redis; sliding window via sorted set is simple and accurate |
| Redis sliding window | `ZADD key now now; ZREMRANGEBYSCORE key 0 (now-window); ZCARD key` in a Lua script | Atomic; O(log N) per request; TTL on key for auto-cleanup |
| Sharding | Shard by `hash(user_id) % N` Redis nodes | Avoid hotspot on single Redis instance at 100K RPS |
| Config propagation | Store limits in DB; cache in each gateway with 60s TTL | 60s eventual propagation meets the requirement; avoids DB hit per request |
| Failure mode | If Redis is unavailable: fail open (allow request) | Fail closed (deny all) is worse UX; log and alert for Redis outage |

**Common Mistakes**
- Per-gateway local counter — each node allows the full limit; effective limit = N × limit
- No Lua script atomicity — race between `ZCARD` check and `ZADD` allows bursts over limit
- Fixed window — burst at window boundary (999 requests in last second of window + 999 in first second of next = 1998 in 2 seconds)
- No `Retry-After` header — clients don't know when to retry

---

### Problem 7 — Design YouTube / Video Streaming (Hard)

**Problem Statement**
Design a video streaming platform. Users upload videos; other users watch them. Focus on the upload pipeline and video serving.

**Constraints to clarify in your session**
- 500 hours of video uploaded per minute; 1B hours watched per day
- Videos must be available for streaming within 5 minutes of upload
- Support multiple resolutions (360p, 720p, 1080p, 4K) and formats (MP4, WebM)
- Streaming must support seek (jump to any timestamp)
- CDN must serve 99.9% of watch requests

**Scope for 45 min**
Focus on: upload pipeline, transcoding, chunked serving with seek support. Mention but don't deep-dive: recommendation, comments, ads.

**Key Decisions**

| Decision | Recommendation | Why |
|---|---|---|
| Upload | Resumable upload to object store (S3 multipart); raw video stored before processing | Large files need resumable; separate raw from processed |
| Transcoding | Upload triggers message to transcoding queue → worker fleet; each worker produces all resolutions | Async to meet 5-min SLA; worker fleet scales independently |
| Chunking | Split into 2-second chunks; store in S3 as `video_id/resolution/chunk_N.ts` | Enables seek: client requests chunk containing target timestamp; adaptive bitrate switching |
| Manifest | HLS (`.m3u8`) or DASH manifest listing all chunks and their timestamps | Client parses manifest to know which chunk to request for any seek position |
| CDN | CDN caches chunks; origin = S3 | Chunks are immutable (SHA-addressed); cache indefinitely; 99.9% served from edge |

**Common Mistakes**
- Serving video as a single file — no seek without downloading entire file
- Synchronous transcoding in the upload request — blocks upload, can't scale independently
- No chunking — CDN can't cache if the URL changes per seek position
- Not mentioning HLS/DASH — the seek mechanism is in the manifest protocol

---

### Problem 8 — Design a Hotel Booking System (Hard)

**Problem Statement**
Design a hotel booking system. Users search for available rooms, reserve them, and complete payment.

**Constraints to clarify in your session**
- 500K hotels; 10M room searches/day; 1M bookings/day
- No double-booking: two users cannot book the same room for overlapping dates
- Search must return availability in < 500ms
- Payment is integrated (assume a payment API exists); booking is atomic with payment
- Cancellations allowed up to 24 hours before check-in

**Scope for 45 min**
Focus on: availability model, double-booking prevention, booking + payment atomicity. Mention but don't deep-dive: search ranking, notification flow.

**Key Decisions**

| Decision | Recommendation | Why |
|---|---|---|
| Availability model | `reservations(room_id, check_in, check_out, status)` table; availability = no overlapping confirmed reservation | Simpler than a calendar bitmap; supports flexible date ranges |
| Double-booking prevention | `SELECT FOR UPDATE` on room_id + date range → payment → insert reservation in one transaction | Pessimistic lock for the short critical section (search → pay); low contention per room |
| Search | Precomputed availability index (denormalized, updated async via CDC) | DB query for availability at 10M searches/day is too slow; serve reads from denormalized store |
| Payment atomicity | Outbox pattern: insert reservation + outbox event in one DB transaction; outbox worker calls payment API | Avoids distributed transaction; payment failure triggers compensating reservation cancellation |
| Cancellation | Soft delete with `status = CANCELLED`; run availability index update | Hard delete loses history; soft delete enables audit trail and refund processing |

**Common Mistakes**
- Optimistic locking for room booking — high retry rate under contention (many users book the same room simultaneously)
- No index on `(room_id, check_in, check_out)` — availability query scans entire table
- Synchronous payment in the booking transaction — payment API timeout causes DB lock to be held
- No compensation on payment failure — reservation remains confirmed with no payment

---

### Problem 9 — Design a Search Autocomplete (Medium)

**Problem Statement**
Design the autocomplete feature for a search bar. As the user types, show the top-5 suggestions.

**Constraints to clarify in your session**
- 10B searches/day; 5 suggestions per keystroke; average query = 4 keystrokes
- Suggestions ranked by search frequency (globally, not personalized for this version)
- New trending queries should appear in suggestions within 1 hour
- P99 latency per keystroke: < 20ms
- Support Unicode (not ASCII-only)

**Scope for 45 min**
Focus on: trie or inverted prefix index, frequency ranking, freshness update pipeline. Mention but don't deep-dive: personalization, spell correction.

**Key Decisions**

| Decision | Recommendation | Why |
|---|---|---|
| Index structure | Trie with top-K stored at each node (precomputed) | O(L) lookup (L = query length); no need to traverse the full trie on each keypress |
| Storage | Redis sorted set per prefix: `prefix:{prefix}` → sorted by frequency | In-memory; O(log N) insert, O(K) top-K read; scales to billions of prefixes |
| Update pipeline | Search logs → Kafka → frequency aggregator (Flink, 1-hour tumbling window) → update Redis | Batch updates every hour meet the freshness SLA; real-time would be overkill |
| Serving | Prefix service is stateless; reads from Redis; CDN caches popular prefixes | Top prefixes ("the", "how to") are queried billions of times; CDN eliminates most load |
| Unicode | Normalize to UTF-8; index on code points | Trie must handle multi-byte characters; normalize at ingest to avoid encoding mismatch |

**Common Mistakes**
- Re-computing top-K on every lookup from raw data — too slow
- Not caching at CDN — popular prefixes would overwhelm the service
- Updating frequency on every search synchronously — writes at 10B/day = 115K writes/sec to the index
- No precomputed top-K at each node — must scan all descendants to find top-K on each request

---

### Problem 10 — Design a Leaderboard (Medium)

**Problem Statement**
Design a real-time leaderboard for a gaming platform. Players submit scores; the leaderboard shows the top-100 globally and the current player's rank.

**Constraints to clarify in your session**
- 10M active players; 100M score updates/day
- Top-100 must refresh within 5 seconds of a new high score
- Player rank query ("what rank am I?") must return in < 50ms
- Scores are per-game (each game has its own leaderboard)
- Historical leaderboards (e.g., "last week's top-100") needed

**Scope for 45 min**
Focus on: score storage, rank computation, real-time top-100. Mention but don't deep-dive: anti-cheat, historical snapshots.

**Key Decisions**

| Decision | Recommendation | Why |
|---|---|---|
| Score store | Redis sorted set per game: `leaderboard:{game_id}` → `ZADD score player_id` | O(log N) insert/update; O(log N) rank query (`ZREVRANK`); O(K) top-K (`ZREVRANGE`) |
| Top-100 | `ZREVRANGE leaderboard:{game_id} 0 99` — O(K) | Native Redis sorted set operation; returns top-100 in order |
| Player rank | `ZREVRANK leaderboard:{game_id} player_id` — O(log N) | Exact rank without scanning; Redis sorted set stores rank implicitly |
| Historical | Cron job snapshots sorted set to S3 daily; Redis TTL = 30 days | Historical leaderboards are read-only; no need to keep in Redis indefinitely |
| Scale | Shard by `game_id` — each game's leaderboard is independent | No cross-shard operation needed; single game leaderboard fits in one Redis node |

**Common Mistakes**
- Using a relational DB for rank — `SELECT COUNT(*) WHERE score > my_score` is O(N) per query
- No TTL on Redis keys — leaderboards for inactive games accumulate indefinitely
- Global sorted set instead of per-game — one key for all games = hot key
- Not addressing concurrent score updates — `ZADD` is atomic in Redis; no extra locking needed

---

## LLD Problems

---

### Problem 11 — Design a Thread-Safe LRU Cache (Medium)

**Problem Statement**
Design an LRU cache that supports get and put operations, is thread-safe, and supports an optional TTL per entry.

**Constraints**
- `get(key)` returns value or null; moves key to most-recently-used position
- `put(key, value, ttl?)` inserts/updates; evicts LRU entry if at capacity
- Thread-safe: concurrent get/put from multiple threads
- TTL: entries expire after their TTL regardless of access pattern

**Key Decisions**
- Data structure: `LinkedHashMap` (access-ordered) + `ReentrantReadWriteLock`; or `ConcurrentHashMap` + explicit doubly-linked list
- TTL: lazy expiry (check on access) vs eager expiry (background thread). Both are acceptable; state your choice and the trade-off (lazy = simple, stale entries count against capacity; eager = complex, more accurate)
- Write lock for put + eviction (exclusive); read lock for get if you separate the "move to front" operation (but move-to-front requires a write lock too)
- Capacity eviction: O(1) with doubly-linked list; `removeEldestEntry` in `LinkedHashMap`

**Common Mistakes**
- `synchronized` on get even when not modifying (blocks all concurrent reads)
- Forgetting that `get` modifies the LRU order — it needs a write lock or careful CAS
- No TTL cleanup — expired entries consume capacity and return stale values

---

### Problem 12 — Design a Pub/Sub System (Medium)

**Problem Statement**
Design an in-process pub/sub event bus. Publishers post events to topics; subscribers receive events for topics they've subscribed to. Support both sync and async delivery.

**Constraints**
- Multiple subscribers per topic
- Slow subscriber must not block other subscribers or the publisher (async mode)
- Subscribers can dynamically subscribe/unsubscribe
- Exception in one subscriber must not affect others

**Key Decisions**
- Subscriber registry: `ConcurrentHashMap<String, CopyOnWriteArrayList<Subscriber>>` — reads are lock-free; subscribe/unsubscribe are infrequent writes
- Async delivery: `ExecutorService` per subscriber (or shared bounded pool); submit delivery task rather than calling inline
- Exception isolation: wrap each subscriber call in try-catch; log and continue to next subscriber
- Unsubscribe during delivery: `CopyOnWriteArrayList` snapshot means iteration continues safely even if concurrent unsubscribe

**Common Mistakes**
- `synchronized` on the entire publish method — serializes all publishers
- No exception isolation — one bad subscriber kills the delivery loop
- `ArrayList` instead of `CopyOnWriteArrayList` — `ConcurrentModificationException` on subscribe during publish

---

### Problem 13 — Design a Connection Pool (Medium)

**Problem Statement**
Design a generic connection pool that manages a fixed set of connections to a resource (e.g., database). Callers borrow a connection, use it, and return it.

**Constraints**
- Fixed pool size (set at construction)
- `borrow()` blocks if no connection is available (up to a configurable timeout)
- `return(connection)` makes the connection available again
- Validate connection health before lending (configurable)
- Pool must handle connections that die while borrowed (caller never returns a dead connection)

**Key Decisions**
- Blocking borrow: `Semaphore(poolSize, true)` — acquire before dequeuing; release on return
- Connection queue: `LinkedBlockingQueue<Connection>` — thread-safe dequeue/enqueue
- Validation: `validateOnBorrow` flag; if validation fails, create a new connection and try again (max 3 retries)
- Dead connection handling: caller calls `invalidate(connection)` instead of `return`; pool creates a replacement and calls `semaphore.release()`
- Timeout: `semaphore.tryAcquire(timeout, unit)` — throws `TimeoutException` if no connection available

**Common Mistakes**
- `synchronized` dequeue without a semaphore — threads spin or sleep-poll instead of blocking efficiently
- No replacement for invalidated connections — pool shrinks over time as connections die
- Releasing semaphore before returning connection to queue — another thread acquires semaphore but finds empty queue

---

### Problem 14 — Design an Async Task Queue (Medium)

**Problem Statement**
Design an in-process task queue that accepts tasks, executes them asynchronously with bounded concurrency, and supports task cancellation.

**Constraints**
- `submit(task)` returns a `Future` for the task result
- Maximum N concurrent tasks (configurable); additional tasks are queued
- `cancel(future)` cancels the task if not yet started; no-op if already running
- Tasks that throw exceptions should not crash the queue
- Graceful shutdown: wait for running tasks to complete, reject new submissions

**Key Decisions**
- Core: `ThreadPoolExecutor(coreSize, maxSize, keepAlive, queue)` with `LinkedBlockingQueue(capacity)`
- `submit()` returns `Future<T>` from `ExecutorService.submit(Callable)`
- Cancellation: `future.cancel(mayInterruptIfRunning)` — built into `Future`
- Exception isolation: `Future.get()` wraps task exceptions in `ExecutionException`; queue continues
- Shutdown: `executor.shutdown()` stops accepting new tasks; `executor.awaitTermination(timeout)` waits for running tasks

**Common Mistakes**
- Re-implementing thread pool with raw threads — reinvents `ThreadPoolExecutor` poorly
- Unbounded queue — memory grows unbounded under load; use bounded queue with rejection handler
- No graceful shutdown — `System.exit()` or `shutdownNow()` kills in-flight tasks

---

### Problem 15 — Design a Distributed ID Generator (Medium)

**Problem Statement**
Design a service that generates unique, sortable IDs for distributed use (like Snowflake). IDs must be globally unique, k-sortable (roughly time-ordered), and generated without coordination.

**Constraints**
- 64-bit integer IDs
- 100K IDs/second per node; thousands of nodes
- IDs must be monotonically increasing within a single node
- No central coordinator — each node generates independently
- Clock skew between nodes is acceptable (IDs from different nodes may interleave)

**Key Decisions**
- Snowflake layout: `[1 unused][41-bit timestamp ms][10-bit node_id][12-bit sequence]`
- 41-bit timestamp: `System.currentTimeMillis() - EPOCH` (custom epoch extends range to ~69 years)
- 12-bit sequence: incremented per millisecond; resets to 0 on new millisecond; if sequence overflows (> 4095) in same ms, wait for next millisecond
- Node ID: assigned at startup via ZooKeeper or environment variable; 10 bits = 1024 nodes max
- Clock skew: if `currentTime < lastTime` (clock moved backwards), either reject or wait until clock catches up

**Common Mistakes**
- Using `UUID.randomUUID()` — not sortable, 128 bits, no time component
- No sequence number — multiple IDs in the same millisecond on same node collide
- Not handling clock rollback — `System.currentTimeMillis()` can go backwards (NTP correction)
- No custom epoch — standard epoch (1970) wastes 41-bit range; custom epoch from 2020 gives 69 more years

---

## Problem Selection Guide

| If you're weak in... | Practice these problems |
|---|---|
| Fan-out / social systems | 3 (Twitter feed) |
| Consistency / double-booking | 8 (Hotel booking) |
| Caching systems | 5 (Distributed cache), 11 (LRU cache) |
| Real-time / geo | 4 (Ride-sharing), 10 (Leaderboard) |
| Async / queues | 2 (Notifications), 14 (Task queue) |
| Search / indexing | 9 (Autocomplete), 12 (Pub/sub) |
| Concurrency primitives | 11, 12, 13, 14 |
| ID generation | 15 |
| Simple warm-up | 1 (URL shortener) |
| Upload pipelines | 7 (YouTube) |

## Difficulty Ramp

```
Week 1 (warm-up):    Problems 1, 6, 10
Week 2 (medium):     Problems 2, 5, 9, 11
Week 3 (hard HLD):   Problems 3, 4, 7, 8
Week 4 (LLD focus):  Problems 12, 13, 14, 15
Week 5 (mixed):      Randomly select 1 HLD + 1 LLD per session
```
