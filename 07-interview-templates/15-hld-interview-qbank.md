---
module: 07-interview-templates
topic: HLD Interview Question Bank
tags: [hld, interview, system-design, distributed-systems, amazon-sde2]
---
# HLD Interview Question Bank

> Questions an Amazon SDE-2 interviewer actually interrupts with during a high-level design session.
> Format: question → answer you should give out loud in ≤ 60 seconds.

---

## 1. Scoping & Requirements

**Q1: What happens if you skip clarifying requirements and just start designing?**
You risk designing the wrong system. The most common failure mode: designing for social-media scale when the problem is an internal tool for 500 employees, or designing for strong consistency when eventual is fine. The first 5 minutes of clarification change which database, which queue, and whether you need sharding at all. Always anchor on: number of users, read/write ratio, latency SLO, and consistency requirement.

**Q2: Interviewer says "design Twitter." What's your first question?**
"What's the scope? Are we designing the tweet posting flow, the feed generation, or both?" Then: "How many DAU — 100M or 500M?" and "Is this read-heavy or write-heavy?" Twitter is 10:1 read to write — that immediately tells you the feed is the bottleneck, not the write path. Wrong assumption here leads to designing the wrong system for 40 minutes.

**Q3: How do you handle it when the interviewer says "assume infinite scale"?**
Still anchor on order of magnitude — "infinite" means different architectures than "10M users." Ask: "Should I design for 100M DAU and 1M RPS, then call out what changes at 10× from there?" This shows you understand that architecture decisions are scale-sensitive. Design for a concrete scale, then explicitly state your scale-out path.

**Q4: Interviewer asks: "What are you explicitly leaving out of scope?"**
Good scoping: "I'm leaving out authentication, analytics, admin tools, and mobile-specific optimizations. I'll focus on the core write path and read path for the main feature. If we have time, I'll cover the notification fan-out." This shows judgment. Trying to design everything in 45 minutes produces a shallow design; saying what you're not designing shows you know what matters.

---

## 2. Capacity Estimation

**Q5: How do you estimate QPS for a system with 10M DAU?**
Assume each DAU generates 10 requests/day on average (adjust for write-heavy apps). 10M × 10 = 100M requests/day. 100M / 86,400 = ~1,200 RPS average. Peak is 2–3× average = ~3,000 RPS. For write-heavy apps (messaging), multiply by 20–50 requests/day. Always state your assumptions explicitly — the number matters less than showing you can derive it.

**Q6: Interviewer asks: "How much storage do you need for 1B posts over 5 years?"**
1 post ≈ 500 bytes (text + metadata). 1B posts × 500 bytes = 500 GB. Over 5 years: if users post at 1M posts/day, 5 years = 1.8B posts → ~900 GB text. Images: 30% of posts have images, average 200 KB = 1.8B × 0.3 × 200 KB = 108 TB. Total: ~110 TB. With 3× replication = ~330 TB. Fits on ~50 commodity storage nodes. State your assumptions on post size and image ratio.

**Q7: How much memory does a cache need to serve 80% of traffic if keys follow a Pareto distribution?**
Pareto / 80-20 rule: 20% of keys receive 80% of traffic. If you have 10M unique keys × 1 KB average value = 10 GB total data. Cache 20% of keys = 2 GB. This serves ~80% of reads. For a 95% hit rate, cache the top 30–40% of keys. Rule of thumb: start with cache = 20% of working set, monitor hit rate, grow until marginal hit rate gain drops below 1% per GB added.

---

## 3. Database Selection

**Q8: You're designing a ride-sharing app. What databases do you use and why?**
PostgreSQL for trips and payments — need ACID (a trip must commit atomically with payment charge). Redis for driver locations — geospatial queries (`GEOADD`, `GEORADIUS`), updated every 3 seconds per driver, must be fast. Kafka for location events — buffer the high-frequency write stream, fan out to multiple consumers (ETA calculation, analytics). S3 for trip history exports, receipts. Each database chosen for a specific access pattern.

**Q9: When would you shard a database and what are the risks?**
Shard when: single-node write throughput is the bottleneck (> 10K writes/sec for Postgres), or dataset exceeds what fits on the largest single machine. Risks: cross-shard queries are expensive (no JOINs across shards), rebalancing is painful (moving data without downtime requires careful orchestration), hot shards (if shard key is skewed), and transactional guarantees across shards require distributed transactions (2PC or Saga). Avoid sharding until you're forced to — it adds irreversible complexity.

**Q10: Interviewer asks: "Why not just use MongoDB for everything?"**
MongoDB is good for document-shaped data with flexible schemas and when access is by document ID. It's a poor fit when: you need JOINs across collections (becomes expensive `$lookup`), you need multi-document ACID transactions (available but slow), or your access patterns require complex aggregations. MongoDB doesn't eliminate the need to think about data modeling — you still need to design around your access patterns; you just use embedded documents instead of joins.

