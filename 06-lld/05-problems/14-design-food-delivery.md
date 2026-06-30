> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design a Food Delivery System (e.g., UberEats, DoorDash) — a massive, multi-actor system focusing on order lifecycle, geolocation, and dynamic assignment.
>
> **Key concepts:**
> - Core Entities: `User`, `Restaurant`, `MenuItem`, `DeliveryAgent`, `Order`.
> - State Pattern: `Order` lifecycle (`PLACED`, `ACCEPTED`, `PREPARING`, `OUT_FOR_DELIVERY`, `DELIVERED`).
> - Observer Pattern: As the `Order` state changes, notifications must be pushed to both the `User` and the `Restaurant`.
> - Strategy Pattern (Dispatching): How do you assign a `DeliveryAgent` to an order? You could use a `NearestAgentStrategy`, a `HighestRatedAgentStrategy`, or a `LeastBusyAgentStrategy`.
> - Search: Implementing a menu search requires a Strategy pattern as well (search by name, category, or rating).
>
> **Key takeaway:** In an LLD interview, you can't design the whole backend. Focus on the core domain models and specifically on the dispatching logic (Strategy) and the status updates (Observer).

---
module: 06-lld
topic: Problems
status: unread
tags: [06-lld, lld, food-delivery, state-machine, observer, strategy]
---
# Design a Food Delivery System

> **Difficulty**: Medium-Hard  
> **Asked at**: Amazon, Uber, DoorDash  
> **Key Patterns**: State (order lifecycle), Observer (tracking), Strategy (delivery assignment)

---

## Understanding the Problem

Design a food delivery platform like Swiggy or DoorDash where users can browse restaurant menus, add items to a cart, place orders, and track them through preparation and delivery. The system assigns a delivery agent to each order and updates order status in real time.

---

## Clarifying Questions

**You**: "Can a cart contain items from multiple restaurants?"  
**Interviewer**: "No. One cart per user, one restaurant per cart. If the user switches restaurants, the cart is cleared."

**You**: "Who assigns the delivery agent — the system automatically, or a human dispatcher?"  
**Interviewer**: "System automatically. Assign the nearest available agent."

**You**: "What are the order statuses we need to model?"  
**Interviewer**: "PLACED → CONFIRMED → PREPARING → PICKED_UP → DELIVERED. Also CANCELLED."

**You**: "Can an order be cancelled after the restaurant confirms it?"  
**Interviewer**: "Only before PREPARING. Once the restaurant starts preparing, no cancellation."

**You**: "Do we need real-time tracking — like GPS coordinates streaming?"  
**Interviewer**: "Mention the approach in deep dive. For now, polling-based status updates are fine."

**You**: "Should we handle restaurant going offline mid-order?"  
**Interviewer**: "Yes, handle it in deep dive."

---

## Final Requirements

**In scope:**
1. Browse restaurant menus; add items to cart
2. Place order from cart
3. Order moves through status lifecycle: PLACED → CONFIRMED → PREPARING → PICKED_UP → DELIVERED
4. Delivery agent automatically assigned when order is confirmed
5. Update order status (by restaurant or agent)
6. Cancel order (only before PREPARING)

**Out of scope:**
- Payment processing
- GPS streaming / real-time tracking infra
- Multi-restaurant carts
- Surge pricing

---

## Core Entities and Relationships

| Entity | Responsibility |
|--------|---------------|
| Restaurant | Owns a menu of MenuItems; has an availability status |
| MenuItem | Name, price, availability flag |
| Cart | Belongs to a user; holds CartItems (item + quantity) for one restaurant |
| Order | Snapshot of cart at placement; owns its status; links to delivery agent |
| OrderStatus | Enum — PLACED, CONFIRMED, PREPARING, PICKED_UP, DELIVERED, CANCELLED |
| DeliveryAgent | Has availability status and location; accepts order assignments |
| DeliveryService | Finds and assigns nearest available agent |
| NotificationService | Notifies users/agents on status changes (Observer) |

`Cart` is mutable during browsing. `Order` is an immutable snapshot created at placement. `DeliveryService` uses a Strategy for agent selection. `Order` owns its state transitions with validation.

---

## Class Design

### MenuItem and Restaurant

| Requirement | What Restaurant must track |
|-------------|---------------------------|
| Identity | restaurant_id, name, cuisine |
| Availability | is_open: bool |
| Menu | menu_items: Dict[str, MenuItem] |

