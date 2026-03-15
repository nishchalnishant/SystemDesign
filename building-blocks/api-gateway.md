# API Gateway

> **Single entry point for API traffic: routing, authentication, rate limiting, and protocol translation.**

---

## 1. Concept Overview

An **API gateway** sits between clients and backend services. It handles cross-cutting concerns (auth, rate limiting, logging, routing) so individual services can stay focused on business logic.

**Why it exists**: Without a gateway, every service would implement auth, rate limits, and routing; clients would need to know many endpoints. The gateway centralizes these and provides a single API surface.

---

## 2. Core Principles

### Typical responsibilities

| Responsibility | Description |
|----------------|-------------|
| **Routing** | Path/host → backend service (e.g. `/users` → user service) |
| **Authentication** | Verify JWT, API key, or OAuth; reject unauthenticated requests |
| **Authorization** | Check permissions (e.g. scope, role) |
| **Rate limiting** | Limit requests per user/key/IP; return 429 when exceeded |
| **Protocol translation** | REST (public) → gRPC (internal); or GraphQL → REST backends |
| **Caching** | Cache responses for read-heavy endpoints |
| **Circuit breaking** | Fail fast when backend is down; avoid cascading failure |
| **Logging / metrics** | Centralized access logs, latency, error rate |

### Architecture

```
  Clients (Web / Mobile / Partners)
        │
        ▼
  ┌─────────────────┐
  │   API Gateway   │  Auth, rate limit, route, cache
  └────────┬────────┘
           │
     ┌─────┼─────┬─────────┐
     ▼     ▼     ▼         ▼
  User  Order  Payment  Notification
  Svc   Svc    Svc      Svc
```

---

## 3. Real-World Usage

- **Kong**: Open-source; plugins for auth, rate limit, logging; on-prem or managed.
- **AWS API Gateway**: Managed; Lambda integration; usage plans and keys.
- **Google Apigee**: Enterprise; monetization, analytics.
- **Nginx / Envoy**: Often used as gateway with custom config or Lua/WASM.

---

## 4. Trade-offs

| Aspect | Pros | Cons |
|--------|------|------|
| **Centralized** | One place for auth, rate limit, routing | Gateway is critical path and potential bottleneck |
| **Protocol translation** | Clients use REST; internals use gRPC | Extra hop and complexity |
| **Managed vs self-hosted** | Managed: less ops | Cost; less control |

**When to use**: Multiple services exposed as one API; need central auth, rate limit, or versioning.  
**When not**: Single service; or when a simple reverse proxy is enough.

---

## 5. Failure Scenarios

| Scenario | Mitigation |
|----------|------------|
| Gateway down | Multiple gateway instances behind LB; stateless |
| Backend down | Circuit breaker; return 503; retry with backoff |
| Auth/rate-limit bypass | Validate at gateway only; don’t trust client to send correct headers |
| Latency spike | Timeouts; circuit breaker; scale gateway horizontally |

---

## 6. Performance Considerations

- **Latency**: Gateway adds one hop; minimize logic and use connection pooling to backends.
- **Throughput**: Scale gateway horizontally; avoid heavy logic in hot path.
- **Caching**: Reduces backend load and latency for cacheable GETs.

---

## 7. Implementation Patterns

- **Gateway per environment**: One gateway for public API; optional separate for internal.
- **Backend for Frontend (BFF)**: Different gateway or route per client type (e.g. mobile vs web) to tailor responses.
- **Gateway + service mesh**: Gateway at edge; mesh handles service-to-service (mTLS, retries, discovery).

---

## Quick Revision

- **Role**: Single entry; routing, auth, rate limit, protocol translation, caching, circuit breaking.
- **Vs reverse proxy**: Gateway adds auth, rate limit, and often API-specific logic; reverse proxy is more generic.
- **Failure**: Stateless; multiple instances; circuit breaker to backends.
- **Interview**: “We use an API gateway for authentication and rate limiting so backends don’t each implement it; we route by path to the right microservice and can translate REST to gRPC internally.”
