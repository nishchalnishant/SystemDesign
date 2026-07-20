# How to Prepare for System Design Interviews

> **Source**: [How to Prepare for System Design Interviews w/ Meta Staff Engineer](https://www.youtube.com/watch?v=1HOVtQ-_fcE&list=PL5q3E8eRUieVFeK1oLahJ8KONkAxDpqk2&index=8)

---

## The System Design Interview Framework

A structured approach to tackle any system design problem:

### Step 1: Requirements Clarification (3-5 minutes)
- **Functional requirements**: What features should the system support?
- **Non-functional requirements**: Scale, latency, consistency, availability
- **Scope**: What's in/out of scope?
- **Constraints**: Budget, timeline, tech stack preferences

#### Key Questions to Ask
- Who are the users? How many users?
- What are the main use cases?
- What is the expected scale (reads/writes per second)?
- What are the latency requirements?
- Is consistency or availability more important?
- Are there any geographic constraints?

### Step 2: Back-of-Envelope Estimation (2-3 minutes)
- Number of users (DAU/MAU)
- Read/write ratio
- Storage requirements
- Bandwidth requirements
- QPS (Queries Per Second)

#### Useful Numbers
```
1 day     = 86,400 seconds ≈ 100K seconds
1 million = 10^6
1 billion = 10^9

Throughput:
- QPS for read-heavy: 10K-100K
- QPS for write-heavy: 1K-10K

Storage:
- 1 text message ≈ 100 bytes
- 1 image (compressed) ≈ 200 KB
- 1 video (1 min) ≈ 50 MB
- 1 metadata record ≈ 1 KB
```

### Step 3: High-Level Design (10-15 minutes)
- Draw the main components
- Show data flow between components
- Identify the core services and databases

#### Common Components
```
Clients → Load Balancer → API Gateway → Service Layer
                                          ↓
                                    Cache (Redis)
                                          ↓
                                    Database (SQL/NoSQL)
                                          ↓
                                    Object Storage (S3)
                                          ↓
                                    Message Queue (Kafka)
                                          ↓
                                    CDN (for static content)
```

### Step 4: Deep Dive (15-20 minutes)
- The interviewer will guide which components to explore
- Discuss trade-offs for each decision
- Show knowledge of distributed systems concepts

#### Areas to Deep Dive
- Database schema and choice
- Caching strategy
- Data partitioning / sharding
- Message queue usage
- API design
- Scaling bottlenecks

### Step 5: Wrap-Up (3-5 minutes)
- Identify potential bottlenecks
- Discuss monitoring and alerting
- Mention trade-offs and alternatives
- Scaling strategies for 10x growth

---

## Core Concepts to Master

### Must-Know Topics
1. **Load Balancing** — distributing traffic across servers
2. **Caching** — Redis/Memcached, strategies, invalidation
3. **Database** — SQL vs NoSQL, indexing, replication, sharding
4. **Message Queues** — Kafka, RabbitMQ, async processing
5. **CDN** — content distribution, edge caching
6. **Object Storage** — S3, media handling
7. **API Design** — REST, GraphQL, pagination, rate limiting
8. **Consistent Hashing** — for distributed data partitioning
9. **CAP Theorem** — consistency vs availability trade-offs
10. **Microservices** — service boundaries, communication patterns

### Advanced Topics
- Consensus algorithms (Raft, Paxos)
- Bloom filters
- Merkle trees
- Leader election
- Distributed locking
- Event sourcing / CQRS
- Service mesh

---

## Common System Design Problems

### Easy
| Problem | Key Concepts |
|---|---|
| URL Shortener | Hashing, base62, read-heavy, caching |
| Paste Bin | Object storage, unique IDs, TTL |
| Rate Limiter | Token bucket, sliding window, Redis |

### Medium
| Problem | Key Concepts |
|---|---|
| Twitter/News Feed | Fan-out, caching, timeline generation |
| Instagram | Media storage, CDN, feeds, notifications |
| Chat System | WebSocket, message ordering, delivery guarantees |
| Notification System | Push/pull, priorities, rate limiting |
| Search Autocomplete | Trie, prefix tree, caching |

### Hard
| Problem | Key Concepts |
|---|---|
| YouTube/Netflix | Video transcoding, CDN, adaptive streaming |
| Google Maps | Graph algorithms, tile rendering, routing |
| Distributed File System | Chunking, replication, metadata service |
| Search Engine | Inverted index, crawling, ranking |
| Payment System | ACID, idempotency, exactly-once processing |

---

## Preparation Timeline

### 4 Weeks Plan

#### Week 1: Foundations
- Review distributed systems concepts
- Study CAP theorem, consistency models
- Learn about load balancers, caching, databases

#### Week 2: Building Blocks
- Deep dive into message queues, CDNs, object storage
- Practice API design and data modeling
- Study consistent hashing, sharding strategies

#### Week 3: Practice Problems
- Design 2-3 easy problems end-to-end
- Design 2-3 medium problems end-to-end
- Time yourself (35-45 minutes per problem)

#### Week 4: Mock Interviews
- Practice with peers or mock interview platforms
- Focus on communication and structured approach
- Get feedback and iterate

---

## Communication Tips

### DO
- **Think out loud** — share your reasoning process
- **Draw diagrams** — visual communication is key
- **Discuss trade-offs** — "We could do X, but the trade-off is Y"
- **Ask clarifying questions** — shows maturity
- **Prioritize** — focus on the most important components
- **Acknowledge unknowns** — "I'm not sure about X, but I'd research..."

### DON'T
- Don't jump into details without a high-level design
- Don't ignore the interviewer's hints or redirections
- Don't over-optimize prematurely
- Don't present only one solution without alternatives
- Don't forget about non-functional requirements
- Don't memorize solutions — understand the principles

---

## Red Flags to Avoid

1. **No requirements gathering** — jumping straight to design
2. **Single point of failure** — no redundancy discussion
3. **No scalability discussion** — design doesn't handle growth
4. **Ignoring trade-offs** — presenting solutions as perfect
5. **Over-engineering** — adding unnecessary complexity
6. **Poor communication** — not explaining decisions

---

## Interview Tips

1. **Practice drawing system diagrams** — get comfortable with whiteboarding
2. **Learn to estimate** — practice back-of-envelope calculations
3. **Build a component toolkit** — know when to use each building block
4. **Study real-world architectures** — read engineering blogs (Meta, Netflix, Uber, Stripe)
5. **Time management is critical** — don't spend too long on any one section
6. **Drive the conversation** — don't wait for the interviewer to ask; proactively discuss trade-offs
