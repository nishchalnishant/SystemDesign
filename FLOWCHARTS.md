> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Visual flowcharts for every major topic in the repo — designed for active recall and pre-interview mental rehearsal.
>
> **Key topics:**
> - ASCII-style flowcharts for all 9 repo sections: foundations, building blocks, scaling, advanced topics, HLD problems, LLD, patterns, templates, and reference
> - Each chart shows the internal structure of a topic file (e.g., CAP → PACELC → Consistency Models → Latency Numbers)
> - Covers 50+ individual topic flowcharts: networking stack, DB internals, Kafka consumer groups, Saga pattern, LLD class hierarchies
> - Designed as a "predict then verify" study technique — glance before reading, redraw from memory after
> - Includes HLD problem flowcharts showing architecture layers for URL Shortener, Chat System, Stock Exchange, etc.
>
> **Key takeaway:** Use this file before interviews — if you can mentally redraw these flowcharts, you can explain any topic clearly under pressure.

---
module: root
status: unread
tags: [root, system-design, flowcharts]
---
# System Design Repository — Topic Flowcharts for Recall

> How to use: Glance before reading a topic to predict structure. After reading, close and redraw from memory. Before an interview, only redraw — if you can draw it, you can explain it.

---

## 01 — FOUNDATIONS

---

### Foundations › Fundamentals

```
01-foundations/fundamentals.md
│
├── CAP Theorem                → Consistency + Availability + Partition Tolerance; pick 2; P always happens → choose C or A
├── PACELC                     → Extends CAP: even without partition, choose Latency vs Consistency (DynamoDB = EL; HBase = EC)
├── Consistency Models         → Strong → Sequential → Causal → Eventual; stronger = higher latency
├── Latency Numbers            → L1 cache 1ns; RAM 100ns; SSD 100µs; network round-trip 1ms; disk seek 10ms; cross-DC 150ms
├── Throughput vs Latency      → Throughput = requests/sec; Latency = time for one; optimizing one often hurts the other
└── Back-of-Envelope           → 1M QPS = ~12 req/ms; 1TB/day at 1KB/record = ~10M records/day; 100ms SLA → 3 hops max at 30ms each
```

---

### Foundations › Networking

```
01-foundations/networking.md
│
├── TCP vs UDP                 → TCP: ordered, reliable, 3-way handshake; UDP: best-effort, lower overhead; video/DNS = UDP
├── HTTP/1.1 vs HTTP/2 vs HTTP/3 → 1.1: head-of-line blocking; H2: multiplexed streams; H3: QUIC (UDP-based, 0-RTT)
├── TLS Handshake              → ClientHello → ServerHello + cert → key exchange → session key; adds 1-2 RTT
├── DNS Resolution             → Browser → OS cache → Resolver → Root → TLD → Authoritative; TTL controls staleness
├── WebSocket                  → Upgrade from HTTP; full-duplex; persistent; chat/live feeds; server can push
└── Long Polling vs SSE        → Long poll: client hangs until data; SSE: server pushes text stream (unidirectional)
```

---

### Foundations › Databases

```
01-foundations/databases.md
│
├── SQL vs NoSQL               → SQL: ACID, joins, schema; NoSQL: flexible schema, horizontal scale; choose by access pattern
├── ACID                       → Atomicity (all-or-nothing), Consistency (constraints hold), Isolation (concurrent = serial), Durability (crash-safe)
├── BASE                       → Basically Available, Soft state, Eventually consistent — NoSQL trade-off
├── B-tree                     → Balanced tree; O(log n) reads and writes; used in RDBMS indexes; good for range queries
├── LSM Tree                   → Log-Structured Merge; writes to memtable → immutable SSTable → compaction; great write throughput, higher read cost
├── Index Types                → B-tree (range), Hash (equality), Inverted (text search), GiST (geo), Covering (include cols)
└── Query Optimization         → Use EXPLAIN; composite index column order matters; avoid SELECT *; N+1 = use JOIN or batch
```

---

### Building Blocks › Caching Layer

```
02-building-blocks/caching-layer.md
│
├── Cache-Aside (Lazy)         → App reads cache; on miss, reads DB, writes to cache; cache can be stale; most common
├── Write-Through              → Write to cache AND DB synchronously; consistent but slow writes
├── Write-Back (Write-Behind)  → Write to cache; async flush to DB; fast writes, risk of data loss
├── Read-Through               → Cache sits in front; cache fetches from DB on miss; simpler app logic
├── Cache Stampede             → Many requests miss simultaneously, overload DB; fix: mutex lock or probabilistic early expiry
├── TTL Strategy               → Short TTL = fresh but more DB load; long TTL = stale but less load; match to data change frequency
└── CDN                        → Edge PoPs cache static/dynamic content near users; origin pull (lazy) vs push (proactive); invalidation = purge API
```

---

### Foundations › Security

```
01-foundations/security.md
│
├── AuthN vs AuthZ             → AuthN: who are you (identity); AuthZ: what can you do (permission)
├── OAuth2 Flows               → Auth Code (web apps, most secure); Client Credentials (M2M); Implicit (deprecated); PKCE (mobile)
├── JWT                        → Header.Payload.Signature; stateless; verify with public key; never store secrets in payload
├── API Key vs Session         → API key: simple, long-lived, no refresh; Session: server-side state, more control
├── mTLS                       → Both client and server present certs; service mesh uses this for inter-service auth
├── Rate Limit Abuse           → IP-based, user-based, endpoint-based; return 429; include Retry-After header
└── OWASP Top 10               → Injection, Broken Auth, XSS, IDOR, Misconfiguration, Cryptographic failure — know top 5 for interviews
```

