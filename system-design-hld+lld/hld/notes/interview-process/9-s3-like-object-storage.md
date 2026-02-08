# #9 S3 like object storage

Below is a complete, time-boxed, interview-ready (1 hour) answer for designing an S3-like object storage system. It follows your preferred pattern: clarify → FR/NFR → APIs & data model → architecture & flows → deep dives (consistency, durability, multipart, lifecycle) → capacity math & scaling → ops/security → trade-offs & wrap-up. Use it as a script in an interview — speak the minutes noted.

***

## 0 – 5 min — Problem recap, scope & assumptions (set the stage)

<br>

Quickly restate and confirm scope so interviewer and you are aligned.

<br>

Goal: Design a scalable, durable, highly-available object storage service that exposes an S3-like API: bucket/object semantics, PUT/GET/DELETE, listing, multipart upload, object metadata, versioning, lifecycle rules, ACLs, presigned URLs, server-side encryption, and lifecycle transitions to colder tiers.

<br>

Primary goals:

* Exabyte-scale storage with durable guarantees (11-9s durability target).
* High throughput & low-latency for small-object metadata ops and large-object streaming.
* Multi-tenancy, strong security (encryption, ACLs), lifecycle management.
* Cheap cold storage tiering and lifecycle transitions.
* Data integrity and auditability.

<br>

Example assumptions (for BoE):

* 1 billion objects total, average object size 100 KB → \~100 TB raw.
* Peak requests: 50k reads/sec, 10k writes/sec (adjust to interviewer numbers).
* Retention policy: hot (30 days), cold (archive indefinite).
* SLA: API availability 99.99%; durability 11 9s for stored objects (design target).

***

## 5 – 15 min — Functional & Non-Functional Requirements

<br>

#### Functional Requirements (Must / Should / Nice)

<br>

Must

1. Bucket & object lifecycle: create/delete/list buckets; put/get/delete objects with metadata.
2. Multipart upload: multipart upload API for large objects with resumability.
3. Listing: list objects with pagination (prefix, delimiter), continuation tokens.
4. Versioning & object locking: optional bucket-level versioning and retention/lock policy (compliance).
5. Access control & auth: per-bucket/object ACLs, IAM, presigned URLs.
6. Data integrity: checksums (MD5/SHA), server-side verification.
7. Server-side encryption: SSE-S3/SSE-KMS options; client-side encryption support.
8. Lifecycle policies: transition objects to colder tiers, expiration.
9. Event notifications: push events for object create/delete to SNS/SQS/webhooks.
10. Metrics & audit logs: access logs, usage metrics, billing counters.

<br>

Should

* Cross-region replication (CRR) and cross-account replication.
* Object tagging, object-level metadata indexing.
* Soft-delete / object retention (WORM compliance).

<br>

Nice-to-have

* Strongly consistent read-after-write for new objects; configurable consistency modes.
* Native snapshot / object batch operations (copy, batch delete).
* Partial object reads (range reads), server-side copy within storage.

<br>

#### Non-Functional Requirements

* Durability: design for >99.999999999% (11 nines) durability via replication/erasure coding + checksums.
* Availability: 99.99% API uptime; regional failover for disaster.
* Scalability: scale to exabytes and millions of ops/sec.
* Performance: low-latency for GET/PUT metadata (<100 ms P95), streaming throughput high for large objects.
* Consistency: read-after-write for new objects preferred; explain chosen consistency model below.
* Cost-efficiency: tiered storage (hot/hot+warm/cold/archival).
* Security & Compliance: TLS, encryption at rest & transit, audit logs, IAM, GDPR features.
* Observability & Operability: monitoring, quotas, throttling, lifecycle automation.

***

## 15 – 25 min — APIs, object model & metadata

<br>

#### Core APIs (S3-like)

```
PUT    /{bucket}/{key}               -> upload object (single PUT)
GET    /{bucket}/{key}               -> download object (support Range)
DELETE /{bucket}/{key}               -> delete object (optionally versioned)
POST   /{bucket}?uploads             -> initiate multipart upload -> uploadId
PUT    /{bucket}/{key}?partNumber&uploadId -> upload part
POST   /{bucket}/{key}?uploadId&complete -> complete multipart upload
GET    /{bucket}?list-type=2&prefix=&continuation-token -> list objects (paged)
PUT    /{bucket}?versioning           -> enable/disable versioning
PUT    /{bucket}?lifecycle            -> set lifecycle rules
PUT    /{bucket}?cors                 -> CORS config
GET    /{bucket}/{key}?tagging        -> object tags
POST   /{bucket}/{key}?copy           -> server-side copy (same region)
```

#### Object model

