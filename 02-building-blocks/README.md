# System Design Building Blocks

> **Fundamental components used in almost every large-scale system.**  
> Each building block is a standalone deep dive with architecture, trade-offs, and real-world examples.

## Index

| Building Block | Description | When to Use |
|----------------|-------------|-------------|
| [Load Balancers](load-balancers.md) | Distribute traffic across multiple servers | Multiple app instances, high availability |
| [Reverse Proxy](reverse-proxy.md) | Single entry point; SSL termination, routing, caching | Fronting app servers, API gateway |
| [CDN](cdn.md) | Edge caching for static/dynamic content | Low-latency global delivery |
| [Caching Layer](caching-layer.md) | In-memory cache (e.g. Redis) for hot data | Read-heavy, latency-sensitive |
| [Message Brokers](message-brokers.md) | Queues and pub/sub for async processing | Decoupling, async jobs, events |
| [Service Discovery](service-discovery.md) | Find service instances in a dynamic cluster | Microservices, containers |
| [API Gateway](api-gateway.md) | Single entry for APIs; auth, rate limit, routing | Microservices, B2B APIs |
| [Distributed Locks](distributed-locks.md) | Coordinate exclusive access across nodes | Leader election, critical sections |
| [Rate Limiting](rate-limiting.md) | Limit requests per user/IP/key | Abuse prevention, fairness, cost control |
| [Sharding](sharding.md) | Partition data across multiple databases | Write scale, storage scale |
| [Replication](replication.md) | Copy data across nodes for availability and read scale | HA, read scaling |
| [Bloom Filter](bloom-filter.md) | Probabilistic set membership; space-efficient dedup | Web crawlers, cache miss reduction, dedup at scale |
| [Architecture Composition](architecture-composition.md) | Patterns for combining building blocks into coherent systems | System design synthesis, component interaction patterns |

## How to Use

- **Interview prep**: Read each doc for concept overview, trade-offs, and real-world examples.
- **Quick revision**: Use the "Quick Revision" section at the end of each doc.
- **Design discussions**: Reference "When to use / When not to use" and "Failure scenarios."

## Related

- **Core concepts**: [01-foundations/](../01-foundations/) — Databases, caching, networking.
- **Advanced**: [04-advanced-topics/](../04-advanced-topics/) — Distributed systems, consistency, transactions.
- **Case studies**: [hld-problems/](../05-hld-problems/) — URL shortener, cache, messaging, etc.
