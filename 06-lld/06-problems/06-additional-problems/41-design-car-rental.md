> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design a Car Rental System — a multi-branch reservation problem that tests date-range availability checking, atomic hold-and-confirm booking, and late/damage fee computation.
>
> **Key concepts:**
> - Core Entities: `Vehicle`, `Branch`, `Reservation`, `Customer`, `RentalAgreement`, `PricingStrategy`.
> - Date-based Inventory: Same shape as hotel booking — a vehicle is available for a date range only if no existing reservation for that vehicle overlaps `[startDate, endDate)`. The overlap check is `existing.start < requestedEnd AND existing.end > requestedStart`.
> - Multi-branch: Vehicles belong to a home branch; search filters by branch + category before running the availability check.
> - Patterns:
>   - Strategy: pluggable pricing (daily rate by category, surge/seasonal pricing).
>   - Factory: creating vehicle instances by category (Economy/SUV/Luxury).
> - Concurrency: Two customers can race to reserve the last SUV for overlapping dates. `createReservation` must atomically check-and-hold — a per-vehicle lock (or DB row lock / unique constraint) guards the check + insert.
>
> **Key takeaway:** This is fundamentally an interval-scheduling problem wearing a car-rental costume. Nail the overlap formula and the atomic check-and-hold, then layer pricing, late fees, and one-way rentals on top.

---
module: 06-lld
topic: Problems
status: unread
tags: [06-lld, lld, car-rental, strategy, factory, concurrency]
---
# Design a Car Rental System

> **Difficulty**: Medium  
> **Asked at**: Uber, Enterprise, Turo, Amazon  
> **Key Patterns**: Strategy (pricing), Factory (vehicle creation), interval-overlap scheduling

---

## Understanding the Problem

Design a car rental system operating across multiple branches. Customers search for available vehicles by branch, category, and date range; reserve a vehicle in advance; pick it up (starting a rental agreement); and return it (which computes any late or damage fees). The system must never double-book the same physical vehicle for overlapping date ranges.

---

## Clarifying Questions

**You**: "Is this a single rental location or a multi-branch operation?"  
**Interviewer**: "Multi-branch. Assume dozens of branches, each with its own fleet."

**You**: "Do customers reserve ahead of time with a date range, or is this walk-up/rent-now only?"  
**Interviewer**: "Both, but model it as a date-range reservation in all cases — a walk-up rental is just a reservation whose start date is now. That keeps one code path for availability."

**You**: "What vehicle categories do we need?"  
**Interviewer**: "Economy, SUV, and Luxury to start. Each category has a different daily rate. Make it easy to add more categories later."

**You**: "How should late returns and damage be handled?"  
**Interviewer**: "If the car comes back after the agreed end date, charge a late fee per extra day. If the inspector flags damage at return, add a flat or itemized damage fee. Both should be computed at return time and added to the final bill."

**You**: "What's the cancellation policy?"  
**Interviewer**: "Customers can cancel a reservation before pickup. Refund logic can be simplified — assume free cancellation more than 24 hours before pickup, no refund inside 24 hours. Cover the details in a deep dive if asked."

**You**: "Should a vehicle only be searchable at its home branch, or can it be picked up at one branch and returned at another?"  
**Interviewer**: "Start with same-branch pickup/return. Mention one-way rentals as an extension."

**You**: "Is thread safety a concern — can two customers try to book the same car for overlapping dates at the same time?"  
**Interviewer**: "Yes, assume concurrent booking attempts and design for it."

---

## Final Requirements

**In scope:**
1. Search available vehicles by branch, category, and date range
2. Create a reservation with an atomic availability check (no double-booking)
3. Pick up a vehicle — convert a reservation into an active `RentalAgreement`
4. Return a vehicle — compute base cost, late fee, and damage fee; close the agreement
5. Cancel a reservation before pickup
6. Pluggable pricing strategy per vehicle category
7. Thread-safe reservation creation across concurrent requests

**Out of scope:**
- Payment processing / gateway integration
- Driver's license verification and insurance underwriting
- Loyalty programs and discount codes
- Fleet redistribution logistics between branches (mentioned in one-way deep dive only)

---

## Core Entities and Relationships

| Entity | Responsibility |
|--------|----------------|
| Vehicle | Physical car — category, home branch, license plate, its own reservation list |
| Branch | Location holding a fleet of vehicles; entry point for search |
| Reservation | Links customer + vehicle + date range; tracks lifecycle (CONFIRMED, ACTIVE, COMPLETED, CANCELLED) |
| Customer | Identity of the renter |
| RentalAgreement | Created at pickup; captures odometer/fuel snapshot, drives the return-time billing |
| PricingStrategy | Computes daily rate for a vehicle category (injected, swappable) |

