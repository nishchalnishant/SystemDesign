---
module: 06-lld
topic: Problems
status: unread
tags: [06-lld, system-design, problems]
---
# Design Food Delivery System (Zomato / Swiggy)

> **Difficulty**: Medium
> **Topics**: Strategy, Observer, State Machine
> **Key Concepts**: Order lifecycle, delivery agent assignment, real-time status updates.

---

## What Breaks Without This Design?

```python
class OrderSystem:
    def __init__(self):
        self._order_status: str = ""  # "PLACED", "PREPARING", "DELIVERED"
        self._agent_id: str = ""

    def place_order(self, restaurant_id: str, items: list[str]) -> None:
        self._order_status = "PLACED"
        self._agent_id = self._find_nearest_agent(restaurant_id)  # hardcoded nearest-only
        self._notify_restaurant(restaurant_id)

    def update_status(self, status: str) -> None:
        self._order_status = status
        if status == "READY":
            self._send_push_notification(self._agent_id)
        if status == "DELIVERED":
            self._charge_customer()
```

**Concrete failures**:
1. **Agent assignment is hardcoded to nearest-only**: No way to switch to highest-rated or least-busy agent assignment without editing `placeOrder()`.
2. **String-based status has no transition rules**: Nothing prevents setting status to `"DELIVERED"` before `"PREPARING"` — the lifecycle is unenforced.
3. **All notifications hardcoded inside `updateStatus`**: Adding an email notification requires editing the method. Adding a new subscriber (analytics, restaurant dashboard) adds more `if` blocks.
4. **No order item modeling**: `List<String>` can't capture quantity, customizations, or per-item pricing.

---

## Derive the Class Structure

**Force 1 — Agent assignment must be swappable**: Extract `DeliveryAssignmentStrategy` interface. `NearestAgentStrategy`, `HighestRatedStrategy`, `LeastBusyStrategy` implement it.

**Force 2 — Order has a strict lifecycle**: Extract `OrderStatus` enum with valid transitions. State machine enforces `PLACED → ACCEPTED → PREPARING → READY → PICKED_UP → DELIVERED`.

**Force 3 — Status change must notify multiple parties**: Extract `OrderObserver` interface. `CustomerNotifier`, `RestaurantNotifier`, `AgentNotifier`, `AnalyticsTracker` implement it. `Order` maintains a list of observers.

**Force 4 — Order items need structure**: Extract `OrderItem` (MenuItem reference, quantity, customizations, price snapshot).

```
God class → Order (entity + state machine + observable)
          → OrderItem (line item)
          → Restaurant + Menu + MenuItem (catalog)
          → DeliveryAgent (state: AVAILABLE, ON_DELIVERY)
          → DeliveryAssignmentStrategy (pluggable)
          → OrderObserver (notification fan-out)
          → OrderService (orchestration)
```

---

## Phase 1: Requirements

**Actors**: Customer, Restaurant, Delivery Agent, System.

**Must-have**:
- Customer browses restaurants and menu, places order
- Restaurant accepts order and marks food ready
- System assigns nearest available delivery agent
- Agent picks up and delivers; status tracked throughout
- Customer and restaurant receive real-time status updates
- Payment processed on delivery confirmation

---

## Phase 2: Order State Machine

```
PLACED ──restaurant accepts──▶ ACCEPTED
ACCEPTED ──kitchen starts──▶ PREPARING
PREPARING ──food ready──▶ READY
READY ──agent picks up──▶ PICKED_UP
PICKED_UP ──delivered──▶ DELIVERED
Any state ──cancel (within window)──▶ CANCELLED
```

---

## Phase 3: Class Diagram

```
Order
  - orderId: String
  - customer: Customer
  - restaurant: Restaurant
  - items: List<OrderItem>
  - status: OrderStatus
  - assignedAgent: DeliveryAgent
  - observers: List<OrderObserver>
  + transition(OrderStatus): void   // validates and fires observers
  + addObserver(OrderObserver): void
  + notifyObservers(): void

OrderItem
  - menuItem: MenuItem
  - quantity: int
  - customization: String
  - priceAtOrder: double   // snapshot; menu price may change later

Restaurant
  - restaurantId: String
  - name: String
  - location: Location
  - menu: List<MenuItem>
  - status: RestaurantStatus   // OPEN, CLOSED, BUSY

DeliveryAgent
  - agentId: String
  - name: String
  - location: Location
  - status: AgentStatus        // AVAILABLE, ON_DELIVERY

DeliveryAssignmentStrategy <<interface>>
  + assign(Order, List<DeliveryAgent>): DeliveryAgent

NearestAgentStrategy implements DeliveryAssignmentStrategy
HighestRatedStrategy implements DeliveryAssignmentStrategy

OrderObserver <<interface>>
  + onStatusChange(Order): void

CustomerNotifier implements OrderObserver
RestaurantNotifier implements OrderObserver
AgentNotifier implements OrderObserver

OrderService
  - assignmentStrategy: DeliveryAssignmentStrategy
  - observers: List<OrderObserver>
  + placeOrder(customerId, restaurantId, items): Order
  + updateOrderStatus(orderId, OrderStatus): void
  + setAssignmentStrategy(DeliveryAssignmentStrategy): void
```

