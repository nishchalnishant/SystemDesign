# Designing Data intensive Applications

## <mark style="color:purple;">Chapter 1: Reliable, Scalable, and Maintainable Applications</mark>

**Thinking About Data Systems**

Applications have moved from being compute intensive to data intensive, bigger problem today is amount, complexity and speed at which data is changing.&#x20;

There are many tools such as (each one has several ways of building each one, each optimized for a different task and these could be used interchangebally)

* Store data so that other application can find it again --database,&#x20;
* remember result of an expensive operation — queues,&#x20;
* Alows user to search data by keyword — caches&#x20;
* Send a message to another process to handle asynchronously — stream processing&#x20;
* Periodically crunch a large amount of accumulated data — batch processing.

Each tool is now connected using APIs which hides how the underlying tech

Each system are optimized for different usecase and they no longer neatly fit into traditional categories.

The boundaries between the categories are becomming blurred

Instead of the complete work being done using a single tool the woek is brokern down into tasks that can be performed efficiently on a single tool, and those different tolls are stiched together using application code.

Three primary concerns to address: <mark style="color:yellow;">**reliability, scalability, and maintainability.**</mark>&#x20;

1. <mark style="color:yellow;">**Reliability**</mark>:&#x20;
   1. Ensuring the system works correctly even in the face of faults (hardware, software, or human errors). A fault-tolerant system can handle unexpected issues without affecting the end-user experience.
   2. Reliability involves ensuring the system continues to work correctly despite faults.&#x20;
   3. Most of the critical bugs are because of error handaling which causes faults which leads to reliability issues.
   4. There are situations where we may choose to sacrifice reliability in order to reduce development cost or operational cost but we must be very conscious of when we are cutting corners.
   5. Faults can be:
      1. **Hardware Faults**: Often random and uncorrelated, hardware faults like disk failures can be mitigated by redundancy.
      2. **Software Errors**: Bugs and glitches, usually systematic and harder to handle, require rigorous testing and fault-tolerance mechanisms. Carefully thinking about assumptions and interaction in the system through testing , process isolation etc can help.
      3. **Human Errors**: Mistakes made by users or administrators. Good design and training can minimize these.
2. <mark style="color:yellow;">**Scalability**</mark>:&#x20;
   1. Maintaining performance levels despite increasing load by describing load and performance quantitatively. Techniques include adding processing capacity to handle higher loads effectively.
   2. Scalability focuses on maintaining performance as the system grows.&#x20;
   3. There can be multiple needs of scalability, like number of users increasing, in case of twitter number of followers increasing etc.
   4. Key points include:
      1. **Describing Load**: Understanding the type and volume of requests the system handles. For example, Twitter’s home timeline serves as an example of varying load types.
      2. **Performance Metrics**: Using response time percentiles to measure and ensure performance under different loads. it is easy to improve performance of 95 percentile but to go from 95 to 99 is hard.
3. <mark style="color:yellow;">**Maintainability**</mark>:&#x20;
   1. Making the system easier for engineers to work on over time. This involves good abstractions to reduce complexity and effective operational practices for system health management.
   2. Maintainability ensures the system remains operable and adaptable over time. Principles include:
      * **Operability**: Making routine tasks easy and providing good monitoring, automation support, and documentation.
      * **Simplicity**: Reducing complexity to help engineers understand the system quickly.
      * **Evolvability**: Facilitating changes to the system to adapt to new requirements and use cases.

Summary

* This chapter lays the foundation for understanding how to design data-intensive applications by focusing on reliability, scalability, and maintainability. These principles guide the development and operation of robust systems capable of handling real-world challenges【6:3†source】【6:4†source】.
* In subsequent chapters, various techniques, architectures, and algorithms will be explored to achieve these goals. This involves moving from fundamental ideas to practical implementations and examining how different components work together to build effective data systems
*   There is unfortunately no easy fix for making applications reliable, scalable, or main‐

    tainable. However, there are certain patterns and techniques that keep reappearing in

    different kinds of applications.



***

## <mark style="color:purple;">Chapter 2: Data Models and Query Languages</mark>

**Overview**

Data models are crucial in software development as they influence how software is written and how problems are approached. This chapter explores various general-purpose data models for data storage and querying, focusing on the relational model, the document model, and several graph-based models. It also examines different query languages and their use cases.&#x20;

There are many different kinds of data models, and every data model embodies assumptions about how it is going to be used. Some kinds of usage are easy and some are not supported; some operations are fast and some perform badly; some data transformations feel natural and some are awkward.

<mark style="color:yellow;">**Relational Model Versus Document Model**</mark>

* **Relational Model**:&#x20;
  * Based on Edgar Codd's relational model proposed in 1970, it organizes data into tables (relations) where each table is an unordered collection of rows (tuples).&#x20;
  * This model uses structured query language (SQL) for data manipulation.
* **Document Model**:&#x20;
  * Uses documents, typically in formats like JSON or XML, to store data.&#x20;
  * Each document is a self-contained unit that can include nested structures such as lists and maps.&#x20;
  * Document databases are more flexible with the schema, allowing for variations in document structure.

<mark style="color:yellow;">**The Birth of NoSQL**</mark>

* NoSQL databases emerged to address the limitations of relational databases, particularly in handling large-scale, distributed systems, and unstructured data.
* They offer various data models (key-value, document, column-family, graph) and aim for horizontal scalability and fault tolerance.
*   Different applications have different requirements, and the best choice of technology

    for one use case may well be different from the best choice for another use case. It

    therefore seems likely that in the foreseeable future, relational databases will continue

    to be used alongside a broad variety of nonrelational datastores

<mark style="color:yellow;">**The Object-Relational Mismatch**</mark>

* Refers to the difficulty in translating data between in-memory objects used in application code and the relational tables used in databases. This mismatch can lead to complex code and performance issues.

<mark style="color:yellow;">**Relationships in Data Models**</mark>

* **Many-to-One and Many-to-Many Relationships**: Handling relationships is straightforward in relational databases using foreign keys. Document databases can also handle relationships but often duplicate data to avoid the need for joins.
* **Are Document Databases Repeating History?**: There is a debate whether document databases are repeating the same patterns seen in the history of relational databases, particularly in terms of needing to add structure and constraints as applications grow.

