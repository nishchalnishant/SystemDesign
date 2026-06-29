---
module: 05-hld-problems
topic: Medium
status: interview-ready
tags: [05-hld-problems, system-design, medium]
---
# Design an E-Commerce Platform

> **Difficulty**: Medium
> **Topics**: Inventory Management, Flash Sales, Cart, Checkout, Fraud
> **Time**: 45 min
> **Companies**: Amazon, Common

---

## Clarifying Questions

1. "Are we designing the full platform (catalog + cart + checkout) or one component?"
2. "What's the scale — 1M DAU normal, 50M during Prime Day?"
3. "Do we need flash sale support (inventory spike to zero in seconds)?"
4. "What's the product catalog size — 100M SKUs? That affects search indexing."
5. "What payment model — authorize-capture or immediate charge?"
6. "Do we need fraud detection, or is that a separate service?"

---

## Back-of-Envelope

```
Normal: 1M DAU, 100M products
  Search: 1M × 5 searches/day / 86,400 = ~58 searches/sec
  Orders: 500K orders/day = 6/sec avg, 50/sec peak

Flash sale (Prime Day):
  50M DAU, 100K orders/min at peak = 1,667 orders/sec
  Inventory: popular item may have 10K units, 500K users trying to buy
  Redis DECR must handle 500K req/sec on a hot key → cluster sharding required

Storage:
  Product catalog: 100M products × 5KB = 500 GB → MongoDB (flexible schema)
  Orders: 500K/day × 365 × 500 bytes = ~90 GB/year → PostgreSQL
  Search index: Elasticsearch (100M documents, ~1KB each = 100 GB indexed)
```

---

## APIs

```
// Search products
GET /api/v1/search?q=laptop&category=electronics&price_max=1000&page=1
  -> { "products": [...], "total": 15000, "facets": { "brand": {...} } }

// Get product
GET /api/v1/products/{product_id}
  -> { "product_id": "...", "name": "...", "price": 999.99, "inventory": 47 }

// Cart operations
POST /api/v1/cart/items
  { "product_id": "...", "quantity": 2 }
  -> { "cart_id": "...", "items": [...], "total": 1999.98 }

// Checkout
POST /api/v1/orders
  { "cart_id": "...", "payment_method_id": "pm_..." }
  -> { "order_id": "...", "status": "pending", "total": 1999.98 }

// Order status
GET /api/v1/orders/{order_id}
  -> { "order_id": "...", "status": "confirmed", "estimated_delivery": "..." }
```

---

## Architecture

```
Client
  |
  +-- Search  --> Search Service
  |                +-- Elasticsearch (BM25 + filters + facets)
  |                +-- Product metadata from MongoDB (by product_id)
  |
  +-- Product --> Product Service
  |                +-- MongoDB (product catalog, flexible schema)
  |                +-- Redis cache (TTL 5min for hot products)
  |
  +-- Cart    --> Cart Service
  |                +-- Redis Hash: HSET cart:{user_id} {product_id} {qty,price_at_add}
  |                +-- TTL 7 days; persisted to PostgreSQL on checkout
  |
  +-- Checkout--> Order Service (Saga)
  |                +-- Step 1: DECR flash_inventory:{product_id} in Redis (pre-check)
  |                +-- Step 2: INSERT order (status=PENDING) + reserve inventory in DB
  |                +-- Step 3: Call Payment Service (Stripe authorize)
  |                +-- Step 4: Confirm order (status=CONFIRMED) + capture payment
  |                     Compensate: INCR inventory + cancel order on any failure
  |
  +-- Fraud   --> Fraud Service (async)
                   +-- Rule engine: < 10ms sync score at checkout init
                   +-- ML model: async score, result in Redis for Pay click
                   +-- Chargeback triggers model retraining pipeline
```

---

## Data Model

```sql
-- Orders (PostgreSQL)
CREATE TABLE orders (
    order_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         BIGINT NOT NULL,
    status          VARCHAR(20) NOT NULL,  -- PENDING/CONFIRMED/SHIPPED/DELIVERED/CANCELLED
    items           JSONB NOT NULL,        -- [{product_id, name, price, qty}]
    subtotal        DECIMAL(10,2),
    tax             DECIMAL(10,2),
    total           DECIMAL(10,2),
    payment_id      VARCHAR(64),
    idempotency_key VARCHAR(64) UNIQUE,
    fraud_score     DECIMAL(5,4),
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX ON orders(user_id, created_at DESC);
CREATE INDEX ON orders(status, created_at DESC);

-- Inventory (PostgreSQL)
CREATE TABLE inventory (
    product_id      VARCHAR(64) PRIMARY KEY,
    total_stock     INT NOT NULL DEFAULT 0,
    reserved_stock  INT NOT NULL DEFAULT 0,
    available_stock INT GENERATED ALWAYS AS (total_stock - reserved_stock) STORED,
    CONSTRAINT no_oversell CHECK (reserved_stock <= total_stock)
);
```

