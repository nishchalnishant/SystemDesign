# Design Twitter/News Feed

> **Difficulty**: Medium  
> **Topics**: Fan-out, Timeline Generation, Caching, Redis  
> **Time**: 60 minutes  
> **Companies**: Meta, Twitter, LinkedIn, Instagram

## Problem Statement

Design a social media news feed like Twitter where users can:
- Post tweets (280 characters)
- Follow other users
- View personalized news feed (tweets from followed users)

## Scale

- 1 billion users
- 500 million DAU (Daily Active Users)
- Each user follows 200 people (average)
- Each user posts 2 tweets/day on average
- Each user views feed 10 times/day

## Capacity Estimation

```
Write QPS (tweets):
500M DAU × 2 tweets/day ÷ 86,400 sec = ~12K tweets/sec (average)
Peak (3×): 36K tweets/sec

Read QPS (feed views):
500M DAU × 10 views/day ÷ 86,400 sec = ~60K views/sec
Peak (3×): 180K views/sec

Storage (5 years):
Daily tweets: 500M users × 2 = 1B tweets/day
5 years: 1B × 365 × 5 = 1.8 trillion tweets
Per tweet: 500 bytes (text + metadata)
Total: 1.8T × 500B = 900 TB
With replication (3×): 2.7 PB
```

## API Design

```http
POST /api/v1/tweets
{
  "user_id": "user_123",
  "content": "Hello Twitter!",
  "media_urls": ["https://cdn.example.com/image.jpg"]
}

GET /api/v1/feed?user_id=user_123&page=1&size=20
Response:
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

## Database Schema

```sql
-- Users
CREATE TABLE users (
    user_id BIGINT PRIMARY KEY,
    username VARCHAR(50) UNIQUE,
    email VARCHAR(100),
    created_at TIMESTAMP
);

-- Tweets
CREATE TABLE tweets (
    tweet_id BIGINT PRIMARY KEY,
    user_id BIGINT,
    content VARCHAR(280),
    created_at TIMESTAMP,
    INDEX idx_user_created (user_id, created_at DESC)
);

-- Followers (bidirectional for feed generation)
CREATE TABLE followers (
    follower_id BIGINT,  -- Person doing the following
    followee_id BIGINT,  -- Person being followed
    created_at TIMESTAMP,
    PRIMARY KEY (follower_id, followee_id),
    INDEX idx_follower (follower_id),
    INDEX idx_followee (followee_id)
);

-- Timeline Cache (Redis)
Key: feed:user_123
Value: [tweet_456, tweet_789, tweet_101, ...]  -- Sorted list of tweet IDs
```

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
    ↓            Message Queue
    ↓                 ↓
PostgreSQL       Timeline Workers
(Tweets DB)      (Update followers' feeds)
```

## Fan-Out Approaches

### Approach 1: Fan-Out on Write (Push Model)

**How it works:**
```
1. User posts tweet
2. Immediately push to ALL followers' timelines
3. Store in Redis: feed:follower_1 → [new_tweet_id, ...]

Example:
Celebrity with 10M followers posts → 10M Redis writes!
```

**Pros:**
- Fast reads (timeline pre-computed)
- Read QPS: 180K/sec easily handled

**Cons:**
- Slow writes for users with many followers
- Hotspot problem (celebrity tweets)

**When to use:**
- Most users (< 10K followers)

---

### Approach 2: Fan-Out on Read (Pull Model)

**How it works:**
```
1. User posts tweet → store in DB
2. On feed request:
   a. Get list of followed users
   b. Query recent tweets from each (SELECT * FROM tweets WHERE user_id IN (...) ORDER BY created_at LIMIT 20)
   c. Merge and sort by timestamp
```

**Pros:**
- Fast writes
- No hotspot problem

**Cons:**
- Slow reads (100+ DB queries to generate feed)
- Complex merge-sort logic

**When to use:**
- Users who follow many people (>1000)

---

### Hybrid Approach (Best!)

```
For normal users (<10K followers):
  Fan-out on write → Push to followers' Redis cache

For celebrities (>10K followers):
  Fan-out on read → Fetch celebrity tweets on-demand

Timeline Generation:
1. Fetch pre-computed timeline from Redis (fan-out on write)
2. Merge with celebrity tweets (fan-out on read)
3. Sort by timestamp
4. Return top 20
```

**Implementation:**
```java
public List<Tweet> getTimeline(String userId, int page, int size) {
    // Get cached timeline
    List<String> cachedTweetIds = redis.lrange("feed:" + userId, 0, size * 2);
    List<Tweet> cachedTweets = getTweetsByIds(cachedTweetIds);
    
    // Get celebrity tweets (users followed with >10K followers)
    List<String> celebrityIds = getCelebrityFollowees(userId);
    List<Tweet> allTweets;
    
    if (!celebrityIds.isEmpty()) {
        List<Tweet> celebrityTweets = db.query(
            "SELECT * FROM tweets WHERE user_id IN (?) " +
            "AND created_at > NOW() - INTERVAL '7 days' " +
            "ORDER BY created_at DESC LIMIT 50",
            celebrityIds
        );
        
        // Merge
        allTweets = mergeAndSort(cachedTweets, celebrityTweets);
    } else {
        allTweets = cachedTweets;
    }
    
    // Paginate
    int start = (page - 1) * size;
    return allTweets.subList(start, Math.min(start + size, allTweets.size()));
}
```

## Caching Strategy

```
Redis Cache:
Key: feed:user_123
Value: Sorted list of tweet IDs (last 500 tweets)
TTL: 7 days (older tweets expire)

Cache-aside:
1. Check Redis for timeline
2. If miss → generate from DB
3. Cache result in Redis
```

## Scaling

**Database Sharding:**
```
Shard tweets by tweet_id:
Shard 1: tweet_id % 4 = 0
Shard 2: tweet_id % 4 = 1
...

Shard users by user_id:
Shard 1: user_id % 4 = 0
...

Challenge: Following graph spans shards
Solution: Denormalize followers table to multiple shards
```

**Redis Cluster:**
```
128 hash slots distributed across nodes
feed:user_123 → hash(user_123) % 128 → Node X
```

## Interview Tips

**Key Trade-offs:**
- Fan-out on write: Fast reads, slow writes (celebrities)
- Fan-out on read: Fast writes, slow reads
- Hybrid: Best of both

**Common Questions:**
- Q: "How do you handle a celebrity with 100M followers?"
- A: Fan-out on read for celebrities, avoid pushing to 100M Redis keys

- Q: "What if Redis goes down?"
- A: Fallback to DB (slower but working), auto-restart Redis
