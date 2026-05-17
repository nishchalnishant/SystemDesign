# Design Google Drive

> **Difficulty**: Hard
> **Topics**: Chunked Uploads, Delta Sync, Conflict Resolution, Distributed File Storage, Version Control
> **Time**: 60-75 minutes
> **Companies**: Google, Dropbox, Box, Microsoft (OneDrive)

---

## Problem Statement

Design a cloud storage and file synchronization service like Google Drive that allows users to upload, store, share files and folders, with real-time synchronization across multiple devices.

---

## Analogy

A filing cabinet that syncs across all your devices. When you update a file on your laptop, your phone sees the update within seconds.

Simple for one person and one device. Now imagine: you and a colleague are both editing `proposal.docx` simultaneously — from different cities. Your laptop has version 3, their desktop has version 3, you both save, and now there are two "version 4s" in conflict. Which one wins? Do you lose work? What if you're on a plane (offline) and make changes that need to merge with changes your colleague made while you were mid-flight?

And scale this to 1 billion users, exabytes of data, uploading 2 petabytes per day. A single user uploading a 15GB video on a slow connection cannot tie up a connection for hours — you need to split it into chunks, upload them in parallel, and resume where you left off if the connection drops.

---

## Why This Is Hard

1. **Chunked uploads and deduplication**: Large files must be split into chunks and reassembled. The same chunk appearing in two different files (e.g., a shared template) should be stored only once. This requires content-addressed storage (SHA-256 per chunk), which has privacy implications — you can detect if someone uploaded a file you also have, without seeing the content.
2. **Conflict resolution**: When two devices edit the same file without syncing, you have a fork. Last-write-wins loses data. Operational Transformation (Google Docs) requires a central authority. For non-collaborative files, creating a "conflict copy" is the pragmatic choice — but now you need to surface it to the user.
3. **Real-time sync without polling**: A file updated on Device A must appear on Device B within seconds. HTTP polling wastes bandwidth. WebSockets require persistent connections from all devices. Choosing the right push mechanism at scale (1B users, many devices each) is non-trivial.
4. **Metadata at exabyte scale**: 10 PB of metadata (filenames, folder structures, permissions) must be queryable with sub-100ms latency. Sharding by user_id keeps user data co-located but creates hotspots for highly active users. Cross-user queries (shared folders, search) require fanout across shards.
5. **Storage tiering and cost**: Storing all files in hot storage (S3 Standard) costs too much. Files not accessed for 90+ days should tier automatically to cold storage (Glacier). But users expect instant access even to cold files — so you need transparent tiering with acceptable restore latency.

---

## Requirements

### Functional Requirements
1. **Upload/Download files** (up to 15 GB per file)
2. **Create folders** and organize files
3. **Share files/folders** with permissions (view, edit)
4. **Real-time sync** across devices
5. **Version history** (restore previous versions)
6. **Collaborative editing** (Google Docs-style)
7. **Search** files by name, content, type
8. **Trash** with 30-day retention

### Non-Functional Requirements
1. **High availability**: 99.9% uptime
2. **Strong consistency**: Same view across devices (for metadata)
3. **Scalability**: 1 billion users, exabytes of data
4. **Reliability**: No data loss (11 nines durability via S3)
5. **Low latency**: < 100ms for metadata operations
6. **Bandwidth efficiency**: Delta sync, compression, deduplication

---

## Capacity Estimation

### User & Storage Estimates
- **Total users**: 1 billion
- **Free tier**: 15 GB/user
- **Paid tier** (10%): 100 GB/user average
- **Total storage**:
  - Free: 900M × 15 GB = 13.5 EB
  - Paid: 100M × 100 GB = 10 EB
  - **Total**: ~23.5 exabytes

### Traffic Estimates
- **DAU**: 200 million
- **Files uploaded per user/day**: 5 files
- **Average file size**: 2 MB
- **Daily uploads**: 200M × 5 × 2 MB = **2 PB/day**
- **Upload QPS (peak)**: 200M × 5 / 86,400 × 3 = **~35K uploads/sec**

