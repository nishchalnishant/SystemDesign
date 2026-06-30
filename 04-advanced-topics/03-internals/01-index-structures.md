> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Index data structures used by major databases — B-tree, B+ tree, LSM tree, hash, inverted, BKD — and how to pick the right one for an access pattern.
>
> **Key topics:**
> - B-tree vs B+ tree: data on internal nodes vs only on leaves; range scan efficiency; InnoDB/MyISAM, PostgreSQL
> - LSM tree (log-structured merge): sequential writes (MemTable + SSTable), slower reads; Cassandra, RocksDB, LevelDB
> - Inverted index: term → posting list; full-text search; Elasticsearch, Solr, Lucene
> - Hash index: O(1) equality only; no range; used in memory (Redis Hash, Memcached)
> - BKD tree: block K-d tree for multi-dimensional range; geo + numeric; Elasticsearch (since 5.0)
> - Each structure: which system uses it, what query patterns it serves, when it breaks
>
> **Key takeaway:** Index choice is dictated by access pattern — point lookups → hash/B-tree; range scans → B+ tree/LSM; full-text → inverted; geo/range on multiple dimensions → BKD. Wrong choice = sequential scan penalty.

---
module: 04-advanced-topics
topic: Internals
status: unread
tags: [04-advanced-topics, system-design, internals]
---
# Index Data Structures

> The five structures behind every production database: B-tree, B+ tree, LSM, hash, inverted, BKD. Pick by access pattern.

---

## 1. Why Index Structures Matter

**Question**: `SELECT * FROM orders WHERE customer_id = 12345` scans 50M rows without an index. A 10 GB table at 16 KB per page = 625,000 page reads. At HDD 100 IOs/sec = 6,250 seconds. Even SSD (10K IOs/sec) = 62 seconds. The fix is one line: `CREATE INDEX idx ON orders(customer_id)`. Why does that single line make the query finish in 5 ms?

**Physical constraint**: Sequential disk I/O is fast (HDD ~150 MB/s, SSD ~500 MB/s). Random I/O is slow (HDD 100 IOs/sec, SSD 10K IOs/sec). An index that converts a full-table random scan into a small number of sequential or localized random reads changes the performance by 3-4 orders of magnitude.

**Minimal solution**: A sorted data structure on disk that maps key → row location, with a fanout of ~100-1000 (so tree depth is 3-4 levels for billions of rows).

**Production generalization**: The choice between B-tree, B+ tree, LSM, hash, inverted, and BKD is dictated by:
- Read vs write ratio
- Point lookup vs range query
- Single dimension vs multi-dimension
- In-memory vs on-disk
- Need for transactions or full-text search

---

## 2. B-Tree (Balanced Tree)

A self-balancing tree where each node holds up to `m` keys and has up to `m+1` children. Keys and data are stored on **both internal and leaf nodes**.

### Properties

- Tree height: O(log_m N) — for N=1B, m=100, height ~3
- Each node = one disk page (typically 8-16 KB)
- Lookup, insert, delete: O(log N)

### When to Use

- **Read-heavy**, in-memory or fits-in-cache indexes
- **Equality + range** queries both supported
- Examples: in-memory databases, certain file systems, classic RDBMS secondary indexes (older MyISAM)

### When It Breaks

- **Write amplification**: every update may split pages and propagate up
- **Random writes**: a single key update touches the leaf and may split, causing writes to multiple pages
- **Poor range scan locality**: leaf nodes may not be physically adjacent

### Used In

- MyISAM (MySQL legacy engine) — both PK and secondary indexes are B-trees
- Older PostgreSQL still uses B-tree but with B+ tree internals
- Most file systems (NTFS, ext4 directory indexes)

---

## 3. B+ Tree

Variant of B-tree where **all data is stored only in leaf nodes**; internal nodes store only separator keys.

### Properties

