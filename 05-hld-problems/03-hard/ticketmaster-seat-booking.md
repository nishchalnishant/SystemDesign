> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design Ticketmaster — the hardest concurrency problem in system design; flash sale seat reservation where millions compete for thousands of seats in seconds.
>
> **Key design decisions:**
> - Seat reservation: two-phase HOLD (lock seat in Redis for 10 min) → PURCHASE (DB commit + payment); Redis SETNX seat_id = user_id for atomic single-winner guarantee
> - Concurrency at flash sale: virtual waiting room (queue users; release N per second into booking flow); prevents thundering herd on seat inventory
> - Seat status: real-time seat map via WebSocket; status states: AVAILABLE → HELD (10 min TTL) → SOLD; broadcast seat_id status changes to all connected clients
> - Inventory atomicity: Redis SETNX (set if not exists) for seat hold — exactly one user wins; if SETNX returns 0, seat is already held
> - Payment flow: held seat → initiate payment (Stripe) → on success, publish SEAT_SOLD event → DB commit booking; compensating transaction on payment failure releases hold
> - Waitlist: users on waitlist notified via WebSocket/push when hold expires (seat released); first in waitlist gets hold offer
> - DB design: events, venues, seats, bookings tables; seat_map indexed by (event_id, section, row, seat_number); booking record is immutable
>
> **Key takeaway:** Redis SETNX + 10-min TTL is the atomic seat locking mechanism — it's the only way to guarantee a single winner under millions of concurrent hold attempts without DB deadlocks.

---
module: 05-hld-problems
topic: Hard
status: complete
tags: [05-hld-problems, system-design, hard, ticketmaster, seat-booking, concurrency, flash-sale, waitlist]
---
# Design a Ticket Booking System (Ticketmaster)

> **Difficulty**: Hard | **Asked at**: Amazon, Ticketmaster, StubHub, Airbnb, Google

---

## Problem Statement

Design a ticket booking system like Ticketmaster. Users browse events, select specific seats from a seating chart, and purchase tickets. The system must handle flash sales where millions of users compete for thousands of seats simultaneously, prevent double-booking under high concurrency, and support a waitlist for sold-out events.

---

## Functional Requirements

1. **Event browsing**: Search events by city, date, category; view available seats on a seating chart
2. **Seat selection**: Reserve a specific seat for a limited time window (hold) while the user completes checkout
3. **Purchase**: Complete payment and confirm booking; seat becomes permanently booked
4. **Flash sales**: Handle millions of concurrent users for high-demand events (Taylor Swift, Super Bowl)
5. **Waitlist**: Join a waitlist for sold-out events; get notified if a seat becomes available
6. **Cancellation**: Cancel a booking up to N hours before the event; seat returns to available pool

---

## Non-Functional Requirements

- **Scale**: 10M concurrent users during flash sales; 100K seat selections/sec at peak
- **Latency**: Seat hold confirmation < 500ms; seating chart load < 1s
- **Consistency**: Two users must never book the same seat — zero double-bookings
- **Availability**: 99.99% — downtime during a Taylor Swift on-sale is catastrophic
- **Fairness**: Users who arrived earlier in the queue should get priority seat selection

---

## Core Entities

| Entity | Key Fields |
|--------|-----------|
| `Event` | event_id, venue_id, name, date, status (on_sale/sold_out/cancelled) |
| `Seat` | seat_id, event_id, section, row, number, type (standard/VIP), price, status |
| `SeatHold` | hold_id, seat_id, user_id, expires_at, status (active/converted/expired) |
| `Booking` | booking_id, user_id, event_id, seat_ids[], total_price, status, created_at |
| `WaitlistEntry` | entry_id, user_id, event_id, position, created_at, notified_at |

---

## API Design

