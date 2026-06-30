> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design a booking system (hotels/flights) — preventing double-booking with inventory locking, idempotent reservations, and consistent availability search.
>
> **Key design decisions:**
> - Inventory locking: SELECT FOR UPDATE on inventory row during booking transaction; pessimistic locking prevents concurrent double-booking
> - Optimistic locking alternative: version field on inventory row; CAS (compare-and-swap) on update; retry on conflict; better throughput
> - Idempotency: idempotency key per booking attempt; prevents duplicate charge if client retries after timeout
> - Two-step booking: hold → confirm flow; hold locks inventory for 10 min; confirm completes payment + booking; release if not confirmed
> - Availability search: read replicas or separate search index (Elasticsearch) for fast availability queries; don't hit primary DB for reads
> - Payment flow: payment processed outside booking DB transaction to avoid holding DB lock during payment processing (300ms+ latency)
> - Overbooking prevention: DB constraint (CHECK inventory ≥ 0) + unique constraint on (room_id, date, booking_id) as last line of defense
>
> **Key takeaway:** Two-step hold → confirm prevents double-booking without holding locks during payment processing; idempotency keys prevent duplicate charges on retry.

---
module: 05-hld-problems
topic: Easy
status: unread
tags: [05-hld-problems, system-design, easy, booking, reservation, optimistic-locking, overbooking]
---
# Design a Booking System (Hotels / Flights)

> **Difficulty**: Easy | **Asked at**: Amazon, Booking.com, Expedia, Airbnb

---

## Problem Statement

Design a booking system for hotels or flights. Users search for availability, view listings, and complete a reservation. The system must prevent double-booking (two users cannot book the same seat/room for the same dates).

---

## Functional Requirements

1. **Search availability**: Find available rooms/seats for given dates and criteria
2. **Reserve**: Book a specific room/seat for given dates; prevent double-booking
3. **Cancel**: Cancel an existing booking; release the inventory
4. **View booking**: Retrieve booking details and status
5. **Inventory management**: Hotel/airline staff can manage available inventory

---

## Non-Functional Requirements

- **Scale**: 10M searches/day, 1M bookings/day → 12 bookings/sec average, 100/sec peak (flash sales)
- **Consistency**: Double-booking is a hard failure — ACID guarantees required for the reservation write
- **Latency**: Search < 200ms, booking < 500ms P99
- **Availability**: 99.99% for search; 99.9% for booking (can tolerate brief downtime)
- **Peak handling**: Sports event or concert sales spike can generate 10,000 booking attempts/second for a single inventory item

---

## Core Entities

| Entity | Key Fields |
|--------|-----------|
| `Property` | property_id, name, type (hotel/flight), location, amenities |
| `Inventory` | inventory_id, property_id, unit (room_number/seat), date_range, available_count, price |
| `Booking` | booking_id, user_id, inventory_id, check_in, check_out, status (PENDING/CONFIRMED/CANCELLED), total_price |
| `User` | user_id, email, payment_method_id |
| `Reservation Lock` | inventory_id, date_range, expires_at (TTL 10 min) — temporary hold during checkout |

---

## API Design

```http
GET /api/v1/search?location=NYC&check_in=2026-07-01&check_out=2026-07-03&guests=2
Response 200: { "results": [{ "property_id": "h123", "available": true, "price": 150, "rating": 4.5 }] }

POST /api/v1/bookings/reserve
Body: { "inventory_id": "inv456", "check_in": "2026-07-01", "check_out": "2026-07-03", "user_id": "u789" }
Response 200: { "reservation_id": "res123", "expires_at": "2026-06-29T12:10:00Z", "price": 300 }
# Holds inventory for 10 minutes while user completes payment

POST /api/v1/bookings/confirm
Body: { "reservation_id": "res123", "payment_token": "tok_abc" }
Response 201: { "booking_id": "bk999", "status": "CONFIRMED" }

DELETE /api/v1/bookings/{booking_id}
Response 200: { "refund_amount": 300, "status": "CANCELLED" }
```

---

## High-Level Design

```
Client
  │
  ▼
Load Balancer
  │
  ├── Search Service → Elasticsearch (fast full-text + geo search)
  │                 → PostgreSQL (availability query on inventory table)
  │
  ├── Booking Service → PostgreSQL (ACID transactions for reservation)
  │                   → Redis (reservation TTL locks)
  │                   → Payment Service (async)
  │
  └── Inventory Service → PostgreSQL (inventory management)
                        → Cache invalidation → Redis

PostgreSQL: Source of truth for inventory and bookings
Elasticsearch: Read replica of property data for search
Redis: Temporary reservation locks (TTL = 10 minutes)
```

**Two-phase booking**:
1. **Reserve** (hold): Create a reservation lock in Redis with a 10-minute TTL. Decrement `available_count` tentatively in PostgreSQL via a transaction.
2. **Confirm** (commit): Process payment. On success, mark booking CONFIRMED in PostgreSQL, remove Redis lock. On failure or timeout, release hold (increment `available_count` back).

