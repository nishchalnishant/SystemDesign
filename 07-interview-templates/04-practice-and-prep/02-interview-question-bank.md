> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** A massive repository of over 90 follow-up questions interviewers use to test the depth of your knowledge.
>
> **Key concepts:**
> - Database Follow-ups: "How do you handle a replica lag?", "What if a shard becomes a hotspot?"
> - Cache Follow-ups: "How do you ensure cache consistency?", "What eviction policy are you using and why?"
> - Microservices Follow-ups: "How do you handle distributed transactions across three services?", "What happens if service B is slow?"
> - Networking Follow-ups: "Why WebSockets instead of Server-Sent Events?", "How does the client know when the job is done?"
>
> **Key takeaway:** You will be interrupted during your interview with these exact questions. Use this list to practice thinking on your feet and giving concise, 60-second answers.

---
module: 07-interview-templates
topic: Interviewer Follow-Up Question Bank
status: unread
tags: [07-interview-templates, interview, follow-up, question-bank]
---
# Interviewer Follow-Up Question Bank

> 90 follow-up questions interviewers actually ask, organized by topic. Each has a terse model answer. Use these to drill being interrupted mid-design and redirecting cleanly.

---

## How to Use

Practice: state your design, then randomly pick a question from the relevant section and answer it out loud in ≤ 60 seconds. The model answers here are the minimum acceptable — go deeper when you can.

---

## 1. Databases & Storage

**Q1: Why not just use a relational database for everything?**
Relational DBs require schemas, enforce ACID per-row, and scale vertically by default. At millions of writes/sec, row-level locking and normalized joins become bottlenecks. Use relational when you need transactions and structured queries; use wide-column (Cassandra), key-value (Redis), or document (Mongo) when you need horizontal write scale.

**Q2: When would you choose Cassandra over DynamoDB?**
Cassandra when you need: multi-cloud/on-prem, tunable consistency per-query (`QUORUM` vs `ONE`), or time-series with wide rows. DynamoDB when you want fully managed, serverless scaling, and are locked to AWS. Both are AP systems; key difference is operational model and tuning surface.

**Q3: Your DB is becoming a bottleneck — what's your escalation path?**
Step 1: add read replicas and cache hot reads. Step 2: connection pooling (PgBouncer). Step 3: vertical scale. Step 4: partition (shard) the write path. Step 5: introduce CQRS — separate read store from write store.

**Q4: You chose eventual consistency. What does the user experience when they hit a stale replica?**
They might see a like count that's 2 seconds behind, or a comment that hasn't appeared yet. Acceptable for social feeds (non-critical, self-healing). Not acceptable for account balance or seat reservation. Always state what "eventual" means in seconds and whether the user notices.

**Q5: How do you handle a hot partition in DynamoDB?**
Add a random suffix to the partition key (write sharding): `user_id + "#" + (random 0..N)`. Reads aggregate across N partitions. Alternatively, use a composite key where the sort key distributes writes (e.g., `user_id + timestamp`). For reads, cache the hot key in DAX or Redis.

**Q6: What's the difference between optimistic and pessimistic locking? When do you use each?**
Pessimistic: acquire lock before read (`SELECT FOR UPDATE`). Use when conflicts are frequent (e.g., seat booking — two users on the same seat). Optimistic: read with version number, write only if version unchanged. Use when conflicts are rare (e.g., user profile updates). Pessimistic has higher contention; optimistic has higher retry rate under conflict.

**Q7: How do you ensure a migration on a 500M-row table doesn't cause downtime?**
Use online schema change tools (pt-online-schema-change, gh-ost). They: create a new table, copy rows in batches, replicate live writes via triggers/binlog, then swap table names atomically. Never run `ALTER TABLE` directly on a large table — it locks the table.

**Q8: What is MVCC and why does Postgres use it?**
Multi-Version Concurrency Control: each row has `xmin`/`xmax` system columns marking which transaction created/deleted it. Readers see a snapshot of the DB at transaction start; they never block writers, and writers never block readers. Postgres uses it to provide snapshot isolation without read locks.

---

## 2. Caching

**Q9: Your cache hit rate is 60% — is that good or bad?**
Depends on the workload. For a social feed with millions of unique users, 60% is reasonable. For a product catalog with 10K SKUs, 60% means your eviction policy or key design is wrong — you should be at 95%+. Always contextualize hit rate against the key space size and access distribution.

