> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Elasticsearch internals — how a distributed search engine built on Apache Lucene delivers fast full-text search and analytics at scale.
>
> **Key topics:**
> - Inverted index: maps term → list of document IDs; the opposite of a DB index; enables full-text search in O(1) per term
> - Cluster topology: nodes (master/data/coordinating/ingest) → indices → shards (primary + replicas); each shard is a Lucene index
> - Write path: document → translog (durability) → in-memory buffer → refresh every 1s → segment → flush to disk; near-real-time search
> - Segment merging: Lucene accumulates immutable segments; background merge reduces segment count; improves read performance
> - Query execution: coordinating node → broadcast to all shards → each shard executes locally → coordinating node merges + ranks
> - Aggregations: bucket (group by), metric (sum/avg/percentile), pipeline — all executed at shard level and merged; very efficient
> - Mapping and analyzers: how text is tokenized, lowercased, stemmed; wrong mapping = poor search relevance
>
> **Key takeaway:** Elasticsearch's power is the inverted index + distributed query fan-out — use it for full-text search, log analytics, and faceted search; don't use it as a primary data store (no ACID transactions).

---
module: 04-advanced-topics
topic: Internals
status: unread
tags: [04-advanced-topics, system-design, internals]
---
# Elasticsearch Internals

> A distributed search and analytics engine built on Apache Lucene, designed for horizontal scalability, real-time search, and full-text analysis.

---

## 1. Why Elasticsearch Exists

**Question**: `SELECT * FROM articles WHERE body LIKE '%machine learning%'` does a full table scan — no relevance ranking, no stemming, no phrase support, no faceting. You need sub-second search across billions of documents with relevance ranking, typo tolerance, and aggregations.

**Physical constraint**: A B+ tree index on a column doesn't help with text search. A full-table scan is O(N). Tokenization, scoring, and relevance ranking require a specialized data structure: the **inverted index**.

**Minimal solution**: Build an inverted index per field. Tokenize text into terms; map each term to the list of documents containing it. Search is O(terms_in_query) lookups + posting list intersection.

**Production generalization**: Elasticsearch (2010, Elastic) is a distributed wrapper around Apache Lucene (1999, Doug Cutting). Lucene provides the inverted index and query engine; Elasticsearch adds the distributed layer: sharding, replication, cluster coordination, REST API, and aggregations. Near-real-time (1-second) search, not real-time, because of how segments are made searchable.

---

## 2. Core Concepts

| Concept | Definition |
|---------|------------|
| **Cluster** | One or more nodes sharing a cluster name |
| **Node** | Single Elasticsearch instance |
| **Index** | Logical collection of documents with a shared schema (mapping) |
| **Shard** | Subset of an index; each shard is a self-contained Lucene index |
| **Primary shard** | Original shard; handles writes |
| **Replica shard** | Copy of primary; serves reads + failover |
| **Document** | JSON object stored in an index |
| **Mapping** | Schema: field names, types, analyzer chain |
| **Segment** | Immutable Lucene mini-index; created on every refresh |
| **Translog** | Write-ahead log for durability before segment is flushed |

### Node Roles

- **Master-eligible**: votes in master election; manages cluster state
- **Data**: holds shards; serves reads and writes
- **Coordinating**: routes requests, distributes queries, merges results
- **Ingest**: pre-processes documents via ingest pipelines

In production, separate master-eligible nodes from data nodes; otherwise a busy data node stalls cluster state propagation.

### Sharding

```
Cluster: "logs-prod"
    ├─ Node 1 (master + data)
    │   ├─ Index "logs-2024-05" - Shard 0 (primary)   + Shard 2 (replica)
    │   └─ Index "logs-2024-05" - Shard 1 (replica)
    ├─ Node 2 (data)
    │   ├─ Index "logs-2024-05" - Shard 1 (primary)   + Shard 0 (replica)
    │   └─ Index "logs-2024-05" - Shard 2 (primary)
    └─ Node 3 (data)
        └─ Index "logs-2024-05" - Shard 2 (replica)   + Shard 1 (primary)
```

**Primary shard count is fixed at index creation** — cannot be changed without reindexing. Choose based on expected total data size: `num_primary_shards ≈ total_GB / 30 GB` (20-50 GB per shard is the sweet spot).

**Replica count can be changed anytime** via `PUT /index/_settings`.

---

## 3. Inverted Index

The core data structure for full-text search. Maps **term → list of document IDs** (the opposite of a typical DB index, which is `key → row`).

### Structure

