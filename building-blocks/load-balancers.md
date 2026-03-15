# Load Balancers

> **Distribute incoming traffic across multiple servers to improve availability, throughput, and latency.**

---

## 1. Concept Overview

A **load balancer (LB)** sits in front of a pool of servers and directs each request to one of them. It provides:
- **High availability**: If one server fails, traffic goes to others.
- **Scalability**: Add more servers to handle more load.
- **Performance**: Spread load so no single server is overwhelmed.

**Why it exists**: A single server is a single point of failure and a capacity ceiling. Load balancers enable horizontal scaling and fault tolerance.

---

## 2. Core Principles

### L4 vs L7

| Layer | What it sees | Routing based on | Use case |
|-------|----------------|-------------------|----------|
| **L4 (Transport)** | IP, port | IP + port (e.g. TCP connection) | Simple, fast; no app awareness |
| **L7 (Application)** | Full HTTP | URL, headers, cookies, body | Path-based routing, SSL termination, sticky sessions |

**Architecture (simplified)**:

```
                    ┌─────────────────┐
  Clients ─────────▶│ Load Balancer   │─────────▶ Server 1
                    │ (L4 or L7)      │─────────▶ Server 2
                    └─────────────────┘─────────▶ Server 3
```

### Common algorithms

| Algorithm | Description | Pros | Cons |
|-----------|-------------|------|------|
| **Round Robin** | Rotate through servers in order | Simple, even in steady state | Ignores load and latency |
| **Least Connections** | Send to server with fewest active connections | Adapts to slow/long requests | Slightly more state |
| **Weighted Round Robin** | Round robin with weights (e.g. 2:1) | Handles heterogeneous capacity | Static weights |
| **Consistent Hash** | Same client/key → same server | Cache affinity, session stickiness | Rebalancing when nodes change |
| **IP Hash** | hash(client IP) % N | Sticky by IP | Uneven if IPs skewed |

### Health checks

- **Active**: LB periodically sends HTTP/TCP checks to each server.
- **Passive**: LB infers health from request success/failure.
- Unhealthy servers are removed from the pool until they pass again.

---

## 3. Real-World Usage

- **AWS ALB/NLB**: ALB (L7) for HTTP/HTTPS; NLB (L4) for TCP/UDP, low latency.
- **GCP Load Balancing**: Global HTTP(S) vs regional TCP/UDP.
- **Nginx / HAProxy**: On-prem or in-cluster L7/L4 load balancing.
- **Kubernetes**: Service + kube-proxy (L4) or Ingress (L7).

---

## 4. Trade-offs

| Choice | Pros | Cons |
|--------|------|------|
| **L4** | Fast, low CPU, no decryption | No URL/header routing, no content-based logic |
| **L7** | Path-based routing, SSL termination, caching | Higher latency and CPU |
| **Round Robin** | Simple | Can send traffic to overloaded or slow nodes |
| **Least Connections** | Better for variable request duration | Needs connection tracking |
| **Consistent Hash** | Sticky sessions, cache affinity | Rebalancing on node add/remove |

**When to use**: Multiple app instances; need HA or horizontal scaling.  
**When not**: Single instance (no need); or when a different component (e.g. API gateway) already does routing and you only need internal L4.

---

## 5. Failure Scenarios

| Scenario | Mitigation |
|----------|------------|
| LB itself fails | Active-passive or active-active LB pair; DNS/anycast failover |
| All backends down | Return 503; circuit breaker at caller |
| One backend slow | Least-connections or timeouts; remove from pool on repeated failure |
| Health check wrong | Tune check interval and threshold; avoid flapping |

---

## 6. Performance Considerations

- **Latency**: L4 adds minimal latency; L7 adds more (parsing, SSL). Use L4 when you don’t need L7 features.
- **Throughput**: LB can be bottleneck (CPU for SSL, connection table size). Scale vertically or use multiple LBs (e.g. anycast).
- **Connection limits**: Max concurrent connections per backend and global; tune timeouts and pool size.

---

## 7. Implementation Patterns

- **Single LB**: Simple; LB is SPOF. Use for dev or low-criticality.
- **Active-Passive**: Standby LB takes over on failure (VIP or DNS).
- **Active-Active**: Multiple LBs share traffic (e.g. DNS round-robin or anycast). Requires stateless backends or shared session store.

---

## Quick Revision

- **L4**: IP+port; fast, no app logic. **L7**: URL/headers; routing, SSL, stickiness.
- **Algorithms**: Round robin (simple), least connections (variable duration), consistent hash (sticky/cache).
- **Health checks**: Remove unhealthy backends; tune to avoid flapping.
- **Failure**: LB HA (active-passive or active-active); backend failures handled by pool and timeouts.
- **Interview**: “We use an L7 LB for path-based routing and SSL termination; least-connections so long requests don’t overload a single server.”
