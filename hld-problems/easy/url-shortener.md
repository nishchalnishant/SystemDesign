# Design URL Shortener

> **Difficulty**: Easy  
> **Topics**: Hashing, Base62 Encoding, Database Sharding  
> **Time**: 45-60 minutes  
> **Companies**: Google, Amazon, Meta, Microsoft

## Problem Statement

Design a URL shortening service like bit.ly or TinyURL that:
- Converts long URLs to short URLs
- Redirects short URLs to original long URLs
- Tracks click analytics (optional)

**Example:**
```
Long URL: https://www.example.com/very/long/url/with/many/parameters?id=12345
Short URL: https://short.ly/abc1234
```

---

## Requirements Gathering

### Functional Requirements

**Must-Have:**
1. Generate short URL from long URL
2. Redirect short URL to long URL (301/302)
3. Short URLs never expire

**Nice-to-Have:**
4. Custom aliases (e.g., short.ly/mycompany)
5. Analytics (clicks, geographic, referrers)
6. Expiration time (TTL)
7. Rate limiting

### Non-Functional Requirements

**Performance:**
- Latency: P99 < 100ms for redirects
- Throughput: 10K writes/sec, 100K reads/sec (10:1 read:write ratio common)

**Availability:**
- 99.99% uptime (53 minutes downtime/year)
- No single point of failure

**Scalability:**
- 100M new URLs/month
- 10B redirects/month

**Security:**
- Prevent spam/malicious URLs
- Rate limiting per user/IP

**Reliability:**
- URLs never lost (durable storage)
- Eventual consistency acceptable

---

## Capacity Estimation

### Traffic Estimates

```
Given:
- 100M new URLs/month
- 100:1 read:write ratio

Writes (creates):
100M/month ÷ 30 days ÷ 86,400 sec/day = 40 URLs/sec (average)
Peak (3×): 120 writes/sec

Reads (redirects):
40 writes/sec × 100 = 4,000 reads/sec (average)
Peak (3×): 12,000 reads/sec
```

### Storage Estimates

```
Per URL record:
├─ short_code (7 chars): 7 bytes
├─ long_url (2 KB avg): 2,000 bytes
├─ user_id (BIGINT): 8 bytes
├─ created_at (TIMESTAMP): 8 bytes
├─ metadata (JSONB): 500 bytes
└─ Total: ~2.5 KB per URL

5 years of URLs:
100M/month × 12 months × 5 years = 6 billion URLs
6B × 2.5 KB = 15 TB

With replication (3×): 45 TB
With indexes (2×): 90 TB

**Final Estimate: 90-100 TB** for 5 years
```

### Bandwidth Estimates

```
Writes:
120 writes/sec × 2.5 KB = 300 KB/sec ≈ 2.4 Mbps

Reads:
12,000 reads/sec × 2.5 KB = 30 MB/sec ≈ 240 Mbps (peak)
```

### Cache Requirements (80/20 Rule)

```
Assumption: 20% of URLs generate 80% of traffic

Daily redirects:
12,000 reads/sec × 86,400 sec = 1B redirects/day

Cache hot URLs (estimate 0.1% of total):
6B URLs × 0.001 = 6M URLs
6M × 2.5 KB = 15 GB cache (feasible!)

Redis cache: 32 GB instance (covers hot URLs + overhead)
```

---

## API Design

### REST Endpoints

**1. Create Short URL**
```http
POST /api/v1/urls
Content-Type: application/json

Request:
{
  "long_url": "https://example.com/very/long/url",
  "custom_alias": "mylink",  // optional
  "expiration": "2026-12-31T23:59:59Z"  // optional
}

Response: 201 Created
{
  "short_url": "https://short.ly/abc1234",
  "long_url": "https://example.com/very/long/url",
  "short_code": "abc1234",
  "created_at": "2026-02-08T10:00:00Z",
  "expires_at": null
}
```

**2. Redirect Short URL**
```http
GET /{short_code}

Response: 301 Moved Permanently
Location: https://example.com/very/long/url
```