<mark style="color:yellow;">**Relational Versus Document Databases Today**</mark>

* Relational databases are preferred for applications with complex queries and transactions.
* Document databases are advantageous for applications with flexible schema requirements and hierarchical data.
*   The main arguments in favor of the document data model are schema flexibility, bet‐

    ter performance due to locality, and that for some applications it is closer to the data

    structures used by the application. The relational model counters by providing better

    support for joins, and many-to-one and many-to-many relationships.
* if the application has document like structure and there is a tree of one to many realtionships then it is good idea to use docuemnt model. The relational technique of shredding/spllitting a document like structure into multiple tables can lead to cumbersome schemas and unnecessarily complicated aplication code.
* But it has a limitation as one cannot refer directly to a nested item within a docuemnt but one need to point out the position but as long as document are not deeply nested this is not a problem
* But if the doument has many to many relationship the document model becomes less appealing. It is possible to reduce the need for joins by denormalizing but the application code needs to do additional work to keep the denormalized data consistent.

<mark style="color:yellow;">**Query Languages for Data**</mark>

* **Declarative Queries on the Web**: Declarative query languages like SQL and XQuery allow specifying what data is needed without detailing how to retrieve it.
* **MapReduce Querying**: A programming model for processing large datasets with a distributed algorithm on a cluster, popularised by Hadoop.

<mark style="color:yellow;">**Graph-Like Data Models**</mark>

* **Property Graphs**: Consist of nodes and edges where both can have properties (key-value pairs).
* **The Cypher Query Language**: Designed for querying property graphs, used by the Neo4j database.
* **Graph Queries in SQL**: SQL extensions or specialised graph query languages can be used to query graph data stored in relational databases.
* **Triple-Stores and SPARQL**: Triple-stores are databases for storing RDF data, and SPARQL is a query language for retrieving and manipulating RDF data.
* **The Foundation: Datalog**: A declarative logic programming language with roots in deductive databases, providing a foundation for many modern query languages.

<mark style="color:yellow;">**Summary**</mark>

The chapter provides a comprehensive comparison of different data models and query languages, highlighting their strengths, weaknesses, and appropriate use cases. Understanding these models helps in choosing the right database and query language for specific application needs .



Deciding between SQL and NoSQL databases depends on the specific requirements of your project. Here’s a comparison to guide your decision:

<br>

<mark style="color:yellow;">When to Use SQL Databases</mark>

* Structured Data:
  * Your data fits well into a predefined schema.
  * Examples: Relational data like customer orders, financial records.
* ACID Compliance:
  * You need Atomicity, Consistency, Isolation, and Durability to ensure data reliability.
  * Examples: Banking, financial transactions.
* Complex Queries:
  * Your application requires complex joins, aggregations, and transactions.
  * Examples: Analytical dashboards, reporting systems.
* Relationships:
  * Data has strong relationships, and you need to enforce foreign keys or constraints.
  * Examples: Inventory systems, HR systems.
* &#x20;Maturity and Support:
  * You prefer a proven, robust technology with a vast ecosystem.
  * Examples: Enterprise applications.

Popular SQL Databases:

* MySQL, PostgreSQL, Oracle Database, Microsoft SQL Server.

When to Use NoSQL Databases

* Unstructured or Semi-Structured Data:
  * Your data does not have a fixed schema or changes frequently.
  * Examples: JSON documents, user-generated content
* High Scalability and Performance:
  * You need to scale horizontally to handle large volumes of data and high throughput.
  * Examples: Social media platforms, real-time analytics
* Flexibility:
  * Your application requires agile schema evolution.
  * Examples: Product catalogs, event tracking systems.
* Large Volumes of Data:
  * You deal with big data or need to store data like logs, sensors, or IoT data.
  * Examples: Logging systems, IoT applications.
* Distributed Systems:
  * Your application is geographically distributed, and you need a database optimized for this.
  * Examples: Content delivery networks, multi-region applications
* Eventual Consistency:
  * You can trade immediate consistency for high availability and partition tolerance (CAP theorem)
  * Examples: Messaging systems, ad-serving platforms.

Popular NoSQL Databases:

* MongoDB, Cassandra, DynamoDB, Redis.

Key Questions to Ask

* What type of data am I handling? (Structured → SQL, Flexible → NoSQL)
* How critical is data consistency? (Critical → SQL, Relaxed → NoSQL)
* What is my scaling strategy? (Vertical → SQL, Horizontal → NoSQL)
* Do I need complex querying? (Yes → SQL, No → NoSQL)
* What are my performance needs? (Read-heavy → SQL, Write-heavy → NoSQL)



| Use Case             | SQL Databases        |  NoSQL Databases         |
| -------------------- | -------------------- | ------------------------ |
| Web Applications     | MySQL, PostgreSQL    | MongoDB, DynamoDB        |
| E-Commerce           | Oracle, MySQL        | Elasticsearch, Couchbase |
| Social Networks      | PostgreSQL           |  Neo4j, Cassandra        |
| Data warehosing      | Amazon Redshift      | Apache Cassandra         |
| Real-Time Analytics  | Snowflake, BigQuery  | Redis, Elasticsearch     |
| IoT Applications     | Microsoft SQL Server | TimescaleDB              |



## <mark style="color:purple;">Chapter 3: Storage and Retrieval</mark>

<mark style="color:yellow;">**Overview**</mark>

* This chapter delves into the data structures and algorithms used by databases to store and retrieve data efficiently. It covers various indexing techniques, storage formats, and their trade-offs.
* here we are discussing how do we give our data to the db and mechanism by which we can ask for it again later from the db POV.
* We need this there is big difference between storage engines that are optimized for transactional workloads and those that are optimized for analytics

<mark style="color:yellow;">**Data Structures That Power Your Database**</mark>

Databases use different data structures to optimize data retrieval and storage.&#x20;

in order to efficiently find the value for a particular key in db we need different data structure an index it is derrived from primiary data&#x20;

There is an important trade off in storage system whell chosed indexes speed up read queries but every index slows down writes.

