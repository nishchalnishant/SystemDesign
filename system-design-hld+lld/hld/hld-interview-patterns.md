# HLD interview patterns

Just like how we have patterns for LLD and coding, we can have similar stuff for HLD.

Following are some of the common themes:

* Contending updates
* Derived data&#x20;
* Fan Out
* Proximity search
* Job scheduling
* Aggregation
* Durable data

***

| **Theme**          | **Common Interview Trigger** | **Primary Tech Choice**       |
| ------------------ | ---------------------------- | ----------------------------- |
| Contending Updates | Booking/Inventory            | Redis Lock / SQL Transactions |
| Derived Data       | Search / Dashboards          | Kafka / CDC / Elasticsearch   |
| Fan-Out            | Social Media Feeds           | Hybrid Push/Pull              |
| Proximity Search   | Uber / Delivery              | Geohash / Quad-tree           |
| Job Scheduling     | Reminder Emails              | Task Queues (Celery/Kafka)    |
| Aggregation        | Ad Clicks / Metrics          | Flink / Redis / HyperLogLog   |
| Durable Data       | Payments / Storage           | Multi-AZ Replication / WAL    |

***

These seven themes are essentially the "building blocks" of almost every senior-level system design interview. Understanding the theory is one thing, but knowing which specific problem triggers which pattern is what will get you the "Strong Hire" rating.

Here are practical examples and technical triggers for each theme.

***

### 1. Contending Updates (Concurrency)

The Problem: Multiple users or processes trying to modify the same piece of data at the exact same time.

* Interview Example: Ticketmaster / Airline Booking. 10,000 people trying to book the same "Front Row" seat at the same second.
* Key Strategies:
  * Pessimistic Locking: "Locking" the row in the database so no one else can touch it until the transaction is done. (Good for low contention).
  * Optimistic Locking (Versioning): Each record has a version number. If the version has changed since you last read it, the write fails.
  * Distributed Locks: Using Redis (Redlock) or ZooKeeper to manage locks across multiple microservices.

***

### 2. Derived Data

The Problem: Your primary database is great for transactions but terrible for search or analytics. You need to "derive" a specialized version of that data.

* Interview Example: E-commerce Search (Amazon). When a product is updated in the SQL database, it must also be updated in Elasticsearch for search and Redis for the product page.
* Key Strategies:
  * CDC (Change Data Capture): Streaming database logs to update secondary stores.
  * Event Sourcing: Storing the history of changes rather than just the current state.

***

### 3. Fan-Out

The Problem: A single event needs to be delivered to a massive number of people.

* Interview Example: Twitter/X Timeline. When Elon Musk tweets, that message needs to appear on the feeds of 150+ million followers.
* Key Strategies:
  * Push Model: Pre-compute the feed and write the tweet into every follower's "inbox." (Fast reads, slow writes for celebrities).
  * Pull Model: Followers fetch the tweet at the moment they open the app. (Slow reads, fast writes).
  * Hybrid: Push for "normal" users, Pull for "celebrities."

***

### 4. Proximity Search (Geospatial)

The Problem: Efficiently finding items based on latitude and longitude coordinates.

* Interview Example: Uber or Yelp. "Find the 5 closest drivers to my current location."
* Key Strategies:
  * Geohashing: Converting a 2D coordinate into a single string. Nearby locations share the same prefix.
  * Quad-Trees: A tree data structure where each node has four children, representing quadrants of a map.
  * Google S2: Using Hilbert Curves to map a sphere onto a 1D line.

***

### 5. Job Scheduling

The Problem: Handling tasks that need to run in the future, periodically, or are too slow for a standard API request.

* Interview Example: Netflix Email Notifications. Scheduling an email to be sent 3 days after a user cancels their subscription.
* Key Strategies:
  * Distributed Task Queues: Using Celery (Python) or Sidekiq (Ruby) with Redis/RabbitMQ.
  * Delay Queues: Putting a message in a queue with a "visible-at" timestamp.
  * Dead Letter Queues: Handling what happens when a scheduled job fails.

***

### 6. Aggregation

The Problem: Summarizing massive streams of data in real-time or near real-time.

* Interview Example: YouTube View Counter / Ad Click Aggregator. You can't write to a database every time someone views a video (millions of writes per second would crash the DB).
* Key Strategies:
  * In-memory Aggregation: Count views in a local buffer for 10 seconds, then send a single `+100` update to the DB.
  * Probabilistic Data Structures: Using a HyperLogLog to count "Unique Viewers" using very little memory.
  * Lambda Architecture: Combining a "Fast Layer" (real-time, slightly inaccurate) with a "Batch Layer" (slow, 100% accurate).

