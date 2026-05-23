---
module: 05-hld-problems
topic: Hard
status: unread
tags: [05-hld-problems, system-design, hard]
---
# System Design: Dropbox File Sync

> **Design a file synchronization service that keeps files consistent across multiple devices, handles offline changes, and resolves conflicts when the same file is edited on two devices simultaneously.**

---

## Problem Scope

**Functional requirements**:
- Upload and download files
- Sync changes across all user devices automatically
- Handle offline edits — sync when device reconnects
- Detect and resolve conflicts (same file edited on two devices)
- Share files/folders with other users

**Non-functional requirements**:
- 100M users, 50M DAU
- Average 2GB storage per user → 200PB total
- Average file size: 500KB (lots of documents, some photos)
- Sync latency: changes visible on other devices within 10 seconds
- Availability: 99.99% (< 1h/year downtime)

---

## Capacity Estimation

```
Users:          100M registered, 50M DAU
Devices/user:   2.5 average → 125M devices
Storage:        100M × 2GB avg = 200PB total
Daily uploads:  50M users × 1 file/day avg = 50M files/day
                = 578 files/second (average)
                = ~5K files/second (peak, 8.6× factor)

File size avg:  500KB
Daily upload:   50M × 500KB = 25TB/day
Bandwidth in:   25TB / 86400s = 290MB/s sustained, ~2.5GB/s peak
Bandwidth out:  3× reads vs writes = 870MB/s sustained

Block size:     4MB (Dropbox uses 4MB blocks for chunking)
Blocks/day:     50M × 500KB / 4MB ≈ 6.25M blocks/day
                (many small files fit in 1 block; large files span multiple)

Metadata ops:   For every file change: update file metadata, list of blocks
                ~50M metadata writes/day = 578/s sustained
```

---

## High-Level Architecture

```
                         ┌─────────────────────────────────────┐
                         │           Clients (Devices)          │
                         │  Desktop  │  Mobile  │  Web Browser  │
                         └─────┬─────┴────┬─────┴───────┬───────┘
                               │          │             │
                    ┌──────────▼──────────▼─────────────▼──────────┐
                    │                  API Gateway                   │
                    │         (auth, rate limiting, routing)         │
                    └──────┬──────────────┬───────────────┬──────────┘
                           │              │               │
               ┌───────────▼──┐  ┌────────▼────┐  ┌──────▼──────────┐
               │  Sync Service│  │ Upload Svc  │  │  Metadata Svc   │
               │  (WebSocket/ │  │ (presigned  │  │  (file/folder   │
               │   SSE notify)│  │  S3 URLs)   │  │   tree, blocks) │
               └───────────┬──┘  └─────────────┘  └──────┬──────────┘
                           │                              │
               ┌───────────▼──────────────────────────────▼──────────┐
               │              Message Queue (Kafka)                    │
               │     file_changed events, sync notifications           │
               └───────────────────────────┬──────────────────────────┘
                                           │
               ┌───────────────────────────▼──────────────────────────┐
               │                    Storage Layer                       │
               │   Block Store (S3)      │    Metadata DB (PostgreSQL) │
               │   4MB content-addressed │    file_id, version, blocks  │
               │   blocks by SHA-256     │    Redis: sync state cache   │
               └──────────────────────────────────────────────────────┘
```

---

## Core Design: Client-Side Chunking

### Why Chunk Files?

- **Resume interrupted uploads**: if connection drops at 95%, resume from last chunk
- **Deduplication**: identical blocks across files or versions share storage (content-addressed)
- **Delta sync**: only upload changed chunks when a file is edited

### Chunking Algorithm

```
File: "presentation.pptx" (40MB)

Split into fixed-size 4MB blocks:
  Block 0: bytes 0       - 4,194,303   SHA-256: "a3f1..."
  Block 1: bytes 4194304 - 8,388,607   SHA-256: "c7b2..."
  Block 2: bytes 8388608 - 12,582,911  SHA-256: "e9d4..."
  ...
  Block 9: bytes 37748736 - 40MB       SHA-256: "ff23..."  (partial, last block)

File metadata: [a3f1, c7b2, e9d4, ..., ff23]  (ordered list of block hashes)
```

**Content-addressable storage**: block is stored at path `s3://blocks/{sha256_hash}`.  
If the same 4MB sequence appears in two different files, it's stored once.

### Delta Sync (Edit Detection)

```
Version 1 of file:  blocks = [a3f1, c7b2, e9d4, 88ab]
User edits middle:
Version 2 of file:  blocks = [a3f1, c7b2, XXXX, 88ab]
                                            ^^^^
                                       only block 2 changed

Upload:  only block XXXX (4MB instead of 40MB)
Savings: 90% bandwidth reduction
```

**Algorithm on client**:
1. Re-chunk modified file
2. Compute SHA-256 for each new chunk
3. Diff against last-known block list
4. Upload only blocks not already in block store (check by hash)
5. Update metadata with new block list