**3. Get URL Info (Optional)**
```http
GET /api/v1/urls/{short_code}

Response: 200 OK
{
  "short_code": "abc1234",
  "long_url": "https://example.com/very/long/url",
  "created_at": "2026-02-08T10:00:00Z",
  "click_count": 12,345
}
```

**4. Delete URL**
```http
DELETE /api/v1/urls/{short_code}

Response: 204 No Content
```

---

## Database Schema

### SQL Schema (PostgreSQL)

```sql
CREATE TABLE urls (
    short_code VARCHAR(7) PRIMARY KEY,
    long_url TEXT NOT NULL,
    user_id BIGINT,  -- NULL for anonymous users
    created_at TIMESTAMP DEFAULT NOW(),
    expires_at TIMESTAMP,  -- NULL = never expires
    click_count BIGINT DEFAULT 0,
    INDEX idx_user_id (user_id),
    INDEX idx_created_at (created_at)
);

CREATE TABLE analytics (
    id BIGSERIAL PRIMARY KEY,
    short_code VARCHAR(7) REFERENCES urls(short_code),
    clicked_at TIMESTAMP DEFAULT NOW(),
    ip_address INET,
    user_agent TEXT,
    referer TEXT,
    country VARCHAR(2)
);

-- Partitioning analytics by date for efficient queries
CREATE TABLE analytics_2026_02 PARTITION OF analytics
    FOR VALUES FROM ('2026-02-01') TO ('2026-03-01');
```

### NoSQL Schema (DynamoDB)

```
Table: URLs
Partition Key: short_code (String)
Attributes:
  - long_url (String)
  - user_id (String)
  - created_at (Number) // Unix timestamp
  - expires_at (Number) // TTL for DynamoDB auto-delete
  - click_count (Number)

GSI: user_id-created_at-index
  - Partition Key: user_id
  - Sort Key: created_at
  - Purpose: Query user's URLs sorted by creation time
```

---

## High-Level Design

### Architecture Diagram

```
┌─────────┐
│ Client  │
└────┬────┘
     │
     ▼
┌──────────────────┐
│ CDN/CloudFlare   │ (DDoS protection, rate limiting)
└─────────┬────────┘
          │
          ▼
┌──────────────────┐
│  Load Balancer   │ (ALB, Nginx)
│  (Round Robin)   │
└─────────┬────────┘
          │
     ┌────┴────┐
     │         │
     ▼         ▼
┌─────────┐ ┌─────────┐
│  App    │ │  App    │ (Stateless, auto-scaling 10-100 instances)
│ Server  │ │ Server  │
└────┬────┘ └────┬────┘
     │           │
     ├───────────┤
     │           │
     ▼           ▼
┌──────────────────────┐     ┌──────────────┐
│  Redis Cache (15GB)  │────▶│ Cache-Aside  │
│  - LRU eviction      │     │ 95% hit rate │
└──────────┬───────────┘     └──────────────┘
           │                        
           │ Cache Miss (5%)
           ▼                        
┌────────────────────────────────┐
│   PostgreSQL (Primary)         │
│   - URLs table                 │
│   - Write: 120 QPS             │
└────────────┬───────────────────┘
             │
             │ Async Replication
             ▼
  ┌──────────────────────────┐
  │  Read Replicas (×3)      │ (For analytics, fallback reads)
  │  - Replication lag: <1s  │
  └──────────────────────────┘
  
┌──────────────────────────────┐
│  ID Generation Service       │ (Snowflake, Zookeeper-based)
│  - Distributed counter       │
└──────────────────────────────┘
```

---

## Data Flow

### Write Flow (Create Short URL)

```
1. Client → POST /api/v1/urls {"long_url": "..."}
2. Load Balancer → App Server
3. App Server:
   a. Generate unique short code (see algorithms below)
   b. Check if short_code exists in DB (collision check)
   c. INSERT into PostgreSQL: (short_code, long_url, user_id, ...)
   d. Update Redis cache (write-through): SET short_code → long_url
4. Return {"short_url": "https://short.ly/abc1234"}
```

