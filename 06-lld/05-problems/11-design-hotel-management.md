> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design a Hotel Management System — tests your ability to model real-world inventory with date-based constraints and dynamic pricing.
>
> **Key concepts:**
> - Core Entities: `Hotel`, `Room`, `RoomType` (Enum), `Guest`, `Reservation`, `Invoice`.
> - Date-based Inventory: The hardest part is checking if a room is available between `startDate` and `endDate`. 
>   - Option A: Store reservations as a list and check for overlapping date ranges (O(N) per room).
>   - Option B: Use a timeline/interval tree for faster querying.
> - Patterns: Factory (for generating specific room types), Strategy (for pricing — e.g., weekend rates vs weekday rates, or loyalty discounts).
> - Concurrency: Similar to BookMyShow, multiple guests might try to book the last available Deluxe room for the same dates simultaneously. Require locks on the specific room or room type.
>
> **Key takeaway:** The concept of an "Inventory" that varies over time is the core challenge. Make sure your `searchAvailableRooms()` method clearly handles date range overlaps logic.

---
module: 06-lld
topic: Problems
status: unread
tags: [06-lld, lld, hotel-management, factory, state, strategy]
---
# Design a Hotel Management System

> **Difficulty**: Medium  
> **Asked at**: Amazon, Booking.com  
> **Key Patterns**: Factory (room types), State (room/reservation status), Strategy (pricing)

---

## Understanding the Problem

Design a hotel booking system where guests can search for available rooms by type and date range, make reservations, check in and check out, and receive a bill based on their stay duration and room pricing.

---

## Clarifying Questions

**You**: "What room types does the hotel support?"  
**Interviewer**: "Single, Double, and Suite. Each has a different base price."

**You**: "How do we define availability — does a room need to be both unoccupied and not reserved for the requested dates?"  
**Interviewer**: "Correct. A room is available only if no existing reservation overlaps with the requested check-in/check-out dates."

**You**: "What happens at check-out — is a bill generated automatically?"  
**Interviewer**: "Yes. Check-out should compute total cost based on nights stayed and hand back a bill."

**You**: "Can a guest cancel a reservation? Is there a refund policy?"  
**Interviewer**: "Yes, guests can cancel. Refund tiers based on how far in advance — cover that in deep dive."

**You**: "Do we support multiple hotels or just one?"  
**Interviewer**: "Model a single hotel for now. Mention how you'd extend to multiple."

**You**: "Should we handle concurrent reservations — two guests booking the same room simultaneously?"  
**Interviewer**: "Mention it, but you don't need to implement locking for now."

---

## Final Requirements

**In scope:**
1. Search available rooms by type and date range
2. Create a reservation for a guest
3. Check in (marks room OCCUPIED)
4. Check out (marks room AVAILABLE, generates bill)
5. Cancel a reservation
6. Prevent double-booking (date range overlap detection)

**Out of scope:**
- Multi-hotel support
- Payment processing
- Loyalty programs
- Concurrent access / locking

---

## Core Entities and Relationships

| Entity | Responsibility |
|--------|---------------|
| Hotel | Holds all rooms; entry point for room search |
| Room | Abstract base for SingleRoom/DoubleRoom/Suite; tracks status |
| RoomType | Enum — SINGLE, DOUBLE, SUITE |
| RoomStatus | Enum — AVAILABLE, RESERVED, OCCUPIED, MAINTENANCE |
| Guest | Identity of the person making a reservation |
| Reservation | Links guest + room + dates; tracks its own lifecycle |
| ReservationStatus | Enum — CONFIRMED, CHECKED_IN, CHECKED_OUT, CANCELLED |
| PricingStrategy | Interface — computes nightly rate for a room |
| BillingService | Computes total bill at check-out |

`Hotel` owns a list of `Room` objects. `Reservation` owns its status transitions. `BillingService` uses a `PricingStrategy` to compute total cost. Room subtypes override `base_price()` for the Factory pattern.

---

## Class Design

### Room (abstract)

| Requirement | What Room must track |
|-------------|---------------------|
| Identity | room_id, room_number, floor |
| Type | room_type: RoomType |
| State | status: RoomStatus |
| Pricing | base_price() -> float |

```
class Room (abstract):
- room_id: str
- room_number: str
- floor: int
- room_type: RoomType
- status: RoomStatus
+ base_price() -> float  # abstract
+ is_available() -> bool

class SingleRoom(Room):
+ base_price() -> float  # returns 100.0

class DoubleRoom(Room):
+ base_price() -> float  # returns 150.0

class Suite(Room):
+ base_price() -> float  # returns 300.0
```

### Reservation

| Requirement | What Reservation must track |
|-------------|----------------------------|
| Identity | reservation_id |
| Parties | guest: Guest, room: Room |
| Dates | check_in: date, check_out: date |
| State | status: ReservationStatus |

