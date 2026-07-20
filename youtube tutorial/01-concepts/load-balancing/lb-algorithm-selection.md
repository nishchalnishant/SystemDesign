---
id: lb-algorithm-selection
tags: [load-balancing, p2c, distributed-systems]
confidence: 3
last-rehearsed: 2026-07-20
source: 5. Load Balancing — "Least-connections is not the default answer"
---
# LB algorithm selection

**Claim in one sentence.** Least-connections routes *more* traffic to a backend that fails fast, because instant 500s look identical to being idle — and P2C is the actual default at scale because it approximates least-connections without any cross-LB coordination.

## Why least-connections fails

Least-connections prefers the backend with the fewest in-flight requests. A backend returning instant 500s completes every request immediately, so its connection count sits near zero — and the algorithm reads that as "most available." The sickest host gets the most traffic.

This is why least-connections needs [outlier detection](./lb-lifecycle.md) alongside it, not instead of it. The algorithm cannot distinguish fast from broken; only response-code observation can.

**Round robin** has the opposite blindness: it ignores backend state entirely, so a slow server keeps taking its full share and requests queue on the sick host. Fine when request cost is uniform and backends are identical — wrong the moment either breaks.

## Why P2C wins

Pick two backends at random; send to whichever has fewer connections.

```
candidates = random.sample(backends, 2)
target = min(candidates, key=lambda b: b.active_connections)
```

Exact least-connections requires every LB instance to know every backend's live state — with N load balancers that's a synchronization problem, and stale state makes the choice wrong anyway. P2C needs two local lookups and no shared state, and maximum load lands within a constant factor of the true minimum rather than degrading as the cluster grows.

Default in Envoy and NGINX Plus. Naming it *and* explaining the no-coordination property is the senior signal.

## What you say in an interview

> "I'd use P2C rather than least-connections. Exact least-connections needs global state across every LB instance, which doesn't scale and is stale by the time you read it — P2C picks two at random and takes the better one, which gets you within a constant factor with zero coordination. And least-connections has a nasty failure anyway: a backend returning instant 500s looks idle, so it attracts more traffic. That's why you pair any connection-based algorithm with outlier detection."

## The table

| Algorithm | Use when | Breaks when |
|---|---|---|
| Round robin | Uniform cost, identical backends | Slow or heterogeneous backends |
| Weighted RR | Known-unequal capacity | Capacity changes at runtime |
| Least connections | Variable request cost | Backends fail fast (looks idle) |
| **P2C** | Default for large fleets | Rarely — this is the safe default |
| Consistent hashing | Cache locality, sticky state | You wanted even distribution |

Consistent hashing is for when a request must reach a *specific* backend — cache locality, sticky sessions, sharded state — not for general balancing. Maglev, Google's variant, trades slightly more disruption on failure for much faster lookup via a precomputed table.

## Probes you should survive

- *"Why not exact least-connections?"* → Requires global state across LB instances. P2C gets nearly the same distribution with local information only.
- *"Why two choices and not three?"* → Two captures almost all the benefit; the improvement from three is marginal. The jump from one (random) to two is the large one.
- *"When is round robin right?"* → Uniform request cost and homogeneous backends — and it's cheap. Don't over-engineer if that genuinely holds.
- *"Consistent hashing for load balancing?"* → Only when locality matters more than evenness. It will produce uneven load, by design.

## Related

[lb-lifecycle](./lb-lifecycle.md) · [retry-budgets](./retry-budgets.md) · [strategy-injection](./strategy-injection.md) · [hot-keys](../caching/hot-keys.md)
