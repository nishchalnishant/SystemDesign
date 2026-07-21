> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** 21 hard HLD problems at SDE-3 / Staff level — multi-region systems, consensus, complex concurrency, and production operations; the most technically demanding interview questions.
>
> **Problems and core concepts:**
> - Distributed Cache: consistent hashing, virtual nodes, eviction (LRU/LFU), failover, replication
> - Distributed Message Queue (Kafka): partitions, ISR replication, consumer groups, exactly-once semantics
> - Payment System: double-entry accounting, idempotency, PCI DSS, reconciliation
> - Ride Sharing (Uber): geospatial indexing (S2/H3), driver matching, surge pricing, real-time ETA
> - Google Drive: chunked uploads, delta sync, conflict resolution (CRDT/OT), permission model
> - Chat System (Slack): WebSocket routing, channel fan-out, thread model, search indexing
> - Search Engine (Google): web crawl → inverted index → TF-IDF/BM25 + PageRank → serving
> - Ad Click Aggregator: Lambda/Kappa architecture, Flink stream processing, deduplication, fraud filtering
> - Google Maps: road graph routing (Dijkstra/A*), H3 geospatial indexing, tile serving, GPS ingestion
> - LLM Chat System: GPU routing, token streaming (SSE), context window management, cost optimization
> - RAG System: document chunking, embedding generation, HNSW vector search, prompt construction
> - Stock Exchange: order book (price-time priority), matching engine, market data fan-out, microsecond latency
>
> **Key takeaway:** Hard problems require multi-subsystem thinking + production details (failure modes, data model, SLOs) — interviewers expect you to proactively surface the hard concurrency or consistency problems.

---

# Hard — HLD Problems

High-level design problems at **SDE-3 / Staff** level. Focus: multi-region, consensus, complex trade-offs, production operations.

## Problems

| Problem | File | Key concepts |
|--------|------|----------------|
| **Distributed Cache (Redis)** | [distributed-cache.md](distributed-cache.md) | Consistent hashing, replication, failover |
| **Distributed Message Queue (Kafka)** | [distributed-message-queue.md](distributed-message-queue.md) | Partitioning, replication, consumer groups |
| **Payment System** | [payment-system.md](payment-system.md) | ACID, idempotency, double-entry |
| **Ride Sharing** | [ride-sharing.md](ride-sharing.md) | Real-time location, geospatial, matching |
| **Google Drive** | [google-drive.md](google-drive.md) | Sync, conflict resolution, chunking |
| **Chat System** | [chat-system.md](chat-system.md) | WebSockets, message queue, presence |
| **Search System** | [search-system.md](search-system.md) | Inverted index, ranking, scaling |
| **Ad Click Aggregator** | [ad-click-aggregator.md](ad-click-aggregator.md) | High-volume event ingestion, real-time vs batch aggregation, dedup |
| **Google Maps** | [google-maps.md](google-maps.md) | Geo-indexing, routing graphs, tile serving, ETA prediction |
| **LLM Chat System** | [llm-chat-system.md](llm-chat-system.md) | Streaming inference, context windows, session management, token cost |
| **RAG System** | [rag-system.md](rag-system.md) | Vector embeddings, semantic search, retrieval pipeline, LLM grounding |
| **Stock Exchange** | [stock-exchange.md](stock-exchange.md) | Order book, matching engine, low-latency, market data distribution |

Use the [HLD template](../../07-interview-templates/01-frameworks/01-hld-template.md) and [SYSTEM_DESIGN_INTERVIEW_FRAMEWORK.md](../../SYSTEM_DESIGN_INTERVIEW_FRAMEWORK.md) when practicing.
