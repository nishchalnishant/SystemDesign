# Arpit biyani System design beginner

## <mark style="color:yellow;">Introduction</mark>

* build a deeper understanding
* pause, Absorb & proceed
* Always better to implement

## <mark style="color:yellow;">What is system design</mark>

Mixture of all these along with how they interact with each other

* <mark style="color:purple;">Decide architecture</mark>
* <mark style="color:purple;">decide component</mark>
* <mark style="color:purple;">decide modules</mark>

What will we do when we design a system

* <mark style="color:purple;">break down problem statement into solvable sub problems</mark>
* <mark style="color:purple;">decide on key componenent and responsibilities</mark>
* <mark style="color:purple;">decide on boundries of each component</mark>
* <mark style="color:purple;">touch upon key challanges in scaling it</mark>
* <mark style="color:purple;">make out architecture fault tolerant and availability</mark>

## <mark style="color:yellow;">How to approach system design</mark>&#x20;

Steps to follow

* <mark style="color:purple;">take baby steps, no matter what</mark>
* <mark style="color:purple;">understand the problem statement</mark>
  * without understanding the problem at hand one would easily digress
* <mark style="color:purple;">break it down into component (essentials)</mark>
  * do not create component for the sake of it
  * create component that you know are must
  * ex — design facebook
    * features / components \[these features are big enough to be a micro-service]
      * authentication
      * notification
      * feed
      * gamification&#x20;
    *   now go through each features (microservice)

        *   ex— feed system



            <figure><img src="../../../.gitbook/assets/image (67).png" alt=""><figcaption></figcaption></figure>
        * web server — to serve the content to the user from the database
        * generator — to generate the content and store them into the database
        * aggregator — we can skip this if not sure about this functionality


* <mark style="color:purple;">For each sub component look into \[repeat these for each component]</mark>
  * database and caching&#x20;
  * scaling and fault tolerant
  * async processing (delegation)
  * communication&#x20;
* <mark style="color:purple;">Add more sub component if needed</mark>
  * understand the scope

## <mark style="color:yellow;">How to evaluate if you have good system?</mark>

* <mark style="color:purple;">you broke your system into components</mark>
* <mark style="color:purple;">every component has a clear set of responsibilities, in most cases exclusive</mark>
  * feed web-server — serves feed over http&#x20;
  * feed generator — pulls data from multiple services and puts them in db
  * feed aggregator&#x20;
    * combines candidate items fetch be genrator
    * filters out redundant ranks and creates a final consumable feed
* &#x20; <mark style="color:purple;">for each component you have slight technical details figured out</mark>
  * database and caching
  * scaling and fault tolerance
  * async processing(delegation)
  * communication
* <mark style="color:purple;">each component (in isolation) is</mark>
  * scalable — horizontally scalable
  * fault tolerant — plan for recovery (mostly data, to a stable state ) in case of a faliure
  * available — component function&#x20;

## <mark style="color:yellow;">Relational database</mark>

* most critical component of any system, these make or break a system
* data is stored and represented in rows and columns.
* ex— MySQL, PostgreSQL, SQL Server, Oracle
* key properties
  * data consistency
  * data durability
  * data integrity
  * constraints
  * everything in one place
