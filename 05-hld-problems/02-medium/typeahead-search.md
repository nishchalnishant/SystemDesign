> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design the Google Search typeahead — returning ranked suggestions within 50ms as the user types, at internet scale with personalization and trending freshness.
>
> **Key design decisions:**
> - Latency budget: 50ms total → 10ms network (CDN/anycast) + 40ms compute; only pre-computed suggestions can hit this
> - Pre-computation: offline Hadoop job aggregates search logs → frequency per query → Trie with top-K suggestions per prefix; refreshed hourly
> - Serving: sharded Trie servers (shard by prefix); request routes to correct shard; top-10 suggestions returned in <10ms
> - CDN acceleration: common short prefixes (2–3 chars) cached at CDN edge; covers 80% of queries without hitting Trie servers
> - Trending freshness: Kafka stream of real-time searches → HyperLogLog frequency counter → inject trending queries (last 5min) into suggestions
> - Personalization: user's recent searches + location blended with global scores; personalization layer after Trie lookup; stored in Redis per user
> - Spell correction: Levenshtein distance fuzzy match for typos; secondary lookup if no Trie match
>
> **Key takeaway:** Pre-compute suggestions offline (hourly batch), serve from CDN for common prefixes (covers 80%), inject real-time trending (Kafka stream) for freshness — all three layers together deliver sub-50ms with current results.

---
module: 05-hld-problems
topic: Medium
status: unread
tags: [05-hld-problems, system-design, medium, typeahead, search, trie, elasticsearch, ranking]
---
# Design Typeahead Search (Google Search Bar)

> **Difficulty**: Medium | **Asked at**: Google, Amazon, LinkedIn, Microsoft

---

## Problem Statement

Design the typeahead / search suggestion system for a major search engine like Google. As a user types a query, the search bar shows real-time completion suggestions ranked by popularity, personalization, and context. The system must respond within 50ms to feel instantaneous.

---

## Functional Requirements

1. **Real-time suggestions**: Return top-5 search completions for each keystroke
2. **Ranking**: Rank by global search frequency, recency, and user-specific history
3. **Freshness**: New trending queries appear in suggestions within minutes
4. **Personalization**: Weight suggestions by the user's own search history
5. **Context-aware**: Location-specific suggestions (e.g., "pizza" → local pizza places)
6. **Multiple languages**: Support non-Latin scripts, diacritics, Unicode

---

## Non-Functional Requirements

- **Scale**: 5B searches/day → 58K searches/sec; each generates ~5 typeahead requests = 290K requests/sec
- **Latency**: < 50ms P99 end-to-end (from keystroke to suggestions rendered)
- **Availability**: 99.99% — degrade gracefully (serve cached/stale suggestions rather than no suggestions)
- **Freshness**: Trending queries visible in suggestions within 5 minutes
- **Storage**: Top 5M queries × ~50 bytes each = 250 MB — fits in memory per region

---

## Core Entities

| Entity | Key Fields |
|--------|-----------|
| `Query` | query_string, global_frequency, last_7d_frequency, language, region |
| `TrieNode` | prefix, top10_suggestions (cached), children |
| `UserHistory` | user_id, query_string, last_searched, search_count |
| `TrendingQuery` | query_string, region, velocity_score, window_start |

---

## API Design

```http
GET /api/v1/suggestions?q=sys&limit=5&lang=en&region=us&user_id=u123
Response 200: {
  "suggestions": [
    { "text": "system design interview", "type": "popular" },
    { "text": "system design", "type": "popular" },
    { "text": "syscall", "type": "popular" },
    { "text": "system restore", "type": "trending" }
  ]
}
```

