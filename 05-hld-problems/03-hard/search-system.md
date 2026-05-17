# Design Search System (Web Search / Product Search)

> **Difficulty**: Hard
> **Topics**: Inverted Index, Crawling, Ranking, Distributed Search, Caching
> **Time**: 60 minutes
> **Companies**: Google, Amazon, Meta, Microsoft

---

## Problem Statement

Design a search system where:
- **Users** can enter a text query and receive a ranked list of relevant documents.
- **Documents** (web pages, products, articles) are indexed and searchable within a bounded delay.
- **Results** are ordered by relevance and returned in under 500ms.

**Scale:**
- Billions of documents, petabytes of index data.
- 10K–100K queries per second.
- Index freshness SLA: minutes for product search, hours for web crawl.

---

## Analogy

A book index at the back of a textbook. Instead of reading every page to find "photosynthesis," you look up the index → page 47, 83, 201. The index was built once (slowly) so every lookup is fast.

At Google's scale: the "book" has a trillion pages, new pages are added every second, and users want results in under 100ms. The index is petabytes large and can't fit on one machine. A single query like "python tutorial" matches hundreds of millions of pages — you must rank them all and return the top 10 in 50ms. The index in the back of the textbook weighs 10,000 tons and must be rebuilt continuously while people are looking things up.

---

## Why This Is Hard

1. **Index size at petabyte scale**: A web-scale inverted index is petabytes of data. It cannot fit on one machine or even one rack. You must shard the index, which means a single query fans out to dozens of shards in parallel — and you must merge and rank results from all shards before returning anything.
2. **Query latency with multi-shard fan-out**: A query must complete in under 200ms. If it fans out to 100 shards, each shard must respond in ~50ms, you must tolerate stragglers (slow shards), and you still have to merge and rank thousands of candidates after all shards respond. Any one slow shard blows your SLA.
3. **Index freshness vs. crawl latency**: A new web page must be discovered, crawled, parsed, and indexed before it appears in search results. Each step adds latency. For product search (price changes, out-of-stock), staleness of minutes is acceptable. For breaking news, it is not. The indexing pipeline must be a continuous stream, not a batch job.
4. **Ranking cost at query time**: Simple term frequency (BM25) is fast. Neural ranking (BERT re-ranker) is 1000x more expensive. You can't run a transformer on 100M candidates. Real systems use a two-phase approach: cheap candidate retrieval (BM25) on the full index, then expensive re-ranking on the top 100-1000 candidates. Getting that boundary right is both an engineering and ML problem.
5. **Crawler politeness and adversarial content**: At web scale, crawlers fetch billions of pages from millions of domains. Crawling too fast violates `robots.txt` and gets you blocked. Too slow and the index goes stale. Simultaneously, spammers actively try to game ranking signals (SEO spam, link farms), so the indexer must filter and score credibility — which itself requires crawling more of the web to evaluate link graphs.

---

## Requirements

### Functional

**Must-have:**
- **Query**: User enters text; system returns ranked list of relevant documents (web pages, products, etc.).
- **Indexing**: New/updated documents are searchable within a bounded delay (minutes to hours).
- **Relevance**: Results ordered by relevance (and optionally recency, popularity).

**Nice-to-have:**
- Autocomplete / suggestions.
- Filters (date, category, site).
- Spelling correction ("did you mean?").
- Personalized ranking.

### Non-Functional

- **Latency**: P99 < 200–500ms for query path.
- **Throughput**: 10K–100K QPS at scale.
- **Availability**: 99.9%+.
- **Freshness**: Index updated within agreed SLA (minutes for product search, hours for web crawl).
- **Scale**: Billions of documents; petabytes of index.

---

## Scale Estimation