- Internal nodes: keys + child pointers only → more keys per page → shorter tree
- Leaf nodes: linked list (sibling pointers) → sequential range scan
- Tree height: same as B-tree, but typically one less level for same dataset
- All actual data is in leaves, all at the same depth

### Why B+ Tree Wins for Databases

```
B-tree:
  Root: [keys, data pointers]
  Internal: [keys, data pointers]
  Leaf: [keys, data pointers]
  → Range scan = backtrack to parent + descend to next leaf

B+ tree:
  Root: [separator keys only]
  Internal: [separator keys only]
  Leaf: [keys, data pointers, sibling pointer → next leaf]
  → Range scan = walk sibling pointers, no backtracking
```

**Range scan = sequential I/O** along the leaf chain. For 1M rows matching a range, this is dramatically faster than B-tree's random leaf visits.

### Used In

- **InnoDB clustered index** (data is in the leaves, sorted by PK)
- **InnoDB secondary indexes** (leaves hold indexed columns + PK)
- **PostgreSQL** — all standard indexes are B+ tree
- **SQLite**, Oracle, DB2

### InnoDB Clustered Index Specifics

In InnoDB, the table data **is** the B+ tree leaf level. The PK is the clustering key. Secondary indexes store (indexed column → PK), so a secondary index lookup is two B+ tree traversals (secondary → PK → clustered leaf).

```sql
-- This is one B+ tree traversal (covering index)
SELECT order_id FROM orders WHERE customer_id = 101;

-- This is two (back-to-table lookup)
SELECT * FROM orders WHERE customer_id = 101;
```

**Index-only scan** ("covering index"): if all SELECT columns are in the index, no second traversal. Add columns via `INCLUDE` (PostgreSQL) or trailing columns in MySQL composite index.

### Trade-offs

| Pro | Con |
|-----|-----|
| O(log N) point lookups | Write amplification on insert (page splits) |
| Fast range scans via leaf chain | Random I/O for non-covering point lookups |
| Stable performance | Page splits cause fragmentation (UUID PKs) |
| Mature, well-understood | Whole-page updates even for single row |

**UUID vs auto-increment PK**: random UUIDs cause page splits at random leaf positions, fragmenting the B+ tree. Auto-increment or time-ordered UUIDv7 always append at the rightmost leaf → sequential, no splits.

---

## 4. LSM Tree (Log-Structured Merge)

Optimized for **write-heavy** workloads. Writes are always sequential; reads may check multiple structures.

### Structure

```
Write path:
  1. Write to in-memory MemTable (sorted skiplist or red-black tree)
  2. When MemTable fills (~64-128 MB), flush to disk as immutable SSTable
  3. Background compaction merges SSTables, removes tombstones, deduplicates

Read path:
  1. Check MemTable (most recent)
  2. Check Bloom filter for each SSTable (skip definite misses)
  3. Check partition summary/index in SSTable
  4. Merge results from all sources by timestamp
```

### SSTable (Sorted String Table)

Immutable, sorted file on disk. Each SSTable has:
- Data block (sorted key-value pairs)
- Bloom filter (probabilistic "key definitely not here" check)
- Sparse index (block-level, not row-level)
- Optional summary

### Compaction Strategies

| Strategy | Behavior | Best For |
|----------|----------|----------|
| **Size-Tiered (STCS)** | Merge SSTables of similar size | Write-heavy, time-series |
| **Leveled (LCS)** | SSTables organized in levels, fixed size, non-overlapping | Read-heavy, predictable latency |
| **Time-Window (TWCS)** | Group by time window; whole window expires together | Time-series with TTL |

### Bloom Filter

Probabilistic structure that answers "is this key in the set?" with one of:
- **Definitely no** (100% accurate — skip this SSTable)
- **Maybe** (false positives possible — read it anyway)

`k` hash functions, `m` bits, `n` inserted items → false positive rate ≈ `(1 - e^(-kn/m))^k`. Tunable: 1% FPR for 10 bits/key.

