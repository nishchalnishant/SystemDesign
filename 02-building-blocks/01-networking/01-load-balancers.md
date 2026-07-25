> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Load balancers in depth — from Layer 4/7 basics through consistent hashing, global server load balancing (GSLB), and high-availability LB setups.
>
> **Key topics:**
> - **L4 vs L7** — what each can and can't route on
> - **Routing algorithms** — round robin, least connections, IP hash, consistent hashing
> - **Consistent hashing** — how to minimize cache invalidation when servers are added/removed
> - **Health checks** — active vs passive, how failover works
> - **Session affinity** — when you need it and why Redis is better
> - **GSLB** — routing users to the geographically closest data center
> - **LB high availability** — active-passive via VRRP/floating IP, active-active
>
> **Key takeaway:** For most systems: L7 LB (Nginx/ALB/Envoy) in front, consistent hashing when you need sticky-without-coupling, GSLB at the edge. The LB itself is never a SPOF in a real production deployment.

---
module: 02-building-blocks
status: unread
tags: [02-building-blocks, system-design, networking]
---
# Load Balancers

---

## Why Load Balancers Exist

A single server has a ceiling: bounded CPU, bounded RAM, bounded network bandwidth. When traffic exceeds what one machine can handle, you add more machines. A load balancer (LB) distributes incoming requests across a fleet of backend servers, ensuring no single server is the bottleneck.

Secondary benefits:
- **High availability:** If one server dies, the LB routes around it.
- **Rolling deploys:** Take servers out of rotation one-at-a-time to deploy without downtime.
- **SSL termination:** Decrypt TLS at the LB edge; backends communicate over plain HTTP internally.

---

## Layer 4 vs Layer 7

Load balancers operate at different layers of the network stack. The layer determines what information they can inspect to make routing decisions.

### Layer 4 (Transport Layer)

Routing decision is based only on **IP address + TCP/UDP port**. The LB never opens the packet to read the HTTP payload.

```
Client IP: 1.2.3.4, Port: 443
→ LB reads: destination IP + port only
→ Routes to Backend A (no knowledge of URL, headers, cookies)
```

- **Speed:** Very fast. The LB acts as a transparent TCP proxy.
- **Limitation:** Can't route `/images` to one server farm and `/api` to another. Can't inspect cookies for session affinity.
- **Use cases:** Raw TCP throughput, database proxies (PgBouncer), UDP (DNS).

### Layer 7 (Application Layer)

Routing decision is based on **HTTP content** — URL path, Host header, query parameters, cookies, request body.

```
GET /videos/abc123
→ LB reads HTTP headers
→ Routes to Video Service (not User Service)

GET /api/users/me (Cookie: session=xyz)
→ LB routes to the server that owns session xyz
```

- **Speed:** Slightly slower — the LB must parse HTTP. In practice the difference is microseconds.
- **Capabilities:** Content-based routing, SSL termination, WebSocket upgrades, header manipulation, canary routing (send 1% of `/checkout` traffic to v2).
- **Use cases:** Every modern web application. AWS ALB, Nginx, HAProxy, Envoy are all L7.

| | L4 | L7 |
|---|---|---|
| Routing basis | IP + port | URL, headers, cookies, body |
| SSL termination | No (pass-through) | Yes |
| Content-based routing | No | Yes |
| Speed | Faster | Slightly slower |
| Use case | TCP/UDP, DB proxies | HTTP microservices, APIs |

---

## Routing Algorithms

### Round Robin

Requests are distributed sequentially: server 1, 2, 3, 1, 2, 3...

- Simple, uniform when all servers are identical.
- Problem: doesn't account for varying request weight. A server handling a 10s video upload gets the next request at the same time as one that finished in 5ms.

### Weighted Round Robin

Each server gets a weight. A server with weight 3 receives 3× more traffic than a weight-1 server.

Use case: mixed-capacity fleet (some servers have more CPU).

### Least Connections

New request goes to the server with the fewest active connections currently.

- Better than round robin for variable-duration requests.
- Requires the LB to track connection count per backend — small overhead.

### Least Response Time

Combines least connections with response time — routes to the server that is both least loaded and fastest.

### IP Hash

`shard = hash(client_ip) % num_backends`

Same IP always maps to the same backend. Provides basic session affinity without a cookie.

- Problem: if you add/remove a server, `num_backends` changes and every client remaps. Causes cache misses and session loss.
- **Consistent hashing** solves this.

---

## Consistent Hashing