**Q11: How do you handle schema migrations on a live production database?**
Never lock the table. Strategy: expand-and-contract. Phase 1 (expand): add new column as nullable, deploy code that writes to both old and new columns. Phase 2 (migrate): backfill old rows in batches (1K rows at a time with `LIMIT + WHERE id > last_id`). Phase 3 (contract): make column non-nullable, deploy code that reads only new column, drop old column. Takes days/weeks for large tables but zero downtime.

---

## 4. Caching

**Q12: Interviewer asks: "How does adding a cache change your failure modes?"**
New failure modes introduced: cache stampede on cold start (all reads miss simultaneously), stale reads if invalidation fails, cache poisoning (storing corrupt data), split-brain between cache and DB after a failed write, and the cache becoming a single point of failure if it's not replicated. Always design your system to degrade gracefully when the cache is down — read from DB, just slower. Cache is an optimization, not a dependency.

**Q13: How do you design a cache for a URL shortener with 1B stored URLs?**
Most URLs are rarely accessed (long tail). Cache the hot set: 1% of URLs get 90% of traffic. 1B × 1% = 10M URLs × ~200 bytes (short URL + long URL) = 2 GB. Fits in a single Redis instance with room to spare. Cache-aside pattern: on redirect request, check Redis; miss → read from Cassandra/DynamoDB → write to Redis with TTL 24h. Eviction: LRU. No need for complex invalidation — TTL handles it since URLs don't change.

**Q14: Your cache is hitting 100% memory. How do you respond?**
First: check if eviction policy is correct — `allkeys-lru` evicts least recently used regardless of TTL. If it is, you're caching too much. Analyze: what are the top cache key patterns by memory? Are you caching large objects unnecessarily? Options: reduce TTL to allow more eviction, filter out cold keys (only cache if access frequency > N), shard the cache across multiple Redis nodes (Redis Cluster), or increase memory and accept higher cost. Audit before scaling — often a few key patterns account for 80% of memory.

**Q15: When does a cache hurt you more than it helps?**
When writes significantly outnumber reads — every write must invalidate or update the cache, net overhead exceeds benefit. When the working set is larger than RAM — cache hit rate is low, you're just adding a cache miss tax to every request. When you need strong consistency — cache adds a stale-read window. When the cache becomes a dependency for correctness (you're using the cache as a primary store) — this is dangerous, cache is not durable by default.

---

## 5. Message Queues & Async

**Q16: Interviewer asks: "Why not just call the downstream service synchronously?"**
Synchronous calls couple availability — if the downstream is down, you fail too. They couple latency — if downstream is slow, your P99 degrades. For operations that don't need an immediate response (email, notification, analytics event, async processing), a queue decouples the two: your service succeeds as soon as it enqueues, regardless of downstream state. The trade-off: you lose the ability to return a result from the downstream operation synchronously.

**Q17: How do you ensure a message is not processed more than once?**
At-least-once delivery (Kafka, SQS) guarantees the message arrives but may duplicate. Make consumers idempotent: assign each message a unique `event_id`, store processed IDs in a DB or Redis set with TTL. On receipt: `IF NOT EXISTS processed:{event_id} THEN process AND INSERT processed:{event_id}`. This is an exactly-once semantic built on top of at-least-once delivery. The idempotency store needs to survive consumer crashes — use durable storage, not in-memory.

**Q18: What is the difference between Kafka and SQS, and when do you choose which?**
Kafka: log-based, messages are retained for days, multiple consumer groups can independently replay, ordering guaranteed within a partition, throughput in millions/sec. SQS: queue-based, messages deleted after acknowledgment, one consumer group, no ordering (FIFO queue exists but limits throughput), simpler ops. Choose Kafka when: you need replay, audit log, fan-out to multiple consumers, or high throughput. Choose SQS when: simple task dispatch, you're AWS-native, and ops simplicity matters more than replay.

**Q19: Your Kafka consumer group is falling behind — lag is growing. What do you do?**
Step 1: check if consumers are CPU-bound or I/O-bound. CPU-bound: add more partitions (allows more parallel consumers), increase consumer instances. I/O-bound (DB writes): batch the DB writes (write 100 messages per transaction instead of 1). Step 2: check if processing logic has a slow step — add a secondary queue for the slow step (async fan-out). Step 3: check for poison pill messages — one message taking 30 seconds blocks the partition. Add timeouts per message and a DLQ for timed-out messages.

**Q20: How do you implement a delay queue (process message after 30 minutes)?**
SQS: set `DelaySeconds` on the message (up to 15 minutes); for longer delays, use a step function or scheduled re-enqueue. Kafka: no native delay support — write to a "delay" topic, a timer service reads the topic and re-publishes to the main topic when the delay elapses. Redis: `ZADD delay_queue <process_at_timestamp> <message>`, a polling worker does `ZRANGEBYSCORE delay_queue 0 <now>` every second to find ready messages.

---

## 6. Scalability

