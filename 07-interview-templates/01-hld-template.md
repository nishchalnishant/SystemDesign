---
module: 07-interview-templates
status: unread
tags: [07-interview-templates, system-design, interview-templates]
---
# HLD Interview Framework (45-60 min)

## The Mental Model

You're an architect hired to design a skyscraper. You don't start by picking the brand of nails. You start with: How many floors? How many occupants? What's the load-bearing requirement? Then: foundation, structure, core systems. Then details.

In HLD, the interviewer is the client. They have a vague ask ("build Twitter"). Your job is to turn that vague ask into a concrete system — through structured questions, estimates, and component diagrams — before writing a single box on the whiteboard.

The interviewer is watching:
1. Do you ask the right questions before designing?
2. Can you make reasonable trade-offs and defend them?
3. Do you know the standard patterns cold?
4. Can you go deep when asked?

---

## Template Mindmap

```
HLD Interview Framework (45-60 min)
├── Core Problem
│   └── Turn a vague "build X" prompt into a concrete, defensible system design
├── Phase 1 — Requirements (0-10 min)
│   ├── Functional → what must the system do (core features only)
│   ├── Non-functional → latency, availability, consistency, durability targets
│   └── PASS-R → Performance, Availability, Scalability, Security, Reliability
├── Phase 2 — Capacity Estimation (10-15 min)
│   ├── QPS = DAU × requests/day / 86400
│   ├── Storage = write QPS × object size × retention days
│   └── Bandwidth = read QPS × response size
├── Phase 3 — API Design (15-20 min)
│   ├── REST endpoints per core feature
│   ├── Request/response shapes — include pagination tokens
│   └── Auth strategy — JWT, API key, OAuth
├── Phase 4 — Data Model (20-25 min)
│   ├── SQL vs NoSQL decision → justify with access pattern
│   ├── Key entities and relationships
│   └── Indexing strategy → primary key, secondary indexes
├── Phase 5 — High-Level Architecture (25-35 min)
│   ├── Component diagram: clients → LB → API servers → DB/cache/queue
│   ├── Data flow for the primary write path and primary read path
│   └── Where async processing fits (background jobs, message queues)
├── Phase 6 — Deep Dives (35-50 min)
│   ├── Pick the hardest sub-problem: fan-out, search, consistency
│   └── Show you can go from box on whiteboard to working design
├── Phase 7 — Bottlenecks & Trade-offs (50-60 min)
│   ├── Identify SPOFs and mitigation
│   └── State trade-offs explicitly — never pretend there are none
├── When to Use
│   └── ✓ Any "design a large-scale system" interview question
└── Interview Angles
    ├── "What would you change at 10× scale?" → horizontal scale, caching, sharding
    ├── "What breaks first?" → DB write path, fan-out, single-region deployment
    └── "How do you handle failures?" → retries, circuit breakers, graceful degradation
```

---

## Interview Flow Timeline

| Phase | Time | Activity |
|-------|------|----------|
| **1. Requirements** | 0-10 min | Functional + non-functional, PASS-R framework |
| **2. Capacity Estimation** | 10-15 min | QPS, storage, bandwidth, cache |
| **3. API Design** | 15-20 min | REST endpoints, request/response shapes |
| **4. Data Model** | 20-25 min | SQL vs NoSQL decision, schema |
| **5. High-Level Architecture** | 25-35 min | Component diagram, data flow |
| **6. Deep Dives** | 35-50 min | Scale, caching, failures, trade-offs |
| **7. Wrap-Up** | 50-60 min | Summary, trade-offs, future improvements |

---

## Phase 1: Requirements (0-10 min)

### Why this phase matters
Every minute you spend here saves three minutes of redesign later. Interviewers deliberately give underspecified problems. Asking the right questions signals senior thinking.

### Functional Requirements

Ask these in order — stop when you have enough to design around:

```
1. What are the 3-5 core features? (not a feature wishlist)
2. Who are the users? (B2C consumers, B2B, internal tool?)
3. What is the primary read/write pattern? (read-heavy, write-heavy, balanced?)
4. What can we explicitly cut for this interview? (scope management)
```

