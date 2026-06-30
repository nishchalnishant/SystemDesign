> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** 50 common mistakes that cause candidates to fail system design interviews, categorized by interview phase.
>
> **Key concepts:**
> - Premature Optimization: Adding Kafka and Redis to your diagram before explaining the basic data flow.
> - Unjustified Choices: "I'll use Cassandra" without explaining *why* Cassandra fits the specific write-heavy access pattern of this problem.
> - Ignoring Non-Functional Requirements: Forgetting to discuss latency, availability, or consistency until the interviewer forces you to.
> - Poor Time Management: Getting bogged down in database schema details while failing to finish the overall architecture diagram.
>
> **Key takeaway:** System Design interviews are as much about communication as technical skill. Avoiding these anti-patterns ensures you signal seniority and structure to the interviewer.

---
module: 07-interview-templates
topic: Interview Anti-Patterns — 50 Mistakes That Kill SDE-3 Offers
status: unread
tags: [07-interview-templates, interview, anti-patterns, sde-3]
---
# Interview Anti-Patterns: 50 Mistakes That Kill SDE-3 Offers

> These are the actual reasons candidates with strong technical knowledge get downleveled to SDE-2 or rejected. Most are not knowledge gaps — they are communication and framing failures.

---

## Category 1: Requirements & Framing (Mistakes 1–10)

**#1 — Jumping to design before clarifying requirements**
Starting to draw boxes without asking 3 targeted questions. Interviewers score this as SDE-2 behavior — SDE-3s are expected to shape the problem, not just solve the stated version.
*Fix:* First 5 minutes = requirements. Ask: scale (users, RPS), SLA (latency, availability), consistency requirements, and one product-shaping question ("is this global or single-region?").

**#2 — Asking too many clarifying questions**
Spending 15 minutes on requirements signals you can't make reasonable assumptions. 3 questions max — ask only questions whose answers change your architecture.
*Fix:* State assumptions explicitly: "I'll assume 10M users, 1000 writes/sec, and P99 < 100ms — please correct me if wrong."

**#3 — Treating the problem as fully specified**
Accepting the problem statement as complete and not pushing back on vague requirements ("design a social network"). SDE-3s are expected to identify ambiguity.
*Fix:* Identify the 2-3 axes where the design diverges based on requirements. "This design changes significantly depending on whether we need global availability or single-region — which is it?"

**#4 — Not scoping to a deliverable in 45 minutes**
Trying to design every component of a large system. You end up with a shallow overview of everything and deep analysis of nothing.
*Fix:* Explicitly scope: "I'll focus on the write path and fan-out since that's where the interesting trade-offs are. I'll sketch the read path at high level."

**#5 — Designing for requirements never mentioned**
Adding multi-region replication, ML-based fraud detection, and GDPR compliance to a problem that asked for a URL shortener. Interviewers see this as lack of judgment.
*Fix:* Design for stated requirements. Mention extensions as "if this requirement came up, I'd add X" — don't implement them.

**#6 — Asking "what would you like me to focus on?" too early**
This signals you don't have a point of view. SDE-3s lead the design. The interviewer can redirect you, but you should propose the structure.
*Fix:* Say "I'm going to focus on X because that's where the interesting trade-offs are — does that align with what you want to explore?" — this is leading, not asking.

**#7 — Not writing down the requirements**
Keeping requirements in your head and designing from memory. You'll miss them under pressure, and the interviewer can't verify you heard them.
*Fix:* Write 3-5 requirement bullets at the top of your whiteboard/doc before drawing anything.

**#8 — Failing to quantify "at scale"**
"We need this to be fast and scalable" without numbers. Every architectural decision should be anchored to a number.
*Fix:* Do back-of-envelope before drawing the HLD. "1M users × 10 writes/day = 10M writes/day = ~116 writes/sec. One DB can handle this — no sharding needed yet."

**#9 — Treating all users as equal**
Designing a news feed as if all 100M users have the same fan-out cost. Celebrities (1M followers) vs regular users (500 followers) need different treatment.
*Fix:* Identify skewed distributions in the problem. "Power users with >1M followers get pull-based feed; regular users get push-based." Interviewers reward this.

**#10 — Changing requirements mid-design without flagging it**
Quietly designing a different system than the one stated because it's more interesting. The interviewer won't catch it until the end.
*Fix:* If you think the requirements should change, say it explicitly: "I'd push back on the 50ms latency SLA here — at this scale it requires expensive infrastructure. Is 200ms acceptable?"

---

## Category 2: High-Level Design (Mistakes 11–20)

