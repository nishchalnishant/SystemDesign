---
module: 05-hld-problems
topic: Medium
status: unread
tags: [05-hld-problems, system-design, medium, instagram, social-feed, media-storage, follow-graph]
---
# Design Instagram

> **Difficulty**: Medium | **Asked at**: Meta, Snap, Pinterest, TikTok

---

## Problem Statement

Design a photo/video sharing platform like Instagram. Users upload photos and videos, follow other users, and see a feed of posts from accounts they follow. The system must handle celebrity accounts with millions of followers and support real-time feed updates.

---

## Functional Requirements

1. **Upload media**: Users upload photos (up to 20MB) and videos (up to 60s)
2. **User feed**: See posts from followed accounts, ranked by recency or engagement
3. **Follow/unfollow**: Follow other users; following is directed (A follows B ≠ B follows A)
4. **Like and comment**: Engage with posts
5. **Discover**: Explore page with trending posts and suggested accounts
6. **Stories**: Ephemeral content visible for 24 hours

---

## Non-Functional Requirements

- **Scale**: 500M DAU, 100M posts/day → 1,150 writes/sec; 10B feed views/day → 115K reads/sec
- **Latency**: Feed load < 200ms P99; photo upload < 2s
- **Availability**: 99.99% — feed reads must work even during partial DB failure
- **Consistency**: Feed can be eventually consistent (user doesn't need to see a post within milliseconds of upload); like counts can lag
- **Storage**: 100M posts/day × 1 MB avg (compressed) = 100 TB/day media storage

---

## Core Entities

| Entity | Key Fields |
|--------|-----------|
| `User` | user_id, username, bio, follower_count, following_count, profile_pic_url |
| `Post` | post_id, user_id, media_urls[], caption, created_at, like_count, comment_count |
| `Follow` | follower_id, followee_id, created_at |
| `Like` | user_id, post_id, created_at |
| `Comment` | comment_id, post_id, user_id, text, created_at |
| `Feed` | user_id, post_id, score (denormalized feed cache) |

---

## API Design

```http
POST /api/v1/posts
Body: multipart/form-data: { media: <file>, caption: "...", tags: [...] }
Response 201: { "post_id": "p123", "status": "processing", "media_url": "..." }

GET /api/v1/feed?user_id=u456&cursor=<timestamp>&limit=20
Response 200: {
  "posts": [{ "post_id": "...", "user": {...}, "media_url": "...", "like_count": 4200 }],
  "next_cursor": "..."
}

POST /api/v1/posts/{post_id}/likes
Response 201: { "like_count": 4201 }

POST /api/v1/users/{user_id}/follow
Body: { "followee_id": "u789" }
Response 201
```

---

## High-Level Design

```
Client
  │
  ▼
CDN (CloudFront) — media files, static assets
  │
  ▼
API Gateway / Load Balancer
  │
  ├── Post Service → S3 (raw media)
  │                → Media Processing Pipeline (Kafka → Lambda: resize, transcode)
  │                → PostgreSQL (post metadata)
  │
  ├── Feed Service → Redis (precomputed feed per user, sorted set by score)
  │                → Fan-out Workers (Kafka → push post to followers' feed caches)
  │
  ├── Follow Service → Graph DB / PostgreSQL (follow relationships)
  │                  → Redis (follower list cache)
  │
  └── Engagement Service → Redis (like counts, comment counts)
                         → Cassandra (like records, comment records)

Fan-out on write: when user A posts, push post_id to all followers' feed caches
Fan-out on read: for celebrity accounts (>1M followers), skip fan-out; pull on read
```

---

## Deep Dive 1: Feed Generation — Push vs Pull

**The core problem**: Instagram has accounts with 1M+ followers. When Cristiano Ronaldo posts, do you push that post to 500M followers' feeds immediately?

**Push (fan-out on write)**:
- On post, fan out to all followers: add `post_id` to each follower's Redis feed sorted set
- Feed reads are O(1) — pre-computed, just fetch from Redis
- Problem: 500M follower writes for one celebrity post = 500M Redis writes in seconds. Takes hours with 100K writes/sec throughput.

**Pull (fan-out on read)**:
- Feed is computed on demand: fetch the list of accounts the user follows → query latest posts from each → merge and rank
- No pre-computation. Works for celebrities.
- Problem: if user follows 1,000 accounts, feed load requires 1,000 DB queries → too slow.

**Hybrid approach** (Instagram's actual design):
- **Regular users (< 1M followers)**: Fan-out on write. When they post, push to all followers' Redis feed caches. Fast reads.
- **Celebrity users (> 1M followers)**: Skip fan-out on write. On feed load, fetch latest posts from celebrities separately, merge with the pre-computed feed from non-celebrity follows.

**Redis feed structure**: Sorted set per user, keyed `feed:{user_id}`, score = `post_timestamp`, member = `post_id`. `ZREVRANGE feed:u456 0 19` returns the 20 most recent post IDs.

---

## Deep Dive 2: Media Upload and Processing

**Problem**: Users upload raw photos and videos. The system needs to store them durably, transcode videos to multiple resolutions, and serve them globally with low latency.

**Upload flow**:
1. Client requests presigned S3 upload URL (`POST /api/v1/posts/upload-url`)
2. Client uploads directly to S3 (bypasses app servers — no bandwidth bottleneck)
3. S3 triggers an event to Kafka `raw-media-uploaded` topic
4. Media Processing Worker consumes event:
   - Photo: resize to 1080p, 720p, 400p thumbnails → upload to S3 processed/
   - Video: transcode to HLS (H.264, multiple bitrates: 1080p, 720p, 480p) → upload to S3
5. Worker writes processed media URLs to PostgreSQL post record
6. Post becomes visible in feeds

**Why presigned S3 upload**: Direct-to-S3 upload eliminates the bandwidth cost of routing 100M uploads/day through app servers. App servers only handle the metadata.

**CDN for media**: CloudFront distributes media globally. CDN origin = S3. First request to an edge node fetches from S3 and caches for 24h. Subsequent requests served from the edge node nearest the viewer. P99 media load < 50ms globally.

**Storage cost**: 100M posts/day × 1 MB avg × 3 resolutions ≈ 300 TB/day. S3 Intelligent-Tiering automatically moves infrequently accessed media (posts > 6 months old) to cheaper storage tiers (Glacier).

---

## Deep Dive 3: Follow Graph and Social Graph Queries

**Problem**: The follow graph (who follows whom) is queried constantly — feed generation reads follower lists, "suggested accounts" queries mutual follows, follower counts are displayed on profiles.

**Storage**: `follow(follower_id, followee_id, created_at)` table in PostgreSQL. Indexes on both `follower_id` (to answer "who does user A follow?") and `followee_id` (to answer "who follows user A?").

**Follower list in Redis**: For active users, cache their following list: `following:{user_id}` = SET of followee_ids. Used by fan-out worker to enumerate followers. TTL = 1 hour (invalidated on follow/unfollow).

**Follower count**: Maintained as a counter in Redis (`follower_count:{user_id}`). Incremented on follow, decremented on unfollow. Periodically reconciled with the DB count.

**Mutual follow (DM eligibility)**: Instagram restricts DMs to mutual follows (bidirectional). Check: `EXISTS(SELECT 1 FROM follow WHERE follower_id=A AND followee_id=B) AND EXISTS(SELECT 1 FROM follow WHERE follower_id=B AND followee_id=A)`. Cached in Redis as a Bloom filter for fast O(1) check.

**Graph database**: For "suggested accounts" (friends of friends), queries require graph traversal. A graph DB (Neo4j, Amazon Neptune) can answer "who has the most mutual follows with user A?" efficiently. The core follow table remains in PostgreSQL; the graph DB is a read-only replica for recommendations.

---

## Interviewer Questions by Level

**Junior**:
- What is a social graph? How do you store follow relationships?
- How do you serve photos and videos globally with low latency?
- What's the difference between a push and pull feed model?

**Mid-level**:
- Explain the hybrid fan-out model. How do you decide when to use push vs pull for a given user?
- How does a presigned S3 URL work? Why use it instead of uploading through the app server?
- How do you maintain the follower count — as a DB query or a cached counter?

**Senior**:
- When a celebrity with 500M followers posts, what happens in your system over the next 60 seconds?
- Design the media processing pipeline — resize, transcode, serve globally — for 100M uploads/day.
- How would you implement the Instagram Stories feature (24-hour expiry, view tracking)?
- How would you detect and prevent coordinated inauthentic behavior (bot followers, fake engagement)?
