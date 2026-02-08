# Elasticsearch Internals

## Overview
Elasticsearch is a distributed search and analytics engine built on Apache Lucene. It's designed for horizontal scalability, real-time search, and full-text analysis.

---

## Core Concepts

### Cluster, Nodes, and Shards

```
Cluster: "my-cluster"
    ├─ Node 1 (Master-eligible, Data)
    │   ├─ Index "products" - Shard 0 (Primary)
    │   └─ Index "products" - Shard 2 (Replica)
    ├─ Node 2 (Data)
    │   ├─ Index "products" - Shard 1 (Primary)
    │   └─ Index "products" - Shard 0 (Replica)
    └─ Node 3 (Data)
        ├─ Index "products" - Shard 2 (Primary)
        └─ Index "products" - Shard 1 (Replica)
```

### Key Terms
- **Index**: Collection of documents (like a database table)
- **Shard**: Subset of an index (for horizontal scaling)
- **Replica**: Copy of a shard (for high availability)
- **Document**: JSON object stored in Elasticsearch
- **Node**: Single server in the cluster

---

## Inverted Index (Core of Search)

### What is an Inverted Index?

Instead of `Document → Words`, Elasticsearch stores `Word → Documents`

**Example Documents:**
```json
Doc1: {"text": "quick brown fox"}
Doc2: {"text": "brown dog"}
Doc3: {"text": "quick dog jumps"}
```

**Inverted Index:**
```
Term       → Document IDs
"quick"    → [Doc1, Doc3]
"brown"    → [Doc1, Doc2]
"fox"      → [Doc1]
"dog"      → [Doc2, Doc3]
"jumps"    → [Doc3]
```

### Token Analysis
Before indexing, text is **analyzed**:
1. **Character filters**: Remove HTML, normalize characters
2. **Tokenizer**: Split into words (`"quick brown"` → `["quick", "brown"]`)
3. **Token filters**: Lowercase, stemming, synonyms

**Example:**
```
Input: "The QUICK Brown fox!"
  ↓ Lowercase filter
"the quick brown fox!"
  ↓ Stop words filter
"quick brown fox"
  ↓ Stemming
"quick brown fox"  (already in root form)
```

### Why It's Fast
- **Direct lookup**: Find term in index → get document IDs instantly
- **Sorted terms**: Binary search on term dictionary
- **Compressed**: Terms and posting lists compressed

---

## Document Indexing Flow

### 1. Client Sends Document
```json
POST /products/_doc/1
{
  "name": "Laptop",
  "price": 999,
  "category": "Electronics"
}
```

### 2. Routing (Choosing Target Shard)
```
shard = hash(routing_value) % number_of_primary_shards

Default routing_value = document ID
shard = hash("1") % 3 = Shard 1
```

### 3. Write to Primary Shard
- Document buffered in memory
- Lucene writes to **translog** (Write-Ahead Log)

### 4. Refresh (Make Doc Searchable)
- Every 1 second (default `refresh_interval`)
- In-memory buffer → new Lucene segment
- **Segment**: Immutable mini-index

### 5. Replicate to Replica Shards
- Primary sends document to all replicas
- Synchronous replication (waits for ack)

### 6. Persist to Disk (Flush)
- Periodically, segments flushed to disk
- Translog cleared

```
Client Request
    ↓
Route to Shard
    ↓
Write to Translog (durability)
    ↓
Buffer in Memory
    ↓ (every 1s)
Refresh → New Segment (searchable)
    ↓ (every 30min)
Flush → Disk (persistent)
```

---

## Segments & Merging

### Problem: Too Many Segments
- Each refresh creates new segment
- Searching across many segments is slow

### Solution: Segment Merging
- Background process merges small segments into larger ones
- **Triggered when**: Too many segments of similar size
- Deleted documents physically removed during merge

### Merge Policies
- **Tiered Merge Policy** (default): Merges segments of similar size
- Balances search speed vs merge I/O

---

## Search Query Flow

### 1. Query Phase (Scatter)
```
Client → Coordinating Node
    ↓
Coordinating Node → All Shards (primary or replica)
Each Shard returns: Top N document IDs + scores
    ↓
Coordinating Node merges results → Global Top N
```

### 2. Fetch Phase (Gather)
```
Coordinating Node requests full documents
    ↓
Only for Top N document IDs
    ↓
Return to client
```

### Example Query
```json
GET /products/_search
{
  "query": {
    "match": {"name": "laptop"}
  },
  "size": 10
}
```

**Flow:**
1. **Scatter**: Query sent to all 3 shards
2. Each shard returns Top 10 docs (IDs + scores)
3. **Merge**: Coordinating node sorts all 30 results → global Top 10
4. **Fetch**: Retrieve full documents for those Top 10 IDs

---

## Scoring & Relevance

### TF-IDF (Classic Scoring)
- **Term Frequency (TF)**: How often term appears in document
- **Inverse Document Frequency (IDF)**: Rarity of term across all documents
- **Score = TF × IDF**

**Example:**
```
Query: "laptop"
Doc1: "laptop laptop computer" → TF=2, IDF=log(1000/50)
Doc2: "laptop" → TF=1, IDF=log(1000/50)
Doc1 scores higher
```

