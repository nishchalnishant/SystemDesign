# #24 Facebook news feed system

Here’s a complete, time-boxed, 1-hour interview-ready answer for designing a Facebook News Feed System. It follows your system design interview structure, including functional & non-functional requirements, APIs/data model, architecture, deep dive, and trade-offs.

***

## 0 – 5 min — Problem recap, scope & assumptions

<br>

Goal: Design a scalable system to generate a personalized news feed for users that displays posts from friends, followed pages, and sponsored content in real-time or near real-time.

<br>

Scope for interview:

* Users can see posts from friends, pages, and groups.
* Feed should be personalized based on relevance (likes, comments, recency).
* Support millions of users generating and consuming posts.
* Real-time updates for new posts, likes, and comments.

<br>

Assumptions:

* 1B+ users, each following hundreds of friends/pages.
* Peak posts per second: \~100K–1M.
* Feed displays top 50–100 posts.
* Users may access via mobile/web with low latency (<200 ms).

***

## 5 – 15 min — Functional & Non-Functional Requirements

<br>

#### Functional Requirements

<br>

Must

1. Personalized feed: Show relevant posts per user.
2. Post types: Support text, images, videos, links.
3. Real-time updates: Show new posts, likes, and comments quickly.
4. Sorting & ranking: Order posts based on relevance (recency, engagement, social graph).
5. Pagination / infinite scroll: Load more posts as the user scrolls.
6. Interactions: Like, comment, share, react to posts.

<br>

Should

* Include sponsored posts/ads.
* Filter content (e.g., hide posts from blocked users).
* Support feed caching for fast access.

<br>

Nice-to-have

* Trending posts or topics.
* Notifications for high-priority updates.
* A/B testing of ranking algorithms.

***

#### Non-Functional Requirements

* Latency: Feed retrieval <200 ms per request.
* Availability: 99.99% uptime globally.
* Scalability: Handle billions of posts and hundreds of millions of users.
* Durability: Persist posts, likes, comments, and feed state.
* Consistency: Eventual consistency acceptable for feed ranking; strong consistency for critical interactions (likes, comments).
* Monitoring: Track latency, cache hit/miss, errors, post engagement metrics.

***

## 15 – 25 min — API / Data Model

<br>

#### APIs

```
# Retrieve personalized feed
get_news_feed(user_id: str, limit: int=50, last_post_id: str=None) -> List[Post]

# Post a new message
create_post(user_id: str, content: str, media: Optional[str]=None)

# Interact with post
like_post(user_id: str, post_id: str)
comment_post(user_id: str, post_id: str, comment: str)
share_post(user_id: str, post_id: str)
```

#### Data Models

<br>

User

```
{
  "user_id": "string",
  "friends": ["user_id1", "user_id2"],
  "following_pages": ["page_id1"]
}
```

Post

```
{
  "post_id": "string",
  "user_id": "string",
  "content": "string",
  "media_url": "string|null",
  "timestamp": "datetime",
  "likes_count": int,
  "comments_count": int
}
```

Feed Entry

```
{
  "user_id": "string",
  "post_id": "string",
  "score": float,
  "timestamp": "datetime"
}
```

***

## 25 – 40 min — High-level architecture & data flow

```
[Client] --> [API Gateway] --> [Feed Service] --> [Cache Layer (Redis/Memcached)]
                                         |
                                         v
                                   [Post Store (DB)]
                                         |
                                         v
                            [Feed Generator / Ranking Engine]
                                         |
                                         v
                                  [Message Queue / Event Bus]
```

Components

1. Feed Service: Handles feed requests, fetches cached feed, or generates feed online.
2. Cache Layer: Stores top-k feed posts per user for low-latency retrieval.
3. Post Store: Persistent storage for all posts (NoSQL DB like Cassandra).
4. Feed Generator / Ranking Engine: Computes feed ranking using social graph, engagement, and recency.
5. Message Queue / Event Bus: Propagates new posts, likes, and comments to update feeds asynchronously.

<br>

Data Flow

1. User creates a post → Post Store + Event Bus.
2. Event triggers Feed Generator → updates top-k feeds in cache.
3. User requests feed → Feed Service fetches from cache → returns to client.
4. Interactions (like/comment/share) → update post engagement counters + Event Bus for feed ranking.

***

## 40 – 50 min — Deep dive — ranking, scaling, caching

<br>

#### Feed Ranking

* Score = αrecency + βengagement + γ\*relationship\_strength
* Ranking can use ML models for personalization.

<br>

#### Scaling

* Horizontal sharding: Partition users and posts by user\_id or hash.
* Caching: Top feeds per user in Redis.
* Push vs Pull model:
  * Push: Precompute feed for active users.
  * Pull: Generate feed on request (for low-activity users).
  * Hybrid model is optimal.

<br>

#### Fault Tolerance

* Replicate caches across regions.
* Asynchronous updates via Event Bus ensure eventual consistency.
* Backup Post Store for durability.

***

## 50 – 55 min — Back-of-the-envelope calculations

<br>

Assumptions

* 1B users, each following 200 friends → 200B potential posts to consider.
* Active users per second \~10M → fetch top 50 posts each → 500M entries/sec.
* Cache top 50 posts per active user → 10M users \* 50 posts \~ 500M entries → manageable with distributed cache.
* Latency: Cached feed retrieval <50 ms; online ranking fallback <200 ms.

<br>

Storage

* Post Store: Cassandra / DynamoDB for high write throughput.
* Cache: Redis cluster per region for top feed per user.
* Event Bus: Kafka for distributing post updates.

***

## 55 – 58 min — Monitoring & ops

<br>

Monitoring

* Feed request latency.
* Cache hit/miss ratio.
* Queue lag in Event Bus.
* Post engagement metrics (likes/comments/shares).

<br>

Operational concerns

* Handling spikes (breaking news, viral posts).
* Rolling updates to ranking algorithms.
* Data replication across regions for disaster recovery.

***

## 58 – 60 min — Trade-offs, evolution & summary

<br>

#### Trade-offs

* Push vs Pull: Push reduces latency but consumes more storage; Pull reduces storage but increases latency.
* Cache size vs freshness: Larger cache reduces recomputation but may serve slightly stale posts.
* SQL vs NoSQL: NoSQL preferred for high write throughput; SQL better for complex queries but slower.

<br>

#### Evolution

1. MVP: Pull-based feed with top posts from friends, simple recency ranking.
2. Phase 2: Hybrid push/pull, precompute top feeds for active users, caching.
3. Phase 3: ML-based personalized ranking, trending posts, ads/sponsored content, global scaling.

<br>

#### Summary

* System generates personalized news feeds in real-time.
* Uses push/pull hybrid model, caching, and event-driven updates.
* Horizontally scalable, fault-tolerant, low-latency (<200 ms), and monitors engagement for continuous improvement.

***

If you want, I can next create a sequence diagram showing a user creating a post, generating feed, caching, and user fetching feed, which is very handy to explain in interviews.

<br>

Do you want me to create that diagram next?
