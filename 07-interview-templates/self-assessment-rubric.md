---
module: 07-interview-templates
topic: Timed Self-Assessment Rubric
status: unread
tags: [07-interview-templates, interview, self-assessment, rubric, practice]
---
# Timed Self-Assessment Rubric

> Score yourself after every practice session. Honest scoring is the only way to identify real gaps. Don't average away your 1s.

---

## How to Use

1. Complete a timed practice session (45 min for HLD, 45 min for LLD)
2. Immediately after, score each dimension 1–4 before reviewing any notes
3. Add brief evidence for any score of 1 or 2
4. Track scores across sessions — look for dimensions stuck below 3

**Scoring scale:**
- **4 — Strong hire:** Did this without prompting, clearly, with depth
- **3 — Hire:** Did this adequately; interviewer wouldn't flag it
- **2 — Borderline:** Did this partially or only after being prompted
- **1 — No hire:** Missed this entirely or got it clearly wrong

---

## Part A: HLD Rubric

### A1 — Requirements & Scoping (10 points)

| # | Dimension | 1 | 2 | 3 | 4 | Score |
|---|---|---|---|---|---|---|
| A1.1 | Asked exactly 3 targeted clarifying questions (not more, not fewer) | Asked none or 5+ | Asked 1–2 or generic ones | Asked 3, most were useful | Asked 3 that each changed the design | |
| A1.2 | Stated assumptions explicitly before designing | No assumptions stated | Stated some, missed obvious ones | Stated the main ones | Stated all key ones including non-obvious | |
| A1.3 | Scoped the problem to what's coverable in 45 min | No scoping; tried to cover everything | Implicitly scoped by running out of time | Mentioned scoping verbally | Explicitly scoped upfront and committed to a focus area | |
| A1.4 | Completed back-of-envelope before drawing HLD | No estimation | Did estimation after being asked | Did rough estimation before HLD | Did quantified estimation that drove architectural decisions | |
| A1.5 | Numbers were anchored to the design | No numbers used | Numbers mentioned but not connected to decisions | Numbers present, loosely connected | Every major decision cited a specific number | |

**A1 Total: ___ / 20**

---

### A2 — High-Level Design (20 points)

| # | Dimension | 1 | 2 | 3 | 4 | Score |
|---|---|---|---|---|---|---|
| A2.1 | Covered both write path and read path | Covered only one | Covered both but one was superficial | Both paths covered adequately | Both paths covered with comparable depth | |
| A2.2 | Every connection between components has a protocol and payload | Boxes only, no connections | Some connections labeled | Most connections labeled | Every connection has protocol, payload type, and consistency expectation | |
| A2.3 | State management is explicit (where is state stored, who owns it) | State never mentioned | Some stateful components identified | Most state was located | All state was explicitly located with ownership and access model | |
| A2.4 | Load balancing and redundancy at every stateless tier | No LB/redundancy mentioned | Mentioned "we'd add redundancy" without detail | Load balancers drawn at main tiers | LBs drawn with type (L4/L7) and health check behavior stated | |
| A2.5 | Caching strategy: what to cache, key, TTL, invalidation | No caching discussed | "Add a cache" with no detail | Cache present with key and TTL | Cache with key design, TTL justification, and invalidation strategy | |
| A2.6 | Database selection was justified against access patterns | DB named without reason | DB named with partial reason | DB named with access pattern justification | DB named with access pattern, schema sketch, and alternative considered | |
| A2.7 | Async vs sync decision was explicit for inter-service calls | No distinction made | Some calls labeled | Most calls have sync/async label | All calls labeled with justification | |
| A2.8 | Failure behavior stated for every I/O call | No failure handling | Happy path only; failure mentioned generically | Main failure paths covered | Every I/O call has explicit failure mode and handling | |
| A2.9 | Monitoring and alerting mentioned before being asked | Never mentioned | Mentioned only when prompted | Mentioned at end | Mentioned proactively with specific metrics and alert thresholds | |
| A2.10 | Design was anchored to requirements throughout (referred back to them) | No callbacks to requirements | One or two callbacks | Regular callbacks | Explicitly verified design against each requirement at end | |

**A2 Total: ___ / 40**

---

### A3 — Deep Dive Quality (15 points)

| # | Dimension | 1 | 2 | 3 | 4 | Score |
|---|---|---|---|---|---|---|
| A3.1 | Consistency model was named precisely (not just "eventual consistency") | No consistency discussion | "Eventually consistent" with no elaboration | Named model with user-visible implications | Named model + implications + why alternatives were rejected | |
| A3.2 | Sharding strategy addressed access distribution / hot keys | No sharding discussed | Sharding mentioned without key analysis | Sharding strategy with key design | Strategy + hot-key analysis + mitigation for skewed distribution | |
| A3.3 | Handled fan-out at scale (social/notification problems) | Fan-out not considered | "Push to all followers" with no scale analysis | Identified the fan-out problem, proposed solution | Pull vs push decision with threshold (e.g., 1M followers), hybrid approach | |
| A3.4 | Went deeper than one level when asked ("and how does that work?") | Couldn't go deeper | Went 1 level deeper then stopped | Went 2 levels deep | Went 3+ levels deep; could reason from first principles | |
| A3.5 | Explicitly stated v1 vs v2 scope (what to defer) | No v1/v2 distinction | Vague deferral | Named what to defer with brief reason | Named what to defer, why, and what trigger would cause the upgrade | |