**Q10: How do you handle cache invalidation for a shared object that 50 services read?**
Option A: TTL-based expiry — simple, no invalidation needed, but stale for TTL window. Option B: event-driven invalidation — write service publishes `cache.invalidate` event, consumers delete their cached copy. Option B is consistent but adds coupling. Hybrid: short TTL (30s) + event-driven — event removes stale cache immediately, TTL is a fallback safety net.

**Q11: What is cache stampede and how do you prevent it?**
When a popular key expires and many requests simultaneously hit the DB to repopulate it. Prevention: probabilistic early expiry (recompute before expiry with increasing probability as TTL → 0), mutex lock (first request gets lock, others wait then read populated cache), or background refresh (async worker refreshes before expiry).

**Q12: When should you NOT use a cache?**
When data is write-heavy and read-once (no benefit), when data must be strongly consistent (cache adds stale risk), when the data set is too large to fit in memory and there's no clear hot set (random access = low hit rate), or when cache failure would cause silent data loss.

**Q13: Redis vs Memcached — when do you choose which?**
Redis when you need: persistence, pub/sub, sorted sets (leaderboards), Lua scripting, cluster with data sharding, or TTL per-key. Memcached when you need: pure caching, simpler operations, multi-threaded (Redis is single-threaded per core), and have already invested in Memcached tooling.

---

## 3. Message Queues & Async

**Q14: Why Kafka over SQS for your use case?**
Kafka when you need: ordered delivery within a partition, consumer replay (rewind offset), high throughput (millions/sec), or fan-out to many consumer groups independently. SQS when you need: simplest managed queue, at-least-once delivery, no ordering required, and are on AWS. SQS is a queue; Kafka is a log.

**Q15: A consumer is processing messages too slowly — the queue is building up. What do you do?**
Short term: scale out consumers (add more instances, same consumer group). Medium term: check if processing is CPU-bound (add consumers) or I/O-bound (add async parallelism within each consumer). Long term: if the schema allows, increase partition count in Kafka so more consumers can process in parallel.

**Q16: How do you guarantee exactly-once processing with Kafka?**
Producer: enable idempotent producer (`enable.idempotence=true`) + transactions. Consumer: read-process-commit in a Kafka transaction if the output is also a Kafka topic; otherwise use an idempotency key stored in the output DB. True exactly-once requires coordination between the queue and the output sink.

**Q17: What's a dead letter queue and when do you use it?**
A DLQ receives messages that failed processing after N retries. Use it when you can't drop failed messages (payment events, order events) — you want to inspect and replay them manually. Always monitor DLQ depth as a critical alert; messages there mean production failures.

**Q18: How do you handle out-of-order messages in a Kafka consumer?**
If ordering matters: use a single partition per logical entity (e.g., `partition = user_id % N`) — all messages for the same user land in the same partition in order. If ordering doesn't matter: process any order, use idempotency keys to handle duplicates. Avoid global ordering — it serializes all consumers.

**Q19: What is backpressure and how do you implement it?**
Backpressure: signal from a downstream component to slow down the upstream producer when it's overwhelmed. Implementations: bounded queue (producer blocks when full), reactive streams (`request(N)` pull model), or rate limiting at the producer. Without backpressure, a slow consumer causes unbounded memory growth or message loss.

---

## 4. Distributed Systems

**Q20: Explain the CAP theorem without using the words "available" or "consistent."**
During a network partition, you must choose: either every node returns the same answer (even if it means refusing to answer), or every node answers immediately (even if answers differ). You can't have both. Example: two replicas can't agree on the current balance if they can't talk to each other.

**Q21: Your service is at 99.9% uptime. How much downtime is that per year?**
~8.7 hours. 99.99% = ~52 minutes. 99.999% = ~5 minutes. For a payment system, 8.7 hours/year is probably unacceptable. For a social feed, it might be fine. Always match your SLA to the business cost of downtime.

**Q22: How do distributed transactions work without 2PC?**
Saga pattern: each service executes a local transaction and publishes an event; if a downstream step fails, compensating transactions undo earlier steps. No distributed lock. The trade-off: intermediate states are visible, and compensations must be idempotent. Use 2PC only when you can't tolerate visible intermediate state (e.g., banking).

**Q23: Two services need to read each other's data. Is that okay?**
No — it creates circular dependency and tight coupling. Solutions: one service queries the other via API (owner of data), or replicate relevant data into the calling service's DB (denormalize across service boundary), or extract shared data into a third service. Never share a DB across services.

