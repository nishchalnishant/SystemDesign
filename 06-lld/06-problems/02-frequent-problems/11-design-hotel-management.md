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

```java
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

enum RoomType {
    SINGLE, DOUBLE, SUITE
}

enum RoomStatus {
    AVAILABLE, RESERVED, OCCUPIED, MAINTENANCE
}

enum ReservationStatus {
    CONFIRMED, CHECKED_IN, CHECKED_OUT, CANCELLED
}

abstract class Room {
    private final String roomId;
    private final String roomNumber;
    private final int floor;
    private final RoomType roomType;
    private RoomStatus status;

    public Room(String roomId, String roomNumber, int floor, RoomType roomType) {
        this.roomId = roomId;
        this.roomNumber = roomNumber;
        this.floor = floor;
        this.roomType = roomType;
        this.status = RoomStatus.AVAILABLE;
    }

    public abstract double basePrice();

    public boolean isAvailable() {
        return status == RoomStatus.AVAILABLE;
    }

    public String getRoomId() {
        return roomId;
    }

    public String getRoomNumber() {
        return roomNumber;
    }

    public int getFloor() {
        return floor;
    }

    public RoomType getRoomType() {
        return roomType;
    }

    public RoomStatus getStatus() {
        return status;
    }

    public void setStatus(RoomStatus status) {
        this.status = status;
    }
}

class SingleRoom extends Room {
    public SingleRoom(String roomId, String roomNumber, int floor) {
        super(roomId, roomNumber, floor, RoomType.SINGLE);
    }

    @Override
    public double basePrice() {
        return 100.0;
    }
}

class DoubleRoom extends Room {
    public DoubleRoom(String roomId, String roomNumber, int floor) {
        super(roomId, roomNumber, floor, RoomType.DOUBLE);
    }

    @Override
    public double basePrice() {
        return 150.0;
    }
}

class Suite extends Room {
    public Suite(String roomId, String roomNumber, int floor) {
        super(roomId, roomNumber, floor, RoomType.SUITE);
    }

    @Override
    public double basePrice() {
        return 300.0;
    }
}

class Guest {
    private final String guestId;
    private final String name;
    private final String email;

    public Guest(String guestId, String name, String email) {
        this.guestId = guestId;
        this.name = name;
        this.email = email;
    }

    public String getGuestId() {
        return guestId;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }
}

class Bill {
    private final String reservationId;
    private final String roomNumber;
    private final int nights;
    private final double amount;
    private final LocalDateTime generatedAt;

    public Bill(String reservationId, String roomNumber, int nights, double amount) {
        this.reservationId = reservationId;
        this.roomNumber = roomNumber;
        this.nights = nights;
        this.amount = amount;
        this.generatedAt = LocalDateTime.now();
    }

    public String getReservationId() {
        return reservationId;
    }

    public String getRoomNumber() {
        return roomNumber;
    }

    public int getNights() {
        return nights;
    }

    public double getAmount() {
        return amount;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }
}

class Reservation {
    private final String reservationId;
    private final Guest guest;
    private final Room room;
    private final LocalDate checkIn;
    private final LocalDate checkOut;
    private ReservationStatus status;

    public Reservation(String reservationId, Guest guest, Room room,
                        LocalDate checkIn, LocalDate checkOut) {
        this.reservationId = reservationId;
        this.guest = guest;
        this.room = room;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
        this.status = ReservationStatus.CONFIRMED;
    }

    public int nights() {
        return (int) ChronoUnit.DAYS.between(checkIn, checkOut);
    }

    public void checkInGuest() {
        if (status != ReservationStatus.CONFIRMED) {
            throw new IllegalStateException("Cannot check in from status " + status);
        }
        status = ReservationStatus.CHECKED_IN;
        room.setStatus(RoomStatus.OCCUPIED);
    }

    public void checkOutGuest() {
        if (status != ReservationStatus.CHECKED_IN) {
            throw new IllegalStateException("Cannot check out from status " + status);
        }
        status = ReservationStatus.CHECKED_OUT;
        room.setStatus(RoomStatus.AVAILABLE);
    }

    public void cancel() {
        if (status == ReservationStatus.CHECKED_OUT || status == ReservationStatus.CANCELLED) {
            throw new IllegalStateException("Cannot cancel from status " + status);
        }
        status = ReservationStatus.CANCELLED;
        if (room.getStatus() == RoomStatus.RESERVED) {
            room.setStatus(RoomStatus.AVAILABLE);
        }
    }

    public String getReservationId() {
        return reservationId;
    }

    public Guest getGuest() {
        return guest;
    }

    public Room getRoom() {
        return room;
    }

    public LocalDate getCheckIn() {
        return checkIn;
    }

    public LocalDate getCheckOut() {
        return checkOut;
    }

    public ReservationStatus getStatus() {
        return status;
    }
}

class Hotel {
    private final String name;
    private final List<Room> rooms = new ArrayList<>();
    private final Map<String, Reservation> reservations = new HashMap<>();

    public Hotel(String name) {
        this.name = name;
    }

    public void addRoom(Room room) {
        rooms.add(room);
    }

    public List<Room> getRooms() {
        return rooms;
    }

    public Map<String, Reservation> getReservations() {
        return reservations;
    }

    private boolean hasConflict(Room room, LocalDate checkIn, LocalDate checkOut) {
        for (Reservation res : reservations.values()) {
            if (!res.getRoom().getRoomId().equals(room.getRoomId())) {
                continue;
            }
            if (res.getStatus() == ReservationStatus.CANCELLED
                    || res.getStatus() == ReservationStatus.CHECKED_OUT) {
                continue;
            }
            // Overlap: existing.checkIn < checkOut AND existing.checkOut > checkIn
            if (res.getCheckIn().isBefore(checkOut) && res.getCheckOut().isAfter(checkIn)) {
                return true;
            }
        }
        return false;
    }

    public List<Room> searchAvailableRooms(LocalDate checkIn, LocalDate checkOut, RoomType roomType) {
        if (!checkIn.isBefore(checkOut)) {
            throw new IllegalArgumentException("checkIn must be before checkOut");
        }
        List<Room> results = new ArrayList<>();
        for (Room room : rooms) {
            if (room.getStatus() == RoomStatus.MAINTENANCE) {
                continue;
            }
            if (roomType != null && room.getRoomType() != roomType) {
                continue;
            }
            if (!hasConflict(room, checkIn, checkOut)) {
                results.add(room);
            }
        }
        return results;
    }

    public Reservation createReservation(Guest guest, Room room,
                                          LocalDate checkIn, LocalDate checkOut) {
        if (hasConflict(room, checkIn, checkOut)) {
            throw new IllegalStateException(
                    "Room " + room.getRoomNumber() + " is not available for these dates");
        }
        Reservation reservation = new Reservation(
                UUID.randomUUID().toString(), guest, room, checkIn, checkOut);
        room.setStatus(RoomStatus.RESERVED);
        reservations.put(reservation.getReservationId(), reservation);
        return reservation;
    }

    public void checkIn(String reservationId) {
        Reservation res = getReservation(reservationId);
        res.checkInGuest();
    }

    public Bill checkOut(String reservationId) {
        Reservation res = getReservation(reservationId);
        res.checkOutGuest();
        double amount = res.getRoom().basePrice() * res.nights();
        return new Bill(reservationId, res.getRoom().getRoomNumber(), res.nights(), amount);
    }

    public void cancelReservation(String reservationId) {
        Reservation res = getReservation(reservationId);
        res.cancel();
    }

    private Reservation getReservation(String reservationId) {
        Reservation res = reservations.get(reservationId);
        if (res == null) {
            throw new NoSuchElementException("Reservation " + reservationId + " not found");
        }
        return res;
    }
}
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

```java
public boolean overlaps(LocalDate aStart, LocalDate aEnd, LocalDate bStart, LocalDate bEnd) {
    return aStart.isBefore(bEnd) && aEnd.isAfter(bStart);
}
```

In SQL: `WHERE check_in < :check_out AND check_out > :check_in AND status NOT IN ('CANCELLED', 'CHECKED_OUT')`

### 2. "How would you implement dynamic pricing?"

Inject a `PricingStrategy` into `BillingService`. Strategies can account for season, occupancy rate, or day of week.

```java
interface PricingStrategy {
    double nightlyRate(Room room, LocalDate night);
}

