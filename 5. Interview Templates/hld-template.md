# HLD Interview Template

> **Complete step-by-step approach for High-Level Design interviews**  
> Optimized for 45-60 minute interviews at senior/staff engineer level

## Interview Flow Timeline

| Phase | Time | Activity |
|-------|------|----------|
| **1. Requirements** | 0-10 min | Clarify functional & non-functional requirements |
| **2. Estimation** | 10-15 min | Back-of-envelope capacity planning |
| **3. API Design** | 15-20 min | Define external interfaces |
| **4. Data Model** | 20-25 min | Database schema & storage strategy |
| **5. High-Level Design** | 25-40 min | Component diagram, data flow |
| **6. Deep Dives** | 40-55 min | Scale, failures, trade-offs |
| **7. Wrap-up** | 55-60 min | Summary, Q&A, next steps |

---

## Phase 1: Requirements Gathering (0-10 min)

### Goals
- ✅ Align on scope with interviewer
- ✅ Identify constraints and scale
- ✅ Clarify assumptions
- ❌ **Don't**: Jump into design before understanding requirements

### Functional Requirements

**Template Questions:**
```
1. What are the core features? (Top 3-5)
2. Who are the users? (B2C, B2B, internal)
3. What are the critical user journeys?
4. What can we deprioritize for MVP?
```

**Example: URL Shortener**
```
✅ Must-have:
  - Generate short URL from long URL
  - Redirect short URL to long URL
  - URLs never expire

Should-have:
  - Custom aliases
  - Analytics (click tracking)

Out-of-scope (for now):
  - User accounts
  - URL expiration
  - Rate limiting per user
```

### Non-Functional Requirements

**Use PASS-R Framework:**

**P**erformance
- Latency targets? (e.g., P99 < 200ms)
- Throughput? (e.g., 10K QPS)

**A**vailability
- Uptime SLA? (99.9% = 8.7 hours/year downtime)
- Acceptable downtime window?

**S**calability
- Expected growth? (10M users today → 100M in 1 year?)
- Peak vs average load? (3x spike during events?)

**S**ecurity
- Authentication needed?
- Data privacy requirements? (GDPR, PII)
- Rate limiting? DDoS protection?

**R**eliability
- Data durability? (can we lose data?)
- Consistency requirements? (strong vs eventual)

**Example:**
```
URL Shortener:
├── Performance: P99 latency < 100ms
├── Availability: 99.99% (52 minutes/year downtime)
├── Scale: 100M URLs created/month, 10B redirects/month
├── Security: Public API (no auth for reads, rate limit writes)
└── Reliability: Never lose URLs, eventual consistency OK
```

---

## Phase 2: Capacity Estimation (10-15 min)

### Goals
- ✅ Estimate storage, bandwidth, QPS
- ✅ Demonstrate quantitative thinking
- ❌ **Don't**: Spend too long on precise calculations

### Key Numbers to Remember

```
TIME CONVERSIONS:
1 day = 86,400 seconds (~100K)
1 million requests/day ≈ 12 requests/second
1 billion requests/day ≈ 12K requests/second

STORAGE:
1 char = 1 byte
1 KB = 1,000 bytes
1 MB = 1,000 KB = 1 million bytes
1 GB = 1,000 MB
1 TB = 1,000 GB

LATENCY:
L1 cache: 0.5 ns
RAM: 100 ns
SSD: 150 μs (microseconds)
Network (same datacenter): 0.5 ms
Network (cross-region): 50-100 ms
Disk seek: 10 ms
```

### Estimation Template

**1. Traffic Estimates**
```
Given:
- 100M new URLs/month
- 100:1 read-to-write ratio (typical for URL shorteners)

Writes (creates):
100M URLs/month = 100M / (30 days × 86,400 s/day)
                ≈ 40 URLs/second (average)
Peak (3x): 120 writes/second

Reads (redirects):
100:1 ratio → 40 × 100 = 4,000 reads/second (average)
Peak (3x): 12,000 reads/second
```

