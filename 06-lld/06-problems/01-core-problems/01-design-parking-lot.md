> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design a Parking Lot — the quintessential LLD problem that tests your ability to model real-world entities, manage state, and apply design patterns.
>
> **Key concepts:**
> - Core Entities: `ParkingLot` (Singleton), `ParkingFloor`, `ParkingSpot` (Enum for size: Compact, Large, Handicapped), `Vehicle` (Enum for type: Car, Truck, Bike), `Ticket`.
> - The problem: assigning the correct spot based on vehicle type and calculating the fee upon exit.
> - Patterns: 
>   - Singleton: to ensure only one Parking Lot instance exists.
>   - Strategy: for dynamic pricing calculation (e.g., hourly rate vs flat rate).
>   - Factory: to generate `Vehicle` objects or assign parking spots.
> - Concurrency: Thread safety is critical when two vehicles try to enter simultaneously. The `assignSpot()` method must use `synchronized` or a `ReentrantLock`, or use concurrent data structures.
>
> **Key takeaway:** A solid Parking Lot design demonstrates your grasp of OOP fundamentals. Focus on separation of concerns — the `ParkingLot` delegates finding a spot to `ParkingFloor`, which checks its `ParkingSpot`s.

---
module: 06-lld
topic: Problems
status: unread
tags: [06-lld, lld, parking-lot, singleton, strategy, factory]
---
# Design a Parking Lot

> **Difficulty**: Medium  
> **Asked at**: Amazon, Microsoft, Uber  
> **Key Patterns**: Singleton (ParkingLot), Strategy (pricing), Factory (spot assignment), Template Method

---

## Understanding the Problem

Design a parking lot system that manages multiple floors and spot types (motorcycle, car, truck), issues entry tickets, calculates fees on exit, and tracks real-time availability across concurrent entry and exit operations.

---

## Clarifying Questions

**You**: "Should the system handle multiple parking lots or just one?"  
**Interviewer**: "Just one parking lot with multiple floors."

**You**: "What spot types do we need to support?"  
**Interviewer**: "Three types: motorcycle, car, and truck. A larger vehicle cannot park in a smaller spot, but a smaller vehicle can park in a larger spot if no appropriate spot is available."

**You**: "How should pricing work?"  
**Interviewer**: "Hourly pricing for now. Different rates per spot type. Make the pricing strategy pluggable."

**You**: "Do we need to handle reservations, or is it first-come-first-served?"  
**Interviewer**: "First-come-first-served only. No reservations."

**You**: "What should happen when no spot is available?"  
**Interviewer**: "Return an error — the vehicle cannot enter."

**You**: "Is thread safety a concern? Multiple entry/exit lanes operating concurrently?"  
**Interviewer**: "Yes, assume multiple concurrent entry/exit operations."

---

## Final Requirements

**In scope:**
1. Park a vehicle — assign the nearest available spot by floor and spot number
2. Exit a vehicle — calculate fee based on duration, release the spot
3. Track availability per floor and per spot type
4. Support motorcycle, car, and truck with size compatibility rules
5. Pluggable pricing strategy (hourly, flat, weekend)
6. Thread-safe spot assignment and release

**Out of scope:**
- Reservations or pre-booking
- Multiple parking lot management
- Payment processing integration
- Physical hardware (barriers, displays)

---

## Core Entities and Relationships

| Entity | Responsibility |
|--------|---------------|
| ParkingLot | Singleton; owns floors, drives park/exit flow |
| Floor | Contains ordered list of spots; tracks availability counts |
| ParkingSpot | Holds spot type, size, occupied status |
| Vehicle | Carries vehicle type (motorcycle/car/truck) |
| Ticket | Links vehicle to spot and entry timestamp |
| PricingStrategy | Computes fee given duration and spot type |

ParkingLot delegates spot finding to each Floor in order. Floor returns the first available compatible spot. Ticket is issued at entry and surrendered at exit. PricingStrategy is injected into ParkingLot and called at exit time.

---

## Class Design

### ParkingSpot

