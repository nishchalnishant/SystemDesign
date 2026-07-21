> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Catalog of 27 curated HLD (High-Level Design) interview problems organized by difficulty — Easy (9), Medium (7), Hard (21); the complete interview problem bank for system design practice.
>
> **Structure:**
> - Easy (01-easy/): 9 problems introducing one concept at a time — URL Shortener, Rate Limiter, Pastebin, Key-Value Store, Autocomplete, Web Crawler, Booking System, Leaderboard, Unique ID Generator
> - Medium (02-medium/): 7 problems combining 3–5 subsystems — Twitter, Instagram, YouTube, WhatsApp, Notification Service, E-Commerce, Typeahead Search
> - Hard (03-hard/): 21 problems requiring SDE-3 depth — Distributed Cache, Message Queue, Payment, Uber, Google Drive, Slack, Search Engine, Ad Click Aggregator, Google Maps, LLM Chat, RAG, Stock Exchange, + 9 more
>
> **How to practice:** Use hld-template.md for structure, capacity-estimation.md for math, trade-offs-cheat-sheet.md for decisions — time yourself at 45 min (easy) or 60 min (hard)
>
> **Key takeaway:** Complete Easy in order (concepts build sequentially), then Medium, then Hard; each problem file has a 5-minute summary at the top so you can quick-review before practicing.

---

# HLD Interview Problems Catalog

27 curated system design problems with files, organized by difficulty and recommended study order.

**For each problem:** use [hld-template.md](../07-interview-templates/01-frameworks/01-hld-template.md) for structure, [capacity-estimation.md](../07-interview-templates/02-cheat-sheets/02-capacity-estimation.md) for math, and [trade-offs-cheat-sheet.md](../07-interview-templates/02-cheat-sheets/01-trade-offs-cheat-sheet.md) for decision points.

---

## Easy (9 problems — do these first)

Each easy problem introduces one or two core concepts in isolation. Solve them in this order — later ones build on earlier ones.

| Order | Problem | File | Core Concept | Time |
|-------|---------|------|--------------|------|
| 1 | URL Shortener | [url-shortener.md](01-easy/url-shortener.md) | Hashing, Base62, DB sharding | 45 min |
| 2 | Unique ID Generator | [unique-id-generator.md](01-easy/unique-id-generator.md) | Snowflake, distributed ID without coordination | 30 min |
| 3 | Rate Limiter | [rate-limiter.md](01-easy/rate-limiter.md) | Token bucket, sliding window, Redis atomic ops | 45 min |
| 4 | Pastebin | [pastebin.md](01-easy/pastebin.md) | Object storage, CDN, TTL | 45 min |
| 5 | Key-Value Store | [key-value-store.md](01-easy/key-value-store.md) | Storage engine, LSM tree, replication | 45 min |
| 6 | Autocomplete | [autocomplete.md](01-easy/autocomplete.md) | Trie, prefix caching, ranking pipeline | 45 min |
| 7 | Web Crawler | [web-crawler.md](01-easy/web-crawler.md) | Distributed queues, Bloom filter dedup, politeness | 60 min |
| 8 | Booking System | [booking-system.md](01-easy/booking-system.md) | Inventory locking, idempotency, overbooking prevention | 45 min |
| 9 | Leaderboard | [leaderboard.md](01-easy/leaderboard.md) | Redis sorted sets, windowed ranking, score aggregation | 45 min |

---

## Medium (6 problems — SDE-2 level)

Medium problems require orchestrating multiple subsystems. Each one applies 3–5 building blocks together.

| Order | Problem | File | Key Challenge | Time |
|-------|---------|------|---------------|------|
| 10 | Notification Service | [notification-service.md](02-medium/notification-service.md) | Fan-out, async, multi-channel delivery | 60 min |
| 11 | Instagram | [instagram.md](02-medium/instagram.md) | Photo storage, CDN, feed generation | 60 min |
| 12 | YouTube | [youtube.md](02-medium/youtube.md) | Video encoding pipeline, chunked upload, adaptive bitrate | 60 min |
| 13 | WhatsApp | [whatsapp.md](02-medium/whatsapp.md) | WebSocket, message ordering, offline delivery | 60 min |
| 14 | Twitter News Feed | [twitter-news-feed.md](02-medium/twitter-news-feed.md) | Fan-out at scale, push vs. pull, Redis sorted sets | 60 min |
| 15 | E-Commerce Platform | [e-commerce-platform.md](02-medium/e-commerce-platform.md) | Inventory management, cart, checkout, order lifecycle | 60 min |

