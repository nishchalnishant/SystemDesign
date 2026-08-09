> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** A timed drill set — problem prompts only, no solutions — to practice running Steps 1-5 yourself. Self-check by comparing your output against the closest solved problem in [05-hld-problems](../), not by reading a provided answer.
>
> **How to use this:** Set a timer per the difficulty tier. Produce the actual artifacts — written requirements list, capacity numbers, API table, architecture diagram, one real deep dive — not just mental notes. Then compare against the pointed-to solved problem and note *where your reasoning diverged and why*, not just whether the boxes matched.
>
> **Key takeaway:** The goal isn't to match the solved problem's diagram exactly — different valid architectures exist at the same scale. The goal is to check whether every box and every deep-dive claim in your output traces back to a requirement or a number, same as the process demands.

---
module: 05-hld-problems
topic: Methodology
status: unread
tags: [05-hld-problems, methodology, practice, drills]
---
# Step 8 — Practice Drills

Reading the process is necessary but not sufficient — the skill is running it under time pressure without a worked example in front of you. These drills are prompts only. Do not open the linked self-check problem until you've produced your own full output.

---

## How to run a drill

1. Set a timer for the tier below.
2. Write the requirements list (Step 1) from the prompt alone — invent reasonable clarifying answers if practicing solo, or better, do this with a friend playing interviewer.
3. Produce, in order: capacity numbers (Step 2) → API table + data model with SQL/NoSQL decision (Step 3) → architecture diagram with every box justified (Step 4) → one real deep dive (Step 5).
4. Stop the timer.
5. Open the self-check link. Compare structurally: did you land on a similar tier of architecture? Did your deep dive pick the same bottleneck, and if not, was theirs more stressed by the numbers than yours?

---

## Tier 1 — 25 minutes (fundamentals rep)

Run the full process but keep scope small — these build speed on Steps 1-3 and a single clean Step 4 signal.

1. **Design a URL Shortener.** Self-check: [url-shortener.md](../01-easy/url-shortener.md)
2. **Design a Pastebin-like service.** Self-check: [pastebin.md](../01-easy/pastebin.md)
3. **Design a Rate Limiter as a hosted service** (not the LLD library version — the HLD version: where does it sit in the request path, how does it scale). Self-check: [rate-limiter.md](../01-easy/rate-limiter.md)
4. **Design a Unique ID Generator** for a distributed system generating IDs at high throughput with no central bottleneck. Self-check: [unique-id-generator.md](../01-easy/unique-id-generator.md)

---

## Tier 2 — 40 minutes (full process, one clear architectural theme)

These have a dominant scaling theme — good for practicing Step 4's signal-table discipline and Step 5's target selection.

5. **Design a Key-Value Store** as a distributed, horizontally-scalable service. Self-check: [key-value-store.md](../01-easy/key-value-store.md)
6. **Design a Leaderboard** updated in real time for a game with millions of concurrent players. Self-check: [leaderboard.md](../01-easy/leaderboard.md)
7. **Design a Notification Service** that delivers via email, SMS, and push at high volume with delivery guarantees. Self-check: [notification-service.md](../02-medium/notification-service.md)
8. **Design a Typeahead/Autocomplete Search** system with sub-100ms latency at scale. Self-check: [typeahead-search.md](../02-medium/typeahead-search.md)

---

## Tier 3 — 50-60 minutes (ambiguous, multi-component, real bottleneck)

Closer to a real onsite round — several Step 4 rows fire at once, and picking the right Step 5 target matters.

9. **Design Twitter's News Feed** (fan-out-on-write vs. fan-out-on-read, celebrity-follower skew). Self-check: [twitter-news-feed.md](../02-medium/twitter-news-feed.md)
10. **Design a Chat System** (WhatsApp-like) with delivery/read receipts and offline message delivery. Self-check: [chat-system.md](../03-hard/chat-system.md)
11. **Design a Ride-Sharing Service** matching riders to nearby drivers in real time. Self-check: [ride-sharing.md](../03-hard/ride-sharing.md)
12. **Design a Distributed Cache** as an infrastructure-level service other teams build on. Self-check: [distributed-cache.md](../03-hard/distributed-cache.md)
13. **Design an Ad Click Aggregator** processing a high-volume click stream into near-real-time aggregated counts. Self-check: [ad-click-aggregator.md](../03-hard/ad-click-aggregator.md)

---

## Tier 4 — no self-check (generate your own requirements from scratch)

No solved problem exists in this repo for these — good for a final confidence check that the process transfers to genuinely unseen prompts. Since there's no answer key, self-check against the [signal table](04-deriving-the-architecture.md#the-signal-table) and the [staff-signal four ingredients](../../07-interview-templates/01-frameworks/05-staff-signal-convention.md) — does every box trace to a requirement/number, and does your deep dive hit mechanism + failure mode + tradeoff + rejected alternative?

14. **Design a group expense-splitting service** (Splitwise-like) at 50M users — settlement computation, notification fan-out on new expenses, and consistency requirements around balances.
15. **Design a flight/train seat availability and booking system** — high read concurrency on seat maps, strict consistency required at booking time to prevent double-booking, low write volume relative to reads.
16. **Design a live-polling/audience-Q&A system** (Slido-like) — thousands of concurrent participants per event, real-time result updates, short bursty event-driven load rather than steady-state traffic.

---

## After the drill: the questions that actually matter

Don't just check "did I draw the same boxes." Ask:

- Did I skip Step 2 and jump straight to architecture? (Common under time pressure — resist it; an undefended diagram costs more time in follow-up questions than the math would have taken.)
- For every box I drew, can I still state its one-sentence justification without looking back at my notes?
- Did I pick the same Step 5 target as the solved version? If not, was mine actually less stressed by the numbers, or did I just default to a "favorite topic"?
- Did the solved version surface a bottleneck or tradeoff I didn't think of? Re-read the relevant row in [04-deriving-the-architecture.md](04-deriving-the-architecture.md) — was the triggering signal in the requirements something I under-weighted?

**This is the end of the methodology sequence.** Return to [00 README](README.md) for the full map, or go straight to [01-easy](../01-easy/), [02-medium](../02-medium/), [03-hard](../03-hard/) for more solved reference material once drills feel fast.
