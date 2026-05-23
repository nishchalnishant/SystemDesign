---
module: 05-hld-problems
topic: Easy
status: unread
tags: [05-hld-problems, system-design, easy]
---
# Design a Web Crawler

> **Difficulty**: Easy
> **Topics**: Distributed Systems, BFS, Bloom Filters, Consistent Hashing, DNS
> **Time**: 45-60 minutes
> **Companies**: Google, Microsoft, Amazon, Cloudflare, Common Crawl

---

## Problem Mindmap

```
Web Crawler
├── Problem Constraints
│   ├── Scale → 10K pages/sec, 5B URLs total, 550TB per crawl cycle (100KB avg page)
│   ├── Latency → throughput-optimized not latency-sensitive; politeness = 1 req/domain/sec
│   └── Core hardness → URL deduplication at 5B scale + politeness enforcement + near-duplicate content detection
├── Architecture Derivation
│   ├── Step 1 → Single-threaded BFS → 1 req/sec = 86K pages/day; need 10K pages/sec = 10K parallel workers
│   ├── Step 2 → Distributed workers with shared URL queue → queue becomes bottleneck; no dedup across workers
│   ├── Step 3 → Bloom filter for seen URLs (6GB at 1% FP for 5B URLs, 10 bits/URL) → constant-time dedup
│   └── Step 4 → Two-level frontier: priority queue (importance score) + per-domain politeness queue (delay 1s)
├── Core Components
│   ├── URL Frontier → priority queue ranked by PageRank/freshness; per-domain back-queue enforces politeness
│   ├── Bloom filter → 6GB for 5B URLs at 1% FPR; in-memory on frontier servers; backup in Redis
│   ├── Fetcher pool → 1000 async worker nodes; DNS cache per worker; respect robots.txt
│   ├── Parser → extracts outlinks + content; pushes new URLs to frontier; content to storage
│   ├── Content dedup → SHA-256 for exact dedup; SimHash (64-bit fingerprint, Hamming ≤3) for near-dedup
│   └── Distributed store → Cassandra for URL metadata (url, last_crawled, checksum, next_crawl)
├── Data Model
│   ├── url_metadata → (url_hash PK, url, last_crawled, content_hash, status, priority, next_crawl_at)
│   └── content_store → S3 for raw HTML (keyed by SHA-256); Elasticsearch for indexed/searchable content
├── APIs
│   ├── POST /crawl/seed → {seed_urls: [...]} → kickstarts new crawl job
│   └── GET /crawl/status/{job_id} → {pages_crawled, queue_depth, errors, estimated_completion}
├── Critical Trade-offs
│   ├── BFS vs Priority → Priority chosen; freshness + importance score avoids crawling low-value pages first
│   ├── Bloom filter FP rate → 1% FPR = 6GB; 0.1% FPR = 9GB; 1% acceptable (miss 1 in 100 new URLs = re-crawl later)
│   └── SimHash threshold → Hamming ≤3 of 64 bits = near-duplicate; threshold tunable per content type
├── Failure Scenarios
│   ├── Fetcher crash → URL re-queued after TTL; idempotent crawl (content hash dedup prevents duplicate storage)
│   ├── Bloom filter reset → rebuild from Cassandra url_metadata on restart; brief duplication window acceptable
│   └── DNS amplification → local DNS cache per fetcher (TTL 600s); rate limit outgoing DNS queries per domain
└── Interview Angles
    ├── Google → "Design Googlebot" → politeness + priority frontier + SimHash + distributed fetch at scale
    ├── Common Crawl → "How do you crawl 5B pages per month?" → 10K workers × 86400s × 1 req/sec = 864M/day = 26B/month
    └── Follow-up → "How do you handle spider traps?" → URL depth limit (max 10 hops) + detect infinite loop patterns
```

---

## What Breaks Without This System?

A search engine needs to index the web. Without a crawler, its index is empty — no queries return results. More specifically: without a designed crawler, you write a naive recursive scraper. It fetches a page, extracts links, recursively fetches those. Within minutes it has visited `example.com` 400 times because it discovered the same URL via 400 different paths. It hammers `wikipedia.org` with 10K requests/second, gets IP-banned, and can never index Wikipedia again. It follows an infinite calendar URL (`/events?date=2026-01-01`, `/events?date=2026-01-02`...) for weeks without making progress on other sites. It downloads 2TB of near-duplicate content because it didn't canonicalize URLs (trailing slash, `http` vs `https`, query parameter order).

The technical failures without a designed system:
- No deduplication → infinite loops, wasted bandwidth, re-crawling already-indexed pages.
- No politeness controls → IP bans from target servers.
- No priority → the crawler never decides what to crawl next; it's DFS into a trap.
- No distributed coordination → one machine can't crawl 5B URLs at 10K pages/sec.

---

## Derive the Architecture

**Step 1 — Single-threaded, naive**
`fetch(seed_url)` → parse HTML → extract links → `fetch(each link)`. Breaks immediately: cycles, no deduplication, single-threaded throughput is ~1 page/sec.

**Step 2 — What constraint forces distributed crawling?**
Target: 10K pages/sec, 550TB per full crawl. A single server with a 1Gbps NIC maxes out at ~125MB/sec download. At average 100KB/page that's 1,250 pages/sec max, ignoring DNS, parsing, and storage overhead. You need 5,000+ crawler workers. Now you have a coordination problem: how do 5,000 workers avoid crawling the same URL?

