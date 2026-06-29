---
module: 07-interview-templates
status: unread
tags: [07-interview-templates, system-design, interview-templates]
---
# Capacity Estimation Cheat Sheet

> **Quick reference for back-of-envelope calculations in system design interviews**

---

## Template Mindmap

```
Capacity Estimation Framework
├── Core Problem
│   └── Determine right order of magnitude for scale — drives every architectural decision
├── Step 1 — Users & Activity
│   ├── Total users → registered base
│   ├── DAU (Daily Active Users) → typically 10-20% of total
│   └── Read/write ratio → e.g., Twitter is 100:1 read-heavy
├── Step 2 — QPS Calculation
│   ├── Avg QPS = DAU × requests_per_user / 86,400
│   └── Peak QPS = Avg QPS × 2-3× (spiky traffic factor)
├── Step 3 — Storage
│   ├── Per-object size → text (1 KB), image (300 KB), video (100 MB)
│   ├── Daily storage = write QPS × object size × 86,400
│   └── Total = daily storage × retention (years × 365)
├── Step 4 — Bandwidth
│   ├── Ingress = write QPS × avg request size
│   └── Egress = read QPS × avg response size
├── Step 5 — Memory (Cache)
│   ├── Hot data = 20% of daily requests serve 80% of traffic (Pareto)
│   └── Cache size = top 20% objects × object size
├── Key Constants to Memorize
│   ├── 1 day = 86,400 seconds (~10^5)
│   ├── 1 month = 2.5M seconds; 1 year = 31.5M seconds (~3×10^7)
│   ├── 1 KB = 10^3 B, 1 MB = 10^6 B, 1 GB = 10^9 B, 1 TB = 10^12 B
│   └── Single server handles ~10K-50K HTTP req/sec (depends on payload)
├── When to Use
│   └── ✓ Every HLD interview — do this before drawing any architecture boxes
└── Interview Angles
    ├── "How many servers do you need?" → Peak QPS / throughput per server
    ├── "What's your storage cost?" → total bytes × $/GB (S3 ~$0.023/GB/month)
    └── "Would you shard the DB?" → yes if storage > 5 TB or write QPS > 10K
```

---

## The Right Mindset: The Napkin Sketch

> **Analogy: A napkin sketch at a startup pitch.**
>
> A founder doesn't need an architect's blueprint to convince investors the building is feasible. They grab a napkin and sketch: "It's about this big, needs this much steel, costs roughly this much." Nobody expects the napkin to match the final construction specs. They want to know: are we talking about a shed or a skyscraper? One server or a thousand?
>
> Back-of-envelope estimation is exactly this. You're not computing the right answer — you're computing the **right order of magnitude**. Is this 10 GB of data or 10 TB? Does this need 10 QPS or 10,000 QPS? Those answers drive completely different architectural choices.

**The goal is**: catch wrong assumptions early, communicate your reasoning clearly, and show you understand the scale implications of your design.

---

## Quick Reference Formula Box

```
┌─────────────────────────────────────────────────────────────────┐
│                    KEY FORMULAS AT A GLANCE                     │
├─────────────────────────────────────────────────────────────────┤
│ QPS (avg)    = Total requests / 86,400 (seconds/day)            │
│ Peak QPS     = Avg QPS × 3                                      │
│ Storage      = Records × Record Size × Replication × Index      │
│ Bandwidth    = QPS × Avg Response Size                          │
│ Cache Size   = 20% of daily unique items × Item size            │
│ Shards       = Total Data / Max Shard Size                      │
│ Downtime     = (1 - Availability) × Total Time                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## Powers of Two

| Power | Exact Value | Approx | Bytes |
|-------|-------------|--------|-------|
| 10    | 1,024       | 1 thousand | 1 KB |
| 20    | 1,048,576   | 1 million | 1 MB |
| 30    | 1,073,741,824 | 1 billion | 1 GB |
| 40    | 1,099,511,627,776 | 1 trillion | 1 TB |
| 50    | ~10^15      | 1 quadrillion | 1 PB |

---

## Time Conversions

```
1 second = 1,000 milliseconds (ms)
1 second = 1,000,000 microseconds (μs)
1 second = 1,000,000,000 nanoseconds (ns)

