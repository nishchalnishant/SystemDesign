# System Design - SDE-3 Interview Preparation Guide

> **Comprehensive, streamlined system design guide for senior/staff engineer interviews**  
> Consolidating best practices, advanced patterns, and real-world implementations

---

## About This Repository

This repository is optimized for **SDE-3 level interview preparation** (Senior/Staff Software Engineer). It consolidates system design concepts, eliminates duplicate content, and provides in-depth insights with production-level trade-offs.

### What's New (v2.0)?

- **Consolidated Content**: Merged duplicate topics from multiple sources  
- **Advanced Topics**: Distributed systems, consensus protocols, consistency models  
- **Interview Templates**: Step-by-step HLD/LLD interview frameworks  
- **Cheat Sheets**: Quick reference for capacity estimation & trade-offs  
- **Enhanced Problems**: All 27 HLD problems with capacity estimation & scaling strategies  
- **Production Focus**: Real-world examples, war stories, and operational insights

---

## Repository Structure

```
SystemDesign/
├── core-concepts/                     # Fundamental building blocks
│   ├── fundamentals.md                # Complete basics (networking, protocols, caching, etc.)
│   ├── databases.md                    # SQL vs NoSQL, ACID, CAP, replication, sharding
│   ├── caching-cdn.md
│   ├── networking.md
│   └── security.md
│
├── advanced-topics/                    # SDE-3 level deep dives
│   ├── distributed-systems.md          # Consistency models, consensus, time & ordering
│   ├── consensus-protocols.md          # Raft, Paxos, Zab
│   ├── distributed-transactions.md     # 2PC, Saga, Outbox patterns
│   ├── observability.md                # Metrics, tracing, SLI/SLO/SLA
│   └── chaos-engineering.md
│
├── hld-problems/                       # 27 enhanced system design problems
│   ├── README.md                       # Problem catalog & difficulty ratings
│   ├── easy/                           # URL Shortener, Pastebin, etc.
│   ├── medium/                         # Twitter, Instagram, etc.
│   └── hard/                           # Uber, Netflix, Distributed Message Queue
│
├── lld-problems/                       # Low-level design & patterns
│   ├── design-patterns/
│   ├── SOLID-principles/
│   ├── concurrency/
│   └── problems/
│
├── interview-templates/                # Frameworks for interviews
│   ├── hld-template.md                 # 45-60 min HLD interview guide
│   ├── lld-template.md                 # LLD approach checklist
│   ├── capacity-estimation.md          # QPS, storage, bandwidth calculations
│   └── trade-offs-cheat-sheet.md       # SQL vs NoSQL, sync vs async, etc.
│
└── reference/                          # Quick reference materials
    ├── numbers-to-know.md              # Latency, throughput, cost estimates
    ├── ml-system-design.md
    └── book-summaries/
```

---

## Learning Paths

### Path 1: Beginner to Intermediate (4-6 weeks)

**Goal**: Build strong foundation in system design concepts

**Week 1-2: Core Concepts**
- [ ] Read `core-concepts/fundamentals.md`
  - Networking: IP, DNS, TCP/UDP, HTTP/HTTPS
  - Load balancing, caching, CDN
  - Availability, scalability, reliability
- [ ] Read `core-concepts/databases.md`
  - SQL vs NoSQL decision matrix
  - ACID vs BASE
  - CAP theorem

**Week 3-4: Practice Easy Problems**
- [ ] URL Shortener (`hld-problems/easy/`)
- [ ] Pastebin
- [ ] Key-Value Store
- Use `interview-templates/hld-template.md` for structured approach

**Week 5-6: Intermediate Concepts**
- [ ] Read `core-concepts/caching-cdn.md`
- [ ] Practice medium problems:
  - Twitter Timeline
  - Instagram
  - Notification Service

**Resources:**
- `interview-templates/capacity-estimation.md` for back-of-envelope calculations
- `interview-templates/trade-offs-cheat-sheet.md` for decision-making

---

