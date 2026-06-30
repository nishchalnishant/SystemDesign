---
module: 05-hld-problems
topic: Hard
status: unread
tags: [05-hld-problems, system-design, hard, uber, ride-sharing, geospatial, matching, surge-pricing]
---
# Design a Ride-Sharing Service (Uber)

> **Difficulty**: Hard | **Asked at**: Uber, Lyft, DoorDash, Amazon

---

## Problem Statement

Design a ride-sharing platform like Uber. Riders request rides; drivers accept and complete trips. The system must match riders to nearby available drivers in seconds, provide real-time ETA, track trip progress, and implement surge pricing during high demand.

---

## Functional Requirements

1. **Ride request**: Rider requests a ride from origin to destination; sees ETA and price
2. **Driver matching**: System matches rider to the nearest available driver
3. **Trip tracking**: Real-time GPS updates from driver; rider sees driver location on map
4. **Pricing**: Dynamic pricing based on demand/supply; surge multiplier during peak
5. **Trip completion**: Driver marks trip complete; payment charged; rating exchanged
6. **Driver availability**: Drivers go online/offline; indicate availability

---

## Non-Functional Requirements

- **Scale**: 5M concurrent drivers; 10M concurrent riders; 1M trips/hour
- **Latency**: Match driver within 5 seconds of ride request; GPS update propagation < 2s
- **Availability**: 99.99% — ride requests must succeed even during partial failures
- **Geo accuracy**: Driver location accurate within 10 meters
- **Matching**: Assign the nearest available driver (within 5 km); re-match if driver declines

---

## Core Entities

| Entity | Key Fields |
|--------|-----------|
| `Rider` | rider_id, name, phone, payment_method_id, rating |
| `Driver` | driver_id, name, vehicle_info, rating, status (available/busy/offline) |
| `Trip` | trip_id, rider_id, driver_id, origin, destination, status, price, started_at, completed_at |
| `DriverLocation` | driver_id, lat, lng, heading, speed, timestamp |
| `PriceEstimate` | trip_id, base_price, surge_multiplier, total_price, valid_until |

---

## API Design

```http
POST /api/v1/rides/request
Body: { "origin": { "lat": 37.4, "lng": -122.1 }, "destination": { "lat": 37.7, "lng": -122.4 }, "ride_type": "uberx" }
Response 201: {
  "trip_id": "t123",
  "driver": { "name": "Alice", "vehicle": "Toyota Prius", "rating": 4.9, "eta_minutes": 4 },
  "price": { "estimate": "$12-15", "surge_multiplier": 1.2 }
}

POST /api/v1/drivers/location
Body: { "driver_id": "d456", "lat": 37.41, "lng": -122.09, "heading": 90, "speed_kmh": 35 }
Response 200: { "status": "ok" }

GET /api/v1/trips/{trip_id}/driver-location
Response 200: { "lat": 37.41, "lng": -122.09, "eta_seconds": 240 }

POST /api/v1/trips/{trip_id}/complete
Body: { "driver_id": "d456", "final_distance_km": 12.4 }
Response 200: { "final_price": 13.50, "receipt_url": "..." }
```

---

## High-Level Design

```
Rider App
  │ POST /rides/request
  ▼
Ride Matching Service
  │ 1. Compute price estimate (base + surge)
  │ 2. Query Location Service: find drivers within 5km
  │ 3. Rank by ETA (not just distance)
  │ 4. Send match request to top driver
  │ 5. If declined: try next driver (up to 3 times)
  │ 6. Notify rider of match (push notification)
  │
  ▼
Driver App                            Rider App
  │ Receive match request             │ Sees driver location on map
  │ Accept/decline within 15s         │ Real-time updates via WebSocket
  │ Send GPS pings every 4s           │
  ▼                                   │
Location Service                      │
  │ Driver GPS pings → Redis           │
  │ Geo-indexed: H3 hexagons          │
  │ Serve "drivers near lat/lng"  ─────┘
  │
  ▼
Storage:
  Redis: driver locations (geo index), trip state, surge cache
  PostgreSQL: trips, users, drivers, payments
  Kafka: trip-events, location-updates, payment-events
```

---

## Deep Dive 1: Geospatial Driver Indexing

**Problem**: A rider requests a ride at (37.4, -122.1). Find all available drivers within 5 km. With 5M concurrent drivers, scanning all driver locations for every request is O(5M) — too slow.

