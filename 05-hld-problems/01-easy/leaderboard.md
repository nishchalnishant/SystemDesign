---
module: 05-hld-problems
topic: Easy
status: unread
tags: [05-hld-problems, system-design, easy, leaderboard, redis-sorted-set, ranking, real-time]
---
# Design a Leaderboard

> **Difficulty**: Easy | **Asked at**: Amazon, Riot Games, Robinhood, Stripe

---

## Problem Statement

Design a real-time leaderboard for a game or competition. Players have scores. The leaderboard shows the top-N players globally, and each player can see their own rank. Scores are updated continuously as players complete matches.

---

## Functional Requirements

1. **Update score**: Record a score update for a player (absolute set or incremental delta)
2. **Top-N leaderboard**: Return the top 100 players globally, ordered by score
3. **Player rank**: Given a player_id, return their current rank and score
4. **Nearby players**: Return the 5 players above and below a given player on the leaderboard
5. **Percentile**: Return what percentile a player is in (e.g., top 5%)

---

## Non-Functional Requirements

- **Scale**: 10M players, 1,000 score updates/sec, 50,000 leaderboard reads/sec
- **Latency**: Score update < 5ms; top-N query < 10ms P99
- **Freshness**: Leaderboard must reflect score updates within 1 second
- **Availability**: 99.99% — leaderboard must be viewable even during DB maintenance

---

## Core Entities

| Entity | Key Fields |
|--------|-----------|
| `Player` | player_id, username, avatar_url |
| `Score` | player_id, score, updated_at, season_id |
| `LeaderboardEntry` | rank, player_id, username, score |

---

## API Design

```http
POST /api/v1/scores
Body: { "player_id": "p123", "score": 4500, "season_id": "s2026" }
Response 200: { "new_rank": 1542, "previous_rank": 1891, "score": 4500 }

GET /api/v1/leaderboard?season_id=s2026&limit=100
Response 200: {
  "leaderboard": [
    { "rank": 1, "player_id": "p001", "username": "xXSniper", "score": 99999 },
    { "rank": 2, "player_id": "p002", "username": "GodMode", "score": 98500 }
  ],
  "total_players": 10000000
}

GET /api/v1/players/{player_id}/rank?season_id=s2026
Response 200: { "rank": 1542, "score": 4500, "percentile": 98.5, "nearby": [...] }
```

---

## High-Level Design

```
Score Update Request
  │
  ▼
App Server
  │
  ├── Redis ZADD leaderboard:{season_id} score player_id
  │    └── O(log N) → returns new rank immediately
  │
  └── PostgreSQL (async write via Kafka)
       └── durable score history for reporting and audit
  │
  ▼
Leaderboard Read:
  Redis ZREVRANGE leaderboard:s2026 0 99 WITHSCORES → top 100
  Redis ZREVRANK leaderboard:s2026 player_id → O(log N)
  Redis ZSCORE leaderboard:s2026 player_id → O(1)
```

**Redis Sorted Set** is the core data structure. It maintains members (player_ids) sorted by score, with O(log N) insert/update and O(log N) rank queries.

Redis sorted set operations used:
- `ZADD key score member` — set/update a player's score
- `ZREVRANK key member` — get 0-indexed rank (highest score = rank 0)
- `ZREVRANGE key start stop WITHSCORES` — get top-N players
- `ZRANGEBYSCORE key min max` — get players in a score range
- `ZCARD key` — total number of players

**PostgreSQL as audit store**: All score updates are written to PostgreSQL asynchronously (via Kafka) for historical analysis, season resets, and dispute resolution. Not on the hot path.

---

## Deep Dive 1: Redis Sorted Set Internals

**Data structure**: Redis sorted set uses a **skip list** + **hash map**.
- Skip list: ordered by score, enables range queries (ZRANGE) in O(log N + K) where K = result count
- Hash map: maps member → score for O(1) ZSCORE lookup

**Memory**: 10M players × ~50 bytes per entry (8 bytes score + 36 bytes player_id + overhead) ≈ 500 MB. Fits in memory on a single Redis node. A 16 GB Redis instance handles 20× this size.

