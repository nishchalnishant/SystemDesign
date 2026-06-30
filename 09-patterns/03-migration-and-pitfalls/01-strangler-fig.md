---
module: 09-patterns
status: unread
tags: [09-patterns, system-design, patterns]
---
# The Strangler Fig Pattern

> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** A safe, incremental strategy for migrating a legacy monolith application to a modern microservices architecture.
>
> **Key concepts:**
> - The Metaphor: The strangler fig vine grows around a host tree. Over time, the vine grows stronger and the host tree dies, leaving only the vine.
> - The Gateway: An API Gateway or load balancer is placed in front of the monolith. It routes old traffic to the monolith and new/migrated traffic to the microservices.
> - Incremental Migration: You carve out one feature at a time from the monolith, build it as a microservice, and update the Gateway routing.
>
> **Key takeaway:** "Big Bang" rewrites (where you spend 2 years rewriting the monolith and flip the switch on day 730) almost always fail. The Strangler Fig pattern allows you to deliver value incrementally and rollback easily if a specific microservice fails.

> **Incrementally migrate a monolith to microservices without a big-bang rewrite.**

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

## What Breaks Without This Pattern?

A 500K-line Rails monolith serves 5M users. The checkout module needs to scale independently for Black Friday — but it can't, because it shares a process with the user profile module, the email service, and the admin panel. Scaling means scaling everything, at 10x cost. Deploying a fix to checkout means deploying the entire monolith — a 45-minute deploy window with risk to every feature.

**Why the naive fix fails**

The obvious answer is a full rewrite: start fresh in microservices. In practice:
- The rewrite takes 12–18 months. Business feature development pauses or duplicates across both systems.
- You must maintain feature parity with the running monolith, which keeps changing during the rewrite.
- At go-live, you switch 100% of traffic at once. Any missed edge case causes a production incident.
- Most big-bang rewrites are cancelled or rolled back before completion.

The core invariant the big-bang rewrite violates: **you cannot safely validate a replacement system without real traffic, but you cannot take real traffic risk on an unvalidated system.**

**The pattern as the minimal fix**

Add a routing layer (reverse proxy or API gateway) in front of the monolith without changing any code. Route one feature's traffic to a new service — the monolith still handles everything else. Validate the new service with production traffic. Extract the next feature. The monolith shrinks incrementally; each extraction is independently deployable and independently reversible. No freeze, no big-bang, no dual-maintenance explosion.

---

## The Problem

You have a monolith that has grown too large to extend safely. Full rewrite is risky — it requires running two systems in parallel for 12–18 months while feature parity is rebuilt from scratch. Most big-bang rewrites fail or get cancelled.

---

## The Pattern

Named after the strangler fig tree that grows around a host tree and gradually replaces it.

```
Phase 1 — Intercept: Add a routing layer (proxy/API gateway) in front of the monolith
Phase 2 — Extract: Implement a new service for one feature; route that path to the new service
Phase 3 — Repeat: Extract the next feature; the monolith shrinks as services multiply
Phase 4 — Remove: Once a module is fully extracted, delete it from the monolith
```

