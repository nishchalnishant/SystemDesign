> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design a real-time gaming leaderboard at hard difficulty — multiple leaderboard scopes (global, regional, tournament, friends), sub-second updates, and millions of concurrent players.
>
> **Key design decisions:**
> - Core: Redis ZADD/ZREVRANK per leaderboard scope; O(log N) update and rank query; separate sorted set per scope (global, per-region, per-tournament)
> - Friends leaderboard: can't pre-compute for all friend groups → query: fetch friend IDs (Redis set) → ZSCORE for each friend → sort client-side; or use secondary sorted set per user updated on score change
> - Score aggregation: match end → Kafka → score aggregation service → atomic ZADD; batch updates to reduce Redis write load
> - Tournament leaderboard: TTL-based sorted set per tournament; active tournament data in Redis; archive to PostgreSQL on completion
> - Scale: 100M players × 8 bytes (score) ≈ 800 MB per sorted set; fits in one Redis node; shard by game_id for multiple concurrent games
> - Nearby rank: ZREVRANK (player rank) → ZREVRANGE (rank-5 to rank+5) → get neighbor entries; O(log N) + O(K)
> - Percentile: (total_players - rank) / total_players × 100; ZCARD O(1) for total count
>
> **Key takeaway:** This is leaderboard easy problem × multiple scopes — the friends leaderboard is the hard part because you can't pre-compute; solve it with on-demand ZSCORE lookup for friend IDs.

---
module: 05-hld-problems
topic: Hard
status: unread
tags: [05-hld-problems, system-design, hard, leaderboard, redis, sorted-set, gaming, real-time]
---
# Design a Real-Time Gaming Leaderboard

> **Difficulty**: Hard | **Asked at**: Riot Games, Roblox, Epic Games, Amazon

---

## Problem Statement

Design a real-time leaderboard for a competitive game with millions of players. Players earn scores during matches; the leaderboard must show global rankings, a player's rank, and their neighbors (players ranked just above and below). Rankings update in near-real-time as scores change. Support multiple leaderboard scopes: global, regional, friends-only, and per-tournament.

---

## Functional Requirements

1. **Score update**: When a player earns points, update their score on the leaderboard
2. **Global rank**: Return a player's global rank (e.g., "Rank #12,345 out of 2M players")
3. **Top-K**: Return the top 100 players with scores and usernames
4. **Leaderboard page**: Return a page of 50 players around a given rank
5. **Friends leaderboard**: Return rankings scoped to a player's friend list
6. **Tournament leaderboard**: Isolated leaderboard per tournament with start/end times

---

## Non-Functional Requirements

- **Scale**: 2M concurrent players; 100K score updates/sec; 1M rank queries/sec
- **Latency**: Score update < 10ms; rank query < 20ms; top-100 < 50ms
- **Accuracy**: Rank must be accurate within 1 second of a score change
- **Availability**: 99.99% — leaderboard visible even during partial failures
- **Tie-breaking**: Players with equal scores ranked by achievement time (earlier = higher rank)

---

## Core Entities

| Entity | Key Fields |
|--------|-----------|
| `Player` | player_id, username, region, avatar_url |
| `Score` | player_id, leaderboard_id, score, last_updated, rank |
| `Leaderboard` | leaderboard_id, name, scope (global/regional/tournament), start_at, end_at |
| `Tournament` | tournament_id, name, leaderboard_id, game_mode, start_at, end_at |

---

## API Design

```http
POST /api/v1/leaderboards/{lb_id}/scores
Body: { "player_id": "p123", "score_delta": 450 }
Response 200: { "new_score": 12450, "rank": 8341 }

GET /api/v1/leaderboards/{lb_id}/top?limit=100
Response 200: {
  "entries": [
    { "rank": 1, "player_id": "p999", "username": "ProSniper", "score": 99850 },
    { "rank": 2, "player_id": "p888", "username": "DarkBlade", "score": 98200 }
  ]
}

GET /api/v1/leaderboards/{lb_id}/players/{player_id}/rank
Response 200: { "rank": 8341, "score": 12450, "neighbors": [...] }

GET /api/v1/leaderboards/{lb_id}/players/{player_id}/friends
Response 200: { "entries": [{ "rank": 3, "player_id": "p567", "username": "FriendA", "score": 11200 }] }
```