```
class MenuItem:
- item_id: str
- name: str
- price: float
- is_available: bool

class Restaurant:
- restaurant_id: str
- name: str
- menu_items: Dict[str, MenuItem]
- is_open: bool
+ add_item(item: MenuItem) -> None
+ get_menu() -> List[MenuItem]
```

### Cart and CartItem

```
class CartItem:
- item: MenuItem
- quantity: int
+ subtotal() -> float

class Cart:
- cart_id: str
- user_id: str
- restaurant_id: str
- items: Dict[str, CartItem]  # item_id -> CartItem
+ add_item(item: MenuItem, quantity: int) -> None
+ remove_item(item_id: str) -> None
+ total() -> float
+ clear() -> None
+ is_empty() -> bool
```

### Order

| Requirement | What Order must track |
|-------------|----------------------|
| Identity | order_id |
| Parties | user_id, restaurant_id, delivery_agent: Optional[DeliveryAgent] |
| Snapshot | items: List[CartItem], total: float |
| Lifecycle | status: OrderStatus |

```
class OrderStatus(Enum):
    PLACED, CONFIRMED, PREPARING, PICKED_UP, DELIVERED, CANCELLED

class Order:
- order_id: str
- user_id: str
- restaurant: Restaurant
- items: List[CartItem]
- total: float
- status: OrderStatus
- delivery_agent: Optional[DeliveryAgent]
- created_at: datetime
+ update_status(new_status: OrderStatus) -> None
+ assign_agent(agent: DeliveryAgent) -> None
+ cancel() -> None
```

### DeliveryAgent

```
class AgentStatus(Enum):
    AVAILABLE, BUSY, OFFLINE

class DeliveryAgent:
- agent_id: str
- name: str
- status: AgentStatus
- latitude: float
- longitude: float
+ accept_order(order: Order) -> None
+ complete_delivery() -> None
```

### DeliveryService and OrderService

```
class DeliveryService:
- agents: List[DeliveryAgent]
+ assign_agent(order: Order) -> Optional[DeliveryAgent]
- _find_nearest_available(order: Order) -> Optional[DeliveryAgent]

class OrderService:
- orders: Dict[str, Order]
- carts: Dict[str, Cart]  # user_id -> Cart
- delivery_service: DeliveryService
- notification_service: NotificationService
+ add_to_cart(user_id, restaurant, item_id, quantity) -> None
+ place_order(user_id) -> Order
+ update_order_status(order_id, new_status) -> None
+ cancel_order(order_id) -> None
+ get_order(order_id) -> Order
```

---

## Implementation

### Core Method: place_order + assign_delivery_agent

**Core logic:**
1. Validate cart is non-empty and restaurant is open
2. Snapshot cart into Order (immutable from here)
3. Clear cart
4. Save order with status PLACED
5. Notify restaurant
6. Restaurant confirms → trigger agent assignment → status moves to CONFIRMED

**Edge cases:**
- Item becomes unavailable between add-to-cart and place_order → raise error listing unavailable items
- Restaurant closed at placement time → raise error
- No available delivery agents → order stays CONFIRMED, retry assignment