### Path 2: SDE-3 Interview Preparation (6-8 weeks)

**Goal**: Master advanced topics and complex system design

**Week 1-2: Master Core Concepts**
- [ ] Review all files in `core-concepts/`
- [ ] Focus on trade-offs and production scenarios

**Week 3-4: Advanced Distributed Systems**
- [ ] `advanced-topics/distributed-systems.md`
  - Consistency models (strong, eventual, causal)
  - Lamport/Vector clocks
  - CRDTs, conflict resolution
- [ ] `advanced-topics/consensus-protocols.md`
  - Raft consensus (step-by-step)
  - Paxos algorithm
  - When to use each

**Week 5-6: Distributed Transactions & Patterns**
- [ ] `advanced-topics/distributed-transactions.md`
  - Two-Phase Commit (2PC)
  - Saga pattern (choreography vs orchestration)
  - Outbox pattern
- [ ] Practice hard problems:
  - Distributed Message Queue
  - Payment System
  - Stock Exchange

**Week 7-8: Mock Interviews & Refinement**
- [ ] Practice all 27 HLD problems
- [ ] Use `interview-templates/hld-template.md` for every problem
- [ ] Time yourself (45-60 min per problem)
- [ ] Focus on:
  - Capacity estimation (first 15 min)
  - Trade-offs (SQL vs NoSQL, sync vs async)
  - Scaling strategies
  - Failure scenarios

**Key Files for SDE-3:**
1. `interview-templates/capacity-estimation.md` → Master QPS, storage, bandwidth calculations
2. `interview-templates/trade-offs-cheat-sheet.md` → Decision matrices for all major choices
3. `advanced-topics/distributed-systems.md` → Understand consistency vs availability deeply
4. `reference/numbers-to-know.md` → Memorize latency numbers

---

### Path 3: Staff/Principal Engineer (Advanced)

**Goal**: Deep expertise in distributed systems and production operations

**Focus Areas:**
- [ ] `advanced-topics/observability.md`
  - Distributed tracing (Jaeger, Zipkin)
  - SLI/SLO/SLA definitions with math
  - Alert fatigue prevention
- [ ] `advanced-topics/chaos-engineering.md`
  - Resilience patterns
  - Production testing strategies
- [ ] Advanced problem variants:
  - Multi-region active-active
  - Cross-datacenter consensus
  - Handling network partitions

---

## Interview Preparation Checklist

### Before the Interview

- [ ] Review `interview-templates/hld-template.md` (know the 7 phases)
- [ ] Memorize numbers from `reference/numbers-to-know.md`
  - 1M requests/day ≈ 12 QPS
  - L1 cache: 0.5ns, RAM: 100ns, SSD: 150μs
  - 99.9% availability = 8.76 hours downtime/year
- [ ] Practice capacity estimation (use `5 Interview Templates/capacity-estimation.md`)
- [ ] Review trade-offs (use `interview-templates/trade-offs-cheat-sheet.md`)

### During the Interview

**Phase 1 (0-10 min): Requirements**
- [ ] Clarify functional requirements (top 3-5 features)
- [ ] Define non-functional requirements (PASS-R: Performance, Availability, Scalability, Security, Reliability)
- [ ] Agree on scale (DAU, QPS, storage)

**Phase 2 (10-15 min): Capacity Estimation**
- [ ] Calculate QPS (writes, reads)
- [ ] Estimate storage (per record × total records × replication)
- [ ] Bandwidth (QPS × response size)

**Phase 3 (15-25 min): Design**
- [ ] API design (REST endpoints)
- [ ] Database schema
- [ ] High-level architecture diagram

**Phase 4 (25-55 min): Deep Dives**
- [ ] Scaling strategies
- [ ] Caching approach
- [ ] Failure scenarios
- [ ] Trade-offs (SQL vs NoSQL, sync vs async, etc.)

**Phase 5 (55-60 min): Wrap-up**
- [ ] Summars design
- [ ] Mention future enhancements
- [ ] Q&A

---

## Key Principles for SDE-3 Interviews

