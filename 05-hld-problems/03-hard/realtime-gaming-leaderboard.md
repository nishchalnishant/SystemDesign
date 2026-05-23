---
module: 05-hld-problems
topic: Hard
status: unread
tags: [05-hld-problems, system-design, hard]
---
# Real-time Gaming Leaderboard

> Design a leaderboard for 10M concurrent players where score updates and rank queries respond in under 10ms.

---

## Problem Mindmap

```
Real-time Gaming Leaderboard
├── Why It Exists
│   ├── 10M concurrent players × scores changing every few seconds
│   ├── Rank query: "what is my global rank out of 80M players?"
│   └── SQL ORDER BY + COUNT(*) on 80M rows = seconds, not milliseconds
├── Core Data Structure: Redis Sorted Set
│   ├── ZADD leaderboard <score> <player_id> → O(log N)
│   ├── ZRANK leaderboard <player_id> → rank (0-indexed) → O(log N)
│   ├── ZREVRANK → rank from top → O(log N)
│   ├── ZRANGE leaderboard 0 99 WITHSCORES REV → top-100 → O(log N + K)
│   └── ZSCORE leaderboard <player_id> → current score → O(1)
├── Leaderboard Types
│   ├── Global → all-time scores across all players
│   ├── Time-windowed → daily/weekly/monthly (key per window)
│   │   ├── Key: leaderboard:daily:2026-05-20
│   │   └── TTL: set expiry = window end + buffer
│   ├── Friends → subset of players (store friend IDs, query only those)
│   ├── Regional → geo-sharded (APAC, NA, EU separate sorted sets)
│   └── Segmented → per game mode, per rank bracket
├── Scaling Beyond Single Redis
│   ├── Read replicas → ZRANK/ZRANGE on replicas, ZADD on primary
│   ├── Sharding by player_id → approximate global rank from shard ranks
│   ├── Redis Cluster → automatic sharding (same key stays on one node)
│   └── Write-through cache → DB as source of truth, Redis as read cache
├── Anti-Cheat
│   ├── Server-authoritative scores → client never submits score directly
│   ├── Rate limiting → max score delta per time window
│   ├── Statistical anomaly detection → score 10× above p99 → flag
│   └── Replay validation → validate game events server-side
├── Persistence
│   ├── Redis RDB/AOF → survives restart but not full failure
│   ├── Periodic DB sync → Redis → PostgreSQL every N minutes
│   └── Event sourcing → all score events in Kafka → rebuild from log
└── Scale
    ├── Fortnite: 80M MAU, 3.4M peak concurrent
    ├── PUBG: 70M players
    └── Redis ZSET: tested at 10M+ members, <1ms ops
```

---

## 1. Why Real-time Gaming Leaderboard Exists

**Question**: Fortnite has 80M registered players. A player asks: "What is my global rank right now?" How do you return that in under 10ms?

**Physical constraint**: SQL `SELECT COUNT(*) FROM scores WHERE score > :myScore` on 80M rows with B-tree index = ~50ms on SSD (full index scan). With 10M concurrent players updating scores, write lock contention makes it worse. At 10,000 score updates/sec, a write-heavy DB table cannot serve both high-throughput writes and low-latency rank queries simultaneously.

**Minimal solution**: PostgreSQL with index on score. Works until 1M players — rank query hits ~10ms. At 10M players: ~100ms. At 80M players: ~500ms. Concurrent writes cause lock contention; write amplification from index maintenance.

**Production generalization**: Redis Sorted Set (skip list + hash map) as primary leaderboard store. O(log N) for ZADD and ZRANK regardless of N. Periodic sync to PostgreSQL for durability. Read replicas scale read throughput. Time-windowed leaderboards use separate sorted sets with TTL.

---

## 2. Core Concepts

### 2.1 Redis Sorted Set Internals

Redis Sorted Set uses two data structures internally:

1. **Skip list**: enables O(log N) rank queries by traversing express lanes
2. **Hash map**: `player_id → score`, enables O(1) score lookup

```
Skip list (simplified, N=8):
Level 3: [head] ──────────────────────────────► [player-G] → NULL
Level 2: [head] ──────────► [player-C] ────────► [player-G] → NULL
Level 1: [head] ─► [A:100] → [C:250] → [E:400] → [G:600] → NULL
```

ZRANK traverses from head down levels — O(log N) average.

### 2.2 Key Redis Commands

```bash
# Add/update score — O(log N)
ZADD leaderboard 15000 "player:user-123"

# Get rank from bottom (0-indexed) — O(log N)
ZRANK leaderboard "player:user-123"           # returns 999999 → rank 1M

# Get rank from top — O(log N)
ZREVRANK leaderboard "player:user-123"        # returns 0 → #1 globally

# Get top-100 with scores — O(log N + 100)
ZRANGE leaderboard 0 99 REV WITHSCORES

# Get players in score range — O(log N + M)
ZRANGEBYSCORE leaderboard 14000 16000 WITHSCORES

# Get current score — O(1)
ZSCORE leaderboard "player:user-123"

# Increment score atomically — O(log N)
ZINCRBY leaderboard 500 "player:user-123"
```

