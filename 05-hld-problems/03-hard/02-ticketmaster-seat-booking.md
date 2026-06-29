---
module: 05-hld-problems
topic: Hard
status: interview-ready
tags: [05-hld-problems, system-design, hard]
---
# Design Ticketmaster (Seat Booking at Scale)

> **Difficulty**: Hard
> **Topics**: Concurrency, Redis NX Locks, Virtual Queue, CQRS
> **Time**: 60 min
> **Companies**: Amazon, Stripe, Common

---

## Clarifying Questions

1. "Are we building the full Ticketmaster platform or focused on the seat booking flow during an on-sale?"
2. "What's the expected peak — 1M concurrent users for a Taylor Swift on-sale?"
3. "How many seats per event — 50K for a stadium? 500 for a small venue?"
4. "Do we need a virtual waiting room, or can we let all users hit the system simultaneously?"
5. "What's the seat hold window — 10 minutes to complete checkout?"
6. "Do we need assigned seating, or is it general admission?"

---

## Back-of-Envelope

```
1M concurrent users for Taylor Swift on-sale
50K seats in the venue
5-minute on-sale window

Peak load:
  1M users hitting "Get Tickets" simultaneously
  500K RPS in the first 10 seconds (before the queue absorbs traffic)

Seat hold operations:
  At most 50K holds in-flight at once (one per seat)
  Each hold has 10-minute TTL

Storage:
  Event metadata: small (thousands of events)
  Seat inventory: 50K seats × ~200 bytes = 10 MB per event (trivially small)
  Bookings: 50K confirmed tickets × 1 KB = 50 MB per event

Throughput constraint:
  PSP (Stripe) can handle ~5K TPS → bottleneck is payment, not seat selection
  Design must queue users and serialize seat claims
```

---

## APIs

```
// Browse events (high read volume, cacheable)
GET /api/v1/events/{event_id}/seats
  -> { "available_count": 48234, "seat_map_url": "...", "on_sale_at": "..." }

// Claim seat (write path, must be atomic)
POST /api/v1/events/{event_id}/seats/{seat_id}/hold
  { "user_id": "user_12345" }
  -> { "hold_token": "jwt_...", "expires_at": "2024-01-15T10:10:00Z", "price": 199.00 }

// Confirm purchase (payment)
POST /api/v1/bookings
  { "hold_token": "jwt_...", "payment_method_id": "pm_..." }
  -> { "booking_id": "...", "confirmation_number": "TM-29471", "status": "CONFIRMED" }

// Join waiting room queue
POST /api/v1/events/{event_id}/queue
  { "user_id": "user_12345" }
  -> { "position": 45821, "estimated_wait_min": 12 }
```

---

## Architecture

```
1M users
  |
  v
Virtual Waiting Room (Redis ZADD queue:{event_id} {timestamp} {user_id})
  |  Batch admit 500 users every second (ZRANGE queue 0 499)
  |  Only admitted users see the seat selection UI
  v
Browse Service (CQRS read side)
  +-- Elasticsearch / cached seat map
  +-- Redis bitfield: GETBIT seats:available:{event_id} {seat_idx}
  +-- Read replicas for availability counts
  |
  v
Booking Service (CQRS write side)
  +-- Redis NX hold: SET seat:{eid}:{sid} {user_id} NX EX 600
  |     If OK: seat held; return hold_token (JWT with seat_id, expires_at)
  |     If nil: seat taken → return 409
  +-- PostgreSQL: INSERT booking (status=HELD, hold_token, expires_at)
  +-- Payment: Stripe authorize → on success, capture
  +-- PostgreSQL: UPDATE booking SET status=CONFIRMED
  +-- Redis: DEL seat:{eid}:{sid} (or let TTL handle it on failure)
  +-- Kafka: booking.confirmed → email, analytics, inventory sync

Background Workers:
  - Hold expiry: scan HELD bookings WHERE expires_at < NOW(); release Redis key + DB record
  - Queue admission: every 1s, ZRANGE queue:{event_id} 0 499 → issue admission tokens
```

---

## Data Model

