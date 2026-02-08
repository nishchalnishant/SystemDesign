# Design AWS S3 (Object Storage)

> **Difficulty**: Hard  
> **Topics**: Distributed Systems, Blob Storage, Metadata Management, Consistent Hashing  
> **Features**: Put, Get, Delete, Buckets.

## Problem Statement

Design a simplified Object Storage Service.
- **Entities**: Buckets (Containers), Objects (Files).
- **Constraints**: Flat structure (Key-Value), Immutable objects (put overwrites).

## Architecture: Metadata vs Data

Decouple metadata from actual blob storage.

1.  **Metadata Store**: DB (DynamoDB/Cassandra) holding `{Key: "vacation.jpg", Size: 2MB, StoragePath: "/disk1/block_99"}`.
2.  **Blob Store**: Physical storage engine (HDD/SSD).

## Implementation

### Storage Strategy (Blob Store)

```python
import uuid
import os

class StorageBackend:
    def save(self, data) -> str:
        # Content Addressable Storage (CAS) or UUID
        path_id = str(uuid.uuid4())
        with open(f"./data/{path_id}", "wb") as f:
            f.write(data)
        return path_id

    def load(self, path_id) -> bytes:
        with open(f"./data/{path_id}", "rb") as f:
            return f.read()
```

### Metadata Service

```python
class S3Object:
    def __init__(self, key, size, storage_path):
        self.key = key
        self.size = size
        self.storage_path = storage_path

class Bucket:
    def __init__(self, name):
        self.name = name
        self.objects = {} # Key -> S3Object

    def put_metadata(self, obj):
        self.objects[obj.key] = obj

    def get_metadata(self, key):
        return self.objects.get(key)
```

### S3 Service Facade

```python
class S3Service:
    def __init__(self, storage):
        self.storage = storage
        self.buckets = {}

    def create_bucket(self, name):
        self.buckets[name] = Bucket(name)

    def put_object(self, bucket_name, key, data):
        bucket = self.buckets.get(bucket_name)
        if not bucket: raise ValueError("No bucket")

        # 1. Save Bytes
        phys_path = self.storage.save(data)
        
        # 2. Save Metadata
        obj = S3Object(key, len(data), phys_path)
        bucket.put_metadata(obj)

    def get_object(self, bucket_name, key):
        bucket = self.buckets.get(bucket_name)
        meta = bucket.get_metadata(key)
        if not meta: return None
        
        return self.storage.load(meta.storage_path)
```

## Key Design Challenges

1.  **Large Files (Multipart Upload)**:
    *   Break file into chunks. `initiate()`, `upload_part()`, `complete()`.
    *   Metadata stores list of chunk IDs.
2.  **Folder Illusion**:
    *   S3 is flat. "folders" are just prefixes in the key string.
    *   Renaming a "folder" is $O(N)$ (copy + delete).
3.  **Versioning**:
    *   Change Metadata Store to map `Key -> List[Object]`.
