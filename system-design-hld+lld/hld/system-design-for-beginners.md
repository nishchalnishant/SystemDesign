# System design for beginners

## Why study System design

* When we are working on a simple project we do some calculations in the backend server an perform CURD operations in the database to return a response
* But in real world we have actual users and this simple artitecture would not work, one need to think about scaling, fault tolerance, security monitoring adn all sort of things to make our systems reliable and work efficiently in all cases.
* For this we study different concepts in system design
* Career Progression: It's a fundamental requirement for advancing to senior roles like Senior Engineer, Tech Lead, and Architect. Mastery of system design signals your readiness to take on more responsibility.
* Shift from Coder to Architect: System design shifts your focus from writing lines of code to architecting entire systems. It trains you to think about the "big picture" and how different components interact to create a cohesive whole.
* Develop Problem-Solving Skills: You learn to analyze complex problems and break them down into smaller, manageable parts. This involves understanding requirements, identifying constraints, and designing a solution that meets the specified needs.
* Understand Scalability and Performance: A primary goal of system design is to build systems that can handle growth. You learn how to design for scalability, ensuring the system remains performant and reliable as user load increases. This is a critical skill for any modern application.
* Make Informed Technical Decisions: System design is all about trade-offs. You learn to evaluate different technologies and architectural patterns (e.g., SQL vs. NoSQL, monolith vs. microservices) and choose the best approach based on the specific requirements of the system.
* Improve Communication and Collaboration: System design provides a common language and framework for discussing complex technical concepts with your team. This leads to more productive discussions, better alignment, and a shared understanding of the system's architecture.
* Reduce Long-Term Costs: A well-designed system is easier and cheaper to maintain and evolve over time. By making good design decisions upfront, you can avoid costly refactoring and reduce technical debt in the long run.
* Build Resilient and Fault-Tolerant Systems: You learn techniques to design systems that are resilient to failures. This includes concepts like redundancy, replication, and graceful degradation, which are essential for building highly available applications.

## What is a server

* A server is nothing but a physical machine (like our laptop) where the code is running. When we build a ReactJS and Node JS application then the appication runs on local host
* The Localhost is the domain name which resolves to the IP adress 127.0.0.1, which is the IP adress of the local laptop.
* For external websites when we hit www.abc.com following happens:
  * abc.com goes to the DNS resolver to find the IP adress of the server which corresponds to this domain. Each server has an IP adress. Its logical adress which is unique for each device.
    *   #### The DNS Resolution Process: A Step-by-Step Guide

        1. Browser and OS Check:
           * Your web browser first checks its own cache to see if it has recently visited the site. If the IP address is found, it uses it directly.
           * If not found in the browser cache, your computer's operating system (OS) checks its cache.
        2. The Recursive Resolver (Your ISP):
           * If the IP address is not found locally, your computer sends a query to a Recursive DNS Server, which is usually operated by your Internet Service Provider (ISP).
           * The recursive resolver acts as a middleman. Its job is to find the correct IP address by asking other DNS servers. It also has a cache to store recent queries to speed things up.
        3. Querying the Root Servers:
           * If the recursive resolver doesn't have the IP in its cache, it starts a journey by asking one of the 13 root DNS servers that are spread across the globe.
           * The root server doesn't know the IP address for `www.google.com`, but it knows where to find the servers that handle the `.com` Top-Level Domain (TLD). It responds by pointing the resolver to the TLD Name Server for `.com`.
        4. Querying the TLD Name Servers:
           * The recursive resolver then sends a query to the `.com` TLD Name Server.
           * The TLD server doesn't have the final IP address either, but it knows which server is responsible for the `google.com` domain. It directs the resolver to the Authoritative Name Server for `google.com`.
        5. Querying the Authoritative Name Servers:
           * Finally, the recursive resolver queries the Authoritative Name Server for `google.com`. This server is the final authority for this domain. It holds the actual DNS records.
           * The authoritative server looks up the record for `www.google.com` and finds its corresponding IP address. It sends this IP address back to the recursive resolver.
        6. Response and Caching:
           * The recursive resolver receives the IP address and sends it back to your computer's operating system.
           * Your OS then passes it to the browser.
           * The recursive resolver caches this IP address for a specific amount of time (called Time To Live or TTL) so it can respond faster the next time someone asks for `google.com`.
        7. Making the Connection:
           * Your browser now has the correct IP address. It uses this address to establish a direct connection with the web server hosting `www.google.com` and begins to load the webpage.

        #### Key DNS Record Types

        The Authoritative Name Server stores different types of records. The most common are:

        * A Record (Address): Maps a domain name to an IPv4 address (e.g., `172.217.168.196`).
        * AAAA Record: Maps a domain name to an IPv6 address.
        * CNAME Record (Canonical Name): Forwards one domain to another domain (e.g., `shop.domain.com` might point to `domain.com`).
        * MX Record (Mail Exchange): Directs email to a mail server.
        * NS Record (Name Server): Points to the authoritative name servers for a domain.
  * The browser gets the IP adress of the server, with this IP adress the browser requests the server (the machine).
  * Now when the server gets the request, if there are multiple applications running (like on our laptop there are multiple applications runnings at the same time, such as Chrome, netflix etc), the server finds the correct application with the help of port then it returns the response.
  * On server side there can be multiple applications running like authentication, logic payment etc, based on the request the request is routed to the required port or IP.
