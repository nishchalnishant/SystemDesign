---
module: 05-hld-problems
topic: Hard
status: interview-ready
tags: [05-hld-problems, system-design, hard]
---
# Design a Hotel Booking System (Booking.com / Airbnb Scale)

> **Difficulty**: Hard
> **Topics**: Double-Booking Prevention, Date-Range Overlap, Search, Saga
> **Time**: 60 min
> **Companies**: Amazon, Airbnb, Common

---

## Clarifying Questions

1. "Are we building the booking transaction, or the full platform including search and property management?"
2. "What scale — 500M users, 100K+ hotels globally?"
3. "Is the inventory model rooms with unlimited dates, or do hosts block specific dates?"
4. "What's the booking window — same-day to 12 months out?"
5. "Do we need to handle rate plans (weekend pricing, advance purchase discounts)?"
6. "What's the consistency requirement — can we ever double-book?"

---

## Back-of-Envelope

```
500M registered users
100K hotels, 10M rooms

Traffic:
  Search: peak 50K QPS (browse >> book)
  Bookings: 2M/day = 23/sec avg, 500/sec peak (holiday weekend flash)

Storage:
  Availability data: 10M rooms × 365 days × 5 years = 18B rows (date-slot model)
  Bookings: 2M/day × 365 × 5 years = 3.6B bookings; at 500 bytes each = 1.8 TB
  Media (photos): CDN-hosted, separate from booking DB

Date-range query complexity:
  "Find available rooms in NYC, Dec 20-23" must check 3 dates across 10K+ NYC hotels
  → Cannot do this with a full table scan; needs pre-aggregated availability index
```

---

## APIs

```
// Search available hotels
GET /api/v1/hotels/search
  ?city=NYC&check_in=2024-12-20&check_out=2024-12-23&guests=2&price_max=300
  -> [{ "hotel_id": 42, "name": "...", "available_rooms": 3, "price_from": 199.00, "rating": 4.8 }]

// Get room availability for a specific hotel
GET /api/v1/hotels/{hotel_id}/rooms/availability
  ?check_in=2024-12-20&check_out=2024-12-23
  -> [{ "room_type_id": 7, "name": "Deluxe King", "available": 2, "price_per_night": 199.00 }]

// Create booking (reserves room, starts 10-min payment window)
POST /api/v1/bookings
  { "hotel_id": 42, "room_type_id": 7, "check_in": "2024-12-20", "check_out": "2024-12-23", "num_rooms": 1 }
  -> { "booking_id": "uuid", "status": "PENDING", "total_price": 597.00, "expires_at": "..." }

// Confirm with payment
POST /api/v1/bookings/{id}/confirm
  { "payment_method_id": "pm_..." }
  -> { "booking_id": "uuid", "status": "CONFIRMED", "confirmation_number": "HB-29471" }

// Cancel booking
POST /api/v1/bookings/{id}/cancel
  -> { "status": "CANCELLED", "refund_amount": 597.00, "refund_eta": "3-5 business days" }
```

---

## Architecture

```
Client
  |
  +-- Search Request
  |     -> Search Service
  |           +-- Elasticsearch (geo + full-text, hotel metadata)
  |           +-- Availability Index (pre-computed, Redis or Cassandra)
  |           -> Returns candidate hotel_ids with available room counts
  |
  +-- Book Request
        -> Booking Service (write-heavy, primary PostgreSQL)
              +-- Availability check + reservation (PostgreSQL exclusion constraint)
              +-- INSERT booking (status=PENDING, expires_in=10min)
              +-- Payment Service (Stripe authorize)
              +-- On payment success: UPDATE booking SET status=CONFIRMED
              +-- Kafka: booking.confirmed -> email, hotel_notification, analytics
              +-- On failure: Saga compensate -> release reservation

Background Workers:
  - Hold expiry: every 5 min, DELETE pending bookings WHERE expires_at < NOW()
    (constraint release happens automatically as rows are deleted)
  - Availability sync: after each confirmed booking, update Redis availability cache
  - Cancellation refund worker: async Stripe refund API calls with retry

State machine:
  PENDING (10-min window) -> CONFIRMED -> COMPLETED (after check-out date)
                         \-> CANCELLED (manual or expired)
```

---

## Data Model