Documents:
```json
Doc1: {"text": "quick brown fox"}
Doc2: {"text": "brown dog"}
Doc3: {"text": "quick dog jumps"}
```

Inverted index:
```
Term       → Document IDs (with positions, frequencies)
"quick"    → [Doc1, Doc3]
"brown"    → [Doc1, Doc2]
"fox"      → [Doc1]
"dog"      → [Doc2, Doc3]
"jumps"    → [Doc3]
```

### Why It's Fast

- **Direct lookup**: find term in dictionary → get doc IDs immediately
- **Sorted terms**: binary search on term dictionary
- **Compressed**: posting lists are delta-encoded and compressed (FOR, PFOR-Delta)
- **Skip lists**: walk through posting list in O(log n) jumps instead of O(n) sequential

### BM25 Scoring (Default)

```
score(D, Q) = Σ IDF(qi) · (f(qi, D) · (k1 + 1)) / (f(qi, D) + k1 · (1 - b + b · |D|/avgdl))
  f(qi, D)  = frequency of qi in D
  IDF(qi)   = ln((N - n(qi) + 0.5) / (n(qi) + 0.5) + 1)
  N         = total docs
  n(qi)     = docs containing qi
  k1, b     = constants (k1=1.2, b=0.75)

Properties:
  - Term frequency saturation: 10 occurrences score only slightly more than 5
  - Document length normalization: short matching docs rank higher than long ones
```

For BKD trees used for numeric/geo range queries, see [index-structures.md](index-structures.md).

---

## 4. Text Analysis Pipeline

Before indexing, text is processed through character filters, a tokenizer, and token filters.

```
"Quick Brown Foxes!"
   ↓ Char filter (HTML strip, char mapping)
"Quick Brown Foxes!"
   ↓ Tokenizer (standard: split on whitespace + punctuation)
["Quick", "Brown", "Foxes"]
   ↓ Token filters: lowercase
["quick", "brown", "foxes"]
   ↓ Stop words removal
["quick", "brown", "foxes"]  (no change; "the", "a" already removed)
   ↓ Stemming (Porter/Snowball)
["quick", "brown", "fox"]
```

The same analyzer must be applied at query time, otherwise you search for "foxes" against an index of stemmed "fox" → no match.

**Built-in analyzers**:
- `standard` (default): lowercase + tokenize
- `english`: standard + stemming + stop words
- `whitespace`: no transformation
- `keyword`: no tokenization (single term = whole string)
- `pattern`: regex-based tokenization

**Custom analyzers** (e.g., for synonyms, edge n-grams, ICU) are configured in the index mapping.

---

## 5. Write Path

```
Client → POST /products/_doc/1 {"name": "Laptop", "price": 999}
    ↓
1. Route: hash(doc_id or routing) % num_primary_shards → target primary shard
    ↓
2. Primary shard:
     - Write to translog (sequential, on disk → durability)
     - Add to in-memory buffer
    ↓
3. After refresh_interval (default 1s):
     - Buffer flushed to a new Lucene segment
     - Segment is searchable (not yet on disk)
     - Replicates to replica shards (in-memory)
    ↓
4. After translog threshold (default 512 MB or 30 min):
     - Segments flushed to disk
     - Translog cleared
```

**Near-Real-Time (NRT)**: documents are searchable 1 second after indexing (default `refresh_interval`). Not real-time; not eventual; bounded 1 s.

**Translog durability trade-off**:
- `index.translog.durability: request` (default for some setups) — sync after each request; slow but safe
- `index.translog.durability: async` — batch sync; faster; up to 5 s loss on crash

---

## 6. Read Path (Scatter-Gather)

```
Client → GET /products/_search {"query": {"match": {"name": "laptop"}}, "size": 10}
    ↓
1. Coordinating node receives request
    ↓
2. Query phase (scatter):
     - Sends query to all shards (primary or replica of each)
     - Each shard executes query locally on its segments
     - Returns: top N doc IDs + scores per shard
    ↓
3. Coordinating node merges:
     - Global top N doc IDs selected
    ↓
4. Fetch phase (gather):
     - For each top N doc ID, fetch the full _source from the shard that holds it
     - Return to client
```

### Query vs Filter Context

```json
{
  "query": {
    "bool": {
      "must":   [{"match": {"name": "laptop"}}],          // scored
      "filter": [{"range": {"price": {"gte": 500}}}]     // yes/no, cached
    }
  }
}
```

