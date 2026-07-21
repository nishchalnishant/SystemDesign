---
module: 09-patterns
topic: Retries, Backoff, and Idempotency
status: unread
tags: [09-patterns, system-design, resiliency, idempotency, retries]
---
# Retries, Backoff, and Idempotency

> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** How to safely retry a failed request without making things worse — and how to make retries safe to begin with.
>
> **Key concepts:**
> - **Retries fix transient failures**, not broken systems. Retrying a genuinely-down service just adds load.
> - **Exponential backoff:** Wait 1s, 2s, 4s, 8s between attempts instead of hammering immediately.
> - **Jitter:** Add randomness to the wait. Without it, every retrying client fires at the same instant and you get a *thundering herd* — the single most common wrong answer in interviews.
> - **Idempotency:** An operation you can safely run twice. Because "the request timed out" never tells you whether the work actually happened.
> - **Idempotency keys:** The client sends a unique ID; the server remembers what it already did with that ID and replays the same answer.
>
> **Key takeaway:** Retries and idempotency are one topic, not two. Any retry you add is a duplicate-execution bug unless the operation on the other end is idempotent. Say both in the same breath and you sound senior.

---

## 🤷‍♂️ Why Should I Care?

Your service calls the Payment Service. You get back... nothing. The connection times out after 30 seconds.

Here is the uncomfortable question: **did the customer get charged?**

You genuinely cannot tell. There are two possibilities and they look identical from where you're standing:

1. The request never arrived. Nothing happened. You *must* retry or the order is lost.
2. The request arrived, the card was charged, and the *response* got lost on the way back. If you retry, you charge them twice.

This ambiguity is permanent. No amount of clever networking removes it — it is a fundamental property of distributed systems. You cannot know, so you must design so that **it doesn't matter**.

That is what idempotency buys you: the freedom to retry without knowing.

---

## 🔁 Part 1: Retrying Correctly

### Retry only what's worth retrying

Not every failure deserves a second attempt.

| Failure | Retry? | Why |
|---------|--------|-----|
| Connection timeout, `503`, `429` | ✅ Yes | Transient. The service may be fine in 2 seconds. |
| `500` from a downstream bug | ⚠️ Maybe once | If it's a deterministic bug, retrying gets the same crash. |
| `400 Bad Request`, `401`, `404` | ❌ Never | Your request is wrong. It will be wrong the second time too. |
| Payment declined (insufficient funds) | ❌ Never | A business outcome, not a failure. Retrying is harassment. |

> **💡 The rule:** Retry *infrastructure* failures. Never retry *business* rejections.

### The naive retry (and why it's dangerous)

```python
for attempt in range(3):
    try:
        return call_service()
    except TimeoutError:
        continue          # immediately try again
```

This is worse than not retrying. The downstream service is already struggling — that's *why* it timed out — and you just tripled your traffic against it. This is how a small blip becomes a full outage.

### Exponential backoff

Wait longer after each failure, giving the downstream service room to recover:

```
attempt 1 → fail → wait 1s
attempt 2 → fail → wait 2s
attempt 3 → fail → wait 4s
attempt 4 → fail → wait 8s   → give up
```

The formula is `wait = base * (2 ** attempt)`, usually with a ceiling (`max_backoff`) so you don't end up waiting 17 minutes.

### Jitter — the part everyone forgets

Exponential backoff alone still has a fatal flaw.

Picture 10,000 clients all talking to a service that goes down for 1 second. All 10,000 fail *at the same moment*. All 10,000 back off *by exactly the same amount*. All 10,000 retry *at exactly the same instant*.

You have built a synchronized battering ram. The service comes back up, gets hit by 10,000 simultaneous requests, and dies again — and now they're synchronized even harder for the next round.

> **💡 Analogy:** A whole classroom is told "wait 5 minutes, then ask your question." In five minutes, 30 hands go up at once and the teacher is overwhelmed. Instead: "wait somewhere between 0 and 5 minutes." Now the questions trickle in and get answered.

**Jitter** adds randomness so the herd spreads out:

