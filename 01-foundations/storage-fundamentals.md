---
module: 01-foundations
status: unread
tags: [01-foundations, system-design, foundations]
---
# Storage Fundamentals

> Physical storage hierarchy from CPU cache to cloud object storage — the layer every database, filesystem, and cache sits on top of. Understanding IOPS, throughput, and latency numbers at each tier separates engineers who guess from engineers who calculate.

---

## Topic Mindmap

```
Storage Fundamentals
├── Why It Exists
│   ├── Problem → data must outlive the process that creates it
│   ├── Physical constraint → RAM is volatile, fast; disk is durable, slow
│   └── What breaks without it → every restart loses all state
├── Storage Hierarchy (L1 → Network)
│   ├── L1/L2/L3 Cache → ns latency, MB capacity, CPU-managed
│   ├── RAM (DRAM) → ~100ns, GBs, volatile
│   ├── NVMe SSD → ~100µs, TBs, persistent
│   ├── SATA SSD → ~200µs, TBs, persistent, cheaper
│   ├── HDD → ~5ms seek, TBs, high throughput sequential
│   └── Network Storage (S3, NFS) → ~1–100ms, unlimited, shared
├── Storage Types
│   ├── Block Storage → raw sectors, OS formats as filesystem
│   ├── File Storage → POSIX filesystem, NFS, SMB
│   └── Object Storage → flat namespace, HTTP API, no random writes
├── Key Metrics
│   ├── IOPS → random 4KB reads/writes per second
│   ├── Throughput → MB/s sustained sequential
│   └── Latency → time to first byte
├── Trade-offs
│   ├── Fast vs Durable → RAM vs NVMe
│   ├── Random vs Sequential → HDD hates random, NVMe handles both
│   └── Cost → NVMe 10× HDD, RAM 100× NVMe per GB
├── Failure Modes
│   ├── Write cliff → SSD slows 10× when NAND buffer full
│   ├── HDD head crash → physical platters destroyed
│   └── Storage bottleneck → CPU waits on I/O (IO-bound vs CPU-bound)
└── Interview Angles
    ├── Amazon → "why is DynamoDB faster than RDS for certain workloads?"
    └── Google → "how do you choose between Colossus and Spanner storage?"
```

---

## What Breaks Without This?

A team designs a "fast" distributed database, choosing HDD nodes because they are cheap. At 10,000 concurrent writes each updating random 4KB blocks, HDD delivers ~200 IOPS per drive — the system saturates at a fraction of capacity. Migrating to NVMe (500,000 IOPS) would have solved the problem entirely. Without understanding the storage tier, engineers blame the wrong layer (network, CPU, application code) and waste weeks debugging a hardware constraint.

## Physical Constraint

Every storage medium has an irreducible physical cost:
- **HDD**: a mechanical arm must move to the right track (seek time ~3–10ms), then wait for the platter to rotate (rotational latency ~2–4ms). Sequential reads are fast (200 MB/s) because the head stays in place. Random reads are 100× slower than sequential.
- **NAND Flash (SSD)**: no moving parts; reads are fast (~100µs). Writes require erasing an entire block (128KB–4MB) before writing, then remapping via the FTL (Flash Translation Layer). Write amplification degrades performance and lifetime.
- **RAM**: capacitor-based DRAM, refreshed every ~64ms. Fast (~100ns, ~50 GB/s bandwidth) but volatile — power loss = data loss.

## Minimal Solution

Single server, local SSD. Works for one machine. Breaks when: data exceeds one machine's disk, or one machine fails and takes all data with it.

## Production Generalization

Tiered storage — hot data on NVMe (or RAM with persistence), warm data on SATA SSD, cold data on HDD or object storage. Distributed across nodes with replication. Each tier matched to the access pattern (random vs sequential, latency-sensitive vs throughput-sensitive).

---

## 1. Storage Hierarchy: Numbers You Must Know

| Tier | Latency | IOPS (random 4KB) | Throughput (seq) | Capacity | Volatile? |
|---|---|---|---|---|---|
| **L1 Cache** | 0.5 ns | N/A (cache lines) | ~2 TB/s | 32–64 KB | Yes |
| **L2 Cache** | 5 ns | N/A | ~1 TB/s | 256 KB–4 MB | Yes |
| **L3 Cache** | 30–40 ns | N/A | ~300 GB/s | 8–64 MB | Yes |
| **RAM (DRAM)** | 100 ns | ~1M+ | 50–100 GB/s | 8 GB–12 TB | Yes |
| **NVMe SSD** | 70–150 µs | 500K–1M+ | 3–7 GB/s | 0.5–8 TB | No |
| **SATA SSD** | 150–300 µs | 80K–100K | 500 MB/s | 0.25–4 TB | No |
| **HDD (7200 RPM)** | 3–10 ms seek | 150–250 | 150–250 MB/s | 1–20 TB | No |
| **Network SSD (EBS gp3)** | 1–3 ms | 16K–64K | 1 GB/s | 1–64 TB | No |
| **Object Storage (S3)** | 10–100 ms | ~3,500 PUT/5,500 GET per prefix | 5–10 GB/s (multi-part) | Unlimited | No |

