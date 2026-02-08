# Database internals

## <mark style="color:purple;">Chapter 1:</mark> <mark style="color:purple;"></mark><mark style="color:purple;">**Introduction and Overview**</mark>

The first chapter introduces various classifications and architectures of **Database Management Systems (DBMS)**. It provides an overview of different DBMS structures, architectures, and key distinctions to lay the groundwork for further chapters.

**1. Purpose of DBMSs**

DBMSs serve different functions based on their use cases:

* **Temporary hot data**: Frequently accessed data that needs to be stored and retrieved quickly.
* **Cold storage**: Long-term, less frequently accessed data.
* **Analytical queries**: Some DBMSs handle complex analytical queries, often used in decision-making and data analysis.
* **Key-value access**: Simple storage and retrieval mechanisms based on key-value pairs.
* **Time-series data**: Optimized to store and retrieve sequences of data points over time.
* **Efficient blob storage**: Some DBMSs are optimized to store large, unstructured binary data.

**2. Taxonomy and Classifications**

The chapter explores different classifications of DBMS based on functionalities, including:

* **Online Transaction Processing (OLTP)**: Systems that handle high volumes of small, user-facing transactions. These queries are predefined and short-lived.
* **Online Analytical Processing (OLAP)**: Systems that manage complex analytical queries and aggregations, often used for data warehousing.
* **Hybrid Transactional and Analytical Processing (HTAP)**: Combines features of OLTP and OLAP to handle both short and long-lived queries.

Additionally, the book briefly mentions classifications like key-value stores, relational databases, document-oriented stores, and graph databases but avoids defining them as it assumes prior knowledge.

**3. DBMS Architecture**

The book highlights that there is no universal design for DBMS architecture. However, it introduces some common themes in the architecture of database systems:

* **Client-Server Model**: DBMSs usually follow a client-server model where the database instances serve as servers, and applications serve as clients.
* **Components**:
  * **Transport Subsystem**: Manages communication between nodes and clients.
  * **Query Processor**: Responsible for parsing, interpreting, and validating queries.
  * **Query Optimizer**: Ensures queries are executed efficiently using internal statistics.
  * **Execution Engine**: Executes the query as planned by the optimizer, interacting with storage and retrieving the required data.

**4. Storage Types: Memory vs Disk-Based DBMS**

* **Memory-Based DBMS**: These systems store data in memory for faster read/write operations but may not be durable in case of system crashes unless explicitly designed to persist.
* **Disk-Based DBMS**: Data is stored on disk, which provides durability and persistence but may be slower in accessing data compared to memory-based systems.

**5. Durability in Memory-Based Systems**

The challenge of ensuring data durability in memory-based systems is discussed. Various techniques like logging, replication, and snapshotting help these systems persist data in case of failures.

**6. Data Layout Types: Column-Oriented vs Row-Oriented DBMS**

* **Row-Oriented DBMS**: Optimized for transactional systems (OLTP) where frequent row-level operations are required.
*   **Column-Oriented DBMS**: Well-suited for analytical systems (OLAP) where operations on large columns of data are frequent.

    Key differences between row and column-oriented layouts:

    * **Row-oriented**: Stores data tuples together in sequence, beneficial for transactional workloads.
    * **Column-oriented**: Stores each column together, optimizing for read-heavy operations across entire columns.

**7. Other Optimizations:**

* **Wide Column Stores**: Specialized stores that combine features of key-value and column-oriented stores, optimizing storage for very large, sparsely populated tables.

**8. Data Files and Indexing**

The structure and importance of **data files** and **index files** are covered:

* **Data Files**: Store the actual data records.
*   **Index Files**: Facilitate faster access to data through pointers or indexing strategies.

    The concept of **Primary Index as an Indirection** is also introduced, where a primary index points to the location of data in the data files.

**9. Buffering, Immutability, and Ordering**

* **Buffering**: Managing data in memory before writing it to disk or sending it over the network.
* **Immutability**: Data structures that are append-only, allowing for consistent reads without requiring locks.
* **Ordering**: Storing data in a way that ensures efficient retrieval based on certain keys, critical in database systems for efficient queries.

**10. Conclusion of Chapter 1**

The chapter concludes by discussing the core concepts of **buffering**, **immutability**, and **ordering**, which will recur throughout the book as the author delves deeper into DBMS and storage engine design .

***

This is a detailed overview of Chapter 1 from _Database Internals: A Deep Dive into How Distributed Data Systems Work_.



## <mark style="color:yellow;">Chapter 2:</mark> <mark style="color:yellow;"></mark><mark style="color:yellow;">**B-Tree Basics**</mark>

**1. Introduction to Binary Search Trees (BST)**

* A **Binary Search Tree (BST)** is a sorted in-memory data structure designed for efficient key-value lookups. BSTs consist of nodes that hold a key, a value, and two child pointers.
* The nodes are organized so that each key in the left subtree is less than the node's key, and each key in the right subtree is greater .

**2. Tree Balancing**

* Unbalanced trees can lead to inefficiency by resembling a linked list, slowing down search operations to linear time, O(N), instead of the logarithmic complexity (O(log N)) expected in a balanced tree .
* Balanced trees ensure logarithmic lookup time by keeping the height difference between subtrees minimal, reducing search space by half with each comparison .

**3. Limitations of BSTs in Disk-Based Storage**

* Although BSTs work well in-memory, they are not ideal for disk-based storage because they require frequent balancing, relocations, and pointer updates due to their low fanout (low number of child nodes). This can increase disk access and make them inefficient .

**4. Paged Binary Trees**

* BSTs can be modified into **Paged Binary Trees** by grouping nodes into pages. This approach improves locality, reducing the need for multiple disk seeks to find child nodes within the same page .

**5. B-Trees Overview**

