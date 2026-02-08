# Complete system desing

*   Most important topics of system design

    * Database fundamentals
    * Data serialisation framework
    * Replication
    * sharding
    * Batch processing
    * Stream processing
    * Other types of storage
    * Caching
    * Load balanding



***

## Systems Design in an Hour

***

## 1. Performing a Systems Design Interview (02:01)

The video emphasizes that the interview is a conversation, not a monologue. Use this framework:

* Step 1: Clarify Requirements: Define functional requirements (what it does) and non-functional requirements (scalability, availability, etc.).
* Step 2: Capacity Estimation: Calculate DAU (Daily Active Users), QPS (Queries Per Second), and storage needs (e.g., "We need 500TB over 5 years").
* Step 3: API Design: Define the endpoints (e.g., `POST /v1/tweet`).
* Step 4: Database Schema: Define the core tables and relationships.
* Step 5: High-Level Design: Draw the "boxes and arrows" (Client $$ $\rightarrow$ $$ LB $$ $\rightarrow$ $$ Server $$ $\rightarrow$ $$ DB).
* Step 6: Deep Dive: Address bottlenecks like sharding, caching, and replication.

***

## 2. Database Fundamentals (04:02)

This detailed breakdown of Database Fundamentals is based on Jordan’s "Systems Design in an Hour" video, supplemented with core concepts from _Designing Data-Intensive Applications_ (DDIA) and industry standard practices.

***

### SQL vs. NoSQL (Relational vs. Non-Relational)

The fundamental choice in any system design is whether to use a structured relational model or a flexible non-relational model.

* Relational (SQL):
  * Data Model: Tables with fixed schemas and relationships defined by primary/foreign keys.
  * Best for: Complex joins, multi-row transactions, and applications where data integrity is paramount (e.g., banking).
  * Scaling: Primarily Vertical (bigger machines). Horizontal scaling (sharding) is possible but highly complex.
  * Examples: PostgreSQL, MySQL, Oracle.
* Non-Relational (NoSQL):
  * Key-Value: Simplest form. O(1) lookups. Great for session data or caching (e.g., Redis, DynamoDB).
  * Document: Stores data as JSON/BSON. Flexible schema allows developers to add fields without migrations (e.g., MongoDB, CouchDB).
  * Graph: Focuses on relationships between entities. Ideal for social networks, fraud detection, or recommendation engines (e.g., Neo4j).
  * Wide-Column: Optimized for massive scale and high-volume writes. Data is stored in columns but grouped by rows (e.g., Cassandra, HBase).

***

### Consistency Models: ACID vs. BASE

This defines how the database handles transactions and availability.

* ACID (The Gold Standard for SQL):
  * Atomicity: "All or nothing." If one part of a transaction fails, the whole thing is rolled back.
  * Consistency: Data must obey all rules (constraints, triggers, etc.) before and after a transaction.
  * Isolation: Transactions occurring simultaneously don't interfere with each other.
  * Durability: Once a transaction is committed, it stays committed even in a power failure.
* BASE (The Trade-off for NoSQL):
  * Basically Available: The system guarantees a response (though it might be stale).
  * Soft State: The state of the system may change over time, even without input (due to eventual consistency).
  * Eventually Consistent: Given enough time, all replicas will converge to the same state.

***

### Storage Engines: LSM-Trees vs. B-Trees

This is a "deep dive" topic frequently used in interviews to distinguish junior and senior candidates.

* B-Trees (Read-Optimized):
  * Mechanism: Breaks the database into fixed-size "pages" (usually 4KB). It uses a balanced tree structure to find data.
  * Pros: Very fast for reads and range queries because data is sorted and indexed in a predictable tree height.
  * Cons: Writes can be slow because a single write might require multiple page updates and rebalancing the tree.
  * Default for: Almost all Relational Databases (PostgreSQL, MySQL).