```http
GET /api/v1/events/{event_id}/seats
Response 200: {
  "event_id": "e123",
  "seats": [
    { "seat_id": "s456", "section": "A", "row": "3", "number": "12",
      "status": "available", "price": 150.00 },
    { "seat_id": "s457", "section": "A", "row": "3", "number": "13",
      "status": "held", "price": 150.00 }
  ]
}

POST /api/v1/seats/hold
Body: { "seat_ids": ["s456", "s789"], "user_id": "u101" }
Response 200: { "hold_id": "h202", "expires_at": "2026-06-29T14:35:00Z" }
Response 409: { "error": "seat_unavailable", "seat_id": "s456" }

POST /api/v1/bookings
Body: { "hold_id": "h202", "payment_method_id": "pm303" }
Response 201: { "booking_id": "b404", "status": "confirmed", "total": 300.00 }

POST /api/v1/events/{event_id}/waitlist
Body: { "user_id": "u101" }
Response 201: { "entry_id": "w505", "position": 1243 }

DELETE /api/v1/bookings/{booking_id}
Response 200: { "refund_amount": 300.00, "status": "cancelled" }
```

---

## High-Level Design

```
User Browser
  │ GET /events/{id}/seats → seating chart
  │ POST /seats/hold → reserve seat(s) for 10 minutes
  │ POST /bookings → complete purchase
  ▼
API Gateway → Load Balancer
  │
  ├── Seat Service
  │     │ Check availability, create hold, convert hold to booking
  │     │ Seat status: available → held → booked (or expired → available)
  │     │ Concurrency control: DB row-level locks or Redis atomic ops
  │     │
  ├── Event Service
  │     │ Event metadata, seating chart config
  │     │ Seat availability cached in Redis (read-heavy)
  │     │
  ├── Payment Service
  │     │ Charge payment method; on failure → release hold
  │     │
  └── Waitlist Service
        │ Queue management; fan-out notifications when seats released

Storage:
  PostgreSQL: seats, bookings, holds (source of truth for availability)
  Redis: seat availability cache, hold TTL management, flash-sale queue
  Kafka: booking-events, waitlist-notifications
  S3: seating chart SVGs
```

---

## Deep Dive 1: Seat Locking Under Concurrency

**Problem**: User A and User B both see seat S456 as available and click "Hold" simultaneously. Without coordination, both succeed → double-booking.

**Option 1: Pessimistic locking (SELECT FOR UPDATE)**:
```sql
BEGIN;

SELECT status FROM seats
WHERE seat_id = 's456' AND event_id = 'e123'
FOR UPDATE;  -- row-level lock; other transactions block here

-- Check: if status != 'available', rollback
UPDATE seats SET status = 'held' WHERE seat_id = 's456';

INSERT INTO seat_holds (hold_id, seat_id, user_id, expires_at)
VALUES ('h202', 's456', 'u101', NOW() + INTERVAL '10 minutes');

COMMIT;
```
One transaction wins the lock; the other blocks until commit, then sees `status = 'held'` and returns 409. Safe but blocking — creates contention at high throughput.

**Option 2: Optimistic locking (compare-and-swap)**:
```sql
-- Attempt atomic status transition; only succeeds if current status = 'available'
UPDATE seats
SET status = 'held', version = version + 1
WHERE seat_id = 's456' AND status = 'available' AND version = 7;

-- rows_affected = 1 → success; rows_affected = 0 → someone else got there first → 409
```
No blocking. Concurrent requests race; exactly one wins (the one whose UPDATE modifies the row). Losing requests retry or return 409 immediately. Better for flash sales where contention is high and blocking would cascade.

**Option 3: Redis atomic hold**:
```redis
# NX = only set if not exists; EX = expire after 600 seconds (10 min)
SET seat_hold:s456 "user:u101" NX EX 600
# Returns OK → hold acquired; nil → seat already held
```
Sub-millisecond, no DB load for the hold step. On checkout, confirm in PostgreSQL atomically:
```sql
UPDATE seats SET status = 'booked' WHERE seat_id = 's456' AND status = 'available';
-- Then delete Redis key
```