---

## Hard (12 problems — SDE-3/Staff level)

Hard problems test distributed systems depth: multi-region, consensus, exactly-once semantics, extreme latency constraints.

| Order | Problem | File | Key Challenge | Time |
|-------|---------|------|---------------|------|
| 16 | Distributed Cache | [distributed-cache.md](03-hard/distributed-cache.md) | Consistent hashing, eviction, cluster topology | 60 min |
| 17 | Chat System | [chat-system.md](03-hard/chat-system.md) | Real-time messaging, storage, offline delivery | 75 min |
| 18 | Search System | [search-system.md](03-hard/search-system.md) | Inverted index, ranking, crawl pipeline | 75 min |
| 19 | Payment System | [payment-system.md](03-hard/payment-system.md) | Idempotency, exactly-once, double-entry ledger | 75 min |
| 20 | Ride Sharing | [ride-sharing.md](03-hard/ride-sharing.md) | Geo-indexing, real-time matching, dispatch | 75 min |
| 21 | Google Drive | [google-drive.md](03-hard/google-drive.md) | Chunking, dedup, sync protocol, conflict resolution | 75 min |
| 22 | Distributed Message Queue | [distributed-message-queue.md](03-hard/distributed-message-queue.md) | Partitioning, replication, consumer groups, Kafka internals | 75 min |
| 23 | Ad Click Aggregator | [ad-click-aggregator.md](03-hard/ad-click-aggregator.md) | High-volume ingestion, real-time vs batch aggregation, dedup | 75 min |
| 24 | Google Maps | [google-maps.md](03-hard/google-maps.md) | Geo-indexing, routing graphs, tile serving, ETA | 75 min |
| 25 | LLM Chat System | [llm-chat-system.md](03-hard/llm-chat-system.md) | Streaming inference, context management, token cost | 75 min |
| 26 | RAG System | [rag-system.md](03-hard/rag-system.md) | Vector embeddings, semantic search, retrieval pipeline | 75 min |
| 27 | Stock Exchange | [stock-exchange.md](03-hard/stock-exchange.md) | Order book, matching engine, low-latency, market data | 75 min |

---

## Patterns That Appear Across Problems

Recognizing these patterns lets you apply a known solution rather than inventing one from scratch.

| Pattern | Appears In | Building Block |
|---------|-----------|----------------|
| Cache-aside + TTL | URL Shortener, Twitter, Instagram, YouTube | [caching-layer.md](../02-building-blocks/02-performance/01-caching-layer.md) |
| Consistent hashing | URL Shortener, Distributed Cache, Web Crawler | [sharding.md](../02-building-blocks/03-data-partitioning/01-sharding.md) |
| Pub-sub / message queue | Notification, YouTube, Twitter, Ride Sharing | [message-brokers.md](../02-building-blocks/04-coordination/01-message-brokers.md) |
| Fan-out (push vs. pull) | Twitter, Instagram, WhatsApp | [replication.md](../02-building-blocks/03-data-partitioning/02-replication.md) |
| Bloom filter dedup | Web Crawler, Distributed Cache | [02-building-blocks/](../02-building-blocks/) |
| Idempotency key | Payment System, Notification, WhatsApp | [distributed-concepts.md](../04-advanced-topics/01-distributed-architecture/02-distributed-concepts.md) |
| Geo-indexing | Ride Sharing | [02-building-blocks/](../02-building-blocks/) |

---

## Company-Specific Frequency

| Company | High-Frequency Problems |
|---------|------------------------|
| Google | Web Crawler, Autocomplete, Search System, Distributed Cache, Google Maps, Ad Click Aggregator |
| Meta | Twitter News Feed, Instagram, WhatsApp, Notification Service |
| Amazon | Rate Limiter, Distributed Message Queue, Unique ID Generator, E-Commerce Platform |
| Uber | Ride Sharing, Distributed Message Queue, Booking System |
| Stripe/PayPal | Payment System |
| Jane Street / Robinhood | Stock Exchange |
| OpenAI / Anthropic / AI companies | LLM Chat System, RAG System |
