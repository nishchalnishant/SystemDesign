---
module: 06-lld
topic: Problems
status: unread
tags: [06-lld, lld, ride-sharing, state-pattern, strategy, observer]
---
# Design a Ride Sharing System

> **Difficulty**: Hard  
> **Asked at**: Amazon, Uber, Lyft  
> **Key Patterns**: State (ride status), Strategy (matching/fare), Observer (driver location updates)

---

## Understanding the Problem

Design a ride-sharing platform where riders request rides, the system matches them to a nearby available driver, tracks the ride through pickup and drop-off, calculates the fare, and allows both parties to rate each other.

---

## Clarifying Questions

**You**: "Is this a real-time system or can we simplify to a synchronous polling model?"  
**Interviewer**: "Simplify to synchronous; skip WebSocket infrastructure."

**You**: "How does driver matching work — closest driver, or do we factor in ratings?"  
**Interviewer**: "Find the nearest available driver; rating is a tiebreaker."

**You**: "Can riders cancel after matching? Can drivers?"  
**Interviewer**: "Yes to both — model the cancellation transitions."

**You**: "How is fare calculated?"  
**Interviewer**: "Base fare + per-km rate + per-minute rate; surge pricing in peak hours is a deep dive."

**You**: "What driver states do we need?"  
**Interviewer**: "OFFLINE, AVAILABLE, ON_RIDE at minimum."

**You**: "Do we need payment processing?"  
**Interviewer**: "Stub it — just model the fare; don't implement actual payment."

---

## Final Requirements

**In scope:**
1. Rider requests a ride with pickup and dropoff location
2. System matches nearest available driver
3. Driver accepts or rejects; if rejected, try next closest
4. Ride lifecycle: REQUESTED → MATCHED → IN_PROGRESS → COMPLETED / CANCELLED
5. Fare calculated on completion (base + distance + time)
6. Mutual rating (rider rates driver, driver rates rider)
7. Driver state machine: OFFLINE → AVAILABLE → ON_RIDE

**Out of scope:**
- Actual geospatial indexing (geohash, quadtree)
- Real-time streaming / WebSockets
- Payment gateway integration

---

## Core Entities and Relationships

| Entity | Responsibility |
|--------|----------------|
| Rider | Requests and rates rides |
| Driver | Accepts rides; has state machine and location |
| DriverStatus | Enum: OFFLINE, AVAILABLE, ON_RIDE |
| Ride | Aggregate: links rider + driver, tracks full lifecycle |
| RideStatus | Enum: REQUESTED, MATCHED, IN_PROGRESS, COMPLETED, CANCELLED |
| Location | (lat, lon) value object |
| MatchingService | Finds the nearest available driver for a request |
| FareCalculator | Strategy: computes fare from distance and duration |
| RatingService | Records and averages ratings for riders and drivers |
| RideService | Orchestrates all ride operations |

---

## Class Design

### Location

```
class Location:
- lat: float
- lon: float
+ distance_km(other: Location) -> float
```

### Driver

| Requirement | What Driver must track |
|-------------|------------------------|
| Identity | driver_id, name, vehicle_info, rating |
| Availability | status: DriverStatus |
| Position | current_location: Location |

```
class Driver:
- driver_id: str
- name: str
- vehicle: str
- rating: float
- status: DriverStatus
- current_location: Location
+ go_online()
+ go_offline()
```

### Ride

| Requirement | What Ride must track |
|-------------|----------------------|
| Parties | rider: Rider, driver: Optional[Driver] |
| Route | pickup: Location, dropoff: Location |
| Lifecycle | status: RideStatus, timestamps |
| Outcome | fare: float, distance_km: float, duration_min: float |

```
class Ride:
- ride_id: str
- rider: Rider
- driver: Optional[Driver]
- pickup: Location
- dropoff: Location
- status: RideStatus
- requested_at: datetime
- started_at: Optional[datetime]
- completed_at: Optional[datetime]
- fare: float
- distance_km: float
```

### FareCalculator

```
abstract class FareCalculator:
+ calculate(ride: Ride) -> float

class StandardFareCalculator(FareCalculator):
- BASE_FARE = 2.0
- PER_KM    = 1.5
- PER_MIN   = 0.25

class SurgeFareCalculator(FareCalculator):
- surge_multiplier: float
```

---

## Implementation

### Core Method: `request_ride`