```
class Reservation:
- reservation_id: str
- guest: Guest
- room: Room
- check_in: date
- check_out: date
- status: ReservationStatus
+ nights() -> int
+ confirm() -> None
+ check_in_guest() -> None
+ check_out_guest() -> None
+ cancel() -> None
```

### Hotel

```
class Hotel:
- rooms: List[Room]
- reservations: Dict[str, Reservation]
+ search_available_rooms(check_in, check_out, room_type) -> List[Room]
+ create_reservation(guest, room, check_in, check_out) -> Reservation
+ check_in(reservation_id) -> None
+ check_out(reservation_id) -> Bill
+ cancel_reservation(reservation_id) -> None
- _has_conflict(room, check_in, check_out) -> bool
```

### BillingService / PricingStrategy

```
class PricingStrategy (abstract):
+ calculate(room: Room, nights: int) -> float

class StandardPricing(PricingStrategy):
+ calculate(room, nights) -> float  # base_price * nights

class WeekendPricing(PricingStrategy):
+ calculate(room, nights) -> float  # base_price * nights * 1.2

class Bill:
- reservation_id: str
- room_number: str
- nights: int
- amount: float
- generated_at: datetime
```

---

## Implementation

### Core Method: search_available_rooms / _has_conflict

**Core logic:**
1. Filter rooms by `room_type` if specified
2. Filter rooms where `status != MAINTENANCE`
3. For each candidate room, check whether any existing CONFIRMED or CHECKED_IN reservation overlaps with `[check_in, check_out)`
4. Two date ranges overlap if: `existing.check_in < check_out AND existing.check_out > check_in`
5. Return rooms with no conflict

**Edge cases:**
- check_in == check_out → raise `ValueError` (zero-night stay)
- check_in > check_out → raise `ValueError`
- Room in MAINTENANCE → never returned even if no reservation

