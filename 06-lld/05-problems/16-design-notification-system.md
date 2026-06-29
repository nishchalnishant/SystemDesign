---
module: 06-lld
topic: Problems
status: unread
tags: [06-lld, lld, notification-system, observer, strategy, chain-of-responsibility]
---
# Design Notification System

> **Difficulty**: Medium
> **Asked at**: Amazon, Uber, LinkedIn
> **Key Patterns**: Observer, Strategy (channel), Chain of Responsibility (retry/fallback)

---

## Understanding the Problem

Design a notification system that can send alerts to users via multiple channels (Email, SMS, Push), with preference-based routing and retry on failure.

---

## Clarifying Questions

**You**: "What channels do we support — Email, SMS, Push?"
**Interviewer**: "Yes, those three."

**You**: "Can a user opt out of specific channels?"
**Interviewer**: "Yes, each user has per-channel preferences."

**You**: "Do we need retry on failure?"
**Interviewer**: "Yes — retry up to 3 times, then fall back to next preferred channel."

**You**: "Is this synchronous or async delivery?"
**Interviewer**: "Discuss both, implement synchronous for now."

**You**: "Do we need notification templates?"
**Interviewer**: "Simple templates with variable substitution."

---

## Final Requirements

**In scope:**
1. Send notifications via Email, SMS, Push
2. Route by user's channel preferences and opt-outs
3. Retry up to 3 times on failure; fall back to next channel
4. Simple template engine for notification content
5. Pluggable new channels without modifying existing code

**Out of scope:**
- Async queuing (follow-up)
- Read receipts / delivery confirmation tracking
- Rate limiting per user (follow-up)
- Notification history

---

## Core Entities and Relationships

| Entity | Responsibility |
|--------|---------------|
| `NotificationService` | Entry point; resolves channels, dispatches notifications |
| `NotificationChannel` (abstract) | Strategy: `send(recipient, message) -> bool` |
| `EmailChannel / SMSChannel / PushChannel` | Concrete channel implementations |
| `User` | Holds contact info (email, phone, device_token) + preferences |
| `UserPreferences` | Ordered list of preferred channels + opt-outs |
| `Notification` | Content: title, body, template vars, type |
| `TemplateEngine` | Renders template + variables into final message |
| `RetryPolicy` | Max retries, backoff |

`NotificationService` iterates user's preferred channels in order, attempts delivery with retry, falls back to next channel on exhausted retries.

---

## Class Design

### NotificationChannel (Strategy)

| Requirement | What Channel must implement |
|-------------|----------------------------|
| "Pluggable new channels" | abstract `send(recipient, content) -> bool` |
| "Per-channel failure" | returns False on failure, True on success |

```
class NotificationChannel (abstract):
+ send(recipient: str, content: str) -> bool
+ get_channel_type() -> ChannelType

class EmailChannel(NotificationChannel):
- smtp_client: SMTPClient
+ send(email: str, content: str) -> bool

class SMSChannel(NotificationChannel):
- sms_gateway: SMSGateway
+ send(phone: str, content: str) -> bool

class PushChannel(NotificationChannel):
- push_service: PushService
+ send(device_token: str, content: str) -> bool
```

### UserPreferences

```
class UserPreferences:
- preferred_channels: list[ChannelType]  # ordered
- opted_out: set[ChannelType]

+ get_active_channels() -> list[ChannelType]
+ is_opted_out(channel: ChannelType) -> bool
```

### NotificationService

```
class NotificationService:
- channels: dict[ChannelType, NotificationChannel]
- user_repo: UserRepository
- template_engine: TemplateEngine
- retry_policy: RetryPolicy

+ send(user_id: str, notification: Notification) -> DeliveryResult
```

---

## Implementation

### Core Method: `send`

**Core logic:**
1. Fetch user and their preferences
2. Get ordered list of active (non-opted-out) channels
3. For each channel, render content and attempt delivery with retry
4. Stop on first successful delivery; log failure if all channels exhausted

**Edge cases:**
- User has no active channels → DeliveryResult.NO_CHANNEL
- All channels fail → DeliveryResult.ALL_FAILED
- Template rendering failure → skip sending, return error

```python
def send(self, user_id, notification):
    user = self.user_repo.get(user_id)
    if not user:
        return DeliveryResult.USER_NOT_FOUND

    active_channels = user.preferences.get_active_channels()
    if not active_channels:
        return DeliveryResult.NO_CHANNEL

    for channel_type in active_channels:
        channel = self.channels.get(channel_type)
        if not channel:
            continue

        recipient = self._get_recipient(user, channel_type)
        content = self.template_engine.render(
            notification.template_id,
            notification.variables
        )

        delivered = self._send_with_retry(channel, recipient, content)
        if delivered:
            return DeliveryResult.SUCCESS

    return DeliveryResult.ALL_FAILED

def _send_with_retry(self, channel, recipient, content):
    for attempt in range(self.retry_policy.max_retries):
        try:
            if channel.send(recipient, content):
                return True
        except Exception:
            pass
        if attempt < self.retry_policy.max_retries - 1:
            time.sleep(self.retry_policy.backoff_seconds * (2 ** attempt))
    return False
```

### Template Engine

