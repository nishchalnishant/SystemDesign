# Distributed Concepts: Idempotency, Retry, Backpressure

> **Essential patterns for robust distributed systems: safe retries, duplicate handling, and flow control.**

---

## Table of Contents

1. [Idempotency](#1-idempotency)
2. [Retry Strategies](#2-retry-strategies)
3. [Backpressure](#3-backpressure)
4. [Distributed Failure Modes](#4-distributed-failure-modes)

---

## 1. Idempotency

> **Analogy**: A doorbell. Press it once — ding. Press it five times fast — still just one ding. The outcome is the same regardless of how many times you trigger it. In payments, clicking "Pay" three times should not charge three times. The operation has already completed; repeated triggers should be safe no-ops.

### Concept Overview

An operation is **idempotent** if performing it once or multiple times has the same effect. In distributed systems, messages and requests can be duplicated (retries, at-least-once delivery), so idempotency prevents double-charging, duplicate orders, or duplicate notifications.

**Why it exists**: Networks and processes fail; retries and at-least-once delivery create duplicates. Idempotent handling makes duplicates safe.

### Core Principles

- **HTTP**: GET, PUT, DELETE are idempotent by convention; POST is not (creates new resource each time).
- **Business operations**: "Charge $10" is not idempotent; "Charge $10 for idempotency_key=abc123" is: server checks if `abc123` was already processed and returns the same result.
- **Implementation**:
  - **Idempotency key**: Client sends unique key (e.g. UUID) per logical operation; server stores key + result; duplicate request with same key returns stored result without re-executing.
  - **Natural key**: e.g. "Deduct inventory for order_id"; processing order_id twice is idempotent if you check "already applied."
  - **Idempotent writes**: "SET key=value" is idempotent; "INCREMENT counter" is not (unless you use "set if not exists" or compare-and-swap).

### Java Implementation

```java
@Service
public class PaymentService {

    private final PaymentRepository paymentRepo;
    private final IdempotencyKeyStore keyStore; // Redis or DB-backed

    public PaymentResult charge(String idempotencyKey, String userId, BigDecimal amount) {
        // Check if this key was already processed
        Optional<PaymentResult> existing = keyStore.get(idempotencyKey);
        if (existing.isPresent()) {
            return existing.get(); // Return cached result — no re-execution
        }

        // Process the payment
        PaymentResult result = processCharge(userId, amount);

        // Store result with TTL (e.g. 24 hours)
        keyStore.store(idempotencyKey, result, Duration.ofHours(24));
        return result;
    }
}
```

```java
// Natural key approach — idempotent by order_id
@Transactional
public void applyOrder(String orderId) {
    if (orderRepo.existsById(orderId)) {
        return; // Already processed — safe no-op
    }
    orderRepo.save(new Order(orderId, ...));
    inventoryService.deduct(orderId);
}
```

### Real-World Usage

- **Payments**: Stripe, PayPal use idempotency keys so duplicate API calls don't double-charge.
- **Message consumers**: Process "order_created" by order_id; skip or no-op if order already processed.
- **Notifications**: Deduplicate by (user_id, notification_type, idempotency_key) so user doesn't get same notification twice.

### Trade-offs

| Approach | Pros | Cons |
|----------|------|------|
| **Idempotency key** | Generic; client-controlled | Storage and TTL for keys; client must generate key |
| **Natural key (e.g. order_id)** | No extra key; simple | Only works when operation has a natural key |
| **Store result** | Duplicate request returns instantly | Need to store and expire results |

### Failure Scenarios

- **Key not stored (crash before persist)**: Retry same key; eventually stored. Use DB or durable store for key storage.
- **Key never expires**: Set TTL (e.g. 24 hours) so storage doesn't grow unbounded.
- **Replay old key**: Optional: bind key to idempotency key + timestamp or version so old replays are rejected.

### Quick Revision (Idempotency)

- **Definition**: Same effect once or many times.
- **Why**: Retries and at-least-once delivery cause duplicates.
- **How**: Idempotency key + store (key → result); or natural key (e.g. order_id) and "already processed" check.
- **Interview**: "We require clients to send an idempotency key for payment and order creation; we store the key and result so duplicate requests return the same result without re-executing."

---

## 2. Retry Strategies

> **Analogy**: Knocking on a door. If no answer, knock again in 30 seconds (fixed retry). If still no answer, wait 1 minute, then 2 minutes, then 4 minutes — each wait doubles (exponential backoff). Jitter is adding a random variation: instead of knocking at exactly 4 minutes, knock somewhere between 3:45 and 4:15. This is crucial when 1,000 people are all knocking on the same door after it briefly went unanswered — without jitter, all 1,000 knock at the exact same moment and overwhelm the person inside.

### Concept Overview

When a call fails (timeout, 5xx, network error), **retrying** can succeed if the failure was transient. Naive retries can overload the failing service (thundering herd) or waste resources; **strategy** (backoff, jitter, limits) makes retries safe and effective.

**Why it exists**: Transient failures are common; retries improve success rate without manual intervention.

### Core Principles

- **Exponential backoff**: Wait longer after each attempt (e.g. `wait = base * 2^attempt`). Reduces load on recovering service.
- **Jitter**: Add randomness to wait time (e.g. `wait += random(0, 100ms)`). Prevents many clients retrying at the same time (thundering herd).
- **Max attempts**: Cap retries (e.g. 3–5) so you eventually fail fast and surface error.
- **Retry only on retryable errors**: Retry on 5xx, timeout, connection error; do **not** retry on 4xx (e.g. 400, 404) unless spec says so (e.g. 429 with Retry-After).
- **Idempotency**: Retries imply duplicate requests; backend must be idempotent or you accept duplicate side effects.

### Java Implementation

```java
public class RetryUtil {

    private static final int MAX_ATTEMPTS = 4;
    private static final long BASE_DELAY_MS = 100;

    public static <T> T withRetry(Callable<T> operation) throws Exception {
        int attempt = 0;
        while (true) {
            try {
                return operation.call();
            } catch (RetryableException e) {
                attempt++;
                if (attempt >= MAX_ATTEMPTS) throw e;

                // Exponential backoff with jitter
                long backoff = BASE_DELAY_MS * (1L << attempt); // 200, 400, 800 ms
                long jitter = ThreadLocalRandom.current().nextLong(0, backoff / 2);
                long waitMs = backoff + jitter;

                Thread.sleep(waitMs);
            } catch (NonRetryableException e) {
                throw e; // 4xx — do not retry
            }
        }
    }
}

// Usage
PaymentResult result = RetryUtil.withRetry(() -> paymentService.charge(userId, amount));
```

**Retry decision by HTTP status:**
```java
private static boolean isRetryable(int statusCode) {
    return statusCode == 429          // Too Many Requests — retry after delay
        || statusCode == 503          // Service Unavailable
        || statusCode >= 500;         // Any 5xx server error
    // 400, 401, 403, 404 → NOT retryable
}
```

### Real-World Usage

- **HTTP clients**: Many libraries support retry with backoff and jitter (e.g. exponential backoff + jitter).
- **Message consumers**: At-least-once delivery + retry on failure; process must be idempotent.
- **Circuit breaker**: After many failures, stop retrying for a period (open circuit); then try again (half-open). Complements retry.

### Trade-offs

| Aggressive retry | Conservative retry |
|------------------|--------------------|
| Higher success rate | Less load on failing service |
| Risk of thundering herd | Slower recovery for user |
| **Mitigation**: Backoff + jitter + limit | **Mitigation**: Fail fast, then circuit breaker |

### Failure Scenarios

- **Service down**: Retries with backoff give it time to recover; circuit breaker stops hammering after repeated failure.
- **Partial success**: Request succeeded on server but response lost; retry may duplicate. Mitigation: idempotent operations.
- **Permanent failure (e.g. 400)**: Do not retry; return error to user.

### Quick Revision (Retry)

- **Backoff**: Increase delay between retries (e.g. exponential).
- **Jitter**: Randomize delay to avoid thundering herd.
- **Limit**: Max retries then fail.
- **Retry only**: 5xx, timeouts, connection errors; not 4xx (unless 429 + Retry-After).
- **Interview**: "We retry with exponential backoff and jitter on 5xx and timeouts, up to 3 times; we make the operation idempotent so duplicate retries are safe."

---

## 3. Backpressure

> **Analogy**: A factory conveyor belt. Items come in from one end and get packaged at the other. If the packaging station gets overwhelmed, the belt has two choices: (1) slow the belt down to match the packaging speed (backpressure), or (2) keep running at full speed until items fall off the end and get lost (no backpressure). A third option: the belt has a buffer zone — a staging area. If the buffer fills up, the belt pauses upstream. Without any of these mechanisms, the floor gets covered in unpackaged items and the whole factory jams.

### Concept Overview

**Backpressure** is the mechanism by which a slower consumer signals producers to slow down or stop sending. Without it, a fast producer (or many producers) can overwhelm a consumer, causing queue growth, memory exhaustion, or cascading failure.

**Why it exists**: In streaming and queue-based systems, producers can be much faster than consumers; backpressure keeps the system stable and prevents resource exhaustion.

### Core Principles

- **Reactive / pull-based**: Consumer pulls when ready (e.g. Kafka consumer fetch); broker doesn't push unbounded.
- **Flow control**: TCP flow control (receiver window); application-level "stop sending until I ack" or "send me N more."
- **Queue depth / lag**: Monitor queue size or consumer lag; if above threshold, slow or reject new work (e.g. return 503, or pause producers).
- **Rate limiting**: Limit producer rate (per user or global) so consumers can keep up.

### Java Implementation

```java
// Backpressure via bounded queue + rejection policy
public class BoundedWorkerPool {

    // Fixed-capacity queue — when full, new tasks are rejected (backpressure)
    private final BlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(1000);
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
        10, 10, 0L, TimeUnit.MILLISECONDS, queue,
        new ThreadPoolExecutor.AbortPolicy() // Throws RejectedExecutionException when full
    );

    public void submit(Runnable task) {
        try {
            executor.execute(task);
        } catch (RejectedExecutionException e) {
            // Signal backpressure to caller — return 503 or enqueue to durable store
            throw new ServiceOverloadedException("Worker pool full, retry later");
        }
    }
}
```

```java
// Backpressure in a reactive stream (Project Reactor)
Flux.fromIterable(events)
    .onBackpressureBuffer(500)           // Buffer up to 500; drop after
    .flatMap(event -> processEvent(event),
             /* concurrency */ 10)       // Max 10 concurrent processings
    .subscribe();
```

```java
// Rate-limit producers with 429 when consumer is overloaded
@RestController
public class IngestController {

    private final Semaphore permits = new Semaphore(500); // max concurrent in-flight

    @PostMapping("/events")
    public ResponseEntity<?> ingest(@RequestBody Event event) {
        if (!permits.tryAcquire()) {
            return ResponseEntity.status(503)
                .header("Retry-After", "2")
                .body("System overloaded, retry in 2 seconds");
        }
        try {
            processAsync(event);
            return ResponseEntity.accepted().build();
        } finally {
            permits.release();
        }
    }
}
```

### Real-World Usage

- **Kafka**: Consumers pull (fetch); lag grows if consumer is slow; backpressure = slow consumer processes backlog, no push from broker.
- **gRPC / HTTP/2**: Flow control via window updates; receiver can reduce window to slow sender.
- **Reactive streams**: Protocols like Reactive Streams (Java) have explicit `request(n)` and backpressure.
- **APIs**: When system is overloaded, return 503 or 429 so clients back off or retry later.

### Circuit Breaker

> **Analogy**: An electrical circuit breaker in your home. Normally the breaker is closed — current flows freely. When it detects a fault (too much current = too many failures), it trips open — current stops flowing, protecting downstream equipment. After a cooldown period, you try resetting it: it goes half-open, allowing a small test current through. If that works without tripping again, the breaker closes and normal operation resumes.

**States:**
```
CLOSED   → Normal operation; requests flow through
OPEN     → Failures exceeded threshold; fast-fail all requests
HALF-OPEN → Testing: let a few requests through to probe recovery
```

```java
public class CircuitBreaker {

    enum State { CLOSED, OPEN, HALF_OPEN }

    private State state = State.CLOSED;
    private int failureCount = 0;
    private Instant openedAt;

    private static final int FAILURE_THRESHOLD = 5;
    private static final Duration COOLDOWN = Duration.ofSeconds(30);

    public <T> T execute(Callable<T> operation) throws Exception {
        if (state == State.OPEN) {
            if (Duration.between(openedAt, Instant.now()).compareTo(COOLDOWN) > 0) {
                state = State.HALF_OPEN;
            } else {
                throw new CircuitOpenException("Circuit open — fast failing");
            }
        }

        try {
            T result = operation.call();
            onSuccess();
            return result;
        } catch (Exception e) {
            onFailure();
            throw e;
        }
    }

    private void onSuccess() {
        failureCount = 0;
        state = State.CLOSED;
    }

    private void onFailure() {
        failureCount++;
        if (failureCount >= FAILURE_THRESHOLD) {
            state = State.OPEN;
            openedAt = Instant.now();
        }
    }
}
```

### Trade-offs

| With backpressure | Without backpressure |
|-------------------|----------------------|
| Consumer not overwhelmed | Queue/memory can grow unbounded |
| Producers must handle "slow down" or 503 | Simpler producer; risk of OOM or cascade |
| **Use**: Bounded resources, stability | **Avoid** in production for fast producers + slow consumers |

### Failure Scenarios

- **Consumer slow**: Backpressure slows producers; scale consumers or optimize; or drop/sample if acceptable.
- **No backpressure**: Queue grows; memory or disk full; broker or consumer crashes; cascade to producers.
- **Aggressive backpressure**: Rejecting all requests can starve the system; use gradual (e.g. 503 with Retry-After) or priority queues.

### Quick Revision (Backpressure)

- **Definition**: Slower consumer signals producers to slow down or stop.
- **Why**: Prevents queue growth and resource exhaustion.
- **How**: Pull-based consumption; flow control (TCP, HTTP/2); queue depth/lag monitoring and 503/429 when overloaded.
- **Interview**: "We use Kafka so consumers pull at their own rate; we monitor consumer lag and if it grows too high we scale consumers or temporarily rate-limit producers and return 503 so clients retry later."

---

## Summary Table

| Concept | Problem | Solution |
|--------|---------|----------|
| **Idempotency** | Duplicate requests (retries, at-least-once) cause duplicate side effects | Idempotency key + store result; or natural key + "already processed" |
| **Retry** | Transient failures; need to succeed without manual retry | Exponential backoff + jitter; max attempts; retry only retryable errors; idempotent backend |
| **Backpressure** | Fast producer overwhelms slow consumer | Pull-based; flow control; queue depth/lag; 503/429 when overloaded |
| **Circuit Breaker** | Retrying into a failing service amplifies the outage | Open circuit on repeated failures; half-open probe; close on recovery |

---

## Quick Revision (All Three)

- **Idempotency**: Same effect once or many times; idempotency keys or natural keys; required for safe retries and at-least-once.
- **Retry**: Backoff + jitter + max attempts; retry 5xx/timeout only; idempotent operations.
- **Backpressure**: Consumer signals "slow down"; pull-based consumption; monitor lag; return 503 when overloaded.
- **Circuit Breaker**: Closed (normal) → Open (failing fast) → Half-open (probing) → Closed (recovered).
- **Interview**: "We design write and payment operations to be idempotent with keys, retry with backoff and jitter on transient failures, use pull-based consumption and 503 under overload so we don't overwhelm downstream services, and wrap external calls in circuit breakers so a dependency failure doesn't cascade."

---

## 4. Distributed Failure Modes

> **Analogy**: A distributed system is like a city's power grid. Most of the time everything works. But failures are not random — they follow predictable patterns: a transformer blows (node failure), a road closes (network partition), a substation gets overwhelmed (cascading overload), a worker misreads a signal (Byzantine fault). Knowing the failure taxonomy means you can design defenses in advance instead of firefighting after the fact.

### Taxonomy of Failures

#### Network Failures

**Partial failure** — some nodes can reach each other but not all. This is the hardest failure type because the system is neither fully up nor fully down.

*Analogy*: You're on a conference call and some participants can hear each other but not you. From your perspective the call is fine. From theirs, you've gone silent. This is a network partition from one direction only.

**Mitigation**:
- Set timeouts on all network calls — never block indefinitely
- Use circuit breakers to stop sending requests to nodes that are likely down
- Design reads to succeed even during partial partition (AP systems), or return errors rather than stale data (CP systems)

**Slow network** — requests don't fail immediately, they take 10× longer than expected. This is worse than outright failure because all your threads pile up waiting.

*Analogy*: A highway where no cars crash but traffic crawls at 5 mph. No accidents to respond to — just gridlock growing.

**Mitigation**: Timeouts + connection pool limits. A 30-second timeout with 100 threads means one slow downstream can hold 100 threads for 30 seconds = 3000 thread-seconds of starvation.

---

#### Node Failures

**Crash-stop** — a node abruptly stops responding. Clean failure: other nodes eventually notice via health checks and stop routing to it.

**Crash-recovery** — a node crashes and restarts, potentially with stale or incomplete state. The hard case: the node was mid-write when it crashed. Did the write commit?

*Analogy*: A waiter who steps out for a break mid-order. When they come back, they can't remember which orders they already placed to the kitchen.

**Mitigation**: Write-ahead logging (WAL) — commit to a log before applying. On restart, replay the log to restore consistent state. Used by PostgreSQL, Kafka, and most durable systems.

**Byzantine failure** — a node behaves incorrectly or maliciously: returns wrong answers, sends conflicting messages to different nodes. Rare in internal systems, relevant in blockchain and federated systems.

*Analogy*: A committee member who tells each other member a different version of the vote count.

**Mitigation**: Byzantine fault-tolerant (BFT) consensus — requires 3f+1 nodes to tolerate f Byzantine nodes. Too expensive for most internal systems; usually handled by trusted network boundaries instead.

---

#### Cascade Failures

One node failing causes load to shift to other nodes, which then fail under the increased load, causing a full outage.

*Analogy*: A restaurant loses one waiter. The remaining waiters get more tables. They slow down. Customers wait longer. More customers leave, but the ones who stay need more attention. The waiters get more overloaded. Eventually the kitchen can't keep up either.

**Mitigation**:
- **Circuit breaker**: Stop sending requests to a failing service instead of letting calls pile up. After a threshold of failures, "trip" the circuit: return errors immediately without attempting the call. After a timeout, try again ("half-open" state). Hystrix, Resilience4j implement this.
- **Load shedding**: Deliberately reject requests when the system is above capacity. Return 503 now rather than 500 in 30 seconds. Prioritize critical traffic (checkout > recommendations).
- **Bulkhead isolation**: Partition resources so one failing component cannot consume all shared resources. Like a ship's watertight compartments — one flooding doesn't sink the whole ship.

```java
// Circuit breaker state machine
enum CircuitState { CLOSED, OPEN, HALF_OPEN }

public class CircuitBreaker {
    private CircuitState state = CircuitState.CLOSED;
    private int failureCount = 0;
    private final int failureThreshold = 5;
    private long lastFailureTime;
    private final long timeout = 30_000; // 30 seconds

    public <T> T execute(Supplier<T> call) {
        if (state == CircuitState.OPEN) {
            if (System.currentTimeMillis() - lastFailureTime > timeout) {
                state = CircuitState.HALF_OPEN; // Try one request
            } else {
                throw new CircuitOpenException("Circuit is OPEN — fast-failing");
            }
        }

        try {
            T result = call.get();
            onSuccess();
            return result;
        } catch (Exception e) {
            onFailure();
            throw e;
        }
    }

    private void onSuccess() {
        failureCount = 0;
        state = CircuitState.CLOSED;
    }

    private void onFailure() {
        failureCount++;
        lastFailureTime = System.currentTimeMillis();
        if (failureCount >= failureThreshold) {
            state = CircuitState.OPEN;
        }
    }
}
```

---

#### Data Corruption & Split-Brain

**Split-brain** — a network partition causes two nodes to both believe they are the leader (primary). Both accept writes. When the partition heals, they have divergent state.

*Analogy*: A company's CEO travels internationally. Poor connectivity causes both the CEO and the VP to believe they have authority to approve the $10M deal. Both sign separate contracts with different terms.

**Mitigation**:
- Leader election via consensus (Raft/Paxos) ensures only one leader at a time
- Fencing tokens: each leader gets an increasing token; storage layer rejects writes from leaders with stale tokens
- STONITH ("Shoot The Other Node In The Head"): old leader gets forcibly killed before new leader is activated

**Stale reads** — a replica is behind the primary. A read from that replica returns outdated data.

*Analogy*: Calling the bank's overseas branch for your balance, not knowing the HQ branch processed a withdrawal 10 seconds ago.

**Mitigation**: Route reads to primary for strong consistency; accept stale reads for eventual consistency (set a staleness bound, e.g., "at most 500ms stale"); use sticky reads so a session always reads its own writes.

---

### Failure Mode Decision Matrix

| Failure | Detection | Recovery Strategy | Trade-off |
|---------|-----------|-------------------|-----------|
| Node crash | Health check timeout | Remove from pool; promote replica | Health check interval = detection lag |
| Network partition | Timeout + error rate | Circuit breaker, fail fast | False positives during slow networks |
| Slow node | Latency P99 spike | Timeout; remove if consistent | Tight timeouts cause false failures |
| Split-brain | Fencing token conflict | STONITH; consensus protocol | Raft adds latency on all writes |
| Cascade | Error rate spike upstream | Load shed; bulkhead; circuit break | Shedding load during peak hits revenue |
| Stale reads | Replication lag metric | Read from primary; version checks | Primary reads increase primary load |
| Byzantine | Inconsistent responses | BFT consensus | 3× resource cost |

---

### Quick Revision

- **Partial failure is worse than total failure** — your system doesn't know if it's healthy or not
- **Timeouts are mandatory** — every network call must have one; pick them based on P99 latency, not hope
- **Circuit breaker** = trip after N failures, fast-fail during OPEN, probe during HALF_OPEN
- **Cascade prevention**: circuit breakers + load shedding + bulkhead isolation (these three together)
- **Split-brain**: fencing tokens + consensus-based leader election
- **Interview answer**: "We set timeouts on all downstream calls, use a circuit breaker pattern with exponential backoff on retries, and bulkhead-isolate critical paths from non-critical ones so payment processing can't be starved by the recommendations service."