### BM25 (Default Scoring)
- Improved version of TF-IDF
- **Saturation**: Diminishing returns for repeated terms
- **Field length normalization**

---

## Sharding Strategy

### Number of Shards
**Decision at index creation time** (cannot change later without reindex)

**Guideline:**
- **Shard size**: 20-50 GB per shard (optimal)
- **Too many shards**: Overhead (each shard = Lucene index)
- **Too few shards**: Cannot distribute across more nodes later

**Example:**
```
Expected data: 500 GB
Shard size: 25 GB
Number of primary shards = 500 / 25 = 20 shards
```

### Replicas
**Can change anytime**

```json
PUT /products/_settings
{
  "number_of_replicas": 2
}
```

**Formula:**
```
Total shards = primary_shards × (1 + replicas)
Example: 3 primaries × (1 + 1 replica) = 6 total shards
```

---

## Cluster State & Master Node

### Master Node Responsibilities
- **Cluster state**: Metadata about indices, shards, nodes
- **Shard allocation**: Decide which node hosts which shard
- **Index creation/deletion**

**NOT responsible for:**
- Data operations (indexing, searching)

### Split-Brain Prevention
- **Quorum**: Minimum master-eligible nodes to elect master
- Formula: `(master_eligible_nodes / 2) + 1`
- **Must have odd number** of master-eligible nodes (3, 5, 7)

---

## Querying Optimizations

### 1. Filter vs Query Context
- **Query context**: Calculates relevance score
- **Filter context**: Boolean match (yes/no), **cached**

```json
{
  "query": {               // Scored
    "bool": {
      "must": [{"match": {"name": "laptop"}}],
      "filter": [          // Cached, fast
        {"range": {"price": {"gte": 500}}}
      ]
    }
  }
}
```

### 2. Field Data Cache
- For aggregations and sorting on `text` fields
- **Expensive**: Loads entire field into memory
- Use `keyword` type instead for aggregations

### 3. Index Sorting
- Pre-sort documents at index time
- Speeds up range queries and aggregations

---

## Handling Deletes & Updates

### Documents are Immutable
- **Delete**: Mark document as deleted in segment
- **Update**: Mark old doc as deleted + index new doc

### Deleted Docs
- Stored in `.del` file per segment
- Skipped during search
- **Physically removed** during segment merge

---

## Translog & Durability

### Purpose
- Ensure no data loss between refreshes
- Append-only log of operations

### Flush
- Periodic (default every 30 min or 512 MB translog)
- Writes segments to disk, clears translog

### Configurable Durability
```json
{
  "index.translog.durability": "request"  // Sync after each request (slow)
  "index.translog.durability": "async"    // Periodic sync (fast, default)
}
```

---

## Aggregations

### Bucket Aggregations
- Group documents (like SQL GROUP BY)
- Example: `terms`, `date_histogram`, `range`

```json
{
  "aggs": {
    "categories": {
      "terms": {"field": "category.keyword"}
    }
  }
}
```

### Metric Aggregations
- Calculate statistics (like SQL AVG, SUM)
- Example: `avg`, `max`, `sum`, `cardinality`

### Pipeline Aggregations
- Operate on output of other aggregations
- Example: `derivative`, `moving_avg`

---

## Common Pitfalls

### ❌ Using Text Fields for Aggregations
- Requires `fielddata=true` (expensive)
- Use `keyword` type instead

### ❌ Deep Pagination
- `from=10000, size=100` → Must load 10,100 docs in each shard
- Use **Search After** or **Scroll API**

### ❌ Wildcard Queries (`*laptop*`)
- Scans all terms in index
- Use n-grams for substring search

### ❌ Too Many Shards
- Each shard has overhead
- Guideline: < 20 shards per GB of heap

---

##. Performance Tuning

### Indexing Performance
- **Bulk API**: Batch documents (10-100 MB batches)
- **Increase `refresh_interval`**: Less frequent refreshes
- **Disable replicas** during initial bulk load

### Search Performance
- Use **filters** (cached) over queries
- **Index sorting** for common sorts
- **Routing**: Route related docs to same shard

### Hardware
- **SSD**: Essential for fast search
- **RAM**: 50% heap, 50% OS cache (for Lucene)
- **Fewer, larger nodes** > many small nodes

---

## Interview Questions

**Q: Explain the inverted index**
- Maps terms → document IDs (reverse of traditional index)
- Enables fast full-text search (O(1) term lookup)
- Built during document indexing with analyzers
- Compressed for space efficiency

**Q: What is the difference between primary and replica shards?**
- **Primary**: Original shard, handles writes
- **Replica**: Copy of primary, serves reads
- Replicas provide high availability and read scalability
- Number of primaries fixed at index creation; replicas changeable

**Q: How does Elasticsearch achieve near real-time search?**
- **Refresh**: Every 1 second, in-memory buffer → new segment
- Segment immediately searchable (not yet on disk)
- **Trade-off**: Speed vs durability (translog ensures durability)

**Q: When would you use filters vs queries?**
- **Filters**: Exact matches, ranges, boolean logic (cached, fast)
- **Queries**: Full-text search with relevance scoring
- Combine in bool query: `must` (queries) + `filter` (filters)
