# Design Pastebin

> **Difficulty**: Easy
> **Topics**: Object Storage, Key Generation, TTL, Caching
> **Time**: 45-60 minutes
> **Companies**: Google, Amazon, GitHub (Gist)

---

## What Breaks Without This System?

A developer needs to share 50KB of a stack trace with a colleague on Slack. Slack's message size limit is 4,000 characters — the trace is truncated. They try emailing it — the security gateway blocks plaintext attachments. They paste it into a Google Doc, share the link — their colleague doesn't have a Google account and gets an access request dialog. The simple act of sharing text across systems is broken by access controls, size limits, and platform coupling.

At a larger scale: a CI/CD system generates 2MB build logs for every failed job. Without a paste service, logs are either truncated in the notification message, stored in a proprietary format that requires the CI login to view, or lost after 24 hours with no way to reference them in post-mortems.

The core gap: no neutral, universally accessible, size-agnostic, linkable text storage.

---

## Derive the Architecture

**Step 1 — Single server**
A Flask app with PostgreSQL: `POST /pastes` stores text in a `text` column, returns a short key. `GET /pastes/:key` reads it back. Works for low volume.

**Step 2 — What breaks at 1M pastes/day?**
- Storage: 1M pastes/day × 10KB avg = 10GB/day → 3.65TB/year. PostgreSQL `text` columns can hold this but storage cost and backup cost are high. Text is unstructured blob data — a relational DB is the wrong tool.
- Read/write ratio: 10:1 reads to writes. The same DB handling both creates read pressure on the write path.
- Large pastes: a 5MB paste in PostgreSQL blocks I/O for every concurrent query while it's being read.

**Step 3 — Split the concerns**

Store the paste **content** in S3 (object storage): unlimited size, cheap per-GB, CDN-compatible, lifecycle policies for TTL.
Store the **metadata** (key, created_at, TTL, user_id, access_count) in PostgreSQL: fast key lookups, rich queries.

```
POST /pastes:
  1. Generate a 6-character key (key generation service)
  2. PUT content to S3 at key "pastes/{key}"
  3. INSERT metadata row into PostgreSQL
  4. Return short URL

GET /pastes/:key:
  1. Read metadata from Redis cache (cache hit → skip PostgreSQL)
  2. If miss: read from PostgreSQL, populate cache
  3. Redirect to S3 presigned URL (or stream from CDN)
```

**Step 4 — Key generation**
Hash-based: SHA-256(content) → take first 6 base62 chars. Deterministic — identical content returns the same key (deduplication free). Collision probability negligible at 62^6 = 56B possible keys for 1M active pastes.
Pool-based (alternative): pre-generate 1M keys, store in a Redis list, pop on demand. Avoids hashing latency and guarantees uniqueness without collision checking.

**Step 5 — Expiry**
- Lazy: check `expires_at` on read, return 404 if expired. Simple but leaves orphaned data in S3/DB.
- Active: a daily cron queries `WHERE expires_at < NOW()`, deletes metadata rows, and calls `S3.deleteObjects()`.
- S3 lifecycle rules: set an S3 object expiry tag at upload time — AWS deletes the object automatically. No cron needed for storage reclaim.

**Step 6 — Scaling reads**
- Cache hot pastes in Redis (top 10% of pastes get 90% of reads).
- Serve content via CDN (CloudFront/Fastly) — S3 URLs route through CDN edge nodes, eliminating origin load for popular pastes entirely.

---

## Real-Life Analogy

Think of a whiteboard in a shared office. Someone writes a block of text on whiteboard #47. They walk up to a colleague and say "check whiteboard 47." The colleague goes, reads it, and walks away. The whiteboard gets erased at end of day unless someone specifically marks it "permanent."

Pastebin is that whiteboard system. You write content (value), you get a numbered URL (key), anyone with the URL can read it, and it expires unless you explicitly set it to persist. The challenge is managing millions of whiteboards, each potentially needing different expiry times, while keeping reads fast even for whiteboards that haven't been visited in weeks.