```python
import uuid
from datetime import date, datetime
from enum import Enum, auto
from typing import Optional, List, Dict
from abc import ABC, abstractmethod


class RoomType(Enum):
    SINGLE = auto()
    DOUBLE = auto()
    SUITE = auto()


class RoomStatus(Enum):
    AVAILABLE = auto()
    RESERVED = auto()
    OCCUPIED = auto()
    MAINTENANCE = auto()


class ReservationStatus(Enum):
    CONFIRMED = auto()
    CHECKED_IN = auto()
    CHECKED_OUT = auto()
    CANCELLED = auto()


class Room(ABC):
    def __init__(self, room_id: str, room_number: str, floor: int, room_type: RoomType):
        self.room_id = room_id
        self.room_number = room_number
        self.floor = floor
        self.room_type = room_type
        self.status = RoomStatus.AVAILABLE

    @abstractmethod
    def base_price(self) -> float:
        pass

    def is_available(self) -> bool:
        return self.status == RoomStatus.AVAILABLE


class SingleRoom(Room):
    def __init__(self, room_id: str, room_number: str, floor: int):
        super().__init__(room_id, room_number, floor, RoomType.SINGLE)

    def base_price(self) -> float:
        return 100.0


class DoubleRoom(Room):
    def __init__(self, room_id: str, room_number: str, floor: int):
        super().__init__(room_id, room_number, floor, RoomType.DOUBLE)

    def base_price(self) -> float:
        return 150.0


class Suite(Room):
    def __init__(self, room_id: str, room_number: str, floor: int):
        super().__init__(room_id, room_number, floor, RoomType.SUITE)

    def base_price(self) -> float:
        return 300.0


class Guest:
    def __init__(self, guest_id: str, name: str, email: str):
        self.guest_id = guest_id
        self.name = name
        self.email = email


class Bill:
    def __init__(self, reservation_id: str, room_number: str,
                 nights: int, amount: float):
        self.reservation_id = reservation_id
        self.room_number = room_number
        self.nights = nights
        self.amount = amount
        self.generated_at = datetime.utcnow()


class Reservation:
    def __init__(self, reservation_id: str, guest: Guest, room: Room,
                 check_in: date, check_out: date):
        self.reservation_id = reservation_id
        self.guest = guest
        self.room = room
        self.check_in = check_in
        self.check_out = check_out
        self.status = ReservationStatus.CONFIRMED

    def nights(self) -> int:
        return (self.check_out - self.check_in).days

    def check_in_guest(self) -> None:
        if self.status != ReservationStatus.CONFIRMED:
            raise ValueError(f"Cannot check in from status {self.status}")
        self.status = ReservationStatus.CHECKED_IN
        self.room.status = RoomStatus.OCCUPIED

    def check_out_guest(self) -> None:
        if self.status != ReservationStatus.CHECKED_IN:
            raise ValueError(f"Cannot check out from status {self.status}")
        self.status = ReservationStatus.CHECKED_OUT
        self.room.status = RoomStatus.AVAILABLE

    def cancel(self) -> None:
        if self.status in (ReservationStatus.CHECKED_OUT, ReservationStatus.CANCELLED):
            raise ValueError(f"Cannot cancel from status {self.status}")
        self.status = ReservationStatus.CANCELLED
        if self.room.status == RoomStatus.RESERVED:
            self.room.status = RoomStatus.AVAILABLE


class Hotel:
    def __init__(self, name: str):
        self.name = name
        self.rooms: List[Room] = []
        self.reservations: Dict[str, Reservation] = {}

    def add_room(self, room: Room) -> None:
        self.rooms.append(room)

    def _has_conflict(self, room: Room, check_in: date, check_out: date) -> bool:
        for res in self.reservations.values():
            if res.room.room_id != room.room_id:
                continue
            if res.status in (ReservationStatus.CANCELLED, ReservationStatus.CHECKED_OUT):
                continue
            # Overlap: existing.check_in < check_out AND existing.check_out > check_in
            if res.check_in < check_out and res.check_out > check_in:
                return True
        return False

    def search_available_rooms(self, check_in: date, check_out: date,
                                room_type: Optional[RoomType] = None) -> List[Room]:
        if check_in >= check_out:
            raise ValueError("check_in must be before check_out")
        results = []
        for room in self.rooms:
            if room.status == RoomStatus.MAINTENANCE:
                continue
            if room_type and room.room_type != room_type:
                continue
            if not self._has_conflict(room, check_in, check_out):
                results.append(room)
        return results

    def create_reservation(self, guest: Guest, room: Room,
                           check_in: date, check_out: date) -> Reservation:
        if self._has_conflict(room, check_in, check_out):
            raise ValueError(f"Room {room.room_number} is not available for these dates")
        reservation = Reservation(
            reservation_id=str(uuid.uuid4()),
            guest=guest,
            room=room,
            check_in=check_in,
            check_out=check_out
        )
        room.status = RoomStatus.RESERVED
        self.reservations[reservation.reservation_id] = reservation
        return reservation

    def check_in(self, reservation_id: str) -> None:
        res = self._get_reservation(reservation_id)
        res.check_in_guest()

    def check_out(self, reservation_id: str) -> Bill:
        res = self._get_reservation(reservation_id)
        res.check_out_guest()
        amount = res.room.base_price() * res.nights()
        return Bill(
            reservation_id=reservation_id,
            room_number=res.room.room_number,
            nights=res.nights(),
            amount=amount
        )

    def cancel_reservation(self, reservation_id: str) -> None:
        res = self._get_reservation(reservation_id)
        res.cancel()

    def _get_reservation(self, reservation_id: str) -> Reservation:
        if reservation_id not in self.reservations:
            raise KeyError(f"Reservation {reservation_id} not found")
        return self.reservations[reservation_id]
```

---

## Verification

Trace: Guest Alice books Room 101 (Double, $150/night) for 3 nights, checks in, checks out.

1. `search_available_rooms(date(2024,6,1), date(2024,6,4), RoomType.DOUBLE)` → returns [Room 101] (no conflicts)
2. `create_reservation(alice, room_101, ...)` → Reservation(id="r1"), room_101.status = RESERVED
3. `search_available_rooms(date(2024,6,2), date(2024,6,3), RoomType.DOUBLE)` → Room 101 NOT returned (conflict: r1.check_in=6/1 < 6/3, r1.check_out=6/4 > 6/2)
4. `check_in("r1")` → r1.status = CHECKED_IN, room_101.status = OCCUPIED
5. `check_out("r1")` → r1.status = CHECKED_OUT, room_101.status = AVAILABLE, Bill(nights=3, amount=450.0)

---

## Deep Dive & Extensibility

### 1. "How exactly does date range overlap detection work?"

Two intervals [A_start, A_end) and [B_start, B_end) overlap if and only if:
`A_start < B_end AND A_end > B_start`

Equivalently, they do NOT overlap if `A_end <= B_start OR B_end <= A_start`.

This handles all cases: B fully before A, B fully after A, partial overlaps, B inside A, A inside B.

```python
def overlaps(a_start: date, a_end: date, b_start: date, b_end: date) -> bool:
    return a_start < b_end and a_end > b_start
```

In SQL: `WHERE check_in < :check_out AND check_out > :check_in AND status NOT IN ('CANCELLED', 'CHECKED_OUT')`

### 2. "How would you implement dynamic pricing?"

Inject a `PricingStrategy` into `BillingService`. Strategies can account for season, occupancy rate, or day of week.

