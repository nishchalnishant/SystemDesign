> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design autocomplete/typeahead search — returning the top 5–10 ranked completions for a prefix within 100ms, at Google-scale.
>
> **Key design decisions:**
> - Data structure: Trie (prefix tree) for prefix lookup; each node stores top-K suggestions cached; O(prefix length) lookup
> - Ranking: suggestions scored by search frequency; updated via Hadoop batch job hourly or Kafka streaming in real-time
> - Scale: Trie doesn't fit in RAM on one server (billions of terms) → shard Trie by prefix range; each server owns A–F, G–M, N–Z
> - Prefix cache: Redis cache top prefixes (2-char and 3-char prefixes handle 80% of queries); re-compute on score change
> - Freshness: trending queries (breaking news) need minutes-fresh data → stream Kafka → real-time frequency update pipeline
> - API design: GET /suggestions?q=sys&limit=10; backend routes to correct Trie shard based on prefix
> - Personalization: blend global frequency score with user's recent queries; weighted combination; stored in user session cache
>
> **Key takeaway:** Cache top suggestions for the most common prefixes in Redis — most queries are short (2–3 chars); the Trie itself only needs to serve cache misses for long-tail prefixes.

---
module: 05-hld-problems
topic: Easy
status: unread
tags: [05-hld-problems, system-design, easy, autocomplete, trie, typeahead, ranking]
---
# Design Autocomplete / Typeahead Search

> **Difficulty**: Easy | **Asked at**: Amazon, Google, LinkedIn, Twitter

---

## Problem Statement

Design a typeahead search / autocomplete system that suggests completions as a user types. For example, typing "sys" into a search box returns suggestions like "system design", "system error", "syscall". Suggestions are ranked by popularity.

---

## Functional Requirements

1. **Suggestions**: Given a prefix string, return the top 5–10 most popular completions
2. **Ranking**: Suggestions ranked by search frequency / popularity score
3. **Freshness**: Trending queries appear in suggestions within minutes (not days)
4. **Personalization**: Optionally weight suggestions by user's own search history
5. **Multiple languages**: Support Unicode / non-ASCII prefixes

---

## Non-Functional Requirements

- **Scale**: 10M users, 1B searches/day → 12K searches/sec; each search triggers ~5 autocomplete requests (one per character)
- **Latency**: Autocomplete response < 50ms P99 (must feel instant)
- **Throughput**: 60K autocomplete requests/sec
- **Freshness**: New trending query appears in suggestions within 5 minutes
- **Storage**: Top 1M suggested queries × 100 bytes each = 100 MB (fits in memory)

---

## Core Entities

| Entity | Key Fields |
|--------|-----------|
| `Query` | query_string, frequency, last_seen |
| `TrieNode` | char, children, is_terminal, top_suggestions (cached) |
| `Suggestion` | query_string, score, display_text |

---

## API Design

```http
GET /api/v1/autocomplete?q=sys&limit=5&user_id=u123
Response 200: {
  "suggestions": [
    { "text": "system design", "score": 98250 },
    { "text": "system error", "score": 45100 },
    { "text": "syscall", "score": 12300 }
  ]
}
```

- `q`: The current prefix string typed by the user
- `limit`: Max suggestions to return (default 5)
- `user_id`: Optional; enables personalized ranking

**Client-side optimization**: Debounce requests — only send a request 150ms after the user stops typing. Client-side cache: cache the last 5 prefixes in memory. If the user deletes a character, serve the cached result for the shorter prefix immediately.

---

## High-Level Design

```
User types "sys" → client debounces → GET /autocomplete?q=sys
  │
  ▼
CDN (caches top-N suggestions per prefix, TTL=60s)
  │  ← ~80% of requests served here
  ▼
Load Balancer
  │
  ▼
Autocomplete Service
  │
  ├── Check Redis (prefix → top 10 suggestions, TTL=60s)
  │    └── Cache hit → return immediately
  │
  └── Trie lookup (in-memory trie loaded from build pipeline)
       └── Return top-K suggestions
  │
  ▼
Response: sorted suggestions list
```

**Trie data structure**: A trie (prefix tree) where each node represents one character. Traversing from the root to any node spells a prefix. Each terminal node stores the query's frequency score. Each internal node caches the top-10 suggestions for that prefix (computed offline).