**What NOT to ask (wastes time):**
- "Should we support dark mode?" — irrelevant to HLD
- "What programming language?" — irrelevant unless asked
- "Do we need logging?" — assumed yes, don't ask
- 10 edge-case questions before establishing core scope

**Example — "Design Twitter":**
```
Functional (must-have):
  - Post a tweet (text, images, video)
  - Follow / unfollow users
  - View home timeline (tweets from followed users)
  - View user profile + their tweets

Should-have:
  - Like, retweet, reply
  - Notifications

Out-of-scope for this session:
  - Search
  - Direct messages
  - Trending topics
  - Ad serving
```

### Non-Functional Requirements — PASS-R Framework

State these out loud. The interviewer may correct you, which is useful.

**P — Performance**
- What is the acceptable read latency? (e.g., timeline load < 200ms P99)
- Write latency? (tweets can tolerate 500ms)

**A — Availability**
- 99.9% = 8.7 hours/year downtime (acceptable for most)
- 99.99% = 52 min/year (payments, healthcare — much harder)
- Can reads and writes fail independently?

**S — Scalability**
- Current scale vs. projected scale (e.g., 10M users → 500M in 2 years)
- Peak vs. average load (3x spike during live events)
- Read-to-write ratio (Twitter: ~100:1, Dropbox: ~1:10)

**S — Security**
- Authentication required? (OAuth 2.0, JWT)
- Any PII / GDPR considerations?
- Rate limiting on public endpoints?

**R — Reliability**
- Can we lose any data? (tweets: no; likes: maybe; analytics: eventually consistent OK)
- Consistency model: strong vs. eventual? (for Twitter timelines: eventual is fine)

**Example — Twitter PASS-R:**
```
P: Timeline load < 200ms P99; tweet post < 500ms
A: 99.99% for read (timeline), 99.9% for write (posting)
S: 300M DAU, 500K tweets/day, peak 3x (news events)
   Read:write ratio = 100:1
S: Auth required (OAuth). Rate limit API to 300 req/15min
R: No tweet data loss. Timeline eventual consistency OK.
```

### Phrase to use at end of Phase 1:
> "Based on what you've told me, here's how I'm scoping this: [repeat back]. Does that match your expectations before I continue?"

---

## Phase 2: Capacity Estimation (10-15 min)

### When to skip
If the interviewer says "don't worry about estimates" or "focus on the design" — skip this entirely. Don't insist.

### When to do it
Always do a quick back-of-envelope when scale is a key part of the problem (sharding decisions, cache sizing, CDN need).

### Key Numbers to Have Cold

```
TIME:
  1 day = 86,400 seconds ≈ 100K seconds
  1M requests/day ≈ 12 req/sec
  1B requests/day ≈ 12K req/sec

STORAGE SIZES:
  1 char = 1 byte
  Tweet text (280 chars) ≈ 280 bytes
  Average URL = 2 KB
  Profile photo thumbnail = 50 KB
  HD photo = 2 MB
  1 min video (compressed) = 10 MB

LATENCY ORDER OF MAGNITUDE:
  L1 cache: 1 ns
  RAM read: 100 ns
  SSD random read: 150 µs
  Network same datacenter: 0.5 ms
  Network cross-region: 50-150 ms
  Disk seek: 10 ms
```

### Estimation Template (Twitter example)

**Step 1 — Writes (QPS):**
```
500K tweets/day
= 500,000 / 86,400
≈ 6 writes/sec (average)
Peak (3x): ~18 writes/sec
```

**Step 2 — Reads (QPS):**
```
Read:write = 100:1
→ 600 reads/sec average
→ 1,800 reads/sec peak

Timeline generation (fanout):
  Average user follows 200 people
  Each tweet fans out to 200 followers
  = 6 writes/sec × 200 = 1,200 timeline updates/sec
```

**Step 3 — Storage (5 years):**
```
Per tweet:
  text: 280 bytes
  metadata (user_id, timestamp, likes): ~500 bytes
  Total: ~1 KB/tweet

Tweets in 5 years:
  500K/day × 365 × 5 = 912M tweets
  912M × 1 KB = ~1 TB tweet data
  + media (assume 10% tweets have images at 2MB avg):
    91M images × 2 MB = 182 TB
  With replication (3x): ~550 TB total
```

