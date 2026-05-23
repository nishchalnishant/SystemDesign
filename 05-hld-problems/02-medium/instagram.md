---
module: 05-hld-problems
topic: Medium
status: unread
tags: [05-hld-problems, system-design, medium]
---
# Design Instagram

> **Difficulty**: Medium
> **Topics**: Media Pipeline, Fan-out, CDN, Social Graph
> **Time**: 60 minutes
> **Companies**: Meta, Snap, Pinterest, TikTok

---

## Problem Mindmap

```
Instagram
├── Problem Constraints
│   ├── Scale → 100M DAU, 50M posts/day = 578 writes/sec, 400TB/day media, 1B followers for top accounts
│   ├── Latency target → feed load < 200ms; photo/video display < 1s; like < 100ms
│   └── Core hardness → fan-out for 1B-follower accounts + media pipeline at 400TB/day + like count at billions
├── Architecture Derivation
│   ├── Step 1 → Naive fan-out: write post to all followers' feeds → 1B writes × 578 posts/sec = impossible in sync
│   ├── Step 2 → Hybrid fan-out: < 1M followers = write-time fan-out; ≥ 1M = read-time merge (celebrity)
│   ├── Step 3 → Media: async pipeline (upload → S3 → Kafka → resize worker → CDN); not blocking post creation
│   └── Step 4 → Like counts: Redis INCR (real-time) + Cassandra COUNTER (durable) + async flush to PostgreSQL
├── Core Components
│   ├── Instagram Snowflake IDs → 41-bit timestamp + 13-bit shard + 10-bit sequence; DB-embedded generation
│   ├── Media Pipeline → upload to S3 raw; Kafka triggers resize workers (thumbnail/standard/HD); CDN for delivery
│   ├── Feed Service → hybrid fan-out; Redis ZSET per user (top-200 posts); celebrity pull merged at read time
│   ├── Social Graph → Cassandra: (user_id, follower_id, created_at); partitioned by user_id; efficient fan-out lookup
│   └── Like Service → Redis INCR per post; Cassandra COUNTER for durability; approximate count acceptable (±0.1%)
├── Data Model
│   ├── posts → (post_id BIGINT PK, user_id, caption, media_urls[], location, created_at, like_count_approx)
│   ├── follows → Cassandra (follower_id, followee_id, created_at); reverse index (followee_id, follower_id) for fan-out
│   └── likes → Cassandra (post_id, user_id, created_at); Redis COUNTER for fast count; PostgreSQL for exact billing
├── APIs
│   ├── POST /posts → {media_upload_id, caption, location?} → {post_id}
│   ├── GET /feed → {cursor?} → [{post_id, user_id, media_url, like_count, ...}]
│   ├── POST /posts/{post_id}/like → {user_id} → {like_count}
│   └── GET /users/{user_id}/posts → paginated media grid
├── Critical Trade-offs
│   ├── 1M follower fan-out threshold → above 1M = celebrity path (read-time merge); avoids billions of Redis writes
│   ├── Approximate like count → Redis INCR (fast) vs exact DB count (slow); ±0.1% acceptable for social feature
│   └── CDN for media → all media served via CDN; S3 is origin only; no direct S3 URLs exposed to clients
├── Failure Scenarios
│   ├── Fan-out Kafka lag → delayed feed update (eventual); posts appear in feed within seconds; no data loss
│   ├── Redis ZSET eviction → rebuild from PostgreSQL post timeline; 1-time latency penalty
│   └── Media upload failure → resumable chunked upload; S3 multipart; post not published until media confirmed
└── Interview Angles
    ├── Meta → "Design Instagram" → Snowflake IDs + hybrid fan-out + async media pipeline is the core
    ├── Snap → "Design Stories" → same feed mechanics; 24h TTL on story objects; separate story feed ZSET
    └── Follow-up → "How do you handle Explore/Discover?" → offline ML ranking; candidate generation via collaborative filtering
```

---

## What Breaks Without This System?

A user posts a photo on a naive system: the image is stored in the app server's local filesystem, the post is saved to MySQL, and the feed is computed on read by joining `posts` and `follows` tables. At 100M DAU with 50M posts/day:

