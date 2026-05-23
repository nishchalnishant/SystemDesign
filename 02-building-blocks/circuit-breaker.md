---
module: 02-building-blocks
status: unread
tags: [02-building-blocks, system-design, building-blocks]
---
# Circuit Breaker

> Stops cascading failures by short-circuiting calls to a failing downstream service until it recovers.

---

## File Mindmap

```
Circuit Breaker
├── Why It Exists
│   ├── Problem → Thread pool exhaustion when downstream is slow
│   └── Forces → Network timeouts are unbounded; threads block; upstream dies too
├── Core Concepts
│   ├── Closed → calls pass through; failures counted
│   ├── Open → calls fail-fast; no network hit
│   └── Half-Open → probe request sent; decide to close or stay open
├── Strategies / Types
│   ├── Count-based → trip after N consecutive failures → simple, bursty traffic can false-trip
│   └── Sliding-window → trip after failure % in last N seconds → smoother, more config
├── Trade-offs
│   ├── Pro: Prevents thread starvation; fast failure feedback
│   └── Con: False positives under bursty but recoverable load
├── Failure Modes
│   └── Half-open storm → limit probe concurrency to 1
└── Interview Angles
    └── Bulkhead vs Circuit Breaker → isolation vs trip
```

---

## 1. Why Circuit Breaker Exists

**Question**: You have a checkout service that calls a payment service. The payment service starts timing out at 30 s per call. Your thread pool has 200 threads. Within 200 concurrent requests you have exhausted all threads. New requests to checkout (not even touching payment) now queue and timeout. Your entire checkout service is down because of a downstream dependency.

**Physical constraint**: Every thread waiting on a network socket holds ~1 MB of stack. A JVM with 4 GB heap has ~4000 usable threads at most. A slow downstream converts your concurrency budget into a waiting queue in seconds.

**Minimal solution**: Set a hard timeout on every downstream call (e.g., 500 ms). This prevents indefinite blocking, but every call still pays the 500 ms penalty while the downstream is fully down — you burn threads on guaranteed failures.

**Production generalization**: The circuit breaker remembers past failures. After a threshold (e.g., 50% failure rate in last 10 s), it opens the circuit: subsequent calls return immediately with a fallback without touching the network. After a cool-down (e.g., 30 s), it lets one probe request through (half-open). If that succeeds, it closes; otherwise it opens again.

---

## 2. Core Concepts / How It Works

### State Machine

```
         failures >= threshold
  CLOSED ─────────────────────► OPEN
    ▲                              │
    │  probe succeeds              │ timeout elapsed
    │                              ▼
    └──────────────────────── HALF-OPEN
              probe fails → OPEN again
```

| State     | What happens to calls              | Failure counter |
|-----------|------------------------------------|-----------------|
| CLOSED    | Pass through; failures increment counter | Active       |
| OPEN      | Fail immediately (no network call) | Frozen         |
| HALF-OPEN | One probe call allowed; rest fail  | Reset on success|

### Key Parameters

| Parameter              | Typical value  | Effect of tuning too low        | Effect of tuning too high      |
|------------------------|----------------|---------------------------------|--------------------------------|
| Failure rate threshold | 50%            | False trips on transient spikes | Trips too late; threads starve |
| Minimum call volume    | 10 calls/window| Trips on first failure          | Never trips in low-traffic     |
| Slow-call threshold    | 2 × P99 RT     | Marks healthy slow calls bad    | Misses truly slow degradation  |
| Wait duration (open)   | 30 s           | Too aggressive retries          | Unnecessary service blackout   |
| Half-open max calls    | 1-5            | Insufficient signal             | Probe storm on recovering svc  |

### Sliding Window Types

**Count-based**: Last N calls. Simple. Sensitive to bursty traffic.

**Time-based**: Last N seconds. Smooths traffic spikes. Requires ring buffer (O(N) memory per breaker instance).

---

## 3. Real-World Usage

| System          | Circuit Breaker usage                                                     |
|-----------------|---------------------------------------------------------------------------|
| Netflix Hystrix | Pioneered the pattern; wraps every inter-service call; dashboard metrics  |
| Resilience4j    | Modern replacement; integrates with Micrometer/Prometheus                 |
| AWS SDK         | Built-in retry + circuit breaker for S3, DynamoDB client calls            |
| Istio/Envoy     | Sidecar proxy enforces outlier detection (equivalent to circuit breaking)  |
| Spring Cloud    | `@CircuitBreaker` annotation wraps any Spring bean method                  |

**Netflix case**: Hystrix was introduced after a 2012 outage where one slow API call caused a thread pool cascade. After Hystrix rollout, they handled downstream failures without full service outages.

**Envoy outlier detection**: Ejection of hosts with >50% 5xx in 10 s for 30 s minimum ejection time — this is circuit breaking at the load balancer layer, transparent to application code.

---

## 4. Trade-offs