**Memory trick**: Each tier is roughly 10× slower and 10× cheaper per GB than the tier above it. RAM is volatile; everything below NVMe is persistent.

### Latency in absolute terms

```
L1 cache reference:      0.5 ns
L2 cache reference:      5 ns
Branch misprediction:    5 ns
Mutex lock/unlock:       25 ns
L3 cache reference:      40 ns
Main memory (DRAM):      100 ns
NVMe random read:        100–150 µs      (1,000× RAM)
SATA SSD random read:    200–300 µs
HDD seek + rotational:   5–10 ms         (50,000× RAM)
Round trip US → EU:      150 ms
```

---

## 2. Storage Types

### Block Storage

Raw, unformatted storage presented as a logical disk. The OS formats it with a filesystem (ext4, XFS, NTFS). Applications read and write fixed-size blocks (512B, 4KB).

- **Examples**: AWS EBS, GCP Persistent Disk, Azure Managed Disk, a physical NVMe drive
- **Use case**: Databases (PostgreSQL, MySQL), VMs, anything needing a POSIX filesystem
- **Characteristics**: Low latency, high IOPS, random access, single-host attachment (usually)
- **Not good for**: Sharing across multiple hosts simultaneously (block device is exclusive)

```
Application → Filesystem (ext4/XFS) → Block Device Driver → Physical disk
                                     ↑
                         OS manages sectors/blocks
```

### File Storage (Network Attached Storage)

A filesystem accessible over a network protocol (NFS, SMB/CIFS). Multiple clients mount the same filesystem concurrently. Familiar POSIX semantics: open/read/write/close.

- **Examples**: AWS EFS, Google Filestore, NFS servers, Ceph FS
- **Use case**: Shared config files, ML training data shared across training nodes, legacy apps
- **Characteristics**: Easy to share, POSIX compatible, moderate latency (~1–5ms network overhead)
- **Not good for**: High-IOPS random workloads (network overhead kills latency), databases

### Object Storage

A flat namespace of objects (key → binary blob). No directories (prefixes simulate them). No random writes — entire object is overwritten atomically. HTTP-based API (GET, PUT, DELETE). Infinitely scalable, extremely durable (S3: 11 nines).

- **Examples**: AWS S3, GCS, Azure Blob Storage, MinIO (self-hosted)
- **Use case**: Images, videos, backups, data lake files (Parquet/ORC), ML model artifacts, static web content
- **Characteristics**: Unlimited scale, cheap (~$23/TB/month), high throughput for large objects, high latency for small objects
- **Not good for**: Small random reads (each GET has 10–100ms baseline), transactional workloads, frequent overwrites

| Dimension | Block | File | Object |
|---|---|---|---|
| Access | Bytes/blocks | Files/directories | Objects via HTTP |
| Sharing | Exclusive (usually) | Multi-host shared | Multi-host shared |
| Latency | Lowest | Medium | Highest |
| Throughput | High | Medium | Very high (for large objects) |
| Scalability | Limited by disk size | Limited by NFS server | Unlimited |
| Cost/TB | Highest | Medium | Lowest |
| Random writes | Yes | Yes | No (replace whole object) |
| Durability | Depends on RAID/replication | Depends | Very high (11 nines in S3) |

---

## 3. SSD Deep Dive: NAND Flash and Write Amplification

SSDs store data in NAND flash cells. Each cell stores 1 bit (SLC), 2 bits (MLC), 3 bits (TLC), or 4 bits (QLC). More bits per cell = cheaper, slower, less durable.

| Cell Type | Bits/Cell | Endurance (P/E cycles) | Speed | Cost |
|---|---|---|---|---|
| SLC | 1 | 50,000–100,000 | Fastest | Highest |
| MLC | 2 | 3,000–10,000 | Fast | High |
| TLC | 3 | 1,000–3,000 | Moderate | Low |
| QLC | 4 | 300–1,000 | Slowest | Lowest |

