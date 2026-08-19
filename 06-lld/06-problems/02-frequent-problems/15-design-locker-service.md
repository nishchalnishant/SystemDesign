> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design an Amazon Hub / Locker Service — an inventory management problem focused on optimally matching package sizes to container sizes.
>
> **Key concepts:**
> - Core Entities: `LockerFacility`, `Locker` (Enum sizes: S, M, L, XL), `Package` (S, M, L, XL), `Order`, `CodeGenerator`.
> - The Matching Algorithm: A package can fit in a locker of the *same* size or any *larger* size. You want to assign the smallest available locker that fits the package to preserve large lockers for large packages.
> - Strategy Pattern: `LockerAssignmentStrategy` encapsulates the logic. (e.g., `OptimalFitStrategy`).
> - Workflow: Delivery agent arrives -> requests locker for package size M -> system finds optimal locker -> opens locker -> generates 6-digit pickup code -> sends to user.
> - Expiration: Lockers are held for ~3 days. A background job must sweep expired lockers, refund the user, and mark the locker available.
>
> **Key takeaway:** The core algorithm is simple but easy to mess up. A Small package fits S, M, L, XL. A Large package only fits L, XL. Use a sorted mapping or Enums with size comparators to handle this cleanly.

---
module: 06-lld
topic: Problems
status: unread
tags: [06-lld, lld, locker-service, strategy, inheritance, geohash]
---
# Design Locker Service

> **Difficulty**: Medium
> **Asked at**: Amazon, DoorDash, Shopify
> **Key Patterns**: Strategy (size matching), Inheritance (locker sizes), Factory

---

## Understanding the Problem

Design a package locker service (like Amazon Locker) where customers can pick up deliveries. Couriers drop packages into available lockers, customers get a code to retrieve them.

---

## Clarifying Questions

**You**: "How many locker sizes do we support?"
**Interviewer**: "Small, Medium, Large."

**You**: "How does a customer retrieve a package — QR code, PIN?"
**Interviewer**: "6-digit PIN that expires after pickup."

**You**: "Can a locker hold more than one package?"
**Interviewer**: "No — one package per locker."

**You**: "How long can a package stay before expiry?"
**Interviewer**: "3 days. After that, the locker is freed and the package returned."

**You**: "Do we need geo-search to find the nearest locker station?"
**Interviewer**: "Discuss it, but focus on locker assignment and retrieval first."

---

## Final Requirements

**In scope:**
1. Assign an available locker of the right size (or larger) to a package
2. Generate a unique PIN for the customer
3. Customer enters PIN to retrieve package; locker frees
4. Locker expiry after 3 days frees the locker
5. Handle no available locker gracefully

**Out of scope:**
- Geo-search (follow-up)
- Payment
- Notifications
- Real-time locker hardware integration

---

## Core Entities and Relationships

| Entity | Responsibility |
|--------|---------------|
| `LockerStation` | A physical location with a collection of lockers |
| `Locker` | One slot — size, status (AVAILABLE/OCCUPIED), current package |
| `Package` | size, tracking ID, delivery details |
| `LockerAssignment` | PIN, locker, package, expiry time |
| `LockerSize` | Enum: SMALL, MEDIUM, LARGE |
| `LockerStatus` | Enum: AVAILABLE, OCCUPIED, EXPIRED |
| `LockerService` | Assigns lockers, verifies PINs, handles expiry |

`LockerService` owns all `LockerStation`s. Assignment creates a `LockerAssignment` (PIN, expiry). Retrieval verifies PIN and clears the locker.

---

## Class Design

### Locker

| Requirement | What Locker must track |
|-------------|----------------------|
| "One package per locker" | package: Optional[Package], status: LockerStatus |
| "Size matching" | size: LockerSize |

```
class Locker:
- id: str
- size: LockerSize
- status: LockerStatus
- current_package: Optional[Package]

+ is_available() -> bool
+ assign(package: Package)
+ release()
+ get_size() -> LockerSize
```

### LockerAssignment

```
class LockerAssignment:
- pin: str          # 6-digit
- locker: Locker
- package: Package
- assigned_at: datetime
- expires_at: datetime
- retrieved: bool
```

### LockerService

```
class LockerService:
- stations: list[LockerStation]
- assignments: dict[str, LockerAssignment]  # pin → assignment
- pin_generator: PINGenerator

+ assign_locker(package: Package, station_id: str) -> Optional[LockerAssignment]
+ retrieve_package(pin: str) -> Optional[Package]
+ expire_stale_assignments()
+ find_nearest_station(lat: float, lon: float) -> LockerStation
```

---

## Implementation

### Core Method: `assign_locker`

**Core logic:**
1. Find the station by ID
2. Find smallest available locker that fits the package size
3. Assign package to locker; generate PIN; set expiry
4. Store PIN → assignment mapping
5. Return assignment (customer gets PIN via separate notification)

**Edge cases:**
- No locker of appropriate size available → return None
- Package size larger than LARGE → reject (out of scope)

