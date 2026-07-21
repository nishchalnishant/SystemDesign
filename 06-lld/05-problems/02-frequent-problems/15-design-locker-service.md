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

```python
SIZE_ORDER = [LockerSize.SMALL, LockerSize.MEDIUM, LockerSize.LARGE]

def assign_locker(self, package, station_id):
    station = self._get_station(station_id)
    if not station:
        return None

    locker = self._find_best_locker(station, package.size)
    if not locker:
        return None

    pin = self.pin_generator.generate()
    expires_at = datetime.now() + timedelta(days=3)

    locker.assign(package)
    assignment = LockerAssignment(
        pin=pin,
        locker=locker,
        package=package,
        assigned_at=datetime.now(),
        expires_at=expires_at,
        retrieved=False
    )
    self.assignments[pin] = assignment
    return assignment

def _find_best_locker(self, station, required_size):
    required_index = SIZE_ORDER.index(required_size)
    # Try each size from required upwards (smallest fit)
    for size in SIZE_ORDER[required_index:]:
        for locker in station.lockers:
            if locker.size == size and locker.is_available():
                return locker
    return None
```

### Core Method: `retrieve_package`

```python
def retrieve_package(self, pin):
    assignment = self.assignments.get(pin)
    if not assignment:
        raise InvalidPINError("PIN not found")
    if assignment.retrieved:
        raise InvalidPINError("Package already retrieved")
    if datetime.now() > assignment.expires_at:
        self._expire_assignment(assignment)
        raise ExpiredPINError("PIN has expired")

    package = assignment.package
    assignment.locker.release()
    assignment.retrieved = True
    del self.assignments[pin]
    return package
```

### Core Method: `expire_stale_assignments`

```python
def expire_stale_assignments(self):
    now = datetime.now()
    expired_pins = [
        pin for pin, a in self.assignments.items()
        if now > a.expires_at and not a.retrieved
    ]
    for pin in expired_pins:
        self._expire_assignment(self.assignments[pin])
        del self.assignments[pin]

def _expire_assignment(self, assignment):
    assignment.locker.release()
    # Trigger return-to-sender flow (out of scope here)
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

```python
import geohash

class LockerStationIndex:
    def __init__(self):
        self.geohash_to_stations = defaultdict(list)

    def add_station(self, station):
        gh = geohash.encode(station.lat, station.lon, precision=6)
        self.geohash_to_stations[gh].append(station)

    def find_nearest(self, lat, lon, k=3):
        gh = geohash.encode(lat, lon, precision=6)
        # Check exact prefix, then neighbors
        candidates = []
        for neighbor in geohash.neighbors(gh) + [gh]:
            candidates.extend(self.geohash_to_stations.get(neighbor, []))
        # Sort by actual Haversine distance
        return sorted(candidates, key=lambda s: haversine(lat, lon, s.lat, s.lon))[:k]
```

### 2. "What if the locker station has hundreds of lockers — how do you find available quickly?"

Track available lockers per size in separate queues:

```python
class LockerStation:
    def __init__(self):
        self.available = {
            LockerSize.SMALL: deque(),
            LockerSize.MEDIUM: deque(),
            LockerSize.LARGE: deque(),
        }

    def get_available(self, size):
        return self.available[size].popleft() if self.available[size] else None

    def return_locker(self, locker):
        self.available[locker.size].appendleft(locker)
```

Assignment is now O(1) per size tier — no scanning all lockers.

### 3. "How do you generate unique PINs that don't collide?"

```python
import secrets

class PINGenerator:
    def __init__(self, store):
        self.store = store  # the assignments dict

    def generate(self):
        for _ in range(10):
            pin = str(secrets.randbelow(1_000_000)).zfill(6)
            if pin not in self.store:
                return pin
        raise RuntimeError("Could not generate unique PIN")
```

6 digits = 1M combinations. At any time there are far fewer active assignments, so collision is negligible. For higher security, use `secrets.token_hex(3)` (6 hex chars).

### 4. "How would you handle concurrent locker assignment?"

Two couriers could simultaneously pick the same locker. Fix: optimistic locking on the locker's status.

```python
import threading

class Locker:
    def __init__(self):
        self._lock = threading.Lock()

    def assign(self, package):
        with self._lock:
            if not self.is_available():
                raise LockerOccupiedError()
            self.current_package = package
            self.status = LockerStatus.OCCUPIED
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

**Frameworks**: [LLD Template](../../../07-interview-templates/01-frameworks/02-lld-template.md) · [UML Diagrams](../../uml-diagrams.md) · [OOP Four Pillars](../../01-oop-fundamentals/four-pillars.md)