```sql
CREATE TABLE events (
    event_id        UUID PRIMARY KEY,
    name            VARCHAR(255),
    venue_id        UUID,
    starts_at       TIMESTAMPTZ,
    on_sale_at      TIMESTAMPTZ,
    total_seats     INT
);

CREATE TABLE seats (
    seat_id         UUID PRIMARY KEY,
    event_id        UUID REFERENCES events(event_id),
    section         VARCHAR(20),
    row             VARCHAR(10),
    number          INT,
    price_tier      VARCHAR(20),
    price           DECIMAL(10,2)
);

CREATE TABLE bookings (
    booking_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id        UUID,
    seat_id         UUID,
    user_id         BIGINT,
    hold_token      VARCHAR(512) UNIQUE,
    status          VARCHAR(20),  -- HELD / CONFIRMED / RELEASED / EXPIRED
    price           DECIMAL(10,2),
    payment_id      VARCHAR(64),
    expires_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- DB backup prevents any double-booking if Redis is bypassed
CREATE UNIQUE INDEX ON bookings(event_id, seat_id) WHERE status IN ('held', 'confirmed');

-- Seat availability as a Redis bitfield (fast BITCOUNT for remaining seats)
-- SETBIT seats:available:{event_id} {seat_index} 1   (seat available)
-- SETBIT seats:available:{event_id} {seat_index} 0   (seat held/confirmed)
-- BITCOUNT seats:available:{event_id}                 (count available)
```

---

## Key Design Decisions

**1. Redis NX for seat holds — primary concurrency mechanism**
`SET seat:{event_id}:{seat_id} {user_id} NX EX 600` — NX means "only set if key does not exist". Only one Redis client wins for any given seat; all others get `nil` immediately. TTL of 600 seconds (10 min) ensures hold releases automatically if checkout is abandoned. Redis is single-threaded per key → no race condition possible. This is the primary lock; PostgreSQL's unique constraint is a secondary safety net.

**2. Virtual waiting room — prevents the stampede problem**
Without a queue: 1M users simultaneously hit the booking endpoint → 950K Redis calls fail with nil → 950K users immediately retry → thundering herd. With a queue: users are ordered by arrival time (ZADD score = unix timestamp). A batch admission process lets in 500 users/second. Each user receives an admission token valid for 5 minutes. Only admitted users can call the seat hold API. The waiting room absorbs the spike and delivers a smooth, predictable load to the booking service.

**3. CQRS: Browse vs. Booking services are separate**
Browse (read) is 10-100× more traffic than booking (write). Browse uses: read replicas, Elasticsearch for seat map search, Redis bitfield for fast availability counts. Booking uses the primary PostgreSQL + Redis. Separating the services prevents high read traffic from contending with the critical write path. The seat map can be slightly stale (10-second lag) — that's fine; staleness is handled with a "seat no longer available" error at hold time.

**4. Defense in depth: Redis NX → DB unique constraint → idempotent service**
Three layers: (1) Redis NX is the fast path — rejects 99.9% of duplicate holds in <1ms. (2) PostgreSQL unique index on (event_id, seat_id) WHERE status IN ('held','confirmed') — if Redis is bypassed or restarts, DB constraint prevents double-booking at write time. (3) Idempotent hold service — if the same hold_token is submitted twice (retry), return the existing hold. Three independent safety layers; system must survive any single layer failing.

---

## Deep Dives

**Hold expiry and inventory release**
When a user abandons checkout: the Redis key expires after 10 min (TTL handles it automatically). For the DB: a background worker runs every 5 minutes, scans `bookings WHERE status='HELD' AND expires_at < NOW()`, sets status='EXPIRED', and optionally fires a "seat available" event to real-time UI updates. The DB expiry worker is critical: if Redis restarts and loses key state, the DB becomes the authoritative source of hold state.

**Real-time seat map updates**
Seat map UI must reflect held/available state near-real-time. Options: (a) WebSocket push from Booking Service on each hold/release — high fan-out, expensive at 1M connected users. (b) Client polls every 5-10s — simpler, 10-second lag acceptable. (c) Server-Sent Events (SSE) for lightweight push. For 1M concurrent users, polling at 10s intervals is 100K requests/sec — manageable with read replicas and caching. SSE is the middle ground: push updates, lower overhead than WebSocket.

