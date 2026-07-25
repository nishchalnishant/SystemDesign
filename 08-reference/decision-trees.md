# Decision Trees — "Which One Do I Pick?"

> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Technology-selection decision trees for the moments in an interview where you must *choose* — SQL vs NoSQL, which consistency model, which cache strategy, push vs pull, sync vs async, and more. Each tree ends at a concrete choice with the one-line justification you'd say out loud.
>
> **Key concepts:**
> - These are *selection* trees (pick a technology/approach), not concept-recall maps — for topic recall see [../FLOWCHARTS.md](../FLOWCHARTS.md).
> - Every leaf names a default and the trigger that would change it — interviewers reward "I'd pick X, but switch to Y if Z."
> - Read a leaf as a starting position, not dogma; state your assumption, then commit.
>
> **Key takeaway:** In an interview, don't enumerate options — traverse the tree out loud to a decision, then justify. Indecision reads as junior; a defended default reads as senior.

---

## How to use these

Interviewers don't want a survey of every database. They want you to **pick one and defend it**. Each tree below encodes the discriminating questions in the order that actually matters, so you converge fast and can say *why*. The pattern to voice: **"Default X because [property]; I'd switch to Y if [trigger]."**

---

## 1. SQL vs NoSQL

```mermaid
flowchart TD
    A[Storing what?] --> B{Strong relational<br/>integrity + multi-row<br/>transactions needed?}
    B -->|Yes| C{Scale beyond<br/>one primary's<br/>write capacity?}
    C -->|No| SQL[Relational: PostgreSQL/MySQL<br/>ACID, joins, mature]
    C -->|Yes| NewSQL[NewSQL: CockroachDB/Spanner/Vitess<br/>distributed ACID, horizontal writes]
    B -->|No| D{Access pattern?}
    D -->|Key lookups,<br/>massive write throughput| KV[Key-Value / Wide-column:<br/>DynamoDB, Cassandra]
    D -->|Flexible/nested docs| DOC[Document: MongoDB]
    D -->|Full-text / relevance| SEARCH[Search: Elasticsearch]
    D -->|Relationships-as-first-class| GRAPH[Graph: Neo4j]
    D -->|Time-series metrics| TS[Time-series: InfluxDB, TimescaleDB]
```

**Say it:** "Start relational — ACID, joins, and it scales further than people think with read replicas + partitioning. Only leave SQL when a *specific* pressure forces it: write throughput past one primary (→ Cassandra/DynamoDB), schema flexibility (→ document), or a query shape SQL is bad at (full-text → ES, traversals → graph)."

---

## 2. Consistency model

```mermaid
flowchart TD
    A[What breaks if a read<br/>is stale?] --> B{Money / inventory /<br/>correctness-critical?}
    B -->|Yes| C{Single region<br/>or global?}
    C -->|Single| SC[Strong consistency<br/>single-leader, synchronous]
    C -->|Global| CP[Consensus: Raft/Paxos<br/>Spanner-style; accept latency]
    B -->|No| D{Must reads reflect<br/>THIS client's own writes?}
    D -->|Yes| RYW[Read-your-writes /<br/>session consistency<br/>sticky routing or read-from-leader]
    D -->|No| E{Tolerable staleness?}
    E -->|Seconds OK| EC[Eventual consistency<br/>async replicas, Dynamo-style]
    E -->|Causal order matters| CC[Causal consistency<br/>vector clocks / version vectors]
```

**Say it:** "Consistency is per-operation, not per-system. Checkout and balance reads need strong/linearizable; a like-count or feed can be eventually consistent. The common middle ground is session consistency — a user must see their own writes even if others see them a beat later."

---

## 3. Caching strategy (read path)

```mermaid
flowchart TD
    A[Read-heavy data?] --> B{Can tolerate<br/>brief staleness?}
    B -->|No, must be fresh| WT[Write-through +<br/>read cache<br/>cache updated on every write]
    B -->|Yes| C{Write volume?}
    C -->|Write-heavy,<br/>reads may lag| WB[Write-back / write-behind<br/>async flush; risk loss on crash]
    C -->|Read-heavy| CA[Cache-aside lazy<br/>app loads on miss, sets TTL]
    CA --> D{Thundering herd<br/>on hot key?}
    D -->|Yes| LOCK[Add request coalescing +<br/>stale-while-revalidate]
    D -->|No| DONE[Cache-aside + TTL is enough]
```

**Say it:** "Default cache-aside with a TTL — simplest, app controls it, cache failure just means a slower read not a wrong one. Add write-through only when staleness is unacceptable, and guard hot keys with request coalescing so a cache miss doesn't stampede the DB."

---

## 4. Push vs Pull (feeds, notifications, fan-out)

```mermaid
flowchart TD
    A[Delivering updates<br/>to many consumers?] --> B{Fan-out size<br/>per producer?}
    B -->|Small/uniform followers| PUSH[Fan-out on write push<br/>precompute each timeline<br/>fast reads, cheap fan-out]
    B -->|Celebrity / huge fan-out| PULL[Fan-out on read pull<br/>merge at query time<br/>avoids write amplification]
    B -->|Mixed| HYBRID[Hybrid<br/>push for normal users,<br/>pull-merge for celebrities]
    A --> C{Latency need?}
    C -->|Real-time| SSE[Server push:<br/>WebSocket / SSE]
    C -->|Near-real-time| POLL[Long-polling / periodic pull]
```

**Say it:** "Push (fan-out on write) makes reads O(1) but explodes for celebrities — one write becomes 100M inserts. So the real answer is hybrid: push for the median user, pull-and-merge for high-fan-out accounts, decided by a follower-count threshold."

---

