---
id: cache-interface-design
tags: [lld, interfaces, strategy-pattern, java]
confidence: 3
last-rehearsed: 2026-07-21
source: 4. Caching — "The interface hides the policy"
---
# The cache interface hides the policy

**Claim in one sentence.** `get`/`put`/`invalidate` is the entire cache contract — the moment eviction policy leaks into the interface, you have frozen it.

## The contract

```java
public interface Cache<K, V> {
    Optional<V> get(K key);
    void put(K key, V value);
    void invalidate(K key);
}
```

That's all of it. LRU vs LFU vs TTL is an implementation detail the caller must not be able to observe. If `getEvictionOrder()` or `getAccessCount()` appears on the interface, the policy is now part of the published contract and swapping it becomes a breaking change rather than a constructor argument.

Note `Optional<V>` rather than a nullable `V`: a cache miss is an expected outcome, not an error, and the type should say so. That's the same reasoning as [exceptions-as-contract](../api-design/exceptions-as-contract.md) — `Optional` for expected absence, exceptions for genuine violations.

## Policy as an injected strategy

```java
public class CacheImpl<K, V> implements Cache<K, V> {
    private final EvictionPolicy<K> policy;
    private final int capacity;

    public CacheImpl(int capacity, EvictionPolicy<K> policy) {
        this.capacity = capacity;
        this.policy = Objects.requireNonNull(policy);
    }
}
```

LRU → LFU is now a constructor argument, not a rewrite. `Objects.requireNonNull` in the constructor is the invariant: there is no such thing as a half-built cache with no policy.

This is structurally the same move as [strategy-injection](../load-balancing/strategy-injection.md) in the load balancer — the varying behavior becomes a collaborator, and the class that holds it never learns which one it got.

## What you say in an interview

> "The cache interface is `get`, `put`, `invalidate` — three methods. Eviction policy stays out of it, because the moment I expose `getAccessCount` I've frozen LFU into the contract and can't swap it. I'd inject the policy as a strategy in the constructor, so LRU to LFU is a constructor argument rather than a rewrite. And `get` returns `Optional` — a miss is an expected outcome, not an exception."

## Where the boundary actually sits

The interface hides *policy*, not *behavior the caller must reason about*. Thread-safety is the line: whether the cache is safe for concurrent access cannot be an implementation detail, because the caller's correctness depends on it. Document it on the interface — the same argument as [concurrency-in-contract](../api-design/concurrency-in-contract.md).

## Probes you should survive

- *"Why not expose statistics?"* → Put them behind a separate `CacheStats` accessor if you need them for monitoring. Don't put policy-revealing methods on the primary contract that callers code against.
- *"Where does TTL live?"* → It's a policy too. Either an eviction strategy or a per-entry expiry checked on read — see [ttl-expiry-strategies](./ttl-expiry-strategies.md). Either way the caller only sees an empty `Optional`.
- *"How does the policy learn about access?"* → `CacheImpl` notifies it — `policy.recordAccess(key)` on a hit, `policy.evictCandidate()` when at capacity. The policy owns ordering; the cache owns storage.
- *"Isn't this over-engineering for one policy?"* → The interface costs nothing; the strategy is worth it when you expect the policy to change or need to test eviction independently of storage. For a fixed single policy, a plain implementation is defensible — say so rather than pattern-matching reflexively.

## Related

[ttl-expiry-strategies](./ttl-expiry-strategies.md) · [single-flight-loading](./single-flight-loading.md) · [strategy-injection](../load-balancing/strategy-injection.md) · [interface-first-design](../api-design/interface-first-design.md) · [exceptions-as-contract](../api-design/exceptions-as-contract.md)
