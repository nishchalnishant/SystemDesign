> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** API gateways in production — what they actually do beyond "routing," how they relate to service meshes, and patterns like BFF and API composition that come up in design interviews.
>
> **Key topics:**
> - **Core functions** — routing, AuthN, rate limiting, SSL termination
> - **API composition** — aggregating multiple backend calls into one client response
> - **BFF (Backend for Frontend)** — why mobile and web get different gateway shapes
> - **Gateway vs service mesh** — what lives at the edge vs what lives east-west
> - **Service discovery** — how gateways know where to route
> - **Request/response transformation** — header injection, payload reshaping
>
> **Key takeaway:** The API gateway is the north-south traffic boundary (external → internal). The service mesh is the east-west boundary (service → service). They're complementary, not competing.

---
module: 02-building-blocks
status: unread
tags: [02-building-blocks, system-design, networking]
---
# API Gateways

---

## What Problem It Solves

In a monolith, a client calls one URL. The app handles routing internally. When you decompose into microservices — 20, 50, 100 services — the client can't call each one directly:
- The client would need to know the address of every service.
- Auth, rate limiting, and SSL termination would need to be reimplemented in every service.
- The internal network topology would be exposed.

An **API gateway** is the single entry point for all external clients. It handles the shared concerns once, centrally.

```
Mobile App ─────┐
                ├──→ API Gateway ──→ User Service
Web Client ─────┤                ──→ Order Service
3rd Party API ──┘                ──→ Payment Service
```

---

## Core Gateway Functions

### 1. Request Routing

Routes incoming requests to the correct backend service based on URL path, Host header, or request attributes.

```
GET /users/123     → User Service
GET /orders/456    → Order Service
POST /payments     → Payment Service
GET /videos/abc    → Video Service (different cluster, different region)
```

Gateway maintains a routing table (static config or pulled from a service registry). On a path match, the request is proxied to the corresponding upstream.

### 2. Authentication and Authorization

Validate JWTs or API keys at the gateway before the request ever reaches a service.

```
Request arrives with: Authorization: Bearer <JWT>
Gateway:
  1. Validates JWT signature (using public key from auth service)
  2. Checks expiry, issuer, audience
  3. If valid: inject user claims as headers (X-User-Id, X-User-Role)
  4. If invalid: return 401 immediately

Backend services receive: X-User-Id: 12345 (trusted, injected by gateway)
```

Backend services don't need to validate JWTs themselves — they trust the gateway's injected headers (since only requests through the gateway reach them; no external access).

### 3. Rate Limiting

Prevent abuse and protect backend services from traffic spikes.

```
Per-IP: 100 req/min  (protects against DDoS)
Per-API-key: 1000 req/min  (enforces SLA tiers)
Per-user: 10 req/s for /search endpoint  (prevents expensive query abuse)
```

Counters are stored in Redis for distributed rate limiting across multiple gateway instances. On limit breach, return `429 Too Many Requests` with `Retry-After` header.

### 4. SSL Termination

Decrypt TLS at the gateway. Traffic between the gateway and backends travels over plain HTTP (or re-encrypted via mTLS handled by the service mesh).

