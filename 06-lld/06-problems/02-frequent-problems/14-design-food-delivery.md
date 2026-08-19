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
| Menu | menu_items: Map<String, MenuItem> |

```
class MenuItem:
- item_id: String
- name: String
- price: double
- is_available: boolean

class Restaurant:
- restaurant_id: String
- name: String
- menu_items: Map<String, MenuItem>
- is_open: boolean
+ add_item(item: MenuItem) -> void
+ get_menu() -> List<MenuItem>
```

### Cart and CartItem

```
class CartItem:
- item: MenuItem
- quantity: int
+ subtotal() -> double

class Cart:
- cart_id: String
- user_id: String
- restaurant_id: String
- items: Map<String, CartItem>  # item_id -> CartItem
+ add_item(item: MenuItem, quantity: int) -> void
+ remove_item(item_id: String) -> void
+ total() -> double
+ clear() -> void
+ is_empty() -> boolean
```

### Order

| Requirement | What Order must track |
|-------------|----------------------|
| Identity | order_id |
| Parties | user_id, restaurant_id, delivery_agent: Optional<DeliveryAgent> |
| Snapshot | items: List<CartItem>, total: double |
| Lifecycle | status: OrderStatus |

```
enum OrderStatus:
    PLACED, CONFIRMED, PREPARING, PICKED_UP, DELIVERED, CANCELLED

class Order:
- order_id: String
- user_id: String
- restaurant: Restaurant
- items: List<CartItem>
- total: double
- status: OrderStatus
- delivery_agent: Optional<DeliveryAgent>
- created_at: Instant
+ update_status(new_status: OrderStatus) -> void
+ assign_agent(agent: DeliveryAgent) -> void
+ cancel() -> void
```

### DeliveryAgent

```
enum AgentStatus:
    AVAILABLE, BUSY, OFFLINE

class DeliveryAgent:
- agent_id: String
- name: String
- status: AgentStatus
- latitude: double
- longitude: double
+ accept_order(order: Order) -> void
+ complete_delivery() -> void
```

### DeliveryService and OrderService

```
class DeliveryService:
- agents: List<DeliveryAgent>
+ assign_agent(order: Order) -> Optional<DeliveryAgent>
- _find_nearest_available(order: Order) -> Optional<DeliveryAgent>

class OrderService:
- orders: Map<String, Order>
- carts: Map<String, Cart>  # user_id -> Cart
- delivery_service: DeliveryService
- notification_service: NotificationService
+ add_to_cart(user_id, restaurant, item_id, quantity) -> void
+ place_order(user_id) -> Order
+ update_order_status(order_id, new_status) -> void
+ cancel_order(order_id) -> void
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

```java
import java.util.*;
import java.time.Instant;

enum OrderStatus {
    PLACED, CONFIRMED, PREPARING, PICKED_UP, DELIVERED, CANCELLED
}

enum AgentStatus {
    AVAILABLE, BUSY, OFFLINE
}

class MenuItem {
    private final String itemId;
    private final String name;
    private final double price;
    private boolean available;

    public MenuItem(String itemId, String name, double price) {
        this.itemId = itemId;
        this.name = name;
        this.price = price;
        this.available = true;
    }

    public String getItemId() { return itemId; }
    public String getName() { return name; }
    public double getPrice() { return price; }
    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }
}

class Restaurant {
    private final String restaurantId;
    private final String name;
    private final Map<String, MenuItem> menuItems = new HashMap<>();
    private boolean open = true;
    private double latitude;
    private double longitude;

    public Restaurant(String restaurantId, String name) {
        this.restaurantId = restaurantId;
        this.name = name;
    }

    public String getRestaurantId() { return restaurantId; }
    public String getName() { return name; }
    public boolean isOpen() { return open; }
    public void setOpen(boolean open) { this.open = open; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }

    public void addItem(MenuItem item) {
        menuItems.put(item.getItemId(), item);
    }

    public MenuItem getItem(String itemId) {
        if (!menuItems.containsKey(itemId)) {
            throw new NoSuchElementException("Item " + itemId + " not found");
        }
        return menuItems.get(itemId);
    }
}

class CartItem {
    private final MenuItem item;
    private int quantity;

    public CartItem(MenuItem item, int quantity) {
        this.item = item;
        this.quantity = quantity;
    }

    public MenuItem getItem() { return item; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public double subtotal() {
        return item.getPrice() * quantity;
    }
}

class Cart {
    private final String userId;
    private String restaurantId;
    private final Map<String, CartItem> items = new HashMap<>();

    public Cart(String userId) {
        this.userId = userId;
    }