### Read Flow (Redirect Short URL)

```
1. Client → GET /abc1234
2. Load Balancer → App Server
3. App Server:
   a. Check Redis cache: GET abc1234
   b. If HIT (95% of requests):
      → Return long_url from cache (2ms latency)
   c. If MISS (5%):
      → Query PostgreSQL: SELECT long_url FROM urls WHERE short_code = 'abc1234'
      → Update cache: SET abc1234 → long_url (TTL: 24 hours)
      → Return long_url (50ms latency)
4. Return HTTP 301 redirect to long_url
5.(Async) Increment click_count (eventually consistent)
```

---

## Deep Dive Topics

### 1. Short Code Generation

**Requirements:**
- Unique (no collisions)
- Short (7 characters = 62^7 = 3.5 trillion combinations)
- URL-safe characters: `[a-zA-Z0-9]` (Base62)

**Option A: Hash-based (MD5/SHA + Base62)**

```python
import hashlib
import base62

def generate_short_code(long_url):
    hash_val = hashlib.md5(long_url.encode()).hexdigest()  # 32 hex chars
    decimal = int(hash_val[:8], 16)  # Take first 8 hex chars
    short_code = base62.encode(decimal)[:7]  # Convert to base62, take 7 chars
    return short_code

# Collision handling
while db.exists(short_code):
    long_url += str(random.randint(0, 999))  # Append random to long_url
    short_code = generate_short_code(long_url)
```

**Pros:** Deterministic (same URL → same short code)  
**Cons:** Collision possible (need retry logic)

---

**Option B: Auto-Incrementing Counter + Base62 (Recommended)**

```python
import base62

def generate_short_code(counter):
    return base62.encode(counter).zfill(7)  # Pad to 7 chars

# Usage
counter = db.increment_counter()  # Distributed counter (e.g., PostgreSQL sequence)
short_code = generate_short_code(counter)
```

**Pros:** Guaranteed unique, no collisions  
**Cons:** Sequential (somewhat predictable)

---

**Option C: Snowflake ID (Distributed ID Generator)**

```
Snowflake ID (64 bits):
├─ 1 bit: Unused (sign bit)
├─ 41 bits: Timestamp (milliseconds since epoch)
├─ 10 bits: Machine ID (1024 machines)
├─ 12 bits: Sequence number (4096 IDs/ms per machine)

Generates unique IDs across distributed servers
```

```python
snowflake_id = id_generator.get_id()  # e.g., 123456789012345
short_code = base62.encode(snowflake_id)[:7]
```

**Pros:** Distributed, no coordination, time-ordered  
**Cons:** Requires separate ID service

---

### 2. Scaling the Database

**Sharding Strategy:**

```
Shard by: hash(short_code) % num_shards

Example (4 shards):
├─ Shard 0: short_codes with hash % 4 = 0
├─ Shard 1: short_codes with hash % 4 = 1
├─ Shard 2: short_codes with hash % 4 = 2
└─ Shard 3: short_codes with hash % 4 = 3

Lookup:
shard_id = hash(short_code) % 4
db = shards[shard_id]
result = db.query("SELECT * FROM urls WHERE short_code = ?", short_code)
```

**Pros:** Even distribution, horizontal scaling  
**Cons:** Cross-shard queries difficult (e.g., "get all user's URLs")

**Solution for user queries:** Use GSI in DynamoDB or replicate to separate analytics DB

---

### 3. Caching Strategy

**Cache-Aside (Lazy Loading):**

```python
def get_long_url(short_code):
    # 1. Check cache
    long_url = redis.get(f"url:{short_code}")
    if long_url:
        return long_url  # Cache HIT
    
    # 2. Cache MISS → Query DB
    long_url = db.query("SELECT long_url FROM urls WHERE short_code = ?", short_code)
    
    # 3. Update cache
    redis.set(f"url:{short_code}", long_url, ex=86400)  # TTL: 24 hours
    
    return long_url
```