1 day  = 24 hours = 86,400 seconds  (~100K seconds — round up)
1 month = 30 days = 2,592,000 seconds (~2.5M seconds)
1 year  = 365 days = 31,536,000 seconds (~30M seconds)
```

**Trick**: Use 100K seconds/day for fast mental math. The error is ~15% — totally acceptable.

---

## QPS (Queries Per Second) Conversions

| Requests/Period | QPS (Average) | Formula |
|----------------|---------------|---------|
| 1M/day         | ~12 QPS       | 1M / 86,400 |
| 10M/day        | ~120 QPS      | 10M / 86,400 |
| 100M/day       | ~1,200 QPS    | 100M / 86,400 |
| 1B/day         | ~12K QPS      | 1B / 86,400 |
| 10B/day        | ~120K QPS     | 10B / 86,400 |
| 100B/day       | ~1.2M QPS     | 100B / 86,400 |

**Peak Traffic Rule**: Peak QPS ≈ 2–3× average QPS. Always use 3× for safety in interviews.

> **Why does peak matter?** Think of a highway at rush hour. The road isn't designed for the average number of cars in a week — it's designed for the worst Friday evening of the year. Your servers are the road.

---

## Latency Numbers Every Engineer Should Know

```
L1 cache reference                           0.5 ns
Branch mispredict                            5   ns
L2 cache reference                           7   ns
Mutex lock/unlock                           25   ns
Main memory reference                      100   ns
Compress 1KB with Snappy               3,000   ns  =   3 μs
Send 2KB over 1 Gbps network          20,000   ns  =  20 μs
Read 1MB sequentially from memory    250,000   ns  = 250 μs
Round trip within same datacenter    500,000   ns  = 500 μs
Disk seek (HDD)                   10,000,000   ns  =  10 ms
Read 1MB sequentially from disk   20,000,000   ns  =  20 ms
Send packet CA→Netherlands→CA    150,000,000   ns  = 150 ms
```

**Key Takeaways:**
- Memory is ~100× faster than disk (HDD).
- SSD seek: ~150 μs (roughly 100× faster than HDD seek).
- Network within one datacenter: ~0.5 ms.
- Cross-region network: ~50–150 ms.
- Cache hits (Redis, Memcached): sub-millisecond. This is why caching exists.

---

## Storage Size Estimates

### Object and File Sizes

| Data Type | Size |
|-----------|------|
| 1 character (ASCII) | 1 byte |
| 1 character (UTF-8) | 1–4 bytes (avg ~1.5 bytes) |
| Short tweet (140 chars) | ~140 bytes |
| Small JSON object | ~1 KB |
| Medium-sized JPEG photo | ~200 KB |
| High-res photo | ~2–5 MB |
| 1080p video (1 min) @ 5 Mbps | ~37 MB |
| 1080p video (1 min) compressed | ~50–100 MB |
| 4K video (1 min) | ~300–400 MB |
| Audio track (3 min, 128 kbps MP3) | ~3 MB |
| Chat message (text only) | ~100–300 bytes |

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

Indexes add 50–100% overhead to raw storage.
```

---

## Storage Calculation Template

```
Total Storage = Records × Record Size × Replication Factor × Index Overhead

Twitter Example:
Daily tweets:    500M
Record size:     500 bytes
Storage/day:     500M × 500 bytes = 250 GB/day
5 years:         250 GB × 365 × 5 = 456 TB raw
With replication (3×): 456 TB × 3 = 1.4 PB
With index overhead (1.5×): 1.4 PB × 1.5 ≈ 2.1 PB
```

---

## Bandwidth Estimation

