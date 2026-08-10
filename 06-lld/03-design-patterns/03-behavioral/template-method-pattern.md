> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** The Template Method Pattern — defines the skeleton of an algorithm in the superclass but lets subclasses override specific steps of the algorithm without changing its structure.
>
> **Key concepts:**
> - The problem: multiple classes have identical overall workflows, but the implementation of specific steps differs. (e.g., `DataMiner` for PDF vs CSV — open file, extract data, parse data, close file).
> - The fix: create an abstract base class.
> - The Template Method: a `final` method (e.g., `mineData()`) that dictates the exact sequence of steps.
> - The Steps: some steps are implemented in the base class (shared code). Other steps are declared `abstract` (forcing subclasses to implement them).
> - Hooks: optional steps with empty default implementations that subclasses *can* override if needed.
> - Difference from Strategy: Strategy uses composition (delegates the whole algorithm). Template uses inheritance (base class controls the algorithm, subclass fills in the blanks).
>
> **Key takeaway:** Template Method is the foundation of almost all object-oriented frameworks (like Spring or React lifecycle methods), where the framework dictates the flow, and you just fill in the specific step implementations.

---
module: 06-lld
topic: Design Patterns
subtopic: Behavioral
status: unread
tags: [06-lld, system-design, design-patterns]
---
# Template Method Pattern

