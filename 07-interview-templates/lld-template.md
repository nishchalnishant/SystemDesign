# LLD Interview Framework (45-60 min)

## The Mental Model

You're drawing blueprints for a single building, not designing the city's infrastructure. HLD asks "what components exist and how do they talk?" LLD asks "what does the code inside one component look like?"

In LLD, the interviewer is watching:
1. Can you translate a real-world problem into clean object hierarchies?
2. Do you know when to apply design patterns — and when not to?
3. Can you write readable, production-quality code under time pressure?
4. Do you think about extensibility, concurrency, and edge cases?

The failure mode is jumping to code before you have a class diagram. Build top-down: requirements → use cases → classes → patterns → code.

---

## Template Mindmap

```
LLD Interview Framework (45-60 min)
├── Core Problem
│   └── Translate a real-world problem into a clean, extensible object model with code
├── Phase 1 — Requirements (0-5 min)
│   ├── Actors → who uses the system (User, Admin, Driver, etc.)
│   ├── Core features → 3-5 primary use cases only
│   └── Constraints → concurrency, persistence, throughput expectations
├── Phase 2 — Use Cases (5-10 min)
│   ├── Primary flows → happy path for each core feature
│   └── Edge cases → concurrent access, invalid inputs, resource limits
├── Phase 3 — Class Identification (10-15 min)
│   ├── Nouns → candidate classes (Order, User, Payment, Seat)
│   ├── Verbs → candidate methods (reserve(), cancel(), process())
│   └── Adjectives → candidate attributes or enums (OrderStatus, SeatType)
├── Phase 4 — Class Diagram (15-25 min)
│   ├── Relationships: inheritance, composition, aggregation, dependency
│   ├── Interfaces → define contracts before implementations
│   └── Attributes + method signatures per class
├── Phase 5 — Design Patterns (25-35 min)
│   ├── Factory → object creation without exposing instantiation logic
│   ├── Strategy → interchangeable algorithms (pricing, discount, routing)
│   ├── Observer → event-driven updates (notification, audit log)
│   ├── Singleton → shared resource (DB connection pool, config)
│   └── Decorator → layered behavior (logging, rate limiting, auth)
├── Phase 6 — Core Code (35-55 min)
│   ├── Code the most complex class or method in full
│   ├── Handle concurrency if relevant (synchronized, locks, atomic ops)
│   └── Show null checks, boundary conditions, error paths
├── When to Use
│   └── ✓ "Design the classes for X" or "write the code for Y" style questions
└── Interview Angles
    ├── "Why did you use Strategy here?" → justify each pattern by the problem it solves
    ├── "How do you handle concurrent bookings?" → locking strategy, optimistic vs pessimistic
    └── "How would you add X feature?" → show extensibility via open/closed principle
```

---

## Interview Flow Timeline

| Phase | Time | Activity |
|-------|------|----------|
| **1. Requirements** | 0-5 min | Actors, core features, constraints |
| **2. Use Cases** | 5-10 min | Primary flows, edge cases |
| **3. Class Identification** | 10-15 min | Nouns → classes, verbs → methods |
| **4. Class Diagram** | 15-25 min | Relationships, UML, attributes |
| **5. Design Patterns** | 25-35 min | Pattern selection + justification |
| **6. Code Key Methods** | 35-50 min | Implement 2-3 critical methods |
| **7. Trade-offs & Extensions** | 50-60 min | SOLID check, extensions, concurrency |

---

## Phase 1: Requirements (0-5 min)

### The Three Questions to Always Ask

```
1. Who are the actors? (who uses this system)
2. What are the must-have use cases? (top 3-5 only)
3. What are the constraints? (concurrency? extensibility? language?)
```

### Identifying Actors
- **Human actors**: Customer, Admin, Attendant, Player
- **System actors**: Payment Gateway, Notification Service, Timer

### Identifying Constraints (ask explicitly)
- "Should this be thread-safe for concurrent access?"
- "Do we need to support new vehicle/payment/piece types later? (extensibility)"
- "Any memory or performance constraints I should know about?"