---

## Failure Scenarios

| Failure | Impact | Mitigation |
|---------|--------|------------|
| Redis restart during on-sale | Lose all in-flight holds | Redis AOF persistence; on restart, reload from DB HELD bookings |
| Payment fails after seat held | Seat locked for 10 min | TTL auto-releases; user can retry hold; ops can manually release |
| Queue service crashes | Users bypass queue | Queue tokens have short TTL (5 min); regenerate on reconnect |
| DB write fails after Redis hold | Inconsistent state | Saga compensating transaction: if DB insert fails, DEL the Redis key |
| Bots claim seats faster than humans | Inventory exhausted instantly | Rate limit by device fingerprint, require CAPTCHA before queue entry |

---

## Interviewer Follow-Up Questions

**On seat holds and concurrency:**
- "Two users click 'Hold' on the same seat at the exact same millisecond. What happens?" → Redis processes commands serially per slot. One `SET NX` succeeds; the second runs immediately after and finds the key exists → returns `nil`. The first user gets the hold; the second user gets a 409 "seat no longer available" response. No race condition is possible because Redis's single-threaded command execution serializes all operations on a key.
- "Your Redis TTL expires but the user is still checking out. How do you handle this?" → The hold_token is a JWT that contains `expires_at`. The booking service validates the token at payment time: if `expires_at < NOW()`, reject with "hold expired, please select seat again." The UX must show a countdown timer to the user. 10 minutes is chosen because payment (Stripe) completes in <30 seconds — the timeout is generous. For slow users: consider extending the hold on an explicit "I'm still here" heartbeat from the UI.
- "What if Redis goes down during an on-sale?" → The Redis key-value store is in-memory with AOF (Append-Only File) persistence. On restart: Redis replays the AOF log → hold keys are restored. For the brief period Redis is down: the booking service falls back to the DB unique constraint as the concurrency guard (slower, ~5-10ms vs <1ms, but correct). Alert ops immediately; restore Redis within 30 seconds for an on-sale event.

**On the virtual waiting room:**
- "How do bots get around your virtual waiting room?" → Bots register thousands of accounts and join the queue with each. Mitigations: (1) Require phone/email verification before queue entry — raises cost per account. (2) CAPTCHA at queue entry — humans pass, bots fail. (3) Browser fingerprinting (TLS fingerprint, canvas fingerprint) — multiple queue positions from the same fingerprint are suspicious. (4) Purchase velocity limit: max 4 tickets per verified account per event. (5) Monitor admission token usage — unused tokens (joined queue, never used for booking) may indicate bots.
- "Your waiting room queue has 1M entries in Redis. That's expensive. How do you optimize?" → A Redis sorted set with 1M entries ≈ 100 MB (each entry: 8-byte score + variable-length user_id ≈ ~100 bytes = 100 MB). That's trivially small for Redis. The queue is ephemeral (on-sale window is 5-30 minutes), so memory is released quickly. If multiple events run simultaneously: partition by event_id (separate sorted sets). 100 events with 1M users each = 100 sorted sets × 100 MB = 10 GB — still within a Redis cluster's capacity.

**On pricing and fees:**
- "How does dynamic pricing (price floors/ceilings per section) work in your data model?" → Prices are stored per seat tier (section), not per individual seat. The `seats` table has a `price_tier` column referencing a `price_tiers` table. Dynamic pricing updates the `price_tiers` table; all seats in that tier immediately reflect the new price. At hold time: capture `price_at_hold` in the booking record — price is locked at hold time, not at confirmation. This matches user expectation ("I saw $199 when I clicked hold").

**On the seat map bitset:**
- "You use a Redis bitfield for available seat counts. Walk me through the operations." → Initialize: `SETBIT seats:available:{event_id} {seat_index} 1` for all 50K seats at event creation. Hold: `SETBIT seats:available:{event_id} {seat_index} 0`. Release: `SETBIT seats:available:{event_id} {seat_index} 1`. Available count: `BITCOUNT seats:available:{event_id}` → O(N/8) bit scan, but N is only 50K/8 = 6KB — effectively O(1). This is faster and more memory-efficient than maintaining a Redis set of available seat IDs.
