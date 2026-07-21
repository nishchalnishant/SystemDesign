---
module: 09-patterns
status: unread
tags: [09-patterns, system-design, patterns]
---
# CQRS + Event Sourcing

> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Two advanced architectural patterns for massive scale: CQRS (splitting reads and writes) and Event Sourcing (storing history instead of current state).
>
> **Key concepts:**
> - **CQRS (Command Query Responsibility Segregation):** Splitting your app into two halves. One half only handles Writes (Commands) using a strict, secure database. The other half only handles Reads (Queries) using a fast, pre-calculated database.
> - **Event Sourcing:** Instead of saving a user's *current* data, you save a log of every *action* they ever took. To find their current data, you fast-forward through the log. 
> - **Eventual Consistency:** Because the fast Read Database is updated a few milliseconds after the Write Database, a user might occasionally see slightly stale data.
>
> **Key takeaway:** These patterns are incredibly powerful for auditing and scaling, but they are also incredibly complex. Only use them when a normal database design physically cannot keep up with your traffic.

---

## Part 1: CQRS (Command Query Responsibility Segregation)

### 🤷‍♂️ Why Should I Care?

Imagine a system like Twitter. 
- **Writes:** People tweet maybe 5,000 times a second. Writing a tweet requires strict rules (checking for spam, checking character limits).
- **Reads:** People view tweets 500,000 times a second. Reading a tweet needs to be blindingly fast. 

If you use a single database for both, you have a problem. If you optimize the database to do complex spam-checking (Writes), the Reads become too slow. If you optimize the database for instant Reads, the spam-checking code breaks. 

A single database trying to be perfect for both Reads and Writes is perfect for neither.

### 🏛️ The Information Kiosk Analogy

Think of a government building. 

If you want to **Register a Car (A Write)**, you go to the Registration Office. It is slow. They check your ID, verify your VIN number, stamp forms, and put it in a secure filing cabinet. (Strict rules, slow).

If you want to **Ask a Question (A Read)** like "What are the rules for parking?", you don't wait in the Registration line. You go to the Information Kiosk in the lobby. The person there just hands you a pre-printed brochure. (No rules, blindingly fast).

CQRS is just doing this in software. 
- You build a **Command Side** (Registration Office) backed by a strict database like PostgreSQL.
- You build a **Query Side** (Information Kiosk) backed by a fast search engine like Elasticsearch.
- When the Registration Office updates a file, they send a memo (an Event) to the Information Kiosk to update their brochures. 

---

### CQRS Architecture

```
         COMMAND SIDE                          QUERY SIDE
    (Write — normalized, strict)        (Read — denormalized, fast)

  ┌─────────────────────┐              ┌─────────────────────┐
  │   Command Handler   │              │    Query Handler    │
  │                     │              │                     │
  │ PlaceOrder()        │              │ GetOrderHistory()   │
  │ CancelOrder()       │              │ GetOrderSummary()   │
  └──────────┬──────────┘              └──────────┬──────────┘
             │ writes to                           │ reads from
             ▼                                     ▼
  ┌─────────────────────┐              ┌─────────────────────┐
  │  Write Database     │              │  Read Database      │
  │  (PostgreSQL)       │──events──►  │  (Elasticsearch /   │
  │                     │              │   Redis)            │
  └─────────────────────┘              └─────────────────────┘
```

---

## Part 2: Event Sourcing

### 🤷‍♂️ Why Should I Care?

Imagine a fraud investigator at a bank looking at your account. The database says your balance is `$0`. But how did it get to `$0`? Who changed it? When? Was it a hacker or did you buy a TV?

A normal database only stores the **current state**. Every time you update a row in a database, you permanently delete the old data. If you don't manually write separate "audit logs", that history is gone forever. 

### ♟️ The Chess Game Analogy

Think about a game of chess. 

A **Normal Database** is like taking a photograph of the board on turn 20. You know exactly where the pieces are right now (the current state). But you have no idea *how* they got there. You don't know who took the Queen. 

**Event Sourcing** is like the piece of paper where chess players write down every single move (`e2 to e4`, `Knight to f3`). 
Instead of taking a photograph, you just save the list of moves. 
If you want to know what the board looks like on turn 20, you just take an empty board and replay the first 20 moves. 