---

## 02 — BUILDING BLOCKS

---

### Building Blocks › Load Balancer

```
02-building-blocks/load-balancers.md
│
├── L4 vs L7                   → L4: TCP/UDP (IP + port only, fast); L7: HTTP (can inspect headers, cookies, URL path)
├── Algorithms                 → Round-robin (equal servers), Least-connections (unequal work), IP-hash (session stickiness), Weighted
├── Health Checks              → Active (LB polls /health) vs Passive (observe failed responses); remove unhealthy nodes
├── Sticky Sessions            → Session affinity; needed if server stores state locally; breaks horizontal scale
└── Hardware vs Software       → HAProxy/Nginx = software (cheap, flexible); F5 = hardware (fast, expensive); AWS ALB/NLB = managed
```

---

### Building Blocks › API Gateway

```
02-building-blocks/api-gateway.md
│
├── Responsibilities           → Auth, rate limiting, SSL termination, routing, request transformation, response aggregation
├── Fan-out Pattern            → One client request → multiple backend calls → aggregate response; add timeout + partial failure handling
├── Circuit Breaker            → Open (fail fast) → Half-Open (probe) → Closed (normal); Hystrix/Resilience4j
├── Protocol Translation       → REST → gRPC internally; HTTP/1.1 → HTTP/2 upstream
└── vs Reverse Proxy           → Gateway: app-layer, aware of APIs; Reverse proxy: dumber, forward traffic
```

---

### Building Blocks › Caching Layer

```
02-building-blocks/caching-layer.md
│
├── Redis Data Structures      → String (simple KV), List (queue), Hash (object fields), Set (unique), ZSet (sorted set = leaderboard)
├── Eviction Policies          → LRU (least recently used), LFU (least frequently used), TTL expiry, allkeys-lru
├── Cache Stampede Fix         → Mutex lock on miss; probabilistic early expiry; background refresh
├── Cache Warming              → Pre-populate on startup from DB; avoids cold start thundering herd
├── Redis Cluster              → 16384 hash slots; node handles subset; gossip protocol for topology
└── Memcached vs Redis         → Memcached: simple KV, multi-threaded; Redis: rich types, persistence, pub-sub, clustering
```

---

### Building Blocks › Message Brokers

```
02-building-blocks/message-brokers.md
│
├── Kafka Architecture         → Topic → Partitions → Replicas; Producer → Broker → Consumer Group; offset = position
├── Delivery Guarantees        → At-most-once (no retry), At-least-once (retry = dupes), Exactly-once (idempotent producer + transactions)
├── Pub-Sub vs Queue           → Pub-sub: all subscribers get message (Kafka, SNS); Queue: one consumer gets it (SQS, RabbitMQ)
├── Fan-out                    → SNS → multiple SQS queues; decouples publisher from N subscribers
├── Dead Letter Queue (DLQ)    → Failed messages after N retries go here; inspect + replay; never silently drop
└── Backpressure               → Consumer slower than producer; bounded queue → apply upstream pressure; circuit breaker
```

---

### Building Blocks › Sharding

```
02-building-blocks/sharding.md
│
├── Range Sharding             → Shard by key range (A-M, N-Z); simple, good for range queries; hotspot if data skewed
├── Hash Sharding              → hash(key) % N shards; even distribution; no range queries; resharding = pain
├── Consistent Hashing         → Hash ring; add/remove node = only adjacent keys move; virtual nodes for balance
├── Directory Sharding         → Lookup service maps key → shard; flexible; lookup service = SPOF unless replicated
├── Hotspot Prevention         → Salt key (append random suffix); scatter writes; celebrity problem in social graphs
└── Resharding                 → Blue-green DB migration; dual-write; backfill; cut over; rollback plan required
```

---

### Building Blocks › Replication

```
02-building-blocks/replication.md
│
├── Leader-Follower            → All writes to leader; followers replicate async; read from follower = stale possible
├── Multi-Leader               → Multiple writers; conflict resolution needed (LWW, CRDTs, custom); cross-DC use case
├── Leaderless (Dynamo-style)  → Write to W nodes, read from R nodes; W+R > N = quorum; sloppy quorum for availability
├── Sync vs Async Replication  → Sync: strong consistency, slow writes; Async: fast writes, data loss on crash
├── Replication Lag            → Read-after-write requires reading from leader or tracking log position
└── Failover                   → Leader dies → elect new leader; fencing tokens prevent split-brain; Raft handles this
```

---

### Building Blocks › Rate Limiting

```
02-building-blocks/rate-limiting.md
│
├── Token Bucket               → Tokens refill at fixed rate; burst allowed up to bucket size; most flexible
├── Leaky Bucket               → Requests drain at fixed rate; smooth output; no burst; good for downstream protection
├── Fixed Window               → Count requests per window (1-min buckets); boundary burst problem
├── Sliding Window Log         → Exact tracking; high memory per user; accurate
├── Sliding Window Counter     → Approximate using two fixed windows weighted; memory-efficient + accurate
├── Redis Implementation       → INCR + EXPIRE atomic; or Lua script for sliding window; or Redis Cell module
└── Distributed Rate Limiting  → Per-node: fast but inaccurate; Centralized Redis: accurate but network hop; Gossip: eventual
```

---

### Building Blocks › Distributed Locks

