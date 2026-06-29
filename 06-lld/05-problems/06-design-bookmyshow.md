---
module: 06-lld
topic: Problems
status: unread
tags: [06-lld, system-design, problems]
---
# Design BookMyShow (Movie Ticket Booking)

> **Difficulty**: Medium-Hard
> **Topics**: Concurrency, Strategy, State, Factory
> **Key Concepts**: Seat locking under concurrency, booking lifecycle, payment flow, lock expiry.

---

## What Breaks Without This Design?

```python
class BookingSystem:
    def __init__(self):
        self._hall_seats = {}  # hall_id → seat array (list[bool])
        self._seat_owner = {}  # "hall_id:seat" → user_id

    def book_seat(self, hall_id, seat_num, user_id):
        if not self._hall_seats[hall_id][seat_num]:
            self._hall_seats[hall_id][seat_num] = True
            self._seat_owner[f"{hall_id}:{seat_num}"] = user_id
            return True
        return False
```

**Concrete failures**:
1. **Race condition**: Two users call `book_seat` for the same seat simultaneously. Both read `False`, both write `True` — double booking.
2. **No temporary hold**: User sees seat as free, spends 4 minutes on payment, another user books it first. No reservation during checkout.
3. **No booking lifecycle**: Once booked, there is no `CONFIRMED` / `CANCELLED` / `EXPIRED` — cancellation is undefined.
4. **No payment integration point**: If payment fails, seat stays marked as booked forever.
5. **No seat types**: Premium, recliner, regular seats all treated identically.

---

## Derive the Class Structure

**Force 1 — Concurrent seat selection requires a temporary hold**: Extract `SeatLock` with TTL. Check-then-lock must be atomic (single mutex for entire operation, not per-seat — per-seat locks cause deadlock when two users request A+B and B+A simultaneously).

**Force 2 — Booking has a lifecycle**: Extract `Booking` with `BookingStatus` enum. Payment completion drives state transitions.

**Force 3 — Multiple payment methods**: Extract `PaymentStrategy` interface. Booking flow calls `strategy.pay()` without caring which method.

**Force 4 — Movies, shows, halls are separate concerns**: A `Show` is one screening of a `Movie` in a `Hall` at a specific time. `Hall` owns its `Seat` grid.

**Force 5 — Seat lock cleanup**: Expired locks must be reclaimed. Two options: lazy expiry (check on next request), background scheduler thread. Both have trade-offs.

```
God class → Movie
          → Show (movie + hall + start_time)
          → Hall (seat grid, capacity)
          → Seat (row, col, type, status)
          → SeatLock (temporary hold + TTL)
          → Booking (seat refs + lifecycle)
          → PaymentStrategy (interchangeable)
          → BookingService (orchestration + mutex)
```

---

## Phase 1: Requirements

**Actors**: Customer, Admin (adds movies/shows/halls), Payment Gateway.

**Must-have**:
- Browse movies by city; view shows for a movie
- View available seats in a hall for a show
- Temporarily lock seats during checkout (5-minute TTL)
- Confirm booking after successful payment
- Cancel booking and release seats
- Auto-expire locks on timeout → seats return to AVAILABLE

**Constraints**:
- Two users cannot book the same seat for the same show
- Seat lock must auto-expire if payment isn't completed within TTL
- Cancellation policy: full refund if cancelled > 2 hours before show

---

## Phase 2: Use Cases

### UC1: Book Seats
1. Customer selects show and seats.
2. `BookingService.lock_seats()` — atomically checks no active lock exists, creates `SeatLock` (TTL = 5 min) for each seat.
3. Customer completes payment via `PaymentStrategy`.
4. **Success** → `Booking.status = CONFIRMED`, locks removed, seats → BOOKED.
5. **Payment failure** → `Booking.status = FAILED`, locks released, seats → AVAILABLE.
6. **Timeout** → locks expire (lazy or scheduled), seats → AVAILABLE.

### UC2: Cancel Booking
1. Customer requests cancellation with `booking_id`.
2. System fetches booking, validates status is `CONFIRMED`.
3. Checks cancellation policy (show start time vs now).
4. Releases seats → AVAILABLE, refunds payment.
5. `Booking.status = CANCELLED`.

### UC3: Admin Adds Show
1. Admin creates `Movie`, `Hall` (with seat layout), `Show` (links both with time).
2. `SeatFactory` generates seat objects from hall layout config.

---

## Phase 3: Class Diagram