### 2.3 Time-Windowed Leaderboards

Each time window gets its own sorted set key:

```
leaderboard:global          → all-time
leaderboard:daily:2026-05-20
leaderboard:weekly:2026-W21
leaderboard:monthly:2026-05
leaderboard:season:S12
```

**Expiry strategy**: Set TTL on daily key = seconds until midnight + 24h buffer.

```bash
# When player scores in daily window:
ZADD leaderboard:daily:2026-05-20 15000 "player:user-123"
EXPIREAT leaderboard:daily:2026-05-20 <midnight+24h unix timestamp>
```

**Score accumulation within window**: Use ZINCRBY for additive games; ZADD with NX+GT flags for "keep highest score" games.

```bash
# Keep highest score only (e.g., speedrun):
ZADD leaderboard:daily GT NX 15000 "player:user-123"
# GT = only update if new score > existing score
```

### 2.4 Friends Leaderboard

Global rank is less motivating than "rank among friends." Approach:

```
Option A: Store friend IDs in Redis Set, intersect with leaderboard
  ZRANGEBYSCORE leaderboard -inf +inf → iterate all 80M (too slow)

Option B: Maintain per-player friends sorted set (fan-out on write)
  On score update: for each friend → ZADD friends:{friendId}:leaderboard score playerId
  Friends list avg 200 → 200 ZADD per score update → ~200 × log(200) ≈ manageable

Option C: Materialized friend leaderboard (batch rebuild every 5min)
  Scheduled job: for each player, ZUNIONSTORE temp {friend1_score, friend2_score, ...}
  Good for large friend networks (>1000 friends)
```

### 2.5 Sharding for Scale

A single Redis node handles ~10M sorted set members comfortably. Beyond that:

**Option A: Functional sharding** (separate leaderboards per game mode, region)
```
shard-0: leaderboard:NA:global
shard-1: leaderboard:EU:global
shard-2: leaderboard:APAC:global
```
Global rank = approximate (sum of lower-ranked players across shards).

**Option B: Score range sharding**
```
shard-0: scores 0–999,999
shard-1: scores 1,000,000–1,999,999
...
```
Rank = exact (sum of ZCARD across all higher-scored shards). Write routing by score range.

**Option C: Read replicas** (simplest)
```
Primary: handles all ZADDs
Replica ×4: handles ZRANK, ZRANGE, ZSCORE reads
Read LB routes queries to replicas
```

### 2.6 Anti-Cheat Validation

Game servers must be authoritative — clients never submit scores directly:

```
Client → Game Server (validate events) → Score API → Redis
          ↑
          Server-authoritative: server computes score from game events
          Client cannot fake: "I killed 100 enemies in 10 seconds"
          Rate check: max score delta per game session = configurable cap
          Statistical: if score > mean + 5σ → flag for review queue
```

---

## 3. Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        Game Client                              │
│  (sends game events, NOT scores — server is authoritative)      │
└──────────────────────┬──────────────────────────────────────────┘
                       │ WebSocket / gRPC
                       ▼
┌──────────────────────────────────────────────────────────────────┐
│                    Game Server Cluster                           │
│  ├── Validates game events (kills, objectives, time)             │
│  ├── Computes score server-side                                  │
│  └── Anti-cheat: rate limit, statistical anomaly detection       │
└──────────────────────┬───────────────────────────────────────────┘
                       │ score update event
                       ▼
┌──────────────────────────────────────────────────────────────────┐
│                     Score API Service                            │
│  ├── POST /score { playerId, delta, windowKeys[] }               │
│  ├── Pipeline: ZINCRBY global + daily + weekly (atomic)          │
│  └── Publish to Kafka topic: score-events                        │
└──────────────┬────────────────────────────┬──────────────────────┘
               │                            │ async
               ▼                            ▼
┌──────────────────────┐        ┌───────────────────────┐
│   Redis Cluster      │        │      Kafka            │
│                      │        │  topic: score-events  │
│  leaderboard:global  │        └───────────┬───────────┘
│  leaderboard:daily   │                    │
│  leaderboard:weekly  │                    ▼
│  Read replicas ×4    │        ┌───────────────────────┐
└──────────────────────┘        │  DB Sync Consumer     │
               │                │  PostgreSQL (persist)  │
               ▼                └───────────────────────┘
