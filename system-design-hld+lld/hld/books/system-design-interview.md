# System design interview

## Chapter 1  Scale from Zero to Millions of Users



* **Single server setup**&#x20;
  * everything( erb app, database, cache) is running on one server.
  * Users access websites using domain names , DNS (paid service provided by 3rd parties) gives them the IP adress of our website&#x20;
  * Once IP adress is obtained HTTP requests are sent directly to our web server from user&#x20;
  * Ther user can send the resuqest from&#x20;
    * WEB application&#x20;
      * uses combination of server side languages to handle business logic, Storage and Client side languages HTML and JS for presentation&#x20;
    * Mobile apprication&#x20;
      * HTTP communication protocol is used between Mobile app and web server&#x20;
      * JSON is the commonly used api response to transfer data&#x20;
* **Multiple server setup**&#x20;
  * as Userbase increases we need multiple servers. One for the web/mobile traffic and other for the database.
  * This helps us in scaling the webtier and data tier independently.
* **Which database to use**&#x20;
  * Relational&#x20;
    * MySQL, Oracle, PostgresSQL etc&#x20;
  * Non relational database/NoSQL
    * CouchDB, Neo4j, Cassandra, HBase, Amazon DynamoDB
    * Can be of&#x20;
      * Key Value stores&#x20;
      * graph stores
      * Column stores
      * Document stores
    * great if our application requires&#x20;
      * Super low latency
        * data is unstructured
        * Serialize and deserialize data
        * Store massive amount of data&#x20;
* **Vertical scaling vs Horizontal Scaling**
  * sdF
* L**oad balancer (Supports web tier in failover and redundancy)**
  * distributes incoming traffic among web servers&#x20;
  * if server 1 goes offiine then all load will be diverted to server 2.
  * If traffic increases rapidly then we need to add more servers to web server pool, and the load balancer automatically starts to send requests to them.
* **Database replication ( Supports data tier in failover and redundancy)**
  * IT can be used in many database management systems usually with a master/Slave relationship between the original(master) and the copies(slaves).
  * if only one slave database is available and it goes offline, read operations wil be directed to the master database temporarily. A new database will replace the old one&#x20;
  * If master goes offline, a slave will be promoted to be the new master. All the database operation will replace the old one for the data replication immediately. In production systems, Promoting a new master is more complicated as the data in a slave databser might not be up to date. The missing data needs to be updated by running data recovery steps&#x20;
  * Advantages&#x20;
    * Better performance
    * Reliability
    * High availability
  * Master databse&#x20;
    * generally supports write queries.
    * All data-modifying commands like insert, delete and update must be sent to master databse.
  * Slave
    * Gets copies of data from the master database and only supports read operations&#x20;
* **Cache**
  * Temporary storage area that stores the result of expensive Reponses or frequently accessed data in memory so that subsequent requests are served more quickly.
  * This reduces databse workloads.
  * One can interact with cache servers using apis&#x20;
  * Caching strategies&#x20;
    * Read through cache&#x20;
      * after receiving a request, a web server firsts checks if the cache has the available response. If it has it sends the data back to the client .
    * Other strategies are also present and used depending upon the datatype, size and access patterns.
  * Consideration for using cache
    * Decide when to use cache
    * Expiration policy
    * Consistency
    * Mitigating failures
    * Eviction policy
* CDN&#x20;
  * network of geographically disperse servers used to deliver static content.
  * There cache static content like images, videos, CSS, JS etc.
  * how CDN works
    * when a user visits a website, a CDN server closest to the users will deliver static content.
    * intuitively the further users are from CDN servers the slower the website loads.
    * if a user tries to get image from the website it first looks at the nearest CDN, if it has the content then it gives it to the user. if not the CDN request the image to the web server. this request also has optional HTTP header and TTL .&#x20;
    * After this the image stays with the CDN till the TTL expires.
  * Consideration of using a CDN
    * Cost
    * Setting an appropriate cache expiry
    * CDN fallback
    * invalidating files&#x20;
* Stateless web tier
  * Scaling web tier horizontally.
  * For this we need to move state( user session data) out of the web tier.
  * A good practice is to store the session ,data in the persistent storage such as relational database of NoSQL.
  * Each web server in the cluster can access state data from the data base, this is called stateless web tier.
  * **Stateful artitecture**
    * A stateful server and stateless server has some key difference.&#x20;
    * A stateful server remembers client data from one request to next. A stateless server keeps no state information.
    * ex-- User A session data and profile image is stored in server. To authenticate user A HTTP requests must be routed to server 1. If request is sent to another server like server 2 authentication would fail as server 2 does not contain user A session data . Similarly all HTTP requests from user B must be routed to server 2 .
    * the issue is that ervey request from the same client must be routed to the same server. This can be done with sticky sessions in most load balance but this adds the overhead as adding or removing servers is much more difficult with this approach.
*   **Stateless artitecture**&#x20;

    * HTTP request from users can be sent to any web server as the server fetches the state data from a shared data store.
    * State data is stored in shared data store and keep out of the web servers.
    * A stateless system is simpler, more robust and scalable.
    * in following system the sessions data is stored in NoSQL, frequest accessed data is stored in cahce , we have multiple web servers which has been vertically scaled and is balanced by load balancers . the databse is scaled using master slave srtitecture . so any of the web server can acess read info from any of the database&#x20;



    <figure><img src="../../../.gitbook/assets/image (97).png" alt="" width="452"><figcaption></figcaption></figure>


* Datacenters&#x20;
  * Now above condition is best for one data region , if we want multiple regions we need to setup two datacenters.
  * In Normal operation users are geoDNS-routed also known as geo-routed, to the closest datacenter.
  * In advent of any significant data center outage, we direct all the traffic to a healthy data center.
  * There are multiple technical challenges to achieve multi-data center setup:
    * Traffic redirection: Effective tools are needed to direct traffic to correct data center&#x20;
    * Data Synchronization
    * Test and deployment&#x20;
* Message queue:
  * A message queue is durable component, stored in memory, that supports asynchronous communication. it serves as a buffer and distributes asynchronous requests.
  * The basic artitecture of a message queue is simple, Input services, called producers/publishers, create message and publish them to a message queue.
  * Other services or servers called consumers connect to the queue and perform actions defined by messages.
  * Decoupling makes the message queue a preferred artitecture for building a scalable and reliable application. With the message queue the producer can post a message to the queue when the consumer is unavailable to process it.
  * ex-- web servers publish a photo processing job to the message queue. Photo processing workers pick up jobs from the message queue and a synchronously perform photo customization tasks .  The produces and the consumer can be scaled independently. Then the size of the queue becomes large, more workers are added to reduce the processing time.
  * But if the queue is empty most of the time, the numbers of workers can be reduced.
* Logging metrics and automation&#x20;
  * we add logging , metrics and automation





## Chapter 1: Scale from Zero to Millions of Users

**Objective**:

* Understand how to build and scale a system from supporting a single user to millions of users.
* Learn various techniques to handle system design interview questions.

**Single Server Setup**:

* Begin with a simple setup where everything runs on a single server: web application, database, cache, etc.
* **Request Flow**:
  1. Users access the website via domain names (e.g., api.mysite.com) managed by a third-party DNS.
  2. The DNS returns an IP address to the user's browser or mobile app.
  3. HTTP requests are sent to the web server using the obtained IP address.
  4. The web server returns HTML pages or JSON responses for rendering.
* **Traffic Sources**:
  * **Web Application**: Uses server-side languages (e.g., Java, Python) for business logic and client-side languages (e.g., HTML, JavaScript) for presentation.
  * **Mobile Application**: Uses HTTP protocol for communication and JSON for data transfer.

**Database Scaling**:

* As user base grows, a single server becomes insufficient. Separate servers are needed for web/mobile traffic (web tier) and the database (data tier), allowing independent scaling.
* **Database Choices**:
  * **Relational Databases (RDBMS)**: e.g., MySQL, Oracle, PostgreSQL. Use tables and rows, support SQL joins.
  * **Non-relational Databases (NoSQL)**: e.g., MongoDB, Cassandra. Use collections of documents or key-value pairs, designed for scalability and flexibility.

**Handling Increased Traffic**:

* **Load Balancing**: Distribute traffic across multiple servers to avoid overload.
* **Caching**: Store frequently accessed data in memory to reduce database load. Common tools include Redis and Memcached.
* **Database Replication**: Copy data across multiple servers to ensure high availability and reliability.
* **Stateless Web Tier**: Design web servers to be stateless, allowing any server to handle any request, which simplifies load balancing and failure recovery.

**Scaling Beyond Single Server**:

* **Horizontal Scaling**: Add more servers to handle increased load. Requires careful consideration of data consistency and partitioning.
* **Database Sharding**: Split large databases into smaller, more manageable pieces (shards). Each shard is an independent database with its own data.
* **Replication and High Availability**: Use master-slave or multi-master replication to ensure data is always available, even if some servers fail.
* **Content Delivery Network (CDN)**: Use CDNs to distribute static content (e.g., images, CSS, JavaScript) closer to users, reducing latency and load on the origin server.
* **Monitoring and Automation**: Implement monitoring to detect issues and automate responses to common problems (e.g., auto-scaling).

**Challenges and Considerations**:

* **Join Operations**: Difficult to perform joins across sharded databases. Denormalization and NoSQL databases can help.
* **Celebrity Problem**: Hotspot keys (e.g., very popular users) can overwhelm a single shard. Solutions include further partitioning or allocating dedicated resources.
* **Resharding**: Adjusting sharding strategy as data grows and access patterns change. Techniques like consistent hashing can simplify this process.

**Key Techniques for Scaling**:

* **Stateless Web Tier**: Design the web tier to be stateless.
* **Redundancy**: Build redundancy at every tier to ensure high availability.
* **Caching**: Cache data wherever possible to reduce load.
* **Multiple Data Centers**: Use multiple data centers to ensure availability and reduce latency.
* **CDN**: Host static assets in a CDN to offload traffic from origin servers.
* **Sharding**: Scale the data tier by sharding.
* **Microservices**: Split monolithic applications into smaller, independent services.
* **Monitoring and Automation**: Continuously monitor the system and use automation tools to manage scale and failures.

This chapter emphasizes that scaling is an iterative process requiring continuous improvement and refinement. Mastery of these techniques will provide a solid foundation for tackling system design interviews【6:0†source】【6:3†source】【6:4†source】【6:5†source】.







## Chapter 2: Back-of-the-Envelope Estimation

**Overview:** Back-of-the-envelope estimation is a crucial skill for system design interviews, allowing candidates to quickly estimate system capacity or performance requirements. This chapter discusses the fundamentals of performing these calculations effectively.

**Key Concepts:**

1. **Scalability Basics:**
   * **Power of Two:** Understanding data volume units using powers of two is essential.
   * **Latency Numbers:** Familiarity with typical computer operation latencies helps in making accurate estimates.
   * **Availability Numbers:** Knowledge of high availability percentages and their impact on system uptime.
2. **Power of Two:**
   * Data volume calculations rely on understanding binary units.
   * A byte is 8 bits, and data storage often uses these binary units (e.g., KB, MB, GB).
3. **Latency Numbers Every Programmer Should Know:**
   * Latency is the time delay in a system.
   * Dr. Dean from Google provided typical latencies for various operations in 2010. Although some numbers may be outdated, they still offer valuable insights.
   * Key latency insights include:
     * Memory operations are fast, while disk operations are slow.
     * Disk seeks should be minimized.
     * Compressing data before transmission saves time.
4. **Availability Numbers:**
   * High availability ensures a system is operational for a long time.
   * Measured in percentages, with common SLAs (Service Level Agreements) from cloud providers being 99.9% or higher.
   * The more "nines" in the availability percentage, the better (e.g., 99.99%).

**Example: Estimating Twitter QPS and Storage Requirements**

1. **Assumptions:**
   * 300 million monthly active users.
   * 50% daily active users.
   * Users post 2 tweets per day on average.
   * 10% of tweets contain media.
   * Data retention period is 5 years.
2. **Estimation Calculations:**
   * **Daily Active Users (DAU):** (300 \text{ million} \times 50% = 150 \text{ million})
   * **Tweets Per Second (TPS):** \[ \text{TPS} = \frac{150 \text{ million} \times 2 \text{ tweets\}}{24 \text{ hours} \times 3600 \text{ seconds\}} \approx 3500 ]
   * **Peak QPS:** Approximately double the average QPS, resulting in \~7000.
   * **Media Storage:**
     * Average tweet size: ( \text{tweet\_id: 64 bytes} + \text{text: 140 bytes} + \text{media: 1 MB} )
     * Daily media storage: \[ 150 \text{ million} \times 2 \times 10% \times 1 \text{ MB} = 30 \text{ TB} ]
     * 5-year media storage: \[ 30 \text{ TB} \times 365 \times 5 \approx 55 \text{ PB} ]

**Tips for Effective Estimations:**

1. **Rounding and Approximation:**
   * Simplify calculations by using rounded numbers.
   * Precision is not expected; focus on the process.
2. **Write Down Assumptions:**
   * Clearly state and document all assumptions for reference.
3. **Label Units:**
   * Always label units to avoid confusion (e.g., 5 MB vs. 5 KB).
4. **Practice Common Estimations:**
   * Regular practice of estimating QPS, storage, cache, number of servers, etc., is essential for mastering back-of-the-envelope calculations.

By mastering these concepts and techniques, candidates can effectively tackle system design interview questions that require quick and accurate estimations.

***

These notes summarize the essential points from Chapter 2 of the "System Design Interview: An Insider's Guide." For detailed explanations and examples, refer to the original text in the book







## Chapter 3: A Ten-Minute Introduction to System Design Topics

**Overview**

Chapter 3 provides a concise introduction to key system design topics, aimed at preparing candidates for system design interviews. This chapter emphasizes the importance of understanding fundamental concepts and typical components of system design to navigate interview questions effectively.

**Key Concepts and Components**

1. **Scalability**
   * **Vertical Scaling**: Adding more power (CPU, RAM) to an existing machine.
   * **Horizontal Scaling**: Adding more machines to handle the load.
   * Trade-offs: Vertical scaling is easier but has hardware limits; horizontal scaling is more complex but offers better fault tolerance and flexibility.
2. **Load Balancing**
   * Distributes incoming network traffic across multiple servers.
   * Methods:
     * **Round Robin**: Distributes requests sequentially.
     * **Least Connections**: Directs traffic to the server with the fewest active connections.
     * **IP Hash**: Routes requests based on the client’s IP address.
3. **Caching**
   * Stores copies of frequently accessed data to reduce latency.
   * Types:
     * **Client-side Cache**: Stored on the user’s device.
     * **Server-side Cache**: Stored on the server.
     * **Content Delivery Network (CDN)**: Distributed cache servers globally to serve content faster.
   * Strategies:
     * **Write-through Cache**: Writes data to cache and database simultaneously.
     * **Write-back Cache**: Writes data to cache initially, then to the database.
4. **Data Partitioning (Sharding)**
   * Splits a database into smaller, more manageable pieces.
   * Methods:
     * **Horizontal Partitioning**: Distributes rows of a table across multiple database servers.
     * **Vertical Partitioning**: Distributes columns of a table across multiple database servers.
     * **Directory-based Partitioning**: Uses a lookup service to determine the database location for each piece of data.
5. **Indexes**
   * Improve the speed of data retrieval operations.
   * Types:
     * **Primary Index**: Unique index built on the primary key.
     * **Secondary Index**: Built on non-primary key columns.
6. **Proxies**
   * Acts as an intermediary for requests from clients seeking resources from servers.
   * Types:
     * **Forward Proxy**: Acts on behalf of clients, typically used for security and performance.
     * **Reverse Proxy**: Acts on behalf of servers, typically used for load balancing and security.
7. **Redundancy and Replication**
   * Ensures availability and reliability of data.
   * Methods:
     * **Master-Slave Replication**: One master database and multiple slave databases.
     * **Master-Master Replication**: Multiple databases where each can act as master.
8. **SQL vs. NoSQL Databases**
   * **SQL Databases**: Structured query language, fixed schema, vertically scalable, ACID compliance.
   * **NoSQL Databases**: Flexible schema, horizontally scalable, BASE compliance, types include key-value stores, document stores, wide-column stores, and graph databases.
9. **CAP Theorem**
   * States that a distributed data store can only provide two out of the following three guarantees:
     * **Consistency**: Every read receives the most recent write.
     * **Availability**: Every request receives a response.
     * **Partition Tolerance**: The system continues to function despite network partitions.
10. **Consistency Patterns**
    * **Strong Consistency**: Guarantees that any read returns the most recent write.
    * **Eventual Consistency**: Guarantees that, given enough time, all replicas will converge to the same value.
11. **Communication Protocols**
    * **HTTP/HTTPS**: Widely used for web communications.
    * **WebSockets**: Full-duplex communication channels over a single TCP connection.
    * **gRPC**: High-performance RPC framework that uses HTTP/2.

**Conclusion**

This chapter highlights fundamental system design concepts and components essential for tackling system design interviews. Understanding these topics helps in designing scalable, reliable, and efficient systems. Candidates are encouraged to grasp these concepts deeply to navigate complex design problems effectively.

