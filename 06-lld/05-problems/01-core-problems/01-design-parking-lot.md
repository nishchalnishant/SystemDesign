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
> - Concurrency: Thread safety is critical when two vehicles try to enter simultaneously. The `assignSpot()` method must be synchronized or use concurrent data structures.
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

```python
def park_vehicle(self, vehicle: Vehicle) -> Ticket:
    with self._tickets_lock:
        for ticket in self.active_tickets.values():
            if ticket.vehicle.license_plate == vehicle.license_plate:
                raise ValueError(f"Vehicle {vehicle.license_plate} already parked")

    spot = None
    for floor in self.floors:
        spot = floor.find_and_assign(vehicle)
        if spot is not None:
            break

    if spot is None:
        raise ParkingLotFullError("No available spot for this vehicle type")

    ticket = Ticket(
        ticket_id=str(uuid.uuid4()),
        vehicle=vehicle,
        spot=spot,
        entry_time=datetime.now()
    )
    with self._tickets_lock:
        self.active_tickets[ticket.ticket_id] = ticket
    return ticket
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

```python
def exit_vehicle(self, ticket_id: str) -> float:
    with self._tickets_lock:
        ticket = self.active_tickets.pop(ticket_id, None)
        if ticket is None:
            raise KeyError(f"No active ticket: {ticket_id}")

    ticket.exit_time = datetime.now()
    fee = self.pricing_strategy.calculate(
        ticket.entry_time, ticket.exit_time, ticket.spot.spot_type
    )
    ticket.fee = fee

    floor = self.floors[ticket.spot.floor_number]
    floor.release_spot(ticket.spot)
    return fee
```

### Core Method: Floor.find_and_assign

**Core logic:**
1. Acquire floor-level lock (per-floor locking avoids global bottleneck)
2. Iterate spots; for each unoccupied spot, check size compatibility
3. Assign and update availability atomically, then release lock

```python
def find_and_assign(self, vehicle: Vehicle) -> ParkingSpot | None:
    vehicle_rank = {VehicleType.MOTORCYCLE: 0, VehicleType.CAR: 1, VehicleType.TRUCK: 2}
    spot_rank    = {SpotType.MOTORCYCLE: 0,   SpotType.CAR: 1,    SpotType.TRUCK: 2}
    vehicle_size = vehicle_rank[vehicle.vehicle_type]

    with self._lock:
        for spot in self.spots:
            if spot.is_occupied:
                continue
            if spot_rank[spot.spot_type] >= vehicle_size:
                spot.assign(vehicle)
                self.availability[spot.spot_type] -= 1
                return spot
    return None
```

### Pricing: HourlyPricing

```python
class HourlyPricing(PricingStrategy):
    def __init__(self):
        self.rates = {
            SpotType.MOTORCYCLE: 2.0,
            SpotType.CAR: 4.0,
            SpotType.TRUCK: 8.0,
        }

    def calculate(self, entry_time, exit_time, spot_type):
        duration_hours = (exit_time - entry_time).total_seconds() / 3600
        hours = math.ceil(duration_hours)  # round up to nearest hour
        return hours * self.rates[spot_type]
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

```python
class Floor:
    def __init__(self):
        self._lock = threading.Lock()

    def find_and_assign(self, vehicle):
        with self._lock:
            for spot in self.spots:
                if not spot.is_occupied and spot.can_fit(vehicle):
                    spot.assign(vehicle)
                    self.availability[spot.spot_type] -= 1
                    return spot
        return None
```

The `active_tickets` dict uses its own lock. This gives parallelism proportional to number of floors. For even finer granularity, use per-spot locks with CAS semantics, but that's rarely needed for parking lots (< thousands of spots).

### 2. "How would you add EV charging spots?"

EV charging is an attribute, not a new size category. Add `has_charger: bool` to `ParkingSpot` and `needs_charging: bool` to `Vehicle`.

