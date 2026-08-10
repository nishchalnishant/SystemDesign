> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design a Ride Sharing System (e.g., Uber/Lyft) — tests matching algorithms, geospatial querying, and managing the lifecycle of a Ride.
>
> **Key concepts:**
> - Core Entities: `Rider`, `Driver`, `Ride`, `Location` (lat, long), `Vehicle`.
> - Matching Strategy (Strategy Pattern): How to find the best driver? `NearestDriverStrategy`, `HighestRatedDriverStrategy`. Requires a spatial data structure (QuadTree or Geohash, usually abstracted behind a `LocationManager`).
> - Pricing Strategy (Strategy Pattern): `SurgePricing`, `StandardPricing`, `DistanceBasedPricing`.
> - State Pattern: `Ride` transitions: `REQUESTED`, `ACCEPTED`, `ARRIVING`, `IN_PROGRESS`, `COMPLETED`.
> - Observer: `Rider` app observing the `Driver`'s location updates.
>
> **Key takeaway:** Focus on the interfaces for the Strategy patterns (Matching and Pricing) and the State machine for the Ride. Abstract the complex geospatial math into a black-box `LocationService.getDriversWithinRadius()`.

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

```java
import java.time.Instant;
import java.time.Duration;
import java.util.*;

enum DriverStatus {
    OFFLINE, AVAILABLE, ON_RIDE
}

enum RideStatus {
    REQUESTED, MATCHED, IN_PROGRESS, COMPLETED, CANCELLED
}

class Location {
    private final double lat;
    private final double lon;

    public Location(double lat, double lon) {
        this.lat = lat;
        this.lon = lon;
    }

    public double getLat() { return lat; }
    public double getLon() { return lon; }

    // Euclidean approximation; use Haversine in production
    public double distanceKm(Location other) {
        double dlat = (this.lat - other.lat) * 111.0;
        double dlon = (this.lon - other.lon) * 111.0 * Math.cos(Math.toRadians(this.lat));
        return Math.sqrt(dlat * dlat + dlon * dlon);
    }
}

class Rider {
    private final String riderId;
    private final String name;
    private double rating;

    public Rider(String riderId, String name) {
        this(riderId, name, 5.0);
    }

    public Rider(String riderId, String name, double rating) {
        this.riderId = riderId;
        this.name = name;
        this.rating = rating;
    }

    public String getRiderId() { return riderId; }
    public String getName() { return name; }
    public double getRating() { return rating; }
    public void setRating(double rating) { this.rating = rating; }
}

class Driver {
    private final String driverId;
    private final String name;
    private final String vehicle;
    private double rating;
    private DriverStatus status;
    private Location currentLocation;

    public Driver(String driverId, String name, String vehicle) {
        this(driverId, name, vehicle, 5.0, DriverStatus.OFFLINE, new Location(0, 0));
    }

    public Driver(String driverId, String name, String vehicle, double rating,
                  DriverStatus status, Location currentLocation) {
        this.driverId = driverId;
        this.name = name;
        this.vehicle = vehicle;
        this.rating = rating;
        this.status = status;
        this.currentLocation = currentLocation;
    }

    public String getDriverId() { return driverId; }
    public String getName() { return name; }
    public String getVehicle() { return vehicle; }
    public double getRating() { return rating; }
    public void setRating(double rating) { this.rating = rating; }
    public DriverStatus getStatus() { return status; }
    public void setStatus(DriverStatus status) { this.status = status; }
    public Location getCurrentLocation() { return currentLocation; }
    public void setCurrentLocation(Location currentLocation) { this.currentLocation = currentLocation; }

    public void goOnline() {
        this.status = DriverStatus.AVAILABLE;
    }

    public void goOffline() {
        if (this.status == DriverStatus.ON_RIDE) {
            throw new IllegalStateException("Cannot go offline mid-ride");
        }
        this.status = DriverStatus.OFFLINE;
    }
}

class Ride {
    private final String rideId;
    private final Rider rider;
    private final Location pickup;
    private final Location dropoff;
    private RideStatus status;
    private Driver driver;
    private final Instant requestedAt;
    private Instant startedAt;
    private Instant completedAt;
    private double fare;
    private double distanceKm;

    public Ride(String rideId, Rider rider, Location pickup, Location dropoff) {
        this.rideId = rideId;
        this.rider = rider;
        this.pickup = pickup;
        this.dropoff = dropoff;
        this.status = RideStatus.REQUESTED;
        this.driver = null;
        this.requestedAt = Instant.now();
        this.startedAt = null;
        this.completedAt = null;
        this.fare = 0.0;
        this.distanceKm = 0.0;
    }

    public String getRideId() { return rideId; }
    public Rider getRider() { return rider; }
    public Location getPickup() { return pickup; }
    public Location getDropoff() { return dropoff; }
    public RideStatus getStatus() { return status; }
    public void setStatus(RideStatus status) { this.status = status; }
    public Driver getDriver() { return driver; }
    public void setDriver(Driver driver) { this.driver = driver; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public double getFare() { return fare; }
    public void setFare(double fare) { this.fare = fare; }
    public double getDistanceKm() { return distanceKm; }
    public void setDistanceKm(double distanceKm) { this.distanceKm = distanceKm; }
}

class FareCalculator {
    protected static final double BASE_FARE = 2.0;
    protected static final double PER_KM = 1.5;
    protected static final double PER_MIN = 0.25;

    public double calculate(Ride ride) {
        if (ride.getStartedAt() == null || ride.getCompletedAt() == null) {
            return 0.0;
        }
        double durationMin = Duration.between(ride.getStartedAt(), ride.getCompletedAt()).getSeconds() / 60.0;
        return BASE_FARE
                + ride.getDistanceKm() * PER_KM
                + durationMin * PER_MIN;
    }
}

class SurgeFareCalculator extends FareCalculator {
    private final double multiplier;

    public SurgeFareCalculator(double multiplier) {
        this.multiplier = multiplier;
    }

    @Override
    public double calculate(Ride ride) {
        return super.calculate(ride) * multiplier;
    }
}

class MatchingService {
    public Driver findDriver(Location pickup, List<Driver> drivers) {
        List<Driver> available = new ArrayList<>();
        for (Driver d : drivers) {
            if (d.getStatus() == DriverStatus.AVAILABLE) {
                available.add(d);
            }
        }
        if (available.isEmpty()) {
            return null;
        }
        // sort by distance, then rating descending as tiebreaker
        return Collections.min(available, Comparator
                .comparingDouble((Driver d) -> d.getCurrentLocation().distanceKm(pickup))
                .thenComparingDouble(d -> -d.getRating()));
    }
}

class RatingService {
    private final Map<String, List<Double>> driverRatings = new HashMap<>();
    private final Map<String, List<Double>> riderRatings = new HashMap<>();

    public void rateDriver(Driver driver, double score) {
        driverRatings.computeIfAbsent(driver.getDriverId(), k -> new ArrayList<>()).add(score);
        List<Double> scores = driverRatings.get(driver.getDriverId());
        driver.setRating(average(scores));
    }

    public void rateRider(Rider rider, double score) {
        riderRatings.computeIfAbsent(rider.getRiderId(), k -> new ArrayList<>()).add(score);
        List<Double> scores = riderRatings.get(rider.getRiderId());
        rider.setRating(average(scores));
    }

    private double average(List<Double> scores) {
        double sum = 0.0;
        for (double s : scores) sum += s;
        return sum / scores.size();
    }
}

class RideService {
    private final Map<String, Ride> rides = new HashMap<>();
    private final Map<String, Driver> drivers = new HashMap<>();
    private final Map<String, Rider> riders = new HashMap<>();
    private final MatchingService matching = new MatchingService();
    private FareCalculator fareCalculator = new FareCalculator();
    private final RatingService ratingService = new RatingService();

    public void registerDriver(Driver driver) {
        drivers.put(driver.getDriverId(), driver);
    }

    public void registerRider(Rider rider) {
        riders.put(rider.getRiderId(), rider);
    }

    public Ride requestRide(String riderId, Location pickup, Location dropoff) {
        Rider rider = riders.get(riderId);
        Ride ride = new Ride(UUID.randomUUID().toString(), rider, pickup, dropoff);
        rides.put(ride.getRideId(), ride);

        Driver driver = matching.findDriver(pickup, new ArrayList<>(drivers.values()));
        if (driver != null) {
            ride.setDriver(driver);
            ride.setStatus(RideStatus.MATCHED);
            driver.setStatus(DriverStatus.ON_RIDE);
        }

        return ride;
    }

    public Ride acceptRide(String driverId, String rideId) {
        // Called when driver explicitly confirms acceptance UI
        Ride ride = rides.get(rideId);
        if (ride.getStatus() != RideStatus.MATCHED) {
            throw new IllegalStateException("Ride not in MATCHED state");
        }
        if (!ride.getDriver().getDriverId().equals(driverId)) {
            throw new IllegalStateException("Driver mismatch");
        }
        return ride;  // already matched; acceptance is implicit in matching
    }

    public Ride startRide(String rideId) {
        Ride ride = rides.get(rideId);
        if (ride.getStatus() != RideStatus.MATCHED) {
            throw new IllegalStateException("Cannot start — ride not MATCHED");
        }
        ride.setStatus(RideStatus.IN_PROGRESS);
        ride.setStartedAt(Instant.now());
        return ride;
    }

    public Ride completeRide(String rideId) {
        Ride ride = rides.get(rideId);
        if (ride.getStatus() != RideStatus.IN_PROGRESS) {
            throw new IllegalStateException("Cannot complete — ride not IN_PROGRESS");
        }
        ride.setCompletedAt(Instant.now());
        ride.setDistanceKm(ride.getPickup().distanceKm(ride.getDropoff()));
        ride.setFare(fareCalculator.calculate(ride));
        ride.setStatus(RideStatus.COMPLETED);
        ride.getDriver().setStatus(DriverStatus.AVAILABLE);
        return ride;
    }

    public Ride cancelRide(String rideId, String by) {
        Ride ride = rides.get(rideId);
        if (ride.getStatus() == RideStatus.COMPLETED || ride.getStatus() == RideStatus.CANCELLED) {
            throw new IllegalStateException("Cannot cancel completed/cancelled ride");
        }
        if (ride.getStatus() == RideStatus.IN_PROGRESS) {
            throw new IllegalStateException("Cannot cancel an in-progress ride");
        }
        if (ride.getDriver() != null) {
            ride.getDriver().setStatus(DriverStatus.AVAILABLE);
        }
        ride.setStatus(RideStatus.CANCELLED);
        return ride;
    }

    public void rateDriver(String rideId, double score) {
        Ride ride = rides.get(rideId);
        if (ride.getStatus() != RideStatus.COMPLETED) {
            throw new IllegalStateException("Can only rate after completion");
        }
        ratingService.rateDriver(ride.getDriver(), score);
    }

    public void rateRider(String rideId, double score) {
        Ride ride = rides.get(rideId);
        if (ride.getStatus() != RideStatus.COMPLETED) {
            throw new IllegalStateException("Can only rate after completion");
        }
        ratingService.rateRider(ride.getRider(), score);
    }

    public void updateDriverLocation(String driverId, Location location) {
        drivers.get(driverId).setCurrentLocation(location);
    }
}
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

```java
import ch.hsr.geohash.GeoHash;
import java.util.*;

