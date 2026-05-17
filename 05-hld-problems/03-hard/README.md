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

Use the [HLD template](../../07-interview-templates/hld-template.md) and [SYSTEM_DESIGN_INTERVIEW_FRAMEWORK.md](../../SYSTEM_DESIGN_INTERVIEW_FRAMEWORK.md) when practicing.