### Variable-Size Chunking (Advanced)

Dropbox's real implementation uses **variable-size chunking** (content-defined chunking with Rabin fingerprinting). Fixed-size chunking fails for insertions: inserting 1 byte shifts all subsequent chunk boundaries, making all blocks different.

```
Rabin fingerprint: rolling hash over a sliding window
                   when hash matches a pattern: split here
Result: chunk boundaries are content-defined, not position-defined
        → insertion in the middle only affects local chunks
```

---

## Upload Flow

```
Client                     Upload Service          Block Store (S3)   Metadata DB
  │                              │                       │                │
  │── 1. compute block hashes ──▶│                       │                │
  │                              │─── 2. which blocks ──▶│                │
  │                              │◀─── already exist? ───│                │
  │◀── 3. upload these blocks ───│                       │                │
  │                              │                       │                │
  │─────── 4. PUT /blocks/{hash} ──────────────────────▶│                │
  │                              │                       │                │
  │── 5. commit: file_id, version, block_list ──────────────────────────▶│
  │                              │                       │                │
  │                              │──── 6. publish file_changed event ───▶ Kafka
  │                              │                       │                │
```

**Presigned URL approach**: Upload Service gives client a presigned S3 URL. Client uploads directly to S3, bypassing service. Upload Service only handles metadata — not bandwidth.

```java
// Generate presigned URL for block upload
String blockHash = "a3f1b2c3...";
String key = "blocks/" + blockHash;

// Check if block already exists (dedup check)
boolean exists = s3.headObject(HeadObjectRequest.builder()
    .bucket("dropbox-blocks")
    .key(key)
    .build()) != null;

if (!exists) {
    // Generate presigned PUT URL (15 minute expiry)
    PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(
        PutObjectPresignRequest.builder()
            .signatureDuration(Duration.ofMinutes(15))
            .putObjectRequest(PutObjectRequest.builder()
                .bucket("dropbox-blocks")
                .key(key)
                .build())
            .build());
    return presigned.url().toString();
}
return null; // block already exists, skip upload
```

---

## Sync Notification: Real-Time Change Propagation

When Device A modifies a file, Devices B, C, D must learn about it within 10 seconds.

### Long Polling vs WebSocket vs SSE

| Approach | Latency | Server Load | Complexity |
|----------|---------|-------------|------------|
| Polling (every 30s) | 0–30s | Low | Low |
| Long Polling | 0–30s | Medium | Medium |
| WebSocket | ~100ms | High (persistent conn) | High |
| SSE (Server-Sent Events) | ~100ms | Medium | Low |

**Dropbox uses long polling** for most clients (simpler, works through proxies). WebSocket for collaborative editing features.

```
Architecture:
  Sync Service maintains user → [device_connection_ids] map in Redis
  
  On file change (Kafka consumer):
    1. Look up which devices belong to user (Redis SET: user:{id}:devices)
    2. Send notification to each connected device (WebSocket/long-poll)
    3. Device receives: {file_id, new_version, changed_block_hashes}
    4. Device downloads only changed blocks from S3
```

---

## Metadata Database Schema

```sql
CREATE TABLE files (
    file_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL,
    parent_id   UUID REFERENCES folders(folder_id),
    name        TEXT NOT NULL,
    size_bytes  BIGINT,
    mime_type   TEXT,
    created_at  TIMESTAMP,
    is_deleted  BOOLEAN DEFAULT FALSE,
    UNIQUE (parent_id, name, user_id)  -- no duplicate names in a folder
);

CREATE TABLE file_versions (
    version_id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    file_id     UUID NOT NULL REFERENCES files(file_id),
    version_num INTEGER NOT NULL,
    block_hashes TEXT[] NOT NULL,  -- ordered array of SHA-256 hashes
    size_bytes  BIGINT,
    device_id   UUID,              -- which device created this version
    created_at  TIMESTAMP,
    UNIQUE (file_id, version_num)
);

CREATE TABLE blocks (
    block_hash  CHAR(64) PRIMARY KEY,  -- SHA-256 hex
    size_bytes  INTEGER,
    s3_key      TEXT,                  -- "blocks/{hash}"
    created_at  TIMESTAMP
);

CREATE TABLE file_shares (
    file_id     UUID NOT NULL REFERENCES files(file_id),
    shared_with UUID NOT NULL REFERENCES users(user_id),
    permission  TEXT CHECK (permission IN ('read', 'write')),
    PRIMARY KEY (file_id, shared_with)
);
```

---

## Offline Editing and Conflict Resolution

### The Problem

Device A edits `report.docx` while offline. Device B also edits `report.docx` while online. When Device A reconnects, both have a newer version than the server's last known state.

### Detection

Using version vectors (one counter per device):

```
Server version: {deviceA: 5, deviceB: 3}
Device A offline edits → local version: {deviceA: 6, deviceB: 3}
Device B online edit   → server version: {deviceA: 5, deviceB: 4}

When A reconnects and tries to commit {deviceA: 6, deviceB: 3}:
  Server current: {deviceA: 5, deviceB: 4}
  A's version does not dominate server version → CONFLICT
```

