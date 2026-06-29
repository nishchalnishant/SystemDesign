---
module: 05-hld-problems
topic: Medium
status: interview-ready
tags: [05-hld-problems, system-design, medium]
---
# Design Uber

> **Difficulty**: Medium
> **Topics**: Geo-Indexing, Real-Time Location, Matching, Dispatch
> **Time**: 45 min
> **Companies**: Uber, Lyft, Amazon

---

## Clarifying Questions

1. "Are we designing the full Uber platform or focusing on ride matching and driver dispatch?"
2. "What's the scale — 5M trips/day, 3M active drivers globally?"
3. "Do we need real-time ETA and route optimization, or just driver matching?"
4. "What consistency model for driver matching — can two riders match the same driver?"
5. "Do we need surge pricing, or just the core dispatch?"
6. "What's the driver location update frequency — every 4 seconds?"

---

## Back-of-Envelope

```
5M trips/day = ~58 trips/sec avg
3M active drivers update location every 4s
  → 3M / 4 = 750K location writes/sec

Location storage per driver: lat/lng/timestamp = ~50 bytes
750K writes/sec → Redis handles this (500K ops/sec per node, Cluster needed)

Rider search radius: 5km, need drivers within radius
  Geohash 4-char prefix ≈ 39km × 20km = city-level sharding

Peak: 5PM surge → ~3× = 174 trips/sec, 2.25M location updates/sec
```

---

## APIs

```
// Driver: update location (called every 4s by driver app)
POST /api/v1/drivers/{driver_id}/location
  { "lat": 37.7749, "lng": -122.4194, "heading": 90, "speed": 30 }
  -> { "status": "ok" }

// Rider: request a ride
POST /api/v1/rides
  { "pickup": { "lat": ..., "lng": ... }, "dropoff": { "lat": ..., "lng": ... } }
  -> { "ride_id": "...", "status": "searching", "eta_seconds": 240 }

// Driver: accept or reject ride request
POST /api/v1/rides/{ride_id}/accept
  -> { "ride_id": "...", "rider_name": "...", "pickup_address": "..." }

// Ride status
GET /api/v1/rides/{ride_id}
  -> { "status": "en_route", "driver_eta_seconds": 120, "driver_location": {...} }
```

---

## Architecture

```
Driver App (every 4s)
  |-- POST /drivers/{id}/location
  |
Location Service
  |-- GEOADD drivers:active {lng} {lat} {driver_id}  (Redis Geo per city shard)
  |-- HSET driver:{driver_id} status lat lng updated_at
  |-- Kafka "location.updated" for analytics + ETA service

Rider App
  |-- POST /rides
  |
Dispatch Service
  |-- GEORADIUS drivers:active {rider_lat} {rider_lng} 5km ASC COUNT 20
  |-- Filter: status=AVAILABLE (HGET driver:{driver_id} status)
  |-- For each candidate: atomic claim via Lua script
  |     HGET driver:{driver_id} status == "AVAILABLE"
  |     HSET driver:{driver_id} status "MATCHED" ride_id {ride_id}
  |     (only one dispatcher can set status=MATCHED first)
  |-- On success: send ride request to driver via WebSocket push

Driver WebSocket Gateway
  |-- Persistent connections from all active drivers
  |-- Pushes ride requests to matched driver
  |-- Receives accept/reject

Rides DB (PostgreSQL)
  |-- stores trips: ride_id, rider_id, driver_id, status, pickup, dropoff, fare
  |-- status: REQUESTED → ACCEPTED → IN_PROGRESS → COMPLETED

Surge Service
  |-- Reads supply/demand per geohash cell every 30s from Redis
  |-- Writes surge_multiplier:{geohash4} to Redis
  |-- Dispatch service reads multiplier at ride request time
```

---

## Data Model

```sql
-- Trips (PostgreSQL)
CREATE TABLE trips (
    trip_id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rider_id        BIGINT NOT NULL,
    driver_id       BIGINT,
    status          VARCHAR(20) NOT NULL,  -- REQUESTED/ACCEPTED/IN_PROGRESS/COMPLETED/CANCELLED
    pickup_lat      DECIMAL(9,6),
    pickup_lng      DECIMAL(9,6),
    dropoff_lat     DECIMAL(9,6),
    dropoff_lng     DECIMAL(9,6),
    fare_amount     DECIMAL(8,2),
    surge_multiplier DECIMAL(4,2) DEFAULT 1.0,
    requested_at    TIMESTAMPTZ DEFAULT NOW(),
    accepted_at     TIMESTAMPTZ,
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ
);
CREATE INDEX ON trips(rider_id, requested_at DESC);
CREATE INDEX ON trips(driver_id, status);

-- Drivers (PostgreSQL, static info)
CREATE TABLE drivers (
    driver_id       BIGINT PRIMARY KEY,
    name            VARCHAR(100),
    vehicle_type    VARCHAR(20),
    license_plate   VARCHAR(20),
    rating          DECIMAL(3,2)
);
```