```python
import random

# Full jitter (AWS's recommendation — the one to quote)
wait = random.uniform(0, min(max_backoff, base * (2 ** attempt)))

# Equal jitter — half fixed, half random. Smoother, less spread.
temp = min(max_backoff, base * (2 ** attempt))
wait = temp / 2 + random.uniform(0, temp / 2)
```

| Strategy | Behaviour |
|----------|-----------|
| No jitter | Synchronized retry storms. Never use in production. |
| Full jitter | Best load spreading. The default choice. |
| Equal jitter | Guarantees some minimum wait; slightly worse spreading. |

> **🎤 Interview note:** Saying "retry with exponential backoff" is a *mid-level* answer. Saying "exponential backoff **with jitter**, because otherwise every client retries in lockstep and you get a thundering herd" is the senior one. It takes four extra words.

### Retries and circuit breakers work together

They solve adjacent problems and belong in the same conversation:

- **Retries** handle a request that failed by accident.
- **[Circuit breakers](../../02-building-blocks/02-performance/03-circuit-breaker.md)** handle a service that is failing *consistently* — stop retrying entirely and fail fast.

Retries without a circuit breaker will keep pounding a dead service until it can never recover. Put the retry logic *inside* the breaker: when the breaker is OPEN, don't even attempt the call.

### Retry budgets

Even with backoff and jitter, retries multiply load exactly when the system is least able to absorb it. A **retry budget** caps the damage: allow retries to be at most ~10% of total requests. Past that, fail immediately. This stops a retry storm from becoming a self-inflicted DDoS.

---

## 🔑 Part 2: Idempotency

An operation is **idempotent** if running it five times leaves the system in the same state as running it once.

```
GET /users/123          → idempotent (reading changes nothing)
PUT /users/123 {age:30} → idempotent (setting to 30 twice = 30)
DELETE /users/123       → idempotent (deleted twice = still deleted)
POST /charges {$50}     → NOT idempotent (charged twice = $100) ⚠️
```

That last one is the problem. And it's the one that involves money.

### Idempotency keys

The client generates a unique ID for the *operation* (not the request) and sends it along. If it retries, it sends **the same key**.

```http
POST /v1/charges
Idempotency-Key: 8f14e45f-ea2b-4c1e-9d3a-7b6f0c2e1a99

{ "amount": 5000, "currency": "usd", "customer": "cus_123" }
```

Server-side logic:

```python
def charge(key, request):
    existing = store.get(key)

    if existing:
        if existing.status == "IN_PROGRESS":
            raise Conflict(409)          # a retry arrived mid-flight
        return existing.response         # replay the original answer

    store.put(key, status="IN_PROGRESS")  # atomic insert; unique index on key
    try:
        response = do_the_actual_charge(request)
        store.put(key, status="DONE", response=response)
        return response
    except Exception:
        store.delete(key)                # allow a genuine retry
        raise
```

The critical detail: **inserting the key must be atomic** — a unique constraint in the database, or `SET NX` in Redis. If two retries arrive simultaneously, exactly one wins the insert and the other gets the `409`. Checking-then-inserting in two steps is a race condition that will charge someone twice.

### Design decisions worth naming