### Used In

- **Cassandra** — MemTable + SSTable + STCS/LCS/TWCS
- **RocksDB** — embedded LSM library (used by CockroachDB, TiKV, MyRocks)
- **LevelDB** — Google's original LSM; rocksdb is its fork
- **HBase** — LSM on top of HDFS
- **Kafka** — uses similar ideas for the log (segment files, retention, compaction)

### Trade-offs

| Pro | Con |
|-----|-----|
| Sequential writes only (very fast) | Reads may hit multiple SSTables (read amplification) |
| No read-before-write | Compaction I/O can spike |
| Cheap ingestion of large batches | Space amplification (multiple versions until compaction) |
| Time-series friendly (TTL via TWCS) | Bloom filter false positives waste I/O |
| Crash recovery from last SSTable | Tombstones complicate compaction |

**Write amplification**: each logical write may be written multiple times (MemTable → SSTable → merged SSTable). For 1 write, STCS amplifies ~10-30x; LCS ~10-50x. Plan disk capacity accordingly.

---

## 5. Hash Index

Maps key → bucket via hash function. O(1) average lookup, O(N) worst case (collisions).

### Properties

- **Equality only** — no range scans (keys are hashed, not ordered)
- **In-memory** typical (Redis Hash, Memcached); on-disk hash indexes exist but are rare
- **No ordering preserved** — `WHERE x > 10` requires full scan

### Collision Strategies

- **Chaining**: each bucket is a linked list of colliding items
- **Open addressing**: probe next slot on collision (linear, quadratic, double hashing)

### Used In

- **Redis Hash** (single field lookup O(1); `HGET key field`)
- **Memcached** (entirely hash-based)
- **PostgreSQL Hash index** (since PG 10; not widely used — B+ tree usually wins)
- **In-memory join hash tables** (MySQL hash join, PostgreSQL hash join)

### Adaptive Hash Index (InnoDB)

InnoDB monitors B+ tree lookups; if a particular page is repeatedly accessed, it builds an in-memory hash from the page to the row position. Bypasses the B+ tree on subsequent lookups. Fully automatic; cannot be configured.

### Trade-offs

| Pro | Con |
|-----|-----|
| O(1) lookup | No range queries |
| Simple | No ordering |
| Cache-friendly | Hash collisions degrade |
| Good for KV | Not durable in most implementations |

---

## 6. Inverted Index

Maps **term → list of documents** containing it. The opposite of a "forward" index (document → terms). Foundation of full-text search.

### Structure

```
Forward (typical DB):     doc_1 → [terms: cat, dog, runs]
                          doc_2 → [terms: cat, sleeps]

Inverted (search):        "cat"    → [doc_1, doc_2]
                          "dog"    → [doc_1]
                          "runs"   → [doc_1]
                          "sleeps" → [doc_2]
```

Each entry stores the **posting list** (sorted doc IDs, with positions for phrase queries, with term frequency for scoring).

### Compressed Posting Lists

Doc IDs in a posting list are sorted, so store as **delta-encoded integers** + variable-byte encoding. Frequencies are encoded with frame-of-reference or PFOR-Delta. Result: GB-scale indexes fit in 10-20% of raw size.

### Scoring

- **TF-IDF** (classic): `score = tf × log(N/df)`. Term frequency × inverse document frequency.
- **BM25** (default since Lucene 5, Elasticsearch 5): adds term frequency saturation (diminishing returns) and field-length normalization. Better for long documents.

### Analysis Pipeline

Before indexing, text is processed:
1. **Character filters**: strip HTML, normalize Unicode, map (`&` → `and`)
2. **Tokenizer**: split on whitespace/punctuation
3. **Token filters**: lowercase, remove stop words, stem (`running` → `run`), synonyms

