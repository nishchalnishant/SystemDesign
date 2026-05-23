---
module: 05-hld-problems
topic: Hard
status: unread
tags: [05-hld-problems, system-design, hard]
---
# Design Ticketmaster / Seat Booking System

## Problem Statement

Design a distributed seat booking system that can handle:
- 1M concurrent users attempting to book 50,000 seats for a high-demand concert in a 5-minute on-sale window
- Zero double-bookings (each seat sold to exactly one buyer)
- Fair access (no user disadvantaged by server routing)
- Sub-second response time under peak load

---

## Functional Requirements

- Browse events and view seat maps
- Reserve a seat (hold it for 10 minutes while user completes payment)
- Confirm booking after successful payment
- Release expired reservations back to available inventory
- View booking history

## Non-Functional Requirements

- **Availability**: 99.99% for browsing, 99.9% for booking
- **Consistency**: Strong consistency for seat assignment — no double-booking ever
- **Throughput**: 200,000 booking attempts/second at peak
- **Latency**: Seat reservation response < 500ms at p99
- **Scale**: 10M events, 100M users globally

---

## Capacity Estimation

```
Concert on-sale event: 50,000 seats
1M users attempting to book simultaneously
→ each user clicks "Buy" ~5 times (retries, different seats)
→ 5M requests / 300 seconds = ~16,700 RPS average
→ Peak burst (first 10 seconds): 500,000 RPS

Payment processing: max 50,000 payments total
Seat reservation hold: 10 minutes per hold

Database seat rows: 50,000 per event
A 10M event catalog: ~500M seat rows (hot: only current/upcoming events)
```

---

## Core Design Challenge: Preventing Double-Booking

The central problem: Seat A has inventory=1. Two users request it simultaneously. Both see "available", both proceed to payment, both pay. Now you've oversold.

This is a **distributed inventory reservation problem** — the hardest part of the design.

### Option 1: Database-Level Locking (pessimistic)

```sql
BEGIN;
SELECT * FROM seats WHERE seat_id = :id AND event_id = :eid AND status = 'available' FOR UPDATE;
-- If row returned: update status to 'held', set holder_id, held_until
UPDATE seats SET status = 'held', holder_id = :user, held_until = NOW() + INTERVAL '10 min'
WHERE seat_id = :id AND event_id = :eid AND status = 'available';
COMMIT;
```

`SELECT FOR UPDATE` acquires a row-level exclusive lock. Only one transaction can hold it.

**Problem**: At 500K RPS, all threads contend on the same rows. Lock wait queues pile up. DB connection pool exhausts. Cascade failure.

### Option 2: Optimistic Locking (CAS in DB)

```sql
UPDATE seats
SET status = 'held', holder_id = :user, held_until = NOW() + 600, version = version + 1
WHERE seat_id = :id AND event_id = :eid AND status = 'available' AND version = :expected_version;
-- Check rows_affected: if 0, seat was taken by someone else (retry with different seat)
```

No blocking locks. If two users attempt simultaneously:
- User A's UPDATE succeeds: `rows_affected = 1`
- User B's UPDATE fails: `rows_affected = 0` (version/status no longer matches)
- User B must retry with a different seat

**Better for throughput** — no lock contention. But DB is still the bottleneck at 500K RPS.

### Option 3: Redis-Based Distributed Lock (recommended for peak load)

Move the hot inventory check **out of the DB** and into Redis:

```
Redis Key: seat:{event_id}:{seat_id}
Value: {user_id, hold_expires_at}
TTL: 600 seconds (10 minute hold)

Command:
SET seat:{event_id}:{seat_id} {user_id} NX EX 600
→ NX = only set if key does NOT exist
→ Returns OK if acquired, nil if already held
```

`SET NX EX` is atomic in Redis (single command, no MULTI/EXEC needed). Only one process acquires the lock per seat.

**Hold flow**:
1. Client sends `POST /reserve {event_id, seat_id}`
2. Service calls `SET seat:{eid}:{sid} {user_id} NX EX 600`
3. If OK: return hold confirmation, user proceeds to payment
4. If nil: return 409 Conflict, user must pick another seat
5. On payment success: write final booking to PostgreSQL, delete Redis key (or let TTL expire)
6. On payment failure / timeout: TTL expires automatically, seat returns to available pool

**Seat availability query**:
```
EXISTS seat:{event_id}:{seat_id}  →  true = held/booked, false = available
```

For seat map display: batch `EXISTS` calls with Redis pipeline, or maintain a Redis Set of held seats per event.

---

## Architecture

