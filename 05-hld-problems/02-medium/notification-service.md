> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design a notification service — delivering push/email/SMS notifications reliably at high throughput with deduplication, prioritization, and user preference management.
>
> **Key design decisions:**
> - Channel abstraction: unified notification model with channel plugins (APNs for iOS, FCM for Android, SendGrid for email, Twilio for SMS); each plugin handles delivery
> - Reliability: Kafka queue per channel; at-least-once delivery; idempotency key prevents duplicates on retry; DLQ for failed notifications
> - Priority queues: critical (OTP, alerts) → high-priority queue; marketing → low-priority queue; ensure critical delivery even under load
> - Fan-out: event (e.g., new follower) → notification service → fan-out to all followers; large fan-outs (10M followers) batched async
> - User preferences: preference DB (user_id → {push: on, email: on, SMS: off, quiet_hours: 22:00–08:00}); check before every send
> - Deduplication: dedup by (user_id, notification_type, reference_id) in Redis with 1-hour TTL; prevent duplicate email for same event
> - Rate limiting: cap per user/per type to avoid notification fatigue; e.g., max 3 marketing emails/day
>
> **Key takeaway:** The preference check + deduplication layer is critical — a raw fan-out without it spams users and destroys engagement; always respect quiet hours and per-channel opt-outs.

---
module: 05-hld-problems
topic: Medium
status: unread
tags: [05-hld-problems, system-design, medium, notifications, push, email, fan-out, reliability]
---
# Design a Notification Service

> **Difficulty**: Medium | **Asked at**: Amazon, Meta, Uber, LinkedIn

---

## Problem Statement

Design a notification service that delivers messages to users across multiple channels: push notifications (iOS/Android), email, and SMS. The service must handle high throughput, support prioritization, and guarantee at-least-once delivery without duplicate notifications.

---

## Functional Requirements

1. **Send notifications**: Trigger notifications from any internal service (order updates, social alerts, marketing)
2. **Multi-channel**: Deliver via push (FCM/APNs), email (SES/SendGrid), SMS (Twilio)
3. **User preferences**: Users opt in/out of specific notification types per channel
4. **Prioritization**: Critical alerts (password reset, payment failure) bypass rate limits and queues
5. **Deduplication**: Prevent duplicate notifications if a message is retried
6. **Delivery tracking**: Track sent, delivered, opened, failed status per notification

---

## Non-Functional Requirements

- **Throughput**: 10M notifications/day → 115/sec average; peak 10,000/sec (flash sale, breaking news)
- **Latency**: High-priority notifications delivered < 1s; low-priority < 5 minutes
- **Reliability**: At-least-once delivery — never drop a notification; acceptable to deliver twice (deduplication at delivery layer)
- **Availability**: 99.99% — notification delivery must not be impacted by upstream service failures
- **Scalability**: Support 1B users with registered devices

---

## Core Entities

| Entity | Key Fields |
|--------|-----------|
| `Notification` | notification_id, user_id, type, channel, title, body, data, priority, idempotency_key |
| `UserPreference` | user_id, notification_type, channel, enabled, quiet_hours |
| `DeviceToken` | user_id, device_id, platform (ios/android/web), token, updated_at |
| `DeliveryRecord` | notification_id, channel, status (sent/delivered/failed/opened), timestamp |
| `Template` | template_id, channel, subject, body_template |

---

## API Design

```http
POST /api/v1/notifications
Body: {
  "user_id": "u123",
  "type": "order_shipped",
  "priority": "high",
  "channels": ["push", "email"],
  "idempotency_key": "order-123-shipped",
  "data": { "order_id": "ord456", "tracking": "UPS789" },
  "template_id": "order-shipped-v2"
}
Response 202: { "notification_id": "n789", "status": "queued" }

POST /api/v1/notifications/bulk
Body: { "user_ids": ["u1", "u2", ...], "type": "promo", "template_id": "..." }
Response 202: { "batch_id": "b123", "count": 5000000 }

GET /api/v1/notifications/{notification_id}/status
Response 200: { "status": "delivered", "channel": "push", "delivered_at": "..." }

PUT /api/v1/users/{user_id}/preferences
Body: { "order_shipped": { "push": true, "email": true, "sms": false } }
```