**Q21: How do you scale a stateless service vs a stateful service?**
Stateless: add instances behind a load balancer — any instance handles any request. Scale horizontally with zero coordination. Stateful: instances have local state (in-memory cache, open WebSocket connections). Scaling requires: sticky sessions (route user to same instance, breaks failover), or externalizing state (move to Redis/DB, make service stateless again). Always prefer stateless services — they are trivially scalable and resilient.

**Q22: What is a hot spot and how do you detect and fix it?**
A hot spot is one shard/partition/node receiving disproportionate traffic. Detection: monitor per-shard CPU, request rate, and latency — one shard at 95% while others are at 10% is a hot spot. Causes: non-uniform key distribution (celebrity user whose shard gets all traffic), a popular product always on the same shard. Fix: write sharding (append a random suffix to the key, reads aggregate across shards), or split the hot key into many keys (fan out writes, merge on read).

**Q23: How do you scale a write-heavy system when the DB is the bottleneck?**
Step 1: batch writes — buffer in-memory and flush to DB every 100ms (reduces per-write overhead). Step 2: async writes — write to a queue, flush to DB via workers. Step 3: add write replicas (some DBs support write scaling via sharding). Step 4: switch to a write-optimized store (Cassandra, DynamoDB) with LSM tree — sequential disk writes instead of random. Step 5: reduce write surface — do you need to write all this data?

**Q24: How do you design for horizontal scalability from the start?**
Keep services stateless (no local cache that must be consistent across instances). Use external storage for all shared state (Redis, DB). Use consistent hashing for routing if you need affinity. Design DB schemas around horizontal partitioning — avoid queries that need data from multiple shards. Build in async from the start — tight synchronous chains don't scale. Every shared resource (DB, cache, queue) should have a clear scaling story.

---

## 7. Consistency & Replication

**Q25: Your design uses eventual consistency. Interviewer asks: "What happens if a user sees stale data?"**
Be specific about what stale means in your system. "A user likes a post and immediately refreshes — they might not see their like for up to 2 seconds. This is acceptable for social interactions. However, for payments or seat bookings, we use strong consistency — a confirmed payment must be immediately visible to prevent double-booking." Always state the business impact of staleness, not just the technical definition.

**Q26: How do you implement read-your-own-writes with a replicated DB?**
Option 1: route all reads from a user to the primary for 1 minute after a write (track `last_write_time` in a cookie or session store). Option 2: conditional routing — route to replica unless the replica's replication lag is less than the time since the user's last write (requires monitoring lag per replica). Option 3: write to primary, cache the written value in Redis for 30 seconds, serve from cache on read.

**Q27: What is the difference between strong consistency and linearizability?**
Strong consistency is a broad term — "reads see the most recent write" — but doesn't specify the exact model. Linearizability is the strongest concrete consistency model: every operation appears to take effect atomically at a single point in real time, and all clients see operations in the same global order. Spanner achieves linearizability using TrueTime (GPS + atomic clocks). It's the gold standard but expensive — requires coordination across nodes for every operation.

**Q28: Two services both write to the same row in the DB. How do you prevent lost updates?**
Optimistic concurrency: read the row with a `version` or `updated_at` field, write with `WHERE version = <read_version>`, check affected rows = 1. If 0, someone else updated — retry. Pessimistic concurrency: `SELECT FOR UPDATE` — holds a row lock until the transaction commits. Use optimistic when conflicts are rare (user profile); use pessimistic when conflicts are frequent and you can't afford retries (inventory decrement, seat reservation).

---

## 8. Reliability & Failure Handling

**Q29: How do you design a payment service to never double-charge?**
Idempotency key: client generates a UUID for each payment attempt and sends it in the request header. Server stores `(idempotency_key, response)` in DB before processing. On duplicate request: return stored response without re-charging. The key must be stored atomically with the charge result in a single transaction. Expire keys after 24 hours. This converts at-least-once network delivery into exactly-once business semantics.

**Q30: What is a circuit breaker and when should it open?**
Tracks failure rate to a downstream dependency. When failures exceed a threshold (e.g., 50% of requests fail in a 10-second window), the circuit opens — subsequent calls fail immediately without attempting the downstream call. After a timeout (e.g., 30 seconds), circuit goes half-open: one test request is allowed through. If it succeeds, circuit closes; if it fails, stays open. This prevents a slow downstream from exhausting your thread pool and cascading the failure.

