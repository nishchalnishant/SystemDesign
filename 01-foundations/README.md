> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Index for the foundations section — the core theory every system design answer builds on.
>
> **Key topics:**
> - Fundamentals: scalability, availability, consistency, performance, latency numbers
> - Databases: SQL vs NoSQL, ACID/BASE, CAP theorem, replication, sharding, indexes, isolation levels
> - Networking: OSI model, TCP/UDP, HTTP/1.1–3, REST/GraphQL/gRPC, WebSockets, DNS
> - Security: AuthN/AuthZ, OAuth 2.0/OIDC, TLS, OWASP, Zero Trust
> - Pointers to building-block deep dives for caching, CDN, load balancers
>
> **Key takeaway:** Read all files in this section before touching anything else — they form the vocabulary for every topic that follows.

---

# Core Concepts

Fundamental building blocks for system design interviews.

## Contents

| Topic | File | Description |
|-------|------|-------------|
| **Fundamentals** | [01-fundamentals.md](01-system-design-basics/01-fundamentals.md) | Broad coverage: IP, OSI, TCP/UDP, DNS, load balancing, caching, CDN, availability, scalability, storage, DB basics, replication, sharding, message brokers, microservices, API gateway, rate limiting, SLA/SLO, and more |
| **Databases** | [01-databases.md](03-database-foundations/01-databases.md) | SQL vs NoSQL, ACID/BASE, CAP, replication, sharding, indexes, isolation levels, selection guide; includes Senior Insights and Quick Revision |
| **Caching & CDN** | [02-building-blocks/caching-layer.md](../02-building-blocks/02-performance/01-caching-layer.md) | Why caching exists, decision framework, cache patterns, eviction policies, Redis, CDN layer — consolidated in building blocks |
| **Networking** | [02-networking.md](02-hardware-and-networking/02-networking.md) | OSI/TCP/IP, TCP vs UDP, HTTP/1.1–3, REST/GraphQL/gRPC/WebSockets, L4/L7 load balancing, API gateway |
| **Security** | [01-security.md](04-security/01-security.md) | Authentication, authorization (RBAC/ABAC), OAuth 2.0/OIDC, TLS, OWASP, Zero Trust |

For **building-block** deep dives (load balancers, message brokers, rate limiting, etc.), see [../02-building-blocks/](../02-building-blocks/).
