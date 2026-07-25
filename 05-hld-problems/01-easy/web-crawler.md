> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design a web crawler — a distributed system that systematically fetches and indexes the web, starting from seed URLs and discovering new ones through link extraction.
>
> **Key design decisions:**
> - URL frontier: priority queue of URLs to crawl; BFS traversal; distributed as message queue (Kafka/SQS) for horizontal scaling
> - Deduplication: Bloom filter for URL seen-check (1B URLs × 10 bits ≈ 1.2 GB); secondary DB for exact dedup if Bloom allows a false positive
> - Politeness: per-domain crawl rate (robots.txt crawl-delay); separate queue per domain; rate limit fetcher per domain
> - Content deduplication: SimHash or MD5 of page content to detect near-duplicates (mirror sites); avoid storing/indexing duplicates
> - Distributed fetcher pool: stateless workers pull from Kafka URL queue; store HTML in object storage (S3); parse and extract links; enqueue new URLs
> - robots.txt compliance: fetch robots.txt for each domain on first visit; cache with TTL; skip disallowed paths
> - Recrawl scheduling: time-based (revisit popular pages every hour, rare pages monthly) + change-detection heuristics
>
> **Key takeaway:** The URL frontier (priority queue), Bloom filter dedup, and per-domain politeness rate limits are the three critical components — get these right and the system scales horizontally.

---
module: 05-hld-problems
topic: Easy
status: unread
tags: [05-hld-problems, system-design, easy, web-crawler, bfs, politeness, deduplication]
---
# Design a Web Crawler

> **Difficulty**: Easy | **Asked at**: Amazon, Google, Microsoft

---

## Problem Statement

Design a web crawler that systematically browses the internet, fetches web pages, and indexes their content for a search engine. The crawler starts from a set of seed URLs and discovers new URLs by parsing links from fetched pages.

---

## Functional Requirements

1. **Crawl URLs**: Fetch web page content starting from seed URLs
2. **Link extraction**: Parse HTML and extract all linked URLs
3. **Politeness**: Respect `robots.txt` and crawl-delay directives
4. **Deduplication**: Never fetch the same URL twice
5. **Recrawl**: Periodically re-crawl pages to detect content changes
6. **Scope filtering**: Crawl only specified domains or content types

---

## Non-Functional Requirements

- **Scale**: Crawl 1 billion pages; internet has ~5 billion indexed pages
- **Throughput**: 1,000 pages/second = 86M pages/day
- **Storage**: 500 KB avg page × 1B pages = 500 TB (compressed with gzip: ~100 TB)
- **Politeness**: No more than 1 request/second per domain
- **Freshness**: High-priority pages re-crawled every 24h; others every 30 days

---

## Core Entities

| Entity | Key Fields |
|--------|-----------|
| `URL Frontier` | url, priority, scheduled_at, domain |
| `Crawled Page` | url, content_hash, fetched_at, http_status, content_location (S3) |
| `Domain Record` | domain, last_crawled, crawl_delay, robots_txt_rules |
| `URL Fingerprint` | url_hash (for deduplication bloom filter) |

---

## API Design

The crawler is a backend pipeline, not a user-facing API. Internal control endpoints:

```http
POST /admin/seeds
Body: { "urls": ["https://example.com", "https://news.ycombinator.com"] }

GET /admin/stats
Response: { "pages_crawled": 1234567, "queue_depth": 50000, "pages_per_sec": 980 }

PUT /admin/config
Body: { "max_depth": 5, "allowed_domains": ["*.com", "*.org"], "concurrency": 1000 }
```

---

## High-Level Design

```
Seed URLs
  │
  ▼
URL Frontier (Redis Sorted Set, priority by crawl_urgency)
  │
  ├── Fetcher Pool (1000 workers)
  │     │
  │     ├── Check robots.txt cache → skip if disallowed
  │     ├── Rate limiter (1 req/sec per domain)
  │     ├── HTTP GET page content
  │     │
  │     ├── Deduplication filter (bloom filter + Redis SET for content hash)
  │     │
  │     ├── Store raw HTML → S3
  │     │
  │     └── Emit to Kafka
  │
  ▼
Kafka (crawled-pages topic)
  │
  ├── Link Extractor: parse HTML → extract URLs → filter → add to URL Frontier
  │
  └── Content Indexer: parse text → send to search engine index
```