**Core logic:**
1. Create a Ride with status REQUESTED.
2. Delegate to MatchingService to find nearest available driver.
3. If no driver found, return ride with status REQUESTED (to be polled or retried).
4. Notify driver; set ride status to MATCHED, driver status to ON_RIDE.

**Edge cases:**
- No drivers online — ride stays REQUESTED.
- Driver rejects — MatchingService skips and tries next candidate.
- Rider cancels before driver accepts — set ride CANCELLED.

```python
from dataclasses import dataclass, field
from datetime import datetime
from enum import Enum, auto
from typing import Optional
import math, uuid


class DriverStatus(Enum):
    OFFLINE   = auto()
    AVAILABLE = auto()
    ON_RIDE   = auto()


class RideStatus(Enum):
    REQUESTED   = auto()
    MATCHED     = auto()
    IN_PROGRESS = auto()
    COMPLETED   = auto()
    CANCELLED   = auto()


@dataclass
class Location:
    lat: float
    lon: float

    def distance_km(self, other: "Location") -> float:
        # Euclidean approximation; use Haversine in production
        dlat = (self.lat - other.lat) * 111.0
        dlon = (self.lon - other.lon) * 111.0 * math.cos(math.radians(self.lat))
        return math.sqrt(dlat ** 2 + dlon ** 2)


@dataclass
class Rider:
    rider_id: str
    name: str
    rating: float = 5.0


@dataclass
class Driver:
    driver_id: str
    name: str
    vehicle: str
    rating: float = 5.0
    status: DriverStatus = DriverStatus.OFFLINE
    current_location: Location = field(default_factory=lambda: Location(0, 0))

    def go_online(self):
        self.status = DriverStatus.AVAILABLE

    def go_offline(self):
        if self.status == DriverStatus.ON_RIDE:
            raise ValueError("Cannot go offline mid-ride")
        self.status = DriverStatus.OFFLINE


@dataclass
class Ride:
    ride_id: str
    rider: Rider
    pickup: Location
    dropoff: Location
    status: RideStatus = RideStatus.REQUESTED
    driver: Optional[Driver] = None
    requested_at: datetime = field(default_factory=datetime.utcnow)
    started_at: Optional[datetime] = None
    completed_at: Optional[datetime] = None
    fare: float = 0.0
    distance_km: float = 0.0


class FareCalculator:
    BASE_FARE = 2.0
    PER_KM    = 1.5
    PER_MIN   = 0.25

    def calculate(self, ride: Ride) -> float:
        if ride.started_at is None or ride.completed_at is None:
            return 0.0
        duration_min = (ride.completed_at - ride.started_at).seconds / 60
        return (self.BASE_FARE
                + ride.distance_km * self.PER_KM
                + duration_min * self.PER_MIN)


class SurgeFareCalculator(FareCalculator):
    def __init__(self, multiplier: float):
        self.multiplier = multiplier

    def calculate(self, ride: Ride) -> float:
        return super().calculate(ride) * self.multiplier


class MatchingService:
    def find_driver(self, pickup: Location,
                    drivers: list[Driver]) -> Optional[Driver]:
        available = [d for d in drivers if d.status == DriverStatus.AVAILABLE]
        if not available:
            return None
        # sort by distance, then rating descending as tiebreaker
        return min(available,
                   key=lambda d: (d.current_location.distance_km(pickup), -d.rating))


class RatingService:
    def __init__(self):
        self._driver_ratings: dict[str, list[float]] = {}
        self._rider_ratings: dict[str, list[float]] = {}

    def rate_driver(self, driver: Driver, score: float):
        self._driver_ratings.setdefault(driver.driver_id, []).append(score)
        driver.rating = sum(self._driver_ratings[driver.driver_id]) / \
                        len(self._driver_ratings[driver.driver_id])

    def rate_rider(self, rider: Rider, score: float):
        self._rider_ratings.setdefault(rider.rider_id, []).append(score)
        rider.rating = sum(self._rider_ratings[rider.rider_id]) / \
                       len(self._rider_ratings[rider.rider_id])


class RideService:
    def __init__(self):
        self.rides: dict[str, Ride] = {}
        self.drivers: dict[str, Driver] = {}
        self.riders: dict[str, Rider] = {}
        self.matching = MatchingService()
        self.fare_calculator: FareCalculator = FareCalculator()
        self.rating_service = RatingService()

    def register_driver(self, driver: Driver):
        self.drivers[driver.driver_id] = driver

    def register_rider(self, rider: Rider):
        self.riders[rider.rider_id] = rider

    def request_ride(self, rider_id: str, pickup: Location,
                     dropoff: Location) -> Ride:
        rider = self.riders[rider_id]
        ride = Ride(ride_id=str(uuid.uuid4()), rider=rider,
                    pickup=pickup, dropoff=dropoff)
        self.rides[ride.ride_id] = ride

        driver = self.matching.find_driver(pickup, list(self.drivers.values()))
        if driver:
            ride.driver = driver
            ride.status = RideStatus.MATCHED
            driver.status = DriverStatus.ON_RIDE

        return ride

    def accept_ride(self, driver_id: str, ride_id: str) -> Ride:
        # Called when driver explicitly confirms acceptance UI
        ride = self.rides[ride_id]
        if ride.status != RideStatus.MATCHED:
            raise ValueError("Ride not in MATCHED state")
        if ride.driver.driver_id != driver_id:
            raise ValueError("Driver mismatch")
        return ride  # already matched; acceptance is implicit in matching

    def start_ride(self, ride_id: str) -> Ride:
        ride = self.rides[ride_id]
        if ride.status != RideStatus.MATCHED:
            raise ValueError("Cannot start — ride not MATCHED")
        ride.status = RideStatus.IN_PROGRESS
        ride.started_at = datetime.utcnow()
        return ride

    def complete_ride(self, ride_id: str) -> Ride:
        ride = self.rides[ride_id]
        if ride.status != RideStatus.IN_PROGRESS:
            raise ValueError("Cannot complete — ride not IN_PROGRESS")
        ride.completed_at = datetime.utcnow()
        ride.distance_km = ride.pickup.distance_km(ride.dropoff)
        ride.fare = self.fare_calculator.calculate(ride)
        ride.status = RideStatus.COMPLETED
        ride.driver.status = DriverStatus.AVAILABLE
        return ride

    def cancel_ride(self, ride_id: str, by: str = "rider") -> Ride:
        ride = self.rides[ride_id]
        if ride.status in (RideStatus.COMPLETED, RideStatus.CANCELLED):
            raise ValueError("Cannot cancel completed/cancelled ride")
        if ride.status == RideStatus.IN_PROGRESS:
            raise ValueError("Cannot cancel an in-progress ride")
        if ride.driver:
            ride.driver.status = DriverStatus.AVAILABLE
        ride.status = RideStatus.CANCELLED
        return ride

    def rate_driver(self, ride_id: str, score: float):
        ride = self.rides[ride_id]
        if ride.status != RideStatus.COMPLETED:
            raise ValueError("Can only rate after completion")
        self.rating_service.rate_driver(ride.driver, score)

    def rate_rider(self, ride_id: str, score: float):
        ride = self.rides[ride_id]
        if ride.status != RideStatus.COMPLETED:
            raise ValueError("Can only rate after completion")
        self.rating_service.rate_rider(ride.rider, score)

    def update_driver_location(self, driver_id: str, location: Location):
        self.drivers[driver_id].current_location = location
```