```
Bandwidth = QPS × Average Payload Size

Upload bandwidth  = Write QPS × Avg Request Size
Download bandwidth = Read QPS × Avg Response Size
```

### Real-World Bandwidth Examples

**Chat Application (WhatsApp-like):**
```
Assumptions:
- 1B DAU, each user sends 10 messages/day
- Avg message: 200 bytes (text only)

Write QPS:  10B messages / 86,400 = ~115,000 writes/sec
Peak:       115,000 × 3 = ~345,000 writes/sec
Bandwidth:  115,000 × 200 bytes = 23 MB/s = 184 Mbps (average)
Peak:       184 × 3 = ~550 Mbps outbound
```

**Video Streaming (Netflix-like):**
```
Assumptions:
- 100M DAU
- 20% are watching at any moment (peak concurrent = 20M users)
- 1080p stream: 5 Mbps per user

Peak bandwidth:  20M users × 5 Mbps = 100 Tbps
→ This is why Netflix uses a massive CDN. No single origin can serve 100 Tbps.
→ CDN caches popular titles at edges. Only cache misses hit origin.
```

**Photo Upload (Instagram-like):**
```
Assumptions:
- 100M DAU, 10% upload a photo/day = 10M uploads/day
- Avg photo: 3 MB

Write QPS:   10M / 86,400 = ~116 uploads/sec
Upload BW:   116 × 3 MB = 348 MB/s inbound
Read QPS:    Assume 10:1 read ratio = 1,160 reads/sec
Download BW: 1,160 × 200 KB (compressed for feed) = 232 MB/s outbound
```

**Key insight**: For media-heavy applications, bandwidth is almost always the binding constraint — not QPS or storage alone.

---

## Cache Size Estimation (80/20 Rule)

```
Assumption: 20% of data generates 80% of traffic ("hot" data)

Example — Social media profile lookups:
Daily active users:  100M
Unique profiles hit: ~20M (many users look at the same popular profiles)
Cache 20% of them:   4M profiles
Profile size:        1 KB
Cache size needed:   4M × 1 KB = 4 GB  (trivially fits in Redis)

Contrast with naive approach (cache everyone):
Total users: 1B × 1 KB = 1 TB — way too large for in-memory cache
```

---

## CDN Bandwidth Calculation

```
Users: 100M DAU
Avg session: 30 min
Avg content per session: 10 MB
Total daily: 100M users × 10 MB = 1 PB/day
Avg bandwidth: 1 PB / 86,400 sec ≈ 11.5 GB/s
Peak bandwidth: 11.5 GB/s × 3 = ~35 GB/s
```

---

## Database Connection Pool Sizing

```
Formula: Max connections per DB = (Core Count × 2) + Effective Spindle Count

Example:
8-core database server with SSD (treat SSD as 1 spindle)
Max connections: (8 × 2) + 1 = 17

For connection pooling:
App servers: 50
Connections per app server: 10
Total connections: 500
DB servers needed: 500 ÷ 17 ≈ 30 DB servers — or use a connection pooler (PgBouncer, ProxySQL)
```

---

## Availability Calculations (Nines)

| Availability | Downtime/Year | Downtime/Month | Downtime/Week |
|--------------|---------------|----------------|---------------|
| 90% (one nine) | 36.5 days | 3 days | 16.8 hours |
| 99% (two nines) | 3.65 days | 7.2 hours | 1.68 hours |
| 99.9% (three nines) | 8.76 hours | 43.8 min | 10.1 min |
| 99.99% (four nines) | 52.56 min | 4.38 min | 1.01 min |
| 99.999% (five nines) | 5.26 min | 26.3 sec | 6.05 sec |

```
Formula: Downtime = (1 - Availability) × Total Time

99.9% uptime/year:
Downtime = (1 - 0.999) × 365 × 24 hours = 8.76 hours/year
```

> **Analogy:** "Five nines" (99.999%) means your service can be down for at most 5 minutes per year. That's roughly the length of one song. Achieving this requires redundancy at every layer — no single point of failure anywhere.

