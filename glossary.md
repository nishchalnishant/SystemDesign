---
description: This page has the glossary of the system design
---

# Glossary



## <mark style="color:purple;">Network Layers</mark>

* Application
  * Network process to application.
  * Types of communicatio, Email, File transfer, clien/server
  * End User layer
  * HTTP, FTP, IRC, SSH, DNS
* Presentation&#x20;
  * Data representation and encryption
  * Encryption, data conversion
  * Syntax Layer
  * HSSL, SSH, IMAP, FTP, MPEG, JPEG
* Session
  * Start, stop session, maintains order
  * Interhost communication&#x20;
  * Synch& send to port
  * APIs, Sackets, WinSock
* Transport
  * Ensures delivery of entire file or message
  * End to end connection and reliability
  * End-to-End Connections
  * TCP, UDP
* Network
  * Routes data to different LANs and WANs based on network adress
  * Path determination and logical addression
  * Pakets
  * IP, ICMP, IPSec, IGMP
* Data Link
  * Transmits packets from node to node based on station address.
  * Physical addressing
  * Frames
  * Ethernet, PPP, Switch, Bridge
* Physical
  * Electrical signal and cabling&#x20;
  * Media, signal and binary transmission&#x20;
  * Physical structure&#x20;
  * Cables, Fiber, Wireless

## <mark style="color:purple;">CAP (consistency, Availability and partition tolerance)</mark>

* Consistency: Ensuring all nodes see the same data at the same time.
* Availability: Ensuring the system remains operational, even in the presence of failures.
* Partition Tolerance: Allowing the system to function despite network failures.



## <mark style="color:purple;">SOLID Principles:</mark>

* Single Responsibility Principle (SRP): A class should have only one reason to change, meaning it should have only one responsibility.
* Open/Closed Principle (OCP): A class should be open for extension but closed for modification.
* Liskov Substitution Principle (LSP): Objects of a superclass should be able to be replaced with objects of its subclass without affecting the correctness of the program.
* Interface Segregation Principle (ISP): Clients should not be forced to depend on interfaces they do not use.
* Dependency Inversion Principle (DIP): High-level modules should not depend on low-level modules; both should depend on abstractions.

API design choices --

<figure><img src=".gitbook/assets/image (131).png" alt="" width="563"><figcaption></figcaption></figure>

## <mark style="color:purple;">Design pattern s</mark>

<figure><img src=".gitbook/assets/image (133).png" alt="" width="513"><figcaption></figcaption></figure>

## <mark style="color:purple;">Caching strategies --</mark>

* Caching strategies are a set of techniques designed to improve the efficiency of systems by storing frequently accessed information in a special intermediate storage known as a cache. This allows the data to be accessed quickly without re-computing or accessing the original source.
* Caching strategies can be varied and should be tailored to the specific needs of the system or application. Here are some common approaches:
  * Full caching: In this approach, all data or results are stored in the cache, allowing instant access to the full set of information. This method is suitable when the amount of data is small and can be efficiently stored in memory.
  * Partial caching: Here, only a portion of the data is cached, usually the most frequently accessed or most used data. This solution is preferable for large data sets or in situations where not all data is constantly in demand.
  * Time-to-live caching: Data is cached for a specified period, after which the information is considered stale and updated from the primary source. This method is suitable for data that does not change frequently.
  * LRU (Least Recently Used) and LFU (Least Frequently Used) strategies: These approaches remove unused data from the cache to make room for new information. They are useful when cache space is limited.
  * Write-through or Write-behind (write-back) caching: The write-through strategy writes data to the cache and the primary source simultaneously, while the write-behind strategy writes to the cache first and then to the source. These methods are used to maintain consistency between the cache and the source of the data.
  * Distributed Caching: This approach distributes the cache across multiple nodes or servers using specialized technologies. This solution is ideal for distributed systems where cache consistency and performance must be ensured across all nodes.
  * Custom Caching: Developers can create customized caching strategies that best suit the unique requirements and features of the system. This may involve combining the strategies mentioned above or developing their own innovative approaches.