```
02-building-blocks/distributed-locks.md
│
├── Use Cases                  → Inventory reservation, cron dedup, leader election, double-spend prevention
├── Redis SETNX                → SET key value NX EX ttl; atomic; but single Redis node = SPOF
├── Redlock                    → Acquire lock on N/2+1 Redis nodes; majority quorum; drift-aware TTL; Antirez original
├── Fencing Token              → Monotonically increasing token with each lock; storage validates token > last seen
├── ZooKeeper Locks            → Ephemeral sequential znodes; watch previous znode; fair queue semantics
└── Risks                      → GC pause > TTL → lock expires while held; always use fencing tokens for correctness
```

---

### Building Blocks › Service Discovery

```
02-building-blocks/service-discovery.md
│
├── Client-Side Discovery      → Client queries registry (Eureka), picks instance, calls directly; client is load-aware
├── Server-Side Discovery      → Client calls LB/gateway; gateway queries registry; client is dumb
├── Consul                     → Health-check + KV store + service mesh; DNS or HTTP API; multi-DC aware
├── Kubernetes DNS             → Service → ClusterIP → kube-dns; Pods register via Endpoints object
└── Health Check               → TCP check, HTTP /health, TTL heartbeat; deregister on failure
```

---

### Building Blocks › Bloom Filter

```
02-building-blocks/bloom-filter.md
│
├── What it does               → Probabilistic set membership: "definitely not in set" OR "probably in set"
├── False Positives Only       → Can say "in set" when not (FP); NEVER says "not in set" when it is (no FN)
├── Structure                  → Bit array + k hash functions; add: set k bits; query: check k bits
├── Space Efficiency           → 10 bits/element gives ~1% FP rate; vs storing all elements = huge savings
├── Use Cases                  → Web crawler URL dedup; cache miss reduction (check BF before DB); CDN negative cache
└── Limitation                 → Cannot remove elements (use Counting Bloom Filter); FP rate grows with fill
```

---

## 03 — SCALING

---

### Scaling › Strategies

```
03-scaling/scaling-strategies.md
│
├── Vertical Scaling           → Bigger machine; simple; hard limit; single point of failure; no code change
├── Horizontal Scaling         → More machines; requires stateless app; LB in front; DB sharding needed
├── Stateless Design           → Session in Redis not in server memory; enables any node to serve any request
├── Auto-scaling               → Scale on CPU/RPS/queue depth; cooldown period; scale-out fast, scale-in slow
├── Database Bottleneck        → Read replicas → caching → sharding → multi-region; each step adds complexity
└── CDN for Static             → Offload 80%+ of bandwidth for media-heavy apps; origin only for cache misses
```

---

### Scaling › Database Scaling

```
03-scaling/database-scaling-deep-dive.md
│
├── Read Replicas              → Async replication; reads scale linearly; write still one leader; replication lag
├── Connection Pooling         → PgBouncer; DB has connection limit (~500-5000); pool multiplexes thousands of app threads
├── Query Optimization         → EXPLAIN ANALYZE; missing index = seq scan; covering index; materialized views for aggregates
├── Range Sharding             → By user_id range; easy range queries; uneven load if IDs grow monotonically
├── Hash Sharding              → hash(user_id) % N; even; cross-shard joins impossible; app must own routing
├── OLAP Separation            → Snowflake / BigQuery for analytics; ETL from OLTP; never run analytics on prod DB
└── Hot Partition Fix          → Add random suffix to key; scatter-gather on read; or virtual nodes
```

---

### Scaling › Global Distribution

```
03-scaling/global-distribution.md
│
├── Active-Passive             → Primary region handles all writes; secondary is warm standby; failover on DR
├── Active-Active              → Both regions accept writes; conflict resolution needed; lower latency globally
├── CRDTs                      → Conflict-free Replicated Data Types; counters/sets/maps that merge automatically
├── Data Residency             → GDPR: EU user data must stay in EU; complicates active-active; need region-aware routing
├── Latency-Based Routing      → Route user to nearest healthy region; Route53 / Cloudflare
├── CDN Topology               → PoPs at edge; shield origin with mid-tier cache; origin behind private network
└── Global Databases           → CockroachDB, Spanner: globally consistent with TrueTime/HLC; high write latency
```

---

## 04 — ADVANCED TOPICS

---

### Advanced › Distributed Systems

```
04-advanced-topics/distributed-systems.md
│
├── Consensus Problem          → All nodes agree on one value despite failures; Paxos (complex), Raft (understandable)
├── Raft                       → Leader election → log replication → commitment (majority quorum); leader handles all writes
├── Leader Election            → Bully algorithm; ZooKeeper ephemeral znodes; Raft built-in; need fencing on split-brain
├── Split-Brain                → Network partition → two leaders; use fencing tokens + majority quorum to prevent data corruption
├── Vector Clocks              → Logical timestamps; detect causality; {A:1, B:2} → happened-after; conflicts = concurrent
└── Gossip Protocol            → Each node spreads state to random peers; O(log N) convergence; Cassandra/Consul use this
```

---

### Advanced › Distributed Concepts

```
04-advanced-topics/distributed-concepts.md
│
├── Idempotency                → Same request N times = same result as once; use idempotency key; safe to retry
├── Exactly-Once Semantics     → At-least-once delivery + idempotent consumer = exactly-once processing
├── Two-Phase Commit (2PC)     → Prepare → Commit; coordinator SPOF; blocking on coordinator failure; avoid in practice
├── Saga Pattern               → Distributed transaction as sequence of local TXs with compensating actions on failure
├── Tombstones                 → Deletion marker in LSM/Cassandra; actual delete during compaction; prevents ghost reads
└── Hinted Handoff             → Node temporarily stores writes for an unavailable node; forwards on recovery
```