- **Storage**: 400TB/day of photo/video data on local filesystems — servers run out of disk in hours. Photos on server A are inaccessible when load balancer routes to server B.
- **Feed read**: "Show me posts from 500 people I follow" requires a join across 50M posts against a 1B-row `follows` table, every single feed load. At 100M DAU × 5 feed refreshes/day = 500M joins/day. The DB collapses.
- **Media serving**: every photo request hits the origin server. At 100M users × 20 photo views/day = 2B requests/day = 23K requests/sec to origin. The app server spends 95% of its time serving static bytes.
- **Celebrity posts**: Kylie Jenner posts. 150M followers each need this post in their feed. If computed on read, 150M users each trigger a join. If pushed on write (fan-out), you have 150M write operations to execute in seconds.

Without a designed system: disk fills up, feeds are stale by hours, media is unavailable, and celebrity posts crash the infrastructure.

---

## Derive the Architecture

**Step 1 — Single server**
Store photos on disk, posts in MySQL, compute feed on read. Breaks at ~10K DAU when disk and join latency become visible.

**Step 2 — Separate media from metadata**
Photos are immutable blobs — the canonical tool is object storage (S3). `POST /upload` saves the image to S3, stores the S3 key in the `posts` table. All servers share the same S3 — no more locality problem. Serve media via CDN (CloudFront): users get photos from edge nodes 10–30ms away, not the origin.

**Step 3 — The upload pipeline**
Synchronous (user waits for): upload raw image to S3, create post record in DB with `status=PROCESSING`. Return success immediately.
Asynchronous (background): transcode to multiple resolutions (thumbnail/320px/1080px/original), generate blurhash placeholder, extract EXIF data, run content moderation. When complete, update post `status=PUBLISHED`.
This ensures the user sees "upload complete" in <2 seconds even for large videos, while processing continues in the background.

**Step 4 — Feed generation: the fan-out problem**
On every post, you need to deliver it to all followers' feeds. Two extreme approaches:
- Fan-out on write (push): when a post is created, write it to every follower's feed cache immediately. Feed reads are instant (pre-built). Write amplification: 1 post × 1M followers = 1M writes.
- Fan-out on read (pull): when a user opens their feed, fetch recent posts from everyone they follow. No write amplification. Read is expensive: join 500 followees × their recent posts.

**The threshold that forces a hybrid**: 1M followers. For regular users (< 1M followers), fan-out on write is fine — 1 post × avg 500 followers = 500 writes, trivial. For celebrities (> 1M followers), fan-out on write is 1 post × 150M writes = too slow (would take hours at 100K writes/sec). Celebrity posts are excluded from write fan-out; instead, they're fetched on read and merged into the pre-built feed.

**Step 5 — Feed storage**
Each user's feed is a Redis sorted set: `feed:{user_id}` → sorted by timestamp, entries are post IDs. Keep the last 1,000 posts. On feed load: `ZREVRANGE feed:{user_id} 0 49` returns 50 post IDs in <1ms. Then batch-fetch post details from a posts cache or DB. For users with a cold/empty feed cache, fall back to a DB query.

**Step 6 — Likes and engagement at scale**
At 100M DAU × 50 likes/day = 5B writes/day = 58K writes/sec. A normalized `likes` table with one row per like at 58K writes/sec exceeds single-DB capacity.
- Use Cassandra COUNTER columns (`likes_count COUNTER`) for write-heavy like counts — Cassandra is write-optimized, eventually consistent, and horizontally scalable.
- For "did I like this post?": Bloom filter per post (probabilistic, fast) or a Redis Set `liked:{post_id}` for hot posts.

**Step 7 — IDs**
Can't use auto-increment across a distributed system. Use Snowflake IDs with an embedded shard ID — IDs are globally unique, time-sortable, and generated locally without coordination. Instagram's variant: a PostgreSQL function that generates 64-bit IDs with epoch-relative timestamp + shard ID + sequence, allowing any shard to generate unique IDs independently.

---

## Real-Life Analogy

Instagram is Twitter but visual. The underlying feed mechanics — fan-out on write for regular users, fan-out on read for celebrities — are essentially identical to Twitter. But the photo upload is what makes Instagram uniquely hard.

Imagine submitting a photo to a magazine. You don't just email them a JPEG. The photo goes through an editorial pipeline: resize for print vs. web vs. mobile, apply quality adjustments, generate thumbnail, tag for search, and route to the right printing system. Only after all of that is the photo "published." If any step in that pipeline fails, the photo either never appears or appears broken.

