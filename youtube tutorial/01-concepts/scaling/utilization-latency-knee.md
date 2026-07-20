---
id: utilization-latency-knee
tags: [scalability, queueing-theory, autoscaling]
confidence: 3
last-rehearsed: 2026-07-21
source: 6. Scalability — "Utilization and the latency knee"
---
# The utilization knee

**Claim in one sentence.** Response time scales as `1/(1-ρ)`, so running servers at 90% CPU is not efficient — it is fragile — and headroom is the price of predictable tail latency, not waste.

## The curve

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

The non-linearity is the whole point. Going 80% → 90% doubles latency; 90% → 95% doubles it again. The same ten percentage points cost very differently depending on where you start.

Target **50–70%** for latency-sensitive services. Batch work with no latency requirement can and should run hot.

## Why it happens

Queueing. At low utilization an arriving request usually finds the server free. As utilization rises, the probability of arriving during a busy period rises — and because arrivals are bursty rather than evenly spaced, queues form well before utilization reaches 100%. A perfectly smooth arrival stream wouldn't do this, and real traffic is never smooth.

## What you say in an interview

> "I'd target fifty to seventy percent utilization, not ninety. Latency goes as one over one minus utilization, so eighty to ninety percent doubles latency and ninety to ninety-five doubles it again — the headroom is buying predictable tail latency, not sitting idle. It also means autoscaling on CPU is usually too late: by the time CPU crosses the threshold, latency has already degraded and the queue has built. I'd scale on queue depth or concurrency instead."

## Why CPU-based autoscaling is late

CPU is a *lagging* indicator. The sequence is: queue builds → latency rises → CPU climbs → threshold crosses → scale-out starts → instances boot (minutes). Users have been experiencing degraded latency for the entire chain.

Leading indicators — queue depth, in-flight concurrency ([Little's Law](./littles-law.md)), p99 latency — turn earlier. Scaling on those buys the minutes that a boot sequence costs.

## Probes you should survive

- *"Isn't 50% utilization wasteful?"* → Compare it against the cost of a 10× latency multiplier and the outage risk at 90%. For a latency-sensitive service the headroom is cheaper than the incident.
- *"When is running hot fine?"* → Batch, async, and queue-consuming workloads where latency doesn't matter. Reserve the headroom for the request path.
- *"Why is the curve non-linear?"* → Queueing under bursty arrivals. Near saturation, small increases in load produce large increases in waiting time.
- *"What should you autoscale on?"* → Queue depth or concurrency. CPU lags the thing users actually feel.

## Related

[littles-law](./littles-law.md) · [amdahl-and-usl](./amdahl-and-usl.md) · [tail-latency-fanout](./tail-latency-fanout.md) · [load-shedding-ladder](./load-shedding-ladder.md)