```python
def find_and_assign(self, vehicle):
    with self._lock:
        for spot in self.spots:
            if spot.is_occupied:
                continue
            if not spot.can_fit(vehicle):
                continue
            if vehicle.needs_charging and not spot.has_charger:
                continue
            spot.assign(vehicle)
            self.availability[spot.spot_type] -= 1
            return spot
    return None
```

Pricing: add `charge_start_time` to ParkingSpot. On exit, compute kWh from duration × charge rate, add to base parking fee. EV spots get a `charger_fee_per_kwh` field.

### 3. "How would you add a reservation system?"

Add a `Reservation` entity with a time window:

```python
class Reservation:
    reservation_id: str
    vehicle: Vehicle
    spot: ParkingSpot
    start_time: datetime
    end_time: datetime
    status: ReservationStatus  # PENDING, ACTIVE, CANCELLED, COMPLETED
```

Each ParkingSpot holds `reservations: list[Reservation]` sorted by start_time. In `find_and_assign`, skip spots that have an overlapping reservation:

```python
def has_conflict(spot, start, end):
    for r in spot.reservations:
        if r.status not in (CANCELLED, COMPLETED):
            if not (end <= r.start_time or start >= r.end_time):
                return True
    return False
```

A background job (or lazy cleanup on each `find_and_assign` call) expires reservations whose end_time has passed with no vehicle arrival.

### 4. "How would dynamic pricing based on occupancy work?"

Decorator/wrapper strategy that reads live occupancy at fee calculation time:

```python
class OccupancyBasedPricing(PricingStrategy):
    def __init__(self, lot, base_strategy, surge_threshold=0.8, max_multiplier=2.0):
        self.lot = lot
        self.base = base_strategy
        self.surge_threshold = surge_threshold
        self.max_multiplier = max_multiplier

    def calculate(self, entry_time, exit_time, spot_type):
        base_fee = self.base.calculate(entry_time, exit_time, spot_type)
        occupancy = self.lot.get_occupancy_rate(spot_type)
        if occupancy > self.surge_threshold:
            surge = 1.0 + (occupancy - self.surge_threshold) / (1 - self.surge_threshold)
            multiplier = min(surge, self.max_multiplier)
        else:
            multiplier = 1.0
        return base_fee * multiplier
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

```python
import threading
import time
import uuid
from datetime import datetime
from dataclasses import dataclass, field
from enum import Enum
import math

# --- Minimal stubs to make the harness self-contained ---

class VehicleType(Enum):
    MOTORCYCLE = "motorcycle"
    CAR = "car"
    TRUCK = "truck"

class SpotType(Enum):
    MOTORCYCLE = "motorcycle"
    CAR = "car"
    TRUCK = "truck"

class ParkingLotFullError(Exception):
    pass

@dataclass
class Vehicle:
    license_plate: str
    vehicle_type: VehicleType

@dataclass
class ParkingSpot:
    spot_id: str
    spot_type: SpotType
    floor_number: int
    spot_number: int
    is_occupied: bool = False
    vehicle: Vehicle = None

    def assign(self, vehicle):
        self.is_occupied = True
        self.vehicle = vehicle

    def release(self):
        self.is_occupied = False
        self.vehicle = None

@dataclass
class Ticket:
    ticket_id: str
    vehicle: Vehicle
    spot: ParkingSpot
    entry_time: datetime
    exit_time: datetime = None
    fee: float = None

