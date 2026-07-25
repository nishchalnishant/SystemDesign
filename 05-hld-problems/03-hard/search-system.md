> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design a web search engine (Google) — the complete pipeline from web crawling to real-time index serving with relevance ranking at internet scale.
>
> **Key design decisions:**
> - Pipeline: Web Crawler → Document Store (raw HTML in S3) → Parser/Indexer → Inverted Index → Ranker → Query Serving
> - Inverted index: word → sorted list of (doc_id, TF score) entries; sharded by term hash; each shard fits in RAM for fast lookup
> - Relevance: TF-IDF/BM25 for content relevance + PageRank for authority; combined scoring with learned ranking model (LambdaMART)
> - Index freshness: periodic batch rebuild (Hadoop) for most of web; incremental updates for news/hot content via streaming pipeline
> - Query serving: <100ms requirement → all data in memory; sharded index; fan-out to all shards, merge top-K, rank, return
> - Personalization: search history, location, language, SafeSearch preference → re-rank results per user context
> - Knowledge Graph: structured data (infoboxes, answer boxes) stored separately in graph DB; surfaced above blue links for direct answers
>
> **Key takeaway:** The inverted index + BM25 scoring is the core — everything else (crawling, PageRank, personalization) feeds into getting the right documents into the top-10 results.

---
module: 05-hld-problems
topic: Hard
status: unread
tags: [05-hld-problems, system-design, hard, search-engine, inverted-index, tf-idf, pagerank, web-crawler]
---
# Design a Web Search Engine (Google)

> **Difficulty**: Hard | **Asked at**: Google, Microsoft (Bing), Amazon

---

## Problem Statement

Design a web-scale search engine. The system crawls billions of web pages, indexes their content, and returns the most relevant results for a user's query in milliseconds. Relevance is determined by a combination of content relevance (TF-IDF/BM25) and link-based authority (PageRank).

---

## Functional Requirements

1. **Web crawling**: Discover and download billions of web pages automatically
2. **Indexing**: Build an inverted index mapping words to pages
3. **Query processing**: Return top-10 most relevant results for a search query
4. **Ranking**: Rank results by content relevance and page authority (PageRank)
5. **Freshness**: Re-crawl pages and update index as content changes
6. **Spell correction**: Suggest corrections for misspelled queries

---

## Non-Functional Requirements

- **Scale**: 50B web pages indexed; 100K queries/second; 5B crawled pages/month
- **Query latency**: Results returned < 200ms at P99
- **Freshness**: Popular pages re-crawled within 24 hours of update
- **Availability**: 99.99% — search downtime is catastrophic
- **Index size**: 50B pages × 1 KB compressed per page = 50 TB for the inverted index

---

## Core Entities

| Entity | Key Fields |
|--------|-----------|
| `Page` | page_id, url, content_hash, crawled_at, last_modified, rank_score |
| `IndexEntry` | term, postings_list[(page_id, tf, positions[])] |
| `Link` | from_page_id, to_page_id, anchor_text |
| `CrawlJob` | url, priority, next_crawl_at, crawl_status |

---

## API Design

```http
GET /api/v1/search?q=distributed+systems+interview&page=1&lang=en
Response 200: {
  "query": "distributed systems interview",
  "total_results": 4200000,
  "results": [{
    "url": "https://example.com/ds-guide",
    "title": "Distributed Systems Interview Guide",
    "snippet": "...A comprehensive guide to system design interviews...",
    "rank_score": 0.94
  }],
  "spell_suggestion": null
}
```

---

## High-Level Design

```
Crawler Subsystem
  ├── Scheduler: priority queue of URLs to crawl (by priority + next_crawl_at)
  ├── Fetcher pool: 10K concurrent HTTP fetchers; download HTML
  ├── Parser: extract text, links, metadata from HTML
  ├── Link extractor: discover new URLs → add to scheduler
  └── Store raw pages: S3; publish to Kafka (crawl-events)

Indexing Subsystem (offline batch)
  ├── Kafka: crawl-events → Index Builder
  ├── Tokenizer: lowercase, remove stop words, stem (play/playing/plays → play)
  ├── TF-IDF computation per (term, page)
  ├── Inverted index builder: term → sorted postings list
  └── Store: distributed inverted index (sharded by term hash)

PageRank Computation (batch, weekly)
  ├── Input: link graph (from_page_id → to_page_id) from Link table
  ├── MapReduce / Spark: iterative PageRank
  └── Store: page_rank_score per page_id

Query Serving
  ├── Query parser: tokenize, spell-correct, expand synonyms
  ├── Index lookup: for each query term, fetch postings list
  ├── Scoring: BM25(content) + PageRank + freshness + click-through rate
  ├── Merge + top-K: DAAT (Document-At-A-Time) merge; return top-10
  └── Snippet generation: extract relevant excerpt from page text
```

