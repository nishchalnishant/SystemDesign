# Design Uber/Grab (Ride Sharing)

> **Difficulty**: Hard
> **Topics**: Geospatial Indexing (QuadTree/Geohash), Real-time Location Updates, Matching Algorithms, State Machine
> **Time**: 60-75 minutes
> **Companies**: Uber, Lyft, Grab, DoorDash

---

## Problem Statement

Design a ride-sharing service where:
- **Riders** can book a ride and see nearby drivers.
- **Drivers** can accept rides and navigate to pickup/drop-off.
- **System** matches riders with nearest available drivers.

**Scale:**
- 100M Users, 1M Drivers.
- 1M Active Daily Rides.
- **Critical Requirement**: Real-time location tracking & matching.

---

## Analogy

A taxi dispatcher with a city map. Riders call in; the dispatcher finds the nearest available driver and assigns them. Simple for 10 drivers and 50 riders.

At Uber's scale: 1 million drivers updating their GPS every 5 seconds = 200,000 location writes per second. A rider requests a ride and must be matched in under 500ms. The driver might be 300 meters away but on the other side of a blocked road (Euclidean distance != travel time). While you're matching Rider A to Driver X, Rider B might also be matching to the same Driver X — you can't assign the same driver twice.

The dispatcher analogy breaks down: no human dispatcher can handle this. The system itself is the dispatcher, and it must work at city scale, globally, in real time.

---

## What Breaks Without This System?

Without real-time geospatial indexing and matching, finding the nearest driver means querying every driver's latitude/longitude stored in a relational table — a full table scan of 1M rows repeated 200K times/sec as drivers update their location. The DB melts at ~100K QPS, matching takes seconds instead of milliseconds, and race conditions cause the same driver to be double-booked across concurrent rider requests. The business fails: riders wait indefinitely and drivers never get assignments.

---

## Derive the Architecture

**1 server, SQL with lat/long columns**: Store each driver's location in `drivers(id, lat, lng, status)`. On rider request, `SELECT * WHERE status='available' ORDER BY distance(lat,lng,rider_lat,rider_lng) LIMIT 5`. Works for 1K drivers. Breaks when: 1M drivers × 200K location updates/sec = 200K writes/sec → PostgreSQL saturates at ~10K writes/sec. Fix: move live driver locations out of SQL into an in-memory geospatial store.

**Redis Geo + in-memory location store**: Driver location updates write to Redis `GEOADD drivers_live {lng} {lat} {driver_id}`. Nearby search uses `GEORADIUS` — O(log N + M) where M = results. Handles 200K writes/sec across a Redis cluster. Breaks when: 1M drivers in a small area (downtown Manhattan) means `GEORADIUS 2km` returns 10K candidates — ranking all 10K by ETA requires 10K map API calls per rider request at 500ms each. Fix: coarser geo-indexing to narrow candidates, then ETA only for top 10.

**Geohash/QuadTree pre-segmentation**: Divide the city into Geohash cells (~150m × 150m at precision 7). Each cell maintains a small list of available drivers. A rider request queries 9 neighboring cells (3×3 grid) → typically 5–20 candidate drivers. ETA computed only for those candidates. Handles 1K ride requests/sec. Breaks when: two riders simultaneously match to the same nearest driver — without a lock, both get assigned. Fix: distributed lock (Redis `SET driver:{id} LOCK NX EX 10`) acquired before sending the driver offer.

**Distributed locking for assignment**: Lock expires in 10 seconds (driver accept window). If driver declines or times out, lock releases and driver re-enters the pool. Eliminates double-booking. Handles the race condition at scale. Breaks when: a location update service node crashes with 10K driver connections — those drivers appear offline instantly, degrading supply visibility. Fix: decouple location ingestion from matching; use a separate Location Service writing to Redis, so a crash in one component doesn't cascade.

**Dedicated services: Location, Matching, Trip**: Location Service ingests GPS at 200K/sec, writes to Redis Geo; Matching Service reads from Redis and manages locks; Trip Service owns the ride state machine (REQUESTED → ACCEPTED → IN_PROGRESS → COMPLETED) persisted in a relational DB for durability. Each scales independently. Breaks when: Trip Service writes at ~1K rides/sec overwhelm a single PostgreSQL writer as the business scales globally. Fix: shard trips by rider_id or region; each region's DB handles its own ride volume.