---

## High-Level Design

```
Producer Services (Order, Auth, Social, Marketing)
  │
  │ POST /notifications
  ▼
Notification API
  │
  ├── Validate + enrich (fetch user prefs, device tokens)
  ├── Deduplication check (Redis idempotency key)
  ├── Priority routing:
  │     high priority → Redis pub/sub (instant)
  │     normal priority → Kafka (buffered)
  │
  ▼
Channel Workers (horizontally scaled)
  ├── Push Worker → FCM (Android) / APNs (iOS)
  ├── Email Worker → Amazon SES / SendGrid
  └── SMS Worker → Twilio / AWS SNS
  │
  ▼
Delivery DB (Cassandra) — status tracking
  │
  ▼
Analytics → ClickHouse (open rates, click rates, delivery rates)
```

**Kafka topics**: `notifications-high`, `notifications-normal`, `notifications-marketing`. High-priority topic has more consumers. Marketing topic is rate-limited to 1,000/sec.

---

## Deep Dive 1: Reliability and At-Least-Once Delivery

**Problem**: Channel providers (FCM, SES, Twilio) are external services that can fail. A notification written to Kafka must not be lost even if a worker crashes mid-delivery.

**Kafka consumer group semantics**: Workers are Kafka consumers. A message is only committed (offset advanced) after the notification is successfully sent to the channel provider (or recorded as permanently failed). If a worker crashes before commit, Kafka re-delivers the message to another worker in the group.

**Retry with exponential backoff**:
```
Attempt 1: immediately
Attempt 2: 30 seconds later
Attempt 3: 5 minutes later
Attempt 4: 30 minutes later
Max 4 retries → move to Dead Letter Queue (DLQ)
```

**Dead Letter Queue**: Notifications that fail all retries go to a Kafka DLQ topic. An ops dashboard monitors the DLQ. Failed notifications are analyzed for patterns (bad device tokens, invalid email addresses) and either retried manually or discarded with logging.

**Idempotent delivery**:
```
idempotency_key = f"{source_service}:{event_type}:{event_id}"
```
Before sending, check Redis: `SETNX idempotency:{key} "sent" EX 86400`. If key exists → already delivered → skip. Prevents duplicate delivery on Kafka re-delivery.

> 🎯 **Staff signal:** The senior point is that exactly-once *delivery* to a third party (FCM/SES/Twilio) is impossible — the network can fail between "provider sent it" and "we recorded it" — so you engineer at-least-once and make duplicates harmless. The mechanism is deferring the Kafka offset commit until *after* the provider confirms, which guarantees no lost notification but admits re-delivery on a crash-before-commit; a Redis `SETNX` idempotency key on `{source}:{event}:{id}` then absorbs the duplicate. Name the pairing explicitly: at-least-once transport + idempotent consumer = effectively-once, and that's the only honest design when a leg of the system is an external service you can't transact with. Knowing exactly-once is a consumer-side property, not a delivery guarantee, is the E5→E6 framing.

---

## Deep Dive 2: User Preference and Quiet Hours

**Problem**: Users have different preferences per notification type and channel. Marketing emails should respect quiet hours; security alerts should always go through.

**Preference storage**: `user_preferences` table in PostgreSQL. Cached in Redis per user (TTL = 5 minutes).

**Decision logic** (before enqueuing):
```
channels_to_send = []
for channel in requested_channels:
    if not user_pref[notification_type][channel]:
        skip  # user opted out
    if channel != 'push' and is_quiet_hours(user_id):
        if priority != 'critical':
            delay_to_after_quiet_hours()
            skip
    channels_to_send.append(channel)
```

**Quiet hours implementation**: Store `quiet_hours_start` and `quiet_hours_end` in the user's local timezone. When a notification arrives during quiet hours, instead of dropping it, schedule it to a delayed queue. Kafka consumer reads a delayed-delivery sorted set in Redis (`ZADD delayed_notifications <delivery_timestamp> <notification_id>`). A scheduler polls this sorted set every minute and moves due notifications to the main Kafka topic.

