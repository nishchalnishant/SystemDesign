---
module: 07-interview-templates
status: unread
tags: [07-interview-templates, system-design, interview-templates]
---
# Architecture by Scale: Capacity → Decision Framework

> **At each scale inflection point, a different bottleneck dominates. The architecture must match the actual bottleneck, not the imagined future one.**

---

## The Framework

Every scale-up decision follows this pattern:

```
Identify bottleneck → apply targeted solution → measure → repeat
```

Premature scaling is waste. Over-architecting for 10M users when you have 1K users adds ops complexity, cost, and bugs. The right question: **"What breaks first at this scale?"**

---

## Scale Tiers

### Tier 0: ≤ 1,000 Users — Single Server

**Bottleneck**: None yet. Simplicity is the priority.

```
                  ┌─────────────────────┐
Users ───HTTP───▶ │  Single Server       │
                  │  App + DB on same    │
                  │  machine             │
                  └─────────────────────┘
```

**Stack**:
- 1 application server (e.g. EC2 t3.medium, 2 vCPU, 4 GB RAM)
- Database on same server (PostgreSQL, SQLite)
- No cache, no CDN, no load balancer

**Capacity math**:
- 1,000 users, 10% concurrent = 100 simultaneous users
- 100 req/s @ 10ms avg latency = 1 req/server-thread
- Single Postgres easily handles 100 QPS with default config

**What breaks first**: RAM (if you add too many in-memory objects) or DB connection limit (default 100 connections in Postgres).

**Interview answer**: "For 1K users, deploy a single server. No need for distributed systems. Optimize only when you have data showing a bottleneck."

---

### Tier 1: 10,000 Users — Separate Database

**Bottleneck**: CPU contention between app and DB on same machine. DB needs dedicated I/O.

```
                  ┌───────────────┐     ┌───────────────────┐
Users ───HTTP───▶ │  App Server   │────▶│  Database Server  │
                  │  (EC2)        │     │  (RDS PostgreSQL) │
                  └───────────────┘     └───────────────────┘
```

**Key changes**:
- Separate database server — dedicated disk IOPS and memory for buffer pool
- App server can scale independently of DB
- Add basic monitoring: CPU, memory, DB connection count, slow query log

**Capacity math**:
- 10K users, 10% concurrent = 1,000 simultaneous users
- 1,000 req/s @ 20ms avg = 20 threads needed
- Single DB server: PostgreSQL handles 500–2,000 QPS for simple queries

**What breaks first**: DB connections (each app thread holds one connection). At 10K users, connection pool pressure starts.

**Solution**: PgBouncer connection pooler in transaction mode. Allows 1,000 app threads to share 50–100 actual DB connections.

---

### Tier 2: 100,000 Users — Cache + CDN

**Bottleneck**: Read-heavy load overwhelms DB. 80% of reads hit the same popular data.

```
                             ┌──────────────────┐
                        ┌───▶│  Redis Cache      │
Users ──CDN──▶ LB ─────┤    └──────────────────┘
                        └───▶ App Servers (x3) ──▶ Primary DB
                                                        │
                                                   Read Replica
```

**Key changes**:
1. **CDN** (CloudFront, Fastly): serve static assets (images, JS, CSS) from edge. Takes 60–70% of bandwidth off origin.
2. **Redis cache**: cache hot reads. User profiles, product catalog, session data.
3. **Read replica**: offload read traffic from primary DB. Eventual consistency acceptable for most reads.
4. **Load balancer**: distribute across multiple app servers.

**Capacity math**:
- 100K users, 10% concurrent = 10,000 simultaneous users
- 10,000 req/s — single DB cannot handle this for complex queries
- Cache hit rate 80%: 8,000 req/s served from Redis (sub-millisecond), 2,000 req/s to DB

**Cache key design**:
```
user:{user_id}:profile     → TTL 300s
product:{product_id}       → TTL 600s
feed:{user_id}:page:{n}    → TTL 60s (stale ok for social feeds)
```

**What breaks first**: Cache invalidation bugs (stale data after writes). Session affinity problems if app servers have local state.

