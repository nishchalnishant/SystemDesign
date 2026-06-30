---
module: 06-lld
topic: Problems
status: unread
tags: [06-lld, lld, s3, file-system, composite, permissions, metadata]
---
# Design S3 Object Storage / File System

> **Difficulty**: Hard
> **Asked at**: Amazon, Dropbox, Google
> **Key Patterns**: Composite (folder hierarchy), Metadata separation, Strategy (permissions)

---

## Understanding the Problem

Design a simplified S3-like object storage or hierarchical file system supporting bucket/folder/object operations, metadata management, and access control.

---

## Clarifying Questions

**You**: "Are we modeling S3 (flat buckets with key prefixes) or a true hierarchical file system?"
**Interviewer**: "S3-style: buckets are top-level, objects have path-like keys. Folders are virtual (key prefix)."

**You**: "What operations do we need — CRUD on objects, bucket creation, metadata?"
**Interviewer**: "Put, get, delete, list (with prefix filter), and per-object ACL."

**You**: "What's the scale assumption?"
**Interviewer**: "Focus on the object model and interfaces. Assume single-node storage for now."

**You**: "Do we need versioning?"
**Interviewer**: "Optional — discuss as a follow-up."

**You**: "What metadata do objects carry?"
**Interviewer**: "Content-type, size, checksum, custom key-value tags."

---

## Final Requirements

**In scope:**
1. Bucket create/delete
2. Object put (with metadata), get, delete
3. List objects in a bucket with optional prefix filter
4. Per-object and per-bucket ACL (READ, WRITE, DELETE permissions)
5. Metadata: content-type, size, checksum, tags

**Out of scope:**
- Versioning (follow-up)
- Multipart upload (follow-up)
- Replication / durability
- Real binary storage (use in-memory byte arrays)

---

## Core Entities and Relationships

| Entity | Responsibility |
|--------|---------------|
| `StorageService` | Entry point — routes to bucket/object operations |
| `Bucket` | Top-level container; owns ACL and object index |
| `StorageObject` | Key, metadata, data (bytes); owned by a bucket |
| `ObjectMetadata` | content_type, size, checksum, tags, created_at |
| `ACL` | Access rules: principal → set of Permission |
| `Permission` | Enum: READ, WRITE, DELETE |
| `Principal` | User or group |

`StorageService` validates ACL before delegating to `Bucket` or `StorageObject`. Metadata is stored separately from binary data (consistent with S3 design).

---

## Class Design

### StorageObject

| Requirement | What StorageObject must track |
|-------------|------------------------------|
| "Metadata separate from data" | metadata: ObjectMetadata, data: bytes |
| "Per-object ACL" | acl: ACL |
| "Path-like key" | key: str (e.g., "images/2024/photo.jpg") |

```
class StorageObject:
- bucket_name: str
- key: str
- data: bytes
- metadata: ObjectMetadata
- acl: ACL

+ get_data() -> bytes
+ get_metadata() -> ObjectMetadata
+ update_acl(principal, permissions: set[Permission])
```

### ObjectMetadata

```
class ObjectMetadata:
- content_type: str
- size: int
- checksum: str   # MD5 or SHA256
- tags: dict[str, str]
- created_at: datetime
- last_modified: datetime
- etag: str       # usually MD5 of content
```

### Bucket

```
class Bucket:
- name: str
- owner: str
- acl: ACL
- objects: dict[str, StorageObject]   # key → object
- created_at: datetime

+ put_object(key, data, metadata, acl) -> StorageObject
+ get_object(key) -> Optional[StorageObject]
+ delete_object(key) -> bool
+ list_objects(prefix="") -> list[StorageObject]
```

### ACL

```
class ACL:
- grants: dict[str, set[Permission]]   # principal_id → permissions

+ grant(principal: str, permissions: set[Permission])
+ revoke(principal: str, permissions: set[Permission])
+ has_permission(principal: str, permission: Permission) -> bool
+ is_public(permission: Permission) -> bool
```

---

## Implementation

### Core Method: `StorageService.put_object`