```java
private static final List<LockerSize> SIZE_ORDER = List.of(
    LockerSize.SMALL, LockerSize.MEDIUM, LockerSize.LARGE
);

public Optional<LockerAssignment> assignLocker(Package pkg, String stationId) {
    Optional<LockerStation> stationOpt = getStation(stationId);
    if (stationOpt.isEmpty()) {
        return Optional.empty();
    }
    LockerStation station = stationOpt.get();

    Optional<Locker> lockerOpt = findBestLocker(station, pkg.getSize());
    if (lockerOpt.isEmpty()) {
        return Optional.empty();
    }
    Locker locker = lockerOpt.get();

    String pin = pinGenerator.generate();
    LocalDateTime expiresAt = LocalDateTime.now().plusDays(3);

    locker.assign(pkg);
    LockerAssignment assignment = new LockerAssignment(
        pin,
        locker,
        pkg,
        LocalDateTime.now(),
        expiresAt,
        false
    );
    assignments.put(pin, assignment);
    return Optional.of(assignment);
}

private Optional<Locker> findBestLocker(LockerStation station, LockerSize requiredSize) {
    int requiredIndex = SIZE_ORDER.indexOf(requiredSize);
    // Try each size from required upwards (smallest fit)
    for (LockerSize size : SIZE_ORDER.subList(requiredIndex, SIZE_ORDER.size())) {
        for (Locker locker : station.getLockers()) {
            if (locker.getSize() == size && locker.isAvailable()) {
                return Optional.of(locker);
            }
        }
    }
    return Optional.empty();
}
```

### Core Method: `retrieve_package`

```java
public Package retrievePackage(String pin) {
    LockerAssignment assignment = assignments.get(pin);
    if (assignment == null) {
        throw new InvalidPINError("PIN not found");
    }
    if (assignment.isRetrieved()) {
        throw new InvalidPINError("Package already retrieved");
    }
    if (LocalDateTime.now().isAfter(assignment.getExpiresAt())) {
        expireAssignment(assignment);
        throw new ExpiredPINError("PIN has expired");
    }

    Package pkg = assignment.getPackage();
    assignment.getLocker().release();
    assignment.setRetrieved(true);
    assignments.remove(pin);
    return pkg;
}
```

### Core Method: `expire_stale_assignments`

```java
public void expireStaleAssignments() {
    LocalDateTime now = LocalDateTime.now();
    List<String> expiredPins = new ArrayList<>();
    for (Map.Entry<String, LockerAssignment> entry : assignments.entrySet()) {
        LockerAssignment a = entry.getValue();
        if (now.isAfter(a.getExpiresAt()) && !a.isRetrieved()) {
            expiredPins.add(entry.getKey());
        }
    }
    for (String pin : expiredPins) {
        expireAssignment(assignments.get(pin));
        assignments.remove(pin);
    }
}

private void expireAssignment(LockerAssignment assignment) {
    assignment.getLocker().release();
    // Trigger return-to-sender flow (out of scope here)
}
```

---

## Verification

```
Station S1: lockers [S1 (SMALL), M1 (MEDIUM), L1 (LARGE)]
Package P1: size=MEDIUM, tracking_id="TRK001"

assign_locker(P1, "S1"):
  _find_best_locker: required=MEDIUM → check MEDIUM first
    M1: size=MEDIUM, AVAILABLE → found!
  pin = "482910", expires = now + 3 days
  M1.assign(P1), M1.status = OCCUPIED
  assignments["482910"] = LockerAssignment(...)

retrieve_package("482910"):
  assignment = assignments["482910"] ✓
  not retrieved ✓, not expired ✓
  package = P1
  M1.release() → M1.status = AVAILABLE
  assignment.retrieved = True
  del assignments["482910"]
  return P1
```

---

## Deep Dive & Extensibility

### 1. "How would you add geo-search for nearest locker station?"

Use geohash to index stations by location. A geohash encodes (lat, lon) into a string where prefix similarity = geographic proximity.

```java
class LockerStationIndex {
    private final Map<String, List<LockerStation>> geohashToStations = new HashMap<>();

    public void addStation(LockerStation station) {
        String gh = Geohash.encode(station.getLat(), station.getLon(), 6);
        geohashToStations
            .computeIfAbsent(gh, k -> new ArrayList<>())
            .add(station);
    }

    public List<LockerStation> findNearest(double lat, double lon, int k) {
        String gh = Geohash.encode(lat, lon, 6);
        // Check exact prefix, then neighbors
        List<LockerStation> candidates = new ArrayList<>();
        List<String> cells = new ArrayList<>(Geohash.neighbors(gh));
        cells.add(gh);
        for (String neighbor : cells) {
            candidates.addAll(geohashToStations.getOrDefault(neighbor, List.of()));
        }
        // Sort by actual Haversine distance
        candidates.sort(Comparator.comparingDouble(
            s -> haversine(lat, lon, s.getLat(), s.getLon())
        ));
        return candidates.subList(0, Math.min(k, candidates.size()));
    }
}
```