* Bucket: namespace, region, owner, ACLs, versioning flag, lifecycle rules, quotas.
* Object: (bucket, key, version\_id) maps to object _manifest_ containing: size, storage class, checksum(s), creation\_ts, metadata (user-defined), tags, location pointers (chunk list), encryption metadata, retention flags.
* Chunk / Part: large objects are stored as parts/chunks; manifest points to chunk ids and offsets.
* Storage classes: HOT, WARM, COLD, ARCHIVE with price/latency trade-offs.
* Index structures: metadata index for quick key -> manifest lookup.

***

## 25 – 40 min — High-level architecture & data flow

```
Clients (SDK / REST)
     |
  API Gateway (auth, throttling, billing)
     |
  Frontend Servers (stateless)  <->  Metadata Service (strongly consistent)
     |                                    |
     |                                    +--> Metadata Store (sharded key-value DB / consensus-backed)
     |
  Data Path:
   - For PUT single: Frontend -> Ingest Node -> Chunk Store / DataNodes (write) -> Manifest persisted to Metadata Store -> ACK
   - For GET: Frontend reads manifest from Metadata -> stream chunks from DataNodes (S3-style GET)
   - Multipart: parts uploaded in parallel to Ingest / DataNodes; completion operation writes manifest atomically
     |
  Storage Layer:
   - DataNodes: store immutable blocks/segments on local SSD/HDD (or object store)
   - Erasure Coding / Replication layer: background EC encode & placement policy
   - Cold Tier: Tiering workers move objects per lifecycle to cheaper backends (object-store on tape / Glacier-like)
     |
  Background Systems:
   - Re-replication / Healing service (checksumming + repair)
   - Garbage Collector (reclaims deleted versions per retention)
   - Inventory / Compaction jobs
   - Metrics / Audit / Billing pipeline
```

Key design choices

* Separation of metadata & data: metadata (small, many ops) in low-latency replicated KV store; data in distributed blob store (DataNodes).
* Partitioning: metadata partitioned by hash(bucket + key) to distribute load; use consistent hashing to place metadata shards.
* Data placement: use placement policy by storage class and failure domain (rack/zone/region).
* Durability approach: erasure coding for large objects to reduce storage overhead; replication (e.g., 3x) for small objects or hot tier for faster recovery.
* Indexing for List: maintain per-bucket object index (sorted by key) partitioned by key prefix to enable efficient pagination (avoid scanning global index).

***

## 40 – 50 min — Consistency, durability, multipart, lifecycle & GC (deep dive)

<br>

#### Consistency model

* Read-after-write for new objects (strong): when a PUT creates a new key, subsequent GET should observe the object.
* Overwrite / Delete semantics: either offer _read-after-write for overwrite_ (strong) or _eventual consistency_ for updates depending on complexity — propose configurable: strong for same-region single-leader metadata writes (use consensus), eventual for cross-region replication to reduce latency.
* Implementation: metadata writes use a consensus protocol (Raft/Paxos) per metadata shard to ensure linearizable updates; manifest pointer switch is atomic.

<br>

#### Put / Multipart upload correctness

* Single PUT: upload to ingest node which streams object into temporary parts on DataNodes (replication or EC stripes). After successful data write + checksum verification, metadata manifest is written via consensus to metadata store — commit point. If commit fails, background job cleans partial data after TTL.
* Multipart: each part uploaded independently; parts stored with temporary IDs. On CompleteMultipartUpload, ingest node validates part checksums and writes final manifest in metadata store atomically. Only after manifest commit is object visible. This ensures no partial objects are visible.

<br>

#### Durability & Encoding

* Small objects: replicate (3x) on separate racks/az for low-latency reads.
* Large objects: erasure code (e.g., 10+2 or 12+3) across DataNodes to save storage while guaranteeing durability.
* Checksums & Integrity: each chunk has checksum; manifest stores combined checksum (e.g., MD5/ETag). Background scrubbing jobs verify checksums and trigger re-replication/repair.

<br>

#### Deletion, versioning & garbage collection

* Versioning: if enabled, DELETE marks a new delete-marker version; older versions retained per lifecycle. Versioned objects can be restored.
* Garbage Collection: when objects/versions are eligible for garbage collection (per lifecycle or retention), mark them for deletion; GC worker removes manifests and schedules chunk reclamation when no manifests reference chunks (reference counting or manifest-based compaction). Use tombstones and multi-phase deletion to ensure consistency under crashes.

<br>

#### Lifecycle & tiering

* Lifecycle rules evaluated by background job: transition objects to WARM -> COLD -> ARCHIVE. Transition means copying (or moving) data to new storage backends and updating manifest atomically (new location pointers). For archival, store in cheap object store (longer retrieval time, e.g., hours).

<br>

#### List & pagination

* List API: return max-keys items and a continuation token. To scale lists across large buckets, maintain bucket index partitions (e.g., shard by prefix) and use continuation tokens that encode shard + offset. Avoid scanning entire index—use lexicographic ranges.