**MongoDB product document** (flexible schema for varying attributes):
```json
{
  "product_id": "p123",
  "name": "MacBook Pro 14",
  "category": "laptops",
  "price": 1999.99,
  "brand": "Apple",
  "attributes": {
    "ram_gb": 16,
    "storage_gb": 512,
    "cpu": "M3 Pro"
  },
  "images": ["s3://..."],
  "created_at": "2024-01-15T00:00:00Z"
}
```

---

## Key Design Decisions

**1. Redis atomic DECR for flash sale inventory**
Before touching the DB, check Redis: `DECR flash_inventory:{product_id}`. If result >= 0: proceed to DB reservation. If result < 0: `INCR flash_inventory:{product_id}` (rollback) and return "sold out." This prevents the DB from ever seeing an oversell scenario. Pre-load: 5 minutes before flash sale, copy inventory count from DB to Redis `SET flash_inventory:{product_id} 10000`. Redis handles 500K DECR/sec per node; DB sees only confirmed reservations (~1,667/sec).

**2. Checkout Saga with compensating transactions**
Multi-step checkout that can fail at any step: (1) DECR Redis inventory, (2) INSERT order PENDING + reserve DB inventory, (3) Stripe authorize, (4) UPDATE order CONFIRMED + capture payment. If step 3 fails: compensate by INCR Redis inventory + DELETE order. If step 4 fails: release Stripe authorization + revert reservation. The Saga orchestrator stores each step's state in an `order_saga_state` table so crashes are recoverable.

**3. MongoDB for product catalog (flexible schema)**
A laptop has RAM/CPU/storage attributes. A shirt has size/color/material. These are fundamentally different schemas. Relational DB requires an EAV (Entity-Attribute-Value) table — complex queries, poor performance. MongoDB document model: each product stores its attributes as a flexible JSON object. Category-specific indexes (`db.products.createIndex({"attributes.ram_gb": 1})` for laptop search). Elasticsearch synced via CDC for full-text search with faceting.

**4. Cart in Redis with price snapshot**
`HSET cart:{user_id} {product_id} {json: {qty, price_at_add, product_name}}`. TTL 7 days. Price captured at add-to-cart time prevents confusion when price changes before checkout (show "price changed" warning). Cart recovery: if Redis key evicted, rebuild from `cart_items` PostgreSQL table (written on checkout). Guest cart: keyed by session_id, merged with user cart on login.

---

## Deep Dives

**Elasticsearch sync from MongoDB**
Product catalog changes (new products, price updates) need to be reflected in search. CDC pipeline: MongoDB Change Streams → Kafka → Elasticsearch indexer. Lag: ~5s acceptable for catalog updates. For flash sale price changes: write directly to Elasticsearch and MongoDB simultaneously (dual-write in the Product Service). Price in search results is from Elasticsearch; price at checkout is always read from the authoritative MongoDB document.

**Fraud detection architecture**
Two-phase: (1) Sync rule engine at checkout initiation — checks velocity rules (>3 orders/hour from same IP, new account + high-value order, shipping address mismatch). Returns fraud_score in <10ms. (2) Async ML model runs within 200ms — features: user history, device fingerprint, network, purchase pattern. Result stored in Redis `fraud_result:{session_id}` TTL 10min. At Pay button click: read cached ML score from Redis. If combined score > threshold: challenge (3DS) or decline. Chargeback data feeds daily model retraining.

---

## Failure Scenarios

| Failure | Impact | Mitigation |
|---------|--------|------------|
| Redis inventory DECR below zero | Oversell if not caught | DECR returns new value; check if >= 0; if negative: INCR rollback + sold out |
| Payment succeeds but DB order fails | Charged but no order | Outbox pattern: Stripe capture only after DB confirm; saga compensates |
| Elasticsearch lag | Search shows stale price | Always show real-time price from MongoDB on product page; Elasticsearch for discovery only |
| Flash sale Redis key eviction | Inventory count lost | High-priority Redis keyspace (no eviction on inventory keys); TTL set to 2 hours |
| Cart Redis eviction | User's cart disappears | Sync cart to PostgreSQL `cart_items` on every add/remove; Redis is just the fast layer |

---

## Interview Questions Asked