- **Query context** (`must`, `should`): relevance score computed; result not cached
- **Filter context** (`filter`, `must_not`): yes/no; result cached in filter cache; much faster

Use filters for anything that doesn't need relevance scoring: status, date range, exact match, geo bbox, term membership.

---

## 7. Segments & Merging

Every refresh creates a new immutable Lucene segment. Over time:
- Segments accumulate → query must scan more files
- More files = more file handles, more I/O

**Background merging**: smaller segments are merged into larger ones by a background thread. Deleted documents are physically removed during merge.

**Merge policy** (default: **Tiered**): balances search speed vs merge I/O. Other policies: `LogByteSizeMergePolicy`, `IndexSortingMergePolicy`.

**Force merge** (`POST /index/_forcemerge?max_num_segments=1`): collapses to one segment. Use after bulk loading a static index to speed up searches; never on a live index (blocks writes).

---

## 8. Aggregations

Three categories:

| Type | What it computes | Example |
|------|------------------|---------|
| **Bucket** | Groups docs (like SQL `GROUP BY`) | `terms`, `date_histogram`, `range`, `histogram` |
| **Metric** | Computes value within buckets | `avg`, `sum`, `max`, `min`, `cardinality`, `percentiles` |
| **Pipeline** | Computes on other aggregations | `derivative`, `moving_avg`, `cumulative_sum` |

```json
{
  "size": 0,
  "aggs": {
    "by_category": {
      "terms": {"field": "category.keyword", "size": 20},
      "aggs": {
        "avg_price": {"avg": {"field": "price"}},
        "p95_price": {"percentiles": {"field": "price", "percents": [95]}}
      }
    }
  }
}
```

All aggregations execute at shard level and merge at the coordinating node. Cardinality uses HyperLogLog++ (approximate). Percentiles use t-digest.

---

## 9. Mappings

Mapping defines the schema. Mappings can be **explicit** or **dynamic** (inferred from the first document).

### Field Types

| Type | Index | Use for |
|------|-------|---------|
| `text` | Inverted, analyzed | Full-text search |
| `keyword` | Inverted, NOT analyzed | Exact match, aggregations, sort |
| `integer`, `long`, `float`, `double` | BKD | Numeric range, sort, aggregations |
| `date` | BKD | Date range, sort, histograms |
| `geo_point` | BKD | Geo bounding box, distance, polygon |
| `ip` | BKD | CIDR range |
| `boolean` | BKD | True/false, term query |
| `object` | Flattened (default) | Nested JSON |
| `nested` | Separate Lucene doc per array entry | Query inside array independently |
| `join` | Special | Parent-child relationships |

**Common mapping mistakes**:
- Mapping numeric ID as `long` when you only do exact lookup → use `keyword` (inverted is faster for equality)
- Mapping a field as `text` and using it for aggregations → requires `fielddata=true` which loads the entire field into heap (OOM risk)
- Using dynamic mapping aggressively → mapping explosion (one entry per unique field name)

### Multi-Field

```json
{
  "properties": {
    "title": {
      "type": "text",
      "fields": {
        "raw":   {"type": "keyword"},
        "english": {"type": "text", "analyzer": "english"}
      }
    }
  }
}
```

Index the same field multiple ways: `title` for full-text, `title.raw` for exact match and aggregations.

---

## 10. Cluster State & Master Election

**Master node** manages:
- Cluster state (mappings, shard assignments, in-flight cluster operations)
- Index creation / deletion
- Shard allocation decisions

**Master does NOT** serve data operations. Master is a coordinator, not a query router.

**Election**: Zen2 discovery (or seed-based in older versions). Quorum: `(N/2)+1` master-eligible nodes must agree. Odd number (3, 5, 7) prevents split-brain tie.

**Split-brain prevention**:
- `discovery.zen.minimum_master_nodes = (master_eligible_nodes / 2) + 1` (set on every node)
- Always use 3, 5, or 7 master-eligible nodes; never 2 or 4

---

## 11. Real-World Usage

| Use case | Topology | Notes |
|----------|----------|-------|
| **Log analytics (ELK)** | Daily indices, 1 primary + 1 replica per shard | ILM rolls over and deletes by age |
| **Full-text search** | Single index, replicas for read scale | Custom analyzers per language |
| **E-commerce search** | One index per locale, sync from primary DB via CDC | Faceted search; typo tolerance |
| **Metrics / observability** | Time-based indices; date_histogram aggregations | Watcher for alerting |
| **Geo apps** | `geo_point` / `geo_shape` mappings | "Within 5 km" queries |
| **Security analytics** | Indices per data source; cross-cluster search | Wazuh, Elastic Security |