---

## Deep Dive 1: Inverted Index at Web Scale

**Problem**: 50B pages, 50TB inverted index. A query for "distributed systems" requires intersecting the postings list for "distributed" (millions of pages) and "systems" (hundreds of millions of pages). How is this done in < 200ms?

**Inverted index structure**:
```
"distributed" → [(page_id=101, tf=5, positions=[12,45,89]), (page_id=204, tf=3, positions=[7,23]), ...]
"systems"     → [(page_id=44, tf=12, positions=[2,8,...]), (page_id=101, tf=8, positions=[5,20,...]), ...]
```

**Sharding**: Index is sharded by term hash across 5,000 index servers. Query for "distributed" → shard 1,337; query for "systems" → shard 2,891. Both shards are queried in parallel.

**Compression**:
- **Delta-encode page IDs**: Store differences between consecutive IDs (e.g., 101, 204, 507 → 101, 103, 303). Small deltas compress well.
- **Variable-length encoding (VByte)**: Encode small integers in 1-2 bytes instead of 4.
- **Result**: 50TB uncompressed → ~5TB compressed index.

**DAAT merge** (Document-At-A-Time):
```python
def intersect(list1, list2):
    i, j = 0, 0
    result = []
    while i < len(list1) and j < len(list2):
        if list1[i].page_id == list2[j].page_id:
            result.append(score(list1[i], list2[j]))
            i += 1; j += 1
        elif list1[i].page_id < list2[j].page_id:
            i += 1
        else:
            j += 1
    return sorted(result, key=lambda x: -x.score)[:10]
```
Both lists are sorted by page_id, so merge is O(N) where N = min(len(list1), len(list2)).

**Skip pointers**: For "AND" queries, jump large runs of non-matching IDs using skip pointers embedded in the postings list. Reduces effective scan length by 10-100x.

> 🎯 **Staff signal:** The decision that shapes everything is *term-partitioning vs document-partitioning* the index, and naming the tradeoff is the tell. Sharding by term (as here) means a single term's entire postings list lives on one server, so a one-word query hits one shard — but a multi-term `AND` must ship million-entry postings lists across the network to intersect them, and a hot term like "the" hotspots one server. Document-partitioning (each shard holds a slice of the corpus, all terms) is the opposite: every query fans out to all 5,000 shards but each does a small local intersection with no cross-shard postings transfer, and load is even — which is why web-scale engines actually shard by document. The compression and skip pointers matter because postings lists are so large that the intersection is I/O-bound, not CPU-bound: delta+VByte shrinks bytes-read 10× and skip pointers let `AND` leapfrog non-matches so you never decode the whole list. E5 says "build an inverted index and intersect postings"; E6 says "the real choice is document- vs term-partitioning — term-sharding wins single-term latency but forces cross-node postings shipping and hotspots on stopwords, so at web scale I document-partition and fan out, and I compress + skip-pointer because intersection is I/O-bound."

---

## Deep Dive 2: PageRank Algorithm

**Problem**: Two pages both contain "distributed systems." One is referenced by 1,000 authoritative pages (Wikipedia, university sites); the other has no inbound links. How does the engine rank the authoritative page higher?

**PageRank formula**:
```
PR(A) = (1 - d) + d × Σ(PR(B) / OutLinks(B))  for all B that link to A
d = damping factor = 0.85 (probability of following a link vs random jump)
```

**Intuition**: A page's rank is the sum of fractional PageRank passed to it from all pages that link to it. High-rank pages passing a link contribute more.

**Computation** (Spark iterative):
```python
# Initialize: all pages start with rank = 1/N
ranks = pages.map(lambda p: (p.page_id, 1.0 / total_pages))

for iteration in range(50):
    # Each page distributes its rank evenly to outbound links
    contributions = links.join(ranks).flatMap(
        lambda (from_id, (to_ids, rank)): [(to_id, rank / len(to_ids)) for to_id in to_ids]
    )
    ranks = contributions.reduceByKey(add).mapValues(
        lambda c: (1 - 0.85) / total_pages + 0.85 * c
    )
```
Converges in 50-100 iterations. For 50B pages, run on a Spark cluster (100 nodes, 12 hours/week).

**Personalized PageRank**: Bias the "random jump" toward the user's browsing history → more relevant results for that user's interests.

