# Folder-by-Folder Review

> **Complete pass through every folder in the repository.** No folder skipped.

---

## Root (`/`)

| Item | Type | Status |
|------|------|--------|
| README.md | File | ✅ Main guide; structure, learning paths |
| SUMMARY.md | File | ✅ Full TOC; links to all sections |
| SYSTEM_DESIGN_REPO_AUDIT.md | File | ✅ Audit report |
| SYSTEM_DESIGN_INTERVIEW_FRAMEWORK.md | File | ✅ Interview framework |
| .gitbook | Dir | Config (unchanged) |
| **5.-interview-templates** | Dir | See below |
| **6.-reference** | Dir | See below |
| **advanced-topics** | Dir | See below |
| **building-blocks** | Dir | See below |
| **core-concepts** | Dir | See below |
| **distributed-systems** | Dir | See below |
| **hld-problems** | Dir | See below |
| **interview-templates** | Dir | See below |
| **lld-problems** | Dir | See below |
| **random-talks** | Dir | See below |
| **reference** | Dir | See below |
| **scaling** | Dir | See below |

---

## 5.-interview-templates

| Item | Type | Status |
|------|------|--------|
| README.md | File | ⚠️ Stub only: "# 5. Interview Templates" |

**Note:** SUMMARY uses this as section "5. Interview Templates" but links children to `interview-templates/` (sibling folder). The real content lives in **interview-templates/** (hld-template, lld-template, capacity-estimation, trade-offs-cheat-sheet).

**Recommendation:** Expand README to describe the section and link to `../interview-templates/` for the actual templates.

---

## 6.-reference

| Item | Type | Status |
|------|------|--------|
| README.md | File | ⚠️ Stub only: "# 6. Reference" |
| book-summaries/ | Dir | See below |

**Note:** SUMMARY uses this as section "6. Reference"; book-summaries links and DDIA/Head First links point to **reference/** (sibling). Actual content: **reference/numbers-to-know.md**, **reference/book-summaries/**.

**Recommendation:** Expand README to describe the section and link to `../reference/` for numbers-to-know and book summaries.

---

## 6.-reference/book-summaries

| Item | Type | Status |
|------|------|--------|
| README.md | File | ✅ Describes DDIA, Head First Java, Head First OOA&D; links to `../../reference/book-summaries/` |

**Note:** Links correctly point to **reference/book-summaries/** where the actual files live.

---

## advanced-topics

| Item | Type | Status |
|------|------|--------|
| chaos-engineering.md | File | ✅ Full content |
| distributed-concepts.md | File | ✅ Idempotency, retry, backpressure |
| distributed-systems.md | File | ✅ Consistency, consensus, transactions, time, conflict resolution |
| observability.md | File | ✅ Metrics, logs, traces, SLI/SLO/SLA, Quick Revision |
| internals/ | Dir | See below |

**No README.** Optional: add README listing these topics and linking to distributed-systems/ for consistency-models and distributed-transactions.

---

## advanced-topics/internals

| Item | Type | Status |
|------|------|--------|
| README.md | File | ✅ Strong: index of Kafka, Cassandra, PostgreSQL, Redis, Elasticsearch, ZooKeeper; how to use; comparison matrix |
| cassandra-internals.md | File | Present |
| elasticsearch-internals.md | File | Present |
| kafka-internals.md | File | Present |
| postgresql-internals.md | File | Present |
| redis-internals.md | File | Present |
| zookeeper-internals.md | File | Present |

**Status:** Complete.

---

## building-blocks

| Item | Type | Status |
|------|------|--------|
| README.md | File | ✅ Index of all 11 building blocks |
| load-balancers.md | File | ✅ |
| reverse-proxy.md | File | ✅ |
| cdn.md | File | ✅ |
| caching-layer.md | File | ✅ |
| message-brokers.md | File | ✅ |
| service-discovery.md | File | ✅ |
| api-gateway.md | File | ✅ |
| distributed-locks.md | File | ✅ |
| rate-limiting.md | File | ✅ |
| sharding.md | File | ✅ |
| replication.md | File | ✅ |

**Status:** Complete.

---

## core-concepts

| Item | Type | Status |
|------|------|--------|
| fundamentals.md | File | ✅ Very large; broad coverage (networking, protocols, scaling, DB, etc.) |
| databases.md | File | ✅ Deep; Senior Insights + Quick Revision |
| caching-cdn.md | File | ✅ Deep; Senior Insights + Quick Revision |
| networking.md | File | ✅ Concise |
| security.md | File | ✅ Concise |

**No README.** Optional: add README listing these five and their purpose.

---

## distributed-systems

| Item | Type | Status |
|------|------|--------|
| consistency-models.md | File | ⚠️ Stub: "# Consistency Models" only (22 bytes) |
| distributed-transactions.md | File | ⚠️ Stub: "# Distributed Transactions" only (28 bytes) |

**No README.** SUMMARY links here for "Consistency Models" and "Distributed Transactions". Full content for both is in **advanced-topics/distributed-systems.md**.

**Recommendation:** Either (1) add short content + "For full treatment see [advanced-topics/distributed-systems.md](../advanced-topics/distributed-systems.md)", or (2) change SUMMARY to link to advanced-topics and add anchors. Option 1 keeps SUMMARY links valid.

---

## hld-problems

| Item | Type | Status |
|------|------|--------|
| README.md | File | ✅ Catalog; difficulty levels; links to templates (paths use 5.-interview-templates in file:// URLs) |
| easy/ | Dir | See below |
| medium/ | Dir | See below |
| hard/ | Dir | See below |

**Note:** README internal links point to `5.-interview-templates`; actual files are in **interview-templates**. Consider updating links to `../interview-templates/`.

---

## hld-problems/easy

| Item | Type | Status |
|------|------|--------|
| README.md | File | ⚠️ Stub: "# Easy" only |
| url-shortener.md | File | ✅ Full design |
| rate-limiter.md | File | ✅ Full design |
| pastebin.md | File | ✅ Full design |
| key-value-store.md | File | ✅ Full design |

**Recommendation:** Expand README to list the four problems with links.

---

## hld-problems/medium

| Item | Type | Status |
|------|------|--------|
| README.md | File | ⚠️ Stub: "# Medium" only |
| twitter.md | File | ⚠️ Stub: "# Twitter" only (11 bytes). SUMMARY links here. |
| twitter-news-feed.md | File | ✅ Full content (news feed / Twitter design) |
| instagram.md | File | ✅ Full content |
| youtube.md | File | ✅ Full content |
| whatsapp.md | File | ✅ Full content |
| notification-service.md | File | ✅ Full content |

**Recommendation:** Make **twitter.md** point to twitter-news-feed (e.g. "See [twitter-news-feed.md](twitter-news-feed.md) for the full Twitter / News Feed design."). Expand README to list all medium problems with links.

---

## hld-problems/hard

| Item | Type | Status |
|------|------|--------|
| README.md | File | ⚠️ Stub: "# Hard" only |
| chat-system.md | File | Present |
| distributed-cache.md | File | ✅ Full design |
| distributed-message-queue.md | File | ✅ Full design |
| google-drive.md | File | ✅ Full design |
| payment-system.md | File | Present |
| ride-sharing.md | File | Present |
| search-system.md | File | ✅ Full design (added recently) |

**Recommendation:** Expand README to list all hard problems with links.

---

## interview-templates

| Item | Type | Status |
|------|------|--------|
| hld-template.md | File | ✅ Full step-by-step HLD guide |
| lld-template.md | File | ✅ LLD checklist |
| capacity-estimation.md | File | ✅ Back-of-envelope calculations |
| trade-offs-cheat-sheet.md | File | ✅ Trade-offs reference |

**No README.** This folder holds the actual template content; SUMMARY lists it under "5. Interview Templates" and links here. Optional: add README listing the four files.

---

## lld-problems

| Item | Type | Status |
|------|------|--------|
| README.md | File | ✅ Master list of LLD problems with probability, difficulty, key concepts |
| SUMMARY.md | File | LLD-specific TOC |
| .gitbook.yaml | File | Config |
| SOLID-principles/ | Dir | See below |
| design-patterns/ | Dir | See below |
| oops-concepts/ | Dir | See below |
| problems/ | Dir | See below |
| concurrency/ | Dir | See below |
| uml-diagrams.md | File | Present |

**Status:** Well structured.

---

## lld-problems/SOLID-principles

| Item | Type | Status |
|------|------|--------|
| README.md | File | Present |
| 1. single-responsibility-principle.md | File | Present |
| 2. open-closed-principle.md | File | Present |
| 3. liskov-substitution-principle.md | File | Present |
| 4. interface-segregation-principle.md | File | Present |
| 5. dependency-inversion-principle.md | File | Present |

**Status:** Complete.

---

## lld-problems/design-patterns

| Item | Type | Status |
|------|------|--------|
| README.md | File | Present |
| behavioral/ | Dir | 10 pattern files + README |
| creational/ | Dir | 5 pattern files + README |
| structural/ | Dir | 7 pattern files + README |

**Status:** Complete.

---

## lld-problems/design-patterns/behavioral

| Item | Type | Status |
|------|------|--------|
| README.md | File | Present |
| chain-of-responsibility.md, command-pattern.md, iterator-pattern.md, mediator-pattern.md, observer-pattern.md, state-pattern.md, strategy-pattern.md, template-method-pattern.md, visitor-pattern.md | Files | Present |

**Status:** Complete.

---

## lld-problems/design-patterns/creational

| Item | Type | Status |
|------|------|--------|
| README.md | File | Present |
| abstract-factory-pattern.md, builder-pattern.md, factory-pattern.md, prototype-pattern.md, singleton.md | Files | Present |

**Status:** Complete.

---

## lld-problems/design-patterns/structural

| Item | Type | Status |
|------|------|--------|
| README.md | File | Present |
| adapter-pattern.md, bridge-pattern.md, composite-pattern.md, decorator-pattern.md, facade-pattern.md, flyweight-pattern.md, proxy-pattern.md | Files | Present |

**Status:** Complete.

---

## lld-problems/oops-concepts

| Item | Type | Status |
|------|------|--------|
| introduction.md | File | Present |
| four-pillars.md | File | Present |
| principles.md | File | Present |
| java-oops.md | File | Present |
| python-oops.md | File | Present |

**No README.** SUMMARY links to "introduction.md" as OOP Introduction. Optional: add README listing these five.

---

## lld-problems/problems

| Item | Type | Status |
|------|------|--------|
| README.md | File | Present |
| 1-design-parking-lot.md through 23-design-unlock-pattern.md | Files | Present (23 numbered problems) |
| rate-limiter.md | File | Present |
| reddit-comments.md | File | Present |
| tunneling-tool.md | File | Present |

**Status:** Many LLD problems; README ties to main lld-problems list.

---

## lld-problems/concurrency

| Item | Type | Status |
|------|------|--------|
| producer-consumer.md | File | ✅ Full content (BlockingQueue, wait/notify, etc.) |
| thread-safe-singleton.md | File | Present |

**No README.** Not in SUMMARY. Optional: add README and consider adding to SUMMARY under LLD.

---

## random-talks

| Item | Type | Status |
|------|------|--------|
| README.md | File | ⚠️ Stub: "# Random talks" only |
| building-ml-systems-for-a-trillion-trilion-floating-point-operations.md | File | ✅ Has content (1729 bytes) |

**Recommendation:** Expand README to list the talk(s) with links. Not in SUMMARY; could be added under Reference or left as supplementary.

---

## reference

| Item | Type | Status |
|------|------|--------|
| numbers-to-know.md | File | ✅ Latency, throughput, cost reference |
| book-summaries/ | Dir | See below |

**No README.** SUMMARY links here for "Numbers to Know" and book summaries. Optional: add README listing numbers-to-know and book-summaries.

---

## reference/book-summaries

| Item | Type | Status |
|------|------|--------|
| ddia.md | File | Present |
| head-first-java/ | Dir | README + chapters + combined file |
| head-first-ooand/ | Dir | README + chapters + combined file |

**Status:** Full content. SUMMARY links to DDIA and both Head First books.

---

## reference/book-summaries/head-first-java

| Item | Type | Status |
|------|------|--------|
| README.md | File | Present |
| chapter-*.md, appendix-a-*.md | Files | Present |
| head-first-java.md | File | Combined |

**Status:** Complete.

---

## reference/book-summaries/head-first-ooand

| Item | Type | Status |
|------|------|--------|
| README.md | File | Present |
| chapter-*.md | Files | Present |
| head-first-ooand.md | File | Combined |

**Status:** Complete.

---

## scaling

| Item | Type | Status |
|------|------|--------|
| README.md | File | ✅ Describes scaling-strategies.md and related links |
| scaling-strategies.md | File | ✅ Horizontal/vertical, DB, replication, caching, queues, async |

**Status:** Complete.

---

## Summary of Actions Taken / Recommended

| Folder / File | Issue | Action |
|---------------|--------|--------|
| 5.-interview-templates/README.md | Stub | Expand with description + link to interview-templates |
| 6.-reference/README.md | Stub | Expand with description + link to reference |
| distributed-systems/consistency-models.md | Stub | Add short intro + link to advanced-topics/distributed-systems.md |
| distributed-systems/distributed-transactions.md | Stub | Add short intro + link to advanced-topics/distributed-systems.md |
| hld-problems/easy/README.md | Stub | List easy problems with links |
| hld-problems/medium/README.md | Stub | List medium problems with links |
| hld-problems/medium/twitter.md | Stub | Point to twitter-news-feed.md |
| hld-problems/hard/README.md | Stub | List hard problems with links |
| random-talks/README.md | Stub | List talk(s) with links |
| core-concepts, distributed-systems, reference, interview-templates, lld-problems/oops-concepts, lld-problems/concurrency | No README | Optional: add README for navigation |
| hld-problems/README.md | file:// links to 5.-interview-templates | Consider relative links to ../interview-templates/ |

---

*End of folder-by-folder review. No folder skipped.*
