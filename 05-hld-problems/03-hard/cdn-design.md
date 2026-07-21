> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design a CDN — geographically distributed content delivery with edge caching, cache invalidation, HTTPS termination, and origin offload.
>
> **Key design decisions:**
> - Edge PoP (Point of Presence): 200+ global locations; each PoP has cache cluster + reverse proxy + TLS termination; requests route to nearest PoP via Anycast BGP
> - Cache hierarchy: L1 (PoP-local SSD cache) → L2 (regional aggregation cache) → Origin; cache-hit at L1 serves in <20ms; L2 in <50ms; origin only for misses
> - Cache key: URL + Vary header (language, device type); CDN can cache different versions for mobile vs desktop, or by language
> - TTL strategy: static assets (images, JS, CSS) → long TTL (1 year, versioned URL); HTML pages → short TTL (5 min) or no cache
> - Cache invalidation: CDN-wide purge API (costly, use sparingly); URL versioning preferred (append hash to filename → new URL = new cache key)
> - Origin shield: single aggregation node per region that talks to origin; prevents thundering herd on origin when cache expires for popular content
> - DDoS protection: edge absorbs volumetric attacks; rate limiting at PoP; challenge-response (CAPTCHA) for bot traffic; BGP anycast for resilience
>
> **Key takeaway:** Anycast routing + origin shield are the two CDN-specific design elements interviewers test — Anycast routes to nearest PoP automatically; origin shield prevents cache stampede on popular content.

---
module: 05-hld-problems
topic: Hard
status: unread
tags: [05-hld-problems, system-design, hard, cdn, edge-caching, anycast, origin-server]
---
# Design a Content Delivery Network (CDN)

> **Difficulty**: Hard | **Asked at**: Cloudflare, Akamai, Netflix, Amazon

---

## Problem Statement

Design a Content Delivery Network (CDN) that caches and serves static and dynamic content from geographically distributed edge nodes close to users. The system must minimize latency globally, handle cache invalidation, support HTTPS termination, and survive origin server failures.

---

## Functional Requirements

1. **Edge caching**: Cache static content (images, JS, CSS, video segments) at PoPs close to users
2. **Origin pull**: On cache miss, fetch from origin server and cache at the edge
3. **Cache invalidation**: Purge cached content globally within 1 minute of request
4. **HTTPS termination**: TLS terminated at the edge; origin communication may be HTTP or HTTPS
5. **Dynamic content acceleration**: For uncacheable content, route requests via the CDN backbone to reduce latency
6. **Custom rules**: Per-customer cache TTL configuration, redirect rules, header manipulation

---

## Non-Functional Requirements

- **Scale**: 100 Tbps served globally (Cloudflare scale), 100M requests/sec
- **Latency**: < 10ms to nearest PoP for 95% of users globally
- **Cache hit rate**: > 95% for popular content
- **Availability**: 99.999% (5 minutes downtime/year)
- **Invalidation**: Global cache purge propagated within 60 seconds

---

## Core Entities

| Entity | Key Fields |
|--------|-----------|
| `CachedObject` | cache_key (URL + vary headers), content_bytes, content_type, etag, expires_at, created_at |
| `Customer` | customer_id, domains[], origin_url, cache_rules[], tls_certificate |
| `PoP` | pop_id, region, anycast_ip, capacity_gbps, is_healthy |
| `PurgeRequest` | purge_id, customer_id, url_pattern, status, created_at |

---

## API Design

**End user request** (implicit — no explicit API call):
```
GET https://cdn.customer.com/images/logo.png
→ Routes to nearest PoP via Anycast
→ PoP: cache hit → serve; cache miss → pull from origin
→ Response headers: X-Cache: HIT, Age: 3600, Cache-Control: max-age=86400
```

**CDN Management API** (for customers):
```http
POST /api/v1/purge
Body: { "customer_id": "c123", "urls": ["https://cdn.c.com/logo.png"] }
Response 202: { "purge_id": "p456", "status": "propagating" }

GET /api/v1/purge/{purge_id}/status
Response 200: { "status": "complete", "pops_purged": 250, "elapsed_ms": 8200 }

PUT /api/v1/customers/{id}/cache-rules
Body: { "rules": [{ "path": "/api/*", "ttl": 0 }, { "path": "/images/*", "ttl": 86400 }] }
```

---

## High-Level Design

```
User (Tokyo)
  │ DNS query: cdn.customer.com → Anycast IP 104.16.0.1
  │ (BGP routes user to nearest PoP)
  ▼
Tokyo PoP (Edge Server)
  │ Check local cache (in-memory LRU + SSD)
  │   HIT → serve (< 2ms)
  │   MISS:
  │     Check regional cache (L2 — larger capacity SSD cluster)
  │       HIT → serve, populate L1
  │       MISS:
  ▼
CDN Backbone (private fiber network)
  │ Route to origin region via anycast backbone
  ▼
Origin Server (customer's server)
  │ Fetch content → return to PoP
  │ PoP stores in L1 + L2 cache, serves response
  ▼
User receives content

Control Plane:
  Customer Management API → Config DB → PoP config propagation (< 30s)
  Purge API → Purge Coordinator → broadcast purge to all PoPs
  Health Monitor → mark unhealthy PoPs → reroute traffic via BGP
```

