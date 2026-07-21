> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design Dropbox file sync — efficient bi-directional file synchronization with delta sync, conflict resolution, and multi-device consistency.
>
> **Key design decisions:**
> - File chunking: files split into 4MB chunks; each chunk content-addressed by SHA256 hash; upload only changed chunks (delta sync saves 90% bandwidth on typical edits)
> - Sync protocol: client computes local chunk manifest → sends to server → server returns list of missing chunks → client uploads only those → server assembles file
> - Change detection: client watches filesystem events (inotify/FSEvents); debounce 200ms; compute diff; sync only changed files
> - Conflict resolution: if two devices edit same file concurrently → both versions preserved; user sees "filename (conflicted copy)" + original; no auto-merge
> - Metadata service: file tree (file_id, parent_folder_id, name, version, chunks[]) in PostgreSQL; version vector per file; chunk store in S3 keyed by hash
> - Offline support: local SQLite tracks pending sync queue; batch uploads on reconnect; reads work offline from local copy
> - Bandwidth optimization: rsync-like differential sync; skip unchanged chunks via hash comparison; compression for text files
>
> **Key takeaway:** Content-addressed chunk storage (SHA256) enables deduplication and delta sync simultaneously — the chunk hash tells you both what to skip uploading and whether a chunk already exists globally.

---
module: 05-hld-problems
topic: Hard
status: unread
tags: [05-hld-problems, system-design, hard, dropbox, file-sync, delta-sync, conflict-resolution]
---
# Design Dropbox File Sync

> **Difficulty**: Hard | **Asked at**: Dropbox, Box, Google, Apple

---

## Problem Statement

Design a file synchronization system like Dropbox. Users store files in a folder on their device. When a file changes, the change syncs to the cloud and propagates to all the user's other devices. The system must handle large files efficiently, resolve conflicts, and work offline.

---

## Functional Requirements

1. **File sync**: Changes to files on any device sync to the cloud and other devices
2. **Delta sync**: Only changed bytes of a file are uploaded (not the whole file on every change)
3. **Conflict resolution**: If the same file is edited on two devices simultaneously, surface both versions
4. **Offline support**: Changes made offline sync when the device reconnects
5. **File sharing**: Share folders with other users (read or read-write)
6. **Version history**: Keep last 30 days of file versions; restore previous versions

---

## Non-Functional Requirements

- **Scale**: 500M users, 10B files stored, 1M sync operations/sec
- **Latency**: File changes visible on other devices within 5 seconds
- **Efficiency**: Uploading a 1 GB file after a 1 KB change should upload ~1 KB (delta sync)
- **Availability**: 99.99% — file access must work during partial failures
- **Storage**: 10B files × 1 MB avg = 10 PB; with dedup: ~3 PB

---

## Core Entities

| Entity | Key Fields |
|--------|-----------|
| `File` | file_id, owner_id, path, size, sha256, content_s3_key, version, updated_at |
| `FileBlock` | block_id (sha256 of block), size, s3_key |
| `FileVersion` | file_id, version, block_ids[], created_at, device_id |
| `SyncEvent` | event_id, user_id, file_id, event_type (create/update/delete), device_id, timestamp |
| `Device` | device_id, user_id, platform, last_sync_cursor |

---

## API Design

```http
# Client SDK handles chunking; these are the underlying REST calls

POST /api/v1/files/upload-block
Body: { "block_id": "<sha256>", "data": "<base64>" }
Response 200: { "block_id": "...", "status": "stored" }

POST /api/v1/files/commit
Body: {
  "path": "/photos/vacation.jpg",
  "block_ids": ["sha256_a", "sha256_b", "sha256_c"],
  "size": 5242880,
  "sha256": "<file_hash>",
  "parent_version": 4
}
Response 200: { "file_id": "f123", "version": 5, "conflict": false }

GET /api/v1/files/delta?cursor=<device_cursor>
Response 200: {
  "changes": [
    { "path": "/photos/vacation.jpg", "type": "update", "version": 5 }
  ],
  "cursor": "<new_cursor>"
}

GET /api/v1/files/{file_id}/download-url
Response 200: { "url": "https://s3...presigned" }
```

---

## High-Level Design

```
Device (Dropbox client)
  │ Watch filesystem for changes (inotify / FSEvents)
  │ On change: chunk file → compute block hashes → upload new blocks → commit
  │
  ▼
Upload API
  │ Block dedup: check if block hash exists in block store
  │ Store new blocks in S3
  │ Commit: update file metadata in PostgreSQL, publish event to Kafka
  │
  ▼
Kafka: sync-events (partitioned by user_id)
  │
  ▼
Notification Service
  │ Push sync notification to user's other devices via long-poll or WebSocket
  │
  ▼
Device (other devices)
  │ Receive notification → call /files/delta?cursor=<last_cursor>
  │ Download changed files → apply to local filesystem
  │
Storage:
  PostgreSQL: file metadata, version history, sharing
  S3: file blocks (content-addressed by sha256)
  Redis: block existence cache (sha256 → exists)
  Cassandra: sync cursors per device
```

