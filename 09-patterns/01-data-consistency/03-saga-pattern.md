---
module: 09-patterns
status: unread
tags: [09-patterns, system-design, patterns]
---
# Saga Pattern

> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** How to manage complex workflows that span across multiple microservices without freezing up the whole system.
>
> **Key concepts:**
> - **Local Transactions:** A Saga is just a sequence of normal, local database saves. Service A saves its data, then tells Service B to do its work.
> - **Compensating Transactions:** If a step fails (e.g., Service C fails), you cannot "undo" Service A and B like a traditional database rollback. You must execute *compensating* actions (like issuing a refund in Service A).
> - **Choreography:** Services just listen to each other's events and react on their own. Good for simple flows (2-4 steps).
> - **Orchestration:** A central controller explicitly tells each service what to do and handles the rollback logic. Good for complex flows.
>
> **Key takeaway:** Sagas embrace "eventual consistency." They are essential for long-running business processes where locking data across multiple databases (like in Two-Phase Commit) would cause massive traffic jams.

---

## 🤷‍♂️ Why Should I Care?

Imagine an e-commerce order that touches three services: Order, Inventory, and Payment. 

1. **Order Service** saves the order as "PENDING".
2. **Inventory Service** successfully reserves the item.
3. **Payment Service** tries to charge the credit card, but it gets declined (insufficient funds).

What do you do now? The Inventory Service has already locked up an item that will never be sold. Since these are separate microservices with separate databases, you can't just yell "ROLLBACK!" to the database. 

If you try to use **Two-Phase Commit (2PC)**, every service freezes and waits for the others. If one service slows down, your entire e-commerce platform grinds to a halt.

**The Saga Pattern** is the modern solution. It says: let each service save its data independently and quickly. If something fails later down the line, we will run a new action to fix the mistake (like a refund). No freezing, no waiting.

---

## ✈️ The Vacation Booking Analogy

To understand Sagas, imagine booking a complex vacation:
1. You book a flight.
2. You book a hotel.
3. You book a rental car.

These are three completely separate companies. You cannot freeze Delta Airlines, Hilton, and Hertz at the exact same moment to guarantee they all have availability. 

Instead, you do it sequentially (a **Saga**). You book the flight. It is locked in. Then you book the hotel. It is locked in. Then you try to book the car, but there are no cars left. 

Because you cannot "database rollback" the real world, you must execute **Compensating Transactions**. You call Hilton and cancel the hotel (which might cost a $5 fee). Then you call Delta and cancel the flight. A Compensating Transaction is a *forward-moving action* that semantically undoes what you just did.

---

## Pattern Mindmap

```
Saga Pattern
├── Core Problem
│   └── Distributed transactions across services without 2PC global lock
├── Key Components
│   ├── Local Transaction → each service commits to its own DB independently
│   ├── Compensating Transaction → semantic undo (forward action, not DB ROLLBACK)
│   ├── Choreography → services react to events, no central coordinator
│   └── Orchestrator → single process issues commands, tracks saga state
├── When to Use
│   ├── ✓ Multi-service workflows that must stay consistent (order, payment, inventory)
│   ├── ✓ Long-running business transactions (minutes to hours)
│   └── ✓ Services own separate databases — no shared DB transaction possible
├── When NOT to Use
│   ├── ✗ Single service with one DB (just use a local ACID transaction)
│   └── ✗ Need true isolation — saga intermediate states are visible to other readers
├── Trade-offs
│   ├── Pro: No distributed lock, no coordinator SPOF, scales horizontally
│   ├── Pro: Each service independently deployable and failure-isolated
│   ├── Con: Compensating transactions are complex to design and test
│   └── Con: Eventual consistency — system is temporarily inconsistent during execution
├── Choreography vs Orchestration
│   ├── Choreography → no SPOF, hard to trace flow, good for simple linear sagas
│   └── Orchestration → explicit flow control, easier debugging, coordinator can be SPOF
├── Real-World Usage
│   ├── Amazon → order saga: Order → Reserve Inventory → Charge Payment → Ship
│   ├── Uber → trip saga: Request → Match Driver → Charge Card → Complete Trip
│   └── Netflix → account saga: Signup → Payment → Profile Creation → Welcome Email
└── Interview Angles
    ├── "How do you handle partial failures?" → describe compensating transactions
    ├── "Choreography vs orchestration?" → trade SPOF for debuggability
    └── "How is this different from 2PC?" → no blocking locks, eventual consistency
```

---

## 💃 Implementation 1: Choreography

In Choreography, there is no central boss. The services are like dancers on a stage who know the routine. They just react to each other's music (events).

