> [!NOTE]
> ** 5-Minute Summary**
>
> **What this covers:** How to build a system where microservices react to things happening in real-time, instead of constantly asking "Did anything happen yet?"
>
> **Key topics:**
> - **The Problem:** In standard microservices, Service A has to call Service B. If Service B is offline, Service A gets an error. They are still tightly coupled!
> - **The Solution (Event-Driven):** Service A just shouts into a megaphone: "Something happened!" It doesn't care who is listening. Service B hears it and does its job.
> - **Commands vs Events:** A Command is "Do this right now" (Expects an answer). An Event is "This just happened in the past" (Doesn't care about the answer).
> - **Choreography vs Orchestration:** Do the services just listen and dance on their own (Choreography)? Or is there one central Boss telling everyone exactly what to do step-by-step (Orchestration)?
> - **Event Sourcing:** Instead of saving your current bank balance ($100), the database saves every single transaction you ever made (+$50, -$10, +$60).
>
> **Key takeaway:** Event-Driven Architecture uses tools like Kafka to make microservices truly independent. It is insanely scalable, but very hard to debug when things go wrong.

---
module: 04-advanced-topics
status: unread
tags: [04-advanced-topics, system-design, event-driven, architecture]
---
# Event-Driven Architecture - System Design Guide

> This guide explains how to build reactive systems using simple analogies.

---

## Why Should I Care?

Imagine a strict office (Standard APIs).
The CEO walks to the Accountant's desk and says, "Process this payroll." The CEO stands there and waits. If the Accountant is in the bathroom, the CEO stands there for 10 minutes doing nothing (Blocking).

Now imagine a News Reporter (Event-Driven Architecture).
The News Reporter stands on a corner and shouts into a megaphone: "The stock market just crashed!"
- The Reporter doesn't care who is listening.
- A businessman hears it and starts crying.
- A stockbroker hears it and starts selling.
- A journalist hears it and writes an article.
All three people reacted instantly, in parallel, and the Reporter didn't have to wait for any of them.

In System Design, this is **Event-Driven Architecture (EDA)**.
Instead of the `Order Service` calling the `Email Service` and waiting for a response, the `Order Service` just shouts into Apache Kafka: *"Order #123 was created!"*
The `Email Service` hears it and sends a receipt. The `Inventory Service` hears it and removes a T-shirt from the warehouse.

---

## Commands vs. Events

To understand this architecture, you must understand the difference in how computers talk.

### 1. A Command (Imperative)
- **What it is:** "Update the user's email address to john@gmail.com."
- **How it works:** It is an order. The sender expects a "Success" or "Failure" response. If the receiver is broken, the sender gets an error.

### 2. An Event (Declarative)
- **What it is:** "The user's email address was changed to john@gmail.com."
- **How it works:** It is a fact about the past. The sender just states the fact and walks away. It expects zero response. If the receiver is broken, the sender doesn't care. The receiver can just read the fact tomorrow when it wakes up.

---

## 🩰 Choreography vs. Orchestration (Who is the Boss?)

When a user buys a product, 5 things have to happen (Payment, Inventory, Shipping, Email, Analytics). How do we coordinate this?

### Option 1: Orchestration (The Conductor)
- **How it works:** There is one central "Boss" service.
- The Boss tells Payment to charge the card. Payment replies "Done."
- The Boss tells Inventory to pack the box. Inventory replies "Done."
- **Pros:** It's very easy to see the whole workflow in one place.
- **Cons:** If the Boss dies, nothing happens. The Boss becomes a massive bottleneck.

### Option 2: Choreography (The Flash Mob)
- **How it works:** There is no Boss. Everyone just listens for Events.
- The user clicks Buy. An event fires: *"Order Placed"*.
- Payment hears it, charges the card, and fires an event: *"Payment Successful"*.
- Inventory hears *"Payment Successful"*, packs the box, and fires an event: *"Box Packed"*.
- Shipping hears *"Box Packed"*...
- **Pros:** Insanely fast and scalable. No single point of failure.
- **Cons:** It is incredibly hard to debug. If a box doesn't ship, you have to hunt through 5 different system logs to figure out which dancer missed their cue.

---

## Event Sourcing (The Bank Statement)

Usually, a database saves the *current state* of something.
For example, a traditional database says: `User: John | Bank Balance: $100`.
If John spends $20, the database deletes `$100` and overwrites it with `$80`.

**Event Sourcing** says: Never delete or overwrite data. Only save a list of Events.
- `Event 1: John deposited $100.`
- `Event 2: John spent $20.`
- `Event 3: John received $50.`

If John asks for his balance, the database quickly does the math ($100 - $20 + $50 = $130) and tells him.
**Why do this?** Because it creates a perfect, un-hackable audit trail. If there is a bug, you can "rewind time" by just deleting Event 3 and recalculating the math! (This is how all bank databases work).

---

## Interview Questions to Practice

1. **"What is the difference between Orchestration and Choreography in microservices?"**
   *Answer:* Orchestration relies on a central controller (like an AWS Step Function) to explicitly command other services and manage the workflow state. Choreography relies on services independently reacting to events published to a message broker, with no central controller.
2. **"What is Event Sourcing?"**
   *Answer:* Instead of storing the current state of a domain object in a database, Event Sourcing stores every state-changing action as an immutable sequence of events in an append-only log. The current state is derived by replaying these events from the beginning.
3. **"What is the biggest drawback of Event-Driven Architecture?"**
   *Answer:* Observability and Debugging. Because workflows are spread out asynchronously across multiple disconnected services, tracking a single user request from start to finish requires complex distributed tracing (like Jaeger or Zipkin). If a message fails silently in the middle of a choreography flow, finding the root cause is very difficult.

---

# 🎯 SDE-3 Deep Dive

Commands/events + choreography/orchestration + event sourcing is the intro. Seniors are pushed on **the dual-write problem (and the outbox pattern that solves it), the Saga pattern for distributed transactions, delivery guarantees + idempotency, and the CQRS/event-sourcing operational realities (schema evolution, replay, ordering).**

## The dual-write problem — the flaw in naive EDA

The intro's "Order Service saves to its DB *and* publishes to Kafka" hides the #1 EDA bug: those are **two separate systems with no shared transaction.** If the DB commit succeeds but the Kafka publish fails (or vice versa), state and events diverge — a lost order or a phantom event. You **cannot** wrap a DB and a broker in one atomic transaction.

The fix is the **Transactional Outbox** ([`07-outbox-cdc-pattern.md`](07-outbox-cdc-pattern.md)): write the event to an `outbox` table **in the same DB transaction** as the state change; a separate relay (poller or CDC via [`03-change-data-capture.md`](../../01-foundations/05-advanced-distributed-theory/03-change-data-capture.md)) reads the outbox and publishes to Kafka. Now the event is durable iff the state change committed — atomicity restored. This is the single most important senior EDA pattern.

## Sagas — distributed transactions without 2PC

Choreography across services means no global ACID transaction. A **Saga** is the answer: a sequence of local transactions, each publishing an event that triggers the next; if a step fails, you run **compensating transactions** to semantically undo prior steps (refund the payment, restock the item) — there's no rollback, only forward-fixing.

- **Choreography saga** (events, no coordinator): decoupled but the workflow is *implicit* and hard to trace — you can't see the flow in one place.
- **Orchestration saga** (a central saga orchestrator / state machine, e.g., Temporal, Step Functions): explicit, observable, easier to reason about, at the cost of a coordinator.
- Sagas give **eventual consistency + atomicity-via-compensation**, never isolation — so you must design for intermediate states being visible (an order briefly "pending").

## Delivery guarantees & idempotency — non-negotiable

Brokers deliver **at-least-once** (exactly-once delivery is impossible — two-generals, [`01-distributed-systems.md`](01-distributed-systems.md)), so **consumers WILL see duplicates** (retries, rebalances, redelivery after a crash before offset commit). Therefore:

- **Every consumer must be idempotent** — dedupe on an event/idempotency key, or make the effect naturally idempotent (upsert, conditional write). This is the price of async.
- **Ordering is per-partition only** — Kafka guarantees order within a partition, not across. To preserve per-entity order, **key events by entity ID** so all of one order's events land in one partition. Global ordering is not available at scale.
- **Poison messages** need a **dead-letter queue** + retry policy, or one bad event blocks the partition forever.

## Event sourcing's operational bill

Event sourcing is powerful but the intro undersells the cost:

- **Schema evolution:** events are immutable and kept forever, so a v1 event must be readable years later — you need versioned events and upcasters. You can't just "migrate the column."
- **Replay & snapshots:** rebuilding state by replaying millions of events is slow; you take periodic **snapshots** and replay only the tail.
- **CQRS pairs with it:** the write side appends events; **read models (projections)** are built by consuming the event stream into query-optimized stores. Reads are eventually consistent with writes — a UX consideration (show the user their own action optimistically).
- **GDPR / deletes** fight immutability — "right to be forgotten" vs an append-only log forces crypto-shredding or tombstoning strategies.

## Interview probes you should survive

- *"Order Service saves to Postgres then publishes to Kafka — what breaks?"* → Dual-write: the two aren't atomic; a crash between them loses the event or emits a phantom. Use the transactional outbox (write event + state in one DB txn, relay via CDC).
- *"How do you do a checkout across payment/inventory/shipping without a distributed transaction?"* → Saga: local transactions chained by events, with compensating transactions to undo on failure. Orchestrated if you need observability, choreographed for decoupling.
- *"Your consumer processed the same event twice — how do you prevent double effects?"* → At-least-once delivery is a given; make consumers idempotent (dedupe on event key / conditional upsert).
- *"How do you keep events for one order in order?"* → Key by order ID so they share a partition; Kafka orders within a partition, not across. No cheap global ordering.
- *"What's hard about event sourcing in year 3?"* → Event schema evolution (immutable old events), replay performance (needs snapshots), read-model rebuilds, and reconciling append-only logs with GDPR deletion.

---

## Applied In

This concept is used by **1 problem** in this repo:

**High-Level Design**

- [Design a Notification Service](../../05-hld-problems/02-medium/notification-service.md)

