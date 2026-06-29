# HLD Interview Problems Catalog

21 curated system design problems with files, organized by difficulty and recommended study order.

Use [hld-template.md](../07-interview-templates/hld-template.md), [hld-cheat-sheet.md](../07-interview-templates/hld-cheat-sheet.md), and [capacity-estimation.md](../07-interview-templates/capacity-estimation.md) on every timed run.

---

## Easy (9 problems)

| Order | Problem | File | Core Concept | Time |
|-------|---------|------|--------------|------|
| 1 | URL Shortener | [url-shortener.md](01-easy/url-shortener.md) | hashing, Base62, DB sharding | 45 min |
| 2 | Unique ID Generator | [unique-id-generator.md](01-easy/unique-id-generator.md) | Snowflake, distributed IDs | 30 min |
| 3 | Rate Limiter | [rate-limiter.md](01-easy/rate-limiter.md) | token bucket, Redis atomic ops | 45 min |
| 4 | Pastebin | [pastebin.md](01-easy/pastebin.md) | object storage, CDN, TTL | 45 min |
| 5 | Key-Value Store | [key-value-store.md](01-easy/key-value-store.md) | storage engine, replication | 45 min |
| 6 | Autocomplete | [autocomplete.md](01-easy/autocomplete.md) | trie, prefix cache, ranking | 45 min |
| 7 | Web Crawler | [web-crawler.md](01-easy/web-crawler.md) | queues, dedup, politeness | 60 min |
| 8 | Booking System | [booking-system.md](01-easy/booking-system.md) | inventory locking, idempotency | 45 min |
| 9 | Leaderboard | [leaderboard.md](01-easy/leaderboard.md) | Redis sorted sets, aggregation | 45 min |

---

## Medium (7 problems — SDE-2 level)

| Order | Problem | File | Key Challenge | Time |
|-------|---------|------|---------------|------|
| 10 | Notification Service | [notification-service.md](02-medium/notification-service.md) | fan-out, async delivery, retries | 60 min |
| 11 | E-Commerce Platform | [e-commerce-platform.md](02-medium/e-commerce-platform.md) | cart, inventory, order lifecycle | 60 min |
| 12 | WhatsApp | [whatsapp.md](02-medium/whatsapp.md) | WebSocket, ordering, offline delivery | 60 min |
| 13 | YouTube | [youtube.md](02-medium/youtube.md) | upload, transcoding, CDN | 60 min |
| 14 | Instagram | [instagram.md](02-medium/instagram.md) | media storage, feed generation | 60 min |
| 15 | Twitter News Feed | [twitter-news-feed.md](02-medium/twitter-news-feed.md) | push vs pull fan-out | 60 min |
| 16 | Uber / Ride-Sharing | [uber.md](02-medium/uber.md) | geo-indexing, real-time matching | 60 min |

---

## Hard (5 problems)

These are useful for deeper practice and Amazon follow-ups, even if the interview stays at SDE-2 scope.

| Order | Problem | File | Key Challenge | Time |
|-------|---------|------|---------------|------|
| 17 | Distributed Cache | [distributed-cache.md](03-hard/distributed-cache.md) | consistent hashing, replication, hot keys | 60 min |
| 18 | Distributed Job Scheduler | [distributed-job-scheduler.md](03-hard/distributed-job-scheduler.md) | leases, retries, idempotency | 75 min |
| 19 | Payment System | [payment-system.md](03-hard/payment-system.md) | idempotency, ledger, saga | 75 min |
| 20 | Hotel Booking | [hotel-booking.md](03-hard/hotel-booking.md) | inventory consistency and overbooking | 75 min |
| 21 | Ticketmaster Seat Booking | [ticketmaster-seat-booking.md](03-hard/ticketmaster-seat-booking.md) | seat locking and high-concurrency checkout | 75 min |

---

## Amazon-Specific Priority

For Amazon SDE-2, prioritize:

1. Rate Limiter
2. Unique ID Generator
3. E-Commerce Platform
4. Notification Service
5. Booking System
6. Web Crawler
7. Distributed Job Scheduler
8. Distributed Cache
9. Payment System
10. Ticketmaster Seat Booking

Amazon interviewers often push on idempotency, SQS/SNS fan-out, DynamoDB access patterns, retries, DLQs, alarms, and operational excellence.

---

## Patterns Across Problems

| Pattern | Appears In | Building Block |
|---------|------------|----------------|
| cache-aside + TTL | URL Shortener, Instagram, Twitter, Distributed Cache | [caching-layer.md](../02-building-blocks/caching-layer.md) |
| consistent hashing | URL Shortener, Distributed Cache | [consistent-hashing.md](../02-building-blocks/consistent-hashing.md) |
| pub-sub / queue | Notification, YouTube, Web Crawler, Job Scheduler | [message-brokers.md](../02-building-blocks/message-brokers.md) |
| idempotency key | Payment, Booking, Job Scheduler, E-Commerce | [api-design-template.md](../07-interview-templates/api-design-template.md) |
| geo-indexing | Uber | [uber.md](02-medium/uber.md) |
| seat/inventory locking | Booking, Hotel Booking, Ticketmaster | [ticketmaster-seat-booking.md](03-hard/ticketmaster-seat-booking.md) |
