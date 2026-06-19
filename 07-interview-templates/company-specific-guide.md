---
module: 07-interview-templates
topic: Company-Specific Interview Guide
status: unread
tags: [07-interview-templates, interview, maang, meta, google, amazon, apple]
---
# Company-Specific Interview Guide

> Same technical content, different signal-gathering. Each company's interviewers are trained to extract different signals. Calibrate your delivery, not your knowledge.

---

## How to Use This File

Before an onsite:
1. Read the company section top to bottom
2. Internalize the "what they're really testing" column — every answer you give should address that signal
3. Review the "avoid" list — these are actual reasons offers get declined at that company
4. Use the sample probes to practice being interrupted mid-design and redirecting correctly

---

## Meta (Facebook)

### Bar Definition
SDE-3 at Meta = "works independently on large projects, drives technical decisions across teams, and unblocks others." The bar is explicitly higher than FAANG average. Interviewers are calibrated on "would this person raise the bar of my team?"

### Interview Structure (System Design)
- 1 round, 45 min
- Single problem, usually a product Meta has built (news feed, notifications, ads, messenger)
- Interviewer will push deep on 2–3 specific areas — they have a checklist of signals they need to see

### What They're Actually Testing

| Area | Surface question | Real signal they want |
|---|---|---|
| Scale intuition | "Design News Feed" | Can you anchor every decision to numbers? Meta = billions of users. If your design doesn't mention fan-out at 1B scale, you missed the point. |
| Product sense | Any problem | Do you ask clarifying questions that reveal you understand the product, not just the engineering? "Is this for mobile-first users?" shows product sense. |
| Trade-off articulation | "Why not just use X?" | Can you say "X costs Y" without hedging? Vague answers ("it depends") without specifics are a red flag at Meta. |
| Operational mindset | "How does this fail?" | Meta interviewers explicitly look for: monitoring, alerting, graceful degradation. Missing these signals SDE-2 thinking. |
| Data model depth | "Walk me through your schema" | Can you reason about denormalization at scale? At Meta's scale, joins are often impossible. |