---

## Deep Dive 1: Delta Sync with Content-Defined Chunking

**Problem**: A user has a 1 GB video file. They edit the title embedded in the video file (a metadata change of 100 bytes in the middle). Uploading the entire 1 GB again is wasteful. How do you upload only the changed bytes?

**Fixed-size chunking (4 MB blocks)**: Split file into 4 MB blocks. Compute SHA-256 of each block. On re-upload, only send blocks with new SHA-256 hashes. 

Problem: inserting bytes at the beginning shifts all block boundaries → all blocks get new hashes → no deduplication.

**Content-Defined Chunking (Rabin fingerprinting)**:
- Slide a 64-byte window over the file, computing a rolling hash at each position
- When the rolling hash matches a pattern (e.g., last 13 bits = 0), create a chunk boundary
- Average chunk size: 8 MB (configurable); range: 1 KB – 128 MB
- Inserting bytes in the middle: only the surrounding chunks change. Adjacent chunks are unaffected.

**Upload protocol**:
1. Client chunks the file using CDC → list of (chunk_offset, chunk_hash)
2. Client sends `need_blocks=[sha256_list]` to server
3. Server checks block store: `block_exists[sha256]` (Redis + S3 check)
4. Server responds: `missing_blocks=[sha256s not yet uploaded]`
5. Client uploads only missing blocks
6. Client commits: `file_path + ordered list of all block sha256s`

For the 100-byte change in a 1 GB video: only 1-2 chunks are new → upload < 32 MB (2 chunks × max 16 MB).

---

## Deep Dive 2: Conflict Detection and Resolution

**Problem**: User A edits `report.docx` on their laptop at 2:00 PM. They go offline. While offline, they're also editing `report.docx` on their tablet. Both devices sync when they reconnect. Which version wins?

**Version vector** (optimistic concurrency):
- Every file has a `version` counter
- When committing a change, the client must provide `parent_version` = the version it based the edit on
- If `parent_version != current_version` in the DB → concurrent edit → **conflict**

```sql
UPDATE files
SET version = version + 1, block_ids = :new_blocks, updated_at = now()
WHERE file_id = :file_id AND version = :parent_version  -- optimistic lock
RETURNING version;
-- 0 rows → conflict
```

**Conflict resolution**:
- Dropbox does NOT auto-merge (too risky for arbitrary file formats)
- Both versions are preserved:
  - Original file: `report.docx` (newer version wins)
  - Conflicted version: `report (Alice's conflicted copy 2026-06-29).docx`
- User must manually merge

**For Google Docs (OT/CRDT)**: Operational Transformation (OT) or CRDT allows automatic merge of concurrent text edits. Dropbox doesn't implement this because it's file-format agnostic.

**Offline sync**: Device tracks a `sync_cursor` (last event ID seen). When reconnecting, sends all pending changes (with their `parent_version` at time of offline edit). Server processes each change; conflicts are detected per file.

---

## Deep Dive 3: Storage Deduplication

**Problem**: 500M users, each with a copy of `cat.jpg` (a viral meme). Storing 500M copies wastes petabytes.

**Content-addressed storage**:
- Block key = SHA-256 of block content
- `S3 path = /blocks/{sha256[0:2]}/{sha256[2:4]}/{sha256}`
- Before uploading a block, check if `S3.head_object(path)` exists — if yes, skip upload
- All 500M copies of `cat.jpg` → one S3 object

**Dedup efficiency**: Dropbox reported 30-40% dedup rate across the user base (popular files, OS files like installer packages shared across users, template documents).

**Block reference counting**: Each block's S3 object is kept alive as long as any file references it. A background GC job scans all file metadata, builds a set of all referenced block SHA-256s, then deletes any S3 objects not in the reference set. GC runs weekly.

**Metadata dedup**: Beyond blocks, entire files can be deduped. If a user copies a folder, the new file records point to the same blocks — no S3 data is copied. Only metadata (file path, permissions) is duplicated.

---

## Interviewer Questions by Level

**Junior**:
- What is file synchronization? What are the challenges compared to just uploading a file?
- What is content-addressed storage? How does deduplication save storage space?
- What happens when two devices edit the same file while both are offline?

**Mid-level**:
- How does delta sync work? Compare fixed-size chunking vs content-defined chunking.
- How do you detect that a file has changed on a device efficiently (without reading the full file)?
- How does the sync cursor work? What does a device do on reconnect after being offline for a week?

