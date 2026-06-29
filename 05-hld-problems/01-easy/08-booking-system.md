---
module: 05-hld-problems
topic: Easy
status: interview-ready
tags: [05-hld-problems, system-design, easy]
---
# Design a Hotel Booking System

> **Difficulty**: Medium
> **Topics**: Concurrency Control, State Machines, Payment Consistency
> **Time**: 45 min
> **Companies**: Uber, Amazon, Airbnb

---

## Clarifying Questions

1. "Are we booking rooms or seats — fixed inventory per date, or dynamic?"
2. "What's the booking flow — instant confirm or hold-then-pay?"
3. "Do we need search (filters, dates, location) or just the booking transaction itself?"
4. "What payment model — authorize-capture or immediate charge?"
5. "How many concurrent bookings at peak — 100/sec or 100K/sec?"
6. "Do we need cancellation, modification, and refund flows?"

---

## Back-of-Envelope

```
Scale:
  100K hotels, 10M rooms
  500K bookings/day = 6/sec avg, 100/sec peak
  30K searches/sec peak (search >> book ratio)

Availability table size:
  10M rooms x 365 days x 5 years = 18B rows -> 900 GB
  (1 row per room_type per date, not per individual room)

Booking table:
  900M bookings over 5 years x ~200 bytes = ~180 GB

Payment:
  Peak 100 bookings/sec -> 100 payment gateway calls/sec (well within Stripe limits)
```

---

## APIs

```
// Search available hotels
GET /api/v1/hotels?city=NYC&check_in=2024-12-20&check_out=2024-12-23&guests=2
  -> [{ "hotel_id": 42, "name": "...", "available_rooms": 3, "price_per_night": 199.00 }]

// Create booking (PENDING state, payment not yet charged)
POST /api/v1/bookings
  { "hotel_id": 42, "room_type_id": 7, "check_in": "...", "check_out": "...", "num_rooms": 1 }
  -> { "booking_id": "uuid", "status": "PENDING", "total_price": 597.00, "expires_at": "..." }

// Confirm with payment
POST /api/v1/bookings/{id}/confirm
  { "payment_method_id": "pm_..." }
  -> { "booking_id": "uuid", "status": "CONFIRMED", "confirmation_number": "HB-29471" }

// Cancel
POST /api/v1/bookings/{id}/cancel
  -> { "booking_id": "uuid", "status": "CANCELLED", "refund_amount": 597.00 }
```

---

## Architecture

```
Client
  |
  +-- Search --> Search Service (Elasticsearch for geo + filters)
  |                 └── room_inventory (PostgreSQL, read replica)
  |
  └── Book  --> Reservation Service
                  +-- Inventory check + hold (atomic SQL UPDATE)
                  +-- Booking record (PostgreSQL)
                  +-- Payment Service (Stripe authorize -> capture)
                  |     └── Idempotency key per booking
                  └── Notification Service (Kafka -> email/SMS)

State machine:
  PENDING (15-min hold) -> CONFIRMED -> COMPLETED
                       \-> CANCELLED
                       \-> EXPIRED (if hold times out)

Background worker:
  Every 60s: UPDATE bookings SET status='EXPIRED'
  WHERE status='PENDING' AND expires_at < NOW()
  + release inventory (reverse the booked_rooms increment)
```

---

## Data Model

```sql
CREATE TABLE room_inventory (
    room_type_id    BIGINT,
    date            DATE,
    total_rooms     INT,
    booked_rooms    INT DEFAULT 0,
    available_rooms INT GENERATED ALWAYS AS (total_rooms - booked_rooms) STORED,
    price           DECIMAL(10, 2),
    PRIMARY KEY (room_type_id, date),
    CONSTRAINT no_overbooking CHECK (booked_rooms <= total_rooms)
);

CREATE TABLE bookings (
    booking_id      UUID PRIMARY KEY,
    user_id         BIGINT,
    hotel_id        BIGINT,
    room_type_id    BIGINT,
    check_in        DATE,
    check_out       DATE,
    num_rooms       INT,
    status          VARCHAR(20),   -- PENDING/CONFIRMED/CANCELLED/EXPIRED
    total_price     DECIMAL(10, 2),
    payment_id      VARCHAR(64),   -- Stripe PaymentIntent ID
    idempotency_key VARCHAR(64) UNIQUE,
    expires_at      TIMESTAMP,     -- 15 min after creation for PENDING
    created_at      TIMESTAMP DEFAULT NOW(),
    cancelled_at    TIMESTAMP
);
```

