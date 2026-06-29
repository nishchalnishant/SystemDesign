---
module: 05-hld-problems
topic: Medium
status: interview-ready
tags: [05-hld-problems, system-design, medium]
---
# Design a Notification Service

> **Difficulty**: Medium
> **Topics**: Kafka, Fan-Out, Channel Routing, Retry, Circuit Breaker
> **Time**: 45 min
> **Companies**: Meta, Google, Amazon, Common

---

## Clarifying Questions

1. "What channels are we supporting — push, email, SMS, or all three?"
2. "What's the scale — 10M notifications/day or 10B?"
3. "Do we need priority tiers — critical alerts vs. marketing emails?"
4. "What are the delivery guarantees — at-least-once is fine, or exactly-once?"
5. "Do we need per-user preferences (DND, opt-out, frequency caps)?"
6. "Is there a latency SLA — critical alerts in <1s, marketing in <5 min?"

---

## Back-of-Envelope

```
10M notifications/day
  Avg: 10M / 86,400 = ~115/sec
  Peak (5PM or campaign blast): ~1,667/sec

Channel split:
  Push: 60% → 6M/day (FCM/APNs)
  Email: 30% → 3M/day (SendGrid)
  SMS:   10% → 1M/day (Twilio)

Kafka topics:
  notifications.critical:    ~100/sec peak (OTP, auth alerts)
  notifications.transactional: ~1,000/sec peak (order confirm, delivery)
  notifications.marketing:   ~500/sec peak (campaigns)

Storage:
  Notification log: ~300 bytes × 10M/day = 3 GB/day → Cassandra (append-only)
```

---

## APIs

```
// Send notification (internal API, called by product services)
POST /api/v1/notifications
  {
    "user_id": 12345,
    "template_id": "order_shipped",
    "params": { "order_id": "ORD-789", "tracking_url": "..." },
    "priority": "transactional",
    "idempotency_key": "order-789-shipped-notif"
  }
  -> { "notification_id": "...", "status": "queued" }

// Get notification history for a user
GET /api/v1/notifications/{user_id}/history?limit=20&cursor={cursor}
  -> { "notifications": [...], "next_cursor": "..." }

// Update user preferences
PUT /api/v1/users/{user_id}/preferences
  {
    "channels": { "push": true, "email": true, "sms": false },
    "dnd_start": "22:00",
    "dnd_end": "08:00",
    "timezone": "America/New_York",
    "frequency_cap": { "marketing": 3, "window_hours": 24 }
  }
  -> { "status": "updated" }
```

---

## Architecture

```
Product Services (Order, Auth, Marketing)
  |
  | POST /notifications
  |
Notification API Service
  +-- Validate request
  +-- Load user preferences (Redis cache, TTL 5min)
  +-- Check DND / frequency cap
  +-- Dedup check: SET NX "notif:dedup:{idempotency_key}" EX 86400
  +-- Route to Kafka topic by priority
  |
  +-- notifications.critical    (partition by user_id)
  +-- notifications.transactional
  +-- notifications.marketing
  |
Channel Workers (Kafka consumers per topic, autoscaled)
  |
  +-- Push Worker  --> FCM (Android) / APNs (iOS)
  +-- Email Worker --> SendGrid
  +-- SMS Worker   --> Twilio
  |
Each worker:
  1. Render template with params
  2. Apply per-channel circuit breaker (Redis failure rate counter)
  3. Send via provider API
  4. Write delivery status to Cassandra
  5. Emit "delivered"/"failed" event to Kafka for retry logic

Retry Worker:
  Consumes "failed" events
  delay = min(2^attempt × 100ms, 30s) + random_jitter
  After 5 attempts: DLQ (notifications.dlq)
```

---

## Data Model

```sql
-- Notification log (Cassandra)
-- partition: user_id
-- clustering: notif_id DESC (Snowflake, time-sortable)
-- columns: template_id, channel, status, sent_at, delivered_at, opened_at

-- User preferences (PostgreSQL, cached in Redis)
CREATE TABLE user_preferences (
    user_id         BIGINT PRIMARY KEY,
    push_enabled    BOOLEAN DEFAULT true,
    email_enabled   BOOLEAN DEFAULT true,
    sms_enabled     BOOLEAN DEFAULT false,
    dnd_start       TIME,
    dnd_end         TIME,
    timezone        VARCHAR(50) DEFAULT 'UTC',
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE frequency_caps (
    user_id         BIGINT,
    category        VARCHAR(50),  -- 'marketing', 'transactional', 'critical'
    max_count       INT,
    window_hours    INT,
    PRIMARY KEY (user_id, category)
);

-- Idempotency keys (Redis, TTL 24h)
-- Key: "notif:dedup:{idempotency_key}"  Value: notification_id
-- SET NX EX 86400 — only first caller wins
```