**Q31: How do you handle a database that's unavailable for 30 seconds?**
Retry with exponential backoff + jitter for transient errors. For longer outages: if reads, serve from cache if available, else return a degraded response (empty list with a flag indicating data may be incomplete). If writes, buffer in a local queue (or SQS), process when DB recovers. Distinguish between: query error (bad SQL — don't retry), connection error (network — retry), and timeout (ambiguous — retry idempotently). Alert on error rate, not individual errors.

**Q32: Interviewer asks: "What's your SLA and how do you meet it?"**
State the target: "P99 < 200ms, 99.9% availability." Then map to requirements: 99.9% availability = 8.7h downtime/year → multi-AZ deployment, no single points of failure. P99 < 200ms: cache hot reads (DB at P99 ~20ms, cache at P99 ~1ms), timeout all downstream calls at 150ms to leave headroom, async everything that doesn't need to block the response. Set budgets per component: 50ms for cache, 80ms for DB, 50ms buffer for overhead.

---

## 9. APIs & Communication

**Q33: How do you design an API for a mobile client on a slow 3G network?**
Minimize round trips — batch endpoints (fetch user + feed + notifications in one call). Compress responses (gzip). Pagination with cursor, not offset — stable under real-time inserts. Return only fields the client needs (field masks or GraphQL). Use HTTP/2 for multiplexing. Provide a diff endpoint — `GET /feed?since={last_cursor}` returns only new items, not the full feed. Cache aggressively with `Cache-Control: max-age=60`.

**Q34: When do you use GraphQL vs REST?**
GraphQL: client-driven field selection (mobile needs fewer fields than web), multiple resource types in one request, rapid iteration on client without backend changes. REST: resource-oriented CRUD, public APIs (GraphQL introspection leaks schema), caching at CDN level (GraphQL POST requests are not cached), simpler tooling. GraphQL is not "better than REST" — it solves the over-fetching and under-fetching problem at the cost of caching, security, and operational complexity.

**Q35: How do you handle API rate limiting at 10M API keys?**
Centralized Redis with `INCR ratelimit:{api_key}:{window}` — correct but 10M keys × many windows = large Redis footprint. Token bucket stored as `(tokens, last_refill_ts)` in Redis hash per key — computes exactly. For highest throughput: local in-process counter that syncs to Redis every 100ms (allows brief overages, saves 99% of Redis calls). Return `429 Too Many Requests` with `Retry-After`, `X-RateLimit-Remaining`, `X-RateLimit-Reset` headers.

**Q36: How do you version an API used by 1000 external customers?**
URL versioning (`/v2/`) — explicit, easy to test, visible in logs. Maintain v1 and v2 simultaneously for at least 6 months post-deprecation notice. Track which customers use which version (API key → version mapping). Deprecation path: announce deprecation, monitor usage, set sunset date, send email to customers still on v1. Never break existing endpoints — additive changes (new optional fields) are non-breaking, removal or type changes require a new version.

---

## 10. Real-Time & Streaming

**Q37: How do you push real-time notifications to 50M users?**
Not all 50M are online simultaneously. Assume 5% concurrent = 2.5M active connections. At 50K connections per server, need 50 WebSocket servers. Publish notification to Kafka topic. Each WebSocket server consumes from Kafka and pushes to its connected clients. A routing service maps `user_id → server_id` (stored in Redis). For offline users: store notification in DB, deliver on next open. Push via APNs/FCM for mobile (no persistent connection needed).

**Q38: How does a live sports score update reach 10M viewers simultaneously?**
Fanout is the challenge. Architecture: score service writes to Kafka → fanout workers read and push to a pub/sub system (Redis Pub/Sub or a dedicated websocket gateway). Clients connect to WebSocket gateways (~50K connections/server → 200 servers for 10M). Gateway subscribes to relevant match topics and pushes to connected clients. For burst events (goal scored), CDN edge push (SSE from CDN edge node) reduces origin server load.

**Q39: What is the difference between SSE and WebSockets for a live feed?**
SSE: server-to-client only, unidirectional, HTTP/1.1 compatible, auto-reconnect built in, works through HTTP proxies. WebSocket: bidirectional, persistent TCP connection, requires upgrade handshake, can be blocked by some proxies. For a read-only live feed (scores, stock prices, social feed updates), SSE is simpler — no need for bidirectional. WebSocket is necessary when the client also sends messages (chat, collaborative editing, multiplayer games).

---

## 11. Search & Indexing

**Q40: How do you design search for an e-commerce platform with 100M products?**
Don't use SQL `LIKE` — full table scan. Use Elasticsearch. Write path: product DB (Postgres) → Debezium CDC → Kafka → Elasticsearch indexer. Read path: search query → Elasticsearch (`bool` query with `match` on title/description + `term` filters on category/brand/price range) → ranked results with BM25. Scale: 100M docs × ~5KB/doc = 500 GB index — fits on a 3-shard Elasticsearch cluster with 2 replicas. Index mapping: `title` as `text` (analyzed), `price` as `float` (range filter), `category_id` as `keyword` (exact match).

**Q41: A user searches for "iphone" but the product is listed as "iPhone." How do you handle it?**
Case normalization at index time — Elasticsearch's standard analyzer lowercases all tokens. So "iPhone" is indexed as "iphone", and query "iphone" matches. For typos ("iphon"): fuzzy matching (`fuzziness: AUTO` in ES match query). For synonyms ("cell phone" → "mobile phone"): synonym filter in the index analyzer. For prefix autocomplete: `edge_ngram` tokenizer at index time, `match_phrase_prefix` at query time.

**Q42: How do you keep search results fresh when product data changes?**
Change Data Capture (CDC): Debezium reads Postgres WAL (write-ahead log), publishes change events to Kafka. A consumer reads from Kafka and updates the Elasticsearch index. Lag: typically 1–5 seconds. For price changes that must be near-real-time (flash sale): write directly to Elasticsearch from the service that updates price, bypassing the CDC lag. Full reindex: run weekly to correct any drift, using an alias swap (index to `products_v2`, swap alias when ready).

---

## 12. Security in HLD

**Q43: How do you secure an API that handles user financial data?**
Transport: TLS everywhere, HSTS header, certificate pinning for mobile. AuthN: OAuth 2.0 + PKCE, short-lived JWTs (15 min) + refresh tokens. AuthZ: RBAC or ABAC per endpoint. Data at rest: encrypt sensitive fields (AES-256) or use DB-level encryption. Audit log: every data access logged with user, timestamp, IP, action. Rate limiting per user and per IP. Input validation at the API boundary. Secrets in Secrets Manager, not env vars.

**Q44: What is a DDoS attack and how do you protect against it at the system design level?**
Volumetric DDoS: flood of traffic to exhaust bandwidth. Protection: CDN/Anycast absorbs volumetric attacks at the edge. Application DDoS (Layer 7): flood of valid-looking HTTP requests. Protection: rate limiting per IP/user, WAF rules to block known patterns, CAPTCHAs, bot detection. Design for graceful degradation: shedload (reject low-priority requests), serve cached responses, disable non-critical features under attack. AWS Shield + CloudFront is a common stack.

---

## 13. Trade-off Questions (Amazon Favorite)

**Q45: Interviewer says: "Why did you choose eventual consistency here? What could go wrong?"**
Name the concrete risk: "A user adds an item to cart on server A, reads cart from server B before replication — they don't see the item. They might think the add failed and add again, resulting in a duplicate." Then state why you accepted it: "Cart adds are low-stakes and self-healing within 1 second. The alternative — synchronous replication — adds 50ms to every cart read, which hurts conversion. The business accepts 1-second staleness."

**Q46: Interviewer asks: "Could you solve this without Kafka?"**
Yes — describe the simpler alternative and its tradeoffs. "We could call the downstream services synchronously, but that couples our availability to theirs. Or we could use a DB table as a queue (transactional outbox pattern) — write events to an `outbox` table in the same DB transaction as the business data, a poller reads and delivers. Simpler ops than Kafka, but lower throughput and no fan-out. At our expected volume of 1K events/sec, the outbox pattern is actually sufficient."

**Q47: Interviewer asks: "What would break first at 10× your current design's scale?"**
Walk through each component: "At 10× (10K RPS), the cache handles it fine — Redis does 100K ops/sec. The DB primary becomes the bottleneck at this write volume — we'd add read replicas to handle the read side, and if writes exceed 10K/sec, we'd shard by user_id. The single Kafka cluster handles 10K events/sec easily. The WebSocket gateway would need 2× servers. So DB writes is the first thing that breaks."

**Q48: Interviewer asks: "Why not use a microservice for X?"**
"Because X has no independent scaling requirement from Y, they share a transaction boundary (they must commit together or not at all), and splitting them would add a network hop and distributed transaction complexity for no benefit. Microservices are for team-scale and deployment independence — both of which are irrelevant here. I'd keep them in the same service until I have a concrete reason to split."

---

---

## 14. CDN & Networking

**Q49: When does a CDN help and when does it not?**
CDN helps when: content is static or semi-static (images, JS, CSS, product pages), globally distributed users (CDN edge is geographically close), and traffic is read-heavy. CDN doesn't help when: content is highly personalized (can't cache per-user responses), data changes faster than CDN TTL (live prices, chat messages), or requests require authentication that varies per call. Rule: cacheable + globally read = CDN; dynamic + user-specific = bypass CDN.

