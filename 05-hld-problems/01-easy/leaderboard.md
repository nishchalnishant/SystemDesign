# Design a Real-Time Leaderboard

> **Difficulty**: Easy-Medium
> **Topics**: Redis Sorted Sets, Distributed Counters, HyperLogLog, Sharding Hot Keys
> **Time**: 40 minutes
> **Companies**: Duolingo, Roblox, Epic Games, Coursera, Chess.com

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
