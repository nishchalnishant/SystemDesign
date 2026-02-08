# System design questions



### EP 1: TinyURL/PasteBin Design Deep Dive

**Functional Requirements**

* **TinyURL:**
  * Given a URL, generate a unique shortened URL.
  * Redirect users to the original URL when they click the shortened URL.
  * Allow links to expire.
  * Optionally provide analytics (e.g., number of clicks, user demographics).
* **PasteBin:**
  * Upload text data.
  * Generate a unique shortened URL for the uploaded text.
  * Redirect users to the text file when they click the shortened URL.
  * Allow pastes to expire.
  * Optionally provide analytics (e.g., number of clicks).

**Capacity Estimates**

* **TinyURL:**
  * 500 million URL shortenings per month.
  * 100:1 read-to-write ratio.
  * 500 bytes per URL.
  * 5 years of storage: 15 TB (can be stored on one machine).
  * Caching 20% of keys (80% of traffic): 50 GB (fits on one server).
* **PasteBin:**
  * Consider average paste size (bytes, kilobytes, gigabytes) to estimate storage needs.

**API Design**

* **Create Endpoint:**
  * Input: Original URL (TinyURL) or text data (PasteBin).
  * Output: Shortened URL.
  * Optional parameters: API key, user ID, expiration time, alias.
* **Get Endpoint:**
  * Input: Shortened URL.
  * Output: Redirect to original URL (TinyURL) or text data (PasteBin).

**Database Tables**

* **Users Table:** (Optional) User ID, email, creation time.
* **URL Mapping Table:** Short URL (primary key), original URL/text data, user ID, expiration time.
* **Analytics Table:** (Optional) Short URL, number of clicks, user demographics.

**URL Shortening Algorithms**

* **Hashing:**
  * Use hash functions like SHA or MD5.
  * Truncate the hash to the desired length.
  * Potential for collisions, requiring probing or conflict resolution.
* **Key Generation Service:**
  * Store all possible keys in the database.
  * Allocate unused keys upon request.
  * Requires atomic operations (e.g., compare-and-set) to prevent race conditions.

**Database Selection**

* **NoSQL:** Preferred due to high read/write throughput and lack of complex joins.
* **Single-leader replication:** Avoids write conflicts in URL generation.
* **LSM-tree based storage engine:** For fast reads and writes.
* **Examples:** MongoDB, Riak (with careful conflict resolution).

**Partitioning and Replication**

* **Partition by short URL key:** Ensures even data distribution.
* **Single-leader replication:** For each partition.
* **Caching:** Cache hot keys to reduce database load.
* **LRU eviction policy:** For cache management.

**Load Balancing**

* **Separate load balancers for create and fetch requests.**
* **Consistent hashing:** Map requests to specific servers based on the short URL.
* **Coordination service (ZooKeeper):** Track server status and partition information.

**PasteBin Specifics**

* **Store paste data in S3.**
* **Use a CDN for popular pastes.**
* \*\*Partition pastes by language or region for efficient delivery.

**Conclusion**

* This design focuses on scalability, availability, and data consistency.
* Consider trade-offs between different approaches (hashing vs. key generation, replication strategies).
* Adapt the design based on specific requirements and scale.

This is a comprehensive breakdown of the TinyURL/PasteBin design problem, covering key aspects like functional requirements, capacity estimation, database selection, partitioning, and load balancing. Remember that this is just one possible approach, and the best solution will depend on the specific constraints and priorities of the system.



## Ep 2: Here are detailed notes from the video:

**Introduction**

* The video is about designing a social media platform like Twitter, Instagram, or Facebook.
* The speaker mentions that the previous video was well-received, which means he has to put more effort into his videos.
* He states that the common denominator between these platforms is the news feed, which is a challenging problem to solve.
* He believes this video will be useful because Elon Musk firing everyone at Twitter might create job openings for viewers who learn how to build a social media platform.

**Functional Requirements**

* Users should be able to create posts containing text, images, or videos.
* Users should be able to follow other users.
* The news feed should be generated with low latency.
* Extended requirements (not discussed in detail): liking posts, replying to posts.

**Capacity Estimations**

* 200 million daily active users.
* Each user follows 200 people on average.
* 100 million new posts per day.
* Average post size: 300 bytes.
* 55 terabytes of data stored over five years.
* 6 gigabytes of cache per day.

**API Design**

* Main endpoints:
  * `create_post`: Creates a new post. Requires user ID, authorization token, metadata, and content.
  * `fetch_news_feed`: Retrieves the user's news feed. Requires user ID and authorization token.
* Extended endpoints:
  * `like_post`: Likes or unlikes a post.
  * `reply_post`: Replies to a post.

**Database Schema**