* Because of these reasons relational data base provides transactions
  * <mark style="color:purple;">A - Atomicity</mark>
    * all statement within a transaction takes place or none
    * ex — publish a post and increase total posts count&#x20;
      * all should be executed if it is complete if not none
    * Mechanism How It Ensures Atomicity
      * Transactions Ensures all SQL commands are executed together or not at all.
        * A transaction is a unit of work consisting of multiple SQL operations executed together.
        * If all operations succeed, the transaction commits (changes are permanently saved).
        * If any operation fails, the transaction rolls back (all changes are undone).
      * Write-Ahead Logging Logs changes before applying them to disk
        * Before making changes to the database, relational databases first write the intended changes to a log file.
        * If a failure occurs, the database can use this log to rollback incomplete transactions.
      * Locking Mechanisms Prevents concurrent modifications leading to partial updates
        * Row-level locks: Ensure that multiple transactions do not modify the same data simultaneously.
        * Pessimistic locking: Prevents other transactions from modifying data until the current transaction completes.
        * Optimistic locking: Used in high-concurrency environments, ensuring transactions only commit if no other changes occurred.
      * Savepoints Allows rolling back part of a transaction
        * Savepoints allow rolling back only a part of a transaction instead of the entire transaction.
  * <mark style="color:purple;">C - Consistency</mark>
    * data will never go incorrect no matter what&#x20;
    * constraints, cascades, triggers
    * one have the necessary tools to ensure that your data never go inconsistent
    * enforce consistency using various mechanisms:
      * Data Integrity Constraints
        * Primary Key Constraint – Ensures unique identifiers
        * Foreign Key Constraint – Maintains referential integrity
        * Check Constraints – Ensures column values meet specific conditions
        * Not Null Constraint – Prevents missing values
      * Transactions and Rollbacks
        * Relational databases ensure that transactions preserve consistency by rolling back if any error occurs.
      * Cascading Actions for Referential Integrity
        * Relational databases enforce consistency across related tables using ON DELETE CASCADE and ON UPDATE CASCADE
      * Isolation Levels to Prevent Dirty Reads
        * Databases use isolation levels to ensure consistency by controlling how transactions interact.
        *

            <figure><img src="../../../.gitbook/assets/image (43).png" alt=""><figcaption></figcaption></figure>
      * Write-Ahead Logging (WAL)
        * Before applying a change, the database writes the intended changes to a log.
        * If the system crashes, the database replays the log to restore consistency.
    * ex — foreign key checks do not allow you to delete parent if the child exists
      * cascade, trigger, constraints
  * <mark style="color:purple;">I - Isolation</mark>
    * when transaction commits the changes outlive the outage
    * when multiple transaction are executing parellaly  the isolation level determines how much changes of one transaction are visible to the other
    * how much details of one transaction is visible to other transaction.
    * if one transaction has 5 transaction and another transaction wit 3 different transaction, isolation means how much of transparency of one transaction is available to another transaction.
    * one can configure these isolation level for these transactions.
    *

        <figure><img src="../../../.gitbook/assets/image (46).png" alt=""><figcaption></figcaption></figure>
    * Relational databases implement isolation using transaction isolation levels and locking mechanisms.
      * Transaction Isolation Levels
        *

            <figure><img src="../../../.gitbook/assets/image (44).png" alt=""><figcaption></figcaption></figure>
      * Locking Mechanisms
        *

            <figure><img src="../../../.gitbook/assets/image (45).png" alt=""><figcaption></figcaption></figure>
      * Multi-Version Concurrency Control (MVCC)
        * MVCC allows multiple versions of a record, so readers don’t block writers and writers don’t block readers.
        * Used by PostgreSQL, MySQL (InnoDB), Oracle.
        * Instead of locking, transactions see old snapshots of data.
    * there are 4 isolation level for relational database
      * _Read Uncommitted,_&#x20;
      * _Read Committed,_&#x20;
      * _Repeatable Read (standard practice, most cases this is used)_
      * _Serializable_
    * Which Isolation Level Should You Use?
      *

          <figure><img src="../../../.gitbook/assets/image (47).png" alt=""><figcaption></figcaption></figure>
  * <mark style="color:purple;">D- Durability</mark>
    * df whene transaction commits the changes outlive the outage
    *

        <figure><img src="../../../.gitbook/assets/image (48).png" alt=""><figcaption></figcaption></figure>
    *   Relational databases (MySQL, PostgreSQL, SQL Server, Oracle) implement durability using:

        1️⃣ Write-Ahead Logging (WAL)

        2️⃣ Commit & Flush to Disk

        3️⃣ Redo & Undo Logs

        4️⃣ Replication & Backups
    * Write-Ahead Logging (WAL)
      * Before committing a transaction, databases log changes in a separate file (the WAL log).
      * If a crash occurs, the database can replay the log to recover committed transactions.
      * Used in PostgreSQL, MySQL (InnoDB), Oracle, SQL Server.
    * Commit & Flush to Disk
      * When a transaction commits, data must be physically written to disk.
      * Databases use fsync() or fdatasync() to ensure data is safely stored.
    * Redo & Undo Logs
      * Redo Logs: Ensure committed transactions are reapplied after a crash.
      * Undo Logs: Allow rollback of uncommitted transactions to maintain consistency.
    * Replication & Backups
      * Replication: Database copies data to secondary nodes, ensuring data is not lost if the primary server fails.
      * Backups: Periodic snapshots prevent data loss beyond the last backup.
  * excecise
    * setup a ssql database(mysql or postgres sql)
    * create a schema for social network&#x20;
    * insert data in users and profile in one transaction

## <mark style="color:yellow;">Database isolation level</mark>

* relational data provides ACID guarantees and i in acid is isolation level helps us tune them&#x20;
* isolation level dictates how much one transaction knows about the other&#x20;
* repeatable reads
  * consistent reads within same transaction&#x20;
  * even if other transaction committed 1st transaction would not see the changes (if value already read)
  * no matter how many other transaction make any changes to the value one would get the same value which was there in the start of the transaction
* read committed
  * reads within the same transaction always reads the fresh value&#x20;
  * con: multiple reads within the same transaction are different
  * always reads the latest committed value even if the update was done in another transaction