**Recommended approach**: Redis atomic SET NX for hold acquisition (fast, no DB lock contention during peak). PostgreSQL UPDATE with `WHERE status = 'available'` for final booking (durable confirmation). If the DB UPDATE fails (race), release the Redis hold and return 409.

**Hold expiry**: A Lua script or TTL-based cleanup job scans expired holds and resets `seats.status = 'available'`:
```lua
-- Runs on hold expiry (triggered by Redis keyspace notification or cron)
local seat_id = ARGV[1]
-- Only release if the hold key is actually gone (TTL expired)
if redis.call('EXISTS', 'seat_hold:' .. seat_id) == 0 then
    -- Update DB: reset seat to available
    return 1
end
return 0
```

---

## Deep Dive 2: Flash Sale Handling

**Problem**: Taylor Swift tickets go on sale at 10:00 AM. 5M users hit "refresh" simultaneously. The seating chart endpoint, seat availability reads, and hold requests all spike 1,000x within seconds. How do you prevent the system from collapsing?

**Problem anatomy**:
1. **Read storm**: 5M users loading the seating chart simultaneously
2. **Write storm**: Millions of hold requests for ~50K seats → 99.9% will fail
3. **Thundering herd**: All failures retry immediately, creating cascading load

**Virtual waiting room** (pre-queue):
```
10:00 AM: Sale opens
Users who arrive before 10:00 AM enter a virtual queue
Queue assigns each user a random position (to prevent advantage from clicking fast)
Users are admitted in batches: release 1,000 users/minute from the queue
Users outside the queue see "You are #123,456 in line — estimated wait: 2 hours"
```
This decouples the demand spike from the booking system. The backend only sees 1,000 users/min instead of 5M simultaneously.

**Queue implementation**:
```python
# On sale start: all users who registered get a random queue token
def assign_queue_position(user_id, event_id):
    position = random.randint(1, 10_000_000)  # randomize to prevent gaming
    redis.zadd(f"queue:{event_id}", {user_id: position})

# Admission: every 60s, advance the cutoff and notify next batch
def admit_next_batch(event_id, batch_size=1000):
    current_cutoff = redis.get(f"queue_cutoff:{event_id}") or 0
    new_cutoff = current_cutoff + batch_size
    redis.set(f"queue_cutoff:{event_id}", new_cutoff)
    # Users with position <= new_cutoff get a session token to enter the booking flow
    admitted = redis.zrangebyscore(f"queue:{event_id}", 0, new_cutoff)
    for user_id in admitted:
        redis.setex(f"admitted:{event_id}:{user_id}", 900, "1")  # 15-min window to book
```

**Seating chart caching**: Pre-warm Redis with full seat availability before sale opens. Serve all GET /seats requests from Redis; bypass PostgreSQL entirely during peak. Update Redis on every hold/booking with `DEL seat_hold:{seat_id}` or `SET seat_hold:{seat_id}`.

**Rate limiting per user**: Each admitted user can hold at most 4 seats. Enforce via Redis counter:
```redis
INCR holds:{event_id}:{user_id}
EXPIRE holds:{event_id}:{user_id} 600
# If value > 4, reject with 429
```

**Seat map read scalability**: The seating chart SVG and seat metadata are static per event. Serve via CDN (CloudFront). Only seat statuses are dynamic — served from Redis as a bitfield (1 bit per seat: 0=available, 1=held/booked). For 50K seats: 50K bits = 6.25 KB per event — trivially small, updated with SETBIT.

---

## Deep Dive 3: Waitlist System

**Problem**: Taylor Swift is sold out. 2M users still want tickets. Some bookings will cancel in the days before the event. How do you fairly notify waitlisted users and give them a chance to book?