### Example — "Design a Parking Lot System"

```
Actors:
  - Customer (parks and retrieves vehicle)
  - Attendant (issues and processes tickets)
  - Admin (configures lot capacity and rates)

Core Features (must-have):
  - Park a vehicle (car, bike, truck)
  - Calculate parking fee based on duration
  - Find and assign an available spot
  - Release a spot on vehicle exit

Should-have:
  - Different spot types (compact, large, handicap)
  - Multiple payment methods

Out-of-scope:
  - Online reservations
  - Real-time availability UI
  - License plate recognition
```

### What NOT to ask:
- Technology stack questions ("should I use Java or Python?") — state your preference and proceed
- Database questions — LLD is about in-memory object design, not persistence
- Network/API questions — that's HLD territory

---

## Phase 2: Use Cases (5-10 min)

### Use Case Template

Write 2-3 primary use cases in this format — it forces you to think about flows before classes:

```
UC1: Park Vehicle
  Actor: Customer
  Precondition: Customer has a vehicle, lot has available spots
  Main Flow:
    1. Customer arrives with vehicle
    2. System checks for available spot by vehicle type
    3. System assigns the spot
    4. System issues ticket with entry time
    5. Customer receives ticket
  Exception Flows:
    - No spots available → throw NoSpotAvailableException
    - Invalid vehicle type → throw InvalidVehicleException

UC2: Exit and Pay
  Actor: Customer
  Precondition: Customer has valid ticket
  Main Flow:
    1. Customer presents ticket at exit
    2. System retrieves ticket by ticket_id
    3. System records exit time
    4. System calculates fee (rate × duration)
    5. Customer pays
    6. System releases spot
  Exception Flows:
    - Invalid/lost ticket → throw InvalidTicketException
    - Payment failure → spot remains occupied, retry
```

### How to prioritize use cases
Implement the core happy path first, then the most likely failure path.

For parking lot: `parkVehicle()` and `exitAndPay()` are the two critical flows — everything else can be discussed without coding.

---

## Phase 3: Class Identification (10-15 min)

### The Noun-Verb Technique

Read your use cases back. Extract:
- **Nouns** → candidate classes (entities, actors, value objects)
- **Verbs** → candidate methods on those classes

**Parking Lot nouns:** ParkingLot, Floor, ParkingSpot, Vehicle, Car, Bike, Truck, Ticket, Payment

**Parking Lot verbs (mapped to methods):**
```
parkVehicle()      → ParkingLot
findAvailableSpot() → ParkingLot
assignVehicle()    → ParkingSpot
isAvailable()      → ParkingSpot
calculateFee()     → PricingStrategy
markExit()         → Ticket
getDurationHours() → Ticket
process()          → Payment
```

### Relationship Types — Know These Cold

| Relationship | Meaning | Example |
|---|---|---|
| IS-A (Inheritance) | "is a kind of" | Car IS-A Vehicle |
| HAS-A (Composition) | "owns, can't exist without" | ParkingLot HAS-A ParkingSpot |
| HAS-A (Aggregation) | "has, but can exist independently" | ParkingLot HAS-A Vehicle |
| USES (Association/Dependency) | "uses temporarily" | ParkingLot USES PricingStrategy |

**Composition vs. Aggregation — the key question:**
> "If the parent is destroyed, does the child cease to exist?"
> Yes → Composition. No → Aggregation.

ParkingLot destroyed → ParkingSpots no longer make sense → Composition.
ParkingLot closed → Vehicles still exist → Aggregation.

---

## Phase 4: Class Diagram (15-25 min)

### Core Classes — Parking Lot