### 1. Always Discuss Trade-offs

Don't just say "I'd use Redis for caching." Say:
> "I'd use Redis for caching because:
> - Sub-millisecond latency (critical for our 100ms SLA)
> - Rich data structures (sorted sets for leaderboards)
> - Limited storage (use LRU eviction)
> - Not durable (persist to DB as source of truth)"

### 2. Think at Scale

- Start simple, then scale
- "For 1K users, single server works. At 1M users, we need..."
- Use real numbers (capacity estimation)

### 3. Production Mindset

- Monitoring: "How do we know it's working?"
- Failure scenarios: "What if the DB goes down?"
- Operational complexity: "How hard is this to maintain?"

### 4. Communication

- Think out loud
- Draw diagrams
- Ask clarifying questions
- Validate assumptions with interviewer

---

## Recommended Study Order

1. **Start here**: `interview-templates/hld-template.md`
2. **Learn basics**: `core-concepts/fundamentals.md`
3. **Master databases**: `core-concepts/databases.md`
4. **Practice estimation**: `interview-templates/capacity-estimation.md`
5. **Learn trade-offs**: `interview-templates/trade-offs-cheat-sheet.md`
6. **Advanced topics**: `advanced-topics/distributed-systems.md`
7. **Practice problems**: `hld-problems/` (start easy → hard)

---

## Migration from Old Structure

If you're familiar with the old repository structure:

| Old Location | New Location |
|--------------|--------------|
| `system-design-hld+lld/hld/basics.md` | `core-concepts/fundamentals.md` |
| `glossary.md` + `system-design-components.md` | `core-concepts/databases.md` |
| `30-days-theory/` | **Removed** (content integrated into Core Concepts) |
| `hld/notes/interview-process/` | `hld-problems/` |
| `lld/lld-interview-guide.md` | `interview-templates/lld-template.md` |

**What was removed:**
- Duplicate content (glossary, repeated design patterns)
- 30-days-theory (integrated into core concepts)
- Scattered interview questions (now organized by difficulty)

**What was added:**
- Advanced distributed systems topics
- Interview templates with timing
- Capacity estimation formulas
- Trade-off decision matrices
- Enhanced problem descriptions with capacity estimation

---

## How to Use This Repository

### For Interview Preparation

```bash
# Clone the repository
git clone https://github.com/yourusername/SystemDesign.git
cd SystemDesign

# Start with interview template
cat "interview-templates/hld-template.md"

# Practice a problem using the template
# Example: Design URL Shortener
open "hld-problems/easy/url-shortener.md"

# Reference cheat sheets during practice
open "interview-templates/capacity-estimation.md"
open "interview-templates/trade-offs-cheat-sheet.md"
```

### For Learning

Follow the learning paths above based on your current level.

---

## Contributing

This repository is under active development. Contributions welcome!

### To-Do List

- [ ] Complete all 27 HLD problem enhancements (capacity estimation, scaling, failures)
- [ ] Add LLD template with examples
- [ ] Create observability.md
- [ ] Add chaos-engineering.md
- [ ] Create practice roadmap (30-day, 60-day plans)
- [ ] Add mermaid diagrams for architecture visualizations

---

## Additional Resources

### Books
- "Designing Data-Intensive Applications" by Martin Kleppmann
- "System Design Interview" by Alex Xu  
- "Web Scalability for Startup Engineers" by Artur Ejsmont

### Online
- [High Scalability Blog](http://highscalability.com/)
- [AWS Architecture Center](https://aws.amazon.com/architecture/)
- [Google Cloud Architecture Center](https://cloud.google.com/architecture)

### Related Repositories
- [System Design Primer](https://github.com/donnemartin/system-design-primer)
- [Awesome System Design](https://github.com/madd86/awesome-system-design)

---

## License

MIT License - Feel free to use this for your interview preparation and share with others!

---

## Star This Repository

If you find this helpful, please star ⭐ the repository and share with others preparing for senior-level interviews!

**Good luck with your interviews!**