---

## Why This Is Hard

1. **Content vs. metadata separation**: Storing large blobs (10KB text) in a relational database alongside metadata causes row bloat and degrades index performance. Content belongs in object storage; metadata belongs in a database. Keeping these in sync atomically is non-trivial.
2. **Key generation at scale**: Generating unique 7-character keys without a central coordinator while preventing collisions requires either pre-generation pools or careful use of distributed IDs.
3. **Expiration is deceptively complex**: "Delete this paste after 24 hours" sounds easy. At 1M pastes/day, that's 1M deletions/day — a background job scanning millions of rows, competing with read traffic, causing DB spikes. Lazy deletion is simpler but wastes storage.
4. **Thundering herd on popular pastes**: A paste linked from Hacker News gets 50,000 simultaneous reads. Without caching, your S3 origin and metadata DB see 50,000 concurrent requests for the same object.
5. **Private pastes**: The security model is "security through obscurity" — a long, unguessable URL is the only access control. This means 16-character keys (not 7) for private pastes, no listing endpoints, and no search indexing.

---

## Requirements

### Functional Requirements
1. Users can paste text and get a unique shareable URL
2. Users can retrieve paste content via URL
3. Pastes can have expiration time (1 hour, 1 day, 1 month, never)
4. Support for custom short URLs (optional)
5. Basic analytics (view count)

### Non-Functional Requirements
1. **High availability**: 99.9% uptime
2. **Low latency**: < 100ms for read operations
3. **Scalability**: Handle millions of pastes per day
4. **Durability**: Pastes must not be lost before expiry
5. **Security**: Private pastes accessible only via URL (no listing/search)

---

## Capacity Estimation

### Traffic Estimates
- **Writes**: 1M new pastes/day = ~12 pastes/sec
- **Reads**: 10:1 read-to-write ratio = 120 reads/sec
- **Peak traffic**: 5× average = 60 writes/sec, 600 reads/sec

### Storage Estimates
- **Average paste size**: 10 KB (text)
- **Daily storage**: 1M × 10 KB = 10 GB/day
- **5-year storage** (assuming 80% pastes expire):
  - Total generated: 1M × 365 × 5 = 1.825 billion pastes
  - Retained (20%): 365 million pastes
  - Storage needed: 365M × 10 KB = **3.65 TB**

### Bandwidth Estimates
- **Write bandwidth**: 12 pastes/sec × 10 KB = 120 KB/sec
- **Read bandwidth**: 120 reads/sec × 10 KB = 1.2 MB/sec

### URL Key Size
- **Unique URLs needed**: ~2 billion (with buffer)
- **Character set**: [a-z, A-Z, 0-9] = 62 characters
- **Key length**: 62^7 = 3.5 trillion unique URLs (7 characters is sufficient for public pastes)
- **Private pastes**: 62^16 = astronomical (16 characters for unguessability)

---

## API Design

### 1. Create Paste
```http
POST /api/v1/pastes
Content-Type: application/json

{
  "content": "Hello, World!",
  "expiration": "1d",     // 1h, 1d, 1m, never
  "customUrl": "my-paste", // optional
  "isPrivate": false
}

Response: 201 Created
{
  "shortUrl": "https://paste.in/aB3dE5f",
  "expiresAt": "2024-02-10T12:00:00Z"
}
```

### 2. Retrieve Paste
```http
GET /api/v1/pastes/{shortKey}

Response: 200 OK
{
  "content": "Hello, World!",
  "createdAt": "2024-02-09T12:00:00Z",
  "expiresAt": "2024-02-10T12:00:00Z",
  "viewCount": 42
}

Response (expired): 404 Not Found
{
  "error": "paste_expired",
  "message": "This paste has expired"
}
```

### 3. Delete Paste (Owner Only)
```http
DELETE /api/v1/pastes/{shortKey}
Authorization: Bearer <token>

Response: 204 No Content
```

---

## High-Level Design

### Architecture

