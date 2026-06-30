> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design Google Maps — map tile serving, road graph routing, real-time traffic ingestion from GPS data, and sub-second ETA computation at global scale.
>
> **Key design decisions:**
> - Map tiles: pre-rendered vector/raster tiles at zoom levels 0–20 stored in S3; served via CDN; tile key = (zoom, x, y) quadtree coordinates
> - Routing graph: road network as directed weighted graph (nodes = intersections, edges = road segments); stored in custom binary format; loaded into memory per region
> - Routing algorithm: Dijkstra for short distances; Contraction Hierarchies (CH) for long routes (200× faster than plain Dijkstra); pre-process graph offline
> - Real-time traffic: GPS pings from active navigating users → Kafka → stream processing → traffic speed per road segment → update edge weights in routing graph
> - ETA: route distance / traffic-adjusted speed per segment; ML model corrects for time-of-day, weather, incidents
> - Geospatial indexing: H3 hexagonal cells for spatial queries (nearby POIs, traffic density per area); R-tree for bounding-box queries
> - Map updates: OSM + commercial providers → validate → conflate → push to tile render pipeline; edge case: road closures propagate in <5 min
>
> **Key takeaway:** Contraction Hierarchies preprocessing is what makes sub-second routing possible — plain Dijkstra on a full road network takes seconds; CH reduces it to milliseconds by pre-computing shortcuts.

---
module: 05-hld-problems
topic: Hard
status: unread
tags: [05-hld-problems, system-design, hard, google-maps, routing, geospatial, eta, map-tiles]
---
# Design Google Maps

> **Difficulty**: Hard | **Asked at**: Google, Uber, Lyft, Apple

---

## Problem Statement

Design a mapping and navigation platform like Google Maps. Users see a map, search for places, get turn-by-turn directions with ETA, and receive real-time traffic updates. The system must serve map tiles globally, compute routes across road networks with millions of edges, and ingest real-time GPS data from millions of active drivers/users.

---

## Functional Requirements

1. **Map rendering**: Display map tiles at various zoom levels globally
2. **Place search**: Search for businesses, addresses, and landmarks by name or category
3. **Routing**: Compute the fastest/shortest route between two points with step-by-step directions
4. **ETA**: Estimate arrival time considering real-time traffic
5. **Navigation**: Real-time turn-by-turn directions; reroute when user deviates
6. **Live traffic**: Display current traffic speed per road segment; incorporate into routing

---

## Non-Functional Requirements

- **Scale**: 1B users, 100M active navigation sessions/day, 10M GPS pings/sec from active users
- **Latency**: Initial route computation < 2s; map tile load < 100ms; ETA update < 5s
- **Accuracy**: Route should always be the fastest available given current traffic
- **Availability**: 99.99% — navigation cannot drop mid-drive
- **Map data**: 10 PB of map tiles (raster and vector); road graph: 1B nodes, 2B edges (road segments)

---

## Core Entities

| Entity | Key Fields |
|--------|-----------|
| `RoadSegment` | segment_id, start_node, end_node, length_m, speed_limit, current_speed, road_class |
| `MapTile` | z (zoom), x, y (tile coordinates), format (raster/vector), gcs_path, last_updated |
| `Place` | place_id, name, category, lat, lng, address, rating |
| `Route` | route_id, start, end, waypoints[], segments[], total_distance, total_time, created_at |
| `GPSPing` | user_id, lat, lng, speed, heading, timestamp |

---

## API Design

```http
GET /api/v1/tiles/{z}/{x}/{y}.pbf
Response 200: <vector tile binary (Protocol Buffers)>

GET /api/v1/directions?origin=37.4,-122.1&dest=37.7,-122.4&mode=driving
Response 200: {
  "routes": [{
    "distance_m": 28400,
    "duration_s": 1860,
    "eta": "2026-06-29T15:31:00Z",
    "legs": [{ "start_address": "...", "steps": [{ "instruction": "Turn left on Market St", "distance_m": 400 }] }]
  }]
}

GET /api/v1/places/search?q=coffee+shop&lat=37.4&lng=-122.1&radius=1000
Response 200: { "places": [{ "place_id": "...", "name": "Blue Bottle", "distance_m": 250 }] }

POST /api/v1/gps/ping
Body: { "user_id": "u123", "lat": 37.4, "lng": -122.1, "speed_kmh": 42, "heading": 180 }
```

---

## High-Level Design

```
Mobile App / Browser
  │
  ├── Map tiles → CDN (99% cache hit; tiles rarely change)
  │
  ├── Place search → Search Service (Elasticsearch, geospatial index)
  │
  ├── Directions → Routing Service
  │     Load road graph (from in-memory store or distributed graph DB)
  │     Run A* / Dijkstra with traffic-weighted edges
  │     Return route + ETA
  │
  ├── GPS pings → Traffic Ingestion Service
  │     Kafka: gps-pings (100M events/min)
  │     Flink: compute speed per segment (aggregate pings on same segment)
  │     Update: segment speeds in Redis + road graph edge weights
  │
  └── Navigation (active turn-by-turn)
        WebSocket: server pushes ETA updates, traffic alerts
        Reroute: recalculate route on deviation or new traffic

Storage:
  GCS: map tile files (PB scale)
  Redis: road segment current speeds, ETA cache
  PostgreSQL: places, road graph metadata
  In-memory graph: partitioned road graph on routing servers
```

