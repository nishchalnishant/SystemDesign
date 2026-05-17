# Bloom Filter

> **A space-efficient probabilistic data structure that answers "is this element in the set?" — with zero false negatives and a controllable false positive rate.**

---

## What Is a Bloom Filter?

A Bloom filter is a bit array of `m` bits, all initially set to `0`, combined with `k` independent hash functions. It can tell you definitively when an element is **NOT** in a set, and probabilistically when it **might be**.

> **Analogy**: A bouncer at a nightclub with a checklist. The checklist is an approximate summary — it says "this person's name has been marked as on the list" (might be a collision). The bouncer uses the checklist for quick rejection (not on list → definitely not allowed in). For anyone on the checklist, they do a secondary full verification. The checklist is small enough to fit in the bouncer's pocket (memory-efficient) — unlike the full guest database.

---

## How It Works

### Insertion

```
Element: "alice@example.com"

Hash functions:
  h1("alice@example.com") % m = 3
  h2("alice@example.com") % m = 14
  h3("alice@example.com") % m = 27

Set bits 3, 14, 27 to 1:
  Before: [0,0,0,0,0,0,...,0]
  After:  [0,0,0,1,0,0,...,0,...,1,...,0,...,1,...]
                  ^              ^            ^
                  3              14           27
```

### Lookup

```
Query: "bob@example.com"

h1("bob") % m = 3   → bit 3 = 1 (set by alice!)
h2("bob") % m = 7   → bit 7 = 0
h3("bob") % m = 27  → bit 27 = 1 (set by alice!)

Any bit = 0? → YES (bit 7 = 0) → DEFINITELY NOT IN SET

Query: "charlie@example.com"

h1("charlie") % m = 3  → bit 3 = 1
h2("charlie") % m = 14 → bit 14 = 1
h3("charlie") % m = 27 → bit 27 = 1

All bits = 1? → YES → PROBABLY IN SET (false positive possible!)
But charlie was never inserted — this is a false positive.
```

### Key Properties

```
False negatives: IMPOSSIBLE
  If an element was inserted, all its bits are 1.
  Checking will always find all 1s → always returns "probably in set."

False positives: POSSIBLE (but controllable)
  An element not in the set may have all its hash positions accidentally set by other elements.
  False positive rate controlled by:
    m (bit array size): larger → lower FP rate
    k (number of hash functions): more → lower FP rate (up to optimal k)
    n (number of inserted elements): more elements → higher FP rate

Deletion: NOT SUPPORTED in basic Bloom filter
  Clearing bits could remove bits shared with other elements.
  Solution: Counting Bloom filter (use counters instead of bits, support decrement)
```

---

## Mathematical Properties

```
Given:
  m = bit array size
  n = number of expected insertions
  k = number of hash functions

Optimal k: k = (m/n) × ln(2) ≈ 0.693 × (m/n)

False positive probability: p = (1 - e^(-kn/m))^k

Practical sizing:
  Target p = 1% false positive rate:
    m = n × log2(1/p) / ln(2) ≈ n × 9.6 bits per element
    k = ln(1/p) / ln(2) ≈ 6.64 ≈ 7 hash functions

  For 1 billion URLs at 1% FP:
    m = 1B × 9.6 = 9.6 billion bits = 1.2 GB
    (Compare: storing 1B URLs as 50-byte strings = 50 GB)
    Savings: 40× compression!
```

---

## Implementation

```java
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import java.nio.charset.StandardCharsets;

public class UrlBloomFilter {
    private final BloomFilter<String> filter;

    public UrlBloomFilter(long expectedUrls, double falsePositiveRate) {
        // Guava BloomFilter handles sizing and hashing automatically
        this.filter = BloomFilter.create(
            Funnels.stringFunnel(StandardCharsets.UTF_8),
            expectedUrls,         // 1_000_000_000L for 1 billion
            falsePositiveRate     // 0.01 for 1%
        );
    }

    public void add(String url) {
        filter.put(url);
    }

    public boolean mightContain(String url) {
        return filter.mightContain(url);  // False = definitely not seen, True = probably seen
    }

    // Memory usage
    public long approximateElementCount() {
        return filter.approximateElementCount();
    }
}

// Usage in web crawler
public class WebCrawler {
    private final UrlBloomFilter seenUrls = new UrlBloomFilter(1_000_000_000L, 0.01);

    public void crawl(String url) {
        if (seenUrls.mightContain(url)) {
            return;  // Probably already crawled — skip
            // 1% of time, this is a false positive → we skip a URL we haven't crawled
            // Acceptable trade-off for 40× memory savings
        }
        seenUrls.add(url);
        // Actually crawl...
    }
}
```