```
Documents:
10B web pages × 10KB average text = 100 TB raw content
Inverted index (term → doc postings): ~20-30% of raw size = 20-30 TB
With 3× replication: 60-90 TB of index storage

QPS:
100K queries/sec peak
20% of queries are "popular" (top 1M unique queries) → served from cache
80K QPS hits actual index

Indexing throughput:
Web crawl: 1B pages/day ÷ 86,400s = ~12K pages/sec to ingest
Each page → parse → tokenize → update posting lists for ~500 terms
= 6M posting list updates/sec (batched and async via Kafka)

Bandwidth:
Search results: 100K QPS × 5KB response = 500 MB/sec outbound
Crawler: 12K pages/sec × 10KB = 120 MB/sec ingest
```

---

## High-Level Architecture

```
  Users ──▶ Load Balancer ──▶ Query Servers (stateless)
                                    │
                    ┌───────────────┼───────────────┐
                    ▼               ▼               ▼
              Cache (hot        Search          Document
              queries)          Index           Store
              (Redis)           (sharded)       (blob: S3)
                                    │
                         ┌──────────┴──────────┐
                         ▼                     ▼
                   Index Shard 1 ...  Index Shard N
                   (Replica × 3)      (Replica × 3)
                         ▲
                         │ (index updates)
                    ┌────┴────┐
                    │  Kafka  │ (document event stream)
                    └────┬────┘
                         │
                    ┌────┴────┐
                    │ Indexer │ (workers, parse + update postings)
                    └────┬────┘
                         │
                    ┌────┴────┐
                    │ Crawler │ (discovery, fetch, dedup)
                    └─────────┘
```

**Components:**
- **Query servers**: Parse query, check cache, fan out to index shards, merge + rank results, return top K.
- **Search index**: Inverted index (term → posting list); sharded by term or doc; replicated for reads.
- **Cache**: Top-K results for popular queries in Redis (TTL 60–300s).
- **Document store**: Raw documents (for snippets, metadata); S3 or columnar DB.
- **Crawler**: Discovers and fetches documents; respects robots.txt; deduplicates by URL hash.
- **Indexer**: Parses documents from Kafka queue; updates posting lists on index shards.

---

## API Design

### Search Query

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

## Data Model

### Inverted Index (Conceptual)

- **Term dictionary**: term → term_id (for compression and faster lookup).
- **Posting list**: term_id → list of (doc_id, frequency, positions, metadata).
- **Document store**: doc_id → (title, url, snippet, metadata).

```
Term "laptop" → [ (doc_1, freq=3, pos=[5,12,47]), (doc_2, freq=1, pos=[3]), (doc_5, freq=2, pos=[1,8]) ]
Term "phone"  → [ (doc_2, freq=2, pos=[7,19]), (doc_3, freq=1, pos=[2]) ]

Query "laptop phone" → intersect/merge posting lists → BM25 score → top K
```

**Posting list compression:**
- Delta encode doc IDs (store gaps, not absolute IDs) → 4-byte IDs become ~1.5 bytes average.
- VByte or Elias-Fano encoding → posting lists 3-5× smaller.
- Critical at petabyte scale: cuts index storage and RAM footprint significantly.

### Sharding Strategies

**By term hash:**
```
Shard = hash(term) % N_shards
Query "hello world":
→ Shard for "hello" returns: [doc_1(0.8), doc_5(0.6), doc_9(0.3)]
→ Shard for "world" returns: [doc_1(0.7), doc_3(0.5)]
→ Query server merges + ranks by combined score
```
- Pro: Each shard holds a complete posting list for its terms (no fan-out per term).
- Con: Multi-term queries require hitting multiple shards and merging scores.

**By document (Elasticsearch-style):**
```
Shard = hash(doc_id) % N_shards
Each shard has its own full inverted index for its documents.
Query → broadcast to ALL shards → each shard returns local top K → global merge.
```
- Pro: Even document distribution; no cross-shard merging of posting lists.
- Con: Every query fans out to every shard (expensive at hundreds of shards).

**Production approach**: Hybrid — doc-partitioned index within a datacenter, replicated across datacenters for availability.