* LSM-Trees (Write-Optimized):
  * The Flow:
    1. Memtable: Writes first go to an in-memory balanced tree (Fast).
    2. WAL (Write-Ahead Log): Simultaneously written to a log on disk for crash recovery.
    3. SSTables: When the Memtable gets full, it's flushed to disk as a "Sorted String Table." These files are immutable.
    4. Compaction: Background processes merge SSTables and discard old or deleted data to keep read performance from degrading.
  * Pros: Extremely high write throughput because writes are sequential disk appends.
  * Cons: Reads can be slower because the database might have to check multiple SSTables to find the latest version of a key.
  * Used by: Cassandra, RocksDB, LevelDB.

***

### Storage Layouts: Row-Oriented vs. Column-Oriented

Where is the data physically placed on the disk?

* Row-Oriented (OLTP - Online Transactional Processing):
  * Logic: Stores all data for a single row together (Row 1 Col A, Col B, Col C... then Row 2).
  * Best for: Frequent small reads/writes of entire records (e.g., "Get all details for User ID 123").
  * Inefficiency: If you only want the "Average Age" of 1 million users, the DB must still scan every single byte of every user record.
* Column-Oriented (OLAP - Online Analytical Processing):
  * Logic: Stores all values for a single column together (All "Ages" stored in one block, all "Names" in another).
  * Best for: Big Data analytics and aggregations (SUM, AVG, COUNT).
  * Pros: Massive compression (storing 1,000 "Male" strings is easier than storing 1,000 diverse rows) and "projection pushdown" (only reading the columns you need).
  * Examples: Amazon Redshift, Google BigQuery, Snowflake.

***

### Summary Checklist for Interviews:

* Use SQL if: You need ACID, complex joins, or have structured data that doesn't scale past one huge machine.
* Use NoSQL if: You need high availability, have unstructured data, or need to scale horizontally to millions of users.
* Use LSM-Trees if: Your application is write-heavy (e.g., logging, sensor data).
* Use Columnar if: You are building a data warehouse for business intelligence.

***

## 3. Data Serialization (14:26)

In the video "Systems Design in an Hour," Jordan explains that data serialization is the "language" your services use to talk to each other over the wire. While JSON is the industry default, it is often suboptimal for high-performance distributed systems.

Following the structure of the video and supplemental industry standards (like those in _Designing Data-Intensive Applications_), here are detailed notes on Data Serialization.

***

### The Core Concept: Serialization vs. Deserialization

* Serialization: The process of converting an in-memory object (like a Python dictionary or a Java class) into a byte stream (1s and 0s) that can be sent over a network or stored on disk.
* Deserialization: The reverse process—taking that byte stream and reconstructing the original object so the receiving service can use it.
* Why it matters: Different services might be written in different languages (e.g., a Go backend talking to a Python data service). Serialization provides a neutral format they both understand.

***

### Textual Formats (The "Human-Readable" Group)

These are easy to debug because humans can read them, but they come with a performance cost.

* JSON (JavaScript Object Notation):
  * Pros: Extremely popular, built-in support in browsers, easy to read/edit manually.
  * Cons: Verbose (repeats key names like `"user_id"` in every single record), slow to parse, no native support for binary data (requires Base64 encoding which increases size by \~33%).
  * Use Case: Public APIs, web-to-server communication.
* XML:
  * Pros: Very structured, supports complex schemas (XSD).
  * Cons: Even more verbose than JSON (due to opening/closing tags `<tag></tag>`), complex to parse.
  * Use Case: Legacy systems, soap-based web services.

***

### Binary Formats (The "Performance" Group)

These formats convert data into a compact binary stream. They are much smaller and faster but require a Schema (a contract) to work.

* Protocol Buffers (Protobuf - developed by Google):
  * How it works: You define your data in a `.proto` file. It uses field tags (numbers like `1`, `2`, `3`) instead of field names (like "username") to identify data, saving massive amounts of space.
  * Pros: Extremely compact, very fast serialization, strongly typed.
  * Cons: Not human-readable (requires a tool to view), requires a code-generation step.
  * Use Case: Internal microservices (gRPC), high-throughput systems.
