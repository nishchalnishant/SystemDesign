---
module: 05-hld-problems
topic: Medium
status: unread
tags: [05-hld-problems, system-design, medium, e-commerce, inventory, orders, product-catalog]
---
# Design an E-Commerce Platform (Amazon)

> **Difficulty**: Medium | **Asked at**: Amazon, Flipkart, Shopify, eBay

---

## Problem Statement

Design a large-scale e-commerce platform like Amazon. Users can browse a product catalog, add items to a cart, place orders, and track delivery. Sellers manage inventory and listings. The system must handle flash sales, holiday traffic spikes, and prevent overselling.

---

## Functional Requirements

1. **Product catalog**: Browse and search products by category, keyword, filters (price, rating)
2. **Inventory**: Real-time stock levels; prevent overselling
3. **Cart**: Add/remove items; cart persists across sessions
4. **Orders**: Place order, process payment, track status (PENDING → CONFIRMED → SHIPPED → DELIVERED)
5. **Seller portal**: Sellers list products, manage inventory, view orders
6. **Recommendations**: "Customers also bought" and personalized suggestions

---

## Non-Functional Requirements

- **Scale**: 100M users, 10M orders/day → 115 orders/sec; 1B product views/day → 12K reads/sec
- **Availability**: 99.99% for browsing; 99.9% for checkout (can tolerate brief downtime during maintenance)
- **Consistency**: Order placement must be ACID — no overselling, no double charges
- **Latency**: Product search < 100ms; checkout < 2s P99
- **Peak**: 100× traffic spike during flash sales (Prime Day, Black Friday)

---

## Core Entities

| Entity | Key Fields |
|--------|-----------|
| `Product` | product_id, seller_id, title, description, category, price, images[], attributes |
| `Inventory` | product_id, warehouse_id, quantity, reserved_quantity |
| `Cart` | user_id, items[{product_id, quantity, price_snapshot}], updated_at |
| `Order` | order_id, user_id, items[], status, total, shipping_address, payment_id, created_at |
| `OrderItem` | order_item_id, order_id, product_id, quantity, unit_price |
| `Seller` | seller_id, name, rating, fulfillment_type (FBA / merchant) |

---

## API Design

```http
GET /api/v1/products?q=laptop&category=electronics&min_price=500&sort=rating&page=1
Response 200: { "products": [...], "total": 4820, "page": 1, "facets": { "brands": [...] } }

GET /api/v1/products/{product_id}
Response 200: { "product_id": "...", "title": "...", "price": 999, "in_stock": true, "quantity": 42 }

POST /api/v1/cart/{user_id}/items
Body: { "product_id": "p123", "quantity": 2 }
Response 200: { "cart": { "items": [...], "total": 1998 } }

POST /api/v1/orders
Body: { "user_id": "u456", "cart_id": "c789", "payment_token": "tok_abc", "shipping_address": {...} }
Response 201: { "order_id": "ord123", "status": "CONFIRMED", "estimated_delivery": "2026-07-03" }

GET /api/v1/orders/{order_id}
Response 200: { "order_id": "...", "status": "SHIPPED", "tracking_number": "UPS1234" }
```

---

## High-Level Design

```
Client
  │
  ▼
CDN (static assets, product images)
  │
  ▼
API Gateway / Load Balancer
  │
  ├── Product Service → Elasticsearch (search + browse)
  │                  → PostgreSQL (canonical product data)
  │                  → Redis (hot product cache, 60s TTL)
  │
  ├── Inventory Service → PostgreSQL (ACID, row-level lock on inventory)
  │                     → Redis (inventory count cache, write-through)
  │
  ├── Cart Service → Redis (cart as hash, TTL 7 days)
  │
  ├── Order Service → PostgreSQL (order lifecycle, ACID)
  │                 → Kafka (order events → fulfillment, notifications)
  │
  └── Payment Service → Stripe / internal payment processor
                      → PostgreSQL (idempotent payment records)

Kafka: order-placed → Fulfillment Service → shipping → status updates
       order-placed → Notification Service → email/SMS
```

**Microservices split**: Product, Inventory, Cart, Order, and Payment are separate services. Each has its own database to avoid cross-service coupling. Inventory and Orders need strong consistency (PostgreSQL). Cart prefers availability (Redis).

---

## Deep Dive 1: Inventory Management and Overselling Prevention

**Problem**: 1,000 users simultaneously add the last unit of a flash-sale item to their carts and proceed to checkout. Without careful coordination, all 1,000 might complete their orders — massive overselling.