**Core anti-double-booking SQL** (single atomic statement):
```sql
UPDATE room_inventory
   SET booked_rooms = booked_rooms + :num_rooms
 WHERE room_type_id = :room_type_id
   AND date BETWEEN :check_in AND :check_out - 1
   AND (booked_rooms + :num_rooms) <= total_rooms;
-- rows_affected = 0 means sold out
```

---

## Key Design Decisions

**1. Atomic conditional UPDATE (not SELECT then INSERT)**
Naive: `SELECT available_rooms ... IF available > 0 THEN INSERT booking`. Between the SELECT and INSERT, another transaction can decrement availability → double-booking. Fix: single `UPDATE room_inventory SET booked_rooms = booked_rooms + N WHERE (booked_rooms + N) <= total_rooms`. If `rows_affected = 0`: sold out. The `CHECK` constraint is a last-resort safety net. No application-level lock needed.

**2. PENDING → CONFIRMED state machine with 15-min hold**
User submits booking → inventory is decremented immediately (hold placed). User has 15 minutes to complete payment. If payment succeeds: status → CONFIRMED. If timeout or failure: background worker sets status → EXPIRED and reverses the inventory increment. This gives a good UX (room feels secured) without losing inventory indefinitely.

**3. Authorize-then-capture payment flow**
Authorize at PENDING creation: Stripe places a hold on the card but doesn't charge. If the DB booking record fails after authorization: release the authorization (no charge). If DB succeeds and user confirms: capture (settle) the authorization. This solves the "charged but not booked" inconsistency. Outbox pattern: the booking service writes a `capture_payment` task to an outbox table inside the same DB transaction as the booking record insert.

**4. Idempotency key for double-click protection**
Client generates a UUID on the first "Book" click. On duplicate click (network retry, accidental double-tap): same UUID is sent. Server checks `bookings(idempotency_key)` — if found, return cached result. Not found: process and store. Key expires after 24 hours. Same key also sent to Stripe — prevents double-charging even if the server retries the Stripe call.

---

## Deep Dives

**Payment timeout handling**
Stripe authorize call times out. Never assume timeout = failure — timeouts mean unknown. Always query Stripe's status endpoint with the idempotency key to get the actual outcome. If charged but not confirmed in DB: confirm the booking via outbox retry. If not charged: release hold. Charging twice is worse than a brief delay.

**Elasticsearch for availability search**
PostgreSQL is availability truth; Elasticsearch is for fast search across 100K hotels with geo + filters. Search returns candidate hotel_ids; Reservation Service confirms actual availability against PostgreSQL. Slight staleness in search results is acceptable — handle "no longer available" gracefully at booking time.

---

## Failure Scenarios

| Failure | Impact | Mitigation |
|---------|--------|------------|
| DB write fails after payment authorized | Charged but no booking | Outbox + capture-settle pattern; authorization released if no booking found |
| Hold expiry worker crashes | Expired holds stay, inventory locked | Worker is idempotent; restart resumes scan from last processed expires_at |
| Double-click submits two bookings | Potential double-charge | Idempotency key at API layer; Stripe idempotency key prevents double-charge |
| Stripe webhook arrives before DB committed | Race: can't find booking | Store webhook event; retry after 30s via delayed queue |
| Search shows available room that just sold out | User gets "no longer available" | Redirect to search with error; Elasticsearch eventually consistent with DB |

---

## Interview Questions Asked

### Uber
1. **"Design trip booking with real-time driver matching — how do you prevent two riders from being matched to the same driver?"** → Optimistic locking on driver state: driver record has a `status` field (AVAILABLE/MATCHED/ON_TRIP) and a `version` column. Matching service does `UPDATE drivers SET status='MATCHED', version=version+1 WHERE driver_id=X AND status='AVAILABLE' AND version=N`. Only one concurrent request can win the CAS; losers retry with a different driver. No distributed lock needed.
2. **"How does Uber handle surge pricing in the booking flow — where does it sit architecturally?"** → Surge multiplier is computed by a separate Pricing Service that runs every 30 seconds based on supply/demand ratio per geohash cell. The multiplier is cached in Redis. Booking service reads surge multiplier at trip-request time and embeds it in the fare estimate. The multiplier is locked in at booking time — rider sees no price change after accepting.

### Amazon
1. **"Design hotel booking with inventory locking — how do you prevent overbooking during a flash sale?"** → Database-level constraint: `CHECK (booked_rooms <= total_rooms)` + atomic `UPDATE room_inventory SET booked_rooms = booked_rooms + 1 WHERE booked_rooms < total_rooms`. If rows_affected = 0, inventory is exhausted. For flash sales with extreme concurrency: queue bookings via a FIFO queue (SQS FIFO), process sequentially per room type — eliminates lock contention at the cost of latency.
2. **"How would you design the confirmation email / notification pipeline so it doesn't block the booking response?"** → Async: booking service publishes a `booking_confirmed` event to Kafka/SQS after the DB commit. Notification service consumes the event and sends email/SMS. The HTTP response returns to the user immediately after DB commit — no waiting for email delivery. Idempotent consumer ensures duplicate events don't send duplicate emails.

