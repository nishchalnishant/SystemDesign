> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design Google Drive — cloud file storage with real-time collaboration, conflict resolution, fine-grained permissions, and offline support at petabyte scale.
>
> **Key design decisions:**
> - File storage: chunked uploads (4MB chunks); each chunk content-addressed by SHA256 hash → deduplication across all users; stored in GCS/S3
> - Metadata: file tree (folder hierarchy), file versions, chunk manifests stored in PostgreSQL or Spanner; chunk store keyed by hash
> - Sync protocol: client maintains local file tree + version vectors; on upload, diff local vs server state; upload only changed chunks (delta sync)
> - Conflict resolution: last-write-wins for simple files; OT/CRDT for collaborative documents (Google Docs); version branching for offline edits
> - Permissions: ACL table (file_id, user_id, permission_level); inherited permissions (folder grants access to children); Google Workspace integration
> - Real-time collaboration: WebSocket channel per document; operational transformation (OT) for concurrent edits; server applies OT to resolve conflicts
> - Offline support: local SQLite file manifest; sync changes queue persisted locally; push all pending changes on reconnect
>
> **Key takeaway:** Content-addressed chunk storage (SHA256 hash as key) enables global deduplication — if 1000 users upload the same file, only one copy is stored; delta sync means only changed chunks are transferred.

---
module: 05-hld-problems
topic: Hard
status: unread
tags: [05-hld-problems, system-design, hard, google-drive, file-storage, collaboration, permissions]
---
# Design Google Drive

> **Difficulty**: Hard | **Asked at**: Google, Microsoft, Dropbox, Amazon

---

## Problem Statement

Design a cloud file storage and collaboration platform like Google Drive. Users upload, organize, and share files and folders. Multiple users can view and edit documents simultaneously. The system must handle petabytes of storage, fine-grained permissions, and real-time collaboration.

---

## Functional Requirements

1. **File storage**: Upload, download, organize files in folders; support any file type up to 5 GB
2. **Sharing**: Share files and folders with specific users or via shareable link (view/comment/edit)
3. **Real-time collaboration**: Multiple users edit Google Docs/Sheets simultaneously
4. **Version history**: Keep previous versions; restore any version within 30 days
5. **Search**: Search files by name, type, and content
6. **Offline access**: Access cached files without internet; sync when reconnected

---

## Non-Functional Requirements

- **Scale**: 1B users, 15 GB free per user, 15 EB total storage
- **Latency**: File uploads < 5s for 10 MB; document open < 2s
- **Availability**: 99.99% — files must always be accessible
- **Durability**: 11 nines (0.000000001% annual data loss probability) via geo-redundant storage
- **Concurrency**: 100 simultaneous editors on a popular Google Doc

---

## Core Entities

| Entity | Key Fields |
|--------|-----------|
| `File` | file_id, owner_id, name, mime_type, size, parent_folder_id, created_at, modified_at |
| `FileContent` | file_id, version, chunk_ids[], gcs_path, sha256 |
| `Permission` | file_id, principal (user/group/anyone), role (viewer/commenter/editor) |
| `Folder` | folder_id, owner_id, name, parent_folder_id |
| `DocumentRevision` | doc_id, revision_id, ops[] (for Docs/Sheets — OT operations) |

---

## API Design

```http
POST /api/v1/files/upload
Headers: Content-Type: multipart/form-data
Body: { file_data, "parent_folder_id": "f123", "name": "report.pdf" }
Response 201: { "file_id": "fi456", "size": 2097152, "version": 1 }

GET /api/v1/files/{file_id}/download
Response 302: redirect to presigned GCS URL

POST /api/v1/files/{file_id}/permissions
Body: { "email": "alice@example.com", "role": "editor" }
Response 200: { "permission_id": "p789" }

GET /api/v1/files/{file_id}/revisions
Response 200: { "revisions": [{ "revision_id": "r3", "modified_at": "...", "modified_by": "..." }] }

GET /api/v1/search?q=budget+2026&type=spreadsheet
Response 200: { "files": [...] }
```

---

## High-Level Design

```
User (browser / mobile)
  │
  ▼
API Gateway + Auth
  │
  ├── File Service
  │     Upload: chunked → GCS (Google Cloud Storage)
  │     Metadata: PostgreSQL (file tree, permissions, versions)
  │     Download: redirect to presigned GCS URL
  │
  ├── Collaboration Service (for Docs/Sheets)
  │     WebSocket connections per document
  │     Operational Transformation (OT) server
  │     Stores ops in Spanner; merges concurrent edits
  │
  ├── Search Service
  │     Elasticsearch: file names, metadata
  │     Content extraction: PDF/Office parser → full-text index
  │
  └── Notification Service
        Kafka: file-change-events → notify collaborators

Storage:
  GCS: file content (chunked, geo-redundant, 11 nines durability)
  PostgreSQL: file metadata, folder tree, permissions, version history
  Spanner: document revision history (globally consistent)
  Elasticsearch: search index
```