**H3 hexagonal indexing** (Uber's actual approach):
- H3 divides the Earth into hexagonal cells at multiple resolution levels
- Resolution 8: ~460m diameter hexagons (city-block scale)
- Resolution 9: ~170m diameter hexagons

**Data structure**: For each driver, store their current H3 cell:
```redis
HSET driver_location:d456 lat 37.41 lng -122.09 h3_r8 "8830e1c2b9fffff" updated_at 1735689600
SADD h3:8830e1c2b9fffff d456  # drivers in this hexagon
```

**Query**: For a ride request at (37.4, -122.1):
1. Compute the H3 cell at resolution 8 for the origin
2. Get all neighboring hexagons within 5 km (H3 k-ring function, k=5 at resolution 8)
3. `SUNIONSTORE nearby_drivers h3:{hex1} h3:{hex2} ... h3:{hex57}` → set of nearby driver IDs
4. Filter: `HGET driver_location:{d_id} status == "available"`
5. Rank by ETA (compute distance for each candidate)

**TTL**: If a driver's location isn't updated for 30 seconds, remove from the H3 index (driver went offline or lost signal).

**Why H3 over geohash**: H3 hexagons have uniform neighbors (6 neighbors all at equal distance); geohash squares have 8 neighbors with varying distances. H3 avoids the "edge artifact" where two nearby points are in cells that aren't adjacent.

---

## Deep Dive 2: Driver Matching and Assignment

**Problem**: 50 drivers are within 5 km of the rider. How do you pick the best one? If the closest driver declines, how do you handle re-matching without the rider waiting 15+ seconds per decline?

**Ranking candidates**: Don't rank by straight-line distance — rank by ETA (estimated time to arrival). Two drivers 2 km away may have different ETAs due to traffic and road geometry.
```python
def rank_candidates(drivers, origin):
    for d in drivers:
        d.eta = routing_service.get_eta(d.location, origin)  # cached route lookup
    return sorted(drivers, key=lambda d: d.eta)
```

**Sequential offer with timeout**:
1. Offer trip to driver #1; set 15-second timeout
2. If accepted → match complete
3. If declined or timeout → offer to driver #2 (pre-fetched)
4. Up to 3 offers; if all decline → notify rider "searching..." → try next batch

**Pre-fetching**: While waiting for driver #1's response, pre-compute ETAs for drivers #2, #3, #4. When driver #1 declines, driver #2 offer is sent immediately (no extra delay).

**Driver state management** (Redis):
```redis
SET driver_state:d456 "offered:trip:t123" EX 15  # lock during offer window
# On accept: SET driver_state:d456 "busy:trip:t123"
# On decline/timeout: DEL driver_state:d456 → driver available again
```

---

## Deep Dive 3: Surge Pricing

**Problem**: It's New Year's Eve at midnight. 10,000 riders request rides in Manhattan. Only 500 available drivers. How do you implement surge pricing that discourages excess demand and incentivizes more drivers to come online?

**Supply-demand ratio per geospatial cell**:
```python
def compute_surge(h3_cell, time_window=5):  # minutes
    demand = count_ride_requests(h3_cell, last_minutes=time_window)
    supply = count_available_drivers(h3_cell)
    ratio = demand / max(supply, 1)  # avoid div by zero
    if ratio < 1.0: return 1.0      # no surge
    if ratio < 2.0: return 1.5      # 1.5x
    if ratio < 3.0: return 2.0      # 2x
    return min(ratio, 5.0)          # cap at 5x
```

**Flink streaming job**: Reads from Kafka (ride-request-events + driver-location-events). Computes supply/demand per H3 cell every 60 seconds. Writes surge multipliers to Redis `surge:{h3_cell}` with TTL 90s.

**Price transparency**: Show surge multiplier to rider before they confirm. If multiplier > 2x, require explicit confirmation ("Prices are 2.4x higher than normal. Confirm?").

**Driver incentive**: Higher surge attracts off-duty drivers. Push notification to nearby offline drivers: "Surge pricing active near you — go online to earn 2x!"

**Price lock**: Once a rider confirms the surge price, lock it for their trip even if surge drops. Store `confirmed_surge_multiplier` in the trip record.

---

## Interviewer Questions by Level

**Junior**:
- How does a ride-sharing app know which drivers are near a rider?
- What is surge pricing? Why does it help balance supply and demand?
- What happens if the first driver declines a trip request?

**Mid-level**:
- How does H3 hexagonal indexing enable efficient geospatial driver lookup?
- How do you rank multiple candidate drivers? Why is ETA better than straight-line distance?
- How do you update 5M concurrent driver locations in Redis efficiently?

**Senior**:
- Design the real-time driver location tracking system — from GPS ping to rider's map display. What is the end-to-end latency budget?
- Design surge pricing computation — how do you compute supply/demand ratios in real-time across all geo cells globally?
- How do you handle driver supply in a new city where Uber has no history? What are cold-start problems and how do you solve them?
- Design the payment and trip completion flow — from driver tapping "End Trip" to rider's card being charged.