The primary structures discussed are hash indexes, SSTables, LSM-Trees, and B-Trees.

1. <mark style="color:red;">**Hash Indexes**</mark><mark style="color:red;">:</mark>
   * Suitable for key-value lookups.
   * Use a hash function to map keys to array positions.
   * Efficient for exact match queries but not for range queries.
2. <mark style="color:red;">**SSTables (Sorted String Tables) and LSM-Trees (Log-Structured Merge Trees)**</mark><mark style="color:red;">:</mark>
   * SSTables store key-value pairs sorted by key and written sequentially.
   * LSM-Trees organize SSTables into levels, periodically merging them to maintain order and remove duplicates.
   * Benefits include efficient sequential writes and good read performance for range queries.
3. <mark style="color:red;">**B-Trees**</mark><mark style="color:red;">:</mark>
   * Store data in fixed-size blocks or pages, maintaining a sorted order.
   * Each node can have multiple children, ensuring the tree remains balanced.
   * B-Trees are optimized for read-heavy operations, supporting efficient insertion, deletion, and key-value lookups.
4. <mark style="color:red;">**Comparing B-Trees and LSM-Trees**</mark><mark style="color:red;">:</mark>
   * B-Trees offer faster read performance due to fewer layers to traverse.
   * LSM-Trees provide better write throughput because of sequential write patterns and reduced write amplification.
   * The choice between B-Trees and LSM-Trees depends on the specific workload requirements.
5. <mark style="color:red;">**Other Indexing Structures**</mark><mark style="color:red;">:</mark>
   * Additional structures like fractal trees borrow concepts from both B-Trees and log-structured approaches to optimize disk seeks and write performance.

**Transaction Processing or Analytics?**

Databases are generally optimized for either transaction processing or analytics, but not both.

* **Transaction Processing (OLTP)**:
  * Focuses on fast query execution for many small transactions.
  * Requires efficient indexing and concurrency control mechanisms.
* **Analytics (OLAP)**:
  * Involves complex queries over large datasets.
  * Benefits from data warehousing techniques, where data is often denormalized to speed up read queries.

**Data Warehousing**

Data warehouses are specialized databases designed for analytics. They use specific schemas and storage formats to optimize for read-heavy operations.

1. **Stars and Snowflakes: Schemas for Analytics**:
   * Star schema: Simplifies query optimization by organizing data into fact tables and dimension tables.
   * Snowflake schema: Normalizes dimension tables to reduce redundancy and improve data integrity.
2. **Column-Oriented Storage**:
   * Stores data by columns rather than rows, making it efficient for read-heavy analytical queries.
   * Allows for column compression, significantly reducing storage requirements and improving I/O performance.
3. **Column Compression**:
   * Compresses data within columns to save space and reduce I/O bandwidth usage.
   * Different techniques like run-length encoding and dictionary encoding are employed.
4. **Sort Order in Column Storage**:
   * Data within columns can be sorted to further enhance query performance.
   * Sorted columns improve compression rates and enable more efficient query execution.
5. **Writing to Column-Oriented Storage**:
   * While reading is optimized, writing to columnar stores can be complex and involves maintaining sort orders and compression.
6. **Aggregation: Data Cubes and Materialized Views**:
   * Pre-computed summaries (materialized views) and multi-dimensional arrays (data cubes) speed up complex analytical queries.

**Summary**

Chapter 3 provides a comprehensive look at the storage and retrieval mechanisms crucial for database performance. It explains the strengths and weaknesses of different indexing techniques and storage formats, helping to understand the best use cases for each in the context of transaction processing and analytics.

***

## Chapter 4: Encoding and Evolution

**Introduction**

* **Evolvability**: Emphasizes the need for systems that adapt easily to changes. Changes in application features often necessitate changes in the data it stores.
* **Schemas**:
  * **Relational Databases**: Assume a single, evolving schema.
  * **Schema-on-Read Databases**: Allow multiple formats to coexist without a strict schema.

**Compatibility**

* **Backward Compatibility**: Newer code reads data written by older code.
* **Forward Compatibility**: Older code reads data written by newer code.
  * Backward compatibility is typically easier as the new code can be designed to handle older formats.
  * Forward compatibility requires old code to ignore unknown new fields.

**Data Encoding Formats**

1. **JSON, XML, and Binary Variants**:
   * Textual formats like JSON and XML are widely used for their readability and flexibility.
   * Binary formats offer efficiency and speed but may lack readability.
2. **Thrift and Protocol Buffers**:
   * Designed by Facebook and Google respectively, these formats support efficient, cross-language data interchange.
   * They require a schema to define data structure, aiding in forward and backward compatibility.
3. **Avro**:
   * Uses a writer’s schema to encode data and a reader’s schema to decode it.
   * Ensures compatibility by resolving differences between schemas dynamically.

**Handling Schema Changes**

* **Avro’s Approach**:
  * **Writer’s Schema**: Used when encoding data.
  * **Reader’s Schema**: Used when decoding data.
  * Avro’s schema resolution process ensures compatibility even when schemas differ.
  * Fields can be added or removed as long as default values are maintained to ensure backward and forward compatibility.

**Modes of Dataflow**

1. **Dataflow Through Databases**:
   * Ensures data consistency and reliability as it moves through different stages of processing.
2. **Dataflow Through Services: REST and RPC**:
   * **REST**: Emphasizes stateless communication using standard HTTP methods.
   * **RPC**: Enables remote procedure calls, allowing services to execute methods on remote servers as if they were local.
3. **Message-Passing Dataflow**:
   * Involves sending messages between components or services, facilitating asynchronous communication and decoupling.

**Summary**

* Data encoding and schema evolution are critical for maintaining system evolvability.
* Different encoding formats offer various levels of efficiency and compatibility.
* Maintaining backward and forward compatibility is essential for smooth rolling upgrades and system updates.

## Chapter 5: Replication - Detailed Notes

**Overview**

Replication is a fundamental technique in distributed systems to ensure data availability and fault tolerance. This chapter delves into various replication methods, their benefits, challenges, and practical implementation in different database systems.

**Leaders and Followers**

