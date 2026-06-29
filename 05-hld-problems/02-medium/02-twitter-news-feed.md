---
module: 05-hld-problems
topic: Medium
status: interview-ready
tags: [05-hld-problems, system-design, medium]
---
# Design Twitter / News Feed

> **Difficulty**: Medium
> **Topics**: Fan-Out, Feed Generation, Social Graph, Caching
> **Time**: 45 min
> **Companies**: Meta, Google, Amazon

---

## Clarifying Questions

1. "Are we designing the full platform or focusing on tweet creation and feed generation?"
2. "What's the scale — 500M DAU? That heavily impacts fan-out strategy."
3. "Is the feed chronological or ranked? Chronological is simpler to design."
4. "Do we need retweets and quotes, or just original tweets?"
5. "What's the celebrity threshold — how do we define 'large follower count'?"
6. "Should we optimize for write latency (push model) or read latency (pull model)?"

---

## Back-of-Envelope

```
500M DAU, 12K tweets/sec write
Feed views: 500M users × 3 checks/day / 86,400 = ~17K feed reads/sec
Peak feed reads: ~60K/sec

Fan-out math:
  Avg followers: 200 → 12K tweets/sec × 200 = 2.4M Redis ZADD/sec
  Threshold: 10K followers → below: push model; above: pull at read time

Tweet storage:
  Avg tweet: 300 bytes
  12K/sec × 86,400s × 300 bytes = ~311 GB/day
  Cassandra handles this with time-series partitioning

Redis memory:
  500M users × 200 posts × 8 bytes (post_id) = ~800 GB → Redis Cluster
```

---

## APIs

```
// Create tweet
POST /api/v1/tweets
  { "content": "...", "media_ids": [...] }
  -> { "tweet_id": 1234567890, "created_at": "..." }

// Get feed
GET /api/v1/feed?cursor={cursor}&limit=20
  -> { "tweets": [...], "next_cursor": "..." }

// Get user timeline
GET /api/v1/users/{user_id}/tweets?cursor={cursor}
  -> { "tweets": [...], "next_cursor": "..." }

// Retweet
POST /api/v1/tweets/{tweet_id}/retweet
  -> { "retweet_id": 9876543210 }
```

---

## Architecture

```
Client
  |
  +-- Tweet --> Tweet Service
  |                +-- Write tweet to Cassandra (authoritative)
  |                +-- Publish "tweet.created" to Kafka
  |                +-- Write to author's own Redis ZSET (read-your-own-writes)
  |
  +-- Feed  --> Feed Service
                   +-- Read user's Redis ZSET feed:{user_id} (precomputed)
                   +-- For followed celebrities (>10K followers): pull their tweets at read time
                   +-- Merge via K-way heap sort (score=tweet_id/timestamp)
                   +-- Return top 20, paginate with cursor

Fan-out Worker (Kafka consumer):
  on "tweet.created":
    1. Read author's follower list from Cassandra (partition by author_id)
    2. For each follower: ZADD feed:{follower_id} {tweet_timestamp} {tweet_id}
    3. ZREMRANGEBYRANK feed:{follower_id} 200 -1  (trim to 200 posts)
    -- Skip if follower_count > 10K (celebrity threshold, handled at read time)

Storage:
  Cassandra tweets table: partition by user_id, cluster by Snowflake tweet_id DESC
  Redis Cluster: feed:{user_id} ZSET, ~800 GB total
```

---

## Data Model

```sql
-- Tweets (Cassandra - write-heavy, time-series)
-- Table: tweets
-- PK: (user_id, tweet_id)  tweet_id is Snowflake (time-sortable)
-- query: "get user's tweets" = partition scan by user_id

-- Social graph (Cassandra)
-- Table: followers_by_user
-- PK: (user_id)  → list of (follower_id, created_at)
-- Table: followees_by_user
-- PK: (user_id)  → list of (followee_id, created_at)

-- Tweet metadata (PostgreSQL for relational queries)
CREATE TABLE tweets (
    tweet_id    BIGINT PRIMARY KEY,  -- Snowflake ID: 41+10+12 bits
    user_id     BIGINT NOT NULL,
    content     VARCHAR(280),
    media_s3_key TEXT[],
    retweet_of  BIGINT REFERENCES tweets(tweet_id),
    reply_to    BIGINT REFERENCES tweets(tweet_id),
    like_count  BIGINT DEFAULT 0,
    retweet_cnt BIGINT DEFAULT 0,
    created_at  TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX ON tweets(user_id, created_at DESC);
```

