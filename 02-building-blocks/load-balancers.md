# Load Balancers

> **Distribute incoming traffic across multiple servers to improve availability, throughput, and latency.**

---

## 1. Why Load Balancers Exist

**Question**: Your single app server handles 5,000 req/s and is at 90% CPU. You need 50,000 req/s in three months. A bigger machine tops out at ~2× the throughput before you hit hardware limits. What do you do?

**Physical constraint**: A single CPU has a fixed instruction-per-second ceiling. A single NIC saturates at ~10–100 Gbps. A single process can only hold so many concurrent TCP connections. No matter how much you spend on vertical scaling, one machine has one set of CPU, memory, and I/O limits — and at some point those limits are absolute.

**Minimal solution**: Put two servers behind a DNS record with two A-entries. Round-trip DNS resolves to one or the other. Works until: one server dies and DNS still points to it (requests fail for minutes until TTL expires), servers receive uneven load because clients cache DNS, and you have no way to drain one server for a deploy.

**Production generalization**: A dedicated load balancer sits in front of the pool. It maintains live health checks so dead servers are removed in seconds (not minutes). It tracks connection state to implement smarter algorithms. It terminates TLS once instead of on each app server. Everything a DNS hack cannot do is what a load balancer provides.

---

## 2. Core Principles

### L4 vs L7

| Layer | What it sees | Routing based on | Use case |
|-------|----------------|-------------------|----------|
| **L4 (Transport)** | IP, port | IP + port (e.g. TCP connection) | Simple, fast; no app awareness |
| **L7 (Application)** | Full HTTP | URL, headers, cookies, body | Path-based routing, SSL termination, sticky sessions |

**L4 analogy**: A postal sorting machine at a distribution center. It reads only the destination address and ZIP code on the envelope and drops it into the correct bin. It does not open the envelope, does not know what is inside, and does not care. Fast, mechanical, no judgment — just routing by address (IP + port).

**L7 analogy**: A human mail clerk at a large law firm. They open each envelope, read the letter, and decide: "This is a contract dispute — goes to floor 3. This is a tax matter — goes to floor 7." Slower than a machine, but capable of context-aware routing based on the actual content (HTTP headers, URL path, cookies, request body).

**Architecture (simplified)**:

```
                    ┌─────────────────┐
  Clients ─────────▶│ Load Balancer   │─────────▶ Server 1
                    │ (L4 or L7)      │─────────▶ Server 2
                    └─────────────────┘─────────▶ Server 3
```

### Common Algorithms

| Algorithm | Description | Pros | Cons |
|-----------|-------------|------|------|
| **Round Robin** | Rotate through servers in order | Simple, even in steady state | Ignores load and latency |
| **Least Connections** | Send to server with fewest active connections | Adapts to slow/long requests | Slightly more state |
| **Weighted Round Robin** | Round robin with weights (e.g. 2:1) | Handles heterogeneous capacity | Static weights |
| **Consistent Hash** | Same client/key → same server | Cache affinity, session stickiness | Rebalancing when nodes change |
| **IP Hash** | hash(client IP) % N | Sticky by IP | Uneven if IPs skewed |

**Algorithm analogies (back to the hotel)**:

- **Round Robin**: The front desk assigns rooms in strict order — Room 101, 102, 103, 104, back to 101. Every guest gets the next room in the rotation regardless of whether the previous occupant is still in the shower. Simple and fair in theory; ignores actual occupancy.

- **Least Connections**: The front desk checks a live board showing which floor has the fewest occupied rooms and directs the next guest there. If floor 3 had a conference group that just checked out, the next five guests all go to floor 3. Adapts to reality, not just the rotation.

- **Weighted Round Robin**: Floor 9 has a larger suite that can host twice as many guests. So the front desk sends two guests to floor 9 for every one it sends to the other floors. Accounts for servers with different hardware capacity (e.g. 8 CPU vs 4 CPU machines).

