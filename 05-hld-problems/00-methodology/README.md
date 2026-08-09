> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** The process for *originating* an HLD architecture from an unfamiliar prompt — not the reference material (building blocks, templates, decision trees already exist elsewhere in this repo), but the missing middle step: how to go from "design X" to "here are my components, here's why each one exists, here's the one part I need to go deep on."
>
> **Why this folder exists:** Knowing what a message queue, a cache, or consistent hashing *is* doesn't tell you *when a fresh problem needs one*. That's a derivation skill, not a recall skill, and it's the one 40 solved problems and a glossary can't teach by themselves — you can only get it by running the derivation yourself, repeatedly, until it's automatic.
>
> **Reading order:**
> 1. [01-requirements-and-scope.md](01-requirements-and-scope.md) — turning a vague prompt into a bounded, numbered requirements list
> 2. [02-capacity-estimation.md](02-capacity-estimation.md) — the math that decides which architecture tier you're even in
> 3. [03-api-and-data-model.md](03-api-and-data-model.md) — contracts and schema, derived from requirements, before any boxes are drawn
> 4. [04-deriving-the-architecture.md](04-deriving-the-architecture.md) — the core derivation: requirement/number → component, mechanically
> 5. [05-identifying-the-bottleneck.md](05-identifying-the-bottleneck.md) — picking the one thing worth a deep dive, and going deep on it
> 6. [06-worked-example-end-to-end.md](06-worked-example-end-to-end.md) — all 5 steps run on one fresh prompt not solved elsewhere in this repo
> 7. [07-interview-playbook.md](07-interview-playbook.md) — running this under a 45-60 minute clock
> 8. [08-practice-drills.md](08-practice-drills.md) — timed prompts, no solutions, self-check against `05-hld-problems/`
>
> **Key takeaway:** Every box in your final diagram should trace back to a specific requirement or a specific number from capacity estimation. If you can't say which one, you added it from memory of a similar problem, not from this problem's actual constraints — and an interviewer probing "why do you need that" will expose the gap immediately.

---
module: 05-hld-problems
topic: Methodology
status: unread
tags: [05-hld-problems, methodology, hld, framework]
---
# HLD Methodology — From Prompt to Architecture

## The problem this solves

You've read the building blocks (`02-building-blocks/`), you know the decision trees (`08-reference/decision-trees.md`), you've studied 40 solved problems. Given "design Uber," you can recognize the pieces once you see them laid out. Given a prompt you haven't seen before — something adjacent but not identical to anything in `05-hld-problems/` — you freeze, or you reach for the nearest memorized problem and force-fit its architecture, and the interviewer notices the seams immediately.

The gap is the same one the LLD side of this repo already names in [06-lld/00-methodology](../../06-lld/00-methodology/README.md): reference material teaches recognition, not derivation. This folder teaches derivation.

## Why memorizing 40 solved problems doesn't transfer

Each solved problem in `05-hld-problems/` is the *output* of a derivation you didn't see happen — the requirements were already chosen, the numbers already computed, the components already selected. Studying outputs teaches you to recognize "oh, this is a Twitter-shaped problem" when the prompt looks like Twitter. It teaches nothing about what to do when the prompt is Twitter-shaped in some ways and not others, which is what actually happens in an interview room, because interviewers deliberately pick prompts that are close-but-not-identical to famous systems specifically to see whether you're deriving or recalling.

## The process (one page version)

```
[HLD Derivation Process]
│
├─ 1. Requirements & Scope
│   └─ Ask across 4 fixed categories (functional, scale, non-functional,
│      explicit non-goals). Produce a numbered list. Nothing below
│      this line should contradict something above it.
│
├─ 2. Capacity Estimation
│   └─ QPS, storage, bandwidth, memory — from the numbers in Step 1.
│      This is not decoration. The numbers decide which tier of
│      architecture you're allowed to propose (see 04-architecture-by-scale.md).
│
├─ 3. API & Data Model
│   └─ Define the contract before the boxes. Each endpoint and each
│      field should trace to a Step 1 requirement.
│
├─ 4. Deriving the Architecture
│   └─ Mechanical pass: for each requirement + each capacity number,
│      ask "what component does this force?" Draw the box only when
│      you can answer that question, not before.
│
└─ 5. Identify the Bottleneck, Go Deep
    └─ Of everything you drew, pick the ONE piece under the most
       stress (from Step 2's numbers) and solve it in real detail.
       This is where staff signal is actually produced — see
       05-staff-signal-convention.md.
```

Steps 1-3 are mostly mechanical and fast. Step 4 is where most of the actual design thinking happens. Step 5 is where most of the actual interview score is decided — see [05-identifying-the-bottleneck.md](05-identifying-the-bottleneck.md) for why depth beats breadth at SDE-3.

---

## How this folder relates to the rest of the repo

| This folder teaches... | The reference material lives in... |
|---|---|
| *How* to derive components from requirements | [02-building-blocks/](../../02-building-blocks/) — what each component is and how it works internally |
| *How* to decide between two valid technology choices | [08-reference/decision-trees.md](../../08-reference/decision-trees.md) — the trees themselves, once you know you're facing a choice |
| *How* to time-box the whole thing live | [07-interview-templates/01-frameworks/01-hld-template.md](../../07-interview-templates/01-frameworks/01-hld-template.md) — the 6-phase timeline this folder's Step 7 builds on |
| *How* to produce a staff-level deep dive, not just an architecture | [07-interview-templates/01-frameworks/05-staff-signal-convention.md](../../07-interview-templates/01-frameworks/05-staff-signal-convention.md) |
| *What* the finished output looks like, for 40 different prompts | [05-hld-problems/01-easy, 02-medium, 03-hard](../) |

Use this folder first if you can already explain what consistent hashing or a message queue does, but can't decide *whether this specific problem needs one* without seeing it in a solved example first.

---

## Routing table

| You are... | Read... |
|---|---|
| Totally new to HLD interviews | Start at [01-requirements-and-scope.md](01-requirements-and-scope.md) and go in order |
| Comfortable with requirements/math, but boxes still feel arbitrary | Jump to [04-deriving-the-architecture.md](04-deriving-the-architecture.md) |
| Can draw a diagram but don't know what to go deep on | Jump to [05-identifying-the-bottleneck.md](05-identifying-the-bottleneck.md) |
| Want to see the whole process run once, start to finish | [06-worked-example-end-to-end.md](06-worked-example-end-to-end.md) |
| About to practice a new problem | [08-practice-drills.md](08-practice-drills.md), then self-check against the matching entry in `05-hld-problems/` |
| Interview is in the next few days | [07-interview-playbook.md](07-interview-playbook.md) for the time-boxed run-through |

---

## Interview Angles

- If an interviewer asks "why did you add that component," the correct answer always cites either a Step 1 requirement or a Step 2 number — "because Twitter has one" is not an answer that survives follow-up.
- Deliberately practice on prompts adjacent-but-different from the 40 solved problems (that's what [08-practice-drills.md](08-practice-drills.md) is for) — practicing only on prompts you'll recognize verbatim doesn't train the derivation muscle the interview actually tests.

**Next:** [01-requirements-and-scope.md](01-requirements-and-scope.md)
