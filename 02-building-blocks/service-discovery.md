---
module: 02-building-blocks
status: unread
tags: [02-building-blocks, system-design, building-blocks]
---
# Service Discovery

> **Mechanism for services to find and communicate with instances of other services in a dynamic environment (e.g. containers, autoscaling).**

---

## File Mindmap

```
Service Discovery
├── Why It Exists
│   ├── Problem → hard-coded IP 10.0.1.45; Kubernetes pod restarts → new IP; service returns 500s
│   └── Forces → containers / autoscaling → IPs are ephemeral; 50 microservices × multiple instances = unmanageable
├── Core Components
│   ├── Service Registry → database of {service name → [IP:port, health]} entries
│   ├── Registration → service registers itself on startup (self-registration) or platform registers (3rd-party)
│   ├── Deregistration → on shutdown (graceful) or TTL expiry (crash) → registry removes stale entry
│   └── Health Checks → registry probes service or service sends heartbeat; stale = removed
├── Discovery Patterns
│   ├── Client-Side Discovery
│   │   ├── Client queries registry → gets instance list → client-side LB (round robin, etc.)
│   │   ├── Examples → Netflix Eureka + Ribbon; Spring Cloud LoadBalancer
│   │   └── Cons → discovery logic in every client; language-specific client libraries needed
│   └── Server-Side Discovery
│       ├── Client sends request to LB; LB queries registry → routes to instance
│       ├── Examples → Kubernetes Service (kube-proxy); AWS ALB + Cloud Map
│       └── Cons → extra hop (LB); LB must be HA
├── Registry Implementations
│   ├── Consul → health checks built-in; DNS + HTTP API; supports KV store; multi-DC
│   ├── etcd → Raft consensus; used by Kubernetes; key-value store; strong consistency
│   ├── Kubernetes → built-in; Service objects + CoreDNS; kube-proxy for L4; Ingress for L7
│   ├── AWS Cloud Map → managed; integrates with ECS/EKS; DNS + API queries
│   └── Netflix Eureka → peer-to-peer; AP (prefers availability over consistency)
├── DNS-Based vs API-Based
│   ├── DNS-based → service-name.namespace.svc.cluster.local → IP list via A/SRV records
│   │   └── Pros → works with any language; built-in caching via DNS TTL
│   └── API-based → query registry HTTP API for instance list
│       └── Pros → richer metadata (version, region, health score); real-time updates
├── Failure Scenarios
│   ├── Registry down → clients use cached instance list; cache timeout = stale routing
│   │   └── Fix → replicate registry (Consul cluster / etcd cluster); clients retry with backoff
│   ├── Thundering herd on registry restart → all services re-register simultaneously
│   │   └── Fix → staggered registration with jitter delay
│   ├── Stale instance in registry → service crashed but registry not updated yet
│   │   └── Fix → aggressive health check intervals + TTL; circuit breaker on client side
│   └── Network partition → Eureka prefers AP (keeps stale); Consul prefers CP (removes unreachable)
├── Trade-offs
│   ├── Pros → no hard-coded IPs; supports autoscaling; enables blue/green and canary
│   └── Cons → registry is new dependency; cache staleness window; added latency
└── Interview Angles
    ├── "Client-side vs server-side discovery?" → client-side: smarter routing but client complexity; server-side: simpler clients but LB is SPOF
    ├── "How does Kubernetes service discovery work?" → DNS (CoreDNS) + Service object + kube-proxy rules
    ├── "What if the registry goes down?" → client caches; replicated registry; circuit breaker
    └── Follow-up: "How do you do zero-downtime deploys with service discovery?" → blue/green + register new, deregister old after health check passes
```

---

## Why Service Discovery Exists

