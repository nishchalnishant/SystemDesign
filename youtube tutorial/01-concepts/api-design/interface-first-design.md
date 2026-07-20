---
id: interface-first-design
tags: [lld, oop, solid, java]
confidence: 3
last-rehearsed: 2026-07-20
source: 3. API Design — "Start with the interface" + "Design rules that carry weight"
---
# Interface-first design

**Claim in one sentence.** In LLD the skill being tested is what you *hide*, not what you expose — and every leak (a mutable collection, a generic setter, a concrete dependency) hands a caller the ability to break an invariant you are responsible for.

## Write the interface first

```java
public interface RateLimiter {
    boolean tryAcquire(String clientId);
}
```

That is the entire public API of a rate limiter. Token bucket or sliding window is an implementation detail the caller must not see. The moment the interface leaks `getTokenCount()` or `getWindowStart()`, the algorithm is part of the contract and you cannot change it without breaking callers.

Writing the interface before the class forces the contract to be decided before the data structure — which is the correct order, because the data structure should follow from the contract.

## Three leaks and their fixes

**Returning internal state.**
```java
// Caller can mutate your list
public List<Item> getItems() { return this.items; }

// Safe
public List<Item> getItems() { return Collections.unmodifiableList(items); }
```
Handing back a mutable internal collection means any caller can corrupt your object's invariants from outside — and the corruption surfaces far from its cause.

**Generic setters.**
```java
// Nothing stops NEW → COMPLETED
order.setStatus(Status.COMPLETED);

// Transitions validated; invalid ones impossible
order.markPaid();      // throws if not in NEW
order.markShipped();   // throws if not in PAID
```
A setter that accepts any enum value pushes the state machine onto every caller. Intention-revealing methods keep it inside the object that owns it — one place to be correct instead of N.

**Half-constructed objects.** If an object can't be used without a dependency, take it in the constructor, not a setter.
```java
public OrderService(PaymentGateway gateway, OrderRepository repo) {
    this.gateway = Objects.requireNonNull(gateway);
    this.repo = Objects.requireNonNull(repo);
}
```
An object that compiles but throws on first use is a design defect. Constructor injection also makes the class testable — you can pass a fake gateway, which is exactly what interviewers probe for.

## What you say in an interview

> "I'd start with the interface — `tryAcquire(clientId)` returning a boolean is the whole contract, and whether it's token bucket or sliding window stays hidden so I can change it later. Dependencies come in through the constructor as interfaces, both so the object can't exist in a half-built state and so I can pass a fake in tests."

## Depend on abstractions, keep interfaces narrow

`OrderService` takes `PaymentGateway`, not `StripeGateway` — the D in SOLID, and what lets you add PayPal without editing `OrderService`.

A `Machine` interface with `print()`, `scan()`, and `fax()` forces a basic printer to implement `fax()` by throwing. Split it — clients shouldn't depend on methods they don't call, the I in SOLID.

## Probes you should survive

- *"Why is a getter that returns the list bad if nobody mutates it?"* → Because "nobody mutates it" is not enforceable. The type permits it, so eventually someone will, and the bug appears nowhere near this class.
- *"Isn't `markPaid()` just a setter with extra steps?"* → No — it encodes *which* transitions are legal. `setStatus` accepts all of them.
- *"Why not field injection?"* → The object exists in an invalid state between construction and injection, and you can't construct one in a test without the framework.
- *"When is a defensive copy better than `unmodifiableList`?"* → When the caller needs a mutable copy, or when the underlying list keeps changing and you don't want them observing it. `unmodifiableList` is a live view, not a snapshot.

## Related

[exceptions-as-contract](./exceptions-as-contract.md) · [concurrency-in-contract](./concurrency-in-contract.md) · [strategy-injection](../load-balancing/strategy-injection.md)