| Dimension        | Pro                                          | Con                                              |
|------------------|----------------------------------------------|--------------------------------------------------|
| Failure isolation| Prevents cascading; upstream stays healthy   | Open circuit means 100% of calls fail-fast       |
| Latency          | Sub-millisecond fail-fast when open          | Adds ~0.1 ms overhead per call when closed       |
| Recovery         | Half-open probes allow automatic recovery    | Probe can fail under load → circuit stays open   |
| Observability    | State transitions are measurable events      | Multiple instances each have independent state   |
| Fallback quality | Forces teams to define degraded behavior     | Fallback logic adds code complexity              |

**When to use**: Any synchronous call to a service you don't own, or any I/O operation with variable latency (DB, external API, cache miss path).

**When NOT to use**: Idempotent local computations. Calls where fail-fast is worse than waiting (e.g., financial transactions that must complete). Use bulkhead (thread pool isolation) instead when you want containment without fail-fast.

---

## 5. Failure Scenarios

| Scenario                         | Symptom                                      | Mitigation                                             |
|----------------------------------|----------------------------------------------|--------------------------------------------------------|
| False trip on traffic spike      | Circuit opens briefly; healthy calls fail    | Increase minimum call volume; use time-based window    |
| Half-open probe storm            | Many instances probe simultaneously           | Set `permittedNumberOfCallsInHalfOpenState = 1`        |
| Downstream recovers but circuit stays open | Stale open state | Ensure wait duration is not too long; use health check endpoint |
| Fallback also fails              | Total failure, no graceful degradation       | Fallback must be simple (static response, cache read)  |
| Distributed state mismatch       | Service A trips, service B has no idea       | Centralize metrics; use sidecar (Envoy) for consistency|
| Thread pool exhaustion before trip| Threshold not reached yet but threads full  | Combine with bulkhead (separate thread pool per downstream) |

---

## 6. Performance Considerations

**Overhead when CLOSED**: Resilience4j adds ~0.1–0.3 ms per call for sliding-window accounting (ring buffer update). Acceptable for services with P99 > 5 ms.

**Memory**: Each CircuitBreaker instance maintains a ring buffer. Time-based window of 10 s at 1000 calls/s = 10,000 entries × ~64 bytes = 640 KB per breaker. Keep count of breaker instances.

**Throughput under OPEN**: Throughput is theoretically infinite because calls don't touch the network — they return immediately. This is the key benefit. Benchmark shows Resilience4j can reject 10M calls/s with circuit open.

**Thread pool sizing**: Combine circuit breaker with a bulkhead of N threads per downstream. Rule of thumb: `N = (upstream_rps × downstream_P99_latency_s) × 1.2`. Example: 500 RPS × 0.1 s P99 × 1.2 = 60 threads.

---

## 7. Implementation Patterns

### Resilience4j — Full Working Example

```java
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

import java.time.Duration;
import java.util.function.Supplier;

public class PaymentServiceClient {

    private final CircuitBreaker circuitBreaker;
    private final PaymentHttpClient httpClient;

    public PaymentServiceClient(PaymentHttpClient httpClient) {
        this.httpClient = httpClient;

        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.TIME_BASED)
            .slidingWindowSize(10)                          // last 10 seconds
            .failureRateThreshold(50)                       // 50% failures → OPEN
            .slowCallRateThreshold(70)                      // 70% slow calls → OPEN
            .slowCallDurationThreshold(Duration.ofMillis(500)) // slow = >500 ms
            .minimumNumberOfCalls(10)                       // need 10 calls before evaluating
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .permittedNumberOfCallsInHalfOpenState(3)       // 3 probes in half-open
            .recordExceptions(Exception.class)
            .build();

        this.circuitBreaker = CircuitBreakerRegistry.ofDefaults()
            .circuitBreaker("paymentService", config);

        // Observe state transitions
        circuitBreaker.getEventPublisher()
            .onStateTransition(event ->
                System.out.printf("[CircuitBreaker] %s → %s%n",
                    event.getStateTransition().getFromState(),
                    event.getStateTransition().getToState()));
    }

    public PaymentResult charge(ChargeRequest request) {
        Supplier<PaymentResult> decorated = CircuitBreaker
            .decorateSupplier(circuitBreaker, () -> httpClient.charge(request));

        return io.github.resilience4j.core.Try.ofSupplier(decorated)
            .recover(throwable -> fallback(request, throwable))
            .get();
    }

    private PaymentResult fallback(ChargeRequest request, Throwable t) {
        // Fallback: queue for async retry, return pending status
        asyncRetryQueue.enqueue(request);
        return PaymentResult.pending("Payment queued; will retry shortly.");
    }
}
```

### Spring Boot Integration

```java
@Service
public class InventoryService {

    @CircuitBreaker(name = "inventoryService", fallbackMethod = "inventoryFallback")
    @TimeLimiter(name = "inventoryService")
    public CompletableFuture<InventoryResponse> getStock(String skuId) {
        return CompletableFuture.supplyAsync(() -> inventoryClient.getStock(skuId));
    }

    public CompletableFuture<InventoryResponse> inventoryFallback(String skuId, Exception ex) {
        // Return cached or default
        return CompletableFuture.completedFuture(InventoryResponse.unavailable());
    }
}
```

