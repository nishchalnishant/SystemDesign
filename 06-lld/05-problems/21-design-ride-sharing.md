---
module: 06-lld
topic: Problems
status: interview-ready
tags: [06-lld, system-design, problems]
---
# Design Ride Sharing System (Uber/Ola)

> **Difficulty**: Medium-Hard
> **Topics**: State Pattern, Strategy Pattern, Observer Pattern, Factory
> **Extension**: Surge pricing, driver rating, scheduled rides, pool (shared) rides

---

## What Breaks Without This Design?

```python
class Ride:
    def __init__(self, rider_id, driver_id):
        self.rider_id = rider_id
        self.driver_id = driver_id
        self.status = "REQUESTED"   # string
        self.price = 0.0            # float — wrong for money

    def accept(self):
        if self.status != "REQUESTED":
            print("Can't accept")  # silent — doesn't raise
            return
        self.status = "ACCEPTED"
        send_sms(self.rider_id, "Driver is coming")     # coupled
        update_map_ui(self.driver_id, self.rider_id)    # coupled
        charge_card(self.rider_id, self.price)          # wrong timing

    def complete(self):
        self.status = "COMPLETED"
        charge_card(self.rider_id, self.price)   # double-charge if called twice
```

**Concrete failures**:
1. **Float for price**: `5.20 * 3 = 15.600000000000001` — driver overpaid by a fraction of a cent per ride, error compounds across millions of rides.
2. **String status**: `self.status = "ACCEPTED"` and later `if self.status == "Accepted"` — case-sensitive comparison; no compiler catches it.
3. **All side effects in one method**: `accept()` does SMS, map update, and (wrongly) charges the card. If charge fails, SMS already sent. No compensation possible.
4. **No invalid transition guard**: Nothing stops calling `complete()` on a `CANCELLED` ride — price is charged on a ride that was cancelled. Silent corruption.
5. **Matching is hardcoded**: `driver_id` passed at construction — no way to swap matching algorithm (nearest driver vs. highest-rated vs. least busy) without rewriting the class.

---

## Derive the Class Structure

**Force 1 — Trip lifecycle has state-dependent behaviour**: `accept()`, `start()`, `complete()`, `cancel()` do different things from different states. Calling `cancel()` after `COMPLETED` must throw. Extract `RideState` interface — each concrete state knows valid transitions.

**Force 2 — Pricing is swappable**: Standard pricing, surge pricing (multiplier × base fare), flat-rate airport pricing — all compute a `Decimal` total from the same `RideRequest`. Extract `PricingStrategy`.

**Force 3 — Matching algorithm is swappable**: Nearest driver, highest-rated driver, least time to pickup — each reads the driver pool and returns one `Driver`. Extract `MatchingStrategy`.

**Force 4 — Notifications must be decoupled**: Rider SMS, driver push, analytics, map service all react to ride events. Extract `RideObserver`. `Ride` notifies all observers; observers know nothing about each other.

```
God class → Ride (rideId, rideState: RideState, observers: list[RideObserver])
          → RideState (interface: accept, start, complete, cancel)
             → RequestedState, AcceptedState, StartedState,
               CompletedState, CancelledState
          → RideObserver (interface: on_ride_event(event))
             → RiderSmsNotifier, DriverPushNotifier, AnalyticsTracker, MapUpdater
          → PricingStrategy (interface: calculate_fare(request) → Decimal)
             → StandardPricing, SurgePricing, FlatRatePricing
          → MatchingStrategy (interface: find_driver(request, drivers) → Driver | None)
             → NearestDriverMatcher, HighestRatedMatcher
          → Driver (id, name, location, rating, status: DriverStatus)
          → RideRequest (rider_id, pickup, dropoff, requested_at)
          → RideEvent (ride_id, from_state, to_state, timestamp)
```

---

## Opening Analogy

Think of ordering a taxi at a stand. You hand a slip (the request) to the dispatcher. The dispatcher scans the lot and assigns the nearest free cab (matching strategy). The cab driver accepts — you're now "ACCEPTED". The taxi arrives, the ride starts ("EN_ROUTE"). You reach the destination ("COMPLETED") and the meter (pricing strategy) prints your fare. If you cancel before the taxi arrives, the driver is made available again and no charge is applied. The state machine ensures you can't be "completed" before you even boarded.

---

## Phase 1: Requirements

