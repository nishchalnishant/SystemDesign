# Design Hotel Management System

> **Difficulty**: Medium
> **Topics**: State Pattern, Strategy Pattern, Concurrency, Date Range Logic
> **Extension**: Dynamic pricing, distributed double-booking prevention, loyalty tiers

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

## Phase 5: Key Java Implementation

```java
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

// ── Date Range ─────────────────────────────────────────────────────────────

class DateRange {
    final LocalDate checkIn;
    final LocalDate checkOut;

    DateRange(LocalDate checkIn, LocalDate checkOut) {
        if (!checkIn.isBefore(checkOut))
            throw new IllegalArgumentException("checkIn must be before checkOut");
        this.checkIn  = checkIn;
        this.checkOut = checkOut;
    }

    // Half-open interval [checkIn, checkOut)
    boolean overlaps(DateRange other) {
        return checkIn.isBefore(other.checkOut) && checkOut.isAfter(other.checkIn);
    }

    long nights() { return ChronoUnit.DAYS.between(checkIn, checkOut); }
}

// ── Enums ──────────────────────────────────────────────────────────────────

enum RoomType   { STANDARD, DELUXE, SUITE }
enum RoomState  { AVAILABLE, RESERVED, OCCUPIED, CLEANING }
enum ResStatus  { CONFIRMED, CHECKED_IN, CANCELLED }

// ── Pricing Strategy ───────────────────────────────────────────────────────

interface PricingStrategy {
    BigDecimal calculate(Room room, DateRange range);
}

class FlatPricing implements PricingStrategy {
    public BigDecimal calculate(Room room, DateRange range) {
        return room.getBasePrice().multiply(BigDecimal.valueOf(range.nights()));
    }
}

class WeekendSurgePricing implements PricingStrategy {
    public BigDecimal calculate(Room room, DateRange range) {
        BigDecimal total = BigDecimal.ZERO;
        LocalDate d = range.checkIn;
        while (d.isBefore(range.checkOut)) {
            boolean isWeekend = d.getDayOfWeek().getValue() >= 6;
            BigDecimal rate   = isWeekend
                    ? room.getBasePrice().multiply(new BigDecimal("1.30"))
                    : room.getBasePrice();
            total = total.add(rate);
            d = d.plusDays(1);
        }
        return total;
    }
}

// ── Room ───────────────────────────────────────────────────────────────────

class Room {
    private final int id;
    private final RoomType type;
    private final BigDecimal basePrice;
    private RoomState state;
    private final List<DateRange> bookedRanges = new ArrayList<>();

    Room(int id, RoomType type, BigDecimal basePrice) {
        this.id        = id;
        this.type      = type;
        this.basePrice = basePrice;
        this.state     = RoomState.AVAILABLE;
    }

    int getId()               { return id; }
    RoomType getType()        { return type; }
    BigDecimal getBasePrice() { return basePrice; }
    RoomState getState()      { return state; }

    synchronized boolean isAvailable(DateRange range) {
        if (state != RoomState.AVAILABLE) return false;
        for (DateRange booked : bookedRanges) {
            if (booked.overlaps(range)) return false;
        }
        return true;
    }

    // Atomic check-and-book: returns true if successful
    synchronized boolean book(DateRange range) {
        if (!isAvailable(range)) return false;
        bookedRanges.add(range);
        state = RoomState.RESERVED;
        return true;
    }

    synchronized void release(DateRange range) {
        bookedRanges.removeIf(r -> r.checkIn.equals(range.checkIn) && r.checkOut.equals(range.checkOut));
        if (bookedRanges.isEmpty()) state = RoomState.AVAILABLE;
    }

    synchronized void checkIn()  { state = RoomState.OCCUPIED; }
    synchronized void startCleaning() { state = RoomState.CLEANING; }
    synchronized void finishCleaning() { state = RoomState.AVAILABLE; }
}

// ── Guest ──────────────────────────────────────────────────────────────────

class Guest {
    final String id;
    final String name;
    Guest(String id, String name) { this.id = id; this.name = name; }
}

// ── Reservation ────────────────────────────────────────────────────────────

class Reservation {
    final String id;
    final Guest guest;
    final Room room;
    final DateRange dateRange;
    final BigDecimal totalPrice;
    ResStatus status;

    Reservation(Guest guest, Room room, DateRange range, BigDecimal price) {
        this.id         = UUID.randomUUID().toString();
        this.guest      = guest;
        this.room       = room;
        this.dateRange  = range;
        this.totalPrice = price;
        this.status     = ResStatus.CONFIRMED;
    }
}

// ── Hotel (Singleton) ──────────────────────────────────────────────────────

public class Hotel {
    private static volatile Hotel instance;
    private final Map<RoomType, List<Room>> roomsByType   = new HashMap<>();
    private final Map<String, Reservation> reservations   = new ConcurrentHashMap<>();
    private PricingStrategy pricingStrategy = new FlatPricing();

    private Hotel() {
        for (RoomType t : RoomType.values()) roomsByType.put(t, new ArrayList<>());
    }

    public static Hotel getInstance() {
        if (instance == null) {
            synchronized (Hotel.class) {
                if (instance == null) instance = new Hotel();
            }
        }
        return instance;
    }

    public void addRoom(Room room) { roomsByType.get(room.getType()).add(room); }
    public void setPricingStrategy(PricingStrategy s) { this.pricingStrategy = s; }

    public List<Room> searchRooms(RoomType type, DateRange range) {
        List<Room> result = new ArrayList<>();
        for (Room r : roomsByType.getOrDefault(type, Collections.emptyList())) {
            if (r.isAvailable(range)) result.add(r);
        }
        return result;
    }

    public Reservation bookRoom(Guest guest, Room room, DateRange range) {
        // Per-room lock is inside room.book() — already synchronized
        if (!room.book(range)) {
            System.out.println("Booking failed: Room " + room.getId() + " unavailable for requested dates.");
            return null;
        }
        BigDecimal price = pricingStrategy.calculate(room, range);
        Reservation res  = new Reservation(guest, room, range, price);
        reservations.put(res.id, res);
        System.out.printf("Booking confirmed: %s | Room %d | %s–%s | Total: %s%n",
                res.id, room.getId(), range.checkIn, range.checkOut, price);
        return res;
    }

    public boolean cancelReservation(String confirmationId) {
        Reservation res = reservations.get(confirmationId);
        if (res == null || res.status == ResStatus.CANCELLED) return false;
        res.status = ResStatus.CANCELLED;
        res.room.release(res.dateRange);
        System.out.println("Cancelled: " + confirmationId);
        return true;
    }

    public void checkIn(String confirmationId) {
        Reservation res = reservations.get(confirmationId);
        if (res == null || res.status != ResStatus.CONFIRMED) throw new IllegalStateException("Invalid check-in");
        res.status = ResStatus.CHECKED_IN;
        res.room.checkIn();
        System.out.println("Checked in: " + res.guest.name + " → Room " + res.room.getId());
    }

    public void checkOut(String confirmationId) {
        Reservation res = reservations.get(confirmationId);
        if (res == null || res.status != ResStatus.CHECKED_IN) throw new IllegalStateException("Invalid check-out");
        res.room.startCleaning();
        System.out.println("Checked out: Room " + res.room.getId() + " now CLEANING");
        // Housekeeping callback would call: res.room.finishCleaning();
    }
}

// ── Demo ───────────────────────────────────────────────────────────────────

class HotelDemo {
    public static void main(String[] args) {
        Hotel hotel = Hotel.getInstance();
        hotel.addRoom(new Room(101, RoomType.STANDARD, new BigDecimal("100")));
        hotel.addRoom(new Room(201, RoomType.DELUXE,   new BigDecimal("200")));
        hotel.setPricingStrategy(new WeekendSurgePricing());

        Guest alice = new Guest("g1", "Alice");
        Guest bob   = new Guest("g2", "Bob");
        DateRange range = new DateRange(LocalDate.of(2026, 7, 4), LocalDate.of(2026, 7, 7));

        List<Room> available = hotel.searchRooms(RoomType.STANDARD, range);
        System.out.println("Available STANDARD rooms: " + available.size());

        Reservation res1 = hotel.bookRoom(alice, available.get(0), range);

        // Bob tries same room and dates → should fail
        Reservation res2 = hotel.bookRoom(bob, available.get(0), range);
        assert res2 == null : "Double booking should have failed";

        if (res1 != null) {
            hotel.checkIn(res1.id);
            hotel.checkOut(res1.id);
        }
    }
}
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
```java
class LoyaltyTierPricing implements PricingStrategy {
    public BigDecimal calculate(Room room, DateRange range) {
        // Fetch guest's tier from LoyaltyService
        // GOLD: 10% off, PLATINUM: 20% off
        BigDecimal base = new FlatPricing().calculate(room, range);
        return base.multiply(discountFactor(guestTier));
    }
}
```

**Pending payment timeout:**
After booking, set `status = PENDING_PAYMENT`. A scheduled job runs every minute:
```sql
UPDATE reservations SET status='CANCELLED' WHERE status='PENDING_PAYMENT'
AND created_at < NOW() - INTERVAL '15 minutes';
```
Then release the room's date range.