---

## Verification

Scenario: Rider requests a ride; driver accepts; ride completes.

1. Driver goes online → `DriverStatus.AVAILABLE`.
2. `request_ride(rider_id, pickup, dropoff)` → MatchingService finds nearest driver; Ride status=MATCHED, driver status=ON_RIDE.
3. `start_ride(ride_id)` → status=IN_PROGRESS, `started_at` set.
4. `complete_ride(ride_id)` → distance computed, fare calculated (e.g., 5km, 10 min → $2 + $7.5 + $2.5 = $12), status=COMPLETED, driver back to AVAILABLE.
5. `rate_driver(ride_id, 4.5)` → driver rating updated.

---

## Deep Dive & Extensibility

### 1. "How would you find the nearest driver efficiently at scale?"

In production, use a geohash or quadtree index. Geohash divides the map into a grid of cells identified by a base-32 string. Drivers in the same or adjacent geohash cells are candidates. This reduces the search from O(N drivers) to O(drivers in nearby cells).

```python
import geohash2

def find_driver_geohash(self, pickup: Location,
                         driver_index: dict[str, list[Driver]]) -> Optional[Driver]:
    cell = geohash2.encode(pickup.lat, pickup.lon, precision=6)  # ~1.2km cell
    neighbors = geohash2.neighbors(cell) + [cell]
    candidates = []
    for c in neighbors:
        candidates.extend(driver_index.get(c, []))
    available = [d for d in candidates if d.status == DriverStatus.AVAILABLE]
    return min(available, key=lambda d: d.current_location.distance_km(pickup),
               default=None)
```

