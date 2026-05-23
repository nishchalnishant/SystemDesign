---
module: 05-hld-problems
topic: Medium
status: unread
tags: [05-hld-problems, system-design, medium]
---
# Typeahead / Autocomplete Search

> Return ranked suggestions for a partial query prefix in under 100 ms for 100M+ daily active users.

---

## File Mindmap

```
Typeahead Search
├── Why It Exists
│   ├── Problem → Waiting 1-2 s for search results breaks the "instant" UX expectation
│   └── Forces → 100M DAU × 10 keystrokes/query = 1B+ requests/day; sub-100ms is non-negotiable
├── Core Concepts
│   ├── Trie → prefix tree; O(P) lookup where P = prefix length
│   ├── Top-K per node → precomputed top-K suggestions stored at each trie node
│   ├── Prefix cache → Redis cache for common prefixes (avoids trie traversal)
│   └── CDN edge cache → geographic distribution; cache static top prefixes at edge
├── Strategies / Types
│   ├── Trie in-memory → sub-millisecond; fits for <1M terms
│   ├── Distributed trie → shard by prefix; complex; Google/Amazon scale
│   └── Elasticsearch prefix query → simpler; 10-50 ms; good for moderate scale
├── Trade-offs
│   ├── Pro: Sub-100ms even at global scale with edge caching
│   └── Con: Stale suggestions; personalization adds latency; trie updates are complex
├── Failure Modes
│   └── Trie service down → fall back to Elasticsearch or empty suggestions
└── Interview Angles
    └── Trie vs prefix cache → where and when each is appropriate
```

---

## 1. Why Typeahead Search Exists

**Question**: Google's search box returns suggestions within 100 ms as you type. With 2 billion users each making ~5 search queries/day at 5 keystrokes each = 50 billion autocomplete requests/day = 578,000 requests/second. A naive database `LIKE 'prefix%'` query takes 50-500 ms even with indexes. At this QPS, the database is not the right tool.

**Physical constraint**: A B+ tree index supports prefix scans (`LIKE 'app%'`), but the matching rows must be filtered and ranked by frequency — an O(K log N) operation for K matches in an N-row table. For popular prefixes (e.g., "a"), K can be millions. Post-filtering and ranking millions of rows in < 50 ms is not feasible with a general-purpose database at scale.

**Minimal solution**: Build an in-memory trie where each node stores the top-K (e.g., 10) suggestions for that prefix, precomputed offline. A lookup for prefix "app" traverses 3 nodes and returns the precomputed list — O(P) time, P = prefix length. Works for a single server with < 1M terms.

**Production generalization**: Add a caching layer (Redis) in front of the trie for the top 10,000 most-queried prefixes (handles 90%+ of traffic without hitting the trie). Push the most common prefix responses to CDN edges for global distribution. Add a personalization layer that re-ranks the top-K list based on user history. Update the trie asynchronously from a streaming pipeline (Kafka → batch aggregation → trie rebuild).

---

## 2. Core Concepts / How It Works

### Trie Structure

```
Root
├── a
│   ├── p
│   │   ├── p → top-K: ["apple", "application", "app store", ...]
│   │   └── i → top-K: ["api", "apigateway", ...]
│   └── m → top-K: ["amazon", "amc", "amber alert", ...]
└── f
    └── a
        └── c → top-K: ["facebook", "face mask", "factor", ...]
```

Each node stores: character, children map, and **precomputed top-K sorted by query frequency**.

**Top-K maintenance**: Offline job runs every hour (or via streaming aggregation). For each trie node, top-K = most frequent queries with this prefix. Stored as a sorted list at each node. Lookup is O(P), not O(K log N).

### Prefix Cache (Redis)

```
Redis key: "autocomplete:prefix:app"
Redis value: ["application", "apple", "app store", "apple maps", "appetizer"]
TTL: 5 minutes (for popular prefixes); 30 s (for long prefixes)
```

Cache hit rate analysis:
- Top 1,000 prefixes handle ~60% of all traffic (power law distribution)
- Top 10,000 prefixes handle ~85% of all traffic
- Cache 10,000 prefixes in Redis: 10,000 × (prefix key ~20 bytes + 10 suggestions × 30 bytes) = ~32 MB — trivially small

