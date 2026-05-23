---
module: 05-hld-problems
topic: Hard
status: unread
tags: [05-hld-problems, system-design, hard]
---
# Design a CDN (Content Delivery Network)

> Serve static and dynamic content to global users with < 20 ms latency and petabyte-scale throughput by caching content at geographically distributed edge servers close to users.

---

## File Mindmap

```
CDN Design
├── Why It Exists
│   ├── Problem → Users in Tokyo serving content from Virginia get 180 ms RTT minimum
│   └── Forces → Speed of light; single origin can't absorb global peak traffic
├── Core Concepts
│   ├── PoP → Point of Presence; edge data center close to users
│   ├── BGP Anycast → same IP announced from multiple locations; traffic routed to nearest PoP
│   ├── Edge cache → HTTP cache at PoP; serves content without hitting origin
│   ├── Pull CDN → content fetched from origin on first miss; cached at edge
│   └── Push CDN → content pre-loaded to edge before any request
├── Strategies / Types
│   ├── Pull CDN → easy, eventually consistent → blog, image, video
│   └── Push CDN → pre-warmed, zero cold miss → large file releases, software updates
├── Trade-offs
│   ├── Pro: Latency reduction × 10; origin offload × 95%
│   └── Con: Cache invalidation complexity; TLS handshake at edge adds ops burden
├── Failure Modes
│   └── PoP failure → BGP withdraws announcement; traffic reroutes to next nearest PoP
└── Interview Angles
    └── Cache invalidation → the hardest part of CDN design
```

---

## 1. Why a CDN Exists

**Question**: Netflix serves 15 petabytes of video per day globally. Their origin servers are in AWS us-east-1. A user in Mumbai requesting a 4K stream from Virginia has a minimum round-trip latency of ~170 ms (speed of light over fiber: ~200,000 km/s; ~8,500 km one-way). With TCP slow start, getting the first segment of a video stream to Mumbai from Virginia takes ~1-2 seconds. The user's startup buffer is empty. Video stutters.

**Physical constraint**: The speed of light imposes a minimum latency of `distance / 200,000 km/s`. Virginia to Mumbai: 8,500 km / 200,000 = 42.5 ms one-way, 85 ms RTT minimum. In practice, internet routing adds 2-3× overhead: realistic RTT is 170-250 ms. No engineering can reduce this below the physical limit — the only solution is to move the content closer.

**Minimal solution**: Deploy a caching server in Mumbai. On the first request for a video segment, the Mumbai server fetches it from Virginia (one-time 170 ms penalty), caches it, and serves all subsequent requests in < 5 ms (user is ~50 km from the Mumbai PoP). Cache hit rate for popular content (top 1% of videos = 99% of views) quickly reaches 95%+.

**Production generalization**: Deploy hundreds of PoPs globally. Use BGP anycast so all PoPs share the same IP — traffic automatically routes to the geographically nearest. Add TLS termination at the edge to reduce TLS handshake latency. Implement a two-tier cache hierarchy (edge PoP → regional shield → origin) to maximize cache hit rate even for long-tail content. Add programmable edge logic (edge compute) for request manipulation, auth, and A/B testing without round-tripping to origin.

---

## 2. Core Concepts / How It Works

### DNS-Based Routing vs BGP Anycast

**DNS-based routing** (used by Akamai, Cloudflare partial):
```
User → Recursive DNS server → CDN authoritative DNS
         CDN DNS detects user's IP geo → returns IP of nearest PoP
         User connects to that PoP IP → TCP + TLS → content served
```
Trade-off: DNS TTL creates stickiness — if a PoP fails, users pointing to it take up to TTL (60-300 s) to reroute. DNS geo-detection is imprecise (resolver IP ≠ user IP for large ISP resolvers).