**Aggregation pipeline** (background):
```
User search logs → Kafka → Query Aggregator (count frequency per query per hour)
  → top-1M query list → Trie Builder (build trie in memory) → upload to trie store
  → Autocomplete Servers load new trie (hot swap, no downtime)
```

---

## Deep Dive 1: Trie Design and Storage

**Naive trie problem**: A full trie for 1M queries × average length 20 chars = 20M nodes. At 50 bytes/node (char + children map + score), that's 1 GB in memory. Acceptable on a modern server.

**Top-K caching at each node**: Instead of traversing the entire subtree under a prefix to find the top 10 suggestions (expensive), precompute and cache top-10 at every node during the trie build.

```
Node "s":      top10 = ["system design", "search", "sports", ...]
Node "sy":     top10 = ["system design", "syscall", "syntax error", ...]
Node "sys":    top10 = ["system design", "syscall", "sys info", ...]
```

Lookup is now O(prefix_length) — just traverse the trie to the prefix node and return its cached top-10.

**Trie compression**: Merge chains of single-child nodes. "sys" with only one child "t" → "syst" stored as a single compressed node. Reduces memory 2–5× for typical tries.

**Serialization**: Serialize the trie as a flat byte array (DFS pre-order) for fast loading. Stored in Redis as a blob or loaded from S3 on startup. Servers load a new trie snapshot every 5 minutes.

---

## Deep Dive 2: Freshness — Handling Trending Queries

**Problem**: A breaking news event starts trending at 2:00 PM. Users should see it in autocomplete by 2:05 PM, not 2:00 AM (next rebuild).

**Approach 1: Batched rebuild** (every 5 minutes). Kafka consumer aggregates the past 5 minutes of search logs. Top-N queries computed. Trie rebuilt and hot-swapped on all servers. 5-minute lag.

**Approach 2: Streaming update** (near real-time). 
```
Search event → Kafka → Flink streaming job → count queries in 5-minute sliding window
  → if query count crosses threshold → push to Redis hot tier
  → Autocomplete service checks Redis hot tier first (prefix → trending suggestions)
```

**Hybrid (recommended)**:
- Trie rebuilt every 15 minutes (stable, historical)
- Redis hot tier updated every 30 seconds with currently trending queries
- On lookup: merge trie results with Redis hot tier results, deduplicate, re-rank

**Decay function**: Weight recent searches more heavily:
```
score = frequency_last_1h × 4 + frequency_last_24h × 2 + frequency_last_week × 1
```
Trending queries (spike in last 1h) rise fast; stale queries fall off naturally.

---

## Deep Dive 3: Scaling and Personalization

**Scaling the autocomplete service**: The trie fits in 1 GB of RAM per server. With 60K QPS and each request taking < 1ms (trie lookup), one server handles ~5K QPS. 15 servers handle 60K QPS. Horizontally scalable — add servers as QPS grows.

**CDN caching**: 80% of autocomplete requests are for common prefixes ("the", "how", "sys"). CDN caches `GET /autocomplete?q=sys → top 10 suggestions` with a 60-second TTL. Reduces origin server load 5×.

**Personalization**:
- User's own recent searches (stored in their session / cookie) are boosted in ranking.
- `personalized_score = global_score × 0.7 + user_affinity × 0.3`
- User affinity: count how many times this user has searched for this query or similar queries.
- Personalization runs on the autocomplete server after the base trie lookup. Adds < 1ms (in-memory user history lookup).

**Prefix sharding** (if needed at very large scale): Shard autocomplete servers by prefix range. Server 1 handles "a-f", server 2 handles "g-m", etc. Load balancer routes by first character of the prefix. Each server holds only its shard of the trie. Reduces per-server memory to 1/26 of total.

---

## Interviewer Questions by Level

**Junior**:
- What is a trie? How does it support prefix lookups?
- Why do you cache top-K suggestions at each trie node instead of searching the subtree on every request?
- What is debouncing and why does the client use it?

**Mid-level**:
- How do you update the trie when query frequencies change? How often do you rebuild?
- How does the CDN help with autocomplete? What's the cache TTL and why?
- How do you handle trending queries that aren't in the current trie?

**Senior**:
- How would you support personalized suggestions at 60K QPS without a per-user database lookup on the hot path?
- Design the aggregation pipeline that computes query frequencies from raw search logs.
- How would you support fuzzy matching — showing suggestions even when the user misspells the prefix?
- How do you handle autocomplete for languages with non-Latin scripts (Chinese, Arabic)?