```
                           ┌───────────────┐
                           │   CDN + WAF   │  ← static event pages, seat maps
                           └───────┬───────┘
                                   │
                           ┌───────▼───────┐
Users ──────────────────►  │  API Gateway  │  ← auth, rate limiting, routing
                           └──────┬────────┘
              ┌───────────────────┼───────────────────┐
              ▼                   ▼                   ▼
     ┌────────────────┐  ┌─────────────────┐  ┌───────────────┐
     │  Browse Service │  │ Booking Service  │  │ Payment Service│
     │  (read-heavy)   │  │  (write-heavy)   │  │  (Stripe/PayPal)│
     └────────┬────────┘  └────────┬────────┘  └───────┬───────┘
              │                    │                    │
    ┌─────────▼──────┐   ┌────────▼────────┐           │
    │  PostgreSQL     │   │  Redis Cluster  │           │
    │  (event catalog │   │  (seat holds,   │           │
    │   event/user DB)│   │   rate limiting)│           │
    └─────────────────┘   └────────┬────────┘           │
                                   │                    │
                          ┌────────▼────────────────────▼─┐
                          │         PostgreSQL              │
                          │   (confirmed bookings, payments)│
                          └────────────────────────────────┘
                                   │
                          ┌────────▼──────┐
                          │  Kafka         │  ← booking events for notifications,
                          └───────┬────────┘    analytics, invoices
                                  │
                     ┌────────────▼────────────┐
                     │  Notification Service    │
                     │  (email, SMS, push)       │
                     └─────────────────────────┘
```

---

## Seat Hold State Machine

```
AVAILABLE
    │
    │  POST /reserve (SET NX in Redis)
    ▼
  HELD  ──── TTL expires (600s, no payment) ────► AVAILABLE
    │
    │  POST /confirm (payment succeeded)
    ▼
CONFIRMED  ──── cannot return to available
    │
    │  edge case: payment refund + cancellation
    ▼
 RELEASED (new row created, original booking marked cancelled)
```

---

## Handling the 1M Rush (Flash Sale Problem)

When 1M users hit "Buy Now" simultaneously at 10:00 AM:

### Problem 1: Database Connection Exhaustion

1M requests → 1M DB connections → DB crashes (max connections ~1000).

**Solution: Virtual Waiting Room**

```
1. Users enter a virtual queue before reaching the booking flow
2. Queue manager (Redis Sorted Set, score = arrival time) admits users in batches
3. Batch size = number of seats available (50,000)
4. All other users get a "you're position X in queue" page (polls queue service)
5. This decouples arrival rate from booking service throughput
```

```
ZADD queue:{event_id} {timestamp} {user_id}
ZRANK queue:{event_id} {user_id}  → user's position
ZRANGE queue:{event_id} 0 49999    → next 50,000 users to admit
```

### Problem 2: Thundering Herd on Seat Status

50,000 seats × 1M users querying seat status = 50B reads/minute. Impossible.

**Solution: Aggressive Caching of Seat Map**

- Cache the full seat availability bitmap per event in Redis (one key, ~6KB for 50K seats as a bitfield)
- Serve seat map from cache with TTL = 5 seconds
- Accept that seat map is stale by up to 5 seconds — user might try to book an "available" seat that was just taken (results in 409, user retries)
- Update Redis bitfield on every hold/confirmation/release

```
SETBIT seats:available:{event_id} {seat_index} 0  ← mark as unavailable on hold
SETBIT seats:available:{event_id} {seat_index} 1  ← mark as available on release
BITCOUNT seats:available:{event_id}               ← total available seats
```

### Problem 3: Redis Single-Key Hotspot

All 1M users hitting `seat:{event_id}:{seat_id}` for the same popular seats.

**Solution: Redis Cluster with Hash Tags**

Force all keys for one event to the same Redis shard using hash tags:
```
{event_12345}:seat:A1
{event_12345}:seat:A2
...
```

All keys with the same `{event_12345}` tag land on the same shard → local MULTI/EXEC possible if needed. But at 500K RPS, even a single Redis shard can handle ~1M simple SET/GET ops/second.

---

## Payment and Hold Confirmation

```
1. POST /reserve → Redis NX success → return hold_token (JWT or UUID) + expiry time
2. Client redirects to payment page
3. User enters payment details → POST /payment with hold_token
4. Payment Service calls Stripe/PayPal
5. On payment success:
   a. Begin DB transaction:
      - INSERT INTO bookings (user_id, event_id, seat_id, hold_token, status='confirmed')
      - No need to check Redis — the hold_token was issued by this system
   b. Publish booking_confirmed event to Kafka
   c. Return 200 to client
6. On payment failure:
   a. No DB write
   b. Redis key TTL continues — seat released automatically at expiry
   c. Optionally: DEL Redis key immediately to release seat faster
```

**Idempotency**: `hold_token` is the idempotency key. If the client retries payment, the payment service checks if `hold_token` already has a confirmed booking — prevents double-charging.

---

## Overbooking Protection: Belt and Suspenders

