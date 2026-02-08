# #25 forum like system like quora, reddit or hackernews

Here’s a complete, time-boxed, 1-hour interview-ready answer for designing a Forum/Q\&A system (like Quora, Reddit, HackerNews). It follows your system design interview structure, including functional & non-functional requirements, APIs/data model, architecture, deep dive, and trade-offs.

***

## 0 – 5 min — Problem recap, scope & assumptions

<br>

Goal: Design a system where users can post questions, answers, discussions, or threads; upvote/downvote content; comment; and browse content sorted by relevance or popularity.

<br>

Scope for interview:

* Users can create posts, comment, upvote/downvote.
* Support categorization (tags, topics, communities).
* Feed should show relevant/popular content.
* Support search for content by keyword or tags.
* Handle millions of users and posts with high read/write traffic.

<br>

Assumptions:

* 100M+ users, 1B+ posts/comments.
* Peak read requests: \~10M/sec.
* Peak write requests: \~1M/sec.
* Support web and mobile clients.
* Real-time updates not strictly required (eventual consistency is acceptable).

***

## 5 – 15 min — Functional & Non-Functional Requirements

<br>

#### Functional Requirements

<br>

Must

1. Post creation: Users can create questions, answers, or discussion threads.
2. Commenting: Users can comment on posts.
3. Voting: Upvote/downvote posts and comments.
4. Tagging: Categorize posts by topics/tags.
5. Search: Search posts by keywords, tags, or authors.
6. Feed/Ranking: Show top/relevant content based on popularity, recency, or user preferences.

<br>

Should

* Support following topics, communities, or users.
* Moderation tools for inappropriate content.
* Notifications for new answers/comments.

<br>

Nice-to-have

* Trending topics/posts.
* Personalized recommendations.
* Bookmarking/favorites for posts.

***

#### Non-Functional Requirements

* Latency: Page load/read operations <200 ms; posting <500 ms.
* Availability: 99.99% uptime.
* Scalability: Handle millions of users, high read/write ratio.
* Durability: Persist posts, votes, comments permanently.
* Consistency: Eventual consistency acceptable for feeds; strong consistency for votes.
* Monitoring: Track active users, posts, votes, API latency, and errors.

***

## 15 – 25 min — API / Data Model

<br>

#### APIs

```
# Posts
create_post(user_id: str, content: str, tags: List[str]) -> post_id
get_post(post_id: str) -> Post
get_posts_by_tag(tag: str, limit: int, last_post_id: str) -> List[Post]

# Comments
add_comment(user_id: str, post_id: str, content: str) -> comment_id
get_comments(post_id: str, limit: int, last_comment_id: str) -> List[Comment]

# Voting
vote_post(user_id: str, post_id: str, vote_type: str)
vote_comment(user_id: str, comment_id: str, vote_type: str)
```

#### Data Models

<br>

User

```
{
  "user_id": "string",
  "username": "string",
  "followed_topics": ["topic1", "topic2"]
}
```

Post

```
{
  "post_id": "string",
  "user_id": "string",
  "content": "string",
  "tags": ["tag1", "tag2"],
  "timestamp": "datetime",
  "votes": int,
  "comments_count": int
}
```

Comment

```
{
  "comment_id": "string",
  "post_id": "string",
  "user_id": "string",
  "content": "string",
  "timestamp": "datetime",
  "votes": int
}
```

Vote

```
{
  "user_id": "string",
  "post_id": "string|null",
  "comment_id": "string|null",
  "vote_type": "up/down",
  "timestamp": "datetime"
}
```

***

## 25 – 40 min — High-level architecture & data flow

```
[Client] --> [API Gateway] --> [Forum Service] --> [Cache Layer]
                                       |
                                       v
                                   [DB Layer]
                                       |
                                       v
                               [Search / Indexing Service]
                                       |
                                       v
                                  [Message Queue]
```

Components

1. Forum Service: Handles CRUD operations for posts, comments, and votes.
2. Cache Layer: Redis/Memcached for hot posts, top posts per tag/topic, and user feeds.
3. Database Layer: NoSQL (e.g., Cassandra) for posts/comments; SQL for relational data if needed.
4. Search/Indexing Service: ElasticSearch for keyword and tag search.
5. Message Queue: Kafka for async updates to feeds, notifications, and search indexing.

<br>

Data Flow

1. User creates post → Forum Service writes to DB → publishes event to Message Queue.
2. Indexing Service consumes event → updates search index.
3. Cache updated with top posts per topic/user feed.
4. User fetches feed/search results → served from cache/DB/Search.

***

## 40 – 50 min — Deep dive — ranking, scaling, caching

<br>

#### Ranking & Feed

* Sort posts by score = αvotes + βrecency + γ\*comments.
* Use hotness algorithm (e.g., Reddit’s time-decay formula).
* Personalized feed can consider followed topics, users, or communities.

<br>

#### Scaling

* Horizontal sharding: Partition posts/comments by topic, user\_id, or post\_id hash.
* Caching: Hot posts per topic, trending posts, and user feeds in Redis.
* Read-heavy optimization: Since reads >> writes, use replication, cache, and CDNs.

<br>

#### Fault Tolerance

* Replicated DBs for durability.
* Retry failed message queue events.
* Eventual consistency acceptable for feeds; votes require stronger consistency.

***

## 50 – 55 min — Back-of-the-envelope calculations

<br>

Assumptions

* 100M users, each following \~50 topics → 5B post feeds to generate.
* Active users/day: 10M → fetch top 50 posts → 500M entries.
* Each post \~500 bytes → 250 GB in cache (distributed across multiple nodes).
* Write throughput: 1M posts/comments/sec → NoSQL DB for high write scalability.
* Latency: Cache hits <10 ms; DB reads <50 ms; feed generation <200 ms.

***

## 55 – 58 min — Monitoring & ops

<br>

Monitoring

* API latency and errors.
* Cache hit/miss ratios.
* Active posts, comments, votes per time window.
* Trending topics analytics.

<br>

Operational concerns

* Scaling search index as post volume grows.
* Managing spam, abuse, and moderation.
* Rolling deployments for service updates without downtime.

***

## 58 – 60 min — Trade-offs, evolution & summary

<br>

#### Trade-offs

* SQL vs NoSQL: NoSQL for high write throughput; SQL better for joins/relations but slower.
* Push vs Pull feeds: Push updates for active users for low latency; Pull for low-activity users to save storage.
* Caching: Improves read latency but may serve slightly stale content.

<br>

#### Evolution

1. MVP: Basic post/comment/vote system, pull-based feed per topic.
2. Phase 2: Hot/trending posts cache, ranking algorithm, search index.
3. Phase 3: Personalized feeds, notifications, moderation tools, analytics dashboards.

<br>

#### Summary

* System supports posts, comments, votes, tagging, and search.
* Uses NoSQL DB, cache, and search index for scalable, low-latency reads.
* Feed ranking combines votes, recency, and comments.
* Horizontally scalable, fault-tolerant, and monitorable system suitable for millions of users.

***

If you want, I can next create a sequence diagram and class diagram for the forum system showing post creation, voting, caching, and feed generation, which is very helpful in interviews.

<br>

Do you want me to create those diagrams next?
