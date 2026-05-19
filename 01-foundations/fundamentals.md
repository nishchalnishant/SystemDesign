# System Design: The Mental Model

This is the entry point. Read this first, then follow the study path at the bottom to go deep on each topic.

---

## What Is System Design?

**Question**: You've written an app that works perfectly for 100 users. Now you need it to work for 10 million. You can't rewrite it from scratch — you're in production. What breaks first, and how do you know what to fix before it breaks?

**Physical constraint**: Every component in a computer system has a hard ceiling: a single CPU core executes ~3 billion cycles per second, a single disk can sustain ~100–200 MB/s sequential throughput (or ~100–200 IOPS random), a single machine has at most a few hundred GB of RAM, and a single network card is bounded by bandwidth and the speed of light. These are not engineering failures — they are physics. When your user count grows, you will eventually exceed one of these ceilings, and the system will fail in a way that is invisible until it isn't.

**Minimal solution**: Put everything on one big machine. Vertical scaling (faster CPU, more RAM, bigger disk) works until you hit the hardware ceiling — which is a real, finite number. At some point, no single machine can be purchased that handles the load.

**Production generalization**: System design is the discipline of deciding, before you hit those ceilings, which components to distribute across multiple machines, how they communicate, what data each owns, and what guarantees each provides. The decisions made early — how data is partitioned, whether the architecture is monolithic or service-oriented, what consistency guarantees the database provides — are extraordinarily expensive to undo later. Getting these right, or at least understanding the tradeoffs you're accepting, is the whole game.

---

## How Every System Is Evaluated

Every production system lives and dies on four axes. When you're designing a system — in an interview or on the job — these are the four questions that drive every architectural decision.

### Scalability

**Question**: Your service handles 1,000 requests/sec today. Your marketing team announces a campaign that will drive 10,000 requests/sec tomorrow. You have 12 hours. What do you do?

**Physical constraint**: A single application server process can handle roughly 100–1,000 requests/sec depending on work per request. Beyond that, the CPU is saturated, or the thread pool is exhausted, or the database connection pool is full. Adding more work to the same machine doesn't help — it just adds queuing latency.

**Minimal solution**: Add a second server behind a load balancer. This doubles capacity. It works until: the database behind both servers becomes the bottleneck (one DB, two app servers hammering it), or the servers need shared state (sessions, caches) that doesn't exist on both machines.

**Production generalization**: Horizontal scaling — adding more identical nodes — is the standard answer. Netflix spins up thousands of additional servers within minutes during a traffic spike. The hard part is making the application stateless (so any server can handle any request) and making the data layer scale independently (read replicas, sharding, caches). Scalability means capacity grows in proportion to resources added — both compute and data tier.

### Availability

**Question**: Your primary database server crashes at 2am. How long until users notice, and how long until service is restored? If those numbers are hours, is that acceptable?

**Physical constraint**: Hardware fails. Hard drives fail at ~1% per year; a cluster of 1,000 disks loses one per month. Network switches fail. Power supplies fail. Even well-maintained cloud VMs are restarted for host maintenance. Any single machine that your system depends on will eventually be unavailable.

**Minimal solution**: Deploy one server. When it fails, restart it. Mean time to recovery (MTTR) is the time to detect + diagnose + restart, typically 5–30 minutes. For most consumer-facing systems, 30 minutes of monthly downtime (99.93% uptime) is below acceptable.

**Production generalization**: Eliminate single points of failure. Replicate critical components (primary + standby DB, multiple app servers, redundant load balancers). Deploy across multiple availability zones so one datacenter failure doesn't take down the system. Build health checks and automated failover so recovery is seconds, not minutes. Availability is measured in "nines": 99.9% = 8.76 hours downtime/year; 99.99% = 52 minutes/year. Each nine is roughly 10× harder to achieve than the previous.

### Consistency

**Question**: A user updates their profile picture. Another user, in a different region, loads that profile 200 milliseconds later. Should they see the new picture or the old one? Does your answer change if this is a bank balance instead of a profile picture?

