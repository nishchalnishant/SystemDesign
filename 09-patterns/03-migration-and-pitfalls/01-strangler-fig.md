---
module: 09-patterns
status: unread
tags: [09-patterns, system-design, patterns]
---
# The Strangler Fig Pattern

> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** A safe, step-by-step strategy for migrating a giant, messy legacy application (a monolith) into a modern microservices architecture.
>
> **Key concepts:**
> - **The Metaphor:** The strangler fig vine grows around a host tree. Over time, the vine grows stronger and the host tree dies, leaving only the vine.
> - **The Gateway:** A router (API Gateway) is placed in front of the old system. It directs old traffic to the old system, and new traffic to the new microservices.
> - **Incremental Migration:** You carve out exactly *one* feature at a time, build it as a new microservice, and update the router.
>
> **Key takeaway:** "Big Bang" rewrites (where you spend 2 years rewriting the system from scratch and flip the switch all at once on day 730) almost always fail. The Strangler Fig pattern allows you to upgrade your system one small piece at a time while safely serving real customers.

---

## 🤷‍♂️ Why Should I Care?

Imagine you have a massive, 10-year-old application with 5 million lines of code. It handles user profiles, orders, inventory, and emails all in one giant block (a Monolith). 

During Black Friday, your Checkout system gets 100x more traffic than usual. You want to scale up *just* the Checkout system. But you can't. Because it's a monolith, to scale Checkout, you have to scale the entire 5-million-line application 100x, which costs a fortune.

**The Naive Fix (The Big Bang Rewrite):**
You tell your boss: "Let's rewrite the whole thing in microservices!" 
Your team spends 18 months building the new system in secret. On launch day, you flip the switch. Instantly, 50 things break because you forgot edge cases that existed in the 10-year-old code. The company loses money, and you are forced to switch back to the old system. The project is cancelled.

This is a classic trap. You cannot safely test a massive new system without real traffic, but you cannot risk giving real traffic to a massive untested system.

---

## 🌳 The Strangler Fig Analogy

In the rainforest, there is a plant called the **Strangler Fig**. 
It doesn't grow from the ground up. Instead, a seed lands on the branches of a giant, old host tree. The vine grows *around* the host tree, slowly sending roots to the ground and branches to the sun. Over many years, the vine completely engulfs the host tree. Eventually, the old tree dies and rots away, leaving a perfectly healthy, strong Strangler Fig tree standing in its place.

In software, we do exactly the same thing:
1. We put a **Router** (API Gateway) in front of the old Monolith.
2. We build **one** new microservice (e.g., just the Orders service) alongside the old Monolith.
3. We tell the Router: "Send all `/orders` traffic to the new service. Send everything else to the old Monolith."
4. We repeat this process, carving out one feature at a time, until the old Monolith is completely hollowed out and we can delete it.

---

## Pattern Mindmap

```
Strangler Fig Pattern
├── Core Problem
│   └── Big-bang monolith rewrites fail; need incremental extraction with real-traffic validation
├── Key Components
│   ├── Routing Layer → reverse proxy or API gateway in front of monolith (no code change)
│   ├── Facade → intercepts calls; routes to new service or monolith per feature
│   ├── Extracted Microservice → owns one bounded context; independently deployable
│   └── Data Migration → service gets its own DB; monolith DB access removed last
├── Migration Steps
│   ├── 1. Add facade → zero change to monolith, all traffic still flows through it
│   ├── 2. Extract feature → new service built alongside monolith
│   ├── 3. Shadow mode → new service runs in parallel; responses compared, not served
│   ├── 4. Canary → 1% → 10% → 50% → 100% traffic shift with rollback at any point
│   └── 5. Remove monolith code → once new service handles 100% traffic, delete old path
├── When to Use
│   ├── ✓ Monolith that needs selective scaling (one module has 10× the load)
│   ├── ✓ Team wants continuous delivery without blocking on monolith deploy cycle
│   └── ✓ Risk-averse migration — each step must be independently reversible
├── When NOT to Use
│   ├── ✗ Small monolith (< 50K LOC) that is easily maintainable — overhead not justified
│   └── ✗ Deeply intertwined modules with no clear domain boundaries
├── Trade-offs
│   ├── Pro: No big-bang risk; each extraction validated with real traffic
│   ├── Pro: Business feature development continues during migration
│   ├── Con: Dual-write complexity during data migration phase
│   └── Con: Routing layer adds latency and operational overhead
├── Real-World Usage
│   ├── Shopify → extracted storefront rendering from Rails monolith via facade routing
│   ├── Amazon → started as a monolith; strangler fig over years into 500+ services
│   └── Netflix → migrated DVD rental monolith to streaming microservices incrementally
└── Interview Angles
    ├── "How do you avoid breaking the monolith during migration?" → facade + canary routing
    ├── "How do you migrate the shared DB?" → strangler data: dual-write then cutover
    └── "What's the risk of this approach?" → long-lived dual systems, sync complexity
```