---

## Key Design Decisions

**1. Hybrid fan-out: 10K follower threshold**
Below 10K followers: push model — fan-out worker writes tweet_id to each follower's Redis ZSET on tweet creation. Lag is acceptable (<1s). Above 10K (celebrities): no fan-out. At feed read time, the feed service fetches the celebrity's recent tweets from Cassandra and merges via K-way heap sort. Threshold is 10K, not 1M (tighter than Instagram) because Twitter's fan-out workers operate at lower throughput.

**2. Write-through on author's own feed**
After writing to Cassandra, the tweet service immediately adds to the author's own `feed:{author_id}` ZSET. This guarantees read-your-own-writes without waiting for the Kafka fan-out consumer to process the event. Fan-out lag doesn't affect the author.

**3. Redis ZSET with 200-post cap**
`feed:{user_id}`: score=tweet_timestamp (Unix ms), member=tweet_id. ZADD + ZREMRANGEBYRANK keeps top 200. Feed read: ZREVRANGE → batch fetch tweet content from PostgreSQL or Cassandra. Cold feed (user inactive >7 days, key TTL expired): rebuild from Cassandra pull on next request.

**4. Cassandra for tweet storage**
Tweets are append-only and read by time range per user. Cassandra partition by user_id, cluster by tweet_id DESC → "get last N tweets by user" is a single partition scan. Handles 12K writes/sec across a 6-node cluster easily. PostgreSQL for tweet metadata with relational needs (like counts, follower stats).

---

## Deep Dives

**Celebrity merge at read time**
User follows 500 regular users + 10 celebrities (Elon, Obama, etc.). Fan-out precomputes the 500 regular user tweets into Redis ZSET. At read time: feed service fetches celebrity tweets from Cassandra (1 query per celebrity, last 24h), adds to a merge heap alongside the precomputed feed. K-way merge by timestamp → top 20 results. Added latency: ~10-20ms for 10 celebrity queries in parallel. Acceptable.

**Trending topics**
Count tweet occurrences of hashtags/words in a sliding 1-hour window. Kafka stream → Flink aggregation → top-K by count in Redis Sorted Set `trending:global`. Updated every 60s. Geographically partitioned (trending:country:US). Heavy use of approximate counts (HyperLogLog for unique users tweeting the trend).

---

## Failure Scenarios

| Failure | Impact | Mitigation |
|---------|--------|------------|
| Fan-out worker lag | Followers see stale feed | Kafka consumer backpressure; autoscale workers; feed falls back to pull |
| Redis node failure | ~1/N of users lose precomputed feed | Redis Cluster replication (1 replica per shard); cold miss rebuilds from Cassandra |
| Cassandra node failure | Tweet writes may fail | Replication factor 3, quorum writes (W=2); automatic repair |
| Tweet storm (viral tweet) | Heavy read on single tweet_id | Cache popular tweets in Redis with TTL 5m; CDN for media |
| Celebrity joins (follower spike) | User crosses threshold, fan-out stops | Threshold check at fan-out time; feed service dynamically adds to celebrity pull list |

---

## Interview Questions Asked

### Meta
1. **"How would you design the ranking algorithm for Facebook News Feed — what signals matter?"** → ML-based ranking model ingests: relationship strength (interaction frequency with poster), content type (video > photo > link for engagement), recency (exponential decay), and negative signals (hide/unfollow rate). The system design side: (1) retrieve top 500 candidates from precomputed chronological feed; (2) score each post via a lightweight XGBoost or neural net model (served via GRPC, <5ms); (3) return top 20 reranked by predicted engagement. Key insight for the interviewer: the feed retrieval and the ranking are separate services — this allows independent scaling and A/B testing.
2. **"How do you handle the EdgeRank-equivalent problem — a user has 1,000 friends but sees posts from only 10 of them?"** → The ranking model downweights content from friends you rarely interact with. Implementation: maintain a `relationship_strength` score per (viewer, author) pair, updated async when interactions occur (likes, comments, profile visits, messages). Strong ties get a boost in ranking. Weak ties still appear occasionally to avoid filter bubbles (controlled by an "serendipity" term in the ranking formula).

