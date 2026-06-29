---
module: 05-hld-problems
topic: Medium
status: interview-ready
tags: [05-hld-problems, system-design, medium]
---
# Design Instagram

> **Difficulty**: Medium
> **Topics**: Feed Generation, Social Graph, Media Storage, Fan-Out
> **Time**: 45 min
> **Companies**: Meta, Amazon

---

## Clarifying Questions

1. "Are we designing the full product or focusing on photo upload, feed, and social graph?"
2. "What's the DAU — 100M, 1B? That determines fan-out strategy."
3. "Do we need Stories (24-hour TTL) or just the main feed?"
4. "Read-heavy or write-heavy optimization? Instagram is probably 100:1 reads to writes."
5. "Is the feed purely chronological or ranked by ML model?"
6. "Do we need real-time notifications (likes, comments) or eventual consistency is fine?"

---

## Back-of-Envelope

```
100M DAU, 50M posts/day
  Writes: 50M / 86,400 = ~578 posts/sec
  Reads: 578 × 100 = ~57,800 feed reads/sec

Media storage:
  Avg photo: 2MB compressed → 50M × 2MB = 100TB/day raw
  + 3 thumbnail sizes → 400TB/day total
  CDN offloads 99%+ of reads

Social graph:
  Avg 300 followers, max ~500M (celebrities)
  Fan-out at 100M DAU, 578 writes/sec → manageable for most users

Likes cache:
  Redis INCR per post: ~10K likes/sec across all posts
```

---

## APIs

```
// Upload photo
POST /api/v1/posts
  multipart: { photo, caption, location }
  -> { post_id, status: "processing" }

// Get feed
GET /api/v1/feed?user_id={id}&cursor={cursor}&limit=20
  -> { posts: [...], next_cursor }

// Like a post
POST /api/v1/posts/{post_id}/like
  -> { likes_count: 10423 }

// Get user's posts
GET /api/v1/users/{user_id}/posts?cursor={cursor}
  -> { posts: [...], next_cursor }
```

---

## Architecture

```
Client
  |
  +-- Upload --> Media Service
  |                +-- S3 (raw photo storage)
  |                +-- Kafka "photo.uploaded" event
  |                +-- Transcoding workers (3 thumbnail sizes + CDN push)
  |                +-- Post DB (PostgreSQL, write post metadata)
  |
  +-- Feed   --> Feed Service
  |                +-- Redis ZSET feed:{user_id} (top 200 posts, score=timestamp)
  |                +-- Fan-out worker (Kafka consumer, writes to follower feeds)
  |                +-- Celebrity merge: pull at read time via K-way heap
  |
  +-- Like   --> Like Service
                   +-- Redis INCR likes:{post_id}
                   +-- Redis SADD likers:{post_id} (cap at 500 for display)
                   +-- Flush to Cassandra COUNTER every 10s

Social Graph: Cassandra
  partition: follower_id
  clustering: followee_id, created_at
  query: "get all users this person follows" → feed fan-out worker
```

---

## Data Model

```sql
-- Post metadata (PostgreSQL)
CREATE TABLE posts (
    post_id     BIGINT PRIMARY KEY,   -- Instagram Snowflake: 41+13+10 bits
    user_id     BIGINT NOT NULL,
    s3_key      VARCHAR(255),
    caption     TEXT,
    location_id BIGINT,
    created_at  TIMESTAMPTZ DEFAULT NOW(),
    like_count  BIGINT DEFAULT 0,
    status      VARCHAR(20) DEFAULT 'active'
);
CREATE INDEX ON posts(user_id, created_at DESC);

-- Social graph (Cassandra)
-- Table: follows
-- PK: (follower_id, followee_id)  -- query: "who does user X follow?"
-- PK: (followee_id, follower_id)  -- query: "who follows user X?" (fan-out)
-- clustering: created_at DESC

-- Like counts (Cassandra COUNTER)
-- Table: post_likes_count
-- PK: post_id → count COUNTER
```

**Instagram Snowflake ID** (different from Twitter):
- 41 bits timestamp + 13 bits shard_id + 10 bits sequence
- Generated inside PostgreSQL via stored function — no external coordinator

---

## Key Design Decisions

