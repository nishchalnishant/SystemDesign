---
module: 04-advanced-topics
status: unread
tags: [04-advanced-topics, system-design, advanced-topics]
---
# Microservices

## What Are Microservices?

**Question**: Your monolith has 200 engineers committing to the same repo. Deployments take 45 minutes and happen twice a week because they require coordination across 30 teams. A bug in the recommendations module causes a deploy rollback that also rolls back a critical payment fix. The search team wants to rewrite their module in Go but the entire codebase is Java. The checkout service needs to scale to 10× during Black Friday but scaling the monolith means scaling the admin portal too. What structural change makes all of these problems go away?

**Physical constraint**: A single process has one deployment unit, one scaling unit, and one failure domain. Any team that shares that process is coupled to every other team's code, release schedule, and failure modes. There is no way to give Team A independent deployability while Teams A and B share the same JVM process — independent deployability requires independent processes.

**Minimal solution**: Split into two services. This works until you need inter-service calls (network latency replaces in-process method calls), shared data (which service owns it?), and debugging across service boundaries (where is the error?). You've created distributed systems problems you didn't have before.

**Production generalization**: Microservices is the trade of monolith problems (deployment coupling, scaling coupling, team coupling) for distributed systems problems (network failures, consistency, observability). The trade is only worth it when the monolith pain is real and your team has the operational maturity to manage the distributed complexity. Every pattern below exists to manage one specific distributed systems problem that microservices creates.

**Analogy:** A restaurant that used to have one chef do everything (monolith) now has specialized stations — grill, salad, dessert, drinks. Each station operates independently. The grill chef doesn't care what the dessert station is doing. They communicate through the expediter (API gateway). If the salad station is overwhelmed, you add another salad chef without touching the grill station. If dessert is broken, the rest of the kitchen keeps running.

**Contrast with a monolith:** In a monolith, all functionality is deployed as one unit. One team's bad deploy can take down everyone. Scaling means scaling everything, even parts that don't need it.

---

## File Mindmap

```
Microservices
├── Why It Exists
│   ├── Problem → 200 engineers, same repo; deploys take 45 min, twice/week; Black Friday: scale checkout not admin portal
│   └── Physical limit → one JVM process = one deployment unit, one scaling unit, one failure domain
├── Monolith vs Microservices Trade
│   ├── Monolith problems → deployment coupling, team coupling, scaling coupling, language lock-in
│   ├── Microservices problems → network failures, consistency, observability, operational complexity
│   └── Rule: only worth the trade when monolith pain is real + team has operational maturity
├── When NOT to Use Microservices
│   ├── Small team (<10 engineers) → overhead exceeds benefit
│   ├── Early product → bounded contexts not yet clear; premature decomposition is expensive
│   └── Simple CRUD → distributed systems complexity for no gain
├── API Gateway
│   ├── Single entry point → auth, rate limiting, routing, SSL termination
│   ├── Spring Cloud Gateway → route predicates + filters; GlobalFilter for cross-cutting concerns
│   └── Avoids N×M client-service coupling; all clients talk to one stable interface
├── Service Discovery
│   ├── Problem → service IPs change in containerized environments; no static addresses
│   ├── Client-side (Eureka + Ribbon) → service registers on start; client fetches registry and load-balances
│   └── Server-side (K8s Service + DNS) → cluster DNS resolves service name to virtual IP
├── Circuit Breaker (Resilience4j)
│   ├── @CircuitBreaker annotation + YAML config: failure-rate-threshold, wait-duration-in-open-state
│   ├── CLOSED → OPEN at 50% failure → HALF_OPEN after wait → probe → CLOSED or OPEN
│   └── Fallback method → graceful degradation when circuit open
├── Saga Pattern
│   ├── Choreography → each service listens for events, publishes next event; no central coordinator
│   ├── Orchestration → central saga orchestrator sends commands, handles compensations
│   └── Compensating transactions → rollback by undoing prior steps (e.g., RefundPayment event)
├── Service Mesh (Sidecar)
│   ├── Envoy/Istio sidecar proxy → mTLS, retries, circuit breaking at mesh layer
│   ├── No code changes needed → infrastructure-level resilience
│   └── Trade-off: adds latency (~1ms), operational complexity
├── Sync vs Async Communication
│   ├── REST/gRPC → synchronous; use when caller needs immediate result; adds latency coupling
│   └── Kafka → async; decouple producer from consumer; fan-out; absorbs traffic bursts
├── DB-per-Service
│   ├── Each service owns its data; no shared DB → no coupling through DB schema
│   └── Cost: joins across services require API calls or denormalized read models (CQRS)
├── Kubernetes Deployment
│   ├── Rolling update → maxSurge/maxUnavailable → zero-downtime deploys
│   ├── Liveness probe → restart if unhealthy; Readiness probe → remove from LB if not ready
│   └── Horizontal Pod Autoscaler → scale on CPU/custom metrics
├── Distributed Tracing
│   ├── Trace ID propagated in HTTP headers across all service hops
│   └── Jaeger / Zipkin → visualize full request trace across 10+ services
└── Interview Angles
    ├── "How do you handle a distributed transaction?" → Saga + compensating transactions
    ├── "How do you debug a production issue across 10 services?" → distributed tracing with trace ID
    └── Follow-up: data consistency across services → outbox pattern + eventual consistency
```

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