### Google
1. **"Design a system to serve the Google+ feed (or any social feed) at Google scale."** → Google-scale social feed uses Bigtable (equivalent to Cassandra) for social graph and tweet storage. Fan-out is the same hybrid model — push for small graphs, pull for large. Key Google angle: use Spanner for the authoritative user table and follow graph (strong consistency, cross-region). Redis still handles feed precomputation. CDN (Google's own) for media at global scale.
2. **"How does YouTube's subscription feed differ from Twitter's news feed architecturally?"** → YouTube subscriptions are follow relationships where subscribers are always low-count relative to Twitter celebrities (no user has 300M subscribers). Fan-out on write works for YouTube because average channel subscriber count is much lower than Twitter's median followed account. Content is also less frequent (one video/week vs. 50 tweets/day) — much lower write amplification.

### Common Follow-ups
1. **"10K users follow a celebrity who tweets. How does the fan-out worker handle this without blocking?"** → Fan-out worker is a Kafka consumer. It reads the "tweet.created" event, fetches the author's follower list from Cassandra (paginated, 1K per batch), and writes to each follower's Redis ZSET. For 10K followers: 10 batches × 1K writes each. Parallelized across worker threads. Target: < 500ms fan-out latency for a 10K-follower user.
2. **"How do you prevent a Redis memory explosion with 500M users each having a 200-post feed?"** → 500M × 200 posts × 8 bytes = 800 GB. Managed with Redis Cluster (sharded across 20+ nodes, 40GB each). TTL-based eviction: keys expire after 7 days of user inactivity (accessed flag via Bloom filter). Only active users maintain a precomputed feed; inactive users get a cold rebuild on next login.
3. **"User A follows User B. User B tweets and fan-out runs. Seconds later, User A unfollows User B. The tweet is now in User A's feed incorrectly."** → Acceptable for eventual consistency. The tweet stays in User A's Redis feed until it naturally ages out (200-post cap, or 7-day TTL). If strong consistency needed: on unfollow, publish "unfollow" event → worker queries follower's feed and ZREM the unfollowed user's recent tweets. In practice, Twitter and Instagram don't do this — a tweet showing briefly post-unfollow is not a product bug.

---

## Interviewer Follow-Up Questions

**On fan-out:**
- "Why is the threshold 10K and not 1M or 100?" → Pure math: at 12K tweets/sec globally and avg 200 followers per user, the fan-out load is 2.4M Redis writes/sec — sustainable across a Redis Cluster. At 10K followers, one celebrity tweet causes 10K Redis writes — still manageable. At 1M+ followers, one tweet causes 1M writes — a spike that can stall the fan-out queue for seconds, delaying other users' feeds. 10K is the inflection point where the per-event cost starts causing tail latency for other users.
- "Fan-out workers are behind. A user opens the app and their feed is 5 minutes stale. What do you do?" → Fall back to a real-time pull for the most recent slice: fetch the last 20 tweets from each followed account directly from Cassandra (union query), overlay on the stale precomputed feed. This is expensive (~20 Cassandra reads) but rare. Return the pull-computed result and async update the Redis ZSET to catch it up. Alert on fan-out queue age — SLA is < 30 seconds lag.
- "How does the feed service handle a user following 5,000 accounts?" → 5,000 followees = 5,000 fan-out targets per tweet that user's followees post. Their feed ZSET is constantly being written to. Feed read is still fast (ZREVRANGE on their ZSET). The challenge is write amplification — 5,000 × tweet rate of followees. Managed by the fan-out worker batching and the 200-post cap (ZADD + ZREMRANGEBYRANK keeps the ZSET from growing unboundedly).

**On storage:**
- "Why Cassandra for tweets instead of PostgreSQL?" → Tweets are time-series, append-only, and partition naturally by user_id. Cassandra's LSM tree excels at write-heavy workloads. The query pattern "get latest N tweets by user_id" maps perfectly to Cassandra's partition + cluster key model. PostgreSQL with a 10B+ row tweets table requires heavy sharding and index management. Cassandra scales horizontally by adding nodes — no schema changes needed.
- "How do you store retweets without duplicating tweet content?" → Retweet stores only a pointer: `retweet_of=original_tweet_id` + `user_id` + `created_at`. Original tweet content is fetched by tweet_id. Fan-out of a retweet follows the same model as original tweets — the retweet_id propagates to followers' feeds; the feed service enriches it with the original tweet content at read time. This prevents storing the same 280-character tweet millions of times.
