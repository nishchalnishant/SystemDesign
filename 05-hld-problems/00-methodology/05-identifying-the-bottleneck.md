> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Step 5 — how to pick the ONE component from your Step 4 diagram worth a real deep dive, and how to produce a staff-level answer on it instead of a correct-but-shallow one.
>
> **Key ideas:**
> - Don't go deep on everything — you don't have time, and breadth-over-depth is the SDE-2 pattern. Pick one, based on where your own numbers say the stress actually is.
> - The bottleneck is usually wherever Step 2's numbers are most extreme relative to a single machine/instance's realistic capacity — highest QPS, largest storage growth rate, or the component with the least slack.
> - A deep dive is not "explain how the component works" — it's the [🎯 Staff signal](../../07-interview-templates/01-frameworks/05-staff-signal-convention.md) shape: mechanism, failure mode, quantified tradeoff, rejected alternative.
> - If the interviewer picks the deep-dive target instead of you, that's fine — the same discipline applies, just applied to their chosen component instead of your own pick.
>
> **Key takeaway:** The architecture diagram gets you to "competent." The one deep dive, done with real mechanism and tradeoffs, is what actually moves the needle to "hire at SDE-3."

---
module: 05-hld-problems
topic: Methodology
status: unread
tags: [05-hld-problems, methodology, deep-dive, staff-signal]
---
# Step 5 — Identifying the Bottleneck, Go Deep

You now have a diagram ([Step 4](04-deriving-the-architecture.md)) where every box is justified. Most candidates stop here and start narrating each box at the same shallow depth — that's the single biggest reason a technically-correct design still reads as SDE-2. This step is about spending your remaining time on exactly one component, in real depth.

---

## Picking the target

```
[Bottleneck Selection]
├── Look back at Step 2's numbers. Which component in your diagram
│   is under the most relative stress?
│   ├── Highest QPS hitting a single logical component
│   ├── Fastest-growing storage (will need resharding/repartitioning soonest)
│   ├── The component with a hard consistency requirement AND high
│   │   write volume (these two together are where real systems break)
│   └── The component you drew but are least sure would actually
│       survive its own numbers — pick that one; hand-waving it is
│       the biggest risk in your design
└── If the interviewer names the target instead, drop your own pick —
    answer theirs with the same depth
```

Do not pick the "impressive-sounding" component (e.g., consistent hashing) just because it's a good story — pick the one your own numbers actually stress. If you can't justify *why* this one over the others, you haven't actually found the bottleneck yet, you've picked a favorite topic.

---

## What "going deep" actually means

Going deep is not re-explaining what the component is. It's answering, out loud, in order:

1. **What exactly breaks, mechanically, under this system's specific load** (not a generic failure mode — this problem's numbers).
2. **The specific mechanism you'd use to prevent or bound it** (name the actual technique — "single-flight," "consistent hashing with virtual nodes," "optimistic concurrency with a version column" — not the category).
3. **The quantified tradeoff** — a cost this mechanism introduces, stated with a number or concrete consequence.
4. **The alternative you rejected, and the condition that would flip your choice.**

This is exactly the [🎯 Staff signal](../../07-interview-templates/01-frameworks/05-staff-signal-convention.md) shape — read that doc's "four ingredients" section before your next drill if this feels abstract.

---

## Worked mini-example: continuing the read-it-later service

**Candidates from the Step 4 diagram:** Load balancer, cache, primary DB + replicas, object storage, message queue + workers.

**Picking the target:** The fetch-and-clean worker pool is the least obviously-safe piece — it calls arbitrary external URLs (slow, sometimes-down, sometimes-malicious origin servers), at ~500 enqueues/sec average with bursts. That's more operationally hazardous than the cache or LB, which are well-trodden patterns. Pick the worker pool.

**Shallow answer (SDE-2 shape):** "We'll have a pool of workers that pull jobs off the queue, fetch the URL, clean the HTML, and store it."

**Deep answer (SDE-3 shape):**
> "The risk here isn't throughput, it's that a single slow or hanging origin server can tie up a worker indefinitely — with no timeout, a few thousand saves pointing at slow sites could starve the whole pool. So: a hard per-fetch timeout (say 10s), and any job exceeding it gets retried with backoff, capped at 3 attempts, then moved to a dead-letter queue rather than retried forever. I'd also cap concurrent fetches *per origin domain*, not just globally — otherwise one viral link saved by thousands of users at once turns into a self-inflicted DoS against that one site, and possibly gets our fetcher IP-banned, which is worse than a slow job. The tradeoff: per-domain caps mean a burst of saves for one site queues up rather than processing in parallel, so worst-case latency-to-availability for that specific domain's articles goes up — acceptable here because 'saved instantly' isn't a requirement (Step 1 only requires eventual availability offline), but I'd flag it if this were a real-time requirement instead."

Notice: mechanism (timeout + backoff + DLQ + per-domain concurrency cap), failure mode (hung worker, self-DoS on target domain), quantified tradeoff (queuing delay for bursty single-domain saves), and an explicit tie back to a Step 1 requirement (eventual availability, not real-time) justifying why the tradeoff is acceptable.

---

## Common mistakes

- **Going deep on the component you know best, not the one your own numbers stress.** This is the "favorite topic" trap — interviewers notice when the depth doesn't correlate with your own stated bottleneck.
- **Spreading the same shallow explanation across every box instead of concentrating time.** Five boxes explained at the same surface depth score lower than four boxes named quickly plus one real deep dive.
- **Deep-diving on mechanism only, skipping the tradeoff.** "We'd use consistent hashing" without naming what it costs (rebalancing complexity, virtual node tuning) is still shallow, just wearing a fancier vocabulary.
- **Not connecting the tradeoff back to a Step 1 requirement.** "I accept this tradeoff because [requirement X] said Y" is what makes the tradeoff a judgment call instead of a guess.

---

## Interview Angles

- Say your selection reasoning out loud before diving in: "given the numbers, I think the riskiest piece here is the worker pool, not the cache — let me go deep there" — this itself is a signal, independent of the deep dive's content.
- Volunteer the weakness in your own chosen component before being asked — see the "meta-signal: self-critique" section of the [staff-signal convention doc](../../07-interview-templates/01-frameworks/05-staff-signal-convention.md).
- If time allows a second, shorter deep dive, do it — but never at the cost of shortening the first one below real depth. One real deep dive beats two shallow ones.

**Next:** [06-worked-example-end-to-end.md](06-worked-example-end-to-end.md) — all 5 steps run on one fresh prompt not solved elsewhere in this repo.