```python
class TemplateEngine:
    def __init__(self):
        self.templates = {}  # template_id → template string

    def register(self, template_id, template):
        self.templates[template_id] = template

    def render(self, template_id, variables):
        template = self.templates.get(template_id)
        if not template:
            raise TemplateNotFoundError(template_id)
        # Simple {{variable}} substitution
        for key, value in variables.items():
            template = template.replace(f"{{{{{key}}}}}", str(value))
        return template
```

### User preference routing

```python
class UserPreferences:
    def __init__(self, preferred_channels, opted_out=None):
        self.preferred_channels = preferred_channels  # [EMAIL, SMS, PUSH]
        self.opted_out = opted_out or set()

    def get_active_channels(self):
        return [c for c in self.preferred_channels if c not in self.opted_out]
```

---

## Verification

```
User: Alice
  preferred_channels = [EMAIL, SMS, PUSH]
  opted_out = {SMS}
  email = "alice@example.com"
  device_token = "tok123"

Notification: template_id="ORDER_CONFIRM", variables={"order_id": "ORD-99"}
Template: "Your order {{order_id}} has been confirmed."

send("alice", notification):
  active_channels = [EMAIL, PUSH]  # SMS filtered out

  Channel 1: EMAIL
    recipient = "alice@example.com"
    content = "Your order ORD-99 has been confirmed."
    _send_with_retry(EmailChannel, "alice@example.com", content):
      attempt 0: EmailChannel.send() → True ✓
  return DeliveryResult.SUCCESS
```

---

## Deep Dive & Extensibility

### 1. "How would you add a new channel (e.g., WhatsApp) without touching existing code?"

Create `WhatsAppChannel(NotificationChannel)` implementing `send()`. Register it in `NotificationService.channels`:

```python
service.channels[ChannelType.WHATSAPP] = WhatsAppChannel(whatsapp_client)
```

Users add WHATSAPP to their `preferred_channels`. No other changes. This is Open/Closed Principle — the system is open for extension (new channel) but closed for modification.

### 2. "How would you make delivery asynchronous?"

Replace the synchronous `_send_with_retry` with an async queue:

```python
def send(self, user_id, notification):
    # Resolve channels synchronously (fast)
    channels = self._resolve_channels(user_id)
    # Enqueue dispatch job
    for channel_type in channels:
        self.queue.publish(DeliveryJob(
            channel_type=channel_type,
            user_id=user_id,
            notification=notification
        ))
        break  # enqueue only first preferred channel; worker handles fallback

class DeliveryWorker:
    def process(self, job):
        delivered = self._send_with_retry(job)
        if not delivered:
            next_channel = self._get_next_channel(job)
            if next_channel:
                self.queue.publish(job.with_channel(next_channel))
```

Retry + fallback moves into the worker. The API returns immediately after enqueue.

### 3. "How would you add rate limiting per user?"

Use a sliding window counter per user:

```python
class RateLimiter:
    def __init__(self, max_per_hour):
        self.max_per_hour = max_per_hour
        self.counters = defaultdict(lambda: deque())

    def allow(self, user_id):
        now = time.time()
        window = self.counters[user_id]
        # Remove events older than 1 hour
        while window and window[0] < now - 3600:
            window.popleft()
        if len(window) >= self.max_per_hour:
            return False
        window.append(now)
        return True
```

`NotificationService.send()` calls `rate_limiter.allow(user_id)` before dispatching.

### 4. "How would you track delivery status (sent, delivered, read)?"

Add a `NotificationLog` entity with status enum (QUEUED, SENT, DELIVERED, READ). Each channel's `send()` returns a delivery receipt ID. A webhook from the channel provider (e.g., Twilio for SMS) updates status.

```python
class NotificationLog:
    - id: str
    - user_id: str
    - channel: ChannelType
    - status: DeliveryStatus
    - sent_at: datetime
    - delivered_at: Optional[datetime]
    - read_at: Optional[datetime]
```

---

## Interviewer Questions by Level

**Junior**: Three channel classes, NotificationService that calls them. Route by user preference. Return success/failure.

**Mid-level**: Strategy for channels. Retry with exponential backoff. Template engine with variable substitution. Fallback to next channel after retry exhausted.

**Senior**: Async queue for non-blocking delivery. Rate limiting per user. Delivery tracking with webhook callbacks. Proactively discuss how to add new channels without code changes.

---

## Common Interview Questions

- **Q**: Why use Strategy for channels instead of a switch statement?
  **A**: New channels require no modification to existing code — only a new class and registration. A switch statement grows with every new channel and violates Open/Closed.

- **Q**: What is the fallback order — how do you decide?
  **A**: User's `preferred_channels` list defines order. After retry exhaustion on channel N, fall back to channel N+1. The user controls their preference order (e.g., prefer push first, SMS as backup).

- **Q**: How do you handle a channel that's permanently down?
  **A**: Circuit breaker — track failure rate per channel. If failure rate exceeds threshold, mark the channel as OPEN (skip it). Retry after a recovery window.

- **Q**: Synchronous vs asynchronous — when to use each?
  **A**: Synchronous: simple, debuggable, for low-volume or time-sensitive notifications. Async: high throughput, non-blocking API, retry without holding request threads. Default to async for production notification systems.

- **Q**: How does template rendering work and what are the security risks?
  **A**: Simple string replace is safe. Risks arise if the template or variables come from untrusted user input (injection via `{{}}` substitution). Fix: escape variable values and disallow arbitrary template syntax.