**Step 4 — Cache sizing (80/20 rule):**
```
20% of tweets = 80% of reads
Top 20% = 182M tweets × 1 KB = ~180 GB
→ Feasible to cache hot tweets in memory
```

### Phrase to use:
> "These are rough numbers — I'm using them to drive architecture decisions, not for precision. The key insight here is [X], which means we need [Y]."

---

## Phase 3: API Design (15-20 min)

### Goal
Show you can design a clean, versioned API before diving into internals. Don't over-specify — just enough to establish the contract.

### REST Naming Rules (non-negotiable)
- Use nouns, not verbs: `/tweets` not `/createTweet`
- Plural for collections: `/users`, `/tweets`
- Hierarchical for owned resources: `/users/{id}/tweets`
- Always version: `/api/v1/...`

### What level of detail to go to
- Show request body and key response fields
- Show the HTTP method and status code
- Do NOT write full OpenAPI specs — that's over-engineering in an interview

### Example — Twitter API

```
POST /api/v1/tweets
Auth: Bearer token required
Request:
{
  "text": "Hello world",
  "media_ids": ["media_123"]  // optional
}
Response: 201 Created
{
  "tweet_id": "1234567890",
  "text": "Hello world",
  "author_id": "user_456",
  "created_at": "2026-05-12T10:00:00Z",
  "likes": 0,
  "retweets": 0
}

GET /api/v1/timelines/home
Auth: Bearer token required
Query params: ?limit=20&cursor=<pagination_token>
Response: 200 OK
{
  "tweets": [...],
  "next_cursor": "abc123",
  "has_more": true
}

POST /api/v1/users/{user_id}/follows
Auth: Bearer token required
Request: {}  // target is the user_id in path
Response: 200 OK

DELETE /api/v1/users/{user_id}/follows/{target_user_id}
Response: 204 No Content
```

**Standard error codes to mention:**
```
400 Bad Request    — malformed input
401 Unauthorized   — missing/invalid token
403 Forbidden      — valid token, wrong permissions
404 Not Found      — resource doesn't exist
409 Conflict       — duplicate (e.g., already following)
429 Too Many Req   — rate limit hit
500 Internal Error — server fault
503 Unavailable    — temporary outage
```

---

## Phase 4: Data Model (20-25 min)

### SQL vs NoSQL Decision (say this out loud)

Use this decision tree:

```
Need ACID transactions?                      → SQL
Data is highly relational (joins)?          → SQL
Schema is fixed and well-defined?           → SQL
Need horizontal write scaling?              → NoSQL
Data is key-value or document-oriented?     → NoSQL
Need massive scale with eventual consistency? → NoSQL
Access pattern is primary key lookups only? → NoSQL
```

**Twitter decision:**
- Tweets: NoSQL (key-value by tweet_id, massive scale)
- User relationships (follows): Graph DB or NoSQL
- User accounts: SQL (structured, ACID needed for auth)

### Schema Template — Twitter

**SQL (PostgreSQL) — Users:**
```sql
CREATE TABLE users (
    user_id     BIGSERIAL PRIMARY KEY,
    username    VARCHAR(50) UNIQUE NOT NULL,
    email       VARCHAR(255) UNIQUE NOT NULL,
    password_hash TEXT NOT NULL,
    bio         TEXT,
    created_at  TIMESTAMPTZ DEFAULT NOW(),
    INDEX idx_username (username)
);

CREATE TABLE follows (
    follower_id BIGINT REFERENCES users(user_id),
    followee_id BIGINT REFERENCES users(user_id),
    created_at  TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY (follower_id, followee_id)
);
```

**NoSQL (DynamoDB / Cassandra) — Tweets:**
```
Table: tweets
Partition Key: tweet_id (UUID or Snowflake ID)
Attributes:
  - author_id (String)
  - text (String, max 280 chars)
  - media_urls (List<String>)
  - created_at (Number — Unix epoch ms)
  - like_count (Number)
  - retweet_count (Number)

GSI: author_id-created_at-index
  - Partition Key: author_id
  - Sort Key: created_at (DESC for profile page)
  - Purpose: "Get all tweets by user X"
```

