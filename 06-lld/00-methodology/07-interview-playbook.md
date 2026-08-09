> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** How to run the 5-step process live in a 45-minute interview under a clock — time-boxing per step, what to say out loud, and how to answer the follow-up questions interviewers commonly ask at each stage.
>
> **Time budget (45-minute round):**
> - Step 1 (Decompose): 5 min
> - Steps 2-3 (Classes + UML): 12 min
> - Step 4 (Methods/interfaces): 8 min
> - Step 5 (Patterns, woven into Step 4 verbally): included above
> - Code core flow: 15 min
> - Extend/follow-ups: 5 min buffer
>
> **Key takeaway:** The clock punishes silent thinking, not wrong turns. Narrate every decision from the process docs — interviewers are grading whether you have a repeatable method, not whether you reach the exact diagram they had in mind.

---
module: 06-lld
topic: Methodology
status: unread
tags: [06-lld, methodology, interview-strategy]
---
# Step 7 — Interview Playbook

The 5-step process from [00 README](README.md) works untimed. This doc is about running it inside a 45-minute clock without losing time to indecision or silent thinking.

---

## Time-boxed structure

```
[45-Minute LLD Round]
├── 0:00–0:05 — Problem Decomposition (Step 1)
│   └── Ask clarifying questions across all 5 categories; write the
│       requirements list visibly (whiteboard/doc), don't just discuss verbally
├── 0:05–0:17 — Classes + Relationships (Steps 2-3)
│   └── Underline nouns live, classify each, draw boxes+arrows as you go
├── 0:17–0:25 — Methods + Interfaces + Patterns (Steps 4-5)
│   └── Underline verbs, assign methods, name each pattern with its
│       one-sentence justification as you add it
├── 0:25–0:40 — Code the core flow
│   └── Pick the 1-2 methods that are the heart of the problem
│       (assignSpot(), calculateFee(), match()) — code those fully;
│       stub the rest with signatures only
└── 0:40–0:45 — Extend / follow-up questions
    └── "How would this change if we added X?" — answer by re-running
        Step 5's justification check against the new requirement
```

If you're behind schedule at the 20-minute mark, cut scope on the diagram (fewer classes, simpler relationships) rather than skipping straight to code with an unclear model — an incomplete-but-consistent diagram recovers better than code built on a model you'll have to redesign mid-typing.

---

## What to narrate at each step

Silence is the main way this process fails to land, even when the internal reasoning is correct. Say the checklist step you're on, not just the conclusion:

- Step 1: *"Let me ask a few clarifying questions before I start designing — first, scope: is this a single location or multiple?"*
- Step 2: *"I'm underlining the nouns in what we just discussed — Member, Membership, GymClass, Booking. Booking needs its own identity because it carries status and a timestamp, so that's an entity, not just a link."*
- Step 3: *"GymClass owns TimeSlot — if the class session is deleted, the time slot has no independent meaning, so that's composition. Member and Booking is more of a reference — a member exists independent of any one booking — so that's association."*
- Step 4: *"Booking cancels itself and reports back a fee, so I'll put `cancel()` on Booking rather than in an external service, since it's mutating its own status."*
- Step 5: *"The requirement said cancellation fee logic needs to be swappable later, so I'll pull that into a `CancellationFeePolicy` interface — that's the Strategy pattern, justified directly by that requirement."*

---

## Common interviewer follow-ups, and how to answer them

| Follow-up | How to answer |
|---|---|
| "Why did you choose composition here instead of aggregation?" | Restate the lifecycle test from [03-relationships-and-uml.md](03-relationships-and-uml.md): "if the parent is deleted, does the child still make sense independently?" |
| "Why this pattern and not a simpler if-else?" | Point to the specific requirement line that demands runtime/extensible variation — run the justification check from [05-spotting-the-pattern.md](05-spotting-the-pattern.md) live. |
| "How would this scale to 10x traffic?" | This is usually a segue into HLD, not LLD — answer briefly (e.g., "the capacity check would move from an in-memory counter to an atomic DB decrement or a distributed lock") and offer to go deeper if they want, without abandoning the LLD diagram. |
| "What if we needed to support two membership tiers with different booking privileges?" | Re-run Step 5's table: does behavior differ per tier (not just a label)? If yes, that's a live Strategy/State candidate you didn't need before — show you can extend the existing diagram rather than restarting it. |
| "How do you handle the race condition on the last spot?" | Name the specific mechanism (synchronized block, atomic counter, DB-level optimistic lock/unique constraint) — see [concurrency-patterns.md](../04-concurrency/concurrency-patterns.md) and [thread-safe-singleton.md](../04-concurrency/thread-safe-singleton.md) for the vocabulary. |
| "Can you code the X method?" | Pick the method most central to the "tricky part" of the problem (per the difficulty callouts in [05-problems](../05-problems/) solved examples) — that's almost always what they want to see coded, not boilerplate getters. |

---

## Recovering from a wrong turn mid-interview

If a clarifying answer at minute 30 invalidates an earlier decision (e.g., you assumed composition but it turns out the child needs to be shared), don't silently patch it — say so and fix it visibly: *"Given that, I should change this from composition to aggregation — let me update the diagram."* Visibly correcting a model based on new information reads as strength, not failure; it's literally what the job requires day to day.

---

## Interview Angles

- Bring your own structure rather than waiting for the interviewer to prompt each step — proactively saying "let me start by clarifying a few things" signals you have a process, before they even ask.
- If time runs short, protect the diagram over the code — a clean, well-justified class diagram with pseudocode-level method stubs is a stronger finish than fully-typed code built on a rushed, inconsistent model.
- Practice against the drills in [08-practice-drills.md](08-practice-drills.md) with an actual timer — the time-boxing above is a skill that degrades under real pressure if you've only ever practiced untimed.
