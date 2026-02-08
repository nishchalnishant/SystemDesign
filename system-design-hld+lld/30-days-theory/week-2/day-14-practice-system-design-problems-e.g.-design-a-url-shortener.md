# Day 14: Practice System Design Problems (e.g., design a URL shortener)



## **System Design Overview**

System design involves architecting large-scale systems with a focus on scalability, availability, reliability, and performance. In interviews, you’re expected to create a system from scratch, taking into consideration various trade-offs and choices based on requirements.

**System design problems** often require you to:

1. **Clarify requirements**: Functional and non-functional requirements.
2. **Define high-level architecture**: Components and interactions.
3. **Address scalability, availability, and performance**: Use caching, load balancing, databases, etc.
4. **Plan for trade-offs and bottlenecks**: Handle potential issues like high traffic or data consistency.
5. **Discuss data storage, redundancy, and fault tolerance**.

***

#### **System Design Problem: Design a URL Shortener**

A URL shortener like Bit.ly converts long URLs into short ones, making it easier to share links. Let’s break down how to approach the design step-by-step.

***

#### **1. Requirements Gathering**

Before diving into the design, clarify both **functional** and **non-functional** requirements.

**Functional Requirements:**

* Shorten a long URL and generate a unique short URL.
* Retrieve the original URL when given the short URL.
* Handle edge cases like invalid URLs or expired URLs.
* Support custom short URLs (optional).

**Non-Functional Requirements:**

* The system should be highly available (since users rely on links working).
* The system should be scalable to handle billions of URL requests.
* Low latency: The redirection process should be fast.
* The system should be fault-tolerant to avoid losing data.
* The short URLs should not clash or duplicate, ensuring uniqueness.

***

#### **2. High-Level Design**

A high-level architecture of a URL shortener typically consists of the following components:

**2.1 Components**

1. **API Layer**:
   * **URL Shortening API**: Accepts long URLs, generates a unique short URL.
   * **Redirection API**: Accepts short URLs and redirects users to the original URL.
2. **Database**:
   * Stores mappings between long URLs and their corresponding short URLs.
   * Stores metadata such as timestamp, expiration date, etc.
3. **Caching**:
   * A cache can store frequently accessed URL mappings to reduce database lookups and increase performance.
4. **Load Balancer**:
   * Ensures traffic is evenly distributed across the application servers.
5. **Distributed System Considerations**:
   * Ensure the design can scale horizontally by adding more servers as the system grows.

***

#### **3. URL Generation Strategy**

The main challenge in URL shorteners is to generate **unique, short URLs**. There are several methods to achieve this:

**3.1 Hash-based Approach**

* **Description**: Use a hash function (e.g., MD5, SHA256) to convert a long URL into a fixed-length string (hash).
* **Pros**: Easy to implement, can generate a unique identifier.
* **Cons**: Hash collisions can occur (i.e., two different URLs generate the same hash), and this needs to be handled with collision resolution strategies (e.g., append a counter or timestamp).

**3.2 Base62 Encoding**

* **Description**: Use a numeric identifier for each URL, then encode the number in a **Base62** format (using digits 0-9, lowercase letters a-z, and uppercase letters A-Z).
  * Example: URL ID `125` in Base62 might be encoded as `cB`.
* **Pros**: Base62 provides a compact and human-readable encoding.
* **Cons**: The system needs to manage unique numeric IDs for each URL, typically using an auto-incrementing field in the database.

**3.3 Random String Generation**

* **Description**: Generate a random string (e.g., 6-8 characters) for each URL using characters from a predefined set (Base62 is common).
* **Pros**: Simple, ensures short URLs.
* **Cons**: Needs to ensure uniqueness by checking the database for existing URLs (i.e., collisions may occur).

***

#### **4. Database Design**

The URL shortener needs a robust data storage strategy to store mappings between short URLs and long URLs.

**4.1 Table Schema**

* **URL Table**:
  * `id` (Primary Key, Auto Increment)
  * `original_url` (Text)
  * `short_url` (String)
  * `creation_date` (Timestamp)
  * `expiration_date` (Optional, if you want URLs to expire after a period)

**4.2 Database Choices**

