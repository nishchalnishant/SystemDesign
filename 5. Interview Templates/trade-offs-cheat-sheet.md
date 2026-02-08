# System Design Trade-offs Cheat Sheet

> **Quick decision guide for senior/staff engineer level system design interviews**

## Core Trade-off Categories

1. [Consistency vs Availability](#consistency-vs-availability)
2. [Latency vs Throughput](#latency-vs-throughput)
3. [SQL vs NoSQL](#sql-vs-nosql)
4. [Synchronous vs Asynchronous](#synchronous-vs-asynchronous)
5. [Push vs Pull](#push-vs-pull)
6. [Horizontal vs Vertical Scaling](#horizontal-vs-vertical-scaling)
7. [Normalization vs Denormalization](#normalization-vs-denormalization)
8. [Caching Strategies](#caching-strategies)
9. [Message Queues](#message-queues)
10. [API Design](#api-design)

---

## Consistency vs Availability

**Decision Framework:**

### Strong Consistency (CP in CAP)
```
Use when:
- Financial transactions (bank transfers)
- Inventory management (prevent overselling)
- Booking systems (seat/hotel reservations)
- Auction systems (highest bidder must be accurate)
- Distributed locks

Technologies:
- Google Spanner
- PostgreSQL (single region)
- Etcd (Raft consensus)
- ZooKeeper
```

### Eventual Consistency (AP in CAP)
```
Use when:
- Social media feeds (stale OK)
- Product catalogs (updates not urgent)
- DNS (propagation delay acceptable)
- Analytics dashboards
- User profiles (own writes visible via session consistency)

Technologies:
- Cassandra
- DynamoDB
- Riak
- CouchDB
```

**Interview Answer Template:**
```
"For [use case], I'd choose [CP/AP] because:
- Consistency requirement: [critical/acceptable to be eventual]
- Availability requirement: [must be always up / OK with downtime]
- User experience: [users expect X]
- Business impact: [lost money / poor UX if inconsistent]"
```

---

## Latency vs Throughput

| Metric | Definition | Optimization |
|--------|------------|--------------|
| **Latency** | Time to complete single request | Caching, CDN, faster algorithm |
| **Throughput** | Requests handled per second | Horizontal scaling, async processing |

**Trade-off Scenarios:**

### Optimize for Latency
```
Use when:
- User-facing APIs (web, mobile)
- Real-time systems (gaming, trading)
- Search engines
- Payment processing

Techniques:
- In-memory caching (Redis, Memcached)
- CDN for static assets
- Database read replicas
- Local caching
```

### Optimize for Throughput
```
Use when:
- Batch processing
- Log aggregation
- Video transcoding
- Data analytics pipelines

Techniques:
- Message queues (decouple producers/consumers)
- Horizontal scaling (more workers)
- Async processing (don't block)
- Batching requests
```

**Example:**
```
Video Upload System:
├── Upload API: Optimize for latency (user waits)
│   → Response <200ms
│   → Upload to S3 directly (pre-signed URL)
│
└── Video Processing: Optimize for throughput
    → Queue jobs (SQS)
    → Process async (100s of workers)
    → User gets notification when done
```

---

## SQL vs NoSQL

### Decision Matrix

| Factor | SQL | NoSQL |
|--------|-----|-------|
| **Data Structure** | Structured, relational | Flexible, nested |
| **Scalability** | Vertical (scale-up) | Horizontal (scale-out) |
| **Transactions** | ACID | Eventually consistent (some support ACID) |
| **Schema** | Fixed, requires migrations | Schema-less, dynamic |
| **Queries** | Complex JOINs | Limited joins, denormalize |
| **Use Case** | Complex queries, relational | High scale, flexible schema |

### SQL: Use When

```
- Structured data (user accounts, orders)
- Complex relationships (many-to-many)
- ACID transactions required
- Reporting & analytics (complex queries)
- Moderate scale (<1M QPS)

Databases:
- PostgreSQL: Advanced features, full-text search, JSONB
- MySQL: Popular, fast reads, replication
- Amazon RDS/Aurora: Managed SQL
```

### NoSQL: Use When

```
- Massive scale (>1M QPS, billions of records)
- Flexible/evolving schema
- High write throughput (logs, events, IoT)
- Key-value lookups (cache, sessions)
- Graph relationships (social networks)

Databases:
- DynamoDB: Key-value, managed, predictable latency
- MongoDB: Document store, flexible schema
- Cassandra: Column-family, high write throughput
- Redis: In-memory, <1ms latency
- Neo4j: Graph database
```

### Polyglot Persistence (Use Both!)

```
E-commerce Example:
├── PostgreSQL: Orders, payments, inventory (ACID critical)
├── MongoDB: Product catalog (flexible schema)
├── Redis: Shopping cart, sessions (fast access)
├── Elasticsearch: Product search (full-text)
└── Cassandra: User activity logs (high write volume)
```

---

## Synchronous vs Asynchronous

### Synchronous

```
Use when:
- User needs immediate response
- Simple, fast operations (<100ms)
- Critical path operations

Example:
User → Login Request → Verify Password → Return JWT
(User waits, must be fast)
```

**Pros:** Simple, immediate feedback  
**Cons:** High latency if blocked, poor scalability

### Asynchronous

```
Use when:
- Long-running operations (>1s)
- Non-critical path
- Can tolerate eventual completion

Example:
User → Upload Video → Queue Job → Return "Processing"
                     ↓
              Background Worker → Transcode
                     ↓
              Send Notification → "Video ready"
```

**Pros:** Better scalability, non-blocking  
**Cons:** Complexity, eventual consistency

### Hybrid Approach

```
Image Upload System:
1. Synchronous: Upload file to S3 (fast, simple)
   Response: {"image_id": "123", "status": "processing"}

2. Asynchronous: Resize, optimize, generate thumbnails
   Queue SNS/SQS → Workers process
   Update DB: status = "ready"

3. Webhook/Polling: Notify client when done
```

---

## Push vs Pull

### Push Model

```
Server → Client (proactive)

Examples:
- WebSockets (chat, real-time updates)
- Server-Sent Events (SSE)
- Mobile push notifications

Pros:
- Real-time updates
- Low latency (immediate)

Cons:
- Persistent connections (resource intensive)
- Complex scaling (stateful)
- Client must be online

Use when:
- Real-time chat
- Live sports scores
- Stock tickers
- Multiplayer games
```

### Pull Model

```
Client → Server (client initiates)

Examples:
- REST APIs
- Polling (check for updates every N seconds)

Pros:
- Stateless, easy to scale
- Works with offline clients
- Simple load balancing

Cons:
- Higher latency (polling interval)
- Wasted requests (polling when no updates)

Use when:
- Email inbox (check every 5 min)
- Social media feeds (pull to refresh)
- News apps (periodic updates)
```

### Hybrid: Long Polling

```
Client → Server: "Give me updates when available"
Server: Holds connection until update (max 30s timeout)
Server → Client: New data OR timeout
Client: Immediately reconnects

Pros:
- Lower latency than regular polling
- Stateless (can load balance)

Cons:
- Still wastes connections
- Not as real-time as WebSockets

Use when:
- Moderate real-time needs
- Can't use WebSockets (firewall restrictions)
```

---

## Horizontal vs Vertical Scaling

### Vertical Scaling (Scale Up)

```
Add more resources to single machine:
- More CPU cores
- More RAM
- Faster disk (SSD → NVMe)

Pros:
- Simple (no code changes)
- No distributed system complexity

Cons:
- Physical limits (max specs)
- Single point of failure
- Expensive (diminishing returns)
-  Downtime during upgrade

Use when:
- Early stage, low traffic
- Monolithic applications
- Single database
```

### Horizontal Scaling (Scale Out)

```
Add more machines:
- Load balancer → Multiple app servers
- Database sharding →  Multiple DB nodes

Pros:
- No upper limit (add infinite nodes)
- Fault tolerant (no single point of failure)
- Cost effective (commodity hardware)

Cons:
- Complexity (distributed systems)
- Data consistency challenges
- Requires stateless design

Use when:
- High traffic (>1M requests/day)
- Need fault tolerance
- Growing rapidly
```

### Real-World Strategy

```
Phase 1 (0-100K users): Vertical
- Single server (app + DB)
- Scale up as needed

Phase 2 (100K-1M users): Hybrid
- Vertical: Larger DB instance
- Horizontal: Multiple app servers behind LB

Phase 3 (>1M users): Horizontal
- App servers: Auto-scaling groups
- Database: Sharding/replication
- Cache: Redis cluster
```

---

## Normalization vs Denormalization

### Normalization (SQL Philosophy)

```
Avoid data redundancy, split into multiple tables

Example: E-commerce
Users Table:
user_id | name       | email
1       | John Doe   | john@example.com

Orders Table:
order_id | user_id | product_id | quantity
101      | 1       | 501        | 2

Products Table:
product_id | name   | price
501        | Laptop | $1000

Pros:
- No redundancy (single source of truth)
- Easy updates (change once)
- Smaller storage

Cons:
- Requires JOINs (slower reads)
- Complex queries

Use when:
- OLTP systems (many writes)
- Data integrity critical
- Storage expensive
```

### Denormalization (NoSQL Philosophy)

```
Duplicate data for faster reads

Example: Same E-commerce
Orders Collection (MongoDB):
{
  "order_id": "101",
  "user": {
    "user_id": "1",
    "name": "John Doe",
    "email": "john@example.com"
  },
  "product": {
    "product_id": "501",
    "name": "Laptop",
    "price": 1000
  },
  "quantity": 2
}

Single document, no JOINs needed!

Pros:
- Faster reads (no JOINs)
- Better for read-heavy workloads

Cons:
- Data redundancy
- Complex updates (update multiple places)
- More storage

Use when:
- Read-heavy systems
- Latency critical (e.g., APIs)
- Storage cheap
```

---

## Caching Strategies

### Cache-Aside (Lazy Loading)

```
Application manages cache explicitly

def get_user(user_id):
    # Check cache first
    user = cache.get(user_id)
    if user:
        return user
    
    # Cache miss → Query DB
    user = db.query(user_id)
    
    # Populate cache
    cache.set(user_id, user, ttl=3600)
    return user

Pros:
- Only cache what's needed
- Cache failures don't break app

Cons:
- Cache miss penalty (extra query)
- Stale data possible

Use when:
- Read-heavy workloads
- Cache misses tolerable
```

### Write-Through Cache

```
Write to cache and DB simultaneously

def update_user(user_id, data):
    db.update(user_id, data)
    cache.set(user_id, data)

Pros:
- Cache always fresh
- No stale reads

Cons:
- Higher write latency
- Cache populated even if not read

Use when:
- Read/write ratio high
- Freshness critical
```

### Write-Behind (Write-Back)

```
Write to cache, async to DB

def update_user(user_id, data):
    cache.set(user_id, data)
    queue.enqueue("update_db", user_id, data)  # Async

Pros:
- Low write latency
- Batch writes to DB

Cons:
- Data loss risk (cache failure before DB write)
- Complex

Use when:
- Write-heavy workloads
- Can tolerate data loss
```

---

## Message Queues

### When to Use Message Queues

```
- Decouple producers from consumers
- Handle traffic spikes (buffering)
- Async processing
- Retry failed operations
- Fan-out (one message → many consumers)

Examples:
-  Order Processing: User creates order → Queue → Payment service, Email service, Inventory service
- Video Upload: Upload → Queue → Transcode, Thumbnail, Notification
```

### SQS vs Kafka vs RabbitMQ

| Feature | SQS | Kafka | RabbitMQ |
|---------|-----|-------|----------|
| **Type** | Simple queue | Distributed log | Message broker |
| **Throughput** | Moderate | Very high | Moderate |
| **Ordering** | FIFO (optional) | Per partition | Yes |
| **Retention** | 14 days max | Configurable (days/TB) | Until consumed |
| **Use Case** | Simple async jobs | Event streaming, logs | Complex routing |

---

## API Design

### REST vs GraphQL vs gRPC

| Factor | REST | GraphQL | gRPC |
|--------|------|---------|------|
| **Protocol** | HTTP/1.1 | HTTP/1.1+ | HTTP/2 |
| **Data Format** | JSON | JSON | Protocol Buffers |
| **Use Case** | Public APIs | Flexible clients | Internal microservices |
| **Performance** | Moderate | Moderate | High (binary) |
| **Caching** | Easy (HTTP) | Hard | No HTTP caching |

---

## Summary: Key Interview Questions

**"Why did you choose...?"**
- SQL vs NoSQL: "I chose SQL because we need ACID transactions for payment processing"
- Sync vs Async: "Async because video processing takes minutes, can't block user"
- Push vs Pull: "WebSockets for chat because real-time is critical"
- Cache Strategy: "Write-through because we can't tolerate stale pricing data"

**Always explain trade-offs!**