### Common Follow-ups
1. **"How do you prevent overbooking when two users submit simultaneously for the last available room?"** → The atomic conditional UPDATE is the core mechanism — it's a single SQL statement with the constraint in the WHERE clause, not a SELECT then UPDATE. The DB's row-level lock serializes concurrent updates to the same row; only one succeeds. This is more efficient than an application-level distributed lock.
2. **"A user clicks 'Book' twice in rapid succession — how do you prevent a double charge?"** → Idempotency key: client generates a UUID on the first click and sends it with the request header (`Idempotency-Key: <uuid>`). Server stores the key with the result. Second request with the same key returns the cached result without re-processing. Key expires after 24 hours.
3. **"How does Airbnb handle calendar blocking — a host blocks dates, then a guest tries to book the same dates?"** → Calendar blocks and bookings share the same `availability` table. Both operations do the same atomic check: `UPDATE availability SET status='BLOCKED' WHERE date IN (...) AND status='AVAILABLE'`. The host's block and the guest's booking compete for the same rows — first writer wins, second gets rows_affected=0 and fails gracefully.
4. **"How do you handle a payment timeout — the charge request to Stripe times out. Did it go through or not?"** → Query the payment gateway's status endpoint with the idempotency key to retrieve the outcome. If it went through: confirm the booking. If it failed: release the hold. If still pending: poll with exponential backoff. Never assume timeout = failure — timeouts mean unknown, and charging twice is worse than a brief hold delay.

---

## Interviewer Follow-Up Questions

**On concurrency and locking:**
- "You use a DB row lock to prevent double-booking. What if 10K users simultaneously try to book the last room?" → All 10K requests serialize at the DB row lock — only one proceeds, the rest wait. Under very high contention, this causes lock queuing and DB CPU spike. Better: optimistic locking with version column — 10K reads succeed (no lock), one UPDATE wins, 9,999 get `rows_affected=0` and fail fast without waiting. Optimistic locking is better under high contention because failures are instant, not queued.
- "Your booking hold lasts 10 minutes. What if the user's browser crashes at minute 9?" → The hold has a `hold_expires_at` timestamp in the DB. A background worker scans for expired holds every minute and releases them (`UPDATE availability SET status='AVAILABLE' WHERE hold_expires_at < NOW() AND status='HELD'`). The worker must handle idempotency — running twice on the same row is fine because it only updates rows still in HELD status.
- "Why is SELECT ... FOR UPDATE better than application-level distributed locks (Redis SETNX) for booking?" → DB-level locking is transactional — the lock is automatically released if the transaction rolls back (crash, timeout, exception). Redis locks require explicit release; a crash before DEL means the lock stays until TTL expiry. DB transactions have ACID rollback guarantees that Redis doesn't.

**On payment failures:**
- "The payment succeeds but the DB write to confirm the booking fails. What happens?" → Charged but not confirmed. Fix with the outbox pattern: within the same DB transaction, write both the booking record AND an outbox event. If the DB transaction fails, use authorize-then-capture: capture (settle) only after DB confirm succeeds. If DB fails, release the authorization — no charge to the user.
- "How do you handle the Stripe webhook arriving before your DB has committed the booking?" → Stripe sends `payment_intent.succeeded` asynchronously. Webhook handler queries DB for the booking by `payment_intent_id`. If not found (race condition): store the webhook event in a `webhook_events` table and retry after 30 seconds. Never drop webhook events. Idempotency: if the same event arrives twice, the second processing is a no-op.

**On scale and search:**
- "How do you search available rooms across 10M properties for given dates without a full table scan?" → Maintain a separate `availability_calendar` table with one row per (property_id, date, status). Index on (date_range, status, location). For a search query, find all property_ids where ALL dates in the range are AVAILABLE. Alternatively: Elasticsearch with a pre-indexed availability bitmap per property.
- "At peak (holiday season), your booking service handles 50K concurrent users. What components fail first?" → Likely the DB write path — booking inserts hit the primary DB. Scale with: connection pooling (PgBouncer), partition the `bookings` table by property_id or date, DB read replicas for search queries. The availability check and payment are the two serialization points — instrument them with histograms to find where latency spikes first.
