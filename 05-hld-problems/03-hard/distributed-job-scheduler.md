# Distributed Job Scheduler

> Design a fault-tolerant cron-at-scale system that runs 10M scheduled jobs exactly-once without a single point of failure.

---

## Problem Mindmap

```
Distributed Job Scheduler
├── Why It Exists
│   ├── 10M cron jobs cannot run on one machine (SPOF + capacity)
│   ├── Exactly-once guarantee: charge card once, send email once
│   └── Missed schedule = SLA breach; double-run = data corruption
├── Scheduler Leader
│   ├── Leader election → ZooKeeper / etcd ephemeral node
│   ├── Only leader polls the job store every tick
│   ├── Follower watches leader; takes over on leader death
│   └── Time-based sharding → leader N owns minute-range [N*10, N*10+10)
├── Job Store
│   ├── Relational DB → jobs table with next_run_at index
│   ├── next_run_at index → range scan finds due jobs in O(log N)
│   ├── Optimistic locking → CAS on job row prevents double-dispatch
│   └── Soft-delete status: PENDING → RUNNING → DONE/FAILED
├── Job Queue
│   ├── Kafka / SQS / RabbitMQ → scheduler enqueues, workers consume
│   ├── At-least-once delivery → idempotency key required
│   ├── Priority queues → critical jobs jump the queue
│   └── Dead letter queue → failed jobs after max retries
├── Worker Pool
│   ├── Stateless workers → horizontally scalable
│   ├── Heartbeat to coordinator → detect stalled jobs
│   ├── Timeout = next_run_at + max_duration → re-enqueue if exceeded
│   └── Result store → job_id → status/output/error
├── Exactly-Once Strategies
│   ├── Idempotent job body → same input, same output, no side-effects
│   ├── Distributed lock (Redis SETNX) → only one worker runs at a time
│   ├── DB atomic CAS → UPDATE jobs SET status='RUNNING' WHERE status='PENDING'
│   └── Outbox pattern → job completion event written in same DB tx
├── DAG / Dependency Jobs
│   ├── Airflow: directed acyclic graph of tasks per workflow
│   ├── Topological sort → schedule tasks in dependency order
│   └── Fan-out/fan-in → parallel tasks, wait for all to complete
└── Real Implementations
    ├── Apache Airflow → DAGs, Python operators, web UI
    ├── LinkedIn Azkaban → Hadoop workflow scheduler
    ├── Uber Cadence/Temporal → workflow orchestration, durable execution
    └── Quartz Scheduler → Java library, clustered mode with JDBC store
```

---

## 1. Why Distributed Job Scheduler Exists

**Question**: You have 10M jobs configured to run at 2 AM daily — billing charges, report generation, ML model retraining. Your single cron server dies at 1:59 AM. What happens?

**Physical constraint**: A single machine can dispatch at most ~100K jobs/min (disk I/O + DB queries). 10M jobs / 60 minutes = 167K jobs/min. Beyond one machine's capacity. Also: single machine = single point of failure; one scheduler = unacceptable for "charge user exactly once" semantics.

