# Reverse Proxy

> **A server that sits in front of application servers and forwards client requests, often providing SSL, caching, and routing.**

---

## Why a Reverse Proxy Exists

**Question**: You run 3 identical app server instances for redundancy. Your app handles HTTPS. That means each of the 3 servers needs: a TLS certificate, TLS termination code, certificate renewal logic, and the private key stored securely. When the certificate expires, you rotate it on all 3 servers simultaneously or face a split-second mismatch window. Now you add a 4th server. And a 5th. How do you avoid managing TLS on every individual backend?

**Physical constraint**: TLS termination requires the server to hold the private key, perform asymmetric decryption for the handshake (~1ms of CPU per new connection), and manage cipher negotiation. Distributing these responsibilities across N app servers means N copies of the private key, N renewal schedules, and N places where a misconfiguration or expired cert breaks user traffic. The private key exposure surface grows with N.

**Minimal solution**: Terminate TLS on exactly one machine in front of all app servers. That machine holds one private key, performs one set of cert renewals, and forwards decrypted traffic to backends over a private network where TLS overhead is unnecessary. One cert renewal touches one machine.

**Production generalization**: A reverse proxy centralizes all client-facing concerns — TLS, routing, caching, compression, header manipulation — so app servers can focus purely on business logic. The proxy also hides backend topology (clients see one IP, not the IPs of individual instances), absorbs SSL handshake CPU, serves static assets directly from disk (no app server involvement), and becomes the natural place to add load balancing, rate limiting, and caching as requirements grow.

---

## 1. Concept Overview

A **reverse proxy** receives requests from clients and forwards them to backend servers. Unlike a forward proxy (client-side), the client does not know it is talking to a proxy; the proxy represents one or more backends.

**Why it exists**: Centralize SSL termination, routing, caching, compression, and security so application servers can stay simple and focused.

**Real-life analogy**: Imagine a large corporation's front desk receptionist. Every visitor — delivery drivers, job applicants, clients, contractors — enters through the lobby and talks to the receptionist first. The receptionist decides:
- "You're here for Legal? That's floor 12."
- "You have a package for Engineering? I'll take it — they're busy."
- "You want a product brochure? I have a stack right here, no need to bother anyone upstairs."

The visitor never sees the internal layout. They don't know there are 10 departments or 200 employees. They just deal with the receptionist. That's the reverse proxy — one face for the whole company, routing internally and handling whatever it can before escalating.

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

### SSL Termination — analogy

The corporation requires that all sensitive mail arrive in sealed, tamper-proof envelopes. The receptionist (proxy) has the master key and opens every sealed envelope at the front desk, reads the destination, and passes a plain copy to the correct department. Internal departments never deal with envelope seals — they just get the letter.

This is **SSL termination**: the reverse proxy holds the TLS certificate and private key, decrypts incoming HTTPS connections, and forwards plain HTTP to backend services. Backends don't need certificates. You manage one cert in one place. If you need to rotate the cert, you do it once at the proxy — not on every server.

The trade-off: if traffic between the proxy and backends travels over an untrusted network, you need **SSL re-encryption** (the receptionist seals the internal envelope again before sending it upstairs). In most private datacenter or VPC setups, plain HTTP internally is acceptable.

### Routing — analogy

A single phone number for the whole company, with an auto-attendant:
- "Press 1 for Sales" → `/api/sales` → Sales microservice
- "Press 2 for Support" → `/api/support` → Support microservice
- "Press 3 for the website" → `/` → Web server

The caller (client) dials one number. The proxy routes based on what they asked for. This is **path-based and host-based routing** — the proxy reads the URL path or the `Host` header and forwards to the correct pool.

### Caching at the Proxy — analogy

The receptionist keeps a drawer of common brochures, forms, and FAQs. When a visitor asks for the company's product spec sheet, the receptionist hands it directly from the drawer without calling anyone upstairs. That's the proxy cache — static files (CSS, images, fonts) or infrequently-changing API responses are stored at the proxy and served without touching the application server.

When the marketing team publishes a new brochure, they send a memo to update the drawer (cache invalidation / TTL expiry). Until then, visitors get the old one.

---

## 3. Real-World Usage