---

### Advanced › Microservices

```
04-advanced-topics/microservices.md
│
├── Service Mesh               → Sidecar proxy (Envoy/Linkerd) per pod; handles mTLS, retries, circuit-breaking transparently
├── Circuit Breaker            → Closed → Open (fail fast) → Half-Open (probe one request) → Closed; Hystrix, Resilience4j
├── Bulkhead                   → Isolate thread pools per dependency; one slow service doesn't exhaust all threads
├── Sidecar Pattern            → Helper container runs alongside main; handles cross-cutting: logging, auth, tracing
├── API Composition            → Gateway aggregates responses from N services; watch for N+1 latency cascade
└── Challenges                 → Distributed tracing required; data consistency across services; network failure handling
```

---

### Advanced › Event-Driven Architecture

```
04-advanced-topics/event-driven-architecture.md
│
├── Event Sourcing             → Store events (not state); replay to reconstruct state; audit log is free; hard to query
├── CQRS                       → Command (write) model separate from Query (read) model; read model = denormalized projection
├── Choreography               → Services react to events autonomously; no orchestrator; loose coupling; harder to trace
├── Orchestration              → Central orchestrator (Saga orchestrator) tells services what to do; easier to trace
├── Outbox Pattern             → Write event to DB outbox table in same TX; relay process publishes to broker; no dual-write problem
└── Event Schema Evolution     → Add fields (backward compatible); never remove/rename; use schema registry (Avro/Protobuf)
```

---

### Advanced › Observability

```
04-advanced-topics/observability.md
│
├── Three Pillars              → Metrics (aggregated numbers), Logs (discrete events), Traces (request flow across services)
├── RED Method                 → Rate (requests/sec), Errors (error rate), Duration (latency) — use for every service
├── SLI / SLO / SLA            → SLI: measured metric; SLO: internal target (e.g., 99.9% uptime); SLA: customer contract with penalty
├── Error Budget               → 100% - SLO = budget for experiments; burn fast → freeze releases; track weekly
├── Distributed Tracing        → Trace ID propagated in headers; Jaeger/Zipkin; find latency bottleneck across services
└── Log Levels                 → DEBUG (dev only), INFO (state changes), WARN (recoverable), ERROR (needs attention); never log PII
```

---

### Advanced › Internals

---

#### Kafka Internals

```
04-advanced-topics/internals/kafka-internals.md
│
├── Log Segments               → Each partition = append-only log; segments split at size/time; old segments deleted by retention policy
├── ISR (In-Sync Replicas)     → Set of replicas fully caught up to leader; leader only acks after ISR confirms
├── Producer Acks              → acks=0 (no wait), acks=1 (leader only), acks=all (full ISR); all = no data loss
├── Consumer Group             → Each partition consumed by exactly one consumer in group; rebalance on join/leave
├── Controller Election        → One broker is controller (ZooKeeper watch or KRaft); manages partition leadership
└── Exactly-Once               → Idempotent producer (dedup by seq#) + transactional API; overhead: ~20% throughput cost
```

---

#### Redis Internals

```
04-advanced-topics/internals/redis-internals.md
│
├── Skip List (ZSet)           → Probabilistic multi-level linked list; O(log N) insert/search; used for sorted sets
├── Dict / Hash Table          → Two hash tables; incremental rehash on resize; avoids pause (copy all at once)
├── Persistence: RDB           → Snapshot at intervals; compact; slow recovery for large datasets; may lose recent writes
├── Persistence: AOF           → Append every write command; durable; large file; rewrite periodically; fsync policy matters
├── Pub-Sub                    → Fire-and-forget; subscriber must be online; no persistence; use Streams for durable
└── Redis Cluster              → 16384 slots; each master owns slots; gossip for topology; client uses MOVED redirect
```

---

#### Cassandra Internals

```
04-advanced-topics/internals/cassandra-internals.md
│
├── Consistent Hash Ring       → Each node owns a token range; replication factor N = N copies on N successive nodes
├── Gossip Protocol            → Nodes exchange state every second with 3 random peers; eventual topology convergence
├── Memtable → SSTable         → Writes go to commit log + memtable; flushed to SSTable on disk; compaction merges SSTables
├── Compaction                 → Size-tiered (write-heavy), Leveled (read-heavy, predictable); tombstones cleared here
├── Bloom Filter per SSTable   → Avoid disk reads for missing keys; FP rate tunable
└── Consistency Levels         → ONE (fast), QUORUM (balanced), ALL (slow); W + R > RF = strong consistency
```

---

#### PostgreSQL Internals

```
04-advanced-topics/internals/postgresql-internals.md
│
├── MVCC                       → Each row has xmin/xmax; readers see snapshot at TX start; no read locks
├── WAL (Write-Ahead Log)      → Changes written to WAL before heap; crash recovery replays WAL; also used for replication
├── VACUUM                     → Marks dead rows as reusable; avoids table bloat; autovacuum runs automatically
├── B-tree Index               → Default; good for equality + range; high write overhead on inserts
├── Index Types                → Hash (equality only), GIN (full-text, arrays), GiST (geo, range), BRIN (sequential data)
└── Connection Limit           → Default 100; use PgBouncer for thousands of app connections; each connection = OS process
```

---

## 05 — HLD PROBLEMS

---

### HLD Easy › URL Shortener