*

## Latency and through put

* latency --
  * it is the time taken for a single request to travel from the client to the server and back, calculated in milliseconds.
  * ex — if it takes 200 ms for the server to send the page data back to the browser then the latency is 200ms
  * round trip time can also be used as a replacement for latency
* Throughput --
  * it is the number of requests or unit of work the sustem can handle per second. typically measured in requests per second.
  * Each server has a limit which means it can handle x number of requests per section more load than this it may go down.
* Scaling and its types
  * scaling means we need to increase the specs of our machine like ram, cpu storage etc.
  * 2 types of scaling
    * horizontal scaling&#x20;
      * As vertical scaling is not possible beyond a point we can't infinietly increase the specs of the machine as we will hit a bottlenect
      * Solution is to add more machine and distribute the incoming trarric/load.
      * to distribute the load between multiple machines we use load balances which has ips of all the machines connected to it and distributes the traffic intelligently among the machines.
      * so the clients make requests to the load balancer and decides where to send the requests
    * vertical scaling&#x20;
      * if we increase the specs (RAM, Storage, GPU) of the same machine to handle more load then it is called vertical scaling
      * Mostly used in SQL database and also in stateful application as it is difficult to maintain consistency of states in horizontal scaling setup
* Auto scalling
  * when the load to our server increases we have a mechanism which launchs another instance that distributes the traffic without us manually doing this.&#x20;
  * This changing numbers of servers dynamically based on the traffic is called auto scalling.
* Back of the envelope estimation
  * in back of envelope estimation we estimate the number of servers storage etc needed dependeing on an. estimated amount of load.
  * We can calculate a lot of things but we can estimate these
    * load estimation
      * Based on DAU calculate the number of reads and number of writes.
    * storage estimation
      * total storage required depending on the amount of data uploaded by the users
    *   resource estimation

        * number of CPUs and servers required



## CAP theorm

* Very important tradeoff while designing any system in terms of distributed systems .&#x20;
* It states that in a distributes systems one can guarantee two out of these three properties simultaniously it is impossible to acheive all three.&#x20;
* CA, AP, CP
* this is very practical as in a distributed system the network partition is bound to happen so the system should be partition tolerant, meaning the p will always be there , we would have to make a tradeoff between CP and AP.
* It is of 3 words
  * Consistency
    * every read request return the same result irrespective of whichever node we are reading from&#x20;
    * this means all the nodes have the same data at the same time
  * availability
    * the system is available and always able to respond to requests even if the nodes fail. this means that even if some node faliure occur the system shoudl continue serving requests with other healthy nodes.
  * partition tolerance
    * the system continues to operate even if there is a communication breakdown or network partition between different nodes.

## Scaling of the database