---

## Replication & Partitioning

### Replication Factor

```
RF=1: No redundancy (single point of failure — never in production)
RF=2: Tolerates 1 node failure
RF=3: Tolerates 2 node failures (recommended default)
RF=5: Enterprise-critical data (Cassandra financial workloads)
```

### Partitioning (Sharding)

```
Number of Shards = Total Data Size / Max Practical Shard Size

Example:
Total data: 1 TB
Max shard: 100 GB (manageable single node)
Shards needed: 10
```

---

## Worked Examples

### Example 1: URL Shortener

```
Given:
- 1M new URLs/day
- 100:1 read:write ratio
- 5-year retention

Step 1 — Write QPS:
1M / 86,400 ≈ 12 writes/sec (avg)
Peak: 12 × 3 = 36 writes/sec

Step 2 — Read QPS:
12 × 100 = 1,200 reads/sec (avg)
Peak: 1,200 × 3 = 3,600 reads/sec

Step 3 — Storage:
URLs/5 years: 1M × 365 × 5 = 1.825B URLs ≈ 2B
Per URL record: 500 bytes (short_code + original_url + metadata)
Raw storage: 2B × 500 bytes = 1 TB
With replication (3×): 3 TB
With index (1.5×): 4.5 TB

Step 4 — Key space:
Short codes (6 alphanumeric chars, 62^6): ~56 billion combinations
2B URLs needs << 56B key space → 6 chars is plenty for 5 years

Verdict: Low QPS, modest storage. Single PostgreSQL + Redis cache. No sharding needed initially.
```

### Example 2: Twitter Timeline

```
Given:
- 300M DAU
- Each user fetches timeline 5×/day
- Timeline: 20 tweets × 500 bytes = 10 KB per response

Step 1 — Read QPS:
300M × 5 = 1.5B requests/day
1.5B / 86,400 ≈ 17,500 QPS (avg)
Peak: 17,500 × 3 = 52,500 QPS

Step 2 — Bandwidth:
52,500 × 10 KB = 525 MB/s = 4.2 Gbps outbound (peak)

Step 3 — Write QPS:
Assume 1% of users tweet once/day: 3M tweets/day
3M / 86,400 ≈ 35 writes/sec (trivial compared to reads)

Verdict: Massively read-heavy (500:1). Cache timelines aggressively in Redis.
Write 35/sec is nothing. Read 52K QPS needs horizontal scaling + CDN for media.
```

### Example 3: Video Streaming (Netflix-like)

```
Given:
- 100M DAU
- Avg 2 hours watching/day
- 1080p: ~5 Mbps bitrate

Step 1 — Storage (new content):
100 new 2-hour movies/day
Per movie (1080p): 2 hours × 3,600 sec × 5 Mbps = 36,000 Mb = 4.5 GB
100 movies × 4.5 GB = 450 GB/day new content
3 quality tiers (1080p + 720p + 480p): 450 GB × 3 = 1.35 TB/day
1 year: 1.35 TB × 365 = ~500 TB/year

Step 2 — Peak concurrent bandwidth:
Peak concurrent viewers: 100M × 20% = 20M
20M × 5 Mbps = 100 Tbps
→ Must use CDN. Origin can't deliver 100 Tbps.

Step 3 — CDN cache hit rate:
If top 1,000 titles cover 80% of views (Pareto principle):
CDN caches these → 80% of bandwidth served from edge, not origin
Origin only handles: 100 Tbps × 20% = 20 Tbps (still massive, but manageable across regions)
```

### Example 4: Ride-Sharing Location Updates (Uber-like)

