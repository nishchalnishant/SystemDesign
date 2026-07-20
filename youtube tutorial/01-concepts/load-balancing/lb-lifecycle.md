---
id: lb-lifecycle
tags: [load-balancing, deployment, health-checks]
confidence: 3
last-rehearsed: 2026-07-21
source: 5. Load Balancing — "The lifecycle: what happens when servers come and go"
---
# LB lifecycle: draining, slow start, outlier detection

**Claim in one sentence.** A load balancer's hard job is transitions, not steady state — backends arriving cold, leaving with work in flight, or degrading without failing — and each transition has a distinct mechanism.

## Connection draining (leaving)

Killing a backend drops its in-flight requests. Draining:

1. Mark it **out of rotation** — no new requests routed
2. Let existing requests finish, up to a **drain timeout** (typically 30–300 s)
3. Terminate, force-closing whatever remains

Set the drain timeout **above your p99 request duration**. Below it, every deploy cuts off real requests — a self-inflicted error rate that shows up as a deploy-correlated spike.

Long-lived connections (WebSocket, SSE, gRPC streams) never finish on their own, so draining alone hangs until timeout. The server must actively send a close frame or `GOAWAY` and let clients reconnect elsewhere.

## Slow start (arriving)

A new backend has an empty cache, cold JIT, and an unwarmed connection pool. Under least-connections it looks *maximally idle* — zero connections — so the LB floods it. It responds slowly, or falls over, and gets ejected. **This is the classic failure right after autoscaling**: you added capacity and the new capacity died.

Slow start ramps the new backend's weight 0 → 100% over 30–60 s. Explicit in NGINX (`slow_start=30s`) and Envoy.

The same effect hits a **whole-fleet restart** — everything cold simultaneously, no warm capacity to absorb the load. That's why staggered restarts exist.

## Outlier detection (degrading)

Active health checks (`GET /health`) catch a *dead* backend. They miss one that's alive but degraded, because `/health` often returns 200 while real requests time out — the health endpoint doesn't touch the broken dependency.

Outlier detection watches real traffic: eject after N consecutive 5xx (Envoy default 5), keep it out for a base period, lengthen on repeat offense. Two safety rails:

- **Max ejection percentage** (e.g. 50%) — without this cap, a bad deploy that makes *every* backend return 5xx causes the LB to eject the entire fleet.
- **Success-rate mode** — eject on deviation from the fleet median, so a fleet-wide failure ejects nobody.

**Active + passive together** is the correct answer: active for dead, passive for degraded.

## What you say in an interview

> "Three transitions to handle. Leaving: drain with a timeout above p99, and explicitly close long-lived connections since they'll never finish on their own. Arriving: slow start, because a cold backend looks maximally idle to least-connections and gets flooded right after autoscaling. Degrading: outlier detection on real response codes, since a health endpoint can return 200 while actual requests time out — with a max-ejection cap so a fleet-wide bad deploy doesn't eject everything."

## Probes you should survive

- *"Why isn't `/health` returning 200 enough?"* → It usually doesn't exercise the dependency that's broken. Passive observation of real traffic does.
- *"What if the drain timeout is too long?"* → Deploys crawl and you hold capacity you wanted back. It's a floor set by p99, not a number to maximize.
- *"Why does the max-ejection cap matter?"* → Without it, a universal failure mode ejects 100% of backends and the LB has nowhere to route. The cap keeps a degraded service alive instead of dead.
- *"How does slow start interact with P2C?"* → It's weighting, applied on top of selection — the new backend is chosen proportionally less often until warm.

## Related

[lb-algorithm-selection](./lb-algorithm-selection.md) · [retry-budgets](./retry-budgets.md) · [backend-registry-cow](./backend-registry-cow.md) · [load-shedding-ladder](../scaling/load-shedding-ladder.md)