### Metadata Estimates
- **Files per user**: 10,000 files
- **Metadata size**: 1 KB per file
- **Total metadata**: 1B users × 10K files × 1 KB = **10 PB**

---

## High-Level Architecture

```
┌──────────────────────────────────────────────────────────┐
│                        Clients                           │
│  Desktop App    Mobile App    Web Browser                │
└──────────────────────────┬───────────────────────────────┘
                           │ HTTPS / WSS
                           ▼
                    ┌─────────────┐
                    │ Load Balancer│
                    └──────┬──────┘
                           │
                    ┌──────▼──────┐
                    │ API Gateway │ (Auth, Rate Limiting, TLS)
                    └──────┬──────┘
                           │
         ┌─────────────────┼─────────────────┐
         ▼                 ▼                 ▼
  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐
  │ File Service│  │ Sync Service│  │Share Service│
  └──────┬──────┘  └──────┬──────┘  └─────────────┘
         │                │ WebSocket
         │                ▼
         │         ┌─────────────┐   ┌─────────────┐
         │         │ WebSocket   │◀──│    Kafka    │
         │         │ Server      │   │  (Pub/Sub)  │
         │         └─────────────┘   └─────────────┘
         │
         ├──────────────────┐
         ▼                  ▼
  ┌─────────────┐    ┌─────────────┐
  │Chunk Service│    │  PostgreSQL  │ (Metadata: files, folders, perms)
  │(Dedup)      │    │  + Redis    │ (Hot metadata cache)
  └──────┬──────┘    └─────────────┘
         │
         ▼
  ┌─────────────┐
  │  S3 / Blob  │ (File chunks — 23 EB)
  │   Storage   │
  └─────────────┘

Background Workers:
  VersionWorker | ThumbnailGenerator | VirusScanner | SearchIndexer
```

---

## Core Components

### 1. File Upload Flow with Chunking

```
Client-side (before upload):
1. Split file into 4 MB chunks.
2. Calculate SHA-256 hash for each chunk.
3. Send chunk hashes to File Service (check deduplication).

For each chunk:
  GET /chunks/exists?hash={sha256}
  → If exists: Skip upload (server already has it — deduplication hit!)
  → If missing: POST /chunks/{hash} with chunk data → stored to S3

Final commit:
POST /files {name, parent_folder_id, chunk_list: [{hash, index}]}
→ File Service inserts file metadata + triggers sync event
```

**Chunking Benefits:**
- Resume uploads: Re-upload only failed chunks
- Bandwidth efficiency: Skip unchanged chunks on re-upload
- Deduplication: Same chunk stored once regardless of how many files reference it
- Parallelization: Upload all chunks simultaneously (up to 10 parallel connections)

**Chunk Schema (PostgreSQL):**
```sql
CREATE TABLE chunks (
    chunk_hash BYTEA PRIMARY KEY,  -- SHA-256 (32 bytes)
    s3_key     VARCHAR(500) NOT NULL,
    size_bytes BIGINT,
    ref_count  INT DEFAULT 1  -- Incremented when another file references this chunk
);

CREATE TABLE file_chunks (
    file_id      UUID,
    chunk_index  INT,
    chunk_hash   BYTEA REFERENCES chunks(chunk_hash),
    PRIMARY KEY (file_id, chunk_index)
);
```

**Deduplication privacy concern**: Content-addressed storage leaks whether two users have identical files. Mitigations: disable cross-user deduplication (store per-user copies), or use convergent encryption (deterministic encryption per chunk so deduplication still works but content is opaque).

---

### 2. Metadata Database Schema