`Branch` owns a list of `Vehicle`s. `Vehicle` owns its own `Reservation` list, which keeps the overlap check local to the vehicle rather than scanning the whole system. `Reservation` transitions to `RentalAgreement` at pickup; `RentalAgreement` is closed at return, at which point `PricingStrategy` plus late/damage rules produce the final bill.

---

## Class Design

### Vehicle

| Requirement | What Vehicle must track |
|-------------|--------------------------|
| Identity & category | vehicle_id, license_plate, category: VehicleCategory |
| Home location | home_branch_id |
| Own bookings for overlap checks | reservations: list[Reservation] |
| Physical state | status: VehicleStatus (AVAILABLE, RESERVED, RENTED, MAINTENANCE) |

```
class Vehicle:
- vehicle_id: str
- license_plate: str
- category: VehicleCategory      # ECONOMY, SUV, LUXURY
- home_branch_id: str
- status: VehicleStatus
- reservations: list[Reservation]
- _lock: threading.Lock          # per-vehicle lock guards check+hold

+ has_conflict(start: date, end: date) -> bool
+ hold(reservation: Reservation) -> None
+ release(reservation_id: str) -> None
```

### Branch

```
class Branch:
- branch_id: str
- name: str
- vehicles: list[Vehicle]

+ search_available(category: VehicleCategory, start: date, end: date) -> list[Vehicle]
```

### Reservation

```
class Reservation:
- reservation_id: str
- customer: Customer
- vehicle: Vehicle
- start_date: date
- end_date: date
- status: ReservationStatus       # CONFIRMED, ACTIVE, COMPLETED, CANCELLED
- created_at: datetime

+ cancel() -> None
```

### RentalAgreement

```
class RentalAgreement:
- agreement_id: str
- reservation: Reservation
- pickup_time: datetime
- return_time: datetime | None
- odometer_start: int
- odometer_end: int | None
- damage_reported: bool
- damage_notes: str | None
- final_bill: Bill | None

+ close(return_time, odometer_end, damage_reported, damage_notes) -> Bill
```

### PricingStrategy

```
class PricingStrategy:                  # abstract
+ daily_rate(category: VehicleCategory) -> float

class StandardPricing(PricingStrategy):
- rates: dict[VehicleCategory, float]

class SeasonalPricing(PricingStrategy):
- base: PricingStrategy
- peak_months: set[int]
- surge_multiplier: float
```

### CarRentalSystem

```
class CarRentalSystem:
- branches: dict[str, Branch]
- reservations: dict[str, Reservation]
- agreements: dict[str, RentalAgreement]
- pricing_strategy: PricingStrategy
- _reservations_lock: threading.Lock

+ search_available_vehicles(branch_id, category, start, end) -> list[Vehicle]
+ create_reservation(customer, branch_id, category, start, end) -> Reservation
+ pickup_vehicle(reservation_id, odometer_start) -> RentalAgreement
+ complete_return(agreement_id, return_time, odometer_end, damage_reported, damage_notes) -> Bill
+ cancel_reservation(reservation_id) -> None
```

---

## Implementation

### Core Method: searchAvailableVehicles

**Core logic:**
1. Look up the branch; filter its fleet by `category` and `status != MAINTENANCE`
2. For each candidate vehicle, check whether any non-cancelled reservation overlaps `[start, end)`
3. Overlap formula: `existing.start < requestedEnd AND existing.end > requestedStart`
4. Return vehicles with no conflict

**Edge cases:**
- `start >= end` — reject with `IllegalArgumentException`
- Vehicle in MAINTENANCE — excluded regardless of reservations
- Branch not found — raise `NoSuchElementException`