**A3 Total: ___ / 20**

---

### A4 — Communication & Leadership (10 points)

| # | Dimension | 1 | 2 | 3 | 4 | Score |
|---|---|---|---|---|---|---|
| A4.1 | Every decision was framed as: decision + alternative + reason + cost | No framing | Some decisions framed | Most decisions framed | All decisions framed with all 4 elements | |
| A4.2 | Thought out loud (no silences > 30 seconds) | Multiple long silences | 1–2 long silences | Mostly narrated, brief pauses | Continuous narration; every pause filled with "I'm thinking about..." | |
| A4.3 | Checked in with the interviewer every 5–7 minutes | Never checked in | Checked in once at end | Checked in 2x | Checked in regularly, incorporated feedback | |
| A4.4 | Led the structure; didn't wait for the interviewer to drive | Interviewer drove entire session | Interviewer prompted major sections | Self-directed most of the time | Fully self-directed; invited interviewer to redirect, didn't need prompting | |
| A4.5 | Maintained position when challenged; updated when shown new info | Immediately agreed with all challenges | Inconsistently defended | Defended position with reasons | Defended with evidence; updated when shown new data, not just new opinion | |

**A4 Total: ___ / 20**

---

### HLD Total Score: ___ / 100

| Range | Signal |
|---|---|
| 85–100 | Strong SDE-3 signal — ready to interview |
| 70–84 | Hire range — some dimensions need sharpening |
| 55–69 | Borderline — specific weaknesses, 2–3 weeks of targeted practice |
| < 55 | SDE-2 range — fundamental gaps, systematic practice needed |

---

## Part B: LLD Rubric

### B1 — Object Model & Design (15 points)

| # | Dimension | 1 | 2 | 3 | 4 | Score |
|---|---|---|---|---|---|---|
| B1.1 | Identified the core entities and their relationships before coding | Started coding immediately | Some entities identified | Main entities identified with relationships | All entities, relationships, and cardinality stated before writing code | |
| B1.2 | Class responsibilities are well-defined (SRP) | One god class | Multiple responsibilities per class, no awareness | Named responsibilities per class | Responsibilities explicitly stated; would split any class that does >1 thing | |
| B1.3 | Interfaces are used where substitutability is needed | No interfaces | Interfaces added but not leveraged | Interfaces at main extension points | Interfaces everywhere behavior might vary; concrete classes are leaf nodes | |
| B1.4 | Encapsulation: internal state is not leaked | Public fields throughout | Some private fields | Most state private | All state private; public methods expose behavior, not data | |
| B1.5 | Inheritance vs composition decision was conscious | Inheritance used everywhere | Composition sometimes used | Default to composition; inheritance for true is-a | Composition default; inheritance only for behavioral polymorphism with justification | |

**B1 Total: ___ / 20**

---

### B2 — Concurrency (15 points)

| # | Dimension | 1 | 2 | 3 | 4 | Score |
|---|---|---|---|---|---|---|
| B2.1 | Identified all shared mutable state | No shared state identified | Some identified | Most identified | All shared state explicitly flagged with ownership | |
| B2.2 | Used the right synchronization primitive for the access pattern | No synchronization | `synchronized` everywhere | Correct primitives for most cases | Correct primitives for every case with justification (RWLock for read-heavy, Semaphore for bounded pools, CAS for counters) | |
| B2.3 | Thread pool is bounded and sized with reasoning | No thread pool | Unbounded pool | Bounded pool | Bounded pool with formula: N_cpu × (1 + wait/compute) for I/O-bound; N_cpu+1 for CPU-bound | |
| B2.4 | No obvious race conditions in the design | Race conditions present that were not identified | Some race conditions addressed | Main race conditions addressed | All shared-state operations are atomic or locked; no check-then-act races | |
| B2.5 | Deadlock prevention was considered | No mention | Mentioned "we'd need to be careful" | Lock ordering policy stated | Lock ordering defined; or lock-free approach used with justification | |

**B2 Total: ___ / 20**

---

### B3 — Code Quality (15 points)

| # | Dimension | 1 | 2 | 3 | 4 | Score |
|---|---|---|---|---|---|---|
| B3.1 | Method naming clearly expresses intent | Cryptic names | Some clarity | Clear names for most methods | All names are self-documenting; no comments needed to understand intent | |
| B3.2 | Methods are focused (single level of abstraction) | Long methods doing many things | Some long methods | Most methods are focused | All methods < 20 lines; no method mixes abstraction levels | |
| B3.3 | Edge cases handled: null, empty, overflow, concurrent modification | No edge cases | Some edge cases mentioned | Main edge cases handled | All edge cases walked through; each has explicit handling or documented assumption | |
| B3.4 | Error handling is appropriate: exceptions vs return values | Exceptions swallowed or missing | Inconsistent handling | Mostly appropriate | Appropriate strategy throughout: domain exceptions at boundaries, no checked exceptions in interfaces | |
| B3.5 | Memory footprint was considered | No memory consideration | Mentioned "it could use memory" | Bounded collections | All caches bounded; eviction strategy stated; memory estimate given | |

