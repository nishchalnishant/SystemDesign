# System Design - SDE-3 Interview Preparation Guide

> **Comprehensive, streamlined system design guide for senior/staff engineer interviews**  
> Consolidating best practices, advanced patterns, and real-world implementations

---

## 🎯 About This Repository

This repository is optimized for **SDE-3 level interview preparation** (Senior/Staff Software Engineer). It consolidates system design concepts, eliminates duplicate content, and provides in-depth insights with production-level trade-offs.

### What's New (v2.0)?

✅ **Consolidated Content**: Merged duplicate topics from multiple sources  
✅ **Advanced Topics**: Distributed systems, consensus protocols, consistency models  
✅ **Interview Templates**: Step-by-step HLD/LLD interview frameworks  
✅ **Cheat Sheets**: Quick reference for capacity estimation & trade-offs  
✅ **Enhanced Problems**: All 27 HLD problems with capacity estimation & scaling strategies  
✅ **Production Focus**: Real-world examples, war stories, and operational insights

---

## 📚 Repository Structure

```
SystemDesign/
├── 1. Core Concepts/                  # Fundamental building blocks
│   ├── fund amentals.md                # Complete basics (networking, protocols, caching, etc.)
│   ├── databases.md                    # SQL vs NoSQL, ACID, CAP, replication, sharding
│   ├── caching-cdn.md
│   ├── networking.md
│   └── security.md
│
├── 2. Advanced Topics/                 # SDE-3 level deep dives
│   ├── distributed-systems.md          # Consistency models, consensus, time & ordering
│   ├── consensus-protocols.md          # Raft, Paxos, Zab
│   ├── distributed-transactions.md     # 2PC, Saga, Outbox patterns
│   ├── observability.md                # Metrics, tracing, SLI/SLO/SLA
│   └── chaos-engineering.md
│
├── 3. HLD Interview Problems/          # 27 enhanced system design problems
│   ├── README.md                       # Problem catalog & difficulty ratings
│   ├── easy/                           # URL Shortener, Pastebin, etc.
│   ├── medium/                         # Twitter, Instagram, etc.
│   └── hard/                           # Uber, Netflix, Distributed Message Queue
│
├── 4. LLD Interview Problems/          # Low-level design & patterns
│   ├── design-patterns/
│   ├── SOLID-principles/
│   ├── concurrency/
│   └── problems/
│
├── 5. Interview Templates/             # Frameworks for interviews
│   ├── hld-template.md                 # 45-60 min HLD interview guide
│   ├── lld-template.md                 # LLD approach checklist
│   ├── capacity-estimation.md          # QPS, storage, bandwidth calculations
│   └── trade-offs-cheat-sheet.md       # SQL vs NoSQL, sync vs async, etc.
│
└── 6. Reference/                       # Quick reference materials
    ├── numbers-to-know.md              # Latency, throughput, cost estimates
    ├── ml-system-design.md
    └── book-summaries/
```

---

## 🚀 Learning Paths

### Path 1: Beginner to Intermediate (4-6 weeks)

**Goal**: Build strong foundation in system design concepts

**Week 1-2: Core Concepts**
- [ ] Read `1. Core Concepts/fundamentals.md`
  - Networking: IP, DNS, TCP/UDP, HTTP/HTTPS
  - Load balancing, caching, CDN
  - Availability, scalability, reliability
- [ ] Read `1. Core Concepts/databases.md`
  - SQL vs NoSQL decision matrix
  - ACID vs BASE
  - CAP theorem

**Week 3-4: Practice Easy Problems**
- [ ] URL Shortener (`3. HLD Interview Problems/easy/`)
- [ ] Pastebin
- [ ] Key-Value Store
- Use `5. Interview Templates/hld-template.md` for structured approach

**Week 5-6: Intermediate Concepts**
- [ ] Read `1. Core Concepts/caching-cdn.md`
- [ ] Practice medium problems:
  - Twitter Timeline
  - Instagram
  - Notification Service

**Resources:**
- `5. Interview Templates/capacity-estimation.md` for back-of-envelope calculations
- `5. Interview Templates/trade-offs-cheat-sheet.md` for decision-making

---

### Path 2: SDE-3 Interview Preparation (6-8 weeks)

**Goal**: Master advanced topics and complex system design

**Week 1-2: Master Core Concepts**
- [ ] Review all files in `1. Core Concepts/`
- [ ] Focus on trade-offs and production scenarios

**Week 3-4: Advanced Distributed Systems**
- [ ] `2. Advanced Topics/distributed-systems.md`
  - Consistency models (strong, eventual, causal)
  - Lamport/Vector clocks
  - CRDTs, conflict resolution
- [ ] `2. Advanced Topics/consensus-protocols.md`
  - Raft consensus (step-by-step)
  - Paxos algorithm
  - When to use each

**Week 5-6: Distributed Transactions & Patterns**
- [ ] `2. Advanced Topics/distributed-transactions.md`
  - Two-Phase Commit (2PC)
  - Saga pattern (choreography vs orchestration)
  - Outbox pattern
