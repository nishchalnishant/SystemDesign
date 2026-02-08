# #16 web crawler

Here’s a complete, time-boxed, 1-hour interview-ready answer for designing a web crawler. It follows your usual system design interview structure, including functional & non-functional requirements, APIs/data model, architecture, deep dive, and trade-offs.

***

## 0 – 5 min — Problem recap, scope & assumptions

<br>

Goal: Design a scalable web crawler that can efficiently discover, fetch, and index web pages. The crawler should handle billions of URLs, respect politeness (robots.txt), support incremental crawling, detect duplicates, and feed data to downstream systems like search engines or analytics pipelines.

<br>

Scope for interview:

* Web crawling: URL discovery, fetching, parsing HTML, handling redirects, errors, duplicate detection.
* Politeness: obey robots.txt, rate-limiting per domain.
* Incremental / scheduled crawling.
* Prioritization of URLs (freshness, importance, domain policies).
* Scalable and fault-tolerant distributed system.

<br>

Assumptions:

* Target: crawl \~1B URLs globally.
* Page size: average 100 KB.
* Crawl frequency: daily updates for news sites, weekly/monthly for static sites.
* Peak fetch: 100k pages/sec globally.

***

## 5 – 15 min — Functional & Non-Functional Requirements

<br>

#### Functional Requirements

<br>

Must

1. URL frontier management: maintain queue of URLs to crawl.
2. Fetcher: download HTML pages efficiently.
3. Parser: extract links, metadata, content.
4. Duplicate detection: avoid re-crawling same content or URL unnecessarily.
5. Politeness enforcement: respect robots.txt, rate-limit per domain/IP.
6. URL prioritization: based on freshness, importance, domain policies.
7. Content storage: store raw HTML, metadata, and optionally parsed text.
8. Error handling & retries: manage fetch failures, timeouts, redirects.
9. Incremental crawling: update previously crawled pages periodically.
10. Metrics & monitoring: track fetch rates, success/failure, latency.

<br>

Should

* Support distributed crawling across multiple machines/regions.
* Support multi-threaded or async fetchers per domain.
* Optional page content extraction for search indexing.

<br>

Nice-to-have

* Page classification (news, blogs, e-commerce).
* Change detection & differential updates.
* Politeness adaptation based on server responsiveness.

***

#### Non-Functional Requirements

* Scalability: handle billions of URLs; distribute crawl across many machines.
* Throughput: 100k pages/sec globally.
* Latency: prioritize fresh or high-value pages; crawl and feed data within few hours.
* Fault tolerance: failure of fetchers, parsers, or storage nodes shouldn’t halt system.
* Durability: ensure fetched pages are reliably stored; no data loss.
* Consistency: eventual consistency acceptable for crawl results.
* Resource efficiency: avoid overloading websites; manage network and compute.
* Monitoring & observability: crawl stats, success/failure, queue lengths, domain coverage.

<br>

SLO examples

* Retry failed fetch 3x before marking failed.
* Average fetch time < 2 sec per page.
* Duplicate URL ratio < 1%.

***

## 15 – 25 min — API / Data Model

<br>

#### Internal API / Component Contracts

* URL Frontier

```
enqueue(url, priority)   # add new URL
dequeue()                # fetch next URL to crawl
mark_done(url, status)   # record completion and status
```

* Fetcher

```
fetch(url) -> {status_code, headers, html_content, fetch_ts}
```

* Parser

```
parse(html_content) -> {extracted_urls[], metadata}
```

* Duplicate Detector

```
is_duplicate(url) -> bool
```

* Content Storage

```
store(page_id, url, content, metadata, fetched_ts)
```

#### Core Data Models

* URL Record: {url, domain, priority, last\_crawl\_ts, status, retry\_count}
* Page Content: {page\_id, url, content, metadata, fetch\_ts}
* Domain Info: {domain, robots\_txt, last\_access\_ts, crawl\_delay}
* Duplicate Tracker: {url\_hash, page\_id} (can use Bloom filter for fast checks)

***

## 25 – 40 min — High-level architecture & data flow

```
+------------------+        +-------------------+        +----------------+
| URL Frontier     |<------>| Fetcher Cluster   |<------>| Parser Cluster |
+------------------+        +-------------------+        +----------------+
       |                           |                            |
       v                           v                            v
  Duplicate Detector            Content Storage            New URLs (enqueue)
       |                           |                            |
       v                           v                            |
  Bloom filter / DB            HDFS / S3 / DB                Priority Queue
       |
       v
 Monitoring & Metrics
```

Components

1. URL Frontier: distributed queue (priority queue) to manage URLs by freshness, domain, and priority. Partitioned by domain for politeness enforcement.
2. Fetcher Cluster: multiple fetchers per domain/IP, multithreaded/async. Apply rate-limits per domain. Handles HTTP redirects, errors, retries.
3. Parser Cluster: extract links, metadata (title, headers, keywords), store page content.
4. Duplicate Detector: Bloom filters or content hash index to skip already fetched URLs.
5. Content Storage: HDFS, S3, or database to store fetched HTML and metadata.
6. Politeness Service: keeps track of domain last-access times, crawl delays.
7. Monitoring & Metrics: queue sizes, fetch success rate, per-domain stats, error rates.