1. **Leader-Based Replication (Master-Slave Replication)**
   * A designated leader handles all write requests.
   * Followers replicate the leader's data, maintaining consistency.
   * Commonly used in databases like PostgreSQL, MySQL, and in distributed message brokers like Kafka.
2. **Synchronous vs. Asynchronous Replication**
   * **Synchronous Replication:** The leader waits for acknowledgments from followers before confirming a write, ensuring consistency but potentially slowing down the system.
   * **Asynchronous Replication:** The leader does not wait for acknowledgments, enhancing performance but risking temporary inconsistencies.
3. **Handling Replication Lag**
   * Inconsistent reads may occur due to replication lag. Techniques to mitigate this include:
     * **Read-Your-Writes Consistency:** Ensuring that a user's writes are visible in their subsequent reads.
     * **Monotonic Reads:** Guaranteeing that a user does not see data "move backward in time" by ensuring read operations are always performed on up-to-date replicas.

**Replication Topologies**

1. **Single-Leader Replication**
   * Simple to implement and ensures strong consistency.
   * Risks include a single point of failure at the leader and limited write throughput.
2. **Multi-Leader Replication**
   * Multiple leaders can accept writes, offering higher availability and write throughput.
   * Challenges include conflict resolution when different leaders process conflicting writes.
3. **Leaderless Replication**
   * All nodes can accept writes, and conflicts are resolved through techniques like "last write wins" or version vectors.
   * Ensures high availability and partition tolerance but requires sophisticated conflict resolution mechanisms.

**Conflict Resolution**

1. **Last Write Wins (LWW)**
   * Each write is timestamped, and the latest timestamp wins.
   * Simple but may lead to data loss if concurrent writes are common.
2. **Version Vectors**
   * Maintain a vector of version numbers to capture causality between different versions.
   * Allows more sophisticated merging of concurrent updates, preserving more data but increasing complexity.

**Practical Considerations**

1. **Handling Write Conflicts**
   * Strategies include discarding old data, merging concurrent writes, or using custom conflict resolution logic.
2. **Detecting Concurrent Writes**
   * In systems like Dynamo, conflicts can arise due to network delays. Detecting and resolving these conflicts is crucial for maintaining consistency.
3. **Tuning Replication for Performance**
   * Balancing synchronous and asynchronous replication can help optimize for both consistency and performance.
   * Monitoring replication lag and adapting the system dynamically to handle variations in network performance or system load.
4. **Operational Concerns**
   * Ensuring fault tolerance requires careful handling of node failures and network partitions.
   * Strategies like automatic failover, data rebalancing, and consistency checking are vital for robust operation.

By understanding these aspects of replication, database administrators and developers can design systems that are both resilient and performant, ensuring data integrity even in the face of failures and network issues.





Here are detailed notes on Chapter 6 of "Designing Data-Intensive Applications":

## Chapter 6: Partitioning

**Introduction to Partitioning**

* **Definition**: Partitioning (or sharding) involves breaking a large database into smaller parts to enhance scalability and performance.
* **Terminology**: While different systems have varying terms for partitions (e.g., shard, region, tablet), partitioning is the established term.
* **Purpose**: The primary goal is scalability, distributing data and query loads across multiple nodes in a shared-nothing architecture. Each node handles queries for its own partitions, enabling query throughput to scale with the number of nodes.

**Partitioning and Replication**

* **Combination**: Partitioning is often combined with replication for fault tolerance, ensuring each data piece is stored in multiple copies across different nodes.
* **Independence**: The choice of partitioning scheme is mostly independent of the replication scheme.

**Partitioning of Key-Value Data**

* **Goal**: Spread data and query load evenly across nodes to prevent hotspots where one partition might receive disproportionate traffic.
* **Key-Range Partitioning**: Assigns continuous ranges of keys to partitions. This is analogous to volumes in a paper encyclopedia, where knowing the boundaries helps determine the correct partition for a key.
* **Skew and Hot Spots**: Uneven partitioning can lead to skew, where some partitions handle more data or queries than others, creating hot spots.

**Rebalancing Partitions**

* **Need for Rebalancing**: Necessary when adding or removing nodes in a cluster to ensure data and query load remain evenly distributed.
* **Strategies**:
  * **Hash Mod N**: An inefficient method where changing the number of nodes requires moving most of the data.
  * **Fixed Number of Partitions**: Create more partitions than nodes and reassign partitions among nodes as the cluster grows or shrinks. This minimizes the data moved during rebalancing.
  * **Rebalancing by Stealing Partitions**: New nodes can 'steal' partitions from existing nodes to balance the load.

**Request Routing**

* **Problem**: Determining which node handles a specific request, especially as partition assignments change.
* **Approaches**:
  * **Any Node Contact**: Clients can contact any node, which forwards the request to the correct node.
  * **Routing Tier**: A dedicated layer that routes requests to the appropriate node.
  * **Partition-Aware Clients**: Clients are aware of partition assignments and connect directly to the correct node.
* **Service Discovery**: Keeping track of partition-to-node assignments is critical. Solutions like ZooKeeper help maintain this metadata and notify nodes of changes.

#### Key Concepts and Mechanisms

* **Shared-Nothing Architecture**: No special hardware is required, allowing use of cost-effective machines distributed across geographic regions.
* **Replication vs. Partitioning**:
  * **Replication**: Copies of the same data on different nodes for redundancy and performance.
  * **Partitioning**: Splitting a database into smaller subsets assigned to different nodes.

#### Summary

Partitioning is essential for handling large datasets and high query throughput by distributing data across multiple nodes. The chapter covers the mechanics of partitioning, rebalancing strategies, and how requests are routed in a partitioned system. Properly managing partition assignments and updates ensures efficient and scalable data handling in distributed systems.

These notes encapsulate the main points of Chapter 6 on partitioning, highlighting the key methods, challenges, and solutions discussed in the book 【3†source】 .



## Chapter 7: Transactions - Detailed Notes

**Introduction to Transactions**

* **Transactions** are critical in database management to handle errors and concurrency issues.
* They provide an abstraction that simplifies dealing with hardware and software faults, reducing a wide range of potential errors to transaction aborts that can be retried.