**BGP Anycast** (used by Cloudflare, Fastly):
```
All PoPs announce the same IP block (e.g., 104.16.0.0/12)
BGP routing protocol selects the route with fewest hops → nearest PoP
User sends SYN to 104.16.x.x → BGP delivers it to nearest PoP automatically
```
Trade-off: Failover is BGP convergence time (~30-60 s). TCP connections are disrupted on failover (stateful connection migrates poorly with anycast). Better for short-lived connections (HTTP/1.1, UDP/QUIC); trickier for long TCP connections.

### PoP Architecture

```
   ┌─────────────────── PoP (e.g., Mumbai) ─────────────────────┐
   │                                                              │
   │  BGP Router ──→ Load Balancer ──→ Edge Cache Servers (N)   │
   │                                         │                   │
   │                                    SSD Cache               │
   │                                    (1-10 TB per server)     │
   │                                         │                   │
   │                                    Regional Shield          │
   │                                    (2nd tier cache)         │
   └──────────────────────────────────────────────────────────────┘
                                             │ cache miss (< 5%)
                                             ↓
                                        Origin (AWS S3, origin servers)
```

**Edge cache servers**: 20-100 servers per major PoP. Each has 1-10 TB SSD + 100 Gbps NIC. Serve content from local SSD in < 1 ms.

**Regional shield**: A second caching tier between edge PoPs and origin. All PoPs in a region (e.g., Asia-Pacific) check the regional shield before going to origin. This "collapses" cache misses: if 10 Asia-Pacific PoPs miss for the same URL simultaneously, only 1 request goes to origin (the other 9 wait or get the regional shield response). Dramatically reduces origin traffic for long-tail content.

### Pull CDN vs Push CDN

**Pull CDN**:
```
First request for /image/photo.jpg:
  Edge cache: MISS → fetch from origin → cache locally → respond to user

Subsequent requests:
  Edge cache: HIT → respond from local SSD (< 1 ms)

Cache TTL: set by origin via Cache-Control: max-age=86400
```

**Push CDN**:
```
Publisher uploads content to CDN API:
  CDN API → propagates to all PoPs → content pre-cached everywhere

First user request anywhere:
  Edge cache: HIT immediately (no cold miss)

Use case: game patches, software releases, known-in-advance large file distributions
```

| Dimension          | Pull CDN                            | Push CDN                              |
|--------------------|-------------------------------------|---------------------------------------|
| Cold miss          | Yes (first request hits origin)     | None (pre-loaded)                     |
| Freshness          | Controlled by Cache-Control TTL     | Publisher controls; manual invalidate |
| Operational effort | Low (no pre-population)             | High (must push updates everywhere)   |
| Best for           | Dynamic/user-generated content      | Large static files, software releases |
| Storage efficiency | Only stores what's requested        | All content on all PoPs (may waste space) |

### Cache Hierarchy and Miss Rate

```
PoP hit rate: 90% for popular content
Regional shield hit rate (of PoP misses): 80%
Total origin traffic: 10% × 20% = 2% of total requests

For Netflix (15 PB/day):
  Origin traffic: 15 PB × 2% = 300 TB/day → manageable
  Edge serves: 14.7 PB/day without touching origin
```

### TLS Termination at Edge

```
User ──[TLS handshake]──→ Edge PoP (terminates TLS)
                              │ internal network (may be HTTP or internal TLS)
                              ↓
                          Origin (no TLS handshake RTT from user)

TLS 1.3 handshake: 1 RTT (vs 2 RTT for TLS 1.2)
  Mumbai edge is 5 ms from user → TLS costs 5 ms (1 RTT to edge)
  Without CDN: TLS to Virginia origin costs 170 ms (1 RTT)
  Savings: 165 ms just from TLS termination at edge
```