---

## Deep Dive 1: Anycast Routing and PoP Selection

**Problem**: How does a user's request automatically reach the nearest CDN edge server, without the CDN needing to know the user's location?

**Anycast**: Every CDN PoP announces the same IP address block (e.g., `104.16.0.0/12`) via BGP to the internet. When a user's router tries to reach that IP, BGP routes the packet to the geographically nearest PoP (by hop count). This is pure network-layer routing — no DNS tricks needed.

**Why Anycast works**: BGP naturally selects the path with the fewest hops. The nearest CDN PoP is almost always reachable in fewer hops than any other PoP. This provides automatic failover: if one PoP goes down and withdraws its BGP announcement, traffic automatically routes to the next-nearest PoP.

**DNS as a fallback**: For some CDNs, users do a DNS lookup first. The CDN's authoritative DNS returns an IP based on the user's DNS resolver location (GeoDNS). Less accurate than Anycast (resolver may be far from user) but gives more control over routing decisions.

**PoP failure**: If a PoP's health monitor detects failure (uptime check fails 3× in 30s), the PoP withdraws its BGP announcement. BGP convergence takes 30-90 seconds — during this time some traffic hits the failed PoP and gets errors. Mitigation: secondary anycast tier with slightly longer BGP path as fallback.

---

## Deep Dive 2: Cache Hierarchy and Eviction

**Problem**: A popular video segment is requested by 10M users/hour from the Tokyo PoP. How do you serve this without fetching from origin 10M times?

**Two-tier cache**:
- **L1 (in-memory, per-edge-server)**: 64 GB RAM, serves the hottest 1M objects. O(1) lookup. LRU eviction. Handles 1M requests/sec per server.
- **L2 (SSD, per-PoP cluster)**: 100 TB SSD, serves the warm tier. O(1) lookup via hash-based sharding across SSD nodes. 10ms latency. Handles cache misses that L1 can't serve.

**Cache key**: `hash(URL + sorted(Vary headers))`. The `Vary: Accept-Encoding` header means gzip and non-gzip versions are cached separately. The `Vary: Cookie` header makes every cookie variant a separate cache entry — CDNs typically strip cookies from cacheable requests.

**Eviction policy**:
- L1: LRU (simple, fast for hot objects)
- L2: LRFU (Least Recently/Frequently Used) — popular but not recently accessed objects stay longer

**Thundering herd on cache miss**: If L2 misses for a popular object, thousands of L1 requests may simultaneously try to fetch from origin. Use **request coalescing** (also called request collapsing): the first L2 miss sends one fetch to origin; all subsequent requests for the same object wait for the first fetch to complete, then all get the result from cache. Redis lock per cache key prevents parallel fetches.

---

## Deep Dive 3: Cache Invalidation Propagation

**Problem**: A customer deploys a new version of their JS bundle at `cdn.customer.com/app.js`. The old version is cached at 250 PoPs globally. How do you invalidate it within 60 seconds?

**Purge coordinator**: A centralized service receives the purge request and fans out to all PoPs.

```
Purge request → Purge Coordinator
  → publish purge event to Kafka topic `purge-events`
  → 250 PoP agents subscribe to this topic
  → each agent: delete from L1 + L2 cache, send ACK
  → coordinator tracks ACKs, marks purge complete when all PoPs ACK
```

**Propagation time**:
- Kafka global replication: ~100ms
- PoP processing: ~50ms
- Total: < 1 second for hot paths; 5-10 seconds worst case (slow PoPs, network hiccups)

**Wildcard purge**: `cdn.customer.com/images/*` — invalidates all objects with matching URL prefix. PoP agents maintain a URL prefix index (trie) to find all matching cache keys efficiently. This is expensive for large caches — rate-limited to 10 wildcard purges/min per customer.

**Soft purge vs hard purge**:
- **Hard purge**: Delete object from cache immediately. Next request gets a cache miss, hits origin. Risk: thundering herd if the object is hot.
- **Soft purge**: Mark as `stale` instead of deleting. Serve stale content while asynchronously revalidating from origin with `If-None-Match: etag`. If origin returns `304 Not Modified`, refresh TTL. If `200 OK`, replace content. No thundering herd.

---

## Interviewer Questions by Level

**Junior**:
- What is a CDN and why does it reduce latency?
- What is a cache hit vs a cache miss? What happens on a cache miss?
- What does the `Cache-Control: max-age=86400` header tell the CDN?

**Mid-level**:
- How does Anycast routing work? How does a request automatically go to the nearest PoP?
- What is the thundering herd problem on cache miss? How do you solve it?
- Compare soft purge vs hard purge. When would you use each?

**Senior**:
- Design the cache invalidation system for global propagation in < 60 seconds across 250 PoPs.
- How would you handle HTTPS certificate management for thousands of customer domains?
- Design the CDN's private backbone network — how do you route miss traffic from edge to origin faster than the public internet?
- How would you implement DDoS mitigation at the CDN layer?

