---
id: littles-law
tags: [scalability, queueing-theory, capacity-planning]
confidence: 3
last-rehearsed: 2026-07-21
source: 6. Scalability — "Little's Law" + "Sizing the pool from Little's Law"
---
# Little's Law

**Claim in one sentence.** `concurrency = arrival rate × latency` sizes every pool you own — and its sharp edge is that *rising latency raises concurrency at constant traffic*, which is where most cascading failures begin.

## The formula

```
L = λ × W
concurrency = arrival_rate × latency

10,000 req/s × 200 ms = 2,000 concurrent requests in flight
```

That number determines thread pool sizes, connection pool sizes, and memory.

## The trap

**If latency rises, concurrency rises even with no traffic increase.** Latency doubling to 400 ms means 4,000 in-flight requests. Thread pools saturate, connections exhaust, and the system falls over — while the traffic graph is flat.

This is the mechanism behind most cascading failures. A dependency slows down, your in-flight count doubles, your pool fills, and now *you* are the slow dependency for everyone upstream. The failure propagates backward through the call graph even though nobody sent more requests.

## Sizing a pool

```
threads = target_throughput × avg_latency
```

500 req/s at 40 ms → `500 × 0.04 = 20 threads`.

Adjustments: CPU-bound work wants roughly **cores + 1** (more threads just thrash). I/O-bound work wants far more than cores, since the threads are mostly parked waiting.

The mistake is sizing by intuition — "100 threads sounds good." Oversized pools cause context-switch thrash and memory pressure; undersized pools leave throughput unclaimed. Deriving the number out loud *is* the answer.

## What you say in an interview

> "Little's Law: concurrency is arrival rate times latency. Ten thousand a second at two hundred milliseconds is two thousand in flight, and that's what sizes the thread and connection pools. The part I'd watch is that latency is a multiplier — if a dependency slows and latency doubles, in-flight doubles with no extra traffic, the pool saturates, and now I'm the slow dependency upstream. That's how cascading failures start, which is why I'd pair the pool with a timeout and admission control."

## Note it is not universal

This sizes **concurrency from a rate**. It does not size resources that bind on exhaustion — file descriptors, socket buffers, ephemeral ports. Those have a ceiling rather than a rate, and [lb-sizing](../load-balancing/lb-sizing.md) is the case where that distinction matters. Knowing which model applies to which resource is the actual skill.

## Probes you should survive

- *"Which latency — average or p99?"* → Average gives you steady-state concurrency. Size against p99 or degraded latency if you want the pool to survive a bad dependency rather than just a normal day.
- *"What happens when the pool is full?"* → Whatever you configured, and you must have decided: block, reject with 503, or queue. The default of unbounded queueing is the wrong answer. See [bounded-queues](./bounded-queues.md).
- *"Does this apply to async / non-blocking?"* → The math holds; the resource changes. You're sizing memory for in-flight state rather than threads, which is why async raises the concurrency ceiling but doesn't remove it.
- *"How does this connect to autoscaling?"* → Concurrency and queue depth lead CPU. Scaling on CPU is late, because latency has already degraded by the time CPU crosses the threshold.

## Related

[amdahl-and-usl](./amdahl-and-usl.md) · [utilization-latency-knee](./utilization-latency-knee.md) · [bounded-queues](./bounded-queues.md) · [timeout-budgets](./timeout-budgets.md) · [lb-sizing](../load-balancing/lb-sizing.md)
