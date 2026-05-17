# Design an E-Commerce Platform

> **Difficulty**: Medium
> **Topics**: Inventory Management, Flash Sale Handling, Cart, Checkout, Search, Recommendation
> **Time**: 60 minutes
> **Companies**: Amazon, Flipkart, Shopify, Alibaba, eBay, Myntra

---

## Problem Statement

Design an e-commerce platform that:
- Lists products with search and filtering
- Manages a shopping cart
- Handles checkout with inventory reservation
- Processes orders and payments
- Supports flash sales (10M users hitting "Buy" simultaneously on one item)
- Handles 1M DAU normal load, 50M DAU during sales events

---

## Why This Is Hard

1. **Flash sale inventory**: 1 product, 1000 units, 1 million users clicking "Buy" simultaneously. Standard database UPDATE will deadlock. Redis atomic operations + pre-warming solve this.
2. **Cart consistency**: Cart items must reflect real-time availability and pricing. A product in cart might sell out, change price, or go on discount before checkout.
3. **Checkout atomicity**: Payment + inventory deduction + order creation must be atomic — or use saga compensating transactions.
4. **Search at scale**: 100M products, full-text search with faceting, personalized ranking — requires Elasticsearch + ML re-ranking.

---

## Scale Estimation

```
Products: 100M listings
DAU: 1M normal, 50M during events
Product searches: 1M × 20 searches/day = 20M/day = 230 searches/sec (normal)
Flash sale peak: 10M users × 10 actions/min = 1.7M requests/sec
Orders: 500K/day normal, 5M/day during events
Inventory writes: 500K orders × avg 3 items = 1.5M inventory updates/day

Cart:
  1M DAU × 5 items avg = 5M active cart entries
  Store in Redis (ephemeral, user session-scoped) + DB for persistence
```

---

## Architecture

```
           [Search]       [Browse]       [Cart]       [Checkout]
               │              │             │               │
               ▼              ▼             ▼               ▼
          ┌──────────────────────────────────────────────────┐
          │                 API Gateway                       │
          │          (Auth, Rate Limit, Routing)              │
          └──────┬─────────────┬──────────────┬──────────────┘
                 │             │              │
    ┌────────────▼──┐  ┌───────▼──────┐ ┌────▼──────────┐
    │ Search Service│  │ Product Svc  │ │ Cart Service  │
    │ (Elasticsearch│  │ (PostgreSQL/ │ │ (Redis)       │
    │  + ML ranking)│  │  MongoDB)    │ └────┬──────────┘
    └───────────────┘  └──────────────┘      │
                                             ▼
                                    ┌─────────────────┐
                                    │ Checkout Service │
                                    │ (Inventory +     │
                                    │  Payment + Order)│
                                    └─────────┬────────┘
                                              │
                    ┌──────────────────────────┼──────────────────┐
                    ▼                          ▼                  ▼
           ┌───────────────┐        ┌───────────────┐  ┌──────────────┐
           │ Inventory DB  │        │ Orders DB     │  │ Payment GW   │
           │ (PostgreSQL + │        │ (PostgreSQL)  │  │ (Stripe etc) │
           │  Redis cache) │        └───────────────┘  └──────────────┘
           └───────────────┘
```

---

## Product Catalog

```
Two stores:
  MongoDB: flexible product schema (a laptop has RAM, a shirt has size/color)
  Elasticsearch: search index (full-text, faceted filtering, geo search)

MongoDB product document:
{
  "product_id": "p_123",
  "title": "Apple MacBook Pro M3",
  "category": ["Electronics", "Laptops"],
  "brand": "Apple",
  "price": 1999.00,
  "attributes": {
    "RAM": "16GB",
    "Storage": "512GB SSD",
    "Screen": "14 inch",
    "Color": "Space Gray"
  },
  "images": ["https://cdn.example.com/img/p123_1.jpg", ...],
  "rating": 4.8,
  "review_count": 2847
}

Sync MongoDB → Elasticsearch via change streams (CDC):
  On MongoDB write → trigger → index document in Elasticsearch
  Lag: < 5 seconds (acceptable for search)
```

---

## Flash Sale: Redis-Backed Inventory

```
Problem: SQL UPDATE for 1 item at 1M concurrent requests → deadlock hell.

Solution: Redis atomic counter for flash sale inventory

Pre-sale setup (before flash sale starts):
  REDIS: SET "inventory:flash:product_123" 1000  (1000 units available)

On each purchase attempt:
  # Atomic decrement — returns new value, never goes below 0
  remaining = REDIS DECR "inventory:flash:product_123"

  IF remaining >= 0:
    → Allow purchase, create pending order, reserve inventory
  ELSE:
    REDIS INCR "inventory:flash:product_123"  # Undo over-decrement
    → Return "Sold out"

After purchase confirmed (payment succeeded):
  → Write to PostgreSQL inventory table (deduct 1 unit permanently)
  → If payment fails: REDIS INCR (restore the unit)

Why this works:
  Redis DECR is atomic (single-threaded Redis processes one command at a time)
  No race conditions — exactly 1000 people will get remaining >= 0
  Redis handles 1M+ ops/sec — easily handles flash sale concurrency
```

---

## Cart Service