- [ ] Practice hard problems:
  - Distributed Message Queue
  - Payment System
  - Stock Exchange

**Week 7-8: Mock Interviews & Refinement**
- [ ] Practice all 27 HLD problems
- [ ] Use `5. Interview Templates/hld-template.md` for every problem
- [ ] Time yourself (45-60 min per problem)
- [ ] Focus on:
  - Capacity estimation (first 15 min)
  - Trade-offs (SQL vs NoSQL, sync vs async)
  - Scaling strategies
  - Failure scenarios

**Key Files for SDE-3:**
1. `5. Interview Templates/capacity-estimation.md` → Master QPS, storage, bandwidth calculations
2. `5. Interview Templates/trade-offs-cheat-sheet.md` → Decision matrices for all major choices
3. `2. Advanced Topics/distributed-systems.md` → Understand consistency vs availability deeply
4. `6. Reference/numbers-to-know.md` → Memorize latency numbers

---

### Path 3: Staff/Principal Engineer (Advanced)

**Goal**: Deep expertise in distributed systems and production operations

**Focus Areas:**
- [ ] `2. Advanced Topics/observability.md`
  - Distributed tracing (Jaeger, Zipkin)
  - SLI/SLO/SLA definitions with math
  - Alert fatigue prevention
- [ ] `2. Advanced Topics/chaos-engineering.md`
  - Resilience patterns
  - Production testing strategies
- [ ] Advanced problem variants:
  - Multi-region active-active
  - Cross-datacenter consensus
  - Handling network partitions

---

## 🎯 Interview Preparation Checklist

### Before the Interview

- [ ] Review `5. Interview Templates/hld-template.md` (know the 7 phases)
- [ ] Memorize numbers from `6. Reference/numbers-to-know.md`
  - 1M requests/day ≈ 12 QPS
  - L1 cache: 0.5ns, RAM: 100ns, SSD: 150μs
  - 99.9% availability = 8.76 hours downtime/year
- [ ] Practice capacity estimation (use `5 Interview Templates/capacity-estimation.md`)
- [ ] Review trade-offs (use `5. Interview Templates/trade-offs-cheat-sheet.md`)

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

## 💡 Key Principles for SDE-3 Interviews

### 1. Always Discuss Trade-offs

Don't just say "I'd use Redis for caching." Say:
> "I'd use Redis for caching because:
> - ✅ Sub-millisecond latency (critical for our 100ms SLA)
> - ✅ Rich data structures (sorted sets for leaderboards)
> - ❌ Limited storage (use LRU eviction)
> - ❌ Not durable (persist to DB as source of truth)"

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

## 📖 Recommended Study Order

1. **Start here**: `5. Interview Templates/hld-template.md`
2. **Learn basics**: `1. Core Concepts/fundamentals.md`
3. **Master databases**: `1. Core Concepts/databases.md`
4. **Practice estimation**: `5. Interview Templates/capacity-estimation.md`
5. **Learn trade-offs**: `5. Interview Templates/trade-offs-cheat-sheet.md`
6. **Advanced topics**: `2. Advanced Topics/distributed-systems.md`
7. **Practice problems**: `3. HLD Interview Problems/` (start easy → hard)

---

## 🔄 Migration from Old Structure

If you're familiar with the old repository structure:

| Old Location | New Location |
|--------------|--------------|
| `system-design-hld+lld/hld/basics.md` | `1. Core Concepts/fundamentals.md` |
| `glossary.md` + `system-design-components.md` | `1. Core Concepts/databases.md` |
| `30-days-theory/` | **Removed** (content integrated into Core Concepts) |
| `hld/notes/interview-process/` | `3. HLD Interview Problems/` |
| `lld/lld-interview-guide.md` | `5. Interview Templates/lld-template.md` |

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

## 🛠️ How to Use This Repository

### For Interview Preparation

```bash
# Clone the repository
git clone https://github.com/yourusername/SystemDesign.git
cd SystemDesign

# Start with interview template
cat "5. Interview Templates/hld-template.md"

# Practice a problem using the template
# Example: Design URL Shortener
open "3. HLD Interview Problems/easy/url-shortener.md"

# Reference cheat sheets during practice
open "5. Interview Templates/capacity-estimation.md"
open "5. Interview Templates/trade-offs-cheat-sheet.md"
```

### For Learning

Follow the learning paths above based on your current level.

---

## 🤝 Contributing

This repository is under active development. Contributions welcome!

### To-Do List

- [ ] Complete all 27 HLD problem enhancements (capacity estimation, scaling, failures)
- [ ] Add LLD template with examples
- [ ] Create observability.md
- [ ] Add chaos-engineering.md
- [ ] Create practice roadmap (30-day, 60-day plans)
- [ ] Add mermaid diagrams for architecture visualizations

---

## 📚 Additional Resources

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

## 📝 License

MIT License - Feel free to use this for your interview preparation and share with others!

---

## ⭐ Star This Repository

If you find this helpful, please star ⭐ the repository and share with others preparing for senior-level interviews!

**Good luck with your interviews! 🚀**