> 🎯 **Staff signal:** The systems insight — more than the eigenvector math — is that PageRank is a *query-independent, precomputed* signal, and that's exactly what makes serving fast. PageRank depends only on the link graph, not the query, so you compute it offline in a batch Spark job (50–100 iterations, hours/week) and store one float per page; at query time it's a lookup, not a computation. That separation is the whole point: the expensive global graph analysis is amortized across every query, so the online path only does the cheap query-dependent scoring. The damping factor isn't a fudge — it's what guarantees convergence (it makes the transition matrix irreducible/aperiodic) and models the "random surfer" escaping rank sinks (pages with no outlinks that would otherwise hoard rank). E5 says "rank by number of inbound links"; E6 says "PageRank is recursive — authority flows from authoritative pages — but the key property for the *system* is that it's query-independent, so I precompute it in batch and reduce serving to a per-page lookup; the damping factor is what makes the iteration converge and drains rank sinks."

---

## Deep Dive 3: Query Processing and Ranking

**Problem**: Query "distributed systems interview" → 3 terms → 3 postings lists → millions of candidate pages. How do you compute a final score and return top-10 in < 200ms?

**BM25 scoring** (content relevance):
```
BM25(q, d) = Σ_term IDF(term) × ((TF × (k1+1)) / (TF + k1 × (1 - b + b × |d|/avgdl)))
k1 = 1.5, b = 0.75
IDF(term) = log((N - df + 0.5) / (df + 0.5))
TF = term frequency in document; |d| = document length; avgdl = average document length
```

**Final ranking formula**:
```
score(d) = α × BM25(query, d) + β × PageRank(d) + γ × freshness(d) + δ × CTR(query, d)
```
- `BM25`: content match quality
- `PageRank`: link authority
- `freshness`: recently updated pages rank higher for time-sensitive queries
- `CTR`: historical click-through rate for this query-document pair

**Early termination**: Postings lists are sorted by TF-IDF descending (best documents first). Stop scanning after accumulating 200 candidates — rarely need to go further for top-10 results.

**Snippet generation**: After selecting top-10, extract a 2-sentence excerpt centered on the highest-density query term occurrence in the page. Highlight query terms.

> 🎯 **Staff signal:** Ranking is a *multi-stage retrieval funnel*, and the senior framing is that you never fully score millions of candidates — you can't afford to. Cheap signals (BM25 from the postings scan, precomputed PageRank lookup) run over many candidates to cut millions to a few hundred; expensive signals (query-document CTR, freshness, and in a modern system a learned/ML re-ranker) run only on those survivors. Early termination is what makes stage one bounded: because postings are sorted by TF-IDF descending, the best documents appear first, so you stop after ~200 candidates knowing the top-10 almost certainly can't be beaten by the long tail — trading a sliver of recall for a hard latency bound. The linear `α·BM25 + β·PageRank + …` blend shown here is really the teaching stand-in for a learned ranker whose weights are trained on click data, which closes a feedback loop (CTR feeds ranking, ranking shapes CTR) that must be de-biased. E5 says "combine BM25 and PageRank into a score"; E6 says "it's a funnel — cheap signals prune millions to hundreds, expensive/learned signals rank the survivors, and early termination on TF-IDF-sorted postings gives a bounded-latency stage-one at a controlled recall cost."

---

## Interviewer Questions by Level

**Junior**:
- What is an inverted index? How does it help find pages containing a word quickly?
- What is PageRank? How does the number of inbound links affect a page's rank?
- What is TF-IDF? What does each component measure?

**Mid-level**:
- How do you intersect two posting lists efficiently? What algorithm do you use?
- How do you crawl the web at scale? How do you avoid crawling the same page twice?
- How do you compress the inverted index to fit in memory?

**Senior**:
- Design the inverted index sharding strategy for 50 billion pages. How do you route queries across shards in parallel?
- Implement DAAT intersection with skip pointers — how do skip pointers reduce scan time?
- Design the web crawler — how do you prioritize which pages to crawl first? How do you handle crawl traps (infinite URL spaces)?
- How do you keep the index fresh without re-crawling all 50B pages daily?

---

## Back-of-Envelope Estimation

**Scale inputs (given in NFRs):**
- 10B documents; 100K queries/sec; index < 1 minute freshness; < 100ms query latency

**Inverted index size:**
- 10B documents × avg 500 unique terms/doc = **5T term-document pairs**
- After deduplication (many docs share terms): ~100B unique `(term → posting_list)` entries
- Each posting entry: `{doc_id (8B), tf-idf score (4B), position (4B)}` = 16 bytes
- Total: 100B × 16 bytes = **~1.6 TB** for the inverted index
- Compressed (Roaring bitmaps for doc_id lists, variable-byte encoding for positions): ~5× compression → **~320 GB** in memory
- Distributed: 320 GB ÷ 128 GB/node = **~3 index shards** minimum; use 10 for redundancy and parallelism

