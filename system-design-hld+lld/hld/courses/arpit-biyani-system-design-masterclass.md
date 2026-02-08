# Arpit biyani system design masterclass

## <mark style="color:purple;">Foundational topics in system design |</mark>

* <mark style="color:red;">**To get most value**</mark>
  * speak up and participate
  * note down new terms that you hear
  * read about them later
  * Revise
  * Ask questions
  * If you need 1:1 help do not hesitate
* <mark style="color:red;">**What is system design**</mark>&#x20;
  * requirements -> system -> product development ( artitecture, service, module, application )
    * artitecture design - macro components (bird eye view)
    * Logical design business logic (algorithms, database, work flows)
    * Physical design — input/output interface storage, processing  \[ capacity planning , H/W decision UI framework, Backup and restore redundancy ]
* <mark style="color:red;">**How to approach system design?**</mark>
  * define the scope --> most important
    * <mark style="color:yellow;">functional scope \[pm]</mark>
      * features for the user&#x20;
        * follow tweet retweet
        * input and output
      * input behaviour and output
        * something that system must have
    * <mark style="color:yellow;">non functional \[tech lead]</mark>
      * how the system should be implemented
      * redundancy, availability, security
      * speed security and reliability
      * something that determines system operational capability
* <mark style="color:red;">**while designing any system**</mark>&#x20;
  * do not assume
  * ask critical question
    * challenges product/ engineering decisions
  * Seek clarification
* <mark style="color:red;">**Building systems**</mark>
  * bottom up
    * build individual components that run in harmony
  * incremental&#x20;
    * build MVP
    * incrementally add features
    * complexities to it
* <mark style="color:red;">**Golden rule — start small and build on top**</mark>
  * define building block
  * define relationships between them
  * define communication
  * identify bottle necks and fix them
  * No premature optimisation&#x20;
* <mark style="color:red;">**Building a blog**</mark>
  * **requirements**
    * single user blog
    * multiple posts
    * each posts has tags
    * browsing by tags
    * Search bar— enabling readers to find blogs
    * Blogs ordered by time (most recent first)
    * Blogs should support !m concurrent readers
    * blog should be available
    * blog should be fast(subjective)
  * For any system we need mostly 6 components (each of the above decision will define how the system scales)
    * database
    * caching&#x20;
    * scaling
    * delegation \[one of the best ways to gain performance out of any system like message queues]
    * concurrency&#x20;
    * communication
  * Lets go in details to all of them
    * **Databases** (each one is defined for a certain use case but each one comes up with their advantages and disadvantages)
      * in memory — caches
      * disk based — my sql mongo db etc
      * server'ed — db that has its own server to run&#x20;
      * embedded — db which runs within the same application
      * row based — how the data is structured in db
      * columnar
      * graph db — model which models subject object relationship
      * time series DB — time along with metric, handling time series data
      * Relational — sql
      * Non relational — no sql
      * Blob storage — data is just binary long object
      * Flat file storage — store flat files (blob storage where we can query on them)
      * Storages for text based search — elastic search&#x20;
  * Tables
    * users&#x20;
      * id
      * name
      * bio
    * blog
      * id&#x20;
      * title
      * user\_id
      * published\_at \[ we can store them in datetime or in epochs (ints) which is much faster than datetime or we can also store them in string of format YYYYMMDD ] one can store the time in UTC and store it and send it on user time on the fly.
      * is\_deleted
      * body \[ we can store body but storing big data like images into the db can lead to performance of the db, instead we can use an external reference to the large data bodies like images]
      * excerpt
      * slug
      * updated
    * tags
      * id
      * name
    * blog\_tags
      * blog id
      * tag\_id
  * Stuff to read&#x20;
    * database redundancy&#x20;
      * standby
      * 1:1
      * N:N
    * Storage redundancy
      * RAID
      * Goe redundancy
      * data center redundancy
  * Now we need API server
    * user  -> API  -> database
    * API server will
      * talk to db on behalf of the user (abstraction)
      * hasve all the business logic
        * authorization checks
        * authentication
        * post process/ filter results
  * Search box
    * To build an efficient search on top of our blog we can use elastic search where we store all the data and use specialized db for searching.
    * So we have to make sure that the data is replicated in two db the original mysql db and elastic search db.&#x20;
    * For this blog we can skip the mysql db and store everything in the elastic search db. but of some usecase we would need both db as elastic search doesn't gives us ACID properties.
  * Caching&#x20;
    * as this is read heavy system we would need caching but these are very costly
    * it improves the locality of reference&#x20;
    * recent data most likely to be accessed
    * caches existes at all the levels in the artitecture
    * cache hit (maximize) and cache miss (minimize)&#x20;
    * one can use cache at all the places in the system.
    * Application server cache \[most underrated ]&#x20;
      * request layer cache \[request lccal]
    * central cache&#x20;
      * redis memcache&#x20;
    * central and distributed cache
      * shareded data
    * CDN&#x20;
      * akamai
      * cloudflare
      * helps in geographic spread
      * mostly static data is cached in the CDN
      * Caches also sit next to db&#x20;
        * seamless access and transport
      * Honarable mentions
        * DIsk cache
        * cpu cache&#x20;
        * and GPU cache
    * What one should read
      * cache eveiction policies
    * CHarachteristics of cache
      * build hhigh through put systems
      * volatility
      * not for transactonal data as these might go stale
      * quick reads and writes
      * super expensive
      * meant for performance
      * reduces disk I/O on DB&#x20;
      * Network I/O on network calls
    * Types of cache
      * write through cache&#x20;
        * Write to DB and write to cache
        * both write pass then write pass
        * data persent at both places everytime
        * pros fast retrivals data consistency fault tolerance
        * higher write latency
      * Write around cache
        * write to db but no write to cache
        * good for applications that do not read immediately after write&#x20;
        * Pros no flooding cache with unused writes
        * Cons recently written items will be cache missed higher read latency \[first read]
      * Write abck cache
        * write to cache and I/o done
        * data written to db in background
        * low latency high through put and write intensive application
        * data availability risk
  * Content delivery network
    * get content from closest availaqble servers instead of hitting main servers
    * improve speed (response times) by keeping content close to users
      * dirty content served to users (time for invalidation)
      * caching policies and confugirations
    * we store content that does not change often image, vid, text, api
  * Lets add cache to our blog
    * ![](<../../../.gitbook/assets/image (136).png>)<br>