```
05-hld-problems/01-easy/url-shortener.md
│
├── Core Challenge             → Generate unique 7-char short code for long URL; redirect fast
├── Hashing                    → MD5(url) → take first 7 chars of Base62; collision → append counter
├── Base62 Encoding            → [0-9][a-z][A-Z] = 62 chars; 7 chars = 62^7 = 3.5 trillion URLs
├── Storage                    → KV store (DynamoDB): short_code → {long_url, user_id, created_at, clicks}
├── Redirect                   → 301 (permanent, browser caches) vs 302 (temporary, tracks every click)
├── Cache Layer                → Cache hot short codes in Redis; LRU eviction; TTL = infinity for popular URLs
└── Sharding                   → Hash short_code to shard; or use Cassandra with short_code as partition key
```

---

### HLD Easy › Rate Limiter

```
05-hld-problems/01-easy/rate-limiter.md
│
├── Core Challenge             → Limit requests per user/IP without centralizing all state on one node
├── Token Bucket               → Allows burst; tokens refill at rate R; most flexible; burst up to bucket capacity
├── Redis Implementation       → INCR + EXPIRE per window; or Lua script for sliding window (atomic)
├── Distributed Challenge      → Per-node counters: fast but inaccurate; centralized Redis: accurate, adds 1ms hop
├── Response                   → 429 Too Many Requests + Retry-After header + X-RateLimit-Remaining
└── Bypass Attack              → Rotate IPs → use user-level rate limiting; API key abuse → circuit breaker
```

---

### HLD Easy › Web Crawler

```
05-hld-problems/01-easy/web-crawler.md
│
├── Core Challenge             → Crawl billions of URLs without revisiting; respect robots.txt; politeness
├── URL Frontier               → Priority queue of URLs to crawl; priority = PageRank or freshness
├── Bloom Filter Dedup         → Check if URL seen before; FP = re-crawl rarely; much cheaper than DB lookup
├── Politeness                 → Per-domain delay queue; don't hammer same server; respect Crawl-delay in robots.txt
├── HTML Parsing               → Extract links; normalize URLs (lowercase, strip tracking params); enqueue new
├── Distributed Workers        → N fetcher workers pull from frontier; consistent hash URL → worker to avoid dedup collision
└── Storage                    → Raw HTML in object storage (S3); extracted data in index pipeline; metadata in DB
```

---

### HLD Easy › Booking System

```
05-hld-problems/01-easy/booking-system.md
│
├── Core Challenge             → Prevent double-booking; handle concurrent reservation attempts for same slot
├── Optimistic Locking         → Read version; update WHERE version=old; if 0 rows updated → conflict → retry
├── Pessimistic Locking        → SELECT FOR UPDATE; holds DB lock; simpler but slower under high concurrency
├── Idempotency Key            → Client sends unique key; server stores key+result; retry = return cached result
├── Two-Phase Reserve          → HOLD (soft lock, 10 min TTL) → CONFIRM (hard commit); hold expires = slot freed
└── Inventory Cache            → Cache available slots; invalidate on booking; slight stale = overbooking risk → use DB as truth
```

---

### HLD Medium › Twitter News Feed

```
05-hld-problems/02-medium/twitter-news-feed.md
│
├── Core Challenge             → Deliver personalized feed in <100ms; celebrities have 100M followers
├── Fan-out on Write (Push)    → On tweet, push to all follower feeds; fast read; expensive write for celebrities
├── Fan-out on Read (Pull)     → On load, fetch from all followees; no precompute; too slow for many followees
├── Hybrid                     → Push for normal users (<10K followers); Pull + merge for celebrities; rank + merge
├── Feed Storage               → Redis sorted set per user; score = tweet timestamp; ZREVRANGE for feed
├── Ranking                    → ML model: engagement signals (likes, retweets, recency, relationship strength)
└── Media                      → Photos/videos in CDN; tweet only stores URL; feed service assembles response
```

---

### HLD Medium › YouTube

```
05-hld-problems/02-medium/youtube.md
│
├── Core Challenge             → Store + transcode + stream petabytes of video; billions of views
├── Upload Pipeline            → Chunked upload (resumable) → raw S3 → transcoding queue → N resolutions → CDN
├── Transcoding                → FFmpeg workers; fan-out per resolution (240p/480p/1080p/4K); output to S3
├── Adaptive Bitrate (ABR)     → HLS/DASH manifests; player picks quality based on bandwidth; segment = 2-10s
├── CDN Strategy               → Static videos cached at edge; popular = cache everywhere; rare = cache on demand
├── Metadata DB                → Video: id, title, channel_id, s3_path, status, view_count; Comments separate service
└── Recommendation             → Watch history + embeddings; two-tower model; candidate generation → ranking
```

---

### HLD Hard › Payment System

```
05-hld-problems/03-hard/payment-system.md
│
├── Core Challenge             → Exactly-once; no money lost or double-charged; audit trail
├── Double-Entry Ledger        → Every TX = debit one account + credit another; ledger sum always = 0
├── Idempotency Key            → Client sends UUID; server returns same result for retries; store key in DB
├── Two-Phase Commit (avoid)   → Use Saga with compensating transactions instead; 2PC = coordinator SPOF
├── Reconciliation             → Nightly job compares internal ledger vs bank statements; flag discrepancies
├── PSP Integration            → Stripe/PayPal webhooks for async confirmation; update order status on webhook
└── Fraud Detection            → Rules engine + ML score; flag → manual review or auto-decline; velocity checks
```

---

### HLD Hard › Distributed Message Queue

