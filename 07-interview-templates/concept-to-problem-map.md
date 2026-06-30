> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** A cross-reference guide linking theoretical system design concepts to practical interview problems that heavily feature those concepts.
>
> **Key concepts:**
> - If you want to practice WebSockets: Look at Chat Apps, Collaborative Editors, or Real-time Dashboards.
> - If you want to practice Graph Databases: Look at Social Network News Feeds or Recommendation Systems.
> - If you want to practice Geo-Spatial indexing (Quadtrees/Geohashes): Look at Uber/Ride-sharing or Yelp/Proximity services.
> - If you want to practice rate limiting: Look at API Gateways or DDoS protection designs.
>
> **Key takeaway:** Use this map bidirectionally. If you feel weak on "Event Sourcing", find the problem that tests it. If you're tackling "Design Uber", use this map to verify you've included the core expected concepts (Geospatial + Real-time updates).

---
module: 07-interview-templates
topic: Concept to Problem Map
status: unread
tags: [07-interview-templates, cross-reference, navigation]
---
# Concept → Problem Cross-Reference

Use this map two ways:
- **Learning a concept** → find which HLD problems demonstrate it in practice
- **Preparing a problem** → find which concepts and patterns you need to know first

---

## Building Block → HLD Problems

| Building Block | Problems That Use It |
|---------------|----------------------|
| **Caching (Redis)** | URL Shortener, Rate Limiter, Leaderboard, Twitter Feed, Instagram, Distributed Cache, Chat System, Autocomplete |
| **CDN** | YouTube, Instagram, Pastebin, Google Drive, Dropbox |
| **Load Balancing** | All problems — mention in every HLD |
| **Message Queue / Kafka** | Notification Service, YouTube (encoding), Twitter Feed (fan-out), Ad Click Aggregator, Distributed Message Queue, Payment System, Ride Sharing |
| **Sharding** | URL Shortener, Twitter Feed, Instagram, WhatsApp, Distributed Cache, Key-Value Store |
| **Replication** | Key-Value Store, Distributed Cache, Payment System, WhatsApp |
| **Rate Limiting** | Rate Limiter (primary), API Gateway (mention in all) |
| **Consistent Hashing** | Distributed Cache, Key-Value Store, Distributed Message Queue |
| **Bloom Filter** | Web Crawler (URL dedup), Distributed Cache (negative cache) |
| **Distributed Locks** | Ticketmaster (seat reservation), Booking System, Hotel Booking |
| **Circuit Breaker** | Payment System, Notification Service, E-Commerce |
| **API Gateway** | All microservices problems — mention in every HLD |
| **Service Discovery** | Microservices problems, Ride Sharing, E-Commerce |
| **Reverse Proxy** | All problems — Nginx/Envoy in front of API servers |

---

## Pattern → HLD Problems

| Pattern | Problems That Use It |
|---------|----------------------|
| **Saga** | Payment System, E-Commerce (order flow), Ride Sharing |
| **Outbox** | Payment System, Notification Service, Order Management |
| **CQRS** | Twitter Feed, E-Commerce, GitHub Code Repo |
| **Event Sourcing** | GitHub Code Repo, Distributed Message Queue |
| **Bulkhead** | Payment System, E-Commerce, Notification Service |
| **Two-Phase Commit** | Payment System (cross-bank), Distributed Cache |
| **Strangler Fig** | Any migration scenario — mention in system evolution questions |

---

## Algorithm → HLD Problems

| Algorithm / Data Structure | Problems |
|---------------------------|----------|
| **Consistent Hashing** | Distributed Cache, Key-Value Store, Distributed Message Queue |
| **Trie** | Autocomplete, Search System |
| **Inverted Index** | Search System, GitHub Code Repo |
| **Quadtree / Geohash** | Ride Sharing, Google Maps, Nearby Friends |
| **Snowflake ID** | URL Shortener, Twitter Feed, any problem needing unique IDs |
| **Token Bucket** | Rate Limiter, API Gateway |
| **Sliding Window** | Rate Limiter (alternative) |
| **LRU / LFU** | Distributed Cache, CDN, any caching layer |
| **Min-Heap** | Leaderboard (top-K), Job Scheduler (priority queue) |
| **Skip List** | Redis sorted sets → Leaderboard, Rate Limiter |

---

## HLD Problem → Concepts Needed

| Problem | Must Know Before Attempting |
|---------|---------------------------|
| URL Shortener | Hashing, Base62, caching, DB sharding |
| Rate Limiter | Token bucket, Redis atomic ops, sliding window |
| Unique ID Generator | Snowflake, clock skew, UUID trade-offs |
| Autocomplete | Trie, prefix caching, ranking |
| Web Crawler | BFS/DFS, distributed queues, dedup (Bloom filter) |
| Notification Service | Pub-sub, fan-out, Kafka, delivery guarantees |
| Twitter Feed | Fan-out (push vs pull), Redis sorted sets, sharding |
| Instagram | CDN, blob storage, feed generation |
| YouTube | Chunked upload, video transcoding, CDN |
| WhatsApp | WebSocket, message ordering, offline delivery |
| Distributed Cache | Consistent hashing, eviction policies, replication |
| Payment System | Idempotency, Saga, double-entry ledger, ACID |
| Ride Sharing | Geohash, matching algorithm, Saga |
| Google Drive | Chunking, dedup, sync conflict resolution |
| Distributed Message Queue | Kafka internals, partitioning, consumer groups |
| Ad Click Aggregator | Stream processing, Lambda/Kappa architecture, dedup |
| Search System | Inverted index, crawling, ranking (TF-IDF) |
| Stock Exchange | Order book, matching engine, low-latency design |
| Google Maps | Graph routing (Dijkstra/A*), geohash, ETA at scale |
| Chat System | WebSocket, message ordering, storage, offline delivery |

---

## Concept Dependency Chains

```
Caching (concepts) → Caching Layer (Redis) → Distributed Cache (HLD) → Cache Eviction policies
Sharding (concepts) → Sharding strategies → URL Shortener → Twitter Feed → Distributed Cache
Message Queues → Kafka internals → Notification Service → Distributed Message Queue → Ad Click Aggregator
Replication → Consistency models → Distributed Systems theory → Payment System
Idempotency → Retry patterns → Saga → Payment System → Distributed Transactions
```
