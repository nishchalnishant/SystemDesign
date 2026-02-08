# Caching & CDN Deep Dive

> **Comprehensive guide to caching strategies and content delivery networks for system design**

## Table of Contents

1. [Caching Fundamentals](#caching-fundamentals)
2. [Caching Strategies](#caching-strategies)
3. [Cache Invalidation](#cache-invalidation)
4. [Distributed Caching](#distributed-caching)
5. [Content Delivery Networks (CDN)](#content-delivery-networks-cdn)
6. [Real-World Examples](#real-world-examples)

---

## Caching Fundamentals

### What is Caching?

**Caching** stores frequently accessed data in a fast-access layer (memory) to reduce latency and load on slower backend systems (databases, APIs).

**Key Principle**: **Locality of Reference**
- **Temporal Locality**: Recently accessed data likely to be accessed again
- **Spatial Locality**: Data near recently accessed data likely to be accessed

### Why Cache?

```
Without Cache:
Client → API Server → Database → API Server → Client
Latency: 100ms (DB query)

With Cache:
Client → API Server → Redis (cache hit) → Client
Latency: 1ms (99% reduction!)
```

**Benefits:**
- ✅ **Reduced Latency**: 10-100× faster responses
- ✅ **Lower Database Load**: Fewer queries to primary DB
- ✅ **Cost Savings**: Cheaper to serve from cache than compute
- ✅ **Improved Scalability**: Handle traffic spikes with cached data

**Costs:**
- ❌ **Stale Data**: Cache may return outdated information
- ❌ **Complexity**: Cache invalidation, consistency challenges
- ❌ **Memory Cost**: RAM more expensive than disk
- ❌ **Cold Start**: Cache misses on first request

---

## Caching Strategies

### 1. Cache-Aside (Lazy Loading)

**How it works:**
```python
def get_user(user_id):
    # 1. Check cache first
    user = cache.get(f"user:{user_id}")
    if user:
        return user  # Cache HIT
    
    # 2. Cache MISS → Query database
    user = db.query("SELECT * FROM users WHERE id = ?", user_id)
    
    # 3. Populate cache
    cache.set(f"user:{user_id}", user, ttl=3600)  # 1 hour TTL
    
    return user
```

**Characteristics:**
- Application controls cache  logic
- Cache only populated on read (lazy)
- Cache failures don't break application

**Pros:**
- ✅ Only caches data that's actually accessed
- ✅ Resilient (cache failure → slower but still works)

**Cons:**
- ❌ Cache miss penalty (extra latency on first request)
- ❌ Stale data possible (until TTL expires)

**Use When:**
- Read-heavy workloads
- Cache misses are acceptable (not latency-critical)

---

### 2. Read-Through Cache

**How it works:**
```
Client → Cache → (if miss) → Database → Cache → Client
```

Cache library automatically handles DB query on miss.

**Difference from Cache-Aside:**
- Cache is responsible for loading data (not application)
- Cleaner application code

**Example (Redis with read-through):**
```python
# Cache library handles miss automatically
user = cache.get("user:123")  # If miss, fetches from DB
```

**Pros:**
- ✅ Simpler application code
- ✅ Consistent cache loading logic

**Cons:**
- ❌ Tighter coupling (cache must know DB structure)
- ❌ Still has cache miss penalty

---

### 3. Write-Through Cache

**How it works:**
```python
def update_user(user_id, data):
    # 1. Write to database
    db.update("UPDATE users SET name = ? WHERE id = ?", data.name, user_id)
    
    # 2. Synchronously update cache
    cache.set(f"user:{user_id}", data, ttl=3600)
    
    return success
```

**Characteristics:**
- Every write goes to **both** cache and database
- Cache is always consistent with database

**Pros:**
- ✅ No stale reads (cache always fresh)
- ✅ Fast subsequent reads

**Cons:**
- ❌ Higher write latency (2 writes instead of 1)
- ❌ Cache pollution (write data that's never read)

**Use When:**
- Read/write ratio is high (same data written then read many times)
- Consistency critical (financial data, user profiles)

---

### 4. Write-Behind (Write-Back) Cache

**How it works:**
```python
def update_user(user_id, data):
    # 1. Write to cache only (fast!)
    cache.set(f"user:{user_id}", data)
    
    # 2. Queue database write (async)
    queue.enqueue("db_write", user_id, data)
    
    return success  # Respond immediately

# Background worker
def db_worker():
    task = queue.dequeue()
    db.update(task.user_id, task.data)
```

**Characteristics:**
- Writes go to cache first
- Database updated asynchronously (batched)

**Pros:**
- ✅ Very low write latency (cache is fast)
- ✅ Can batch writes (write 1000 updates at once)

**Cons:**
- ❌ **Data loss risk** (if cache crashes before DB write)
- ❌ Complex (need queue, workers, retry logic)

**Use When:**
- Write-heavy workloads (logging, analytics)
- Can tolerate some data loss (non-critical data)
- Want to batch writes for efficiency

---

### 5. Refresh-Ahead (Predictive Refresh)

**How it works:**
```python
# Cache monitors access patterns
# Before TTL expires, proactively refresh hot keys
def background_refresh():
    hot_keys = cache.get_hot_keys()  # e.g., "homepage_data"
    for key in hot_keys:
        if cache.ttl(key) < 300:  # 5 min before expiry
            # Proactively refresh
            data = db.query(key)
            cache.set(key, data, ttl=3600)
```

**Pros:**
- ✅ No cache miss penalty for hot data
- ✅ Consistent performance

**Cons:**
- ❌ Complex (predict which keys to refresh)
- ❌ Wasted refreshes if data not accessed

**Use When:**
- Predictable access patterns (homepage, trending data)
- Cache misses are unacceptable (sub-10ms latency SLA)

---

## Cache Invalidation

> "There are only two hard things in Computer Science: cache invalidation and naming things." — Phil Karlton

### Strategies

#### 1. TTL (Time-To-Live)

```python
# Set expiration time
cache.set("user:123", user_data, ttl=3600)  # Expire after 1 hour
```

**Pros:** Simple, automatic cleanup  
**Cons:** May serve stale data until TTL expires

**Best for:** Data that changes slowly (user profiles, product catalogs)

#### 2. Event-Based Invalidation

```python
def update_user(user_id, data):
    db.update(user_id, data)
    # Invalidate on write
    cache.delete(f"user:{user_id}")
```

**Pros:** No stale data (immediate invalidation)  
**Cons:** More complex, must track all dependencies

**Best for:** Critical data (inventory, pricing)

#### 3. Versioning

```python
# Include version in cache key
version = 1
cache.set(f"user:123:v{version}", user_data)

# On schema change, increment version
version = 2  # Old cache keys auto-expire via TTL
```

**Pros:** Smooth rollouts, no invalidation storms  
**Cons:** Storage overhead (multiple versions)

---

## Distributed Caching

### Single-Node Cache (e.g., Redis single instance)

```
Client → Redis (single instance)
```

**Pros:** Simple, low latency  
**Cons:** Single point of failure, limited by single machine memory

### Distributed Cache Cluster

**Example: Redis Cluster**

```
Clients
├── Cache Node 1 (hash slots 0-5460)
├── Cache Node 2 (hash slots 5461-10922)
└── Cache Node 3 (hash slots 10923-16383)
```

**Sharding Strategy (Consistent Hashing):**
```python
slot = CRC16(key) mod 16384
node = slot_to_node_mapping[slot]
```

**Pros:**
- Horizontal scaling (add more nodes)
- High availability (replicas)

**Cons:**
- Complexity (cluster management)
- Network overhead (cross-node requests)

### Cache Replication

```
Write to Primary → Replicate to Secondary (async)
Reads can go to any replica
```

**Pros:** High availability, read scaling  
**Cons:** Replication lag (eventual consistency)

---

## Eviction Policies

When cache is full, which item to remove?

| Policy | Description | Use Case |
|--------|-------------|----------|
| **LRU (Least Recently Used)** | Evict oldest accessed item | General purpose |
| **LFU (Least Frequently Used)** | Evict least accessed item | Hot data patterns |
| **FIFO (First In First Out**) | Evict oldest inserted item | Simple, predictable |
| **Random** | Evict random item | Fast, low overhead |
| **TTL** | Evict expired items first | Time-sensitive data |

**Most Common: LRU**
```
Cache: [A, B, C, D] (capacity = 4)
Access: E
Result: Evict least recently used (e.g., A)
Cache: [B, C, D, E]
```

---

## Content Delivery Networks (CDN)

### What is a CDN?

**CDN** is a geographically distributed network of caching servers (edge locations) that serve static content closer to users.

```
User in India → CDN Edge (Mumbai) → Serve fast!
User in USA → CDN Edge (Virginia) → Serve fast!
Origin Server (California) → Only hit on cache miss
```

### How CDN Works

**1. Push CDN (Proactive)**
```
You upload assets to CDN
CDN replicates to all edge locations
Best for: Predictable, static content (images, videos)
```

**2. Pull CDN (Lazy)**
```
User requests asset → CDN edge checks local cache
If miss → Fetch from origin, cache, serve
Best for: Dynamic content, unpredictable access
```

### CDN Caching

**Cache-Control Headers:**
```http
Cache-Control: public, max-age=31536000, immutable
# Cache for 1 year, never revalidate (perfect for versioned assets)

Cache-Control: public, max-age=3600, must-revalidate
# Cache for 1 hour, then revalidate with origin

Cache-Control: no-cache
# Always revalidate (CDN can cache but must check origin)

Cache-Control: no-store
# Never cache (sensitive data)
```

### CDN Invalidation

**Problem:** Asset updated, but CDN still serves old version

**Solutions:**

**1. Versioned URLs (Best Practice)**
```
Old: https://cdn.example.com/app.js
New: https://cdn.example.com/app.v2.js (or app.abc123.js)
```

**2. Cache Purge (Manual)**
```
CDN API: purge("/images/logo.png")
→ Invalidates across all edge locations
```

**3. TTL Management**
```
Short TTL for frequently changing content
Long TTL for versioned/immutable assets
```

---

## Real-World Examples

### Example 1: Twitter Timeline Caching

**Architecture:**
```
User requests timeline
└→ API Server
   ├→ Redis Cache (key: user:123:timeline)
   │   └→ Cache HIT: Return cached timeline (2ms)
   │
   └→ Cache MISS:
      ├→ Query database (followers, posts)
      ├→ Compute timeline
      ├→ Cache result (TTL: 5 min)
      └→ Return timeline (200ms)
```

**Strategy:** Cache-aside with short TTL (stale feed acceptable for 5 min)

### Example 2: YouTube Video Streaming

**Architecture:**
```
User watches video
└→ CDN Edge (nearest location)
   ├→ Video chunks cached? → Serve from edge (5ms latency)
   └→ Cache MISS → Fetch from origin, cache, serve
```

**Strategy:** Pull CDN with long TTL (videos rarely change)

### Example 3: E-commerce Product Catalog

**Architecture:**
```
Update product price
├→ Database: UPDATE products SET price = 99.99
├→ Invalidate cache: DELETE cache:product:123
└→ Next read: Cache MISS → Fresh data from DB

Read product
└→ Cache-aside: Check cache → DB → Cache
```

**Strategy:** Event-based invalidation (pricing must be accurate)

---

## Decision Matrix

| Scenario | Caching Strategy | Eviction | TTL |
|----------|------------------|----------|-----|
| **User profiles** | Cache-aside | LRU | 1 hour |
| **Product catalog (read-heavy)** | Read-through | LRU | 10 min |
| **Real-time analytics** | Write-behind | LFU | No TTL |
| **Static assets (images, JS)** | CDN (Pull) | LRU | 1 year |
| **Session data** | Write-through | TTL | 30 min |
| **Leaderboard (hot data)** | Refresh-ahead | LFU | 5 min |

---

## Interview Tips

**When asked about caching:**

1. **Clarify requirements:**
   - Read vs write ratio?
   - Staleness tolerance?
   - Latency SLA?

2. **Discuss trade-offs:**
   - Write-through: Consistency vs Latency
   - Cache-aside: Simplicity vs Staleness

3. **Mention eviction:**
   - "I'd use LRU eviction for general purpose"
   - "TTL handles automatic cleanup"

4. **Consider failure modes:**
   - Cache failure → Fallback to DB
   - Thundering herd → Cache stampede protection

**Example Answer:**
> "For user profiles, I'd use **cache-aside** with Redis. On read, check cache first (sub-ms latency). On miss, query PostgreSQL, cache result with **1-hour TTL**. Use **LRU eviction** when cache is full. On user update, **invalidate cache** to prevent stale data. This balances simplicity, performance, and consistency."
