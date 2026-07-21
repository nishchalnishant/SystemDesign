> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design a hotel booking system (Booking.com) — availability search, double-booking prevention, concurrent reservation handling, and payment integration.
>
> **Key design decisions:**
> - Availability search: Elasticsearch for fast full-text + geo + date range queries; pre-computed availability calendar (bitmap per room per date) for O(1) lookup
> - Room inventory: room_availability table (room_id, date) with unique constraint; prevents double-booking at DB constraint level
> - Reservation flow: two-phase — HOLD (lock room for 10 min, idempotent) → CONFIRM (complete payment, convert hold to booking); hold TTL prevents abandoned reservations from blocking inventory
> - Concurrency: SELECT FOR UPDATE (pessimistic) or optimistic locking (version field + CAS retry); pessimistic preferred for short booking transactions
> - Search ranking: hotel scoring by price, rating, distance, availability; Elasticsearch custom scoring function; ML re-ranking for personalization
> - Flash sales: popular hotels → high contention; Redis atomic DECR for available count; actual DB availability updated async; Redis as fast semaphore
> - Pricing: dynamic pricing engine (base price × demand multiplier); price varies by date, season, advance booking; stored with room inventory
>
> **Key takeaway:** Two-phase HOLD → CONFIRM with a 10-minute TTL is the key pattern — it prevents overbooking while not blocking inventory indefinitely; the unique DB constraint on (room_id, date) is the final safety net.

---
module: 05-hld-problems
topic: Hard
status: unread
tags: [05-hld-problems, system-design, hard, hotel-booking, availability, double-booking, reservation]
---
# Design a Hotel Booking System (Booking.com)

> **Difficulty**: Hard | **Asked at**: Booking.com, Expedia, Airbnb, Amazon

---

## Problem Statement

Design a hotel booking platform like Booking.com. Users search for hotels by location, check-in/out dates, and guest count; view available rooms; and book rooms with payment. The system must prevent double-booking (the same room cannot be booked by two users for overlapping dates) and handle high concurrency during flash sales.

---

## Functional Requirements

1. **Hotel search**: Search by city, date range, guest count; filter by price, rating, amenities
2. **Room availability**: Show available rooms for a property and date range
3. **Booking**: Reserve a room for a date range; process payment
4. **Cancellation**: Cancel a booking and release the room
5. **Price management**: Hotels set prices per room type per date (dynamic pricing)
6. **Reviews**: Users leave ratings and reviews after checkout

---

## Non-Functional Requirements

- **Scale**: 500K hotels, 2M rooms, 10M bookings/day, 100K concurrent searches
- **Latency**: Search results < 1s; booking confirmation < 3s
- **Consistency**: No double-booking under any concurrency scenario
- **Availability**: 99.99% — booking cannot fail for valid requests
- **Overbooking prevention**: At most 1 booking per room per overlapping date range

---

## Core Entities

| Entity | Key Fields |
|--------|-----------|
| `Hotel` | hotel_id, name, city, lat, lng, star_rating, amenities[] |
| `Room` | room_id, hotel_id, room_type, max_guests, description |
| `RoomAvailability` | room_id, date, status (available/booked/blocked), price |
| `Booking` | booking_id, user_id, room_id, check_in, check_out, status, total_price, payment_id |
| `Review` | review_id, hotel_id, user_id, booking_id, rating, text, created_at |

---

## API Design

```http
GET /api/v1/hotels/search?city=Paris&check_in=2026-07-01&check_out=2026-07-05&guests=2
Response 200: {
  "hotels": [{
    "hotel_id": "h123",
    "name": "Hotel Lumière",
    "rating": 4.5,
    "min_price": 150,
    "available_rooms": 3
  }]
}

GET /api/v1/hotels/{hotel_id}/rooms?check_in=2026-07-01&check_out=2026-07-05&guests=2
Response 200: { "rooms": [{ "room_id": "r456", "type": "Deluxe", "price_per_night": 180, "available": true }] }

POST /api/v1/bookings
Body: { "room_id": "r456", "check_in": "2026-07-01", "check_out": "2026-07-05", "guest_count": 2 }
Response 201: { "booking_id": "b789", "status": "confirmed", "total_price": 720, "payment_id": "p012" }

DELETE /api/v1/bookings/{booking_id}
Response 200: { "status": "cancelled", "refund_amount": 720 }
```

---

## High-Level Design

```
User
  │
  ▼
Search Service
  │ Query Elasticsearch (geo + date + price filter)
  │ Availability from Redis cache (pre-computed availability bitmap)
  │
  ▼
Room Availability Service
  │ Exact availability for a hotel: query RoomAvailability table
  │ Cache: available rooms per hotel per date range (Redis, TTL 60s)
  │
  ▼
Booking Service
  │ 1. Lock room for dates (pessimistic or optimistic lock)
  │ 2. Process payment (Stripe)
  │ 3. Insert booking record
  │ 4. Mark room as booked in RoomAvailability
  │ 5. Confirm to user
  │
  ▼
PostgreSQL: hotels, rooms, bookings, RoomAvailability
Redis: availability cache, booking locks
Elasticsearch: hotel search index
Kafka: booking-events (→ email confirmation, review prompt)
```

