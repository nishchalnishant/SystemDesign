> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design a distributed job scheduler — executing millions of one-off and recurring jobs reliably with at-least-once guarantees, exactly-once prevention, and worker failure handling.
>
> **Key design decisions:**
> - Job store: PostgreSQL as source of truth; job row: {job_id, cron_expression, next_run_time, status, worker_id}; index on next_run_time for efficient polling
> - Scheduler: leader node (via distributed lock) scans for due jobs (next_run_time ≤ now); claims job via CAS update (status: PENDING → CLAIMED); publishes to Kafka
> - Worker pool: stateless workers pull from Kafka; execute job; update status to COMPLETED or FAILED in DB; heartbeat for long-running jobs
> - At-least-once: if worker dies mid-job, timeout detection (last_heartbeat + timeout < now) → re-queue; idempotent job design required
> - Exactly-once prevention: idempotency key per (job_id, scheduled_time) → deduplicate in DB with unique constraint; compensating rollback if re-run
> - Distributed leader election: ZooKeeper / etcd ephemeral node; only leader schedules; follower promotes if leader crashes within TTL
> - Monitoring: job execution latency, miss rate (jobs running past scheduled time), DLQ for repeatedly failed jobs
>
> **Key takeaway:** CAS-based job claiming (optimistic locking on status field) prevents two workers running the same job — combine with idempotent job logic as defense in depth.

---
module: 05-hld-problems
topic: Hard
status: unread
tags: [05-hld-problems, system-design, hard, job-scheduler, cron, distributed, exactly-once]
---
# Design a Distributed Job Scheduler

> **Difficulty**: Hard | **Asked at**: Uber, Airbnb, LinkedIn, Amazon

---

## Problem Statement

Design a distributed job scheduler that executes millions of scheduled jobs at their configured times. Jobs can be one-off (run once at a specific time) or recurring (cron-style). The system must guarantee at-least-once execution, avoid duplicate execution, and handle worker failures gracefully.

---

## Functional Requirements

1. **Schedule jobs**: Create jobs with a cron expression or one-time timestamp
2. **Execute jobs**: Dispatch jobs to workers at the scheduled time (within ±1 second)
3. **Job types**: HTTP callback (webhook), or message queue publish
4. **Job management**: Pause, resume, cancel, view history, retry on failure
5. **At-least-once execution**: Job must execute even if the scheduler crashes
6. **Deduplication**: Job must not execute twice for the same scheduled slot

---

## Non-Functional Requirements

- **Scale**: 10M active jobs; 100,000 job executions/sec at peak
- **Timing accuracy**: Jobs fire within 1 second of their scheduled time
- **Availability**: 99.99% — scheduler downtime = missed jobs
- **Exactly-once**: Strong preference; at-least-once with idempotent workers acceptable
- **Fault tolerance**: Worker crash must not prevent job execution (reassign to another worker)

---

## Core Entities

| Entity | Key Fields |
|--------|-----------|
| `Job` | job_id, name, schedule (cron / timestamp), job_type, payload, status, retry_config |
| `JobExecution` | execution_id, job_id, scheduled_at, started_at, completed_at, status, worker_id, result |
| `Worker` | worker_id, status (healthy/offline), last_heartbeat, current_job_count |

---

## API Design

```http
POST /api/v1/jobs
Body: {
  "name": "daily-report",
  "schedule": "0 9 * * 1-5",  # cron: 9 AM on weekdays
  "job_type": "http_callback",
  "payload": { "url": "https://reports.internal/generate", "method": "POST" },
  "retry": { "max_attempts": 3, "backoff": "exponential" },
  "timeout_seconds": 300
}
Response 201: { "job_id": "j123", "next_run": "2026-06-30T09:00:00Z" }

DELETE /api/v1/jobs/{job_id}
POST /api/v1/jobs/{job_id}/pause
POST /api/v1/jobs/{job_id}/resume

GET /api/v1/jobs/{job_id}/executions?limit=10
Response 200: { "executions": [{ "execution_id": "...", "status": "success", "started_at": "..." }] }
```

---

## High-Level Design