`application.yml`:
```yaml
resilience4j:
  circuitbreaker:
    instances:
      inventoryService:
        sliding-window-type: TIME_BASED
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
        permitted-number-of-calls-in-half-open-state: 3
```

### Bulkhead + Circuit Breaker (Combined Pattern)

```java
// Thread pool bulkhead isolates payment calls from the main executor
BulkheadConfig bulkheadConfig = BulkheadConfig.custom()
    .maxConcurrentCalls(20)         // at most 20 in-flight payment calls
    .maxWaitDuration(Duration.ofMillis(100))
    .build();

Bulkhead bulkhead = Bulkhead.of("paymentBulkhead", bulkheadConfig);

// Decorate with both: bulkhead first (capacity), then circuit breaker (failure rate)
Supplier<PaymentResult> decorated = Decorators
    .ofSupplier(() -> httpClient.charge(request))
    .withBulkhead(bulkhead)
    .withCircuitBreaker(circuitBreaker)
    .withFallback(Exception.class, ex -> PaymentResult.pending("queued"))
    .decorate();
```

---

## Quick Revision

- Three states: CLOSED (normal), OPEN (fail-fast), HALF-OPEN (probing)
- Opens when failure rate OR slow-call rate exceeds threshold over sliding window
- Fail-fast in OPEN state costs ~0 ms; saves threads for healthy calls
- Half-open sends limited probes; closes only on success
- Always pair with a fallback; the fallback must be simpler and cheaper than the real call
- Combine with bulkhead (thread pool limit) for full isolation
- Resilience4j is the current Java standard (Hystrix is in maintenance mode)
- Envoy/Istio outlier detection is the equivalent at the infrastructure layer

---

## See Also

- [02-building-blocks/rate-limiting.md](rate-limiting.md) — sister pattern for request control
- [02-building-blocks/load-balancers.md](load-balancers.md) — health checks complement circuit breakers
- [02-building-blocks/service-discovery.md](service-discovery.md) — removing unhealthy instances
- [04-advanced-topics/distributed-systems.md](../04-advanced-topics/distributed-systems.md) — CAP, failure modes
- [05-hld-problems/03-hard/distributed-cache.md](../05-hld-problems/03-hard/distributed-cache.md)

---

## Interview Questions Asked

### Conceptual

**Q1: Explain the three states of a circuit breaker and what triggers each transition.**

A: CLOSED → OPEN: failure rate (or slow-call rate) exceeds threshold over the sliding window AND minimum call volume is met. OPEN → HALF-OPEN: wait duration elapses. HALF-OPEN → CLOSED: all permitted probe calls succeed. HALF-OPEN → OPEN: any probe call fails.

**Q2: What is the difference between a circuit breaker and a retry with exponential backoff?**

A: Retry is per-request: it retries that specific failed call. Circuit breaker is system-level: once open, it stops ALL calls to that downstream, not just the current one. Retries can worsen a degraded system by adding load; circuit breakers reduce load. They are complementary — retry handles transient single-call failures; circuit breaker handles systemic downstream failure.

**Q3: How does Resilience4j's time-based sliding window work internally?**

A: It maintains a ring buffer of N seconds (configurable). Each second slot tracks call count, failure count, and slow-call count. On each call result, the oldest slot is evicted and the new result added. Failure rate = sum(failures across all slots) / sum(total calls across all slots). This gives a smoothed view vs count-based which can be skewed by traffic bursts.

### Comparison / Trade-off

**Q: Circuit breaker vs bulkhead — when do you use each, and when do you use both?**

A: Bulkhead limits concurrency to a downstream — it prevents thread pool exhaustion but all calls still go through (slowly). Circuit breaker fails-fast but doesn't directly control concurrency. Use bulkhead when you want isolation without dropping calls. Use circuit breaker when the downstream is definitively unhealthy and fast failure is preferable. Use both when you want: (1) a cap on in-flight calls so the downstream isn't hammered, AND (2) fast failure when the downstream is proven unhealthy.

### Scenario / Design

**Q: Your payment service is deployed in 50 pods. Each pod has its own circuit breaker instance. The downstream payment gateway is slow. Will the circuit breakers trip at the same time? Is this a problem?**

A: No, they will not trip simultaneously. Each pod independently tracks its own failure window, so they will trip at slightly different times depending on which requests they happened to route. This is generally acceptable — staggered tripping is not harmful. The problem arises if traffic is uneven: lightly-loaded pods never reach the minimum call volume threshold and keep sending calls to the degraded gateway. Solutions: (1) centralize circuit breaker state in Redis (adds latency and a distributed system dependency), (2) use a sidecar proxy (Envoy outlier detection) which operates per upstream host globally, (3) lower the minimum call volume threshold. Envoy is the preferred solution at scale because it is infrastructure-level and consistent across all pods.
