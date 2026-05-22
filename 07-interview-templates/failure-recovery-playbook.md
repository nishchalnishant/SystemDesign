# Failure Recovery Playbook

## Framework: Answering "What Happens When X Fails?"

Use this template when the interviewer asks:
- "How does your system handle a database outage?"
- "What happens if your cache goes down?"
- "Walk me through your failure recovery."
- "How do you ensure no data is lost?"
- "What's your degradation strategy?"

---

## Degradation Hierarchy

Not all failures require the same response. Classify first, then apply the appropriate pattern.

```
Level 0: Transient error     → retry with backoff
Level 1: Single component    → circuit breaker + fallback
Level 2: Dependency failure  → graceful degradation (reduced functionality)
Level 3: Cascading failure   → shed load, preserve core functions
Level 4: Datacenter failure  → regional failover
Level 5: Multi-region event  → disaster recovery mode
```

---

## Level 0: Transient Errors — Retry with Backoff

**When to use**: network timeouts, temporary DB unavailability, 503s from a downstream service.

**Exponential backoff with jitter**:

```java
long delay = Math.min(BASE_MS * (1L << attempt), MAX_MS);
long jitter = ThreadLocalRandom.current().nextLong(delay / 2);
Thread.sleep(delay + jitter);
```

| Attempt | Base delay | With jitter (range) |
|---|---|---|
| 1 | 100ms | 100-150ms |
| 2 | 200ms | 200-300ms |
| 3 | 400ms | 400-600ms |
| 4 | 800ms | 800-1200ms |
| 5 | 1600ms | cap at MAX |

**Why jitter?** Without jitter, all clients retry simultaneously after an outage — creating a thundering herd that overwhelms the recovering service.