```sql
CREATE TABLE users (
    user_id          BIGINT PRIMARY KEY,
    email            VARCHAR(255) UNIQUE,
    storage_quota_gb INT DEFAULT 15,
    storage_used_bytes BIGINT DEFAULT 0
);

CREATE TABLE files (
    file_id          UUID PRIMARY KEY,
    owner_id         BIGINT REFERENCES users(user_id),
    parent_folder_id UUID REFERENCES files(file_id),
    name             VARCHAR(255) NOT NULL,
    file_type        VARCHAR(50),
    size_bytes       BIGINT,
    is_directory     BOOLEAN DEFAULT FALSE,
    created_at       TIMESTAMP DEFAULT NOW(),
    modified_at      TIMESTAMP DEFAULT NOW(),
    is_deleted       BOOLEAN DEFAULT FALSE,
    version          INT DEFAULT 1,
    INDEX idx_parent_owner (parent_folder_id, owner_id),
    INDEX idx_modified (modified_at)
);

CREATE TABLE file_versions (
    version_id     UUID PRIMARY KEY,
    file_id        UUID REFERENCES files(file_id),
    version_number INT,
    chunk_list     JSONB,     -- [{chunk_hash, chunk_index}]
    size_bytes     BIGINT,
    created_at     TIMESTAMP DEFAULT NOW()
);

CREATE TABLE permissions (
    permission_id   UUID PRIMARY KEY,
    file_id         UUID REFERENCES files(file_id),
    user_id         BIGINT REFERENCES users(user_id),
    permission_type ENUM('view', 'edit', 'owner'),
    granted_by      BIGINT,
    granted_at      TIMESTAMP DEFAULT NOW(),
    UNIQUE(file_id, user_id)
);
```

---

### 3. Real-Time Sync Protocol

```
Sync flow (Device A edits file, Device B should see update):

Device A uploads changes
  → File Service updates metadata in PostgreSQL
  → File Service publishes event to Kafka:
    { user_id: 123, file_id: "abc", version: 5, event: "file_modified" }

Kafka → Sync Service (WebSocket server for this user's sessions)
  → Sync Service pushes to all connected devices for user 123:
    { type: "file_modified", fileId: "abc", version: 5 }

Device B receives event:
  → Compares local version (3) with remote version (5)
  → Fetches file metadata: GET /files/abc
  → Downloads only changed chunks (delta sync)
  → Reconstructs updated file
```

**WebSocket Events:**
```json
{
  "type": "file_modified",
  "fileId": "abc123",
  "version": 5,
  "timestamp": 1644444444,
  "modifiedBy": "user456"
}
```

**Conflict Resolution:**
```java
public void resolveConflict(int localVersion, int remoteVersion,
                             long localModified, long remoteModified) {
    if (localVersion < remoteVersion) {
        // Remote is newer, pull remote changes
        downloadFile();
    } else if (localVersion > remoteVersion) {
        // Local is newer (offline edits), push to server
        uploadFile();
    } else {
        // Same version number but both were modified (true conflict)
        if (localModified > remoteModified) {
            // Both survive: create a conflict copy
            createConflictCopy();  // Creates "proposal (Conflict Copy 2026-05-12).docx"
        }
    }
}
```

**When to create a conflict copy vs. auto-merge:**
- Plain text files (code): Auto-merge via 3-way diff if feasible.
- Binary files (Word, PDF): Create conflict copy — cannot auto-merge.
- Google Docs format: Use CRDT/OT for real-time collaborative resolution.

---

### 4. Delta Sync (Bandwidth Optimization)

**rsync-style algorithm:**
```java
public Delta computeDelta(File localFile, File remoteFile) {
    // Client sends rolling hash signatures for local chunks
    List<ChunkSignature> localSignatures = localFile.getChunks().stream()
        .map(chunk -> new ChunkSignature(chunk.getIndex(), chunk.getHash()))
        .collect(toList());

    // Server compares local signatures against remote version
    List<ChunkDelta> delta = new ArrayList<>();
    for (Chunk chunk : remoteFile.getChunks()) {
        if (!localSignatures.contains(chunk.getHash())) {
            // This chunk is new or changed — include in delta
            delta.add(new ChunkDelta(chunk.getIndex(), chunk.getData()));
        }
        // Matching chunks are skipped — client already has them
    }

    return new Delta(delta);  // Client downloads only the delta
}
```

