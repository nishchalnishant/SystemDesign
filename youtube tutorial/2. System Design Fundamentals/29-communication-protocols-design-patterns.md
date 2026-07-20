# Communication Protocols & Design Patterns

> **Source**: Videos #7, #52, #80 from the playlist
> - Everything You Need to Know About HTTP
> - Client Architecture Patterns
> - Everything About Web Applications

---

## Communication Protocols Summary

| Protocol | Direction | Connection | Latency | Best For |
|---|---|---|---|---|
| **HTTP** (REST) | Request-Response | Short-lived | Medium | CRUD APIs |
| **WebSocket** | Bidirectional | Persistent | Low | Chat, gaming, live updates |
| **SSE** | Server → Client | Persistent (HTTP) | Low | Live feeds, notifications |
| **gRPC** | Bidirectional | Persistent (HTTP/2) | Very Low | Microservices |
| **MQTT** | Pub/Sub | Persistent | Very Low | IoT devices |
| **AMQP** | Producer-Consumer | Persistent | Low | Message queues |

---

## Common Design Patterns in System Design

### Creational Patterns
| Pattern | Purpose |
|---|---|
| **Singleton** | Single instance of a class (config, connection pool) |
| **Factory** | Create objects without specifying exact class |
| **Builder** | Construct complex objects step by step |

### Structural Patterns
| Pattern | Purpose |
|---|---|
| **Proxy** | Control access to an object |
| **Adapter** | Make incompatible interfaces work together |
| **Facade** | Simplified interface to a complex subsystem |

### Behavioral Patterns
| Pattern | Purpose |
|---|---|
| **Observer** | Notify dependents when state changes (pub/sub) |
| **Strategy** | Select algorithm at runtime |
| **Command** | Encapsulate a request as an object |

---

## Architectural Patterns

| Pattern | Description | Use Case |
|---|---|---|
| **Monolith** | Single deployable unit | Small teams, simple apps |
| **Microservices** | Independent services | Large teams, complex systems |
| **Event-Driven** | Services communicate via events | Loosely coupled systems |
| **CQRS** | Separate read/write models | Different read/write patterns |
| **Event Sourcing** | Store events, not state | Audit trails, financial systems |
| **Serverless** | Cloud functions, pay-per-use | Spiky workloads, simple functions |
| **Service Mesh** | Network layer for microservices | Complex microservice networking |

---

## Idempotency

### What is Idempotency?
Making the same request multiple times produces the same result.

### Why It Matters
- Network failures cause **retries**
- Without idempotency, retries can cause **duplicate operations**
- Critical for **payments, orders, state changes**

### How to Implement
```
Client sends: POST /payment { amount: 100, idempotency_key: "abc123" }

Server:
1. Check if "abc123" already processed
2. If yes → return cached response
3. If no → process payment, store result keyed by "abc123"
4. Return response
```

### HTTP Methods and Idempotency
| Method | Idempotent? | Why |
|---|---|---|
| GET | Yes | Reading doesn't change state |
| PUT | Yes | Same update applied = same result |
| DELETE | Yes | Deleting twice = same result |
| POST | No | Each call may create a new resource |
| PATCH | No | Depends on implementation |