```sql
-- Option A: Date-slot table (explicit per-room-type per-date)
CREATE TABLE room_availability (
    room_type_id    BIGINT,
    date            DATE,
    hotel_id        BIGINT,
    total_rooms     INT,
    booked_rooms    INT DEFAULT 0,
    price           DECIMAL(10,2),
    PRIMARY KEY (room_type_id, date),
    CONSTRAINT no_overbooking CHECK (booked_rooms <= total_rooms)
);
-- Pro: fast availability query (SELECT WHERE date BETWEEN AND booked_rooms < total_rooms)
-- Con: 10M rooms × 365 = 3.65B rows; manageable but large

-- Option B: Bookings with exclusion constraint (recommended — simpler, leverages PG)
CREATE TABLE bookings (
    booking_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id         BIGINT NOT NULL,
    user_id         BIGINT NOT NULL,
    check_in        DATE NOT NULL,
    check_out       DATE NOT NULL,
    status          VARCHAR(20) NOT NULL,  -- PENDING/CONFIRMED/CANCELLED/EXPIRED
    total_price     DECIMAL(10,2),
    payment_id      VARCHAR(64),
    idempotency_key VARCHAR(64) UNIQUE,
    expires_at      TIMESTAMPTZ,           -- 10 min after creation for PENDING
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- The key constraint: prevent any two active bookings for the same room with overlapping dates
-- PostgreSQL's GiST (Generalized Search Tree) extension handles date-range overlap natively
CREATE EXTENSION IF NOT EXISTS btree_gist;
ALTER TABLE bookings ADD CONSTRAINT no_double_booking
    EXCLUDE USING gist (
        room_id WITH =,
        daterange(check_in, check_out, '[)') WITH &&
    ) WHERE (status != 'cancelled' AND status != 'expired');
-- This constraint fires on INSERT and UPDATE; overlapping bookings fail with ConstraintViolation

CREATE INDEX ON bookings(room_id, check_in, check_out) WHERE status IN ('pending', 'confirmed');
CREATE INDEX ON bookings(user_id, created_at DESC);
```

---

## Key Design Decisions

**1. PostgreSQL exclusion constraint for double-booking prevention**
Naive approach: `SELECT COUNT(*) FROM bookings WHERE room_id=X AND dates_overlap`. Between the SELECT and INSERT, another transaction inserts an overlapping booking → double-booking. Fix: PostgreSQL `EXCLUDE USING gist` constraint enforces non-overlap at the database level, atomically. Any INSERT that would create an overlap is rejected with a constraint violation before it commits. This makes double-booking impossible regardless of application logic or concurrency. The `WHERE status != 'cancelled'` clause ensures cancelled bookings don't block future ones.

**2. Two-phase booking: PENDING → CONFIRMED with 10-minute window**
INSERT booking with status=PENDING (constraint prevents overlap during hold period). User has 10 minutes to complete payment. If payment succeeds: UPDATE status=CONFIRMED. If payment fails or times out: background worker deletes/expires the PENDING booking → releases the date range. The exclusion constraint applies to PENDING as well, so two users can't hold the same room simultaneously. This gives users a secure hold without long-term inventory lock.

**3. Elasticsearch for search, PostgreSQL for booking**
Search query: "hotels in NYC, $100-300/night, pool, check-in Dec 20": full-text + geo + filters → Elasticsearch handles this well. But Elasticsearch is eventually consistent — availability data can be 10-30 seconds stale. Booking service always confirms against PostgreSQL at the moment of INSERT. If Elasticsearch showed 3 rooms but PostgreSQL confirms 0 at booking time: return "no longer available, please search again." Staleness in search is acceptable; staleness in the booking write path is not.

**4. Cancellation Saga: compensating transactions for refund**
Cancel request → Booking Service: (1) UPDATE booking SET status=CANCELLED. (2) Async: call Stripe refund API. (3) If Stripe refund fails: retry with exponential backoff via DLQ. (4) If Stripe refund succeeds: UPDATE booking SET refund_amount, refund_processed_at. The Saga uses the outbox pattern: the refund request is written to an `outbox_events` table in the same transaction as the status update. A worker drains the outbox → calls Stripe → marks complete. Cancellation is confirmed immediately to the user; refund is async (3-5 business days per Stripe).

---

## Deep Dives

**Availability pre-computation for search**
The search query "find rooms in NYC, Dec 20-23" can't scan 10M bookings in real-time. Solution: maintain a `availability_summary` table or Redis hash: `HSET avail:{hotel_id} {room_type_id}:{date} {available_count}`. Refreshed by Kafka consumer after each booking event. Search service reads from this cache (10-30s stale) → returns results. Counts are approximate for display purposes; actual availability confirmed at booking time. For very accurate counts: use the date-slot Option A table with a covering index — allows a fast range query.

**Rate plans and dynamic pricing**
Hotels set different prices per: room type, date range (weekend premium), advance purchase window (10% off if booked 30+ days ahead), corporate rate codes. Implemented as a `rate_plans` table: `(hotel_id, room_type_id, date_range, discount_type, amount)`. Pricing Service resolves the applicable rate at search time given check-in/out dates and any user rate codes. Price at booking is locked in — stored in the `bookings` table at PENDING creation — so price changes don't affect in-progress bookings.

---

