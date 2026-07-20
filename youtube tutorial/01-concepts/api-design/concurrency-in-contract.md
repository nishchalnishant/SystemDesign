---
id: concurrency-in-contract
tags: [lld, concurrency, java, cas]
confidence: 3
last-rehearsed: 2026-07-21
source: 3. API Design — "Concurrency belongs in the contract"
---
# Concurrency in the contract

**Claim in one sentence.** Thread safety is behavior, not documentation — and the classic failure is a `get()`-then-`set()` pair that reads consistent and oversells under concurrency.

## The bug and the fix

```java
public class InventoryService {
    private final ConcurrentHashMap<String, AtomicInteger> stock = new ConcurrentHashMap<>();

    /** Thread-safe. Returns false if insufficient stock. */
    public boolean reserve(String sku, int qty) {
        AtomicInteger count = stock.get(sku);
        if (count == null) return false;
        while (true) {
            int current = count.get();
            if (current < qty) return false;
            if (count.compareAndSet(current, current - qty)) return true;
        }
    }
}
```

The CAS loop is the whole point. A `get()`-then-`set()` pair lets two threads both read a stock of 1, both pass the check, and both succeed — overselling. `compareAndSet` fails if the value moved since the read, and the loop retries against the new value.

Note also that `ConcurrentHashMap` alone would **not** have saved this. The map makes each individual operation atomic; it does not make your read-check-write sequence atomic. Compound operations need their own atomicity — that's the distinction interviewers are actually testing.

## What you say in an interview

> "This method is called from multiple threads, so the check-then-decrement has to be atomic — a get followed by a set lets two threads both see stock of one and both succeed. I'd use a CAS loop: read the current value, and only commit if it hasn't changed since. And I'd document the thread-safety guarantee on the method, because a caller can't infer it."

## State the guarantee explicitly

Every shared object should say which it is:

| Guarantee | Meaning |
|---|---|
| **Immutable** | Safe by construction; the best answer when reachable |
| **Thread-safe** | Any thread, any time, no external locking |
| **Conditionally thread-safe** | Individual calls safe; compound sequences need caller locking |
| **Not thread-safe** | Confine to one thread or lock externally |

`ConcurrentHashMap` is the third row, and treating it as the second is exactly the oversell bug above.

## CAS vs lock

CAS wins under low-to-moderate contention: no context switch, no blocking. Under *high* contention the retry loop burns CPU spinning while a lock would park the thread — so a heavily-contested single key can perform worse with CAS. That's the same shape as the [hot-key](../caching/hot-keys.md) problem, and the answer is likewise to reduce contention on the single value rather than to optimize the loop.

## Probes you should survive

- *"Isn't `ConcurrentHashMap` enough?"* → It makes each operation atomic, not your sequence of them. Check-then-act still races.
- *"When does the CAS loop lose to a lock?"* → High contention. Spinning wastes CPU where blocking would yield it.
- *"Could you avoid the loop?"* → `AtomicInteger.updateAndGet` with a lambda, or `getAndUpdate` — same CAS underneath, less code to get wrong.
- *"How would you test this?"* → Concurrent load with N threads reserving against known stock, asserting the total reserved never exceeds it. Single-threaded tests cannot find this bug.

## Related

[interface-first-design](./interface-first-design.md) · [exceptions-as-contract](./exceptions-as-contract.md) · [hot-keys](../caching/hot-keys.md) · [contention-reduction](../scaling/contention-reduction.md)