**NoSQL — Timeline (pre-computed fan-out):**
```
Table: timelines
Partition Key: user_id
Sort Key: tweet_id (Snowflake — encodes timestamp)
Attributes:
  - author_id
  - text (denormalized for fast reads)
  - created_at

Purpose: Home timeline reads are O(1) lookups
```

### Indexing to mention:
- Primary key — always O(1) lookups
- GSI / secondary index — secondary access patterns
- TTL on timelines (evict old entries after 30 days)

---

## Phase 5: High-Level Architecture (25-35 min)

### Components to always include
Never omit these — each has a reason:

| Component | Why |
|-----------|-----|
| CDN | Static assets, edge caching, DDoS shield |
| Load Balancer | Horizontal scaling, health checks |
| App Servers (stateless) | Scale out easily, no session state |
| Cache (Redis) | Absorb read load, sub-millisecond latency |
| Primary DB | Source of truth for writes |
| Message Queue | Decouple write path from fan-out |
| Read Replicas | Scale read throughput independently |
| Object Storage (S3) | Media files, blobs |

### Twitter Architecture Diagram

```
                        ┌──────────────┐
                        │   Clients    │
                        │(Web, iOS, Android)
                        └──────┬───────┘
                               │
                        ┌──────▼───────┐
                        │     CDN      │  ← static assets, cached API responses
                        └──────┬───────┘
                               │
                        ┌──────▼───────┐
                        │Load Balancer │  ← health checks, SSL termination
                        └──┬───────┬───┘
                           │       │
               ┌───────────▼┐     ┌▼───────────┐
               │ Write API  │     │  Read API  │  ← separate services
               │ (tweet,    │     │ (timeline, │
               │  follow)   │     │  search)   │
               └─────┬──────┘     └─────┬──────┘
                     │                  │
          ┌──────────▼──┐         ┌─────▼──────────┐
          │  Message    │         │  Redis Cache   │
          │  Queue      │         │  (timelines,   │
          │  (Kafka)    │         │   tweet data)  │
          └──────┬───────┘         └─────┬──────────┘
                 │                       │ cache miss
          ┌──────▼───────┐         ┌─────▼──────────┐
          │ Fan-out      │         │   Cassandra    │
          │ Service      │         │   (tweets,     │
          │ (async)      │         │    timelines)  │
          └──────┬───────┘         └────────────────┘
                 │
      ┌──────────▼────────────┐
      │  Timeline Write Workers│  ← push tweet to each follower's timeline
      └──────────┬─────────────┘
                 │
          ┌──────▼───────┐
          │   Cassandra  │
          │  (timelines) │
          └──────────────┘

Separate:
  ┌──────────────┐     ┌──────────────┐
  │  PostgreSQL  │     │     S3       │
  │  (users,     │     │  (photos,   │
  │   follows)   │     │   videos)   │
  └──────────────┘     └──────────────┘
```

### Data Flow — Tweet Post (Write Path)
```
1. Client → POST /api/v1/tweets
2. Load Balancer → Write API server
3. Write API:
   a. Validate auth token
   b. Validate input (length, media)
   c. Generate Snowflake tweet_id
   d. Write tweet to Cassandra (tweet table)
   e. Publish event to Kafka: {tweet_id, author_id, follower_list}
4. Return 201 to client immediately (fast path done)

Async (Kafka consumers):
5. Fan-out Service reads event
6. Fetches follower list from PostgreSQL
7. Writes tweet_id to each follower's timeline in Cassandra
   (Celebrity problem: skip for users with >1M followers — pull instead)
```

### Data Flow — Timeline Read (Read Path)
```
1. Client → GET /api/v1/timelines/home?cursor=X
2. Load Balancer → Read API server
3. Read API:
   a. Check Redis: GET timeline:{user_id}
   b. Cache HIT → return top-N tweets (90%+ of requests)
   c. Cache MISS → query Cassandra timeline table
   d. Hydrate with tweet details (Cassandra tweet table)
   e. Write back to Redis (TTL: 24h)
4. Return paginated tweets
```

### Common Patterns to Name-Drop