public Driver findDriverGeohash(Location pickup, Map<String, List<Driver>> driverIndex) {
    String cell = GeoHash.withCharacterPrecision(pickup.getLat(), pickup.getLon(), 6).toBase32();  // ~1.2km cell
    List<String> neighbors = new ArrayList<>(Arrays.asList(
            GeoHash.fromGeohashString(cell).getAdjacent()).stream()
            .map(GeoHash::toBase32).toList());
    neighbors.add(cell);

    List<Driver> candidates = new ArrayList<>();
    for (String c : neighbors) {
        candidates.addAll(driverIndex.getOrDefault(c, Collections.emptyList()));
    }

    List<Driver> available = new ArrayList<>();
    for (Driver d : candidates) {
        if (d.getStatus() == DriverStatus.AVAILABLE) {
            available.add(d);
        }
    }
    if (available.isEmpty()) {
        return null;
    }
    return Collections.min(available,
            Comparator.comparingDouble(d -> d.getCurrentLocation().distanceKm(pickup)));
}
```

### 2. "How does surge pricing work?"

Surge multiplier = f(demand / supply) in a geographic zone. When open ride requests in a zone exceed available drivers by a threshold, activate surge.

```java
public double computeSurge(String zoneId) {
    int openRequests = openRequestsInZone(zoneId);
    int availableDrivers = availableDriversInZone(zoneId);
    if (availableDrivers == 0) {
        return 3.0;
    }
    double ratio = (double) openRequests / availableDrivers;
    if (ratio > 3) {
        return 2.5;
    } else if (ratio > 2) {
        return 1.8;
    } else if (ratio > 1.5) {
        return 1.3;
    }
    return 1.0;
}
```

`RideService` then selects `SurgeFareCalculator(multiplier)` for rides in that zone.

### 3. "How does ride cancellation by either party work?"

Cancellation is valid in REQUESTED or MATCHED states. If a driver cancels a matched ride, the system should rematch with the next closest driver before giving up. Track a `cancelled_by_drivers` list on the Ride to skip already-rejected drivers.

```java
// Ride gains a private Set<String> skippedDrivers field (lazily initialized)
// to track drivers who have already cancelled or rejected this ride.