## 5. Sync vs Async (request handling)

```mermaid
flowchart TD
    A[Does the caller need<br/>the result to proceed?] --> B{Yes?}
    B -->|Yes, immediately| SYNC[Synchronous request/response<br/>keep it fast, set timeouts]
    B -->|No, fire-and-forget| C{Ordering / durability<br/>required?}
    C -->|Yes| MQ[Async via durable queue<br/>Kafka / SQS; consumer processes]
    C -->|Best-effort| EVENT[Event bus / pub-sub<br/>decoupled subscribers]
    A --> D{Operation slow<br/>or spiky?}
    D -->|Yes| ASYNC2[Accept-202 + async worker<br/>return job id, poll/callback]
```

**Say it:** "If the user is blocked on the answer, sync. If it's slow (email, video encode, ML scoring) or spiky, return 202 Accepted with a job id and process on a durable queue — this decouples the burst from the workers and lets you retry."

---

## 6. Load balancing layer

```mermaid
flowchart TD
    A[Balancing what?] --> B{Need to route on<br/>URL/host/headers?}
    B -->|Yes| L7[Layer 7 app LB<br/>content-based routing, TLS term,<br/>sticky sessions]
    B -->|No, raw speed| L4[Layer 4 network LB<br/>connection-level, lower latency,<br/>very high throughput]
    A --> C{Stateful connections<br/>e.g. WebSocket?}
    C -->|Yes| STICKY[Consistent hashing /<br/>session affinity]
    A --> D{Global users?}
    D -->|Yes| GEO[GeoDNS / Anycast +<br/>regional LBs]
```

**Say it:** "L7 when I need to route by path/host or terminate TLS; L4 when I just need to spray connections at max throughput with minimal latency. Globally, GeoDNS or Anycast picks the nearest region, then a regional LB fans out."

---

## 7. Message delivery guarantee

```mermaid
flowchart TD
    A[What's the cost<br/>of a lost / duplicate<br/>message?] --> B{Loss acceptable?}
    B -->|Yes, e.g. metrics| ALO0[At-most-once<br/>fire and forget, no retries]
    B -->|No| C{Duplicates acceptable?}
    C -->|Yes if handled| ALO[At-least-once +<br/>idempotent consumer<br/>THE common default]
    C -->|No, must be exactly-once| EO[Effectively-once:<br/>idempotency keys OR<br/>transactional outbox +<br/>dedup on consumer]
```

**Say it:** "There's no free exactly-once over a network. The practical answer is at-least-once delivery plus an idempotent consumer — dedup on a message/idempotency key — which *behaves* exactly-once. True transactional exactly-once (Kafka transactions) exists but costs throughput and scope."

---

## 8. Which partitioning / sharding key

```mermaid
flowchart TD
    A[Picking a shard key] --> B{Queries mostly<br/>by one entity id?}
    B -->|Yes| HASH[Hash-partition on that id<br/>even spread, no hotspots]
    B -->|No, range scans<br/>by time/order| RANGE[Range-partition<br/>good for scans, watch<br/>for hot latest partition]
    A --> C{Risk of a hot key<br/>celebrity/popular item?}
    C -->|Yes| SALT[Add salt/composite key<br/>or split the hot entity]
    A --> D{Need related rows<br/>co-located for joins?}
    D -->|Yes| ENTITY[Entity-group / directory<br/>partition by tenant/user]
```

**Say it:** "Hash-partition on the id you filter by most — it spreads load evenly. Range-partitioning helps time-ordered scans but concentrates writes on the newest partition. The failure mode to preempt is a hot key: a celebrity or viral item, fixed by salting or splitting that entity."

---

## 9. Rate limiting algorithm

```mermaid
flowchart TD
    A[Rate limiting need] --> B{Need smooth,<br/>burst-tolerant limiting?}
    B -->|Yes, allow bursts| TB[Token bucket<br/>refill rate + burst capacity<br/>the usual default]
    B -->|No, strict constant rate| LB[Leaky bucket<br/>fixed drain rate, queues excess]
    A --> C{Simple per-window count?}
    C -->|Yes, accept edge bursts| FW[Fixed window counter<br/>simplest, boundary spike risk]
    C -->|Need accuracy at edges| SW[Sliding window log/counter<br/>accurate, more memory]
```

**Say it:** "Token bucket is the default — it enforces an average rate while permitting short bursts, which matches real traffic. Fixed-window is simplest but lets 2× the limit through at the window boundary; sliding window fixes that at the cost of more state."

---

## 10. Storage for a blob / file

```mermaid
flowchart TD
    A[Storing large binary?] --> B{Serve to end users<br/>at scale?}
    B -->|Yes| OBJ[Object store + CDN<br/>S3/GCS + CloudFront<br/>never blobs in the DB]
    B -->|No, internal| C{Access pattern?}
    C -->|Random read/write| BLOCK[Block storage / EBS<br/>attach to a compute node]
    C -->|Shared POSIX FS| FILE[File storage / EFS/NFS]
    A --> D{Metadata + search<br/>over files?}
    D -->|Yes| META[Blob in object store,<br/>metadata + pointer in DB]
```

**Say it:** "Blobs go in object storage fronted by a CDN; the database holds only metadata and the object key. Putting large binaries in the DB bloats it, wrecks backups, and blows the cache — the classic anti-pattern."

---

## Related

- Concept-recall flowcharts for every topic: [../FLOWCHARTS.md](../FLOWCHARTS.md)
- The numbers behind these tradeoffs: [numbers-to-know.md](numbers-to-know.md)
- Cloud-service equivalents for each leaf: [cloud-services-cheat-sheet.md](cloud-services-cheat-sheet.md)