```
API Service → Job Store (PostgreSQL) → parse cron + compute next_run_at

Scheduler (central)
  ├── Poll loop: SELECT jobs WHERE next_run_at <= now() AND status = 'active' LIMIT 1000
  │   → for each job: claim it (optimistic lock), publish to Kafka dispatch-queue
  │   → update next_run_at to next cron window
  │
  └── Heartbeat monitor: detect stale job executions (no heartbeat > 30s)
       → requeue timed-out executions

Kafka: dispatch-queue (partitioned by job_id)

Worker Pool (horizontally scaled)
  ├── Consume job from Kafka
  ├── Execute job (HTTP call / message publish)
  ├── Send heartbeat every 5s during execution
  └── Write result to PostgreSQL + ack Kafka offset

PostgreSQL: jobs, job_executions
Redis: distributed lock (claim job before dispatch)
```

---

## Deep Dive 1: Preventing Duplicate Execution

**Problem**: The scheduler crashes after dispatching a job to Kafka but before updating the job's `status` to `executing`. On restart, the scheduler sees the job as un-executed and dispatches it again → duplicate execution.

**Two-phase claim with idempotency**:

**Phase 1: Claim (atomic)**:
```sql
UPDATE jobs
SET status = 'executing',
    execution_id = gen_uuid(),
    locked_until = now() + interval '5 minutes'
WHERE job_id = :job_id
  AND status = 'active'
  AND next_run_at <= now()
RETURNING execution_id;
-- 0 rows: already claimed by another scheduler instance
```
Optimistic locking via `status` field. Only one scheduler wins the race.

**Phase 2: Dispatch (idempotent)**:
Publish to Kafka with `execution_id` as the message key. Workers check Redis `SETNX execution:{execution_id} "running" EX 3600` before executing — if key exists, the execution is already in progress → skip.

**Worker-side idempotency**: Each job's HTTP callback target should be designed to be idempotent (safe to call twice). Include `execution_id` in the webhook payload. The target service can check if it has already processed this execution_id.

**Missed job detection**: A sweeper job runs every minute. It finds executions where `status = 'executing' AND locked_until < now()` → timed out. These are reset to `status = 'active'` and re-queued. The sweeper must check that the worker truly died (no heartbeat) before re-queuing.

