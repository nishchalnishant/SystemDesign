# Microservices

## What Are Microservices?

A microservices architecture structures an application as a collection of small, independently deployable services, each responsible for a single business capability and owning its data.

**Analogy:** A restaurant that used to have one chef do everything (monolith) now has specialized stations — grill, salad, dessert, drinks. Each station operates independently. The grill chef doesn't care what the dessert station is doing. They communicate through the expediter (API gateway). If the salad station is overwhelmed, you add another salad chef without touching the grill station. If dessert is broken, the rest of the kitchen keeps running.

**Contrast with a monolith:** In a monolith, all functionality is deployed as one unit. One team's bad deploy can take down everyone. Scaling means scaling everything, even parts that don't need it.

---

## When to Go Microservices (and When NOT to)

### Good reasons to adopt microservices

- **Team scale**: Multiple teams can own and deploy services independently without coordinating releases
- **Independent scaling**: One service gets 100x more traffic than others (e.g., search vs. admin)
- **Technology heterogeneity**: Different problems need different tools (ML inference in Python, payments in Java, real-time in Go)
- **Fault isolation**: Failures in one service don't cascade to the entire system
- **Compliance**: Different data residency or audit requirements per business domain

### When NOT to use microservices — the "microservices premium"

Martin Fowler coined this: microservices add a significant overhead of operational complexity. You pay this tax upfront, even before you gain the benefits.

**Avoid microservices if:**
- You're in early product-market fit stage (the system will change too fast)
- Small team (< 10 engineers) — coordination overhead eats productivity
- The domain isn't well understood yet — service boundaries drawn wrong are hard to undo
- You don't have DevOps maturity (CI/CD, container orchestration, observability)

**Start with a monolith, modularize, then extract services** — this is the practical path.

---

## Key Patterns

### 1. API Gateway

A single entry point for all clients. The gateway handles:
- **Routing** — forwards requests to the correct service
- **Authentication/Authorization** — validates JWTs before forwarding
- **Rate limiting** — protects backend services from overload
- **Request aggregation** — combines calls to multiple services into one response (BFF pattern)
- **SSL termination**

```
Client → API Gateway → [User Service]
                     → [Order Service]
                     → [Payment Service]
```

Tools: Kong, AWS API Gateway, NGINX, Envoy

**Java example with Spring Cloud Gateway:**
```java
@Bean
public RouteLocator routeLocator(RouteLocatorBuilder builder) {
    return builder.routes()
        .route("user-service", r -> r.path("/users/**")
            .filters(f -> f.addRequestHeader("X-Service", "user"))
            .uri("lb://user-service"))
        .route("order-service", r -> r.path("/orders/**")
            .uri("lb://order-service"))
        .build();
}
```

---

### 2. Service Discovery

Services don't have fixed IPs (containers start/stop). Service discovery solves this.

**Two modes:**
- **Client-side discovery** (Eureka): Client queries registry, does load balancing itself
- **Server-side discovery** (Kubernetes): Load balancer queries registry, client talks to LB

```
Service A → Registry (Eureka/Consul/K8s DNS) → IP:Port of Service B
```

**Java example with Eureka:**
```java
@SpringBootApplication
@EnableEurekaServer
public class DiscoveryServer { ... }

// Client side
@SpringBootApplication
@EnableDiscoveryClient
public class OrderService {
    @Autowired
    private DiscoveryClient discoveryClient;

    public String getUserServiceUrl() {
        List<ServiceInstance> instances = discoveryClient.getInstances("user-service");
        return instances.get(0).getUri().toString();
    }
}
```

---

### 3. Circuit Breaker

When a downstream service is failing, keep calling it? No — fail fast and provide a fallback.

**States:**
- **Closed** — requests flow normally
- **Open** — requests fail immediately (circuit is "tripped")
- **Half-Open** — let one request through to probe recovery

```
Without circuit breaker: slow downstream → thread pool exhaustion → cascade failure
With circuit breaker:    slow downstream → fast fail → caller handles gracefully
```

**Java example with Resilience4j:**
```java
@CircuitBreaker(name = "paymentService", fallbackMethod = "paymentFallback")
public PaymentResponse processPayment(PaymentRequest request) {
    return paymentClient.process(request);
}

public PaymentResponse paymentFallback(PaymentRequest request, Exception ex) {
    // Queue the payment for retry, return "pending" status
    paymentQueue.enqueue(request);
    return PaymentResponse.pending(request.getId());
}
```

