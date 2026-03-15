# Distributed Concepts: Idempotency, Retry, Backpressure

> **Essential patterns for robust distributed systems: safe retries, duplicate handling, and flow control.**

---

## 1. Idempotency

### Concept overview

An operation is **idempotent** if performing it once or multiple times has the same effect. In distributed systems, messages and requests can be duplicated (retries, at-least-once delivery), so idempotency prevents double-charging, duplicate orders, or duplicate notifications.

**Why it exists**: Networks and processes fail; retries and at-least-once delivery create duplicates. Idempotent handling makes duplicates safe.

### Core principles

- **HTTP**: GET, PUT, DELETE are idempotent by convention; POST is not (creates new resource each time).
- **Business operations**: “Charge $10” is not idempotent; “Charge $10 for idempotency_key=abc123” is: server checks if `abc123` was already processed and returns same result.
- **Implementation**:
  - **Idempotency key**: Client sends unique key (e.g. UUID) per logical operation; server stores key + result; duplicate request with same key returns stored result without re-executing.
  - **Natural key**: e.g. “Deduct inventory for order_id”; processing order_id twice (e.g. “apply order 123”) is idempotent if you check “already applied.”
  - **Idempotent writes**: “SET key=value” is idempotent; “INCREMENT counter” is not (unless you use “set if not exists” or compare-and-swap).

### Real-world usage

- **Payments**: Stripe, PayPal use idempotency keys so duplicate API calls don’t double-charge.
- **Message consumers**: Process “order_created” by order_id; skip or no-op if order already processed.
- **Notifications**: Deduplicate by (user_id, notification_type, idempotency_key) so user doesn’t get same notification twice.

### Trade-offs

| Approach | Pros | Cons |
|----------|------|------|
| **Idempotency key** | Generic; client-controlled | Storage and TTL for keys; client must generate key |
| **Natural key (e.g. order_id)** | No extra key; simple | Only works when operation has a natural key |
| **Store result** | Duplicate request returns instantly | Need to store and expire results |

### Failure scenarios

- **Key not stored (crash before persist)**: Retry same key; eventually stored. Use DB or durable store for key storage.
- **Key never expires**: Set TTL (e.g. 24 hours) so storage doesn’t grow unbounded.
- **Replay old key**: Optional: bind key to idempotency key + timestamp or version so old replays are rejected.

### Quick revision (idempotency)

- **Definition**: Same effect once or many times.
- **Why**: Retries and at-least-once delivery cause duplicates.
- **How**: Idempotency key + store (key → result); or natural key (e.g. order_id) and “already processed” check.
- **Interview**: “We require clients to send an idempotency key for payment and order creation; we store the key and result so duplicate requests return the same result without re-executing.”

---

## 2. Retry Strategies

### Concept overview

When a call fails (timeout, 5xx, network error), **retrying** can succeed if the failure was transient. Naive retries can overload the failing service (thundering herd) or waste resources; **strategy** (backoff, jitter, limits) makes retries safe and effective.

**Why it exists**: Transient failures are common; retries improve success rate without manual intervention.

### Core principles

- **Exponential backoff**: Wait longer after each attempt (e.g. `wait = base * 2^attempt`). Reduces load on recovering service.
- **Jitter**: Add randomness to wait time (e.g. `wait += random(0, 100ms)`). Prevents many clients retrying at the same time (thundering herd).
- **Max attempts**: Cap retries (e.g. 3–5) so you eventually fail fast and surface error.
- **Retry only on retryable errors**: Retry on 5xx, timeout, connection error; do **not** retry on 4xx (e.g. 400, 404) unless spec says so (e.g. 429 with Retry-After).
- **Idempotency**: Retries imply duplicate requests; backend must be idempotent or you accept duplicate side effects.

### Real-world usage

- **HTTP clients**: Many libraries support retry with backoff and jitter (e.g. exponential backoff + jitter).
- **Message consumers**: At-least-once delivery + retry on failure; process must be idempotent.
- **Circuit breaker**: After many failures, stop retrying for a period (open circuit); then try again (half-open). Complements retry. See [advanced-topics/chaos-engineering.md](chaos-engineering.md).

### Trade-offs

| Aggressive retry | Conservative retry |
|------------------|--------------------|
| Higher success rate | Less load on failing service |
| Risk of thundering herd | Slower recovery for user |
| **Mitigation**: Backoff + jitter + limit | **Mitigation**: Fail fast, then circuit breaker |