## Failure Scenarios

| Failure | Impact | Mitigation |
|---------|--------|------------|
| Payment fails after PENDING booking | Room locked for 10 min | Background worker expires PENDING holds; room released automatically |
| DB write fails after Stripe authorize | Charged but no booking | Outbox + authorize-then-capture; release auth if DB fails |
| Elasticsearch stale data shows wrong count | User clicks "Book" but room full | Return 409 "no longer available"; prompt to search again |
| Cancellation refund Stripe call fails | Booking cancelled, no refund | DLQ + retry with exponential backoff; ops alert after 3 failures |
| Hotel-initiated cancellation | Guest stranded | Generate new search suggestions; apply full refund + compensation credit |

---

## Interviewer Follow-Up Questions

**On double-booking prevention:**
- "Two users reserve the same room for the same dates at the same time. Walk me through exactly what happens at the DB level." → Both transactions issue `INSERT INTO bookings (room_id=X, check_in=Dec20, check_out=Dec23, status='pending')`. PostgreSQL evaluates the exclusion constraint on each INSERT. The first INSERT to commit acquires the GiST index slot — its date range `[Dec20, Dec23)` is registered. When the second INSERT arrives, the constraint check finds overlap with the existing pending booking → raises `ExclusionViolationError` → second transaction rolls back. First user gets the hold; second user gets a 409. If both arrive within the same microsecond: PostgreSQL serializes via the GiST index lock — one waits, one proceeds, then the waiter fails the constraint check.
- "Your exclusion constraint prevents double-booking. But two users search and both see '1 room available'. How do you handle this UX?" → This is the read-write gap: availability counts shown in search are approximate. When user A books the last room: B's search results become stale. When B tries to book: the exclusion constraint fires → B gets "Room no longer available, please search again." This is a graceful degradation — the constraint is correct, the search was informational. Redirect B to similar hotels nearby (Elasticsearch query) to recover the UX.

**On availability and counts:**
- "How do you show accurate 'X rooms left' counts without querying the full bookings table?" → Maintain a materialized view or denormalized counter. Option A (date-slot table): `available_rooms = total_rooms - booked_rooms` is directly queryable per (room_type, date). Option B (bookings only): compute on the fly with `SELECT total_rooms - COUNT(*) FROM bookings WHERE room_id=X AND dates_overlap AND status IN ('pending','confirmed')` — fast with the right index, but under high concurrency, counts can be slightly stale. For search display, cache the count in Redis; for booking confirmation, always recompute from the DB.

**On pricing and rate plans:**
- "A hotel wants to charge 20% more on weekends and 10% less for bookings made 30+ days in advance. How does your system support this?" → `rate_plans` table: `(hotel_id, room_type_id, start_date, end_date, day_of_week_mask, advance_days_min, multiplier)`. Pricing Service evaluates all applicable rate plans for a booking request, computes the effective price per night, and returns the total. Priority rules (corporate rate > advance purchase > weekend surcharge) are configured per hotel. Price is locked at PENDING creation time — stored in the `bookings` row — so subsequent rate plan changes don't affect open holds.

**On corporate rates:**
- "How does a corporate travel booking work differently from a direct booking?" → Corporate users are associated with a `corporate_account_id` which has negotiated rate codes with hotels. At search time: Pricing Service checks if the user's corporate account has a rate code for the hotel → applies the negotiated discount. Rate codes are stored in `corporate_rate_agreements(hotel_id, corporate_id, rate_code, discount_percent, validity_start, validity_end)`. The rate code is included in the booking record for reporting (corporate travel reports show spend per company). Corporate bookings may also allow billing to a company card (central billing) rather than the individual's card.

**On hotel-initiated cancellations:**
- "The hotel cancels your booking 2 days before your trip. How does this flow work?" → Hotel-initiated cancellation: Hotel Management Portal calls `DELETE /api/v1/hotel/bookings/{id}` with cancellation reason. Booking Service: (1) UPDATE status=HOTEL_CANCELLED. (2) Trigger full refund via Stripe. (3) Kafka event → Compensation Service → issue platform credit (2x the booking value per policy). (4) Search Service → offer guest 3 comparable alternatives in same area and dates. The key difference from user-initiated cancellation: hotel-initiated always triggers full refund regardless of cancellation policy, plus compensation.

**On price lock:**
- "You show me a price of $199/night. I take 9 minutes to enter payment. The hotel raises the price to $249. Do I pay $199 or $249?" → $199 — price is locked at PENDING creation. The `bookings` table stores `price_per_night` and `total_price` at the time of the PENDING insert. The booking confirmation uses these stored values, not a re-fetch from the rate plan. This is standard practice (Booking.com, Airbnb do this). Edge case: if the PENDING booking expires and the user tries again, they'll see the new price.