**Q24: How do you detect a network partition vs a slow node?**
You can't reliably distinguish them. This is the fundamental problem. You can use heartbeats with timeouts — if no heartbeat in N seconds, treat as failed. But a slow node looks the same as a partitioned node from the timeout perspective. Fencing tokens prevent split-brain by making old leaders unable to write even if they come back.

**Q25: What is a split-brain scenario and how do you prevent it?**
Two nodes both believe they are the leader and accept writes independently, leading to divergence. Prevention: require quorum acknowledgment for every write (Raft) — without quorum, neither can proceed. Alternatively, use fencing tokens: the storage layer rejects writes from any leader whose token is older than the current token.

**Q26: Your service has 10 downstream dependencies. How do you prevent a cascade failure?**
Circuit breaker per dependency: after N failures, open the circuit and return a fallback (cached response, default value, or graceful error). Timeout per dependency: don't wait indefinitely. Bulkhead: use separate thread pools per dependency so one slow dependency doesn't exhaust all threads. Degrade gracefully — return partial results rather than failing entirely.

**Q27: What's the difference between idempotency and exactly-once delivery?**
Idempotency: the operation can be applied multiple times with the same result (safe to retry). Exactly-once: the operation is applied exactly once, regardless of retries or failures. Idempotency is a property of the operation; exactly-once is a delivery guarantee. Exactly-once requires coordination between sender, queue, and receiver. Idempotency is often a cheaper way to tolerate at-least-once delivery.

---

## 5. API Design

**Q28: How do you version a REST API without breaking existing clients?**
Additive changes (new fields, new endpoints) are non-breaking — ship without version bump. Breaking changes (remove field, change type, rename) require a new version. Versioning in URL (`/v2/`) is explicit; versioning in headers (`Accept: application/vnd.api+v2+json`) is cleaner but harder to test. Support old versions for at least 6 months post-deprecation notice.

**Q29: How do you implement pagination for a feed with real-time inserts?**
Cursor-based (keyset) pagination: use `(created_at, id)` as cursor. Each page returns `next_cursor = last item's (created_at, id)`. New inserts don't shift pages because the cursor is positional, not offset-based. Offset pagination (`OFFSET 100 LIMIT 20`) is broken for real-time data — inserts shift rows and cause duplicates/skips.

**Q30: A client is calling your API 10,000 times per second and causing DB load. What do you do?**
Rate limit per client (token bucket, 1000 req/sec). Return `429 Too Many Requests` with `Retry-After` header. Add a CDN/cache layer for GET requests. Consider batching endpoints (accept array of IDs in one request). Investigate if the client can subscribe to events instead of polling.

**Q31: gRPC vs REST — when do you choose which?**
gRPC: service-to-service communication where you control both ends, need streaming, or need low-latency binary protocol. REST: public APIs, browser clients, teams with diverse language stacks, or when human-readability matters. gRPC requires protobuf schema management; REST is more flexible but less type-safe.

**Q32: How do you make a non-idempotent POST idempotent?**
Client generates a UUID and sends it as `Idempotency-Key: <uuid>` header. Server stores `idempotency_key → response` in Redis with TTL 24h. On duplicate request with same key, return stored response without re-executing. The key must be unique per logical operation, not per retry.

---

## 6. Scalability & Performance

**Q33: Your P99 latency is fine but P999 is 10 seconds. What's happening?**
P999 outliers typically indicate: GC pauses (JVM stop-the-world), lock contention (one slow path holds a lock others wait for), tail latency amplification (a request depending on N services has latency = max, not average), or hot spots (a small % of requests hit a slow resource). Profile the 0.1% — add tracing spans to identify which component contributes the 10s.

**Q34: How do you reduce tail latency in a microservices call chain?**
Hedged requests: send the same request to two replicas simultaneously after a short delay (e.g., 95th percentile of normal latency), use the first response. Timeout + retry: set aggressive timeouts per hop. Async fan-out: parallelize independent downstream calls rather than sequential. Avoid long synchronous call chains — each hop adds tail latency.

**Q35: You need to support 100× the current traffic with minimal code changes. Walk me through.**
First, identify the bottleneck: is it the app tier, DB reads, DB writes, or network? App tier: add instances behind a load balancer (stateless services scale horizontally). DB reads: add read replicas + cache. DB writes: shard by user_id or tenant_id. If the app is stateful, extract state to Redis/DB first.

**Q36: What is a thundering herd and how do you prevent it?**
Many clients retry simultaneously after a failure, causing a burst that overwhelms a recovering service. Prevention: exponential backoff with jitter (`sleep = base * 2^attempt + random(0, base)`). The jitter breaks synchronization so retries are spread across time rather than simultaneous.

