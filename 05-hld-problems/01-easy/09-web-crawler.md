---
module: 05-hld-problems
topic: Easy
status: interview-ready
tags: [05-hld-problems, system-design, easy]
---
# Design a Web Crawler

> **Difficulty**: Easy/Medium
> **Topics**: Distributed Queues, Deduplication, Politeness, Backpressure
> **Time**: 60 min
> **Companies**: Google, Amazon, Microsoft

---

## Clarifying Questions

1. "Is this a general web crawler (all of the internet) or domain-scoped?"
2. "What's the target throughput — 100M pages/day or 10B?"
3. "Do we need to re-crawl pages periodically, or is this a one-time crawl?"
4. "How do we handle JavaScript-rendered pages (SPAs)?"
5. "What's the output — raw HTML to object storage, or a processed index?"
6. "Do we need near-duplicate detection, or just exact URL deduplication?"

---

## Back-of-Envelope

```
Target: 100M pages/day
  Fetch rate: 100M / 86,400 = 1.2K pages/sec avg, 3K pages/sec peak

Storage:
  Raw HTML avg 100KB compressed: 100M x 100KB = 10TB/day
  Metadata (URL, status, timestamps): 1KB/page = 100GB/day

Deduplication:
  1B known URLs at 1% FPR Bloom filter = ~1.2 GB in RAM (cheap)

Workers:
  Each worker: 10 pages/sec (fetch + parse + enqueue)
  3K pages/sec peak / 10 = 300 workers needed at peak
```

---

## APIs

```
// Seed URLs (admin endpoint)
POST /api/v1/crawler/seeds
  { "urls": ["https://example.com", "https://news.ycombinator.com"] }
  -> { "enqueued": 2 }

// URL status lookup
GET /api/v1/crawler/status?url=https://example.com
  -> { "status": "FETCHED", "last_fetch_at": "...", "http_status": 200 }
```

---

## Architecture

```
Seed URL API
  |
  v
URL Canonicalizer (normalize: lowercase, strip fragment, sort params)
  |
  v
SQS Frontier Queue (one message per URL to crawl)
  |
  v
Crawler Workers (ECS/EC2 autoscaled)
  |
  +-- Robots Cache (Redis, TTL 24h per domain)
  +-- Per-domain Rate Limiter (Redis token bucket, 1 req/sec default)
  +-- HTTP Fetcher
  |
  +-- S3 (raw HTML, gzip compressed)
  |     key: raw/{year}/{month}/{day}/{url_hash}.html.gz
  |
  +-- Link Parser -> canonicalize -> Bloom filter check
  |                               -> DynamoDB conditional write (dedup)
  |                               -> SQS Frontier (new URLs)
  |
  └── DynamoDB url_status table (update status=FETCHED)

SQS DLQ: permanently failed URLs after 3 retries
CloudWatch alarms: queue depth, message age, DLQ depth
```

---

## Data Model

```
url_status table (DynamoDB):
  url_hash         PK (SHA-256 of canonical URL, hex string)
  canonical_url    String
  domain           String (GSI partition key for domain-level queries)
  status           ENUM: DISCOVERED | FETCHING | FETCHED | FAILED | TERMINAL
  last_fetch_at    Timestamp
  retry_count      Number
  content_s3_key   String
  http_status      Number

domain_policy table (DynamoDB):
  domain           PK
  robots_rules     String (raw robots.txt content)
  crawl_delay_ms   Number (default 1000)
  last_access_at   Timestamp
  failure_rate     Number
```

---

## Key Design Decisions

**1. Two-phase deduplication: Bloom filter then DynamoDB conditional write**
Bloom filter (in-process, ~1.2 GB for 1B URLs at 1% FPR): fast approximate check. If "not seen": proceed to DynamoDB. DynamoDB `PutItem` with `ConditionExpression: attribute_not_exists(url_hash)` — atomic, authoritative. Bloom filter's 1% false positives occasionally skip a URL we haven't seen — acceptable for crawling. False negatives are impossible (Bloom never misses a true member), so we never re-crawl a seen URL.

**2. Per-domain politeness as a correctness requirement**
300 workers hitting the same domain at 1 page/sec each = 300 req/sec to one site → instant IP ban. Politeness rule: `fetch only if now >= domain.last_access_at + crawl_delay_ms`. Implemented via Redis token bucket per domain. Parse `robots.txt` and respect `Crawl-delay`. One crawler worker per domain at any time (partition SQS messages by domain hash for coarse locality).