    public String getUserId() { return userId; }
    public Map<String, CartItem> getItems() { return items; }

    public void addItem(Restaurant restaurant, MenuItem item, int quantity) {
        if (restaurantId != null && !restaurantId.equals(restaurant.getRestaurantId())) {
            items.clear();
            restaurantId = null;
        }
        restaurantId = restaurant.getRestaurantId();
        if (items.containsKey(item.getItemId())) {
            CartItem existing = items.get(item.getItemId());
            existing.setQuantity(existing.getQuantity() + quantity);
        } else {
            items.put(item.getItemId(), new CartItem(item, quantity));
        }
    }

    public void removeItem(String itemId) {
        items.remove(itemId);
    }

    public double total() {
        double sum = 0;
        for (CartItem ci : items.values()) {
            sum += ci.subtotal();
        }
        return sum;
    }

    public void clear() {
        items.clear();
        restaurantId = null;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }
}

class DeliveryAgent {
    private final String agentId;
    private final String name;
    private double latitude;
    private double longitude;
    private AgentStatus status;

    public DeliveryAgent(String agentId, String name, double lat, double lng) {
        this.agentId = agentId;
        this.name = name;
        this.latitude = lat;
        this.longitude = lng;
        this.status = AgentStatus.AVAILABLE;
    }

    public String getAgentId() { return agentId; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public AgentStatus getStatus() { return status; }

    public void acceptOrder() {
        this.status = AgentStatus.BUSY;
    }

    public void completeDelivery() {
        this.status = AgentStatus.AVAILABLE;
    }
}

class Order {
    private static final Map<OrderStatus, Set<OrderStatus>> VALID_TRANSITIONS = new HashMap<>();
    static {
        VALID_TRANSITIONS.put(OrderStatus.PLACED, EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED));
        VALID_TRANSITIONS.put(OrderStatus.CONFIRMED, EnumSet.of(OrderStatus.PREPARING, OrderStatus.CANCELLED));
        VALID_TRANSITIONS.put(OrderStatus.PREPARING, EnumSet.of(OrderStatus.PICKED_UP));
        VALID_TRANSITIONS.put(OrderStatus.PICKED_UP, EnumSet.of(OrderStatus.DELIVERED));
        VALID_TRANSITIONS.put(OrderStatus.DELIVERED, EnumSet.noneOf(OrderStatus.class));
        VALID_TRANSITIONS.put(OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));
    }

    private final String orderId;
    private final String userId;
    private final Restaurant restaurant;
    private final List<CartItem> items;
    private final double total;
    private OrderStatus status;
    private DeliveryAgent deliveryAgent;
    private final Instant createdAt;

    public Order(String orderId, String userId, Restaurant restaurant,
                 List<CartItem> items, double total) {
        this.orderId = orderId;
        this.userId = userId;
        this.restaurant = restaurant;
        this.items = items;
        this.total = total;
        this.status = OrderStatus.PLACED;
        this.deliveryAgent = null;
        this.createdAt = Instant.now();
    }

    public String getOrderId() { return orderId; }
    public String getUserId() { return userId; }
    public Restaurant getRestaurant() { return restaurant; }
    public OrderStatus getStatus() { return status; }
    public Optional<DeliveryAgent> getDeliveryAgent() { return Optional.ofNullable(deliveryAgent); }

    public void updateStatus(OrderStatus newStatus) {
        if (!VALID_TRANSITIONS.get(status).contains(newStatus)) {
            throw new IllegalStateException("Cannot transition from " + status + " to " + newStatus);
        }
        this.status = newStatus;
    }

    public void assignAgent(DeliveryAgent agent) {
        this.deliveryAgent = agent;
    }

    public void cancel() {
        if (status != OrderStatus.PLACED && status != OrderStatus.CONFIRMED) {
            throw new IllegalStateException("Cannot cancel order in status " + status);
        }
        this.status = OrderStatus.CANCELLED;
        if (deliveryAgent != null) {
            deliveryAgent.completeDelivery();
        }
    }
}

class DistanceUtil {
    /** Approximate distance in km between two lat/lng points. */
    public static double haversineDistance(double lat1, double lng1, double lat2, double lng2) {
        double R = 6371;
        double dlat = Math.toRadians(lat2 - lat1);
        double dlng = Math.toRadians(lng2 - lng1);
        double a = Math.pow(Math.sin(dlat / 2), 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.pow(Math.sin(dlng / 2), 2);
        return R * 2 * Math.asin(Math.sqrt(a));
    }
}

class DeliveryService {
    private final List<DeliveryAgent> agents = new ArrayList<>();

    public void addAgent(DeliveryAgent agent) {
        agents.add(agent);
    }