- **Consistent Hash**: VIP loyalty members always get assigned to the same floor, no matter when they arrive or how many other guests are staying. Their preferences, saved settings, and room layout are already known on that floor. This mirrors sticky sessions: the same user (or cache key) always routes to the same backend so session state and local caches remain warm.

- **IP Hash**: Room assignment is based on the guest's home country ZIP code. Same ZIP → same floor, every time. Predictable but can cause imbalance if guests from one ZIP code dominate.

### Health Checks

- **Active**: LB periodically sends HTTP/TCP checks to each server.
- **Passive**: LB infers health from request success/failure.
- Unhealthy servers are removed from the pool until they pass again.

**Health check analogy**: The hotel manager calls each room's phone every 5 minutes. If a room doesn't answer after 3 tries, the front desk stops sending new guests there. Once the room answers again (the server recovers), it is added back to the rotation. The key challenge is **flapping** — a room that answers on some calls and not others causes guests to be assigned and then re-routed repeatedly. Tune the failure threshold (e.g. 3 missed checks before removal, 2 passed checks before reinstatement) to avoid bouncing.

---

## 3. Real-World Usage

- **AWS ALB/NLB**: ALB (L7) for HTTP/HTTPS; NLB (L4) for TCP/UDP, low latency.
- **GCP Load Balancing**: Global HTTP(S) vs regional TCP/UDP.
- **Nginx / HAProxy**: On-prem or in-cluster L7/L4 load balancing.
- **Kubernetes**: Service + kube-proxy (L4) or Ingress (L7).

**Concrete scenario — e-commerce checkout**:  
The checkout service handles payment processing (long-running requests, ~2s each). Round robin would pile up slow requests on one server while others are idle. Least-connections ensures that the server currently processing 10 payments doesn't get the 11th until another server finishes theirs. This is why Least Connections is the preferred algorithm for workloads with variable request duration.

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

**The LB as a SPOF**: If the hotel has only one front desk agent and they call in sick, no guests get assigned rooms — the whole hotel stops. This is why you run an active-passive or active-active pair. In active-passive, a backup LB watches the primary via a heartbeat (like a second agent watching the front desk) and takes over the Virtual IP if the primary stops responding. In active-active, both agents are at the desk sharing the load, and a DNS or anycast layer distributes incoming traffic between them.

---

## 6. Performance Considerations

- **Latency**: L4 adds minimal latency (microseconds); L7 adds more (SSL handshake, HTTP parsing). Use L4 when you don't need L7 features.
- **Throughput**: LB can become a CPU bottleneck at high TLS volume. Scale vertically or distribute via multiple LBs with anycast.
- **Connection limits**: Max concurrent connections per backend and global; tune keep-alive timeouts and connection pool size.

**SSL termination cost**: Terminating TLS at the LB means every inbound TLS handshake is paid by the LB's CPU. At 100k connections/second, this is significant. Options: use a dedicated SSL accelerator, distribute termination across multiple LB instances, or offload to a CDN layer above.

---

## 7. Implementation Patterns

- **Single LB**: Simple; LB is SPOF. Use for dev or low-criticality.
- **Active-Passive**: Standby LB takes over on failure (VIP or DNS).
- **Active-Active**: Multiple LBs share traffic (e.g. DNS round-robin or anycast). Requires stateless backends or shared session store.

---

## Quick Revision

- **L4**: IP+port; fast, no app logic. **L7**: URL/headers; routing, SSL, stickiness.
- **Algorithms**: Round robin (simple rotation), least connections (adapts to variable load), consistent hash (sticky/cache affinity).
- **Health checks**: Remove unhealthy backends; tune thresholds to avoid flapping.
- **Failure**: LB HA (active-passive or active-active); backend failures handled by pool removal and timeouts.
- **Hotel analogy**: Front desk = LB; floors = servers; room assignment policy = algorithm; manager calling rooms = health check.
- **Interview**: "We use an L7 LB for path-based routing and SSL termination; least-connections so long-running payment requests don't pile up on one server; active-passive LB pair so the load balancer itself isn't a SPOF."
