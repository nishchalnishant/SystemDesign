# Design a Hotel/Flight Booking System

> **Difficulty**: Easy-Medium
> **Topics**: Inventory Management, Optimistic Locking, Overbooking, Idempotency, Two-Phase Reservation
> **Time**: 45 minutes
> **Companies**: Booking.com, Expedia, Airbnb, MakeMyTrip, OYO, Cleartrip

---

## Problem Statement

Design a hotel booking system that:
- Lists available rooms for a given date range
- Allows users to book rooms (preventing double-booking)
- Manages cancellations and refunds
- Supports 100K hotels, 10M rooms, 50M DAU
- Handles race conditions during peak booking periods (New Year's Eve, holiday weekends)

---

## Analogy

A concert ticket counter. Two fans arrive simultaneously for the last ticket. Only one can get it. The box office uses a numbered token system — whoever gets token #1 gets the ticket; #2 is told it's sold out. In software, this "token" is a database row-level lock. The critical insight: holding the lock for the entire "browse → fill form → pay" workflow (30 seconds) would make the system unusable. Instead, we hold locks only for the milliseconds of the actual booking transaction.

---

## Why This Is Hard

1. **Concurrency on hot inventory**: A hotel in Paris on New Year's Eve has 200 rooms. 5,000 users browse it simultaneously. All 5,000 see "Available." When 5,000 try to book at midnight, you need exactly 200 to succeed and 4,800 to fail gracefully — without database deadlocks or overselling.
2. **Inventory vs booking gap**: Users browse availability (read), fill in details (delay), then pay (write). The room can become unavailable during this gap. The system must handle this gracefully (re-check at payment time).
3. **Payment atomicity**: A room must not be marked "booked" unless payment succeeds, and payment must not be charged unless the room is booked. These are in different systems (booking DB vs payment gateway). Requires careful state machine design.
4. **Overbooking strategies**: Airlines deliberately oversell because ~5% of passengers don't show up. Hotels sometimes also oversell. The system must support configurable overbooking levels while tracking actual occupancy.

---

## Scale Estimation

```
Hotels: 100K worldwide
Rooms: 10M total (100 rooms avg per hotel)
DAU: 50M users
Peak bookings: 500K bookings/day → 6 bookings/sec avg, 100 bookings/sec peak

Availability searches:
  50M DAU × 5 searches/day = 250M searches/day = 3K searches/sec avg
  Peak: 30K searches/sec (holiday weekend)

Storage:
  Room availability table: 10M rooms × 365 days × 5 years = 18B rows
  Per row: 50 bytes → 900GB (manageable, heavily cached)
  Bookings: 500K/day × 365 days × 5 years = 900M bookings → 200GB

Cache:
  Hot hotels (top 1% by search volume = 1K hotels) → Redis cache
  99% of availability reads served from cache
```

---

## Database Schema

```sql
-- Hotels and room types
CREATE TABLE hotels (
    hotel_id   BIGINT PRIMARY KEY,
    name       VARCHAR(255),
    city       VARCHAR(100),
    country    CHAR(2),
    rating     DECIMAL(2, 1),
    lat        DECIMAL(9, 6),
    lng        DECIMAL(9, 6)
);

CREATE TABLE room_types (
    room_type_id BIGINT PRIMARY KEY,
    hotel_id     BIGINT REFERENCES hotels(hotel_id),
    name         VARCHAR(100),   -- "Deluxe Double", "Suite"
    max_guests   INT,
    base_price   DECIMAL(10, 2),
    total_rooms  INT             -- Physical inventory count
);

-- Inventory: one row per (room_type, date)
CREATE TABLE room_inventory (
    room_type_id    BIGINT,
    date            DATE,
    total_rooms     INT,      -- Physical rooms
    booked_rooms    INT DEFAULT 0,
    available_rooms INT GENERATED ALWAYS AS (total_rooms - booked_rooms) STORED,
    price           DECIMAL(10, 2),  -- Dynamic pricing
    PRIMARY KEY (room_type_id, date),
    CONSTRAINT no_overbooking CHECK (booked_rooms <= total_rooms)
);

-- Bookings
CREATE TABLE bookings (
    booking_id     UUID PRIMARY KEY,
    user_id        BIGINT,
    hotel_id       BIGINT,
    room_type_id   BIGINT,
    check_in       DATE,
    check_out      DATE,
    num_rooms      INT,
    status         ENUM('PENDING', 'CONFIRMED', 'CANCELLED', 'NO_SHOW'),
    total_price    DECIMAL(10, 2),
    payment_id     VARCHAR(64),   -- External payment reference
    created_at     TIMESTAMP,
    cancelled_at   TIMESTAMP,
    INDEX idx_user_bookings (user_id, created_at DESC),
    INDEX idx_hotel_date (hotel_id, check_in)
);
```

---

## Booking Flow (Preventing Double-Booking)

```
The core challenge: decrement inventory atomically.

Naive approach (WRONG - race condition):
  1. SELECT available_rooms > 0 (sees: 1 available)
  2. [Two threads both see 1 available simultaneously]
  3. INSERT INTO bookings (...)
  4. UPDATE room_inventory SET booked_rooms = booked_rooms + 1
  Result: booked_rooms = 2, but only 1 room existed → OVERSOLD

Correct approach: Optimistic locking with constraint:

  BEGIN TRANSACTION
    UPDATE room_inventory
    SET booked_rooms = booked_rooms + num_rooms
    WHERE room_type_id = ? AND date BETWEEN check_in AND check_out
    AND (booked_rooms + num_rooms) <= total_rooms;  ← Atomic check+update

    IF rows_updated < expected:
      ROLLBACK; RETURN "No availability"

    INSERT INTO bookings (..., status = 'PENDING');
  COMMIT

  → Call payment gateway (OUTSIDE transaction)

  BEGIN TRANSACTION
    UPDATE bookings SET status = 'CONFIRMED', payment_id = ?
    WHERE booking_id = ? AND status = 'PENDING'
  COMMIT

The CHECK constraint `booked_rooms <= total_rooms` is the final safety net.
If two transactions update simultaneously, one will violate the constraint → rollback.
```

---

## Two-Phase Booking with Reservation Timeout

```
Problem: User browses, starts booking, then abandons.
         Room is "held" but never paid for.

Solution: Two-phase commit with timeout

Phase 1 (Soft Hold, 15 minutes):
  User clicks "Book Now"
  System creates booking with status='PENDING', sets expires_at = now() + 15 min
  Increments booked_rooms (room appears unavailable to others)
  User sees: "Room held for 15 minutes — complete payment to confirm"

Phase 2 (Confirm):
  User pays → payment gateway confirms
  Update booking status='CONFIRMED', set payment_id
  Room remains booked

Expiry job (runs every minute):
  SELECT * FROM bookings WHERE status='PENDING' AND expires_at < NOW()
  For each expired:
    UPDATE bookings SET status='EXPIRED'
    UPDATE room_inventory SET booked_rooms = booked_rooms - num_rooms
  → Room becomes available again for other users
```

---

## Availability Search Optimization

```
Slow query: SELECT hotels WHERE city='Paris' AND date BETWEEN X AND Y AND available > 0
  → Joins hotels + room_types + room_inventory across millions of rows → seconds

Optimization 1: Materialized availability view
  Pre-compute: for each hotel, a bitmask of "is any room available for next 365 days?"
  Refresh on every booking (async, < 1 second)
  Search: filter on bitmask first → dramatically reduces rows to join

Optimization 2: Cache hot city-date combinations
  Paris on Dec 31: millions of searches
  Cache Redis: "availability:Paris:2026-12-31" → list of available hotels
  TTL: 60 seconds (stale availability is ok for search results; re-check at booking time)

Optimization 3: Elasticsearch for search
  Index hotels with geo coordinates, amenities, rating
  Geospatial filter: "hotels within 5km of Eiffel Tower"
  Availability: Elasticsearch has a "available" boolean field (approximate)
  Exact availability: checked at booking time (DB source of truth)
```

---

## API Design

```http
# Search availability
GET /hotels?city=Paris&check_in=2026-12-31&check_out=2027-01-02&guests=2

Response: [
  { "hotel_id": 123, "name": "Hotel Lumière", "rating": 4.5,
    "rooms": [
      { "room_type_id": 456, "name": "Deluxe Double", "price": 250, "available": 3 }
    ]
  }, ...
]

# Initiate booking (Phase 1 - soft hold)
POST /bookings
{
  "room_type_id": 456,
  "check_in": "2026-12-31",
  "check_out": "2027-01-02",
  "num_rooms": 1,
  "guests": 2
}
Response: { "booking_id": "bk_abc", "status": "PENDING", "expires_at": "...", "total": 500 }

# Confirm booking (Phase 2 - after payment)
POST /bookings/{booking_id}/confirm
{ "payment_method_id": "pm_xyz" }
Response: { "booking_id": "bk_abc", "status": "CONFIRMED", "confirmation_code": "LUMIERE2026" }

# Cancel booking
POST /bookings/{booking_id}/cancel
Response: { "status": "CANCELLED", "refund_amount": 500, "refund_eta": "3-5 business days" }
```

---

## Interview Talking Points

**Q: "How do you prevent two users from booking the last room?"**
> "Atomic conditional update. Rather than SELECT then UPDATE (race condition), we do a single UPDATE with the constraint in the WHERE clause: `UPDATE room_inventory SET booked_rooms = booked_rooms + 1 WHERE booked_rooms < total_rooms`. If rows_affected = 0, the inventory wasn't available. The database's row-level lock ensures only one concurrent UPDATE succeeds for the same row. The CHECK constraint `booked_rooms <= total_rooms` is a final safety net."

**Q: "What happens if payment fails after we hold the room?"**
> "The room remains in PENDING state until the 15-minute timer expires. Our expiry job runs every minute, finds expired PENDING bookings, decrements booked_rooms, and marks the booking as EXPIRED. The room reappears as available. If payment failed partway (gateway returned error), we immediately trigger the rollback rather than waiting for timeout. Idempotency key on the payment API ensures we don't accidentally charge twice on retry."

---

## Interview Questions Asked

### Uber
1. **"Design trip booking with real-time driver matching — how do you prevent two riders from being matched to the same driver?"** → Optimistic locking on driver state: driver record has a `status` field (AVAILABLE/MATCHED/ON_TRIP) and a `version` column. Matching service does `UPDATE drivers SET status='MATCHED', version=version+1 WHERE driver_id=X AND status='AVAILABLE' AND version=N`. Only one concurrent request can win the CAS; losers retry with a different driver. No distributed lock needed.
2. **"How does Uber handle surge pricing in the booking flow — where does it sit architecturally?"** → Surge multiplier is computed by a separate Pricing Service that runs every 30 seconds based on supply/demand ratio per geohash cell. The multiplier is cached in Redis. Booking service reads surge multiplier at trip-request time and embeds it in the fare estimate. The multiplier is locked in at booking time — rider sees no price change after accepting.

### Amazon
1. **"Design hotel booking with inventory locking — how do you prevent overbooking during a flash sale?"** → Database-level constraint: `CHECK (booked_rooms <= total_rooms)` + atomic `UPDATE room_inventory SET booked_rooms = booked_rooms + 1 WHERE booked_rooms < total_rooms`. If rows_affected = 0, inventory is exhausted. For flash sales with extreme concurrency: queue bookings via a FIFO queue (SQS FIFO), process sequentially per room type — eliminates lock contention at the cost of latency.
2. **"How would you design the confirmation email / notification pipeline so it doesn't block the booking response?"** → Async: booking service publishes a `booking_confirmed` event to Kafka/SQS after the DB commit. Notification service consumes the event and sends email/SMS. The HTTP response returns to the user immediately after DB commit — no waiting for email delivery. Idempotent consumer ensures duplicate events (at-least-once delivery) don't send duplicate emails.

### Common Follow-ups
1. **"How do you prevent overbooking when two users submit simultaneously for the last available room?"** → The atomic conditional UPDATE is the core mechanism — it's a single SQL statement with the constraint in the WHERE clause, not a SELECT then UPDATE. The DB's row-level lock serializes concurrent updates to the same row; only one succeeds. This is more efficient than an application-level distributed lock.
2. **"A user clicks 'Book' twice in rapid succession — how do you prevent a double charge?"** → Idempotency key: client generates a UUID on the first click and sends it with the request header (`Idempotency-Key: <uuid>`). Server stores the key in a Redis/DB table with the result. Second request with the same key returns the cached result without re-processing. Key expires after 24 hours.
3. **"How does Airbnb handle calendar blocking — a host blocks dates, then a guest tries to book the same dates?"** → Calendar blocks and bookings share the same `availability` table. Both operations do the same atomic check: `UPDATE availability SET status='BLOCKED' WHERE date IN (...) AND status='AVAILABLE'`. The host's block and the guest's booking compete for the same rows — first writer wins, second gets rows_affected=0 and fails gracefully.
4. **"How do you handle a payment timeout — the charge request to Stripe times out. Did it go through or not?"** → Query the payment gateway's status endpoint with the idempotency key to retrieve the outcome. If it went through: confirm the booking. If it failed: release the hold. If still pending: poll with exponential backoff. Never assume timeout = failure — timeouts mean unknown, and charging twice is worse than a brief hold delay.
