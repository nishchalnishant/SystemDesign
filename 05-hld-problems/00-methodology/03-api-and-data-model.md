> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Step 3 — deriving the API contract and the data model directly from the Step 1 requirements list, before drawing a single architecture box.
>
> **Key ideas:**
> - Every requirement produces at least one endpoint. If a requirement doesn't map to an endpoint, either the requirement is incomplete or the API is missing something — this is a mechanical cross-check, not a formality.
> - Design the API first, the schema second — the API is the contract callers rely on; the schema is an implementation detail that can change without breaking anyone.
> - For the data model, decide entities and their fields from the API request/response shapes you just wrote, then decide SQL vs. NoSQL using [decision-trees.md](../../08-reference/decision-trees.md) — don't pick the database before you know what you're storing.
> - Keep the API minimal — one endpoint per requirement, not one per imagined future feature. Extra endpoints invite "why does this exist" questions you can't answer from the requirements list.
>
> **Key takeaway:** If you can't point to which Step 1 requirement justifies a given endpoint or field, you're speculating, not deriving — cut it or trace it.

---
module: 05-hld-problems
topic: Methodology
status: unread
tags: [05-hld-problems, methodology, api-design, data-model]
---
# Step 3 — API & Data Model

Before any boxes get drawn, define the contract. This step is intentionally mechanical — it's a translation exercise from the requirements list ([Step 1](01-requirements-and-scope.md)), not a creative one, and treating it that way keeps you from designing endpoints or fields no requirement actually asked for.

---

## The mechanical pass: requirement → endpoint

```
[API Derivation]
├── For each functional requirement, ask: "what's the minimal
│   request/response that satisfies this?"
│   ├── "User saves a URL" → POST /articles {url}
│   ├── "User lists saved articles, paginated, most-recent-first"
│   │   → GET /articles?cursor=&limit= → [{id, title, savedAt, ...}]
│   ├── "User marks read/unread" → PATCH /articles/{id} {status}
│   └── "User views article content offline-first"
│       → GET /articles/{id}/content (returns the cleaned copy, not
│         a redirect to the original URL)
├── Prefer cursor-based pagination over offset for anything at scale
│   (offset pagination degrades badly past a few thousand rows —
│   this is worth saying out loud, it's a small but real signal)
└── Note which endpoints are read-heavy vs. write-heavy — this feeds
    directly into Step 4 (read/write paths often end up as
    physically different pipelines)
```

Do this as a quick table, not full OpenAPI syntax — interviewers want to see the mapping from requirement to contract, not swagger-level completeness.

---

## The mechanical pass: API shape → data model

Once the API exists, the fields you just wrote *are* your first draft of the schema — you don't invent new fields at this stage, you organize the ones already implied by the API.

```
[Data Model Derivation]
├── 1. List entities implied by the API (User, Article, ReadState)
├── 2. For each entity, list fields from the request/response
│      bodies you already wrote
├── 3. Identify relationships (User 1—N Article; Article 1—1
│      CleanedContent, stored separately because it's large and
│      accessed differently than metadata)
└── 4. THEN pick SQL vs. NoSQL — using the actual access pattern:
       ├── Need joins / transactions / strong relational integrity?
       │   → SQL (see decision-trees.md)
       ├── Simple key-value lookups at very high scale, schema
       │   loosely structured or evolving? → NoSQL
       └── Large binary/blob content (article HTML, images)?
           → Object storage (S3-class), referenced by URL/key
             from the relational row, never stored inline
```

Cross-reference: [08-reference/decision-trees.md](../../08-reference/decision-trees.md) has the full SQL-vs-NoSQL and consistency-model trees — use them here, don't re-derive the tradeoffs from scratch in the interview.

---

## Worked mini-example: continuing the read-it-later service

**API (derived directly from the 7 Step 1 requirements):**

| Requirement | Endpoint |
|---|---|
| 1. Save a URL, fetch+store cleaned copy | `POST /articles {url}` → `202 Accepted {articleId, status: "processing"}` (async — fetching/cleaning takes time, so this can't be synchronous) |
| 2. List saved articles, paginated | `GET /articles?cursor=&limit=20` → `[{id, title, url, savedAt, readState}]` |
| 3. Mark read/unread, synced across devices | `PATCH /articles/{id} {readState}` |
| 4. View content offline-first | `GET /articles/{id}/content` → cleaned HTML/text payload |

Note requirement 1 forcing an async response (`202`, not `200`) — that single detail is already a hint that Step 4 will need a queue or background worker for the fetch-and-clean step, derived here, not assumed.

**Data model:**

- `User { id, email, createdAt }`
- `Article { id, userId, url, title, savedAt, readState, contentRef }` — `contentRef` points to object storage, not an inline blob
- `ArticleContent` (object storage, not a DB row) — keyed by `contentRef`, holds the cleaned HTML/text (~200 KB average, per [Step 2](02-capacity-estimation.md))

**SQL vs. NoSQL:** `User` and `Article` metadata need relational integrity (a user's article list, filtering by `readState`, pagination) but no complex joins — either a relational DB or a document store both work; the read-state-sync-across-devices requirement (eventual consistency acceptable, per Step 1) leans toward a design that tolerates replication lag, so a horizontally-scalable document store (e.g., a Dynamo-style store) is defensible, but so is sharded Postgres — state the tradeoff rather than picking silently. `ArticleContent` is unambiguous: object storage, given the 10 TB/day figure from Step 2 — no relational DB stores blobs at that volume.

---

## Common mistakes

- **Jumping straight to schema without writing the API first.** The API is what callers depend on; designing the schema first tends to leak storage details into the contract (e.g., exposing an internal `content_blob_key` field directly instead of a clean `contentRef`).
- **Adding endpoints with no matching requirement.** "We'll probably also want a search endpoint" — if search wasn't in Step 1, either go back and ask, or explicitly park it as future scope. Don't design it now.
- **Picking the database before defining what's being stored.** "We'll use Cassandra" stated before the entities and access patterns exist is a memorized-answer smell, not a derived one.
- **Missing the async signal.** Requirements that involve any kind of processing delay (fetching, cleaning, transcoding, transcribing) should produce a `202`-style API response — designing it as synchronous and only fixing it in Step 4 wastes a chance to show you saw it early.

---

## Interview Angles

- Narrate the trace explicitly: "this endpoint exists because of requirement 2" — it's a cheap, visible way to demonstrate the whole approach is requirement-driven, not memorized.
- When an API detail (like async processing) foreshadows an architecture decision, say so out loud even though you haven't drawn the component yet: "I'll come back to this — async here likely means a queue in the architecture."
- If asked "why not just one big table," the answer should reference access patterns and volume from Step 2/3, not a generic "NoSQL scales better" — the generic answer is exactly what signals SDE-2, not SDE-3.

**Next:** [04-deriving-the-architecture.md](04-deriving-the-architecture.md) — the core derivation: requirement/number → component, mechanically.