**#11 — "I'd use microservices" with no justification**
Defaulting to microservices regardless of scale or team size. At SDE-3, the expectation is to justify architecture against requirements, not choose the fashionable option.
*Fix:* Justify: "The components have very different scaling profiles — the feed reader is 100× read-heavy versus the write path. That's the reason to separate them. Otherwise I'd keep it as a monolith."

**#12 — Naming AWS services without explaining why**
"I'd put this in DynamoDB" without explaining the data model, key schema, or why DynamoDB fits. Interviewers know when you're hiding behind service names.
*Fix:* For every service named: explain the access pattern, the key design, and why this service's trade-offs match the requirements.

**#13 — Drawing boxes without connections**
A diagram with 8 boxes and no explanation of what flows between them, in what format, with what consistency guarantees.
*Fix:* For every connection: state the protocol (sync HTTP, async Kafka, etc.), the data payload type, and the consistency expectation.

**#14 — One-size-fits-all caching**
Adding "a Redis cache" to every DB call without thinking about invalidation, cache coherence, or whether the data changes often enough to benefit.
*Fix:* State: what you're caching, the cache key, the TTL and why, and the invalidation strategy when data changes.

**#15 — No load balancer, no redundancy**
A single-server design for a system at 1M users with no mention of failure handling. This is the most visible signal of SDE-2 design thinking.
*Fix:* Every stateless tier should have a load balancer. Every stateful tier should have replication. State it explicitly even if you don't draw every detail.

**#16 — Stateful app servers**
App servers that hold in-memory session state, requiring sticky sessions. These can't scale horizontally.
*Fix:* Move all state to an external store (Redis for sessions). App servers are stateless and interchangeable — any server can handle any request.

**#17 — The "and then a miracle occurs" pattern**
Vague components like "the notification service handles delivery" without explaining how: queue or sync call? What if the user's device is offline? What if it fails?
*Fix:* For every component you draw, be able to explain: how it receives work, how it processes it, what it does on failure, and how you'd monitor it.

**#18 — Missing the async/sync decision**
Designing a payment service with synchronous calls to downstream fraud detection (adds 200ms to P99) when async would work. Or using async for a step that requires a result (checkout must know if payment succeeded).
*Fix:* For every inter-service call: state whether the caller needs the result (sync) or just the eventual completion (async). Make the boundary explicit.

**#19 — No data flow for failure cases**
Happy path only. "What if the payment service is down during checkout?" — if you haven't thought about this, you'll be caught off guard.
*Fix:* For every I/O call in your design: state the failure behavior. "Payment fails → we return error to user, no order is created — atomic failure is correct here."

**#20 — Designing by committee**
Hedging every decision: "we could use Kafka, or SQS, or RabbitMQ — it depends." At SDE-3, you're expected to make decisions and defend them.
*Fix:* Pick one, state why, and acknowledge the trade-off of the alternative: "I'd use Kafka because we need replay and multi-consumer fan-out. SQS would be simpler but can't replay."

---

## Category 3: Deep Dives (Mistakes 21–30)

**#21 — "Eventual consistency" without specifying what eventual means**
Saying "we'll use eventual consistency" and stopping there. Interviewers hear: "I know the term but not the implication."
*Fix:* Specify: "Consistent within 2 seconds under normal conditions. Users might see a stale like count briefly — that's acceptable for this workload."

**#22 — Ignoring the write path entirely**
Spending 30 minutes on the read architecture and 2 minutes on writes. Writes are where consistency, ordering, and locking problems live.
*Fix:* Explicitly split time: 40% reads, 40% writes, 20% failure modes. The write path should be as detailed as the read path.

**#23 — Wrong consistency model for the workload**
Using QUORUM reads/writes for a metrics counter (overkill, kills throughput) or using ONE consistency for a bank balance (data loss risk). 
*Fix:* State the consistency model and justify it against the business requirement: "Bank balance requires linearizability — reading a stale balance is incorrect. Metrics dashboard tolerates 30s staleness."

**#24 — Sharding without a hot-key analysis**
Proposing `user_id % N` sharding without asking "is the access distribution uniform?" Hot users (celebrities, viral content) will hammer one shard.
*Fix:* Ask: "Is access uniform or skewed? For celebrities, I'd add a write buffer or shard their data further."

**#25 — Re-inventing the wrong wheel**
Proposing a custom consensus algorithm or custom distributed lock when Raft (via etcd) or Redis Redlock (with caveats) already exist.
*Fix:* Use well-known solutions for well-known problems. Know their limitations. "I'd use Redlock, but it requires a quorum of Redis nodes and has known failure modes under clock drift — for this use case the risk is acceptable because the lock duration is short."

