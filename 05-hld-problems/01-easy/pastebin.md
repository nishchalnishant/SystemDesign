> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design Pastebin — a text sharing service that introduces object storage, CDN delivery, and TTL-based expiration in a simple read-heavy architecture.
>
> **Key design decisions:**
> - Storage split: metadata (paste_id, user_id, created_at, expires_at, size, visibility) in DB; content in object storage (S3/GCS)
> - ID generation: random 8-char Base62 string (collision probability negligible at Pastebin scale); check uniqueness in DB before creating
> - CDN delivery: paste content served via CDN (CloudFront); origin-pull on first request; TTL matches paste expiration
> - Expiration: lazy deletion (check on read) + background cleanup job (scan for expired rows daily); don't rely on DB TTL alone
> - Privacy model: public (indexed), unlisted (URL is the password — not searchable), private (requires auth)
> - Read vs write ratio: reads dominate (read:write ≈ 100:1); cache hot pastes in Redis; metadata for analytics only
> - Abuse prevention: rate limit paste creation per IP; scan content for malware/abuse keywords; CAPTCHA for anonymous users
>
> **Key takeaway:** Pastebin is URL Shortener + object storage — key insight is to separate metadata (DB) from content (S3), and serve content through CDN to avoid origin load.

---
module: 05-hld-problems
topic: Easy
status: unread
tags: [05-hld-problems, system-design, easy, blob-storage, cdn, object-storage]
---
# Design Pastebin

> **Difficulty**: Easy | **Asked at**: Amazon, Google, Dropbox

---

## Problem Statement

Design a service like Pastebin where users can paste text content (code, logs, notes) and share it via a short unique URL. The paste is accessible publicly or privately. Users can optionally set an expiration time.

---

## Functional Requirements

1. **Create paste**: Submit text content, receive a unique short URL
2. **Read paste**: Access paste content via its URL
3. **Expiration**: Pastes can have an optional TTL (1 hour, 1 day, 1 week, forever)
4. **Syntax highlighting**: Display code with language-specific highlighting
5. **Private pastes**: Optionally password-protected or unlisted (URL is the only access control)

---

## Non-Functional Requirements

- **Scale**: 1M new pastes/day → 12 writes/sec; 10M reads/day → 115 reads/sec
- **Size**: Text content up to 10 MB per paste; most pastes < 10 KB
- **Latency**: Read P99 < 50ms (hot pastes cached at CDN edge)
- **Durability**: Paste content must never be lost (durable object storage)
- **Availability**: 99.9% uptime; brief degradation acceptable during maintenance

---

## Core Entities

| Entity | Key Fields |
|--------|-----------|
| `Paste` | paste_id (short code), user_id, title, language, created_at, expires_at, visibility, content_url |
| `User` | user_id, username, email, tier |
| `PasteContent` | stored in S3 (key: paste_id), not in the database |

---

## API Design

```http
POST /api/v1/pastes
Body: {
  "content": "def hello(): ...",
  "language": "python",
  "title": "My code",
  "expires_in": "86400",   // seconds; null = no expiry
  "visibility": "public"   // public | private | unlisted
}
Response 201: {
  "paste_id": "abc1234",
  "url": "https://paste.ly/abc1234",
  "expires_at": "2026-07-01T00:00:00Z"
}

GET /api/v1/pastes/{paste_id}
Response 200: {
  "paste_id": "abc1234",
  "content": "def hello(): ...",
  "language": "python",
  "created_at": "...",
  "view_count": 42
}

DELETE /api/v1/pastes/{paste_id}
Response 204
```

---

## High-Level Design

```
Client
  │
  ▼
CDN (CloudFront)
  │  ← caches rendered paste pages for public URLs
  ▼
Load Balancer
  │
  ▼
App Servers (stateless)
  │          │
  ▼          ▼
PostgreSQL   S3 (or equivalent blob store)
(metadata)   (paste content)
  │
  ▼
Redis
(hot paste cache + paste_id counter)
```

