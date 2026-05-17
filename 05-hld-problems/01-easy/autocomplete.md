# Design Autocomplete / Typeahead Search

> **Difficulty**: Easy
> **Topics**: Trie, Caching, Prefix Search
> **Time**: 45-60 min
> **Companies**: Google, Amazon, Meta

---

## Real-Life Analogy

Picture a well-organized bookstore with an experienced clerk. When you walk to the "S" section and start saying "Sal-", the clerk immediately offers "Salinger? Sales? Salami?" without pausing to think. They are not searching from scratch — they have already spent years mentally grouping inventory by section, then by shelf, then by the first three letters. The answer is pre-sorted in their head before you finish the word. That is exactly how a production autocomplete system works: by the time your query arrives, the top completions for every prefix have already been computed, ranked, and cached. The query path does almost no computation — it is a lookup.

Now consider predictive text on a smartphone keyboard. When you type "hap-", the keyboard shows "happy", "happened", "happiness". The order is not alphabetical — it reflects a blend of global usage frequency, your personal history ("happiness" because you use it more than others), and recent context (the word before). If you just typed "feel", the keyboard surfaces "happy" before "happening" because it has learned that "feel happy" is more common than "feel happening" in your writing. Autocomplete at scale works the same way: a global frequency score is blended with a per-user recency signal to produce a ranked list.

One more frame: think about how Google Search behaves. When 500 million people type "how to make" every day, Google does not evaluate completions fresh for each one. It pre-computes the top-10 completions for "how to make" in an offline pipeline, stores them, and serves them from memory. The real-time query path is read-only. Google only re-runs the ranking pipeline when global query frequency shifts enough to change the top-10. Understanding that the write path (building the trie / frequency table) and the read path (serving suggestions) are completely decoupled is the central insight of this problem.

---

## Why This Is Hard

1. **Latency budget is tiny**: Suggestions must appear within 100ms of a keystroke — users perceive anything slower as broken. This budget includes network round-trip, prefix lookup, ranking, and serialization. You have roughly 20ms of actual compute to spend.
2. **Scale makes a single trie impossible**: Google's query corpus at 8.5 billion searches/day spans tens of billions of unique prefix strings. A single in-memory trie for all English queries does not fit on one machine. Partitioning the trie introduces lookup-routing complexity.
3. **Freshness vs. cost tension**: A trending query ("ChatGPT outage") must appear in suggestions within minutes. But rebuilding a full trie from scratch takes hours. You need an architecture that can hot-patch trending terms without a full rebuild.
4. **Prefix explosion**: The string "internet" generates 8 prefixes (i, in, int, inte, inter, ...). At Google scale, every new query multiplies the storage requirement 8x on average. Storage and update fan-out grow faster than raw query volume.
5. **Typo tolerance is expensive at query time**: If you want to show "restaurant" when the user typed "restarant", you need either a fuzzy index built at write time (large, expensive) or edit-distance computation at query time (too slow at scale). The choice is a major architectural decision.
6. **Personalization without per-user tries**: Storing a full trie per user is infeasible (1 billion users × trie size = petabytes). But serving the same global suggestions to everyone produces poor results. You need a lightweight per-user signal that overlays on top of the shared global structure.

---

## Requirements Gathering

### Functional Requirements

**Must-Have:**
1. Given a prefix typed by the user, return the top-5 completions ranked by relevance
2. Suggestions update to reflect new/trending queries within 10-15 minutes
3. Suggestions are language-scoped (English vs. Spanish vs. Hindi are separate indexes)