**URL Frontier**: A Redis sorted set where the score is the next-crawl timestamp. Workers call `ZPOPMIN` to get the highest-priority URL to crawl. Priority factors: domain authority, incoming link count, recency of last change.

**Fetcher Pool**: 1,000 concurrent workers, each handling one HTTP request at a time. Async I/O (Python asyncio, Go goroutines) makes this efficient — most time is spent waiting on network I/O.

**Kafka decoupling**: Fetched pages are published to Kafka. Link extraction and content indexing are separate consumers. This allows independent scaling — indexing is slower than fetching.

---

## Deep Dive 1: Politeness and robots.txt

**Problem**: Crawling too aggressively can overload target servers (causing DoS), get the crawler IP banned, or violate website terms.

**robots.txt**: Every domain publishes `https://domain.com/robots.txt`. Example:
```
User-agent: *
Disallow: /private/
Crawl-delay: 1
```
The crawler must fetch and parse robots.txt before crawling any URL on a domain. Cache the robots.txt rules per domain (TTL: 24h) in Redis to avoid fetching it on every request.

**Crawl-delay enforcement**: Maintain a `last_crawled_at` timestamp per domain. Before fetching the next URL from a domain, verify `now - last_crawled_at > crawl_delay`. If not, push the URL back to the frontier with a future scheduled_at.

**Per-domain queues**: Organize the URL frontier as a two-level structure:
1. Domain queue: one queue per domain, ordered by priority
2. Fetcher queue: a single queue that fetcher workers pull from, populated by rotating across domain queues with rate limiting

This ensures no domain gets more than its fair share of requests regardless of how many pending URLs it has.

**Politeness beyond robots.txt**: Use exponential backoff on 429 (Too Many Requests) and 503 responses. Detect soft rate limits (responses arriving slower than usual) and back off proactively.

> 🎯 **Staff signal:** The insight is that politeness is a *per-domain* invariant that must survive a frontier sharded for *throughput* — and those two goals fight. The senior structure is the two-level frontier: per-domain queues enforce crawl-delay locally, feeding one fetcher queue that round-robins across domains, so a domain with a million pending URLs still gets exactly its rate-limited share. Name the failure of the naive single priority queue: it would let one large site monopolize your fetchers and get you IP-banned. Recognizing that fairness/politeness is a scheduling constraint you design the frontier *around*, not a check you bolt on, is the E5→E6 framing.

---

## Deep Dive 2: Deduplication at Scale

**Problem**: With 1 billion pages and millions of links discovered per day, checking every URL against a database before fetching is too slow.

**URL deduplication (exact URLs)**:
- **Bloom filter**: A probabilistic data structure — O(1) lookup, 1% false positive rate. Store all seen URLs in a bloom filter (40 bits per URL × 1B URLs = 5 GB). A false positive means occasionally skipping a new URL (acceptable). Zero false negatives — no duplicate fetches.
- **Secondary check**: For URLs passing the bloom filter, verify in a Redis SET (exact check). Redis can hold 1B keys in ~100 GB with a hashing trick (`hash(url)` → 8-byte key).

**Content deduplication (same content, different URLs)**:
- Compute SHA256 of page content after fetching. Check hash against a Redis SET.
- If hash already seen: skip indexing (duplicate content). Store just the URL → canonical_url mapping.
- SimHash: For near-duplicate detection (pages that differ by one sentence), use SimHash — a locality-sensitive hash where similar content produces similar hashes.

**URL normalization**: Before deduplication, normalize URLs:
- Remove default ports: `http://example.com:80/` → `http://example.com/`
- Sort query params: `?b=2&a=1` → `?a=1&b=2`
- Remove fragments: `#section` removed (same page)
- Lowercase scheme and host

