> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design an e-commerce platform (Amazon) — product catalog, inventory management, cart, checkout, payment, and order lifecycle with flash sale support.
>
> **Key design decisions:**
> - Product catalog: Elasticsearch for full-text search + faceted filtering; PostgreSQL for canonical product data; CDN for product images
> - Inventory: inventory DB with optimistic locking (version field + CAS); reserve on add-to-cart, confirm on checkout; 30-min hold TTL
> - Cart service: Redis for active cart (TTL = 30 days); cart is eventually consistent; don't put cart in main order DB
> - Checkout flow: cart → inventory reservation → payment → order creation; each step is idempotent; compensating transactions on failure
> - Payment: async payment via Stripe/Braintree; payment service publishes Kafka event on success → order confirmed; idempotency key prevents double-charge
> - Flash sales: inventory counter in Redis (atomic DECR); actual DB inventory updated async; Redis acts as distributed semaphore
> - Order history: Cassandra for order events (order_id by user_id); immutable event log; read by user timeline
>
> **Key takeaway:** Flash sales require Redis atomic DECR for inventory (not DB) — DB cannot handle 100K concurrent reservation attempts; Redis holds the semaphore, DB is eventually consistent.

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

> 🎯 **Staff signal:** The senior insight is separating the *reservation* from the *commit* — a soft `reserved_quantity` hold on add-to-cart, with a TTL that auto-restores on abandonment, so a full cart never permanently strands inventory the way a naive decrement-at-checkout does. The flash-sale extension is temporarily *moving the source of truth into Redis*: an atomic Lua check-and-decrement sheds 99% of doomed requests before they reach Postgres, and you reconcile back to the DB after the window. Name what you're trading — during the sale Redis is authoritative and the DB is eventually reconciled, which is only safe because oversell-by-one is recoverable and DB write-contention on one hot row is not. Consciously relocating the consistency boundary for the duration of the spike is the E5→E6 framing.

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

> 🎯 **Staff signal:** The move is treating CDC (Postgres WAL → Debezium → Kafka → ES indexer) as the *default* sync path but then recognizing where its ~5s lag is unacceptable and cutting a fast lane around it. A stale product description for 5 seconds is invisible; a stale *price* is a legal and trust problem — you'd honor a price you no longer offer — so price updates get a direct ES PATCH that bypasses the pipeline. Name the general principle: the source-of-truth stays Postgres, but you tier your propagation latency by the *business cost of staleness per field*, not one SLA for the whole document. Splitting one entity's fields across two freshness paths by consequence-of-staleness is the E5→E6 line.

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

> 🎯 **Staff signal:** The senior answer is naming *why not 2PC*: an order spans Inventory, Payment, and Order services, and a two-phase commit would hold locks across a payment call (300ms+) and turn the payment processor into a participant in your transaction — unacceptable coupling and latency. The saga replaces atomicity with *compensations*: each step commits locally and, on downstream failure, an orchestrator runs the inverse (release inventory, refund payment). The load-bearing detail is that every step must be idempotent — the payment `idempotency_key` enforced by a `UNIQUE` constraint is what makes a double-click or a retry safe. Choosing eventual consistency with compensations over distributed locking, and knowing idempotency is the price of admission, is the E5→E6 framing.

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

---

## Back-of-Envelope Estimation

**Scale inputs (given in NFRs):**
- 100M users; 10M orders/day; 1B product views/day; 100× traffic spike during flash sales; checkout < 2s P99

**Read throughput:**
- 1B product views/day ÷ 86,400 sec = **~11,574 reads/sec** average
- Peak (flash sale starts): 100× = **~1.16M product page reads/sec**
- Each product page: product metadata (~2 KB) + inventory count (~50 bytes) + reviews summary (~1 KB)
- CDN serves product metadata (static, changes < once/hour): handles 99% of 1.16M/sec peak
- Inventory count (changes every second during flash sale): cannot be cached; **~11,574 inventory reads/sec** during normal operations, **~1.16M/sec** during flash sale

