# Design Twitter/News Feed

> **Difficulty**: Medium
> **Topics**: Fan-out, Timeline Generation, Caching, Redis
> **Time**: 60 minutes
> **Companies**: Meta, Twitter, LinkedIn, Instagram

---

## Real-Life Analogy

Think of a daily newspaper personalized for every subscriber. The printing press runs overnight, assembling each person's paper based on which writers they subscribe to. When you wake up, your paper is already waiting at the door — pre-assembled, fast to read.

That's fan-out on write. The catch: if a celebrity columnist publishes an article, you'd need to physically print 100 million copies of their piece — one for each subscriber's doorstep. That's the "celebrity problem." You can't pre-assemble papers for 100M subscribers every time Elon Musk tweets.

The solution Twitter (and most large social networks) use: for regular users (< 10K followers), pre-print their section and push it to followers' mailboxes overnight. For celebrities (> 10K followers), don't pre-print — instead, when a subscriber opens their paper, the printing press quickly fetches the celebrity's latest articles and staples them in on-demand. The reader never notices the difference.

---

## Why This Is Hard

1. **The celebrity (hotspot) problem**: A celebrity with 100M followers posts one tweet. Naive fan-out = 100M writes to Redis in seconds. This overwhelms the write pipeline and creates hot shards in Redis.
2. **Feed freshness vs. performance trade-off**: Pre-computed feeds (fan-out on write) are fast to read but expensive to update. On-demand feeds (fan-out on read) are cheap to write but require scanning hundreds of follows on every read.
3. **Ordering across shards**: Tweets are stored across many DB shards. Generating a feed requires fetching from multiple shards and merge-sorting by timestamp. At 180K reads/sec, this merge-sort must be invisible.
4. **Cache invalidation**: A user's feed cache becomes stale the moment anyone they follow tweets. At 12K tweets/sec across 500M users, you can't invalidate caches on every write — you'd evict the entire cache every second.
5. **Read-your-own-writes**: After posting a tweet, users expect to see it in their own feed immediately. This requires careful cache write-through that doesn't require waiting for full fan-out to complete.

---

## Scale

- 1 billion users total
- 500 million DAU (Daily Active Users)
- Each user follows 200 people (average)
- Each user posts 2 tweets/day on average
- Each user views feed 10 times/day

---

## Capacity Estimation

```
Write QPS (tweets):
500M DAU × 2 tweets/day ÷ 86,400 sec = ~12K tweets/sec (average)
Peak (3×): 36K tweets/sec

Read QPS (feed views):
500M DAU × 10 views/day ÷ 86,400 sec = ~60K views/sec
Peak (3×): 180K views/sec

Fan-out writes (regular users, avg 200 followers):
12K tweets/sec × 200 followers = 2.4M Redis writes/sec
This is why celebrities can't use fan-out on write.

Storage (5 years):
Daily tweets: 500M users × 2 = 1B tweets/day
5 years: 1B × 365 × 5 = 1.8 trillion tweets
Per tweet: 500 bytes (text + metadata)
Total: 1.8T × 500B = 900 TB
With replication (3×): 2.7 PB
```

---

## API Design

```http
POST /api/v1/tweets
{
  "user_id": "user_123",
  "content": "Hello Twitter!",
  "media_urls": ["https://cdn.example.com/image.jpg"]
}
Response: 201 Created
{
  "tweet_id": "tweet_789",
  "created_at": "2026-02-08T10:00:00Z"
}

GET /api/v1/feed?user_id=user_123&page=1&size=20
Response: 200 OK
{
  "tweets": [
    {
      "tweet_id": "tweet_456",
      "user_id": "user_789",
      "username": "john_doe",
      "content": "This is a tweet",
      "created_at": "2026-02-08T10:00:00Z",
      "likes": 42,
      "retweets": 10
    }
  ],
  "next_page": 2
}

POST /api/v1/users/{user_id}/follow
{
  "target_user_id": "user_789"
}
```

---

## Database Schema