**Two-phase reservation**:
1. **Reserve** (add to cart): Decrement `reserved_quantity` — soft hold, not committed.
2. **Commit** (place order): Decrement `quantity`. Release `reserved_quantity`. If `quantity < request`: fail, notify user.
3. **Release** (cart abandoned): On cart TTL expiry, restore `reserved_quantity`.

**Atomic inventory decrement** (PostgreSQL):
```sql
UPDATE inventory
SET quantity = quantity - :qty,
    reserved_quantity = reserved_quantity - :qty
WHERE product_id = :pid
  AND warehouse_id = :wid
  AND quantity >= :qty;
-- 0 rows affected → out of stock
```

**Redis pre-filter for flash sales**: Store `available_count` in Redis. On add-to-cart, atomically decrement Redis counter (Lua script). Only requests that pass Redis proceed to the DB. Reduces DB write contention 100×.

```lua
-- Lua: atomic check-and-decrement
local count = tonumber(redis.call('GET', KEYS[1]))
if count and count >= tonumber(ARGV[1]) then
  redis.call('DECRBY', KEYS[1], ARGV[1])
  return 1
end
return 0
```

**Redis → DB consistency**: Redis count is authoritative during the flash sale. After the sale window, a reconciliation job syncs Redis count with the PostgreSQL ground truth.

---

## Deep Dive 2: Product Search and Catalog

**Problem**: A product catalog with 500M SKUs, varying attributes (electronics have specs, clothing has sizes/colors), needs fast full-text search with faceted filters.

**Elasticsearch** is the search layer:
- Index: one document per product with all searchable fields (title, description, brand, category, attributes as nested objects)
- Query: multi-match on title (boosted) + description, filtered by category, price range, availability
- Facets: `agg` on brand, price_range, rating — returned alongside search results for filter UI
- Autocomplete: edge n-gram analyzer on title for prefix matching (typeahead)

**Sync PostgreSQL → Elasticsearch**:
- Write: product updates go to PostgreSQL (source of truth)
- CDC: Debezium reads PostgreSQL WAL → Kafka `product-updates` topic → Elasticsearch indexer consumer
- Lag: < 5 seconds for new products to appear in search

**Image storage**: Product images in S3. CDN (CloudFront) serves images. Images are resized server-side to multiple resolutions (thumbnail, detail, zoom) via Lambda@Edge on upload.

**Price updates**: Price changes are frequent. Store price separately from product attributes. Elasticsearch price field updated in real-time (not batched). Price updates bypass the 5-second CDC pipeline via a direct Elasticsearch PATCH call.

---

## Deep Dive 3: Order Processing and Consistency

**Problem**: Placing an order involves multiple steps: reserve inventory, charge payment, create order record. If any step fails mid-way, the system must not be left in a partial state.

**Saga pattern** (distributed transactions without a 2PC):

```
Order Saga:
  Step 1: Reserve inventory (Inventory Service)
  Step 2: Charge payment (Payment Service)
  Step 3: Create order (Order Service)
  Step 4: Confirm reservation (Inventory Service)

Compensating actions on failure:
  Step 2 fails → Release inventory reservation
  Step 3 fails → Refund payment + Release inventory
```

Each step is idempotent and produces an event to Kafka. A saga orchestrator (stateful process) tracks the saga state and executes compensating actions on failure.

**Idempotency**: Payment Service requires an idempotency key (`order_attempt_id`). Duplicate order submissions (user double-clicks) use the same key → same payment charge → no double billing. PostgreSQL `unique(idempotency_key)` enforces this.

**Order state machine**:
```
PENDING → CONFIRMED → PROCESSING → SHIPPED → DELIVERED
       ↘ PAYMENT_FAILED
       ↘ CANCELLED (before shipping)
              ↘ RETURN_REQUESTED → RETURNED
```
State transitions are events in Kafka. Each service listens to relevant events and updates its own state accordingly.

---

## Interviewer Questions by Level

**Junior**:
- What is overselling and why is it a problem for e-commerce?
- How do you store a user's cart — database or cache? Why?
- What is the order lifecycle from cart to delivery?

**Mid-level**:
- Walk me through the complete checkout flow — what happens between "Place Order" and "Order Confirmed"?
- How do you prevent two users from buying the last item simultaneously?
- How does Elasticsearch stay in sync with the PostgreSQL product catalog?

**Senior**:
- Design the flash sale system for Prime Day — 1M concurrent users, 10K products, inventory exhausted in seconds.
- How do you implement the distributed transaction for order placement using the saga pattern?
- How do you handle the case where payment succeeds but the order record creation fails?
- How would you design a personalized recommendation engine at Amazon scale?