**Step 3 — URL deduplication at 5B URLs**
Option A: DB lookup (`SELECT 1 FROM visited WHERE url = ?`). 5B rows, URL up to 2KB each → 10TB table, 50ms per lookup, 500K lookups/sec = unsustainable.
Option B: Bloom filter. A 6GB in-memory Bloom filter with 3 hash functions gives <0.1% false positive rate for 5B URLs. Lookup is O(3) hash operations — nanoseconds. False positives mean we occasionally skip a valid unvisited URL — acceptable. No false negatives (never crawl a URL twice).

**Step 4 — URL Frontier (the scheduling backbone)**
A FIFO queue is not enough — it would drain `example.com`'s 1M internal links all at once, violating politeness (1 request per domain per second is the standard limit).

Two-level structure:
- Front queues: N priority queues (importance/PageRank score determines which queue a URL enters).
- Back queues: one FIFO queue per domain. A URL for `cnn.com` goes to the `cnn.com` back queue. Crawlers pull from back queues, enforcing one request per domain per second with a per-domain timer.

**Step 5 — Distributed coordination across 5,000 workers**
Use consistent hashing: hash(domain) → worker ID. All URLs for `cnn.com` always go to worker #42, regardless of which worker discovered the URL. This eliminates coordination: no shared state needed for per-domain politeness — each worker owns a domain partition exclusively.

**Step 6 — What remains as hard problems**
- DNS: 10K pages/sec = 10K DNS lookups/sec. Default OS DNS is synchronous and ~100ms. Build a local async DNS cache with TTL respect — reuse resolved IPs for 5 minutes.
- robots.txt: cache per domain, TTL 1 day. Never fetch a page before fetching and honoring its robots.txt.
- Near-duplicate content: SimHash of page content; pages with Hamming distance < 3 are near-duplicates. Discard or merge.
- Spider traps: URL length limit (>500 chars: skip), max URLs per domain per crawl, URL pattern detection (infinite calendar URLs).

---

## Real-Life Analogy

Imagine a city's postal sorting facility tasked with cataloging every address in the country.

Letters arrive continuously (URLs discovered from crawled pages). A triage team sorts each envelope by neighborhood and urgency (the URL Frontier — priority queue by domain rank, back queue by domain to enforce politeness). Before a letter gets sent to a carrier, a clerk checks a massive stamp ledger to see if that address was already visited last week (the Bloom filter for deduplication). Carriers respect "No Junk Mail" signs on mailboxes (robots.txt). Once a carrier reaches an address, they read the contents and extract every new address mentioned inside (link extraction). Some streets loop back to themselves — a calendar that generates a unique URL for every possible date, stretching to infinity (spider traps). Experienced carriers recognize these loops and stop walking them.

The facility runs 24/7, with hundreds of carriers operating simultaneously across different neighborhoods (distributed workers partitioned by domain via consistent hashing). Without coordination, two carriers would show up at the same mailbox — wasted work. With coordination, every address gets visited exactly once, at an appropriate frequency.

---

## Why This Is Hard

1. **Scale of the web**: The indexed web has over 5 billion pages; the full crawlable web is estimated at 40-50 billion pages. A single-machine crawler cannot process this volume. Distributed coordination is required from the start, not bolted on later.
2. **Politeness vs. throughput**: Hammering a single domain server with thousands of requests per second is indistinguishable from a DDoS attack. You must enforce per-domain crawl delays (e.g., 1 request every 3 seconds per host), which caps your throughput per domain and forces prioritization across thousands of domains simultaneously.
3. **Duplicate content at scale**: The web is roughly 30-40% duplicate or near-duplicate content (mirror sites, syndicated articles, canonical URL variants). Without deduplication at both the URL level (exact seen-set) and the content level (near-duplicate detection), you waste bandwidth, storage, and index slots.
4. **Spider traps**: Websites inadvertently (or deliberately) generate infinite URLs — a calendar page with "next day" links, session IDs appended to every URL, infinite pagination. Googlebot has encountered paths with 100+ query parameters that lead nowhere. Detecting and escaping traps requires URL normalization, depth limits, and content fingerprinting.
5. **DNS resolution bottleneck**: At scale, DNS lookups can dominate crawl latency. A fresh DNS lookup takes 20-120ms; at 10K URLs/sec this becomes the critical path. Crawlers must maintain their own distributed DNS cache with tunable TTLs, bypassing the OS resolver entirely.
6. **Dynamic and JavaScript-rendered content**: Over 60% of modern websites rely on JavaScript to render content. A simple HTTP fetcher sees an empty shell. Crawling rendered content requires headless browsers (Puppeteer, Chromium), which are 50-100x more expensive per page than raw HTTP fetches — requiring a separate, smaller rendering tier.

---

## Requirements Gathering

### Functional Requirements

**Must-Have:**
1. Given a set of seed URLs, crawl all reachable URLs by following links
2. Store the raw HTML content of each page
3. Extract and deduplicate URLs found on crawled pages
4. Respect robots.txt rules for each domain
5. Support re-crawling pages periodically to detect changes