---

## Query Execution Path

```
User query: "python tutorial beginners"

1. Query Server receives request
   - Tokenize: ["python", "tutorial", "beginners"]
   - Check Redis cache: MISS

2. Fan-out to index shards
   - Query all N index shards in parallel
   - Each shard: look up posting lists for all 3 terms
   - Each shard: compute BM25 score for local docs, return top 100

3. Merge phase
   - Collect top-100 from each shard
   - Re-rank all candidates (global BM25, optionally neural rerank on top 50)
   - Return top 20 results

4. Enrich results
   - Fetch snippets from document store
   - Apply personalization boost (optional)

5. Cache result
   - Write top-K to Redis with TTL 5 minutes

Total: target < 150ms
```

**Two-phase ranking:**
- Phase 1 (cheap, on all candidates): BM25 or TF-IDF — O(n log n), microseconds per doc.
- Phase 2 (expensive, on top 50-100): Cross-encoder neural model (BERT-style) — milliseconds per doc.
- Never run Phase 2 on more than ~100 candidates at query time.

---

## Indexing Pipeline

```
New Document Arrives:
   ↓
Crawler (or event trigger for product search)
   ↓
Kafka topic: "documents-to-index"
   ↓
Indexer workers (consume from Kafka):
  1. Download raw content (if URL) or receive body
  2. Parse HTML / extract text
  3. Tokenize + normalize (lowercase, stop words, stemming)
  4. Compute doc-level signals: PageRank, freshness, click-through rate
  5. Update posting lists on index shards (batch writes, LSM-style)
   ↓
Document searchable (typical lag: 30s – 10 minutes)
```

**Index update strategies:**
- **In-place update**: Directly modify posting list entry. Simple, but causes write amplification at scale.
- **LSM-style merge**: Append new postings to a memtable; periodically merge + compact to disk segments. Same approach as RocksDB — optimizes writes at cost of read complexity.
- **Snapshot + swap**: Build full index offline (MapReduce/Spark job) and atomically swap in new version. Works for batch refresh, not real-time.

---

## Scaling Strategy

### Query Path

- Cache popular queries (20% of traffic) in Redis — avoids hitting index entirely.
- Query servers stateless — scale horizontally behind load balancer.
- Index sharded (by term or by doc) — each shard replicated ×3 for read throughput and HA.
- Straggler mitigation: issue parallel hedged requests; use result from first responder.
- Shard fan-out limit: if a query fans out to >200 shards, latency variance kills P99. Limit shard count or use two-tier index (coarse index for routing + fine index per region).

### Indexing Path

- Documents flow via Kafka; indexer workers consume and update shards.
- Index updates in-place (update posting list) or periodic merge (LSM-style).
- Use Kafka partitioning to parallelize: partition by `hash(doc_id)` → each indexer worker owns a shard range.

### Storage

- Hot posting lists (top 10K terms) kept in RAM per shard (Redis or JVM heap).
- Cold posting lists on SSD.
- Raw documents in S3 (blob storage); only metadata in fast DB for snippet generation.

---

## Bottlenecks and Mitigations

| Bottleneck | Mitigation |
|------------|------------|
| Query latency | Cache hot queries; two-phase ranking (cheap BM25 + expensive rerank); reduce shard fan-out |
| Index size | Posting list compression (delta + VByte); tiered storage (hot terms in RAM, cold on SSD); sharding |
| Index update latency | Async indexing via Kafka; batch + LSM-style merge; real-time path for high-priority docs |
| Ranking cost | Top-K candidate retrieval with BM25; neural rerank on top 50-100 only |
| Crawler politeness | Rate limit per domain (token bucket); respect robots.txt; distributed coordinator |
| Hot terms | "the", "and" appear in billions of docs — posting lists GB in size. Use skip lists + early termination (WAND algorithm) to avoid reading full list |
| Stale cache | TTL expiry (60–300s); event-driven invalidation for product search (price change webhook) |