**Physical constraint**: Network round-trip between datacenters is ~50–150ms. Writing to a database in us-east and reading from us-west within 50ms means the read happens before the write has had time to propagate. There is no way to propagate data faster than the speed of light across a continent.

**Minimal solution**: Route all reads and writes to one node in one region. Every read sees the latest write because there's only one copy. This works until: that region goes down (availability fails), or the latency from far-away users is unacceptable (performance fails).

**Production generalization**: Consistency is a spectrum, not a binary. Strong consistency (every read reflects the latest write) requires coordination, which costs latency. Eventual consistency (reads may be stale briefly) allows replicas to operate independently, which improves availability and latency. The right choice depends on domain: bank balances require strong consistency; social media like-counts tolerate eventual. This is the heart of the CAP theorem. → Deep dive: [Distributed Systems](../04-advanced-topics/distributed-systems.md)

### Performance (Latency & Throughput)

**Question**: Your API returns in 50ms at P50, but in 2,000ms at P99. Half your users have a fine experience. One in a hundred users gets a two-second wait. Which number do you optimize, and why does the P99 exist at all?

**Physical constraint**: Latency has irreducible floors: a disk seek takes ~4ms, a network round-trip within a datacenter takes ~0.5ms, a cross-continent round-trip takes ~150ms. Any operation that touches disk or crosses a network adds these floors to your response time. Ten sequential database queries each taking 5ms = 50ms of unavoidable latency.

**Minimal solution**: Run every operation synchronously and sequentially. Latency = sum of all operation times. This is simple and correct. It breaks when any one operation is slow (a slow DB query bloats every response), and when you need high throughput (sequential processing caps at 1 request / total latency).

**Production generalization**: Parallelism (execute independent operations concurrently) and caching (skip expensive operations entirely for repeat reads) are the two primary tools. Google found that every 100ms of latency costs 1% of revenue. Amazon found that 1 second of slowness reduced sales by 7%. Latency is how long a single request takes; throughput is how many requests per second the system can sustain. They often trade off: batching improves throughput but increases individual latency.

---

## The Core Building Blocks

Every large-scale system is assembled from the same fundamental components. You don't need to reinvent them — you need to know when to reach for each one and what you're accepting when you do.

### 1. Networking & Protocols

**Question**: Service A calls Service B. The call times out. Is the problem in A's code, B's code, the network between them, DNS resolution, TLS negotiation, or B's database? You have to fix it in production in the next 10 minutes. How do you even start?

**Physical constraint**: Every network call crosses multiple layers: application serialization, OS TCP stack, NIC, network switches, the remote NIC, remote OS TCP stack, and finally the remote application. A packet can be dropped at any layer. Latency accumulates at each layer. Without knowing which layer introduced the problem, debugging is guesswork.

**Minimal solution**: Use the OSI model as a structured debugging ladder. Start at Layer 7 (application logs): is the service returning errors? Drop to Layer 4 (TCP): is connection establishment slow? Drop to Layer 3 (IP/routing): is there packet loss?

**Production generalization**: TCP/IP handles reliable delivery — every packet is acknowledged and retransmitted if lost. HTTP/REST is the lingua franca of web APIs. gRPC is what Google and Uber use internally when performance matters and you control both ends. DNS translates `google.com` into `142.250.80.46`. Understanding which layer fails tells you which tool to reach for and which team to call.

→ Deep dive: [Networking](../01-foundations/networking.md)

### 2. Databases

**Question**: You need to store 10 billion rows of user activity events. Each write must be fast (you have 500,000 events/second). Reads are always by user ID + time range. You never join this data with other tables. Should you use PostgreSQL or Cassandra? What breaks if you choose wrong?

**Physical constraint**: A relational database enforces consistency across rows and tables by acquiring locks during writes. At high write throughput, those locks create contention. A single PostgreSQL primary can sustain roughly 10,000–50,000 simple writes/second; beyond that, you need to either shard or switch to a write-optimized store.

**Minimal solution**: Use one relational database for everything. Simple, unified, well-understood. Works until write throughput exceeds what one primary can handle, or data volume exceeds what one disk can store.