<br>

Data flow

1. Dequeue URL from URL frontier.
2. Fetcher downloads page respecting domain politeness.
3. Page passed to parser; extracted URLs sent back to frontier if not duplicate.
4. Page content + metadata stored in storage for downstream usage.
5. Metrics updated asynchronously.

***

## 40 – 50 min — Deep dive — distributed crawling, deduplication, prioritization

<br>

#### Distributed crawling

* Frontier is sharded by domain hash: ensures only one fetcher per domain shard at a time.
* Multiple fetchers per shard for efficiency.
* Leader election or coordination via Zookeeper / etcd ensures one master per domain shard.

<br>

#### Deduplication

* URL-level: normalize URLs, hash, store in Bloom filter.
* Content-level: hash content (MD5/SHA-1) for exact duplicates, store fingerprints in DB.

<br>

#### Prioritization

* Priority = f(freshness, domain importance, crawl policy, recency).
* News sites get higher priority; rarely updated blogs lower.
* Use priority queue or scoring function in frontier.

<br>

#### Politeness & rate-limiting

* Track last-access per domain; ensure minimum crawl-delay (robots.txt or policy).
* Queue per domain or domain bucket ensures fetchers respect delays.

<br>

#### Incremental crawling

* Track last crawl timestamp. Only enqueue if due.
* Periodically recrawl pages based on change frequency.

<br>

#### Fault-tolerance

* Fetcher crash → unacknowledged URL re-enqueued.
* Parser crash → URL remains in frontier or failure queue for retry.
* Storage failures → retry async; maintain idempotency via page\_id or URL hash.

***

## 50 – 55 min — Back-of-the-envelope calculations

<br>

Assumptions

* 1B URLs, avg 100 KB per page → 100 TB raw content.
* Fetch throughput: 100k pages/sec globally.
* Page fetch = 100 KB → 10 GB/sec → 864 TB/day if fully saturating network (practical throttled).

<br>

Frontier sizing

* 1B URLs in frontier → partition across N nodes (say 100 shards) → 10M URLs per node.
* Priority queues + metadata \~ 100 bytes/URL → \~1 GB per node.

<br>

Fetcher cluster

* 100k pages/sec → 10 GB/sec outbound traffic. Can scale horizontally: 1000 fetchers at 100 pages/sec each.

<br>

Storage

* Use distributed object store (HDFS/S3) for fetched HTML.
* Content deduplication reduces storage for repeated pages (\~20–30% savings).

<br>

Deduplication

* Bloom filter: 1B URLs, 1% false positive → \~1.2 GB memory (fits in RAM).
* Additional DB for content hash for content-level deduplication.

***

## 55 – 58 min — Monitoring, ops & security

<br>

Monitoring & metrics

* Fetch success/failure per domain.
* Frontier queue size.
* Duplicate detection hit rate.
* Latency per fetch.
* Storage utilization.

<br>

Operational concerns

* Handle network errors, timeouts, retries.
* Respect robots.txt & crawl-delay policies.
* Detect and block crawler traps or infinite loops.
* Maintain secure storage and access control for content.

<br>

Security

* IP rotation or proxy network to avoid being blocked.
* Avoid fetching sensitive/private URLs.
* Rate-limits prevent DoS against sites.

***

## 58 – 60 min — Trade-offs, evolution & summary

<br>

#### Trade-offs

* Freshness vs throughput: higher frequency = more bandwidth; prioritize high-value sites.
* Storage vs deduplication: full content storage vs hash-only dedup for savings.
* Single frontier vs distributed: centralized easier but bottleneck; sharded frontier needed for global scale.
* Politeness vs speed: obey robots.txt + crawl-delay limits peak fetch per domain; balance via multiple domains concurrently.

<br>

#### Evolution

1. MVP: single-region, frontier + fetcher + parser + storage, obey politeness.
2. Scale: distributed frontier, multiple shards, domain-based sharding, multi-region fetchers.
3. Enhance: incremental crawling, content hashing, freshness scoring, ML for prioritization, advanced deduplication, adaptive politeness based on server load.

<br>

#### Summary

<br>

Build a distributed, sharded, fault-tolerant web crawler:

* URL frontier sharded by domain.
* Fetcher cluster respects politeness & rate-limits.
* Parser extracts links & content.
* Deduplication (URL & content) avoids re-fetching.
* Content stored in distributed storage, metrics collected for monitoring.
* System scales horizontally and incrementally crawls high-priority content efficiently.

***

If you want, I can:

* Draw a sequence diagram for enqueue → fetch → parse → store → enqueue new URLs, or
* Provide a capacity calculator for your expected number of URLs / page sizes / fetch rates.

<br>

Do you want me to create the sequence diagram next?