**Fan-out on Write** (what Twitter uses for most users)
- Push tweet to all followers' timelines at write time
- Pros: fast reads (timeline is pre-computed)
- Cons: expensive for celebrities (Lady Gaga → 100M writes per tweet)

**Fan-out on Read** (for celebrities / hybrid)
- Don't pre-compute; merge at read time
- Pros: cheap writes
- Cons: slow reads if following many celebrities

**CQRS (Command Query Responsibility Segregation)**
- Separate write path from read path (different services, different DBs)
- Twitter does this: write API vs. read API

**Event-Driven Architecture**
- Decouple services via Kafka; fan-out service subscribes to tweet events

---

## Phase 6: Deep Dives (35-50 min)

### How to pick what to deep-dive on

At minute 35, say: *"I want to deep-dive on the areas you care most about. My top candidates are: [1] the fan-out service and celebrity problem, [2] the caching strategy, [3] failure handling. Which would you like to focus on?"*

This shows initiative and gives the interviewer control. Do NOT silently pick one and start — confirm with them.

### Deep Dive: Caching Strategy

**Cache-Aside (Lazy Loading):**
```python
def get_timeline(user_id, limit=20):
    cache_key = f"timeline:{user_id}"
    
    # Check cache
    cached = redis.lrange(cache_key, 0, limit - 1)
    if cached:
        return cached
    
    # Cache miss — query DB
    tweets = cassandra.query(
        "SELECT * FROM timelines WHERE user_id=? ORDER BY tweet_id DESC LIMIT ?",
        [user_id, limit]
    )
    
    # Warm cache (TTL: 24h)
    redis.rpush(cache_key, *tweets)
    redis.expire(cache_key, 86400)
    return tweets
```

**Cache Eviction Policies:**
- LRU: evict least recently accessed (good for timelines)
- TTL: expire after N seconds (good for tweet data)
- LFU: evict least frequently accessed (good for media cache)

**Cache Stampede Problem + Fix:**
```
Problem: 1000 requests hit same cache key at expiry simultaneously
         → all 1000 go to DB

Fix 1: Mutex lock on cache miss (only one request fetches from DB)
Fix 2: Probabilistic early expiration (refresh before expiry)
Fix 3: Background refresh (never let cache expire, async refresh)
```

### Deep Dive: Scaling the Database

**When to shard:**
- Single node can't handle write QPS (>10K writes/sec on commodity hardware)
- Single node storage exceeds ~10TB

**Sharding Strategy — Tweets:**
```
Shard by: hash(tweet_id) % num_shards

Shard 0: tweet_ids [0, 1B)
Shard 1: tweet_ids [1B, 2B)
Shard 2: tweet_ids [2B, 3B)

Pros: Even distribution, predictable shard for any tweet_id
Cons: Can't easily do range queries across shards
      Resharding is painful (consistent hashing mitigates this)
```

**Read Replicas:**
```
Primary:    All writes
Replica ×3: All reads (timeline queries, profile lookups)
Replication lag: ~100ms (acceptable for eventual consistency)

Rule: never read from primary unless you need latest data
```

### Deep Dive: Failure Scenarios

Always address these three — even briefly:

**1. What if the fan-out service goes down?**
- Kafka retains events with configurable retention (7 days default)
- Fan-out service resumes processing from last committed offset
- Timelines may lag but will catch up; no data loss

**2. What if Redis goes down?**
- Read API falls back to Cassandra (higher latency, but functional)
- Add Redis Sentinel or Redis Cluster for HA
- Warm cache on restart from DB

**3. What if a DB shard goes down?**
- Cassandra: multi-region replication, quorum reads/writes
- 1 node down → queries route to replicas (RF=3, quorum=2)
- Automatic recovery when node rejoins

### Deep Dive: Celebrity Problem (Twitter-specific)

```
Normal user (500 followers):
  Post tweet → fan-out to 500 timeline rows = trivial

Celebrity (100M followers):
  Post tweet → fan-out to 100M timeline rows = minutes of lag

Solution — Hybrid Fan-out:
  IF follower_count < threshold (e.g., 1M):
    Fan-out on write (push to timelines)
  ELSE:
    Fan-out on read (fetch at read time, merge with pre-computed timelines)

At read time for home timeline:
  1. Fetch user's pre-computed timeline (from Cassandra)
  2. Identify followed celebrities (from a separate celebrity set)
  3. Fetch celebrity tweets directly (by author_id)
  4. Merge + sort by timestamp
  5. Cache merged result in Redis
```

