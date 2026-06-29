---
module: 05-hld-problems
topic: Easy
status: interview-ready
tags: [05-hld-problems, system-design, easy]
---
# Design Pastebin

> **Difficulty**: Easy
> **Topics**: Object Storage, Key Generation, CDN, Expiry
> **Time**: 45 min
> **Companies**: Amazon, Google

---

## Clarifying Questions

1. "What's the max paste size — 1 MB, 10 MB, or larger?"
2. "Do we need user accounts, or are anonymous pastes acceptable?"
3. "What's the expiry model — mandatory TTL, or optional/no-expiry for paid users?"
4. "Do we need private pastes (secret URL) or just public by default?"
5. "How many pastes per day and what's the read/write ratio?"
6. "Do we need version history or just latest content per paste?"

---

## Back-of-Envelope

```
Scale:
  10M pastes/day = ~115 writes/sec
  100M reads/day = ~1,160 reads/sec (10:1 read/write ratio)

Storage:
  1MB avg × 10M pastes/day × 365 days = 3.65 PB/year → S3 + Intelligent-Tiering
  Metadata only: 1KB/paste × 10M/day × 365 = 3.65 TB/year → PostgreSQL (manageable)

Key space:
  7-char Base62 = 62^7 = ~3.5 trillion unique keys (essentially infinite)

Cache:
  Top 10% of pastes get 90% of reads → cache 1M pastes × 1MB = 1TB in Redis
  Realistically use CDN for public pastes; Redis for hot metadata
```

---

## APIs

```
// Create paste (small content inline, large via presigned URL)
POST /api/v1/pastes
  { "content": "...", "language": "python", "expires_in": 86400, "private": false }
  → { "key": "aB3kZ9p", "url": "https://pastebin.io/aB3kZ9p", "expires_at": "..." }

// Read paste
GET /api/v1/pastes/{key}
  → { "content": "...", "language": "python", "created_at": "...", "view_count": 42 }
  → 410 Gone if expired (not 404 — 404 means never existed)

// Large paste: get presigned S3 URL for direct upload
POST /api/v1/pastes/upload-url
  → { "upload_url": "https://s3.../presigned", "key": "aB3kZ9p" }
```

---

## Architecture

```
Client
  │
  ├── Small paste (<1MB) ──► API Server ──► PostgreSQL (metadata) + S3 (content)
  │
  └── Large paste (>1MB) ──► API Server ──► Presigned S3 URL (client uploads direct)
                                               └── S3 Event → update metadata DB

Read path:
  Client ──► CDN (CloudFront)
               ├── HIT → serve from edge (public pastes only)
               └── MISS → API Server → Redis (hot metadata) → PostgreSQL + S3 URL

Key Generation Service (KGS):
  - Pre-generates pool of 7-char Base62 keys offline
  - Stores in Redis list (LPOP to claim a key atomically)
  - Refills pool asynchronously when below threshold
```

```
Expiry pipeline:
  Scheduled job (every 1 hour)
    → scan pastes WHERE expires_at < NOW() AND deleted = false
    → soft-delete (set deleted=true)
    → async S3 lifecycle rule deletes actual objects after grace period
    → Redis DEL for cached keys
```

---

## Data Model

```sql
CREATE TABLE pastes (
    id           BIGSERIAL PRIMARY KEY,
    short_key    VARCHAR(10) UNIQUE NOT NULL,   -- e.g., "aB3kZ9p"
    s3_object_key VARCHAR(255) NOT NULL,         -- S3 path to content
    language     VARCHAR(50),
    created_at   TIMESTAMP DEFAULT NOW(),
    expires_at   TIMESTAMP,
    view_count   BIGINT DEFAULT 0,
    is_private   BOOLEAN DEFAULT false,
    owner_id     BIGINT,                         -- NULL for anonymous
    deleted      BOOLEAN DEFAULT false
);

-- Partition by created_at month for efficient bulk expiry
-- DROP PARTITION is O(1) vs row-level DELETE on 300M rows
CREATE INDEX idx_expires_at ON pastes (expires_at) WHERE deleted = false;
CREATE INDEX idx_owner_id   ON pastes (owner_id)   WHERE owner_id IS NOT NULL;
```