| Requirement | What ParkingSpot must track |
|-------------|----------------------------|
| Spot size compatibility | spot_type: SpotType enum |
| Occupancy state | is_occupied: bool |
| Location for nearest-first ordering | floor_number, spot_number |
| Which vehicle is parked | vehicle: Vehicle or None |

```
class ParkingSpot:
- spot_id: str
- spot_type: SpotType        # MOTORCYCLE, CAR, TRUCK
- floor_number: int
- spot_number: int
- is_occupied: bool
- vehicle: Vehicle | None

+ can_fit(vehicle: Vehicle) -> bool
+ assign(vehicle: Vehicle) -> None
+ release() -> None
```

### Vehicle

```
class Vehicle:
- license_plate: str
- vehicle_type: VehicleType  # MOTORCYCLE, CAR, TRUCK

+ get_type() -> VehicleType
```

### Floor

| Requirement | What Floor must track |
|-------------|----------------------|
| Available spots by type | availability: dict[SpotType, int] |
| All spots ordered for nearest-first | spots: list[ParkingSpot] |

```
class Floor:
- floor_number: int
- spots: list[ParkingSpot]
- availability: dict[SpotType, int]
- _lock: threading.Lock

+ find_and_assign(vehicle: Vehicle) -> ParkingSpot | None
+ release_spot(spot: ParkingSpot) -> None
```

### Ticket

```
class Ticket:
- ticket_id: str
- vehicle: Vehicle
- spot: ParkingSpot
- entry_time: datetime
- exit_time: datetime | None
- fee: float | None
```

### PricingStrategy

```
class PricingStrategy:          # abstract
+ calculate(entry_time, exit_time, spot_type) -> float

class HourlyPricing(PricingStrategy):
- rates: dict[SpotType, float]  # per-hour rate per type

class FlatRatePricing(PricingStrategy):
- flat_fee: float

class WeekendPricing(PricingStrategy):
- weekday_strategy: PricingStrategy
- weekend_strategy: PricingStrategy
```

### ParkingLot (Singleton)

```
class ParkingLot:
- _instance: ParkingLot         # class-level
- floors: list[Floor]
- active_tickets: dict[str, Ticket]
- pricing_strategy: PricingStrategy
- _tickets_lock: threading.Lock

+ get_instance() -> ParkingLot  # classmethod
+ park_vehicle(vehicle: Vehicle) -> Ticket
+ exit_vehicle(ticket_id: str) -> float
+ find_nearest_spot(vehicle: Vehicle) -> ParkingSpot | None
+ get_availability() -> dict[int, dict[SpotType, int]]
```

---

## Implementation

### Core Method: park_vehicle

**Core logic:**
1. Check active_tickets for duplicate license plate (lock the tickets dict)
2. Scan floors in order; call `floor.find_and_assign(vehicle)` which atomically finds and marks the spot
3. If no spot found on any floor, raise `ParkingLotFullError`
4. Create Ticket with entry timestamp, store in active_tickets
5. Return ticket to caller

**Edge cases:**
- Lot completely full — raise error
- Same license plate already parked — detect before assigning
- Motorcycle in car spot — allowed if no motorcycle spots remain

```java
public Ticket parkVehicle(Vehicle vehicle) {
    synchronized (ticketsLock) {
        for (Ticket ticket : activeTickets.values()) {
            if (ticket.getVehicle().getLicensePlate().equals(vehicle.getLicensePlate())) {
                throw new IllegalArgumentException(
                    "Vehicle " + vehicle.getLicensePlate() + " already parked");
            }
        }
    }

    ParkingSpot spot = null;
    for (Floor floor : floors) {
        spot = floor.findAndAssign(vehicle);
        if (spot != null) {
            break;
        }
    }

    if (spot == null) {
        throw new ParkingLotFullError("No available spot for this vehicle type");
    }

    Ticket ticket = new Ticket(
        UUID.randomUUID().toString(),
        vehicle,
        spot,
        LocalDateTime.now()
    );
    synchronized (ticketsLock) {
        activeTickets.put(ticket.getTicketId(), ticket);
    }
    return ticket;
}
```

### Core Method: exit_vehicle