* Cache hit and cache miss --
  *

      <figure><img src=".gitbook/assets/image (135).png" alt="" width="563"><figcaption></figcaption></figure>

<figure><img src=".gitbook/assets/image (134).png" alt="" width="465"><figcaption></figcaption></figure>

## <mark style="color:purple;">Scalability, Reliability and availability</mark>

* Definition Availability and reliability are crucial aspects of system design that focus on ensuring that a system is accessible, responsive, and dependable. Let's explore these concepts further:
* Scalability --
  * Definition --
    * critical aspect of system design that refers to the ability of a system to handle increasing workloads or accommodate growth without sacrificing performance.
    * involves designing systems that can effectively handle larger amounts of data, users, or transactions as demand increases.
    *

        <figure><img src=".gitbook/assets/image (132).png" alt="" width="563"><figcaption></figcaption></figure>
  * Here are some key concepts related to scalability:
    * Horizontal Scalability: Also known as "scaling out," horizontal scalability involves adding more machines or nodes to a system to distribute the load. By dividing the workload across multiple machines, the system can handle increased traffic or data volume. This can be achieved through techniques like load balancing and partitioning.
    * Vertical Scalability: Also known as "scaling up," vertical scalability involves upgrading the resources (CPU, memory, storage) of an individual machine to handle increased demands. This approach has limitations, as there is typically a maximum threshold for how much a single machine can be scaled.
    * Elasticity: Elasticity refers to the ability of a system to automatically and dynamically scale resources up or down based on demand. Cloud platforms provide auto-scaling features that adjust the number of instances or resources allocated to a system based on predefined rules or metrics.
    * Shared-Nothing Architecture: This architecture ensures that each node or machine in a distributed system operates independently and does not share resources or state with other nodes. This allows for easier horizontal scalability since new nodes can be added without requiring coordination with existing nodes.
* Availability:
  * Definition Availability refers to the ability of a system to be operational and accessible, typically measured as a percentage of time that a system is up and running without any disruptions. High availability is essential for systems that require continuous operation, such as e-commerce platforms, financial systems, or communication networks.
  * Here are some key considerations for achieving high availability:
    * Redundancy: Introduce redundancy by duplicating critical components or introducing backup systems. If one component fails, another takes over seamlessly to ensure uninterrupted operation.
    * Fault Tolerance: Design systems to tolerate failures at various levels, such as hardware failures, software errors, or network issues. This includes mechanisms like error handling, retry strategies, and failover mechanisms.
    * Load Balancing: Distribute the workload across multiple instances or servers to prevent overloading and increase system availability. Load balancing techniques include round-robin, least connection, or adaptive algorithms that route incoming requests efficiently.
    * Failover and Disaster Recovery: Plan for disaster scenarios by establishing failover mechanisms and disaster recovery strategies. This involves replicating data across multiple locations, having backup systems ready, and implementing failover processes to minimize downtime.
* Reliability:&#x20;
  * Definition Reliability focuses on ensuring that a system consistently performs its intended functions correctly over a specified period. A reliable system minimizes failures, errors, and unexpected behaviours, thus building trust with users.
  * Here are some key considerations for achieving system reliability:
    * Robust Error Handling: Implement robust error handling mechanisms to gracefully handle exceptions, recover from errors, and maintain system stability. This includes proper logging, exception handling, and error reporting.
    * Data Integrity: Ensure the integrity of data by using techniques such as checksums, data validation, backups, and redundancy. This helps detect and recover from data corruption or loss.
    * Monitoring and Alerting: Continuously monitor the system's health and performance, and set up alerts to promptly identify and respond to potential issues. Monitoring tools and automated alerting systems play a crucial role in maintaining reliability.
    * Testing and Validation: Thoroughly test the system to identify and rectify potential weaknesses or flaws. This includes unit testing, integration testing, stress testing, and performance testing.
    * Version Control and Rollbacks: Implement version control systems and establish rollback mechanisms to revert to a stable state in case of issues or failures introduced by software updates or changes.
    * Documentation and Maintenance: Maintain up-to-date documentation that captures the system's architecture, dependencies, and configurations. Regularly perform system maintenance tasks such as software updates, security patches, and hardware maintenance.
    * Both availability and reliability are interrelated and often involve trade-offs with other system design considerations, such as scalability and performance. Striking the right balance between these factors is crucial to ensure a robust and dependable system.

