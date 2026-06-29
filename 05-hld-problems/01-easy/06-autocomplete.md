---
module: 05-hld-problems
topic: Easy
status: interview-ready
tags: [05-hld-problems, system-design, easy]
---
# Design Autocomplete / Search Suggestions

> **Difficulty**: Easy/Medium
> **Topics**: Trie, Streaming, Caching, Personalization
> **Time**: 45 min
> **Companies**: Google, Meta, Amazon

---

## Clarifying Questions

1. "Is this for a search bar (Google-style) or a field-specific autocomplete (city, username)?"
2. "How fast must suggestions appear — per-keystroke latency budget?"
3. "Do we need personalized suggestions per user, or just global trending?"
4. "How many queries per day, and how quickly must new trending terms appear?"
5. "Multi-language / multi-script support required?"
6. "Should we support typo correction, or just prefix matching?"

---

## Back-of-Envelope

```
Scale:
  500M searches/day × 4 keystrokes avg = 2B autocomplete requests/day
  2B / 86,400 = ~23K QPS average, 100K QPS peak

Trie size:
  100M unique prefixes × 300 bytes/node = 30 GB total trie
  Top 18K short prefixes (1-3 chars) = ~9 MB → fits in-process on every node

Cache tiers:
  CDN: cache top 1K most common prefixes (covers ~40% of traffic)
  Redis: cache top 100K prefixes × 200 bytes = 20 MB
  In-process: top 10K prefixes in LRU map per server instance

Personalization:
  50 recent queries/user × 30 bytes = 1.5 KB/user
  1B users × 1.5 KB = 1.5 TB total user history → Redis Cluster
```

---

## APIs

```
// Query suggestions (fires on each keystroke)
GET /api/v1/autocomplete?q={prefix}&lang=en&limit=5
  → { "suggestions": ["restaurant near me", "restaurant menu", ...], "source": "cache" }

// Log accepted search (for training the suggestion model)
POST /api/v1/search/log
  { "query": "restaurant near me", "position_clicked": 1, "session_id": "..." }
  → 204 No Content
```

Key decision: `GET` returns at most 5 suggestions regardless of trie depth — precomputed top-K at each node, no live aggregation.

---

## Architecture

```
Client (fires on keystroke, debounced 100ms)
  │
  ▼
CDN (caches popular prefixes, TTL=60s)
  │ (miss)
  ▼
Load Balancer → Autocomplete Service (stateless)
  │               │
  │               ├── In-process LRU cache (top 10K prefixes)
  │               │
  │               ├── Redis Cluster (top 100K prefixes)
  │               │
  │               └── Trie Service (full in-memory trie, sharded by 3-char prefix)
  │
  └── Personalization: overlay user's recent queries (Redis ZREVRANGE user:{id}:history)

Write path (async):
  Search event → Kafka → Flink (10-min sliding window, hot-patch trie nodes)
                       └── Spark batch (hourly, full trie rebuild from Hive)
```

---

## Data Model

```sql
-- Query frequency store (aggregated, batch-built)
CREATE TABLE query_frequencies (
    prefix       VARCHAR(100),
    query        VARCHAR(255),
    frequency    BIGINT,
    last_seen    TIMESTAMP,
    PRIMARY KEY (prefix, query)
);
-- This is the source for trie build; not queried on hot path

-- User history (in Redis, not SQL)
-- Redis: ZADD user:{user_id}:history {timestamp} {query}
-- ZREVRANGE returns last 50 queries in recency order
-- TTL = 30 days
```

---

## Key Design Decisions

**1. Trie with precomputed top-K at each node (not live aggregation)**
Naive: traverse all children and sort at query time → O(N) per request. Better: at build time, push the top-K global suggestions down to every prefix node. `GET /autocomplete?q=res` → traverse to "res" node → read pre-stored list → return. O(prefix_length) query time. Build-time cost: O(K × nodes) but this runs hourly offline.

**2. Three-tier cache to avoid trie server on every keystroke**
CDN handles the most common short prefixes (1-3 chars) — these are ~60% of traffic and change slowly. Redis handles the long tail of popular prefixes. Trie server only receives cache misses. This keeps the trie server fleet small and avoids the 30GB trie being a hot path bottleneck.