---

## Failure Scenarios

### Index Shard Failure
```
Impact: Queries involving terms owned by that shard return degraded results
        (other shards still respond; results just miss some docs).
Duration: 1-2 seconds for replica promotion (if shard is replicated).
Mitigation:
- Each shard has ×3 replicas; reads route to any healthy replica.
- During failover: return partial results with a "results may be incomplete" flag.
- Index data is not the source of truth (raw docs still in S3) — shard can be rebuilt.
```

### Crawler Falling Behind
```
Scenario: Crawler throughput drops (target domain throttling, network issues).
          Pages not recrawled; index goes stale.

Detection: Monitor "index age" metric per domain — alert if P90 age > SLA.
Mitigation:
- Prioritize crawl queue: break news sites, product inventory pages → higher priority.
- Fallback: accept stale results but surface "page may be outdated" warning to user.
- Circuit breaker: if >10% of crawl attempts fail for a domain, back off exponentially.
```

### Cache Cold Start (Redis Restart)
```
Scenario: Redis cache node fails; all queries hit the index directly.

Impact: Query latency spikes 3-5× until cache warms up.
        If index also gets hit with 100K QPS simultaneously → cascade failure.

Mitigation:
- Cache fallback: gradually warm cache by serving index results and writing them back.
- Rate limiting on cache miss: shed 50% of load via load balancer during cold start.
- Multi-layer cache: L1 local in-process cache (JVM heap) for top 1000 queries; 
  falls back to Redis only if L1 misses.
```

### Ranking Model Failure
```
Scenario: Neural re-ranker service crashes or returns errors mid-query.

Impact: Results returned in raw BM25 order (still correct, just lower quality).

Mitigation:
- Degrade gracefully: skip re-rank phase, return BM25-ranked results.
- Circuit breaker on re-ranker: if error rate > 5% for 30s, bypass re-ranker entirely.
- Alert P1: re-ranker failure degrades result quality (revenue impact for product search).
```

---

## Improvements and Extensions

- **Autocomplete**: Separate prefix index (trie or n-gram) or use same index with prefix queries; cache top-10 suggestions per prefix (millions of prefixes, but top ones are cacheable).
- **Spell correction**: Edit distance (Levenshtein), n-gram overlap, or seq2seq ML model; suggest correction and re-query transparently.
- **Personalization**: User history / embeddings; rerank or boost by user affinity. Stored in user profile service; applied at merge phase.
- **Faceted search**: Store metadata fields (category, brand, price) in index; aggregate counts per filter for faceted navigation panels.
- **Real-time product search**: Ingest inventory change events (Kafka); indexer updates price/stock fields within seconds; reader queries always see fresh values for structured fields.
- **Knowledge graph / entity disambiguation**: "Apple" = fruit or company? Entity recognition disambiguates and enriches results.

---

## Interview Talking Points

**Q: "How does an inverted index work?"**
- A: "For every term in the corpus, we maintain a posting list — an ordered list of all document IDs containing that term, plus metadata like term frequency and position. A query like 'python tutorial' looks up both terms' posting lists and intersects/merges them. This turns a search over billions of documents into a lookup in a sorted list — O(log N) to find the term, then O(k) to scan the posting list where k is the number of matching docs."

**Q: "How do you shard the index?"**
- A: "Two main strategies: term-based sharding routes each term to one shard, so a multi-term query hits one shard per term and merges scores. Doc-based sharding puts each document's full local index on one shard, so every query fans out to every shard but you merge top-K results. Production systems typically use doc-based because it gives more even data distribution and simpler query routing — you broadcast to all shards and each returns its top K, then you do a global merge."