Configuration:
```yaml
resilience4j:
  circuitbreaker:
    instances:
      paymentService:
        slidingWindowSize: 10
        failureRateThreshold: 50       # Trip if 50% of last 10 calls fail
        waitDurationInOpenState: 10s   # Wait 10s before half-open
        permittedCallsInHalfOpenState: 3
```

---

### 4. Saga Pattern

Distributed transactions without 2PC. A saga is a sequence of local transactions, each publishing an event or message to trigger the next step.

**Two implementations:**

**Choreography** — services react to events, no central coordinator:
```
Order Service → publishes OrderCreated
  → Inventory Service listens → reserves stock → publishes StockReserved
    → Payment Service listens → charges card → publishes PaymentCompleted
      → Order Service listens → marks order confirmed
```
If payment fails → PaymentFailed event → Inventory Service listens → releases stock (compensating transaction)

**Orchestration** — a saga orchestrator directs each step:
```
Saga Orchestrator → calls Inventory Service
                  → calls Payment Service
                  → calls Notification Service
If step fails → Orchestrator calls compensating transactions in reverse
```

**When to use which:**
- Choreography: simpler, looser coupling, harder to track overall flow
- Orchestration: easier to reason about, single point of failure risk

---

### 5. Sidecar Pattern

A helper container deployed alongside the main service container in the same pod.

The sidecar handles cross-cutting concerns so the service doesn't have to:
- **mTLS** — encrypt all service-to-service traffic (Envoy sidecar)
- **Distributed tracing** — inject trace headers automatically
- **Log collection** — ship logs to centralized platform
- **Health checks and metrics** — expose Prometheus metrics

```
Pod: [Order Service Container] + [Envoy Sidecar]
                                    ↕ mTLS
Pod: [Payment Service Container] + [Envoy Sidecar]
```

This is the foundation of a **service mesh** (Istio, Linkerd).

---

## Communication: Sync vs Async

### Synchronous (REST / gRPC)

Use when: the caller needs an immediate response.

**REST**: easy to implement, human-readable, widely supported. Overhead: HTTP headers, text serialization.

**gRPC**: binary protocol (Protobuf), 5-10x smaller payload, strongly typed, built-in streaming. Good for internal service-to-service.

```java
// gRPC service definition (proto)
service OrderService {
    rpc CreateOrder (CreateOrderRequest) returns (OrderResponse);
    rpc StreamOrders (google.protobuf.Empty) returns (stream Order);
}
```

**Problem with sync**: creates temporal coupling. If Payment Service is down, Order Service call fails. Chain of sync calls = chain of failure points.

### Asynchronous (Kafka / RabbitMQ / SQS)

Use when: the caller doesn't need an immediate response, or for fan-out (one event → multiple consumers).

```
Order Service → publishes to Kafka topic "order-created"
  → Inventory Service (consumer group A) processes
  → Analytics Service (consumer group B) processes
  → Email Service (consumer group C) processes
```

Benefits: decoupling, buffering (handles traffic spikes), retry on failure
Cost: eventual consistency, harder to debug, requires idempotent consumers

**Rule of thumb:** use async for anything that crosses a bounded context boundary and doesn't need an immediate response.

---

## Data Management: Database per Service

Each microservice owns its data. No shared database.

**Why:** shared DB creates coupling. If Order Service and User Service share a DB, a schema change by one team breaks the other. You can't scale or deploy independently.

**Patterns:**

| Pattern | When to use |
|---------|------------|
| Each service has its own DB (Postgres, MySQL) | Default — full isolation |
| Shared DB, separate schema per service | Transitional state during migration |
| CQRS — separate read/write models | High read load with complex queries |
| Event sourcing — store events, derive state | Audit trail required, complex state machines |

**Challenge:** cross-service queries. You can't do a JOIN across services.

Solutions:
1. **API composition** — call each service, join in memory at the API layer
2. **CQRS read model** — maintain a denormalized read replica with data from multiple services (updated via events)
3. **GraphQL federation** — each service owns its schema slice, gateway composes

---

## Deployment: Containers + Kubernetes

Microservices and containers are natural partners. Each service is a Docker image.

**Kubernetes concepts relevant to microservices:**