### Functional
- Rider requests a ride with pickup and dropoff location.
- System finds an available driver (configurable matching strategy).
- Driver accepts or rejects the ride; on rejection, system finds next driver.
- Ride progresses: `REQUESTED → ACCEPTED → EN_ROUTE → COMPLETED`.
- Cancellation allowed before `EN_ROUTE`; after that, cancellation fee applies.
- Fare computed at completion using the active pricing strategy.

### Non-Functional
- Invalid state transitions must throw `InvalidTransitionException`.
- Fare must be computed with `Decimal` — no `float`.
- Adding a new observer (analytics, loyalty) must not touch `Ride`.
- Matching strategy is injectable (dependency-injected, not hardcoded).

---

## Phase 2: Use Cases

### Actors
- **Rider** — requests, tracks, rates, cancels.
- **Driver** — accepts, rejects, starts, completes.
- **System** — drives matching, state transitions, notifications.

### UC1: Request and Accept Ride (Happy Path)
1. Rider submits `RideRequest(pickup, dropoff)`.
2. `RideService.request_ride()` invokes `MatchingStrategy.find_driver()`.
3. Matching returns nearest available `Driver`.
4. New `Ride` created in `RequestedState`; driver notified (observer).
5. Driver calls `ride.accept()` → `RequestedState → AcceptedState`.
6. Rider notified (observer): "Driver on the way."

### UC2: Start and Complete Ride
1. Driver arrives at pickup, calls `ride.start()` → `AcceptedState → EnRouteState`.
2. Rider and driver notified of trip start.
3. Driver reaches dropoff, calls `ride.complete()` → `EnRouteState → CompletedState`.
4. `PricingStrategy.calculate_fare(request)` → fare locked in.
5. Payment triggered (observer); receipt sent to rider.

### UC3: Cancel Before Pickup
1. Rider calls `ride.cancel()` from `RequestedState` or `AcceptedState`.
2. State → `CancelledState`.
3. Driver status set back to `AVAILABLE`.
4. No fare charged (or cancellation fee if after accepted).

### UC4: Surge Pricing Detection
1. At request time, `SurgePricing.calculate_fare()` checks current supply/demand ratio.
2. If `available_drivers / active_requests < 0.5`: surge multiplier = 2×.
3. Rider shown surge price before confirming; fare locked in at confirmation time.

---

## Phase 3: Class Diagram

```
┌──────────────────────────────────────────────┐
│                  RideService                 │
│──────────────────────────────────────────────│
│ - matcher: MatchingStrategy                  │
│ - pricing: PricingStrategy                   │
│ - active_rides: dict[str, Ride]              │
│ - available_drivers: list[Driver]            │
│──────────────────────────────────────────────│
│ + request_ride(req: RideRequest) → Ride      │
│ + get_ride(ride_id: str) → Ride              │
└──────────────────────────────────────────────┘

┌──────────────────────────────────────┐
│                 Ride                 │  <<Context>>
│──────────────────────────────────────│
│ - ride_id: str                       │
│ - request: RideRequest               │
│ - driver: Driver                     │
│ - current_state: RideState           │
│ - observers: list[RideObserver]      │
│ - fare: Decimal | None               │
│──────────────────────────────────────│
│ + accept() / start() / complete()    │
│ + cancel() / set_state()             │
│ + add_observer()                     │
└──────────────────────────────────────┘

         delegates to ↓
┌────────────────────┐
│     RideState      │  <<interface>>
│────────────────────│
│ + accept(ride)     │
│ + start(ride)      │
│ + complete(ride)   │
│ + cancel(ride)     │
└────────────────────┘
 ┌──────┬───────┬──────┐
 ▼      ▼       ▼      ▼
Req   Acc    EnRoute  Completed
State State  State    State / CancelledState

┌──────────────────────────┐   ┌──────────────────────────┐
│    MatchingStrategy      │   │    PricingStrategy        │
│──────────────────────────│   │──────────────────────────│
│ + find_driver(req,       │   │ + calculate_fare(req,     │
│     drivers) → Driver    │   │     duration, distance)   │
└──────────────────────────┘   │     → Decimal             │
▲            ▲                 └──────────────────────────┘
NearestMatcher HighestRated   ▲               ▲
```

**State transition diagram:**
```
REQUESTED ──accept()──► ACCEPTED ──start()──► EN_ROUTE ──complete()──► COMPLETED
    │                       │
  cancel()              cancel()
    ▼                       ▼
CANCELLED              CANCELLED (+ cancellation fee if driver already arrived)
```

---

## Phase 4: Design Patterns Applied