```
                     ┌──────────────┐
Request ──────────▶  │  API Gateway │
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

---

## Implementation Steps

### Step 1: Add the routing layer
Deploy an API gateway or reverse proxy in front of the monolith. Initially, all traffic passes through to the monolith unchanged.

### Step 2: Identify extraction candidates
Start with:
- **Low-risk, high-value**: modules with clear boundaries, minimal shared state
- **New features**: build them as services from day one, never in the monolith
- Avoid starting with authentication or shared database modules — too much coupling

### Step 3: Extract one module
1. Build the new service (DB schema, API, business logic)
2. Run in shadow mode — mirror traffic to both old and new, compare results
3. Cut over: route the path to the new service
4. Deprecate the monolith code path (don't delete yet)
5. Monitor for 2–4 weeks before deletion

### Step 4: Handle shared data
This is the hardest part. Strategies:
- **Shared database (temporary)**: new service reads/writes the same DB as monolith. Not ideal, but buys time
- **Database-per-service**: new service has its own DB; sync via events (Outbox pattern) during transition
- **Anti-corruption layer**: adapter that translates between monolith's data model and the new service's model

---

## When to Use

- Monolith has become a deployment bottleneck (one team blocks all others)
- You need to scale a specific module independently (e.g. user authentication at 10× the request rate of everything else)
- Tech debt in one area is slowing all development; extraction lets you rewrite that module cleanly
- Team has grown and needs independent deploy cadences

## When NOT to Use

- Small system where a monolith is the right architecture
- The monolith has no clean module boundaries — extraction will just move the spaghetti
- You don't have the infrastructure to run multiple services (monitoring, service discovery, CI/CD)

---

## Key Risks

| Risk | Mitigation |
|------|-----------|
| Data consistency during dual-write period | Use Outbox pattern; define one system as source of truth |
| Distributed transactions across monolith + new service | Saga pattern for cross-service workflows |
| Latency increase (network hop where there was a function call) | Accept it initially; optimize hot paths last |
| Team thrash (nobody knows which system to change) | Hard cutover dates; deprecate monolith path immediately after cutover |

---

## Deep Dive: Database Decomposition Walkthrough

This is always the follow-up question in interviews: **"The code routing is clear — but how do you split the database?"**

### The Problem

The monolith has one large shared database. The Orders module and the Users module both read and write the same DB. If you extract Orders Service but leave it reading the monolith's DB, you have a **distributed monolith** — microservices shape with monolith coupling. Any schema change requires coordinating two teams, and the DB becomes a shared bottleneck.

### The 6-Step DB Decomposition

Use the Orders table as the concrete example (monolith `orders` table → Orders Service DB).

---

**Step 1: Schema Analysis — Identify Data Ownership**

Before writing any code, map every column in the shared table to a domain owner:

```
orders table (monolith DB):
  id              → Orders owns
  user_id         → reference to Users domain (foreign key to users table)
  product_id      → reference to Products domain
  status          → Orders owns
  total_amount    → Orders owns
  shipping_addr   → Orders owns (or Shipping domain)
  promo_code_id   → Promotions domain (foreign key)
  created_at      → Orders owns
```

Result: Orders Service should own `id, status, total_amount, shipping_addr, created_at`. References to other domains become event-based (store the ID, not a FK).

**Decision rule:** A table is owned by the domain that most frequently writes to it and that has the most business logic around it. FK relationships → become event subscriptions in the target architecture.

---

**Step 2: Create the New Schema in Orders Service DB**

```sql
-- Orders Service DB (separate PostgreSQL instance)
CREATE TABLE orders (
  id            UUID PRIMARY KEY,
  user_id       UUID NOT NULL,          -- no FK constraint; Users DB is separate
  status        TEXT NOT NULL,
  total_amount  DECIMAL(10,2),
  shipping_addr JSONB,
  created_at    TIMESTAMP
);
-- No promo_code_id FK; promotions data denormalized at write time if needed
```

Key change: no cross-service foreign keys. Store IDs, not references. If Orders needs user name for display, it either fetches it from Users Service at read time or denormalizes it at write time.

---

**Step 3: Dual-Write Phase (Both DBs Written)**

The Orders module in the monolith continues to write to the monolith DB. Simultaneously, the monolith (or a new Orders Service) writes to the Orders Service DB. Both writes happen on every order creation/update.

```
                    ┌─────────────────────────────────────────┐
Order Create ──────▶│  Monolith (Orders module still active)  │
                    └─────────────┬───────────────────────────┘
                                  │
                    ┌─────────────┴────────────────────────┐
                    │                                      │
            ┌───────▼──────┐                    ┌──────────▼──────────┐
            │ Monolith DB  │                    │  Orders Service DB  │
            │ (orders tbl) │                    │   (new, separate)   │
            └──────────────┘                    └─────────────────────┘