| Question | Answer |
|----------|--------|
| Who generates the key? | The **client**. A server-generated key changes on every retry, defeating the purpose. |
| How long to store keys? | 24h is typical (Stripe's window). Long enough to outlive any retry, short enough to bound storage. |
| What if the same key arrives with a *different* body? | Reject with `422`. The client has a bug — it's reusing a key for a different operation. |
| Where to store them? | Redis for speed, or the same DB as the operation so the key and the work commit in one transaction. |

### Making things naturally idempotent

Idempotency keys are the general tool, but sometimes you can restructure so the problem disappears:

- **Use a deterministic ID.** Instead of "create an order and assign an ID", have the client supply the order ID. A duplicate insert then fails on the primary key — harmless.
- **Prefer absolute over relative.** `SET balance = 100` is idempotent; `balance = balance + 50` is not.
- **Use conditional updates.** `UPDATE orders SET status='SHIPPED' WHERE id=1 AND status='PENDING'` — the second execution matches zero rows and does nothing.
- **Deduplicate on a natural key.** A message queue consumer can record processed `message_id`s and skip repeats. (This is what "exactly-once processing" actually means in practice: at-least-once delivery plus idempotent consumers.)

---

## ⚠️ Where This Bites You

- **Retry without idempotency** → duplicate charges, duplicate orders, duplicate emails. The classic production incident.
- **Idempotency without atomic key insertion** → a race between two retries defeats the whole mechanism.
- **Backoff without jitter** → synchronized thundering herd; the service can never get back up.
- **Retrying business failures** → a declined card retried 5 times may trigger the bank's fraud detection and lock the customer's account.
- **Retries at every layer** → client retries 3×, gateway retries 3×, service retries 3× = 27 calls downstream. Retry at **one** layer, usually the outermost.

---

## 🎤 Interview Questions to Practice

1. **"Your payment request times out. What do you do?"**
   *Answer:* I can't tell whether the charge succeeded, so I retry — but only safely. The client sends an idempotency key with the original request and reuses it on retry. The Payment Service checks the key: if it already processed it, it replays the stored response instead of charging again. The retry itself uses exponential backoff with jitter, wrapped in a circuit breaker so I stop entirely if the service is consistently down.

2. **"Why isn't exponential backoff enough on its own?"**
   *Answer:* Because all failing clients back off by the same amount and retry in lockstep, creating a thundering herd that re-kills the service the moment it recovers. Jitter randomizes each client's wait so the load spreads out. Full jitter — a random wait between 0 and the computed backoff — gives the best distribution.

3. **"How do you make `POST /orders` idempotent?"**
   *Answer:* The client generates a UUID as an idempotency key. The server atomically inserts it with a unique constraint before doing work — if the insert fails, this is a duplicate, so return the stored response. Alternatively, let the client supply the order ID so a duplicate becomes a primary-key collision. Keys expire after ~24 hours.

4. **"What's the difference between at-least-once and exactly-once delivery?"**
   *Answer:* Exactly-once delivery over an unreliable network is impossible. What systems actually provide is at-least-once delivery plus idempotent consumers, which produces exactly-once *effects*. Kafka's "exactly-once semantics" works this way — idempotent producers with sequence numbers, plus transactional offset commits.

5. **"Where would you put the retry logic — client, gateway, or service?"**
   *Answer:* One layer only, ideally the outermost caller. Retries at multiple layers multiply: 3 layers of 3 retries is 27 downstream calls from a single user request. Inner layers should fail fast and let the outer layer decide.

---

## Related

**Builds on**

- [Circuit Breakers](../../02-building-blocks/02-performance/03-circuit-breaker.md) — stop retrying a service that is consistently down
- [Rate Limiting](../../02-building-blocks/02-performance/02-rate-limiting.md) — the server-side counterpart to a retry budget
- [Bulkhead Pattern](02-bulkhead-pattern.md) — isolate resources so one slow dependency can't exhaust your threads
- [Saga Pattern](../01-data-consistency/03-saga-pattern.md) — compensating transactions depend on idempotent steps
- [Outbox Pattern](../01-data-consistency/01-outbox-pattern.md) — guarantees at-least-once publishing, which requires idempotent consumers

**Applied in**

- [Payment System](../../05-hld-problems/03-hard/payment-system.md) — idempotency keys preventing double charges
- [Distributed Job Scheduler](../../05-hld-problems/03-hard/distributed-job-scheduler.md) — retry and failure handling for job execution
- [Notification Service](../../05-hld-problems/02-medium/notification-service.md) — at-least-once delivery with deduplication
- [Distributed Message Queue](../../05-hld-problems/03-hard/distributed-message-queue.md) — consumer-side deduplication

**Frameworks**: [API Design Template](../../07-interview-templates/01-frameworks/03-api-design-template.md) · [Failure Recovery Playbook](../../07-interview-templates/03-pitfalls-and-recovery/02-failure-recovery-playbook.md) · [Anti-Patterns](../03-migration-and-pitfalls/02-anti-patterns.md)