* **B-Trees** are widely used in databases to manage large datasets stored on disk. They offer several benefits over BSTs by having a higher fanout and thus smaller height, reducing the number of disk accesses required for lookups.
* They were introduced in 1971 and have become one of the most popular storage structures .

**6. B-Tree Node Structure and Fanout**

* Each B-Tree node can hold multiple keys and pointers. The number of child nodes, known as the **fanout**, helps determine the efficiency of the tree. A higher fanout means fewer levels (or height) and thus fewer disk seeks.
* Nodes are logically divided into **root**, **internal**, and **leaf nodes**. **Root nodes** are at the top of the tree and have no parents, **leaf nodes** store actual data values, and **internal nodes** act as separators guiding searches .

**7. Separator Keys**

* **Separator keys** are used in internal B-Tree nodes to split the tree into subtrees. These keys are stored in sorted order to enable binary search within the tree. Each separator key is associated with a pointer to its respective subtree .

**8. B+-Trees**

* **B+-Trees** are a common variant of B-Trees where actual data values are stored only in the leaf nodes, while internal nodes store only separator keys. This optimizes the retrieval of values during point queries and range scans .

**9. Lookup Algorithm**

* To find a specific key, the search begins at the root and follows pointers down the tree based on the separator keys. The search continues until it reaches a leaf node, where the key is either found or determined to be absent .

**10. Insertions and Node Splitting**

* During an insertion, the key is added to the appropriate leaf node. If the node exceeds its capacity, it is split in two, and the split propagates up to the parent. If necessary, this process continues all the way to the root, potentially increasing the tree’s height .

**11. Merging Nodes During Deletions**

* When a key is deleted, and nodes become underpopulated, they may merge with neighboring nodes to maintain tree balance. This reduces the number of nodes and can also propagate changes upwards to the parent node .

**12. Performance Considerations**

* B-Trees reduce the number of disk seeks by storing multiple keys per node, using binary search to quickly locate a subtree, and maintaining a high fanout.
* This structure balances the need for frequent updates while minimizing performance degradation, even with low occupancy levels .

**13. Summary**

* B-Trees provide a balanced structure suitable for disk-based systems by reducing disk I/O with high fanout and logarithmic complexity for searches.
* Their design supports efficient insertions, deletions, and lookups through node splits and merges, making them an ideal choice for database storage engines .



## <mark style="color:yellow;">Chapter 3:</mark> <mark style="color:yellow;"></mark><mark style="color:yellow;">**File Formats**</mark>

**1. Introduction**

* This chapter explores how databases manage and store data on disk, focusing on **file formats** and **on-disk structures**.
* It highlights how different from memory storage, disk-based systems require efficient formats that are **easy to serialize** and **deserialize** due to access latency constraints .

**2. Binary Encoding**

* Binary encoding helps store data efficiently by converting higher-level abstractions like strings and integers into their raw binary forms.
* **Primitive types** like integers, booleans, and floating-point numbers are used to build more complex structures .
  * For instance, floating-point numbers like `float` and `double` follow the IEEE 754 standard. This standard divides numbers into sign, exponent, and fraction components .
* Endianness (big-endian vs. little-endian) is crucial when encoding multi-byte data types, as it impacts the order in which bytes are stored and retrieved .

**3. Strings and Variable-Size Data**

* Strings and arrays often use **variable-length encodings**. A common format is the **Pascal string**, which stores the length followed by the actual data .
* In systems like SQLite, unoccupied space is managed through free blocks, ensuring efficient use of memory and fast access to variable-size data .

**4. Page Structure and Slotted Pages**

* Pages are fixed-size blocks that store data records. A **page** typically holds **B-tree nodes**, which may include keys, values, and pointers to other nodes.
* **Slotted pages** manage variable-size records and handle free space effectively by splitting pages into segments .
  * This design ensures that adding, deleting, or updating records doesn't require relocating every element in the page, preserving insertion order and enabling quick access .

**5. Managing Variable-Size Data**

* Pages can become **fragmented** when records are deleted or updated, causing gaps in storage.
* Techniques like **first fit** or **best fit** are used to insert new records into free segments, optimizing the use of available space .

**6. Versioning**

* Database file formats evolve, and versioning allows the system to support legacy and current file formats.
* This can be implemented through version-specific headers, file naming conventions, or separate versioning files .

**7. Checksumming**

* To detect **data corruption**, systems use **checksums** and **cyclic redundancy checks (CRCs)**. These mechanisms ensure that changes to files (e.g., due to hardware failure) can be detected before propagating bad data across subsystems .

**8. Summary**

* Chapter 3 covers the design principles for on-disk data storage formats, particularly how to efficiently store and manage variable-size records using slotted pages.
* It emphasizes the importance of **binary encoding**, **checksumming**, and **versioning** in ensuring that data can be reliably stored, accessed, and maintained in disk-based systems .

These notes encapsulate the key themes and technical details from Chapter 3 of _Database Internals: A Deep Dive into How Distributed Data Systems Work_.



## <mark style="color:yellow;">Chapter 4:</mark> <mark style="color:yellow;"></mark><mark style="color:yellow;">**Implementing B-Trees**</mark>

**1. Page Header**

* The **page header** stores essential metadata that is used for navigation and managing optimizations. It typically contains:
  * Flags describing page contents and layout.
  * The number of cells in the page.
  * Offsets marking free space and other metadata.
  * Examples include the storage of page sizes in **PostgreSQL**, heap record counts in **MySQL InnoDB**, and cell counts with pointers in **SQLite**.

**2. Magic Numbers**

* **Magic numbers** are multibyte constants placed in the page or file headers to identify the page’s format or version.
* These numbers help perform sanity checks and ensure that the data is correctly loaded and aligned on disk. If the magic number doesn't match, an error can be flagged.

**3. Sibling Links**