```
Movie
  - movie_id: str
  - title: str
  - duration_minutes: int
  - genre: str
  - language: str

Show
  - show_id: str
  - movie: Movie
  - hall: Hall
  - start_time: datetime
  - end_time: datetime
  + get_available_seats() → list[Seat]
  + get_seats_by_type(seat_type: SeatType) → list[Seat]

Hall
  - hall_id: str
  - name: str
  - total_capacity: int
  - seats: list[Seat]

Seat
  - seat_id: str
  - row: int
  - col: int
  - type: SeatType        # REGULAR, PREMIUM, RECLINER
  - status: SeatStatus    # AVAILABLE, LOCKED, BOOKED
  - price: float

SeatLock
  - lock_id: str
  - seat_id: str
  - show_id: str
  - user_id: str
  - created_at: datetime
  - expires_at: datetime
  + is_expired() → bool

Booking
  - booking_id: str
  - show: Show
  - seats: list[Seat]
  - user: User
  - status: BookingStatus  # INITIATED, CONFIRMED, CANCELLED, FAILED, EXPIRED
  - total_amount: float
  - created_at: datetime
  + confirm()
  + cancel()
  + expire()

PaymentStrategy  <<interface>>
  + pay(amount: float, booking_id: str) → PaymentResult

CreditCardPayment(PaymentStrategy)
UPIPayment(PaymentStrategy)
WalletPayment(PaymentStrategy)

BookingService  <<singleton>>
  - _active_locks: dict[str, SeatLock]   # key = "show_id:seat_id"
  - _bookings: dict[str, Booking]
  - _lock_mutex: threading.Lock
  + lock_seats(show_id, seat_ids, user_id) → list[SeatLock]
  + confirm_booking(lock_ids, payment_strategy) → Booking
  + cancel_booking(booking_id) → void
  + _cleanup_expired_locks() → void

SeatFactory  <<static>>
  + create_seats(layout: HallLayout) → list[Seat]
```

---

## Phase 4: Design Patterns

| Pattern | Where | Why |
|---------|-------|-----|
| **Strategy** | `PaymentStrategy` | Swap UPI / card / wallet without touching booking flow |
| **State** | `BookingStatus` | `INITIATED → CONFIRMED / CANCELLED / FAILED / EXPIRED` — transitions are well-defined and guarded |
| **Factory** | `SeatFactory.create_seats()` | Create typed seat objects from hall config without BookingService knowing seat subtypes |
| **Singleton** | `BookingService` | Single instance owns the lock mutex — multiple instances would break atomicity |
| **Template Method** | `PaymentStrategy` base | Common pre/post hooks (logging, idempotency check) with strategy-specific `_do_pay()` |

---

## Phase 5: Key Implementation

### Enums and Data Classes

```python
from enum import Enum
import datetime
import uuid

class SeatType(Enum):
    REGULAR = "REGULAR"
    PREMIUM = "PREMIUM"
    RECLINER = "RECLINER"

class SeatStatus(Enum):
    AVAILABLE = "AVAILABLE"
    LOCKED = "LOCKED"
    BOOKED = "BOOKED"

class BookingStatus(Enum):
    INITIATED = "INITIATED"
    CONFIRMED = "CONFIRMED"
    CANCELLED = "CANCELLED"
    FAILED = "FAILED"
    EXPIRED = "EXPIRED"

SEAT_PRICES = {
    SeatType.REGULAR: 150.0,
    SeatType.PREMIUM: 250.0,
    SeatType.RECLINER: 400.0,
}
```

### Core Domain Classes

```python
class Seat:
    def __init__(self, seat_id, row, col, seat_type):
        self.seat_id = seat_id
        self.row = row
        self.col = col
        self.type = seat_type
        self.status = SeatStatus.AVAILABLE

    def price(self):
        return SEAT_PRICES[self.type]

class SeatLock:
    def __init__(self, lock_id, seat_id, show_id, user_id, created_at, expires_at):
        self.lock_id = lock_id
        self.seat_id = seat_id
        self.show_id = show_id
        self.user_id = user_id
        self.created_at = created_at
        self.expires_at = expires_at

    def is_expired(self):
        return datetime.datetime.utcnow() >= self.expires_at

class Booking:
    def __init__(self, booking_id, show_id, seats, user_id, total_amount):
        self.booking_id = booking_id
        self.show_id = show_id
        self.seats = seats  # list of Seat
        self.user_id = user_id
        self.total_amount = total_amount
        self.status = BookingStatus.INITIATED
        self.created_at = datetime.datetime.utcnow()

    def confirm(self):
        if self.status != BookingStatus.INITIATED:
            raise ValueError(f"Cannot confirm booking in state {self.status}")
        self.status = BookingStatus.CONFIRMED

    def cancel(self):
        if self.status not in (BookingStatus.CONFIRMED, BookingStatus.INITIATED):
            raise ValueError(f"Cannot cancel booking in state {self.status}")
        self.status = BookingStatus.CANCELLED

    def expire(self):
        if self.status == BookingStatus.INITIATED:
            self.status = BookingStatus.EXPIRED
```