---

## High-Level Design

```
Game Server (match outcome)
  │ POST /scores with score_delta
  ▼
Score Service
  │ 1. Compute new score: ZINCRBY lb:{lb_id} {delta} {player_id}
  │ 2. Redis ZSET handles ranking automatically (O(log N))
  │ 3. Publish score-update event → Kafka
  │
  ▼
Redis ZSET: lb:{lb_id}
  │ ZINCRBY: update score
  │ ZREVRANK: get player's rank (0-indexed)
  │ ZREVRANGE: top-K players
  │ ZRANGEBYSCORE: page by score range
  │
  ▼
Kafka: score-updates
  ├── DB Writer: persist scores to PostgreSQL (source of truth)
  └── Notification Service: push rank change alerts to players

PostgreSQL: player profiles, leaderboard configs, historical scores
Redis: live leaderboard data (primary serving layer)
```

---

## Deep Dive 1: Redis Sorted Set as Leaderboard

**Problem**: 2M players, 100K score updates/sec, 1M rank queries/sec. A SQL `ORDER BY score DESC` with OFFSET for pagination requires a full index scan for deep pages — too slow.

**Redis Sorted Set (ZSET)**:
- Data structure: skip list + hash map. Each member has a score (float64).
- Rank queries are O(log N) — skip list traversal.
- Range queries are O(log N + K) — K elements returned.

**Core operations**:
```redis
# Update player score (add delta)
ZINCRBY lb:global 450 "player:p123"  → new_score: 12450

# Get player's 0-indexed rank (highest score = rank 0)
ZREVRANK lb:global "player:p123"  → 8340  (rank 8341, 1-indexed)

# Get top 100 players with scores
ZREVRANGE lb:global 0 99 WITHSCORES

# Get 50 players around rank 8341 (ranks 8316-8365)
ZREVRANGE lb:global 8315 8364 WITHSCORES
```

**Tie-breaking**: Players with equal scores must be ranked by achievement time (earlier = higher rank). Redis scores are float64; encode as: `score = raw_score * 1e10 + (MAX_TIME - achievement_timestamp)`. This ensures higher raw score wins; for equal raw scores, earlier timestamp wins.

**Memory**: 2M players × ~100 bytes per ZSET entry = 200 MB — fits in a single Redis instance. For 20 leaderboards: 4 GB — use Redis Cluster or split across Redis instances by leaderboard_id.

---

## Deep Dive 2: Friends Leaderboard at Scale

**Problem**: Each player has up to 200 friends. A player wants to see their ranking among their friends only. Naively, you'd query each friend's score individually and sort — 200 Redis calls per friends-leaderboard request.

**Approach 1: Redis pipeline** — Batch 200 `ZSCORE lb:global friend_id` calls in a single pipeline → 200 scores → sort in application → return ranked list. Latency: ~5ms (one round-trip). Sufficient for 200 friends.

**Approach 2: Per-player friends ZSET** — When a player's score updates, also update a ZSET for each of their followers/friends. Write amplification: one score update → up to 200 ZSET updates. Acceptable for 200 friends at 100K updates/sec = 20M Redis writes/sec — too high.

**Hybrid**: Use pipeline approach (Approach 1) for friends leaderboard (reads are rare; latency is acceptable). Reserve per-player ZSETs for celebrity players with 10K+ followers only.

**Cache**: Cache a player's friends leaderboard in Redis: `friends_lb:{player_id}` with TTL 30s. Valid for most use cases where friends lists don't change mid-session.

