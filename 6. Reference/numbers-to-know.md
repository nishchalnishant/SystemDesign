# Numbers Every Engineer Should Know

> **Critical reference for system design interviews - memorize these!**

## Latency Numbers (2024)

### Memory & Storage Hierarchy

| Operation | Latency | Notes |
|-----------|---------|-------|
| **L1 cache reference** | 0.5 ns | 1 CPU cycle |
| **Branch mispredict** | 5 ns | Pipeline flush |
| **L2 cache reference** | 7 ns | 14× L1 |
| **Mutex lock/unlock** | 25 ns | Context switch overhead |
| **Main memory (RAM) reference** | 100 ns | 200× L1, fundamental unit |
| **Compress 1KB with Snappy** | 3 μs | 3,000 ns |
| **Send 2KB over 1 Gbps network** | 20 μs | Same datacenter |
| **Read 1MB sequentially from memory** | 250 μs | ~4 GB/sec throughput |
| **Round trip in same datacenter** | 500 μs | 0.5 ms |
| **Read 1MB from SSD** | 1 ms | 4× faster than HDD |
| **Disk seek (HDD)** | 10 ms | Random access |
| **Read 1MB sequentially from disk** | 20 ms | Sequential 10× faster |
| **Send packet CA → Netherlands → CA** | 150 ms | Cross-continental |

### Key Takeaways

```
Latency Comparison (Human Scale):
If L1 cache = 1 second, then:
- L2 cache = 14 seconds
- RAM = 3 minutes
- SSD read = 23 days
- HDD seek = 231 days (7.5 months)
- Network cross-continent = 9.5 years
```

**Rules of Thumb:**
- Memory is ~100,000× faster than disk
- SSD is ~100× faster than HDD
- Sequential access is ~10× faster than random
- Same-datacenter network: <1ms
- Cross-region network: 50-150ms

---

## Throughput Numbers

### Network Bandwidth

| Connection | Bandwidth | Real Throughput |
|------------|-----------|-----------------|
| **4G LTE** | 100 Mbps | ~12 MB/sec |
| **Home Internet** | 100-1000 Mbps | 12-125 MB/sec |
| **Datacenter NIC** | 10 Gbps | ~1.25 GB/sec |
| **High-end NIC** | 100 Gbps | ~12.5 GB/sec |

### Storage Throughput

| Storage Type | Sequential Read | Random IOPS |
|--------------|----------------|-------------|
| **HDD** | 100-200 MB/sec | 100-200 IOPS |
| **SATA SSD** | 500-600 MB/sec | 90K IOPS |
| **NVMe SSD** | 3-7 GB/sec | 500K-1M IOPS |
| **Memory (RAM)** | 20-100 GB/sec | N/A |

---

## Time Conversions (for QPS calculations)

```
SECONDS:
1 minute = 60 seconds
1 hour = 3,600 seconds
1 day = 86,400 seconds (≈ 100K for mental math)
1 week = 604,800 seconds (≈ 600K)
1 month (30 days) = 2,592,000 seconds (≈ 2.5M)
1 year = 31,536,000 seconds (≈ 30M)

MILLISECONDS:
1 second = 1,000 ms
1 minute = 60,000 ms
1 hour = 3,600,000 ms

REQUESTS PER SECOND (QPS):
1 req/sec = 86,400 req/day (≈ 100K)
10 req/sec = 864,000 req/day (≈ 1M)
100 req/sec = 8,640,000 req/day (≈ 10M)
1,000 req/sec = 86,400,000 req/day (≈ 100M)
10,000 req/sec = 864,000,000 req/day (≈ 1B)
```

**Quick Conversion Formula:**
```
QPS ≈ Daily Requests ÷ 100,000

Examples:
- 1M requests/day ≈ 10 QPS
- 10M requests/day ≈ 100 QPS
- 100M requests/day ≈ 1,000 QPS (1K QPS)
- 1B requests/day ≈ 10,000 QPS (10K QPS)
```

---

## Storage Sizes

### Powers of 2 & 10

