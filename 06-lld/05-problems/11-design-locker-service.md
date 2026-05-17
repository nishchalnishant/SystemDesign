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

## Phase 5: Key Java Implementation

```java
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

// ── Size & State Enums ─────────────────────────────────────────────────────

enum SlotSize  { SMALL, MEDIUM, LARGE, XL }
enum SlotState { FREE, RESERVED, OCCUPIED, OUT_OF_SERVICE }

// ── Package ────────────────────────────────────────────────────────────────

class Package {
    final String orderId;
    final String customerId;
    final SlotSize requiredSize;

    Package(String orderId, String customerId, SlotSize size) {
        this.orderId      = orderId;
        this.customerId   = customerId;
        this.requiredSize = size;
    }
}

// ── Slot ───────────────────────────────────────────────────────────────────

class Slot {
    private final String id;
    private final SlotSize size;
    private SlotState state;
    private Package currentPackage;
    private String accessCode;
    private Instant depositedAt;

    private static final int EXPIRY_DAYS = 3;

    Slot(String id, SlotSize size) {
        this.id    = id;
        this.size  = size;
        this.state = SlotState.FREE;
    }

    String getId()      { return id; }
    SlotSize getSize()  { return size; }
    SlotState getState(){ return state; }

    // Can this slot physically hold a package of the given size?
    boolean canFit(SlotSize pkgSize) {
        return size.ordinal() >= pkgSize.ordinal();
    }

    // Step 1: Reserve at order time (before package arrives)
    synchronized void reserve() {
        if (state != SlotState.FREE) throw new IllegalStateException("Slot " + id + " not FREE");
        state = SlotState.RESERVED;
    }

    // Step 2: Delivery agent deposits package → generates and stores one-time code
    synchronized String occupy(Package pkg) {
        if (state != SlotState.RESERVED)
            throw new IllegalStateException("Slot " + id + " not RESERVED");
        this.currentPackage = pkg;
        this.state          = SlotState.OCCUPIED;
        this.depositedAt    = Instant.now();
        this.accessCode     = generateCode();
        return accessCode;
    }

    // Step 3: Customer unlocks with code
    synchronized boolean unlock(String code) {
        if (state != SlotState.OCCUPIED) return false;
        if (!this.accessCode.equals(code)) return false;
        release();
        return true;
    }

    synchronized void release() {
        currentPackage = null;
        accessCode     = null;
        depositedAt    = null;
        state          = SlotState.FREE;
    }

    boolean isExpired() {
        return state == SlotState.OCCUPIED
                && depositedAt != null
                && depositedAt.plus(EXPIRY_DAYS, ChronoUnit.DAYS).isBefore(Instant.now());
    }

    // Cryptographically random 6-digit code (100000–999999)
    private String generateCode() {
        SecureRandom rng = new SecureRandom();
        return String.format("%06d", 100000 + rng.nextInt(900000));
    }
}

// ── Kiosk ──────────────────────────────────────────────────────────────────

class Location {
    final double lat, lng;
    Location(double lat, double lng) { this.lat = lat; this.lng = lng; }

    double distanceTo(Location other) {
        // Simplified Euclidean; production uses Haversine formula
        double dlat = lat - other.lat, dlng = lng - other.lng;
        return Math.sqrt(dlat*dlat + dlng*dlng);
    }
}

class Kiosk {
    final String id;
    final Location location;
    private final List<Slot> slots = new ArrayList<>();

    Kiosk(String id, Location location) { this.id = id; this.location = location; }

    void addSlot(Slot s) { slots.add(s); }

    List<Slot> getSlots() { return Collections.unmodifiableList(slots); }
}

// ── Allocation Strategy ────────────────────────────────────────────────────

interface AllocationStrategy {
    Slot findSlot(Kiosk kiosk, SlotSize packageSize);
}

class BestFitStrategy implements AllocationStrategy {
    public Slot findSlot(Kiosk kiosk, SlotSize packageSize) {
        Slot best = null;
        for (Slot s : kiosk.getSlots()) {
            if (s.getState() == SlotState.FREE && s.canFit(packageSize)) {
                if (best == null || s.getSize().ordinal() < best.getSize().ordinal()) {
                    best = s;
                }
            }
        }
        return best;
    }
}

// ── Code Registry ──────────────────────────────────────────────────────────

class CodeRegistry {
    // Maps one-time code → slotId
    private final Map<String, String> codeToSlotId = new ConcurrentHashMap<>();

    void register(String code, String slotId) { codeToSlotId.put(code, slotId); }
    String resolve(String code)               { return codeToSlotId.get(code);  }
    void invalidate(String code)              { codeToSlotId.remove(code);      }
}

// ── Locker Service ─────────────────────────────────────────────────────────

public class LockerService {
    private final List<Kiosk> kiosks          = new ArrayList<>();
    private final Map<String, Slot> slotIndex = new ConcurrentHashMap<>();
    private final CodeRegistry codeRegistry   = new CodeRegistry();
    private final AllocationStrategy strategy;

    LockerService(AllocationStrategy strategy) { this.strategy = strategy; }

    public void addKiosk(Kiosk kiosk) {
        kiosks.add(kiosk);
        for (Slot s : kiosk.getSlots()) slotIndex.put(s.getId(), s);
    }

    // UC1 + UC2: Find best slot and reserve it
    public Slot assignLocker(Location userLocation, SlotSize packageSize) {
        List<Kiosk> sorted = new ArrayList<>(kiosks);
        sorted.sort(Comparator.comparingDouble(k -> k.location.distanceTo(userLocation)));

        for (Kiosk kiosk : sorted) {
            Slot slot = strategy.findSlot(kiosk, packageSize);
            if (slot != null) {
                slot.reserve();
                System.out.printf("Assigned slot %s (%s) at kiosk %s%n",
                        slot.getId(), slot.getSize(), kiosk.id);
                return slot;
            }
        }
        System.out.println("No available locker found.");
        return null;
    }

    // UC2: Delivery agent confirms deposit; returns code for customer
    public String confirmDeposit(String slotId, Package pkg) {
        Slot slot = slotIndex.get(slotId);
        if (slot == null) throw new IllegalArgumentException("Slot not found: " + slotId);
        String code = slot.occupy(pkg);
        codeRegistry.register(code, slotId);
        System.out.println("Package deposited. Code sent to customer: " + code);
        return code;
    }

    // UC3: Customer enters code
    public boolean unlockSlot(String code) {
        String slotId = codeRegistry.resolve(code);
        if (slotId == null) { System.out.println("Invalid code."); return false; }
        Slot slot = slotIndex.get(slotId);
        boolean opened = slot.unlock(code);
        if (opened) {
            codeRegistry.invalidate(code);
            System.out.println("Slot " + slotId + " opened. Package retrieved.");
        } else {
            System.out.println("Failed to unlock slot " + slotId);
        }
        return opened;
    }

    // UC4: Expiration sweep (run by scheduler)
    public void runExpirationCheck() {
        for (Slot slot : slotIndex.values()) {
            if (slot.isExpired()) {
                System.out.println("Slot " + slot.getId() + " expired — flagging for return pickup.");
                // In production: create ReturnRequest, notify delivery service
                // After agent collects: slot.release();
            }
        }
    }
}

// ── Demo ───────────────────────────────────────────────────────────────────

class LockerDemo {
    public static void main(String[] args) {
        LockerService service = new LockerService(new BestFitStrategy());

        Kiosk kiosk = new Kiosk("Kiosk-Downtown", new Location(40.712, -74.006));
        kiosk.addSlot(new Slot("S1", SlotSize.SMALL));
        kiosk.addSlot(new Slot("M1", SlotSize.MEDIUM));
        kiosk.addSlot(new Slot("L1", SlotSize.LARGE));
        service.addKiosk(kiosk);

        // Order placed: MEDIUM package for customer near downtown
        Slot assigned = service.assignLocker(new Location(40.714, -74.008), SlotSize.MEDIUM);
        // Best fit: M1 (MEDIUM fits, SMALL does not, LARGE would waste space)

        // Delivery agent deposits package
        Package pkg = new Package("ORD-001", "cust-42", SlotSize.MEDIUM);
        String code = service.confirmDeposit(assigned.getId(), pkg);

        // Customer picks up
        service.unlockSlot(code);

        // Code reuse attempt → fails
        service.unlockSlot(code);
    }
}
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
```java
class RefrigeratedSlot extends Slot {
    boolean isCoolingActive;
    // Override occupy() to also activate cooling
    // Add temperature monitoring
}
```

**Return handling:**
```java
class ReturnRequest {
    String slotId;
    String orderId;
    Instant expiredAt;
    ReturnStatus status; // PENDING, PICKED_UP
}
// Scheduler creates ReturnRequest for expired slots
// Delivery agent scans barcode → ReturnRequest.status = PICKED_UP → slot.release()
```

**Concurrency at scale:**
For a distributed locker management system, replace `synchronized` on `Slot` with:
```
SELECT slot_id FROM slots WHERE state='FREE' AND size>=? ORDER BY size ASC LIMIT 1
FOR UPDATE SKIP LOCKED;
```
`SKIP LOCKED` allows concurrent allocation requests to skip rows being locked by other transactions, enabling parallel assignment without waiting.
