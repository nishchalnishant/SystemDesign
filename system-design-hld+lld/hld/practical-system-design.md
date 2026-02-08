# Practical system design

## <mark style="color:red;">How to approach</mark>

* One need to apprach the problem methodically, just as we would in an algorithms interview.
* Start by breaking down the requirements, structuring your thoughts and guiding the interviewer through the process.
* Solve the problem step by step, adapting the approach to specific requirements.
* We analyze trade-offs and justify the descisions. By the end we will have the skills to clearly and convincingly showcase the thought process inany system design interview.

## <mark style="color:red;">Steps</mark>&#x20;

1. <mark style="color:yellow;">**Requirements gathering**</mark>&#x20;
   1. <mark style="color:purple;">**Functional requirements gathering**</mark>
      1. First step is understanding what the system is supposed to do.
      2. Main actions users will perform and clarifying use-cases.
      3. ex —&#x20;
         1. Short URL service --
            1. shortening the URL
            2. Redirecting users from a short URL to the original URL.
         2. Twitter --
            1. allowing users to post tweets
            2. enabling users to view a home feed of tweets
            3. Supporting engagement through likes, retweets and replies.
   2. <mark style="color:purple;">**Non-Functional requirements gathering**</mark>
      1. Next step should be to focus on how it should perform.
      2. Non-functional requirements define the system's operational needs such as scalability, reliability and performance.
      3. Key non-functional requirements to consider in an interview
         1. Scalability — What traffic or data volume should the system handle
         2. Latency what response time are acceptable
         3. Availability what level of uptime is required
         4. Consistency Does the system require strong consistency(financial transactions) or can it tolerate eventual consistency (social meida feeds)
         5. Relaibility : How shoudl the system handle failures for instance should it have failover mechanism for servers or data venter outage
         6. Security: While less common in general interviews, one might ask about requirements for authentication, encryption or DDoS protection if the role demands it
      4. ex —&#x20;
         1. Short URL service —&#x20;
            1. Scalability — Handle millions of URL shortening and redirection request daily
            2. Latency: Redirect users from a short URL to original quickly
            3. Availability: Maintain very high uptime, especially for redirection functionality.
         2.  Twitter --

             1. Scalability serve 500M DAU and handle milltion of tweets per minute
             2. Latency: Load the. first 10 tweets of a user home feed quickly within 500ms
             3. Consistency: Allow silent delays in tweet propagation across regions.


2. <mark style="color:yellow;">C</mark><mark style="color:yellow;">**apacity Estimation --**</mark>
   1. these can guide us about scalability, database design, and caching
   2. avoid wasting time on unnecessary or overly detailed estimation instead
      1. database scaling — use capacity estimation to decide the number of shards or replicas based on query volume and storage growth
      2. caching requirements — estimate traffic volumes and read to write ratios to determine caache sise and plaaement
      3. message queues configuration — estimate events per second to configure tools like kafka or rabbitmq
      4. load balancer sizing — estimate peak rps ti configure load balancers effectively.
   3. ex--&#x20;
      1. short URL service
         1. traffic assumption
            1. 200 RPS for creating short URLs.
            2. 20000 RPS for redirecting short URL to long URLs
         2. Storage requirements
            1. Long URLs (100 bytes)
            2. Short URLs (8 bytes)
            3. Creating time (8 bytes)
            4. created by user id (20 bytes)
            5. Expiration time(8 bytes)
         3. each entry is 144 bytes which can be rounded up to 256 bytes which leads us to five year storage of 8 tb
         4. insights for design
            1. A single relational database like MySQL or PostgreSQL could handle this scale with read replicas for redirect lookups.
            2. If traffic grows beyond expectations, a distributed NoSQL database like DynamoDB or Cassandra may be required.
            3. Caching frequently accessed redirects in Redis could significantly reduce database load.
      2. Twitter
         1. Traffic assumption
            1. 500M DAU (Daily Active Users)
            2. Each user tweets twice per day, resulting in 1B tweets/day.
            3. Each user views 100 tweets/day on average.
            4. At peak, 20% of DAU (\~100M users) interact with the system simultaneously.
         2.  **Storage Requirements**

             Each tweet includes:

             1. Tweet ID (8 bytes8bytes)
             2. Created by (user ID, 20 bytes20bytes)
             3. Posted time (8 bytes8bytes)
             4. Content (140 bytes, rounded up to 256 bytes256bytes with metadata)
             5. Media link (optional, average 100 bytes100bytes)
             6. Number of likes (8 bytes8bytes)
             7. Hashtags (variable, average 64 bytes64bytes)
             8. Users mentioned (variable, average 64 bytes64bytes)
         3. Insights for design
            1. A distributed DB is required
            2. Tweets should be partitoned based on USER ID to balance storage across nodes.
            3. Cache popular tweets in redis to optimize read performance for frequently viewed timelines.