```java
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

enum VehicleCategory { ECONOMY, SUV, LUXURY }

enum VehicleStatus { AVAILABLE, RESERVED, RENTED, MAINTENANCE }

enum ReservationStatus { CONFIRMED, ACTIVE, COMPLETED, CANCELLED }

class Customer {
    private final String customerId;
    private final String name;
    private final String licenseNumber;

    public Customer(String customerId, String name, String licenseNumber) {
        this.customerId = customerId;
        this.name = name;
        this.licenseNumber = licenseNumber;
    }

    public String getCustomerId() { return customerId; }
    public String getName() { return name; }
    public String getLicenseNumber() { return licenseNumber; }
}

class Reservation {
    private final String reservationId;
    private final Customer customer;
    private final Vehicle vehicle;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private ReservationStatus status;
    private final LocalDateTime createdAt;

    public Reservation(String reservationId, Customer customer, Vehicle vehicle,
                        LocalDate startDate, LocalDate endDate) {
        this.reservationId = reservationId;
        this.customer = customer;
        this.vehicle = vehicle;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = ReservationStatus.CONFIRMED;
        this.createdAt = LocalDateTime.now();
    }

    public void cancel() {
        if (status == ReservationStatus.ACTIVE || status == ReservationStatus.COMPLETED) {
            throw new IllegalStateException("Cannot cancel from status " + status);
        }
        status = ReservationStatus.CANCELLED;
    }

    public String getReservationId() { return reservationId; }
    public Customer getCustomer() { return customer; }
    public Vehicle getVehicle() { return vehicle; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public ReservationStatus getStatus() { return status; }
    public void setStatus(ReservationStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}

class Vehicle {
    private final String vehicleId;
    private final String licensePlate;
    private final VehicleCategory category;
    private final String homeBranchId;
    private VehicleStatus status;
    private final List<Reservation> reservations = new ArrayList<>();
    private final ReentrantLock lock = new ReentrantLock();

    public Vehicle(String vehicleId, String licensePlate, VehicleCategory category, String homeBranchId) {
        this.vehicleId = vehicleId;
        this.licensePlate = licensePlate;
        this.category = category;
        this.homeBranchId = homeBranchId;
        this.status = VehicleStatus.AVAILABLE;
    }

    public boolean hasConflict(LocalDate start, LocalDate end) {
        for (Reservation r : reservations) {
            if (r.getStatus() == ReservationStatus.CANCELLED || r.getStatus() == ReservationStatus.COMPLETED) {
                continue;
            }
            if (r.getStartDate().isBefore(end) && r.getEndDate().isAfter(start)) {
                return true;
            }
        }
        return false;
    }

    public ReentrantLock getLock() { return lock; }
    public void addReservation(Reservation r) { reservations.add(r); }
    public List<Reservation> getReservations() { return reservations; }

    public String getVehicleId() { return vehicleId; }
    public String getLicensePlate() { return licensePlate; }
    public VehicleCategory getCategory() { return category; }
    public String getHomeBranchId() { return homeBranchId; }
    public VehicleStatus getStatus() { return status; }
    public void setStatus(VehicleStatus status) { this.status = status; }
}

class Branch {
    private final String branchId;
    private final String name;
    private final List<Vehicle> vehicles = new ArrayList<>();

    public Branch(String branchId, String name) {
        this.branchId = branchId;
        this.name = name;
    }

    public void addVehicle(Vehicle vehicle) { vehicles.add(vehicle); }
    public List<Vehicle> getVehicles() { return vehicles; }
    public String getBranchId() { return branchId; }
    public String getName() { return name; }

    public List<Vehicle> searchAvailable(VehicleCategory category, LocalDate start, LocalDate end) {
        List<Vehicle> results = new ArrayList<>();
        for (Vehicle v : vehicles) {
            if (v.getStatus() == VehicleStatus.MAINTENANCE) {
                continue;
            }
            if (category != null && v.getCategory() != category) {
                continue;
            }
            v.getLock().lock();
            try {
                if (!v.hasConflict(start, end)) {
                    results.add(v);
                }
            } finally {
                v.getLock().unlock();
            }
        }
        return results;
    }
}

interface PricingStrategy {
    double dailyRate(VehicleCategory category);
}

class StandardPricing implements PricingStrategy {
    private static final Map<VehicleCategory, Double> RATES = Map.of(
        VehicleCategory.ECONOMY, 40.0,
        VehicleCategory.SUV, 70.0,
        VehicleCategory.LUXURY, 150.0
    );

    @Override
    public double dailyRate(VehicleCategory category) {
        return RATES.get(category);
    }
}

class Bill {
    private final String agreementId;
    private final double baseCost;
    private final double lateFee;
    private final double damageFee;
    private final double total;

    public Bill(String agreementId, double baseCost, double lateFee, double damageFee) {
        this.agreementId = agreementId;
        this.baseCost = baseCost;
        this.lateFee = lateFee;
        this.damageFee = damageFee;
        this.total = baseCost + lateFee + damageFee;
    }

    public String getAgreementId() { return agreementId; }
    public double getBaseCost() { return baseCost; }
    public double getLateFee() { return lateFee; }
    public double getDamageFee() { return damageFee; }
    public double getTotal() { return total; }
}

class RentalAgreement {
    private static final double LATE_FEE_PER_DAY = 60.0;
    private static final double FLAT_DAMAGE_FEE = 250.0;

    private final String agreementId;
    private final Reservation reservation;
    private final LocalDateTime pickupTime;
    private LocalDateTime returnTime;
    private final int odometerStart;
    private Integer odometerEnd;
    private boolean damageReported;
    private String damageNotes;
    private Bill finalBill;

    public RentalAgreement(String agreementId, Reservation reservation,
                            LocalDateTime pickupTime, int odometerStart) {
        this.agreementId = agreementId;
        this.reservation = reservation;
        this.pickupTime = pickupTime;
        this.odometerStart = odometerStart;
    }

    public Bill close(PricingStrategy pricingStrategy, LocalDateTime returnTime, int odometerEnd,
                       boolean damageReported, String damageNotes) {
        this.returnTime = returnTime;
        this.odometerEnd = odometerEnd;
        this.damageReported = damageReported;
        this.damageNotes = damageNotes;

        long plannedDays = ChronoUnit.DAYS.between(
            reservation.getStartDate(), reservation.getEndDate());
        plannedDays = Math.max(plannedDays, 1);
        double dailyRate = pricingStrategy.dailyRate(reservation.getVehicle().getCategory());
        double baseCost = plannedDays * dailyRate;

        LocalDate actualReturnDate = returnTime.toLocalDate();
        long lateDays = ChronoUnit.DAYS.between(reservation.getEndDate(), actualReturnDate);
        double lateFee = lateDays > 0 ? lateDays * LATE_FEE_PER_DAY : 0.0;

        double damageFee = damageReported ? FLAT_DAMAGE_FEE : 0.0;

        this.finalBill = new Bill(agreementId, baseCost, lateFee, damageFee);
        return this.finalBill;
    }

    public String getAgreementId() { return agreementId; }
    public Reservation getReservation() { return reservation; }
    public LocalDateTime getPickupTime() { return pickupTime; }
    public LocalDateTime getReturnTime() { return returnTime; }
    public int getOdometerStart() { return odometerStart; }
    public Integer getOdometerEnd() { return odometerEnd; }
    public boolean isDamageReported() { return damageReported; }
    public String getDamageNotes() { return damageNotes; }
    public Bill getFinalBill() { return finalBill; }
}

class CarRentalSystem {
    private final Map<String, Branch> branches = new HashMap<>();
    private final Map<String, Reservation> reservations = new ConcurrentHashMap<>();
    private final Map<String, RentalAgreement> agreements = new ConcurrentHashMap<>();
    private final PricingStrategy pricingStrategy;

    public CarRentalSystem(PricingStrategy pricingStrategy) {
        this.pricingStrategy = pricingStrategy;
    }

    public void addBranch(Branch branch) {
        branches.put(branch.getBranchId(), branch);
    }

    public List<Vehicle> searchAvailableVehicles(String branchId, VehicleCategory category,
                                                  LocalDate start, LocalDate end) {
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("start must be before end");
        }
        Branch branch = branches.get(branchId);
        if (branch == null) {
            throw new NoSuchElementException("Branch " + branchId + " not found");
        }
        return branch.searchAvailable(category, start, end);
    }

    public Reservation createReservation(Customer customer, String branchId, VehicleCategory category,
                                          LocalDate start, LocalDate end) {
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("start must be before end");
        }
        Branch branch = branches.get(branchId);
        if (branch == null) {
            throw new NoSuchElementException("Branch " + branchId + " not found");
        }

        for (Vehicle vehicle : branch.getVehicles()) {
            if (vehicle.getStatus() == VehicleStatus.MAINTENANCE) {
                continue;
            }
            if (category != null && vehicle.getCategory() != category) {
                continue;
            }
            vehicle.getLock().lock();
            try {
                if (!vehicle.hasConflict(start, end)) {
                    Reservation reservation = new Reservation(
                        UUID.randomUUID().toString(), customer, vehicle, start, end);
                    vehicle.addReservation(reservation);
                    reservations.put(reservation.getReservationId(), reservation);
                    return reservation;
                }
            } finally {
                vehicle.getLock().unlock();
            }
        }
        throw new NoSuchElementException("No vehicle available for requested category/dates");
    }

    public RentalAgreement pickupVehicle(String reservationId, int odometerStart) {
        Reservation reservation = getReservation(reservationId);
        if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw new IllegalStateException("Cannot pick up from status " + reservation.getStatus());
        }
        reservation.setStatus(ReservationStatus.ACTIVE);
        reservation.getVehicle().setStatus(VehicleStatus.RENTED);

        RentalAgreement agreement = new RentalAgreement(
            UUID.randomUUID().toString(), reservation, LocalDateTime.now(), odometerStart);
        agreements.put(agreement.getAgreementId(), agreement);
        return agreement;
    }

    public Bill completeReturn(String agreementId, LocalDateTime returnTime, int odometerEnd,
                                boolean damageReported, String damageNotes) {
        RentalAgreement agreement = agreements.get(agreementId);
        if (agreement == null) {
            throw new NoSuchElementException("Agreement " + agreementId + " not found");
        }
        Bill bill = agreement.close(pricingStrategy, returnTime, odometerEnd, damageReported, damageNotes);

        Reservation reservation = agreement.getReservation();
        reservation.setStatus(ReservationStatus.COMPLETED);
        reservation.getVehicle().setStatus(VehicleStatus.AVAILABLE);
        return bill;
    }

    public void cancelReservation(String reservationId) {
        Reservation reservation = getReservation(reservationId);
        reservation.cancel();
    }

    private Reservation getReservation(String reservationId) {
        Reservation reservation = reservations.get(reservationId);
        if (reservation == null) {
            throw new NoSuchElementException("Reservation " + reservationId + " not found");
        }
        return reservation;
    }
}
```