**Nice-to-Have:**
6. Prioritize crawling by domain authority (PageRank approximation)
7. Near-duplicate content detection to avoid storing redundant pages
8. Sitemap.xml support for faster initial discovery
9. JavaScript rendering for dynamic pages
10. Real-time crawl status and metrics dashboard

### Non-Functional Requirements

**Performance:**
- Throughput: 10,000 pages/second (864M pages/day)
- Crawl latency: pages queued within 24 hours of discovery for high-priority domains
- DNS cache hit rate: >99% (critical — each miss adds 50-120ms)

**Scale:**
- 5 billion URLs in the URL seen-set
- 10 PB of raw HTML content over 5 years
- 100K distinct domains in the politeness queue at any time

**Politeness:**
- Minimum 1 second between requests to the same host
- Respect Crawl-delay directive in robots.txt
- Identify crawler with User-Agent: Googlebot-compatible string

**Reliability:**
- No URL permanently lost from the frontier
- Durable storage for crawled content (3× replication)
- At-least-once crawl guarantee; idempotent content storage

**Availability:**
- Crawl workers: stateless, replaceable; tolerate 10% worker failure with zero data loss
- Frontier service: 99.9% uptime (core coordinator)

---

## Capacity Estimation

### Traffic Estimates

```
Given:
- 5 billion pages to crawl
- Target: full recrawl every 30 days
- Effective crawl rate needed: 5B / (30 × 86,400) = ~1,930 pages/sec
- Target with 5× safety margin: 10,000 pages/sec

Workers needed:
- Average page fetch time: 500ms (network + parsing)
- Pages/worker/sec: 1 / 0.5 = 2 pages/sec
- Workers needed: 10,000 / 2 = 5,000 crawler workers
- Assume 100-core machines, 50 threads/machine: 50 machines
```

### Storage Estimates

```
Per page:
├─ Raw HTML content (avg): 100 KB
├─ Metadata (URL, timestamp, status, content hash): 500 bytes
├─ Extracted links (avg 50 links × 200 bytes): 10 KB
└─ Total: ~110 KB per page

Full crawl (5B pages):
5B × 110 KB = 550 TB per crawl cycle

Re-crawl frequency: monthly
Retention: 5 years (60 snapshots)
Dedup saves: ~35% (duplicate content)
Net storage: 550 TB × 60 × 0.65 = 21.45 PB

URL seen-set (Bloom filter):
5B URLs × 10 bits/URL (Bloom filter) = 6.25 GB in RAM
With 1% false positive rate: acceptable

With 3× replication: ~65 PB object storage total
Cold storage cost (S3 Glacier): 65 PB × $0.004/GB/month = $260K/month
```

### Bandwidth Estimates

```
Inbound (fetching pages):
10,000 pages/sec × 100 KB = 1 GB/sec inbound bandwidth

DNS lookups (with 99% cache hit):
10,000 pages/sec × 1% miss = 100 DNS lookups/sec (manageable)
Without DNS cache: 10,000 lookups/sec at 100ms each = impossible
```

---

## API Design

**1. Submit Seed URLs**
```http
POST /api/v1/frontier/seeds
Content-Type: application/json

Request:
{
  "urls": [
    "https://example.com",
    "https://news.ycombinator.com"
  ],
  "priority": "high"   // high | normal | low
}

Response: 202 Accepted
{
  "enqueued": 2,
  "job_id": "seed-20260513-001"
}
```

**2. Get Crawl Status for a Domain**
```http
GET /api/v1/domains/{host}/status

Response: 200 OK
{
  "host": "example.com",
  "pages_crawled": 14823,
  "last_crawled_at": "2026-05-13T08:00:00Z",
  "crawl_delay_ms": 3000,
  "robots_txt_cached": true,
  "blocked_paths": ["/admin", "/private"]
}
```

**3. Get Crawled Content for a URL**
```http
GET /api/v1/pages?url=https://example.com/page1

Response: 200 OK
{
  "url": "https://example.com/page1",
  "canonical_url": "https://example.com/page1",
  "content_hash": "sha256:abc123...",
  "crawled_at": "2026-05-13T08:01:00Z",
  "http_status": 200,
  "content_type": "text/html",
  "storage_path": "s3://crawl-bucket/2026/05/13/abc123.html.gz"
}
```

**4. Force Re-Crawl a URL**
```http
POST /api/v1/pages/recrawl
Content-Type: application/json

Request:
{
  "url": "https://example.com/page1",
  "priority": "high"
}

Response: 202 Accepted
{
  "url": "https://example.com/page1",
  "estimated_crawl_time": "2026-05-13T08:05:00Z"
}
```

---

## High-Level Architecture

