# #4 Distributed message queues

Nice — let’s design a distributed message queue (think Kafka / Pulsar / SQS-style system) end-to-end. I’ll cover scope, functional + non-functional requirements, APIs, the high-level architecture, core algorithms (partitioning, replication, delivery semantics), failure handling, scaling, operational concerns, trade-offs and a short evolution path. You can use this as an interview answer or an implementation blueprint.

<br>

## Problem statement

<br>

Build a fault-tolerant, highly-available distributed message queue that lets producers publish messages and consumers read them reliably. Key capabilities:

* High throughput for many producers/consumers
* Horizontal scalability (by partitioning)
* Durable storage and configurable retention
* Consumer groups & load-balanced delivery
* Exactly-once or at-least-once semantics where required
* Low latency for publish/subscribe patterns

***

## Functional requirements

* Produce messages to a topic (appended to end of topic/partition).
* Consume messages by offset (pull) and/or push (server push).
* Topic/partition model: topics can be partitioned for concurrency.
* Consumer groups: distribute partitions among group members (each message to one consumer in group).
* Durability: persisted messages survive node crashes until retention/compaction.
* Retention & compaction: time/size-based retention; optional log compaction by key.
* Ordering: ordering guaranteed within a partition.
* Acks & delivery semantics: support at-most-once, at-least-once, and exactly-once (with idempotence/transactions).
* Administrative APIs: create/delete topics, set retention, adjust partitions, view offsets.
* Monitoring & metrics: throughput, lag, broker health, storage usage.

***

## Non-functional requirements

* Throughput: millions of msgs/sec aggregate (configurable).
* Latency: low publish-to-deliver latency (tens–hundreds ms).
* Availability: 99.9%+ for reads/writes; graceful degradation when components fail.
* Scalability: linear scale by adding broker nodes and partitions.
* Durability: configurable replication factor (e.g., 3).
* Security: TLS, authN/authZ, per-topic ACLs.
* Operational simplicity: easy rebalancing, safe node replacement.

***

## External APIs (surface area)

<br>

REST/gRPC or binary protocol for performance.

<br>

Producer:

* Produce(topic, partition?, messages\[], acks=leader|quorum|all) -> ack\_offsets\[]
* CreateProducer(client\_id, config)

<br>

Consumer:

* Fetch(topic, partition, offset, max\_bytes) -> messages\[]
* CommitOffset(consumer\_group, topic, partition, offset) (or auto-commit)
* JoinGroup(consumer\_group, client\_id) -> assigned\_partitions\[] (for group coordination)
* Optional push: Subscribe(topic, consumer\_group, callback\_url)

<br>

Admin:

* CreateTopic(name, partitions, retention\_ms, replication\_factor)
* DescribeTopic(name), AlterTopic(...), GetLag(consumer\_group)

***

## High-level architecture

```
Producers  -->  LoadBalancer / Broker Router  -->  Broker Nodes (partition leaders/followers)
                                            |                |
                                            |         Durable Log Storage (local disk / SSD)
                                            |                |
                                            +--> Metadata / Controller (Cluster manager: leader election, configs)
                                            |
                                            +--> Replication (async/sync) --> other broker replicas
Consumers  <--------------------------------------------------/
```

Components:

1. Brokers: store partition logs, serve produce/fetch requests.
2. Controller / Cluster Manager: manages metadata (topic → partitions → leader mapping), partition creation, leader elections.
3. Metadata Store (coordinator): strongly consistent store (e.g., ZooKeeper, Raft) to store cluster state and coordinate leader election.
4. Storage Layer: append-only logs on local disk per partition, segmented files for efficient deletion/compaction.
5. Replicator: mechanism to copy segment data from leader to followers (pull or push).
6. Producer/Consumer Clients: client libraries implement batching, retries, idempotence, and group coordination.
7. Coordinator service for consumer groups: elects partition assignments (range/round-robin/sticky).
8. Monitoring and Admin UI.

***

## Core data model & storage

* Topic → N partitions. Each partition = ordered append-only log.
* Messages appended with monotonically increasing offset (per-partition).
* Log segmented into fixed-size files (e.g., 1 GB or time window). Old segments deleted per retention.
* Each message stored with metadata: timestamp, key (optional), headers, payload, sequence id (for idempotence), checksum.

***

## Partitioning & ordering

* Partition selection: keyed (hash(key) mod P) for ordering by key, or round-robin for load balancing.
* Ordering guarantee is per-partition only.

***

## Replication & consistency

* Each partition has replicas (replication factor R). One replica is leader; others are followers.
* Write path:
  1. Producer sends to leader.
  2. Leader appends to local log, returns ack depending on acks config:
     * acks=0: no ack (low latency, at-most-once).
     * acks=1 (leader): leader ack after durable append to leader.
     * acks=all or acks=quorum: ack after followers replicate (sync) → stronger durability.
