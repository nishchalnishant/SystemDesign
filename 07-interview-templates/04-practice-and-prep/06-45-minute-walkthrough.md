> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** A minute-by-minute pacing rubric for a 45-minute system-design round. Interviewers fail SDE-3 candidates on *pacing and depth allocation* far more often than on missing knowledge — you run out of time in the requirements weeds and never reach the deep dive where the senior signal lives.
>
> **The five phases (45 min):**
> - **0–5 Scope & requirements** — bound the problem, don't design yet.
> - **5–10 Estimation** — one number that drives one architecture decision.
> - **10–20 High-level design** — the boxes, the happy path, end to end.
> - **20–35 Deep dive** — the 15 minutes that decide your level.
> - **35–45 Bottlenecks, tradeoffs, wrap** — you volunteer the weaknesses.
>
> **Key takeaway:** Budget deliberately. If you're still gathering requirements at minute 12, you will not pass. The deep dive is where E5 vs E6 is decided — protect it.

---
module: 07-interview-templates
topic: 45-Minute Interview Walkthrough
status: unread
tags: [07-interview-templates, interview, pacing, timing, sde-3]
---
# The 45-Minute Walkthrough

> **The most common SDE-3 failure isn't a wrong answer — it's spending 20 minutes on requirements and 3 minutes on the deep dive. Depth allocation is a skill. Rehearse the clock.**

---

## The Clock at a Glance

| Time | Phase | What you produce | What you must NOT do |
|---|---|---|---|
| **0–5** | Scope & requirements | Functional + non-functional reqs, one clear scope cut | Start drawing architecture |
| **5–10** | Estimation | 2–3 numbers, each tied to a decision | Estimate for its own sake |
| **10–20** | High-level design | End-to-end happy path, labeled boxes | Optimize prematurely |
| **20–35** | Deep dive | 1–2 components in real depth | Stay shallow across everything |
| **35–45** | Bottlenecks & wrap | SPOFs, tradeoffs, "what I'd do with more time" | Discover a fatal flaw with 1 min left |

---

## 0–5 min — Scope & Requirements

**Goal:** agree on *what* you're building before *how*. Bound it out loud.

- **Functional:** the 3–5 core operations. ("Post a tweet, view a feed, follow a user." Not: DMs, ads, search — explicitly park those.)
- **Non-functional:** the ones that actually change the design — scale, latency target, consistency needs, availability. ("Feed loads < 200ms, eventual consistency OK, read-heavy 100:1.")
- **One scope cut:** name what you're *excluding* and why. This shows judgment and buys you time.

> **Pacing trap:** the biggest time sink is over-clarifying. Ask 3–4 sharp questions, state your assumptions, and move. "I'll assume 100M DAU and read-heavy; correct me if that's off."

**Say this to transition:** *"That's my scope. Let me size it before I design."*

---

## 5–10 min — Estimation

**Goal:** produce numbers that *drive a decision*, not numbers for a table.

- **Traffic:** DAU → QPS (average, then ×3–5 peak). Read:write ratio.
- **Storage:** per-item size × volume × time × replication factor. Land on a per-year figure.
- **Bandwidth or a bottleneck number** if relevant (e.g., video egress).

Each number must end in *"...therefore..."*:
- "230 writes/s avg but 1,150/s peak, **therefore** a single primary is fine and I don't need sharding yet."
- "36 PB/year, **therefore** cold data tiers to object storage — it can't all live on SSD."

> **Pacing trap:** don't derive six numbers. Two or three that each unlock an architecture choice. If a number doesn't change your design, don't compute it.

**Say this to transition:** *"So the design has to handle ~1k writes/s peak and tier storage. Here's the architecture."*

---

## 10–20 min — High-Level Design

**Goal:** a labeled, end-to-end happy path. Client → load balancer → services → data stores. Draw it, then *walk one request through it start to finish*.

- Start simple and correct. A working monolith-ish diagram beats a half-drawn microservices mesh.
- Label every arrow with what flows and why (sync/async, protocol).
- Cover the write path AND the read path — most designs live or die on the read path.
- Name your data store choices *with a one-line reason* as you place them.