**When NOT to retry**:
- Non-idempotent operations (POST /charge) without an idempotency key
- HTTP 4xx responses (client error — retrying won't help)
- Already past the user-facing timeout (user gave up, pointless to succeed now)

---

## Level 1: Single Component Failure — Circuit Breaker

**Pattern**: wrap downstream calls in a circuit breaker. After N consecutive failures, "open" the circuit — stop sending requests for a cooldown period, return fast failures instead.

```
States:
  CLOSED (normal) → requests pass through
       │
       │  failure_count > threshold
       ▼
  OPEN (failure mode) → fast-fail all requests, return fallback
       │
       │  timeout expires
       ▼
  HALF-OPEN → let one probe request through
       │              │
  success →      failure →
  CLOSED        OPEN (reset timer)
```

**Configuration guide**:

| Parameter | Typical Value | Why |
|---|---|---|
| Failure threshold | 5 failures in 10 seconds | Filters noise, catches real failures |
| Open duration | 30-60 seconds | Give downstream time to recover |
| Half-open probe | 1 request | Don't hammer recovering service |
| Fallback | Cached response / default value | User sees degraded but not broken |

**Java**: Resilience4j `CircuitBreaker`, Hystrix (deprecated).

**Fallback strategies**:
- Return stale cached data
- Return a default/empty response ("product details temporarily unavailable")
- Return from a secondary service
- Queue the request for later processing

---

## Level 2: Dependency Failure — Graceful Degradation

**Principle**: identify which features are "core" vs "enrichment". When a dependency fails, disable enrichment features, keep core working.

### Feature Priority Matrix

| Feature | Dependency | Behavior on Failure |
|---|---|---|
| Load product page | Product DB | Show error page (cannot serve) |
| Show product recommendations | Recommendation service | Hide widget silently |
| Display product reviews | Reviews service | Show "Reviews loading..." (hide count) |
| Show personalized pricing | Pricing rules engine | Show list price |
| Track analytics | Analytics service | Drop event (non-critical) |
| Send order confirmation email | Email service | Queue for later, don't block checkout |

Document this matrix in your design — it shows operational maturity.

### Read-Only Mode

When the primary database is unreachable but replicas are available:

- Disable all write paths
- Serve reads from replica (stale by replication lag — announce to user)
- Queue write operations (user actions that need persisting) in a durable queue
- Drain queue when primary recovers

---

## Level 3: Cascading Failure — Load Shedding

**Problem**: Service A is overwhelmed → latency rises → timeouts → retries → A is even more overwhelmed → cascade.

**Load shedding**: deliberately reject requests above a threshold rather than letting all requests fail slowly.

```
if (current_queue_depth > MAX_ACCEPTABLE_QUEUE) {
    return 503 SERVICE_UNAVAILABLE;  // fast, cheap
}
// otherwise: process normally
```

**Priority-based shedding**:
- Shed low-priority traffic first (analytics pings, background jobs)
- Preserve critical path (checkout, payments, auth)
- Use request headers or user tier to classify priority

**Bulkhead pattern**: isolate thread pools per downstream dependency.

```
Thread pool for Payment Service:  max 20 threads
Thread pool for Inventory Service: max 20 threads
Thread pool for Email Service:     max 5 threads

If Email Service hangs and exhausts its 5-thread pool:
→ Payment and Inventory are completely unaffected
→ Email requests get fast rejections from the bulkhead
```

Without bulkheads, one slow dependency can exhaust the shared thread pool and kill all other requests.

---

## Level 4: Datacenter / AZ Failure — Regional Failover

### Active-Active (Multi-Region)

All regions serve traffic simultaneously. On failure:

1. Health checks detect AZ failure (ELB / Route 53 health checks)
2. Route 53 / Global Accelerator removes failing AZ from DNS
3. Traffic automatically routes to remaining AZs/regions
4. **RTO** (Recovery Time Objective): seconds to minutes (DNS TTL)
5. **RPO** (Recovery Point Objective): near-zero (writes replicated synchronously or async with small lag)

**Requirement**: data must be replicated across regions. Options:
- Synchronous cross-region replication (strong consistency, high latency)
- Asynchronous (eventual consistency, lower latency, small data loss window)

### Active-Passive (Standby)

Primary region handles all traffic. Standby is warm but not serving.

1. Primary fails → operations team triggers failover
2. DNS cutover to standby region
3. **RTO**: 5-30 minutes (manual failover step)
4. **RPO**: depends on replication lag (minutes)

Use active-passive when: write conflicts across regions are unacceptable, and you can tolerate minutes of downtime.

### RTO / RPO Targets by Tier

| Tier | Service Type | RTO Target | RPO Target |
|---|---|---|---|
| Critical | Payments, auth | < 1 min | 0 (sync replication) |
| High | Core user features | < 5 min | < 1 min |
| Medium | Non-core features | < 30 min | < 5 min |
| Low | Analytics, batch | < 4 hours | < 1 hour |

---

## Level 5: Multi-Region — Disaster Recovery Mode

When no region is healthy:

1. Activate static DR site (S3/CDN-served fallback page with status)
2. Enable offline queue (store user actions in client local storage or a durable queue)
3. Communicate status via status page (Statuspage.io)
4. Prioritize recovery of auth → data store → core services → non-core

---

## Database Failure Scenarios

### Primary DB Down (reads OK, writes fail)

```
Detection: connection timeout to primary
           health check fails on primary endpoint

Immediate:
1. Circuit breaker opens for all write paths
2. Read traffic routes to replica (declare "read-only mode" to users)
3. Queue write operations if durable queue exists

Recovery:
1. Automated: if using managed DB (RDS, Cloud SQL), automatic failover to replica
   → Promote replica to primary: ~30-60 seconds
2. Manual: promote replica, update DNS/connection string
3. Drain write queue after promotion
```

### Full DB Unavailability

```
Recovery sequence:
1. Restore from most recent backup (RTO depends on backup size + restore speed)
2. Replay WAL/binlog from backup point to present (RPO = last WAL segment retained)
3. Run consistency checks
4. Gradually re-enable write traffic
```

### Read Replica Lag Spike

```
Symptom: reads return stale data (user sees old state)
         replica lag metric > threshold (e.g., > 30 seconds)

Response:
1. Route time-sensitive reads to primary (write-after-read consistency)
2. Alert if replica can't catch up (may indicate replica overload)
3. Add replica if read load is the cause
```

---

## Cache Failure Scenarios

### Cache Miss (cache is down or cold)

```
Detection: Redis connection timeout, connection refused

Behavior: read-through (go to DB on every request)
Impact: DB load spike → possible DB cascade

Mitigation:
1. Circuit breaker on cache layer
2. Request coalescing: if cache is cold, only let one request hit DB for a key,
   have all others wait for the first to populate cache (dog-pile protection)
3. Probabilistic early expiration: recompute cache entry slightly before TTL
   to avoid thundering herd at exact expiration time
```

### Dog-Pile / Thundering Herd

When a popular cache key expires, thousands of requests simultaneously miss and hit the DB.

**Solution: mutex / token-based refresh**:

```java
String cached = cache.get(key);
if (cached == null) {
    boolean acquired = cache.setNX("lock:" + key, "1", 5_seconds);
    if (acquired) {
        try {
            String value = db.fetch(key);
            cache.set(key, value, TTL);
            return value;
        } finally {
            cache.delete("lock:" + key);
        }
    } else {
        // Another thread is refreshing — wait briefly and retry
        Thread.sleep(50);
        return cache.get(key);  // will likely hit now
    }
}
return cached;
```

---

## Message Queue Failure Scenarios

### Consumer Group Lag Spike

```
Symptom: Kafka consumer group lag grows
         → downstream services receive delayed events

Response:
1. Check consumer CPU/memory (processing too slow?)
2. Scale out consumers (add pods up to partition count)
3. Check upstream producer rate for spike
4. If lag is hours behind: consider temporary consumer scale-out + 
   partition rebalance to parallelize catch-up
```

### Kafka Broker Down

```
With replication factor 3, min.insync.replicas = 2:
→ Broker failure: partition leader election (~30 seconds)
→ Producers: brief pause, then reconnect to new leader
→ Consumers: reconnect to new leader

If 2 of 3 brokers fail:
→ acks=all writes fail (insufficient in-sync replicas)
→ Consider fallback: write to a durable buffer (local disk, DB) and replay later
```

---

## Payment-Specific Recovery

Payment failures are high-stakes: wrong recovery = double charge or lost payment.

```
Failure modes:
1. Request sent, response lost (client timeout):
   → Use idempotency key: retry with same key → no double charge
   → Payment provider deduplicates on idempotency key

2. Payment succeeded but confirmation lost:
   → Reconciliation job runs every 5 minutes
   → Queries payment provider for transactions in last 10 minutes
   → Marks any unconfirmed-but-charged bookings as confirmed

3. Payment provider down:
   → Circuit breaker: fast fail new payment attempts
   → Queue retry attempts (with exponential backoff up to 24 hours)
   → User shown "payment processing, we'll confirm via email"

4. Partial system failure (payment charged, DB write failed):
   → Transactional outbox: write to outbox atomically with DB
   → Outbox CDC publishes event only if DB commit succeeds
   → No orphaned charges
```

---

## Failure Recovery Template for Interviews

When asked "walk me through failure recovery for [component]":

1. **Classify the failure** — which component, which layer (network/app/data)
2. **Detection** — how do you know it failed? (health checks, circuit breaker, alert)
3. **Immediate mitigation** — what happens automatically in the first 60 seconds?
4. **Graceful degradation** — what functionality is preserved? What is disabled?
5. **Recovery path** — how does the system return to normal? Any manual steps?
6. **Data integrity** — was any data lost? How do you know? How do you reconcile?
7. **Prevention** — what change prevents this failure mode in the future?

---

## See Also

- `07-interview-templates/monitoring-slo-template.md` — SLO/SLI/error budget
- `02-building-blocks/circuit-breaker.md` — Circuit breaker patterns
- `04-advanced-topics/chaos-engineering.md` — Testing failure modes deliberately
- `09-patterns/saga-pattern.md` — Saga compensation for distributed transaction failures
- `02-building-blocks/message-brokers.md` — Message queue failure modes