class StandardPricing implements PricingStrategy {
    @Override
    public double nightlyRate(Room room, LocalDate night) {
        return room.basePrice();
    }
}

class OccupancyPricing implements PricingStrategy {
    private final Hotel hotel;

    public OccupancyPricing(Hotel hotel) {
        this.hotel = hotel;
    }

    @Override
    public double nightlyRate(Room room, LocalDate night) {
        long occupied = hotel.getReservations().values().stream()
                .filter(r -> !r.getCheckIn().isAfter(night) && r.getCheckOut().isAfter(night))
                .filter(r -> r.getStatus() != ReservationStatus.CANCELLED
                        && r.getStatus() != ReservationStatus.CHECKED_OUT)
                .count();
        double occupancyRate = (double) occupied / hotel.getRooms().size();
        double multiplier = 1.0 + (occupancyRate * 0.5); // up to 50% surcharge
        return room.basePrice() * multiplier;
    }
}

class BillingService {
    private final PricingStrategy strategy;

    public BillingService(PricingStrategy strategy) {
        this.strategy = strategy;
    }

    public Bill generateBill(Reservation reservation) {
        double total = 0.0;
        LocalDate current = reservation.getCheckIn();
        while (current.isBefore(reservation.getCheckOut())) {
            total += strategy.nightlyRate(reservation.getRoom(), current);
            current = current.plusDays(1);
        }
        return new Bill(reservation.getReservationId(), reservation.getRoom().getRoomNumber(),
                reservation.nights(), total);
    }
}
```

### 3. "How would you implement cancellation with refund tiers?"

```java
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public double calculateRefund(Reservation reservation, LocalDate cancelDate) {
    long daysUntilCheckin = ChronoUnit.DAYS.between(cancelDate, reservation.getCheckIn());
    double total = reservation.getRoom().basePrice() * reservation.nights();
    if (daysUntilCheckin >= 7) {
        return total;          // full refund
    } else if (daysUntilCheckin >= 3) {
        return total * 0.5;    // 50% refund
    } else if (daysUntilCheckin >= 1) {
        return total * 0.25;   // 25% refund
    } else {
        return 0.0;            // no refund (same day or past)
    }
}
```

### 4. "How would you handle overbooking / waitlists?"

Add a `Waitlist` per room. When a room becomes available (cancellation, check-out), notify the first waiter.

```java
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.Deque;

