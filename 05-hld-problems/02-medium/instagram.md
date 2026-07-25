> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design Instagram — a photo/video sharing platform with follow-graph, feed generation, and media delivery at billions-of-users scale.
>
> **Key design decisions:**
> - Media storage: photos/videos stored in object storage (S3); CDN for delivery; thumbnail generation on upload via worker queue
> - Follow graph: adjacency list in DB (follower_id, followee_id); Redis cache of followed user IDs per user; graph DB for recommendations
> - Feed generation: fan-out-on-write (precompute timelines on post → fast reads, expensive celebrity writes) vs fan-out-on-read (pull on demand → simpler, slower for active users)
> - Hybrid: fan-out-on-write for normal users; fan-out-on-read for celebrities (>1M followers); merge at read time
> - Media upload: client → signed S3 URL (direct upload, bypass app server) → async CDN propagation + thumbnail generation
> - Capacity: 1B users, 50M active daily, 100M posts/day → ~1150 posts/sec; read:write ≈ 100:1 (mostly browsing)
> - Storage: each photo ~300KB; 100M posts/day → 30TB/day; cold storage (S3 Glacier) after 90 days
>
> **Key takeaway:** The celebrity problem is the hardest part — pure fan-out-on-write breaks at 10M followers; hybrid (fan-out for normal users, pull-on-read for celebrities merged at read time) is the production solution.

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

> 🎯 **Staff signal:** The senior framing is that push and pull aren't a choice — they're each a *failure mode you route around per-user*. Fan-out-on-write gives O(1) reads but its cost is O(followers) per post, which detonates on a 500M-follower celebrity (hours to fan out one post); fan-out-on-read is O(follows) per feed load, which detonates on users who follow thousands. The hybrid picks the cheaper failure for each account: push for the many normal users, pull for the few celebrities, merged at read time. Name the crossover — you switch strategies at a follower-count threshold because that's where write-amplification overtakes read-amplification. Choosing the fan-out direction by *which cost blows up for this specific account* is the E5→E6 line.

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

> 🎯 **Staff signal:** The move is keeping bytes off the app tier on both the write and read paths. On write, a presigned S3 URL lets the client PUT the raw 1 MB directly to S3 — your servers never touch the 100M-uploads/day of media bandwidth, only the metadata. Processing is *asynchronous and event-driven* (S3 event → Kafka → transcode worker → post becomes visible), so upload latency isn't hostage to transcoding a video into HLS ladders. On read, CDN edge caching turns the origin fetch into a one-time cost per PoP. Naming that the architecture's job is to make the app servers handle *only metadata* while S3+CDN+workers move and transform the bytes is the E5→E6 framing.

---

## Deep Dive 3: Follow Graph and Social Graph Queries

**Problem**: The follow graph (who follows whom) is queried constantly — feed generation reads follower lists, "suggested accounts" queries mutual follows, follower counts are displayed on profiles.

**Storage**: `follow(follower_id, followee_id, created_at)` table in PostgreSQL. Indexes on both `follower_id` (to answer "who does user A follow?") and `followee_id` (to answer "who follows user A?").

**Follower list in Redis**: For active users, cache their following list: `following:{user_id}` = SET of followee_ids. Used by fan-out worker to enumerate followers. TTL = 1 hour (invalidated on follow/unfollow).

**Follower count**: Maintained as a counter in Redis (`follower_count:{user_id}`). Incremented on follow, decremented on unfollow. Periodically reconciled with the DB count.

**Mutual follow (DM eligibility)**: Instagram restricts DMs to mutual follows (bidirectional). Check: `EXISTS(SELECT 1 FROM follow WHERE follower_id=A AND followee_id=B) AND EXISTS(SELECT 1 FROM follow WHERE follower_id=B AND followee_id=A)`. Cached in Redis as a Bloom filter for fast O(1) check.

**Graph database**: For "suggested accounts" (friends of friends), queries require graph traversal. A graph DB (Neo4j, Amazon Neptune) can answer "who has the most mutual follows with user A?" efficiently. The core follow table remains in PostgreSQL; the graph DB is a read-only replica for recommendations.

> 🎯 **Staff signal:** The senior insight is that "follow" is one relation queried in *three* access patterns that no single store serves well, so you fan it into purpose-built projections rather than forcing one. The authoritative edge lives in Postgres with indexes on *both* directions (`follower_id` and `followee_id`) because "who I follow" and "who follows me" are different queries; hot following-lists are cached as Redis SETs for the fan-out worker; and friends-of-friends traversal goes to a graph DB *read replica* because a 2-hop join is where SQL falls over. Name the discipline: keep one source of truth, derive read-optimized replicas per query shape, never let the recommendation graph become a second writable copy. Projecting one relation into multiple stores by query pattern is the E5→E6 line.

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

