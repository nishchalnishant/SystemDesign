# HLD Interview Problems Catalog

> **27 curated system design problems organized by difficulty**

## How to Use This Catalog

1. **Start with Easy**: Build confidence with fundamental concepts
2. **Progress to Medium**: Apply advanced patterns (caching, sharding, replication)
3. **Tackle Hard**: Complex distributed systems, multi-region, consistency challenges

**For Each Problem:**
- Use [`hld-template.md`](file:///Users/nishchalnishant/Documents/GitHub/SystemDesign/5.%20Interview%20Templates/hld-template.md) for structured approach
- Practice capacity estimation with [`capacity-estimation.md`](file:///Users/nishchalnishant/Documents/GitHub/SystemDesign/5.%20Interview%20Templates/capacity-estimation.md)
- Reference [`trade-offs-cheat-sheet.md`](file:///Users/nishchalnishant/Documents/GitHub/SystemDesign/5.%20Interview%20Templates/trade-offs-cheat-sheet.md) for decisions

---

## Difficulty Levels

### Easy (1-2 weeks experience)
**Focus:** Basic architecture, simple scaling, single DB

### Medium (SDE-2 level)
**Focus:** Distributed systems, caching, replication, load balancing

### Hard (SDE-3/Staff level)
**Focus:** Multi-region, consensus, complex trade-offs, production operations

---

## Problem List

### Easy Problems (7 problems)

| # | Problem | Key Concepts | Estimated Time |
|---|---------|--------------|----------------|
| 1 | **URL Shortener** | Hashing, Base62, DB sharding | 45 min |
| 2 | **Pastebin** | Key generation, S3 storage, CDN | 45 min |
| 3 | **Key-Value Store** | Hash map, persistence, simple replication | 45 min |
| 4 | **Rate Limiter** | Token bucket, sliding window, Redis | 45 min |
| 5 | **Web Crawler** | BFS/DFS, politeness policy, distributed queue | 60 min |
| 6 | **Autocomplete** | Trie data structure, caching, prefix search | 45 min |
| 7 | **Unique ID Generator** | Snowflake, UUID, timestamp-based | 30 min |

**Interview Frequency:** ⭐⭐⭐⭐⭐ (Very Common)

---

### Medium Problems (12 problems)

| # | Problem | Key Concepts | Estimated Time |
|---|---------|--------------|----------------|
| 8 | **Twitter** | Timeline generation, fan-out, Redis caching | 60 min |
| 9 | **Instagram** | Image storage (S3), CDN, follower/feed model | 60 min |
| 10 | **YouTube** | Video transcoding, CDN, recommendation algorithm | 60 min |
| 11 | **Notification Service** | Push notifications, pub-sub, fan-out | 60 min |
| 12 | **News Feed** | Ranking algorithm, pagination, denormalization | 60 min |
| 13 | **Chat System (WhatsApp)** | WebSockets, message queue, read receipts | 60 min |
| 14 | **Search Engine (Google)** | Inverted index, PageRank, distributed crawling | 75 min |
| 15 | **Dropbox** | File chunking, sync, version control, delta sync | 60 min |
| 16 | **Netflix** | Video streaming, adaptive bitrate, CDN | 60 min |
| 17 | **Uber** | Real-time location, geospatial indexing (quadtree) | 60 min |
| 18 | **Yelp/Nearby** | Geohashing, proximity search, read-heavy | 60 min |
| 19 | **API Rate Limiter** | Token bucket, distributed rate limiting, Redis | 45 min |

**Interview Frequency:** ⭐⭐⭐⭐ (Common)

---

### Hard Problems (8 problems)

| # | Problem | Key Concepts | Estimated Time |
|---|---------|--------------|----------------|
| 20 | **Distributed Message Queue (Kafka)** | Partitioning, replication, consumer groups | 75 min |
| 21 | **Payment System (Stripe)** | ACID transactions, double-entry bookkeeping, idempotency | 75 min |
| 22 | **Stock Exchange** | Order matching, low-latency, consistency | 75 min |
| 23 | **Ticketmaster (Booking)** | Concurrency, distributed locking, overbooking prevention | 75 min |
| 24 | **Google Drive** | Conflict resolution, operational transform, sync | 75 min |
| 25 | **Distributed Cache (Redis Cluster)** | Consistent hashing, replication, failover | 60 min |
| 26 | **Search autocomplete (Google-scale)** | Trie sharding, distributed aggregation, caching | 60 min |
| 27 | **Ad Click Aggregator** | Stream processing, Lambda architecture, late data handling | 75 min |

**Interview Frequency:** ⭐⭐⭐ (Occasional, Senior+ roles)

---

## Problem Selection Guide

### For FAANG Interviews

**Most Common (Practice First):**
1. URL Shortener (Easy)
2. Rate Limiter (Easy)
3. Twitter/News Feed (Medium)
4. Notification Service (Medium)
5. Chat System (Medium)

**Company-Specific:**
- **Google**: Search Engine, YouTube, Autocomplete
- **Meta**: News Feed, Instagram, Chat System
- **Amazon**: Product Search,Distributed Cache
- **Netflix**: Video Streaming
- **Uber**: Real-time Location, Geospatial Search

---

## Practice Roadmap

### Week 1-2: Easy Problems
- [ ] URL Shortener
- [ ] Pastebin
- [ ] Rate Limiter
- [ ] Unique ID Generator

**Goal:** Nail fundamentals (API design, capacity estimation, basic scaling)

### Week 3-4: Medium Problems (Part 1)
- [ ] Twitter
- [ ] Notification Service
- [ ] Chat System
- [ ] Instagram

**Goal:** Master caching, replication, fan-out patterns

### Week 5-6: Medium Problems (Part 2)
- [ ] Search Engine
- [ ] Netflix
- [ ] Uber
- [ ] Dropbox

**Goal:** Geospatial indexing, CDN, real-time systems

### Week 7-8: Hard Problems
- [ ] Distributed Message Queue
- [ ] Payment System
- [ ] Stock Exchange

**Goal:** Consensus, transactions, extreme consistency/latency requirements

---

## Key Patterns Across Problems

### Pattern 1: Caching (80% of problems)
```
Problems: Twitter, Instagram, YouTube, Yelp, Autocomplete
Strategy: Cache-aside with TTL, Redis/Memcached
Key: Identify hot data (80/20 rule)
```

### Pattern 2: Sharding/Partitioning (60% of problems)
```
Problems: URL Shortener, Twitter, Message Queue, Distributed Cache
Strategy: Consistent hashing, range-based, hash-based
Key: Choose partition key to distribute load evenly
```

### Pattern 3: Pub-Sub/Message Queue (50% of problems)
```
Problems: Notification, Chat, News Feed, Kafka
Tools: Kafka, RabbitMQ, SQS
Key: Decouple producers/consumers, async processing
```

### Pattern 4: Geospatial Indexing (10% of problems)
```
Problems: Uber, Yelp, Nearby
Data Structures: Quadtree, Geohash, S2 geometry
Key: Efficient proximity search
```

### Pattern 5: CDN (40% of problems)
```
Problems: YouTube, Netflix, Instagram, Pastebin
Strategy: Push vs Pull CDN, cache static assets
Key: Serve from edge locations, reduce origin load
```

---

## Resources

- **Template**: [`hld-template.md`](file:///Users/nishchalnishant/Documents/GitHub/SystemDesign/5.%20Interview%20Templates/hld-template.md)
- **Estimation**: [`capacity-estimation.md`](file:///Users/nishchalnishant/Documents/GitHub/SystemDesign/5.%20Interview%20Templates/capacity-estimation.md)
- **Trade-offs**: [`trade-offs-cheat-sheet.md`](file:///Users/nishchalnishant/Documents/GitHub/SystemDesign/5.%20Interview%20Templates/trade-offs-cheat-sheet.md)
- **Numbers**: [`numbers-to-know.md`](file:///Users/nishchalnishant/Documents/GitHub/SystemDesign/6.%20Reference/numbers-to-know.md)

---

## Interview Tips

1. **Time Management**: 15 min estimation, 30 min design, 15 min deep dive
2. **Start Simple**: Single server → Scale out
3. **Justify Choices**: "I chose Redis because..." (not just "use Redis")
4. **Ask Questions**: Clarify requirements before diving in
5. **Think Production**: Monitoring, failures, costs

**Good luck! 🚀**
