# #10 Real time gaming leaderboard

Below is a complete, time-boxed, interview-ready (1 hour) answer for designing a Real-time Gaming Leaderboard (think global/top-N leaderboards, per-region leaderboards, friend/room leaderboards, seasonal ladders). It follows your pattern: clarify → FR/NFR → APIs & data model → high-level design → deep dives (ranking, sharding, consistency, anti-cheat) → capacity math → ops/security → trade-offs & wrap-up. Use this as a script you can speak during an interview; minutes show what to say and when.

***

## 0 – 5 min — Problem recap, scope & assumptions

<br>

Quickly restate problem and align scope.

<br>

Goal: Build a real-time leaderboard system that ingests player score updates and presents sorted leaderboards with low latency. Must support global leaderboards, per-region and per-game-mode leaderboards, friend/party leaderboards, top-K queries, rank queries for a single player, pagination, seasonal resets, and anti-cheat measures.

<br>

Key SLA / example assumptions (adjustable):

* 50M monthly active players (MAU), 5M daily active (DAU).
* Peak concurrent players submitting updates: 200k updates/sec.
* Read traffic (leaderboard pages, rank lookups): 500k QPS peak (reads >> writes).
* Leaderboard query latency target: P95 < 50 ms.
* Freshness: updates visible in leaderboards within ≤ 1–2 s for “near-real-time” modes; configurable for slower seasonal processing.
* Retention: keep raw score events 30 days for audit + anti-cheat.

<br>

Clarify whether exact ranking ties are broken deterministically (e.g., timestamp) — assume deterministic tie-breaker (higher timestamp older wins) unless told otherwise.

***

## 5 – 15 min — Functional & Non-Functional Requirements

<br>

### Functional Requirements (Must / Should / Nice)

<br>

Must

1. Submit score: client/servers submit updates {player\_id, leaderboard\_id, score, metadata, ts}.
2. Get top K: GET /leaderboard/{id}/top?k=100 returns top-K entries with ranks and metadata.
3. Get player rank: GET /leaderboard/{id}/player/{player\_id} → rank, score, neighbors.
4. Pagination: page through leaderboard results.
5. Friend / party leaderboards: filtered leaderboards for a player’s social graph or party.
6. Time windows & seasons: support rolling/seasonal leaderboards with reset/decay policies.
7. Atomic updates & tie-breaking: consistent rank semantics on score updates.
8. Anti-cheat & validation: flag suspicious updates; allow rollbacks.
9. Audit & history: keep event log for reconciliation & disputes.
10. Notifications: webhooks/push when user crosses thresholds (top-100, new personal best).

<br>

Should

* Support multiple scoring types (max score, cumulative points, ELO).
* Support leaderboard merges and derived leaderboards (e.g., per-region aggregated into global).
* Provide admin tools to adjust ranks, correct fraud, and run seasonal resets.

<br>

Nice-to-have

* Real-time streaming for client UIs (WebSocket/SSE) to push rank changes; ML-based anomaly detection.

<br>

### Non-Functional Requirements

* Latency: leaderboard queries P95 < 50 ms; writes visible within ≤ 1–2 s (configurable).
* Throughput: handle 200k writes/sec and 500k reads/sec peaks, scale horizontally.
* Availability: 99.95% read availability; writes highly available with eventual consistency across regions.
* Durability: persistent event log and snapshots for recovery.
* Scalability: support millions of leaderboards across many games/modes and millions of players.
* Consistency: strong ranking consistency within a partition (leaderboard or shard), eventual across replicated shards.
* Security: auth for updates, anti-spoofing, rate limiting, audit logs.
* Cost: optimize memory for hot leaderboards; tier cold data.
* Observability: metrics for update latency, staleness, fraud flags, read/write QPS.

<br>

Acceptance criteria examples: top-K P95 < 50ms; update-to-view <= 2s for near-real-time leaderboards; false-positive fraud flags < 1%.

***

## 15 – 25 min — APIs, data model & acceptance schema

<br>

#### External APIs (simple)

```
POST   /score      {player_id, leaderboard_id, score, metadata, ts, signature}
GET    /leaderboard/{id}/top?k=100&cursor=...
GET    /leaderboard/{id}/player/{player_id}?neighbors=5
GET    /leaderboard/{id}/range?start=100&limit=50
GET    /leaderboard/{id}/friends/{player_id}?k=50
POST   /leaderboard/{id}/admin/reset  (admin)
POST   /leaderboard/{id}/admin/correct  (admin correction)
WS     /leaderboard/{id}/subscribe  (optional push updates)
```

#### Data model

* ScoreEvent: {event\_id, player\_id, leaderboard\_id, score, ts, meta} stored in append-only event log (Kafka).
* LeaderboardEntry (materialized): {leaderboard\_id, player\_id, score, tie\_breaker\_ts, rank, extra\_meta} — stored in fast serving store (in-memory sorted structure + persisted shard).
* Indexes: player → position mapping for fast rank lookup, and sorted index for ranges.