### 1. State Pattern — Ride lifecycle
**Why:** Without it, every action needs a chain of `if status == "REQUESTED"` guards. 5 states × 4 actions = 20 branches in one class. Each new state (like `DRIVER_ARRIVED`) requires touching all branches. State pattern localises each state's logic in its own class.

**How:** `Ride` holds `current_state: RideState`. Each concrete state implements valid transitions and raises `InvalidTransitionException` for invalid ones.

### 2. Strategy Pattern — Pricing and Matching
**Why:** Surge pricing, flat-rate, and standard pricing produce different fares from the same inputs. Nearest-driver and highest-rated matching are interchangeable algorithms. Hardcoding either couples business rules to the ride lifecycle.

**How:** `RideService` takes `PricingStrategy` and `MatchingStrategy` in its constructor. `calculate_fare()` and `find_driver()` are called by the service — `Ride` never touches either directly.

### 3. Observer Pattern — Notifications
**Why:** Rider SMS, driver push, analytics, map widget — all respond to ride events. Coupling them in `Ride` violates SRP and OCP. Observer decouples.

**How:** `Ride.add_observer(obs)` registers. `Ride._notify(event)` called on each state transition. Each observer reacts independently.

---

## Phase 5: Key Python Implementation

```python
from abc import ABC, abstractmethod
from decimal import Decimal
from datetime import datetime
from enum import Enum, auto
import math

# ── Value objects ─────────────────────────────────────────────────────────────

class Location:
    def __init__(self, latitude, longitude):
        self.latitude = latitude
        self.longitude = longitude

    def distance_km(self, other):
        # Haversine simplified for interview
        lat_diff = abs(self.latitude - other.latitude) * 111.0
        lon_diff = abs(self.longitude - other.longitude) * 111.0 * math.cos(math.radians(self.latitude))
        return math.sqrt(lat_diff**2 + lon_diff**2)

class RideRequest:
    def __init__(self, rider_id, pickup, dropoff):
        self.rider_id = rider_id
        self.pickup = pickup
        self.dropoff = dropoff
        self.requested_at = datetime.now()

class DriverStatus(Enum):
    AVAILABLE = auto()
    ON_TRIP   = auto()
    OFFLINE   = auto()

class Driver:
    def __init__(self, driver_id, name, location, rating, status=DriverStatus.AVAILABLE):
        self.driver_id = driver_id
        self.name = name
        self.location = location
        self.rating = rating
        self.status = status

class RideStatus(Enum):
    REQUESTED  = auto()
    ACCEPTED   = auto()
    EN_ROUTE   = auto()
    COMPLETED  = auto()
    CANCELLED  = auto()

class RideEvent:
    def __init__(self, ride_id, from_status, to_status):
        self.ride_id = ride_id
        self.from_status = from_status
        self.to_status = to_status
        self.timestamp = datetime.now()

# ── Observer ──────────────────────────────────────────────────────────────────

class RideObserver(ABC):
    @abstractmethod
    def on_ride_event(self, event): ...

class RiderSmsNotifier(RideObserver):
    def on_ride_event(self, event):
        print(f"[SMS→RIDER] Ride {event.ride_id}: {event.to_status.name}")

class AnalyticsTracker(RideObserver):
    def on_ride_event(self, event):
        print(f"[ANALYTICS] {event.ride_id}: {event.from_status.name}→{event.to_status.name}")

# ── Pricing Strategy ──────────────────────────────────────────────────────────

class PricingStrategy(ABC):
    @abstractmethod
    def calculate_fare(self, request, duration_min): ...

class StandardPricing(PricingStrategy):
    BASE_FARE   = Decimal("30")
    PER_KM      = Decimal("12")
    PER_MIN     = Decimal("2")

    def calculate_fare(self, request, duration_min):
        dist = Decimal(str(round(request.pickup.distance_km(request.dropoff), 2)))
        return self.BASE_FARE + self.PER_KM * dist + self.PER_MIN * Decimal(str(duration_min))

class SurgePricing(PricingStrategy):
    def __init__(self, multiplier, base=None):
        self._base = base or StandardPricing()
        self._multiplier = multiplier

    def calculate_fare(self, request, duration_min):
        return (self._base.calculate_fare(request, duration_min) * self._multiplier).quantize(Decimal("0.01"))

# ── Matching Strategy ─────────────────────────────────────────────────────────

class MatchingStrategy(ABC):
    @abstractmethod
    def find_driver(self, request, drivers): ...

class NearestDriverMatcher(MatchingStrategy):
    def find_driver(self, request, drivers):
        available = [d for d in drivers if d.status == DriverStatus.AVAILABLE]
        if not available:
            return None
        return min(available, key=lambda d: d.location.distance_km(request.pickup))

# ── State ─────────────────────────────────────────────────────────────────────

class InvalidTransitionException(Exception): ...

class RideState(ABC):
    @abstractmethod
    def accept(self, ride): ...
    @abstractmethod
    def start(self, ride): ...
    @abstractmethod
    def complete(self, ride): ...
    @abstractmethod
    def cancel(self, ride): ...

    def _reject(self, action, state):
        raise InvalidTransitionException(f"Cannot '{action}' from '{state}'")

class RequestedState(RideState):
    def accept(self, ride):
        ride.driver.status = DriverStatus.ON_TRIP
        ride.set_state(AcceptedState(), RideStatus.ACCEPTED)
    def start(self, r):   self._reject("start", "REQUESTED")
    def complete(self, r): self._reject("complete", "REQUESTED")
    def cancel(self, ride):
        ride.set_state(CancelledState(), RideStatus.CANCELLED)

class AcceptedState(RideState):
    def accept(self, r):  self._reject("accept", "ACCEPTED")
    def start(self, ride):
        ride.set_state(EnRouteState(), RideStatus.EN_ROUTE)
    def complete(self, r): self._reject("complete", "ACCEPTED")
    def cancel(self, ride):
        ride.driver.status = DriverStatus.AVAILABLE
        ride.set_state(CancelledState(), RideStatus.CANCELLED)

class EnRouteState(RideState):
    def accept(self, r):  self._reject("accept", "EN_ROUTE")
    def start(self, r):   self._reject("start", "EN_ROUTE")
    def complete(self, ride):
        duration_min = (datetime.now() - ride.request.requested_at).seconds / 60
        ride.fare = ride._pricing.calculate_fare(ride.request, duration_min)
        ride.driver.status = DriverStatus.AVAILABLE
        ride.set_state(CompletedState(), RideStatus.COMPLETED)
        print(f"[FARE] Ride {ride.ride_id}: ₹{ride.fare}")
    def cancel(self, r):  self._reject("cancel", "EN_ROUTE")

class CompletedState(RideState):
    def accept(self, r):  self._reject("accept", "COMPLETED")
    def start(self, r):   self._reject("start", "COMPLETED")
    def complete(self, r): self._reject("complete", "COMPLETED")
    def cancel(self, r):  self._reject("cancel", "COMPLETED")

class CancelledState(RideState):
    def accept(self, r):  self._reject("accept", "CANCELLED")
    def start(self, r):   self._reject("start", "CANCELLED")
    def complete(self, r): self._reject("complete", "CANCELLED")
    def cancel(self, r):  self._reject("cancel", "CANCELLED")

# ── Ride Context ──────────────────────────────────────────────────────────────

class Ride:
    def __init__(self, ride_id, request, driver, pricing):
        self.ride_id = ride_id
        self.request = request
        self.driver = driver
        self._pricing = pricing
        self.fare = None
        self._observers = []
        self._status = RideStatus.REQUESTED
        self._current_state = RequestedState()

    def add_observer(self, obs):
        self._observers.append(obs)

    def set_state(self, state, new_status):
        event = RideEvent(self.ride_id, self._status, new_status)
        self._status = new_status
        self._current_state = state
        for obs in self._observers:
            obs.on_ride_event(event)

    def accept(self):   self._current_state.accept(self)
    def start(self):    self._current_state.start(self)
    def complete(self): self._current_state.complete(self)
    def cancel(self):   self._current_state.cancel(self)

# ── Service ───────────────────────────────────────────────────────────────────

class RideService:
    def __init__(self, matcher, pricing):
        self._matcher = matcher
        self._pricing = pricing
        self._drivers = []
        self._rides = {}
        self._counter = 0

    def register_driver(self, driver):
        self._drivers.append(driver)

    def request_ride(self, request):
        driver = self._matcher.find_driver(request, self._drivers)
        if driver is None:
            raise RuntimeError("No drivers available")
        self._counter += 1
        ride_id = f"RIDE-{self._counter:04d}"
        ride = Ride(ride_id, request, driver, self._pricing)
        ride.add_observer(RiderSmsNotifier())
        ride.add_observer(AnalyticsTracker())
        self._rides[ride_id] = ride
        print(f"Matched ride {ride_id} → driver {driver.name}")
        return ride

# ── Demo ──────────────────────────────────────────────────────────────────────

if __name__ == "__main__":
    service = RideService(
        matcher=NearestDriverMatcher(),
        pricing=SurgePricing(Decimal("1.5")),
    )
    driver = Driver("D1", "Rahul", Location(12.97, 77.59), rating=4.8)
    service.register_driver(driver)

    req = RideRequest("U1", Location(12.97, 77.60), Location(12.95, 77.62))
    ride = service.request_ride(req)
    ride.accept()     # REQUESTED → ACCEPTED
    ride.start()      # ACCEPTED → EN_ROUTE
    ride.complete()   # EN_ROUTE → COMPLETED, fare computed
```