**Q50: What is the difference between a Layer 4 and Layer 7 load balancer?**
Layer 4 (TCP): routes based on IP + port, doesn't inspect payload, very fast, used for raw TCP/UDP traffic. Layer 7 (HTTP): inspects headers, URL, cookies — can route `/api` to one backend and `/static` to another, terminate SSL, add headers, do health checks by URL path, support sticky sessions via cookie. Use L4 for non-HTTP workloads or when you need maximum throughput. Use L7 (ALB, Nginx) for HTTP microservices — path routing, header-based routing, WebSocket support.

**Q51: How does DNS-based load balancing work and what are its limitations?**
Authoritative DNS returns different IPs for the same domain (round-robin or geo-based). Client caches the IP for the TTL. Limitation: clients ignore short TTLs and cache longer, so DNS changes are slow to propagate (can take minutes to hours in practice). Failover via DNS is not instant — clients stuck on old IP until TTL expires. Use DNS for coarse geo-routing (US traffic → US cluster); use load balancers for fine-grained routing within a region.

**Q52: What is TCP connection reuse and why does it matter for high-throughput services?**
Opening a TCP connection takes a 3-way handshake (~1 RTT) plus TLS handshake (~1–2 RTT) = 100–300ms overhead per new connection. HTTP/1.1 keep-alive and HTTP/2 multiplexing reuse existing connections. At 10K RPS, creating a new connection per request adds 1M+ handshakes/sec — impossible. Solution: connection pooling to upstream services, HTTP/2 to downstream clients, persistent connections in gRPC. Always measure connection overhead when debugging high-latency services.

