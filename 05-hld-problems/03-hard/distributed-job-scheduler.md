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
