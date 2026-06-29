---
module: 06-lld
topic: Problems
status: unread
tags: [06-lld, system-design, problems]
---
# Design Locker Service (Amazon Locker)

> **Difficulty**: Medium
> **Topics**: Strategy Pattern, State Pattern, Allocation Algorithms
> **Extension**: Geo-spatial search, expiration job, returns handling

---

## Opening Analogy

Amazon package lockers are standalone kiosks at grocery stores and apartment lobbies. Each kiosk has a fixed set of compartments in three sizes. When you place an order, Amazon's system finds the nearest kiosk that has a free compartment big enough for your package. When the delivery driver deposits the package, you get a one-time 6-digit code. Enter the code at the kiosk, the right door opens, you take your package, and the slot becomes free again. If you do not pick up within 3 days, the package goes back and the slot is freed.

The key design challenges: **allocation by size** (do not waste a large compartment on a small package), **one-time code security** (code expires after single use), and **timeout release** (idle lockers must be reclaimed).

---

## Phase 1: Requirements

### Functional
- Assign the best-fit locker slot for a package at the nearest available kiosk.
- Generate a unique, one-time 6-digit access code per assignment.
- Allow customer to unlock their specific slot by entering the code.
- Auto-release slots whose package has not been collected after 3 days.
- Support sizes: SMALL, MEDIUM, LARGE, XL.
- Delivery agent marks slot as occupied after depositing the package.

### Non-Functional
- Allocation must be thread-safe — two concurrent orders must not receive the same slot.
- Code validation must be O(1).
- Best-fit allocation must not waste locker sizes unnecessarily.

---

## Phase 2: Use Cases

### Actors
- **Customer** — selects locker pickup at checkout, later enters code to retrieve package.
- **Delivery Agent** — deposits package into assigned slot, confirms deposit.
- **System** — assigns slot, generates code, sends notification, manages expiration.

### UC1: Assign Locker (Order Placement)
1. Customer selects locker pickup and provides their zip/location.
2. System determines package size from item dimensions.
3. System sorts nearby kiosks by distance.
4. For each kiosk, system finds the smallest available slot that fits the package (Best Fit).
5. System reserves the slot (marks as `RESERVED`).
6. System returns the kiosk address and locker ID to customer.

### UC2: Deposit Package
1. Delivery agent arrives at the assigned kiosk, scans the package barcode.
2. System opens the assigned slot door.
3. Agent places package inside and closes door.
4. System transitions slot from `RESERVED` to `OCCUPIED`.
5. System generates 6-digit code and sends it to the customer.

### UC3: Pickup Package
1. Customer enters 6-digit code at the kiosk touchscreen.
2. System validates code: correct, not expired, slot is `OCCUPIED`.
3. System opens the matching slot.
4. Customer retrieves package.
5. System transitions slot to `FREE` on door close, invalidates the code.

### UC4: Expiration (Cron Job)
1. Nightly job queries all `OCCUPIED` slots where `depositedAt < NOW() - 3 days`.
2. System generates return labels and notifies the delivery service.
3. After confirmed pickup by agent, slot transitions to `FREE`.

---

## Phase 3: Class Diagram

```
┌───────────────────────────────────────┐
│            LockerService              │  <<Facade>>
│───────────────────────────────────────│
│ - kiosks: List<Kiosk>                │
│ - codeRegistry: CodeRegistry         │
│ - allocationStrategy: AllocStrategy  │
│───────────────────────────────────────│
│ + assignLocker(loc, pkgSize): Slot   │
│ + confirmDeposit(slotId): String     │   returns 6-digit code
│ + unlockSlot(code): boolean          │
│ + runExpirationCheck(): void         │
└───────────────────────────────────────┘
             │ holds
    ┌────────┴─────────┐
    ▼                  ▼
┌──────────┐   ┌─────────────────────────────┐
│  Kiosk   │   │  AllocationStrategy         │ <<interface>>
│──────────│   │─────────────────────────────│
│ id       │   │ findSlot(kiosk,size): Slot  │
│ location │   └──────────────┬──────────────┘
│ slots    │            ┌─────┴────────┐
│──────────│            ▼              ▼
│findSlot()│     BestFitStrategy  FirstFitStrategy
└──────────┘

┌──────────────────────────────────────┐
│                Slot                  │
│──────────────────────────────────────│
│ - id: String                         │
│ - size: SlotSize                     │
│ - state: SlotState                   │
│ - currentPackage: Package            │
│ - accessCode: String                 │
│ - depositedAt: Instant               │
│──────────────────────────────────────│
│ + reserve(): void                    │
│ + occupy(pkg): String (returns code) │
│ + unlock(code): boolean              │
│ + release(): void                    │
│ + isExpired(): boolean               │
└──────────────────────────────────────┘

<<enumeration>>    <<enumeration>>
SlotSize           SlotState
────────           ─────────
SMALL              FREE
MEDIUM             RESERVED
LARGE              OCCUPIED
XL                 OUT_OF_SERVICE

┌─────────────────────────────────────┐
│           CodeRegistry              │
│─────────────────────────────────────│
│ - codeToSlot: Map<String, String>   │
│─────────────────────────────────────│
│ + register(code, slotId): void      │
│ + resolve(code): String (slotId)    │
│ + invalidate(code): void            │
└─────────────────────────────────────┘
```