**3. SQS visibility timeout for crash safety**
Worker fetches a URL, message becomes invisible (visibility timeout = 5 min). If worker crashes mid-fetch: message reappears after 5 min and another worker retries. If worker completes: explicit `DeleteMessage`. This gives automatic retry without a separate retry queue. After `maxReceiveCount` retries (3): message goes to DLQ for manual inspection.

**4. Exponential backoff on 5xx, terminal on 404/410**
5xx errors: server is overloaded, retry with exponential backoff (1min, 5min, 30min). 404/410: page is gone, mark status=TERMINAL, never re-enqueue. 429 (rate limited): respect `Retry-After` header. Robots-blocked: mark domain as DISALLOWED, skip all paths matching the rule.

---

## Deep Dives

**Near-duplicate detection with Simhash**
Two pages with different UTM parameters serve identical content. URL canonicalization handles the easy case (strip UTM params). For near-duplicates: compute 64-bit Simhash fingerprint of page content. Pages with Hamming distance ≤ 3 are near-duplicates. Store fingerprints in a distributed table indexed by most-significant bits (locality-sensitive hashing). On new page: query for similar fingerprints → deduplicate. Adds ~50ms per page but avoids indexing 30% duplicate content.

**JavaScript-rendered pages (SPAs)**
Static HTML parser handles 80% of pages. SPAs that render content via JavaScript need a headless Chrome instance (Puppeteer/Playwright). Two worker pools: fast/static (300 workers) and slow/headless (20 workers). Detection: if parsed HTML has <10 links but contains React/Vue bundle markers → re-enqueue to the headless pool. Headless workers are 10-50× slower and more expensive — use them sparingly.

---

## Failure Scenarios

| Failure | Impact | Mitigation |
|---------|--------|------------|
| Worker crash mid-fetch | URL not processed | SQS visibility timeout returns message to queue after 5 min |
| Repeated 5xx from domain | Retries amplify load on sick domain | Exponential backoff; pause domain after 5 consecutive failures |
| Permanent 404/410 | Wasted retry cycles | Mark status=TERMINAL immediately; never re-enqueue |
| Parser failure (malformed HTML) | URL stuck | Send to DLQ with S3 key; parse offline; don't block the queue |
| Frontier queue grows faster than workers consume | Memory/cost explosion | CloudWatch alarm on queue depth; autoscale workers; prioritize by domain rank |
| Bloom filter restart (in-process) | False negatives until filter rebuilt | Persist Bloom filter to S3 hourly; rebuild from DynamoDB on cold start |

---

## Interview Questions Asked

### Google
1. **"Design a web crawler that discovers all URLs on the internet — how do you avoid crawling infinite URLs from calendar pages?"** → Canonicalize URLs first (strip UTM params, normalize), then cap crawl depth per domain, apply URL pattern filters (block `/calendar?date=*`), and deprioritize high-cardinality parameterized URLs. The interviewer wants to see URL frontier management and the insight that infinite URL spaces must be bounded by policy, not just deduplication.
2. **"How do you partition work across 300 crawler workers without two workers hitting the same domain simultaneously?"** → Partition SQS messages by hash(domain) — all URLs for the same domain land in the same partition → naturally serialized per domain. For distributed queues without partitioning (standard SQS): use a per-domain Redis lock with TTL equal to the crawl delay. First worker to acquire the lock crawls; others skip and re-enqueue for later.

### Amazon
1. **"Design this using AWS-native services — what replaces Kafka, the dedup store, and the content store?"** → SQS for frontier queue (with DLQ), DynamoDB for url_status and domain_policy (conditional writes for dedup), S3 for raw HTML, ECS for autoscaled workers, CloudWatch for alarms. The interviewer is checking AWS service fluency: SQS visibility timeout replaces Kafka consumer group offsets; DynamoDB conditional writes replace ZooKeeper distributed locks.
2. **"Your SQS DLQ is filling up with URLs that always fail. How do you operationalize this?"** → CloudWatch alarm on DLQ depth. Lambda triggered on DLQ messages: log URL, domain, failure reason, HTTP status. Aggregate by domain to detect patterns (all failures from one domain → IP blocked, add to denylist; all 500s → wait 24h and retry; all 404s → mark TERMINAL). Weekly review of top DLQ domains.

