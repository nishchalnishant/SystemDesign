---
module: 05-hld-problems
topic: Hard
status: interview-ready
tags: [05-hld-problems, system-design, hard]
---
# Design a Distributed Job Scheduler

> **Difficulty**: Hard
> **Topics**: Queues, Leases, Retries, Idempotency, Cron, DAG
> **Time**: 75 min
> **Companies**: Amazon, Uber, Airbnb, Google

---

## Clarifying Questions

1. "Are we scheduling one-time jobs, recurring (cron) jobs, or both?"
2. "What scale — 10M scheduled jobs total, or 10M per day?"
3. "What's the latency requirement — fire within 1 second of scheduled time?"
4. "Do we need job dependencies (Job B runs after Job A succeeds)?"
5. "What's the retry policy — is exactly-once execution required?"
6. "Can job handlers be assumed idempotent, or must the scheduler guarantee exactly-once?"

---

## Back-of-Envelope

```
10M active scheduled jobs (total outstanding at any time)
100K job completions/minute = ~1,667/sec average throughput

Scheduler scan:
  Every 30 seconds: scan jobs WHERE next_run_at <= NOW() + 30s AND status='PENDING'
  With index on (next_run_at, status): O(log N + K) where K = jobs due in next 30s
  At 1,667/sec × 30s window = ~50K jobs due per scan → fast index range read

Storage:
  Job record: ~500 bytes × 10M = 5 GB (trivial for PostgreSQL/DynamoDB)
  Job payload (large): stored in S3 → job table holds S3 key reference

Worker fleet:
  100K completions/min = 1,667 jobs/sec
  Each worker completes 5 jobs/sec → 1,667 / 5 = ~334 workers needed at peak
```

---

## APIs

```
// Submit a job
POST /api/v1/jobs
  {
    "job_type": "send_email",
    "payload": {...},          // or payload_s3_key for large payloads
    "run_at": "2026-06-26T10:00:00Z",
    "cron_expression": null,   // non-null for recurring jobs
    "idempotency_key": "client-generated-uuid",
    "max_attempts": 5
  }
  -> { "job_id": "job_abc123", "status": "SCHEDULED" }

// Query job status
GET /api/v1/jobs/{job_id}
  -> { "job_id": "...", "status": "RUNNING", "attempt_count": 1, "last_run_at": "..." }

// Cancel a job
POST /api/v1/jobs/{job_id}/cancel
  -> { "status": "CANCELLED" }
```

---

## Architecture

```
Client
  |
Job API
  +-- Validate request
  +-- Check idempotency_key (UNIQUE constraint)
  +-- INSERT job (status=SCHEDULED, run_at, retry config)
  +-- Return job_id

Scheduler Shards (multiple for HA):
  +-- Every 30s: scan time bucket for due jobs
  +-- For each due job: conditional UPDATE SCHEDULED→READY (prevents double-enqueue)
  +-- Enqueue ready jobs to SQS ready queue

SQS Ready Queue
  |
Worker Fleet (ECS/Lambda, autoscaled)
  +-- Receive message (SQS visibility timeout = max_job_duration)
  +-- Acquire DB lease: UPDATE jobs SET status=RUNNING, lease_owner=worker_id, lease_expires_at=now+5min
  +-- Execute job handler (idempotent; passes idempotency_key to side-effecting services)
  +-- On success: UPDATE jobs SET status=SUCCEEDED; DEL SQS message
  +-- On failure: UPDATE jobs SET status=SCHEDULED, run_at=now+backoff, attempt_count++
  +-- Heartbeat every 30s: UPDATE jobs SET heartbeat_at=NOW() WHERE job_id=X AND lease_owner=worker_id

Zombie Detector (background, runs every 5 min):
  SELECT * FROM jobs WHERE status='RUNNING' AND heartbeat_at < NOW() - INTERVAL 5 MINUTES
  -> Mark FAILED + increment attempt_count + re-enqueue if under max_attempts

DLQ: Jobs with attempt_count >= max_attempts → alert + manual review
```

---

## Data Model