---

## 🚦 The 4 Phases of Migration

```
                     ┌──────────────┐
Request ──────────▶  │  API Gateway │  <-- The Interceptor
                     │   / Router   │
                     └──────┬───────┘
                            │
              ┌─────────────┼────────────────┐
              │             │                │
        /users/*       /orders/*        /products/*
              │             │                │
        ┌─────▼────┐  ┌─────▼──────┐  ┌────▼──────┐
        │  User    │  │   Orders   │  │  Monolith │
        │  Service │  │   Service  │  │ (remaining│
        │  (new)   │  │   (new)    │  │  modules) │
        └──────────┘  └────────────┘  └───────────┘
```

**Phase 1 — Intercept**: Put the API Gateway in front of the monolith. For now, it just blindly passes 100% of traffic to the monolith.
**Phase 2 — Extract**: Build a brand new microservice just for Orders. 
**Phase 3 — Route**: Update the API Gateway to route `/orders` traffic to the new service. 
**Phase 4 — Delete**: Once you are 100% sure the new Orders service works, delete the old Orders code out of the Monolith.

*Rinse and repeat until the Monolith is gone.*

---

## 💾 The Hard Part: Splitting the Database

This is the ultimate interview question: *"Routing code is easy. How do you split a massive shared database without downtime?"*

If your new Orders microservice still reads from the old Monolith database, you have failed. You just built a "Distributed Monolith". If the Monolith database crashes, your new microservice crashes too. 

You must give the new microservice its own completely separate database. Here is how you do it safely:

### The 6-Step Database Split

1. **Create the New DB:** Create a brand new, empty database just for the Orders microservice.
2. **Dual-Write:** Change the Monolith code so that whenever a user creates an order, it saves the data to the old DB *AND* the new DB simultaneously. 
3. **Wait:** Wait a few weeks. Let the new DB fill up with fresh data. Use a script to copy over the older historical data in the background.
4. **Read Cutover:** Tell the new Orders microservice to start reading from the new DB. (The Monolith is still doing the writing). Monitor for bugs.
5. **Write Cutover:** Flip the switch. The new Orders microservice now handles all reads AND writes to its own DB. The Monolith stops writing order data entirely.
6. **Delete:** Delete the old Orders tables from the Monolith DB.

Because you do this incrementally, if anything goes wrong at Step 4, you just flip a switch to read from the old DB again. Zero downtime. Zero panic.

---

## 🎤 Interview Talking Points

**Q: "We have a massive legacy monolith that is slowing down our development. How would you migrate us to microservices?"**
> "I would absolutely avoid a 'Big Bang' rewrite, as they almost always fail. I would use the Strangler Fig pattern. First, we put an API Gateway in front of the monolith. Then, we identify one high-value, low-risk module to extract—like the user profile system. We build it as a new microservice alongside the monolith, and route just that traffic to the new service. We incrementally strangle the monolith one feature at a time, allowing us to safely test in production and easily roll back if a specific new service fails."

**Q: "If we extract the Orders service, how do we handle the database migration?"**
> "We must give the Orders service its own database, otherwise we just create a distributed monolith. I would implement a Dual-Write phase. The monolith would continue handling traffic but write data to both the old DB and the new Orders DB. Once the new DB is fully populated and verified, we switch the read traffic to the new DB. After a stabilization period, we switch the write traffic to the new service entirely, and finally deprecate the old tables in the monolith DB."
