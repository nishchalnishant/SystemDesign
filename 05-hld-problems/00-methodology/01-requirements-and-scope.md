> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Step 1 — turning a one-line prompt ("design Twitter") into a bounded, numbered requirements list across 4 fixed categories, before any architecture discussion starts.
>
> **Key ideas:**
> - Ask across 4 categories every time: Functional, Scale/Non-functional numbers, Constraints & explicit non-goals, Consistency expectations. Don't free-associate questions — a fixed checklist prevents the panic-freeze on an unfamiliar prompt.
> - Explicit non-goals are as valuable as functional requirements — "we will NOT support X" is what lets you justify a smaller architecture later without it reading as a gap.
> - Stop asking once you have enough to start the math (Step 2) — 5-8 functional requirements is normal; interviewers penalize both under-asking and over-asking (using all your time on questions).
>
> **Key takeaway:** Everything you design later must trace back to a line in this list. If you can't point to which requirement justified a component, you're about to over-engineer or under-justify it.

---
module: 05-hld-problems
topic: Methodology
status: unread
tags: [05-hld-problems, methodology, requirements, scoping]
---
# Step 1 — Requirements & Scope

The prompt you get ("design Twitter," "design a ride-sharing app") is deliberately underspecified — that's not the interviewer being lazy, it's the first thing being tested. Real systems don't ship with a spec; someone has to produce one. This step is that production.

---

## The 4 fixed categories

```
[Requirements Extraction — ask across all 4, every time]
├── 1. Functional — "what must a user be able to DO"
│   └── List actions as verbs: post a tweet, follow a user, view a feed.
│       Stop at 5-8 core actions — more than that means you haven't
│       found the actual scope boundary yet.
├── 2. Scale & Non-Functional Numbers — "what are the actual numbers"
│   └── DAU/MAU, read:write ratio, average payload size, growth rate.
│       These numbers feed Step 2 directly — don't skip to
│       "it should be fast and scalable," get digits.
├── 3. Constraints & Explicit Non-Goals — "what are we NOT building"
│   └── Single region or multi-region? Real-time or eventual-consistency
│       OK? Explicitly excluded features (e.g., "no video, just text
│       and images" for a Twitter clone). This is what lets you defend
│       a smaller design later.
└── 4. Consistency & Availability Expectations
    └── Can a user see their own post immediately (read-your-writes)?
        Is it OK if a follower sees it 2 seconds late? Is downtime
        ever acceptable (batch/analytics systems) or never (payments)?
        This single answer often decides SQL vs NoSQL before you've
        drawn anything.
```

Ask these as questions to the interviewer, or answer them yourself if practicing solo — but always touch all 4 categories. Skipping category 3 or 4 is the most common miss; candidates default to functional-only questions and end up guessing at consistency requirements mid-design.

---

## Worked mini-example: designing a "read-it-later" bookmarking service

**Prompt:** "Design a Pocket/Instapaper-like service — users save article URLs to read later, offline."

- Q (Functional): "What are the core actions?" → A: "Save a URL, list saved articles, mark as read, view the article content even if the original site goes down."
- Q (Functional): "Does 'view offline' mean we store a full copy of the page content?" → A: "Yes — fetch and store a cleaned/readable version at save time."
- Q (Scale): "How many users, how many saves per user per day?" → A: "10M users, ~5 saves/user/day."
- Q (Scale): "Average article size after cleaning?" → A: "~50 KB text + images, assume 200 KB average with images."
- Q (Constraints): "Multi-device sync required?" → A: "Yes, read state must sync across devices."
- Q (Constraints): "Out of scope?" → A: "No social features (sharing, comments), no full-text search across saved articles for v1."
- Q (Consistency): "If I mark an article read on my phone, does it need to show read on my laptop instantly, or is a few seconds of lag OK?" → A: "A few seconds is fine — eventual consistency OK for read-state sync."

**Requirements list (the actual deliverable):**
1. User saves a URL; system fetches and stores a cleaned, readable copy of the content.
2. User lists their saved articles (paginated, most-recent-first).
3. User marks an article as read/unread; state syncs across the user's devices within a few seconds (eventual consistency acceptable).
4. User can view article content offline-first (from the stored copy, not a live fetch of the original site).
5. Scale: 10M users, ~50M saves/day, ~200 KB average stored size per article.
6. Out of scope: sharing, comments, full-text search across articles (v1).
7. Multi-region: not specified — ask, or state the assumption ("assuming single-region for v1, note the multi-region extension in the deep dive").

---

## Common mistakes

- **Jumping to architecture during requirements-gathering.** If you catch yourself saying "so we'd probably use Kafka for—" while still in this step, stop — that belongs in Step 4, and saying it early signals pattern-matching, not derivation.
- **Only asking functional questions.** A design with no consistency requirement stated is a design where you silently guessed CP or AP — and guessed wrong half the time.
- **Not writing anything down.** A verbal-only Q&A that never becomes a numbered list is hard for both you and the interviewer to hold — write the list visibly.
- **Over-asking.** Spending 15 of your 45 minutes on requirements when the prompt was reasonably clear is its own failure mode — 5-8 minutes is normal for an easy/medium problem.

---

## Interview Angles

- Narrate the category you're in: "Let me ask a few scale questions before we get into architecture" signals structure, the same way it does in the [LLD problem decomposition step](../../06-lld/00-methodology/01-problem-decomposition.md).
- If the interviewer doesn't answer a question precisely (common — they may not have a number in mind), state a reasonable assumption out loud and move on: "I'll assume 10M DAU unless you say otherwise" — indecision here burns more time than a slightly-wrong assumption you can revise later.
- Explicit non-goals are a defensive tool: when asked later "what about search," you want to be able to say "we scoped that out in requirements" rather than realizing mid-diagram that you're missing something.

**Next:** [02-capacity-estimation.md](02-capacity-estimation.md) — turn the numbers from category 2 into QPS, storage, and bandwidth figures that decide your architecture tier.