* Sibling links point to the left and right sibling pages, which allows fast traversal of neighboring nodes without needing to ascend to the parent node.
* Although useful, they introduce complexity in split and merge operations because sibling pointers must be updated. This additional operation can add locking overhead.

**4. Rightmost Pointers and High Keys**

* **Rightmost pointers**: Each B-Tree node contains an additional pointer that points to the rightmost child, which is essential for maintaining tree balance during splits.
* **Node High Keys**: These are stored alongside pointers in each node and represent the highest key value that can reside in a subtree. This method is used by **PostgreSQL**.

**5. Binary Search with Indirection**

* A **binary search** is employed within each node to find the correct subtree by comparing keys.
* **Indirection pointers** are sometimes used to manage large data structures, storing references instead of directly storing the data.

**6. Propagating Splits and Merges**

* When a node is split, **breadcrumbs** are collected during tree traversal. These allow the system to backtrack to the parent nodes to propagate changes.
* These breadcrumbs are used to propagate structural changes (such as inserts, splits, and merges) back up the tree. Many databases, such as **PostgreSQL**, store these breadcrumbs in a stack.

**7. Rebalancing**

* Instead of splitting a node when it becomes full, **rebalancing** transfers some elements to adjacent sibling nodes, improving space utilization.
* _B-Trees_\* are a variant where two nodes are split into three when both sibling nodes are full, increasing the average occupancy.

**8. Right-Only Appends**

* Some systems avoid rebalancing by appending data only to the rightmost node, which reduces the frequency of node splits.
* **SQLite** employs this technique, where the rightmost page is filled until it requires splitting, allowing more frequent right appends.

**9. Bulk Loading**

* In cases where data is presorted, **bulk loading** avoids many of the costly split operations by inserting data directly into leaf pages and propagating keys upwards.
* **Immutable B-Trees** created in this way do not require space for future updates, leading to improved space utilization and performance.

**10. Compression**

* **Compression** techniques can reduce the size of the stored data, improving performance by reducing the amount of I/O required.
* Compression can be applied at different levels, including row-wise and page-wise, with different algorithms (e.g., **Snappy**, **zLib**) depending on the data characteristics and system requirements.
* Page-wise compression allows for independent compression and decompression of each page, reducing I/O overhead while preserving retrieval speed.

**11. Vacuum and Maintenance**

* Regular maintenance is essential to prevent **fragmentation**, which occurs due to frequent updates and deletes.
* **Page defragmentation** is required to consolidate scattered data and improve access speed by reducing page-level fragmentation.

**12. Summary**

* This chapter provides detailed insights into B-Tree implementations, including the management of node splits, merges, rebalancing, and compression techniques.
* Key optimization techniques like bulk loading, rebalancing, and sibling pointers help maintain the efficiency and space utilization of B-Trees, ensuring performance in both read-heavy and write-heavy workloads【16:2†source】【16:12†source】【16:18†source】.



## <mark style="color:yellow;">Chapter 5:</mark> <mark style="color:yellow;"></mark><mark style="color:yellow;">**Transaction Processing and Recovery**</mark>

**1. ACID Properties of Transactions**

* **Atomicity**: Transactions must be indivisible; either all operations are successfully executed, or none are.
* **Consistency**: A transaction should bring the database from one valid state to another, maintaining data integrity.
* **Isolation**: Transactions should not interfere with each other, and changes made by one transaction should not be visible to others until committed.
* **Durability**: Once a transaction is committed, its changes must be saved to disk and survive system failures .

**2. Buffer Management**

* Databases use a two-level memory hierarchy: slow persistent storage (disk) and faster main memory (RAM).
* **Page Cache**: Cached copies of data pages are stored in memory to reduce disk access. When a transaction modifies data, changes are first made in the cache before being flushed to disk.
* **Cache Eviction**: When the cache reaches its capacity, a page-replacement policy (e.g., Least Recently Used, LRU) decides which cached data should be evicted and written to disk.
* **Write-Ahead Log (WAL)**: A log is maintained to record changes made to pages in memory before they are written to disk. This ensures durability and allows the database to recover from crashes .

**3. Steal and Force Policies**

* **Steal Policy**: Allows modified pages to be flushed to disk even before the transaction commits. This improves performance but increases the complexity of recovery, as uncommitted changes must be undone in case of rollback.
* **Force Policy**: Requires all modified pages to be flushed to disk before a transaction commits. This ensures simpler recovery but may lead to performance bottlenecks due to increased I/O operations .

**4. ARIES Recovery Algorithm**

* **ARIES (Algorithm for Recovery and Isolation Exploiting Semantics)**: A widely used recovery algorithm that supports high concurrency and ensures durability through WAL.
  * **Analysis Phase**: Identifies in-progress transactions and dirty pages at the time of a crash.
  * **Redo Phase**: Replays operations from the WAL to bring the database to its state before the crash.
  * **Undo Phase**: Rolls back incomplete transactions to restore a consistent database state .

**5. Concurrency Control**

* **Optimistic Concurrency Control (OCC)**: Allows transactions to execute without locks and checks for conflicts at commit time. If a conflict is found, one transaction is aborted.
* **Multiversion Concurrency Control (MVCC)**: Maintains multiple versions of data to allow concurrent read and write operations without conflicts. Each transaction operates on a consistent snapshot of the data.
* **Pessimistic Concurrency Control (PCC)**: Uses locks to prevent transactions from modifying the same data simultaneously. Locks are acquired and held until the transaction completes .

**6. Transaction Isolation Levels**

* **Read Uncommitted**: Transactions can see uncommitted changes made by other transactions, leading to dirty reads.
* **Read Committed**: A transaction can only see committed changes, avoiding dirty reads.
* **Repeatable Read**: Ensures that if a transaction reads data twice, it sees the same value both times, preventing non-repeatable reads.
* **Serializable**: The strictest level of isolation, ensuring that transactions execute in a way that their results are equivalent to some serial execution .