* usually we have one database server, the application server queries from this DB and gets the result.&#x20;
* But at a certain scale the server starts giving slow response or may go down, to mitigate this we need to scale the database
* There are various methods to scale the database and one can use various methods to mitigate this like
* Hierarchy one should follow for database scaling — indexing > partitioning > vertical scaling > even then if the db is slow then we replicate the data into multiple servers instead of one (Master-slave artitecture
* First, always and always prefer vertical scaling. It's easy. You just need toincrease the specs of a single device. If you hit the bottleneck here thenonly do the below things.
* When you have read heavy traffic, do master-slave architecture.
* When you have write-heavy traffic, do sharding because the entire datacan’t fit in one machine. Just try to avoid cross-shard queries here.
*   If you have read heavy traffic but master-slave architecture becomes slower not able to handle the load, then you can also do sharding and distribute the load. But it generally happens on a very large scale.

    *

        ### 1. Vertical Scaling (Scaling Up)

        Vertical scaling involves adding more resources (CPU, RAM, storage, etc.) to a single database server.

        * Method: Upgrade the hardware of the existing server to increase its capacity.
          * Pros: It is generally simpler and faster to implement, and it maintains the simplicity of a single database instance (no distributed system complexity).
          * Cons: It's limited by the maximum capacity of a single machine's hardware, and it creates a single point of failure.

        ### 2. Horizontal Scaling (Scaling Out)

        Horizontal scaling involves adding more servers (nodes) to distribute the database workload and data. This is typically required for systems with massive traffic or data volume that a single server cannot handle.

        #### Replication

        Replication is primarily used to scale read operations and increase high availability (fault tolerance).

        * Master-Replica (or Primary-Secondary) Architecture: The primary server handles all write operations, and the data is copied to one or more replica servers. The replicas handle the majority of read queries.
          * _Benefit:_ Offloads read traffic from the primary, improving read performance. Provides redundancy.
          * _Drawback:_ Writes are still limited by the primary server. Data consistency issues (replica lag) can occur.

        #### Sharding

        Sharding is a technique to scale write operations and massive data volume by dividing the dataset across multiple independent database servers (shards).

        * Method: The entire dataset is split into smaller, more manageable parts called shards, and each shard is hosted on a separate server.
        * Benefit: Distributes both read and write load across many machines, allowing the system to grow virtually limitless in capacity. Improves query performance by scanning less data per query.
        * Drawback: It is significantly more complex to implement and manage. Cross-shard queries can be slow, and a poor choice of a "shard key" can lead to uneven data distribution (hot shards).
    * Partitioning --
      * means breaking the big table into multiple small tables&#x20;
      * the table are kept in the same database server each divided in to multiple tables&#x20;
      * Benefits of partitioning&#x20;
        * when index files are large it sharts showing some performance issues while searching on the large index files .
        * After partitioning each table has its own index, so searching on smaller tables is faster.
        * behind the scenes the db knows which partition to look for the data.



    *   ### 3. Optimizations and Other Strategies

        These methods work in conjunction with vertical or horizontal scaling to reduce the load on the database.

        * Caching:&#x20;
          * Store frequently accessed data in a faster, in-memory store (like Redis or Memcached) instead of hitting the database for every request.&#x20;
          * This is highly effective for scaling read-heavy applications.
        * Indexing:&#x20;
          * Create indexes on frequently queried columns to speed up data retrieval.&#x20;
          * A well-placed index can drastically reduce query time.
          * with indexing the database uses the index to directly jump to the rows one need making it much faster.
          * One has the Id column then the database will make a copy of that id column in a data structure (B-trees). It uses the B-trres to search the specific id. Searching is faster here as the IDs are stored in a sorted way such that one can apply a binary search kind of a thing to search in O(logN).
          * To enable indexing in any column, one just needs to add one line of syntax on all the overhead of creating b-trees etc are handled by DB.&#x20;
        * Query Optimization:&#x20;
          * Analyze and tune slow-running database queries to make them more efficient.&#x20;
          * This involves rewriting SQL, restructuring the schema, or adjusting indexes.
        * Denormalization:&#x20;
          * Intentionally introduce controlled redundancy in the database schema to reduce the need for expensive JOIN operations, which can be a performance bottleneck for read-heavy workloads.
        * Connection Pooling:&#x20;
          * Efficiently manage and reuse database connections to minimize the overhead of establishing and closing new connections for every transaction.
        * Materialized Views:&#x20;
          * Pre-calculate the results of complex, time-consuming queries and store them as a static table (the materialized view).&#x20;
          * The application then queries this view instead of executing the full query, which is then periodically refreshed.



## SQL vs NoSQL database and when to use which database

* SQL db&#x20;
  * data stpred in tables&#x20;
  * predefined schema, meaning the structure of the data must be defined before inserting data.
  * Follows ACID proberties ensuring data integrity and reliability
  * ex — MySQL , PostresSQL, Oracle, SQL server
* NoSQL db
  * Categorised into 4 types&#x20;
    * Document based
    * Key value based
    * Column family stores
    * Graph databases
  * Flexible schema, meaning one can insert new data types of fields which many not be defined in the initial schema
  * Not follows ACID and priorities other factors such as scalablity and performance.
* Scaling in SQL Vs NoSQL db&#x20;
  * SQL is designed to scale vertically while NoSQL db scale horizontally&#x20;
  * Generally Sharing is done in NoSQL DBs to accomodate large amounts of data
  * Sharedin can also be done in SQL DB but generally, we avoid it as we use SQL DB for ACID but ensuring data consistency becomes very difficult when data is distributed across multiple servers and querying data by joins across shards is also complex and expensive&#x20;
* When to use which database
  * When data is unstructured and want to use flexible schema, go withNoSQL.Ex: reviews, recommendation of e-commerce app
  * When data is structured and has a fixed schema, go with SQL.Ex: customer accounts table of e-commerce app
  * If you want data integrity and consistency, go with SQL DB because itmaintains the ACID property.Ex: Financial transactions, account balances of banking appOrders, payments of e-commerce app.Stock trading platforms.
  * If you want high availability, scalability (means storing large amounts ofdata which doesn’t fit in one server), and low latency, go with NoSQLbecause of horizontal scalability & sharding.Ex: Posts, likes, comments, messages of social media app.Store large amounts of real-time data, such as the driver location of adelivery app.
  * When you want to perform complex queries, joins, and aggregations, gowith SQL. Generally, we have to do complex queries, joins, etc, whenperforming data analytics. Store required data for those in SQL.

## Microservices

* Monolith — eantire application is built as a single unit is called as  monolith
* Microservices — Break down large applications into smaller managebale and independently deployable services.
  * ex — For e commerce app we break it into follwoing services&#x20;
    * User services
    * Product services
    * Order services
    * Payment services
* Why do we break our app into microservices
  * if one componenent has a lot of traffic and requires a lot of resources then one can scale each other independently
  * Flexibiltiy to choose tech stack. In monolith whole backend is written in one single language, but in microservices one can write different services into different stacks&#x20;
  * Failure of one service doesn't necessarily impact others.&#x20;
* Load balancer deep dive
* Caching
* Blob storage
* Content delivery Network(CDN)

## Message broker

* these are needed in asynchronous programming meaning when a client sends a request and it takes some time to complete .
* In this case we don't directly assign the task to the worked we place a meesage broker in between which acts like a queue, the server puts the task as a message in the queue and processes it.
* Why do we put a message broker in between&#x20;
  * It gives reliability suppose the producer goes down still workers can function without any problems
  * it also gives the retry feature. Suppose the worker fails to process in between then it can retry after some time as the message is still present in the message broker
  * it makes the system decoupled. producers and consumers can both do the task at their own pace and they are not dependent on each other
* When do we use message brokers
  * &#x20;we have two microservices, the two common ways to communicate between these are through rest APIS and message broker&#x20;
  * We use message broker when&#x20;
    * the task is non critical m meaning we can afford to send them with some delay
    * the task takes some time to complete
* These are of two types&#x20;
  * message queues — RabbitMQ, AQW SQS
    * queue where the producer puts the message from one side and consumer pulls it from another&#x20;
    * only thing different between message queue and message stream is that a message queue can only have one kind of consumer for one type of message
  * message streams — Apache Kafka , AWS Kinesis
    * in this for one message we can have more that one type of consumer&#x20;
    * We use this when we have write to one and read by many
    * in message streams instead of deleting the message once it has been processed by one of the consumer it iterates over the message . in this way these messages are only processed once by any type of consumer service.&#x20;
    * For deletion these are never deleted it lies there forever one can manually delete it or set an expiry time but consumer services can't delete the message in message streams.

## Apache Kafka deep dive

* kafka is usesd as a message stream, it has high throughput meaning one can dump a lot of data simultaneously in kafka and it can handle it without crashing
* ex — uber tracking diver location at each 2 sec, get the driver location and insert it, if there are lot of driver and we do a db write every 2 sec then db might go down. But we can put that data every 2 sec in kafka as the throughput is very high .After each 10 min the consumer will take those bulk data from kafka and write them into DB. In a way we perform operation in DB every 10 minutes not 2 sec
* Kafka internals
  * Producers — publishes a msg to kafka. As for sending email the producer can send {"email","message"} to the kafka
  * Consumers — Subscribes to the kafka topics and processes the feed of messages
  * Brokers — Kafka server that stores and manages the topics
  * Topic — A category/feed name to which records are published, send email can be a topic, writelocationtoDB can be a topic
* Ex — A database
  * Broker — Database server
  * Topics — Tables&#x20;
  * Partition — each topic is divided into partitions for parallelism, partition is similar to sharding in DB tables. On what basis do we do partition For that we have to decide and code it outself. Lets say our SendNotification topic we partition it based on location north indian goes to partition 1 south indian goes to parititon 2&#x20;
  * Consumer groups — when we make a consumer that subscribes to a topic we have to assign a group to the consumer. Each consumer within a gorup does one type of processing from a subset of partitions.

## Realtime pub-sub

* In Message Broker, whenever a publisher pushes a message to the messagebroker, it remains in the broker until the consumer pulls it from it via APIs
* In Pubsub, as soon as the publisher pushes a message to the Pubsub Broker,it immediately gets delivered to the consumers who are subscribed to this broker.&#x20;
* Here we don't have any API call or anything , but the message automatically gets delivered to the consumer in realtime by the PubSub broker.
* The emssages are not stored/retained in pubsub&#x20;
* Ex — Redis&#x20;
  * In addtion to caching redis can be used for Pubsub as well.
  * Pubsub can be used to build low latency application like real-time chatting application. For chatting application we use websocket connnection but in a horizontally scaled environment there can be many servers connected to different client
  * If client wants to send a message to client-3 he can't send it direclt as theclient -3 is not colelcted to server 1.
  * to do this we have a redis pubsub
* Event driven Artitecture
  * suppose we are building an e-commerce platform like amazon, when user purchases an item he sends a request to the order service. The order service verifies the item and calls payment service for payment.
  * if payment is sucessful then user is redirected to sucess page, 2 apis are called which are an inventory service to update the inventory count of the product and a notification service to send an email to the user for this order.
  * Now one can se that inventory and notification service have nothing to do with the sucess page, it doesnot send any reponse to the client
  * So the client doesn't need to wait for their response, these are non critical tasks that can be done asynchronously
  * We can put the task of order success info in the message broker and let the inventory and notification service consume it asynchronously without making the client wait
  * this is event driven architecture where the producer puts the message called event in the message broker and forgets about it&#x20;
  * now its consumer duty to process it.
  * Why EDA --
    * Decoupling — producers don't need to know about consumers, in above example without using EDA it was very tightly coupled if any of the system goes down then it would have affected the whole system
    * Resilience - Faliure of one componenet doesn't block others
    * Scalability — each service can be scaled horizontally without affecting each other .
  * Types of EDA patterns  (only first 2 are being used)
    * simple event notification
      * Here the producer only notifies that an event has occured.
      * Consumers need to fetch the additional details if required.
      * in this the producer only notifies that an event occured, the consumers need to fetch additional details if required.
    * event carried state transfer
      * it is the same as the simple event notification pattern, the only difference here is that the producer sends all the necessary details as part of the event in the message broker so consumers don't need to fetch anything from the database or the external source.
      * Advantage : reduced latency because consumers don't need to make any extra network calls to fetch additional infomration
      * Disadvantage — events are larger in size which could increase broker storage and bandwidth costs.
    * Event sourcing
    * Event sourcing with CQRS (command query responsibility segregation)
* Distributed system&#x20;
* Auto recoverable system using leader election
* Big Data tools
* Consistency deep dive

## Consistent hashing

* Suppose we have a key-value distributed database and we don’t useconsistent hashing then we will follow the following simple approach to findwhich database server a particular key belongs
  * Hash the key with any hash function (such as SHA256, SHA128, MD5,etc).
  * Then, take Mod of that with the number of servers to find its belonging.ata redundancy and data recovery
* Let's the number of servers is 3.key1 belongs to => (Hash(key1) % 3) server
* This approach is perfectly fine when the number of servers is fixed. Thismeans we don’t have an auto-scaling or a dynamic number of servers.
* When the number of servers becomes dynamic, then problems occur.
* If we use a consistent hashing method to find which key belongs to which server, then the data movements will be minimal when the number of servers changes.
* _How Consistent Hashing used to find which data belongs to which node?_
  * We take the server identity (such as IP or ID) and pass it to a hash function such as SHA128. It will generate some random number between\[0, 2¹²⁸).
  * For visualisation, we will place it in the ring. Divide the ring into \[0, 2¹²⁸),i.e., the range of hash. Whatever the number comes by passing theserver\_id to the hash function, place that server on that place of the ring.
  * Similarly, do the above thing for the keys also. Divide the circle in thesame range as the hash function, then pass the key to the hash function,and whatever the number comes, place the key at that position of thering.
  * Any key will go to its nearest clockwise server. In the above example:
    * key-1 and key-4 belongs to Node-1
    * key-3 belongs to Node-2
    * key-2 and key-5 belongs to Node-3
  * Suppose Node-2 is removed from the above setup, then the key-3 will go toNode-1, and no other keys will be affected.&#x20;
  * This is the power of consistent hashing. It makes the minimum movement of keys.&#x20;
  * But it is not the job of consistent hashing to do this movement. You need to do it yourself.Consistent hashing is just an algorithm that tells which key belongs to which node.
* Use of consistent hashing — dsitributed database such as AWS DynnamoDB apache cassandra use it internally.
* Data redundancy and data recovery&#x20;
  *

## Proxy

* A proxy is an intermediary server that sits between a client and anotherserver.
* There are two kinds of proxy:
  * 1\. Forward Proxy
    * acts on behalf of the client , when a client makes a request this request goes through a forward proxy.
    * The server doesn't know about the client's identitt. The server will only know the forward proxy IP
    * Main features — hides the client, the server only sees the forward proxy not the client.
    * Use cases --
      * Clients use forward proxies to access restricted content (e.g., accessing geo-blocked websites).
      * It is also used for caching. Forward proxy can cache the frequently accessed content so the client won’t have to make a request to the server.Content is directly returned from the forward proxy.
      * Organisations (such as companies, colleges etc) sets up a forward proxy to control employees’ internet usage by filtering certain websites.
    * Flow:
      * Client requests www.abc.com.
      * Forward Proxy receives the request and forwards it to the server.
      * The server sends the response back to the forward proxy.
      * Forward proxy sends the response to the client.
  * 2\. Reverse Proxy
    * A reverse proxy acts on behalf of a server.&#x20;
    * Clients send requests to the reverse proxy, which forwards them to the appropriate server in the backend.&#x20;
    * The response goes back through the reverse proxy to the client.
    * In forward proxy, servers don’t know the client’s identity, but in reverse proxy, the client doesn’t know about the server.&#x20;
    * They send the request to reverse proxy. It is the job of the reverse proxy to route this request to the appropriate server.
    * Main Feature: Hides the server. The client only sees the reverse proxy, not the server.
    * Use Cases:
      * Load Balancer, as you saw above.
      * SSL termination: Handles encryption and decryption, reducing serverworkload.
      * Caching: Stores static content to reduce server load.
      * Security: Protects backend servers from direct exposure to the internet.
    * Flow:
      * Client requests www.abc.com.
      * The reverse proxy receives the request.
      * Reverse proxy forwards the request to one of several backend servers.
      * The server sends the response back to the proxy.
      * Proxy sends the response to the client.
      * Example of Reverse Proxy: Ngnix, HAProxy
