---
module: 05-hld-problems
topic: Easy
status: unread
tags: [05-hld-problems, system-design, easy]
---
# Design a Real-Time Leaderboard

> **Difficulty**: Easy-Medium
> **Topics**: Redis Sorted Sets, Distributed Counters, HyperLogLog, Sharding Hot Keys
> **Time**: 40 minutes
> **Companies**: Duolingo, Roblox, Epic Games, Coursera, Chess.com

---

## Problem Mindmap

```
Real-Time Leaderboard
├── Problem Constraints
│   ├── Scale → 100M users, 1.25M leaderboards, 10K score updates/sec avg (100K/sec burst during game events)
│   ├── Latency target → rank lookup < 10ms; score update < 5ms; top-100 list < 50ms
│   └── Core hardness → real-time rank computation across 100M users without full sort on every update
├── Architecture Derivation
│   ├── Step 1 → SQL ORDER BY score DESC with RANK() → O(N log N) full sort; unacceptable at 100M rows
│   ├── Step 2 → Redis Sorted Set → ZADD O(log N); ZRANK O(log N); ZREVRANGE O(log N + K); all sub-millisecond
│   ├── Step 3 → Only top-1M users in Redis (covering 99.9% of queries); tail users computed from PostgreSQL
│   └── Step 4 → Friend leaderboard = pull-based: fetch friend list → ZSCORE for each friend → sort in-app
├── Core Components
│   ├── Redis Sorted Set → ZADD leaderboard:{game_id} score user_id; ZREVRANK for rank; ZREVRANGE for top-K
│   ├── PostgreSQL → persistent score store; source of truth; synced async from Redis via Kafka consumer
│   ├── Score Update Service → validates score (anti-cheat rules) → ZADD Redis → publish to Kafka
│   ├── Friend Leaderboard Service → fetch friends from Social Graph → batch ZSCORE → sort → return top-10 friends
│   └── Leaderboard Snapshot job → hourly Spark job for weekly/monthly leaderboard snapshots into S3
├── Data Model
│   ├── Redis → ZSET "lb:{game_id}:{period}" → {user_id: score}; TTL = period end time
│   └── PostgreSQL scores → (user_id, game_id, score, updated_at); indexed on (game_id, score DESC) for tail queries
├── APIs
│   ├── POST /score → {user_id, game_id, score, delta} → {new_score, rank}
│   ├── GET /leaderboard/{game_id}?page=1&size=100 → [{rank, user_id, score, username, avatar}]
│   └── GET /leaderboard/{game_id}/me → {user_id, score, rank, percentile, neighbors: ±5 ranks}
├── Critical Trade-offs
│   ├── Redis vs DB sort → Redis ZSET chosen; O(log N) update and rank vs O(N log N) full scan
│   ├── Real-time vs Snapshot → real-time for current leaderboard; snapshot for historical weekly/monthly (Spark job)
│   └── Global vs Friend leaderboard → global uses Redis ZSET; friend uses pull-based ZSCORE batch (avoid fan-out on write)
├── Failure Scenarios
│   ├── Redis failure → serve stale leaderboard from PostgreSQL snapshot (up to 1 min stale); auto-rebuild Redis on restart
│   ├── Hot key (popular game) → shard ZSET by score range: lb:{game_id}:0-1M, lb:{game_id}:1M-2M; aggregate at read time
│   └── Score manipulation → server-side anti-cheat validation before ZADD; anomaly detection on delta distribution
└── Interview Angles
    ├── Duolingo → "Design XP leaderboard for 50M daily active learners" → Redis ZSET + weekly snapshot + friend leaderboard
    ├── Chess.com → "Design ELO rating leaderboard" → same Redis ZSET; ELO computed server-side before ZADD
    └── Follow-up → "How do you show a user their rank when they're #45,789,321?" → ZREVRANK is O(log N); works even at 100M
```

---

## What Breaks Without This System?

Duolingo launches a weekly XP competition. Rank is computed by querying PostgreSQL: `SELECT COUNT(*) + 1 FROM user_scores WHERE score > (SELECT score FROM user_scores WHERE user_id = ?)`. At 100M users and 10K score updates/sec, this query takes 3–8 seconds. The leaderboard shows ranks from 10 minutes ago. After a tournament event drives 100K score updates/sec, the DB falls behind and rank data becomes 45 minutes stale. Users refresh obsessively to see if they've climbed — their refreshes compound the query load. The DB locks up. The game stops working.