```java
// ─── Enums ────────────────────────────────────────────────────────────────

enum VehicleType { BIKE, CAR, TRUCK }
enum SpotType    { COMPACT, LARGE, HANDICAP }
enum PaymentMethod  { CASH, CREDIT_CARD, UPI }
enum PaymentStatus  { PENDING, SUCCESS, FAILED }

// ─── Vehicle hierarchy ────────────────────────────────────────────────────

abstract class Vehicle {
    - String licensePlate
    - VehicleType type
    + Vehicle(String licensePlate, VehicleType type)
    + String getLicensePlate()
    + VehicleType getType()
}

class Car   extends Vehicle { + Car(String plate)   }
class Bike  extends Vehicle { + Bike(String plate)  }
class Truck extends Vehicle { + Truck(String plate) }

// ─── ParkingSpot ─────────────────────────────────────────────────────────

class ParkingSpot {
    - String spotId
    - SpotType type
    - boolean isOccupied
    - Vehicle vehicle           // null if unoccupied
    + boolean isAvailable()
    + boolean canFit(VehicleType type)
    + void assignVehicle(Vehicle v)
    + void removeVehicle()
}

// ─── Ticket ───────────────────────────────────────────────────────────────

class Ticket {
    - String ticketId
    - Vehicle vehicle
    - ParkingSpot spot
    - Instant entryTime
    - Instant exitTime          // null until exit
    + void markExit(Instant exitTime)
    + double getDurationHours()
}

// ─── Payment ──────────────────────────────────────────────────────────────

class Payment {
    - String paymentId
    - double amount
    - PaymentMethod method
    - PaymentStatus status
    + boolean process()
}

// ─── ParkingLot (top-level orchestrator) ─────────────────────────────────

class ParkingLot {
    - Map<String, ParkingSpot> spots
    - Map<String, Ticket> activeTickets
    - PricingStrategy pricingStrategy
    + Ticket parkVehicle(Vehicle vehicle)
    + double exitAndPay(String ticketId, PaymentMethod method)
    + ParkingSpot findAvailableSpot(VehicleType type)    // private
}
```

### UML Diagram

```
┌─────────────────────────────────┐
│         ParkingLot              │
├─────────────────────────────────┤
│ - spots: Map<String,ParkingSpot>│
│ - activeTickets: Map            │
│ - pricingStrategy               │
├─────────────────────────────────┤
│ + parkVehicle(Vehicle): Ticket  │
│ + exitAndPay(ticketId): double  │
└──────────────┬──────────────────┘
               │ composes (1..*)
               ▼
┌──────────────────────┐         ┌──────────────────┐
│     ParkingSpot      │◄────────│     Ticket        │
├──────────────────────┤ refs    ├──────────────────┤
│ - spotId             │         │ - ticketId        │
│ - type: SpotType     │         │ - entryTime       │
│ - isOccupied         │         │ - exitTime        │
├──────────────────────┤         ├──────────────────┤
│ + isAvailable()      │         │ + markExit()      │
│ + canFit(VehicleType)│         │ + getDuration()   │
│ + assignVehicle()    │         └────────┬─────────┘
│ + removeVehicle()    │                  │ refs
└──────────────────────┘                  ▼
                               ┌──────────────────┐
                               │    Vehicle        │◄──┐
                               ├──────────────────┤   │
                               │ - licensePlate   │   │ IS-A
                               │ - type           │   │
                               └──────────────────┘   │
                                    ▲   ▲   ▲         │
                                    │   │   │─────────┘
                               ┌────┘   │   └────┐
                             Car      Bike     Truck

interface PricingStrategy
  └── HourlyPricing
  └── FlatRatePricing
  └── WeekendPricing
```

### Whiteboard vs. verbal approach
- On whiteboard: draw boxes, write attribute names (no types), arrow for relationships
- Verbally: "ParkingLot composes ParkingSpot — the spots can't exist outside the lot. Ticket has references to both the Vehicle and the ParkingSpot it was assigned."

---

## Phase 5: Design Patterns (25-35 min)

### Pattern Selection Guide

| Problem you have | Pattern to use |
|---|---|
| Only one instance should exist | Singleton |
| Object creation logic is complex | Factory / Abstract Factory |
| Behavior should be swappable at runtime | Strategy |
| One object needs to notify many others | Observer |
| New features need to wrap existing behavior | Decorator |
| Need to navigate a tree of objects uniformly | Composite |
| Step-by-step algorithm with customizable steps | Template Method |
| Object passes through stages/handlers | Chain of Responsibility |
| Undo/redo of operations | Command |
| Stateful behavior that changes per state | State |