---

## Key Design Decisions

**1. Kafka priority topics for multi-tier SLAs**
One queue → marketing blasts delay critical OTPs. Three topics: `notifications.critical` (OTP, security alerts, SLA: <5s), `notifications.transactional` (order confirmations, SLA: <60s), `notifications.marketing` (promotions, SLA: <5min). Each topic has dedicated consumer groups with separate autoscaling policies. Critical workers have higher priority in worker fleet scheduling. Partitioned by user_id for per-user ordering.

**2. Circuit breaker per channel**
If FCM starts returning errors (their outage): without a circuit breaker, all push workers queue up, exhaust retry threads, and delay transactional notifications. Circuit breaker (per channel): track error rate in Redis `cb:{channel}:errors` with 1-min sliding window. If error rate > 50%: open circuit, stop sending to that channel, return "PROVIDER_DOWN" status. After 30s: half-open, probe with one request. Close on success. This prevents cascade failures.

**3. Redis dedup with `SET NX`**
`SET "notif:dedup:{idempotency_key}" {notification_id} NX EX 86400` — atomically sets if not exists, expires after 24h. If the product service retries the same notification (network timeout, at-least-once Kafka): the second call hits the existing Redis key, returns the cached notification_id without re-sending. This prevents double push notifications to users.

**4. DND and frequency cap enforcement**
DND: `if current_time in [dnd_start, dnd_end] for user timezone: delay to dnd_end`. Delayed notifications stored in a "scheduled" Kafka topic with a future timestamp. Frequency cap: Redis counter `freq_cap:{user_id}:{category}:{window_bucket}` — INCR and check against max_count. If exceeded: drop (marketing) or queue for next window (transactional). Critical notifications bypass both DND and frequency caps.

---

## Deep Dives

**Template rendering**
Templates stored in a key-value store (Redis or DynamoDB): `template:{template_id}:{channel}:{locale}`. Rendering is done in the channel worker using a lightweight template engine (Mustache/Handlebars). Personalization: user name, locale-specific date formats, currency. A/B testing: template_id lookup checks experiment assignment first (10% of users get template_v2). Template cache TTL: 5 minutes (allows rollout without worker restarts).

**Large-scale marketing campaigns**
Campaign sends 100M notifications at once. Naive: product service calls POST /notifications 100M times — overloads the API and Kafka. Better: product service creates a campaign job (POST /campaigns) with user segment + template. Campaign worker queries the user segment (batch DB query), writes directly to `notifications.marketing` Kafka topic in batches of 1,000, at a controlled rate (1,000/sec). This rate-limits the campaign to avoid overwhelming SendGrid (which has its own throughput limits).

---

## Failure Scenarios

| Failure | Impact | Mitigation |
|---------|--------|------------|
| FCM outage | Push notifications fail | Circuit breaker opens; fall back to email if user has it; retry when circuit closes |
| Kafka consumer lag | Notifications delayed | Autoscale consumers; alert on consumer lag > 5min; prioritize critical topic |
| SendGrid rate limit (429) | Email delivery throttled | Exponential backoff retry; respect Retry-After header; use multiple SendGrid accounts |
| Duplicate notification | User receives same push twice | Redis dedup SET NX prevents double-send; provider-side idempotency key |
| DLQ fills up | Notifications permanently undelivered | Alert on DLQ depth; manual review + replay for transactional; discard marketing |

---

## Interview Questions Asked

### Meta
1. **"How does Facebook decide whether to send a push notification or an in-app notification for a 'like' event?"** → Presence check first: if the user is currently active in the app (WebSocket connected, or last_active < 5 minutes ago), send in-app (badge update via WebSocket). No push notification needed — interrupting an active user is bad UX. If user is inactive: check push preference → send FCM/APNs. The notification service has a `delivery_strategy` field per template: "in-app-preferred" vs "push-always". Presence data comes from the real-time presence service (Redis).

### Google
1. **"How do you design a notification system for Google Workspace where a single email can trigger notifications across Calendar, Meet, Gmail, and Chat simultaneously?"** → Cross-product fan-out: the originating event (e.g., calendar invite) publishes to a shared notification bus (Kafka). Each product's notification consumer subscribes and decides whether to notify. Deduplication challenge: user gets 4 notifications for one calendar invite. Solution: "notification aggregation" layer — within a 5-minute window, group notifications by event_id and render as one summary: "3 new updates in Calendar". The aggregation window is product-configurable.

