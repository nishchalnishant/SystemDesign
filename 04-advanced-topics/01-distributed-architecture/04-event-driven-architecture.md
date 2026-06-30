> [!NOTE]
> **📋 5-Minute Summary**
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

## 🤷‍♂️ Why Should I Care?

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

## 🗣️ Commands vs. Events

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

## 📼 Event Sourcing (The Bank Statement)

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

## 🎤 Interview Questions to Practice

1. **"What is the difference between Orchestration and Choreography in microservices?"**
   *Answer:* Orchestration relies on a central controller (like an AWS Step Function) to explicitly command other services and manage the workflow state. Choreography relies on services independently reacting to events published to a message broker, with no central controller.
2. **"What is Event Sourcing?"**
   *Answer:* Instead of storing the current state of a domain object in a database, Event Sourcing stores every state-changing action as an immutable sequence of events in an append-only log. The current state is derived by replaying these events from the beginning.
3. **"What is the biggest drawback of Event-Driven Architecture?"**
   *Answer:* Observability and Debugging. Because workflows are spread out asynchronously across multiple disconnected services, tracking a single user request from start to finish requires complex distributed tracing (like Jaeger or Zipkin). If a message fails silently in the middle of a choreography flow, finding the root cause is very difficult.