> 🎯 **Staff signal:** The load-bearing statement is that **exactly-once execution is impossible, so the design is at-least-once dispatch + idempotent execution = effectively-once** — and every layer here exists to enforce that. The atomic claim (`UPDATE ... WHERE status='active' RETURNING`) wins the *dispatch* race via optimistic locking, but a crash between dispatch and status-update still double-dispatches, so a stable `execution_id` carried into Kafka and gated by worker-side `SETNX` is what makes the duplicate a no-op. The subtle E6 detail is the **lease** (`locked_until`): a claimed job isn't done, it's *leased for 5 minutes* — the sweeper can only reclaim it after the lease expires AND heartbeat is gone, because reclaiming a still-running-but-slow worker is exactly how you turn "at-least-once" into "run twice concurrently." Naming the lease-plus-heartbeat as the guard against premature reclaim, and pushing final idempotency to the callback target (it's the only place that can truly dedupe side effects), is the staff line. E5 says "use a lock"; E6 says the lock is a lease and the guarantee is effectively-once.

---

## Deep Dive 2: Scaling the Scheduler to 10M Jobs

**Problem**: A single scheduler polling PostgreSQL for due jobs every second with 10M active jobs will struggle. `SELECT ... WHERE next_run_at <= now()` on 10M rows requires a full index scan unless the index is efficient.

**Partitioned job table by `next_run_at`**:
```sql
CREATE TABLE jobs (
  job_id text,
  next_run_at timestamp,
  ...
  PRIMARY KEY (job_id, next_run_at)
) PARTITION BY RANGE (next_run_at);

CREATE TABLE jobs_2026_06 PARTITION OF jobs
  FOR VALUES FROM ('2026-06-01') TO ('2026-07-01');
```
Only the current-month partition is scanned for due jobs. Index on `(next_run_at, status)` within the partition.

**Time-bucketed dispatch**: Instead of polling every second, the scheduler reads jobs due in the next 60 seconds (60× fewer polls). Publishes them to a Kafka topic with partition = `scheduled_seconds % 60`. Separate consumer groups read from each partition 1 second before jobs are due.

**Distributed scheduler** (HA): Run 3 scheduler instances. Each instance uses a distributed lock (`SELECT pg_try_advisory_lock(1)` or Redis Redlock) to become the leader. Only the leader polls and dispatches. Follower instances take over within 5 seconds if the leader fails.

> 🎯 **Staff signal:** The scaling insight is converting a *per-second full-table scan* into a *bounded look-ahead window*: instead of polling "what's due now" against 10M rows every second, read "what's due in the next 60s" once a minute and hand those jobs to a time-bucketed queue (Kafka partition = `scheduled_seconds % 60`) that fires them at the right second. That's a 60× reduction in query load and moves the precise-timing burden off the database and onto the queue. Partitioning the table by `next_run_at` so only the current window's partition is ever scanned is the second half — it turns an index scan over all-time jobs into a scan over one small partition (and makes retiring old jobs a partition drop, not a mass delete). The E6 note is the honesty that *poll-based scheduling trades timing precision for throughput*: you accept up-to-1s dispatch jitter to avoid a coordination-heavy exact-timer-per-job design, and you keep dispatch single-leader (advisory lock) so 3 HA instances don't each fire the same window. E5 optimizes the query; E6 changes the *access pattern* from scan-now to windowed-lookahead and names the precision tradeoff.

---

## Deep Dive 3: Retry and Failure Handling

**Problem**: A job's HTTP callback fails with a 500 error. The system must retry 3 times with exponential backoff, but not retry for too long.

**Retry state machine**:
```
PENDING → EXECUTING → SUCCESS
                    ↘ FAILED (attempt < max_attempts) → PENDING (with delay)
                    ↘ DEAD (attempt = max_attempts)
```

**Retry scheduling**: After a failed execution, compute next retry time:
```python
delay = base_delay * (2 ** (attempt_number - 1)) + jitter
next_retry_at = now() + timedelta(seconds=min(delay, max_delay))
```
Insert a new row in `job_executions` with `next_retry_at` as the `next_run_at`. The scheduler picks this up like any other due job.

**Jitter**: Add random jitter (`random.uniform(0, delay)`) to prevent all failed jobs retrying at the same time (thundering herd on external services).

**Dead letter queue**: After `max_attempts` exhausted, write the job execution to a DLQ (Kafka topic). Ops team can inspect and manually replay if needed.

**Alerting**: Prometheus counter `job_execution_failed{job_name, attempt_number}`. Alert when failure rate for a job exceeds 3 failures in 10 minutes.

> 🎯 **Staff signal:** The elegant move is that retries are *not a separate mechanism* — a failed job simply re-inserts itself as a new due job with `next_run_at = now() + backoff`, so the scheduler's existing "pick up due jobs" loop handles retries with zero new machinery. That reuse is the design tell. The two E6 details: (1) **jitter is mandatory, not optional** — pure exponential backoff synchronizes every job that failed against a downed dependency to retry at the *same* instant, so the recovery attempt re-DDoSes the service just as it comes back; randomized jitter spreads the retry thundering herd, and it's the failure most candidates forget to name. (2) A bounded retry policy needs a **terminal state (DLQ)** — infinite retries silently pile up load and hide a persistently broken target, so after `max_attempts` the execution goes to a dead-letter topic for human inspection rather than looping forever. E5 says "retry with exponential backoff"; E6 adds jitter to protect the recovering dependency and a DLQ so failure is observable and bounded.

---

## Interviewer Questions by Level

**Junior**:
- What is a cron expression? Give an example.
- What is at-least-once vs exactly-once job execution?
- What happens if a worker crashes while executing a job?

**Mid-level**:
- How do you prevent two scheduler instances from dispatching the same job twice?
- How do you implement retry with exponential backoff?
- How do you scale the scheduler from 1M to 10M active jobs?

**Senior**:
- Design the exactly-once execution guarantee end-to-end. What's the weakest link?
- How do you handle a 100× spike in jobs due at the same second (e.g., all monthly billing jobs due at midnight)?
- How do you implement distributed locking for the leader election without a single point of failure?
- Design the job history and execution audit log for compliance — what must be stored, how long, and how do you query it efficiently?

---

## Back-of-Envelope Estimation

**Scale inputs (given in NFRs):**
- 100M jobs/day; 1M concurrent jobs; < 1 second scheduling latency

**Job ingestion rate:**
- 100M jobs/day ÷ 86,400 sec = **~1,157 job submissions/sec** average
- Peak (morning batch launches): 10× = **~11,570 jobs/sec**
- Each job record: `{job_id, type, payload, priority, schedule, status, created_at}` ≈ 500 bytes
- Peak write throughput: 11,570 × 500 bytes = **~5.8 MB/sec** — trivial for a DB

**Job queue depth:**
- 1M concurrent jobs × average 5 min execution = **5M job-minutes** in flight at any given time
- Jobs in "pending" state (scheduled but not yet picked up): assume 60-second window → 1,157 × 60 = **~70K pending jobs** in the queue at any moment
- Redis sorted set (`ZADD jobs:pending {run_at_epoch} {job_id}`): 70K entries × 100 bytes/entry = **~7 MB** — trivial

**Scheduler poll and dispatch:**
- `< 1 second` scheduling latency: scheduler must poll the pending queue at least once per second
- Polling query: `ZRANGEBYSCORE jobs:pending -inf {now} LIMIT 0 1000` — pop up to 1,000 due jobs
- At 1,157 submissions/sec, 1,000 jobs/poll at 1 Hz keeps pace; at burst: increase to 10 Hz polling = 10,000 jobs/poll cycle
- Polling at 10 Hz with 10 scheduler instances = 100 poll cycles/sec → Redis handles 100 ZRANGEBYSCORE ops/sec comfortably

**Worker fleet sizing:**
- 1M concurrent jobs; assume average job duration: 5 minutes
- Workers needed = 1M jobs ÷ (1 worker handles 1 job at a time) ... but jobs are parallel across workers
- If each job uses one worker thread for 5 min: need 1M worker slots simultaneously
- At 8 threads per worker machine: **125,000 worker machines** — clearly impractical
- Reframe: 1M "concurrent" includes jobs that are just queued, not actively running. Actually executing at any moment: assume 10% = **100K actively executing**, needing **12,500 8-core machines**
- Typical reality: heterogeneous — short jobs (1s) dominate numerically; long jobs (1h) dominate machine-hours

**State storage:**
- Job states (pending → running → completed/failed) + history: 100M jobs/day × 500 bytes × 30-day retention = **~1.5 TB/month**
- Partition by date: each day's jobs in one table partition → easy purge, efficient queries by `scheduled_date`

**Architecture decisions driven by these numbers:**
- **Redis sorted set for ready-queue, not polling DB**: At 10 Hz polling × 10 scheduler instances, a `SELECT ... WHERE run_at <= now() LIMIT 1000` on a DB with 70K pending rows requires an index scan under concurrent writes (new job submissions). Under peak burst, this causes index lock contention. Redis `ZRANGEBYSCORE` is O(log N + M) on an in-memory sorted set with no locking — it's ~0.01ms at 70K entries vs ~5ms for a DB query under load.
- **At-least-once execution with idempotency, not exactly-once**: Exactly-once requires distributed transactions (scheduler + worker + DB). At the 1,157 jobs/sec rate, two-phase commit adds ~10ms of overhead per job — 11.57 seconds of overhead per second of throughput. Instead: detect duplicate execution (job_id uniqueness in the result table) and make jobs idempotent. Worker claims a job with `UPDATE ... SET status='running', worker_id=? WHERE job_id=? AND status='pending'` — a single atomic DB update.
- **< 1 second scheduling latency drives in-memory queue**: If the pending queue lives only in PostgreSQL, the scheduler must execute a DB query round trip (5–10ms), parse results, then dispatch. At 11,570 jobs/sec burst, 10ms × 11,570 = 115 seconds of queue backlog accumulates per second — unacceptable. Redis sorted set enables 0.1ms dispatch, processing the full burst in under 1 second.

---

## Related

**Concepts used in this design**

- [Distributed Locks](../../02-building-blocks/04-coordination/02-distributed-locks.md)
- [ZooKeeper Internals](../../04-advanced-topics/03-internals/10-zookeeper-internals.md)
- [Message Brokers](../../02-building-blocks/04-coordination/01-message-brokers.md)
- [Consensus Algorithms](../../01-foundations/05-advanced-distributed-theory/02-consensus-algorithms.md)
- [Raft & Paxos](../../04-advanced-topics/03-internals/11-raft-paxos-conceptual.md)

**Practice next**

- [Distributed Message Queue](../03-hard/distributed-message-queue.md)
- [Unique ID Generator](../01-easy/unique-id-generator.md)

Both hinge on leader election and exactly-once delivery.

**Frameworks**: [HLD Template](../../07-interview-templates/01-frameworks/01-hld-template.md) · [Capacity Estimation](../../07-interview-templates/02-cheat-sheets/02-capacity-estimation.md) · [Trade-offs Cheat Sheet](../../07-interview-templates/02-cheat-sheets/01-trade-offs-cheat-sheet.md)