**Q37: Your service is CPU-bound at 80% capacity. How do you scale?**
Short term: vertical scale (more CPU cores) or horizontal scale (add instances). Medium term: profile to identify the hot path — is it serialization, crypto, regex? Optimize the hot path. Long term: offload CPU work to async workers, or use more efficient algorithms/data structures. If it's predictably periodic, pre-scale before peak.

---

## 7. Data Modeling

**Q38: How do you model a many-to-many relationship at scale?**
Association table: `(user_id, group_id, joined_at)`. Index on both FK columns. At scale, if one side is very high cardinality (e.g., 1M members per group), denormalize: store member list in the group document (Mongo) or as a Cassandra wide row. For social graphs, use a dedicated graph store (TAO, Neo4j) when traversal depth > 2.

**Q39: User posts have tags. How do you query "all posts with tag X" efficiently?**
Inverted index: `tag → [post_id]`. In a relational DB: `post_tags(tag, post_id)` table, index on `tag`. At scale, Elasticsearch inverted index. For simple tag filtering on low cardinality tags, a relational index is sufficient; for full-text search or complex boolean queries, use a search engine.

**Q40: How do you design a schema for a time-series workload (e.g., metrics, IoT)?**
Key requirements: fast writes, range queries by time, efficient expiry of old data. Cassandra: partition key = `(device_id, time_bucket)` (e.g., one partition per day), cluster key = `timestamp`. TimescaleDB: hypertables with automatic time-based partitioning. Never use a single-table without time partitioning — old data bloats indexes and slows queries.

**Q41: Explain denormalization. When is it correct?**
Storing redundant data to avoid joins. Correct when: joins at scale are too slow (join across shards is not possible), read performance is more important than write overhead, or the data is append-only (no update consistency problem). Example: storing `username` in every post row so you don't need a join to render a feed. The trade-off is that a username change requires updating all posts.

---

## 8. Search

**Q42: How does an inverted index work?**
Maps each token → list of document IDs containing that token (posting list). Query "cat dog" intersects the two posting lists. Scoring (BM25, TF-IDF) computes relevance per document. Optimizations: skip pointers in posting lists for faster intersection, delta-compression (store diffs between sorted doc IDs), SSTable on disk for range merge.

**Q43: Your search index is stale by 30 seconds. A user searches for their just-created document and it doesn't appear. How do you handle this?**
Two options: async indexing with a "check your document" UX hint ("your document will appear in search in ~30 seconds"), or write-through indexing (sync to index on write, at cost of write latency). Hybrid: write-through for the author's own search results (use a per-user recent writes cache), async for everyone else.

**Q44: How do you implement autocomplete for a search bar with 1B queries/day?**
Trie stored in Redis (sorted set per prefix). On each keypress, query the sorted set for the prefix and return top-K by score (query frequency). Pre-compute top-K for common prefixes. For long-tail prefixes, fall back to a search engine. Keep the trie in memory; it's typically small (vocabulary is bounded, not 1B unique strings).

---

## 9. Real-Time & Streaming

**Q45: WebSocket vs long-polling vs SSE — when do you use each?**
WebSocket: bidirectional real-time (chat, multiplayer games). SSE (Server-Sent Events): server-to-client streaming only (live feeds, notifications) — simpler than WebSocket, automatic reconnect. Long-polling: fallback for environments where WebSocket/SSE is blocked. Choose the simplest that meets your requirements.

**Q46: How do you fan out a message to 10 million connected WebSocket clients?**
You can't fan out from a single server — 10M connections require many servers. Publish message to Kafka/Pub-Sub. Each WebSocket server consumes from Pub-Sub and pushes to its connected clients. Each server handles 100K connections → need 100 servers. Routing layer maps user_id → server_id so targeted messages go to the right server.

**Q47: What is head-of-line blocking and how does HTTP/2 solve it?**
In HTTP/1.1, a slow response blocks all subsequent requests on the same connection. HTTP/2 multiplexes many streams over one connection — each stream is independent, so one slow response doesn't block others. HTTP/3 (QUIC) solves it further by removing TCP's in-order delivery requirement.

---

## 10. Security

**Q48: A user's JWT is stolen. How do you invalidate it before expiry?**
JWTs are stateless — you can't invalidate them server-side without state. Solutions: short-lived tokens (15 min expiry) + refresh token rotation; `jti` (JWT ID) blocklist in Redis (check on every request, TTL = token expiry); or switch to opaque tokens (session IDs stored server-side, trivially revocable).