### How to mention patterns naturally (not mechanically)

DON'T say: "I will now apply the Strategy pattern."

DO say: "The fee calculation needs to change depending on pricing tier — hourly, flat, weekend discount. Rather than a big if/else here, I'd pull this out into a PricingStrategy interface so each pricing type is its own class. That's the Strategy pattern — it lets us add new pricing rules without touching ParkingLot."

### Pattern 1: Singleton — ParkingLot

**When to use:** There is physically one parking lot. One instance ensures centralized state.
**When NOT to use:** If you might have multiple lots — remove Singleton, use a Factory.

```java
class ParkingLot {
    private static volatile ParkingLot instance;
    
    private ParkingLot() { /* initialize spots */ }
    
    public static ParkingLot getInstance() {
        if (instance == null) {
            synchronized (ParkingLot.class) {
                if (instance == null) {          // double-checked locking
                    instance = new ParkingLot();
                }
            }
        }
        return instance;
    }
}
```
**Mention:** "The volatile keyword prevents instruction reordering — important for thread safety in double-checked locking."

---

### Pattern 2: Factory — Vehicle Creation

**When to use:** Creation logic varies by type; callers shouldn't know concrete classes.

```java
class VehicleFactory {
    public static Vehicle create(VehicleType type, String plate) {
        switch (type) {
            case CAR:   return new Car(plate);
            case BIKE:  return new Bike(plate);
            case TRUCK: return new Truck(plate);
            default:    throw new IllegalArgumentException("Unknown type: " + type);
        }
    }
}

// Usage — caller only knows VehicleType, not concrete class
Vehicle v = VehicleFactory.create(VehicleType.CAR, "KA-01-AB-1234");
```

---

### Pattern 3: Strategy — Pricing

**When to use:** The algorithm varies (hourly, flat, peak) and must be switchable at runtime.

```java
interface PricingStrategy {
    double calculateFee(double hours);
}

class HourlyPricing implements PricingStrategy {
    private final double ratePerHour;
    
    HourlyPricing(double rate) { this.ratePerHour = rate; }
    
    public double calculateFee(double hours) {
        return Math.ceil(hours) * ratePerHour;  // round up to next hour
    }
}

class FlatRatePricing implements PricingStrategy {
    private final double flatRate;
    
    FlatRatePricing(double rate) { this.flatRate = rate; }
    
    public double calculateFee(double hours) {
        return flatRate;
    }
}

class PeakHoursPricing implements PricingStrategy {
    private final double peakRate;
    private final double offPeakRate;
    
    public double calculateFee(double hours) {
        // simplified: half peak, half off-peak
        return (hours / 2 * peakRate) + (hours / 2 * offPeakRate);
    }
}
```

---

### Pattern 4: Observer — Spot Availability Notification

**When to use:** Multiple systems (display boards, mobile app, reservation system) need to react when a spot opens up.

```java
interface ParkingObserver {
    void onSpotReleased(ParkingSpot spot);
}

class DisplayBoard implements ParkingObserver {
    public void onSpotReleased(ParkingSpot spot) {
        System.out.println("Spot " + spot.getSpotId() + " now available");
    }
}

// In ParkingLot:
class ParkingLot {
    private List<ParkingObserver> observers = new ArrayList<>();
    
    public void addObserver(ParkingObserver obs) { observers.add(obs); }
    
    private void notifyObservers(ParkingSpot spot) {
        observers.forEach(obs -> obs.onSpotReleased(spot));
    }
}
```

---

## Phase 6: Code Key Methods (35-50 min)

### What to implement vs. what to explain verbally

**Implement (write actual code):**
- The most complex method (usually the orchestrator method)
- Any method that involves the design pattern you picked
- Any method with a tricky edge case (concurrency, null handling)

**Explain verbally:**
- Boilerplate getters/setters
- Simple constructors
- Obvious one-liners

**For parking lot — implement these three:**

### Method 1: parkVehicle()