The CDN also handles TLS certificate management, renewal (Let's Encrypt / ACME), and OCSP stapling.

### Cache Invalidation

The hardest part. Three approaches:

| Strategy              | How it works                                    | Latency to invalidate | Complexity |
|-----------------------|-------------------------------------------------|-----------------------|------------|
| TTL expiry            | Wait for max-age to expire                      | Up to TTL duration    | None       |
| Purge API             | CDN API call → propagates to all PoPs           | 5-30 seconds          | Low        |
| Cache key versioning  | Embed version in URL: `/style.v42.css`          | Instant (new URL = new cache) | Medium (requires URL management) |
| Surrogate key / tags  | Tag cached objects; invalidate by tag           | 5-30 seconds          | High (requires tagging at origin) |

**Best practice**: Use cache key versioning (URL-embedded version) for static assets (CSS, JS). Use Purge API for content that must be invalidated immediately (news articles, product updates). Use short TTL (60-300 s) for dynamic content that changes frequently.

---

## 3. Real-World Usage

| CDN           | Architecture highlights                                                       |
|---------------|-------------------------------------------------------------------------------|
| Cloudflare    | BGP anycast; 285 PoPs; edge compute (Workers) on V8 isolates                 |
| Akamai        | DNS-based routing; 340,000 servers; Intelligent Platform                      |
| AWS CloudFront| 450+ PoPs; tight S3/EC2 integration; Lambda@Edge for serverless edge compute |
| Fastly        | VCL (Varnish Configuration Language) for programmable caching; instant purge (150 ms) |
| Netflix Open Connect | Self-operated CDN; ISP partnerships to place servers inside ISP networks |

**Netflix Open Connect (OCF)**: Netflix operates its own CDN rather than using commercial CDNs. They place dedicated appliances (OCF Appliances, 100-200 TB SSD each) directly inside ISP networks (not in data centers). Traffic from ISP customers to Netflix never leaves the ISP network — 0 transit costs and lowest possible latency. Netflix pre-populates appliances nightly with the next day's predicted popular content using ML-based prediction models.

**Cloudflare Workers**: Instead of just caching, Cloudflare runs V8 JavaScript isolates at every PoP. This allows full request handling (auth, A/B testing, edge rendering) without any round-trip to origin. Cold start: < 5 ms. This is the leading edge of "edge compute."

---

## 4. Trade-offs

| Dimension          | Pro                                                    | Con                                                         |
|--------------------|--------------------------------------------------------|-------------------------------------------------------------|
| Latency            | 5-15 ms at edge vs 50-250 ms to origin                 | First-miss latency is worse (edge + origin RTT)             |
| Throughput         | Each PoP has 100 Gbps+ aggregate capacity             | Coordinating 450 PoPs has operational complexity            |
| Origin offload     | 95-98% of traffic served from cache                    | Cache misses for dynamic/personalized content still hit origin |
| Cost               | Reduces origin egress cost; CDN egress cheaper than origin egress | CDN fees add up at petabyte scale (~$0.01-0.08/GB) |
| Security           | DDoS absorption at edge; WAF integration               | CDN is a MITM for TLS — security depends on CDN provider trust |
| Freshness          | Configurable TTL; purge API                            | Stale content risk; cache invalidation is complex           |
| Personalization    | Static assets cached perfectly                         | Personalized content (user-specific responses) often bypasses CDN cache |

---

## 5. Failure Scenarios

| Scenario                      | Symptom                                              | Mitigation                                                     |
|-------------------------------|------------------------------------------------------|----------------------------------------------------------------|
| PoP failure (hardware)        | Users near that PoP see increased latency            | BGP withdraws announcement; traffic reroutes to next nearest PoP |
| BGP convergence delay         | 30-60 s of disruption during rerouting               | Pre-announce backup routes; use anycast with multiple paths   |
| Cache poisoning               | Attacker injects malicious content into CDN cache    | Strict origin validation; signed URLs; CSP headers            |
| Origin overload on cache flush| Purging a popular object causes thundering herd      | Stagger purges; use request coalescing (one origin req while others wait) |
| TLS certificate expiry at edge| Users get cert error; traffic drops to zero          | Automated ACME renewal; monitor 30 days before expiry        |
| Hot object (viral content)    | Single object overwhelms one PoP's bandwidth         | Object is replicated across all PoP servers; CDN handles internally |
| DDoS at edge                  | PoP capacity saturated                               | Blackhole routing; rate limiting at edge; scrubbing center upstream |
| DNS hijacking                 | Users redirected to wrong PoP                        | DNSSEC; monitoring DNS responses; fail-safe origin access     |

### Request Coalescing (Cache Stampede Prevention)

```
Popular object expires simultaneously for 10,000 concurrent users:

Without coalescing: 10,000 requests to origin simultaneously → origin overload

With request coalescing:
  - First request: MISS → forward to origin → hold response
  - Requests 2-9,999 arriving during origin fetch: WAIT (queued)
  - Origin responds → cache populated → all 9,999 waiting requests served from cache
  - Origin sees: 1 request (not 10,000)
```

This is implemented at the edge server level (Varnish, Nginx proxy_cache_lock).

---

## 6. Performance Considerations

### Scale Estimation

```
Netflix video delivery:
  15 PB/day = 15 × 10^15 bytes / 86,400 s = 1.7 Tbps average
  Peak (3× average): ~5 Tbps
  With 450 PoPs: 5 Tbps / 450 ≈ 11 Gbps per PoP average
  Each PoP: 20-100 servers × 100 Gbps NIC = 2-10 Tbps capacity
  → Significant headroom per PoP

YouTube (Google CDN):
  ~1 billion video hours/day = avg 8K Tbps aggregate
  Google CDN: 100+ PoPs with massive bandwidth
```

### Cache Hit Rate Economics

```
Assumption: 1 TB/day in origin traffic, $0.08/GB origin egress
Without CDN: 1 TB/day × 30 days = 30 TB/month × $0.08 = $2,400/month

With CDN (95% hit rate):
  Origin egress: 30 TB × 5% = 1.5 TB × $0.08 = $120/month (origin)
  CDN fees: 30 TB × $0.04/GB = $1,200/month
  Total: $1,320/month → 45% savings
  
At 1 PB/month scale:
  Without CDN: $82,000/month
  With CDN (5% origin): $4,100 + $41,000 CDN = $45,100 → 45% savings still
  (CDN fees decrease per-GB at volume; often $0.005-0.01/GB at scale)
```

### Latency Breakdown

```
User (Mumbai) → Edge PoP (Mumbai, 5 ms RTT)
  Cache HIT:
    Network: 5 ms
    TLS: 5 ms (1 RTT, TLS 1.3)
    Processing: 1 ms
    Total: 11 ms  ✓

  Cache MISS (pull from origin, Virginia):
    Edge → Regional Shield: 20 ms
    Shield → Origin: 150 ms (Mumbai to Virginia)
    Origin processing: 5 ms
    Total miss path: 175 ms (paid once; cached for TTL duration)
    Subsequent requests: 11 ms
```

---

## 7. Implementation Patterns

### CDN URL Design with Cache Key Versioning

```java
public class AssetUrlBuilder {

    private final String cdnBaseUrl;    // e.g., "https://cdn.example.com"
    private final String contentVersion; // from build pipeline, e.g., "git-sha-abc1234"

    // Versioned static assets: infinite TTL safe because URL changes on update
    public String buildStaticAssetUrl(String path) {
        // /static/style.css → https://cdn.example.com/static/v/abc1234/style.css
        return String.format("%s/static/v/%s%s", cdnBaseUrl, contentVersion, path);
    }

    // Content-addressed: URL contains hash of content → perfect cache key
    public String buildContentAddressedUrl(String path, String contentHash) {
        // https://cdn.example.com/assets/sha256/abc.../photo.jpg
        return String.format("%s/assets/sha256/%s/%s",
            cdnBaseUrl, contentHash, FilenameUtils.getName(path));
    }

    // Time-bucketed URLs for content that changes hourly (e.g., trending data)
    public String buildTimeBucketedUrl(String path) {
        String hourBucket = LocalDateTime.now(ZoneOffset.UTC)
            .truncatedTo(ChronoUnit.HOURS)
            .format(DateTimeFormatter.ofPattern("yyyyMMddHH"));
        // Cache key includes hour → auto-expires every hour without purge
        return String.format("%s/hourly/%s%s", cdnBaseUrl, hourBucket, path);
    }
}
```

### Origin Response Headers for CDN Caching

```java
@RestController
public class VideoController {

    @GetMapping("/video/{id}/segment/{segment}")
    public ResponseEntity<Resource> getVideoSegment(
            @PathVariable String id,
            @PathVariable String segment) {

        Resource resource = videoService.getSegment(id, segment);

        return ResponseEntity.ok()
            // Cache at CDN for 24 hours; allow stale for 1 hour during revalidation
            .header("Cache-Control", "public, max-age=86400, stale-while-revalidate=3600")
            // ETag for conditional requests (304 Not Modified)
            .header("ETag", "\"" + resource.getChecksum() + "\"")
            // Surrogate keys for tag-based invalidation (Fastly/Cloudflare)
            .header("Surrogate-Key", "video-" + id + " segment")
            // Vary header: CDN caches separate copies per Accept-Encoding
            .header("Vary", "Accept-Encoding")
            .body(resource);
    }

    @GetMapping("/user/{id}/profile")
    public ResponseEntity<UserProfile> getUserProfile(@PathVariable String id) {
        UserProfile profile = userService.getProfile(id);

        return ResponseEntity.ok()
            // Private: only user's browser caches; CDN must not cache
            .header("Cache-Control", "private, max-age=300")
            // Or: CDN bypass header (Cloudflare convention)
            .header("CDN-Cache-Control", "no-store")
            .body(profile);
    }
}
```

### Cache Invalidation via Purge API

```java
import java.net.http.*;
import java.net.URI;

public class CdnInvalidationService {

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final String cloudfrontDistributionId;
    private final CloudFrontClient cloudFrontClient;

    // Invalidate specific paths on CloudFront
    public String invalidatePaths(List<String> paths) {
        CreateInvalidationResponse response = cloudFrontClient.createInvalidation(
            CreateInvalidationRequest.builder()
                .distributionId(cloudfrontDistributionId)
                .invalidationBatch(InvalidationBatch.builder()
                    .callerReference(UUID.randomUUID().toString())
                    .paths(Paths.builder()
                        .quantity(paths.size())
                        .items(paths) // e.g., ["/images/product-123.jpg", "/api/product/123"]
                        .build())
                    .build())
                .build()
        );
        return response.invalidation().id(); // can poll for completion
    }

    // Fastly: instant purge by surrogate key tag (< 150 ms propagation)
    public void purgeByTag(String tag) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.fastly.com/service/{serviceId}/purge/" + tag))
            .header("Fastly-Key", fastlyApiKey)
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();
        HttpResponse<String> response = httpClient.send(request,
            HttpResponse.BodyHandlers.ofString());
        // 200 OK with {"status": "ok"} → purge queued
    }
}
```

### Edge Compute Logic (Cloudflare Workers pattern in Java concept)

```
// Pseudocode: logic that runs at the edge (in reality: JavaScript/WASM for Cloudflare Workers)
// Java equivalent using AWS Lambda@Edge

public class EdgeAuthHandler implements RequestHandler<CloudFrontEvent, CloudFrontEvent> {

    @Override
    public CloudFrontEvent handleRequest(CloudFrontEvent event, Context context) {
        CloudFrontRequest request = event.getRecords().get(0).getCf().getRequest();

        // 1. Auth check at edge — no round trip to origin
        String token = extractToken(request.getHeaders());
        if (!jwtValidator.isValid(token)) {
            return buildUnauthorizedResponse(); // 401 from edge; origin not touched
        }

        // 2. A/B test routing at edge
        String variant = abTestRouter.getVariant(request);
        request.getHeaders().put("x-ab-variant",
            List.of(Map.of("key", "x-ab-variant", "value", variant)));

        // 3. Geo-based content routing
        String country = request.getHeaders().getOrDefault("cloudfront-viewer-country",
            List.of(Map.of("value", "US"))).get(0).get("value");
        if ("CN".equals(country)) {
            request.setUri("/cn" + request.getUri()); // route to China-specific content
        }

        return event; // modified request forwarded to origin or edge cache
    }
}
```

---

## Quick Revision

- CDN solves the physical latency constraint: speed of light means you must move content closer
- PoP = Point of Presence; edge data center; typically 1-50 ms from end users
- BGP Anycast: same IP announced from all PoPs; BGP routes traffic to nearest PoP automatically
- DNS-based routing: CDN DNS returns nearest PoP IP; slower failover but handles TCP connections better
- Pull CDN: fetch from origin on first miss, cache locally; zero pre-configuration
- Push CDN: pre-load content before requests; zero cold misses; best for known large file releases
- Cache hierarchy: Edge PoP → Regional Shield → Origin; origin sees only 2-5% of total traffic
- Cache invalidation: versioned URLs (best), Purge API (5-30 s propagation), TTL expiry (simple)
- TLS termination at edge: TLS handshake RTT = edge RTT (5 ms) instead of origin RTT (170 ms)
- Request coalescing: only one origin request for a cache miss; others wait; prevents thundering herd
- Netflix Open Connect: ISP-deployed appliances; pre-populate nightly; 0 internet transit for top 95% of traffic
- Edge compute (Cloudflare Workers): run JS/WASM at PoP for auth, A/B testing, personalization without origin RTT

---

## See Also

- [02-building-blocks/cdn.md](../../02-building-blocks/cdn.md) — CDN as a building block
- [02-building-blocks/caching-layer.md](../../02-building-blocks/caching-layer.md) — caching fundamentals
- [02-building-blocks/load-balancers.md](../../02-building-blocks/load-balancers.md) — L4/L7 load balancing
- [05-hld-problems/02-medium/youtube.md](../02-medium/youtube.md) — video delivery system
- [05-hld-problems/02-medium/typeahead-search.md](../02-medium/typeahead-search.md) — CDN for prefix caching
- [01-foundations/networking.md](../../01-foundations/networking.md) — BGP, DNS, TCP fundamentals

---

## Interview Questions Asked

### Conceptual

**Q1: Explain how BGP anycast works and why it's better than DNS-based routing for CDNs.**

A: BGP anycast assigns the same IP address block to multiple PoPs globally. Each PoP's upstream router announces this IP prefix to the BGP routing table. BGP then selects the route with the lowest AS-path (fewest network hops) for each user's traffic, effectively routing each user to the closest PoP with no DNS overhead. The benefits over DNS-based: (1) no DNS resolution latency — the TCP SYN packet is routed to the nearest PoP before DNS even enters the picture; (2) no DNS caching staleness — failover happens at the BGP layer (30-60 s convergence) without waiting for DNS TTL expiry; (3) works at the IP packet level — any protocol (UDP, ICMP, QUIC) benefits, not just HTTP. The downside: BGP convergence takes 30-60 s vs DNS TTL of 30-60 s — similar, but BGP failover disrupts existing TCP connections (the SYN for the connection now goes to a different PoP than ongoing packets). This is why anycast works best with QUIC (connection migration handles IP changes) or short-lived HTTP/1.1 connections, rather than long-lived TCP streams.

**Q2: How does a CDN achieve a 95%+ cache hit rate when content is constantly being created?**

A: The 95%+ hit rate relies on the Pareto principle in content access: roughly 1% of content gets 99% of views. For video platforms, a new movie release gets millions of views in hours; a video posted 3 years ago gets 1 view/week. The CDN focuses its cache capacity on this "hot" 1%: (1) Large SSD capacity per PoP (1-10 TB) easily holds the top-1M most accessed objects; (2) LFU (Least Frequently Used) eviction keeps popular objects resident while evicting long-tail content; (3) Regional shields further catch long-tail content that a single PoP might miss — if 10 PoPs in Asia-Pacific each see 1 request for a niche video, the regional shield serves all 10 from one origin fetch; (4) Content prediction: Netflix pre-populates PoPs with content predicted to be popular tomorrow (ML models), so new releases have 0 cold-start misses. The remaining 2-5% of requests (truly unique/personalized content) appropriately bypasses the cache.

**Q3: What is TLS termination at the edge and what does it actually save?**

A: TLS termination at the edge means the CDN PoP handles the TLS handshake with the client, not the origin server. The client establishes an encrypted connection to the nearest PoP (e.g., 5 ms RTT for a user close to a PoP). The PoP may then connect to the origin over a persistent, pre-warmed TLS connection (or internal HTTP over private network). The savings: (1) TLS 1.3 handshake cost = 1 RTT to the TLS endpoint. With TLS at origin (Virginia), that's 170 ms per new connection. With TLS at edge (Mumbai), 5 ms. Saving: 165 ms per connection establishment. (2) Session resumption: the CDN PoP maintains TLS session tickets, so returning users skip the full handshake. (3) OCSP stapling: the PoP pre-fetches certificate revocation status and includes it in the handshake — eliminates the client's need to make a separate OCSP request (saves 50-200 ms). (4) HTTP/2 or HTTP/3 multiplexing: the CDN terminates HTTP/2 from clients and may use HTTP/1.1 to origin — the CDN translates protocol versions, allowing older origins to benefit from H2 multiplexing.

### Comparison / Trade-off

**Q: A startup asks: should we build our own CDN like Netflix Open Connect, or use a commercial CDN like Cloudflare? At what scale does the build-vs-buy decision flip?**

A: Use a commercial CDN until you are spending > $10M/year on CDN fees AND have the engineering resources to manage a global network. The math: (1) Commercial CDN: ~$0.01-0.08/GB at enterprise pricing. At 1 PB/month: $10,000-80,000/month. (2) Self-operated CDN: hardware (~$50K per server × 100 servers per PoP × 50 PoPs = $250M capex), bandwidth peering agreements (complex ISP negotiations), 24/7 NOC operations. Only at multi-petabyte scale does the per-GB cost of self-operation ($0.001-0.005/GB) beat commercial CDN pricing. Netflix crossed that threshold around 2012 and invested years into Open Connect. Additional factors: control (Netflix can optimize for video-specific access patterns), privacy (no third-party touching content), and peering directly inside ISPs (something commercial CDNs don't offer). For a startup at < 10 Gbps average throughput, use CloudFront or Cloudflare — the operational cost of a self-operated CDN is orders of magnitude higher than the fees.

### Scenario / Design

**Q: Design the cache invalidation system for a news website where articles are updated frequently and the front page changes every minute.**

A: Three-tier invalidation strategy: (1) Static assets (CSS/JS/images): URL versioning with content hash. No invalidation needed — new content = new URL = new cache key. TTL = 1 year. Zero operational overhead. (2) Article content (updated occasionally): Use surrogate key tags. Each article page is tagged with `article-{id}` in the `Surrogate-Key` response header. When an article is updated, the CMS triggers a Fastly/Cloudflare purge-by-tag API call for `article-{id}`. Propagation time: < 150 ms on Fastly, < 1 s on CloudFront. The CDN invalidates all PoPs. (3) Front page (changes every minute): Short TTL of 60 seconds. No active purge needed — stale for at most 60 s is acceptable for a news homepage. Use `stale-while-revalidate=30` to allow the CDN to serve stale content while fetching fresh content in the background — users never wait for a cache miss, and the front page is at most 90 s stale. Additionally: for breaking news requiring immediate update, a separate "breaking news" API endpoint bypasses CDN cache entirely (`Cache-Control: no-store`) — it's a small payload queried directly from origin, where real-time accuracy matters most.