> 🎯 **Staff signal:** The move is a *two-stage* dedup that plays each structure to its strength: a bloom filter as an O(1), 5 GB in-memory pre-filter that answers "definitely new / maybe seen" with zero false negatives — so it *never* lets a duplicate through — backed by an exact Redis SET check only for the maybes. Name why the false-positive direction is the safe one: a bloom false positive occasionally skips a genuinely new URL (a cheap miss), whereas a false negative would re-fetch and is the failure you can't tolerate at 1B pages. The deeper senior point is separating *URL* dedup from *content* dedup (SimHash for near-duplicates), because the same page is reachable by many URLs. Choosing the probabilistic structure *because its error mode is the acceptable one* is the E5→E6 line.

---

## Deep Dive 3: Prioritization and Recrawling

**Problem**: With 1 billion pages and a crawl rate of 1,000 pages/second, it takes 11 days to crawl everything once. How do you prioritize?

**Factors for priority scoring**:
1. **Domain authority**: PageRank-like score based on incoming links. High-authority domains (Wikipedia, NYT) crawled more frequently.
2. **Change frequency**: Track how often a page's content hash changes over time. High-change pages (news homepages) get higher priority. Compute `change_rate = changes / days`.
3. **Incoming links**: Pages with more inlinks are more important. Track inlink count during crawl.
4. **User demand**: If many users search for terms only found on a specific page, boost its crawl priority.

**Recrawl scheduling**:
```
next_crawl_at = last_crawl_at + (1 / change_rate) × freshness_target
```
A page that changes daily: next crawl in 1 day. A page unchanged for 6 months: next crawl in 30 days.

**URL frontier implementation**: Redis sorted set (`ZSET`):
- Key: `frontier`
- Score: `next_crawl_at` (Unix timestamp)
- Member: URL

Workers call `ZPOPMIN` to get the URL with the earliest scheduled crawl time. URLs not yet due sit in the frontier until their scheduled time.

> 🎯 **Staff signal:** The senior reframe is that a full crawl takes ~11 days, so "crawl everything" is never the goal — *freshness allocation* is. The insight is making recrawl interval a function of measured `change_rate` (`next_crawl = last_crawl + freshness_target / change_rate`), so a news homepage that changes hourly is revisited constantly while a static docs page drifts to monthly — you spend your fixed 1,000 pages/sec budget where content actually moves. Implementing that as a Redis ZSET scored by `next_crawl_at` with `ZPOPMIN` turns "what should I crawl next" into a single O(log N) pop. Converting a fixed crawl budget into a demand-and-change-weighted schedule, rather than a flat round, is the E5→E6 framing.

---

## Interviewer Questions by Level

**Junior**:
- What is BFS vs DFS for crawling? Which do you use and why?
- How do you avoid crawling the same page twice?
- What is robots.txt and why must you respect it?

**Mid-level**:
- How do you enforce the politeness constraint (1 request/second per domain) across 1,000 parallel workers?
- How does a bloom filter work? What's the false positive rate tradeoff?
- How do you detect and handle crawl traps (infinite URL spaces like calendars with next/prev links)?

**Senior**:
- How do you scale the URL frontier to handle 10 billion URLs? What are the storage requirements?
- Design the recrawl scheduler — how do you decide when to re-crawl a page?
- How do you handle JavaScript-rendered pages (SPAs) that require a headless browser?
- How would you distribute the crawler across multiple datacenters without duplicate work?

---

## Related

**Concepts used in this design**

- [Message Brokers](../../02-building-blocks/04-coordination/01-message-brokers.md)
- [Bloom Filter](../../02-building-blocks/02-performance/04-bloom-filter.md)
- [Rate Limiting](../../02-building-blocks/02-performance/02-rate-limiting.md)
- [Consistent Hashing](../../02-building-blocks/03-data-partitioning/03-consistent-hashing.md)

**Practice next**

- [Typeahead Search](../02-medium/typeahead-search.md)
- [GitHub Code Repo](../03-hard/github-code-repo.md)

A crawler feeds the search index built in typeahead.

**Frameworks**: [HLD Template](../../07-interview-templates/01-frameworks/01-hld-template.md) · [Capacity Estimation](../../07-interview-templates/02-cheat-sheets/02-capacity-estimation.md) · [Trade-offs Cheat Sheet](../../07-interview-templates/02-cheat-sheets/01-trade-offs-cheat-sheet.md)