**7. Log-Based Recovery**

* **Write-Ahead Log (WAL)** ensures that all modifications are logged before being applied to the database.
* During recovery, the system replays the WAL to recover uncommitted transactions and restore the database to a consistent state.
* **Physical Redo and Logical Undo**: ARIES uses physical redo (reapplying changes as they were logged) and logical undo (reverting actions at a higher level) to handle both committed and uncommitted transactions .

**8. Summary**

* The chapter provides an overview of how modern databases handle transaction processing and recovery. It emphasizes the importance of ACID properties, concurrency control mechanisms, and the ARIES recovery algorithm. Buffer management, caching, and WAL are critical for ensuring data durability and recovery in case of system failures .



## <mark style="color:yellow;">Chapter 6:</mark> <mark style="color:yellow;"></mark><mark style="color:yellow;">**B-Tree Variants**</mark>

**1. Introduction to B-Tree Variants**

* B-Trees, while highly efficient, have certain drawbacks like high write amplification and space overhead. Several variants have been developed to address these issues, particularly when used with modern storage media such as SSDs.

**2. Copy-on-Write B-Trees**

* **Copy-on-Write** (CoW) B-Trees avoid in-place updates by copying the node to a new location before modifying it.
* Advantages:
  * CoW ensures immutability of old nodes, which allows readers to access old versions of the tree without locks.
  * Writers modify copied nodes without interrupting concurrent read operations.
  * CoW guarantees data integrity as changes are only committed once the entire operation is complete.
* Example: The **LMDB** storage engine uses CoW, allowing it to avoid complex caching and logging mechanisms【16:9†source】 .

**3. Lazy B-Trees**

* **Lazy B-Trees** reduce the number of I/O operations by buffering updates in-memory. Instead of directly writing to disk after each operation, updates are accumulated and propagated in bulk to disk.
* Benefits:
  * This approach minimizes the number of writes by batching updates, which reduces disk activity and improves performance.
  * Buffered updates to individual nodes are written later, which also decreases write amplification .

**4. FD-Trees**

* The **Flash Disk Tree (FD-Tree)** is designed to minimize random writes, which can be costly on HDDs and SSDs. It uses a combination of a small mutable head tree and larger immutable sorted runs.
* When the head tree becomes full, its contents are moved to an immutable run.
* This structure limits random write operations, as most data handling is done through appends and merging .

**5. Bw-Trees**

* **Buzzword-Trees (Bw-Trees)** solve the problems of write and space amplification by separating base nodes from modifications. Instead of modifying nodes directly, updates are stored as **delta nodes**, which form a chain leading back to the base node.
* These nodes are addressed using a mapping table, and concurrency is handled through **compare-and-swap** operations. This allows for lock-free concurrent access.
* **Delta nodes** contain inserts, updates, and deletes, which reduces the need for rewriting full nodes .

**6. Cache-Oblivious B-Trees**

* **Cache-Oblivious B-Trees** are designed to perform optimally without knowing the sizes of the cache lines, filesystem blocks, or disk pages. This allows them to function efficiently across multiple storage hierarchies without platform-specific tuning.
* These trees use a **van Emde Boas layout**, where nodes are recursively split and laid out in memory in such a way that maintains locality across different levels of the memory hierarchy .

**7. Key Concepts**

* **Update Chains**: In Bw-Trees, delta nodes represent updates or inserts and are linked to the original base node in a chain. These chains are traversed during reads to reconstruct the node’s state .
* **Fractional Cascading**: This technique, used in FD-Trees, propagates references to elements from lower-level trees to higher levels. It reduces search costs when moving between levels of sorted runs .

**8. Summary of B-Tree Variants**

* **Copy-on-Write B-Trees** offer a way to handle concurrent operations without locks by maintaining immutable copies of old nodes.
* **Lazy B-Trees** and **FD-Trees** focus on reducing write amplification and random writes by batching updates and using immutable storage for older data.
* **Bw-Trees** further optimize updates by using delta nodes and in-memory mappings, avoiding in-place modifications and locks.
* **Cache-Oblivious B-Trees** aim for efficient memory usage across storage hierarchies without requiring specific configurations .

This chapter explores the evolution and optimizations of B-Trees, each designed to address specific storage and performance challenges.



## <mark style="color:yellow;">Chapter 7:</mark> <mark style="color:yellow;"></mark><mark style="color:yellow;">**Log-Structured Storage**</mark>

**1. Introduction to Log-Structured Storage**

* Log-structured storage is an approach where modifications to data are written sequentially to a log rather than modifying existing records. This allows for fast, sequential writes but requires mechanisms to handle old or redundant data.
* Log-structured Merge Trees (LSM Trees) are a popular example of this technique, used in systems such as LevelDB and Cassandra.

**2. LSM Tree Structure**

* **LSM Trees** consist of two components: memory-resident components (memtables) and immutable, disk-resident files called Sorted String Tables (SSTables).
* **Memtables** store data in memory until they reach a certain size, at which point they are flushed to disk as an SSTable.
* SSTables are read-only, which makes them ideal for sequential writes, but multiple SSTables may need to be merged during reads to reconcile data .

**3. Writes and Updates**

* In LSM Trees, writes are batched in memory, and when the memtable is full, it is flushed to disk as a new SSTable.
* Updates and deletions are handled by appending new records to the memtable, and older versions of data are reconciled during reads or during periodic compaction.
* This reduces random disk I/O but increases **write amplification**, where multiple writes occur as data moves through different levels of SSTables .

**4. Compaction and Garbage Collection**