```

**Source of truth during this phase: Monolith DB.** If a write to Orders Service DB fails, log the failure but don't fail the user request. A reconciliation job catches up async.

**Outbox pattern for reliability:**
Instead of a synchronous dual-write (which risks partial failure), write only to the monolith DB but include an `outbox` table entry. A CDC process (Debezium) reads the outbox and replicates to Orders Service DB. This guarantees eventual consistency without distributed transactions.

```sql
-- Monolith DB: outbox table (written in same transaction as orders)
INSERT INTO outbox (event_type, payload, created_at)
VALUES ('ORDER_CREATED', '{"id":"...", "user_id":"...", ...}', NOW());

-- Debezium → Kafka → Orders Service consumer → INSERT into Orders Service DB
```

---

**Step 4: Read Cutover (New Service Reads from New DB)**

Once Orders Service DB is verified to be in sync (compare row counts, spot-check values):

1. Orders Service starts serving **read** traffic from its own DB
2. Monolith still handles writes (dual-write continues)
3. Run for 1–2 weeks, monitoring read discrepancies

```
Read  /orders/{id} ──────▶ Orders Service ──────▶ Orders Service DB ✓
Write /orders       ──────▶ Monolith       ──────▶ Both DBs (dual-write)
```

If read discrepancies are detected → fall back to monolith DB reads. Fix the sync bug. Retry.

---

**Step 5: Write Cutover (New Service Owns Writes)**

Once reads are stable for 2+ weeks:

1. Orders Service takes over **write** traffic
2. Monolith stops writing to `orders` table
3. Dual-write reversed: Orders Service DB is now source of truth; monolith reads from Orders Service API if it still needs order data

```
Write /orders ──────▶ Orders Service ──────▶ Orders Service DB ✓
              (Monolith no longer writes to monolith DB orders table)
```

Keep the monolith DB `orders` table alive but read-only for 2 more weeks (rollback safety net).

---

**Step 6: Remove Shared Access — Delete the Monolith Table Reference**

After 2–4 weeks of stable Orders Service ownership:

1. Remove all DB access to `orders` table from monolith code
2. Rename/archive the monolith `orders` table (don't drop immediately)
3. Remove dual-write code from monolith
4. Drop `outbox` entries for ORDER_* events if no other consumer needs them

```sql
-- After validation period
ALTER TABLE orders RENAME TO orders_archived_2026_07;
-- Drop in 30 days if no rollback needed
```

**Done.** Orders domain is fully extracted with its own DB. No shared schema. The monolith no longer knows the Orders table exists.

---

### Why Not Just Do It All at Once?

| Big-Bang DB Split | Incremental (6 Steps) |
|---|---|
| Zero validation before cutover | Each step validated with real traffic |
| Rollback = restore entire DB backup | Rollback = revert one step |
| Dual-write bugs discovered at go-live | Bugs caught during step 3 when stakes are low |
| Migration takes 1 weekend with team freeze | Migration runs over weeks with zero downtime |

---

### Common Failure Mode: Distributed Monolith

**Sign:** Orders Service has its own deployment, but still reads directly from monolith DB using a shared DB connection string.

**Why it's bad:** Any monolith DB migration must now coordinate with Orders team. Monolith DB goes down → Orders Service goes down. You have microservices operational complexity without the independence benefit.

**Fix:** Never allow a service to directly read another service's DB. All cross-service data access must go through the owning service's API.

---

## Relation to Other Patterns

- **Outbox Pattern**: use to sync data from monolith DB to new service during migration
- **Saga Pattern**: replace monolith transactions that span extracted modules
- **API Gateway**: the routing layer that makes strangler fig routing possible without client changes
- **Change Data Capture**: Debezium reads monolith DB WAL log → publishes events to Kafka → target service consumes

---

## Quick Revision

- **Problem**: Monolith can't scale; full rewrite is too risky
- **Solution**: Route incrementally at the API layer; extract one module at a time; delete from monolith after cutover
- **Key tool**: API gateway / reverse proxy as the routing intercept point
- **Hardest part**: shared database — plan data ownership before extracting each module
- **Failure mode**: extracting without clear data ownership → distributed monolith (microservices shape, monolith coupling)