```
┌─────────┐
│  Client │
└────┬────┘
     │ HTTPS
     ▼
┌──────────────────┐
│  CDN (CloudFront)│  (Caches popular paste content at edge)
└─────────┬────────┘
          │ Cache miss
          ▼
┌──────────────────┐
│  Load Balancer   │
└─────────┬────────┘
          │
     ┌────┴────────┐
     ▼             ▼
┌─────────┐   ┌─────────┐
│  API    │   │  API    │  (Stateless, horizontally scaled)
│ Server  │   │ Server  │
└────┬────┘   └────┬────┘
     │             │
     ├─────────────┤
     │             │
     ▼             ▼
┌──────────────────────┐
│   Redis Cache        │  (Hot pastes, LRU eviction)
└──────────┬───────────┘
           │ Cache miss
           ▼
     ┌─────┴──────────┐
     ▼                ▼
┌──────────┐   ┌─────────────┐
│PostgreSQL│   │  S3 / Blob  │
│(Metadata)│   │  Storage    │
│short_key │   │  (Content)  │
│expires_at│   │             │
└──────────┘   └─────────────┘

┌──────────────────────────┐
│   Key Generation Service │  (Pre-generated key pool)
└──────────────────────────┘

┌──────────────────────────┐
│   Cleanup Service        │  (Cron: deletes expired pastes)
└──────────────────────────┘
```

**Why split content and metadata?**
- S3 costs ~$0.023/GB vs ~$0.10+/GB for database storage — an order of magnitude cheaper for blobs
- DB row size bloat degrades index performance (scanning metadata to find expired pastes becomes slow if each row is 10KB)
- S3 integrates directly with CloudFront CDN for content delivery
- DB is optimized for metadata operations (filtering, sorting by expiry, counting)

---

## Detailed Component Design

### 1. Key Generation Service

#### Option A: Pre-generate Keys (Recommended)

```
Key Generator runs offline:
  → Generate batch of 1M unique 7-char Base62 keys
  → Store in key_pool table with used=false

API Server on paste creation:
  → SELECT short_key FROM key_pool WHERE used=false LIMIT 1 FOR UPDATE
  → Mark as used=true
  → Use key for new paste
```

**Pros:** Fast (no hashing), zero collision risk, operation is atomic
**Cons:** Requires extra storage for key pool (~7 MB for 1M keys); key generation service is a dependency

**Optimization:** Each API server pre-fetches 1,000 keys into local memory on startup. This eliminates the key pool DB from the hot path entirely.

#### Option B: Hash-based Generation

```
MD5(content + timestamp + random_salt) → Base62 encode → Take first 7 chars
```

**Handle collision:** Query DB. If collision, append salt and retry (rare in practice but must be handled).

**Pros:** No central key service
**Cons:** Collision handling adds latency; same content at same time could generate same key

### 2. Database Schema

#### Metadata Table (PostgreSQL)
```sql
CREATE TABLE pastes (
    id BIGSERIAL PRIMARY KEY,
    short_key VARCHAR(10) UNIQUE NOT NULL,
    s3_object_key VARCHAR(255) NOT NULL,  -- Pointer to content in S3
    created_at TIMESTAMP DEFAULT NOW(),
    expires_at TIMESTAMP,                 -- NULL = never expires
    view_count BIGINT DEFAULT 0,
    is_private BOOLEAN DEFAULT false,
    owner_id BIGINT,
    INDEX idx_short_key (short_key),
    INDEX idx_expires_at (expires_at)     -- For cleanup job
);

-- Key pool for pre-generated keys
CREATE TABLE key_pool (
    short_key VARCHAR(10) PRIMARY KEY,
    used BOOLEAN DEFAULT false,
    created_at TIMESTAMP DEFAULT NOW(),
    INDEX idx_used (used)
);
```

### 3. Caching Strategy