* Use leader-based replication with ISR (in-sync replicas) tracked by controller.
* For durability, require acks=all with quorum to tolerate leader failover without data loss.

***

## Consumer groups & offset management

* Consumers in a group get disjoint partitions to process.
* Offsets committed by consumer (synchronously or asynchronously). Two models:
  * Consumer-managed offset: stored in a special internal topic (durable, replicated).
  * Broker-managed offset: broker stores offsets per group.
* On rebalance, the controller coordinates partition reassignment.

***

## Delivery semantics & exactly-once

* At-most-once: producer not retried, consumer auto-commits before processing.
* At-least-once: consumer processes and then commits; duplicates possible.
* Exactly-once (EOS): Achieved with idempotent producers + transactions:
  * Producers assign a unique producer id (PID) and monotonic sequence numbers per partition. Broker rejects out-of-order duplicates.
  * Transactional writes across partitions/topics: two-phase commit-like protocol; transactional markers written to log allow consumers to read only committed transactions (Kafka’s transactional API).
  * This is more complex and has throughput/cost trade-offs.

***

## Rebalance & failure handling

* Leader failure: controller detects leader death, picks new leader from ISR (must be in-sync).
* Replica catch-up: followers fetch segments; once catch-up, rejoin ISR.
* Consumer rebalance: controller/coordinator triggers reassignments; use “sticky” assignment to reduce movement.
* Backpressure: Brokers limit producer throughput; clients implement retries with exponential backoff; producers should batch.

***

## Compaction & retention

* Time/size-based retention: delete old segments after retention window or size threshold.
* Log compaction: retain only latest message per key (useful for changelogs/state stores).
* Implement compaction as background maintenance per partition segment.

***

## Scaling & placement

* Scale by partitions: add more partitions per topic and spread across brokers.
* Storage scaling: add brokers and rebalance partitions.
* Hot partitions: if skewed keys cause hotspots, have partitioning strategies (salting, more partitions for high-volume topics).
* Multi-tenancy: quotas per tenant/topic on bytes/sec and storage.

***

## Metrics & observability

* Broker: CPU, disk I/O, free disk, replication lag, ISR size, network throughput.
* Topic: throughput (msg/sec, bytes/sec), consumer lag per partition, segment count, retention pressure.
* Alerts: replication lag > threshold, high unclean leader election frequency, disk filling.

***

## Security

* TLS encryption for client-broker and inter-broker comms.
* Authentication: mTLS, OAuth bearer tokens.
* Authorization: ACLs per topic/consumer group (produce/consume/create).
* Auditing: admin actions, topic changes.

***

## Back-of-the-envelope capacities (example)

<br>

Assume: 1M msgs/sec aggregate, avg msg 1 KB → 1 GB/sec ≈ 86.4 TB/day raw.

* With replication factor 3 → \~259 TB/day. (So design for compaction, retention policy, compression.)
* If each broker has 4 TB SSD, need \~65 brokers to store one day of data; real systems compress and use larger drives.

***

## Trade-offs & choices

* Leader-based replication (simpler) vs consensus-per-write (Raft per partition): leader-based gives good throughput but relies on separate controller; Raft gives stronger consistency per-partition at cost of ops complexity.
* Sync replication (acks=all) provides durability at cost of latency.
* Exactly-once semantics adds complexity & throughput overhead.
* Many partitions increases parallelism but increases metadata churn and controller load.

***

## Evolution path

1. MVP: single-controller + brokers, leader-based replication, partitioned topics, simple offset commits.
2. Add consumer group coordinator, admin APIs, monitoring.
3. Add idempotent producers & transactions for EOS.
4. Introduce tiered storage (hot SSD + cold object store) to reduce cost.
5. Multi-cluster geo-replication for global reads/writes.

***

## Example simple RPC flow (produce)

1. Producer batches M messages → choose partition P (by key/hash).
2. Send Produce(topic,P,batch, acks=all) to leader L.
3. L append to local segment, fsync (or async depending on durability), assign offsets.
4. L replicate to followers; when quorum acks, L responds to producer with offsets.

<br>

## Final notes (interview tips)

* Start by clarifying required delivery semantics and scale (throughput & retention) — these drive choices (replication factor, storage layout, whether to support EOS).
* Emphasize partitioning (scaling) and replicas (durability).
* Discuss how consumer groups and offsets work; show you understand rebalances and failure scenarios.
* Be ready to defend trade-offs: latency vs durability, complexity vs features.

<br>

Want a time-boxed 1-hour script for presenting this in an interview (what to say in each 5–10 minute block) or pseudocode for producer/replicator logic?