* **Compaction** merges multiple SSTables into one, removing obsolete records and ensuring that the most recent version of each key is stored.
* During compaction, live data is preserved, and shadowed or deleted records are discarded.
* Garbage collection is a crucial part of maintaining performance, as it reclaims space taken up by obsolete records and reduces the number of SSTables .

**5. Read, Write, and Space Amplification**

* Log-structured storage is characterized by a trade-off between **read, write, and space amplification**, often referred to as the **RUM conjecture**.
  * **Read amplification** occurs because multiple SSTables need to be scanned to retrieve the latest version of a record.
  * **Write amplification** occurs due to repeated writes to different SSTable levels during compaction.
  * **Space amplification** results from storing multiple versions of data until compaction occurs .

**6. Concurrency in LSM Trees**

* **LSM Trees** allow for concurrent reads and writes without locking because writes are always append-only, and reads only require traversing the latest SSTables.
* As a result, LSM Trees have low read latency and high write throughput, making them suitable for workloads with high write operations .

**7. Unordered LSM Storage**

* Some LSM-based systems, such as **Bitcask**, use unordered storage. In these systems, data is appended without ordering, and lookups rely on in-memory indexes.
* These systems offer fast point lookups but do not support range queries due to the unordered nature of the data on disk .

**8. WiscKey**

* **WiscKey** is an enhancement over traditional LSM Trees, where keys are stored in an ordered LSM structure, and values are stored in separate, unordered logs (vLogs).
* This separation allows WiscKey to reduce write amplification by keeping values out of the SSTables and only compacting the keys, making it more efficient for large datasets .

**9. Applications of Log-Structured Storage**

* Log-structured storage is used in modern systems like SSDs and file systems to reduce random write costs and extend the lifetime of storage devices.
* By grouping small random writes into larger sequential writes, LSS systems reduce the need for frequent erasures on SSDs and improve overall performance .

**10. Conclusion**

* Log-structured storage, particularly through LSM Trees, is ideal for write-heavy workloads due to its ability to batch writes and avoid random disk I/O.
* However, this comes at the cost of higher read amplification, requiring systems to optimize their data access patterns and compaction processes .



## Chapter 8 introduction to distributed systems&#x20;

#### 1. **Concurrent Execution**:

* Distributed systems involve processes running concurrently on different nodes. Managing these processes can be tricky since they execute simultaneously, potentially affecting the same data at different times.
* Concurrency models are essential for organizing how multiple processes access shared resources in distributed databases. This helps ensure consistency and correctness.

#### 2. **Shared State in a Distributed System**:

* A key challenge in distributed systems is maintaining a consistent shared state across multiple nodes.
* Each node has its own local state, and the global state is often derived by combining the local states.
* Managing this shared state is complex because of the need for synchronization between distributed nodes.

#### 3. **Fallacies of Distributed Computing**:

* There are common misconceptions or "fallacies" regarding distributed systems, including:
  * The network is reliable.
  * Latency is zero.
  * Bandwidth is infinite.
  * The network is secure.
  * Topology doesn’t change.
  * There’s one administrator.
  * Transport cost is zero.
  * The network is homogeneous.
* Recognizing and addressing these fallacies is crucial when designing reliable distributed systems.

#### 4. **Processing and Clocks**:

* Processing time and clock synchronization are vital for distributed systems. Time, however, is not synchronized across nodes in real-world systems, leading to potential issues like inconsistent states and incorrect data replication.

#### 5. **State Consistency**:

* Achieving a consistent state across distributed nodes can be challenging because nodes might process information at different times or experience network delays. Maintaining state consistency is crucial for the system's reliability.

#### 6. **Local and Remote Execution**:

* Nodes can execute processes locally or send processes to other nodes (remote execution). Ensuring that processes run correctly across distributed systems requires careful coordination.

#### 7. **Need to Handle Failures**:

* Failures, such as node crashes or network partitioning, are inevitable in distributed systems. These failures must be detected and handled to ensure that the system continues to function properly.
* Redundancy and fault tolerance mechanisms are commonly used to mitigate the effects of failures.

#### 8. **Network Partitions and Partial Failures**:

* Network partitioning occurs when a network is split into disjoint segments. In such cases, some nodes may be unable to communicate with others, but they still function. This causes partial failures in the system.
* Handling such failures requires strategies like consensus algorithms and partition-tolerant architectures.

#### 9. **Cascading Failures**:

* A cascading failure happens when a single point of failure causes a chain reaction, leading to the failure of other system components. This is a critical issue that needs to be prevented in large distributed systems.

#### 10. **Distributed Systems Abstractions**:

* Links: Distributed systems rely on various types of links between nodes, including reliable, unreliable, and ordered links.
* Proper abstractions of links and other system components help developers build more reliable distributed systems.

***

These points cover the key concepts from Chapter 8 that are essential for understanding distributed systems, their execution, and the challenges they present. This should serve as a solid guide for your exam preparation.



## Chapter 9 **Failure Detection**

#### 1. **Introduction to Failure Detection**:

* Failure detection is crucial for distributed systems to maintain high availability and low latency.
* Detecting failures is difficult due to the asynchronous nature of distributed systems, where slow processes may be incorrectly assumed to have failed.

#### 2. **Failure Models**:

* **Crash Faults**: The simplest model where a process stops all actions and cannot recover until restarted.
* **Omission Faults**: Processes omit some actions or cannot communicate with others due to network failures.
* **Arbitrary (Byzantine) Faults**: More complex; the process continues but behaves incorrectly, potentially sending faulty data.

#### 3. **Heartbeats and Pings**:

* Two common methods for failure detection are used:
  1. **Ping**: Actively querying processes to see if they are alive by expecting a timely response.
  2. **Heartbeats**: The process itself sends periodic signals to confirm it's still alive.