* read uncommitted
  * reads even uncommitted values from other transaction
  * dirty read
  * reads the latest value even if it was not committed in another transaction
* serialisable
  * every read is locking (depends on engine) nd while one transaction reads, others will have to wait
  * storage engine can alter the implementation so read documentation before you alter.
  * when another transaction tries to read when another transaction is not committed it waits till the earlier transaction is completed.

## <mark style="color:yellow;">Scaling databases</mark>

* database are most important component of any system out there, it makes or breaks any system
* vertical scaling&#x20;
  * adds more CPU, RAM, DISK to the db
  * requires downtime during reboot
  * gives you ability to handle scale more load
  * vertical scaling has a physical hardware limitation
* Horizontal scaling: Read replicas
  * when read:write= 90:10
  * you move read to other database so that master is free to do writes
  * to manage this we use SYNC/ASYNC replication, where the master is free to do writes while replica handles the reads . to make sure things are consistent across two db is we use the aync/async replication.
  * API servers should know which DB to connect to get things done
  * <mark style="color:red;">**replication**</mark>&#x20;
    * changes on one db(master) needs to be sent to replica to maintain consistency
    * <mark style="color:purple;">**synchronous replication**</mark>&#x20;
      * do not terminate write unless the write operation is done in both master and replica
      * strong consistency
      * zero replication lags
      * slower write
      * master pushes the data to the replica
    * <mark style="color:purple;">**Asynchronous replication**</mark>&#x20;
      * terminates the write as soon as the write operation is done in master&#x20;
      * eventual consistency
      * same replication lag
      * faster write&#x20;
      * replica pulls the data from the master&#x20;
      * to do this all db has a replication where it periodically checks with the master whare are the updates after the last update&#x20;
  * <mark style="color:red;">**Sharding**</mark>
    * as one note cannot handle the data load we split it into multiple exclusive substes write on a particular row/document will go to one particular shard.a&#x20;
    * This way we scale our overall database load
    * not: shareds are indepentend no replication b/w them
    * api server needs to know whom to connect to to get things done
    * Note— some database has a proxy that cares of routing , one can use API as well
    * each shard can have its own replication
    *

        <figure><img src="../../../.gitbook/assets/image (69).png" alt=""><figcaption></figcaption></figure>
* exercise
  * create a master replica db where the read and write are handled by the api
  * create a shard and replicate the master db into different shard using both api and reverse proxy

## <mark style="color:purple;">Sharding and partitoning</mark>

* SHARDING: method of distribute data across multiple machines, multiple database&#x20;
  * adv:
    * handles large reads and writes
    * increase overall storage capacity
    * high availability
  * dis:
    * operationally expensive
    * cross-shard of queries are expensive
* Partitoninng : splitting a subset of data within the same instance, splitting the data
* How a database is scaled
  * a db server is just a database process(mysql, mongodb) running on an ec2 instance
  * single machine has an exposed port to it.
  * as we are getting more users, we scale up our DB give it more PCU, RAM and disk for the ec2 instance
  * but this has a limit, after this we need to scale it up again
  * for this we need to do the horizontal scaling
  * if one DB is able to handle 1000 WPS and we can't scale up beyond that so we split the data horizontally and SPLIT the data&#x20;
  *

      <figure><img src="../../../.gitbook/assets/image (70).png" alt=""><figcaption></figcaption></figure>
  * by adding one more DB server we reduced the load to 750 WPS on each node thus high through put.
  * each data base server is thus a shard and we say that the data is partitoned
  * overall a db is sharded while the data is partitoned.
  *   one can choose these 5 partiton to be stored in the 5 different shard or onto a 2 different shard

      <figure><img src="../../../.gitbook/assets/image (71).png" alt=""><figcaption></figcaption></figure>
  * how do we partiton the data
    * vertical partioning
      * pick some table and put it in one db and pick other tables and put it in another db
      * monolith to micoservice
    * horizontal partioning&#x20;
      * very common
      * pick some row put it in on one db and pick others and put it on another db
    *

        <figure><img src="../../../.gitbook/assets/image (73).png" alt=""><figcaption></figcaption></figure>



## <mark style="color:purple;">Non relational databases</mark>

