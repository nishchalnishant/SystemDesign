---
module: 05-hld-problems
topic: Medium
status: unread
tags: [05-hld-problems, system-design, medium]
---
# Design Notification Service

> **Difficulty**: Medium
> **Topics**: Pub-Sub, Message Queues, Third-party Integration, Rate Limiting
> **Time**: 45 minutes
> **Companies**: Amazon (SNS), Uber, LinkedIn, Airbnb

---

## Problem Mindmap

```
Notification Service
├── Problem Constraints
│   ├── Scale → 10M notifications/day = 115/sec avg; 1,667/sec peak; push/email/SMS channels
│   ├── Latency target → critical alerts < 5s; marketing < 5 min; DND scheduling for time zones
│   └── Core hardness → reliable delivery across unreliable third-party gateways (FCM/APNS/Twilio) + deduplication
├── Architecture Derivation
│   ├── Step 1 → Synchronous HTTP call to FCM per event → third-party latency blocks service; failures propagate
│   ├── Step 2 → Async Kafka queue → decouple producer from delivery; workers retry independently
│   ├── Step 3 → Separate Kafka topics by priority: critical / transactional / marketing → no marketing blocking critical
│   └── Step 4 → Redis idempotency key (TTL 24h) + circuit breaker (50% failure threshold) + DLQ for failed notifications
├── Core Components
│   ├── Notification API → validates request; enriches with user preferences (channel, DND); publishes to Kafka topic
│   ├── Kafka topics → "notif-critical" / "notif-transactional" / "notif-marketing"; separate consumer groups per channel
│   ├── Channel Workers → Push worker (FCM/APNS), Email worker (SendGrid), SMS worker (Twilio); retry with backoff
│   ├── Redis dedup → SET NX "notif:dedup:{idempotency_key}" EX 86400; skip if key exists (duplicate detected)
│   ├── Circuit breaker → per-channel; open at 50% error rate in 1-min window; half-open probe after 30s
│   └── Cassandra logs → (user_id, notif_id, channel, status, sent_at, delivered_at); TTL 1 year; audit trail
├── Data Model
│   ├── notifications → (notif_id UUID PK, user_id, type, channel, payload, priority, idempotency_key, status, created_at)
│   └── user_preferences → (user_id, channel_enabled{push,email,sms}, dnd_start, dnd_end, timezone, frequency_cap)
├── APIs
│   ├── POST /notifications → {user_id, type, channel, payload, idempotency_key, priority?} → {notif_id, queued_at}
│   ├── GET /notifications/{user_id}/history → [{notif_id, type, status, sent_at}] paginated
│   └── PUT /users/{user_id}/preferences → {dnd_start, dnd_end, channel_enabled, frequency_cap}
├── Critical Trade-offs
│   ├── Priority lanes → separate Kafka topics prevent marketing burst from delaying OTP/critical alerts
│   ├── At-least-once vs exactly-once → at-least-once with Redis dedup; easier than Kafka transactions; 24h TTL covers retry window
│   └── DND handling → delay queue: notifications scheduled to send at DND end time; stored in delayed Kafka topic
├── Failure Scenarios
│   ├── FCM rate limit → exponential backoff (1s, 2s, 4s, 8s); circuit breaker opens if > 50% fail; DLQ after 5 retries
│   ├── Kafka consumer lag → add consumer instances; Kafka partitions allow parallel consumption; auto-scale on lag metric
│   └── Redis dedup unavailable → fail-open: allow notification to send; risk of duplicate acceptable vs missed notification
└── Interview Angles
    ├── Amazon SNS → "Design SNS fan-out" → Kafka topics + channel workers + circuit breaker = production-grade answer
    ├── Uber → "Design ride status notifications" → critical lane for driver arrival; DLQ replay for missed ride events
    └── Follow-up → "How do you handle frequency capping?" → Redis counter INCR per (user_id, channel, day); reject if > cap
```

---

## What Breaks Without This System?

Uber needs to send a push notification when a driver accepts a ride. Without a dedicated notification service, the Order Service calls the FCM API directly in the HTTP request handler — synchronously. FCM is occasionally slow (2–5 seconds). Every ride acceptance request blocks a thread for 2–5 seconds waiting for FCM. At peak (10K ride acceptances/sec), all thread pool slots are occupied waiting for FCM. The Order Service stops responding to new requests. A notification delivery slowdown has taken down ride matching.