---

## Why This Is Hard

1. **Geospatial indexing at write-heavy scale**: 1M drivers × 1 update/4 seconds = 250K writes/second to a geo-indexed store. Standard SQL with lat/long columns cannot handle this. You need an index structure (Geohash, S2, QuadTree) that makes "find all drivers within 2km" O(log N) not O(N).
2. **Race conditions in driver matching**: Two riders simultaneously match to the same nearest driver. Without a distributed lock, both get assigned to the same driver. Acquiring a lock per driver match at 250K QPS is expensive — you need lightweight, expiring distributed locks.
3. **Distance ≠ ETA**: The nearest driver (by GPS distance) might be 5 minutes away due to traffic, while a slightly farther driver is 2 minutes away. A real matching service must use road-graph ETA, not Euclidean distance — which requires a maps API call for each candidate.
4. **Mobile network unreliability**: Drivers are on mobile networks. GPS updates arrive late, out of order, or not at all. A driver who hasn't pinged in 10 seconds might be in a tunnel, or their phone died. The system must handle stale location data gracefully.
5. **State machine consistency**: A trip goes through REQUESTED → ACCEPTED → ARRIVED → IN_PROGRESS → COMPLETED. If the driver's app crashes mid-trip, state must be recoverable. If the network partitions, the same trip must not be in two different states on two different services.

---

## Requirements

### Functional
1. Update driver location (every 3-5 seconds).
2. Find nearby drivers (Geospatial search).
3. Request a ride & match with driver.
4. Calculate Estimated Time of Arrival (ETA).
5. Handle Trip State (Requested → Accepted → InProcess → Completed).

### Non-Functional
- **Low Latency**: Location updates visible < 5s. Match < 2s.
- **Consistency**: Driver assigned to only ONE rider (ACID on assignment).
- **Availability**: High availability for Booking Service.
- **Resilience**: Handle network partitions (mobile networks are flaky).

---

## Scale Estimation

```
Location Updates:
1M drivers × (1 ping/4s) = 250K writes/sec to Location Service

Ride Matching:
1M active rides/day ÷ 86,400s = ~12 match requests/sec
Peak factor 10x = 120 match requests/sec (manageable)

Storage:
Trip metadata: 1M rides/day × 1KB × 365 = 365 GB/year
Location history: 1M drivers × 20 KB/day = 20 GB/day → Cassandra (time series)
Driver/Rider metadata: PostgreSQL (ACID, relatively small)

Bandwidth:
Location pings: 250K × 200 bytes (lat, long, heading, speed) = 50 MB/sec ingest
```

---

## Data Architecture

### 1. Geospatial Storage (The Core Problem)

**Challenge**: How to efficiently store and query "Drivers within 2km"?

#### Option A: SQL (lat, long columns)
```sql
SELECT * FROM drivers
WHERE lat BETWEEN x1 AND x2
  AND long BETWEEN y1 AND y2;
```
- **Cons**: Full table scan or 2D index scan — extremely slow at millions of drivers. Not suitable.

#### Option B: Geohash (Redis)
```
Concept: Divide the world into a grid of rectangles.
         Each cell is encoded as a Base32 string.

"u4pruydqqv" → specific 10m × 10m spot
"u4pruy"     → larger ~1km × 1km area (prefix)

Redis GEOADD / GEORADIUS:
GEOADD driver_locations 37.7749 -122.4194 "driver_42"
GEORADIUS driver_locations 37.7751 -122.4190 2 km ASC COUNT 10

Pros: Extremely fast prefix search; built into Redis.
Cons: Edge cases — two drivers at the border of two cells have very different
      hashes despite being meters apart. Solution: also check 8 neighboring cells.
```

#### Option C: QuadTree (In-Memory)
```
Structure: Tree where each node has 4 children (NW, NE, SW, SE).
           Leaf nodes store Driver IDs.

Dense city: nodes split down to 10m grid cells.
Rural areas: nodes remain large (no drivers to index).

Pros: Dynamic splitting adapts to driver density. Fast k-NN search.
Cons: Hard to distribute across multiple servers;
      synchronizing inserts/deletes across partitioned QuadTree is complex.
```