This prevents double-booking while allowing users time to complete payment.

---

## Deep Dive 1: Preventing Double-Booking

**Problem**: Two users simultaneously try to book the last available room. Without coordination, both might read `available_count = 1`, both pass the availability check, and both complete the booking — resulting in a double-booking.

**Option 1: Optimistic locking**:
```sql
-- Read inventory with version
SELECT available_count, version FROM inventory WHERE inventory_id = 'inv456';
-- returns: available_count=1, version=42

-- Write with version check
UPDATE inventory
SET available_count = available_count - 1, version = version + 1
WHERE inventory_id = 'inv456' AND version = 42 AND available_count >= 1;
-- If 0 rows affected → another user booked first → retry or reject
```
No lock held between read and write. Fails fast on conflict. Good for low contention.

**Option 2: Pessimistic locking**:
```sql
BEGIN;
SELECT available_count FROM inventory WHERE inventory_id = 'inv456' FOR UPDATE;
-- Row is locked; other transactions wait
UPDATE inventory SET available_count = available_count - 1 WHERE inventory_id = 'inv456';
COMMIT;
```
Guarantees correctness but creates a serialization bottleneck for high-demand inventory.

**Option 3: Redis-based lock with Lua** (for flash sales):
```lua
-- Atomic check-and-decrement
if redis.call('GET', key) > 0 then
  redis.call('DECR', key)
  return 1  -- success
else
  return 0  -- sold out
end
```
Redis serves as a fast pre-filter. Only requests that pass Redis proceed to PostgreSQL. Reduces DB contention 10×.

**Recommendation**: Optimistic locking for normal booking. Redis pre-filter + optimistic locking for flash sales.

---

## Deep Dive 2: Search and Availability Queries

**Problem**: Search queries combine multiple filters (location, dates, price range, amenities, room type). Availability must reflect real-time inventory, including in-progress reservations.

**Search (Elasticsearch)**: Property data (name, description, amenities, location, rating) is indexed in Elasticsearch. Full-text search and geo queries run in < 10ms. Updated asynchronously from PostgreSQL via Change Data Capture (Debezium → Kafka → ES indexer).

**Availability query (PostgreSQL)**:
```sql
SELECT i.inventory_id, i.price, i.available_count
FROM inventory i
WHERE i.property_id = ANY(:property_ids)
  AND i.check_in >= :check_in
  AND i.check_out <= :check_out
  AND i.available_count > 0;
```
Index: `(property_id, check_in, check_out, available_count)`. For hotels with 365 days of inventory × 1,000 rooms, this table has 365K rows per property — manageable.

**Combining search results**: Run Elasticsearch query first → get property_ids → run availability SQL for those properties. Two-step allows each system to do what it's best at.

**Cache for popular date ranges**: Cache availability for popular weekends (Labor Day, New Year's) in Redis with a 30-second TTL. Invalidate on any booking or cancellation.

---

## Deep Dive 3: Handling Cancellations and Refunds

**Problem**: A user cancels a booking. Inventory must be released. Refund must be processed. These operations must be atomic — no partial state.

**Cancellation flow**:
1. User calls `DELETE /bookings/{booking_id}`
2. Application fetches booking → validates cancellation eligibility (within cancellation window)
3. Begin PostgreSQL transaction:
   - Update `booking.status = CANCELLED`
   - Increment `inventory.available_count`
   - Insert `refund_request` row
4. Commit transaction
5. Async: Payment service processes refund (asynchronous — may take minutes for bank processing)

**Saga pattern for refunds**: The booking confirmation saga has two steps: (1) confirm booking, (2) charge payment. Cancellation is the reverse saga: (1) cancel booking, (2) refund payment. If the refund fails, the booking remains cancelled but a compensating transaction is queued for retry.

**Partial refunds**: Cancellation policies vary (full refund > 7 days before check-in, 50% refund within 7 days, no refund < 24 hours). Store the policy on the booking at time of creation. Apply at cancellation time.

**Inventory release timing**: When a booking is cancelled, should the room immediately become bookable again? Yes — release inventory atomically in the same transaction as the cancellation. Another user can book it immediately.

---

## Interviewer Questions by Level

**Junior**:
- What is double-booking and why is it a problem?
- How does optimistic locking prevent double-booking?
- What is the purpose of the 10-minute reservation hold?

**Mid-level**:
- Walk me through the complete booking flow from search to confirmed booking.
- How do you handle the case where payment fails after the reservation hold is created?
- How do you design the availability query for a hotel with 1,000 rooms over 365 days?

**Senior**:
- A Taylor Swift concert goes on sale and 100,000 users simultaneously try to book the last 1,000 seats. How does your system handle this?
- How do you ensure the inventory count in Redis stays consistent with PostgreSQL?
- Design a cancellation flow that handles partial refunds and ensures the payment refund is eventually consistent.
- How would you support overbooking (like airlines do intentionally) at a configurable rate?