```java
public synchronized Ticket parkVehicle(Vehicle vehicle) {
    if (vehicle == null) {
        throw new IllegalArgumentException("Vehicle cannot be null");
    }
    
    ParkingSpot spot = findAvailableSpot(vehicle.getType());
    if (spot == null) {
        throw new NoSpotAvailableException(
            "No available spot for vehicle type: " + vehicle.getType()
        );
    }
    
    spot.assignVehicle(vehicle);
    
    String ticketId = UUID.randomUUID().toString();
    Ticket ticket = new Ticket(ticketId, vehicle, spot, Instant.now());
    activeTickets.put(ticketId, ticket);
    
    return ticket;
}
```

**Mention:** "I made this synchronized to prevent two threads from assigning the same spot simultaneously. In a distributed system, we'd use distributed locking instead."

### Method 2: findAvailableSpot()

```java
private ParkingSpot findAvailableSpot(VehicleType vehicleType) {
    return spots.values().stream()
        .filter(ParkingSpot::isAvailable)
        .filter(spot -> spot.canFit(vehicleType))
        .findFirst()
        .orElse(null);
}

// In ParkingSpot:
public boolean canFit(VehicleType vehicleType) {
    switch (this.type) {
        case COMPACT:
            return vehicleType == VehicleType.BIKE || vehicleType == VehicleType.CAR;
        case LARGE:
            return true;  // can fit any vehicle
        case HANDICAP:
            return vehicleType == VehicleType.CAR;  // policy decision
        default:
            return false;
    }
}
```

**Mention:** "This is O(n) over all spots. For a large lot, we could maintain separate queues per spot type to make this O(1) — a `Map<SpotType, Queue<ParkingSpot>>`."

### Method 3: exitAndPay()

```java
public double exitAndPay(String ticketId, PaymentMethod paymentMethod) {
    Ticket ticket = activeTickets.get(ticketId);
    if (ticket == null) {
        throw new InvalidTicketException("Ticket not found: " + ticketId);
    }
    
    ticket.markExit(Instant.now());
    double hours = ticket.getDurationHours();
    double fee = pricingStrategy.calculateFee(hours);
    
    Payment payment = new Payment(UUID.randomUUID().toString(), fee, paymentMethod);
    boolean success = payment.process();
    
    if (!success) {
        throw new PaymentFailedException("Payment failed for ticket: " + ticketId);
    }
    
    // Release spot only after successful payment
    ticket.getSpot().removeVehicle();
    activeTickets.remove(ticketId);
    notifyObservers(ticket.getSpot());
    
    return fee;
}

// In Ticket:
public double getDurationHours() {
    long durationMs = Duration.between(entryTime, exitTime).toMillis();
    return durationMs / (1000.0 * 60 * 60);
}
```

### Error Handling Approach

Define custom exceptions — interviewers notice this:
```java
class NoSpotAvailableException extends RuntimeException {
    NoSpotAvailableException(String msg) { super(msg); }
}

class InvalidTicketException extends RuntimeException {
    InvalidTicketException(String msg) { super(msg); }
}

class PaymentFailedException extends RuntimeException {
    PaymentFailedException(String msg) { super(msg); }
}
```

### Concurrency Considerations

Raise these even if not asked:

```
1. parkVehicle() and releaseSpot() both modify spot state
   → synchronized on ParkingLot or use ReentrantLock per spot

2. Multiple readers of activeTickets are fine, but writes need protection
   → Use ConcurrentHashMap<String, Ticket> for activeTickets

3. If ParkingLot is Singleton + multi-threaded:
   → double-checked locking with volatile (shown in Singleton section)

4. If deployed across multiple servers:
   → Distributed lock (Redis SETNX) on spot assignment
   → Idempotency key on ticket creation
```

---

## Phase 7: Trade-offs & Extensions (50-60 min)

### SOLID Principles Checklist (run through this quickly)

**S — Single Responsibility:**
- ParkingSpot only manages spot state (not pricing, not ticketing) ✓
- Ticket only tracks a parking session ✓
- PricingStrategy only calculates fee ✓