```text
Input:  "The QUICK Brown fox!"
After char filter:  "The QUICK Brown fox!"
After tokenizer:    ["The", "QUICK", "Brown", "fox!"]
After lowercase:    ["the", "quick", "brown", "fox!"]
After stop words:   ["quick", "brown", "fox!"]
After stemming:     ["quick", "brown", "fox"]
```

### Used In

- **Elasticsearch** / OpenSearch (Lucene-based)
- **Apache Solr** (Lucene-based)
- **PostgreSQL GIN** — generalized inverted index for arrays, JSONB, full-text `tsvector`
- **MongoDB text indexes**
- **GitHub code search** (inverted + ranking)

### Trade-offs

| Pro | Con |
|-----|-----|
| Fast term lookup (binary search on term dict) | Bad for range queries / numeric |
| Fast boolean queries (AND/OR/NOT on posting lists) | Index is large (gigabytes for text corpora) |
| Scoring and ranking built in | Reindexing required for new analyzers |
| Phrase queries via position info | Highlighting / aggregations expensive |

---

## 7. BKD Tree (Block K-D Tree)

Multi-dimensional index for **range queries across multiple numeric or geo dimensions** simultaneously. Used by Lucene/Elasticsearch since v5 for numeric and geo_point fields.

### The Problem

A B+ tree on `latitude` answers `latitude BETWEEN 37.7 AND 37.8` efficiently. But for `latitude BETWEEN 37.7 AND 37.8 AND longitude BETWEEN -122.5 AND -122.4`:
- B+ tree on latitude gives all rows in lat range → must still filter longitude linearly
- B+ tree on composite (lat, lon) sorts by lat first → bounding-box query degenerates to a range scan + linear filter

### K-D Tree Basics

Partition k-dimensional space recursively, splitting on a different dimension at each level:

```
Level 0 (root): split on latitude (median lat = 37.75)
  Left:  lat < 37.75
  Right: lat >= 37.75

Level 1: split on longitude (median lon per half)
Level 2: split on latitude again
...
```

A range query prunes entire subtrees whose bounding box doesn't intersect the query box. Expected query time: O(k × N^(1-1/k)).

### BKD Tree: Block Storage

**Key innovation**: store the K-D tree in large, sequential disk blocks. Leaf blocks hold many points (512 or 1024). Navigating requires a few block reads, not one seek per point.

```
BKD Tree:
                    [Internal: split lat=37.75]
                   /                          \
    [Internal: split lon=-122.4]    [Internal: split lon=-122.3]
        /          \                    /          \
[Leaf block]  [Leaf block]    [Leaf block]  [Leaf block]
 512 points    512 points      512 points    512 points
 (seq read)    (seq read)     (seq read)    (seq read)
```

**Query algorithm**:
1. Start at root, prune subtrees whose bounding box doesn't intersect the query box
2. At each internal node: recurse into matching children
3. At leaves: sequential scan of ~512 points (fits in L2 cache) to check exact bounds
4. Return matching document IDs as a bitset (DocIdSet)

**Performance** (1M points, 0.01° bbox):

| Approach | Reads | Notes |
|----------|-------|-------|
| B-Tree on lat | O(N) per dimension | Must filter lon separately |
| BKD Tree | ~3-10 block reads | Prunes non-matching branches |

### What Elasticsearch Indexes as BKD

- **Numeric fields** (`integer`, `long`, `float`, `double`): range queries (`gte`, `lte`)
- **Date fields** (`date`): date range queries
- **Geo fields** (`geo_point`): bounding box, distance, polygon
- **IP fields** (`ip`): CIDR range queries

**Mapping impact**: `keyword` uses inverted index (exact match). `integer`/`long` use BKD (range). Don't map numeric IDs as `integer` if you only need exact lookups — `keyword` is more efficient.

```json
PUT /products/_mapping
{
  "properties": {
    "price":        {"type": "float"},      // BKD: range queries
    "category_id":  {"type": "keyword"},    // inverted: exact match
    "location":     {"type": "geo_point"},  // BKD: geo bbox
    "created_at":   {"type": "date"}        // BKD: date range
  }
}
```