| Power | Exact (2^n) | Approximate (10^n) | Name |
|-------|-------------|-------------------|------|
| 2^10 | 1,024 | ~1 thousand | KB |
| 2^20 | 1,048,576 | ~1 million | MB |
| 2^30 | 1,073,741,824 | ~1 billion | GB |
| 2^40 | 1,099,511,627,776 | ~1 trillion | TB |
| 2^50 | ~1,125,899,906,842,624 | ~1 quadrillion | PB |

### Data Sizes

```
CHARACTER/TEXT:
- 1 ASCII char = 1 byte
- 1 UTF-8 char = 1-4 bytes (average 1.5)
- 1 emoji = 4 bytes
- UUID (32 hex chars) = 16 bytes
- IPv4 address = 4 bytes
- IPv6 address = 16 bytes

COMMON OBJECTS:
- Small tweet (140 chars) = ~140 bytes
- Tweet with metadata = ~500 bytes
- User profile = ~1-2 KB
- Small JSON payload = ~1 KB
- Medium JSON payload = ~10 KB
- Typical web page (HTML) = ~100 KB
- Small image (thumbnail) = ~10-50 KB
- Medium image (JPEG) = ~200-500 KB
- High-res photo = ~2-5 MB
- Song (MP3, 3 min) = ~3 MB
- 1080p video (1 min) = ~50-100 MB
- 4K video (1 min) = ~300-400 MB
```

### Database Record Sizes

```
User Table:
├── BIGINT (user_id): 8 bytes
├── VARCHAR(50) (name): ~20 bytes avg
├── VARCHAR(100) (email): ~25 bytes avg
├── CHAR(60) (hashed_password): 60 bytes
├── TIMESTAMP (created_at): 8 bytes
├── JSONB (metadata): ~100-500 bytes
└── TOTAL: ~300-700 bytes (use ~1 KB with overhead)

Social Media Post:
├── Post ID: 8 bytes
├── User ID: 8 bytes
├── Text (280 chars): 280 bytes
├── Timestamps: 16 bytes
├── Metadata: 100 bytes
└── TOTAL: ~400-500 bytes

Index Overhead: Add 50-100% to storage estimates
Replication: Multiply by replication factor (typically 3)
```

---

## Availability (Nines)

| Availability | Downtime/Year | Downtime/Month | Downtime/Day |
|--------------|---------------|----------------|--------------|
| **90%** (1 nine) | 36.5 days | 3 days | 2.4 hours |
| **99%** (2 nines) | 3.65 days | 7.2 hours | 14.4 min |
| **99.9%** (3 nines) | 8.76 hours | 43.8 min | 1.44 min |
| **99.95%** | 4.38 hours | 21.9 min | 43.2 sec |
| **99.99%** (4 nines) | 52.6 min | 4.38 min | 8.64 sec |
| **99.999%** (5 nines) | 5.26 min | 26.3 sec | 0.86 sec |
| **99.9999%** (6 nines) | 31.5 sec | 2.63 sec | 0.09 sec |

**Formula:**
```
Downtime = (1 - Availability) × Total Time

Example: 99.9% availability for 1 year
Downtime = (1 - 0.999) × 365 days × 24 hours = 8.76 hours/year
```

---

## Typical System Scales

### Small Startup (0-10K users)

```
DAU: 1,000-10,000
QPS: 1-10
Storage: 10-100 GB
Bandwidth: 1-10 Mbps
Servers: 1-2 (monolith)
Database: Single instance
```

### Medium Startup (10K-1M users)

```
DAU: 10,000-1,000,000
QPS: 10-1,000
Storage: 100 GB - 10 TB
Bandwidth: 10-100 Mbps
Servers: 5-50 (microservices)
Database: Primary + read replicas
Cache: Redis/Memcached cluster
```

### Large Scale (1M-100M users)

```
DAU: 1,000,000-100,000,000
QPS: 1,000-100,000
Storage: 10 TB - 1 PB
Bandwidth: 100 Mbps - 10 Gbps
Servers: 100-10,000 (distributed)
Database: Sharded across many nodes
Cache: Distributed cache (Redis Cluster)
CDN: Required
```