**Q53: How do you reduce latency for a globally distributed system?**
Geo-routing: route users to the nearest region (Route53 latency routing). Regional data isolation: replicate read data to each region, write to user's home region. CDN for static assets. Edge caching for semi-static API responses. Pre-warm TCP connections (connection pool always has open connections). Async pre-fetching (fetch the next page before the user requests it). Measure: `curl -w "%{time_connect} %{time_starttransfer} %{time_total}"` to separate DNS/TLS/TTFB.

---

## 15. Observability & Monitoring

**Q54: What are the four golden signals and what does each tell you?**
Latency: how long requests take — P50 (typical), P99 (tail), P999 (worst). Distinguish success latency from error latency. Traffic: how much demand — RPS, events/sec. Saturation: how full is your system — CPU%, memory%, queue depth, DB connection pool utilization. Errors: rate of failed requests — 5xx%, timeout rate, DLQ depth. These four cover the full health of a service. Add: downstream dependency error rate as a fifth signal for microservices.

**Q55: How do you debug a latency spike that only affects 1% of requests?**
P99 spike but P50 is normal = tail latency problem. Steps: (1) add distributed tracing (Jaeger/X-Ray) — find which span in the call chain is slow for the slow requests. (2) Correlate with infrastructure: GC pause logs, DB slow query log, CPU steal time on the host. (3) Check if the slow 1% maps to a specific shard, AZ, or instance type. Common causes: GC pause, lock contention, a slow DB query that only triggers on certain data, or a downstream service with its own P99 problem.

**Q56: How do you set up alerting that doesn't page you constantly?**
Alert on symptoms, not causes. Page on: error rate > 1% for 5 minutes, P99 latency > SLO for 5 minutes, availability < 99.9%. Don't page on: CPU > 80% (unless it correlates with user impact), individual pod restart (Kubernetes handles it), disk usage < 85% (not urgent). Alert fatigue kills on-call effectiveness. Use `burn rate` alerting (SLO-based): alert when you're burning through your error budget too fast, not when a single threshold is crossed.

**Q57: What is distributed tracing and how does it work?**
Each request gets a `trace_id` at entry. Each service adds a `span` with `(trace_id, span_id, parent_span_id, start_time, end_time, tags)`. Spans are sent asynchronously to a collector (Jaeger, Zipkin, X-Ray). Trace view: a Gantt chart of all spans in a request — shows exactly which service took how long. Critical for debugging microservice latency: pinpoints the slow hop without guessing. Implementation: inject `trace_id` into request headers, extract on receipt, pass downstream.

**Q58: A deploy just went out and error rate jumped from 0.1% to 5%. What do you do first?**
Rollback immediately — don't investigate first. Rollback takes 2 minutes; investigation takes 20. Error rate at 5% is burning your SLO. After rollback and confirmation error rate returned to 0.1%: investigate in staging. Check: what changed in the deploy (diff), which error type spiked (NullPointerError? DB connection refused?), what percentage of endpoints are affected (one endpoint = feature-specific bug; all endpoints = config or infra issue). Root cause analysis after service is healthy.

---

## 16. Storage Patterns

**Q59: What is the outbox pattern and when do you use it?**
Ensures a DB write and a downstream event are always consistent — no event lost on crash. Write business data + outbox row in a single DB transaction. A background poller reads the outbox table and publishes to Kafka/SQS, then marks rows as sent. If the app crashes after the DB commit but before Kafka publish, the poller retries on restart. Use when: Kafka publish must be guaranteed after a successful DB write. Alternative: Kafka transactional producer (more complex, ties you to Kafka).

**Q60: What is CQRS and when is it worth the complexity?**
Command Query Responsibility Segregation: separate the write model (commands that mutate state) from the read model (queries that return data). Write model: normalized DB optimized for writes and consistency. Read model: denormalized, pre-computed views optimized for the query shape (e.g., a materialized feed table). Sync via events. Worth it when: read and write have very different scaling needs (writes: low volume, strong consistency; reads: high volume, eventual consistency, different schema shape). Overkill for simple CRUD — adds event sync complexity and two models to maintain.