Even with Redis NX, edge cases exist:
- Redis Cluster failover during a SET — is the key durable?
- Network partition between booking service and Redis

**Defense-in-depth**:

1. **Redis NX** — primary guard (fast, ~0.1ms)
2. **Database unique constraint** — secondary guard:
   ```sql
   CREATE UNIQUE INDEX ON bookings(event_id, seat_id)
   WHERE status IN ('held', 'confirmed');
   ```
   Even if two Redis NX calls somehow both succeed, only one DB INSERT will win. The loser gets a unique constraint violation → return 409.
3. **Booking service is idempotent** — same `hold_token` can be submitted multiple times, only one booking row created.

---

## Seat Release / Expiry Jobs

Held seats with expired TTLs in Redis are automatically released (key expires). But the DB also needs cleanup if any hold was written there:

```
Scheduled job (runs every 60 seconds):
UPDATE seats SET status = 'available'
WHERE status = 'held' AND held_until < NOW();

OR: use Redis keyspace notifications to trigger release on TTL expiry
```

For high-volume events: use Kafka with a delayed-processing topic (set message TTL = hold duration) to trigger expiry events.

---

## Scaling Read-Heavy Browse Traffic

Browse (event catalog, seat maps) is 99% of traffic — reads should never compete with writes.

- **Event catalog**: PostgreSQL read replicas + aggressive CDN caching (event info rarely changes)
- **Seat map**: Redis bitfield (updated on holds/releases), served via a read-heavy microservice
- **CQRS split**: Browse Service reads from read replicas + Redis; Booking Service writes to primary PostgreSQL + Redis

---

## Database Schema (Simplified)

```sql
CREATE TABLE events (
  event_id    UUID PRIMARY KEY,
  name        TEXT,
  venue_id    UUID,
  event_time  TIMESTAMPTZ,
  total_seats INT
);

CREATE TABLE seats (
  seat_id     UUID PRIMARY KEY,
  event_id    UUID REFERENCES events,
  section     TEXT,
  row         TEXT,
  number      INT,
  price_cents INT
);

CREATE TABLE bookings (
  booking_id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     UUID NOT NULL,
  event_id    UUID NOT NULL,
  seat_id     UUID NOT NULL,
  hold_token  UUID UNIQUE NOT NULL,
  status      TEXT CHECK (status IN ('held', 'confirmed', 'cancelled')),
  held_until  TIMESTAMPTZ,
  confirmed_at TIMESTAMPTZ,
  payment_id  TEXT,
  CONSTRAINT no_double_booking UNIQUE (event_id, seat_id) DEFERRABLE
);

CREATE INDEX ON bookings(event_id, status);
CREATE INDEX ON bookings(hold_token);
```

---

## Trade-off Discussion

| Decision | Alternative | Why This Choice |
|---|---|---|
| Redis NX for holds | DB pessimistic lock | 10x higher throughput, no DB connection exhaustion |
| Redis NX for holds | DB optimistic lock | Faster (0.1ms vs 5ms), no retry storms under high contention |
| Virtual waiting room | Reject excess traffic | Fairer experience, prevents thundering herd |
| Bitfield for seat map | Query DB on each request | Reduces DB load by 99%; acceptable 5s staleness |
| DB unique constraint as backup | Trust Redis entirely | Defense-in-depth; Redis cluster failover is possible |
| Kafka for booking events | Synchronous notification | Decouples booking latency from email/SMS delivery |

---

## Interview Discussion Points

**Q: What if Redis cluster fails during peak on-sale?**
All new seat holds fail. Options: (1) fall back to DB pessimistic locking — throughput drops 10x, may cascade; (2) activate read-only mode, serve "system at capacity" until Redis recovers; (3) pre-provision Redis Sentinel with 3 replicas for this event.

**Q: How do you handle VIP presale vs general on-sale?**
Pre-generate hold tokens for VIP users before on-sale starts. VIP hold tokens bypass the waiting room queue and go directly to payment. General sale opens after VIP window.

**Q: Can you scale the booking service horizontally?**
Yes — the booking service is stateless. All state is in Redis (holds) and PostgreSQL (confirmed bookings). Scale to 100+ instances behind a load balancer.

**Q: How do you prevent users from hoarding seats by holding many at once?**
Rate limit: each user can have at most 2-4 active holds at a time. Enforced with a Redis counter per user: `INCR holds:{user_id}` with TTL matching the hold duration.

---

## See Also

- `02-building-blocks/distributed-locks.md` — Redis SET NX, Redlock, fencing tokens
- `02-building-blocks/rate-limiting.md` — Rate limiting for booking attempts
- `09-patterns/saga-pattern.md` — Saga for hold → payment → confirm flow
- `09-patterns/two-phase-commit.md` — 2PC vs saga for payment finalization
- `05-hld-problems/01-easy/booking-system.md` — Simpler hotel booking baseline