---

## Key Design Decisions

**1. Pre-generated key pool (KGS) over hash-based short keys**
Hash the content and take first 7 chars → collision handling required in the hot path (check DB, retry). Pre-generate keys offline → LPOP from Redis is atomic, no collision possible. KGS can be a single process or a small Kafka-backed pipeline. Keys are truly random (no content-correlation), preventing enumeration attacks.

**2. S3 for content, PostgreSQL for metadata only**
API servers never buffer large paste content in memory. Presigned PUT URLs let clients upload directly to S3 — no proxy overhead. PostgreSQL stores only the S3 key pointer and metadata (~1KB/paste). CDN (CloudFront) caches public paste content at the edge — API servers handle only cache misses.

**3. 410 Gone on expired paste reads**
HTTP 410 = "resource existed but is permanently gone." HTTP 404 = "never existed." Search engines and bots treat 410 as a signal to remove the URL from their index — correct for expired pastes. 404 could cause unnecessary re-crawl attempts.

**4. Partition DROP for bulk expiry (not row DELETE)**
At 10M pastes/day × 30-day retention = 300M rows to delete monthly. `DELETE WHERE expires_at < cutoff` causes table bloat, heavy I/O, and long undo logs. Partition the table by `created_at` month (PostgreSQL range partitioning). Drop entire partition (DDL operation, O(1)) when all pastes in that month have expired. Far cheaper than row-level cleanup.

---

## Deep Dives

**View counting without DB writes on every read**
Naive: `UPDATE pastes SET view_count = view_count + 1` on every read → write bottleneck at 1K reads/sec.
Solution: Redis `INCR views:{paste_id}`. Periodic job (every 30s) reads and flushes increments to the DB in a batch UPDATE. At 1K reads/sec, Redis handles this trivially; DB gets one batch write per 30 seconds per paste.

**Private paste access control**
No auth token needed: generate a 32-byte cryptographically random access token and embed it in the URL (`/p/{key}?token={32-byte-base62}`). The unguessable URL is the access control. Store the hashed token in the DB. On read: hash the provided token, compare with stored hash — if no match, 403 Forbidden. This is the pattern Dropbox/Google Docs use for shared links.

---

## Failure Scenarios

| Failure | Impact | Mitigation |
|---------|--------|------------|
| S3 unavailable on write | Paste creation fails | Return 503; client retries. Presigned URL approach means no data loss (S3 is the source of truth) |
| KGS Redis down | Can't claim new keys | Fall back to hash-based key generation with collision retry in hot path |
| Expiry job crashes mid-run | Some expired pastes remain visible briefly | Idempotent soft-delete: re-running the job is safe. Redis TTL also expires cached copies |
| Viral paste (100K concurrent reads) | API server bottleneck | CDN absorbs public paste reads; API server only serves cache misses. Private pastes: Redis caches presigned URL (5-min TTL) |
| DB full due to missing partition drop | Table bloat, slow queries | Alert on partition age > 35 days; monthly partition drop is automated |

---

## Interview Questions Asked

### Amazon
1. **"Design Pastebin with a 30-day expiry on all pastes — walk me through the deletion pipeline."** → Tests background job design: a scheduled job (cron or SQS delayed message) scans for expired pastes, deletes from S3 and the metadata DB, and removes the short key from Redis. The interviewer wants to hear about soft-delete first (mark expired, hard-delete async) to avoid race conditions with in-flight reads.
2. **"How do you generate short keys that don't collide at 10M pastes/day?"** → Pre-generation via Key Generation Service (KGS) that produces a pool of random base62 keys and marks them used atomically. Alternatively, hash the content and take the first 7 characters — but then you must handle the collision case. KGS is cleaner because collision handling is done offline.

