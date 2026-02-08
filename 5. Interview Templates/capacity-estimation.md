# Capacity Estimation Cheat Sheet

> **Quick reference for back-of-envelope calculations in system design interviews**

## Powers of Two

| Power | Exact Value | Approx | Bytes |
|-------|-------------|--------|-------|
| 10    | 1,024       | 1 thousand | 1 KB |
| 20    | 1,048,576   | 1 million | 1 MB |
| 30    | 1,073,741,824 | 1 billion | 1 GB |
| 40    | 1,099,511,627,776 | 1 trillion | 1 TB |
| 50    | ~10^15      | 1 quadrillion | 1 PB |

## Time Conversions

```
1 second = 1,000 milliseconds (ms)
1 second = 1,000,000 microseconds (μs)
1 second = 1,000,000,000 nanoseconds (ns)

1 day = 24 hours = 86,400 seconds (~100K seconds)
1 month = 30 days = 2,592,000 seconds (~2.5M seconds)
1 year = 365 days = 31,536,000 seconds (~30M seconds)
```

## QPS (Queries Per Second) Conversions

| Requests/Period | QPS (Average) | Formula |
|----------------|---------------|---------|
| 1M/day         | ~12 QPS       | 1M / 86,400 |
| 10M/day        | ~120 QPS      | 10M / 86,400 |
| 100M/day       | ~1,200 QPS    | 100M / 86,400 |
| 1B/day         | ~12K QPS      | 1B / 86,400 |
| 10B/day        | ~120K QPS     | 10B / 86,400 |
| 100B/day       | ~1.2M QPS     | 100B / 86,400 |

**Peak Traffic Rule**: Peak QPS ≈ 2-3× average QPS (use 3× for safety)

## Latency Numbers Every Engineer Should Know

```
L1 cache reference                           0.5 ns
Branch mispredict                            5   ns
L2 cache reference                           7   ns
Mutex lock/unlock                           25   ns
Main memory reference                      100   ns
Compress 1KB with Zippy                  3,000   ns  =   3 μs
Send 2KB over 1 Gbps network            20,000   ns  =  20 μs
Read 1MB sequentially from memory      250,000   ns  = 250 μs
Round trip within same datacenter      500,000   ns  = 500 μs
Disk seek                           10,000,000   ns  =  10 ms
Read 1MB sequentially from disk     20,000,000   ns  =  20 ms
Send packet CA→Netherlands→CA      150,000,000   ns  = 150 ms
```

**Key Takeaways:**
- Memory is ~100× faster than disk
- Network within datacenter: ~0.5ms
- Cross-region network: ~50-150ms
- SSD seek: ~150μs (100× faster than HDD)

## Storage Size Estimates

### Text Data

| Data Type | Size |
|-----------|------|
| 1 character (ASCII) | 1 byte |
| 1 character (UTF-8) | 1-4 bytes (avg ~1.5 bytes) |
| Short tweet (140 chars) | ~140 bytes |
| Small JSON object | ~1 KB |
| Medium-sized image (JPEG) | ~200 KB |
| High-res photo | ~2-5 MB |
| 1080p video (1 min) | ~50-100 MB |
| 4K video (1 min) | ~300-400 MB |

### Database Record Estimates

```
Typical User Record:
├── user_id (BIGINT): 8 bytes
├── username (VARCHAR(50)): ~20 bytes avg
├── email (VARCHAR(100)): ~25 bytes avg
├── hashed_password (CHAR(60)): 60 bytes
├── created_at (TIMESTAMP): 8 bytes
├── metadata (JSONB): ~500 bytes
└── Total: ~620 bytes ≈ 1 KB (with overhead)

Typical Tweet:
├── tweet_id: 8 bytes
├── user_id: 8 bytes
├── text (280 chars): 280 bytes
├── media_url: 100 bytes
├── timestamps: 16 bytes
├── metadata: 100 bytes
└── Total: ~512 bytes ≈ 500 bytes

Indexes add 50-100% overhead
```

## Storage Calculation Template

```
Total Storage = Records × Record Size × Replication Factor × Index Overhead

Example: Twitter
Daily tweets: 500M
Record size: 500 bytes
Storage per day = 500M × 500 bytes = 250 GB/day
5 years = 250 GB × 365 × 5 = 456 TB
With replication (3×): 456 TB × 3 = 1.4 PB
With index (1.5×): 1.4 PB × 1.5 = 2.1 PB
```

## Bandwidth Calculation

```
Bandwidth = QPS × Average Response Size

Example:
12,000 reads/sec × 2 KB = 24 MB/s = 192 Mbps

Upload: Writes/sec × Request Size
Download: Reads/sec × Response Size
```

## Cache Size Estimation (80/20 Rule)

```
Assumption: 20% of data generates 80% of traffic

Daily Requests = 1B
Unique items = 100M
Cache 20% = 20M items
Item size = 1 KB
Cache Size = 20M × 1 KB = 20 GB
```

## CDN Bandwidth Calculation

```
Users: 100M DAU
Avg session: 30 min
Avg content per session: 10 MB
Total daily: 100M users × 10 MB = 1 PB/day
Peak bandwidth: 1 PB/day ÷ 86,400 sec × 3 (peak factor) = 40 GB/s
```

## Database Connection Pool Sizing