* Apache Thrift (developed by Facebook):
  * Similar to Protobuf but includes its own RPC (Remote Procedure Call) framework. It supports a wide variety of languages and is used for internal communication between services.
* Apache Avro:
  * Unique Feature: It doesn't use field tags. Instead, it sends the schema along with the data (or stores it in a registry).
  * Pros: Best for "Schema Evolution" (easily handles changing data structures over time).
  * Use Case: Big Data pipelines (Kafka, Hadoop), where you store data for a long time and the schema might change.

***

### Critical Concept: Schema Evolution

In a real system, you cannot update all your services at the same time. You need your data format to handle "versioning."

* Backward Compatibility: New code can read data written by old code.
  * _Example:_ You add a new field. The new code knows the field is optional and provides a default value for old records.
* Forward Compatibility: Old code can read data written by new code.
  * _Example:_ You add a field to a "Tweet" object. Old servers that don't know about this field yet will simply ignore the extra bytes without crashing.
* The Golden Rule: To maintain compatibility in binary formats (like Protobuf), never reuse or change a field tag number once it has been assigned.

***

#### 5. Summary Comparison Table

| **Feature**  | **JSON**                | **Protobuf / Thrift**  | **Avro**              |
| ------------ | ----------------------- | ---------------------- | --------------------- |
| Format       | Text (Human-readable)   | Binary                 | Binary                |
| Payload Size | Large (Verbose)         | Small (Compressed)     | Very Small            |
| Speed        | Slow (Parsing overhead) | Very Fast              | Fast                  |
| Schema       | Optional (Schemaless)   | Required (IDL)         | Required (JSON-based) |
| Best For     | Public APIs             | Internal Microservices | Big Data / Kafka      |

***

#### Interview Tip:

If asked "Which format should we use?", the senior answer is: _"Use JSON for public-facing APIs where ease of use for 3rd party developers is key. Use a binary format like Protobuf for internal microservices to save on bandwidth and CPU costs, especially at scale."_

***

## 4. Replication (15:47)

These notes combine the key concepts from Jordan’s "Systems Design in an Hour" video (starting at 15:47) with deep-dive insights from _Designing Data-Intensive Applications_ (DDIA) and standard distributed systems theory.

***

### The Goal of Replication

Replication means keeping a copy of the same data on multiple machines connected via a network. We do this for three main reasons:

* High Availability: Keeping the system running even if one machine (or a whole datacenter) goes down.
* Latency: Placing data geographically closer to users so they can access it faster.
* Scalability: Increasing the number of machines that can serve read queries (Read Scaling).

***

### Leader-Based Replication (Master-Slave)

This is the most common pattern. One node is designated as the Leader, and others are Followers.

*   The Workflow: 1. All Writes must go to the Leader.

    2\. The Leader saves the data locally and sends the change to all Followers (via a replication log or change stream).

    3\. Reads can be handled by either the Leader or any of the Followers.
* Synchronous vs. Asynchronous:
  * Synchronous: The Leader waits for the Follower to confirm it received the data before telling the user "Success."
    * _Pro:_ Data is guaranteed to be on both nodes.
    * _Con:_ If a follower is slow/down, the whole system freezes.
  * Asynchronous: The Leader sends the data but doesn't wait for a response.
    * _Pro:_ High performance; the system stays fast even if followers lag.
    * _Con:_ If the leader crashes before data is replicated, that data is lost forever.
  * Semi-Synchronous: Usually, one follower is synchronous and the rest are asynchronous.

***

### Handling Replication Lag (Consistency Issues)

Since most systems use asynchronous replication for speed, followers may lag behind the leader. This leads to three famous "Consistency" problems:

* Reading Your Own Writes: A user updates their profile (Leader), then refreshes the page (reads from a lagging Follower). Their update is gone!
  * _Solution:_ Always read the user's own profile from the Leader, or enforce a "cool-down" where you read from the leader for 1 minute after a write.