```python
import uuid
from datetime import datetime
from enum import Enum, auto
from typing import Optional, List, Dict
import math


class OrderStatus(Enum):
    PLACED = auto()
    CONFIRMED = auto()
    PREPARING = auto()
    PICKED_UP = auto()
    DELIVERED = auto()
    CANCELLED = auto()


class AgentStatus(Enum):
    AVAILABLE = auto()
    BUSY = auto()
    OFFLINE = auto()


class MenuItem:
    def __init__(self, item_id: str, name: str, price: float):
        self.item_id = item_id
        self.name = name
        self.price = price
        self.is_available = True


class Restaurant:
    def __init__(self, restaurant_id: str, name: str):
        self.restaurant_id = restaurant_id
        self.name = name
        self.menu_items: Dict[str, MenuItem] = {}
        self.is_open = True

    def add_item(self, item: MenuItem) -> None:
        self.menu_items[item.item_id] = item

    def get_item(self, item_id: str) -> MenuItem:
        if item_id not in self.menu_items:
            raise KeyError(f"Item {item_id} not found")
        return self.menu_items[item_id]


class CartItem:
    def __init__(self, item: MenuItem, quantity: int):
        self.item = item
        self.quantity = quantity

    def subtotal(self) -> float:
        return self.item.price * self.quantity


class Cart:
    def __init__(self, user_id: str):
        self.user_id = user_id
        self.restaurant_id: Optional[str] = None
        self.items: Dict[str, CartItem] = {}

    def add_item(self, restaurant: Restaurant, item: MenuItem, quantity: int) -> None:
        if self.restaurant_id and self.restaurant_id != restaurant.restaurant_id:
            self.items.clear()
            self.restaurant_id = None
        self.restaurant_id = restaurant.restaurant_id
        if item.item_id in self.items:
            self.items[item.item_id].quantity += quantity
        else:
            self.items[item.item_id] = CartItem(item, quantity)

    def remove_item(self, item_id: str) -> None:
        self.items.pop(item_id, None)

    def total(self) -> float:
        return sum(ci.subtotal() for ci in self.items.values())

    def clear(self) -> None:
        self.items.clear()
        self.restaurant_id = None

    def is_empty(self) -> bool:
        return len(self.items) == 0


class DeliveryAgent:
    def __init__(self, agent_id: str, name: str, lat: float, lng: float):
        self.agent_id = agent_id
        self.name = name
        self.latitude = lat
        self.longitude = lng
        self.status = AgentStatus.AVAILABLE

    def accept_order(self) -> None:
        self.status = AgentStatus.BUSY

    def complete_delivery(self) -> None:
        self.status = AgentStatus.AVAILABLE


class Order:
    VALID_TRANSITIONS = {
        OrderStatus.PLACED: {OrderStatus.CONFIRMED, OrderStatus.CANCELLED},
        OrderStatus.CONFIRMED: {OrderStatus.PREPARING, OrderStatus.CANCELLED},
        OrderStatus.PREPARING: {OrderStatus.PICKED_UP},
        OrderStatus.PICKED_UP: {OrderStatus.DELIVERED},
        OrderStatus.DELIVERED: set(),
        OrderStatus.CANCELLED: set(),
    }

    def __init__(self, order_id: str, user_id: str, restaurant: Restaurant,
                 items: List[CartItem], total: float):
        self.order_id = order_id
        self.user_id = user_id
        self.restaurant = restaurant
        self.items = items
        self.total = total
        self.status = OrderStatus.PLACED
        self.delivery_agent: Optional[DeliveryAgent] = None
        self.created_at = datetime.utcnow()

    def update_status(self, new_status: OrderStatus) -> None:
        if new_status not in self.VALID_TRANSITIONS[self.status]:
            raise ValueError(f"Cannot transition from {self.status} to {new_status}")
        self.status = new_status

    def assign_agent(self, agent: DeliveryAgent) -> None:
        self.delivery_agent = agent

    def cancel(self) -> None:
        if self.status not in (OrderStatus.PLACED, OrderStatus.CONFIRMED):
            raise ValueError(f"Cannot cancel order in status {self.status}")
        self.status = OrderStatus.CANCELLED
        if self.delivery_agent:
            self.delivery_agent.complete_delivery()


def haversine_distance(lat1: float, lng1: float, lat2: float, lng2: float) -> float:
    """Approximate distance in km between two lat/lng points."""
    R = 6371
    dlat = math.radians(lat2 - lat1)
    dlng = math.radians(lng2 - lng1)
    a = math.sin(dlat/2)**2 + math.cos(math.radians(lat1)) * math.cos(math.radians(lat2)) * math.sin(dlng/2)**2
    return R * 2 * math.asin(math.sqrt(a))


class DeliveryService:
    def __init__(self):
        self.agents: List[DeliveryAgent] = []

    def add_agent(self, agent: DeliveryAgent) -> None:
        self.agents.append(agent)

    def assign_agent(self, order: Order) -> Optional[DeliveryAgent]:
        """Assigns nearest available agent. Returns None if no agent available."""
        restaurant = order.restaurant
        available = [a for a in self.agents if a.status == AgentStatus.AVAILABLE]
        if not available:
            return None
        nearest = min(available, key=lambda a: haversine_distance(
            restaurant.latitude if hasattr(restaurant, 'latitude') else 0,
            restaurant.longitude if hasattr(restaurant, 'longitude') else 0,
            a.latitude, a.longitude
        ))
        nearest.accept_order()
        order.assign_agent(nearest)
        return nearest


class OrderService:
    def __init__(self, delivery_service: DeliveryService):
        self.orders: Dict[str, Order] = {}
        self.carts: Dict[str, Cart] = {}
        self.delivery_service = delivery_service

    def _get_or_create_cart(self, user_id: str) -> Cart:
        if user_id not in self.carts:
            self.carts[user_id] = Cart(user_id)
        return self.carts[user_id]

    def add_to_cart(self, user_id: str, restaurant: Restaurant,
                    item_id: str, quantity: int) -> None:
        item = restaurant.get_item(item_id)
        if not item.is_available:
            raise ValueError(f"Item {item.name} is not available")
        cart = self._get_or_create_cart(user_id)
        cart.add_item(restaurant, item, quantity)

    def place_order(self, user_id: str, restaurant: Restaurant) -> Order:
        cart = self._get_or_create_cart(user_id)
        if cart.is_empty():
            raise ValueError("Cart is empty")
        if not restaurant.is_open:
            raise ValueError("Restaurant is currently closed")
        # Validate all items still available
        unavailable = [ci.item.name for ci in cart.items.values()
                       if not ci.item.is_available]
        if unavailable:
            raise ValueError(f"Unavailable items: {', '.join(unavailable)}")
        order = Order(
            order_id=str(uuid.uuid4()),
            user_id=user_id,
            restaurant=restaurant,
            items=list(cart.items.values()),
            total=cart.total()
        )
        self.orders[order.order_id] = order
        cart.clear()
        return order

    def update_order_status(self, order_id: str, new_status: OrderStatus) -> None:
        order = self._get_order(order_id)
        order.update_status(new_status)
        if new_status == OrderStatus.CONFIRMED:
            agent = self.delivery_service.assign_agent(order)
            if not agent:
                print(f"No agent available for order {order_id}. Will retry.")
        if new_status == OrderStatus.DELIVERED and order.delivery_agent:
            order.delivery_agent.complete_delivery()

    def cancel_order(self, order_id: str) -> None:
        order = self._get_order(order_id)
        order.cancel()

    def _get_order(self, order_id: str) -> Order:
        if order_id not in self.orders:
            raise KeyError(f"Order {order_id} not found")
        return self.orders[order_id]
```