**Waitlist data model**:
```sql
CREATE TABLE waitlist (
    entry_id    UUID PRIMARY KEY,
    user_id     UUID NOT NULL,
    event_id    UUID NOT NULL,
    position    INT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    notified_at TIMESTAMPTZ,
    status      TEXT NOT NULL DEFAULT 'waiting'  -- waiting/offered/booked/expired
);
CREATE UNIQUE INDEX ON waitlist(event_id, user_id);  -- one entry per user per event
CREATE INDEX ON waitlist(event_id, position);         -- fast position lookup
```

**Position assignment**: Monotonically increasing counter per event stored in Redis:
```redis
INCR waitlist_counter:e123  → 1243  # user's waitlist position
```

**Seat release flow**: When a booking is cancelled:
1. Reset `seats.status = 'available'`; clear Redis hold key
2. Publish `seat-released` event to Kafka with `event_id`, `seat_id`, `seat_type`

**Waitlist notification service** (Kafka consumer):
```python
def on_seat_released(event):
    event_id = event["event_id"]
    seat_type = event["seat_type"]

    # Find next eligible waitlisted user
    entry = db.query("""
        SELECT * FROM waitlist
        WHERE event_id = %s AND status = 'waiting'
        ORDER BY position ASC
        LIMIT 1
        FOR UPDATE SKIP LOCKED  -- skip if another worker is processing this entry
    """, event_id)

    if not entry:
        return  # nobody waiting

    # Mark as offered; give 15-minute window to book
    db.execute("""
        UPDATE waitlist SET status = 'offered', notified_at = NOW()
        WHERE entry_id = %s
    """, entry.entry_id)

    # Issue a time-limited booking token
    token = generate_token(entry.user_id, event_id, seat_id=event["seat_id"])
    redis.setex(f"waitlist_token:{token}", 900, f"{entry.user_id}:{event['seat_id']}")

    # Notify user
    send_push_notification(entry.user_id, f"A seat is available! You have 15 minutes to book.")
    send_email(entry.user_id, booking_url=f"/book?token={token}")
```

**Token expiry**: If the user doesn't book within 15 minutes, the token expires, `waitlist.status` resets to `'waiting'`, and the next user in line is offered the seat:
```python
def on_token_expired(token):
    user_id, seat_id = redis.get(f"waitlist_token:{token}").split(":")
    # Reset user's waitlist status
    db.execute("UPDATE waitlist SET status = 'waiting' WHERE user_id = %s AND event_id = %s",
               user_id, event_id)
    # Re-release the seat to the next person
    kafka.publish("seat-released", {"seat_id": seat_id, "event_id": event_id})
```

**SKIP LOCKED**: PostgreSQL's `FOR UPDATE SKIP LOCKED` prevents multiple Kafka consumer workers from selecting the same waitlist entry simultaneously — essential for correctness when running multiple notification service replicas.

**Fairness guarantee**: Position is assigned at join time via monotonic counter. Notification always goes to the lowest-position `waiting` user. If they don't respond, the seat cascades to the next — no user can be skipped arbitrarily.

---

## Interviewer Questions by Level

**Junior**:
- What is a seat hold? Why do we need a time limit on holds?
- What happens if a user pays but we fail to mark the seat as booked in the database?
- What is a waitlist? How do you decide who gets notified first?

**Mid-level**:
- How do you prevent two users from booking the same seat simultaneously? Compare optimistic vs pessimistic locking.
- How do you expire a seat hold automatically? What are the trade-offs between a cron job and Redis TTL?
- Design the waitlist notification flow — what triggers it, how do you avoid notifying multiple users for the same seat?

**Senior**:
- Design the flash sale architecture for 5M concurrent users hitting the on-sale moment. How does a virtual waiting room work, and how do you implement fair admission?
- How do you serve the seating chart at scale during a flash sale? What is cached, what is dynamic, and how do you keep the two in sync?
- Design the seat hold system using Redis atomic operations. What failure modes exist (Redis crash, network partition between Redis and PostgreSQL) and how do you handle them?
- How do you handle partial payment failures — payment succeeds at the PSP but the DB write to create the booking fails. How do you recover without double-charging?