## <mark style="color:purple;">Foundational topics in system design ||</mark>

* <mark style="color:red;">**Caching at various level**</mark>
  * DNS level
  * API server (in memory of the server or on- disk)
  * Client side
  * Pre computed (materilized table views) good on read havy system
  * Load balancers
  * centralised cache
  * transparent cache in front of DB ex — cacheops&#x20;
  * API Gateway cache
  * DB buffer pool
* <mark style="color:red;">**Scaling**</mark>&#x20;
  * what is scalability&#x20;
    * number of requests system can handle simultaniously
    * add more resources to the infrastructure
  * this refers to the scaling strategy to be applied
    * horizontal — the minions
      * adding more machine&#x20;
      * we have load balancers to balance the load to multiple machines of our load balancers
      * load balancers in itself are multiple machines which talk with each other to make sure the laod is balanced among multiple servers.
      * although we have multiple machines behind lb but the db is not scaled till now as it can't handle that many requests, so all db elastic search all others needs to be scaled accordingly.
      * So how to scale db horizontally
        * master/worker — master writes and worker reads
        * sharded/ distributed db — we shard the database and partition the data . we can again replicate the sharded db&#x20;
    * vertical — the hulk
      * vertical infrastructure bulky scalling up
      * more CPU, RAM and disk
      *
  * So which strategy to use&#x20;
    * do not over optimize
    * do not over provison
    * Do sequentially
      * start by vertical scaling&#x20;
      * then add read replicas
      * then shard the database
* <mark style="color:red;">**Delegation**</mark>&#x20;
  * <mark style="color:purple;">**what does not need to be done in realtime should not be done in realtime**</mark>
  * delegate whatever at hand and respond
  * typical flow of delegation
    * Client makes a request server delegates and immediately returns a response task picked up by workers . workers upon completing it updates the database&#x20;
    * cleint constantly pings through a seperate API call to fetch the status (optional)
    * effectively manage requests in a large scale distributed system(asynchronous)
    * Client no longer need to wait for task to be completed
    *   makes it possible for applications/ services to communicate asynshronously

        <figure><img src="../../../.gitbook/assets/image (137).png" alt=""><figcaption></figcaption></figure>
  * How this works&#x20;
    * we use  queue it can be either notaml queue or a fifo queue
    * what should be the features of such queue
      * expiration , delay at most once, at least once
      * exactly once waits untill message retreived
  * Two common implementation
    * <mark style="color:red;">**message queues**</mark>
      * sqs rabbit mq, redis etc
    * <mark style="color:red;">**data streams pub sub**</mark>
      * kafka kinesis
  * ways to delegate
    * pub-sub — some publisher which are publishing the data and there are subscriber which are reading the data&#x20;
    * this is done my message brokers like SQS, RabbitMQ, Redis etc.
      * Getting basic analytics