3. <mark style="color:yellow;">**Database design**</mark>
   1. Choosing the right db is one of the critical steps in system design as it directly impacts scalability, performance and maintainability
   2. For small scale systems relational DB like MYSQL or postgreSQL often suffice due to their robust query capabilities and strong consistency
   3. But for larger-scale systems handling significant data volumes or high write throughput, distributes NoSQL databases like cassandra, DynamoDB or BIgtable maybe necessary.
   4. Equally important is the data modelling to identify potimal partitioning keys, normalisation denormalization opportunities and trade-offs between performance and storage.
   5. Level of detail invested in data modeling should be proportional to its impact on the design
   6. ex --
      1. Short URL service
         1. Primary model is mapping between short and long URLs
            1. simple queries — basic lookup and inserts
            2. strong consistency requirements : Once mapping is written it must be immediately visible to all readers
            3. Read heavy traffic — For more redirects(reads) occur than URL generations(writes)
            4. A key-value store like DYnamoDB is a natural fit. Dynamo DB provides horizontal scalability, high performance and optional consistency.
         2. Additional secondary indexes can support features like&#x20;
            1. listing all short URLs created by a user
            2. Expiring short URLs based on expiration\_time
         3. initial insights
            1. Caching : we could use redis to cache frequently accessed short-long mappings reducing DB load
            2. URL Generation --
               1. We couse Base-62 encoding to generate compact, User-friendly short URLs
               2. Implement collision handlaing for cases where the same short URL is generated more than once.
      2. Twitter&#x20;
         1. Database choice:
            1. Small Row Sizes: Individual tweets are small (e.g., \~512 bytes).
            2. High Write Volume: Billions of tweets are generated daily.
            3. Relational Query Needs: Queries like “list tweets by a user” require relationships.
            4. Eventual Consistency: Strong consistency is not strictly necessary for tweets.
         2. Given these requirements:
            1. LSM-Based Databases (e.g., Cassandra): Ideal for write-heavy workloads and horizontal scaling.
            2. B-Tree-Based Databases (e.g., MySQL, MongoDB): Better for relational queries but may face scalability challenges with high write volumes.
         3.  Data model<br>

             <figure><img src="../../.gitbook/assets/image (60).png" alt=""><figcaption></figcaption></figure>
         4. A compromise might be a document-oriented database like MongoDB. MongoDB is more horizontally scalable than relational databases, supports eventual consistency to enhance scalability, and offers better relational query capabilities than Cassandra.
         5. This separation allows efficient queries for both:
            * Listing users a given user is following.
            * Listing followers of a given user.
4. <mark style="color:yellow;background-color:yellow;">**API Design --**</mark>
   1. Specify input and outputs (API design)
      1. With the requirements clear the next step is to define the inputs and outputs
      2. This involves outlining the api endpoints and their expected behaviour.
      3. Doing so not only clarifies the data flow but also helps us identify the system read and write paths.
   2. ex--
      1. **Short URL Service**
         1. _shortenUrl()_
            1. Input: a long Url
            2. Output: A shortened Url.
         2. _redirectUrl()_
            1. input: shortened Url
            2. Output: HTTP 302 Redirect to the original URL.
      2. **Twitter**
         1. _postTweet()_
            1. input: tweet
            2. output: timestamp, tweetId
         2. _like()_
            1. input: tweetId
            2. output: some kind of success message
         3. _homeFeed()_
            1. input: userId
            2. output: an array of tweets