---

## Verification

**Scenario**: Bob reserves an SUV at branch `SFO` for 2024-07-01 to 2024-07-04 (3 planned days, $70/day).

1. `searchAvailableVehicles("SFO", SUV, 2024-07-01, 2024-07-04)` → returns [Vehicle V1] (fleet has one SUV, no conflicting reservations)
2. `createReservation(bob, "SFO", SUV, 2024-07-01, 2024-07-04)` — vehicle lock acquired, `hasConflict` returns false, Reservation `r1` created and appended to V1's reservation list, status `CONFIRMED`
3. `searchAvailableVehicles("SFO", SUV, 2024-07-02, 2024-07-03)` → V1 NOT returned (conflict: `r1.start=07-01 < 07-03` and `r1.end=07-04 > 07-02`)
4. `pickupVehicle("r1", odometerStart=12000)` → `r1.status = ACTIVE`, `V1.status = RENTED`, RentalAgreement `a1` created with `pickupTime = now`

**Return on 2024-07-06 (2 days late), no damage:**
1. `completeReturn("a1", 2024-07-06T10:00, odometerEnd=12450, damageReported=false, null)`
2. `plannedDays = 3` → `baseCost = 3 * 70 = $210`
3. `lateDays = days(07-04, 07-06) = 2` → `lateFee = 2 * 60 = $120`
4. `damageFee = $0`
5. `Bill.total = 210 + 120 + 0 = $330`
6. `r1.status = COMPLETED`, `V1.status = AVAILABLE` — V1 is now bookable again for dates on/after 2024-07-06