```sql
-- Main jobs table (PostgreSQL)
CREATE TABLE jobs (
    job_id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_type            VARCHAR(100) NOT NULL,
    payload_s3_key      VARCHAR(512),      -- large payloads stored in S3
    status              VARCHAR(20) NOT NULL,
    -- SCHEDULED | READY | RUNNING | SUCCEEDED | FAILED | CANCELLED
    run_at              TIMESTAMPTZ NOT NULL,
    cron_expression     VARCHAR(100),      -- non-null for recurring jobs
    attempt_count       INT DEFAULT 0,
    max_attempts        INT DEFAULT 5,
    lease_owner         VARCHAR(100),      -- worker_id holding the lease
    lease_expires_at    TIMESTAMPTZ,
    heartbeat_at        TIMESTAMPTZ,
    idempotency_key     VARCHAR(64) UNIQUE NOT NULL,
    created_at          TIMESTAMPTZ DEFAULT NOW(),
    updated_at          TIMESTAMPTZ DEFAULT NOW()
);

-- Critical index: scheduler scan reads this path every 30 seconds
CREATE INDEX idx_jobs_scheduler ON jobs(run_at, status)
    WHERE status IN ('SCHEDULED', 'READY');

-- DynamoDB alternative key design:
-- PK = time_bucket(run_at)  e.g., "2026-06-26T10:05" (5-minute buckets)
-- SK = run_at#job_id
-- GSI1PK = job_id (for status queries)
-- GSI2PK = status (for worker queries)
-- Time buckets let multiple scheduler shards scan non-overlapping windows

-- Job dependencies (for DAG execution)
CREATE TABLE job_dependencies (
    dependent_job_id    UUID REFERENCES jobs(job_id),
    prerequisite_job_id UUID REFERENCES jobs(job_id),
    PRIMARY KEY (dependent_job_id, prerequisite_job_id)
);
-- pending_deps counter on jobs table: decremented when each prerequisite completes
-- Job enqueued when pending_deps reaches 0
```

---

## Key Design Decisions

**1. Atomic DB claim prevents double-enqueue across scheduler shards**
Multiple scheduler shards scan for due jobs simultaneously. Naive approach: shard 1 reads job X as SCHEDULED and enqueues it; shard 2 reads the same record before shard 1's UPDATE commits → two SQS messages. Fix: each scheduler uses conditional UPDATE: `UPDATE jobs SET status='READY' WHERE job_id=X AND status='SCHEDULED' AND run_at <= NOW()`. The DB row lock serializes concurrent updates → only one shard's UPDATE succeeds (rows_affected=1); the other gets rows_affected=0 and skips. SQS can still deliver duplicate messages (at-least-once), so workers must be idempotent regardless.

**2. Worker lease + heartbeat for zombie detection**
Worker acquires lease at job start: `lease_owner=worker_id, lease_expires_at=NOW()+5min`. Worker heartbeats every 30s. If worker crashes: heartbeat stops, lease expires. Zombie detector finds jobs with `heartbeat_at < NOW() - 5min AND status='RUNNING'` → marks FAILED and re-enqueues. The 5-minute zombie detection window is intentional: long enough to avoid false positives (GC pause, slow I/O), short enough to recover quickly. For time-sensitive jobs: shorten to 2-minute detection.

**3. At-least-once delivery — idempotency is the job handler's contract**
Exactly-once execution is impossible end-to-end when workers can crash after side effects but before ACK. The scheduler guarantees at-least-once: every job runs at least once, possibly multiple times. Job handlers MUST be idempotent: `send_email(user_id, template_id, idempotency_key)` — the email service stores the key; a second call with the same key returns the original result without sending again. The `idempotency_key` from the job record is passed through to downstream services. This is the standard contract for all distributed job systems.

**4. Cron jobs: store expression, compute next run at completion**
Storing cron as a `cron_expression` field. On job completion (success or terminal failure): parse the expression → compute `next_run_at = next_occurrence(expression, NOW())` → INSERT a new job record for the next run. This approach: each run is an independent job record with its own history. No special cron scheduler needed — the same scheduler scans for the next instance. Cancelling a cron: set status='CANCELLED' on the current job + delete the `cron_expression` so no future job is created.

---

## Deep Dives

**DAG job dependencies**
Job B must run after Job A succeeds. Store edges in `job_dependencies(dependent_job_id, prerequisite_job_id)`. Each job has a `pending_deps` counter (count of uncompleted prerequisites). On job creation: `pending_deps = count(prerequisites)`. On prerequisite completion: `UPDATE jobs SET pending_deps = pending_deps - 1 WHERE job_id = dependent_job_id`. When `pending_deps = 0`: job becomes SCHEDULED → enqueued normally. Cycle detection: at job submission time, topological sort of the dependency graph; reject if a cycle is detected.

**Backpressure when workers fall behind**
Key metric: oldest READY job age. If a job was enqueued 5 minutes ago but hasn't been picked up: workers are behind. Alarm: CloudWatch metric on SQS `ApproximateAgeOfOldestMessage`. Response: autoscale workers. But autoscaling takes 2-5 minutes; for burst: pre-warm workers when queue depth exceeds threshold. Rate limiting on job submission during backlog: reject new jobs with 503 (backpressure) or route to a lower-priority queue. The queue itself is the buffer; worker autoscaling is the release valve.

---

## Failure Scenarios