The other failure: Airbnb sends a booking confirmation email, an SMS, and a push notification. Without a unified notification service, each product team calls email/SMS/push APIs directly. There's no retry on failure — if the SMS gateway times out, the SMS is silently dropped. There's no rate limiting — 1M marketing emails blast out at once, exhausting the SendGrid rate limit and delaying transactional (booking-critical) emails by hours. There's no DND/quiet hours logic — users get notifications at 3am. There's no deduplication — a bug causes the same notification to be sent 3 times.

Without a notification service: delivery reliability is untested, priority is undefined (marketing and transactional notifications compete equally), and every product team reimplements the same retry/dedup/rate-limit logic differently.

---

## Derive the Architecture

**Step 1 — Direct API call in the request handler**
`POST /orders → order saved → FCM.send() → response`. Breaks: FCM latency in the critical path, no retry on FCM failure, no rate limiting.

**Step 2 — Move notifications off the critical path**
Publish a `notification_requested` event to Kafka after the business operation completes. The notification service consumes from Kafka and handles delivery. The original service responds immediately — notification delivery is fully async. FCM timeouts no longer affect the Order Service.

**Step 3 — What forces priority separation?**
"Your driver arrived" (time-critical, user waiting) must not be delayed by a batch of "Your weekly summary" emails being processed ahead of it. A single Kafka topic processes messages in FIFO order — one slow batch job starves real-time notifications.

Solution: separate topics per priority tier:
- `notifications.critical` (OTP, fraud alerts): consumed first, dedicated consumers, smallest batch size.
- `notifications.transactional` (order confirmations, ride receipts): standard latency.
- `notifications.marketing` (promotions, digests): processed during off-peak hours, large batch sizes allowed.

**Step 4 — Channel routing and fan-out**
One `notification_requested` event may need to send push + email + SMS. The notification service reads user preferences (opted-in channels, DND settings, locale) and fans out to channel-specific workers. Each channel (FCM, APNs, SendGrid, Twilio) has its own consumer group with channel-specific retry and rate-limit logic.

**Step 5 — Reliability: what happens when FCM fails?**
- Retry with exponential backoff: `delay = min(2^attempt * 100ms, 30s) + jitter`.
- DLQ (Dead Letter Queue): after N retries, move to DLQ. Ops team can inspect, replay, or drop.
- Circuit breaker: if FCM failure rate exceeds 50% in 60 seconds, stop sending to FCM (fail fast) and alert. Prevents retry storms from amplifying the outage.

**Step 6 — Deduplication (idempotency)**
The same event may be processed twice (Kafka at-least-once delivery). Without dedup, the user gets the same notification twice. Fix: store `notification_id` (derived from event key) in Redis with TTL=24h. Before sending, check `SETNX notification:{id}`. If key already exists, skip. If not, set the key and proceed.

**Step 7 — DND and delivery windows**
User preferences: quiet hours (11pm–8am), preferred channel, opt-out per notification type. The notification service reads preferences before dispatching. Critical notifications (OTP, fraud) bypass DND. Marketing notifications are queued and released at the start of the user's morning window.

---

## Real-Life Analogy

Think of a postal service that handles three types of mail: telegrams (SMS), express packages (push notifications), and regular letters (email). When a business drops off a shipment at the post office, the postal service decides how to route each piece, ensures priority telegrams go out today while bulk marketing flyers go out whenever capacity allows, and tracks delivery confirmation.

The tricky part: the postal service doesn't own the last mile. They hand packages to FedEx, UPS, or local carriers (Twilio, SendGrid, FCM/APNs). If FedEx is down, they reroute to UPS. If a package was already delivered, they don't deliver it again just because the driver crashed and restarted their route. And if you're on the Do Not Disturb list, your mail piles up at the post office until your preferred delivery window.

That's the notification service. You're a reliable intermediary with no direct control over final delivery.

---

## Why This Is Hard

1. **Priority inversion**: A marketing blast of 1M emails can be queued at the same time as an OTP that expires in 30 seconds. If they share the same queue, the OTP sits behind 1M marketing messages and the user can't log in. You need separate queues with dedicated workers, not just priority flags.

2. **Exactly-once delivery is impossible at the transport layer**: Kafka guarantees at-least-once delivery. If a worker crashes after calling Twilio but before committing its Kafka offset, it will process the message again on restart — sending the user two OTPs. The only fix is idempotency keys checked before every third-party API call.

