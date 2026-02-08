# LLD Interview Template

> **Step-by-step approach for Low-Level Design interviews (45-60 minutes)**

## Interview Timeline

| Phase | Time | Activity |
|-------|------|----------|
| **1. Requirements** | 0-5 min | Clarify problem, identify actors, core features |
| **2. Use Cases** | 5-10 min | Define primary use cases & user flows |
| **3. Class Diagram** | 10-25 min | Design classes, relationships, attributes |
| **4. Design Patterns** | 25-35 min | Apply patterns, justify choices |
| **5. Code Key Methods** | 35-50 min | Implement critical functionality |
| **6. Discussion** | 50-60 min | Trade-offs, extensions, edge cases |

---

## Phase 1: Requirements Gathering (0-5 min)

### Goals
- ✅ Understand the problem scope
- ✅ Identify actors (users, systems)
- ✅ List core features
- ❌ **Don't**: Jump into classes before understanding requirements

###Template Questions

```
1. Who are the actors? (Admin, User, System)
2. What are the must-have features? (Top 3-5)
3. What can be deprioritized?
4. Any constraints? (Language, concurrency, scale)
```

**Example: Design a Parking Lot System**

```
Actors:
- Customer (parks car)
- Attendant (issues tickets)
- Admin (configures spots)

Core Features:
✅ Must-have:
  - Park vehicle (car, bike, truck)
  - Calculate fee based on time
  - Find available spot
  
Should-have:
  - Different spot types (compact, large, handicap)
  - Payment processing
  
Out-of-scope:
  - Real-time spot availability UI
  - Reservation system
```

---

## Phase 2: Use Cases (5-10 min)

### Define Primary Use Cases

**Template:**
```
UC1: Park Vehicle
Actor: Customer
Flow:
  1. Customer arrives with vehicle
  2. System checks for available spot
  3. System assigns spot based on vehicle type
  4. System issues ticket
  5. Customer receives ticket

UC2: Calculate Fee
Actor: System
Flow:
  1. Customer returns to exit
  2. System retrieves ticket
  3. System calculates parking duration
  4. System computes fee (rate × hours)
  5. Customer pays
  6. System releases spot
```

**Identify Edge Cases:**
```
- What if no spots available?
- What if customer loses ticket?
- What if payment fails?
- What if customer exceeds max time?
```

---

## Phase 3: Class Diagram (10-25 min)

### Step 1: Identify Nouns (Entities)

From requirements, extract nouns:
- Parking Lot
- Parking Spot
- Vehicle (Car, Bike, Truck)
- Ticket
- Payment

### Step 2: Define Classes

**Core Classes:**

```java
class ParkingLot {
    - List<Floor> floors
    - Map<String, ParkingSpot> spots
    + ParkingLot(int numFloors, int spotsPerFloor)
    + Ticket parkVehicle(Vehicle vehicle)
    + double calculateFee(Ticket ticket)
    + void releaseSpot(Ticket ticket)
    + ParkingSpot findAvailableSpot(VehicleType type)
}

abstract class Vehicle {
    - String licensePlate
    - VehicleType type
    + Vehicle(String licensePlate, VehicleType type)
    + String getLicensePlate()
    + VehicleType getType()
}

class Car extends Vehicle {
    + Car(String licensePlate)
}

class Bike extends Vehicle {
    + Bike(String licensePlate)
}

class Truck extends Vehicle {
    + Truck(String licensePlate)
}

enum VehicleType {
    BIKE, CAR, TRUCK
}

class ParkingSpot {
    - String spotId
    - SpotType type
    - boolean isOccupied
    - Vehicle vehicle
    + ParkingSpot(String spotId, SpotType type)
    + boolean isAvailable()
    + void assignVehicle(Vehicle vehicle)
    + void removeVehicle()
}

enum SpotType {
    COMPACT,    // Bike, Car
    LARGE,      // Car, Truck
    HANDICAP    // Any
}

class Ticket {
    - String ticketId
    - Vehicle vehicle
    - ParkingSpot spot
    - Date entryTime
    - Date exitTime
    + Ticket(String ticketId, Vehicle vehicle, ParkingSpot spot)
    + void markExit(Date exitTime)
    + double getDurationHours()
}

class Payment {
    - double amount
    - PaymentMethod method
    - PaymentStatus status
    + Payment(double amount, PaymentMethod method)
    + boolean process()
}

enum PaymentMethod {
    CASH, CREDIT_CARD, UPI
}

enum PaymentStatus {
    PENDING, SUCCESS, FAILED
}
```

### Step 3: Define Relationships