> 🔵 **Java idiom:** An `abstract` class defines the invariant algorithm skeleton in a **`final` method** (so subclasses can't override the sequence) that calls `abstract` primitive steps plus optional `protected` **hooks** subclasses fill in. **JDK equivalent:** `AbstractList`/`AbstractMap` (you implement `get`/`size`, get the rest free), `java.io.InputStream.read(byte[])` calling `read()`, `HttpServlet.service()` dispatching to `doGet`/`doPost`, Spring's `JdbcTemplate`. **Interview gotcha:** it's the **Hollywood Principle** — "don't call us, we'll call you" (framework owns control flow, calls your overrides). Contrast with Strategy: Template Method varies steps via *inheritance* at compile time (one algorithm, pluggable steps); Strategy varies the *whole* algorithm via *composition* at runtime. Keep the template method `final`.

## Question

You are building an order processing system. Every order goes through: validate → calculate price → notify customer. Email orders validate differently from SMS orders. The notification medium differs too. But the sequence never changes. Write `processOrder()` for both `EmailOrder` and `SMSOrder`.

Try it before reading on.

---

## Pattern Mindmap

```
[Template Method Pattern]
├── Core Concept
│   ├── What → Define the skeleton of an algorithm in a base class; subclasses fill in steps
│   └── Why → Enforces a fixed sequence while allowing step-level variation without code duplication
├── Key Components
│   ├── Abstract base class → processOrder() calls validate(), calculatePrice(), notify() in order
│   ├── Template method → final; cannot be overridden — sequence is locked
│   ├── Abstract steps → validate(), notify() — must be overridden by subclass
│   └── Hook methods → optional; base class provides default, subclass may override
├── When to Use
│   ├── ✓ Multiple classes share the same algorithm sequence but differ in steps
│   ├── ✓ Prevent subclasses from changing the overall flow (mark template method final)
│   └── ✓ Frameworks/libraries where callers extend and fill in hooks
├── When NOT to Use
│   ├── ✗ Steps vary so much that the shared skeleton is meaningless — use Strategy
│   └── ✗ Composition preferred over inheritance — Strategy + delegation is more flexible
├── Trade-offs
│   ├── Pro: Eliminates duplication of algorithm skeleton; sequence enforced centrally
│   └── Con: Inheritance coupling — subclass tied to base class; hard to change template later
├── Real-World Examples
│   ├── Java Collections.sort() → algorithm fixed; compareTo() is the customizable step
│   └── Spring AbstractController → handleRequest() is the template; handleRequestInternal() is the hook
└── Interview Angles
    ├── vs Strategy → Strategy changes the whole algorithm at runtime; Template fixes sequence, varies steps
    ├── Hollywood Principle → "Don't call us, we'll call you" — base class calls subclass methods
    └── Code challenge: implement data parser with fixed parse() → open → extract → close steps
```

---

## Problem Without the Pattern

```java
class EmailOrderProcessor {
    void processOrder(Order o) {
        // Step 1: validate
        if (o.getEmail() == null) {
            throw new IllegalArgumentException("No email");
        }
        // Step 2: calculate
        double price = o.getBasePrice() * 1.1;  // email surcharge
        // Step 3: notify
        emailService.send(o.getEmail(), "Your order: $" + price);
    }
}


class SMSOrderProcessor {
    void processOrder(Order o) {
        // Step 1: validate
        if (o.getPhone() == null) {
            throw new IllegalArgumentException("No phone");
        }
        // Step 2: calculate
        double price = o.getBasePrice();  // no surcharge
        // Step 3: notify
        smsService.send(o.getPhone(), "Order: $" + price);
    }
}
```

**What breaks**:
1. **Duplicated structure**: Both classes have an identical three-step sequence — validate → calculate → notify. The order is repeated, not shared.
2. **SRP violation**: If the sequence gains a 4th step (e.g., log audit trail), it must be added to every processor class.
3. **Risk of divergence**: A developer adds "log audit" to `EmailOrderProcessor` but forgets `SMSOrderProcessor`. Silent inconsistency.

---

## Derive the Minimal Fix

The constraint: **the algorithm's sequence lives in one place; only the varying steps are overridable**.

Step 1 — move the sequence into a base class as a `final` method (the "template"):
```java
abstract class OrderProcessor {
    // Template method — sequence is fixed
    final void processOrder(Order o) {
        validate(o);
        double price = calculatePrice(o);
        notify(o, price);
    }

    abstract void validate(Order o);
    abstract double calculatePrice(Order o);
    abstract void notify(Order o, double price);
}
```

Step 2 — subclasses only provide the varying steps:
```java
class EmailOrderProcessor extends OrderProcessor {
    void validate(Order o) {
        if (o.getEmail() == null) {
            throw new IllegalArgumentException("No email");
        }
    }

    double calculatePrice(Order o) {
        return o.getBasePrice() * 1.1;
    }

    void notify(Order o, double price) {
        emailService.send(o.getEmail(), "Your order: $" + price);
    }
}
```

Adding a new channel (`PushNotification`) is one new subclass — the sequence in `processOrder()` is untouched.

---

> **Category**: Behavioral Pattern
> **Purpose**: Define the skeleton of an algorithm in the base class, deferring some steps to subclasses. Subclasses can override specific steps without changing the algorithm's structure.

## Real-Life Analogy

**A recipe.**

When you bake a cake, the overall process is fixed:
1. Preheat oven
2. Mix ingredients
3. Bake
4. Cool and plate

These steps always happen in this order — that's the **template**. But the *details* vary:
- Chocolate cake: mix cocoa powder and dark sugar.
- Vanilla cake: mix vanilla extract and white sugar.
- Red velvet: mix red dye, cocoa, and buttermilk.

The recipe (base class) owns the sequence. The specific ingredient choices (abstract methods) are filled in by each cake type (subclass). You can't reorder the steps — you can't plate before baking — but you can customize what happens *within* each step.

**Without Template Method**: `ChocolateCake` and `VanillaCake` each repeat the full baking flow (preheat, mix, bake, cool) with tiny differences — massive code duplication. Any change to the common logic (e.g., change oven temperature) requires modifying every class.

---

## Formal Definition

The Template Method Pattern provides a blueprint for executing an algorithm. It allows subclasses to override specific steps of the algorithm, but the overall structure remains the same. The invariant parts are never changed; only the variant parts are customizable.

**Four Key Steps**:

| Step | Type | Description |
|---|---|---|
| **Template Method** | Non-overridable in base class | Defines the skeleton. Calls other steps in order. Cannot be overridden. |
| **Primitive Operations** | `abstract` | Variable steps that subclasses MUST implement. |
| **Concrete Operations** | Regular methods in base class | Shared behavior. Subclasses inherit unchanged. |
| **Hooks** | Optional methods with default behavior | Subclasses CAN override, but don't have to. |

---

## Understanding the Problem

Without Template Method, common logic is duplicated across classes:

```java
// EmailNotification handles sending emails
class EmailNotification {
    void send(String to, String message) {
        System.out.println("Checking rate limits for: " + to);
        System.out.println("Validating email recipient: " + to);
        String formatted = message.strip();
        System.out.println("Logging before send: " + formatted + " to " + to);

        // Compose Email
        String composedMessage = "<html><body><p>" + formatted + "</p></body></html>";

        // Send Email
        System.out.println("Sending EMAIL to " + to + " with content:\n" + composedMessage);

        // Analytics
        System.out.println("Analytics updated for: " + to);
    }
}


// SMSNotification handles sending SMS messages
class SMSNotification {
    void send(String to, String message) {
        System.out.println("Checking rate limits for: " + to);
        System.out.println("Validating phone number: " + to);
        String formatted = message.strip();
        System.out.println("Logging before send: " + formatted + " to " + to);

        // Compose SMS
        String composedMessage = "[SMS] " + formatted;

        // Send SMS
        System.out.println("Sending SMS to " + to + " with message: " + composedMessage);

        // Analytics (custom)
        System.out.println("Custom SMS analytics for: " + to);
    }
}


// Main function to test sending notifications
public class Main {
    public static void main(String[] args) {
        EmailNotification emailNotification = new EmailNotification();
        SMSNotification smsNotification = new SMSNotification();

        emailNotification.send("example@example.com", "Your order has been placed!");

        System.out.println();

        smsNotification.send("1234567890", "Your OTP is 1234.");
    }
}
```

**Issues**:
- **Code Duplication**: Rate limit check, formatting, logging, and analytics are copied verbatim across both classes. Violates DRY.
- **Hardcoded Behavior**: Adding a PushNotification requires duplicating the entire `send()` skeleton again.
- **Maintenance Overhead**: Changing the rate limit logic requires updating every notification class.
- **Lack of Extensibility**: New notification types are expensive to add and risky to maintain.

---

## Solution: Template Method Pattern

```java
// Abstract class defining the template method and common steps
abstract class NotificationSender {
    // Template method (final — not meant to be overridden)
    final void send(String to, String rawMessage) {
        // Common Logic
        rateLimitCheck(to);
        validateRecipient(to);
        String formatted = formatMessage(rawMessage);
        preSendAuditLog(to, formatted);

        // Specific Logic: defined by subclasses
        String composedMessage = composeMessage(formatted);
        sendMessage(to, composedMessage);

        // Optional Hook
        postSendAnalytics(to);
    }

    // Common step 1: Check rate limits
    void rateLimitCheck(String to) {
        System.out.println("Checking rate limits for: " + to);
    }

    // Common step 2: Validate recipient
    void validateRecipient(String to) {
        System.out.println("Validating recipient: " + to);
    }

    // Common step 3: Format the message (can be customized)
    String formatMessage(String message) {
        return message.strip();  // Trim spaces
    }

    // Common step 4: Pre-send audit log
    void preSendAuditLog(String to, String formatted) {
        System.out.println("Logging before send: " + formatted + " to " + to);
    }

    // Abstract: Subclasses must implement custom message composition
    abstract String composeMessage(String formattedMessage);

    // Abstract: Subclasses must implement custom message sending
    abstract void sendMessage(String to, String message);

    // Optional hook for analytics (can be overridden)
    void postSendAnalytics(String to) {
        System.out.println("Analytics updated for: " + to);
    }
}


// Concrete class for email notifications
class EmailNotification extends NotificationSender {
    // Implement message composition for email
    String composeMessage(String formattedMessage) {
        return "<html><body><p>" + formattedMessage + "</p></body></html>";
    }

    // Implement email sending logic
    void sendMessage(String to, String message) {
        System.out.println("Sending EMAIL to " + to + " with content:\n" + message);
    }
}


// Concrete class for SMS notifications
class SMSNotification extends NotificationSender {
    // Implement message composition for SMS
    String composeMessage(String formattedMessage) {
        return "[SMS] " + formattedMessage;
    }

    // Implement SMS sending logic
    void sendMessage(String to, String message) {
        System.out.println("Sending SMS to " + to + " with message: " + message);
    }

    // Override optional hook for custom SMS analytics
    @Override
    void postSendAnalytics(String to) {
        System.out.println("Custom SMS analytics for: " + to);
    }
}


// Client code
public class Main {
    public static void main(String[] args) {
        EmailNotification emailSender = new EmailNotification();
        emailSender.send("john@example.com", "Welcome to TUF+!");

        System.out.println();

        SMSNotification smsSender = new SMSNotification();
        smsSender.send("9876543210", "Your OTP is 4567.");
    }
}
```

### Class Diagram

```mermaid
classDiagram
    class NotificationSender {
        <<abstract>>
        +send(String to, String rawMessage)
        #rateLimitCheck(String to)
        #validateRecipient(String to)
        #formatMessage(String message) String
        #preSendAuditLog(String to, String formatted)
        #composeMessage(String formattedMessage)* String
        #sendMessage(String to, String message)*
        #postSendAnalytics(String to)
    }

    class EmailNotification {
        #composeMessage(String formattedMessage) String
        #sendMessage(String to, String message)
    }

    class SMSNotification {
        #composeMessage(String formattedMessage) String
        #sendMessage(String to, String message)
        #postSendAnalytics(String to)
    }

    class Main {
        +main(String[] args)
    }

    NotificationSender <|-- EmailNotification
    NotificationSender <|-- SMSNotification
    Main ..> NotificationSender : uses
```

---

## How Template Method Resolves the Issues

| Issue | Solution |
|---|---|
| **Code Duplication** | Rate limit check, validation, logging, and analytics are centralized in the base class. Written once, inherited by all. |
| **Hardcoded Behavior** | Email vs. SMS specifics handled by subclasses implementing `compose_message()` and `send_message()`. |
| **Lack of Extensibility** | Add `PushNotification` by extending `NotificationSender` and implementing the two abstract methods. Zero changes to existing classes. |
| **Maintenance Overhead** | Update rate limit logic in one place (`NotificationSender`). All subclasses inherit the fix automatically. |

---

## Template Method vs. Strategy

| Aspect | Template Method | Strategy |
|---|---|---|
| **Mechanism** | Inheritance. Subclasses fill in abstract steps. | Composition. Context delegates to an injected strategy object. |
| **Runtime swap?** | No — algorithm is fixed at compile time via class hierarchy. | Yes — strategy can be swapped at runtime. |
| **Use when** | The overall skeleton is fixed; only specific steps vary. | The entire algorithm varies and needs to be swappable. |

---

## When to Use

- Multiple classes follow the **same algorithm structure** but differ in specific steps.
- You want to **avoid code duplication** of common steps across similar classes.
- You need to **enforce a fixed order of steps** — subclasses should not reorder the algorithm.
- You want to provide **optional hooks** — override to customize, skip to get default behavior.

---

## Pros & Cons

**Pros**
- Promotes code reusability — shared steps written once in base class.
- Supports OCP — new behaviors added by subclassing, not modifying.
- Enforces consistent flow — the algorithm sequence is guaranteed by the template method.
- Hooks allow optional customization without forcing implementation.

**Cons**
- Inheritance-based — reduces flexibility; behavior is tightly coupled to base class.
- Changes in base class affect all subclasses — be careful with inheritance hierarchies.
- Can lead to too many subclasses if many step combinations are needed (consider Strategy instead).
- Not ideal if the algorithm varies significantly between implementations — use Strategy in that case.