**Core logic:**
1. Validate bucket exists
2. Check caller has WRITE permission on the bucket
3. Compute checksum of data
4. Create `ObjectMetadata` and `StorageObject`
5. Store in bucket's object index
6. Return the stored object's metadata

**Edge cases:**
- Bucket does not exist → BucketNotFoundError
- Caller lacks WRITE permission → PermissionDeniedError
- Key is empty → raise ValueError
- Overwrite existing object (versioning disabled) → silently replaces

```python
def put_object(self, caller, bucket_name, key, data, content_type, tags=None):
    bucket = self._get_bucket(bucket_name)
    if not bucket:
        raise BucketNotFoundError(bucket_name)
    if not bucket.acl.has_permission(caller, Permission.WRITE):
        raise PermissionDeniedError(f"{caller} lacks WRITE on {bucket_name}")
    if not key:
        raise ValueError("Object key cannot be empty")

    checksum = hashlib.md5(data).hexdigest()
    metadata = ObjectMetadata(
        content_type=content_type,
        size=len(data),
        checksum=checksum,
        tags=tags or {},
        created_at=datetime.utcnow(),
        last_modified=datetime.utcnow(),
        etag=checksum
    )
    obj = StorageObject(
        bucket_name=bucket_name,
        key=key,
        data=data,
        metadata=metadata,
        acl=ACL()   # inherit bucket ACL by default or start empty
    )
    bucket.objects[key] = obj
    return metadata
```

### Core Method: `Bucket.list_objects`

```python
def list_objects(self, prefix=""):
    if not prefix:
        return list(self.objects.values())
    return [
        obj for key, obj in self.objects.items()
        if key.startswith(prefix)
    ]
```

### Core Method: `StorageService.get_object`

```python
def get_object(self, caller, bucket_name, key):
    bucket = self._get_bucket(bucket_name)
    if not bucket:
        raise BucketNotFoundError(bucket_name)

    obj = bucket.get_object(key)
    if not obj:
        raise ObjectNotFoundError(key)

    # Check bucket-level READ, then object-level READ
    if not (bucket.acl.has_permission(caller, Permission.READ) or
            obj.acl.has_permission(caller, Permission.READ) or
            obj.acl.is_public(Permission.READ)):
        raise PermissionDeniedError(f"{caller} lacks READ on {key}")

    return obj.data, obj.metadata
```

### ACL implementation

```python
class ACL:
    PUBLIC_PRINCIPAL = "*"

    def __init__(self):
        self.grants = {}

    def grant(self, principal, permissions):
        if principal not in self.grants:
            self.grants[principal] = set()
        self.grants[principal].update(permissions)

    def has_permission(self, principal, permission):
        return (permission in self.grants.get(principal, set()) or
                self.is_public(permission))

    def is_public(self, permission):
        return permission in self.grants.get(self.PUBLIC_PRINCIPAL, set())
```

---

## Verification

```
StorageService ss

# Create bucket
ss.create_bucket(caller="alice", name="photos", owner="alice")
  bucket created, alice has all permissions (owner)

# Upload object
ss.put_object(caller="alice", bucket_name="photos",
              key="2024/june/vacation.jpg", data=b"...", content_type="image/jpeg")
  checksum = MD5(data) = "abc123"
  object stored: key="2024/june/vacation.jpg"

# List with prefix
ss.list_objects(caller="alice", bucket_name="photos", prefix="2024/june/")
  → [vacation.jpg object]

# Bob tries to read without permission
ss.get_object(caller="bob", bucket_name="photos", key="2024/june/vacation.jpg")
  bucket.acl.has_permission("bob", READ) → False
  obj.acl.has_permission("bob", READ) → False
  obj.acl.is_public(READ) → False
  → raise PermissionDeniedError

# Make object public
obj.update_acl("*", {Permission.READ})
ss.get_object(caller="bob", ...)
  obj.acl.is_public(READ) → True
  → returns data + metadata
```

---

## Deep Dive & Extensibility

### 1. "How would you add object versioning?"

Change `Bucket.objects` from `dict[str, StorageObject]` to `dict[str, list[StorageObject]]`. Each put appends a new version. The latest version is `objects[key][-1]`. Add a `version_id` field to `StorageObject` (UUID or sequential).

