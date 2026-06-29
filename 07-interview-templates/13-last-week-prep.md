---
module: 07-interview-templates
topic: Last Week Prep Plan
status: unread
tags: [07-interview-templates, amazon, prep, schedule]
---
# Last Week Before the Amazon Interview

One week out. Stop learning new things. Everything below is review and drilling.

---

## Day-by-Day Plan

### Day 7 (7 days out) — HLD Framework
- Read `07-interview-templates/hld-template.md` end to end
- Read `07-interview-templates/amazon-hld-guide.md` end to end
- Do one timed 45-min HLD problem from scratch: **URL Shortener**
- Grade yourself against the scoring rubric in `hld-template.md`

### Day 6 — Building Blocks
- Read all of `02-building-blocks/` (11 files — ~2 hours)
- For each: state in one sentence what it is, when you'd use it, and one trade-off
- Focus extra time on: `consistent-hashing.md`, `message-brokers.md`, `rate-limiting.md`

### Day 5 — HLD Problems (Tier 1)
Do two timed 45-min problems:
1. **Rate Limiter** (`05-hld-problems/01-easy/rate-limiter.md`)
2. **Notification Service** (`05-hld-problems/02-medium/notification-service.md`)

After each: immediately review what you missed, check the failure handling section.

### Day 4 — LLD Framework + Problems
- Read `06-lld/00-interview-strategy.md`
- Do two timed 45-min LLD problems from memory (no notes):
  1. **Parking Lot**
  2. **Vending Machine**
- Check: did you name patterns and justify them? Did you write the critical section?

### Day 3 — HLD Problems (Tier 2) + Capacity Estimation
- Do two timed 45-min problems:
  1. **E-Commerce Platform** (`05-hld-problems/02-medium/e-commerce-platform.md`)
  2. **WhatsApp** (`05-hld-problems/02-medium/whatsapp.md`)
- Drill capacity estimation: `07-interview-templates/capacity-estimation.md`
- Practice doing estimates out loud in under 5 minutes for: Twitter, YouTube, Uber

### Day 2 — LLD Problems (Tier 1) + Concurrency
- Do two timed 45-min LLD problems:
  1. **BookMyShow** (focus: concurrent seat locking)
  2. **ATM** (focus: state machine + atomic withdraw)
- Review `06-lld/04-concurrency/locks-semaphores.md`
- Write thread-safe singleton from memory without looking

### Day 1 (day before) — Light Review Only
- Re-read `08-reference/numbers-to-know.md` (latency numbers)
- Re-read `07-interview-templates/amazon-hld-guide.md` (failure handling section)
- Re-read `07-interview-templates/interview-question-bank.md` — pick 10 random questions, answer each out loud
- Sleep. Stop at 6pm.

---

## The 10 Things to Know Cold

If you can answer all 10 without hesitation, you're ready.

1. **Capacity math**: Given 10M DAU, 50 actions/day — what's average QPS? Peak QPS? (→ ~5,800 avg, ~17,000–58,000 peak)
2. **Consistent hashing**: Why use it? What problem does it solve vs modulo hashing?
3. **Rate limiting**: Token bucket vs sliding window — when does each break down?
4. **Fan-out on write vs read**: Which for feeds? What breaks at celebrity scale?
5. **Idempotency**: How do you prevent double-charge on retry? What's the implementation?
6. **Cache invalidation**: Write-through vs cache-aside vs write-behind — trade-offs?
7. **DB choice**: When DynamoDB over RDS? What do you give up?
8. **Message queue**: SQS vs Kafka — when does each win?
9. **Seat/inventory locking**: How do you prevent double-booking without killing throughput?
10. **Thread-safe singleton**: Write double-checked locking from memory in Python.

---

## LLD Problems to Know Cold (draw class diagram in < 5 min)

- Parking Lot
- Vending Machine  
- BookMyShow
- ATM
- Rate Limiter

---

## HLD Problems to Know Cold (sketch architecture in < 10 min)

- URL Shortener
- Rate Limiter
- Notification System
- Feed / News Feed (Twitter)
- E-Commerce with inventory

---

## On the Day

**Before the interview:**
- Glance at `08-reference/numbers-to-know.md` — just the latency table
- Glance at `07-interview-templates/amazon-hld-guide.md` — just the failure handling section

**In the interview:**
1. Repeat the problem back before asking clarifying questions
2. Estimate scale before drawing anything
3. State every trade-off when you make a choice — don't wait to be asked
4. When asked to deep-dive, stop drawing and go deep — don't resist the redirect
5. Mention failure handling proactively for every stateful component

**If you blank:**
→ Say "let me think about the failure modes here" — buys 30 seconds and signals the right instinct.