* `posts` table: Stores user ID, content, metadata (timestamp, location), and potentially an S3 URL for images or videos.
* `users` table: Stores user ID, name, email, password hash, and creation date.
* `user_follows` table: Stores user IDs of followers and followees.

**Architecture**

* **Challenge:** High read volume for news feeds.
* **Naive Solution:** Using a single SQL database for all operations. This is inefficient due to expensive join operations for news feeds.
* **Optimized Solution:** Pre-compute news feeds for most users.
  * For users with many followers, perform some computations on the read path.
* **Database Choice:** NoSQL database like Cassandra for better scalability and performance.
* **Sharding:** Shard the `posts` table by user ID to improve read performance for user profiles.
* **Replication:** Use leaderless replication in Cassandra for eventual consistency.
* **Caching:**
  * Cache popular posts.
  * Cache entire user feeds in Redis for faster retrieval.
* **Object Storage:** Store images and videos in S3 for scalability and performance.
* **CDN:** Use a CDN for popular images and videos to improve delivery speed.
* **Microservices:** Separate write and read services for independent scaling.

**Detailed Architecture Diagram**

* **Client:** Sends requests to the load balancer.
* **Load Balancer:** Distributes traffic to the appropriate microservices.
* **Follow Microservice:** Handles follow/unfollow operations. Updates the `user_follows` table in Cassandra. Publishes changes to a Kafka topic.
* **Flink Consumer:** Consumes changes from the Kafka topic and updates the user feed caches.
* **Post Microservice:** Handles post creation. Publishes the post to an in-memory message broker (e.g., RabbitMQ).
* **In-Memory Message Broker:** Distributes posts to Flink consumer nodes.
* **Flink Consumer Nodes:** Load followers of the user who posted the tweet and update their feed caches in Redis.
* **Cassandra:** Stores posts and user data.
* **Redis:** Caches user feeds and popular posts.
* **S3:** Stores images and videos.
* **CDN:** Delivers images and videos to clients.

**Conclusion**

* The speaker summarizes the key concepts discussed in the video.
* He encourages viewers to leave questions in the comments.

This is a comprehensive summary of the video, covering the key concepts and architectural details.



EP 3:&#x20;

This video by Jordan has no life is a deep dive into the design of a file storage system like Dropbox or Google Drive. The author starts by outlining the functional requirements of such a system, which include:

* **Uploading and downloading files:** Users should be able to easily upload and download files to and from the cloud.
* **File sharing:** Users should be able to share files with other users, granting them varying levels of access (e.g., read-only, read-write).
* **File synchronization:** Changes made to files on one device should be automatically propagated to other devices.
* **Version history:** Users should be able to access and restore previous versions of their files.
* **Offline editing:** Users should be able to edit files offline and have those changes synced to the cloud when an internet connection is restored.

The author then discusses the capacity and performance requirements of the system, considering factors like:

* **File size limits:** The maximum size of files that can be stored.
* **Read/write ratio:** The expected ratio of read operations to write operations.
* **File chunking:** The optimal size of file chunks for efficient storage and transfer.
* **Number of users:** The expected number of users and their storage needs.

Next, the author delves into the API endpoints for the system, including:

* **Upload endpoint:** Handles file uploads, including metadata like file name, user ID, and potentially diffs between the old and new versions of the file.
* **Download endpoint:** Handles file downloads, providing the necessary information to retrieve the file from storage.
* **User management endpoint:** Handles user-related operations, such as adding or removing users from a file's sharing list.

The author then focuses on the database design, emphasizing the importance of:

* **Metadata storage:** Storing essential metadata about files, such as file name, user ID, version history, and chunk information.
* **Transactional consistency:** Ensuring that all changes to a file's metadata are atomic and consistent.
* **Data partitioning:** Distributing the metadata across multiple servers for scalability and performance.
* **Database choice:** Choosing a suitable database system, such as a relational database like MySQL or PostgreSQL, for its transactional capabilities.

The author then presents their proposed system design, highlighting key aspects such as:

* **File chunking:** Splitting files into smaller chunks for efficient storage and transfer.
* **Client-side diffing:** Leveraging client-side logic to identify and upload only the changed portions of files.
* **Conflict resolution:** Handling situations where multiple users make conflicting changes to the same file.
* **Asynchronous updates:** Using a publish-subscribe mechanism to notify clients of changes to shared files.

The author concludes by discussing potential optimizations and extensions to the system, such as:

* **Implementing a custom block storage system:** For improved performance and scalability.
* **Supporting real-time collaboration:** Enabling multiple users to edit files simultaneously.
* **Implementing advanced features:** Such as version control, search, and file previews.

Overall, this video provides a comprehensive and insightful overview of the design considerations for a file storage system like Dropbox or Google Drive. The author presents a well-reasoned design, considering various factors and trade-offs. The video is also engaging and informative, making it a valuable resource for anyone interested in systems design and distributed systems.
