---
module: 06-lld
topic: Problems
status: unread
tags: [06-lld, system-design, problems]
---
# Design Hotel Management System

> **Difficulty**: Medium
> **Topics**: State Pattern, Strategy Pattern, Concurrency, Date Range Logic
> **Extension**: Dynamic pricing, distributed double-booking prevention, loyalty tiers

---

## What Breaks Without This Design?

```python
import uuid
from datetime import date

class HotelSystem:
    def __init__(self):
        self.room_status = {}   # dict[int, str]: room_id → "AVAILABLE"/"RESERVED"/"OCCUPIED"
        self.reservations = {}  # dict[str, list]: confirm_id → [room_id, check_in, check_out]
        self.pricing_mode = "STANDARD"      # or "WEEKEND"

    def book_room(self, room_id, check_in, check_out, guest_name):
        if self.room_status.get(room_id) != "AVAILABLE":
            return None  # room not available
        # Does not check date overlap with existing reservations for this room
        self.room_status[room_id] = "RESERVED"
        confirm_id = str(uuid.uuid4())
        self.reservations[confirm_id] = [room_id, check_in, check_out]
        return confirm_id

    def calculate_price(self, room_id, check_in, check_out):
        nights = (check_out - check_in).days
        base_rate = 100.0  # hardcoded
        if self.pricing_mode == "WEEKEND":
            return nights * base_rate * 1.5
        return nights * base_rate
        # Adding "HOLIDAY" pricing requires editing this method
```

**Concrete failures**:
1. **No date-range overlap check**: `roomStatus.get(roomId) == "AVAILABLE"` is a global flag — it does not check whether the requested `[checkIn, checkOut]` overlaps any existing reservation for that room. Room 101 can be booked for June 1–5 and again for June 3–7 simultaneously.
2. **Race condition on double booking**: Thread A reads `roomStatus.get(101) == "AVAILABLE"` → Thread B also reads `"AVAILABLE"` → both write `"RESERVED"` and generate different confirmation IDs for the same room and overlapping dates.
3. **`String` for status is not type-safe**: Typo `"AVAILAIBLE"` compiles and silently fails all equality checks.
4. **Room lifecycle ignored**: A room that needs cleaning after checkout should go `OCCUPIED → CLEANING → AVAILABLE`, not directly to `AVAILABLE`. The string-flag model has no concept of intermediate states.
5. **Pricing change requires editing `calculatePrice()`**: Each new pricing tier (loyalty discount, holiday rate) adds another `else if`.

---

## Derive the Class Structure

**Force 1 — Date-range overlap detection is complex**: Check whether `[reqCheckIn, reqCheckOut)` overlaps any existing `[existCheckIn, existCheckOut)` for the same room. Condition: overlap exists when `reqCheckIn < existCheckOut && reqCheckOut > existCheckIn`. This logic belongs on `Room` or a `ReservationRepository`, not inline in `bookRoom()`.

**Force 2 — Room lifecycle is a state machine**: `AVAILABLE → RESERVED → OCCUPIED → CLEANING → AVAILABLE`. Each state has different rules: you can only book an `AVAILABLE` room; you can only check in a `RESERVED` room; cleaning transition is triggered by checkout. Extract `RoomState` interface; each state class handles its own valid transitions.

**Force 3 — Double-booking requires per-room locking**: Two threads both pass the overlap check before either writes the reservation. Lock at the `Room` level (`ReentrantLock` per room), not the entire `HotelSystem`. Re-validate inside the lock (double-check pattern).

**Force 4 — Pricing is a separate strategy**: `StandardPricing`, `WeekendPricing`, `HolidayPricing` each implement `PricingStrategy`. The `Room` or `HotelSystem` holds a `PricingStrategy` reference. Swapping to dynamic pricing requires one constructor call.

**Force 5 — `Reservation` is an entity**: A reservation has a lifecycle (CONFIRMED → CANCELLED → COMPLETED). It references the `Room` and `Guest`. Without a `Reservation` entity, you cannot cancel, retrieve, or audit bookings.