### Google
1. **"How would you deduplicate content — if two users paste identical text, should you store it once?"** → Content-addressable storage: hash the raw content (SHA-256), use the hash as the S3 object key. Multiple paste records can point to the same S3 object. Dedup is free at write time; the complication is deletion — use reference counting or tombstoning so the S3 object isn't deleted while other pastes reference it.
2. **"How would you add syntax highlighting for 50 programming languages without slowing down reads?"** → Client-side rendering with a JS library (Prism.js, highlight.js) — zero backend cost, language detection from the stored `language` field, no additional latency. Only consider server-side rendering if SEO is required (bots don't execute JS).

### Common Follow-ups
1. **"How do you handle pastes larger than 1 MB?"** → Stream directly to S3 using multipart upload — bypass the API server's memory entirely. Set a hard cap (e.g., 10 MB) enforced at the load balancer. Store only metadata in the DB; never buffer the full payload in the application tier.
2. **"How do you implement private pastes (accessible only via secret link)?"** → Generate a high-entropy random token (32 bytes, base62-encoded) as the paste URL instead of a short key. No authentication required — the unguessable URL is the access control. Optionally add an optional password layer (bcrypt hash stored in the DB) for extra protection.
3. **"How do you count views without hammering the database on every read?"** → Write view events to a Kafka topic; a batch consumer aggregates counts and flushes to the DB every 30 seconds. Alternatively, use Redis INCR on a `views:{paste_id}` key and periodically sync to the DB. Never do a synchronous DB write on each page view.
4. **"How do you prevent abuse — someone pasting malware or CSAM?"** → Hash-based blocklist (PhotoDNA for CSAM, MD5/SHA-1 of known malware). Content scanning pipeline triggered asynchronously after upload — paste is visible immediately but flagged/removed within seconds if matched. Rate limiting per IP/account prevents mass upload of new variants.

---

## Interviewer Follow-Up Questions

**On storage and retrieval:**
- "Your pastebin supports 1 MB pastes at 10M pastes/day — how much storage do you need in a year and how does that change your architecture?" → 1 MB × 10M × 365 = ~3.65 PB/year. At that scale: S3 with Intelligent-Tiering (hot → Standard, cold → Glacier after 30 days). The DB stores only metadata (key, owner, created_at, expiry, content_type, s3_key) — never the raw content. Separate hot-metadata cache (Redis) from cold storage (S3).
- "How do you serve a 10 MB paste to 100K concurrent users without your API servers becoming the bottleneck?" → Presigned S3 URL: API server returns a short-lived URL pointing directly to S3/CloudFront, client fetches content directly from CDN. API servers never touch the paste body after upload. For private pastes: signed CloudFront URL with short expiry (5 min).
- "A user edits a paste — do you overwrite in place or create a new version?" → Never overwrite in place (S3 is eventually consistent on overwrites, and you lose history). Write a new S3 object, create a new DB row with an incremented version number, and keep the old version accessible via version history. Add a `latest_version_id` pointer in the paste metadata for fast current-version reads.

**On expiry and cleanup:**
- "You soft-delete expired pastes. At 10M pastes/day with 30-day TTL, you have 300M rows soft-deleted. How do you clean this up without killing the DB?" → Partition the metadata table by `created_at` month. Drop old partitions (DDL, not row-level DELETE) when they're past retention. Row-level DELETE locks the table and generates huge undo logs. Partition drop is O(1) and metadata-only.
- "A paste expires while someone is actively reading it. What should they see?" → The read path checks expiry at request time: if expired, return 410 Gone (not 404 — 404 means never existed; 410 means existed but is gone). Don't delete from S3 in the middle of a read — the presigned URL should still work until its own short expiry (5 min). Deletion from S3 happens asynchronously after a grace period.

**On abuse and security:**
- "How do you stop someone from using your pastebin as free file hosting by creating pastes with no expiry?" → Enforce limits: max paste size (1 MB for anonymous, 10 MB for authenticated), mandatory expiry for anonymous users (max 30 days), rate limit by IP (10 pastes/hour anonymous). Authenticated users can have longer retention tied to a paid plan. Flag accounts creating large pastes rapidly.
- "Two users paste the same source code. Should you deduplicate the S3 storage?" → Hash the content (SHA-256), use the hash as the S3 key. Reference counting in the DB tracks how many paste records point to the same S3 object. Delete the S3 object only when ref count hits zero. Trade-off: content-addressable storage enables dedup but complicates deletion and makes abuse harder to handle (shared content between users).