---

## Deep Dive & Extensibility

### 1. "How do you prevent two customers from double-booking the same vehicle for overlapping dates under concurrency?"

The check (`hasConflict`) and the mutation (`addReservation`) must be atomic as a single critical section, per vehicle. `createReservation` above already does this: it acquires `vehicle.getLock()`, checks for conflicts, and appends the reservation before releasing the lock. Two threads racing for the same vehicle serialize on that lock — the second thread re-evaluates `hasConflict` and sees the first thread's reservation, so it correctly fails or falls through to try another vehicle.

```java
vehicle.getLock().lock();
try {
    if (!vehicle.hasConflict(start, end)) {
        Reservation reservation = new Reservation(UUID.randomUUID().toString(), customer, vehicle, start, end);
        vehicle.addReservation(reservation);
        reservations.put(reservation.getReservationId(), reservation);
        return reservation;
    }
} finally {
    vehicle.getLock().unlock();
}
```

Per-vehicle locking (rather than one global lock) gives parallelism proportional to fleet size — a booking attempt on vehicle A never blocks a booking attempt on vehicle B. In a distributed system, replace the in-memory lock with a DB transaction using `SELECT ... FOR UPDATE` on the vehicle row, or an exclusion constraint (`EXCLUDE USING gist (vehicle_id WITH =, daterange(start_date, end_date) WITH &&)` in Postgres) so the database itself rejects overlapping inserts even across multiple application servers.

### 2. "How would you support one-way rentals (pick up at branch A, return at branch B)?"

Add `pickup_branch_id` and `return_branch_id` to `Reservation`, and an optional `one_way_fee` in `PricingStrategy`. The vehicle's `home_branch_id` no longer determines where it must be returned — instead, `Vehicle.status` transitions to `AVAILABLE` at the *return* branch, not the pickup branch, meaning the vehicle effectively "moves" between branches' fleets.

```java
class Reservation {
    private final String pickupBranchId;
    private final String returnBranchId;
    // ...constructor sets both; if equal, it's a same-branch rental
}

interface PricingStrategy {
    double dailyRate(VehicleCategory category);
    default double oneWayFee(String pickupBranchId, String returnBranchId) {
        return pickupBranchId.equals(returnBranchId) ? 0.0 : 75.0;
    }
}
```