### Failure scenarios

- **Service down**: Retries with backoff give it time to recover; circuit breaker stops hammering after repeated failure.
- **Partial success**: Request succeeded on server but response lost; retry may duplicate. Mitigation: idempotent operations.
- **Permanent failure (e.g. 400)**: Do not retry; return error to user.

### Quick revision (retry)

- **Backoff**: Increase delay between retries (e.g. exponential).
- **Jitter**: Randomize delay to avoid thundering herd.
- **Limit**: Max retries then fail.
- **Retry only**: 5xx, timeouts, connection errors; not 4xx (unless 429 + Retry-After).
- **Interview**: “We retry with exponential backoff and jitter on 5xx and timeouts, up to 3 times; we make the operation idempotent so duplicate retries are safe.”

---

## 3. Backpressure

### Concept overview

**Backpressure** is the mechanism by which a slower consumer signals producers to slow down or stop sending. Without it, a fast producer (or many producers) can overwhelm a consumer, causing queue growth, memory exhaustion, or cascading failure.

**Why it exists**: In streaming and queue-based systems, producers can be much faster than consumers; backpressure keeps the system stable and prevents resource exhaustion.

### Core principles

- **Reactive / pull-based**: Consumer pulls when ready (e.g. Kafka consumer fetch); broker doesn’t push unbounded.
- **Flow control**: TCP flow control (receiver window); application-level “stop sending until I ack” or “send me N more.”
- **Queue depth / lag**: Monitor queue size or consumer lag; if above threshold, slow or reject new work (e.g. return 503, or pause producers).
- **Rate limiting**: Limit producer rate (per user or global) so consumers can keep up.

### Real-world usage

- **Kafka**: Consumers pull (fetch); lag grows if consumer is slow; backpressure = slow consumer processes backlog, no push from broker.
- **gRPC / HTTP/2**: Flow control via window updates; receiver can reduce window to slow sender.
- **Reactive streams**: Protocols like Reactive Streams (e.g. Java) have explicit request(n) and backpressure.
- **APIs**: When system is overloaded, return 503 or 429 so clients back off or retry later.

### Trade-offs

| With backpressure | Without backpressure |
|-------------------|----------------------|
| Consumer not overwhelmed | Queue/memory can grow unbounded |
| Producers must handle “slow down” or 503 | Simpler producer; risk of OOM or cascade |
| **Use**: Bounded resources, stability | **Avoid** in production for fast producers + slow consumers |

### Failure scenarios

- **Consumer slow**: Backpressure slows producers; scale consumers or optimize; or drop/sample if acceptable.
- **No backpressure**: Queue grows; memory or disk full; broker or consumer crashes; cascade to producers.
- **Aggressive backpressure**: Rejecting all requests can starve the system; use gradual (e.g. 503 with Retry-After) or priority queues.

### Quick revision (backpressure)

- **Definition**: Slower consumer signals producers to slow down or stop.
- **Why**: Prevents queue growth and resource exhaustion.
- **How**: Pull-based consumption; flow control (TCP, HTTP/2); queue depth/lag monitoring and 503/429 when overloaded.
- **Interview**: “We use Kafka so consumers pull at their own rate; we monitor consumer lag and if it grows too high we scale consumers or temporarily rate-limit producers and return 503 so clients retry later.”

---

## Summary Table

| Concept | Problem | Solution |
|--------|---------|----------|
| **Idempotency** | Duplicate requests (retries, at-least-once) cause duplicate side effects | Idempotency key + store result; or natural key + “already processed” |
| **Retry** | Transient failures; need to succeed without manual retry | Exponential backoff + jitter; max attempts; retry only retryable errors; idempotent backend |
| **Backpressure** | Fast producer overwhelms slow consumer | Pull-based; flow control; queue depth/lag; 503/429 when overloaded |

---

## Quick Revision (all three)

- **Idempotency**: Same effect once or many times; idempotency keys or natural keys; required for safe retries and at-least-once.
- **Retry**: Backoff + jitter + max attempts; retry 5xx/timeout only; idempotent operations.
- **Backpressure**: Consumer signals “slow down”; pull-based consumption; monitor lag; return 503 when overloaded.
- **Interview**: “We design write and payment operations to be idempotent with keys, retry with backoff and jitter on transient failures, and use pull-based consumption and 503 under overload so we don’t overwhelm downstream services.”