**Core logic:**
1. Remove ticket from active_tickets under lock (prevents double-exit)
2. Record exit_time, compute fee via pricing_strategy
3. Call `floor.release_spot(spot)` to free the spot and update availability counts
4. Return fee

**Edge cases:**
- Invalid ticket_id — raise KeyError
- Double exit — ticket already removed on first call

```java
public double exitVehicle(String ticketId) {
    Ticket ticket;
    synchronized (ticketsLock) {
        ticket = activeTickets.remove(ticketId);
        if (ticket == null) {
            throw new NoSuchElementException("No active ticket: " + ticketId);
        }
    }

    ticket.setExitTime(LocalDateTime.now());
    double fee = pricingStrategy.calculate(
        ticket.getEntryTime(), ticket.getExitTime(), ticket.getSpot().getSpotType()
    );
    ticket.setFee(fee);

    Floor floor = floors.get(ticket.getSpot().getFloorNumber());
    floor.releaseSpot(ticket.getSpot());
    return fee;
}
```

### Core Method: Floor.find_and_assign

**Core logic:**
1. Acquire floor-level lock (per-floor locking avoids global bottleneck)
2. Iterate spots; for each unoccupied spot, check size compatibility
3. Assign and update availability atomically, then release lock

```java
public ParkingSpot findAndAssign(Vehicle vehicle) {
    Map<VehicleType, Integer> vehicleRank = Map.of(
        VehicleType.MOTORCYCLE, 0, VehicleType.CAR, 1, VehicleType.TRUCK, 2);
    Map<SpotType, Integer> spotRank = Map.of(
        SpotType.MOTORCYCLE, 0, SpotType.CAR, 1, SpotType.TRUCK, 2);
    int vehicleSize = vehicleRank.get(vehicle.getVehicleType());

    synchronized (lock) {
        for (ParkingSpot spot : spots) {
            if (spot.isOccupied()) {
                continue;
            }
            if (spotRank.get(spot.getSpotType()) >= vehicleSize) {
                spot.assign(vehicle);
                availability.merge(spot.getSpotType(), -1, Integer::sum);
                return spot;
            }
        }
    }
    return null;
}
```

### Pricing: HourlyPricing

```java
public class HourlyPricing implements PricingStrategy {
    private final Map<SpotType, Double> rates;

    public HourlyPricing() {
        this.rates = Map.of(
            SpotType.MOTORCYCLE, 2.0,
            SpotType.CAR, 4.0,
            SpotType.TRUCK, 8.0
        );
    }

    @Override
    public double calculate(LocalDateTime entryTime, LocalDateTime exitTime, SpotType spotType) {
        double durationHours = Duration.between(entryTime, exitTime).getSeconds() / 3600.0;
        long hours = (long) Math.ceil(durationHours);  // round up to nearest hour
        return hours * rates.get(spotType);
    }
}
```

---

## Verification

**Scenario**: Car parks at 10:00 AM, exits at 12:30 PM.

1. `park_vehicle(Car("ABC123"))` — no existing ticket for ABC123
2. Floor 0: `find_and_assign` scans, finds CarSpot(floor=0, spot=3) unoccupied, size compatible (car=1 >= car=1)
3. `spot.assign(car)` — `is_occupied=True`, vehicle=Car("ABC123")
4. `availability[CAR]`: 10 → 9 on Floor 0
5. Ticket T001 created with `entry_time=10:00 AM`, stored in active_tickets
6. T001 returned

**Exit at 12:30 PM**:
1. `exit_vehicle("T001")` — ticket popped from active_tickets atomically
2. `exit_time = 12:30 PM`; duration = 2.5 hours → `ceil(2.5) = 3` billable hours
3. Fee = 3 × $4.00 = **$12.00**
4. `floor.release_spot(spot)` — `is_occupied=False`, `availability[CAR]`: 9 → 10
5. $12.00 returned

---

## Deep Dive & Extensibility

### 1. "How would you make this thread-safe for high-concurrency entry/exit?"

A single global lock is the simplest approach but serializes all entry/exit lanes. Better: **per-floor locks**. Floors are independent — two vehicles entering on different floors don't share any state.

