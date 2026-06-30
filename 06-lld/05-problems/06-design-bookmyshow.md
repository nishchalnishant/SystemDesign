> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design BookMyShow (Ticket Booking) — a high-frequency LLD problem that tests your ability to handle concurrency (seat locking) and manage complex relationships (Cinemas, Screens, Shows).
>
> **Key concepts:**
> - Core Entities: `Cinema`, `Screen`, `Show`, `Seat`, `Booking`.
> - State Pattern: `Seat` transitions between Available, Locked, and Booked.
> - Concurrency (The core challenge): Two users trying to book the same seat simultaneously. 
>   - DB approach: Row-level locking (Pessimistic: `SELECT FOR UPDATE`, or Optimistic with versioning).
>   - In-memory LLD approach: Use `ConcurrentHashMap` for locks or synchronize the `lockSeat()` method.
> - TTL (Time To Live): When a user selects seats, they are "Locked" for 5-10 minutes. If payment isn't completed, a background job (or delay queue) must revert them to "Available".
>
> **Key takeaway:** The interviewer is looking for how you prevent double-booking. Clearly explain the difference between a `Seat` (physical chair) and a `ShowSeat` (that chair for a specific movie at a specific time). The lock must be on the `ShowSeat`.

---
module: 06-lld
topic: Problems
status: unread
tags: [06-lld, lld, bookmyshow, ticket-booking, state-pattern, seat-lock]
---
# Design BookMyShow

> **Difficulty**: Hard  
> **Asked at**: Amazon, Flipkart, Paytm  
> **Key Patterns**: State (booking status), Strategy (payment), TTL-based seat locking, Template Method (booking flow)

---

## Understanding the Problem

Design a movie ticket booking system where users can browse shows, select and temporarily lock seats, pay to confirm their booking, and cancel if needed — with the system preventing double-booking under concurrent load.

---

## Clarifying Questions

**You**: "Should we support multiple movies, multiple halls, multiple shows?"  
**Interviewer**: "Yes — a Hall screens different Shows for different Movies at different times."

**You**: "How long can a user hold seats before payment?"  
**Interviewer**: "10 minutes. If payment isn't completed within that window, release the seats."

**You**: "What payment methods should we support?"  
**Interviewer**: "Abstract it away — just a PaymentService. Assume it returns success or failure."

**You**: "Can two users book the same seat simultaneously?"  
**Interviewer**: "No — that's the key problem. Use a lock mechanism to prevent it."

**You**: "Should cancellation give a full refund?"  
**Interviewer**: "Full refund within 2 hours of booking. After that, partial refund. The cancellation policy is pluggable."

**You**: "Do we need seat recommendations — best available seat?"  
**Interviewer**: "Nice to have. Cover it as a follow-up."

---

## Final Requirements

**In scope:**
1. Browse shows for a movie
2. Lock seats for a TTL window (10 minutes) before payment
3. Confirm booking — process payment, finalize booking
4. Cancel booking with refund per cancellation policy
5. Release expired seat locks (lazy or scheduled)
6. BookingStatus state machine: PENDING → CONFIRMED → CANCELLED
7. Prevent double-booking under concurrent access

**Out of scope:**
- User authentication
- Seat recommendation algorithm (follow-up)
- Physical ticket printing
- Partial seat selection across multiple shows

---

## Core Entities and Relationships

| Entity | Responsibility |
|--------|---------------|
| Movie | Title, description, duration |
| Hall | Physical venue with a seating layout |
| Show | A specific screening: movie + hall + datetime |
| Seat | Physical seat in a hall; row + column |
| SeatLock | Temporary hold on a seat for a user; has TTL |
| Booking | Confirmed purchase; links user to seats in a show |
| BookingStatus | Enum: PENDING, CONFIRMED, CANCELLED |
| PaymentService | Abstraction for charge/refund |
| CancellationPolicy | Computes refund amount based on time since booking |

Show owns the seat availability state. SeatLock is the key anti-double-booking mechanism — a seat is unavailable if it has an active non-expired lock or a confirmed booking.

---

## Class Design

### Movie

```
class Movie:
- movie_id: str
- title: str
- duration_minutes: int
- language: str
- genre: str
```

### Hall and Seat

```
class Hall:
- hall_id: str
- name: str
- seats: list[list[Seat]]   # 2D grid: rows x cols
- total_capacity: int

class Seat:
- seat_id: str
- hall_id: str
- row: int
- col: int
- seat_category: SeatCategory  # STANDARD, PREMIUM, VIP
```

### Show