**Production generalization**: Relational databases (PostgreSQL, MySQL) give you ACID guarantees, joins, and a decades-proven track record. NoSQL databases (Cassandra, DynamoDB, MongoDB) trade query flexibility for horizontal scalability — Cassandra's LSM-tree storage accepts writes at 500,000/second across a cluster because it never updates in-place. The wrong choice here is the hardest architectural mistake to undo. Match the database to the access pattern, not the other way around.

→ Deep dive: [Databases](../01-foundations/databases.md)

### 3. Caching

**Question**: Your database can do 10,000 reads/sec. Your app needs 500,000 reads/sec. You cannot afford 50 database replicas. What do you do?

**Physical constraint**: RAM access is ~100ns. Disk access is ~4ms — 40,000× slower. A database query that reads from disk (even a warm buffer pool read) is orders of magnitude slower than reading from memory. If reads are idempotent and data changes slowly, you're paying 40,000× the cost on every repeat access unnecessarily.

**Minimal solution**: Copy the result into RAM the first time. Return from RAM on all subsequent reads. This works until: data changes (stale reads), RAM fills up (eviction), or the process restarts (cold start).

**Production generalization**: Cache-aside pattern + TTL for staleness control + LRU eviction for memory pressure + warm-up jobs for cold start. Redis is a RAM-backed hash map over a network — the network adds ~0.5ms but makes the cache shared across all app instances. Twitter caches the home timelines of active users entirely in Redis — what would take a complex database query is served from memory in under 1ms. The hard problems are *what* to cache, *when* to invalidate it, and how to handle a cold cache after a restart.

→ Deep dive: [Caching](../01-foundations/caching-cdn.md) | [Caching Layer (Building Block)](../02-building-blocks/caching-layer.md)

### 4. Content Delivery Networks (CDN)

**Question**: You have 10 million users worldwide, all requesting the same 50MB JavaScript bundle every time they visit your site. Your origin server is in Virginia. A user in Tokyo gets ~150ms of latency on every byte. Bandwidth costs alone would bankrupt you. What do you do?

**Physical constraint**: The speed of light means a round-trip from Tokyo to Virginia takes ~150ms regardless of how fast your servers are. This is a physics ceiling, not an engineering problem. You cannot optimize your way below the latency imposed by geographic distance.

**Minimal solution**: Put a copy of static files in a server close to each major user population. A server in Tokyo can respond to Tokyo users in ~10ms. This works until: the copy is stale (the original changed), you have too many distinct pieces of content to store everywhere, or the file is dynamic/user-specific.

**Production generalization**: A CDN is a globally distributed network of caching servers (edge nodes) that serve content from the location closest to the user. On the first request from a region, the edge fetches from your origin and caches the result. All subsequent requests from that region are served locally. Netflix delivers over 15% of global internet traffic almost entirely through CDN — it is economically impossible to serve that from a handful of origin data centers.