<br>

Acceptance semantics: For “max” leaderboards keep max(score) per player; for cumulative, increment stored value. Tie-breaker: lower tie\_breaker\_ts (earlier) wins or configurable.

***

## 25 – 40 min — High-level architecture & data flow

```
Clients
  |
API Gateway (auth, rate-limit)
  |
Ingest Frontend (stateless)  ---> Event Bus (Kafka / Pulsar)  ---> Stream Processing
  |                                     |                              |
  |                                     +----> Raw Event Sink (S3)    |
  |                                                                    |
  +----> Real-time Serving Layer <-------------------------------------+
             (Sharded In-Memory Indexes)
             (e.g., Redis Sorted Sets / Custom C++ service / Aerospike)
             |
       Read API / Cache (CDN for top pages) 
             |
       WebSocket Push Service (optional)
```

Components

1. API Gateway / Ingest Frontend
   * Validate request (auth, signature), rate-limit, quick sanity checks (score ranges). Reject obviously invalid updates.
   * Write canonical ScoreEvent to Event Bus with partitioning key = leaderboard\_id (ensures ordering per leaderboard).
2. Event Bus (Kafka/Pulsar)
   * Durable, partitioned stream. Partition by leaderboard\_id % N so each leaderboard’s events hit same partition; ensures ordered processing per leaderboard.
   * Short retention (hours-days) for replay; long-term archive to S3 for audit.
3. Stream Processor (Flink/Beam)
   * Consumes events per partition; applies dedupe, anti-cheat heuristics, attribute scoring policy (max vs cumulative), computes delta, and writes updates to Serving Layer.
   * Maintains state (player’s current score) in windowed state store for correctness and de-duplication.
4. Serving Layer (materialized leaderboards)
   * In-memory sorted structures sharded by leaderboard (or leaderboard shard). Options: Redis Sorted Sets, custom in-memory service backed by persistent storage (RocksDB + memory indices), or a combination.
   * Each shard maintains score -> player sorted index and player -> score map for O(logN) rank updates and O(logN) rank lookups.
   * Persist periodic snapshots and write-ahead logs for recovery; also sync to cold store for durability.
5. Read APIs
   * Serve reads directly from Serving Layer; use read replicas for heavy read traffic.
   * Use caches/CDN for global top-K pages (top 100) served frequently.
6. Anti-cheat / Fraud Detection
   * Stream processor applies rules: impossible jumps, geo/ip anomalies, rate anomalies, emulator signatures; suspicious events go to quarantine stream for manual/automated review.
   * Admin workflows to rollback fraudulent updates.
7. Push / Notification Service
   * Optional: pushes to subscribers via WebSocket or SSE for live UI updates.
8. Batch / Recompute Jobs
   * Periodic full recompute from event archives for replay after fixes or to reconcile anomalies (daily/weekly jobs).

***

## 40 – 50 min — Core algorithms & deep dives

<br>

#### Ranking & Data structures

* Sorted Set per shard: balanced tree (skip list, B-tree) or binary heap with indexes; Redis Sorted Set (ZSET) is a good off-the-shelf.
* Operations
  * updateScore(player, newScore): check existing score; if update needed, update sorted set (O(log N)) and update player->score map. Then compute new rank (rank = zrank) and optionally notify player/peers.
  * getTopK(k): range query on sorted set tail/head O(log N + k).
  * getRank(player): O(log N) via index.
* Sharding
  * Each leaderboard is assigned to a shard; heavy leaderboards (millions entries) can be range-sharded by score ranges or by hash of player\_id with a merge-step for global top-K.
  * Global top-K across many shards: maintain per-shard top-K and merge them (k-way merge) for a final top-K—cheap since k is small (e.g., 100). Keep cached global top-K refreshed frequently.

<br>

#### Update semantics & concurrency

* Per-leaderboard partitioning ensures ordered processing of updates for a single leaderboard.
* Exactly-once / idempotency: dedupe via event\_id or (player\_id, ts) and stream-processing checkpointing; ensure processor commits offsets only after durable update to Serving Layer or use transactional write (two-phase commit) patterns. Flink + Kafka EOS can help.
* Atomicity: update to Serving Layer must atomically update sorted set + inverted index + persist WAL. Use transactions or carefully ordered writes with recovery.

<br>

#### Friend leaderboards & filters

* For friends, retrieving top among friend-set: maintain per-player friend lists in cache; when querying, fetch friend scores (multi-get) and sort client-side or precompute friend leaderboard for frequent players. For large friend lists, stream processor can maintain a per-player small priority queue of top friends.

<br>

#### Seasonal resets, TTLs & decay

* Seasonal leaderboards: on season end, freeze leaderboard snapshot, export final stats, reset per-season storage, and optionally seed new season with initial weights.
* Score decay (ELO/time decay): stream processor applies decay transformations (periodic job) to stored scores.