---

## Deep Dive 3: Tournament Leaderboard Lifecycle

**Problem**: A weekend tournament starts at Saturday noon, ends Sunday midnight. 500K players participate. Scores accumulate during the tournament. At end, final rankings are frozen and prizes distributed.

**Lifecycle management**:
```
Tournament created → Create ZSET lb:tournament:{t_id} in Redis
Tournament starts → Accept score updates (ZINCRBY)
Tournament ends → Snapshot final rankings to PostgreSQL; set Redis key TTL = 7 days
Prize distribution → Query PostgreSQL snapshot (Redis may have expired)
```

**Score isolation**: Tournament scores are separate from global scores. A tournament ZINCRBY only affects `lb:tournament:{t_id}`, not `lb:global`.

**Real-time score feed** (during tournament):
```
Top players' scores update every few seconds.
Push top-20 leaderboard to all active tournament viewers via WebSocket.

Flink job: read from Kafka score-updates (filter tournament events)
→ every 5 seconds: ZREVRANGE lb:tournament:{t_id} 0 19 WITHSCORES
→ publish to Pub/Sub channel "tournament:{t_id}:top20"
→ WebSocket gateway fans out to all subscribed viewers
```

**Handling massive concurrent updates at tournament start**:
- Tournament start triggers a flood of score submissions. Redis ZINCRBY is serialized (single-threaded Redis). At 100K writes/sec, Redis can handle this — Redis benchmarks at 1M simple ops/sec on modern hardware.
- For extreme throughput: batch score updates with a 1-second tumbling window in the application layer before writing to Redis. `ZADD ... GT` (update only if greater) semantics can replace ZINCRBY if scores only go up.

---

## Interviewer Questions by Level

**Junior**:
- What is a leaderboard? What data does it show?
- Why is Redis a good fit for a leaderboard? What data structure does it use?
- What is the time complexity of getting a player's rank in a Redis sorted set?

**Mid-level**:
- How does Redis ZSET enable O(log N) rank queries? What data structure backs it?
- How do you handle tie-breaking for players with equal scores?
- How do you implement a friends leaderboard without fan-out writes?

**Senior**:
- Design the score update pipeline for 100K updates/sec — from game server to Redis to persistent storage.
- How do you shard the global leaderboard across multiple Redis instances when 2M players exceed single-instance memory?
- Design the tournament leaderboard lifecycle — creation, live updates, finalization, historical queries.
- How do you handle rank accuracy during Redis node failure? What is the recovery path?

---

## Back-of-Envelope Estimation

**Scale inputs (given in NFRs):**
- 10M concurrent players; 1B score updates/day; leaderboard read < 10ms; top-10 globally

**Score update throughput:**
- 1B score updates/day ÷ 86,400 sec = **~11,574 updates/sec** average
- Peak (weekend prime time): 5× = **~57,870 updates/sec**
- Each update: `{player_id, game_id, score_delta, ts}` ≈ 50 bytes
- Peak write throughput: 57,870 × 50 bytes = **~2.9 MB/sec** — trivial for Kafka ingest

**Redis sorted set for global leaderboard:**
- 10M concurrent players in the sorted set: `ZADD leaderboard {score} {player_id}`
- Memory per player: player_id (8 bytes) + score (8 bytes) + hash table overhead = ~50 bytes
- Total: 10M × 50 bytes = **~500 MB** for the full global leaderboard in Redis
- `ZADD` is O(log N): log₂(10M) ≈ 23 operations per update → at 57,870/sec = **~1.3M comparisons/sec** — Redis single thread handles ~10M comparisons/sec, so this is fine