By mastering these topics, candidates can demonstrate their ability to design robust systems and effectively communicate their design choices during interviews.



## Detailed Notes on Chapter 4: Designing a Rate Limiter

**Introduction to Rate Limiting**

* **Definition:** Rate limiting is a technique used to control the rate of traffic sent or received by a network system.
* **Purpose:** Limits the number of requests a client can make to prevent abuse and overloading.
* **Examples:**
  * A user can post a maximum of 2 posts per second.
  * A single IP address can create up to 10 accounts per day.
  * A device can claim rewards no more than 5 times per week.

**Benefits of Rate Limiting**

1. **Prevent Resource Starvation:** Helps to avoid Denial of Service (DoS) attacks by blocking excess API calls.
   * Examples: Twitter and Google Docs enforce rate limits on their APIs.
2. **Cost Reduction:** By limiting excess requests, fewer servers are needed, saving costs especially when using paid third-party APIs.
3. **Prevent Overloading:** Reduces server load by filtering out unnecessary requests, often caused by bots or user misbehavior.

**Step-by-Step Design Process**

1. **Understanding the Problem and Design Scope:**
   * Clarify whether the rate limiter is client-side or server-side.
   * Determine if the rate limiter should throttle based on IP, user ID, etc.
   * Assess the system's scale (startup vs. large company) and distributed environment requirements.
   * Decide if the rate limiter is a separate service or integrated into application code.
2. **High-Level Design:**
   * Rate limiting rules and their storage.
   * Handling rate-limited requests (e.g., returning HTTP 429 status code).
   * Use of HTTP headers (X-Ratelimit-Remaining, X-Ratelimit-Limit, X-Ratelimit-Retry-After) to inform clients about rate limits.
3. **Detailed Design Deep Dive:**
   * **Rate Limiting Rules:** Configuration of rules (e.g., max 5 messages/day, max 5 logins/minute).
   * **Handling Exceeding Requests:** Returning appropriate HTTP status codes or queuing requests for later processing.
   * **Redis for Counter Storage:** Using Redis for its speed and time-based expiration strategy with commands like INCR and EXPIRE.

**Algorithms for Rate Limiting**

1. **Token Bucket:**
   * **Mechanism:** A bucket with a predefined capacity fills with tokens at a fixed rate. Requests consume tokens.
   * **Parameters:** Bucket size (max tokens) and refill rate (tokens per time unit).
   * **Pros:** Simple implementation, memory efficient, allows short bursts of traffic.
2. **Other Algorithms:**
   * **Leaking Bucket:** Similar to token bucket but maintains a constant outflow of requests.
   * **Fixed Window Counter:** Tracks requests in fixed time windows, e.g., per minute.
   * **Sliding Window Log:** Logs request timestamps to allow a moving window of request limits.
   * **Sliding Window Counter:** Combines fixed and sliding window techniques to smooth out traffic bursts.

**Rate Limiter in a Distributed Environment**

* **Challenges:** Synchronizing counters across multiple servers.
* **Solutions:**
  * Use a centralized data store like Redis.
  * Employ sharding and consistent hashing to distribute the load.

**Performance Optimization and Monitoring**

* **Monitoring:** Track metrics such as request rates, dropped requests, and error rates.
* **Optimization:** Adjust algorithms and parameters based on traffic patterns and resource utilization.

**Hard vs. Soft Rate Limiting**

* **Hard:** Strictly enforces the limit, rejecting any excess requests.
* **Soft:** Allows temporary exceeding of limits, often with a warning or delayed processing.

**Implementation Considerations**

* Evaluate your technology stack for compatibility.
* Decide whether to implement rate limiting in the application code or as a separate service.
* Consider commercial API gateways if internal resources are insufficient.

**Additional Considerations**

* Rate limiting at different OSI layers.
* Best practices for client design to avoid rate limiting, such as caching, exception handling, and retry logic.

**Summary**

* Chapter 4 provides a comprehensive guide to designing a rate limiter, discussing various algorithms, system design considerations, and practical implementation tips to handle large-scale, distributed environments effectively.

**References**

* References to further reading and examples from companies like Twitter, Google, and Stripe are included for deeper understanding and real-world application .



## Chapter 5: Designing Consistent Hashing

**The Rehashing Problem**

When using a traditional hash method to balance the load across servers, issues arise when the number of servers changes. For example, if we have a fixed number of servers, the key distribution might look something like this:

\[ \text{serverIndex} = \text{hash(key)} % N ]

Where `N` is the number of servers. However, if the number of servers changes, this rehashing approach results in a significant redistribution of keys, causing a large number of cache misses and inefficiencies.

**Consistent Hashing**

Consistent hashing addresses the rehashing problem by minimizing the number of keys that need to be remapped when the server pool changes. In consistent hashing, only a fraction of the keys (approximately k/n where k is the total number of keys and n is the number of servers) are redistributed.

**Hash Space and Hash Ring**

Consistent hashing maps both servers and keys to a hash ring. The hash function (e.g., SHA-1) outputs a range of values that form this ring, ensuring that each key maps to a position on the ring. Servers are also hashed and placed on this ring.

1. **Mapping Servers:** Servers are mapped onto the hash ring based on their IP or name.
2. **Mapping Keys:** Keys are hashed and placed on the ring, and each key is stored on the first server encountered when moving clockwise from the key's position.

**Server Lookup**

To find which server stores a key, we move clockwise from the key's position on the ring until a server is found.

**Adding and Removing Servers**

When a server is added, only a fraction of the keys need to be redistributed. Similarly, when a server is removed, only the keys on that server need to be redistributed to the next server clockwise on the ring.

**Virtual Nodes**

Virtual nodes (or replicas) are used to improve the distribution of keys and ensure more even load balancing. Each server is represented by multiple virtual nodes on the ring. This approach reduces the variance in data distribution and ensures that no single server becomes a bottleneck.

**Benefits of Consistent Hashing**

1. **Minimized Redistribution:** Only a small fraction of keys are redistributed when servers are added or removed.
2. **Horizontal Scaling:** The system scales more easily and evenly distributes data.
3. **Hotspot Mitigation:** Consistent hashing helps to distribute keys more evenly, preventing specific servers from becoming overloaded.

**Real-World Applications**

Consistent hashing is used in many real-world systems such as:

* Amazon's Dynamo
* Apache Cassandra
* Discord
* Akamai's CDN
* Maglev Network Load Balancer

Consistent hashing's advantages make it a crucial technique for designing scalable and resilient distributed systems. By minimizing key redistribution and evenly distributing data, consistent hashing effectively addresses the challenges associated with dynamic server pools.



## Chapter 6: Designing a Key-Value Store

**Key-Value Store Overview**

* **Definition**: A key-value store is a non-relational database where each unique identifier (key) is stored with its associated value. The data pairing is known as a "key-value" pair.
* **Key Characteristics**:
  * The key must be unique.
  * The key can be plain text or hashed values.
  * Values can be strings, lists, objects, etc., usually treated as opaque objects.
  * Examples of key-value stores: Amazon Dynamo, Memcached, Redis.

**Design Requirements**

* **Operations**:
  * `put(key, value)`: Inserts a value associated with a key.
  * `get(key)`: Retrieves the value associated with a key.
* **System Characteristics**:
  * Small key-value pair size (<10 KB).
  * Ability to handle large datasets.
  * High availability and scalability.
  * Automatic scaling with traffic.
  * Tunable consistency.
  * Low latency.

**Single Server Key-Value Store**

* **Approach**: Use a hash table to store key-value pairs in memory.
* **Limitations**: Memory constraints make it impossible to fit all data in memory.
* **Optimizations**:
  * Data compression.
  * Store frequently used data in memory, the rest on disk.

**Distributed Key-Value Store**

* **Definition**: Also known as a distributed hash table, it distributes key-value pairs across multiple servers.
* **CAP Theorem**:
  * **Consistency**: All clients see the same data simultaneously.
  * **Availability**: System responds to requests even if some nodes are down.
  * **Partition Tolerance**: System continues to operate despite network partitions.
  * **Trade-offs**: Must sacrifice one of the three properties (CAP).

**System Components**

* **Data Partitioning**:
  * **Challenge**: Evenly distribute data and minimize movement when nodes are added/removed.
  * **Solution**: Consistent hashing places servers on a hash ring; keys are hashed onto the ring and stored on the first server encountered moving clockwise.
* **Data Replication**:
  * **High Availability**: Replicate data asynchronously across multiple servers (N replicas).
  * **Reliability**: Replicas are placed in distinct data centers to handle data center outages.