**Digest mode**: Some notification types (marketing, social) support digest mode — aggregate multiple notifications into one daily summary email. A batching worker groups notifications by user and template, waits until 8 AM in the user's timezone, and sends one digest.

> 🎯 **Staff signal:** The insight is that quiet hours turn notifications into a *scheduling* problem, and the senior move is refusing to drop or busy-wait — you park delayed notifications in a Redis sorted set scored by `delivery_timestamp` and a scheduler `ZRANGEBYSCORE`-polls only what's now due, so millions of deferred sends cost O(due) per tick, not O(all pending). The subtlety that separates senior from correct is that priority overrides the schedule: a critical security alert bypasses quiet hours while a marketing email defers — so "respect quiet hours" is a policy *parameterized by notification class*, not a global gate. Modeling deferral as a time-ordered queue with per-class override, rather than a sleep or a discard, is the E5→E6 line.

---

## Deep Dive 3: Device Token Management and Push Delivery

**Problem**: Mobile device tokens expire, rotate (app reinstall), or become invalid (uninstall). Sending to stale tokens wastes throughput and hides real delivery failures.

**Token lifecycle**:
- iOS (APNs): Token rotates on every app reinstall. APNs returns `410 Gone` for unregistered devices.
- Android (FCM): Token may be refreshed by Firebase. FCM returns `NOT_REGISTERED` for invalid tokens.

**Token storage**: `device_tokens(user_id, device_id, platform, token, updated_at)`. Index on `user_id`. One user may have multiple devices (phone + tablet + web).

**Token refresh**: Mobile app calls `PUT /api/v1/devices/{device_id}/token` on every app launch. Token refresh is cheap — just a DB upsert.

**Stale token cleanup**:
- After any push delivery attempt: if FCM/APNs returns `NOT_REGISTERED` or `410 Gone`, immediately mark `device_tokens.is_valid = false` for that token.
- Nightly job: delete tokens not refreshed in 90 days.

**Fanout to multi-device**: A user with 3 registered devices receives the push notification on all active devices. Worker queries all valid device tokens for the user and sends one FCM/APNs request per token. FCM batch send API allows up to 500 tokens per request.

> 🎯 **Staff signal:** The senior insight is that the delivery-failure signal is *also* your token-hygiene signal — a `410 Gone` / `NOT_REGISTERED` isn't just a failed send to retry, it's the provider telling you the token is dead, and treating it as such (immediately mark invalid, stop sending) is what keeps your throughput real. Naive systems keep hammering stale tokens, wasting quota and masking their true delivery rate behind phantom sends. Pair the reactive invalidation (on the failure response) with a proactive 90-day sweep for tokens that silently went dark. Recognizing that the error response is a feedback loop that keeps the token store clean — not just an exception to catch — is the E5→E6 framing.

---

## Interviewer Questions by Level

**Junior**:
- What are the different channels for sending notifications? How do mobile push notifications work?
- What is a Dead Letter Queue and when would a notification end up there?
- How do you store user notification preferences?

**Mid-level**:
- How do you guarantee at-least-once delivery without losing notifications?
- How do you handle quiet hours for non-critical notifications?
- What's the difference between FCM and APNs? How do you manage device tokens?

**Senior**:
- Design the bulk notification system for sending 10M marketing emails in 30 minutes without overwhelming SES/SendGrid.
- How do you handle a scenario where the user preference service is down — do you send notifications or drop them?
- How would you implement notification batching/digest mode to avoid over-notifying users?
- Design the analytics pipeline to track delivery rate, open rate, and click-through rate for 10M notifications/day.

---

## Back-of-Envelope Estimation

**Scale inputs (given in NFRs):**
- 1B users; 10M notifications/day average; peak 10,000/sec (flash sales); < 1s high-priority delivery