### Write Amplification

NAND cannot overwrite in place. To update one 4KB page, the SSD must:
1. Read the entire 128KB–4MB erase block into a buffer
2. Erase the block (all cells to 1)
3. Modify the target page in the buffer
4. Write the entire block back

**Write amplification factor (WAF)** = actual data written to NAND ÷ data written by host.

- Ideal WAF: 1.0 (every byte written by host writes 1 byte to NAND)
- Sequential writes WAF: ~1–2
- Random small writes WAF: 10–50 (each 4KB write may erase/rewrite 4MB)

The **Flash Translation Layer (FTL)** manages this with:
- **Wear leveling**: Distribute writes evenly across all cells to prevent premature cell death
- **TRIM**: OS tells SSD which blocks are no longer needed, so FTL can erase ahead of time
- **Over-provisioning**: 7–28% of raw NAND is reserved as a write buffer, hiding erase overhead

### The Write Cliff

SSDs maintain an internal DRAM write buffer (SLC cache). Writes go to SLC cache at full speed (3–5 GB/s). When the cache fills and background folding (SLC→TLC) can't keep up, writes slow to TLC speed (300–500 MB/s). Sustained heavy writes trigger this "write cliff."

**Database implication**: PostgreSQL and MySQL fsync their WAL/redo log. Many small fsyncs are brutal for TLC SSDs because each fsync drains the SLC cache. Enterprise NVMe SSDs with large DRAM buffers and power-loss protection capacitors (PLCs) are mandatory for database servers.

---

## 4. HDD: Sequential Wins, Random Kills

HDDs have spinning magnetic platters and a mechanical read/write head. Performance depends on:

- **Seek time**: Time to move head to correct track — 3–10ms average
- **Rotational latency**: Wait for the correct sector to rotate under the head — 0–8.3ms for 7200 RPM (average 4.2ms)
- **Transfer rate**: Once head is positioned, 150–250 MB/s sequential

**Random IOPS formula**:
```
Max random IOPS = 1 / (seek_time + rotational_latency + transfer_time)
               = 1 / (5ms + 4ms + 0.02ms)
               ≈ 111 IOPS for 4KB random reads
```

**Implication**: MySQL on HDD doing random index lookups hits a hard wall at ~200 IOPS per disk. SSDs providing 80,000 IOPS eliminate this bottleneck entirely. HDDs remain cost-effective only for sequential workloads (video streaming, backup, analytics scan-heavy workloads).

---

## 5. NVMe: The Architecture Advantage

NVMe (Non-Volatile Memory Express) is not just a faster interface — it's a fundamentally different protocol designed for flash.

**AHCI (old SATA protocol)**: 1 command queue, 32 commands deep. Designed for HDDs with single mechanical head.

**NVMe**: 65,535 queues × 65,536 commands each. Parallelizes I/O across all NAND die simultaneously.

```
SATA SSD:  1 queue depth → bottleneck at >32 concurrent I/Os
NVMe SSD:  65,535 queues → millions of concurrent I/Os, NAND parallelism
```

| Metric | SATA SSD (AHCI) | NVMe SSD (PCIe 4.0) |
|---|---|---|
| Sequential read | 550 MB/s | 7,000 MB/s |
| Sequential write | 520 MB/s | 6,500 MB/s |
| Random read IOPS | 100,000 | 1,000,000 |
| Random write IOPS | 90,000 | 700,000 |
| Latency (4KB read) | 150–300 µs | 70–100 µs |

---

## 6. Storage Hierarchy in System Design

### Tiered Storage Pattern

Match data temperature to storage tier:

```
Hot data   → RAM / NVMe          (active users, live sessions, recent orders)
Warm data  → SATA SSD / NVMe     (last 30 days of orders, recent posts)
Cold data  → HDD / Object Store  (historical records, archived logs, raw events)
```

**Real examples**:
- **Cassandra**: uses SSTable on SSD for hot data, compacts to HDD or S3 for cold
- **Kafka**: topic partitions on local NVMe for active segments; older segments tiered to S3 (Kafka Tiered Storage)
- **Elasticsearch**: hot shards on NVMe, warm/cold shards on HDD, frozen shards on object storage
- **Snowflake**: columnar files in S3 (object storage), compute nodes cache hot data in local NVMe

### I/O Bound vs CPU Bound