---

## Phase 6: Trade-offs and Extensions

### Trade-offs

| Decision | Choice | Alternative | Reason |
|---|---|---|---|
| Fare at completion | Compute fare when `complete()` is called | Compute at request (upfront pricing) | Driver can't know exact duration before trip; upfront needs ML price estimate |
| Matching in service | `RideService` calls `MatchingStrategy` | `Ride` holds a reference to matcher | Service owns the driver pool; `Ride` doesn't know about other rides |
| Driver free on cancel | Set `AVAILABLE` in `AcceptedState.cancel()` | Use observer to free driver | State has direct reference to driver; simpler and clearer than a separate observer |
| Decimal for fare | `Decimal` with `.quantize("0.01")` | `float` | `float` precision errors compound; `Decimal` is exact |

### Extensions

**Driver rejection flow (re-matching):**
```python
class RideService:
    def driver_rejected(self, ride_id: str) -> Ride:
        old_ride = self._rides[ride_id]
        old_ride.cancel()  # CancelledState, driver freed
        # exclude the rejecting driver and re-match
        return self.request_ride(old_ride.request)
```

**Pool/shared rides — Composite Ride:**
```python
class PoolRide:
    def __init__(self, rides: list[Ride]) -> None:
        self._legs = rides  # Composite: one driver, multiple riders
    def complete_leg(self, ride_id: str) -> None:
        # complete individual leg, driver remains ON_TRIP until last leg
        ...
```