### Common Follow-ups
1. **"Two workers race to crawl the same URL simultaneously. How do you prevent duplicate fetches?"** → DynamoDB conditional write: `PutItem(url_hash, status=FETCHING) WHERE attribute_not_exists(url_hash)`. Only one worker wins; the other gets `ConditionalCheckFailedException` and discards the URL. The Bloom filter catches 99% of duplicates before they reach DynamoDB — the conditional write handles the 1% that slip through and true race conditions.
2. **"How do you re-crawl pages periodically — news sites vs. Wikipedia vs. product pages?"** → Adaptive scheduling based on observed change rate: track content hash per page. If hash changes on re-crawl → high changer → shorten interval. If hash unchanged → low changer → double the interval (exponential backoff). Baseline by content type: news sites (1h), blog posts (1 week), Wikipedia (1 day), e-commerce products (6h). Sitemap feeds are more efficient than polling for sites that provide them.

---

## Interviewer Follow-Up Questions

**On politeness and scheduling:**
- "Your crawler visits 100M pages/day. How do you avoid getting blocked by websites?" → Politeness rules: (1) robots.txt — parse and honor before crawling any page on a domain. (2) Rate limit per domain: max 1 request/second to any single domain (configurable per domain via a Redis `ratelimit:{domain}` key). (3) Respect `Crawl-delay` in robots.txt. (4) Rotate User-Agent strings and declare your crawler bot identity. Getting blocked is expensive — you lose the domain and must re-crawl clean.
- "How do you schedule re-crawls? A news site changes every hour but a Wikipedia article changes yearly." → Adaptive scheduling: track last-modified time from HTTP headers (`Last-Modified`, `ETag`). If a page hasn't changed in the last 5 visits, double its crawl interval (exponential backoff). Cap at 30 days for slow-changing pages. News sites: crawl the sitemap (updated every 15 min) rather than brute-forcing all URLs. Sitemap-first is more efficient than discovering URLs via link parsing.
- "Your frontier has 1 billion URLs. How do you prioritize which URLs to crawl next?" → Priority queue with multiple tiers: (1) seed URLs and home pages — highest priority; (2) URLs discovered recently from high-PageRank pages; (3) URLs from popular domains; (4) tail-end URLs. Use a scored priority queue (Redis Sorted Set: score = crawl_priority × recency_weight). The scheduler pops the highest-score URL that respects domain politeness constraints.

**On deduplication:**
- "Two different URLs serve identical content. How do you detect this?" → Simhash (or MinHash LSH): compute a 64-bit fingerprint of the page content. Pages with Hamming distance ≤ 3 are considered near-duplicates. Store the simhash in a distributed hash table indexed by the most significant bits. On each new page: compute simhash, query for similar simhashes, deduplicate. Canonical URL normalization (strip UTM params, lowercase, trailing slash) handles the easy cases before content comparison.
- "How do you avoid crawling the same URL twice when you have 300 crawler workers running in parallel?" → Distributed URL seen-set: Bloom filter (fast approximate membership testing) backed by DynamoDB conditional write (authoritative dedup). Before enqueuing a URL: check Bloom filter (O(1), ~1% false positive rate). If "not seen": add to queue AND issue DynamoDB conditional write. Bloom filter's false positives mean you occasionally skip a URL you haven't seen — acceptable for crawling. Size: 1B URLs at 1% FP rate = ~1.2 GB of memory.

**On storage and parsing:**
- "Your crawler downloads 10 TB of HTML per day. Where do you store the raw pages?" → S3 with Gzip compression (compresses 4-10×, so ~1-2.5 TB/day actual storage). S3 key: `raw/{year}/{month}/{day}/{url_hash}.html.gz`. Metadata (URL, crawl_time, status_code, content_type, content_hash, links_found) goes to DynamoDB or Parquet on S3 for analysis. Never store raw HTML in the DB — blob storage is 10-100× cheaper.
- "How do you extract links from JavaScript-rendered pages (React/Vue apps)?" → Headless browser (Puppeteer/Playwright): spawn a Chrome instance, load the page, wait for network idle, then extract links from the DOM. This is 10-50× slower and more CPU-intensive than static HTML parsing. Strategy: start with lightweight static parsing; if the page has <10 links but appears to be an SPA (React bundle detected), re-queue for headless rendering. Maintain two pools: fast/static and slow/headless.
