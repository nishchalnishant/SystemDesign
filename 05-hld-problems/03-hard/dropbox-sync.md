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