#### Option D: Google S2 Geometry (Production Choice)
```
Uses Hilbert space-filling curves to map 2D coordinates to 1D string.
Preserves spatial locality: nearby cells have nearby string keys.
Used by Uber, Google Maps, Lyft in production.

Similar implementation to Geohash but with better mathematical properties
for range queries and edge-case handling.
```

**Selected**: **Redis GEOSPATIAL** for real-time driver locations (in-memory, fast writes, GEORADIUS built-in). S2/Geohash for persistent location history in Cassandra.

---

## System Components

### 1. Location Service (Write-Heavy)

```
Drivers ping lat/long every 4 seconds.
Traffic: 1M × 0.25 = 250K writes/sec.

Optimization:
- Write ONLY to Redis (volatile, TTL 10s per driver).
- Do NOT write every ping to PostgreSQL — too expensive.
- Kafka: Publish location event every ping.
- Cassandra consumer: Reads from Kafka, writes to Cassandra every 30s
  (for trip replay, dispute resolution, analytics).

Driver record in Redis:
GEOADD driver_locations {lng} {lat} driver_{id}
HSET driver_state:{id} status AVAILABLE heading 270 speed 35 updated_at 1700000000
EXPIRE driver_state:{id} 10  # Expire after 10s of silence → driver appears offline
```

### 2. Driver Matching Service

```
Rider requests ride for location (lat, long):

Step 1: Query Redis for nearby available drivers
GEORADIUS driver_locations {lng} {lat} 2 km ASC COUNT 10
→ [driver_A (0.3km), driver_B (0.8km), driver_C (1.2km)]

Step 2: Filter by status
HGET driver_state:A → status: AVAILABLE (keep)
HGET driver_state:B → status: ON_TRIP (skip)

Step 3: Get ETA (optional, production systems)
Call Maps API for driving ETA from each candidate to rider.
Sort by ETA, not raw distance.

Step 4: Attempt assignment (with distributed lock)
1. Acquire Redlock on driver_A.
2. Send notification to driver_A.
3. If driver_A accepts within 30s:
   → Create Trip in PostgreSQL (ACID).
   → Set driver_state:A status = ON_TRIP.
   → Release lock.
4. If driver_A rejects/times out:
   → Release lock, repeat for driver_B.
```

### 3. Trip Service (State Machine)

```
States:
REQUESTED → ACCEPTED → DRIVER_ARRIVED → IN_PROGRESS → COMPLETED
                ↓                             ↓
            CANCELLED                    CANCELLED

Database: PostgreSQL (ACID required for payment integration and state transitions).

trips table: trip_id, rider_id, driver_id, status, pickup_loc, dropoff_loc,
             fare, created_at, accepted_at, started_at, completed_at

State transitions are guarded by DB transactions:
UPDATE trips SET status = 'ACCEPTED', accepted_at = NOW()
WHERE trip_id = X AND status = 'REQUESTED';

If 0 rows updated: trip already moved to another state (race condition caught).
```

---

## Architecture Diagram

```
      Rider App               Driver App
         │                        │
         ▼                        ▼
    ┌──────────┐            ┌──────────┐
    │API Gateway│           │API Gateway│
    └────┬─────┘            └────┬─────┘
         │                       │
         ▼ (WebSocket)           ▼ (UDP / HTTP/2)
   ┌─────────────┐        ┌─────────────┐
   │Notification │        │ Location    │
   │  Service    │        │  Service    │ (250K writes/sec)
   └─────────────┘        └──────┬──────┘
                                 │ write
                          ┌──────▼──────┐
                          │Redis Cluster│ (GEO Index + Driver State)
                          │ (Ephemeral) │
                          └─────────────┘
                                 │        ┌─────────────┐
                                 │        │   Kafka     │ (Location events)
                                 │        └──────┬──────┘
                                 │               │
                                 │        ┌──────▼──────┐
                                 │        │  Cassandra  │ (Location History)
                                 │        └─────────────┘
         ┌───────────────────────┘
         │ GEORADIUS query
         │
   ┌─────────────┐        ┌─────────────┐
   │ Matching    │───────▶│  Trip DB    │
   │ Service     │        │ (PostgreSQL)│
   └─────────────┘        └─────────────┘
         │
         │ distributed lock
         ▼
   ┌─────────────┐
   │ Redis Lock  │ (Redlock per driver during matching)
   └─────────────┘
```