| Concept | What it does |
|---------|-------------|
| Pod | One or more containers sharing network/storage |
| Deployment | Manages replica count, rolling updates |
| Service | Stable DNS name + load balancing for a set of pods |
| ConfigMap / Secret | Externalizes configuration |
| Horizontal Pod Autoscaler | Scales pods based on CPU/memory metrics |
| Ingress | HTTP routing rules (which path → which service) |

**Zero-downtime deployment:**
```yaml
strategy:
  type: RollingUpdate
  rollingUpdate:
    maxSurge: 1          # Spin up 1 extra pod before terminating old
    maxUnavailable: 0    # Never reduce below desired count during update
```

**Health checks (critical):**
```yaml
livenessProbe:
  httpGet:
    path: /health/live
    port: 8080
  initialDelaySeconds: 30
readinessProbe:
  httpGet:
    path: /health/ready   # Only send traffic when service is ready
    port: 8080
```

---

## Trade-offs and Challenges

### Operational complexity

- Each service needs its own CI/CD pipeline, monitoring, logging
- Local development is hard (run 10 services locally?)
- Service dependency management — which version of Service A works with Service B?

**Mitigation:** invest in platform engineering, developer tooling, and local dev environments (docker-compose, Minikube, Telepresence)

### Distributed tracing

A single request touches 5-10 services. How do you debug a latency spike?

**Solution:** propagate a trace ID through every hop. Tools: Jaeger, Zipkin, AWS X-Ray.

Every service logs: `traceId, spanId, parentSpanId, duration, status`

```java
// Spring Boot with Micrometer Tracing
@GetMapping("/orders/{id}")
public Order getOrder(@PathVariable String id) {
    // Trace ID automatically propagated via HTTP headers
    log.info("Fetching order {}", id);  // Log includes trace context
    return orderService.findById(id);
}
```

### Partial failures

In a monolith, it's all-or-nothing. In microservices, one service can be slow/down while others run fine.

Design for partial failure:
- Circuit breakers on all outbound calls
- Timeouts on every network call (never wait indefinitely)
- Fallbacks that degrade gracefully ("show cached data")
- Bulkhead pattern — isolate thread pools per downstream service

### Network overhead

Every inter-service call crosses the network. Latency adds up.

- Minimize synchronous call chains
- Co-locate latency-sensitive services (same AZ)
- Use gRPC instead of REST for internal calls
- Cache aggressively within services

---

## Interview Talking Points

**"Walk me through how you'd decompose a monolith into microservices."**
- Identify bounded contexts (DDD) — group by business capability, not technical layer
- Extract services with stable, well-defined interfaces first
- Keep a strangler fig pattern — new features go into services, migrate old features gradually
- Don't extract everything at once — operational risk is too high

**"How do you handle distributed transactions?"**
- Avoid 2PC — it's slow and a single point of failure
- Use Saga pattern: choreography for loose coupling, orchestration for complex flows
- Design compensating transactions for every step
- Accept eventual consistency — the business often can too

**"How do you handle a service that's unavailable?"**
- Circuit breaker + fallback (return cached data, return default, queue for retry)
- Timeout + retry with exponential backoff + jitter
- Bulkhead — isolate failure so it doesn't consume all resources

**"What's the biggest risk of microservices?"**
- The "distributed monolith" anti-pattern: tightly coupled services that must deploy together
- Avoid this by owning your data, communicating via events not DB joins, designing for independent deployability

**"When would you recommend NOT using microservices?"**
- Early-stage startup: domain isn't understood, premature decomposition = wrong boundaries
- Small teams: you need at least 2-pizza team per service
- Without DevOps maturity: you'll pay the operational tax without getting the benefits

---

## Quick Reference

| Decision | Recommendation |
|----------|---------------|
| Service boundary | One bounded context, owns its data |
| Sync communication | REST (external), gRPC (internal) |
| Async communication | Kafka (high throughput), RabbitMQ (complex routing) |
| Service discovery | Kubernetes DNS (K8s), Eureka (Spring) |
| Resiliency | Circuit breaker + timeout + retry + fallback |
| Deployment | Kubernetes with rolling updates + health checks |
| Observability | Distributed tracing (Jaeger) + centralized logs + metrics |
| Distributed transactions | Saga pattern (prefer choreography for simple, orchestration for complex) |