    /** Assigns nearest available agent. Returns Optional.empty() if no agent available. */
    public Optional<DeliveryAgent> assignAgent(Order order) {
        Restaurant restaurant = order.getRestaurant();
        List<DeliveryAgent> available = new ArrayList<>();
        for (DeliveryAgent a : agents) {
            if (a.getStatus() == AgentStatus.AVAILABLE) {
                available.add(a);
            }
        }
        if (available.isEmpty()) {
            return Optional.empty();
        }
        DeliveryAgent nearest = null;
        double bestDistance = Double.MAX_VALUE;
        for (DeliveryAgent a : available) {
            double distance = DistanceUtil.haversineDistance(
                    restaurant.getLatitude(), restaurant.getLongitude(),
                    a.getLatitude(), a.getLongitude());
            if (distance < bestDistance) {
                bestDistance = distance;
                nearest = a;
            }
        }
        nearest.acceptOrder();
        order.assignAgent(nearest);
        return Optional.of(nearest);
    }
}

class OrderService {
    private final Map<String, Order> orders = new HashMap<>();
    private final Map<String, Cart> carts = new HashMap<>();
    private final DeliveryService deliveryService;

    public OrderService(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    private Cart getOrCreateCart(String userId) {
        return carts.computeIfAbsent(userId, Cart::new);
    }

    public void addToCart(String userId, Restaurant restaurant, String itemId, int quantity) {
        MenuItem item = restaurant.getItem(itemId);
        if (!item.isAvailable()) {
            throw new IllegalStateException("Item " + item.getName() + " is not available");
        }
        Cart cart = getOrCreateCart(userId);
        cart.addItem(restaurant, item, quantity);
    }

    public Order placeOrder(String userId, Restaurant restaurant) {
        Cart cart = getOrCreateCart(userId);
        if (cart.isEmpty()) {
            throw new IllegalStateException("Cart is empty");
        }
        if (!restaurant.isOpen()) {
            throw new IllegalStateException("Restaurant is currently closed");
        }
        List<String> unavailable = new ArrayList<>();
        for (CartItem ci : cart.getItems().values()) {
            if (!ci.getItem().isAvailable()) {
                unavailable.add(ci.getItem().getName());
            }
        }
        if (!unavailable.isEmpty()) {
            throw new IllegalStateException("Unavailable items: " + String.join(", ", unavailable));
        }
        Order order = new Order(
                UUID.randomUUID().toString(),
                userId,
                restaurant,
                new ArrayList<>(cart.getItems().values()),
                cart.total()
        );
        orders.put(order.getOrderId(), order);
        cart.clear();
        return order;
    }

    public void updateOrderStatus(String orderId, OrderStatus newStatus) {
        Order order = getOrder(orderId);
        order.updateStatus(newStatus);
        if (newStatus == OrderStatus.CONFIRMED) {
            Optional<DeliveryAgent> agent = deliveryService.assignAgent(order);
            if (agent.isEmpty()) {
                System.out.println("No agent available for order " + orderId + ". Will retry.");
            }
        }
        if (newStatus == OrderStatus.DELIVERED && order.getDeliveryAgent().isPresent()) {
            order.getDeliveryAgent().get().completeDelivery();
        }
    }

    public void cancelOrder(String orderId) {
        Order order = getOrder(orderId);
        order.cancel();
    }

    public Order getOrder(String orderId) {
        if (!orders.containsKey(orderId)) {
            throw new NoSuchElementException("Order " + orderId + " not found");
        }
        return orders.get(orderId);
    }
}
```

---

## Verification

Trace: User places order, restaurant confirms, agent assigned, delivered.

1. `add_to_cart("u1", restaurant, "item1", 2)` → Cart has 2x Burger ($10 each), total=$20
2. `place_order("u1", restaurant)` → Order(id="o1", status=PLACED), cart cleared
3. `update_order_status("o1", CONFIRMED)` → order.status=CONFIRMED, nearest agent found (agent_a), agent_a.status=BUSY
4. `update_order_status("o1", PREPARING)` → order.status=PREPARING (valid transition)
5. `cancel_order("o1")` → throws IllegalStateException: Cannot cancel in PREPARING
6. `update_order_status("o1", PICKED_UP)` → order.status=PICKED_UP
7. `update_order_status("o1", DELIVERED)` → order.status=DELIVERED, agent_a.status=AVAILABLE

---

## Deep Dive & Extensibility

### 1. "How do you assign the nearest delivery agent?"

Use Haversine distance from the restaurant's location to each available agent's last known location. This runs in O(n) where n = number of available agents.

For production at scale: index agent locations in a geospatial data structure (PostGIS, Redis GEOSEARCH, or a quadtree). Query returns agents within a radius in O(log n + k).

```java
// Redis GEOSEARCH approach (pseudocode):
// On agent location update: GEOADD agents_geo lng lat agent_id
// On assignment: GEOSEARCH agents_geo FROMLONLAT restaurant_lng restaurant_lat
//                BYRADIUS 5 km ASC COUNT 1
```

### 2. "Walk through the order status state machine."

Valid transitions form a DAG:

```
PLACED → CONFIRMED → PREPARING → PICKED_UP → DELIVERED
  ↓           ↓
CANCELLED  CANCELLED
```

Enforced via a transition table (`Map<OrderStatus, Set<OrderStatus>>`). Any invalid transition throws `IllegalStateException` immediately. This is better than a series of if/else because:
- Adding a new status = add one entry to the map
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

```java
public void restaurantGoOffline(String restaurantId) {
    Restaurant restaurant = restaurants.get(restaurantId);
    restaurant.setOpen(false);
    for (Order order : orders.values()) {
        boolean matchesRestaurant = order.getRestaurant().getRestaurantId().equals(restaurantId);
        boolean cancellable = order.getStatus() == OrderStatus.PLACED
                || order.getStatus() == OrderStatus.CONFIRMED;
        if (matchesRestaurant && cancellable) {
            order.cancel();
            // trigger refund and user notification
        }
    }
}
```

### 5. "How would you implement ratings and reviews?"

Add a `Review` entity linked to a completed Order. Enforce one review per order (not per user/restaurant pair — so the user can review every delivery).

```java
class Review {
    private final String reviewId;
    private final String orderId;
    private final String userId;
    private final int restaurantRating;
    private final int agentRating;
    private final String comment;
    private final Instant createdAt;

