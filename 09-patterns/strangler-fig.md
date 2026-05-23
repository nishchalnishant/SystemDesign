---
module: 09-patterns
status: unread
tags: [09-patterns, system-design, patterns]
---
# The Strangler Fig Pattern

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

## Relation to Other Patterns

- **Outbox Pattern**: use to sync data from monolith DB to new service during migration
- **Saga Pattern**: replace monolith transactions that span extracted modules
- **API Gateway**: the routing layer that makes strangler fig routing possible without client changes

---

## Quick Revision

- **Problem**: Monolith can't scale; full rewrite is too risky
- **Solution**: Route incrementally at the API layer; extract one module at a time; delete from monolith after cutover
- **Key tool**: API gateway / reverse proxy as the routing intercept point
- **Hardest part**: shared database — plan data ownership before extracting each module
- **Failure mode**: extracting without clear data ownership → distributed monolith (microservices shape, monolith coupling)