---

## Verification

Trace: User places order, restaurant confirms, agent assigned, delivered.

1. `add_to_cart("u1", restaurant, "item1", 2)` → Cart has 2x Burger ($10 each), total=$20
2. `place_order("u1", restaurant)` → Order(id="o1", status=PLACED), cart cleared
3. `update_order_status("o1", CONFIRMED)` → order.status=CONFIRMED, nearest agent found (agent_a), agent_a.status=BUSY
4. `update_order_status("o1", PREPARING)` → order.status=PREPARING (valid transition)
5. `cancel_order("o1")` → raises ValueError: Cannot cancel in PREPARING
6. `update_order_status("o1", PICKED_UP)` → order.status=PICKED_UP
7. `update_order_status("o1", DELIVERED)` → order.status=DELIVERED, agent_a.status=AVAILABLE

---

## Deep Dive & Extensibility

### 1. "How do you assign the nearest delivery agent?"

Use Haversine distance from the restaurant's location to each available agent's last known location. This runs in O(n) where n = number of available agents.

For production at scale: index agent locations in a geospatial data structure (PostGIS, Redis GEOSEARCH, or a quadtree). Query returns agents within a radius in O(log n + k).

```python
# Redis GEOSEARCH approach (pseudocode):
# On agent location update: GEOADD agents_geo lng lat agent_id
# On assignment: GEOSEARCH agents_geo FROMLONLAT restaurant_lng restaurant_lat
#                BYRADIUS 5 km ASC COUNT 1
```

### 2. "Walk through the order status state machine."

Valid transitions form a DAG:

```
PLACED → CONFIRMED → PREPARING → PICKED_UP → DELIVERED
  ↓           ↓
CANCELLED  CANCELLED
```

Enforced via a transition table (dict of sets). Any invalid transition raises ValueError immediately. This is better than a series of if/else because:
- Adding a new status = add one entry to the dict
- Transitions are data, not code — easy to audit

### 3. "Real-time tracking — polling vs WebSocket?"

**Polling**: Client calls `GET /orders/{id}/status` every 5s. Simple to implement. Wasteful when nothing changes. Adds server load proportional to active orders × polling frequency.

