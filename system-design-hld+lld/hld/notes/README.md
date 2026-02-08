# Notes

## Basic Concepts&#x20;



1. System design concept guide
   * Importance of system design&#x20;
     * Provides high level of understanding of the components and their relationships.
     * Helps in making informed descisions about various tradeoffs.
   * What is system design
     * deals with designing the structure of a software system to ensure that it meets the desired functionality, performance, Scalability, reliability and other quality attributes.
     * It is the process of designing the artitecture, components, data etc. for a system to satisfy functional and non-functional requirements.
     * Overall, system design in an open ended discussion topic. That is why most tech firms prefer to include system design interview rounds.
   * Some key steps involved in designing a system
     * Requirement analysis: Understand functional and non functional requirements
     * High level design: Define the overall structure, Components and their interactions
     * Database design: Designing the structure of the database like defining tables, relationships, indexing strategies, data patterns etc.
     * API design: Designing APIs through which users or other systems interact with the system.
     * Component design: Designing individual components their responsibilities and interactions.
     * Scalability and performance: Ensuring that the system can handle its expected workload efficiency and can scale to accommodate increasing workload demand.
     * Reliability and fault tolerance: Designing the system to minimize the impact of failures and to recover gracefully from errors or faults.
     * Security: Protecting the system from unauthorized access, data breaches and other security threats.
     * Testing: Designing test to verify that the system meets its requirements and performs as expected.
     * Documentations: Documenting the design descisions, artitecture and other relevant information to facilitate understanding , maintainance and future development of the system.
   * Essential concepts used in system design
     * Availability&#x20;
       * system should remain up and return the response of a client request.
       * can be quantified by measuring the percentage of time system remain operational in a given time window.
       * We usually define the system availability in terms of number of 9s&#x20;
       * For designing a highly available system we need to consider factors like replication, load balancing, failover mechanism, disaster recovery strategies etc.
       *

           <figure><img src="../../../.gitbook/assets/image (100).png" alt=""><figcaption></figcaption></figure>
     * Throughput
       * measure the performance and capacity of a system handling tasks , transactions or operations within a given time interval.
       * High throughput is crucial when handling a large data or a high number of concurrent requests.
     * Latency
       * Metric related to system performance.
       * Measures the time taken by a request to receive a response from a server.
         * it can be affected by seped of processing, network congestion, distance between the source and destination and the efficiency of the system components.
         * Lower latency means quick response time, higher system performance and a smooth user experience.
         * reducing latency is important in applications involving real time responsiveness is critical like online gaming, video streaming and financial trading etc.
         * Strategies for reducing latency: optimizing algorithms, improving network infrastructure, using faster hardware components, minimizing unnecessary processing steps etc.
     *   Network Protocols

         * Network acts as platform for communication between users and servers or different servers.&#x20;
         * Protocols on other side are the rules governing how servers or machines communicate over the network. Some common protocols are HTTP, TCP/IP etc.



         <figure><img src="../../../.gitbook/assets/image (101).png" alt=""><figcaption></figcaption></figure>
     * Load balancing&#x20;
       * machines that balance the load among various servers, these are used to prevent single point of faliures.
       * Depending on how these machines are deployed these can be of different types.
       * these distribute traffic, prevent service from breakdown and distribute to the system reliability
       * Acts as traffic managers and help us to maintain system throughput and availability.
     * Proxies
       * Present between client and server. when a client sends a request it passes through the proxy and then reqches the servers.
       * these are of two types&#x20;
         * forward proxy: acts as a mask for the clients and hides the clients iddentity from the server
         * Reverse Proxy: acts as mask for the servers and hides the servers iddentity from the response&#x20;
     * Databases&#x20;
       * associated with data that needs to be stored or later retrieval.
       * One way to classify database is based on their structure which can be relational or non relational.
         * Relational database include MySQL and PostgresSQL enforce strict relationships among data and have a higly structured organization&#x20;
         * Non Relational Database such as cassandra and redis have flexible structure and can store both structured and unstructured data. Often used in system that are highley distributed and require high spped
       * Database partition&#x20;
         * is a way of dividing database into smaller chunks to increase performance of the system.
         * If done properly can improve latency and throughput so that more and more request can be served&#x20;
       * ACID vs BASE
         * Relational and non relational databse ensure different types of compliance.
         * Relational database are associated with ACID while non relation with BASE
         * ACID: Atomicity, Consistency, Isolation, Durability
           * This ensures that transactions are executed in a controlled manner, A transaction is an interaction with the database.
           * Atomicity: A group of operations are treated as a single unit of work, if any of these operation fail , entire transaction will fail. This ensures all or nothing is achieved.
           * Consistency: Data base state changes do not corrupt the data and that transactions can move from one valid state to another
           * Isolation: Transactions are executed independently of each other, without affecting other transactions. This ensures concurrency in database operations.
           * Durability: Data written to the database is persisted and will remain there even in the event of faliure.
         * BASE: Basically available soft state eventual consistency
           * maintains integrity of NoSQL databases and ensures their proper functioning. It is a key factor in the scalability of NoSQL databases.
           * Basically available: Ensures that system is always available
           * Soft state: provides flexibility to the system and allows it to change over time for faster access.
           * Eventual Consistency: ensures that the data in the system takes some time to reach a consistent state and eventually becomes consistent.
       * SQL vs NoSQL
         * one needs to be clear about type of storage according to the system requirements.
         * If system is distributed in nature and scalability is essential then NoSQL databases are the best choice to go with.
         * NoSQL databases when the amount of data is huge
         * SQL databases are favorable when data structure is more important .
         * It is preferred when queries are complex and databases require fewer updates but there is always a tradeoff between NoSQL and SQL databases.
         * Sometimes according to requirements a polyglot artitecture comprising both SQL and NoSQL databases is used to ensure application performance.
     * Scalability in distributed system
       * As service grows number of requests increases, system can become slow and performance may be affected.&#x20;
       * Scaling is the best way to adress this issue. There are two main ways to scale
         * horizontal scaling: Adding more servers to distribute requests.&#x20;
         * Vertical scaling: Increasing the capacity of a single machine by upgrading the resources like CPU, RAM etc.
     * Caching:
       * It helps to improve the performance of a system by reducing its latency by storing frequently used data in a cache so that it can be accessed quickly instead of querying the database
       * But implementing a cache also adds complexity because it is important to maintain consistency between data in the cache and data in the main database.
       * Cache is expensive, so its size is limited.
       * To ensure best performance, cache eviction algorithms like LIFO, FIFO, LRU and LFU
     * Consistent hashing:
       * Widely used concepts in distributed systems because it offers flexibility in scaling. It is an improvement over traditional hashing which is ineffective for handling requests over a network.
       * In this Users and servers are located in a virtual circular ring structure called hash ring. The ring is considered infinite and can accommodate any numbers of servers .
       * This allows for efficient distribution of requests or data among servers. It helps us achieve horizontal scaling which increases throughput and reduces latency.
     * CAP Theorm
       * Concepts for designing networked shared data systems.
       * It states that a distributed data system can provide Consistency, avalability and partition tolerance.
       * One can make tradeoffs between three properties based on the use cases of our DBMS Systems.
         * Consistency:
           * everything should go on in a very well coordinated manner and with proper synchronization.
           * It ensures that systems returns the results such that any read operation should give the most recent write operation
         * Availability:
           * System is always available whenever the request is made.
         * Availability:
         * Partition Tolerance:
           * necessary for any distributed system, so we always need to choose between availability and consistency.
           * It corresponds to the conditional that the system should work irrespective of any node faliure or network faliure.
     *   Some other concepts

         * Server-sent events
         * Long polling&#x20;
         * Client server architecture
         * Web sockets
         * Load balancing algorithms
         * Peer to Peer networks
         * Master Slave replication&#x20;
         * Throttling and rate limiting&#x20;
         * How to choose the right database
         * Leader election in distributed systems


