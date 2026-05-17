# The Strangler Fig Pattern

> **Incrementally migrate a monolith to microservices without a big-bang rewrite.**

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
