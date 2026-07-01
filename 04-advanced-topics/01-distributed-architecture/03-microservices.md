> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Microservices architecture in depth — the real trade-offs, the service mesh pattern, inter-service communication, and how to handle the hard problems (distributed transactions, tracing, deployment).
>
> **Key topics:**
> - **Monolith vs Microservices** — when to split, when to stay
> - **Inter-service communication** — synchronous (REST/gRPC) vs asynchronous (events)
> - **Service mesh** — what Istio/Envoy actually do and why you need them at scale
> - **Circuit breaker** — preventing cascading failures
> - **Distributed transactions** — Saga pattern (choreography vs orchestration)
> - **API gateway** — edge concerns vs service mesh concerns
> - **Service discovery** — how services find each other in a dynamic fleet
>
> **Key takeaway:** Microservices solve an organizational scaling problem. They create distributed systems problems in return. The service mesh and Saga pattern are how mature teams manage that complexity.

---
module: 04-advanced-topics
status: unread
tags: [04-advanced-topics, system-design, microservices]
---
# Microservices Architecture

---

## Monolith vs Microservices

A **monolith** bundles all business logic into a single deployable unit. A **microservices** architecture splits it into independently deployable services, each owning its own data store.

### When Microservices Win

- **Team scale:** >30–50 engineers stepping on each other's code. Separate services = separate team ownership, separate deploys.
- **Independent scaling:** Checkout needs 100× more capacity than User Settings on Black Friday. With a monolith, you scale everything. With microservices, you scale only what's hot.
- **Polyglot runtime:** ML recommendation service in Python, video processing in Go, core business logic in Java.
- **Fault isolation:** A bug in the Recommendation service should not take down the Checkout service.

### When a Monolith Wins

- Startup/early product (< 10 engineers). You don't understand your domain boundaries yet.
- Monoliths are faster to develop, easier to debug (single process, single stack trace), and simpler to deploy.
- Rule: **start with a modular monolith**. Extract services when team size, deploy frequency, or scaling needs demand it.

---

## The Database-per-Service Rule

The critical invariant: **each service owns its data exclusively**. No other service may read or write its tables directly.

```
✓ Correct:
  Order Service → [orders DB]
  Payment Service → [payments DB]
  Payment Service calls Order Service API to read order data

✗ Wrong:
  Payment Service → [orders DB directly] ← coupling, schema changes break payment
```

Sharing a DB converts microservices into a distributed monolith — you've added network latency without gaining independent deployability.

---

## Inter-Service Communication

### Synchronous: REST and gRPC

**REST (HTTP/JSON):** Simple, widely supported. Every language has HTTP clients. Works well for request-response patterns where the caller needs the result before proceeding.

**gRPC (Protocol Buffers over HTTP/2):** Binary serialization (4–10× smaller than JSON), bidirectional streaming, strongly typed IDL (`.proto` files generate client + server stubs). Use when:
- High throughput / low latency between internal services
- Streaming (server-push events, bidirectional chat)
- Type safety across service boundaries is critical

```protobuf
// payment.proto
service PaymentService {
  rpc Charge(ChargeRequest) returns (ChargeResponse);
  rpc StreamEvents(Empty) returns (stream PaymentEvent);  // server streaming
}

message ChargeRequest {
  string order_id = 1;
  int64 amount_cents = 2;
  string currency = 3;
}
```