**Q49: How do you prevent SSRF in a service that fetches user-provided URLs?**
Allowlist of permitted domains. Resolve the URL, check the resulting IP is not in RFC-1918 (10.x, 172.16-31.x, 192.168.x) or loopback (127.x) ranges before fetching. Use a dedicated egress proxy that enforces the allowlist. Never rely solely on domain allowlist — DNS rebinding can resolve an allowed domain to an internal IP.

**Q50: Explain OAuth 2.0 Authorization Code + PKCE flow.**
Client generates `code_verifier` (random 128 chars) and `code_challenge = SHA256(code_verifier)`. Redirect user to auth server with `code_challenge`. Auth server returns auth code. Client sends auth code + `code_verifier` to token endpoint. Auth server recomputes `SHA256(code_verifier)` and verifies it matches stored `code_challenge`. Prevents auth code interception — even if the code is stolen, it's useless without the `code_verifier`.

**Q51: How do you store passwords securely?**
Hash with bcrypt, scrypt, or Argon2 — all are slow by design (to resist brute-force). Never use MD5, SHA-1, or SHA-256 for passwords (too fast). Include a per-user salt (bcrypt does this automatically). Work factor should be tuned so hashing takes ~100ms on your hardware. On breach, force reset for all users.

---

## 11. Reliability & Operations

**Q52: How do you do a zero-downtime deployment?**
Blue-green: deploy new version alongside old, shift traffic (DNS or LB), rollback by pointing back. Rolling: replace instances one at a time; old and new run simultaneously — requires backward-compatible API and DB schema changes. Canary: shift 1% traffic to new version, monitor, ramp. All require: health checks, graceful shutdown (drain in-flight requests), and backward-compatible schema migrations.

**Q53: What metrics do you alert on for a microservice?**
Golden signals: latency (P99, P999), error rate (5xx / total), saturation (CPU, memory, queue depth), traffic (RPS). Add: downstream dependency error rate (are they failing you?), DB connection pool saturation, cache hit rate. Alert on rate of change, not just absolute value — a sudden 2× error rate spike matters more than a sustained 0.1%.

**Q54: A production incident is happening right now. Walk me through your first 5 minutes.**
1. Acknowledge the alert, join the incident channel. 2. Check error rate and latency dashboards to confirm scope. 3. Identify what changed in the last 30 min (deploys, config changes, cron jobs). 4. Attempt fastest mitigation (rollback if recent deploy, feature flag off, redirect traffic). 5. Communicate status to stakeholders. Root cause analysis after mitigation.

**Q55: How do you design for graceful degradation?**
Define which features are critical vs nice-to-have. For non-critical: add circuit breaker with fallback (return cached/default value). For critical: ensure those paths have no single points of failure (replicas, retries). Example: recommendation engine fails → serve popular items instead of personalized. Payment service fails → fail the whole checkout, no degraded mode.

**Q56: What is chaos engineering and why do teams do it?**
Deliberately injecting failures (kill a node, add latency, drop packets) in production or staging to verify that systems fail gracefully and that alerts fire correctly. Teams do it because they discover failure modes before incidents do. Netflix Chaos Monkey is the canonical example. Start in staging; graduate to production only when you trust your fallbacks.

---

## 12. Concurrency & Java-Specific (LLD)

**Q57: ReentrantLock vs synchronized — when do you use each?**
`synchronized` is simpler and sufficient for most cases. `ReentrantLock` when you need: timed lock attempts (`tryLock(timeout)`), interruptible locking (`lockInterruptibly`), or a fair lock (`new ReentrantLock(true)`). `ReentrantReadWriteLock` is a separate class — use it when you have many concurrent readers and infrequent writers.

**Q58: What is a race condition vs a data race?**
Data race: two threads access shared memory without synchronization (at least one write) — undefined behavior in Java's memory model. Race condition: the correctness of the program depends on timing — even with proper synchronization, the outcome varies. Example: check-then-act (`if (map.containsKey(k)) map.get(k)`) is a race condition even if each call is individually thread-safe.

**Q59: Your thread pool is rejecting tasks. What do you do?**
Check RejectedExecutionHandler — default throws `RejectedExecutionException`. Understand why the queue is full: is the work rate > processing rate? Increase pool size if CPU/I/O headroom exists; increase queue capacity as a buffer; implement backpressure (block the caller instead of rejecting). Add queue depth to your monitoring.