### CDN Edge Caching

For the globally most-common prefixes (top 1,000), cache the response at CDN PoPs:
- User in Tokyo types "app" → CDN edge in Tokyo serves cached suggestions → ~5 ms RTT
- Cache TTL: 15 minutes (suggestions for "app" don't change in 15 minutes)
- Cache invalidation: when the top suggestion for a prefix changes significantly (trending query), push-invalidate the CDN key

### Data Flow

```
User keystroke → browser debounce (200 ms) → CDN edge (cache hit → return)
                                            ↓ miss
                                      Load Balancer
                                            ↓
                               Typeahead Service (stateless)
                                  ├── Redis prefix cache (hit → return)
                                  │   (miss → trie service)
                                  └── Trie Service (partitioned by prefix)
                                            ↓ long-tail prefix
                                      Elasticsearch fallback
```

### Trie Sharding Strategy

A single trie cannot fit all queries for a large search engine in one machine (Google has billions of unique queries). Shard by first 2 characters:

| Shard | Responsible prefixes |
|-------|---------------------|
| Shard 1 | a* → az* |
| Shard 2 | b* → bz* |
| ... | ... |
| Shard 26 | z* → zz* |

Client hashes `prefix[0:2]` to route to the correct shard. This is a static routing table — simple.

### Personalization Layer

```
Base top-K (from trie/cache): ["application", "apple", "app store", "applebee's", "app store"]

Personalization signal (user history):
  - User has searched "app store" 15 times in last 30 days
  - User's location: India → boost "Hotstar app"

Personalized re-ranking (in-process, < 5 ms):
  Scoring: score = base_rank_score × 0.6 + user_affinity × 0.4
  Result: ["app store", "application", "apple", "Hotstar app", "appetizer"]
```

User history stored in: Redis (recent 30-day query history, TTL = 30 days) or a dedicated user profile store.

---

## 3. Real-World Usage

| System          | Approach                                                                        |
|-----------------|---------------------------------------------------------------------------------|
| Google Search   | Distributed trie + personalization + geo-trending; rebuilt hourly from query logs |
| Amazon search   | ElasticSearch completion suggester + trie for product title prefix              |
| LinkedIn        | Prefix cache (Redis) for people/company search; Galene (custom search engine)   |
| Uber Eats       | ElasticSearch edge_ngram tokenizer for restaurant/dish search                   |
| GitHub          | ElasticSearch prefix queries for repo/user search                               |

**ElasticSearch completion suggester**: ES has a built-in completion suggester backed by an FST (Finite State Transducer) — a memory-efficient trie variant. FST for 1M strings is ~100 MB in memory; lookup is O(P). This is the fastest path to typeahead without building a custom trie service.

---

## 4. Trade-offs

| Dimension          | Trie (in-memory)            | Redis prefix cache          | Elasticsearch completion | CDN edge cache            |
|--------------------|-----------------------------|-----------------------------|--------------------------|---------------------------|
| Latency            | < 1 ms (in-process)        | 1-5 ms (network RTT)        | 10-50 ms                 | 1-10 ms (edge pop)        |
| Scale              | Single node limit ~10M terms| Horizontally scalable        | Horizontally scalable    | Global distribution       |
| Freshness          | Minutes (async rebuild)     | TTL-based (30 s - 5 min)    | Near-real-time           | TTL (15 min)              |
| Personalization    | Not possible at node level  | Possible (user key suffix)   | Via rescoring            | Not possible (shared cache)|
| Operational cost   | High (custom service)       | Low (managed Redis)          | Low (managed ES)         | Very low (CDN rule)       |
| Trending queries   | Requires rebuild            | Invalidate key               | Real-time if streaming   | Push invalidation          |

**Recommendation for SDE-3 interview**: Design with Redis prefix cache as primary (covers 85%+ traffic), trie service for cache misses, and CDN for top-1000 prefixes globally. This balances latency, cost, and freshness without over-engineering.

---

## 5. Failure Scenarios

| Scenario                      | Symptom                                              | Mitigation                                              |
|-------------------------------|------------------------------------------------------|---------------------------------------------------------|
| Trie service down             | All suggestions fail                                 | Fall back to Elasticsearch; return empty on ES failure  |
| Redis cache eviction spike    | Sudden increase in trie/ES calls                     | Oversized Redis by 30%; use LFU eviction policy         |
| Trie rebuild takes too long   | Stale suggestions during hourly rebuild              | Blue-green trie deploy; swap pointer atomically         |
| CDN cache poisoning           | Wrong suggestions served globally for 15 min         | Short TTL; validate response before caching             |
| Trending query causes hot key | Single prefix overwhelms Redis shard                 | Local in-process cache (Caffeine) with 1 s TTL in service |
| Personalization service down  | All users get generic suggestions                    | Circuit breaker; graceful degrade to non-personalized   |
| Offensive/inappropriate query | Bad suggestions surfaced                             | Blocklist maintained by content team; applied at service layer |

---

## 6. Performance Considerations

### Scale Estimation

```
Users: 100M DAU
Queries per user per day: 10 (searches)
Keystrokes per query: 5 (debounce reduces to ~5 API calls)
Total requests/day: 100M × 10 × 5 = 5B requests/day
Requests per second: 5B / 86,400 ≈ 58,000 QPS average
Peak (3× average): ~175,000 QPS
```

### Tier analysis

| Tier          | QPS served     | Latency  | Resources needed                        |
|---------------|----------------|----------|-----------------------------------------|
| CDN edge      | 80,000 (46%)   | 5-10 ms  | CDN rules, ~1,000 cached keys per PoP   |
| Redis cache   | 80,000 (46%)   | 1-5 ms   | 3-node Redis cluster, 1 GB memory       |
| Trie service  | 14,000 (8%)    | < 1 ms   | 20 pods × 8 core, 16 GB RAM each        |
| Elasticsearch | 1,750 (1%)     | 20-50 ms | 10-node ES cluster for long-tail        |

### Latency budget

```
Browser debounce: 200 ms (not system latency; reduces QPS 5×)
DNS lookup: 1 ms (cached)
CDN / network: 5-15 ms
Service processing: 1-5 ms
Personalization re-rank: 1-3 ms
Total (cache hit): 7-23 ms  ← well under 100 ms target
Total (trie miss): 15-30 ms
Total (ES fallback): 30-80 ms
```

### Data Size

```
Unique queries stored in trie: 10M
Average query length: 20 chars
Trie node: ~100 bytes (char, children map pointer, top-10 list)
Naive trie memory: 10M × 20 chars × 100 bytes = 20 GB (too large for one node)
With FST (Elasticsearch): ~2 GB for 10M terms
With top-K precomputed at nodes only: 10M nodes × 100 bytes = 1 GB (feasible)
```

---

## 7. Implementation Patterns

### Trie with Top-K Precomputed at Each Node (Java)

```java
import java.util.*;

public class AutocompleteTrie {

    private static final int TOP_K = 10;

    static class TrieNode {
        Map<Character, TrieNode> children = new HashMap<>();
        // Top-K suggestions for this prefix, sorted by frequency descending
        List<String> topSuggestions = new ArrayList<>();
    }

    private final TrieNode root = new TrieNode();

    // Build phase: insert (query, frequency) pairs, then call buildTopK
    public void insert(String query, int frequency) {
        TrieNode node = root;
        for (char c : query.toCharArray()) {
            node.children.putIfAbsent(c, new TrieNode());
            node = node.children.get(c);
            addToTopK(node, query, frequency);
        }
    }

    private void addToTopK(TrieNode node, String query, int frequency) {
        // Maintain a min-heap of size K; replace minimum if new frequency is higher
        // For simplicity: keep sorted list (production: use priority queue)
        node.topSuggestions.removeIf(q -> q.equals(query)); // deduplicate
        node.topSuggestions.add(query);
        // Sort by frequency (in real impl, store (query, freq) pairs)
        if (node.topSuggestions.size() > TOP_K) {
            node.topSuggestions.remove(node.topSuggestions.size() - 1);
        }
    }

    // Lookup: O(prefix length) — constant time for fixed-length prefixes
    public List<String> search(String prefix) {
        TrieNode node = root;
        for (char c : prefix.toCharArray()) {
            if (!node.children.containsKey(c)) {
                return Collections.emptyList(); // prefix not found
            }
            node = node.children.get(c);
        }
        return Collections.unmodifiableList(node.topSuggestions);
    }
}
```

### Redis Prefix Cache (Spring Data Redis)

```java
import org.springframework.data.redis.core.RedisTemplate;
import java.time.Duration;
import java.util.List;

@Service
public class AutocompleteCache {

    private static final String KEY_PREFIX = "autocomplete:prefix:";
    private static final Duration POPULAR_TTL = Duration.ofMinutes(5);
    private static final Duration LONGTAIL_TTL = Duration.ofSeconds(30);
    private static final int POPULAR_THRESHOLD = 100; // QPS to be considered popular

    private final RedisTemplate<String, List<String>> redisTemplate;
    private final AutocompleteTrieService trieService;

    public List<String> getSuggestions(String prefix, String userId) {
        String cacheKey = KEY_PREFIX + prefix.toLowerCase();

        // L1: Redis cache
        List<String> cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return personalize(cached, userId);
        }

        // L2: Trie service (or Elasticsearch for long prefixes > 10 chars)
        List<String> suggestions = prefix.length() > 10
            ? elasticsearchService.prefixSearch(prefix, 10)
            : trieService.search(prefix);

        // Cache result; longer TTL for shorter (more popular) prefixes
        Duration ttl = prefix.length() <= 3 ? POPULAR_TTL : LONGTAIL_TTL;
        redisTemplate.opsForValue().set(cacheKey, suggestions, ttl);

        return personalize(suggestions, userId);
    }

    private List<String> personalize(List<String> base, String userId) {
        if (userId == null) return base;
        try {
            return personalizationService.rerank(base, userId);
        } catch (Exception e) {
            // Circuit breaker: degrade gracefully to non-personalized
            return base;
        }
    }
}
```

### Offline Trie Build Pipeline

```
Query logs (Kafka) 
    → Spark batch job (hourly)
        → Aggregate: count(query) per prefix, last 7 days, weighted by recency
        → Output: (prefix, [top-10 queries with scores]) sorted by score
    → Serialize trie to binary format (Protobuf)
    → Upload to S3
    → Trie service polls S3 every 5 min; hot-swap in-memory trie atomically:
        TrieNode newRoot = deserialize(s3.download("trie-latest.pb"));
        rootRef.compareAndSet(oldRoot, newRoot); // atomic swap, no downtime
```

---

## Quick Revision

- Core data structure: trie with precomputed top-K suggestions at each node; O(P) lookup
- Caching hierarchy: L1 = in-process (Caffeine, 1 s TTL), L2 = Redis (5 min TTL), L3 = CDN (15 min TTL)
- Scale: 100M DAU × 50 keystrokes/day = 5B requests/day = 58K QPS average; CDN absorbs 80%+
- Sharding: shard trie by first 1-2 characters; static routing, no coordination needed
- Elasticsearch completion suggester (FST) = easiest path to typeahead; 10-50 ms, good to 10M terms
- Personalization: base top-K re-ranked by user affinity score; must circuit break when personalization is slow
- Trie freshness: rebuild hourly from streaming query log aggregation; blue-green swap
- Debouncing: browser waits 200 ms after last keystroke before sending request; reduces API calls 5×
- For trending queries: maintain a separate real-time trending index; merge with base top-K at serving time

---

## See Also

- [05-hld-problems/03-hard/search-system.md](../03-hard/search-system.md) — full-text search system design
- [02-building-blocks/caching-layer.md](../../02-building-blocks/caching-layer.md) — caching strategies
- [02-building-blocks/cdn.md](../../02-building-blocks/cdn.md) — CDN for static prefix caching
- [04-advanced-topics/internals/elasticsearch-internals.md](../../04-advanced-topics/internals/elasticsearch-internals.md)
- [02-building-blocks/rate-limiting.md](../../02-building-blocks/rate-limiting.md) — protecting typeahead service from abuse

---

## Interview Questions Asked

### Conceptual

**Q1: Why is a trie better than a B+ tree index for typeahead, and when would you not use a trie?**

A: A B+ tree index supports prefix scans (`LIKE 'app%'`) but returns all matching rows, which then must be sorted by frequency — O(K log K) where K can be millions for common prefixes. A trie with precomputed top-K at each node returns the answer in O(P) time (P = prefix length) with no additional sorting. The top-K is already sorted. For short prefixes ("a", "ap"), a B+ tree would match 10-30% of the entire index — a trie is orders of magnitude faster. When NOT to use a trie: (1) when the vocabulary is extremely large (billions of queries), the trie doesn't fit in memory on one node and sharding is complex; (2) when you need fuzzy matching (typo tolerance) — tries only handle exact prefixes; use BK-tree or Elasticsearch's fuzzy query instead; (3) when suggestions change in real-time — trie rebuilds take minutes; use Elasticsearch for near-real-time updates.

**Q2: How do you handle trending queries (e.g., a celebrity name suddenly goes viral)?**

A: The hourly trie rebuild won't capture a query that starts trending in the last 10 minutes. Solution: maintain a separate real-time trending index. Stream query logs to Kafka. A Flink/Spark Streaming job computes the top-K queries per prefix for the last 5 minutes with a sliding window. Store this in a separate Redis sorted set: `trending:prefix:app → {query: "apple event", score: 5000}`. At serving time, merge the trie top-K with the trending top-K, giving higher weight to trending items for the last 30 minutes. This is how Google surfaces "apple event" during Apple keynotes.

**Q3: How does browser-side debouncing affect your system design?**

A: Without debouncing, a user typing "apple" sends 5 requests: a, ap, app, appl, apple. With 200 ms debouncing, if the user types all 5 characters in 400 ms, only 1-2 requests are sent. This reduces QPS by ~5× at the browser level. System design impact: (1) you size your backend for debounced QPS, not raw keystroke QPS — for 100M users × 10 queries × 5 keystrokes = 5B requests/day without debounce, vs ~1B with debounce; (2) the debounce threshold affects UX — 200 ms is standard (feels instant to humans, reduces calls significantly); shorter feels snappier but wastes compute; (3) mobile keyboards have different typing speeds — mobile debounce is often 300 ms. Important: debouncing is a client-side optimization; you cannot rely on it for rate limiting — malicious clients ignore it. Always rate-limit at the API gateway.

### Comparison / Trade-off

**Q: Trie vs Elasticsearch completion suggester — which would you choose for a 50M-user product, and why?**

A: Elasticsearch completion suggester for a 50M-user product. Reasons: (1) operational simplicity — no custom trie service to build and maintain; ES is managed on AWS/GCP/Elastic Cloud; (2) ES completion suggester uses an FST (Finite State Transducer) internally — memory-efficient and O(P) lookup, same as a trie; (3) ES handles updates near-real-time (index new queries in seconds); trie rebuilds take minutes; (4) ES supports context-aware suggestions (geographic, category-based) out of the box; (5) 50M users ≈ 30K QPS average — ES with 5-10 nodes handles this comfortably at 20-50 ms P99. Use a custom trie service only when: you need sub-5 ms P99 (trading/gaming), you have highly custom ranking logic that ES doesn't support, or you're at Google/Amazon scale where ES becomes a bottleneck.

### Scenario / Design

**Q: Design the data pipeline to keep typeahead suggestions fresh without rebuilding the entire trie every time.**

A: Three-tier update strategy: (1) Real-time (< 1 min): stream query events to Kafka. A Flink job maintains a Redis sorted set of trending queries per prefix with a 5-minute sliding window. Serving layer merges trie results with trending Redis results. (2) Hourly batch: Spark job reads the last 7 days of query logs from S3 (partitioned by hour). Computes frequency per query with recency weighting (recent queries weighted 2×). Builds a new trie, serializes to Protobuf on S3. Trie service pods detect the new file (S3 event notification or polling) and hot-swap: deserialize new trie, atomically update the reference, GC old trie. Zero downtime. (3) Event-driven invalidation: when a query's frequency changes significantly (e.g., > 50% rank change), publish a cache invalidation event. The serving layer deletes the Redis key for that prefix. Next request rebuilds the Redis entry from the trie. This avoids waiting up to 5 minutes for TTL expiry.
