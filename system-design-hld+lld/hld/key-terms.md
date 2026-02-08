# Key terms

## IP

* IPV4
* IPV6

## Consistency, availability and scalabiltiy

* Scalability
  * Scalability is the measure of how well a system responds to changes by adding or removing resources to meet demands.
  * types
    * horizontal
      *   #### Advantages



          * Increased redundancy
          * Better fault tolerance
          * Flexible and efficient
          * Easier to upgrade


      * Disadvantages
        * Increased complexity
        * Data inconsistency
        * Increased load on downstream services
    * vertical
      *   #### Advantages

          * Simple to implement
          * Easier to manage
          * Data consistent


      * Disadvantages
        * Risk of high downtime
        * Harder to upgrade
        * Can be a single point of failure

## CAP

* Consistency
  * Every read receives the most recent write or an error
  * Consistency patterns&#x20;
    * Weak
      *   After a write, reads may or may not see it. A best effort approach is taken.

          This approach is seen in systems such as memcached.&#x20;
      * Weak consistency works well in real time use cases such as VoIP, video chat, and realtime multiplayer games.&#x20;
      * For example, if you are on a phone call and lose reception for a few seconds, when you regain connection you do not hear what was spoken during connection loss.
    * Eventual
      * After a write, reads will eventually see it (typically within milliseconds). Data is replicated asynchronously.
      *   This approach is seen in systems such as DNS and email. Eventual consistency works well in highly available systems.


    * Strong
      *   After a write, reads will see it. Data is replicated synchronously.

          This approach is seen in file systems and RDBMSes.&#x20;
      * Strong consistency works well in systems that need transactions.
* Availability
  * Every request receives a response, without guarantee that it contains the most recent version of the information
  * Availability patterns
    * Fail over
      * Active-passive
        *   With active-passive fail-over, heartbeats are sent between the active and the passive server on standby. If the heartbeat is interrupted, the passive server takes over the active's IP address and resumes service.


      * Active-active
        * active-active, both servers are managing traffic, spreading the load between them.
        * If the servers are public-facing, the DNS would need to know about the public IPs of both servers. If the servers are internal-facing, application logic would need to know about both servers.
* partition tolerance
  * The system continues to operate despite arbitrary partitioning due to network failures
  * One can have any two of the three
    * CP (**consistency and partition tolerance)**
      *   Waiting for a response from the partitioned node might result in a timeout error. CP is a good choice if your business needs require atomic reads and writes.


      * **AP (availability and partition tolerance)**
        * Responses return the most readily available version of the data available on any node, which might not be the latest. Writes might take some time to propagate when the partition is resolved.
        * Good choice if we need eventual consistency or when the system needs to continue working despite external errors.

## Replication

* Master master
  *   #### Advantages

      * Applications can read from both masters.
      * Distributes write load across both master nodes.
      * Simple, automatic, and quick failover.

      #### Disadvantages

      * Not as simple as master-slave to configure and deploy.
      * Either loosely consistent or have increased write latency due to synchronization.
      * Conflict resolution comes into play as more write nodes are added and as latency increases.

      <br>
* master slave
  *   #### Advantages



      * Backups of the entire database of relatively no impact on the master.
      * Applications can read from the slave(s) without impacting the master.
      * Slaves can be taken offline and synced back to the master without any downtime.

      #### Disadvantages



      * Replication adds more hardware and additional complexity.
      * Downtime and possibly loss of data when a master fails.
      * All writes also have to be made to the master in a master-slave architecture.
      * The more read slaves, the more we have to replicate, which will increase replication lag.<br>
*   ### Synchronous vs Asynchronous replication



    * The primary difference between synchronous and asynchronous replication is how the data is written to the replica. In synchronous replication, data is written to primary storage and the replica simultaneously. As such, the primary copy and the replica should always remain synchronised.
    * In contrast, asynchronous replication copies the data to the replica after the data is already written to the primary storage. Although the replication process may occur in near-real-time, it is more common for replication to occur on a scheduled basis and it is more cost-effective.

## OSI model

* Application&#x20;
* presentation
* session&#x20;
* transport
* Data
* Network
* Physical