**Q60: ConcurrentHashMap vs Collections.synchronizedMap — what's the difference?**
`Collections.synchronizedMap` wraps every method in a `synchronized(mutex)` — global lock, no concurrency. `ConcurrentHashMap` uses segment-level or bucket-level locking (Java 8+: CAS + synchronized per bucket head) — allows concurrent reads and segment-local writes. Use `ConcurrentHashMap` for all concurrent use cases; `synchronizedMap` is legacy.

**Q61: Explain the happens-before guarantee in Java.**
A happens-before relationship guarantees that all writes before the relationship are visible to all reads after it. Sources: `volatile` reads/writes, `synchronized` block entry/exit, `Thread.start()`, `Thread.join()`, and explicit lock operations. Without happens-before, the JIT and CPU can reorder operations, making shared state invisible across threads.

**Q62: What is false sharing and how do you avoid it?**
When two threads write to different variables that happen to share the same CPU cache line (64 bytes), each write invalidates the other CPU's cache — serializing what should be parallel work. Fix: pad variables to force separate cache lines (`@Contended` annotation in Java 9+), or use `LongAdder` which internally manages per-CPU cells. Critical in high-throughput counters.

---

## 13. HLD Deep Dive (System-Specific)

**Q63: How does Kafka guarantee message ordering?**
Within a partition: messages are appended in order and consumed in order. Across partitions: no ordering guarantee. To guarantee order for a logical entity (e.g., all events for user_id=123), always route that entity to the same partition (`hash(user_id) % num_partitions`). Increasing partition count requires re-routing existing producers.

**Q64: How does Cassandra handle a node failure during a write?**
With `QUORUM` consistency: write must succeed on `⌊N/2⌋ + 1` replicas. If the failed node is part of the quorum, the write fails. If not, it succeeds. The failed node misses the write — on recovery, it uses hinted handoff (other nodes stored the write as a "hint") or read repair to reconcile. Anti-entropy (Merkle tree comparison) runs in the background.

**Q65: Walk me through what happens when you call PUT /object on S3.**
Client sends request to S3 frontend (load balancer). Frontend authenticates via HMAC-SHA256 signature. Request routes to metadata service (looks up bucket → storage node mapping). Storage node writes the object to the underlying file system (XFS/EXT4) and replicates to 3 AZs. Metadata service records the key → storage node mapping. Client receives 200 + ETag (MD5 of object).

**Q66: How does a CDN cache work and what are its failure modes?**
CDN edge node caches objects by URL. On miss, fetches from origin and caches with TTL from `Cache-Control` header. Failure modes: cache poisoning (attacker serves malicious response that gets cached), stale content (origin updated but CDN not invalidated), thundering herd on cold start (origin overwhelmed when CDN clears), cache bypass (vary headers or cookies prevent caching).

**Q67: How do you design a rate limiter that works across a cluster of servers?**
Local token bucket per server doesn't work — each server has its own counter, total rate = N × limit. Centralized Redis: each request increments a Redis counter with 1-second TTL (`INCR + EXPIRE`). Lua script for atomicity. Trade-off: Redis becomes a dependency; if Redis is down, fail open (allow) or fail closed (deny). Sliding window log in Redis gives smoother limiting than fixed window.

**Q68: How does consistent hashing work and why is it better than modulo hashing?**
Hash both servers and keys onto a ring (0 to 2^32). Each key is served by the first server clockwise from the key's hash. When a server is added/removed, only `K/N` keys need remapping (K = total keys, N = nodes). Modulo hashing (`key % N`) remaps nearly all keys when N changes — causes mass cache misses on node addition/removal. Virtual nodes handle uneven distribution.

**Q69: How does Elasticsearch rank search results?**
Default: BM25 (Best Match 25). BM25 score = sum over query terms of: `IDF(term) × TF(term, doc) × (k1 + 1) / (TF + k1 × (1 - b + b × docLen/avgDocLen))`. IDF rewards rare terms; TF rewards frequent matches; doc length normalization penalizes long documents. For production, usually augmented with: field boosting (title > body), recency decay, custom signals (CTR, engagement).

**Q70: How does a load balancer decide which server to send a request to?**
Algorithms: round-robin (equal distribution, ignores server load), least connections (sends to server with fewest active requests — better for variable-latency requests), consistent hashing (same client always hits same server — for stateful apps, session affinity), weighted round-robin (accounts for heterogeneous hardware). L7 LBs (Nginx, ALB) can route by path, header, or cookie.

---

## 14. Architecture Trade-offs