**Critical rule**: App servers must be stateless. All state in Redis or DB. This enables horizontal scaling.

---

### Tier 3: 1,000,000 Users — Async + Sharding

**Bottleneck**: Write throughput exceeds single DB primary capacity. Synchronous processing creates latency spikes.

```
                                ┌──── Redis Cache
Users ──CDN──▶ API Gateway ────┤
                                └──── App Servers ──▶ Kafka ──▶ Workers
                                           │
                                    DB Router (shard key)
                                    ┌──────┴──────┐
                                  Shard 0      Shard 1
                                (user 0-500K) (user 500K-1M)
```

**Key changes**:
1. **Message queue (Kafka/SQS)**: decouple slow operations (email, push notification, ML inference, image resizing) from request path. Return 202 Accepted, process asynchronously.
2. **Database sharding**: horizontal partition by shard key (user_id % N). Each shard is an independent DB.
3. **API Gateway**: rate limiting, auth token validation, routing — extracted from app servers.
4. **Search service**: Elasticsearch or OpenSearch for full-text search (DB LIKE queries won't scale).

**Capacity math**:
- 1M users, 10% concurrent = 100,000 simultaneous
- 100,000 req/s peak — requires ~50 app servers at 2,000 req/s each
- Write QPS: 10,000 writes/s — single PostgreSQL maxes at ~5,000 writes/s (depends on write size)
- 2 shards: each handles 5,000 writes/s ✓

**Sharding trade-offs**:

| Concern | Impact |
|---------|--------|
| Cross-shard queries | Must scatter-gather or denormalize |
| Resharding | Expensive — requires data migration |
| Joins | Cannot join across shards in DB |
| Hotspot shards | If shard key is skewed (celebrity users) |

**Shard key selection**:
- `user_id`: uniform distribution, user data co-located ✓
- `created_at`: time-based — recent shard takes all writes ✗ (hotspot)
- `geography`: uneven regional growth ✗ (hotspot risk)

**What breaks first**: Hot shards from power users. Mitigate with consistent hashing or logical shard routing.

---

### Tier 4: 10,000,000 Users — Full Distributed Systems

**Bottleneck**: Global latency (serving users in other continents), hot data (viral content), operational complexity.

```
                    ┌── US-East ──▶ Regional DB cluster
Users ── DNS GEO ──┤
                    └── EU-West ──▶ Regional DB cluster
                         │
                    Global CDN (99% static, 1% dynamic)
                    Message bus (Kafka, global partition)
                    Microservices (domain-isolated teams)
```

**Key changes**:
1. **Multi-region deployment**: data replicated across regions. Users routed to nearest region via GeoDNS.
2. **Microservices**: decompose monolith by domain (user service, order service, inventory service). Each team deploys independently. Each service has its own database (database per service pattern).
3. **Event-driven architecture**: services communicate via events (Kafka), not synchronous calls. Decouples failure domains.
4. **Global content delivery**: CDN edge nodes cache content at 200+ PoPs worldwide. Dynamic content cached with surrogate keys.
5. **Data tiering**: hot data in Redis, warm data in DB, cold data in S3/Glacier.

**Capacity math**:
- 10M users, 5% concurrent peak = 500,000 simultaneous users
- 500,000 req/s — requires ~250 app servers (or ~50 with aggressive async)
- DB write QPS: 50,000/s — requires 10+ shards or distributed DB (CockroachDB, Spanner)
- Network: 500K req/s × 10KB avg = 5 GB/s — CDN handles most; origin sees <100 MB/s

**Architecture decisions forced at 10M**:

| Problem | Solution | Trade-off |
|---------|----------|-----------|
| Cross-region write conflicts | Active-Passive (one write region) or Active-Active + CRDT | Active-Active adds conflict resolution complexity |
| Hot keys (viral content) | Local cache per pod + probabilistic refresh | Slight staleness |
| Service discovery | Kubernetes + service mesh (Istio/Envoy) | Ops complexity |
| Distributed tracing | Jaeger/Zipkin with sampling | Storage cost |
| Schema changes | Blue-green deploy, additive-only migrations | Slower rollout |

---

## Decision Framework: What Changes at Each Tier

| Scale | Primary Bottleneck | Solution | New Complexity |
|-------|-------------------|----------|----------------|
| 1K users | Nothing | Single server | None |
| 10K users | App+DB CPU contention | Separate DB | DB ops |
| 100K users | Read QPS, bandwidth | Cache + CDN + read replica | Cache invalidation |
| 1M users | Write QPS, slow ops | Sharding + async (Kafka) | Cross-shard queries |
| 10M users | Latency (global), team scale | Multi-region + microservices | Distributed consistency |

---

## Component Selection by Scale

### Database

| Scale | Choice | Why |
|-------|--------|-----|
| 1K–100K | PostgreSQL single server | Simple, correct, ACID |
| 100K–1M | PostgreSQL + read replicas + PgBouncer | Reads scale out, writes to primary |
| 1M–10M | Sharded PostgreSQL or Cassandra/DynamoDB | Write scale-out |
| 10M+ | Globally distributed (Spanner, CockroachDB) or Cassandra multi-DC | Multi-region writes |

### Cache

| Scale | Choice | Why |
|-------|--------|-----|
| 1K–10K | No cache (or local in-process) | Overhead not justified |
| 10K–1M | Redis single node | Simple, fast, TTL support |
| 1M+ | Redis Cluster | Hash-slot sharding, 16384 slots |

### Message Queue

| Scale | Choice | Why |
|-------|--------|-----|
| 1K–100K | None (synchronous ok) | No need yet |
| 100K–1M | SQS or RabbitMQ | Simple async, no replay needed |
| 1M+ | Kafka | Log-based, replay, ordering per partition |

### Search

| Scale | Choice | Why |
|-------|--------|-----|
| 1K–100K | DB full-text (PostgreSQL tsvector) | Good enough |
| 100K+ | Elasticsearch / OpenSearch | Dedicated scoring, sharded |

---

## Interview Script

**If asked "Design X for 10M users":**

1. **Estimate first**: "10M users, 10% concurrent = 1M simultaneous. Assume 10 req/s per active user = 10M req/s peak." (Then adjust — 10 req/s is high, maybe 1 req/s = 1M req/s.)

2. **State the bottleneck**: "At 1M req/s, the write path is the bottleneck. Reads can be cached. Writes need sharding or a write-scalable DB."

3. **Evolve the architecture**: "I'd start with PostgreSQL + read replicas for the first 100K users. At 1M, add Redis cache and Kafka for async. At 10M, introduce sharding or switch to Cassandra for write-heavy tables."

4. **Name the trade-off**: "Sharding forces cross-shard queries to scatter-gather, which adds latency. I'd denormalize hot join paths and pre-aggregate."

---

## Capacity Estimation Quick Reference

```
Assume 10% of users are active at any given time.
Assume each active user makes 1 request/second (typical browsing).
Peak = 3-5x average.

Users   → Active  → Req/s (avg) → Req/s (peak)
1K      → 100     → 100         → 300-500
10K     → 1K      → 1K          → 3K-5K
100K    → 10K     → 10K         → 30K-50K
1M      → 100K    → 100K        → 300K-500K
10M     → 1M      → 1M          → 3M-5M

Single PostgreSQL:    ~2K-5K write QPS (simple inserts)
Single Redis:         ~100K-1M ops/s (in-memory, simple commands)
Single Kafka broker:  ~100K-1M messages/s (batched, sequential I/O)
Single app server:    ~1K-10K req/s (depends on work per request)
```

---

## See Also

- **Database selection**: [07-interview-templates/database-selection-tree.md](database-selection-tree.md)
- **Amazon HLD guide**: [amazon-hld-guide.md](amazon-hld-guide.md)
- **HLD cheat sheet**: [hld-cheat-sheet.md](hld-cheat-sheet.md)
- **Scaling deep-dive**: [03-scaling/](../03-scaling/)
