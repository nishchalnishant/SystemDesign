> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** 31 single-claim concept files restructured from folders 3–6, plus a matching rate-limiting stub. Each concept file states one claim, gives a line to say verbatim in an interview, and lists the probes that usually follow.
>
> **Key topics:**
> - [API Design](./api-design/) (8 files) — protocol selection, pagination, idempotency, versioning, status codes, interface design
> - [Caching](./caching/) (8 files) — working-set sizing, cache-aside race, hot keys, placement, stampede protection
> - [Load Balancing](./load-balancing/) (7 files) — algorithm selection, lifecycle, retry budgets, sizing, strategy injection
> - [Scaling](./scaling/) (8 files) — Amdahl/USL, Little's Law, utilization knee, tail latency, load shedding, timeouts
> - [Rate Limiting](./rate-limiting/) — stub, no original content (source folder had none to atomize)
>
> **Key takeaway:** This is the rehearsal layer and the better entry point over the long-form folders 1–7. Start at [INDEX.md](./INDEX.md) for the mistakes tables and HLD→LLD mappings — the fastest pre-interview skim.

---

# Concepts

31 concept files across four topics, restructured from the long-form folders 3–6 in this directory. Each file is one claim, an interview line to say verbatim, and the probes that follow it.

## Contents

| Folder | Files | Description |
|--------|-------|-------------|
| [INDEX.md](./INDEX.md) | — | Full skim table: every concept's claim, plus per-topic mistakes tables, HLD→LLD mappings, and cross-cutting threads |
| [api-design/](./api-design/) | 8 | Protocols, pagination, idempotency, versioning, status codes, interface/exception design, concurrency |
| [caching/](./caching/) | 8 | Sizing, cache-aside race, hot keys, placement, stampede protection, negative caching, TTL expiry |
| [load-balancing/](./load-balancing/) | 7 | Algorithm selection, lifecycle (draining/slow start/outlier detection), retry budgets, sizing, strategy injection |
| [scaling/](./scaling/) | 8 | Amdahl/USL, Little's Law, utilization knee, tail latency fan-out, load shedding, bounded queues, timeouts |
| [rate-limiting/](./rate-limiting/) | 0 | Stub — see [INDEX.md § Not yet covered](./INDEX.md#not-yet-covered) |