**Q61: What is event sourcing and how does it differ from storing current state?**
Event sourcing: store every state change as an immutable event (`OrderCreated`, `ItemAdded`, `OrderShipped`). Current state = replay of all events. Traditional: store only the latest state. Benefits of event sourcing: full audit log, temporal queries ("what was the state on date X"), easy to project new read models by replaying events, event-driven integration is natural. Costs: query current state requires replay (mitigated by snapshots), event schema evolution is hard, eventual consistency on read side. Use for: audit-critical domains (finance, compliance), not for simple CRUD.

**Q62: How do you handle large binary files (images, video) in your system design?**
Never store blobs in a relational DB — they bloat backups, slow vacuums, and can't be served efficiently. Pattern: client requests presigned URL from your API (`PUT` presigned URL to S3), client uploads directly to S3 (bypasses your server), your server receives a callback (S3 event → Lambda/SQS) confirming upload complete, then stores the S3 key in your DB. CDN in front of S3 for reads. This keeps your API servers out of the data path — they never touch the bytes.

---

## 17. Multi-Region & Disaster Recovery

**Q63: What is the difference between active-active and active-passive multi-region?**
Active-active: both regions serve traffic and accept writes. Requires conflict resolution for concurrent writes to the same record. Higher availability and lower latency globally. Complex: need a replication strategy (CRDTs, last-write-wins, or application merge). Active-passive: one region serves all traffic (primary), the other is a hot standby (passive) with replica DB. Simpler — no write conflicts. Failover requires DNS flip + promoting replica. Recovery time objective (RTO): active-active = seconds, active-passive = minutes.

**Q64: How do you design for RPO = 0 (zero data loss on region failure)?**
RPO = 0 means every committed write must be durable in two regions before the client gets success. Implementation: synchronous cross-region replication — write must be acknowledged by both primary and secondary region replica before returning. This adds cross-region latency (~50–150ms) to every write. Most systems can't afford this — instead, accept RPO of seconds (async replication) and design compensating logic for the rare loss. Reserve RPO = 0 for financial transactions.