2. System design interview guide
   * These are open ended conversations where candidates and interviewer discuss to reach a solution.
   * Step 1: Requirements Clarifications
     * Ask question and clarify any ambiguities early in the interview process.
     * We can ask&#x20;
       * who will be using the system?
       * How will they be using it?
       * How many users are there?
       * What does the system do?
       * What are the inputs and outputs of the system?
       * How much data is expected to be handled?
       * How many requests per second are expected?
       * What is the expected read-to-write ratio?
   * Step 2: Back of the envelope estimation
     * It is important to understand the system scale.
     * One need to consider the factors like&#x20;
       * read-to-write ratio,&#x20;
       * number of concurrent requests,&#x20;
       * number of monthly users,&#x20;
       * number of daily active users ,&#x20;
       * required storage capacity,&#x20;
       * bandwith requiremtns etc
   * Step 3: Create a high level design
     * identify all critical components and attempt to draw a digram representing the interactions of these components to fulfill both functional and non functional requirements.
     * these components include&#x20;
       * different client
       * backend services
       * data stores&#x20;
       * cache systems
       * load balancers
       * CDNs&#x20;
       * message queues
       * batch processing systems&#x20;
     * By out linging the interaction of key components one can better explain the overall structure of the system and how various components will collaborate to achieve the desired results.
       *

           <figure><img src="../../../.gitbook/assets/image (102).png" alt=""><figcaption></figcaption></figure>
   * Step 4: Database design
     * defining how data will be processed is a crucial
     * one need to understand type of data stored, relationships between different types of data , expected workload ( read-heavy, write heavy or balanced)
     * [https://www.enjoyalgorithms.com/blog/sql-vs-nosql/](https://www.enjoyalgorithms.com/blog/sql-vs-nosql/)
   * Step 5: Design Core components
     * Once all high level structure has been discussed then we discuss the major components in more detail.
     * At this stage we get the we might get some inputs from the interviewer.
     * For ex-- if we were asked to design a tiny URL then&#x20;
       * Generating and storing a hash of the full URL
         * MD5 and Base62
         * Hash Collisions
         * SQL and NoSQL
         * Database Schema
       * Translating a hashed URL to full URL
         * Database lookup
         * API and Object-oriented design
   * Step 6: Scale the dsign
     * to meet the scale requiremnts we can use ideas like
       * load balancing&#x20;
       * horizontal scaling&#x20;
       * caching&#x20;
       * database sharding
       * replication etc
   * Step 7: Identifying and resolving bottlenecks
     * One can discuss following bottlenecks
       * &#x20;Is there any single point of faliure? what steps can be taken to mitigate this?
       * Are there enough replicas of data to ensure that users can still be served if a few servers are down?
       * Are there enough copies of different services running to ensure that a few faliures will not cause a total system shutdown?
       * How is the performance of the service being monitored? are there alerts in place to notify the team when a critical components fail or their performance degrades?
   * System design questions
     * Key value store
     * typehead system design
     * Pastebin system design
     * Designing API rate limiter
     * designing web crawler
     * Youtube system design
     * Designing Google Docs
3. Network Protocols
   * Group of computers connected through communication channels for resource sharing, data exchange and various types of communication.
   * these devices communicate with each other following rules known as network protocols.
   * Why do we need protocols?
     * these define the format of data packets so that devices can process transmitted data correctly
     * Specify how devices can exchange messages: sequence of messages , types of message&#x20;
     * provide a way to route data packets , how devices are identified how address are assigned how routing descisions are made t send data to desired destination.
     * safety in data transmission,&#x20;
     * implement flow control to manage the rate of data transmission
     * features to detect and correct errors during data transmission
     * Protocols are designed to scale well with growth in the network size.
   * OSI Model
     * divides whole system into 7 different layers, where each layer has specific responsibilities and each layer interacts with adjecent layers to fecilitate communication.
     * Seven layers of OSI Model:
       * Application
         * Top layer&#x20;
         * Fecilitate comm. b/w software application or services runnign on different devices over network
         * ex -- HTTP, SMTP, FTP
       * Presentation:
         * Responsible for data formatting, encrypting and compression.
       * Session:
         * Establish reliable and efficient data transfer.&#x20;
         * It segments the data received from upper layers into smaller units (data packets) and provides error recovery, flow control and congestion control
         * Ex-- TCP and UDP
       * Network
         * determine the best path for the data packets to reach their destination across different networks, using routing protocols and ip adressing
       * Data link layer
         * Provide reliable and error free transmission of data over physical layer.
         * It is responsible for establishing and terminating connections between network nodes, performing error detection and correction and handling the flow of data frames between adjacent node.
       * Physical layer
         * deals with physical transmission.
         * defines the electrical, mechanical and physical aspects of the network like cables, connectors and signaling methods.
   * Types of netowk protools&#x20;
     * each protocol has a different purpouse and operate in different layers of OSI model. These are:
       * Internet protocol(IP):
         * Network layer protocol
         * helps data packets to reach their destination.
         * when one machine wants to send data to another machine, it sends it in the form of IP packets
         * An IP packet is fundamental data unit that is transmitted over any network that uses IP.
         * It has 2 key parts:
           *

               <figure><img src="../../../.gitbook/assets/image (96).png" alt=""><figcaption></figcaption></figure>
           * IP header
             * contains info about the packet like source destination IP adress, size of packet, version of IP and other control info
           * IP data (payload)
             * It contains the actual data being transmitted.&#x20;
         * What happens&#x20;
           * When device sends a packet it first determines the IP adress of the destination device, Packet is then sent to the first hop on the network i.e. a router.
           * The router examines the destination IP address in the header and determines the next hop on the network to which the packet should be forwarded.
           * This process continues till the packet reaches its final destination.
           * Here routing algos are used to determine best path
         * What is an IP adress?
           * digital address that helps us identify and locate a device on a network.
           * unique and assigned to each device or domain that connects to the internet.
           * Written as four numbers separated by periods, these represent numerical value of the address.
         * Types of IP address:
           * IPv4&#x20;
             * First version of IP
             * Uses 32 bit address scheme, so it can accommodate 2^31 distinct IP address
             * Still carries major chunk of internet traffic
             * also provides benefits like encrypted data transmission and cost effective data routing.
           * IPv6
             * developed to overcome the limitation of IPv4
             * uses 128 bit address space
             * improved routing, packet processing and security features compared to IPv4.
             * But IPv6 is not compatible with IPv4 and upgrading can present a challenge.
       * Transmission control protocol (TCP)
         * Used to send large data. The data is divided into multiple packets.
         * But increase in packets increases the risk of packets not reaching the destination or arriving in the wrong order, TCP helps us in mitigating this
         * TCP is transport layer protocol built on top of the IP, it is used in conjunction with the IP to provide a complete suite of protocols for transmitting the data .
         * How it works
           * Breaks data into small packets and numbers them in sequence to ensure that they are received in the correct order at the receiver.
           * If there is any loss of packet TCP can retransmit it.
           * **So IP is responsible for delivering the packet while TCP helps to put them back in correct order**&#x20;
           * when a device wants to communicate with another device using TCP, it establishes a connection with the destination device through a sequenced acknowledgement
4. Process management in OS
5. Latency of a system
6. Throughput of a system
7. Availability of a system
8. Distributed system Concepts
9. Client server architecture
10. Peer-to-peer Architecture

## Advanced concepts



1. Load balancers&#x20;
2. Load balancing algorithms
3. Forward and reverse proxy
4. What is a websocket
5. Server sent events
6. http long pooling
7. Consistent hashing
8. Throttling and rate limiting&#x20;
9. Publisher subscriber pattern
10. Leader election

## Database concepts



1. Fundamentals of caching
2. Storage and redundancy
3. Database partition&#x20;
4. Master slave replication&#x20;
5. SQL vs NoSQL database
6. Key-Value Database
7. Graph Database
8. SQL for Data Science

## Basic interview concepts



1. Design LRU Cache
2. Design LFU Cache
3. Design Bloom filter
4. Design key value store
5. Paste bin system design
6. Typeahead System design
7. URL shortener system design
8. Design Notification Service
9. Design QR code generator
10. Design API rate limiter

## Advanced interview concepts



1. Web crawler system design
2. You tube system design
3. WhatsApp system design
4. Google Docs system design
5. Instagram system design
6. Twitter system design
7. Dropbox system design
8. Yelp system design
9. Uber system design
10. Recommender system