`completeReturn` must move the vehicle from the pickup branch's fleet to the return branch's fleet:

```java
public Bill completeReturn(String agreementId, String actualReturnBranchId, LocalDateTime returnTime,
                            int odometerEnd, boolean damageReported, String damageNotes) {
    RentalAgreement agreement = agreements.get(agreementId);
    Vehicle vehicle = agreement.getReservation().getVehicle();
    Branch oldBranch = branches.get(agreement.getReservation().getPickupBranchId());
    Branch newBranch = branches.get(actualReturnBranchId);

    oldBranch.getVehicles().remove(vehicle);
    newBranch.addVehicle(vehicle);
    vehicle.setStatus(VehicleStatus.AVAILABLE);
    // ... rest of billing logic, plus oneWayFee if branches differ
}
```

This creates a fleet-imbalance problem (every vehicle drifting toward high-demand drop-off branches) — worth mentioning that production systems run a separate rebalancing job, out of scope here.

### 3. "How would you add dynamic pricing based on demand/season?"

Wrap the base `PricingStrategy` with a decorator that adjusts the daily rate based on external signals — season, local demand, or day of week — without touching `RentalAgreement.close()`.

```java
class SeasonalPricing implements PricingStrategy {
    private final PricingStrategy base;
    private final Set<Integer> peakMonths;
    private final double surgeMultiplier;

    public SeasonalPricing(PricingStrategy base, Set<Integer> peakMonths, double surgeMultiplier) {
        this.base = base;
        this.peakMonths = peakMonths;
        this.surgeMultiplier = surgeMultiplier;
    }

    @Override
    public double dailyRate(VehicleCategory category) {
        int currentMonth = LocalDate.now().getMonthValue();
        double rate = base.dailyRate(category);
        return peakMonths.contains(currentMonth) ? rate * surgeMultiplier : rate;
    }
}

class DemandBasedPricing implements PricingStrategy {
    private final PricingStrategy base;
    private final Branch branch;
    private final double surgeThreshold;
    private final double maxMultiplier;

    public DemandBasedPricing(PricingStrategy base, Branch branch, double surgeThreshold, double maxMultiplier) {
        this.base = base;
        this.branch = branch;
        this.surgeThreshold = surgeThreshold;
        this.maxMultiplier = maxMultiplier;
    }

    @Override
    public double dailyRate(VehicleCategory category) {
        long totalOfCategory = branch.getVehicles().stream()
            .filter(v -> v.getCategory() == category).count();
        long rentedOfCategory = branch.getVehicles().stream()
            .filter(v -> v.getCategory() == category && v.getStatus() == VehicleStatus.RENTED)
            .count();
        double utilization = totalOfCategory == 0 ? 0.0 : (double) rentedOfCategory / totalOfCategory;
        double baseRate = base.dailyRate(category);
        if (utilization <= surgeThreshold) {
            return baseRate;
        }
        double surge = 1.0 + (utilization - surgeThreshold) / (1 - surgeThreshold);
        return baseRate * Math.min(surge, maxMultiplier);
    }
}
```

Because `RentalAgreement.close()` only calls `pricingStrategy.dailyRate(category)`, any of these strategies can be swapped in without changing booking or billing logic — the Strategy pattern keeps pricing rules fully decoupled from the reservation lifecycle.

---

## Interviewer Questions by Level

**Junior**: Define the `Reservation` and `RentalAgreement` classes and explain the difference between them. Walk through what fields each needs and why they're separate objects rather than one class.

**Mid-level**: Implement `createReservation` with the date-overlap check. Explain the overlap formula and why `start < end` matters. Implement `completeReturn` including late fee computation. Justify per-vehicle locking over a single global lock.

**Senior**: Two customers hit "Book Now" on the same SUV for overlapping dates within milliseconds of each other in a distributed, multi-instance deployment — the in-memory lock in this design does not help. How do you guarantee exactly one succeeds? (Expect: DB transaction + row lock or exclusion constraint, idempotency key on the request.) Design one-way rentals and discuss the fleet-imbalance consequence. Discuss how you'd evolve `PricingStrategy` into a full rules engine without an explosion of `if` statements.

---

## Common Interview Questions