| Failure | Impact | Mitigation |
|---------|--------|------------|
| Scheduler shard crashes | Jobs due during outage not enqueued | Another shard scans same time window; conditional UPDATE prevents duplication |
| Worker crashes mid-job | Job stuck in RUNNING | Heartbeat timeout detected by zombie detector → re-enqueue |
| Job always fails | Repeated retries | Exponential backoff delays; DLQ after max_attempts; alert ops |
| Hot time bucket (10K jobs due at same second) | Scheduler scan slow | Shard bucket by hash suffix; distribute scan across multiple shards |
| Large payload in job record | DB bloat | Store payload in S3; job record holds S3 key only |

---

## Interview Follow-ups

**How do you support cron?**
Store a schedule expression. When a run succeeds or fails terminally, compute the next `run_at` and insert the next job instance.

**How do you cancel a job?**
If status is `SCHEDULED` or `READY`, conditionally set `CANCELLED`. If already `RUNNING`, mark cancellation requested and let cooperative workers stop safely.

**What metric matters most?**
Oldest ready job age. It shows whether the system is falling behind even if average latency looks fine.

---

## Interviewer Follow-Up Questions

**On job correctness:**
- "A job is scheduled for 3pm. Your scheduler is down from 2:50-3:05pm. Does the job run at 3:05pm when the scheduler comes back?" → Depends on your missed-job policy. Options: (1) Run missed jobs immediately on recovery (best for most cases — correctness over timing). (2) Skip missed jobs (acceptable for idempotent metric aggregation where running late produces wrong numbers). (3) Run with `fired_too_late` flag so the job can self-decide. Implement via the DB: on scheduler startup, query `SELECT * FROM jobs WHERE next_run_at < NOW() AND status = 'PENDING'` and enqueue all of them. Recovery is automatic and correct.
- "How do you guarantee a job runs exactly once when your scheduler has multiple replicas?" → Two techniques: (1) DB-level atomic claim — each replica races to `UPDATE jobs SET status='RUNNING', picked_by='{host}', picked_at=NOW() WHERE job_id=X AND status='PENDING'`. Only one UPDATE succeeds (DB row lock serializes). (2) Distributed lock (Redis SETNX) per job_id. Both achieve exactly-one dispatch. The job execution itself must also be idempotent — if a worker crashes mid-job and another picks it up, running twice should be safe or detectable. Idempotency at execution is separate from exactly-once dispatch.
- "A job has been running for 6 hours. Its normal runtime is 5 minutes. How do you detect and handle this?" → Heartbeat: the worker emits a heartbeat every 30 seconds (`UPDATE jobs SET heartbeat_at=NOW() WHERE job_id=X`). The scheduler monitors `jobs WHERE status='RUNNING' AND heartbeat_at < NOW() - INTERVAL 5 MINUTES` — these are zombie jobs. Zombie handling: mark as `FAILED`, increment `retry_count`, re-enqueue if under max retries, send alert. The original worker may still be running — idempotent jobs handle this gracefully; non-idempotent jobs need the worker to check a `cancelled` flag before writing output.

**On scheduling and scale:**
- "You have 10M scheduled jobs with varying intervals (some every minute, some monthly). How do you efficiently find which jobs are due?" → Time-indexed query: `SELECT * FROM jobs WHERE next_run_at <= NOW() + INTERVAL 1 MINUTE AND status='PENDING' LIMIT 1000` executed every 30 seconds. Index on `(next_run_at, status)` makes this O(log N + K) where K=1,000. At 10M jobs with a balanced index: fast. The scheduler doesn't scan all 10M jobs — it only reads the ones due in the next window. After enqueueing: `UPDATE jobs SET next_run_at = next_run_at + interval, status='PENDING'`.
- "Your job scheduler must handle 100K job completions per minute. What's the bottleneck?" → DB write throughput: every job completion writes a result + updates the job status + computes next_run_at. 100K/min = ~1,667 writes/second. PostgreSQL handles ~10K writes/sec on reasonable hardware, so this is fine with connection pooling. Bottleneck in practice: the scheduler's polling query (reading due jobs) scans the same index being written by 1,667 completions/sec — index contention. Partition the `jobs` table by job_type or hash(job_id) to distribute write pressure.
- "How do you handle job dependencies — Job B must run after Job A completes?" → DAG (Directed Acyclic Graph) scheduler. Store edges in a `job_dependencies` table: `(dependent_job_id, prerequisite_job_id)`. On job completion, query `SELECT dependent_job_id FROM job_dependencies WHERE prerequisite_job_id = X`. For each dependent job, decrement its pending dependency count (stored as a counter): `UPDATE jobs SET pending_deps = pending_deps - 1 WHERE job_id = Y`. When `pending_deps = 0`: enqueue the job. This is how Airflow's executor works internally — a DAG scheduler with dependency tracking via edge tables.