The unique challenge for Instagram: **photo upload is a multi-step pipeline** (upload raw → compress → generate multiple sizes → push to CDN → save metadata to DB) that must be **atomic** (all steps succeed or the post doesn't exist) and **idempotent** (retrying a failed step doesn't create duplicates). At 50M posts/day, even a 0.1% failure rate means 50,000 failed uploads daily that users expect to retry.

---

## Why This Is Hard

1. **Upload pipeline atomicity**: If S3 upload succeeds but DB write fails, the user has an orphaned image. If transcoding succeeds but fan-out fails, the post exists but followers never see it. You need compensating transactions or a saga pattern.
2. **Storage scale**: 400 TB/day of media. That's not a "database problem" — it's a fundamentally different infrastructure problem (object storage, CDN, storage tiering).
3. **Hot post handling**: A viral post with 10M likes in an hour. Naive design: 10M writes to a `like_count` column. This serializes all like updates through a single DB row — immediate bottleneck.
4. **Feed generation for 100M DAU at 10 feeds/day** = 1 billion feed requests/day = ~11,500 reads/sec average, 34,500/sec peak. The fan-out + merge must be fast enough to be invisible to users.
5. **Stories with 24-hour expiry**: 24-hour TTL at scale requires either active cleanup (expensive background jobs) or leveraging infrastructure-level expiry (S3 lifecycle policies).
6. **Snowflake IDs for posts**: Auto-incrementing IDs don't work across shards. UUIDs are 128-bit and break B-tree index locality. Custom time-sortable IDs (Snowflake-style) are needed.

---

## Requirements

### Functional Requirements
1. **Upload photos/videos** (max 10 photos per post, 60s video)
2. **Follow/Unfollow** users
3. **News Feed** showing posts from followed users (chronological + algorithmic ranking)
4. **Like, Comment, Share** posts
5. **Stories** (24-hour temporary posts)
6. **Direct Messaging** (text, photos, videos)
7. **Search** users and hashtags
8. **Explore page** with personalized recommendations

### Non-Functional Requirements
1. **High availability**: 99.95% uptime
2. **Low latency**: < 200ms for feed load
3. **Scalability**: 1 billion users, 100M DAU
4. **Eventual consistency** acceptable for likes/comments
5. **Global distribution**: Multi-region deployment

---

## Capacity Estimation

### Traffic Estimates
- **Daily Active Users (DAU)**: 100 million
- **Posts uploaded/day**: 50 million (0.5 posts per DAU)
- **Photos per post**: Average 3 photos
- **Total photos/day**: 150 million
- **Feed requests/user/day**: 10
- **Total feed requests/day**: 1 billion

### Storage Estimates
- **Average photo size**: 2 MB (compressed)
- **Average video size**: 20 MB (60s at 3 Mbps)
- **Daily storage**:
  - Photos: 150M × 2 MB = 300 TB/day
  - Videos (10% of posts): 5M × 20 MB = 100 TB/day
  - **Total**: 400 TB/day
- **5-year storage**: 400 TB × 365 × 5 = **730 PB**

### Bandwidth Estimates
- **Upload**: 400 TB / 86400s = **4.6 GB/sec**
- **Feed views**: 1B requests/day × 3 photos × 2 MB = 6 PB/day = **70 GB/sec**
- **With CDN caching** (80% cache hit): **14 GB/sec** from origin

---

## API Design

### 1. Upload Post
```http
POST /api/v1/posts
Authorization: Bearer <token>
Content-Type: multipart/form-data

{
  "caption": "Sunset at the beach",
  "images": [<binary1>, <binary2>],
  "location": {"lat": 37.7749, "lon": -122.4194},
  "tags": ["#sunset", "#beach"]
}

Response: 202 Accepted  (not 201 — image processing is async)
{
  "postId": "abc123",
  "status": "processing",
  "estimatedReadyAt": "2026-02-08T10:00:30Z"
}
```

Note: 202 Accepted is correct here — the post is queued for processing, not yet complete.