***

### 7. Durable Data (Persistence)

The Problem: Ensuring that once a system says "Success," the data is safe even if a whole datacenter explodes.

* Interview Example: Digital Wallet (PayPal/Venmo). If a user transfers $500, that record cannot be lost under any circumstances.
* Key Strategies:
  * Write-Ahead Log (WAL): Every change is written to a sequential file on disk _before_ it is applied to the database.
  * Multi-Region Replication: Synchronously copying data to a different geographic location.
  * Quorum Writes ($$ $W+R > N$ $$): Ensuring a majority of nodes confirm the data is saved.

***



## Contending updates

* Situation: Many writes to the same key, such that they conflict with one another
* examples: Distributed counter, order inventory on popular products
* Solutions:
  * write to the same database, use locking (naive solution, too slow)
  * Multiple database leaders, eventual convergence via automatic merging
    * very relevant for counting, see something similar to our version vector from before
  * Stream processing \[best]
    * each event can go in a log based message broker and processed in small batches.
    * like use kafka.

***

## Derived data&#x20;

* situation: Need to keep two datasets in sync with one another
* example: Global secondary indexes, data transformations on slow database which accepts writes to faster view that is read optimised
* Solutions
  * two phase commit (naive, slows down writes but may be unavoidable)
  * change data capture
    * when updating one database, sink its changes into a log based message broker and use a stateful consumer to enrich the data and sink it to another view
    * avoids an expensive write, but means the derived dataset is eventually consistent with the original

***

## Fan Out — \[most imp]

* Situation-
  * deliver data at write time directly to multiple interested parties to avoid expensive read queries or many active connections on receiving devices
  * examples: push notification, news feed, mutual friends list, stock price discovery
  * Solutions:
    * synchronous delivery to every interested party (naive, request will time out)
    * asynchronous delivery via stream processing
      * sink message to log based message broker, in consumer logic figure out all parties that are interested and send to them accordingly.
      *

          <figure><img src="../../.gitbook/assets/image (3).png" alt=""><figcaption></figcaption></figure>


      * In prior solution, we are prone to a "propular message" problem. if too many users are interested in the message, it becomes very expensive to send it to all of them.
      * instead, we opt for a hybric approach
      * the majority of messages are delivered directly to destination caches asynchronously.
      * if the stream consumer deems a message has too many interested parties, it can also place it in a dedicated "popular message cache" that users can poll from.

***

## Proximity search

* Situation: Find close items in a database to you
* examples: uber driver search, doordash, yelp, AirBnb, Find my friends
* Solution:
  * build indexes on latitute and longitute ( naive, too slow)
  * use a geospatial index
    * data likely will need to be partitioned, use bounding boxes as partition methodology
    * certain geographic areas much more popular than other, shards should have even amount of load, not necessarily geographic coverage ( e.g. shard for new york city, shard for alaska)

***

## Job scheduling

* situation: run a series of tasks on one worker in a cluster
* examples: Build a job scheduler, Netflix/ youtube video upload encoding
* Solutions:
  * round robin jobs into log based message broker patterns (naive)
    * one consumer per partition, slow jobs delay other jobs in that partition
  *   in memory message broker delivering message round robin to workers

      * slow jobs do not prevent jobs behing them in the queue from running



***

## Aggregation

* Situation: Distributed messages that need to be aggregated by some key or time
* examples: metrics/ logs, job scheduler completion messages, data enrichment
* solutions:
  * write everything to a distributed database, run batch job (naive, slow results)
  * stream processing, all messages go into a log based message broker partition based on their aggregation key
    * stream processing consumer framework handle fault tolerance for us

***

## Idempotence

* situation: You do not want to see the output of your tasks more than once
* examples: One time execution in job scheduler, confirmation email to users
* Solution:
  * two phase commit (naive, slow)
    * ensure task is marked as completed from one data source at the same time it is performed
  * idempotency keys
    * store a unique id for the task and check if we have seen it before all incoming tasks&#x20;
    * if reaching an external service can send task ID and external service can reject if it has seen before (fencing tokens)

***

## Durable data

* Situation: you have data that absolutly can not be lost once written
* examples: Financial transactions
* Solutions:
  * synchronous replication (naive , if any replica fails no write can be made)
  * Distributed consensus
    * Can tolerate node falire and still proceed
    * Fairly slow for reads and writes, so using change data capture to derrive read optimized data views can be benificial here