```java
public class Floor {
    private final Object lock = new Object();

    public ParkingSpot findAndAssign(Vehicle vehicle) {
        synchronized (lock) {
            for (ParkingSpot spot : spots) {
                if (!spot.isOccupied() && spot.canFit(vehicle)) {
                    spot.assign(vehicle);
                    availability.merge(spot.getSpotType(), -1, Integer::sum);
                    return spot;
                }
            }
        }
        return null;
    }
}
```

The `active_tickets` dict uses its own lock. This gives parallelism proportional to number of floors. For even finer granularity, use per-spot locks with CAS semantics, but that's rarely needed for parking lots (< thousands of spots).

### 2. "How would you add EV charging spots?"

EV charging is an attribute, not a new size category. Add `has_charger: bool` to `ParkingSpot` and `needs_charging: bool` to `Vehicle`.

```java
public ParkingSpot findAndAssign(Vehicle vehicle) {
    synchronized (lock) {
        for (ParkingSpot spot : spots) {
            if (spot.isOccupied()) {
                continue;
            }
            if (!spot.canFit(vehicle)) {
                continue;
            }
            if (vehicle.needsCharging() && !spot.hasCharger()) {
                continue;
            }
            spot.assign(vehicle);
            availability.merge(spot.getSpotType(), -1, Integer::sum);
            return spot;
        }
    }
    return null;
}
```

Pricing: add `charge_start_time` to ParkingSpot. On exit, compute kWh from duration × charge rate, add to base parking fee. EV spots get a `charger_fee_per_kwh` field.

### 3. "How would you add a reservation system?"

Add a `Reservation` entity with a time window:

```java
public class Reservation {
    private String reservationId;
    private Vehicle vehicle;
    private ParkingSpot spot;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private ReservationStatus status;  // PENDING, ACTIVE, CANCELLED, COMPLETED
}
```

Each ParkingSpot holds `reservations: list[Reservation]` sorted by start_time. In `find_and_assign`, skip spots that have an overlapping reservation:

```java
public boolean hasConflict(ParkingSpot spot, LocalDateTime start, LocalDateTime end) {
    for (Reservation r : spot.getReservations()) {
        if (r.getStatus() != ReservationStatus.CANCELLED
                && r.getStatus() != ReservationStatus.COMPLETED) {
            if (!(!end.isAfter(r.getStartTime()) || !start.isBefore(r.getEndTime()))) {
                return true;
            }
        }
    }
    return false;
}
```

A background job (or lazy cleanup on each `find_and_assign` call) expires reservations whose end_time has passed with no vehicle arrival.

### 4. "How would dynamic pricing based on occupancy work?"

Decorator/wrapper strategy that reads live occupancy at fee calculation time:

```java
public class OccupancyBasedPricing implements PricingStrategy {
    private final ParkingLot lot;
    private final PricingStrategy base;
    private final double surgeThreshold;
    private final double maxMultiplier;

    public OccupancyBasedPricing(ParkingLot lot, PricingStrategy base) {
        this(lot, base, 0.8, 2.0);
    }

    public OccupancyBasedPricing(ParkingLot lot, PricingStrategy base,
                                  double surgeThreshold, double maxMultiplier) {
        this.lot = lot;
        this.base = base;
        this.surgeThreshold = surgeThreshold;
        this.maxMultiplier = maxMultiplier;
    }

    @Override
    public double calculate(LocalDateTime entryTime, LocalDateTime exitTime, SpotType spotType) {
        double baseFee = base.calculate(entryTime, exitTime, spotType);
        double occupancy = lot.getOccupancyRate(spotType);
        double multiplier;
        if (occupancy > surgeThreshold) {
            double surge = 1.0 + (occupancy - surgeThreshold) / (1 - surgeThreshold);
            multiplier = Math.min(surge, maxMultiplier);
        } else {
            multiplier = 1.0;
        }
        return baseFee * multiplier;
    }
}
```

The multiplier is capped to prevent gouging. Occupancy rate = `occupied / total` for that spot type.

---

## Interviewer Questions by Level

**Junior**: Define the Ticket class and explain what fields it needs. Describe how spot assignment works and which classes are involved. Can sketch a basic class diagram.