### 2. Get Feed
```http
GET /api/v1/feed?limit=20&cursor=xyz

Response: 200 OK
{
  "posts": [
    {
      "postId": "...",
      "userId": "...",
      "username": "john_doe",
      "imageUrls": ["https://cdn.instagram.com/abc123_640.jpg"],
      "caption": "...",
      "likes": 1250,
      "comments": 45,
      "timestamp": "2024-02-09T10:00:00Z"
    }
  ],
  "nextCursor": "abc"
}
```

**Cursor-based vs. offset pagination:**
Offset pagination (`?page=5`) breaks with new posts — inserting new items shifts everything, causing duplicates or gaps. Cursor-based pagination uses the last seen post ID as an anchor, unaffected by new inserts.

### 3. Like/Unlike Post
```http
POST /api/v1/posts/{postId}/like
DELETE /api/v1/posts/{postId}/like
```

### 4. Post Comment
```http
POST /api/v1/posts/{postId}/comments
{
  "text": "Beautiful shot!",
  "mentionedUsers": ["@jane_doe"]
}
```

---

## High-Level Architecture

```
┌─────┐   ┌─────────┐   ┌──────┐
│ iOS │   │ Android │   │  Web │
└──┬──┘   └────┬────┘   └──┬───┘
   └──────┬────┘            │
          │  HTTPS          │
          ▼                 │
  ┌───────────────┐         │
  │  CDN          │         │  (Static assets, media delivery)
  │ (CloudFront)  │◄────────┘
  └───────┬───────┘
          │ Cache miss
          ▼
  ┌───────────────┐
  │ Load Balancer │
  └───────┬───────┘
          │
  ┌───────┼────────────┬──────────────┐
  ▼       ▼            ▼              ▼
Upload  Feed        Social Graph   Search
Service Service     Service        Service
  │       │            │
  │       ▼            ▼
  │   Redis Cache   Graph DB
  │   (Timelines)   (Followers)
  │
  ▼
  Kafka
  │
  ├──► Image Processor (resize, compress, thumbnail)
  └──► Feed Fan-out Worker (push to follower caches)

Storage:
  ├── S3 (original + processed images)
  ├── PostgreSQL (users, posts metadata)
  ├── Cassandra (likes, comments - write-heavy)
  └── Redis (timeline caches, hot counters)
```

---

## Detailed Component Design

### 1. Image Upload & Processing Pipeline

**The critical design principle: separate upload acknowledgment from processing completion.**

```
Phase 1 (Synchronous, ~200ms):
  Client uploads → API generates postId → Stores raw to S3 → Writes metadata (status=processing) → Returns 202

Phase 2 (Asynchronous, ~30 seconds):
  Image Processor consumes from Kafka:
    → Download raw from S3
    → Generate variants:
        150×150 (thumbnail for profile grid)
        640×640  (standard feed)
        1080×1080 (full resolution)
        1080×1920 (story format, 9:16)
    → Compress:
        JPEG 85% quality (balance size/quality)
        WebP for modern browsers (30% smaller than JPEG)
    → Upload all variants to CDN origin
    → Update metadata: status=ready, cdn_urls=[...]

Phase 3 (Asynchronous, concurrent with Phase 2):
  Fan-out Worker consumes from Kafka:
    → Fetch follower list from Graph DB
    → For each follower with <1M followers: push postId to their Redis timeline
    → For celebrity poster: skip fan-out (handled on read)
```

**Handling pipeline failures:**

The pipeline must be idempotent — if any step fails and retries, it shouldn't create duplicate images or duplicate feed entries.

```java
// Idempotency: each processing step checks if already done
public void processImage(String postId, String s3Key) {
    // Check if already processed (idempotency guard)
    Post post = db.getPost(postId);
    if (post.getStatus() == PostStatus.READY) {
        return;  // Already processed — idempotent retry
    }

    List<ImageVariant> variants = generateVariants(s3Key);
    uploadVariantsToCDN(postId, variants);

    // Atomic metadata update
    db.updatePost(postId, PostStatus.READY, variants);
    // If this DB write fails, Kafka message is not committed
    // → Worker retries → idempotency guard at top prevents reprocessing
}
```

### 2. Snowflake ID Generation for Posts

**Why not auto-increment?**
- Auto-increment requires a single sequence generator — doesn't scale across shards
- Cross-shard inserts need coordination for unique IDs
- Reveals business metrics (total post count) to external users

