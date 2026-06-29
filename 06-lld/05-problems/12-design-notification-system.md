---
module: 06-lld
topic: Problems
status: unread
tags: [06-lld, system-design, problems]
---
# Design Notification System

> **Difficulty**: Medium
> **Topics**: Observer, Strategy, Chain of Responsibility, Template Method
> **Key Concepts**: Multi-channel delivery, user preferences, retry/fallback, rate limiting.

---

## What Breaks Without This Design?

```python
class NotificationService:
    def send_order_update(self, user_id, message):
        if self._user_has_email(user_id):
            self._send_email(user_id, message)
        if self._user_has_phone(user_id):
            self._send_sms(user_id, message)
        if self._user_has_fcm_token(user_id):
            self._send_push(user_id, message)
```

**Concrete failures**:
1. **Adding a new channel (WhatsApp, Slack) requires editing `sendOrderUpdate`**: And every other notification method in the system.
2. **No user preference respect**: User opted out of SMS for marketing but this sends SMS unconditionally.
3. **No retry on failure**: `sendEmail()` throws — the exception propagates, SMS and push are never attempted.
4. **No rate limiting**: A burst of 10,000 order updates sends 10,000 × 3 channel calls simultaneously — external providers get hammered.
5. **Notification types mixed with delivery logic**: "order update" payload construction is entangled with "how to send."

---

## Derive the Class Structure

**Force 1 — Channel delivery must be swappable and extendable**: Extract `NotificationChannel` interface. `EmailChannel`, `SMSChannel`, `PushChannel`, `WhatsAppChannel` implement it independently.

**Force 2 — User has per-channel, per-type preferences**: Extract `UserNotificationPreference` (channel enabled/disabled per `NotificationType`).

**Force 3 — Failed channel should retry / fallback**: Wrap each `NotificationChannel` in a `RetryDecorator`. Define `FallbackChain`: try primary channel; on failure try secondary.

**Force 4 — Notification has a type-specific template**: Extract `NotificationTemplate` (subject + body per `NotificationType`). Decouple message construction from delivery.

```
God class → Notification (what to send: type, content, target user)
          → NotificationChannel (how to send: email / SMS / push)
          → NotificationRouter (which channels to use for this user+type)
          → UserPreferenceService (user's channel opt-ins)
          → NotificationTemplate (message construction)
          → RetryDecorator (wraps channel with retry)
          → NotificationService (orchestration entry point)
```

---

## Phase 1: Requirements

**Actors**: System events (trigger), User (recipient), External providers (Email/SMS/Push gateways).

**Must-have**:
- Send notifications across multiple channels (email, SMS, push)
- Respect user's channel preferences per notification type
- Retry failed deliveries (configurable attempts + backoff)
- Rate limit per user to avoid spam
- Support multiple notification types (ORDER_UPDATE, PROMO, SECURITY_ALERT)

**Constraints**:
- `SECURITY_ALERT` should always be sent regardless of preferences
- Rate limit: max 5 promo notifications per user per hour

---

## Phase 2: Class Diagram

```
Notification
  - notificationId: String
  - type: NotificationType     // ORDER_UPDATE, PROMO, SECURITY_ALERT
  - userId: String
  - templateData: Map<String,String>  // e.g. {"orderId": "123", "status": "DELIVERED"}
  - createdAt: Instant

NotificationType (enum)
  ORDER_UPDATE, PROMO, OTP, SECURITY_ALERT

NotificationChannel <<interface>>
  + send(Notification): DeliveryResult
  + getChannelType(): ChannelType

EmailChannel implements NotificationChannel
SMSChannel implements NotificationChannel
PushChannel implements NotificationChannel

RetryDecorator implements NotificationChannel
  - delegate: NotificationChannel
  - maxAttempts: int
  - backoffMs: long
  + send(Notification): DeliveryResult  // retries delegate on failure

UserPreferenceService
  + getEnabledChannels(userId, NotificationType): List<ChannelType>

NotificationTemplate <<interface>>
  + render(NotificationType, Map<String,String>): RenderedMessage

RateLimiter
  + isAllowed(userId, NotificationType): boolean

NotificationRouter
  - channels: Map<ChannelType, NotificationChannel>
  - preferenceService: UserPreferenceService
  - rateLimiter: RateLimiter
  + route(Notification): List<DeliveryResult>

NotificationService
  + send(Notification): void
```

---

## Phase 3: Design Patterns

| Pattern | Where | Why |
|---------|-------|-----|
| **Strategy** | `NotificationChannel` | Swap/add channels without touching routing logic |
| **Decorator** | `RetryDecorator` | Add retry behavior around any channel transparently |
| **Template Method** | `NotificationTemplate` | Define rendering skeleton; subclass per type fills in fields |
| **Observer** | System events → `NotificationService` | Decouple event producers from notification consumers |
| **Chain of Responsibility** | Fallback chain | Try email → fallback to SMS → fallback to push |

---

## Phase 4: Key Implementation

### Channel Interface + Retry Decorator

```python
import time
from abc import ABC, abstractmethod

class NotificationChannel(ABC):
    @abstractmethod
    def send(self, notification): ...

    @abstractmethod
    def get_channel_type(self): ...

class RetryDecorator(NotificationChannel):
    def __init__(self, delegate, max_attempts, backoff_ms):
        self._delegate = delegate
        self._max_attempts = max_attempts
        self._backoff_ms = backoff_ms

    def send(self, notification):
        for attempt in range(1, self._max_attempts + 1):
            try:
                return self._delegate.send(notification)
            except Exception as e:
                if attempt == self._max_attempts:
                    return DeliveryResult.failure(str(e))
                time.sleep(self._backoff_ms * attempt / 1000)  # linear backoff; use exponential for prod
        return DeliveryResult.failure("Max retries exceeded")

    def get_channel_type(self):
        return self._delegate.get_channel_type()
```