## <mark style="color:purple;">Types of IP address</mark>

* Public
* Private
* Static
* Dynamic

## <mark style="color:purple;">TCP vs UDP</mark>

## <mark style="color:purple;">ACID Properties</mark>&#x20;

* A – atomicity&#x20;
  * &#x20;either a transaction happens completely or they don't happen at all
* C --consistency&#x20;
  * give same result for two similar operation at the same time
* I -- Isolation&#x20;
  * two different operations are independent of each other
* D -- durability&#x20;
  * the data is getting stored into the disk regularly

## <mark style="color:purple;">Types of database</mark>

<figure><img src=".gitbook/assets/image (130).png" alt="" width="488"><figcaption></figcaption></figure>

* Relational
  * Most popular
  * Based on two properties we decide if we want to use relational db or not
    * Schema
      * How our db is going to be structured
      * There are schema constraints that makes sure that we choose relational db \[ this helps that garbage or null values don't get included into the dataset
      * In relational db we have table and rows , and if the data can be stored using table and rows then we can use the relational db.
    * Acid properties
      * A – atomicity \[ either a transaction happens completely or they don't happen at all]
      * C --consistency \[ give same result for two similar operation at the same time]
      * I -- Isolation \[ two different operations are independent of each other
      * D -- durability \[ the data is getting stored into the disk regularly]
    * When to choose relational db
      * If we need acid properties
      * If we have fixed schema
      * If we are mandating the transactions of data
    * When not to use relational db
      * If we are not sure of the schema initially , it is not advised to use relational db although we can do that but it will be hard
      * Horizontal Scaling is hard
    * Benefits
      * Database for interrelated complex data can easily be designed
      * Ensures garbage or null value is not populated into the db
      * Ensures all other schemas constraints are followed
* Non relational \[No Fixed schema]
  * Key-value stores
    * Just like hash map
    * ex — Discount, caching ability, application related rata
    * ex — Redis, Amazon DynamoDB, RIak, Aerospike, Couchbase server and level DB
  * Document based db&#x20;
    * Used when we are not sure how the data is going to evolve over time
    * Heavy read and write
    * Provide shading capability
    * Their structure is like collection and inside that we have documents
    * Ex-- MongoDB; Cosmos DB; ArangoDB; Couchbase Server
  * Column based DB&#x20;
    * Midway between relational and document based db , these have fixed schema in form of rows and columns but don’t support the acid schema
    * Heavy writes , special reads
    * Ex -- storing health tracking data , storing the event data like liking the song , updating the playlist etc
    * Distributed
    * Ex -- cassandra , hbase , siylla
  * Search DB&#x20;
    * Whenever we interact with application where we need to search anything
    * The data is stored into the index form
    * Ex-- elastic

## <mark style="color:purple;">Proxies</mark>

* Always think of proxies as on behalf , whenever someone or something works on behalf of another then this is known as proxies .
* Forward
  * Proxy sits on the client side
  * Use cases –
    * safety \[ server doesn't knows about ip address of the client ] ,
    * blocking access to the certain server&#x20;
    * uses in cases of caching the data at client side
* Reverse
  * Proxy sits on the server side
  * If reverse proxy fails it becomes the reason for single point failure.
  * Use cases
    * Anonymity of server , client doesn't knows ip address of the server
    * Caching the response at the server side
    * Mitigating the ddos attack
    * Ssl encryption

## <mark style="color:purple;">Types of system</mark>

* Authorization system – user login identity management
  * Volume – low
  * Security – utmost priority
* Streaming system --
  * Volume –high
  * Retrieval – high
* Transactional system – banking , ecommerce sites
  * Security – high priority
  * How the data flows is of utmost priority
  * Make sure that there is no double entry of data into the system
* Heavy compute systems
  * Image recognition , video processing



## <mark style="color:purple;">Choosing a database</mark>

* Indexes
  * database index is used for the purpose of speeding up reads conditioned on the value of a specific key, be carefult to not overuse indexes as they slow down the database writes
  * There are two main types
    * LSM trees + SS tables
      * Writes first fo to a balanced binary search tree in memory
      * Tree flusehd to a sorted table on disk when it gets too big
      * Can binary seach Sstables for the value of  a key
      * If there are too many SSTables they can be merged together(old values of keys will be discarded))
    * B trees
      * A binary tree using pointers on disk
      * Writes iterate through the binary tree and either overwrites the existing key value of create a new page on disk and modify the parent pointer to the new page
* Replication
  * Replication is the process of having multiple copies of data in order to make sure that if a databse goes down the data itsn't lost
  * Types
    * Single leader replication
      * All writes go to one databse reads come from any database
      * Usefilt to ensure that there are no data conflicts
      * All writes go to one node
    * Multi leader replication
      * Writes can go to a small subset of leader databses reads can come from any database
      * Used for increasing write throughput beyond just one database node( at the cost of potential write conflicts)
    * Leaderless replication
      * Writes goes to all databses, reads comes from all databases
      * Used for increasing write throughput beyond just one database node( at the cost of potential write conflicts)
* SQL DATABASES
  * Key features
    * Relational/Normalized data -- Changes to one table may require changes to others
      * Ex -- adding an author and their books to different tables on different nodes
      * May require two phased commit (Expensive)
    * Have transactional (acid guarantees)
      * Excessively slow if you don't need them (dur to two phase locking)
      * Typically use B-trees
      * Better for reads than writes in theory
    * Conclusion --
      * Use sql when correctness is of more important than spped
      * Like banking applications , job scheduling

## <mark style="color:purple;">STAR Method:</mark>

* Situation: Describe the context or situation.
* Task: Explain what was required of you.
* Action: Detail the steps you took.
* Result: Share the outcome or what you learned.

&#x20;

## <mark style="color:purple;">Architecture Patterns</mark>&#x20;

* Architecture patterns, also known as architectural styles or design patterns, are established solutions or templates that provide guidance for designing the overall structure and organization of software systems. They define the relationships, interactions, and responsibilities among the components of a system. Here are some commonly used architecture patterns:
* Layered Architecture: In layered architecture, the system is divided into logical layers, with each layer having a specific responsibility. Typically, these layers include presentation/UI, business logic, and data storage. Layered architecture promotes separation of concerns and modular development.&#x20;
* Client-Server Architecture: In client-server architecture, the system is divided into two main components: the client, which requests services or resources, and the server, which provides those services or resources. This pattern enables distributed processing and scalability.
* Microservices Architecture: Microservices architecture structures an application as a collection of small, loosely coupled, and independently deployable services. Each service focuses on a specific business capability and can be developed, deployed, and scaled independently. This pattern promotes flexibility, agility, and scalability.
* Event-Driven Architecture: Event-driven architecture (EDA) emphasizes the production, detection, and consumption of events. Events are used to trigger actions or communicate changes between components. EDA enables loose coupling, scalability, and real-time responsiveness.
* Service-Oriented Architecture (SOA): SOA is an architectural approach where functionality is provided as services, which are self-contained and reusable components. Services communicate with each other through well-defined interfaces using standard protocols. SOA promotes modularity, interoperability, and reusability.
* Model-View-Controller (MVC): MVC is a design pattern commonly used in user interface development. It separates the application into three interconnected components: the model (data and business logic), the view (presentation layer), and the controller (handles user input and coordinates between the model and view).
* Repository Pattern: The repository pattern provides an abstraction layer between the data access logic and the rest of the application. It centralizes data access operations and hides the implementation details of data storage, enabling easier maintenance and testability.
* Publish-Subscribe Pattern: The publish-subscribe pattern facilitates communication between components by allowing publishers to send messages (events) to subscribers who have expressed interest in those messages. It supports loose coupling and enables event-driven systems.
* Peer-to-Peer (P2P) Architecture: P2P architecture enables distributed systems where participants can act as both clients and servers, sharing resources and services directly with other participants. P2P systems are decentralized, promoting scalability and fault tolerance.
* Domain-Driven Design (DDD): DDD is an approach that focuses on modeling the domain of a system and aligning the software design with the domain concepts and business requirements. It emphasizes understanding the core business domain and using a common language between domain experts and developers.



## <mark style="color:purple;">Design Trade off --</mark>

* Design trade-offs refer to the compromises and decisions that must be made when designing a system or software application. It involves weighing the advantages and disadvantages of different design choices and selecting the most suitable option based on the specific requirements, constraints, and priorities of the project. Here are some key points to consider when dealing with design trade-offs:
* Performance vs. Scalability: Performance optimization techniques, such as caching or denormalization, can enhance the response time of a system. However, they may impact scalability by increasing resource requirements or complexity. It's important to strike a balance between achieving optimal performance and ensuring the system can handle increasing loads.
* Flexibility vs. Complexity: Adding flexibility and extensibility to a system can enhance its adaptability to future changes and requirements. However, this often comes at the cost of increased complexity and development effort. It's essential to evaluate the trade-off between flexibility and simplicity, considering the anticipated need for future modifications.
* Security vs. Usability: Implementing robust security measures can enhance system protection but may introduce additional user friction or complexity. Striking a balance between security and usability is crucial to provide adequate protection while maintaining a positive user experience.
* Development Time vs. Code Maintainability: Opting for quick development techniques or shortcuts may reduce development time but could impact the maintainability and readability of the codebase. It's important to consider the long-term implications of design choices and ensure the codebase remains manageable and comprehensible for future maintenance and enhancements.
* Cost vs. Performance: Design decisions may have cost implications, particularly when selecting hardware or cloud service options. Higher-performance solutions tend to be more expensive, so it's crucial to evaluate the trade-off between cost and the level of performance required to meet project goals.
* Simplicity vs. Extensibility: Keeping the system design simple can enhance understandability and ease of maintenance. However, extreme simplicity may limit the system's ability to accommodate future enhancements or changes. Striking the right balance between simplicity and extensibility is necessary to avoid over-engineering or excessive complexity.
* Consistency vs. Optimization: Striving for consistency and standardization can improve code maintainability and ease of collaboration. However, it may limit optimization opportunities in specific areas. It's important to evaluate the trade-off between consistency and the potential benefits of tailored optimizations.
* Data Consistency vs. Performance: Ensuring data consistency across distributed systems or in high-concurrency scenarios often comes with a performance cost. Striking a balance between data consistency requirements and performance optimizations is necessary to meet the system's functional and performance goals.
* User Experience vs. Functionality: Prioritizing user experience may involve simplifying or streamlining functionality. Trade-offs between a rich feature set and a user-friendly interface should be evaluated to ensure the application provides a positive user experience without sacrificing essential functionality.
* Time to Market vs. Technical Debt: When facing tight deadlines, it may be tempting to take shortcuts or delay addressing technical debt. However, accumulating technical debt can lead to long-term maintenance issues. Balancing time to market with addressing technical debt is important to ensure a sustainable and maintainable system.



## <mark style="color:purple;">TCP and UDP</mark>

* Both are transport layer protocol
* Tcp --
  * Used in cases when the data is larger and the \[packets are divided into many parts
  * To maintain the order of the packets we use tcp
  * Also it is reliable , in cases when some packets are missing while transferring them in chunks , tcp makes sure that the missing pieces gets resent
  * Tcp creates a two way connection where we send the data in both ways , that is why it is more responsible
  * It uses 3 way connection
    * BUT
    * All these is very expensive as it adds some overhead on top of the existing data
    * It also takes time because of 3 way hand shake
  * Tcp has many application layer such as
    * Http
    * Smtp
* Udp \[ user datagram protocol]
  * The benefit of udp is that we don't need any handshake between the client and server , thus if any part of data packet if not arrive then it is lost forever
  * Also udp dosen't correct the order of the data
  * But we it because it is a lot faster than tcp thus it is used in cases of streaming and gaming as if there is break in connection we don't worry about that
  * Also udp is used in dns