* **Consistency**:
  * **Quorum Consensus**: Ensures consistency across replicas.
  * **Definitions**:
    * **N**: Number of replicas.
    * **W**: Write quorum size.
    * **R**: Read quorum size.
  * **Example**: With N=3, a write operation must be acknowledged by W replicas, and a read operation must wait for R replicas.

**Handling Failures and Outages**

* **Merkle Trees**:
  * **Usage**: Efficiently verify the contents of large data structures and synchronize data.
  * **Construction**: Keys are hashed into buckets, and the tree is built upwards from these hashes.
* **Data Center Outage**:
  * **Solution**: Replicate data across multiple data centers to ensure availability during an outage.

**System Architecture**

* **Components**:
  * **Clients**: Communicate with the key-value store via simple APIs (`get(key)` and `put(key, value)`).
  * **Coordinator**: Node acting as a proxy between clients and the key-value store.
  * **Nodes**: Distributed on a ring using consistent hashing.
  * **Replication**: Data is replicated at multiple nodes; system is decentralized.
* **Write Path**:
  * **Process**: Write requests are persisted in a commit log, stored in memory, and flushed to disk as SSTables when necessary.
* **Read Path**:
  * **Process**: Checks for data in memory first, then uses Bloom filters to find data on disk if not found in memory.

**Summary**

* The chapter covers the design considerations, trade-offs, and techniques involved in creating a scalable, high-availability key-value store. It references various popular systems like Dynamo, Cassandra, and BigTable, highlighting their architectures and mechanisms.

This summary encapsulates the core ideas and steps from Chapter 6 on designing a key-value store, focusing on distributed systems,&#x20;





## Chapter 7: Designing a Unique ID Generator in Distributed Systems

**Step 1: Understand the Problem and Establish Design Scope**

* **Clarification Questions**: The chapter emphasizes the importance of asking clarifying questions to understand the requirements. Example questions include:
  * Characteristics of unique IDs.
  * Increment behavior of IDs.
  * ID content (numerical only).
  * ID length (64-bit requirement).
  * System scale (10,000 IDs per second).
* **Requirements**:
  * IDs must be unique.
  * IDs should be numerical values.
  * IDs should fit into 64-bit.
  * IDs must be ordered by date.
  * The system must generate over 10,000 unique IDs per second.

**Step 2: Propose High-Level Design and Get Buy-In**

* **Options Considered**:
  * **Multi-master Replication**:
    * Uses auto\_increment with a modification where the increment is by `k` (number of database servers).
    * Pros: Scalability with the number of servers.
    * Cons: Difficult to scale with multiple data centers, IDs do not increment uniformly across servers, challenging to manage with server additions/removals.
  * **UUID (Universally Unique Identifier)**:
    * A 128-bit number used to identify information.
    * Pros: Simple generation without server coordination, easily scalable.
    * Cons: Exceeds the 64-bit requirement, IDs do not increase with time, and can be non-numeric.
  * **Ticket Server**:
    * Uses a centralized auto\_increment feature in a single database server.
    * Pros: Easy to implement for small to medium-scale applications.
    * Cons: Single point of failure, requires data synchronization when multiple ticket servers are used.
  * **Twitter Snowflake Approach**:
    * Divides the ID into sections: sign bit, timestamp, datacenter ID, machine ID, sequence number.
    * Each section has a specific bit allocation ensuring IDs are unique, sortable, and fit into 64-bit.

**Step 3: Design Deep Dive**