public void driverCancel(String rideId, String driverId) {
    Ride ride = rides.get(rideId);
    if (ride.getStatus() != RideStatus.MATCHED) {
        throw new IllegalStateException("Driver can only cancel a matched ride");
    }
    ride.getDriver().setStatus(DriverStatus.AVAILABLE);
    ride.getSkippedDrivers().add(driverId);
    ride.setDriver(null);
    ride.setStatus(RideStatus.REQUESTED);

    // Attempt rematch excluding skipped drivers
    List<Driver> candidates = new ArrayList<>();
    for (Driver d : drivers.values()) {
        if (!ride.getSkippedDrivers().contains(d.getDriverId())) {
            candidates.add(d);
        }
    }
    Driver newDriver = matching.findDriver(ride.getPickup(), candidates);
    if (newDriver != null) {
        ride.setDriver(newDriver);
        ride.setStatus(RideStatus.MATCHED);
        newDriver.setStatus(DriverStatus.ON_RIDE);
    }
}
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

---

## Related

**Patterns applied here**

- [Factory Pattern](../../03-design-patterns/01-creational/factory-pattern.md)
- [Observer Pattern](../../03-design-patterns/03-behavioral/observer-pattern.md)
- [State Pattern](../../03-design-patterns/03-behavioral/state-pattern.md)
- [Strategy Pattern](../../03-design-patterns/03-behavioral/strategy-pattern.md)

**SOLID focus**: [Single Responsibility](../../02-solid-principles/01-single-responsibility.md) · [Dependency Inversion](../../02-solid-principles/05-dependency-inversion.md)

**Concurrency**: [Concurrency Patterns](../../04-concurrency/concurrency-patterns.md) · [Futures & Async Patterns](../../04-concurrency/futures-async-patterns.md)

**Practice next**

- [Design Food Delivery](../02-frequent-problems/14-design-food-delivery.md)
- [Design Mentorship Platform](18-design-mentorship-platform.md)

All three match supply to demand in real time.

**Frameworks**: [LLD Template](../../../07-interview-templates/01-frameworks/02-lld-template.md) · [UML Diagrams](../../uml-diagrams.md) · [OOP Four Pillars](../../01-oop-fundamentals/four-pillars.md)