---

## Phase 4: Design Patterns Applied

### 1. Strategy Pattern — Slot allocation algorithm
**Why:** The "find the best slot" logic can legitimately vary. `BestFitStrategy` (smallest slot that fits the package) minimizes wasted space. `FirstFitStrategy` (first available slot big enough) is faster. `RandomStrategy` is useful for load spreading. The Strategy pattern lets `LockerService` accept any algorithm at construction time, without knowing the algorithm's internals.

### 2. State Pattern — Slot lifecycle
**Why:** A slot in `FREE` state can be reserved; a slot in `OCCUPIED` state can be unlocked; a slot in `RESERVED` state should neither be assigned again nor unlocked. Encoding these as an enum with guarded transitions (throwing if invalid action attempted in wrong state) prevents illegal operations cleanly, without the caller needing to check state manually.

### 3. Null Object / Guard Pattern — Code validation
**Why:** `CodeRegistry.resolve(code)` returns `null` if the code does not exist. Callers always check before proceeding. This is simple but correct — the alternative is a checked exception, which is noisier for a common failure path.

---

## Phase 5: Key Implementation

```python
import math
import secrets
from abc import ABC, abstractmethod
from datetime import datetime, timezone, timedelta
from enum import Enum

# ── Size & State Enums ─────────────────────────────────────────────────────

class SlotSize(Enum):
    SMALL  = 0
    MEDIUM = 1
    LARGE  = 2
    XL     = 3

class SlotState(Enum):
    FREE           = "FREE"
    RESERVED       = "RESERVED"
    OCCUPIED       = "OCCUPIED"
    OUT_OF_SERVICE = "OUT_OF_SERVICE"

# ── Package ────────────────────────────────────────────────────────────────

class Package:
    def __init__(self, order_id, customer_id, required_size):
        self.order_id      = order_id
        self.customer_id   = customer_id
        self.required_size = required_size

# ── Slot ───────────────────────────────────────────────────────────────────

class Slot:
    EXPIRY_DAYS = 3

    def __init__(self, id, size):
        self.id               = id
        self.size             = size
        self.state            = SlotState.FREE
        self._current_package = None
        self._access_code     = None
        self._deposited_at    = None

    # Can this slot physically hold a package of the given size?
    def can_fit(self, pkg_size):
        return self.size.value >= pkg_size.value

    # Step 1: Reserve at order time (before package arrives)
    def reserve(self):
        if self.state != SlotState.FREE:
            raise RuntimeError(f"Slot {self.id} not FREE")
        self.state = SlotState.RESERVED

    # Step 2: Delivery agent deposits package → generates and stores one-time code
    def occupy(self, pkg):
        if self.state != SlotState.RESERVED:
            raise RuntimeError(f"Slot {self.id} not RESERVED")
        self._current_package = pkg
        self.state            = SlotState.OCCUPIED
        self._deposited_at    = datetime.now(timezone.utc)
        self._access_code     = self._generate_code()
        return self._access_code

    # Step 3: Customer unlocks with code
    def unlock(self, code):
        if self.state != SlotState.OCCUPIED:
            return False
        if self._access_code != code:
            return False
        self._release()
        return True

    def _release(self):
        self._current_package = None
        self._access_code     = None
        self._deposited_at    = None
        self.state            = SlotState.FREE

    def is_expired(self):
        return (
            self.state == SlotState.OCCUPIED
            and self._deposited_at is not None
            and datetime.now(timezone.utc) - self._deposited_at > timedelta(days=self.EXPIRY_DAYS)
        )

    # Cryptographically random 6-digit code (100000–999999)
    def _generate_code(self):
        return f"{100000 + secrets.randbelow(900000):06d}"

# ── Kiosk ──────────────────────────────────────────────────────────────────

class Location:
    def __init__(self, lat, lng):
        self.lat = lat
        self.lng = lng

    def distance_to(self, other):
        # Simplified Euclidean; production uses Haversine formula
        return math.sqrt((self.lat - other.lat) ** 2 + (self.lng - other.lng) ** 2)

class Kiosk:
    def __init__(self, id, location):
        self.id       = id
        self.location = location
        self._slots   = []

    def add_slot(self, s):
        self._slots.append(s)

    def get_slots(self):
        return list(self._slots)

# ── Allocation Strategy ────────────────────────────────────────────────────

class AllocationStrategy(ABC):
    @abstractmethod
    def find_slot(self, kiosk, package_size): ...

class BestFitStrategy(AllocationStrategy):
    def find_slot(self, kiosk, package_size):
        candidates = [
            s for s in kiosk.get_slots()
            if s.state == SlotState.FREE and s.can_fit(package_size)
        ]
        return min(candidates, key=lambda s: s.size.value, default=None)

# ── Code Registry ──────────────────────────────────────────────────────────

class CodeRegistry:
    def __init__(self):
        # Maps one-time code → slot_id
        self._code_to_slot = {}

    def register(self, code, slot_id):
        self._code_to_slot[code] = slot_id

    def resolve(self, code):
        return self._code_to_slot.get(code)

    def invalidate(self, code):
        self._code_to_slot.pop(code, None)

# ── Locker Service ─────────────────────────────────────────────────────────

class LockerService:
    def __init__(self, strategy):
        self._kiosks       = []
        self._slot_index   = {}
        self._code_registry  = CodeRegistry()
        self._strategy       = strategy

    def add_kiosk(self, kiosk):
        self._kiosks.append(kiosk)
        for s in kiosk.get_slots():
            self._slot_index[s.id] = s

    # UC1 + UC2: Find best slot and reserve it
    def assign_locker(self, user_location, package_size):
        sorted_kiosks = sorted(self._kiosks, key=lambda k: k.location.distance_to(user_location))
        for kiosk in sorted_kiosks:
            slot = self._strategy.find_slot(kiosk, package_size)
            if slot is not None:
                slot.reserve()
                print(f"Assigned slot {slot.id} ({slot.size.name}) at kiosk {kiosk.id}")
                return slot
        print("No available locker found.")
        return None

    # UC2: Delivery agent confirms deposit; returns code for customer
    def confirm_deposit(self, slot_id, pkg):
        slot = self._slot_index.get(slot_id)
        if slot is None:
            raise ValueError(f"Slot not found: {slot_id}")
        code = slot.occupy(pkg)
        self._code_registry.register(code, slot_id)
        print(f"Package deposited. Code sent to customer: {code}")
        return code

    # UC3: Customer enters code
    def unlock_slot(self, code):
        slot_id = self._code_registry.resolve(code)
        if slot_id is None:
            print("Invalid code.")
            return False
        slot   = self._slot_index[slot_id]
        opened = slot.unlock(code)
        if opened:
            self._code_registry.invalidate(code)
            print(f"Slot {slot_id} opened. Package retrieved.")
        else:
            print(f"Failed to unlock slot {slot_id}")
        return opened

    # UC4: Expiration sweep (run by scheduler)
    def run_expiration_check(self):
        for slot in self._slot_index.values():
            if slot.is_expired():
                print(f"Slot {slot.id} expired — flagging for return pickup.")
                # In production: create ReturnRequest, notify delivery service
                # After agent collects: slot._release()

# ── Demo ───────────────────────────────────────────────────────────────────

if __name__ == "__main__":
    service = LockerService(BestFitStrategy())

    kiosk = Kiosk("Kiosk-Downtown", Location(40.712, -74.006))
    kiosk.add_slot(Slot("S1", SlotSize.SMALL))
    kiosk.add_slot(Slot("M1", SlotSize.MEDIUM))
    kiosk.add_slot(Slot("L1", SlotSize.LARGE))
    service.add_kiosk(kiosk)

    # Order placed: MEDIUM package for customer near downtown
    assigned = service.assign_locker(Location(40.714, -74.008), SlotSize.MEDIUM)
    # Best fit: M1 (MEDIUM fits, SMALL does not, LARGE would waste space)

    # Delivery agent deposits package
    pkg  = Package("ORD-001", "cust-42", SlotSize.MEDIUM)
    code = service.confirm_deposit(assigned.id, pkg)

    # Customer picks up
    service.unlock_slot(code)

    # Code reuse attempt → fails
    service.unlock_slot(code)
```