**Instagram's Shard-Based ID:**

```
64-bit integer breakdown:
├─ 41 bits: Timestamp (milliseconds since epoch) → time-sortable
├─ 13 bits: Logical Shard ID → determines DB shard
└─ 10 bits: Sequence Number → prevents collision within same shard + millisecond
```

```java
public long generatePostId(int shardId) {
    long timestamp = System.currentTimeMillis() - EPOCH_MS;  // 41 bits
    long sequence = atomicCounter.getAndIncrement() & 0x3FF;  // 10 bits

    return (timestamp << 23) | ((long)shardId << 10) | sequence;
}
```

**Benefits:**
- Time-sortable (ORDER BY post_id is equivalent to ORDER BY created_at)
- Shard ID embedded in ID → router knows which shard to query without lookup
- No central coordinator needed (each shard generates its own IDs)

### 3. Feed Generation Strategy

Same hybrid model as Twitter, adapted for Instagram:

```java
public Feed generateFeed(String userId) {
    int followerCount = graphDb.getFollowerCount(userId);

    if (followerCount > 1_000_000) {
        // Celebrity: pull model — don't fanout, fetch on demand
        return pullFeed(userId);
    } else {
        // Regular user: push model — pre-computed cache
        return redis.get("feed:" + userId);
    }
}

public Feed pullFeed(String userId) {
    List<String> following = graphDb.getFollowing(userId, 200);
    List<Post> posts = new ArrayList<>();

    for (String followedUser : following) {
        posts.addAll(postDb.getRecentPosts(followedUser, 10));
    }

    // Rank by ML model (not just chronological)
    return rankingService.rank(posts, userId);
}
```

**Ranking vs. Chronological:**
Early Instagram was chronological. Algorithmic ranking (by engagement, interests, relationships) was added later. For system design interviews, start with chronological sorted sets in Redis, then mention ML-based ranking as an extension.

### 4. Database Schema

#### Users Table (PostgreSQL)
```sql
CREATE TABLE users (
    user_id BIGSERIAL PRIMARY KEY,
    username VARCHAR(30) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    full_name VARCHAR(100),
    bio TEXT,
    profile_pic_url VARCHAR(500),
    follower_count INT DEFAULT 0,   -- Denormalized for fast celebrity detection
    created_at TIMESTAMP DEFAULT NOW(),
    INDEX idx_username (username)
);
```

#### Posts Table (Sharded by user_id)
```sql
CREATE TABLE posts (
    post_id BIGINT PRIMARY KEY,     -- Snowflake ID (shard embedded)
    user_id BIGINT NOT NULL,
    caption TEXT,
    location JSONB,
    status VARCHAR(20) DEFAULT 'processing',  -- processing, ready, failed
    created_at TIMESTAMP DEFAULT NOW(),
    INDEX idx_user_created (user_id, created_at)
);

CREATE TABLE post_images (
    image_id BIGSERIAL PRIMARY KEY,
    post_id BIGINT REFERENCES posts(post_id),
    variant VARCHAR(20),     -- thumbnail, feed, full, story
    cdn_url VARCHAR(500),
    width INT,
    height INT,
    format VARCHAR(10)       -- jpeg, webp
);
```

#### Social Graph (PostgreSQL adjacency list — scalable for reads)
```sql
CREATE TABLE followers (
    follower_id BIGINT NOT NULL,
    followee_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    PRIMARY KEY (follower_id, followee_id),
    INDEX idx_followee (followee_id)  -- "Who follows this person?" query
);
```

**When to use a Graph DB (Neo4j)?**
For simple follow/follower relationships, PostgreSQL with the adjacency list above is sufficient and operationally simpler. Graph DBs shine for multi-hop queries ("friends of friends") — not needed for basic feed generation.

#### Likes (Cassandra — Write-Heavy)
```sql
CREATE TABLE likes (
    post_id TEXT,
    user_id BIGINT,
    created_at TIMESTAMP,
    PRIMARY KEY (post_id, user_id)   -- Prevents duplicate likes (PK constraint)
);

-- Like count (separate table for aggregation)
CREATE TABLE like_counts (
    post_id TEXT PRIMARY KEY,
    count COUNTER
);

-- Increment count atomically
UPDATE like_counts SET count = count + 1 WHERE post_id = 'abc123';
```