### Amazon
1. **"Design the notification service for Amazon's order pipeline — how do you notify customers across 200 countries in their local language?"** → Locale-aware template rendering: template lookup uses `template:{id}:{channel}:{locale}`. User locale stored in preferences. Currency formatting, date formats, and copy are all locale-specific. For 200 countries: 200+ locale files per template. Translation pipeline: source template in English → automatic translation (Amazon Translate) → human review for key markets (EN, DE, FR, JP, ZH) → stored in DynamoDB. Cache templates in Redis per locale (TTL 1h) to avoid DB hits on every notification render.
2. **"How do you handle notification delivery for a Prime Day sale with 100M customers?"** → Campaign mode: pre-compute the target user list (100M customer_ids) offline, store in S3 as a partitioned file. Campaign worker reads file in batches (1M users/batch), writes to `notifications.marketing` Kafka at a controlled 50K/sec. SendGrid has a throughput limit: negotiate a dedicated IP pool + 10M emails/hour throughput. Campaign takes 10 hours — start 10 hours before Prime Day. Monitor per-batch delivery rates; pause and investigate if error rate exceeds 5%.

### Common Follow-ups
1. **"How do you prevent a user from receiving duplicate push notifications?"** → Three layers: (1) Redis SET NX idempotency key at ingestion — prevents duplicate Kafka messages. (2) Kafka consumer at-most-once processing within a dedup window — idempotent consumer group offset commits. (3) FCM/APNs have message_id — same message_id = deduplication at provider level. In practice, (1) catches 99% of duplicates; (2) catches Kafka consumer redeliveries; (3) catches any that slip through.
2. **"How do you handle a user who has uninstalled the app but still receives push notifications?"** → FCM/APNs return a specific error code for unregistered tokens: `NotRegistered` (FCM) or `BadDeviceToken` (APNs). The push worker intercepts these error responses and: (1) deletes the device token from the user's token list in the DB, (2) marks the user as "push_disabled" in preferences. Future notifications skip push for this user. Email becomes the fallback channel if the user still has email preferences enabled.

---

## Interviewer Follow-Up Questions

**On delivery reliability:**
- "A notification fails to deliver. How does your retry mechanism work?" → Exponential backoff: `delay = min(2^attempt × 100ms, 30s) + random_jitter`. Attempt 1: 0ms (immediate). Attempt 2: 100ms + jitter. Attempt 3: 200ms + jitter. Attempt 4: 400ms. Attempt 5: 30s. After 5 attempts: move to DLQ. Jitter prevents retry storms (100 failed notifications all retrying at exactly 30s = thundering herd on the provider). DLQ: alert ops, manual review, replay if transactional. Marketing: log and discard.
- "How do you guarantee a critical security alert (2FA code) is delivered in under 5 seconds?" → Critical topic has dedicated consumer group with 20 pre-warmed workers (not autoscaled — scale-up takes 30s, unacceptable for critical). No retry delay for critical (attempt 1 immediate, attempt 2 immediate via different channel). Channel fallback: push → SMS if push fails within 1s. SMS is highly reliable (carrier routing). 2FA codes also have short TTL (5 min) so retrying makes no sense after expiry — the retry worker checks token expiry before re-sending.
- "Your notification service is supposed to send transactional emails but your email provider (SendGrid) goes down. What's your plan?" → Circuit breaker opens on SendGrid. Options: (1) Queue emails in Kafka (notifications.transactional) with a 30-minute retry window — most outages are brief. (2) Failover to secondary email provider (Mailgun) — pre-configured as backup, circuit breaker routes there on primary failure. (3) Fall back to SMS for order-critical notifications (not marketing). The decision tree is encoded in the channel worker's routing config, not hard-coded.

**On scale and preferences:**
- "How do you enforce a frequency cap of '3 marketing notifications per user per day'?" → Redis counter: `INCR freq_cap:{user_id}:marketing:{date_bucket}` with `EXPIRE ... {seconds_until_midnight}`. Check count before sending: if count >= 3, drop the notification or queue for tomorrow. Date bucket is UTC date. Key expires at midnight UTC. This is approximate — in a distributed system, two workers might both read count=2 and both send, allowing 4 on a race. For marketing, this is acceptable. For strict caps: Lua script to check-and-increment atomically.
- "How do you update user preferences in real-time when a user opts out mid-campaign?" → Opt-out is stored in PostgreSQL (durable). Notification workers cache preferences in Redis (TTL 5min). On opt-out: invalidate Redis cache immediately (`DEL user_prefs:{user_id}`). Next notification send for this user: cache miss → re-fetch from PostgreSQL → sees opt-out flag → skip. Worst case: 5-minute window where a cached "opted-in" state allows one more notification after opt-out. For strict compliance (GDPR unsubscribe): TTL 0 (no cache) for email opt-outs; always read from DB.
