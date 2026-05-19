# Chain of Responsibility

## Question

A customer support system has three tiers: a bot handles FAQs, a junior agent handles simple billing, and a senior agent handles escalations. Write `handleRequest(Request r)` that routes the request to the right tier.

Try it before reading on.

---

## Problem Without the Pattern

```java
class SupportSystem {
    public void handleRequest(Request r) {
        if (r.type == FAQ) {
            bot.answer(r);
        } else if (r.type == BILLING && r.complexity == LOW) {
            juniorAgent.handle(r);
        } else if (r.complexity == HIGH) {
            seniorAgent.handle(r);
        } else {
            manager.escalate(r);
        }
    }
}
```

**What breaks**:
1. **OCP violation**: Adding a new tier (e.g., `TechSupport`) means editing `handleRequest`.
2. **SRP violation**: `SupportSystem` must know the rules for every tier's decision boundary.
3. **Routing logic and handling logic are mixed**: The condition that decides *who* handles is inseparable from the code that *calls* the handler.
4. **Rigid order**: Reordering the chain requires rewriting conditions, not just reordering objects.

---

## Derive the Minimal Fix

The constraint: **each handler decides whether to handle or pass — the sender should not know the chain structure**.

Step 1 — extract a `Handler` interface with `handle(Request)` and a `next` link:
```java
abstract class SupportHandler {
    protected SupportHandler next;

    public void setNext(SupportHandler next) { this.next = next; }

    public abstract void handle(Request r);

    protected void passToNext(Request r) {
        if (next != null) next.handle(r);
        else System.out.println("Unhandled: " + r);
    }
}
```

Step 2 — each tier is its own class that handles what it can, passes the rest:
```java
class BotHandler extends SupportHandler {
    public void handle(Request r) {
        if (r.type == FAQ) System.out.println("Bot: answered FAQ");
        else passToNext(r);
    }
}

class JuniorAgentHandler extends SupportHandler {
    public void handle(Request r) {
        if (r.complexity == LOW) System.out.println("Junior: handled");
        else passToNext(r);
    }
}
```

Step 3 — wire the chain once at setup; the sender just calls the first link:
```java
SupportHandler bot    = new BotHandler();
SupportHandler junior = new JuniorAgentHandler();
SupportHandler senior = new SeniorAgentHandler();
bot.setNext(junior);
junior.setNext(senior);

bot.handle(incomingRequest); // request propagates automatically
```

Adding `TechSupport` is now: create `TechSupportHandler`, insert it into the chain at the right position. Zero edits to existing handlers.

---

> **Category**: Behavioral Pattern
> **Purpose**: Pass a request along a chain of handlers. Each handler decides to process the request or pass it to the next handler in the chain.

## Real-Life Analogy

**A customer support ticket escalation system.**

When you raise a support ticket, it goes to Level 1 (basic helpdesk). If L1 can't solve it, they escalate to Level 2 (technical support). If L2 can't solve it, they escalate to Level 3 (engineering). Each level handles what it can and passes the rest up.

Key properties:
- **L1 handles** routine questions (password reset, account info). They don't know L3 exists.
- **L2 handles** deeper technical issues. If solved, the chain stops. No need to bother L3.
- **L3 handles** anything that reaches them. If they can't handle it, the ticket is unresolved.
- **You (the sender) don't know** which level will ultimately handle it. You just submit the ticket.

This is Chain of Responsibility: the sender is decoupled from the receiver. The chain is configured at setup time. Handlers can be added, removed, or reordered without touching the client or other handlers.

---

## Formal Definition

The Chain of Responsibility Pattern transforms particular behaviors into standalone handler objects. It allows a request to be passed along a chain of handlers, where each handler decides whether to process the request or pass it to the next. This decouples the sender from the receivers.

**Key Components**:

| Component | Role | Example |
|---|---|---|
| **Handler** | Abstract class/interface defining `handleRequest()` and a reference to the next handler. | `SupportHandler` |
| **Concrete Handler** | Processes the request if it can, otherwise delegates to `nextHandler`. | `GeneralSupport`, `BillingSupport` |
| **Client** | Sends requests to the first handler in the chain. Unaware of which handler processes it. | `ChainOfResponsibilityDemo` |

---

## Understanding the Problem

Without Chain of Responsibility, all logic is crammed into a single method:

```java
// SupportService class: Handles different types of support requests
public class SupportService {

    // Method to handle the support request based on the type of issue
    public void handleRequest(String type) {
        if (type.equals("general")) {
            System.out.println("Handled by General Support");
        } else if (type.equals("refund")) {
            System.out.println("Handled by Billing Team");
        } else if (type.equals("technical")) {
            System.out.println("Handled by Technical Support");
        } else if (type.equals("delivery")) {
            System.out.println("Handled by Delivery Team");
        } else {
            System.out.println("No handler available");
        }
    }

    // Main function: Entry point to test the chain of responsibility pattern
    public static void main(String[] args) {
        // Create an instance of SupportService
        SupportService supportService = new SupportService();
        
        // Test with different types of requests
        supportService.handleRequest("general");
        supportService.handleRequest("refund");
        supportService.handleRequest("technical");
        supportService.handleRequest("delivery");
        supportService.handleRequest("unknown");
    }
}
```

| Issue | Description |
|---|---|
| **Violation of OCP** | Every time a new request type is added, `handleRequest` must be modified. |
| **Monolithic Code** | All logic in one method. Hard to maintain, test, and extend. Each handler is tightly coupled with the others. |
| **Scalability** | Can't change the order of processing without modifying the core logic. Adding new handlers or reordering is cumbersome. |

---