- Q: Why is the reservation list stored per-`Vehicle` instead of a single global reservation table scanned on every search? A: Locality — checking availability for one vehicle only touches that vehicle's reservations, keeping the check O(reservations for that vehicle) rather than O(all reservations system-wide), and lets the lock scope match the conflict scope exactly.
- Q: What's the date-range overlap formula and why? A: `existing.start < requestedEnd AND existing.end > requestedStart`. Two ranges fail to overlap only when one ends at or before the other starts; this formula is the direct negation of that and correctly covers full/partial containment in either direction.
- Q: Why model a walk-up rental as a reservation starting "now" instead of a separate code path? A: One code path for availability checking avoids duplicating the overlap logic and locking discipline; the only difference is `start_date = today`.
- Q: Why keep `RentalAgreement` separate from `Reservation`? A: `Reservation` is a booking intent (dates, customer, vehicle) that can be cancelled before pickup. `RentalAgreement` is created only at pickup and carries operational data (odometer, damage) irrelevant to a reservation that never gets picked up.
- Q: How do you stop a vehicle from being rented out from under a customer who already reserved it? A: The vehicle's status alone is not the source of truth — a vehicle can be `AVAILABLE` today but have a future `CONFIRMED` reservation. Availability is always derived from the overlap check against reservations, never from `status` in isolation.
- Q: How would you test for double-booking under concurrency? A: Spin up N threads calling `createReservation` for the same category/dates against a branch with one matching vehicle. Assert exactly one succeeds and the rest raise `NoSuchElementException`, and that the vehicle's reservation list has exactly one non-cancelled entry for that window.
- Q: How is a late fee different from a cancellation no-show fee? A: Late fee is computed at `completeReturn` from the gap between the reservation's `end_date` and the actual return date. A no-show is a reservation that's still `CONFIRMED` past its start date with no pickup — that's a separate lifecycle rule (auto-expire or auto-cancel), not part of billing.

---

## Concurrency Test Harness

Runnable tests that verify thread-safety invariants. No external deps beyond the JDK standard library.

```java
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

class CarRentalConcurrencyTest {

    static CarRentalSystem makeSystem(int suvCount) {
        CarRentalSystem system = new CarRentalSystem(new StandardPricing());
        Branch branch = new Branch("SFO", "San Francisco");
        for (int i = 0; i < suvCount; i++) {
            branch.addVehicle(new Vehicle("V" + i, "PLATE-" + i, VehicleCategory.SUV, "SFO"));
        }
        system.addBranch(branch);
        return system;
    }

    // ─────────────────────────────────────────────────────────────
    // TEST 1: Overlapping-date race on a single vehicle.
    // One SUV in the fleet. 20 threads race to reserve the SAME
    // overlapping date range. Exactly one must succeed.
    // ─────────────────────────────────────────────────────────────
    static void testOverlappingDatesExactlyOneWins() throws InterruptedException {
        CarRentalSystem system = makeSystem(1);
        LocalDate start = LocalDate.of(2024, 7, 1);
        LocalDate end = LocalDate.of(2024, 7, 4);

        List<Reservation> succeeded = Collections.synchronizedList(new ArrayList<>());
        List<Integer> failed = Collections.synchronizedList(new ArrayList<>());

        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            final int idx = i;
            Thread t = new Thread(() -> {
                Customer c = new Customer("cust-" + idx, "Customer " + idx, "DL-" + idx);
                try {
                    Reservation r = system.createReservation(c, "SFO", VehicleCategory.SUV, start, end);
                    succeeded.add(r);
                } catch (NoSuchElementException e) {
                    failed.add(idx);
                }
            });
            threads.add(t);
        }
        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();

        if (succeeded.size() != 1) {
            throw new AssertionError("Expected exactly 1 success, got " + succeeded.size());
        }
        if (failed.size() != 19) {
            throw new AssertionError("Expected 19 failures, got " + failed.size());
        }
        System.out.println("PASS: testOverlappingDatesExactlyOneWins");
    }

    // ─────────────────────────────────────────────────────────────
    // TEST 2: Non-overlapping dates on the same vehicle both succeed.
    // One SUV. Two threads reserve disjoint date ranges concurrently.
    // Both must succeed since the ranges don't overlap.
    // ─────────────────────────────────────────────────────────────
    static void testNonOverlappingDatesBothSucceed() throws InterruptedException {
        CarRentalSystem system = makeSystem(1);
        LocalDate rangeAStart = LocalDate.of(2024, 7, 1);
        LocalDate rangeAEnd = LocalDate.of(2024, 7, 4);
        LocalDate rangeBStart = LocalDate.of(2024, 7, 10);
        LocalDate rangeBEnd = LocalDate.of(2024, 7, 14);

        List<Reservation> succeeded = Collections.synchronizedList(new ArrayList<>());
        List<Exception> failures = Collections.synchronizedList(new ArrayList<>());

        Runnable bookA = () -> {
            try {
                Customer c = new Customer("alice", "Alice", "DL-A");
                succeeded.add(system.createReservation(c, "SFO", VehicleCategory.SUV, rangeAStart, rangeAEnd));
            } catch (Exception e) {
                failures.add(e);
            }
        };
        Runnable bookB = () -> {
            try {
                Customer c = new Customer("bob", "Bob", "DL-B");
                succeeded.add(system.createReservation(c, "SFO", VehicleCategory.SUV, rangeBStart, rangeBEnd));
            } catch (Exception e) {
                failures.add(e);
            }
        };

        Thread t1 = new Thread(bookA);
        Thread t2 = new Thread(bookB);
        t1.start(); t2.start();
        t1.join(); t2.join();

        if (succeeded.size() != 2) {
            throw new AssertionError("Expected 2 successes, got " + succeeded.size() + ", failures=" + failures);
        }
        System.out.println("PASS: testNonOverlappingDatesBothSucceed");
    }

    // ─────────────────────────────────────────────────────────────
    // TEST 3: Fleet-level race — 5 SUVs, 50 threads reserve the same
    // overlapping dates. Exactly 5 must succeed (one per vehicle),
    // and no vehicle may end up with two non-cancelled overlapping
    // reservations.
    // ─────────────────────────────────────────────────────────────
    static void testFleetLevelNoDoubleBooking() throws InterruptedException {
        CarRentalSystem system = makeSystem(5);
        LocalDate start = LocalDate.of(2024, 8, 1);
        LocalDate end = LocalDate.of(2024, 8, 5);

        List<Reservation> succeeded = Collections.synchronizedList(new ArrayList<>());
        List<Integer> failed = Collections.synchronizedList(new ArrayList<>());

        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            final int idx = i;
            Thread t = new Thread(() -> {
                Customer c = new Customer("cust-" + idx, "Customer " + idx, "DL-" + idx);
                try {
                    Reservation r = system.createReservation(c, "SFO", VehicleCategory.SUV, start, end);
                    succeeded.add(r);
                } catch (NoSuchElementException e) {
                    failed.add(idx);
                }
            });
            threads.add(t);
        }
        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();

        if (succeeded.size() != 5) {
            throw new AssertionError("Expected 5 successes, got " + succeeded.size());
        }
        if (failed.size() != 45) {
            throw new AssertionError("Expected 45 failures, got " + failed.size());
        }

        Set<String> vehiclesUsed = new HashSet<>();
        for (Reservation r : succeeded) {
            String vid = r.getVehicle().getVehicleId();
            if (vehiclesUsed.contains(vid)) {
                throw new AssertionError("Double-booking on vehicle " + vid);
            }
            vehiclesUsed.add(vid);
        }
        System.out.println("PASS: testFleetLevelNoDoubleBooking");
    }

    public static void main(String[] args) throws InterruptedException {
        testOverlappingDatesExactlyOneWins();
        testNonOverlappingDatesBothSucceed();
        testFleetLevelNoDoubleBooking();
        System.out.println("All concurrency tests passed.");
    }
}
```