**Question**: Your Order Service needs to call the Payment Service. You hard-code the IP `10.0.1.45:8080` in the config. Three days later, a Kubernetes rolling deployment restarts the Payment Service pod — it comes up with IP `10.0.1.67:8080`. Your Order Service starts returning 500s. You update the config, redeploy, and 2 days later it happens again. At 50 microservices with multiple instances each, constantly changing on autoscale events and deploys, how do you stop playing whack-a-mole with IPs?

**Physical constraint**: Container orchestrators (Kubernetes, ECS) assign ephemeral IPs to each container instance. An IP is valid only for the lifetime of that container — which may be minutes during a rolling deploy. There is no static IP to hard-code. The system must be able to answer "where is the Payment Service right now?" dynamically, and the answer must be consistent across all 10 nodes in your Order Service deployment simultaneously.

**Minimal solution**: Maintain a shared registry: services write their `name → host:port` on startup, delete it on shutdown. Clients query the registry by name at call time. Works until: the registry itself goes down (all services lose the ability to find each other), or a service crashes without deregistering (registry still returns the dead instance until a TTL expires or a health check catches it).

**Production generalization**: A production registry (Consul, etcd, Kubernetes Services + DNS) adds three things to the minimal solution: high availability (clustered registry with quorum), health checking (active probes remove unhealthy instances automatically), and client-side caching (clients cache the instance list with a short TTL so a brief registry outage doesn't immediately break all calls). Kubernetes abstracts this further — a `Service` object is a stable virtual IP backed by dynamic pod IPs, with kube-proxy handling the routing table updates.

---

## The GPS / Google Maps Analogy

When you want to get to a restaurant, you don't memorize its IP address (street address). You search by name, and Maps gives you the current location — even if the restaurant moved last week. If it shut down, Maps shows "permanently closed." Service discovery works the same way: services register their current `host:port` under a name, others look them up by name, and the registry marks unhealthy instances as unavailable.

**Why it exists**: In a container or autoscaling environment, instance IPs change on every deploy or restart. Hard-coded addresses in configs break immediately. Discovery keeps clients and load balancers in sync with the actual, living topology.

---

## 1. Concept Overview

In a distributed system, service instances come and go (deploys, scaling, failures). **Service discovery** lets a client or router find the current set of healthy instances (e.g. `host:port` or DNS names) for a service.

The registry is the Maps database:
- Instances **register** their address on startup (like a new restaurant listing itself).
- Instances **deregister** on shutdown or fail health checks (like Maps removing a closed business).
- Clients or load balancers **query** the registry to get the live list.

---

## 2. Core Principles

### Client-side vs Server-side Discovery

**Client-side** is you personally opening Maps and navigating: the client queries the registry, gets the list of healthy instances, and picks one (round-robin, least-connections, etc.). More control, but every client needs the discovery SDK.

**Server-side** is calling a taxi service and letting the dispatcher navigate: the client calls a fixed LB endpoint, and the LB queries the registry for backends. Simpler client, extra hop.

| Mode | How it works | Pros | Cons |
|------|--------------|------|------|
| **Client-side** | Client queries a registry (e.g. Consul, etcd), gets list of instances, chooses one (e.g. round-robin) | Fewer hops; client can do smart LB | Client complexity; every client needs discovery logic |
| **Server-side** | Client talks to a fixed endpoint (e.g. LB or proxy); LB/proxy uses registry to find backends | Simple client | Extra hop; LB can be bottleneck |

### Registry

- **Registry** holds: service name → list of (host, port, metadata, health).
- **Registration**: Instances register on start and deregister on shutdown; often with TTL and heartbeat.
- **Discovery**: Clients or LBs query the registry (or subscribe to updates) to get the current list.

### Health Checks

Maps doesn't wait for you to arrive and find the door locked — it polls businesses and updates status. Similarly, Consul runs active health checks (HTTP `/health`, TCP ping, or script) against each registered instance. A failed check marks the instance unhealthy and removes it from the return list. This is the equivalent of Maps updating "restaurant permanently closed" in real time.

### Architecture

```
  Service A (client)          Registry (Consul / etcd / Eureka)
        │                              │
        │  "Where is service-b?"       │
        │─────────────────────────────▶│
        │  [host1:8080, host2:8080]    │
        │◀─────────────────────────────│
        │                              │
        │  Request to host1:8080       │
        ▼                              │
  Service B (host1)                    │
        │  (registers on startup)      │
        │─────────────────────────────▶│
```

---

## 3. Real-World Usage

- **Consul**: Service registry, health checks, DNS interface; used in many on-prem and cloud setups.
- **etcd**: Key-value store used by Kubernetes for cluster state; often used as registry.
- **Kubernetes**: Built-in: Services and DNS (e.g. `service-name.namespace.svc.cluster.local`); no separate registry app.
- **AWS**: Cloud Map; ECS/EKS integrations.
- **Eureka**: Netflix OSS; client-side discovery; often used with Spring Cloud.

---

## 4. Trade-offs

| Choice | Pros | Cons |
|--------|------|------|
| **Client-side** | No extra hop; client can do LB and failover | Heavy clients; every language needs SDK |
| **Server-side** | Thin clients; central control | Extra hop; LB/registry critical path |
| **DNS-based** | Universal; simple | TTL lag; less flexible than API registry |
| **API-based registry** | Real-time; rich metadata | Dependency on registry availability |

**When to use**: Microservices or any environment where instance endpoints change (containers, autoscaling).  
**When not**: Single monolith or static, long-lived instances with fixed config.

---

## 5. Failure Scenarios

| Scenario | Mitigation |
|----------|------------|
| Registry down | Cache last known list in clients; tolerate stale; multi-node registry (Consul, etcd cluster) |
| Stale entries | TTL and heartbeats; health checks; deregister on failure |
| Thundering herd | Clients back off when registry is slow; cache and rate-limit discovery calls |
| Split brain | Use CP store (etcd, Consul) with quorum; avoid serving stale data |

When the registry is unavailable, clients fall back to their last cached list — like using an offline Maps cache when you lose signal. Stale is better than nothing, but health checks ensure the list stays fresh when the registry recovers.

---

## 6. Performance Considerations

- **Latency**: Discovery should be fast; cache results with short TTL or use watch/long-poll for updates.
- **Scale**: Registry must handle many services and instances; scale registry (cluster) and limit update rate per service.

---

## 7. Implementation Patterns

### Self-registration with Spring Cloud + Consul (Java)

```java
// application.yml — service registers itself on startup
spring:
  cloud:
    consul:
      host: consul-server
      port: 8500
      discovery:
        service-name: order-service
        health-check-path: /actuator/health
        health-check-interval: 10s

// Client-side lookup with Feign (load-balanced)
@FeignClient(name = "order-service")  // resolves via registry
public interface OrderClient {
    @GetMapping("/orders/{id}")
    Order getOrder(@PathVariable String id);
}
```

The `@FeignClient` with a service name (not a URL) delegates to the discovery client, which asks Consul for the live instance list and applies round-robin load balancing — Maps-on-autopilot.

- **Kubernetes**: Use Service + DNS; optional sidecar or client that uses API for more dynamic behavior.
- **Consul**: Agents on each node; services register; clients use DNS or HTTP API; health checks drive removal.
- **Service mesh**: Sidecar proxies often implement discovery and LB; application stays discovery-agnostic.

---

## Quick Revision

- **Purpose**: Find current, healthy instances of a service in a dynamic environment.
- **Client-side**: Client gets list from registry and chooses instance. **Server-side**: LB/proxy uses registry.
- **Registry**: Registration (with TTL/heartbeat) and discovery (API or DNS); health checks remove bad instances.
- **Failure**: Registry HA; clients cache list; health checks and TTL avoid stale entries.
- **Interview**: "We use Consul for service discovery: instances register on startup and clients query Consul to get the list of healthy instances so we don't rely on static IPs in a scaling environment."