**1. Hybrid fan-out with 1M follower threshold**
Below 1M followers: push model — on post, fan-out worker reads all followers from Cassandra, writes post_id to each follower's Redis ZSET `feed:{user_id}`. Above 1M (celebrities like Kylie Jenner): skip fan-out entirely. At read time, feed service pulls from celebrity's post list and merges with the user's precomputed feed via K-way heap sort. This avoids writing to 300M feeds when one person posts.

**2. Redis ZSET for feed with 200-post cap**
`feed:{user_id}` stores (score=post_timestamp, member=post_id). On feed read: ZREVRANGE → batch fetch post metadata from PostgreSQL (or Redis cache). Cap at 200 posts per user — users who don't open the app for a week get their feed rebuilt on next open from Cassandra directly.

**3. Like counts in Redis + periodic flush**
`INCR likes:{post_id}` is atomic and O(1). Flushing to Cassandra COUNTER every 10s means the DB is never the bottleneck for like traffic. Redis acts as a write buffer. On cache miss (cold post), read from Cassandra and warm Redis.

**4. Separate media service with CDN**
S3 for durable storage, CDN (CloudFront) for serving. Photos are write-once/read-many — CDN hit rate is 99%+. Signed CDN URLs expire in 24h for private accounts. Transcoding (3 sizes: 150px thumbnail, 640px feed, 1080px full) runs async after upload via Kafka → worker fleet.

---

## Deep Dives

**Feed staleness for celebrity posts**
If Taylor Swift posts and 300M followers need to see it: fan-out would write 300M Redis entries. Instead, mark her as "celebrity" (follower_count > 1M). Her posts are stored in a separate celebrity feed index. At read time, feed service queries: (1) user's precomputed ZSET feed, (2) celebrity followees' recent posts — merge via heap. This adds ~20ms but avoids the fan-out write storm.

**Stories (24-hour TTL)**
S3 lifecycle policy deletes media after 25 hours. Redis ZSET `active_stories:{viewer_user_id}` with score=expiry_timestamp. `ZRANGEBYSCORE 0 {now}` → expired stories. TTL on ZSET members removed via background job every minute. Story metadata (views) in Cassandra with TTL=48h.

---

## Failure Scenarios

| Failure | Impact | Mitigation |
|---------|--------|------------|
| Fan-out worker lag | Stale feeds for users with many followees | Kafka backpressure + autoscale workers; fresh reads fall back to pull |
| Redis feed cache miss | User opens app, feed is cold | Rebuild from Cassandra social graph + PostgreSQL posts; cache for 24h |
| S3 write fails after metadata inserted | Post visible but no photo | Kafka event retry; S3 write is idempotent by post_id key |
| Like counter Redis eviction | Count lost from buffer | Flush to Cassandra on eviction (LRU callback); Redis TTL is 7 days |
| Celebrity posts fan-out lag | Regular users see post on next read | Pull-on-read fills the gap; acceptable eventually-consistent feed |

---

## Interview Questions Asked

### Meta
1. **"Design Instagram's feed ranking — how do you move from chronological to ML-ranked feed?"** → Chronological feed is easy to generate (sort by timestamp). ML ranking adds a model that scores each post by predicted engagement (likes, comments, shares) for a specific user. Implementation: (1) retrieve top 500 candidates from chronological feed, (2) score each via a lightweight ML model (user-post affinity × recency × relationship strength), (3) return top 20. The model is pre-trained offline on engagement data; scoring is done at read time in a ranking service. For the system design, the key change is adding a Ranking Service between the Feed Service and the client.

