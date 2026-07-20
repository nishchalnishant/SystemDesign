# Memory & Storage Systems

> **Source**: [10+ Key Memory & Storage Systems: Crash Course System Design #5](https://www.youtube.com/playlist?list=PLCRMIe5FDPsd0gVs500xeOewfySTsmEjf) — Video #1

---

## Storage Hierarchy (Speed vs Cost)

```
Fastest ←──────────────────────────────→ Slowest
Cheapest ←─────────────────────────────→ Most Expensive

CPU Registers → L1 Cache → L2 Cache → L3 Cache → RAM → SSD → HDD → Tape
   ~1 ns         ~1 ns      ~4 ns      ~10 ns    ~100ns ~100μs ~10ms  ~s
```

---

## Key Storage Systems

### 1. RAM (Random Access Memory)
- Volatile (data lost on power off)
- ~100 ns access time
- Used for: application memory, caching (Redis, Memcached)

### 2. SSD (Solid State Drive)
- Non-volatile, no moving parts
- ~100 μs random read
- Used for: databases, OS drives, hot storage

### 3. HDD (Hard Disk Drive)
- Non-volatile, mechanical spinning disks
- ~10 ms seek time
- Used for: bulk storage, backups, cold data

### 4. Object Storage (S3, GCS)
- Distributed, accessed via HTTP/REST
- Higher latency (~50-100ms first byte)
- Used for: media files, backups, data lakes
- Virtually unlimited capacity

---

## Database Storage Engines

### B-Tree Based (Read-optimized)
- Used by: PostgreSQL, MySQL (InnoDB)
- Good for read-heavy workloads
- In-place updates

### LSM-Tree Based (Write-optimized)
- Used by: Cassandra, RocksDB, LevelDB
- Good for write-heavy workloads
- Append-only, compaction in background

---

## In-Memory Databases
- **Redis**: Key-value, rich data structures, persistence optional
- **Memcached**: Simple key-value cache, no persistence
- **VoltDB**: In-memory relational database

---

## Distributed Storage
- **HDFS**: Hadoop Distributed File System, batch processing
- **S3**: Object storage, serverless, pay-per-use
- **Ceph**: Unified storage (block, file, object)

---

## Key Takeaways
- Choose storage based on **access patterns** and **latency requirements**
- **Hot data** → RAM/SSD, **Warm data** → SSD, **Cold data** → HDD/Object Storage
- Database storage engine choice impacts read/write performance dramatically