**What each test verifies:**
- `testOverlappingDatesExactlyOneWins`: The per-vehicle `ReentrantLock` around `hasConflict` + `addReservation` in `createReservation` makes check-and-hold atomic. Without the lock, two threads could both observe no conflict before either inserts, resulting in two overlapping reservations on the same car.
- `testNonOverlappingDatesBothSucceed`: The lock only serializes access to a vehicle's reservation list — it does not reject valid concurrent bookings for disjoint date ranges, confirming the lock isn't over-restrictive.
- `testFleetLevelNoDoubleBooking`: With multiple vehicles of the same category, the system correctly spreads concurrent demand across the fleet (one reservation per vehicle) and rejects only the requests that exceed fleet capacity for that window.

---

## Related

**Patterns applied here**

- [Strategy Pattern](../../03-design-patterns/03-behavioral/strategy-pattern.md)
- [Factory Pattern](../../03-design-patterns/01-creational/factory-pattern.md)

**SOLID focus**: [Open/Closed](../../02-solid-principles/02-open-closed.md)

**Concurrency**: [Concurrency Patterns](../../04-concurrency/concurrency-patterns.md)

**Practice next**

- [Design Hotel Management](../02-frequent-problems/11-design-hotel-management.md)
- [Design a Parking Lot](../01-core-problems/01-design-parking-lot.md)

Both share the interval-overlap availability check and the reservation-then-agreement lifecycle shape used here.

**Frameworks**: [LLD Template](../../../07-interview-templates/01-frameworks/02-lld-template.md) · [UML Diagrams](../../01-oop-fundamentals/uml-diagrams.md) · [OOP Four Pillars](../../01-oop-fundamentals/four-pillars.md)