**Impact**: Reduces bandwidth by 80%+ for small edits to large files (e.g., appending to a 1GB document).

---

### 5. File Sharing & Permissions

**Share Link Schema:**
```sql
CREATE TABLE share_links (
    link_id        UUID PRIMARY KEY,
    file_id        UUID REFERENCES files(file_id),
    created_by     BIGINT,
    token          VARCHAR(64) UNIQUE,  -- Cryptographically random token
    permission     ENUM('view', 'edit'),
    expires_at     TIMESTAMP,
    password_hash  VARCHAR(255),        -- Optional password protection
    created_at     TIMESTAMP DEFAULT NOW()
);
```

**Access Control (with folder permission inheritance):**
```java
public boolean checkAccess(long userId, String fileId, String action) {
    // Check direct permission on this file
    Permission perm = db.query(
        "SELECT permission_type FROM permissions WHERE user_id = ? AND file_id = ?",
        userId, fileId
    );

    if (perm != null && perm.can(action)) {
        return true;
    }

    // Inherit permission from parent folder (recursive)
    String parent = getParentFolder(fileId);
    if (parent != null) {
        return checkAccess(userId, parent, action);  // Walk up folder tree
    }

    return false;  // No access found
}
```

**Performance concern**: Recursive permission checking per request is expensive on deep folder trees. Mitigations: cache permissions in Redis (TTL 60s), materialize effective permissions in a denormalized table.

---

### 6. Version Control

**Version Retention Policy:**
- **Last 30 days**: Keep all versions
- **30-90 days**: Keep weekly snapshots
- **90+ days**: Keep monthly snapshots
- **Manually pinned versions**: Kept indefinitely

**Storage Optimization (Copy-on-Write):**
```
Version 1: [chunk_A, chunk_B, chunk_C]
User edits only middle section:
Version 2: [chunk_A, chunk_B_new, chunk_C]
            ↑ shared   ↑ new chunk   ↑ shared

Storage cost of V2 = only chunk_B_new (not full file copy)
```

**Garbage Collection:**
```sql
-- Find chunks with ref_count = 0 (no file version references them)
-- Run nightly; delete from S3 and chunks table
DELETE FROM chunks WHERE ref_count = 0 AND created_at < NOW() - INTERVAL '7 days';
```

---

## Advanced Features

### 1. Collaborative Editing (Google Docs)

Real-time collaborative editing requires a different model than file sync — the granularity is individual keystrokes, not file versions.

**Operational Transformation (OT):**
```
User 1: insert "Hello" at position 0
User 2: insert "World" at position 0 (concurrent)

Without OT: Both operations apply at position 0 → "WorldHello" (wrong order)
With OT:    User 2's operation is transformed: insert "World" at position 5
            → Result: "Hello World" (correct)
```

**CRDT (Conflict-free Replicated Data Types):**
- Yjs library: Each character has a globally unique ID. Merge is deterministic regardless of operation order.
- No central authority needed — peers can merge offline edits.
- Used by Notion, Figma, Liveblocks.

**For Google Drive file sync** (not Google Docs): Use version numbers + conflict copies. No need for OT/CRDT at the file level.

### 2. Full-Text Search (Elasticsearch)

**Index Schema:**
```json
{
  "mappings": {
    "properties": {
      "file_id":    {"type": "keyword"},
      "name":       {"type": "text"},
      "content":    {"type": "text"},    // Extracted from PDF, DOCX
      "file_type":  {"type": "keyword"},
      "owner_id":   {"type": "keyword"},
      "shared_with": {"type": "keyword"}, // For permission-aware search
      "modified_at": {"type": "date"}
    }
  }
}
```