### Common Follow-ups
1. **"How do you handle a user with 100M followers posting — doesn't that flood your write pipeline?"** → Fan-out on write is infeasible for celebrities. Switch to fan-out on read: celebrity posts are NOT pushed to follower feeds. Instead, at read time, the feed service pulls the celebrity's recent posts and merges them with the user's precomputed feed. Threshold: 1M followers. The merge is a K-way heap sort across the user's followed celebrities — at most 10-20ms of extra latency.
2. **"How do you scale the social graph query — fetching all followers for fan-out?"** → Cassandra partitioned by user_id with a followees list. For a user with 500K followers, fanning out means reading 500K rows → writing 500K Redis ZSET entries. This is done by the fan-out worker (Kafka consumer), which batches writes and runs concurrently. At 578 posts/sec and avg 300 followers each, that's 173K Redis writes/sec — manageable across a Redis cluster.
3. **"A user deletes a post. How do you remove it from all follower feeds?"** → Send a `post.deleted` event to Kafka. Fan-out worker processes it and issues `ZREM feed:{user_id} {post_id}` for each follower. For celebrities, the post just falls out naturally at read time (not in the precomputed feed). For deleted posts still in DB: soft-delete with `status='deleted'`; feed service filters these out at serve time. Background job cleans up after 24h.
4. **"How do you handle the photo upload size and compression?"** → Client does initial resize to max 1080px and compresses to JPEG 80% quality before upload (reduces avg upload from 8MB to ~1.5MB). Server-side: raw upload goes to S3, then Kafka triggers transcoding workers (FFmpeg) that produce 3 standardized sizes. This offloads compute from the upload path and standardizes formats regardless of client-side compression quality.
5. **"What if a user has 0 followers — does fan-out still happen?"** → No fan-out needed (nobody to push to). Post is still written to the user's own post list in PostgreSQL. When they gain followers later, new followers see posts via "load older posts" pull path or a recomputed feed.

---

## Interviewer Follow-Up Questions

**On feed generation:**
- "Why Redis ZSET and not a DB query for the feed every time?" → Feed DB query would be: SELECT posts WHERE user_id IN (SELECT followee_id FROM follows WHERE follower_id = X) ORDER BY created_at DESC LIMIT 20. At 57,800 QPS, this hits the follows table (large) + posts table (huge) with a join — impossibly slow even with read replicas. ZSET is precomputed, O(log N) lookup, in-memory. The trade-off is write amplification (fan-out on post) vs. read speed.
- "How does the feed service handle a user who hasn't opened Instagram in 30 days?" → Redis TTL on `feed:{user_id}` evicts the key after 7 days of inactivity. On next open, feed is a cache miss → fall back to the pull model: query Cassandra social graph for followees, fetch their recent posts from PostgreSQL, rebuild and cache the feed. First load is ~500ms; subsequent loads hit the warm ZSET.
- "A user follows 2,000 accounts. How many posts are in their Redis ZSET?" → Capped at 200 posts (ZADD + ZREMRANGEBYRANK to trim). Out of 2,000 followees posting ~0.5 posts/day each = 1,000 new posts/day in the feed. At 200-post cap, the feed refreshes effectively every few hours. Scrolling past the cap triggers an older-posts load from PostgreSQL.

**On storage:**
- "Why S3 and not a custom blob store?" → S3 has 11 nines durability, multi-region replication, lifecycle policies (move to Glacier after 1 year), direct CDN integration. Building a blob store in-house (like Facebook's Haystack) makes sense at 10B+ photos/day. At 50M/day, S3 cost ($0.023/GB/month) is cheaper than engineering and ops overhead. Key naming: `media/{user_id}/{post_id}/{size}.jpg` for CDN cache effectiveness.
- "How do you handle media serving for 100M users simultaneously?" → CDN (CloudFront/Akamai) with 200+ PoPs globally. Cache hit rate for Instagram-scale content is 97%+. Only first request per PoP hits origin (S3). Signed URLs for private accounts, TTL 24h. Estimated CDN bandwidth: 100M users × 20 photos/session × 300KB avg = 600TB/day — CDN absorbs this; origin S3 sees <3% of that.

**On social graph:**
- "Why Cassandra for the social graph and not PostgreSQL?" → The follows table at Instagram scale: 100M users × 300 avg followees = 30B rows. PostgreSQL with B-tree index on (follower_id) works, but at 578 writes/sec for fan-out reads (reading 500K follower rows per celebrity post), it saturates. Cassandra is designed for this: partition by follower_id, fan-out read is a single partition scan. Linear scale-out by adding nodes. No joins needed (denormalized for the access pattern).
- "How do you handle a bidirectional follow (mutual follows for close friends)?" → Store two rows: (A follows B) and (B follows A) — both in the same Cassandra table. The "close friends" feature adds a `relationship_type` column (ENUM: follows/close_friend). Feed service queries follows WHERE relationship_type='close_friend' for Stories visibility. No separate table needed; the column filters the list at query time.