* Monotonic Reads: A user sees a comment, refreshes, and it's gone. Refreshes again, and it's back. (They are hitting different followers with different lag levels).
  * _Solution:_ Ensure a specific user always reads from the same follower (based on a hash of their User ID).
* Consistent Prefix Reads: If someone replies to a comment, you shouldn't see the reply before you see the original comment.
  * _Solution:_ Ensure related data is written to the same partition.

***

### Multi-Leader Replication

Used primarily in Multi-Datacenter setups. Each datacenter has its own leader.

* Pros: Better performance (users write to the nearest DC) and tolerance for datacenter outages.
* Cons: Conflict Resolution. If two users in different DCs edit the same document at the exact same time, which one wins?
  * _Resolution Strategies:_ Last Write Wins (LWW), Collaborative Editing (like Google Docs using Operational Transformation), or Conflict-free Replicated Data Types (CRDTs).

***

### Leaderless Replication (Quorums)

Made famous by Amazon DynamoDB and Cassandra. There is no "leader." The client sends writes to several nodes at once.

* Quorum Math ($$ $W + R > N$ $$):
  * $$ $N$ $$ = Total replicas.
  * $$ $W$ $$ = Number of nodes that must confirm a write.
  * $$ $R$ $$ = Number of nodes you must query for a read.
  * Rule: If you write to $$ $W$ $$ nodes and read from $$ $R$ $$ nodes, and $$ $W+R$ $$ is greater than $$ $N$ $$, you are guaranteed to get the latest data because the read and write sets must overlap.
* Sloppy Quorums and Hinted Handoff: If the intended nodes are down, the system writes to "temporary" nodes to maintain availability, then moves the data back when the original nodes return.

***

### Node Failures: How to recover?

* Follower Failure (Catch-up recovery): The follower keeps a log. When it reboots, it asks the leader for all data changes that happened while it was "asleep."
*   Leader Failure (Failover): 1. Determine the leader is dead (usually via a timeout/heartbeat).

    2\. Elect a new leader (usually the follower with the most up-to-date data).

    3\. Reconfigure the system so clients now send writes to the new leader.

    * _Danger:_ Split Brain. Two nodes both think they are the leader. This is a nightmare scenario that can lead to massive data corruption.

***

#### Summary Checklist for Interviews:

| **Strategy** | **Best For**                | **Main Downside**                     |
| ------------ | --------------------------- | ------------------------------------- |
| Leader-Based | Most standard apps          | Scalability bottleneck on the Leader  |
| Multi-Leader | Multi-region / Offline apps | Complex conflict resolution           |
| Leaderless   | High write availability     | Stale reads / Complex "overlap" logic |

Key Term to Drop: _"In a high-read environment, I would suggest Asynchronous Leader-Based replication to ensure low latency, but I would implement 'Read-Your-Own-Writes' logic to prevent a poor user experience during replication lag."_

***

## 5. Partitioning / Sharding (27:47)

These notes cover Section 5: Partitioning / Sharding from Jordan's video (starting at 27:47), combined with technical depth from _Designing Data-Intensive Applications_.

***

### What is Partitioning (Sharding)?

When a dataset becomes too large to fit on a single machine, or the query throughput exceeds a single node's capacity, we must break the data into smaller chunks called partitions (known as shards in the NoSQL world).

* Main Goal: Scalability. By spreading data and query load across multiple nodes, the system can handle much higher traffic.
* The Challenge: Ensuring data is distributed evenly. If the distribution is unfair, we get a Skewed system where one node has more data/traffic than others, creating a Hotspot.

***

### Strategies for Partitioning

How do we decide which piece of data goes to which node?

#### A. Partitioning by Key Range

Assign a continuous range of keys (from A to Z) to each partition.

* Example: Partition 1 handles names starting with A–M; Partition 2 handles N–Z.
* Pros: Very efficient for range queries (e.g., "Get all users whose names start with B").
* Cons: High risk of hotspots. If you partition by "Timestamp" and all your traffic is current, one node (the "Today" node) will be crushed while others sit idle.

