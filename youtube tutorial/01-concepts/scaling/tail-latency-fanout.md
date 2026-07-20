---
id: tail-latency-fanout
tags: [scalability, tail-latency, microservices]
confidence: 3
last-rehearsed: 2026-07-21
source: 6. Scalability — "Tail latency at scale"
---
# Tail latency in fan-out

**Claim in one sentence.** Fan out to 100 services with p99 = 10 ms each and 63% of requests hit at least one slow dependency — your p99 becomes the common case, and the fix is hedging, not caching.

## The arithmetic

```
P(all 100 fast) = 0.99^100 ≈ 37%
⇒ 63% of requests hit at least one slow dependency
```

At small scale, average latency describes the system. At fan-out scale, **the tail is the system**. Each dependency you add multiplies tail exposure, so a design that looks fine per-service is broken in aggregate.

Worth internalizing: every individual service here is meeting its SLO. Nobody is misbehaving. The failure is emergent from the fan-out itself, which is why it can't be fixed by tuning any one service.

## The fixes

- **Hedged requests** — after p95 elapses, send a duplicate to another replica and take the first response. Costs a few percent extra load, cuts the tail dramatically. The insight is that a slow request is usually slow because of *that replica's* transient state (GC pause, cold cache, noisy neighbor), not because the work is inherently slow.
- **Tied requests** — the duplicate carries a cancellation for the original, so whichever starts first kills the other. Same benefit, less wasted work.
- **Fan-out limits** — every additional dependency multiplies exposure. Sometimes the answer is fewer calls, not faster ones.

## What you say in an interview

> "With a hundred dependencies at p99 of ten milliseconds, only about thirty-seven percent of requests have every call come back fast — so nearly two thirds hit at least one slow one and my p99 is the normal case. Caching won't fix that; it improves the average and the tail *is* the miss path. What fixes it is hedging: after p95 elapses, fire a duplicate to another replica and take whichever answers first. A few percent extra load for a large tail improvement."

**Interview signal:** when asked "how do you reduce p99 in a fan-out system," the answer is hedging. Reaching for caching is the common wrong turn — see [working-set-sizing](../caching/working-set-sizing.md), where the latency math shows the cache does nothing for the miss path.

## Probes you should survive

- *"Why does hedging work at all?"* → Slowness is usually replica-specific and transient — a GC pause, a cold cache, a noisy neighbor. A second replica is unlikely to be in the same bad state at the same moment.
- *"Doesn't hedging add load?"* → A few percent, because you only hedge past p95 — by definition at most 5% of requests. That's the trade, and it's a good one.
- *"When does hedging fail?"* → When slowness is systemic rather than per-replica — an overloaded fleet or a slow shared dependency. Then the hedge adds load to an already-struggling system and makes it worse. Hedging needs a budget for the same reason [retries](../load-balancing/retry-budgets.md) do.
- *"How do you reduce fan-out?"* → Batch calls to the same service, denormalize so fewer services are needed, or precompute the join asynchronously.

## Related

[utilization-latency-knee](./utilization-latency-knee.md) · [retry-budgets](../load-balancing/retry-budgets.md) · [working-set-sizing](../caching/working-set-sizing.md) · [timeout-budgets](./timeout-budgets.md)