**Relationships:**
```
ParkingLot → has many → ParkingSpot (Composition)
ParkingLot → issues → Ticket (Association)
ParkingSpot → occupied by → Vehicle (Association)
Ticket → references → Vehicle, ParkingSpot (Association)
Car, Bike, Truck → extends → Vehicle (Inheritance)
```

**UML Diagram (text representation):**
```
┌─────────────────┐
│  ParkingLot     │
├─────────────────┤
│ - floors        │
│ - spots         │
├─────────────────┤
│ + parkVehicle() │
│ + calculateFee()│
└────────┬────────┘
         │ composes
         ▼
┌─────────────────┐       ┌──────────────┐
│  ParkingSpot    │◄──────│   Ticket     │
├─────────────────┤       ├──────────────┤
│ - spotId        │       │ - ticketId   │
│ - type          │       │ - entryTime  │
│ - isOccupied    │       │ - exitTime   │
├─────────────────┤       └──────┬───────┘
│ + isAvailable() │              │ references
│ + assignVehicle()│              ▼
└────────┬────────┘       ┌──────────────┐
         │ assigns        │   Vehicle    │◄───┐
         └───────────────▶├──────────────┤    │
                          │ - plate      │    │
                          │ - type       │    │
                          └──────▲───────┘    │
                                 │            │
                    ┌────────────┼────────────┤
                    │            │            │
              ┌─────▼────┐ ┌────▼────┐ ┌────▼────┐
              │   Car    │ │  Bike   │ │  Truck  │
              └──────────┘ └─────────┘ └─────────┘
```

---

## Phase 4: Design Patterns (25-35 min)

### Common Patterns for LLD

**1. Singleton (Parking Lot Manager)**
```java
class ParkingLot {
    private static ParkingLot instance;
    private ParkingLot() { /* private constructor */ }
    
    public static ParkingLot getInstance() {
        if (instance == null) {
            synchronized (ParkingLot.class) {
                if (instance == null) {
                    instance = new ParkingLot();
                }
            }
        }
        return instance;
    }
}
```
**Why**: Only one parking lot instance needed.

---

**2. Factory (Vehicle Creation)**
```java
class VehicleFactory {
    public static Vehicle createVehicle(VehicleType type, String plate) {
        switch (type) {
            case CAR:
                return new Car(plate);
            case BIKE:
                return new Bike(plate);
            case TRUCK:
                return new Truck(plate);
            default:
                throw new IllegalArgumentException("Unknown vehicle type");
        }
    }
}

// Usage
Vehicle car = VehicleFactory.createVehicle(VehicleType.CAR, "ABC123");
```
**Why**: Encapsulate object creation logic.

---

**3. Strategy (Fee Calculation)**
```java
interface PricingStrategy {
    double calculateFee(double hours);
}

class HourlyPricing implements PricingStrategy {
    private double ratePerHour;
    
    public HourlyPricing(double rate) {
        this.ratePerHour = rate;
    }
    
    public double calculateFee(double hours) {
        return hours * ratePerHour;
    }
}

class FlatRatePricing implements PricingStrategy {
    private double flatRate;
    
    public FlatRatePricing(double rate) {
        this.flatRate = rate;
    }
    
    public double calculateFee(double hours) {
        return flatRate;  // Ignore hours
    }
}

// Usage
PricingStrategy strategy = new HourlyPricing(10.0);
double fee = strategy.calculateFee(ticket.getDurationHours());
```
**Why**: Different pricing strategies (hourly, flat, weekend discounts).

---

**4. Observer (Spot Availability Notification)**
```java
interface ParkingObserver {
    void onSpotAvailable(ParkingSpot spot);
}

class ParkingLot {
    private List<ParkingObserver> observers = new ArrayList<>();
    
    public void addObserver(ParkingObserver observer) {
        observers.add(observer);
    }
    
    public void releaseSpot(Ticket ticket) {
        ParkingSpot spot = ticket.getSpot();
        spot.removeVehicle();
        notifyObservers(spot);
    }
    
    private void notifyObservers(ParkingSpot spot) {
        for (ParkingObserver observer : observers) {
            observer.onSpotAvailable(spot);
        }
    }
}

// Usage
ParkingLot lot = ParkingLot.getInstance();
lot.addObserver(new DisplayBoard());  // Updates display when spot free
```
**Why**: Notify systems (display boards, mobile apps) when spots become available.

---

## Phase 5: Code Key Methods (35-50 min)

### Implementation