Standard hash-based routing maps `hash(key) % N` — changing N remaps ~all keys. Consistent hashing minimizes remapping: adding/removing one node moves only ~1/N of the keys.

### How It Works

Place both servers and keys on a virtual ring (0 to 2³²-1). A key is served by the first server clockwise from its hash position.

```
Ring (0 → 2³²):
       0
      /|\
Server A  Server C
    |         |
Server B  (new key K lands here → routes to C)
```

**Adding a server D between B and C:** Only keys between B and D migrate from C to D. All other keys are unaffected.

**Real production use:** Memcached client libraries, DynamoDB partitioning, Cassandra token ring, CDN edge node selection.

**Virtual nodes:** To avoid hot spots when servers have different capacities or hash poorly, each physical server is represented by multiple virtual nodes on the ring. A server with 2× capacity gets 2× virtual nodes → receives ~2× the keys.

```python
import hashlib

class ConsistentHashRing:
    def __init__(self, nodes, vnodes_per_node=150):
        self.ring = {}
        self.sorted_keys = []
        for node in nodes:
            for i in range(vnodes_per_node):
                key = self._hash(f"{node}:{i}")
                self.ring[key] = node
        self.sorted_keys = sorted(self.ring.keys())

    def _hash(self, key):
        return int(hashlib.md5(key.encode()).hexdigest(), 16)

    def get_node(self, key):
        h = self._hash(key)
        for ring_key in self.sorted_keys:
            if h <= ring_key:
                return self.ring[ring_key]
        return self.ring[self.sorted_keys[0]]  # wrap around
```

---

## Session Affinity (Sticky Sessions)

Some stateful applications store session state in server local memory. If a subsequent request hits a different server, the session is gone.

**Option 1 — Cookie-based affinity:** LB sets a cookie (`AWSALB`, `SERVERID`) on first response. All subsequent requests from the client route to the same backend.

- Breaks if the target server dies: client session lost.
- Couples client state to a specific physical server.

**Option 2 — Externalize session state (preferred):** Store sessions in Redis. Any server in the pool can serve any request. LB doesn't need to be sticky.

```
Client → LB → Any Server → Redis (session lookup) → response
```

This is almost always the right answer. Sticky sessions are a band-aid for stateful servers; external session state makes the fleet horizontally scalable.

---

## Health Checks

The LB must know which backends are alive before routing to them.

### Active Health Checks

LB proactively sends periodic requests to every backend:
- **L4 check:** TCP connect to port 80. If it accepts the connection, the server is up.
- **L7 check:** HTTP GET `/health`. If response is 200, server is healthy.

```
LB → GET /health → Backend (every 5s)
If 3 consecutive failures → mark unhealthy → stop routing
If 2 consecutive successes → mark healthy → resume routing
```

**Configurable thresholds:** `unhealthy_threshold=3, healthy_threshold=2, interval=5s, timeout=3s`

### Passive Health Checks

LB monitors actual traffic responses. If a backend returns 5xx errors at >N% rate, it's removed. No extra probe traffic.

Used together with active checks for defense in depth.

---

## Global Server Load Balancing (GSLB)

GSLB distributes traffic across **data centers in different geographic regions**, routing users to the closest or most available DC.

### DNS-based GSLB

The most common approach: return different IP addresses based on the client's geographic location (via anycast or latency-based DNS).

```
User in Tokyo → DNS query for api.example.com
→ GSLB returns IP of Tokyo DC

User in London → DNS query for api.example.com
→ GSLB returns IP of Frankfurt DC
```

**Tools:** AWS Route 53 (latency-based / geolocation routing), Cloudflare, Akamai.

**Limitation:** DNS TTL means failover takes minutes (TTL is usually 30–300s). Not suitable for instant failover.

### Anycast

Multiple DCs announce the same IP range. BGP routing directs clients to the topologically nearest DC.

```
IP 1.2.3.4 is announced from:
  → AWS us-east-1
  → AWS eu-west-1
  → AWS ap-northeast-1

User in Tokyo sends packet to 1.2.3.4 → BGP routes to ap-northeast-1
```

**Pros:** Sub-second failover (BGP re-converges in <30s). No DNS TTL issues.
**Used by:** Cloudflare, Google (8.8.8.8), Fastly for CDN edge.

### GSLB Failover Logic

GSLB continuously health-checks each DC. If a DC becomes unhealthy, it stops returning that IP (DNS) or withdraws the BGP route (anycast), rerouting all traffic to healthy DCs.

---

## Load Balancer High Availability

The LB itself cannot be a single point of failure. If it goes down, everything goes down.