```
05-hld-problems/03-hard/distributed-message-queue.md
│
├── Core Challenge             → Durable, ordered, scalable; partition for parallelism; replay capability
├── Topic → Partitions         → Each partition = ordered log; N partitions → N consumers in parallel
├── Replication                → Each partition replicated to M brokers; ISR = in-sync replicas; leader handles writes
├── Offset Management          → Consumer commits offset after processing; restart from offset; earliest vs latest
├── Producer Acks              → acks=all = no data loss; acks=1 = fast, lose on leader crash; acks=0 = fire-forget
├── Consumer Groups            → Each group reads all messages independently; within group: one consumer per partition
└── Compaction                 → Keep only latest value per key; used for CDC and materialized views
```

---

### HLD Hard › LLM Chat System

```
05-hld-problems/03-hard/llm-chat-system.md
│
├── Core Challenge             → Stream tokens in real-time; manage long context; control token cost at scale
├── Streaming Architecture     → SSE or WebSocket; LLM generates tokens one by one; stream to client as produced
├── Context Management         → Context window limited (~128K tokens); summarize old turns; sliding window strategy
├── Prompt Caching             → Cache prefix (system prompt) at model layer; reduces latency + cost for repeat prefixes
├── Load Balancing             → GPU servers stateless if no KV cache; route to least-loaded; autoscale on queue depth
├── Token Cost Control         → Max output tokens limit; prompt compression; retrieval instead of long context
└── Multi-turn Storage         → Store conversation history in DB; retrieve relevant turns on each request
```

---

### HLD Hard › RAG System

```
05-hld-problems/03-hard/rag-system.md
│
├── Core Challenge             → Retrieve semantically relevant documents at query time; inject into LLM context
├── Ingestion Pipeline         → Documents → chunk → embed (text-embedding-ada-002) → store in vector DB
├── Vector DB                  → Pinecone/Weaviate/pgvector; stores embedding + metadata; ANN search (HNSW/IVF)
├── ANN Search                 → Approximate Nearest Neighbor; HNSW = graph-based, fast; IVF = cluster-based, recall trade-off
├── Hybrid Search              → Dense (semantic) + sparse (BM25 keyword); combine scores with RRF or weighted sum
├── Reranking                  → Cross-encoder reranker on top-K candidates; more accurate, slower; run on small set
└── Context Assembly           → Top-K chunks → dedup → format → inject into prompt before LLM call
```

---

### HLD Hard › Stock Exchange

```
05-hld-problems/03-hard/stock-exchange.md
│
├── Core Challenge             → Match buy/sell orders with sub-millisecond latency; no lost orders; audit trail
├── Order Book                 → Per-symbol: sorted bids (desc) + asks (asc); match when bid ≥ ask
├── Matching Engine            → Single-threaded per symbol (no locks); FIFO within price level; price-time priority
├── Order Types                → Market (execute immediately at best price), Limit (execute at price or better), Stop
├── Low Latency                → Bypass kernel networking (DPDK/kernel bypass); binary protocol; CPU pinning; cache line alignment
├── Persistence                → Write to WAL before ack; replay on crash; match journal = audit trail
└── Market Data                → Best bid/ask (Level 1) → full order book (Level 2) → trade feed; publish via multicast UDP
```

---

## 06 — LLD

---

### LLD › OOP Four Pillars

```
06-lld/01-oop-fundamentals/four-pillars.md
│
├── Encapsulation              → Bundle data + methods; hide internals; expose only interface; reduces coupling
├── Abstraction                → Hide complexity; show only what's necessary; interfaces + abstract classes
├── Inheritance                → IS-A relationship; reuse code; Java = single class inheritance, multiple interface
├── Polymorphism               → Same method name, different behavior; compile-time (overloading) vs runtime (overriding)
└── Java Specifics             → interface (contract), abstract class (partial impl), final (no override), default methods
```

---

### LLD › SOLID Principles

```
06-lld/02-solid-principles/
│
├── SRP                        → One class, one reason to change; split God classes; UserService vs UserPersistence
├── OCP                        → Open for extension (add new strategy), closed for modification (don't touch existing); use Strategy/Factory
├── LSP                        → Subtypes must be substitutable for base type; Rectangle/Square violation; don't weaken postconditions
├── ISP                        → Many small interfaces > one fat interface; implement only what you use; IReadable vs IWritable
└── DIP                        → High-level modules depend on abstractions; inject dependencies; enables testing with mocks
```

---

### LLD › Design Patterns — Creational

```
06-lld/03-design-patterns/01-creational/
│
├── Singleton                  → Single instance per JVM; double-checked locking + volatile; enum singleton = safest
├── Factory Method             → Subclass decides which class to instantiate; decouple creation from use
├── Abstract Factory           → Factory of factories; create families (dark/light theme with Button+Checkbox+Input)
├── Builder                    → Construct complex object step-by-step; fluent API; immutable result; vs telescoping constructor
└── Prototype                  → Clone existing object; avoid expensive initialization; deep vs shallow copy matters
```

---

### LLD › Design Patterns — Structural

```
06-lld/03-design-patterns/02-structural/
│
├── Adapter                    → Convert incompatible interface to expected one; legacy integration; uses composition
├── Bridge                     → Decouple abstraction from implementation; both can vary independently; vs Adapter (adaptation vs design)
├── Composite                  → Tree of objects; leaf and composite share same interface; file system, UI hierarchy
├── Decorator                  → Wrap to add behavior; chain of wrappers; InputStream → BufferedInputStream → DataInputStream
├── Facade                     → Simplified interface to complex subsystem; hides complexity; doesn't add new behavior
├── Flyweight                  → Share intrinsic state; extrinsic state passed at runtime; glyph characters, game objects
└── Proxy                      → Placeholder: Virtual (lazy), Remote (network), Protection (access control), Cache (memoize)
```

