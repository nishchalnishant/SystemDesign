# Day 7: Practice Mock Interview Questions on Basic System Design Concepts

Mock interviews provide an excellent way to solidify your understanding of system design concepts and prepare for real-world interviews. The seventh day focuses on practicing common system design interview questions that assess your knowledge of foundational topics such as scalability, load balancing, caching, databases, and distributed systems. Below are sample interview questions, along with detailed explanations and approaches for answering them.

***

#### **1. Design a URL Shortener (like bit.ly)**

**Problem Statement**: Design a service like bit.ly, where users can input a long URL and receive a shortened URL. The service should handle a large number of URL requests, and the shortened URLs should be unique and accessible.

**Key Design Considerations**:

* **Storage of URLs**: How and where will you store the long and short URLs?
* **Short URL Generation**: How will you generate unique short URLs?
* **Handling High Traffic**: How will the system scale as the number of URLs and users grows?
* **Redirection Logic**: How will you efficiently handle redirections when someone clicks on a short URL?

**Solution Outline**:

1. **API Endpoints**:
   * **POST /shorten**: Accepts a long URL and returns a short URL.
   * **GET /{shortURL}**: Redirects the user to the original long URL.
2. **Short URL Generation**:
   * Use a **hashing algorithm** (e.g., MD5, SHA-256) to generate a hash of the long URL, or use a **counter-based approach** where a unique ID is generated and encoded using Base62 (to include letters and digits).
3. **Database Design**:
   * Use a relational database (SQL) or a NoSQL database to store the mappings between short URLs and long URLs. For scalability, consider sharding the database by short URL prefixes.
4. **Caching**:
   * Use caching (e.g., Redis) for fast access to frequently requested URLs to reduce database load.
5. **Scalability**:
   * Implement horizontal scaling by distributing the service across multiple servers, with load balancers in place.
   * Store large amounts of data efficiently by partitioning the database.

***

#### **2. Design a System like Twitter**

**Problem Statement**: Design a simplified version of Twitter, where users can post tweets, follow other users, and see tweets from the users they follow.

**Key Design Considerations**:

* **Feed Generation**: How will you generate and display a user's feed?
* **Database Design**: How will you store user information, tweets, and followers?
* **Handling High Read/Write Traffic**: How will you scale the system to handle millions of users posting and reading tweets simultaneously?
* **Data Consistency**: How will you ensure consistency of the follower-followee relationship across distributed servers?

**Solution Outline**:

1. **API Endpoints**:
   * **POST /tweet**: Users can post a tweet.
   * **GET /feed/{userId}**: Returns a list of tweets from users the given user is following.
   * **POST /follow**: Allows one user to follow another user.
2. **Feed Generation Approaches**:
   * **Pull-Based Model**: When a user requests their feed, the system queries the database to fetch the latest tweets from users they follow.
   * **Push-Based Model**: When a user posts a tweet, the system pushes the tweet to all followers in real-time. This approach is more complex but can result in faster feed updates.
3. **Database Design**:
   * Store users, tweets, and followers in separate tables (in an SQL database) or collections (in NoSQL databases).
   * **Follower Table**: Store user relationships (who follows whom) efficiently to allow for quick lookups.
   * **Tweet Table**: Store each tweet with a timestamp and user ID for quick retrieval.
4. **Scalability and Caching**:
   * **Horizontal Scaling**: Use load balancers to distribute requests across multiple servers.
   * **Caching**: Cache user feeds or frequently requested tweets to improve performance and reduce database load.

***

#### **3. Design a High-Scale Notification System**

**Problem Statement**: Design a system that sends notifications (such as emails or push notifications) to millions of users in real-time.

**Key Design Considerations**:

* **Scalability**: How will you send notifications to millions of users efficiently?
* **Reliability**: How will you ensure that notifications are delivered, even in the event of failures?
* **Latency**: How will you minimize latency to deliver notifications as quickly as possible?
* **Data Model**: How will you track users, notifications, and delivery status?

**Solution Outline**:

1. **Notification Service**:
   * The service will receive requests to send notifications (email, SMS, push) to users. It will use a **message queue** (e.g., RabbitMQ, Kafka) to handle requests asynchronously and ensure reliability.