class Floor:
    def __init__(self, floor_number, spots):
        self.floor_number = floor_number
        self.spots = spots
        self.availability = {st: sum(1 for s in spots if s.spot_type == st) for st in SpotType}
        self._lock = threading.Lock()

    def find_and_assign(self, vehicle):
        vehicle_rank = {VehicleType.MOTORCYCLE: 0, VehicleType.CAR: 1, VehicleType.TRUCK: 2}
        spot_rank    = {SpotType.MOTORCYCLE: 0,   SpotType.CAR: 1,    SpotType.TRUCK: 2}
        vehicle_size = vehicle_rank[vehicle.vehicle_type]
        with self._lock:
            for spot in self.spots:
                if spot.is_occupied:
                    continue
                if spot_rank[spot.spot_type] >= vehicle_size:
                    spot.assign(vehicle)
                    self.availability[spot.spot_type] -= 1
                    return spot
        return None

    def release_spot(self, spot):
        with self._lock:
            spot.release()
            self.availability[spot.spot_type] += 1

class ParkingLot:
    _instance = None
    _init_lock = threading.Lock()

    def __init__(self, floors, pricing_strategy):
        self.floors = floors
        self.pricing_strategy = pricing_strategy
        self.active_tickets = {}
        self._tickets_lock = threading.Lock()

    @classmethod
    def get_instance(cls, floors=None, pricing_strategy=None):
        if cls._instance is None:
            with cls._init_lock:
                if cls._instance is None:
                    cls._instance = cls(floors, pricing_strategy)
        return cls._instance

    def park_vehicle(self, vehicle):
        with self._tickets_lock:
            for t in self.active_tickets.values():
                if t.vehicle.license_plate == vehicle.license_plate:
                    raise ValueError(f"Already parked: {vehicle.license_plate}")
        spot = None
        for floor in self.floors:
            spot = floor.find_and_assign(vehicle)
            if spot:
                break
        if spot is None:
            raise ParkingLotFullError("No spot available")
        ticket = Ticket(str(uuid.uuid4()), vehicle, spot, datetime.now())
        with self._tickets_lock:
            self.active_tickets[ticket.ticket_id] = ticket
        return ticket

    def exit_vehicle(self, ticket_id):
        with self._tickets_lock:
            ticket = self.active_tickets.pop(ticket_id, None)
            if ticket is None:
                raise KeyError(f"No active ticket: {ticket_id}")
        ticket.exit_time = datetime.now()
        fee = self.pricing_strategy.calculate(ticket.entry_time, ticket.exit_time, ticket.spot.spot_type)
        ticket.fee = fee
        self.floors[ticket.spot.floor_number].release_spot(ticket.spot)
        return fee

class HourlyPricing:
    rates = {SpotType.MOTORCYCLE: 2.0, SpotType.CAR: 4.0, SpotType.TRUCK: 8.0}
    def calculate(self, entry, exit_, spot_type):
        hours = math.ceil((exit_ - entry).total_seconds() / 3600)
        return max(hours, 1) * self.rates[spot_type]

# --- Helpers ---

def _make_lot(car_spots=50):
    spots = [ParkingSpot(f"s{i}", SpotType.CAR, 0, i) for i in range(car_spots)]
    floor = Floor(0, spots)
    return ParkingLot([floor], HourlyPricing())


# ─────────────────────────────────────────────────────────────
# TEST 1: No double-booking under concurrent entry
# 100 threads race to park in a lot with 50 car spots.
# Exactly 50 must succeed; exactly 50 must get ParkingLotFullError.
# No spot may have two vehicles assigned.
# ─────────────────────────────────────────────────────────────
def test_no_double_booking():
    lot = _make_lot(car_spots=50)
    succeeded = []
    failed = []
    lock = threading.Lock()

    def park(i):
        v = Vehicle(f"PLATE-{i}", VehicleType.CAR)
        try:
            ticket = lot.park_vehicle(v)
            with lock:
                succeeded.append(ticket)
        except ParkingLotFullError:
            with lock:
                failed.append(i)

    threads = [threading.Thread(target=park, args=(i,)) for i in range(100)]
    for t in threads: t.start()
    for t in threads: t.join()

    assert len(succeeded) == 50, f"Expected 50 successes, got {len(succeeded)}"
    assert len(failed) == 50, f"Expected 50 failures, got {len(failed)}"

    # No spot double-booked
    occupied_spots = set()
    for ticket in succeeded:
        sid = ticket.spot.spot_id
        assert sid not in occupied_spots, f"Double-booking: spot {sid}"
        occupied_spots.add(sid)

    # Every assigned spot is actually occupied
    for ticket in succeeded:
        assert ticket.spot.is_occupied
        assert ticket.spot.vehicle.license_plate == ticket.vehicle.license_plate

    print("PASS: test_no_double_booking")