### Massive Scale (100M+ users)

```
DAU: 100,000,000+
QPS: 100,000-1,000,000+
Storage: 1 PB+
Bandwidth: 10+ Gbps
Servers: 10,000+
Database: Multi-region, heavily sharded
CDN: Global distribution required
Examples: Google, Facebook, Netflix
```

---

## Cost Estimates (AWS - Approximate 2024)

### Compute (EC2)

| Instance Type | vCPU | RAM | Cost/Month |
|---------------|------|-----|------------|
| **t3.micro** | 2 | 1 GB | $7 |
| **t3.small** | 2 | 2 GB | $15 |
| **t3.medium** | 2 | 4 GB | $30 |
| **t3.large** | 2 | 8 GB | $60 |
| **m5.large** | 2 | 8 GB | $70 |
| **m5.xlarge** | 4 | 16 GB | $140 |
| **m5.2xlarge** | 8 | 32 GB | $280 |
| **c5.large** (CPU optimized) | 2 | 4 GB | $62 |

### Storage

| Storage Type | Cost |
|--------------|------|
| **EBS (SSD)** | $0.10/GB/month |
| **EBS (HDD)** | $0.045/GB/month |
| **S3 Standard** | $0.023/GB/month |
| **S3 Glacier (archive)** | $0.004/GB/month |

### Database

| Service | Cost |
|---------|------|
| **RDS db.t3.micro** | $15/month + storage |
| **RDS db.m5.large** | $120/month + storage |
| **DynamoDB** | $0.25/GB/month + $1.25 per million writes |
| **ElastiCache (Redis) t3.micro** | $12/month |

### Network

| Service | Cost |
|---------|------|
| **Data Transfer OUT** | $0.09/GB (first 10 TB) |
| **Data Transfer IN** | Free |
| **CloudFront (CDN)** | $0.085/GB |
| **Load Balancer (ALB)** | $16/month + $0.008/LCU-hour |

### Example: Twitter-like App (1M DAU)

```
Assumptions:
- 1M DAU, 10 posts/day = 10M posts/day
- 100:1 read:write ratio
- 100 QPS writes, 10K QPS reads (avg)

Costs:
├── Compute (50× m5.large): $3,500/month
├── Database (db.m5.2xlarge + replicas): $1,200/month
├── Cache (Redis cluster): $500/month
├── Storage (S3, 10TB): $230/month
├── CDN (CloudFront, 100TB/month): $8,500/month
├── Load Balancers: $100/month
└── TOTAL: ~$14,000/month ≈ $168K/year
```

---

## Interview Quick Reference

### Memorize These 10 Numbers

1. **L1 cache**: 0.5 ns (fastest)
2. **RAM**: 100 ns
3. **SSD**: 1 ms (1,000 μs)
4. **HDD seek**: 10 ms
5. **Network (same DC)**: 0.5 ms
6. **Network (cross-region)**: 150 ms
7. **1M requests/day** ≈ 12 QPS
8. **99.9% availability** = 8.76 hours downtime/year
9. **Rule of thumb**: Peak traffic = 3× average
10. **Index overhead**: Add 50-100% to storage

### Quick Calculation Template

```
1. QPS = Daily Requests ÷ 100,000
2. Storage = Records × Size/Record × Replication (3×)
3. Bandwidth = QPS × Response Size
4. Cache Size = Hot Data (20% of total)
5. Peak QPS = Average QPS × 3
```

---

## Practice Problems

Use these numbers to quickly estimate:

**Q: 100M users, each generates 5MB data. Storage needed with 3× replication?**
```
= 100M × 5MB × 3
= 500M MB × 3
= 1,500M MB
= 1,500 TB = 1.5 PB
```

**Q: System handles 10B requests/day. What's the QPS?**
```
= 10B ÷ 100,000
= 100,000 QPS (100K QPS)
Peak (3×): 300K QPS
```

**Q: If 99.99% availability is required, max downtime/month?**
```
= (1 - 0.9999) × 30 days × 24 hours × 60 min
= 0.0001 × 43,200 min
= 4.32 minutes/month
```
