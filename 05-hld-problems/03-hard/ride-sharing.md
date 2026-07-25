> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design a ride-sharing platform (Uber) — real-time driver location tracking, geospatial matching, dynamic pricing, and trip management at global scale.
>
> **Key design decisions:**
> - Location tracking: drivers send GPS updates every 4s → Kafka → location store (Redis with GEOADD); high write throughput, short TTL
> - Geospatial indexing: S2 (Google) or H3 (Uber) cell hierarchy; convert GPS coords to cell ID; find nearby drivers via cell + neighbors lookup
> - Driver matching: when rider requests → query Redis for drivers within 1km radius → rank by ETA (distance ÷ speed) → offer to nearest; retry expanding radius
> - State machine: driver states (offline → available → en-route-to-pickup → on-trip → available); state transitions trigger events
> - Surge pricing: demand/supply ratio per H3 cell; if demand/supply > threshold → multiply base price; real-time recomputed every 60s
> - Trip management: PostgreSQL for trip records (idempotent); Kafka for trip events (trip_started, trip_ended, payment_completed)
> - ETA computation: pre-computed road graph (OSRM) → shortest path → adjust with real-time traffic; GPU acceleration for mass matching
>
> **Key takeaway:** The geospatial challenge — H3/S2 cell indexing in Redis — is what makes Uber work at scale; without it, "find nearby drivers" degrades to a full table scan with haversine distance.

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

> 🎯 **Staff signal:** The precise reason to prefer H3 hexagons over a geohash grid is a correctness bug, not aesthetics: geohash cells have *non-uniform* neighbor distances — a square's diagonal neighbors are 1.4× farther than its edge neighbors, and adjacent points can straddle a boundary into cells that aren't even lexicographically near — so a naïve "same-prefix" proximity query silently misses drivers that are physically closest. Hexagons have exactly six equidistant neighbors, so a k-ring is a clean, distance-uniform radius. The deeper design point is that this whole scheme trades exactness for a bounded write path: driver locations churn at 5M × (1/4Hz) = ~1M writes/sec, so you can't afford a query-time spatial join — you *pre-bucket* each driver into a cell on write, turning a nearest-neighbor search into a set-union over ~57 precomputed cells. The 30s TTL is the safety valve: a crashed driver's app stops updating, and you must not dispatch a rider to a ghost. E5 says "use geohashing to find nearby drivers"; E6 says "H3 because uniform hex neighbors avoid geohash's boundary/diagonal artifacts that drop the closest driver, and I pre-bucket on write with a location TTL so matching is a set-union, never a scan, and never routes to a stale driver."

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

> 🎯 **Staff signal:** Matching is a distributed-locking problem masquerading as a ranking problem, and the failure it must prevent is *double-dispatch* — the same driver offered to two riders concurrently. That's why the offer is a short-TTL lock (`SET driver_state EX 15`), not a flag: the TTL guarantees a crashed matcher or a driver who never responds auto-releases the driver instead of stranding them locked forever, so the lock is self-healing. The second insight is ranking by *ETA, not distance* — a driver 500m away across a river with no bridge is worse than one 2km away on the same road — which quietly makes matching depend on the routing service, so its ETA cache latency is on the critical path. And sequential-with-prefetch is the deliberate middle ground between broadcast-to-all (fast but causes multiple drivers to race-accept, needing rollback) and pure-sequential (clean but slow per decline); prefetching the next candidates' ETAs hides the re-offer latency. E5 says "offer to the nearest driver, then the next if they decline"; E6 says "the offer is a TTL'd lock so a crash can't leave a driver double-booked or stuck, I rank by routed ETA not straight-line distance, and I prefetch the fallback candidates so a decline doesn't cost the rider another round-trip."

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

> 🎯 **Staff signal:** Surge is a *control system*, not just a pricing formula — its real job is to keep supply and demand in equilibrium per cell, and the senior details are all about damping and honesty. The price must be *locked at quote time* (`confirmed_surge_multiplier` in the trip record), because a price that changes between "confirm" and "pickup" is both a UX betrayal and a legal problem — the quote is a contract. The multiplier is computed on a smoothed window (5-min demand vs current supply), not instantaneously, because raw supply/demand is spiky and an unfiltered signal makes prices oscillate wildly, which itself distorts behavior — a feedback loop where the measurement changes the thing measured. The per-cell granularity plus caps exist to prevent surge from being gamed or from teleporting demand across a boundary. E5 says "raise prices when demand exceeds supply"; E6 says "surge is a feedback controller — I lock the multiplier at quote time because the price is a contract, smooth the supply/demand signal over a window to damp oscillation, and cap it, because an un-damped controller whose output changes its own input will ring."

---

## Back-of-Envelope Estimation

**Scale inputs (given in NFRs):**
- 5M concurrent drivers; 10M concurrent riders; 1M trips/hour