**O — Open/Closed:**
- New vehicle type: add a class, no existing code changes ✓
- New pricing model: add a PricingStrategy implementation ✓

**L — Liskov Substitution:**
- Car/Bike/Truck can replace Vehicle anywhere Vehicle is used ✓
- HourlyPricing/FlatRatePricing are interchangeable via interface ✓

**I — Interface Segregation:**
- PricingStrategy has one focused method — not a god interface ✓

**D — Dependency Inversion:**
- ParkingLot depends on PricingStrategy interface, not HourlyPricing ✓
- Easy to inject mock PricingStrategy in tests ✓

### Extension Q&A Template

**Q: How would you add a reservation system?**
> Add a `Reservation` class with `reservationTime`, `vehicleType`, `spotType`, `customerId`. Add a `reserveSpot()` method to ParkingLot that pre-allocates a spot and marks it as reserved (not available for walk-ins). `findAvailableSpot()` skips reserved spots.

**Q: What if we have multiple parking lots in different cities?**
> Remove the Singleton. Create a `ParkingLotFactory` or a `ParkingLotRegistry` that manages multiple instances keyed by locationId. Each ParkingLot gets a `location: Location` field.

**Q: How would you handle electric vehicles needing charging spots?**
> Add `EV` to VehicleType, `CHARGING` to SpotType. Add `ChargingSpot extends ParkingSpot` with a `chargerType` attribute. The `canFit()` logic on ChargingSpot handles EV compatibility.

**Q: How do you make fee calculation time-of-day aware?**
> The PricingStrategy already handles this — add `TimeAwarePricing implements PricingStrategy` that checks the entry/exit hour and applies different rates. ParkingLot passes the ticket's timestamps to `calculateFee()`.

---

## Pattern Quick Reference

| Problem | Pattern | Parking Lot Example |
|---|---|---|
| Enforce single instance | Singleton | ParkingLot manager |
| Encapsulate creation logic | Factory | VehicleFactory |
| Swap algorithms at runtime | Strategy | PricingStrategy |
| Notify multiple dependents | Observer | DisplayBoard, MobileApp |
| Add behavior without subclassing | Decorator | LoggingParkingLot wraps ParkingLot |
| Tree of objects, uniform interface | Composite | Floor → Row → Spot |
| Request passes through handlers | Chain of Responsibility | Payment → validation → processing → logging |
| Encapsulate a request as object | Command | ParkCommand, ExitCommand (for undo) |
| Object behavior varies by state | State | Spot: Available/Occupied/Reserved/Maintenance |
| Fixed algorithm, customizable steps | Template Method | FeeCalculator with abstract getRate() |

---

## Common Traps in LLD Interviews

1. **Coding before designing** — drawing classes on the whiteboard takes 5 minutes; refactoring code takes 15
2. **Over-using Singleton** — using it for everything signals copy-paste thinking; justify it or skip it
3. **Missing extensibility** — hard-coding `if (type == CAR)` everywhere; use polymorphism
4. **Forgetting concurrency** — any system accessed by multiple threads needs at least one mention of synchronization
5. **Not writing custom exceptions** — `throw new RuntimeException("error")` signals junior code
6. **God classes** — ParkingLot doing pricing, spot finding, payment processing, AND notifications; split responsibilities
7. **Using getters/setters for everything** — violates encapsulation; tell objects to do things, don't pull data out and do it yourself
8. **Ignoring null handling** — `findAvailableSpot()` returns null; if you don't handle it, `parkVehicle()` will NPE
9. **Pattern name-dropping without justification** — "I used Strategy here" without explaining why or what problem it solves
10. **Not thinking about testing** — Singleton makes testing hard; if asked, say "I'd add a reset method or use dependency injection in tests"

---

## Quick Verbal Template for Opening Answer

When you first hear the problem, say this before writing anything:

> "Let me start by identifying the actors and core use cases. Then I'll extract the main classes, define their relationships, and we can look at which design patterns apply naturally. I'll code the 2-3 most interesting methods once we agree on the design. Sound good?"

This signals process, buys you thinking time, and aligns with what interviewers actually want to see.