## ACID

* Atomicity
  * Each transaction is all or nothing
* consistency
  * Any transaction will bring the database from one valid state to another
* Isolation
  * Executing transactions concurrently has the same results as if the transactions were executed serially
* Durability&#x20;
  * Once a transaction has been committed, it will remain so

## BASE

* With the increasing amount of data and high availability requirements, the approach to database design has also changed dramatically.&#x20;
*   To increase the ability to scale and at the same time be highly available, we move the logic from the database to separate servers. In this way, the database becomes more independent and focused on the actual process of storing data.


*   In the NoSQL database world, ACID transactions are less common as some databases have loosened the requirements for immediate consistency, data freshness, and accuracy in order to gain other benefits, like scale and resilience.


*   BASE properties are much looser than ACID guarantees, but there isn't a direct one-for-one mapping between the two consistency models. Let us understand these terms:

    <br>
* Basic Availability
  *   The database appears to work most of the time.

      ####
* #### Soft-state
  *   #### Stores don't have to be write-consistent, nor do different replicas have to be mutually consistent all the time.

      ####
* #### Eventual consistency
  *   #### The data might not be consistent immediately but eventually, it becomes consistent. Reads in the system are still possible even though they may not give the correct response due to inconsistency.

      <br>
* Replication
  * Master - slave
    * reads/writes— master
    * reads — slave
    * master synchronously passes the data to slave
  *   Master - master

      * reads and writes both goes to both any of the servers and the data consistency is maintained to both the servers.





## Sharding

* Dividing the data into multiple database so that requests are divided into multiple db
* Can lead to hot spots where some of the popular requests are to a single shard we ca overcome this by using a cache so that frequent request are stored in a cache and given to user faster.

Cache

* Cache hit
  * when a request is successfully served from cache
  * The cache hit can be divided into
    * hot cache
      * Fastest possible rate
      * data is retreived from L1
    * cold cache
      * Slowest possible rate for data to be read
      * data is found at L3 or lower
    * warm cache
      * Middle of cold and hot cahce,
      * data found in L2 or L3&#x20;
* Cache miss --
  * when a request is not found in the cache
* Cache invalidation
  * Removal or replacement of old or invalidated or old data from cache
* Types of cache
  * write through&#x20;
    * Data is written into the cache and the corresponding database simultaneously
  * write around
    * Where write directly goes to the database or permanent storage, bypassing the cache
  * Write back
    * Where the write is only done to the caching layer and the write is confirmed as soon as the write to the cache completes
* Distributed cache
  * A distributed cache is a system that pools together the random-access memory (RAM) of multiple networked computers into a single in-memory data store used as a data cache to provide fast access to data.
* Global cache
  *   we will have a single shared cache that all the application nodes will use. When the requested data is not found in the global cache, it's the responsibility of the cache to find out the missing piece of data from the underlying data store.

      <br>

## Proxy

* intermediary piece of hardware/software sitting between the client and the backend server
* Types
  * #### Forward Proxy
    * server that sits in front of a group of client machines. When those computers make requests to sites and services on the internet, the proxy server intercepts those requests and then communicates with web servers on behalf of those clients, like a middleman.
  * #### Reverse Proxy
    * A reverse proxy is a server that sits in front of one or more web servers, intercepting requests from clients. When clients send requests to the origin server of a website, those requests are intercepted by the reverse proxy server.

## DB --

* database is an organized collection of structured information, or data, typically stored electronically in a computer system. A database is usually controlled by a Database Management System (DBMS).
* Componenta
  * Schema
    * define the shape of a data structure, and specify what kinds of data can go where. Schemas can be strictly enforced across the entire database, loosely enforced on part of the database, or they might not exist at all.
  * Table
    * table contains various columns just like in a spreadsheet. A table can have as meager as two columns and upwards of a hundred or more columns, depending upon the kind of information being put in the table.
  * Column
    * column contains a set of data values of a particular type, one value for each row of the database. A column may contain text values, numbers, enums, timestamps, etc
  * Row
    * Data in a table is recorded in rows. There can be thousands or millions of rows in a table having any particular information.
