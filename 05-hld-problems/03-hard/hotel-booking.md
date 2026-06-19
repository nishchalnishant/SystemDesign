---
module: 05-hld-problems
topic: Hard
status: unread
tags: [05-hld-problems, system-design, hard]
---
# Design a Hotel Booking System (Airbnb / Booking.com)

## Problem Statement

Design a hotel booking platform that supports:
- Searching available hotels/rooms for a date range
- Booking a room for specific dates (no double-booking)
- Cancellation and refund flows
- 500M users, 100K+ hotels, peak load around holidays

---

## Functional Requirements

- Search hotels by location, dates, filters (price, amenities, rating)
- View hotel details and room inventory for a date range
- Book a room (reserve + pay atomically)
- Cancel a booking (with policy-based refund)
- View booking history (user + hotel sides)

## Non-Functional Requirements

- **Availability**: 99.99% for search, 99.9% for booking
- **Consistency**: No double-booking — a room for a date range must be sold to exactly one guest
- **Latency**: Search < 200ms p99, booking < 1s p99
- **Scale**: 500M users, 2M room-nights booked per day, 100K QPS search
- **Durability**: No lost bookings — payment + reservation must be atomic

---

## Capacity Estimation

```
Search:
  500M users × 3 searches/day = 1.5B searches/day
  = 17,000 QPS average; peak ~100,000 QPS

Bookings:
  2M bookings/day = 23 bookings/second average
  Peak (Black Friday, New Year): 10× → 230 bookings/second

Room inventory:
  500K hotels × 100 rooms avg = 50M rooms
  Each room has N date slots (1 year = 365 dates)
  50M rooms × 365 = 18.25B room-date records
  At 50 bytes each ≈ 900 GB (fits in partitioned DB, not in memory)

Storage:
  Booking record: ~500 bytes
  2M bookings/day × 365 days × 500 bytes = ~365 GB/year
```

---

## Core Design Challenge: Room Availability & Double-Booking

A room is available for a date range if no confirmed booking overlaps that range. Two users searching simultaneously both see "available". Both click "Book". Without coordination, both bookings are confirmed — a double-booking.

This is a **date-range inventory reservation problem**: harder than a single-seat booking because a booking spans multiple date slots, and availability must be atomic across all dates in the range.

---

## Data Model

### Hotels and Rooms
```sql
hotels (
  hotel_id      UUID PRIMARY KEY,
  name          TEXT,
  location_id   UUID,
  star_rating   INT,
  amenities     JSONB
)

rooms (
  room_id       UUID PRIMARY KEY,
  hotel_id      UUID REFERENCES hotels,
  room_type     TEXT,        -- 'single', 'double', 'suite'
  base_price    DECIMAL,
  max_occupancy INT
)
```

### Room Availability (two schema options — see deep dive)

**Option A: date-slot table (one row per room per date)**
```sql
room_availability (
  room_id   UUID,
  date      DATE,
  status    ENUM('available', 'held', 'booked'),
  booking_id UUID,           -- NULL if available
  version   BIGINT,          -- for optimistic locking
  PRIMARY KEY (room_id, date)
)
```

**Option B: booking overlap check (no pre-expanded slots)**
```sql
bookings (
  booking_id    UUID PRIMARY KEY,
  room_id       UUID,
  user_id       UUID,
  check_in      DATE,
  check_out     DATE,         -- exclusive end date
  status        ENUM('pending', 'confirmed', 'cancelled'),
  payment_id    UUID,
  created_at    TIMESTAMP,
  expires_at    TIMESTAMP     -- for pending holds
)
-- Index: (room_id, check_in, check_out) for overlap queries
```

### Users and Payments
```sql
users (user_id UUID, name, email, payment_methods JSONB)
payments (payment_id UUID, booking_id, amount, status, gateway_ref)
```

---

## High-Level Architecture

