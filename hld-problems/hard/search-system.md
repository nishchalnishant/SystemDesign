# Design Search System (Web Search / Product Search)

> **Difficulty**: Hard  
> **Topics**: Inverted Index, Crawling, Ranking, Distributed Search, Caching  
> **Time**: 60 minutes  
> **Companies**: Google, Amazon, Meta, Microsoft

---

## 1. Requirements Clarification

### Functional requirements

**Must-have:**
- **Query**: User enters text; system returns ranked list of relevant documents (web pages, products, etc.).
- **Indexing**: New/updated documents are searchable within a bounded delay (e.g. minutes to hours).
- **Relevance**: Results ordered by relevance (and optionally recency, popularity).

**Nice-to-have:**
- Autocomplete / suggestions.
- Filters (date, category, site).
- Spelling correction (“did you mean?”).
- Personalized ranking (optional).

### Non-functional requirements

- **Latency**: P99 < 200–500 ms for query path.
- **Throughput**: 10K–100K queries per second (QPS) at scale.
- **Availability**: 99.9%+.
- **Freshness**: Index updated within agreed SLA (e.g. minutes for product search, hours for web crawl).
- **Scale**: Billions of documents; petabytes of index.

---

## 2. High-Level Architecture

```
  Users ──▶ Load Balancer ──▶ Query Servers (stateless)
                                    │
                    ┌───────────────┼───────────────┐
                    ▼               ▼               ▼
              Cache (hot        Search          Document
              queries)          Index           Store
              (Redis)           (sharded)       (e.g. blob)
                    │               │
                    │               │  Index built from
                    │               ▼
                    │         Indexer / Crawler
                    │               │
                    │               ▼
                    │         Raw Documents (queue + storage)
```

**Components:**
- **Query servers**: Parse query, fetch from cache or search index, rank/merge, return results.
- **Search index**: Inverted index (term → list of doc IDs + metadata); sharded by term or doc.
- **Cache**: Cache top-K results for popular queries (e.g. Redis).
- **Document store**: Raw documents (for snippets, metadata); can be blob store or DB.
- **Crawler / ingester**: Discovers or receives documents; sends to queue; indexer consumes and updates index.
- **Indexer**: Builds/updates inverted index from document stream.

---

## 3. API Design

### Search query

```http
GET /search?q=user+query&limit=20&offset=0&filter=category:electronics
```

**Response:**
```json
{
  "query": "user query",
  "results": [
    {
      "id": "doc_123",
      "title": "Document Title",
      "snippet": "Relevant excerpt...",
      "url": "https://...",
      "score": 0.95,
      "metadata": {}
    }
  ],
  "total_estimate": 1000000,
  "took_ms": 45
}
```

### Indexing (internal or admin)

```http
POST /index
Content-Type: application/json

{
  "doc_id": "doc_123",
  "title": "Title",
  "body": "Full text...",
  "url": "https://...",
  "metadata": { "category": "electronics" }
}
```

---

## 4. Data Model

### Inverted index (conceptual)

- **Term dictionary**: term → term_id (for compression).
- **Posting list**: term_id → list of (doc_id, frequency, positions, metadata).
- **Document store**: doc_id → (title, url, snippet, metadata).

**Sharding:**
- **By term hash**: Each shard owns a range of terms; query “hello world” → query shards for “hello” and “world”, then merge.
- **By document**: Each shard owns a subset of documents; full document index per shard; query broadcast to all shards, merge results. (Common in Elasticsearch-style systems.)

### Example (simplified)

```
Term "laptop" → [ (doc_1, freq=3), (doc_2, freq=1), (doc_5, freq=2) ]
Term "phone"  → [ (doc_2, freq=2), (doc_3, freq=1) ]

Query "laptop phone" → intersect/merge posting lists → rank → top K
```

---

## 5. Scaling Strategy

- **Query path**: 
  - Cache popular queries (e.g. 20% of traffic); cache hot posting lists if needed.
  - Query servers stateless; scale horizontally.
  - Index sharded (by term or by doc); each shard replicated for read capacity and HA.
- **Indexing path**:
  - Documents flow via queue (Kafka); indexer workers consume and update shards.
  - Index updates can be in-place (update posting list) or periodic merge (LSM-style).
- **Storage**: Index on SSD for latency; raw docs in blob store (S3) or DB; cache in RAM/SSD.

---

## 6. Bottlenecks and Mitigations

| Bottleneck | Mitigation |
|------------|------------|
| Query latency | Cache hot queries; optimize ranking (two-phase: cheap candidate retrieval + expensive rerank); reduce shard fan-out where possible |
| Index size | Compression (posting lists); tiered storage (hot terms in RAM); sharding |
| Index update latency | Async indexing via queue; batch updates; merge strategies (e.g. LSM) |
| Ranking cost | Retrieve top-K candidates with cheap scoring (BM25, etc.); optional rerank with heavier model on smaller set |
| Crawl rate / politeness | Rate limit per domain; respect robots.txt; distributed crawlers with coordinator |

---

## 7. Improvements and Extensions

- **Autocomplete**: Separate prefix index (trie or n-gram) or use same index with prefix queries; cache suggestions.
- **Spell correction**: Edit distance, n-gram overlap, or ML; suggest correction and re-query.
- **Personalization**: User history / embeddings; rerank or boost by user affinity.
- **Faceted search**: Store metadata in index; aggregate counts per filter (e.g. category) for faceted navigation.
- **Real-time**: For product search, ingest events (Kafka); indexer updates index; query sees new docs within seconds/minutes.

---

## Quick Revision

- **Core**: Inverted index (term → posting list); query = look up terms, merge, rank, return top K.
- **Scale**: Shard index (by term or doc); replicate for reads; cache hot queries.
- **Indexing**: Queue (Kafka) + indexer workers; async; eventual consistency of index.
- **Ranking**: BM25 or similar for candidate retrieval; optional neural rerank on small set.
- **Interview**: “We maintain an inverted index sharded by term; query servers hit cache for popular queries and otherwise query index shards, merge and rank results. New documents are pushed to a queue and indexer workers update the index asynchronously.”