```
Seed URLs
    │
    ▼
┌─────────────────────────────────────────────────────────┐
│                    URL Frontier Service                  │
│  ┌─────────────────┐    ┌──────────────────────────┐    │
│  │  Priority Queue  │    │  Back Queue (per domain)  │    │
│  │  (domain rank)   │───▶│  politeness enforcer     │    │
│  └─────────────────┘    └──────────────────────────┘    │
└────────────────────────────┬────────────────────────────┘
                             │ Next URL to fetch
                             ▼
┌─────────────────────────────────────────────────────────┐
│              Crawler Worker Pool (5,000 workers)         │
│                                                          │
│   [Worker 1]   [Worker 2]   [Worker 3]   [Worker N]     │
│   domain A     domain B     domain C     domain X        │
│       │             │            │            │          │
│       └─────────────┴────────────┴────────────┘         │
│                          │                               │
│              Consistent hash(domain) → Worker            │
└──────────────────────────┬──────────────────────────────┘
                           │
          ┌────────────────┼─────────────────┐
          │                │                 │
          ▼                ▼                 ▼
┌──────────────┐  ┌──────────────┐  ┌──────────────────┐
│ DNS Resolver │  │ robots.txt   │  │  HTTP Fetcher    │
│ Cache Pool   │  │ Cache (Redis)│  │  (raw content)   │
└──────────────┘  └──────────────┘  └────────┬─────────┘
                                             │
                              ┌──────────────┼──────────────┐
                              │              │              │
                              ▼              ▼              ▼
                    ┌──────────────┐ ┌────────────┐ ┌───────────────┐
                    │ URL Extractor│ │ URL Dedup  │ │ Content Store │
                    │ (HTML parser)│ │ (Bloom     │ │ (S3/GCS)      │
                    │ + normalizer │ │  Filter)   │ │ + SimHash DB  │
                    └──────┬───────┘ └─────┬──────┘ └───────────────┘
                           │               │
                           │ New URLs      │ Not seen before
                           └───────────────┘
                                   │
                                   ▼
                          URL Frontier Service
                           (feedback loop)
```

---

## Data Flow

### Write Flow (Crawling a Page)

```
1. Frontier service dequeues next URL for a domain (respecting crawl delay)
2. Crawler worker assigned to that domain (via consistent hash) receives the URL
3. Worker checks robots.txt cache (Redis):
   a. Cache HIT → apply rules immediately
   b. Cache MISS → fetch /robots.txt, parse, cache for 24 hours
4. If robots.txt disallows path → skip URL, mark as "disallowed" in metadata DB
5. Worker resolves DNS via local resolver cache:
   a. Cache HIT → proceed
   b. Cache MISS → forward to DNS resolver pool (5 dedicated resolvers), cache result
6. Worker issues HTTP GET with crawler User-Agent, follows up to 3 redirects
7. Response body is:
   a. Content-hashed (SHA-256)
   b. Checked against content dedup store: if hash seen → skip storage, update metadata
   c. Stored compressed to object storage (S3): s3://bucket/{date}/{hash}.html.gz
8. URL extractor parses HTML, extracts all <a href> links
9. Each extracted URL goes through normalization pipeline:
   - Lowercase scheme+host
   - Remove default ports (:80, :443)
   - Remove tracking params (utm_*, fbclid, etc.)
   - Resolve relative URLs against base URL
   - Decode percent-encoding, re-encode consistently
10. Normalized URL checked against Bloom filter:
    a. "Definitely not seen" → add to Bloom filter, enqueue in URL Frontier
    b. "Possibly seen" → check metadata DB (handles false positives)
11. Metadata DB updated: URL, crawl timestamp, HTTP status, content hash, storage path
```

### Re-Crawl Flow (Periodic Freshness)

```
1. Scheduler scans metadata DB for URLs where last_crawled_at < freshness threshold
2. Freshness threshold varies by domain type:
   - News sites: recrawl every 4 hours
   - Static pages: recrawl every 30 days
3. Selected URLs enqueued in Frontier with appropriate priority
4. Crawl proceeds as write flow above
5. If content hash matches previous crawl → no new storage; update crawl timestamp only
```

---

## Deep Dives

### 1. URL Frontier: Priority Queue + Back Queue

The URL Frontier is the brain of the crawler. It answers one question every millisecond: "Which URL should be fetched next?" This is harder than it sounds because two competing constraints must be balanced simultaneously — crawl importance (some pages matter more) and crawl politeness (no domain gets hammered).

The solution is a two-tier queue architecture:

**Front Queue (Priority):** A set of priority-ordered queues, one per priority level (e.g., 3 levels: high/medium/low based on PageRank score or domain authority). A URL from `nytimes.com` ranks higher than a URL from an obscure personal blog. The front queue determines *which URL to consider next*.

**Back Queue (Politeness):** One queue per domain currently being crawled. The front queue's output feeds into the back queue for the URL's domain. A worker assigned to `nytimes.com` only picks from `nytimes.com`'s back queue and enforces a minimum delay between picks (e.g., 3 seconds, or whatever robots.txt `Crawl-delay` specifies).

```java
public class URLFrontier {
    // One priority queue per level: 0=high, 1=medium, 2=low
    private final PriorityQueue<CrawlURL>[] frontQueues;

    // One queue per domain being actively crawled
    private final Map<String, Queue<CrawlURL>> backQueues;

    // Earliest time a domain can be fetched again
    private final Map<String, Long> domainNextFetchTime;

    public CrawlURL nextURL(String workerDomain) {
        Queue<CrawlURL> domainQueue = backQueues.get(workerDomain);
        if (domainQueue == null || domainQueue.isEmpty()) {
            return null;
        }

        long now = System.currentTimeMillis();
        long nextAllowed = domainNextFetchTime.getOrDefault(workerDomain, 0L);

        if (now < nextAllowed) {
            return null;  // Too soon for this domain
        }

        CrawlURL url = domainQueue.poll();
        int crawlDelayMs = url.getCrawlDelayMs();  // From robots.txt
        domainNextFetchTime.put(workerDomain, now + crawlDelayMs);
        return url;
    }

    public void enqueue(CrawlURL url) {
        int priority = computePriority(url);  // Based on PageRank, freshness, etc.
        frontQueues[priority].offer(url);
        // Router moves URLs from front queues to domain back queues periodically
    }
}
```