**Throughput:**
- Average: 10M/day ÷ 86,400 = **~115 notifications/sec**
- Peak: **10,000/sec** (flash sale launch, breaking news, sports score alerts)
- Each notification payload: `{notif_id, user_id, type, title, body, channel, priority, ts}` ≈ 500 bytes
- Peak write to Kafka: 10,000 × 500 bytes = **5 MB/sec** — trivial

**Fan-out by channel:**
- Push notifications (mobile): 60% of users have push enabled = 600M users; 10,000 peak × 60% = **6,000 FCM/APNs calls/sec**
- Email: 30% opt-in = 300M users; email rate: average 1M emails/day = **~11.6 emails/sec** average; peak 3,000/sec
- SMS: 10% opt-in = 100M users; SMS peak: 1,000/sec during alerts

**Push notification delivery latency:**
- FCM/APNs processing time: ~500ms average (queue + device wakeup)
- App server → Kafka → push worker → FCM/APNs → device = ~800ms total
- < 1 second budget means the internal queue wait must be < 200ms

**User preference lookup:**
- Every notification must check user's channel preferences before delivery
- 10,000 notifications/sec × 1 DB lookup/notification = 10,000 reads/sec for preferences
- User preference record: 1KB per user × 1B users = **~1 TB** stored in DB; hot preferences in Redis (DAU × 1 KB = 100M × 1 KB = **~100 GB** Redis)

**Deduplication:**
- At-least-once delivery means a retry may duplicate
- Redis dedup key: `notif:{notif_id}:{channel}` with 24h TTL
- 10,000/sec × 86,400 sec × 50 bytes/key = **~43 GB** in Redis for 24h dedup window — fits in one Redis node

**Architecture decisions driven by these numbers:**
- **Kafka priority queues (separate topics per priority tier)**: High-priority notifications (flash sale starts NOW, OTP code) must be delivered in < 1 second. Low-priority (weekly digest) can wait minutes. A single Kafka topic processes FIFO — a burst of 10K low-priority emails could block 6K urgent push notifications. Separate topics `notifications-high`, `notifications-low` with dedicated consumer groups ensure high-priority consumers are never blocked by low-priority volume.
- **Separate workers per channel (push, email, SMS)**: FCM calls, SMTP relay calls, and SMS gateway calls have different rate limits, retry semantics, and SLAs. A single multi-channel worker would have FCM's 6,000 calls/sec competing with email's 11.6 calls/sec for the same thread pool. Dedicated workers per channel can be scaled independently: scale push workers for flash sales, scale email workers for newsletter sends.
- **Redis for user preference caching**: 10,000 preference lookups/sec at < 1ms each = Redis (0.1ms per GET). DB query for preferences: 5–10ms × 10,000/sec = 100 core-seconds/sec of DB capacity just for preference reads. Redis cache (100 GB for DAU's preferences) eliminates the DB bottleneck entirely. Preferences change rarely (user settings changes are < 1/day per user) so cache TTL of 1 hour is safe.

---

## Related

**Concepts used in this design**

- [Message Brokers](../../02-building-blocks/04-coordination/01-message-brokers.md)
- [Rate Limiting](../../02-building-blocks/02-performance/02-rate-limiting.md)
- [Circuit Breaker](../../02-building-blocks/02-performance/03-circuit-breaker.md)
- [Event-Driven Architecture](../../04-advanced-topics/01-distributed-architecture/04-event-driven-architecture.md)
- [Outbox Pattern](../../09-patterns/01-data-consistency/01-outbox-pattern.md)

**Practice next**

- [WhatsApp](../02-medium/whatsapp.md)
- [Rate Limiter](../01-easy/rate-limiter.md)

Delivery guarantees here mirror the chat pipeline.

**Frameworks**: [HLD Template](../../07-interview-templates/01-frameworks/01-hld-template.md) · [Capacity Estimation](../../07-interview-templates/02-cheat-sheets/02-capacity-estimation.md) · [Trade-offs Cheat Sheet](../../07-interview-templates/02-cheat-sheets/01-trade-offs-cheat-sheet.md)