### Payment Strategy

```python
from abc import ABC, abstractmethod

class PaymentResult:
    def __init__(self, success, transaction_id="", error=""):
        self.success = success
        self.transaction_id = transaction_id
        self.error = error

class PaymentStrategy(ABC):
    @abstractmethod
    def pay(self, amount, booking_id):
        pass

class CreditCardPayment(PaymentStrategy):
    def __init__(self, card_token):
        self._card_token = card_token

    def pay(self, amount, booking_id):
        # Call payment gateway API with card_token
        # Returns success/failure with transaction_id
        transaction_id = f"CC-{uuid.uuid4().hex[:8]}"
        return PaymentResult(success=True, transaction_id=transaction_id)

class UPIPayment(PaymentStrategy):
    def __init__(self, upi_id):
        self._upi_id = upi_id

    def pay(self, amount, booking_id):
        transaction_id = f"UPI-{uuid.uuid4().hex[:8]}"
        return PaymentResult(success=True, transaction_id=transaction_id)
```

### BookingService — The Critical Section

```python
class SeatAlreadyLockedException(Exception):
    pass

class LockExpiredException(Exception):
    pass

class PaymentFailedException(Exception):
    pass

class BookingService:
    _instance = None

    def __new__(cls):
        if cls._instance is None:
            cls._instance = super().__new__(cls)
            cls._instance._active_locks = {}   # "show_id:seat_id" → SeatLock
            cls._instance._bookings = {}        # booking_id → Booking
        return cls._instance

    def lock_seats(self, show_id, seat_ids, seats, user_id, ttl_minutes=5):
        # Phase 1: validate — all-or-nothing check before creating any lock
        for seat_id in seat_ids:
            key = f"{show_id}:{seat_id}"
            existing = self._active_locks.get(key)
            if existing is not None and not existing.is_expired():
                raise SeatAlreadyLockedException(
                    f"Seat {seat_id} already locked by another user"
                )

        # Phase 2: create locks
        now = datetime.datetime.utcnow()
        expiry = now + datetime.timedelta(minutes=ttl_minutes)
        locks = []
        for seat, seat_id in zip(seats, seat_ids):
            lock = SeatLock(
                lock_id=uuid.uuid4().hex,
                seat_id=seat_id,
                show_id=show_id,
                user_id=user_id,
                created_at=now,
                expires_at=expiry,
            )
            self._active_locks[f"{show_id}:{seat_id}"] = lock
            seat.status = SeatStatus.LOCKED
            locks.append(lock)
        return locks

    def confirm_booking(self, show_id, seats, lock_ids, user_id, payment):
        # Validate locks still valid
        for seat in seats:
            key = f"{show_id}:{seat.seat_id}"
            lock = self._active_locks.get(key)
            if lock is None or lock.is_expired():
                raise LockExpiredException(
                    f"Lock for seat {seat.seat_id} has expired"
                )

        total = sum(seat.price() for seat in seats)
        booking = Booking(
            booking_id=uuid.uuid4().hex,
            show_id=show_id,
            seats=seats,
            user_id=user_id,
            total_amount=total,
        )
        self._bookings[booking.booking_id] = booking

        result = payment.pay(total, booking.booking_id)
        if not result.success:
            booking.status = BookingStatus.FAILED
            self._release_locks(show_id, seats)
            self._restore_seats(seats, SeatStatus.AVAILABLE)
            raise PaymentFailedException(result.error)

        booking.confirm()
        self._release_locks(show_id, seats)
        self._restore_seats(seats, SeatStatus.BOOKED)

        return booking

    def cancel_booking(self, booking_id):
        booking = self._bookings.get(booking_id)
        if booking is None:
            raise ValueError(f"Booking {booking_id} not found")
        booking.cancel()
        self._restore_seats(booking.seats, SeatStatus.AVAILABLE)

    def _release_locks(self, show_id, seats):
        for seat in seats:
            self._active_locks.pop(f"{show_id}:{seat.seat_id}", None)

    def _restore_seats(self, seats, status):
        for seat in seats:
            seat.status = status

    def cleanup_expired_locks(self):
        """Background scheduler calls this periodically."""
        expired_keys = [
            k for k, lock in self._active_locks.items() if lock.is_expired()
        ]
        for key in expired_keys:
            del self._active_locks[key]
        return len(expired_keys)
```