**Minimal solution**: Single cron + DB. Works until: machine dies (missed jobs), DB becomes bottleneck (can't scan 10M rows in 60s), two machines running cron cause double-dispatch.

**Production generalization**: Leader-elected scheduler cluster that shards the job namespace, enqueues due jobs to a distributed queue, stateless workers with heartbeat-based liveness detection, and idempotency keys throughout the pipeline to handle at-least-once delivery safely.

---

## 2. Core Concepts

### 2.1 Leader Election

Only one scheduler should scan and dispatch at any time — otherwise two leaders both enqueue the same job.

**ZooKeeper ephemeral node approach**:
```
/scheduler/leader → [node-1 holds this znode; ephemeral]
node-2, node-3 → watch /scheduler/leader
node-1 dies → ZK session expires → znode deleted → node-2 wins race to create it
```

**etcd lease approach** (preferred in k8s):
```
PUT /scheduler/leader value=node-1 LEASE=30s
node-1 refreshes lease every 10s
lease expires → other nodes attempt atomic PUT (compare-and-swap)
```

### 2.2 Job Table Schema

```sql
CREATE TABLE jobs (
    id           BIGINT PRIMARY KEY,
    name         VARCHAR(255) NOT NULL,
    cron_expr    VARCHAR(100) NOT NULL,       -- "0 2 * * *"
    next_run_at  TIMESTAMP NOT NULL,          -- indexed
    status       ENUM('PENDING','RUNNING','DONE','FAILED') DEFAULT 'PENDING',
    worker_id    VARCHAR(100),                -- which worker owns it
    heartbeat_at TIMESTAMP,                  -- last worker heartbeat
    retry_count  INT DEFAULT 0,
    max_retries  INT DEFAULT 3,
    payload      JSONB,                       -- job parameters
    idempotency_key VARCHAR(255) UNIQUE,      -- prevents double-run
    created_at   TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_next_run_status ON jobs (next_run_at, status)
    WHERE status = 'PENDING';
```

### 2.3 Exactly-Once vs At-Least-Once

| Guarantee | Mechanism | Risk |
|-----------|-----------|------|
| At-least-once | Enqueue; re-enqueue if no ack | Double execution |
| At-most-once | Enqueue; never retry | Missed execution on failure |
| Exactly-once | At-least-once + idempotent job | Complexity cost |

**Idempotency key**: `job_id + scheduled_time` → hash → UUID stored in result DB. Worker checks before executing: if key already exists → skip, return cached result.

### 2.4 Dispatch Algorithm

```
Every 30 seconds, leader runs:
1. SELECT id FROM jobs
   WHERE next_run_at <= NOW() + 30s
     AND status = 'PENDING'
   ORDER BY next_run_at
   LIMIT 10000
   FOR UPDATE SKIP LOCKED   ← PostgreSQL: other schedulers skip locked rows

2. For each job:
   UPDATE jobs SET status='RUNNING', worker_id=NULL, heartbeat_at=NOW()
   WHERE id=? AND status='PENDING'   ← optimistic lock

3. Enqueue job_id to Kafka topic `job-dispatch`

4. Compute next_run_at from cron_expr, update job row
```

`FOR UPDATE SKIP LOCKED` is the key: if multiple scheduler nodes run (e.g., during leader re-election overlap), they each grab different rows — no double-dispatch.

### 2.5 Time-Based Sharding

For 10M jobs, even `FOR UPDATE SKIP LOCKED` on one DB is slow. Shard by minute:

```
Scheduler shard 0: owns jobs with next_run_at minute % 10 == 0
Scheduler shard 1: owns jobs with next_run_at minute % 10 == 1
...
Scheduler shard 9: owns jobs with next_run_at minute % 10 == 9
```

Each shard has its own leader. 10 shards × 100K dispatch capacity = 1M jobs/min.

### 2.6 Worker Heartbeat & Stall Detection

```
Worker polls queue → picks up job → starts heartbeat goroutine:
  every 10s: UPDATE jobs SET heartbeat_at=NOW() WHERE id=? AND worker_id=?

Reaper thread (runs on scheduler leader, every 60s):
  SELECT id FROM jobs
  WHERE status='RUNNING'
    AND heartbeat_at < NOW() - INTERVAL '2 minutes'
  → Mark as PENDING (or FAILED if retry_count >= max_retries)
  → Re-enqueue to job queue
```

### 2.7 Job DAG (Airflow-style)

```
         [extract_data]
               │
      ┌────────┴────────┐
      ▼                 ▼
[transform_A]    [transform_B]     ← parallel tasks
      │                 │
      └────────┬────────┘
               ▼
          [load_data]
               │
               ▼
        [send_report]
```

Each node in the DAG is a job. Airflow's scheduler does topological sort, marks each task PENDING only when all upstream tasks are DONE.

---

## 3. Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                      Scheduler Cluster                           │
│                                                                  │
│  ┌──────────┐   leader election   ┌──────────┐  ┌──────────┐   │
│  │Scheduler │◄────(etcd/ZK)──────►│Scheduler │  │Scheduler │   │
│  │  Node-1  │  (only leader       │  Node-2  │  │  Node-3  │   │
│  │ (LEADER) │   scans job store)  │ (standby)│  │ (standby)│   │
│  └────┬─────┘                     └──────────┘  └──────────┘   │
└───────┼──────────────────────────────────────────────────────────┘
        │ range scan every 30s
        ▼
┌───────────────────┐
│     Job Store     │        ┌─────────────────────────────────────┐
│  PostgreSQL/MySQL │        │          Job Queue (Kafka)           │
│  ─────────────── │        │  topic: job-dispatch                 │
│  jobs table       │──────► │  partitioned by job_type/priority   │
│  next_run_at idx  │        └────────────────┬────────────────────┘
└───────────────────┘                         │ consume
                                   ┌──────────┼──────────┐
                                   ▼          ▼          ▼
                              ┌────────┐ ┌────────┐ ┌────────┐
                              │Worker-1│ │Worker-2│ │Worker-N│
                              └───┬────┘ └───┬────┘ └───┬────┘
                                  │          │          │
                                  └──────────┼──────────┘
                                             ▼
                                    ┌─────────────────┐
                                    │  Result Store   │
                                    │  (Redis/DB)     │
                                    │  job_id→status  │
                                    └─────────────────┘
```

---

## 4. Real-World Usage

| System | Approach | Use Case |
|--------|----------|----------|
| Apache Airflow | DAG-based, Python operators, DB scheduler | Data pipelines, ETL |
| LinkedIn Azkaban | Hadoop workflow scheduler, ZIP-packaged jobs | Hadoop at LinkedIn scale |
| Uber Cadence/Temporal | Durable execution, workflow history replay | Long-running workflows |
| Quartz (Java) | JDBC cluster mode, `JobStore` interface | Java enterprise apps |
| AWS Step Functions | State machine, serverless | AWS-native workflows |
| k8s CronJob | Controller watches next schedule, creates Pod | Container workloads |

---

## 5. Trade-offs

| Decision | Option A | Option B | Consideration |
|----------|----------|----------|---------------|
| Job store | Relational DB (strong guarantees) | Redis (fast, weaker durability) | Use DB; Redis TTL unreliable for job state |
| Queue | Kafka (ordered, durable) | SQS (managed, at-least-once) | Kafka for replay/audit; SQS for simplicity |
| Exactly-once | Idempotency key | Distributed lock | Both; lock prevents concurrent, key prevents replay |
| DAG complexity | Simple cron (no dependencies) | Full DAG engine | DAG only when dependencies required |
| Leader election | ZooKeeper | etcd | etcd preferred in k8s; ZK in Hadoop ecosystem |

---

## 6. Failure Scenarios

### 6.1 Scheduler Leader Dies Mid-Dispatch
**Symptom**: 500 jobs in status='RUNNING' with no worker assigned; leader died after DB update but before Kafka enqueue.
**Fix**: Reaper thread detects `heartbeat_at < NOW() - 2min` → re-marks as PENDING → re-enqueues. Worker idempotency key prevents double-execution if original job also completed.

### 6.2 Worker Dies Mid-Job
**Symptom**: Job in RUNNING state; worker OOM-killed at minute 3 of a 10-minute job.
**Fix**: Heartbeat stops; reaper detects after 2min timeout; re-enqueues. Job must be designed idempotent (e.g., billing uses idempotency key to prevent double-charge).

### 6.3 Clock Skew Causes Missed Jobs
**Symptom**: Job scheduled for 02:00:00 but scheduler's clock reads 02:00:01 — job scanned at T-30s sees `next_run_at` in the future.
**Fix**: Use `next_run_at <= NOW() + 5s` (lookahead buffer). NTP synchronization required; use `CLOCK_REALTIME` not `CLOCK_MONOTONIC`.

### 6.4 Job Queue Lag Spike
**Symptom**: 1M jobs pile up at midnight; Kafka consumer lag grows; jobs run hours late.
**Fix**: Pre-warm worker pool before midnight. Priority queue: urgent jobs in separate high-priority topic. Shed load: skip non-critical jobs if lag > threshold.

---

## 7. Performance Numbers

| Metric | Number | Notes |
|--------|--------|-------|
| Max dispatch rate (1 scheduler) | ~100K jobs/min | Limited by DB scan + Kafka produce |
| Max dispatch rate (10 shards) | ~1M jobs/min | With time-based sharding |
| Job pickup latency | <5s | Scheduler scan interval 30s → avg 15s |
| Worker throughput | 10–1000 jobs/sec/worker | Depends on job duration |
| DB index scan (10M rows) | ~50ms | `next_run_at` B-tree index, PENDING filter |
| Kafka produce latency | <5ms | acks=1, local broker |
| Heartbeat interval | 10s | Reaper timeout: 120s |

---

## 8. Java Implementation

### 8.1 Leader Election with etcd

```java
public class SchedulerLeaderElection {
    private final EtcdClient etcd;
    private final String nodeId = UUID.randomUUID().toString();
    private volatile boolean isLeader = false;

    public void start() {
        ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor();
        ses.scheduleAtFixedRate(this::tryAcquireLeadership, 0, 10, TimeUnit.SECONDS);
    }

    private void tryAcquireLeadership() {
        // CAS: only succeed if key doesn't exist (or we already hold it)
        boolean acquired = etcd.putIfAbsent(
            "/scheduler/leader",
            nodeId,
            30 /* TTL seconds */
        );
        if (acquired && !isLeader) {
            isLeader = true;
            log.info("Node {} became leader", nodeId);
            startDispatchLoop();
        } else if (!acquired) {
            isLeader = false; // another node holds leadership
        }
    }
}
```

### 8.2 Job Dispatch Loop

```java
@Scheduled(fixedRate = 30_000)
public void dispatchDueJobs() {
    if (!leaderElection.isLeader()) return;

    Instant horizon = Instant.now().plusSeconds(30);
    List<Job> dueJobs = jobRepository.findDueJobs(horizon, 10_000);

    for (Job job : dueJobs) {
        boolean claimed = jobRepository.claimJob(job.getId(), myNodeId);
        if (!claimed) continue; // another scheduler got it (SKIP LOCKED)

        jobQueue.enqueue(JobMessage.builder()
            .jobId(job.getId())
            .idempotencyKey(job.getId() + ":" + job.getNextRunAt().toEpochMilli())
            .payload(job.getPayload())
            .build());

        // Compute and update next_run_at based on cron expression
        Instant nextRun = CronExpression.parse(job.getCronExpr()).next(Instant.now());
        jobRepository.reschedule(job.getId(), nextRun);
    }
}
```

### 8.3 Worker with Idempotency Check

```java
@KafkaListener(topics = "job-dispatch")
public void handleJob(JobMessage msg) {
    String idempotencyKey = msg.getIdempotencyKey();

    // Check if already executed (at-least-once → exactly-once)
    if (resultStore.exists(idempotencyKey)) {
        log.info("Job {} already executed, skipping", msg.getJobId());
        return;
    }

    // Heartbeat thread — updates DB every 10s
    ScheduledFuture<?> heartbeat = heartbeatExecutor.scheduleAtFixedRate(
        () -> jobRepository.updateHeartbeat(msg.getJobId()),
        0, 10, TimeUnit.SECONDS
    );

    try {
        Object result = jobExecutor.execute(msg.getJobId(), msg.getPayload());
        resultStore.save(idempotencyKey, result);
        jobRepository.markDone(msg.getJobId());
    } catch (Exception e) {
        jobRepository.markFailed(msg.getJobId(), e.getMessage());
    } finally {
        heartbeat.cancel(false);
    }
}
```

### 8.4 Stalled Job Reaper

```java
@Scheduled(fixedRate = 60_000)
public void reapStalledJobs() {
    if (!leaderElection.isLeader()) return;

    Instant stalledBefore = Instant.now().minusSeconds(120);
    List<Job> stalled = jobRepository.findStalled(stalledBefore);

    for (Job job : stalled) {
        if (job.getRetryCount() >= job.getMaxRetries()) {
            jobRepository.markFailed(job.getId(), "Max retries exceeded");
            alertService.notify("Job " + job.getId() + " permanently failed");
        } else {
            jobRepository.requeueJob(job.getId()); // status → PENDING, retry_count++
        }
    }
}
```

---

## 9. Quick Revision

- **Leader election**: etcd CAS / ZooKeeper ephemeral node; only leader dispatches
- **`FOR UPDATE SKIP LOCKED`**: PostgreSQL feature that lets N schedulers share work without contention
- **Idempotency key**: `job_id:scheduled_timestamp` → stored in result DB → skip if exists
- **Heartbeat**: worker updates `heartbeat_at` every 10s; reaper re-enqueues after 2min silence
- **Time sharding**: shard scheduler by `minute % N` → N × capacity without coordination
- **DAG**: topological sort; downstream task PENDING only when all upstreams DONE
- **At-least-once + idempotent = exactly-once**: never claim exactly-once without idempotent job body

---

## 10. See Also

- `09-patterns/outbox-pattern.md` — reliable job enqueue in same DB transaction
- `09-patterns/saga-pattern.md` — compensating transactions for multi-step jobs
- `02-building-blocks/message-brokers.md` — Kafka queue details
- `02-building-blocks/distributed-locks.md` — leader election primitives

---

## 11. Interview Questions Asked

1. **Google**: "Design a cron system for 10M jobs. How do you prevent two workers from running the same job?"
2. **Meta**: "Your scheduler leader dies during dispatch. How do you recover without double-executing jobs?"
3. **Stripe**: "Billing jobs must run exactly-once. Walk me through your idempotency strategy."
4. **Amazon**: "How do you scale job dispatch beyond what one machine can handle?"
5. **Uber**: "How does Cadence/Temporal guarantee that a workflow runs to completion even if the service restarts?"
6. **Airbnb**: "Your job queue has 1M pending jobs and workers can't keep up. What do you do?"
7. **LinkedIn**: "Describe how Azkaban handles job dependency DAGs."
8. **Netflix**: "A job takes 2 hours. How does your system detect that the worker is still alive vs stuck?"