---

## Interviewer Follow-Up Questions

- "How do you prevent two riders from being matched to the same driver simultaneously?" → Optimistic locking on the `Driver` record. In a real system: `UPDATE drivers SET status='ON_TRIP', version=version+1 WHERE driver_id=X AND status='AVAILABLE' AND version=N`. `rows_affected=0` means another request already claimed the driver — retry matching with the next available driver. At LLD level: show the version field on `Driver` and the conditional update logic in `find_driver()`.
- "How does surge pricing work architecturally?" → Surge multiplier computed by a background `SurgeCalculator` every 30 seconds: `surge = active_requests / available_drivers`. If `surge > 2.0`: multiplier = 1.5x. The multiplier is stored in Redis keyed by `geohash(area)`. `RideService` reads the multiplier at request time, wraps `StandardPricing` with `SurgePricing(multiplier)`, and shows the rider the estimated fare. Fare is locked in at request confirmation — not recalculated at completion. This is the "price shown is the price paid" contract.
- "What if the driver's GPS location is stale — how do you ensure accurate matching?" → Drivers send location updates every 5 seconds via WebSocket. `Driver.location` is updated on receipt. Matching always uses the latest location snapshot. For production: use a geospatial index (Redis GEOSEARCH or PostGIS) to efficiently find drivers within a radius, not a linear scan. At LLD: describe the linear scan and say "production would replace this with a geospatial index query."
- "How do you handle the case where a driver starts a ride but the app crashes — how does the system recover?" → Ride state is persisted in the DB, not only in-memory. On driver app restart: `GET /rides/active` returns the current ride in `EN_ROUTE` state. The driver can call `complete()`. The state machine starts from the persisted state, not from `REQUESTED`. Critical invariant: state transitions are written to the DB atomically (within the same DB transaction as the notification dispatch or via an outbox pattern).
- "Walk me through the complete ride lifecycle from request to payment." → (1) Rider requests → `RideService.request_ride()` calls `MatchingStrategy.find_driver()` → finds nearest driver. (2) `Ride` created in `REQUESTED` state; driver notified. (3) Driver accepts → `RequestedState.accept()` → `ACCEPTED`; rider notified "driver on the way." (4) Driver arrives, calls start → `ACCEPTED → EN_ROUTE`. (5) Trip ends, driver calls complete → `EnRouteState.complete()` computes fare via `PricingStrategy`, sets `driver.status = AVAILABLE`, transitions to `COMPLETED`. (6) `COMPLETED` triggers `PaymentObserver` to charge the card. Each step is a separate state; each side effect is an observer. No state can be skipped.