---

## Phase 6: Trade-offs and Extensions

### Trade-offs

| Decision | Choice | Alternative | Reason |
|---|---|---|---|
| Slot allocation | Best Fit (smallest that fits) | First Fit | Best Fit reduces fragmentation; First Fit is faster but wastes large slots |
| Code storage | `CodeRegistry` (in-memory Map) | Stored in `Slot` object | Separate registry enables O(1) lookup by code without scanning all slots |
| Expiration detection | Polling `isExpired()` on all slots | DB query on `deposited_at` | In-memory polling works for LLD; production uses `SELECT WHERE deposited_at < NOW()-3d` |
| Code generation | `SecureRandom` 6-digit | UUID | 6-digit matches real Amazon locker UX; UUID is harder to enter on kiosk keypad |

### Extensions

**Geo-spatial kiosk lookup at scale:**
Instead of linear scan + sort, use a `QuadTree` or `R-Tree` spatial index. Query: "find all kiosks within radius R of user location". Libraries: Google S2, H3 (Uber), PostGIS for DB.

**Refrigerated lockers:**
```python
class RefrigeratedSlot(Slot):
    def __init__(self, id, size):
        super().__init__(id, size)
        self.is_cooling_active = False

    def occupy(self, pkg: Package) -> str:
        code = super().occupy(pkg)
        self.is_cooling_active = True   # activate cooling on deposit
        # Add temperature monitoring here
        return code
```