**Q71: Microservices vs monolith — what's your starting point for a new product?**
Start monolith. Microservices solve team-scale and deployment-independence problems — those don't exist for a new product with one team. Extract services when: a component has a clearly different scaling profile, or team ownership boundaries demand it. Premature microservices add distributed systems overhead before you've validated the domain model.

**Q72: Synchronous vs asynchronous communication between services — when do you choose each?**
Synchronous (HTTP/gRPC): when the caller needs the result to proceed, and latency SLO allows the round trip. Asynchronous (message queue): when the operation can be deferred, you need to decouple sender from receiver, or you need buffering for bursty traffic. Async introduces complexity: message ordering, DLQ, idempotency. Sync is simpler; prefer it until you need async benefits.

**Q73: How do you choose between SQL and NoSQL for a new project?**
SQL: structured data with relationships, need ACID transactions, query patterns are not fully known (flexible queries via SQL). NoSQL: known access patterns that map to the NoSQL model, horizontal write scale needed, or data is naturally document/key-value/graph shaped. Don't choose NoSQL because it's "modern" — choose it when a specific NoSQL model solves a specific problem SQL can't.

**Q74: When would you use a graph database?**
When your primary queries involve multi-hop traversals over relationships: "friends of friends," "what path exists between A and B," "all accounts reachable from a fraudulent account." Relational DBs can do this but require expensive recursive CTEs. Graph DBs (Neo4j, Amazon Neptune) store adjacency natively — traversal is O(depth), not O(table size).

**Q75: Your system needs 99.999% availability. What does that change in your architecture?**
Single-region fails this — AWS region has ~99.99% SLA. Requires: multi-region active-active or active-passive, global load balancer (Route53, Cloudflare), cross-region DB replication with automatic failover. Operational: chaos engineering, runbooks, automated failover with no human in loop. Cost: roughly 2× infrastructure. Validate whether the business actually needs five-nines before committing.

---

## 15. Estimation

**Q76: How many servers do you need to handle 1M requests/second?**
Depends on request complexity. A simple API call (cache hit, return JSON): one server handles ~5K-50K RPS. At 1M RPS, need 20–200 servers. A DB-backed call: one server handles ~500-5K RPS depending on query latency. Add 30% headroom for peaks. Always state your assumptions: "I'm assuming each request takes 10ms of compute time on a 32-core server."

**Q77: Estimate the storage needed for 100M users with 10 posts each.**
100M × 10 = 1B posts. Assume 1 post = 1KB text + metadata. 1B × 1KB = 1TB raw. With 3× replication = 3TB. Add: 1 image per 10 posts = 100M images × 100KB = 10TB. Total: ~13TB. Add 20% overhead for indexes/metadata = ~16TB. This is manageable on a few large nodes or distributed across a small object store cluster.

**Q78: How long does it take to read 1GB from disk vs memory vs network?**
Memory: ~1GB/s (DDR4 sequential) → ~1 second. NVMe SSD: ~3GB/s → ~0.3 seconds. HDD: ~100MB/s → ~10 seconds. Network (1Gbps): ~125MB/s → ~8 seconds. Network (10Gbps): ~1.25GB/s → ~0.8 seconds. These numbers drive architectural decisions — e.g., why caching in memory is 10–100× faster than DB reads.

---

## 16. Behavioral + Design Philosophy

**Q79: A junior engineer on your team proposes a complex distributed solution for a simple problem. How do you handle it?**
Ask them to walk through the failure modes and operational overhead of their solution. Often complexity reveals itself. Then propose the simpler alternative and explain why: "this adds 3 new failure modes; what specific problem does the complexity solve that a simple DB transaction doesn't?" Guide, don't dismiss.

**Q80: You disagree with the tech lead's architectural decision. What do you do?**
State disagreement once, clearly, with specific technical reasoning and data ("this approach has O(N²) fan-out at 10M users; here's an alternative"). If overruled: commit fully and execute, document your objection and the decision rationale in the design doc. Escalate only if the decision creates a correctness or security risk, not just a performance preference.

**Q81: How do you decide when a design is "good enough" to ship?**
It correctly handles the stated requirements at the stated scale. Critical failure modes are handled (data loss, security, consistency). Operational visibility exists (monitoring, alerting). Future scale path is clear even if not implemented. Avoid perfect-is-the-enemy-of-good — ship a correct V1 and iterate. Document known limitations explicitly.