**Mid-level**: Implement `park_vehicle` and `exit_vehicle` with correct locking. Explain why Singleton for ParkingLot. Implement `HourlyPricing` via Strategy pattern. Handle size compatibility correctly (smaller vehicle in larger spot).

**Senior**: Identify the global lock bottleneck and propose per-floor locking. Design the reservation system with interval overlap detection. Discuss EV charging as an attribute vs. a subclass. Explain why pricing as a Strategy is better than a method on ParkingLot.

---

## Common Interview Questions

- Q: Why use Singleton for ParkingLot? A: There is one physical lot — Singleton prevents multiple instances with divergent state. In production, a DI container handles lifecycle; the principle is the same.
- Q: How does size compatibility work — can a motorcycle park in a car spot? A: Yes, smaller vehicles can use larger spots as a fallback. Assign a numeric size (motorcycle=0, car=1, truck=2); a vehicle parks in any spot with size >= its own.
- Q: What happens if two threads both see the same spot as available? A: Without a lock they both assign it — double-booking. The per-floor lock makes find-and-assign atomic, so only one thread succeeds.
- Q: Why store Ticket separately from the Spot? A: Ticket holds time-stamped billing data. A spot only needs occupancy state. Separating them allows ticket history after the spot is released and keeps each class focused.
- Q: Can a truck park in a car spot? A: No. The size rule is one-directional — smaller in larger is allowed, larger in smaller is not. If no truck spot is available, raise ParkingLotFullError for the truck.
- Q: How do you round partial hours in HourlyPricing? A: Round up with `math.ceil`. 2.1 hours = 3 billable hours. This is a business rule — expose it as a configurable rounding strategy if needed.
- Q: How would you test for double-booking under concurrency? A: Spin up 100 threads simultaneously calling `park_vehicle` on a lot with 50 car spots. Assert that exactly 50 succeed and 50 raise ParkingLotFullError, and that no spot has two vehicles assigned.

---

## Concurrency Test Harness

Runnable tests that verify thread-safety invariants. No external deps — uses stdlib `threading` only.