3. **Third-party vendors are unreliable**: Twilio has outages. SendGrid rate-limits you. APNs silently drops tokens. You cannot design around a single vendor — you need circuit breakers, fallback vendors, and per-vendor rate limiters that match each vendor's published TPS cap.

4. **User preferences are a moving target**: A user opts out of marketing emails at 2pm. A marketing blast was already in-flight at 1:58pm. Do you check preferences at enqueue time or at delivery time? Checking at delivery time is more correct but adds a DB lookup on every message. Checking only at enqueue time misses late opt-outs.

5. **DND creates a scheduling problem**: "Don't send between 10pm and 8am" sounds simple until you realize you have 500M users across 40 timezones. You can't just reject messages — you have to hold them and re-deliver at the right local time, which means a delayed queue with per-message wake-up times.

6. **Retry storms amplify vendor failures**: If SendGrid goes down and you have 500K pending emails, each retrying with 3 attempts at 30-second intervals, you'll hammer a recovering SendGrid with 1.5M requests the moment it comes back up. Exponential backoff with jitter is required, not optional.

---

## Requirements

### Functional Requirements
1. Send email, SMS, and push notifications
2. Support bulk notifications (e.g., "Marketing blast to 1M users")
3. Support transactional notifications (e.g., "OTP", "Order Confirmed")
4. User preferences: opt-out/opt-in per channel
5. Do Not Disturb (DND) windows per user timezone
6. Frequency caps: prevent notification spam

### Non-Functional Requirements
1. **High Availability**: 99.99% uptime
2. **Durability**: No notification lost
3. **Throughput**: 1M+ notifications/minute
4. **Latency**: Real-time for OTP (< 5s end-to-end), eventual for marketing (< 10 min)
5. **Deduplication**: Exactly-once delivery semantics despite at-least-once infrastructure

---

## Capacity Estimation

```
Daily notifications: 10M/day
Peak: 1M within 10 minutes (marketing blast) = 1,667/sec sustained

Channel breakdown (typical):
  Push: 60% → 6M/day
  Email: 30% → 3M/day
  SMS: 10% → 1M/day

Storage (notification logs, 1KB each):
  10M/day × 1KB × 365 days = 3.65 TB/year
  Use Cassandra: write-heavy, TTL support, scales horizontally

OTP worker sizing:
  Assume 100K OTPs/day peak → ~70/sec at peak
  A single worker can do ~500 Twilio calls/sec with async I/O
  → 1 OTP worker instance is fine; run 3 for HA

Marketing worker sizing:
  1M emails in 10 min = 1,667 emails/sec
  SendGrid limit: 100 emails/sec per API key with standard plan
  → Need 17+ parallel workers with different API keys (or enterprise tier)
```

---

## API Design

### 1. Send Notification
```http
POST /api/v1/notifications
Authorization: Bearer <service-token>
Content-Type: application/json

{
  "idempotency_key": "order-confirmed-user-123-order-456",
  "user_id": "user_123",
  "type": "TRANSACTIONAL",        // TRANSACTIONAL | MARKETING | SYSTEM
  "channels": ["EMAIL", "PUSH"],  // Which channels to try
  "priority": "HIGH",             // HIGH | NORMAL | LOW
  "template_id": "order_confirmed",
  "payload": {
    "order_id": "456",
    "total": "$42.00"
  }
}

Response: 202 Accepted
{
  "notification_id": "notif_789",
  "status": "queued"
}
```

### 2. Bulk Send
```http
POST /api/v1/notifications/bulk
{
  "campaign_id": "summer-sale-2026",
  "segment": "all-users",         // or user_ids[] for explicit list
  "template_id": "summer_promo",
  "scheduled_at": null,           // null = send now
  "channels": ["EMAIL"]
}

Response: 202 Accepted
{
  "campaign_id": "summer-sale-2026",
  "estimated_recipients": 1000000,
  "estimated_completion": "2026-05-12T15:30:00Z"
}
```

### 3. Update User Preferences
```http
PUT /api/v1/users/{user_id}/preferences
{
  "email_enabled": true,
  "sms_enabled": false,
  "push_enabled": true,
  "timezone": "America/New_York",
  "dnd_start_time": "22:00",
  "dnd_end_time": "08:00",
  "marketing_frequency_cap": 3    // max marketing notifications per day
}
```

---

## Data Schema