**Separation of metadata and content**: The paste metadata (paste_id, user_id, language, timestamps, visibility) lives in PostgreSQL. The actual text content is stored in S3 as a blob (key = paste_id). This separation is critical:
- Metadata table stays small and indexable
- Content can be arbitrarily large (up to 10 MB) without bloating the database
- S3 handles durability (11 nines) and geo-replication automatically

**Paste ID generation**: Same approach as URL shortener. A distributed counter → Base62 encode → 7-character unique ID. 62^7 = 3.5 trillion possible paste IDs.

**Read path**: CDN serves cached HTML for popular public pastes. Cache miss → App server fetches metadata from PostgreSQL, content from S3 (or Redis hot cache), renders response.

**Write path**: App server generates paste_id → stores metadata in PostgreSQL → uploads content blob to S3. Both writes must succeed (use a transaction and a compensating delete if S3 upload fails).

---

## Deep Dive 1: Storing Large Content Efficiently

**Problem**: Paste content can be up to 10 MB. Storing it in a PostgreSQL TEXT column works for small pastes but creates issues at scale:
- Bloats the database, increasing backup size and vacuum time
- Makes row-level replication transfer the full content on every update
- Can't be served directly from a CDN without going through the app server

**Solution: Object storage (S3)**. Store content as a blob keyed by paste_id. PostgreSQL stores only a `content_url` column pointing to the S3 object.

**Reading content**:
1. App server fetches metadata from PostgreSQL (paste_id, language, visibility, content_url)
2. App server fetches content from S3 using the content_url
3. Renders the full paste page

**Optimization: Presigned URLs for large pastes**: For pastes > 100 KB, instead of proxying the content through the app server, return a presigned S3 URL to the client. The client downloads directly from S3 (bypassing app server bandwidth). Reduces app server load for large pastes.

**Content deduplication**: Hash the content (SHA256). If two users submit identical content, store only one S3 object. Track hash → s3_key in PostgreSQL. Saves storage for common snippets (standard boilerplate, license headers).

> 🎯 **Staff signal:** The move is *keeping the blob out of the primary database entirely* — Postgres holds only metadata + a `content_url`, and the 10 MB body lives in S3. Name the failure you're avoiding: TEXT columns bloat every backup, inflate replication traffic on rows that never change, and vacuum grinds; the DB's job is fast metadata lookup, not blob serving. The senior extension is the presigned URL — for large pastes you hand the client a direct-to-S3 link so the paste body never traverses app-server bandwidth. Recognizing that content and metadata have different scaling axes and belong in different stores is the E5→E6 framing.

---

## Deep Dive 2: Expiration and Cleanup

**Problem**: Expired pastes must be deleted to reclaim storage. S3 charges per GB-month — unbounded paste accumulation is expensive.

**Approach 1: Lazy deletion** (simple): On read, check `expires_at`. If past expiry, return 404 and delete the paste asynchronously. Never runs proactively — storage cost accumulates.

**Approach 2: Scheduled sweeper** (recommended): A background job runs every hour. Query: `SELECT paste_id FROM pastes WHERE expires_at < NOW() AND deleted_at IS NULL`. For each expired paste: delete from S3, soft-delete in PostgreSQL (`deleted_at = NOW()`). A nightly job hard-deletes soft-deleted rows older than 7 days.

**Index on expires_at**: `CREATE INDEX idx_pastes_expires_at ON pastes(expires_at) WHERE expires_at IS NOT NULL;` — partial index covering only expiring pastes. Sweeper query is O(expired pastes) not O(all pastes).

**S3 Lifecycle rules**: Configure S3 object lifecycle rule to delete objects with `expires-at` tag after the TTL. Belt-and-suspenders: even if the sweeper misses a paste, S3 eventually cleans it up.