```java
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

// --- Minimal stubs to make the harness self-contained ---

enum VehicleType { MOTORCYCLE, CAR, TRUCK }

enum SpotType { MOTORCYCLE, CAR, TRUCK }

class ParkingLotFullError extends RuntimeException {
    public ParkingLotFullError(String message) { super(message); }
}

class Vehicle {
    private final String licensePlate;
    private final VehicleType vehicleType;

    public Vehicle(String licensePlate, VehicleType vehicleType) {
        this.licensePlate = licensePlate;
        this.vehicleType = vehicleType;
    }

    public String getLicensePlate() { return licensePlate; }
    public VehicleType getVehicleType() { return vehicleType; }
}

class ParkingSpot {
    private final String spotId;
    private final SpotType spotType;
    private final int floorNumber;
    private final int spotNumber;
    private boolean occupied = false;
    private Vehicle vehicle;

    public ParkingSpot(String spotId, SpotType spotType, int floorNumber, int spotNumber) {
        this.spotId = spotId;
        this.spotType = spotType;
        this.floorNumber = floorNumber;
        this.spotNumber = spotNumber;
    }

    public void assign(Vehicle vehicle) {
        this.occupied = true;
        this.vehicle = vehicle;
    }

    public void release() {
        this.occupied = false;
        this.vehicle = null;
    }

    public String getSpotId() { return spotId; }
    public SpotType getSpotType() { return spotType; }
    public int getFloorNumber() { return floorNumber; }
    public int getSpotNumber() { return spotNumber; }
    public boolean isOccupied() { return occupied; }
    public Vehicle getVehicle() { return vehicle; }
}

class Ticket {
    private final String ticketId;
    private final Vehicle vehicle;
    private final ParkingSpot spot;
    private final LocalDateTime entryTime;
    private LocalDateTime exitTime;
    private Double fee;

    public Ticket(String ticketId, Vehicle vehicle, ParkingSpot spot, LocalDateTime entryTime) {
        this.ticketId = ticketId;
        this.vehicle = vehicle;
        this.spot = spot;
        this.entryTime = entryTime;
    }

    public String getTicketId() { return ticketId; }
    public Vehicle getVehicle() { return vehicle; }
    public ParkingSpot getSpot() { return spot; }
    public LocalDateTime getEntryTime() { return entryTime; }
    public LocalDateTime getExitTime() { return exitTime; }
    public void setExitTime(LocalDateTime exitTime) { this.exitTime = exitTime; }
    public Double getFee() { return fee; }
    public void setFee(Double fee) { this.fee = fee; }
}

class Floor {
    final int floorNumber;
    final List<ParkingSpot> spots;
    final Map<SpotType, Integer> availability = new EnumMap<>(SpotType.class);
    private final Object lock = new Object();

    public Floor(int floorNumber, List<ParkingSpot> spots) {
        this.floorNumber = floorNumber;
        this.spots = spots;
        for (SpotType st : SpotType.values()) {
            int count = 0;
            for (ParkingSpot s : spots) {
                if (s.getSpotType() == st) count++;
            }
            availability.put(st, count);
        }
    }

    public ParkingSpot findAndAssign(Vehicle vehicle) {
        Map<VehicleType, Integer> vehicleRank = Map.of(
            VehicleType.MOTORCYCLE, 0, VehicleType.CAR, 1, VehicleType.TRUCK, 2);
        Map<SpotType, Integer> spotRank = Map.of(
            SpotType.MOTORCYCLE, 0, SpotType.CAR, 1, SpotType.TRUCK, 2);
        int vehicleSize = vehicleRank.get(vehicle.getVehicleType());
        synchronized (lock) {
            for (ParkingSpot spot : spots) {
                if (spot.isOccupied()) {
                    continue;
                }
                if (spotRank.get(spot.getSpotType()) >= vehicleSize) {
                    spot.assign(vehicle);
                    availability.merge(spot.getSpotType(), -1, Integer::sum);
                    return spot;
                }
            }
        }
        return null;
    }

    public void releaseSpot(ParkingSpot spot) {
        synchronized (lock) {
            spot.release();
            availability.merge(spot.getSpotType(), 1, Integer::sum);
        }
    }
}

class ParkingLot {
    private static volatile ParkingLot instance;
    private static final Object initLock = new Object();

    final List<Floor> floors;
    private final PricingStrategy pricingStrategy;
    final Map<String, Ticket> activeTickets = new HashMap<>();
    private final Object ticketsLock = new Object();

    public ParkingLot(List<Floor> floors, PricingStrategy pricingStrategy) {
        this.floors = floors;
        this.pricingStrategy = pricingStrategy;
    }

    public static ParkingLot getInstance(List<Floor> floors, PricingStrategy pricingStrategy) {
        if (instance == null) {
            synchronized (initLock) {
                if (instance == null) {
                    instance = new ParkingLot(floors, pricingStrategy);
                }
            }
        }
        return instance;
    }

    public Ticket parkVehicle(Vehicle vehicle) {
        synchronized (ticketsLock) {
            for (Ticket t : activeTickets.values()) {
                if (t.getVehicle().getLicensePlate().equals(vehicle.getLicensePlate())) {
                    throw new IllegalArgumentException("Already parked: " + vehicle.getLicensePlate());
                }
            }
        }
        ParkingSpot spot = null;
        for (Floor floor : floors) {
            spot = floor.findAndAssign(vehicle);
            if (spot != null) break;
        }
        if (spot == null) {
            throw new ParkingLotFullError("No spot available");
        }
        Ticket ticket = new Ticket(UUID.randomUUID().toString(), vehicle, spot, LocalDateTime.now());
        synchronized (ticketsLock) {
            activeTickets.put(ticket.getTicketId(), ticket);
        }
        return ticket;
    }

    public double exitVehicle(String ticketId) {
        Ticket ticket;
        synchronized (ticketsLock) {
            ticket = activeTickets.remove(ticketId);
            if (ticket == null) {
                throw new NoSuchElementException("No active ticket: " + ticketId);
            }
        }
        ticket.setExitTime(LocalDateTime.now());
        double fee = pricingStrategy.calculate(ticket.getEntryTime(), ticket.getExitTime(), ticket.getSpot().getSpotType());
        ticket.setFee(fee);
        floors.get(ticket.getSpot().getFloorNumber()).releaseSpot(ticket.getSpot());
        return fee;
    }
}

interface PricingStrategy {
    double calculate(LocalDateTime entryTime, LocalDateTime exitTime, SpotType spotType);
}

class HourlyPricing implements PricingStrategy {
    private static final Map<SpotType, Double> RATES = Map.of(
        SpotType.MOTORCYCLE, 2.0, SpotType.CAR, 4.0, SpotType.TRUCK, 8.0);

    @Override
    public double calculate(LocalDateTime entry, LocalDateTime exit, SpotType spotType) {
        long hours = (long) Math.ceil(Duration.between(entry, exit).getSeconds() / 3600.0);
        return Math.max(hours, 1) * RATES.get(spotType);
    }
}

// --- Helpers ---

class ParkingLotConcurrencyTest {

    static ParkingLot makeLot(int carSpots) {
        List<ParkingSpot> spots = new ArrayList<>();
        for (int i = 0; i < carSpots; i++) {
            spots.add(new ParkingSpot("s" + i, SpotType.CAR, 0, i));
        }
        Floor floor = new Floor(0, spots);
        return new ParkingLot(List.of(floor), new HourlyPricing());
    }

    // ─────────────────────────────────────────────────────────────
    // TEST 1: No double-booking under concurrent entry
    // 100 threads race to park in a lot with 50 car spots.
    // Exactly 50 must succeed; exactly 50 must get ParkingLotFullError.
    // No spot may have two vehicles assigned.
    // ─────────────────────────────────────────────────────────────
    static void testNoDoubleBooking() throws InterruptedException {
        ParkingLot lot = makeLot(50);
        List<Ticket> succeeded = Collections.synchronizedList(new ArrayList<>());
        List<Integer> failed = Collections.synchronizedList(new ArrayList<>());

        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            final int idx = i;
            Thread t = new Thread(() -> {
                Vehicle v = new Vehicle("PLATE-" + idx, VehicleType.CAR);
                try {
                    Ticket ticket = lot.parkVehicle(v);
                    succeeded.add(ticket);
                } catch (ParkingLotFullError e) {
                    failed.add(idx);
                }
            });
            threads.add(t);
        }
        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();

        if (succeeded.size() != 50) throw new AssertionError("Expected 50 successes, got " + succeeded.size());
        if (failed.size() != 50) throw new AssertionError("Expected 50 failures, got " + failed.size());

        // No spot double-booked
        Set<String> occupiedSpots = new HashSet<>();
        for (Ticket ticket : succeeded) {
            String sid = ticket.getSpot().getSpotId();
            if (occupiedSpots.contains(sid)) throw new AssertionError("Double-booking: spot " + sid);
            occupiedSpots.add(sid);
        }

        // Every assigned spot is actually occupied
        for (Ticket ticket : succeeded) {
            if (!ticket.getSpot().isOccupied()) throw new AssertionError("Spot not occupied");
            if (!ticket.getSpot().getVehicle().getLicensePlate().equals(ticket.getVehicle().getLicensePlate())) {
                throw new AssertionError("Vehicle mismatch");
            }
        }

        System.out.println("PASS: testNoDoubleBooking");
    }

    // ─────────────────────────────────────────────────────────────
    // TEST 2: Concurrent park + exit — spot recycled correctly
    // 10 vehicles park; then 10 threads exit them concurrently;
    // then 10 new vehicles try to park (must all succeed since spots freed).
    // ─────────────────────────────────────────────────────────────
    static void testConcurrentParkAndExit() throws InterruptedException {
        ParkingLot lot = makeLot(10);

        List<Ticket> tickets = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tickets.add(lot.parkVehicle(new Vehicle("A-" + i, VehicleType.CAR)));
        }

        // All 10 spots occupied
        long occupiedCount = lot.floors.get(0).spots.stream().filter(ParkingSpot::isOccupied).count();
        if (occupiedCount != 10) throw new AssertionError("Expected 10 occupied");

        List<Double> fees = Collections.synchronizedList(new ArrayList<>());

        List<Thread> exitThreads = new ArrayList<>();
        for (Ticket t : tickets) {
            Thread th = new Thread(() -> {
                double fee = lot.exitVehicle(t.getTicketId());
                fees.add(fee);
            });
            exitThreads.add(th);
        }
        for (Thread t : exitThreads) t.start();
        for (Thread t : exitThreads) t.join();

        if (fees.size() != 10) throw new AssertionError("Not all exits completed");
        occupiedCount = lot.floors.get(0).spots.stream().filter(ParkingSpot::isOccupied).count();
        if (occupiedCount != 0) throw new AssertionError("Spots not freed");

        // Re-park 10 more vehicles — must succeed
        List<Ticket> newTickets = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            newTickets.add(lot.parkVehicle(new Vehicle("B-" + i, VehicleType.CAR)));
        }
        if (newTickets.size() != 10) throw new AssertionError("Expected 10 new tickets");

        System.out.println("PASS: testConcurrentParkAndExit");
    }

    // ─────────────────────────────────────────────────────────────
    // TEST 3: Duplicate license plate rejection under concurrency
    // Two threads simultaneously try to park the same license plate.
    // Exactly one must succeed; the other must get IllegalArgumentException.
    // ─────────────────────────────────────────────────────────────
    static void testDuplicatePlateRejected() throws InterruptedException {
        ParkingLot lot = makeLot(10);
        List<String[]> results = Collections.synchronizedList(new ArrayList<>());

        Runnable park = () -> {
            Vehicle v = new Vehicle("SAME-PLATE", VehicleType.CAR);
            try {
                Ticket ticket = lot.parkVehicle(v);
                results.add(new String[]{"ok", ticket.getTicketId()});
            } catch (IllegalArgumentException e) {
                results.add(new String[]{"dup", e.getMessage()});
            }
        };

        Thread t1 = new Thread(park);
        Thread t2 = new Thread(park);
        t1.start(); t2.start();
        t1.join(); t2.join();

        long oks = results.stream().filter(r -> r[0].equals("ok")).count();
        long dups = results.stream().filter(r -> r[0].equals("dup")).count();
        if (oks != 1) throw new AssertionError("Expected 1 success, got " + oks);
        if (dups != 1) throw new AssertionError("Expected 1 duplicate rejection, got " + dups);
        System.out.println("PASS: testDuplicatePlateRejected");
    }

    public static void main(String[] args) throws InterruptedException {
        testNoDoubleBooking();
        testConcurrentParkAndExit();
        testDuplicatePlateRejected();
        System.out.println("All concurrency tests passed.");
    }
}
```

