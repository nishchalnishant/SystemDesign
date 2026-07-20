---
id: working-set-sizing
tags: [caching, capacity-planning, estimation]
confidence: 3
last-rehearsed: 2026-07-21
source: 4. Caching — "Sizing: the question that separates levels"
---
# Working-set sizing

**Claim in one sentence.** You size a cache from the *working set* — the fraction of data actually being read — not from total stored data, and the arithmetic usually lands two orders of magnitude below the naive number.

## Why it happens

Access is Zipfian: a small head of keys serves most reads. So the question "how big should the cache be?" is really "how big is the head?", and that compounds down through two filters:

```
1B tweets stored, avg 1 KB          → 1 TB total
Daily active set ~5% of tweets      → 50 GB
Top 20% of that serves 80% of reads → 10 GB working set

→ One 16 GB Redis node holds it. Not 1 TB.
```

## What you say in an interview

> "I'd size on the working set, not total data. If 5% of tweets are active daily and the top 20% of those serve 80% of reads, a terabyte of storage needs about ten gigabytes of cache — one Redis node. And I'd push hit rate hard: going 95% to 99% is another 5× off the database, which is almost always cheaper than adding a read replica."

## Hit rate is non-linear in value

| Hit rate | DB load remaining | Meaning |
|---|---|---|
| 80% | 20% | 5× reduction |
| 95% | 5% | 20× reduction |
| 99% | 1% | 100× reduction |

The last few points are the valuable ones. 95% → 99% removes another 5× of database load for a marginal amount of memory — reliably cheaper than a replica, and worth saying out loud.

## The latency caveat

```
avg = 0.95 × 1ms + 0.05 × 30ms = 2.45 ms
```

Good average. But **p99 is the miss path**, ~30 ms — the cache did nothing for it. If the interviewer asks about p99, the cache is not the answer; hedging is. See [tail-latency-fanout](../scaling/tail-latency-fanout.md).

## Probes you should survive

- *"How big should the cache be?"* → Working set, derived out loud. Never "let's say 100 GB."
- *"Will a cache fix our p99?"* → No. It improves the average; p99 remains the miss path.
- *"Why not just cache everything?"* → You pay memory for keys nobody reads, and eviction churn hurts the keys that matter.
- *"Where does the 20/80 come from?"* → Zipfian access, empirically common. State it as an assumption to validate, not a law.

## Related

[cache-aside-race](./cache-aside-race.md) · [hot-keys](./hot-keys.md) · [cache-placement](./cache-placement.md) · [tail-latency-fanout](../scaling/tail-latency-fanout.md)
