---
id: strategy-injection
tags: [lld, load-balancing, design-patterns, java]
confidence: 3
last-rehearsed: 2026-07-21
source: 5. Load Balancing — "Strategy for the algorithm" + "The empty-backend case"
---
# Strategy injection

**Claim in one sentence.** The balancing algorithm is a strategy taking only *healthy* backends — the filtering belongs to the balancer, so no strategy implementation can forget to check health.

## The interface

```java
public interface LoadBalancingStrategy {
    Backend select(List<Backend> healthy);
}
```

The parameter name is the design decision. Passing all backends and letting each strategy filter means every new strategy is a fresh opportunity to omit the check. Passing `healthy` makes the invariant structural — it holds for implementations that haven't been written yet.

Same principle as an injected eviction policy in a cache: the algorithm varies, the contract around it doesn't.

## P2C implementation

```java
public class P2CStrategy implements LoadBalancingStrategy {
    private final Random random = new Random();

    @Override
    public Backend select(List<Backend> healthy) {
        if (healthy.isEmpty()) throw new NoHealthyBackendException();
        if (healthy.size() == 1) return healthy.get(0);

        int i = random.nextInt(healthy.size());
        int j = random.nextInt(healthy.size() - 1);
        if (j >= i) j++;                       // distinct index without a retry loop

        Backend a = healthy.get(i), b = healthy.get(j);
        return a.activeConnections() <= b.activeConnections() ? a : b;
    }
}
```

The `j >= i` adjustment is worth pointing out: it produces a distinct second index in bounded work, where `while (i == j) j = random(...)` has no worst-case bound. Small, and the kind of thing interviewers notice.

## The empty case is part of the contract

Returning `null` when nothing is healthy forces every caller to null-check, and someone will forget — producing an NPE far from the cause. Throw a domain exception or return `Optional<Backend>`; either makes the case impossible to ignore.

This is [exceptions-as-contract](../api-design/exceptions-as-contract.md) applied: "no healthy backend" is a genuine failure, not a legitimate absence, so an exception fits better than `Optional` here.

## What you say in an interview

> "The algorithm goes behind a strategy interface, and I'd pass it only the healthy backends — that way filtering is the balancer's responsibility and no strategy can forget it. For the empty case I'd throw a domain exception rather than return null, since a null return means every caller has to remember to check."

## Probes you should survive

- *"Why not let the strategy filter?"* → Then correctness depends on every implementation remembering. Putting it in the caller makes it hold for strategies not yet written.
- *"Why not `Optional<Backend>`?"* → Defensible. But no healthy backend is an error condition rather than expected absence, and an exception carries that meaning.
- *"Is `Random` OK here?"* → Under contention `ThreadLocalRandom` is better — `Random`'s CAS on a shared seed becomes a contention point on a hot path.
- *"How would you add a new algorithm?"* → New class implementing the interface, injected at construction. No change to the balancer — the Open/Closed point.

## Related

[lb-algorithm-selection](./lb-algorithm-selection.md) · [backend-registry-cow](./backend-registry-cow.md) · [interface-first-design](../api-design/interface-first-design.md)