* <mark style="color:red;">**Concurrency**</mark>&#x20;
  * one being able to run multiple  jobs with an impression that all jobs are running at the same time&#x20;
  * read on concurrency vs parellelism
  * Sync- async programming model
    * task executed one after other -sync
    * tasks could context switch in b/w aync
    * programming languages upon above model&#x20;
      * node js
      * java
      * python
  * Issues
    * communication b/w sub-componenet complex
    * number of possible execution path — very large
    * indeterminals outcomes
      * depending on the execution path
    * concurrent use of shared resources
      * high DB connections
      * same memory blocks without locks
    * complicated cordination and data exchange
    * How do we handle concurrency
      * make things thread safe
        * locks
        * lock free data structure
    * Dead lock and starvation
    * parellel algorithms
    * lock free data structures (conflich free)
    * persistent data structures
* <mark style="color:red;">**How will our users communicate? what if we want to show**</mark>&#x20;
  * communication decisions determine how different components of the system will communicate with each other
    * client server
    * client client -> p2p
    * inter service communication
    * intera service communication
    * inter process communication
    * intra process communication
  * the usual communication
    * <mark style="color:red;">**client (demands) -> <- server (fulfills)**</mark>
      * channel is kept open (optional timeout)
      * server does the heavy lifting
    * <mark style="color:red;">**short polling**</mark>&#x20;
      * bad idea&#x20;
      * client keeps bugging every x seconds to send the data  for nay new data the server will response instantly but not sure about the data . whatever it has it sends
      * the connection&#x20;
      * ex — criccbuzz , provisioning a server on the cloud
    * <mark style="color:red;">**http long polling**</mark>&#x20;
      * same as short polling except it does not send emty response&#x20;
      * in case of timeout the process needs to be repeated — establish connections and requests&#x20;
      * what is the difference b/w short and long polling
        * server does not sends empty response&#x20;
          * this will lead to connections being open untill&#x20;
            * server sends the data&#x20;
            * connection timeout
          * client reconnects when connection times out
    * <mark style="color:red;">**Web sockets**</mark>
      * client  --— bidirenctional channel --> server&#x20;
      * best for quick response cases chats
      * instead of client asking for data server can proactively send the data to client over the established channel&#x20;
      * all the data reaches the client depending on when the data is available
        * real time data transfer
        * low communication overhead why
        * which protocol could it be using and why&#x20;
        * when data changes frequently
    * How will our client talk to server for blog
      * HTTP rest

## Relational databases

* Data records stored in tables (rows and columns)
* tables related to other tables using 1:1, 1:N N:N relation
* Normal forms: 1NF, 2NF, 3NF each new normal forms reduces redundancy
* Ex DB: MYSql Oracle, PostgreSQL
* Constrains on columns and combination of columns like Unique, Not Null, Primary key, Foreign Key
* Transactions (because of transactions relational DB are being used in the industry)
  * Atomicity - each transaction is a single unit of execution
  * Consistency : Database goes from one valid state to another&#x20;
  * Durability: Post commit data is recoverable even after failure of system
  * Isolation: Concurrent execution= sequential execution&#x20;
* Database index -—&#x20;
  * primary job is to improve lookups
    * improves lookup performance HOW?
    * Decreases the write performance HOW
  * Index is typically a small table having two columns primary/ candidate key — disk where KV stored
  * Data structure used : B+ tree internal and serialization of B+ tree are good to know
  * Types of index&#x20;
    * primary index — key — address to data
    * clustered index — index+data residing together ordered by the key Huge boost in performance recording and updated expensive.
    * Secondary index, Composite index, Partial index etc.
* Databaase locking&#x20;
  * Read lock/ shared lock
    * reserved for read by the curresnt session&#x20;
    * other session can read the locked data
    * other sessions cannot write update locked data
  * Write lock/ exclusive lock&#x20;
    * reserved for write by current session&#x20;
    * other session cannot read/write locked data
    * lock granuality : table, row, column, page level locking&#x20;
  * if engine detects deadlock then it kills the transation running which the deadlock was detected
  * why do we need DB locks when we can handle it at application side
    * Distributed arch/locks needs a single source <— Multiple app and process
  * Locking reads
    * if you query the data and then INSERT/UPDATE within same transaction, regular select statement does not give enough protection \[auto  commit=off]
    * Select .... for share ;
      * shared mode locks on any rows that are read, other sessions can read but not modify if rows modified other transactions waits until the first one ends and then uses the latest values&#x20;
    * Select ...... for Update
      * locks the rows and any associated index entries other transactions are blocked from updating these rows or even reading them in creation isolcation levels&#x20;
      * Useful when dealing with tree structured data in same or split tables

## Non relational databases

## Distributed systems

## Distributed ID generators

## Social networks |

## Social network ||

## Storage Engines ||

## High throughput systems |

## High through put systems ||

## Information retrieval systems |

## Information retrival systems ||

## Algorithmic system design |

## Algorithmic system design ||