### User Preferences (MySQL — low-write, high-read)
```sql
CREATE TABLE user_preferences (
    user_id BIGINT PRIMARY KEY,
    email_enabled BOOLEAN DEFAULT TRUE,
    sms_enabled BOOLEAN DEFAULT FALSE,
    push_enabled BOOLEAN DEFAULT TRUE,
    timezone VARCHAR(50) DEFAULT 'UTC',
    dnd_start_time TIME,          -- NULL = no DND
    dnd_end_time TIME,
    marketing_frequency_cap INT DEFAULT 5,  -- per day
    updated_at TIMESTAMP DEFAULT NOW()
);
```

### Device Tokens (for Push)
```sql
CREATE TABLE device_tokens (
    token_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token VARCHAR(512) NOT NULL,         -- FCM or APNs token
    platform VARCHAR(10),                -- ANDROID | IOS | WEB
    is_active BOOLEAN DEFAULT TRUE,
    last_seen TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_token (token)              -- For deduplication
);
```

### Notification Log (Cassandra — write-heavy, TTL-enabled)
```sql
-- Cassandra CQL
CREATE TABLE notification_logs (
    notification_id UUID,
    user_id BIGINT,
    channel VARCHAR(10),               -- EMAIL | SMS | PUSH
    type VARCHAR(20),                  -- TRANSACTIONAL | MARKETING
    status VARCHAR(20),                -- QUEUED | SENT | FAILED | DELIVERED
    template_id VARCHAR(100),
    idempotency_key VARCHAR(255),
    third_party_id VARCHAR(100),       -- Twilio/SendGrid message ID
    created_at TIMESTAMP,
    sent_at TIMESTAMP,
    error_message TEXT,
    PRIMARY KEY (user_id, created_at, notification_id)  -- Partition by user, cluster by time
) WITH default_time_to_live = 31536000;  -- 1 year TTL
```

### Idempotency Store (Redis)
```
Key:   idempotency:{idempotency_key}
Value: {notification_id, status, result}
TTL:   24 hours (covers all retry windows)
```

---

## Architecture

```
Client Services (Order Service, Auth Service, Marketing Tool)
       ↓
   Load Balancer
       ↓
┌───────────────────────────────────────────────────┐
│              Notification API                      │
│  - Rate limiting (100 req/sec per service)         │
│  - Idempotency check (Redis)                       │
│  - Input validation                                │
│  - User preference lookup → route/drop/delay       │
└────────────────────┬──────────────────────────────┘
                     │
         ┌───────────┴──────────────┐
         │                          │
         ▼                          ▼
┌─────────────────┐        ┌──────────────────────┐
│  Service Router │        │  User Preferences DB  │
│  (routes to     │◀──────▶│  (MySQL + Redis cache)│
│   Kafka topics) │        └──────────────────────┘
└────────┬────────┘
         │
         ▼
    Kafka Topics (Decoupling & Buffering)
    ┌──────────────────────────────────────┐
    │  [priority-otp]   [transactional]    │
    │  [email]          [sms]   [push]     │
    │  [delayed]        [dlq]              │
    └──────────────────────────────────────┘
         │
         ▼
┌─────────────────────┐
│   Workers           │
│   (Stateless        │  ← Scale independently per channel
│    Consumers)       │
└────────┬────────────┘
         │
    ┌────┴────┬─────────────┐
    ▼         ▼             ▼
  Email      SMS          Push
  Handler    Handler      Handler
(SendGrid) (Twilio)    (FCM/APNs)
    │         │             │
    ▼         ▼             ▼
  AWS SES  MessageBird  Fallback
(fallback) (fallback)   (Web Push)
         │
         ▼
  Cassandra (Notification Logs)
```

---

## Key Components

### 1. Priority Handling — Separate Queues

The most critical design decision: **never mix OTPs with marketing messages in the same queue.**

```
Kafka Topics:
  priority-otp         → dedicated workers (3 instances, always-on)
  transactional        → 10 worker instances
  email-marketing      → 20 worker instances (throttled to vendor limit)
  sms-marketing        → 5 worker instances
  push-marketing       → 15 worker instances
  delayed              → 2 worker instances (handles DND re-delivery)
  dlq-*                → 1 instance each (monitoring + replay)

Worker assignment:
  OTP workers: process message → call Twilio immediately → commit offset
  Marketing workers: process message → check frequency cap → call vendor → commit offset
```