**BFS vs. DFS — Why BFS Wins:**

A depth-first strategy would follow one link chain deep into a site before backtracking. This is catastrophic for two reasons. First, it violates politeness — DFS on a single domain means sending hundreds of sequential requests to the same server with minimal pause. Second, it delays discovery of important pages on other domains. A single site's internal pages are lower priority than discovering entirely new high-authority domains.

BFS explores the web level by level: all links found on seed pages first, then all links found on those pages, and so on. This naturally distributes load across many domains and ensures high-PageRank pages (which tend to be linked from many sources, discovered early in BFS) are crawled before obscure deep pages. Googlebot uses BFS with a priority overlay — roughly BFS order, but with domain authority used to jump important sites ahead.

### 2. Distributed Crawling with Consistent Hashing

The critical correctness requirement in distributed crawling: **only one worker should crawl any given domain at a time**. Without this, two workers fetch the same page simultaneously, doubling bandwidth cost and violating politeness rules.

The solution is consistent hashing on the domain name.

```java
public class CrawlerRouter {
    private final ConsistentHashRing<CrawlerWorker> ring;

    public CrawlerRouter(List<CrawlerWorker> workers) {
        // Each worker added to ring with 150 virtual nodes (for even distribution)
        this.ring = new ConsistentHashRing<>(workers, 150);
    }

    public CrawlerWorker workerForDomain(String domain) {
        // Same domain always maps to same worker
        return ring.getNode(domain);
    }

    // When a worker fails: reassign its domains to its successor on the ring
    public void workerFailed(CrawlerWorker failedWorker) {
        ring.removeNode(failedWorker);
        // All domains previously handled by failedWorker now route to successor
        // URLs in failedWorker's in-memory back queues are re-fetched from durable frontier
    }
}
```

When a worker fails, consistent hashing ensures that only the failed worker's domains are redistributed — not the entire mapping. This minimizes the disruption window and makes recovery fast.

**DNS Caching — The Hidden Bottleneck:**

At 10K pages/sec, with an average of 5 distinct domains per second, you'd need 5 DNS lookups/sec — manageable. But with cold start or cache expiry, you'd need 10K lookups/sec. Each lookup takes 50-120ms. That's your entire crawl budget spent on DNS.

The fix is a dedicated DNS resolver pool — 5-10 servers running custom DNS resolvers (e.g., Unbound) that maintain an in-memory cache with TTLs tuned for crawl workloads:

```java
public class CrawlerDNSCache {
    // TTL for crawl purposes (ignore very short TTLs from origin DNS)
    private static final int MIN_CACHE_TTL_SECONDS = 300;  // 5 minutes
    private static final int MAX_CACHE_TTL_SECONDS = 3600; // 1 hour

    private final LoadingCache<String, InetAddress> cache = CacheBuilder.newBuilder()
        .expireAfterWrite(MAX_CACHE_TTL_SECONDS, TimeUnit.SECONDS)
        .maximumSize(1_000_000)  // 1M domains in cache
        .build(CacheLoader.from(this::resolve));

    private InetAddress resolve(String host) {
        // Query dedicated DNS resolver pool, not OS resolver
        return dnsResolverPool.resolve(host);
    }

    public InetAddress lookup(String host) {
        return cache.getUnchecked(host);  // Returns cached or fetches
    }
}
```

With a 1M-entry DNS cache and a 5-minute minimum TTL, 99%+ of lookups are served from memory at microsecond latency.

### 3. Politeness Policy and robots.txt

robots.txt is a plain-text file at `{domain}/robots.txt` that specifies which URL paths a crawler is permitted or forbidden to access, and optionally a `Crawl-delay` in seconds.

```
# Example robots.txt
User-agent: Googlebot
Disallow: /admin/
Disallow: /private/
Crawl-delay: 3

User-agent: *
Disallow: /checkout/
Disallow: /api/
```

Compliance is not optional — violating robots.txt damages trust and can get your crawler's IP range blocked. Industry standard is to cache robots.txt per domain for 24 hours and re-fetch periodically.

```java
public class RobotsTxtCache {
    private final RedisClient redis;

    public RobotsTxtRules getRules(String host) {
        String cacheKey = "robots:" + host;
        String cached = redis.get(cacheKey);

        if (cached != null) {
            return RobotsTxtRules.parse(cached);
        }

        // Fetch and parse
        String content = httpClient.get("https://" + host + "/robots.txt");
        if (content == null) {
            content = "";  // No robots.txt = all paths allowed
        }

        redis.setex(cacheKey, 86400, content);  // Cache 24 hours
        return RobotsTxtRules.parse(content);
    }

    public boolean isAllowed(String host, String path, String userAgent) {
        RobotsTxtRules rules = getRules(host);
        return rules.isAllowed(userAgent, path);
    }
}
```

**Spider Trap Detection:**