### Used In

- **Lucene 6+ / Elasticsearch 5+** for numeric and geo
- Some specialized time-series databases

### Trade-offs

| Pro | Con |
|-----|-----|
| Fast multi-dim range queries | Point lookups not faster than B+ tree |
| Sequential leaf scans (cache-friendly) | Build cost (recursive partitioning) |
| Handles geo naturally | Not ideal for 4+ dimensions |
| Compression friendly (sorted points) | Updates require re-segment merge |

---

## 8. Comparison Matrix

| Structure | Lookup | Range | Writes | Space | Used By |
|-----------|--------|-------|--------|-------|---------|
| **B-tree** | O(log N) | Yes | Random I/O | Compact | MyISAM, filesystems |
| **B+ tree** | O(log N) | Yes (sequential) | Random I/O | Compact | InnoDB, PostgreSQL, SQLite |
| **LSM** | O(log N) amp | Yes | Sequential, fast | Ample | Cassandra, RocksDB, HBase |
| **Hash** | O(1) avg | No | O(1) | Compact | Redis, Memcached |
| **Inverted** | O(log T) | n/a | Append-only | Large | Elasticsearch, Solr, Lucene |
| **BKD** | O(log N) | Multi-dim | Append-only | Medium | Lucene 6+, ES 5+ |

(`T` = term dictionary size; "Amp" = amplification)

---

## Real-World Usage

| Database | Default Index | Why |
|----------|--------------|-----|
| **PostgreSQL** | B+ tree | General purpose; transactions; complex queries |
| **MySQL/InnoDB** | B+ tree (clustered by PK) | OLTP; ACID; range scans common |
| **Cassandra** | LSM (SSTable) | Write-heavy; time-series; no transactions |
| **Elasticsearch** | Inverted + BKD | Full-text + numeric + geo; near-real-time |
| **Redis** | Hash + skiplist | In-memory; no persistence guarantees for indexes |
| **MongoDB** | B-tree (WiredTiger) | Document store; range + equality |
| **DynamoDB** | B-tree (managed) | Single-digit ms latency; range on sort key |
| **ClickHouse** | LSM (MergeTree) | Columnar; analytics; append-mostly |

---

## Trade-offs (General)

| Dimension | Pro | Con |
|-----------|-----|-----|
| **B+ tree** | Mature, range-efficient | Write amplification, page splits |
| **LSM** | Write-optimized, sequential I/O | Read amplification, compaction cost |
| **Hash** | O(1) point lookups | No range, no ordering |
| **Inverted** | Fast full-text + ranking | Large index, bad for numeric |
| **BKD** | Multi-dim range | Build cost, slower updates |

---

## Failure Scenarios

| Scenario | Symptom | Mitigation |
|----------|---------|------------|
| B+ tree page split storm | INSERTs slow, fragmentation grows | Use auto-increment PK; UUIDv7 instead of UUIDv4; `OPTIMIZE TABLE` |
| LSM compaction falling behind | Read latency rises; disk fills | Increase compaction throughput; tiered storage; tune `compaction_throughput_mb_per_sec` |
| Inverted index bloat | Index size > data size; slow searches | Reindex with better analyzer; remove unused fields; use `keyword` instead of `text` |
| BKD build cost | Slow indexing on geo/numeric-heavy ingest | Bulk index; pre-sort; raise `refresh_interval` |
| Hash collisions | Latency spikes for hot keys | Resize hash table; switch to consistent hashing for sharding |
| Index corruption | Queries return wrong results | Backup indexes; periodic `ANALYZE`; verify with `CHECK TABLE` (MySQL) |

---

## Performance

**Choosing by access pattern**:

| Access Pattern | Best Structure | Reason |
|----------------|----------------|--------|
| Point lookup by PK | B+ tree | O(log N), cached root |
| Range scan on time | B+ tree (clustered) | Sequential leaf walk |
| Equality on session_id | Hash or B+ tree | Hash if no other queries on it |
| Full-text search | Inverted | Built for term lookup + ranking |
| Geo bounding box | BKD | Multi-dim range |
| Time-series ingest | LSM | Sequential writes, time-window TTL |
| In-memory KV | Hash | O(1) lookup |
| Composite WHERE (a, b) | B+ tree on (a, b) | Composite index column order matters |
| Full scan (analytics) | None (sequential scan) | Index adds overhead when returning most rows |

**Composite index column order** (B+ tree): equality columns first, range column last, ORDER BY column last. The first range column breaks the index's ability to use subsequent columns for filtering.

---

## Implementation Patterns

### Java — Indexing Strategy in Hibernate/JPA

```java
@Entity
@Table(name = "orders", indexes = {
    @Index(name = "idx_customer_date", columnList = "customer_id, created_at"),
    @Index(name = "idx_status_partial",
           columnList = "status",
           options = "WHERE status = 'PENDING'")  // PG partial index
})
public class Order {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;                          // clustered in InnoDB

    @Column(nullable = false)
    private Long customerId;

    @Column(name = "created_at")
    private Instant createdAt;

    // ...
}

// Query that uses idx_customer_date efficiently:
List<Order> recent = orderRepo
    .findByCustomerIdAndCreatedAtAfter(123L, Instant.now().minus(30, DAYS));
// Translates to: WHERE customer_id = 123 AND created_at > ?
// Uses both columns of composite index → index range scan, no sort
```

### SQL — Index Design Rules

```sql
-- Rule 1: equality first, range last
-- WHERE status = 'PENDING' AND created_at > X ORDER BY created_at
CREATE INDEX idx_status_created ON orders(status, created_at);

-- Rule 2: covering index — include SELECT columns
CREATE INDEX idx_status_covering
ON orders(status, created_at) INCLUDE (order_id, total);  -- PostgreSQL
-- or MySQL: trailing columns
CREATE INDEX idx_status_covering
ON orders(status, created_at, order_id, total);

-- Rule 3: partial index for hot subsets
CREATE INDEX idx_pending
ON orders(created_at) WHERE status = 'PENDING';

-- Rule 4: expression index for function-based WHERE
CREATE INDEX idx_lower_email ON users(lower(email));
-- Now WHERE lower(email) = 'alice@example.com' uses the index
```

### Java — Elasticsearch Index Template

```java
// Elasticsearch Java API client (8.x)
ElasticsearchClient client = new ElasticsearchClient(transport);

CreateIndexRequest req = CreateIndexRequest.of(b -> b
    .index("products")
    .mappings(m -> m
        .properties("name",        p -> p.text(t -> t.analyzer("standard")))
        .properties("price",       p -> p.float_(f -> f))                 // BKD
        .properties("category_id", p -> p.keyword(k -> k))                // inverted
        .properties("location",    p -> p.geoPoint(g -> g))                // BKD
        .properties("created_at",  p -> p.date(d -> d))                    // BKD
    )
);
client.indices().create(req);
```

---

## Quick Revision

- **B-tree**: keys on internal + leaf; range = backtrack; MyISAM, filesystems
- **B+ tree**: data only on leaves; leaves linked; range = sequential scan; InnoDB clustered, PostgreSQL
- **LSM**: MemTable + immutable SSTable + background compaction; write-optimized; Cassandra, RocksDB
- **Hash**: O(1) equality; no range; in-memory; Redis, Memcached
- **Inverted**: term → posting list; full-text + ranking; Elasticsearch, Solr, GIN in PG
- **BKD**: multi-dim range, sequential block storage; geo + numeric; Lucene 6+, ES 5+
- Choose by access pattern: point → B+ tree or hash; range → B+ tree or LSM; full-text → inverted; geo/multi-dim → BKD
- Composite index column order: equality, range, sort
- UUID PKs cause B+ tree fragmentation; prefer auto-increment or UUIDv7
- LSM write amplification 10-50x; plan disk capacity

