> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Step 1 of the LLD process — turning a one-line prompt ("Design a parking lot") into a concrete, bounded requirements list before touching classes or diagrams.
>
> **Key ideas:**
> - Never start designing from the raw prompt; the raw prompt is deliberately underspecified — that's the test.
> - Ask clarifying questions across 5 fixed categories: Scope, Actors, Core Flow, Constraints, Non-functional. Same 5 categories, every problem.
> - Write down the answers as a numbered requirements list before Step 2. This list is your contract — every class you draw later must trace back to a line in it.
> - Silence from the interviewer on a question means "use reasonable judgment, state your assumption out loud."
>
> **Key takeaway:** A requirements list with 8-12 concrete bullet points is the actual deliverable of Step 1. If you can't write that list, you're not ready to draw classes yet — go back and ask more questions.

---
module: 06-lld
topic: Methodology
status: unread
tags: [06-lld, methodology, requirements, clarifying-questions]
---
# Step 1 — Problem Decomposition

Every LLD prompt ("design a parking lot," "design Splitwise") is intentionally underspecified. The interviewer wants to see whether you design blind or whether you scope the problem first. Skipping this step is the single most common reason candidates redesign mid-interview — a constraint surfaces at minute 25 that invalidates the class diagram from minute 10.

---

## The 5 fixed categories

Ask questions from all five, every time, regardless of domain. The specific questions change; the categories don't.

```
[Clarifying Question Categories]
├── 1. Scope
│   ├── Single instance or multiple? (one parking lot, or a chain of them?)
│   ├── What's explicitly out of scope? (payments? reservations? admin panel?)
│   └── Is this a library/API, a service, or a full application?
├── 2. Actors
│   ├── Who/what interacts with the system? (User, Admin, external Payment Gateway)
│   └── Do actors have different permissions or views?
├── 3. Core Flow
│   ├── What's the primary happy-path sequence? (enter → get spot → exit → pay)
│   └── What triggers each step — user action, timer, external event?
├── 4. Constraints & Edge Cases
│   ├── What happens when a resource is exhausted? (no spot available)
│   ├── What are the business rules that vary? (pricing, eligibility, matching rules)
│   └── Are there rules the interviewer says are "fixed" vs "should be pluggable"?
└── 5. Non-functional
    ├── Concurrency — multiple actors acting simultaneously on shared state?
    ├── Scale — rough order of magnitude (affects data structure choice, not just architecture)
    └── Extensibility — what's the "if we add X later" the interviewer hints at?
```

The **non-functional** category is the one candidates skip most and interviewers care about most — it's where Strategy, Observer, and thread-safety requirements come from. A pluggable pricing rule *is* a request for the Strategy pattern, stated as a requirement instead of a pattern name.

---

## Worked mini-example: "Design a ride-sharing service"

**Scope**
- Q: "Single city or multi-region?" → A: "Single city, don't worry about geo-sharding."
- Q: "Are we building rider + driver apps, or just the matching backend?" → A: "Just the matching and trip lifecycle logic."

**Actors**
- Q: "Just Rider and Driver, or is there a Dispatcher/Admin role?" → A: "Rider and Driver only."

**Core Flow**
- Q: "Walk me through one trip end to end?" → A: "Rider requests → system matches nearest available driver → driver accepts/rejects → trip starts → trip ends → fare calculated."

**Constraints**
- Q: "What happens if no driver is available?" → A: "Request times out after N seconds, rider is notified."
- Q: "Is fare calculation fixed or should it support surge pricing later?" → A: "Assume it'll need surge pricing — make it swappable."
- Q: "Can a driver reject a match?" → A: "Yes, and the system should retry with the next-nearest driver."

**Non-functional**
- Q: "How many concurrent ride requests should this handle conceptually?" → A: "Don't worry about literal scale, but the matching should not block on a single global lock."

That "make it swappable" line is a direct signal for Strategy on fare calculation (see [05-spotting-the-pattern.md](05-spotting-the-pattern.md)). The reject-and-retry line tells you `Driver` needs a status/availability state, which becomes a candidate for State pattern if the states get complex enough with transitions and side effects — otherwise a plain enum is enough (see [02-nouns-to-classes.md](02-nouns-to-classes.md) on when NOT to reach for a pattern).

---

## Turning answers into a requirements list

After the Q&A, write it down as a flat, numbered list — this is the actual artifact of Step 1:

1. System matches one rider to one nearest available driver per request.
2. Driver can accept or reject; on reject, system retries with next-nearest driver.
3. Request times out after N seconds with no match; rider is notified.
4. Fare calculation must be pluggable (flat rate now, surge pricing later).
5. Trip has a lifecycle: requested → matched → in-progress → completed / cancelled.
6. Matching must not serialize on a single lock across all requests.
7. Out of scope: payments, multi-city, driver onboarding.

Every class and relationship you introduce in Steps 2–3 should be traceable to one of these lines. If you find yourself adding a class that doesn't map to any line, either you've discovered a missing requirement (go back and state the assumption out loud) or you're over-engineering (see [YAGNI in principles.md](../01-oop-fundamentals/principles.md)).

---

## Interview Angles

- State assumptions explicitly when the interviewer says "use your judgment" — e.g., "I'll assume a rider can only have one active trip at a time; let me know if that's wrong." This is worth more than guessing silently.
- If the interviewer gives you a firm constraint ("just one parking lot"), don't over-generalize past it — a `List<ParkingLot>` when they said "just one" reads as not listening, not as forward-thinking.
- Budget this step to 3-5 minutes of a 45-minute round. Long enough to avoid rework, short enough to leave time for the diagram and code.

**Next:** [02-nouns-to-classes.md](02-nouns-to-classes.md) — turn this requirements list into candidate classes.