Spider traps generate infinite URLs. Common patterns:
- Calendar pages with "next" links: `/calendar?date=2026-01-01`, `/calendar?date=2026-01-02`, ...
- Session IDs: `/page?session=abc123&page=1`, `/page?session=def456&page=1` (same content, different session)
- Infinite pagination: pagination with no `rel="nofollow"` on extreme page numbers

Googlebot handles this with a combination of:
1. **URL normalization**: Strip session IDs and tracking parameters before deduplication. `/page?session=abc123&utm_source=x` normalizes to `/page`.
2. **Depth limit**: Never follow more than N links from a seed URL (e.g., 50 hops).
3. **Domain URL count cap**: If a domain has generated more than 1M unique URLs, flag it for manual review.
4. **Query parameter capping**: URLs with more than 5 query parameters are deprioritized.

```java
public class URLNormalizer {
    // Parameters that are always stripped (tracking noise)
    private static final Set<String> STRIP_PARAMS = Set.of(
        "utm_source", "utm_medium", "utm_campaign", "utm_content", "utm_term",
        "fbclid", "gclid", "sessionid", "PHPSESSID", "jsessionid"
    );

    public String normalize(String rawUrl) {
        URI uri = URI.create(rawUrl);
        String host = uri.getHost().toLowerCase();
        String path = uri.getPath();

        // Remove default ports
        // Strip tracking params
        Map<String, String> params = parseQueryParams(uri.getQuery());
        params.keySet().removeAll(STRIP_PARAMS);

        // Remove fragments (#section - server never sees these)
        // Decode then re-encode path consistently
        return buildURI(uri.getScheme(), host, path, params);
    }
}
```

### 4. Deduplication: Bloom Filters and SimHash

**URL-level Deduplication (Bloom Filter):**

With 5 billion URLs in the seen-set, a hash set would require ~200 GB of RAM (5B × 40 bytes per entry). A Bloom filter with 1% false positive rate needs only ~6.25 GB — a 32× reduction.

A Bloom filter is a bit array of size `m` with `k` hash functions. To add a URL: set `k` bits at positions `h1(url) % m`, `h2(url) % m`, ..., `hk(url) % m`. To check if a URL is seen: test whether all `k` bits are set. If any bit is 0, the URL is definitively new. If all bits are 1, the URL is probably seen (with 1% false positive rate). False positives mean we occasionally skip a URL we haven't actually crawled — acceptable. False negatives are impossible by design.

```java
public class URLSeenFilter {
    // Optimal params for 5B elements, 1% FP rate:
    // m = 47.9 billion bits ≈ 6 GB
    // k = 7 hash functions
    private final BloomFilter<String> filter = BloomFilter.create(
        Funnels.stringFunnel(StandardCharsets.UTF_8),
        5_000_000_000L,   // expected insertions
        0.01              // false positive rate
    );

    public boolean isDefinitelyNew(String normalizedUrl) {
        return !filter.mightContain(normalizedUrl);
    }

    public void markSeen(String normalizedUrl) {
        filter.put(normalizedUrl);
    }
}
```

**Content-level Deduplication (SimHash):**

Even after URL normalization, two different URLs can have identical or near-identical content (mirror sites, syndicated articles). SimHash detects near-duplicates: pages with >80% text overlap hash to values that differ by fewer than 3 bits out of 64.

SimHash algorithm:
1. Extract and tokenize text content of the page
2. For each token, compute a 64-bit hash
3. For each bit position: if the hash bit is 1, add token's weight; if 0, subtract weight
4. Final SimHash: set bit to 1 if sum > 0, else 0

Two pages with SimHash values differing by ≤ 3 bits (Hamming distance ≤ 3) are near-duplicates.

```java
public class ContentDeduplicator {
    private final Map<Long, String> simHashStore;  // hash → canonical URL

    public boolean isNearDuplicate(String pageContent) {
        long simHash = SimHash.compute(pageContent);

        // Check all stored hashes for Hamming distance <= 3
        // In practice, use a lookup table partitioning the 64-bit hash
        // into 4 × 16-bit segments for efficient lookup (pigeonhole principle)
        for (long storedHash : simHashStore.keySet()) {
            if (Long.bitCount(simHash ^ storedHash) <= 3) {
                return true;  // Near-duplicate found
            }
        }

        simHashStore.put(simHash, canonicalUrl);
        return false;
    }
}
```

The Wayback Machine (Internet Archive) uses a similar scheme — SimHash on page content — to decide whether to store a new snapshot or skip it as unchanged.

---

## Trade-offs Matrix

