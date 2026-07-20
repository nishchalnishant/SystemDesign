---
id: load-shedding-ladder
tags: [scalability, resilience, availability]
confidence: 3
last-rehearsed: 2026-07-21
source: 6. Scalability — "What to do when you cannot add capacity"
---
# The load shedding ladder

**Claim in one sentence.** Autoscaling takes minutes and spikes take seconds, so there is always a window where you cannot add capacity — and the senior answer is a ranked ladder: **shed selectively > degrade features > queue > fall over.**

## Why the window exists

Traffic spikes arrive in seconds. Instances boot in minutes. That gap is structural — no autoscaling configuration closes it. So every system has a regime where the only available lever is serving *less*, and the design question is which less.

**A system serving 80% of traffic well beats one serving 100% at 30-second latency.** The second one is usually serving 0%, because everything times out.

## The four rungs

**1. Load shedding** — reject a fraction of requests early, at the edge, to keep the rest fast. Shed by *priority*: drop background refreshes and analytics before checkout. Rejecting early matters — a request rejected after consuming a database connection has already cost you.

**2. Brownout / graceful degradation** — reduce feature richness rather than availability. Turn off recommendations, serve stale cache, drop personalization, return a smaller page. The site stays up in a diminished form.

**3. Backpressure** — propagate "slow down" upstream instead of buffering. Unbounded queues turn a throughput problem into a memory problem and then a crash; bounded queues that reject when full fail predictably. See [bounded-queues](./bounded-queues.md).

**4. Admission control** — cap concurrency at the entry point. If [Little's Law](./littles-law.md) says 2,000 in flight is your capacity, admitting 10,000 does not serve more users — it makes all 10,000 slow and times most of them out.

## What you say in an interview

> "Autoscaling takes minutes and the spike takes seconds, so there's a window where I can't add capacity and have to serve less. I'd shed selectively first — drop analytics and background refreshes, protect checkout — then degrade features, turning off recommendations and serving stale cache. Backpressure and admission control keep the queue bounded so failure is predictable rather than an OOM. Eighty percent of traffic served fast is a better outcome than a hundred percent timing out."

Most candidates never mention the first two rungs. That's the differentiator.

## Cost, briefly

At this tier, architecture decisions are cost decisions. Two worth knowing: **cross-AZ data transfer is billed** — chatty microservices across zones can cost more than the compute — and **provisioning for peak wastes the peak-to-median difference**, which is why autoscaling and spot capacity for batch work matter.

## Probes you should survive

- *"How do you decide what to shed?"* → By business priority, declared in advance. Requests carry a criticality tier; the shedder drops from the bottom. Deciding during an incident is too late.
- *"Isn't rejecting requests a failure?"* → It's a *chosen* failure with a bounded blast radius, versus an unchosen one that takes everything. Shedding 20% deliberately beats losing 100%.
- *"Where should shedding happen?"* → As early as possible — the edge or LB — before the request consumes a connection, a thread, or a database query.
- *"How does a client know to back off?"* → `503` with `Retry-After`, and the client honors it with jittered backoff. See [status-codes](../api-design/status-codes.md).

## Related

[littles-law](./littles-law.md) · [bounded-queues](./bounded-queues.md) · [utilization-latency-knee](./utilization-latency-knee.md) · [retry-budgets](../load-balancing/retry-budgets.md) · [lb-lifecycle](../load-balancing/lb-lifecycle.md)