**#26 — Ignoring N+1 query problems**
Designing a feed that loads 50 posts and then fires 50 separate queries for author avatars. At scale this is catastrophic.
*Fix:* Batch queries: "I'd load the 50 posts, extract the unique author IDs, then fetch all avatars in one query. Cache the avatar map in Redis."

**#27 — Single-region thinking**
Designing a system for "global users" as a single AWS region. 200ms transatlantic RTT is invisible in your design.
*Fix:* State the latency budget upfront. If users are global, either: accept cross-region latency and justify it, or add a CDN/edge layer, or design multi-region active-passive.

**#28 — Conflating replication and sharding**
"I'd replicate across 3 nodes for scale." Replication is for fault tolerance and read scale. Sharding is for write scale. Using the wrong solution for the actual bottleneck.
*Fix:* State which problem you're solving: "This is a write bottleneck — I need sharding, not just replication. I'll add replicas for read scale separately."

**#29 — Missing the fan-out problem**
For any social/notification system: not addressing what happens when a user with 10M followers posts something. Naive fan-out = 10M writes synchronously.
*Fix:* Identify the fan-out problem explicitly. "For users with >1M followers, I'd use pull-based feed generation at read time rather than pre-computing. This is the Meta approach."

**#30 — No consideration of data retention and deletion**
Designing a storage system without stating: how long data lives, how old data is expired/archived, and (if user data) how deletion works under GDPR.
*Fix:* For any user-data system: state TTL for each data type, archival strategy, and deletion mechanism. "User posts are retained for 3 years, then moved to cold storage (S3 Glacier). Deletion requests are processed within 30 days."

---

## Category 4: Communication & Framing (Mistakes 31–40)

**#31 — Thinking silently for 3+ minutes**
Long silences while you think on the whiteboard. Interviewers can't assess you when you're silent.
*Fix:* Think out loud: "I'm considering whether to shard by user_id or by post_id. User_id means all of a user's posts land on one shard — easier fan-out but hot user risk. Post_id gives better distribution but makes user feed queries cross-shard. I'll go with user_id because..."

**#32 — Over-explaining the obvious**
Spending 5 minutes explaining what a REST API is, or what a load balancer does. Interviewers are senior engineers — they know the basics.
*Fix:* State decisions without explaining primitives. "I'll add a load balancer" — don't explain what it does. Explain only what's non-obvious: "I'm using consistent hashing here so that adding a node only remaps K/N keys instead of all of them."

**#33 — Never pushing back on the interviewer's "hint"**
When an interviewer says "have you thought about using X?" — accepting it uncritically. Sometimes it's a hint; sometimes it's a test of whether you'll defend your design.
*Fix:* Evaluate the hint: "That's interesting — X would give us Y, but it would cost us Z. Given our requirements, I think my current approach is better because [reason]. But if we needed Y more than Z, X makes sense."

**#34 — Saying "I don't know" and stopping**
When asked about something you're unfamiliar with (e.g., Spanner internals). Stopping cold is worse than reasoning through it.
*Fix:* Say what you do know and reason from there: "I haven't worked with Spanner directly, but I know it achieves external consistency using TrueTime — GPS/atomic clock-backed timestamps that bound clock skew. I'd reason about it as..."

**#35 — Using the wrong vocabulary**
Saying "sharding" when you mean replication, or "partitioning" when you mean both. Imprecise language makes interviewers doubt your depth.
*Fix:* Nail the vocabulary. Sharding = horizontal partition of writes. Replication = copies for fault tolerance/read scale. Partition = generic term. Consistency = agreement. Availability = responses even during failure.

**#36 — Not narrating trade-offs — only decisions**
"I'd use Kafka." Without: "over X, because Y, at the cost of Z."
*Fix:* Every decision = decision + alternative + reason + trade-off. This is the SDE-3 framing pattern.

**#37 — Losing track of the requirements mid-design**
Optimizing for P99 latency when the stated SLO was P50. Forgetting the initial constraints as the design grows.
*Fix:* Write requirements at the top and physically refer back to them when making trade-offs: "Going back to our 100ms P99 requirement — this component adds at most 20ms, leaving 80ms budget for the DB call."

**#38 — Summarizing instead of going deep when asked**
"Any questions?" / "Let's dive deeper on the storage layer." — responding with more high-level overview instead of concrete mechanisms.
*Fix:* When asked to go deep: switch from component-level to mechanism-level. Not "we store data in S3" but "the object is sharded by key hash to one of 1024 storage nodes; the metadata service keeps a consistent hash ring mapping key ranges to nodes."

**#39 — Making it a monologue**
Not checking in with the interviewer. Running for 20 minutes without pausing to see if they're following or want to redirect.
*Fix:* Every 5-7 minutes: "Does that make sense? Any area you'd like me to go deeper on before I move to the next component?"

