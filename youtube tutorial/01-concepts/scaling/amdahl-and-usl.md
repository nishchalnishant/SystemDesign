---
id: amdahl-and-usl
tags: [scalability, queueing-theory, sharding]
confidence: 3
last-rehearsed: 2026-07-21
source: 6. Scalability — "Amdahl's Law" + "The Universal Scalability Law"
---
# Amdahl, USL, and why sharding is the endgame

**Claim in one sentence.** Amdahl says a serial fraction caps your speedup; USL says coordination between workers makes throughput *peak and then fall* — and sharding is the answer precisely because it drives the coordination term toward zero.

## Amdahl — the ceiling

```
speedup = 1 / (s + (1 - s)/N)
as N → ∞:  speedup → 1/s
```

At **5% serial work the ceiling is 20×**, no matter how many machines you buy. Ten thousand servers and twenty servers land in nearly the same place.

In a real system the serial fraction is the shared thing every request touches: a single primary accepting writes, a distributed lock, a sequence generator, an auth service. Scaling the stateless tier while every request still funnels through one primary is buying servers against a fixed ceiling.

## USL — the decline

Amdahl is optimistic: it assumes adding workers never actively hurts. USL adds a **coherence** term for workers coordinating with each other:

```
C(N) = N / (1 + α(N-1) + βN(N-1))
       α = contention (serial work)
       β = coherence  (cross-talk between workers)
```

The `βN²` term grows quadratically, so throughput **peaks and then falls**. Past some node count, adding capacity makes the system slower. Anyone who has watched a database cluster degrade as replicas were added has seen this.

Concrete coherence costs: cache invalidation broadcast to all nodes, gossip protocols, consensus quorum rounds, distributed lock negotiation. Each additional node makes every *other* node do more work — that's what makes it quadratic rather than linear.

## What you say in an interview

> "There's a ceiling and then a decline. Amdahl caps you at one over the serial fraction — five percent serial means twenty times, however many machines. But the Universal Scalability Law is the sharper point: coordination between nodes grows quadratically, so throughput peaks and then actually drops. So I'd add nodes until the coherence cost dominates, then shard into independent units instead. Sharding works because it converts one coordinating cluster into many non-coordinating ones — it drives the coherence term toward zero."

That last sentence is a much better answer than "sharding splits the data."

## Probes you should survive

- *"What's the serial fraction in this design?"* → Name it concretely: the primary that takes all writes, the lock, the ID generator. A candidate who can't point at it hasn't found the bottleneck.
- *"Why would adding a replica make things slower?"* → Every write must now propagate to one more node, and consistency coordination grows with membership. That's β.
- *"How do you reduce β?"* → Partition so nodes don't need to agree. Independent shards, per-tenant isolation, eventual consistency where the domain tolerates it.
- *"Is Amdahl or USL more useful in practice?"* → USL — it predicts the observed shape, a peak followed by decline. Amdahl only predicts a plateau, which is not what real clusters do.

## Related

[littles-law](./littles-law.md) · [utilization-latency-knee](./utilization-latency-knee.md) · [contention-reduction](./contention-reduction.md) · [tail-latency-fanout](./tail-latency-fanout.md)