> **Pacing trap:** premature optimization. Don't add Kafka, CDN, and 4 caches before the basic flow is on the board. Get it working, then let the deep dive add sophistication.

**Say this to transition:** *"That's the end-to-end flow. The interesting part is [X] — let me go deep there."* — and let the interviewer redirect if they care about a different component.

---

## 20–35 min — The Deep Dive (this decides your level)

**Goal:** demonstrate mechanical depth on 1–2 components. This is 15 of your 45 minutes — a third of the interview. Protect it ruthlessly.

Pick the component where the *hard* problem lives — usually:
- The consistency/concurrency hotspot (booking, payment, counter).
- The scale bottleneck (feed fan-out, hot key, celebrity problem).
- The data model that makes or breaks the query pattern.

For your chosen component, hit the SDE-3 checklist:
1. **The specific mechanism** — not "use a cache" but "cache-aside, 30s TTL + jitter, single-flight on miss."
2. **The failure mode** — what breaks and how you detect/handle it.
3. **The tradeoff, quantified** — "this costs one extra DB round trip per key per TTL; acceptable because..."
4. **The alternative you rejected** — "I considered write-through but the write latency doubled."

> **This is the E5→E6 line.** Breadth ("I'd also add monitoring, and a CDN, and...") is SDE-2 hedging. Depth on the one thing that's actually hard is the senior signal. When in doubt, go **deeper on one thing** rather than **wider across many.**

**Say this if the interviewer is quiet:** *"There are two hard parts here — [A] and [B]. Which would you like me to go deeper on?"*

---

## 35–45 min — Bottlenecks, Tradeoffs & Wrap

**Goal:** show you can critique your own design before the interviewer does.

- **Volunteer the SPOF.** "The coordinator is my single point of failure — I'd make it HA with a tested failover." Naming it first is a strong signal.
- **Name the bottleneck that appears at 10× scale** and how you'd address it.
- **State one tradeoff you consciously made** and the condition under which you'd flip it. ("I chose eventual consistency; if this were financial data I'd switch to quorum reads and eat the latency.")
- **"With more time I'd..."** — 2–3 items (monitoring, DR, the scope you cut). Shows you know it's not done.

> **Pacing trap:** discovering a fatal flaw at minute 44. Do a mental "does the happy path actually work end to end?" pass around minute 33 so surprises surface while you still have time to fix them.

---

## Failure Modes by Phase (self-diagnosis)

| If you… | You'll be scored as | Fix |
|---|---|---|
| Still clarifying at minute 12 | Can't prioritize | Cap requirements at 5 min, state assumptions, move |
| Estimate for 10 min with no decisions | Ritualistic, not analytical | Every number ends in "therefore..." |
| Draw for 20 min, no deep dive | SDE-2 breadth, no depth | Hard-stop the HLD at minute 20 |
| Stay shallow across 6 components | Hedging | Go deep on 1–2; say "the rest is standard" |
| Never mention a failure mode | Happy-path-only thinker | Volunteer one SPOF + one bottleneck |

---

## Tier-Specific Timing Notes

- **Easy (URL shortener, rate limiter):** the "design" is quick — spend *more* of the deep-dive budget on scale/edge cases, because there's less architecture to draw.
- **Medium (Instagram, WhatsApp):** classic split — the fan-out / feed / delivery mechanism is your deep dive.
- **Hard (payments, stock exchange, ticketmaster):** you may not finish the HLD. That's expected — go deep on the consistency-critical path early; don't try to cover everything.

---

## Applied In

- [HLD Template](../01-frameworks/01-hld-template.md) — the section-by-section structure to fill in each phase
- [Mock Interview Problems](./03-mock-interview-problems.md) — run these against this clock with a real timer
- [SDE-3 Rapid-Recall Bank](./05-sde3-rapid-recall-bank.md) — the deep-dive interrupts you'll face in the 20–35 window
