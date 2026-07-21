> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** 7 medium HLD problems at SDE-2 level — each requiring orchestration of multiple subsystems (3–5 building blocks) working together.
>
> **Problems and core concepts:**
> - Twitter/News Feed: fan-out-on-write vs fan-out-on-read, timeline caching in Redis, celebrity problem (hybrid approach)
> - Instagram: photo/video storage (S3), CDN delivery, follow graph (adjacency list), feed generation
> - YouTube: video upload → transcoding pipeline (FFmpeg), multi-resolution storage (S3), CDN streaming, recommendations
> - WhatsApp: WebSocket persistent connections, message routing via message queue, delivery receipts, group messaging fan-out
> - Notification Service: multi-channel delivery (push/email/SMS), at-least-once with deduplication, priority queues
> - E-Commerce Platform: product catalog, inventory management, cart → checkout → payment → order lifecycle
> - Typeahead Search (Google): Trie with Elasticsearch backend, sub-50ms latency requirement, personalization layer
>
> **Key takeaway:** Medium problems test your ability to compose building blocks — always lead with data model, then add components one by one as scale demands.

---

# Medium — HLD Problems

High-level design problems at **SDE-2** level. Focus: distributed systems, caching, replication, load balancing.

## Problems

| Problem | File | Key concepts |
|--------|------|----------------|
| **Twitter / News Feed** | [twitter-news-feed.md](twitter-news-feed.md) | Timeline, fan-out, Redis |
| **Instagram** | [instagram.md](instagram.md) | Image storage, CDN, follower/feed |
| **YouTube** | [youtube.md](youtube.md) | Video transcoding, CDN, recommendations |
| **WhatsApp** | [whatsapp.md](whatsapp.md) | Chat, WebSockets, message queue |
| **Notification Service** | [notification-service.md](notification-service.md) | Push, pub-sub, fan-out, queues |
| **E-Commerce Platform** | [e-commerce-platform.md](e-commerce-platform.md) | Inventory, cart, checkout, order management, payment integration |

Use the [HLD template](../../07-interview-templates/01-frameworks/01-hld-template.md) and [trade-offs cheat sheet](../../07-interview-templates/02-cheat-sheets/01-trade-offs-cheat-sheet.md) when practicing.