* If the pings or heartbeats are delayed beyond a set timeout, the system flags the process as failed.
* These methods, while effective, are sensitive to network latency and must be carefully tuned.

#### 4. **Timeout-Free Failure Detection**:

* Some algorithms avoid the use of strict timeouts.
* For instance, **timeout-free failure detectors** rely on counting heartbeats instead of waiting for a specific time limit to expire.

#### 5. **Outsourced Heartbeats**:

* In some systems like **SWIM (Scalable Weakly Consistent Infection-Style Membership Protocol)**, responsibility for detecting failure is distributed among neighbors.
* Instead of a single node being responsible, several other nodes attempt to reach the suspect node and report back.
* This enhances reliability by cross-verifying failure reports.

#### 6. **Gossip and Failure Detection**:

* Gossip-based failure detection works by randomly spreading state information between nodes.
* Each node maintains a list of other members and their **heartbeat counters**, which are incremented periodically and shared with neighbors.
* If a node stops incrementing its heartbeat, others will eventually consider it failed after a set timeout period.

#### 7. **Phi-Accrual Failure Detector**:

* This type of detector avoids binary "alive or dead" status and instead works on a sliding scale, calculating the probability that a node has failed.
* It dynamically adjusts based on network conditions, providing a more flexible way of handling failures in uncertain environments.
* **Cassandra** and **Akka** are examples of systems using this detection method.

#### 8. **Reversing Failure Detection (FUSE)**:

* FUSE (Failure Notification Service) offers a way to detect and propagate failures in systems where complete failure detection may not be feasible.
* In this model, failures detected in a group are converted into "group failures," where each member stops responding once a failure is detected.
* It simplifies failure detection but may incorrectly propagate failure due to single process isolation.

#### 9. **Trade-offs in Failure Detection**:

* There’s always a trade-off between **accuracy** (correctly identifying failure) and **efficiency** (how quickly it identifies failures).
* An algorithm that prioritizes efficiency may generate false positives (incorrectly identifying a process as failed), while one that focuses on accuracy might delay failure detection.

#### 10. **Summary**:

* Failure detectors, such as **pings**, **heartbeats**, **timeout-free** methods, **gossip-based**, and **phi-accrual** systems, each have their pros and cons.
* The choice of detector depends on the system’s requirements for reliability, speed, and fault tolerance.

These concepts are essential for designing distributed systems that can handle failures gracefully while maintaining high availability.

For further reading on failure detection algorithms, refer to works by Tushar Deepak Chandra and Sam Toueg (1996), and Felix C. Freiling, Rachid Guerraoui, and Petr Kuznetsov (2011) .



## Chapter 10 **Leader Election** in distributed systems

#### 1. **Introduction to Leader Election**:

* **Leader election** is a key concept in distributed systems, used to ensure that one process is designated as the leader to coordinate tasks.
* The leader handles responsibilities like maintaining consistency, managing communication, and resolving conflicts.

#### 2. **Bully Algorithm**:

* **Bully Algorithm** is one of the simplest algorithms for leader election. In this approach:
  * Processes have unique ranks.
  * The highest-ranked process that notices the absence of a leader initiates the election.
  * It sends messages to higher-ranked processes and waits for a response.
  * If no response is received, it assumes leadership.
* **Problems**: It is prone to **split-brain** situations where different parts of the system independently elect leaders, leading to conflicting operations. It also has issues with unstable high-ranked nodes repeatedly failing and getting re-elected.

#### 3. **Next-In-Line Failover**:

* This approach is an optimization of the Bully Algorithm.
* Leaders maintain a list of alternative leaders. If the current leader fails, the highest-ranked alternative from the list becomes the new leader without starting a complete re-election process.
* This reduces the number of steps involved in the leader election.

#### 4. **Candidate/Ordinary Optimization**:

* This optimization divides processes into two categories: **candidates** and **ordinary processes**.
* Only candidate nodes can become leaders.
* An ordinary node starts an election by communicating with candidates and picking the highest-ranked candidate.
* To avoid simultaneous elections, the algorithm uses a random delay (tiebreaker).

#### 5. **Invitation Algorithm**:

* Instead of competing, processes invite others to join groups.
* Each process starts as a leader of a single-member group.
* Leaders merge their groups with others by inviting peers. The leader of the larger group remains the leader after merging.
* This algorithm allows for multiple leaders, but eventually, groups merge, reducing the number of leaders.

#### 6. **Ring Algorithm**:

* In the **Ring Algorithm**, nodes are arranged in a logical ring.
* When a node detects a leader failure, it starts an election by passing a message around the ring, collecting the IDs of live nodes.
* The node with the highest ID becomes the new leader.
* This method is vulnerable to network partitioning, which can lead to multiple leaders in different partitions.

#### 7. **Split-Brain Problem**:

* A common challenge in leader election algorithms is the **split-brain problem**, where network partitions can result in multiple leaders being elected in isolated segments of the system.
* This can lead to conflicting actions by different leaders.

#### 8. **Consensus Algorithms and Leader Election**:

* Many consensus algorithms, like **Multi-Paxos** and **Raft**, rely on leader election for coordination.
* These algorithms ensure that all nodes agree on a single leader to maintain system consistency.

#### 9. **Stable Leader Election**:

* Stable leader election algorithms combine leader election with failure detection to ensure that the leader retains its position as long as it doesn’t crash or become unreachable.
* These algorithms aim to reduce the cost of leader reelection by ensuring stability in the leadership role.

#### 10. **Summary**:

* Leader election is crucial in distributed systems to avoid excessive coordination overhead.
* Algorithms like **Bully**, **Ring**, and **Next-in-Line Failover** provide different methods to elect leaders efficiently.
* The challenge of avoiding multiple leaders (split-brain) is addressed by requiring a majority vote or consensus, as seen in algorithms like **Raft**.