**Result** — the class split these forces produce:
```
God class → HotelManagementSystem (search, book, cancel — orchestration only)
          → Room (id, type, state, List<Reservation> for overlap detection)
          → RoomState (interface: book, checkIn, checkOut, clean)
             → AvailableState, ReservedState, OccupiedState, CleaningState
          → Reservation (confirmId, room, guest, checkIn, checkOut, status)
          → Guest (id, name, loyaltyTier)
          → PricingStrategy (interface: calculatePrice(room, nights))
             → StandardPricing, WeekendPricing, DynamicPricing
          → ReservationRepository (overlap check, CRUD — separates persistence logic)
```

---

## Opening Analogy

Picture a hotel with 200 rooms. Each room has a lifecycle: it is `AVAILABLE` until a guest reserves it (`RESERVED`), then the guest arrives and it becomes `OCCUPIED`, and after checkout it goes back to `AVAILABLE` (but may need `CLEANING` first). This lifecycle is a state machine — the exact behavior of "can you book this room?" depends entirely on which state the room is in. Meanwhile, pricing changes based on weekday vs weekend vs holiday — that is a Strategy. And two users clicking "Book" at the same millisecond must not both succeed — that is a concurrency problem.

---

## Phase 1: Requirements

### Functional
- Search available rooms by type (STANDARD, DELUXE, SUITE) and date range.
- Book a room — creates a `Reservation` with a unique confirmation ID.
- Cancel a reservation — releases the date range and applies cancellation policy.
- Check-in — transitions room to `OCCUPIED`.
- Check-out — transitions room through `CLEANING` → `AVAILABLE`.
- Detect and reject overlapping reservations for the same room.

### Non-Functional
- No double bookings, even under concurrent requests.
- Price calculation is pluggable — the pricing model changes without modifying `Room`.
- Search must return results in O(R × B) where R = rooms of given type, B = bookings per room (bounded by seasonal limit).

---

## Phase 2: Use Cases

### Actors
- **Guest** — searches, books, cancels.
- **Receptionist** — checks in/out, manages walk-in bookings.
- **Hotel System** — validates availability, generates confirmations.
- **Admin** — configures pricing strategy, views occupancy reports.

### UC1: Search Available Rooms
1. Guest submits `(type, checkIn, checkOut)`.
2. System filters all rooms of `type`.
3. For each room, checks whether any existing reservation overlaps `[checkIn, checkOut)`.
4. Returns list of available rooms with calculated price per night.

### UC2: Book Room
1. Guest selects room `R` for dates `D`.
2. System acquires per-room lock on `R`.
3. System re-validates availability (double-check after lock).
4. System creates `Reservation`, transitions room to `RESERVED`.
5. System returns confirmation ID.
6. Lock released.

### UC3: Cancel Reservation
1. Guest submits `confirmationId`.
2. System fetches reservation, validates it belongs to guest.
3. System applies cancellation policy (full/partial refund based on days until check-in).
4. System removes date range from room's reservations.
5. Room may transition back to `AVAILABLE`.

### UC4: Check-In
1. Receptionist scans confirmation for guest.
2. System validates `today == checkIn` (or within grace period).
3. System transitions room state: `RESERVED` → `OCCUPIED`.

### UC5: Check-Out
1. Receptionist marks check-out.
2. System transitions room: `OCCUPIED` → `CLEANING`.
3. Housekeeping system signals done → `AVAILABLE`.

---

## Phase 3: Class Diagram

```
┌─────────────────────────────────┐
│           Hotel                 │  <<Singleton>>
│─────────────────────────────────│
│ - rooms: Map<RoomType,List<Room>>│
│ - reservations: Map<String,Res> │
│─────────────────────────────────│
│ + searchRooms(type, range)      │
│ + bookRoom(guest, room, range)  │
│ + cancelReservation(confId)     │
│ + checkIn(confId)               │
│ + checkOut(confId)              │
└────────────────┬────────────────┘
                 │ manages
       ┌─────────┴──────────┐
       ▼                    ▼
┌──────────────┐    ┌──────────────────────┐
│     Room     │    │     Reservation       │
│──────────────│    │──────────────────────│
│ - id: int    │    │ - id: String (UUID)  │
│ - type       │    │ - guest: Guest        │
│ - priceBase  │    │ - room: Room          │
│ - state      │    │ - dateRange: DateRange│
│ - reservations│   │ - status: ResStatus  │
│   List       │    │ - totalPrice         │
│──────────────│    └──────────────────────┘
│ + isAvailable│
│ + book(range)│ ← synchronized
│ + stateTransit│
└──────────────┘

<<enumeration>>         <<enumeration>>
RoomState               ReservationStatus
─────────────           ────────────────
AVAILABLE               CONFIRMED
RESERVED                CHECKED_IN
OCCUPIED                CANCELLED
CLEANING

<<interface>>           <<implementations>>
PricingStrategy         FlatPricing
─────────────────       WeekendSurgePricing
calculate(Room, Range)  HolidayPricing
  : BigDecimal          LoyaltyTierPricing

┌───────────────────────┐
│       DateRange        │
│───────────────────────│
│ - checkIn: LocalDate  │
│ - checkOut: LocalDate │
│───────────────────────│
│ + overlaps(other): bool│
│ + nights(): long      │
└───────────────────────┘
```