> 🎯 **Staff signal:** The senior instinct is treating cleanup as *defense in depth across two independent systems*, because a single sweeper is a single point of storage-leak. The app-level sweeper gives you prompt, queryable deletion — and the partial index `WHERE expires_at IS NOT NULL` keeps it O(expiring pastes), not O(all pastes), so it stays cheap as the table grows. The S3 lifecycle rule is the backstop that reclaims cost even if the sweeper is down for a week. Naming *why* you don't trust one deletion path — a missed delete is silent, unbounded S3 spend — is the E5→E6 line.

---

## Deep Dive 3: Abuse Prevention

**Problem**: Pastebin is trivially abused for storing malware, stolen credentials, scraped data, or phishing pages. Without controls, the service becomes a content distribution network for attackers.

**Rate limiting**: 5 pastes/minute per IP for anonymous users. 60 pastes/minute per authenticated user. Implemented via Redis token bucket (see Rate Limiter design).

**Content scanning**:
1. **Size limit**: Hard cap at 10 MB. Reject immediately.
2. **Known malware hashes**: Hash new paste content with SHA256. Check against a local bloom filter seeded from VirusTotal/ClamAV signatures. Flag matches for manual review.
3. **URL scanning**: Extract URLs from content. Check against Google Safe Browsing API. Reject pastes containing known malicious URLs.
4. **CSAM detection**: Integrate PhotoDNA or equivalent hash-matching for image data in pastes.

**Takedown API**: Legal compliance — provide a `POST /admin/takedown/{paste_id}` endpoint. Immediately soft-deletes the paste and logs the reason. DMCA and government requests handled here.

**Spam detection**: ML classifier (logistic regression on content features) flags spammy pastes (gibberish, template credential dumps). Flagged pastes go to a review queue rather than being published immediately.

> 🎯 **Staff signal:** The insight most candidates miss is that a public paste service is *inherently* a malware/phishing CDN unless abuse control is a first-class subsystem, not an afterthought. The layered answer matters: a local bloom filter seeded from known-malware hashes gives an O(1) in-memory reject before you ever store the blob, and Google Safe Browsing catches malicious URLs the content links to. Name the tradeoff of async-vs-sync — synchronous scanning on the write path adds latency but stops bad content from ever being reachable, whereas a review queue trades a window of exposure for throughput. Treating abuse as an architectural pillar with a latency budget is the E5→E6 framing.

---

## Interviewer Questions by Level

**Junior**:
- Why do you store paste content in S3 instead of the database?
- How do you generate unique paste IDs?
- What happens when a paste expires — how is it deleted?

**Mid-level**:
- How does the CDN work for serving paste content? What cache headers do you set?
- How do you handle concurrent writes where two users submit the same content?
- Design the expiration cleanup system. What's the index strategy?

**Senior**:
- How would you support real-time collaborative editing of pastes (like Google Docs)?
- How do you balance privacy (private pastes) with abuse prevention (scanning content)?
- A viral paste gets 1M views in 5 minutes. Walk me through what happens at each layer and where you'd see failures.
- How would you implement paste versioning (edit history)?

---

## Related

**Concepts used in this design**

- [CDN](../../02-building-blocks/01-networking/05-cdn.md)
- [Caching Layer](../../02-building-blocks/02-performance/01-caching-layer.md)
- [Sharding](../../02-building-blocks/03-data-partitioning/01-sharding.md)
- [Storage Fundamentals](../../01-foundations/02-hardware-and-networking/01-storage-fundamentals.md)

**Practice next**

- [URL Shortener](../01-easy/url-shortener.md)
- [Google Drive](../03-hard/google-drive.md)

Blob storage plus metadata is the shared shape.

**Frameworks**: [HLD Template](../../07-interview-templates/01-frameworks/01-hld-template.md) · [Capacity Estimation](../../07-interview-templates/02-cheat-sheets/02-capacity-estimation.md) · [Trade-offs Cheat Sheet](../../07-interview-templates/02-cheat-sheets/01-trade-offs-cheat-sheet.md)