### Meta-Specific Patterns to Know Cold
- **Fan-out on write vs fan-out on read** — News feed is the canonical Meta problem. Know push vs pull vs hybrid (celebrities use pull, regular users use push). Know the threshold (~1M followers) where you switch strategies.
- **Ranked feed** — Meta's feeds are not chronological. Know how to score posts (engagement signals, recency decay) and serve the top-K efficiently.
- **Notification coalescing** — "3 people liked your post" is not 3 separate notifications. Know how to group and batch.
- **Ads auction** — If asked anything ads-related: second-price auction, pCTR × bid ranking, budget pacing, frequency capping.
- **TAO (Facebook's graph store)** — Know that Meta uses a custom graph cache (TAO) layered over MySQL for social graph data. You don't need internals, but referencing "a graph-optimized cache layer" shows you understand why standard relational doesn't work for social graphs.

### Meta Interview Phases (45 min)

```
0–5 min:   Requirements — they expect you to drive this; ask 3 sharp questions
5–10 min:  Scale estimation — they will stop you if you skip this; do it explicitly
10–25 min: HLD — draw the diagram, narrate every component choice
25–40 min: Deep dive — they will pick 1-2 areas; go very deep, not broad
40–45 min: Failure modes + monitoring — explicitly mention alerts, dashboards, on-call
```

### Phrases That Signal SDE-3 at Meta
- "At Meta's scale, X would bottleneck because Y — so I'd use Z instead."
- "I'd instrument this with P99 latency and error rate metrics; alert if P99 > Xms."
- "The trade-off here is write amplification vs read latency — I'd choose write amplification because reads are 100× more frequent."
- "For rollout I'd use feature flags, start at 1% of users, monitor error rate, ramp to 10% then 100%."

### What to Avoid at Meta
- **Jumping to microservices without justifying scale** — Meta interviewers will call this out. Start monolith, add complexity when the numbers demand it.
- **Vague consistency answers** — "eventual consistency" without explaining what the user experiences is not acceptable. Say: "Users may see a like count that's 5 seconds stale — acceptable because likes are non-critical."
- **Skipping monitoring** — This is a documented signal in Meta's rubric. Always close with "how do we know this is working."
- **Over-indexing on databases** — Meta's problems are often about application-level data structures (sorted sets, graphs), not DB schema.

---

## Google

### Bar Definition
SDE-3 at Google (L5) = "significant scope and impact, drives projects end-to-end, recognized technical leader." Google's system design bar is the highest of the MAANG companies for pure technical depth. Interviewers are PhD-heavy and comfortable going very deep on algorithms and distributed systems.

### Interview Structure (System Design)
- 1–2 rounds depending on level/role
- Problems often have a Google-internal flavor: search, maps, YouTube, ads, distributed infrastructure
- Interviewers often have a specific "deep dive" area prepared — they want to see you go multiple layers deep

### What They're Actually Testing

| Area | Surface question | Real signal they want |
|---|---|---|
| First-principles reasoning | "How would you design distributed storage?" | Don't reference AWS S3 internals — reason from scratch. Google values building from fundamentals, not stitching managed services. |
| Algorithmic depth | Any geo or search problem | Expect to derive the data structure, not just name it. "Quad tree" is the start; explaining insertion, rebalancing, query complexity is the finish. |
| Consistency model mastery | "What consistency does your system need?" | Google interviewers probe until you've named the specific model (linearizable, sequential, causal, eventual) and justified it. |
| Correctness under failure | "What happens when a node crashes mid-write?" | Walk through the failure step by step. "We'd retry" is not enough — explain what state the system is left in and how you recover it. |
| Spanner/Bigtable awareness | Cloud storage problems | Google built Spanner and Bigtable. Knowing their use cases (Spanner: global transactions; Bigtable: time-series/wide-column) signals you've read the literature. |

### Google-Specific Patterns to Know Cold
- **MapReduce / Dataflow model** — Even if you don't use it, knowing the paradigm (map → shuffle → reduce, bounded vs unbounded, Apache Beam as the API) signals fluency with large-scale data processing.
- **Consistent hashing + virtual nodes** — Google interviewers will often ask you to implement or extend consistent hashing. Know virtual nodes, why they help with hotspots, and the math.
- **Spanner's TrueTime** — For any globally distributed system: Spanner uses GPS/atomic clock-backed TrueTime to achieve external consistency. You don't need full internals, but knowing that "global transactions require clock synchronization and commit-wait" separates you.
- **GFS / Colossus mental model** — Large file storage at Google: chunk servers + master. Colossus (GFS v2) uses per-cell metadata servers. Know why: metadata on one master is a bottleneck; distributed metadata is the fix.
- **B-tree vs LSM at Google scale** — Bigtable and LevelDB both use LSM. Know why: write-heavy workloads, sequential writes to SSTables, compaction in background. Google has deeply explored LSM trade-offs.

### Google Interview Phases (45–60 min)

```
0–8 min:   Requirements — Google interviewers often give underspecified problems on purpose
            to see if you ask the RIGHT questions (not just any questions)
8–15 min:  Scale + constraints — be explicit about numbers, latency SLOs, durability requirements
15–30 min: HLD — Google interviewers often interrupt with "why not X?" — answer confidently
30–50 min: Deep dive — they will go very deep; expect "and what if that fails?", "what's the
            complexity?", "how does that scale to 10×?"
50–60 min: Extensions — Google often ends with "now make it globally distributed"
```

### Phrases That Signal SDE-3 at Google
- "This requires linearizability because clients must read their own writes — eventual consistency would cause confusing behavior."
- "The time complexity of this lookup is O(log N) on the consistent hash ring; with virtual nodes it becomes O(log V×N) but hot-spot variance drops by √V."
- "I'd use an LSM-based store here because writes are 10× more frequent than reads and we can afford slightly higher read amplification."
- "For global distribution we'd need to account for clock skew — either use TrueTime-style bounded uncertainty or accept that our transactions are serializable but not externally consistent."

### What to Avoid at Google
- **Managed service name-dropping** — "I'd use DynamoDB for this" without explaining the data model and why it fits will not land well. Google interviewers want to see you reason from principles.
- **Stopping at the first answer** — Google deliberately probes deeper. If you give an answer and stop, they'll keep asking "and then what?" until you go deeper or admit you don't know. Go 2–3 layers deep proactively.
- **Handwavy consistency** — "Eventually consistent" without defining what eventual means for your system (seconds? minutes? after partition heals?) is a signal of shallow understanding.
- **Ignoring correctness** — Google prioritizes correctness over simplicity. An answer that's simple but wrong under concurrent writes will be called out.

---

## Amazon

### Bar Definition
SDE-3 at Amazon (SDE III) = "solves ambiguous problems, designs scalable systems, mentors SDE-IIs, drives multi-team technical decisions." Amazon's interviews are uniquely bifurcated: Leadership Principles (LP) matter as much as technical depth. System design success requires anchoring answers in LP framing.

### Interview Structure (System Design)
- 1–2 rounds
- Problems often have an Amazon/AWS flavor: order processing, fulfillment, DynamoDB-adjacent, S3-adjacent, distributed services
- Interviewers explicitly map your answers to LP signals — they're trained to score on both

### What They're Actually Testing

| Area | Surface question | Real signal they want |
|---|---|---|
| Customer obsession | "What are the requirements?" | Do your first questions focus on the user/customer experience, not just engineering constraints? |
| Ownership | "How do you handle failures?" | Do you design for operational excellence — runbooks, alarms, capacity planning — not just happy path? |
| Dive deep | Any problem | Will you go one level deeper without being prompted? Amazon interviewers expect unprompted depth. |
| Invent and simplify | "How would you scale this?" | Can you identify the simplest design that solves the problem? Amazon penalizes over-engineering explicitly. |
| Are Right, A Lot | Trade-off questions | Do you commit to a position and defend it with data, or do you hedge? |

### Amazon-Specific Patterns to Know Cold
- **DynamoDB access patterns** — Amazon interviewers expect fluency: partition key + sort key design, GSI vs LSI, single-table design, hot partition problem. "Use DynamoDB" without explaining the key schema is incomplete.
- **SQS + SNS fan-out** — The canonical Amazon async pattern: SNS topic → multiple SQS queues → consumers. Know when to use this vs Kafka (SQS: simple, managed, at-least-once; Kafka: ordered, high throughput, replay).
- **Idempotency + exactly-once** — Amazon's payment and order systems require exactly-once processing. Know the idempotency key pattern, dedup within a time window, and how to make retries safe.
- **Two-pizza team principle** — Amazon decomposes systems so each service is owned by one small team. System designs that create cross-team dependencies are penalized. Each service must be independently deployable with its own DB.
- **Operational excellence** — Amazon explicitly asks "how do you operate this in production." Have answers for: CloudWatch alarms, runbooks, capacity planning, on-call rotation impact.

### Amazon Interview Phases (45–60 min)

```
0–5 min:   Requirements — anchor first questions on customer impact, not just functionality
5–10 min:  Scale estimation — Amazon interviewers care about order-of-magnitude reasoning
10–25 min: HLD — draw the standard AWS architecture; use AWS service names where appropriate
            (they don't penalize this like Google does)
25–40 min: Deep dive — they will often ask about failure handling and operational concerns
40–50 min: LP signals — expect "tell me about a time you had to make a trade-off like this"
            interleaved with the technical discussion
50–60 min: Extensions / scale
```

### Phrases That Signal SDE-3 at Amazon
- "From a customer perspective, the most critical thing is that orders are never lost — so I'd prioritize durability over latency here."
- "I'd design this so each service owns its own data store — no shared DBs — so teams can deploy independently."
- "For idempotency: the client sends an idempotency key; the server stores key → result in a DynamoDB table with TTL 24h; duplicate requests return the cached result."
- "I'd add a CloudWatch alarm on queue depth and P99 processing latency; if the alarm fires, the runbook says to scale up the consumer fleet."

### What to Avoid at Amazon
- **Ignoring LPs in answers** — Not explicitly framing your decisions in LP language loses signal. Weave in "I'm prioritizing customer obsession here because…" naturally.
- **Over-engineering** — Amazon's "Invent and Simplify" LP explicitly penalizes unnecessary complexity. If you propose a distributed saga when a simple DB transaction works at the scale given, you'll lose points.
- **Assuming AWS** — Don't assume the design must use AWS services. Ask "are we designing for AWS or cloud-agnostic?" first. Then use AWS services only where they genuinely fit.
- **No operational story** — Designs without alarms, dashboards, and failure runbooks signal SDE-2 at Amazon. Always close with "here's how we operate this."

---

## Apple

### Bar Definition
SDE-3 at Apple = "deep technical expert, works cross-functionally, owns significant product surfaces, designs for privacy and reliability." Apple's bar is less publicly documented than MAANG peers but is known for: privacy-first thinking, hardware/software co-design awareness, and high polish on design decisions.

### Interview Structure (System Design)
- 1–2 rounds, sometimes split into HLD + deep-dive on separate days
- Problems often reflect Apple product surfaces: iCloud sync, App Store, Apple Pay, Siri, Maps, Health
- Interviewers tend to be senior ICs who care deeply about their own domain — expect domain-specific follow-ups

### What They're Actually Testing

| Area | Surface question | Real signal they want |
|---|---|---|
| Privacy by design | Any problem involving user data | Do you ask "what data do we actually need?" and propose minimization before building? Apple fails candidates who collect data without justifying it. |
| Reliability | "How does this fail?" | Apple ships hardware + software in tandem. Reliability failures are public. Show you design for graceful degradation and offline-first. |
| Simplicity | Design a feature | Apple's design philosophy extends to engineering. Over-complex designs raise flags. |
| Cross-functional awareness | Anything iCloud/device | Do you understand that Apple designs for the full stack: on-device, network, server? Know where computation should happen (on device vs server). |
| Security depth | Auth, sync, payments | Apple leads on security. Know E2E encryption, Secure Enclave, certificate pinning, and why they matter. |

### Apple-Specific Patterns to Know Cold
- **iCloud sync model** — CloudKit: client-side conflict resolution with change tokens. CKRecord has a `recordChangeTag` — server rejects writes with stale tags (optimistic concurrency). Devices sync via zones + fetch changes. Important because Apple designs sync differently from Google Drive (client-authoritative vs server-authoritative).
- **End-to-end encryption** — Apple's iMessage, Health, and Passwords use E2E encryption where Apple cannot decrypt data. Know: asymmetric key exchange (ECDH), per-message symmetric encryption (AES-256), key escrow via iCloud Keychain. Know WHY: regulatory protection, user trust.
- **On-device ML** — Apple heavily invests in on-device inference (CoreML, Neural Engine). For any ML problem, ask "can we run inference on-device?" — Apple prefers this for privacy. Know tradeoffs: on-device = no server cost, better privacy, limited to device capability; server = unlimited compute, requires data leaving device.
- **App Store review process** — If the problem involves developer tooling: binary validation, entitlement checks, privacy label verification. Apple has strict binary analysis at submission time.
- **Apple Pay / Secure Enclave** — Payment flows: tokenization (PAN → device account number stored in Secure Enclave), contactless via NFC, Apple's role as the payment network intermediary. Know why tokenization matters for security.

### Apple Interview Phases (45–60 min)

```
0–8 min:   Requirements — Apple interviewers respond well to privacy-first questions:
            "What data does this system need to retain?" asked early signals Apple fit
8–15 min:  Constraints — ask explicitly about: on-device vs server computation, offline support
15–30 min: HLD — Apple problems often span device + server; draw both halves
30–45 min: Deep dive — expect heavy focus on sync protocol, conflict resolution, or security model
45–60 min: Edge cases — Apple interviewers ask about edge cases more than most companies:
            "What if the user has 14 devices and changes on 3 simultaneously?"
```

### Phrases That Signal SDE-3 at Apple
- "Before deciding where to store this data, I want to understand what the minimum necessary data set is — for privacy reasons we shouldn't retain anything we don't need."
- "For sync conflict resolution: last-write-wins is simple but creates data loss; I'd use a CRDT or server-authoritative merge with user-visible conflict UI for high-value data."
- "This should be E2E encrypted — Apple's server should hold only ciphertext; decryption happens on-device with keys derived from user credentials."
- "I'd make this work offline-first — the device is the source of truth; sync to server when connectivity is available."

### What to Avoid at Apple
- **Privacy afterthought** — Adding "and of course we'd encrypt the data" at the end of a design, rather than designing around it from the start, is a red flag. Privacy should shape the architecture.
- **Server-first thinking** — Proposing to run heavy computation on servers when on-device is feasible signals misunderstanding of Apple's philosophy.
- **Ignoring conflict resolution** — Any sync problem that doesn't address concurrent edits will be probed. Have a clear answer for: what happens when device A and device B both modify the same record while offline?
- **Handwavy security** — "We'll use TLS and OAuth" is not sufficient at Apple. Explain the key management model, what happens if a device is lost, how keys are rotated.

---

## Cross-Company Comparison

| Dimension | Meta | Google | Amazon | Apple |
|---|---|---|---|---|
| Primary signal | Scale reasoning + product sense | First-principles + algorithmic depth | Ownership + LP alignment + simplicity | Privacy-first + reliability + sync |
| Tone | Fast-paced, push back expected | Academic, depth > breadth | Structured, LP framing throughout | Deliberate, detail-oriented |
| Managed services | Fine to use | Reason from principles first | AWS services expected | Apple platforms expected |
| Key differentiator topic | Fan-out at 1B users | Consistency models + distributed correctness | Idempotency + operational excellence | E2E encryption + offline-first sync |
| Biggest mistake | Skip monitoring / vague trade-offs | Stop at first answer / handwavy consistency | Over-engineer / ignore LPs | Privacy afterthought / server-first |
| Depth vs breadth | Breadth first, depth on demand | Depth first always | Breadth + LP framing | Depth on security/sync |
| What makes you SDE-3 vs SDE-2 | Monitoring + operational story | Multi-layer depth + correctness proofs | Cross-team design + LP anchoring | Privacy architecture + conflict resolution |

---

## Universal SDE-3 Signals (All Companies)

These are expected at SDE-3 regardless of company. Missing any one is a common reason for downleveling to SDE-2.

```
1. You drive the structure — you don't wait to be asked "what about caching?"
2. You anchor every choice in numbers — "I'd shard because we'll exceed single-node write capacity at 10K writes/sec"
3. You name trade-offs explicitly — not just "I chose X" but "X over Y because Z, at the cost of W"
4. You address failure modes unprompted — at least 2: what if the DB is down, what if the cache is poisoned
5. You mention operational concerns — monitoring, alerting, on-call impact
6. You distinguish between what to build now vs later — "in v1 I'd use X; at 10× scale we'd need Y"
7. You know when to stop going deeper — SDE-3 spends time on the 20% of decisions that matter, not perfect coverage
```

---

## Quick Reference: Problem → Company Likelihood

| Problem | Most likely at | Key angle to prepare |
|---|---|---|
| News Feed / Social Graph | Meta | Fan-out at scale, feed ranking, TAO-like graph cache |
| Ads System | Meta, Google | Auction mechanics, budget pacing, click aggregation |
| Distributed Storage | Google, Amazon | LSM vs B-tree, consistency model, replication |
| Search | Google | Inverted index, ranking, crawl pipeline, freshness |
| Payment System | Amazon, Apple | Idempotency, exactly-once, ledger, reconciliation |
| Maps / Geo | Google, Apple | Geo-indexing, routing, ETA, tile serving |
| Chat / Messaging | Meta, Apple | WebSocket, message ordering, E2E encryption |
| Cloud Sync | Apple, Google | Conflict resolution, change tokens, offline-first |
| Recommendation | Meta, Amazon | Collaborative filtering, embedding retrieval, feedback loop |
| Job Scheduler | Amazon, Google | Distributed coordination, priority queues, fault tolerance |
