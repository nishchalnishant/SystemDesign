---
module: 05-hld-problems
topic: Easy
status: interview-ready
tags: [05-hld-problems, system-design, easy]
---
# Design a Leaderboard

> **Difficulty**: Easy/Medium
> **Topics**: Redis Sorted Sets, Sharding, Aggregation
> **Time**: 45 min
> **Companies**: Google, Meta, Amazon

---

## Clarifying Questions

1. "Is this a global leaderboard or per-game, per-level, or per-region?"
2. "How many concurrent users and what's the write rate — 10K scores/sec or 10M?"
3. "Real-time ranking or near-real-time (e.g., 30-second delay acceptable)?"
4. "Do we need multiple time windows — all-time, weekly, daily simultaneously?"
5. "Do we need a friends leaderboard (rank among my 500 friends only)?"
6. "Can a user's score only increase, or can it decrease (game resets, corrections)?"

---

## Back-of-Envelope

```
Scale:
  100M players, 10K writes/sec avg, 100K burst, 100K reads/sec

Redis Sorted Set memory:
  100M players × 90 bytes (skiplist + hashmap) = ~9 GB
  → Single large Redis instance (needs 16GB RAM)
  → Only store active players (last 30 days) to reduce to ~10M players = ~900MB

Rank query latency:
  ZREVRANK: O(log N) = log₂(100M) ≈ 27 hops in skiplist = <1ms

Write throughput:
  100K ZADD/sec → single Redis node tops out at ~100K simple commands/sec
  → Pipeline 100 updates per round-trip → effectively 1K pipeline calls/sec
```

---

## APIs

```
// Submit a score
POST /api/v1/leaderboard/{game_id}/score
  { "user_id": "u123", "score": 5000 }
  → 204 No Content

// Get top-K players
GET /api/v1/leaderboard/{game_id}?page=1&size=100
  → { "rankings": [{"rank": 1, "user_id": "u456", "score": 98765}, ...], "total": 100000000 }

// Get caller's rank and nearby players
GET /api/v1/leaderboard/{game_id}/me
  → { "rank": 42317, "score": 5000, "nearby": [...] }
```

---

## Architecture

```
Client
  │
  ▼
Score Service
  ├── ZADD leaderboard:{game_id} {score} {user_id}   (sync, Redis)
  └── Kafka event (async) → PostgreSQL batch consumer (flush every 500ms)

Redis Sorted Set:
  - One ZSET per game per time window
  - Key: "lb:{game_id}:alltime", "lb:{game_id}:weekly:{year_week}"
  - ZADD GT: update only if new score > existing (monotonic scores)
  - TTL on weekly/monthly keys = period end timestamp

Read path:
  Top-K:       ZREVRANGE lb:{game_id}:alltime 0 99 WITHSCORES
  User rank:   ZREVRANK  lb:{game_id}:alltime {user_id}
  Nearby:      ZRANGE lb:{game_id}:alltime {rank-5} {rank+5} WITHSCORES

Friends leaderboard (pull-based):
  1. Fetch friend_ids from Social Graph Service
  2. Pipeline ZSCORE lb:{game_id}:alltime {friend_id} × 500
  3. Sort in application memory
  4. Cache result 30s in Redis
```

---

## Data Model

```sql
-- Persistent score store (PostgreSQL, async from Kafka)
CREATE TABLE player_scores (
    game_id    VARCHAR(50),
    user_id    BIGINT,
    score      BIGINT,
    updated_at TIMESTAMP DEFAULT NOW(),
    PRIMARY KEY (game_id, user_id)
);
-- Redis is the source of truth for ranking; DB is for durability + analytics

-- Archived weekly results (after RENAME)
CREATE TABLE leaderboard_snapshots (
    game_id     VARCHAR(50),
    week_id     VARCHAR(10),   -- e.g., "2024-W23"
    user_id     BIGINT,
    rank        INT,
    score       BIGINT,
    PRIMARY KEY (game_id, week_id, rank)
);
```

---

## Key Design Decisions

**1. Redis Sorted Set as the primary ranking store**
B-tree DB query for rank: `SELECT COUNT(*) WHERE score > {my_score}` — O(N) full table scan, slow at 100M rows. Redis ZREVRANK: O(log N) skiplist traversal, ~1ms at 100M members. ZADD is also O(log N) — handles 100K writes/sec on a single node. No other data structure provides this combination of O(log N) rank queries and O(log N) inserts.

**2. ZADD GT flag for monotonically increasing scores**
Bug scenario: score drops from 5000 to 3000 due to client error. Without protection: ZADD overwrites with 3000. With `ZADD GT`: Redis only updates if the new value is greater than existing. Atomic — no application-level read-modify-write. Requires Redis 6.2+. For games with both increasing and decreasing scores: use ZADD without GT and validate at the application layer.