***

## 50 – 55 min — Scaling, capacity planning & back-of-the-envelope math

<br>

#### Example BoE Sizing (sample numbers — adapt to interviewer)

<br>

Assume:

* 1 billion objects, avg 100 KB → 100 TB raw object bytes.
* Hot tier replication 3x for small objects; assume 20% of objects in hot tier (20 TB) replicated 3x = 60 TB. Remaining 80 TB in erasure-coded warm tier with 1.33x overhead → \~106.6 TB. Total raw storage \~166.6 TB plus metadata overhead.

<br>

Storage math

* Hot objects replicated: 20 TB × 3 = 60 TB
* Warm objects EC: 80 TB × 1.33 ≈ 106.6 TB
* Total ≈ 166.6 TB

<br>

Requests

* Peak reads 50k/sec, writes 10k/sec → design frontends and metadata shards to handle 60k ops/sec.
* Metadata store: handle \~60k strongly-consistent ops/sec; shard across many Raft groups. Use leader placement and client-side partitioning (hash) to route requests to correct shard.

<br>

Network & throughput

* Large-object streaming capacity planned in DataNodes: e.g., if average GET transfers 1 MB and 50k/s reads → 50 GB/s (!) unrealistic—adjust to real scales. Show you will compute real numbers during interview.

<br>

Nodes

* DataNodes: choose servers with high-capacity disks (e.g., 24 × 16 TB) and sufficient network (100 Gbps) per rack. Number of DataNodes = total required storage / usable per node.
* Metadata cluster: multiple smaller instances per Raft shard; scale number of shards to distribute load.

<br>

Repair & scrubbing

* Background repair bandwidth provisioned as % of network to avoid interfering with client traffic.

***

## 55 – 58 min — Operations, security & monitoring

<br>

#### Monitoring & observability

* Key metrics: ops/sec (GET/PUT/DELETE), latencies P50/P95/P99, data node disk/IO utilization, EC/replication backlog, checksum failures, GC backlog, per-bucket quotas, access logs for auditing.
* Tracing: end-to-end request traces for slow PUT/GET.
* Alerts: high checksum failure rate, EC repair lag, metadata leader unavailability, GC pile-up.

<br>

#### Security & compliance

* Auth & Authorization: IAM (per-bucket policies), ACLs, presigned URLs for temporary access.
* Encryption: SSE-S3 (service-managed keys) and SSE-KMS (customer-managed) support. Client-side encryption allowed. Keys in KMS with audit trail.
* Network: TLS in transit, VPC endpoints for private connectivity.
* Audit logs: write access logs to immutable storage for compliance.
* GDPR: implement object deletion & metadata purge; implement retention/litigation hold.

<br>

#### Operational practices

* Rolling upgrades, leader migration, safe rebalancing tooling, dry-run lifecycle simulation, capacity forecasting, tenant throttles and quotas.

***

## 58 – 60 min — Trade-offs, evolution & summary (wrap-up)

<br>

#### Key trade-offs

* Replication vs Erasure Coding: replication gives low-latency reads and simpler recovery but higher storage cost; EC is storage-efficient at cost of compute and higher repair bandwidth. Use hybrid: replicate small/hot objects, EC for large/cold objects.
* Strong vs Eventual Consistency: Strong consistency (consensus per metadata shard) simplifies correctness but increases latency and reduces cross-region flexibility. Offer configurable modes: strong within region, eventual across regions with CRR.
* Metadata store choice: A strongly-consistent, sharded Raft-backed KV store provides linearizability for manifests; but managing many Raft groups increases complexity. Alternatives: single-log systems (like Kafka-style) or use a managed metadata DB.

<br>

#### Evolution path

1. MVP: single-region with metadata backed by a scalable KV store + DataNodes with replication, basic APIs, multipart, lifecycle.
2. Phase 2: add erasure coding for warm/cold tier, cross-region replication, versioning, stronger lifecycle rules.
3. Phase 3: implement object tagging indexing, advanced lifecycle analytics, cold archival integration (tape-like), multi-region active-active capability, and compatibility layers (S3 API binding).

<br>

#### One-line summary

<br>

Build a two-layer system: a scalable, consensus-backed metadata plane for lookups and atomic updates, and a distributed blob store plane (DataNodes) for storing chunks with a mix of replication and erasure coding — expose S3-like APIs with multipart upload, lifecycle management, versioning, and strong durability, while optimizing for cost with tiering and efficient GC/repair.

***

If you want, I can now:

* convert this into a 5-slide interview deck (one slide per major time block), or
* produce a sequence diagram for PUT (single and multipart) and GET flows, or
* run a detailed capacity calculator if you give me expected objects/day, avg size, retention days, and replication/EC policy.

<br>