**Question**: You have 12 microservices. A mobile client needs data from 4 of them to render a single screen. It makes 4 parallel API calls. Each call crosses the mobile network (~50ms RTT). Total: 200ms minimum just in network time, plus battery drain and error handling complexity on the client. The client also needs to know the IP or hostname of all 4 services. When you add service 13, you update every client. What sits in front of all services to fix this?

**Physical constraint**: Every network hop from a mobile device adds 30–100ms RTT (cellular) or 5–20ms (WiFi). Four separate calls from mobile = 4× the latency cost. A datacenter-internal call is ~1ms RTT — 30–100× cheaper. An aggregation layer inside the DC can make the 4 internal calls in parallel and return one response to the client.

**Minimal solution**: Clients call each service directly. Breaks at: N services require clients to know N addresses, auth logic duplicates across all services, and one mobile screen with 4 service calls has 4× the failure surface.

**Production generalization**: The API Gateway is a single entry point that absorbs all the cross-cutting concerns (auth, rate limiting, TLS termination, routing) once, centrally. The BFF (Backend For Frontend) pattern extends this further: a dedicated gateway per client type, so mobile gets an aggregated endpoint optimized for its payload and the web client gets a different one.

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

**Question**: You deploy 10 instances of the Order Service in Kubernetes. Each instance gets a different ephemeral IP. The Payment Service needs to call the Order Service. You hardcode the IP of one instance. That instance gets restarted during a rolling deploy — its IP changes. The Payment Service is now calling a dead address. How do you call a service whose IP changes constantly?

**Physical constraint**: Container IPs are assigned dynamically and change on every restart, reschedule, or scale event. With Kubernetes spinning up and down pods continuously, a hardcoded IP has a half-life of hours. You cannot use IP addresses as stable service identifiers in a containerized environment.

**Minimal solution**: Give each service a fixed hostname in `/etc/hosts`. Breaks when: services scale horizontally (one hostname, one IP — no load balancing), when services move between nodes, or in multi-cluster setups.

**Production generalization**: A service registry (Kubernetes DNS, Consul, Eureka) decouples "name" from "IP." Services register themselves on startup with their current IP. Callers look up the name and get a current, healthy IP back. The registry is the stable address; the underlying IPs are an implementation detail.

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

**Question**: The Payment Service is responding in 30 seconds instead of 300ms. Each Order Service thread waits 30 seconds for a response. You have a thread pool of 200 threads. At normal load (100 req/sec) with 300ms response time, you use 30 threads. With 30-second responses, each new request holds a thread for 30 seconds — at 100 req/sec you exhaust all 200 threads in 2 seconds. Now your Order Service is also "down" even though only Payment is slow. How do you prevent a slow dependency from taking down your healthy service?

**Physical constraint**: Thread pools are finite. A thread blocked on a network call is a thread not available for any other work. At a per-thread memory cost of ~1MB and typical JVM heap constraints, you cannot have more than a few thousand threads per process. One slow downstream with a long timeout can exhaust your entire thread budget faster than your load balancer can drain the old connections.

**Minimal solution**: Set a 1-second timeout on all downstream calls. Better — but now you're failing fast for 1 second per call. At 100 req/sec during a Payment outage, you're still making 100 calls/sec to a broken service. You're wasting 100 network round-trips per second to learn the same thing you already knew: Payment is down.

**Production generalization**: After N consecutive failures, stop making the call entirely (open the circuit). Return the fallback immediately — no network cost. After a cooldown, let one probe request through to check recovery (half-open). This converts "100 calls/sec to a broken service" into "0 calls/sec for 10 seconds, then 1 probe."

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

**Question**: Checkout requires: reserve inventory (Inventory Service), charge card (Payment Service), create order (Order Service). These are three separate databases. 2PC would work but the coordinator becomes a SPOF and blocks all three services when it crashes. What is the alternative that handles failures without locking?