```
class Show:
- show_id: str
- movie: Movie
- hall: Hall
- start_time: datetime
- end_time: datetime
- seat_prices: dict[SeatCategory, float]
- seat_locks: dict[str, SeatLock]       # seat_id -> active lock
- confirmed_seats: set[str]             # seat_ids confirmed in bookings
- _lock: threading.Lock                 # for atomic seat operations
```

### SeatLock

```
class SeatLock:
- lock_id: str
- seat_id: str
- show_id: str
- user_id: str
- locked_at: datetime
- expires_at: datetime
- ttl_minutes: int = 10

+ is_expired() -> bool
```

### Booking

```
class Booking:
- booking_id: str
- user_id: str
- show: Show
- seats: list[Seat]
- total_amount: float
- status: BookingStatus     # PENDING, CONFIRMED, CANCELLED
- created_at: datetime
- confirmed_at: datetime | None
- cancelled_at: datetime | None
- payment_id: str | None
```

### PaymentService (abstract)

```
class PaymentService:
+ charge(user_id: str, amount: float, booking_id: str) -> str  # returns payment_id
+ refund(payment_id: str, amount: float) -> bool
```

### CancellationPolicy (abstract)

```
class CancellationPolicy:
+ compute_refund(booking: Booking, cancelled_at: datetime) -> float

class TimedRefundPolicy(CancellationPolicy):
- full_refund_window_hours: int = 2
- partial_refund_percent: float = 0.5
```

---

## Implementation

### Core Method: lock_seats

**Core logic:**
1. Acquire show-level lock (atomic across concurrent requests)
2. For each seat_id, verify: not in `confirmed_seats` and not in `seat_locks` with active (non-expired) lock
3. If any seat fails validation, release the show lock and raise ConflictError
4. Create SeatLock for each seat, add to `seat_locks`
5. Return list of lock objects (caller uses these to confirm or release)

**Edge cases:**
- Seat already confirmed — cannot lock
- Seat locked by another user with non-expired TTL — cannot lock
- Seat locked by same user again — treat as conflict (force cancel first)
- Partial lock failure — must be all-or-nothing (rollback created locks)

```python
def lock_seats(self, show: Show, seat_ids: list[str],
               user_id: str) -> list[SeatLock]:
    with show._lock:
        # First pass: validate all seats
        for seat_id in seat_ids:
            if seat_id in show.confirmed_seats:
                raise SeatUnavailableError(f"Seat {seat_id} is already booked")
            existing_lock = show.seat_locks.get(seat_id)
            if existing_lock and not existing_lock.is_expired():
                raise SeatUnavailableError(f"Seat {seat_id} is currently held by another user")

        # Second pass: create locks (atomic since we hold show._lock)
        now = datetime.now()
        locks = []
        for seat_id in seat_ids:
            lock = SeatLock(
                lock_id=str(uuid.uuid4()),
                seat_id=seat_id,
                show_id=show.show_id,
                user_id=user_id,
                locked_at=now,
                expires_at=now + timedelta(minutes=10)
            )
            show.seat_locks[seat_id] = lock
            locks.append(lock)

        return locks
```

### Core Method: confirm_booking

**Core logic:**
1. Verify booking is in PENDING state
2. Verify all seat locks are still valid (not expired)
3. Call PaymentService.charge — if it fails, cancel the booking
4. Move seats from `seat_locks` to `confirmed_seats`
5. Set booking status to CONFIRMED

**Edge cases:**
- Lock expired between lock_seats and confirm — payment hasn't happened yet, release and fail cleanly
- Payment failure — release locks, set booking to CANCELLED

```python
def confirm_booking(self, booking: Booking,
                    payment_service: PaymentService) -> Booking:
    if booking.status != BookingStatus.PENDING:
        raise InvalidStateError(f"Cannot confirm a {booking.status} booking")

    show = booking.show
    seat_ids = [seat.seat_id for seat in booking.seats]

    with show._lock:
        # Verify locks still valid
        for seat_id in seat_ids:
            lock = show.seat_locks.get(seat_id)
            if lock is None or lock.is_expired() or lock.user_id != booking.user_id:
                booking.status = BookingStatus.CANCELLED
                raise LockExpiredError("Seat lock expired. Please retry booking.")

        # Process payment
        try:
            payment_id = payment_service.charge(
                booking.user_id, booking.total_amount, booking.booking_id
            )
        except PaymentError:
            # Release locks on payment failure
            for seat_id in seat_ids:
                show.seat_locks.pop(seat_id, None)
            booking.status = BookingStatus.CANCELLED
            raise

        # Confirm seats
        for seat_id in seat_ids:
            show.seat_locks.pop(seat_id)
            show.confirmed_seats.add(seat_id)

        booking.status = BookingStatus.CONFIRMED
        booking.payment_id = payment_id
        booking.confirmed_at = datetime.now()
        return booking
```

