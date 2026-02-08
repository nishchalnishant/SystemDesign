# System design



## <mark style="color:purple;">EP 1: Database design</mark>

* For staters --
  * if someone wants to access data not on local system we need database
* Objective
  * Fast reads
  * Fast writes
  * Persistent
* How do we do this&#x20;
  * we store this in disks, much cheaper than SSDs but slower
* Implementation of database&#x20;
  * Naive database implementation&#x20;
    * Literally just a **list** O(n) reads and writes
    * if we were to write a new entry we would have to search and update&#x20;
  * Slightly better implementation&#x20;
    * Append only log on disk to take advantage of sequential logs
    * To see what is the latest value we go from bottom and find the value the first one is the latest value
  * Better data base implementation&#x20;
    * Hashmap O(1) reads and writes&#x20;
    * but this does not scale as the second there is too much data we are in trouble&#x20;
    * hashmap has to go on disk which becomes slow
  * Indexes — makes reads times much faster
    * Keeps extra data on each write to improve database read times&#x20;
    * pro — faster reads
    * Con — slower writes(only use indexes if we need them do not declare index for every field)
    * Types of index implementation&#x20;
      * hash index
        * Keep an in memory of hash table of the key mapped to the memory location of the corresponding data ocassionally write to disk for persistance
        * pros — easy to implement and very fast (disks are slow, RAM is fast)
        * Cons — All the keys must fit in the memory if one wants to do range query
        *

            <figure><img src="../../../.gitbook/assets/image (56).png" alt="" width="372"><figcaption></figcaption></figure>
      * lsm tree+SStables
        *

            <figure><img src="../../../.gitbook/assets/image (57).png" alt=""><figcaption></figcaption></figure>
        * Write first goes to an in memory balanced binary binary search tree(memtable) eventually written to disk
        * when tree becomes too large write the contents of it sorted by the key name to an sstable file
        * TO increate persistance keep log on disk of memtable writes to restore it in the event of a crash
        * how it works&#x20;
          * Tree gets written to the SSTable files , where the keys are sorted&#x20;
          * But since we are using only append only logs there will be duplicate keys in the compacted SSTable.&#x20;
          * this can be merges in O(n) time. In case of duplicate keys we are going to take the latest one&#x20;
          * For reads&#x20;
        * Pros :thumbsup:
          * High write throughput due to writes going to in memory buffer
          * Good for range queries due to internal sorting of data in the index
        * Cons
          * SLow reads, especially if the key we are looking for is old or does not exists&#x20;
          * Merging process of log segments can take up background resources
      * B tree
        * Model the data such that it is a tree
        *

            <figure><img src="../../../.gitbook/assets/image (58).png" alt=""><figcaption></figcaption></figure>

            First one has reference from a to e and second one is from p to z
        * To read: just traverse throguh the tree and find the value
        * To update : traverse through the tree and change the value
        * To write : traverse through the tree if there is extra space in the block where the value belongs add the key otherwise you will have to split the location block in two add the key and then update hte parent block to reflect this action. Can be made durable in the vent of crashes using a write ahead log
        * Pros :thumbsup:
          * Relatively fast reads, most B-tree can be stored in only 3 or 4 levels
          * Good for range queries as data is kep internally stored
        * Cons:
          * Relatively slow writes, have to write to disk as opposed to memory
  * Conclusion&#x20;
    * In a system it is important to know what type of databse engine/design we are using so that we can optimize of writes or reads
    * Hash indexes:&#x20;
      * Fast but only useful on small datasets
      * ex — redis
    * SSTables and LST trees:&#x20;
      * better for writing, slower for readings
      * ex — BigTable, Dynamo, HBase, Cassandra, LevelDB, RocksDB, and AsterixDB
    * Btrees :&#x20;
      * better for reading, slower for writing&#x20;
      * ex - MySQL, Postgres, MongoDB

## <mark style="color:purple;">EP 2: Single leader replication</mark>&#x20;

* replication  Having redundant copies od db so that the application does not fail in the event of crash, reduce load on each db serving request
* types&#x20;
  * single leader
  * multi leader
  * leader less
* Single leader replication overview&#x20;
  * All writes to one master db&#x20;
  * master db sends list of writes to other db via replication log
  * reads can come from any db
  * Increasing availability:
    * Adding a follower
      * Initialize the follower using a Consistent snapshot of the leader db which is associated with some position in the replication log
      * THe follower can now begin accepting changes from the leader&#x20;
    * One follower crash
      * At the time of crashs the folower knows where it was up to the replication log
        * step 1: on reboot, fetch all the new changes from the leader node
        * Start implementing the changes in the replication log from the index at which the follower node has previously failed&#x20;
    * on leader crash (failover)
      * &#x20;Determine a new leader via some source of consensus( perhaps the most up to data replica)
      * configure all the clients to send writes to the new leader&#x20;
      * Configure all the other follower to get changes from the new leader
      * Problems with failover&#x20;
        * Some writes from the previous leader may have been propagates by only some or no relica (leads to either lost data or inconsistent replicas)
        * Accidental Failover due to network congestion can hurt our db performance even more
        * If the old leader comes back, need to ensure that it does not think it is the leader and continue to accept writes(split brain)
  * Types&#x20;
    * Synchronous consistency
      * client does not receive success message untill all replicas complete the write
      * strong consistency
      * tradeoff -— data always up to date but writes takes much longer
    * Asynchronous consistency
      * client receives success message the second that master complete the write
      * eventual consistency
      * tradeoff — writes much faster but client can make stale reads to a replica
  * Dealing with eventual consistency
    * Reading your own writes
      * Don't see write after just making it.
      * you may write a change read from a replica and then not see the change
      * Solution:&#x20;
        * problem Always read from the leader for editable areas of application
        * solution always read from the leader or up to date replica for some period after a client write
    * Monotonic reads
      * Reads look like they are going back in time.
      * Fix by having each user reads from the same replica.
      * problem if each user is reading from different replica then they will see different versions as the data will take time to replicate&#x20;
      * solution — make user read from a single replica by hashing the user id to that replica
    * Consistent prefix reads
      * Casual relationships between writes lost because preceding write takes longer to replicate (due to being on different partitions)
      * Put casually related events on same partition or if not possible keep track of casual dependencies)
      * solution: get msg of a chat to a single partition or for each msg get a timestamp and load all the msg before that timestamp
  * replication log implementation option
    * Copy over sql statement&#x20;
      * Bad because SQL statements are non deterministic
    * USer internal write ahead log
      * not scalable if changing db engine , says which bytes were changes, other database my have different data in different location on disk
    * Use logical log
      * describes which rows were modified and how allows for more future proofing if the underlying database engine changes in the future
* Conclusion --
  * pros&#x20;
    * all writes goes through one machine which ensures consistency across replicas
  * Cons
    * Since all writes have to go through one machine write throughput may not be acceptable
      * either need to partition the dataset and have a single leader per partition or look towards other replication strategies.

## EP 3: Multileader replication

* Types of replication&#x20;
  * single&#x20;
  * multi
  * no
* Multi layer replication&#x20;
  * Multiple leader db, possibilly across data center
  * leader db send each other writes through some pre existing topology
  * reads can come from any db
* topology
  * circuiler topology
  * start topology
  * all to all topology
  * Circle and star topologies can easily fail(any node crash for circle, central node crash for star)
  * all to all topology can have writes delivered out of order due to race condition
* Pros&#x20;
  * Can have a leader in each data center making having a global service more managable (outages between datacenter do not break application)
  * are not limited to the write throughput of a single node
* Cons
  * having to deal with write conflicts between multiple leader.
* Conflict resolution&#x20;
  * Conflict avoidance&#x20;
    * easiset way to deal with conflicts is to just avoid them&#x20;
    * have all writes to the same item go to a given leader&#x20;
    * not always possible if leader is down or you want to change your db configuration
  * Last write wind&#x20;
    * each write is assigned a timestamp, write with latest timestamp is kep
      * pros&#x20;
        * very easy to implement
      * Cons&#x20;
        * do we use the client or the server timestamp
        * writes will be lost
        * clock skew on servers means that their times are not exactly synchronised
  * On read
    * when db detects concurrent conflicting writes store both writes&#x20;
    * return both values to a user on the next read to manually merge the vaules and store the resulting merged values in the db
  * On write&#x20;
    * When db detects concurrent writes, call snippent of code to merge them somehow(often application specific)
    * this is the idea between conflict free replicated data types
* Detecting Concurrent writes
  * two writes are concurrent if each client did not know about the write of the other when they made the write, has nothing to do with actual time of write
  * if  one client know about the write of the other we would have a causality relationship between the two
  * Hence detecting concurrent writes is all about keeping track of what a client has seen from the db before making a write&#x20;
  * So we use version vectors&#x20;
    * For each key keep track of an increasing version number for each key for each replica
    *

        <figure><img src="../../../.gitbook/assets/image (59).png" alt=""><figcaption></figcaption></figure>


    * Version vector semantics&#x20;
      * every time a client reads from a db the db giest it the version vector of a key
      * every time a client writes to a db it passes the db its most recently read version vector for a key and the db supplies it with a new one&#x20;
      * Two values have a hapens before relationship if one version vector is strictly greater than the other (for each element of the list, the number is greater than or equal to the corresponding element of the other list)
      * All the  version vectors are concurrent&#x20;
        * the corresponding values can either be mwerged somehow or kept as siblings in the db
        * Merging version vectors means taking the max of both version vectors at every index.



## EP 4: Leaderless replication&#x20;

