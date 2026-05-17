# System Design: The Mental Model

This is the entry point. Read this first, then follow the study path at the bottom to go deep on each topic.

---

## What Is System Design?

System design is the process of deciding *how* to build something before you build it — choosing the right components, the right data stores, the right communication patterns, and the right tradeoffs for the problem you're solving. It is architecture before code.

Think of it like planning a city. You wouldn't just start laying roads randomly — you'd decide where the highways go, where the power grid connects, how water is distributed, and how emergency services reach any neighborhood within minutes. Every decision has cascading consequences. Build the highway in the wrong place and you've created a bottleneck that the city will live with for decades. System design is that same planning exercise, but for software.

Why does it matter in practice? Because Facebook, Google, and Uber didn't get to planetary scale by accident. The decisions made in the first 18 months of a system's life — how data is partitioned, whether the architecture is monolithic or service-oriented, what consistency guarantees the database provides — are extraordinarily expensive to undo. Getting these right, or at least understanding the tradeoffs you're accepting, is the whole game.

---

## How Every System Is Evaluated

Every production system lives and dies on four axes. When you're designing a system — in an interview or on the job — these are the four questions that drive every architectural decision.

### Scalability

Can the system handle 10× the current load? Scalability means the system's capacity grows in proportion to resources added. Think of a highway: you can add more lanes (horizontal scaling), or upgrade single lanes to move faster (vertical scaling). Netflix scales horizontally — when traffic spikes during a new show release, it spins up thousands of additional servers within minutes rather than hoping one giant machine can absorb the load.

### Availability

Is the system up when users need it? Availability is measured in "nines" — 99.9% means ~8.76 hours of downtime per year; 99.99% means ~52 minutes. Think of a hospital: even at 3am, the lights stay on and the ER is staffed. Achieving high availability means eliminating single points of failure, deploying across multiple regions, and building systems that degrade gracefully rather than crashing completely.

### Consistency

Do all users see the same data at the same time? A bank's ATM network must be strongly consistent — you can't withdraw money twice from two ATMs simultaneously. A social media like-count, on the other hand, can tolerate being slightly stale (eventually consistent). The tradeoff here is fundamental: stronger consistency requires coordination between nodes, which costs latency. This is the heart of the CAP theorem. → Deep dive: [Distributed Systems](../04-advanced-topics/distributed-systems.md)

### Performance (Latency & Throughput)

How fast does the system respond, and how much work can it do per second? Google found that every 100ms of latency costs them 1% of revenue. Amazon found that 1 second of slowness reduced sales by 7%. Latency is how long a single request takes; throughput is how many requests per second the system can sustain. These often trade off against each other — batching improves throughput but hurts individual latency.

---

## The Core Building Blocks

Every large-scale system is assembled from the same fundamental components. You don't need to reinvent them — you need to know when to reach for each one and what you're accepting when you do.

### 1. Networking & Protocols

Before any two services can communicate, they need a shared language. TCP/IP handles reliable delivery (think certified mail — every packet is acknowledged). HTTP/REST is the lingua franca of web APIs. gRPC is what Google and Uber use internally when performance matters and you control both ends. DNS is the phone book — it translates `google.com` into `142.250.80.46`. Understanding the OSI model tells you *where* in the stack a failure or bottleneck is occurring.

→ Deep dive: [Networking](../01-foundations/networking.md)

### 2. Databases

Your database is where state lives. Relational databases (PostgreSQL, MySQL) give you ACID guarantees, joins, and a decades-proven track record — Instagram's 2 billion users were served by PostgreSQL for years. NoSQL databases (Cassandra, DynamoDB, MongoDB) trade some query flexibility for horizontal scalability and schema flexibility — Uber uses Cassandra because it scales writes across data centers effortlessly. The wrong choice here is the hardest architectural mistake to undo.

→ Deep dive: [Databases](../01-foundations/databases.md)

### 3. Caching