### Core Method: cancel_booking

**Core logic:**
1. Verify booking is CONFIRMED
2. Compute refund via CancellationPolicy
3. Call PaymentService.refund
4. Remove seats from confirmed_seats
5. Set booking to CANCELLED

```python
def cancel_booking(self, booking: Booking, payment_service: PaymentService,
                   cancellation_policy: CancellationPolicy) -> float:
    if booking.status != BookingStatus.CONFIRMED:
        raise InvalidStateError("Can only cancel a confirmed booking")

    cancelled_at = datetime.now()
    refund_amount = cancellation_policy.compute_refund(booking, cancelled_at)

    if refund_amount > 0:
        payment_service.refund(booking.payment_id, refund_amount)

    show = booking.show
    with show._lock:
        for seat in booking.seats:
            show.confirmed_seats.discard(seat.seat_id)

    booking.status = BookingStatus.CANCELLED
    booking.cancelled_at = cancelled_at
    return refund_amount
```

### SeatLock.is_expired + release_expired_locks

```python
def is_expired(self) -> bool:
    return datetime.now() > self.expires_at

def release_expired_locks(self, show: Show) -> int:
    """Returns count of locks released. Call lazily or on a scheduler."""
    with show._lock:
        expired = [
            seat_id for seat_id, lock in show.seat_locks.items()
            if lock.is_expired()
        ]
        for seat_id in expired:
            del show.seat_locks[seat_id]
        return len(expired)
```

---

## Verification

**Scenario**: User A locks seats [S1, S2] for Show X, pays, confirms.

1. `lock_seats(show_x, ["S1","S2"], user_a)` — show._lock acquired
2. S1: not in confirmed_seats ✓, no active lock ✓
3. S2: not in confirmed_seats ✓, no active lock ✓
4. SeatLock created for S1 (expires 10:15), SeatLock created for S2 (expires 10:15)
5. `seat_locks = {"S1": lock_a1, "S2": lock_a2}` — lock released

**Concurrent attempt by User B for S1 at same time**:
1. `lock_seats(show_x, ["S1"], user_b)` — waits on show._lock
2. Acquires lock after User A completes
3. `seat_locks["S1"]` exists, `lock_a1.is_expired()` = False → **SeatUnavailableError** raised

**User A confirms at 10:12 (within TTL)**:
1. `confirm_booking(booking_a, payment_service)`
2. Lock valid, payment succeeds, payment_id = "PAY_001"
3. S1, S2 moved to `confirmed_seats`; locks removed
4. booking_a.status = CONFIRMED

**User B tries again at 10:12**:
1. S1 in `confirmed_seats` → **SeatUnavailableError** (seat already booked)

---

## Deep Dive & Extensibility

### 1. "How does the seat lock TTL work — lazy vs scheduler?"

**Lazy expiry**: On each call to `lock_seats`, scan `seat_locks` and remove expired entries before checking availability. No background thread needed. The downside: stale locks stay in memory until the next `lock_seats` call for that show.

```python
def _evict_expired_locks(self, show: Show):
    # Called at start of lock_seats, under show._lock
    expired = [sid for sid, lock in show.seat_locks.items() if lock.is_expired()]
    for sid in expired:
        del show.seat_locks[sid]
```

**Scheduler-based expiry**: A background thread runs every 30 seconds, scans all shows for expired locks, and releases them. Keeps memory clean and shows accurate availability. Requires thread-safe iteration.

**Recommendation**: Use lazy expiry for simplicity in interviews. Mention scheduler as the production choice — it ensures that if 1000 seats are locked at 9:00 AM and no one comes back, they're freed by 9:11 AM regardless of whether the show page is refreshed.

### 2. "How do you prevent double-booking race conditions?"

The critical section is: check availability + create lock. These must be atomic.

The show-level `_lock` (threading.Lock) makes this atomic in a single-node system. The sequence under the lock:
1. Check all seats — if any fail, raise immediately (no partial state)
2. Create all locks — only if all seats pass validation

This prevents the TOCTOU (time-of-check-time-of-use) race: without the lock, two threads could both check S1 as available and both create locks.

For a distributed system (multiple app servers): the lock must be external. Use Redis with `SET NX PX` (set if not exists, with TTL):

```python
def lock_seat_redis(self, redis, show_id, seat_id, user_id, ttl_ms):
    key = f"seat_lock:{show_id}:{seat_id}"
    acquired = redis.set(key, user_id, nx=True, px=ttl_ms)
    return acquired  # True if locked, False if already taken
```