**Client-side behavior**:
- Debounce: send request 80ms after user stops typing (Google uses ~80ms, shorter than Instagram's 150ms)
- Client cache: cache results for the last 10 prefixes in sessionStorage
- Keyboard nav: up/down arrows navigate suggestions without new requests

---

## High-Level Design

```
User types "sys"
  │ (debounced 80ms)
  ▼
Browser cache check → hit: return cached suggestions
  │ miss:
  ▼
CDN PoP (cache: q=sys → suggestions, TTL=30s)
  │ miss (~5% of requests):
  ▼
Load Balancer
  │
  ▼
Typeahead Service (stateless, horizontally scaled)
  │
  ├── In-memory Trie (holds top-5M queries, loaded from S3 snapshot)
  │    └── O(prefix_len) lookup → top-10 global suggestions
  │
  ├── Redis hot tier (trending queries, TTL=60s)
  │    └── For prefix "sys": any trending query starting with "sys"?
  │
  └── User history (Redis hash, top-10 recent searches per user)
       └── Filter user history by prefix "sys"
  │
  ▼
Merge and re-rank: global + trending + personal → top 5
  │
  ▼
Response < 10ms (after CDN miss)
```

**Query aggregation pipeline**:
```
Search logs → Kafka → Flink (sliding 5min window count per query)
  → Batch job (hourly): recompute top-5M queries → rebuild trie → upload to S3
  → Typeahead servers: hot-swap trie snapshot every 15 min
  → Flink streaming: trending queries → Redis hot tier (updated every 30s)
```

---

## Deep Dive 1: Trie vs. Elasticsearch for Suggestions

**Trie approach** (Google's actual implementation):
- In-memory trie with top-K cached at every node
- O(prefix_length) lookup — traverse trie to prefix node, return cached top-K
- Cold on startup (load from S3), warm after first request to each node
- Memory: 5M queries × 20 chars avg = 100M nodes × 50 bytes = 5 GB per server (manageable)
- Best for: latency-critical, high-throughput, prefix-only matching

**Elasticsearch approach**:
- Edge n-gram analyzer tokenizes "system" → ["s", "sy", "sys", "syst", ...]. Each prefix maps to the full query.
- Query: `{ "match": { "title.edge_ngrams": "sys" } }` returns all queries starting with "sys"
- Ranking: by `frequency` field
- Best for: fuzzy matching, complex ranking, full-text
- Latency: ~20ms per query (network + ES overhead) — too slow for < 50ms budget unless cached heavily

**Recommendation**: Trie for the hot path (latency-critical prefix matching). Elasticsearch as fallback for fuzzy/typo correction.

**Trie compression**: Patricia trie (Radix trie) — compress chains of single-child nodes. "sys→y→s→t→e→m" with no branches becomes "system" in one node. Reduces node count 3-5× for typical vocabularies.

---

## Deep Dive 2: Ranking and Personalization

**Base ranking signal** (global popularity):
```
base_score = α × freq_last_1h + β × freq_last_24h + γ × freq_last_7d
```
With `α=4, β=2, γ=1` — recency-weighted. A trending query (spike today) ranks above a chronically popular query with slower growth.

**Personalization boost** (per-user):
```
personal_score = base_score + personal_boost

personal_boost = Σ (user_history[query].count × recency_decay)
recency_decay = e^(-λ × days_since_last_search)
```
A query the user searched yesterday gets a higher boost than one searched 6 months ago.

**Personalization delivery**: User history is stored in Redis hash `user_history:{user_id}` → top-50 recent queries. On typeahead request, the service filters the user's history by prefix, computes personal_score for matching entries, merges with global suggestions.

**Position bias correction**: The top suggestion position has a massive clickthrough advantage. A suggestion in position 1 gets 3× more clicks than position 3, regardless of quality. To train the ranking model correctly, log the suggestion rank at the time of click, then correct for position bias in the model training pipeline (propensity weighting).

---

## Deep Dive 3: Freshness — Trending Queries in < 5 Minutes

**Problem**: "Taylor Swift Eras Tour" starts trending at 9:00 PM. Users should see it in suggestions by 9:05 PM, not after the next hourly trie rebuild.

**Streaming detection**:
```
Flink job: 5-minute sliding window
  → for each (prefix, query): count occurrences
  → velocity = count_this_window / count_prev_window
  → if count > 10,000 AND velocity > 5.0: classify as trending
  → push to Redis: ZADD trending:{prefix} {score} {query} EXPIREAT <15min>
```

**Serving trending queries**: Typeahead service looks up `ZREVRANGE trending:{prefix} 0 2` (top 3 trending for this prefix). Merge with trie results, flag trending suggestions with a "🔥" or "trending" badge.

**Cooldown**: A query promoted to trending gets a minimum TTL in the trending tier of 10 minutes, even if velocity drops. Prevents flickering.

**Flash suppression**: Filter out potentially harmful trending queries (breaking news that's offensive, misinformation) via a blocklist of patterns. Manual override endpoint for the ops team to suppress specific queries within 60 seconds.

---

## Interviewer Questions by Level

**Junior**:
- What is a trie? How does it support prefix-based lookups?
- Why is debouncing important for the client? What's the right debounce delay?
- How do CDN and browser caching help reduce latency?

**Mid-level**:
- How do you build and update the trie? What is the lag before a new trending query appears?
- How do you personalize suggestions for different users without a per-user database lookup on every keystroke?
- Compare trie vs. Elasticsearch for typeahead. When would you use each?

**Senior**:
- Design the streaming pipeline that detects trending queries and updates suggestions within 5 minutes.
- How do you handle typos in the prefix — showing "system design" even when the user types "sytem"?
- How would you shard typeahead servers by prefix region when the trie outgrows single-server memory?
- How do you measure ranking quality for typeahead — what metrics do you use and how do you A/B test ranking changes?

---

## Back-of-Envelope Estimation

**Scale inputs (given in NFRs):**
- 5B searches/day; 290K typeahead requests/sec; < 50ms P99 end-to-end; top 5M queries in memory

**Request throughput:**
- 5B searches/day → 58K search requests/sec; each generates ~5 keystrokes = **290K typeahead requests/sec**
- Each request: `{prefix, user_id, language, region}` ≈ 50 bytes
- Peak: 2× = **~580K requests/sec**

**Trie storage for top suggestions:**
- Top 5M queries × ~50 bytes each = **~250 MB** — fits in the RAM of a single server
- Each trie node stores: character, frequency, top-10 suggestions (list of strings)
- With top-10 suggestions cached at each prefix node: retrieval = one trie traversal, O(L) where L = prefix length (~5 chars) → sub-millisecond
- At 580K requests/sec: 580K trie lookups × 5 levels = 2.9M trie node reads/sec → easily served from one in-memory trie per replica

**Replica sizing for 580K requests/sec:**
- Each trie server handles 50K requests/sec (conservative, based on a Node.js/Go event loop)
- Servers needed: 580K ÷ 50K = **~12 trie servers**
- Each server holds a full copy of the 250 MB trie — total memory: 12 × 250 MB = **3 GB** replicated trie storage

**Trie update frequency:**
- Top 5M queries change as new trending queries emerge
- Rebuild trie from fresh query logs: Spark batch job, runs every 1 hour
- New trie: 250 MB, takes ~2 minutes to build; deploy to servers with blue-green swap (serve old trie during rebuild)
- 5-minute trending freshness: trie rebuild every 5 minutes is too expensive (2 min rebuild every 5 min = 40% compute time on builds)
- Solution: maintain a "trending delta" in Redis (`ZINCRBY trending {query}` per search); merge into trie at each hourly rebuild; for 5-minute trending, pre-bake top-50 trending queries separately and serve them with a boost at read time

**Query log volume:**
- 5B searches/day; each log entry: `{query, ts, user_id}` = 100 bytes
- 5B × 100 bytes = **~500 GB/day** of query logs → aggregated daily in S3 for Spark batch processing

**Architecture decisions driven by these numbers:**
- **In-memory trie with full replication across 12 servers**: 250 MB per trie × 12 servers = 3 GB total. This is cheap enough to replicate fully (no sharding needed). Sharding a trie by prefix (A–G on server 1, H–N on server 2) would require knowing which server to query based on the first character — adds routing complexity for 0 benefit at 250 MB trie size. Full replication gives each server complete independence: any request can go to any server (no routing logic, no cross-shard fan-out).
- **Hourly batch rebuild from query logs + Redis for trending delta**: Rebuilding the trie in real-time on every search (increment frequency, re-rank top-10 at every node) requires locking the trie during writes — blocks 580K reads/sec. Batch rebuild from yesterday's query logs (clean, stable frequencies) keeps the trie read-only. The Redis trending set (`ZINCRBY`) handles real-time frequency increments lock-free at 290K writes/sec → used to detect emerging trends and inject them at read time.
- **CDN caching for top-1000 prefixes**: The top 1,000 prefixes (e.g., "th", "wh", "ho", "how to") account for ~30% of all requests. Their suggestions are static for hours. CDN caches these responses with a 5-minute TTL → 30% of 580K = **174K requests/sec** served from CDN edge without hitting trie servers. This reduces trie server load from 580K to **~406K requests/sec** — need only 9 servers instead of 12.