**2. Storage Estimates**
```
Per URL:
├── short_url: 7 chars × 1 byte = 7 B
├── long_url: 2 KB (average)
├── metadata: 1 KB (user_id, created_at, etc.)
└── Total: ~3 KB per URL

Total Storage (5 years):
100M URLs/month × 12 months/year × 5 years = 6B URLs
6B URLs × 3 KB = 18 TB
With replication (3x): 54 TB
With indexes (2x): 108 TB
```

**3. Bandwidth Estimates**
```
Writes:
120 writes/sec × 3 KB = 360 KB/s ≈ 3 Mbps

Reads:
12,000 reads/sec × 3 KB = 36 MB/s ≈ 288 Mbps
```

**4. Cache Requirements (80/20 Rule)**
```
Assumption: 20% of URLs generate 80% of traffic

Daily redirect traffic:
12,000 reads/sec × 86,400 sec/day = 1B redirects/day

Unique URLs (20% of all):
6B total URLs × 20% = 1.2B unique URLs in cache
1.2B × 3 KB = 3.6 TB cache (impractical!)

Further optimization (cache hot URLs from today):
100:1 read:write ratio suggests caching recent URLs
Cache 1 day of URLs: 100M/month ÷ 30 = 3.3M/day
3.3M × 20% (hot URLs) = 660K URLs
660K × 3 KB = 2 GB cache (feasible!)
```

---

## Phase 3: API Design (15-20 min)

### REST API Template

**Naming Conventions:**
- Use nouns (not verbs): `/users`, not `/getUsers`
- Plural for collections: `/orders`, not `/order`
- Hierarchical: `/users/123/orders`

**Example: URL Shortener**

```
POST /api/v1/urls
Request:
{
  "long_url": "https://example.com/very/long/url",
  "custom_alias": "mylink" // optional
}

Response: 201 Created
{
  "short_url": "https://short.ly/abc1234",
  "long_url": "https://example.com/very/long/url",
  "created_at": "2026-02-08T10:00:00Z",
  "id": "abc1234"
}

GET /api/v1/urls/{short_code}
Response: 301 Moved Permanently
Location: https://example.com/very/long/url

DELETE /api/v1/urls/{short_code}
Response: 204 No Content
```

**Standard Error Responses:**
```
400 Bad Request: Invalid input
401 Unauthorized: Missing/invalid auth token
403 Forbidden: Not allowed to access
404 Not Found: Resource doesn't exist
409 Conflict: Resource already exists
429 Too Many Requests: Rate limit exceeded
500 Internal Server Error: Server-side error
503 Service Unavailable: Temporary outage
```

---

## Phase 4: Data Model (20-25 min)

### Database Schema

**URL Shortener Example:**

**SQL Schema:**
```sql
CREATE TABLE urls (
    id VARCHAR(7) PRIMARY KEY,  -- short code
    long_url TEXT NOT NULL,
    user_id BIGINT,  -- NULL for anonymous
    created_at TIMESTAMP DEFAULT NOW(),
    expires_at TIMESTAMP,  -- NULL for never
    click_count BIGINT DEFAULT 0,
    INDEX idx_user_id (user_id),
    INDEX idx_created_at (created_at)
);

CREATE TABLE analytics (
    id BIGSERIAL PRIMARY KEY,
    short_url_id VARCHAR(7) REFERENCES urls(id),
    clicked_at TIMESTAMP DEFAULT NOW(),
    ip_address VARCHAR(45),
    user_agent TEXT,
    referer TEXT
);
```

**NoSQL Schema (DynamoDB):**
```
Table: URLs
Partition Key: short_code (String)
Attributes:
  - long_url (String)
  - user_id (String)
  - created_at (Number) // Unix timestamp
  - ttl (Number) // For expiration

GSI: user_id-created_at-index
  - Partition Key: user_id
  - Sort Key: created_at
  - Purpose: Query user's URLs
```

### Indexing Strategy

```
B-Tree Index (SQL):
  - id (primary key): Fast lookups
  - user_id: Filter by user
  - created_at: Time-range queries

Hash Index (NoSQL):
  - Partition key: Exact match lookups
  - GSI: Secondary access patterns
```