Redis SET NX is atomic. Lock all requested seats using a pipeline. If any fail, release already-acquired locks via DEL.

### 3. "How do you handle cancellation and refund?"

```python
class TimedRefundPolicy(CancellationPolicy):
    def compute_refund(self, booking: Booking, cancelled_at: datetime) -> float:
        hours_since_booking = (cancelled_at - booking.confirmed_at).total_seconds() / 3600
        if hours_since_booking <= 2:
            return booking.total_amount  # full refund
        elif hours_since_booking <= 24:
            return booking.total_amount * 0.5  # 50% refund
        else:
            return 0.0  # no refund (show may have already passed)
```

On cancellation:
1. Compute refund amount
2. Call `PaymentService.refund(payment_id, refund_amount)` — fire idempotent refund request
3. Release seats from `confirmed_seats` — these seats become available for new bookings
4. Set booking.status = CANCELLED

What if the show time has already passed? Add a guard: if `show.start_time < datetime.now()`, cancellation is disallowed (show already happened).

### 4. "How would you implement seat recommendation (best available)?"

Define "best" as premium seats first, then best row/column position (center of hall):

```python
def find_best_seats(self, show: Show, count: int,
                    preferred_category: SeatCategory) -> list[Seat]:
    available = [
        seat
        for row in show.hall.seats
        for seat in row
        if seat.seat_id not in show.confirmed_seats
        and not self._has_active_lock(show, seat.seat_id)
    ]

    # Score: prefer requested category, then proximity to center
    center_row = show.hall.total_rows // 2
    center_col = show.hall.total_cols // 2

    def score(seat):
        category_match = 0 if seat.seat_category == preferred_category else 1
        distance = abs(seat.row - center_row) + abs(seat.col - center_col)
        return (category_match, distance)

    available.sort(key=score)

    # Find count consecutive seats in the same row (better UX)
    for i in range(len(available) - count + 1):
        group = available[i:i+count]
        if all(s.row == group[0].row for s in group):
            return group

    return available[:count]  # fallback: any available seats
```

### 5. "How do you handle sold-out shows?"

A show is sold-out when `len(confirmed_seats) + len(active_locks) == hall.total_capacity`.

```python
def is_sold_out(self, show: Show) -> bool:
    with show._lock:
        active_lock_count = sum(
            1 for lock in show.seat_locks.values()
            if not lock.is_expired()
        )
        return len(show.confirmed_seats) + active_lock_count >= show.hall.total_capacity
```

For the browse experience, compute `available_count` as `total - confirmed - active_locks`. Show "Filling Fast" below a threshold (e.g., < 10% remaining). Show "Sold Out" at 0 available.

---

## Interviewer Questions by Level

**Junior**: Explain the booking lifecycle (PENDING → CONFIRMED → CANCELLED). Why do we need a temporary lock before payment? What fields does a Booking need?

**Mid-level**: Implement `lock_seats` with the atomic check-and-lock pattern. Explain why the two-pass approach (validate all, then create all) is necessary. Implement `is_expired()` on SeatLock.

**Senior**: Design distributed seat locking with Redis SET NX. Implement lazy TTL expiry vs. scheduler trade-offs. Handle the payment failure case — what state should the booking be in and what happens to the seat locks?

---

## Common Interview Questions

- Q: Why use a temporary lock before payment rather than booking directly? A: Payment takes 2-5 seconds. Without a lock, another user could select the same seat during that window and both transactions might succeed, causing double-booking. The lock reserves the seat atomically before any money changes hands.
- Q: What if payment fails after seats are locked? A: Release all seat locks for that booking, set booking status to CANCELLED, no refund needed (payment never succeeded). The seats become available immediately.
- Q: How do you prevent double-booking atomically? A: Use a per-show mutex (or Redis SET NX for distributed). The critical section is: check availability + create lock. Both must happen under the same lock so no two threads can both see a seat as available.
- Q: What is the TTL cleanup strategy — lazy or scheduled? A: Lazy (evict on each `lock_seats` call) is simple and correct. Scheduled (background thread every 30s) is more accurate for real-time availability counts. Production systems use both — lazy for correctness, scheduler for freshness.
- Q: How does the seat lock TTL interact with payment processing time? A: The TTL must be longer than the worst-case payment processing time. 10 minutes is conservative. If payment takes longer (rare), the lock expires, the user must restart. A heartbeat mechanism can extend the lock TTL during active payment.
- Q: What is the difference between PENDING and CONFIRMED status? A: PENDING means seats are locked but payment hasn't completed. CONFIRMED means payment succeeded and seats are permanently reserved. Only CONFIRMED bookings are reflected in `confirmed_seats`.