Why separate instances, not just priority flags? A single Kafka consumer processes messages in order within a partition. If 100K marketing emails are already in-flight in a worker thread pool, a new OTP message waits in the queue. Physical separation guarantees OTP workers are never saturated by marketing load.

### 2. Deduplication — Idempotency Keys

Kafka is at-least-once. Workers crash. Vendors get called twice.

```java
public void processNotification(NotificationEvent event) {
    String idempotencyKey = event.getIdempotencyKey();

    // Check Redis BEFORE calling third party
    String existing = redis.get("idempotency:" + idempotencyKey);
    if (existing != null) {
        log.info("Duplicate notification detected, skipping: {}", idempotencyKey);
        return;  // Ack Kafka offset — message already processed
    }

    // Attempt delivery
    try {
        String thirdPartyId = sendViaVendor(event);

        // Store result atomically
        redis.setex("idempotency:" + idempotencyKey, 86400,
            buildResult(event.getNotificationId(), "SENT", thirdPartyId));

        updateNotificationLog(event.getNotificationId(), "SENT", thirdPartyId);

    } catch (Exception e) {
        // Don't set idempotency key on failure — allow retry
        throw e;  // Re-throw to prevent Kafka offset commit → will retry
    }
}
```

**The subtle case:** Twilio supports its own idempotency keys. Pass your `notification_id` as Twilio's `X-Twilio-Idempotency-Token` header. Even if your worker retries, Twilio deduplicates at their end. Belt and suspenders.

### 3. Failure Handling — Dead Letter Queues (DLQ)

```
Message fails 3 times → move to DLQ
  ↓
DLQ size alert fires → engineer investigates
  ↓
Options:
  a. Fix data issue → replay message to main queue
  b. Permanently invalid (bad email format) → mark FAILED, no replay
  c. Vendor-side issue resolved → bulk replay entire DLQ

DLQ retention: 7 days (gives engineers time to investigate)
```

