# HLD theory question

## 1. What is CAP theorem?

CAP(Consistency-Availability-Partition Tolerance) theorem says that a distributed system cannot guarantee C, A and P simultaneously. It can at max provide any 2 of the 3 guarantees. Let us understand this with the help of a distributed database system.&#x20;

* **Consistency**: This states that the data has to remain consistent after the execution of an operation in the database. For example, post database updation, all queries should retrieve the same result.
* **Availability**: The databases cannot have downtime and should be available and responsive always.
* **Partition Tolerance**: The database system should be functioning despite the communication becoming unstable.

The following image represents what databases guarantee what aspects of the CAP Theorem simultaneously. We see that RDBMS databases guarantee consistency and Availability simultaneously. Redis, MongoDB, Hbase databases guarantee Consistency and Partition Tolerance. Cassandra, CouchDB guarantees Availability and Partition Tolerance. [**Complete Video Tutorial**](https://www.youtube.com/watch?v=8UryASGBiR4).

<figure><img src="https://d3n0h9tb65y8q.cloudfront.net/public_assets/assets/000/002/781/original/CAP_theorem.png?1644591573" alt="" width="563"><figcaption></figcaption></figure>



## 2. How is Horizontal scaling different from Vertical scaling?

* **Horizontal scaling** refers to the addition of more computing machines to the network that shares the processing and memory workload across a distributed network of devices. In simple words, more instances of servers are added to the existing pool and the traffic load is distributed across these devices in an efficient manner.
* **Vertical scaling** refers to the concept of upgrading the resource capacity such as increasing RAM, adding efficient processors etc of a single machine or switching to a new machine with more capacity. The capability of the server can be enhanced without the need for code manipulation.

This has been demonstrated in the image below:

<figure><img src="https://d3n0h9tb65y8q.cloudfront.net/public_assets/assets/000/002/758/original/horizontal_scaling_different_from.png?1644588019" alt="" width="563"><figcaption></figcaption></figure>

**Horizontal scaling vs. Vertical scaling:**

| Category              | Horizontal Scaling                                                                                                                                                                      | Vertical Scaling                                                                                                                                                                             |
| --------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Load Balancing        | Requires load balancing for distributing request traffic across multiple machines.                                                                                                      | Since there is just one single machine, the load balancer is not required.                                                                                                                   |
| Failure Resilience    | This is more resistant to application failure because if one server fails, traffic is routed to other servers.                                                                          | This is more prone to failure as there is only one machine and failure of this results in failure of the entire application.                                                                 |
| Machine Communication | Since there are multiple machines being involved, it is very much necessary to have network communication.                                                                              | Vertical scaling makes use of inter-process communication within the machine which makes it quite fast.                                                                                      |
| Data Consistency      | There exist possibilities of data inconsistencies here because there are different machines for handling different requests which might result in data being out of sync.               | As there is only one machine, there is no issue of data inconsistency.                                                                                                                       |
| Limitations           | Since this scaling requires multiple servers, there might be concerns on budget and space but the scaling of the application can be done as much as needed based on the business needs. | Vertical scaling has a limit on the capacity of the resources that are achievable. If the resources are scaled up above this limit, then the application might crash and result in downtime. |
|                       |                                                                                                                                                                                         |                                                                                                                                                                                              |



## 3. What do you understand by load balancing? Why is it important in system design?

Load balancing refers to the concept of distributing incoming traffic efficiently across a group of various backend servers. These servers are called server pools. Modern-day websites are designed to serve millions of requests from clients and return the responses in a fast and reliable manner. In order to serve these requests, the addition of more servers is required. In such a scenario, it is essential to distribute request traffic efficiently across each server so that they do not face undue loads. Load balancer acts as a traffic police cop facing the requests and routes them across the available servers in a way that not a single server is overwhelmed which could possibly degrade the application performance.

<figure><img src="https://d3n0h9tb65y8q.cloudfront.net/public_assets/assets/000/002/759/original/load_balancing.png?1644588072" alt="" width="563"><figcaption></figcaption></figure>

When a server goes down, the load balancer redirects traffic to the remaining available servers. When a new server gets added to the configuration, the requests are automatically redirected to it. Following are the benefits of load balancers:

* They help to prevent requests from going to unhealthy or unavailable servers.
* Helps to prevent resources overloading.
* Helps to eliminate a single point of failure since the requests are routed to available servers whenever a server goes down.
* Requests sent to the servers are encrypted and the responses are decrypted. It aids in SSL termination and removes the need to install X.509 certificates on every server.
* Load balancing impacts system security and allows continuous software updates for accomodating changes in the system.



## 4. What do you understand by Latency, throughput, and availability of a system?

Performance is an important factor in system design as it helps in making our services fast and reliable. Following are the three key metrics for measuring the performance:

* **Latency**: This is the time taken in milliseconds for delivering a single message.
* **Throughput**: This is the amount of data successfully transmitted through a system in a given amount of time. It is measured in bits per second.
* **Availability**: This determines the amount of time a system is available to respond to requests. It is calculated: System Uptime / (System Uptime+Downtime.



## 5. What is Sharding?

Sharding is a process of splitting the large logical dataset into multiple databases. It also refers to horizontal partitioning of data as it will be stored on multiple machines. By doing so, a sharded database becomes capable of handling more requests than a single large machine. Consider an example - in the following image, assume that we have around 1TB of data present in the database, when we perform sharding, we divide the large 1TB data into smaller chunks of 256GB into partitions called shards.

<figure><img src="https://d3n0h9tb65y8q.cloudfront.net/public_assets/assets/000/002/760/original/Sharding.png?1644588137" alt="" width="563"><figcaption></figcaption></figure>

Sharding helps to scale databases by helping to handle the increased load by providing increased throughput, storage capacity and ensuring high availability



## 6. How is NoSQL database different from SQL databases?

| Category     | SQL                                                                      | NoSQL                                                                           |
| ------------ | ------------------------------------------------------------------------ | ------------------------------------------------------------------------------- |
| Model        | Follows relational model.                                                | Follows the non-relational model.                                               |
| Data         | Deals with structured data.                                              | Deals with semi-structured data.                                                |
| Flexibility  | SQL follows a strict schema.                                             | NoSQL deals with dynamic schema and is very flexible.                           |
| Transactions | Follows ACID (Atomicity, Consistency, Isolation, Durability) properties. | Follows BASE (Basic Availability, Soft-state, Eventual consistency) properties. |

Check out more differences [**here**](https://www.interviewbit.com/blog/sql-vs-nosql/).



## 7. How is sharding different from partitioning?

* **Database Sharding** - Sharding is a technique for dividing a single dataset among many databases, allowing it to be stored across multiple workstations. Larger datasets can be divided into smaller parts and stored in numerous data nodes, boosting the system’s total storage capacity. A sharded database, similarly, can accommodate more requests than a single system by dividing the data over numerous machines. Sharding, also known as horizontal scaling or scale-out, is a type of scaling in which more nodes are added to distribute the load. Horizontal scaling provides near-limitless scalability for handling large amounts of data and high-volume tasks.
* **Database Partitioning** - Partitioning is the process of separating stored database objects (tables, indexes, and views) into distinct portions. Large database items are partitioned to improve controllability, performance, and availability. Partitioning can enhance performance when accessing partitioned tables in specific instances. Partitioning can act as a leading column in indexes, reducing index size and increasing the likelihood of finding the most desired indexes in memory. When a large portion of one area is used in the resultset, scanning that region is much faster than accessing data scattered throughout the entire table by index. Adding and deleting sections allows for large-scale data uploading and deletion, which improves performance. Data that are rarely used can be uploaded to more affordable data storage devices.

The following table lists the differences between sharding and partitioning:

| Sharding                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 | Partitioning                                                                                                                                                                                                                                |
| ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Sharding is a type of partitioning and is also referred to as horizontal partitioning. Sharding can also be defined as replicating the schema and then dividing the data based on a shard key.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           | A partition is a logical database’s split into separate, independent portions. Database partitioning is commonly used for load balancing, manageability, performance, and availability.                                                     |
| <p>The advantages of sharding include the following: </p><ol><li><strong>Increased Read/Write Throughput:</strong> Distributing the dataset across several shards increases both read and write operation capacity, as long as the read and write operations are limited to a single shard. </li><li><strong>Increased Storage Capacity:</strong> Boosting the number of shards allows for near-infinite scalability by increasing overall total storage capacity. </li><li><strong>High Availability</strong>: Every piece of data is copied since each shard is a replica set. Moreover, because the data is dispersed, even if an entire shard goes down, the database as a whole remains partially functional, with separate shards hosting different parts of the schema.</li></ol> | The advantages of partitioning include all that of sharding since sharding is a type of partitioning. Besides this, partitioning includes the benefits of vertical partitioning as well which involves dividing the schema of the database. |



## 8. How is performance and scalability related to each other?

A system is said to be scalable if there is increased performance is proportional to the resources added. Generally, performance increase in terms of scalability refers to serving more work units. But this can also mean being able to handle larger work units when datasets grow. If there is a performance problem in the application, then the system will be slow only for a single user. But if there is a scalability problem, then the system may be fast for a single user but it can get slow under heavy user load on the application.

## 9. What is Caching? What are the various cache update strategies available in caching?

Caching refers to the process of storing file copies in a temporary storage location called cache which helps in accessing data more quickly thereby reducing site latency. The cache can only store a limited amount of data. Due to this, it is important to determine cache update strategies that are best suited for the business requirements. Following are the various caching strategies available:

* **Cache-aside**: In this strategy, our application is responsible to write and read data from the storage. Cache interaction with the storage is not direct. Here, the application looks for an entry in the cache, if the result is not found, then the entry is fetched from the database and is added to the cache for further use. Memcached is an example of using this update strategy.

<figure><img src="https://d3n0h9tb65y8q.cloudfront.net/public_assets/assets/000/002/762/original/Cache-aside.png?1644588205" alt="" width="563"><figcaption></figcaption></figure>

Cache-aside strategy is also known as lazy loading because only the requested entry will be cached thereby avoiding unnecessary caching of the data. Some of the disadvantages of this strategy are:

* In cases of a cache miss, there would be a noticeable delay as it results in fetching data from the database and then caching it.
* The chances of data being stale are more if it is updated in the database. This can be reduced by defining the time-to-live parameter which forces an update of the cache entry.
* When a cache node fails, it will be replaced by a new, empty node which results in increased latency.
* **Write-through**: In this strategy, the cache will be considered as the main data store by the system and the system reads and writes data into it. The cache then updates the database accordingly as shown in the database.

<figure><img src="https://d3n0h9tb65y8q.cloudfront.net/public_assets/assets/000/002/763/original/Write-through.png?1644588248" alt="" width="563"><figcaption></figcaption></figure>

* The system adds or updates the entry in the cache.
* The cache synchronously writes entries to the database. This strategy is overall a slow operation because of the synchronous write operation. However, the subsequent reads of the recently written data will be very fast. This strategy also ensures that the cache is not stale. But, there are chances that the data written in the cache might never be read. This issue can be reduced by providing appropriate TTL.
* **Write-behind (write-back)**: In this strategy, the application does the following steps:
  * Add or update an entry in the cache
  * Write the entry into the data store asynchronously for improving the write performance. This is demonstrated in the image below:<br>

<figure><img src="https://d3n0h9tb65y8q.cloudfront.net/public_assets/assets/000/002/765/original/Write-behind_(write-back).png?1644588313" alt="" width="563"><figcaption></figcaption></figure>

The main disadvantage of this method is that there are chances of data loss if the cache goes down before the contents of the cache are written into the database.

* **Refresh-ahead**: Using this strategy, we can configure the cache to refresh the cache entry automatically before its expiration.

<figure><img src="https://d3n0h9tb65y8q.cloudfront.net/public_assets/assets/000/002/766/original/Refresh-ahead.png?1644588346" alt="" width="563"><figcaption></figcaption></figure>



This cache strategy results in reduced latency if it can predict accurately what items are needed in future.



## 10. What are the various Consistency patterns available in system design?

Consistency from the CAP theorem states that every read request should get the most recently written data. When there are multiple data copies available, there arises a problem of synchronizing them so that the clients get fresh data consistently. Following are the consistency patterns available:

* **Weak consistency**: After a data write, the read request may or may not be able to get the new data. This type of consistency works well in real-time use cases like VoIP, video chat, real-time multiplayer games etc. For example, when we are on a phone call, if we lose network for a few seconds, then we lose information about what was spoken during that time.
* **Eventual consistency**: Post data write, the reads will eventually see the latest data within milliseconds. Here, the data is replicated asynchronously. These are seen in DNS and email systems. This works well in highly available systems.
* **Strong consistency**: After a data write, the subsequent reads will see the latest data. Here, the data is replicated synchronously. This is seen in RDBMS and file systems and are suitable in systems requiring transactions of data.



## 11. What do you understand by Content delivery network?

Content delivery network or in short CDN is a globally distributed proxy server network that serves content from locations close by to the end-users. Usually, in websites, static files like HTML, CSS, JS files, images and videos are served from CDN.

Using CDN in delivering content helps to improve performance:

* Since users receive data from centres close to them as shown in the image below, they don't have to wait for long.



<figure><img src="https://d3n0h9tb65y8q.cloudfront.net/public_assets/assets/000/002/767/original/Content_delivery_network.png?1644588740" alt="" width="563"><figcaption></figcaption></figure>

* Load on the servers is reduced significantly as some of the responsibility is shared by CDNs.

There are two types of CDNs, they are:

* **Push CDNs**: Here, the content is received by the CDNs whenever changes occur on the server. The responsibility lies in us for uploading the content to CDNs. Content gets updated to the CDN only when it is modified or added which in turn maximises storage by minimising the traffic. Generally, sites with lesser traffic or content work well using push CDNs.
* **Pull CDNs**: Here new content is grabbed from the server when the first user requests the content from the site. This leads to slower requests for the first time till the content gets stored/cached on the CDN. These CDNs minimizes space utilized on CDN but can lead to redundant traffic when expired files are pulled before they are changed. Websites having heavy traffic work well when used with pull CDNs.



## 12. What do you understand by Leader Election?

In a distributed environment where there are multiple servers contributing to the availability of the application, there can be situations where only one server has to take lead for updating third party APIs as different servers could cause problems while using the third party APIs. This server is called the primary server and the process of choosing this server is called leader election. The servers in the distributed environment have to detect when the leader server has failed and appoint another one to become a leader. This process is most suitable in high availability and strong consistency based applications by using a consensus algorithm.



## 13. How do you answer system design interview questions?

* **Ask questions to the interviewer for clarification**: Since the questions are purposefully vague, it is advised to ask relevant questions to the interviewer to ensure that both you and the interviewer are on the same page. Asking questions also shows that you care about the customer requirements.
* **Gather the requirements**: List all the features that are required, what are the common problems and system performance parameters that are expected by the system to handle. This step helps the interviewer to see how well you plan, expect problems and come up with solutions to each of them. Every choice matters while designing a system. For every choice, at least one pros and cons of the system needs to be listed.
* **Come up with a design**: Come up with a high-level design and low-level design solutions for each of the requirements decided. Discuss the pros and cons of the design. Also, discuss how they are beneficial to the business.

The primary objective of system design interviews is to evaluate how well a developer can plan, prioritize, evaluate various options to choose the best possible solution for a given problem.

## 14. What are some of the design issues in distributed systems?

Following are some of the issues found in distributed systems:

* **Heterogeneity**: The Internet allows applications to run over a heterogeneous collection of computers and networks. There would be different types of networks and the differences are masked by the usage of standard Internet protocols for communicating with each other. This becomes an issue while designing distributed applications
* **Openness**: Openness represents the measure by which a system can be extended and re-implemented in different ways. In distributed systems, it specifies the degree to which new sharing services can be added and made available for client usage.
* **Security**: The information maintained in distributed systems need to be secure as they are valuable to the users. The confidentiality, availability and integrity of the distributed systems have to be maintained and this sometimes becomes a challenge.
* **Scalability**: A system is scalable if it remains effective when there is a significant increase in the request traffic and resources. Designing a distributed system involves planning well in advance how well the system can be made scalable under varying user loads.
* **Failure Handling**: In a distributed environment, the failures are partial, meaning if some components fail, others would still function. It becomes challenging to handle these failures as it involves identifying right components where the failures occur.



## 15. What are the critical metrics for measuring system performance? <a href="#id-3.1" id="id-3.1"></a>

&#x20;The most critical metrics are

* Latency: It measures the system's response time in milliseconds.
* Availability: It is the ratio of uptime to total time. Availability shows how much time the system was available to handle requests.
* Throughput: It measures data flow per unit of time, usually represented in bits per second.



## 16.  What is CDN? <a href="#id-3.7" id="id-3.7"></a>

[CDN](https://www.naukri.com/code360/library/cdn-caching)(Content Delivery Network) is the network of servers spread globally to serve static files such as HTML, CSS, JS etc. The purpose of CDNs is to reduce the response time of requests by having data centres geographically close to the end-user. Streaming companies such as NetFlix also use CDN for better performance.



## 17. What is a proxy server? <a href="#id-3.8" id="id-3.8"></a>

The word ‘proxy’ has the dictionary definition as ‘substitute’. Proxy servers act as the middleman between client and server. All the requests coming from clients can be masked with the help of proxy servers. VPN is based on the concept of proxy servers. In complex system designs, proxies can be helpful. For example, you can use a reverse proxy to serve requests you don't want the central servers to handle.

<figure><img src="https://files.codingninjas.in/article_images/system-design-interview-questions-6-1655823950.webp" alt="" width="563"><figcaption></figcaption></figure>



## 18. What is consistent hashing? <a href="#id-3.10" id="id-3.10"></a>

Hashing is the process of converting a given input into preferably an integer. In system- design, hashing is a critical step of load balancing. When a hashing algorithm is deterministic, it produces the same output when the same input is applied multiple times. For example, if the string ‘hello’ produces 10001 as output, then whenever input is ‘hello’, the output should be 10001. A deterministic hashing algorithm is known as consistent hashing when applied in system design.



## 19. What is endpoint protection? <a href="#id-3.12" id="id-3.12"></a>

When designing a large-scale system, it becomes critical to avoid too many processes that are not important to run on the system. For example, a user can click on a button multiple times when in an irritated state. Imagine the system has to serve all the incoming requests. Thus endpoint protection is needed to save resources.

In some situations, we will want to limit the times a user can request a service. For example, paid API services. The word ‘protection’ is used intentionally because it protects the system from cyber-attacks leading to DoS( Denial of Services).

## 20. What are microservices? <a href="#id-3.13" id="id-3.13"></a>

Microservices are an architectural and organizational way to develop software, where software is made up of small components communicating through a well-defined set of APIs. It helps to build scalable software which is easy to manage and upgrade. Each small service is autonomous and specialised.&#x20;