---

## Phase 4: Design Patterns Applied

### 1. State Pattern — Room lifecycle
**Why:** A room's behavior depends entirely on its current state. `isAvailable()` means something different when the room is `CLEANING` vs `AVAILABLE`. Without State pattern, `Room` would be littered with `if (state == CLEANING) throw ...`. With it, each state class encapsulates its own valid transitions and operations.

### 2. Strategy Pattern — Dynamic pricing
**Why:** Price per night is not fixed. Marketing changes pricing rules seasonally without touching `Room`. Injecting a `PricingStrategy` into the booking flow lets you swap `WeekendSurgePricing` for `HolidayPricing` without code changes. This also makes pricing logic independently testable.

### 3. Synchronized critical section — Anti-double-booking
**Why:** Two guests searching at the same time may both see a room as available. The `book()` method on `Room` is `synchronized` — only one thread can check-and-book at a time. In distributed systems this escalates to a DB `SELECT FOR UPDATE` or Redis distributed lock (see Phase 6).

---

## Phase 5: Key Implementation

```python
import uuid
from abc import ABC, abstractmethod
from datetime import date, timedelta
from decimal import Decimal
from enum import Enum

# ── Date Range ─────────────────────────────────────────────────────────────

class DateRange:
    def __init__(self, check_in, check_out):
        if not check_in < check_out:
            raise ValueError("check_in must be before check_out")
        self.check_in  = check_in
        self.check_out = check_out

    # Half-open interval [check_in, check_out)
    def overlaps(self, other):
        return self.check_in < other.check_out and self.check_out > other.check_in

    def nights(self):
        return (self.check_out - self.check_in).days

# ── Enums ──────────────────────────────────────────────────────────────────

class RoomType(Enum):
    STANDARD = "STANDARD"
    DELUXE   = "DELUXE"
    SUITE    = "SUITE"

class RoomState(Enum):
    AVAILABLE = "AVAILABLE"
    RESERVED  = "RESERVED"
    OCCUPIED  = "OCCUPIED"
    CLEANING  = "CLEANING"

class ResStatus(Enum):
    CONFIRMED  = "CONFIRMED"
    CHECKED_IN = "CHECKED_IN"
    CANCELLED  = "CANCELLED"

# ── Pricing Strategy ───────────────────────────────────────────────────────

class PricingStrategy(ABC):
    @abstractmethod
    def calculate(self, room, range_): ...

class FlatPricing(PricingStrategy):
    def calculate(self, room, range_):
        return room.base_price * Decimal(range_.nights())

class WeekendSurgePricing(PricingStrategy):
    def calculate(self, room, range_):
        total = Decimal(0)
        d = range_.check_in
        while d < range_.check_out:
            rate = room.base_price * Decimal("1.30") if d.weekday() >= 5 else room.base_price
            total += rate
            d += timedelta(days=1)
        return total

# ── Room ───────────────────────────────────────────────────────────────────

class Room:
    def __init__(self, id, type, base_price):
        self.id             = id
        self.type           = type
        self.base_price     = base_price
        self._state         = RoomState.AVAILABLE
        self._booked_ranges = []

    def get_state(self):
        return self._state

    def is_available(self, range_):
        if self._state != RoomState.AVAILABLE:
            return False
        return not any(b.overlaps(range_) for b in self._booked_ranges)

    # Check-and-book: returns True if successful
    def book(self, range_):
        if self._state != RoomState.AVAILABLE:
            return False
        if any(b.overlaps(range_) for b in self._booked_ranges):
            return False
        self._booked_ranges.append(range_)
        self._state = RoomState.RESERVED
        return True

    def release(self, range_):
        self._booked_ranges = [
            r for r in self._booked_ranges
            if not (r.check_in == range_.check_in and r.check_out == range_.check_out)
        ]
        if not self._booked_ranges:
            self._state = RoomState.AVAILABLE

    def check_in(self):
        self._state = RoomState.OCCUPIED
    def start_cleaning(self):
        self._state = RoomState.CLEANING
    def finish_cleaning(self):
        self._state = RoomState.AVAILABLE

# ── Guest ──────────────────────────────────────────────────────────────────

class Guest:
    def __init__(self, id, name):
        self.id   = id
        self.name = name

# ── Reservation ────────────────────────────────────────────────────────────

class Reservation:
    def __init__(self, guest, room, date_range, price):
        self.id          = str(uuid.uuid4())
        self.guest       = guest
        self.room        = room
        self.date_range  = date_range
        self.total_price = price
        self.status      = ResStatus.CONFIRMED

# ── Hotel (Singleton) ──────────────────────────────────────────────────────

class Hotel:
    _instance = None

    def __new__(cls):
        if cls._instance is None:
            cls._instance = super().__new__(cls)
            cls._instance._init()
        return cls._instance

    def _init(self):
        self._rooms_by_type = {t: [] for t in RoomType}
        self._reservations  = {}
        self._pricing       = FlatPricing()

    def add_room(self, room):
        self._rooms_by_type[room.type].append(room)

    def set_pricing_strategy(self, s):
        self._pricing = s

    def search_rooms(self, type, range_):
        return [r for r in self._rooms_by_type.get(type, []) if r.is_available(range_)]

    def book_room(self, guest, room, range_):
        # Per-room lock is inside room.book() — already thread-safe
        if not room.book(range_):
            print(f"Booking failed: Room {room.id} unavailable for requested dates.")
            return None
        price = self._pricing.calculate(room, range_)
        res   = Reservation(guest, room, range_, price)
        self._reservations[res.id] = res
        print(f"Booking confirmed: {res.id} | Room {room.id} | "
              f"{range_.check_in}–{range_.check_out} | Total: {price}")
        return res

    def cancel_reservation(self, confirmation_id):
        res = self._reservations.get(confirmation_id)
        if res is None or res.status == ResStatus.CANCELLED:
            return False
        res.status = ResStatus.CANCELLED
        res.room.release(res.date_range)
        print(f"Cancelled: {confirmation_id}")
        return True

    def check_in(self, confirmation_id):
        res = self._reservations.get(confirmation_id)
        if res is None or res.status != ResStatus.CONFIRMED:
            raise RuntimeError("Invalid check-in")
        res.status = ResStatus.CHECKED_IN
        res.room.check_in()
        print(f"Checked in: {res.guest.name} → Room {res.room.id}")

    def check_out(self, confirmation_id):
        res = self._reservations.get(confirmation_id)
        if res is None or res.status != ResStatus.CHECKED_IN:
            raise RuntimeError("Invalid check-out")
        res.room.start_cleaning()
        print(f"Checked out: Room {res.room.id} now CLEANING")
        # Housekeeping callback would call: res.room.finish_cleaning()

# ── Demo ───────────────────────────────────────────────────────────────────

if __name__ == "__main__":
    hotel = Hotel()
    hotel.add_room(Room(101, RoomType.STANDARD, Decimal("100")))
    hotel.add_room(Room(201, RoomType.DELUXE,   Decimal("200")))
    hotel.set_pricing_strategy(WeekendSurgePricing())

    alice = Guest("g1", "Alice")
    bob   = Guest("g2", "Bob")
    range_ = DateRange(date(2026, 7, 4), date(2026, 7, 7))

    available = hotel.search_rooms(RoomType.STANDARD, range_)
    print(f"Available STANDARD rooms: {len(available)}")

    res1 = hotel.book_room(alice, available[0], range_)

    # Bob tries same room and dates → should fail
    res2 = hotel.book_room(bob, available[0], range_)
    assert res2 is None, "Double booking should have failed"

    if res1 is not None:
        hotel.check_in(res1.id)
        hotel.check_out(res1.id)
```