---

## Back-of-Envelope Estimation

**Scale inputs (given in NFRs):**
- 1B users globally; 10 PB content; 10M requests/sec peak; 99.9% cache hit rate

**Request distribution:**
- 10M requests/sec globally, spread across 200 PoPs (Points of Presence)
- Average: 10M ÷ 200 = **50K requests/sec per PoP**
- Peak PoP (North America, EU): 3× average = **~150K requests/sec per PoP**
- Each request: HTTP/2, avg response size 100 KB (mix of images, JS, CSS, video segments)
- Peak bandwidth per PoP: 150K × 100 KB = **~15 GB/sec per top PoP**
- Need 10× 100 GbE NICs per PoP (each handles 12.5 GB/sec max) = 120 GbE total ingress/egress

**Cache storage per PoP:**
- 10 PB total content; 99.9% hit rate → 0.1% misses → origin fetch for 0.1% of 10M = 10K origin requests/sec
- Zipf distribution: top 1% of content serves 50% of requests; top 10% serves 80%
- Store top 10% of 10 PB = **1 PB per PoP** — impossible (1 PB SSD per PoP)
- Reality: store enough to achieve 99.9% hit rate for traffic reaching that PoP
- Each PoP serves regional traffic: 10 PB ÷ 200 PoPs × regional popularity = **~50 TB per PoP** of cached content achieves 99.9% hit rate for local traffic
- 50 TB at $100/TB (NVMe SSD): **~$5K hardware per PoP** for cache storage

**Origin shield:**
- Without origin shield: 10K origin requests/sec × 200 PoPs = **2M origin requests/sec** — would overwhelm origin
- With origin shield (one PoP per region designated as shield): 10K misses/sec across 200 PoPs → consolidated at ~20 shield PoPs → **500 origin requests/sec per shield** → origin sees **~10K aggregate requests/sec** — manageable

**Cache hit rate arithmetic:**
- 99.9% hit rate at 10M requests/sec → 10K misses/sec hit origin
- Average origin fetch: 50 ms (round trip + server processing)
- 10K requests × 50ms = 500 core-seconds/sec of origin capacity needed → **~500 origin server cores** to handle misses at <100ms
- For a 1 GB video file: split into 2-second segments (~4 MB each); cache segments independently → popular video = popular segments; first segment (bytes 0-4MB) cached more than later segments

**BGP anycast routing:**
- 200 PoPs all announce the same IP prefix (anycast)
- User's DNS resolver returns the same IP for all users
- BGP routing naturally sends each user to the geographically nearest PoP
- Latency: user → nearest PoP = ~5–30ms (within region); user → origin = ~100–300ms (cross-continent)
- 99.9% of requests served at PoP latency; only 0.1% incur origin latency

**Architecture decisions driven by these numbers:**
- **Origin shield tier between edge PoPs and origin**: Without it, 10K misses/sec × 200 PoPs = 2M origin requests/sec. Origin would need thousands of servers purely for CDN misses. Origin shield consolidates misses at 20 regional hubs: the shield checks its own cache first; only a true global miss (not in any shield) reaches origin. This reduces origin load by **200× (2M → 10K/sec)** for a cold cache, and higher as shields warm up.
- **50 TB SSD cache per PoP**: The Zipf distribution means a small fraction of content drives most traffic. 50 TB of local cache (the popular "long tail" for that region) achieves 99.9% hit rate. Going to 500 TB per PoP would improve hit rate from 99.9% to 99.99% but at 10× the hardware cost. The 0.09% improvement (900 fewer origin requests/sec globally) doesn't justify 10× more SSDs. The marginal value of cache storage follows diminishing returns sharply after the 99.9% threshold.
- **BGP anycast for automatic failover**: If a PoP goes down, BGP withdraws its route announcements. Within ~30 seconds (BGP convergence), traffic reroutes to the next nearest PoP. No DNS change needed, no client-side failover logic. The 50K requests/sec from the failed PoP redistribute to neighboring PoPs — each absorbs ~5K additional requests/sec (spreading to 10 neighboring PoPs), staying within their capacity headroom.

---

## Related

**Concepts used in this design**

- [CDN](../../02-building-blocks/01-networking/05-cdn.md)
- [Caching Layer](../../02-building-blocks/02-performance/01-caching-layer.md)
- [Global Distribution](../../03-scaling/04-global-distribution.md)
- [Consistent Hashing](../../02-building-blocks/03-data-partitioning/03-consistent-hashing.md)

**Practice next**

- [Distributed Cache](../03-hard/distributed-cache.md)
- [YouTube](../02-medium/youtube.md)

YouTube is the canonical workload a CDN is built to serve.

**Frameworks**: [HLD Template](../../07-interview-templates/01-frameworks/01-hld-template.md) · [Capacity Estimation](../../07-interview-templates/02-cheat-sheets/02-capacity-estimation.md) · [Trade-offs Cheat Sheet](../../07-interview-templates/02-cheat-sheets/01-trade-offs-cheat-sheet.md)