**Write throughput (normal):**
- 10M orders/day ÷ 86,400 sec = **~115 orders/sec** average
- Each order write touches: orders table (1 row), inventory table (decrement), payments (1 record) = 3 DB writes/order
- 115 × 3 = **~345 DB writes/sec** — trivial for PostgreSQL

**Flash sale write throughput:**
- 100× traffic: **11,500 orders/sec** peak; 3 DB writes each = **34,500 writes/sec**
- Inventory decrement at 11,500/sec for the same SKU → extreme lock contention on one row
- Redis `DECR inventory:{sku_id}` is atomic, in-memory, single-threaded: handles 1M ops/sec → **11,500 DECR/sec** is 1.15% of Redis capacity

**Inventory storage:**
- 10M SKUs × 3 warehouses × 50 bytes = **~1.5 GB** for all inventory in Redis (fits in one instance)
- DB mirrors Redis state (eventual consistency, synced every second) for durability

**Order storage:**
- 10M orders/day × 365 days × 500 bytes/order = **~1.8 TB/year** — partitioned PostgreSQL handles this easily

**Product catalog storage:**
- 1M products × 5 KB per product (metadata + images indexed) = **~5 GB** product catalog
- Elasticsearch index for search: 1M products × 2 KB index size = **~2 GB** — fits in one ES node's heap

**Architecture decisions driven by these numbers:**
- **Redis for inventory reservation during flash sales**: At 11,500 order attempts/sec for a single SKU, a PostgreSQL `UPDATE inventory SET quantity = quantity - 1 WHERE sku_id = ? AND quantity > 0` causes a row-level lock held for ~5ms per transaction. Queue depth: 11,500 × 5ms = **57.5 concurrent lock-waits** → P99 latency spikes to seconds, killing the < 2s checkout SLA. Redis `DECR inventory:{sku_id}` (atomic, 0.1ms, no locking) handles 11,500/sec with P99 < 1ms. The DB is only written on confirmed purchase (Saga: reserve in Redis → charge card → confirm in DB).
- **CDN for product catalog, dynamic inventory API for stock counts**: Product metadata (title, images, description) changes at most once/day. CDN with 24h TTL serves 1.16M product page loads/sec during flash sale at near-zero origin cost. Inventory counts change every millisecond during a flash sale — must hit the backend (Redis, not DB) for accuracy. Separating these two concerns means CDN absorbs 99% of the flash sale read surge, and only inventory+order writes hit the backend.
- **Flash sale event bus via Kafka**: At 100× surge, 1.16M simultaneous requests hitting checkout within milliseconds is a thundering herd. Kafka queues the order requests; a checkout consumer processes them at a controlled rate (matching inventory processing capacity). Users see "your order is in queue" rather than a 503 error. This also prevents the inventory DECR from going negative during race conditions at system boundaries.

---

## Related

**Concepts used in this design**

- [Saga Pattern](../../09-patterns/01-data-consistency/03-saga-pattern.md)
- [Outbox Pattern](../../09-patterns/01-data-consistency/01-outbox-pattern.md)
- [CQRS & Event Sourcing](../../09-patterns/02-architecture-and-scaling/01-cqrs-event-sourcing.md)
- [Sharding](../../02-building-blocks/03-data-partitioning/01-sharding.md)
- [Circuit Breaker](../../02-building-blocks/02-performance/03-circuit-breaker.md)

**Practice next**

- [Payment System](../03-hard/payment-system.md)
- [Hotel Booking](../03-hard/hotel-booking.md)

Checkout hands off to the payment system; inventory mirrors booking.

**Frameworks**: [HLD Template](../../07-interview-templates/01-frameworks/01-hld-template.md) · [Capacity Estimation](../../07-interview-templates/02-cheat-sheets/02-capacity-estimation.md) · [Trade-offs Cheat Sheet](../../07-interview-templates/02-cheat-sheets/01-trade-offs-cheat-sheet.md)