## Solution: Chain of Responsibility

```java
// Abstract class defining the SupportHandler
abstract class SupportHandler {
    protected SupportHandler nextHandler;

    // Method to set the next handler in the chain
    public void setNextHandler(SupportHandler nextHandler) {
        this.nextHandler = nextHandler;
    }

    // Abstract method to handle the request
    public abstract void handleRequest(String requestType);
}

// Concrete Handler for General Support
class GeneralSupport extends SupportHandler {
    @Override
    public void handleRequest(String requestType) {
        if (requestType.equalsIgnoreCase("general")) {
            System.out.println("GeneralSupport: Handling general query");
        } else if (nextHandler != null) {
            nextHandler.handleRequest(requestType);
        }
    }
}

// Concrete Handler for Billing Support
class BillingSupport extends SupportHandler {
    @Override
    public void handleRequest(String requestType) {
        if (requestType.equalsIgnoreCase("refund")) {
            System.out.println("BillingSupport: Handling refund request");
        } else if (nextHandler != null) {
            nextHandler.handleRequest(requestType);
        }
    }
}

// Concrete Handler for Technical Support
class TechnicalSupport extends SupportHandler {
    @Override
    public void handleRequest(String requestType) {
        if (requestType.equalsIgnoreCase("technical")) {
            System.out.println("TechnicalSupport: Handling technical issue");
        } else if (nextHandler != null) {
            nextHandler.handleRequest(requestType);
        }
    }
}

// Concrete Handler for Delivery Support
class DeliverySupport extends SupportHandler {
    @Override
    public void handleRequest(String requestType) {
        if (requestType.equalsIgnoreCase("delivery")) {
            System.out.println("DeliverySupport: Handling delivery issue");
        } else if (nextHandler != null) {
            nextHandler.handleRequest(requestType);
        } else {
            System.out.println("DeliverySupport: No handler found for request");
        }
    }
}

// Client Code
public class ChainOfResponsibilityDemo {
    public static void main(String[] args) {
        SupportHandler general = new GeneralSupport();
        SupportHandler billing = new BillingSupport();
        SupportHandler technical = new TechnicalSupport();
        SupportHandler delivery = new DeliverySupport();

        // Setting up the chain: general -> billing -> technical -> delivery
        general.setNextHandler(billing);
        billing.setNextHandler(technical);
        technical.setNextHandler(delivery);

        // Testing the chain of responsibility with different request types
        general.handleRequest("refund");    // Passes through General -> handled by Billing
        general.handleRequest("delivery");  // Passes through General -> Billing -> Technical -> handled by Delivery
        general.handleRequest("unknown");   // Reaches end of chain unhandled
    }
}
```

### Class Diagram

```mermaid
classDiagram
    class SupportHandler {
        <<abstract>>
        #SupportHandler nextHandler
        +setNextHandler(SupportHandler nextHandler)
        +handleRequest(String requestType)*
    }

    class GeneralSupport {
        +handleRequest(String requestType)
    }

    class BillingSupport {
        +handleRequest(String requestType)
    }

    class TechnicalSupport {
        +handleRequest(String requestType)
    }

    class DeliverySupport {
        +handleRequest(String requestType)
    }

    class ChainOfResponsibilityDemo {
        +main(String[] args)
    }

    SupportHandler <|-- GeneralSupport
    SupportHandler <|-- BillingSupport
    SupportHandler <|-- TechnicalSupport
    SupportHandler <|-- DeliverySupport
    SupportHandler o-- SupportHandler : nextHandler
    ChainOfResponsibilityDemo ..> SupportHandler : uses
```

---

## How Chain of Responsibility Fixes the Issues

| Issue | Solution |
|---|---|
| **Violation of OCP** | Add a new handler class without modifying existing code. Each handler is open for extension and closed for modification. |
| **Monolithic Code** | Logic is separated into individual handler classes, each responsible for one type of request. |
| **Scalability** | New handlers can be added without changing existing logic. Chain order can change by simply rearranging `setNextHandler()` calls. |

---

## When to Use

- Multiple objects can handle a request, but the specific handler is unknown beforehand.
- You want to decompose request senders from receivers.
- You need to dynamically change or reorder the chain at runtime.
- You want each handler to have a single, well-defined responsibility.

This pattern is valuable in: customer support routing, middleware pipelines (Spring filters, servlet filters), GUI event bubbling, logging (DEBUG → INFO → WARN → ERROR), and sign-up/validation pipelines.

---

## Pros & Cons

**Pros**
- Reduces coupling between sender and receiver — sender doesn't know which handler processes it.
- Easy to add or remove handlers without changing existing code.
- Follows Single Responsibility Principle and Open/Closed Principle.
- Dynamic control over handler execution sequence.

**Cons**
- Performance issues if the chain is very long — every handler is evaluated in sequence.
- Debugging is harder — the dynamic flow makes it difficult to trace exactly which handler processed a request.
- Risk of unhandled requests — if no handler matches, the request falls off the end of the chain.
- Order matters — incorrect chain setup can break the logic.

---

## Real-World Examples

1. **Sign-Up Validation Pipeline**: Validate email → check age → verify terms accepted → CAPTCHA check. Each check is a handler; fails at the first invalid step.
2. **Customer Support Ticket Routing**: General → Billing → Technical → Delivery as shown above.
3. **GUI Event Bubbling**: A mouse click on a button bubbles up: Button → Panel → Window → Application.
4. **Servlet Filters / Spring Security Filter Chain**: Each filter handles authentication, authorization, logging, etc. in sequence.
5. **Logging Frameworks**: Log4j uses levels as a chain — DEBUG passes through if level is set higher.