---

### LLD › Design Patterns — Behavioral

```
06-lld/03-design-patterns/03-behavioral/
│
├── Observer                   → Subject notifies observers on state change; push vs pull; EventBus; Java EventListener
├── Strategy                   → Family of algorithms, interchangeable; eliminate conditionals; SortStrategy, PaymentStrategy
├── Command                    → Encapsulate request as object; undo/redo; queue commands; macro commands
├── State                      → Object changes behavior as state changes; eliminates state-based conditionals; Elevator, VendingMachine
├── Template Method            → Skeleton in abstract class; steps overridden in subclass; Hollywood Principle (don't call us)
├── Chain of Responsibility    → Pass request along handler chain until handled; logging, middleware, approval workflow
├── Iterator                   → Sequential access without exposing internals; Java Iterable; external vs internal iterator
├── Mediator                   → Centralize communication between objects; reduce coupling; ChatRoom mediates users
└── Visitor                    → Add operations to objects without changing classes; double dispatch; AST traversal
```

---

### LLD › UML Diagrams

```
06-lld/uml-diagrams.md
│
├── Two types                  → Structural (class, component, deployment) + Behavioral (sequence, activity, state)
├── Class Diagram              → Most used in LLD; shows: ClassName | attributes | methods
├── Visibility Notations       → + public, - private, # protected, ~ package
└── Relationships              → Association (uses), Aggregation (has-a, hollow diamond), Composition (owns-a, filled diamond), Inheritance (arrow), Realization (dashed arrow to interface)
```

---

### LLD › Concurrency Patterns

```
06-lld/04-concurrency/concurrency-patterns.md
│
├── ReentrantReadWriteLock     → Multiple concurrent readers, one exclusive writer; fair=true to prevent writer starvation
├── Semaphore                  → Count gate on resource pool (DB connections, API slots); binary semaphore = mutex without ownership
├── CountDownLatch             → One-shot fan-out/join; await until count reaches 0; cannot reset
├── CyclicBarrier              → N threads synchronize at checkpoint; resets automatically; use for batch phases
├── ThreadPoolExecutor         → corePoolSize, maxPoolSize, keepAlive, bounded queue, rejection policy; CallerRunsPolicy = backpressure
├── AtomicInteger / CAS        → Lock-free single-field updates; compare-and-set; not for multi-field invariants
├── ConcurrentHashMap          → Segment locking (Java 8: CAS on bucket); use over synchronized HashMap
└── volatile                   → Visibility guarantee across threads; NO atomicity; never use for increment — use Atomic*
```

---

### LLD › Futures & Async Patterns

```
06-lld/04-concurrency/futures-async-patterns.md
│
├── Future                     → submit() returns Future<T>; .get() blocks; .get(timeout) throws TimeoutException; no chaining
├── CompletableFuture basics   → supplyAsync (start) → thenApply (sync transform) → thenApplyAsync (async transform) → exceptionally (recover)
├── thenCompose vs thenApply   → thenApply: T→U (wrap result); thenCompose: T→CF<U> (flatMap, avoid nested CFs)
├── allOf / anyOf              → allOf: wait for ALL, aggregate via .join(); anyOf: FIRST wins = hedged requests / replica race
├── Timeout                    → .orTimeout(500, ms) throws on breach; .completeOnTimeout("default", 500, ms) completes with value
├── Error handling             → exceptionally: failure only, recovery; handle(value, ex): always runs, inspect both
├── Promise bridge             → new CompletableFuture<>(); callback calls .complete(r) or .completeExceptionally(t)
└── get vs join                → .get() throws checked exceptions (use at boundary); .join() throws unchecked (use inside lambdas)
```

---

### LLD › Classic Problems

```
06-lld/05-problems/ — Tier 1
│
├── parking-lot                → Spot types (Compact/Large/Motorcycle); Strategy for spot selection; Observer for entry/exit events
├── rate-limiter               → Strategy pattern for algorithm; token bucket with AtomicLong; thread-safe per-user counter
├── tic-tac-toe                → Board as 2D array; win check after each move; State for game lifecycle
├── vending-machine            → State machine (Idle→HasCoin→Dispensing→OutOfStock); Command for each user action
├── splitwise                  → Graph of debts; simplify via net balances; debt = directed edge; settle with greedy
├── snake-and-ladder           → BFS for shortest path; board as array; Observer for game events
├── elevator-system            → SCAN algorithm (direction-based); State per elevator; Mediator for dispatch
└── comment-system             → Composite for nested threads; Observer for notification; pagination strategy

06-lld/05-problems/ — Tier 2
│
├── lru-cache                  → LinkedHashMap (Java) or Doubly-LL + HashMap; O(1) get + put; Proxy for LRU logic
├── hotel-management           → Rooms (State: Available/Reserved/Occupied); Strategy for pricing; Builder for booking
├── logger-library             → Singleton Logger; Chain of Responsibility for log levels; Decorator for formatters
└── s3-object-storage          → Composite for bucket/object hierarchy; Proxy for access control; Builder for presigned URL
```

---

## 07 — INTERVIEW TEMPLATES

---

### Templates › HLD Framework

```
07-interview-templates/hld-template.md
│
├── Step 1: Clarify (5 min)    → Users, scale (QPS/storage), consistency, latency SLA, read vs write ratio
├── Step 2: Estimate (5 min)   → QPS: DAU × actions/day / 86400; Storage: QPS × record_size × retention
├── Step 3: API Design (5 min) → REST endpoints; request/response schemas; auth mechanism
├── Step 4: Data Model (5 min) → Tables/collections; indexes; sharding key; what goes in cache vs DB
├── Step 5: High-Level Design (15 min) → Draw: clients → CDN/LB → API servers → cache → DB → async workers
└── Step 6: Deep Dives (15 min) → Bottleneck + trade-off per component; interviewer will guide what to dig into
```