---

## Deep Dive: Location Updates

**Problem**: 1M Drivers sending updates every 3s = 250K QPS (Writes).
**Solution**:
1. **Driver App**: Use UDP (no connection overhead) or persistent gRPC stream for location pings.
2. **Load Balancer**: Route by driver_id to sticky location server (reduces Redis fan-out).
3. **Location Service**:
   - Update `Redis GEO` for real-time geo queries (TTL 10s per driver).
   - Publish to **Kafka** topic `driver-locations` for async consumers.
   - Do NOT write to Cassandra synchronously — use Kafka consumer for that.
4. **Only AVAILABLE/ON_TRIP drivers** are written to the GEO index. Offline drivers are excluded.

---

## Handling Race Conditions (Matching)

**Scenario**: Riders A and B simultaneously match to the same Driver X.

```
Without lock:
Thread 1: Query Redis → Driver X available
Thread 2: Query Redis → Driver X available
Thread 1: Notify Driver X
Thread 2: Notify Driver X
Driver X: Accepts → two trips created, driver assigned twice

With Redlock:
Thread 1: SET driver_lock:X {token1} NX PX 30000 → SUCCESS (acquired)
Thread 2: SET driver_lock:X {token2} NX PX 30000 → FAIL (already locked)
Thread 2: Try Driver Y instead.
Thread 1: Driver X accepts → create trip → release lock by deleting key with token1 match.
```

**Redlock implementation:**
```
-- Acquire (atomic: SET only if NOT EXISTS)
SET driver_lock:{driver_id} {unique_token} NX PX 30000

-- Release (atomic: only delete if we own the lock)
if redis.GET(driver_lock:{driver_id}) == unique_token:
    redis.DEL(driver_lock:{driver_id})
```

The 30-second TTL acts as a safety net: if the matching server crashes, the lock auto-expires and the driver becomes matchable again.

---

## Surge Pricing

```
Concept: Price multiplier when demand >> supply in an area.

Implementation:
1. Partition world into S2 cells (e.g., ~1km × 1km each).
2. Count active ride requests per cell (stream processing via Flink/Spark Streaming).
3. Count available drivers per cell (from Redis GEO).
4. Surge multiplier = Request_Count / Driver_Count (clipped to max 5x).
5. Write cell → multiplier to Redis (TTL 60s, refreshed continuously).
6. Pricing Service reads multiplier at trip creation time.

Surge display:
- Show surge zones on rider map (shaded polygons).
- Display estimated wait time per zone.
```

---

## Failure Scenarios

### Redis GEO Node Failure
```
Impact: Cannot query for nearby drivers in affected hash slots.
Duration: 1-2 seconds for replica promotion (Redis Cluster failover).
Mitigation:
- Redis Cluster with replicas per master.
- During failover: return "No drivers available" to rider (graceful degradation).
- Location data is volatile (rebuilt from driver pings within seconds).
```

### Matching Service Crash Mid-Match
```
Scenario: Lock acquired on driver_A; server crashes before trip creation.

Effect: Lock has 30s TTL → auto-expires.
Driver A: 30 seconds of "limbo" (locked but no one sending them a job).
Recovery: After TTL expiry, driver_A is matchable again automatically.
```

### Driver App Loses Connectivity
```
Scenario: Driver enters tunnel; no GPS pings for 60 seconds.

Effects:
- Redis TTL (10s) expires → driver appears offline in GEO index.
- No new riders are matched to this driver.
- If driver was on a trip: Trip Service detects no location updates → mark trip as "location unknown" (still active).
- When driver reconnects: Resume sending pings; driver reappears in GEO index.
```

