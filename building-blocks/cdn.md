# CDN (Content Delivery Network)

> **Geographically distributed edge caches that serve content close to users for lower latency and higher availability.**

---

## 1. Concept Overview

A **CDN** is a network of edge servers (points of presence, PoPs) that cache and serve content. Users are directed to the nearest (or least-loaded) edge, so content is delivered with lower latency and less load on the origin.

**Why it exists**: Distance and network hops add latency. Caching at the edge reduces round-trip time and offloads the origin.

---

## 2. Core Principles

### Push vs pull

| Mode | How | Use case |
|------|-----|----------|
| **Push (proactive)** | You upload assets to the CDN; CDN distributes to edges | Known set of assets (e.g. static site, video library) |
| **Pull (on-demand)** | First request to edge → miss → fetch from origin → cache → serve | Dynamic or large catalog; cache on first request |

### What gets cached

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

---

## 3. Real-World Usage

- **CloudFront (AWS), Cloudflare, Fastly, Akamai**: Generic CDN for static and dynamic content.
- **Video**: Netflix, YouTube use CDNs for video segments; often multi-tier (edge → regional → origin).

---

## 4. Trade-offs

| Aspect | Pros | Cons |
|--------|------|------|
| **Edge caching** | Low latency; offload origin | Staleness; invalidation or TTL required |
| **Push** | Predictable; no origin hit after push | Must upload and manage distribution |
| **Pull** | Simple; only popular content cached | First request per edge is slow; origin must handle misses |
| **Cost** | Pay for egress from CDN (often cheaper than origin egress) | Invalidation and requests can add cost |

**When to use**: Static assets, public APIs or pages that benefit from global low latency.  
**When not**: Highly personalized or real-time data that cannot be cached at edge.

---

## 5. Failure Scenarios

| Scenario | Mitigation |
|----------|------------|
| Edge down | CDN routes to next nearest edge; origin as fallback |
| Origin down | Cached content still served; uncacheable requests fail (503) |
| Stale content | Versioned URLs; purge API; short TTL for dynamic |
| DDoS / abuse | CDN absorbs volumetric attacks; rate limit and WAF at edge |

---

## 6. Performance Considerations

- **Latency**: Edge close to user → low RTT; cache hit → no origin round-trip.
- **Throughput**: CDN scales horizontally; origin sees only miss traffic.
- **Invalidation**: Purge can be global but not instant; use TTL + versioned URLs when possible.

---

## 7. Implementation Patterns

- **Static assets**: Long TTL; immutable URLs (hash or version in path).
- **Dynamic**: Short TTL or `no-cache` with revalidation; or don’t cache.
- **API**: Cache GET by path + query (or key) where safe; avoid caching personalized or sensitive data.

---

## Quick Revision

- **Purpose**: Low latency and origin offload by caching at edge.
- **Push**: You upload; CDN distributes. **Pull**: First request pulls from origin; then cached.
- **Cache control**: Versioned URLs for static (long TTL); purge or TTL for updates.
- **Failure**: Multiple edges; origin fallback; CDN can absorb DDoS.
- **Interview**: “We use a CDN for static assets and redirect pages; pull model with long TTL and versioned URLs so we don’t need to purge on deploy. For dynamic API we use short TTL or no-cache.”

**For deeper caching strategies and Cache-Control**, see [core-concepts/caching-cdn.md](../core-concepts/caching-cdn.md).
