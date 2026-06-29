---
module: 02-building-blocks
status: unread
tags: [02-building-blocks, system-design, building-blocks]
---
# CDN (Content Delivery Network)

> **Geographically distributed edge caches that serve content close to users for lower latency and higher availability.**

---

## Why a CDN Exists

**Question**: Your origin server is in us-east-1 (Virginia). A user in Mumbai loads your home page. The page has 50 static assets (images, JS, CSS). Each asset requires a round-trip to Virginia: ~180ms RTT × 50 assets = 9 seconds of pure network time, before any server processing. Even with HTTP/2 multiplexing, the single RTT to Virginia dominates. Your user in Mumbai experiences a 9-second blank screen. How do you serve content to global users at local-network speeds?

**Physical constraint**: The speed of light in fiber optic cable is approximately 200,000 km/sec. Mumbai to Virginia is roughly 13,000 km. One way: 65ms. Round trip: 130ms. This is a physical constant — no amount of software optimization can make a round-trip to Virginia take less than 130ms from Mumbai. The only solution is to not go to Virginia — serve the content from a machine in Mumbai instead.

**Minimal solution**: Deploy a caching server in Mumbai. On the first request for each asset, the Mumbai server fetches from Virginia and stores a local copy. Every subsequent request is served from Mumbai at ~5ms RTT. The origin sees only first-request cache-miss traffic. This is the pull model: cache on first demand.

**Production generalization**: A CDN is a globally distributed network of pull-cache servers (edge nodes / PoPs) run by a third party (Cloudflare, CloudFront, Akamai, Fastly), so you don't manage the Mumbai server yourself. Anycast routing directs each user to the nearest PoP automatically. At scale (Netflix, YouTube), multi-tier CDNs add regional caches between the origin and edge — so even a cache miss at the Mumbai edge doesn't reach Virginia, only the nearest regional PoP (Singapore). CDN cache hit rates of 95–99% are achievable for static content, meaning your origin handles 1–5% of total traffic.

---

## File Mindmap

```
CDN (Content Delivery Network)
├── Why It Exists
│   ├── Problem → origin in us-east-1; user in Mumbai; 50 assets × 180ms RTT = 9s blank screen
│   └── Forces → speed of light in fiber ≈ 200,000 km/s; Mumbai→Virginia ~130ms RTT; irreducible physics
├── Core Mechanism
│   ├── Edge nodes (PoPs) → globally distributed caching servers
│   ├── Pull model (on-demand) → first request → cache miss → fetch from origin → cache → serve
│   │   └── Subsequent requests → served from edge at ~5ms vs ~150ms to origin
│   └── Anycast routing → DNS directs each user to nearest/fastest PoP automatically
├── Push vs Pull
│   ├── Push (proactive)
│   │   ├── You upload assets to CDN; CDN distributes to all edges before first request
│   │   └── Best for → small known set: marketing pages, software binary releases
│   └── Pull (on-demand)
│       ├── Edge fetches on first miss; caches for subsequent requests
│       └── Best for → large catalogs; only popular content gets cached (long tail stays on origin)
├── What Gets Cached
│   ├── Static → images, JS, CSS, fonts (long TTL, e.g. 1 year + versioned URL)
│   ├── Dynamic → API responses, HTML (short TTL 30s–5min or ETag revalidation)
│   └── Streaming → HLS/DASH video segments (immutable by design; TTL = segment duration)
├── Multi-Tier CDN (Netflix model)
│   ├── Origin (Virginia) → Regional PoP (Singapore) → Edge PoP (Mumbai) → User
│   └── Edge miss hits regional first; origin only sees miss traffic from regional PoPs
├── Cache Control Patterns
│   ├── Versioned URLs → content hash in filename; new deploy = new URL = guaranteed cold miss
│   │   └── TTL: 1 year; no purge needed; old file expires naturally
│   ├── Short TTL → 30s–5min for dynamic content; ETag + stale-while-revalidate
│   └── Personalized content → bypass CDN entirely; never cache user-scoped data at edge
├── Real-World Providers
│   ├── Cloudflare → global PoPs; WAF; cache purge ~150ms globally
│   ├── CloudFront (AWS) → S3/ALB integration; Lambda@Edge for dynamic logic
│   ├── Fastly → real-time purge; VCL customization
│   └── Akamai → largest PoP network; enterprise-grade
├── Failure Scenarios
│   ├── Edge down → CDN routes to next nearest PoP; origin as ultimate fallback
│   ├── Origin down → stale-while-revalidate / stale-if-error serves cached content; uncacheable requests fail (503)
│   ├── Stale content → versioned URLs for static; short TTL or purge API for dynamic
│   └── DDoS → CDN absorbs volumetric attacks at edge; WAF blocks application-layer attacks
├── Performance Impact
│   ├── Latency → Mumbai edge hit ~5ms vs ~150ms origin → 30× improvement
│   ├── Throughput → 95–99% hit rate for static; origin handles 1–5% of traffic
│   └── Invalidation → Cloudflare purge ~150ms globally; not atomic; rely on versioned URLs for correctness
├── Trade-offs
│   ├── Pros → global low latency; origin offload; DDoS absorption; scales infinitely
│   └── Cons → staleness / invalidation complexity; CDN egress cost; personalized content cannot be cached
└── Interview Angles
    ├── "How do you avoid cache invalidation on deploys?" → content-hashed URLs; new hash = new cache entry
    ├── "What happens if origin goes down?" → stale-if-error serves cached content; dynamic pages fail
    ├── "Push vs pull — which do you choose?" → pull for large catalogs; push for known small asset sets
    └── Follow-up: "How does multi-tier CDN help vs single-tier?" → regional PoP absorbs miss traffic; origin rarely hit
```