---

## Deep Dive 1: Chunked Upload for Large Files

**Problem**: A user uploads a 5 GB video file. On a mobile connection, this upload may be interrupted midway. Restarting from scratch wastes bandwidth.

**Resumable upload protocol**:

1. **Initiate**: `POST /upload/initiate` → returns `upload_id`
2. **Upload chunks**: Client splits file into 5 MB chunks; uploads each with byte range header
   ```http
   PUT /upload/{upload_id}
   Content-Range: bytes 0-5242879/5368709120
   Content-Length: 5242880
   Body: <chunk data>
   Response 200: { "received_bytes": 5242880 }
   ```
3. **Resume**: Client queries `GET /upload/{upload_id}/status` → `{ "received_bytes": 10485760 }` → resumes from that offset
4. **Complete**: Final chunk returns 201 Created with the file metadata

**GCS storage**: Each chunk is stored as a GCS object. After all chunks are uploaded, GCS composes them into a single object (server-side — no re-download). Chunked composition is atomic.

**Content deduplication**: Before storing, compute SHA-256 of each chunk. If an identical chunk exists (same SHA-256), skip upload — reuse the existing GCS object. Effective for files with lots of padding or standard headers.

---

## Deep Dive 2: Real-Time Collaboration with OT

**Problem**: Alice and Bob simultaneously edit "Project Plan.docx". Alice inserts "urgent" at position 10; Bob deletes the character at position 10. Applied naively, one edit will corrupt the other. How do you merge concurrent edits?

**Operational Transformation (OT)**:
- Every edit is an operation: `{ type: "insert", position: 10, text: "urgent" }` or `{ type: "delete", position: 10, count: 1 }`
- The OT server receives operations from all clients, assigns a global sequence number, and transforms concurrent operations against each other before applying

**Transform function** (Insert vs Delete):
```python
def transform_insert_against_delete(op_insert, op_delete):
    # If insert position is after the delete, shift insert left
    if op_insert.position > op_delete.position:
        op_insert.position -= op_delete.count
    return op_insert
```

**Server-side state machine**:
```
Client A sends: Op(seq=5, insert at 10, "urgent")
Server has processed up to seq=6 (Bob's delete at 10)
Server transforms Op_A against ops 5..6 → adjusted_position = 9
Server applies adjusted op → assigns seq=7
Server broadcasts seq=7 to all clients including B
```

**Client-side**: Each client maintains a local copy. Operations are applied immediately locally for responsiveness (optimistic). Server reconciliation arrives within 100ms and may reorder operations. Client transforms buffered local ops against server ops.

---

## Deep Dive 3: Permissions and Sharing

**Problem**: A folder is shared with a team. The team's membership changes. How do you ensure permissions are checked efficiently without a slow recursive lookup on every file access?

**Permission inheritance**: Files inherit permissions from their parent folder. The permission model is:
- Explicit permissions on a file override inherited permissions
- Effective role = max(explicit role, inherited role) — editors > commenters > viewers

**Permission table** (PostgreSQL):
```sql
CREATE TABLE permissions (
  file_id text,
  principal_type text,  -- user, group, domain, anyone
  principal_id text,
  role text,            -- viewer, commenter, editor, owner
  can_share bool,
  PRIMARY KEY (file_id, principal_type, principal_id)
);
```

**Efficient access check**: On every file open, check:
1. Is the file's `owner_id` the current user? → allow
2. Is there an explicit permission row for `(file_id, user_id)`?
3. Is there a permission for any group the user belongs to?
4. Walk up the folder tree: same checks for each ancestor folder

**Caching**: Cache user's effective permission per file in Redis (`perm:{user_id}:{file_id}` → role, TTL 5 min). Invalidated when any permission on the file or ancestor folder changes.

**Sharing links**: Generate `share_token` (256-bit random). Store in permissions table as `principal_type=link, principal_id=sha256(token)`. Anyone with the link makes requests with `?token=...`; server looks up permission by token hash.

---

## Interviewer Questions by Level

**Junior**:
- How do you store 15 GB of files for 1B users? What storage backend would you use?
- What is file sharing? What's the difference between a view and an edit permission?
- Why is resumable upload important for large files?

**Mid-level**:
- How does chunked upload work? How does a client resume after an interrupted upload?
- How do you implement permission inheritance (file inherits permissions from parent folder)?
- How does content deduplication reduce storage costs?

**Senior**:
- Design the real-time collaboration system for Google Docs — how does OT prevent conflicting edits from corrupting the document?
- How do you check permissions efficiently for a deeply nested folder structure with group memberships?
- Design the version history system — how do you store 30 days of revision history for 15 EB of files without tripling storage costs?
- How do you implement cross-organizational sharing (Alice at Company A shares a file with Bob at Company B)?

