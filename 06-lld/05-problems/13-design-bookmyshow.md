---
module: 06-lld
topic: Problems
status: unread
tags: [06-lld, system-design, problems]
---
# Design BookMyShow (Movie Ticket Booking)

> **Difficulty**: Medium-Hard
> **Topics**: Concurrency, Factory, Strategy, State
> **Key Concepts**: Seat locking under concurrency, booking lifecycle, payment flow.

---

## What Breaks Without This Design?

```python
class BookingSystem:
    def __init__(self):
        self._hall_seats: dict[str, list[bool]] = {}  # hall_id → seat array
        self._seat_owner: dict[str, str] = {}          # "hall_id:seat" → user_id

    def book_seat(self, hall_id: str, seat_num: int, user_id: str) -> bool:
        if not self._hall_seats[hall_id][seat_num]:
            self._hall_seats[hall_id][seat_num] = True
            self._seat_owner[f"{hall_id}:{seat_num}"] = user_id
            return True
        return False
```

**Concrete failures**:
1. **Race condition**: Two users call `bookSeat` for the same seat simultaneously. Both read `false`, both write `true` — double booking.
2. **No temporary hold**: A user sees a seat as free, spends 4 minutes filling payment details, but another user books it first. No seat reservation during checkout.
3. **No booking state**: Once booked, there is no `CONFIRMED` / `CANCELLED` / `EXPIRED` lifecycle — cancellation is undefined.
4. **No payment integration point**: Booking and payment are conflated; if payment fails, the seat stays marked as booked forever.

---

## Derive the Class Structure

**Force 1 — Concurrent seat selection requires a temporary lock**: Extract `SeatLock` that holds a seat for N minutes (TTL). A scheduler or lazy check on the next request expires stale locks.

**Force 2 — Booking has a lifecycle**: Extract `Booking` with a `BookingStatus` enum (`INITIATED`, `CONFIRMED`, `CANCELLED`, `EXPIRED`). Payment completion transitions status.

**Force 3 — Multiple payment methods**: Extract `PaymentStrategy` interface. `CreditCardPayment`, `UPIPayment`, `WalletPayment` implement it.

**Force 4 — Movies, shows, and halls are separate concerns**: Extract `Movie`, `Show` (a Movie in a Hall at a time), `Hall` (with its seat layout), `Seat`.

```
God class → Show (movie + hall + time)
          → Hall (seat grid)
          → Seat (row, col, type, availability)
          → SeatLock (temporary hold with TTL)
          → Booking (lifecycle + seat references)
          → PaymentStrategy (interchangeable payment)
          → BookingService (orchestration)
```

---

## Phase 1: Requirements

**Actors**: Customer, Admin (adds movies/shows), Payment Gateway.

**Must-have**:
- Browse movies and shows by city
- View available seats in a hall
- Temporarily lock seats during checkout (5-minute hold)
- Confirm booking after successful payment
- Cancel booking and release seats

**Constraints**:
- Two users cannot book the same seat for the same show
- Seat lock must auto-expire if payment isn't completed

---

## Phase 2: Use Cases

### UC1: Book Seats
1. Customer selects show and seats.
2. System creates `SeatLock` records for each seat (TTL = 5 min).
3. Customer completes payment via `PaymentStrategy`.
4. On success → `Booking` status = `CONFIRMED`, locks removed, seats marked BOOKED.
5. On failure/timeout → locks expire, seats return to AVAILABLE.

### UC2: Cancel Booking
1. Customer requests cancellation.
2. System checks cancellation policy (before 2h = full refund).
3. Seats released → AVAILABLE.
4. `Booking` status = `CANCELLED`.

---

## Phase 3: Class Diagram

```
Movie
  - movieId: String
  - title: String
  - durationMinutes: int
  - genre: String

Show
  - showId: String
  - movie: Movie
  - hall: Hall
  - startTime: LocalDateTime
  + getAvailableSeats(): List<Seat>

Hall
  - hallId: String
  - name: String
  - seats: List<Seat>

Seat
  - seatId: String
  - row: int
  - col: int
  - type: SeatType  // REGULAR, PREMIUM, RECLINER
  - status: SeatStatus  // AVAILABLE, LOCKED, BOOKED

SeatLock
  - lockId: String
  - seat: Seat
  - show: Show
  - userId: String
  - createdAt: Instant
  - expiresAt: Instant
  + isExpired(): boolean

Booking
  - bookingId: String
  - show: Show
  - seats: List<Seat>
  - user: User
  - status: BookingStatus
  - totalAmount: double
  + confirm()
  + cancel()

PaymentStrategy <<interface>>
  + pay(amount: double): PaymentResult

BookingService
  + lockSeats(showId, seatIds, userId): List<SeatLock>
  + confirmBooking(lockIds, PaymentStrategy): Booking
  + cancelBooking(bookingId): void
```

---

## Phase 4: Design Patterns

| Pattern | Where | Why |
|---------|-------|-----|
| **Strategy** | `PaymentStrategy` | Swap UPI / card / wallet without changing booking flow |
| **State** | `BookingStatus` | `INITIATED → CONFIRMED / CANCELLED / EXPIRED` transitions are well-defined |
| **Factory** | `SeatFactory` | Create `REGULAR`, `PREMIUM`, `RECLINER` seats from config |
| **Singleton** | `BookingService` | Single orchestrator for all seat lock operations |

---

## Phase 5: Key Implementation

### Concurrent Seat Locking (Critical Section)

```python
import threading
import datetime

class BookingService:
    def __init__(self):
        self._active_locks: dict[str, SeatLock] = {}
        self._lock_mutex = threading.Lock()

    def lock_seats(self, show_id: str, seat_ids: list[str], user_id: str) -> list[SeatLock]:
        with self._lock_mutex:
            # 1. Verify no active lock exists for any of the requested seats
            for seat_id in seat_ids:
                key = f"{show_id}:{seat_id}"
                existing = self._active_locks.get(key)
                if existing is not None and not existing.is_expired():
                    raise SeatAlreadyLockedException(seat_id)
            # 2. Create locks atomically
            locks = []
            for seat_id in seat_ids:
                lock = SeatLock(show_id, seat_id, user_id, datetime.timedelta(minutes=5))
                self._active_locks[f"{show_id}:{seat_id}"] = lock
                locks.append(lock)
            return locks
```

### Booking Confirmation

```python
def confirm_booking(self, lock_ids: list[str], payment: PaymentStrategy) -> Booking:
    locks = self._resolve_locks(lock_ids)  # raises if any expired
    amount = self._calculate_amount(locks)
    result = payment.pay(amount)
    if not result.is_success():
        raise PaymentFailedException()
    booking = Booking(locks, amount)
    booking.confirm()              # status → CONFIRMED
    self._release_locks(lock_ids)  # remove from active_locks
    self._mark_seats_booked(locks) # seat.status → BOOKED
    return booking
```

---

## Interview Tips

- **Seat locking TTL**: Interviewers expect you to discuss how expired locks get cleaned up — lazy expiry on next access (done above) vs. a background scheduler thread.
- **Concurrency**: The `synchronized (lockMutex)` block is the key — explain why you can't use per-seat locks (deadlock if two users request seats A+B and B+A simultaneously).
- **Scalability follow-up**: Distributed lock via Redis `SET NX PX` if asked about multi-instance deployment.