```python
class PricingStrategy(ABC):
    @abstractmethod
    def nightly_rate(self, room: Room, night: date) -> float:
        pass

class StandardPricing(PricingStrategy):
    def nightly_rate(self, room: Room, night: date) -> float:
        return room.base_price()

class OccupancyPricing(PricingStrategy):
    def __init__(self, hotel: Hotel):
        self.hotel = hotel

    def nightly_rate(self, room: Room, night: date) -> float:
        occupied = sum(
            1 for r in self.hotel.reservations.values()
            if r.check_in <= night < r.check_out
            and r.status not in (ReservationStatus.CANCELLED, ReservationStatus.CHECKED_OUT)
        )
        occupancy_rate = occupied / len(self.hotel.rooms)
        multiplier = 1.0 + (occupancy_rate * 0.5)  # up to 50% surcharge
        return room.base_price() * multiplier

class BillingService:
    def __init__(self, strategy: PricingStrategy):
        self.strategy = strategy

    def generate_bill(self, reservation: Reservation) -> Bill:
        total = 0.0
        current = reservation.check_in
        while current < reservation.check_out:
            total += self.strategy.nightly_rate(reservation.room, current)
            current = date(current.year, current.month, current.day + 1)
        return Bill(reservation.reservation_id, reservation.room.room_number,
                    reservation.nights(), total)
```

### 3. "How would you implement cancellation with refund tiers?"

```python
from datetime import date

def calculate_refund(reservation: Reservation, cancel_date: date) -> float:
    days_until_checkin = (reservation.check_in - cancel_date).days
    total = reservation.room.base_price() * reservation.nights()
    if days_until_checkin >= 7:
        return total          # full refund
    elif days_until_checkin >= 3:
        return total * 0.5    # 50% refund
    elif days_until_checkin >= 1:
        return total * 0.25   # 25% refund
    else:
        return 0.0            # no refund (same day or past)
```

### 4. "How would you handle overbooking / waitlists?"

Add a `Waitlist` per room. When a room becomes available (cancellation, check-out), notify the first waiter.

```python
from collections import deque

class Waitlist:
    def __init__(self):
        self.queue: deque = deque()  # (guest, check_in, check_out)

    def add(self, guest: Guest, check_in: date, check_out: date) -> None:
        self.queue.append((guest, check_in, check_out))

    def next(self):
        return self.queue.popleft() if self.queue else None
```

On cancellation: check each waiting entry against the newly freed dates. If compatible, auto-create a reservation and notify the guest.

### 5. "How do you extend this to multiple hotels?"

Add a `HotelChain` (or `HotelRepository`) that holds a list of `Hotel` objects. The search API takes location as a parameter; use geolocation to find hotels near the user, then delegate `search_available_rooms` to each.

```python
class HotelChain:
    def __init__(self):
        self.hotels: List[Hotel] = []

    def search(self, city: str, check_in: date, check_out: date,
               room_type: Optional[RoomType] = None) -> List[tuple]:
        results = []
        for hotel in self.hotels:
            if hotel.city == city:
                rooms = hotel.search_available_rooms(check_in, check_out, room_type)
                results.extend([(hotel, room) for room in rooms])
        return results
```

---

## Interviewer Questions by Level

**Junior**: Walk me through the date overlap formula. Why is `check_in < check_out_B AND check_out > check_in_B` the right condition?

**Mid-level**: The Room has a `status` field, but availability is determined by reservations, not just status. Could they get out of sync? How do you prevent that?

**Senior**: At 100k concurrent users on a booking site, two users try to book the last available room simultaneously. Your `_has_conflict` check passes for both, then both create reservations. How do you prevent this in production?

---

## Common Interview Questions

- **Q: What's the date range overlap formula?**  
  A: `a_start < b_end AND a_end > b_start`. Two ranges don't overlap only if one ends before the other starts.

- **Q: Why have both Room.status and Reservation.status?**  
  A: Room.status reflects physical state (is it physically occupied right now?). Reservation.status tracks the booking lifecycle. A room can be RESERVED (has a future booking) but physically AVAILABLE (nobody checked in yet).

- **Q: How do you prevent double-booking in SQL?**  
  A: Use a unique partial index or a DB-level conflict check inside a transaction with SELECT FOR UPDATE. The application-level check is a safety net, not the primary guard.

- **Q: What does check-out return and why?**  
  A: A Bill object — it's the primary output of check-out. Returning it from the method makes it easy for the caller to display or email it without a separate fetch.

- **Q: How would you add room amenities (wifi, ocean view, jacuzzi)?**  
  A: Add a `Set[str]` amenities field to Room. Search can filter by required amenities. No structural change to the class hierarchy needed.

- **Q: Why use abstract Room instead of a RoomType enum + single Room class?**  
  A: Factory pattern via subclasses lets each type override `base_price()` and potentially other behaviors. A single class with an enum is simpler but requires conditionals whenever behavior differs by type.