**Key Concepts and Definitions**

1. **Atomicity**: Ensures that a series of operations within a transaction are all completed successfully or none are, preventing partial updates that could lead to data inconsistencies.
2. **Isolation**: Ensures that concurrently running transactions do not interfere with each other, providing a consistent view of the database at any point in time.
3. **Dirty Reads**: Occur when a transaction reads data written by another transaction that has not yet committed. Isolation levels like read committed and stronger levels prevent this anomaly.
4. **Dirty Writes**: Happen when a transaction overwrites data that another transaction has written but not yet committed. Most transaction implementations prevent dirty writes.
5. **Read Skew (Non-repeatable Reads)**: Occur when different parts of a database are read at different points in time. This is usually prevented by snapshot isolation, which provides a consistent snapshot for the duration of a transaction.
6. **Lost Updates**: Arise when two clients concurrently perform a read-modify-write cycle, resulting in one client's changes being lost. Snapshot isolation can prevent this, though manual locking might be required in some cases.
7. **Write Skew**: Happens when a transaction reads data, makes a decision based on that data, and then writes based on that decision, but by the time the write occurs, the initial data has changed. Only serializable isolation can prevent this.
8. **Phantom Reads**: Occur when a transaction reads a set of rows that match a condition, and another transaction subsequently modifies the database in a way that affects the result of the initial read.

**Isolation Levels**

* **Read Committed**: Prevents dirty reads but allows other anomalies.
* **Snapshot Isolation (Repeatable Read)**: Prevents most anomalies but not write skew.
* **Serializable**: Prevents all anomalies but can be more complex and performance-intensive to implement.

**Concurrency Control**

* Concurrency control mechanisms ensure correct transaction execution in a multi-user environment.
* **Two-Phase Locking (2PL)**: Ensures serializability by acquiring all locks before releasing any. However, it can lead to deadlocks and performance issues.
* **Serializable Snapshot Isolation (SSI)**: Avoids blocking by allowing transactions to proceed and checking for conflicts at commit time, aborting transactions that are not serializable.

**Performance Considerations**

* Different isolation levels and concurrency control mechanisms impact performance.
* **Two-Phase Locking** can lead to reduced throughput due to locking and deadlocks.
* **Serializable Snapshot Isolation** provides better performance and scalability, particularly in distributed systems, but requires careful handling of transaction conflicts and aborts.

**Encapsulation of Transactions**

* Encapsulating transactions within stored procedures can reduce the network overhead associated with interactive, statement-by-statement transaction execution.
* By grouping operations within a single procedure, the system can optimize execution and reduce latency.

**Practical Applications**

* The chapter discusses the use of transactions in various practical scenarios, emphasizing the importance of choosing the right isolation level and concurrency control mechanism based on the application's requirements and workload characteristics.

**Summary**

* Transactions simplify error handling and concurrency in databases by abstracting many of the underlying complexities.
* Choosing the right isolation level and concurrency control strategy is crucial for ensuring data consistency and achieving optimal performance.
* While simple applications might manage without complex transactions, more intricate applications benefit significantly from the robustness transactions provide.



## Chapter 8: The Trouble with Distributed Systems

**Introduction**

* The chapter explores the myriad ways in which distributed systems can fail, emphasizing a pessimistic approach to understanding and handling these failures.
* Working with distributed systems introduces new failure modes not present in single-computer systems, requiring different strategies to ensure reliability.

**Faults and Partial Failures**

* On a single computer, software usually works predictably unless there is a complete failure.
* In distributed systems, partial failures are common. A system component may fail while others continue to function, necessitating fault-tolerant design.
* Engineers must build systems that continue to operate correctly even when parts of the system fail.

**Building Reliable Systems from Unreliable Components**

* The goal is to construct reliable systems despite using unreliable components.
* Examples include error-correcting codes and TCP/IP, which build reliability on top of less reliable layers.
* Despite improvements, the reliability of the higher-level system is fundamentally limited by the underlying unreliability.

**Unreliable Networks**

* Networks are inherently unreliable. Packets can be lost, delayed, or reordered.
* Communication over a network can fail in numerous ways, and software must handle these failures gracefully.
* Network faults, although seemingly rare, are frequent enough that systems must be designed to handle them.

**Network Faults in Practice**

* Network issues, even in controlled environments like datacenters, are common.
* Studies show that even with redundant hardware, human error and unforeseen issues can cause significant outages.
* Network partitions occur when parts of the network are cut off from each other, leading to split-brain scenarios and other complications.

**Detecting Faults**

* Detecting whether a node or a network link has failed is challenging.
* Timeouts are commonly used to infer failures, but they can't distinguish between different types of failures.
* Systems must be designed to tolerate both node and network failures, using strategies like redundancy and consensus protocols.

**Unreliable Clocks**

* Clock synchronization across distributed systems is difficult. Clocks can drift and be out of sync, causing issues with coordination.
* Timeouts and delays can make it hard to rely on synchronized clocks.
* Systems must account for these inaccuracies and avoid depending too heavily on clock synchronization.

**Process Pauses**

* Processes in a distributed system can pause unpredictably due to garbage collection or other reasons.
* A paused process may be mistakenly assumed to have failed, leading to incorrect failure handling.
* Designing for these pauses is essential for building robust distributed systems.

**Knowledge, Truth, and Lies**

* The state of a distributed system can be difficult to ascertain due to partial failures and network unreliability.
* Consensus protocols and algorithms help ensure that nodes in a distributed system agree on a common state.
* Byzantine faults, where nodes may act maliciously or incorrectly, further complicate achieving consensus.

**Summary**

* Distributed systems must be designed with the expectation of partial failures and unreliable components.
* Effective fault tolerance involves detecting failures, handling them gracefully, and ensuring the system continues to function.
* Despite the challenges, building reliable distributed systems is possible by understanding and mitigating the risks associated with their inherent unreliability.

These notes provide a comprehensive overview of Chapter 8, highlighting the key challenges and strategies for managing distributed systems. For a deeper dive into each section, refer to the specific pages and detailed discussions in the book "Designing Data-Intensive Applications" 【3†source】 .