**When to use which:**
- External-facing APIs → REST (browser/mobile clients don't speak gRPC natively without grpc-web)
- Internal service-to-service → gRPC (performance + type safety)

### Asynchronous: Event-Driven

Services emit events to a message broker (Kafka, SQS). Other services subscribe. Producer doesn't wait for consumers.

```
Order Service → "order.placed" event → Kafka
    ├─ Inventory Service (decrements stock)
    ├─ Email Service (sends confirmation)
    └─ Analytics Service (records conversion)
```

**Pros:** Loose coupling, fan-out, temporal decoupling (consumers can be down and catch up).
**Cons:** No immediate response, harder to trace, eventual consistency.

Use async when: the caller doesn't need the result, multiple downstream consumers, operations that can be retried.

---

## Service Discovery

In a dynamic fleet, service instances start and stop constantly. Services can't rely on static IPs.

### Client-Side Discovery

Services query a registry (Consul, etcd, Zookeeper) to get the list of healthy instances, then load-balance themselves.

```
Order Service → Consul: "where is Payment Service?"
→ Consul: ["10.0.1.5:8080", "10.0.1.6:8080"]
→ Order Service picks one via round-robin
```

### Server-Side Discovery (via Load Balancer)

Services call a well-known DNS name (`payment-service.internal`). The platform (Kubernetes service, AWS ALB, Envoy) resolves it to a healthy backend.

```
Order Service → payment-service.internal:8080
→ Kubernetes Service → one of the payment pods
```

In Kubernetes, this is the standard model. `kube-proxy` manages service-to-pod routing; services reference each other by DNS name.

---

## Service Mesh

At small scale (5–10 services), direct HTTP/gRPC calls work fine. At large scale (100+ services), cross-cutting concerns multiply: every service needs retries, timeouts, mTLS, tracing, circuit breaking. Writing this logic in every service is expensive and inconsistent.

A **service mesh** moves this logic into a sidecar proxy (Envoy) that runs alongside every service instance. The service's code talks only to localhost; the sidecar handles all network concerns.

```
[Order Service Pod]                    [Payment Service Pod]
  App (localhost:3000)                   App (localhost:3000)
  Envoy sidecar (:15001) ←──mTLS──────→ Envoy sidecar (:15001)
```

**What the sidecar handles:**
- **mTLS:** Mutual TLS between all services — identity verification + encryption in transit, with zero code changes to the application.
- **Retries + timeouts:** Configurable per-route: retry 3 times on 503, timeout after 500ms.
- **Circuit breaking:** Stop sending to a service that's failing (see below).
- **Distributed tracing:** Automatically propagate trace headers and emit spans.
- **Traffic shaping:** Canary deploys (send 5% of traffic to v2 of a service).

**Istio** is the most common service mesh control plane; **Envoy** is the data plane (the sidecar proxy).

```yaml
# Istio VirtualService: canary deploy — 5% to v2
apiVersion: networking.istio.io/v1alpha3
kind: VirtualService
metadata:
  name: payment-service
spec:
  http:
  - route:
    - destination:
        host: payment-service
        subset: v1
      weight: 95
    - destination:
        host: payment-service
        subset: v2
      weight: 5
```

---

## Circuit Breaker

Without a circuit breaker, if Payment Service is slow or down, every call from Order Service blocks waiting for a timeout. Under load, Order Service accumulates threads waiting for Payment — it runs out of threads, becomes slow itself, and cascades up the call chain. This is how one slow service takes down an entire system.

A **circuit breaker** monitors failure rate. When it exceeds a threshold, the breaker **trips open** — subsequent calls to Payment Service fail immediately (fail-fast) without waiting. After a timeout, it enters **half-open**: allows a probe request through. If the probe succeeds, it closes.

```
States:
  CLOSED (normal) → OPEN (failing, fail-fast) → HALF-OPEN (probing) → CLOSED
```

```python
# Using pybreaker
import pybreaker

payment_breaker = pybreaker.CircuitBreaker(
    fail_max=5,       # trip after 5 consecutive failures
    reset_timeout=30  # try again after 30s
)

@payment_breaker
def call_payment_service(order_id):
    return requests.post("http://payment-service/charge", json={"order_id": order_id})
```

**Fallback strategies when the circuit is open:**
- Return a cached/default response
- Queue the request for later (async processing)
- Return a graceful degradation (show "payment unavailable, try again in a moment")

---

## Distributed Transactions: The Saga Pattern

In a monolith, a transaction spans multiple tables atomically (ACID). In microservices, there is no global transaction across service databases. The Saga pattern coordinates multi-service operations as a sequence of local transactions with compensating rollbacks.

### Example: Order Placement

A successful order requires: reserve inventory → charge payment → confirm order. Any step can fail.

### Choreography Saga

Services react to events from each other. No central coordinator.

```
Order Service → "order.created" event
  → Inventory Service: reserve stock → "stock.reserved" event
    → Payment Service: charge card → "payment.completed" event
      → Order Service: mark order confirmed

Failure at Payment:
  → Payment Service → "payment.failed" event
    → Inventory Service: compensating transaction → release stock
      → Order Service: cancel order → notify user
```

**Pros:** No SPOF (no coordinator), services are decoupled.
**Cons:** Hard to track global saga state, complex failure paths, difficult to add new steps.

### Orchestration Saga

A central Saga Orchestrator manages the sequence. It calls each service and handles failures.

```python
class OrderSagaOrchestrator:
    def execute(self, order_id):
        try:
            inventory_txn = self.inventory.reserve(order_id)
            payment_txn = self.payment.charge(order_id)
            self.order.confirm(order_id)
        except PaymentError:
            self.inventory.release(inventory_txn)   # compensating transaction
            self.order.cancel(order_id)
            raise
```

**Pros:** Single place to see/manage saga state. Easy to add steps.
**Cons:** Orchestrator is a SPOF and can become a bottleneck.

**When to use which:** Choreography for simple 2–3 step flows; orchestration for complex business processes where the state machine needs to be visible and auditable.

---

## API Gateway vs Service Mesh: Responsibilities

These are complementary, not competing.

| Concern | API Gateway | Service Mesh |
|---|---|---|
| Who uses it | External clients (browser, mobile) → services | Service → service (east-west) |
| Auth/authZ | Yes (JWT validation, OAuth) | mTLS identity; fine-grained policy in Istio |
| Rate limiting | Yes (per client IP / API key) | Rarely (better at edge) |
| Routing | URL → service mapping | Traffic shaping, canary, retries |
| SSL termination | Yes (north-south) | mTLS (east-west, pod-to-pod) |
| Tracing | Edge span | Full distributed trace propagation |

In a mature microservices deployment: API Gateway at the edge handles external traffic; service mesh handles internal traffic.

---

## Deployment Patterns

### Blue-Green Deployment

Two identical production environments (blue = current, green = new). Switch traffic from blue to green atomically. Rollback = switch back.

- Zero downtime, instant rollback.
- Cost: runs two full production environments simultaneously.

### Canary Deployment

New version receives a small percentage of traffic (1%–5%). Monitor error rate and latency. Gradually increase to 100% if healthy.

```yaml
# Istio canary: 5% to v2
- destination: { subset: v1 }  weight: 95
- destination: { subset: v2 }  weight: 5
```

Automatic rollback if SLO breach detected.

### Feature Flags

Ship the code to all users, but gate the feature behind a flag (LaunchDarkly, Flipt). Enable per user/region/percentage without redeploying.

---

## Interview Questions to Practice

1. **"When would you choose microservices over a monolith?"**
   *When the team is large enough (30+ engineers) that people are constantly blocked by others' deploys or schema changes. Or when different components have dramatically different scaling needs (checkout vs recommendation engine). Early product: always start with a modular monolith. Microservices solve an organizational problem; they introduce distributed systems problems in return.*

2. **"How do you handle a distributed transaction across 3 microservices without a global DB transaction?"**
   *Use the Saga pattern. Each service executes a local transaction and emits an event (choreography) or is called by an orchestrator. If any step fails, compensating transactions undo previous steps. Example: reserve inventory → charge payment → confirm order. If payment fails, compensating transaction releases inventory. The key is each step must be idempotent and have a compensating action.*

3. **"What is a service mesh and why do you need it?"**
   *A service mesh (Istio + Envoy) moves network concerns — retries, timeouts, mTLS, circuit breaking, distributed tracing — into sidecar proxies that run alongside every service. Services don't implement these themselves; the sidecar handles it transparently. You need it when you have 20+ services and the cost of maintaining network resilience logic in every service's codebase becomes prohibitive.*

4. **"A slow downstream service is causing cascading failures. How do you fix it?"**
   *Implement a circuit breaker. Monitor failure rate to the downstream service. When failures exceed a threshold (e.g., 5 in 10 seconds), trip the circuit open — all subsequent calls fail immediately with a fallback response instead of waiting for the timeout. After a reset window, allow a probe request through. If it succeeds, close the circuit. This prevents thread pool exhaustion in the caller service.*

5. **"How do services discover each other in a Kubernetes cluster?"**
   *Kubernetes creates a DNS record for every Service object: `<service-name>.<namespace>.svc.cluster.local`. Services call each other by this DNS name. kube-proxy on each node intercepts traffic to the Service's ClusterIP and distributes it across healthy pods. For external-to-cluster traffic, an Ingress controller (Nginx, ALB Ingress) acts as the L7 API gateway.*
