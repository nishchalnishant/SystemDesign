---
id: retry-budgets
tags: [load-balancing, reliability, resilience]
confidence: 3
last-rehearsed: 2026-07-21
source: 5. Load Balancing — "Retries and their danger"
---
# Retry budgets

**Claim in one sentence.** Retries multiply offered load exactly when capacity has dropped, so an unbudgeted retry policy converts a partial outage into a total one.

## Why it happens

Every backend is struggling. Each client retries 3×. Offered load triples at the precise moment capacity fell — a **retry storm**. The system had a chance to recover at reduced throughput; retries removed it.

The perverse property is that each client is behaving reasonably in isolation. Retrying a failed request is locally correct and globally catastrophic, which is why the control has to be a *system-level budget* rather than a per-request setting.

## The controls

- **Retry budget** — cap retries at ~10–20% of *total requests*, not per-request. Under partial failure you retry; under widespread failure the budget is exhausted and retries stop automatically. Envoy and gRPC both implement this.
- **Only retry idempotent requests**, or ones carrying an [idempotency key](../api-design/idempotency-keys.md).
- **Never retry the same backend** — retry elsewhere, or you're hammering a host you already know is sick.
- **Jittered exponential backoff** — synchronized retries produce a thundering herd, so the jitter is load-bearing, not a refinement.
- **Circuit breaker** as the outer guard — stop calling a dependency that's failing, instead of retrying against it.

## What you say in an interview

> "Retries need a budget, not just a count — cap total retries at ten or twenty percent of request volume so that under widespread failure they shut off automatically. Per-request limits don't do that; three retries each is a 3× load multiplier exactly when you have less capacity. Plus jittered backoff, retry a different backend, and only retry things that are safe to repeat."

## Budget vs per-request count

| | Per-request (`retries: 3`) | Budget (10% of traffic) |
|---|---|---|
| Isolated failure | Retries — good | Retries — good |
| Widespread failure | **3× load multiplier** | Budget exhausts, retries stop |
| Tuning | Per call site | One fleet-wide number |

The budget is adaptive by construction: when failures are rare, retries are cheap and always available; when failures are common — the dangerous case — the budget is already spent.

## Probes you should survive

- *"Why not just lower the retry count?"* → Any nonzero per-request count is still a multiplier under widespread failure. The budget makes the *aggregate* the thing you bound.
- *"Why jitter and not just backoff?"* → Backoff alone keeps failures synchronized — everyone waits 1s, then all retry at once. Jitter spreads the arrival.
- *"How does this differ from a circuit breaker?"* → The budget limits retry volume; the breaker stops calling the dependency at all. Complementary — the breaker is the outer guard.
- *"What's safe to retry?"* → GET/PUT/DELETE by HTTP semantics, plus anything with an idempotency key. A bare POST is not.

## Related

[lb-lifecycle](./lb-lifecycle.md) · [idempotency-keys](../api-design/idempotency-keys.md) · [status-codes](../api-design/status-codes.md) · [load-shedding-ladder](../scaling/load-shedding-ladder.md) · [timeout-budgets](../scaling/timeout-budgets.md)
