# Rate Limiting

> **Topic from**: [Design a Distributed Rate Limiter w/ a Ex-Meta Staff Engineer](https://www.youtube.com/watch?v=MIJFyUPG4Z4) — Hello Interview

**This topic is already covered in depth elsewhere in the repo. No notes were duplicated here.**

The video covers distributed rate limiting, which `05-hld-problems/01-easy/rate-limiter.md` already treats as a full problem walkthrough — including the multi-server counter problem, Redis failure modes, multi-region splitting, and hot keys.

---

## Where to find it

| What you need | File |
|---|---|
| **Full HLD problem walkthrough** — requirements, API, algorithms deep dive, distributed counters, fail-open vs fail-closed, multi-region, hot keys, hierarchical rules, interviewer questions by level | [05-hld-problems/01-easy/rate-limiter.md](../../05-hld-problems/01-easy/rate-limiter.md) |
| **The 5 algorithms** — token bucket, leaky bucket, fixed window, sliding window log, GCRA — plus placement and the atomicity trap | [02-building-blocks/02-performance/02-rate-limiting.md](../../02-building-blocks/02-performance/02-rate-limiting.md) |
| **LLD implementation** — class design, thread safety, concurrency test harness | [06-lld/05-problems/](../../06-lld/05-problems/) — see the rate limiter problem |

**Related in this directory:**
- [Scalability](../6.%20Scalability/01-scalability-hld-and-lld.md) — rate limiting is admission control, the first rung of the shed → degrade → queue → collapse ladder
- [API Design](../3.%20API%20Design/01-api-design-hld-and-lld.md) — the 429 and `Retry-After` response contract
- [Load Balancing](../5.%20Load%20Balancing/01-load-balancing-hld-and-lld.md) — where the limiter sits relative to the LB and gateway

---

## The distributed problem in one paragraph

With N app servers each holding an in-process counter, a user gets N× their limit by spreading requests across servers. The fix is a shared authoritative counter (Redis), which introduces a round-trip on every request (~0.3 ms same-DC) and a new failure mode: what happens when Redis is down. Fail open protects revenue and allows abuse; fail closed protects the system and blocks paying customers; local fallback degrades to per-server counting, allowing up to N× the limit but avoiding a total outage. Most APIs choose fail open with alerting; payment and auth endpoints choose fail closed.

The full treatment, including multi-region and hot-key handling, is in the HLD file above.

---

## Note on this folder

Folders 3–6 in this directory contain notes because those videos covered material the repo had genuine gaps in. This one did not — the existing coverage is more thorough than a new summary would be, and duplicating it would create two copies to keep in sync.

One real gap was found and filled in place: **GCRA** was added as a fifth algorithm to `02-rate-limiting.md`, along with the check-and-increment atomicity trap.