- **Nginx**: High-performance reverse proxy and web server; widely used for static files and proxying to app servers.
- **Envoy**: Used in service meshes (Istio) and as API gateway; rich observability and routing.
- **Caddy**: Automatic HTTPS; simple config.
- **AWS ALB**: Managed L7 load balancer that acts as reverse proxy.

**Nginx config snippet** (path-based routing):

```nginx
server {
    listen 443 ssl;
    server_name api.example.com;

    # SSL termination here — backends use plain HTTP
    ssl_certificate     /etc/ssl/certs/example.crt;
    ssl_certificate_key /etc/ssl/private/example.key;

    location /api/ {
        proxy_pass http://api_servers;
    }

    location / {
        proxy_pass http://web_servers;
    }

    location /static/ {
        root /var/www/static;    # serve directly from disk, no upstream hit
        expires 1y;
    }
}
```

---

## 4. Trade-offs

| Aspect | Pros | Cons |
|--------|------|------|
| **SSL at proxy** | Single place for certs; backends simpler | Proxy must be secure and highly available |
| **Caching at proxy** | Lower latency and origin load | Staleness; invalidation logic |
| **Single entry** | Central place for auth, rate limit, logging | Proxy can become bottleneck and SPOF |
| **Hide backends** | Attackers can't target individual servers directly | Proxy itself becomes a high-value target |

**When to use**: You need one entry point for SSL, routing, or caching in front of multiple backends.  
**When not**: Direct client-to-server with no need for proxy features; or when an API gateway already provides these capabilities.

---

## 5. Failure Scenarios

| Scenario | Mitigation |
|----------|------------|
| Proxy down | Multiple proxy instances behind DNS or LB; health checks |
| Backend down | Proxy returns 502/503; circuit breaker; retry/fallback |
| Cache poisoning | Validate responses; restrict cache keys; avoid caching user input in key |
| SSL cert expiry | Automate renewal (e.g. Let's Encrypt via Caddy or Certbot); monitor and alert |

**The receptionist as SPOF**: If the lobby receptionist quits without notice, no visitor gets routed. You solve this by having two receptionists at adjacent desks (active-active proxy pair) behind a DNS or L4 load balancer. If one goes down, the other handles all traffic without the client noticing anything.

**502 Bad Gateway — the forwarding breakdown**: When the receptionist tries to reach floor 12 and the phone just rings (backend is unresponsive), she tells the visitor "sorry, that department isn't available right now" — a 502 or 503. A circuit breaker pattern keeps her from re-dialing that floor on every single call; she marks it as unavailable for 30 seconds before retrying.

---

## 6. Performance Considerations

- **Latency**: Proxy adds one extra network hop; keep keep-alive connections to backends to avoid per-request TCP handshakes.
- **Throughput**: Proxy CPU is consumed by TLS, compression, and routing logic; scale horizontally if needed.
- **Caching**: Cache hit ratio and TTL directly affect how much load reaches the backends; monitor and tune.
- **Connection pooling**: The proxy should maintain a warm connection pool to each backend; cold connections per request are expensive at scale.

---

## 7. Implementation Patterns

- **Single proxy**: Dev or small setups. Receptionist desk of one — fine for a small office.
- **Proxy + LB**: LB in front of multiple proxy instances for HA. Two receptionists, one security guard at the door routing them.
- **Proxy per service (sidecar)**: Each microservice has its own proxy sidecar (e.g. Envoy in Istio). Every department has its own phone extension handler. Enables per-service observability, retries, and mTLS between services.

---

## Quick Revision

- **Role**: One entry point; forwards to backends; handles SSL, routing, caching, compression.
- **SSL termination**: Decrypt at proxy; backends speak plain HTTP; manage one cert in one place.
- **Vs load balancer**: Reverse proxy often does L7 routing and caching; LB can be L4-only. In practice, many "L7 load balancers" are reverse proxies with routing and SSL built in.
- **Failure**: Multiple proxy instances; health checks; 502/503 and circuit breaking for backend failures.
- **Receptionist analogy**: All visitors in → proxy decides destination → internal departments stay hidden.
- **Interview**: "We put a reverse proxy in front of our app servers for SSL termination and path-based routing; we cache static assets there to reduce load on the origin; we run two proxy instances behind a DNS failover so the proxy itself isn't a SPOF."