**Physical constraint**: Each service owns its database. A database transaction only locks rows within one database — crossing a database boundary breaks ACID. The only cross-service operation that doesn't require a distributed lock is a local operation followed by a message that triggers the next local operation. The saga pattern exploits this: each step is a local transaction; the sequence is coordinated via events, not locks.

**Minimal solution**: Call the three services sequentially and if any fails, call the ones that already succeeded to undo. This is a saga with manual compensation logic — functional but fragile if the "undo" call also fails.

**Production generalization**: A saga formalizes this into a sequence of (forward transaction, compensating transaction) pairs. Each compensating transaction must be idempotent (safe to call multiple times) and can always succeed (no blocking on external systems). The orchestration vs. choreography choice determines whether a central coordinator drives the steps (easier to reason about) or each service reacts to events from the previous step (looser coupling, harder to observe).

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

**Question**: You have 30 microservices. Every service needs mTLS for encryption, distributed tracing headers, and Prometheus metrics. You could add this to each service — but that is 30 implementations, 30 places to update when the tracing library changes, and 30 services that now contain infrastructure code mixed with business logic. How do you add these cross-cutting concerns to every service without modifying any service?

**Physical constraint**: You cannot inspect or intercept network traffic from outside the process without a proxy. But if the proxy runs in the same Linux network namespace as the service (same pod), it can intercept all inbound and outbound traffic transparently — the service sends a plain HTTP call and the proxy upgrades it to mTLS before it leaves the pod.

**Minimal solution**: Shared library that every service imports. Breaks at: every language needs its own implementation, library upgrades require redeploying all services simultaneously, and business code is polluted with infrastructure concerns.

**Production generalization**: The sidecar proxy runs as a separate container in the same pod, sharing the network namespace. The service process never knows the sidecar exists — it sends and receives plain traffic. The sidecar intercepts everything: adds TLS, injects trace headers, emits metrics. This is the foundation of a service mesh (Istio, Linkerd): uniform observability and security across all services with zero per-service implementation cost.

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

## Deep Dive: Istio VirtualService, DestinationRule, and Canary Deployments

Istio is a service mesh built on Envoy sidecars. The two most important Istio resources for interviews are **VirtualService** (traffic routing rules) and **DestinationRule** (what to do with traffic once it arrives at a subset of pods).

### Core Concepts

```
VirtualService  → "where does traffic go?" (routing rules, traffic splits, retries, timeouts)
DestinationRule → "how do we treat each destination?" (subsets, load balancing, circuit breaking, mTLS)
```

Together they answer: route 90% of `/orders` traffic to stable pods and 10% to canary pods, with mTLS and circuit breaking on both.

---

### DestinationRule — Define Subsets

A subset maps to a set of pods selected by labels. You must define subsets in DestinationRule before you can reference them in VirtualService.

```yaml
apiVersion: networking.istio.io/v1alpha3
kind: DestinationRule
metadata:
  name: orders-destination
  namespace: production
spec:
  host: orders-service          # Kubernetes Service name
  trafficPolicy:
    tls:
      mode: ISTIO_MUTUAL        # Enforce mTLS for all traffic to this service
    connectionPool:
      http:
        http1MaxPendingRequests: 100
        http2MaxRequests: 1000
    outlierDetection:           # Circuit breaker: eject unhealthy pods
      consecutive5xxErrors: 5   # After 5 consecutive 5xx errors...
      interval: 10s             # ...within 10 seconds...
      baseEjectionTime: 30s     # ...eject pod for 30 seconds
      maxEjectionPercent: 50    # Never eject more than 50% of pods
  subsets:
    - name: stable              # Label selector for stable pods
      labels:
        version: stable
      trafficPolicy:
        loadBalancer:
          simple: ROUND_ROBIN
    - name: canary              # Label selector for canary pods
      labels:
        version: canary
      trafficPolicy:
        loadBalancer:
          simple: LEAST_CONN    # Canary uses least-connections (fewer pods)
```

---

### VirtualService — Traffic Splitting (Canary Deployment)

```yaml
apiVersion: networking.istio.io/v1alpha3
kind: VirtualService
metadata:
  name: orders-routing
  namespace: production
spec:
  hosts:
    - orders-service
  http:
    - match:
        - headers:
            x-canary-user:
              exact: "true"    # Specific users always get canary (internal testers)
      route:
        - destination:
            host: orders-service
            subset: canary
            port:
              number: 8080

    - route:                    # Default route: weighted traffic split
        - destination:
            host: orders-service
            subset: stable
            port:
              number: 8080
          weight: 90            # 90% to stable
        - destination:
            host: orders-service
            subset: canary
            port:
              number: 8080
          weight: 10            # 10% to canary
      timeout: 5s
      retries:
        attempts: 3
        perTryTimeout: 2s
        retryOn: "5xx,reset,connect-failure"
```