### 2. "How does surge pricing work?"

Surge multiplier = f(demand / supply) in a geographic zone. When open ride requests in a zone exceed available drivers by a threshold, activate surge.

```python
def compute_surge(self, zone_id: str) -> float:
    open_requests = self._open_requests_in_zone(zone_id)
    available_drivers = self._available_drivers_in_zone(zone_id)
    if available_drivers == 0:
        return 3.0
    ratio = open_requests / available_drivers
    if ratio > 3:
        return 2.5
    elif ratio > 2:
        return 1.8
    elif ratio > 1.5:
        return 1.3
    return 1.0
```

`RideService` then selects `SurgeFareCalculator(multiplier)` for rides in that zone.

### 3. "How does ride cancellation by either party work?"

Cancellation is valid in REQUESTED or MATCHED states. If a driver cancels a matched ride, the system should rematch with the next closest driver before giving up. Track a `cancelled_by_drivers` list on the Ride to skip already-rejected drivers.

```python
def driver_cancel(self, ride_id: str, driver_id: str):
    ride = self.rides[ride_id]
    if ride.status != RideStatus.MATCHED:
        raise ValueError("Driver can only cancel a matched ride")
    ride.driver.status = DriverStatus.AVAILABLE
    ride._skipped_drivers = getattr(ride, "_skipped_drivers", set())
    ride._skipped_drivers.add(driver_id)
    ride.driver = None
    ride.status = RideStatus.REQUESTED
    # Attempt rematch excluding skipped drivers
    candidates = [d for d in self.drivers.values()
                  if d.driver_id not in ride._skipped_drivers]
    new_driver = self.matching.find_driver(ride.pickup, candidates)
    if new_driver:
        ride.driver = new_driver
        ride.status = RideStatus.MATCHED
        new_driver.status = DriverStatus.ON_RIDE
```

### 4. "How do you track real-time driver location?"

Drivers emit location pings every 3-5 seconds. `update_driver_location(driver_id, location)` updates the in-memory location and the geohash index. For persistence, write to a time-series store (Redis geospatial index). This also lets the rider's app render the driver moving on the map.

### 5. "Walk me through the driver state machine"

```
OFFLINE --go_online()--> AVAILABLE
AVAILABLE --matched()--> ON_RIDE
ON_RIDE --complete/cancel()--> AVAILABLE
AVAILABLE --go_offline()--> OFFLINE
ON_RIDE --go_offline()--> BLOCKED (must complete ride first)
```

Enforce transitions in Driver methods; `go_offline()` raises if status is ON_RIDE.

---

## Interviewer Questions by Level

**Junior**: What states can a Ride be in, and what triggers each transition?  
**Mid-level**: How does MatchingService decide which driver to assign, and what happens if that driver rejects?  
**Senior**: Explain how geohashing enables O(1)-ish nearest driver lookup and how you'd keep the index consistent with driver location updates.

---

## Common Interview Questions

- **Q: How do you find the nearest available driver efficiently?** A: Geohash the map into cells; index drivers by cell. On request, query the rider's cell and 8 neighbors — usually a few dozen candidates instead of all drivers.
- **Q: What triggers surge pricing?** A: High demand-to-supply ratio in a geographic zone; multiplier increases with the ratio and is applied via SurgeFareCalculator strategy.
- **Q: What if no driver accepts the ride?** A: Track rejected drivers; after N retries or a timeout, notify the rider that no driver is available. Keep the ride in REQUESTED state for possible retry.
- **Q: What are the valid ride state transitions?** A: REQUESTED → MATCHED (driver found) → IN_PROGRESS (driver at pickup) → COMPLETED; any non-IN_PROGRESS state → CANCELLED.
- **Q: How do you handle a driver going offline mid-ride?** A: System should detect missed heartbeats and mark driver OFFLINE; reassign a new driver or escalate to support. The existing ride remains IN_PROGRESS.
- **Q: How is fare calculated?** A: Base fare + (distance_km × per_km_rate) + (duration_min × per_min_rate), then multiplied by surge if applicable.
- **Q: How does rating work?** A: After COMPLETED status, rider rates driver and driver rates rider; ratings stored as running averages in RatingService.