What counts as a "poison pill":
- Permanently invalid email address (4xx from SendGrid — don't retry)
- Malformed phone number (format validation should catch this upstream)
- Template rendering error (variable missing from payload)

What warrants retry:
- Vendor 5xx errors (transient)
- Network timeout
- Rate limit exceeded (429 — back off, then retry)

```java
public void handleDeliveryFailure(NotificationEvent event, Exception e, int attemptNumber) {
    if (e instanceof PermanentDeliveryException) {
        // 4xx from vendor — no point retrying
        moveToDLQ(event, "PERMANENT_FAILURE", e.getMessage());
        updateLog(event.getNotificationId(), "FAILED");
    } else if (attemptNumber >= MAX_RETRIES) {
        moveToDLQ(event, "MAX_RETRIES_EXCEEDED", e.getMessage());
    } else {
        // Transient failure — re-throw, Kafka will re-deliver
        // Exponential backoff handled by Kafka consumer retry config
        throw new RetryableException(e);
    }
}
```

### 4. Third-Party Integration — Circuit Breaker + Fallback

```java
@CircuitBreaker(
    failureRateThreshold = 50,      // Trip if 50% of calls fail
    waitDurationInOpenState = 30000, // Wait 30s before half-open probe
    slidingWindowSize = 20           // Evaluate over last 20 calls
)
public String sendViaSendGrid(EmailEvent event) throws DeliveryException {
    // ... call SendGrid API
}

public String sendEmail(EmailEvent event) {
    try {
        return sendViaSendGrid(event);
    } catch (CallNotPermittedException e) {
        // Circuit is open — SendGrid is down, fail fast
        log.warn("SendGrid circuit open, falling back to AWS SES");
        return sendViaAWSSES(event);
    } catch (Exception e) {
        // SendGrid failed on this call
        log.warn("SendGrid failed, trying AWS SES: {}", e.getMessage());
        return sendViaAWSSES(event);
    }
}
```

**Outbound rate limiting per vendor:**
```
Twilio limit: 100 SMS/sec → token bucket with 100 tokens/sec
SendGrid limit: 100 emails/sec (standard) → 1,000/sec (enterprise)
FCM limit: 600K messages/min → 10K/sec per project

Implementation: Redis token bucket per vendor
  Key: rate_limit:twilio
  Refill: 100 tokens every second
  Workers check before every API call
```

### 5. DND and Delayed Delivery

```java
public void routeNotification(NotificationEvent event) {
    UserPreferences prefs = getUserPreferences(event.getUserId());

    // Check channel opt-out
    if (!isChannelEnabled(prefs, event.getChannel())) {
        log.info("User {} has disabled {}, dropping", event.getUserId(), event.getChannel());
        return;
    }

    // Marketing-only: check frequency cap
    if (event.getType() == MARKETING) {
        String capKey = "freq_cap:" + event.getUserId() + ":" + today();
        long count = redis.incr(capKey);
        redis.expireAt(capKey, endOfDay());
        if (count > prefs.getMarketingFrequencyCap()) {
            log.info("Frequency cap exceeded for user {}, dropping", event.getUserId());
            return;
        }
    }

    // Check DND (transactional/OTP bypass DND)
    if (event.getType() == MARKETING && isInDND(prefs)) {
        ZonedDateTime deliveryTime = nextDeliveryWindowStart(prefs);
        publishToDelayedQueue(event, deliveryTime);
        return;
    }

    // Route to appropriate Kafka topic
    publishToChannel(event);
}
```

**Delayed queue implementation:**
- Kafka topic `delayed` with workers that sleep until `scheduled_delivery_time`
- Alternative: Redis Sorted Set with score = delivery timestamp; cron sweeps and publishes to main queue

---

## Scaling

### Kafka Partitioning Strategy

```
Topic: email-marketing
Partitions: 50
Partition key: hash(user_id) % 50

Why user_id? Ensures a single user's notifications are always processed
in order (no out-of-order email/push to same user from same campaign).

Workers: 50 consumer instances (1 per partition)
Throughput: 50 workers × 100 emails/sec each = 5,000 emails/sec
            5,000/sec × 60 = 300,000/min → within vendor limits with pooled keys
```

### Scaling Workers Independently

```
OTP workers:      3 instances (low volume, must be fast)
Email workers:   20 instances (high volume, throttled to SendGrid limit)
SMS workers:      5 instances (expensive — throttle aggressively)
Push workers:    15 instances (FCM handles high throughput well)

Auto-scaling: Kafka consumer lag triggers horizontal pod autoscaler
  If lag > 10,000 messages → spin up additional worker instances
  If lag < 1,000 messages for 5 min → scale down
```

### User Preferences Caching

```
User preferences: Read on every notification delivery
Pattern: Read-heavy, write-rare (users don't change preferences often)

Cache: Redis with 1-hour TTL
  Key: prefs:{user_id}
  Miss → MySQL query → populate cache

Write-through: On preference update, invalidate Redis key immediately
Concern: 500M users × ~200 bytes = 100 GB → cache only active users
  Active users (last 30 days): ~200M → 40 GB → feasible
```

---

## Failure Scenarios

### Kafka Goes Down
**Impact:** No new notifications queued; API returns 503
**Mitigation:**
- Kafka cluster with 3+ brokers (replication factor 3)
- ISR (In-Sync Replicas) = 2 → survives single broker failure
- Producers retry with exponential backoff (async producers buffer locally)
- RTO: < 30 seconds for automatic leader election

### SendGrid/Twilio Outage
**Impact:** Email/SMS delivery halted; messages queue up
**Mitigation:**
- Circuit breaker trips → route to fallback vendor (AWS SES / MessageBird)
- Messages buffered in Kafka (7-day retention) — no loss
- Automatic recovery: circuit breaker probes vendor every 30 seconds
- Alert on DLQ growth rate to detect sustained outage

### Worker Crash Mid-Processing
**Impact:** Message re-delivered → potential duplicate
**Mitigation:**
- Idempotency keys (Redis, 24h TTL) prevent duplicate delivery
- Pass notification_id to vendor as their idempotency key
- Kafka offset only committed after successful delivery + Redis write

### Redis (Idempotency Store) Goes Down
**Impact:** Cannot check idempotency → risk of duplicate delivery
**Mitigation:**
- Redis Cluster with replicas (Sentinel or Cluster mode)
- Fallback: check `notification_logs` Cassandra table (slower, 50ms vs 1ms)
- OTP duplicates are less harmful than OTP loss — acceptable temporary risk

---

## Trade-offs

| Aspect | Choice | Alternative | Trade-off |
|--------|--------|-------------|-----------|
| **Queue** | Kafka per channel | Single queue with priorities | Physical isolation prevents OTP starvation vs. simpler ops |
| **Deduplication** | Redis idempotency keys | DB unique constraint | ~1ms check vs. DB write overhead on every delivery |
| **Preferences check** | At delivery time | At enqueue time | Catches late opt-outs vs. 1 extra DB lookup per message |
| **DND handling** | Delayed queue (Redis Sorted Set) | Drop and re-send | No notification loss vs. complexity of scheduling |
| **Vendor fallback** | Circuit breaker + secondary | Retry primary only | Resilience vs. maintaining two vendor integrations |
| **Log storage** | Cassandra | PostgreSQL | TTL support + write throughput vs. SQL query flexibility |

---

## Interview Tips

**Q: "How do you handle users in Do Not Disturb (DND) mode?"**
Workers check User Preferences DB before sending. If DND is active for marketing messages, park the message in a `delayed` Kafka topic or Redis Sorted Set with `score = delivery_timestamp`. A scheduler sweeps and re-publishes at the start of the user's delivery window. OTPs and security alerts bypass DND entirely.

**Q: "How to prevent spamming users?"**
Implement a frequency cap in Redis: `INCR freq_cap:{user_id}:{date}` before sending marketing notifications. If the count exceeds the cap (e.g., 5 marketing notifications/day), drop the message. Use `EXPIREAT` to reset the counter at midnight in the user's timezone. Critical (transactional) notifications bypass the cap.

**Q: "What if the worker crashes after sending to Twilio but before updating the DB?"**
Idempotency. The worker checks Redis for `idempotency:{idempotency_key}` before calling Twilio. If present, the message was already sent — skip. If not present, call Twilio, then set the Redis key. If Twilio supports idempotency keys (it does via `X-Twilio-Idempotency-Token`), pass the notification_id — Twilio deduplicates on their end too. Redis key is set only after Twilio confirms success; Kafka offset is committed only after Redis key is set.

**Q: "How do you know if a push notification was actually delivered?"**
FCM/APNs return delivery receipts asynchronously via webhook. Store the initial status as `SENT` in the notification log. When the delivery receipt arrives, update to `DELIVERED` or `FAILED`. For failed tokens (uninstalled app), mark the device token as inactive in the `device_tokens` table. Periodically prune inactive tokens to avoid wasting push budget.

**Q: "How do you handle a marketing blast to 1M users without overwhelming SendGrid?"**
Chunk the blast: query users in batches of 10K, enqueue to Kafka. Workers consume at a rate that matches SendGrid's rate limit (throttled via Redis token bucket). A 1M-email blast at 5K emails/sec takes ~200 seconds — well within a 10-minute SLA. Kafka buffers the 1M messages durably; workers drain the queue at whatever rate the vendor allows.

---

## Interview Questions Asked

### Meta
1. **"Design a push notification system for 2 billion users."** → Tests fan-out architecture and channel isolation at scale; key answer: separate Kafka topics per channel (push/SMS/email) with priority lanes, device token registry, and vendor abstraction layer.

### Google
1. **"How would you fan-out a notification to 100M subscribers?"** → Tests write-amplification awareness; key answer: hybrid fan-out — write to a Kafka topic once, worker pool fans out in parallel batches; never write to 100M rows synchronously.

### Amazon
1. **"Walk me through an SNS/SQS-based notification architecture."** → Tests knowledge of managed queuing primitives; key answer: SNS topic per event type, fan-out to per-channel SQS queues, Lambda/ECS workers per queue with DLQ for failures.

### Common Follow-ups
1. **"How do you handle notification preferences and quiet hours?"** → Tests read path awareness; check User Preferences DB before delivery; park marketing messages in Redis Sorted Set scored by `delivery_timestamp`; OTPs bypass all preferences.
2. **"How do you deduplicate duplicate sends?"** → Tests idempotency design; Redis key `idempotency:{notification_id}:{channel}` checked before every vendor call; pass vendor-native idempotency tokens (e.g., `X-Twilio-Idempotency-Token`) as a second guard.
3. **"How do you track delivery vs open rates?"** → Tests observability depth; delivery: vendor webhooks update `notification_logs` status to `DELIVERED`/`FAILED`; opens: SDK beacon on notification tap writes to event bus → analytics pipeline.
4. **"How do you handle Apple APNs token expiry?"** → Tests mobile platform specifics; APNs returns `410 Gone` or `BadDeviceToken` for stale tokens; on each response, mark token `inactive` in `device_tokens` table and re-register when the app next opens.
