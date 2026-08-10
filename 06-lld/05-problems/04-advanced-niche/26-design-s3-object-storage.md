> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design S3 Object Storage / File System — an advanced problem combining the Composite pattern for hierarchical data with Strategy for permissions and metadata management.
>
> **Key concepts:**
> - Core Entities: `FileSystemEntry` (interface), `File` (leaf), `Directory` (composite), `User`, `Permission`.
> - Composite Pattern: A `Directory` contains a list of `FileSystemEntry`s. Both `File` and `Directory` implement methods like `getSize()` and `delete()`.
> - Separation of Concerns: The LLD focuses on the *metadata* (names, paths, sizes, permissions), not the actual physical byte storage (which would be handled by a storage engine).
> - Permissions (Strategy): Checking if a `User` has `READ` or `WRITE` access requires traversing up the tree. If the user doesn't have explicit access to the file, check the parent directory, and so on.
> - Concurrency: Handling concurrent file writes or directory creations requires careful locking, usually `ReadWriteLock` on specific directory nodes.
>
> **Key takeaway:** This is the quintessential Composite Pattern problem. Focus heavily on how `getSize()` works recursively on a `Directory` and how path resolution (`/usr/bin/java`) traverses the tree.

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

```java
public ObjectMetadata putObject(String caller, String bucketName, String key, byte[] data,
                                 String contentType, Map<String, String> tags) {
    Bucket bucket = getBucket(bucketName);
    if (bucket == null) {
        throw new BucketNotFoundError(bucketName);
    }
    if (!bucket.getAcl().hasPermission(caller, Permission.WRITE)) {
        throw new PermissionDeniedError(caller + " lacks WRITE on " + bucketName);
    }
    if (key == null || key.isEmpty()) {
        throw new IllegalArgumentException("Object key cannot be empty");
    }

    String checksum = md5Hex(data);
    Instant now = Instant.now();
    ObjectMetadata metadata = new ObjectMetadata(
        contentType,
        data.length,
        checksum,
        tags != null ? tags : new HashMap<>(),
        now,
        now,
        checksum
    );
    StorageObject obj = new StorageObject(
        bucketName,
        key,
        data,
        metadata,
        new ACL()   // inherit bucket ACL by default or start empty
    );
    bucket.getObjects().put(key, obj);
    return metadata;
}

private static String md5Hex(byte[] data) {
    try {
        MessageDigest digest = MessageDigest.getInstance("MD5");
        byte[] hash = digest.digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    } catch (NoSuchAlgorithmException e) {
        throw new RuntimeException(e);
    }
}
```

### Core Method: `Bucket.list_objects`

```java
public List<StorageObject> listObjects(String prefix) {
    if (prefix == null || prefix.isEmpty()) {
        return new ArrayList<>(this.objects.values());
    }
    List<StorageObject> result = new ArrayList<>();
    for (Map.Entry<String, StorageObject> entry : this.objects.entrySet()) {
        if (entry.getKey().startsWith(prefix)) {
            result.add(entry.getValue());
        }
    }
    return result;
}
```

### Core Method: `StorageService.get_object`

```java
public AbstractMap.SimpleEntry<byte[], ObjectMetadata> getObject(String caller, String bucketName, String key) {
    Bucket bucket = getBucket(bucketName);
    if (bucket == null) {
        throw new BucketNotFoundError(bucketName);
    }

    StorageObject obj = bucket.getObject(key).orElse(null);
    if (obj == null) {
        throw new ObjectNotFoundError(key);
    }

    // Check bucket-level READ, then object-level READ
    boolean allowed = bucket.getAcl().hasPermission(caller, Permission.READ)
            || obj.getAcl().hasPermission(caller, Permission.READ)
            || obj.getAcl().isPublic(Permission.READ);
    if (!allowed) {
        throw new PermissionDeniedError(caller + " lacks READ on " + key);
    }

    return new AbstractMap.SimpleEntry<>(obj.getData(), obj.getMetadata());
}
```

### ACL implementation

```java
public class ACL {
    public static final String PUBLIC_PRINCIPAL = "*";

    private final Map<String, Set<Permission>> grants = new HashMap<>();

    public void grant(String principal, Set<Permission> permissions) {
        grants.computeIfAbsent(principal, k -> new HashSet<>()).addAll(permissions);
    }

    public void revoke(String principal, Set<Permission> permissions) {
        Set<Permission> existing = grants.get(principal);
        if (existing != null) {
            existing.removeAll(permissions);
        }
    }

    public boolean hasPermission(String principal, Permission permission) {
        return grants.getOrDefault(principal, Collections.emptySet()).contains(permission)
                || isPublic(permission);
    }

    public boolean isPublic(Permission permission) {
        return grants.getOrDefault(PUBLIC_PRINCIPAL, Collections.emptySet()).contains(permission);
    }
}
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

```java
public StorageObject putObjectVersioned(String key, byte[] data, ObjectMetadata metadata) {
    versions.computeIfAbsent(key, k -> new ArrayList<>());
    StorageObject obj = new StorageObject(/* ... */ generateId());
    versions.get(key).add(obj);
    return obj;
}