┌──────────────────────┐
│   Leaderboard API    │        ┌───────────────────────┐
│  GET /rank/:playerId │        │  CDN / Edge Cache     │
│  GET /top100         │◄──────►│  top-100 cached 30s   │
│  GET /friends-rank   │        └───────────────────────┘
└──────────────────────┘
```

---

## 4. Real-World Usage

| Game/Platform | Players | Approach |
|--------------|---------|----------|
| Fortnite | 80M MAU, 3.4M peak concurrent | Redis Sorted Sets, regional sharding |
| PUBG | 70M players | Redis + MySQL eventual sync |
| League of Legends | 150M accounts | Time-windowed per-season leaderboards |
| Steam | 120M MAU | PostgreSQL with materialized views for top-N |
| Chess.com | 100M members | Redis for live tournament, DB for historical |

---

## 5. Trade-offs

| Decision | Option A | Option B | Notes |
|----------|----------|----------|-------|
| Storage | Redis only (fast, volatile) | Redis + DB sync (durable) | Always sync to DB — Redis can lose data |
| Time windows | Separate sorted set per window | Single set + timestamp filtering | Separate sets: faster; filtering: flexible |
| Friends rank | Fan-out on write | Fan-out on read | Fan-out on write: faster reads, more writes |
| Global sharding | Score-range shards (exact rank) | Regional shards (approx rank) | Exact rank costly to maintain at 100M+ |
| Anti-cheat depth | Rate limit only | Statistical + replay validation | Replay validation is expensive, worth it |

---

## 6. Failure Scenarios

### 6.1 Redis Primary Dies
**Symptom**: ZADD fails; score updates lost; rank reads fail.
**Fix**: Redis Sentinel / Redis Cluster auto-failover to replica (30s–60s). In-flight score updates buffered in Kafka — replay from offset. Players see stale ranks during failover window.

### 6.2 Leaderboard Drift (Redis vs DB out of sync)
**Symptom**: Redis leaderboard shows different ranks than DB truth after Redis restart.
**Fix**: Kafka consumer reprocesses `score-events` topic from last committed offset to rebuild Redis state. Reconciliation job: compare Redis ZSCORE with DB scores, patch deltas.

### 6.3 Score Injection (Cheater Submits API Call)
**Symptom**: Player bypasses game client, calls Score API directly with inflated score.
**Fix**: Score API validates JWT signed by game server. Score delta must come with signed game session token. Game server is the only issuer.

### 6.4 Thundering Herd on Season Reset
**Symptom**: Season resets at midnight — 10M players query their new rank simultaneously.
**Fix**: Stagger reset: rename old sorted set key, create new empty one. Pre-compute top-10K list into Redis string key (cached 60s). CDN caches `/top100` for 30s. Rate limit rank API.

---

## 7. Performance Numbers

| Operation | Latency | Notes |
|-----------|---------|-------|
| ZADD (score update) | ~0.1ms | O(log N), N=10M |
| ZREVRANK (global rank) | ~0.1ms | O(log N) |
| ZRANGE top-100 | ~0.5ms | O(log N + 100) |
| DB sync (Kafka consumer) | eventual, ~1s lag | Not on critical path |
| CDN top-100 hit | <5ms | Cached at edge |
| Redis Cluster write (3 shards) | ~0.3ms | 3 ZADD pipeline |
| Friends leaderboard (200 friends) | ~1ms | ZRANGE over 200-member set |
| Peak throughput (single Redis) | ~100K ZADD/sec | With pipelining |
| Redis memory for 10M members | ~1 GB | ~100 bytes/member |

---

## 8. Java Implementation

### 8.1 Score Update (Atomic Pipeline Across Windows)

```java
@Service
public class LeaderboardService {
    private final RedisTemplate<String, String> redis;

    public void updateScore(String playerId, long scoreDelta) {
        String globalKey = "leaderboard:global";
        String dailyKey  = "leaderboard:daily:" + LocalDate.now();
        String weeklyKey = "leaderboard:weekly:" + getIsoWeek();

        // Pipeline all updates atomically — single RTT to Redis
        redis.executePipelined((RedisCallback<Object>) conn -> {
            conn.zIncrBy(globalKey.getBytes(), scoreDelta, playerId.getBytes());
            conn.zIncrBy(dailyKey.getBytes(),  scoreDelta, playerId.getBytes());
            conn.zIncrBy(weeklyKey.getBytes(), scoreDelta, playerId.getBytes());

            // Set TTL on daily key if not already set (EXPIRE NX equivalent)
            long ttl = secondsUntilEndOfDay() + 86400;
            conn.expire(dailyKey.getBytes(), ttl);
            return null;
        });
    }

    public RankResult getGlobalRank(String playerId) {
        Long rank  = redis.opsForZSet().reverseRank("leaderboard:global", playerId);
        Double score = redis.opsForZSet().score("leaderboard:global", playerId);
        return new RankResult(rank != null ? rank + 1 : -1, score); // 1-indexed
    }