**Nice-to-Have:**
4. Personalized suggestions (user's own recent queries ranked higher)
5. Fuzzy matching (tolerate one character typo)
6. Contextual suggestions (previous word influences ranking)
7. Safe-search filtering (remove adult/offensive completions by default)

### Non-Functional Requirements

**Performance:**
- P99 latency: < 100ms end-to-end (target < 20ms at the service layer)
- Throughput: 100K suggestion requests/sec at peak (Google Search scale)

**Availability:**
- 99.99% uptime — autocomplete failure is noticeable but not catastrophic
- Stale suggestions (5 min old) are acceptable during partial outages

**Freshness:**
- New trending queries surface within 10-15 minutes
- Stable queries rebuild on a 1-hour batch cycle

**Scalability:**
- 100M unique prefixes indexed
- 500M queries/day driving frequency signal updates

**Consistency:**
- Eventual consistency acceptable; not every server needs to serve identical top-5 at the same instant

---

## Capacity Estimation

### Traffic Estimates

```
Assumptions:
- 500M searches/day on the platform
- On average, each search triggers 4 autocomplete requests (user types 4-8 chars)
- 10:1 peak-to-average ratio

Autocomplete requests:
500M × 4 = 2B autocomplete requests/day
2B / 86,400 sec = 23,000 requests/sec (average)
Peak (10x): 100,000 requests/sec (100K QPS)

Write path (frequency updates):
500M query completions/day → fed into Kafka → batched every 10 min
→ 500M / (6 batches/hour × 24 hours) = ~3.5M query events/batch
```

### Storage Estimates

```
Trie node storage:
- Assume 100M unique prefixes (English)
- Each trie node: prefix string (avg 5 chars = 5 bytes) + top-5 terms (5 × 50 bytes avg) + metadata (score, pointer) = ~300 bytes/node

Total trie storage:
100M nodes × 300 bytes = 30 GB

With language multiplier (10 languages): 300 GB

Query frequency table (prefix → top-k list):
- Stored separately in Redis as key-value
- Key: "prefix:how_to_make" (avg 20 bytes)
- Value: JSON list of 5 suggestions (avg 500 bytes)
- 100M entries × 520 bytes = 52 GB (per language, hot prefixes only)

Cold prefix storage (PostgreSQL, long tail):
100M prefixes × 520 bytes = 52 GB/language → manageable
```

### Bandwidth Estimates

```
Request payload: prefix string (avg 8 bytes)
Response payload: 5 suggestions × 50 chars avg = 250 bytes JSON

Inbound: 100K req/sec × 8 bytes = 0.8 MB/sec
Outbound: 100K req/sec × 250 bytes = 25 MB/sec ≈ 200 Mbps peak

Write pipeline (Kafka):
500M events/day × 30 bytes/event = 15 GB/day ingest
= 1.7 MB/sec sustained
```

### Cache Requirements

```
Observation: prefix traffic is extremely skewed.
Top-3 prefix buckets (by length) cover ~80% of traffic.
Queries of length 1-3 chars are most common; length > 8 is rare.

Hot prefix cache size:
- Prefixes of length 1: 26 (English letters)
- Prefixes of length 2: 676
- Prefixes of length 3: 17,576
- Total hot prefixes: ~18,000

18K × 500 bytes per suggestion list = 9 MB — fits in L1 memory on any server

Extended cache (top 1M frequent prefixes):
1M × 500 bytes = 500 MB Redis cache per region (trivial)

Cache hit rate target: >99% (only the long tail misses)
```

---

## API Design

**1. Get Autocomplete Suggestions**
```http
GET /api/v1/autocomplete?q={prefix}&limit=5&lang=en&uid={user_id}

Parameters:
  q      - prefix string typed by the user (required)
  limit  - number of suggestions (default: 5, max: 10)
  lang   - language code (default: "en")
  uid    - user ID for personalization (optional)

Response: 200 OK
{
  "prefix": "how to m",
  "suggestions": [
    { "term": "how to make money", "score": 0.94 },
    { "term": "how to make friends", "score": 0.87 },
    { "term": "how to make pasta", "score": 0.81 },
    { "term": "how to make slime", "score": 0.76 },
    { "term": "how to make a resume", "score": 0.71 }
  ],
  "served_from": "cache"
}
```

**2. Ingest Query Event (Internal)**
```http
POST /internal/v1/query-event
Content-Type: application/json

Request:
{
  "query": "how to make money",
  "user_id": "u_abc123",
  "timestamp": "2026-05-13T14:23:00Z",
  "lang": "en",
  "selected_suggestion": "how to make money"  // null if user typed full query
}

Response: 202 Accepted
```

**3. Force Refresh Prefix (Admin)**
```http
POST /internal/v1/trie/refresh
Content-Type: application/json

Request:
{
  "prefix": "chatgpt",
  "lang": "en"
}

Response: 200 OK
{ "refreshed": true, "new_top5": ["chatgpt outage", "chatgpt login", ...] }
```

---

## High-Level Architecture

```
┌──────────┐
│  Client  │  (browser / mobile)
└────┬─────┘
     │ GET /autocomplete?q=how+to+m
     ▼
┌─────────────────────┐
│  CDN / Edge Cache   │  (Cloudflare — caches top-1M prefix responses, 60s TTL)
└──────────┬──────────┘
           │ Cache miss
           ▼
┌─────────────────────┐
│   Load Balancer     │  (L7, routes by language shard)
└──────────┬──────────┘
           │
     ┌─────┴──────┐
     ▼            ▼
┌─────────┐  ┌─────────┐
│ Query   │  │ Query   │  Autocomplete Service (stateless, 50-200 instances)
│ Server  │  │ Server  │  - Prefix normalization (lowercase, trim)
└────┬────┘  └────┬────┘  - Personalization score overlay
     │             │       - Top-K merge
     ▼             ▼
┌────────────────────────────────────┐
│  Redis Cluster (Suggestion Cache)  │
│  Key: "en:how to m"               │
│  Value: JSON top-5 list           │
│  TTL: 60 seconds (hot prefixes)   │
│  ~500 MB per region               │
└──────────────────┬─────────────────┘
                   │ Cache miss (<1% of requests)
                   ▼
┌────────────────────────────────────┐
│  Trie Service (in-process / RPC)   │
│  - Sharded trie in memory          │
│  - Prefix → top-k lookup in O(L)   │
│  - 30 GB per language, sharded     │
└──────────────────┬─────────────────┘
                   │ Long-tail miss
                   ▼
┌────────────────────────────────────┐
│  PostgreSQL (cold prefix store)    │
│  - All prefixes + top-5 JSON blob  │
│  - Fallback for uncached prefixes  │
└────────────────────────────────────┘

-- Write / Update Path --

┌──────────┐
│  Client  │ (completes a search)
└────┬─────┘
     ▼
┌─────────────────┐
│  Ingest API     │ (POST /internal/v1/query-event)
└────────┬────────┘
         ▼
┌─────────────────┐
│  Kafka Topic    │  query-events (partitioned by lang)
│  500M events/day│
└────────┬────────┘
         ▼
┌──────────────────────────────────────┐
│  Frequency Aggregation Service       │
│  (Flink / Spark Streaming)           │
│  - Windowed count per query term     │
│  - Recency decay: score × e^(-λt)   │
│  - Output: (prefix, term, score)     │
└──────────────┬───────────────────────┘
               │
     ┌─────────┴────────────┐
     ▼                      ▼
┌──────────────┐     ┌──────────────────────┐
│ Trie Builder │     │  Hot Patch Service   │
│ (batch, 1h)  │     │  (streaming, <10min) │
│ Full rebuild │     │  Top-N trending terms│
└──────┬───────┘     └──────────┬───────────┘
       │                        │
       ▼                        ▼
┌──────────────────────────────────────┐
│  Trie Service (in-memory, per shard) │
│  + Redis cache invalidation          │
└──────────────────────────────────────┘
```

---

## Data Flow

### Write / Update Flow (Query Ingestion)

```
1. User completes a search: "how to make pasta"
2. Client fires POST /internal/v1/query-event asynchronously (fire-and-forget, does not block search)
3. Ingest API validates event and publishes to Kafka topic "query-events-en" (partition key: lang)
4. Flink streaming job consumes events in 10-minute tumbling windows:
   a. Counts occurrences of each (prefix, completion_term) pair in the window
   b. Applies recency decay to the running global score:
      new_score = (window_count × recency_weight) + (old_score × decay_factor)
   c. Recomputes top-5 for each affected prefix
5. Hot Patch Service pushes updated (prefix → top-5) directly into:
   a. Redis: SET "en:how to m" <new JSON> EX 60
   b. Trie Service: RPC to invalidate and reload affected trie subtree
6. Full batch rebuild (every 1 hour):
   a. Spark job reads entire query_events table from S3 (raw event log)
   b. Recomputes global frequency scores for all 100M prefixes
   c. Serializes trie to disk, deploys to Trie Service nodes via blue-green swap
   d. Flushes Redis keys for affected prefixes
```

### Read / Query Flow (Serving Suggestions)

```
1. User types "how to m" — browser fires GET /api/v1/autocomplete?q=how+to+m&lang=en&uid=u_abc123
2. CDN checks edge cache (60s TTL):
   - HIT (~60% of traffic): return cached response immediately (<5ms)
   - MISS: forward to Load Balancer
3. Load Balancer routes to a Query Server (routes by language for data locality)
4. Query Server normalizes prefix: lowercase, strip leading/trailing whitespace, collapse internal spaces
5. Query Server checks Redis: GET "en:how to m"
   - HIT (~39% of remaining traffic): deserialize JSON, proceed to step 8
   - MISS (<1%): proceed to step 6
6. Query Server calls Trie Service (same region, in-process or local RPC):
   a. Trie traversal to node representing "how to m" — O(L) where L = prefix length
   b. Return pre-computed top-5 list stored at that node
7. If Trie Service misses (long-tail prefix not in loaded shard): fall through to PostgreSQL
   SELECT top5_json FROM prefix_suggestions WHERE prefix = 'how to m' AND lang = 'en'
8. Personalization overlay (if uid provided):
   a. Fetch user's personal query history from User Profile Service (Redis, LRU per user, 50-entry cap)
   b. Boost score of any global suggestion that also appears in user history:
      final_score = global_score × (1 + personalization_boost × recency_factor)
   c. Re-sort top-5 by final_score
9. Return JSON response to client
10. Populate Redis and CDN edge cache for this prefix (write-back)
```

---

## Deep Dive Topics

### 1. Trie Data Structure

A trie (prefix tree) stores strings character by character. Each node represents one character, and a path from root to a node spells out a prefix. The critical insight for autocomplete: at each node you do not store all descendants — you precompute and store the top-K completions. This converts a subtree traversal (expensive) into a direct node lookup (O(L) where L is the prefix length).

```java
public class TrieNode {
    private final Map<Character, TrieNode> children = new HashMap<>();
    // Precomputed top-5 completions for this prefix, sorted by score descending
    private final List<Suggestion> topK = new ArrayList<>(5);
    private boolean isTerminal = false;

    public TrieNode getChild(char c) {
        return children.get(c);
    }

    public TrieNode getOrCreateChild(char c) {
        return children.computeIfAbsent(c, k -> new TrieNode());
    }

    public List<Suggestion> getTopK() {
        return Collections.unmodifiableList(topK);
    }

    public void updateTopK(Suggestion candidate, int k) {
        topK.add(candidate);
        topK.sort(Comparator.comparingDouble(Suggestion::getScore).reversed());
        if (topK.size() > k) {
            topK.remove(topK.size() - 1);
        }
    }
}

public class AutocompleteTrie {
    private final TrieNode root = new TrieNode();
    private static final int TOP_K = 5;

    // Called during trie build — O(L × K) per term insertion
    public void insert(String term, double score) {
        TrieNode current = root;
        Suggestion suggestion = new Suggestion(term, score);
        for (char c : term.toCharArray()) {
            current = current.getOrCreateChild(c);
            current.updateTopK(suggestion, TOP_K);  // Update top-K at every prefix node
        }
        current.setTerminal(true);
    }

    // Query path — O(L), returns pre-sorted list
    public List<Suggestion> search(String prefix) {
        TrieNode current = root;
        for (char c : prefix.toCharArray()) {
            current = current.getChild(c);
            if (current == null) return Collections.emptyList();
        }
        return current.getTopK();
    }
}

public record Suggestion(String term, double score) {
    public double getScore() { return score; }
}
```

**Trie vs. Prefix-Indexed DB (B-tree with LIKE queries)**

| Dimension | Trie (in-memory) | B-tree + `LIKE 'prefix%'` |
|---|---|---|
| Lookup latency | O(L) — microseconds | O(log N + K) — milliseconds |
| Ranking at query time | None needed (precomputed) | Requires ORDER BY — expensive |
| Memory cost | High (30 GB for 100M prefixes) | Low (indexes fit in buffer pool) |
| Update complexity | Full subtree update on insert | Single row insert |
| When to choose | High QPS, latency-critical | Low QPS, infrequent queries, small corpus |

Use a B-tree with `LIKE 'prefix%'` only when the query corpus is small (< 1M terms) or query volume is low (< 1K QPS). At Google/Amazon scale, the trie is the only viable in-memory structure.

**Partitioning the Trie Across Machines**

At Google scale (8.5B searches/day, hundreds of languages), a single trie per language does not fit on one machine. Two partitioning strategies:

- **Partition by first letter**: 26 shards per language, each handling one starting letter. Simple routing (`shard = prefix[0]`). Problem: highly uneven — 'S' and 'C' prefixes are far more common than 'X' or 'Z'. Leads to hot shards.
- **Consistent hash of first N characters**: Hash the first 3 characters of the prefix to pick a shard. Spreads load more evenly. Problem: a query for "ho" must fan out to all shards that own any prefix starting with "ho" (e.g., "hoa", "hob", ..., "hoz") unless you route by full 3-char prefix.

Production approach: partition by first 2-3 characters (the prefix "hot bucket"). Route `prefix[:3]` to a shard deterministically. Within the shard, serve all completions for prefixes sharing that 3-char bucket. This gives 17,576 buckets for English — assign ~5-10 buckets per machine across ~3,000 shards.

---

### 2. Scoring and Ranking

The score for a term in the autocomplete list is:

```
final_score = term_frequency_score × recency_weight × personalization_multiplier
```

**Term frequency score**: Normalized log count of how many times this query was submitted globally in the trailing 30 days.

```java
double termFrequencyScore(long rawCount, long maxCountInCorpus) {
    return Math.log1p(rawCount) / Math.log1p(maxCountInCorpus);  // Normalized to [0,1]
}
```

**Recency weight**: Queries spike (breaking news) and decay. Apply exponential decay over time:

```java
double recencyWeight(Instant termLastSeen, Instant now, double halfLifeHours) {
    double hoursAgo = Duration.between(termLastSeen, now).toHours();
    double lambda = Math.log(2) / halfLifeHours;  // decay constant
    return Math.exp(-lambda * hoursAgo);
}
// halfLifeHours = 24 for news queries; = 168 (1 week) for stable queries
```

**Personalization score**: If the user has typed "how to make" before (in their personal history), boost that completion:

```java
double personalizationMultiplier(String term, List<String> userHistory) {
    long recencyIndex = userHistory.indexOf(term);  // -1 if not found
    if (recencyIndex == -1) return 1.0;  // No boost
    // More recent = higher boost. Most recent item (index 0) gets 2x boost.
    return 1.0 + (1.0 / (recencyIndex + 1));  // 2.0, 1.5, 1.33, 1.25, ...
}
```

**Final ranking**:
```java
double finalScore = termFrequencyScore × recencyWeight × personalizationMultiplier;
```

Sort top-5 by `finalScore` descending. The global trie stores `(term_frequency_score × recency_weight)` at build time. Personalization overlay is applied at query serve time in the Query Server, not in the trie.

---

### 3. Caching Strategy

Three caching layers, each serving a different purpose:

**Layer 1 — CDN Edge Cache (Cloudflare/Fastly)**
- Caches the full HTTP response for a prefix string
- TTL: 60 seconds (short enough to propagate trending updates)
- Coverage: top-1M most common prefixes cover ~60% of traffic
- Key: `lang + normalized_prefix` — same response for all users (no personalization at this layer)

**Layer 2 — Redis Cluster (Global Prefix Cache)**
- Key: `"en:how to m"` → JSON list of top-5 (without personalization)
- TTL: 60 seconds for hot prefixes, 300 seconds for warm prefixes
- Size: ~500 MB per language for top-1M prefixes (trivially small)
- Hit rate: ~99% for prefixes of length <= 6 (where traffic concentrates)

**Why locality of reference dominates here**: Prefix length follows a power-law distribution. Most users type 3-6 characters before selecting a suggestion. The universe of distinct prefixes of length <= 6 for English is: 26 + 676 + 17,576 + 456,976 + ... ≈ 500K prefixes. At 500 bytes per entry, that is 250 MB — easily warm in a single Redis instance. The entire hot tier fits in-process as an LRU map on each Query Server (no Redis call needed for the top-10K prefixes).

**Layer 3 — In-Process LRU on Query Server**
- Java `LinkedHashMap`-based LRU, 10K entries per server instance
- No network hop — sub-microsecond lookup
- Covers the ~10K prefixes that account for ~40% of traffic (single-letter and two-letter prefixes + most common three-letter prefixes)

```java
// In-process LRU cache (thread-safe)
private final Map<String, List<Suggestion>> localCache =
    Collections.synchronizedMap(new LinkedHashMap<>(10_000, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<Suggestion>> eldest) {
            return size() > 10_000;
        }
    });
```

**Cache Invalidation on Trie Update**: When the Hot Patch Service updates a prefix, it publishes a Redis `PUBLISH invalidate-prefix "en:how to m"`. Query Servers subscribe and clear their local LRU entry. CDN entries expire naturally via TTL (acceptable 60s stale window for trending).

---

### 4. Personalization and Fuzzy Matching

**Personalization without per-user tries**

Storing a separate trie per user is economically infeasible: 1 billion users × 30 GB/trie = 30 exabytes. Instead, use a two-tier approach:

- The shared global trie serves the base top-5 for every prefix.
- A per-user query history store (Redis hash, key: `user:{uid}:history`, value: ordered list of last 50 queries, TTL: 30 days) is fetched at query time.
- The Query Server takes the global top-5, applies the personalization multiplier (see Scoring section), re-sorts, and returns the adjusted top-5.
- Storage per user: 50 queries × 30 chars = 1,500 bytes/user. 1B users × 1,500 bytes = 1.5 TB — manageable in Redis Cluster.

This means personalization never touches the trie. The trie is purely global. Personalization is a stateless computation at serve time layered on top.

**Fuzzy Matching: build time vs. query time**

| Approach | Cost | Latency | Quality |
|---|---|---|---|
| Query-time edit distance (BFS over trie) | Low build cost | High (10-100ms) | High |
| Build-time expansion (index all 1-edit variants) | High storage (26x expansion) | Low (<1ms) | High |
| Phonetic normalization (Soundex, Metaphone) | Medium | Low | Medium |
| Separate fuzzy index (BK-tree or SymSpell) | Medium | Low (1-5ms) | High |

Production recommendation: use a pre-built fuzzy correction layer (SymSpell dictionary) as a pre-processor. If the raw prefix `q` has no results in the trie, run SymSpell to generate the most likely correction and re-query the trie with the corrected prefix. Display "Did you mean: X?" with the corrected suggestions. This adds ~2ms and avoids 26x storage explosion from build-time expansion.

**Real-time updates vs. batch updates to the trie**

The Kafka pipeline serves two consumers simultaneously:

1. **Streaming job (Flink, 10-minute windows)**: Identifies terms whose frequency has changed significantly in the current window. Pushes hot patches to the trie and Redis for those specific prefixes only. This is surgical — it does not rebuild the trie. It mutates only the `topK` list at affected trie nodes. Keeps autocomplete fresh for trending events (elections, outages, sports scores) without a full rebuild.

2. **Batch job (Spark, every 1 hour)**: Reads the full event log from S3, recomputes global frequency scores for all 100M prefixes using the complete 30-day window, serializes a new trie, and performs a blue-green deployment: the new trie is loaded on half the nodes while the other half continue serving, then traffic shifts over. This corrects any drift that the streaming patches introduced (streaming patches can transiently over-weight a single 10-minute spike).

---

## Trade-offs Matrix

| Aspect | Choice | Alternative | Trade-off |
|---|---|---|---|
| **Index structure** | Trie with precomputed top-K | B-tree + `LIKE 'prefix%'` | O(L) lookup vs. simpler operations; trie wins above 10K QPS |
| **Trie partitioning** | Shard by first 3-char prefix bucket | Shard by consistent hash of full prefix | Predictable routing vs. even load; 3-char bucket has hot-spot risk on 'S'/'C' buckets |
| **Trie update** | Streaming hot-patch + hourly batch rebuild | Full rebuild every 10 min | Freshness vs. operational complexity; dual-path is harder to operate but necessary at scale |
| **Personalization** | Per-user history overlay at serve time | Per-user trie | 1.5 TB vs. 30 EB; overlay is the only viable option at scale |
| **Fuzzy matching** | Pre-processor (SymSpell) on miss | Build-time index expansion | 2ms extra latency vs. 26x storage; pre-processor is the practical choice |
| **Caching** | Three-tier (in-process + Redis + CDN) | Redis only | Sub-microsecond local hits vs. simpler architecture; three-tier justified at 100K QPS |
| **Freshness window** | 10-minute streaming + 1-hour batch | 1-hour batch only | Trending coverage vs. operational simplicity |

---

## Failure Scenarios

### Scenario 1: Trie Service Node Crash

**Impact:** Requests routed to the crashed node hit a cache miss, then fall through to PostgreSQL. PostgreSQL cold-path latency is 10-50ms vs. <1ms trie lookup. Under 100K QPS, all queries for prefixes owned by that shard suddenly hit PostgreSQL — potential overload.

**Mitigation:**
- Each prefix shard has at least 3 replicas (primary + 2 hot standbys). Orchestrator (Kubernetes) detects crash and reroutes within 5 seconds.
- Redis cache absorbs the traffic during the 5-second failover window — hot prefixes are cached and continue to serve without hitting the trie or PostgreSQL.
- Cold prefixes (cache miss + shard down) degrade to PostgreSQL — acceptable for the <1% long-tail.
- RTO: < 30 seconds for full trie shard restoration.

### Scenario 2: Kafka Consumer Lag (Streaming Pipeline Falls Behind)

**Impact:** Trending queries (e.g., a major news event) do not appear in suggestions for longer than the SLA of 10-15 minutes. The batch rebuild is still running on its 1-hour cycle so corrections will eventually land, but users see stale suggestions during a high-traffic event — exactly when freshness matters most.

**Mitigation:**
- Monitor Kafka consumer lag per partition. Alert at 5M events lag (approximately 5 minutes of backlog at peak).
- Auto-scale Flink taskmanagers on lag alert (Kubernetes HPA on custom Kafka lag metric).
- Priority bypass: implement a thin "trending injection" service that detects terms appearing > 10K times in the last 60 seconds via a sliding window counter, and force-patches those specific terms into the trie without waiting for the full 10-minute Flink window. This keeps viral moments (sports scores, breaking news) current.

### Scenario 3: Redis Cache Cluster Failure

**Impact:** All 100K QPS fall through to the Trie Service directly. The Trie Service was sized for cache-miss traffic (~1K QPS). Sudden 100x overload causes trie service CPU saturation and cascading latency increase. Autocomplete response times climb from 20ms to >500ms — effectively broken from a user perspective.

**Mitigation:**
- In-process LRU on each Query Server (10K entries) continues serving ~40% of traffic with no Redis dependency at all.
- Circuit breaker on the Redis call: if Redis is unresponsive for >100ms, bypass and call Trie Service directly. Trie Service scales horizontally — pre-provision 10x capacity as a cold standby pool that auto-scales on high CPU (takes 90 seconds to provision).
- Redis Cluster is deployed across 3 AZs. A single AZ failure affects only 1/3 of keyspace; remaining 2/3 continues serving, and consistent hashing redistributes requests.
- RTO: 90 seconds to full capacity via auto-scaling; in-process LRU ensures top prefixes never go dark.

---

## Monitoring & Alerts

**Key Metrics:**
```
- Autocomplete P99 latency (end-to-end):     target < 100ms
- Autocomplete P99 latency (service layer):  target < 20ms
- Cache hit rate (Redis):                    target > 99%
- CDN hit rate:                              target > 60%
- Trie lookup P99:                           target < 1ms
- Kafka consumer lag (query-events topic):   alert at 5M events
- Trie shard availability:                   target 100% (shard-level health check)
- Suggestion freshness (trending term lag):  alert if > 15 minutes
```

**Alerts:**
```
- P0: Service-layer P99 > 200ms for 2 min → Page on-call (user-visible degradation)
- P0: Trie shard down with no replica available → Page on-call immediately
- P1: Redis cache hit rate < 95% for 5 min → Ticket (potential Redis capacity issue)
- P1: Kafka consumer lag > 5M events for 10 min → Page streaming team
- P2: Trending term lag > 10 min → Alert (freshness SLA at risk)
- P2: CDN hit rate < 50% → Investigate (possible cache key misconfiguration)
```

---

## Interview Tips

**Common Questions:**

1. **"Why not just use a database LIKE query for autocomplete?"** → Works for small corpora (< 1M terms, < 1K QPS). At Google scale, `LIKE 'prefix%'` with ORDER BY is too slow — it requires a sequential scan or index range scan, then a sort. The trie precomputes the sorted top-K at every node, reducing query-time work to a pointer traversal.

2. **"How does the trie handle trending queries in real time?"** → Two-path pipeline: a Flink streaming job hot-patches individual trie nodes when a prefix's top-K list changes significantly within a 10-minute window, while a Spark batch job does full recalculation hourly. The streaming path handles trending events; the batch path ensures correctness over the full 30-day window.

3. **"A trie for all English queries at Google scale doesn't fit on one machine — how do you partition it?"** → Partition by first 2-3 characters. There are 17,576 possible 3-character prefixes in English. Assign ~5-10 prefix buckets per shard machine. Route queries deterministically: `shard = prefixBucketMap[prefix[:3]]`. Each shard holds a complete sub-trie for its prefix buckets plus all longer extensions.

4. **"How do you personalize without storing a per-user trie?"** → Keep a lightweight personal query history (last 50 queries, 1.5 KB/user) in Redis. At serve time, fetch the global top-5 from the trie, apply a personalization score multiplier to any global suggestion that matches user history, re-sort, and return. The trie stays global and shared; personalization is a stateless post-processing step.

5. **"How do you handle typos?"** → Run a SymSpell correction as a pre-processor on cache/trie miss. If "restarant" returns no results, SymSpell generates "restaurant" as the most likely correction in <1ms, and the system re-queries the trie with the corrected term. Build-time expansion (indexing all 1-edit variants) is impractical due to 26x storage growth.

6. **"What is your caching strategy and why?"** → Three tiers: in-process LRU (10K entries, no network hop) covers ~40% of traffic; Redis Cluster (top-1M prefixes, 500 MB/language) covers ~59%; CDN edge cache (60s TTL) offloads repeated requests before they reach the origin. The key insight is that prefix traffic is extremely concentrated — the 18K prefixes of length 1-3 cover the majority of queries and fit in 9 MB of in-process memory.

**Time Allocation:**
- Requirements: 5 min
- Estimation: 8 min
- API Design: 5 min
- Architecture diagram + data flow: 12 min
- Deep dives (trie structure + partitioning, scoring, caching, personalization): 20 min
- Failure scenarios: 5 min
- Trade-offs discussion: 5 min

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