**Redis structures:**
- `drivers:active:{city}` — Redis Geo sorted set (GEOADD, GEORADIUS)
- `driver:{driver_id}` — Hash: `{status, lat, lng, updated_at, current_trip_id}`
- `surge_multiplier:{geohash4}` — String, computed every 30s

---

## Key Design Decisions

**1. Redis Geo for driver location and proximity search**
750K location writes/sec needs in-memory storage — PostgreSQL geography queries at this rate would require massive sharding. Redis Geo (`GEOADD`/`GEORADIUS`) uses a geohash-based sorted set internally. `GEORADIUS drivers:active:sf 37.77 -122.41 5 km ASC COUNT 20` returns 20 nearest drivers in O(log N + K). One Redis Geo set per city (geohash 4-char prefix ≈ city level) → shards naturally by geography.

**2. Atomic driver claim via Lua script**
Two dispatchers simultaneously find driver D5 as the best match for two different riders. Without atomicity: both claim D5 and two riders get the same driver. Fix: Redis Lua script executes atomically:
```
if driver_status == "AVAILABLE":
    set driver_status = "MATCHED", ride_id = X
    return "claimed"
else:
    return "already_taken"
```
Lua scripts run atomically on Redis (single-threaded execution within the script). Losing dispatcher moves to the next candidate driver.

**3. Driver state machine**
AVAILABLE → MATCHED (dispatcher claims) → EN_ROUTE (driver accepts) → ON_TRIP (pickup confirmed) → AVAILABLE (trip complete). State stored in Redis Hash for fast reads. PostgreSQL `trips` table is the durable record. Redis is the hot working state; PostgreSQL is the audit trail.

**4. Geohash city-level sharding for surge pricing**
Supply = GEORADIUS result count for a geohash cell. Demand = active ride requests in that cell in the last 5 minutes. Surge multiplier = f(demand/supply ratio) — lookup table (1.0x at 1:1, 2.5x at 5:1). Computed every 30 seconds by surge service, written to Redis. Read by dispatch service at ride request time. Multiplier locked in at request time — rider sees no price change after accepting.

---

## Deep Dives