5. <mark style="color:yellow;">**Design Workflows (read and write paths)**</mark>
   1. These workflows outline how data is processed and provide a foundation for high-level design.
   2. ex --
      1. **Short URL Service**
         1. _Write Path:_
            1. A user submits a long URL through the API.
            2. The system generates a unique short URL using hashing or a key-generation algorithm.
            3. The short-long URL mapping is stored in the database
         2.  _Read Path:_

             1. A user accesses the short URL.
             2. The system queries the database to retrieve the corresponding long URL.
             3. The user is redirected to the original long URL.


      2. **Twitter**
         1. _Write Path:_
            1. A user posts a tweet via the API.
            2. The tweet is stored in the database and indexed.
            3. The system propagates the tweet to followers’ home feed using some kind of fan-out mechanism
         2. _Write Path:_
            1. A user likes a tweet via the API.
            2. The system updates the database by increasing the like counter for that tweet.
         3. _Read Path:_
            1. A user opens their home feed.
            2. The system fetches tweets from database.
            3. Tweets are returned to the user.
6. <mark style="color:yellow;">**High-level design**</mark>
   1. Now we can begin sketching the high level-design there is no need to start with an overly complicated digram
   2. start with something simple and refine it as you optimize the design.
   3. A straightforward starting point often involves a basic setup, a client making a request to a service which then interats with the db for both the read and write paths.
   4. ex—&#x20;
      1. short URL service
         1.

             <figure><img src="../../.gitbook/assets/image (61).png" alt=""><figcaption></figcaption></figure>


         2. This captures read and write path, but as we scale and handle more traffic this setup will encounter scalability challanges given that service is read-heavy.
         3. To address this we introduce&#x20;
            1. API gateway
               1. acts as central entry point for client requests
               2. Streamlining operations and enhancing scalability. Key function include
                  1. routing- Directs requests to the appropriate service, whether its URL generation or redirection
                  2. Rate limiting — limits excessive usage
            2. cache
               1. Introducing cache significantly reduces load by storing frequently accessed short-to-long mappings.
               2.  this improves performance by--

                   * Serving redirect requests directly when the mapping exists in the cache, avoiding database queries.
                   * Supporting low-latency, high-throughput operations with technologies like Redis.
                   * Using efficient eviction policies (e.g., LRU) to manage storage and prioritise popular mappings.


         4.

             <figure><img src="../../.gitbook/assets/image (62).png" alt=""><figcaption></figcaption></figure>


      2. Twitter service
         1.  Initial design

             <figure><img src="../../.gitbook/assets/image (63).png" alt=""><figcaption></figcaption></figure>


         2. Here home feed serive fetches data directly from the db and computes the live feed for each user on-the-fly.
         3. this aproach represents significant scalability challanges. As the Userbase grows computing the feet in real time becomes expensive and introduces high latency.
         4. To solve this we need more scalable approach
            1. A common pattern with high computational cost is to precompute the data that needs to be fetched. later.
            2.  This pattern reduces the need to real time computation by offloading expensive operations to asynchronous processes. ex--

                * **Video Streaming Services**: Transcode video streams for different resolutions and formats ahead of time to avoid real-time delays.
                * **Social Media Notifications**: Precompute notification lists (e.g., likes, hashtag, comments, mentions) instead of generating them dynamically for millions of users.
                * **Advertising Platforms**: Precompute bidding results, ad rankings, and user segmentation for fast delivery of targeted ads.

                <br>
         5. The pattern usually goes like this:
            1. Client post some data by calling some service.&#x20;
            2. This service writes data to the database, then sends the data as an event to message queue for async processing.&#x20;
            3. A separate service will then pick up the event from the queue and process it.&#x20;
            4. After processing, it will store the data into a cache or database or both depending on the trade offs we are willing to make.&#x20;
            5. The client can then fetch the precomputed data without waiting for the service to process it in real time.
            6. Using this pattern, in our evolved system the client will make a request to **Tweet service**, the tweet service will store the data to database and then send it as an event to the **message queue**.&#x20;
            7. A separate service, which in this case is the **Home Feed Generator** will pick up this event from the queue and process it.&#x20;
            8. It will then store it to both the **database** and **cache**. The client will then fetch home feed via the Home Feed Service without the need to compute it in real time.
            9.

                <figure><img src="../../.gitbook/assets/image (64).png" alt=""><figcaption></figcaption></figure>


         6.  Notice how we added an API Gateway here as well! By reusing a component we know works effectively, we leverage its proven benefits and apply them to the Twitter service.

             As mentioned in our _social media notifications_ example, we can also do pre-computation for elements such as hashtag, mentions and etc. Let's create a tweet analyzer service for that.
         7.

             <figure><img src="../../.gitbook/assets/image (65).png" alt=""><figcaption></figcaption></figure>