```
Formula: Connections = (Core Count × 2) + Effective Spindle Count

Example:
8-core database server with SSD (treat as 1 spindle)
Max connections: (8 × 2) + 1 = 17 connections

For Pooling:
Total app servers: 50
Connections per server: 10
Total connections: 500
Database servers needed: 500 ÷ 17 ≈ 30 DB servers (or use read replicas)
```

## Availability Calculations (Nines)

| Availability | Downtime/Year | Downtime/Month | Downtime/Week |
|--------------|---------------|----------------|---------------|
| 90% (one nine) | 36.5 days | 3 days | 16.8 hours |
| 99% (two nines) | 3.65 days | 7.2 hours | 1.68 hours |
| 99.9% (three nines) | 8.76 hours | 43.8 min | 10.1 min |
| 99.99% (four nines) | 52.56 min | 4.38 min | 1.01 min |
| 99.999% (five nines) | 5.26 min | 26.3 sec | 6.05 sec |

**Formula:**
```
Downtime = (1 - Availability) × Total Time
Example: 99.9% availability
Downtime/year = (1 - 0.999) × 365 days × 24 hours = 8.76 hours
```

## Replication & Partitioning

### Replication Factor

```
Replication Factor = Number of Copies

Common configurations:
├── RF=1: No redundancy (single point of failure)
├── RF=2: Can tolerate 1 node failure
├── RF=3: Can tolerate 2 node failures (recommended)
└── RF=5: Enterprise (Cassandra for critical data)
```

### Partitioning (Sharding)

```
Number of Shards = Total Data Size / Max Shard Size

Example:
Total data: 1 TB
Max shard size: 100 GB (practical limit for single node)
Number of shards: 1 TB ÷ 100 GB = 10 shards
```

## Quick Estimation Examples

### Example 1: URL Shortener

```
Given:
- 1M new URLs/day
- 100:1 read:write ratio
- 5-year retention

Write QPS:
1M/day ÷ 86,400 = ~12 writes/sec (avg)
Peak: 12 × 3 = 36 writes/sec

Read QPS:
12 × 100 = 1,200 reads/sec (avg)
Peak: 1,200 × 3 = 3,600 reads/sec

Storage:
URLs/year: 1M/day × 365 = 365M
5 years: 365M × 5 = 1.8B URLs
Per URL: 500 bytes (short_code + long_url + metadata)
Total: 1.8B × 500 bytes = 900 GB
With replication (3×): 900 GB × 3 = 2.7 TB
```

### Example 2: Twitter Timeline

```
Given:
- 300M DAU
- Each user fetches timeline 5 times/day
- Timeline size: 20 tweets × 500 bytes = 10 KB

Read QPS:
300M users × 5 fetches/day = 1.5B requests/day
1.5B ÷ 86,400 = ~17,500 QPS (avg)
Peak: 17,500 × 3 = 52,500 QPS

Bandwidth:
52,500 QPS × 10 KB = 525 MB/s = 4.2 Gbps
```

### Example 3: Video Streaming (Netflix-like)

```
Given:
- 100M DAU
- Avg 2 hours watching/day
- 1080p: ~5 Mbps bitrate

Storage (new content/day):
Assume 100 new 2-hour movies/day
100 movies × 2 hours × 5 Mbps = 1,000 hours × 5 Mbps
= 5,000 Mb/s × 3,600 sec = 18,000,000 Mb = 2.25 TB/day
With multiple qualities (1080p, 720p, 480p): 2.25 TB × 3 = 6.75 TB/day
1 year: 6.75 TB × 365 = 2.5 PB/year

Bandwidth:
Peak concurrent users: 100M DAU × 20% concurrent = 20M
20M × 5 Mbps = 100 Tbps (use CDN!)
```

## Cost Estimation (AWS Prices - Approximate)

```
Compute (EC2):
├── t3.medium (2 vCPU, 4 GB RAM): ~$30/month
├── c5.large (2 vCPU, 4 GB RAM): ~$60/month
└── m5.xlarge (4 vCPU, 16 GB RAM): ~$140/month

Storage:
├── S3 Standard: $0.023/GB/month
├── S3 Glacier: $0.004/GB/month
├── EBS SSD: $0.10/GB/month
└── RDS (PostgreSQL): $0.125/GB/month + instance cost

Networking:
├── Data transfer OUT: $0.09/GB (first 10 TB)
├── CloudFront (CDN): $0.085/GB
└── Data transfer IN: Free

Database:
├── DynamoDB: $0.25/GB/month + $1.25 per million writes
└── RDS db.r5.large: ~$180/month + storage
```

## Interview Tips

1. **Round aggressively**: 86,400 → 100K, 1,000,000 → 1M
2. **Show your work**: Write calculations on board
3. **State assumptions**: "Assuming 3× peak traffic..."
4. **Use powers of 10**: 1K, 1M, 1B, 1T
5. **Don't aim for precision**: ±20% is fine
6. **Focus on magnitude**: Order of magnitude matters, not exact numbers

## Common Interview Mistakes

❌ Spending >5 minutes on calculations  
❌ Getting bogged down in exact numbers  
❌ Not stating assumptions clearly  
❌ Forgetting replication factor  
❌ Forgetting peak traffic (use 2-3× average)  
❌ Not accounting for indexes (adds 50-100% storage)  

✅ Quick, ballpark estimates  
✅ Clear assumptions  
✅ Round numbers  
✅ Show scaling thinking