**#40 — No operational story**
Ending with "and that's my design." SDE-3 interviewers explicitly look for: how do you know the system is healthy? How do you handle the 2am page?
*Fix:* Always close with: "For operations: I'd alert on P99 latency and error rate per component. If the queue depth alert fires, the runbook says to scale out consumers. I'd add request tracing to diagnose slow paths."

---

## Category 5: LLD-Specific (Mistakes 41–50)

**#41 — No thread safety in concurrent designs**
Designing a cache or rate limiter in LLD without mentioning thread safety. The interviewer will always ask — don't make them ask.
*Fix:* Proactively state: "This will be accessed concurrently, so I'll use ConcurrentHashMap instead of HashMap, and CAS operations for the counter."

**#42 — Using synchronized everywhere**
Blanket `synchronized` on every method as a "safe" default. Shows you don't understand the performance implications of global locking.
*Fix:* Use the right synchronization primitive for the access pattern: ReadWriteLock for read-heavy, ConcurrentHashMap for map operations, atomic classes for counters, Semaphore for bounded resources.

**#43 — Infinite thread pools**
`Executors.newCachedThreadPool()` for anything remotely production-like. Unbounded threads = OOM under load.
*Fix:* Always bound: `Executors.newFixedThreadPool(N)` where N is calculated. Show the thread-pool sizing formula for I/O-bound vs CPU-bound.

**#44 — Missing the null/edge cases**
LLD that works for happy path but crashes on empty input, null, concurrent modification, or integer overflow. Interviewers will probe these.
*Fix:* After the happy path, walk through: null inputs, empty collections, max-value integers, concurrent access. State your handling for each.

**#45 — Over-engineering with patterns for simple problems**
Adding Factory, Builder, Command, Observer, and Visitor to a class that needs 3 methods. Design pattern name-dropping without purpose.
*Fix:* Use a pattern only when it solves a specific problem you've identified. State the problem first: "I need to decouple the notification sender from the event source — that's why I'm adding an Observer here." If there's no problem, no pattern needed.

**#46 — Mutable shared state without documentation**
Shared fields modified by multiple classes with no comment on thread safety or ownership. In a design interview this reads as "I didn't think about this."
*Fix:* For every shared mutable field: state who owns it, who can modify it, and what synchronization protects it.

**#47 — No consideration for memory footprint**
Designing an in-memory cache that stores the full JSON blob for 10M objects with no eviction. 10M × 10KB = 100GB of memory that will OOM the process.
*Fix:* State the memory budget upfront. "I'll cap this at 1M entries — with 10KB average size that's 10GB. I'll use LRU eviction to stay within budget."

**#48 — Checked exceptions leaking through interfaces**
Defining an interface method that throws `IOException` when the interface should be abstraction-level agnostic. Callers shouldn't need to know the implementation uses files.
*Fix:* Wrap checked exceptions in domain exceptions at the boundary: `StorageException wraps IOException`. The interface declares `StorageException`.

**#49 — Single Responsibility violation without justification**
A class named `UserManager` that handles authentication, profile management, email sending, and session tracking. When called out, "it's simpler."
*Fix:* Split on responsibility. If you feel resistance, state it: "In a real codebase I'd split these — for this interview I'll keep UserManager and mention where the split would go."

**#50 — No scalability story in LLD**
Designing a rate limiter or LRU cache without mentioning: "and here's how this would change if we needed it distributed / across multiple nodes."
*Fix:* At the end of any LLD: "This is a single-node implementation. For distributed scale: [brief description of the distributed version — e.g., Redis for cache, centralized rate limiter with sharding]."

---

## Pre-Interview Checklist

Before every mock or real interview, verify you won't commit these:

```
Requirements
[ ] Will ask exactly 3 targeted questions, then state assumptions
[ ] Will write requirements on whiteboard before drawing
[ ] Will scope explicitly to what I can cover in 40 min

HLD
[ ] Will do back-of-envelope before drawing
[ ] Will draw connections, not just boxes
[ ] Will state failure behavior for every I/O call
[ ] Will cover both write path and read path

Deep Dive
[ ] Will specify consistency model with user-visible implications
[ ] Will address hot-key / skew distribution
[ ] Will address fan-out for social/notification problems

Communication
[ ] Will narrate every decision as: decision + alternative + reason + trade-off
[ ] Will think out loud, no 3-minute silences
[ ] Will check in every 5-7 minutes
[ ] Will close with monitoring and operations

LLD
[ ] Will state thread safety primitives before being asked
[ ] Will bound all thread pools and caches
[ ] Will walk through edge cases after happy path
```