**3. Weekly reset via RENAME (not DELETE + rebuild)**
If you delete the weekly set and start fresh, there's a window where the leaderboard is empty. RENAME is O(1) and atomic: `RENAME lb:game1:weekly:W23 lb:game1:weekly:W22-archive`. Old key is now the archived weekly standings; new writes go to a fresh key. No downtime, no empty leaderboard window.

**4. Top-1M in Redis, tail users via PostgreSQL COUNT**
Storing 100M users in Redis = 9GB — workable but expensive. Archive players with score=0 or inactive > 30 days to PostgreSQL. Keep only top-1M in Redis. Users below rank 1M are told their approximate rank via `SELECT COUNT(*) FROM player_scores WHERE score > {my_score}` (can be approximate with a DB stats estimate or a fast covering index).

---

## Deep Dives

**Tiebreaker encoding**
Two players with score 5000: Redis ZADD doesn't break ties deterministically. Encode a tiebreaker into the float score:
`effective_score = score × 10^13 - unix_timestamp_of_first_achievement`
Earlier achievers get a fractionally higher effective score. At display time, strip the tiebreaker before showing the raw score to users. The score field in Redis is a 64-bit float — sufficient precision for scores up to ~900,000 with a 13-digit timestamp appended.

**Multiple time windows**
Each score update must write to multiple ZSET keys: alltime + weekly + daily. Use a Redis pipeline to send all 3 ZADDs in one round-trip. Weekly keys have a TTL set at creation = seconds until end of the week. Daily keys expire after 48 hours. Storage cost: O(active_windows × active_players) — manageable.

---

## Failure Scenarios

| Failure | Impact | Mitigation |
|---------|--------|------------|
| Redis node down | All ranking queries fail | Redis Sentinel/Cluster with automatic failover; fall back to PostgreSQL rank query |
| Score submitted twice | Duplicate score event (Kafka at-least-once) | ZADD GT is idempotent for increasing scores; DB upsert handles duplicates |
| Clock skew in tiebreaker | Wrong tiebreaker ordering | Use server-assigned timestamp, not client timestamp |
| Weekly RENAME at midnight | Race with concurrent writes | RENAME is atomic; ongoing ZADD to old key after rename goes to archive (acceptable for last seconds of window) |
| Redis memory full | Eviction of scores | Monitor memory; alert at 70%; archive inactive players proactively |

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

---

## Interviewer Follow-Up Questions

**On Redis Sorted Sets:**
- "ZADD adds a score update — what happens if the same user's score arrives twice with different values?" → ZADD with the same member updates the score in place (not additive). `ZADD leaderboard 500 user:123` followed by `ZADD leaderboard 700 user:123` leaves user:123 with score 700. If you want additive scoring (cumulative points), use `ZINCRBY leaderboard 200 user:123` — this adds 200 to the current score atomically. Never read-then-write from application code; use atomic Redis commands.
- "ZREVRANK gives global rank. If you have 100M players, how much memory does the sorted set consume?" → Redis sorted set uses a skiplist + hash map internally. Each member uses ~90 bytes of overhead + string key size. 100M players × (90 bytes + ~20 bytes key) = ~11 GB. That's one large Redis instance — workable with a 16 GB instance but leaves little headroom. Mitigation: keep only active players (non-zero score in the last 30 days), archive inactive players to DynamoDB, reduce the active set to ~10M.
- "What happens to ZREVRANK performance as the sorted set grows to 100M members?" → O(log N) for ZRANK/ZREVRANGE. At 100M: log₂(100M) ≈ 27 hops in the skiplist — still very fast in practice (<1ms). The bottleneck is memory, not time complexity. Range queries (top-100) are O(log N + K) where K=100, equally fast.

**On consistency and scale:**
- "A game event fires score updates at 50K events/second. How do you prevent Redis from becoming a bottleneck?" → Pipeline: batch score updates into Redis pipelines (100 updates per pipeline call = 100× fewer round-trips). Use `MULTI/EXEC` pipeline for atomicity if needed. If 50K/sec still saturates one Redis: partition the leaderboard by game_id or region into multiple Redis shards; global leaderboard is computed via periodic aggregation.
- "You need a friends leaderboard — rank the user among their 500 friends only. How?" → Can't use ZREVRANK on the global sorted set. Options: (1) For each rank request, `ZSCORE leaderboard friend_id` for all 500 friends (500 Redis calls — too slow). Better: HMGET scores in a pipeline (1 round-trip). Sort in application memory (O(500 log 500) = trivial). (2) Pre-compute friends leaderboard at write time (fan-out to each friend's sorted set) — works if social graph is small, but celebrity with 1M friends is expensive.
- "Your leaderboard resets weekly. How do you execute the reset without downtime?" → Don't delete the old set. Rename it (`RENAME leaderboard leaderboard:2024-W23`) to archive last week's scores, then start a fresh empty key. RENAME is O(1) and atomic. Old set stays readable for "last week's standings." Scheduled job runs at midnight UTC Sunday. New week starts fresh; no need for row-by-row deletion.