### Duplicate Trip Creation (DB Race Condition)
```
Two matching threads both complete the lock and create a trip:

Prevention:
CREATE TABLE trips (
    ...
    driver_id BIGINT,
    status VARCHAR(20),
    UNIQUE constraint on (driver_id, status) WHERE status IN ('ACCEPTED', 'IN_PROGRESS')
);
-- Only one active trip per driver enforced at DB level.
```

---

## Interview Talking Points

**Q: "How to handle high demand (Surge Pricing)?"**
- A: "Partition the world into S2 cells. A stream processing job (Flink/Spark) reads the location and request event streams, counts active requests and available drivers per cell in real time, and writes a multiplier to Redis. The pricing service reads the multiplier at trip creation time. Cells are small enough that local demand/supply mismatch is visible, but large enough to have statistical significance."

**Q: "Rider cancels while Driver is arriving?"**
- A: "State machine. Trip must be in ACCEPTED or DRIVER_ARRIVED state for cancellation to be valid. On cancellation: UPDATE trips SET status = 'CANCELLED' WHERE trip_id = X AND status IN ('ACCEPTED', 'DRIVER_ARRIVED'). Notify driver via WebSocket push. Evaluate cancellation fee based on how far driver has traveled (business logic, applied async)."

**Q: "How do you prevent the same driver being assigned to two riders?"**
- A: "Three layers of protection: (1) Redlock on driver_id during matching window; (2) Atomic status update in Redis (AVAILABLE → LOCKED) before notifying driver; (3) DB unique constraint on active trips per driver as a final safety net. Defense in depth — any one layer alone could have race conditions, all three together make it effectively impossible."

**Q: "Why not store driver locations in PostgreSQL?"**
- A: "250K writes/second of lat/long updates with an index that needs to support radius queries — PostgreSQL would be overwhelmed. Redis GEO gives sub-millisecond GEORADIUS queries and handles 250K writes/second easily because it's purely in-memory. The data is also volatile by nature — a location from 30 seconds ago is useless. Redis TTL handles expiry automatically."

**Q: "What's your ETA calculation strategy?"**
- A: "For candidate filtering, use Euclidean distance (fast, rough). For final matching decision, call the Maps service (Google Maps Matrix API or in-house routing) to get real ETA considering traffic and road topology. This adds ~50-100ms to the match latency but significantly improves match quality. Cap the Maps API calls to top 3-5 candidates to control cost and latency."

---

## Interview Questions Asked

### Uber
1. **"Design Uber's dispatch system."** → Tests geospatial indexing and real-time matching; key answer: Redis GEO for driver location storage, GEORADIUS queries for nearby drivers, Redlock for atomic driver assignment, WebSocket for real-time driver notification.

### Lyft
1. **"Design real-time driver matching."** → Tests latency and correctness under concurrency; key answer: two-phase matching — filter by proximity (Redis GEO), rank by ETA (Maps API on top-5 candidates), atomic lock-and-assign with DB unique constraint as safety net.

### Common Follow-ups
1. **"Geohash vs. H3 vs. quadtree for spatial indexing — what do you choose?"** → Tests spatial data structure trade-offs; Geohash: simple, string-prefix neighbors but irregular cell shapes; H3 (Uber's hexagonal grid): uniform area cells, better for demand heatmaps; Quadtree: adaptive density, good for non-uniform distributions; Redis GEO uses geohash internally — pragmatic default choice.
2. **"How do you compute ETA at scale?"** → Tests approximation vs. accuracy trade-off; coarse ETA: Euclidean distance + average speed for filtering; precise ETA: Google Maps Distance Matrix API (or in-house OSRM) called only for top 3-5 candidates to control cost and latency; results cached per (origin cell, destination cell) pair for 30s.
3. **"How does surge pricing get triggered?"** → Tests stream processing design; Flink job reads driver location and ride request streams, computes supply/demand ratio per S2/H3 cell in a 5-minute rolling window, writes multiplier to Redis; pricing service reads multiplier at trip creation time; multiplier decays if demand normalizes.
4. **"How do you predict driver supply?"** → Tests ML in ops systems; LSTM or gradient-boosted model trained on historical supply by (cell, hour, day-of-week, weather, events); predictions written to Redis hourly; used to proactively incentivize drivers to reposition to high-demand areas before surge occurs.