```sql
-- Users
CREATE TABLE users (
    user_id BIGINT PRIMARY KEY,
    username VARCHAR(50) UNIQUE,
    email VARCHAR(100),
    follower_count INT DEFAULT 0,  -- Denormalized for fast celebrity checks
    created_at TIMESTAMP
);

-- Tweets
CREATE TABLE tweets (
    tweet_id BIGINT PRIMARY KEY,   -- Snowflake ID (time-sortable)
    user_id BIGINT,
    content VARCHAR(280),
    created_at TIMESTAMP,
    INDEX idx_user_created (user_id, created_at DESC)
);

-- Followers (bidirectional for feed generation)
CREATE TABLE followers (
    follower_id BIGINT,   -- Person doing the following
    followee_id BIGINT,   -- Person being followed
    created_at TIMESTAMP,
    PRIMARY KEY (follower_id, followee_id),
    INDEX idx_follower (follower_id),
    INDEX idx_followee (followee_id)
);

-- Timeline Cache in Redis:
-- Key: feed:user_123
-- Value: Sorted set of tweet IDs (score = timestamp)
-- redis.zadd("feed:user_123", {tweet_id: timestamp, ...})
-- TTL: 7 days
```

---

## Architecture

```
          Client
             ↓
       Load Balancer
             ↓
    ┌────────┴────────┐
    ↓                 ↓
Timeline API      Tweet Write API
    ↓                 ↓
Redis Cache      Fan-out Service
(Feed Cache)          ↓
    ↓            Message Queue (Kafka)
    ↓                 ↓
PostgreSQL       Timeline Workers
(Tweets DB)      (Update followers' feeds in Redis)
                      ↓
              Celebrity Resolver
              (Skip fan-out for >10K followers)
```

---

## Fan-Out Approaches

### Approach 1: Fan-Out on Write (Push Model)

When a user tweets, immediately push to all followers' timeline caches.

```
1. User posts tweet
2. Fan-out service: GET all follower IDs (SELECT follower_id FROM followers WHERE followee_id = ?)
3. For each follower: redis.zadd("feed:{follower_id}", tweet_id, timestamp)

Example:
Normal user (200 followers) posts → 200 Redis writes → fast
Celebrity (10M followers) posts → 10M Redis writes → catastrophic
```

**Pros:** Read is O(1) — just fetch pre-computed feed from Redis
**Cons:** Celebrity tweets cause write amplification proportional to follower count

**When to use:** All users with < 10K followers

---

### Approach 2: Fan-Out on Read (Pull Model)

When a user requests their feed, query all followed users' recent tweets on-demand.

```
1. User requests feed
2. GET all followed user IDs: SELECT followee_id FROM followers WHERE follower_id = ?
3. Query tweets: SELECT * FROM tweets WHERE user_id IN (followees) ORDER BY created_at DESC LIMIT 20
4. Merge and sort results
```

**Pros:** Write is O(1) — just store the tweet, no fan-out
**Cons:** Read requires joining across potentially 200+ users, across multiple DB shards. At 180K reads/sec, this is untenable.

**When to use:** Celebrities only — their tweets are fetched on-demand when followers open their feed

---

### Hybrid Approach (The Real Solution)

```
On tweet creation:
  IF user.follower_count < 10,000:
    → Publish to Kafka → Timeline Workers fan out to all followers' Redis caches
  ELSE (celebrity):
    → Just store tweet in DB, no fan-out

On feed request for user X:
  1. Fetch pre-computed feed from Redis (contains tweets from normal followed users)
  2. Identify which followees are celebrities (follower_count >= 10,000)
  3. Query celebrity tweets from DB (recent 7 days, limit 50 per celebrity)
  4. Merge and sort all tweets by timestamp
  5. Return top 20 to user
```

**Implementation:**