```java
// Diagnose whether a service is I/O bound:
// CPU usage low but latency high → likely I/O bound
// Check: iostat -x 1 (Linux) — if %util > 80% on a disk, that disk is the bottleneck

// Common fix: move random-read-heavy workload from HDD to SSD
// Or: ensure buffer pool / page cache is large enough to serve reads from RAM

// PostgreSQL: shared_buffers controls how much RAM is used as buffer pool
// MySQL InnoDB: innodb_buffer_pool_size — set to 70–80% of RAM
```

### Why Databases Use Write-Ahead Logging (WAL/redo log)

Random writes to a B-tree index are expensive (one page write may require multiple disk I/Os due to page splits). WAL converts random writes into sequential writes:

```
Without WAL: each UPDATE modifies a random page → 1 random write per update
With WAL:    each UPDATE appends to WAL → 1 sequential write per update
             Background process asynchronously applies changes to data pages
```

Sequential writes on HDD: 150 MB/s. Random writes on HDD: ~1 MB/s (250 IOPS × 4KB). **WAL makes HDD viable for databases at all.**

---

## 7. Object Storage Architecture

S3 and equivalent services achieve 11 nines durability by:

1. **Erasure coding**: Split object into N+K chunks. Store N data chunks + K parity chunks across N+K drives/nodes. Can recover from K simultaneous failures.
2. **Geographic replication**: Replicate across ≥3 AZs automatically (S3 Standard).
3. **Versioning**: Each overwrite creates a new version; delete is a soft delete.

**Throughput scaling trick**: S3 rate-limits per prefix. To scale beyond 3,500 PUT/5,500 GET per second, shard across multiple prefixes:
```
# BAD: all objects under one prefix
s3://bucket/images/image1.jpg
s3://bucket/images/image2.jpg

# GOOD: hash-prefixed to distribute across S3 partitions
s3://bucket/a3f/image1.jpg
s3://bucket/b7c/image2.jpg
```

---

## 8. Choosing Storage: Decision Framework

```
Q1: Does data need to outlive the process?
  No → RAM (cache, session state with TTL backup)
  Yes → persistent storage

Q2: What's the access pattern?
  Random small reads/writes → Block (NVMe for IOPS, SATA SSD for cost)
  Sequential large reads/writes → HDD or object storage
  Shared across many hosts → File storage (NFS/EFS) or Object storage

Q3: What's the latency requirement?
  < 1ms → NVMe or RAM
  < 10ms → SATA SSD or fast network block storage
  > 10ms acceptable → HDD, object storage

Q4: What's the scale?
  < 10 TB, single host → local NVMe
  10 TB–1 PB, multi-host → distributed block (Ceph, SAN) or NAS
  > 1 PB → object storage

Q5: What's the cost tolerance?
  Low → HDD + object storage (cold tier)
  High → NVMe + RAM
```

---

## Quick Revision

- HDD: 150–250 IOPS random, 150–250 MB/s sequential, 5–10ms seek latency
- SATA SSD: 80–100K IOPS random, 500 MB/s sequential, 200µs latency
- NVMe SSD: 500K–1M IOPS random, 3–7 GB/s sequential, 70–100µs latency
- RAM: ~1M+ IOPS, 50–100 GB/s bandwidth, 100ns latency, volatile
- Block storage: raw sectors, OS formats, low latency, exclusive mount
- File storage: POSIX filesystem over network, shareable, moderate latency
- Object storage: flat HTTP API, unlimited scale, no random writes, highest latency
- Write amplification on TLC NAND: 10–50× for random small writes
- WAL converts random writes → sequential writes (critical for HDD viability)
- S3 durability: 11 nines (erasure coding + 3-AZ replication)
- Each storage tier is ~10× slower and ~10× cheaper per GB than the tier above

---

## Interview Questions Asked

1. **Google**: "Our logging service writes 500MB/s of small (1KB) log records. Should we store them on SSDs or HDDs? What's your IOPS math?" (Sequential workload → HDD is fine and 10× cheaper)
2. **Amazon**: "A customer says their DynamoDB reads are slow but their RDS queries are fast for the same data. How would you explain this in terms of storage architecture?" (DynamoDB uses SSD + in-memory caching; answer depends on access pattern and index structure)
3. **Meta**: "Design a cold storage system for 10 years of user photos that costs as little as possible. What storage type and what's the durability strategy?" (Object storage + erasure coding + Glacier-tier)
4. **Netflix**: "We need to store 100 PB of video. Walk me through the storage tier decision." (Object storage for master files, CDN edge cache on NVMe for hot content)
5. **Stripe**: "Our database is I/O bound. CPU is at 10% but latency spikes to 500ms. How do you diagnose and fix this?" (iostat, buffer pool size, index access pattern, upgrade HDD → NVMe)
