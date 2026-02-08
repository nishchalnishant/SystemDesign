# Design Uber/Grab (Ride Sharing)

> **Difficulty**: Hard  
> **Topics**: Geospatial Indexing (QuadTree/Geohash), Real-time Location Updates, Matching Algorithms, State Machine  
> **Time**: 60-75 minutes  
> **Companies**: Uber, Lyft, Grab, DoorDash

## Problem Statement

Design a ride-sharing service where:
- **Riders** can book a ride and see nearby drivers.
- **Drivers** can accept rides and navigate to pickup/drop-off.
- **System** matches riders with nearest available drivers.

**Scale:**
- 100M Users, 1M Drivers.
- 1M Active Daily Rides.
- **Critical Requirement**: Real-time location tracking & matching.

## Requirements

### Functional
1. Update driver location (every 3-5 seconds).
2. Find nearby drivers (Geospatial search).
3. Request a ride & match with driver.
4. Calculate Estimated Time of Arrival (ETA).
5. Handle Trip State (Requested -> Accepted -> InProcess -> Completed).

### Non-Functional
- **Low Latency**: Location updates visible < 5s. Match < 2s.
- **Consistency**: Driver assigned to only ONE rider (ACID).
- **Availability**: High availability for Booking Service.
- **Resilience**: Handle network partitions (mobile networks are flaky).

## Data Architecture

### 1. Geospatial Storage (The Core Problem)
**Challenge**: How to store and query "Drivers within 2km"?

#### Option A: SQL (`lat`, `long`)
- Query: `SELECT * FROM drivers WHERE lat BETWEEN x AND y AND long BETWEEN a AND b`
- **Cons**: Too slow on large datasets (Index scan in 2 dimensions).

#### Option B: Geohash (Redis / String)
- **Concept**: Divide world into grid rectangles. Base32 string.
- `u4pruydqqv` -> Specific 10m x 10m spot.
- `u4pruy` -> Larger regions (prefix match).
- **Pros**: Fast prefix search. Find drivers via `startWith("u4pruy")`.
- **Cons**: Edge cases (nearby driver might be in different hash if at border).

#### Option C: QuadTree (In-Memory) - **Preferred**
- **Structure**: Tree where each node has 4 children (NW, NE, SW, SE).
- **Leaf Node**: Contains list of Driver IDs.
- **Pros**: Dynamic splitting (dense areas have deeper trees). Fast k-NN search.
- **Limitation**: Hard to distribute across servers (syncing complicated).

**Industry Standard Solution**: **Google S2 Geometry** (Hilbert Curves) or **Geohash in Redis**.
- Use **Redis Geospatial** (`GEOADD`, `GEORADIUS`).
- Updates are fast (Key: DriverID, Val: Lat/Long).

## System Components

### 1. Location Service (Write Heavy)
- Drivers ping lat/long every 4s.
- **Traffic**: 1M drivers * (1/4) = 250k QPS (Write).
- **Optimization**: Do NOT write every ping to DB.
  - **Redis**: Update volatile location.
  - **Cassandra/Kafka**: Snapshot every 30s for history/trip logs.

### 2. Driver Matching Service
- Rider requests ride for location `(Lat, Long)`.
- Service queries `Redis GEO`: "Find drivers within 2km with status=AVAILABLE".
- Returns List: `[Driver A, Driver B, Driver C]`.
- Algorithm:
  1. Lock Driver A (Redis Lock / Distributed Lock).
  2. Send Notification to Driver A.
  3. If Accepted -> Create Trip.
  4. If Rejected/Timeout -> Unlock A, Lock B, Repeat.
  
### 3. Trip Service (State Machine)
- Manages strict state transitions.
- **Database**: PostgreSQL (ACID required for payment/state).
- `trips` table: `trip_id`, `rider_id`, `driver_id`, `status`, `start_loc`, `end_loc`.

## Architecture Diagram

```
      Rider App               Driver App
         │                        │
         ▼                        ▼
    ┌──────────┐            ┌──────────┐
    │API Gateway│           │API Gateway│
    └────┬─────┘            └────┬─────┘
         │                       │
         ▼ (WebSocket)           ▼ (UDP/HTTP)
   ┌─────────────┐        ┌─────────────┐
   │Notification │        │ Location    │
   │  Service    │        │  Service    │ (Ingest Geolocation)
   └─────────────┘        └──────┬──────┘
                                 │
                          ┌──────▼──────┐
                          │ Redis Cluster│ (Geo Index)
                          │ (Ephemeral) │
                          └─────────────┘
                                 ▲
         ┌───────────────────────┘
         │ (Find nearby)
         │
   ┌─────────────┐        ┌─────────────┐
   │ Matching    │───────▶│ Trip DB     │
   │ Service     │        │ (PostgreSQL)│
   └─────────────┘        └─────────────┘
```

## Deep Dive: Location Updates

**Problem**: 1M Drivers sending updates every 3s = Millions of writes.
**Solution**:
1. **Driver App**: Batch updates if offline needed (though mostly real-time).
2. **Load Balancer**: UDP (faster) or gRPC.
3. **Location Service**:
   - Save to **Redis** (TTL 10s).
   - Only drivers with `status=AVAILABLE` or `ON_TRIP` matter.
   - Using **Redis Pub/Sub** to push location to Rider's Map (only for assigned driver).

## Handling Race Conditions (Matching)

**Scenario**: 2 Riders match same Driver A.
**Solution**:
- **Distributed Lock** (Redlock) on DriverID.
- First Request acquires lock: `SET resource_name my_random_value NX PX 30000`
- If Driver accepts: Update Status `AVAILABLE` -> `BUSY` (Atomic in DB).
- Release Lock.

## Scale Estimation

**Storage**:
- Users/Drivers metadata: MySQL/PostgreSQL.
- Trip History: 1M trips/day * 1KB * 365 = 365 GB/year (Archive to Cold Storage).
- Location History: 1M Drivers * 20kb/day -> Cassandra (Time Series).

**Bandwidth**:
- Location Pings: Very High. separate "Location Service" from "API Service" to avoid bottleneck.

## Interview Q&A

**Q: "How to handle high demand (Surge Pricing)?"**
- A: "Partition world into zones (S2 Cells). Calculate `Request_Count / Driver_Count` ratio in real-time (Stream Processing e.g., Flink/Spark). If Ratio > Threshhold, apply Multiplier."

**Q: "Rider cancels while Driver is arriving?"**
- A: "State Machine check. Change Trip State `CANCELLED`. Notify Driver via Push. Apply Cancellation Fee (business logic)."