**Why Cassandra for likes?**
A viral post gets 10M likes. That's 10M writes to one `post_id` partition. Cassandra's COUNTER type handles this without locking, using distributed increment with conflict-free merging.

### 5. Timeline Cache (Redis)

```java
// Sorted set: score = post timestamp, member = post_id
redis.zadd("feed:" + userId, postTimestamp, postId);

// Retrieve feed (most recent first, top 20)
List<String> postIds = redis.zrevrange("feed:" + userId, 0, 19);

// Fetch full post data in batch (N+1 query avoided)
List<Post> posts = db.batchGetPosts(postIds);

// TTL: 7 days
redis.expire("feed:" + userId, 604800);
```

**Memory optimization:**
- Store only post IDs in Redis (not full post data)
- Fetch full post metadata in a single batch DB query
- Cache hot post metadata separately: `post:{postId}` → JSON blob, 1 hour TTL

### 6. Hot Post Handling (Viral Posts)

**Problem:** Viral post with 10M likes/hour. Cassandra COUNTER type serializes increments — becomes a bottleneck at extreme scale.

**Solution: Write-Behind Cache**

```java
// Buffer likes in Redis (fast, in-memory increment)
redis.incr("likes:" + postId);
redis.sadd("likers:" + postId, userId);  // For deduplication

// Batch flush to Cassandra every 10 seconds (background worker)
for (String postId : redis.scanKeys("likes:*")) {
    long count = redis.get("likes:" + postId);
    cassandra.execute(
        "UPDATE like_counts SET count = count + ? WHERE post_id = ?",
        count, postId
    );
    redis.delete("likes:" + postId);
}
```

This reduces Cassandra write pressure from 10M/hour to ~360 batched writes/hour for that post.

---

## Scalability Strategies

### 1. Database Sharding

```
Posts sharded by user_id (not post_id):
  Reason: Most queries are "get posts by user X" — co-locating a user's posts
          on one shard avoids scatter-gather.

Shard key: user_id % 16
  Shard 0: user_ids where user_id % 16 = 0
  ...
  Shard 15: user_ids where user_id % 16 = 15

Cross-shard fan-out: Acceptable — followers table on each shard,
                    fan-out workers query the right post shard per followee
```

### 2. CDN Strategy

```
200+ PoPs globally

Cache hierarchy:
  Edge PoP (1-50ms from user)
    → Regional Cache (50-100ms)
      → S3 Origin

Cache headers:
  Post images: Cache-Control: max-age=86400, immutable
  (Images never change once uploaded — immutable is a strong hint to CDN)

Image optimization by device:
  Modern browser → WebP (30% smaller)
  iOS/Android → HEIC or JPEG depending on OS version
  Slow connection → Downgrade to lower resolution variant
```

### 3. Stories (24-Hour Expiry)

```java
// Upload story to S3 with lifecycle tag
s3.putObject(bucket, "stories/user123/story456.mp4", videoData,
    ObjectMetadata.withTag("expires", "24h"));

// S3 Lifecycle Policy:
// objects tagged "expires: 24h" in prefix "stories/" → delete after 1 day

// Track active stories in Redis sorted set (score = expiry timestamp)
redis.zadd("active_stories:" + userId, expiryTimestamp, storyId);
redis.expireat("active_stories:" + userId, expiryTimestamp);

// Query active stories for a user's following list
// (Client filters by Redis TTL, S3 object exists check as fallback)
```

### 4. Hashtag Search (Elasticsearch)

```json
{
  "mappings": {
    "properties": {
      "hashtag": {"type": "keyword"},
      "post_id": {"type": "keyword"},
      "created_at": {"type": "date"},
      "likes": {"type": "integer"}
    }
  }
}
```

Elasticsearch index updated asynchronously (Kafka consumer writes to ES). Eventual consistency on search is acceptable (new posts appear in search within seconds).

---

## Advanced Features

### 1. Explore Page Ranking

```java
// Composite relevance score for ranking candidates
score = (
    0.3 * text_similarity(post.caption, user.interests) +
    0.4 * engagement_rate(post) +               // likes/impressions
    0.2 * recency_score(post.created_at) +      // decay function
    0.1 * creator_authority(post.user_id)        // follower count signal
)
```