---

## 12. Trade-offs

| Dimension | Pro | Con |
|-----------|-----|-----|
| **Full-text search** | Sub-second; BM25 ranking; analyzers | Not transactional; no ACID |
| **Scalability** | Horizontal sharding; replicas for reads | Cross-shard queries expensive |
| **NRT** | 1 s from index to search | Not real-time (e.g., 100 ms queries impossible) |
| **Aggregations** | Rich, fast, distributed | Approximate (cardinality, percentiles) |
| **Operations** | No schema migrations; dynamic mapping | Mapping explosion; shard rebalance is slow |
| **Storage** | Compressed; columnar doc values | Not a system of record; data lost on shard failure if no replica |

---

## 13. Failure Scenarios

| Scenario | Symptom | Mitigation |
|----------|---------|------------|
| **Shard imbalance** | Hot node; cold nodes | Force shard allocation awareness; rebalance |
| **Mapping explosion** | Cluster yellow; mapping limit hit | Disable dynamic mapping; use `index.mapping.total_fields.limit` |
| **Deep pagination** | `from=10000, size=100` → OOM or timeout | `search_after` or PIT API (`POST /pit`) |
| **Wildcard query** | `*laptop*` scans all terms | Avoid leading wildcards; use `n-gram` analyzer for substring |
| **Fielddata OOM** | Heap exhausted; cluster red | Don't aggregate on `text` fields; use `keyword` |
| **Slow merges** | Search latency rises; disk full | Tune `indices.merge.scheduler.*`; reduce shard count |
| **Master instability** | Frequent re-elections; cluster state churn | Add more master-eligible nodes; check network; reduce GC pauses |
| **Split brain** | Two masters; data divergence | Set `minimum_master_nodes`; use odd-numbered masters |
| **Translog corruption** | Lost writes on restart | Lower `index.translog.flush_threshold_size` |

---

## 14. Performance

### Shard Sizing

- **Optimal shard size**: 20-50 GB
- **Max shards per GB of heap**: 20 (rule of thumb)
- **Total shards** = `num_primary × (1 + num_replicas)`

```
Expected data: 500 GB
Shard size: 25 GB
Number of primary shards = 500 / 25 = 20
Replicas = 1
Total shards = 40
```

### Indexing Performance

- **Bulk API**: 5-15 MB per request; 1000-5000 docs per request
- **Increase `refresh_interval`**: 30s or higher for high-volume indexing; reverts to 1s when ingest is done
- **Disable replicas during initial load**: `index.number_of_replicas: 0`, bulk load, re-enable

### Search Performance

- Use `filter` context for non-scored predicates (cached)
- `_source` filtering: `{"_source": ["title", "price"]}` to limit network transfer
- Index sorting for common sort fields
- Routing: `?routing=user_id` to scope queries to a subset of shards

### Hardware

- **SSDs essential** for segment I/O
- **RAM**: 50% JVM heap, 50% OS page cache (Lucene uses both)
- **Fewer, larger nodes > many small nodes**: reduces shard coordination overhead

---

## 15. Implementation Patterns

### Java — Elasticsearch Java API Client (8.x)

```java
ElasticsearchClient client = new ElasticsearchClient(transport);

// Index a document
Product laptop = new Product("Laptop", 999, "Electronics");
client.index(i -> i
    .index("products")
    .id("1")
    .document(laptop));

// Search with bool query (filter for non-scored predicates)
SearchResponse<Product> response = client.search(s -> s
    .index("products")
    .query(q -> q
        .bool(b -> b
            .must(m -> m.match(mq -> mq.field("name").query("laptop")))
            .filter(f -> f.range(r -> r.field("price").gte(JsonData.of(500))))
        )
    ),
    Product.class);

// Aggregations
SearchResponse<Void> aggResponse = client.search(s -> s
    .index("products")
    .size(0)
    .aggregations("by_category", a -> a
        .terms(t -> t.field("category").size(20))
        .aggregations("avg_price", sub -> sub.avg(av -> av.field("price")))
    ),
    Void.class);
```

### Index Template (Time-Series Logs)

```json
PUT _index_template/logs-template
{
  "index_patterns": ["logs-*"],
  "template": {
    "settings": {
      "number_of_shards": 1,
      "number_of_replicas": 1,
      "refresh_interval": "5s"
    },
    "mappings": {
      "properties": {
        "@timestamp":   {"type": "date"},
        "level":        {"type": "keyword"},
        "message":      {"type": "text"},
        "host":         {"type": "keyword"},
        "response_ms":  {"type": "integer"}
      }
    }
  }
}
```