## Chapter 9: "Consistency and Consensus" from _Designing Data-Intensive Applications_

**Overview**

Chapter 9 delves into the intricacies of maintaining consistency and reaching consensus in distributed systems. It explores various algorithms and protocols designed to build fault-tolerant systems, acknowledging the inherent challenges due to network unreliability and node failures.

**Key Topics Covered**

1. **Introduction to Consistency and Consensus**
   * Fault tolerance is crucial for distributed systems to function correctly despite component failures.
   * The chapter emphasizes abstractions like consensus, which can simplify the development of fault-tolerant applications by providing robust guarantees.
2. **Consistency Guarantees**
   * **Linearizability**: A strong consistency model where operations appear instantaneous and atomic. Despite its appeal for its simplicity and intuitive behavior, linearizability can be slow and expensive in systems with significant network delays.
   * **Causal Consistency**: A weaker model than linearizability that maintains the cause-and-effect relationship between operations without enforcing a total order. This model is less affected by network issues and has lower coordination overhead.
3. **Achieving Consensus**
   * The FLP Impossibility Result states that achieving consensus is impossible in a purely asynchronous system with even a single node failure. However, practical systems often circumvent this by using timeouts, randomization, or failure detectors.
   * Consensus algorithms ensure that all nodes in a system agree on a single decision, which is crucial for tasks like leader election and transaction commits.
4. **Atomic Commit and Two-Phase Commit (2PC)**
   * **Atomic Commit**: Ensures all nodes in a distributed transaction either commit or abort, maintaining atomicity. This is a specific case of the consensus problem.
   * **Two-Phase Commit (2PC)**: A widely used algorithm to achieve atomic commit, involving a prepare phase and a commit/abort phase. While commonly implemented, 2PC has limitations, particularly under failure conditions.
5. **Total Order Broadcast**
   * Ensures messages are delivered to all nodes in the same order, a crucial requirement for maintaining consistency across replicas in distributed systems.
6. **Distributed Transactions and Consensus**
   * Discusses the relationship between distributed transactions and consensus, highlighting how various problems in distributed systems can be reduced to consensus problems.
7. **Membership and Coordination Services**
   * Membership services track which nodes are currently part of the system, essential for tasks like leader election and maintaining system integrity. These services rely on consensus to ensure consistent views across the cluster.
8. **Fault-Tolerant Consensus Algorithms**
   * **ZooKeeper's Zab Protocol**: Ensures reliability and consistency in distributed systems, used in the ZooKeeper coordination service.
   * **etcd's Raft Algorithm**: A consensus algorithm designed for understandability and practical use in distributed systems, providing a balance between performance and fault tolerance.

**Summary**

The chapter concludes by summarizing the key points about consistency and consensus:

* Linearizability provides a strong consistency model but can be costly.
* Causal consistency offers a more relaxed approach, beneficial for scenarios where network partitions are common.
* Consensus is central to solving various problems in distributed systems, and while theoretically challenging, practical implementations like Zab and Raft make it achievable.

Understanding these concepts is vital for designing robust, fault-tolerant distributed systems that can gracefully handle network partitions, node failures, and other challenges inherent to distributed environments.





## Chapter 10: Batch Processing from _Designing Data-Intensive Applications_

**Overview**

Chapter 10 of _Designing Data-Intensive Applications_ focuses on batch processing, highlighting its importance, methodologies, and implementations. Batch processing systems differ from online systems (services) and stream processing systems (near-real-time systems) in their operation and performance metrics.

**Key Concepts**

1. **Batch Processing Defined**:
   * Batch processing involves handling large datasets by running jobs that process these inputs and produce outputs. These jobs can range from a few minutes to several days.
   * Typically scheduled to run periodically (e.g., daily or hourly), the main performance measure is throughput—how quickly the system can process a given dataset size.
2. **Comparison with Other Systems**:
   * **Services (Online Systems)**: Operate based on requests and responses, prioritizing response time and availability.
   * **Stream Processing Systems**: Process data shortly after it arrives, aiming for lower latency than batch systems by operating on event streams.
3. **Importance of Batch Processing**:
   * Essential for scalable, reliable, and maintainable applications.
   * Historically significant, with roots tracing back to punch card tabulating machines.
   * Modern examples include MapReduce, a batch processing algorithm known for its scalability and impact on systems like Hadoop, CouchDB, and MongoDB.

**Batch Processing with Unix Tools**

* **Unix Philosophy**:
  * Simplicity and composability: Using simple tools like `awk`, `grep`, and `sort` to build complex processing pipelines.
  * The design principles of Unix tools influence modern dataflow engines, where input immutability and composable outputs are key.

**Dataflow Engines and MapReduce**

* **MapReduce**:
  * Introduced by Google and later widely adopted in open-source projects.
  * Divides processing into two main phases: map and reduce, separated by a shuffle and sort step.
  * MapReduce jobs read from a distributed filesystem (like HDFS) and write results back to it, facilitating fault tolerance and parallel processing.
* **Partitioning**:
  * A critical aspect of distributed batch processing frameworks.
  * In MapReduce, data is partitioned for mappers based on input file blocks, then repartitioned, sorted, and merged for reducers.
* **Fault Tolerance**:
  * Achieved by frequent writes to disk, allowing recovery from task failures without restarting the entire job.
  * Dataflow engines, in contrast, keep more intermediate state in memory, trading off fault tolerance for faster execution.

**Specialization for Different Domains**

* Batch processing extends beyond business intelligence to domains like machine learning, using frameworks like Mahout and MADlib.
* Spatial and approximate search algorithms are also implemented using batch processing engines for applications in areas such as genome analysis.

**Summary of Chapter 10**

* The chapter delves into the principles of batch processing using Unix tools and modern dataflow engines like MapReduce.
* It discusses the challenges and solutions in partitioning and fault tolerance.
* Emphasizes the growing convergence between MPP databases and batch processing systems, both aiming to store and process large-scale data efficiently.

By understanding these fundamental concepts and methodologies, one can appreciate the role of batch processing in the landscape of data-intensive applications 【3†source】 .



## Chapter 11: Stream Processing

