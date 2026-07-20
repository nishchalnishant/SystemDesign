---
id: backend-registry-cow
tags: [lld, concurrency, java, copy-on-write]
confidence: 3
last-rehearsed: 2026-07-21
source: 5. Load Balancing — "Connection counting" + "Health state belongs in a registry" + "Draining as a third state"
---
# Backend registry: copy-on-write and connection counting

**Claim in one sentence.** Health state is read every request and written every few seconds, so the registry recomputes an immutable snapshot on write and lets readers take a `volatile` reference with no lock at all.

## Copy-on-write registry

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

**The read/write ratio is the justification**, and stating it is the point — not the code. Reads happen on every request; health changes happen every few seconds. Rebuilding the whole list on each change is wasteful in isolation and obviously correct given a ratio of maybe 10⁶:1.

`volatile` gives the visibility guarantee: a reader sees either the old complete list or the new complete list, never a partially-built one. The list is immutable, so no reader can be affected by a concurrent rebuild.

## Connection counting must be exact

`activeConnections` is read by the strategy and mutated by every request thread. A non-atomic increment/decrement leaks counts — and a backend whose count drifts upward is silently starved of traffic forever, because P2C never picks it.

```java
public class Backend {
    private final AtomicInteger active = new AtomicInteger();

    public void acquire() { active.incrementAndGet(); }
    public void release() { active.decrementAndGet(); }
    public int activeConnections() { return active.get(); }
}
```

The release **must** be in a `finally`:

```java
Backend b = strategy.select(registry.healthy());
b.acquire();
try {
    return forward(request, b);
} finally {
    b.release();
}
```

An exception path that skips the release permanently inflates the count. This is the single most common bug in an LLD load balancer, and interviewers look for the `finally` specifically.

## Draining is a third state

Health is not a boolean. A draining backend takes **no new requests but still has in-flight ones**, so it's neither in the healthy list nor immediately deletable.

```java
public enum BackendState { HEALTHY, DRAINING, UNHEALTHY }
```

Removal waits until `activeConnections()` reaches zero or the drain timeout fires — which is precisely why the counter has to be exact. It's the termination condition, not just an input to routing. This enum is the LLD expression of [connection draining](./lb-lifecycle.md); connecting them out loud is worth doing.

## What you say in an interview

> "Health state goes in a registry, not the strategy. Reads happen per request and writes every few seconds, so I'd rebuild an immutable snapshot on each health change and have readers take a volatile reference — no locking on the hot path. Connection counts need to be atomic with the decrement in a finally block, because a leaked count means that backend never gets picked again. And health is three states, not two — draining takes no new work but still has requests in flight."

## Probes you should survive

- *"Why not `CopyOnWriteArrayList`?"* → Same idea, but it copies per mutation. Here one health event may change several entries, so one explicit rebuild is cheaper and clearer.
- *"Why is `volatile` enough?"* → Only the reference is mutated, and the list is immutable. Readers see one complete version or another.
- *"What if the count leaks?"* → That backend looks permanently busy and P2C stops choosing it. Silent capacity loss with no error anywhere — the worst kind of bug.
- *"When can a draining backend be removed?"* → When `activeConnections()` hits zero, or the drain timeout fires. The counter is the termination condition.

## Related

[strategy-injection](./strategy-injection.md) · [lb-lifecycle](./lb-lifecycle.md) · [concurrency-in-contract](../api-design/concurrency-in-contract.md) · [contention-reduction](../scaling/contention-reduction.md)
