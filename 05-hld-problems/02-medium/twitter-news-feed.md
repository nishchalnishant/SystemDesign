> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design Twitter/news feed — one of the most classic interview problems; the core challenge is serving personalized timelines at millisecond latency for users following celebrities.
>
> **Key design decisions:**
> - Fan-out-on-write: when a user tweets, push to all followers' timeline caches (Redis); reads are fast (O(1)); expensive for celebrities (10M followers = 10M writes)
> - Fan-out-on-read: pull tweets from all followees on timeline load; simple writes; slow reads for users following many accounts
> - Hybrid: fan-out-on-write for normal users (<10K followers); fan-out-on-read for celebrities; merge at read time (get pre-computed timeline + pull last 100 celebrity tweets)
> - Timeline storage: Redis sorted set per user (tweet_id by timestamp); ZRANGE for chronological feed; TTL 7 days
> - Tweets DB: Cassandra (tweet_id, user_id, content, created_at, like_count); tweet_id as row key; timeline sharded by user_id
> - Media: photos/videos stored in S3 + CDN; tweet stores S3 URL; media is separate from tweet metadata
> - Search: Elasticsearch indexing tweet content; separate from timeline serving
>
> **Key takeaway:** The celebrity (hotspot) problem is the hardest part — always answer it proactively with the hybrid approach; interviewers will specifically ask about it.

---
module: 05-hld-problems
topic: Medium
status: unread
tags: [05-hld-problems, system-design, medium, twitter, news-feed, fan-out, timeline, social]
---
# Design Twitter / News Feed

> **Difficulty**: Medium | **Asked at**: Twitter, Meta, LinkedIn, Google

---

## Problem Statement

Design a Twitter-like social platform where users post tweets (text + media, up to 280 chars), follow other users, and see a feed (timeline) of tweets from accounts they follow. The system must handle celebrity accounts with millions of followers and serve personalized timelines at millisecond latency.

---

## Functional Requirements

1. **Post tweet**: Create a tweet (text, images, videos, polls)
2. **Home timeline**: See tweets from followed accounts, ranked by recency or engagement
3. **Follow/unfollow**: Directed follow graph
4. **Retweet / Like / Reply**: Social engagement on tweets
5. **Search**: Full-text search over tweets
6. **Trends**: Top trending hashtags and topics in real time

---

## Non-Functional Requirements

