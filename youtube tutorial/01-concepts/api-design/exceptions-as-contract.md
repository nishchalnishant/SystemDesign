---
id: exceptions-as-contract
tags: [lld, error-handling, java]
confidence: 3
last-rehearsed: 2026-07-20
source: 3. API Design — "Exceptions are part of the contract"
---
# Exceptions as contract

**Claim in one sentence.** What a method throws is as much API as what it returns — and collapsing a failure into a `null` return destroys the caller's ability to distinguish "absent" from "broken."

## The distinction that matters

```java
Optional<User> findById(String id);              // may legitimately not exist
User getById(String id) throws UserNotFound;     // absence is an error here
```

Same lookup, two different contracts, and the difference is whether absence is *expected*. `find` says the caller should handle emptiness as a normal branch. `get` says the caller has already established this user exists, so absence means something is wrong upstream.

Returning `null` from either erases that distinction and forces every caller to guess.

## The rules

- **Throw domain exceptions** — `InsufficientBalanceException`, not `RuntimeException`. The type is information; a caller can catch and handle `InsufficientBalance` specifically, and cannot do anything useful with `RuntimeException`.
- **Never swallow into a null return** — the caller cannot tell absence from failure, so they cannot respond correctly to either.
- **`Optional<T>` for legitimate absence, exceptions for genuine failure.**
- **Never `throws Exception`** in a signature — it tells the caller nothing and forces them to catch everything, including bugs they should let propagate.

## What you say in an interview

> "The exceptions are part of the signature. I'd use `Optional` where absence is a normal outcome and a domain exception where it's genuinely an error — `InsufficientBalanceException`, not a generic runtime exception, because the caller can actually branch on the specific type. Returning null for a failure is the thing to avoid; it collapses 'not there' and 'something broke' into one value."

## Where the two levels meet

An HLD status code and an LLD exception type are the same decision at different layers:

```
409 Conflict            ⟷  DuplicateOrderException
402 Payment Required    ⟷  InsufficientBalanceException
404 Not Found           ⟷  Optional.empty() / UserNotFound
```

When an interviewer moves from HLD to LLD on the same problem, showing that the error contract lines up across the boundary is the point. See [status-codes](./status-codes.md).

## Probes you should survive

- *"Checked or unchecked?"* → Checked when the caller can plausibly recover (insufficient balance — show the user a message). Unchecked for programming errors and unrecoverable state. Java's checked exceptions are widely disliked because that line is drawn wrong so often.
- *"Isn't `Optional` just null with extra syntax?"* → It's null the type system knows about. The compiler forces the absence branch; null doesn't.
- *"Where do you catch?"* → At the layer that can decide something. Catching in a repository to log-and-rethrow adds noise; catching at the service boundary where you can map to a status code is useful.
- *"Exception for control flow?"* → No — they're expensive (stack capture) and they hide the normal path. If it happens on a meaningful fraction of calls, it's a return value.

## Related

[interface-first-design](./interface-first-design.md) · [status-codes](./status-codes.md) · [concurrency-in-contract](./concurrency-in-contract.md)