```
┌──────────────┐          ┌─────────────────┐
│ Order Service│          │Inventory Service │
│ Creates order│─OrderCreated──►│ Reserves stock  │
│ (PENDING)    │          │                 │─StockReserved──►┐
└──────────────┘          └─────────────────┘                │
                                                             ▼
                                                  ┌────────────────┐
                                                  │Payment Service │
                                                  │ Charges card   │
                                                  └────────────────┘
                                                         │
                                              PaymentCompleted
                                                         │
                                              ┌──────────▼───────┐
                                              │ Order Service    │
                                              │ Mark CONFIRMED   │
                                              └──────────────────┘

Compensation chain (PaymentFailed):
  PaymentService → publishes PaymentFailed
  InventoryService listens → publishes StockReleased
  OrderService listens → marks order FAILED
```

### Choreography: Pros and Cons
```
✓ Simple: No central boss, services are loosely coupled
✓ No single point of failure
✗ Hard to visualize overall flow (it's scattered everywhere)
✗ Debugging is a nightmare: you have to trace logs across 5 different services
✓ Use when: Simple, straight-line flows with 3-4 steps
```

---

## 🎼 Implementation 2: Orchestration

In Orchestration, there is a central boss (the Orchestrator). Think of it like a symphony conductor. The conductor points to the Inventory Service and says "Reserve the item". Then points to the Payment Service and says "Charge the card". If the card fails, the conductor points back to the Inventory service and says "Release the item".

```
┌─────────────────────────────────────────────────────────┐
│                 ORDER SAGA ORCHESTRATOR                 │
│                                                         │
│  Step 1: reserve_inventory(order_id, items)             │
│           ↓ success                                     │
│  Step 2: charge_payment(order_id, amount)               │
│           ↓ success                                     │
│  Step 3: confirm_order(order_id)                        │
│           ↓ success                                     │
│  Step 4: send_notification(order_id)                    │
│                                                         │
│  On failure at Step N:                                  │
│    Execute compensation for Steps N-1 → 1               │
└─────────────────────────────────────────────────────────┘
        │           │           │           │
        ▼           ▼           ▼           ▼
  Inventory    Payment      Order        Notification
  Service      Service      Service      Service
```

### Orchestration: Pros and Cons
```
✓ Explicit flow: You can open one file and see the entire business process
✓ Easier debugging: The Orchestrator tracks the exact state of the order
✗ Orchestrator is a single point of failure (if it dies, the process stops)
✗ Tighter coupling (the boss needs to know about everyone)
✓ Use when: Complex flows with 5+ steps, parallel actions, or complex logic
```

---

## 🔄 Idempotency (Why it's Critical)

If a step fails, the Orchestrator might retry it. This means every action must be **idempotent** (meaning it can safely happen twice without causing a double-charge).

```java
// Good (Idempotent):
public void reserve(String orderId) {
    if (alreadyReserved(orderId)) return; // Safe!
    doReserve(orderId);
}
```

---

## 🪦 What if a Compensating Action Fails? (The Dead Letter Queue)

If you need to refund a user, but the payment provider's API is down, what happens? 
You can't just give up. You put the task into a retry loop.

```
Attempt 1: immediate
Attempt 2: wait 30 seconds
Attempt 3: wait 2 minutes
...
Attempt 7: wait 24 hours
→ If still failing after 48 hours: move to Dead Letter Queue (DLQ)
```

The **Dead Letter Queue (DLQ)** is the graveyard for tasks that computers simply cannot fix. When a message lands here, a human engineer gets paged. 

The human will look at the DLQ, realize the refund API has been broken for 3 days, and manually log into the banking portal to refund the angry customer. 

---

## 🎤 Interview Talking Points

**Q: "Why use Saga instead of Two-Phase Commit (2PC)?"**
> "2PC requires freezing all databases until everyone is ready. If one service slows down, the entire system locks up. Sagas solve this by using fast, local database saves. If something fails later on, we just run a 'compensating transaction' to undo it. It trades immediate perfect consistency for high availability and speed, which is exactly what modern microservices need."

**Q: "What if a compensating transaction fails?"**
> "We must make them retryable. We log every attempt. If the compensation fails due to a temporary network blip, we retry with exponential backoff. If it fails permanently (e.g., the money was already wired via a bank transfer), the compensation goes to a Dead Letter Queue and an engineer or customer support rep has to manually resolve it."

**Q: "When would you choose Choreography vs Orchestration?"**
> "I choose Choreography for very simple, linear flows of 2 to 3 steps because it's fast and requires no central infrastructure. However, for anything complex—like a checkout flow with 5 steps, parallel branches, and tricky rollback logic—I strictly use Orchestration. Having a central 'conductor' makes the code readable and debugging a million times easier when things break."

---

## Applied In

This concept is used by **5 problems** in this repo:

**High-Level Design**

- [Design a Booking System (Hotels / Flights)](../../05-hld-problems/01-easy/booking-system.md)
- [Design an E-Commerce Platform (Amazon)](../../05-hld-problems/02-medium/e-commerce-platform.md)
- [Design a Hotel Booking System (Booking.com)](../../05-hld-problems/03-hard/hotel-booking.md)
- [Design a Payment System](../../05-hld-problems/03-hard/payment-system.md)
- [Design a Ticket Booking System (Ticketmaster)](../../05-hld-problems/03-hard/ticketmaster-seat-booking.md)