**Q65: How do you test your disaster recovery plan?**
Gameday: regularly simulate failures in production (kill a region's traffic, fail over DB, restart services). Chaos engineering: random failure injection (Chaos Monkey, AWS Fault Injection Simulator). Measure actual RTO/RPO against targets — don't assume they match the design. Common finding: failover works but DNS propagation takes longer than expected, or the standby DB is lagging further than assumed. DR plans that are never tested always fail when needed. Test at least quarterly.

**Q66: What is a bulkhead and how does it prevent cascading failure?**
Named after ship compartments that contain flooding to one section. In software: isolate resources per dependency so one slow dependency doesn't starve all threads. Implementation: separate thread pools per downstream service. If the payment service is slow, its thread pool fills up and rejects requests — but the order service thread pool is unaffected. Without bulkheads: one slow downstream fills the shared thread pool and all traffic to all services stalls. Also applies to DB connections: separate pool for read path vs write path.

---

## 18. Cost & Efficiency

**Q67: Interviewer asks: "Your design works. Now how do you make it cheaper?"**
First: profile before optimizing. Common cost drivers: over-provisioned compute (right-size instances, use auto-scaling), Kafka with too many partitions (each partition needs a broker thread), S3 request costs (batch small operations), cross-AZ data transfer (route within AZ when possible), cache miss rate (more cache = fewer expensive DB reads). Spot instances for stateless workers (70% cheaper). Reserved instances for stable base load (40% cheaper). Evaluate: is this a compute cost, storage cost, or data transfer cost — each has a different lever.

**Q68: How do you reduce database costs at scale?**
Read replicas: offload read traffic to cheaper read replicas (charged separately, but may cost less than scaling up the primary). Data tiering: move cold data to cheaper storage (Glacier, S3) via lifecycle policies. Compression: store compressed JSON/Protobuf instead of plain text — reduces storage and I/O. Connection pooling (PgBouncer): reduces connection overhead, allows smaller DB instance. Query optimization: a slow query reading 100M rows per request costs 100× more than one reading 1M rows — query plan matters for cost too.

---

## 19. Distributed System Patterns

**Q69: What is the Saga pattern and when do you use it over 2PC?**
Saga: a sequence of local transactions, each publishing an event that triggers the next. On failure, compensating transactions undo previous steps. No distributed lock. Use when: services are independently owned (can't coordinate via shared DB), operations span multiple services, you can tolerate intermediate visible state. 2PC: all participants lock resources until coordinator says commit/rollback. Use when: strong consistency is required, all participants support 2PC, and low latency is not critical. 2PC blocks on coordinator failure; Saga is always available but temporarily inconsistent.

**Q70: What is the difference between choreography and orchestration in microservices?**
Choreography: each service reacts to events, no central coordinator. `OrderService` publishes `OrderCreated` → `InventoryService` reserves stock, publishes `StockReserved` → `PaymentService` charges, publishes `PaymentCharged`. Pros: decoupled, no single point of failure. Cons: hard to see the full flow, distributed debugging is painful. Orchestration: a central orchestrator (`OrderWorkflow`) explicitly calls each service in sequence. Pros: visible flow, easy to handle failures centrally. Cons: orchestrator becomes a bottleneck and coupling point. Choose based on team ownership — choreography when each service is owned by a different team.

**Q71: How does the transactional outbox pattern prevent dual write problems?**
Dual write: write to DB, then publish to Kafka. If the app crashes between the two, DB has the data but Kafka doesn't — downstream services never learn of the change. Outbox fix: within the same DB transaction, write to the business table AND an `outbox` table (with event payload). A separate process (Debezium CDC or a poller) reads the outbox and publishes to Kafka, then deletes the row. If publishing fails, the row remains in the outbox and retries. DB transaction guarantees atomicity of business write + outbox write.

**Q72: What is back-pressure and how do you propagate it upstream?**
Signal from a slow consumer to slow down the producer. Without it: the producer fills an unbounded queue until memory is exhausted. Mechanisms: bounded queue — producer blocks when queue is full (`queue.Queue(maxsize=N)` in Python, `Kafka producer` with `max.block.ms`). HTTP 429 — API signals the client to retry later. TCP flow control — receiver's buffer fills, TCP window shrinks, sender slows automatically. gRPC flow control — stream-level credit-based flow control. Always design producers to handle backpressure — a queue that never fills up has no backpressure.

**Q73: How does leader election work in a distributed system?**
Requires consensus — can't be done with pings alone (network partition looks like a crash). Approaches: Raft (implemented by etcd, Consul) — candidate requests votes, wins with majority, all log entries go through leader. ZooKeeper — ephemeral sequential nodes: each candidate creates `/election/node-NNNN`, the one with the smallest sequence number is leader. Database-based: optimistic lock on a `leader` table row with expiry timestamp — candidate that wins the CAS becomes leader. All require a quorum to prevent split-brain.

---

## 20. Amazon-Specific Patterns

**Q74: What is the Amazon "two-pizza team" rule and how does it influence system design?**
Teams small enough to be fed by two pizzas (~6–10 people). Implication for architecture: each team owns one or two services end-to-end (design, deploy, operate). Systems are designed as loosely coupled services with well-defined APIs — because tight coupling across teams creates coordination overhead. Each team owns their DB (no shared databases across service owners). This is why Amazon's service-oriented architecture predates the term "microservices."

**Q75: What is the Amazon "working backwards" process and how do you apply it in a design interview?**
Start from the customer experience, work backwards to the technical requirements. In an interview: "The customer needs to see their order status in real-time. That means we need sub-second updates. Working backwards: WebSocket push from our backend, fed by an event stream, sourced from order state changes." This framing shows you're not designing for technical elegance but for customer value — which is what Amazon cares about. Always anchor your design decisions on customer impact.

**Q76: How does Amazon use cell-based architecture?**
A "cell" is a fully independent replica of the entire stack (app servers, DB, cache) serving a subset of users. Blast radius of a failure is limited to one cell's user set. No cross-cell calls. New deploys roll out cell-by-cell (canary at cell level). Used for: high-availability services where even a regional outage of one cluster must not affect all users. Common pattern: shard users into cells by `user_id % N`, each cell is completely self-contained. Trade-off: cross-cell operations (e.g., a user following a user in another cell) require cross-cell API calls.

**Q77: What is exponential backoff with jitter and why is jitter critical?**
Exponential backoff: retry delay doubles each attempt — 1s, 2s, 4s, 8s. Prevents overwhelming a recovering service. Jitter: add randomness to the delay — `sleep = min(cap, base * 2^attempt) + random(0, base)`. Without jitter: all retrying clients (who all failed at the same time due to the same outage) retry simultaneously — thundering herd. Jitter spreads retries across time. "Full jitter" (`sleep = random(0, min(cap, base * 2^attempt))`) is more aggressive spread. AWS SDKs implement this by default — use it.

---

## Quick Reference Index

| Topic | Questions |
|---|---|
| Scoping & Requirements | Q1–Q4 |
| Capacity Estimation | Q5–Q7 |
| Database Selection | Q8–Q11 |
| Caching | Q12–Q15 |
| Message Queues & Async | Q16–Q20 |
| Scalability | Q21–Q24 |
| Consistency & Replication | Q25–Q28 |
| Reliability & Failure Handling | Q29–Q32 |
| APIs & Communication | Q33–Q36 |
| Real-Time & Streaming | Q37–Q39 |
| Search & Indexing | Q40–Q42 |
| Security in HLD | Q43–Q44 |
| Trade-off Questions | Q45–Q48 |
| CDN & Networking | Q49–Q53 |
| Observability & Monitoring | Q54–Q58 |
| Storage Patterns | Q59–Q62 |
| Multi-Region & Disaster Recovery | Q63–Q66 |
| Cost & Efficiency | Q67–Q68 |
| Distributed System Patterns | Q69–Q73 |
| Amazon-Specific Patterns | Q74–Q77 |
