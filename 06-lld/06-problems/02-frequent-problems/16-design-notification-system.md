> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design a Notification System — a common LLD question focusing on decoupling the generation of events from the delivery of messages.
>
> **Key concepts:**
> - Core Entities: `NotificationContext`, `NotificationDispatcher`, `User`, `NotificationTemplate`.
> - Strategy Pattern (Channel): The system must send via Email, SMS, or Push. These are separate `DeliveryStrategy` classes.
> - Chain of Responsibility (Fallback/Retry): If an SMS fails, automatically try Email. Link the handlers in a chain.
> - Factory Pattern: To construct the correct notification object based on the event type (e.g., `OrderShippedEvent` generates a specific notification).
> - Abstraction: The system sending the notification (e.g., the Billing Service) should not know *how* the user receives it. It just publishes an event to a queue, and the Notification System consumes it.
>
> **Key takeaway:** The key to this problem is extensibility. When the interviewer asks "How do we add WhatsApp notifications?", you should just need to add a `WhatsAppStrategy` class without modifying core logic.

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

```java
public DeliveryResult send(String userId, Notification notification) {
    User user = userRepo.get(userId);
    if (user == null) {
        return DeliveryResult.USER_NOT_FOUND;
    }

    List<ChannelType> activeChannels = user.getPreferences().getActiveChannels();
    if (activeChannels.isEmpty()) {
        return DeliveryResult.NO_CHANNEL;
    }

    for (ChannelType channelType : activeChannels) {
        NotificationChannel channel = channels.get(channelType);
        if (channel == null) {
            continue;
        }

        String recipient = getRecipient(user, channelType);
        String content = templateEngine.render(
            notification.getTemplateId(),
            notification.getVariables()
        );

        boolean delivered = sendWithRetry(channel, recipient, content);
        if (delivered) {
            return DeliveryResult.SUCCESS;
        }
    }

    return DeliveryResult.ALL_FAILED;
}

private boolean sendWithRetry(NotificationChannel channel, String recipient, String content) {
    for (int attempt = 0; attempt < retryPolicy.getMaxRetries(); attempt++) {
        try {
            if (channel.send(recipient, content)) {
                return true;
            }
        } catch (Exception e) {
            // swallow and retry
        }
        if (attempt < retryPolicy.getMaxRetries() - 1) {
            try {
                long backoffMillis = (long) (retryPolicy.getBackoffSeconds() * Math.pow(2, attempt) * 1000);
                Thread.sleep(backoffMillis);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }
    return false;
}
```

### Template Engine

```java
public class TemplateEngine {
    private final Map<String, String> templates = new HashMap<>(); // templateId -> template string

    public void register(String templateId, String template) {
        templates.put(templateId, template);
    }

    public String render(String templateId, Map<String, Object> variables) {
        String template = templates.get(templateId);
        if (template == null) {
            throw new TemplateNotFoundError(templateId);
        }
        // Simple {{variable}} substitution
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            template = template.replace(placeholder, String.valueOf(entry.getValue()));
        }
        return template;
    }
}
```

### User preference routing

```java
public class UserPreferences {
    private final List<ChannelType> preferredChannels; // [EMAIL, SMS, PUSH]
    private final Set<ChannelType> optedOut;

    public UserPreferences(List<ChannelType> preferredChannels, Set<ChannelType> optedOut) {
        this.preferredChannels = preferredChannels;
        this.optedOut = (optedOut != null) ? optedOut : new HashSet<>();
    }

    public List<ChannelType> getActiveChannels() {
        List<ChannelType> active = new ArrayList<>();
        for (ChannelType c : preferredChannels) {
            if (!optedOut.contains(c)) {
                active.add(c);
            }
        }
        return active;
    }
}
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

```java
service.getChannels().put(ChannelType.WHATSAPP, new WhatsAppChannel(whatsappClient));
```

Users add WHATSAPP to their `preferred_channels`. No other changes. This is Open/Closed Principle — the system is open for extension (new channel) but closed for modification.

### 2. "How would you make delivery asynchronous?"

Replace the synchronous `_send_with_retry` with an async queue:

```java
public void send(String userId, Notification notification) {
    // Resolve channels synchronously (fast)
    List<ChannelType> channels = resolveChannels(userId);
    // Enqueue dispatch job (only first preferred channel; worker handles fallback)
    if (!channels.isEmpty()) {
        ChannelType channelType = channels.get(0);
        queue.publish(new DeliveryJob(channelType, userId, notification));
    }
}

public class DeliveryWorker {
    public void process(DeliveryJob job) {
        boolean delivered = sendWithRetry(job);
        if (!delivered) {
            ChannelType nextChannel = getNextChannel(job);
            if (nextChannel != null) {
                queue.publish(job.withChannel(nextChannel));
            }
        }
    }
}
```

Retry + fallback moves into the worker. The API returns immediately after enqueue.

### 3. "How would you add rate limiting per user?"

Use a sliding window counter per user:

```java
public class RateLimiter {
    private final int maxPerHour;
    private final Map<String, Deque<Long>> counters = new ConcurrentHashMap<>();

    public RateLimiter(int maxPerHour) {
        this.maxPerHour = maxPerHour;
    }

    public synchronized boolean allow(String userId) {
        long now = System.currentTimeMillis() / 1000;
        Deque<Long> window = counters.computeIfAbsent(userId, k -> new ArrayDeque<>());
        // Remove events older than 1 hour
        while (!window.isEmpty() && window.peekFirst() < now - 3600) {
            window.pollFirst();
        }
        if (window.size() >= maxPerHour) {
            return false;
        }
        window.addLast(now);
        return true;
    }
}
```

`NotificationService.send()` calls `rate_limiter.allow(user_id)` before dispatching.

### 4. "How would you track delivery status (sent, delivered, read)?"

Add a `NotificationLog` entity with status enum (QUEUED, SENT, DELIVERED, READ). Each channel's `send()` returns a delivery receipt ID. A webhook from the channel provider (e.g., Twilio for SMS) updates status.

```java
public class NotificationLog {
    private String id;
    private String userId;
    private ChannelType channel;
    private DeliveryStatus status;
    private Instant sentAt;
    private Instant deliveredAt; // nullable
    private Instant readAt;      // nullable
}
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

---

## Related

**Patterns applied here**

- [Factory Pattern](../../03-design-patterns/01-creational/factory-pattern.md)
- [Chain of Responsibility Pattern](../../03-design-patterns/03-behavioral/chain-of-responsibility.md)
- [Observer Pattern](../../03-design-patterns/03-behavioral/observer-pattern.md)
- [Strategy Pattern](../../03-design-patterns/03-behavioral/strategy-pattern.md)

**SOLID focus**: [Open/Closed](../../02-solid-principles/02-open-closed.md) · [Dependency Inversion](../../02-solid-principles/05-dependency-inversion.md)

**Concurrency**: [Producer-Consumer](../../04-concurrency/producer-consumer.md) · [Futures & Async Patterns](../../04-concurrency/futures-async-patterns.md)

**Practice next**

- [Design Pub/Sub](../03-domain-specific/23-design-pub-sub.md)
- [Design Rate Limiter](../01-core-problems/02-design-rate-limiter.md)

Pub-sub is the delivery backbone; rate limiting caps fan-out.

**Frameworks**: [LLD Template](../../../07-interview-templates/01-frameworks/02-lld-template.md) · [UML Diagrams](../../01-oop-fundamentals/uml-diagrams.md) · [OOP Four Pillars](../../01-oop-fundamentals/four-pillars.md)