**Q82: You're designing a system but requirements are ambiguous. How do you proceed?**
List your assumptions explicitly: "I'm assuming 10M users, peak 100K RPS, P99 < 100ms, data must be durable." Ask 3 targeted questions that change the architecture if answered differently. Don't ask questions whose answers wouldn't change your design. Then design to the stated assumptions and call out "if X changes, we'd need to revisit Y."

---

## 17. Extended / Hard Follow-ups

**Q83: How does Raft achieve consensus?**
Leader election: candidate increments term, requests votes, wins with majority. Log replication: leader appends entry, sends `AppendEntries` to followers, commits when majority acknowledge. Safety: leader completeness (leader has all committed entries from previous terms), election restriction (only nodes with up-to-date log can win). Split votes are resolved by randomized election timeouts.

**Q84: What is the two-generals problem and what does it imply?**
Two generals coordinating an attack can never be 100% certain the other received their message, because acknowledgments can also be lost. In distributed systems: you cannot guarantee message delivery over an unreliable network. Implication: protocols must be designed to tolerate message loss (idempotent retries), not assume delivery.

**Q85: How does Zookeeper use ZAB for coordination?**
ZAB (Zookeeper Atomic Broadcast): leader broadcasts proposals, followers acknowledge, leader commits when quorum reached, sends commit to followers. On leader failure: new leader elected, runs recovery phase to ensure all committed proposals are replicated before accepting new writes. ZAB guarantees total order of updates; Raft provides similar guarantees with a simpler design.

**Q86: What is the difference between a process crash and a Byzantine failure?**
Crash failure: node stops responding — detectable via timeout, safe to exclude. Byzantine failure: node sends incorrect or malicious data — undetectable without cryptographic verification (digital signatures on messages). Standard distributed systems assume crash failures; blockchain/consensus in adversarial environments requires Byzantine fault tolerance (needs 3f+1 nodes to tolerate f Byzantine nodes).

**Q87: How would you design a globally consistent counter (like "likes" on a post) that works across 5 data centers?**
Approach 1: single-region write with cross-region replication — consistent but region failure breaks writes. Approach 2: per-region counter with periodic aggregation — eventually consistent, shows stale counts but highly available. Approach 3: CRDTs (G-Counter = grow-only) — each region maintains its own count, merge = sum of all regions — no conflicts, eventually consistent. For likes, eventual consistency is acceptable; choose approach 2 or 3.

**Q88: How does Kafka handle replication?**
Each partition has a leader and N-1 followers (ISR = in-sync replicas). Producer writes to leader; leader forwards to ISR. `acks=all` waits for all ISR to acknowledge before returning success. If a follower falls behind (lag > `replica.lag.time.max.ms`), it's removed from ISR. Leader election: any ISR can become leader. `unclean.leader.election=false` (default) prevents non-ISR from becoming leader — prevents data loss at cost of availability.

**Q89: How do you implement a distributed rate limiter with sliding window semantics?**
Redis Sorted Set: `ZADD key timestamp timestamp` (use timestamp as both score and member). `ZREMRANGEBYSCORE key 0 (now - window)` to remove old entries. `ZCARD key` to count current requests. This is O(log N) per request. For high throughput (>100K req/sec), shard the rate limiter by user_id across multiple Redis nodes. Atomic Lua script: remove old entries + count + conditionally add in one operation.

**Q90: Walk me through designing a feature flag system for 1B users.**
Storage: feature flags in a low-latency store (Redis) with DB as source of truth. Client SDK fetches flag config on startup, caches locally, refreshes every 60s. Targeting rules: flag config contains rules `{if: user.country == "US" AND user.tier == "premium", return: true}`. Evaluation is local (no network call per flag check). Rollout: `percentage_rollout: hash(user_id + flag_name) % 100 < rollout_percent`. Audit log every flag change. Kill switch: `global_off` rule evaluated first.

---

## Quick Reference Index

| Topic | Questions |
|---|---|
| Databases | Q1–Q8 |
| Caching | Q9–Q13 |
| Messaging | Q14–Q19 |
| Distributed Systems | Q20–Q27 |
| API Design | Q28–Q32 |
| Scalability | Q33–Q37 |
| Data Modeling | Q38–Q41 |
| Search | Q42–Q44 |
| Real-Time | Q45–Q47 |
| Security | Q48–Q51 |
| Reliability | Q52–Q56 |
| Concurrency/Java | Q57–Q62 |
| System Deep Dives | Q63–Q70 |
| Architecture Trade-offs | Q71–Q75 |
| Estimation | Q76–Q78 |
| Behavioral | Q79–Q82 |
| Hard / Extended | Q83–Q90 |
