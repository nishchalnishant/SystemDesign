# Object Storage in System Design Interviews

> **Source**: [Object Storage in System Design Interviews w/ Ex-Meta Staff Engineer](https://www.youtube.com/watch?v=1HOVtQ-_fcE&list=PL5q3E8eRUieVFeK1oLahJ8KONkAxDpqk2&index=7)

---

## What is Object Storage?

**Object storage** stores data as discrete units called **objects**, each containing the data itself, metadata, and a unique identifier. Unlike file systems (hierarchical) or block storage (fixed-size blocks), object storage uses a **flat namespace**.

---

## Object Storage vs File Storage vs Block Storage

| Feature | Object Storage | File Storage | Block Storage |
|---|---|---|---|
| **Structure** | Flat namespace | Hierarchical (directories) | Fixed-size blocks |
| **Access** | HTTP/REST API | NFS/SMB protocols | iSCSI/FC protocols |
| **Metadata** | Rich, custom metadata | Limited (file attributes) | None |
| **Scalability** | Virtually unlimited | Limited by file system | Limited by volume |
| **Use Case** | Media, backups, data lakes | Shared file access, NAS | Databases, OS disks |
| **Performance** | Higher latency | Moderate | Lowest latency |
| **Examples** | S3, GCS, Azure Blob | EFS, FSx | EBS, local SSD |

---

## Key Concepts

### Objects
- **Data**: The actual content (image, video, document, log file)
- **Metadata**: Key-value pairs describing the object (content-type, creation date, custom tags)
- **Key**: Unique identifier within a bucket (acts like a path)

### Buckets
- Top-level container for objects
- Globally unique name (in services like S3)
- Access policies and configurations applied at bucket level

### Operations
```
PUT    /bucket/key    → Upload object
GET    /bucket/key    → Download object
DELETE /bucket/key    → Delete object
HEAD   /bucket/key    → Get metadata only
LIST   /bucket/       → List objects (with prefix filtering)
```

---

## When to Use Object Storage

| Scenario | Why Object Storage |
|---|---|
| **User-uploaded media** (images, videos) | Cheap, scalable, CDN integration |
| **Static website assets** | HTTP accessible, CDN friendly |
| **Backups and archives** | Durability (11 9s), tiered storage |
| **Data lakes** | Schema-on-read, massive scale |
| **Log storage** | Append-only, cheap at scale |
| **Machine learning datasets** | Large files, batch access |

---

## Amazon S3 (Most Common Example)

### Storage Classes
| Class | Use Case | Retrieval | Cost |
|---|---|---|---|
| **S3 Standard** | Frequently accessed | Instant | Highest |
| **S3 Intelligent-Tiering** | Varying access patterns | Instant | Auto-optimized |
| **S3 Standard-IA** | Infrequent access | Instant | Lower storage, higher retrieval |
| **S3 Glacier** | Archival (minutes-hours) | Minutes to hours | Very low |
| **S3 Glacier Deep Archive** | Long-term archive | 12-48 hours | Lowest |

### Durability & Availability
- **Durability**: 99.999999999% (11 nines) — designed to not lose data
- **Availability**: 99.99% — may have brief outages
- Data replicated across **≥3 Availability Zones**

---

## Design Patterns with Object Storage

### 1. Pre-signed URLs (Direct Upload/Download)
```
Client → Server: "I want to upload a photo"
Server → Client: Pre-signed PUT URL (valid for 15 min)
Client → S3:     Upload directly using pre-signed URL
Client → Server: "Upload complete, key = photos/abc123.jpg"
```
- **Benefit**: Server doesn't handle file transfer; reduces server load
- **Security**: URL expires; scoped to specific key and operation

### 2. CDN Integration
```
User → CDN (edge cache) → Object Storage (origin)
```
- CDN caches objects at edge locations close to users
- Dramatically reduces latency for frequently accessed objects
- Use **Cache-Control** headers for expiration

### 3. Thumbnail/Processing Pipeline
```
Upload → Object Storage → Event trigger → Lambda/Worker
                                            ↓
                                    Generate thumbnails
                                    Transcode video
                                    Extract metadata
                                            ↓
                                    Store processed versions
```
- Use event notifications (S3 events) to trigger processing
- Store multiple versions (original, thumbnail, compressed)

### 4. Reference Pattern (Large Objects)
```
Message Queue message: { "type": "video_process", "s3_key": "videos/raw/abc.mp4" }
```
- Don't put large objects in message queues or databases
- Store the object in S3, pass the **reference (key/URL)** in the message

---

## Object Naming/Key Strategy

```
{content_type}/{date}/{user_id}/{unique_id}.{extension}

photos/2024/01/15/user_123/550e8400-e29b-41d4-a716-446655440000.jpg
videos/raw/2024/01/user_456/abc123.mp4
videos/transcoded/2024/01/user_456/abc123_720p.mp4
```

### Best Practices
- Use **random prefixes** or UUIDs to avoid hot partitions
- Use **forward slashes** as logical separators (simulates folders)
- Include **content type** and **resolution** for media files
- Avoid sequential prefixes (like timestamps) at the start of keys

---

## Access Control

### Bucket Policies
```json
{
  "Effect": "Allow",
  "Principal": "*",
  "Action": "s3:GetObject",
  "Resource": "arn:aws:s3:::my-public-bucket/*"
}
```

### IAM Policies
- Service-level access control
- Grant specific services permission to read/write specific buckets

### Pre-signed URLs
- Time-limited, operation-specific access
- No IAM credentials needed by the client

### CORS Configuration
- Required for browser-based direct uploads
- Specify allowed origins, methods, headers

---

## Cost Optimization

1. **Lifecycle policies**: Automatically transition objects to cheaper storage tiers
   ```
   Day 0-30:  S3 Standard
   Day 30-90: S3 Standard-IA
   Day 90+:   S3 Glacier
   Day 365+:  Delete
   ```

2. **Multipart uploads**: For large files (>100MB), upload in parts for reliability
3. **Compression**: Compress before storing (gzip, zstd)
4. **Deduplication**: Hash-based deduplication to avoid storing duplicates

---

## Interview Tips

1. **Always use object storage for media** — don't store images/videos in a database
2. **Mention pre-signed URLs** for direct client uploads — shows scalability awareness
3. Discuss **CDN integration** for read-heavy media workloads
4. Mention **event-driven processing** (upload triggers transcoding/thumbnailing)
5. Use the **reference pattern** — store the object, pass the key/URL
6. Discuss **storage tiers** for cost optimization
7. Address **naming strategy** to avoid hot partitions