#### B. Partitioning by Hash of Key

A hash function is applied to the key, and the result determines the partition.

* Example: $$ $hash(user\_id) \pmod{number\_of\_nodes}$ $$.
* Pros: Distributes data very evenly, even if the input keys are similar. This eliminates most hotspot issues.
* Cons: You lose the ability to do efficient range queries. To find all users in a range, you’d have to query every single partition (scatter-gather).

***

### The Rebalancing Problem

As your application grows, you will need to add more nodes. You cannot simply use $$ $hash(key) \pmod n$ $$, because if $$ $n$ $$ changes from 10 to 11, almost every single key will result in a different hash, forcing you to move 99% of your data to new nodes.

#### Consistent Hashing

This is the industry-standard solution used by systems like Cassandra and DynamoDB.

* The Ring: Imagine the hash output space as a circle (a ring). Both nodes and data keys are mapped onto this ring.
* The Rule: A key belongs to the first node it encounters when moving clockwise around the ring.
* Benefit: When a new node is added, only a small fraction of the data (the data between the new node and its neighbor) needs to be moved.

***

### Partitioning and Secondary Indexes

This is a major pain point in system design. If you partition by `user_id`, but want to search for users by `email`, how does the database find them?

* Local Index (Document-based): Each partition maintains its own index for the data it stores. To find an email, the database must send the query to every partition and merge the results. This is called Scatter-Gather and can be very slow.
* Global Index (Term-based): You create a separate index that covers all partitions, and you partition _the index itself_ by the term you are searching for (e.g., Email A-M on Node 1, N-Z on Node 2).
  * _Pros:_ Reads are fast.
  * _Cons:_ Writes are slow and complex because a single write to a partition might require updating multiple global index nodes.

***

### Request Routing

Once data is partitioned, how does the client know which node to talk to? There are three main approaches:

1. Gossip Protocol: The nodes talk to each other. You can hit any node, and if it doesn't have the data, it forwards your request to the right one (used by Cassandra).
2. Coordination Service: Use a service like Apache ZooKeeper to keep track of which node has which partition. Clients query ZooKeeper first.
3. Load Balancer / Proxy: A "Routing Tier" sits in front of the database and directs traffic based on the partition key.

***

#### Summary Table: Partitioning Trade-offs

| **Strategy**       | **Best For**                                 | **Main Weakness**                 |
| ------------------ | -------------------------------------------- | --------------------------------- |
| Key-Range          | Range queries (e.g., Sensor data)            | Hotspots on specific ranges       |
| Hash-Based         | Evenly distributed load                      | Range queries are impossible/slow |
| Consistent Hashing | Dynamic environments (adding/removing nodes) | Complexity of implementation      |

***

## 6. Processing: Batch & Stream (37:47)

#### Batch Processing (Offline)

* Uses MapReduce or Spark.
* Processes "data at rest" in large chunks.
* High throughput, but high latency.

#### Stream Processing (Real-time)

* Processes "data in motion" using tools like Apache Kafka or Flink.
* Message Brokers: Decouple producers and consumers.
* Change Data Capture (CDC): Observes changes in a database and streams them to other systems (like a search index or cache).

***

In the video "Systems Design in an Hour" (starting at 37:47), Jordan explains how modern systems handle the massive flow of data. He categorizes processing into two main paradigms: Batch (working on historical data) and Stream (working on data as it arrives).

Here are the detailed notes on Processing: Batch & Stream, supplemented with industry-standard concepts.

***

### Batch Processing (Data at Rest)

Batch processing involves running computations on a large, static dataset that has already been collected and stored.

* The Framework: Historically dominated by Hadoop/MapReduce, but now largely replaced by Apache Spark.
* The Workflow (MapReduce):
  1. Map: Filter and sort data (e.g., "Extract all 'Buy' events from a log file").
  2. Shuffle/Sort: Group related data together.
  3. Reduce: Aggregate the data (e.g., "Sum the total revenue per day").