### Router with Preference + Rate Limiting

```python
import logging

log = logging.getLogger(__name__)

class NotificationRouter:
    def __init__(
        self,
        channels,
        preference_service,
        rate_limiter,
    ):
        self._channels = channels
        self._preference_service = preference_service
        self._rate_limiter = rate_limiter

    def route(self, notification):
        # SECURITY_ALERT bypasses preferences and rate limits
        if notification.type == NotificationType.SECURITY_ALERT:
            target_channels = [ChannelType.EMAIL, ChannelType.SMS, ChannelType.PUSH]
        else:
            target_channels = self._preference_service.get_enabled_channels(
                notification.user_id, notification.type
            )

        if not self._rate_limiter.is_allowed(notification.user_id, notification.type):
            log.warning("Rate limit hit for user %s type %s", notification.user_id, notification.type)
            return [DeliveryResult.rate_limited()]

        results = []
        for ct in target_channels:
            channel = self._channels.get(ct)
            if channel is not None:
                results.append(channel.send(notification))
        return results
```

### Token Bucket Rate Limiter (per-user per-type)

```python
import datetime

class InMemoryRateLimiter(RateLimiter):
    _LIMITS = {  # dict[NotificationType, RateLimit]
        NotificationType.PROMO:        RateLimit(max_tokens=5,   refill_period=datetime.timedelta(hours=1)),
        NotificationType.ORDER_UPDATE: RateLimit(max_tokens=100, refill_period=datetime.timedelta(hours=1)),
    }

    def __init__(self):
        self._buckets = {}  # dict[str, TokenBucket]

    def is_allowed(self, user_id, notification_type):
        limit = self._LIMITS.get(notification_type)
        if limit is None:
            return True  # no limit for this type
        key = f"{user_id}:{notification_type}"
        if key not in self._buckets:
            self._buckets[key] = TokenBucket(limit.max_tokens, limit.refill_period)
        return self._buckets[key].try_consume()
```

### Notification Service Entry Point

```python
import uuid
import datetime

class NotificationService:
    def __init__(self, router, template_engine):
        self._router = router
        self._template_engine = template_engine

    def send(self, user_id, notification_type, data):
        msg = self._template_engine.render(notification_type, data)
        notification = Notification(
            notification_id=str(uuid.uuid4()),
            type=notification_type,
            user_id=user_id,
            rendered_message=msg,
            created_at=datetime.datetime.now(),
        )
        results = self._router.route(notification)
        self._audit_log(notification, results)
```

---

## Interview Tips

- **Start with the channel interface**: The entire design extends naturally from `NotificationChannel` + `DeliveryResult`.
- **Decorator for retry vs. inheritance**: Explain why decorator (RetryDecorator wraps any channel) is better than `EmailChannelWithRetry extends EmailChannel` — the latter requires N × M subclasses for N channels × M behaviors.
- **SECURITY_ALERT bypass**: Interviewers often ask "what if user opted out of all channels?" — security alerts must always go through.
- **Rate limiting scope**: Per-user per-type is the right granularity. Global rate limiting is too coarse; per-user-only doesn't prevent promo spam from one campaign.
- **Async follow-up**: For scale, `NotificationService.send()` enqueues to a queue (SQS/Kafka) and workers process asynchronously. The LLD design above is the per-message processing logic inside each worker.

---

## Interviewer Follow-Up Questions

- "What's your class hierarchy for different notification channels?" → `NotificationChannel` interface with `send(notification: Notification) → Result`. Implementations: `EmailChannel`, `SMSChannel`, `PushChannel`, `SlackChannel`. `NotificationService` holds a registry of channels. On `send(notification, channels=[EMAIL, PUSH])`: delegate to each requested channel. Adding a new channel = implement the interface and register — no changes to `NotificationService`. This is OCP applied to channel extensibility.
- "How do you retry failed notifications without blocking the main flow?" → Async with retries: put the notification on a queue (Redis/SQS). A worker dequeues and attempts delivery. On failure: exponential backoff retry (30s, 60s, 120s). After N failures: move to DLQ for manual inspection. The main application flow completes immediately after enqueueing — no retry logic in the request path. Retry state is tracked in the queue infrastructure, not in the application code.
- "A user has both push notification and SMS enabled. Push fails (device offline). Do you send SMS?" → Define a fallback chain per channel group: `[PUSH, SMS]` in priority order. On push failure (non-retryable: device unregistered): try SMS. On push failure (retryable: network timeout): retry push first, then SMS after N retries. The `NotificationService` implements a `FallbackChain` pattern: try channels in order, stop on first success. Whether to fall back is a notification-type policy: transactional notifications (OTP, order confirmation) should always fall back; marketing notifications may not (cost concern).
- "How do you prevent sending duplicate notifications when a worker retries after a timeout?" → Idempotency: each notification gets a UUID at creation time. Before sending: `SET notification_sent:{uuid} 1 NX EX 86400` in Redis. If the key already exists: skip (already sent). After successful send: the key is already set. On retry: same UUID → key exists → skip. This ensures at-most-once delivery even when the queue delivers at-least-once. Critical: the UUID must be generated before enqueueing, not on each worker attempt.