---

## Redis Bloom Filter (Production Use)

```bash
# RedisBloom module — persistent, distributed, handles large scale

# Create Bloom filter: 1M elements, 0.1% false positive rate
BF.RESERVE dedup_filter 0.001 1000000

# Insert elements
BF.ADD dedup_filter "user_event_12345_ts_1700000000"

# Check (returns 0 = definitely not present, 1 = probably present)
BF.EXISTS dedup_filter "user_event_12345_ts_1700000000"

# Batch operations (more efficient)
BF.MADD dedup_filter "event_1" "event_2" "event_3"
BF.MEXISTS dedup_filter "event_1" "event_4"
# Returns: [1, 0]  (event_1 present, event_4 not present)

# Scale-out: Redis Bloom filter automatically expands when needed
BF.RESERVE auto_expand_filter 0.01 10000 EXPANSION 2
# After 10K elements, creates a new sub-filter 2× the size
```

---

## Real-World Use Cases

### 1. Web Crawler URL Deduplication (Google/Bing)

```
Problem: Crawling the web generates billions of URLs. Before fetching a URL,
         check if it's already been crawled.
         
Without Bloom filter: Store all crawled URLs in DB → 50 bytes × 1B = 50 GB
                       Each lookup: DB query (~5ms)
                       
With Bloom filter: 1.2 GB in RAM (40× smaller)
                   Each lookup: microseconds (bitwise operations)
                   
Trade-off: 1% of URLs may be skipped (false positives) — acceptable for crawlers.
```

### 2. Cassandra Read Optimization

```
Cassandra uses Bloom filters per SSTable (sorted-string table file on disk).
Before reading an SSTable (slow disk I/O), check Bloom filter:
  - Returns 0: skip this SSTable (element definitely not here) → saves I/O
  - Returns 1: read the SSTable (probably here, or false positive)

Without Bloom filters: Read all SSTables for every key lookup (very slow)
With Bloom filters: Only read SSTables that likely contain the key
```

### 3. Redis Cache Stampede Prevention

```
Problem: Millions of requests for a non-existent key hit the cache, then hit the DB.
         (Cache layer doesn't protect against "null" cache misses)

Solution: Bloom filter as a "pre-gate" to the cache
  On every key creation: BF.ADD key
  On lookup: 
    if BF.EXISTS(key) == 0: return null immediately (skip cache + DB)
    else: proceed to cache/DB lookup

Use case: User profile lookup for user IDs that don't exist (deleted accounts, invalid IDs)
```

### 4. Blockchain / Merkle Proof

```
Bitcoin uses Bloom filters in SPV (Simplified Payment Verification) clients.
Mobile wallet wants to know if it received any transactions in a block.
Instead of downloading the full block (~1MB), client sends a Bloom filter
containing its wallet addresses.
Full node filters transactions through the Bloom filter and returns only matches.
Bandwidth savings: 100× for mobile clients.
```

### 5. Email Spam / Malware URL Blocklist

```
Chrome's Safe Browsing:
  Google maintains a Bloom filter of ~5 billion known malicious URLs
  Filter downloaded to your browser (~500MB compressed)
  Every URL you visit: checked against local Bloom filter instantly (no network call)
  If positive: verify with Google API (confirm it's not a false positive)
  Result: Privacy (URLs not sent to Google for every visit) + speed
```

---

## Counting Bloom Filter (Supports Deletion)

```
Replace each bit with a counter (typically 4-bit):
  Add element: increment counters at k hash positions
  Remove element: decrement counters at k hash positions
  Check: if any counter = 0 → definitely not present

Counter overflow risk: If counter reaches max (15 for 4-bit), increments are capped.
Use 4-bit counters for typical workloads (P(overflow) < 1 in 10^15 insertions).

Use when: Cache TTL expiry (need to delete expired entries from filter)
          Dynamic blocklists (need to un-block entries)
```

---

## Quick Reference

| Property | Value |
|----------|-------|
| Space | ~9.6 bits per element at 1% FP rate |
| Time: Insert | O(k) — k hash function evaluations |
| Time: Lookup | O(k) — k hash function evaluations |
| False negatives | Impossible |
| False positives | Controllable (typically 0.1% - 1%) |
| Deletion | Not supported (use Counting BF) |
| Best for | Membership queries on large sets where false negatives are unacceptable |