* Characteristics:
  * High Throughput: Can process petabytes of data efficiently.
  * High Latency: Jobs take minutes or hours to complete.
  * Immutable Input: The source data is never changed; the process creates a brand-new output file.
* Use Cases: Daily financial reports, search engine indexing (crawling the web), and large-scale machine learning model training.

***

### Stream Processing (Data in Motion)

Stream processing deals with "unbounded" data—a continuous flow of events that never technically ends.

* Message Brokers: The "pipes" that carry the data.
  * Traditional (RabbitMQ/ActiveMQ): The broker tracks which messages are processed. Once a consumer finishes a task, the message is deleted. Good for task distribution.
  * Log-Based (Apache Kafka/Amazon Kinesis): The broker is an append-only log. Messages are not deleted immediately. Multiple consumers can read from the same log at different speeds by tracking their own offset.
* Characteristics:
  * Low Latency: Events are processed in milliseconds or seconds.
  * Event-Driven: The system reacts immediately to a specific user action (e.g., sending a notification the moment a user signs up).
* Use Cases: Fraud detection (must happen before the transaction completes), real-time stock price alerts, and live sports analytics.

***

### Change Data Capture (CDC)

CDC is a critical "bridge" between databases and streaming systems.

* The Concept: Instead of querying a database every 5 minutes to see what changed (polling), you treat the database's internal Write-Ahead Log (WAL) as a stream.
* How it works: Any time a row is inserted or updated in your main SQL database, a message is automatically sent to a stream (like Kafka).
* The Benefit: This keeps secondary systems—like your Search Index (Elasticsearch) or your Cache (Redis)—perfectly in sync with your primary database without manual code updates.

***

### Event Sourcing

Instead of storing only the _current state_ of an object (e.g., "User Balance: $50"), you store every _event_ that led to that state.

* The Log: `[Deposit $100, Withdraw $20, Withdraw $30]`
* Pros: You have a perfect audit trail; you can "replay" the log to reconstruct the state at any point in time.
* Cons: Replaying a log of millions of events is slow, so you must periodically take "snapshots" of the current state.

***

### Windowing in Streams

Since streams are infinite, you can't just ask for the "average." You have to define a time window:

* Tumbling Window: Fixed-size, non-overlapping blocks (e.g., "Every 5 minutes, give me the sum").
* Sliding Window: Overlapping blocks (e.g., "Give me the sum of the last 5 minutes, updated every 1 minute").
* Session Window: Groups events together based on user activity (e.g., "All clicks a user makes until they are inactive for 30 minutes").

***

### Comparison: Batch vs. Stream

| **Feature** | **Batch Processing**    | **Stream Processing**                |
| ----------- | ----------------------- | ------------------------------------ |
| Data Source | Fixed-size (Bounded)    | Infinite (Unbounded)                 |
| Focus       | Throughput (Efficiency) | Latency (Speed)                      |
| Tools       | Spark, Hadoop, Hive     | Kafka, Flink, Samza                  |
| Complexity  | Simple to reason about  | Complex (handles late-arriving data) |

***

#### Interview Tip:

If a recruiter asks how to handle "real-time analytics," don't just say "Kafka." Explain that you would use Change Data Capture to stream database updates into Kafka, and then use a processing engine like Apache Flink or Spark Streaming to perform Windowed Aggregations before storing the results in a dashboard database.

Would you like to wrap up with the final section, Point 7 & 8: Caching, Load Balancing, and System Design Patterns (like Rate Limiting and Fan-out)?





***

## 7. Caching & Load Balancing (53:29)

* Caching: Store expensive computations or frequent queries in memory (Redis/Memcached). Use CDN (Content Delivery Network) for static assets like images/videos.
  * _Eviction:_ LRU (Least Recently Used) is the most common policy.
* Load Balancing: Distributes traffic across servers.
  * L4 (Transport): Based on IP/Port.
  * L7 (Application): Based on HTTP headers, cookies, or URL paths (smarter but slower).

***