```java
public String getPasteContent(String shortKey, Timestamp expiresAt) {
    String cacheKey = "paste:" + shortKey;

    // Check expiry first (avoid returning stale cached content after expiry)
    if (expiresAt != null && expiresAt.before(new Timestamp(System.currentTimeMillis()))) {
        throw new NotFoundException("Paste expired");
    }

    // Cache TTL = min(remaining TTL, 1 hour max)
    // Don't cache "never expires" pastes longer than 1 hour (LRU will handle eviction)
    long ttl = expiresAt == null ? 3600 :
        Math.min(
            Duration.between(Instant.now(), expiresAt.toInstant()).toSeconds(),
            3600
        );

    if (cache.exists(cacheKey)) {
        return cache.get(cacheKey);
    } else {
        String content = s3Client.getObject(s3Key);
        cache.setex(cacheKey, ttl, content);
        return content;
    }
}
```

**Cache Eviction:** LRU (Least Recently Used)

**SDE-3 Optimization: Consistent Hashing for Redis Cluster**

As traffic grows, a single Redis instance isn't enough. When adding nodes to a Redis cluster:
- Naive approach: `hash(key) % N`. Adding a node changes N, remapping almost all keys → massive cache miss spike (thundering herd hits the DB and S3).
- **Consistent hashing**: Only `1/N` of keys are remapped when a node is added/removed. Add virtual nodes to ensure even load distribution across nodes of different capacities.

### 4. Expiration & Cleanup

#### Lazy Deletion (On Every Read)
```java
// Check expiry before returning content
if (paste.getExpiresAt() != null &&
    paste.getExpiresAt().before(new Timestamp(System.currentTimeMillis()))) {
    throw new NotFoundException("Paste expired");
}
```
Simple but leaves expired rows in DB consuming space.

#### Active Cleanup (Background Job)
```java
// Cron job runs hourly
String query = """
    SELECT short_key, s3_object_key
    FROM pastes
    WHERE expires_at < NOW()
    LIMIT 10000
    """;

List<Paste> expiredPastes = db.executeQuery(query);

for (Paste paste : expiredPastes) {
    s3Client.deleteObject(paste.getS3ObjectKey());
    db.delete(paste.getId());
}
```

**SDE-3 Optimization: Avoid DB Scans for Cleanup**

Running `WHERE expires_at < NOW()` on a large table is slow even with an index, and the cleanup job competes with read traffic.

Better approaches:
- **S3 Lifecycle Policies**: Since content is in S3, configure lifecycle rules to delete objects after N days. Objects tagged `expires:1d` go into the `1d/` prefix; S3 auto-deletes. Avoids cleanup worker entirely for content.
- **DynamoDB TTL**: If you use DynamoDB for metadata, the TTL attribute handles deletion natively at no extra cost — no cleanup worker needed.
- **Postgres Table Partitioning**: Partition `pastes` by day. Instead of `DELETE FROM pastes WHERE expires_at < X` (slow row-level deletes), just `DROP TABLE pastes_2024_01` — instant and doesn't hold locks.

---

## Read/Write Flow

### Write Flow
```
1. Client → POST /pastes {content, expiration}
2. Load Balancer → API Server
3. API Server:
   a. Validate content (size < 1MB, no prohibited content)
   b. Fetch unique key from local pre-fetched key pool
   c. Generate S3 object key: "pastes/{short_key}"
   d. Upload content to S3 (atomic — if S3 fails, don't write metadata)
   e. INSERT into PostgreSQL: (short_key, s3_object_key, expires_at, ...)
   f. Return {shortUrl: "https://paste.in/{short_key}"}
```

**Idempotency note**: If the S3 upload succeeds but the DB write fails, the S3 object becomes an orphan. Periodic orphan cleanup: compare S3 keys against DB; delete any not in DB older than 1 hour.

### Read Flow
```
1. Client → GET /pastes/{short_key}
2. CDN: Check if cached → If hit, return directly (0 origin load)
3. Load Balancer → API Server
4. API Server:
   a. Check Redis cache for paste:{short_key}
   b. If HIT: Check expiry, return content
   c. If MISS:
      - Query PostgreSQL for metadata (s3_object_key, expires_at)
      - If expired → return 404
      - Fetch content from S3
      - Cache in Redis with appropriate TTL
      - Return content
5. (Async) Increment view_count via background job
```