Then `POST logs-2024.05.12/_doc` automatically applies the template.

### Search-After Pagination

```json
POST /products/_search
{
  "size": 100,
  "query": {"match": {"name": "laptop"}},
  "sort": [{"price": "asc"}, {"_id": "asc"}],
  "search_after": [499, "last-seen-doc-id"]
}
```

Constant cost regardless of depth. Use instead of `from`/`size` for deep pagination.

---

## Quick Revision

- Elasticsearch is a distributed wrapper around Apache Lucene
- Inverted index: term → posting list; full-text + scoring; O(1) term lookup
- Sharding: primary + replica; primary count fixed at creation; replica count changeable
- NRT: documents searchable 1 s after indexing (refresh interval)
- Translog provides durability before segment is flushed
- Query execution: scatter (each shard) → gather (merge top N at coordinator) → fetch (full _source)
- BM25 default scoring; saturation + length normalization
- Filter context (cached) vs query context (scored)
- Segments are immutable; background merge keeps count manageable
- Aggregations: bucket, metric, pipeline; distributed across shards
- Cluster state managed by master node; quorum-based election; odd master count
- Use ES for search, analytics, logs; never as a system of record

---

## See Also

- [04-advanced-topics/internals/index-structures.md](index-structures.md) — inverted index, BKD tree internals
- [04-advanced-topics/internals/cassandra-internals.md](cassandra-internals.md) — Lucene segments vs SSTables (both immutable, both merge)
- [04-advanced-topics/internals/kafka-internals.md](kafka-internals.md) — Logstash/Beats → Kafka → ES pipeline
- [04-advanced-topics/observability.md](../observability.md) — ELK stack patterns
- [04-advanced-topics/stream-processing.md](../stream-processing.md) — near-real-time analytics

---

## Interview Questions Asked

**Q: Explain the inverted index.**

A: An inverted index maps `term → list of document IDs` containing the term, plus position and frequency info. It is the opposite of a "forward" DB index (`row_id → column_values`). For each unique term in the corpus, the posting list is sorted by document ID and stored with delta-encoding + variable-byte compression for space. Search is O(terms_in_query) dictionary lookups + posting list intersection (AND, OR via skip lists). Scoring (BM25) is computed from per-term frequency, document length, and inverse document frequency.

**Q: How does Elasticsearch achieve near-real-time search?**

A: Documents are written to the translog (durability) and an in-memory buffer. Every `refresh_interval` (default 1 second), the in-memory buffer is flushed to a new immutable Lucene segment, which is immediately searchable. This 1-second delay is the "near-real-time" aspect. Segments are later flushed to disk and the translog is cleared. Real-time search (0 ms) is not possible without major architectural changes; for that, use a system designed for it (e.g., real-time indexing via in-memory store).

**Q: When to use filter vs query context?**

A: **Filter context**: yes/no match (no scoring); result is cached; used in `bool.filter` and `bool.must_not`. **Query context**: relevance scored; result not cached; used in `bool.must` and `bool.should`. Rule: anything that doesn't need relevance scoring should be in a filter. This includes: status fields, date ranges, exact term matches, geo bounding boxes, exists/missing checks. Filters are dramatically faster because they skip scoring and benefit from the filter cache.

**Q: How do you scale Elasticsearch writes?**

A: At index creation, size the primary shard count: `num_primary_shards ≈ total_data_size_GB / 30`. You cannot increase this without reindexing. Increase replicas (cheap) for read scale. For write-heavy workloads: increase `refresh_interval` (e.g., 30s) to reduce segment churn; tune bulk request size to 5-15 MB; use ingest pipelines for pre-processing; use multiple coordinating nodes; tune thread pools (`indexing`, `search`, `write`). For time-series data, use ILM (Index Lifecycle Management) to roll over daily and delete old indices.

**Q: When NOT to use Elasticsearch?**

A: As a primary data store (no ACID, no transactions, eventual consistency on replica). For pure OLTP with frequent updates (use a relational DB). For pure key-value lookups (use Redis or DynamoDB). For analytics over structured data with joins (use a data warehouse like Snowflake or BigQuery). Use ES when you need: full-text search, log analytics, faceted aggregations, near-real-time indexing, distributed search across many fields. For everything else, the operational cost of running ES is hard to justify.