- Centralizes certificate management: renew one cert (or use Let's Encrypt automation) rather than per-service.
- Backends don't need TLS configuration.

### 5. Request/Response Transformation

Reshape requests or responses between what the client sends and what the backend expects.

**Common patterns:**
- Inject authentication headers (`X-User-Id` from JWT claims)
- Strip internal headers before forwarding to upstream
- Add CORS headers to all responses
- Rewrite URL paths (`/api/v1/users` → `/users`)
- Transform response payloads (rename fields, filter fields the client doesn't need)

### 6. Observability

Every request through the gateway gets a unique request ID. Log all requests with: path, method, status code, latency, upstream service, user identity. This provides an audit trail for security and a central place to measure latency across all services.

---

## API Composition

When a client needs data from multiple services, it can either make N calls or the gateway can aggregate them.

**Without composition:** Mobile app makes 3 requests:
```
GET /users/123        → User Service
GET /orders?user=123  → Order Service
GET /recommendations?user=123 → Recommendation Service
```

**With API composition (gateway aggregates):**
```
GET /dashboard/123

Gateway:
  1. Call User Service → get user profile
  2. Call Order Service → get recent orders
  3. Call Recommendation Service → get recommendations
  4. Merge responses into one JSON
  5. Return single response to client
```

**Benefits:** Fewer round trips for the client (critical on mobile where each round trip costs battery + latency). Backend services remain simple (single-concern APIs).

**Pitfalls:**
- Gateway becomes bottleneck if composition logic grows complex.
- Error handling: if one upstream fails, do you return partial data or fail the whole request?
- Timeouts: the composed response is as slow as the slowest upstream.

Mitigation: set per-upstream timeouts; return partial responses with explicit nulls for failed upstreams; circuit-break failing upstreams.

---

## BFF: Backend for Frontend

Different clients have different data needs:
- Mobile: low bandwidth, small screen, battery-sensitive. Wants minimal data, pre-aggregated.
- Web: large screen, fast connection. Wants rich data, multiple fields.
- 3rd party API: specific schema expectations, versioning requirements.

**Single gateway problem:** The gateway either returns the maximum data set (mobile wastes bandwidth) or the minimum (web makes extra calls).

**BFF pattern:** Each client gets its own gateway tailored to its needs.

```
Mobile App  → Mobile BFF  ──┐
Web App     → Web BFF    ──┤─→ Internal Services
Partner API → Partner BFF──┘
```

Each BFF has its own team ownership, its own composition logic, and its own routing rules. The internal services remain generic and stable.

**When to use BFF:** When different client types have genuinely different data shape requirements, or when clients are built by separate teams.

**When not to:** Avoid BFF proliferation — if the clients need essentially the same data, a single gateway with query parameters is simpler.

---

## API Gateway vs Load Balancer vs Service Mesh

These are often confused. Each operates at a different boundary.

| | API Gateway | Load Balancer | Service Mesh |
|---|---|---|---|
| Traffic | External → internal | External → internal | Internal → internal |
| Layer | L7 (HTTP application layer) | L4 or L7 | L7 (sidecar) |
| Concerns | Auth, rate limit, routing, composition | Distribute load | mTLS, retries, circuit break, tracing |
| Auth enforcement | Yes | No | Workload identity (mTLS) |
| Where it runs | Dedicated gateway fleet | Before any backend | Sidecar per pod |
| Example | Kong, AWS API GW, Nginx | AWS NLB, HAProxy | Istio + Envoy |

**In a production Kubernetes setup:**
```
Internet
  ↓
Load Balancer (AWS NLB) — TCP distribution across gateway pods
  ↓
API Gateway (Kong / Nginx Ingress) — AuthN, rate limit, routing
  ↓
Service Mesh (Istio/Envoy sidecar) — mTLS, retries, tracing
  ↓
Backend Service
```

---

## Service Discovery Integration

Gateway must know where to route. In a dynamic fleet, service instances change constantly. Two patterns:

**Static routing (small/simple):** Gateway config file maps paths to fixed upstream URLs. Simple; requires deploy to update.

**Dynamic routing via service registry:** Gateway queries Consul or Kubernetes DNS to get healthy endpoints for each service. Auto-updates when instances start/stop.

```
# Kubernetes: Gateway routes to service DNS name
GET /orders → http://order-service.default.svc.cluster.local:8080/orders
```

Kubernetes Service objects act as the service registry — `kube-proxy` ensures the DNS name always resolves to healthy pods.

---

## Rate Limiting Architecture

For a distributed gateway fleet, rate limit state must be shared:

```
Gateway Instance A ──→ Redis (shared counter) ←── Gateway Instance B
                           ↑
                    Rate limit logic: INCR + EXPIRE
```

**Token bucket algorithm (common choice):**
```python
def is_allowed(client_id, limit_per_minute):
    key = f"rate:{client_id}"
    with redis.pipeline() as pipe:
        pipe.incr(key)
        pipe.expire(key, 60)  # TTL = 1 minute window
        count, _ = pipe.execute()
    return count <= limit_per_minute
```

**Sliding window** is more accurate but requires more Redis operations (sorted set with timestamps). Token bucket is the common production choice.

---

## Interview Questions to Practice

1. **"What is the difference between an API gateway and a service mesh? Do you need both?"**
   *The API gateway handles north-south traffic (external clients to internal services): authentication, rate limiting, SSL termination, URL routing. The service mesh handles east-west traffic (service to service): mTLS, retries, circuit breaking, distributed tracing. They're complementary. In a mature microservices deployment you need both: the gateway at the edge, the mesh for internal resilience. At small scale (< 10 services), just the API gateway is often enough.*

2. **"How does the API gateway handle authentication without making services implement it themselves?"**
   *The gateway validates the JWT signature using the auth server's public key. If valid, it extracts claims (user_id, roles) and injects them as trusted headers (X-User-Id, X-User-Role). Backend services receive these headers and trust them — they know only requests that passed through the gateway arrive on the internal network. No service implements JWT validation; they just read headers. mTLS (via service mesh) enforces that only the gateway can be the source of internal calls.*

3. **"What is API composition and when would you use it?"**
   *API composition is when the gateway aggregates multiple upstream calls into a single response for the client. Use it when a screen or feature needs data from several services and the client making N sequential calls would be slow (high latency, mobile battery cost). Common in BFF pattern. Risk: the composed response is as slow as the slowest upstream — set per-upstream timeouts and decide whether to return partial data on failure.*

4. **"A gateway is receiving 100K requests/second. How would you rate-limit by user?"**
   *Run multiple gateway instances behind a load balancer. Use Redis for shared rate-limit state: token bucket per user_id. Each gateway instance increments the Redis counter on each request. INCR + EXPIRE in a pipeline gives atomic increment with TTL. This scales horizontally — add gateway instances without changing rate-limit logic. Redis can handle ~1M ops/sec on a single node; for larger scale, use Redis Cluster or shard counters by user_id range across Redis shards.*

5. **"What is the BFF pattern and when would you avoid it?"**
   *BFF (Backend for Frontend) creates a dedicated API gateway per client type (mobile, web, partner). Each BFF has its own team, its own composition logic, and its own data shaping. Use it when clients have genuinely different data needs or are owned by different teams. Avoid it when clients need essentially the same data — a single gateway with optional query params is simpler and avoids duplicating routing, auth, and rate-limiting logic across multiple BFF instances.*

---

## Applied In

**High-Level Design** — where the gateway (auth, composition, rate-limit fan-in) is a design decision:

- [Design an E-Commerce Platform](../../05-hld-problems/02-medium/e-commerce-platform.md) — gateway composes product/inventory/pricing services per page
- [Design a Payment System](../../05-hld-problems/03-hard/payment-system.md) — centralized auth + idempotency-key enforcement at the edge
- [Design a Notification Service](../../05-hld-problems/02-medium/notification-service.md) — per-client rate limiting and request shaping at the gateway
