---
id: bounded-queues
tags: [lld, concurrency, java, backpressure]
confidence: 3
last-rehearsed: 2026-07-21
source: 6. Scalability — "Bounded queues, always"
---
# Bounded queues

**Claim in one sentence.** An unbounded queue converts a throughput problem into a memory problem and then a crash — and `Executors.newFixedThreadPool()` gives you one by default.

## The defect

```java
// Unbounded — a slow consumer becomes OOM
private final Queue<Task> queue = new LinkedList<>();

// Bounded — backpressure is expressed in the type
private final BlockingQueue<Task> queue = new ArrayBlockingQueue<>(1000);
```

The single most common scalability defect in an LLD design. An unbounded queue doesn't fail when it's overloaded — it *absorbs*, silently, growing latency for everything already in it, until the heap is gone. The crash arrives long after the overload started and looks like a memory bug rather than a capacity one.

A bound makes the overload visible at the moment it happens.

## The JDK trap

**`Executors.newFixedThreadPool()` uses an unbounded `LinkedBlockingQueue` internally.** Under overload it queues until OOM rather than rejecting. So does `newSingleThreadExecutor`. `newCachedThreadPool` has the opposite problem — an unbounded *thread* count.

Construct `ThreadPoolExecutor` directly with a bounded queue and an explicit `RejectedExecutionHandler`. Naming this default is a strong signal, because it's a trap that ships in production constantly.

## Choosing the full-queue policy

Having bounded it, you must choose what happens when it fills — and stating the choice is the point:

| Policy | Behavior | Use when |
|---|---|---|
| `put()` | Block the producer | Producer can afford to slow down |
| `offer()` | Return false, drop | Data is droppable (metrics, logs) |
| `offer(timeout)` | Wait, then give up | Bounded latency requirement |
| Reject + 503 | Shed load | Request-serving path |

Blocking the producer *is* backpressure — it propagates slowness upstream instead of hiding it. On a request path you generally don't want it, because the caller is a user; you want a fast `503` with `Retry-After`.

## What you say in an interview

> "Bounded queue, always — unbounded turns overload into an OOM that shows up minutes later looking like a memory leak. Worth knowing that `newFixedThreadPool` uses an unbounded queue internally, so I'd build the `ThreadPoolExecutor` directly with a bounded queue and an explicit rejection handler. On a request path rejection means a 503 with `Retry-After`; for a producer that can slow down, blocking is real backpressure."

## Sizing the bound

Queue depth is latency: a 1,000-deep queue drained at 500/s means the last entry waits two seconds. Size it from the latency you're willing to add, not from a round number — and if the answer is "requests would wait longer than the client timeout," the queue should be shorter, since those entries are dead on arrival.

## Probes you should survive

- *"How deep should the queue be?"* → Depth ÷ drain rate = added latency. Size against the client timeout; anything queued beyond it is wasted work.
- *"Isn't rejecting worse than queueing?"* → Rejecting fast is honest and lets the client retry elsewhere. Queueing past the client's timeout burns capacity on requests nobody is waiting for anymore.
- *"What does `CallerRunsPolicy` do?"* → Runs the task on the submitting thread — throttling the producer by occupying it. Effective backpressure, but it stalls whatever that thread was doing, including an accept loop.
- *"Why is OOM the bad failure mode?"* → It's delayed and non-local. The process dies well after the overload, taking healthy in-flight work with it, and the stack trace points at allocation rather than capacity.

## Related

[littles-law](./littles-law.md) · [load-shedding-ladder](./load-shedding-ladder.md) · [timeout-budgets](./timeout-budgets.md) · [contention-reduction](./contention-reduction.md)
