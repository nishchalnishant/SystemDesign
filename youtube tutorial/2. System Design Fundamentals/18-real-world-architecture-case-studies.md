# Real-World Architecture Case Studies

> **Source**: Videos #28, #31, #32, #34, #54, #95 from the playlist
> - Amazon Prime Video Ditches AWS Serverless, Saves 90%
> - How Discord Stores TRILLIONS of Messages
> - Uncovering Stack Overflow's Shocking Architecture
> - Demystifying the Unusual Evolution of the Netflix API Architecture
> - How Disney Hotstar Captures One Billion Emojis!
> - Trillions of Web Pages: Where Does Google Store Them?

---

## Amazon Prime Video: Serverless → Monolith

### The Problem
- Video quality monitoring used AWS Step Functions + Lambda
- At scale: too many state transitions, too expensive
- Lambda cold starts caused latency issues

### The Solution
- Moved to a **monolith** running on ECS (single process)
- Reduced cost by **90%**
- Better performance (in-process communication)

### Lesson
- **Microservices aren't always the answer**
- Distributed overhead can exceed benefits at certain scales
- Right-size your architecture for the problem

---

## Discord: Storing Trillions of Messages

### Evolution
- **v1**: MongoDB (single replica set) — worked for small scale
- **v2**: Migrated to **Cassandra** — distributed, write-optimized
- **v3**: Migrated to **ScyllaDB** (C++ rewrite of Cassandra)

### Why ScyllaDB?
- Cassandra had **GC pauses** (Java) causing latency spikes
- ScyllaDB: Same API, no GC (C++), better tail latency
- **Shard-per-core** architecture for predictable performance

### Key Design
- Messages partitioned by `(channel_id, bucket)` — bucket = time window
- Avoids unbounded partition growth
- Read path: Query latest bucket first

---

## Stack Overflow: Monolith at Scale

### The Architecture
- **9 web servers** handle the entire site
- **Single SQL Server** primary (with replicas)
- **Redis** for caching (256 GB across 2 servers)
- **Elasticsearch** for search
- **HAProxy** for load balancing

### Why It Works
- **Aggressive caching**: Most pages served from cache
- **Optimized SQL**: Carefully tuned queries, minimal ORM
- **.NET monolith**: Fast, well-optimized
- Handles **~6,000 requests/second** with this setup

### Lesson
- You don't always need microservices
- Careful optimization can take a monolith very far
- Right tool for the right scale

---

## Netflix API Architecture Evolution

### v1: Monolithic API
- Single API serving all clients (web, mobile, TV)

### v2: BFF (Backend for Frontend)
- Separate API layer per client type
- Each client gets optimized responses

### v3: Federated GraphQL (Current)
- **API Gateway** (Zuul) for routing
- **GraphQL Federation**: Each team owns their domain's GraphQL schema
- Unified graph across all services
- Teams can evolve APIs independently

### Key Components
- **Zuul**: Edge gateway (routing, filtering, auth)
- **Eureka**: Service discovery
- **Hystrix**: Circuit breaker (now Resilience4j)
- **Ribbon**: Client-side load balancing

---

## Disney Hotstar: One Billion Emojis

### The Challenge
- During live cricket matches: **25+ million concurrent users**
- Users sending emojis in real-time — billions of events

### Architecture
- **Kafka** for ingesting emoji events
- **Spark Streaming** for real-time aggregation
- **Time-windowed aggregation**: Count emojis per 5-second window
- **Push to clients** via WebSocket

### Key Design Decisions
- **Don't store individual events** — aggregate immediately
- **Client-side batching**: Batch emojis before sending
- **Approximate counts are OK** — exact counts not needed for emojis

---

## Google: Storing Trillions of Web Pages

### Key Technologies
- **GFS** (Google File System) / **Colossus**: Distributed file storage
- **Bigtable**: Wide-column store for web index
- **MapReduce** / **Spanner**: Processing and global database
- **Protocol Buffers**: Efficient serialization

### Scale
- Crawls and indexes **trillions of web pages**
- Stores in compressed, indexed format
- Custom hardware and software at every level