**Content Extraction Pipeline (async):**
```
File uploaded → Kafka event → Content Extractor worker:
- PDF: Apache Tika
- DOCX/XLSX: Apache POI
- Images: Tesseract OCR
- ZIP: Recursively extract and index contents

Extracted text → Elasticsearch index
```

### 3. Trash & Recovery

```sql
CREATE TABLE trash (
    trash_id         UUID PRIMARY KEY,
    file_id          UUID,
    original_parent_id UUID,
    deleted_by       BIGINT,
    deleted_at       TIMESTAMP DEFAULT NOW(),
    auto_delete_at   TIMESTAMP DEFAULT (NOW() + INTERVAL '30 days')
);

-- Cron job: Permanently delete files after 30 days
-- 1. Delete chunks with ref_count = 0 from S3
-- 2. Remove file_versions, file_chunks, permissions
-- 3. Remove from trash table
DELETE FROM trash WHERE auto_delete_at < NOW();
```

---

## Scalability Strategies

### 1. Database Sharding

**Metadata sharding by user_id:**
```
Shard 0: user_id % 10 = 0  (contains all files owned by these users)
Shard 1: user_id % 10 = 1
...

Benefit: User's entire folder tree co-located on one shard → fast folder listing
Challenge: Shared files (cross-user permission lookups) require fanout to multiple shards
Solution: Denormalize shared file metadata into recipient's shard on share event
```

### 2. CDN for Downloads

```
Popular files cached at edge CDN (CloudFront / Fastly):
- Files accessed > N times in 24 hours → promoted to CDN cache
- Signed URLs: Time-limited S3 URLs (expire in 15 min)
  - Client requests download URL from File Service
  - File Service generates signed URL: s3.generatePresignedUrl(s3_key, ttl=900s)
  - Client downloads directly from S3/CDN (bypasses application servers)
```

### 3. S3 Storage Tiers

```
Hot files (accessed < 30 days old):   S3 Standard          ($0.023/GB/month)
Warm files (30-90 days):              S3 Intelligent-Tiering (auto-tiers)
Cold files (90+ days, infrequent):    S3 Glacier Instant    ($0.004/GB/month)
Archive (>1 year, very infrequent):   S3 Glacier Deep       ($0.00099/GB/month)

Transparent to user: File Service handles tier resolution and restore requests.
Restore from Glacier: 1-5 minutes (Instant Retrieval) vs 3-5 hours (Flexible).
```

---

## Failure Scenarios

### Upload Interrupted Mid-Chunk
```
Scenario: Client uploads chunks 0-5 of a 20-chunk file, then connection drops.

Recovery:
- Client stores uploaded chunk hashes in local DB.
- On resume: GET /files/{upload_id}/status → server returns uploaded chunks.
- Client resumes from chunk 6.

Server-side: Multipart upload ID tracks partial upload state.
Cleanup: Abort incomplete multipart uploads after 7 days (S3 lifecycle policy).
```

### Sync Conflict at Scale
```
Scenario: User edits file on mobile (offline), desktop (offline), then both reconnect.

Effect: Two versions of the file, both claiming to be v3 from v2.

Resolution:
- Whichever sync arrives first claims v3.
- Second sync detects version conflict (expected v2, found v3).
- Creates "filename (Conflict Copy 2026-05-12).docx" for the second version.
- Both versions preserved. User notified in UI.
```

### Metadata DB Failure
```
Impact: Cannot list files, create folders, or start new uploads.
Recovery:
- PostgreSQL synchronous standby with automatic failover (Patroni).
- RTO < 2 min, RPO = 0.
- During failover: Read-only mode (downloads from S3 still work via cached signed URLs).
```

### S3 Region Outage
```
Impact: New uploads fail; downloads of non-cached files fail.
Recovery:
- Multi-region S3 replication (Cross-Region Replication).
- Route53 health checks fail over to replica region.
- Trade-off: replication lag ~seconds; very recent uploads may not be in replica.
```

---

## Trade-offs