    public Review(String reviewId, String orderId, String userId,
                  int restaurantRating, int agentRating, String comment) {
        if (!(restaurantRating >= 1 && restaurantRating <= 5
                && agentRating >= 1 && agentRating <= 5)) {
            throw new IllegalArgumentException("Ratings must be 1-5");
        }
        this.reviewId = reviewId;
        this.orderId = orderId;
        this.userId = userId;
        this.restaurantRating = restaurantRating;
        this.agentRating = agentRating;
        this.comment = comment;
        this.createdAt = Instant.now();
    }

    public String getReviewId() { return reviewId; }
    public String getOrderId() { return orderId; }
    public String getUserId() { return userId; }
    public int getRestaurantRating() { return restaurantRating; }
    public int getAgentRating() { return agentRating; }
    public String getComment() { return comment; }
    public Instant getCreatedAt() { return createdAt; }
}

// Inside OrderService (or a dedicated ReviewService):
private final Map<String, Review> reviews = new HashMap<>();

public Review submitReview(String orderId, int restRating,
                            int agentRating, String comment) {
    Order order = getOrder(orderId);
    if (order.getStatus() != OrderStatus.DELIVERED) {
        throw new IllegalStateException("Can only review completed orders");
    }
    if (reviews.containsKey(orderId)) {
        throw new IllegalStateException("Order already reviewed");
    }
    Review review = new Review(UUID.randomUUID().toString(), orderId, order.getUserId(),
            restRating, agentRating, comment);
    reviews.put(orderId, review);
    return review;
}
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
  A: Cart becomes a `Map<String, List<CartItem>>` keyed by restaurant_id. Placing the order creates one Order per restaurant. Delivery assignments are per-order. Checkout total aggregates across all sub-orders.

---

## Related

**Patterns applied here**

- [Observer Pattern](../../03-design-patterns/03-behavioral/observer-pattern.md)
- [State Pattern](../../03-design-patterns/03-behavioral/state-pattern.md)
- [Strategy Pattern](../../03-design-patterns/03-behavioral/strategy-pattern.md)

**SOLID focus**: [Single Responsibility](../../02-solid-principles/01-single-responsibility.md) · [Dependency Inversion](../../02-solid-principles/05-dependency-inversion.md)

**Concurrency**: [Concurrency Patterns](../../04-concurrency/concurrency-patterns.md)

**Practice next**

- [Design Ride-Sharing](../03-domain-specific/22-design-ride-sharing.md)
- [Design Order Management](../03-domain-specific/21-design-order-management.md)

Ride-sharing shares the matching and dispatch model.

**Frameworks**: [LLD Template](../../../07-interview-templates/01-frameworks/02-lld-template.md) · [UML Diagrams](../../01-oop-fundamentals/uml-diagrams.md) · [OOP Four Pillars](../../01-oop-fundamentals/four-pillars.md)