<br>

#### Anti-cheat & rollback

* Flagging: rules in stream processor produce flaggedEvent stream. Admin tools to approve/reject flags.
* Rollback: to revert a fraudulent update, either subtract delta (if stored) or replay events from archive excluding flagged events and recompute leaderboard (cold recompute). Keep write-ahead logs to support partial rewinds.

***

## 50 – 55 min — Back-of-the-envelope capacity & sizing

<br>

Use interviewer numbers if provided; here’s a sample sizing example.

<br>

Assumptions

* Writes: 200k updates/sec peak.
* Reads: 500k QPS peak (mostly for top-K pages & rank lookups).
* Avg leaderboard size: many are small (100s), a few are large (millions). Hot leaderboards: 10k leaderboards with heavy traffic.

<br>

Event bus (Kafka)

* Each event \~300 bytes → 200k × 300 B = 60 MB/s ≈ 518 Mbps ingest. With replication factor 3 → \~1.56 Gbps sustained. Plan partitions = 2000+ for parallelism.

<br>

Stream processors

* Scale workers to process 200k eps with stateful keyed parallelism; number depends on per-partition throughput—maybe dozens to a few hundred Flink task managers depending on CPU/IO.

<br>

Serving layer

* Memory for hot leaderboards:
  * Suppose top 10k hot leaderboards average 1M players each (worst-case): storing each entry (player\_id + score + metadata) \~ 32 bytes (optimistic) → 1M × 32 B = 32 MB per leaderboard → 10k × 32 MB = 320 GB RAM.
  * Realistic: mix of sizes; use Redis cluster with \~500 GB–1 TB RAM for hot sets, and persist rest to disk-backed stores.
* Read replicas: scale to serve 500k QPS via many read replicas and CDN for cached top-K.

<br>

Persistence

* Raw event archival: 200k eps × 300 B = 60 MB/s → per day ≈ 5.2 TB/day → 30-day = \~156 TB in S3 (choose compression). Plan cost & lifecycle.

<br>

Networking & IO

* DataNodes and serving nodes need high network bandwidth (10–100 Gbps) per rack; partition leader placement and client proximity for latency.

***

## 55 – 58 min — Observability, ops, security & testing

<br>

Monitoring

* Metrics: write/s read/s, ingestion lag (Kafka lag), stream process latency, serving layer P95/P99 latency, cache hit ratio, leaderboards hotness, fraud flag rate, snapshot/restore latency.

<br>

Alerts

* High processing lag, serving errors, spike in flagged events, large divergence between cached top-K and merged top-K.

<br>

Testing

* Load tests with synthetic updates & reads, chaos testing (kill stream processors, replays), correctness tests with known sequences, anti-cheat simulation.

<br>

Security

* Auth & signature on updates to avoid spoofing (JWT + HMAC), rate-limiting to prevent spammy updates, ACLs for admin ops. Encrypt event archives at rest and in transit.

<br>

Operational playbooks

* Recompute leaderboard from archive, emergency rollback, scaling add shard, tuning top-K cache durations.

***

## 58 – 60 min — Trade-offs, evolution & summary (wrap-up)

<br>

### Key trade-offs

* Memory (in-memory sorted sets) vs Cost: keeping entire large leaderboards in RAM gives fastest queries but is expensive. Hybrid: keep top-K in RAM, cold parts on disk, and use k-way merge for global rankings.
* Strong ordering & low latency vs throughput: per-leaderboard partitioning ensures ordering but can create hotspots if one leaderboard is huge; mitigate with sub-sharding and top-K caches.
* Real-time accuracy vs cost: real-time exact ranks are expensive—provide near-real-time (1–2s) and offer eventual strong correctness via periodic reconciliation.
* Anti-cheat complexity vs UX: aggressive blocking reduces fraud but risks false positives—provide quarantine and human review.

<br>

### Evolution path

1. MVP: API ingest → Kafka → single-stream processor → Redis ZSET per leaderboard + read APIs + top-K cache.
2. Add: sharding for very large leaderboards, stream-based anti-cheat, friend leaderboard optimization, WebSocket push.
3. Add: cross-region replication, global top-K merging with periodic refresh, ML-based fraud detection, admin tools for rollbacks.

<br>

### One-line summary

<br>

Partition events by leaderboard, process them in a stateful stream to compute and validate score deltas, materialize sorted leaderboards in a sharded, memory-optimized serving layer (top-K in RAM + cold storage for tails), and provide low-latency read APIs with caching and admin tooling for fraud detection and reconciliation — balancing latency, correctness and cost.

***

If you’d like, I can now:

* convert this into a 5-slide interview deck for quick delivery, or
* produce a sequence diagram for submitScore -> stream -> serving -> read flow, or
* run capacity math tuned to _your_ expected writes/sec, reads/sec, and average leaderboard sizes.

<br>

Which would you like next?