---

## Back-of-Envelope Estimation

**Scale inputs (given in NFRs):**
- 500M DAU; 100M posts/day; 10B feed views/day; 100 TB/day media storage

**Write throughput:**
- 100M posts/day ÷ 86,400 sec = **~1,150 writes/sec** (photo/video uploads)
- Each post record: `{post_id, user_id, media_urls[], caption, created_at}` ≈ 500 bytes
- Media: 100M posts × 1 MB compressed = **100 TB/day** → 1.16 GB/sec inbound to storage

**Read throughput:**
- 10B feed views/day ÷ 86,400 sec = **~115,700 reads/sec** (feed loads)
- Read:write ratio = 115,700 ÷ 1,150 = **~100:1** — read-heavy; justify a dedicated cache layer

**Fan-out on write (pre-computed feeds):**
- When a user with 1M followers posts: send 1M feed update events
- Average follower count: 200 followers/user
- 1,150 posts/sec × 200 = **230,000 feed write events/sec** to pre-compute
- Storage: 500M users × 20 feed items × 8 bytes (post_id pointer) = **80 GB** for all user feed caches in Redis

**Celebrity fan-out problem:**
- User with 100M followers posts: naively generates 100M feed writes → takes 100M ÷ 230K events/sec = **~7 minutes** to fan-out
- Solution: hybrid — pre-compute feeds for ordinary users (< 1M followers); pull-on-read for celebrity posts (merge celebrity posts at read time from a separate celebrity timeline store)

**Media storage:**
- 100 TB/day new uploads; 3× replication = **300 TB/day** written to S3
- 7-year retention (user content): 100 TB × 365 × 7 = **~255 PB** total over 7 years
- At $23/TB/month S3 Standard for hot + S3 Glacier at $4/TB/month for old content: archive after 90 days → cost ~$5M/month

**Architecture decisions driven by these numbers:**
- **Pre-computed feed cache in Redis (fan-out on write)**: At 115,700 feed reads/sec with < 200ms P99, querying a DB for each read would require joining posts + follows for each user — 500M users × 20 feed items = 10B DB reads/day. Redis feed cache (80 GB total) serves all reads in < 1ms. The 230K pre-write events/sec to maintain the cache is acceptable given the 100:1 read:write ratio.
- **Hybrid fan-out for celebrities**: Pure fan-out on write for a 100M-follower post takes 7 minutes of background work — users see the post 7 minutes late. Pure pull-on-read for all users means every feed load queries all following relationships (expensive DB joins). Hybrid: pre-compute for ≤ 1M followers (covers 99.9% of users); pull-and-merge celebrity posts at read time (affects < 0.1% of accounts but they have 99% of followers).
- **CDN for all media**: 100 TB/day uploads means 100 TB × (avg 10 views each) = 1 PB/day served. Serving from origin: 1 PB ÷ 86,400 sec = **~11.6 GB/sec** aggregate bandwidth — requires a massive CDN. CDN cache hit rate >95% for popular photos. Media is immutable (never updated) so cache TTL can be years. Origin only serves cache misses (< 5%) = 580 MB/sec origin bandwidth.

---

## Related

**Concepts used in this design**

- [CDN](../../02-building-blocks/01-networking/05-cdn.md)
- [Caching Layer](../../02-building-blocks/02-performance/01-caching-layer.md)
- [Sharding](../../02-building-blocks/03-data-partitioning/01-sharding.md)
- [Message Brokers](../../02-building-blocks/04-coordination/01-message-brokers.md)
- [Storage Fundamentals](../../01-foundations/02-hardware-and-networking/01-storage-fundamentals.md)

**Practice next**

- [Twitter News Feed](../02-medium/twitter-news-feed.md)
- [YouTube](../02-medium/youtube.md)

YouTube replaces the photo pipeline with transcoding.

**Frameworks**: [HLD Template](../../07-interview-templates/01-frameworks/01-hld-template.md) · [Capacity Estimation](../../07-interview-templates/02-cheat-sheets/02-capacity-estimation.md) · [Trade-offs Cheat Sheet](../../07-interview-templates/02-cheat-sheets/01-trade-offs-cheat-sheet.md)