---

## Phase 4: Design Patterns

| Pattern | Where | Why |
|---------|-------|-----|
| **Strategy** | `DeliveryAssignmentStrategy` | Swap assignment algorithm (nearest / rated / load) at runtime |
| **Observer** | `OrderObserver` | Decouple order state changes from notification channels |
| **State** | `OrderStatus` transitions | Enforce valid lifecycle; reject illegal jumps |
| **Snapshot / Value Object** | `priceAtOrder` in `OrderItem` | Freeze price at order time; insulated from menu price changes |

---

## Phase 5: Key Implementation

### Order Transition with Observer Notification

```python
from enum import Enum, auto

class OrderStatus(Enum):
    PLACED    = auto()
    ACCEPTED  = auto()
    PREPARING = auto()
    READY     = auto()
    PICKED_UP = auto()
    DELIVERED = auto()
    CANCELLED = auto()

class Order:
    _VALID_TRANSITIONS: dict[OrderStatus, set[OrderStatus]] = {
        OrderStatus.PLACED:    {OrderStatus.ACCEPTED, OrderStatus.CANCELLED},
        OrderStatus.ACCEPTED:  {OrderStatus.PREPARING, OrderStatus.CANCELLED},
        OrderStatus.PREPARING: {OrderStatus.READY},
        OrderStatus.READY:     {OrderStatus.PICKED_UP},
        OrderStatus.PICKED_UP: {OrderStatus.DELIVERED},
    }

    def transition(self, new_status: OrderStatus) -> None:
        allowed = self._VALID_TRANSITIONS.get(self.status, set())
        if new_status not in allowed:
            raise InvalidStateTransitionException(self.status, new_status)
        self.status = new_status
        self._notify_observers()

    def _notify_observers(self) -> None:
        for obs in self._observers:
            obs.on_status_change(self)
```

### Agent Assignment

```python
import math

class NearestAgentStrategy(DeliveryAssignmentStrategy):
    def assign(self, order: Order, agents: list[DeliveryAgent]) -> DeliveryAgent:
        pickup = order.restaurant.location
        available = [a for a in agents if a.status == AgentStatus.AVAILABLE]
        if not available:
            raise NoAgentAvailableException()
        return min(available, key=lambda a: self._distance(a.location, pickup))

    def _distance(self, a: Location, b: Location) -> float:
        dlat = a.lat - b.lat
        dlon = a.lon - b.lon
        return math.sqrt(dlat * dlat + dlon * dlon)  # Euclidean; use Haversine for prod
```

### Place Order Flow

```python
def place_order(
    self,
    customer_id: str,
    restaurant_id: str,
    item_requests: list[OrderItemRequest],
) -> Order:
    customer = self._customer_repo.find_by_id(customer_id)
    restaurant = self._restaurant_repo.find_by_id(restaurant_id)

    items = [
        OrderItem(
            menu_item=restaurant.find_menu_item(req.menu_item_id),
            quantity=req.quantity,
            customization=req.customization,
        )
        for req in item_requests
    ]

    order = Order(customer, restaurant, items)
    for obs in self._observers:
        order.add_observer(obs)

    available = self._agent_repo.find_available()
    agent = self._assignment_strategy.assign(order, available)
    order.assigned_agent = agent
    agent.status = AgentStatus.ON_DELIVERY

    order.transition(OrderStatus.PLACED)  # fires observers → notifies customer + restaurant
    return order
```

---

## Interview Tips

- **Start with the state machine**: Draw `PLACED → ACCEPTED → PREPARING → READY → PICKED_UP → DELIVERED` immediately. This shows you think in terms of lifecycle.
- **Price snapshot**: Always mention that `OrderItem` stores `priceAtOrder` — menu prices change, but historical orders must remain accurate.
- **Observer vs. event bus**: For a single-service design, Observer is fine. If asked about scaling, mention async events (Kafka/SQS) decoupling restaurant, customer, and agent notifications.
- **Concurrency in agent assignment**: If two orders are placed simultaneously and both `assign()` calls see the same agent as available, both assign the same agent. Fix: mark agent `ON_DELIVERY` atomically before confirming assignment, or use a distributed lock on `agentId`.
