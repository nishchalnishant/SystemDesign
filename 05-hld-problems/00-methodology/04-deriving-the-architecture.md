> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Step 4, the centerpiece of the process — a signal table mapping specific requirement phrasings and capacity numbers to the component each one forces, plus the discipline to draw a box only when you can cite what forced it.
>
> **Key ideas:**
> - Read the table by symptom, not by component name — you'll rarely think "I should add a cache"; you'll notice "same data read far more than it's written" and the table tells you that's a cache.
> - Every component needs a one-sentence justification tied to a specific Step 1 requirement or Step 2 number. No justification → don't draw it.
> - Build the diagram left to right, following the request path: client → edge → app tier → data tier → async/background paths last. This ordering itself prevents the common mistake of drawing infrastructure before the request path that needs it.
> - Most solved HLD problems combine 4-8 components, not one clever trick. The signal table composes — a real prompt usually triggers several rows at once.
>
> **Key takeaway:** If you can't point to the requirement or number that demands a component, you're adding it from memory of a similar-looking problem, and an interviewer probing "why is that there" will expose it immediately.

---
module: 05-hld-problems
topic: Methodology
status: unread
tags: [05-hld-problems, methodology, architecture, decision-tree]
---
# Step 4 — Deriving the Architecture

By this point you have requirements ([Step 1](01-requirements-and-scope.md)), numbers ([Step 2](02-capacity-estimation.md)), and a contract ([Step 3](03-api-and-data-model.md)). This step turns those into boxes on a diagram. It's a lookup against symptoms you already have in hand, not a creative leap — the table below is the lookup, the same way [05-spotting-the-pattern.md](../../06-lld/00-methodology/05-spotting-the-pattern.md) is the lookup on the LLD side.

---

## The signal table