- **Scale**: 300M MAU, 500M tweets/day → 5,800 writes/sec; 50B timeline reads/day → 580K reads/sec
- **Latency**: Timeline load < 200ms P99
- **Availability**: 99.99% — timelines must load even if some services are degraded
- **Consistency**: Eventual consistency for timeline (a new tweet may take a few seconds to appear in all followers' feeds)
- **Fan-out**: A tweet from an account with 100M followers must not cause a 30-minute delay in delivery

---

## Core Entities

| Entity | Key Fields |
|--------|-----------|
| `User` | user_id, username, follower_count, following_count, is_verified |
| `Tweet` | tweet_id, user_id, text, media_urls[], created_at, like_count, retweet_count, reply_to_tweet_id |
| `Follow` | follower_id, followee_id, created_at |
| `Like` | user_id, tweet_id, created_at |
| `Timeline` | user_id, tweet_id, score (denormalized, stored in Redis) |

---

## API Design

```http
POST /api/v1/tweets
Body: { "text": "Hello world!", "media_ids": [], "reply_to": null }
Response 201: { "tweet_id": "t123", "created_at": "...", "url": "https://x.com/user/t123" }

GET /api/v1/timeline?user_id=u456&cursor=<tweet_id>&count=20
Response 200: {
  "tweets": [{ "tweet_id": "...", "text": "...", "user": {...}, "like_count": 500 }],
  "next_cursor": "..."
}

POST /api/v1/tweets/{tweet_id}/likes
Response 201: { "like_count": 501 }

POST /api/v1/tweets/{tweet_id}/retweets
Response 201: { "retweet_count": 42 }

GET /api/v1/search?q=systemdesign&filter=latest
Response 200: { "tweets": [...] }
```

---

## High-Level Design

```
Client
  │
  ▼
API Gateway / Load Balancer
  │
  ├── Tweet Service → PostgreSQL (tweet storage, sharded by tweet_id)
  │                → Kafka (tweet-created events)
  │                → Elasticsearch (full-text search index)
  │
  ├── Timeline Service → Redis (precomputed timelines per user)
  │                    → Fan-out Worker (consumes Kafka events)
  │
  ├── Follow Service → PostgreSQL (follow graph)
  │                  → Redis (follower lists cache)
  │
  └── Engagement Service → Redis (like/retweet counts)
                         → Cassandra (engagement records)

Fan-out Worker (Kafka consumer):
  On tweet-created:
    - For regular users: push tweet_id to all followers' Redis timelines
    - For celebrities: skip fan-out; pull on timeline read
```

---

## Deep Dive 1: Timeline Fan-Out — The Celebrity Problem

**The problem**: Katy Perry has 100M followers. When she tweets, naive fan-out requires 100M Redis writes. At 1M writes/second, that's 100 seconds of fan-out — followers don't see the tweet for over a minute.

**Twitter's hybrid fan-out**:

**Regular users** (< ~100K followers): Fan-out on write. The fan-out worker reads the user's followers from Redis, pushes the tweet_id to each follower's Redis timeline sorted set. Fast writes enable fast reads.

**Celebrity users** (> ~100K followers): Skip write-time fan-out. Instead, when a user opens their timeline:
1. Fetch their precomputed timeline from Redis (contains tweets from non-celebrity follows)
2. Fetch the latest tweets from celebrities they follow (direct DB read, limited to top 10 celebrities)
3. Merge and rank both sets

**Timeline storage** (Redis sorted set):
```
key: timeline:{user_id}
member: tweet_id
score: tweet_timestamp (Unix ms)
```
`ZREVRANGE timeline:u456 0 19` returns the 20 most recent tweet IDs.

**Celebrity identification**: A background job periodically marks accounts with > 100K followers as `is_celebrity = true`. Followed on follow/unfollow event: if a user follows a celebrity, add the celebrity to `celebrity_follows:{user_id}` Redis set. Timeline service reads this set to know which celebrities to fetch on read.

---

## Deep Dive 2: Tweet Storage and Sharding

**Problem**: 500M tweets/day × 365 days × years of data = hundreds of billions of tweets. A single PostgreSQL instance cannot store this.

**Sharding strategy**: Shard by `user_id`. All tweets from the same user go to the same shard. This enables efficient "user profile" queries (`SELECT * FROM tweets WHERE user_id = X ORDER BY created_at`).

**Snowflake tweet IDs**: Twitter uses time-ordered 64-bit IDs (Snowflake). High bits are timestamp, enabling range queries by creation time without a secondary index. Cursor-based pagination uses tweet_id as the cursor.

**Timeline read for home page**: The timeline service retrieves tweet_ids from Redis, then fetches tweet content from the tweet service via batch lookup. The tweet service resolves tweet_ids to tweet objects and caches frequently read tweets in Redis (TTL = 1 hour).

**Cassandra as alternative**: Cassandra is better for append-only, time-series tweet data. Partition key: `user_id`, clustering key: `tweet_id DESC`. Each partition holds all tweets from one user, ordered newest-first. No sharding config needed — Cassandra handles distribution automatically.

---

## Deep Dive 3: Trending Topics and Real-Time Search

**Problem**: Twitter's trending hashtags must reflect what's happening right now — topics trending in the last 5-10 minutes, not the last day.

**Tweet ingestion pipeline**:
```
Tweet posted → Kafka `tweets` topic
  → Trending Worker: extract hashtags, mentions, keywords
    → Flink streaming job: count occurrences in 5-minute sliding windows
    → For each hashtag: if count > threshold → push to trending list
  → Elasticsearch: index tweet text, hashtags, author, timestamp
```

**Trending algorithm**:
- Count hashtag occurrences in 5-minute windows
- Apply velocity weighting: a hashtag going from 100→10,000 in 5 minutes ranks higher than a hashtag steadily at 5,000
- Geographic segmentation: trending topics differ by country/city. Separate counts per region.
- Cache top-50 trends per region in Redis, refreshed every minute.

**Full-text search (Elasticsearch)**:
- Tweets indexed in real-time (< 10s from post)
- Supports: keyword search, hashtag filter, user filter, date range, media filter
- "Latest" tweets: `SORT_BY: created_at DESC` — Elasticsearch can sort by time efficiently with index ordering
- "Top" tweets: Sort by engagement score (composite of likes, retweets, replies — precomputed and stored in ES document)

---

## Interviewer Questions by Level

**Junior**:
- What is a home timeline? How is it different from a user's profile page?
- Why can't you compute the timeline from scratch on every page load?
- What is fan-out and why is it challenging for celebrity accounts?

**Mid-level**:
- Explain the hybrid fan-out approach. At what follower threshold do you switch between push and pull?
- How do you paginate through a Twitter timeline? Why use cursor-based rather than offset-based pagination?
- How do like counts stay accurate under high concurrency?

**Senior**:
- A celebrity with 100M followers posts a tweet. Walk me through the full fan-out process end-to-end.
- How would you design real-time trending topics with geographic segmentation?
- How do you handle eventual consistency — a follower posts a tweet and sees it in their own timeline immediately, but their followers don't for 5 seconds?
- Design the Twitter search system — how do you index 500M tweets/day for sub-second full-text search?

---

## Back-of-Envelope Estimation

**Scale inputs (given in NFRs):**
- 300M MAU; 500M tweets/day; 50B timeline reads/day; celebrity accounts with 100M followers

**Write throughput:**
- 500M tweets/day ÷ 86,400 sec = **~5,800 writes/sec** (new tweets)
- Each tweet: `{tweet_id, user_id, text, media_urls[], created_at}` ≈ 300 bytes
- Write to DB: 5,800 × 300 bytes = **~1.7 MB/sec** — trivial

**Read throughput:**
- 50B timeline reads/day ÷ 86,400 sec = **~580,000 reads/sec**
- Read:write ratio = 580K ÷ 5,800 = **~100:1** — extremely read-heavy

**Fan-out workload:**
- 5,800 tweets/sec × average 200 followers = **1.16M fan-out events/sec**
- Peak celeb tweet: 1 tweet → 100M writes to follower timelines = **100M events** from 1 write
- At 1.16M events/sec: single celeb tweet takes **~86 seconds** to fan-out to all followers
- This motivates the hybrid approach: pre-compute ordinary users, pull-on-read for celebrities

**Timeline cache sizing:**
- 300M MAU × 800 tweets cached per user × 8 bytes (tweet_id pointer) = **~1.9 TB** for all timeline caches
- Only DAU (say 150M active today) needs hot cache: 150M × 800 × 8 bytes = **~960 GB** in Redis
- At $7/GB for Redis Enterprise: **~$6.7M/month** just for timeline Redis — motivates tiering (Redis for hot, Cassandra for older timeline)

**Tweet storage:**
- 500M tweets/day × 365 days × 300 bytes = **~54 TB/year** raw tweet text
- With media (30% of tweets include images, avg 100 KB): 500M × 30% × 100 KB = **~15 TB/day** media → **5.5 PB/year** media

**Architecture decisions driven by these numbers:**
- **Fan-out on write for non-celebrity accounts, pull on read for celebrities**: At 580K reads/sec, pulling and assembling a timeline from raw tweets + follow lists at read time (580K DB joins/sec) would require thousands of DB cores. Pre-computed timelines in Redis serve 580K reads/sec in < 1ms each. For celebrities (> 1M followers), fan-out on write takes 86+ seconds — user's tweet appears late. Hybrid: fan-out writes to followers of ordinary users; at read time, merge celebrity tweets from a separate celebrity tweet store.
- **Snowflake IDs for tweet ordering without DB sort**: Sorting 50B timelines/day by `created_at` requires time-based ordering of pointers in Redis sorted sets. Snowflake IDs (64-bit: 41-bit timestamp + 10-bit machine + 12-bit sequence) embed time, enabling `ZRANGEBYSCORE` on tweet_id as a proxy for time — no separate timestamp sort needed. At 5,800 new tweets/sec, Snowflake's 4,096 IDs/sec/machine × multiple machines provides sufficient uniqueness.
- **Cassandra for tweet storage over PostgreSQL**: 54 TB/year of tweet text with mostly append writes (new tweets, like count increments) and time-range reads (fetch tweets for user X between time T1 and T2). Cassandra's partition key `(user_id)` + clustering key `(tweet_id DESC)` gives O(1) write and efficient time-range scans. PostgreSQL sharding at this scale requires complex manual sharding; Cassandra scales horizontally by adding nodes with automatic rebalancing.