---

## 1. Concept Overview

A **CDN** is a network of edge servers (points of presence, PoPs) that cache and serve content. Users are directed to the nearest (or least-loaded) edge, so content is delivered with lower latency and less load on the origin.

**Why it exists**: Distance and network hops add latency. Caching at the edge reduces round-trip time and offloads the origin.

**Real-life analogy — Starbucks**: The main coffee roasting facility is in Seattle (your origin server). Shipping every cup of coffee from Seattle to a customer in Mumbai would take days. Instead, Starbucks opens stores on every corner around the world (edge nodes / PoPs). You get your coffee locally in Mumbai, not from Seattle. When the Mumbai store runs out of a blend, they restock from the nearest regional warehouse, which in turn restocks from Seattle. You, the customer, always get fast service. Seattle only deals with wholesale orders — not individual cups.

The CDN edge is your local Starbucks: it serves the content you cached there. Seattle (your origin) only gets involved when the edge has a miss (runs out of stock).

---

## 2. Core Principles

### Push vs Pull

| Mode | How | Use case |
|------|-----|----------|
| **Push (proactive)** | You upload assets to the CDN; CDN distributes to edges | Known set of assets (e.g. static site, video library) |
| **Pull (on-demand)** | First request to edge → miss → fetch from origin → cache → serve | Dynamic or large catalog; cache on first request |

**Push analogy**: Starbucks HQ decides to launch a new Pumpkin Spice Latte for fall. Before the launch date, they pre-ship the syrup and cups to every single store worldwide. On day 1, every store already has inventory — no reordering from Seattle. This is push: you know exactly what content exists and you proactively distribute it to all edges before any user asks.

**Pull analogy**: A new regional drink is not pre-distributed. The first customer in Mumbai who orders it triggers the store to call the regional warehouse (origin), which ships one unit. From then on, the Mumbai store stocks it locally. This is pull: edges only cache content after a real user request triggers the first miss. The first requester per edge experiences the origin round-trip; everyone after gets the cached copy.

**When pull beats push**: You have millions of assets (a large image library, a video catalog). Pre-distributing all of them would fill every edge unnecessarily. Pull ensures only popular content gets cached — the long tail stays on origin.

**When push beats pull**: You have a small set of known assets (a marketing landing page, a software binary release) that you know millions of users will hit simultaneously. Pull would mean the first user per edge pays the miss penalty, and the origin absorbs a burst. Push pre-warms every edge.

### What Gets Cached

- **Static**: Images, JS, CSS, fonts (long TTL, e.g. 1 year with versioned URLs).
- **Dynamic**: API responses, HTML (short TTL or cache-control: no-cache with validation).
- **Streaming**: Video chunks (HLS/DASH segments) at edge.

### Architecture

```
  User (Asia) ──▶ CDN Edge (Mumbai) ──▶ Cache HIT → response
                        │
                        │ MISS
                        ▼
  User (US) ──▶ CDN Edge (Virginia) ──▶ Origin (e.g. S3 / app server)
```

**Multi-tier CDN (Netflix model)**: Origin → Regional PoP → Edge PoP → User. The Mumbai edge first checks its own cache, then the nearest regional cache (Singapore), then origin. This way Seattle is rarely needed even for initial misses — the regional PoP often has it.

---

## 3. Real-World Usage

- **CloudFront (AWS), Cloudflare, Fastly, Akamai**: Generic CDN for static and dynamic content.
- **Video**: Netflix, YouTube use CDNs for video segments; often multi-tier (edge → regional → origin).

