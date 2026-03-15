# Reverse Proxy

> **A server that sits in front of application servers and forwards client requests, often providing SSL, caching, and routing.**

---

## 1. Concept Overview

A **reverse proxy** receives requests from clients and forwards them to backend servers. Unlike a forward proxy (client-side), the client does not know it is talking to a proxy; the proxy represents one or more backends.

**Why it exists**: Centralize SSL termination, routing, caching, compression, and security so application servers can stay simple and focused.

---

## 2. Core Principles

### Functions

- **SSL/TLS termination**: Decrypt at proxy; backends see plain HTTP (simplifies cert management and backend code).
- **Routing**: Path-based or host-based routing to different backends (e.g. `/api` → API servers, `/` → web).
- **Caching**: Cache responses at the edge; reduce load on origin.
- **Compression**: Gzip/Brotli at proxy.
- **Load balancing**: Distribute across backends (overlaps with L7 load balancer).
- **Security**: Hide backend IPs; add headers (e.g. X-Forwarded-For); WAF integration.

### Architecture

```
  Clients ─────▶ Reverse Proxy (Nginx / Envoy / Caddy)
                        │
                        ├──▶ App Server 1
                        ├──▶ App Server 2
                        └──▶ App Server 3
```

---

## 3. Real-World Usage

- **Nginx**: High-performance reverse proxy and web server; widely used for static files and proxying to app servers.
- **Envoy**: Used in service meshes (Istio) and as API gateway; rich observability and routing.
- **Caddy**: Automatic HTTPS; simple config.
- **AWS ALB**: Managed L7 load balancer that acts as reverse proxy.

---

## 4. Trade-offs

| Aspect | Pros | Cons |
|--------|------|------|
| **SSL at proxy** | Single place for certs; backends simpler | Proxy must be secure and highly available |
| **Caching at proxy** | Lower latency and origin load | Staleness; invalidation logic |
| **Single entry** | Central place for auth, rate limit, logging | Proxy can become bottleneck and SPOF |

**When to use**: You need one entry point for SSL, routing, or caching in front of multiple backends.  
**When not**: Direct client-to-server with no need for proxy features.

---

## 5. Failure Scenarios

| Scenario | Mitigation |
|----------|------------|
| Proxy down | Multiple proxy instances behind DNS or LB; health checks |
| Backend down | Proxy returns 502/503; circuit breaker; retry/fallback |
| Cache poisoning | Validate responses; restrict cache keys; avoid caching user input in key |
| SSL cert expiry | Automate renewal (e.g. Let’s Encrypt); monitor and alert |

---

## 6. Performance Considerations

- **Latency**: Proxy adds a small hop; keep connection pooling to backends and timeouts tuned.
- **Throughput**: Proxy CPU and connection limits; scale horizontally if needed.
- **Caching**: Hit ratio and TTL affect both performance and freshness.

---

## 7. Implementation Patterns

- **Single proxy**: Dev or small setups.
- **Proxy + LB**: LB in front of multiple proxy instances for HA.
- **Proxy per service**: Each service has its own proxy (e.g. sidecar in service mesh).

---

## Quick Revision

- **Role**: One entry point; forwards to backends; can do SSL, routing, caching, compression.
- **Vs load balancer**: Reverse proxy often does L7 routing and caching; LB can be L4-only. In practice many “L7 load balancers” are reverse proxies.
- **Failure**: Multiple proxy instances; health checks; 502/503 and circuit breaking for backend failures.
- **Interview**: “We put a reverse proxy in front of our app servers for SSL termination and path-based routing; we cache static assets there to reduce load on the origin.”