```java
public List<Tweet> getTimeline(String userId, int page, int size) {
    // Step 1: Get cached timeline (from fan-out on write for normal users)
    List<String> cachedTweetIds = redis.zrevrange("feed:" + userId, 0, size * 2);
    List<Tweet> cachedTweets = getTweetsByIds(cachedTweetIds);

    // Step 2: Get celebrity followees and fetch their tweets on-demand
    List<String> celebrityIds = getCelebrityFollowees(userId);  // > 10K followers
    List<Tweet> allTweets;

    if (!celebrityIds.isEmpty()) {
        List<Tweet> celebrityTweets = db.query(
            "SELECT * FROM tweets WHERE user_id IN (?) " +
            "AND created_at > NOW() - INTERVAL '7 days' " +
            "ORDER BY created_at DESC LIMIT 50",
            celebrityIds
        );

        // Step 3: Merge and sort
        allTweets = mergeAndSort(cachedTweets, celebrityTweets);
    } else {
        allTweets = cachedTweets;
    }

    // Step 4: Paginate
    int start = (page - 1) * size;
    return allTweets.subList(start, Math.min(start + size, allTweets.size()));
}
```

---

## Caching Strategy

```
Redis Cache:
Key: feed:user_123
Type: Sorted Set (score = tweet timestamp, member = tweet_id)
Size: Keep last 500 tweet IDs per user
TTL: 7 days (inactive users' caches expire, rebuilt on next login)

Operations:
Add tweet:  redis.zadd("feed:user_123", timestamp, tweet_id)
Get feed:   redis.zrevrange("feed:user_123", 0, 19)  // Top 20, newest first
Trim old:   redis.zremrangebyrank("feed:user_123", 0, -501)  // Keep latest 500

Cache-aside (for cold starts):
1. Check Redis for timeline
2. If miss (inactive user) → regenerate from DB (query last 7 days of followed users' tweets)
3. Populate Redis cache
4. Return
```

**Memory calculation:**
```
500M users × 500 tweet IDs × 8 bytes = 2 TB of Redis
That's too expensive → Only cache active users (users active in last 7 days)
Active users: 100M × 500 × 8 bytes = 400 GB → feasible with Redis cluster
```

---

## Scaling

### Database Sharding

```
Tweets: Shard by user_id
  Shard 1: user_id % 4 = 0
  Shard 2: user_id % 4 = 1
  Shard 3: user_id % 4 = 2
  Shard 4: user_id % 4 = 3

Why user_id, not tweet_id?
  Fetching a user's tweets is the common query pattern.
  If sharded by tweet_id, a user's tweets are on all shards (scatter-gather every time).
  If sharded by user_id, all of one user's tweets are on one shard (single-shard query).

Challenge: Follower graph spans shards
  Solution: Replicate the followers table to each shard (acceptable — follower data is small)
```

### Redis Cluster

```
128 hash slots distributed across Redis nodes
feed:user_123 → hash(user_123) % 128 → Node X

Hot key issue: One viral user's feed in all 100M feeds = 100M writes to Redis
Solution: Rate-limit fan-out workers. If fan-out queue exceeds threshold for a user,
          downgrade them to celebrity mode temporarily.
```

### Fan-Out Service Scaling

```
Kafka topic: fan-out-jobs
Partition key: tweet_author_id (ensures ordering for same author)
Workers: 100 consumer instances

Each worker:
  1. Consume tweet event from Kafka
  2. Batch followers (fetch 1000 at a time)
  3. Pipeline Redis writes (MULTI/EXEC blocks of 500 operations)
  4. Commit Kafka offset

Throughput: 12K tweets/sec × 200 avg followers = 2.4M Redis writes/sec
With 100 workers: 24K operations/worker/sec → manageable
```

---

## Failure Scenarios

### Redis Goes Down

**Impact:** Feed reads hit PostgreSQL directly
**Mitigation:**
- Fall back to fan-out on read from DB (slower, ~200ms vs ~5ms, but functional)
- Redis cluster with replica failover (< 30 seconds RTO)
- Backfill cache when Redis recovers

### Fan-Out Service Falls Behind