Caching is the simplest performance multiplier in system design. It's the coffee shop putting the most popular drinks on the counter rather than fetching everything from the back room. Redis sitting in front of a database can absorb 100,000 reads per second that would have hammered your DB. Twitter caches the home timelines of active users entirely in Redis — what would take a complex database query is served from memory in under 1ms. The hard problems are *what* to cache, *when* to invalidate it, and how to handle a cold cache after a restart.

→ Deep dive: [Caching](../01-foundations/caching-cdn.md) | [Caching Layer (Building Block)](../02-building-blocks/caching-layer.md)

### 4. Content Delivery Networks (CDN)

A CDN is a network of servers distributed globally that cache static content (images, videos, JS files) close to users. Think of it as regional warehouses for Amazon: instead of shipping everything from one central facility in Ohio, they pre-position popular products near major cities so delivery is fast. Without a CDN, a user in Singapore requesting a Netflix video stored in Virginia would experience 150ms of network latency on every byte. With a CDN, they hit a server 20ms away. Netflix delivers over 15% of global internet traffic, almost entirely through CDN.

→ Deep dive: [CDN](../01-foundations/caching-cdn.md#cdn) | [CDN (Building Block)](../02-building-blocks/cdn.md)

### 5. Load Balancing

A load balancer is the traffic cop at the entrance to your datacenter. When 10,000 requests per second arrive at your service, the load balancer distributes them across your fleet of servers so no single machine is overwhelmed. Without one, you have a single point of failure and a scaling ceiling. Google's load balancing infrastructure is one of the most sophisticated in the world — their Maglev system handles millions of packets per second with sub-millisecond decisions. The interesting design choices are the routing algorithm (round-robin vs. least-connections vs. consistent hashing) and how health checks work.

→ Deep dive: [Load Balancers](../02-building-blocks/load-balancers.md)

### 6. Message Queues & Async Processing

Some work doesn't need to happen right now. When you post a photo on Instagram, you don't want to wait while the system resizes it to 12 different resolutions before showing you a success screen. Instead, the photo is put on a queue, and background workers process it asynchronously. Message queues (Kafka, RabbitMQ, SQS) are the postal service of distributed systems — the sender drops a message and moves on; the receiver picks it up when ready. Kafka, used by LinkedIn, Uber, and Airbnb, can handle millions of messages per second with durability guarantees.

→ Deep dive: [Message Brokers](../02-building-blocks/message-brokers.md)

### 7. Sharding & Replication

When a single database server can no longer hold all your data or handle all your reads, you scale out. Replication means copying data to multiple servers — reads can be spread across replicas (read replicas are how Instagram handled 1B users on Postgres). Sharding means splitting data across multiple servers so each server owns a subset — Uber shards its trip data by city, so the Sydney database doesn't need to know about New York trips. These two patterns are complementary and together form the backbone of every distributed storage system.

→ Deep dive: [Sharding](../02-building-blocks/sharding.md) | [Replication](../02-building-blocks/replication.md)

### 8. Security

Security isn't a feature you add at the end. Authentication answers "who are you?" — OAuth 2.0 and JWTs are the dominant patterns. Authorization answers "what are you allowed to do?" — RBAC and ACLs enforce it. Encryption protects data in transit (TLS) and at rest (AES-256). Rate limiting prevents abuse and DDoS attacks. The threat model for a payment system at Stripe is completely different from a social media platform, but both have non-negotiable baseline requirements.

→ Deep dive: [Security](../01-foundations/security.md)

---

## The SDE-3 Differentiators

Junior candidates know the building blocks. Senior candidates understand the hard problems that emerge when those building blocks interact at scale. These topics separate L5/SDE-3 candidates from the rest.

- **Consistency models & distributed systems** — Strong vs. eventual consistency, linearizability, the CAP theorem, consensus algorithms (Raft, Paxos). → [Distributed Systems](../04-advanced-topics/distributed-systems.md) | [Distributed Concepts](../04-advanced-topics/distributed-concepts.md)
- **Event-driven architecture & CQRS** — Decoupling services via events, separating read and write models, event sourcing. → [Event-Driven Architecture](../04-advanced-topics/event-driven-architecture.md)
- **Observability & tracing** — You can't fix what you can't see. Metrics, logs, distributed traces, and how to correlate them across a microservices mesh. → [Observability](../04-advanced-topics/observability.md)
- **Chaos engineering** — Netflix's practice of deliberately breaking production to find failure modes before users do. → [Chaos Engineering](../04-advanced-topics/chaos-engineering.md)
- **Idempotency & failure recovery** — What happens when a payment request is retried? How do you design operations that are safe to run twice? → [Distributed Concepts](../04-advanced-topics/distributed-concepts.md)

---

## Recommended Reading Order

Follow this path for a systematic understanding that builds on itself. Each stage depends on the previous one.

**Stage 1 — Foundations** (read first, everything else builds on these)
1. [Networking](../01-foundations/networking.md) — TCP/IP, DNS, HTTP, OSI model
2. [Databases](../01-foundations/databases.md) — SQL vs NoSQL, indexing, ACID, CAP theorem
3. [Caching & CDN](../01-foundations/caching-cdn.md) — Strategies, eviction policies, CDN patterns
4. [Security](../01-foundations/security.md) — Auth, encryption, rate limiting

**Stage 2 — Building Blocks** (the components every system uses)
5. [Load Balancers](../02-building-blocks/load-balancers.md)
6. [Message Brokers](../02-building-blocks/message-brokers.md)
7. [Sharding](../02-building-blocks/sharding.md)
8. [Replication](../02-building-blocks/replication.md)
9. [Caching Layer](../02-building-blocks/caching-layer.md)
10. [CDN](../02-building-blocks/cdn.md)
11. [API Gateway](../02-building-blocks/api-gateway.md)
12. [Rate Limiting](../02-building-blocks/rate-limiting.md)

**Stage 3 — HLD Problems** (apply what you've learned)
13. Easy: [URL Shortener](../05-hld-problems/01-easy/url-shortener.md), [Rate Limiter](../05-hld-problems/01-easy/rate-limiter.md), [Key-Value Store](../05-hld-problems/01-easy/key-value-store.md)
14. Medium: [Instagram](../05-hld-problems/02-medium/instagram.md), [YouTube](../05-hld-problems/02-medium/youtube.md), [WhatsApp](../05-hld-problems/02-medium/whatsapp.md)
15. Hard: [Distributed Cache](../05-hld-problems/03-hard/distributed-cache.md), [Payment System](../05-hld-problems/03-hard/payment-system.md), [Search System](../05-hld-problems/03-hard/search-system.md)

**Stage 4 — Advanced Topics** (for senior roles)
16. [Distributed Systems](../04-advanced-topics/distributed-systems.md)
17. [Event-Driven Architecture](../04-advanced-topics/event-driven-architecture.md)
18. [Observability](../04-advanced-topics/observability.md)
19. [Chaos Engineering](../04-advanced-topics/chaos-engineering.md)

**Stage 5 — Interview Prep**
20. [HLD Template](../07-interview-templates/hld-template.md)
21. [Capacity Estimation](../07-interview-templates/capacity-estimation.md)
22. [Trade-offs Cheat Sheet](../07-interview-templates/trade-offs-cheat-sheet.md)

---

## Quick Reference: Numbers Every Engineer Should Know

Memorize these. They come up in every capacity estimation, and rattling them off confidently signals experience.

### Latency Hierarchy

| Operation | Latency |
|-----------|---------|
| L1 cache reference | 0.5 ns |
| L2 cache reference | 7 ns |
| RAM reference | 100 ns |
| SSD read | ~1 ms |
| HDD seek | ~10 ms |
| Same-datacenter round trip | 0.5 ms |
| Cross-region (CA ↔ Europe) | ~150 ms |

**Human-scale analogy:** If L1 cache = 1 second, then RAM = 3 minutes, SSD = 23 days, HDD = 7.5 months, and a cross-continent network hop = 9.5 years.

### Availability (The "Nines")

| SLA | Downtime/Year | Downtime/Month |
|-----|---------------|----------------|
| 99% (2 nines) | 3.65 days | 7.2 hours |
| 99.9% (3 nines) | 8.76 hours | 43.8 minutes |
| 99.99% (4 nines) | 52.6 minutes | 4.38 minutes |
| 99.999% (5 nines) | 5.26 minutes | 26.3 seconds |

### QPS Conversion

```
1 day ≈ 100,000 seconds

Quick formula:  QPS ≈ Daily Requests ÷ 100,000

1M requests/day  ≈  10 QPS
10M requests/day ≈  100 QPS
1B requests/day  ≈  10,000 QPS

Peak QPS ≈ Average QPS × 3
```

### Scale of Major Systems

| Scale | DAU | QPS (avg) | Storage |
|-------|-----|-----------|---------|
| Early startup | 1K–10K | 1–10 | 10–100 GB |
| Growing startup | 10K–1M | 10–1K | 100 GB–10 TB |
| Large platform | 1M–100M | 1K–100K | 10 TB–1 PB |
| Hyperscale (Google/Meta) | 100M+ | 100K–1M+ | 1 PB+ |

→ Full reference with storage sizes, data object sizes, and cost estimates: [Numbers to Know](../08-reference/numbers-to-know.md)

---

## Interview Questions Asked

### Conceptual
1. **"Explain CAP theorem with a real example"** → CAP says you can't have consistency + availability + partition tolerance simultaneously. During a network split, you must choose: Cassandra picks AP (returns stale data), HBase picks CP (rejects writes). Testing whether you understand the trade-off, not just the acronym.
2. **"What is eventual consistency — give a real-world example"** → All replicas converge to the same value given no new writes. Example: DNS propagation — a record update takes minutes to reach all resolvers. Testing: do you know what "eventual" means operationally and when it's acceptable.
3. **"What is PACELC and how is it better than CAP?"** → PACELC adds: even without partition (normal operation), you must choose latency vs consistency. CAP only covers the partition case. DynamoDB is PA/EL — available during partition, low-latency in normal ops. Testing whether you know real systems operate under PACELC constraints daily.
4. **"What latency should you target for P99 in a user-facing API?"** → Under 200ms P99 for interactive APIs; under 1s for complex queries. P50 often looks fine while P99 is broken — testing whether you think in percentiles, not averages.

### Comparison / Trade-off
1. **"What is the difference between consistency and availability in a distributed system?"** → Consistency = every read reflects the latest write (or errors). Availability = every request gets a response (possibly stale). You can tune one at the cost of the other via quorum sizes (W, R, N).
2. **"When would you choose AP over CP?"** → When stale reads are acceptable and downtime is worse than inconsistency — shopping cart, DNS, social feeds. Choose CP for financial ledgers, inventory counts, or any system where stale reads cause incorrect decisions.

### Scenario / Design
1. **"How do you design a system that needs both strong consistency AND high availability?"** → You can't have both during a partition (CAP). In practice: synchronous replication within a region for consistency + cross-region async replication for availability; route reads to leader for freshness; use quorum writes (W + R > N). Accept that during a partition you must pick one.
2. **"What is a split-brain scenario and how do you prevent it?"** → Two nodes both believe they are the leader and accept conflicting writes. Prevent with: Raft/Paxos leader election (only one can get majority), fencing tokens (monotonically increasing token; stale leader's writes are rejected), odd number of nodes so a majority is always possible.

→ Full reference with storage sizes, data object sizes, and cost estimates: [Numbers to Know](../08-reference/numbers-to-know.md)
