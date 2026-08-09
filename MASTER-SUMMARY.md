# Master Summary — Complete System Design Repository

Every "5-Minute Summary" (or a synthesized equivalent) from all 244 content files in this repository: 72 HLD/LLD problem write-ups plus 172 conceptual, methodology, pattern, template, and reference files covering the entire repo.

> Note: the catalog READMEs (`05-hld-problems/README.md`, `06-lld/README.md`) state stale problem counts (27 HLD, 23 LLD). The true counts on disk are 36 + 36 = 72, reflected here.

## Contents

**HLD — 36 problems**
- [Easy (9)](#hld-easy)
- [Medium (7)](#hld-medium)
- [Hard (20)](#hld-hard)

**LLD — 36 problems**
- [Core Problems (5)](#lld-core-problems)
- [Frequent Problems (12)](#lld-frequent-problems)
- [Domain-Specific (7)](#lld-domain-specific)
- [Advanced / Niche (12)](#lld-advanced-niche)

**Foundations, Building Blocks & Scaling — 29 files**
- [Foundations (9)](#foundations)
- [Building Blocks (16)](#building-blocks)
- [Scaling (4)](#scaling)

**Advanced Topics & Patterns — 31 files**
- [Advanced Topics (23)](#advanced-topics)
- [Distributed Systems Patterns (8)](#distributed-systems-patterns)

**HLD & LLD Methodology — 16 files**
- [HLD Methodology (8)](#hld-methodology)
- [LLD Methodology (8)](#lld-methodology)

**LLD Core Concepts — 39 files**
- [OOP Fundamentals (5)](#oop-fundamentals)
- [SOLID Principles (5)](#solid-principles)
- [Design Patterns — GoF (23)](#design-patterns-gang-of-four)
- [Concurrency Patterns (4)](#concurrency-patterns)
- [Glossary & UML Reference (2)](#glossary--uml-reference)

**[Interview Templates — 18 files](#interview-templates)**

**Reference & Book Summaries — 39 files**
- [Reference Docs (5)](#reference-docs)
- [DDIA Summary (1)](#book-summary-designing-data-intensive-applications)
- [Head First Java Summary (21)](#book-summary-head-first-java)
- [Head First OOA&D Summary (12)](#book-summary-head-first-object-oriented-analysis--design)

---

# HLD — High-Level Design

## HLD: Easy

### Autocomplete

*File: `05-hld-problems/01-easy/autocomplete.md`*

**What this covers:** Design autocomplete/typeahead search — returning the top 5–10 ranked completions for a prefix within 100ms, at Google-scale.

**Key design decisions:**
- Data structure: Trie (prefix tree) for prefix lookup; each node stores top-K suggestions cached; O(prefix length) lookup
- Ranking: suggestions scored by search frequency; updated via Hadoop batch job hourly or Kafka streaming in real-time
- Scale: Trie doesn't fit in RAM on one server (billions of terms) → shard Trie by prefix range; each server owns A–F, G–M, N–Z
- Prefix cache: Redis cache top prefixes (2-char and 3-char prefixes handle 80% of queries); re-compute on score change
- Freshness: trending queries (breaking news) need minutes-fresh data → stream Kafka → real-time frequency update pipeline
- API design: GET /suggestions?q=sys&limit=10; backend routes to correct Trie shard based on prefix
- Personalization: blend global frequency score with user's recent queries; weighted combination; stored in user session cache

**Key takeaway:** Cache top suggestions for the most common prefixes in Redis — most queries are short (2–3 chars); the Trie itself only needs to serve cache misses for long-tail prefixes.

### Booking System

*File: `05-hld-problems/01-easy/booking-system.md`*

**What this covers:** Design a booking system (hotels/flights) — preventing double-booking with inventory locking, idempotent reservations, and consistent availability search.

**Key design decisions:**
- Inventory locking: SELECT FOR UPDATE on inventory row during booking transaction; pessimistic locking prevents concurrent double-booking
- Optimistic locking alternative: version field on inventory row; CAS (compare-and-swap) on update; retry on conflict; better throughput
- Idempotency: idempotency key per booking attempt; prevents duplicate charge if client retries after timeout
- Two-step booking: hold → confirm flow; hold locks inventory for 10 min; confirm completes payment + booking; release if not confirmed
- Availability search: read replicas or separate search index (Elasticsearch) for fast availability queries; don't hit primary DB for reads
- Payment flow: payment processed outside booking DB transaction to avoid holding DB lock during payment processing (300ms+ latency)
- Overbooking prevention: DB constraint (CHECK inventory ≥ 0) + unique constraint on (room_id, date, booking_id) as last line of defense

**Key takeaway:** Two-step hold → confirm prevents double-booking without holding locks during payment processing; idempotency keys prevent duplicate charges on retry.

### Key Value Store

*File: `05-hld-problems/01-easy/key-value-store.md`*

**What this covers:** Design a distributed key-value store (like Redis, DynamoDB, Cassandra) — covers consistent hashing, replication, storage engines, and CAP theorem trade-offs.

**Key design decisions:**
- Data partitioning: consistent hashing with virtual nodes; each key maps to a node clockwise on the ring; vnodes ensure even distribution
- Replication: replicate to N successor nodes (N=3); read from R, write to W; quorum W+R>N; tunable per-operation consistency
- Storage engine: LSM tree for write-heavy (Cassandra approach) — MemTable → SSTable; Bloom filter to skip disk reads for non-existent keys
- Write path: Commit log (durability) → MemTable (in-memory) → periodic SSTable flush → compaction; all sequential I/O
- Failure handling: hinted handoff (queue writes for down nodes); read repair (fix inconsistency on read); Merkle tree anti-entropy
- CAP choice: AP (Cassandra-style) vs CP (Zookeeper-style); most KV stores default to AP with tunable consistency
- Operations: get, put, delete (tombstone marker); TTL via background sweeper; range scans only if using range sharding

**Key takeaway:** Consistent hashing + LSM tree is the Cassandra blueprint — understand the write path (commit log → MemTable → SSTable) and quorum reads/writes cold.

### Leaderboard

*File: `05-hld-problems/01-easy/leaderboard.md`*

**What this covers:** Design a real-time leaderboard — ranking millions of players by score with fast top-N and individual rank queries using Redis sorted sets.

**Key design decisions:**
- Core data structure: Redis Sorted Set — ZADD for score update, ZRANGE/ZREVRANGE for top-N, ZRANK for player rank; all O(log N) operations
- Score update: ZADD player_id score (absolute set) or ZINCRBY player_id delta (incremental); atomic, no race conditions
- Global rank for player: ZREVRANK leaderboard player_id → O(log N); nearby players with ZREVRANGE rank±5
- Windowed leaderboards (daily/weekly): separate sorted sets per window with TTL; ZUNIONSTORE to merge; background expiry
- Scale: 100M players × 16 bytes (member + score) ≈ 1.6 GB; fits in single Redis instance; sharding by game_id for many games
- DB persistence: Redis is source of truth for ranking; persist to DB asynchronously for history and analytics
- Percentile calculation: ZCARD (total count) + ZRANK (position) → percentile = (total - rank) / total × 100

**Key takeaway:** Redis sorted sets are purpose-built for leaderboards — ZADD + ZREVRANK gives you real-time ranking with O(log N) updates and reads; no DB needed for real-time queries.

### Pastebin

*File: `05-hld-problems/01-easy/pastebin.md`*

**What this covers:** Design Pastebin — a text sharing service that introduces object storage, CDN delivery, and TTL-based expiration in a simple read-heavy architecture.

**Key design decisions:**
- Storage split: metadata (paste_id, user_id, created_at, expires_at, size, visibility) in DB; content in object storage (S3/GCS)
- ID generation: random 8-char Base62 string (collision probability negligible at Pastebin scale); check uniqueness in DB before creating
- CDN delivery: paste content served via CDN (CloudFront); origin-pull on first request; TTL matches paste expiration
- Expiration: lazy deletion (check on read) + background cleanup job (scan for expired rows daily); don't rely on DB TTL alone
- Privacy model: public (indexed), unlisted (URL is the password — not searchable), private (requires auth)
- Read vs write ratio: reads dominate (read:write ≈ 100:1); cache hot pastes in Redis; metadata for analytics only
- Abuse prevention: rate limit paste creation per IP; scan content for malware/abuse keywords; CAPTCHA for anonymous users

**Key takeaway:** Pastebin is URL Shortener + object storage — key insight is to separate metadata (DB) from content (S3), and serve content through CDN to avoid origin load.

### Rate Limiter

*File: `05-hld-problems/01-easy/rate-limiter.md`*

**What this covers:** Design a distributed rate limiter — enforcing request limits per user/IP/API key across a cluster of servers without double-counting.

**Key design decisions:**
- Algorithm choice: Token Bucket (allows controlled burst, most common), Sliding Window Counter (accurate, low memory), Leaky Bucket (smooth output)
- Redis implementation: INCR + EXPIRE for fixed window; Lua script for atomic sliding window; sorted set (ZADD/ZREMRANGEBYSCORE) for sliding window log
- Distributed consistency: centralized Redis (accurate, single point) vs local counter with async sync (faster, slight over-count allowed)
- API Gateway vs application layer: prefer gateway (Nginx, Envoy) for centralized enforcement; application layer for fine-grained per-feature control
- Response headers: X-RateLimit-Limit, X-RateLimit-Remaining, X-RateLimit-Reset, Retry-After on 429
- Rule configuration: store rules in Redis or config service; hot-reload without restart; different tiers (free/pro/enterprise)
- Failure mode: if Redis is down → fail open (allow requests) not fail closed (block everything) to maintain availability

**Key takeaway:** Token Bucket in Redis with Lua scripts for atomic operations — centralize the rate limit state in Redis, and fail open if Redis is unavailable.

### Unique Id Generator

*File: `05-hld-problems/01-easy/unique-id-generator.md`*

**What this covers:** Design a distributed unique ID generator — generating globally unique, time-sortable 64-bit IDs at scale without central coordination.

**Key design decisions:**
- Snowflake ID (Twitter): 64 bits = 1 sign + 41 timestamp (ms) + 10 machine ID + 12 sequence; ~4096 IDs/ms per machine
- Alternatives: UUID v4 (random, not sortable, 128 bits), DB auto-increment (single point of failure), segment-based (pre-allocate ranges)
- Clock skew problem: if system clock goes backward, IDs from same machine could repeat; solution: wait until clock catches up or reject
- Machine ID assignment: ZooKeeper or etcd for machine registration; each worker registers and gets unique ID on startup
- Sorting property: Snowflake IDs are monotonically increasing within a machine and roughly ordered across machines → great for pagination
- High availability: no single coordinator; each machine generates IDs independently; horizontal scaling trivial
- Custom epoch: set epoch to company founding date to maximize usable timestamp bits (41 bits = ~69 years from epoch)

**Key takeaway:** Snowflake is the industry standard — 41-bit timestamp + 10-bit machine ID + 12-bit sequence = no coordination, sortable by time, 4096 IDs/ms per machine.

### Url Shortener

*File: `05-hld-problems/01-easy/url-shortener.md`*

**What this covers:** Design a URL shortener (Bitly) — one of the most common system design interview questions; covers hashing, Base62 encoding, and high-read-volume caching.

**Key design decisions:**
- ID generation: hash (MD5/SHA256, take first 7 chars) vs counter (auto-increment → Base62 encode); counter preferred for uniqueness
- Base62 encoding: 62^7 = 3.5 trillion combinations; supports custom aliases with collision detection
- Storage: write once, read many; MySQL/PostgreSQL for metadata; 301 vs 302 redirect (301 = cached at browser, loses analytics; 302 = server always sees request)
- Caching: hot URLs cached in Redis (90/10 rule — 20% URLs get 80% traffic); cache-aside; TTL = 24h
- DB schema: {short_code, original_url, user_id, created_at, expires_at, click_count}
- Scale: 100M URLs, 10B redirects/day → ~115K reads/sec → need Redis caching + read replicas; writes are trivial
- Analytics: async click counter (Kafka → batch DB write); avoid write-amplification on hot rows

**Key takeaway:** The redirect layer is read-heavy (100:1 read-to-write); cache aggressively in Redis; use 302 (not 301) for accurate analytics.

### Web Crawler

*File: `05-hld-problems/01-easy/web-crawler.md`*

**What this covers:** Design a web crawler — a distributed system that systematically fetches and indexes the web, starting from seed URLs and discovering new ones through link extraction.

**Key design decisions:**
- URL frontier: priority queue of URLs to crawl; BFS traversal; distributed as message queue (Kafka/SQS) for horizontal scaling
- Deduplication: Bloom filter for URL seen-check (1B URLs × 10 bits ≈ 1.2 GB); secondary DB for exact dedup if Bloom allows a false positive
- Politeness: per-domain crawl rate (robots.txt crawl-delay); separate queue per domain; rate limit fetcher per domain
- Content deduplication: SimHash or MD5 of page content to detect near-duplicates (mirror sites); avoid storing/indexing duplicates
- Distributed fetcher pool: stateless workers pull from Kafka URL queue; store HTML in object storage (S3); parse and extract links; enqueue new URLs
- robots.txt compliance: fetch robots.txt for each domain on first visit; cache with TTL; skip disallowed paths
- Recrawl scheduling: time-based (revisit popular pages every hour, rare pages monthly) + change-detection heuristics

**Key takeaway:** The URL frontier (priority queue), Bloom filter dedup, and per-domain politeness rate limits are the three critical components — get these right and the system scales horizontally.

---

## HLD: Medium

### E Commerce Platform

*File: `05-hld-problems/02-medium/e-commerce-platform.md`*

**What this covers:** Design an e-commerce platform (Amazon) — product catalog, inventory management, cart, checkout, payment, and order lifecycle with flash sale support.

**Key design decisions:**
- Product catalog: Elasticsearch for full-text search + faceted filtering; PostgreSQL for canonical product data; CDN for product images
- Inventory: inventory DB with optimistic locking (version field + CAS); reserve on add-to-cart, confirm on checkout; 30-min hold TTL
- Cart service: Redis for active cart (TTL = 30 days); cart is eventually consistent; don't put cart in main order DB
- Checkout flow: cart → inventory reservation → payment → order creation; each step is idempotent; compensating transactions on failure
- Payment: async payment via Stripe/Braintree; payment service publishes Kafka event on success → order confirmed; idempotency key prevents double-charge
- Flash sales: inventory counter in Redis (atomic DECR); actual DB inventory updated async; Redis acts as distributed semaphore
- Order history: Cassandra for order events (order_id by user_id); immutable event log; read by user timeline

**Key takeaway:** Flash sales require Redis atomic DECR for inventory (not DB) — DB cannot handle 100K concurrent reservation attempts; Redis holds the semaphore, DB is eventually consistent.

### Instagram

*File: `05-hld-problems/02-medium/instagram.md`*

**What this covers:** Design Instagram — a photo/video sharing platform with follow-graph, feed generation, and media delivery at billions-of-users scale.

**Key design decisions:**
- Media storage: photos/videos stored in object storage (S3); CDN for delivery; thumbnail generation on upload via worker queue
- Follow graph: adjacency list in DB (follower_id, followee_id); Redis cache of followed user IDs per user; graph DB for recommendations
- Feed generation: fan-out-on-write (precompute timelines on post → fast reads, expensive celebrity writes) vs fan-out-on-read (pull on demand → simpler, slower for active users)
- Hybrid: fan-out-on-write for normal users; fan-out-on-read for celebrities (>1M followers); merge at read time
- Media upload: client → signed S3 URL (direct upload, bypass app server) → async CDN propagation + thumbnail generation
- Capacity: 1B users, 50M active daily, 100M posts/day → ~1150 posts/sec; read:write ≈ 100:1 (mostly browsing)
- Storage: each photo ~300KB; 100M posts/day → 30TB/day; cold storage (S3 Glacier) after 90 days

**Key takeaway:** The celebrity problem is the hardest part — pure fan-out-on-write breaks at 10M followers; hybrid (fan-out for normal users, pull-on-read for celebrities merged at read time) is the production solution.

### Notification Service

*File: `05-hld-problems/02-medium/notification-service.md`*

**What this covers:** Design a notification service — delivering push/email/SMS notifications reliably at high throughput with deduplication, prioritization, and user preference management.

**Key design decisions:**
- Channel abstraction: unified notification model with channel plugins (APNs for iOS, FCM for Android, SendGrid for email, Twilio for SMS); each plugin handles delivery
- Reliability: Kafka queue per channel; at-least-once delivery; idempotency key prevents duplicates on retry; DLQ for failed notifications
- Priority queues: critical (OTP, alerts) → high-priority queue; marketing → low-priority queue; ensure critical delivery even under load
- Fan-out: event (e.g., new follower) → notification service → fan-out to all followers; large fan-outs (10M followers) batched async
- User preferences: preference DB (user_id → {push: on, email: on, SMS: off, quiet_hours: 22:00–08:00}); check before every send
- Deduplication: dedup by (user_id, notification_type, reference_id) in Redis with 1-hour TTL; prevent duplicate email for same event
- Rate limiting: cap per user/per type to avoid notification fatigue; e.g., max 3 marketing emails/day

**Key takeaway:** The preference check + deduplication layer is critical — a raw fan-out without it spams users and destroys engagement; always respect quiet hours and per-channel opt-outs.

### Twitter News Feed

*File: `05-hld-problems/02-medium/twitter-news-feed.md`*

**What this covers:** Design Twitter/news feed — one of the most classic interview problems; the core challenge is serving personalized timelines at millisecond latency for users following celebrities.

**Key design decisions:**
- Fan-out-on-write: when a user tweets, push to all followers' timeline caches (Redis); reads are fast (O(1)); expensive for celebrities (10M followers = 10M writes)
- Fan-out-on-read: pull tweets from all followees on timeline load; simple writes; slow reads for users following many accounts
- Hybrid: fan-out-on-write for normal users (<10K followers); fan-out-on-read for celebrities; merge at read time (get pre-computed timeline + pull last 100 celebrity tweets)
- Timeline storage: Redis sorted set per user (tweet_id by timestamp); ZRANGE for chronological feed; TTL 7 days
- Tweets DB: Cassandra (tweet_id, user_id, content, created_at, like_count); tweet_id as row key; timeline sharded by user_id
- Media: photos/videos stored in S3 + CDN; tweet stores S3 URL; media is separate from tweet metadata
- Search: Elasticsearch indexing tweet content; separate from timeline serving

**Key takeaway:** The celebrity (hotspot) problem is the hardest part — always answer it proactively with the hybrid approach; interviewers will specifically ask about it.

### Typeahead Search

*File: `05-hld-problems/02-medium/typeahead-search.md`*

**What this covers:** Design the Google Search typeahead — returning ranked suggestions within 50ms as the user types, at internet scale with personalization and trending freshness.

**Key design decisions:**
- Latency budget: 50ms total → 10ms network (CDN/anycast) + 40ms compute; only pre-computed suggestions can hit this
- Pre-computation: offline Hadoop job aggregates search logs → frequency per query → Trie with top-K suggestions per prefix; refreshed hourly
- Serving: sharded Trie servers (shard by prefix); request routes to correct shard; top-10 suggestions returned in <10ms
- CDN acceleration: common short prefixes (2–3 chars) cached at CDN edge; covers 80% of queries without hitting Trie servers
- Trending freshness: Kafka stream of real-time searches → HyperLogLog frequency counter → inject trending queries (last 5min) into suggestions
- Personalization: user's recent searches + location blended with global scores; personalization layer after Trie lookup; stored in Redis per user
- Spell correction: Levenshtein distance fuzzy match for typos; secondary lookup if no Trie match

**Key takeaway:** Pre-compute suggestions offline (hourly batch), serve from CDN for common prefixes (covers 80%), inject real-time trending (Kafka stream) for freshness — all three layers together deliver sub-50ms with current results.

### Whatsapp

*File: `05-hld-problems/02-medium/whatsapp.md`*

**What this covers:** Design WhatsApp — real-time messaging with WebSocket connections, delivery receipts, group messaging, and end-to-end encryption at billions of messages per day.

**Key design decisions:**
- Connection management: persistent WebSocket per client; connection server stores {user_id → connection_id}; Redis tracks which server holds each user's connection
- Message routing: sender → connection server → message queue (Kafka) → recipient's connection server → WebSocket push
- Delivery receipts: single check (sent), double check (delivered), blue double check (read); each state stored in message DB
- Offline delivery: messages stored in Cassandra if recipient offline; delivered on reconnect; TTL = 30 days
- Group messaging: group message fan-out to all member connections; capped at 256/1024 members for performance
- End-to-end encryption: Signal Protocol (Double Ratchet); keys never leave devices; server only routes ciphertext
- Media: images/videos sent as S3 URLs; metadata in message; recipient downloads directly from CDN

**Key takeaway:** The key insight is routing — a connection registry (Redis: user_id → server_id) tells the routing layer which WebSocket server to forward the message to.

### Youtube

*File: `05-hld-problems/02-medium/youtube.md`*

**What this covers:** Design YouTube — a video upload, transcoding, and streaming platform with CDN delivery, search indexing, and recommendation engine at global scale.

**Key design decisions:**
- Video upload: client → resumable upload → object storage (GCS/S3); chunked to handle large files and network interruptions
- Transcoding pipeline: upload triggers message to Kafka → transcoding workers (FFmpeg) → multiple resolutions (360p/720p/1080p/4K) → stored in S3 per quality tier
- CDN delivery: videos served from CDN edge nodes; adaptive bitrate streaming (HLS/DASH) selects quality based on bandwidth
- Metadata storage: video metadata (title, description, tags, duration, channel_id) in PostgreSQL; Elasticsearch for search
- View count: async counter via Kafka → batch aggregation; don't write to DB per view (thundering herd)
- Recommendations: collaborative filtering (viewers who watched X also watched Y); updated offline via Spark; served from feature store
- Capacity: 500 hours of video uploaded per minute; storage at multiple quality levels; CDN handles 99% of bandwidth

**Key takeaway:** Separate the upload path (async transcoding pipeline) from the streaming path (CDN + adaptive bitrate) — these have very different throughput and latency requirements.
---

## HLD: Hard

### Ad Click Aggregator

*File: `05-hld-problems/03-hard/ad-click-aggregator.md`*

**What this covers:** Design an ad click aggregator — high-volume event ingestion with near-real-time aggregation for dashboards and accurate batch aggregation for billing.

**Key design decisions:**
- Dual pipeline (Lambda Architecture): Speed layer (Flink/Kafka Streams → real-time counts) + Batch layer (Spark → accurate historical counts); merge for read queries
- Event ingestion: click events → Kafka (partitioned by ad_id); 10K events/sec → scale horizontally; Kafka retains 7 days for reprocessing
- Deduplication: Bloom filter in Flink for same-session dedup; exact dedup via Redis SETNX with TTL for cross-session; remove bot clicks via fraud filter
- Aggregation windows: 1-minute tumbling windows in Flink; store in Redis sorted sets for real-time dashboard; Spark hourly/daily for billing-accurate counts
- Query API: GET /clicks?ad_id=X&start=T1&end=T2&granularity=minute; served from pre-aggregated results store
- Fault tolerance: Flink checkpoints every 60s; on failure, replay from last checkpoint offset; exactly-once via checkpoint + transactional writes
- Storage: real-time counts in Redis; historical in Cassandra (ad_id + window start → count); compact with time-series optimization

**Key takeaway:** Lambda architecture is necessary here — real-time Flink for dashboards (low latency, approximate), Spark batch for billing (high accuracy, high latency); don't try to serve both from one pipeline.

### Cdn Design

*File: `05-hld-problems/03-hard/cdn-design.md`*

**What this covers:** Design a CDN — geographically distributed content delivery with edge caching, cache invalidation, HTTPS termination, and origin offload.

**Key design decisions:**
- Edge PoP (Point of Presence): 200+ global locations; each PoP has cache cluster + reverse proxy + TLS termination; requests route to nearest PoP via Anycast BGP
- Cache hierarchy: L1 (PoP-local SSD cache) → L2 (regional aggregation cache) → Origin; cache-hit at L1 serves in <20ms; L2 in <50ms; origin only for misses
- Cache key: URL + Vary header (language, device type); CDN can cache different versions for mobile vs desktop, or by language
- TTL strategy: static assets (images, JS, CSS) → long TTL (1 year, versioned URL); HTML pages → short TTL (5 min) or no cache
- Cache invalidation: CDN-wide purge API (costly, use sparingly); URL versioning preferred (append hash to filename → new URL = new cache key)
- Origin shield: single aggregation node per region that talks to origin; prevents thundering herd on origin when cache expires for popular content
- DDoS protection: edge absorbs volumetric attacks; rate limiting at PoP; challenge-response (CAPTCHA) for bot traffic; BGP anycast for resilience

**Key takeaway:** Anycast routing + origin shield are the two CDN-specific design elements interviewers test — Anycast routes to nearest PoP automatically; origin shield prevents cache stampede on popular content.

### Chat System

*File: `05-hld-problems/03-hard/chat-system.md`*

**What this covers:** Design a team chat system (Slack) — real-time channel messaging with presence indicators, thread replies, file sharing, and search across message history.

**Key design decisions:**
- Connection layer: WebSocket gateway servers; each user connects to a gateway; Redis tracks {user_id → gateway_id}; gateways stateless except connection state
- Message routing: sender → gateway → Kafka → fan-out service → each member's gateway → WebSocket push to clients
- Channel membership: channel members stored in DB + cached in Redis; fan-out scope determined by membership list
- Message storage: Cassandra for messages (channel_id as partition key, timestamp as clustering key); append-only; query by channel + time range
- Threads: each message can have a thread; thread replies stored separately; thread_id = parent message_id
- Presence: heartbeat every 30s; presence state (online/away/offline) in Redis with TTL; propagated to workspace members on change
- Search: Elasticsearch indexes message text; index on (workspace_id, channel_id, content); query by workspace + text + time range

**Key takeaway:** The fan-out routing layer (Redis: user → gateway mapping) is the core architectural challenge — without it, you don't know which server to push messages to.

### Distributed Cache

*File: `05-hld-problems/03-hard/distributed-cache.md`*

**What this covers:** Design a distributed cache (Redis Cluster / Memcached) — horizontal scaling of in-memory key-value storage with consistent hashing, replication, and eviction.

**Key design decisions:**
- Consistent hashing: keys distributed across nodes using hash ring; virtual nodes (vnodes) ensure even load; adding/removing node only moves O(K/N) keys
- Replication: each primary has 1–2 replicas; async replication for performance; replica auto-promotes on primary failure (Sentinel/Cluster)
- Eviction policies: LRU (evict least recently used), LFU (evict least frequently used); choose based on access pattern; allkeys-lru for pure cache
- Cache-aside pattern: application checks cache → miss → fetch from DB → populate cache; avoids stale data on write
- Write-through vs write-behind: write-through (write to cache + DB atomically) vs write-behind (write to cache, async DB flush); write-behind risks data loss
- Hot key problem: single popular key overwhelms one node; solution: local in-process micro-cache + key sharding (append shard ID suffix)
- Failure handling: circuit breaker on cache; fall through to DB on cache unavailability; don't crash on cache miss

**Key takeaway:** Consistent hashing with virtual nodes is the foundation — getting the hot key problem right (local micro-cache + key sharding) separates senior candidates.

### Distributed Job Scheduler

*File: `05-hld-problems/03-hard/distributed-job-scheduler.md`*

**What this covers:** Design a distributed job scheduler — executing millions of one-off and recurring jobs reliably with at-least-once guarantees, exactly-once prevention, and worker failure handling.

**Key design decisions:**
- Job store: PostgreSQL as source of truth; job row: {job_id, cron_expression, next_run_time, status, worker_id}; index on next_run_time for efficient polling
- Scheduler: leader node (via distributed lock) scans for due jobs (next_run_time ≤ now); claims job via CAS update (status: PENDING → CLAIMED); publishes to Kafka
- Worker pool: stateless workers pull from Kafka; execute job; update status to COMPLETED or FAILED in DB; heartbeat for long-running jobs
- At-least-once: if worker dies mid-job, timeout detection (last_heartbeat + timeout < now) → re-queue; idempotent job design required
- Exactly-once prevention: idempotency key per (job_id, scheduled_time) → deduplicate in DB with unique constraint; compensating rollback if re-run
- Distributed leader election: ZooKeeper / etcd ephemeral node; only leader schedules; follower promotes if leader crashes within TTL
- Monitoring: job execution latency, miss rate (jobs running past scheduled time), DLQ for repeatedly failed jobs

**Key takeaway:** CAS-based job claiming (optimistic locking on status field) prevents two workers running the same job — combine with idempotent job logic as defense in depth.

### Distributed Message Queue

*File: `05-hld-problems/03-hard/distributed-message-queue.md`*

**What this covers:** Design a distributed message queue (Kafka) — durable, ordered, partitioned message storage with consumer groups and exactly-once delivery semantics.

**Key design decisions:**
- Partitioning: topic split into P partitions; each partition is an ordered append-only log; partition key determines which partition; enables parallelism
- Durability: messages written to disk + replicated to ISR (in-sync replicas); acks=all ensures no data loss; configurable retention (time or size)
- Consumer groups: each group gets independent offset cursor per partition; add consumers = parallel consumption up to partition count
- Offset management: offsets stored in __consumer_offsets topic; auto-commit vs manual commit; commit after processing for at-least-once
- Backpressure: consumers pull at their own rate; producers never overwhelm consumers; natural backpressure via pull model
- Exactly-once: idempotent producer (epoch + sequence) eliminates duplicates; transactional API for atomic multi-partition writes + offset commit
- Compaction: log compaction retains only latest value per key; enables Kafka as a changelog / state store (source of truth)

**Key takeaway:** Kafka's partition-based parallelism + pull model + ISR replication = high throughput + durability; consumer groups make it trivially scalable to add downstream consumers.

### Dropbox Sync

*File: `05-hld-problems/03-hard/dropbox-sync.md`*

**What this covers:** Design Dropbox file sync — efficient bi-directional file synchronization with delta sync, conflict resolution, and multi-device consistency.

**Key design decisions:**
- File chunking: files split into 4MB chunks; each chunk content-addressed by SHA256 hash; upload only changed chunks (delta sync saves 90% bandwidth on typical edits)
- Sync protocol: client computes local chunk manifest → sends to server → server returns list of missing chunks → client uploads only those → server assembles file
- Change detection: client watches filesystem events (inotify/FSEvents); debounce 200ms; compute diff; sync only changed files
- Conflict resolution: if two devices edit same file concurrently → both versions preserved; user sees "filename (conflicted copy)" + original; no auto-merge
- Metadata service: file tree (file_id, parent_folder_id, name, version, chunks[]) in PostgreSQL; version vector per file; chunk store in S3 keyed by hash
- Offline support: local SQLite tracks pending sync queue; batch uploads on reconnect; reads work offline from local copy
- Bandwidth optimization: rsync-like differential sync; skip unchanged chunks via hash comparison; compression for text files

**Key takeaway:** Content-addressed chunk storage (SHA256) enables deduplication and delta sync simultaneously — the chunk hash tells you both what to skip uploading and whether a chunk already exists globally.

### Github Code Repo

*File: `05-hld-problems/03-hard/github-code-repo.md`*

**What this covers:** Design GitHub — code repository hosting with Git operations, pull request workflows, code search, and CI/CD pipeline triggering at millions-of-repos scale.

**Key design decisions:**
- Repository storage: Git objects (blobs, trees, commits, tags) stored in object store; repositories as bare Git repos on distributed storage (Gitaly at GitLab, network of NFS at GitHub)
- Repository routing: consistent hashing routes repository operations to specific storage nodes; replica set per shard for HA
- Clone/fetch: large repos offloaded to CDN (pack-objects for smart HTTP); popular repos cached at CDN edge; partial clone for monorepos
- Pull request model: PR = branch + metadata; diff computed on PR creation and cached; code review inline comments stored as GitHub Suggestions objects
- Code search: Elasticsearch full-text index of repository content; incremental indexing on push; indexed by repo, path, language, content
- Webhooks: push event → fan-out to registered webhooks; at-least-once via retry queue; CI/CD systems (GitHub Actions) triggered via webhook
- CI/CD: GitHub Actions = YAML workflow definition + runner infrastructure; job queue in PostgreSQL; runners pull jobs; artifact storage in S3

**Key takeaway:** Repository storage routing is the core scalability challenge — consistent hashing with replica sets per shard ensures both load distribution and HA; Git's content-addressable storage (SHA1 objects) naturally enables deduplication.

### Google Drive

*File: `05-hld-problems/03-hard/google-drive.md`*

**What this covers:** Design Google Drive — cloud file storage with real-time collaboration, conflict resolution, fine-grained permissions, and offline support at petabyte scale.

**Key design decisions:**
- File storage: chunked uploads (4MB chunks); each chunk content-addressed by SHA256 hash → deduplication across all users; stored in GCS/S3
- Metadata: file tree (folder hierarchy), file versions, chunk manifests stored in PostgreSQL or Spanner; chunk store keyed by hash
- Sync protocol: client maintains local file tree + version vectors; on upload, diff local vs server state; upload only changed chunks (delta sync)
- Conflict resolution: last-write-wins for simple files; OT/CRDT for collaborative documents (Google Docs); version branching for offline edits
- Permissions: ACL table (file_id, user_id, permission_level); inherited permissions (folder grants access to children); Google Workspace integration
- Real-time collaboration: WebSocket channel per document; operational transformation (OT) for concurrent edits; server applies OT to resolve conflicts
- Offline support: local SQLite file manifest; sync changes queue persisted locally; push all pending changes on reconnect

**Key takeaway:** Content-addressed chunk storage (SHA256 hash as key) enables global deduplication — if 1000 users upload the same file, only one copy is stored; delta sync means only changed chunks are transferred.

### Google Maps

*File: `05-hld-problems/03-hard/google-maps.md`*

**What this covers:** Design Google Maps — map tile serving, road graph routing, real-time traffic ingestion from GPS data, and sub-second ETA computation at global scale.

**Key design decisions:**
- Map tiles: pre-rendered vector/raster tiles at zoom levels 0–20 stored in S3; served via CDN; tile key = (zoom, x, y) quadtree coordinates
- Routing graph: road network as directed weighted graph (nodes = intersections, edges = road segments); stored in custom binary format; loaded into memory per region
- Routing algorithm: Dijkstra for short distances; Contraction Hierarchies (CH) for long routes (200× faster than plain Dijkstra); pre-process graph offline
- Real-time traffic: GPS pings from active navigating users → Kafka → stream processing → traffic speed per road segment → update edge weights in routing graph
- ETA: route distance / traffic-adjusted speed per segment; ML model corrects for time-of-day, weather, incidents
- Geospatial indexing: H3 hexagonal cells for spatial queries (nearby POIs, traffic density per area); R-tree for bounding-box queries
- Map updates: OSM + commercial providers → validate → conflate → push to tile render pipeline; edge case: road closures propagate in <5 min

**Key takeaway:** Contraction Hierarchies preprocessing is what makes sub-second routing possible — plain Dijkstra on a full road network takes seconds; CH reduces it to milliseconds by pre-computing shortcuts.

### Hotel Booking

*File: `05-hld-problems/03-hard/hotel-booking.md`*

**What this covers:** Design a hotel booking system (Booking.com) — availability search, double-booking prevention, concurrent reservation handling, and payment integration.

**Key design decisions:**
- Availability search: Elasticsearch for fast full-text + geo + date range queries; pre-computed availability calendar (bitmap per room per date) for O(1) lookup
- Room inventory: room_availability table (room_id, date) with unique constraint; prevents double-booking at DB constraint level
- Reservation flow: two-phase — HOLD (lock room for 10 min, idempotent) → CONFIRM (complete payment, convert hold to booking); hold TTL prevents abandoned reservations from blocking inventory
- Concurrency: SELECT FOR UPDATE (pessimistic) or optimistic locking (version field + CAS retry); pessimistic preferred for short booking transactions
- Search ranking: hotel scoring by price, rating, distance, availability; Elasticsearch custom scoring function; ML re-ranking for personalization
- Flash sales: popular hotels → high contention; Redis atomic DECR for available count; actual DB availability updated async; Redis as fast semaphore
- Pricing: dynamic pricing engine (base price × demand multiplier); price varies by date, season, advance booking; stored with room inventory

**Key takeaway:** Two-phase HOLD → CONFIRM with a 10-minute TTL is the key pattern — it prevents overbooking while not blocking inventory indefinitely; the unique DB constraint on (room_id, date) is the final safety net.

### Llm Chat System

*File: `05-hld-problems/03-hard/llm-chat-system.md`*

**What this covers:** Design an LLM chat system (ChatGPT) — GPU inference serving with streaming token delivery, context window management, and cost-efficient routing at massive scale.

**Key design decisions:**
- Inference serving: requests routed to GPU servers (A100/H100 clusters); model loaded in GPU VRAM; batch requests for efficiency (dynamic batching)
- Streaming: SSE (Server-Sent Events) for token-by-token streaming; HTTP/2 for multiplexed connections; no WebSocket needed (unidirectional)
- Context management: conversation history stored in DB (PostgreSQL); retrieved on each request; truncated with sliding window when exceeding context limit
- Routing: load balancer routes to GPU servers by model (gpt-4, gpt-3.5, claude); KV-cache locality routing (route to server with cached context prefix)
- Cost optimization: small/fast model for simple queries; large model only for complex queries; prompt caching for repeated system prompts
- Rate limiting: token-based rate limits (not request-based); 1M tokens/day per user; token counting per request before processing
- Conversation storage: PostgreSQL for conversation metadata; Cassandra for message history (user_id + conversation_id partition key)

**Key takeaway:** Token streaming via SSE (not polling) is critical for UX — users see partial responses immediately; GPU routing with KV-cache locality minimizes latency on follow-up messages.

### Metrics Monitoring System

*File: `05-hld-problems/03-hard/metrics-monitoring-system.md`*

**What this covers:** Design a metrics monitoring system (Prometheus + Grafana) — time-series metric collection, storage, query, and alerting for distributed service observability.

**Key design decisions:**
- Collection: pull model (Prometheus scrapes /metrics endpoint every 15s) vs push model (StatsD, Datadog Agent push to collector); pull easier to discover what's down
- Metric types: Counter (monotonically increasing, e.g. requests_total), Gauge (can decrease, e.g. memory_usage), Histogram (latency buckets), Summary
- Time-series storage: custom columnar format; data points sorted by (metric_name, labels, timestamp); compressed with delta + gorilla encoding
- Cardinality: high-cardinality labels (user_id, request_id) explode storage — enforce label cardinality limits; only low-cardinality labels (env, service, region)
- Query: PromQL — rate(requests_total[5m]) → compute per-second rate over 5 min; histogram_quantile(0.99, ...) for p99 latency
- Alerting: alert rules evaluated every 15s against time-series DB; fire when condition holds for >2 min (avoid flapping); route via Alertmanager to PagerDuty/Slack
- Long-term storage: Thanos or Cortex for multi-region aggregation + object storage retention (S3); Prometheus local storage limited to ~15 days

**Key takeaway:** Cardinality control is the primary operational challenge — unbounded label values (user_id, trace_id) make the time-series DB explode in memory; enforce it at ingestion.

### Payment System

*File: `05-hld-problems/03-hard/payment-system.md`*

**What this covers:** Design a payment system — the most correctness-critical system design problem; covers double-charge prevention, double-entry accounting, PCI compliance, and reconciliation.

**Key design decisions:**
- Idempotency: every payment request has an idempotency key (client-generated UUID); server stores key → result; retry returns same result without re-charging
- Double-entry accounting: every transaction has debit + credit entries; ledger entries are immutable; account balance = sum of all entries
- Payment flow: initiate → payment processor (Stripe/Braintree) → async webhook confirmation → order update; never wait synchronously on payment response
- Saga pattern: cross-service payment (reserve funds → charge → fulfill); compensating transaction on failure (refund); distributed without 2PC
- PCI DSS compliance: never store raw card numbers; tokenize with payment processor; TLS everywhere; cardholder data isolated in separate service
- Reconciliation: daily batch job compares internal ledger vs payment processor report; flag mismatches for manual review
- Failure handling: timeout ≠ failure; query payment processor for status; idempotency key prevents double-charge on retry

**Key takeaway:** Idempotency key + double-entry ledger are the two non-negotiable foundations — timeout does not mean failure, so always query status before retrying a payment.

### Rag System

*File: `05-hld-problems/03-hard/rag-system.md`*

**What this covers:** Design a RAG (Retrieval-Augmented Generation) system — grounding LLM responses in a document corpus via semantic search, embedding vectors, and prompt construction.

**Key design decisions:**
- Ingestion pipeline: documents → chunking (512–1024 tokens, overlap 50 tokens) → embedding model → vector embeddings → vector DB (Pinecone/Weaviate/pgvector)
- Chunking strategy: fixed-size chunks vs semantic chunks (sentence-boundary aware); overlap prevents context loss at chunk boundaries
- Retrieval: user query → embed query → ANN search in vector DB (HNSW index) → top-K semantically similar chunks → re-rank with cross-encoder
- Hybrid search: semantic (dense vector) + keyword (BM25) → combine scores with RRF (Reciprocal Rank Fusion); catches both semantic and exact matches
- Prompt construction: system prompt + retrieved chunks + user query → sent to LLM; chunk count limited by context window (e.g., 5 chunks × 512 tokens)
- Citation: each chunk tagged with source document + page; LLM instructed to cite sources; UI displays reference links
- Freshness: incremental ingestion for new documents; chunk-level deduplication by content hash; re-embed on document update

**Key takeaway:** HNSW vector search + re-ranking (cross-encoder) is the retrieval backbone — ANN gives recall, re-ranking gives precision; hybrid BM25+vector catches cases where semantic search misses exact keyword matches.

### Realtime Gaming Leaderboard

*File: `05-hld-problems/03-hard/realtime-gaming-leaderboard.md`*

**What this covers:** Design a real-time gaming leaderboard at hard difficulty — multiple leaderboard scopes (global, regional, tournament, friends), sub-second updates, and millions of concurrent players.

**Key design decisions:**
- Core: Redis ZADD/ZREVRANK per leaderboard scope; O(log N) update and rank query; separate sorted set per scope (global, per-region, per-tournament)
- Friends leaderboard: can't pre-compute for all friend groups → query: fetch friend IDs (Redis set) → ZSCORE for each friend → sort client-side; or use secondary sorted set per user updated on score change
- Score aggregation: match end → Kafka → score aggregation service → atomic ZADD; batch updates to reduce Redis write load
- Tournament leaderboard: TTL-based sorted set per tournament; active tournament data in Redis; archive to PostgreSQL on completion
- Scale: 100M players × 8 bytes (score) ≈ 800 MB per sorted set; fits in one Redis node; shard by game_id for multiple concurrent games
- Nearby rank: ZREVRANK (player rank) → ZREVRANGE (rank-5 to rank+5) → get neighbor entries; O(log N) + O(K)
- Percentile: (total_players - rank) / total_players × 100; ZCARD O(1) for total count

**Key takeaway:** This is leaderboard easy problem × multiple scopes — the friends leaderboard is the hard part because you can't pre-compute; solve it with on-demand ZSCORE lookup for friend IDs.

### Ride Sharing

*File: `05-hld-problems/03-hard/ride-sharing.md`*

**What this covers:** Design a ride-sharing platform (Uber) — real-time driver location tracking, geospatial matching, dynamic pricing, and trip management at global scale.

**Key design decisions:**
- Location tracking: drivers send GPS updates every 4s → Kafka → location store (Redis with GEOADD); high write throughput, short TTL
- Geospatial indexing: S2 (Google) or H3 (Uber) cell hierarchy; convert GPS coords to cell ID; find nearby drivers via cell + neighbors lookup
- Driver matching: when rider requests → query Redis for drivers within 1km radius → rank by ETA (distance ÷ speed) → offer to nearest; retry expanding radius
- State machine: driver states (offline → available → en-route-to-pickup → on-trip → available); state transitions trigger events
- Surge pricing: demand/supply ratio per H3 cell; if demand/supply > threshold → multiply base price; real-time recomputed every 60s
- Trip management: PostgreSQL for trip records (idempotent); Kafka for trip events (trip_started, trip_ended, payment_completed)
- ETA computation: pre-computed road graph (OSRM) → shortest path → adjust with real-time traffic; GPU acceleration for mass matching

**Key takeaway:** The geospatial challenge — H3/S2 cell indexing in Redis — is what makes Uber work at scale; without it, "find nearby drivers" degrades to a full table scan with haversine distance.

### Search System

*File: `05-hld-problems/03-hard/search-system.md`*

**What this covers:** Design a web search engine (Google) — the complete pipeline from web crawling to real-time index serving with relevance ranking at internet scale.

**Key design decisions:**
- Pipeline: Web Crawler → Document Store (raw HTML in S3) → Parser/Indexer → Inverted Index → Ranker → Query Serving
- Inverted index: word → sorted list of (doc_id, TF score) entries; sharded by term hash; each shard fits in RAM for fast lookup
- Relevance: TF-IDF/BM25 for content relevance + PageRank for authority; combined scoring with learned ranking model (LambdaMART)
- Index freshness: periodic batch rebuild (Hadoop) for most of web; incremental updates for news/hot content via streaming pipeline
- Query serving: <100ms requirement → all data in memory; sharded index; fan-out to all shards, merge top-K, rank, return
- Personalization: search history, location, language, SafeSearch preference → re-rank results per user context
- Knowledge Graph: structured data (infoboxes, answer boxes) stored separately in graph DB; surfaced above blue links for direct answers

**Key takeaway:** The inverted index + BM25 scoring is the core — everything else (crawling, PageRank, personalization) feeds into getting the right documents into the top-10 results.

### Stock Exchange

*File: `05-hld-problems/03-hard/stock-exchange.md`*

**What this covers:** Design a stock exchange — matching engine, order book, market data distribution, and microsecond-latency infrastructure for financial trading systems.

**Key design decisions:**
- Order book: per-symbol price-time priority book; buy orders (bids) sorted descending by price; sell orders (asks) sorted ascending; best bid/ask = NBBO
- Matching engine: single-threaded per symbol to avoid locks; match buy vs sell when bid ≥ ask; price-time priority (same price → earliest order wins)
- Order types: market (execute immediately at best price), limit (execute at specified price or better), stop (trigger at price, then market)
- Data structures: price level → doubly-linked list of orders; price levels in Red-Black tree; O(log N) insert/cancel, O(1) best bid/ask
- Market data: after every trade → publish trade feed (price, qty, time) + order book delta to all subscribers; fan-out via multicast UDP or pub-sub
- Latency: co-location (exchange rack), FPGA for market data processing, kernel bypass (DPDK), CPU pinning; target <100μs round-trip
- Persistence: event sourcing (log every order event); replay log to reconstruct order book; WAL for crash recovery

**Key takeaway:** The matching engine must be single-threaded per symbol — any locking or coordination introduces latency spikes that fairness-sensitive traders exploit; price-time priority is non-negotiable for regulatory compliance.

### Ticketmaster Seat Booking

*File: `05-hld-problems/03-hard/ticketmaster-seat-booking.md`*

**What this covers:** Design Ticketmaster — the hardest concurrency problem in system design; flash sale seat reservation where millions compete for thousands of seats in seconds.

**Key design decisions:**
- Seat reservation: two-phase HOLD (lock seat in Redis for 10 min) → PURCHASE (DB commit + payment); Redis SETNX seat_id = user_id for atomic single-winner guarantee
- Concurrency at flash sale: virtual waiting room (queue users; release N per second into booking flow); prevents thundering herd on seat inventory
- Seat status: real-time seat map via WebSocket; status states: AVAILABLE → HELD (10 min TTL) → SOLD; broadcast seat_id status changes to all connected clients
- Inventory atomicity: Redis SETNX (set if not exists) for seat hold — exactly one user wins; if SETNX returns 0, seat is already held
- Payment flow: held seat → initiate payment (Stripe) → on success, publish SEAT_SOLD event → DB commit booking; compensating transaction on payment failure releases hold
- Waitlist: users on waitlist notified via WebSocket/push when hold expires (seat released); first in waitlist gets hold offer
- DB design: events, venues, seats, bookings tables; seat_map indexed by (event_id, section, row, seat_number); booking record is immutable

**Key takeaway:** Redis SETNX + 10-min TTL is the atomic seat locking mechanism — it's the only way to guarantee a single winner under millions of concurrent hold attempts without DB deadlocks.
---

# LLD — Low-Level Design

## LLD: Core Problems

### Design Parking Lot

*File: `06-lld/05-problems/01-core-problems/01-design-parking-lot.md`*

**What this covers:** Design a Parking Lot — the quintessential LLD problem that tests your ability to model real-world entities, manage state, and apply design patterns.

**Key concepts:**
- Core Entities: `ParkingLot` (Singleton), `ParkingFloor`, `ParkingSpot` (Enum for size: Compact, Large, Handicapped), `Vehicle` (Enum for type: Car, Truck, Bike), `Ticket`.
- The problem: assigning the correct spot based on vehicle type and calculating the fee upon exit.
- Patterns: 
  - Singleton: to ensure only one Parking Lot instance exists.
  - Strategy: for dynamic pricing calculation (e.g., hourly rate vs flat rate).
  - Factory: to generate `Vehicle` objects or assign parking spots.
- Concurrency: Thread safety is critical when two vehicles try to enter simultaneously. The `assignSpot()` method must be synchronized or use concurrent data structures.

**Key takeaway:** A solid Parking Lot design demonstrates your grasp of OOP fundamentals. Focus on separation of concerns — the `ParkingLot` delegates finding a spot to `ParkingFloor`, which checks its `ParkingSpot`s.

### Design Rate Limiter

*File: `06-lld/05-problems/01-core-problems/02-design-rate-limiter.md`*

**What this covers:** Design a Rate Limiter — an essential system design and LLD problem focusing on algorithms, concurrency, and time-based state management.

**Key concepts:**
- Algorithms: Token Bucket (most common, used by AWS), Leaking Bucket (queue-based), Fixed Window, Sliding Window Log, Sliding Window Counter.
- Core Entities: `RateLimiter` (interface), `TokenBucketLimiter` (implementation), `UserRule` (limits per user/IP).
- The problem: efficiently tracking requests and dropping those that exceed the limit without locking up the system.
- Concurrency: Highly concurrent. `allowRequest()` must be thread-safe. Use `AtomicInteger` or explicit locking.
- Lazy Refill: Instead of a background thread constantly refilling buckets (expensive), refill tokens *on-demand* when the next request arrives by calculating time elapsed since the last request.

**Key takeaway:** If you implement Token Bucket, use lazy refill and `ConcurrentHashMap` for storing buckets per user ID. For Sliding Window, use a thread-safe Deque or Redis Sorted Sets.

### Design Tic Tac Toe

*File: `06-lld/05-problems/01-core-problems/03-design-tic-tac-toe.md`*

**What this covers:** Design Tic-Tac-Toe — a classic introductory LLD problem focusing on state machines, game loops, and optimized win-checking logic.

**Key concepts:**
- Core Entities: `Game` (orchestrator), `Board` (2D array or 1D array), `Player`, `Piece` (Enum: X, O).
- The Game Loop: wait for input, validate move, update board, check win/draw, switch player.
- Optimized Win Check: $O(1)$ instead of $O(N)$. Keep track of row sums, col sums, and two diagonal sums. If any sum equals $N$ or $-N$, the current player wins.
- Extension: Design it for an $N \times N$ board.
- Patterns: Strategy for different win conditions (if rules change), State pattern for game lifecycle (Not Started, In Progress, Finished).

**Key takeaway:** The $O(1)$ win checking logic (using row/col counter arrays) is the main "gotcha" of this problem. A naive $O(N)$ sweep after every move is often penalized.

### Design Vending Machine

*File: `06-lld/05-problems/01-core-problems/04-design-vending-machine.md`*

**What this covers:** Design a Vending Machine — the textbook example for demonstrating the State Design Pattern.

**Key concepts:**
- Core Entities: `VendingMachine` (context), `State` (interface), `Item`, `Inventory` (manages stock), `Coin`/`Note` (Enums).
- States: `IdleState`, `HasMoneyState`, `DispensingState`, `OutOfStockState`.
- State Pattern implementation: Each state implements methods like `insertCoin()`, `selectProduct()`, `dispense()`, and `cancel()`. If an action is invalid for a state (e.g., `dispense()` while in `IdleState`), it throws an exception.
- Workflow: User inserts coin -> transitions to `HasMoneyState`. User selects product -> validates stock -> transitions to `DispensingState`. Dispenses item, returns change -> transitions back to `IdleState`.

**Key takeaway:** DO NOT use if-else blocks for state management in a Vending Machine interview. The interviewer specifically wants to see a clean, polymorphic State Pattern implementation.

### Design Splitwise

*File: `06-lld/05-problems/01-core-problems/05-design-splitwise.md`*

**What this covers:** Design Splitwise — a popular problem that tests OOP modeling of complex entities (Expenses, Splits) and algorithmic graph simplification.

**Key concepts:**
- Core Entities: `User`, `Group`, `Expense`, `Split` (interface).
- Split Types (Strategy Pattern): `EqualSplit`, `ExactSplit`, `PercentageSplit`.
- Expense Management: An `Expense` has an amount, a paid-by user, and a list of `Split`s detailing who owes what.
- The Algorithm: Debt simplification. To minimize transactions, model users as nodes and debts as directed edges. 
  1. Calculate the net balance for each user (sum of incoming - sum of outgoing).
  2. Separate users into "debtors" (negative balance) and "creditors" (positive balance).
  3. Greedily match the largest debtor with the largest creditor to settle debts efficiently.

**Key takeaway:** The OOP part requires the Strategy pattern for different split types. The algorithmic part requires the "Minimize Cash Flow" greedy graph algorithm. Both are equally important for passing this interview.
---

## LLD: Frequent Problems

### Design Bookmyshow

*File: `06-lld/05-problems/02-frequent-problems/06-design-bookmyshow.md`*

**What this covers:** Design BookMyShow (Ticket Booking) — a high-frequency LLD problem that tests your ability to handle concurrency (seat locking) and manage complex relationships (Cinemas, Screens, Shows).

**Key concepts:**
- Core Entities: `Cinema`, `Screen`, `Show`, `Seat`, `Booking`.
- State Pattern: `Seat` transitions between Available, Locked, and Booked.
- Concurrency (The core challenge): Two users trying to book the same seat simultaneously. 
  - DB approach: Row-level locking (Pessimistic: `SELECT FOR UPDATE`, or Optimistic with versioning).
  - In-memory LLD approach: Use `ConcurrentHashMap` for locks or synchronize the `lockSeat()` method.
- TTL (Time To Live): When a user selects seats, they are "Locked" for 5-10 minutes. If payment isn't completed, a background job (or delay queue) must revert them to "Available".

**Key takeaway:** The interviewer is looking for how you prevent double-booking. Clearly explain the difference between a `Seat` (physical chair) and a `ShowSeat` (that chair for a specific movie at a specific time). The lock must be on the `ShowSeat`.

### Design Chess

*File: `06-lld/05-problems/02-frequent-problems/07-design-chess.md`*

**What this covers:** Design Chess — a complex OOP modeling problem focusing on inheritance, polymorphism, and validating complex business rules.

**Key concepts:**
- Core Entities: `Game`, `Board` (8x8 array of `Box`), `Player`, `Move`.
- Polymorphism: `Piece` is an abstract base class with an abstract method `isValidMove(start, end)`. Concrete classes (`King`, `Queen`, `Knight`, etc.) implement their specific movement logic.
- The Game Loop: A central `Game` orchestrator manages player turns, gets the proposed move, checks if it's valid for that piece, and executes it.
- Tricky Rules: 
  - Castling: requires tracking if the King and Rook have moved yet.
  - En Passant: requires knowing the exact *previous* move.
  - Check/Checkmate: requires simulating a move and seeing if the King is still under attack.

**Key takeaway:** Keep the pieces "dumb" regarding the state of the entire game. A `Piece` should only validate its geometric move (e.g., Knight moves in an L-shape). The `Board` or `Game` must validate if the path is blocked by other pieces.

### Design Snake And Ladder

*File: `06-lld/05-problems/02-frequent-problems/08-design-snake-and-ladder.md`*

**What this covers:** Design Snake and Ladder — a simpler, entity-relationship focused LLD problem that tests basic game loops and random number generation handling.

**Key concepts:**
- Core Entities: `Game`, `Board`, `Player`, `Dice`, `Jumper` (class representing both Snakes and Ladders).
- The Board: usually an array or map of size 100.
- The Jumper: a `Snake` is just a `Jumper` where `start > end`. A `Ladder` is a `Jumper` where `end > start`. Representing both as a single `Jumper(start, end)` class simplifies logic.
- The Game Loop: Roll dice, calculate new position, check for Jumper at new position, update position, check for win condition (position >= 100).
- Extensions: Multiple dice, rolling a 6 grants an extra turn (requires a `while` loop inside the player's turn), different board sizes.

**Key takeaway:** Do not create separate `Snake` and `Ladder` classes. Creating a single `Jumper` (or `Entity`) class that maps a `start` position to an `end` position makes the logic incredibly clean.

### Design Elevator System

*File: `06-lld/05-problems/02-frequent-problems/09-design-elevator-system.md`*

**What this covers:** Design Elevator System — a classic problem testing State machines, scheduling algorithms, and concurrent request handling.

**Key concepts:**
- Core Entities: `ElevatorSystem` (orchestrator), `ElevatorCar`, `Button` (Internal/External), `Display`.
- State Pattern: `ElevatorState` (MovingUp, MovingDown, Idle, DoorOpen).
- The Algorithm: SCAN (or LOOK) algorithm. The elevator maintains two min/max heaps or sorted sets: one for upward requests, one for downward requests. It sweeps fully up, then fully down.
- Strategy Pattern: the `ElevatorDispatchStrategy` decides *which* car gets a request (e.g., shortest wait time, nearest car moving in the same direction).
- Concurrency: Requests arrive asynchronously from different floors. The request queues must be thread-safe (e.g., `PriorityBlockingQueue`).

**Key takeaway:** Do not use a basic FIFO queue for elevator requests, or the elevator will bounce erratically. You must mention the SCAN/LOOK directional sweep algorithm and use sorted data structures.

### Design Comment System

*File: `06-lld/05-problems/02-frequent-problems/10-design-comment-system.md`*

**What this covers:** Design a Nested Comment System (like Reddit or HackerNews) — tests tree data structures, Composite pattern, and recursive rendering.

**Key concepts:**
- Core Entities: `User`, `Post`, `Comment`.
- Tree Structure (Composite Pattern): A `Comment` contains a list of `Comment`s (its children/replies).
- Database Storage: Storing trees in SQL is hard. 
  - Adjacency List (storing `parentId`) is simple but requires recursive queries to fetch deep threads.
  - Materialized Path (storing `path="1/4/7"`) allows fetching an entire thread in one query (`LIKE '1/4/%'`).
- Sorting (Strategy Pattern): Implement strategies for "Top" (upvotes - downvotes), "New", and "Controversial".
- Pagination: Fetching the whole tree is too heavy. You must support lazy-loading (e.g., "Load more comments...").

**Key takeaway:** The interviewer will heavily probe how you store and retrieve the nested structure from a database. Be prepared to explain the Materialized Path approach for O(1) thread retrieval.

### Design Hotel Management

*File: `06-lld/05-problems/02-frequent-problems/11-design-hotel-management.md`*

**What this covers:** Design a Hotel Management System — tests your ability to model real-world inventory with date-based constraints and dynamic pricing.

**Key concepts:**
- Core Entities: `Hotel`, `Room`, `RoomType` (Enum), `Guest`, `Reservation`, `Invoice`.
- Date-based Inventory: The hardest part is checking if a room is available between `startDate` and `endDate`. 
  - Option A: Store reservations as a list and check for overlapping date ranges (O(N) per room).
  - Option B: Use a timeline/interval tree for faster querying.
- Patterns: Factory (for generating specific room types), Strategy (for pricing — e.g., weekend rates vs weekday rates, or loyalty discounts).
- Concurrency: Similar to BookMyShow, multiple guests might try to book the last available Deluxe room for the same dates simultaneously. Require locks on the specific room or room type.

**Key takeaway:** The concept of an "Inventory" that varies over time is the core challenge. Make sure your `searchAvailableRooms()` method clearly handles date range overlaps logic.

### Design Atm

*File: `06-lld/05-problems/02-frequent-problems/12-design-atm.md`*

**What this covers:** Design an ATM System — an extensive OOP problem that heavily utilizes the State Pattern, Chain of Responsibility, and hardware integration abstraction.

**Key concepts:**
- Core Entities: `ATM` (context), `State` (interface), `CardReader`, `CashDispenser`, `BankService`, `Transaction`.
- State Pattern: `IdleState`, `HasCardState`, `SelectOperationState`, `DispensingState`. Handles the exact flow of the user interaction.
- Chain of Responsibility: Often used for the `CashDispenser`. A request for $170 goes to the $100 handler (dispenses 1, passes $70 down) -> $50 handler (dispenses 1, passes $20 down) -> $20 handler (dispenses 1).
- Hardware Abstraction: The ATM doesn't "know" how to physically spit out money. It calls `dispenser.dispense(amount)`, which acts as a Facade/Proxy to the hardware layer.

**Key takeaway:** Like the Vending Machine, ATM requires the State pattern. But it adds complexity via integration with an external `BankService` (which must handle the actual balance check and deduction via atomic transactions).

### Design Lru Cache

*File: `06-lld/05-problems/02-frequent-problems/13-design-lru-cache.md`*

**What this covers:** Design an LRU Cache — arguably the most famous data structure interview question. Tests your ability to combine primitive data structures to achieve O(1) time complexity for complex operations.

**Key concepts:**
- Core Data Structures: A `HashMap` (for O(1) lookups) + a `DoublyLinkedList` (for O(1) additions and removals).
- The Nodes: The nodes in the linked list must store BOTH the `key` and the `value` (so when you evict the tail node, you know which key to remove from the HashMap).
- `get(key)`: If present, return value AND move the node to the front (head) of the list. O(1).
- `put(key, value)`: If present, update value and move to front. If not present, add to front. If at capacity, remove the tail node (Least Recently Used) from both the list and the map. O(1).
- Dummy Head/Tail: Using a dummy head and dummy tail node eliminates all null checks when adding/removing nodes, drastically simplifying the code.

**Key takeaway:** Memorize the exact wiring of the `DoublyLinkedList` with dummy head and tail nodes. This question is so common that any hesitation on the pointer wiring is heavily penalized.

### Design Food Delivery

*File: `06-lld/05-problems/02-frequent-problems/14-design-food-delivery.md`*

**What this covers:** Design a Food Delivery System (e.g., UberEats, DoorDash) — a massive, multi-actor system focusing on order lifecycle, geolocation, and dynamic assignment.

**Key concepts:**
- Core Entities: `User`, `Restaurant`, `MenuItem`, `DeliveryAgent`, `Order`.
- State Pattern: `Order` lifecycle (`PLACED`, `ACCEPTED`, `PREPARING`, `OUT_FOR_DELIVERY`, `DELIVERED`).
- Observer Pattern: As the `Order` state changes, notifications must be pushed to both the `User` and the `Restaurant`.
- Strategy Pattern (Dispatching): How do you assign a `DeliveryAgent` to an order? You could use a `NearestAgentStrategy`, a `HighestRatedAgentStrategy`, or a `LeastBusyAgentStrategy`.
- Search: Implementing a menu search requires a Strategy pattern as well (search by name, category, or rating).

**Key takeaway:** In an LLD interview, you can't design the whole backend. Focus on the core domain models and specifically on the dispatching logic (Strategy) and the status updates (Observer).

### Design Locker Service

*File: `06-lld/05-problems/02-frequent-problems/15-design-locker-service.md`*

**What this covers:** Design an Amazon Hub / Locker Service — an inventory management problem focused on optimally matching package sizes to container sizes.

**Key concepts:**
- Core Entities: `LockerFacility`, `Locker` (Enum sizes: S, M, L, XL), `Package` (S, M, L, XL), `Order`, `CodeGenerator`.
- The Matching Algorithm: A package can fit in a locker of the *same* size or any *larger* size. You want to assign the smallest available locker that fits the package to preserve large lockers for large packages.
- Strategy Pattern: `LockerAssignmentStrategy` encapsulates the logic. (e.g., `OptimalFitStrategy`).
- Workflow: Delivery agent arrives -> requests locker for package size M -> system finds optimal locker -> opens locker -> generates 6-digit pickup code -> sends to user.
- Expiration: Lockers are held for ~3 days. A background job must sweep expired lockers, refund the user, and mark the locker available.

**Key takeaway:** The core algorithm is simple but easy to mess up. A Small package fits S, M, L, XL. A Large package only fits L, XL. Use a sorted mapping or Enums with size comparators to handle this cleanly.

### Design Notification System

*File: `06-lld/05-problems/02-frequent-problems/16-design-notification-system.md`*

**What this covers:** Design a Notification System — a common LLD question focusing on decoupling the generation of events from the delivery of messages.

**Key concepts:**
- Core Entities: `NotificationContext`, `NotificationDispatcher`, `User`, `NotificationTemplate`.
- Strategy Pattern (Channel): The system must send via Email, SMS, or Push. These are separate `DeliveryStrategy` classes.
- Chain of Responsibility (Fallback/Retry): If an SMS fails, automatically try Email. Link the handlers in a chain.
- Factory Pattern: To construct the correct notification object based on the event type (e.g., `OrderShippedEvent` generates a specific notification).
- Abstraction: The system sending the notification (e.g., the Billing Service) should not know *how* the user receives it. It just publishes an event to a queue, and the Notification System consumes it.

**Key takeaway:** The key to this problem is extensibility. When the interviewer asks "How do we add WhatsApp notifications?", you should just need to add a `WhatsAppStrategy` class without modifying core logic.

### Design Coupon System

*File: `06-lld/05-problems/02-frequent-problems/17-design-coupon-system.md`*

**What this covers:** Design a Coupon/Discount System — tests complex business rule validation and compounding mathematical operations using patterns.

**Key concepts:**
- Core Entities: `Cart`, `Item`, `Coupon`, `DiscountResult`.
- Strategy Pattern (Discount Type): `PercentageDiscount`, `FlatDiscount`, `BOGODiscount` (Buy One Get One).
- Chain of Responsibility (Validation): Before applying a coupon, it must pass a chain of checks: `ExpirationValidator` -> `MinimumCartValueValidator` -> `UserEligibilityValidator`.
- Composite Pattern (Stacking): If users can apply multiple coupons, create a `CompositeCoupon` that contains a list of coupons and applies them sequentially to the cart total.

**Key takeaway:** E-commerce pricing rules change daily. Hardcoding `if (coupon == "SUMMER50")` is an instant fail. Use the Strategy pattern so the Marketing team can configure new coupons via database rows, mapped to your generic strategies.
---

## LLD: Domain-Specific

### Design Mentorship Platform

*File: `06-lld/05-problems/03-domain-specific/18-design-mentorship-platform.md`*

**What this covers:** Design a Mentorship Platform (or calendar booking system) — focuses on availability, interval overlap detection, and two-sided marketplace matching.

**Key concepts:**
- Core Entities: `Mentor`, `Mentee`, `Session`, `Availability` (Time slots).
- Scheduling/Conflict Detection: The hardest part. You must check if a proposed session overlaps with any existing accepted sessions. Represent time as Unix timestamps and check if `new_start < exist_end && new_end > exist_start`.
- Strategy Pattern (Matching): Finding a mentor involves ranking them by relevance. Implement strategies like `SkillMatchStrategy`, `RatingStrategy`, or `AvailabilityStrategy`.
- State Pattern: `Session` transitions from `REQUESTED` -> `ACCEPTED` -> `IN_PROGRESS` -> `COMPLETED`.

**Key takeaway:** Handling time is tricky. Always store intervals as UTC timestamps, not formatted strings. Use a simple interval overlap check for the availability logic.

### Design Logger Library

*File: `06-lld/05-problems/03-domain-specific/19-design-logger-library.md`*

**What this covers:** Design a Logger Library — a classic framework-design problem that is the textbook use case for the Chain of Responsibility pattern.

**Key concepts:**
- Core Entities: `Logger` (Singleton), `LogAppender` (Strategy), `LogFilter` (Chain of Responsibility).
- Chain of Responsibility: Create handlers for `DEBUG`, `INFO`, `WARN`, `ERROR`. Link them: `DebugLogger -> InfoLogger -> WarnLogger -> ErrorLogger`. If the system is set to `WARN`, the `DebugLogger` and `InfoLogger` simply pass the request along without acting.
- Strategy Pattern (Appender): Where do the logs go? Provide strategies for `ConsoleAppender`, `FileAppender`, `DatabaseAppender`.
- Asynchronous Logging: For performance, don't write to disk on the main thread. Use a `BlockingQueue` and a background consumer thread to write logs (Producer-Consumer pattern).

**Key takeaway:** The interviewer wants to see Chain of Responsibility for log levels, Strategy for destinations, and Producer-Consumer (BlockingQueue) for async performance.

### Design Library Management

*File: `06-lld/05-problems/03-domain-specific/20-design-library-management.md`*

**What this covers:** Design a Library Management System — a comprehensive OOP modeling exercise testing inheritance, state management, and business rule enforcement.

**Key concepts:**
- Core Entities: `Library`, `Book`, `BookItem` (a specific physical copy), `Member`, `Librarian`.
- Inheritance: Differentiate between a `Book` (the abstract concept: Harry Potter, ISBN 123) and a `BookItem` (the physical copy: Barcode 999, placed on Rack 5).
- State Pattern: `BookItem` transitions between `AVAILABLE`, `LOANED`, `LOST`, `RESERVED`.
- Enforcement Rules: Max 5 books per user, max 10 days checkout. These are business rules that must be checked before a state transition.
- Fine Calculation: Use the Strategy pattern if fines vary by book type or member type.

**Key takeaway:** The biggest mistake candidates make is conflating `Book` and `BookItem`. A library has one `Book` record for "The Hobbit", but might own five physical `BookItem` copies. You checkout a `BookItem`, not a `Book`.

### Design Order Management

*File: `06-lld/05-problems/03-domain-specific/21-design-order-management.md`*

**What this covers:** Design an Order Management System (OMS) — tests your ability to handle complex, multi-step distributed workflows (like reserving inventory, charging payment, then confirming).

**Key concepts:**
- Core Entities: `Order`, `OrderLineItem`, `InventoryManager`, `PaymentProcessor`, `Warehouse`.
- State Pattern: `Order` moves through `CREATED`, `PENDING_PAYMENT`, `CONFIRMED`, `SHIPPED`, `DELIVERED`, `CANCELLED`.
- Saga Pattern (LLD variation): The orchestrator calls Inventory (reserve items), then Payment (charge card). If Payment fails, it must call Inventory (release items) to rollback the transaction.
- Observer Pattern: Notifications (email, SMS) triggered by state transitions.

**Key takeaway:** The main challenge is the rollback mechanism if a later step fails. Clearly define the `OrderOrchestrator` class that handles the try-catch block and invokes the compensating transactions (un-reserve inventory, refund payment) if necessary.

### Design Ride Sharing

*File: `06-lld/05-problems/03-domain-specific/22-design-ride-sharing.md`*

**What this covers:** Design a Ride Sharing System (e.g., Uber/Lyft) — tests matching algorithms, geospatial querying, and managing the lifecycle of a Ride.

**Key concepts:**
- Core Entities: `Rider`, `Driver`, `Ride`, `Location` (lat, long), `Vehicle`.
- Matching Strategy (Strategy Pattern): How to find the best driver? `NearestDriverStrategy`, `HighestRatedDriverStrategy`. Requires a spatial data structure (QuadTree or Geohash, usually abstracted behind a `LocationManager`).
- Pricing Strategy (Strategy Pattern): `SurgePricing`, `StandardPricing`, `DistanceBasedPricing`.
- State Pattern: `Ride` transitions: `REQUESTED`, `ACCEPTED`, `ARRIVING`, `IN_PROGRESS`, `COMPLETED`.
- Observer: `Rider` app observing the `Driver`'s location updates.

**Key takeaway:** Focus on the interfaces for the Strategy patterns (Matching and Pricing) and the State machine for the Ride. Abstract the complex geospatial math into a black-box `LocationService.getDriversWithinRadius()`.

### Design Pub Sub

*File: `06-lld/05-problems/03-domain-specific/23-design-pub-sub.md`*

**What this covers:** Design a Pub-Sub Messaging System (like Kafka or RabbitMQ) — an advanced systems problem disguised as LLD, testing deep understanding of concurrency, queues, and message delivery guarantees.

**Key concepts:**
- Core Entities: `Topic`, `Message`, `Publisher`, `Subscriber`, `Queue`/`Broker`.
- Observer Pattern: The foundational pattern. Subscribers observe Topics.
- Delivery Strategies: `AtMostOnce`, `AtLeastOnce`, `ExactlyOnce`.
- Push vs Pull: Does the broker push messages to subscribers (RabbitMQ style, easy for LLD), or do subscribers poll the broker (Kafka style, better for scale)?
- Concurrency: Thread pools for workers consuming messages, thread-safe queues (`ConcurrentLinkedQueue`), and handling slow consumers without blocking the publisher.

**Key takeaway:** Keep it simple initially: implement an in-memory "Push" based system. If asked for high throughput, introduce a `BlockingQueue` per topic, where a worker thread reads from the queue and pushes to the subscribers asynchronously.

### Design Inventory Management

*File: `06-lld/05-problems/03-domain-specific/24-design-inventory-management.md`*

**What this covers:** Design an Inventory Management System — tests handling of concurrent stock updates and alerting mechanisms when stock runs low.

**Key concepts:**
- Core Entities: `Warehouse`, `Product`, `InventoryItem`, `StockAlert`.
- Concurrency (The core challenge): Two users buying the last item. 
  - Use a `ConcurrentHashMap` for `product_id -> quantity`.
  - Use `synchronized` methods or `AtomicInteger.compareAndSet` for decrementing stock.
- Reservation Strategy: When a user adds to cart, "reserve" the item (decrement available, increment reserved). If checkout fails/times out, revert it.
- Observer Pattern: When stock drops below a threshold, trigger a `LowStockEvent` to notify suppliers.

**Key takeaway:** The interviewer will hammer you on the exact moment the stock count is updated. Differentiate between "Available Quantity" (can be added to cart), "Reserved Quantity" (in carts), and "Purchased Quantity".
---

## LLD: Advanced / Niche

### Design Minesweeper

*File: `06-lld/05-problems/04-advanced-niche/25-design-minesweeper.md`*

**What this covers:** Design Minesweeper — tests your ability to model a game board, handle cell states, and implement recursive flood-fill algorithms (DFS/BFS).

**Key concepts:**
- Core Entities: `Game`, `Board`, `Cell`.
- State Pattern (Cell): A `Cell` can be `HIDDEN`, `REVEALED`, or `FLAGGED`. It also holds its content (`MINE` or `NUMBER_1_TO_8`).
- The Setup Phase: Placing $M$ mines randomly on an $N \times N$ board, then calculating the adjacent mine count for all non-mine cells.
- The Game Loop (Flood Fill): When a user clicks a `HIDDEN` cell:
  - If it's a mine: Game Over.
  - If it's a number: Reveal just that cell.
  - If it's empty (0 adjacent mines): Reveal it, then recursively (or using a queue/BFS) reveal all its 8 neighbors. If any neighbor is also a 0, continue the recursion.

**Key takeaway:** The recursion (Flood Fill) is the core algorithmic challenge. Ensure you check boundary conditions (`x < 0 || y >= N`) and only recurse on `HIDDEN` cells to prevent infinite loops.

### Design S3 Object Storage

*File: `06-lld/05-problems/04-advanced-niche/26-design-s3-object-storage.md`*

**What this covers:** Design S3 Object Storage / File System — an advanced problem combining the Composite pattern for hierarchical data with Strategy for permissions and metadata management.

**Key concepts:**
- Core Entities: `FileSystemEntry` (interface), `File` (leaf), `Directory` (composite), `User`, `Permission`.
- Composite Pattern: A `Directory` contains a list of `FileSystemEntry`s. Both `File` and `Directory` implement methods like `getSize()` and `delete()`.
- Separation of Concerns: The LLD focuses on the *metadata* (names, paths, sizes, permissions), not the actual physical byte storage (which would be handled by a storage engine).
- Permissions (Strategy): Checking if a `User` has `READ` or `WRITE` access requires traversing up the tree. If the user doesn't have explicit access to the file, check the parent directory, and so on.
- Concurrency: Handling concurrent file writes or directory creations requires careful locking, usually `ReadWriteLock` on specific directory nodes.

**Key takeaway:** This is the quintessential Composite Pattern problem. Focus heavily on how `getSize()` works recursively on a `Directory` and how path resolution (`/usr/bin/java`) traverses the tree.

### Design Search Engine

*File: `06-lld/05-problems/04-advanced-niche/27-design-search-engine.md`*

**What this covers:** Design a Search Engine (Inverted Index) — tests text processing, efficient data structures (`Map<String, List<Document>>`), and ranking algorithms.

**Key concepts:**
- Core Entities: `Document`, `SearchEngine`, `InvertedIndex`, `Tokenizer`, `Ranker`.
- The Inverted Index: A `Map<String, List<DocResult>>`. For every word (token), it stores a list of documents that contain the word, along with the term frequency.
- Tokenization (Strategy Pattern): Before indexing, text must be processed: lowercase, remove punctuation, remove stop words ("the", "is"), and stem ("running" -> "run").
- Ranking (Strategy Pattern): When querying, you retrieve the lists for each word. How do you sort them? Usually via TF-IDF (Term Frequency - Inverse Document Frequency) or PageRank.
- Set Intersection: If a user searches "fast car", you must fetch the list for "fast" and the list for "car", and efficiently find the intersection of the two lists.

**Key takeaway:** The heart of this problem is the Inverted Index data structure and the Set Intersection algorithm for multi-word queries.

### Design Tetris

*File: `06-lld/05-problems/04-advanced-niche/28-design-tetris.md`*

**What this covers:** Design Tetris — a very complex game state problem that tests 2D array manipulation, matrix rotation math, and game loops.

**Key concepts:**
- Core Entities: `Game`, `Board` (2D array, e.g., 20x10), `Tetromino` (the falling piece).
- Tetromino (Factory): 7 distinct shapes (I, J, L, O, S, T, Z). Each is represented by a small 2D array or a list of relative coordinates.
- Actions (Command Pattern): Move Left, Move Right, Move Down, Rotate, Drop.
- Collision Detection: Before applying any command, the system must simulate it. If the new coordinates overlap with the board boundaries or existing settled blocks, the move is invalid.
- Rotation Logic: Rotating a 2D matrix 90 degrees involves transposing the matrix and reversing the rows (or applying a standard 2D rotation matrix: `x' = -y, y' = x`).
- Line Clearing: After a piece settles, check all rows. If a row is full, remove it, shift all rows above it down by 1, and increment the score.

**Key takeaway:** Collision detection is the hardest part. Always keep the `Tetromino`'s local coordinates separate from its global position `(x, y)` on the `Board`. Add the local offsets to `(x, y)` to check against the board array.

### Design Version Control

*File: `06-lld/05-problems/04-advanced-niche/29-design-version-control.md`*

**What this covers:** Design a Version Control System (like Git) — a highly advanced LLD problem focusing on Directed Acyclic Graphs (DAGs), hashing, and immutability.

**Key concepts:**
- Core Entities: `Blob` (file content), `Tree` (directory structure), `Commit` (snapshot), `Branch` (pointer to a commit), `Repository`.
- Content-Addressable Storage: Every object is hashed (e.g., SHA-1). The hash is the ID. If a file's content doesn't change between commits, both commits point to the *exact same* Blob hash. This deduplication saves massive amounts of space.
- The DAG: Commits point to their parent commit(s). This forms a Directed Acyclic Graph.
- Branches: A branch is literally just a named pointer (a string -> hash map, e.g., `"main" -> "a1b2c3"`).
- Commands (Command Pattern): `git add` (creates Blobs in staging), `git commit` (creates a Tree and a Commit object), `git checkout` (updates HEAD and working directory).

**Key takeaway:** Do not store full copies of the project for every commit. Explain how Git uses immutable hashing to share unchanged blobs and trees between commits.

### Design Tunneling Service

*File: `06-lld/05-problems/04-advanced-niche/30-design-tunneling-service.md`*

**What this covers:** Design an HTTP Tunneling Service (like ngrok) — tests understanding of networking, proxy servers, and establishing long-lived connections for reverse tunneling.

**Key concepts:**
- Core Entities: `TunnelServer`, `TunnelClient` (runs on localhost), `PublicEndpoint`, `ConnectionManager`.
- The Tunnel: The `TunnelClient` opens a long-lived TCP connection (or WebSocket) *outbound* to the `TunnelServer`. This bypasses the local NAT/Firewall.
- Request Routing: When a public user hits `https://xyz.ngrok.io`, the `TunnelServer` looks up the active tunnel for `xyz`, forwards the HTTP request down the established TCP connection to the `TunnelClient`.
- Replaying/Monitoring (Observer): A nice-to-have feature is a local dashboard that observes all requests passing through the `TunnelClient` to display them to the developer.

**Key takeaway:** The core trick to NAT traversal is that the connection must be initiated from the *inside* (localhost) to the *outside* (public server). The public server then multiplexes incoming web traffic down that established connection.

### Design Text Editor

*File: `06-lld/05-problems/04-advanced-niche/31-design-text-editor.md`*

**What this covers:** Design a Text Editor — a specialized LLD problem focusing on the Command pattern (Undo/Redo) and the Gap Buffer data structure.

**Key concepts:**
- Core Entities: `Editor`, `Document` (Gap Buffer), `CommandManager`, `Command` (Insert/Delete).
- The Gap Buffer: Storing text as a single `String` or `ArrayList` is too slow for insertions ($O(N)$). A Gap Buffer allocates a large empty "gap" at the cursor position. Insertions into the gap are $O(1)$. Moving the cursor shifts the gap.
- Undo/Redo (Command Pattern): Every action (type 'a', hit backspace) is encapsulated in an `ICommand` object with `execute()` and `undo()` methods.
- Two Stacks: Maintain an `UndoStack` and a `RedoStack`. When you type, push to `UndoStack` and clear `RedoStack`. When you hit Ctrl+Z, pop from `UndoStack`, call `undo()`, and push to `RedoStack`.

**Key takeaway:** The Command pattern with two stacks is the standard, expected answer for any Undo/Redo mechanism. Mentioning the Gap Buffer (or a Rope data structure) for the underlying text storage shows deep domain knowledge.

### Design Download Manager

*File: `06-lld/05-problems/04-advanced-niche/32-design-download-manager.md`*

**What this covers:** Design a Download Manager — focuses on network protocols (HTTP Range requests), threading, and merging files.

**Key concepts:**
- Core Entities: `DownloadTask`, `ChunkDownloader`, `FileMerger`, `ConnectionManager`.
- HTTP Range Requests: The secret sauce. You send `Range: bytes=0-1023` in the HTTP header to download just a specific chunk of a file.
- Thread Pool: The `DownloadTask` determines the file size, divides it into $N$ chunks, and submits $N$ `ChunkDownloader` runnables to an `ExecutorService`.
- Merging: As chunks finish, they write to temporary files. Once all complete, the `FileMerger` combines them into the final file.
- Resuming: If paused, the system saves the state of which chunks are complete. On resume, it only requests the incomplete byte ranges.

**Key takeaway:** Explain how to use `java.util.concurrent.ExecutorService` and `CountDownLatch` (to wait for all chunks to finish before merging). The interviewer is testing your multithreading and network knowledge.

### Design Unlock Pattern

*File: `06-lld/05-problems/04-advanced-niche/33-design-unlock-pattern.md`*

**What this covers:** Design Android Unlock Pattern — a graph traversal problem masquerading as LLD. Tests DFS/Backtracking and constraint validation.

**Key concepts:**
- Core Entities: `PatternValidator`, `Grid` (3x3).
- The Rules: You can connect any two dots, *unless* there is a dot directly between them. If there is a dot in between, you can only make the jump if the intermediate dot has *already been visited*.
- The Jump Table: Precompute a 2D array (or Map) `jumps[start][end]` which stores the intermediate node. E.g., `jumps[1][3] = 2`.
- Backtracking (DFS): To find all valid patterns of length $N$, use DFS. Keep a `visited` boolean array. Before visiting `next`, check if `jumps[current][next]` is non-zero. If it is, ensure `visited[jumps[current][next]]` is true.

**Key takeaway:** This is a classic LeetCode algorithm problem (Number of Valid Words for Each Puzzle / Android Unlock Patterns) wrapped in an object-oriented shell. Memorize the "Jump Table" concept to handle the "intermediate dot" rule elegantly.

### Design Lock Free Queue

*File: `06-lld/05-problems/04-advanced-niche/34-design-lock-free-queue.md`*

**What this covers:** Design a Lock-Free Queue — an extremely advanced, low-level concurrency problem (Michael-Scott queue algorithm).

**Key concepts:**
- Core Entities: `Node` (contains `value` and `AtomicReference<Node> next`), `Queue` (contains `AtomicReference<Node> head` and `tail`).
- CAS (Compare-And-Swap): Hardware-level atomic operation. `atomicRef.compareAndSet(expectedValue, newValue)`. It only updates if the current value matches what we *expect* it to be.
- Enqueue: 
  1. Read the `tail` and `tail.next`.
  2. Use CAS to try and set `tail.next` to the new node.
  3. If CAS fails (another thread snuck in), loop and try again (Spinlock).
  4. If CAS succeeds, use CAS to update `tail` to the new node.
- The ABA Problem: A thread reads 'A', another thread changes it to 'B', then back to 'A'. The first thread's CAS succeeds, but the queue state is corrupted. Solved by attaching a version number to the pointer (`AtomicStampedReference` in Java).

**Key takeaway:** You are not expected to invent this algorithm in an interview. You are expected to know *how* CAS works, what the ABA problem is, and how `AtomicStampedReference` solves it.

### Design Concurrent Lru Cache

*File: `06-lld/05-problems/04-advanced-niche/35-design-concurrent-lru-cache.md`*

**What this covers:** Design a Concurrent LRU Cache — takes the standard LRU cache and asks "how do we make this thread-safe without locking the whole structure and killing performance?"

**Key concepts:**
- The Problem: Wrapping the whole LRU (HashMap + DoublyLinkedList) in a `synchronized` block makes it thread-safe, but limits throughput to 1 thread at a time.
- Striped Locking (The Solution): Divide the cache into $N$ separate "segments" (e.g., 16 segments). 
- Hashing to Segments: Use `hash(key) % N` to determine which segment a key belongs to.
- Segment Isolation: Each segment has its own independent `HashMap`, `DoublyLinkedList`, and `ReentrantLock`.
- Concurrency: Thread A accessing Segment 2 and Thread B accessing Segment 5 can proceed entirely in parallel without blocking each other.

**Key takeaway:** This is the exact design of Java's pre-8 `ConcurrentHashMap`. Explain that an LRU requires *both* a map and a linked list to be updated atomically, which is why you can't just use a `ConcurrentHashMap` out of the box (you still need to lock the segment to update the linked list pointers).

### Design High Contention Counter

*File: `06-lld/05-problems/04-advanced-niche/36-design-high-contention-counter.md`*

**What this covers:** Design a High-Contention Counter — testing advanced concurrency concepts (like Java's `LongAdder`) to optimize metrics counting under massive load.

**Key concepts:**
- The Problem: `AtomicLong` uses a single CAS loop. If 1000 threads try to increment it simultaneously, 999 fail, spin, and retry, causing massive CPU contention and cache-line invalidation (false sharing).
- Striped Counters: Instead of one variable, use an array of variables (cells). 
- The Algorithm: 
  - When a thread wants to increment, it hashes its own Thread ID to pick a specific cell in the array and increments that cell using CAS.
  - Since threads map to different cells, contention is drastically reduced.
- Getting the Total: When you need the actual count, iterate through the array and sum all the cells. (This is slightly slower, but usually reads are rare compared to writes for metrics).

**Key takeaway:** This is a very specific systems question. Knowing the difference between `AtomicLong` (good for low contention) and `LongAdder` (Striped cells, good for high contention) is the key to passing this.


---

# Foundations, Building Blocks & Scaling

## Foundations

### Fundamentals

*File: `01-foundations/01-system-design-basics/01-fundamentals.md`*

**What this covers:** The absolute basics of system design. Think of this as the "mental model" you need before building any massive application.

**Key topics:**
- **Scalability:** When your app gets popular, how do you handle it? You can buy a bigger server (Vertical Scaling) or buy many small servers (Horizontal Scaling).
- **Availability:** How often is your app online? We measure this in "nines" (99.9% uptime means your app is down for about 8.7 hours a year).
- **Consistency:** When one user changes their profile picture, does everyone in the world instantly see the new picture, or is it okay if some people see the old one for a few seconds?
- **Performance:** Measured in Latency (how fast one person gets a response) and Throughput (how many total people can get responses at the same time).
- **The 8 Building Blocks:** Load Balancers, Caches, Databases, Message Queues, CDNs, Reverse Proxies, API Gateways, and Service Discovery.

**Key takeaway:** Every big app is just a combination of these 8 building blocks, balanced against the trade-offs of Speed, Availability, and Consistency.
### Storage Fundamentals

*File: `01-foundations/02-hardware-and-networking/01-storage-fundamentals.md`*

**What this covers:** How computers store data, from lightning-fast memory to massive, slow hard drives, and how to choose the right one for your application.

**Key topics:**
- **Storage hierarchy:** Think of it like a kitchen. L1/L2 cache is the cutting board (tiny but instant), RAM is the counter (bigger but temporary), SSD is the fridge (large and permanent), and Network Storage is the grocery store (unlimited but a drive away).
- **Storage types:** Block (raw drives), File (shared folders), Object (flat, huge buckets like Amazon S3).
- **Key metrics:** IOPS (how many small tasks you can do per second), throughput (how fast you can move massive files), and latency (how long it takes to start).
- **Trade-offs:** Fast storage is expensive and often temporary. Permanent storage is cheaper but much slower.
- **Failure modes:** When drives slow down drastically, or when they physically break.
- **When to use each tier:** RAM for active users, SSDs for fast databases, HDDs for massive archives, S3 for images/videos.

**Key takeaway:** Every time you design a system, you are making a choice about storage. Knowing the speed and cost of each type separates the guessing engineers from the great ones.
### Networking

*File: `01-foundations/02-hardware-and-networking/02-networking.md`*

**What this covers:** Networking fundamentals for system design. How computers actually talk to each other across the globe.

**Key topics:**
- **The OSI Model:** The 7 steps data takes to get from your app to the physical wire.
- **TCP vs UDP:** TCP is a certified mail delivery (reliable but slow). UDP is throwing a newspaper at a porch (unreliable but fast).
- **HTTP Evolution:** How we went from HTTP/1 (a single-lane road) to HTTP/3 (a multi-lane highway with flying cars).
- **REST vs GraphQL vs gRPC:** Different ways APIs talk. REST is a fixed restaurant menu, GraphQL is a custom order, gRPC is a walkie-talkie.
- **Real-Time Patterns:** WebSockets (phone call), SSE (radio broadcast), and Long Polling (waiting on hold).
- **DNS:** The internet's phonebook, and why changes take 48 hours to update.

**Key takeaway:** Choosing the right network protocol can be the difference between a fast, snappy app and a broken, lagging mess.
### Databases

*File: `01-foundations/03-database-foundations/01-databases.md`*

**What this covers:** Everything you need to know about Databases for System Design.

**Key topics:**
- **SQL vs NoSQL:** SQL is a strict Excel spreadsheet (perfect for banking). NoSQL is a flexible folder of documents (perfect for social media).
- **ACID vs BASE:** ACID ensures perfection (if a bank transfer fails halfway, it rolls back entirely). BASE accepts chaos for speed (it's okay if your Instagram like-count is a few seconds behind).
- **The CAP Theorem:** During a network outage, you must choose between staying online (Availability) or freezing the system to protect data accuracy (Consistency).
- **Indexes:** The database's table of contents. It makes reading 100x faster, but writing 2x slower.
- **Sharding & Replication:** Replication is copying your database so if one dies, another takes over. Sharding is cutting a massive database in half because it's too big to fit on one computer.

**Key takeaway:** Choosing the wrong database is the most expensive mistake you can make. Always choose based on what your app needs to do most: read fast, write fast, or never lose money.
### Security

*File: `01-foundations/04-security/01-security.md`*

**What this covers:** Security fundamentals for distributed systems — the mechanisms that go beyond passwords: mTLS, encryption at rest/in transit, secrets management, and defense-in-depth patterns.

**Key topics:**
- **Authentication vs Authorization** — AuthN (who) vs AuthZ (what)
- **HTTPS/TLS** — how the handshake works, certificate chains, pinning
- **mTLS** — mutual authentication between services, zero-trust networking
- **Encryption at rest** — AES-256, envelope encryption, KMS
- **Secrets management** — why `.env` files are wrong at scale, Vault/KMS patterns
- **RBAC vs ABAC** — role-based vs attribute-based access control
- **OWASP Top 10** — the attack surfaces you must design against

**Key takeaway:** The goal is defense in depth — assume any perimeter will be breached. Encrypt everything, authenticate every service-to-service call, and never store a secret in a repo or config file.
### OAuth & JWT

*File: `01-foundations/04-security/02-oauth-jwt.md`*

**What this covers:** How modern applications prove who you are (authentication) and what you're allowed to do (authorization) — specifically OAuth 2.0 and JSON Web Tokens (JWT).

**Key topics:**
- Authentication vs Authorization — the fundamental distinction
- Session-based auth: the old hotel keycard approach
- JWT: self-contained tickets and how to read them
- OAuth 2.0: why "Login with Google" works, and the 4 grant flows
- Token refresh, revocation, and the hardest security tradeoff

**Key takeaway:** For stateless, distributed microservices → use JWTs. For third-party login delegation → use OAuth 2.0 Authorization Code Flow. Know exactly how to revoke a JWT (it's the hardest part).
### Consistency & Conflicts

*File: `01-foundations/05-advanced-distributed-theory/01-consistency-and-conflicts.md`*

**What this covers:** The full consistency ladder — from the weakest guarantees all the way up to linearizability — and how to reason about conflict resolution in distributed systems.

**Key topics:**
- **The consistency ladder:** eventual → monotonic reads → read-your-writes → causal → sequential → linearizable
- **Linearizability vs Serializability** — the distinction every Staff candidate must know
- **Causal consistency** — vector clocks, happens-before, practical use in social apps
- **CRDTs** — conflict-free merge without coordination
- **Conflict resolution strategies** — LWW, vector clocks, application-level merge
- **Split-brain and quorum** — how majority rules prevents silent data loss

**Key takeaway:** "Strong consistency" is not a precise term. Interviewers expect you to name the exact model (linearizable, serializable, causal) and justify the trade-off for your specific use case.
### Consensus Algorithms

*File: `01-foundations/05-advanced-distributed-theory/02-consensus-algorithms.md`*

**What this covers:** How multiple computers vote to agree on a single truth, even if half of them are broken or offline.

**Key topics:**
- **The Problem:** If you have 3 database servers, and they all receive different data at the same time, how do they agree on which data is "correct"?
- **Consensus:** The mathematical process of voting to find a single truth.
- **Raft & Paxos:** The two most famous "voting" algorithms. (Raft is easier to understand, Paxos is the older, harder one).
- **Leader Election:** The easiest way to avoid arguments is to elect a boss (The Leader). Everyone else just copies the boss.
- **Heartbeats:** How the followers know if the boss is dead, so they can trigger a new election.

**Key takeaway:** Distributed databases (like CockroachDB or etcd) use Raft to ensure they never lose your data, even if entire data centers lose power.
### Change Data Capture

*File: `01-foundations/05-advanced-distributed-theory/03-change-data-capture.md`*

**What this covers:** How to safely move data from your main database into a cache or a search engine without losing anything.

**Key topics:**
- **The Problem:** Dual Writes. If your app tries to save data to the Database AND the Cache at the same time, one might fail, leaving them permanently out of sync.
- **The Solution:** Change Data Capture (CDC). Only the Database is allowed to accept writes. Other systems "eavesdrop" on the database to update themselves.
- **The Write-Ahead Log (WAL):** The database's secret diary. Every database writes down what it is going to do in a diary *before* it actually does it.
- **Debezium:** The most popular tool that reads the secret diary and shouts the changes into a Message Queue (like Kafka).

**Key takeaway:** Never let your application code write to a database and a cache at the exact same time. Always use CDC to let the database update the cache automatically in the background.

## Building Blocks

### Load Balancers

*File: `02-building-blocks/01-networking/01-load-balancers.md`*

**What this covers:** Load balancers in depth — from Layer 4/7 basics through consistent hashing, global server load balancing (GSLB), and high-availability LB setups.

**Key topics:**
- **L4 vs L7** — what each can and can't route on
- **Routing algorithms** — round robin, least connections, IP hash, consistent hashing
- **Consistent hashing** — how to minimize cache invalidation when servers are added/removed
- **Health checks** — active vs passive, how failover works
- **Session affinity** — when you need it and why Redis is better
- **GSLB** — routing users to the geographically closest data center
- **LB high availability** — active-passive via VRRP/floating IP, active-active

**Key takeaway:** For most systems: L7 LB (Nginx/ALB/Envoy) in front, consistent hashing when you need sticky-without-coupling, GSLB at the edge. The LB itself is never a SPOF in a real production deployment.
### Reverse Proxy

*File: `02-building-blocks/01-networking/02-reverse-proxy.md`*

**What this covers:** How to hide your servers from the internet so hackers can't attack them directly.

**Key topics:**
- **Forward Proxy vs Reverse Proxy:** A Forward Proxy hides the *user* from the internet (like a VPN). A Reverse Proxy hides the *server* from the internet.
- **Security:** Your actual servers sit behind a locked wall. The internet only talks to the Reverse Proxy.
- **SSL Termination:** The Reverse Proxy handles the heavy lifting of decrypting secure traffic so your servers don't have to.
- **Caching:** If 1,000 people ask for the same image, the Reverse Proxy saves a copy and hands it out, so your main server doesn't have to do the work 1,000 times.

**Key takeaway:** Never put your backend application server directly on the public internet. Always put a Reverse Proxy (like NGINX or HAProxy) in front of it.
### API Gateway

*File: `02-building-blocks/01-networking/03-api-gateway.md`*

**What this covers:** API gateways in production — what they actually do beyond "routing," how they relate to service meshes, and patterns like BFF and API composition that come up in design interviews.

**Key topics:**
- **Core functions** — routing, AuthN, rate limiting, SSL termination
- **API composition** — aggregating multiple backend calls into one client response
- **BFF (Backend for Frontend)** — why mobile and web get different gateway shapes
- **Gateway vs service mesh** — what lives at the edge vs what lives east-west
- **Service discovery** — how gateways know where to route
- **Request/response transformation** — header injection, payload reshaping

**Key takeaway:** The API gateway is the north-south traffic boundary (external → internal). The service mesh is the east-west boundary (service → service). They're complementary, not competing.
### Service Discovery

*File: `02-building-blocks/01-networking/04-service-discovery.md`*

**What this covers:** How microservices find each other in a world where IP addresses change every 5 minutes.

**Key topics:**
- **The Problem:** In the cloud, servers are constantly dying and being replaced. If the Payment Service is at IP `192.168.1.5` today, it might be at `10.0.0.9` tomorrow. How does the Cart Service know where to find it?
- **Service Registry:** The "Yellow Pages" of your cloud. Every time a new server spins up, it calls the Registry and says, "Hi, I'm a Payment Server, here is my IP!"
- **Client-Side Discovery:** The Cart Service asks the Yellow Pages for the address, and then calls the Payment Service directly.
- **Server-Side Discovery:** The Cart Service asks a Load Balancer, and the Load Balancer checks the Yellow Pages and forwards the call.

**Key takeaway:** Hardcoding IP addresses in your code is a guaranteed way to break your system. Service Discovery automates the process of tracking which servers are alive and where they live.
### CDN

*File: `02-building-blocks/01-networking/05-cdn.md`*

**What this covers:** How to make Netflix load instantly for a user in Australia, even though the main server is in California.

**Key topics:**
- **The Problem:** The speed of light. Data takes 150 milliseconds to cross the ocean. If a webpage requires 100 images, it will take 15 seconds to load.
- **The Solution:** A Content Delivery Network (CDN). A global network of "mini-servers" placed in every major city in the world.
- **PoPs (Points of Presence):** The physical locations of these mini-servers.
- **Push vs Pull CDNs:** Does your main server push the video to the CDN, or does the CDN pull the video when a user asks for it?
- **TTL (Time to Live):** How long the CDN is allowed to keep the video before it has to ask the main server for a fresh copy.

**Key takeaway:** Never serve static files (images, videos, HTML, Javascript) from your main application server. Always put them in an S3 bucket behind a CDN.
### WebSockets & SSE

*File: `02-building-blocks/01-networking/06-websockets-sse.md`*

**What this covers:** How servers push data to browsers in real time — the three competing approaches: Long Polling, Server-Sent Events (SSE), and WebSockets.

**Key topics:**
- Why HTTP's request-response model breaks down for real-time apps
- Long Polling: the "spinning wheel" trick
- Server-Sent Events (SSE): a one-way news ticker
- WebSockets: a permanent two-way phone call
- Exact tradeoffs: latency, scalability, infra complexity, and when to pick each

**Key takeaway:** Pick WebSockets for bidirectional real-time (chat, gaming). Pick SSE for server-to-client streams (notifications, live scores). Use Long Polling only as a last resort when WebSockets are blocked.
### Caching Layer

*File: `02-building-blocks/02-performance/01-caching-layer.md`*

**What this covers:** How to make your database 100x faster by saving the answers to common questions in memory.

**Key topics:**
- **The Problem:** Databases save data on hard drives, which are physically slow to read from.
- **The Solution (Caching):** Saving data in RAM (Memory), which is lightning fast. Tools like Redis or Memcached do this.
- **Cache Aside (The Lazy Way):** The app checks the cache. If it's empty, it asks the database, and then saves the answer in the cache for next time.
- **Write-Through (The Safe Way):** When saving data, the app writes to the cache *and* the database at the exact same time.
- **Eviction Policies (LRU):** RAM is expensive. When the cache gets full, you have to throw something away. LRU (Least Recently Used) throws away the oldest, least popular data.

**Key takeaway:** Caching is the ultimate cheat code for system design. If your app is slow, putting Redis in front of the database is almost always the first step to fixing it.
### Rate Limiting

*File: `02-building-blocks/02-performance/02-rate-limiting.md`*

**What this covers:** How to stop bad actors from spamming your website and crashing your servers.

**Key topics:**
- **The Problem:** A hacker writes a script to guess a password 10,000 times a second. Your database melts.
- **The Solution (Rate Limiting):** A bouncer at the front door who counts how many times you visit. If you visit too fast, you are blocked (`HTTP 429 Too Many Requests`).
- **Token Bucket Algorithm:** Giving each user a bucket of coins. Every click costs a coin. The bucket slowly refills over time.
- **Leaky Bucket Algorithm:** Pouring water (requests) into a funnel. The funnel drips out at a steady rate. If you pour too fast, the water spills over the top (blocked).
- **Fixed Window vs Sliding Window:** Different math tricks for counting how many requests a user made in the last 60 seconds.

**Key takeaway:** Every public API must have a Rate Limiter. Without it, a single malicious user (or a poorly written script) can take down your entire company.
### Circuit Breaker

*File: `02-building-blocks/02-performance/03-circuit-breaker.md`*

**What this covers:** How to stop a tiny failure in one service from crashing your entire company.

**Key topics:**
- **The Problem:** The "Cascading Failure." If the Email Service gets slow, the Payment Service waits for it. Then the Cart Service waits for the Payment Service. Soon, the entire system is frozen.
- **The Solution (Circuit Breaker):** Just like the electrical box in your house. If a wire draws too much power, the breaker "trips" and shuts off the electricity to save the house from burning down.
- **Closed State:** Everything is normal. Traffic flows.
- **Open State:** The service is broken. The Circuit Breaker trips and instantly blocks all traffic to that service so it has time to recover.
- **Half-Open State:** The Circuit Breaker slowly lets 1 or 2 requests through to see if the service is fixed yet.

**Key takeaway:** In microservices, services *will* fail. A Circuit Breaker accepts the failure gracefully instead of letting it destroy the rest of the architecture.
### Bloom Filter

*File: `02-building-blocks/02-performance/04-bloom-filter.md`*

**What this covers:** A magical data structure that can search through a billion items using almost zero RAM.

**Key topics:**
- **The Problem:** Storing 1 billion usernames in RAM (so you can check if a username is taken) takes hundreds of gigabytes of memory.
- **The Bloom Filter:** A clever math trick that compresses those 1 billion usernames into just a few Megabytes!
- **The Catch:** It is slightly inaccurate. It will never give you a False Negative ("Definitely Not"), but it might give you a False Positive ("Probably Yes").
- **Use Cases:** Checking if a URL is malicious, preventing users from seeing the same recommendation twice, or skipping expensive database queries.

**Key takeaway:** If you are asked to quickly check if an item exists in a massive dataset (millions/billions of items) during an interview, the answer is almost always a Bloom Filter.
### Sharding

*File: `02-building-blocks/03-data-partitioning/01-sharding.md`*

**What this covers:** How to split a massive database into smaller pieces when it gets too big for one computer.

**Key topics:**
- **The Problem:** Your database has 10 Terabytes of data. The biggest hard drive you can buy is 8 Terabytes. What do you do?
- **The Solution (Sharding):** Cutting the database in half. Put 5 Terabytes on Server A, and 5 Terabytes on Server B.
- **The Shard Key:** How do you decide who goes where? If you shard by Last Name, all the A-M people go to Server A. If you shard by User ID, all even numbers go to Server A.
- **The Hotspot Problem (The Celebrity Problem):** If Justin Bieber joins your app, and he is on Server A, his millions of followers will overwhelm Server A while Server B sits completely empty.
- **The Catch:** Sharding ruins your ability to do complex searches (JOINs). You should avoid sharding until it is your absolute last resort.

**Key takeaway:** Sharding is horizontally scaling your data. It solves storage limits, but it makes your application code 10x more complicated.
### Replication

*File: `02-building-blocks/03-data-partitioning/02-replication.md`*

**What this covers:** How to copy your database so if a server explodes, you don't lose all your users' data.

**Key topics:**
- **The Problem:** Hard drives fail. If your Database only exists on one hard drive, you are one lightning strike away from your company going bankrupt.
- **The Solution (Replication):** Making perfect copies of your database on different servers.
- **Primary-Replica (Leader-Follower):** One boss takes all the new data (Writes), and copies it to 3 assistants (Reads). Most common setup!
- **Multi-Primary (Multi-Leader):** Multiple bosses taking new data at the exact same time. Faster, but causes conflicts.
- **Synchronous vs Asynchronous:** Do you force the user to wait until the copy is 100% finished? Or do you say "Done!" immediately and copy it in the background?

**Key takeaway:** Sharding is for when you run out of *space*. Replication is for when you want to handle more *reads*, and protect against hardware failures. You almost always use both!
### Consistent Hashing

*File: `02-building-blocks/03-data-partitioning/03-consistent-hashing.md`*

**What this covers:** How to add or remove servers from your database cluster without having to move millions of users around.

**Key topics:**
- **The Problem:** Standard hashing uses `User_ID % Number_of_Servers`. If you have 4 servers, and you add a 5th server, the math changes for *every single user*. You have to move 99% of your data to new servers, which crashes the system.
- **Consistent Hashing (The Solution):** Instead of standard math, we put the servers on a giant circle (like a clock).
- **How it works:** To find where a user belongs, you drop them on the clock, and they walk clockwise until they hit a server.
- **Adding a server:** If you add a new server to the clock, only the users immediately behind it have to move. 90% of the data stays exactly where it is!
- **Virtual Nodes:** To prevent one server from getting stuck with a huge slice of the clock, we create "fake" servers (Virtual Nodes) to spread the load perfectly evenly.

**Key takeaway:** If you are designing a distributed system (like DynamoDB or Cassandra) where you might need to add or remove servers in the future, you *must* use Consistent Hashing.
### Message Brokers

*File: `02-building-blocks/04-coordination/01-message-brokers.md`*

**What this covers:** How message brokers decouple services, the delivery guarantee models that separate a junior answer from a senior one, and how to reason about ordering, backpressure, and failure modes.

**Key topics:**
- **Async decoupling** — producers and consumers scale independently
- **Point-to-Point vs Pub/Sub** — task queues vs event fans
- **Delivery guarantees** — at-most-once, at-least-once, exactly-once
- **Idempotent consumers** — how to handle duplicates safely
- **Ordering** — when it's guaranteed and when it's not
- **Backpressure** — what happens when consumers can't keep up
- **Dead Letter Queue** — handling poison pill messages
- **Kafka vs RabbitMQ vs SQS** — when to use what

**Key takeaway:** Every message broker system you design must answer three questions: what delivery guarantee do you need, how do you handle duplicates, and how do you handle consumer lag? These are the senior-signal follow-ups.
### Distributed Locks

*File: `02-building-blocks/04-coordination/02-distributed-locks.md`*

**What this covers:** How to stop two servers from accidentally deleting each other's work.

**Key topics:**
- **The Problem:** A race condition. If two servers try to buy the exact same airplane ticket at the exact same millisecond, the database might accidentally sell the ticket twice.
- **The Solution (Distributed Lock):** A digital "Bathroom Key." Only the person holding the key is allowed to buy the ticket.
- **The Danger (Deadlocks):** What happens if a server grabs the key, and then instantly dies? The key is lost forever, and nobody can ever buy that ticket again.
- **The Fix (TTL / Leases):** Adding a timer to the key. If the server doesn't return the key in 10 seconds, the key magically teleports back to the front desk.
- **Tools:** Redis (Redlock) or Apache ZooKeeper are the industry standards for managing these keys.

**Key takeaway:** Whenever you have multiple servers touching the exact same data, you must use a Distributed Lock to prevent chaos.
### Architecture Composition

*File: `02-building-blocks/05-composition/01-architecture-composition.md`*

**What this covers:** How to put all the building blocks together to create a final, working architecture.

**Key topics:**
- **The Goal:** You now understand Load Balancers, CDNs, API Gateways, Caches, and Message Brokers. How do they actually connect?
- **The 3-Tier Architecture:** The classic way to build web apps (Presentation, Logic, Data).
- **The Path of a Request:** Following a user's click from their mobile phone, through the CDN, into the Gateway, down to the Database, and back.
- **Putting it together:** A high-level view of how a modern tech company wires these components to handle millions of users without crashing.

**Key takeaway:** In an interview, nobody expects you to build a perfect system. They expect you to draw a logical flow of boxes where every box protects the next box from failing.

## Scaling

### Scaling Fundamentals

*File: `03-scaling/01-scaling-fundamentals.md`*

**What this covers:** How to grow your application from 10 users to 10 million users without it crashing.

**Key topics:**
- **The Problem:** A single computer can only do so much work. When you get too popular, your server crashes.
- **Vertical Scaling (Scaling Up):** Buying a bigger, faster, more expensive computer. (Like replacing a bicycle with a Ferrari). It's easy, but has a hard limit.
- **Horizontal Scaling (Scaling Out):** Buying 100 cheap computers and making them work together. (Like having 100 bicycles). It's infinitely scalable, but much harder to manage.
- **Stateful vs Stateless:** To scale horizontally, your servers must be "Stateless" (they cannot remember who you are).

**Key takeaway:** Never try to scale a Stateful server. Always separate your App Servers (Stateless) from your Database (Stateful), and scale them independently.
### Database Internals

*File: `03-scaling/02-database-internals.md`*

**What this covers:** How databases actually save data onto a physical hard drive, and why different databases are good at different things.

**Key topics:**
- **The Problem:** Saving data to a hard drive is extremely slow. If you just dump data on a drive randomly, it will take hours to find it later.
- **B-Trees (The Phonebook):** Used by PostgreSQL and MySQL. Data is sorted neatly as it arrives.
  - *Pros:* Reading data is lightning fast because everything is alphabetical.
  - *Cons:* Writing data is slow, because you have to carefully erase and rewrite sections to keep the alphabetical order perfect.
- **LSM Trees (The Logbook):** Used by Cassandra and DynamoDB. Data is just quickly scribbled at the very bottom of a list as fast as possible.
  - *Pros:* Writing data is insanely fast.
  - *Cons:* Reading data is slower, because the database has to search through a messy pile of notes.

**Key takeaway:** If your app does 90% Reads (like Twitter or Wikipedia), use a B-Tree database. If your app does 90% Writes (like tracking Uber GPS locations), use an LSM Tree database.
### Database Scaling

*File: `03-scaling/03-database-scaling.md`*

**What this covers:** The full database scaling staircase — from indexing to sharding — plus the production-grade details on shard-key selection, replication lag, and read-after-write consistency that distinguish a senior answer.

**Key topics:**
- **The staircase:** Index → Cache → Read Replicas → Sharding (reach for each in order)
- **Replication lag** — the gap between primary and replica, and when it bites you
- **Read-after-write consistency** — how to prevent "I just saved my post but it disappeared"
- **Sharding strategies** — range vs hash vs directory-based, and how to choose
- **Shard-key selection** — the most consequential architectural decision in a sharded system
- **Hot partitions** — when one shard gets all the traffic
- **Resharding** — how to split shards without downtime

**Key takeaway:** Reach for sharding last. But when you do shard, the shard-key decision is permanent and painful to change — get it right the first time.
### Global Distribution

*File: `03-scaling/04-global-distribution.md`*

**What this covers:** How to run your application in multiple countries at the exact same time.

**Key topics:**
- **The Problem:** If all your servers are in New York, users in Australia will experience massive lag (Latency). And if a hurricane hits New York, your global business is entirely offline.
- **The Solution:** Copy-pasting your entire architecture into multiple Data Centers around the world.
- **Active-Passive (The Backup Generator):** New York does 100% of the work. London just sits there, empty, waiting. If New York explodes, you flip a switch and London takes over. (Cheap, safe, but Australians still have lag).
- **Active-Active (The Two Kitchens):** New York and London are both working at the same time. Australians go to London, Americans go to New York. (Extremely fast, but incredibly hard to keep their databases synced).
- **Data Sovereignty:** Legal rules. Europe says European user data is legally not allowed to leave Europe. You *must* have a European data center.

**Key takeaway:** Global distribution solves both Latency (speed) and Disaster Recovery (survival), but it requires Geo-Routing (a smart DNS) to send users to the right place.

---

# Advanced Topics & Patterns

## Advanced Topics

### Distributed Systems

*File: `04-advanced-topics/01-distributed-architecture/01-distributed-systems.md`*

**What this covers:** The fundamental philosophy of why building apps on 100 computers is so much harder than building apps on 1 computer.

**Key topics:**
- **The Definition:** A distributed system is a bunch of separate computers acting like one giant computer.
- **The Orchestra Analogy:** 100 musicians playing together. If they are perfectly synced, it sounds like one giant instrument. If the conductor is bad, it sounds like chaos.
- **The 8 Fallacies of Distributed Computing:** The lies that junior developers believe when they first start building distributed systems. (e.g., "The network is reliable", "Latency is zero").
- **CAP Theorem:** The fundamental rule that proves you cannot have a perfect database. You must always sacrifice something.

**Key takeaway:** In a distributed system, everything that can go wrong *will* go wrong. The network will drop packets, servers will randomly reboot, and clocks will drift out of sync. You must write code that expects failure at every step.
### Distributed Concepts

*File: `04-advanced-topics/01-distributed-architecture/02-distributed-concepts.md`*

**What this covers:** How multiple computers make a decision when the network breaks and they can't talk to each other.

**Key topics:**
- **The Problem:** You have 3 database servers. Server A thinks your password is "Dog". Server B thinks your password is "Cat". Who is right?
- **Quorum (Majority Rules):** The computers hold a vote. To make any decision, a strict majority (more than 50%) of the servers must agree.
- **Split Brain:** What happens if the network cable between New York and London is cut? Both cities think the other city died, and both cities try to become the "Boss". They start writing conflicting data!
- **Clock Drift:** Time is an illusion in distributed systems. You cannot trust a computer's internal clock to figure out which event happened first.

**Key takeaway:** Distributed systems require complex voting mechanisms to ensure data isn't corrupted during a network failure. You must design systems that can survive when half the computers suddenly stop responding.
### Microservices

*File: `04-advanced-topics/01-distributed-architecture/03-microservices.md`*

**What this covers:** Microservices architecture in depth — the real trade-offs, the service mesh pattern, inter-service communication, and how to handle the hard problems (distributed transactions, tracing, deployment).

**Key topics:**
- **Monolith vs Microservices** — when to split, when to stay
- **Inter-service communication** — synchronous (REST/gRPC) vs asynchronous (events)
- **Service mesh** — what Istio/Envoy actually do and why you need them at scale
- **Circuit breaker** — preventing cascading failures
- **Distributed transactions** — Saga pattern (choreography vs orchestration)
- **API gateway** — edge concerns vs service mesh concerns
- **Service discovery** — how services find each other in a dynamic fleet

**Key takeaway:** Microservices solve an organizational scaling problem. They create distributed systems problems in return. The service mesh and Saga pattern are how mature teams manage that complexity.
### Event-Driven Architecture

*File: `04-advanced-topics/01-distributed-architecture/04-event-driven-architecture.md`*

**What this covers:** How to build a system where microservices react to things happening in real-time, instead of constantly asking "Did anything happen yet?"

**Key topics:**
- **The Problem:** In standard microservices, Service A has to call Service B. If Service B is offline, Service A gets an error. They are still tightly coupled!
- **The Solution (Event-Driven):** Service A just shouts into a megaphone: "Something happened!" It doesn't care who is listening. Service B hears it and does its job.
- **Commands vs Events:** A Command is "Do this right now" (Expects an answer). An Event is "This just happened in the past" (Doesn't care about the answer).
- **Choreography vs Orchestration:** Do the services just listen and dance on their own (Choreography)? Or is there one central Boss telling everyone exactly what to do step-by-step (Orchestration)?
- **Event Sourcing:** Instead of saving your current bank balance ($100), the database saves every single transaction you ever made (+$50, -$10, +$60).

**Key takeaway:** Event-Driven Architecture uses tools like Kafka to make microservices truly independent. It is insanely scalable, but very hard to debug when things go wrong.
### Stream Processing

*File: `04-advanced-topics/01-distributed-architecture/05-stream-processing.md`*

**What this covers:** How companies process millions of data points per second in real-time.

**Key topics:**
- **Batch Processing:** Waiting until the end of the day, gathering all the data into a giant pile, and processing it all at once. (Like waiting to do laundry until Sunday).
- **Stream Processing:** Processing data the exact millisecond it arrives, one piece at a time. (Like washing every shirt the exact second you take it off).
- **The Use Case:** Uber matching you with a driver, or a bank blocking a stolen credit card, *must* happen in real-time (Stream Processing). Generating a monthly sales report can happen at midnight (Batch Processing).
- **Time Windows:** How do you count "Trending Tweets in the last 5 minutes" if the data never stops flowing? You chop the infinite stream into 5-minute chunks (Windows).

**Key takeaway:** Stream Processing (using tools like Apache Flink or Spark Streaming) is what allows modern apps to feel "live" and reactive, instead of making you wait until tomorrow for the database to update.
### Kubernetes and Containers

*File: `04-advanced-topics/01-distributed-architecture/06-kubernetes-containers.md`*

**What this covers:** How modern teams deploy, scale, and manage distributed systems — from containers (Docker) to orchestration (Kubernetes).

**Key topics:**
- Why VMs were replaced by containers
- Docker: what a container actually is
- Kubernetes architecture: the control plane vs worker nodes
- Core K8s concepts: Pods, Deployments, Services, Ingress
- Horizontal Pod Autoscaling (HPA) — how systems auto-scale under load
- Production failure scenarios and how K8s handles them

**Key takeaway:** In a system design interview, when asked "how do you deploy and scale this?", your answer should mention containerization, horizontal scaling via Kubernetes HPA, and health checks/rolling deployments.
### Transactional Outbox and Change Data Capture (CDC)

*File: `04-advanced-topics/01-distributed-architecture/07-outbox-cdc-pattern.md`*

**What this covers:** Two complementary patterns — the Transactional Outbox and Change Data Capture (via Debezium) — that solve the "dual-write problem": reliably publishing an event to a broker at the same time as writing to a database, without distributed transactions.

**Key concepts:**
- **Dual-write problem:** writing to the DB and publishing to Kafka as two separate operations can leave the system inconsistent if one succeeds and the other fails or the process crashes in between.
- **Transactional Outbox:** write the event to an `outbox` table in the same DB transaction as the business write; a separate relay process polls the outbox and publishes to Kafka, giving at-least-once delivery.
- **CDC with Debezium:** reads the database's replication log (WAL/binlog) directly and converts row changes into Kafka events — no application code changes needed, sub-second latency, and captures even non-application (DBA) writes.
- **Outbox + CDC combined:** application writes atomically to business table + outbox table; Debezium reads the outbox from the WAL and publishes, giving atomicity, low latency, and no polling load — the best-of-both-worlds pattern for high-volume systems.
- **Production pitfalls:** replication slot lag can cause unbounded WAL growth if Debezium falls behind; schema evolution requires Schema Registry compatibility checks; deletes emit tombstone events; ordering requires partitioning by aggregate_id.

**Key takeaway:** The transactional outbox (optionally paired with CDC/Debezium) is the standard way to guarantee atomicity between a database write and an event publish, at the cost of at-least-once delivery — so downstream consumers must be idempotent.
### gRPC vs REST vs GraphQL

*File: `04-advanced-topics/01-distributed-architecture/08-grpc-rest-graphql.md`*

**What this covers:** A comparison of the three dominant API paradigms — REST, gRPC, and GraphQL — their transport/serialization models, strengths, pitfalls, and when to choose each (including common hybrid architectures).

**Key concepts:**
- **REST:** stateless, resource-oriented, JSON over HTTP; simple and cacheable, but prone to over-fetching, under-fetching (N+1 round trips), and versioning drift (`/v1/`, `/v2/`).
- **gRPC:** Protobuf over HTTP/2, schema-first (`.proto` files), compile-time type safety, native bidirectional streaming, and built-in deadline propagation; fast and compact but not browser-native and payloads aren't human-readable.
- **GraphQL:** client specifies exactly the fields it needs in a single query against one endpoint, eliminating over/under-fetching and versioning; introduces its own N+1 problem at the resolver level (fixed with DataLoader batching) and needs query-depth limits to prevent abuse.
- **Common hybrid architectures:** gRPC internally between microservices with REST/GraphQL at the edge (BFF or API gateway) is the most common production pattern at scale.

**Key takeaway:** Use REST for public/cacheable APIs, gRPC for high-performance internal service-to-service calls (especially streaming), and GraphQL when multiple clients need flexible, differently-shaped views of the same data.
### Observability

*File: `04-advanced-topics/02-system-reliability/01-observability.md`*

**What this covers:** How to build a system you can actually debug at 3 AM — the three observability pillars in depth, plus SLO/SLI/error budgets and the RED/USE methodologies that staff engineers apply to on-call incidents.

**Key topics:**
- **Three pillars:** metrics, logs, distributed traces — what each tells you and when to use each
- **RED method** — Rate, Errors, Duration — for service-level health
- **USE method** — Utilization, Saturation, Errors — for resource-level health
- **SLI / SLO / SLA / Error Budget** — the contract that governs when to ship vs when to fix
- **Distributed tracing** — how Trace IDs flow across services, sampling strategies
- **Structured logging** — why `grep` on plain text doesn't scale
- **Alerting** — what makes a good alert vs noise

**Key takeaway:** Observability is not a tool you bolt on after launch. It's a design constraint. Every RPC should emit a span. Every background job should emit a metric. Every error should emit a structured log with a correlation ID.
### Chaos Engineering

*File: `04-advanced-topics/02-system-reliability/02-chaos-engineering.md`*

**What this covers:** Why Netflix pays programmers to intentionally break their own website.

**Key topics:**
- **The Problem:** You write a backup plan for when a server catches on fire. But you never actually test the backup plan, because you are terrified of breaking the website. When a real fire happens, the backup plan fails.
- **The Solution (Chaos Engineering):** Intentionally setting your servers on fire during the middle of the day, while all your engineers are awake and drinking coffee, to prove that your backup plans actually work.
- **Chaos Monkey:** A famous program invented by Netflix. It randomly unplugs servers in production.
- **The Blast Radius:** Start small. Break 1 server in a test environment. Then break 1 server in Production. Don't break 50 servers in Production on your first day.

**Key takeaway:** Distributed systems are so complex that the only way to know if they are reliable is to constantly, aggressively break them on purpose.
### Telemetry and Tracing

*File: `04-advanced-topics/02-system-reliability/03-telemetry-tracing.md`*

**What this covers:** How engineers debug failures in distributed microservices — the Three Pillars of Observability (Logs, Metrics, Traces) and how to wire them together with OpenTelemetry.

**Key topics:**
- Why debugging a microservice failure is like finding a broken link in a chain
- Logs: structured events at a point in time
- Metrics: numerical measurements over time (the graphs you watch)
- Traces: end-to-end request journeys across multiple services
- OpenTelemetry: the industry standard for capturing all three
- Practical: how to debug a P0 incident using traces + logs

**Key takeaway:** Distributed Tracing with a tool like Jaeger/Tempo is the defining skill that separates a senior engineer from a junior one. If something goes wrong at 3 AM, traces tell you exactly which service and which line of code caused a 2,000ms latency spike.
### Index Structures

*File: `04-advanced-topics/03-internals/01-index-structures.md`*

**What this covers:** How databases search through billions of rows of data instantly.

**Key topics:**
- **The Problem:** If you ask a database for "User #450", and it has to check every single row one by one (A Full Table Scan), it will take 10 minutes.
- **B-Tree Index (The Phonebook):** The standard database index. It sorts data into a massive tree. Perfect for searching ranges ("Find all users between ages 20 and 30").
- **Hash Index (The Coat Check):** A math trick that jumps instantly to the exact location. Blazing fast, but completely useless for searching ranges.
- **Inverted Index (The Book Glossary):** How Google and Elasticsearch work. Instead of mapping "Document -> Words", it maps "Word -> Documents".

**Key takeaway:** Indexes make reading data 100x faster, but they make writing data slightly slower (because you have to update the index every time you add data). You must choose the right type of index for your specific search queries.
### Consensus Protocols

*File: `04-advanced-topics/03-internals/02-consensus-protocols.md`*

**What this covers:** The exact mathematical algorithm computers use to agree on something when half the computers are broken.

**Key topics:**
- **The Problem:** If you have 5 computers, and the Leader dies, the remaining 4 computers will panic. Who is in charge now?
- **Consensus Protocols:** Algorithms like Paxos or Raft that allow computers to hold a democratic election and pick a new leader without human intervention.
- **Raft (The Easy One):** Every server has a random timer. The first timer to go off yells, "Vote for me!" If they get a majority of votes, they become the Leader. The Leader then forces everyone else to copy their notebook exactly.
- **Heartbeats:** The Leader constantly sends a "Heartbeat" message ("I'm alive! I'm alive!") every 50 milliseconds. If the followers stop hearing the heartbeat, they assume the Leader died, and they start a new election.

**Key takeaway:** Consensus protocols (specifically Raft) are the engine inside Apache ZooKeeper, etcd, and Kubernetes. They are the only way to build a truly self-healing distributed system.
### Kafka Internals

*File: `04-advanced-topics/03-internals/03-kafka-internals.md`*

**What this covers:** How Apache Kafka achieves 10M+ messages/sec throughput and the production-grade reliability guarantees that separate it from a simple queue.

**Key topics:**
- **Append-only log + sequential I/O** — why Kafka is fast
- **Partitions, offsets, consumer groups** — how it scales
- **Replication (ISR), acks, min.insync.replicas** — how it stays durable under failure
- **Leader election** — how it recovers automatically
- **Log compaction** — how Kafka acts as a state store
- **Exactly-once semantics** — idempotent producers + transactional API
- **Zero-copy + page-cache batching** — why commodity hardware is enough

**Key takeaway:** Kafka is fast because it exploits sequential disk I/O and an append-only log. It is reliable because ISR + acks + min.insync.replicas give you tunable durability. Understanding the full acks/ISR model is the most-tested senior follow-up in any Kafka interview question.
### Redis Internals

*File: `04-advanced-topics/03-internals/04-redis-internals.md`*

**What this covers:** How Redis works under the hood, and why it is the fastest database on Earth.

**Key topics:**
- **The Core Secret:** Redis stores 100% of its data in RAM (Memory), not on a physical Hard Drive. This makes it 100,000x faster than a normal database.
- **Single-Threaded Magic:** Redis only processes one command at a time. It doesn't use multiple CPU cores. This sounds slow, but it actually eliminates all "traffic jams" (Locking), making it incredibly fast.
- **Persistence (RDB vs AOF):** Because RAM forgets everything when the power goes out, Redis has to secretly back up its data to the hard drive.
  - *RDB:* Takes a massive snapshot of the RAM every 5 minutes. (Fast, but you might lose 4 minutes of data).
  - *AOF:* Writes every single command to a log file. (Safe, but a bit slower).

**Key takeaway:** Redis is essentially a giant "Dictionary" (Key-Value store) that lives in RAM. It is perfect for Caching, Leaderboards (Sorted Sets), and Rate Limiting.
### Cassandra Internals

*File: `04-advanced-topics/03-internals/05-cassandra-internals.md`*

**What this covers:** The database that Apple uses to store 10 Petabytes of data across 100,000 servers without a single point of failure.

**Key topics:**
- **The Problem:** Standard databases have a "Leader". If the Leader dies, the database freezes while it holds an election.
- **Masterless Architecture:** Cassandra has no Leaders. Every single server is completely equal. You can unplug 50 servers and it won't even flinch.
- **The Ring (Consistent Hashing):** Cassandra places all its servers on a giant mathematical clock to distribute data perfectly evenly.
- **Gossip Protocol:** How do 1,000 equal servers communicate without a Leader? They gossip. Server A whispers a secret to Server B. Server B whispers it to Server C. Within 1 second, all 1,000 servers know the secret.
- **Tunable Consistency:** You can choose your Quorum. Do you want lightning-fast, risky writes? Or slow, perfectly accurate writes? You choose on every single query!

**Key takeaway:** If you are building a system that requires 100% uptime (Availability) and massive Write speeds (like logging billions of IoT sensor metrics), Cassandra is the undisputed king.
### PostgreSQL Internals

*File: `04-advanced-topics/03-internals/06-postgresql-internals.md`*

**What this covers:** The magic trick that allows 100 people to read a database while 100 other people are writing to it, without anyone waiting in line.

**Key topics:**
- **The Problem:** If User A is reading a bank account balance, and User B is updating that balance at the exact same millisecond, the database might crash or return half-written, corrupted data.
- **Locking (The Old Way):** If User B is writing, lock the door. User A has to wait outside until User B finishes. Very slow.
- **MVCC (Multi-Version Concurrency Control):** The PostgreSQL magic trick. Instead of locking the door, PostgreSQL quietly makes a photocopy of the data for User A to read, while User B modifies the original document. Everyone is happy, nobody waits!
- **VACUUM:** Because MVCC creates thousands of old "photocopies", PostgreSQL has to hire a janitor (the VACUUM process) to clean up the trash in the background.
- **WAL (Write-Ahead Log):** How PostgreSQL survives power outages by writing down what it is *going* to do before it actually does it.

**Key takeaway:** PostgreSQL is the most beloved relational database in the world because MVCC allows for massive concurrency (thousands of users at once) without sacrificing absolute ACID strictness.
### MySQL Internals

*File: `04-advanced-topics/03-internals/07-mysql-internals.md`*

**What this covers:** How the most popular open-source database in the world (MySQL with InnoDB) actually organizes data on a hard drive.

**Key topics:**
- **Clustered Index (The Main Phonebook):** In MySQL, the Table *is* the Index. The data isn't just lying around randomly; it is physically sorted on the hard drive by the Primary Key (e.g., User ID).
- **Secondary Indexes (The Back Index):** If you want to search by "Last Name," MySQL builds a tiny, separate index. But it doesn't point to the data; it points back to the Primary Key! (A two-step lookup).
- **Redo Log (The Crash Saver):** If the power goes out, the Redo log ensures you never lose a committed transaction.
- **Undo Log (The Time Machine):** If you make a mistake and type `ROLLBACK`, the Undo log remembers the old data so it can magically reverse your changes.

**Key takeaway:** Because MySQL physically sorts data by the Primary Key, queries that search by ID are unbelievably fast. Queries that search by anything else (like email) require an extra "hop" through the Secondary Index.
### DynamoDB Internals

*File: `04-advanced-topics/03-internals/08-dynamodb-internals.md`*

**What this covers:** Amazon's proprietary NoSQL database that guarantees single-digit millisecond responses, no matter how massive your data gets.

**Key topics:**
- **The Problem:** Relational databases (like PostgreSQL) get slower as they get bigger. Amazon needed a database that runs at the exact same speed whether it holds 1 Gigabyte or 100 Petabytes of data.
- **Partition Keys (The Aisle Number):** How DynamoDB guarantees O(1) performance. It chops your data into physical partitions. You *must* provide the exact Partition Key to find your data.
- **Sort Keys (The Shelf Number):** Once you are in the correct Aisle, you can scan the items on the shelf using a Sort Key (e.g., "Find all orders in Aisle 5 between Jan 1st and Feb 1st").
- **Provisioned Capacity (RCUs/WCUs):** You don't rent a "Server" from Amazon. You rent "Read Capacity Units" and "Write Capacity Units". If you go over your limit, Amazon throttles your database instantly.
- **GSI (Global Secondary Index):** If you want to search by something other than the Partition Key, you have to pay Amazon to secretly copy all your data into a brand new table with a different Partition Key.

**Key takeaway:** DynamoDB forces you to design your database backwards. You must know exactly what your search queries will be *before* you are allowed to design your tables.
### Elasticsearch Internals

*File: `04-advanced-topics/03-internals/09-elasticsearch-internals.md`*

**What this covers:** How websites let you search for "black running shoes" and instantly find the exact product out of 10 million items.

**Key topics:**
- **The Problem:** Standard databases are terrible at searching paragraphs of text. If you use a SQL `LIKE '%running%'` query, it has to scan every single word in the database. It takes minutes.
- **The Inverted Index:** Instead of mapping "Document -> Words", Elasticsearch maps "Word -> Documents". (Like the Glossary at the back of a textbook).
- **Tokenization:** Before saving a document, Elasticsearch rips it apart. It removes punctuation, makes everything lowercase, and converts words to their root (e.g., "Running" becomes "run").
- **Apache Lucene:** The actual core engine that does the searching. Elasticsearch is just a giant wrapper around Lucene that allows it to scale across 100 servers.
- **Shards:** Chopping the Inverted Index into pieces so multiple servers can search at the exact same time.

**Key takeaway:** Elasticsearch isn't really a database. It is a highly specialized search engine. You should use a normal database (like PostgreSQL) to store your real data, and copy it to Elasticsearch purely for the search bar.
### ZooKeeper Internals

*File: `04-advanced-topics/03-internals/10-zookeeper-internals.md`*

**What this covers:** The tiny, ultra-reliable database that coordinates all the other massive databases.

**Key topics:**
- **The Problem:** If you have 50 Kafka servers, they all need to agree on exactly who is the Leader, what IP address they should use, and what the configuration settings are. If they disagree, the cluster explodes.
- **The Source of Truth:** ZooKeeper is a tiny, incredibly strict, CP (Consistent and Partition Tolerant) database. It uses a Consensus Protocol (ZAB, similar to Raft) to guarantee that the data it holds is mathematically perfect.
- **Znodes (The Folder Structure):** ZooKeeper stores data like a Mac/Windows file system. It has folders and files.
- **Ephemeral Nodes (The Dead Man's Switch):** A file that magically deletes itself if the server that created it stops responding. Perfect for detecting if a server crashed!

**Key takeaway:** ZooKeeper is rarely used by developers directly. It is used *internally* by massive tools (Kafka, Hadoop) as a "Source of Truth" to coordinate their clusters and manage leader elections.
### Raft and Paxos: Consensus Algorithms

*File: `04-advanced-topics/03-internals/11-raft-paxos-conceptual.md`*

**What this covers:** A deep, mechanism-level look at Raft and Paxos — the two consensus algorithms underlying nearly every production distributed database, log, and coordination service — including their phases, failure modes, and where each is used in real systems.

**Key concepts:**
- **Quorum:** with N nodes, decisions require a majority (⌊N/2⌋ + 1) to agree; any two majorities overlap in at least one node, which is the core safety insight behind both algorithms.
- **Paxos:** two-phase protocol (Prepare/Promise, then Accept/Accepted) run per value via Proposer/Acceptor/Learner roles; prone to "dueling proposers" livelock, solved in practice by Multi-Paxos (a stable elected leader, as used in ZooKeeper's ZAB).
- **Raft:** designed for understandability — a single strong leader, log-centric consensus (not single-value), explicit monotonic terms, randomized election timeouts to avoid split votes, and leader-driven log repair.
- **Raft vs Paxos:** Raft disallows log holes and mandates a leader at all times; Paxos (via Multi-Paxos) can leave holes that must be patched with no-ops. Raft powers etcd, CockroachDB, TiKV, Kafka's KRaft; Paxos/ZAB powers ZooKeeper, Chubby, and (per-shard) Spanner.
- **Failure scenarios:** split-brain is resolved because the minority partition can never reach quorum to commit; a leader crash after a majority commit is safe (new leader already has the entry) but requires client-side idempotency keys since the client may retry.

**Key takeaway:** Consensus algorithms guarantee that a cluster agrees on a single sequence of truth despite crashes and network partitions by requiring majority quorums; Raft is the practical default for new systems because its explicit leader election and log repair make it dramatically easier to implement correctly than Paxos.
### Stream Processing vs Batch Processing

*File: `04-advanced-topics/03-internals/12-stream-vs-batch.md`*

**What this covers:** A detailed comparison of batch processing (Spark) and stream processing (Flink), including micro-batch as a middle ground, windowing strategies, watermarks/late data, and the Lambda vs Kappa architecture debate.

**Key concepts:**
- **Batch (Spark):** processes a bounded, static dataset on a schedule; high throughput, simple fault tolerance (rerun the job), but latency of minutes to hours — used for nightly ETL, ML training, billing, compliance reports.
- **Stream (Flink):** processes an unbounded, continuous event sequence with persistent state, sub-second to seconds latency, and checkpoint-based fault tolerance — used for fraud detection, real-time dashboards, alerting, surge pricing.
- **Micro-batch (Spark Structured Streaming):** processes small time-boxed batches (seconds to minutes) using the same API as batch Spark — a pragmatic middle ground when sub-second latency isn't required.
- **Windowing:** tumbling (fixed, non-overlapping), sliding (overlapping), and session (gap-based) windows define how streaming aggregations group events in time; watermarks determine when a window can safely close given out-of-order/late-arriving events.
- **Lambda vs Kappa architecture:** Lambda ran separate batch and speed layers (two codebases that drift); Kappa replaces it with a single stream pipeline plus Kafka replay for historical backfill, and is the modern default.

**Key takeaway:** Choose stream processing when results must update in under a minute and completeness can be eventual (dashboards, alerts); choose batch when the dataset is bounded and correctness requires full data (ML training, compliance) — most production systems use both.

## Distributed Systems Patterns

### Outbox Pattern

*File: `09-patterns/01-data-consistency/01-outbox-pattern.md`*

**What this covers:** How to reliably send messages to other parts of your system immediately after updating your own database, solving the "Dual-Write Problem."

**Key concepts:**
- **The Dual-Write Problem:** If you update your database and then send a message, the messaging system might be down. If you send the message first, your database might crash. You get stuck in an inconsistent state.
- **The Outbox Table:** An extra table (or "folder") in the *same* database as your main data.
- **Transactional Guarantee:** You update your main data AND drop a message into the Outbox table in the *exact same database save*. It is all-or-nothing (atomic).
- **Message Relay:** A separate background worker (like Debezium) constantly checks the Outbox table and safely forwards those messages to the rest of the system.

**Key takeaway:** The Outbox Pattern is the gold standard for making sure a microservice reliably tells the rest of the system what it just did, without ever dropping a message.
### Two-Phase Commit

*File: `09-patterns/01-data-consistency/02-two-phase-commit.md`*

**What this covers:** A method used to ensure that a transaction involving multiple separate databases either completely succeeds everywhere, or completely fails everywhere (all-or-nothing).

**Key concepts:**
- **Phase 1 (Prepare):** The "Coordinator" asks all databases, "Are you ready and able to save this data?" Every database locks its data and replies "Yes" or "No".
- **Phase 2 (Commit/Rollback):** If *all* databases said "Yes", the Coordinator tells them to permanently save (Commit). If *any* database said "No", the Coordinator tells everyone to cancel (Rollback).
- **The Blocking Problem:** If the Coordinator crashes after Phase 1, the databases are stuck holding their data locked indefinitely until the Coordinator wakes back up.

**Key takeaway:** 2PC provides perfect data consistency, but it is slow and highly vulnerable to getting "stuck." Because of this, modern microservices usually avoid 2PC and use the Saga Pattern instead.
### Saga Pattern

*File: `09-patterns/01-data-consistency/03-saga-pattern.md`*

**What this covers:** How to manage complex workflows that span across multiple microservices without freezing up the whole system.

**Key concepts:**
- **Local Transactions:** A Saga is just a sequence of normal, local database saves. Service A saves its data, then tells Service B to do its work.
- **Compensating Transactions:** If a step fails (e.g., Service C fails), you cannot "undo" Service A and B like a traditional database rollback. You must execute *compensating* actions (like issuing a refund in Service A).
- **Choreography:** Services just listen to each other's events and react on their own. Good for simple flows (2-4 steps).
- **Orchestration:** A central controller explicitly tells each service what to do and handles the rollback logic. Good for complex flows.

**Key takeaway:** Sagas embrace "eventual consistency." They are essential for long-running business processes where locking data across multiple databases (like in Two-Phase Commit) would cause massive traffic jams.
### CQRS and Event Sourcing

*File: `09-patterns/02-architecture-and-scaling/01-cqrs-event-sourcing.md`*

**What this covers:** Two advanced architectural patterns for massive scale: CQRS (splitting reads and writes) and Event Sourcing (storing history instead of current state).

**Key concepts:**
- **CQRS (Command Query Responsibility Segregation):** Splitting your app into two halves. One half only handles Writes (Commands) using a strict, secure database. The other half only handles Reads (Queries) using a fast, pre-calculated database.
- **Event Sourcing:** Instead of saving a user's *current* data, you save a log of every *action* they ever took. To find their current data, you fast-forward through the log.
- **Eventual Consistency:** Because the fast Read Database is updated a few milliseconds after the Write Database, a user might occasionally see slightly stale data.

**Key takeaway:** These patterns are incredibly powerful for auditing and scaling, but they are also incredibly complex. Only use them when a normal database design physically cannot keep up with your traffic.
### Bulkhead Pattern

*File: `09-patterns/02-architecture-and-scaling/02-bulkhead-pattern.md`*

**What this covers:** How to design a system so that a failure in one minor component doesn't cause the entire system to crash.

**Key concepts:**
- **The Ship Metaphor:** A ship is built with watertight compartments (bulkheads). If the hull gets a hole, only one compartment floods, saving the whole ship from sinking.
- **Connection Pools:** In software, a server has a limited number of "workers" (threads). If you dedicate a small, limited pool of workers to each specific task, a broken task can only use up its own workers, leaving the rest of the system perfectly fine.
- **Hardware Isolation:** Running critical tasks and non-critical tasks on completely separate servers.

**Key takeaway:** The Bulkhead pattern is a defensive shield. It sacrifices a tiny bit of efficiency in exchange for a massive gain in system stability during emergencies.
### Retry and Idempotency

*File: `09-patterns/02-architecture-and-scaling/03-retry-and-idempotency.md`*

**What this covers:** How to safely retry a failed request without making things worse — and how to make retries safe to begin with.

**Key concepts:**
- **Retries fix transient failures**, not broken systems. Retrying a genuinely-down service just adds load.
- **Exponential backoff:** Wait 1s, 2s, 4s, 8s between attempts instead of hammering immediately.
- **Jitter:** Add randomness to the wait. Without it, every retrying client fires at the same instant and you get a *thundering herd* — the single most common wrong answer in interviews.
- **Idempotency:** An operation you can safely run twice. Because "the request timed out" never tells you whether the work actually happened.
- **Idempotency keys:** The client sends a unique ID; the server remembers what it already did with that ID and replays the same answer.

**Key takeaway:** Retries and idempotency are one topic, not two. Any retry you add is a duplicate-execution bug unless the operation on the other end is idempotent. Say both in the same breath and you sound senior.
### Strangler Fig

*File: `09-patterns/03-migration-and-pitfalls/01-strangler-fig.md`*

**What this covers:** A safe, step-by-step strategy for migrating a giant, messy legacy application (a monolith) into a modern microservices architecture.

**Key concepts:**
- **The Metaphor:** The strangler fig vine grows around a host tree. Over time, the vine grows stronger and the host tree dies, leaving only the vine.
- **The Gateway:** A router (API Gateway) is placed in front of the old system. It directs old traffic to the old system, and new traffic to the new microservices.
- **Incremental Migration:** You carve out exactly *one* feature at a time, build it as a new microservice, and update the router.

**Key takeaway:** "Big Bang" rewrites (where you spend 2 years rewriting the system from scratch and flip the switch all at once on day 730) almost always fail. The Strangler Fig pattern allows you to upgrade your system one small piece at a time while safely serving real customers.
### Anti-Patterns

*File: `09-patterns/03-migration-and-pitfalls/02-anti-patterns.md`*

**What this covers:** The most common mistakes engineers make when designing complex systems. Knowing what *not* to do is just as important as knowing what to do.

**Key concepts:**
- **Distributed Monolith:** Splitting an app into microservices, but keeping them so tangled together that if one fails, they all fail.
- **Shared Database:** Multiple microservices reading and writing to the exact same database tables.
- **N+1 Queries:** Making 100 small trips to the database instead of 1 big trip.
- **Thundering Herd:** When a broken server comes back online, and 10,000 waiting users hit it at the exact same millisecond, crashing it again.

**Key takeaway:** Senior engineers are defined by the anti-patterns they avoid. In an interview, spotting these traps and explaining why they are bad is a massive green flag.

---

# HLD & LLD Methodology

## HLD Methodology

### Requirements And Scope

*File: `05-hld-problems/00-methodology/01-requirements-and-scope.md`*

**What this covers:** Step 1 — turning a one-line prompt ("design Twitter") into a bounded, numbered requirements list across 4 fixed categories, before any architecture discussion starts.

**Key ideas:**
- Ask across 4 categories every time: Functional, Scale/Non-functional numbers, Constraints & explicit non-goals, Consistency expectations. Don't free-associate questions — a fixed checklist prevents the panic-freeze on an unfamiliar prompt.
- Explicit non-goals are as valuable as functional requirements — "we will NOT support X" is what lets you justify a smaller architecture later without it reading as a gap.
- Stop asking once you have enough to start the math (Step 2) — 5-8 functional requirements is normal; interviewers penalize both under-asking and over-asking (using all your time on questions).

**Key takeaway:** Everything you design later must trace back to a line in this list. If you can't point to which requirement justified a component, you're about to over-engineer or under-justify it.
### Capacity Estimation

*File: `05-hld-problems/00-methodology/02-capacity-estimation.md`*

**What this covers:** Step 2 — converting the raw numbers from Step 1 into QPS, storage, and bandwidth figures, and reading those figures to decide which architecture tier you're in *before* you draw anything.

**Key ideas:**
- Capacity estimation isn't a ritual to perform and forget — it's the input to Step 4. "We need 12 TB" is the sentence that forces "single Postgres instance" off the table, not a preference.
- Use round numbers (1 day ≈ 100K seconds, not 86,400) — precision doesn't matter, order of magnitude does.
- Always compute both average and peak (peak is usually 2-3x average for consumer traffic, higher for flash-sale/event-driven traffic) — architecture decisions should be sized to peak, not average.
- Map the final numbers to a tier using the architecture-by-scale cheat sheet — this tells you what NOT to propose as much as what to propose.

**Key takeaway:** If you can't point to which number in your capacity estimate justifies a given component (sharding, caching, a queue), you added that component from memory of a similar problem, not from this problem's actual load.
### Api And Data Model

*File: `05-hld-problems/00-methodology/03-api-and-data-model.md`*

**What this covers:** Step 3 — deriving the API contract and the data model directly from the Step 1 requirements list, before drawing a single architecture box.

**Key ideas:**
- Every requirement produces at least one endpoint. If a requirement doesn't map to an endpoint, either the requirement is incomplete or the API is missing something — this is a mechanical cross-check, not a formality.
- Design the API first, the schema second — the API is the contract callers rely on; the schema is an implementation detail that can change without breaking anyone.
- For the data model, decide entities and their fields from the API request/response shapes you just wrote, then decide SQL vs. NoSQL using the decision-trees reference — don't pick the database before you know what you're storing.
- Keep the API minimal — one endpoint per requirement, not one per imagined future feature. Extra endpoints invite "why does this exist" questions you can't answer from the requirements list.

**Key takeaway:** If you can't point to which Step 1 requirement justifies a given endpoint or field, you're speculating, not deriving — cut it or trace it.
### Deriving The Architecture

*File: `05-hld-problems/00-methodology/04-deriving-the-architecture.md`*

**What this covers:** Step 4, the centerpiece of the process — a signal table mapping specific requirement phrasings and capacity numbers to the component each one forces, plus the discipline to draw a box only when you can cite what forced it.

**Key ideas:**
- Read the table by symptom, not by component name — you'll rarely think "I should add a cache"; you'll notice "same data read far more than it's written" and the table tells you that's a cache.
- Every component needs a one-sentence justification tied to a specific Step 1 requirement or Step 2 number. No justification → don't draw it.
- Build the diagram left to right, following the request path: client → edge → app tier → data tier → async/background paths last. This ordering itself prevents the common mistake of drawing infrastructure before the request path that needs it.
- Most solved HLD problems combine 4-8 components, not one clever trick. The signal table composes — a real prompt usually triggers several rows at once.

**Key takeaway:** If you can't point to the requirement or number that demands a component, you're adding it from memory of a similar-looking problem, and an interviewer probing "why is that there" will expose it immediately.
### Identifying The Bottleneck

*File: `05-hld-problems/00-methodology/05-identifying-the-bottleneck.md`*

**What this covers:** Step 5 — how to pick the ONE component from your Step 4 diagram worth a real deep dive, and how to produce a staff-level answer on it instead of a correct-but-shallow one.

**Key ideas:**
- Don't go deep on everything — you don't have time, and breadth-over-depth is the SDE-2 pattern. Pick one, based on where your own numbers say the stress actually is.
- The bottleneck is usually wherever Step 2's numbers are most extreme relative to a single machine/instance's realistic capacity — highest QPS, largest storage growth rate, or the component with the least slack.
- A deep dive is not "explain how the component works" — it's the staff-signal shape: mechanism, failure mode, quantified tradeoff, rejected alternative.
- If the interviewer picks the deep-dive target instead of you, that's fine — the same discipline applies, just applied to their chosen component instead of your own pick.

**Key takeaway:** The architecture diagram gets you to "competent." The one deep dive, done with real mechanism and tradeoffs, is what actually moves the needle to "hire at SDE-3."
### Worked Example End To End

*File: `05-hld-problems/00-methodology/06-worked-example-end-to-end.md`*

**What this covers:** All 5 methodology steps run back-to-back on one prompt — "Design a read-it-later bookmarking service" (Pocket/Instapaper-like) — a problem not solved elsewhere in `05-hld-problems/`, so the reasoning can be followed without recalling a memorized answer.

**Key ideas:**
- This doc collects the same worked example threaded through Steps 1-5 into one continuous read — it's not new content, it's proof the process composes into a full design.
- Every artifact shown (requirements list, capacity numbers, API/schema, architecture diagram, deep dive) is the literal deliverable each step doc says to produce.
- Cover the final diagram and try to redo Steps 2-5 yourself from just the Step 1 requirements list before reading further — that's the actual practice rep.

**Key takeaway:** Nothing in this design was pattern-matched from memory. Every decision traces back to one line in the Step 1 requirements list — that traceability is what makes the process work on a prompt you've genuinely never seen.
### Interview Playbook

*File: `05-hld-problems/00-methodology/07-interview-playbook.md`*

**What this covers:** Running Steps 1-5 under a real 45-60 minute clock, with time budgets per phase, pacing checkpoints, and recovery moves for when you're behind.

**Key ideas:**
- This doc doesn't re-teach the phases — the HLD template already has the timeline and phase content. This doc adds the pacing discipline specific to running the *derivation* process (Steps 1-5) inside that timeline.
- The single most common failure isn't missing knowledge, it's time mismanagement — over-spending on requirements or the diagram and leaving no time for the one deep dive that actually produces staff signal.
- Build in a mid-interview checkpoint: at the 25-30 minute mark, you should have a diagram with justified boxes. If you don't, cut scope, don't cut the deep dive.

**Key takeaway:** A finished, well-justified architecture with no deep dive scores lower than a slightly rougher architecture with one real deep dive — protect the last 10-15 minutes at all costs.
### Practice Drills

*File: `05-hld-problems/00-methodology/08-practice-drills.md`*

**What this covers:** A timed drill set — problem prompts only, no solutions — to practice running Steps 1-5 yourself. Self-check by comparing your output against the closest solved problem in `05-hld-problems/`, not by reading a provided answer.

**Key concepts:**
- How to use this: set a timer per the difficulty tier. Produce the actual artifacts — written requirements list, capacity numbers, API table, architecture diagram, one real deep dive — not just mental notes.
- Then compare against the pointed-to solved problem and note where your reasoning diverged and why, not just whether the boxes matched.

**Key takeaway:** The goal isn't to match the solved problem's diagram exactly — different valid architectures exist at the same scale. The goal is to check whether every box and every deep-dive claim in your output traces back to a requirement or a number, same as the process demands.

## LLD Methodology

### Problem Decomposition

*File: `06-lld/00-methodology/01-problem-decomposition.md`*

**What this covers:** Step 1 of the LLD process — turning a one-line prompt ("Design a parking lot") into a concrete, bounded requirements list before touching classes or diagrams.

**Key ideas:**
- Never start designing from the raw prompt; the raw prompt is deliberately underspecified — that's the test.
- Ask clarifying questions across 5 fixed categories: Scope, Actors, Core Flow, Constraints, Non-functional. Same 5 categories, every problem.
- Write down the answers as a numbered requirements list before Step 2. This list is your contract — every class you draw later must trace back to a line in it.
- Silence from the interviewer on a question means "use reasonable judgment, state your assumption out loud."

**Key takeaway:** A requirements list with 8-12 concrete bullet points is the actual deliverable of Step 1. If you can't write that list, you're not ready to draw classes yet — go back and ask more questions.
### Nouns To Classes

*File: `06-lld/00-methodology/02-nouns-to-classes.md`*

**What this covers:** Step 2 of the LLD process — mechanically converting your requirements list into candidate classes by underlining nouns, then filtering each one through a 4-way classification.

**Key ideas:**
- Underline every noun in your requirements list. Each one is a *candidate* — not automatically a class.
- Classify each candidate as: Entity (has identity + lifecycle), Value Object (immutable, defined by its values), Enum (closed, small set of options), or Attribute (belongs inside another class, isn't its own class).
- The identity test is the single most useful filter: "do I need to tell two instances apart even if all their fields are equal?" Yes → Entity. No → Value Object.
- Under-modeling (missing a class) gets caught in Step 3 when a relationship doesn't make sense. Over-modeling (a class for everything) is the more common interview failure — resist it.

**Key takeaway:** Every noun is a suspect, not a verdict. The classification step is what separates a clean 6-8 class diagram from a bloated 20-class one that buries the interview in ceremony.
### Relationships And Uml

*File: `06-lld/00-methodology/03-relationships-and-uml.md`*

**What this covers:** Step 3 of the LLD process — for every pair of related classes from Step 2, deciding the relationship type (association / aggregation / composition / inheritance) and cardinality, then drawing the actual UML class diagram.

**Key ideas:**
- Four relationship questions, asked in order, for every class pair: (1) Is it IS-A or HAS-A? (2) If HAS-A, does the part's lifecycle depend on the whole? (3) Can the part be shared across multiple wholes? (4) What's the cardinality on each side?
- Composition (filled diamond) = part dies with whole, not shared. Aggregation (hollow diamond) = part outlives whole, can be shared. Association = a plain "uses/knows about" link, often via a method parameter, no ownership implied.
- Default to composition or association; reach for inheritance only when LSP holds — prefer composition over inheritance when in doubt.
- Draw the diagram as boxes with a 3-part layout (name / fields / methods) connected by typed arrows.

**Key takeaway:** The relationship type isn't a UML trivia question — it dictates real code (does the constructor take the object or create it? does deleting the parent cascade-delete the child?). Get the relationship right and the code follows almost mechanically.
### Verbs To Methods And Interfaces

*File: `06-lld/00-methodology/04-verbs-to-methods-and-interfaces.md`*

**What this covers:** Step 4 of the LLD process — extracting verbs from the requirements list and assigning each to the class that should own it, then deciding when a verb needs an interface instead of a concrete method.

**Key ideas:**
- Underline every verb/action in the requirements. Each verb becomes a candidate method on exactly one class.
- Ownership rule: a method belongs to the class that owns the data it primarily reads/mutates ("Tell, Don't Ask" / high cohesion). If a method needs data from two classes equally, it's a sign a third coordinating class (a Service/Manager) is missing.
- A verb wants an interface when the requirements say or imply "this should be swappable/pluggable/vary by type" — that phrasing is the same signal Step 5 uses to pick Strategy, Factory, etc.
- Keep methods on entities behavioral, not just getters/setters — anemic domain models (all data, no behavior) are a common interview criticism.

**Key takeaway:** Assigning a verb to a class is a cohesion decision, not a naming exercise — the question is always "which object's internal state does this action primarily change?"
### Spotting The Pattern

*File: `06-lld/00-methodology/05-spotting-the-pattern.md`*

**What this covers:** Step 5 of the LLD process — a signal table mapping the exact phrasing interviewers use in requirements to the design pattern that resolves it, plus the discipline to *not* apply a pattern when it isn't earning its complexity.

**Key ideas:**
- Patterns aren't chosen because they're impressive — they're chosen because a specific requirement phrase creates a specific structural problem, and the pattern is the named solution to that exact problem.
- Read the table by requirement phrasing, not by pattern name — you'll rarely think "I should use Observer"; you'll notice "multiple parts of the system need to react when X changes" and the table tells you that's Observer.
- Every pattern application needs a one-sentence justification tied back to a Step 1 requirement. No justification → don't add the pattern.
- Most solved LLD problems combine 2-4 patterns, not one.

**Key takeaway:** If you can't point to the sentence in the requirements that demands a pattern, you're adding accidental complexity, and interviewers penalize that as much as missing a needed pattern.
### Worked Example End To End

*File: `06-lld/00-methodology/06-worked-example-end-to-end.md`*

**What this covers:** All 5 methodology steps run back-to-back on one fresh prompt — "Design a Gym Membership & Class Booking System" — a problem not already solved elsewhere in this repo, so you can follow the reasoning without recalling a memorized answer.

**Key ideas:**
- This doc is meant to be read *after* Steps 1-5, as proof the process produces a real design mechanically, not as a shortcut to skip the individual step docs.
- Every artifact shown (requirements list, class table, diagram, method table, pattern list) is the literal deliverable each step doc says to produce.
- Cover the final diagram and try to redo Steps 2-5 yourself from just the Step 1 requirements list before reading further — that's the actual practice rep.

**Key takeaway:** Nothing in this design was pattern-matched from memory. Every decision traces back to one line in the Step 1 requirements list — that traceability is what makes the process work on a problem you've genuinely never seen.
### Interview Playbook

*File: `06-lld/00-methodology/07-interview-playbook.md`*

**What this covers:** How to run the 5-step process live in a 45-minute interview under a clock — time-boxing per step, what to say out loud, and how to answer the follow-up questions interviewers commonly ask at each stage.

**Key concepts:**
- Time budget (45-minute round): Step 1 Decompose — 5 min; Steps 2-3 Classes + UML — 12 min; Step 4 Methods/interfaces — 8 min; Step 5 Patterns (woven into Step 4 verbally) — included above; Code core flow — 15 min; Extend/follow-ups — 5 min buffer.

**Key takeaway:** The clock punishes silent thinking, not wrong turns. Narrate every decision from the process docs — interviewers are grading whether you have a repeatable method, not whether you reach the exact diagram they had in mind.
### Practice Drills

*File: `06-lld/00-methodology/08-practice-drills.md`*

**What this covers:** A timed drill set — problem prompts only, no solutions — to practice running the 5-step process yourself. Self-check by comparing your output against the closest solved problem in `05-problems/`, not by reading a provided answer.

**Key concepts:**
- How to use this: set a timer per the difficulty tier (20/30/40 min). Produce the actual artifacts — written requirements list, class table, diagram, method table, pattern list with justifications — not just mental notes.
- Then compare against the pointed-to solved problem and note where your reasoning diverged and why, not just whether the final class names matched.

**Key takeaway:** The goal isn't to match the solved problem's diagram exactly — different valid designs exist. The goal is to check whether every class/relationship/pattern in your output traces back to a requirement, same as the process demands.

---

# LLD Core Concepts

## OOP Fundamentals

### Four Pillars

*File: `06-lld/01-oop-fundamentals/four-pillars.md`*

**What this covers:** The four pillars of OOP with deep dives, real-world analogies, Java/Python code examples, and design principle implications.

**The four pillars:**
- Encapsulation: bundle data + methods; private fields, public getters/setters; prevents invalid state; e.g., BankAccount hides `balance`, exposes `deposit()`/`withdraw()`
- Inheritance: IS-A relationship; child inherits parent's interface and/or implementation; `Dog extends Animal`; avoid deep hierarchies (>2 levels = problem)
- Polymorphism: same method name, different behavior per class; runtime polymorphism via method overriding; compile-time via overloading; enables `List<Animal>` containing Dogs and Cats
- Abstraction: hide complexity behind simple interface; abstract classes (partial implementation) and interfaces (pure contract); `PaymentGateway` interface hides Stripe/PayPal details
- IS-A vs HAS-A: inheritance (IS-A) = tight coupling; composition (HAS-A) = flexible; `Car HAS-A Engine` (not IS-A); prefer composition
- This file covers: analogies, code examples in Java and Python, common mistakes, interview questions

**Key takeaway:** Polymorphism via interfaces is the most interview-relevant pillar — it's how you swap algorithms (Strategy), handle events (Observer), and build extensible systems (OCP); memorize the `Shape.draw()` example.
### Introduction

*File: `06-lld/01-oop-fundamentals/introduction.md`*

**What this covers:** Why OOP exists — the historical context, the problems it solves, and how it changed software development from procedural to object-oriented thinking.

**Key concepts:**
- Problem OOP solves: procedural code = giant interconnected blob; change one function → cascade of breaks; OOP = modular containers (classes) with clear boundaries
- Object = data (state) + behavior (methods) bundled together; state is hidden from outside (encapsulation); interact via public interface only
- Class vs Object: class is blueprint, object is instance; multiple objects from one class, each with independent state
- Message passing: objects communicate by calling methods (sending messages); loose coupling through interfaces
- When to use OOP: modeling real-world entities with state and behavior; large systems requiring team collaboration; systems that need extensibility
- Mindmap covers: OOP introduction → classes/objects → encapsulation → inheritance → polymorphism → abstraction → design principles

**Key takeaway:** OOP's core value is managing complexity through encapsulation — hide state behind methods, expose minimal interface, change internals without breaking callers.
### Java OOPs

*File: `06-lld/01-oop-fundamentals/java-oops.md`*

**What this covers:** Java OOP syntax reference for LLD interviews — classes, objects, access modifiers, interfaces, abstract classes, generics, and Java-specific patterns.

**Key topics:**
- Class structure: fields, constructors, methods; `public`/`private`/`protected`/package-private; `static` vs instance members; `final` for immutability
- Interfaces: `interface` = pure contract (all methods abstract by default); `implements`; from Java 8: `default` methods allow interface evolution without breaking implementors
- Abstract classes: `abstract class` = partial implementation; cannot instantiate; `extends`; use when subclasses share code; interface = behavior contract, abstract class = shared code
- Key Java-specific: `@Override` annotation; `equals()`/`hashCode()` contract; `Comparable` vs `Comparator`; generics (`List<T>`, bounded wildcards `<T extends Comparable<T>>`)
- Enums: use for fixed set of constants (Order.Status: PENDING, SHIPPED, DELIVERED); enums in Java are classes and can have methods
- Common patterns in LLD: `ParkingLot implements Singleton`; `VehicleFactory.create(type)`; `PaymentStrategy` interface with `CreditCard`, `UPI` implementations

**Key takeaway:** For LLD interviews in Java — interfaces for behavior contracts, abstract classes for shared implementation, enums for state machines; master the Builder pattern via telescoping constructor problem.
### Principles

*File: `06-lld/01-oop-fundamentals/principles.md`*

**What this covers:** OOP design principles — IS-A vs HAS-A, composition vs inheritance, plus DRY/KISS/YAGNI/Law of Demeter; the decision frameworks that determine class structure.

**Key principles:**
- IS-A (inheritance): only when the relationship is truly "B is a type of A" forever; `Dog IS-A Animal` ✓; `Stack IS-A Vector` ✗ (Java design mistake)
- HAS-A (composition): preferred; `Car HAS-A Engine`; change behavior by swapping components; less coupling; easier testing
- Why prefer composition: inheritance exposes internals; subclasses depend on parent implementation details; changes in parent break all children
- DRY (Don't Repeat Yourself): every piece of knowledge has one authoritative location; duplication = bugs (fix in one place, forget the other)
- KISS (Keep It Simple, Stupid): don't add complexity before it's needed; simplest solution that works is often correct
- YAGNI (You Ain't Gonna Need It): don't implement features "in case they're needed"; adds complexity, delays delivery
- Law of Demeter: talk only to your immediate dependencies (don't chain: `a.getB().getC().doSomething()`); reduces coupling

**Key takeaway:** The composition vs inheritance decision is the most important judgment call in LLD interviews — default to composition (HAS-A); only use inheritance when the IS-A relationship is permanently true and you want code reuse.
### Python OOPs

*File: `06-lld/01-oop-fundamentals/python-oops.md`*

**What this covers:** Python OOP syntax reference for LLD interviews — classes, dataclasses, ABC, properties, dunder methods, and Python-specific patterns.

**Key topics:**
- Class structure: `__init__` constructor; `self` is explicit; `_private` convention (not enforced); `__private` name mangling (double underscore)
- Properties: `@property` for getters; `@prop.setter` for setters with validation; Pythonic alternative to Java getters/setters
- Class methods / static: `@classmethod` (receives cls, factory methods); `@staticmethod` (no cls/self, utility functions); useful for alternative constructors
- ABC (Abstract Base Class): `from abc import ABC, abstractmethod`; `class Shape(ABC): @abstractmethod def area()`; enforces interface implementation
- Dunder methods: `__str__`/`__repr__` (string representation), `__eq__`/`__hash__` (equality), `__lt__` (comparison/sorting), `__len__`, `__iter__` (iteration protocol)
- Dataclasses: `@dataclass` auto-generates `__init__`, `__repr__`, `__eq__`; `frozen=True` for immutability; great for value objects
- Multiple inheritance: Python supports it; MRO (Method Resolution Order) via C3 linearization; prefer mixins over complex hierarchies

**Key takeaway:** Python's ABC + `@abstractmethod` replaces Java interfaces for LLD; use dataclasses for value objects and DTOs; `@property` gives Java-style encapsulation without explicit getter/setter boilerplate.

## SOLID Principles

### Single Responsibility Principle

*File: `06-lld/02-solid-principles/01-single-responsibility.md`*

**What this covers:** The Single Responsibility Principle (SRP) — the first SOLID principle; states that a class should have one, and only one, reason to change.

**Key concepts:**
- The problem: a "God Object" (e.g., an `Employee` class that calculates pay, saves to DB, and formats reports). Changes to any of these three areas require modifying the same class.
- The fix: split the class by "reason to change" (or actor). `PayCalculator`, `EmployeeRepository`, `EmployeeReportFormatter`.
- Cohesion: SRP increases cohesion (methods in a class are highly related). Low cohesion implies SRP violation.
- Coupling: SRP reduces coupling. A change to database schema no longer impacts payroll calculation logic.
- How to spot violations: look for class names with "And" (e.g., `ReportAndPrinter`), very long classes, or classes importing too many unrelated packages.

**Key takeaway:** A "reason to change" maps to a stakeholder or concern (DBA vs HR vs Product). If two different stakeholders can request changes that touch the same class, you have an SRP violation.
### Open/Closed Principle

*File: `06-lld/02-solid-principles/02-open-closed.md`*

**What this covers:** The Open/Closed Principle (OCP) — software entities should be open for extension but closed for modification.

**Key concepts:**
- The problem: massive `switch` or `if/else` statements. Adding a new case (e.g., a new shape to an area calculator) requires modifying existing, tested code.
- The risk: modifying existing code risks introducing regressions into already working features.
- The fix: polymorphism. Define an interface (`Shape` with `area()`). The calculator calls the interface. Add new shapes by creating new classes that implement `Shape`.
- "Closed for modification": the `AreaCalculator` class never changes when new shapes are added.
- "Open for extension": the system's behavior is extended by adding new shape classes.
- Strategy Pattern: OCP is the foundation of the Strategy Pattern (swapping algorithms at runtime without changing the caller).

**Key takeaway:** If you have to open an existing file to add a new feature (like a new payment method or a new notification type), you are violating OCP. Use interfaces and polymorphism instead.
### Liskov Substitution Principle

*File: `06-lld/02-solid-principles/03-liskov-substitution.md`*

**What this covers:** The Liskov Substitution Principle (LSP) — subclasses must be substitutable for their base classes without breaking program correctness.

**Key concepts:**
- The problem: a subclass changes the expected behavior of a parent class method. E.g., `Ostrich extends Bird` but throws an exception on `fly()`.
- The symptom: callers are forced to use `instanceof` checks (`if (bird instanceof Ostrich)`) to avoid crashing. This defeats polymorphism.
- The classic violation: `Square extends Rectangle`. If a caller expects to change width independently of height, a `Square` will break that expectation.
- The fix: break the inheritance hierarchy. `Ostrich` and `Sparrow` should both extend `Bird`, but only `Sparrow` implements `Flyable`.
- Contracts: subclasses must honor the contract of the parent. They cannot strengthen preconditions (require more) or weaken postconditions (guarantee less).

**Key takeaway:** If a subclass implements a parent method by throwing `UnsupportedOperationException`, or if a caller needs an `instanceof` check to safely use an object, you have an LSP violation. Fix it by segregating interfaces or using composition.
### Interface Segregation Principle

*File: `06-lld/02-solid-principles/04-interface-segregation.md`*

**What this covers:** The Interface Segregation Principle (ISP) — clients should not be forced to depend on methods they do not use.

**Key concepts:**
- The problem: "Fat" interfaces. A `Worker` interface with `work()`, `eat()`, and `sleep()`. A `Robot` class implements `Worker` but has to leave `eat()` and `sleep()` empty or throw exceptions.
- The symptom: classes implementing interfaces with dummy methods or returning `null` just to satisfy the compiler. This leads directly to LSP violations.
- The fix: split the fat interface into smaller, highly cohesive, role-specific interfaces (`Workable`, `Eatable`, `Sleepable`).
- The result: `Human` implements all three. `Robot` implements only `Workable`. Neither class is forced to implement methods it doesn't need.
- Client-centric design: interfaces should be designed based on what the *caller* needs, not what the *implementer* happens to do.

**Key takeaway:** ISP is SRP for interfaces. Instead of one massive `MultiFunctionPrinter` interface, create `Printer`, `Scanner`, and `Fax` interfaces. Classes can implement multiple interfaces if they support multiple roles.
### Dependency Inversion Principle

*File: `06-lld/02-solid-principles/05-dependency-inversion.md`*

**What this covers:** The Dependency Inversion Principle (DIP) — high-level modules should not depend on low-level modules; both should depend on abstractions.

**Key concepts:**
- The problem: high-level business logic (`OrderService`) instantiating low-level infrastructure (`MySQLDatabase`). If you change to PostgreSQL, you have to rewrite `OrderService`.
- The symptom: `new` keywords scattered throughout business logic classes for infrastructure or external services. Impossibility of unit testing without real databases/network.
- The fix: inversion. Define an abstraction (`OrderRepository` interface). `OrderService` depends on the interface. `MySQLDatabase` implements the interface.
- Dependency Injection (DI): how DIP is implemented. Instead of creating its own dependencies, `OrderService` receives them via its constructor (e.g., from Spring).
- Testing: DIP makes mocking trivial. Inject a `MockOrderRepository` into `OrderService` to test business logic in isolation.

**Key takeaway:** "Depend on abstractions, not concretions." You should never use `new` inside a high-level class to create a low-level dependency. Pass it in via the constructor as an interface.

## Design Patterns (Gang of Four)

### Abstract Factory Pattern

*File: `06-lld/03-design-patterns/01-creational/abstract-factory-pattern.md`*

**What this covers:** The Abstract Factory Pattern — provides an interface for creating families of related or dependent objects without specifying their concrete classes.

**Key concepts:**
- The problem: you have *families* of products (e.g., Mac UI vs Windows UI). A Mac Button must be used with a Mac Checkbox. Mixing a Mac Button with a Windows Checkbox breaks the system.
- The fix: create an `UIFactory` interface with `createButton()` and `createCheckbox()`.
- Concrete factories: `MacFactory` implements it (returns Mac items). `WinFactory` implements it (returns Win items).
- Client usage: The client receives a `UIFactory`. It doesn't know (or care) which OS it's on. It just calls `factory.createButton()`, guaranteeing compatible products.
- Difference from Factory Method: Factory Method creates *one* product. Abstract Factory creates *multiple related* products (a family).

**Key takeaway:** Use Abstract Factory only when you have multiple, distinct product families and you must enforce that objects from different families are never mixed. It's rare in standard LLD problems but common in framework design.
### Builder Pattern

*File: `06-lld/03-design-patterns/01-creational/builder-pattern.md`*

**What this covers:** The Builder Pattern — separates the construction of a complex object from its representation, allowing step-by-step creation.

**Key concepts:**
- The problem: the "Telescoping Constructor" anti-pattern. A class has many optional fields, leading to `User(name, null, null, 25, null)`. Unreadable, error-prone, hard to maintain.
- The fix: a static nested `Builder` class. The Builder has the same fields. It exposes fluent setter methods that return `this`.
- The build method: `build()` calls the private `User` constructor, passing the builder instance. It validates all constraints before creating the object.
- Immutability: Builder is the best way to construct immutable objects (no setters on the final `User` class) that have many optional parameters.
- Usage: `User u = new User.Builder("Alice").age(25).phone("123").build();`

**Key takeaway:** If a class has more than 4 parameters or multiple optional parameters, use the Builder pattern. It's universally expected in Java LLD interviews for creating domain models and configuration objects.
### Factory Pattern

*File: `06-lld/03-design-patterns/01-creational/factory-pattern.md`*

**What this covers:** The Factory Pattern (Simple Factory & Factory Method) — creates objects without specifying the exact class to create.

**Key concepts:**
- The problem: business logic filled with `if/else` statements calling `new SMSNotification()`, `new EmailNotification()`. If a new type is added, you violate OCP by modifying the business logic.
- Simple Factory: extract the `if/else` creation logic into a single `NotificationFactory` class. The client passes a string/enum, gets back the interface (`Notification`).
- Factory Method (GoF): define an interface for creating an object, but let subclasses decide which class to instantiate. E.g., `Logistics` class has abstract `createTransport()`. `RoadLogistics` returns `Truck`, `SeaLogistics` returns `Ship`.
- Benefit: highly decoupled. The client code only depends on the `Notification` interface, not the concrete implementations.
- Use cases: whenever object creation logic is complex, requires conditionals based on input, or depends on configurations.

**Key takeaway:** This is the most frequently used pattern in LLD interviews. Whenever you have different types of a thing (e.g., Vehicles in a Parking Lot, Cards in a Deck, Payment Methods), use a Factory to create them.
### Prototype Pattern

*File: `06-lld/03-design-patterns/01-creational/prototype-pattern.md`*

**What this covers:** The Prototype Pattern — used to clone existing objects without coupling to their specific classes, especially when creation is expensive.

**Key concepts:**
- The problem: creating an object from scratch is expensive (e.g., requires DB calls, parsing XML, or complex math). Or, you have an object and want a copy, but its fields are private.
- The fix: define a `clone()` method on the object itself (via a `Prototype` interface).
- Shallow vs Deep Copy: The hardest part of this pattern.
- Shallow copy: copies primitive fields and *references* to objects. Modifying a nested object in the clone modifies the original!
- Deep copy: recursively copies every nested object. Modifying the clone does not affect the original.
- Registry: often paired with a Prototype Registry (a Map) storing pre-configured prototypes (e.g., `registry.get("basic_enemy").clone()`).

**Key takeaway:** Use Prototype when object initialization is costly and you need many similar instances. In Java, beware of the default `Object.clone()` (it's a shallow copy and requires `Cloneable`, which is considered a broken interface).
### Singleton Pattern

*File: `06-lld/03-design-patterns/01-creational/singleton.md`*

**What this covers:** The Singleton Pattern — ensures a class has only one instance and provides a global access point to it.

**Key concepts:**
- The problem: multiple instances of shared resources (like DB connection pools or loggers) waste memory and cause conflicts.
- Implementation: private constructor, static variable holding the instance, static `getInstance()` method.
- Thread safety: lazy initialization in multithreaded environments causes race conditions (creating multiple instances).
- Double-checked locking: the standard Java fix. Check if null, `synchronized` block, check if null again. Variable must be `volatile`.
- Enum Singleton: Joshua Bloch's recommended Java approach. Thread-safe by default, handles serialization automatically.
- Anti-pattern: Singleton is often considered an anti-pattern because it acts like global state, making unit testing difficult. Dependency Injection (Spring) handles singletons better.

**Key takeaway:** If asked to implement a Singleton in a Java interview, you must know how to write the double-checked locking version and explain why `volatile` is required (prevents instruction reordering).
### Adapter Pattern

*File: `06-lld/03-design-patterns/02-structural/adapter-pattern.md`*

**What this covers:** The Adapter Pattern — allows objects with incompatible interfaces to collaborate.

**Key concepts:**
- The problem: your system expects an interface (e.g., `PaymentGateway`), but the third-party library you must use has a different interface (`RazorpayClient`). You can't change either.
- The fix: create an `Adapter` class that implements your expected interface (`PaymentGateway`), and holds a reference to the third-party object (`RazorpayClient`).
- The mapping: inside the `Adapter`, map the methods and data types from what your system passes in, to what the third-party object expects.
- Client usage: The client code only talks to the `Adapter` via the known interface. It is unaware of the third-party library underneath.
- Analogy: a travel plug adapter that lets a European laptop plug into a US wall socket.

**Key takeaway:** Adapter is the standard solution whenever you integrate with legacy code or third-party APIs. It protects your core business logic from being polluted by external dependencies.
### Bridge Pattern

*File: `06-lld/03-design-patterns/02-structural/bridge-pattern.md`*

**What this covers:** The Bridge Pattern — decouples an abstraction from its implementation so that the two can vary independently.

**Key concepts:**
- The problem: class explosion via inheritance. If you have 3 shapes (Circle, Square, Triangle) and 3 colors (Red, Blue, Green), inheritance requires 9 classes (`RedCircle`, `BlueSquare`, etc.).
- The fix: favor composition over inheritance. Split them into two separate hierarchies: `Shape` and `Color`.
- The bridge: The `Shape` class holds a reference to a `Color` object (the bridge). When `Shape` needs to draw, it delegates the color part to its composed `Color` object.
- Result: you now have 3 Shape classes + 3 Color classes = 6 classes (instead of 9). Adding a new shape (e.g., Pentagon) requires adding exactly 1 class, not 3.
- Common use case: Cross-platform UI (a `Button` abstraction bridging to a `WindowsRenderer` or `MacRenderer` implementation).

**Key takeaway:** Bridge is the ultimate application of "prefer composition over inheritance." Whenever you see an inheritance tree growing multiplicatively on two different dimensions, use a Bridge to split them.
### Composite Pattern

*File: `06-lld/03-design-patterns/02-structural/composite-pattern.md`*

**What this covers:** The Composite Pattern — composes objects into tree structures to represent part-whole hierarchies, allowing clients to treat individual objects and compositions uniformly.

**Key concepts:**
- The problem: operating on trees of objects where leaves and branches have different interfaces. E.g., getting the total price of a shopping cart containing loose Items and bundled Boxes (which contain more items/boxes).
- The fix: define a common interface (`Component`) for both leaf nodes and composite nodes (e.g., `FileSystemNode` with `getSize()`).
- Leaf: represents end objects (e.g., `File`). `getSize()` returns its own size.
- Composite: represents complex objects (e.g., `Folder`). Contains a list of `Component`s. Its `getSize()` iterates through children and sums their sizes.
- Client usage: The client calls `getSize()` on the root. It doesn't care if it's looking at a single file or a folder with a million nested files.

**Key takeaway:** If an interview problem involves a tree structure (File System, Organization Chart, UI DOM Tree, Nested Tasks), you must immediately think of the Composite pattern.
### Decorator Pattern

*File: `06-lld/03-design-patterns/02-structural/decorator-pattern.md`*

**What this covers:** The Decorator Pattern — lets you attach new behaviors to objects dynamically by placing them inside special wrapper objects that contain the behaviors.

**Key concepts:**
- The problem: adding features to an object via inheritance leads to class explosion. E.g., `Coffee`, `CoffeeWithMilk`, `CoffeeWithSugar`, `CoffeeWithMilkAndSugar`.
- The fix: create an interface (`Beverage` with `getCost()`). The base object (`Espresso`) implements it.
- The decorators: create wrapper classes (`Milk`, `Sugar`) that *also* implement `Beverage` AND accept a `Beverage` in their constructor.
- Chaining: `new Sugar(new Milk(new Espresso()))`. When `getCost()` is called on the outermost object, it delegates down the chain and adds its own cost.
- Flexibility: you can add or remove decorators at runtime. The client just sees a `Beverage`.

**Key takeaway:** This is a very common LLD question (e.g., "Design a Pizza pricing system with toppings" or "Design a text formatting tool"). Decorator avoids subclass explosion by wrapping objects recursively.
### Facade Pattern

*File: `06-lld/03-design-patterns/02-structural/facade-pattern.md`*

**What this covers:** The Facade Pattern — provides a simplified, higher-level interface to a complex subsystem of classes.

**Key concepts:**
- The problem: a client needs to perform a common task (e.g., "Checkout"), but doing so requires orchestrating 5 different complex subsystems (Inventory, Payment, Shipping, Loyalty, Email).
- The fix: create a `CheckoutFacade` class that exposes a single `placeOrder()` method.
- The facade handles the complexity: inside `placeOrder()`, it coordinates the 5 subsystems in the correct order, handling errors and passing data between them.
- Benefits: isolates clients from subsystem changes. If the Payment system upgrades from v1 to v2, only the Facade changes; the client UI stays the same.
- Difference from Adapter: Adapter changes an existing interface to match another interface. Facade creates a new, simpler interface for an entire complex system.

**Key takeaway:** Use Facade to hide "spaghetti" coordination logic from the client. In Spring/Java, @Service classes often act as facades orchestrating multiple Repositories.
### Flyweight Pattern

*File: `06-lld/03-design-patterns/02-structural/flyweight-pattern.md`*

**What this covers:** The Flyweight Pattern — minimizes memory usage by sharing as much data as possible with similar objects, instead of keeping all data in each object.

**Key concepts:**
- The problem: an app crashes due to OutOfMemory because it creates millions of small objects (e.g., 1 million Trees in a forest game, or 100,000 characters in a text editor).
- Intrinsic vs Extrinsic state: The key to Flyweight.
- Intrinsic state: state that is shared and unchanging (e.g., a Tree's 3D mesh and texture). This is stored *inside* the Flyweight object.
- Extrinsic state: state that is unique per instance (e.g., the `x, y` coordinates of a specific Tree). This is passed *into* the Flyweight methods by the client.
- Factory/Cache: a `FlyweightFactory` pools these objects. `getTreeType("Oak")` returns the shared "Oak" flyweight (only 1 exists in memory). The client maintains an array of `(x, y, oak_reference)`.

**Key takeaway:** Only use Flyweight when you have a memory problem caused by a massive number of similar objects. It is the textbook solution for "design a text editor" (characters) or "design a game environment" (trees/particles).
### Proxy Pattern

*File: `06-lld/03-design-patterns/02-structural/proxy-pattern.md`*

**What this covers:** The Proxy Pattern — provides a surrogate or placeholder for another object to control access to it.

**Key concepts:**
- The problem: you want to add access control, lazy loading, caching, or logging to an object, but you can't or shouldn't modify the object's code itself.
- The fix: create a Proxy class that implements the same interface as the real object.
- The intercept: the client talks to the Proxy. The Proxy performs its duty (e.g., checks permissions, checks the cache, or initializes the heavy object) and then delegates the work to the real object.
- Types of Proxies: Virtual Proxy (lazy loads heavy objects, e.g., high-res images), Protection Proxy (access control/auth), Cache Proxy (returns cached results), Remote Proxy (hides network calls, e.g., gRPC stubs).
- Difference from Decorator: Decorator *adds behavior* (like toppings). Proxy *controls access* to the object.

**Key takeaway:** Proxy is heavily used under the hood in modern frameworks (e.g., Spring AOP, Hibernate lazy-loading). If an interview asks you to add caching or auth to an existing service without modifying it, use a Proxy.
### Chain Of Responsibility Pattern

*File: `06-lld/03-design-patterns/03-behavioral/chain-of-responsibility.md`*

**What this covers:** The Chain of Responsibility Pattern — passes requests along a chain of handlers. Upon receiving a request, each handler decides either to process it or to pass it to the next handler in the chain.

**Key concepts:**
- The problem: hardcoding the routing logic for requests (e.g., a massive `if-else` block for determining if an auth token, cache, or DB should handle a request).
- The fix: create an abstract `Handler` class with a `setNext(Handler next)` method and a `handle(Request req)` method.
- The chain: link the handlers together (`authHandler.setNext(cacheHandler).setNext(dbHandler)`).
- Processing: the client sends the request to the *first* handler in the chain. If a handler can fully resolve it, it does; otherwise, it calls `next.handle(req)`.
- Use cases: Middleware in web frameworks (Express, Spring), Logger levels (DEBUG -> INFO -> ERROR), Event bubbling in UI frameworks.

**Key takeaway:** Use this pattern when you have multiple objects that can handle a request, and the specific handler shouldn't be known a priori by the sender.
### Command Pattern

*File: `06-lld/03-design-patterns/03-behavioral/command-pattern.md`*

**What this covers:** The Command Pattern — encapsulates a request as an object, allowing you to parameterize clients with different requests, queue or log requests, and support undoable operations.

**Key concepts:**
- The problem: tightly coupling a UI button to the business logic it triggers. Or, needing to implement "Undo/Redo" functionality.
- The fix: create a `Command` interface with an `execute()` method.
- Concrete Commands: `TurnOnLightCommand`, `TransferMoneyCommand`. These hold the parameters needed to execute the action and a reference to the receiver.
- Invoker: the object calling the command (e.g., a Button). It just calls `command.execute()`.
- Undo: add an `undo()` method to the interface. Maintain a `Stack<Command>` of executed commands. To undo, pop the stack and call `undo()`.
- Async/Queuing: because the request is now an object, it can be serialized, saved to a database, or put on a queue (e.g., Kafka) to be executed later.

**Key takeaway:** Command is the definitive answer to any LLD interview question involving "Undo/Redo" functionality (like a Text Editor) or job queuing.
### Interpreter Pattern

*File: `06-lld/03-design-patterns/03-behavioral/interpreter-pattern.md`*

**What this covers:** The Interpreter Pattern — defines a grammar for a simple language and an interpreter that evaluates sentences in that language, representing each grammar rule as a class.

**Key concepts:**
- The problem: you have a small domain language (arithmetic expressions, filter rules, boolean queries) and need to parse + evaluate it repeatedly.
- The fix: model the grammar as a **class hierarchy of expressions**. Each rule = one class with an `interpret(context)` method.
- **Terminal expressions** are the leaves (a number, a variable). **Non-terminal expressions** compose others (`Add`, `And`, `GreaterThan`).
- The parsed sentence becomes an **Abstract Syntax Tree (AST)**; interpreting = recursively calling `interpret()` down the tree.

**Key takeaway:** Interpreter is niche but shows up for rules engines, query/filter DSLs, calculators, and feature-flag conditions. For anything beyond a *simple, stable* grammar, use a real parser generator (ANTLR) instead — Interpreter doesn't scale to complex languages.
### Iterator Pattern

*File: `06-lld/03-design-patterns/03-behavioral/iterator-pattern.md`*

**What this covers:** The Iterator Pattern — provides a way to access the elements of an aggregate object sequentially without exposing its underlying representation (list, stack, tree, etc.).

**Key concepts:**
- The problem: you want to iterate over a custom collection (e.g., a Binary Search Tree or a Graph), but you don't want to expose its internal node structure to the client.
- The fix: extract the traversal behavior into a separate `Iterator` object.
- Interface: the Iterator has methods like `hasNext()` and `next()`.
- Decoupling: the client code uses the Iterator interface, completely unaware of whether it's traversing an array, a linked list, or a complex tree.
- Built-in: in Java, this is deeply integrated via the `Iterable` and `Iterator` interfaces, powering the enhanced `for-each` loop.

**Key takeaway:** You rarely need to write this from scratch in LLD interviews because standard libraries provide it. However, if asked to implement a custom data structure (like a specialized graph), providing an Iterator is the correct OOP approach.
### Mediator Pattern

*File: `06-lld/03-design-patterns/03-behavioral/mediator-pattern.md`*

**What this covers:** The Mediator Pattern — defines an object that encapsulates how a set of objects interact, keeping them from referring to each other explicitly.

**Key concepts:**
- The problem: "Spaghetti" dependencies. 10 UI components all need to update each other. If one changes, the other 9 must react. This results in $O(N^2)$ connections.
- The fix: introduce a Mediator (e.g., a `DialogController`).
- The hub: components only communicate with the Mediator ($O(N)$ connections). "Hey Mediator, I was clicked."
- The logic: the Mediator holds the complex coordination logic: "Since Component A was clicked, disable B, enable C, and clear D."
- Famous use case: Air Traffic Control. Planes don't talk directly to other planes to avoid crashing; they talk to the ATC tower (the Mediator).

**Key takeaway:** Mediator centralizes complex communication between peers. It's heavily used in complex UI screens to prevent components from becoming tightly coupled.
### Memento Pattern

*File: `06-lld/03-design-patterns/03-behavioral/memento-pattern.md`*

**What this covers:** The Memento Pattern — captures and externalizes an object's internal state so it can be restored later, without violating encapsulation.

**Key concepts:**
- The problem: implementing undo/redo needs to save/restore an object's state, but exposing its private fields to a history manager breaks encapsulation.
- Three roles: Originator (the object whose state we snapshot), Memento (an opaque state snapshot), Caretaker (holds the mementos, e.g., an undo stack — but can't read inside them).
- The Originator creates mementos (`save()`) and restores from them (`restore(m)`). The Caretaker only *stores* mementos; it never inspects their contents.
- The Memento is opaque to everyone except the Originator that made it — that's what preserves encapsulation.

**Key takeaway:** Memento is the correct answer for "add undo/redo" — text editors, drawing apps, game save points, transactional rollback. It differs from Command-based undo: Command re-computes the reverse action; Memento restores a saved snapshot.
### Observer Pattern

*File: `06-lld/03-design-patterns/03-behavioral/observer-pattern.md`*

**What this covers:** The Observer Pattern — defines a subscription mechanism to notify multiple objects about any events that happen to the object they're observing.

**Key concepts:**
- The problem: tightly coupling a state-holding object (Subject) to the objects that need to know about its changes (Observers). Hardcoding `chart.update()` inside `StockMarket`.
- The fix: the Subject maintains a list of Observers (which implement an `Observer` interface).
- Publish/Subscribe: Observers call `subject.subscribe(this)`. When the Subject changes, it loops through the list and calls `observer.update()` on all of them.
- Push vs Pull: The Subject can "push" the new data in the `update(data)` method, or it can just notify `update()`, and the Observer "pulls" the data via `subject.getState()`.
- Use cases: Model-View-Controller (MVC) where Views observe the Model; event handling systems (button clicks); real-time feeds.

**Key takeaway:** Observer is extremely common in LLD (e.g., "Design a Notification System" or "Design a Live Cricket Scoreboard"). It's the OOP foundation of event-driven architectures.
### State Pattern

*File: `06-lld/03-design-patterns/03-behavioral/state-pattern.md`*

**What this covers:** The State Pattern — lets an object alter its behavior when its internal state changes. It appears as if the object changed its class.

**Key concepts:**
- The problem: massive `switch(currentState)` statements inside every method of a class (e.g., `insertCoin()` behaves differently if state is `IDLE` vs `SOLD_OUT`).
- The fix: extract the state-specific behaviors into separate classes.
- Interface: create a `State` interface with methods for all possible actions (`insertCoin`, `dispense`).
- Concrete States: `IdleState`, `HasCoinState`. Each implements the actions valid for that state (and throws exceptions for invalid ones).
- Context: the main object (`VendingMachine`) holds a reference to the current `State` object and delegates all actions to it. The state objects themselves usually trigger the transition to the next state.

**Key takeaway:** State is the only acceptable answer for "Design a Vending Machine" or "Design an Elevator". It transforms a spaghetti mess of `if/else` state checks into clean, polymorphic classes.
### Strategy Pattern

*File: `06-lld/03-design-patterns/03-behavioral/strategy-pattern.md`*

**What this covers:** The Strategy Pattern — defines a family of algorithms, encapsulates each one, and makes them interchangeable at runtime.

**Key concepts:**
- The problem: a class does something specific in lots of different ways (e.g., calculating pricing for normal, premium, and VIP users), leading to massive `if-else` blocks that violate OCP.
- The fix: extract the algorithms into separate classes that all implement a common interface.
- Interface: `PricingStrategy` with `calculatePrice(cart)`.
- Concrete Strategies: `NormalPricing`, `VipPricing`.
- Context: the `Checkout` class holds a `PricingStrategy` reference. It calls `strategy.calculatePrice(cart)`. You can swap the strategy at runtime.
- Difference from State: State transitions are usually automatic and internal; Strategies are usually injected by the client and stay the same for the duration of the task.

**Key takeaway:** This is arguably the most important pattern in LLD. Any time an interview problem has "multiple ways to do X" (payment methods, sorting algorithms, pricing rules, rate-limiting algorithms), use Strategy.
### Template Method Pattern

*File: `06-lld/03-design-patterns/03-behavioral/template-method-pattern.md`*

**What this covers:** The Template Method Pattern — defines the skeleton of an algorithm in the superclass but lets subclasses override specific steps of the algorithm without changing its structure.

**Key concepts:**
- The problem: multiple classes have identical overall workflows, but the implementation of specific steps differs. (e.g., `DataMiner` for PDF vs CSV — open file, extract data, parse data, close file).
- The fix: create an abstract base class.
- The Template Method: a `final` method (e.g., `mineData()`) that dictates the exact sequence of steps.
- The Steps: some steps are implemented in the base class (shared code). Other steps are declared `abstract` (forcing subclasses to implement them).
- Hooks: optional steps with empty default implementations that subclasses *can* override if needed.
- Difference from Strategy: Strategy uses composition (delegates the whole algorithm). Template uses inheritance (base class controls the algorithm, subclass fills in the blanks).

**Key takeaway:** Template Method is the foundation of almost all object-oriented frameworks (like Spring or React lifecycle methods), where the framework dictates the flow, and you just fill in the specific step implementations.
### Visitor Pattern

*File: `06-lld/03-design-patterns/03-behavioral/visitor-pattern.md`*

**What this covers:** The Visitor Pattern — lets you separate algorithms from the objects on which they operate.

**Key concepts:**
- The problem: you have a complex tree/structure of different objects (e.g., AST nodes, or Document elements). You need to add a new operation (e.g., "Export to XML") that behaves differently for each node type. Modifying every node class violates OCP and pollutes the domain models.
- The fix: move the operation logic into a separate `Visitor` class.
- Double Dispatch: the core mechanism. The element calls `visitor.visit(this)`. The visitor executes the logic specific to that element type.
- Structure: `Element` interface has `accept(Visitor)`. `Visitor` interface has `visit(TypeA)`, `visit(TypeB)`.
- Pros: adding a new operation (e.g., "Export to JSON") just means creating a new `JsonVisitor` class. Zero changes to the element classes.
- Cons: adding a new *element type* requires updating every single Visitor interface and implementation.

**Key takeaway:** Visitor is the most complex pattern. Only use it when your object structure (the types of nodes) is very stable, but you frequently need to add new operations across that structure.

## Concurrency Patterns

### Concurrency Patterns

*File: `06-lld/04-concurrency/concurrency-patterns.md`*

**What this covers:** Core Concurrency Patterns and Utilities — the essential thread synchronization tools beyond basic `synchronized` blocks.

**Key concepts:**
- `ReadWriteLock`: allows multiple threads to read simultaneously, but only one thread to write (and blocks reads while writing). Crucial for cache implementations.
- `Semaphore`: restricts the number of concurrent threads accessing a resource (e.g., max 10 DB connections). It's a bouncer with $N$ permits. Essential for Rate Limiters.
- `CountDownLatch`: makes one or more threads wait until a set of operations being performed in other threads completes. Useful for scatter-gather (e.g., query 3 APIs in parallel, wait for all 3 to finish).
- `ConcurrentHashMap`: thread-safe map that uses bucket-level locking (lock stripping) instead of locking the whole map. Much faster than `Collections.synchronizedMap()`.
- Thread Pools (`ExecutorService`): never create threads manually (`new Thread()`). Use pools to reuse threads, bound resource usage, and handle task queuing.
- `AtomicInteger` / `AtomicLong`: lock-free, thread-safe primitives using CAS (Compare-And-Swap) hardware instructions. Perfect for counters.

**Key takeaway:** Learn to map LLD problems to the right concurrency tool: Cache = `ReadWriteLock` + `ConcurrentHashMap`. Rate Limiter = `Semaphore`. Scatter-Gather = `CountDownLatch`. Counter = `AtomicInteger`.
### Futures Async Patterns

*File: `06-lld/04-concurrency/futures-async-patterns.md`*

**What this covers:** Futures and Async Patterns — how to perform asynchronous, non-blocking programming using `CompletableFuture` (Java's version of Promises).

**Key concepts:**
- The problem: standard `Future.get()` blocks the thread until the result is ready, wasting thread pool resources.
- `CompletableFuture`: allows chaining operations non-blocking. When task A finishes, trigger task B on the same thread pool.
- Chaining (`thenApply`, `thenAccept`): transforms the result or consumes it without blocking.
- Composition (`thenCompose`): chaining dependent async calls (e.g., fetch user ID -> use ID to fetch profile). Like `flatMap`.
- Parallel execution (`thenCombine`): run task A and task B in parallel, then merge their results (e.g., fetch price from Amazon + price from eBay, then compare).
- Multi-task (`allOf`, `anyOf`): wait for an array of futures to all complete (scatter-gather), or return as soon as the fastest one finishes.
- Error handling (`exceptionally`): cleanly handle exceptions in the async chain without breaking the flow.

**Key takeaway:** In modern LLD, blocking I/O is a bottleneck. If an interview asks "How do you query 3 microservices and combine the result?", your answer should be `CompletableFuture.allOf()` running on an `ExecutorService`.
### Producer Consumer

*File: `06-lld/04-concurrency/producer-consumer.md`*

**What this covers:** The Producer-Consumer Pattern — the fundamental concurrency pattern where one or more threads produce data and put it in a shared queue, while one or more threads consume it.

**Key concepts:**
- The problem: Producers generating data faster than consumers can process it causes OutOfMemory (if queue is unbounded). If they share an `ArrayList` without locking, race conditions corrupt the data.
- The primitive fix: `wait()` and `notifyAll()`. Lock a shared object. If the queue is full, the producer calls `wait()`. If empty, the consumer calls `wait()`. They `notifyAll()` each other after adding/removing items.
- The modern fix: use a `BlockingQueue` (like `ArrayBlockingQueue`). It handles all the locking, waiting, and notifying internally.
- Bounded Buffers: always use a fixed-size queue (bounded buffer). This applies backpressure to producers if consumers are too slow, preventing the system from crashing.
- Poison Pill: how to gracefully shut down consumer threads. The producer sends a special "poison pill" object. When the consumer reads it, it terminates its loop.

**Key takeaway:** Never implement `wait()/notify()` manually in an interview unless explicitly asked. Say "I will use an `ArrayBlockingQueue` for thread-safe producer-consumer communication."
### Thread Safe Singleton

*File: `06-lld/04-concurrency/thread-safe-singleton.md`*

**What this covers:** Thread-Safe Singleton Pattern — how to ensure a class has exactly one instance, even when multiple threads try to create it simultaneously.

**Key concepts:**
- The problem: a basic lazy-loaded singleton (`if (instance == null) instance = new Singleton();`) causes race conditions. Two threads evaluating the `null` check simultaneously will create two instances.
- The bad fix: adding `synchronized` to the method signature. This kills performance because *every* call to `getInstance()` acquires a lock, even after the instance is created.
- The correct fix: Double-Checked Locking. Check for null, enter `synchronized(Singleton.class)` block, check for null again, then instantiate.
- The trap: you MUST declare the instance variable as `volatile`. Without `volatile`, compiler instruction reordering can cause another thread to see a partially constructed object.
- The best fix: Joshua Bloch's Enum Singleton (`public enum Singleton { INSTANCE; }`). The JVM guarantees thread safety and serialization safety automatically.

**Key takeaway:** If asked to write a Singleton in an interview, write the Double-Checked Locking version and explicitly explain why the `volatile` keyword is absolutely necessary.

## Glossary & UML Reference

### Glossary

*File: `06-lld/glossary.md`*

**What this covers:** A comprehensive glossary of OOP, SOLID, design-pattern (all 23 GoF), concurrency, and UML terminology used in LLD interviews.

**Key concepts:**
- OOP core: Encapsulation, Inheritance, Polymorphism, Abstraction, Composition vs. Inheritance.
- SOLID: SRP, OCP, LSP, ISP, DIP — one line each on what failure mode it prevents.
- Design patterns: all 23 GoF patterns grouped Creational / Structural / Behavioral, each with intent + a one-line trigger phrase.
- Concurrency: race condition, deadlock, mutex, semaphore, thread-safe singleton, producer-consumer.
- UML: class diagram relationship types (association, aggregation, composition, inheritance, realization).

**Key takeaway:** In an LLD interview, naming the pattern by its correct name *and* its trigger condition ("this is the Strategy pattern because the algorithm varies independently of the client") signals seniority the same way precise distributed-systems vocabulary does in HLD. Use this file to refine that vocabulary, not to learn the patterns for the first time — see 03-design-patterns/ for full treatments.
### UML Diagrams

*File: `06-lld/uml-diagrams.md`*

**What this covers:** Reference on UML notation for LLD interviews — the structural vs. behavioral diagram types, and detailed class-diagram relationship notation (association, aggregation, composition, inheritance, realization, dependency), plus a recap of the four OOP pillars.

**Key concepts:**
- Two diagram categories: Structural (static view — Class, Object, Component/Package diagrams) vs. Behavioral (dynamic view — Sequence, Activity, State Machine, Use Case diagrams).
- Class diagram relationships, weakest to strongest coupling: Dependency (dashed arrow, "uses a parameter/return type") → Association (solid line, "uses-a") → Aggregation (hollow diamond, "has-a" but parts can outlive the whole) → Composition (filled diamond, "owns-a," parts die with the whole) → Inheritance (hollow triangle, "is-a") → Realization (dashed line + hollow triangle, interface implementation).
- Visibility notation: `+` public, `-` private, `#` protected, `~` package.
- Sequence diagram notation: lifelines (vertical dashed lines), activation bars, synchronous calls (solid filled arrow), returns (dashed arrow), self-calls.
- Worked examples: BookMyShow class diagram (Show, Screen, Seat, Booking, User, Payment) and an ATM sequence diagram (User → ATM → Bank → Account).
- Interview guidance: always start LLD answers with a class diagram enumerating entities, then layer in relationships; know Composition vs. Aggregation distinctly (Order-OrderItems is composition, Employee-Department is aggregation).

**Key takeaway:** Class diagrams are the primary tool for LLD interviews — start every design by drawing entities and their relationships (favoring composition/aggregation over inheritance where possible) before writing any code.

---

# Interview Templates

### HLD Template

*File: `07-interview-templates/01-frameworks/01-hld-template.md`*

**What this covers:** The ultimate cheat sheet for passing a 45-minute High-Level Design (HLD) interview.

**Key concepts:**
- **Phase 1: Requirements (The Blueprint):** Before you build a house, ask how many people will live in it. Never start drawing boxes immediately.
- **Phase 2: Capacity Estimation (The Math):** Figure out how much traffic you will get. (Are you building a small driveway or a 10-lane highway?)
- **Phase 3: API Design (The Menu):** What exact commands can users send to your app? (Like a restaurant menu).
- **Phase 4: Database Schema (The Filing Cabinet):** Decide exactly how you will store the data.
- **Phase 5: Architecture (The Factory Floor):** Draw the boxes (Load Balancers, Servers, Databases).
- **Phase 6: Deep Dives (The Magnifying Glass):** Pick the hardest technical problem (like the "Celebrity Problem" on Twitter) and solve it.

**Key takeaway:** Pacing is everything. If you spend 20 minutes doing Math, you will run out of time and fail. Use this template to structure your 45 minutes perfectly.

### LLD Template

*File: `07-interview-templates/01-frameworks/02-lld-template.md`*

**What this covers:** The ultimate cheat sheet for passing a 45-minute Low-Level Design (LLD / Object-Oriented Design) interview.

**Key concepts:**
- **Phase 1: Requirements (The Rules):** Before writing code, figure out exactly who is using the app and what they can do.
- **Phase 2: Use Cases (The Story):** Write down exactly what happens when a user clicks a button, step-by-step.
- **Phase 3: Classes (The Nouns):** Look at your story. Every noun (Person, Car, Ticket) becomes a Class. Every verb (Park, Pay) becomes a Method.
- **Phase 4: Design Patterns (The Magic Tricks):** Use famous coding tricks (like Strategy or Factory) to make your code flexible, so it doesn't break when the boss asks for a new feature tomorrow.
- **Phase 5: Code (The Actual Work):** Write the Java/Python code for the single hardest part of the app.

**Key takeaway:** Junior developers immediately start writing `class ParkingLot { ... }` on the whiteboard. Senior developers spend 20 minutes agreeing on the blueprint with the interviewer before writing a single line of code.

### API Design Template

*File: `07-interview-templates/01-frameworks/03-api-design-template.md`*

**What this covers:** A simple, structured template for designing APIs during a system design interview.

**Key concepts:**
- **Protocol Choice (How they talk):** Use REST for public apps (like a Web Browser). Use gRPC for internal servers talking to each other (super fast). Use GraphQL for Mobile apps (saves battery).
- **Anatomy of an Endpoint:** `METHOD /v1/resource/identifier`. Always explain what goes in, and what comes out.
- **Pagination (Flipping pages):** "Offset" pagination is like saying "skip 100 pages." It is slow. "Cursor" pagination is like a bookmark. It is fast. Always use Cursor.
- **Versioning (Upgrades):** Put `/v1/` in your URLs so you don't break old apps when you release `/v2/`.
- **Idempotency (The Double-Charge Problem):** The magic trick to make sure a user doesn't get charged twice if their internet drops.

**Key takeaway:** The interviewer doesn't just want a list of URLs. They want to see how you handle large amounts of data (Pagination) and network failures (Idempotency).

### Monitoring SLO Template

*File: `07-interview-templates/01-frameworks/04-monitoring-slo-template.md`*

**What this covers:** A simple framework for answering interview questions about monitoring, reliability, and what to do when your servers crash.

**Key concepts:**
- **SLI, SLO, SLA:** SLI is the speedometer. SLO is the speed limit you set for yourself. SLA is the ticket the police gives you if you break it.
- **Error Budgets:** If your goal is 99.9% uptime, you are allowed to be down for 43 minutes a month. That 43 minutes is your "budget". Spend it wisely.
- **The Four Golden Signals (RED):** The 4 vital signs of your app (Latency, Traffic, Errors, Saturation).
- **Alerting:** Don't wake up an engineer at 3 AM because "CPU is high". Only wake them up if "Users can't buy things".

**Key takeaway:** Junior developers build things. Senior developers build things *and know how to fix them when they break at 3 AM*. Mentioning SLOs and Error Budgets proves you have real-world experience.

### Staff Signal Convention

*File: `07-interview-templates/01-frameworks/05-staff-signal-convention.md`*

**What this covers:** The `🎯 Staff signal` callout convention used throughout this repo's deep dives, and — more importantly — *what a staff signal actually is*, so you can produce them live in an interview instead of just recognizing them.

**Key concepts:**
- **The core idea:** At SDE-3/staff level, interviewers already assume you know the components. What they're listening for is the *one sentence per hard decision* that shows you understand the tradeoff, the failure mode, and when the default breaks. That sentence is the staff signal.

**Key takeaway:** Breadth is SDE-2. A quantified tradeoff on the one thing that's actually hard is SDE-3. Every deep dive in this repo ends with a `🎯 Staff signal:` line naming exactly that differentiator — study them as a pattern, not as trivia.

### Trade-offs Cheat Sheet

*File: `07-interview-templates/02-cheat-sheets/01-trade-offs-cheat-sheet.md`*

**What this covers:** The ultimate cheat sheet for comparing technologies. System design is entirely about justifying trade-offs.

**Key concepts:**
- **SQL vs NoSQL:** A rigid Filing Cabinet vs A messy but infinite pile of Bins.
- **Sync vs Async:** Waiting in line for a burger vs taking a buzzer and sitting down.
- **Horizontal vs Vertical Scaling:** Hiring a team of normal workers vs hiring one Superman.
- **Monolith vs Microservices:** A Swiss Army Knife vs A Professional Kitchen.

**Key takeaway:** There are no "perfect" solutions, only trade-offs. The fastest way to fail an interview is to say a technology is "always better." Use this sheet to explain *why* you chose X over Y.

### Capacity Estimation

*File: `07-interview-templates/02-cheat-sheets/02-capacity-estimation.md`*

**What this covers:** The definitive cheat sheet for "Back-of-the-Envelope" math in System Design interviews.

**Key concepts:**
- **Traffic (QPS):** How many people click a button every second? (Calculate Read QPS and Write QPS).
- **Storage:** How much hard drive space do you need for 5 years? (Write QPS * Size of Object * 5 Years).
- **Bandwidth:** How thick does the internet pipe need to be? (QPS * Size of Object).
- **Memory (Cache):** The 80/20 rule. Cache 20% of your data to serve 80% of your users.

**Key takeaway:** The interviewer doesn't care if your math is perfectly accurate. They just want to know if you are building a small shed (10 GB) or a massive skyscraper (10 PB), because that changes the entire architecture.

### Database Selection Tree

*File: `07-interview-templates/02-cheat-sheets/03-database-selection-tree.md`*

**What this covers:** A simple cheat sheet to help you choose the right database in an interview, without sounding like you're just guessing.

**Key concepts:**
- **Relational (SQL):** The accountant. Perfect for money, perfect for complex relationships, but bad at infinite scaling.
- **Document (MongoDB):** The flexible folder. Perfect when you don't know exactly what your data will look like (e.g., product catalogs).
- **Wide-Column (Cassandra):** The firehose. Perfect for writing millions of things per second (e.g., IoT sensors, chat messages).
- **Key-Value (Redis / DynamoDB):** The dictionary. Perfect when you just need to look up one specific thing instantly.
- **Search (Elasticsearch):** The Google for your app. Perfect for typing "Red Shoes" and getting fuzzy matches.

**Key takeaway:** Saying "I chose MongoDB because it's fast" is a red flag. Saying "I chose MongoDB because our data doesn't have a strict schema, and we don't need complex JOINs" is a green flag.

### Architecture by Scale

*File: `07-interview-templates/02-cheat-sheets/04-architecture-by-scale.md`*

**What this covers:** How to upgrade an app as it gets more popular.

**Key concepts:**
- **1,000 users (Startup):** Put everything on one computer. (It's fine, really).
- **10,000 users:** The database gets busy. Move it to its own computer.
- **100,000 users:** People are complaining it's slow. Add a Load Balancer, a Cache (Redis), and a CDN.
- **1,000,000 users:** The database is literally on fire from too many writes. Break the database into pieces (Sharding) and add a Message Queue (Kafka) so users aren't waiting for slow jobs.
- **10,000,000 users:** The code is too big for one team. Break the app into Microservices.

**Key takeaway:** Never start an interview by saying "I will build 50 microservices and use Kafka." If the interviewer asks for a small internal tool, you will fail for over-engineering. Always match the solution to the specific bottleneck.

### Concept to Problem Map

*File: `07-interview-templates/02-cheat-sheets/05-concept-to-problem-map.md`*

**What this covers:** A cheat sheet linking big fancy tech words to the actual interview questions where you need to use them.

**Key concepts:**
- If you want to practice **WebSockets**: Look at Chat Apps, or Live Dashboards.
- If you want to practice **Graph Databases**: Look at Social Network News Feeds (Friends of Friends).
- If you want to practice **Map coordinates (Geohashes)**: Look at Uber or Yelp.
- If you want to practice **Rate limiting**: Look at API Gateways or DDoS protection.

**Key takeaway:** If you feel weak on a concept like "Message Queues", find the problem that tests it (e.g., YouTube Video Processing) and study that.

### Interview Anti-Patterns

*File: `07-interview-templates/03-pitfalls-and-recovery/01-interview-anti-patterns.md`*

**What this covers:** The most common reasons smart people fail System Design interviews, and how to avoid them.

**Key concepts:**
- **Requirements:** Don't start drawing boxes immediately. Ask questions first.
- **Architecture:** Don't say "I'll use Kafka and Microservices" unless you can explain *why* you need them.
- **Deep Dives:** Don't just say "Eventual Consistency." Explain *what that means for the user* (e.g., "They might see a stale Like count for 2 seconds").
- **Communication:** Don't think silently for 3 minutes. The interviewer cannot read your mind.

**Key takeaway:** System Design interviews test your communication as much as your tech skills. Avoid these anti-patterns to show you are a Senior Engineer.

### Failure Recovery Playbook

*File: `07-interview-templates/03-pitfalls-and-recovery/02-failure-recovery-playbook.md`*

**What this covers:** A playbook for handling the inevitable interviewer question: *"What happens when this breaks?"*

**Key concepts:**
- **The Circuit Breaker:** If a server is crashing, stop sending it traffic so it has time to recover.
- **Thundering Herd:** When a cache expires, 10,000 users will try to hit the database at the exact same millisecond. (You must prevent this).
- **Graceful Degradation:** If the "Recommendation Engine" breaks on Amazon, don't crash the whole website. Just hide the recommendations and let people buy things.

**Key takeaway:** Never say "it just crashes." Senior engineers walk the interviewer through exactly how the system detects the failure, protects itself, and recovers without losing data.

### Prep Toolkit

*File: `07-interview-templates/04-practice-and-prep/01-prep-toolkit.md`*

**What this covers:** How to actually study for a System Design interview without losing your mind.

**Key concepts:**
- **The 4-Week Plan:** Don't cram. Week 1 is Basics. Week 2 is Advanced. Week 3 is Deep Dives. Week 4 is Practice.
- **Grading Yourself:** If you can't explain *why* you chose a database, give yourself a failing grade on that practice run.
- **Company Differences:** Amazon cares about "Leadership Principles" (Customer obsession). Meta cares about massive scale (Billions of users). Google cares about deep algorithms (How does the database *actually* work?).

**Key takeaway:** You cannot memorize System Design. You have to understand the "Why". Use this toolkit to structure your studying so you don't panic on interview day.

### Interview Question Bank

*File: `07-interview-templates/04-practice-and-prep/02-interview-question-bank.md`*

**What this covers:** The top 20 questions interviewers will interrupt you with to test if you actually understand the boxes you are drawing.

**Key concepts:**
- **Databases:** "Why not just use SQL for everything?"
- **Caching:** "What happens if the cache gets full?"
- **Queues:** "What happens if a background worker crashes halfway through a job?"
- **Architecture:** "What happens if the whole data center loses power?"

**Key takeaway:** You will be interrupted. Don't panic. Use this list to practice giving confident, 60-second answers.

### Mock Interview Problems

*File: `07-interview-templates/04-practice-and-prep/03-mock-interview-problems.md`*

**What this covers:** 5 practice problems to test yourself before the real interview.

**Key concepts:**
- **Constraints:** Never just design "Twitter". Design Twitter for 100 Million Users where the Feed must load in 200 milliseconds.
- **Time Boxing:** You must finish these in exactly 45 minutes.
- **The Goal:** Practice drawing boxes while talking out loud.

**Key takeaway:** Reading about System Design is like reading a book about riding a bike. You won't actually learn until you try it and fall off. Get a whiteboard (or a piece of paper) and practice these 5 problems.

### Worked Examples

*File: `07-interview-templates/04-practice-and-prep/04-worked-examples.md`*

**What this covers:** Real examples of what a "Good" interview sounds like. It shows the back-and-forth conversation between an Interviewer and a Candidate.

**Key concepts:**
- **The Setup:** See how a strong candidate asks questions *before* drawing anything.
- **Handling Pushback:** Notice how the candidate reacts when the interviewer says, "I don't like that idea." (Spoiler: They don't get defensive; they explain the trade-offs).
- **The Secret Weapon:** Read the `[ANNOTATION]` blocks. They explain *why* the candidate said what they said.

**Key takeaway:** System Design is a conversation, not a test. Read these transcripts to understand the rhythm and flow of a passing interview.

### SDE-3 Rapid Recall Bank

*File: `07-interview-templates/04-practice-and-prep/05-sde3-rapid-recall-bank.md`*

**What this covers:** A staff-level rapid-recall deck — the questions a senior interviewer fires *after* you've drawn the boxes, to test whether you understand the machinery. Unlike the beginner "gotcha" bank (02-interview-question-bank.md), every answer here quantifies a trade-off instead of just naming a component.

**Key concepts:**
- Cover the **A:** line. Say your answer out loud in ≤ 60 seconds. Then reveal and compare.
- The bar is not "correct" — it's "correct *and* quantified *and* names the failure mode."
- Drill the whole deck once a day for the 5 days before an on-site.

**Key takeaway:** SDE-3 signal = you name the number, the tradeoff, and when the default breaks. "Use a cache" is SDE-2. "Cache-aside with a 30s TTL + jitter, single-flight on miss, accept a stampede window ≤ one DB call per key per TTL" is SDE-3.

### 45-Minute Walkthrough

*File: `07-interview-templates/04-practice-and-prep/06-45-minute-walkthrough.md`*

**What this covers:** A minute-by-minute pacing rubric for a 45-minute system-design round. Interviewers fail SDE-3 candidates on *pacing and depth allocation* far more often than on missing knowledge — you run out of time in the requirements weeds and never reach the deep dive where the senior signal lives.

**Key concepts:**
- **0–5 Scope & requirements** — bound the problem, don't design yet.
- **5–10 Estimation** — one number that drives one architecture decision.
- **10–20 High-level design** — the boxes, the happy path, end to end.
- **20–35 Deep dive** — the 15 minutes that decide your level.
- **35–45 Bottlenecks, tradeoffs, wrap** — you volunteer the weaknesses.

**Key takeaway:** Budget deliberately. If you're still gathering requirements at minute 12, you will not pass. The deep dive is where E5 vs E6 is decided — protect it.

---

# Reference & Book Summaries

## Reference Docs

### Cloud Services Cheat Sheet

*File: `08-reference/cloud-services-cheat-sheet.md`*

**What this covers:** A Rosetta Stone for cloud computing. It maps generic architectural components to their specific AWS, GCP, and Azure service names.

**Key concepts:**
- Object Storage: AWS S3 == GCP Cloud Storage == Azure Blob Storage.
- Message Queues: AWS SQS == GCP Pub/Sub == Azure Service Bus.
- Managed Relational DBs: AWS RDS == GCP Cloud SQL == Azure SQL Database.
- Serverless Compute: AWS Lambda == GCP Cloud Functions == Azure Functions.

**Key takeaway:** In an interview, it's safer to use generic terms ("I will use a managed message queue") rather than vendor-specific ones. But if an interviewer specifically asks "How would you implement this on AWS?", this cheat sheet ensures you know the right managed service to name-drop.
### Decision Trees

*File: `08-reference/decision-trees.md`*

**What this covers:** Technology-selection decision trees for the moments in an interview where you must *choose* — SQL vs NoSQL, which consistency model, which cache strategy, push vs pull, sync vs async, and more. Each tree ends at a concrete choice with the one-line justification you'd say out loud.

**Key concepts:**
- These are *selection* trees (pick a technology/approach), not concept-recall maps — for topic recall see FLOWCHARTS.md.
- Every leaf names a default and the trigger that would change it — interviewers reward "I'd pick X, but switch to Y if Z."
- Read a leaf as a starting position, not dogma; state your assumption, then commit.

**Key takeaway:** In an interview, don't enumerate options — traverse the tree out loud to a decision, then justify. Indecision reads as junior; a defended default reads as senior.
### ML System Design

*File: `08-reference/ml-system-design.md`*

**What this covers:** An introduction to designing Machine Learning systems at scale, focusing on the differences between traditional software systems and ML pipelines.

**Key concepts:**
- Training Pipeline vs Inference Pipeline: Training is batch-heavy and done offline. Inference (serving predictions) is real-time and latency-sensitive.
- Data Drift / Concept Drift: Models degrade over time as the real world changes. You must monitor input distributions and set up automated retraining loops.
- Feature Store: A centralized database that stores cleaned, curated features so both training and inference pipelines use the exact same data logic (preventing training-serving skew).
- Evaluation: Offline metrics (Precision/Recall) vs Online metrics (A/B testing, click-through rates).

**Key takeaway:** Designing an ML system is 80% data engineering (pipelines, feature stores, monitoring) and 20% actual model training. Focus the design on data flow, not on the math of the neural network.
### Numbers to Know

*File: `08-reference/numbers-to-know.md`*

**What this covers:** The absolute minimum set of latency numbers and capacity metrics you must memorize for System Design interviews.

**Key concepts:**
- Latency Hierarchy: L1 cache (~0.5ns) → Mutex lock (~25ns) → Main memory (~100ns) → SSD read (~10,000ns or 10μs) → Network round trip (~150ms).
- The Golden Rule: Reading from disk is ~100x slower than reading from memory. A network call is ~1,000,000x slower than reading from memory.
- Availability 9s: 99% = 3.65 days of downtime/year; 99.9% ("three nines") = 8.76 hours/year; 99.99% ("four nines") = 52 minutes/year.

**Key takeaway:** You don't need exact numbers, just orders of magnitude. Knowing that a cross-country network round trip takes ~150ms instantly explains why CDNs are strictly necessary for global static asset delivery.
### System Design Glossary

*File: `08-reference/system-design-glossary.md`*

**What this covers:** A comprehensive glossary defining the most important vocabulary and terminology used in System Design interviews.

**Key concepts:**
- Data concepts: Sharding, Partitioning, Replication, Consistent Hashing.
- Consistency models: Strong Consistency, Eventual Consistency, Linearizability.
- Networking concepts: CDN, Load Balancer, API Gateway, Reverse Proxy.
- Concurrency concepts: Race condition, Deadlock, Semaphore, Mutex.

**Key takeaway:** Using the exact right terminology (e.g., "We will use Consistent Hashing to minimize cache invalidation during node failure") immediately signals seniority to the interviewer. Use this glossary to refine your technical vocabulary.

## Book Summary: Designing Data-Intensive Applications

### Designing Data-Intensive Applications (DDIA)

*File: `08-reference/book-summaries/ddia.md`*

**What this covers:** A chapter-by-chapter summary of *Designing Data-Intensive Applications (DDIA)* by Martin Kleppmann, widely considered the most important book for senior backend engineers.

**Key concepts:**
- Replication: Single-leader, Multi-leader, and Leaderless (Dynamo style). Problems with replication lag (read-after-write consistency).
- Partitioning: Key-range vs Hash partitioning. Handling hot spots. Rebalancing partitions safely.
- Transactions: ACID properties. Isolation levels (Read Uncommitted, Read Committed, Repeatable Read, Serializable). Race conditions like Write Skew.
- Consensus: Two-Phase Commit (2PC) vs Raft/Paxos. Linearizability.

**Key takeaway:** DDIA doesn't teach you how to pass an interview; it teaches you how databases actually work under the hood. Reading this summary provides the vocabulary (e.g., "SSTables", "Write-Ahead Log", "Linearizability") that interviewers expect from Staff-level candidates.

## Book Summary: Head First Java

### Appendix A: Additional Topics

*File: `08-reference/book-summaries/head-first-java/appendix-a-additional-topics.md`*

**What this covers:** Leftover topics that didn't fit into the main chapters, including bit manipulation, immutability, and access modifiers.

**Key concepts:**
- Bitwise Operators: Manipulating individual bits (`&`, `|`, `^`, `~`) and shifting bits (`<<`, `>>`, `>>>`).
- Immutability: Why `String` is immutable. It makes Strings thread-safe and allows the JVM to cache them efficiently (String Pool).
- Access Modifiers: `public` (anywhere), `protected` (same package + subclasses anywhere), `default` (same package only), `private` (same class only).
- Assertions: Using the `assert` keyword to test assumptions during development (they are ignored in production by default).

**Key takeaway:** Access modifiers are your primary tool for encapsulation. A solid grasp of when to use `protected` vs `default` (package-private) is a hallmark of an experienced Java developer.
### Chapter 0: How to Use This Book

*File: `08-reference/book-summaries/head-first-java/chapter-00-how-to-use-this-book.md`*

**What this covers:** The pedagogical philosophy behind the "Head First" series and how to get the most out of the book.

**Key concepts:**
- Metacognition: Thinking about thinking. Your brain needs to be tricked into believing the material is important to retain it.
- Visuals: The brain processes images and conversational tone better than dry, academic text.
- Redundancy: Repeating concepts in different ways helps solidify neural pathways.

**Key takeaway:** Don't just read passively. Do the exercises, look at the pictures, and try to explain the concepts out loud to trick your brain into storing the information long-term.
### Chapter 1: Breaking the Surface

*File: `08-reference/book-summaries/head-first-java/chapter-01-breaking-the-surface.md`*

**What this covers:** The absolute basics of Java, including the structure of a class, the main method, and how Java code is compiled and executed.

**Key concepts:**
- The JVM: Java code is compiled into bytecode (`.class` files), which is then interpreted by the Java Virtual Machine (JVM). This enables "Write Once, Run Anywhere".
- Code Structure: Source code goes in a `.java` file, which must contain a class matching the filename.
- The `main()` Method: The entry point of every Java application: `public static void main(String[] args)`.
- Basic Syntax: Loops (`while`, `for`), conditionals (`if`, `else`), and branching.

**Key takeaway:** Java is strictly object-oriented, but execution always starts in a static context (the `main` method). Your first job is usually to create an object inside `main` and hand control over to it.
### Chapter 2: Classes and Objects

*File: `08-reference/book-summaries/head-first-java/chapter-02-classes-and-objects.md`*

**What this covers:** An introduction to Object-Oriented Programming (OOP) and how classes act as blueprints for objects.

**Key concepts:**
- Classes vs Objects: A class is a blueprint; an object is a concrete instance of that class living in memory.
- State (Instance Variables): Data that represents what an object *knows* (e.g., `size`, `breed`, `name`).
- Behavior (Methods): Code that represents what an object *does* (e.g., `bark()`, `play()`).
- Instantiation: Using the `new` keyword (e.g., `Dog d = new Dog();`) to create an object on the heap.

**Key takeaway:** Stop thinking in terms of procedural steps and start thinking in terms of "Things" (Objects). A program is just a bunch of objects sending messages (method calls) to each other.
### Chapter 3: Primitives and References

*File: `08-reference/book-summaries/head-first-java/chapter-03-primitives-and-references.md`*

**What this covers:** How variables work in Java, specifically the critical difference between primitive types and object references.

**Key concepts:**
- Primitives: Hold fundamental values (`int`, `boolean`, `byte`, `double`). They have a fixed size.
- Object References: Variables that hold a *pointer* to an object on the heap, not the object itself. Like a remote control for a TV.
- Array Basics: Arrays are always objects, even if they are declared to hold primitive values. `int[] nums = new int[5];` creates one array object holding 5 primitives.
- Garbage Collection: When an object on the heap no longer has any active references pointing to it, the JVM will automatically destroy it to reclaim memory.

**Key takeaway:** Java passes everything by value. But when passing an object, the "value" being passed is the bits representing the reference (the remote control), meaning the receiving method can modify the original object.
### Chapter 4: How Objects Behave

*File: `08-reference/book-summaries/head-first-java/chapter-04-how-objects-behave.md`*

**What this covers:** How objects behave in Java through methods, parameters, return types, and encapsulation.

**Key concepts:**
- Encapsulation: Hide the data (instance variables) by making them `private`. Expose the behavior (methods) by making them `public`.
- Pass-by-Value: Java passes all arguments by value. For primitives, it passes a copy of the value. For objects, it passes a copy of the *reference* (the remote control).
- Setters and Getters: Use these to enforce constraints. A setter can check if `height > 0` before modifying the private instance variable.

**Key takeaway:** The core of object-oriented design is encapsulation. By hiding the internal state, you prevent external classes from putting your object into an invalid state.
### Chapter 5: Extra-Strength Methods

*File: `08-reference/book-summaries/head-first-java/chapter-05-extra-strength-methods.md`*

**What this covers:** Building a complete program (a simple game called "Sink a Dot Com") and the standard Java loop constructs.

**Key concepts:**
- The `for` loop: Traditional `for(int i=0; i<10; i++)` vs the enhanced for-each loop `for(String name : names)`.
- Casting: Converting from a larger primitive type to a smaller one (e.g., `(int) 3.14`) results in truncation and requires an explicit cast to tell the compiler you accept the risk.
- Program Flow: Converting pseudo-code into real Java code.

**Key takeaway:** This chapter transitions you from isolated syntax rules into writing a functioning program with loops, input parsing, and state tracking. The enhanced `for` loop is almost always preferred over the traditional `for` loop when iterating over collections.
### Chapter 6: Using the Java Library

*File: `08-reference/book-summaries/head-first-java/chapter-06-using-the-java-library.md`*

**What this covers:** Exploring the Java Standard Library (API), particularly focusing on `ArrayList` and reading JavaDocs.

**Key concepts:**
- Arrays vs ArrayList: Standard arrays have a fixed size upon creation. `ArrayList` grows and shrinks dynamically.
- Packages: Java classes are grouped into packages (like folders). You must `import java.util.ArrayList;` to use it, unless it's in `java.lang` (which is imported automatically).
- Standard Library: You don't need to write everything from scratch. Java comes with thousands of pre-built classes.

**Key takeaway:** Never use a standard array when you don't know the exact size of the collection in advance. `ArrayList` is the most commonly used data structure in Java programming.
### Chapter 7: Inheritance and Polymorphism

*File: `08-reference/book-summaries/head-first-java/chapter-07-inheritance-and-polymorphism.md`*

**What this covers:** Core OOP concepts: Inheritance (the IS-A relationship) and Polymorphism.

**Key concepts:**
- Inheritance: Using the `extends` keyword, a subclass inherits all public and protected instance variables and methods from its superclass.
- Overriding: A subclass can redefine a method inherited from a superclass to provide its own specific behavior.
- Polymorphism: You can declare a reference variable of a superclass type, but assign it a subclass object. `Animal a = new Dog();`.
- IS-A vs HAS-A: Use inheritance only when the subclass "IS-A" type of the superclass (e.g., A Dog IS-A Animal). If a class "HAS-A" something else, use composition (e.g., a Bathroom HAS-A Tub).

**Key takeaway:** Polymorphism allows you to write flexible, extensible code. You can write a method that takes an `Animal` array and calls `.makeNoise()` on all of them, and it works perfectly whether the array contains `Dog`, `Cat`, or `Lion` objects.
### Chapter 8: Interfaces and Abstract Classes

*File: `08-reference/book-summaries/head-first-java/chapter-08-interfaces-and-abstract-classes.md`*

**What this covers:** The power of abstraction in Java, using `abstract` classes and `interface`s.

**Key concepts:**
- Abstract Classes: Used when a class is so general that it shouldn't be instantiated (e.g., `new Animal()` doesn't make sense). Use the `abstract` keyword.
- Abstract Methods: Methods with no body. If a class has an abstract method, the class *must* be abstract. The first concrete subclass *must* implement all abstract methods.
- Interfaces: A 100% pure abstract class. They define a contract (what a class can do) without specifying how. Use the `implements` keyword.
- Multiple Inheritance: Java doesn't allow a class to `extend` multiple classes, but it *does* allow a class to `implements` multiple interfaces.

**Key takeaway:** Use an abstract class for a shared base implementation among closely related classes (IS-A). Use an interface to define a role that any class can play, regardless of where it is in the inheritance tree (e.g., `Pet`, `Serializable`).
### Chapter 9: Life and Death of an Object

*File: `08-reference/book-summaries/head-first-java/chapter-09-life-and-death-of-an-object.md`*

**What this covers:** The lifecycle of an object: Constructors, the Heap, the Stack, and Garbage Collection.

**Key concepts:**
- The Stack vs The Heap: Method invocations and local variables live on the Stack. Objects (and their instance variables) live on the Heap.
- Constructors: The code that runs when you say `new`. It has the same name as the class and no return type. If you don't write one, the compiler provides a no-arg default constructor.
- `super()`: The first line of *every* constructor is a call to `super()`, either implicitly added by the compiler or explicitly written by you. This ensures the entire inheritance tree is built properly.

**Key takeaway:** Understanding the Stack and the Heap is crucial for debugging `NullPointerException`s and memory leaks. Objects live on the Heap until their reference count drops to zero, at which point the Garbage Collector destroys them.
### Chapter 10: Numbers Matter

*File: `08-reference/book-summaries/head-first-java/chapter-10-numbers-matter.md`*

**What this covers:** The `static` keyword, the `Math` library, and Wrapper classes (Autoboxing).

**Key concepts:**
- `static` methods: Methods that don't depend on an instance variable value. You call them using the Class name, not a reference variable (e.g., `Math.abs(-5)`). A static method cannot access a non-static (instance) variable.
- `static` variables: A variable shared by all instances of a class. There is only one copy of it, regardless of how many objects are instantiated.
- `final`: A `final` variable's value cannot be changed. A `final` method cannot be overridden. A `final` class cannot be extended. A `static final` variable is a constant.
- Wrapper Classes: Turning a primitive into an object (e.g., `int` -> `Integer`) so it can be used in an `ArrayList`. Autoboxing does this automatically in modern Java.

**Key takeaway:** Think of `static` as "belongs to the class, not the object." If a behavior shouldn't change depending on which object is executing it, it should probably be static.
### Chapter 11: Risky Behavior

*File: `08-reference/book-summaries/head-first-java/chapter-11-risky-behavior.md`*

**What this covers:** Exception handling in Java. How to write code that deals with unpredictable runtime errors (like network failures or bad file paths).

**Key concepts:**
- Try/Catch blocks: Put risky code inside `try { }`. Handle the failure in `catch(Exception e) { }`. Put cleanup code in `finally { }` (which runs no matter what).
- Checked vs Unchecked Exceptions: `RuntimeException`s (like `NullPointerException` or `IndexOutOfBoundsException`) are unchecked; you aren't forced to handle them. All other exceptions (like `IOException`) are checked; the compiler forces you to handle them or declare them.
- The `throws` keyword: If a method doesn't want to handle an exception, it can "duck" by adding `throws ExceptionType` to its declaration, forcing the caller to handle it.

**Key takeaway:** Exceptions are for exceptional circumstances, not for regular control flow. If a file might be missing, catch the exception. If a user enters a negative number, use an `if` statement, not an exception.
### Chapter 12: A Very Graphic Story

*File: `08-reference/book-summaries/head-first-java/chapter-12-a-very-graphic-story.md`*

**What this covers:** Introduction to Graphical User Interfaces (GUIs) in Java using the Swing library, focusing on event handling.

**Key concepts:**
- Swing Basics: `JFrame` is the window. `JButton` is a widget that goes in the window.
- Event Handling: How do you make a button *do* something? You use the Observer Pattern.
- Interfaces as Callbacks: Your class implements `ActionListener`, which forces you to write an `actionPerformed(ActionEvent e)` method. You then register your class with the button: `button.addActionListener(this);`.

**Key takeaway:** While writing desktop Swing apps is rare today, the concept of Event Listeners is fundamental. The exact same pattern (registering a callback function to handle an asynchronous event) is used in Javascript DOM manipulation, Android development, and Node.js.
### Chapter 13: Work on Your Swing

*File: `08-reference/book-summaries/head-first-java/chapter-13-work-on-your-swing.md`*

**What this covers:** Layout Managers in Java Swing and advanced GUI components.

**Key concepts:**
- Layout Managers: You don't usually specify absolute X, Y coordinates for buttons. You give them to a Layout Manager which decides where they go based on the window size.
- BorderLayout: Divides the window into 5 regions (North, South, East, West, Center). Default for a JFrame.
- FlowLayout: Places components in a row, wrapping to the next line when space runs out. Default for a JPanel.
- BoxLayout: Stacks components vertically.

**Key takeaway:** Layout managers are early implementations of responsive design. They allow a UI to gracefully adapt when a user resizes the window, much like Flexbox or CSS Grid do for the web today.
### Chapter 14: Saving Objects

*File: `08-reference/book-summaries/head-first-java/chapter-14-saving-objects.md`*

**What this covers:** How to persist object state beyond the lifetime of the JVM using Serialization and File I/O.

**Key concepts:**
- Serialization: Flattens an object into a stream of bytes so it can be saved to a file or sent over a network. The class must implement `java.io.Serializable`.
- `transient`: If an instance variable cannot or should not be saved (like a network connection or a password), mark it as `transient`. It will be skipped during serialization.
- Deserialization: Reading the bytes back into a live object. The JVM must have access to the class's bytecode, or it will throw an exception.
- File I/O: Using `FileWriter` and `BufferedWriter` to write plain human-readable text instead of serialized object bytes.

**Key takeaway:** Serialization is a powerful tool for saving the exact state of an object graph, but it's fragile. If the class definition changes before deserialization, things break. Today, JSON/XML mapping is usually preferred over native Java serialization.
### Chapter 15: Make a Connection

*File: `08-reference/book-summaries/head-first-java/chapter-15-make-a-connection.md`*

**What this covers:** Network programming with Sockets and Multithreading in Java.

**Key concepts:**
- Sockets: A `Socket` is an object representing a network connection between two machines. A `ServerSocket` listens for incoming client requests.
- Multithreading: You need threads so your program can do two things at once (like reading from the network while updating the GUI).
- `Runnable` and `Thread`: You implement the `Runnable` interface (defining a `run()` method), pass it to a new `Thread` object, and call `start()`.
- Synchronization: If two threads modify the same object at the same time, you get a race condition. Use the `synchronized` keyword to lock the object so only one thread accesses it at a time.

**Key takeaway:** Multithreading introduces extreme complexity (race conditions, deadlocks). Always lock the minimum amount of code necessary when dealing with shared mutable state.
### Chapter 16: Data Structures

*File: `08-reference/book-summaries/head-first-java/chapter-16-data-structures.md`*

**What this covers:** The Java Collections Framework and how to use Generics for type safety.

**Key concepts:**
- Collections: `List` (ordered, allows duplicates, e.g., `ArrayList`), `Set` (unordered, no duplicates, e.g., `HashSet`), and `Map` (key-value pairs, e.g., `HashMap`).
- Sorting: To sort a collection, the objects must implement `Comparable` (defining `compareTo()`). Or you can pass a custom `Comparator` to the `sort()` method.
- Generics: Using `<T>` (e.g., `ArrayList<String>`) allows the compiler to enforce type safety. You can't accidentally put an `Integer` into an `ArrayList<String>`.
- Polymorphism with Generics: `ArrayList<Animal>` is *not* a supertype of `ArrayList<Dog>`. To write a method that takes a list of any Animal subtype, use wildcards: `void takeAnimals(ArrayList<? extends Animal> list)`.

**Key takeaway:** The Collections framework is the bread and butter of Java programming. Understand when to use a List (order matters), a Set (uniqueness matters), or a Map (key lookups matter).
### Chapter 17: Release Your Code

*File: `08-reference/book-summaries/head-first-java/chapter-17-release-your-code.md`*

**What this covers:** How to package, deploy, and execute Java applications.

**Key concepts:**
- Source vs Class: Keep your `.java` files in one directory (e.g., `src`) and your compiled `.class` files in another (e.g., `classes`).
- Packages: Prevent naming collisions. The package structure must exactly match the directory structure (e.g., `package com.headfirstjava;` must live in `com/headfirstjava/`).
- JAR Files: Java ARchive. It's a zip file containing your entire directory structure of `.class` files.
- Manifest: A file inside the JAR (`META-INF/MANIFEST.MF`) that tells the JVM which class holds the `main()` method, allowing the JAR to be executable.

**Key takeaway:** A Java program isn't just one file. It's a structured hierarchy of packages. JAR files are the standard way to bundle these hierarchies so a user can just double-click or run `java -jar App.jar`.
### Chapter 18: Distributed Computing

*File: `08-reference/book-summaries/head-first-java/chapter-18-distributed-computing.md`*

**What this covers:** Java Remote Method Invocation (RMI), servlets, and enterprise computing concepts.

**Key concepts:**
- RMI: Allows an object on one machine to call methods on an object running on another machine as if it were local.
- Stubs and Skeletons: The client uses a "Stub" (a proxy) that looks like the real object. The server uses a "Skeleton" to unpack the network call and invoke the real object.
- Servlets: Java code running inside a web server (like Tomcat) that handles HTTP requests and responses.
- Jini (Apache River): An older technology for network service discovery.

**Key takeaway:** While RMI is mostly legacy today (replaced by REST APIs, gRPC, and microservices), the underlying concept—using a local proxy (stub) to abstract away the complexity of a network call—remains a fundamental architectural pattern.
### Head First Java

*File: `08-reference/book-summaries/head-first-java/head-first-java.md`*

**What this covers:** A massive single-file compilation of all 18 chapters of notes from *Head First Java*.

**Key concepts:**
- Object-Oriented Programming: Encapsulation, Inheritance, Polymorphism, Abstract classes, and Interfaces.
- Memory Management: The Heap (for objects) and the Stack (for method invocations and local variables). The Garbage Collector.
- Data Structures & Generics: `ArrayList`, `HashSet`, `TreeSet`, `HashMap`. Using `<T>` to ensure type safety at compile time.
- Concurrency: Threads, `Runnable`, and the `synchronized` keyword to prevent race conditions.

**Key takeaway:** This file contains the entire book's summary. It's excellent for a `Cmd+F` search when you need to recall a specific Java quirk or syntax rule (like the difference between `==` and `.equals()`).

## Book Summary: Head First Object-Oriented Analysis & Design

### Chapter 0: Introduction

*File: `08-reference/book-summaries/head-first-ooand/chapter-00-introduction.md`*

**What this covers:** The introduction to the *Head First OOA&D* learning approach.

**Key concepts:**
- Metacognition: Tricking your brain into thinking this material matters through visual learning, exercises, and redundancy.
- OOA&D defined: Object-Oriented Analysis (figuring out what the system needs to do) and Object-Oriented Design (figuring out how the code should be structured).

**Key takeaway:** Like all Head First books, you need to actively engage with the material to retain the complex architectural concepts introduced later.
### Chapter 1: Well-Designed Apps Rock

*File: `08-reference/book-summaries/head-first-ooand/chapter-01-well-designed-apps-rock.md`*

**What this covers:** The foundational rules of building great software, using a "Guitar inventory" application as an example.

**Key concepts:**
- Step 1: Make it work. The app must do what the customer asked for.
- Step 2: Apply OOP principles. Use encapsulation. (e.g., Hide the `serialNumber` string, expose a `getSerialNumber()` method).
- Step 3: Strive for maintainable design. Use delegation. (e.g., Don't put guitar search logic in the main app; put it in an `Inventory` class).
- Encapsulation: Identify what varies (e.g., Guitar properties) and encapsulate it away from what stays the same (e.g., the Inventory search mechanism).

**Key takeaway:** Code that works today but breaks when requirements change tomorrow is bad code. The goal of OOA&D is to build software that is easy to modify.
### Chapter 2: Gathering Requirements

*File: `08-reference/book-summaries/head-first-ooand/chapter-02-gathering-requirements.md`*

**What this covers:** How to extract the real requirements from a customer, using a "Dog Door" application as an example.

**Key concepts:**
- Requirements: What the system *must* do to be successful.
- Use Cases: A specific scenario describing what a system does to achieve a particular goal for a particular user (Actor).
- Main Success Scenario (Happy Path): The steps where everything goes right.
- Alternate Paths: What happens when things go wrong (e.g., the dog gets stuck outside).

**Key takeaway:** Customers rarely know exactly what they want. It is your job as an analyst to write down use cases, find the edge cases (alternate paths), and ensure the system accounts for them *before* you write the code.
### Chapter 3: Requirements Change

*File: `08-reference/book-summaries/head-first-ooand/chapter-03-requirements-change.md`*

**What this covers:** Dealing with the inevitable truth of software development: requirements will always change mid-project.

**Key concepts:**
- Updating Use Cases: When the customer asks for a new feature (like a bark recognizer for the Dog Door), you must first update the use case, not the code.
- Code Flexibility: If your code is tightly coupled, a requirement change will require a massive rewrite.
- Delegation: Separating responsibilities. Instead of the Dog Door handling the bark recognition, delegate that to a `BarkRecognizer` object.

**Key takeaway:** The single constant in software engineering is change. By encapsulating what varies and delegating responsibilities, you create a system that can absorb new requirements without breaking existing functionality.
### Chapter 4: Analysis

*File: `08-reference/book-summaries/head-first-ooand/chapter-04-analysis.md`*

**What this covers:** Textual analysis. How to translate the use cases written in plain English into concrete classes and methods.

**Key concepts:**
- Nouns and Verbs: Look at the use case. The nouns (Dog, Door, Remote) are your candidate classes. The verbs (Barks, Opens, Closes) are your candidate methods.
- Class Diagrams: Visualizing the classes, their methods, and how they interact before writing any code.
- Identifying the Real Problem: Sometimes the use case is hiding the real problem. Textual analysis helps uncover missing objects (like a `Bark` object to represent the sound the dog makes).

**Key takeaway:** Don't start coding from a blank slate. Write the use case, highlight the nouns, highlight the verbs, and you instantly have your first draft of a class diagram.
### Chapter 5: Good Design = Flexible Software

*File: `08-reference/book-summaries/head-first-ooand/chapter-05-good-design-flexible-software.md`*

**What this covers:** Abstracting away concrete implementations to create truly flexible, plug-and-play software architectures.

**Key concepts:**
- Programming to an Interface: Do not tie your code to a concrete class (e.g., `DogDoor`). Tie it to an interface (e.g., `Door`). This allows you to swap in a `CatDoor` later without changing the rest of the code.
- Encapsulation (Revisited): It's not just about hiding variables. It's about hiding *behavior* that might change.
- Cohesion: Ensuring a class does only one thing. A `DogDoor` should not be responsible for parsing audio files of barks.

**Key takeaway:** Flexibility in software comes from loose coupling. If Class A doesn't know exactly what Class B is (because it only knows Class B's Interface), Class A won't break when Class B changes.
### Chapter 6: Solving Really Big Problems

*File: `08-reference/book-summaries/head-first-ooand/chapter-06-solving-really-big-problems.md`*

**What this covers:** Scaling up OOA&D techniques to handle large, complex systems (like a massive strategy game framework).

**Key concepts:**
- Domain Analysis: When the problem is too big, break it down into smaller, manageable domains.
- The Big Picture: You can't write use cases for a massive system all at once. You must first establish the overarching architecture and the main subsystems.
- Feature Lists: Before writing detailed use cases, write a high-level list of features the system must support.

**Key takeaway:** Big problems are just collections of small problems. Don't let the scale overwhelm you. Break the system down into modules, and apply the OOA&D process (Requirements -> Analysis -> Design) to each module independently.
### Chapter 7: Architecture

*File: `08-reference/book-summaries/head-first-ooand/chapter-07-architecture.md`*

**What this covers:** Defining the overarching structure of an application—its Architecture.

**Key concepts:**
- What is Architecture?: It's the design of the highest-level components and how they interact. It's the decisions that are hardest to change later.
- The Three Qs of Architecture: 1. Is it part of the core essence of the system? 2. What the fuck does it mean? (Is it confusing/unclear?) 3. How the heck do we do it? (Is it technically difficult?)
- Reducing Risk: Address the architectural components that answer "yes" to the three Qs first. Build prototypes to prove the hardest parts are solvable.

**Key takeaway:** Don't start coding the easy, boring parts first. Identify the highest-risk, most architecturally significant use cases and prove they work before committing to the design.
### Chapter 8: Design Principles

*File: `08-reference/book-summaries/head-first-ooand/chapter-08-design-principles.md`*

**What this covers:** The core principles of Object-Oriented Design (the SOLID principles, though not explicitly named as such here).

**Key concepts:**
- OCP (Open-Closed Principle): Classes should be open for extension but closed for modification. You should be able to add new behavior without changing existing code.
- DRY (Don't Repeat Yourself): Abstract out common code into a single place to avoid bugs when updating logic.
- SRP (Single Responsibility Principle): Every object in your system should have a single responsibility, and all its services should be narrowly aligned with that responsibility.
- Liskov Substitution Principle: Subtypes must be substitutable for their base types.

**Key takeaway:** These principles are the guardrails of good design. If you find yourself copying and pasting code, or changing a class every time a new feature is added, you are violating these principles.
### Chapter 9: Iterating and Testing

*File: `08-reference/book-summaries/head-first-ooand/chapter-09-iterating-and-testing.md`*

**What this covers:** The iterative development process and Test-Driven Development (TDD).

**Key concepts:**
- Iterative Development: Build a tiny, working piece of the system, test it, and show it to the customer. Then build the next piece. Do not write all the code at once.
- Feature Driven Development: Pick one feature, analyze it, design it, code it, test it.
- Testing: Tests prove your code does what the customer asked.
- Test-Driven Development (TDD): Write the test *before* you write the code. It forces you to think about how the object will be used before you get bogged down in implementation details.

**Key takeaway:** Big Bang integration (writing all the code and praying it works together at the end) always fails. Iteration and TDD ensure that your software is always in a working, verifiable state.
### Chapter 10: The OOAD Lifecycle

*File: `08-reference/book-summaries/head-first-ooand/chapter-10-the-ooad-lifecycle.md`*

**What this covers:** Bringing all the concepts together into a unified Object-Oriented Analysis and Design Lifecycle.

**Key concepts:**
- The OOA&D Lifecycle: 1. Feature List (What does it do?) 2. Use Cases (How do people use it?) 3. Break Up the Problem (Modules/Architecture) 4. Requirements (Specifics) 5. Domain Analysis (Nouns/Verbs) 6. Design (Applying Principles) 7. Implementation (Coding/Testing).
- It's not a waterfall: You don't have to perfectly finish step 2 before starting step 3. The process is cyclical.

**Key takeaway:** This chapter serves as a roadmap for any new software project. You don't just start typing. You talk to the customer, write use cases, find the objects, design the architecture, apply SOLID principles, and iterate.
### Head First OOA&D

*File: `08-reference/book-summaries/head-first-ooand/head-first-ooand.md`*

**What this covers:** A massive single-file compilation of all chapters from *Head First Object-Oriented Analysis & Design*.

**Key concepts:**
- The Three Steps to Great Software: 1. Make sure your software does what the customer wants. 2. Apply basic OOP principles to add flexibility. 3. Strive for a maintainable, reusable design.
- Delegation: Often better than inheritance. Instead of inheriting behavior, an object delegates the work to a specialized helper object (HAS-A vs IS-A).
- Cohesion: A class should do one thing and do it well. High cohesion means methods and variables are tightly related to a single purpose.

**Key takeaway:** This file contains the entire book's summary. It's a great reference for core software engineering principles. The overarching theme is that requirements *will* change, and your job is to design a system where a change in one place doesn't break everything else.
