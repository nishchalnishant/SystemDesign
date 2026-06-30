> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** The Adapter Pattern — allows objects with incompatible interfaces to collaborate.
>
> **Key concepts:**
> - The problem: your system expects an interface (e.g., `PaymentGateway`), but the third-party library you must use has a different interface (`RazorpayClient`). You can't change either.
> - The fix: create an `Adapter` class that implements your expected interface (`PaymentGateway`), and holds a reference to the third-party object (`RazorpayClient`).
> - The mapping: inside the `Adapter`, map the methods and data types from what your system passes in, to what the third-party object expects.
> - Client usage: The client code only talks to the `Adapter` via the known interface. It is unaware of the third-party library underneath.
> - Analogy: a travel plug adapter that lets a European laptop plug into a US wall socket.
>
> **Key takeaway:** Adapter is the standard solution whenever you integrate with legacy code or third-party APIs. It protects your core business logic from being polluted by external dependencies.

---
module: 06-lld
topic: Design Patterns
subtopic: Structural
status: unread
tags: [06-lld, system-design, design-patterns]
---
# Adapter Pattern

## Question

Your `CheckoutService` calls `paymentGateway.charge(amount, currency)`. You just switched payment providers from an internal gateway to Razorpay. Razorpay's SDK exposes `razorpayClient.initiatePayment(RazorpayRequest request)`. You cannot change `CheckoutService`, and you cannot change Razorpay's SDK. How do you make them work together?

Try it before reading on.

---

## Pattern Mindmap

```
[Adapter Pattern]
├── Problem It Solves
│   ├── CheckoutService expects PaymentGateway.charge(amount, currency)
│   ├── Razorpay SDK exposes initiatePayment(RazorpayRequest)
│   └── Cannot modify CheckoutService; cannot modify Razorpay SDK
├── Core Structure
│   ├── Target interface: PaymentGateway with charge(amount, currency)
│   ├── Adaptee: RazorpayClient with initiatePayment(RazorpayRequest)
│   ├── Adapter: RazorpayAdapter implements PaymentGateway
│   │   └── charge() builds RazorpayRequest and calls razorpayClient.initiatePayment()
│   └── Client: CheckoutService uses PaymentGateway — never knows about Razorpay
├── Analogy
│   ├── Travel power adapter: your laptop plug ≠ foreign wall socket
│   ├── Adapter bridges the two standards without modifying either
│   └── You need one adapter class, not a new laptop
├── Class Adapter vs Object Adapter
│   ├── Object adapter: adapter holds reference to adaptee (composition)
│   ├── Class adapter: adapter extends adaptee (inheritance) — Java rarely used
│   └── Object adapter preferred: works with subclasses of adaptee too
├── When to Use
│   ├── Integrating third-party library with incompatible interface
│   ├── Legacy code cannot be modified but must plug into new system
│   └── You want a consistent interface across multiple providers
├── Adapter vs Facade vs Decorator
│   ├── Adapter: makes incompatible interfaces work together (interface translation)
│   ├── Facade: simplifies a complex subsystem (hides complexity)
│   └── Decorator: adds behavior while keeping same interface (wraps, extends)
├── Trade-offs
│   ├── One adapter per library — manageable
│   ├── If library interface changes, only adapter needs updating
│   └── Over-adapting (adapter chains) → complexity, prefer direct integration
└── Interview Angles
    ├── When would you use an Adapter over a Facade?
    ├── Object adapter vs class adapter — what is the difference?
    └── How does Adapter relate to the Open/Closed Principle?
```

## Problem Without the Pattern

The instinct is to modify the call site:

```python
class CheckoutService:
    # Old code: payment_gateway.charge(amount, currency)
    # New code — now we must know Razorpay's API:
    def checkout(self, order):
        req = RazorpayRequest()
        req.set_amount(order.get_amount() * 100)  # Razorpay wants paise, not rupees
        req.set_currency_code(order.get_currency().upper())
        self._razorpay_client.initiate_payment(req)
```

**What breaks**:
1. **SRP violation**: `CheckoutService` now contains Razorpay-specific translation logic (paise conversion, field mapping).
2. **Coupling**: `CheckoutService` directly imports Razorpay's SDK. Switching to Stripe means rewriting `CheckoutService` again.
3. **Untestable**: You cannot mock `razorpay_client` without testing `CheckoutService`'s Razorpay-specific translation code too.

---

## Derive the Minimal Fix

The constraint: **`CheckoutService` must call the interface it already knows; translation is someone else's problem**.

Step 1 — define (or keep) the interface `CheckoutService` expects:
```python
from abc import ABC, abstractmethod

class PaymentGateway(ABC):
    @abstractmethod
    def charge(self, amount: float, currency: str):
        pass
```

Step 2 — write an adapter that implements the expected interface but internally calls the incompatible library:
```python
class RazorpayAdapter(PaymentGateway):
    def __init__(self, client):
        self._razorpay_client = client

    def charge(self, amount: float, currency: str):
        # Translation happens here, not in CheckoutService
        req = RazorpayRequest()
        req.set_amount(int(amount * 100))  # rupees → paise
        req.set_currency_code(currency.upper())
        self._razorpay_client.initiate_payment(req)
```

Step 3 — `CheckoutService` receives `PaymentGateway` via injection; it never knows Razorpay exists:
```python
class CheckoutService:
    def __init__(self, gateway: PaymentGateway):
        self._gateway = gateway

    def checkout(self, order):
        self._gateway.charge(order.get_amount(), order.get_currency())  # unchanged

# Wiring:
service = CheckoutService(RazorpayAdapter(RazorpayClient()))
```