These concepts provide a solid foundation for understanding how leader election works in distributed systems and the trade-offs involved in different algorithms .



## Chapter 11: **Replication and Consistency**

**1. Introduction to Replication:**

* **Fault tolerance** is a key property of distributed systems, ensuring correct operations even with component failures.
* Replication involves maintaining multiple copies of data to provide redundancy. This helps systems remain functional if one machine fails, with replicas providing failover.
* Two replication approaches are:
  * **Primary/Replica Databases**: Explicit failover to a replica, which is promoted to master after the primary fails.
  * **Multi-replica systems**: Read and write queries are processed by multiple participants to ensure consistency.

**2. Challenges with Replication:**

* **Atomic updates to multiple copies** are costly and involve consensus. It is critical to balance **cost-efficiency** and **flexibility** while making data appear consistent to users.
* Replication is essential in **multi-datacenter deployments**, aiding with availability, fault tolerance, and latency reduction by placing copies closer to clients.

**3. Achieving Availability:**

* Availability ensures that a system continues to function despite failures, minimizing downtime and service disruption.
* **Redundancy** and **replication** are core strategies to maintain system availability, even when parts of the system fail.

**4. Infamous CAP Theorem:**

* The **CAP theorem** suggests that a distributed system can only guarantee two out of three properties:
  * **Consistency**: All nodes see the same data at the same time.
  * **Availability**: The system always processes requests, even during failures.
  * **Partition Tolerance**: The system continues operating despite network partitions.
* Practical systems must balance these properties, with trade-offs between **strong consistency** and **availability** under partition tolerance.

**5. Consistency Models:**

* Different consistency models explain how a distributed system handles data visibility and operation order in the presence of multiple copies:
  1. **Strict Consistency**: Writes are instantly visible across all nodes—impractical in real systems due to network delays.
  2. **Linearizability**: Operations appear instantaneous, maintaining real-time order. It’s the strongest single-operation consistency model.
  3. **Sequential Consistency**: Operations are applied in the same order but not necessarily in real-time.
  4. **Causal Consistency**: Causally related operations are applied in the same order across nodes, but independent operations may vary in their order.
  5. **PRAM/FIFO Consistency**: Writes from the same process are seen in the order they occurred, but writes from different processes may be reordered.

**6. Session Models:**

* These models focus on the client perspective, ensuring consistency during a session:
  * **Read-own-writes**: A client sees its own previous writes.
  * **Monotonic Reads**: A client cannot see older data after viewing newer data.
  * **Monotonic Writes**: Writes from a client are applied in the order they were made.
  * **Writes-follow-reads**: Writes are ordered after the reads that observed earlier values.

**7. Eventual Consistency:**

* Eventual consistency ensures that, in the absence of further updates, all replicas will eventually converge to the same state. However, during updates, replicas can temporarily diverge.
* **Tunable Consistency**: Distributed systems allow tuning for consistency, where factors like **read/write quorum** (the number of nodes required to acknowledge an operation) can be adjusted to balance between performance and data accuracy.

**8. Conflict-Free Replicated Data Types (CRDTs):**

* **CRDTs** allow operations to be performed independently on replicas and later merged, without conflict, to reach a consistent state. These are useful in eventually consistent systems where synchronization is expensive.
* **Examples of CRDTs** include **counters**, **grow-only sets**, and complex data structures like **Conflict-Free Replicated JSON (CRJ)**, which allows independent modifications to nested data structures.

**9. Witness Replicas:**

* These replicas store only metadata, rather than full data copies, to reduce storage costs while maintaining redundancy for quorum-based operations.

**10. Conclusion:**

* Replication and consistency are central to building fault-tolerant systems that maintain availability despite failures. Balancing **strong consistency**, **availability**, and **performance** is critical in distributed system design.

These detailed points provide a comprehensive understanding of replication and consistency in distributed databases, emphasizing the trade-offs involved in system design.



## Chapter 12: **Anti-Entropy and Dissemination**

**1. Introduction to Anti-Entropy**

* **Anti-entropy** refers to mechanisms used in distributed systems to synchronize replicas and reduce inconsistency (entropy).
* Distributed systems inherently deal with inconsistency due to the asynchronous nature of communications and potential network failures.
* Entropy in a system is the measure of **disorder** (i.e., the divergence between nodes), and anti-entropy techniques focus on minimizing this disorder by bringing nodes back into sync.

**2. Communication Patterns**

* There are three main types of communication patterns used for disseminating updates:
  1. **Broadcast**: A single process sends a message to all other nodes.
  2. **Peer-to-Peer Information Exchange**: Nodes exchange updates directly with one another periodically.
  3. **Cooperative Broadcast**: Message recipients help propagate information, speeding up the dissemination process.
* Each of these approaches has its strengths and weaknesses, with **broadcasting** being simple but expensive in large clusters, while **peer-to-peer** and **cooperative broadcast** scale better.

**3. Read Repair**

* **Read Repair** is one of the anti-entropy mechanisms that works during read operations. When a client queries data, the system checks if replicas are in sync.
* If inconsistencies are found during a read operation, the **coordinator** node sends updates to lagging replicas to repair their state.
* **Blocking Read Repair**: Forces the client to wait until the replicas are updated.
* **Asynchronous Read Repair**: Repairs inconsistencies in the background after serving the client request.

**4. Merkle Trees**

* **Merkle Trees** are used to detect and fix inconsistencies in data stored across nodes. They allow for efficient comparison of large datasets by comparing hash trees instead of individual records.
* **How it Works**: Nodes exchange hash trees. If any differences are detected, they recursively narrow down to the mismatched sections and repair only the differing data.
* This technique is ideal for handling large datasets, minimizing the data exchanged while ensuring consistency.

**5. Hinted Handoff**

