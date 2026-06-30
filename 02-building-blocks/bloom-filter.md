> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Bloom Filter — a space-efficient probabilistic data structure that answers "is this element in the set?" with zero false negatives but a tunable false positive rate.
>
> **Key topics:**
> - Core mechanics: bit array of m bits + k hash functions; insert sets k bits; lookup checks if all k bits are 1
> - No false negatives: if a bit is 0, the element is DEFINITELY not in the set
> - False positives: all bits are 1 but the element was never inserted (collision) — rate tunable via m and k
> - Space efficiency: 1B URLs → ~1.2 GB for 1% FPR (vs 50 GB for a hash set)
> - Optimal parameters: m = -n·ln(p) / (ln2)²; k = m/n · ln2; calculable for any n (elements) and p (false positive rate)
> - Counting Bloom Filter: allows deletions by using counters instead of bits
> - Where used: Cassandra (avoid disk reads for non-existent keys), web crawlers (URL deduplication), Chrome Safe Browsing, HBase
>
> **Key takeaway:** Use a Bloom filter whenever you need to reduce expensive DB/disk lookups for elements that definitely don't exist — the memory savings are orders of magnitude.

---
module: 02-building-blocks
status: unread
tags: [02-building-blocks, system-design, building-blocks]
---
# Bloom Filter

> **A space-efficient probabilistic data structure that answers "is this element in the set?" — with zero false negatives and a controllable false positive rate.**

---

## File Mindmap

```
Bloom Filter
├── Why It Exists
│   ├── Problem → 1B URLs in hash set = 50 GB RAM; DB lookup at 5ms × 10K/s = impossible
│   └── Forces → storing full keys requires O(key_size × n) memory; no compression without error
├── Core Mechanics
│   ├── Data structure → bit array of m bits (all 0 initially) + k independent hash functions
│   ├── Insert element
│   │   ├── Compute h1(x) % m, h2(x) % m … hk(x) % m
│   │   └── Set those k bit positions to 1
│   └── Lookup element
│       ├── Compute same k positions
│       ├── Any bit = 0? → DEFINITELY NOT in set (no false negatives)
│       └── All bits = 1? → PROBABLY in set (false positive possible)
├── Key Properties
│   ├── False negatives → IMPOSSIBLE; inserted element always sets its bits
│   ├── False positives → POSSIBLE; collision from other elements' bits
│   ├── Deletion → NOT supported in basic BF; clearing bits breaks other elements
│   └── Space → O(m) bits, independent of element size
├── Mathematical Sizing
│   ├── m = bit array size, n = insertions, k = hash functions
│   ├── Optimal k = (m/n) × ln(2) ≈ 0.693 × (m/n)
│   ├── False positive rate p = (1 - e^(-kn/m))^k
│   └── Practical: 1% FP rate → ~9.6 bits/element, ~7 hash functions
│       └── 1B URLs at 1% FP → 1.2 GB (vs 50 GB hash set = 40× savings)
├── Variants
│   └── Counting Bloom Filter
│       ├── Replace bits with counters (4-bit typical)
│       ├── Add → increment k counters; Remove → decrement k counters
│       ├── Supports deletion; counter overflow risk (negligible at 4-bit)
│       └── Use case → cache TTL expiry, dynamic blocklists
├── Real-World Use Cases
│   ├── Web Crawler URL deduplication → 1B URLs, 1.2 GB filter, µs lookups, 1% skip rate acceptable
│   ├── Cassandra SSTable pre-filter → skip disk reads for keys definitely not in SSTable
│   ├── Redis cache stampede prevention → gate cache+DB lookups for never-set keys
│   ├── Bitcoin SPV wallet → send BF of wallet addresses; full node filters transactions → 100× bandwidth saving
│   └── Chrome Safe Browsing → local BF of 5B malicious URLs (~500 MB); API call only on positive
├── Implementation
│   ├── Java (Guava) → BloomFilter.create(Funnels.stringFunnel(), expectedSize, fpRate)
│   └── Redis (RedisBloom) → BF.RESERVE / BF.ADD / BF.EXISTS / BF.MADD / BF.MEXISTS
│       └── Auto-expand → BF.RESERVE … EXPANSION 2 (doubles sub-filter on overflow)
├── Trade-offs
│   ├── Pros → massive memory savings; O(k) time; no false negatives
│   └── Cons → false positives; no deletion (basic); not useful if FP rate unacceptable
└── Interview Angles
    ├── "Why no false negatives?" → inserted element sets its bits; they can't be unset by others
    ├── "How do you tune FP rate?" → increase m (larger array) or decrease n (fewer inserts per filter)
    ├── "When would you NOT use a BF?" → when false positives cause irreversible harm (e.g. payment dedup)
    └── Follow-up: "What's the difference between a BF and a hash set?" → BF trades correctness for space
```

---

## Why Bloom Filters Exist

**Question**: A web crawler has already visited 1 billion URLs. Before fetching a new URL, it must check whether it's been visited before. Option A: store all 1 billion URLs in a hash set in memory. At 50 bytes per URL average, that's 50 GB of RAM just for deduplication. A machine with 64 GB of RAM has almost no headroom left. Option B: store them in a database and query on each URL. At 5ms per lookup and 10,000 URLs/sec to check, that's 50 seconds of DB query time per second — impossible. Is there a way to answer "have I seen this URL?" in microseconds, using 1–2 GB instead of 50 GB, if you can tolerate occasionally saying "yes" when the answer is actually "no"?

**Physical constraint**: A hash set mapping URL strings to their presence requires storing the strings themselves (or a full-length hash with zero collision probability, which requires as many bits as the string). At 50 bytes per URL, 1 billion URLs is irreducibly 50 GB. RAM at ~$5/GB makes this an expensive dedicated machine. The question is: can you trade a small, controlled probability of error for a 40× reduction in memory?

**Minimal solution**: Instead of storing the full URL, map each URL to `k` bit positions in a compact bit array using `k` hash functions. Set those bits to 1 on insert. On lookup, check those same `k` bit positions — if any is 0, the URL was definitely never inserted. If all are 1, it was probably inserted (but a false positive is possible: those bits may have been set by other URLs). The bit array for 1 billion URLs with 1% false positive rate is ~1.2 GB — a 40× reduction.

**Production generalization**: Bloom filters are a lookup pre-filter: they eliminate the "definitely not present" case cheaply, forwarding only "probably present" cases to the expensive authoritative check (database, disk, network). The false positive rate is mathematically controlled by the bit array size and number of hash functions, and can be tuned to whatever the use case tolerates. Used in Cassandra (skip SSTables that don't contain a key), Google Chrome (malicious URL detection), and cache stampede prevention (reject lookups for keys that have never been set).

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