* **Twitter Snowflake Approach**:
  * **ID Layout**:
    * Sign bit: 1 bit, always 0.
    * Timestamp: 41 bits (milliseconds since epoch).
    * Datacenter ID: 5 bits (32 datacenters).
    * Machine ID: 5 bits (32 machines per datacenter).
    * Sequence number: 12 bits (reset every millisecond).
  * **Timestamp**:
    * Represents milliseconds since a custom epoch (e.g., Twitter's epoch: 1288834974657).
    * The maximum timestamp can represent up to \~69 years.
    * IDs are sortable by time.
  * **Sequence Number**:
    * Allows for 4096 IDs per millisecond per machine.
    * Ensures uniqueness when multiple IDs are generated in the same millisecond.

**Step 4: Wrap Up**

* **Discussion Points**:
  * Clock synchronization challenges: Assumption that all ID generation servers have the same clock, which is not always true. Network Time Protocol (NTP) is a common solution.
  * Section length tuning: Adjusting bits allocation based on application needs (e.g., more timestamp bits for long-term applications).
  * High availability: Ensuring the ID generator system is always available due to its critical nature.
* **Additional References**:
  * Universally Unique Identifier: [Wikipedia](https://en.wikipedia.org/wiki/Universally_unique_identifier)
  * Ticket Servers: Distributed Unique Primary Keys on the Cheap: [Flickr Engineering Blog](https://code.flickr.net/2010/02/08/ticket-servers-distributed-unique-primary-keys-on-the-cheap/)
  * Announcing Snowflake: [Twitter Engineering Blog](https://blog.twitter.com/engineering/en_us/a/2010/announcing-snowflake.html)
  * Network Time Protocol: [Wikipedia](https://en.wikipedia.org/wiki/Network_Time_Protocol)

This chapter provides a comprehensive guide to understanding, designing, and implementing a unique ID generator in distributed systems, focusing on scalability, reliability, and the importance of clarifying requirements and proposing effective design strategies.



## Chapter 8: Designing a URL Shortener

**Step 1 - Understand the Problem and Establish Design Scope**

System design interview questions are typically open-ended. Clarifying questions are essential to establish the scope:

* **Example of URL shortener operation**: Transform a long URL (e.g., `https://www.example.com/long-url`) to a short one (e.g., `https://tinyurl.com/y7keocwj`). Clicking the short URL redirects to the original URL.
* **Traffic volume**: 100 million URLs generated per day.
* **Shortened URL length**: As short as possible.
* **Allowed characters**: Combination of numbers (0-9) and characters (a-z, A-Z).
* **URL deletion or update**: For simplicity, assume URLs cannot be deleted or updated.

Basic use cases include URL shortening, URL redirecting, and ensuring high availability, scalability, and fault tolerance.

**Back-of-the-Envelope Estimation**

* **Write operations**: 100 million URLs/day ≈ 1160 URLs/second.
* **Read operations**: Assuming a 10:1 read-to-write ratio, 11,600 reads/second.
* **Storage**: Support for 365 billion URLs over 10 years requires ≈ 365 TB, assuming an average URL length of 100 bytes.

**Step 2 - Propose High-Level Design and Get Buy-In**

**API Endpoints**:

1. **URL shortening**:
   * `POST api/v1/data/shorten`
   * Request parameter: `{longUrl: longURLString}`
   * Returns: `shortURL`
2. **URL redirecting**:
   * `GET api/v1/shortUrl`
   * Returns: `longURL` for HTTP redirection

**Step 3 - Design Deep Dive**

**Data Model**:

* Store `<shortURL, longURL>` mapping in a relational database.
* Table structure: `id`, `shortURL`, `longURL`.

**Hash Function**:

* Hash value length: 7 characters, using \[0-9, a-z, A-Z].
* Two approaches: "hash + collision resolution" and "base 62 conversion".

**Hash + Collision Resolution**:

* Use hash functions like CRC32, MD5, or SHA-1.
* Trim hash to 7 characters, resolve collisions with additional strings or bloom filters.

**Base 62 Conversion**:

* Convert numeric IDs to a base 62 representation.
* Example: Convert `11157` (base 10) to base 62, resulting in characters \[0-9, a-z, A-Z].
* Steps: Generate a unique ID, convert to base 62, map to short URL.

**URL Shortening Flow**:

1. Check if the long URL exists in the database.
2. If it exists, fetch and return the short URL.
3. If new, generate a unique ID, convert to short URL, and store it.

Example:

* Input: `https://en.wikipedia.org/wiki/Systems_design`
* Unique ID: `2009215674938`
* Short URL: `zn9edcu`
* Database: Save `ID`, `shortURL`, `longURL`.

**URL Redirecting Flow**:

1. User clicks a short URL.
2. Load balancer forwards request.
3. Check cache for short URL.
4. If not in cache, fetch from database.
5. Return long URL to user.

**Step 4 - Wrap Up**

Key topics covered:

* API design, data model, hash functions, URL shortening, and URL redirecting.
* Discussed approaches to designing unique ID generators, such as multi-master replication, UUIDs, ticket servers, and Twitter Snowflake.

Additional considerations:

* Clock synchronization.
* Section length tuning.
* High availability for ID generators.

Reference materials include details on UUIDs, ticket servers, Twitter Snowflake, and Network Time Protocol.

These notes capture the critical aspects of designing a URL shortener, emphasizing system design principles and practical implementation strategies.



## Chapter 9: Designing a Web Crawler

**Purpose and Use Cases**

A web crawler, also known as a robot or spider, is widely used by search engines to discover new or updated content on the web. It collects content such as web pages, images, videos, PDFs, etc. The primary use cases include:

1. **Search Engine Indexing**: Collects web pages to create a local index for search engines (e.g., Googlebot).
2. **Web Archiving**: Collects information from the web to preserve data for future use (e.g., US Library of Congress archives).
3. **Web Mining**: Discovers useful knowledge from the internet (e.g., financial firms downloading shareholder reports).
4. **Web Monitoring**: Monitors copyright and trademark infringements over the internet.

**Design Scope and Problem Understanding**

The basic algorithm for a web crawler is:

1. Download web pages addressed by a set of URLs.
2. Extract URLs from these web pages.
3. Add new URLs to the list to be downloaded and repeat the steps.

To design a scalable web crawler, understanding the following requirements is crucial:

* Purpose: Search engine indexing.
* Scale: Collect 1 billion pages per month.
* Content Type: HTML only.
* Storage: Store HTML pages for up to 5 years.
* Duplicate Handling: Ignore pages with duplicate content.

Characteristics of a good web crawler:

* **Scalability**: Efficiently handle billions of web pages using parallelization.
* **Robustness**: Handle bad HTML, unresponsive servers, crashes, and malicious links.
* **Politeness**: Avoid making too many requests to a website within a short time interval.
* **Extensibility**: Support new content types with minimal changes.

**Back of the Envelope Estimation**

* **QPS (Queries Per Second)**: 1,000,000,000 pages / 30 days / 24 hours / 3600 seconds ≈ 400 pages per second.
* **Peak QPS**: 800 (double the average QPS).
* **Storage Requirement**: 500 TB per month (assuming average page size of 500k). Over 5 years, 30 PB storage is needed.

**High-Level Design**

The high-level design includes the following components:

1. **Seed URLs**: Starting points for the crawl process. Seed URL selection can be based on locality or topics.
2. **URL Frontier**: Stores URLs to be downloaded, functioning as a FIFO queue.
3. **HTML Downloader**: Downloads web pages from the internet using URLs provided by the URL Frontier.
4. **DNS Resolver**: Translates URLs into IP addresses for the HTML Downloader.

**Workflow of Web Crawler**

1. **Add seed URLs** to the URL Frontier.
2. **HTML Downloader** fetches URLs from the URL Frontier and starts downloading after resolving IP addresses via the DNS Resolver.
3. **Content Parser** parses HTML pages and checks for malformations.
4. **Content Seen? Component** checks if the content is already in storage.
   * If yes, discard the page.
   * If no, pass the content to Link Extractor.
5. **Link Extractor** extracts links from HTML pages.
6. **URL Filter** filters the extracted links.
7. **URL Seen? Component** checks if the URL is already in storage.
   * If yes, do nothing.
   * If no, add it to the URL Frontier.

**Design Deep Dive**

1. **DFS vs BFS**:
   * BFS is commonly used as it handles URLs in a FIFO manner, but has problems with internal links flooding and lack of prioritization.
2. **URL Frontier**:
   * Prioritization can be managed using multiple queues with different priorities.
   * Storage includes both in-memory buffers and disk storage to balance durability and speed.
3. **HTML Downloader**:
   * Implements Robots.txt protocol to respect website rules.
   * Performance optimizations include distributed crawl, DNS caching, and politeness policies to avoid overwhelming servers.

#### Conclusion

Designing a scalable web crawler requires careful consideration of scalability, robustness, politeness, and extensibility. A well-structured workflow and efficient component interactions are crucial for handling the complexities of web crawling on a large scale.





## Chapter 10: Designing a Notification System

**1. Understanding the Problem and Establishing Design Scope**

* **Types of Notifications**: The system supports push notifications, SMS messages, and emails.
* **Real-time System**: It's a soft real-time system where notifications should be received as soon as possible but slight delays are acceptable under high load.
* **Supported Devices**: iOS devices, Android devices, and desktops/laptops.
* **Triggers**: Notifications can be triggered by client applications or scheduled on the server-side.
* **Opt-out Option**: Users can opt-out of receiving notifications.
* **Volume**: The system sends 10 million push notifications, 1 million SMS messages, and 5 million emails daily.

**2. High-level Design and Components**

* **Different Types of Notifications**:
  * **iOS Push Notification**: Requires a provider to send requests to Apple Push Notification Service (APNS), which then sends notifications to iOS devices.
  * **Android Push Notification**: Uses Firebase Cloud Messaging (FCM) to send notifications.
  * **SMS Messages**: Uses third-party services like Twilio or Nexmo for sending SMS.
  * **Emails**: Standard email service protocols are used.
* **Contact Info Gathering Flow**:
  * Gather and store user contact information, ensuring it's kept up-to-date and secure.
* **Notification Sending/Receiving Flow**:
  * Notifications are sent by making API calls to the respective service providers (APNS for iOS, FCM for Android, third-party services for SMS, and email servers).

**3. Design Deep Dive**

* **Reliability**:
  * **Data Loss Prevention**: Ensures notifications are never lost by persisting data in a database and implementing a retry mechanism.
  * **Exactly Once Delivery**: While not always possible due to the distributed nature, a deduplication mechanism helps minimize duplicate notifications.
* **Additional Components and Considerations**:
  * **Notification Templates**: Preformatted templates are used to standardize and speed up notification creation.
  * **Notification Settings**: Users have fine-grained control over their notification preferences.
  * **Rate Limiting**: Limits the number of notifications a user can receive to prevent overwhelming them.
  * **Retry Mechanism**: Ensures failed notifications are retried and alerts are sent to developers if the problem persists.
  * **Security**: Uses appKey and appSecret to secure push notification APIs.
  * **Monitoring and Tracking**: Monitors queued notifications and tracks metrics like open rate, click rate, and engagement for analytics.

**4. Improved High-level Design**

* **Decoupling Components**:
  * Move databases and caches out of the notification server.
  * Use message queues to decouple system components.
  * Introduce workers to handle notification processing in parallel.
* **Components**:
  * **Service APIs**: Services that send notifications via internal or verified client APIs.
  * **Notification Servers**: Provide APIs, validate data, query databases/caches, and put notification data into message queues.
  * **Caches**: Store user info, device info, and notification templates.
  * **Databases**: Store user, notification, and settings data.
  * **Message Queues**: Buffer high volumes of notifications and assign each type a distinct queue.
  * **Workers**: Pull notification events from queues and send them to third-party services.
  * **Third-party Services**: Deliver notifications to user devices.

**5. Wrapping Up**

* The chapter outlines the design of a scalable notification system supporting multiple formats (push notifications, SMS, email).
* Emphasizes the importance of reliability, security, tracking, and respecting user preferences.
* The improved design includes decoupled components, retry mechanisms, rate limiting, and extensive monitoring to ensure system robustness and user satisfaction.

These notes capture the key aspects and detailed components of designing a scalable notification system as discussed in Chapter 10 of the book.





## Chapter 11: Designing a News Feed System

**Step 1: Understand the Problem and Establish Design Scope**

* **Clarification Questions**:
  * Is the system for a mobile app, web app, or both? (Both)
  * Important features: Users can publish posts and see friends' posts.
  * Sorting of news feed: Reverse chronological order.
  * Maximum number of friends a user can have: 5000.
  * Traffic volume: 10 million Daily Active Users (DAU).
  * Content types: Text, images, videos.

**Step 2: Propose High-Level Design and Get Buy-In**

* **Design Flows**:
  * **Feed Publishing**: Data is written into cache and database, and then populated to friends' news feed.
  * **News Feed Building**: Aggregates friends' posts in reverse chronological order.
* **APIs**:
  * **Feed Publishing API**:
    * Method: POST `/v1/me/feed`
    * Parameters: `content`, `auth_token`
  * **News Feed Retrieval API**:
    * Method: GET `/v1/me/feed`
    * Parameter: `auth_token`
* **High-Level Design**:
  * **Components**:
    * **User**: Posts content.
    * **Load Balancer**: Distributes traffic to web servers.
    * **Web Servers**: Redirect traffic to internal services.
    * **Post Service**: Persists posts in database and cache.
    * **Fanout Service**: Pushes new content to friends' news feed.
    * **Notification Service**: Informs friends about new content.

**Step 3: Design Deep Dive**

* **Feed Publishing Deep Dive**:
  * **Web Servers**:
    * Handles client communication, authentication, and rate-limiting to prevent spam.
  * **Fanout Service**:
    * **Fanout on Write**: Pre-computes news feed during post time. Fast but can cause hotkey problems.
    * **Fanout on Read**: Generates news feed on demand. Efficient for inactive users but slower to fetch.
    * **Hybrid Approach**: Uses push model for most users and pull model for celebrities or users with many friends. Consistent hashing helps distribute load.
* **News Feed Building Deep Dive**:
  * **Detailed Design**:
    * **Components**:
      * **User**: Requests news feed.
      * **Load Balancer**: Redirects traffic.
      * **Web Servers**: Route requests to news feed service.
      * **News Feed Service**: Fetches news feed from cache.
      * **Caches**:
        * **News Feed Cache**: Stores news feed IDs.
        * **Content Cache**: Stores post data.
        * **Social Graph Cache**: Stores user relationship data.
        * **Action Cache**: Stores user interactions with posts.
        * **Counters Cache**: Stores counters for likes, replies, followers, etc.
    * **Media Storage**: Images and videos stored in CDN for fast retrieval.

**Step 4: Wrap Up**

* **Discussion Points**:
  * **Scalability**: Addressing how to scale components.
  * **Trade-offs**: Understanding pros and cons of design choices.
  * **Additional Considerations**: Such as rate-limiting, authentication, and resource estimations.

#### Summary

The chapter provides a structured approach to designing a scalable and efficient news feed system, emphasizing the importance of understanding requirements, proposing high-level designs, diving into critical components, and addressing scalability and trade-offs.



## Chapter 12: Designing a Chat System

**Step 1 - Understand the Problem and Establish Design Scope**

1. **Clarification Questions**:
   * Type of chat app: One-on-one or group chat?
   * Platforms: Mobile app, web app, or both?
   * Scale: Startup or massive scale (e.g., 50 million daily active users)?
   * Group member limit: Maximum number of participants (e.g., 100 people).
   * Features: Important features like attachments, online indicators, etc.
   * Message size limit: Text length limitations (e.g., less than 100,000 characters).
   * Security: Need for end-to-end encryption.
   * Storage: Duration for storing chat history (e.g., forever).
2. **Key Requirements**:
   * One-on-one chat with low delivery latency.
   * Small group chat (max 100 people).
   * Online presence indicators.
   * Support for multiple devices.
   * Push notifications.

**Step 2 - Propose High-Level Design and Get Buy-In**

1. **Client-Server Communication**:
   * Clients can be mobile or web applications.
   * Clients communicate via a chat service, not directly with each other.
   * Fundamental operations include receiving messages, finding recipients, and holding messages for offline recipients.
2. **Network Protocols**:
   * HTTP for sending messages with persistent connections using keep-alive headers.
   * WebSockets for bidirectional communication between clients and servers, ensuring real-time updates and efficient connection management.
3. **High-Level Components**:
   * Stateless services for login, signup, user profile management.
   * Stateful services for handling persistent data like chat history.
   * Third-party integrations for additional functionalities.

**Step 3 - Design Deep Dive**

1. **Service Discovery**:
   * Using Apache Zookeeper for recommending the best chat server based on location, server capacity, etc.
   * Flow: User login -> Load balancer -> API server -> Service discovery -> Best chat server -> User connection via WebSocket.
2. **Message Flows**:
   * **One-on-One Chat**:
     * Sender sends message -> Chat server assigns message ID -> Message stored in key-value store -> Forwarded to recipient or push notification if offline.
   * **Message Synchronization**:
     * Devices maintain a variable `cur_max_message_id` to track the latest message ID, ensuring message synchronization across multiple devices.
   * **Group Chat**:
     * More complex logic compared to one-on-one chat, involving multiple recipients and managing group dynamics.
3. **Online/Offline Indicators**:
   * Using a publish-subscribe model to inform friends about online status changes.
   * Efficient for small groups but challenging for large groups; possible solution is to fetch status on group entry or manual refresh.

**Step 4 - Wrap Up**

1. **Architecture Summary**:
   * Chat system architecture includes chat servers for messaging, presence servers for online status, push notification servers, key-value stores for persistence, and API servers for additional functionalities.
2. **Additional Topics**:
   * **Media Support**: Handling photos and videos with compression, cloud storage, and thumbnails.
   * **End-to-End Encryption**: Ensuring only sender and recipient can read messages.
   * **Caching**: Reducing data transfer by caching messages on the client-side.
   * **Load Time Improvement**: Using geographically distributed networks for caching user data.
   * **Error Handling**: Strategies for chat server errors and message resend mechanisms.

By understanding the problem, proposing a high-level design, diving deep into specific components, and wrapping up with potential extensions and improvements, this chapter provides a comprehensive guide to designing a robust chat system .





## Chapter 13: Designing YouTube

Chapter 13 of the book "System Design Interview – An Insider's Guide" focuses on designing YouTube, a popular video-sharing platform. The chapter is structured to guide you through the process of understanding the problem, establishing design requirements, and proposing a high-level system architecture. Here are the detailed notes:

**Step 1: Understand the Problem and Establish Design Scope**

**Requirements Gathering:**

* **Functional Requirements:**
  * Users should be able to upload videos.
  * Videos should be streamed to viewers.
  * Support for comments, likes, and dislikes on videos.
  * Search functionality to find videos.
  * User account management (sign up, log in, log out).
* **Non-Functional Requirements:**
  * **Scalability:** The system should handle a large number of users and videos.
  * **Availability:** The service should be highly available, minimizing downtime.
  * **Consistency:** Ensure data consistency, especially in video metadata and user interactions.
  * **Performance:** Videos should load quickly, and user interactions should be responsive.

**Step 2: High-Level Design**

**Architecture Overview:**

* **Client Side:**
  * Web and mobile clients for users to interact with the platform.
  * Video player for streaming videos.
  * Interfaces for uploading videos, managing accounts, and interactions.
* **Server Side:**
  * **Video Storage:** Distributed storage system to store video files.
  * **Video Streaming:** Efficient streaming service to deliver videos to users.
  * **Metadata Database:** Database to store metadata about videos (title, description, tags, etc.), users, and interactions.
  * **User Authentication and Management:** Services to handle user accounts and authentication.
  * **Load Balancers:** Distribute traffic evenly across servers to ensure scalability and availability.

**Components:**

1. **API Gateway:**
   * Acts as an entry point for all client requests.
   * Handles routing to appropriate backend services.
2. **Video Upload Service:**
   * Manages the process of uploading videos from users.
   * Stores videos in a distributed storage system.
3. **Video Storage Service:**
   * Uses a distributed file system (e.g., HDFS, S3) for storing video files.
   * Ensures redundancy and availability of video data.
4. **Video Processing Service:**
   * Transcodes uploaded videos into various formats and resolutions.
   * Generates thumbnails for videos.
5. **Content Delivery Network (CDN):**
   * Distributes video content geographically closer to users.
   * Reduces latency and improves streaming performance.
6. **Metadata Service:**
   * Manages video metadata, user data, and interactions.
   * Uses a scalable database (e.g., NoSQL, SQL) for storage.
7. **Search Service:**
   * Indexes video metadata to enable fast search functionality.
   * Uses search engines like Elasticsearch.
8. **Comment and Interaction Service:**
   * Manages comments, likes, dislikes, and other interactions on videos.
   * Ensures real-time updates and consistency.
9. **Notification Service:**
   * Sends notifications to users for various events (e.g., new comments, likes).

**Step 3: Design Deep Dive**

**Video Upload Flow:**

1. User uploads a video through the client interface.
2. The video is sent to the Video Upload Service.
3. The service stores the video in the Video Storage.
4. The Video Processing Service transcodes the video into multiple formats.
5. Thumbnails are generated and stored.
6. Metadata is extracted and stored in the Metadata Service.
7. The CDN caches the video for efficient delivery.

**Video Streaming Flow:**

1. User requests to watch a video.
2. The request goes through the API Gateway.
3. The API Gateway retrieves video metadata from the Metadata Service.
4. The CDN serves the video stream to the user.

**Scalability and Reliability Considerations:**

* Use sharding and replication in databases to handle large volumes of data.
* Employ load balancers to distribute traffic and prevent server overloads.
* Implement caching mechanisms at multiple levels (e.g., CDN, metadata cache) to reduce load and improve performance.
* Use microservices architecture to allow independent scaling and maintenance of different components.

**Failure Handling:**

* Design the system to be resilient to server failures.
* Implement data redundancy and backups.
* Use automated monitoring and alerting systems to detect and respond to failures quickly.

**Security and Privacy:**

* Ensure secure communication between clients and servers using HTTPS.
* Implement robust authentication and authorization mechanisms.
* Protect user data and comply with privacy regulations.

**Step 4: Wrap-Up**

In this chapter, the design of YouTube emphasizes scalability, availability, performance, and user experience. The proposed architecture includes key components like the Video Upload Service, CDN, and Metadata Service, ensuring efficient video storage, processing, and streaming. By leveraging distributed systems, caching, and load balancing, the design aims to handle millions of users and videos effectively. The chapter provides a comprehensive approach to tackling the complexities of designing a large-scale video-sharing platform like YouTube.







## Chapter 14: Designing an Online Collaboration Tool

**Objective:** Design an online collaboration tool similar to Google Docs or Microsoft Office 365. This tool allows multiple users to edit documents simultaneously in real-time.

**Step 1: Understand the Problem and Establish Design Scope**

**Key Questions and Answers:**

1. **Platform:** Is it a web app, mobile app, or both?
   * _Both web and mobile app._
2. **Type of documents:** What types of documents will be supported?
   * _Text documents, spreadsheets, and presentations._
3. **Number of users:** How many users can collaborate on a document simultaneously?
   * _Up to 100 users._
4. **File storage:** Should files be stored in the cloud, and what storage service should be used?
   * _Yes, use a cloud storage service like AWS S3._
5. **Access control:** What kind of access control and permissions are required?
   * _View, edit, and comment permissions._
6. **Version control:** Is version control needed?
   * _Yes, to track changes and revert to previous versions._
7. **Real-time updates:** How real-time should the collaboration be?
   * _Changes should reflect almost instantly, with minimal latency._

**Features to Focus On:**

* Real-time collaboration with low latency.
* Access control and permissions.
* Version control and change tracking.
* Cross-platform support (web and mobile).
* Scalability to support millions of users.

**Step 2: Propose High-Level Design and Get Buy-In**

**High-Level Design Components:**

1. **Frontend:**
   * Web and mobile applications that connect to the backend services via APIs.
2. **Backend Services:**
   * **API Gateway:** Handles API requests and routes them to appropriate backend services.
   * **Collaboration Service:** Manages real-time editing and conflict resolution.
   * **File Storage Service:** Handles storage and retrieval of documents.
   * **Authentication Service:** Manages user authentication and authorization.
   * **Notification Service:** Sends notifications for document changes and comments.
3. **Database:**
   * **Relational Database:** Stores user data, document metadata, and access permissions.
   * **NoSQL Database:** Stores real-time collaboration data and document changes.
4. **Real-Time Communication:**
   * **WebSocket Servers:** Facilitate real-time updates and communication between clients and the server.
5. **Version Control:**
   * **Version Control System:** Tracks document changes and allows reverting to previous versions.

**Architecture Diagram:**

* Illustrate the interactions between frontend applications, API gateway, backend services, and databases.

**Step 3: Design Components in Detail**

**Real-Time Collaboration:**

* Use WebSockets for real-time communication.
* Implement Operational Transformation (OT) or Conflict-free Replicated Data Types (CRDT) for conflict resolution.
* Ensure changes are propagated to all users with minimal latency.

**File Storage:**

* Store documents in a scalable cloud storage solution (e.g., AWS S3).
* Implement backup and redundancy to ensure data durability and availability.

**Authentication and Authorization:**

* Use OAuth 2.0 for user authentication.
* Implement role-based access control (RBAC) to manage permissions.

**Version Control:**

* Maintain a version history for each document.
* Allow users to view and revert to previous versions.
* Implement change tracking to show who made what changes and when.

**Scalability Considerations:**

* Use load balancers to distribute traffic across multiple servers.
* Implement auto-scaling to handle peak loads.
* Use caching to reduce database load and improve response times.

**Step 4: Address Non-Functional Requirements**

**Reliability:**

* Implement data redundancy and regular backups.
* Use health checks and failover mechanisms for high availability.

**Security:**

* Encrypt data at rest and in transit.
* Implement secure authentication and authorization mechanisms.

**Performance:**

* Optimize WebSocket connections for low latency.
* Use content delivery networks (CDNs) to reduce latency for static content.

**Usability:**

* Design an intuitive and responsive user interface.
* Provide real-time feedback and notifications to users.

**Conclusion**

Designing an online collaboration tool involves multiple components working together seamlessly to provide a real-time, collaborative experience. The key challenges include managing real-time updates, ensuring data consistency, and providing a scalable and reliable service. By addressing these challenges through careful design and implementation, we can build a robust collaboration tool that meets the needs of millions of users.

\
Chapter 15: Designing Google Drive - Detailed Notes
---------------------------------------------------

**Overview:** In this chapter, the focus is on designing a scalable and efficient cloud storage system akin to Google Drive. Google Drive allows users to store documents, photos, videos, and other files in the cloud, accessible from any device.

**Step 1: Understand the Problem and Establish Design Scope**

* **Key Features:**
  * Upload and download files.
  * File synchronization across devices.
  * Notifications for file changes and sharing.
* **Key Questions:**
  * What are the primary use cases?
  * What are the expected performance requirements?
  * How much data will be stored, and how frequently will it be accessed?

**Step 2: High-Level Design**

* **Component Overview:**
  * **Client Applications:** Interface for users to interact with the service (e.g., web, mobile apps).
  * **API Gateway:** Manages all client requests, ensuring they are routed correctly and securely.
  * **Metadata Service:** Stores metadata about the files, such as names, paths, permissions, etc.
  * **Storage Service:** Handles the actual storage of files, often utilizing distributed file systems.
  * **Notification Service:** Manages notifications to users about changes to their files.
  * **Synchronization Service:** Ensures files are updated across all user devices.

**Step 3: Detailed Component Design**

* **Client Applications:**
  * **Web and Mobile Clients:** Should be intuitive and responsive, allowing easy upload/download, sharing, and viewing of files.
  * **Synchronization Client:** Runs on user devices to keep files up-to-date.
* **API Gateway:**
  * **Security:** Implements authentication and authorization.
  * **Rate Limiting:** Protects the system from abuse by limiting the number of requests a user can make in a given period.
* **Metadata Service:**
  * **Database Choice:** Uses a highly scalable database (e.g., NoSQL) to store file metadata.
  * **Indexing:** Ensures fast retrieval of metadata through efficient indexing strategies.
* **Storage Service:**
  * **Distributed Storage System:** Utilizes systems like HDFS, GFS, or cloud storage solutions (AWS S3, Google Cloud Storage).
  * **Redundancy and Replication:** Ensures data is not lost and is highly available through replication strategies.
* **Notification Service:**
  * **Event-Driven:** Uses systems like Kafka or Pub/Sub to handle real-time notifications.
  * **User Preferences:** Allows users to set preferences for what notifications they receive.
* **Synchronization Service:**
  * **Conflict Resolution:** Implements strategies to handle file conflicts (e.g., last-write wins, versioning).
  * **Efficient Sync:** Minimizes data transfer by only syncing changes (e.g., using diff algorithms).

**Step 4: Scalability and Reliability Considerations**

* **Load Balancing:** Distributes incoming traffic across multiple servers to ensure no single server is overwhelmed.
* **Caching:** Uses caching mechanisms to store frequently accessed data and reduce load on the backend.
* **Data Sharding:** Splits data across multiple databases to ensure scalability.
* **Backup and Disaster Recovery:** Regular backups and robust disaster recovery plans to handle data loss.

**Step 5: Security and Compliance**

* **Data Encryption:** Ensures all data is encrypted both in transit and at rest.
* **Access Control:** Implements fine-grained access controls to ensure only authorized users can access data.
* **Compliance:** Adheres to data protection regulations like GDPR, CCPA, etc.

**Conclusion:** Designing a service like Google Drive involves careful planning and consideration of various components and their interactions. The design needs to be scalable, reliable, and secure to handle millions of users and vast amounts of data efficiently.

**Reference Material:**

1. Google Drive Overview: Google Drive
2. Distributed File Systems: [HDFS](https://hadoop.apache.org/docs/r1.2.1/hdfs_design.html), GFS
3. Event-Driven Architecture: [Kafka](https://kafka.apache.org/), Pub/Sub
4. Data Protection Regulations: [GDPR](https://gdpr-info.eu/), CCPA

These notes provide a comprehensive overview of the key aspects involved in designing a ride-sharing service as described in Chapter 15 of the book "System Design Interview – An Insider's Guide



## Chapter 16: The Learning Continues - Detailed Notes

Chapter 16 of "System Design Interview: An Insider's Guide" is a concluding chapter that emphasizes the importance of continuous learning in the field of system design. Here are the detailed notes from this chapter:

**1. Importance of Continuous Learning**

* Designing robust systems is a complex task that requires years of experience and ongoing education.
* To stay proficient and competitive, it's crucial to constantly learn and adapt to new technologies and methodologies.

**2. Real-World System Architectures**

* The chapter provides a list of real-world systems and architectures to study, which can offer valuable insights and practical knowledge.
* Learning from the architectures of well-known companies can help understand how large-scale systems are built and maintained.

**3. Suggested Reading Materials**

* **Facebook**:
  * _Timeline_ and _Scaling_: Articles discuss how Facebook scales its timeline feature and overall infrastructure.
  * _Chat Systems_ and _Photo Storage_: Explore how Facebook handles chat functionalities and photo storage at scale.
  * _Memcache_ and _TAO_: Facebook’s approach to distributed data stores and caching.
* **Amazon**:
  * _Architecture_: General insights into Amazon's architecture.
  * _Dynamo_: A deep dive into Amazon's highly available key-value store.
* **Netflix**:
  * _Experimentation Platform_ and _Recommendations_: Insights into how Netflix conducts A/B testing and builds its recommendation engine.
* **Google**:
  * _File System and Docs_: Details on the Google File System and the technology behind Google Docs.
  * _Bigtable_: Google's distributed storage system for structured data.
* **Twitter**:
  * _Scaling and Snowflake_: Articles on how Twitter scales and generates unique IDs.
* **Other Companies**:
  * Uber, Pinterest, Instagram, and others have their architectural designs and scaling strategies shared in various articles and blog posts.

**4. Principles and Technologies**

* The chapter encourages understanding both the shared principles and the underlying technologies used in these real-world systems.
* Researching these technologies helps in identifying the problems they solve, which in turn strengthens one’s knowledge base and refines the design process.

**5. Recommended Blogs and Websites**

* The chapter lists various engineering blogs from companies like Yahoo, Yelp, and Zoom, which provide insights into the latest practices and challenges in system design.

**6. Encouragement for Continuous Improvement**

* The chapter concludes by motivating readers to keep learning and refining their skills. The field of system design is ever-evolving, and staying updated is crucial for long-term success.

These notes capture the essence of Chapter 16, highlighting the need for continuous learning and providing resources for further study in system design.

Reference:

* "System Design Interview: An Insider’s Guide," Chapter 16: The Learning Continues​​.





## Chapter 17: Designing an Advertisement System

**1. Introduction to Advertisement Systems:**

* Advertisement systems are crucial for generating revenue in many online services.
* Key components: Advertisers, users, and the platform hosting the ads.
* Main goals: Maximize revenue and ensure relevant ads for users.

**2. Requirements and Scope:**

* System should support various ad formats (e.g., banner ads, video ads).
* High-level requirements include scalability, real-time bidding, and user targeting.
* System must handle high traffic and large volumes of data.

**3. High-Level Design:**

* **Ad Exchange**: Core component where advertisers bid for ad slots.
* **Ad Server**: Delivers the selected ad to users.
* **User Data Store**: Stores user profiles and activity data for targeting.
* **Advertiser Data Store**: Stores information about advertisers and their campaigns.

**4. Real-Time Bidding (RTB):**

* **Auction Process**: Advertisers bid in real-time for each ad impression.
* **Bid Request**: Sent to multiple bidders to get their bids.
* **Bid Response**: Bidders respond with their bids and ad creatives.
* **Winner Selection**: Highest bid wins, and the ad is displayed to the user.

**5. Ad Targeting:**

* **User Profiling**: Based on user activity, demographics, and preferences.
* **Contextual Targeting**: Ads are served based on the content being viewed.
* **Behavioral Targeting**: Ads are based on users' past behavior and interactions.

**6. Ad Serving:**

* **Ad Delivery**: Ensuring that ads are delivered quickly and correctly.
* **Ad Rendering**: Making sure the ad is displayed properly on various devices and platforms.
* **Tracking and Analytics**: Monitoring ad performance (clicks, impressions, conversions).

**7. Scalability Considerations:**

* **Data Sharding**: Distributing data across multiple databases to handle large volumes.
* **Caching**: Using caches to reduce load and increase speed.
* **Load Balancing**: Distributing incoming traffic evenly across servers.

**8. Handling High Traffic:**

* **Rate Limiting**: Preventing abuse and ensuring fair resource usage.
* **Throttling**: Reducing the rate of processing to manage high loads.

**9. Data Privacy and Security:**

* **User Consent**: Ensuring compliance with privacy laws and regulations.
* **Data Anonymization**: Protecting user identities while using their data for targeting.
* **Secure Storage**: Keeping user and advertiser data secure.

**10. Monitoring and Maintenance:**

* **Health Checks**: Regularly monitoring the health of the system components.
* **Logging and Alerting**: Keeping detailed logs and setting up alerts for issues.
* **System Updates**: Regularly updating the system to fix bugs and improve performance.

**11. Conclusion:**

* Building an advertisement system involves complex challenges, including handling high traffic, ensuring data privacy, and maximizing ad relevance.
* A well-designed system can effectively balance these challenges and provide significant revenue opportunities for the platform.

These notes provide a comprehensive overview of the key aspects involved in designing an advertisement system, focusing on high-level design, scalability, data privacy, and real-time bidding processes.

<br>



## Chapter 18: Designing a URL Shortener

**Overview**

This chapter focuses on the design of a URL shortening service, such as TinyURL. The goal is to understand the problem, establish the design scope, and propose a solution that includes high-level design, API endpoints, data models, and handling scalability and fault tolerance.

**Step 1: Understanding the Problem and Design Scope**

**Clarification Questions:**

1. **Example of URL Shortener:**
   * Converts a long URL (e.g., `https://www.example.com/...`) into a short URL (e.g., `https://tinyurl.com/abc123`).
2. **Traffic Volume:**
   * Expected to handle 100 million URLs generated per day.
3. **Shortened URL Length:**
   * As short as possible, typically around 7 characters.
4. **Allowed Characters:**
   * Combination of numbers (0-9) and letters (a-z, A-Z).
5. **Operations on Shortened URLs:**
   * Assume URLs cannot be deleted or updated for simplicity.

**Use Cases:**

1. **URL Shortening:** Converts a long URL into a short URL.
2. **URL Redirecting:** Redirects a short URL to the original long URL.
3. **High Availability, Scalability, and Fault Tolerance:** Ensures the service remains robust and responsive under heavy load.

**Step 2: High-Level Design**

**API Endpoints:**

1. **URL Shortening Endpoint:**
   * `POST api/v1/data/shorten`
   * Request: `{ longUrl: longURLString }`
   * Response: `shortURL`
2. **URL Redirecting Endpoint:**
   * `GET api/v1/shortUrl`
   * Response: `longURL` for HTTP redirection

**Data Flow:**

1. **Shorten URL Flow:**
   * Check if the long URL exists in the database.
   * If it exists, return the existing short URL.
   * If it doesn't exist, generate a unique ID, convert it to a short URL using base 62 conversion, store the mapping, and return the short URL.
2. **Redirect URL Flow:**
   * Retrieve the long URL from the cache or database using the short URL.
   * Perform an HTTP redirect to the long URL.

**Step 3: Design Deep Dive**

**Data Model:**

* Store `<shortURL, longURL>` mapping in a relational database.
* Table schema: `id (primary key), shortURL, longURL`.

**Hash Function:**

1. **Hash + Collision Resolution:**
   * Use a hash function (e.g., CRC32, MD5, SHA-1) to generate a short URL.
   * Resolve collisions by appending a predefined string until the collision is resolved.
   * Bloom filters can be used to improve performance by checking if a short URL exists.
2. **Base 62 Conversion:**
   * Convert a unique ID to a base 62 representation (characters 0-9, a-z, A-Z).
   * Example: Convert `11157` to base 62 resulting in `2TX`.

**Unique ID Generation:**

* Use a distributed unique ID generator, such as Twitter Snowflake, to generate globally unique IDs.
* Convert these IDs to short URLs using base 62 conversion.

**URL Shortening Process:**

1. Input: long URL.
2. Check if the long URL exists in the database.
3. If it exists, return the existing short URL.
4. If it doesn't exist, generate a new unique ID.
5. Convert the ID to a short URL using base 62.
6. Store the new mapping in the database.

**URL Redirecting Process:**

1. User requests a short URL.
2. Forward the request to web servers via a load balancer.
3. Check if the short URL is in the cache.
4. If not, fetch it from the database.
5. Redirect the user to the long URL.

**Step 4: Wrap Up**

In this chapter, the following topics were discussed:

1. API design for URL shortening and redirecting.
2. Data models for storing URL mappings.
3. Hash functions and base 62 conversion for generating short URLs.
4. Handling scalability and fault tolerance in the system.
5. Using a distributed unique ID generator for creating unique short URLs.

<br>



<br>