public Optional<StorageObject> getObject(String key, String versionId) {
    List<StorageObject> objVersions = versions.getOrDefault(key, Collections.emptyList());
    if (objVersions.isEmpty()) {
        return Optional.empty();
    }
    if (versionId != null) {
        return objVersions.stream()
                .filter(v -> v.getVersionId().equals(versionId))
                .findFirst();
    }
    return Optional.of(objVersions.get(objVersions.size() - 1));  // latest
}
```

### 2. "How would you handle multipart upload for large objects?"

```java
public class MultipartUpload {
    private final String uploadId;
    private final String bucket;
    private final String key;
    private final Map<Integer, byte[]> parts = new TreeMap<>();  // part_number → bytes

    public MultipartUpload(String uploadId, String bucket, String key) {
        this.uploadId = uploadId;
        this.bucket = bucket;
        this.key = key;
    }

    public String uploadPart(int partNumber, byte[] data) {
        parts.put(partNumber, data);
        return md5Hex(data);  // ETag for the part
    }

    public byte[] complete() {
        // TreeMap keeps parts sorted by part number
        int totalLength = parts.values().stream().mapToInt(p -> p.length).sum();
        byte[] allData = new byte[totalLength];
        int offset = 0;
        for (byte[] part : parts.values()) {
            System.arraycopy(part, 0, allData, offset, part.length);
            offset += part.length;
        }
        return allData;
    }
}
```

`StorageService` manages `active_uploads: dict[upload_id, MultipartUpload]`. Client uploads parts in parallel, then calls `complete_multipart_upload`.

### 3. "How would you model the Composite pattern for a true hierarchical file system?"

For a true FS (not S3 flat), use Composite:

```java
public interface FileSystemEntry {
    String getName();
    int getSize();
}

public class File implements FileSystemEntry {
    private final String name;
    private final byte[] content;

    public File(String name, byte[] content) {
        this.name = name;
        this.content = content;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int getSize() {
        return content.length;
    }
}

public class Directory implements FileSystemEntry {
    private final String name;
    private final Map<String, FileSystemEntry> children = new HashMap<>();

    public Directory(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    public void add(FileSystemEntry entry) {
        children.put(entry.getName(), entry);
    }

    @Override
    public int getSize() {
        // sum of children sizes (recursive)
        int total = 0;
        for (FileSystemEntry entry : children.values()) {
            total += entry.getSize();
        }
        return total;
    }

    public List<FileSystemEntry> list() {
        return new ArrayList<>(children.values());
    }
}
```

`get_size()` on `Directory` recursively sums children — the classic Composite pattern.

### 4. "How do you store metadata separately from data?"

S3's design principle: metadata reads are far more frequent than data reads. Store metadata in a fast index (DB/in-memory dict) separate from the binary data (block storage). `get_object` can return just metadata (HEAD request) without loading the binary.

```java
public class StorageBackend {

    public String put(String objectId, byte[] data) throws IOException {  // returns storage path
        String path = String.format("/storage/%s/%s", objectId.substring(0, 2), objectId);
        try (FileOutputStream fos = new FileOutputStream(path)) {
            fos.write(data);
        }
        return path;
    }

    public byte[] get(String path) throws IOException {
        return Files.readAllBytes(Paths.get(path));
    }
}
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

---

## Related

**Patterns applied here**

- [Composite Pattern](../../03-design-patterns/02-structural/composite-pattern.md)
- [Facade Pattern](../../03-design-patterns/02-structural/facade-pattern.md)
- [Strategy Pattern](../../03-design-patterns/03-behavioral/strategy-pattern.md)

**SOLID focus**: [Interface Segregation](../../02-solid-principles/04-interface-segregation.md) · [Dependency Inversion](../../02-solid-principles/05-dependency-inversion.md)

**Concurrency**: [Concurrency Patterns](../../04-concurrency/concurrency-patterns.md) · [Futures & Async Patterns](../../04-concurrency/futures-async-patterns.md)

**Practice next**

- [Design Version Control](29-design-version-control.md)
- [Design an LRU Cache](../02-frequent-problems/13-design-lru-cache.md)

Version control layers history over the same object store.

**Frameworks**: [LLD Template](../../../07-interview-templates/01-frameworks/02-lld-template.md) · [UML Diagrams](../../uml-diagrams.md) · [OOP Four Pillars](../../01-oop-fundamentals/four-pillars.md)