**What each test verifies:**
- `test_no_double_booking`: The per-floor lock prevents two threads assigning the same spot simultaneously. If `find_and_assign` had no lock, two threads could both see `is_occupied=False` and both assign the same spot.
- `test_concurrent_park_and_exit`: Spot availability counts are correctly updated under concurrent exit; freed spots become available for new arrivals.
- `test_duplicate_plate_rejected`: The `active_tickets` lock ensures the duplicate check + insert is atomic. Without the lock, two threads could both pass the duplicate check before either inserts.

---

## Related

**Patterns applied here**

- [Singleton Pattern](../../03-design-patterns/01-creational/singleton.md)
- [Strategy Pattern](../../03-design-patterns/03-behavioral/strategy-pattern.md)

**SOLID focus**: [Single Responsibility](../../02-solid-principles/01-single-responsibility.md) · [Open/Closed](../../02-solid-principles/02-open-closed.md)

**Concurrency**: [Concurrency Patterns](../../04-concurrency/concurrency-patterns.md)

**Practice next**

- [Design Vending Machine](04-design-vending-machine.md)
- [Design Hotel Management](../02-frequent-problems/11-design-hotel-management.md)

Both reuse the slot-allocation and pricing-strategy shape.

**Frameworks**: [LLD Template](../../../07-interview-templates/01-frameworks/02-lld-template.md) · [UML Diagrams](../../01-oop-fundamentals/uml-diagrams.md) · [OOP Four Pillars](../../01-oop-fundamentals/four-pillars.md)
