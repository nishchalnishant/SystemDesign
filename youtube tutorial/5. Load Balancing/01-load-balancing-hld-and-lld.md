# The Ultimate Guide to Load Balancers

> **Topic from**: [The Ultimate Guide to Load Balancers (System Design Fundamentals)](https://www.youtube.com/watch?v=xg7Dj2AXLyk) — Software Developer Diaries
>
> **Note**: These notes are written from general knowledge on the topic, not transcribed from the video.

**Prerequisites** — this file assumes the fundamentals and does not repeat them:
- L4 vs L7, round robin / weighted / least-connections, consistent hashing with virtual nodes, session affinity, GSLB, VRRP HA → [Load balancers](../../02-building-blocks/01-networking/01-load-balancers.md)
- Forward vs reverse proxy, LB vs API gateway vs service mesh → [Proxy & load balancing](../2.%20System%20Design%20Fundamentals/12-proxy-reverse-proxy-load-balancing.md)

This file covers what those do not: **algorithm selection under real conditions**, the **deploy/failure lifecycle** (draining, slow start, outlier detection), **what the LB does to your request**, and the LLD view of a balancer as a class.

**Related in this directory:**
- [Scalability](../6.%20Scalability/01-scalability-hld-and-lld.md) — retry budgets and outlier ejection are load shedding. Note the sizing methods differ: that file derives pool sizes from Little's Law, while the Sizing section below binds on resource exhaustion (file descriptors, ephemeral ports) instead
- [Caching](../4.%20Caching/01-caching-hld-and-lld.md) — why consistent hashing at the LB cannot fix a hot key
- [API Design](../3.%20API%20Design/01-api-design-hld-and-lld.md) — the strategy-injection pattern the registry design reuses

---

# Part 1 — HLD

## Least-connections is not the default answer

The standard list — round robin, weighted, least-connections — hides the fact that each fails differently at scale.

**Round robin** ignores backend state entirely. One slow server keeps receiving its full share, so requests queue on the sick host. Fine when request cost is uniform and backends are identical; wrong the moment either assumption breaks.

**Least-connections** tracks in-flight requests and prefers the idlest backend. Better — but it has a specific failure mode worth knowing: a backend that fails *fast* (instant 500s) shows near-zero connections, so least-connections routes **more** traffic to the broken host. Failing fast looks like being idle. This is why least-connections needs outlier detection alongside it, not instead of it.

**Power of two choices (P2C)** is what large systems actually run. Pick two backends at random, send to whichever has fewer connections.

```
candidates = random.sample(backends, 2)
target = min(candidates, key=lambda b: b.active_connections)
```

The result is close to optimal load distribution without any global coordination. Exact least-connections requires every LB instance to know every backend's state — with N load balancers that is a synchronization problem. P2C needs only two lookups and no shared state, and the maximum load lands within a constant factor of the true minimum rather than growing with cluster size. Naming P2C and explaining *why* — no coordination — is a strong senior signal. It is the default in Envoy and NGINX Plus.

**Consistent hashing** is for when a request must reach a *specific* backend (cache locality, sticky sessions, sharded state), not for general balancing. Maglev — Google's variant — trades a little disruption on failure for much faster lookup via a precomputed table, which matters at the scale where hashing itself becomes the bottleneck.

| Algorithm | Use when | Breaks when |
|---|---|---|
| Round robin | Uniform cost, identical backends | Slow or heterogeneous backends |
| Weighted RR | Known-unequal backend capacity | Capacity changes at runtime |
| Least connections | Variable request cost | Backends fail fast (looks idle) |
| **P2C** | Default for large fleets | Rarely — this is the safe default |
| Consistent hashing | Cache locality, sticky state | You wanted even distribution |

## The lifecycle: what happens when servers come and go

This is the part interviews probe and most notes omit. A load balancer's hard job is not steady state — it is transitions.

### Connection draining (graceful shutdown)

Deploying by killing a backend drops its in-flight requests. Draining fixes it:

1. Mark the backend **out of rotation** — no new requests routed to it
2. Let existing requests finish, up to a **drain timeout** (typically 30–300 s)
3. Terminate after the timeout, force-closing whatever remains

Set the drain timeout above your p99 request duration. Below it, you are cutting off real requests during every deploy. Long-lived connections (WebSocket, SSE, gRPC streams) need explicit handling — they will never finish on their own, so the server must send a close frame or `GOAWAY` and let clients reconnect.

### Slow start (the cold-backend problem)

A newly added backend has an empty local cache, cold JIT, an unwarmed connection pool. Under least-connections it looks *maximally idle* — zero connections — so the LB floods it with traffic. It responds slowly, or falls over, and gets ejected. This is the classic failure right after autoscaling.

Slow start ramps a new backend's weight from 0 to 100% over a window (30–60 s), giving it time to warm before carrying a full share. Explicitly configured in NGINX (`slow_start=30s`) and Envoy.

The same effect appears after a **restart of the whole fleet**: everything is cold simultaneously, so there is no warm capacity to absorb the load. Staggered restarts exist for this reason.

### Outlier detection (passive ejection)

Active health checks — periodic `GET /health` — catch a *dead* backend. They miss a backend that is alive but degraded, because `/health` may still return 200 while real requests time out.

Outlier detection watches actual traffic: eject a backend after N consecutive 5xx responses (Envoy default 5), keep it out for a base ejection period, and lengthen that period on each repeat offense. Two safety rails matter:

- **Max ejection percentage** (e.g. 50%) — never eject so many backends that the survivors collapse under the redistributed load. Without this cap, a bad deploy that makes every backend return 5xx causes the LB to eject the entire fleet.
- **Success-rate mode** — eject on statistical deviation from the fleet median rather than a fixed threshold, so a fleet-wide failure ejects nobody.

Active + passive together is the correct answer: active for dead, passive for degraded.

### Retries and their danger

Retrying a failed request seems obviously good, and it is how a small outage becomes a total one. If every backend is struggling and each client retries 3×, offered load triples exactly when capacity has dropped — a **retry storm**.

Required controls:
- **Retry budget** — cap retries at ~10–20% of total requests, not per-request. Envoy and gRPC both implement this.
- **Only retry idempotent requests**, or requests with an idempotency key ([API design](../3.%20API%20Design/01-api-design-hld-and-lld.md))
- **Never retry the same backend** — retry elsewhere, or you are hammering a host you already know is sick
- **Jittered exponential backoff** — synchronized retries produce a thundering herd
- **Circuit breaker** as the outer guard ([circuit breaker](../../02-building-blocks/02-performance/03-circuit-breaker.md))

## What the LB does to your request

An LB is not transparent, and the changes it makes cause real bugs.

**Client IP is lost.** The backend sees the LB's IP on every connection. This breaks IP-based rate limiting, geolocation, and audit logging.

- **L7**: the LB appends `X-Forwarded-For: <client>, <proxy1>`. Trust only the entries your own proxies added — clients can forge the header, so counting from the right is the safe read.
- **L4**: no HTTP layer exists to add a header, so use the **PROXY protocol** — a small text preamble carrying the real source address before the TCP payload. Both ends must be configured for it; enabling it on only one side breaks the connection outright.

**TLS terminates at the LB.** Backends then receive plaintext, which is why the LB is where you enforce TLS version and cipher policy. If the backend network is not trusted, re-encrypt LB→backend (TLS passthrough or mTLS via service mesh). Terminating TLS also means the LB — not the backend — holds your certificates.

**Connection reuse changes the shape of traffic.** The LB keeps a keepalive pool to backends. This is why a load test through the LB and one hitting backends directly produce different numbers, and why `least-connections` counts *LB-to-backend* connections rather than client connections.

## Sizing

Two limits, and they bind at very different points:

- **Throughput** — modern software LBs handle 10k–100k req/s per instance; hardware and kernel-bypass more
- **Concurrent connections** — usually the real constraint. Each connection consumes a file descriptor and socket buffers. 100k idle WebSocket connections cost far more memory than 10k req/s of short HTTP.

For long-lived connections, size on **connections**, not requests/sec. Ephemeral port exhaustion (~28k per source IP/destination pair) is the classic wall — worked around with multiple source IPs or SO_REUSEPORT.

**Direct Server Return (DSR)** removes the LB from the response path entirely: requests go through it, responses go straight from backend to client. For video and large downloads, where responses dwarf requests, this multiplies effective capacity.

---

# Part 2 — LLD

"Design a load balancer" is a common LLD prompt. The mechanics are simple; the design points are the interfaces.

## Strategy for the algorithm

```java
public interface LoadBalancingStrategy {
    Backend select(List<Backend> healthy);
}
```

Algorithm is a strategy, injected — same principle as the eviction policy in [caching](../4.%20Caching/01-caching-hld-and-lld.md). Note the parameter is `healthy`, not all backends: filtering is the balancer's job, so no strategy has to remember to check health.

```java
public class P2CStrategy implements LoadBalancingStrategy {
    private final Random random = new Random();

    @Override
    public Backend select(List<Backend> healthy) {
        if (healthy.isEmpty()) throw new NoHealthyBackendException();
        if (healthy.size() == 1) return healthy.get(0);

        int i = random.nextInt(healthy.size());
        int j = random.nextInt(healthy.size() - 1);
        if (j >= i) j++;                       // distinct index without a retry loop

        Backend a = healthy.get(i), b = healthy.get(j);
        return a.activeConnections() <= b.activeConnections() ? a : b;
    }
}
```

The `j >= i` adjustment avoids a `while (i == j)` loop — bounded work, no worst case. Small, but the kind of thing interviewers notice.

## The empty-backend case is part of the contract

Returning `null` when nothing is healthy forces every caller to null-check, and someone will forget. Throw a domain exception, or return `Optional<Backend>` — the caller cannot then ignore it.

## Connection counting must be exact

`activeConnections` is read by the strategy and mutated by every request thread. A non-atomic increment/decrement leaks counts, and a backend whose count drifts upward is silently removed from consideration forever.

```java
public class Backend {
    private final AtomicInteger active = new AtomicInteger();

    public void acquire() { active.incrementAndGet(); }
    public void release() { active.decrementAndGet(); }
    public int activeConnections() { return active.get(); }
}
```

The release **must** be in a `finally` — an exception path that skips it permanently inflates the count:

```java
Backend b = strategy.select(registry.healthy());
b.acquire();
try {
    return forward(request, b);
} finally {
    b.release();
}
```

This is the single most common bug in an LLD load balancer, and interviewers look for the `finally`.

## Health state belongs in a registry, not the strategy

```java
public class BackendRegistry {
    private final Map<String, Backend> backends = new ConcurrentHashMap<>();
    private volatile List<Backend> healthySnapshot = List.of();

    public List<Backend> healthy() { return healthySnapshot; }   // no lock on read

    void onHealthCheck(String id, boolean up) {
        backends.get(id).setHealthy(up);
        healthySnapshot = backends.values().stream()
                                  .filter(Backend::isHealthy).toList();
    }
}
```

Reads happen on every request; health changes happen every few seconds. Recomputing an immutable snapshot on change lets the hot path read a `volatile` reference with no locking — copy-on-write, chosen because the read/write ratio is extreme. Explaining that ratio is the point, not the code.

## Draining as a third state

Health is not a boolean. A draining backend takes **no new requests but still has in-flight ones**, so it can be neither in the healthy list nor immediately deletable.

```java
public enum BackendState { HEALTHY, DRAINING, UNHEALTHY }
```

Removal waits until a draining backend's `activeConnections()` reaches zero or the drain timeout fires. Modelling this as an enum rather than a boolean is the LLD expression of the HLD draining section — a good thing to connect out loud.

---

# Connecting the layers

| HLD concern | LLD expression |
|---|---|
| Algorithm choice | Injected `LoadBalancingStrategy` |
| Health checking | `BackendRegistry` + copy-on-write snapshot |
| Connection draining | `BackendState.DRAINING` + in-flight count |
| Least-connections tracking | `AtomicInteger` + release in `finally` |
| No healthy backends | Domain exception, not `null` |

---

## Common mistakes

| Mistake | Fix |
|---|---|
| "Round robin" as the reflexive answer | P2C for large fleets; justify by coordination cost |
| Least-connections without outlier detection | Fast-failing backends look idle |
| Active health checks only | Add passive — `/health` misses degraded hosts |
| No max-ejection cap | A bad deploy ejects the entire fleet |
| Deploying without draining | Drops in-flight requests every release |
| No slow start after autoscale | Cold backend floods, falls over, gets ejected |
| Unbounded retries | Retry budget + jittered backoff, or a retry storm |
| Trusting `X-Forwarded-For` blindly | Count from the right; clients forge it |
| Sizing on req/s for WebSockets | Size on concurrent connections |
| `release()` outside `finally` | Count leaks; backend silently drops out |

---

## Key takeaways

- **P2C** is the modern default — near-optimal placement with **no cross-LB coordination**
- Least-connections misroutes to **fast-failing** backends; they read as idle
- **Draining + slow start** are the deploy-path essentials; their absence causes dropped requests and post-autoscale collapse
- **Outlier detection needs a max-ejection cap**, or a fleet-wide fault ejects everything
- Retries need a **budget**, not just backoff — otherwise a small outage becomes total
- The LB **rewrites your request**: `X-Forwarded-For` at L7, PROXY protocol at L4, TLS terminating at the edge
- Size long-lived connections on **connection count**, not requests/sec
- In LLD: strategy injection, `AtomicInteger` with `finally`, and draining as a **three-state enum**