| Decision | Choice Made | Alternative | Trade-off |
|----------|-------------|-------------|-----------|
| **Crawl order** | BFS with priority overlay | DFS | BFS distributes load across domains and discovers high-rank pages earlier; DFS is simpler but hammers single domains and delays discovery |
| **Domain-to-worker assignment** | Consistent hashing on domain | Random assignment | Consistent hashing guarantees one worker per domain (politeness); random assignment is simpler but causes duplicate crawls and violates per-host rate limits |
| **URL deduplication** | Bloom filter (6 GB RAM, 1% FP) | Full hash set (200 GB RAM) | Bloom filter is 32× more memory efficient; 1% false positive means occasionally skipping a valid URL — acceptable given crawl scale |
| **Content deduplication** | SimHash (64-bit fingerprint) | MD5 exact match only | SimHash catches near-duplicates (mirrors, syndication) that MD5 misses; slightly more CPU cost; reduces stored content by ~35% |
| **DNS resolution** | Dedicated resolver pool with extended TTL cache | OS-level resolver | Dedicated pool is operationally complex but reduces DNS latency from 100ms to <1ms at scale; OS resolver is simpler but becomes the bottleneck above ~1K pages/sec |
| **robots.txt compliance** | Cache per domain for 24 hours | Re-fetch every request | 24-hour cache eliminates 99.9% of robots.txt fetches; stale rules occasionally miss a newly added disallow — mitigated by checking major sites more frequently |
| **JavaScript rendering** | Separate rendering tier (Chromium pool) | Skip JS-rendered pages | Rendering tier catches 60%+ of modern web content that is otherwise invisible; but costs 50-100× more per page than raw HTTP — use selectively for known JS-heavy domains |

---

## Failure Scenarios

### Scenario 1: Frontier Service Crash

**Detection:** Worker heartbeat monitor detects no new URLs dispatched for > 30 seconds; frontier health check fails.

**Impact:** All crawl workers idle. No new URLs dispatched. In-flight crawls complete normally. No data loss — the frontier state is persisted to a distributed queue (Kafka or Redis Streams), not held in memory.

**Mitigation:**
- Frontier service runs as a redundant pair (primary + standby) with leader election via ZooKeeper
- Standby takes over within 10 seconds of detecting primary failure
- Workers buffer up to 100 URLs each locally; can continue fetching for ~50 seconds without frontier contact
- **RTO**: < 15 seconds, **RPO**: zero (all state is durable)

### Scenario 2: Bloom Filter Corruption or Loss