**Overview**

* **Stream Processing**: Processing data continuously as it arrives, rather than in fixed batches. This approach handles unbounded, incremental data effectively.
* **Comparison with Batch Processing**: Batch processing deals with bounded data and processes it in chunks, whereas stream processing deals with continuous data streams.

**Key Concepts**

* **Stream Definition**: A stream refers to data that becomes available incrementally over time.
* **Event**: In stream processing, an event is a small, self-contained, immutable object with details of an occurrence and usually includes a timestamp.
* **Producers and Consumers**: Producers generate events, and consumers process them. Events are grouped into topics or streams.

**Representing, Storing, and Transmitting Streams**

* **Event Representation**: Events can be encoded as text (e.g., JSON) or binary formats.
* **Storage**: Events can be stored in files, relational tables, or document databases.
* **Transmission**: Events can be sent over the network for processing by other nodes. Events generated by producers are processed by consumers, often grouped by topics.

**Databases and Streams**

* **Database Writes as Streams**: Writes to a database can be viewed as a stream of changes (changelog), useful for keeping derived systems updated.
* **Log Compaction**: Allows retention of the full state of a database by keeping the most recent update for each key.
* **Derived Data Systems**: Can be kept in sync with the primary database by consuming the changelog, enabling fresh views and up-to-date indexes.

**Stream Processing Models**

* **Complex Event Processing (CEP)**: Detects patterns within event streams using declarative queries, and outputs complex events when patterns are found.
* **Stream Analytics**: Focuses on aggregations and statistical metrics over large numbers of events, using techniques such as windowing to smooth out data.

**Processing Techniques**

* **Windowing**: Aggregating data over fixed intervals, e.g., computing average values over a 5-minute window.
* **Probabilistic Algorithms**: Used to reduce memory usage with approximate results, including Bloom filters, HyperLogLog, and percentile estimation.

**Fault Tolerance and Exactly-Once Semantics**

* **State Recovery**: Ensures state can be recovered after a failure, either through remote datastore replication or local state replication.
* **Microbatching, Checkpointing, Transactions, Idempotent Writes**: Techniques for achieving exactly-once semantics in stream processing.

**Message Brokers**

* **AMQP/JMS-style Broker**: Assigns individual messages to consumers, suitable for asynchronous RPC tasks where exact order isn't crucial.
* **Log-based Broker**: Retains messages on disk, ensuring ordered delivery and enabling message rereading if necessary. Uses partitioning for parallelism.

**Practical Uses and Implementations**

* **Updating Databases**: Streams can keep databases in sync with other systems.
* **Real-Time Alerts**: Pushing events to users through notifications or dashboards.
* **Processing Pipelines**: Complex pipelines where streams are processed through multiple stages.

**Implementations and Frameworks**

* **CEP Systems**: Examples include Esper, IBM InfoSphere Streams, and Apama.
* **Stream Analytics Frameworks**: Include Apache Storm, Spark Streaming, Flink, Samza, and Kafka Streams.

**Summary**

* Stream processing is like continuous batch processing on unbounded data streams.
* Provides mechanisms for keeping systems up-to-date, detecting complex patterns, and handling large-scale analytics in real-time.
* Essential for applications requiring low-latency updates and real-time data processing.

These notes cover the primary concepts and details of stream processing as discussed in Chapter 11 of "Designing Data-Intensive Applications" 【3†source】 .





## Chapter 11: Stream Processing

**Overview**

* **Stream Processing**: Processing data continuously as it arrives, rather than in fixed batches. This approach handles unbounded, incremental data effectively.
* **Comparison with Batch Processing**: Batch processing deals with bounded data and processes it in chunks, whereas stream processing deals with continuous data streams.

**Key Concepts**

* **Stream Definition**: A stream refers to data that becomes available incrementally over time.
* **Event**: In stream processing, an event is a small, self-contained, immutable object with details of an occurrence and usually includes a timestamp.
* **Producers and Consumers**: Producers generate events, and consumers process them. Events are grouped into topics or streams.

**Representing, Storing, and Transmitting Streams**

* **Event Representation**: Events can be encoded as text (e.g., JSON) or binary formats.
* **Storage**: Events can be stored in files, relational tables, or document databases.
* **Transmission**: Events can be sent over the network for processing by other nodes. Events generated by producers are processed by consumers, often grouped by topics.

**Databases and Streams**

* **Database Writes as Streams**: Writes to a database can be viewed as a stream of changes (changelog), useful for keeping derived systems updated.
* **Log Compaction**: Allows retention of the full state of a database by keeping the most recent update for each key.
* **Derived Data Systems**: Can be kept in sync with the primary database by consuming the changelog, enabling fresh views and up-to-date indexes.

**Stream Processing Models**

* **Complex Event Processing (CEP)**: Detects patterns within event streams using declarative queries, and outputs complex events when patterns are found.
* **Stream Analytics**: Focuses on aggregations and statistical metrics over large numbers of events, using techniques such as windowing to smooth out data.

**Processing Techniques**

* **Windowing**: Aggregating data over fixed intervals, e.g., computing average values over a 5-minute window.
* **Probabilistic Algorithms**: Used to reduce memory usage with approximate results, including Bloom filters, HyperLogLog, and percentile estimation.

**Fault Tolerance and Exactly-Once Semantics**

* **State Recovery**: Ensures state can be recovered after a failure, either through remote datastore replication or local state replication.
* **Microbatching, Checkpointing, Transactions, Idempotent Writes**: Techniques for achieving exactly-once semantics in stream processing.

**Message Brokers**

* **AMQP/JMS-style Broker**: Assigns individual messages to consumers, suitable for asynchronous RPC tasks where exact order isn't crucial.
* **Log-based Broker**: Retains messages on disk, ensuring ordered delivery and enabling message rereading if necessary. Uses partitioning for parallelism.

**Practical Uses and Implementations**

* **Updating Databases**: Streams can keep databases in sync with other systems.
* **Real-Time Alerts**: Pushing events to users through notifications or dashboards.
* **Processing Pipelines**: Complex pipelines where streams are processed through multiple stages.

**Implementations and Frameworks**