```
                        ┌─────────────┐
                        │   Clients   │
                        │ (Web/Mobile)│
                        └──────┬──────┘
                               │
                        ┌──────▼──────┐
                        │   CDN       │ ← static assets, search results cache
                        └──────┬──────┘
                               │
                        ┌──────▼──────┐
                        │  API Gateway│ ← auth, rate limiting, routing
                        └──┬──────┬───┘
                           │      │
              ┌────────────▼─┐  ┌─▼───────────────┐
              │ Search Service│  │  Booking Service │
              │  (read-heavy) │  │  (write-critical)│
              └──────┬────────┘  └────────┬─────────┘
                     │                    │
          ┌──────────▼──┐      ┌──────────▼──────────┐
          │Elasticsearch│      │   Booking DB         │
          │(hotels/rooms│      │ (PostgreSQL, sharded │
          │ searchable) │      │  by hotel_id)        │
          └─────────────┘      └─────────┬────────────┘
                                         │
                               ┌─────────▼──────────┐
                               │  Payment Service    │
                               │  (Stripe/Adyen)     │
                               └────────────────────┘

Supporting:
  Redis          ← availability cache, distributed locks, session holds
  Kafka          ← booking events for notifications, analytics, CDC
  Notification   ← email/SMS confirmations via Kafka consumer
```

---

## Deep Dive: Preventing Double-Booking

### Option 1: Database Row Locking (`SELECT FOR UPDATE`)

For the date-slot schema (Option A):

```sql
BEGIN TRANSACTION;

-- Lock all date rows for the room in the requested range
SELECT * FROM room_availability
WHERE room_id = $1
  AND date >= $check_in AND date < $check_out
FOR UPDATE;  -- row-level exclusive lock

-- Check all slots are available
-- (application code verifies all returned rows have status='available')

-- Reserve all slots
UPDATE room_availability
SET status = 'held', booking_id = $booking_id, expires_at = NOW() + interval '10 minutes'
WHERE room_id = $1
  AND date >= $check_in AND date < $check_out;

COMMIT;
```

**How it prevents double-booking:** `FOR UPDATE` locks the rows. If User B requests the same room while User A holds the lock, User B's `SELECT FOR UPDATE` blocks until User A's transaction commits. User A confirms → rows become 'held' → User B sees 'held', booking fails.

**Downside:** Lock held for duration of payment processing (up to 30s). Other bookings for unrelated rooms on same DB shard also wait if they hit the same rows. Deadlock risk if two transactions lock rooms in different order.

**Mitigation:** Keep hold phase short. Lock only the date rows being booked, not the room row itself. Set `lock_timeout = 5s` to fail fast rather than long-wait.

---

### Option 2: Optimistic Locking (version check)

```sql
-- Read availability without locking
SELECT version, status FROM room_availability
WHERE room_id = $1 AND date >= $check_in AND date < $check_out;

-- All available → attempt CAS-style update
UPDATE room_availability
SET status = 'held', booking_id = $booking_id, version = version + 1
WHERE room_id = $1
  AND date >= $check_in AND date < $check_out
  AND status = 'available'          -- only if still available
  AND version = $read_version;      -- only if not modified since we read

-- rows_affected == expected_count → success
-- rows_affected < expected_count → conflict → retry or fail
```

**How it prevents double-booking:** The `WHERE version = $read_version` clause is a CAS — it only updates if no other transaction changed the row since we read it. If two users try simultaneously, one UPDATE wins (all rows match version), the other gets `rows_affected = 0`.

**Advantage:** No locks held. Other transactions never block. Better throughput under moderate contention.

**Disadvantage:** Under high contention (Black Friday), most transactions will fail and retry → retry storms. Need exponential backoff + jitter. Not suitable if contention rate > 50%.

---

### Option 3: Distributed Lock + Availability Check (Redis)

For extremely high contention on popular rooms:

```
1. Acquire Redis lock: SET room:{room_id}:lock {booking_id} NX PX 30000
   → NX = only if not exists; PX = expires in 30 seconds
2. If lock acquired:
   a. Check availability in DB (read)
   b. Create booking record (write)
   c. Release lock: DEL room:{room_id}:lock (if still owner)
3. If lock not acquired → room is being booked → return "try again"
```