**Return handling:**
```python
from enum import Enum
from dataclasses import dataclass, field
from datetime import datetime

class ReturnStatus(Enum):
    PENDING   = "PENDING"
    PICKED_UP = "PICKED_UP"

@dataclass
class ReturnRequest:
    slot_id:    str
    order_id:   str
    expired_at: object
    status:     object = ReturnStatus.PENDING

# Scheduler creates ReturnRequest for expired slots
# Delivery agent scans barcode → request.status = ReturnStatus.PICKED_UP → slot._release()
```

**Concurrency at scale:**
For a distributed locker management system, replace `synchronized` on `Slot` with:
```
SELECT slot_id FROM slots WHERE state='FREE' AND size>=? ORDER BY size ASC LIMIT 1
FOR UPDATE SKIP LOCKED;
```
`SKIP LOCKED` allows concurrent allocation requests to skip rows being locked by other transactions, enabling parallel assignment without waiting.

---

## Interviewer Follow-Up Questions

- "What entities does a locker service need?" → `LockerBank` (a group of lockers at a location), `Locker` (size: SMALL/MEDIUM/LARGE, status: AVAILABLE/OCCUPIED/RESERVED, current code), `Reservation` (locker_id, order_id, user_id, pin, expiry, status), `Package`. Key operations: assign locker (find available locker of right size), generate PIN, mark delivered, allow pickup (verify PIN), release locker.
- "Two delivery agents simultaneously try to assign a locker. How do you prevent conflicts?" → Atomic assignment: `UPDATE lockers SET status='RESERVED', reservation_id=X WHERE locker_id=Y AND status='AVAILABLE'`. Only one UPDATE succeeds. Don't SELECT then UPDATE — race condition between the check and the assignment. The DB constraint enforces single-assignment atomically. For distributed systems with multiple locker service instances: the DB is the single source of truth; no distributed lock needed.
- "The customer doesn't pick up within 3 days. How do you reclaim the locker?" → Background job (cron every hour): `SELECT * FROM reservations WHERE status='READY' AND expiry < NOW()`. For each expired reservation: notify the customer (last chance), mark reservation as `EXPIRED`, release the locker (`UPDATE lockers SET status='AVAILABLE'`), trigger return-to-warehouse or re-delivery workflow. Expiry is a timestamp on the reservation — simple to query and enforce.
- "How do you generate and verify the pickup PIN securely?" → PIN generation: `secrets.randbelow(10**6)` (Python) for a 6-digit cryptographically random PIN. Store as a bcrypt hash in the reservation (don't store plaintext). Verification: hash the entered PIN, compare to stored hash. Rate limiting: after 3 wrong attempts, lock the reservation and require agent intervention. Time-bounded: the PIN is only valid during the reservation window. Never log or display the plaintext PIN after generation — it's a one-time credential.
- "The locker door sensor fails — the system thinks the locker is occupied but it's actually empty. How do you detect and recover?" → Sensors + reconciliation: the locker hardware reports door state (open/closed) to the service. If `status=OCCUPIED` but `door_opened=True AND no_pickup_event_received`: alert (anomaly). Periodic physical audit: a technician scans all lockers; reconcile with DB. If DB says occupied but locker is empty: trigger manual investigation, clear the reservation, notify logistics team. Hardware failures require an out-of-band reconciliation process.
