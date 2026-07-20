---
id: timeout-budgets
tags: [lld, reliability, distributed-systems]
confidence: 3
last-rehearsed: 2026-07-21
source: 6. Scalability — "Timeouts are mandatory"
---
# Timeout budgets

**Claim in one sentence.** A call without a timeout is a thread leak waiting for a network hiccup — and timeouts must *decrease* down the call chain, or you hold a thread for a caller who already gave up.

## Why a missing timeout is fatal

Threads park on a dead dependency. The pool drains. The service stops serving **everything**, including requests that don't touch that dependency. That's the classic cascading failure, and the blast radius is far wider than the original fault.

```java
HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
request.timeout(Duration.ofSeconds(5));
```

Both matter and they're different: connect timeout covers establishing the socket (should be short — a healthy connect is milliseconds), request timeout covers the whole exchange.

## The budget must shrink

If the client has 5 s and you call a dependency with a 10 s timeout, the client has already given up while you sit holding a thread waiting for an answer nobody will read.

```
client        5s
 └─ service   4s   (leaves 1s for your own work + response)
     └─ dep   3s
```

Pass the **remaining budget** as a deadline, not a fixed timeout — each hop subtracts its elapsed time and forwards what's left. gRPC does this natively with deadline propagation; over HTTP you carry it in a header.

The distinction matters: a static per-hop timeout doesn't account for time already spent, so a chain of 3-second timeouts can exceed a 5-second client budget by the third hop.

## What you say in an interview

> "Every network call gets a timeout, or a hiccup becomes a thread leak and the pool drains — then the service stops serving everything, not just the calls touching that dependency. And the budget has to shrink down the chain: if the client gave me five seconds, I can't call a dependency with ten, or I'm holding a thread for someone who's already gone. I'd propagate a deadline rather than a fixed timeout, so each hop subtracts what's already elapsed."

## Setting the value

From the dependency's p99, plus margin — not from a round number. A timeout below p99 fails healthy requests; far above it means you wait long past the point the call was going to succeed.

Timeout, [retry budget](../load-balancing/retry-budgets.md), and circuit breaker are one system: the timeout bounds a single attempt, the retry budget bounds aggregate attempts, the breaker stops attempting altogether. Any one alone has a gap.

## Probes you should survive

- *"How do you pick the number?"* → The dependency's p99 plus margin. Anything lower fails requests that would have worked.
- *"Timeout vs deadline?"* → A timeout is per-call and restarts each hop; a deadline is absolute and propagates. Deadlines are correct for a call chain because they account for elapsed time.
- *"What if the dependency is slow but working?"* → You time out and it keeps processing — wasted work on their side, and a mutation may still commit after you gave up. That's precisely why retries need [idempotency keys](../api-design/idempotency-keys.md).
- *"Does timing out free the thread?"* → In blocking I/O only when the timeout is actually enforced by the client library. A `Socket` with no `soTimeout` blocks forever regardless of any higher-level timeout you configured.

## Related

[littles-law](./littles-law.md) · [retry-budgets](../load-balancing/retry-budgets.md) · [bounded-queues](./bounded-queues.md) · [idempotency-keys](../api-design/idempotency-keys.md) · [tail-latency-fanout](./tail-latency-fanout.md)