---

## Deep Dive 1: Routing Algorithm at Scale

**Problem**: Compute the fastest route from San Francisco to Los Angeles across a road graph with 1B nodes and 2B edges. A single-threaded Dijkstra on this graph would take hours.

**Hierarchical routing (Contraction Hierarchies — CH)**:
- **Observation**: Long-distance routes always use highways; local roads are only relevant near start/end.
- **Pre-processing**: Build a hierarchy of "shortcut" edges. A shortcut edge (A→C) is added if the shortest path from A to C goes through an intermediate node B, and B is of lower importance (rank) than A and C. This contracts unimportant nodes.
- **Bidirectional search**: Run Dijkstra forward from the origin and backward from the destination simultaneously. Each search only explores upward in the hierarchy. The two searches meet at the highest-ranking nodes.
- **Speedup**: CH reduces query time from O(N log N) to O(√N log N) or better. SF→LA in milliseconds, not hours.

**Graph partitioning**: The 1B-node graph is partitioned into tiles (matching map tile boundaries). Each routing server holds the full graph of a continent in RAM (200 GB per server). For cross-continent routing, boundary nodes connect partitions.

**Traffic-weighted edges**: Each edge has weight = `length_m / current_speed`. Updated every 5 minutes from GPS ping aggregation. Pre-processing (CH shortcut computation) runs every 15 minutes on the latest traffic data.

---

## Deep Dive 2: Real-Time Traffic from GPS Pings

**Problem**: 10M active navigation users send GPS pings every 5 seconds = 2M pings/sec. How do you turn these pings into traffic speed estimates per road segment?

**Map matching**: GPS coordinates have 5-10m accuracy — they don't fall exactly on road segments. **Hidden Markov Model (HMM) map matching** probabilistically assigns each GPS ping to the most likely road segment given the sequence of pings and road geometry.

```
GPS ping sequence: (37.401, -122.107), (37.402, -122.107), (37.403, -122.106)
HMM assigns: all three pings → segment_id=S1234 (El Camino Real, northbound)
Speed estimate: 3 pings × 5s = 15s; distance between first+last = 220m → speed ≈ 53 km/h
```

**Flink stream processing**:
```
source: Kafka gps-pings
keyBy: segment_id (after map matching)
window: 60-second sliding window
aggregate: compute median speed from all pings in window for this segment
sink: Redis HSET segment_speeds:{segment_id} speed:42 updated_at:1735689600
      Also write to segment DB for historical analysis
```

**Segment speed database**: Redis hash `segment_speeds:{segment_id}` holds current speed and last-updated timestamp. Routing service reads speeds from Redis when computing routes. Speed data expires after 5 minutes if no new pings received (fallback to speed limit).

---

## Deep Dive 3: ETA Prediction

**Problem**: "Turn left in 500m, ETA 15 minutes" — but traffic changes. How do you continuously update ETA during navigation and surface it to the user?

**ETA components**:
```
ETA = sum over remaining route segments of (segment_length / predicted_speed)
```

**Predicted speed per segment**: Not just current speed — model the traffic at the *time the user will pass through that segment*. A user 20 minutes away from a bottleneck benefits from predicting traffic in 20 minutes, not now.

**Historical traffic patterns**: For each segment, store average speed by day-of-week + hour-of-day. A machine learning model blends:
```
predicted_speed = α × current_speed + β × historical_speed_at_ETA_time + γ × incident_factor
```

**Live ETA updates**: During navigation, the server recalculates ETA every 30 seconds using updated segment speeds. If ETA changes by > 2 minutes, push update to the client via WebSocket.

**Rerouting**: If the user deviates from the route (GPS position > 50m off the expected path for 5 seconds), the routing service recomputes from the user's current position. Reroute results are pushed via WebSocket within 2 seconds.

---

## Interviewer Questions by Level

**Junior**:
- What is a map tile? How does the zoom level affect which tiles are displayed?
- How does a GPS-based navigation app know when you've deviated from the route?
- What is the difference between the shortest route and the fastest route?

**Mid-level**:
- How do you build a routing algorithm that considers real-time traffic? What data structure represents the road graph?
- How do you convert raw GPS pings into speed estimates per road segment?
- How does map matching work? Why can't you just use the raw GPS coordinates?

**Senior**:
- Explain Contraction Hierarchies. How does pre-processing the road graph enable sub-second route queries on a billion-node graph?
- Design the traffic ingestion pipeline for 2M GPS pings/sec — from ingest to updating segment speeds globally.
- How do you predict ETA for a segment the user won't reach for 30 minutes? What data do you use?
- Design the map tile generation pipeline — how do you render and cache tiles at 20 zoom levels for the entire world?