---

## See Also

- [01-foundations/databases.md](../../01-foundations/databases.md) — index types, trade-offs
- [04-advanced-topics/internals/postgresql-internals.md](postgresql-internals.md) — B+ tree, GIN, GiST, BRIN
- [04-advanced-topics/internals/mysql-internals.md](mysql-internals.md) — InnoDB clustered B+ tree
- [04-advanced-topics/internals/cassandra-internals.md](cassandra-internals.md) — LSM tree, SSTable, compaction
- [04-advanced-topics/internals/elasticsearch-internals.md](elasticsearch-internals.md) — inverted index, BKD
- [04-advanced-topics/internals/redis-internals.md](redis-internals.md) — hash, skiplist
- [04-advanced-topics/internals/dynamodb-internals.md](dynamodb-internals.md) — managed B-tree

---

## Interview Questions Asked

**Q: How does a B+ tree differ from a B-tree, and why do databases prefer B+ tree?**

A: B-tree stores keys and data on both internal and leaf nodes. B+ tree stores only separator keys on internal nodes; all actual data is on leaves, which are linked via sibling pointers. The benefit: internal nodes pack more keys per page (no data overhead), so the tree is shallower; range scans walk the leaf chain sequentially without backtracking to parents; predictable performance (all data at same depth). PostgreSQL, InnoDB, SQLite all use B+ tree.

**Q: When would you choose an LSM tree over a B+ tree?**

A: When writes dominate reads. LSM (used by Cassandra, RocksDB) makes all writes sequential (append to MemTable, flush to immutable SSTable), avoiding the random I/O and page splits of B+ tree. Cost: reads may check multiple SSTables (read amplification) and background compaction can spike. Time-series, append-mostly event streams, write-heavy metrics ingestion are ideal LSM workloads.

**Q: How does an inverted index enable full-text search, and how does it differ from a B+ tree index?**

A: B+ tree maps `key → row(s)` where the key is the indexed column value. Inverted index maps `term → list of documents` containing that term — the opposite direction. This makes term-based queries (find all documents containing "machine learning") O(log T) on the term dictionary + sequential read of the posting list. A B+ tree on a text column would require scanning every row and tokenizing. Plus, inverted indexes store positions, frequencies, and field-level metadata to support phrase queries, scoring (BM25), and highlighting.

**Q: What is a BKD tree used for, and how does it differ from a B+ tree for geo queries?**

A: BKD (Block K-D Tree) is used for multi-dimensional range queries. A B+ tree on `latitude` answers `lat BETWEEN 37.7 AND 37.8` efficiently, but for `lat BETWEEN 37.7 AND 37.8 AND lon BETWEEN -122.5 AND -122.4` it degenerates to a lat range scan plus a linear longitude filter. A BKD tree recursively partitions k-dimensional space (split on lat at root, lon at level 1, lat at level 2, ...) and prunes subtrees whose bounding boxes don't intersect the query box. Stored in sequential block leaves (~512 points) for cache-friendly I/O. Used by Lucene/Elasticsearch for numeric ranges, date ranges, geo bounding boxes, and IP CIDR queries.

**Q: Why do databases usually use auto-increment integer primary keys rather than UUIDs?**

A: In a B+ tree clustered index (InnoDB, PostgreSQL), the physical order of rows matches the PK order. An auto-increment PK always appends at the rightmost leaf page — sequential I/O, no page splits, no fragmentation. A random UUID v4 PK inserts at a random leaf position — each insert may split a page, causing random I/O, write amplification, and fragmentation that grows over time. Result: UUID PK tables are larger, slower to insert into, and have worse cache behavior. The fix is UUID v7 (time-ordered) which preserves the leftmost bits for ordering and only uses random bits in the suffix.