→ Deep dive: [CDN](../01-foundations/caching-cdn.md#cdn) | [CDN (Building Block)](../02-building-blocks/cdn.md)

### 5. Load Balancing

**Question**: You have 10 servers, each capable of handling 1,000 requests/sec. That's 10,000 requests/sec total capacity. But every client connects to the same IP address — server 1. Servers 2–10 sit idle. How do you distribute the connections?

**Physical constraint**: A server process has a bounded number of threads and file descriptors. Under TCP, each connection consumes a file descriptor (default OS limit ~65,536). Once saturated, new connections are refused regardless of whether other servers are available. There is no built-in mechanism for clients to self-distribute across a server fleet.

**Minimal solution**: Put one machine in front of all servers that receives all connections and forwards each one to one backend. This is a load balancer. It solves the routing problem. It also becomes a single point of failure — if it dies, all traffic dies.

**Production generalization**: Deploy redundant load balancers with failover. Use health checks to detect and stop routing to failed backends. Choose routing algorithm based on workload: round-robin for stateless services, least-connections for variable-duration requests, consistent hashing when requests for the same key should hit the same backend (e.g., caching affinity). Google's Maglev system handles millions of packets per second with sub-millisecond routing decisions.

→ Deep dive: [Load Balancers](../02-building-blocks/load-balancers.md)

### 6. Message Queues & Async Processing

**Question**: A user uploads a photo. Your system must: resize it to 12 resolutions, extract metadata, run it through a content moderation ML model, and notify followers. That work takes 8 seconds. The user is waiting at a loading spinner. How do you give them a response in under 200ms?

**Physical constraint**: CPU-bound work (image resizing, ML inference) takes real wall-clock time proportional to the complexity of the computation. You cannot make 8 seconds of CPU work complete in 200ms on a single thread — the physics don't allow it. The user's request latency and the total processing latency are decoupled problems that need decoupled solutions.

**Minimal solution**: Accept the upload, store the raw file, return a 202 Accepted immediately. Process everything else asynchronously in the background. The user gets a fast response; the work still happens. This breaks when: the background work fails silently, the background process crashes mid-work, or producers are generating work faster than workers can consume it.

**Production generalization**: Message queues (Kafka, RabbitMQ, SQS) are a durable buffer between producers and consumers. The sender drops a message and moves on; the receiver picks it up when ready. Messages are persisted so a crashed worker can retry. Consumer lag is measurable so you can scale workers when producers outpace them. Kafka handles millions of messages per second with durability guarantees, used by LinkedIn, Uber, and Airbnb.

→ Deep dive: [Message Brokers](../02-building-blocks/message-brokers.md)

### 7. Sharding & Replication

**Question**: Your PostgreSQL primary is at 80% CPU handling read queries, and your dataset is 20TB — too large for one machine's disk. You cannot vertically scale further. You have two distinct problems: too many reads, and too much data. Are these the same problem? Do they have the same solution?

**Physical constraint**: A single disk has a maximum I/O throughput (~500 MB/s SSD sequential). A single CPU can execute a finite number of query threads. A single machine has a maximum disk capacity. All three are hard physical ceilings, and they can be hit independently — a read-heavy workload may hit CPU before disk capacity, while a data-archival workload may hit disk before CPU.

**Minimal solution**: For the read problem: make copies of the data on additional machines (replicas) and direct reads to them. This is replication. For the data volume problem: put different partitions of the data on different machines. This is sharding. These are distinct solutions to distinct constraints.

**Production generalization**: Replication copies data to multiple servers — read replicas spread read load (how Instagram handled 1B users on Postgres). Sharding splits data across servers so each owns a subset — Uber shards trip data by city, so Sydney's database doesn't carry New York's rows. The two are complementary: you replicate each shard for availability, and you shard for capacity. Together they form the backbone of every distributed storage system.

→ Deep dive: [Sharding](../02-building-blocks/sharding.md) | [Replication](../02-building-blocks/replication.md)

### 8. Security

**Question**: An attacker sends your login endpoint 1 million requests per second, each with a different username/password combination harvested from a leaked credentials database. Your server happily processes each one. Within minutes, they've accessed thousands of real user accounts. Your code has no bugs. What went wrong?

**Physical constraint**: A server cannot distinguish a malicious request from a legitimate one at the packet level — both are valid TCP connections with valid HTTP bodies. Without application-layer controls, every request is treated identically regardless of intent or volume. The application layer is the only place where business context (is this request pattern anomalous?) can be evaluated.

**Minimal solution**: Accept every request and authenticate it against the database. This is correct for one user. At scale with an attacker, it exhausts DB connections, leaks account existence via timing differences, and provides no circuit breaker against credential stuffing.

**Production generalization**: Authentication (who are you? — OAuth 2.0, JWTs) and authorization (what are you allowed to do? — RBAC, ACLs) are necessary but not sufficient. Rate limiting, IP blocking, CAPTCHA, and anomaly detection are the controls that make authentication resistant to automated attack. Encryption (TLS in transit, AES-256 at rest) ensures that breaching the network or disk doesn't expose data. The threat model for a payment system at Stripe is completely different from a social media platform, but both have non-negotiable baseline requirements.

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