```
Given:
- 1M active drivers sending GPS location every 5 seconds
- Location record: 50 bytes (lat, lng, timestamp, driver_id)

Step 1 — Write QPS:
1M drivers / 5 sec = 200,000 writes/sec (200K QPS)
This is high — needs a write-optimized store.

Step 2 — Write bandwidth:
200,000 × 50 bytes = 10 MB/s (manageable)

Step 3 — Read QPS (rider app requesting nearby drivers):
5M active riders, each polling every 10 sec = 500K reads/sec

Step 4 — Storage:
Only need current location (last known position), not history (for now)
1M drivers × 50 bytes = 50 MB → fits entirely in Redis
Historical location: retain 30 days → 200K writes/sec × 86,400 × 30 = ~500 TB

Verdict:
- Current locations: Redis (50 MB, sub-ms reads/writes)
- Geospatial queries (find drivers near me): Redis GEOSEARCH or PostGIS
- Historical logs: Cassandra or S3 (write-heavy, time-series friendly)
```

---

## Cost Estimation (AWS Prices — Approximate)

```
Compute (EC2):
├── t3.medium (2 vCPU, 4 GB RAM): ~$30/month
├── c5.large (2 vCPU, 4 GB RAM): ~$60/month
└── m5.xlarge (4 vCPU, 16 GB RAM): ~$140/month

Storage:
├── S3 Standard: $0.023/GB/month
├── S3 Glacier (cold archive): $0.004/GB/month
├── EBS SSD (gp3): $0.08/GB/month
└── RDS (PostgreSQL): $0.115/GB/month + instance cost

Networking:
├── Data transfer OUT: $0.09/GB (first 10 TB/month)
├── CloudFront (CDN): $0.085/GB
└── Data transfer IN: Free

Database:
├── DynamoDB: $0.25/GB/month + $1.25 per million writes
└── RDS db.r5.large: ~$180/month + storage
```

---

## Common Mistakes

### Mistakes to Avoid

**Math errors:**
- Forgetting that 1 day = 86,400 seconds (not 100,000 — but rounding to 100K is fine).
- Treating "1 billion" as 1,000 MB instead of 1,000,000,000 bytes.
- Mixing up MB/s (megabytes) and Mbps (megabits): 1 MB/s = 8 Mbps.

**Missing factors:**
- **Replication**: Always multiply raw storage by replication factor (usually 3×).
- **Index overhead**: Adds 50–100% on top of data storage. Forgetting this underestimates storage by 2×.
- **Peak vs average**: Designing for average QPS means your system falls over at peak. Always size for 3× average.
- **Read vs write ratio**: Many systems are 10:1 to 100:1 read-heavy. Optimization strategy differs completely.

**Wrong focus:**
- Spending 10 minutes calculating to 4 significant figures. Order of magnitude is the goal.
- Not stating assumptions explicitly. The interviewer can't evaluate your reasoning without them.
- Confusing bandwidth with storage. Bandwidth is a rate (per second); storage is cumulative (over years).

**Architectural blind spots:**
- Ignoring CDN for media-heavy applications (video, images).
- Not accounting for connection pool limits when sizing DB servers.
- Forgetting that cache only helps reads, not writes.

### What Good Looks Like

```
Bad:  "We need 847.3 GB of storage."

Good: "Assuming 500 bytes per record and 100M records, that's 50 GB raw.
       With 3× replication and 1.5× index overhead, call it ~225 GB —
       round to 250 GB. Fits comfortably on a single SSD instance,
       but I'd provision 500 GB to leave headroom for growth."
```

---

## Interview Tips

1. **Round aggressively**: 86,400 → 100K. 1,000,000 → 1M. Nobody will correct you.
2. **State assumptions first**: "I'll assume 100M DAU, 10:1 read/write ratio, and 5-year retention."
3. **Show your work step by step**: QPS → bandwidth → storage, in that order.
4. **Use powers of 10**: Think in 1K, 1M, 1B, 1T.
5. **Relate numbers to architecture**: "52K QPS means we need a read replica + caching layer."
6. **Know when to stop**: Once you have the order of magnitude, move on. Don't refine forever.
7. **Sanity check your answer**: "100 Tbps peak bandwidth — that's clearly CDN territory."