7. <mark style="color:yellow;">**Detailed component analysis --**</mark>
   1. Once we have the high-level design, we can focus on specific parts of the system to look for deep dive opportunities.&#x20;
   2. For example, if we add caching, we could decide on an eviction policy like LRU or LFU and choose a tool like Redis or Memcached based on what the system needs
   3. We can also look at other areas, like improving database further using indexing or partitioning, or picking the right message broker for handling events, or ensuring the system can handle failures with retries or leader election.
   4. Short URL Service Deep Dive
      1. Since this system is read-heavy, caching becomes a crucial component for improving performance and scalability.
      2. Caching --
         1.  To optimize the performance of the redirectURL() API, which is vital for this system, we employ a two-level caching strategy. Given the natural locality of access for requests, caching is particularly effective.

             At the edge, Content Delivery Networks (CDNs) will store mappings from short URLs to long URLs for the most frequently requested links. For example, if a celebrity shares a short URL on social media, this mapping should be available in the CDN. CDNs, hosted at Internet Exchange Points (IXPs), provide ultra-low latency responses to clients. Their limited storage space focuses on high-demand mappings, ensuring scalability and fault tolerance by handling significant traffic without overloading the API Gateway.

             In the data center, we use a caching node such as Redis. With multiple Redis nodes offering hundreds of GBs of memory, a larger set of mappings can be cached compared to the CDN. This approach improves performance by reducing the need to query the database directly. Both CDN and Redis caches use a Least Recently Used (LRU) eviction policy to ensure the cache contains the most relevant mappings.
      3. Partitioning --
         1.  To ensure scalability, the database and cache layers are partitioned. The short URL is the ideal partitioning key for several reasons. The system primarily performs lookups based on the short URL, making it a natural choice for directing requests to the appropriate cache or database node. Additionally, since short URLs are randomly generated, they are evenly distributed across nodes, preventing hotspots and ensuring balanced load distribution.

             Other potential partitioning keys, such as the long URL or user ID, are less effective. Long URLs are not commonly used for lookups, and user IDs may create uneven distribution, leading to hotspots in specific partitions.
      4.  There are two main approaches to generating short URLs: hashing and random generation.

          * Hashing (e.g., using MD5 or SHA-2) creates a deterministic mapping of long URLs to short URLs. While it avoids the need for random number generation, it introduces a higher risk of collisions, particularly because the generated hash (20 bytes or more) must be truncated to fit within the shorter 8-character length of the short URL.
          * Random generation minimizes the likelihood of collisions by randomly selecting strings for short URLs. If a collision occurs, the system can simply regenerate another random string. Though this approach requires computational resources for random number generation, modern operating systems like Linux provide efficient random generation through tools like /dev/urandom, making the overhead negligible.


   5. Twitter service deep dive --
      1. Home feed service --
         1.  The Home Feed recommends the top K most interesting tweets for a user, ranked by a scoring algorithm. A possible formula for the score is:

             Score of a Tweet=(weight\_like×number\_of\_likes)+(weight\_followers×number\_of\_followers\_of\_author)+(weight\_retweet×number\_of\_retweets)Score of a Tweet=(weight\_like×number\_of\_likes)+(weight\_followers×number\_of\_followers\_of\_author)+(weight\_retweet×number\_of\_retweets)

             Given that pre-generation has been chosen for better performance, we can analyze the storage requirements to determine the appropriate technology and cost. To store the 20 most interesting tweets for 500 million Daily Active Users (DAU), we would need:

             500M×20×512 bytes=5.12 TB500M×20×512bytes=5.12TB

             Modern caching systems, like Redis, are well-suited for handling this scale. For example:

             * Redis servers with 128 GB of memory each could store these mappings efficiently.
               * To meet the 5.12 TB requirement, approximately 40 servers would be needed.

             This calculation ensures the system is scalable and cost-effective while maintaining low-latency access to precomputed feeds.


      2.  **Home Feed Generator**

          The Home Feed Generator pulls new tweets from a message queue, calculates their scores, and updates the top-K tweets for each user. Using Redis Sorted Sets (backed by a skip list) is an efficient approach for this task. Sorted Sets allow quick insertions and retrievals of the top K items without destroying the list, unlike a heap, where items must be popped repeatedly to retrieve the list.

          **Trade-offs and Technology Choices**

          To ensure scalability and performance, sharding plays a key role:

          1. Tweets Database: Tweets should be sharded by Tweet ID. This evenly distributes the load because Tweet IDs are uniformly generated. A common query pattern, such as looking up tweets by hashtag, remains performant because hashtags can store references to relevant Tweet IDs, directing the query to the correct shard.
          2. Redis Cache for Home Feed: The cache storing the Home Feed should be sharded by user\_id. Since Home Feeds for all users are roughly the same size, sharding by user ensures an even load across cache servers and allows the system to quickly locate the correct shard for a user’s feed.


      3.  **Handling Bottlenecks and Failure Scenarios**

          The current design handles typical workloads well but could face challenges in high-impact scenarios, such as when a celebrity with millions of followers posts a tweet. In such cases:

          * A celebrity’s tweet could dominate the top-K lists of millions of users, overwhelming the cache system with updates.
          * To mitigate this, the Home Feed Generator should avoid adding tweets from users with a very high follower count (e.g., > 1,000) directly to individual feeds. Instead, such tweets can be added to a dedicated cache or feed for popular users.
          * The Home Feed Service can periodically pull from this popular feed (e.g., every 5 minutes) and merge it with individual user feeds, reducing the load on the system while still including relevant content.

          Another scalability challenge arises with the `number_of_likes` field in the Tweet document, particularly when a tweet from a highly-followed user goes viral. For instance, if millions of users press the "Like" button simultaneously, the number\_of\_likes field in the database becomes a hot spot, creating a bottleneck and potentially overwhelming the system.


      4.  To address this issue, we can implement a **sharded counter service** for users with a significant number of followers (e.g., more than 1,000). This service would handle the counting in a distributed manner:

          1. **Sharding the Counter**: Instead of a single counter, the system would use multiple subcounters (e.g., 100 shards) for a single tweet. Each shard stores part of the number\_of\_likes value in a high-performance key-value store like Redis. This distributes the load, preventing a single counter from becoming a bottleneck.
          2. **Periodic Aggregation**: The service periodically aggregates the values from the subcounters (e.g., every 5 minutes) and writes the total back to the number\_of\_likes field in the Tweet document stored in the database. This reduces the write frequency to the database while ensuring the count remains reasonably up-to-date.
          3. **Efficient Reads**: For real-time applications, such as displaying the number of likes on the client side, the total can be fetched dynamically by summing up the values of the subcounters in Redis. Alternatively, for slightly stale but faster reads, the periodically updated value in the database can be used.

          This sharded counter approach minimizes contention and ensures that the system can handle viral tweets from highly-followed users without performance degradation. <br>