| Requirement / number symptom | Component it forces | Why it fits | Full reference |
|---|---|---|---|
| Multiple app servers needed (QPS beyond one box's capacity, per [Step 2](02-capacity-estimation.md)) | **Load Balancer** | Distributes requests across horizontally scaled instances; also gives you health-check-based failover | [02-building-blocks/](../../02-building-blocks/) |
| Same data read far more often than it's written (read:write ratio skewed, e.g., 10:1+) | **Cache** (Redis/Memcached, or CDN if content is static/media) | Absorbs read load off the primary DB; cache-aside is the default pattern | [decision-trees.md](../../08-reference/decision-trees.md) |
| Storage volume in the multi-TB+ range, or content is large binary (images, video, article HTML) | **Object Storage** (S3-class) | Relational DBs aren't built for blob volume at this scale; store a reference, not the blob, in the DB row | [architecture-by-scale.md](../../07-interview-templates/02-cheat-sheets/04-architecture-by-scale.md) |
| An operation is slow, unreliable-external-dependency-based, or doesn't need to block the caller's response (fetch-and-clean, video transcode, email send) | **Message Queue + Worker** | Decouples the fast synchronous path from the slow/unreliable async work; also smooths traffic spikes | [02-building-blocks/](../../02-building-blocks/) |
| A single DB instance's storage or write-QPS exceeds what one machine can hold/serve (per Step 2 numbers) | **Sharding / Partitioning** | Splits data across multiple DB instances by a key (user ID, geographic region, hash) | [decision-trees.md](../../08-reference/decision-trees.md) |
| Read-heavy DB load that a cache alone doesn't fully absorb, but writes stay on one primary | **Read Replicas** | Scales read capacity independently of write capacity; introduces replication lag to reason about | [decision-trees.md](../../08-reference/decision-trees.md) |
| Consistency requirement says "a user must see their own write immediately" (read-your-writes) | **Route reads-after-write to the primary** (or use sticky sessions / read-your-writes tokens) | A stale replica read right after a write breaks this specific requirement — must be handled explicitly, not assumed away | [system-design-glossary.md](../../08-reference/system-design-glossary.md) |
| Requirement explicitly tolerates eventual consistency across devices/replicas (per Step 1) | **Async replication is acceptable** — don't over-engineer strong consistency | Matching the actual tolerance from Step 1 avoids adding consensus machinery (e.g., Paxos/Raft-backed strong consistency) nothing in the requirements asked for | [system-design-glossary.md](../../08-reference/system-design-glossary.md) |
| Need to find "nearest" or "most relevant" item among many nodes/servers that can be added/removed | **Consistent Hashing** | Minimizes redistribution when nodes are added/removed, vs. naive `hash % N` | [system-design-glossary.md](../../08-reference/system-design-glossary.md) |
| Global user base, or requirement explicitly states multi-region / low-latency-worldwide | **CDN + Multi-region deployment** | Pushes static/cacheable content close to users; multi-region duplicates the whole stack, which is a large jump in complexity — only take it if Step 1 actually asked for it | [architecture-by-scale.md](../../07-interview-templates/02-cheat-sheets/04-architecture-by-scale.md) |
| Search/filter across free text or many attributes at scale | **Search Index** (inverted index / Elasticsearch-class system) | A relational `LIKE '%term%'` doesn't scale; a dedicated index does prefix/full-text search efficiently | [02-building-blocks/](../../02-building-blocks/) |
| Real-time push to many connected clients (chat, live scores, notifications) | **WebSockets / long-polling + pub-sub fanout** | HTTP request/response can't push; a persistent connection plus a fanout layer is needed for many-to-many delivery | [02-building-blocks/](../../02-building-blocks/) |
| A single write path is a bottleneck because many producers write the same hot key/record | **Write batching / buffering, or a dedicated counter service** | Prevents lock contention on one row; batches writes to reduce per-write overhead | [system-design-glossary.md](../../08-reference/system-design-glossary.md) |

Notice the shape: the left column is always something you already wrote down in Steps 1-3. If you find yourself wanting to add a row's component but can't point to the matching left-column phrase in your own requirements/numbers, that's the signal you're pattern-matching from a memorized problem, not deriving.

---

## Build order: draw left to right, along the request path

```
[Diagram Build Order]
client → [CDN, if static/media-heavy]
       → [Load Balancer]
       → [App/API tier, horizontally scaled]
       → [Cache] ←→ [Primary DB (+ read replicas / shards, if justified)]
       → [Object Storage, if large blobs]
       → (branch) [Message Queue] → [Workers] → (writes back to DB/storage)
```

Drawing in this order — request path first, async/background paths last — mirrors how the system actually processes a request and keeps you from front-loading infrastructure (queues, search indexes) before you've established *why* the synchronous path alone isn't enough.

---

## Worked mini-example: continuing the read-it-later service

Pulling from Steps 1-3: 10M users, ~1,500-4,500 QPS, 10 TB/day storage, async save-and-clean flow, eventual-consistency-tolerant read-state sync.

| Symptom (from Steps 1-3) | Component |
|---|---|
| ~4,500 QPS peak, beyond one server | Load balancer + horizontally scaled app tier |
| Reads (list articles, view content) >> writes (saves), and content doesn't change once cleaned | Cache in front of the `Article` metadata DB; CDN for serving cleaned content (mostly static once written) |
| 10 TB/day of article content | Object storage for `ArticleContent`; DB stores only the reference |
| `POST /articles` triggers fetch-and-clean, which is slow and can fail (dead links, slow origin sites) | Message queue + worker pool: API enqueues a job, returns `202`, worker fetches/cleans/writes to object storage, updates `Article.status` to `ready` |
| Read-state sync tolerates eventual consistency (Step 1) | No need for synchronous cross-device consistency machinery — async replication between the primary DB and any read replicas is sufficient |
| Metadata storage (~10M users × tens of rows each) isn't yet at a scale requiring sharding (Step 2's numbers don't demand it) | Single logical primary DB with read replicas — sharding is not yet justified; explicitly note this as a decision, not an oversight |

**Resulting diagram (left to right):** Client → CDN (serves cached cleaned content) → Load Balancer → App tier → [Cache ←→ Primary DB + read replicas] for metadata; App tier also → Message Queue → Worker pool → Object Storage (writes cleaned content) + DB (updates status).

Every box above has a one-line justification citing a specific requirement or number — that traceability is the actual deliverable of this step, not the diagram's visual polish.

---

## Common mistakes

- **Drawing components in isolation from the request path.** A queue floating on the diagram with no arrow showing what enqueues to it or what consumes from it signals the box was added by reflex, not derivation.
- **Adding a component whose justification is "for scale" with no number attached.** "For scale" is not a citation — "because Step 2 says 10 TB/day" is.
- **Skipping sharding-or-not as an explicit decision.** Even "we don't need sharding yet, because Step 2's numbers are well within a single instance's capacity" is a decision worth stating — silence here reads as not having considered it.
- **Solving for a consistency requirement stronger than what Step 1 actually specified.** Adding Raft-backed strong consistency for a system that explicitly said "a few seconds of lag is fine" is over-engineering exactly as much as under-engineering the reverse case.

---

## Interview Angles

- Narrate the row you're applying as you draw: "reads dominate writes here, so — cache" — this makes the derivation visible instead of leaving the interviewer to infer whether you derived it or recalled it.
- When two rows conflict or trade off against each other (e.g., strong consistency vs. horizontal read scaling), say so explicitly and state your choice — this is where the conversation naturally hands off to [Step 5](05-identifying-the-bottleneck.md).
- If you're unsure whether a component is justified, default to leaving it out and stating the boundary condition that would add it later ("if write volume grew 10x, I'd shard by user ID here") — this reads as calibrated judgment, not a gap.

**Next:** [05-identifying-the-bottleneck.md](05-identifying-the-bottleneck.md) — picking the one piece worth a deep dive, and going deep on it.