**3. Two-path freshness: streaming hot-patch + hourly batch rebuild**
Batch alone: trending term takes up to 1 hour to appear. Streaming alone: hard to ensure global correctness (missed messages, ordering issues). Two-path: Flink detects when a prefix's top-K changes and issues a targeted trie node update; Spark's hourly rebuild is the correctness backstop. Hot-patch is approximate; batch is authoritative.

**4. Personalization as a post-processing overlay (not per-user trie)**
A separate trie per user = petabytes of storage. Instead: global trie returns top-5, then boost any result matching the user's Redis history by a score multiplier, re-rank, return top-5 from the merged list. This is stateless and fast (<1ms extra latency) and requires no per-user trie.

---

## Deep Dives

**Trie hot-patch under concurrent reads**
1000 readers are traversing the trie when a hot-patch update arrives. Two options:
- Lock during update: blocks all readers — unacceptable at 23K QPS.
- Copy-on-write (COW): hot-patch writes new node versions into a new snapshot, then atomically swaps the root pointer. In-flight readers hold a reference to the old snapshot and complete normally; new requests see the new version. No locks during reads.

**Typo correction with SymSpell**
On a trie miss: run SymSpell pre-processor (<1ms). SymSpell precomputes all words within edit distance 1-2 using a delete-only dictionary (26× smaller than build-time expansion). Re-query the trie with the corrected term and label the suggestion as a correction. Don't expand all variants at build time — that multiplies storage by 26+ for each term.

---

## Failure Scenarios

| Failure | Impact | Mitigation |
|---------|--------|------------|
| Trie service node down | Suggestions unavailable for that shard | Consistent hashing re-routes to replica; load balancer health checks |
| Redis down | Falls back to trie service for all requests | Circuit breaker; trie service has its own in-process cache |
| Flink lag (hot-patch delay) | Trending terms take longer to appear | Alert on Flink consumer lag; hourly batch ensures eventual correctness |
| CDN serving stale prefixes | Popular prefix suggestions outdated for up to TTL | Short TTL (60s) limits staleness; invalidate on major trie rebuild |
| User history Redis eviction | Personalization degrades to global suggestions | Graceful degradation — global suggestions are still useful |

---

## Interview Questions Asked

### Google
1. **"Design Google Search autocomplete for 5 billion daily searches — start with the data model."** → The interviewer wants to see you reason about trie vs. inverted index first, then arrive at a trie pre-loaded with top-K suggestions at each node. Key data point: top-3 prefix lengths (1-3 chars) cover ~60% of traffic and fit in 9 MB in-process — say this explicitly to show you've thought about caching tiers.
2. **"A new trending query 'ChatGPT outage' goes from 0 to 1M searches in 10 minutes. How does it appear in autocomplete within 5 minutes?"** → Streaming pipeline (Kafka → Flink) that hot-patches individual trie nodes when a prefix's count crosses a threshold, bypassing the hourly batch rebuild. The interviewer is probing whether you understand the two-path (streaming + batch) architecture and why batch alone is insufficient for trending.
3. **"How do you partition the trie when it can't fit on one machine?"** → Partition by 2-3 character prefix (17,576 possible 3-char prefixes in English). Route `GET /autocomplete?q=sea` deterministically to the shard owning "sea*". Each shard stores a complete sub-trie. The interviewer wants to hear about the routing layer and how you handle prefix buckets that are disproportionately large (e.g., "s" prefixes).

### Meta
1. **"Design real-time trending hashtag suggestions for Instagram — how is this different from Google autocomplete?"** → Trending on Instagram is recency-weighted (last 1 hour matters more than last 30 days), not just frequency-weighted. Use an exponential decay score: `score = count × e^(-λt)`. The trie scoring function must incorporate decay, which means the top-K at each node changes more rapidly — streaming updates are more critical than at Google.
2. **"How would you personalize suggestions for a user without rebuilding the trie per user?"** → Global trie stays shared. Personalization is a stateless post-processing step: fetch user's recent query history from Redis, apply a score multiplier to any global suggestion that matches user history, re-rank the top-5, return. This avoids per-user trie storage (petabytes) at the cost of marginal re-ranking latency (<1ms).