**CloudFront with versioned URLs** (deploy pattern):

```
# Old deploy
https://cdn.example.com/app.a1b2c3.js   ← TTL: 1 year

# New deploy — new hash, new URL, fresh cache automatically
https://cdn.example.com/app.d4e5f6.js   ← TTL: 1 year
```

No cache purge needed. The old file expires naturally. The new file is a brand-new URL that the CDN has never seen — it pulls from origin on first request and caches for a year. This is the versioned URL pattern and it is the standard approach for static assets in production.

---

## 4. Trade-offs

| Aspect | Pros | Cons |
|--------|------|------|
| **Edge caching** | Low latency; offload origin | Staleness; invalidation or TTL required |
| **Push** | Predictable; no origin hit after push | Must upload and manage distribution; wasted space for unpopular content |
| **Pull** | Simple; only popular content cached | First request per edge is slow (miss penalty); origin must handle miss bursts |
| **Cost** | Pay for egress from CDN (often cheaper than origin egress) | Invalidation API calls and per-request fees can add cost |

**When to use**: Static assets, public APIs or pages that benefit from global low latency.  
**When not**: Highly personalized or real-time data that cannot be cached at edge (e.g. a user's private dashboard, live auction prices).

---

## 5. Failure Scenarios

| Scenario | Mitigation |
|----------|------------|
| Edge down | CDN routes to next nearest edge; origin as fallback |
| Origin down | Cached content still served (stale-while-revalidate); uncacheable requests fail (503) |
| Stale content | Versioned URLs; purge API; short TTL for dynamic |
| DDoS / abuse | CDN absorbs volumetric attacks; rate limit and WAF at edge |

**Origin down — the Mumbai store during a Seattle outage**: If the Seattle roasting facility burns down but the Mumbai store already has inventory, customers in Mumbai can still get coffee. The store serves from its existing stock until it runs out. This maps to `stale-while-revalidate` or `stale-if-error` cache headers: the CDN continues serving stale cached content even after origin goes down, giving you time to recover. Uncacheable requests (dynamic personalized pages) fail because there's nothing cached to fall back on.

**Stale content — the wrong menu**: If Mumbai got last month's menu and Seattle updated it, customers order an item that was discontinued. Solution: short TTL on dynamic content, or use versioned URLs for static so the new URL is a guaranteed miss (forces cache refresh).

---

## 6. Performance Considerations

- **Latency**: Edge physically close to user → low RTT. A cache hit means the user never reaches your origin datacenter. A request from Mumbai to Virginia origin adds ~150ms one-way. A Mumbai edge hit adds ~5ms.
- **Throughput**: CDN scales horizontally across hundreds of edge nodes; your origin only sees cache miss traffic. For a high-traffic site, CDN can absorb 95%+ of requests.
- **Invalidation**: Purge can be near-instant (Cloudflare ~150ms globally) but not truly atomic. For correctness, rely on versioned URLs for static and TTL for dynamic rather than on-demand purging.

---

## 7. Implementation Patterns

- **Static assets**: Long TTL (1 year); immutable URLs (content hash in filename). No invalidation needed — new hash = new URL.
- **Dynamic HTML**: Short TTL (30s–5min) or `Cache-Control: no-cache` with `ETag` revalidation. CDN serves cached while revalidating in background.
- **API GET responses**: Cache by path + query string where safe; never cache authenticated or personalized responses unless scoped by user token (and even then, be careful).
- **Video streaming**: Cache HLS/DASH segments at edge; segments are immutable by design (fixed-duration chunks with timestamp URLs).

---

## Quick Revision

- **Purpose**: Low latency and origin offload by caching at geographically close edge nodes.
- **Push**: You distribute proactively. **Pull**: Edge fetches on first miss, caches for subsequent requests.
- **Cache control**: Versioned URLs for static (long TTL, no purge needed); short TTL or purge for dynamic.
- **Failure**: Multiple edges; origin fallback via stale-if-error; CDN absorbs DDoS volumetrically.
- **Starbucks analogy**: Origin = Seattle roastery; edges = local stores; push = pre-shipping seasonal inventory; pull = ordering only when a customer asks.
- **Interview**: "We use a CDN for static assets and redirect pages; pull model with long TTL and content-hashed URLs so we don't need to purge on deploy. For dynamic API responses we use a 30-second TTL with stale-while-revalidate. For personalized data we bypass the CDN entirely."

**For deeper caching strategies and Cache-Control**, see [02-building-blocks/caching-layer.md](caching-layer.md).
