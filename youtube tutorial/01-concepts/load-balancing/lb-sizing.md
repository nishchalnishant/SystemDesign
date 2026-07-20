---
id: lb-sizing
tags: [load-balancing, capacity-planning, networking]
confidence: 3
last-rehearsed: 2026-07-21
source: 5. Load Balancing — "Sizing"
---
# LB sizing

**Claim in one sentence.** Load balancers are usually bound by **concurrent connections**, not requests per second — so for long-lived connections you size on connections, and the wall you hit first is often ephemeral port exhaustion rather than CPU.

## The two limits

- **Throughput** — modern software LBs handle 10k–100k req/s per instance; hardware and kernel-bypass go higher.
- **Concurrent connections** — usually the real constraint. Each connection costs a file descriptor and socket buffers. **100k idle WebSocket connections cost far more memory than 10k req/s of short HTTP**, even though the second number looks busier.

That asymmetry is the whole point. A workload that's quiet in requests/sec can be at capacity in connections, and a dashboard showing low CPU tells you nothing about it.

## Ephemeral port exhaustion

The classic wall: **~28k connections per source IP / destination pair on Linux**. Each outbound connection to the same destination consumes an ephemeral port, and the default range is finite — Linux `net.ipv4.ip_local_port_range` is `32768 60999`, so 28,232 usable. An LB opening many connections to a single backend address hits this regardless of how much CPU or memory is free.

The number is platform-specific, not a law: macOS defaults to 49152–65535 (16,384), and the Linux range is tunable. Quote 28k as "the Linux default" rather than a universal constant — and know that it's raisable, because that's the obvious follow-up.

Workarounds: multiple source IPs, `SO_REUSEPORT`, or connection pooling that reuses rather than reopens.

Note this is a *different* sizing method from thread-pool sizing, which derives from [Little's Law](../scaling/littles-law.md). Here the binding constraint is resource exhaustion — descriptors, buffers, ports — not throughput × latency. Knowing which model applies to which resource is the actual skill.

## What you say in an interview

> "For this workload I'd size on connections rather than requests per second, since these are long-lived. Each one holds a file descriptor and socket buffers, so a hundred thousand idle connections is a much bigger memory commitment than a high request rate on short-lived ones. And the specific wall to watch is ephemeral ports — about 28k per source-IP-to-destination pair — which you get past with additional source IPs or `SO_REUSEPORT`."

## Direct Server Return

DSR removes the LB from the response path: requests flow through it, responses go straight from backend to client. Where responses dwarf requests — video, large downloads — this multiplies effective capacity, because the LB was only ever bottlenecked on bytes it didn't need to see.

## Probes you should survive

- *"Why not size with Little's Law here?"* → That sizes concurrency from throughput and latency. The LB binds on exhaustible resources — descriptors, ports, buffers — which is a ceiling, not a rate.
- *"Where does 28k come from?"* → Linux's default `ip_local_port_range` (32768–60999). It's per source IP *and* destination pair, so adding source IPs multiplies it — and the range itself is tunable via sysctl.
- *"So just raise the range?"* → It buys roughly 2× at most, since you can't reclaim the low 32k of reserved and well-known ports. Real headroom comes from more source IPs, more backend addresses, or connection reuse — raising the range alone doesn't change the shape of the problem.
- *"How do you know which limit you're near?"* → Track open file descriptors and connection count as first-class metrics. CPU utilization won't show it.
- *"When is DSR not usable?"* → When you need L7 features on the response — header rewriting, response caching, compression. DSR means the LB never sees it.

## Related

[lb-request-mutation](./lb-request-mutation.md) · [littles-law](../scaling/littles-law.md) · [bounded-queues](../scaling/bounded-queues.md) · [lb-lifecycle](./lb-lifecycle.md)