### Caching: The Performance Booster

Caching is the process of storing copies of data in a high-speed storage layer (usually RAM) so that future requests for that data can be served faster.

#### Where to Cache?

* Client-Side: Browser cache or mobile app storage.
* CDN (Content Delivery Network): Caches static assets (images, JS, CSS, video) geographically close to the user.
* Load Balancer/Reverse Proxy: Can cache common responses to reduce server load.
* Application Cache: Using Redis or Memcached to store database query results or expensive computations.

#### Caching Strategies

1. Cache Aside (Lazy Loading): The application checks the cache. If data isn't there (a "cache miss"), it reads from the database and then writes the result into the cache for next time. _Most common strategy._
2. Read-Through: The application asks the cache for data. If missing, the cache itself pulls the data from the DB and returns it.
3. Write-Through: Data is written to the cache and the database simultaneously. High consistency, but adds latency to writes.
4. Write-Behind (Write-Back): Data is written to the cache only. The cache periodically "flushes" updates to the DB. Fast, but risky (data loss if the cache crashes).

#### Cache Eviction & Invalidation

* TTL (Time-to-Live): Automatically delete a key after a set time.
* Eviction Policies: When the cache is full, how do we decide what to delete?
  * LRU (Least Recently Used): Discards the least recently accessed items.
  * LFU (Least Frequently Used): Discards items used the fewest number of times.
  * FIFO (First In, First Out): Discards the oldest items.

***

### Load Balancing: The Traffic Cop

A Load Balancer (LB) distributes incoming network traffic across a group of backend servers to ensure no single server is overwhelmed.

#### Load Balancing Algorithms

* Round Robin: Sends requests sequentially to each server in the list.
* Least Connections: Sends traffic to the server currently handling the fewest active requests.
* IP Hash: Uses the client's IP address to determine which server gets the request (ensures a user stays on the same server—"sticky sessions").

#### L4 vs. L7 Load Balancing

* L4 (Transport Layer): Makes routing decisions based on network info (IP address and TCP/UDP ports). Fast, but can't "see" the data inside the request.
* L7 (Application Layer): Makes decisions based on the actual content of the request (HTTP headers, cookies, URL paths). Slower, but much smarter (e.g., sending `/video` requests to one set of servers and `/images` to another).

***

### High-Scale Challenges (The "Interview Killers")

When caching and load balancing at massive scale, things can break in specific ways:

* Cache Penetration: Requests for keys that don't exist (e.g., searching for a non-existent User ID) bypass the cache and hit the DB every time.
  * _Solution:_ Use a Bloom Filter or cache the "null" result with a short TTL.
* Cache Breakdown (The Thundering Herd): A very hot key expires, and 10,000 requests hit the DB at the exact same second to refresh it.
  * _Solution:_ Use Mutex Locks so only one request can refresh the cache at a time.
* Cache Stampede: Similar to breakdown, but occurs when many different items expire simultaneously.
  * _Solution:_ Add jitter (randomness) to your TTLs so items don't expire at the same time.

***

#### Summary Table: Caching vs. Load Balancing

| **Feature**  | **Caching**                 | **Load Balancing**                   |
| ------------ | --------------------------- | ------------------------------------ |
| Primary Goal | Reduce Latency / Speed      | High Availability / Reliability      |
| Storage Type | RAM (Redis, Memcached)      | Routing logic (Nginx, HAProxy)       |
| Benefit      | Saves expensive DB/CPU work | Prevents server "bottlenecks"        |
| Key Risk     | Stale data (out of sync)    | Single point of failure (if LB dies) |

***

### 8. Systems Design Interview Patterns (57:34)

The video concludes with common patterns to solve specific problems:

* Fan-out: How a tweet is pushed to millions of followers (Push vs. Pull models).
* Rate Limiting: Using Token Bucket or Leaky Bucket algorithms to prevent API abuse.
* Proximity/Geospatial: Using Quad-Trees or Geohashes to find nearby points (e.g., Uber or Yelp).

***