**Score conflicts (tie-breaking)**: When two players have the same score, Redis sorts lexicographically by member name. This is non-ideal for games where tied scores should rank by who achieved it first. Solution: encode timestamp in the score as a fractional component:
```
score = integer_score + (1 - (timestamp / MAX_TIMESTAMP))
```
Higher integer score wins. Among ties, earlier timestamp wins (fractional part closer to 1).

Alternatively, use compound keys: `ZADD lb {score} {player_id}` and `ZADD lb_tiebreak {timestamp} {player_id}`. Query lb first; on ties, consult lb_tiebreak.

---

## Deep Dive 2: Multiple Leaderboards and Segmentation

**Problem**: A game needs multiple concurrent leaderboards: global all-time, current season, weekly, regional (North America vs Europe), by game mode.

**Solution**: One Redis sorted set per leaderboard. Key naming convention:
```
leaderboard:global:alltime
leaderboard:season:2026-q2
leaderboard:weekly:2026-w26
leaderboard:region:na:weekly:2026-w26
leaderboard:mode:ranked:season:2026-q2
```

**Score updates fan out**: A single score update event is consumed by all relevant leaderboard consumers:
```
Score event → Kafka → Consumer group:
  - Update global leaderboard
  - Update current season leaderboard
  - Update weekly leaderboard (expires Monday)
  - Update regional leaderboard
```

**Weekly leaderboard reset**: Use Redis key expiry. Set `EXPIREAT leaderboard:weekly:2026-w26 <next_monday_unix>`. When the key expires, the leaderboard is automatically deleted. New entries for the next week go to a new key. No manual cleanup needed.

**Memory management**: Keep only active leaderboards (current season, last season for historical reference). Archive old leaderboards by dumping to S3 before expiry.

---

## Deep Dive 3: High-Cardinality Rank and Percentile Queries

**Problem**: "What percentile am I in?" requires knowing the total number of players and your rank.

```python
def get_percentile(player_id, season_id):
    rank = redis.zrevrank(f"leaderboard:{season_id}", player_id)  # 0-indexed
    total = redis.zcard(f"leaderboard:{season_id}")
    percentile = 100 * (1 - rank / total)
    return round(percentile, 1)
```

`ZREVRANK` is O(log N). `ZCARD` is O(1). Both are fast enough to run on the hot path.

**Nearby players**: Show 5 players above and below the current player:
```python
def get_nearby(player_id, season_id):
    rank = redis.zrevrank(f"leaderboard:{season_id}", player_id)
    start = max(0, rank - 5)
    end = rank + 5
    return redis.zrevrange(f"leaderboard:{season_id}", start, end, withscores=True)
```

**Top-N caching**: The top 100 leaderboard is read 50K times/sec but changes infrequently (only when top players' scores change). Cache the top-100 list in Redis with a 1-second TTL (as a string, not a sorted set). On cache miss, recompute from the sorted set.

**Score update rate limiting**: A player can submit many score updates rapidly (e.g., 10 updates/second from multiple game sessions). Rate limit to 1 update/second per player_id using a Redis token bucket. Buffer additional updates in a per-player queue, apply the highest score.

---

## Interviewer Questions by Level

**Junior**:
- What data structure does Redis use for sorted sets? What are its time complexities?
- How do you get a player's rank from a sorted set?
- How do you handle weekly leaderboard resets?

**Mid-level**:
- How do you handle tie-breaking when two players have the same score?
- How do you design multiple concurrent leaderboards (global, seasonal, weekly, regional)?
- How does the Redis sorted set scale to 10M players? What's the memory footprint?

**Senior**:
- At 1,000 score updates/sec, how do you ensure write durability while keeping update latency < 5ms?
- How would you design a leaderboard for 1 billion players? Does Redis still work?
- How would you implement a "relative leaderboard" that shows only friends of the current user (not global)?
- Design the score update pipeline that handles out-of-order events (a match result arriving late).
