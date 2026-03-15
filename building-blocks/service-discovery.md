# Service Discovery

> **Mechanism for services to find and communicate with instances of other services in a dynamic environment (e.g. containers, autoscaling).**

---

## 1. Concept Overview

In a distributed system, service instances come and go (deploys, scaling, failures). **Service discovery** lets a client or router find the current set of healthy instances (e.g. host:port or DNS names) for a service.

**Why it exists**: Hard-coded or static config breaks when instances change. Discovery keeps clients and load balancers in sync with the actual topology.

---

## 2. Core Principles

### Client-side vs server-side

| Mode | How it works | Pros | Cons |
|------|--------------|------|------|
| **Client-side** | Client queries a registry (e.g. Consul, etcd), gets list of instances, chooses one (e.g. round-robin) | Fewer hops; client can do smart LB | Client complexity; every client needs discovery logic |
| **Server-side** | Client talks to a fixed endpoint (e.g. LB or proxy); LB/proxy uses registry to find backends | Simple client | Extra hop; LB can be bottleneck |

### Registry

- **Registry** holds: service name → list of (host, port, metadata, health).
- **Registration**: Instances register on start and deregister on shutdown; often with TTL and heartbeat.
- **Discovery**: Clients or LBs query the registry (or subscribe to updates) to get the current list.

### Architecture

```
  Service A (client)          Registry (Consul / etcd / Eureka)
        │                              │
        │  "Where is service-b?"       │
        │─────────────────────────────▶│
        │  [host1:8080, host2:8080]    │
        │◀─────────────────────────────│
        │                              │
        │  Request to host1:8080      │
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

---

## 6. Performance Considerations

- **Latency**: Discovery should be fast; cache results with short TTL or use watch/long-poll for updates.
- **Scale**: Registry must handle many services and instances; scale registry (cluster) and limit update rate per service.

---

## 7. Implementation Patterns

- **Kubernetes**: Use Service + DNS; optional sidecar or client that uses API for more dynamic behavior.
- **Consul**: Agents on each node; services register; clients use DNS or HTTP API; health checks drive removal.
- **Service mesh**: Sidecar proxies often implement discovery and LB; application stays discovery-agnostic.

---

## Quick Revision

- **Purpose**: Find current, healthy instances of a service in a dynamic environment.
- **Client-side**: Client gets list from registry and chooses instance. **Server-side**: LB/proxy uses registry.
- **Registry**: Registration (with TTL/heartbeat) and discovery (API or DNS); health checks remove bad instances.
- **Failure**: Registry HA; clients cache list; health checks and TTL avoid stale entries.
- **Interview**: “We use Consul for service discovery: instances register on startup and clients query Consul to get the list of healthy instances so we don’t rely on static IPs in a scaling environment.”