---

## Phase 7: Wrap-Up (50-60 min)

### Summary Template (30 seconds, out loud)

> "To summarize: we designed a Twitter-like system handling 300M DAU with ~600 reads/sec and ~6 writes/sec. Key decisions: (1) separate read and write paths (CQRS), (2) fan-out on write for normal users with hybrid pull for celebrities, (3) Redis for pre-computed timelines giving sub-millisecond reads, (4) Kafka to decouple the write path from fan-out. The main trade-off is write amplification from fan-out — we accept that cost to keep reads fast."

### Trade-offs to Mention

| Decision | Trade-off Made | Alternative Considered |
|----------|---------------|----------------------|
| Fan-out on write | Fast reads, expensive writes | Fan-out on read (slower reads) |
| Eventual consistency on timelines | Better availability | Strong consistency (much harder) |
| Cassandra for tweets | High write throughput, tunable consistency | PostgreSQL (stronger guarantees, harder to scale) |
| Pre-computed timelines | Wasted storage, staleness risk | On-demand assembly (slower reads) |
| Kafka for async fan-out | Decoupled, resilient | Synchronous fan-out (simpler but fragile) |

### Future Improvements to Mention

> "Given more time, I'd look at: (1) search — Elasticsearch for full-text tweet search, (2) rate limiting — token bucket at the load balancer layer, (3) content moderation pipeline as a separate async service consuming from Kafka, (4) geo-distributed deployment with regional Cassandra clusters and cross-region replication."

---

## Common Interview Traps

1. **Jumping to architecture before requirements** — design the wrong thing, then can't pivot
2. **No capacity estimation → no justification for choices** — why do you need a cache? prove it
3. **Using only one database for everything** — shows shallow knowledge of storage options
4. **Ignoring the read/write ratio** — completely changes the architecture (fan-out exists because of this)
5. **No cache invalidation strategy** — adding Redis without explaining when data goes stale
6. **Forgetting async/queues** — everything synchronous at scale → cascading failures
7. **No failure discussion** — happy path only signals junior thinking
8. **Over-designing** — 12 microservices for a URL shortener; complexity must match scale
9. **Treating all users the same** — no awareness of power users / celebrities / hot partitions
10. **No trade-off discussion** — every decision has a cost; never say "I'd use X" without "because... and the trade-off is..."

---

## Interview Phrases That Land Well

**Phase 1 (Requirements):**
- "Before I start designing, let me clarify scope — I want to make sure I'm solving the right problem."
- "For this session, I'll assume X. Can you confirm that's in scope?"
- "I'll deprioritize Y for now — we can revisit if time allows."

**Phase 2 (Estimation):**
- "These numbers are rough orders of magnitude — I'm using them to drive architecture decisions."
- "The key insight from this estimate is [X], which means we need [Y]."

**Phase 3-4 (API + Data Model):**
- "I'm choosing NoSQL here because our access pattern is purely key-based and we need horizontal write scaling — we don't need joins."
- "I'll use a Snowflake ID here instead of UUID — it encodes timestamp which gives us natural ordering and avoids hotspots on the partition key."

**Phase 5 (Architecture):**
- "I'm separating the read and write paths here — this is CQRS — because the read:write ratio is 100:1 and they have different scaling requirements."
- "I'd use Kafka here to decouple the write path from the fan-out. This means a tweet write returns fast, and the fan-out happens asynchronously."

**Phase 6 (Deep Dives):**
- "There's a trade-off here between consistency and availability. For timelines, I'd accept eventual consistency to get higher availability."
- "The celebrity problem is a classic hot partition issue. The solution is a hybrid fan-out..."
- "If the cache goes down, we fall back to the database — higher latency, but the system stays functional."

**Phase 7 (Wrap-Up):**
- "The core trade-off in this design is [X vs Y]. I chose X because [reason], but Y would be better if [condition]."
- "If I had another hour, the next thing I'd design is [specific component] because [business/scale reason]."