| Aspect | Choice | Trade-off |
|--------|--------|-----------|
| **Chunking** | 4 MB chunks | Smaller = more deduplication; larger = less overhead per chunk |
| **Consistency** | Strong (PostgreSQL metadata) | Higher latency vs. eventual (but users expect files to "just be there") |
| **Sync** | WebSocket push | Persistent connection overhead vs. polling latency |
| **Deduplication** | Hash-based (SHA-256) | Storage savings vs. potential content inference across users |
| **Conflict resolution** | Conflict copy for binary, merge for text | Safest for binary; loses last-write-wins simplicity |

---

## Interview Talking Points

**Q: How to handle large files (100 GB+)?**
- A: "Multipart upload: split into 4MB chunks, upload in parallel (up to 10 concurrent chunks), commit when all are received. Resume via stored chunk manifest on client side — re-upload only failed chunks. Bandwidth throttling via client-side rate limiting prevents one upload from saturating the connection for other operations."

**Q: Preventing data loss?**
- A: "Three layers: (1) S3 11-nines durability via cross-AZ storage; (2) versioning — every file edit creates a new version (no destructive updates to chunk storage); (3) cross-region replication for disaster recovery. The immutable chunk store (content-addressed) means a deleted file is just a metadata delete — chunks persist until garbage collection removes unreferenced ones."

**Q: Optimizing for mobile devices?**
- A: "Selective sync: user chooses which folders to sync locally vs. cloud-only. Photo backup: queue uploads for WiFi-only, compress before upload. Thumbnail generation: serve 200KB thumbnail from CDN instead of 5MB original. Delta sync: on re-upload, send only changed chunks. Offline mode: cache recently accessed files locally with SQLite metadata store."

**Q: How do you handle the 10 PB metadata problem?**
- A: "Shard PostgreSQL by user_id. Each user's folder tree lives on one shard — all folder listing queries are single-shard. For shared files (cross-user), denormalize: when User A shares a file with User B, write a lightweight metadata record to User B's shard pointing at the canonical file_id. Permissions check still requires looking up User A's shard, but folder listing for User B is fast."

---

## Interview Questions Asked

### Google
1. **"Design Google Drive"** → Probe: file chunking, deduplication, sync protocol, metadata scalability. Hint: content-defined chunking (Rabin fingerprint) finds natural break points so edits don't shift all chunk boundaries; SHA-256 hash per chunk enables cross-user deduplication without revealing content.

### Dropbox
1. **"Design the Dropbox sync client"** → Probe: how client detects changes and minimizes bandwidth. Hint: client watches filesystem events (inotify/FSEvents), computes chunked hashes locally, uploads only delta chunks that changed; rsync-style rolling checksum for diff detection.

### Common Follow-ups
1. **"How does content-defined chunking differ from fixed-size chunking?"** → Fixed chunks shift on insert/delete causing all downstream chunks to look "changed"; content-defined (Rabin) finds split points based on content so only the edited region produces new chunks.
2. **"Two users upload the identical 2GB file — do you store it twice?"** → No: SHA-256 hash of each chunk is the storage key; second upload finds all chunks already present and just creates a new metadata record pointing at existing chunk IDs (deduplication at chunk granularity).
3. **"User edits a doc offline on laptop and phone simultaneously — how do you resolve the conflict?"** → On sync, detect version vector divergence; for binary files create a conflict copy (safe, no data loss); for text files attempt 3-way merge using common ancestor version; surface conflict to user if merge fails.
4. **"How do you do bandwidth-efficient delta sync?"** → Client stores chunk hashes of last-synced version; on next sync, diff local hashes vs server manifest; upload only chunks whose hash changed — typically <5% of file for small edits.
5. **"How do you handle a file larger than 100GB?"** → Multipart upload: 4MB chunks uploaded in parallel (up to 10 concurrent); server commits only after receiving manifest confirming all chunk ETags; client stores upload-session ID so interrupted uploads resume from last successful chunk.