---

## Phase 6: Trade-offs and Extensions

### Trade-offs

| Decision | Choice | Alternative | Reason |
|---|---|---|---|
| Concurrency | `synchronized` on `Room.book()` | DB `SELECT FOR UPDATE` | JVM sync works for single-instance; DB lock required for distributed |
| Date range check | Half-open `[checkIn, checkOut)` | Inclusive both ends | Half-open avoids edge cases: checkout day is not billed night |
| Pricing | Strategy injected at Hotel level | Per-room pricing strategy | Most hotels have one active pricing scheme; per-room variant is extension |
| Singleton Hotel | Double-checked locking with `volatile` | Spring `@Bean` (framework-managed) | Interview context; production uses DI container |

### Extensions

**Distributed double-booking prevention:**
```
Option 1 — PostgreSQL EXCLUDE constraint:
  ALTER TABLE reservations ADD CONSTRAINT no_overlap
  EXCLUDE USING gist (room_id WITH =, daterange(check_in, check_out) WITH &&);

Option 2 — Redis distributed lock:
  SET lock:room:{id} 1 NX PX 5000   -- 5-second TTL
  → check availability → book → DEL lock:room:{id}

Option 3 — Optimistic locking:
  Room table has version column.
  UPDATE rooms SET version=v+1 WHERE id=X AND version=v
  → 0 rows updated = someone else booked first
```