**parkVehicle():**
```java
public Ticket parkVehicle(Vehicle vehicle) throws NoSpotAvailableException {
    ParkingSpot spot = findAvailableSpot(vehicle.getType());
    if (spot == null) {
        throw new NoSpotAvailableException("No spot available for " + vehicle.getType());
    }
    
    spot.assignVehicle(vehicle);
    String ticketId = generateTicketId();
    Ticket ticket = new Ticket(ticketId, vehicle, spot, new Date());
    activeTickets.put(ticketId, ticket);
    return ticket;
}
```

**findAvailableSpot():**
```java
private ParkingSpot findAvailableSpot(VehicleType vehicleType) {
    for (ParkingSpot spot : spots.values()) {
        if (spot.isAvailable() && spot.canFit(vehicleType)) {
            return spot;
        }
    }
    return null;
}

// In ParkingSpot class
public boolean canFit(VehicleType vehicleType) {
    switch (this.type) {
        case COMPACT:
            return vehicleType == VehicleType.BIKE || vehicleType == VehicleType.CAR;
        case LARGE:
            return true;  // Can fit any vehicle
        case HANDICAP:
            return vehicleType == VehicleType.CAR;
        default:
            return false;
    }
}
```

**calculateFee():**
```java
public double calculateFee(Ticket ticket) {
    ticket.markExit(new Date());
    double hours = ticket.getDurationHours();
    PricingStrategy strategy = getPricingStrategy();  // Could vary by time of day
    return strategy.calculateFee(hours);
}

// In Ticket class
public double getDurationHours() {
    long durationMs = exitTime.getTime() - entryTime.getTime();
    return durationMs / (1000.0 * 60 * 60);  // Convert ms to hours
}
```

---

## Phase 6: Discussion (50-60 min)

### Trade-offs

**Q: Why use Strategy pattern for pricing?**
> A: Allows flexible pricing (hourly, flat, peak/off-peak) without modifying ParkingLot class. Easy to add new strategies (member discounts, weekend rates).

**Q: Why Singleton for ParkingLot?**
> A: Only one parking lot instance needed. Ensures centralized state management. However, makes testing harder (can inject mock instead).

**Q: How to handle concurrency (multiple customers parking simultaneously)?**
> A: Use synchronized blocks or locks when assigning spots:
```java
public synchronized Ticket parkVehicle(Vehicle vehicle) {
    // ... atomic spot assignment
}
```

### Extensions

**Q: How would you add a reservation system?**
> A: Add `Reservation` class with `reservationTime`, `vehicle`, `spotType`. Modify `findAvailableSpot()` to check reserved spots.

**Q: What if we need multiple parking lots in different cities?**
> A: Remove Singleton, use Factory to create ParkingLot instances. Add `Location` field.

---

## SOLID Principles Checklist

**S - Single Responsibility**
- ✅ ParkingSpot only manages spot state
- ✅ Ticket only tracks parking session
- ✅ PricingStrategy only calculates fees

**O - Open/Closed**
- ✅ Can add new VehicleTypes without modifying ParkingSpot
- ✅ Can add new PricingStrategies without modifying Ticket

**L - Liskov Substitution**
- ✅ Car, Bike, Truck can replace Vehicle without breaking code

**I - Interface Segregation**
- ✅ PricingStrategy interface has only one method (focused)

**D - Dependency Inversion**
- ✅ ParkingLot depends on PricingStrategy interface, not concrete implementations

---

## Common LLD Problems

1. **Parking Lot** (this example)
2. **Elevator System**
3. **Library Management**
4. **ATM Design**
5. **Chess Game**
6. **Hotel Booking System**
7. **Vending Machine**
8. **Online Shopping Cart**
9. **Snake & Ladder**
10. **Tic-Tac-Toe**

---

## Interview Tips

**Do's:**
- ✅ Ask clarifying questions
- ✅ Start with simple classes, then refine
- ✅ Explain design pattern choices
- ✅ Code at least 2-3 key methods
- ✅ Use meaningful names (not FooManager, BarService)

**Don'ts:**
- ❌ Don't code before designing classes
- ❌ Don't over-engineer (avoid 10 patterns for simple problem)
- ❌ Don't forget edge cases (null checks, concurrency)
- ❌ Don't use getters/setters for everything (violates encapsulation)

**Example Answer Template:**
> "I'll design a parking lot with classes for ParkingLot, ParkingSpot, Vehicle (with Car/Bike/Truck subclasses), and Ticket. I'll use **Factory pattern** to create vehicles, **Strategy pattern** for flexible pricing, and **Observer pattern** to notify when spots become available. The key methods are parkVehicle() which finds an available spot atomically, and calculateFee() which computes cost based on the pricing strategy."