**Advantage:** Single Redis roundtrip to serialize all booking attempts for a room. Availability check inside lock is safe — no concurrent modifier.

**Disadvantage:** Redis lock is not durable. If Redis crashes after lock acquired but before DB write, the lock disappears → potential double-booking. Must combine with DB-level idempotency (unique constraint on booking_id).

**Safety net — unique constraint:**
```sql
-- Overlap exclusion constraint (PostgreSQL with exclusion constraint)
ALTER TABLE bookings ADD CONSTRAINT no_double_booking
  EXCLUDE USING gist (
    room_id WITH =,
    daterange(check_in, check_out, '[)') WITH &&
  )
  WHERE (status != 'cancelled');
```
This constraint makes it physically impossible to insert two overlapping confirmed bookings for the same room — the DB is the final arbiter regardless of which locking strategy you use.

---

### Recommended Approach: Two-Phase Commit with DB Exclusion Constraint

```
Phase 1 — Hold (10 minute TTL):
  1. INSERT booking (status='pending', expires_at=now+10min)
     → if overlapping non-cancelled booking exists, INSERT fails (exclusion constraint)
     → return hold_token to user
  2. Decrement Redis availability cache for fast subsequent reads

Phase 2 — Confirm (after payment):
  1. Process payment via payment gateway
  2. On payment success: UPDATE booking SET status='confirmed', payment_id=$id
  3. On payment failure: UPDATE booking SET status='cancelled'
  4. Publish booking.confirmed event to Kafka

Background job:
  Every 5 minutes: DELETE/UPDATE bookings WHERE status='pending' AND expires_at < NOW()
  → releases held rooms back to available
```

The exclusion constraint on the `bookings` table is the atomic double-booking prevention. Holds are real bookings (status='pending') that expire — a second booking attempt during the hold period will fail the overlap constraint.

---

## Deep Dive: Search — Date-Range Availability Query

Naive query: join `rooms` with `room_availability` for all dates in range. For a 7-night stay across 50M rooms — impossibly slow.

**Strategy: pre-computed availability index**

```
availability_index (
  hotel_id     UUID,
  room_type    TEXT,
  available_from  DATE,    -- start of contiguous available block
  available_until DATE,    -- end of contiguous available block
  min_price    DECIMAL
)
```

Updated by a background worker when bookings are created/cancelled. Search query:

```sql
SELECT h.*, ai.min_price
FROM hotels h
JOIN availability_index ai ON ai.hotel_id = h.hotel_id
WHERE h.location_id = $location
  AND ai.available_from <= $check_in
  AND ai.available_until >= $check_out
  AND ai.min_price <= $max_price
ORDER BY ai.min_price ASC
LIMIT 50;
```

**Elasticsearch for location search:** Hotels are indexed in Elasticsearch with geo-coordinates. Location search uses `geo_distance` query to find hotels within radius, then fetches availability from DB.

```
Flow:
1. Elasticsearch: hotel_ids within 10km of NYC, matching filters → [h1, h2, ..., h500]
2. PostgreSQL: which of these hotel_ids have availability for 2026-07-01 → 2026-07-05?
3. Merge, sort by price/rating, return top 50
```

---

## Deep Dive: Cancellation and Refund Saga

Cancellation must atomically update booking status and trigger refund. Use saga pattern:

```
CancelBooking Saga:
  Step 1: UPDATE booking SET status='cancellation_pending'
  Step 2: Call payment gateway refund API
  Step 3a (success): UPDATE booking SET status='cancelled', refund_id=$id
           Publish booking.cancelled event → availability_index rebuild
  Step 3b (failure): UPDATE booking SET status='confirmed'  ← compensate
           Alert ops for manual refund review

Idempotency: store refund_id to prevent double-refund on retry
```

