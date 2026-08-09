> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Running Steps 1-5 under a real 45-60 minute clock, with time budgets per phase, pacing checkpoints, and recovery moves for when you're behind.
>
> **Key ideas:**
> - This doc doesn't re-teach the phases — [01-hld-template.md](../../07-interview-templates/01-frameworks/01-hld-template.md) already has the timeline and phase content. This doc adds the pacing discipline specific to running the *derivation* process (Steps 1-5) inside that timeline.
> - The single most common failure isn't missing knowledge, it's time mismanagement — over-spending on requirements or the diagram and leaving no time for the one deep dive that actually produces staff signal.
> - Build in a mid-interview checkpoint: at the 25-30 minute mark, you should have a diagram with justified boxes. If you don't, cut scope, don't cut the deep dive.
>
> **Key takeaway:** A finished, well-justified architecture with no deep dive scores lower than a slightly rougher architecture with one real deep dive — protect the last 10-15 minutes at all costs.

---
module: 05-hld-problems
topic: Methodology
status: unread
tags: [05-hld-problems, methodology, interview-playbook, timing]
---
# Step 7 — Interview Playbook

The individual steps ([1](01-requirements-and-scope.md) through [5](05-identifying-the-bottleneck.md)) are each straightforward alone. The actual interview adds a constraint none of them address individually: a clock. This step is about running all five under that clock without the most common failure — spending so long getting the diagram "right" that the deep dive gets rushed or skipped.

This builds directly on [01-hld-template.md](../../07-interview-templates/01-frameworks/01-hld-template.md)'s 6-phase timeline — read that first if you haven't. What follows is the pacing discipline for running the derivation process specifically inside those phases.

---

## Time budget, mapped to the 5 steps

```
[45-Minute Run]
├── 0-8 min   → Step 1: Requirements & Scope
│               Target: numbered list, all 4 categories touched
├── 8-13 min  → Step 2: Capacity Estimation
│               Target: QPS/storage/bandwidth numbers, tier identified
├── 13-20 min → Step 3: API & Data Model
│               Target: endpoint table + entity list, SQL/NoSQL decided
├── 20-32 min → Step 4: Deriving the Architecture
│               Target: full diagram, every box justified out loud
└── 32-45 min → Step 5: Identify the Bottleneck, Go Deep
                Target: ONE component, full staff-signal-shaped answer
```

For a 60-minute slot, add the extra 15 minutes to Step 5 (a second, shorter deep dive) — never to Steps 1-3, which shouldn't expand just because time exists.

---

## Checkpoints and recovery moves

```
[Checkpoint @ ~13 min]
└── Do I have a numbered requirements list AND computed numbers?
    NO → you're behind. Cut remaining requirements questions short:
         state an assumption and move on rather than continuing to ask.

[Checkpoint @ ~20 min]
└── Do I have an API table and a data model with SQL/NoSQL decided?
    NO → you're behind. Skip full schema detail (just name the
         entities and the storage choice), move to the diagram.

[Checkpoint @ ~32 min]
└── Do I have a diagram where every box has a stated justification?
    NO → STOP adding boxes. A diagram with 4 well-justified boxes and
         no 5th "for completeness" box beats a diagram with 7 boxes
         where the last 3 are unjustified. Move to Step 5 regardless.

[Checkpoint @ ~40 min, if given 45 min total]
└── Have I stated mechanism + failure mode + tradeoff for my chosen
    bottleneck? If not, this is the last checkpoint — cut the
    remaining polish and finish the deep dive's tradeoff sentence,
    even if it means skipping a summary at the end.
```

The recovery move is always the same shape: cut scope in the *earlier* phase, never cut time from Step 5. An interview that ends with "we ran out of time before the deep dive" reads as SDE-2 regardless of how clean the diagram was.

---

## What each phase should sound like, out loud

- **Step 1:** "Let me ask a few questions across functional scope, scale, constraints, and consistency before I start drawing anything."
- **Step 2:** "Given those numbers, that's about 1,500 QPS peak and low-TB storage — so I'm thinking [tier], not [heavier tier]."
- **Step 3:** "Here's the API — each of these maps to one of the requirements we listed. Given the access pattern, I'd lean [SQL/NoSQL] because [reason tied to Step 3's actual entities]."
- **Step 4:** "Walking through the diagram left to right: [component] because [requirement/number]; [component] because [requirement/number]..."
- **Step 5:** "Of everything here, I think the real risk is [component], because [numbers]. Let me go deep on that." — then the full mechanism/failure-mode/tradeoff answer.

Narrating the *step you're in*, not just the content, is itself part of the signal — it shows the interviewer you're running a process, not improvising.

---

## Common mistakes

- **Treating all 45 minutes as equally elastic.** Requirements and API design are the phases most likely to overrun because they feel comfortable (you're asking/answering, not committing to hard tradeoffs) — this is exactly why they need the hardest time discipline.
- **Redrawing the diagram repeatedly for tidiness.** A messy-but-complete diagram at minute 30 beats a beautifully redrawn one at minute 38 that ate 8 minutes from the deep dive.
- **Picking the deep-dive target only after the clock is already tight.** Decide it as soon as the diagram is done — don't spend deep-dive time also deciding what to deep-dive on.
- **Ending on the diagram with no closing statement.** A short closing line (see [01-hld-template.md](../../07-interview-templates/01-frameworks/01-hld-template.md)'s example) that restates the core tradeoff costs 15 seconds and meaningfully improves how the whole answer is remembered.

---

## Interview Angles

- If the interviewer redirects you mid-phase (e.g., "let's skip ahead to architecture"), follow it immediately — protecting your own plan over their explicit steer is a bigger problem than any time lost.
- If you finish Step 4 early, don't pad it with more boxes — move straight into Step 5 and use the extra time there instead.
- Practicing with a visible timer (see [08-practice-drills.md](08-practice-drills.md)) is the only way this pacing becomes automatic — reading this doc once won't build the instinct for when you're behind.

**Next:** [08-practice-drills.md](08-practice-drills.md) — timed prompts, no solutions, self-check against `05-hld-problems/`.