### Seat Factory

```python
class SeatFactory:
    @staticmethod
    def create_seats(rows, cols_per_row):
        """
        cols_per_row: {row_index: (num_cols, SeatType)}
        e.g. {0: (8, SeatType.RECLINER), 1: (10, SeatType.PREMIUM), 2: (12, SeatType.REGULAR)}
        """
        seats = []
        for row, (num_cols, seat_type) in cols_per_row.items():
            for col in range(num_cols):
                seat_id = f"R{row}C{col}"
                seats.append(Seat(seat_id, row, col, seat_type))
        return seats
```

---

## Phase 6: Expiry — Two Strategies

| Strategy | How | Trade-off |
|----------|-----|-----------|
| **Lazy expiry** | `lock_seats()` checks `is_expired()` before raising — stale locks evicted on next conflict | Expired locks linger in memory; seat shows as LOCKED even when nobody holds it |
| **Background scheduler** | `cleanup_expired_locks()` runs every 60s on a daemon thread | Seats return to AVAILABLE promptly; extra thread complexity |

**Interview answer**: Start with lazy expiry (simpler, no extra thread). Add scheduler if product requirement is "seat must show available within N seconds of expiry."

```python
import threading

def start_lock_cleanup_scheduler(service, interval_seconds=60):
    def _run():
        while True:
            service.cleanup_expired_locks()
            threading.Event().wait(interval_seconds)
    t = threading.Thread(target=_run, daemon=True)
    t.start()
```

---

## Interview Tips

- **Why one global mutex, not per-seat locks?** Two users requesting seats [A, B] and [B, A] with per-seat locks → deadlock. Global mutex eliminates this entirely. At scale, switch to Redis `SET NX PX` per seat (each lock is atomic, no multi-lock deadlock risk because you lock one at a time).
- **Distributed deployment**: Replace in-memory `_active_locks` with Redis. `SET show_id:seat_id user_id NX PX 300000` — atomic check-and-set, TTL built in.
- **Idempotency on confirm**: If `confirm_booking` is called twice (network retry), check `booking.status == CONFIRMED` and return existing booking — don't double-charge.
- **Scalability follow-up**: Partition shows by city → each city's BookingService shard handles only local shows. No cross-shard seat contention.
- **SOLID check**: SRP (each class one job), OCP (add `WalletPayment` without changing `BookingService`), DIP (`BookingService` depends on `PaymentStrategy` interface, not concrete class).

---

## Interviewer Follow-Up Questions

- "What are the core entities?" → `Movie`, `Theatre` (has many `Screen`s), `Screen` (has many `Seat`s, runs many `Show`s), `Show` (a movie at a screen at a time), `Seat`, `Booking` (links User → Show → Seat(s)), `Payment`. The key constraint: one `Seat` can have at most one active `Booking` per `Show`.
- "Two users try to book the same seat simultaneously. How do you prevent double-booking?" → Optimistic locking at the DB level: `UPDATE seats SET status='BOOKED', booking_id=X WHERE seat_id=Y AND show_id=Z AND status='AVAILABLE'`. Rows affected = 1 → success; rows affected = 0 → already booked, inform user. The DB row lock serializes the final write — no distributed lock needed. The application layer doesn't need to manage the race condition.
- "How do you implement a 10-minute seat hold (like BookMyShow's timer)?" → Temporary reservation: `UPDATE seats SET status='HELD', held_by=user_id, held_until=NOW()+600 WHERE seat_id=Y AND status='AVAILABLE'`. A background job (cron every minute): `UPDATE seats SET status='AVAILABLE' WHERE status='HELD' AND held_until < NOW()`. On payment completion: `UPDATE seats SET status='BOOKED' WHERE seat_id=Y AND held_by=user_id`. The hold is just a status in the DB — no in-memory state or Redis lock required.
- "How do you display the seat map for a 1,000-seat theatre with real-time availability?" → Cache the seat availability map in Redis as a bitset or hash (`HSET show:{show_id}:seats {seat_id} {status}`). On hold/book: update Redis + queue a DB write (async). Seat map read: single Redis HGETALL → client renders. Stale-by-design: Redis may show a seat as available that was just held 100ms ago. The DB is authoritative; Redis is display-only. Conflict: the seat hold/book attempt always hits the DB — Redis is only for the display rendering.