**Canary rollout progression:**
```
Start:  stable=100, canary=0  (deploy canary pods, no traffic yet)
Step 1: stable=90,  canary=10  (monitor error rate, latency p99)
Step 2: stable=50,  canary=50  (if metrics healthy, continue)
Step 3: stable=0,   canary=100 (full cutover; rename canary→stable)
Rollback: stable=100, canary=0  (instant, no pod restart needed)
```

---

### VirtualService — Fault Injection (Chaos Testing)

Istio can inject faults at the mesh layer without changing any service code:

```yaml
http:
  - fault:
      delay:
        percentage:
          value: 10.0           # 10% of requests
        fixedDelay: 500ms       # get a 500ms artificial delay
      abort:
        percentage:
          value: 5.0            # 5% of requests
        httpStatus: 503         # get an immediate 503
    route:
      - destination:
          host: inventory-service
          subset: stable
```

Use this in pre-production to validate that your circuit breakers and timeouts are configured correctly.

---

### mTLS Mode Configuration

```yaml
# Strict: all traffic must be mTLS (rejects plain HTTP)
spec:
  trafficPolicy:
    tls:
      mode: ISTIO_MUTUAL   # Istio manages cert rotation automatically

# Permissive: accepts both mTLS and plain HTTP (use during migration)
spec:
  trafficPolicy:
    tls:
      mode: DISABLE        # or leave mode unset = permissive default

# PeerAuthentication: enforce mesh-wide or namespace-wide policy
apiVersion: security.istio.io/v1beta1
kind: PeerAuthentication
metadata:
  name: default
  namespace: production
spec:
  mtls:
    mode: STRICT           # All services in 'production' namespace require mTLS
```

**Migration path:** Start with `PERMISSIVE` (accepts both). Once all sidecars are injected and verified, switch to `STRICT`. No certificate rotation code needed — Istio rotates certs automatically via SPIFFE/SPIRE.

---

### Interview Talking Points

**"How do you do a canary deployment with zero code changes?"**
> "With Istio, I define a DestinationRule with two subsets — `stable` and `canary` — mapped to pod label selectors. A VirtualService routes 90% of traffic to `stable` and 10% to `canary`. To promote, I change the weight to 50/50, then 100/0. Rollback is instant — just flip the weight back. The services themselves have no routing logic."

**"How does mTLS work in a service mesh?"**
> "Each pod gets an Envoy sidecar injected automatically. The sidecar intercepts all inbound and outbound TCP traffic. Istio's control plane issues a SPIFFE certificate to each sidecar based on the pod's Kubernetes service account. When Order Service calls Payment Service, Order's sidecar presents its cert, Payment's sidecar verifies it. The application code sends plain HTTP — it never knows TLS is happening. The mesh handles cert rotation every 24 hours automatically."

**"How does Istio circuit breaking differ from Resilience4j?"**
> "Resilience4j is in-process — it only protects calls from one specific service. If 20 services call Payment Service, each needs its own circuit breaker configured. Istio's `outlierDetection` in DestinationRule operates at the mesh layer — it monitors real traffic across all callers and ejects a Payment pod after N consecutive errors, regardless of which service sent the request. Mesh-level circuit breaking is uniform and zero-code."

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

**Question**: The Order Service and User Service share a Postgres database. The Order team needs to add a nullable column to the `users` table for order preferences. The User team is running a migration that holds a table lock for 30 seconds. The Order team's deploy fails because the User team's migration is blocking their query. Both teams are blocked by the other's schema. How do you give each team full ownership of their schema without impacting the other?

**Physical constraint**: A database enforces a schema. Any process that connects to the database is constrained by that schema. Two services sharing a database are therefore sharing a schema — a deployment constraint is identical to a code dependency. Independent deployability requires independent schema ownership, which requires separate database instances (or at minimum separate schemas with enforced access controls).

**Minimal solution**: Give each service its own schema in the same database cluster. Reduces interference but doesn't eliminate it: a runaway query from one service can exhaust connection pool or IOPS, affecting the other. Shared infrastructure = shared failure domain.

**Production generalization**: Database-per-service is the logical endpoint: each service has its own database instance, managed independently. Cross-service queries become API calls or event-driven denormalized read models. The cost is no cross-service JOINs — the benefit is true deployment independence and independent scaling.

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