Two-stage ranking:
1. **Candidate generation**: Retrieve top 1,000 candidates (collaborative filtering, content-based)
2. **Re-ranking**: Apply ML model to score and sort the 1,000 candidates, return top 50

### 2. Duplicate Upload Detection

```
Problem: User retries a failed upload → duplicate post
Solution: Perceptual hash (pHash) of uploaded image
  → Compute pHash client-side before upload
  → Include in upload request
  → Server checks against recent uploads by same user (last 24h)
  → If match found: return existing postId (idempotent)
```

---

## Trade-offs

| Aspect | Choice | Trade-off |
|--------|--------|-----------|
| **Feed Generation** | Hybrid (push + pull) | Complexity vs. performance for all user types |
| **Like Storage** | Cassandra COUNTER | Eventual consistency vs. write throughput |
| **Image Storage** | S3 + CDN | $0.023/GB vs. $0.10+/GB for DB; CDN integration |
| **Post ID** | Snowflake (shard-embedded) | No central coordinator vs. shard ID reveals sharding |
| **Graph Storage** | PostgreSQL adjacency list | Simplicity vs. Graph DB query power |
| **Story expiry** | S3 lifecycle policy | Zero cleanup cost vs. PostgreSQL TTL complexity |

---

## Failure Scenarios

### Upload Pipeline Failure

If transcoding fails mid-pipeline:
- Kafka offset not committed → automatic retry
- Idempotency guard prevents re-processing completed steps
- After 3 retries: post status = `failed`, user notified via push notification
- Raw file preserved in S3 for manual recovery

### Fan-out Service Outage

- Kafka retains messages for 7 days
- When fan-out service recovers, it catches up from last committed offset
- Users see feeds populated in reverse order (newest first) as workers process backlog
- Acceptable SLA: eventual consistency — feed fully populated within minutes

---

## Interview Discussion Points

**Q: How to handle celebrity users with 100M followers?**
- Pull-based feed for celebrities — never fan-out to 100M Redis keys
- Separate queue for celebrity tweet ingestion with priority workers
- Followers see celebrity posts within seconds (eventual consistency acceptable)

**Q: Preventing duplicate photo uploads?**
- Perceptual hashing (pHash): Generate hash of image content
- Compare with existing hashes in bloom filter for fast rejection
- Full comparison for bloom filter positives to eliminate false positives
- Trade-off: Some near-duplicate photos missed vs. significant computation to detect all

**Q: Optimizing feed load time?**
- **Prefetch**: Pre-load next page while user scrolls (speculative loading)
- **Progressive rendering**: Render blurry thumbnail first, replace with full image on load
- **Cursor-based pagination**: Avoid offset pagination which breaks with new inserts
- **Connection-aware quality**: Detect bandwidth, serve 360p on slow connections instead of 1080p

---

## Interview Questions Asked

### Meta
1. **"Design the Instagram feed for 1 billion users."** → Tests hybrid fan-out understanding; key answer: fan-out on write for normal users (< 10K followers), fan-out on read for celebrities; Redis stores pre-computed feed lists per user.

### Common Follow-ups
1. **"How does Meta actually implement the feed — why pull for high-follow accounts?"** → Tests real-world systems knowledge; writing to 100M Redis keys per post is O(100M) — instead, celebrity posts are fetched at read time and merged in memory with the pre-computed feed.
2. **"How do you deduplicate photos?"** → Tests content hashing; perceptual hash (pHash) on upload → check Bloom filter → full hash comparison on positives; trade-off is false positives vs. compute cost.
3. **"How do you handle CDN invalidation for deleted photos?"** → Tests cache invalidation; send purge request to CDN (e.g., CloudFront Invalidation API); short TTL (e.g., 60s) on image responses limits stale window; deleted photo ID added to a deny-list checked at CDN edge.
4. **"What are the architectural differences between Stories and Feed?"** → Tests product-to-engineering translation; Stories are ephemeral (24h TTL in object store), single-viewer ordered, no ranking needed; Feed is persistent, ranked by ML, fan-out required.
5. **"How do you count likes at scale?"** → Tests approximate counting; buffer increments in Redis (`INCR likes:{post_id}`), flush to DB every N seconds; use HyperLogLog for unique-liker counts to avoid storing every user ID.
