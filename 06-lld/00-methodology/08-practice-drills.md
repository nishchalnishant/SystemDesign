> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** A timed drill set — problem prompts only, no solutions — to practice running the 5-step process yourself. Self-check by comparing your output against the closest solved problem in [06-problems](../06-problems/), not by reading a provided answer.
>
> **How to use this:** Set a timer per the difficulty tier (20/30/40 min). Produce the actual artifacts — written requirements list, class table, diagram, method table, pattern list with justifications — not just mental notes. Then compare against the pointed-to solved problem and note *where your reasoning diverged and why*, not just whether the final class names matched.
>
> **Key takeaway:** The goal isn't to match the solved problem's diagram exactly — different valid designs exist. The goal is to check whether every class/relationship/pattern in your output traces back to a requirement, same as the process demands.

---
module: 06-lld
topic: Methodology
status: unread
tags: [06-lld, methodology, practice, drills]
---
# Step 8 — Practice Drills

Reading the process is necessary but not sufficient — the skill is running it under time pressure without the scaffolding of a worked example in front of you. These drills are prompts only. Do not open the linked self-check problem until you've produced your own full output.

---

## How to run a drill

1. Set a timer for the tier below.
2. Write the requirements list (Step 1) from the prompt alone — invent reasonable clarifying answers if you're practicing solo, or better, do this with a friend playing interviewer.
3. Produce: class table (Step 2) → diagram with relationships and cardinality (Step 3) → method table with interfaces flagged (Step 4) → pattern list with one-sentence justifications each (Step 5).
4. Stop the timer.
5. Open the self-check link. Compare structurally: did you land on a similar entity set? Did you justify every pattern, and does the solved version's pattern choice match your reasoning or reveal something you missed?

---

## Tier 1 — 20 minutes (fundamentals rep)

Run the full process but keep scope small — these are meant to build speed on Steps 1-3.

1. **Design a Tic-Tac-Toe game** for two players on an NxN board. Self-check: [03-design-tic-tac-toe.md](../06-problems/01-core-problems/03-design-tic-tac-toe.md)
2. **Design a Vending Machine** that dispenses items based on selection and payment, with a defined set of states (idle, selecting, dispensing, out-of-stock). Self-check: [04-design-vending-machine.md](../06-problems/01-core-problems/04-design-vending-machine.md)
3. **Design an ATM** that handles PIN validation, balance inquiry, withdrawal with denomination dispensing, and insufficient-funds/insufficient-cash cases. Self-check: [12-design-atm.md](../06-problems/02-frequent-problems/12-design-atm.md)

---

## Tier 2 — 30 minutes (full process, one clear pattern)

These have a dominant pattern signal — good for practicing Step 5's justification discipline.

4. **Design a Rate Limiter** as a library that can be configured with different limiting algorithms (fixed window, sliding window, token bucket) without the calling code changing. Self-check: [02-design-rate-limiter.md](../06-problems/01-core-problems/02-design-rate-limiter.md)
5. **Design an Elevator System** for a building with multiple elevators and floors, optimizing request handling as elevators move. Self-check: [09-design-elevator-system.md](../06-problems/02-frequent-problems/09-design-elevator-system.md)
6. **Design a Notification System** that can send via email, SMS, or push, with the delivery channel selectable per notification and new channels addable later. Self-check: [16-design-notification-system.md](../06-problems/02-frequent-problems/16-design-notification-system.md)
7. **Design a Logger Library** where log messages pass through a configurable sequence of handlers (e.g., level filter → formatter → sink), each able to stop or forward the message. Self-check: [19-design-logger-library.md](../06-problems/03-domain-specific/19-design-logger-library.md)

---

## Tier 3 — 40 minutes (ambiguous, multi-pattern, concurrency)

Closer to a real onsite round — ambiguous scope, likely 2+ patterns, and a concurrency wrinkle to reason about explicitly.

8. **Design Splitwise** — users split group expenses in various ways (equal, exact amounts, percentages), and the system settles up debts with the minimum number of transactions. Self-check: [05-design-splitwise.md](../06-problems/01-core-problems/05-design-splitwise.md)
9. **Design a Coupon/Discount System** where multiple coupons can apply to an order, some stack and some are mutually exclusive, and eligibility rules vary per coupon type. Self-check: [17-design-coupon-system.md](../06-problems/02-frequent-problems/17-design-coupon-system.md)
10. **Design a Ride-Sharing Service** matching riders to nearby available drivers, with trip lifecycle and swappable fare calculation, under concurrent ride requests. Self-check: [22-design-ride-sharing.md](../06-problems/03-domain-specific/22-design-ride-sharing.md)
11. **Design a Concurrent LRU Cache** as a thread-safe library used by multiple callers simultaneously, supporting O(1) get/put. Self-check: [35-design-concurrent-lru-cache.md](../06-problems/04-advanced-niche/35-design-concurrent-lru-cache.md)

---

## Tier 4 — no self-check (generate your own requirements from scratch)

No solved problem exists in this repo for these — good for a final confidence check that the process transfers to genuinely unseen prompts. Since there's no answer key, self-check by re-reading your own output against the [Pattern Justification Check](05-spotting-the-pattern.md#the-discipline-half-dont-force-fit) and the [Method Ownership Test](04-verbs-to-methods-and-interfaces.md#the-mechanical-pass) — does every class, relationship, and pattern trace back to a requirement line?

12. **Design a food-ordering kitchen display system** — orders come in from multiple channels (dine-in, delivery apps), get routed to the correct kitchen station, and station staff mark items ready; the restaurant wants to add new order channels later without touching kitchen routing logic.
13. **Design a document version-control mini-system** (not full Git) — documents have revisions, users can view diffs between any two revisions, and roll back to a prior revision.
14. **Design a multi-sport scoreboard system** — supports different sports (basketball, soccer, tennis) each with different scoring rules and different "game over" conditions, displayed through one common scoreboard UI.

---

## After the drill: the questions that actually matter

Don't just check "did I get the same class names." Ask:

- Did I skip Step 1 and start naming classes immediately? (Common under time pressure — resist it, it costs more time later than it saves.)
- For every pattern I added, can I still state the one-sentence justification without looking back at my notes?
- Did I add a class or interface the solved version didn't need? Was that over-engineering, or a genuinely different valid interpretation of ambiguous scope?
- Did the solved version use a pattern I missed? Re-read the relevant row in [05-spotting-the-pattern.md](05-spotting-the-pattern.md) — was the triggering phrase in the requirements something I under-weighted?

**This is the end of the methodology sequence.** Return to [00 README](README.md) for the full map, or go straight to [06-problems](../06-problems/) for more solved reference material once drills feel fast.
