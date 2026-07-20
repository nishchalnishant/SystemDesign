# Distributed System Patterns

> **Source**: [Top 7 Most-Used Distributed System Patterns](https://www.youtube.com/playlist?list=PLCRMIe5FDPsd0gVs500xeOewfySTsmEjf) — Video #26

---

## 1. Ambassador Pattern
- Proxy that handles cross-cutting concerns for a service
- Handles: retries, circuit breaking, logging, monitoring
- Like a sidecar but for network communication
- **Used by**: Envoy proxy, service meshes

## 2. Circuit Breaker Pattern
- Prevents cascading failures in distributed systems
- **States**: Closed (normal) → Open (failing, reject calls) → Half-Open (test recovery)
```
Closed:    Requests flow normally, failures counted
           If failures exceed threshold → Open
Open:      All requests immediately rejected
           After timeout → Half-Open
Half-Open: Allow limited requests to test
           If success → Closed
           If failure → Open
```
- **Tools**: Hystrix (Netflix), Resilience4j

## 3. CQRS (Command Query Responsibility Segregation)
- Separate read and write models
```
Write (Command) → Write DB (optimized for writes)
Read  (Query)   → Read DB  (optimized for reads, denormalized)
                   Sync via events
```
- **Benefits**: Optimize read and write independently, different scaling
- **Use when**: Read/write patterns are very different

## 4. Event Sourcing
- Store state changes as a sequence of events (not current state)
```
Events: [UserCreated, NameChanged, EmailVerified, PasswordChanged]
Current state = replay all events
```
- **Benefits**: Complete audit trail, time travel, event replay
- **Challenges**: Event schema evolution, eventual consistency

## 5. Leader Election
- One node is elected "leader" to coordinate work
- Others are followers/replicas
- **Algorithms**: Raft, Paxos, ZooKeeper
- **Use when**: Need single coordinator (e.g., DB primary, scheduler)

## 6. Saga Pattern
- Manage distributed transactions across microservices
- Each step has a compensating action for rollback
```
Order Service → Payment Service → Inventory Service
    ↓ (fail)        ↓ (fail)
Compensate:     Refund payment    Restore inventory
```
- **Choreography**: Services communicate via events
- **Orchestration**: Central coordinator manages the saga

## 7. Strangler Fig Pattern
- Gradually replace legacy system with new system
```
1. Route all traffic to old system
2. Build new feature in new system
3. Route that feature's traffic to new system
4. Repeat until old system is fully replaced
```
- **Named after**: Strangler fig tree that grows around and replaces host tree