**Driver GPS ping rate:**
- Drivers send GPS pings every 4 seconds while on trip (tighter) or every 10 seconds while available
- Assume 5M drivers online (worst case all active): 5M / 4 sec = **1.25M location writes/sec**
- Each ping: `{driver_id, lat, lng, heading, speed, h3_cell, ts}` ≈ 100 bytes
- Write throughput: 1.25M × 100 bytes = **~125 MB/sec** to the location store

**Redis sizing for driver locations:**
- 5M drivers × 200 bytes per Redis hash = **~1 GB** for all driver locations in memory
- Well within a single Redis node (typically 30–100 GB RAM available)
- H3 cell sets: ~1M distinct H3 cells at resolution 8 (city block scale), each set averages 5 drivers = 5M entries, ~500 MB additional
- Total: **~1.5 GB** — comfortably in-memory with no eviction needed

**Ride request rate:**
- 1M trips/hour = **~278 ride requests/sec** peak
- Each ride request triggers: H3 lookup (1 Redis query for each of ~57 neighbor cells) + driver ranking + match send
- 278 req/sec × 57 SUNIONSTORE ops = **~15,800 Redis ops/sec** for matching — manageable on a single Redis instance (handles ~1M ops/sec)

**Match latency budget (5 seconds total):**
- H3 geo lookup: ~5 ms (Redis in-memory)
- Driver ranking (up to 50 candidates, ETA computation): ~50 ms
- Match request → driver response: up to 15 seconds (driver has 15s to accept)
- Re-match on decline: ~5 ms (already have ranked list)
- Total to first acceptance: P50 ~2s, P99 ~4.5s — within 5s SLA

**Location update write path:**
- 1.25M writes/sec to Redis: ~125 MB/sec. Redis single-threaded can do ~1M SET/sec — this approaches the limit.
- Solution: shard Redis by driver_id prefix across 4 Redis nodes → 312K writes/sec per node (comfortable headroom)
- H3 index: `SADD h3:{cell} driver_id` on every GPS ping is expensive at 1.25M/sec. Optimization: only update H3 index when the driver crosses into a new H3 cell (changes every ~460m at resolution 8). Reduces H3 index writes by ~10× since most pings stay in the same cell.

**Trip data storage:**
- 1M trips/hour × 24 hours = 24M trips/day
- Trip record: ~1 KB (rider, driver, origin, destination, pricing, timestamps, route)
- 24M × 1 KB = **~24 GB/day** → ~8.7 TB/year
- PostgreSQL handles this comfortably — trips are OLTP: low write rate (1M/hr = 278/sec), high read rate, relational (join riders/drivers/payments)

**Surge pricing computation:**
- Compute supply/demand ratio per H3 cell, updated every 1 minute
- ~1M distinct H3 cells at resolution 7 (neighborhood scale for surge) globally
- Pipeline: Kafka location-updates → Flink streaming (1-minute tumbling window) → Redis surge cache per H3 cell
- 1M cells × 50 bytes = **50 MB** in Redis for all surge multipliers globally

**Architecture decisions driven by these numbers:**
- **Redis over PostgreSQL for driver locations**: 1.25M writes/sec with sub-2ms read latency for H3 lookups is impossible with a relational DB. Redis in-memory operations are the only option that satisfies the matching latency budget.
- **H3 index update only on cell change**: Reduces write amplification by ~10× (from 1.25M/sec to ~125K H3 index updates/sec). Without this optimization, a driver doing 35 km/h in a city generates 9 H3 index updates/min even while staying in the same cell.
- **4-shard Redis cluster**: One Redis node cannot handle 1.25M writes/sec. Shard on driver_id prefix; consistent hashing for rebalancing when adding nodes.
- **PostgreSQL for trips**: 278 writes/sec is trivially low for PostgreSQL. The relational model is valuable — trip joins to riders, drivers, and payments are critical for billing and analytics. No justification for a NoSQL solution here.
- **30-second TTL for driver locations**: If a driver hasn't pinged in 30 seconds, they're offline or lost signal. Evict from H3 index immediately to avoid matching riders to unreachable drivers. TTL-based eviction in Redis is the correct mechanism.

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

---

## Related

**Concepts used in this design**

- [WebSockets & SSE](../../02-building-blocks/01-networking/06-websockets-sse.md)
- [Sharding](../../02-building-blocks/03-data-partitioning/01-sharding.md)
- [Distributed Locks](../../02-building-blocks/04-coordination/02-distributed-locks.md)
- [Message Brokers](../../02-building-blocks/04-coordination/01-message-brokers.md)
- [Global Distribution](../../03-scaling/04-global-distribution.md)

**Practice next**

- [Google Maps](../03-hard/google-maps.md)
- [Payment System](../03-hard/payment-system.md)

Maps supplies the geospatial index; payments settle the completed trip.

**Frameworks**: [HLD Template](../../07-interview-templates/01-frameworks/01-hld-template.md) · [Capacity Estimation](../../07-interview-templates/02-cheat-sheets/02-capacity-estimation.md) · [Trade-offs Cheat Sheet](../../07-interview-templates/02-cheat-sheets/01-trade-offs-cheat-sheet.md)