Switching to Stripe is now: write `StripeAdapter(PaymentGateway)`. `CheckoutService` is untouched.

---

> **Type**: Structural
> **Purpose**: Allows objects with incompatible interfaces to collaborate. Acts as a wrapper/translator between two sides.

> **Analogy**: A travel power adapter. Your US laptop plug doesn't fit a UK socket. The adapter converts the interface without changing either side — your laptop is unchanged, the wall socket is unchanged, and the adapter sits in between doing the translation.

---

## The Core Idea

You have an existing system expecting a specific interface (`Target`), and a new component that does what you need but has a different interface (`Adaptee`). Instead of modifying either side, you write an `Adapter` that wraps the Adaptee and exposes the Target interface.

**Three roles:**
- **Target**: The interface your system expects
- **Adaptee**: The existing class with a different interface
- **Adapter**: Wraps Adaptee, implements Target

---

## Problem Statement

You have a `CheckoutService` that expects a `PaymentGateway` interface (`pay(orderId, amount)`).
You want to integrate Razorpay, which has a different method: `makePayment(invoiceId, amount)`.
Modifying Razorpay's API or your core `CheckoutService` is not an option.

---

## Implementation

```python
from abc import ABC, abstractmethod

# 1. Target Interface — what your system expects
class PaymentGateway(ABC):
    @abstractmethod
    def pay(self, order_id: str, amount: float):
        pass

# 2. Existing valid implementation of Target
class PayUGateway(PaymentGateway):
    def pay(self, order_id: str, amount: float):
        print(f"Paid {amount} using PayU for Order {order_id}")

# 3. Adaptee — third-party/legacy code with incompatible interface
class RazorpayAPI:
    def make_payment(self, invoice_id: str, amount: float):
        print(f"Paid {amount} using Razorpay for Invoice {invoice_id}")

# 4. Adapter — wraps Adaptee, implements Target
class RazorpayAdapter(PaymentGateway):
    def __init__(self):
        self._api = RazorpayAPI()

    def pay(self, order_id: str, amount: float):
        # Translation: 'order_id' maps to Razorpay's 'invoice_id' concept
        self._api.make_payment(order_id, amount)

# 5. Client — only knows about PaymentGateway, unaware of Razorpay's API
def process_payment(gateway: PaymentGateway):
    gateway.pay("ORD-123", 500)

if __name__ == "__main__":
    process_payment(PayUGateway())       # Direct implementation
    process_payment(RazorpayAdapter())  # Via Adapter — client code unchanged
```

### Class Diagram

```mermaid
classDiagram
    class PaymentGateway {
        <<interface>>
        +pay(String orderId, double amount)
    }

    class PayUGateway {
        +pay(String orderId, double amount)
    }

    class RazorpayAPI {
        +makePayment(String invoiceId, double amount)
    }

    class RazorpayAdapter {
        -RazorpayAPI api
        +pay(String orderId, double amount)
    }

    class Main {
        +processPayment(PaymentGateway gateway)
        +main(String[] args)
    }

    PaymentGateway <|.. PayUGateway
    PaymentGateway <|.. RazorpayAdapter
    RazorpayAdapter o-- RazorpayAPI : adapts
    Main ..> PaymentGateway : uses
```

---

## Another Example: Legacy Logging System

```python
from abc import ABC, abstractmethod

# Your system's logging interface
class Logger(ABC):
    @abstractmethod
    def log(self, level: str, message: str):
        pass

# Third-party legacy logger with different signature
class LegacyLogger:
    def write_log(self, severity: int, msg: str):
        print(f"[{severity}] {msg}")

# Adapter bridges the two
class LegacyLoggerAdapter(Logger):
    def __init__(self):
        self._legacy = LegacyLogger()

    def log(self, level: str, message: str):
        severity = 1 if level == "ERROR" else 2 if level == "WARN" else 3
        self._legacy.write_log(severity, message)
```

---

## When to Use in Interviews

- Integrating third-party libraries that don't match your interface: "I'd wrap it in an Adapter. The Adapter implements my interface and delegates to the third-party API."
- Legacy code migration: "We can't change 10-year-old code, but we can write an Adapter that makes it compatible with the new system."
- When interviewers ask about the Strangler Fig pattern: Adapters are a key tool — wrap legacy components to make them fit the new interface progressively.

---

## Common Violations

| Violation | Symptom | Fix |
|---|---|---|
| Modifying Adaptee to fit Target | Changing third-party code | Write an Adapter instead |
| Adapter does business logic | Translation AND computation inside Adapter | Adapter should only translate, not process |
| Two-way coupling | Adapter knows details of both sides deeply | Keep Adapter thin — just translate method signatures |

---

## Adapter vs Facade

| | Adapter | Facade |
|---|---|---|
| Purpose | Make incompatible interfaces work together | Simplify a complex interface |
| Changes interface? | Yes — wraps one interface to look like another | No — just simplifies existing subsystem |
| Number of classes wrapped | Usually one | Usually many subsystem classes |

---

## When to Use

- Integrating existing classes/libraries that don't match your interface
- Legacy code migration without modifying original code
- Making unrelated classes work together

---

## Interview Tips

**Q: "Adapter vs Decorator?"**
- "Adapter changes the interface of an object. Decorator keeps the same interface but adds behavior. Use Adapter when you need translation; use Decorator when you need enhancement."

**Q: "Give a real-world Adapter example"**
- "Integrating a third-party payment SDK. Our system expects `pay(orderId, amount)` but the SDK has `makePayment(invoiceId, amount)`. The Adapter wraps the SDK and exposes our interface. Neither side changes."