Without real-time rank infrastructure: ranks are either computed too slowly to be meaningful (minutes behind), computed too expensively (full table scans per user), or not updated during the event that makes them interesting (the tournament). You lose the engagement loop that makes the leaderboard valuable.

---

## Derive the Architecture

**Step 1 — Single DB query**
`SELECT COUNT(*) + 1 FROM user_scores WHERE score > ?` with an index on `score`. Works for small N. At 100M rows, even an indexed count takes 50–200ms per query. At 100K reads/sec, that's 5,000–20,000 seconds of DB compute/second — unsustainable.

**Step 2 — What data structure gives O(log N) insert and O(log N) rank in one shot?**
A skip list (which is what Redis Sorted Set implements). `ZADD leaderboard score member` inserts in O(log N). `ZREVRANK leaderboard member` returns rank in O(log N) — it doesn't scan, it traverses the skip list levels. For 100M members, that's roughly 27 levels of skip → sub-millisecond even on 100M entries.

**Step 3 — What forces us away from a single Redis Sorted Set for 100M users?**
100M × 30 bytes/entry = 3GB. A single Redis instance can hold this, but:
- A single global leaderboard with 100M entries is feasible in one Redis node.
- 1.25M leaderboards (global + 250 country + 1M friend groups) is not — the memory and fan-out cost is prohibitive.
- Solution: keep only top-1M users in Redis (the meaningful competition range); compute rank for the other 99M with the DB query (they're far enough behind that 10ms latency is acceptable).

**Step 4 — Score update path**
At 100K writes/sec peak:
- Redis ZADD: Redis handles 1M ops/sec. Writing to Redis synchronously is fine.
- PostgreSQL sync write: 100K writes/sec saturates a single DB node (~50K max). Solution: Redis is the synchronous write (user sees update immediately); PostgreSQL is the durable store updated async via Kafka + batch consumer (500ms batch window). Idempotent: `ON CONFLICT (user_id) DO UPDATE SET score = GREATEST(excluded.score, score)`.

**Step 5 — Friend group leaderboards**
User has 500 friends. Fan-out on write: every score update triggers 500 leaderboard updates × 100M users = 50B write operations/day. Infeasible.

Pull-based alternative: on query, fetch friend IDs from the social graph, `SELECT score WHERE user_id IN (friend_ids)`, sort in application layer. Friend list ≤ 500 entries — sort is O(500 log 500), trivially fast. Cache result in Redis for 30 seconds. No write fan-out needed.

**Step 6 — Multiple time windows**
One Redis Sorted Set per window: `leaderboard:alltime`, `leaderboard:weekly:2026-w20`, `leaderboard:monthly:2026-05`. Each score update writes to all active windows in a Redis pipeline. Weekly/monthly keys get a TTL set at creation — they expire automatically after the window closes. Storage cost: proportional to active windows × active users, not total history.

---

## Problem Statement

Design a real-time leaderboard that:
- Tracks scores for 100M users across multiple games/categories
- Displays global top-100 rankings updated in real-time
- Shows a user's rank and nearby players (rank ± 5)
- Supports multiple leaderboards: global, country-specific, friend-group
- Handles score updates at 10K writes/sec (burst: 100K/sec during tournaments)

---

## Analogy

A marathon scoreboard at the finish line. As runners cross, the time is recorded and the board instantly re-orders. First place might change hands as runners finish. The challenge: 100,000 runners, each finishing at a slightly different time, but viewers always see the correct ranking instantly.

---

## Scale Estimation

```
Users: 100M
Leaderboards: 1 global + 250 country + 1M friend groups = ~1.25M leaderboards
Score updates: 10K/sec avg, 100K/sec peak (live tournament)
Reads (rank queries): 100K/sec (much higher than writes)

Storage per leaderboard:
  Sorted set entry: member_id (8B) + score (8B) + overhead = ~30 bytes
  Global leaderboard: 100M × 30 bytes = 3GB in Redis
  Too large for single Redis instance → only top N in Redis, rest in DB

Practical design:
  Redis: store only top 1M users in global leaderboard (99th percentile)
  PostgreSQL: store all 100M users (for rank calculation of non-top users)
```

---

## Core Concept: Redis Sorted Set

```
Redis ZADD: O(log N) insert/update
Redis ZRANK: O(log N) rank lookup
Redis ZRANGE: O(log N + K) to fetch top-K

Commands:
  # Update score
  ZADD leaderboard:global 9500 "user:12345"

  # Get user's rank (0-indexed, lower = better if scores are descending)
  ZREVRANK leaderboard:global "user:12345"  → 42 (rank #43)

  # Get top 10
  ZREVRANGE leaderboard:global 0 9 WITHSCORES

  # Get user's neighbors (rank 40-50)
  rank = ZREVRANK(user)
  ZREVRANGE leaderboard:global (rank-5) (rank+5) WITHSCORES

Time complexity:
  Insert/update: O(log N)
  Rank query: O(log N)
  Range query (top K): O(log N + K)
  All sub-millisecond for N up to 100M
```

---

## Architecture

```
User completes action (scores points)
         │
         ▼
┌─────────────────────┐
│    Score Service    │
│  1. Update DB       │ ← PostgreSQL (persistent, all users)
│  2. Update Redis    │ ← Redis ZADD (fast, top users)
│  3. Publish event   │ ← Kafka (for friend group leaderboards)
└─────────────────────┘
         │
         ├─────────────────────────┐
         ▼                         ▼
┌────────────────┐        ┌────────────────────┐
│ Redis Cluster  │        │ Friend Group Worker│
│ (Sorted Sets)  │        │ (updates friend    │
│ - global       │        │  leaderboards)     │
│ - per country  │        └────────────────────┘
└────────────────┘

Query path:
User requests rank →
  If user in Redis sorted set: ZREVRANK (sub-ms)
  If user NOT in Redis (below top 1M): SELECT COUNT(*) FROM scores WHERE score > user_score
                                       (indexed query, ~10ms, cached)
```

---

## Score Update Flow

```sql
-- PostgreSQL: single source of truth for all users
CREATE TABLE user_scores (
    user_id     BIGINT PRIMARY KEY,
    username    VARCHAR(50),
    country     CHAR(2),
    score       BIGINT DEFAULT 0,
    last_played TIMESTAMP,
    INDEX idx_score_desc (score DESC)  -- For rank calculation by range query
);

-- Score update (transactional)
BEGIN;
  UPDATE user_scores
  SET score = score + :points_earned, last_played = NOW()
  WHERE user_id = :user_id;
COMMIT;

-- Then: async Redis update (non-blocking)
redis.execute_command("ZADD", "leaderboard:global", new_score, f"user:{user_id}")
redis.execute_command("ZADD", f"leaderboard:country:{country}", new_score, f"user:{user_id}")
```

---

## Rank Query for Users Outside Top 1M

```sql
-- Approximate rank (fast, uses index)
SELECT COUNT(*) + 1 as rank
FROM user_scores
WHERE score > (SELECT score FROM user_scores WHERE user_id = :user_id);

-- This uses the idx_score_desc index → O(log N) scan → < 10ms even at 100M rows

-- Cache: rank for a user is unlikely to change frequently for lower-ranked users
-- Cache key: rank:{user_id}, TTL: 60 seconds
```

---

## Handling Score Updates at 100K/sec

```
Problem: 100K updates/sec to Redis ZADD is feasible (Redis handles 1M ops/sec).
         But 100K writes/sec to PostgreSQL is challenging (~50K max with pooling).

Solution: Async write batching to PostgreSQL

  1. Score update API → immediately writes to Redis (synchronous, user sees update)
  2. Score update API → publishes to Kafka "score_updates" topic
  3. Batch consumer reads from Kafka, groups updates by user_id
  4. Batch INSERT/UPDATE to PostgreSQL every 500ms

Trade-off:
  PostgreSQL may lag Redis by up to 500ms (acceptable for leaderboard)
  If system crashes in that 500ms window: re-consume from Kafka (at-least-once)
  Idempotent: ON CONFLICT (user_id) DO UPDATE SET score = GREATEST(excluded.score, score)
```

---

## Friend Group Leaderboards

```
Challenge: User X has 500 friends. Each friend's score update should update X's
           friend group leaderboard. With 100M users × 500 friends = 50B fan-out events/day.

Solution: Pull-based friend leaderboard (compute on query, not on write)

On query for "friend leaderboard":
  1. Fetch friend IDs from social graph (Friend Service)
  2. SELECT username, score FROM user_scores WHERE user_id IN (friend_ids + self)
  3. Sort in application layer (small list: ≤ 500 friends)
  4. Cache result: Redis key "friendlb:{user_id}" TTL: 30 seconds

This avoids the fan-out write problem entirely.
30-second cache means occasional staleness (acceptable — friends see near-real-time rankings).
```

---

## Interview Talking Points

**Q: "How does Redis Sorted Set handle ties in score?"**
> "By default, when two members have the same score, Redis ranks them lexicographically by member key. For a real leaderboard where ties should resolve by who achieved the score first, you can encode time into the score: `effective_score = score × 10^13 - unix_timestamp`. This makes scores unique while preserving the time-wins-tie semantic. Higher score still wins; among equal scores, earlier achiever has a slightly higher effective_score."

**Q: "How do you handle a tournament where everyone's score changes simultaneously?"**
> "Tournament scores are often batch-computed at the end (all participants played at the same time). We'd compute final scores server-side, then run a Lua script in Redis to atomically update all entries at once: `redis.call('ZADD', key, score, member)` in a pipeline. Redis pipelines are processed atomically, so the leaderboard transitions from old scores to new scores without any intermediate inconsistent state being visible to readers."

---

## Interview Questions Asked

### Google
1. **"Design a real-time gaming leaderboard for 10 million concurrent players — what's your data structure of choice and why?"** → Redis Sorted Set: O(log N) insert and rank query, atomic ZADD and ZRANK, built-in support for range queries (top-K, percentile rank). The interviewer wants to hear you justify it over a database (too slow for real-time at 10M players) and mention that a single Redis Sorted Set handles 10M members comfortably (< 1 GB).
2. **"How do you display a player's rank on a leaderboard with 10M entries without scanning the full set?"** → `ZREVRANK key member` in Redis is O(log N) — returns rank in ~1ms regardless of set size. This is the key insight: rank is computed from the skip list structure, not by scanning. For relative rank (e.g., "you're in the top 2.3%"), divide ZREVRANK result by ZCARD.

### Meta
1. **"Design weekly and monthly leaderboard windows alongside an all-time leaderboard — how do you maintain multiple windows without duplicating all writes?"** → One Redis Sorted Set per time window: `leaderboard:alltime`, `leaderboard:weekly:{week_id}`, `leaderboard:monthly:{month_id}`. Each score update writes to all relevant keys in a Redis pipeline (atomic multi-key write). Weekly/monthly keys expire automatically via TTL after the window closes. Storage is proportional to active windows × players, not total history.
2. **"A player's score can only go up — but what if a bug causes a score to be reported lower than the current? How do you prevent score regression?"** → Use Redis `ZADD NX` (only add if not exists) or application-level check: `ZADD key GT score member` (update only if new score is Greater Than existing). The `GT` flag, available in Redis 6.2+, atomically ensures scores are monotonically increasing without a separate read-modify-write cycle.

### Amazon
1. **"How would you implement a leaderboard for Amazon's seller performance ranking — updated daily, queried by millions of sellers?"** → Batch computation: daily Spark job computes rankings over the full seller dataset, writes results to DynamoDB (seller_id → rank, score, percentile). Real-time reads go to DynamoDB (single-digit ms latency), not Redis — the leaderboard is only updated once per day so no real-time write path is needed. Simpler and more cost-effective than Redis for low-update-frequency use cases.

### Common Follow-ups
1. **"How do you handle ties in rank — two players with the same score?"** → Encode time into the score as a tiebreaker: `effective_score = actual_score × 10^13 - unix_timestamp_of_first_achievement`. Earlier achievers get a fractionally higher effective score, so they rank above later achievers with the same score. This is lossless at display time — strip the timestamp component before showing the score to users.
2. **"How do you implement a top-K leaderboard across sharded Redis instances without using a global sorted set?"** → Each shard maintains a local top-K sorted set. A periodic aggregation job (every 5s) queries the top-K from each shard, merges the lists in application memory (O(K × shards)), and writes the merged global top-K to a separate Redis key. Queries for the global top-K read this pre-computed merged key — O(1) read at the cost of slight staleness.
3. **"How do you shard the leaderboard across geographic regions while keeping a single global ranking?"** → Each region maintains a local Redis Sorted Set for low-latency writes. A background aggregation service (every 30s) pulls top-K from each regional shard, merges, and publishes the global top-K to all regions. Full global rank (not just top-K) requires a MapReduce-style job — impractical in real-time, so most systems only guarantee real-time ranking for the top-K and compute full rank on a slower cadence.