2. **Message Queues**:
   * Use a message queue to handle a large number of notification requests. Message queues decouple the producer (system that generates notifications) from the consumer (notification delivery system) and help handle spikes in traffic.
3. **Worker Processes**:
   * Implement worker processes to consume messages from the queue and send notifications via external APIs (e.g., email service providers, push notification services).
4. **Database**:
   * Store notification templates and user delivery preferences in a database. Track delivery status (sent, failed, etc.) to monitor the system.
5. **Failure Handling**:
   * Implement **retry mechanisms** for failed notifications. If a notification fails to deliver, retry it after a certain interval.
   * Use **circuit breakers** to stop sending requests to failing services temporarily to prevent overload.

***

#### **4. Design an E-Commerce Platform**

**Problem Statement**: Design a system for an e-commerce platform where users can browse products, add them to a cart, and place orders.

**Key Design Considerations**:

* **Database Design**: How will you store product information, user profiles, orders, and inventory?
* **Cart Management**: How will the system handle users adding/removing items from their cart?
* **Order Processing**: How will the system process and fulfill user orders efficiently?
* **Inventory Management**: How will the system ensure that inventory is up to date and prevent overselling?

**Solution Outline**:

1. **API Endpoints**:
   * **GET /products**: Retrieve a list of products.
   * **POST /cart/add**: Add an item to a user's cart.
   * **POST /order/checkout**: Create an order and process payment.
2. **Database Design**:
   * Use an SQL or NoSQL database to store **product information** (name, price, category), **user profiles**, **orders**, and **inventory levels**.
   * **Order Table**: Track the status of each order (pending, shipped, completed).
   * **Inventory Table**: Track available inventory for each product and update it in real-time as orders are placed.
3. **Scalability**:
   * Implement load balancing across multiple servers to handle user traffic.
   * Use a **distributed cache** (e.g., Redis) to store frequently accessed data, such as product details, to improve performance.
4. **Order Fulfillment**:
   * After an order is placed, a background service can handle payment processing, inventory updates, and shipment notifications. Use **message queues** to decouple the order creation process from fulfillment to ensure scalability.

***

#### **5. Design a Real-Time Chat Application**

**Problem Statement**: Design a real-time chat application where users can send and receive messages instantly.

**Key Design Considerations**:

* **Low Latency**: How will you ensure that messages are delivered with minimal delay?
* **Scalability**: How will you handle millions of concurrent users sending messages?
* **Message Delivery Guarantees**: How will you ensure that messages are delivered reliably, even in the event of network failures?

**Solution Outline**:

1. **Real-Time Communication**:
   * Use **WebSockets** to establish a persistent connection between the client and server for low-latency, real-time communication.
2. **Message Queue**:
   * Use a message queue (e.g., Kafka, RabbitMQ) to handle message delivery. Each message is placed in the queue and consumed by a recipient in real-time.
3. **Database Design**:
   * Store user profiles, chat histories, and message metadata (timestamps, sender/receiver) in a database. For scalability, consider partitioning the database based on user IDs or chat room IDs.
4. **Fault Tolerance**:
   * Implement a **retry mechanism** for undelivered messages. If the recipient is offline, store the message in the database and deliver it when the user comes back online.
5. **Scalability**:
   * Scale horizontally by distributing chat services across multiple servers and using load balancers to distribute connections.

***

#### **General Tips for System Design Interviews**:

* **Clarify Requirements**: Always start by asking clarifying questions to understand the scope and scale of the problem.
* **Break Down the System**: Break the problem down into components (e.g., APIs, databases, caching, load balancing) and explain how each component works together.
* **Trade-offs**: Be prepared to discuss trade-offs between consistency, availability, and performance (e.g., CAP theorem, latency vs. scalability).
* **Scalability and Fault Tolerance**: Always emphasize how your design can scale to handle millions of users and how it can tolerate node failures without impacting performance.
* **Caching and Load Balancing**: Incorporate

caching and load balancing techniques to improve system performance and reliability.

***

Practicing these mock interview questions will prepare you to discuss fundamental system design concepts in interviews.