* **CEP Systems**: Examples include Esper, IBM InfoSphere Streams, and Apama.
* **Stream Analytics Frameworks**: Include Apache Storm, Spark Streaming, Flink, Samza, and Kafka Streams.

**Summary**

* Stream processing is like continuous batch processing on unbounded data streams.
* Provides mechanisms for keeping systems up-to-date, detecting complex patterns, and handling large-scale analytics in real-time.
* Essential for applications requiring low-latency updates and real-time data processing.

These notes cover the primary concepts and details of stream processing as discussed in Chapter 11 of "Designing Data-Intensive Applications" 【3†source】 .



## Chapter 12: The Future of Data Systems

**1. Data Integration**

* **Combining Specialized Tools by Deriving Data**:
  * Applications need to combine multiple pieces of software to serve diverse use cases efficiently.
  * Data integration can be achieved using batch processing and event streams.
  * Systems of record hold the authoritative data, and other systems derive data through transformations.
  * Asynchronous and loosely coupled derivations increase robustness and fault tolerance.
* **Batch and Stream Processing**:
  * These processes update derived data systems, akin to maintaining indexes or materialized views.
  * Batch processors and stream processors can be seen as implementations of triggers and stored procedures.
  * Derived data systems offer specialized indexes maintained by different software components.

**2. Unbundling Databases**

* **Composing Data Storage Technologies**:
  * Instead of a single integrated database, applications can use a combination of tools.
  * Federated databases unify reads, allowing queries across multiple storage systems.
  * Unbundled databases focus on unifying writes, ensuring data changes propagate correctly across systems.
* **Designing Applications Around Dataflow**:
  * Applications should be designed to handle dataflows as transformations between datasets.
  * This approach allows easy evolution and recovery from errors by reprocessing data with new transformations.

**3. Observing Derived State**

* Derived state can be updated by observing changes in underlying data.
* Downstream consumers can observe the derived state, enabling dynamic updates in user interfaces and offline functionality.

**4. Aiming for Correctness**

* **The End-to-End Argument for Databases**:
  * Ensuring correctness and integrity is vital.
  * Asynchronous event processing and end-to-end operation identifiers help maintain integrity.
  * Constraints can be checked asynchronously, balancing performance and correctness.
* **Enforcing Constraints**:
  * Clients can wait for checks to pass or proceed with the risk of potential constraint violations.
  * This method is scalable and robust, avoiding the need for distributed transactions.

**5. Doing the Right Thing**

* **Predictive Analytics**:
  * Predictive models and analytics derived from data can have significant impacts.
  * Ethical considerations are crucial when using data for decisions affecting people's lives.
* **Privacy and Tracking**:
  * The tech industry needs a cultural shift regarding personal data, emphasizing user respect and privacy.
  * Data should be purged when no longer needed, and access control can be enforced through cryptographic protocols.

**Summary**

* The chapter emphasizes the importance of composing various specialized tools for data integration.
* Unbundling components of a database allows for flexible, robust, and scalable application design.
* Ensuring correctness in data systems involves asynchronous processing and careful constraint management.
* Ethical considerations and respect for user privacy are paramount as data systems evolve and impact society.

These notes encapsulate the main points and insights from Chapter 12, focusing on future trends and practices in data system design and the ethical implications of handling large-scale data.



## Chapter 13: Data Integration

**Overview**

Chapter 13 of "Designing Data-Intensive Applications" focuses on the challenges and methods of integrating data from various sources. It explores the complexities of combining data stored in different formats and managed by different systems into a cohesive, unified view.

**Key Concepts**

1. **Data Silos**: Different departments or systems within an organization often manage their own data independently, leading to isolated data silos. Integrating these silos is crucial for providing a unified view of the organization's data.
2. **ETL (Extract, Transform, Load)**: ETL processes are fundamental in data integration:
   * **Extract**: Retrieving data from various sources.
   * **Transform**: Converting the extracted data into a format suitable for analysis or further processing.
   * **Load**: Storing the transformed data into a target database or data warehouse.
3. **Data Warehousing**: A central repository where data from different sources is stored in a structured manner. Data warehouses support complex queries and analysis, providing a consolidated view of the organization's data.
4. **Batch and Stream Processing**: Two primary methods for processing data:
   * **Batch Processing**: Collecting and processing data in large chunks at regular intervals.
   * **Stream Processing**: Continuously processing data as it is generated, allowing for real-time analysis and reaction.
5. **Data Lakes**: A more flexible approach than traditional data warehouses, data lakes store raw data in its original format. They allow for diverse types of data (structured, semi-structured, and unstructured) to be stored together, enabling advanced analytics and machine learning.
6. **Data Integration Tools**: Various tools and platforms help in data integration, including:
   * **Apache Kafka**: A distributed event streaming platform capable of handling real-time data feeds.
   * **Apache Nifi**: A data integration tool that supports data routing, transformation, and system mediation logic.
7. **Schema Integration**: Harmonizing the schemas from different data sources is a significant challenge. Schema-on-read (interpreting data schema as it is read) versus schema-on-write (enforcing schema when data is written) approaches are discussed.
8. **Data Quality**: Ensuring the quality and consistency of integrated data is critical. This involves dealing with duplicates, inconsistencies, and missing data.
9. **Metadata Management**: Keeping track of metadata (data about data) is crucial for understanding and managing the integrated data. Metadata helps in tracking the lineage, source, and transformation of data.
10. **Data Governance**: Establishing policies and procedures for managing data integration processes to ensure data security, privacy, and compliance with regulations.

**Practical Considerations**

* **Scalability**: The integration system must handle increasing volumes of data efficiently.
* **Fault Tolerance**: Systems should be resilient to failures and capable of recovering from disruptions.
* **Latency**: Minimizing the delay between data generation and availability for analysis is critical, especially for real-time use cases.
* **Consistency**: Ensuring data consistency across different sources and transformations is essential for reliable analytics.

**Conclusion**

Data integration is a complex but essential aspect of modern data management, enabling organizations to unlock the full potential of their data. By addressing the technical challenges and employing the right tools and techniques, organizations can achieve a unified, comprehensive view of their data landscape.

