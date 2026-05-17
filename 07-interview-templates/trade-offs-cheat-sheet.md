# System Design Trade-offs Cheat Sheet

> **Quick decision guide for senior/staff engineer level system design interviews**

## Core Trade-off Categories

1. [Consistency vs Availability](#consistency-vs-availability)
2. [Latency vs Throughput](#latency-vs-throughput)
3. [SQL vs NoSQL](#sql-vs-nosql)
4. [Synchronous vs Asynchronous Processing](#synchronous-vs-asynchronous-processing)
5. [Push vs Pull](#push-vs-pull)
6. [Horizontal vs Vertical Scaling](#horizontal-vs-vertical-scaling)
7. [Monolith vs Microservices](#monolith-vs-microservices)
8. [Stateful vs Stateless](#stateful-vs-stateless)
9. [Normalization vs Denormalization](#normalization-vs-denormalization)
10. [Caching Strategies](#caching-strategies)
11. [Message Queues](#message-queues)
12. [API Design (REST vs GraphQL vs gRPC)](#api-design)

---

## Consistency vs Availability

> **Analogy:** Two bank branches sharing one ledger vs two branches keeping separate ledgers that sync nightly. The shared ledger (strong consistency) means every teller always sees the true balance — but if the connection goes down, both branches stop working. Separate ledgers (eventual consistency) keep both branches running through outages, but a deposit at one branch might not show at the other until tomorrow.

### Decision Criteria

**Choose Strong Consistency (CP) when:** incorrect data causes direct harm — financial loss, double-booking, overselling, or security violations.

**Choose Eventual Consistency (AP) when:** stale data is merely inconvenient — a follower count being off by a few seconds, a feed not showing the latest post immediately.

### Strong Consistency (CP in CAP)

```
Use when:
- Financial transactions (bank transfers, payments)
- Inventory management (prevent overselling concert tickets)
- Hotel/flight seat reservations (no double-booking)
- Auction systems (highest bidder must be authoritative)
- Distributed locks (only one node holds a lock)

Technologies:
- Google Spanner (global strong consistency via TrueTime)
- PostgreSQL (single region)
- etcd (Raft consensus — leader always has latest)
- ZooKeeper
```

### Eventual Consistency (AP in CAP)

```
Use when:
- Social media feeds (seeing a post 2 seconds late is fine)
- Product catalogs (price update propagates in <1 minute — acceptable)
- DNS (propagation delay of minutes to hours is tolerated)
- Analytics dashboards (approximate counts are fine)
- User profiles (read-your-own-writes via session consistency)

Technologies:
- Cassandra (tunable consistency — QUORUM vs ONE)
- DynamoDB (eventual by default, strong available per-item)
- Riak
- CouchDB
```

### Real-World Examples

- **Stripe**: Strong consistency for payment processing (CP). A charge either happened or it didn't.
- **Twitter**: Eventual consistency for timeline (AP). Your follower count being 99 vs 100 for 2 seconds is fine.
- **Amazon**: Eventual for product reviews (AP), strong for inventory "add to cart" (CP — can't sell what you don't have).

### Interview Phrase Template

> "For [payment processing / reservations], I'd choose strong consistency — the business impact of a duplicate charge or double-booking is severe enough that we can't tolerate stale reads. I'd use [PostgreSQL / Spanner] and accept that during a partition we'd degrade gracefully rather than serve incorrect data.
>
> For [the activity feed / user profile display], eventual consistency is fine. A user seeing their own post appear 500ms later doesn't hurt the business. I'd use Cassandra with QUORUM reads to balance consistency and availability."

---

## Latency vs Throughput

> **Analogy:** A sports car vs a freight train. The sports car (low latency) gets from A to B fastest for one passenger. The freight train (high throughput) moves the most cargo per hour, even though each individual item takes longer to arrive.

### Decision Criteria

**Optimize for latency when:** a human is waiting for the response. Every millisecond of latency is felt.

**Optimize for throughput when:** no human waits on individual operations. You care about total jobs processed per hour.

| Metric | Definition | Optimization |
|--------|------------|--------------|
| **Latency** | Time to complete one request | Caching, CDN, faster algorithm, closer server |
| **Throughput** | Requests handled per unit time | Horizontal scaling, async processing, batching |

### Optimize for Latency

```
Use when:
- User-facing APIs (every 100ms extra = measurable conversion drop)
- Real-time systems (gaming tick rate, high-frequency trading)
- Search engines (user abandons if >200ms)
- Payment processing (user is staring at a spinner)

Techniques:
- In-memory caching (Redis sub-ms reads)
- CDN for static assets (serve from edge, not origin)
- Read replicas (distribute read load)
- Connection pooling (avoid handshake overhead per request)
- Pre-computation (compute timelines on write, not on read)
```

### Optimize for Throughput

```
Use when:
- Batch jobs (nightly ETL, ML training data prep)
- Log aggregation (ingest millions of events/sec)
- Video transcoding (process 10,000 videos per day)
- Data analytics pipelines (crunch last month's data)

Techniques:
- Message queues (decouple producers from consumers)
- Horizontal scaling (add workers, not faster workers)
- Async processing (don't block on slow operations)
- Batching (group DB writes, network calls)
- Micro-batching (Spark Streaming, Flink)
```

### Real-World Example

```
Video Upload System:
├── Upload API: Optimize for latency
│   → User waits. Response must be <200ms.
│   → Accept file, write to S3 (pre-signed URL), return 202 Accepted
│
└── Video Processing: Optimize for throughput
    → User doesn't watch transcoding happen
    → Queue job to SQS
    → 100 worker instances transcode in parallel
    → Notification when done (webhook / push)
```

### Interview Phrase Template

> "The upload API is on the critical path — the user is staring at a progress bar. I'll optimize for latency: accept the file quickly, store it, and return immediately. The transcoding pipeline is not user-facing and can take minutes — I'll optimize for throughput using a job queue and a fleet of workers."

---

## SQL vs NoSQL

> **Analogy: A filing cabinet vs a pile of labeled bins.**
>
> A filing cabinet (SQL) has rigid, labeled folders. Everything has a defined place. You can cross-reference folders with precision. Finding all orders for a given customer is trivial — but adding a new type of document means reorganizing the entire system.
>
> Labeled bins (NoSQL) let you toss in any shape of document. Super flexible, scales easily by adding bins, but finding relationships across bins requires more work on your end.

### Decision Criteria

**Choose SQL when:** data is relational, transactions span multiple entities, or you need complex queries with JOINs.

**Choose NoSQL when:** you need to scale horizontally past what a single SQL node handles, data is document/key-value/graph shaped, or your schema evolves rapidly.

### Decision Matrix

| Factor | SQL | NoSQL |
|--------|-----|-------|
| **Data Structure** | Structured, relational | Flexible, nested |
| **Scalability** | Vertical (scale-up) | Horizontal (scale-out) |
| **Transactions** | ACID across tables | Eventual (some support ACID per-document) |
| **Schema** | Fixed, migrations required | Schema-less, dynamic |
| **Joins** | Native, efficient | Manual denormalization |
| **Best For** | Complex queries, financial | High scale, flexible schema |

### SQL: Use When

```
- Structured data with relationships (user → orders → products)
- ACID transactions required across multiple entities
- Complex reporting queries (GROUP BY, window functions)
- Moderate scale (<500K QPS)

Databases:
- PostgreSQL: Full-text search, JSONB, advanced indexing
- MySQL: Fast reads, wide ecosystem, replication
- Aurora: Managed SQL, 5× faster than MySQL, auto-scaling storage
```

### NoSQL: Use When

```
- Massive scale (>1M QPS, billions of records)
- Schema evolves frequently (startup with changing requirements)
- High write throughput (IoT events, activity logs, analytics)
- Simple key lookups (sessions, cache, user prefs)

Databases:
- DynamoDB: Key-value, serverless, predictable latency at any scale
- MongoDB: Document store, flexible schema, good for catalogs
- Cassandra: Column-family, built for high-write, time-series workloads
- Redis: In-memory, <1ms, lists, sorted sets, pub/sub
- Neo4j: Graph, friend-of-friend queries, recommendation engines
```

### Polyglot Persistence (Use Both)

```
E-commerce Platform:
├── PostgreSQL: Orders, payments, inventory (ACID critical)
├── MongoDB: Product catalog (flexible attributes per category)
├── Redis: Shopping cart, user sessions (sub-ms access)
├── Elasticsearch: Product search (full-text, faceting)
└── Cassandra: User activity/clickstream logs (high write volume)
```

### Real-World Examples

- **GitHub**: PostgreSQL for repositories, issues, PRs. MySQL at scale. Vitess for sharding.
- **Netflix**: Cassandra for viewing history and user preferences (billions of writes/day).
- **Airbnb**: MySQL for bookings and payments (ACID). Elasticsearch for search.

### Interview Phrase Template

> "I'd use PostgreSQL for the core transactional data — user accounts, orders, and payments — because we need ACID guarantees and complex JOIN queries. For the product catalog, which has variable attributes per category (a laptop has RAM specs, a shirt has size/color), I'd use MongoDB for its flexible schema. For sessions and cart data, Redis gives us sub-millisecond access without hitting the primary DB."

---

## Synchronous vs Asynchronous Processing

> **Analogy: A dine-in restaurant vs a deli counter.**
>
> Synchronous is like a waiter who takes your order, walks to the kitchen, stands there while the chef cooks, and brings your food back. You wait. Nothing else happens until your food arrives.
>
> Asynchronous is like ordering at a deli counter: you get a buzzer, go sit down, and the deli calls you when your order is ready. You can do other things. The kitchen can handle many orders simultaneously without anyone standing at the window waiting.

### Decision Criteria

**Choose Synchronous when:** the caller needs the result immediately to proceed, and the operation is fast (<100ms).

**Choose Asynchronous when:** the operation is slow, can run in the background, or decoupling producers from consumers improves resilience.

### Synchronous

```
Use when:
- User needs an immediate response (login, payment confirmation)
- Operation is fast (<100ms)
- Result is required to proceed (validate credit card → charge)

Example:
User → POST /login → Verify Password → Return JWT
(User waits. Should be <100ms.)
```

**Pros:** Simple mental model, immediate feedback, easy error handling.
**Cons:** High latency if the downstream is slow, thread/connection blocked while waiting.

### Asynchronous

```
Use when:
- Operation takes >1 second (video transcoding, email sending, ML inference)
- Non-critical path (audit logging, analytics events)
- Decoupling is valuable (order service shouldn't care if email service is slow)

Example:
User → POST /upload-video → Queue Job → Return 202 "Processing"
                              ↓
                     Background Worker → Transcode
                              ↓
                     Notify User → "Video is ready"
```

**Pros:** Non-blocking, better resource utilization, resilient to downstream slowness, natural retry mechanism.
**Cons:** Complexity (job tracking, failure handling, idempotency), eventual completion (user doesn't know immediately if it worked).

### Hybrid Approach

```
Order Processing:
1. Synchronous: Validate order, reserve inventory, return order ID (fast)
   Response: {"order_id": "abc123", "status": "confirmed"}

2. Asynchronous: Charge payment, send confirmation email, update analytics
   Queue SNS/SQS → Workers execute
   If payment fails → Compensating transaction (release inventory)
```

### Real-World Examples

- **Stripe**: Synchronous charge API (you need to know if the card was declined). Async webhook for dispute resolution.
- **GitHub**: Synchronous PR creation. Async CI/CD pipeline (you're notified when checks pass).
- **Uber**: Synchronous ride matching (you need a driver assigned now). Async driver rating processing.

### Interview Phrase Template

> "The checkout flow is synchronous — the user needs to know if their payment succeeded before we show them a confirmation page. But sending the order confirmation email, updating inventory analytics, and triggering the fulfillment notification are all async. They're not on the critical path. I'd publish an order_confirmed event to SQS and let downstream consumers handle their own concerns independently."

---

## Push vs Pull

> **Analogy: Push = your phone buzzing with a notification. Pull = you checking your email manually every few hours.**
>
> Push is proactive — the server delivers updates the moment they happen. Pull is on-demand — the client decides when to ask for updates.

### Decision Criteria

**Choose Push when:** updates must arrive in real time and you can maintain persistent connections.

**Choose Pull when:** updates are infrequent, clients may be offline, or stateless scalability matters more than immediacy.

### Push Model

```
Server → Client (proactive delivery)

Examples:
- WebSockets (chat, collaborative editing, multiplayer games)
- Server-Sent Events (live dashboards, feed updates)
- Mobile push notifications (APNs, FCM)
- Webhooks (server calls your server when an event occurs)

Pros:
- True real-time (sub-second latency)
- No wasted requests

Cons:
- Persistent connections consume server resources
- Stateful — harder to load balance (need sticky sessions or pub/sub layer)
- Client must be online (or queue messages for delivery)

Use when:
- Real-time chat (Slack, WhatsApp)
- Live sports scores, stock tickers
- Multiplayer games
- Collaborative editing (Google Docs)
```

### Pull Model

```
Client → Server (client-initiated polling)

Examples:
- REST APIs called on demand
- Polling (client calls server every N seconds)
- Cron jobs that fetch data periodically

Pros:
- Stateless — trivially load balanced
- Works even if client was offline
- Simple server implementation

Cons:
- Latency equal to polling interval
- Wasted requests when nothing has changed

Use when:
- Email inbox (check every few minutes is fine)
- Social media feed (pull-to-refresh pattern)
- Dashboard data that refreshes every 30 seconds
- Batch data exports
```

### Hybrid: Long Polling

```
Client → Server: "Give me updates. I'll wait."
Server: Holds the request open (up to 30s timeout)
Server → Client: New data arrives → respond immediately
                 No data for 30s → respond with empty/timeout
Client: Immediately re-opens a new request

Analogy: Calling a friend and being put on hold until they have news,
rather than hanging up and calling back every 5 minutes.

Pros:
- Lower effective latency than polling
- Stateless enough to load balance (each new request can hit any server)

Cons:
- Ties up a connection per waiting client
- Not as real-time as WebSockets
- More complex than simple polling

Use when: Moderate real-time needs, WebSockets blocked by proxies/firewalls
```

### Real-World Examples

- **Slack**: WebSockets (push) for real-time message delivery. Falls back to long polling in constrained networks.
- **Gmail**: Long polling originally. Now WebSockets + push notifications.
- **GitHub Webhooks**: Push model — GitHub calls your server when a PR is opened.
- **RSS readers**: Pull — the client polls the feed URL on a schedule.

### Interview Phrase Template

> "For the live chat feature, I'd use WebSockets — users expect messages to appear instantly, and maintaining a persistent connection per user is acceptable at our scale. For the activity feed, pull (REST API + client-side polling every 60 seconds) is fine — users understand the feed isn't perfectly real-time, and it's much simpler to scale stateless REST servers than stateful WebSocket servers."

---

## Horizontal vs Vertical Scaling

> **Analogy: Vertical = hiring one super-employee who does everything. Horizontal = hiring a team of regular employees.**
>
> A single superhero employee can work faster (more CPUs, more RAM) — but there's a limit to how super any one person can be, they're a single point of failure, and the cost grows non-linearly. A team of regular employees is cheaper, resilient (someone gets sick, others cover), and infinitely expandable — but requires coordination and management overhead.

### Decision Criteria

**Choose Vertical (Scale Up) when:** your application is early-stage, stateful, or architecturally difficult to distribute. Buy time, then plan for horizontal.

**Choose Horizontal (Scale Out) when:** you need fault tolerance, have hit the vertical ceiling, or are building for high scale from the start.

### Vertical Scaling (Scale Up)

```
Add more resources to one machine:
- More CPU cores
- More RAM
- Faster storage (HDD → SSD → NVMe)
- Faster network interface

Pros:
- No code changes required
- No distributed systems complexity
- No data consistency challenges

Cons:
- Hard physical limits (AWS largest instance ~24 TB RAM, 448 vCPUs)
- Single point of failure
- Expensive at the high end (diminishing returns)
- Requires downtime to resize (usually)

Use when:
- Early stage, low traffic
- Stateful applications (database, session store)
- Buy time before re-architecting
```

### Horizontal Scaling (Scale Out)

```
Add more machines:
- Load balancer → multiple stateless app servers
- Database: read replicas, then sharding

Pros:
- No theoretical upper limit
- Fault tolerant (lose a node, others pick up load)
- Cost-effective (commodity hardware)
- Zero downtime scaling (add nodes while running)

Cons:
- Distributed systems complexity
- Data consistency is harder
- Requires stateless design (or external session store)
- Network latency between nodes

Use when:
- Traffic exceeds one machine's capacity
- Fault tolerance is a requirement
- Growing rapidly and unpredictably
```

### Real-World Scaling Strategy

```
Phase 1: 0–100K users → Vertical
  - Single server (app + DB on same machine)
  - Scale up instance size as needed
  - Fast to deploy, simple ops

Phase 2: 100K–1M users → Hybrid
  - Vertical: Larger DB instance (more RAM for buffer pool)
  - Horizontal: Multiple app servers behind a load balancer
  - Add read replica for DB

Phase 3: >1M users → Horizontal
  - App servers: Auto-scaling group
  - DB: Sharding or moving to distributed DB (Cassandra, DynamoDB)
  - Cache layer: Redis cluster
  - CDN for all static assets
```

### Real-World Examples

- **Instagram**: Started on a single server. Scaled vertically first, then horizontally. Now runs on thousands of servers.
- **Stack Overflow**: Runs on a surprisingly small number of heavily optimized vertical servers. Proves vertical + optimization beats horizontal + complexity.
- **Google, Facebook, Netflix**: Full horizontal at scale. Commodity servers, massive fleets.

### Interview Phrase Template

> "I'd start with vertical scaling for simplicity — a larger RDS instance handles our initial load with no code changes. As we grow past ~100K DAU, I'd introduce read replicas to handle the read-heavy workload and add horizontal app servers behind a load balancer. Sharding becomes necessary only if write QPS exceeds what a single primary DB instance can handle, which I'd estimate happens around [X] QPS based on our write pattern."

---

## Monolith vs Microservices

> **Analogy: A Swiss Army knife vs a professional kitchen.**
>
> A Swiss Army knife (monolith) does everything in one tool. Easy to carry, simple to use, great for camping. But you wouldn't run a Michelin-star restaurant with it — you need specialized knives, each maintained by a skilled expert, each optimized for one job.
>
> A professional kitchen (microservices) has specialists: the pastry chef, the saucier, the grill cook. Each can be replaced, upgraded, or scaled independently. But coordinating 20 specialists requires a head chef, clear protocols, and communication overhead a camper never needs.

### Decision Criteria

**Choose Monolith when:** your team is small, the domain is not yet well understood, or the operational overhead of microservices would exceed its benefits.

**Choose Microservices when:** different parts of the system need to scale independently, multiple teams own different domains, or deployment cadences differ significantly per component.

### Monolith

```
All functionality in one deployable unit.

Pros:
- Simple to develop, test, and deploy
- No network latency between components (function calls)
- Easy transactions (no distributed transaction problem)
- One codebase, one language, one deployment

Cons:
- Entire application re-deploys for any change
- One failure can crash everything
- Hard to scale individual hot components
- Technology lock-in across entire system

Use when:
- Small team (<10 engineers)
- New product (domain not yet understood)
- Prototype / startup MVP
- Low traffic
```

### Microservices

```
Domain-separated, independently deployable services.

Pros:
- Scale each service independently (video transcoding needs more CPUs than auth)
- Independent deployment (User service deploys without touching Order service)
- Technology heterogeneity (Python for ML, Go for high-throughput, Java for legacy)
- Fault isolation (payment service failure doesn't take down the catalog)
- Team autonomy (each team owns one service end-to-end)

Cons:
- Distributed systems problems (network failures, latency, partial failures)
- Distributed transactions are hard (two-phase commit, saga pattern)
- Service discovery, circuit breaking, observability complexity
- Higher operational overhead (many deployments, many logs, many monitors)

Use when:
- Multiple independent teams
- Clear domain boundaries (DDD — Domain-Driven Design)
- Parts of the system have very different scaling needs
- High organizational velocity needed
```

### Migration Pattern

```
Don't start with microservices. Start with a well-structured monolith.
When you feel the pain, extract services surgically:

Step 1: Identify pain point (e.g., video transcoding is slow and blocks deploys)
Step 2: Extract to a service (VideoService), communicate via queue
Step 3: Stabilize the boundary
Step 4: Repeat only where needed

"Monolith first, then extract" → avoids premature microservice complexity
```

### Real-World Examples

- **Amazon**: Monolith until ~2001. Jeff Bezos mandated "API mandate" — all teams expose services via APIs. Transitioned to microservices over years.
- **Shopify**: Runs a large, well-structured Ruby on Rails monolith serving billions in GMV. Has selectively extracted services. Proof that monolith scales with care.
- **Netflix**: Full microservices. 700+ services. Invented much of the tooling (Hystrix, Eureka, Zuul) to manage the complexity.

### Interview Phrase Template

> "Given that this is a new product and the team is 5 engineers, I'd start with a well-structured monolith. The domain is not fully understood yet, and the overhead of managing multiple services would slow us down. I'd design clean internal module boundaries so that when we do need to extract services — likely starting with [video transcoding / notification delivery / search] — the seams are already well-defined."

---

## Stateful vs Stateless

> **Analogy: A personal shopper who remembers all your preferences vs a vending machine.**
>
> The personal shopper (stateful) remembers your size, your past purchases, your allergies. Every interaction is richer because of accumulated context. But if your personal shopper gets sick, that context is gone. And you can't have 50 personal shoppers covering for each other because none of them know you.
>
> A vending machine (stateless) treats every customer identically. It has no memory of you. Put in a coin, get a snack — fully self-contained. You can have 1,000 identical vending machines, any one of which can serve you equally well. Scaling is trivial.

### Decision Criteria

**Choose Stateless when:** you need to scale horizontally, have multiple server instances, or want simple load balancing.

**Choose Stateful when:** the interaction is inherently session-dependent, long-running, or streaming — and you have strategies to handle failover.

### Stateless

```
Each request contains all information needed to process it.
Server holds no client-specific memory between requests.

Examples:
- REST APIs with JWT (the token carries identity and claims)
- HTTP request handlers (process and forget)
- Serverless functions (Lambda, Cloud Functions)

Pros:
- Any server handles any request → perfect horizontal scaling
- Simple load balancing (no sticky sessions)
- Easy fault recovery (failed node → requests rerouted instantly)

Cons:
- Larger request payloads (must send context each time)
- No server-side session state → client must manage its own state

Pattern: Externalize all state
- Session data → Redis (shared across all servers)
- User state → Database
- Tokens → Signed JWT (self-contained)
```

### Stateful

```
Server maintains client-specific state across multiple requests.

Examples:
- WebSocket connections (server tracks each connected client)
- Game servers (each player's position/state kept in memory)
- Streaming services (ongoing transcoding job context)

Pros:
- Richer, lower-latency interaction (no round-trips to DB for context)
- Natural for streaming/bidirectional protocols

Cons:
- Horizontal scaling requires sticky sessions or a shared state layer
- Node failure loses in-flight state (requires replication or recovery)
- Load balancing is complex (can't send user A to server B mid-session)
```

### Making Stateful Systems Scale

```
Problem: WebSocket chat server. User A is connected to Server 1.
         User B is connected to Server 2. A sends B a message.
         Server 1 can't push to Server 2's connection.

Solution: Externalize the "who is connected where" state.
- Each server publishes incoming messages to Redis Pub/Sub
- All servers subscribe to Redis
- Server 2 sees the message and pushes it to User B's WebSocket

The servers are now effectively stateless regarding message routing.
```

### Interview Phrase Template

> "I'll design the API layer as stateless — JWT carries all needed claims, no session lookups required. Any server can handle any request, which makes horizontal scaling and load balancing trivial. For the WebSocket connections, which are inherently stateful, I'll use Redis Pub/Sub as a message bus so that a message received by any server instance is fanned out to the correct connected client regardless of which server they're connected to."

---

## Normalization vs Denormalization

> **Analogy: A library card catalog vs a book with all the index entries photocopied into the back of every relevant book.**
>
> The catalog (normalized) has one authoritative entry per book. Update the author's name in one place, it's updated everywhere. But finding "all books by this author published after 2010 in the genre Mystery" requires cross-referencing multiple cards.
>
> Photocopied index pages (denormalized) mean every book carries its own pre-assembled context. Looking up related books is fast — it's right there. But if the author changes their name, you need to update every book that mentioned them.

### Normalization (SQL Philosophy)

```
Avoid data redundancy. Split into multiple tables.

Example: E-commerce
Users:    user_id | name       | email
Orders:   order_id | user_id | product_id | quantity
Products: product_id | name | price

Pros:
- Single source of truth (update name in one row)
- Smaller total storage
- Easy writes (no cascading updates)

Cons:
- JOINs required for reads (slower)
- Complex queries for nested data

Use when: OLTP (many writes), data integrity critical, storage constrained
```

### Denormalization (NoSQL Philosophy)

```
Duplicate data for faster reads. Embed related data in one document.

Example: Same order stored as one MongoDB document:
{
  "order_id": "101",
  "user": { "id": "1", "name": "Alice", "email": "alice@example.com" },
  "product": { "id": "501", "name": "Laptop", "price": 1200 },
  "quantity": 2,
  "total": 2400
}

Pros:
- No JOINs → faster reads
- One document = one network round-trip
- Ideal for read-heavy workloads

Cons:
- Data redundancy (Alice's name in every order)
- Complex updates (Alice changes her email → update all orders)
- More storage

Use when: Read-heavy systems, latency critical, storage cheap
```

### Real-World Examples

- **PostgreSQL order history**: Normalized. Correct name displayed even if user changed email after ordering.
- **MongoDB product catalog**: Denormalized. "All specs in one document" for a fast API response.
- **Redis leaderboard**: Fully denormalized. Pre-computed score + username stored together.

### Interview Phrase Template

> "The order records in PostgreSQL will be normalized — user and product records live in their own tables. For the product catalog API, I'll denormalize into MongoDB documents so a single document read returns everything a product page needs without JOINs. The data changes infrequently, so update propagation isn't a burden."

---

## Caching Strategies

> **Analogy: Your desk vs the filing room.**
>
> Your desk (cache) has the documents you've touched recently. Reaching for them is instant. But the desk has limited space, so eventually you file things back (eviction). The filing room (database) has everything, but walking there takes time.

### Cache-Aside (Lazy Loading)

```
Application manages the cache explicitly.

def get_user(user_id):
    user = cache.get(user_id)      # Check cache first
    if user:
        return user                 # Cache hit
    user = db.query(user_id)        # Cache miss → DB
    cache.set(user_id, user, ttl=3600)
    return user

Analogy: You check your desk before walking to the filing room.
         Only fetch what you actually need.

Pros: Only caches requested data. Cache failure doesn't break app.
Cons: First request is always slow (cache miss). Stale data possible.
Use when: Read-heavy, cache misses are tolerable, unpredictable access patterns.
```

### Write-Through Cache

```
Write to cache and DB simultaneously.

def update_user(user_id, data):
    db.update(user_id, data)
    cache.set(user_id, data)

Analogy: You update the file AND immediately put a copy on your desk.
         Your desk is always current.

Pros: Cache always fresh. No stale reads after writes.
Cons: Higher write latency. Cache fills with data that may never be read.
Use when: Read/write ratio is high. Freshness is critical (e.g., pricing data).
```

### Write-Behind (Write-Back)

```
Write to cache immediately, async to DB.

def update_user(user_id, data):
    cache.set(user_id, data)                     # Instant
    queue.enqueue("persist_user", user_id, data) # Async

Analogy: You update your desk immediately but only file it at end of day.
         Risk: if the office burns down before end of day, the file is lost.

Pros: Very low write latency.
Cons: Data loss risk if cache fails before DB write. Complex.
Use when: Write-heavy workloads where some data loss is acceptable (counters, analytics).
```

### Cache Eviction Policies

```
LRU (Least Recently Used): Evict what hasn't been accessed longest.
  → Best default for most use cases.

LFU (Least Frequently Used): Evict what's been accessed least often.
  → Better for skewed access patterns (viral content spikes).

TTL (Time to Live): Evict after a fixed duration regardless of access.
  → Best for data with a natural freshness window (session tokens, API responses).
```

### Interview Phrase Template

> "I'd use cache-aside with a 1-hour TTL for user profile reads — it's read-heavy, misses are rare after warm-up, and slight staleness is fine. For product pricing, I'd use write-through cache — we can't serve stale prices to users making purchase decisions. The slightly higher write latency is acceptable."

---

## Message Queues

> **Analogy: A restaurant order ticket system.**
>
> The waiter doesn't stand in the kitchen until your food is ready. They hand the order ticket to the kitchen (the queue) and go take more orders. The kitchen processes tickets at its own pace. If it gets busy, tickets pile up rather than waiters getting stuck. If a chef makes a mistake, the ticket can be retried.

### When to Use Message Queues

```
- Decouple producers from consumers (order service doesn't care if email service is slow)
- Buffer traffic spikes (10K orders/sec arrive, but payment service processes 1K/sec)
- Async processing (don't block user while sending emails, resizing images)
- Retry failed operations (message stays in queue until processed successfully)
- Fan-out (one order → payment service + email service + inventory service)
```

### SQS vs Kafka vs RabbitMQ

| Feature | SQS | Kafka | RabbitMQ |
|---------|-----|-------|----------|
| **Type** | Managed queue | Distributed log | Message broker |
| **Throughput** | Moderate | Very high (millions/sec) | Moderate |
| **Ordering** | FIFO (optional) | Per partition | Yes |
| **Retention** | Up to 14 days | Configurable (retain forever) | Until consumed |
| **Replay** | No | Yes (rewind to any offset) | No |
| **Use Case** | Simple async jobs, AWS-native | Event streaming, audit log, ETL | Complex routing, RPC patterns |
| **Analogy** | Ticket box | Tape recorder | Smart PBX phone system |

### Real-World Examples

- **Order Processing**: User creates order → publish to SQS → Payment service, Email service, Inventory service each consume independently.
- **Video Upload**: User uploads → publish to Kafka → Transcoding workers, Thumbnail generator, Content moderation each subscribe.
- **Audit Log**: Every write event published to Kafka. Retained 90 days. Replay for compliance auditing.

### Interview Phrase Template

> "I'd use SQS for the order-to-fulfillment pipeline — it's simple, managed, and we don't need replay. For the analytics pipeline where we need to replay events for backfills and new ML model training, I'd use Kafka with configurable retention. The key win is decoupling: if the email service goes down for 30 minutes, orders still process and emails are delivered once it recovers."

---

## API Design

> See also: networking.md for full analogies (REST = fixed menu, GraphQL = custom order, gRPC = walkie-talkie).

### REST vs GraphQL vs gRPC

| Factor | REST | GraphQL | gRPC |
|--------|------|---------|------|
| **Protocol** | HTTP/1.1 | HTTP/1.1+ | HTTP/2 |
| **Data Format** | JSON | JSON | Protocol Buffers (binary) |
| **Over/Under-fetching** | Common | Eliminated | N/A (method-based) |
| **Performance** | Good | Good | Excellent |
| **Caching** | Easy (HTTP verbs/ETags) | Hard (POST-based) | No HTTP caching |
| **Browser Support** | Excellent | Good | Needs proxy (gRPC-Web) |
| **Best For** | Public APIs, CRUD | Mobile/BFF, flexible clients | Internal microservices |
| **Contract** | OpenAPI / Swagger | GraphQL Schema | `.proto` file |

### Decision Criteria

**Use REST when:** building a public API, exposing CRUD operations, or browser clients need to consume it directly.

**Use GraphQL when:** building a Backend-for-Frontend (BFF), mobile apps that need to minimize payload, or multiple clients need different views of the same data.

**Use gRPC when:** calling between internal services where you control both ends, need bi-directional streaming, or maximum throughput/minimum latency matters.

### Interview Phrase Template

> "The public API used by third-party developers will be REST — it's universally understood and documentation tooling (OpenAPI, Postman) is excellent. The mobile app has a GraphQL BFF layer — it lets the app fetch exactly what it needs without multiple round-trips or over-fetching on a limited data connection. Internal service communication uses gRPC — we own both ends, Protobuf is faster than JSON, and we use server streaming for real-time updates between services."

---

## Summary: Key Interview Phrases

**On every trade-off, structure your answer as:**

```
1. State what you chose
2. Why it fits THIS use case (not why it's generally good)
3. What you give up (the trade-off)
4. At what point you'd reconsider (scaling trigger)
```

**Example answers:**

| Decision | Phrase |
|----------|--------|
| SQL vs NoSQL | "SQL for payments (ACID), NoSQL for catalog (flexible schema)" |
| Sync vs Async | "Sync for checkout (user waits), async for email/transcoding (background)" |
| Push vs Pull | "WebSockets for chat (real-time), polling for feed refresh (simplicity)" |
| Cache strategy | "Write-through for pricing (freshness), cache-aside for profiles (lazy)" |
| Monolith vs Microservices | "Monolith to start, extract only where independent scaling is needed" |
| Stateless vs Stateful | "Stateless app layer, externalize session state to Redis" |
| Consistent vs Available | "CP for payments, AP for feeds — match CAP choice to business impact of wrong data" |

**Always explain trade-offs. Never just say "I'd use X." Say "I'd use X because Y, accepting the trade-off of Z."**
