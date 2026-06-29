---
module: 02-building-blocks
topic: Blob Storage
status: unread
tags: [02-building-blocks, system-design, blob-storage, s3]
---
# Blob Storage (S3 / Object Storage)

> **You need to store 10 billion images — each 200KB. SQL columns can't hold binary blobs at this scale; filesystem on a single machine runs out of inodes. Where do you put them, and how do the rest of your services access them?**

---

## File Mindmap

```
Blob Storage (S3 / Object Storage)
├── What It Solves
│   ├── vs Filesystem → single machine inode limit; no replication; no global URL
│   ├── vs SQL BLOB column → row locks on write; backup includes binaries; ~1GB max
│   └── Use case → user uploads, video, audio, backups, ML datasets, log archives
├── Key Concepts
│   ├── Bucket → namespace for objects; globally unique name
│   ├── Object key → full "path" within bucket (e.g. user/123/2024-01/photo.jpg)
│   ├── Metadata → content-type, custom headers stored alongside object
│   ├── Presigned URL → time-limited signed URL for upload or download; no credentials exposed
│   └── Multipart upload → for files > 100MB; parallel parts, resumable
├── Access Patterns
│   ├── Direct upload (via server) → server becomes bottleneck; doubles bandwidth cost
│   ├── Presigned URL upload → client uploads directly to S3; server only signs
│   └── CDN in front → CloudFront caches GET responses at edge; S3 as origin
├── Lifecycle Policies
│   ├── Transition rule → move to Glacier/cold storage after N days
│   └── Expiry rule → delete object after N days
├── Consistency Model
│   └── S3 strongly consistent (since Dec 2020) → read-after-write for new objects and overwrites
└── Failure Handling
    ├── Cross-region replication → async copy to secondary region; for DR
    └── Versioning → retains all versions; enables rollback; protects against accidental delete
```

---

## Why Not Store Files in a Database?

- **SQL BLOB column**: practical limit ~1GB per row; write locks the row during upload; backup size balloons because binaries are included; no built-in CDN or presigned URL primitive
- **S3 object**: no size limit per object (up to 5TB); parallel writes to any key; backups are independent of your DB; built-in lifecycle, versioning, replication
- **Operational cost**: DB instances are expensive per GB; S3 is ~$0.023/GB/month (standard tier)
- **Serving**: DB cannot stream a 4GB video to a client; S3 supports range requests natively (used by video players for seek)

---

## Direct Upload vs Presigned URL

**Direct upload through your server** means the client sends the file to your API, which then forwards it to S3. Your server becomes a bandwidth bottleneck, doubles egress cost, and adds latency.

**Presigned URL** pattern: server signs a time-limited S3 URL and returns it to the client; client uploads directly to S3. Server only handles metadata.

```python
import boto3

s3 = boto3.client("s3")

def get_upload_url(bucket: str, key: str, expires_in: int = 300) -> str:
    return s3.generate_presigned_url(
        "put_object",
        Params={"Bucket": bucket, "Key": key, "ContentType": "image/jpeg"},
        ExpiresIn=expires_in,
    )

def get_download_url(bucket: str, key: str, expires_in: int = 3600) -> str:
    return s3.generate_presigned_url(
        "get_object",
        Params={"Bucket": bucket, "Key": key},
        ExpiresIn=expires_in,
    )
```

Flow: `POST /media/upload-url` → server returns presigned PUT URL → client PUTs file directly to S3 → client calls `POST /media/confirm` with the key → server stores key in DB.

---

## Multipart Upload

Use when file > 100MB. Allows parallel part uploads and resumability. Each part is 5MB–5GB.

```python
import boto3

s3 = boto3.client("s3")

def multipart_upload(bucket: str, key: str, file_path: str) -> None:
    mpu = s3.create_multipart_upload(Bucket=bucket, Key=key)
    upload_id = mpu["UploadId"]
    parts = []
    with open(file_path, "rb") as f:
        part_number = 1
        while chunk := f.read(5 * 1024 * 1024):  # 5MB chunks
            resp = s3.upload_part(
                Bucket=bucket, Key=key, PartNumber=part_number,
                UploadId=upload_id, Body=chunk,
            )
            parts.append({"PartNumber": part_number, "ETag": resp["ETag"]})
            part_number += 1
    s3.complete_multipart_upload(
        Bucket=bucket, Key=key, UploadId=upload_id,
        MultipartUpload={"Parts": parts},
    )
```

---

## Lifecycle Policies

```python
lifecycle_config = {
    "Rules": [
        {
            "ID": "archive-and-expire",
            "Status": "Enabled",
            "Filter": {"Prefix": "uploads/"},
            "Transitions": [
                {"Days": 90, "StorageClass": "GLACIER"},
            ],
            "Expiration": {"Days": 365},
        }
    ]
}

s3.put_bucket_lifecycle_configuration(
    Bucket="my-bucket",
    LifecycleConfiguration=lifecycle_config,
)
```

- Transition to Glacier after 90 days → 10x cheaper storage for cold data
- Expire after 365 days → automatic deletion; no application code needed

---

## Key Naming Strategy

**Bad**: sequential keys like `000001.jpg`, `000002.jpg` → S3 partitions by key prefix; sequential keys hammer one partition → hot-spot throttling.

**Good**: prefix with high-cardinality segment first:

```
user_id/date/uuid.jpg
e.g.  u-8f3a/2024-01-15/c7d2e1b0-....jpg
```

S3 distributes load across partitions based on key prefix. Random or hash-based prefixes ensure even distribution.

**CDN integration**: put CloudFront in front of S3. CloudFront caches GET responses at edge PoPs. S3 stays private (no public ACLs). CloudFront uses an Origin Access Control (OAC) to authenticate to S3. Clients never get raw S3 URLs — they get CloudFront URLs or short-lived presigned URLs.

---

## When to Use Blob Storage in HLD Interviews

- Any user-uploaded content: photos, videos, documents, avatars
- Audio files, podcast episodes, recorded meetings
- Backups (DB snapshots, transaction logs shipped to S3)
- Log archives (compress + ship to S3 after 24h, query with Athena)
- ML training data, model checkpoints
- Static assets that would otherwise bloat your DB

Signal in the prompt: "users upload", "store images/videos", "serve files globally", "petabyte scale storage".

---

## Common Interview Follow-Ups

**"How do you handle large file uploads from mobile clients?"**
Generate a presigned multipart upload URL server-side. Client uploads parts directly to S3. If connection drops, client resumes from last completed part using the upload ID.

**"What if two users upload the same file?"**
Content-addressed storage: hash the file (SHA-256), use the hash as the S3 key. Before upload, check if the key already exists. If yes, store only the DB reference — no re-upload. Deduplication is automatic.

**"How do you serve private files?"**
Generate presigned GET URLs with short TTL (e.g. 60 seconds). Never expose public S3 URLs or bucket ACLs. For high-traffic private content, use CloudFront signed URLs (longer-lived, cached at edge) with a signing key pair rotated periodically.