```python
def put_object_versioned(self, key, data, metadata):
    if key not in self.versions:
        self.versions[key] = []
    obj = StorageObject(..., version_id=generate_id())
    self.versions[key].append(obj)
    return obj

def get_object(self, key, version_id=None):
    versions = self.versions.get(key, [])
    if not versions:
        return None
    if version_id:
        return next((v for v in versions if v.version_id == version_id), None)
    return versions[-1]  # latest
```

### 2. "How would you handle multipart upload for large objects?"

```python
class MultipartUpload:
    def __init__(self, upload_id, bucket, key):
        self.upload_id = upload_id
        self.parts = {}   # part_number → bytes

    def upload_part(self, part_number, data):
        self.parts[part_number] = data
        return hashlib.md5(data).hexdigest()  # ETag for the part

    def complete(self):
        all_data = b"".join(self.parts[n] for n in sorted(self.parts))
        return all_data
```

`StorageService` manages `active_uploads: dict[upload_id, MultipartUpload]`. Client uploads parts in parallel, then calls `complete_multipart_upload`.

### 3. "How would you model the Composite pattern for a true hierarchical file system?"

For a true FS (not S3 flat), use Composite:

```python
class FileSystemEntry (abstract):
+ get_name() -> str
+ get_size() -> int

class File(FileSystemEntry):
- name: str
- content: bytes
+ get_size() -> int  # len(content)

class Directory(FileSystemEntry):
- name: str
- children: dict[str, FileSystemEntry]
+ add(entry: FileSystemEntry)
+ get_size() -> int  # sum of children sizes (recursive)
+ list() -> list[FileSystemEntry]
```

`get_size()` on `Directory` recursively sums children — the classic Composite pattern.

### 4. "How do you store metadata separately from data?"

S3's design principle: metadata reads are far more frequent than data reads. Store metadata in a fast index (DB/in-memory dict) separate from the binary data (block storage). `get_object` can return just metadata (HEAD request) without loading the binary.

```python
class StorageBackend:
    def put(self, object_id, data: bytes) -> str:  # returns storage path
        path = f"/storage/{object_id[:2]}/{object_id}"
        with open(path, 'wb') as f:
            f.write(data)
        return path

    def get(self, path) -> bytes:
        with open(path, 'rb') as f:
            return f.read()
```

`StorageObject.data_path` points to the backend; `metadata` lives in memory/DB.

---

## Interviewer Questions by Level

**Junior**: Bucket, StorageObject with key and data. Put/get/delete. List with prefix. Basic owner-only access.

**Mid-level**: ACL with per-principal, per-permission grants. Public read support. Metadata separate from data. Checksum computation on put.

**Senior**: Versioning design (list of versions per key). Multipart upload flow. Composite for true file system. Metadata vs data store separation. Permission inheritance (bucket → object fallthrough).

---

## Common Interview Questions

- **Q**: How does S3 simulate folders when it's actually flat?
  **A**: S3 has no true folders. Objects have keys like "images/2024/photo.jpg". A "folder" is just a common key prefix. `list_objects(prefix="images/")` returns all objects with that prefix — simulating folder contents.

- **Q**: Why separate metadata from binary data?
  **A**: Metadata (size, content-type, tags) is read far more often than data (for listing, HEAD requests). Storing metadata in a fast index allows O(1) metadata lookups without reading the binary blob.

- **Q**: How do you check permissions — bucket-level or object-level?
  **A**: Both. Check bucket ACL first. If bucket ACL grants permission, access is allowed. If not, check object-level ACL. If either grants (or public), allow. Both must deny to reject.

- **Q**: What's the ETag in object metadata?
  **A**: Entity Tag — usually the MD5 of the object's content. Used for cache validation (If-None-Match header) and integrity checks. On update, ETag changes so clients know to re-fetch.

- **Q**: What is the Composite pattern and why use it for a file system?
  **A**: Composite treats individual objects (files) and collections (directories) uniformly. `get_size()` on a directory recursively sums its children's sizes. Callers don't need to know if they're dealing with a file or directory — they call the same interface.