---

## Back-of-Envelope Estimation

**Scale inputs (given in NFRs):**
- 1B users; 2B files stored; average file 500 KB; 50M uploads/day; 1B file accesses/day

**Storage sizing:**
- 2B files × 500 KB average = **~1 PB** raw file data
- With 3× replication: **~3 PB** physical storage
- Growth rate: 50M uploads/day × 500 KB = **~25 TB/day** new data
- Annual growth: **~9 PB/year** — requires continuous capacity planning

**Upload throughput:**
- 50M uploads/day ÷ 86,400 sec = **~579 uploads/sec** average
- Peak (business hours): 5× = **~2,895 uploads/sec**
- Average upload: 500 KB in ~400ms on a 10 Mbps link
- Peak aggregate bandwidth: 2,895 × 500 KB / 400ms = **~3.6 GB/sec** inbound peak

**Download/access throughput:**
- 1B file accesses/day ÷ 86,400 sec = **~11,574 accesses/sec** average
- Average file: 500 KB; P99 file: ~10 MB (documents, presentations)
- Aggregate outbound: 11,574 × 500 KB = **~5.8 GB/sec** average
- CDN handles ~95% of accesses (frequently accessed files cached at edge) → origin sees ~580 accesses/sec

**Metadata DB sizing:**
- Per file: `{file_id, owner_id, name, mime_type, size, storage_key, parent_folder_id, created_at, modified_at, shared_with[]}` ≈ 1 KB
- 2B files × 1 KB = **~2 TB** metadata storage
- Sharded by `owner_id` (user's files on the same shard — efficient tree traversal)
- Each shard: 2 TB ÷ 20 shards = **100 GB per shard** — manageable for PostgreSQL

**Sharing and permission model:**
- 1B users; assume 10% files shared = 200M shared files
- Each shared file: avg 5 share entries → 1B share records
- 1B share records × 200 bytes = **~200 GB** ACL storage
- ACL lookup on every file access: Redis cache (`file_id → {allowed_users_set}`) for hot files

**Thumbnail generation:**
- Images/videos: assume 30% of 50M uploads = 15M thumbnail requests/day
- Thumbnail job: ~500ms CPU per image (resize to 256×256, encode JPEG)
- 15M ÷ 86,400 sec = **~174 thumbnail jobs/sec** → needs ~87 workers at 2 jobs/sec/worker
- Thumbnails stored in object storage; served via CDN (immutable once generated)

**Architecture decisions driven by these numbers:**
- **Chunked upload for large files**: Uploading a 500 MB video in one HTTP request means a single TCP failure loses everything and the client must restart. Chunking at 5 MB per chunk means only 1 chunk (5 MB) is retried on failure. At 2,895 uploads/sec including large files, chunked upload also enables parallel chunk uploads (4× concurrency = 4× effective bandwidth utilization per client).
- **Separate metadata store from blob store**: Metadata (2 TB) requires ACID transactions, foreign keys, and complex queries (list folder contents, search by name, check permissions). Blob data (1 PB) requires high throughput, cheap per-byte cost, and geo-replication. Merging them would force the blob store to handle relational queries it's not optimized for, or force the metadata DB to handle 25 TB/day of blob ingestion. S3 for blobs + PostgreSQL for metadata is the natural split.
- **CDN for file downloads**: At 5.8 GB/sec average outbound, serving from origin servers in one region would require massive bandwidth and would be slow for geographically distant users. Files are immutable per version — a file's content at version V never changes. CDN TTL can be long (24h+). 95% CDN hit rate reduces origin outbound from 5.8 GB/sec to **290 MB/sec** — a 20× reduction in origin bandwidth cost.

---

## Related

**Concepts used in this design**

- [Storage Fundamentals](../../01-foundations/02-hardware-and-networking/01-storage-fundamentals.md)
- [CDN](../../02-building-blocks/01-networking/05-cdn.md)
- [Sharding](../../02-building-blocks/03-data-partitioning/01-sharding.md)
- [Consistency & Conflicts](../../01-foundations/05-advanced-distributed-theory/01-consistency-and-conflicts.md)

**Practice next**

- [Dropbox Sync](../03-hard/dropbox-sync.md)
- [Pastebin](../01-easy/pastebin.md)

Dropbox sync is this problem viewed from the client.

**Frameworks**: [HLD Template](../../07-interview-templates/01-frameworks/01-hld-template.md) · [Capacity Estimation](../../07-interview-templates/02-cheat-sheets/02-capacity-estimation.md) · [Trade-offs Cheat Sheet](../../07-interview-templates/02-cheat-sheets/01-trade-offs-cheat-sheet.md)
