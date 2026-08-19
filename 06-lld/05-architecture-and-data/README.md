---
module: 06-lld
topic: Architecture and Data
status: unread
tags: [06-lld, system-design, architecture, api, database]
---
# Architecture & Data Design for LLD

> **Why this matters for SDE-3:** 
> While Junior/Mid-level LLD interviews focus strictly on classes, objects, and design patterns, Senior (SDE-3) interviews expect you to bridge the gap between Low-Level and High-Level Design. This means your class structures must integrate cleanly with databases, external APIs, and decoupled domains.

This section covers the three critical pillars of SDE-3 level LLD:

1. **[Clean Architecture (Hexagonal/Ports & Adapters)](01-clean-architecture.md)**
   How to structure your classes so that your core business logic is completely isolated from your database, UI, and external frameworks.

2. **[API Design for LLD](02-api-design-for-lld.md)**
   How to expose your LLD engine to the outside world using RESTful or GraphQL paradigms.

3. **[Database Schema Design for LLD](03-database-schema-design.md)**
   How to persist the state of your objects. Converting object-oriented inheritance and relationships into relational tables (ER diagrams) or NoSQL collections.

### How to use this section
Read this *after* mastering Design Patterns and Concurrency. When you practice the problems in `06-problems`, challenge yourself to explicitly write out the REST APIs and DB schemas for each problem.