---

## Phase 5: High-Level Design (25-40 min)

### Component Diagram

**URL Shortener Architecture:**

```
┌─────────┐
│ Client  │
└────┬────┘
     │
     ▼
┌─────────────────┐
│   CDN/CloudFlare│ (DDoS protection, DNS)
└─────────┬───────┘
          │
          ▼
┌─────────────────┐
│  Load Balancer  │ (ALB, Nginx)
└─────────┬───────┘
          │
     ┌────┴────┐
     │         │
     ▼         ▼
┌─────────┐ ┌─────────┐
│  App    │ │  App    │ (Stateless, auto-scaling)
│ Server  │ │ Server  │
└────┬────┘ └────┬────┘
     │           │
     ├───────────┤
     │           │
     ▼           ▼
┌──────────────────┐     ┌──────────────┐
│  Redis Cache     │────▶│ Write-Through│
│  (Read-Through)  │     │   Updates    │
└──────────────────┘     └───────┬──────┘
     │                            │
     │ Cache Miss                 │
     ▼                            ▼
┌───────────────────────────────────┐
│   PostgreSQL (Primary)            │
│   - URLs table                    │
│   - Analytics table               │
└───────────────┬───────────────────┘
                │
                ▼
 ┌──────────────────────────┐
 │  Read Replicas (×3)      │ (For analytics queries)
 └──────────────────────────┘
```

### Data Flow

**Write Flow (Create Short URL):**
```
1. Client → POST /api/v1/urls {"long_url": "..."}
2. Load Balancer → App Server
3. App Server:
   a. Generate unique short code
   b. Check if exists in DB (handle collision)
   c. INSERT into PostgreSQL
   d. Update cache (write-through)
4. Return {"short_url": "https://short.ly/abc1234"}
```

**Read Flow (Redirect):**
```
1. Client → GET /abc1234
2. Load Balancer → App Server
3. App Server:
   a. Check Redis cache
   b. If HIT → Return long_url (99% of requests)
   c. If MISS → Query PostgreSQL
   d. Update cache
4. Return 301 redirect to long_url
```

---

## Phase 6: Deep Dives (40-55 min)

### Common Deep Dive Topics

**1. Short Code Generation**

**Base62 Encoding:**
```
Characters: [a-zA-Z0-9] = 62 chars
7-char short code: 62^7 = 3.5 trillion combinations

Algorithm:
1. Use auto-incrementing counter (e.g., PostgreSQL SERIAL)
2. Encode counter to base62

Example: ID=12345 → base62 → "dnh"
```

**Collision Handling:**
```
Option A: Check-and-retry
while True:
  short_code = generate_random()
  if not exists(short_code):
    insert(short_code, long_url)
    break

Option B: Use counter + distributed ID generator (Snowflake)
No collisions, but sequential (predictable)
```

**2. Scaling the Database**

**Sharding Strategy:**
```
Shard by: hash(short_code) % num_shards
├── Shard 0: short_codes [a-h]*
├── Shard 1: short_codes [i-p]*
└── Shard 2: short_codes [q-z]*

Pros: Evenly distributed writes
Cons: Cross-shard queries difficult (e.g., get all user's URLs)
```

**Read Replicas:**
```
Primary: Handles all writes
Replicas (×3): Handle read queries (analytics)
Replication lag: ~100ms (acceptable for analytics)
```

**3. Caching Strategy**

**Cache-Aside (Lazy Loading):**
```
def get_long_url(short_code):
    # Check cache
    cached = redis.get(short_code)
    if cached:
        return cached
    
    # Cache miss → query DB
    long_url = db.query(short_code)
    
    # Update cache
    redis.set(short_code, long_url, ttl=86400)  # 1 day
    return long_url
```

**Cache Eviction:**
- LRU (Least Recently Used)
- TTL (Time-To-Live): 24 hours

---

*[Continued with more sections: Failure Scenarios, Monitoring, Security...]*