**ETA calculation**
ETA is not just distance/speed. Uber uses real-time traffic graph (sourced from aggregate driver location data, HERE/Google Maps traffic overlay). ETA Service: input=(driver_location, pickup_location) → road-network graph traversal (Dijkstra or A*) with real-time edge weights. Result cached in Redis for 30s (same route query doesn't recompute). If driver deviates significantly from route (>2 min delay): ETA recomputed and pushed to rider via WebSocket.

**Ghost drivers (location data stale)**
Driver app crashes, location stuck at old position. Solution: TTL on driver's geo entry. If no location update within 30s: ZREM driver from `drivers:active`. Dispatcher never matches a ghost. On reconnect: driver re-registers via POST /location.

---

## Failure Scenarios

| Failure | Impact | Mitigation |
|---------|--------|------------|
| Redis Geo node failure | City-level driver search breaks | Redis Cluster with replicas; geo set rebuilt from location update stream |
| Dispatcher claims same driver twice | Two riders matched to one driver | Lua atomic claim; second claimer gets "already_taken", picks next candidate |
| Driver app crashes after match | Driver doesn't accept, rider waits | 30s accept timeout; dispatch moves to next driver automatically |
| Surge service lag | Stale surge multiplier shown | Redis TTL=60s on surge key; stale price better than no price |
| PostgreSQL trips DB down | Can't create new trips | Write-ahead: create trip record in Redis first, async flush to PostgreSQL |

---

## Interview Questions Asked

### Amazon
1. **"Design a ride-sharing system for Amazon Last Mile delivery drivers — how is it different from consumer Uber?"** → Key differences: (1) Drivers have pre-planned routes (packages to deliver), not dynamic dispatch. (2) Matching is batch (assign driver to route at start of shift), not real-time. (3) SLA is delivery time window, not minimum ETA. Architecture differences: use route optimization (TSP solver or OR-Tools) offline, not real-time GEORADIUS matching. Driver location tracking is the same (GPS every 4s to Redis). Customer notifications differ — package delivery ETA vs. ride ETA.
2. **"How does Uber's surge pricing system avoid a feedback loop — drivers see surge, flood the area, surge disappears, drivers leave?"** → Surge dampening: (1) Update surge multiplier every 30s, not real-time (prevents oscillation). (2) Cap at 5x with UX warning to riders. (3) Show drivers surge zones on a heat map but don't pin surge to their exact GPS location — reduces the incentive to congregate. (4) Surge algorithms use supply forecast (drivers currently heading to area), not just current supply — prevents overshooting.

### Uber/Lyft
1. **"Walk me through what happens in your system from when a rider taps 'Request' to when a driver arrives."** → (1) POST /rides → Dispatch Service reads rider GPS. (2) GEORADIUS on Redis: get 20 nearest AVAILABLE drivers. (3) Sort by ETA (not distance). (4) Lua claim first driver. (5) Push ride request via WebSocket to driver. (6) Driver accepts within 30s → trip status ACCEPTED, WebSocket push to rider with driver location. (7) Driver navigates to pickup; location updates every 4s → Redis → rider's app polls trip status every 5s or receives WebSocket pushes. (8) Driver arrives → pickup confirmed → status IN_PROGRESS → dropoff → COMPLETED → fare computed.

### Common Follow-ups
1. **"What happens if no driver is found within a 5km radius?"** → Expand search radius: 5km → 8km → 12km. After each GEORADIUS with no results: double radius, retry. Cap at 25km or 3 retries. If still no driver: return "no drivers available" to rider. Queue the request for 2 minutes (maybe a driver completes a trip and becomes available nearby). Notify rider with estimated wait time.
2. **"How do you handle a driver who rejects the ride request?"** → Status reverts to AVAILABLE in Redis. Dispatch service selects the next candidate from the original GEORADIUS list (ranked by ETA). Each driver gets 30s to accept. After 3 rejections for a single ride request: re-run GEORADIUS with fresh data (driver positions may have changed in the last 90s).

---

## Interviewer Follow-Up Questions

**On location tracking:**
- "750K writes/sec to Redis — how do you handle this without a single Redis node bottleneck?" → Redis Cluster with city-level sharding. Each city gets its own `drivers:active:{city_code}` Geo set. Hash slot assignment by city_code → natural geographic distribution. At 750K/sec globally, split across 200 cities → avg 3,750 writes/sec per city. A single Redis node handles 500K ops/sec — each city shard is well under capacity. Hot cities (NYC, London) may need further partitioning by geohash prefix.
- "A driver's GPS signal drops for 10 seconds then recovers. What happens to location accuracy?" → Location service detects gap (no update for 10s → remove from active geo set via TTL). On recovery: re-register with fresh GPS. During the gap: driver appears offline to dispatch — no new rides assigned. If driver was on a trip: ETA becomes stale; rider app shows "reconnecting." When GPS recovers: new location updates resume, ETA is recomputed. 10-second gaps are common in tunnels/parking garages — accepted degradation.
- "How do you prevent a driver from gaming location to collect surge pricing?" → Anomaly detection on location stream: if driver teleports (location delta > physically possible speed), flag as invalid. Invalid locations: discard, don't update Redis. If driver pattern shows repeated clustering at surge zones without completing trips: fraud detection (low acceptance rate + location gaming = penalty score). Surge eligibility based on location at ride acceptance time, not request time — driver must be in the zone when they accept, not just when they clicked the heat map.

**On matching and dispatch:**
- "You run GEORADIUS and get 20 drivers. How do you rank them?" → Primary sort: ETA to pickup (not distance — a driver on a highway 3km away may be faster than one 1.5km away in traffic). ETA computed from real-time traffic graph. Secondary: driver rating (ties broken by rating). Tertiary: recency of last trip (prefer idle drivers over drivers who just completed a trip — reduces driver fatigue). The GEORADIUS result is by distance; re-rank by ETA is done in the dispatch service after the initial geo query.
- "How does your dispatch service scale to handle 58 trips/sec globally?" → Dispatch service is stateless — all state in Redis and PostgreSQL. Scale horizontally: 58 trips/sec across 20 dispatch servers = ~3 trips/sec per server. Each dispatch is ~5 Redis calls (GEORADIUS + N HGET + 1 Lua claim) taking ~5ms total. One dispatch server can handle 200 dispatches/sec. 20 servers handle 4,000 dispatches/sec — 70× headroom. Bottleneck is Redis Cluster write throughput for the Lua claim, not the dispatch service CPU.