---

### Templates › Capacity Estimation

```
07-interview-templates/capacity-estimation.md
│
├── QPS Math                   → 1M DAU, 10 actions/day = 10M req/day = 116 RPS ≈ ~100 QPS
├── Storage Math               → 100 QPS × 1KB/record × 86400s × 365 days = ~3TB/year
├── Bandwidth                  → 100 QPS × 100KB avg response = 10MB/s = 80 Mbps ingress
├── Cache Size                 → 80/20 rule: 20% of data = 80% of requests; cache 20% of daily active data
└── DB Connections             → 10 app servers × 50 threads each = 500 connections; use connection pool
```

---

### Templates › Trade-offs Cheat Sheet

```
07-interview-templates/trade-offs-cheat-sheet.md
│
├── SQL vs NoSQL               → SQL: joins, ACID, schema; NoSQL: scale, flexible schema; choose by access pattern
├── Push vs Pull (Feed)        → Push: fast read, expensive write for celebrities; Pull: fresh, slow for many followees
├── Sync vs Async              → Sync: simple, slower, blocks caller; Async: fast response, eventual consistency, complex
├── CP vs AP (CAP)             → CP: banking, inventory; AP: social feed, search; most systems allow tunable consistency
└── Cache-Aside vs Write-Through → Cache-aside: lazy, stale possible; Write-through: consistent, write overhead
```

---

## 09 — PATTERNS

---

### Patterns › Outbox

```
09-patterns/outbox-pattern.md
│
├── Problem                    → Write to DB + publish to Kafka in one atomic operation — impossible with 2-phase commit
├── Solution                   → Write event to outbox table in same DB transaction; relay process polls and publishes
├── Relay (CDC or Polling)     → CDC: tail WAL (Debezium); Polling: SELECT unsent events + mark sent; CDC is more efficient
├── Idempotent Consumer        → Relay may retry; consumer must be idempotent; deduplicate by event_id
└── Trade-off                  → At-least-once delivery; adds outbox table + relay process; simplest exactly-once alternative
```

---

### Patterns › Saga

```
09-patterns/saga-pattern.md
│
├── Problem                    → Distributed transaction across N services; no global lock; 2PC doesn't scale
├── Choreography               → Each service publishes event; next service reacts; no orchestrator; harder to trace
├── Orchestration              → Central orchestrator sends commands; explicit flow; easier to trace + test
├── Compensating Transactions  → On failure, roll back completed steps in reverse order; must be idempotent
└── When to Use                → Order flow (reserve inventory → charge → ship); any multi-service write workflow
```

---

### Patterns › CQRS + Event Sourcing

```
09-patterns/cqrs-event-sourcing.md
│
├── CQRS                       → Command model (write): normalized, consistent; Query model (read): denormalized, fast
├── Event Sourcing             → Never update state; append events; replay events to reconstruct state; audit trail free
├── Projection                 → Consume events, build read model; rebuild by replaying; eventual consistency
├── Snapshot                   → Periodic state snapshot to avoid replaying entire history; checkpoint
└── When to Use                → High audit requirements, complex business rules, event-driven systems; not CRUD apps
```

---

### Patterns › Strangler Fig

```
09-patterns/strangler-fig.md
│
├── Problem                    → Migrate legacy monolith to microservices without big-bang rewrite
├── Phase 1                    → Route traffic through facade/gateway; monolith handles everything
├── Phase 2                    → Extract one capability to new service; gateway routes that path to new service
├── Phase 3                    → Expand extraction; shadow mode test new service against monolith
├── Phase 4                    → Monolith fully replaced; remove routing logic; decommission legacy
└── Anti-Corruption Layer      → Translate between legacy domain model and new domain model at boundary
```

---

### Patterns › Anti-Patterns

```
09-patterns/anti-patterns.md
│
├── Distributed Monolith       → Microservices with synchronous coupling + shared DB; worst of both worlds
├── Big Ball of Mud            → No clear structure; everything depends on everything; add strangler fig to escape
├── Chatty Services            → Too many fine-grained calls between services; N+1 problem at service level; batch or aggregate
├── Shared Database            → Two services share one DB; tight coupling; schema change breaks both; each service owns its data
└── Premature Optimization     → Adding cache/sharding/queue before proving the bottleneck exists; measure first
```

---

## SUPPORTING MATERIALS

---

### Interview Framework

```
SYSTEM_DESIGN_INTERVIEW_FRAMEWORK.md
│
├── Phase 1: Clarify (5 min)        → Nail scope: who, what scale, consistency level, latency SLA, read/write ratio
├── Phase 2: Estimate (5 min)       → QPS = DAU × actions / 86400; storage = QPS × size × retention
├── Phase 3: High-Level Design (10 min) → Draw the happy path: client → LB → service → cache → DB
├── Phase 4: Identify Bottlenecks (5 min) → What breaks first? DB writes? Cache misses? Single points of failure?
├── Phase 5: Trade-offs (10 min)    → SQL vs NoSQL, push vs pull, sync vs async — justify each choice
├── Phase 6: Deep Dive (15 min)     → Interviewer picks; know sharding, replication, exactly-once cold
└── Phase 7: Scaling (5 min)        → 10× traffic: add read replicas → cache → shard → multi-region
```
