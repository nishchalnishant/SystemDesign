---
module: 02-building-blocks
topic: Search Indexing
status: unread
tags: [02-building-blocks, system-design, search, elasticsearch, inverted-index]
---

# Search & Inverted Index

**Opening question**: Amazon has 350 million products. A user searches "wireless headphones under $50". A full table scan of 350M rows takes minutes. You need results in under 100ms. How?

---

## Mindmap

```
[Search & Inverted Index]
├── Problem
│   ├── SQL LIKE '%keyword%' → full table scan, no index possible on middle-of-string match
│   ├── RDBMS B-tree index → prefix match only; can't match "headphones" in "wireless headphones"
│   └── Solution → inverted index: word → list of document IDs that contain it
├── Inverted Index
│   ├── Build time: tokenize text → normalize → map token to posting list
│   ├── Query time: look up each token → intersect posting lists → rank by score
│   ├── Posting list: [(doc_id, tf_score, position)] sorted by relevance
│   └── Storage: LSM-tree based (Lucene segments) — fast write, compact on merge
├── Write Path
│   ├── Source DB → CDC (Debezium) → Kafka → Indexer → Elasticsearch
│   └── Batch alternative: Spark job reads DB snapshot → builds index → pushes to ES
├── Query Path
│   ├── Parse query → tokenize → look up each token in inverted index
│   ├── Intersect posting lists → TF-IDF or BM25 score → top-K results
│   └── Filters (price, category) applied as bit-mask on top of text match
├── Elasticsearch Concepts
│   ├── Index → like a database table; sharded across nodes
│   ├── Shard → one Lucene instance; primary + replica pair
│   ├── Mapping → field types (text vs keyword): text is analyzed; keyword is exact-match
│   └── Refresh interval → 1s default; new docs searchable within 1s of write
└── Interview Angles
    ├── Why not just use Postgres full-text search? → works up to ~10M docs; at 350M need dedicated search
    ├── How do you keep search in sync with DB? → CDC + Kafka; eventual consistency acceptable for search
    └── How do you handle typos? → fuzzy matching (edit distance 1-2), phonetic analysis
```

---

## 1. How an inverted index works

```python
# Simplified inverted index
index = {
    "wireless":   [101, 205, 890],
    "headphones": [101, 340, 890, 1200],
    "bluetooth":  [205, 890, 1200],
}

def search(query: str) -> list[int]:
    tokens = query.lower().split()
    posting_lists = [set(index.get(t, [])) for t in tokens]
    return list(set.intersection(*posting_lists))  # doc IDs matching ALL tokens

# search("wireless headphones") → [101, 890]
```

At query time: look up each token O(1), intersect posting lists, rank by score. No full scan.

---

## 2. Write path: keeping search in sync

**CDC + Kafka** (preferred for product catalog):
- Debezium tails the DB WAL → Kafka topic → indexer consumer writes to Elasticsearch
- Lag ~1-2s. Inventory and price changes are fresh.

**Batch rebuild** (simpler, acceptable for analytics):
- Spark reads DB snapshot hourly → builds Elasticsearch index from scratch
- Stale by up to 1 hour; fine for indexes where freshness is not critical

Use CDC for the product catalog (price/stock must be near-real-time); use batch for search analytics or recommendation indexes.

---

## 3. Elasticsearch data model

```python
# Document stored in Elasticsearch
product = {
    "id": "B08N5KWB9H",
    "title": "Sony WH-1000XM4 Wireless Headphones",  # text — analyzed, supports full-text
    "brand": "Sony",                                   # keyword — exact match / facet filter
    "price": 279.99,                                   # float — range filter
    "category": "Electronics > Headphones",            # keyword
    "in_stock": True,                                  # bool filter
    "rating": 4.7,                                     # float — sort/filter
}
```

`text` fields are tokenized and analyzed (lowercased, stemmed). `keyword` fields are stored as-is for exact match, aggregations, and faceted filtering. Mixing them up is a common mapping mistake.

---

## 4. Query: text + filters combined

```python
import requests

def search_products(query: str, max_price: float) -> list:
    body = {
        "query": {
            "bool": {
                "must": {"match": {"title": query}},
                "filter": [
                    {"range": {"price": {"lte": max_price}}},
                    {"term": {"in_stock": True}},
                ],
            }
        },
        "size": 20,
    }
    resp = requests.post("http://es:9200/products/_search", json=body)
    return [h["_source"] for h in resp.json()["hits"]["hits"]]
```

`must` contributes to relevance score (BM25). `filter` is cached and applied as a bitset — zero score contribution, faster than `must`.

---

## 5. Scaling search

- Shard by product category so each shard fits in RAM (~30GB per shard)
- Replica shards handle read load; primary handles writes
- Cache top-1000 queries in Redis (TTL 60s) — search traffic is heavily head-skewed
- For 350M products: ~10 primary shards × 30M docs each; 2 replicas each = 30 ES nodes
- Bulk indexing during off-peak: increase `refresh_interval` to 30s to reduce I/O during batch writes

---

## 6. Common interview follow-ups

**"Postgres full-text search vs Elasticsearch?"**
Postgres works well under 10M rows; beyond that ES wins on query latency, relevance scoring (BM25), and horizontal scalability.

**"How do you handle misspellings?"**
Fuzzy query in ES with `fuzziness: "AUTO"` — edit distance 1 for short terms, 2 for longer ones. Also: phonetic analysis for names.

**"How do you rank results?"**
BM25 (ES default) for text relevance. Then re-rank with an ML model using signals like purchase rate, CTR, and revenue — business relevance on top of text relevance.
