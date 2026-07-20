# Scale an App to Millions of Users

> **Topic from**: [Scale an App to Millions of Users - System Design](https://www.youtube.com/watch?v=tjubQ97lxA4) — Caleb Curry
>
> **Note**: These notes are written from general knowledge on the topic, not transcribed from the video.

**Prerequisites** — this file assumes the fundamentals and does not repeat them:
- The tier-by-tier evolution (1K → 10K → 100K → 1M → 10M+ users) → [Architecture by scale](../../07-interview-templates/02-cheat-sheets/04-architecture-by-scale.md)
- Vertical vs horizontal, statelessness → [Scaling fundamentals](../../03-scaling/01-scaling-fundamentals.md)
- Index → cache → replicas → sharding staircase → [Database scaling](../../03-scaling/03-database-scaling.md)
- Geo-routing, active-active, data sovereignty → [Global distribution](../../03-scaling/04-global-distribution.md)

The linked files answer **what to add at each stage**. This file answers **why scaling stops working** — the quantitative limits, and what you do when you hit them. That is the senior conversation.

**Related in this directory** — this file supplies the quantitative backing for the others:
- [Caching](../4.%20Caching/01-caching-hld-and-lld.md) — the tail-latency argument for why caching does not fix p99
- [Load Balancing](../5.%20Load%20Balancing/01-load-balancing-hld-and-lld.md) — retry budgets and outlier ejection as load shedding
- [Rate Limiting](../7.%20Rate%20Limiting/README.md) — admission control at the edge, the first line of the shedding ladder

---

# Part 1 — HLD

## Why adding servers stops helping

The tier-by-tier story implies scaling is additive: more users, more servers. It isn't, and knowing the shape of the curve is what separates a senior answer.

### Amdahl's Law — the serial fraction caps you

If a fraction `s` of the work is inherently serial, maximum speedup with N workers is:

```
speedup = 1 / (s + (1 - s)/N)
as N → ∞:  speedup → 1/s
```

At **5% serial work, the ceiling is 20×** — no matter how many machines you buy. Ten thousand servers and twenty servers land in nearly the same place.

In a real system, the serial fraction is the shared thing every request touches: a single primary database accepting writes, a distributed lock, a sequence generator, an auth service. Scaling the stateless tier while every request still funnels through one primary means you are buying servers against a fixed ceiling.

### The Universal Scalability Law — throughput can *decrease*

Amdahl is optimistic: it assumes adding workers never actively hurts. USL adds a **coherence** term for the cost of workers coordinating with each other:

```
C(N) = N / (1 + α(N-1) + βN(N-1))
       α = contention (serial work)
       β = coherence  (cross-talk between workers)
```

Because the `βN²` term grows quadratically, throughput **peaks and then falls**. Past a certain node count, adding capacity makes the system slower. Anyone who has watched a database cluster degrade as replicas were added has seen this.

Concrete coherence costs: cache invalidation broadcast to all nodes, gossip protocols, consensus rounds needing a quorum, distributed lock negotiation. Each additional node makes every other node do more work.

The practical takeaway for an interview: **"we'd add nodes until the coherence cost dominates, then shard into independent units instead."** Sharding works precisely because it converts one coordinating cluster into many non-coordinating ones — it drives β toward zero. That is the real reason sharding is the endgame, and it is a much better answer than "sharding splits the data."

### Little's Law — the one to actually compute

```
L = λ × W
concurrency = arrival_rate × latency
```

Simple and constantly useful:

```
10,000 req/s × 200 ms latency = 2,000 concurrent requests in flight
```

That determines thread pool sizes, connection pool sizes, and memory. It also exposes a trap: **if latency rises, concurrency rises even at constant traffic.** Latency doubling to 400 ms means 4,000 in-flight requests — thread pools saturate, connections exhaust, and the system falls over without any traffic increase. Most cascading failures start here.

### Utilization and the latency knee

Queueing theory gives the response time multiplier at utilization ρ:

```
latency_multiplier ≈ 1 / (1 - ρ)
```

| Utilization | Latency vs idle |
|---|---|
| 50% | 2× |
| 70% | 3.3× |
| 80% | 5× |
| 90% | 10× |
| 95% | 20× |

This is why **running servers at 90% CPU is not efficient — it is fragile.** The curve is non-linear: the jump from 80% to 90% doubles latency, and 90% to 95% doubles it again. Target 50–70% for latency-sensitive services, and treat the headroom as the cost of predictable tail latency rather than waste.

It also explains why autoscaling on CPU is often too late — by the time CPU hits the threshold, latency has already degraded and the queue has built.

## Tail latency at scale

At small scale, average latency describes the system. At large scale, **the tail is the system**.

If a request fans out to 100 services and each has a p99 of 10 ms, the chance that *all* 100 come back fast is `0.99^100 ≈ 37%`. So **63% of requests hit at least one slow dependency.** Your p99 becomes the common case.

This is why fan-out architectures need:
- **Hedged requests** — after p95 elapses, send a duplicate to another replica, take the first response. Costs a few percent extra load, cuts the tail dramatically.
- **Tied requests** — the duplicate carries a cancellation for the original once one starts.
- **Fan-out limits** — every additional dependency multiplies tail exposure.

Interview signal: when asked "how do you reduce p99 in a fan-out system," the answer is hedging, not caching. Caching improves the average; the tail is where the miss path lives.

## What to do when you cannot add capacity

Scaling has a ceiling within any given time window — autoscaling takes minutes, and traffic spikes take seconds. The senior answer is that you shed load rather than fall over.

**Load shedding** — reject a fraction of requests early, at the edge, to keep the rest fast. A system serving 80% of traffic well beats one serving 100% at 30-second latency. Shed by priority: drop background refreshes and analytics before checkout.

**Brownout / graceful degradation** — reduce feature richness rather than availability. Turn off recommendations, serve stale cache, drop personalization, return a smaller page. The site stays up in a diminished form.

**Backpressure** — propagate "slow down" upstream instead of buffering. Unbounded queues turn a throughput problem into a memory problem and then a crash; bounded queues that reject when full fail predictably.

**Admission control** — cap concurrency at the entry point. If Little's Law says 2,000 in flight is your capacity, admitting 10,000 does not serve more users; it makes all 10,000 slow and times most of them out.

The ranking to state: **shed selectively > degrade features > queue > fall over.** Most candidates never mention the first two.

## Cost, briefly

At the millions-of-users tier, architecture decisions are cost decisions. Two worth knowing: **cross-AZ data transfer is billed** (chatty microservices across zones can cost more than compute), and **provisioning for peak wastes the difference between peak and median** — which is why autoscaling and spot/preemptible capacity for batch work matter.

---

# Part 2 — LLD

Scalability shows up in LLD as: does this class fall apart under concurrency?

## Bounded queues, always

The single most common scalability defect in an LLD design:

```java
// Unbounded — a slow consumer becomes OOM
private final Queue<Task> queue = new LinkedList<>();

// Bounded — backpressure is expressed in the type
private final BlockingQueue<Task> queue = new ArrayBlockingQueue<>(1000);
```

With the bounded version you must then choose a full-queue policy, and stating the choice is the point:

| Policy | Behavior | Use when |
|---|---|---|
| `put()` | Block the producer | Producer can afford to slow down |
| `offer()` | Return false, drop | Data is droppable (metrics, logs) |
| `offer(timeout)` | Wait, then give up | Bounded latency requirement |
| Reject + 503 | Shed load | Request-serving path |

`Executors.newFixedThreadPool()` uses an **unbounded** `LinkedBlockingQueue` internally — under overload it queues until OOM rather than rejecting. Constructing `ThreadPoolExecutor` directly with a bounded queue and an explicit `RejectedExecutionHandler` is the correct move, and naming that default is a strong signal.

## Sizing the pool from Little's Law

```
threads = target_throughput × avg_latency
```

For 500 req/s at 40 ms: `500 × 0.04 = 20 threads`. For CPU-bound work, cores + 1. For I/O-bound work, far more than cores — the threads are mostly parked.

The mistake is sizing by intuition ("100 threads sounds good"). Oversized pools cause context-switch thrash and memory pressure; undersized pools leave throughput unclaimed. Deriving the number is the answer.

## Shared mutable state is the serial fraction

Amdahl's `s` in code form: every `synchronized` block on a shared object is serial work that caps your throughput regardless of core count.

```java
// Every increment serializes on one lock
private long count;
public synchronized void increment() { count++; }

// CAS — better, still one contended cache line
private final AtomicLong count = new AtomicLong();

// Striped — threads touch different cells, contention collapses
private final LongAdder count = new LongAdder();
```

`LongAdder` maintains per-thread cells summed on read — it trades read cost for write scalability. Under high write contention it is dramatically faster than `AtomicLong`, and it is the direct LLD expression of "reduce the serial fraction." See [36-design-high-contention-counter](../../06-lld/05-problems/04-advanced-niche/36-design-high-contention-counter.md).

## Avoid coordination rather than optimizing it

USL's β term in code: prefer designs where threads do not talk.

- Thread-local accumulation, merged at the end, beats a shared structure with a lock
- Immutable objects need no synchronization at all
- Copy-on-write suits extreme read/write ratios (as in the [load balancer registry](../5.%20Load%20Balancing/01-load-balancing-hld-and-lld.md))
- Sharded locks beat one global lock — the same insight as database sharding, one level down

## Timeouts are mandatory

A call without a timeout is a thread leak waiting for a network hiccup. Once threads are parked on a dead dependency, the pool drains and the service stops serving *everything* — the classic cascading failure.

```java
HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
request.timeout(Duration.ofSeconds(5));
```

Timeout budgets must **decrease** down the call chain. If the client has 5 s and you call a dependency with a 10 s timeout, the client has already given up while you hold a thread waiting. Pass the remaining budget as a deadline.

---

# Connecting the layers

| HLD concern | LLD expression |
|---|---|
| Amdahl serial fraction | `synchronized` on shared state → `LongAdder` |
| USL coherence cost | Thread-local / immutable / sharded locks |
| Little's Law sizing | Thread and connection pool sizing |
| Backpressure | Bounded `BlockingQueue` + rejection policy |
| Load shedding | `RejectedExecutionHandler` → 503 |
| Cascading failure | Timeouts + decreasing deadline budget |

---

## Common mistakes

| Mistake | Fix |
|---|---|
| "Add more servers" with no ceiling named | Amdahl: 5% serial → 20× cap |
| Assuming throughput grows monotonically | USL: coherence makes it peak and fall |
| Targeting 90% utilization as efficient | 90% = 10× latency; target 50–70% |
| Optimizing average latency in fan-out | 100 deps at p99 10ms → 63% hit a slow one |
| No load-shedding story | Shed > degrade > queue > collapse |
| Unbounded queue | `ArrayBlockingQueue` + explicit policy |
| `newFixedThreadPool` under overload | `ThreadPoolExecutor` + bounded queue |
| Pool size by intuition | `threads = throughput × latency` |
| Calls without timeouts | Timeouts + decreasing budget |
| Sharding explained as "splits data" | Sharding removes **coordination** (β → 0) |

---

## Key takeaways

- **Amdahl**: a 5% serial fraction caps you at 20× regardless of fleet size — find the shared thing every request touches
- **USL**: coordination cost is quadratic, so throughput **peaks then declines**; sharding wins because it eliminates coordination, not because it splits data
- **Little's Law** (`L = λW`) sizes every pool you own — and rising latency raises concurrency at constant traffic, which is how cascades start
- **90% utilization means 10× latency**; headroom buys predictable tails
- In fan-out, **p99 becomes the common case** — hedge requests, don't cache
- When capacity runs out: **shed selectively, then degrade features**, before queueing or collapsing
- In LLD: bounded queues, pool sizes derived not guessed, `LongAdder` over `AtomicLong` under contention, timeouts everywhere