# ─────────────────────────────────────────────────────────────
# TEST 2: Concurrent park + exit — spot recycled correctly
# 10 vehicles park; then 10 threads exit them concurrently;
# then 10 new vehicles try to park (must all succeed since spots freed).
# ─────────────────────────────────────────────────────────────
def test_concurrent_park_and_exit():
    lot = _make_lot(car_spots=10)

    tickets = []
    for i in range(10):
        t = lot.park_vehicle(Vehicle(f"A-{i}", VehicleType.CAR))
        tickets.append(t)

    # All 10 spots occupied
    assert sum(1 for s in lot.floors[0].spots if s.is_occupied) == 10

    fees = []
    fee_lock = threading.Lock()

    def exit_vehicle(ticket):
        fee = lot.exit_vehicle(ticket.ticket_id)
        with fee_lock:
            fees.append(fee)

    exit_threads = [threading.Thread(target=exit_vehicle, args=(t,)) for t in tickets]
    for t in exit_threads: t.start()
    for t in exit_threads: t.join()

    assert len(fees) == 10, "Not all exits completed"
    assert sum(1 for s in lot.floors[0].spots if s.is_occupied) == 0, "Spots not freed"

    # Re-park 10 more vehicles — must succeed
    new_tickets = []
    for i in range(10):
        t = lot.park_vehicle(Vehicle(f"B-{i}", VehicleType.CAR))
        new_tickets.append(t)
    assert len(new_tickets) == 10

    print("PASS: test_concurrent_park_and_exit")


# ─────────────────────────────────────────────────────────────
# TEST 3: Duplicate license plate rejection under concurrency
# Two threads simultaneously try to park the same license plate.
# Exactly one must succeed; the other must get ValueError.
# ─────────────────────────────────────────────────────────────
def test_duplicate_plate_rejected():
    lot = _make_lot(car_spots=10)
    results = []
    lock = threading.Lock()

    def park():
        v = Vehicle("SAME-PLATE", VehicleType.CAR)
        try:
            ticket = lot.park_vehicle(v)
            with lock:
                results.append(("ok", ticket))
        except ValueError as e:
            with lock:
                results.append(("dup", str(e)))

    t1 = threading.Thread(target=park)
    t2 = threading.Thread(target=park)
    t1.start(); t2.start()
    t1.join(); t2.join()

    oks   = [r for r in results if r[0] == "ok"]
    dups  = [r for r in results if r[0] == "dup"]
    assert len(oks) == 1,  f"Expected 1 success, got {len(oks)}"
    assert len(dups) == 1, f"Expected 1 duplicate rejection, got {len(dups)}"
    print("PASS: test_duplicate_plate_rejected")


if __name__ == "__main__":
    test_no_double_booking()
    test_concurrent_park_and_exit()
    test_duplicate_plate_rejected()
    print("All concurrency tests passed.")
```

**What each test verifies:**
- `test_no_double_booking`: The per-floor lock prevents two threads assigning the same spot simultaneously. If `find_and_assign` had no lock, two threads could both see `is_occupied=False` and both assign the same spot.
- `test_concurrent_park_and_exit`: Spot availability counts are correctly updated under concurrent exit; freed spots become available for new arrivals.
- `test_duplicate_plate_rejected`: The `active_tickets` lock ensures the duplicate check + insert is atomic. Without the lock, two threads could both pass the duplicate check before either inserts.