```
Cart is per-user, session-scoped:
  Redis Hash: HSET cart:{user_id} product_id quantity

Cart operations:
  Add item: HSET cart:{user_id} {product_id} {qty}  O(1)
  Remove item: HDEL cart:{user_id} {product_id}
  View cart: HGETALL cart:{user_id}  O(N items)
  Cart TTL: EXPIRE cart:{user_id} 604800  (7 days of inactivity)

On cart view — enrich with real-time data:
  Fetch product details: GET /products/{ids} (batch API)
  Fetch current prices: Redis price cache (TTL 60 sec)
  Check availability: Inventory service
  Flag items: "Price changed since you added", "Only 3 left!", "Out of stock"

Persistent cart (for logged-in users):
  Sync Redis cart → DB on checkout initiation
  On login: merge DB cart with active Redis cart
```

---

## Checkout + Order Flow

```
1. Initiate Checkout
   Validate cart: re-check availability and prices
   Calculate total: items + shipping + tax
   Return: checkout_session_id + final price (10-minute window to complete)

2. Inventory Pre-Reservation (Saga Step 1)
   For each item: reserve_inventory(product_id, qty, checkout_session_id)
   Uses row-level locking:
     UPDATE inventory SET reserved = reserved + qty
     WHERE product_id = ? AND (available - reserved) >= qty
   If insufficient: fail fast, release all reservations, return "Item unavailable"

3. Payment (Saga Step 2)
   POST to payment gateway with idempotency key = checkout_session_id
   On success: payment_id returned

4. Order Confirmation (Saga Step 3)
   BEGIN TRANSACTION
     INSERT INTO orders (order_id, user_id, items, total, payment_id, status='CONFIRMED')
     UPDATE inventory SET available = available - qty, reserved = reserved - qty
   COMMIT

   On failure at Step 3: compensate
     Refund payment (POST /refund with payment_id)
     Release inventory reservations

5. Post-order (async)
   Send confirmation email
   Update analytics
   Trigger fulfillment
```

---

## Product Search

```
Elasticsearch query with facets + ML re-ranking:

Search: "laptop under 1000"
→ Elasticsearch:
  bool query:
    must: multi_match("laptop", fields: [title^3, description, brand])
    filter: range(price: {lte: 1000})
    filter: term(in_stock: true)
→ Returns top 200 candidates with BM25 scores

→ ML re-ranker (LightGBM):
  Features: BM25 score, user's purchase history, product rating,
            conversion rate, click-through rate, recency
  Re-ranks top 200 → returns top 20 for display

Facets sidebar:
  Elasticsearch aggregations:
    terms(brand), range(price: [0-500, 500-1000]),
    terms(rating), terms(category)
  Returns counts per bucket: "Apple (142), Dell (98), HP (67)"
```

---

## Interview Talking Points

**Q: "How do you handle the flash sale stampede?"**
> "Two layers: (1) Queue — put excess requests into a waiting queue, show users 'you're #4,231 in queue' rather than erroring out. (2) Redis atomic counter — as each user reaches the front of the queue, they atomically DECR the Redis inventory counter. If the result is >= 0, they get the item. If < 0, they INCR back and see 'sold out.' This guarantees exactly N users succeed where N is the inventory count, with zero database deadlocks."

**Q: "What if inventory shows available but the item is actually out of stock?"**
> "Cache invalidation is the culprit. Our inventory cache has a 60-second TTL. For flash sales, we bypass the cache entirely (direct Redis counter). For regular items, the cart-to-checkout flow always re-checks live inventory in the reservation step before charging payment. We prefer 'fail at checkout' over 'oversell and disappoint later.' The UX shows 'Sorry, this item sold out while in your cart' which is acceptable."

---

## Interview Questions Asked

### Amazon
1. **"Design Amazon.com checkout."** → Tests distributed transaction and inventory consistency; key answer: reservation-based checkout (reserve → pay → confirm), idempotency keys for payment, saga pattern for rollback on failure.

### Common Follow-ups
1. **"How do you handle a flash sale — 10K items, 1M requests in 1 second?"** → Tests atomic counter design; pre-load inventory into Redis counter; each request does atomic `DECR` — if result >= 0, proceed to payment; if < 0, `INCR` back and return sold-out; queue excess requests with position tracking rather than rejecting.
2. **"How do you handle cart abandonment recovery?"** → Tests async workflow design; TTL-based cart expiry event triggers a Kafka message → email/push worker sends reminder after 1h; re-check inventory availability before sending (item may be sold out); cap at 2 reminders to avoid spam.
3. **"How do you ensure price consistency when a cart is held?"** → Tests snapshot isolation; capture price at `add_to_cart` time and store in cart row; re-validate price at checkout — if price dropped, apply new price (user benefit); if price rose, show user the change and require re-confirmation.
4. **"How does fraud detection work at checkout?"** → Tests ML inference in critical path; rule-based checks (velocity, IP reputation) run synchronously in < 10ms; ML model scores run async and can halt fulfillment if score exceeds threshold post-payment; chargebacks trigger model retraining signals.
5. **"How do you handle partial order failures (3 of 5 items shipped, 2 fail)?"** → Tests saga/compensation; each line item is a separate fulfillment unit; partial fulfillment triggers partial refund via compensating transaction; customer notified per item status; order status machine supports `PARTIAL_FULFILLED` state.