**B3 Total: ___ / 20**

---

### B4 — Extensibility & Scalability (5 points)

| # | Dimension | 1 | 2 | 3 | 4 | Score |
|---|---|---|---|---|---|---|
| B4.1 | Extension points are explicit (OCP) | Design is closed to extension | Some extension points | Main variation axes have extension points | All places where behavior should vary are open for extension; other places are closed | |
| B4.2 | Single-node design is complete; distributed version sketched | No distributed consideration | "This doesn't scale" acknowledged | Distributed version described verbally | Single-node fully working; distributed version described with specific changes (e.g., "replace HashMap with Redis, add consistent hashing") | |

**B4 Total: ___ / 8**

---

### LLD Total Score: ___ / 68

Normalize to /100: multiply by 1.47

| Normalized Range | Signal |
|---|---|
| 85–100 | Strong SDE-3 LLD signal |
| 70–84 | Hire range |
| 55–69 | Borderline |
| < 55 | SDE-2 range |

---

## Session Log Template

Use this after every practice session. Fill it in immediately.

```
Date: ____________
Problem: ____________
Type: [ ] HLD   [ ] LLD
Time taken: ___ / 45 min

HLD Score: ___ / 100    LLD Score: ___ (normalized) / 100

Lowest-scoring dimensions this session:
1. ____________ (score: ___)  Evidence: ______________________________
2. ____________ (score: ___)  Evidence: ______________________________
3. ____________ (score: ___)  Evidence: ______________________________

What I'll do differently next session:
1. ____________
2. ____________

Running weak areas (updated after each session):
[ ] ____________
[ ] ____________
[ ] ____________
```

---

## Progress Tracker

Copy this table and update it after each session to visualize improvement over time.

| Session | Date | Problem | HLD | LLD | Weakest Dimension |
|---|---|---|---|---|---|
| 1 | | | | | |
| 2 | | | | | |
| 3 | | | | | |
| 4 | | | | | |
| 5 | | | | | |
| 6 | | | | | |
| 7 | | | | | |
| 8 | | | | | |
| 9 | | | | | |
| 10 | | | | | |

**Target:** Three consecutive sessions at 85+ on both HLD and LLD before scheduling a real interview.

---

## Dimension Priority Guide

If you're pressed for time, fix dimensions in this order — they have the highest signal weight at FAANG:

| Priority | Dimension | Why it matters most |
|---|---|---|
| 1 | A2.8 — Failure behavior for every I/O | Missing this is the #1 reason for SDE-2 downlevel |
| 2 | A2.1 — Write path depth | Most candidates over-index on read path |
| 3 | A3.1 — Precise consistency model | "Eventual consistency" without specification is a red flag |
| 4 | B2.2 — Right synchronization primitive | `synchronized` everywhere signals LLD shallow thinking |
| 5 | A4.1 — Decision framing | Trade-off articulation is the core SDE-3 communication signal |
| 6 | A1.4 — Estimation before design | Skipping this is immediately visible |
| 7 | A2.9 — Monitoring unprompted | Amazon and Meta explicitly score this |
| 8 | B2.3 — Bounded + sized thread pool | Unbounded pools = immediate LLD red flag |

---

## Calibration Examples

### Score 4 on A2.8 (Failure behavior)
> "If the payment service is down, the checkout call returns a 503. We do not create an order. The client should retry with the same idempotency key. If payment is down for > 30 seconds, we'd fail the checkout flow and show the user an error rather than leaving them in an uncertain state. Separately, the payment service's circuit breaker will open after 5 failures in 10 seconds and return fast failures until the half-open probe succeeds."

### Score 2 on A2.8 (Failure behavior)
> "We'd add retry logic to handle failures."

---

### Score 4 on A3.1 (Consistency model)
> "I'd use read-your-own-writes consistency here. After a user updates their profile, they must see the updated version immediately — but other users can tolerate seeing the old version for up to 5 seconds. I'd achieve this by routing the update author's reads to the primary replica with a session token, and other users' reads to the read replicas with 5-second replication lag. This avoids linearizability overhead for everyone except the author."

### Score 2 on A3.1 (Consistency model)
> "We'd use eventual consistency for this."

---

### Score 4 on B2.2 (Synchronization primitive)
> "The `SearchIndex` has many concurrent readers and occasional bulk index updates. `synchronized` would serialize all reads — unacceptable. I'll use `ReentrantReadWriteLock`: readers acquire `readLock()` which allows concurrent access; the indexer acquires `writeLock()` which is exclusive. For the per-term lock I'll use a `ConcurrentHashMap<String, ReentrantReadWriteLock>` so updates to different terms don't block each other."

### Score 2 on B2.2 (Synchronization primitive)
> "I'll mark the methods as synchronized to prevent race conditions."