* Types
  * Sql
    * A SQL (or relational) database is a collection of data items with pre-defined relationships between them.&#x20;
    * These items are organized as a set of tables with columns and rows. Tables are used to hold information about the objects to be represented in the database.&#x20;
    * Each column in a table holds a certain kind of data and a field stores the actual value of an attribute.&#x20;
    * The rows in the table represent a collection of related values of one object or entity.
    * Advantages
      * Simple and accurate
      * Accessibility
      * Data consistency
      * Flexibility
    * Disadvantages
      * Expensive to maintain
      * Difficult schema evolution
      * Performance hits (join, denormalization, etc.)
      * Difficult to scale due to poor horizontal scalability
    * Example --
      *
  * Nosql
    * NoSQL is a broad category that includes any database that doesn't use SQL as its primary data access language. These types of databases are also sometimes referred to as non-relational databases.
    *   Types

        * Document
          * document database (also known as a document-oriented database or a document store) is a database that stores information in documents. They are general-purpose databases that serve a variety of use cases for both transactional and analytical applications.
          * **Advantages**
            * Intuitive and flexible
            * Easy horizontal scaling
            * Schemaless<br>
          * **Disadvantages**
            * Schemaless
            * Non-relational
          * Examples--
            * MongDB
            * Amazon DocumentDB
            * Cosmos DB
            *
        * Key-value
          * One of the simplest types of NoSQL databases, key-value databases save data as a group of key-value pairs made up of two data items each. They're also sometimes referred to as a key-value store.
          * **Advantages**
            * Simple and performant
            * Highly scalable for high volumes of traffic
            * Session management
            * Optimized lookups<br>
          * **Disadvantages**
            * Basic CRUD
            * Values can't be filtered
            * Lacks indexing and scanning capabilities
            * Not optimized for complex queries
          * Examples&#x20;
            * [Redis](https://redis.io/)
            * [Memcached](https://memcached.org/)
            * [Amazon DynamoDB](https://aws.amazon.com/dynamodb)
            * [Aerospike](https://aerospike.com/)
        * Graph
          * graph database is a NoSQL database that uses graph structures for semantic queries with nodes, edges, and properties to represent and store data instead of tables or documents.
          *   The graph relates the data items in the store to a collection of nodes and edges, the edges representing the relationships between the nodes. The relationships allow data in the store to be linked together directly and, in many cases, retrieved with one operation.


          * **Advantages**
            * Query speed
            * Agile and flexible
            * Explicit data representation<br>
          * **Disadvantages**
            * Complex
            * No standardized query language
          * **Examples**
            * [Neo4j](https://neo4j.com/)
            * [ArangoDB](https://www.arangodb.com/)
            * [Amazon Neptune](https://aws.amazon.com/neptune)
            * [JanusGraph](https://janusgraph.org/)
          * **Use cases**
            * Fraud detection
            * Recommendation engines
            * Social networks
            * Network mapping
        * Timeseries
          * A time-series database is a database optimized for time-stamped, or time series, data.
          * **Advantages**
            * Fast insertion and retrieval
            * Efficient data storage
          * **Examples**
            * [InfluxDB](https://www.influxdata.com/)
            * [Apache Druid](https://druid.apache.org/)
          * **Use cases**
            * IoT data
            * Metrics analysis
            * Application monitoring
            * Understand financial trends
        * Wide column
          * Wide column databases, also known as wide column stores, are schema-agnostic. Data is stored in column families, rather than in rows and columns.
          * **Advantages**
            * Highly scalable, can handle petabytes of data
            * Ideal for real-time big data applications
          * **Use cases**
            * Business analytics
            * Attribute-based data storage
          * **Examples**
            * [BigTable](https://cloud.google.com/bigtable)
            * [Apache Cassandra](https://cassandra.apache.org/)
            * [ScyllaDB](https://www.scylladb.com/)
          * **Disadvantages**
            * Expensive
            * Increased write time
        * Multi-model
          * Multi-model databases combine different database models (i.e. relational, graph, key-value, document, etc.) into a single, integrated backend. This means they can accommodate various data types, indexes, queries, and store data in more than one model.
          * **Advantages**
            * Flexibility
            * Suitable for complex projects
            * Data consistent<br>
          * **Examples**
            * [ArangoDB](https://www.arangodb.com/)
            * [Azure Cosmos DB](https://azure.microsoft.com/en-in/services/cosmos-db)
            * [Couchbase](https://www.couchbase.com/)
          * **Disadvantages**
            * Complex
            * Less mature

        <br>
    * reasons for picking SQL or NoSQL based database:
      * **For SQL**
        * Structured data with strict schema
          * Relational data
          * Need for complex joins
          * Transactions
          * Lookups by index are very fast
      * **For NoSQL**
        * Dynamic or flexible schema
        * Non-relational data
        * No need for complex joins
        * Very data-intensive workload
        * Very high throughput for IOPS



## Transactions

* A transaction is a series of database operations that are considered to be a _"single unit of work"_.&#x20;
* The operations in a transaction either all succeed, or they all fail. In this way, the notion of a transaction supports data integrity when part of a system fails.&#x20;
* Not all databases choose to support ACID transactions, usually because they are prioritizing other optimizations that are hard or theoretically impossible to implement together.
* _Usually, relational databases support ACID transactions, and non-relational databases don't (there are exceptions)._
* _Types_
  * #### Active
    * #### In this state, the transaction is being executed. This is the initial state of every transaction.
  * #### Partially Committed
    * #### When a transaction executes its final operation, it is said to be in a partially committed state.
  * #### Committed
    * #### If a transaction executes all its operations successfully, it is said to be committed. All its effects are now permanently established on the database system.
  * #### Failed
    * #### The transaction is said to be in a failed state if any of the checks made by the database recovery system fails. A failed transaction can no longer proceed further.
  * #### Aborted
    *   #### If any of the checks fail and the transaction has reached a failed state, then the recovery manager rolls back all its write operations on the database to bring the database back to its original state where it was prior to the execution of the transaction. Transactions in this state are aborted.

        The database recovery module can select one of the two operations after a transaction aborts:

        * Restart the transaction
          * Kill the transaction

        ####
    *   #### Terminated



        If there isn't any roll-back or the transaction comes from the _committed state_, then the system is consistent and ready for a new transaction and the old transaction is terminated.

        <br>
* ## Distributed Transactions
  * distributed transaction is a set of operations on data that is performed across two or more databases. It is typically coordinated across separate nodes connected by a network
  * distributed transaction processing is more complicated, because the database must coordinate the committing or rollback of the changes in a transaction as a self-contained unit.
  * In other words, all the nodes must commit, or all must abort and the entire transaction rolls back. This is why we need distributed transactions.
  * possible solution
    * Two-phase commit
      * distributed algorithm that coordinates all the processes that participate in a distributed transaction on whether to commit or abort (roll back) the transaction.
      * phases
        * prepare phase
          *   involves the coordinator node collecting consensus from each of the participant nodes. The transaction will be aborted unless each of the nodes responds that they're _prepared_.

              <br>
        *   commit phase

            * f all participants respond to the coordinator that they are _prepared_, then the coordinator asks all the nodes to commit the transaction. If a failure occurs, the transaction will be rolled back.


      *   Following problems may arise in the two-phase commit protocol:

          * What if one of the nodes crashes?
          * What if the coordinator itself crashes?
          * It is a blocking protocol.


    * Three phase commit
      * extension of the two-phase commit where the commit phase is split into two phases. This helps with the blocking problem that occurs in the two-phase commit protocol.<br>
      * **Prepare phase**
        *   This phase is the same as the two-phase commit.


      * **Pre-commit phase**
        * Coordinator issues the pre-commit message and all the participating nodes must acknowledge it.&#x20;
        *   If a participant fails to receive this message in time, then the transaction is aborted.


      * **Commit phase**
        * This step is also similar to the two-phase commit protocol.

## Sagas

* saga is a sequence of local transactions.&#x20;
* Each local transaction updates the database and publishes a message or event to trigger the next local transaction in the saga.&#x20;
*   If a local transaction fails because it violates a business rule then the saga executes a series of compensating transactions that undo the changes that were made by the preceding local transactions.


* Coordination
  * Choreography: Each local transaction publishes domain events that trigger local transactions in other services
  * Orchestration: An orchestrator tells the participants what local transactions to execute.
* Problems:
  * The saga pattern is particularly hard to debug
  * There's a risk of cyclic dependency between saga participants.
  * Lack of participant data isolation imposes durability challenges.
  * Testing is difficult because all services must be running to simulate a transaction

## Data partitoning

* technique to break up a database into many smaller parts.&#x20;
* It is the process of splitting up a database or a table across multiple machines to improve the manageability, performance, and availability of a database
* Methods
  *   Horizontal partitoning/ sharding

      * we split the table data horizontally based on the range of values defined by the _partition key_. It is also referred to as _**database sharding.**_
      * Each partition has the same schema and columns, but also a subset of the shared data. Likewise, the data held in each is unique and independent of the data held in other partitions.
      * Sharding can be implemented at both application or the database level.



      * Partitoning criteria
        * Hash based
          *   strategy divides the rows into different partitions based on a hashing algorithm rather than grouping database rows based on continuous indexes.

              The disadvantage of this method is that dynamically adding/removing database servers becomes expensive.


        * List based
          * each partition is defined and selected based on the list of values on a column rather than a set of contiguous ranges of values.
        * Range based
          * maps data to various partitions based on ranges of values of the partitioning key.&#x20;
          *   In other words, we partition the table in such a way that each partition contains rows within a given range defined by the partition key.


          * Ranges should be contiguous but not overlapping, where each range specifies a non-inclusive lower and upper bound for a partition.&#x20;
          *   Any partitioning key values equal to or higher than the upper bound of the range are added to the next partition.

              <br>
        * Composite
          * composite partitioning partitions the data based on two or more partitioning techniques. H
          *   ere we first partition the data using one technique, and then each partition is further subdivided into sub-partitions using the same or some other method.


      *   Advantages



          * **Availability**: Provides logical independence to the partitioned database, ensuring the high availability of our application. Here individual partitions can be managed independently.
          * **Scalability**: Proves to increase scalability by distributing the data across multiple partitions.
          * **Security**: Helps improve the system's security by storing sensitive and non-sensitive data in different partitions. This could provide better manageability and security to sensitive data.
          * **Query Performance**: Improves the performance of the system. Instead of querying the whole database, now the system has to query only a smaller partition.
          * **Data Manageability**: Divides tables and indexes into smaller and more manageable units.

          <br>
      *   Disadvantages

          * **Complexity**: Sharding increases the complexity of the system in general.
          * **Joins across shards**: Once a database is partitioned and spread across multiple machines it is often not feasible to perform joins that span multiple database shards. Such joins will not be performance efficient since data has to be retrieved from multiple servers.
          * **Rebalancing**: If the data distribution is not uniform or there is a lot of load on a single shard, in such cases, we have to rebalance our shards so that the requests are as equally distributed among the shards as possible.


      *   When to use sharding

          * Leveraging existing hardware instead of high-end machines.
          * Maintain data in distinct geographic regions.
          * Quickly scale by adding more shards.
          * Better performance as each machine is under less load.
          * When more concurrent connections are required.

          <br>
  * **Ver**tical partitoning
    * we partition the data vertically based on columns. We divide tables into relatively smaller tables with few elements, and each part is present in a separate partition.\
      Consistent hashing
* traditional hashing-based distribution methods, we use a hash function to hash our partition keys (i.e. request ID or IP). Then if we use the modulo against the total number of nodes (server or databases). This will give us the node where we want to route our request.
* The problem with this is if we add or remove a node, it will cause `N` to change, meaning our mapping strategy will break as the same requests will now map to a different server
* Consistent hashing solves this horizontal scalability problem by ensuring that every time we scale up or down, we do not have to re-arrange all the keys or touch all the servers , we only need to re distribute a part of the data specifically K/N where K — number of partition key N - number of nodes.
*   How does it work

    * Consistent Hashing is a distributed hashing scheme that operates independently of the number of nodes in a distributed hash table by assigning them a position on an abstract circle, or hash ring.
    * Using consistent hashing, only `K/N` data would require re-distributing.
    * Now, when the request comes in we can simply route it to the closest node in a clockwise (can be counterclockwise as well) manner.&#x20;
    *   This means that if a new node is added or removed, we can use the nearest node and only a _fraction_ of the requests need to be re-routed.


    * In theory, consistent hashing should distribute the load evenly however it doesn't happen in practice.&#x20;
    * Usually, the load distribution is uneven and one server may end up handling the majority of the request becoming a _hotspot_, essentially a bottleneck for the system.&#x20;
    * We can fix this by adding extra nodes but that can be expensive.


* Virtual nodes
  *   to ensure a more evenly distributed load, we can introduce the idea of a virtual node, sometimes also referred to as a VNode.


  * Instead of assigning a single position to a node, the hash range is divided into multiple smaller ranges, and each physical node is assigned several of these smaller ranges. Each of these subranges is considered a VNode.&#x20;
  * Hence, virtual nodes are basically existing physical nodes mapped multiple times across the hash ring to minimize changes to a node's assigned range.
  *   As VNodes help spread the load more evenly across the physical nodes on the cluster by diving the hash ranges into smaller subranges, this speeds up the re-balancing process after adding or removing nodes. This also helps us reduce the probability of hotspots.

      <br>
*   Advantages



    * Makes rapid scaling up and down more predictable.
    * Facilitates partitioning and replication across nodes.
    * Enables scalability and availability.
    * Reduces hotspots.

    <br>
*   Disadvantages



    * Increases complexity.
    * Cascading failures.
    * Load distribution can still be uneven.
    * Key management can be expensive when nodes transiently fail.<br>

## Database federation

* splits up databases by function. The federation architecture makes several distinct physical databases appear as one logical database to end-users.
* All of the components in a federation are tied together by one or more federal schemas that express the commonality of data throughout the federation.&#x20;
* These federated schemas are used to specify the information that can be shared by the federation components and to provide a common basis for communication among them.
*   Characteristics



    * **Transparency**: Federated database masks user differences and implementations of underlying data sources. Therefore, the users do not need to be aware of where the data is stored.
    * **Heterogeneity**: Data sources can differ in many ways. A federated database system can handle different hardware, network protocols, data models, etc.
    * **Extensibility**: New sources may be needed to meet the changing needs of the business. A good federated database system needs to make it easy to add new sources.
    * **Autonomy**: A Federated database does not change existing data sources, interfaces should remain the same.
    * **Data integration**: A federated database can integrate data from different protocols, database management systems, etc.<br>
*   Advantages



    * Flexible data sharing.
    * Autonomy among the database components.
    * Access heterogeneous data in a unified way.
    * No tight coupling of applications with legacy databases.<br>
*   Disadvantages



    * Adds more hardware and additional complexity.
    * Joining data from two databases is complex.
    * Dependence on autonomous data sources.
    * Query performance and scalability.<br>

N-tier artitecture

* divides an application into logical layers and physical tiers. Layers are a way to separate responsibilities and manage dependencies.&#x20;
* Each layer has a specific responsibility. A higher layer can use services in a lower layer, but not the other way around.
* Tiers are physically separated, running on separate machines. A tier can call to another tier directly, or use asynchronous messaging.&#x20;
* Although each layer might be hosted in its own tier, that's not required. Several layers might be hosted on the same tier.&#x20;
* Physically separating the tiers improves scalability and resiliency and adds latency from the additional network communication.
* An N-tier architecture can be of two types:
  * In a closed layer architecture, a layer can only call the next layer immediately down.
  * In an open layer architecture, a layer can call any of the layers below it.
* Types
  *   #### 3-Tier architecture



      3-Tier is widely used and consists of the following different layers:

      * **Presentation layer**: Handles user interactions with the application.
      * **Business Logic layer**: Accepts the data from the application layer, validates it as per business logic and passes it to the data layer.
      * **Data Access layer**: Receives the data from the business layer and performs the necessary operation on the database.

      ####
  * #### 2-Tier architecture
    *   #### In this architecture, the presentation layer runs on the client and communicates with a data store. There is no business logic layer or immediate layer between client and server.

        ####
  * #### Single Tier or 1-Tier architecture
    * #### It is the simplest one as it is equivalent to running the application on a personal computer. All of the required components for an application to run are on a single application or server.
*   #### Advantages

    ####

    * Can improve availability.
    * Better security as layers can behave like a firewall.
    * Separate tiers allow us to scale them as needed.
    * Improve maintenance as different people can manage different tiers.<br>
*   #### Disadvantages

    ####

    * Increased complexity of the system as a whole.
    * Increased network latency as the number of tiers increases.
    * Expensive as every tier will have its own hardware cost.
    * Difficult to manage network security.



* Message Brokers
  * message broker is a software that enables applications, systems, and services to communicate with each other and exchange information.&#x20;
  * The message broker does this by translating messages between formal messaging protocols.&#x20;
  * This allows interdependent services to _"talk"_ with one another directly, even if they were written in different languages or implemented on different platforms.
  * Message brokers can validate, store, route, and deliver messages to the appropriate destinations.&#x20;
  * They serve as intermediaries between other applications, allowing senders to issue messages without knowing where the receivers are, whether or not they are active, or how many of them there are.&#x20;
  * This facilitates the decoupling of processes and services within systems.
  *   Message brokers offer two basic message distribution patterns or messaging styles:

      * [**Point-to-Point messaging**](https://karanpratapsingh.com/courses/system-design/message-queues): This is the distribution pattern utilized in message queues with a one-to-one relationship between the message's sender and receiver.
      * [**Publish-Subscribe messaging**](https://karanpratapsingh.com/courses/system-design/publish-subscribe): In this message distribution pattern, often referred to as _"pub/sub"_, the producer of each message publishes it to a topic, and multiple message consumers subscribe to topics from which they want to receive messages.


  * Ex —&#x20;
    * [NATS](https://nats.io/)
    * [Apache Kafka](https://kafka.apache.org/)
    * [RabbitMQ](https://www.rabbitmq.com/)
    * [ActiveMQ](https://activemq.apache.org/)

## Message queues

* form of service-to-service communication that facilitates asynchronous communication.
* asynchronously receives messages from producers and sends them to consumers.
* Queues are used to effectively manage requests in large-scale distributed systems.&#x20;
* In small systems with minimal processing loads and small databases, writes can be predictably fast.&#x20;
* However, in more complex and large systems writes can take an almost non-deterministic amount of time.
* Working
  * Messages are stored in the queue until they are processed and deleted.
  *   Each message is processed only once by a single consumer. Here's how it works:

      * A producer publishes a job to the queue, then notifies the user of the job status.
      * A consumer picks up the job from the queue, processes it, then signals that the job is complete.


*   Advantages



    * **Scalability**: Message queues make it possible to scale precisely where we need to. When workloads peak, multiple instances of our application can add all requests to the queue without the risk of collision.
    * **Decoupling**: Message queues remove dependencies between components and significantly simplify the implementation of decoupled applications.
    * **Performance**: Message queues enable asynchronous communication, which means that the endpoints that are producing and consuming messages interact with the queue, not each other. Producers can add requests to the queue without waiting for them to be processed.
    * **Reliability**: Queues make our data persistent, and reduce the errors that happen when different parts of our system go offline.

    <br>
*   Features

    *   Push or Pull Delivery



        Most message queues provide both push and pull options for retrieving messages. Pull means continuously querying the queue for new messages. Push means that a consumer is notified when a message is available. We can also use long-polling to allow pulls to wait a specified amount of time for new messages to arrive.
    *   FIFO (First-In-First-Out) Queues



        In these queues, the oldest (or first) entry, sometimes called the _"head"_ of the queue, is processed first.
    *   Schedule or Delay Delivery



        Many message queues support setting a specific delivery time for a message. If we need to have a common delay for all messages, we can set up a delay queue.



    *   At-Least-Once Delivery



        Message queues may store multiple copies of messages for redundancy and high availability, and resend messages in the event of communication failures or errors to ensure they are delivered at least once.
    *   Exactly-Once Delivery



        When duplicates can't be tolerated, FIFO (first-in-first-out) message queues will make sure that each message is delivered exactly once (and only once) by filtering out duplicates automatically.
    *   Dead-letter Queues



        A dead-letter queue is a queue to which other queues can send messages that can't be processed successfully. This makes it easy to set them aside for further inspection without blocking the queue processing or spending CPU cycles on a message that might never be consumed successfully.
    *   Ordering



        Most message queues provide best-effort ordering which ensures that messages are generally delivered in the same order as they're sent and that a message is delivered at least once.
    *   Poison-pill Messages



        Poison pills are special messages that can be received, but not processed. They are a mechanism used in order to signal a consumer to end its work so it is no longer waiting for new inputs, and are similar to closing a socket in a client/server model.
    *   Security



        Message queues will authenticate applications that try to access the queue, this allows us to encrypt messages over the network as well as in the queue itself
    *   Task Queues



        Tasks queues receive tasks and their related data, run them, then deliver their results. They can support scheduling and can be used to run computationally-intensive jobs in the background.
* Examples
  * [Amazon SQS](https://aws.amazon.com/sqs)
  * [RabbitMQ](https://www.rabbitmq.com/)
  * [ActiveMQ](https://activemq.apache.org/)
  * [ZeroMQ](https://zeromq.org/)



* Publish-subscribe
  * publish-subscribe is also a form of service-to-service communication that facilitates asynchronous communication.&#x20;
  * In a pub/sub model, any message published to a topic is pushed immediately to all the subscribers of the topic.
  * The subscribers to the message topic often perform different functions, and can each do something different with the message in parallel.&#x20;
  * The publisher doesn't need to know who is using the information that it is broadcasting, and the subscribers don't need to know where the message comes from.&#x20;
  * This style of messaging is a bit different than message queues, where the component that sends the message often knows the destination it is sending to.
  * Unlike message queues, which batch messages until they are retrieved, message topics transfer messages with little or no queuing and push them out immediately to all subscribers. Here's
  *   Working&#x20;



      * A message topic provides a lightweight mechanism to broadcast asynchronous event notifications and endpoints that allow software components to connect to the topic in order to send and receive those messages.
      * To broadcast a message, a component called a _publisher_ simply pushes a message to the topic.
      * All components that subscribe to the topic (known as _subscribers_) will receive every message that was broadcasted.
  *   Advantages --



      * **Eliminate Polling**: Message topics allow instantaneous, push-based delivery, eliminating the need for message consumers to periodically check or _"poll"_ for new information and updates. This promotes faster response time and reduces the delivery latency which can be particularly problematic in systems where delays cannot be tolerated.
      * **Dynamic Targeting**: Pub/Sub makes the discovery of services easier, more natural, and less error-prone. Instead of maintaining a roster of peers where an application can send messages, a publisher will simply post messages to a topic. Then, any interested party will subscribe its endpoint to the topic, and start receiving these messages. Subscribers can change, upgrade, multiply or disappear and the system dynamically adjusts.
      * **Decoupled and Independent Scaling**: Publishers and subscribers are decoupled and work independently from each other, which allows us to develop and scale them independently.
      * **Simplify Communication**: The Publish-Subscribe model reduces complexity by removing all the point-to-point connections with a single connection to a message topic, which will manage subscriptions and decide what messages should be delivered to which endpoints.


  *
  * [https://github.com/karanpratapsingh/system-design?tab=readme-ov-file#enterprise-service-bus-esb](https://github.com/karanpratapsingh/system-design?tab=readme-ov-file#enterprise-service-bus-esb)<br>
  * [https://www.youtube.com/watch?v=-MTSQjw5DrM\&ab\_channel=Fireship](https://www.youtube.com/watch?v=-MTSQjw5DrM\&ab_channel=Fireship)
*

    <br>

    <br>