**Leaderboard read performance:**
- `ZREVRANGE leaderboard 0 9 WITHSCORES` (top 10): O(log N + K) = O(23 + 10) ≈ O(33) operations
- At 10M reads/day = **~115 reads/sec** average, peak 5× = 575 reads/sec
- Redis handles 1M ops/sec; 575 ZREVRANGE ops/sec = **0.06% of Redis capacity**
- < 10ms easily achieved: Redis ZREVRANGE latency is typically 0.1–0.5ms

**Player rank lookup:**
- `ZREVRANK leaderboard {player_id}`: O(log N) = ~23 operations
- Each of 10M concurrent players checks their rank every 60 seconds = 10M/60 = **~167K rank lookups/sec**
- Redis handles this comfortably at 1M ops/sec

**Score update pipeline (high-frequency writes):**
- 57,870 updates/sec directly to Redis: Redis ZADD is synchronous; at this rate, a single Redis instance is 5.8% saturated (57,870 ÷ 1M) — fine
- But with 10M concurrent players all potentially sending updates simultaneously: burst to 10M/sec would require Kafka buffering → batch update Redis every 100ms

**Sharding for regional leaderboards:**
- Global leaderboard: one Redis sorted set (500 MB fits in one instance)
- Regional leaderboards (top 100 per country): 200 countries × 500 MB = **~100 GB** → Redis cluster with 2 nodes (50 GB each)
- Game-specific leaderboards: if 1M active games each have a leaderboard → would need 1M Redis sorted sets → impractical to keep all in RAM; use DB-backed leaderboards for low-volume games, Redis only for top-1000 most active games

**Architecture decisions driven by these numbers:**
- **Redis sorted set as the core data structure**: The leaderboard requires rank queries (`ZREVRANK`), range queries (`ZREVRANGE top-10`), and point updates (`ZADD`) all in O(log N). At 10M players, a DB `SELECT ... ORDER BY score DESC LIMIT 10` with a full table sort is O(N log N) = unacceptable. A DB with an index can do O(log N) for rank, but the index write overhead at 57,870 updates/sec causes lock contention. Redis sorted set is a native, lock-free skip list implementation optimized exactly for this pattern.
- **Kafka buffer for burst absorption, not direct Redis writes**: At peak, 57,870 updates/sec to Redis is fine (5.8% utilization). But at flash game events with 10M simultaneous score submissions, direct Redis writes would spike to 10M/sec (100% Redis utilization, queueing). Kafka absorbs the spike; a consumer batch-processes updates every 100ms using Redis `ZADD` pipeline calls (pipeline 1,000 ZADDs in one roundtrip) — 57 pipeline calls vs 57,870 individual calls.
- **500 MB in-memory global leaderboard enables < 10ms reads**: Storing the leaderboard in PostgreSQL and querying `SELECT ... ORDER BY score DESC LIMIT 10` at 575 reads/sec requires an index on `score DESC`. At 10M rows × 57,870 updates/sec, index maintenance would cause significant write amplification. Redis sorted set's 500 MB footprint fits in L3 cache on modern servers, giving sub-millisecond reads without any disk I/O.

---

## Related

**Concepts used in this design**

- [Redis Internals](../../04-advanced-topics/03-internals/04-redis-internals.md)
- [Sharding](../../02-building-blocks/03-data-partitioning/01-sharding.md)
- [Consistent Hashing](../../02-building-blocks/03-data-partitioning/03-consistent-hashing.md)
- [Caching Layer](../../02-building-blocks/02-performance/01-caching-layer.md)

**Practice next**

- [Leaderboard](../01-easy/leaderboard.md)
- [Metrics Monitoring System](../03-hard/metrics-monitoring-system.md)

Start with the easy leaderboard for the sorted-set fundamentals.

**Frameworks**: [HLD Template](../../07-interview-templates/01-frameworks/01-hld-template.md) · [Capacity Estimation](../../07-interview-templates/02-cheat-sheets/02-capacity-estimation.md) · [Trade-offs Cheat Sheet](../../07-interview-templates/02-cheat-sheets/01-trade-offs-cheat-sheet.md)