### Amazon
1. **"How do you handle inventory for a Prime Day flash sale where 500K users hit 'Buy' within the first 10 seconds for a product with 10K units?"** → Pre-load Redis: `SET flash_inventory:product_id 10000` 5 minutes before sale. On buy: `DECR flash_inventory:product_id`. If result >= 0: reserve in DB. If result < 0: rollback INCR + "sold out" to user. Only 10K DB reservation writes occur (the successful ones), not 500K. Redis DECR is atomic — no two users can both get count >= 0 for the same decrement that would oversell. For extra protection: DB CHECK constraint `reserved_stock <= total_stock` as a belt-and-suspenders.
2. **"How does Amazon's recommendation engine determine 'Customers who bought X also bought Y'?"** → Collaborative filtering: build a co-purchase matrix (product_A, product_B, co_purchase_count). Updated daily via batch MapReduce job over order history. Online serving: given product_id → lookup co-purchase table → return top 10 by count. Real-time signals (current session items) update a separate "session-based" recommendation using a lightweight matrix factorization model. System design: Spark for batch, Redis for serving the co-purchase matrix in memory, online model served via TensorFlow Serving.

### Common Follow-ups
1. **"A user adds an item to their cart, waits 2 hours, then checks out. The price changed. What do you show?"** → Cart stores `price_at_add`. At checkout render: fetch current price from MongoDB for each item, compare with `price_at_add`. If different: show "Price changed: was $X, now $Y" — require user to acknowledge before proceeding. If price dropped: show "Great news, price decreased!" If increased: user may abandon cart (acceptable). Always charge `current_price` at checkout, not `price_at_add` — stored price is for display, not for billing.
2. **"How do you prevent inventory from going negative if two users simultaneously buy the last item?"** → Three layers: (1) Redis `DECR` pre-check (first line, handles 99.9% of cases). (2) DB atomic reservation: `UPDATE inventory SET reserved_stock = reserved_stock + qty WHERE product_id = X AND (reserved_stock + qty) <= total_stock`. Returns rows_affected=0 on oversell — rollback. (3) DB CHECK constraint `reserved_stock <= total_stock` as a hard stop. The combination guarantees no oversell even if Redis state is lost.
3. **"Your checkout saga fails halfway. How do you recover?"** → Saga state machine persisted to `order_saga_state` table: each step is recorded as PENDING/COMPLETED/FAILED with the result (e.g., `stripe_auth_id`). On recovery (worker restart): query all PENDING sagas → resume from last completed step. Compensation: if a step fails, run compensating transactions in reverse order (release Stripe auth → INCR inventory). Idempotent steps: the same Stripe authorize call with the same idempotency_key returns the existing auth result instead of double-charging.

---

## Interviewer Follow-Up Questions

**On inventory and flash sales:**
- "What if Redis goes down during a flash sale?" → Fallback to database-only mode: remove the Redis pre-check, go directly to DB reservation with the atomic conditional UPDATE. Throughput drops from 500K/sec to ~5K/sec (DB limit). Queue excess requests in SQS FIFO — serialize checkout. Show users a "high demand" message with estimated wait time. This is the graceful degradation path: slower but correct. Pre-warm Redis replicas with sentinel; failover is < 30s for read replicas (automatic). Redis Cluster makes single-node failure transparent if configured with replicas.
- "How do you handle the 'add to cart then checkout later' pattern for limited inventory?" → Cart reservation is soft (no inventory hold). Inventory check happens at checkout, not at add-to-cart. This means the user can add item to cart but may find it sold out at checkout — industry standard (Amazon, Walmart do this). Alternative: hard hold at add-to-cart (inventory decremented) with 30-min TTL. Complex to implement, hurts conversion if users abandon carts. Only used for extremely limited items (concert tickets, pre-orders) where even soft reservations cause user frustration.

**On search:**
- "How do you rank 100M products for a search query 'blue running shoes'?" → Elasticsearch BM25 base score (term frequency × inverse document frequency across name, description, category). Boosted by: (a) `function_score`: multiply BM25 by sales rank (more sold = better product), (b) filter for user's price range + size availability, (c) personalization score (user previously bought Nike → boost Nike results), (d) promoted products (paid placements, capped at 3 per page). Result: relevance × popularity × personalization. A/B test ranking formula changes using bucket experiment system.
- "A user searches for 'iPhone' but we call it 'Apple iPhone 15 Pro Max'. How does search find it?" → Elasticsearch synonym expansion: at index time and query time, expand "iPhone" → ["iPhone", "Apple iPhone"]. Elasticsearch query analyzer applies the synonym filter. Additionally: n-gram tokenization for partial matches ("iPho" → "iPhone"). Product names are normalized at index time (lowercase, strip punctuation). User query is analyzed the same way: "iphone" → match "Apple iPhone 15 Pro Max" via token overlap. For edge cases (misspellings): fuzziness parameter in Elasticsearch (edit distance 1 or 2).