### Resolution Strategies

**Strategy 1: Last Write Wins** (simple, lossy)
- Compare timestamps; keep newer version
- Other version is discarded
- Used for: photos, binary files where merging is impossible

**Strategy 2: Both Copies** (Dropbox's actual behavior for binary files)
```
report.docx         ← server version (Device B's edit)
report (conflicted copy, Device A, 2024-01-15).docx  ← Device A's version
```
- User manually resolves
- No data loss
- Noisy: lots of conflict copies if users work offline frequently

**Strategy 3: Three-Way Merge** (text files)
```
Base version (common ancestor): "Hello world"
Device A edit: "Hello wonderful world"
Device B edit: "Hello world!"

Three-way merge:
  A changed: inserted "wonderful " before "world"
  B changed: appended "!"
  Result: "Hello wonderful world!"  (both changes applied, no conflict)

Conflict only if both edited the same region:
  A: "Hello wonderful world"  (changed "world" to "wonderful world")
  B: "Hello earth"            (changed "world" to "earth")
  → True conflict: must show both and ask user
```

**Implementation**: Git's merge algorithm, GNU diff3, or Google Docs OT.

---

## Deduplication and Storage Efficiency

```
Global dedup:  identical blocks across all users share one S3 object
               "node_modules.zip" uploaded by 1M developers → stored once

Per-user dedup: same block in different files for one user
                backup copy + original share blocks

Compression:   blocks compressed before storage (LZ4 for speed, zstd for ratio)
               text files: 70% reduction
               binary/already-compressed: minimal gain

Storage efficiency example:
  100M users × 2GB = 200PB raw
  With global dedup + compression: ~60-80PB actual S3 storage
```

---

## Failure Scenarios

| Scenario | Impact | Mitigation |
|----------|--------|------------|
| Upload interrupted mid-block | Partial block on S3, metadata not committed | Client retries idempotently; block upload is PUT to hash-based key |
| Metadata DB down | Uploads fail; no new syncs | Read from replica; retry with backoff |
| Block Store S3 outage | Downloads fail; uploads fail | Multi-region S3 replication; pre-fetch blocks to edge CDN |
| Notification service down | Devices don't learn about changes | Fallback to polling every 30s; missed events caught on reconnect |
| Large file upload (10GB) | Timeout on single upload | Multipart upload; S3 multipart supports 10,000 parts × 5MB = 50GB max |
| Sync storm (1M devices online at once) | Thundering herd on reconnect | Exponential backoff + jitter for reconnect; notification fanout via Kafka with consumer lag |

---

## Trade-off Decisions

| Decision | Choice | Alternative | Why |
|----------|--------|-------------|-----|
| Block size | 4MB fixed | Variable (Rabin) | Fixed simpler; 4MB balances overhead vs delta efficiency |
| Conflict resolution | Both copies | Merge | Safe for binary; merge only for text with diff3 |
| Notification | Long poll | WebSocket | Simpler, works through corporate proxies |
| Storage backend | S3 (object store) | Custom block store | S3: 11-nines durability, no ops burden |
| Dedup scope | Global (all users) | Per-user only | Global maximizes savings; requires hash-based addressing |

---

## Interview Q&A

**Q: How do you handle a 10GB file upload over a flaky connection?**

Client-side chunking into 4MB blocks. Each block is uploaded independently with retry. The commit (metadata update with final block list) only happens after all blocks succeed. If upload fails at block 7 of 10, client resumes from block 7. Block uploads are idempotent — re-uploading a block with the same hash to the same S3 key is a no-op.

**Q: How does delta sync work when a user edits a 1GB video file?**

Client re-chunks the entire file. For a video, sequential edits (e.g. added title card at start) would shift all fixed-size chunk boundaries — changing every block. This is the failure mode of fixed-size chunking. Solution: Rabin fingerprint variable-size chunking. The boundary is determined by content, so a change to the start of a video only changes the first few chunks. The rest of the 1GB file's blocks are unchanged and don't need uploading.

**Q: Two users share a folder. Both edit the same document simultaneously. What happens?**

Each device uploads its version independently. The Metadata Service detects a conflict using version vectors: neither version dominates the other (concurrent edits). The service creates a conflict copy with both versions and notifies all devices. For text files, attempt automatic three-way merge using the common ancestor version (stored in version history). If merge succeeds, no conflict file. If merge fails (overlapping edits), surface both versions to users.

---

## See Also

- **Architecture by scale**: [07-interview-templates/architecture-by-scale.md](../../07-interview-templates/architecture-by-scale.md)
- **Consistency and conflicts**: [01-foundations/consistency-and-conflicts.md](../../01-foundations/consistency-and-conflicts.md)
- **CDC for sync**: [01-foundations/change-data-capture.md](../../01-foundations/change-data-capture.md)
