# Scaling, Vertical vs Horizontal & System Design Concepts

> **Source**: Videos #50, #79, #85, #90, #92, #93, #96, #97, #98 from the playlist
> - Vertical Vs Horizontal Scaling
> - Scalability Simply Explained in 10 Minutes
> - 8 Most Important System Design Concepts
> - 8 Most Important Tips for Designing Fault-Tolerant Systems
> - System Design Was HARD - Until You Knew the Trade-Offs (Part 1 & 2)
> - 7 System Design Concepts Explained in 10 Minutes
> - 20 System Design Concepts You Must Know - Final Part
> - System Design Interview – BIGGEST Mistakes to Avoid

---

## Vertical vs Horizontal Scaling

| Feature | Vertical Scaling (Scale Up) | Horizontal Scaling (Scale Out) |
|---|---|---|
| **How** | Bigger machine (CPU, RAM, disk) | More machines |
| **Cost** | Expensive, diminishing returns | Cheaper commodity hardware |
| **Limit** | Hardware ceiling | Virtually unlimited |
| **Complexity** | Simple | Requires distributed architecture |
| **Downtime** | Often required for upgrade | No downtime |
| **Single Point of Failure** | Yes | No (with redundancy) |

---

## 8 Core System Design Concepts

### 1. Load Balancing
- Distribute traffic across multiple servers
- Algorithms: Round Robin, Least Connections, IP Hash

### 2. Caching
- Store frequently accessed data in fast storage
- Layers: Client, CDN, Application, Database

### 3. Database Sharding
- Split data across multiple database instances
- Key decision: choosing the shard key

### 4. Replication
- Copy data across multiple nodes
- Primary-replica for read scaling + fault tolerance

### 5. Message Queues
- Async communication between services
- Decouple producers from consumers

### 6. Rate Limiting
- Control request frequency per client
- Protect against abuse and overload

### 7. Consistent Hashing
- Distribute data across nodes with minimal redistribution
- Used in caches, databases, CDNs

### 8. CAP Theorem
- Choose 2 of 3: Consistency, Availability, Partition Tolerance

---

## Designing Fault-Tolerant Systems

### 8 Key Tips

1. **Redundancy**: No single point of failure (multiple replicas, AZs, regions)
2. **Health Checks**: Detect failures quickly (active + passive)
3. **Circuit Breakers**: Stop cascading failures
4. **Retries with Exponential Backoff**: Handle transient failures
5. **Timeouts**: Don't wait forever for a response
6. **Graceful Degradation**: Serve partial functionality when subsystems fail
7. **Chaos Engineering**: Test failure handling proactively (Netflix Chaos Monkey)
8. **Monitoring & Alerting**: Detect issues before users notice

---

## System Design Trade-Offs

Every design decision involves trade-offs:

| Trade-Off | Option A | Option B |
|---|---|---|
| Consistency vs Availability | Strong consistency (CP) | High availability (AP) |
| Latency vs Throughput | Optimize for speed | Optimize for volume |
| SQL vs NoSQL | ACID, joins, schema | Flexible, scalable, fast |
| Monolith vs Microservices | Simple, fast to start | Scalable, independent teams |
| Read vs Write optimization | Denormalize (fast reads) | Normalize (fast writes) |
| Push vs Pull | Real-time, more resources | On-demand, simpler |
| Batch vs Stream processing | Higher throughput | Lower latency |
| Memory vs Disk | Fast, expensive, volatile | Slow, cheap, persistent |

---

## System Design Interview Mistakes to Avoid

1. ❌ **Jumping to solution** without understanding requirements
2. ❌ **Not asking clarifying questions** — scope matters
3. ❌ **Over-engineering** — designing for Google's scale on day 1
4. ❌ **Under-engineering** — ignoring scalability entirely
5. ❌ **Not discussing trade-offs** — everything has pros and cons
6. ❌ **Ignoring non-functional requirements** — latency, availability, consistency
7. ❌ **Monologuing** — not engaging with the interviewer
8. ❌ **No back-of-envelope math** — show you can estimate
9. ❌ **Forgetting about failure cases** — what happens when X goes down?
10. ❌ **Not having a structured approach** — use a framework

---

## 20 System Design Concepts Summary

| # | Concept | One-liner |
|---|---|---|
| 1 | Load Balancer | Distribute traffic |
| 2 | CDN | Cache at edge |
| 3 | Cache | Fast data access |
| 4 | Database | Persistent storage |
| 5 | Replication | Copy data for availability |
| 6 | Sharding | Split data for scale |
| 7 | Message Queue | Async communication |
| 8 | API Gateway | Single entry point |
| 9 | Rate Limiter | Control request rate |
| 10 | Consistent Hashing | Minimize data movement |
| 11 | DNS | Domain → IP resolution |
| 12 | Proxy/Reverse Proxy | Intermediary for requests |
| 13 | Logging | Record system events |
| 14 | Monitoring | Observe system health |
| 15 | Alerting | Notify on issues |
| 16 | Idempotency | Same request = same result |
| 17 | Heartbeat | Detect node liveness |
| 18 | Checksum | Verify data integrity |
| 19 | Bloom Filter | Probabilistic set membership |
| 20 | Consensus | Agree on values (Raft/Paxos) |