### Active-Passive (VRRP / Floating IP)

Two LB instances share a virtual IP (VIP). The active LB owns the VIP and handles all traffic. The passive LB monitors the active via heartbeat. If the active fails, the passive claims the VIP (via VRRP or keepalived) within ~1–2 seconds.

```
                       VIP: 10.0.0.1
                            |
              +-------------+
              |             |
    Active LB (owns VIP)   Passive LB (standby)
              |
         Backend Pool
```

**Tools:** keepalived + VRRP (HAProxy HA), AWS NLB (multiple AZs).

### Active-Active

Both LB instances are active behind a shared anycast or DNS round-robin entry. Traffic is split across both.

- Requires both LBs to share state (connection tables, rate-limit counters) — typically via a shared backend (Redis) or by making the LBs stateless (L7 only).
- More complex but higher throughput.

### Cloud Managed LBs

AWS ALB, GCP Load Balancing, Azure Load Balancer are natively HA — they run as a distributed service across multiple AZs. No VRRP needed; the cloud provider handles it.

---

## Choosing an LB Algorithm: Quick Reference

| Situation | Algorithm |
|---|---|
| Homogeneous fleet, stateless requests | Round robin |
| Mixed server capacities | Weighted round robin |
| Long-lived or heavy requests | Least connections |
| Session affinity required, immutable fleet | IP hash or cookie affinity |
| Elastic fleet (servers added/removed often) | Consistent hashing |
| Cache pool (Memcached/Redis sharding) | Consistent hashing |

---

## Interview Questions to Practice

1. **"What is the difference between L4 and L7 load balancers? When would you use each?"**
   *L4 routes based on IP/port, no packet inspection — fastest, used for raw TCP like DB proxies or non-HTTP. L7 routes on URL, headers, cookies — enables content-based routing (send /videos to video servers), SSL termination, canary deploys. In practice, use L7 (ALB/Nginx) for all web traffic; L4 for non-HTTP or when you need minimal latency overhead.*

2. **"Why is consistent hashing better than modulo hashing for a cache pool?"**
   *With modulo hashing (`hash(key) % N`), adding one cache node changes N, remapping ~(N-1)/N of all keys — a cache miss storm. With consistent hashing, adding one node only moves ~1/N of keys — all other keys still route to their original node. This is critical for cache warmth: remapping 100% of keys means 100% cache miss rate until the new nodes warm up.*

3. **"How would you design a highly available load balancer?"**
   *Two approaches: (1) Active-passive with VRRP — two LB instances share a floating IP; passive takes over in ~1s if active dies. (2) Cloud-managed LB (ALB, GCP LB) — natively distributed across AZs by the provider, no VRRP needed. In AWS, always use ALB/NLB over self-managed HAProxy; the managed service handles HA, scaling, and certificate renewal.*

4. **"How does GSLB route a user to the right data center?"**
   *Two mechanisms: (1) DNS-based — the GSLB DNS server returns a different A record based on the client's IP geolocation or latency. TTL is usually 30–60s, so failover takes a minute. (2) Anycast — multiple DCs advertise the same IP via BGP; the internet's routing layer sends packets to the nearest one. Anycast failover is nearly instant (BGP re-convergence ~30s). Cloudflare and most CDNs use anycast for edge PoPs.*

5. **"A backend server is responding slowly but not returning errors. How does the LB handle it?"**
   *Standard health checks only detect down servers (no response or 5xx). A slow server that returns 200 stays in rotation. Fix: (1) Set connection/request timeouts — if a backend doesn't respond within Xms, fail the request and retry on another backend. (2) Use least-connections algorithm — slow servers accumulate connections faster and stop receiving new ones naturally. (3) Passive health checks with error-rate thresholds — if p99 latency from a backend exceeds threshold, reduce its weight or remove it.*

---

## Applied In

**High-Level Design** — where the LB tier is a deep-dive topic, not just a box:

- [Design a Stock Exchange](../../05-hld-problems/03-hard/stock-exchange.md) — least-outstanding-requests to shed slow matching-engine replicas
- [Design a Chat System](../../05-hld-problems/03-hard/chat-system.md) — L4 for sticky WebSocket connections vs L7 for HTTP APIs
- [Design Ride Sharing](../../05-hld-problems/03-hard/ride-sharing.md) — GSLB to route drivers to the nearest regional cluster
- [Design a URL Shortener](../../05-hld-problems/01-easy/url-shortener.md) — the simplest place to reason about active-active LB HA