### 2. "What if the locker station has hundreds of lockers — how do you find available quickly?"

Track available lockers per size in separate queues:

```java
class LockerStation {
    private final Map<LockerSize, Deque<Locker>> available = new EnumMap<>(LockerSize.class);

    public LockerStation() {
        available.put(LockerSize.SMALL, new ArrayDeque<>());
        available.put(LockerSize.MEDIUM, new ArrayDeque<>());
        available.put(LockerSize.LARGE, new ArrayDeque<>());
    }

    public Optional<Locker> getAvailable(LockerSize size) {
        Deque<Locker> queue = available.get(size);
        return queue.isEmpty() ? Optional.empty() : Optional.of(queue.pollFirst());
    }

    public void returnLocker(Locker locker) {
        available.get(locker.getSize()).offerFirst(locker);
    }
}
```

Assignment is now O(1) per size tier — no scanning all lockers.

### 3. "How do you generate unique PINs that don't collide?"

```java
class PINGenerator {
    private final Map<String, LockerAssignment> store; // the assignments map
    private final SecureRandom random = new SecureRandom();

    public PINGenerator(Map<String, LockerAssignment> store) {
        this.store = store;
    }

    public String generate() {
        for (int i = 0; i < 10; i++) {
            String pin = String.format("%06d", random.nextInt(1_000_000));
            if (!store.containsKey(pin)) {
                return pin;
            }
        }
        throw new RuntimeException("Could not generate unique PIN");
    }
}
```

6 digits = 1M combinations. At any time there are far fewer active assignments, so collision is negligible. For higher security, use `secrets.token_hex(3)` (6 hex chars).

### 4. "How would you handle concurrent locker assignment?"

Two couriers could simultaneously pick the same locker. Fix: optimistic locking on the locker's status.

```java
class Locker {
    private final ReentrantLock lock = new ReentrantLock();
    private Package currentPackage;
    private LockerStatus status;

    public void assign(Package pkg) {
        lock.lock();
        try {
            if (!isAvailable()) {
                throw new LockerOccupiedError();
            }
            this.currentPackage = pkg;
            this.status = LockerStatus.OCCUPIED;
        } finally {
            lock.unlock();
        }
    }
}
```

Or use a DB-level compare-and-swap: `UPDATE lockers SET status='OCCUPIED' WHERE id=? AND status='AVAILABLE'`. Check affected rows == 1; if 0, retry with another locker.

---

## Interviewer Questions by Level

**Junior**: Locker, Package, LockerService. Assign by size. Generate a PIN. Retrieve and release. May miss expiry and concurrency.

**Mid-level**: Best-fit size matching (smallest locker that fits). PIN generation without collision. Expiry background job. Thread-safety for assignment.

**Senior**: Geohash for geo-search. O(1) locker lookup via per-size queues. CAS or DB-level locking for concurrent assignment. PIN security (entropy analysis).

---

## Common Interview Questions

- **Q**: Why assign the smallest locker that fits rather than always using LARGE?
  **A**: Resource efficiency — a SMALL package in a LARGE locker wastes space that could serve a large package. Best-fit minimizes waste.

- **Q**: How do you prevent two couriers from getting the same locker?
  **A**: Lock on the locker's status during assignment. In a distributed system, use DB CAS: `UPDATE ... WHERE status='AVAILABLE'`. If rows affected == 0, pick another locker.

- **Q**: What happens when a PIN expires?
  **A**: A background job runs periodically, finds assignments older than 3 days, calls `locker.release()`, and initiates return-to-sender. The locker becomes available again.

- **Q**: How would you notify the customer that their package is ready?
  **A**: Observer pattern — after `assign_locker` succeeds, fire a `PackageAssigned` event. A `NotificationService` subscriber sends SMS/email with the PIN and station address.

- **Q**: Why geohash for nearest station search?
  **A**: Geohash encodes location as a string where lexicographic proximity approximates geographic proximity. Prefix search + neighbor cells gives all stations within ~1–5 km without scanning all stations.

---

## Related

**Patterns applied here**

- [Factory Pattern](../../03-design-patterns/01-creational/factory-pattern.md)
- [Observer Pattern](../../03-design-patterns/03-behavioral/observer-pattern.md)
- [Strategy Pattern](../../03-design-patterns/03-behavioral/strategy-pattern.md)

**SOLID focus**: [Single Responsibility](../../02-solid-principles/01-single-responsibility.md) · [Open/Closed](../../02-solid-principles/02-open-closed.md)

**Concurrency**: [Concurrency Patterns](../../04-concurrency/concurrency-patterns.md)

**Practice next**

- [Design Elevator System](09-design-elevator-system.md)
- [Design Hotel Management](11-design-hotel-management.md)

Allocation of a scarce physical resource is the common core.

**Frameworks**: [LLD Template](../../../07-interview-templates/01-frameworks/02-lld-template.md) · [UML Diagrams](../../01-oop-fundamentals/uml-diagrams.md) · [OOP Four Pillars](../../01-oop-fundamentals/four-pillars.md)