**Q: "How do you keep query latency under 200ms with hundreds of shards?"**
- A: "Three things: (1) Cache hot queries — 20% of queries account for 80% of traffic, and cached results skip the index entirely. (2) Parallel fan-out with a deadline — issue requests to all shards simultaneously with a 100ms deadline; use whatever results have arrived by deadline and gracefully degrade if a shard is slow. (3) WAND early termination algorithm — instead of scoring every document in a posting list, use upper-bound scores to skip docs that can't make the top K. This prunes 90%+ of candidates before scoring."

**Q: "How do you handle freshness for product search vs. web search?"**
- A: "They have different SLAs. Product search changes are event-driven: a price change fires a Kafka event → indexer updates that document's structured fields within seconds. Web search is crawl-driven: a crawler re-fetches pages on a schedule (popular pages daily, obscure pages monthly). The indexing pipeline is the same queue-based system, but the ingestion trigger differs. For breaking news, we maintain a 'priority crawl queue' for news domains that gets processed within minutes."

**Q: "How does ranking work at scale?"**
- A: "Two phases. Phase 1: BM25 (term frequency × inverse document frequency) on every candidate matching the query — this is fast, runs on raw posting list data, and returns a rough top-K from each shard. Phase 2: Optional neural re-ranker (BERT cross-encoder) on the global top 50-100 candidates. The cross-encoder considers full query-document interaction but costs ~5ms per candidate, so you can't run it on thousands of docs at query time. The key insight is that BM25 candidates are good enough that the top 100 almost always contain the correct best results — you're just re-ordering them."

---

## Quick Revision

- **Core**: Inverted index (term → posting list); query = look up terms, merge, rank, return top K.
- **Scale**: Shard index (by term or doc); replicate for reads; cache hot queries.
- **Indexing**: Kafka queue + indexer workers; async; eventual consistency of index.
- **Ranking**: BM25 for candidate retrieval; optional neural rerank on small set (top 50-100).
- **Failures**: Degrade gracefully — partial shard results, skip rerank on model failure, rate-limit during cache cold start.
- **Interview summary**: "We maintain an inverted index sharded by document; query servers broadcast to all shards in parallel, each shard runs BM25 and returns its local top-K, query server merges globally and optionally re-ranks with a neural model. Popular queries are cached in Redis. New documents are pushed to Kafka and indexer workers update the index asynchronously."

---

## Interview Questions Asked

### Google
1. **"Design a web search index."** → Tests inverted index fundamentals and distributed retrieval; key answer: doc-based sharding across hundreds of shards, parallel fan-out with deadline, BM25 scoring per shard, global merge + optional neural rerank on top-K candidates.

### Elasticsearch (Internals Deep Dive)
1. **"Walk me through how Elasticsearch indexes and queries a document."** → Tests segment and Lucene internals; key answer: documents buffered in memory, periodically flushed to immutable Lucene segments; query hits all segments per shard and merges; background segment merging reduces segment count for read performance.

### Common Follow-ups
1. **"How do you update an index without downtime (blue-green index)?"** → Tests zero-downtime deployment; build new index in parallel on new cluster; alias (e.g., `search_alias`) points to old index during build; atomically switch alias to new index when ready; old index kept briefly for rollback.
2. **"BM25 vs. neural ranking — when do you use each?"** → Tests retrieval model selection; BM25 is fast (microseconds, runs on posting lists) — use for first-stage retrieval over all candidates; neural reranker (cross-encoder) is accurate but slow (~5ms/doc) — use on top 50-100 candidates only.
3. **"How do you handle query expansion and spell correction?"** → Tests query understanding pipeline; spell correction via edit-distance on a word frequency dictionary; query expansion adds synonyms from a curated thesaurus or learned embeddings; both applied pre-retrieval so index is queried with the enriched query.
4. **"How do you balance freshness vs. relevance in ranking?"** → Tests feature engineering; add a recency decay factor to the BM25 score (e.g., exponential decay with half-life of 7 days for news); tune decay weight per query type (news queries weight freshness heavily, reference queries weight relevance); A/B test decay parameters continuously.