* very broad generalisation of databases that are non relational like mysql, pstgresql
* ex — MongoDB, Cassandra, Redis, Couchbase, and Neo4j.
* these are interesting as shard out of the box (horizontally scalability)
* document db
  * ex — mongo db, elastic search
  * mostly json based
  * supports complex queries (almost like relational (Sql) databases
  * partial updates to document possible (can do total-post+=1 without rewriting the entire document). one doesn't need to read the entire document to update a single info
  * closest to relational database
  * use-case: in app notification service, catalog service(for e-commerce site)
* key value store&#x20;
  * ex— redis, dynamodb, aerospike
  * extremely simple db
  * limited functionalities (get, put, del)
  * ment for key based access pattern
  * does not support complex queries like aggregation
  * can be heavily shared and partitioned
  * use-case: profile data, order data, auth data
  * one can use relational db and docuemntdb
* Graph database
  * ex— neo4j, neptune, DGraph
  * what if graph data structure had a database
  * it stores data that are represented as nodes, edges and relations
  * ex— A — > B&#x20;
  * great for running complex graph algorithms and running the graph algorithms will be hard to implement over the key value stores&#x20;
  * powerful to model social networks, recommendation and fraud detection

## <mark style="color:purple;">Picking the right data base</mark>

* not a fight, so no need to pick a side
* a db is designed to solve a particular problem really well
* each kind of db picks a segment with a slight overlap
* common misconception
  * picking non relational db because relation db do not scale
* why non-relational DB scale
  * there are no relations and constrains
  * data is modelled to be sharded (split across multiple db)
* if we relax the above on relational db we can scale it too
  * do no use foreign key
  * do not use cross shard transaction
  * do manual sharding
* Does this mean no DB is different&#x20;
  * no each single db has some peculiar properties and guarantees and if you need those you pick that DB
* How does this helps in designing system
  * while designing any system
    * do not jump to a particular DB right away
  * Understand WHAT data you are storing
  * understand HOW MUCH of data you will be storing
  * understand how you will be ACCESSING the data
  * what kind of QUERIES you will be firing&#x20;
  * ANY SPECIAL feature you expect ex— expiration
* how to pick the right DB (not exhaustive&#x20;
  * if a data can fit on a single node
    * relational DB -you need strong consistency? data correctness is needed
    * relational DB— need complex queries, aggregation
    * redis — access is KV based but it needs it to be really fase
    * redis — need advanced data structures and algorithms
  * if data can't feed in one node
    * relational db — if you have expertise in sql and can do manual sharding
    * kV store like DDB or mongoDB — simple KV based access
    * graph DB like Neo4J — require sophisticated graph algorithms
    * mongoDB or any document DB — nothing specific but want future proof

## <mark style="color:purple;">Caching</mark>

* Caches are anything that helps us avoid an expensive any network i/o, disk i/o or computation. EX--
  * API call to get profile information
  * reading a specific line from a file
  * doing multiple table joins
* Store frequently data in a temporary storage location.
* user sends a request to api if the data is present in the cache then we get that from cache if not we fire a query and stores them in cache for future and then send the data to user
* Caches are faster and expensive hence we don't cache all the data (we just cache a subset of data that is more likely to be accessed)
* we typically use redis, memcache.
* what to cache— recently published tweets, as these are more likely to be sent to the user.
* Note— caches are not restricted to ram based storage, any storage that is neare and helps us avoid something expensive is a cache for us.
* One can call cache as glorified hash tables.
* &#x20;Example --
  * google news — most recent news articles are stored in cache to be accessed, hence served from cache
  * auth tokens— authentication are cached in cache to avoid load on the db
  * live stream — last 10 minutes of live stream is cached on CDN as it will be accessed the most&#x20;
* excercies--
  * setup redis locally&#x20;
  * put and get some data
  * measure time taken&#x20;
  * compare it with database

## <mark style="color:purple;">Populate and scaling a cache</mark>

* cache sits between the API server and the database.
* there are twoo ways to populate the cache.
* Lazy population (most popular)
  * read first go to the cache, if the data exists, return data , else go to database/do heavy operation persisits in the cache, return data.
  * ex— caching blogs we can store the json&#x20;
* Eager population&#x20;
  * write s go to both the db and cache in the same request call
    * ex— live cricket score
    * thousand of people are watching score, we will be serving it from cache . so why not update cache and db at once and save the cache miss.
  * proactively push the data to the cache, as we anticipate the need
    * ex— when a celibrity tweets/posts something
    * when account with 10000 followers post something, proactively put it to the cache.
    * we will anyway need itt and save a cache miss
*   Scaling a cache&#x20;

    * cache is similar to a db hence scaling a cache is similar to a regular db.
    * vertical  scaling --
      * make you cache bigger to handle more data/load
    * horizontal scaling (replica) scaling reads --
      * same data replicated to multiple nodes so that reads could scale&#x20;
      * api -> master + replica
    *   horizontal scaling (sharding )

        * data partitoned across multiple shards so that writes could scale
        * each shard can have multiple replica



    &#x20;

## <mark style="color:purple;">Caching at different levels</mark>

* Most common cache we see was redis
* but that is not the only type of cache out there or not the only place that can be used as a cache
* literally every piece/component in out infrastructure caches something for us.
* Also too much caching is bad— stale data and invalidated data
* Client side caching&#x20;
  * storing frequently accessed data on client side/user side.
  * ex— browser, mobile devices etc.
  * cache near constant data. ex— images, js files, user information etc.
  * it should be okay serving the cached into(stale)
  * invalidation by time(expiry)
  * massive performance boost as we need not make any request to the backend
* CDN
  * content delivery network
  * set of servers distributed across the world
  * request from a user goest to the nearest CDN server and hence user gets very quick response
  * US folks getting images from US servers is faster than fetching it from india
  * CDN does lazy cache population
  * user request coms to the CD(closest server)
  * CDN server checks if it has the data it yes return the data to origin else, cdn makes the same request.
  * hosting is in aus, the CDN is in front of the infra . the request to the cdn goes to the aus if the csn doens't have the data . the data from aus goes back to the cdn and then gets served to the user.&#x20;
* Remote cache(redis)
  * centralized cache that we most commonly use redis.
  * multiple API servers use it to store frequently accessed data
  * every key stored should have an expiry(memory leak)
  * size of the cache is relatively small as compared to the DB
* Database caching&#x20;
  * instead of computing total post by users everytime we sotre the total post as column and update it one a while&#x20;
  * saves an expensive DB computation.
  * every time a post is published also update the users table which has the total posts and do total-posts=total-posts+1
* Notes — there are other places like load balancer where we can cache&#x20;
* note — we can cache some data at every single component in the system but should we do it — no necessarily ( it is very use-case specific and subject to tolerance level of staleness of the served data)
* Just because we can it doesn't means we should



## <mark style="color:purple;">Asynchronous processing</mark>&#x20;

* User sent the request and we immediatly handle it is synchronous&#x20;
* loading insta feed is synchronous logging on a website is synchronous payments are synchronous
* Most interactions on the web are synchronous&#x20;
* some things that should not be synchronous
  * spinning up a VM as it takes time and user will not on the same page waiting for the response
  * instead he/she would love to move around and keep checking status once in a while
  * this is asynchronous.
* message queues/brokers
  * brokers help two services/application through messages.
  * we use message broker when we want to do something asynchronously&#x20;
  * ex— video processing&#x20;
  * once the video is uploaded we need to convert to other resolution&#x20;
  * when a video is uploaded a message is sent to message broker where we send the details of the video which is sent to the workers which processes the video
  * Features of message broker
    * brokers help us connect different subsystems&#x20;
    * brokers act as a buffer for the messages thus the consumers can consume at their own pace, ex— notification system&#x20;
    * workers can retain the messages for n days (depends on the broker)
    * brokers can re-queue the message if not delated
    * ex— consumer read the message but before it could delete it , it deletes.
  * Typical flow&#x20;
    * ex — auto subtitle(auto captioning)
    * user uploads video to s3 through video service&#x20;
    * video service puts a message(after upload completes to brker and returns response to the user(user sees upload complete )
    * message is asynchronously read by captioner service
* Exercise
  * setup rabbit mq locally
  * write some code to push and read messages
  * go through the documentation&#x20;

## <mark style="color:purple;">Message streams</mark>

* similar to message queues
* ex— wheenever a new vlog is uploaded , we want to index it in a search engine and do a count ++ for the user total blog
* Approach 1 : we use one message brokder nad logic in consumer table&#x20;
*

    <figure><img src="../../../.gitbook/assets/image (128).png" alt=""><figcaption></figcaption></figure>
* Problem with this is what if the job fails after one tasks
* Approach 2:&#x20;
  * two brokers and two set of consumers&#x20;
  * api servers writes to two brokers and each has it s own set of consumers&#x20;
  * this still does not solve the problem&#x20;
  * hence we want write to one and read by many semantic&#x20;
  * this is where message streams come into the picture
* approach 3 : message streams&#x20;
  * similar to message brokers with one change : multiple types of consumers read the same message
  *

      <figure><img src="../../../.gitbook/assets/image (129).png" alt=""><figcaption></figcaption></figure>


  * api server pushes one message in kafka, search and counter service both reads the message and does their work
  * message quees— sqs, rabbit mq
  * message streams — kafka, kinesis here we have consumer groups like search service and counter service.
    * the consumer group iterate over the message in the same order.
    * the messages lie in the order and stay there forever.
* Kafka essentials&#x20;
  * message streams that holds the messages internally kafka has topic&#x20;
  * each topic has n partitions&#x20;
  * message is sent to a topic and depending on the configured hash key it is \[ut into a partition&#x20;
  * within partition messages are ordered, no ordering guarantee across partitions&#x20;
  * limitations of kafka
    * \#consumers =#partitions&#x20;
  * when we commit a message we can mark that message that we have read this message
* excercis
  * setup kafka locally
  * write some code to push and read messages
  * go thorugh the documentation



Realtime PubSub

* Both message brokers and messages strams require consumers to pull the messages out.
* realtime pubsub makes things reactive instead of continuous pooling &#x20;
* advantage: consumers can pull at their own pace consumers do not get overwhelmed
* disadvantage — consumption log when high ingestion&#x20;
* what if we want low latency? Zero lag \_> realtime pubsub
* instead of consumers pullting the message, the message is pushed to them ex— redis pubsub
* this way we get really fast delivery time, but it can overwhelms the consumers&#x20;
* what if consumers receive message faster than they could process&#x20;
* practical use case : message broadcast, configuration push.
* ex—&#x20;
  * setup redis locally
  * go through redis pubsub dosumentation&#x20;
  * rest realtime broadcast
  * test it it persists the message.



## <mark style="color:purple;">Load balancers</mark>&#x20;

* One of the most important component in distributed system that makes it each to scale the load horizontally
* load balancer is the only point of contant.
* it abstracts of the details of the system, what apis are there how may servers are ther e
* every load balancer has either&#x20;
  * static IP
  * static DNS name
* Load balancer hides the #servers that are behind it allowing us to add as many servers as possible without client knowing about it.
* Request response flow
  * client already has ip/domain of laod balancer ex — auth.example.com
  * client makes api call and it comes to the load balancer — ex— get auth.example.com/login
  * load balancer picks one server and makes the same request
  * load balancer gets the response from the sever
  * load balancer responds back to the client.
* Job of the load balancer is to balance to load. it does that using load balancing algorithms.&#x20;
  * round robin&#x20;
    * distribute the load iteratively, uniform distribution
  * weighted round robin&#x20;
    * distribute the load iteratively but as per weights , non uniform laod
    * used when we have non uniform servers
  * least connection&#x20;
    * pick the server having the least connection from the load balancer , used when the response time has a big varriance.
  * hash based routing&#x20;
    * hash of some attribute ip, userid , url determines which server to use.
    * random enough&#x20;
    * one can build a sticy session where the request from one user goes to one particular server.
* &#x20;Key advantages of load balancers
  * scalability&#x20;
    * with more servers behind the load balancer we can how handle more requests&#x20;
  * availability&#x20;
    * even if one of the servers crash it does not take down our entire systems.
    * load balancer will forward request to other healthy servers. improving the availability
    * with s2 down load balancer will forward any new request to s1 and s3.

## <mark style="color:purple;">Circuit breakers</mark>&#x20;

* prevent cascading failures&#x20;
* no matter whatever happens it stops the failure at some point of time, it helps in minimising a complete collapse of the system.
* ex--
  * social network (feed)
  * feed service depends on recommendation and trending service. trending service depends on post service which depend on profile service.
  * profile service depends on profile db and post db depends on post db.
  * if for some reason profile db is on heavy load profile service load time shoots up then all the features that depends on the profile db shoots up.
  * if it is too slow the http connections start taking time and this hogs up the number of http request one service can accept.
* to solve this we make a cal to a service only if the service is healthy (this is circuit breaker) . We break the circuit down when we see the failure cascade.
* It prevents the entire product from collapsing by preventing cascading failure
*   How is it implemented

    * a common db holds the settings for each breakers
    * service before making calls to others, checks the config. Cache the config to avoid checking the db



    <figure><img src="../../../.gitbook/assets/image (66).png" alt=""><figcaption></figcaption></figure>


* Circuit breaker DB--
  * in case of the outage the circuit is tripped and DB is updated
  * services will periodically check and stop sending requirement to affected service
*   Excercis

    * implement a simple circuit breaker(DB)
    * Write a simple service that checks this settings every-time before calling&#x20;
    * ex — post service serves post
    * profile service serves profile
    * post service calls profile for info



## <mark style="color:purple;">Data redundancy and recovery</mark>

* API servers are stateless but databases are stateful
* api servers going down is fine because a new one will spinup instantly
* api servers gets request and it does not matter which one handles it
* DB going down is catastrophic almost always an outage



* A good system always takes care of catastrophic situation
* the only way to protect ourselves against loss of data is to create multiple copies of it -> data redundancy
* Redundancy can be implemented at row/document level, table level or db level.
* Redundant data can be stored on different table, different db or different regions
* Backup and restore
  * daily backup of data(incremental)
  * weekly complete backup
  * storing one copy across region -> disaster recovery
  * When something goes wrong just restore the last backup
  * almost always the easies thing to do
* Continuous redundancy
  * setup replica of the DB and writes goes from both DB(sync/async)
  * API server writing to both db
  * API writes to one and is copied to other asynchronously
  * If the main DB goes down replica can takes its place

## <mark style="color:purple;">Leader Election for auto recovery --</mark>

* Leader election is base condition of recursion, if one server goes down then another server comes up.
* Orchestrator spins up a new machine and puts up behind the load balancer.
* When one orchestrator is down somehow other artitecture comes back up and takes responsibility.
* In a leader-orchestrator setup we have orchestrator leader(this keeps on eye on the orchestrate leader)  and orchestrator worker (this keeps up an eye on the server. When a orchestrator leader goes down one of the orcehstrator worker gets promoted to the orchestrator leader.

Client- Server model --

* most common way for machine to talk with each other.
* client (demands a job) and server (does the job)
* The communication happens over the common network connecting the two.
* Two protocols uses are TCP(most of the time) and UDP.
* Some important properties of TCP --
  * breaks because of network interruption
  * breaks because server/client initiated it
  * Hence connection remains open almost forever.
* Protocol over TCP --
  * TCP does not dictate what data can be sent over it common format agreed upon by client and server is called a protocol:HTTP
  * HTTP is the language and TCP is whether we are speaking or writing.
* HTTP --
  * http is a format that client and server understands, one can make their own format and make
    * client send data in it
    * server passes and processes it
  * There are many versions of it — HTTP1.1/ HTTP 2/ HTTP3
  * HTTP 1.1 is the most commonly used one&#x20;
    * For client and server to talk over HTTP 1.1, they need to establish TCP connection
    * Connection is typically terminated once response is sent to client&#x20;
    * Almost new connection for every request/response&#x20;
    * Hence people pass "connection: Keep-alive" which tells client and server to not close the connection
* Web-socket --
  * heart and soul of any real time communication&#x20;
  * there are bi-directional communication.
  * Key-features: server can proactively send data to client, without client asking for it.
  * As there is no need for setting up TCP, every single time we get really low latency.
  * Anywhere we need realtime, low latency communication is needed your end user over the internet think about web socket.
  * ex— chat, realtime likes on live stream, stock market ticks.
* ex — build a chat application using SocketIO

## <mark style="color:purple;">Blob Storage and S3 --</mark>

* blob — binary large object.
* earlier when people uploaded any files they uploaded it to server and were stored on the hard disk attached to it
* getting a file was simple, make and API call , the handler reads the file and return
* This is precisely how static folders/ routed worked.
* When user uploads the file —&#x20;
  * accept it on HTTP POST
  * create absolute path using folder&#x20;
  * store the file at 1 location
  * Early days of internet
* This worked well for some time but it can't work with multiple servers
* Hence, we need to infinitely scalable network attached storage/file system
* Any file that needs to be accesible by any server is stored at places accible by all. Here API servers are now stateless
* On S3 you have&#x20;
  * bucket (namespace) eg: insta-imagees, mybucket
  * keys : path of the file within bucket
  * s3;//insta-images/user123/72896.png. \[bucket/key]
* One can seamlessly, create the file, store file can be stored in s3.
* Disadvantages--
  * reads are slower&#x20;

## <mark style="color:purple;">Bloom Filter</mark>&#x20;

* Makes sure a particular this is definitely not part of it.
* Key insight: Once something is "watched" you cannot take it back
  * once a part/reel is watched by you we do not take it out of the set.
* This means, storing actual data is not work it.
* This is the concept over which bloom filters are setup
* Bloom Filter --
  * Filter — bit array ( we take 8 bit array)
  * when bloom filters says it is not preset it is 100 percent sure but if it says that it says yes then it is not sure&#x20;
  * redis has it as one of its core feature, now a days mostly people go for it.
* Practical application
  * use it whenever  we insert but not remember
  * need a no with 100% certainty
  * having false positive is okay

## Consistent hashing --

* One of the most amazing and popular algorithm out there&#x20;
* We first understand consistent hashing and ten look into practical implementation.
* Hash based ownership --
  * say we have a load balancer and when a request comes it it uses hash function to route the request of the user to a particular server.
  * Note— hashing logic is not a service but just a simple code running in the load balancer's code.
  * If one of the server is taken down then the routing function changes now the request will be every distributed between remaining two servers so no hiccups
  * This is why we use hash based routing as one of ht almost common ways of routing for stateless backends.
  * But the problem arises when the Backend is stateful
    * picking which node owns the data depends on the hash based ownership.
    * So the request for the same key should go to a particular node.
    * Challenge : if a node is removed or added then the output of the hash function changes.
    * To solve this we need to move the key to the new node to make sure after any change the hash function is working as intended.
* To solve this we use consistent hashing --
  * THis algorithm helps in determining data ownership.
  * It will not do data transfer for us.
  * It is not a service in itself.
* How consisiten hashing works --
  * Look it as a ring.
  * Given a hash function are cyclin we can visualize it as a ring of integers .
  * Every node occupies one slot in th ering, the slot is calulcated by passing node's IP adress
  * This ring can be modelled as a simple array and can be part of proxy.
  * the process is&#x20;
    * key k1 -> hash function -> 0 -> node to right -. node 0
    * key k2 -> hash -> 10 -> node to right -> node 3
  * Scaling up --
    * when we add a new node to the ring&#x20;
    * Say node 3 hashes to slot 1&#x20;
    * the key that hased between slot 12 and slot 1 will now be owned by node 3 insted of nde 0
    * Other keys continue to remain at their respective node (minimal data movement)
    * operationally&#x20;
      * snapshot node 0
      * create node 3
      * delete unwanted keys
  * Scaling down --
    * say we scale down and remove node 0 all the keys that were owned by node 0 will now be owned bt node 2 next in the ring&#x20;
    * minimal data transfer&#x20;
    * operationally&#x20;
      * copy everything from node 0 to node 1&#x20;
      * create node 2 from the ring
      * delete unwanted keys.
* Exercise --
  * understand consistent hashing
  * read the blog
  * implement it in your favourite programming language
    * 2 arrays +binary search

## <mark style="color:purple;">Big Data processing --</mark>

* Set of tools that helps up process the large amount of data in parallize manner
* problem — count number of words in 1 tb text data
  * approach 1 : run a loop which finds a space and increases the counter&#x20;
  * approach 2 : pareelalize this so that we can divide the data and process it using threads
  * approach 3 : if we have multiple servers we make one server as coordinator which divides the data into multiple chunks and and the coordinator passes the chunk and sends it to the worker&#x20;
  * we have tools for this known as spark and other which helps us in solving the big data taks&#x20;
  * we just need to write the business logic and tools will handle the distributed computation.&#x20;

## <mark style="color:purple;">Real world systems --</mark>

1. <mark style="color:purple;">**E-commerce product listing page**</mark>
   * Shop owner has to list 100 products&#x20;
   * Requirements --
     * add a new product
     * update/delete existing product
     * list all the products on the website
     * customer should be able to quickly access the catalog&#x20;
   * Storage --
     * not huge data only 100 rows (fits into single node)
       * 100 \*1 kb = 100 kb data
     * There seems to be a structure we can go ahead with sql DB as we have a structure although any db can be used
       * product table to hold all the catalog
   * Serving the data
     * simple REST based HTTP web-server is fine
     * we will need many (handle request)
     * hence put a load balancer
     * load balancer will have DNS like api.mystore.com
   * Artitecture till now
     * user -> LB-> api server->DB
     * instead of drawing LB and multiple servers every time we can draw simple digram &#x20;
     * user -> catalog backend -> catalog db
     * now adding a frontend
     * user -> catalog frontend -> catalog backend -> catalog db
   * Show owner admin interface&#x20;
     * shop owner needs admin console to manage the catalog&#x20;
     * we will have admin ui which makes call to the backend to update the catalog
     * user -> frontend -> backend ->(admin ui) and backend
   * Understand load on each component
     * the call on the frontend is balanced as we have lb on the frontend as this can be scaled independently
     * for the admin UI we can use 1 server is good enough as long as it is available all the time&#x20;
     * Backend will have decent load , so we would need another lb to scale the backend&#x20;
     * As end user is only reading and not writing, so for DB we create a read replica so that read request goes to the catalog DB replica and the catalog master handles the write&#x20;
     * the master can handle the read request as well.
     * We can add the cache as well so that but we would need to handle the cache invalidation as well. we would have to make sure the data in cache is latest.
   * exercise --
     * design DB schema for this system
     * write a simple backend API service exposing APIs
     * Setup DB replication&#x20;
     * Move read APIs to read from replica.
2. <mark style="color:purple;">**Design API rate limiter**</mark>
   * System break down under tremendous load and we need to ensure that doesn't happen
   * Design a rate limiter that
     * limit the number of request in a given period
     * allows developers to configure threshold of a granuer level
     * does not add a massive additional overhead
   * Where does it fit&#x20;
     * if we have a frontend proxy then the request goes to rate limited to check if we can pass the request
     * if yes then the request is passed to the service
     * or if we have lb it goes to the service, then it check if the request is within the limits
   * Dissecting rate limiter
     * rate limiter needs to track number of request in a given period
     * hence we need a db to hold the count but which one&#x20;
       * for every incoming request we could update the db
       * for every incoming request we would read from db(aggregate)
       * we need ability to clock the time&#x20;
     * So we choose redis to store the key value store and update the counter for every new request
   * The rate limmiter is now only a redis db, but we need to scale this
   * this db is write heavy so we can use shard&#x20;