**Impact:** Followers see stale feeds during high-traffic events
**Mitigation:**
- Kafka retains messages for 7 days — workers catch up when load normalizes
- For freshness SLA: fall back to partial fan-out on read for users whose cache is > 5 minutes stale
- Priority queues: verified accounts get priority fan-out workers

### Database Shard Failure

**Impact:** Tweets from users on that shard unavailable
**Mitigation:**
- Synchronous replication to standby (PostgreSQL streaming replication)
- Auto-failover (< 60 seconds)
- For read queries: serve from replica until primary recovers

---

## Trade-offs

| Aspect | Fan-out on Write | Fan-out on Read | Hybrid |
|--------|-----------------|-----------------|--------|
| **Read latency** | ~5ms (Redis) | ~200ms (DB scatter-gather) | ~5ms + celebrity fetch |
| **Write cost** | High (200× amplification) | Zero amplification | Low (only for < 10K followers) |
| **Complexity** | Medium | Low | High |
| **Celebrity handling** | Broken | Graceful | Graceful |
| **Storage** | High (Redis per user) | Low | Medium |

---

## Interview Tips

**The most important concept to explain clearly:**
> "The hybrid model exists because of an asymmetry: reading one user's pre-computed feed is O(1) in Redis, but writing to 100M followers' feeds is O(100M). We solve this by capping fan-out at 10K followers and pulling celebrity tweets on-demand."

**Common Questions:**
- **Q: "How do you handle a celebrity with 100M followers?"**
  → Fan-out on read. Store tweet in DB, never push to Redis. When followers load their feed, fetch celebrity's recent tweets and merge with their pre-computed feed. The merge happens in the app server, in memory.

- **Q: "What if Redis goes down?"**
  → Fallback to DB queries. Feed generation takes ~200ms instead of ~5ms, but service stays up. Redis cluster with replicas minimizes downtime risk.

- **Q: "How do you ensure a user sees their own tweet immediately?"**
  → On tweet creation, write-through to the poster's own Redis feed synchronously before returning the API response. Fan-out to followers happens asynchronously.

- **Q: "What about inactive users?"**
  → Don't maintain their feed cache. When an inactive user opens the app, detect cache miss, regenerate feed from DB (fan-out on read for that one request), populate cache.

---

## Interview Questions Asked

### Meta
1. **"Design Facebook News Feed."** → Tests ranking vs. chronological feed trade-off; key answer: fan-out on write for most users, ML ranking on the pre-computed candidate set, EdgeRank/Graph API to determine what content surfaces.

### Google
1. **"How do you rank content at scale?"** → Tests two-phase retrieval understanding; key answer: retrieval phase fetches a large candidate set (pre-computed fan-out), ranking phase scores with lightweight ML model (logistic regression or GBDT) under a strict latency budget.

### Common Follow-ups
1. **"How does Twitter handle the celebrity problem (e.g., Elon Musk with 150M followers)?"** → Tests hybrid fan-out knowledge; celebrity tweets are never fanned out — they're stored in a high-follower cache; on feed load, follower's pre-computed feed is merged with a real-time fetch of celebrity tweets in the app server.
2. **"Real-time vs. eventual consistency for feed — what does Twitter choose?"** → Tests consistency trade-off reasoning; eventual consistency is acceptable for feed (users tolerate a few seconds of delay); own tweets use write-through to poster's feed for immediate visibility.
3. **"How do you A/B test different ranking algorithms?"** → Tests experimentation infrastructure; route a percentage of traffic to a ranking variant via feature flags; log impressions and engagements per variant; compare metrics (CTR, dwell time) in an experimentation platform; ranking is stateless so switching is safe.
4. **"How does tweet deletion propagate to all feeds?"** → Tests async propagation; soft-delete the tweet in DB immediately (returns 404 on API); publish a `tweet_deleted` event to Kafka; fan-out workers scan Redis feed lists and remove the tweet ID; CDN purge for any cached tweet detail pages; eventual consistency — brief window where deleted tweet is still visible.