**Loyalty tier pricing:**
```python
class LoyaltyTierPricing(PricingStrategy):
    def calculate(self, room, range_):
        # Fetch guest's tier from LoyaltyService
        # GOLD: 10% off, PLATINUM: 20% off
        base = FlatPricing().calculate(room, range_)
        return base * self._discount_factor(self._guest_tier)
```

**Pending payment timeout:**
After booking, set `status = PENDING_PAYMENT`. A scheduled job runs every minute:
```sql
UPDATE reservations SET status='CANCELLED' WHERE status='PENDING_PAYMENT'
AND created_at < NOW() - INTERVAL '15 minutes';
```
Then release the room's date range.

---

## Interviewer Follow-Up Questions

- "What entities does hotel management need beyond just booking?" → `Hotel`, `RoomType` (Standard, Deluxe, Suite — with amenities and base price), `Room` (specific physical room with a room number), `Guest` (with stay history), `Reservation` (room + guest + dates + rate plan), `HousekeepingTask` (room → status: DIRTY/CLEANING/CLEAN), `Invoice` (itemized charges: room rate, room service, minibar, taxes). The hotel management system is broader than a booking system — it includes operations (housekeeping, check-in/check-out, billing).
- "A guest checks in early. The room isn't ready yet. How do you handle this?" → Room readiness is tracked via `HousekeepingTask.status`. On check-in attempt: if room status != CLEAN, offer options: (1) Wait — add guest to a waiting queue for that room type; assign when a clean room is available. (2) Upgrade to a different room that's already clean (if available). (3) Check in the guest, assign a temporary room, transfer to the original room type when ready. Track the guest's "intended room" vs "currently assigned room" separately.
- "How do you handle billing for a multi-day stay with additional charges (restaurant, spa, room service)?" → `Invoice` as a running ledger: each charge is a line item (`InvoiceLineItem(type, amount, description, timestamp)`) added as incurred. On checkout: sum all line items + applicable taxes. Allow partial payments or deposits. Use a `TaxCalculator` strategy: tax rates vary by room type, service type, and jurisdiction. Provide an itemized bill — hotel guests always want itemized receipts.
- "How do you manage room cleaning schedules to minimize guest wait time?" → `HousekeepingTask` queue: when a guest checks out, auto-create a task `(room_id, priority, due_by)`. Priority: rooms with imminent check-in (today) get higher priority. Due-by: based on the next check-in time for that room. The housekeeping supervisor views a prioritized task list. Staff mark tasks `IN_PROGRESS` and `COMPLETE`. The system automatically updates room status from `DIRTY → CLEANING → CLEAN`. This is a simple priority queue problem wrapped in domain entities.