**Senior**:
- Design the content-defined chunking algorithm. How does Rabin fingerprinting work and why does it resist insertion/deletion better than fixed-size chunking?
- Design the global deduplication system for 10 PB of blocks across 500M users.
- How would you implement real-time collaborative editing (like Google Docs) on top of the block-sync model?
- A user accidentally deletes their entire Dropbox folder. Design the recovery path.

---

## Back-of-Envelope Estimation

**Scale inputs (given in NFRs):**
- 500M users; 1B files; average file size 1 MB; 10M file uploads/day

**Storage sizing:**
- 1B files × 1 MB average = **~1 PB** raw file storage
- With 3× replication (data durability): **~3 PB** physical
- Storage growth: 10M uploads/day × 1 MB = **~10 TB/day** new data
- Annual growth: **~3.65 PB/year** — must provision capacity ahead of this curve

**Upload throughput:**
- 10M uploads/day ÷ 86,400 sec = **~115 uploads/sec** average
- Peak (workday 9 AM): 10× = **~1,150 uploads/sec**
- Each 1 MB upload in ~1s on a 10 Mbps connection → 1,150 concurrent upload streams = **~1.15 GB/sec** inbound bandwidth at peak

**Chunk-based deduplication:**
- Files split into 4 MB chunks (Dropbox uses content-defined chunking at ~4 MB average)
- 1B files at 1 MB average = 1B chunks (1 file ≈ 1 chunk on average for small files)
- Larger files (10 MB) = ~2.5 chunks each
- Dedup ratio: studies show 30–50% of chunks are duplicates (office documents, node_modules)
- Effective storage: 1 PB × 60% unique = **~600 TB** unique chunk data (vs 1 PB before dedup)
- Chunk hash index: 600M unique chunks × 32 bytes (SHA-256) = **~20 GB** — fits in memory for fast dedup lookup

**Metadata DB sizing:**
- Per file: `{file_id, user_id, name, path, size, chunks[], version, modified_at}` ≈ 500 bytes
- 1B files × 500 bytes = **~500 GB** metadata — fits in a sharded PostgreSQL cluster (e.g., 10 shards × 50 GB each)
- Per chunk reference: `{file_id, chunk_hash, sequence_number}` ≈ 60 bytes
- Avg 2 chunks per file: 2B chunk refs × 60 bytes = **~120 GB**

**Sync event throughput:**
- 500M users; assume 10% daily active = 50M DAU; each DAU makes 5 file changes/day
- Sync events: 50M × 5 ÷ 86,400 = **~2,900 events/sec** to propagate to connected clients
- Each user has ~2 connected devices; sync must fan-out events to 2 devices/user
- Fan-out: 2,900 × 2 = **~5,800 push notifications/sec** to devices via long-poll or SSE

**Architecture decisions driven by these numbers:**
- **Content-addressed chunk storage (S3 + SHA-256 hash as key)**: Deduplication eliminates 40% of storage (600 TB vs 1 PB). Chunk identity is the hash — two identical chunks (same SHA-256) are stored once. This is only possible with content-addressing; path-based storage can't deduplicate across users. At $23/TB/month S3 standard, dedup saves $9.2K/month.
- **Delta sync, not full-file upload on every change**: A 10 MB Word document that had one sentence changed: without delta sync, upload 10 MB. With delta sync (rsync-style or Dropbox's ZXDB chunking), re-upload only the changed chunks (maybe 1 × 4 MB chunk). At 1,150 uploads/sec peak, delta sync reduces upload bandwidth from 1.15 GB/sec to **~460 MB/sec** (60% reduction).
- **Block server separate from metadata server**: The 1.15 GB/sec upload stream and the 500 GB metadata DB have completely different access patterns. Block storage is write-once, read-many, gigabytes per item. Metadata is small random reads/writes, byte-sized items. Merging them means the block I/O saturates the metadata server's I/O subsystem. Separating allows S3 to handle block bytes (optimized for throughput) while PostgreSQL handles metadata (optimized for ACID and complex queries).

---

## Related

**Concepts used in this design**

- [Storage Fundamentals](../../01-foundations/02-hardware-and-networking/01-storage-fundamentals.md)
- [Consistency & Conflicts](../../01-foundations/05-advanced-distributed-theory/01-consistency-and-conflicts.md)
- [Message Brokers](../../02-building-blocks/04-coordination/01-message-brokers.md)
- [Sharding](../../02-building-blocks/03-data-partitioning/01-sharding.md)

**Practice next**

- [Google Drive](../03-hard/google-drive.md)
- [GitHub Code Repo](../03-hard/github-code-repo.md)

Both resolve concurrent edits to a shared file tree.

**Frameworks**: [HLD Template](../../07-interview-templates/01-frameworks/01-hld-template.md) · [Capacity Estimation](../../07-interview-templates/02-cheat-sheets/02-capacity-estimation.md) · [Trade-offs Cheat Sheet](../../07-interview-templates/02-cheat-sheets/01-trade-offs-cheat-sheet.md)