### Common Follow-ups
1. **"How do you update suggestions in real-time without rebuilding the full trie?"** → Streaming hot-patch: Flink consumer detects when a prefix's top-K list changes (new entry appears in top-10 or existing entry's rank shifts significantly), then issues a targeted node update to the trie service. Full rebuild happens hourly as a correctness backstop; streaming handles the 95th-percentile freshness requirement.
2. **"How do you handle typos — 'restarant' instead of 'restaurant'?"** → SymSpell pre-processor on cache/trie miss: generates the most likely correction within edit distance 1 in <1ms using a pre-built frequency dictionary. Re-query the trie with the corrected term and label the result as a correction. Build-time expansion (indexing all 1-edit variants) is impractical — 26× storage growth for every term.
3. **"How do you handle queries in multiple languages and scripts (Arabic, Chinese, Japanese)?"** → Separate trie instances per language/script, selected by `Accept-Language` header or detected from the query character set. Shared caching infrastructure; language-specific scoring models (character n-gram frequency varies by language). The interviewer is checking whether your design assumes ASCII-only.

---

## Interviewer Follow-Up Questions

**On data structure trade-offs:**
- "Why a trie instead of just querying Elasticsearch for autocomplete?" → Latency. Autocomplete fires on every keystroke — p99 must be <50ms. Elasticsearch queries hit a distributed cluster over the network: ~20-80ms typical, with tail latencies spiking under load. An in-memory trie on the autocomplete service returns results in <1ms. Elasticsearch is the right tool for full search (after the user hits Enter), not for the keystroke-level suggestion loop.
- "Your trie holds 10M unique prefixes. How do you estimate its memory footprint?" → Each trie node: ~50 bytes for pointers + character + count. 10M prefixes × average 6 chars per query = ~60M nodes × 50 bytes = ~3 GB. That fits on one large machine but is tight. Optimization: compressed trie (Patricia trie) collapses single-child chains, reducing node count by 40-60%. DAWG (Directed Acyclic Word Graph) deduplicates suffix sharing further.
- "How is the trie structured to return top-K suggestions per prefix, not just all completions?" → Each trie node stores a pre-computed list of the top-K (e.g., 5) suggestions for all queries that pass through that node, not just the queries terminating there. This is computed at build time. Lookup is O(prefix_length) — traverse to the node, read the pre-computed list. Cost: O(K × N) extra storage at build time, but O(1) extra at query time.

**On freshness and updates:**
- "How does a query that trends 10 minutes ago appear in autocomplete?" → Two-path architecture: (1) Batch job rebuilds the trie hourly from the full query log — correct and comprehensive but stale. (2) Streaming pipeline (Kafka → Flink) monitors query counts per prefix in a 5-minute sliding window. When a prefix's top-K changes, it hot-patches just that trie node. The streaming path handles trending; the batch path ensures correctness.
- "How do you handle a trie hot-patch that arrives while 1000 concurrent requests are reading the trie?" → Copy-on-write: the trie is a read-only snapshot. A hot-patch writes a new version of affected nodes into a new copy, then atomically swaps the trie reference (pointer swap). In-flight readers hold a reference to the old snapshot and complete normally; new requests see the new version. No locks needed during reads.

**On personalization and safety:**
- "How do you prevent autocomplete from surfacing offensive or legally sensitive queries?" → Blocklist filter applied as a post-processing step before returning suggestions. Maintain a Redis SET of blocked phrases; check each candidate suggestion against it in O(1). For edge cases (variations of blocked terms): run candidates through a fuzzy match against the blocklist. Blocklist updates take effect immediately — no trie rebuild required.
- "A user types 'how to' — the global top suggestion might be 'how to make a bomb'. How do you personalize safely?" → Two-tier filtering: (1) Global blocklist removes absolutely prohibited suggestions. (2) Personalized re-ranking boosts suggestions based on user's own query history (stored in Redis). Safe suggestion pool → re-rank → return. Never surface suggestions the user hasn't personally engaged with if they're in a risky category.