---

## Deep Dive 1: Preventing Double-Booking

**Problem**: Alice and Bob simultaneously book room R456 for July 1-5. Both see the room as available. If both bookings succeed, the same room is booked twice for the same dates.

**Approach 1: Database-level constraint** — The `RoomAvailability` table has one row per (room_id, date). Status can be `available` or `booked`. Booking atomically updates all rows for the date range:

```sql
-- Check and lock availability rows
SELECT * FROM room_availability
WHERE room_id = 'r456'
  AND date >= '2026-07-01' AND date < '2026-07-05'
  AND status = 'available'
FOR UPDATE;  -- pessimistic lock; blocks concurrent updates

-- If all rows returned (all dates available), update them
UPDATE room_availability
SET status = 'booked', booking_id = 'b789'
WHERE room_id = 'r456'
  AND date >= '2026-07-01' AND date < '2026-07-05';

-- Insert booking record
INSERT INTO bookings (booking_id, room_id, check_in, check_out, ...) VALUES (...);
```

All three statements run in one transaction. `FOR UPDATE` locks the rows; the concurrent transaction for Bob must wait. When Bob's transaction runs, it finds status = 'booked' → fails → booking rejected.

**Approach 2: Unique constraint** — Add a `UNIQUE(room_id, date)` constraint on `room_availability`. The concurrent insert for Bob fails with a unique violation → booking rejected.

**Two-phase approach (better UX)**:
1. **Pre-booking (soft lock)**: `SET room_availability status='reserved' WHERE status='available'` — hold for 10 minutes
2. **Payment**: Process payment (2-5 seconds)
3. **Confirm**: `UPDATE status='booked'` — permanent
4. **Timeout**: Background job resets `reserved` rows where `reserved_at < now() - 10min` back to `available`

This prevents the room from being shown as available during payment, reducing failed payments.

---

## Deep Dive 2: Availability Search at Scale

**Problem**: A user searches for hotels in Paris for July 1-5 with 2 guests. There are 50,000 hotels in Paris. For each hotel, checking availability requires querying the `RoomAvailability` table — 50,000 queries per search request is not feasible.

**Pre-computed availability index**:
- For each hotel, maintain an availability counter in Redis: `avail:{hotel_id}:{date}` = count of available rooms on that date
- When a room is booked: `DECRBY avail:{hotel_id}:{date} 1` for each booked date
- When cancelled: `INCRBY`
- Search query: for each date in range, check `avail:{hotel_id}:{date} > 0`

**Elasticsearch geo-search + availability filter**:
- Elasticsearch document per hotel includes geo_point, star_rating, amenities, min_price
- Availability from Redis is checked post-search (hotel candidates from ES, then filter by availability)
- 50K hotels in Paris → ES returns top 1,000 by rating/price → check availability for those 1,000 hotels in Redis pipeline → return 20 results

**Stale data tolerance**: Availability cache can be slightly stale (up to 60 seconds). Users see a room as available, click book, and only then might see "sold out." This is acceptable — the actual double-booking prevention happens at the DB lock level.

---

## Deep Dive 3: Dynamic Pricing

**Problem**: A hotel wants to charge $300/night during peak season (July 4th weekend) and $120/night off-season. A rate plan may apply discounts for 7-night stays. Prices must be applied correctly in the booking total.

**Price table** (`RoomPricing`):
```sql
CREATE TABLE room_pricing (
  room_id text,
  date date,
  base_price decimal,
  min_stay_nights int DEFAULT 1,
  PRIMARY KEY (room_id, date)
);
```

**Price calculation at booking**:
```python
nights = (check_out - check_in).days
prices = db.query(
    "SELECT date, base_price FROM room_pricing WHERE room_id = %s AND date >= %s AND date < %s",
    room_id, check_in, check_out
)
subtotal = sum(p.base_price for p in prices)
min_stay = max(p.min_stay_nights for p in prices)
if nights < min_stay:
    raise BookingError("Minimum stay requirement not met")
total = subtotal * (0.9 if nights >= 7 else 1.0)  # 10% weekly discount
```

**Price update API** (for hotel managers): `PUT /api/v1/hotels/{hotel_id}/pricing` with a date range and price matrix. Updates the `room_pricing` table and invalidates availability/price caches.

---

## Interviewer Questions by Level

**Junior**:
- What is double-booking? Why is it a critical problem for hotel systems?
- What does "availability" mean for a hotel room? How is it tracked?
- What happens to a booking when a user cancels?

**Mid-level**:
- How do you prevent two users from booking the same room for the same dates simultaneously?
- How do you implement a two-phase booking (soft reserve → confirm) to prevent payment failures?
- How do you search for available hotels in a city efficiently without querying every hotel's calendar?