---

## Scalability & Optimization

### 1. Database Sharding
- **Shard key**: `hash(short_key) % num_shards`
- Distributes load evenly across shards
- Challenge: Cross-shard queries (e.g., "all pastes by user") require scatter-gather; keep user lookups on a separate index

### 2. Read Replicas
- Primary handles writes (low QPS — 12 pastes/sec)
- Read replicas handle GET requests
- View count can be eventually consistent (batch-update from Redis periodically)

### 3. CDN for Popular Pastes
- Serve popular pastes from edge PoPs worldwide
- `Cache-Control: max-age=3600` for public pastes
- For expiring pastes: set `Cache-Control: max-age` to remaining TTL

### 4. Rate Limiting
- Prevent abuse: 10 pastes/hour per IP (free), 100/hour per authenticated user
- Use Redis counters (sliding window)

---

## Security Considerations

### 1. Private Pastes
- Generate cryptographically strong 16-character keys (using `SecureRandom`)
- No indexing or listing endpoints for private pastes
- No analytics that could leak existence of private pastes
- Consider password-protected pastes: store `bcrypt(password)` hash, require on read

### 2. Input Validation
- Limit paste size: Max 1 MB (reject at API layer before S3 upload)
- Strip or encode HTML to prevent XSS when rendering
- Content moderation for public pastes (CSAM detection via hash matching)

### 3. DDoS Protection
- CloudFlare or AWS Shield at edge
- Rate limiting at load balancer level
- IP-based blocking for repeat offenders

---

## Trade-offs

| Aspect | Choice | Alternative | Trade-off |
|--------|--------|-------------|-----------|
| **Content Storage** | S3 | Database (BLOB) | Cost + CDN integration vs. simpler single-store architecture |
| **Key Generation** | Pre-generated pool | Hash-based | No runtime collision risk vs. no key service dependency |
| **Expiration cleanup** | S3 Lifecycle + Lazy delete | Active DB cron | Zero DB load vs. storage accumulation without cleanup |
| **Caching** | Redis LRU | No cache | 95% hit rate reduces S3 egress cost vs. operational overhead |
| **Metadata DB** | PostgreSQL | DynamoDB | SQL flexibility vs. native TTL support |

---

## Extensions

### 1. Syntax Highlighting
- Detect language from content (or user hint), store in metadata
- Client-side rendering with libraries (Prism.js, highlight.js)
- No backend cost

### 2. Paste History (User Accounts)
```sql
ALTER TABLE pastes ADD COLUMN owner_id BIGINT;
CREATE INDEX idx_owner_id ON pastes(owner_id);
```

### 3. Analytics Dashboard
- Track views over time (TimescaleDB or InfluxDB)
- Geographic distribution (from CDN access logs)

---

## Interview Discussion Points

**Q: Why not store everything in the database?**
- S3 is 4× cheaper for blob storage ($0.023/GB vs $0.10+/GB for DB)
- DB performance degrades with large rows — index scans become slower as row size grows
- S3 integrates natively with CloudFront CDN
- DB should store only structured, queryable data; blobs are neither

**Q: How to handle 1M pastes/sec?**
- Horizontal scaling of stateless API servers
- Pre-fetched key pools eliminate key generation bottleneck
- Database sharding by `hash(short_key)`
- Aggressive caching (Redis cluster with consistent hashing)
- CDN absorbs read traffic at edge

**Q: What if the Key Generation Service goes down?**
- API servers have 1,000 keys pre-fetched in local memory — survives minutes of KGS downtime
- Fallback: Hash-based generation with collision check
- Multiple KGS instances (active-passive standby)

**Q: How do you handle the "HN effect" — a paste suddenly getting 50,000 hits?**
- First request warms CDN and Redis cache
- Subsequent requests served from CDN edge — zero origin load
- If CDN miss (first hit per PoP), Redis absorbs the load
- S3 handles concurrent reads well (it's infinitely scalable)

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
