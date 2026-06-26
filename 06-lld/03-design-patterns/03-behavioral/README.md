# Behavioral Design Patterns

Behavioral patterns are concerned with algorithms and the assignment of responsibilities between objects.

## Patterns in this Category

### [Observer](./observer-pattern.md)
Defines a subscription mechanism to notify multiple objects about events.
* **Interview Examples:** Stock market ticker, order status fan-out (customer + restaurant + analytics), event-driven pub/sub.
* **Used in problems:** Food Delivery (OrderObserver), Notification System.

### [Strategy](./strategy-pattern.md)
Defines a family of algorithms and makes them interchangeable.
* **Interview Examples:** Payment gateway (UPI / card / wallet), delivery agent assignment (nearest / rated / least-busy), fine calculation (per-day / flat / tiered).
* **Used in problems:** BookMyShow (PaymentStrategy), Food Delivery (DeliveryAssignmentStrategy), Library Management (FineCalculator).

### [State](./state-pattern.md)
Allows an object to alter its behavior when its internal state changes.
* **Interview Examples:** Vending machine (NoCoin → HasCoin → Dispensing), order lifecycle, ATM (Idle → CardInserted → PinVerified).
* **Used in problems:** Vending Machine, Food Delivery, ATM.

### [Command](./command-pattern.md)
Encapsulates a request as an object — enables undo/redo, queuing, and replay.
* **Interview Examples:** Text editor undo/redo, task queue, transaction log replay.
* **Used in problems:** Tic-Tac-Toe (PlacePieceCommand with undo).

### [Chain of Responsibility](./chain-of-responsibility-pattern.md)
Passes a request along a chain of handlers; each handler processes or forwards.
* **Interview Examples:** Notification fallback (push → SMS → email), logging levels, HTTP middleware, approval workflow.
* **Used in problems:** Logger Library (log level chain), Notification System (fallback chain).

### [Template Method](./template-method-pattern.md)
Defines the skeleton of an algorithm; subclasses override specific steps.
* **Interview Examples:** Data parser (Open → Parse → Close; subclass implements Parse for CSV/JSON/XML), game AI behavior.
* **Used in problems:** Notification System (NotificationTemplate).

---

## When to Use Behavioral Patterns

- **Observer**: one state change → multiple independent consumers need to react.
- **Strategy**: multiple algorithms for the same task; need to swap at runtime.
- **State**: same action does different things depending on current state.
- **Command**: need undo/redo OR need to queue/replay requests as objects.
- **Chain of Responsibility**: a request may be handled by one of several handlers; handlers are configurable.
- **Template Method**: algorithm skeleton is fixed; specific steps vary by subclass.