**Cancellation policy enforcement:**
```java
CancellationPolicy policy = hotel.getPolicy();
long daysUntilCheckIn = ChronoUnit.DAYS.between(now, booking.checkIn);
BigDecimal refundAmount = policy.calculateRefund(daysUntilCheckIn, booking.totalAmount);
// Free cancellation >7 days, 50% refund 3-7 days, no refund <3 days
```

---

## Deep Dive: Handling Overbooking (Airline-Style)

Some hotels intentionally overbook by 5–10% (based on historical no-show rates). System design implication:

- `rooms` table has `virtual_capacity` (e.g., 10 rooms physically, 11 virtual)
- Availability check uses virtual_capacity
- If all 11 bookings confirm and all guests show up, hotel handles it operationally (room upgrade, partner hotel)
- System exposes `overbooking_buffer` as a hotel-level config, not exposed to users

**For our design:** default to no overbooking (virtual_capacity == physical_capacity). Hotels can configure a buffer in their admin panel.

---

## Bottlenecks and Mitigations

| Bottleneck | Mitigation |
|---|---|
| DB contention on popular rooms | Optimistic locking + exclusion constraint; Redis lock for extreme hot rooms |
| Search latency for date-range availability | Pre-computed availability_index; Elasticsearch for location |
| Payment gateway timeout during booking hold | Async payment with webhook callback; hold TTL gives 10 min window |
| Expired hold cleanup lag | Background job + index on `(status, expires_at)` |
| Holiday peak search QPS (100K+) | Cache search results in Redis with 60s TTL; CDN for static hotel pages |
| Multi-region consistency | Bookings DB in single region per hotel (hotel_id shard); cross-region replication read-only |

---

## API Design

```
GET  /api/v1/hotels?location={lat,lon}&radius=10km&check_in=2026-07-01&check_out=2026-07-05&guests=2
     → [ { hotel_id, name, rating, min_price, room_types_available } ]

GET  /api/v1/hotels/{hotel_id}/rooms?check_in=...&check_out=...
     → [ { room_id, type, price, amenities, availability: true } ]

POST /api/v1/bookings
     Body: { room_id, check_in, check_out, user_id, payment_method_id }
     → { booking_id, status: "pending", expires_at, total_amount }

POST /api/v1/bookings/{booking_id}/confirm
     → { booking_id, status: "confirmed", confirmation_number }

DELETE /api/v1/bookings/{booking_id}
     → { booking_id, status: "cancelled", refund_amount }
```

---

## Trade-offs Summary

| Decision | Choice | Reason |
|---|---|---|
| Double-booking prevention | Exclusion constraint + optimistic locking | DB is final arbiter; no distributed lock needed for correctness |
| Search index | Elasticsearch + pre-computed availability_index | Geo-search + date range too slow on relational DB alone |
| Hold mechanism | Pending booking with expires_at | Reuses booking table; no separate hold table; constraint prevents overlap |
| Sharding | By hotel_id | All bookings for a hotel on same shard; avoids cross-shard joins |
| Cancellation | Saga pattern | Payment refund is external; need compensation on failure |

---

## Quick Revision

- **Core problem**: date-range availability reservation without double-booking
- **Key insight**: DB exclusion constraint on `(room_id, daterange)` is the atomic safety net — no distributed lock can guarantee safety without a DB-level constraint
- **Hold pattern**: INSERT as 'pending' (constraint prevents overlap), expire after 10 min, confirm on payment
- **Search**: Elasticsearch for location, pre-computed availability_index for date-range
- **Hard question always asked**: "What happens if payment succeeds but DB update fails?" → idempotency key + payment gateway webhook callback; booking reconciliation job

---

## See Also

- `05-hld-problems/03-hard/ticketmaster-seat-booking.md` — single-seat high-contention booking
- `09-patterns/saga-pattern.md` — payment + booking saga with compensation
- `09-patterns/two-phase-commit.md` — why 2PC is avoided here
- `09-patterns/outbox-pattern.md` — reliable event publishing on booking confirm
- `02-building-blocks/distributed-locks.md` — Redlock for hot-room locking
