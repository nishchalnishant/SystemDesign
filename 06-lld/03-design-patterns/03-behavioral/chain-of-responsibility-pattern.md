---
module: 06-lld
topic: Design Patterns
status: unread
tags: [06-lld, system-design, design-patterns, behavioral]
---
# Chain of Responsibility Pattern

## Intent

Pass a request along a chain of handlers. Each handler decides to process the request or forward it to the next handler in the chain.

---

## When to Use

- A request can be handled by one of several handlers, and you don't know which at compile time.
- You want to decouple request senders from receivers.
- Handlers should be configurable at runtime (add, remove, reorder).

**Real examples**: Logging levels (DEBUG → INFO → WARN → ERROR), HTTP middleware pipeline, notification fallback chain (email → SMS → push), approval workflow (manager → director → VP).

---

## What Breaks Without It

```python
class NotificationService:
    def notify(self, user_id: str, message: str, urgency: str) -> bool:
        if urgency == "LOW":
            return self._send_push(user_id, message)
        elif urgency == "MEDIUM":
            ok = self._send_push(user_id, message)
            if not ok:
                return self._send_sms(user_id, message)
        elif urgency == "HIGH":
            ok = self._send_push(user_id, message)
            if not ok:
                ok = self._send_sms(user_id, message)
            if not ok:
                return self._send_email(user_id, message)
        return False
```

Adding a new urgency level or a new channel (WhatsApp) requires editing this method. The fallback order is hardcoded and untestable in isolation.

---

## Structure

```
Handler <<abstract>>
  - next: Handler | None
  + set_next(handler): Handler
  + handle(request): Response | None   // calls next.handle() if not handled

ConcreteHandlerA(Handler)
ConcreteHandlerB(Handler)
ConcreteHandlerC(Handler)

Client configures: A → B → C
```

---

## Python Implementation

```python
from __future__ import annotations
from abc import ABC, abstractmethod

class NotificationHandler(ABC):
    def __init__(self) -> None:
        self._next: NotificationHandler | None = None

    def set_next(self, handler: NotificationHandler) -> NotificationHandler:
        self._next = handler
        return handler  # enables chaining: a.set_next(b).set_next(c)

    def handle(self, user_id: str, message: str) -> bool:
        if self._next is not None:
            return self._next.handle(user_id, message)
        return False  # nobody handled it

    @abstractmethod
    def _can_handle(self, user_id: str) -> bool: ...

    @abstractmethod
    def _send(self, user_id: str, message: str) -> bool: ...


class PushHandler(NotificationHandler):
    def handle(self, user_id: str, message: str) -> bool:
        if self._can_handle(user_id):
            return self._send(user_id, message) or super().handle(user_id, message)
        return super().handle(user_id, message)

    def _can_handle(self, user_id: str) -> bool:
        return self._has_push_token(user_id)

    def _send(self, user_id: str, message: str) -> bool:
        # call FCM
        return True

    def _has_push_token(self, user_id: str) -> bool:
        return True  # stub


class SMSHandler(NotificationHandler):
    def handle(self, user_id: str, message: str) -> bool:
        if self._can_handle(user_id):
            return self._send(user_id, message) or super().handle(user_id, message)
        return super().handle(user_id, message)

    def _can_handle(self, user_id: str) -> bool:
        return self._has_phone(user_id)

    def _send(self, user_id: str, message: str) -> bool:
        # call SMS gateway
        return True

    def _has_phone(self, user_id: str) -> bool:
        return True  # stub


class EmailHandler(NotificationHandler):
    def handle(self, user_id: str, message: str) -> bool:
        return self._send(user_id, message)

    def _can_handle(self, user_id: str) -> bool:
        return True

    def _send(self, user_id: str, message: str) -> bool:
        # call email provider
        return True
```

### Building the chain

```python
push = PushHandler()
sms = SMSHandler()
email = EmailHandler()

push.set_next(sms).set_next(email)

# Try push → SMS → email until one succeeds
push.handle("user_123", "Your order is out for delivery")
```

---

## Logger Example (Classic)

```python
from enum import IntEnum

class LogLevel(IntEnum):
    DEBUG = 0
    INFO  = 1
    WARN  = 2
    ERROR = 3

class Logger(ABC):
    def __init__(self, level: LogLevel) -> None:
        self._level = level
        self._next: Logger | None = None

    def set_next(self, logger: Logger) -> Logger:
        self._next = logger
        return logger

    def log(self, level: LogLevel, message: str) -> None:
        if level >= self._level:
            self._write(message)
        if self._next is not None:
            self._next.log(level, message)  # pass to ALL higher loggers (fan-out, not stop-first)

    @abstractmethod
    def _write(self, message: str) -> None: ...

class ConsoleLogger(Logger):
    def _write(self, message: str) -> None:
        print(f"[CONSOLE] {message}")

class FileLogger(Logger):
    def _write(self, message: str) -> None:
        print(f"[FILE] {message}")  # write to file in prod

class AlertLogger(Logger):
    def _write(self, message: str) -> None:
        print(f"[ALERT] {message}")  # send PagerDuty alert in prod
```

> **Note**: Loggers use fan-out (every matching logger processes); notification fallbacks use stop-first (stop at first success). Both are Chain of Responsibility — the difference is whether you call `super().handle()` always or only on failure.

---

## Design Patterns Comparison

| Pattern | Similarity | Difference |
|---------|-----------|------------|
| **Decorator** | Wraps handlers in sequence | Decorator always calls next; CoR can stop the chain |
| **Strategy** | Selects a handler | Strategy picks one; CoR tries each in order |
| **Observer** | Broadcasts to multiple | Observer always notifies all; CoR stops at first handler |

---

## Key Points for Interview

- **Chain is built by the client** — handlers don't know who comes next. The builder (or DI container) wires the chain.
- **Stop-first vs. fan-out**: Notification fallback = stop on first success. Logging = fan-out (all matching levels log). Clarify which variant you need.
- **Empty chain handling**: The base `handle()` returns `False` / `None` when `_next` is `None`. Clients must handle this case.
- **Used in**: Logger library (problem 14), Notification system fallback (problem 17), HTTP middleware (`django.middleware`, Spring filters).