In software, instead of saving `balance = $0`, you save a list of events:
1. `AccountCreated`
2. `Deposited($100)`
3. `BoughtTV($100)`

By replaying those events, you can calculate that the balance is `$0`. 

**The Superpower:** If a bug caused TVs to ring up as `$50` instead of `$100`, you can literally fix the code, rewind the event log, and replay history to get the true mathematical balance. 

---

### Implementation Code

#### Event Sourced Application (Java)

```java
public class Order {
    private String status;
    private List<DomainEvent> pendingEvents = new ArrayList<>();

    // COMMAND: Do an action
    public void placeOrder(String userId) {
        if (this.status != null) throw new Exception("Order already exists");
        
        // We don't save to a database. We just create an Event!
        raiseEvent(new OrderPlacedEvent(userId));
    }

    private void raiseEvent(DomainEvent event) {
        apply(event);                  // 1. Update memory
        pendingEvents.add(event);     // 2. Queue to save to Event Store
    }

    // APPLY: Update the state based on the event
    private void apply(DomainEvent event) {
        if (event instanceof OrderPlacedEvent) {
            this.status = "PENDING";
        }
    }
}
```

#### Event Store (The Database)

Instead of a table with columns for `name` and `status`, you just have a giant log of events.

```sql
CREATE TABLE event_store (
    aggregate_id   VARCHAR(64),       -- The Order ID
    sequence_num   BIGINT,            -- Move 1, Move 2, Move 3
    event_type     VARCHAR(100),      -- 'OrderPlaced', 'ItemShipped'
    event_data     JSONB,             -- The actual data payload
    occurred_at    TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY (aggregate_id, sequence_num)
);
```

---

### Snapshots (The Performance Fix)

If an order has 10,000 events, replaying them every time you want to see the order is too slow. 

To fix this, we use **Snapshots**. Every 50 events, you take a "photograph" of the chess board and save it. Next time, you just load the photograph (snapshot) and only replay the 2 or 3 moves that happened after the photo was taken.

---

### CQRS + Event Sourcing (The Ultimate Combo)

Usually, these two patterns are used together. 
1. The **Command Side** uses **Event Sourcing**. It appends events to the giant Event Store.
2. The Event Store pushes those events to the **Query Side**.
3. The Query Side builds fast, normal databases (like MongoDB or Redis) so users can actually read their data quickly without replaying chess games.

---

## Trade-offs

| Feature | Normal Database (CRUD) | CQRS + Event Sourcing |
|---------|------------------------|-----------------------|
| **Getting Current Data** | Fast (Just read the row) | Complex (Must query a separate Read DB or replay events) |
| **Audit Trail** | Poor (Must be built manually) | Perfect (Every click is saved forever) |
| **Time Travel** | Impossible | Easy (Just replay events up to last Tuesday) |
| **Storage Size** | Small | Massive (You never delete anything) |
| **Complexity** | Very Low | Extremely High |

---

## 🎤 Interview Talking Points

**Q: "Explain CQRS and when you'd use it."**
> "CQRS separates the Write side of an app from the Read side. You use it when your read traffic and write traffic have completely different needs. For example, if users are reading data 100 times more often than writing it, you can make the Read database a highly-optimized, denormalized Elasticsearch index, while keeping the Write database as a strict PostgreSQL table. The trade-off is 'eventual consistency'—it takes a few milliseconds for the Write side to update the Read side."

**Q: "What's the advantage of Event Sourcing over just storing the current state?"**
> "Event Sourcing gives you three superpowers. First, a perfect audit trail, because you save every single action the user takes. Second, time travel—you can reconstruct the system state at any exact second in the past by replaying the event log up to that timestamp. Third, flexibility—if you want to build a brand new analytics dashboard tomorrow, you can replay the last 5 years of historical events into it. The downside is massive complexity and a steep learning curve."

---

## Applied In

This concept is used by **1 problem** in this repo:

**High-Level Design**

- [Design an E-Commerce Platform (Amazon)](../../05-hld-problems/02-medium/e-commerce-platform.md)