class WaitlistEntry {
    private final Guest guest;
    private final LocalDate checkIn;
    private final LocalDate checkOut;

    public WaitlistEntry(Guest guest, LocalDate checkIn, LocalDate checkOut) {
        this.guest = guest;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
    }

    public Guest getGuest() {
        return guest;
    }

    public LocalDate getCheckIn() {
        return checkIn;
    }

    public LocalDate getCheckOut() {
        return checkOut;
    }
}

class Waitlist {
    private final Deque<WaitlistEntry> queue = new ArrayDeque<>();

    public void add(Guest guest, LocalDate checkIn, LocalDate checkOut) {
        queue.addLast(new WaitlistEntry(guest, checkIn, checkOut));
    }

    public WaitlistEntry next() {
        return queue.isEmpty() ? null : queue.pollFirst();
    }
}
```

On cancellation: check each waiting entry against the newly freed dates. If compatible, auto-create a reservation and notify the guest.

### 5. "How do you extend this to multiple hotels?"

Add a `HotelChain` (or `HotelRepository`) that holds a list of `Hotel` objects. The search API takes location as a parameter; use geolocation to find hotels near the user, then delegate `search_available_rooms` to each.

```java
import java.time.LocalDate;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class HotelChain {
    private final List<Hotel> hotels = new ArrayList<>();

    public List<Map.Entry<Hotel, Room>> search(String city, LocalDate checkIn, LocalDate checkOut,
                                                RoomType roomType) {
        List<Map.Entry<Hotel, Room>> results = new ArrayList<>();
        for (Hotel hotel : hotels) {
            if (hotel.getCity().equals(city)) {
                List<Room> rooms = hotel.searchAvailableRooms(checkIn, checkOut, roomType);
                for (Room room : rooms) {
                    results.add(new AbstractMap.SimpleEntry<>(hotel, room));
                }
            }
        }
        return results;
    }
}
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

---

## Related

**Patterns applied here**

- [Factory Pattern](../../03-design-patterns/01-creational/factory-pattern.md)
- [State Pattern](../../03-design-patterns/03-behavioral/state-pattern.md)
- [Strategy Pattern](../../03-design-patterns/03-behavioral/strategy-pattern.md)

**SOLID focus**: [Single Responsibility](../../02-solid-principles/01-single-responsibility.md) · [Open/Closed](../../02-solid-principles/02-open-closed.md)

**Practice next**

- [Design BookMyShow](06-design-bookmyshow.md)
- [Design Library Management](../03-domain-specific/20-design-library-management.md)

Booking, lending and seating share the reservation lifecycle.

**Frameworks**: [LLD Template](../../../07-interview-templates/01-frameworks/02-lld-template.md) · [UML Diagrams](../../01-oop-fundamentals/uml-diagrams.md) · [OOP Four Pillars](../../01-oop-fundamentals/four-pillars.md)