**WebSocket**: Server pushes status updates to client when they happen. Efficient — one persistent connection per active session. Requires connection management infrastructure. Best for live tracking.

**Server-Sent Events (SSE)**: Server pushes over HTTP. One-directional (server → client). Simpler than WebSocket for read-only streams. Works through HTTP proxies.

For a food delivery app: use WebSocket for the live tracking map screen. Use push notifications (FCM/APNS) for key milestones (order confirmed, agent on the way, delivered).

### 4. "Restaurant goes offline mid-order — what happens?"

On order PLACED or CONFIRMED, if the restaurant marks itself `is_open=False`:
- Orders already in PREPARING or beyond: continue to completion (restaurant is physically making the food)
- Orders in PLACED or CONFIRMED: cancel with a full refund, notify the user

```python
def restaurant_go_offline(self, restaurant_id: str) -> None:
    restaurant = self.restaurants[restaurant_id]
    restaurant.is_open = False
    for order in self.orders.values():
        if (order.restaurant.restaurant_id == restaurant_id
                and order.status in (OrderStatus.PLACED, OrderStatus.CONFIRMED)):
            order.cancel()
            # trigger refund and user notification
```

### 5. "How would you implement ratings and reviews?"

Add a `Review` entity linked to a completed Order. Enforce one review per order (not per user/restaurant pair — so the user can review every delivery).

```python
class Review:
    def __init__(self, review_id: str, order_id: str, user_id: str,
                 restaurant_rating: int, agent_rating: int, comment: str):
        if not (1 <= restaurant_rating <= 5 and 1 <= agent_rating <= 5):
            raise ValueError("Ratings must be 1-5")
        self.review_id = review_id
        self.order_id = order_id
        self.user_id = user_id
        self.restaurant_rating = restaurant_rating
        self.agent_rating = agent_rating
        self.comment = comment
        self.created_at = datetime.utcnow()

def submit_review(self, order_id: str, rest_rating: int,
                  agent_rating: int, comment: str) -> Review:
    order = self._get_order(order_id)
    if order.status != OrderStatus.DELIVERED:
        raise ValueError("Can only review completed orders")
    if order_id in self.reviews:
        raise ValueError("Order already reviewed")
    review = Review(str(uuid.uuid4()), order_id, order.user_id,
                    rest_rating, agent_rating, comment)
    self.reviews[order_id] = review
    return review
```

---

## Interviewer Questions by Level

**Junior**: Why does Cart store a restaurant_id and clear itself when the restaurant changes? What problem does that prevent?

**Mid-level**: The Order status machine is implemented as a transition table dict. What would break if you used if/else in `update_status` instead? What's harder to maintain?

**Senior**: At 100k concurrent orders, the `assign_agent` method iterates all available agents linearly. How would you redesign the agent selection to handle this at scale?

---

## Common Interview Questions

- **Q: Why is Order a snapshot of Cart rather than a reference to it?**  
  A: Cart is mutable — users can modify it at any time. Order must be immutable once placed. Snapshotting prices and items at placement time prevents price changes or item removals from affecting existing orders.

- **Q: What is the order status state machine?**  
  A: PLACED → CONFIRMED → PREPARING → PICKED_UP → DELIVERED. CANCELLED is reachable from PLACED and CONFIRMED only.

- **Q: How do you assign the nearest delivery agent?**  
  A: Compute Haversine distance from restaurant to each available agent, pick the minimum. For scale: use Redis GEOSEARCH or PostGIS spatial index.

- **Q: What if the assigned agent rejects the delivery?**  
  A: Mark that agent OFFLINE or UNAVAILABLE temporarily. Retry assignment with the next nearest agent. After N retries, escalate to manual dispatch.

- **Q: How do you handle cart abandonment?**  
  A: Run a background job that clears carts older than 30 minutes with no activity. Send a push notification to re-engage the user before clearing.

- **Q: Why can't you cancel an order in PREPARING status?**  
  A: The restaurant has already started cooking — food cost is incurred. The policy prevents waste. Some platforms allow cancellation with a partial fee.

- **Q: How would you model a multi-restaurant cart?**  
  A: Cart becomes a dict of restaurant_id → List[CartItem]. Placing the order creates one Order per restaurant. Delivery assignments are per-order. Checkout total aggregates across all sub-orders.