* Any replica can accept a write from a client. ex — Amazon DynamoDB, Cassandra and Voldemort
* sends reads and writes to all nodes in parellel, once a certain predefined threshold of nodes return a success value, the client is told that the read/write was successful
* We are dealing with a lot of network calls
* Keeping data up to date
  * Anti entropy
    * Background process that looks at multiple nodes and their stored values, user version numbers of the data in order to attempt to make sure that each replica holds the most up to date copy of the data.
  * Read repair
    * idea: since we are reading from multiple replicas in parellel, take the most up to date piece of data and propagate it tow the other replicas that had outdated data.
    * Problem: How can we guarantee that at least one of the replicas we read from has the most up to date data&#x20;
    * quoroms&#x20;
      * idea : [https://youtu.be/atDe7qka6q8?list=PLjTveVh7FakKjb4UYzUazqBNNF-WGurXp\&t=266](https://youtu.be/atDe7qka6q8?list=PLjTveVh7FakKjb4UYzUazqBNNF-WGurXp\&t=266)
* Conclusion&#x20;
  * pros&#x20;
    * Can work quite well in practice with across datacenter solution by having quorom write parameter be small enough that all writes can go to one datacenter
  * Cons
    * Slower reads as a result of mulitple queries to nodes(if using reads repair)
    * still have to reason about write conflicts
    * quoroms are not perfect, provide illusion of strong consistency when in reality this is often not true.

## EP 5: Partitoning&#x20;

*   **Partitioning**

    * Partitioning is the process of dividing a large database table into smaller chunks, each stored on a different node.
    * It is often used in conjunction with replication to ensure data availability and durability.
    * Objectives of partitioning:
      * Ensure each partition has a similar amount of data and queries.
      * Avoid hotspots, where one partition has significantly more data or queries than others.
    * **Partitioning Methodologies**
      * **Key Range Partitioning:**
        * Divides data into ranges based on keys.
        * Pros: Simple, effective for range queries.
        * Cons: Prone to hotspots, difficult to predict query patterns and balance data evenly.
      * **Hash Range Partitioning:**
        * Divides data based on the hash of the keys.
        * Pros: Evenly distributes data across nodes.
        * Cons: Cannot perform range queries efficiently, still susceptible to hotspots if certain keys have high activity.
    * **Indexes in Partitioned Databases**
      * **Local Indexes:**
        * Stored within each partition, only containing data for that partition.
        * Pros: Fast for writes.
        * Cons: Slow for reads, requires checking every partition.
      * **Global Indexes:**
        * Partitioned across multiple nodes, containing data from multiple partitions.
        * Pros: Fast for reads.
          * Cons: Slow for writes, requires writing to multiple nodes, potential for write failures.
    * **Rebalancing Partitions**
      * **Fixed Number of Partitions:**
        * A fixed number of partitions is maintained, regardless of the number of nodes.
        * Pros: Simple to implement.
        * Cons: Difficult to choose the optimal number of partitions, can lead to imbalanced partitions if not chosen carefully.
      * **Dynamic Partitioning:**
        * The database automatically creates or merges partitions based on size or load.
        * Pros: Adapts to changing data volumes.
        * Cons: Can lead to unnecessary rebalancing, especially during network issues.
      * **Fixed Number of Partitions per Node:**
        * Each node maintains a fixed number of partitions.
        * Pros: Scales well with increasing data volumes.
        * Cons: Requires careful rebalancing algorithms to avoid hotspots.

    **Summary**

    * Partitioning adds complexity to database systems but is often necessary for scaling to handle large datasets.
    * Careful consideration must be given to partitioning methodology, index choices, and rebalancing strategies to ensure optimal performance and avoid hotspots.
    * Additional components like routing tiers or coordination services are often required to manage partitioned databases.

    **Additional Notes**

    * The video also discusses the challenges of dealing with hotspots and the importance of choosing appropriate hash functions.
    * It mentions the use of consistent hashing for rebalancing partitions.
    * The video provides a good overview of the key concepts and trade-offs involved in database sharding.



## EP 6 ACID transactions

*   Here are the detailed notes from the video:

    **Introduction**

    * The video starts with a brief introduction to transactions and their purpose.
    * The speaker mentions that they will be covering transactions in two parts, with this part focusing on the ACID properties and different isolation levels.

    **ACID Properties**

    * **Atomicity:** All operations within a transaction must either succeed completely or fail completely. There should be no partial successes.
    * **Consistency:** A transaction must leave the database in a valid state. The database's invariants must hold after a transaction completes.
    * **Isolation:** Concurrent transactions should not interfere with each other. Each transaction should appear to be the only one executing on the database.
    * **Durability:** Once a transaction is committed, its changes must be persistent and survive system failures.

    **Implementing Isolation**

    * **Serial Execution:**
      * The simplest approach, where transactions are executed one after another.
      * Requires all data to be in memory and uses stored procedures.
      * Limited throughput due to single-threaded execution.
    * **Two-Phase Locking (2PL):**
      * More common approach, uses locks to control access to data.
      * Locks can be shared (for reading) or exclusive (for writing).
      * Transactions acquire locks in the growing phase and release them in the shrinking phase.
      * **Predicate Locks:** Locks on data that may not exist yet, used to prevent concurrent modifications.
      * **Index Range Locking:** An optimization for predicate locks, where locks are placed on a range of indexes instead of individual rows.
      * **Performance Issues:** 2PL can lead to performance bottlenecks due to excessive locking and deadlocks.
    * **Serializable Snapshot Isolation (SSI):**
      * A more recent approach that allows concurrent execution and only aborts transactions if a concurrency issue is detected.
      * Reads are taken from a snapshot of the database.
      * Transactions are aborted if they read uncommitted writes that later commit or if they read data that is subsequently modified by another transaction.
      * **Performance:** Generally better performance than 2PL, especially in low-concurrency scenarios.

    **Conclusion**

    * Transactions are a powerful abstraction for managing data consistency but can impact performance due to the need for isolation.
    * The video concludes with a brief overview of the three isolation implementation methods and a preview of the next video, which will cover weaker isolation levels.

    **Additional Notes**

    * The speaker uses humor and informal language throughout the video.
    * The video includes visual aids such as diagrams and code examples to illustrate concepts.
    * The speaker emphasizes the importance of understanding isolation levels for building scalable database applications.

    I hope these notes are helpful! Let me know if you have any other questions.



## EP 7&#x20;



*   Sure, here are the detailed notes from the video:

    **Introduction**

    * The video starts with a brief introduction to weak isolation levels and their purpose.
    * The speaker mentions that they will be covering four weak isolation levels in this part: Read Committed, Read Uncommitted, Repeatable Read, and Snapshot Isolation.

    **Read Committed**

    * Transactions read committed data from other transactions.
    * Dirty reads (reading uncommitted data) are not allowed.
    * Phantom reads (reading data that is inserted or deleted by another transaction) are possible.
    * Can lead to inconsistent reads if multiple transactions are modifying the same data.

    **Read Uncommitted**

    * Transactions can read both committed and uncommitted data from other transactions.
    * Dirty reads are allowed.
    * Phantom reads are possible.
    * Can lead to inconsistent reads and lost updates.

    **Repeatable Read**

    * Transactions read committed data from other transactions.
    * Dirty reads are not allowed.
    * Phantom reads are prevented using next-key locking.
    * Can lead to write skew, where two transactions try to update the same data and one of them fails.

    **Snapshot Isolation**

    * Transactions read data from a snapshot of the database taken at the start of the transaction.
    * Dirty reads are not allowed.
    * Phantom reads are prevented.
    * Can lead to write skew, but less likely than with Repeatable Read.

    **Choosing an Isolation Level**

    * The choice of isolation level depends on the application's requirements for consistency and performance.
    * Stronger isolation levels (e.g., Repeatable Read, Snapshot Isolation) provide more consistency but can lead to lower performance.
    * Weaker isolation levels (e.g., Read Committed, Read Uncommitted) can provide higher performance but can lead to less consistency.
    * The speaker recommends choosing the weakest isolation level that satisfies the application's requirements.

    **Conclusion**

    * Weak isolation levels can be used to improve performance in certain scenarios, but they can also lead to less consistency.
    * The video concludes with a brief overview of the four weak isolation levels and a reminder of the importance of choosing the right isolation level for your application.



## EP8: SQL and its Pitfalls" by Jordan has no life:

**Introduction**

* The video starts with a brief introduction to the topic of relational databases and their limitations.
* The speaker mentions that he will be discussing the pitfalls of relational databases and exploring alternative options in subsequent videos.

**Relational Databases: Background**

* **Definition:** Relational databases are comprised of tables with rows of structured data.
  * They use a predefined schema, meaning all columns are defined beforehand.
  * Relationships between tables are established through foreign keys.
  * They utilize a declarative query language (SQL) and a query optimizer for efficient data retrieval.
* **Popular Examples:** MySQL, PostgreSQL
* **Implementation Details:**
  * Typically use B-trees for data storage.
  * Support transactions using two-phase locking.
  * All reads and writes are performed to disk.

**Scaling Relational Databases**

* **Traditional Approach:** Vertical scaling (using more powerful machines).
* **Modern Approach:** Horizontal scaling (sharding or partitioning data across multiple nodes).
* **Challenges of Sharding:**
  * **Write Operations:**
    * Distributed transactions are required to ensure data consistency across multiple shards.
    * This can be slow and resource-intensive due to network communication.
  * **Read Operations:**
    * May require querying multiple shards to retrieve complete data.
    * Increases latency and complexity.

**Problems with Relational Database Philosophy**

* **Data Duplication:**
  * Emphasis on normalization can lead to data duplication when sharding.
  * Changes to related data require updates across multiple locations.
* **Schema Limitations:**
  * Predefined schema can hinder flexibility and adaptability to evolving data requirements.
* **Join Operations:**
  * Can be slow and inefficient when data is distributed across multiple shards.
* **Transaction Overhead:**
  * Two-phase locking can introduce significant overhead, especially in high-concurrency scenarios.
* **Performance Bottlenecks:**
  * B-trees can be slow for write operations compared to in-memory data structures.
  * Single-leader replication can limit write throughput.

**NoSQL Databases**

* **Key Characteristics:**
  * **Self-contained Documents:** Data related to an object is stored together in a single document.
  * **Schema-less:** Allows for flexible data structures and easier data evolution.
  * **Data Duplication:** May involve some data duplication for improved performance and scalability.
* **Design Patterns:**
  * **Document Databases:** Suitable for applications with self-contained data and limited relationships.
  * **Graph Databases:** Ideal for representing complex relationships and social networks.

**Conclusion**

* Relational databases are well-suited for certain applications but can have limitations in terms of scalability and performance.
* NoSQL databases offer alternative approaches that can be more suitable for specific use cases.
* The choice of database depends on the specific requirements of the application.

**Additional Notes**

* The video provides a high-level overview of the concepts and does not delve into specific NoSQL technologies.
* The speaker emphasizes the importance of understanding the trade-offs between different database options.
* The video encourages viewers to consider alternative approaches like "NewSQL" databases that aim to improve the scalability of the relational model.

I hope these notes are helpful! Let me know if you have any further questions.



## EP9: Data warehousing" by Jordan has no life:

**Introduction**

* The video starts with an apology for not delivering a promised database case study.
* The speaker explains the reasons for the delay:
  * Lack of time due to an upcoming vacation.
  * The need to cover prerequisite concepts not yet discussed in the series.
* He promises to deliver the case study in a future video.
* The video then transitions to the topic of analytical databases.

**Analytical Databases: Purpose**

* Businesses often need to run complex queries on large datasets for analysis (e.g., A/B testing, data science).
* These queries can be time-consuming when executed on the transactional database (used for client interactions).
* Analytical databases are dedicated systems for running these queries efficiently.

**ETL Process**

* Data is extracted from the transactional database and loaded into the analytical database.
* This process is typically automated and scheduled as a batch job.

**Star and Snowflake Schemas**

* **Star Schema:** A central fact table contains core data with foreign keys referencing dimension tables.
* **Snowflake Schema:** An extension of the star schema where dimension tables themselves have further dimension tables.
* These schemas optimize data organization for analytical queries.

**Column-Oriented Storage**

* **Traditional Databases:** Use row-oriented storage, storing entire rows together.
* **Analytical Databases:** Use column-oriented storage, storing all values of a single column together.
* **Benefits:**
  * Improved compression due to high data redundancy within columns.
  * Better CPU cache utilization.
  * Enhanced parallelization.
* **Implementation:**
  * Columns are stored in sorted order.
  * Writes are buffered in an in-memory LSM tree and periodically merged into sorted column files.
  * Reads require merging data from both the tree and column files.

**Column Compression**

* **Bitmap Encoding:** Represents column values using a bitwise representation (1 for presence, 0 for absence).
* **Run-Length Encoding:** Compresses bitmap encodings by representing sequences of 1s and 0s with their lengths.

**Column Sorting**

* Creates sorted replicas of the database for specific columns.
* Improves query performance, especially for range queries.
* Enables further compression due to increased data locality.

**Materialized Views**

* Pre-computed results of common queries.
* Improve query performance by avoiding repeated calculations.
* Increase write latency due to the need to update materialized views.

**Data Cubes**

* Multi-dimensional materialized views that pre-compute aggregations across multiple dimensions.
* Example: Sales of each product on each day.

**Summary**

* Analytical databases are optimized for complex read queries.
* Column-oriented storage, compression, and materialized views are key techniques for improving performance.
* The ETL process is crucial for populating the analytical database.

**Note:**

* The video provides a high-level overview of the concepts.
* It does not delve into specific technologies or implementation details.

I hope these notes are helpful!



## EP10: Unreliable clocks" by Jordan has no life:

**Introduction**

* The video starts with a brief introduction and a disclaimer about the short length of the episode.
* The speaker mentions that he is currently on vacation and trying to upload as many videos as possible.
* The topic of the episode is unreliable clocks in distributed systems.

**The Problem with Relying on System Clocks**

* In a distributed system with multiple nodes, conflicts can arise when determining the order of events.
* Timestamps seem like a straightforward solution, but they are unreliable due to several factors:
  * **NTP Synchronization:**
    * Network time protocol (NTP) synchronizes system clocks with external servers.
    * Network latency introduces inaccuracies in the received time.
  * **Quartz Clock Drift:**
    * Internal clocks in computers have slight variations in their oscillation rates, leading to gradual time discrepancies.
  * **Clock Skew:**
    * Even with NTP, significant clock skew can occur, especially in geographically distributed systems.
  * **Client-Side Clock Manipulation:**
    * Users can easily modify their system clocks, potentially affecting the order of events.
  * **Leap Seconds:**
    * Irregular adjustments to the global clock can disrupt time-based ordering.

**Why System Clocks are Unreliable for Elapsed Time**

* **Clock Jumps:**
  * Sudden synchronization with NTP can cause significant jumps in the system clock, making elapsed time calculations inaccurate.
* **Leap Seconds:**
  * These unpredictable adjustments can introduce errors in elapsed time measurements.

**Alternatives to System Clocks**

* **Monotonic Clocks:**
  * These clocks provide a continuous, increasing count of elapsed time, independent of the system clock.
  * More suitable for measuring time intervals within a system.
* **Logical Timestamps:**
  * Techniques like version vectors and Lamport timestamps provide a more reliable way to order events in a distributed system.

**Consequences of Relying on Unreliable Clocks**

* **Data Loss:**
  * In "last write wins" scenarios, incorrect timestamps can lead to data being overwritten by older writes.
* **Concurrency Issues:**
  * Incorrect ordering of events can cause unexpected behavior and data inconsistencies.

**Practical Considerations**

* **Cassandra:**
  * A popular NoSQL database that relies on timestamps for conflict resolution.
  * Emphasizes the importance of keeping servers synchronized with NTP.
* **Google Spanner:**
  * Utilizes specialized hardware with GPS clocks for high-precision time synchronization.
  * Enables consistent snapshots based on accurate time intervals.

**Conclusion**

* System clocks are inherently unreliable in distributed systems.
* Relying on them for event ordering can lead to significant issues.
* Logical timestamps and other techniques provide more robust solutions for handling concurrency and ensuring data consistency.

**Note:**

* The video provides a high-level overview of the concepts.
* It does not delve into the technical details of NTP, clock synchronization algorithms, or specific implementations of logical timestamps.

I hope these notes are helpful!



## EP11: Fencing Tokens" by Jordan has no life:

**Introduction**

* The video starts with a brief introduction and an apology for the short length and somewhat lazy nature of the episode.
* The speaker explains that he is about to leave for vacation and wanted to squeeze in one more video before leaving.
* The topic of the episode is fencing tokens.

**Consensus in Distributed Systems**

* The video introduces the concept of consensus in distributed systems.
* Consensus algorithms aim to ensure that only one node in a distributed system performs a specific action (e.g., being the leader, holding a lock).
* This is crucial for maintaining data consistency and preventing conflicts.

**Unreliable Nodes**

* Even in controlled environments, nodes in a distributed system can become unreliable.
* Possible causes of unreliability include:
  * **Process Preemption:**
    * Processes can be paused by the operating system (e.g., garbage collection, virtual machine migration).
  * **Hardware Failures:**
    * Node crashes, power outages, network disruptions.
  * **Software Bugs:**
    * Unexpected behavior or errors in the node's software.

**Impact of Unreliable Nodes**

* Unreliable nodes can disrupt consensus mechanisms.
* Examples:
  * **Leader Election:** If the elected leader fails, other nodes may incorrectly assume leadership, leading to a split-brain scenario.
  * **Distributed Locks:** If a node holding a lock fails, other nodes may incorrectly acquire the lock, leading to data corruption.

**Process Pauses and Lease Expiration**

* The video illustrates the problem of process pauses using the example of a distributed lock with a lease.
* If a node holding the lock is paused during garbage collection, its lease may expire.
* Another node can then acquire the lock, leading to data corruption when the original node resumes execution.

**Fencing Tokens**

* Fencing tokens are a mechanism to prevent incorrect actions by unreliable nodes.
* Each time a consensus decision is made (e.g., electing a leader, granting a lock), a unique, monotonically increasing token is issued.
* This token is associated with the decision.
* When a node attempts to perform an action based on an old decision, it must present the corresponding token.
* If the presented token is outdated, the action is rejected.

**Example**

* The video uses a simple example to illustrate how fencing tokens work.
* Client 1 acquires a lock with token 33.
* Client 1 is paused, and its lease expires.
* Client 2 acquires the lock with token 34.
* Client 1 resumes execution and attempts to write data.
* The storage system rejects the write because it has a more recent token (34).

**Benefits of Fencing Tokens**

* Prevent actions based on outdated information.
* Ensure that consensus decisions are respected.
* Improve data consistency and prevent conflicts.

**Conclusion**

* Fencing tokens are a valuable mechanism for ensuring the correctness of consensus algorithms in distributed systems.
* They provide a simple yet effective way to handle unreliable nodes and prevent unexpected behavior.

**Note:**

* The video provides a high-level overview of the concepts.
* It does not delve into the technical details of specific consensus algorithms or implementations of fencing tokens.

I hope these notes are helpful!



## EP12: Linearizability and Ordering" by Jordan has no life:

**Introduction**

* The video starts with Jordan returning from his brother's wedding and expressing his dissatisfaction with his subscriber count.
* He jokingly threatens to "outsource" his content creation to someone who can generate views with "day in their life" videos.
* He then transitions to the main topic: linearizability and ordering.

**Linearizability**

* **Definition:** Linearizability is equivalent to strong consistency.
  * Once an object is written to any replica, all subsequent reads must return the updated value.
  * No stale reads are allowed.
* **Key Points:**
  * Linearizability guarantees consistency per object, not across multiple objects within a transaction.
  * It does not prevent race conditions within a single transaction.
* **Use Cases:**
  * Situations where only one "source of truth" is required (e.g., distributed locks, uniqueness constraints).

**Ordering**

* **Total Order:**
  * Every operation must have a defined order relative to all other operations.
  * This order must be consistent across all replicas.
  * Ensures that all replicas have the same view of the data's history.
* **Partial Order:**
  * Some operations may not have a defined order relative to each other.
  * Only preserves causality (if A happens before B, then B cannot be read before A).
  * Example: Version vectors.

**Creating an Order**

* **Single-Leader Replication:**
  * A right-ahead log can easily establish a total order.
* **Multi-Leader Replication:**
  * More challenging to achieve a total order.
  * Requires techniques like Lamport timestamps or version vectors.

**Lamport Timestamps**

* **Mechanism:**
  * Each operation is assigned a timestamp consisting of a counter and a node ID.
  * Counters are incremented locally.
  * If a node encounters a higher counter, it updates its own counter.
  * Node IDs are used to break ties when counters are equal.
* **Advantages:**
  * Simple to implement.
  * Low space overhead.
* **Disadvantages:**
  * Only provides a total order retroactively.
  * Cannot express concurrent operations.
  * Can lead to data loss in case of conflicts.

**Version Vectors**

* **Mechanism:**
  * Each replica maintains a vector of counters, one for each replica.
  * Counters are incremented locally.
  * Version vectors are included with each operation.
  * Allows for detecting concurrent operations.
* **Advantages:**
  * Can express concurrent operations.
* **Disadvantages:**
  * Higher space overhead compared to Lamport timestamps.

**Total Order Broadcast**

* **Definition:**
  * A protocol that ensures all messages are delivered to all nodes in the same order.
* **Relationship to Linearizability:**
  * Total order broadcast can be used to implement linearizable storage.
  * Linearizable storage can be used to implement total order broadcast.

**Consensus**

* **Definition:**
  * The problem of achieving agreement among a group of nodes in a distributed system.
* **Relationship to Linearizability and Ordering:**
  * Consensus algorithms are crucial for implementing both linearizability and total order broadcast.

**Conclusion**

* Linearizability and ordering are fundamental concepts in distributed systems.
* Achieving strong consistency requires careful consideration of ordering and consensus.
* The choice of techniques depends on the specific requirements of the application.

**Note:**

* The video provides a high-level overview of the concepts.
* It does not delve into the technical details of specific consensus algorithms or implementations.

I hope these notes are helpful!

## EP13: Two Phase Commit" by Jordan has no life:

**Introduction**

* Jordan apologizes for the delay in posting the video, citing his roommate being home all day as the reason.
* He acknowledges the inevitability of his friends finding out about his YouTube channel.
* He emphasizes the importance of this video as it delves into consensus algorithms, starting with two-phase commit.

**Atomic Commit**

* **Definition:** Ensures that a distributed transaction either succeeds on all participating nodes or fails on all of them.
* **Use Cases:**
  * **Partitioned Databases:** Transactions spanning multiple partitions.
  * **Global Secondary Indexes:** Maintaining consistency across secondary indexes.
  * **Derived Data Consistency:** Keeping data warehouses and caches in sync.
  * **Preventing Anomalies:** Ensuring that emails are only sent after successful writes.

**Two-Phase Commit (2PC)**

* **Components:**
  * **Coordinator Node:** Manages the commit process.
  * **Participant Nodes:** Nodes involved in the transaction (e.g., database partitions).
* **Phases:**
  * **Prepare Phase:**
    * Coordinator sends a "prepare" request to all participants.
    * Participants check if they can commit the transaction and respond with "yes" or "no."
    * If a participant responds "no," the coordinator aborts the transaction.
  * **Commit/Abort Phase:**
    * If all participants respond "yes," the coordinator sends a "commit" request to all participants.
    * If any participant responds "no," the coordinator sends an "abort" request to all participants.

**Key Concepts**

* **Monotonically Increasing Transaction IDs:** Unique identifiers for each transaction to prevent confusion.
* **Local Transactions:** Each node executes its own local transaction with ACID properties.
* **Commit Point:** Once a participant responds "yes," it is committed to the transaction and will eventually commit it.
* **Coordinator Log:** The coordinator maintains a log of transaction decisions for fault tolerance.

**Issues with Two-Phase Commit**

* **Blocking:** If a participant fails after the prepare phase, the coordinator is blocked indefinitely.
* **Single Point of Failure:** The coordinator node is a single point of failure.
* **In-Doubt Transactions:** If the coordinator fails, participants that have already prepared may be unable to commit or abort, leading to blocked resources.

**Heterogeneous vs. Database Internal Transactions**

* **Heterogeneous Transactions:** Use a generic XA interface, limiting optimizations.
* **Database Internal Transactions:** Allow for more efficient implementations within a specific database system.

**Conclusion**

* Two-phase commit is a basic consensus algorithm with limitations.
* Subsequent videos will explore more advanced consensus algorithms like Paxos, Raft, and Zab.

**Additional Notes**

* Jordan uses humor and informal language throughout the video.
* He provides clear explanations and uses diagrams to illustrate concepts.
* He acknowledges the complexity of the topic and promises to provide more in-depth explanations in future videos.

I hope these notes are helpful!



## EP14: Raft in 15 minutes" by Jordan has no life:

**Introduction**

* Jordan introduces Raft, a consensus algorithm popularized for its ease of understanding.
* He aims to demonstrate the algorithm's simplicity by explaining it within 15 minutes.

**Raft: Core Concepts**

* **Consensus Algorithm:** Raft ensures that all nodes in a distributed system agree on a single decision.
* **Replicated Log:** Raft creates a replicated log across all nodes, where each entry represents a decision.
* **Fault Tolerance:** Raft can tolerate node failures while maintaining consistency.
* **Leader Election:** Only one node (leader) can append entries to the log at a time.

**Key Terms**

* **Quorum:** A majority of nodes in the cluster.
* **Fencing Token:** A mechanism to prevent outdated actions (similar to term numbers in Raft).
* **Leader:** The node responsible for appending entries to the log.
* **Followers:** Nodes that receive and replicate log entries from the leader.
* **Term:** A unique identifier for each election period.

**Leader Election**

* **Followers:**
  * Periodically receive heartbeats from the leader.
  * If no heartbeats are received within a timeout, they assume the leader is dead and become candidates.
* **Candidates:**
  * Increase their term number.
  * Request votes from other nodes.
  * If a candidate receives votes from a quorum, it becomes the leader.
* **Randomized Timeouts:** Prevent simultaneous elections by introducing randomness in follower timeouts.

**Log Replication**

* **Leader:**
  * Appends entries to its local log.
  * Sends entries to followers via the "replicate log" function.
* **Followers:**
  * Acknowledge receipt of entries.
* **Log Invariant:** If two logs have the same term number at a given index, they must be identical up to that index.
* **Prefix and Suffix:** The leader determines the prefix (entries already present in the follower's log) and sends the suffix (new entries) to the follower.
* **Log Consistency:** If a follower's log differs from the leader's, the leader adjusts the prefix and resends entries.

**Committing Entries**

* Once the leader receives acknowledgments from a quorum of followers for an entry, it commits the entry.
* Committed entries are applied to the underlying database.

**Handling Node Failures**

* If a leader fails, followers initiate new elections.
* Raft ensures that only one leader exists per term.

**Comparison with Two-Phase Commit**

* Raft provides a more robust and efficient mechanism for achieving consensus compared to two-phase commit.

**Conclusion**

* Raft is a powerful and elegant consensus algorithm.
* Its simplicity and effectiveness make it a popular choice in distributed systems.

**Note:**

* This is a simplified explanation of Raft.
* The video provides a high-level overview and may not cover all the intricacies of the algorithm.

I hope these notes are helpful!



## EP15: Batch Processing" by Jordan has no life:

**Introduction**

* Jordan introduces the concept of batch processing, explaining that it involves performing large-scale computations on massive datasets.
* He highlights common use cases such as building search indexes, machine learning, data aggregation, and ETL processes.
* He emphasizes the limitations of traditional data warehouses, such as the constraints of SQL and the need for structured data.

**Distributed File Systems**

* Jordan introduces distributed file systems as a foundation for batch processing.
* He explains that they are similar to local file systems but operate across multiple computers.
* He mentions Hadoop Distributed File System (HDFS) as an example, highlighting its replication and redundancy features.

**MapReduce**

* Jordan introduces MapReduce as a programming model for batch processing on top of HDFS.
* He explains that it involves writing two functions:
  * **Mapper:** Processes individual records, extracting key-value pairs.
  * **Reducer:** Aggregates values for the same key.
* He describes the key steps in the MapReduce process:
  * Data partitioning: Distributing key-value pairs to different reducer nodes.
  * Sorting: Sorting key-value pairs within each partition.
  * Reduction: Aggregating values for each key.
* He discusses data locality optimization, where mappers are preferentially executed on the nodes where the input data resides.
* He explains how MapReduce handles faults by allowing individual mapper/reducer tasks to be retried.

**Join Operations in MapReduce**

* Jordan discusses three types of joins in MapReduce:
  * **Sort-Merge Join:**
    * Performed by the reducer.
    * Data is partitioned and sorted based on the join key.
    * Reducer merges the sorted data to perform the join.
  * **Broadcast Hash Join:**
    * Used when one dataset is small enough to fit in memory on each mapper.
    * The smaller dataset is broadcast to all mappers.
    * Join is performed on the mapper side.
  * **Partition Hash Join:**
    * Used when neither dataset is small enough for broadcast.
    * Both datasets are partitioned using the same hash function.
    * Mappers only load the relevant partition of the smaller dataset.

**Pitfalls of MapReduce**

* **Stragglers:** Slow-running tasks can significantly impact overall job completion time.
* **Intermediate State Materialization:** Writing intermediate results to disk can be time-consuming and inefficient.
* **Unnecessary Sorting:** Sorting within each partition can be redundant in some cases.

**Dataflow Engines**

* Jordan introduces dataflow engines as a more modern approach to batch processing.
* He mentions Apache Spark as a prominent example.
* He highlights the key advantages of dataflow engines:
  * **Reduced Intermediate State:** Data is processed in a more stream-like manner, minimizing disk I/O.
  * **Improved Data Locality:** Better optimization of data movement across the cluster.
  * **Flexibility:** More flexible programming model with support for various operations.
  * **Fault Tolerance:** Checkpointing mechanisms to recover from node failures.

**Conclusion**

* Jordan summarizes the key concepts of batch processing, emphasizing the importance of distributed file systems and efficient processing models.
* He briefly mentions stream processing as a related concept for processing data in real-time.

**Note:**

* This is a summary of the key points discussed in the video.
* The video provides more in-depth explanations and uses visual aids to illustrate the concepts.

I hope these notes are helpful!



## EP16: Stream Processing" by Jordan has no life:

**Introduction**

* **Long Video:** Jordan apologizes for the length of the video, stating it will be a comprehensive overview of stream processing.
* **Fundamental Concepts:** He emphasizes that this video covers the final fundamental/theoretical topic in the series.
* **Future Focus:** Subsequent videos will delve into specific technologies and more in-depth algorithms.

**What is Stream Processing?**

* **Unbounded Data:** Unlike batch processing, which operates on finite datasets, stream processing handles continuous, unbounded streams of data.
* **Asynchronous Processing:** Messages are processed as they arrive, asynchronously.
* **Producers and Consumers:**
  * **Producers:** Generate messages (e.g., servers, clients).
  * **Consumers:** Process the received messages.
* **Message Brokers:**
  * Intermediaries that buffer messages.
  * Allow producers to push messages and consumers to pull them.
  * Can be in-memory (e.g., Redis) or log-based.

**Message Brokers: In-Memory vs. Log-Based**

* **In-Memory:**
  * High performance for fast processing.
  * Suitable when order is not critical and maximum throughput is desired.
  * Limited durability as messages are typically lost upon consumer acknowledgment.
* **Log-Based:**
  * Messages are stored on disk in an append-only log.
  * Provides durability and fault tolerance.
  * Enables ordered processing within partitions.
  * Can be partitioned and replicated for scalability.
  * Potential bottleneck if a single message takes a long time to process.

**Message Delivery Patterns**

* **Fan-out:** Every message is sent to all subscribed consumers.
* **Load Balancing:** Each consumer handles a subset of messages for improved throughput.

**Message Ordering**

* **Not Guaranteed:** Generally, messages are not processed in the order they are received.
* **Order Within Partitions:** Log-based brokers can ensure order within a partition.

**Common Use Cases**

* **Logging and Metrics:**
  * Aggregating events within time windows (tumbling, hopping, sliding).
  * Challenges:
    * Defining time windows accurately due to clock skew.
    * Handling late-arriving events.
* **Change Data Capture:**
  * Streaming changes from a database to update other systems (caches, search indexes, data warehouses).
  * Enables efficient updates and derived data generation.
* **Event Sourcing:**
  * Capturing user events to reconstruct the state of the system and derive various data views.

**Stream Joins**

* **Stream-Stream Joins:** Joining two streams of events (e.g., joining search events with click events).
  * Requires maintaining local indexes within each stream.
* **Stream-Table Joins:** Joining a stream with a database table.
  * Requires keeping a local copy of the table in the stream and updating it with changes.
* **Table-Table Joins:** Joining two tables based on changes to either table.
  * Requires subscribing to change data capture events for both tables.

**Fault Tolerance**

* **At-Least-Once Delivery:** Ensure each message is processed at least once.
  * Achieved through checkpointing and micro-batching.
* **Exactly-Once Delivery:** Ensure each message is processed exactly once.
  * Challenging to achieve.
  * Can be implemented using:
    * **Two-Phase Commit:** Expensive and less fault-tolerant.
    * **Idempotency:** Designing operations to have no effect if repeated.

**Conclusion**

* Stream processing is a critical technology for handling real-time data.
* Message brokers play a crucial role in enabling efficient and reliable stream processing.
* Understanding concepts like message ordering, fault tolerance, and stream joins is essential for designing and implementing effective stream processing systems.

**Note:**

* This is a summary of the key points discussed in the video.
* The video provides more in-depth explanations and uses visual aids to illustrate the concepts.

I hope these notes are helpful!



## EP17: Consistent Hashing" by Jordan has no life:

**Introduction**

* Jordan apologizes for the delay in covering Cassandra, explaining that he needs to cover consistent hashing and gossip protocols first.
* He emphasizes the importance of consistent hashing for understanding how Cassandra works, particularly its sharding mechanism.
* He states that consistent hashing is a simple algorithm for balancing load across multiple nodes in a cluster while minimizing rebalancing when nodes are added or removed.

**Use Cases of Consistent Hashing**

* **Sharding:** Distributing data across multiple nodes in a partitioned database.
* **Load Balancing:** Directing traffic from clients to specific application servers.

**Consistent Hashing Algorithm**

1. **Hash Function:**
   * Choose a hash function that maps any input to a value within a specific range (e.g., 0 to 999).
   * This range represents a circular ring.
2. **Node Placement:**
   * Place multiple "virtual nodes" (replicas) for each physical node on the ring.
   * This helps distribute load more evenly and reduces the impact of node failures.
3. **Key Placement:**
   * Hash the key to obtain a value within the ring's range.
   * Move clockwise around the ring from the key's hash value until encountering a node's virtual node.
   * Assign the key to that node.

**Benefits of Consistent Hashing**

* **Even Load Distribution:** Helps distribute load evenly across nodes.
* **Minimal Rebalancing:** When a node is added or removed, only a small fraction of keys need to be reassigned.
* **Efficient Key Assignment:** Determining the node for a key is a relatively fast operation.

**Applications**

* **Sharding in Databases:** Cassandra, Memcached, Redis
* **Load Balancing in Web Servers**

**Conclusion**

* Consistent hashing is a valuable algorithm for distributing load and minimizing rebalancing in distributed systems.
* It is a crucial concept for understanding how systems like Cassandra handle data distribution and node failures.

**Additional Notes**

* The video is relatively short and concise.
* Jordan uses clear and simple language to explain the concept.
* He provides a visual representation of the ring and node placement to aid understanding.

I hope these notes are helpful!



## EP18: Gossip Protocol" by Jordan has no life:

**Introduction**

* Jordan starts by acknowledging his hungover state and apologizing for the quick video.
* He explains that he's covering Gossip Protocol to prepare for the next video on Cassandra.

**What is Gossip Protocol?**

* **Peer-to-Peer Communication:** Gossip protocols enable nodes in a cluster to directly exchange information with each other.
* **Contrast with Centralized Services:** Unlike centralized membership services, gossip protocols distribute information dissemination across the nodes.
* **Infectious Disease Analogy:** The spread of information resembles an infectious disease, where each node shares information with a few others, leading to rapid propagation.

**Use Case: Cassandra**

* Cassandra utilizes gossip protocol to:
  * **Track Node Membership:** Determine which nodes are active and available in the cluster.
  * **Monitor Node Health:** Track node load and identify potential failures.

**How Gossip Protocol Works**

* **Random Node Selection:** Each node randomly selects a few other nodes to communicate with.
* **Message Broadcasting:** Nodes broadcast their local information (e.g., load, health status) to the selected nodes.
* **Information Propagation:** The received information is then further propagated by the receiving nodes, creating a chain reaction.
* **Example:**
  * Node A receives a heartbeat from Node B.
  * Node A randomly selects two other nodes (C and D) and broadcasts the heartbeat information to them.
  * Nodes C and D then broadcast the information to other randomly selected nodes, and so on.

**Information Exchanged in Gossip Protocol**

* **Node Load:** Information about the current load on each node (e.g., number of requests, resource utilization).
* **Heartbeats:** Timestamps indicating the last time a node was active.
* **Failure Detection:** Nodes use heartbeat timestamps to determine if other nodes have failed.

**Limitations of Gossip Protocol**

* **Order of Delivery:** Messages may not be delivered in the order they were sent.
* **Time Delays:** Propagation of information can take time, leading to potential delays in detecting failures.
* **Inconsistency:** Temporary inconsistencies in information may exist due to the asynchronous nature of the protocol.

**Conclusion**

* Gossip protocol is a simple and efficient mechanism for distributing information in a distributed system.
* It is a key component of many distributed systems, including Cassandra.
* While not perfect, it provides a robust and scalable approach to node membership and failure detection.

**Note:**

* This is a summary of the key points discussed in the video.
* The video provides a more in-depth explanation and uses visual aids to illustrate the concept.

I hope these notes are helpful!



## EP19: Cassandra Deep Dive" by Jordan has no life:

**Introduction**

* Jordan emphasizes the importance of in-depth knowledge of specific technologies in systems design interviews.
* He states that this video will focus on Apache Cassandra, a NoSQL wide-column database.
* He highlights Cassandra's strengths in high read and write throughput.

**Cassandra's Design Philosophy**

* **High Availability:** Designed for high availability and linear scalability.
* **Single Partition Operations:** Optimized for single partition reads and writes to minimize network overhead and coordination.
* **Data Organization:** Uses a partition key and allows for arbitrary columns. Clustering keys act as indexes.

**Storage Engine**

* **LSM Tree:** Uses an LSM tree-based storage engine for fast writes.
* **Log Compaction:** Buffers writes to an in-memory tree and periodically compacts them into sorted string tables (SSTables).
* **Bloom Filters:** Used to optimize reads by approximating the presence of keys in SSTables.

**Partitioning**

* **Consistent Hashing:** Uses consistent hashing to distribute partitions across nodes, minimizing rebalancing upon node addition/removal.

**Replication**

* **Dynamo-style Replication:** Replicates data across multiple nodes for fault tolerance.
* **Writes:** Sent to multiple replicas, with success determined by a configurable quorum.
* **Last Write Wins:** Resolves conflicts by prioritizing the most recent write based on timestamps.
* **Read Repair:** Corrects inconsistencies between replicas during reads.
* **Anti-Entropy Process:** Background process to ensure data consistency across replicas.
* **Hinted Handoff:** Stores writes temporarily if replicas are unavailable and delivers them later.

**Fault Tolerance**

* **Data Replication:** Provides high availability through data replication.
* **Linear Scalability:** Read and write throughput scales linearly with the number of replicas.
* **Flexible Replication Topology:** Allows for configuring replication across different data centers.
* **Gossip Protocol:** Used for node failure detection.

**Indexing**

* **Local Secondary Indexes:** Supports local secondary indexes based on clustering keys.
* **No Global Secondary Indexes:** Limitations on indexing across partitions.

**Use Cases**

* **Write-Heavy Applications:** Ideal for applications with high write throughput and low read complexity.
* **Examples:** Sensor data, chat messages, user activity tracking.

**Limitations**

* **Limited Strong Consistency:** Challenges in achieving strong consistency due to eventual consistency model and clock skew.
* **Limited Support for Complex Relationships:** Not well-suited for applications with complex joins across partitions.
* **Lack of Global Secondary Indexes:** Can limit read performance in some scenarios.

**Conclusion**

* Cassandra is a powerful NoSQL database well-suited for high-throughput, write-heavy applications.
* Understanding its strengths and limitations is crucial for effective systems design.

**Note:**

* This is a summary of the key points discussed in the video.
* The video provides more in-depth explanations and uses visual aids to illustrate the concepts.

I hope these notes are helpful!



## EP20: Coordination Services" by Jordan has no life:

**Introduction**

* Jordan acknowledges new subscribers and briefly mentions his gym routine.
* He states that this video will cover coordination services, a crucial component of distributed systems.
* He plans to discuss HBase in a future video, but needs to cover coordination services first.

**What are Coordination Services?**

* In distributed systems with multiple nodes, there's a need for shared state or cluster configuration information.
* This includes:
  * Node IP addresses
  * Partition locations
  * Node health status
  * Distributed locks
* Coordination services provide a centralized mechanism to manage this information.
* Examples: ZooKeeper, etcd

**Key Characteristics of Coordination Services**

* **Highly Available:** Replicated across multiple nodes for fault tolerance.
* **Key-Value Stores:** Typically based on a replicated key-value store.
* **Consensus Layer:** Built on top of a consensus algorithm (e.g., Paxos, Raft, Zab) to ensure data consistency.
* **Read-Heavy Workloads:** Optimized for frequent reads, as writes are typically slower due to consensus requirements.

**Read Consistency**

* **Monotonic Reads:** Ensure that subsequent reads from different replicas do not appear to go backward in time.
* **Predicate Validity:** Allow clients to "watch" keys and receive notifications when they change, ensuring that assumptions made during reads remain valid.
* **Strong Consistency:**
  * Achieved through:
    * Reading from the leader.
    * Using the "sync" operation to ensure all replicas have seen a write before reading.
    * Quorum reads (reading from a majority of nodes), though this can have limitations due to race conditions.

**Limitations of Strong Consistency**

* **Performance Overhead:** Strong consistency can significantly impact read performance due to increased communication and synchronization.

**Conclusion**

* Coordination services are essential for managing shared state and ensuring consistency in distributed systems.
* They are used extensively in various database systems and other distributed applications.
* Understanding their characteristics and limitations is crucial for effective systems design.

**Note:**

* This video provides a high-level overview of coordination services.
* It does not delve into the specific implementations of ZooKeeper, etcd, or other coordination services.

I hope these notes are helpful!



## EP21: Hadoop File System Design" by Jordan has no life:

**Introduction**

* **Context:** Jordan provides a brief background on distributed file systems, highlighting HDFS as a prominent example.
* **HDFS's Role:** He emphasizes HDFS as a foundation for large-scale data processing frameworks like MapReduce, Spark, and Hive.
* **Key Focus:** The video will delve into the architecture of HDFS, focusing on how it achieves high read and write throughput.

**HDFS Architecture**

* **Data Storage:**
  * Data is divided into chunks (typically 64-128 MB).
  * Chunks are replicated across multiple data nodes for fault tolerance and improved read performance.
* **NameNode:**
  * **Metadata Master:** Stores all metadata about files in the HDFS cluster (names, locations, etc.).
  * **Keeps Metadata in Memory:** For fast access to file locations.
  * **Edit Log:** Records all metadata changes to ensure data recovery in case of failure.
  * **FsImage:** Periodically checkpoints the in-memory state to the FsImage file.
  * **Safe Mode:** A period after startup where the NameNode collects block reports from DataNodes to build its internal state.
* **DataNodes:**
  * Store and serve data chunks.
  * Report block locations to the NameNode.
  * Participate in replication processes.
* **Replication:**
  * **Rack Awareness:** Replicates data across different racks to minimize the impact of rack failures.
  * **Pipelined Replication:** Data is replicated sequentially between DataNodes, improving efficiency.
  * **Strong Consistency:** Aims for strong consistency but may experience temporary inconsistencies due to potential failures in the replication pipeline.

**Read Operations**

* **Client Queries NameNode:** The client queries the NameNode to locate the DataNodes holding the desired chunk.
* **DataNode Selection:** The client selects the closest DataNode based on network proximity.
* **Data Retrieval:** The client reads the data directly from the selected DataNode.
* **Client-Side Caching:** Clients cache read data for subsequent reads.

**Write Operations**

* **NameNode Interaction:** The client interacts with the NameNode to determine the primary replica and the replication pipeline.
* **Pipelined Writes:** Data is written to the primary replica and then replicated to secondary replicas.
* **Write Acknowledgements:** The client waits for acknowledgments from all replicas before considering the write successful.
* **Handling Failures:** If a write fails, the client retries the operation.

**High Availability**

* **NameNode High Availability:**
  * Utilizes a coordination service (like ZooKeeper) to elect a leader among multiple NameNode instances.
  * The leader manages the file system, while standby NameNodes remain up-to-date.
* **DataNode Failures:** Data replication ensures data availability even if some DataNodes fail.

**Conclusion**

* HDFS is a robust and scalable distributed file system designed for large-scale data processing.
* Its key features include data chunking, replication, and a hierarchical architecture.
* HDFS provides a solid foundation for building data-intensive applications.

**Note:**

* This summary provides a high-level overview of the key concepts discussed in the video.
* The video provides more in-depth explanations and uses visual aids to illustrate the concepts.

I hope these notes are helpful!



## EP22: HBase/BigTable Deep Dive" by Jordan has no life:

**Introduction**

* **Channel Anniversary:** Celebrates the one-month anniversary of the channel and expresses gratitude for the growing subscriber base.
* **Research Challenges:** Mentions the difficulty of finding accurate and in-depth information on HBase online, with many resources being plagiarized or superficial.
* **BigTable Connection:** Highlights that HBase is heavily modeled after Google's BigTable, which has abundant documentation.
* **Video Purpose:** To compare and contrast HBase with Cassandra, both being wide-column NoSQL databases.

**HBase Overview**

* **Built on HDFS:** Leverages HDFS for data storage, improving upon its limitations (high latency writes, sequential appends/truncates).
* **LSM Trees and SSTables:** Utilizes LSM trees and SSTables for efficient random read/write performance and lower latency.
* **Data Model:**
  * Wide-column database with rows, columns, and column families.
  * Row keys are crucial for data organization and should be carefully designed.
  * Row keys can be composite (multiple parts) to enable efficient data sorting.

**HBase Architecture**

* **Master Server:**
  * Manages metadata (file locations, etc.).
  * Handles partitioning based on row keys (range-based partitioning).
  * Not suitable for time-series data where all writes go to a single partition (hotspotting).
  * Uses ZooKeeper for high availability and fault tolerance.
* **Region Servers:**
  * Run on HDFS DataNodes.
  * Handle read/write operations for specific partitions.
  * Utilize in-memory LSM trees for writes.
  * Flush LSM trees to SSTables (column-oriented storage) on disk.
  * Leverage HDFS for data replication and fault tolerance.
* **Replication:**
  * Leverages HDFS's replication mechanism for data redundancy.
  * Ensures strong consistency by waiting for all replicas to acknowledge writes.

**Read/Write Operations**

* **Reads:**
  * Client queries the Master Server for the location of the relevant Region Server.
  * Region Server checks the in-memory LSM tree and then the SSTables for the requested data.
  * Utilizes optimizations like Bloom filters to speed up reads.
* **Writes:**
  * Writes are initially buffered in the memory LSM tree.
  * When the LSM tree reaches a certain size, it's flushed to an SSTable.
  * SSTables are replicated across HDFS DataNodes.

**Analytics Processing**

* **Column-Oriented Storage:** Enables efficient read throughput for analytical queries across entire columns.
* **HDFS Integration:** Seamless integration with MapReduce and other data processing frameworks.
* **Data Lake Use Case:** Ideal for storing large amounts of unstructured data for later analysis.

**Comparison with Cassandra**

* **Key Differences:**
  * **Replication:** HBase leverages HDFS replication, while Cassandra has its own replication strategy.
  * **Consistency:** HBase generally provides stronger consistency due to HDFS's replication guarantees.
  * **Write Performance:** Cassandra is often considered to have better write performance due to its leaderless replication.
  * **Use Cases:** HBase is better suited for analytical workloads and data lakes, while Cassandra excels in real-time transactional processing.

**Conclusion**

* HBase is a powerful NoSQL database with strengths in handling large datasets and enabling efficient analytical processing.
* Its integration with HDFS provides robust data storage and fault tolerance.
* Understanding its architecture and use cases is crucial for effective systems design.

**Note:**

* This summary provides a high-level overview of the key concepts discussed in the video.
* The video provides more in-depth explanations and uses visual aids to illustrate the concepts.

I hope these notes are helpful!



## EP23: Conflict-Free Replicated Data Types" by Jordan has no life:

**Introduction**

* **Context:** The video transitions from wide-column NoSQL databases (like Cassandra) to key-value stores.
* **CRDTs:** Introduces Conflict-Free Replicated Data Types (CRDTs) as a crucial concept for understanding how key-value stores handle data consistency in multi-leader replication scenarios.

**What are CRDTs?**

* **Purpose:** Designed to resolve conflicts that arise in multi-leader replication systems where multiple nodes can independently write data.
* **Core Idea:** Each replica maintains its own local state. When replicas communicate, they merge their states in a way that guarantees eventual consistency.
* **Key Properties:**
  * **Commutativity:** The order of operations does not affect the final outcome.
  * **Idempotency:** Repeatedly applying the same operation has no additional effect.

**Types of CRDTs**

* **Operation-Based CRDTs:**
  * Transmit operations (e.g., increment, decrement) between replicas.
  * More efficient for large datasets with infrequent updates.
  * Require deduplication mechanisms to handle duplicate operations.
* **State-Based CRDTs:**
  * Transmit the entire state of the data structure between replicas.
  * Simpler to reason about but can be less efficient for large datasets.
  * Merge function must be commutative and idempotent.

**Examples of CRDTs**

* **Grow-Only Counter:**
  * Each replica maintains a counter.
  * Increments are applied locally.
  * Merging involves taking the maximum value across all replicas.
* **G-Counter (Grow-Only Counter):**
  * Each replica maintains an array of counters, one for each replica.
  * Increments are applied to the corresponding index in the array.
  * Merging involves taking the element-wise maximum of the arrays.
* **PN-Counter (Positive-Negative Counter):**
  * Extends G-Counter with an array for decrements.
  * Merging involves taking the element-wise maximum for increments and decrements.
  * Final value is calculated by summing increments and subtracting decrements.
* **Sets:**
  * Each replica maintains two sets: added and removed.
  * Merging involves taking the union of added sets and the union of removed sets.
  * Variations include timestamping or tagging elements to handle more complex scenarios.
* **Sequences (CRDTs for Ordered Lists):**
  * More complex to implement due to the need to maintain order.
  * Involves techniques like timestamping, unique identifiers, and careful merge strategies.

**Use Cases of CRDTs**

* **Collaborative Editing:** Google Docs, Figma, Office 365
* **Online Chat Systems:** Ensuring consistent message order across clients.
* **Offline Editing:** Syncing changes from offline clients to the server.
* **Distributed Key-Value Stores:** Ryak, Redis (some implementations)

**Conclusion**

* CRDTs are a powerful technique for achieving eventual consistency in multi-leader replication systems.
* They offer a robust solution for handling conflicts and ensuring data integrity in distributed environments.
* Understanding CRDTs is crucial for designing and implementing scalable and reliable distributed systems.

**Note:**

* This summary provides a high-level overview of the key concepts discussed in the video.
* The video provides more in-depth explanations and uses visual aids to illustrate the concepts.

I hope these notes are helpful!



## EP24: Riak Explained" by Jordan has no life:

**Introduction**

* **Short and Sweet:** Jordan acknowledges the video's brevity, comparing it to his ex-girlfriend's interest in him.
* **Importance of Riak:** He emphasizes the importance of covering Riak, a distributed key-value store, due to its frequent appearance in his systems design studies and its use in various companies.

**Riak: A Distributed Key-Value Store**

* **High Throughput:** Riak is designed for extremely high read and write throughput.
* **Similarities to Cassandra:** Shares many similarities with Cassandra, including replication, partitioning, and the use of an LSM tree-based storage engine.
* **Key Differences:** The primary differences between Riak and Cassandra lie in their data models and conflict resolution mechanisms.

**Data Modeling**

* **Cassandra:** Wide-column store with rows, columns, and clustering keys.
* **Riak:** Simple key-value store where the value can be any data type (blobs, JSON, strings, etc.).
* **Limited Secondary Indexing:** Riak's key-value nature limits native secondary indexing capabilities.
  * Requires extra metadata or integration with a separate search index for efficient searches on values.

**Conflict Resolution**

* **Cassandra:** Uses "last write wins" to resolve conflicts, which can lead to data loss.
* **Riak:** Utilizes version vectors to track write dependencies.
  * Allows for storing "siblings" (conflicting writes) when conflicts occur.
  * Enables application-level merging of siblings or utilizes CRDTs for automatic conflict resolution.

**CRDTs in Riak**

* **Conflict-Free Replicated Data Types:** Allow Riak to handle certain data types (counters, sets, maps) with automatic conflict resolution.
* **Simplified Conflict Handling:** Eliminates the need for custom application logic to merge conflicting writes.

**Conclusion**

* **High Throughput:** Riak excels at high read and write throughput due to its architecture and efficient data handling.
* **Data Flexibility:** Offers greater flexibility in data storage compared to Cassandra.
* **Conflict Resolution:** Provides more robust conflict resolution mechanisms than Cassandra's "last write wins" approach.
* **Limitations:** Limited native secondary indexing capabilities and potential challenges with complex data structures.

**Note:**

* This summary provides a high-level overview of the key concepts discussed in the video.
* The video provides more in-depth explanations and uses visual aids to illustrate the concepts.

I hope these notes are helpful!



## EP25: Distributed Caching Primer" by Jordan has no life:

**Introduction**

* **Motivation:** Jordan emphasizes the importance of caching in large-scale systems for improved read performance and reduced load on databases.
* **Unique Value Proposition:** He highlights the importance of providing in-depth and valuable content, differentiating his channel from other systems design channels.
* **Rant:** Briefly criticizes the abundance of generic and redundant content on YouTube, particularly regarding software engineering tips and tricks.

**What is Caching?**

* **Definition:** Caching involves storing frequently accessed data in a faster storage medium (typically RAM) to reduce latency and improve performance.
* **Benefits:**
  * **Reduced Latency:** Faster reads from the cache compared to accessing the database.
  * **Reduced Database Load:** Offloads read traffic from the database, improving overall system performance.
  * **Improved Data Locality:** Stores data closer to the user, minimizing network hops.
  * **Pre-computed Results:** Caches pre-computed results of expensive operations.

**Trade-offs of Caching**

* **Cache Misses:** If the requested data is not found in the cache (cache miss), it can lead to increased latency.
* **Thrashing:** Frequent cache misses due to limited cache size or poor eviction policies.
* **Increased Complexity:** Requires careful design and management of the cache, including eviction policies and data consistency mechanisms.
* **Write Complexity:** Maintaining cache consistency with the database can introduce complexity, especially for write operations.

**Cache Eviction Policies**

* **Least Recently Used (LRU):** Evicts the least recently accessed data. Generally considered the most effective policy.
* **Least Frequently Used (LFU):** Evicts the least frequently accessed data. Can be less effective than LRU in some scenarios.
* **First-In, First-Out (FIFO):** Evicts data in the order it was added to the cache. Simple but often less efficient than LRU.
* **Sliding Window:** Evicts data that has not been accessed within a specific time window.

**Types of Caches**

* **Application Server Caches:** Caches maintained within individual application servers.
  * **Pros:** Simple to implement, low latency.
  * **Cons:** Limited scalability, data loss if the server fails.
* **Global Caches:**
  * Dedicated cache servers accessible by multiple application servers.
  * **Pros:** Scalability, high availability, independent scaling.
  * **Cons:** Requires additional network hops.

**Write Strategies**

* **Write-Through:** Writes to both the cache and the database simultaneously. Requires atomic transactions to ensure consistency.
* **Write-Around:** Writes only to the database. Involves invalidating the corresponding cache entry.
* **Write-Back:** Writes only to the cache initially. Data is eventually written back to the database.
  * **Pros:** High write throughput.
  * **Cons:** Potential data loss if the cache server fails. Requires mechanisms to ensure data consistency (e.g., distributed locks).

**Keeping the Cache Valid**

* **Cache Invalidation:**
  * **Write-Around:** Invalidate cache entries when data is updated in the database.
  * **Write-Back:** Requires mechanisms to ensure data consistency, such as distributed locks or asynchronous updates.
* **Time-to-Live (TTL):** Set an expiration time for each cache entry.

**Distributed Caching Services**

* **Redis:** A popular in-memory data store with support for various data structures (strings, hashes, lists, sets).
* **Memcached:** A high-performance, in-memory key-value store.

**Conclusion**

* Caching is a critical technique for improving the performance of distributed systems.
* Careful consideration must be given to cache eviction policies, write strategies, and data consistency mechanisms.
* Distributed caching services provide scalable and reliable solutions for managing cache data.

**Note:**

* This summary provides a high-level overview of the key concepts discussed in the video.
* The video provides more in-depth explanations and uses visual aids to illustrate the concepts.

I hope these notes are helpful!



## EP26: Redis and Memcached Explained (While Drunk?)" by Jordan has no life:

**Introduction**

* **Recording Circumstances:** Jordan humorously recounts recording the initial version of this video while intoxicated, acknowledging the potential incoherence.
* **Re-recording:** He explains that this is a re-recording to provide a more concise and coherent explanation.
* **Focus:** The video will focus on comparing and contrasting Memcached and Redis, both popular in-memory data stores used for caching in distributed systems.

**Memcached**

* **Distributed Hash Map:** Essentially a distributed hash map where nodes don't inherently know about each other.
* **Client Library:** A client library is responsible for directing requests to the appropriate node using consistent hashing.
* **Consistent Hashing:** Each application server maintains a list of Memcached nodes and uses consistent hashing to determine the node responsible for a given key.
* **LRU Cache:** Each Memcached node utilizes an LRU (Least Recently Used) cache to evict data when memory capacity is exceeded.
* **Limited Features:** Primarily a simple key-value store with basic features.
* **No Built-in Replication:** Lacks built-in replication or failure handling.
  * Facebook's approach: "Gutter Rattus" - dynamically replace failed nodes with new ones, allowing the system to eventually repopulate the new nodes.

**Redis**

* **Enhanced Memcached:** Redis extends Memcached with several key features:
  * **Data Structures:** Supports various data structures beyond simple key-value pairs, including sets, lists, sorted sets, hashes, and more.
  * **Atomic Operations:** Enables atomic operations on data structures (e.g., appending to strings, incrementing counters).
  * **Transactions:** Supports basic transactions for executing multiple operations atomically.
  * **Range Queries:** Allows for range queries on sorted sets.
  * **Hash Tagging:** Enables partial key hashing for more granular control over data distribution.
  * **Persistence:** Supports data persistence through checkpointing or write-ahead logs.

**Redis Cluster**

* **Distributed Mode:** Redis Cluster enables running Redis in a distributed manner.
* **Single-Leader Replication:** Employs single-leader replication with automatic failover.
* **Partitioning:** Uses a fixed number of partitions (16,384) for data distribution.
* **Quorum-Based Leader Election:** Requires a quorum of nodes to agree on a new leader to prevent split-brain scenarios.
* **Epoch Numbers:** Used to prevent conflicting leader elections.

**Comparison**

* **Memcached:** Simpler, more limited feature set, better for performance-critical scenarios where simplicity is paramount.
* **Redis:** More feature-rich, suitable for a wider range of use cases, but potentially more complex to manage.

**Choosing Between Memcached and Redis**

* **Consider:**
  * Required features (data structures, transactions, persistence).
  * Performance requirements (throughput, latency).
  * Complexity and maintainability.
  * Fault tolerance requirements.

**Conclusion**

* Both Memcached and Redis are valuable tools for caching in distributed systems.
* Understanding their strengths and weaknesses is crucial for making informed decisions in system design.
* Caching is a critical optimization technique for improving the performance and scalability of applications.

**Note:**

* This summary provides a high-level overview of the key concepts discussed in the video.
* The video provides more in-depth explanations and uses visual aids to illustrate the concepts.

I hope these notes are helpful!

## EP27: Search Indexes" by Jordan has no life:

**Introduction**

* **Context:** Jordan acknowledges a delay in uploading the video due to his roommate's unexpected presence.
* **Importance of Search:** Emphasizes the ubiquitous nature of search functionality in modern applications (Google, Amazon, Twitter).
* **Limitations of Databases:** Traditional databases are not optimized for complex search queries.
* **Introduction to Lucene:** Introduces Lucene as a popular open-source search library.

**Lucene Architecture**

* **LSM Tree & SSTables:** Utilizes an LSM tree architecture with SSTables for efficient storage and retrieval.
* **Write Process:**
  * Writes are initially buffered in memory.
  * Periodically, the buffer is flushed to an immutable SSTable file.
  * SSTables are merged and compacted over time.
* **Read Process:**
  * Reads may involve accessing multiple SSTables.
  * Results from different SSTables are merged to produce the final result.

**Document Tokenization**

* **Importance of Tokenization:**
  * Breaking down documents into individual terms (words) is crucial for indexing and searching.
* **Considerations:**
  * Handling punctuation, case sensitivity, contractions, and common words (stop words).
  * Choosing appropriate tokenization rules based on the specific search requirements.

**Inverted Index**

* **Core Concept:**
  * Instead of mapping documents to their terms, the inverted index maps terms to the documents that contain them.
* **Data Structure:**
  * Typically stored in sorted SSTables for efficient searching.
* **Term Prefixes:**
  * Efficiently search for terms with prefixes using binary search on the sorted index.
* **Term Suffixes:**
  * Requires a separate inverted index with reversed terms.
* **Numeric and Geo-Spatial Data:**
  * Lucene uses specialized data structures and algorithms for handling these data types.

**Elasticsearch**

* **Distributed Lucene:** Elasticsearch scales Lucene across a cluster of nodes.
* **Partitioning:**
  * Data is partitioned across nodes based on document IDs.
  * Each node maintains a local inverted index for its assigned documents.
* **Replication:**
  * Replicates data across multiple nodes for fault tolerance and improved read performance.
* **Caching:**
  * Caches query results and intermediate results to improve performance.

**Benefits of Elasticsearch**

* **Scalability:** Handles large volumes of data and high query traffic.
* **High Performance:** Achieves high read and write throughput through efficient indexing and caching.
* **Rich Features:** Supports a wide range of search functionalities, including full-text search, faceting, and geolocation searches.

**Conclusion**

* Search indexes are critical components of many modern applications.
* Lucene provides a robust foundation for building high-performance search engines.
* Elasticsearch scales Lucene to a distributed environment, enabling high availability and scalability.

**Note:**

* This summary provides a high-level overview of the key concepts discussed in the video.
* The video provides more in-depth explanations and uses visual aids to illustrate the concepts.

I hope these notes are helpful!



## EP28: Time Series Databases" by Jordan has no life:

**Introduction**

* **Focus:** The video focuses on time series databases, their characteristics, and design considerations.
* **Specificity:** Jordan emphasizes that he won't delve into specific databases (like TimescaleDB) as they are not commonly discussed in interviews.
* **Abstraction:** He focuses on general design principles and common optimizations found in time series databases.

**What are Time Series Databases?**

* **Purpose:** Designed to efficiently store and query time-stamped data, such as sensor readings, log files, and financial data.
* **High Throughput:** Optimized for high read and write throughput, especially for recent data.
* **Ordered Data:** Data is inherently ordered by timestamp.

**Use Cases**

* **IoT Data:** Storing and analyzing sensor data from various devices.
* **Log Aggregation:** Collecting and analyzing log data from servers and applications.
* **Financial Data:** Storing and analyzing stock prices, trading data, and other financial time series.
* **Metrics Monitoring:** Tracking system performance metrics (CPU, memory, network).

**Time Series Data Characteristics**

* **High Write Throughput:** Frequent insertions of new data points.
* **Ordered Data:** Data is inherently ordered by timestamp.
* **Data Locality:** Adjacent data points often have similar values.
* **Read Patterns:**
  * Frequent reads of recent data.
  * Range queries (e.g., data within a specific time interval).
  * Aggregations (e.g., averages, sums, min/max values).
* **Data Retention:** Often involves retaining data for varying durations (e.g., short-term for high-resolution data, long-term for aggregated data).

**Design Considerations**

* **Data Ordering:**
  * **Compound Index:** Utilize a compound index on (timestamp, source ID) to efficiently access data from specific sources within a time range.
  * **Data Locality:** Store data from the same source and within similar time intervals together to improve read performance.
* **Column-Oriented Storage:**
  * Store data in columns to optimize reads for specific metrics.
  * Enables efficient compression and aggregation.
* **Compression:**
  * Utilize compression techniques (e.g., run-length encoding, bitmap encoding) to reduce storage space and improve read performance.
* **Chunking:**
  * Divide the data into smaller "chunks" based on time intervals or other criteria.
  * Enables efficient indexing and deletion of older data.
* **Caching:**
  * Cache frequently accessed data in memory to minimize disk I/O.
  * Cache indexes for recently accessed chunks.

**Conclusion**

* Time series databases are specialized for handling time-stamped data efficiently.
* Key design considerations include data ordering, column-oriented storage, compression, and chunking.
* Understanding these concepts is crucial for designing and implementing effective time series data solutions.

**Note:**

* This summary provides a high-level overview of the key concepts discussed in the video.
* The video provides more in-depth explanations and uses visual aids to illustrate the concepts.

I hope these notes are helpful!



## EP29: Geospatial Indexes" by Jordan has no life:

**Introduction**

* **Context:** Explains the need for geospatial indexes to efficiently find points within a certain radius of a given location.
* **Examples:** Uses Uber, Yelp, and Tinder as examples of applications that require geospatial indexing.
* **Limitations of Traditional Indexing:** Discusses the inefficiency of using traditional indexes (like those on latitude or longitude) for range queries on geographic data.

**Geohashing**

* **Core Concept:**
  * Divides the Earth's surface into a hierarchical grid of cells.
  * Each cell is assigned a unique alphanumeric code (geohash).
  * Smaller cells have longer geohashes, providing more precise location information.
* **Properties:**
  * Geohashes of nearby locations tend to have similar prefixes.
  * Allows for efficient range queries based on geohash prefixes.
* **Example:**
  * Illustrates how geohashes are generated by recursively subdividing the map into smaller regions.
  * Explains the relationship between geohash length and the size of the bounding box.

**Geospatial Indexing**

* **Indexing on Geohashes:**
  * Creates an index on the geohash field.
  * Enables efficient range queries to find points within a specific geohash range.
* **Finding Points Within a Radius:**
  * Determines the geohash depth that encompasses the desired radius.
  * Truncates the geohash of the query point to the appropriate depth.
  * Performs a range query on the index to find points within that geohash range.
  * Filters the results to include only points within the actual radius.

**Distributed Geospatial Indexing**

* **Partitioning:**
  * Partitions the data based on geohashes, assigning different regions to different nodes.
  * Enables efficient data distribution and load balancing.
* **Replication:**
  * Replicates data across multiple nodes for fault tolerance and improved read performance.

**Practical Considerations**

* **Geohash Libraries:**
  * Utilizes libraries to efficiently encode/decode geohashes and perform geospatial calculations.
* **Alternative Approaches:**
  * Mentions alternative approaches like using Voronoi diagrams or quadtrees for geospatial indexing.
* **Real-World Examples:**
  * Discusses how companies like Lyft and Uber use geospatial indexing in their systems.

**Conclusion**

* Geospatial indexing is a crucial technique for efficiently handling location-based queries in applications like ride-sharing, location-based services, and mapping.
* Geohashing provides a robust and efficient mechanism for indexing and querying geographic data.
* Understanding geospatial indexing is essential for designing scalable and performant location-based applications.

**Note:**

* This summary provides a high-level overview of the key concepts discussed in the video.
* The video provides more in-depth explanations and uses visual aids to illustrate the concepts.

I hope these notes are helpful!



## EP30: Chain Replication" by Jordan has no life:

**Introduction**

* **Recording Environment:** Jordan humorously acknowledges recording in his underwear while working from home.
* **Incentive to Subscribe:** Jokes about offering "dirty underwear shots" at 1,000 subscribers.
* **Focus:** Introduces Chain Replication as a distributed consensus algorithm.
* **Historical Context:** Mentions that his Operating Systems professor authored the original Chain Replication paper.

**Chain Replication: Core Concepts**

* **Strong Consistency & High Availability:** Aims to achieve both strong consistency and high availability.
* **Linearizability:** Guarantees that operations appear to execute in a sequential order, as if they were executed on a single machine.
* **Chain Structure:**
  * Nodes are organized in a linear chain (head, replicas, tail).
  * Writes propagate sequentially from head to tail.
  * Reads are always performed from the tail.

**Write Process**

1. **Client Writes to Head:** The client sends the write request to the head node.
2. **Head Propagation:** The head node propagates the write to the next replica in the chain.
3. **Sequential Propagation:** Each replica propagates the write to the next replica in the chain.
4. **Tail Acknowledgment:** The tail node acknowledges the write.
5. **Acknowledgement Propagation:** Acknowledgements are propagated back through the chain to the head.
6. **Client Confirmation:** The head node acknowledges the write to the client.

**Read Process**

* **Reads from Tail:** All reads are performed from the tail node.
* **Strong Consistency Guarantee:** Since reads are from the tail, they always reflect the latest committed writes.

**Failover Handling**

* **Head Failure:** The next node in the chain becomes the new head.
* **Tail Failure:** Reads are directed to the previous replica in the chain.
* **Middle Node Failure:** The chain is reconfigured by removing the failed node and connecting the adjacent nodes.
* **Coordination Service:** An external service (like ZooKeeper) is used to track node failures and manage the chain.

**Advantages of Chain Replication**

* **Strong Consistency:** Guarantees linearizability.
* **High Availability:** Tolerates node failures with minimal disruption.
* **Efficient Writes:** Writes are propagated sequentially, reducing network overhead compared to multi-cast approaches.

**Disadvantages**

* **Limited Read Throughput:** Reads are restricted to the tail node, limiting scalability.
* **Single Point of Failure:** The tail node is a bottleneck for read performance.

**Crack: Enhancing Read Throughput**

* **Dirty Reads:** Allows replicas to return "dirty" reads (unacknowledged writes) to clients.
* **Version Numbers:** Each write is assigned a version number.
* **Read Resolution:**
  * If a replica encounters a "dirty" read, it contacts the tail node to obtain the latest committed version.
  * The tail node responds with the highest committed version number.
* **Improved Read Throughput:** Enables reads from any replica, significantly improving read performance.

**Conclusion**

* Chain Replication is a valuable technique for achieving strong consistency and high availability in distributed systems.
* Crack provides a mechanism to improve read throughput while maintaining strong consistency.
* Understanding these concepts is crucial for designing and implementing robust distributed systems.

**Note:**

* This summary provides a high-level overview of the key concepts discussed in the video.
* The video provides more in-depth explanations and uses visual aids to illustrate the concepts.

I hope these notes are helpful!



## EP31: Content Delivery Networks (CDNs)" by Jordan has no life:

**Introduction**

* **Importance of CDNs:** CDNs (Content Delivery Networks) are crucial for modern websites, significantly improving the speed and efficiency of delivering static content to users.
* **Focus:** The video will explain what CDNs are, how they work, and their benefits and drawbacks.

**What are Static Content and Object Stores?**

* **Static Content:** Includes files that rarely change (e.g., HTML, images, videos).
* **Object Stores:**
  * Preferred for storing static content due to their scalability, cost-effectiveness, and flat structure.
  * Examples: AWS S3, Azure Blob Storage, Google Cloud Storage.
  * Offer higher scalability and lower cost compared to distributed file systems like HDFS.
  * Lack strong consistency guarantees and are not suitable for batch processing.

**How CDNs Work**

* **Distributed Caching:** CDNs are geographically distributed caches that store copies of static content closer to users.
* **Reduced Latency:** By serving content from nearby locations, CDNs significantly reduce latency for users.
* **Reduced Load on Origin Servers:** Offloads traffic from the origin servers (application servers) by serving content directly from the CDN.
* **Improved Performance:**
  * Faster content delivery leads to improved user experience and increased user engagement.
* **Types of CDNs:**
  * **Push CDNs:** Content is proactively pushed to the CDN by the origin server. Suitable for predictable content updates.
  * **Pull CDNs:** Content is fetched from the origin server only when requested by a user. Suitable for dynamic content and unpredictable updates.

**Benefits of Using CDNs**

* **Reduced Latency:** Faster content delivery for users.
* **Improved User Experience:** Faster loading times lead to increased user engagement and satisfaction.
* **Reduced Load on Origin Servers:** Protects servers from overload, especially during traffic spikes.
* **Increased Availability:** Provides redundancy and fault tolerance in case of origin server failures.
* **Improved Security:** Can act as a reverse proxy, mitigating DDoS attacks.

**Drawbacks of Using CDNs**

* **Stale Data:** CDNs may serve outdated content if not properly updated.
* **Cache Misses:** If the requested content is not found in the CDN, it requires an additional request to the origin server, increasing latency.
* **URL Management:** Requires managing URLs to ensure they point to the correct location (CDN or origin server).
* **Cost:** CDNs typically involve subscription fees based on data transfer and storage.

**Conclusion**

* CDNs are essential for delivering high-performance and reliable content to users worldwide.
* They are a critical component of modern web infrastructure and should be considered for any website with significant traffic.

**Note:**

* This summary provides a high-level overview of the key concepts discussed in the video.
* The video provides more in-depth explanations and uses visual aids to illustrate the concepts.

I hope these notes are helpful!



## EP32: Google Spanner" by Jordan has no life:

**Introduction**

* **Context:** Jordan acknowledges the delay in uploading the video, citing his roommate's interruptions.
* **Focus:** Introduces Spanner, a globally distributed database system developed by Google.
* **NewSQL:** Positions Spanner as a "NewSQL" database, a category of databases that aim to bridge the gap between traditional relational databases and NoSQL databases.
* **Key Features:** Highlights Spanner's focus on strong consistency, high availability, and scalability.

**Spanner's Guarantees**

* **ACID Properties:**
  * **Atomicity:** Transactions are executed atomically.
  * **Consistency:** Transactions maintain the integrity of the database.
  * **Isolation:** Concurrent transactions do not interfere with each other.
  * **Durability:** Committed transactions are persistent.
* **Serializability:** Ensures that concurrent transactions appear to execute in a serial order.
* **Linearizability:** Guarantees that every operation appears to have executed atomically at a single point in time.
* **High Availability:** Achieves high availability through data replication and fault tolerance.

**Challenges of Distributed Databases**

* **Clock Synchronization:** Traditional distributed systems rely on NTP for clock synchronization, which can introduce inaccuracies.
* **Two-Phase Locking:** Traditional two-phase locking can lead to performance bottlenecks, especially in read-heavy workloads.

**Spanner's Approach**

* **TrueTime API:**
  * Provides a unique approach to time synchronization.
  * Returns an interval (tmin, tmax) instead of a single timestamp.
  * This interval represents the uncertainty in the current time.
* **Hardware-Based Time Synchronization:** Utilizes specialized hardware (atomic clocks) and GPS for highly accurate time synchronization.
* **Read Optimization:**
  * Eliminates the need for read locks by leveraging TrueTime.
  * Reads are performed against a consistent snapshot of the data.
* **Write Optimization:**
  * Waits for a period equal to the uncertainty interval before committing writes.
  * Ensures that subsequent reads will see the committed write.

**Comparison with Traditional Databases**

* **Stronger Consistency Guarantees:** Provides stronger consistency guarantees than many traditional distributed databases.
* **Improved Performance:** Achieves high performance through optimized read operations and efficient concurrency control.
* **Scalability:** Designed to scale horizontally to handle massive datasets.

**Limitations**

* **Complexity:** Requires specialized hardware and complex synchronization mechanisms.
* **Cost:** May be more expensive to deploy and maintain compared to traditional databases.

**Conclusion**

* Spanner is a groundbreaking database system that pushes the boundaries of distributed computing.
* Its innovative approach to time synchronization and concurrency control enables strong consistency and high performance.
* While complex, Spanner represents a significant advancement in the field of distributed databases.

**Note:**

* This summary provides a high-level overview of the key concepts discussed in the video.
* The video provides more in-depth explanations and uses visual aids to illustrate the concepts.

I hope these notes are helpful!



## EP33: Load Balancing" by Jordan has no life:

**Introduction**

* **Personal Anecdote:** Jordan shares a personal anecdote about the importance of load balancing in his own life, relating it to the concept of distributing tasks effectively.
* **Vertical vs. Horizontal Scaling:** Explains the difference between vertical scaling (increasing the capacity of a single server) and horizontal scaling (adding more servers).
* **Need for Load Balancing:** Highlights the necessity of load balancing in horizontally scaled systems to prevent overloading individual servers.

**How Load Balancing Works**

* **Load Balancer Placement:**
  * Load balancers sit between clients and application servers.
  * They can also be placed between application servers and databases.
* **Heartbeat Exchange:** Load balancers exchange heartbeats with servers to monitor their health and load.
* **Routing Policies:**
  * **Least Connections:** Directs traffic to servers with the fewest active connections.
  * **Least Response Time:** Directs traffic to servers with the fastest response times.
  * **Round Robin:** Distributes traffic evenly across servers in a cyclical order.
  * **Weighted Round Robin:** Distributes traffic based on the capacity of each server.
  * **Hashing:**
    * **Layer 4 Hashing:** Uses source/destination IP addresses and ports for routing.
    * **Layer 7 Hashing:** Uses information within the request (e.g., URL, headers) for routing.
    * **Consistent Hashing:** Ensures that requests for the same key are consistently routed to the same server, improving cache utilization.

**Load Balancer Availability**

* **Active-Active:** Multiple load balancers operate concurrently, sharing the load.
* **Active-Passive:** One load balancer is active, while the other is in standby mode, taking over if the active one fails.
* **Coordination Services:** Coordination services (like ZooKeeper) can be used to manage the state of multiple load balancers.

**Additional Load Balancer Functions**

* **Reverse Proxy:** Can act as a reverse proxy, handling SSL/TLS encryption, filtering requests, and providing security features.
* **Caching:** Some load balancers can cache frequently accessed content to improve performance.

**Conclusion**

* Load balancing is a critical component of any distributed system.
* It ensures that traffic is distributed evenly across servers, preventing overloading and maximizing performance.
* Choosing the right load balancing algorithm and ensuring high availability are crucial for system reliability.

**Note:**

* This summary provides a high-level overview of the key concepts discussed in the video.
* The video provides more in-depth explanations and uses visual aids to illustrate the concepts.

I hope these notes are helpful!



## EP34: Amazon Aurora" by Jordan has no life:

**Introduction**

* **Motivation:** Jordan acknowledges viewer support and discusses the potential of quitting his job if he reaches 10,000 subscribers (humorously).
* **Focus:** Introduces Amazon Aurora, a "NewSQL" database designed to bridge the gap between traditional relational databases and NoSQL databases.
* **Performance Claims:** Highlights Aurora's claimed 5x performance improvement over traditional MySQL.

**Traditional RDBMS Design**

* **B-Tree Structure:** Explains the traditional RDBMS architecture with B-trees on disk and a memory page cache.
* **Write Process:** Describes the write process, including writing to the page cache, updating the write-ahead log, and eventual disk writes.

**Aurora's Architecture**

* **Decoupled Storage and Compute:**
  * Separates the compute layer (page cache) from the storage layer (disk).
  * Allows independent scaling of compute and storage resources.
* **Write Ahead Log (WAL):**
  * Writes are initially recorded in the WAL.
  * WAL entries are replicated to multiple storage nodes.
  * Disk nodes apply WAL entries asynchronously.
* **Reduced Network Overhead:**
  * Only WAL entries are transmitted over the network, significantly reducing write overhead compared to sending entire modified pages.
* **Independent Scaling:**
  * Compute and storage layers can be scaled independently based on application needs.
* **Protection Groups:**
  * Data is replicated across three Availability Zones within a region for high availability.
  * Each protection group consists of six storage nodes.
* **Write Quorum:**
  * Writes require acknowledgments from a quorum of storage nodes (four out of six) for durability.
* **Read Quorum:**
  * Reads are typically performed from the primary compute node, which maintains an up-to-date view of the data.
  * Quorum reads are used for recovery after node failures.
* **Caching Layer Replication:**
  * Multiple read replicas can be created for improved read performance.
  * Read replicas asynchronously replicate the write-ahead log from the primary replica.
* **Data Partitioning:**
  * Data is partitioned into 10GB segments.
  * Partitions are distributed across storage nodes using consistent hashing.
* **Failure Handling:**
  * Automatic failover for both compute and storage nodes.
  * Efficient recovery mechanisms for node failures.

**Conclusion**

* **Key Advantages:** High performance, scalability, high availability, and strong consistency.
* **Use Cases:** Suitable for a wide range of applications, including e-commerce, gaming, and financial services.
* **Importance of Understanding:** Emphasizes the importance of understanding Aurora's architecture for systems design interviews.

**Note:**

* This summary provides a high-level overview of the key concepts discussed in the video.
* The video provides more in-depth explanations and uses visual aids to illustrate the concepts.

I hope these notes are helpful!



## EP35: MongoDB" by Jordan has no life:

**Introduction**

* **Context:** Jordan acknowledges the delay in uploading the video, attributing it to his roommate's interruptions.
* **Focus:** Introduces MongoDB as a document-oriented NoSQL database.
* **Comparison:** Positions MongoDB as a middle ground between relational databases and other NoSQL databases like Cassandra.

**MongoDB: Core Concepts**

* **Document Model:**
  * Stores data in flexible documents, allowing for dynamic schemas.
  * Documents can contain nested structures (sub-documents, arrays).
  * Enables storing complex, interrelated data within a single unit.
* **Collections:** Documents are organized into collections, analogous to tables in relational databases.
* **Data Locality:** Storing related data within a single document improves data locality and reduces the need for joins.
* **Flexible Schema:** Allows for changes to the data structure without requiring major schema migrations.

**Architectural Overview**

* **Sharding:** Partitions data across multiple servers based on a shard key (usually the document ID).
* **Replication:** Uses single-leader replication within each shard for high availability.
* **B-Tree Indexing:** Utilizes B-tree indexes for efficient data retrieval.
* **Secondary Indexes:** Supports creating secondary indexes on any field within a document, enabling flexible querying.
* **Cross-Partition Queries:** Allows for queries that span multiple partitions, providing greater flexibility compared to some other NoSQL databases.

**Comparison with Other Databases**

* **vs. Relational Databases:**
  * Offers greater flexibility in data modeling and schema evolution.
  * Can be more efficient for storing and querying complex, interrelated data.
  * May have limitations in terms of strong consistency and complex joins.
* **vs. Cassandra:**
  * Provides more flexibility in data modeling and querying.
  * May have lower write performance due to the use of B-trees and single-leader replication.
  * Offers better support for complex queries and secondary indexes.

**Use Cases**

* **Applications with Evolving Data Models:** Suitable for applications where data structures may change frequently.
* **Applications with Complex Data Relationships:** Well-suited for storing and querying complex, nested data structures.
* **Content Management Systems:** Can be used to store and manage content with flexible structures.

**Limitations**

* **Write Performance:** May have lower write performance compared to some other NoSQL databases.
* **Complexity:** Can be more complex to manage and administer compared to simpler key-value stores.

**Conclusion**

* MongoDB is a versatile NoSQL database that offers a balance between flexibility and performance.
* Its document-oriented model and flexible schema make it suitable for a wide range of applications.
* Understanding its strengths and limitations is crucial for choosing the right database for a given use case.

**Note:**

* This summary provides a high-level overview of the key concepts discussed in the video.
* The video provides more in-depth explanations and uses visual aids to illustrate the concepts.

I hope these notes are helpful!



## EP36: VoltDB" by Jordan has no life:

**Introduction**

* **Recording Context:** Jordan acknowledges recording early in the morning and mentions a potential school-wide party that might affect his ability to post the video on time.
* **Focus:** Introduces VoltDB, a high-performance, in-memory database.
* **Unique Approach:** Highlights VoltDB's unique approach to serializability and its focus on optimizing performance.

**VoltDB's Design Philosophy**

* **Traditional RDBMS Limitations:** Discusses the limitations of traditional RDBMSes, particularly the overhead of two-phase locking and the prevalence of deadlocks.
* **Single-Threaded Execution:** VoltDB addresses these limitations by executing transactions serially on each node.
* **In-Memory Storage:** Stores all data in memory, eliminating the need for frequent disk I/O.
* **Stored Procedures:** Utilizes stored procedures to minimize network overhead and improve transaction efficiency.

**Key Architectural Features**

* **In-Memory Storage:**
  * Eliminates disk I/O bottlenecks.
  * Requires careful consideration of memory capacity and data volume.
* **Stored Procedures:**
  * Reduce network overhead by executing multiple operations within a single transaction.
  * Improve performance by minimizing round trips between the client and the database.
* **Command Log:**
  * Records transaction instructions for durability.
  * Supports both synchronous and asynchronous writes to disk.
* **Single-Threaded Execution:**
  * Simplifies concurrency control by avoiding the need for complex locking mechanisms.
  * Requires careful consideration of transaction execution order.
* **Sharding:**
  * Partitions data across multiple nodes for scalability.
  * Uses a coordinator node to handle transactions that span multiple partitions.
* **Replication:**
  * Supports both single-leader and multi-leader replication.
  * Handles write conflicts using techniques like "last write wins" or application-level conflict resolution.

**Performance Considerations**

* **Memory Constraints:** The amount of memory available limits the amount of data that can be stored in-memory.
* **Transaction Size:** Large transactions can increase execution time and potentially impact performance.
* **Scalability:** Careful sharding and replication strategies are crucial for scaling VoltDB to handle high transaction volumes.

**Conclusion**

* VoltDB is a unique database that offers high performance and strong consistency by leveraging in-memory storage and single-threaded execution.
* Its focus on stored procedures and efficient concurrency control makes it suitable for high-throughput, low-latency applications.
* Understanding VoltDB's architecture and design principles provides valuable insights into the challenges and trade-offs involved in building high-performance databases.

**Note:**

* This summary provides a high-level overview of the key concepts discussed in the video.
* The video provides more in-depth explanations and uses visual aids to illustrate the concepts.

I hope these notes are helpful!



## EP36: VoltDB" by Jordan has no life:

**Introduction**

* **Recording Context:** Jordan acknowledges recording early in the morning and mentions a potential school-wide party that might affect his ability to post the video on time.
* **Focus:** Introduces VoltDB, a high-performance, in-memory database.
* **Unique Approach:** Highlights VoltDB's unique approach to serializability and its focus on optimizing performance.

**VoltDB's Design Philosophy**

* **Traditional RDBMS Limitations:** Discusses the limitations of traditional RDBMSes, particularly the overhead of two-phase locking and the prevalence of deadlocks.
* **Single-Threaded Execution:** VoltDB addresses these limitations by executing transactions serially on each node.
* **In-Memory Storage:** Stores all data in memory, eliminating the need for frequent disk I/O.
* **Stored Procedures:** Utilizes stored procedures to minimize network overhead and improve transaction efficiency.

**Key Architectural Features**

* **In-Memory Storage:**
  * Eliminates disk I/O bottlenecks.
  * Requires careful consideration of memory capacity and data volume.
* **Stored Procedures:**
  * Reduce network overhead by executing multiple operations within a single transaction.
  * Improve performance by minimizing round trips between the client and the database.
* **Command Log:**
  * Records transaction instructions for durability.
  * Supports both synchronous and asynchronous writes to disk.
* **Single-Threaded Execution:**
  * Simplifies concurrency control by avoiding the need for complex locking mechanisms.
  * Requires careful consideration of transaction execution order.
* **Sharding:**
  * Partitions data across multiple nodes for scalability.
  * Uses a coordinator node to handle transactions that span multiple partitions.
* **Replication:**
  * Supports both single-leader and multi-leader replication.
  * Handles write conflicts using techniques like "last write wins" or application-level conflict resolution.

**Performance Considerations**

* **Memory Constraints:** The amount of memory available limits the amount of data that can be stored in-memory.
* **Transaction Size:** Large transactions can increase execution time and potentially impact performance.
* **Scalability:** Careful sharding and replication strategies are crucial for scaling VoltDB to handle high transaction volumes.

**Conclusion**

* **Key Advantages:** High performance, scalability, high availability, and strong consistency.
* **Use Cases:** Suitable for a wide range of applications, including e-commerce, gaming, and financial services.
* **Importance of Understanding:** Emphasizes the importance of understanding Aurora's architecture and design principles for systems design interviews.

**Note:**

* This summary provides a high-level overview of the key concepts discussed in the video.
* The video provides more in-depth explanations and uses visual aids to illustrate the concepts.

I hope these notes are helpful!



## EP38: WebSockets, Long Polling, Server Sent Events" by Jordan has no life:

**Introduction**

* **Personal Updates:** Jordan shares personal updates about his sunburn and plans for the channel, including a shift towards more interview-focused content in June.
* **Focus:** Introduces three techniques for real-time updates in applications: polling, long polling, and server-sent events.

**Polling**

* **Concept:** Clients periodically send requests to the server to check for updates.
* **Pros:** Simple to implement.
* **Cons:**
  * High server load due to frequent requests, even when no updates are available.
  * Inefficient use of resources.
  * Potential for overloading the server.

**Long Polling**

* **Concept:** Clients send a request to the server. The server holds the connection open until new data is available or a timeout occurs.
* **Pros:** More efficient than traditional polling as it reduces the number of requests.
* **Cons:**
  * Still requires re-establishing connections, which can be resource-intensive.
  * Limited to one-directional communication (client to server).
  * Potential for race conditions if multiple clients are connected.

**WebSockets**

* **Concept:** Establishes a persistent, two-way connection between the client and server.
* **Pros:**
  * Bidirectional communication allows for real-time updates in both directions.
  * Reduced overhead due to persistent connections and efficient message framing.
* **Cons:**
  * Higher overhead for infrequent updates compared to long polling.
  * Potential for the "thundering herd" problem if many connections are re-established simultaneously.
  * Requires server-side support for managing a large number of concurrent connections.

**Server-Sent Events (SSE)**

* **Concept:** A one-way communication mechanism from server to client.
* **Pros:**
  * Simpler to implement than WebSockets.
  * Lower overhead than WebSockets for one-way communication.
  * Handles re-establishing connections more gracefully.
* **Cons:**
  * Limited to one-way communication.

**Choosing the Right Approach**

* **Polling:** Suitable for infrequent updates or when simplicity is paramount.
* **Long Polling:** A good balance between efficiency and simplicity.
* **WebSockets:** Ideal for applications requiring real-time, bidirectional communication (e.g., chat, collaborative editing).
* **Server-Sent Events:** Suitable for one-way communication scenarios (e.g., notifications, live updates).

**Conclusion**

* Understanding the trade-offs between polling, long polling, WebSockets, and Server-Sent Events is crucial for designing efficient and scalable real-time applications.
* The choice of technology depends on the specific requirements of the application, such as the frequency of updates, the need for bidirectional communication, and the desired level of performance.

**Note:**

* This summary provides a high-level overview of the key concepts discussed in the video.
* The video provides more in-depth explanations and uses visual aids to illustrate the concepts.

I hope these notes are helpful!



## EP37: Monoliths vs. Microservices" by Jordan has no life:

**Introduction**

* **Context:** Jordan acknowledges a delay in uploading the video and briefly discusses his personal life.
* **Focus:** Introduces the concept of microservices architecture and compares it to the traditional monolith architecture.

**Monolith Architecture**

* **Definition:** A single, self-contained application that includes all the functionality of a system.
* **Advantages:**
  * Simple to implement and deploy.
  * Easy to test due to the presence of all code in a single unit.
  * Easier to scale initially by simply deploying more instances of the same application.
* **Disadvantages:**
  * Difficult to maintain and update as the codebase grows.
  * Slow deployments due to the need to redeploy the entire application for any changes.
  * Limited scalability as all components are tightly coupled.
  * Difficult to introduce new technologies or frameworks.

**Microservices Architecture**

* **Definition:** A system where functionality is broken down into small, independent services that communicate with each other.
* **Advantages:**
  * Improved maintainability and scalability.
  * Independent deployment and scaling of individual services.
  * Technology diversity: Each service can use the most suitable technology stack.
  * Increased resilience: Failure of one service does not necessarily bring down the entire system.
* **Disadvantages:**
  * Increased complexity in terms of development, deployment, and monitoring.
  * Increased communication overhead between services.
  * Potential for distributed system issues (e.g., latency, consistency).

**Docker and Microservices**

* **Docker:** A containerization technology that packages applications and their dependencies into isolated containers.
* **Benefits:**
  * Enables consistent deployment across different environments.
  * Improves resource utilization by allowing multiple containers to run on the same host.
  * Facilitates easier scaling and management of microservices.

**Conclusion**

* The choice between monolith and microservices architecture depends on factors such as the size and complexity of the application, the team's size and expertise, and the desired level of scalability and flexibility.
* Microservices offer significant advantages for large-scale, complex systems, but they also introduce additional complexity and operational challenges.

**Note:**

* This summary provides a high-level overview of the key concepts discussed in the video.
* The video provides more in-depth explanations and uses visual aids to illustrate the concepts.

I hope these notes are helpful!



## EP39: Apache Spark" by Jordan has no life:

**Introduction**

* **Spark as a Dataflow Engine:** Spark is an open-source cluster computing framework designed for large-scale data processing. It's considered a dataflow engine, offering significant performance improvements over MapReduce.
* **Developed at UC Berkeley:** Spark was developed by researchers at UC Berkeley around 10 years ago.

**Limitations of MapReduce**

* **Inefficient for Iterative Jobs:** MapReduce is poorly suited for iterative jobs or jobs that involve chaining multiple map and reduce stages.
  * **Example:** PageRank algorithm, where iterative calculations are performed on a graph.
* **Intermediate State Materialization:** MapReduce writes intermediate results to disk after each map and reduce stage, leading to significant disk I/O overhead.

**Spark's Approach: Resilient Distributed Datasets (RDDs)**

* **In-Memory Computation:** Spark utilizes RDDs, which are in-memory data structures that represent a collection of data.
* **Lineage Tracking:** Spark tracks the lineage of each RDD, representing the sequence of transformations applied to the original data.
* **Reduced Disk I/O:** By keeping data in memory, Spark minimizes disk I/O, significantly improving performance.
* **Fault Tolerance:**
  * **Narrow Dependencies:** If a node fails during a narrow dependency (where data is processed independently on each node), other nodes can recompute the lost data.
  * **Wide Dependencies:** If a node fails during a wide dependency (where data is shuffled between nodes), Spark can recompute the data from the last checkpoint.

**Checkpointing**

* **Periodically Saving State:** Spark allows for checkpointing, where the state of an RDD is saved to persistent storage (like HDFS).
* **Fault Recovery:** Checkpoints enable faster recovery from node failures in case of wide dependencies.

**Comparison with MapReduce**

* **Spark:**
  * Better for iterative jobs and complex data pipelines.
  * Reduced disk I/O due to in-memory computations.
  * More flexible and expressive programming model.
* **MapReduce:**
  * Simpler to understand and implement.
  * More robust to node failures due to its focus on disk-based storage.

**Conclusion**

* Spark has become a popular choice for large-scale data processing due to its performance advantages over MapReduce.
* Its use of RDDs and efficient fault tolerance mechanisms make it well-suited for a wide range of data processing tasks.

**Note:**

* This summary provides a high-level overview of the key concepts discussed in the video.
* The video provides more in-depth explanations and uses visual aids to illustrate the concepts.

I hope these notes are helpful!



## EP40: Flink in 15 Minutes, Stateful Stream Processing!" by Jordan has no life:

**Introduction**

* **Personal Anecdote:** Jordan starts with a humorous anecdote about his diet and a hangover.
* **Focus:** Introduces Apache Flink as a stream processing framework.
* **Clarification:** Clarifies that Flink is not a message broker, but rather a framework for processing streams of data.

**Key Concepts**

* **Stateful Stream Processing:** Flink enables stateful computations on streams of data, unlike Spark Streaming which uses micro-batching.
* **Real-time Processing:** Processes messages in real-time, allowing for low-latency applications.
* **State Management:** Flink maintains state (e.g., windowed aggregations, user sessions) to perform complex stream processing operations.

**Architecture**

* **Job Manager:**
  * Manages the execution of Flink jobs.
  * Creates a dataflow graph based on the user's program.
  * Assigns tasks to Task Managers.
  * Monitors the health of Task Managers.
* **Task Managers:**
  * Execute tasks defined by the Job Manager.
  * Manage the local state of the application.
* **Data Flow:**
  * Data flows through a series of operators (e.g., source, map, filter, reduce, window).
  * Operators can be chained together to form complex data processing pipelines.

**State Management**

* **Importance of State:**
  * Essential for many stream processing applications, such as windowing, joins, and aggregations.
  * Allows for maintaining context across multiple messages.
* **Statefulness:** Flink provides built-in mechanisms for managing state, such as:
  * **Keyed State:** State associated with a specific key (e.g., user ID).
  * **Operator State:** State associated with an operator (e.g., windowing state).
* **Checkpointing:**
  * Periodically saves the state of the application to persistent storage (e.g., HDFS).
  * Enables fault tolerance and recovery from node failures.

**Fault Tolerance**

* **Checkpoint Barriers:**
  * Barriers are inserted into the data stream.
  * When a barrier reaches a Task Manager, it triggers a checkpoint.
  * Ensures that all data up to the barrier has been processed before checkpointing.
* **Asynchronous Checkpointing:** Checkpointing occurs asynchronously in the background, minimizing the impact on application performance.

**Use Cases**

* **Real-time Analytics:** Analyzing streaming data from sensors, social media, and other sources.
* **Fraud Detection:** Detecting and preventing fraudulent activities in real-time.
* **Recommendation Systems:** Generating real-time recommendations based on user behavior.
* **IoT Applications:** Processing data from IoT devices.

**Conclusion**

* Flink is a powerful stream processing framework that enables real-time, stateful computations on streaming data.
* Its key features include in-memory processing, fault tolerance, and efficient state management.
* Flink is well-suited for a wide range of applications that require real-time data analysis and processing.

**Note:**

* This summary provides a high-level overview of the key concepts discussed in the video.
* The video provides more in-depth explanations and uses visual aids to illustrate the concepts.

I hope these notes are helpful!



## EP41: Bloom Filters for set approximation" by Jordan has no life:

**Introduction**

* **Personal Updates:**
  * Jordan discusses his subscriber count and the potential for a "day in the life" video.
  * Mentions an upcoming collaboration with another YouTuber.
* **Focus:** Introduces Bloom Filters, a probabilistic data structure used to approximate the contents of a set.

**What are Bloom Filters?**

* **Purpose:**
  * Determine if an element **definitely does not** belong to a set.
  * Cannot definitively say if an element **is** in the set (may produce false positives).
* **Key Characteristics:**
  * **Space-Efficient:** Uses a small amount of memory to represent a large set.
  * **Fast Lookups:** Can quickly determine if an element is **not** in the set.
  * **Probabilistic:** May produce false positives (indicating an element is in the set when it's not).

**Use Cases**

* **Cache Filtering:**
  * Check if a requested item is in the cache before querying the database.
  * Avoid unnecessary database lookups for items that are likely not in the cache.
* **LSM Tree Reads:**
  * Check if a key is present in previous SSTables before searching the current SSTable.
  * Reduces the number of SSTables that need to be searched.
* **Other Applications:**
  * Spam filtering
  * Network intrusion detection
  * Database indexing

**Bloom Filter Implementation**

* **Initialization:**
  * Create an array of bits (initialized to 0).
  * Choose 'k' independent hash functions.
* **Adding an Element:**
  * Hash the element using each of the 'k' hash functions.
  * Set the corresponding bits in the array to 1.
* **Checking for Membership:**
  * Hash the element using each of the 'k' hash functions.
  * If any of the corresponding bits are 0, the element is definitely not in the set.
  * If all bits are 1, the element **may** be in the set (potential false positive).

**False Positives**

* **Probability of False Positives:** Increases as more elements are added to the set.
* **Mitigation:**
  * Increase the size of the bit array.
  * Increase the number of hash functions.
* **Trade-off:** Increasing the number of hash functions or the array size can reduce false positives but increases memory usage.

**Conclusion**

* Bloom Filters are a powerful and space-efficient data structure for approximate set membership.
* They are widely used in various applications to improve performance and reduce resource usage.
* Understanding Bloom Filters is valuable for systems design interviews and for designing efficient data structures.

**Note:**

* This summary provides a high-level overview of the key concepts discussed in the video.
* The video provides more in-depth explanations and uses visual aids to illustrate the concepts.

I hope these notes are helpful!



## EP42: Merkle Trees" by Jordan has no life:

**Introduction**

* **Personal Anecdote:** Jordan starts with a humorous anecdote about his pre-workout routine and energy levels.
* **Merkle Trees:** Introduces Merkle Trees as an efficient algorithm for calculating differences between two sets of records.

**Core Concepts**

* **Hash Tree:** Merkle Trees are essentially hash trees.
* **Leaf Nodes:** Each leaf node represents a hash of a specific piece of data (e.g., a file).
* **Parent Nodes:** Each parent node's hash is calculated by combining the hashes of its child nodes.
* **Root Hash:** The topmost node in the tree, representing the overall hash of the entire dataset.

**Use Cases**

* **Git Version Control:**
  * Tracks changes between different versions of files.
  * Uses Merkle Trees to efficiently identify and transmit only the changed portions of files.
* **Anti-Entropy in Distributed Systems:**
  * Used in systems like Cassandra to efficiently identify and synchronize differences between replicas.
* **Blockchain:**
  * Used to verify the integrity of blocks in a blockchain.
  * Ensures that transactions within a block have not been tampered with.

**Merkle Tree Example**

* **Illustrates the creation of a Merkle Tree** with four files (a.txt, b.txt, c.txt, d.txt).
* **Demonstrates how a change to one file** affects the hashes of its parent nodes and ultimately the root hash.
* **Explains how to efficiently identify the changed file** by traversing the tree and comparing hashes.

**Merkle Trees in Git**

* **Versioning:** Git uses Merkle Trees to track changes between different versions of files.
* **Branching:** Each branch in Git has its own Merkle Tree, representing the history of changes in that branch.
* **Efficient Change Tracking:** Only the changed files and their corresponding hashes need to be stored and transmitted.

**Merkle Trees in Cassandra**

* **Anti-Entropy:** Used to efficiently identify and repair inconsistencies between replicas.
* **Incremental Read Repair:** Cassandra uses Merkle Trees to identify and repair only the changed portions of data, reducing network overhead.
* **Immutable SSTables:** Leverages the immutability of SSTables to optimize the anti-entropy process.

**Conclusion**

* Merkle Trees are a powerful data structure with numerous applications in distributed systems.
* They provide an efficient way to calculate and track changes in data.
* Understanding Merkle Trees is crucial for understanding the inner workings of systems like Git, Cassandra, and blockchain.

**Note:**

* This summary provides a high-level overview of the key concepts discussed in the video.
* The video provides more in-depth explanations and uses visual aids to illustrate the concepts.

I hope these notes are helpful!



## EP43: Data Serialization (Protocol Buffers, Thrift, Avro)" by Jordan has no life:

**Introduction**

* **Personal Updates:** Jordan shares personal updates about his recent collaboration video and plans for the channel.
* **Focus:** Introduces the concept of data serialization, explaining the need to convert in-memory data into a format suitable for storage or transmission.
* **Limitations of Language-Specific Serialization:** Discusses the limitations of built-in serialization libraries (like Python's pickle) such as language-specific limitations, performance issues, and security vulnerabilities.

**Standardized Encodings: JSON and XML**

* **Popularity:** Explains the popularity of JSON and XML due to their widespread adoption and cross-platform compatibility.
* **Limitations:**
  * **Verbosity:** JSON and XML can be verbose, leading to increased network overhead.
  * **Data Type Limitations:** Limited data type support and potential for ambiguity (e.g., differentiating between numbers and strings).
  * **Performance:** Can be less performant compared to binary encodings.

**Binary Encodings: Thrift and Protocol Buffers**

* **Introduction:** Introduces Thrift (developed by Facebook) and Protocol Buffers (developed by Google) as efficient binary encoding formats.
* **Schema Definition:**
  * Requires defining a schema that describes the structure of the data.
  * Enables code generation for efficient encoding and decoding.
* **Field Tags:**
  * Uses field tags to identify fields within a message, allowing for compact encoding.
* **Schema Evolution:**
  * Supports schema evolution by allowing the addition of new fields while maintaining compatibility with older versions.
* **Performance:**
  * Significantly more compact and efficient than text-based formats like JSON and XML.

**Apache Avro**

* **Focus on Schema Evolution:** Designed with a strong focus on schema evolution.
* **Writer and Reader Schemas:** Allows for differences between the writer schema (used to encode data) and the reader schema (used to decode data).
* **Flexibility:** Supports more flexible schema evolution, including the addition, removal, and modification of fields.
* **Self-Describing Format:** Can optionally include the writer schema within the encoded data, making it more self-contained.

**Conclusion**

* Data serialization is a crucial aspect of distributed systems.
* Binary encoding formats like Thrift, Protocol Buffers, and Avro offer significant advantages over text-based formats in terms of performance and efficiency.
* Understanding the trade-offs between different serialization formats is important for designing and implementing efficient and scalable systems.

**Note:**

* This summary provides a high-level overview of the key concepts discussed in the video.
* The video provides more in-depth explanations and uses visual aids to illustrate the concepts.

I hope these notes are helpful!



## EP44: Apache Parquet" by Jordan has no life:

**Introduction**

* **Personal Updates:** Jordan shares personal updates about his recent sunburn and plans for the channel, including a shift towards more interview-focused content in June.
* **Focus:** Introduces Parquet, a columnar storage format for efficient data processing and analysis.
* **Relationship to Other Technologies:** Explains how Parquet builds upon other technologies like HDFS and MapReduce.

**Key Concepts**

* **Columnar Storage:**
  * Stores data in columns rather than rows.
  * Optimizes for analytical queries that typically access a subset of columns.
* **Data Compression:**
  * Employs efficient compression techniques (e.g., run-length encoding, dictionary encoding, bit-packing) to reduce storage space and improve read performance.
* **Predictive Encoding:**
  * Predicts the next value in a column based on previous values.
  * Encodes the difference between the actual value and the predicted value, resulting in smaller data sizes.
* **Data Partitioning:**
  * Partitions data into smaller files for better data locality and parallel processing.
  * Enables efficient filtering and querying of specific subsets of data.
* **Metadata:**
  * Includes statistics about the data (e.g., min, max, number of null values).
  * Enables efficient predicate pushdown and query optimization.

**Comparison with Other Formats**

* **Compared to Row-Oriented Formats:**
  * Offers significant performance advantages for analytical queries.
  * More efficient storage for data with many columns.
* **Compared to Other Columnar Formats:**
  * Provides a balance between compression efficiency and query performance.
  * Supports a wide range of data types and complex data structures.

**Use Cases**

* **Data Warehousing:** Storing and analyzing large volumes of data for business intelligence and reporting.
* **Data Lakes:** Storing and processing diverse data sets for data exploration and analysis.
* **Machine Learning:** Training and serving machine learning models.
* **Stream Processing:** Processing and analyzing streaming data in real-time.

**Conclusion**

* Parquet is a powerful and efficient data storage format for analytical workloads.
* Its columnar storage, efficient compression, and rich metadata support make it a popular choice for data warehousing, data lakes, and other data-intensive applications.
* Understanding Parquet is crucial for anyone working with big data and distributed systems.

**Note:**

* This summary provides a high-level overview of the key concepts discussed in the video.
* The video provides more in-depth explanations and uses visual aids to illustrate the concepts.

I hope these notes are helpful!



## EP45: TCP versus UDP" by Jordan has no life:

**Introduction**

* **Context:** Jordan acknowledges a delay in uploading the video, citing procrastination and a lack of motivation.
* **Focus:** Introduces TCP and UDP, two fundamental protocols in network communication.
* **Importance:** Emphasizes the importance of understanding these protocols for systems design interviews and general networking knowledge.

**TCP (Transmission Control Protocol)**

* **Reliable and Ordered Delivery:**
  * Guarantees reliable and ordered delivery of data.
  * Uses sequence numbers to ensure data is received in the correct order.
  * Implements error checking mechanisms (checksums) to detect data corruption.
* **Three-Way Handshake:**
  * Establishes a connection between two endpoints before data transmission.
  * Involves SYN, SYN-ACK, and ACK messages.
* **Flow Control:**
  * Prevents the receiver from being overwhelmed with data.
  * Uses windowing mechanisms to regulate the flow of data.
* **Congestion Control:**
  * Adjusts the transmission rate to avoid overloading the network.
  * Uses algorithms like additive increase/multiplicative decrease to dynamically adjust the sending rate.
* **Connection Termination:**
  * Involves a four-way handshake to gracefully close the connection.

**UDP (User Datagram Protocol)**

* **Unreliable and Unordered Delivery:**
  * Does not guarantee reliable or ordered delivery of data.
  * Packets may be lost, duplicated, or arrive out of order.
* **No Handshake:**
  * No connection establishment required.
  * Data can be sent immediately without any overhead.
* **Lightweight:**
  * Lower overhead compared to TCP due to the lack of handshakes and error checking.

**Use Cases**

* **TCP:**
  * HTTP, HTTPS, FTP, Telnet
  * Applications that require reliable and ordered data delivery.
* **UDP:**
  * DNS, DHCP, streaming media (e.g., video conferencing, online gaming)
  * Applications where performance and low latency are critical and some data loss is acceptable.

**Conclusion**

* TCP and UDP are fundamental protocols in network communication.
* Understanding their characteristics and trade-offs is crucial for designing and implementing robust and efficient network applications.
* The choice between TCP and UDP depends on the specific requirements of the application.

**Note:**

* This summary provides a high-level overview of the key concepts discussed in the video.
* The video provides more in-depth explanations and uses visual aids to illustrate the concepts.

I hope these notes are helpful!



## EP46: Certificate Transparency (SSL/TLS)" by Jordan has no life:

**Introduction**

* **Personal Anecdote:** Jordan starts with a humorous anecdote about his recent diet and exercise struggles.
* **Focus:** Introduces Certificate Transparency, a system designed to improve the security of HTTPS.
* **Motivation:** Explains the need for Certificate Transparency to address the issue of malicious certificates.

**Man-in-the-Middle Attacks**

* **How they work:**
  * Attackers intercept communication between a user and a server.
  * Trick the user into communicating with them instead of the actual server.
  * Steal sensitive information like passwords, credit card details, and location data.

**HTTPS and SSL/TLS**

* **Encryption:** HTTPS uses SSL/TLS to encrypt communication between the user and the server.
* **Public-Key Encryption:** Utilizes public-key cryptography (RSA) to encrypt data.
* **Server Certificates:** Servers present certificates to prove their identity.
* **Certificate Authority (CA):**
  * Issues certificates to websites.
  * Verifies the identity of the website owner.

**Limitations of Traditional Certificate Verification**

* **Trust in CAs:** Relies on the trust in the integrity of the Certificate Authority.
* **Potential for Malicious CAs:** Malicious CAs can issue fraudulent certificates, allowing attackers to impersonate legitimate websites.

**Certificate Transparency**

* **Goal:** To increase transparency and accountability in the certificate issuance process.
* **Key Components:**
  * **Certificate Logs:** Publicly auditable logs that record all issued certificates.
  * **Monitors:** Programs that monitor certificate logs for suspicious activity.
  * **Auditors:** Programs that verify the authenticity of certificates by checking their presence in the logs.

**How Certificate Transparency Works**

1. **Certificate Issuance:** When a Certificate Authority issues a certificate, it is added to the public log.
2. **Log Verification:**
   * Browsers and other clients verify that a certificate is present in the log.
   * This is done by checking if the certificate's hash is included in the Merkle Tree of the log.
3. **Monitor and Auditor Interaction:**
   * Monitors and auditors independently verify the integrity of the log.
   * They compare their own views of the log and detect inconsistencies.
   * This helps to identify and mitigate potential attacks on the log itself.

**Merkle Trees in Certificate Transparency**

* **Efficient Verification:** Merkle Trees enable efficient verification of certificate inclusion in the log.
* **Reduced Storage and Transmission:** Only a small portion of the log needs to be transmitted for verification.

**Limitations**

* **Log Consistency:** Ensuring consistency across multiple log servers is a challenge.
* **Performance Overhead:** Log verification can add a small overhead to the HTTPS handshake.
* **Potential for Attacks:** While rare, attacks on the log servers or the Merkle Tree itself are possible.

**Conclusion**

* Certificate Transparency is a crucial security mechanism for the modern web.
* It enhances the trust and security of HTTPS by increasing transparency in the certificate issuance process.
* Understanding the principles of Certificate Transparency is important for anyone interested in web security and systems design.

**Note:**

* This summary provides a high-level overview of the key concepts discussed in the video.
* The video provides more in-depth explanations and uses visual aids to illustrate the concepts.

I hope these notes are helpful!



## EP47: Collaborative Text Editing (operational transform v. CRDT)" by Jordan has no life:

**Introduction**

* **Personal Updates:** Jordan discusses his recent sunburn and plans for the channel, including a shift towards more interview-focused content in June.
* **Focus:** Introduces the challenge of collaborative text editing, where multiple users simultaneously edit a document.
* **Problem Statement:** Highlights the issue of conflicting edits and the need for a mechanism to ensure all users see a consistent version of the document.

**Operational Transform**

* **Concept:**
  * A technique for resolving conflicts in collaborative text editing.
  * Accepts the fact that edits will occur concurrently.
  * Defines a transformation function ("T") that modifies subsequent operations based on the order of operations.
* **Example:**
  * Illustrates how inserting characters at different positions can lead to inconsistencies.
  * Shows how the transformation function modifies subsequent operations to maintain consistency.
* **Limitations:**
  * Requires a central authority or a consensus algorithm to order operations.
  * Can become complex and inefficient for a large number of concurrent users.

**CRDTs for Text Editing**

* **Concept:**
  * Explores the use of Conflict-Free Replicated Data Types (CRDTs) for collaborative text editing.
  * CRDTs are designed to converge to the same state regardless of the order of operations.
* **Challenges:**
  * Traditional CRDTs are designed for commutative operations (e.g., sets, counters).
  * Text editing operations (insertions, deletions) are not inherently commutative.
* **Character-Based Approach:**
  * Represents the document as a set of character-position tuples.
  * Each character is assigned a unique position within the document.
  * Insertions and deletions are represented as operations on these tuples.
* **Position Calculation:**
  * Calculates character positions based on the relative positions of neighboring characters.
  * Ensures that insertions and deletions maintain consistent positions.
* **Conflict Resolution:**
  * Handles conflicts (e.g., concurrent insertions at the same position) using strategies like timestamping or arbitrary resolution.

**Conclusion**

* Collaborative text editing presents significant challenges in ensuring consistency and handling concurrent operations.
* Operational Transform and CRDT-based approaches offer different solutions to these challenges.
* CRDTs show promise for more scalable and decentralized collaborative editing systems.
* The field of collaborative text editing is an active area of research with ongoing advancements.

**Note:**

* This summary provides a high-level overview of the key concepts discussed in the video.
* The video provides more in-depth explanations and uses visual aids to illustrate the concepts.

I hope these notes are helpful!



## EP48: Bitcoin" by Jordan has no life:

**Introduction**

* **Personal Anecdote:** Jordan starts with a humorous anecdote about his roommate's unexpected presence and the resulting delay in recording.
* **Focus:** Introduces Bitcoin as a decentralized cryptocurrency built on blockchain technology.
* **Motivation:** Explains the need for a decentralized system to ensure trust in a world where not all parties can be trusted.

**Coins and Transactions**

* **Coins as Transaction Records:**
  * Explains that coins represent a chain of transaction records.
  * Each transaction record includes:
    * The public key of the current owner.
    * The hash of the previous block (where the coin was acquired).
    * The previous owner's signature (verifying ownership).
* **Example:**
  * Illustrates a simple transaction where a user transfers a coin to another user.
  * Explains how signatures are used to verify the validity of transactions.

**Blockchain: A Chain of Blocks**

* **Purpose:**
  * Creates an ordered and immutable record of all transactions.
  * Prevents double-spending by ensuring that each coin can only be spent once.
* **Block Structure:**
  * Each block contains:
    * A hash of the previous block in the chain.
    * A set of transactions.
    * A nonce (a unique number).
* **Mining:**
  * The process of finding a valid nonce for a block.
  * Involves performing computationally intensive calculations (proof-of-work).
  * The first miner to find a valid nonce adds the block to the blockchain.

**Proof-of-Work**

* **Purpose:**
  * Prevents spam and ensures that only valid blocks are added to the blockchain.
  * Controls the rate at which new blocks are added to the blockchain.
* **Mechanism:**
  * Miners must find a nonce that, when combined with other block data, produces a hash that starts with a certain number of leading zeros.
  * The difficulty of finding the nonce is adjusted to maintain a consistent block generation rate.

**Blockchain Consensus**

* **Forking:**
  * Explains how forks can occur when multiple miners find valid nonces for different blocks simultaneously.
  * Describes how the longest chain is typically selected as the valid chain.
* **Double Spending:**
  * Explains how double spending attempts can be prevented by verifying the validity of transactions and adhering to the longest chain rule.

**Limitations of Bitcoin**

* **Scalability:**
  * Limited transaction throughput due to the slow block generation rate.
* **Environmental Impact:**
  * Proof-of-work consumes significant computational resources, leading to high energy consumption.
* **Alternative Consensus Mechanisms:**
  * Briefly mentions alternative consensus mechanisms like proof-of-stake, which aim to improve efficiency and reduce energy consumption.

**Conclusion**

* Bitcoin is a groundbreaking technology with the potential to revolutionize finance and other industries.
* Understanding the underlying principles of blockchain technology is crucial for anyone interested in distributed systems and cryptocurrency.

**Note:**

* This summary provides a high-level overview of the key concepts discussed in the video.
* The video provides more in-depth explanations and uses visual aids to illustrate the concepts.

I hope these notes are helpful!



## EP49: Neo4j (Graph Databases)" by Jordan has no life:

**Introduction**

* **Personal Updates:** Jordan acknowledges the delay in uploading the video, citing his recent graduation and the resulting busyness.
* **Series Conclusion:** He mentions that this will likely be one of the last videos in the current systems design series.
* **Focus:** Introduces Neo4j as a popular graph database.

**Graph Databases: A New Approach**

* **Traditional Databases:** Traditional databases (relational) are not well-suited for handling complex relationships between data.
* **Graph Data:** Many real-world datasets are inherently graph-like (social networks, knowledge graphs, recommendation systems).
* **Graph Databases:**
  * Designed to efficiently store and query data with complex relationships.
  * Represent data as nodes and edges, allowing for efficient traversal of connections.

**Neo4j: A Native Graph Database**

* **Native vs. Non-Native:**
  * **Non-Native:** Graph databases built on top of existing databases (e.g., Cassandra).
  * **Native:** Neo4j is a native graph database, meaning it stores data in a graph-specific format.
* **Data Model:**
  * **Nodes:** Represent entities (e.g., users, products).
  * **Relationships:** Represent connections between nodes (e.g., "follows," "friends," "bought").
  * **Properties:** Attributes associated with nodes and relationships.
* **Flexible Schema:** Allows for dynamic schema changes and the addition of new properties.

**Neo4j's Architecture**

* **Index-Free Adjacency:**
  * Stores data using linked lists to represent relationships between nodes.
  * Eliminates the need for traditional indexes, improving query performance.
* **Disk-Based Storage:**
  * Stores data on disk, allowing for efficient handling of large datasets.
* **Transaction Support:**
  * Supports ACID properties (Atomicity, Consistency, Isolation, Durability) to ensure data integrity.
  * Uses two-phase locking and a write-ahead log for transaction management.

**Performance Advantages**

* **Efficient Traversal:**
  * Enables efficient traversal of complex relationships through linked lists.
  * Reduces the overhead of traditional indexing.
* **High Throughput:**
  * Can handle high-volume graph queries with minimal latency.

**Challenges**

* **Complexity:**
  * Can be more complex to design and implement than traditional databases.
* **Scalability:**
  * Scaling graph databases can be challenging, especially for very large datasets.

**Conclusion**

* Neo4j is a powerful graph database that offers significant advantages for handling complex relationships.
* Its unique architecture and efficient data storage make it well-suited for a wide range of applications.
* Understanding graph databases is becoming increasingly important for systems designers and data engineers.

**Note:**

* This summary provides a high-level overview of the key concepts discussed in the video.
* The video provides more in-depth explanations and uses visual aids to illustrate the concepts.

I hope these notes are helpful!

## EP50: Approaching Interview Questions" by Jordan has no life:

**Introduction**

* **Personal Updates:** Jordan shares personal updates about his recent activities and acknowledges the support of his viewers.
* **Series Conclusion:** Announces that this will likely be the last video in the current systems design series.
* **Shift in Focus:** Plans to shift the channel's focus towards more interview-specific content, including practice problems.
* **Video Goal:** Provides guidance on how to approach systems design interview questions effectively.

**Approaching Interview Questions**

1. **Clarify Requirements:**
   * **Scope Definition:** Understand the specific scope of the problem. Avoid designing the entire system; focus on a specific component.
   * **Ask Questions:**
     * **Scale:** Number of users, data volume, performance goals.
     * **Functional Requirements:** Features to be included (e.g., for Twitter: posting tweets, direct messages, image uploads).
   * **Hone in on Scope:** Determine the specific aspect of the system to focus on based on the interview's scope and your own expertise.
2. **Capacity Estimations:**
   * **Data Volume:** Estimate the amount of data to be stored and processed (e.g., number of users, tweets per user, average tweet size).
   * **Read/Write Ratios:** Determine the expected ratio of read and write operations.
   * **Storage Requirements:** Estimate the storage capacity needed based on data volume and user growth.
   * **Example:** Calculate the storage required for 140-character tweets, considering the number of users and their tweeting frequency.
3. **API Definitions:**
   * **Define Basic APIs:**
     * Create simple function definitions for key operations (e.g., create user, post tweet, get user timeline).
     * Specify input parameters and expected output.
   * **Keep it Concise:** Avoid over-engineering the API definitions; focus on the core functionalities.
4. **Data Tables:**
   * **Design Basic Schema:**
     * Create simple data tables to represent the key entities (e.g., users, tweets, followers).
     * Include relevant fields (e.g., user ID, username, tweet ID, timestamp).
   * **Consider Relationships:**
     * Think about how different tables will be related (e.g., one-to-many relationship between users and tweets).
5. **System Design**
   * **Load Balancing:**
     * Discuss how to distribute traffic across multiple servers.
     * Consider load balancing algorithms (e.g., round robin, least connections).
   * **Replication:**
     * Explain how to replicate data for fault tolerance and improved read performance.
   * **Sharding:**
     * Discuss how to partition data across multiple servers to handle large datasets.
   * **Caching:**
     * Explain how to use caching to improve read performance and reduce load on the database.
   * **Microservices:**
     * Consider breaking down the system into smaller, independent services.
6. **Trade-offs and Justifications**
   * **Explain Design Choices:**
     * Justify your design decisions by explaining the trade-offs involved.
     * Compare and contrast different approaches and explain why you chose a particular solution.
   * **Address Challenges:**
     * Anticipate potential challenges and discuss how you would address them (e.g., scalability, fault tolerance, data consistency).
7. **Handling Interviewer Challenges**
   * **Expect Challenges:**
     * Interviewers will challenge your assumptions and ask probing questions.
   * **Be Prepared to Defend Your Choices:**
     * Explain the reasoning behind your design decisions and address any concerns raised by the interviewer.
   * **Iterate and Refine:**
     * Be willing to adjust your design based on the interviewer's feedback.

**Conclusion**

* Emphasizes the importance of a structured approach to systems design interviews.
* Encourages viewers to practice and refine their problem-solving skills.
* Announces plans for future videos focusing on practice problems.

**Note:**

* This summary provides a high-level overview of the key concepts discussed in the video.
* The video provides more in-depth explanations and uses visual aids to illustrate the concepts.

I hope these notes are helpful!

## EP51: Choosing a Database for Systems Design: All you need to know in one video

**Introduction**

* **Motivation:** Jordan acknowledges viewer requests for a comprehensive database overview and apologizes for the delay.
* **Scope:** This video aims to provide a high-level overview of various database types and their suitability for different use cases.
* **Review of Basics:** Briefly reviews key concepts like indexing (LSM trees, B-trees), replication (single-leader, multi-leader), and data models (relational, document).

**SQL Databases**

* **Characteristics:**
  * Relational data model (tables, rows, columns).
  * ACID properties (Atomicity, Consistency, Isolation, Durability).
  * Typically use B-trees for indexing.
  * Often use two-phase locking for concurrency control.
* **Use Cases:**
  * Applications requiring strong consistency and transactional integrity (e.g., financial systems, banking).
  * Situations where data integrity and correctness are paramount.

**NoSQL Databases**

* **Document Databases (MongoDB):**
  * Stores data in flexible documents (JSON-like).
  * Offers greater flexibility in data modeling.
  * Good for applications with evolving data structures.
  * Uses B-trees for indexing.
* **Wide Column Stores (Cassandra):**
  * Stores data in columns, optimized for efficient reads of specific columns.
  * Supports multi-leader replication for high write throughput.
  * Suitable for high-write applications where eventual consistency is acceptable (e.g., social media feeds).
* **Key-Value Stores (Memcached, Redis):**
  * Simple key-value pairs.
  * In-memory storage for high performance.
  * Suitable for caching, session management, and other applications with high read/write frequency.
* **Graph Databases (Neo4j):**
  * Stores data as nodes and relationships.
  * Optimized for traversing and querying complex relationships.
  * Suitable for social networks, recommendation systems, and knowledge graphs.
* **Time Series Databases:**
  * Optimized for storing and querying time-stamped data (e.g., sensor data, financial data).
  * Often use specialized indexing and compression techniques.

**NewSQL Databases**

* **VoltDB:**
  * In-memory database with single-threaded execution for high performance.
  * Suitable for low-latency, high-throughput applications.
* **Spanner:**
  * Google's globally distributed database with strong consistency guarantees.
  * Utilizes hardware-based time synchronization for accurate timestamps.

**Choosing the Right Database**

* **Consider:**
  * Data volume and velocity.
  * Read/write ratios.
  * Consistency requirements (ACID vs. eventual consistency).
  * Scalability and availability needs.
  * Data model complexity.
  * Performance requirements (latency, throughput).

**Conclusion**

* This video provides a comprehensive overview of various database types and their suitability for different use cases.
* Understanding the strengths and weaknesses of each database is crucial for making informed design decisions.
* Choosing the right database can significantly impact the performance, scalability, and reliability of an application.

**Note:**

* This summary provides a high-level overview of the key concepts discussed in the video.
* The video provides more in-depth explanations and uses visual aids to illustrate the concepts.

I hope these notes are helpful!