**Query processing:**
- 100K queries/sec arriving; each query: tokenize → look up 3–5 terms in inverted index → merge posting lists → rank → return top-10
- Posting list merge for a 3-term query: intersect up to 1M docs per term → typically narrows to ~10K candidates after AND merge → rank top-10 via BM25 scoring
- Per query latency: 5ms (inverted index lookups from RAM) + 10ms (posting list merge + ranking) + 5ms (network) = **~20ms** — well within 100ms budget
- Leaves 80ms headroom for: query parsing, spell correction, synonym expansion, personalization

**Real-time index update (< 1 minute freshness):**
- New documents crawled: assume 10M new/updated docs/day = **~115 new docs/sec**
- Each doc must be indexed within 60 seconds of crawl completion
- Indexing pipeline: parse → tokenize → compute TF-IDF → write to inverted index segment
- Write to a separate in-memory "delta index" segment updated in real-time; merge delta into main index every 5 minutes (Lucene's segment merge strategy)
- Delta index size: 115 docs/sec × 60 sec × 500 terms/doc × 16 bytes = **~55 MB** per minute → trivial in memory

**Throughput and fan-out:**
- 100K queries/sec × 10 shard replicas (for HA) = 1M query operations/sec across shards
- Per shard: 100K ÷ 10 shards = **10K queries/sec per shard** → each shard needs to handle 10K concurrent posting list lookups
- Each posting list lookup: O(1) from inverted index (hash map) + O(M log M) sort for top-K ranking
- At 10K queries/sec per shard, a 16-core server handles 160K ops/sec → **3 servers per shard** with headroom

**Storage for raw documents + metadata:**
- 10B docs × avg 5 KB raw document = **~50 PB** raw document storage
- With compression (LZ4): **~10 PB** physical — stored in distributed file system (HDFS/S3), not queried at search time
- Search hits: return URL + snippet; snippet extracted from stored forward index or cached summary (~200 bytes per doc)
- Forward index for snippets: 10B × 200 bytes = **~2 TB** — stored on fast SSD, not RAM

**Architecture decisions driven by these numbers:**
- **10 shards with replica sets, not one monolithic index**: 320 GB inverted index distributed across 10 shards = 32 GB per shard. A single 320 GB index on one machine requires 320 GB RAM — possible but creates a single point of failure. 10 shards fit in 64 GB RAM with room for the operating system. At 100K queries/sec, query fan-out to 10 shards and merge top-10 results takes ~5ms of network overhead but enables horizontal scaling: add more shards to increase throughput or index size.
- **Delta index + periodic merge for < 1-minute freshness**: Writing a new document directly into the main sorted inverted index would require rebuilding the sorted structure — O(N) insert into a sorted array. Instead, a small in-memory delta index (~55 MB) accepts real-time writes (O(1) hash insert). Queries search both the main index and delta index, merging results. Every 5 minutes, the delta is merged into the main index (background, while queries continue). This achieves near-real-time freshness without disrupting query serving.
- **BM25 ranking computed at query time, not pre-sorted**: Pre-sorting 10B documents by relevance for every possible query is impossible. Instead, BM25 (TF-IDF variant with term saturation and document length normalization) is computed at query time from per-term statistics (IDF pre-computed, stored per term) and per-document statistics (term frequency + doc length, stored per posting). At 10 posting list merges per query × 1M documents per posting list → 10M scoring ops per query → takes ~5ms on modern CPUs. This is the right trade-off: index is smaller (no pre-computed scores per query), ranking quality is high.

---

## Related

**Concepts used in this design**

- [Elasticsearch Internals](../../04-advanced-topics/03-internals/09-elasticsearch-internals.md)
- [Index Structures](../../04-advanced-topics/03-internals/01-index-structures.md)
- [Caching Layer](../../02-building-blocks/02-performance/01-caching-layer.md)
- [Sharding](../../02-building-blocks/03-data-partitioning/01-sharding.md)
- [Bloom Filter](../../02-building-blocks/02-performance/04-bloom-filter.md)

**Practice next**

- [Typeahead Search](../02-medium/typeahead-search.md)
- [Web Crawler](../01-easy/web-crawler.md)

The crawler builds the corpus; typeahead is the query-suggest front end.

**Frameworks**: [HLD Template](../../07-interview-templates/01-frameworks/01-hld-template.md) · [Capacity Estimation](../../07-interview-templates/02-cheat-sheets/02-capacity-estimation.md) · [Trade-offs Cheat Sheet](../../07-interview-templates/02-cheat-sheets/01-trade-offs-cheat-sheet.md)