    public List<LeaderboardEntry> getTop100() {
        Set<TypedTuple<String>> top = redis.opsForZSet()
            .reverseRangeWithScores("leaderboard:global", 0, 99);
        return top.stream()
            .map(t -> new LeaderboardEntry(t.getValue(), t.getScore()))
            .collect(Collectors.toList());
    }
}
```

### 8.2 Anti-Cheat Rate Limiter

```java
@Component
public class AntiCheatValidator {
    private final RedisTemplate<String, String> redis;
    // Max score gain per 10-minute window
    private static final long MAX_SCORE_PER_WINDOW = 50_000;
    private static final long WINDOW_SECONDS = 600;

    public boolean isValidScoreDelta(String playerId, long delta) {
        String key = "anticheat:" + playerId + ":" + (System.currentTimeMillis() / (WINDOW_SECONDS * 1000));

        Long currentTotal = redis.opsForValue().increment(key, delta);
        redis.expire(key, WINDOW_SECONDS * 2, TimeUnit.SECONDS);

        if (currentTotal > MAX_SCORE_PER_WINDOW) {
            log.warn("Anti-cheat: player {} exceeded score rate limit: {} in window", playerId, currentTotal);
            flagForReview(playerId, delta, currentTotal);
            return false;
        }
        return true;
    }
}
```

### 8.3 Friends Leaderboard (Fan-out on Write)

```java
public void updateScoreWithFriendsFanout(String playerId, long scoreDelta, List<String> friendIds) {
    redis.executePipelined((RedisCallback<Object>) conn -> {
        byte[] playerBytes = playerId.getBytes();

        // Update global/time-windowed leaderboards
        conn.zIncrBy("leaderboard:global".getBytes(), scoreDelta, playerBytes);

        // Fan-out: update this player's entry in each friend's leaderboard
        // Friends list capped at 500 to bound write amplification
        friendIds.stream().limit(500).forEach(friendId -> {
            String friendLBKey = "leaderboard:friends:" + friendId;
            conn.zIncrBy(friendLBKey.getBytes(), scoreDelta, playerBytes);
        });
        return null;
    });
}
```

### 8.4 DB Sync Consumer (Kafka → PostgreSQL)

```java
@KafkaListener(topics = "score-events", groupId = "db-sync-consumer")
public void persistScore(ScoreEvent event) {
    // Upsert to DB — idempotent on (player_id, window_key)
    scoreRepository.upsert(
        event.getPlayerId(),
        event.getWindowKey(),
        event.getScoreDelta(),
        event.getTimestamp()
    );
}

// SQL:
// INSERT INTO scores (player_id, window_key, total_score, updated_at)
// VALUES (?, ?, ?, ?)
// ON CONFLICT (player_id, window_key)
// DO UPDATE SET total_score = scores.total_score + EXCLUDED.total_score,
//               updated_at = EXCLUDED.updated_at
```

---

## 9. Quick Revision

- **Redis Sorted Set**: skip list + hash map; ZADD/ZRANK O(log N); ZSCORE O(1)
- **Time-windowed**: separate key per window (`leaderboard:daily:2026-05-20`), set TTL
- **ZADD GT NX**: keeps highest score only — for speedrun/best-attempt games
- **Friends leaderboard**: fan-out on write (update friend's set on each score update)
- **Score sharding**: exact rank = sum ZCARD of all shards with higher score range
- **Anti-cheat**: server-authoritative score; rate limit delta per window; flag statistical outliers
- **Thundering herd on reset**: pre-compute top-N into cached string; CDN cache top-100 page

---

## 10. See Also

- `02-building-blocks/caching-layer.md` — Redis fundamentals, eviction policies
- `01-foundations/databases.md` — why SQL rank queries are slow at scale
- `09-patterns/cqrs-event-sourcing.md` — score event log for rebuild/audit
- `05-hld-problems/03-hard/metrics-monitoring-system.md` — monitoring the leaderboard system

---

## 11. Interview Questions Asked

1. **Riot Games (League of Legends)**: "Design a leaderboard for 100M players. How do you return rank in <10ms?"
2. **Epic Games**: "How does Redis Sorted Set achieve O(log N) rank queries? What data structure is it?"
3. **Google (Stadia)**: "A player claims rank 1 globally with an impossible score. How does your system detect this?"
4. **Meta**: "How do you implement a friends leaderboard where rank is computed only among a player's 200 friends?"
5. **Amazon (Twitch)**: "Redis dies and loses all leaderboard data. How do you reconstruct it?"
6. **Tencent**: "How do you shard a leaderboard sorted set across multiple Redis nodes while preserving exact global rank?"
7. **Netflix (gaming)**: "Season reset at midnight causes 10M simultaneous rank queries. How do you handle this?"
8. **Activision**: "Daily vs weekly vs all-time leaderboards — how do you efficiently maintain all three simultaneously?"