* **Relational Database (e.g., MySQL, PostgreSQL)**:
  * Works well for storing URL mappings with atomic transactions.
  * Indexed lookups are fast for read-heavy operations.
* **NoSQL Database (e.g., DynamoDB, Cassandra)**:
  * Useful for high throughput and distributed environments.
  * Can store key-value pairs where the key is the short URL and the value is the original URL.
* **Sharding for Scalability**:
  * As the number of URLs grows, horizontal partitioning (sharding) may be necessary.
  * One way to shard is based on the short URL prefix (e.g., URLs starting with ‘a’ are stored on one shard, ‘b’ on another).

***

#### **5. Caching**

To improve performance and reduce the load on the database, frequently accessed URLs should be cached.

**5.1 Caching Strategy**

* Use a distributed cache like **Redis** or **Memcached**.
* Cache mappings of short URLs to long URLs for fast redirection.
* Set an appropriate **time-to-live (TTL)** on cached entries to avoid stale data.

**5.2 Cache Invalidation**

* If a URL expires or is deleted, the cache entry must be invalidated to prevent serving outdated data.

***

#### **6. Handling High Availability and Fault Tolerance**

**6.1 Replication and Redundancy**

* Ensure that the database is replicated across multiple servers to avoid a single point of failure.
* **Master-slave replication** can be used for load distribution.

**6.2 Load Balancing**

* Use a **load balancer** (e.g., AWS ELB, NGINX) to distribute incoming traffic across multiple application servers.

**6.3 Failover Strategy**

* In case of server failure, the system should automatically switch to backup servers (replicated databases, backup caches, etc.).

***

#### **7. URL Expiry and Deletion**

Some URL shorteners provide an expiration date for short URLs. After a certain period, the URLs become invalid and are removed.

* **Implementation**:
  * Add an `expiration_date` field in the database.
  * Periodically run a background job to delete expired URLs or mark them as inactive.

***

#### **8. Security and Considerations**

**8.1 Prevent Abuse**

* URL shorteners can be misused for phishing or redirecting to malicious websites. To mitigate this:
  * **Blacklist** known malicious domains.
  * **Rate limiting** to prevent users from creating too many short URLs in a short period.

**8.2 Redirection Security**

* Ensure that HTTP requests for short URLs are redirected properly using **HTTP 301 (Permanent Redirect)** or **302 (Temporary Redirect)** to avoid issues with search engine indexing.

**8.3 Custom Short URLs**

* Users may want to create custom short URLs (e.g., `http://short.ly/my-link`).
  * Ensure that custom URLs are unique and not already taken.
  * Allow users to reserve specific URLs if needed.

***

#### **9. Traffic Estimation and Scaling**

**9.1 Read and Write Traffic**

* Assume the service is popular and handles billions of URLs.
* **Write operations** (URL shortening) are less frequent than **read operations** (redirections), so the system needs to optimize for reads.

**9.2 Scaling with High Traffic**

* **Horizontally scale** application servers to handle millions of concurrent requests.
* Use **CDNs** to cache responses globally and reduce latency for users far from the origin servers.

***

#### **10. Example API Design**

**10.1 URL Shortening API**

```http
POST /shorten
Request Body:
{
  "original_url": "http://example.com/very/long/url",
  "custom_alias": "my-link" (optional)
}

Response Body:
{
  "short_url": "http://short.ly/abc123"
}
```

**10.2 URL Redirection API**

```http
GET /abc123
Response:
HTTP 301 Redirect to: http://example.com/very/long/url
```

***

#### **11. Conclusion: Trade-offs and Considerations**

**Key Trade-offs:**

* **Consistency vs Availability**: Choose an eventual consistency model (AP system) to prioritize availability over strict consistency. If the short URL lookup is slightly delayed, it’s acceptable as long as the system remains available.
* **Scalability**: The system must handle billions of URL mappings. Horizontal scalability is essential to maintain performance as traffic grows.
* **Fault Tolerance**: Design for fault tolerance by using replication and load balancing to avoid a single point of failure.

By understanding these steps and reasoning through the choices made during the system design process, you'll be well-prepared to tackle system design questions like URL shortener during interviews.