* **Hinted Handoff** is another method used to handle temporary node failures. If a node is down, a neighbor temporarily stores the writes intended for the failed node. When the failed node recovers, the stored updates are forwarded.
* This technique helps ensure availability even when some nodes are temporarily unavailable.

**6. Bitmap Version Vectors**

* **Bitmap Version Vectors** allow tracking which writes have been seen by each node. When a node detects that another is missing some updates, it can identify and send the missing updates precisely, without redundant operations.
* These vectors are used to maintain a compact history of the writes seen by each node, making it easy to identify and resolve missing data.

**7. Gossip Dissemination**

* **Gossip Protocols** are based on the idea of spreading updates similarly to how rumors or diseases spread.
* Each node randomly selects a few peers and exchanges the most recent updates. This process repeats until all nodes are synchronized.
* **Advantages**: Gossip is scalable and resilient to node failures, as updates will still spread even if some nodes are temporarily unreachable.
* **Gossip Mechanics**: Processes periodically select random peers to exchange information. This introduces redundancy, but also ensures updates eventually reach all nodes.

**8. Hybrid Gossip and Partial Views**

* **Hybrid Gossip Protocols** combine **gossip-based** and **tree-based** techniques to optimize for both scalability and reliability. They create a **spanning tree** to minimize the overhead, while falling back to gossip if parts of the tree fail.
* **Partial Views**: To reduce the overhead of maintaining a global view of the entire cluster, nodes maintain a **partial view** (a subset of the entire cluster). These partial views are periodically updated to ensure freshness while reducing costs.

**9. Summary**

* Anti-entropy mechanisms such as **read repair**, **Merkle trees**, and **gossip protocols** play a critical role in ensuring data consistency in distributed systems.
* These techniques aim to reduce the overhead of full dataset comparisons while maintaining high availability and reliability.
* **Hybrid protocols** offer flexible solutions for large-scale systems, balancing the need for consistency with the cost of communication.

This chapter provides a comprehensive overview of how distributed systems use anti-entropy mechanisms to maintain consistency and ensure efficient communication across nodes .



#### Chapter 13: **Distributed Transactions**

**1. Introduction to Distributed Transactions:**

* **Distributed transactions** involve executing multiple operations atomically across multiple nodes in a distributed database.
* Transactions are described by state transitions from state A to state B, ensuring atomicity and consistency.
* The main challenge is coordinating multiple servers to execute a transaction and ensure consistency across partitions.

**2. Atomicity and Rollbacks:**

* Transactions are atomic, meaning all operations either complete successfully, or none are applied (rollback occurs).
* This is crucial to avoid leaving the database in an inconsistent state in case of a failure.
* Network partitions and node failures introduce additional challenges for maintaining consistent atomic operations across nodes .

**3. Two-Phase Commit (2PC):**

* 2PC is one of the most common protocols used for **atomic commitment**:
  1. **Phase 1 - Prepare**: The coordinator sends the transaction proposal to all nodes (cohorts), collecting votes on whether to commit.
  2. **Phase 2 - Commit/Abort**: If all votes are positive, the coordinator commits the transaction; otherwise, it sends an abort message.
* In 2PC, failures of a cohort or the coordinator can block the entire transaction process. The coordinator needs to persist a decision log to ensure failed nodes can learn the final outcome upon recovery .

**4. Failures in 2PC:**

* **Cohort Failures**: If a cohort fails during the prepare phase, the coordinator must abort the transaction.
* **Coordinator Failures**: If the coordinator fails before completing the commit phase, cohorts may remain in an uncertain state, blocking the transaction.
* 2PC is considered a **blocking protocol** because failures can cause the system to wait indefinitely until nodes recover .

**5. Three-Phase Commit (3PC):**

* 3PC is an enhancement of 2PC to handle coordinator failures more gracefully. It adds an additional **Prepare** phase:
  1. **Propose**: The coordinator sends the transaction proposal.
  2. **Prepare**: The coordinator notifies cohorts of the vote results (commit or abort).
  3. **Commit**: Cohorts execute the commit or abort decision.
* 3PC includes timeouts and additional state transitions to ensure progress even if the coordinator fails. However, it introduces higher message overhead .

**6. Distributed Transactions with Calvin:**

* **Calvin** is a distributed transaction protocol that uses deterministic execution to reduce coordination costs.
* It introduces a **sequencer** that determines the global transaction order. By deciding transaction boundaries before execution, Calvin can parallelize transactions without conflicts.
* Calvin eliminates the need for two-phase locking by agreeing on execution order upfront, minimizing contention and improving throughput .

**7. Distributed Transactions with Spanner:**

* **Spanner**, another distributed transaction system, uses **TrueTime**, a high-precision clock API to enforce global consistency.
* Spanner relies on **Paxos groups** for consistent replication of transaction logs. It also uses **two-phase commit** across partitions for transactional consistency, allowing single-shard transactions to avoid cross-partition coordination.
* Spanner provides **external consistency**, ensuring that the order of transactions matches real-time order, similar to **linearizability** .

**8. Database Partitioning:**

* Distributed transactions often involve **partitioning** data into smaller, manageable pieces.
* Partitions can be split based on load and distribution, allowing better management of high-traffic areas.
* Systems like **Spanner** and **Calvin** use partitioning schemes to manage transactions across large-scale databases efficiently .

**9. Coordination Avoidance:**

* To reduce the cost of maintaining strong consistency, **coordination avoidance** techniques like **Invariant Confluence** can be used.
* **RAMP (Read-Atomic Multi-Partition)** transactions allow read and write operations to proceed concurrently across partitions without stalling, by fetching missing state information when necessary .

***

These detailed points summarize the key concepts of distributed transactions, focusing on 2PC and 3PC protocols, Calvin and Spanner systems, and techniques to minimize coordination overhead for better performance in distributed environments.