**Senior**:
- Design the availability calendar system for 2M rooms × 365 days = 730M rows. How do you query and update this efficiently?
- A hotel overbooks by mistake (more bookings than rooms). How does the system detect and handle this?
- Design the dynamic pricing system — how do you support per-date, per-room-type pricing with last-minute discounts and minimum-stay requirements?
- How do you handle a flash sale where a hotel drops prices at midnight and 100,000 users try to book the same 50 rooms?

---

## Back-of-Envelope Estimation

**Scale inputs (given in NFRs):**
- 500K hotels; 10M rooms; 10M searches/day; 500K bookings/day

**Search throughput:**
- 10M searches/day ÷ 86,400 sec = **~115 searches/sec** average
- Peak (weekend mornings, travel season): 10× = **~1,150 searches/sec**
- Each search: `{city, check_in, check_out, guests, filters}` → returns up to 20 hotels with availability

**Availability check complexity:**
- To check if a hotel has a room available for dates [check_in, check_out]:
  - Naive: query all bookings that overlap the date range for each room
  - `SELECT count(*) FROM bookings WHERE room_id = ? AND check_in < ? AND check_out > ?`
  - For 10M rooms × 1,150 searches/sec: 11.5B DB queries/sec — impossible
- Optimization: pre-aggregate availability into a date-partitioned cache
  - `available_rooms:{hotel_id}:{date}` → integer count of available rooms
  - Pre-compute nightly (batch) + update on booking/cancellation (incremental)
  - 1,150 searches × 7 nights (avg stay) × 20 hotels per search = **161,000 Redis reads/sec** — comfortable on a Redis cluster

**Availability cache sizing:**
- 500K hotels × 365 days × 10 bytes per `{date: available_count}` entry = **~1.8 GB** — fits in a single Redis instance

**Booking write throughput:**
- 500K bookings/day ÷ 86,400 sec = **~5.8 bookings/sec** average
- Peak: 10× = **~58 bookings/sec** — very low throughput for a DB
- Each booking: `{booking_id, user_id, hotel_id, room_id, check_in, check_out, guests, status, price}` ≈ 500 bytes
- Peak write: 58 × 500 bytes = **29 KB/sec** — negligible

**Concurrency risk (high-season flash booking):**
- Popular hotel, 1 remaining room: 100 users try to book simultaneously
- Without locking: all 100 see "1 room available," all 100 try to INSERT booking → 100 double-bookings
- With `SELECT FOR UPDATE` on availability row: serialized, only 1 succeeds, 99 get "no availability" error
- At 58 bookings/sec, this lock contention is negligible — P99 wait < 5ms

**Payment processing:**
- 500K bookings/day × $150 avg transaction = **$75M/day** through the payment system
- Credit card hold (not capture): charge on check-in or 24h before
- Idempotency key per booking: prevent double-charges on retry

**Architecture decisions driven by these numbers:**
- **Pre-aggregated availability cache, not per-booking queries**: Search is 10M/day vs bookings 500K/day (20× more searches). Querying raw booking rows for every search at 1,150/sec is 161K DB queries/sec for 7 nights × 20 hotels — unsustainable. Pre-aggregated Redis counters reduce this to 161K simple Redis GET calls/sec — 20× faster and eliminates DB read load.
- **Optimistic locking for room assignment, pessimistic for final booking commit**: The search phase (which room type is available?) uses optimistic reads from cache. The final room assignment (`UPDATE rooms SET status='reserved' WHERE room_id=? AND status='available'`) uses a DB-level UPDATE with row-level lock. If the row was already taken (0 rows updated), return 409 Conflict. This avoids holding locks during the slow payment processing step.
- **Date-range index on bookings table**: `CREATE INDEX idx_bookings_overlap ON bookings (hotel_id, check_out, check_in)` enables efficient overlap queries for availability recomputation. Without this index, recomputing availability after a cancellation requires a full table scan.

---

## Related

**Concepts used in this design**

- [Distributed Locks](../../02-building-blocks/04-coordination/02-distributed-locks.md)
- [Saga Pattern](../../09-patterns/01-data-consistency/03-saga-pattern.md)
- [Two-Phase Commit](../../09-patterns/01-data-consistency/02-two-phase-commit.md)
- [PostgreSQL Internals](../../04-advanced-topics/03-internals/06-postgresql-internals.md)
- [Caching Layer](../../02-building-blocks/02-performance/01-caching-layer.md)

**Practice next**

- [Booking System](../01-easy/booking-system.md)
- [Payment System](../03-hard/payment-system.md)

The easy variant introduces the reservation model this extends.

**Frameworks**: [HLD Template](../../07-interview-templates/01-frameworks/01-hld-template.md) · [Capacity Estimation](../../07-interview-templates/02-cheat-sheets/02-capacity-estimation.md) · [Trade-offs Cheat Sheet](../../07-interview-templates/02-cheat-sheets/01-trade-offs-cheat-sheet.md)