**Detection:** Sudden spike in DB "URL already exists" constraint violations (false negatives are impossible, but we'd see duplicate writes if the filter was reset); monitoring alert if duplicate rate > 0.1%.

**Impact:** If Bloom filter is lost (e.g., process restart without persistence), we treat all URLs as new. We re-crawl content we've already seen until the filter is rebuilt. Bandwidth and storage waste, but no data loss.

**Mitigation:**
- Serialize Bloom filter to disk every 15 minutes (a 6 GB file takes ~10 seconds to write)
- On startup, reload from last checkpoint and replay the URL queue for the last 15 minutes from durable storage
- Content deduplication (SimHash) acts as a second line of defense — redundant re-crawls will not generate duplicate stored content
- **Recovery time**: < 20 minutes to rebuild from checkpoint

### Scenario 3: Object Storage (S3) Write Failure

**Detection:** P99 write latency to S3 exceeds 5 seconds; error rate alert fires at > 0.1% failed writes.

**Impact:** Crawled content cannot be persisted. Workers are fetching pages and discarding them. URL metadata (crawled, found links) may still be written to the metadata DB — creating a record of a crawl with no associated content.

**Mitigation:**
- Workers write content to a local disk buffer first, then async upload to S3
- Failed S3 writes are retried with exponential backoff (max 5 retries over 2 minutes)
- If buffer fills (disk > 80% full), worker pauses fetching and focuses on draining the upload queue
- Dead-letter queue (SQS) captures URLs whose content could not be stored after all retries; these are re-enqueued in the frontier with high priority
- **RTO**: Content stored once S3 recovers; **RPO**: Up to 2 minutes of content in local buffer during failure

---

## Monitoring & Alerts

**Key Metrics:**
```
Crawl throughput:
- Pages crawled per second: target 10,000 (P1 alert if < 5,000 for 5 min)
- URLs discovered per second: target 50,000 (new links extracted)
- Frontier queue depth: target < 100M (P1 if > 500M — crawl falling behind)

Politeness:
- Requests per second per domain: max 1/Crawl-delay (alert if violated)
- robots.txt cache hit rate: >99.9%

DNS performance:
- DNS cache hit rate: >99% (P1 alert if < 95% — crawl speed collapses)
- DNS lookup P99 latency: < 5ms (cache hit), < 150ms (cache miss)

Content pipeline:
- Duplicate URL rate: ~30-40% expected (alert if < 5% — Bloom filter may be broken)
- Near-duplicate content rate: ~15-20% expected
- S3 write success rate: >99.9%

Worker health:
- Active workers: 5,000 expected (P1 alert if < 4,000)
- Per-worker fetch error rate: < 1% (404s, timeouts)
```

**Alerts:**
```
- P0: Crawl throughput < 2,000 pages/sec for 3 min → Page on-call (frontier or worker pool failure)
- P0: DNS cache hit rate < 90% → Immediate investigation (crawl will grind to halt)
- P1: Frontier queue depth > 1B URLs → Scale up worker pool
- P1: S3 write error rate > 1% → Check object storage health
- P2: robots.txt fetch failure rate > 5% → Network or DNS issue
- P2: Worker error rate > 5% for any specific domain → Possible IP block; rotate egress IPs
```

---

## Interview Tips

**Common Questions:**

1. **"How do you ensure politeness?"** → Two-tier frontier: priority queue for importance, back queue per domain for rate limiting. Enforce Crawl-delay from robots.txt. Consistent hash on domain ensures one worker owns each domain — no two workers hit the same host simultaneously.

2. **"How do you handle duplicate pages?"** → Two levels: URL dedup via Bloom filter (6 GB for 5B URLs, 1% FP rate) and content dedup via SimHash (Hamming distance ≤ 3 = near-duplicate). Explain that URL normalization (stripping tracking params, session IDs) reduces duplicates before they even reach the Bloom filter.

3. **"What is a spider trap and how do you handle it?"** → Infinite URL generation from calendars, session IDs, endless pagination. Mitigations: URL normalization strips session IDs; depth limit caps link-following depth; per-domain URL count cap flags unusual domains; query parameter count limit deprioritizes URLs with many parameters.

4. **"How do you scale to 10K pages/sec?"** → 5,000 worker threads across 50 machines. Consistent hashing assigns domains to workers. DNS resolver pool with 1M-entry cache eliminates the DNS bottleneck. Frontier service dispatches work; object storage handles content at any scale.

5. **"How does Googlebot differ from Common Crawl?"** → Googlebot is proprietary, crawls the full web continuously, uses PageRank to prioritize, respects robots.txt strictly, and feeds Google's index. Common Crawl is open, periodic (monthly snapshots), does not prioritize by PageRank, and stores raw crawl data for academic/research use. Both use BFS, Bloom filter deduplication, and politeness enforcement — the architecture is essentially the same at scale.

**Time Allocation:**
- Requirements: 5 min
- Estimation: 5 min
- API + Architecture: 10 min
- Deep dives (Frontier, Dedup, Politeness, DNS): 25 min
- Failure scenarios: 5 min
- Trade-offs: 5 min

---

## Interview Questions Asked

### Google
1. **"Design Googlebot — how do you prioritize which pages to crawl first across 50 billion URLs?"** → PageRank-informed priority queue: seed with high-PageRank domains, score new URLs based on the linking page's rank, freshness signals (last-modified header, sitemap change frequency), and content importance. The interviewer wants to hear that priority is dynamic — a page that wasn't important yesterday (no inbound links) can become important today (viral link acquisition).
2. **"How do you avoid re-crawling a page that hasn't changed?"** → Check `Last-Modified` and `ETag` headers; send conditional GET (`If-Modified-Since` / `If-None-Match`). If the server returns 304 Not Modified, skip processing. Additionally, compare SimHash of new content vs. stored hash — even without server-side caching headers, content dedup catches unchanged pages.
3. **"Googlebot needs to render JavaScript — how does that change the architecture?"** → Add a headless browser rendering pool (Chromium-based) between the fetcher and the parser. Two-pass strategy: fast HTML fetch first for link discovery; JS rendering is deferred and done asynchronously for pages that require it (detected by comparing raw HTML link count vs. rendered DOM link count). JS rendering is ~10× more expensive — apply selectively.

### Amazon
1. **"Design a price monitoring crawler for Amazon Marketplace — crawling competitor sites for price data."** → Scheduled recrawl (not BFS-style discovery) — the URL set is known and fixed. Use a time-based priority queue (recrawl every N hours based on price volatility). Key differences from general crawl: no link discovery needed, politeness must be strict (rate limits to avoid blocks), and content extraction (price parsing) is the primary goal rather than link graph construction.
2. **"Competitor sites start blocking your crawler — how do you handle IP blocking?"** → Egress IP rotation via a proxy pool. Rotate User-Agent strings (though unethical to spoof as Googlebot). Rate limit aggressively (1 req/10s per domain). Use residential proxy pools for sites with sophisticated bot detection. At the design level: per-domain error rate monitoring — if a domain's error rate spikes, back off exponentially and alert.

### Common Follow-ups
1. **"What is a crawler trap and how do you detect one?"** → Infinite URL generation: calendars (`?date=2026-05-18`, `?date=2026-05-19`, ...), session IDs in URLs, infinite pagination. Detection: per-domain URL count cap (flag domains where discovered URL count > 100× average), depth limit (stop following links beyond depth N), query parameter count limit (deprioritize URLs with > 3 parameters).
2. **"How do you detect near-duplicate pages (same content, different URLs)?"** → SimHash: compute a 64-bit fingerprint from the page's content shingles; pages within Hamming distance ≤ 3 are near-duplicates. Store all SimHashes in a distributed table; on each new crawl, query for near-neighbors before storing. URL normalization (strip tracking params, canonicalize) is a cheaper first pass that catches exact duplicates.
3. **"How do you distinguish discovery crawl (finding new URLs) from scheduled recrawl (refreshing known URLs)?"** → Two separate queues in the frontier: discovery queue (BFS from seed URLs, runs continuously) and recrawl queue (time-ordered by next-crawl-timestamp, runs on schedule). Workers pull from recrawl queue with higher priority during off-peak hours, switch to discovery during low-recrawl periods. The interviewer is checking whether you conflate the two use cases.
4. **"How do you handle DNS resolution at scale — DNS is often the bottleneck for crawlers."** → Dedicated DNS resolver pool with a large in-memory cache (1M entries covering ~80% of domains). Pre-resolve domains before the HTTP fetch to pipeline the latency. Use anycast DNS servers close to crawler workers. Cache TTL-respecting but with a minimum floor (don't re-resolve sub-second). DNS cache hit rate is a P0 metric — if it drops below 90%, crawl throughput collapses.