**Cache Eviction:** LRU (Least Recently Used)  
**Cache Size:** 32 GB (covers 6M hot URLs)

---

### 4. Rate Limiting

**Token Bucket Algorithm (per user/IP):**

```python
def create_url(user_id, long_url):
    key = f"rate_limit:{user_id}"
    tokens = redis.get(key) or 10  # 10 tokens initially
    
    if tokens <= 0:
        raise RateLimitExceeded("Try again in 1 minute")
    
    # Deduct token
    redis.decr(key)
    redis.expire(key, 60)  # Refill after 60 seconds
    
    # Proceed with URL creation
    short_code = generate_short_code(...)
    db.insert(short_code, long_url, user_id)
```

**Limits:**
- 10 URLs/minute per user (free tier)
- 1000 URLs/minute per user (paid tier)

---

## Failure Scenarios & Mitigation

### Scenario 1: Database Primary Failure

**Impact:** Cannot create new URLs, writes fail  
**Mitigation:**
- Promote read replica to primary (automated failover)
- Reads continue from replicas (cached redirects unaffected)
- **RTO**: < 5 minutes, **RPO**: < 1 minute of data loss

### Scenario 2: Cache Failure (Redis Down)

**Impact:** All requests hit database (performance degradation)  
**Mitigation:**
- Database read replicas handle load (12K QPS tolerable)
- Degrade gracefully (slightly higher latency)
- **Auto-restart** Redis, **persistent storage** (RDB/AOF)

### Scenario 3: ID Generation Service Down

**Impact:** Cannot generate new short codes  
**Mitigation:**
- Pre-generate IDs and store in buffer (1M pre-generated IDs)
- Fallback to hash-based generation
- Multi-region ID service (active-active)

---

## Monitoring & Alerts

**Key Metrics:**
```
- Success rate: 99.99% (SLA)
- P99 latency: <100ms
- QPS: 12K reads/sec, 120 writes/sec
- Cache hit rate: >95%
- Database connection pool usage: <80%
```

**Alerts:**
```
- P0: Success rate < 99.9% for 5 min → Page on-call
- P1: P99 latency > 200ms for 10 min → Ticket
- P2: Cache hit rate < 90% → Investigate
```

---

## Cost Estimation (AWS)

```
Compute (EC2, 50× t3.medium):
50 × $30/month = $1,500/month

Database (RDS db.m5.2xlarge + 3 replicas):
4 × $280/month = $1,120/month

Cache (ElastiCache Redis, cache.m5.large):
$180/month

Storage (100 TB RDS):
100 TB × $0.125/GB = $12,500/month

Load Balancer (ALB):
$20/month

Data Transfer (240 Gbps out):
~1 PB/month × $90/TB = $90,000/month (use CDN to reduce!)

CDN (CloudFlare):
~$5,000/month (caches static redirects)

TOTAL: ~$20,000/month (optimized with CDN caching)
```

---

## Interview Tips

**Common Questions:**
1. **"How do you generate short codes?"** → Base62 encoding of counter/Snowflake ID
2. **"What if the database goes down?"** → Read replicas, automated failover
3. **"How do you prevent spam?"** → Rate limiting, CAPTCHA, blacklist malicious domains
4. **"How do you scale to billions of URLs?"** → Sharding, caching, read replicas

**Good Answer Template:**
> "I'd use a counter-based approach with Base62 encoding to generate 7-character short codes (3.5 trillion combinations). Store URLs in PostgreSQL, shard by hash(short_code) for horizontal scaling. Use Redis cache for 95% hit rate on redirects. Implement token bucket rate limiting per user. Monitor success rate (99.99% SLA) and P99 latency (<100ms)."

**Time Allocation:**
- Requirements: 5 min
- Estimation: 5 min
- API + Schema: 5 min
- Architecture: 10 min
- Deep dives (short code gen, sharding, caching): 20 min
- Failure scenarios: 5 min
