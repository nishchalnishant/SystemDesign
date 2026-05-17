# Design Google Maps

> **Difficulty**: Hard
> **Topics**: Graph Algorithms (Dijkstra/A*), Geospatial Indexing, Tile Rendering, ETA Models, Real-time Traffic
> **Time**: 60-75 minutes
> **Companies**: Google, Apple, Uber, Lyft, HERE Technologies

---

## Problem Statement

Design a mapping and navigation system that:
- Renders interactive maps at any zoom level globally
- Provides turn-by-turn navigation with real-time ETA
- Incorporates live traffic data to adjust routes dynamically
- Supports search for places (businesses, addresses, landmarks)
- Handles 1B+ MAU and petabytes of map data

---

## Analogy

A master cartographer who has divided the entire world into a grid of tiles at multiple zoom levels (like a fractal zoom). To show any view, they just assemble the right tiles — there's no need to render the entire world on demand. For navigation, they're not just drawing the shortest straight-line path — they're consulting a dynamic graph where edge weights (road speeds) change in real-time based on a fleet of sensors (GPS probes from all driving users).

---

## Why This Is Hard

1. **Map data scale**: The world's road network has ~60M road segments. The map data (satellite imagery, street view, points of interest) is petabytes. Serving any viewport instantly requires intelligent pre-computation and tiling.
2. **Routing at global scale**: Dijkstra's algorithm on a 60M-node graph would take minutes. Real navigation runs in milliseconds through Contraction Hierarchies — a preprocessing technique that creates "highway shortcuts" through the graph.
3. **Real-time traffic integration**: 1B users + millions of IoT sensors generate terabytes of location data daily. Fusing this into an accurate speed model per road segment in near-real-time requires massive stream processing infrastructure.
4. **Dynamic re-routing**: ETA changes every few minutes as traffic evolves. The system must re-compute routes for 100M+ active navigations without overwhelming compute.
5. **ETA accuracy**: Users trust ETAs for scheduling. An ETA model must account for traffic, road type, time of day, weather, accidents, construction — not just distance. Machine learning on historical trip data is essential.

---

## Critical Requirements

### Functional
- Display interactive map (zoom, pan, satellite/street view)
- Route calculation: fastest/shortest path with multiple modes (drive, walk, transit, bike)
- Turn-by-turn navigation with voice guidance
- Live traffic overlay
- Place search (name, address, category)
- ETA estimation with confidence intervals

### Non-Functional
- **Map tile latency**: < 100ms P99 (tile delivery from CDN)
- **Route calculation**: < 500ms P99 for most routes
- **ETA accuracy**: ± 5-10% of actual travel time
- **Traffic freshness**: Road speed updates within 2-3 minutes
- **Scale**: 1B MAU, 100M+ active navigations simultaneously

---

## Scale Estimation

```
Map tiles:
  World at zoom levels 0-20 ≈ 4^20 = 10^12 possible tiles (not all land/relevant)
  Relevant tiles at all zoom levels: ~4 billion tiles
  Avg tile size: 30KB (raster) or 10KB (vector)
  Total map data: 4B × 30KB = 120 TB (raster at all zoom levels)
  Pre-rendered and cached in CDN

Route requests:
  1B MAU × 3 routes/day = 3B routes/day = 35K routes/sec (avg)
  Peak (Monday morning): 10× = 350K routes/sec

Traffic data ingestion:
  500M active Android/iOS devices sending GPS probes every 5s when navigating
  100M active navigations × 1 probe/5s = 20M location events/sec
  Plus IoT sensors, traffic cameras, incident reports

ETA computations (re-calculation for active navigations):
  100M active navigations × re-route every 3 min = 550K ETA refreshes/sec
```

---

## Core Concepts

### 1. Map Tile System

```
Tile Coordinate System (TMS/Slippy Map):
  Each tile is identified by (zoom, x, y)
  At zoom level Z: 2^Z × 2^Z grid of tiles covering the world
  Zoom 0: 1 tile (entire world, 256×256 pixels)
  Zoom 10: 1,048,576 tiles (city level)
  Zoom 20: ~10^12 tiles (building level)

Tile Types:
  Raster tiles: Pre-rendered PNG/WebP images
    Pro: Universal browser support, simple
    Con: Large files, re-render for any style change

  Vector tiles (Mapbox/Google current approach):
    Protobuf-encoded geographic data (roads, polygons, labels)
    Rendered client-side using WebGL (Mapbox GL, Google Maps JS SDK)
    Pro: Tiny files (10KB vs 30KB), dynamic styling, smooth zoom
    Con: Client compute required, complex rendering pipeline

Tile Cache Hit Rate:
  Popular areas (NYC, London) hit 99.9% CDN cache
  Rural areas: may miss cache; origin server renders on demand
  Pre-warming: At launch, pre-render all zoom 0-14 tiles (manageable volume)
  Zoom 15+: Render on demand, cache aggressively (LRU TTL: 30 days)
```

### 2. Road Graph Representation

```
Graph model:
  Nodes: Road intersections + waypoints (~1B nodes globally)
  Edges: Road segments between intersections (~60M edges globally)
  Edge attributes:
    - Distance (meters)
    - Speed limit (km/h)
    - Current speed (from traffic layer)
    - Road type (highway, arterial, residential, pedestrian)
    - Directionality (one-way, two-way)
    - Turn restrictions (no left turn, no U-turn)
    - Access restrictions (toll, HOV, truck)

Storage:
  Adjacency list format: compressed, 50-100GB per major region
  Sharded by geographic region (North America, Europe, Asia, etc.)
  Immutable base graph + mutable traffic weights updated every 2 min
```

### 3. Routing Algorithm: Contraction Hierarchies

```
Naive Dijkstra on 60M nodes: ~seconds. Unusable.

Contraction Hierarchies (CH) — state of art for road networks:

Preprocessing (offline, run once per graph update):
  1. Rank all nodes by importance (major highways > local roads)
  2. "Contract" less important nodes: add "shortcut edges" that bypass them
  3. Result: a hierarchical graph where routing upward (coarse) then downward (fine)
     finds optimal paths in milliseconds

Query time (online, per route request):
  Bidirectional search from source and destination simultaneously
  Search expands upward in hierarchy (shortcuts) until both searches meet
  Unpack shortcuts to get full turn-by-turn route
  Typical query: 0.5-5ms for city-to-city routes

Why it works:
  A shortcut edge from LA to NYC represents the entire interstate highway route.
  The search never needs to visit individual intersections along I-40.
  It only expands "important" nodes in each direction.

Speed updates (traffic):
  Traffic changes only edge weights, not graph topology.
  CH shortcuts have precomputed which edges they "contain."
  When an edge's speed changes, affected shortcuts are updated.
  Full CH rebuild: every few days (topology changes from construction)
  Weight updates: every 2 minutes (traffic layer)
```

### 4. ETA Model

```
Simple ETA = sum(segment_distance / segment_speed) — too crude.

Production ETA model (ML-based):
  Features:
    - Route segments (distance, road type, current speed)
    - Time of day + day of week
    - Historical speed at this segment at this time (p50, p90)
    - Weather (rain → slower, snow → much slower)
    - Special events (sports game, concert near destination)
    - Driver-specific (commercial truck → different speed profile)

  Model: LightGBM or Neural network trained on billions of historical trips
  Output: Estimated travel time + confidence interval (p10, p50, p90)

  "15-30 min" = p10 to p90 range (uncertainty shown to user)

Traffic probe fusion:
  GPS probes from navigating users → real observed speeds per segment
  Fused with sensor data → speed model updated every 2 minutes
  Historical + real-time speeds averaged: real_time_weight = 0.7, historical = 0.3
```

---

## Architecture

```
                CLIENT (Mobile / Web)
                        │
                        │ Map tiles (CDN)
                        │ Route requests (API)
                        │ GPS probes (ingest)
                        ▼
              ┌────────────────────┐
              │   CDN Edge Network │  ← 99% of tile requests served here
              │ (Cloudflare/Akamai)│
              └─────────┬──────────┘
                        │ Cache miss (< 1%)
                        ▼
          ┌─────────────────────────────┐
          │      API Gateway            │
          │  (Auth, Rate Limit, Route)  │
          └──────┬──────────────┬───────┘
                 │              │
     ┌───────────▼──┐    ┌──────▼────────────┐
     │ Tile Server  │    │  Routing Service   │
     │ (on-demand   │    │  (CH algorithm,    │
     │  tile render)│    │   ETA model)       │
     └──────────────┘    └──────────┬─────────┘
                                    │
                   ┌────────────────┼────────────────┐
                   ▼                ▼                 ▼
           ┌────────────┐  ┌──────────────┐  ┌────────────┐
           │  Road Graph│  │ Traffic DB   │  │   ETA ML   │
           │  (Sharded  │  │ (speed per   │  │   Model    │
           │  by region)│  │  segment)    │  │ (TensorFlow│
           └────────────┘  └──────┬───────┘  │  Serving)  │
                                  │          └────────────┘
                                  ▲
                   ┌──────────────┘
                   │
         TRAFFIC INGESTION PIPELINE
         ┌─────────────────────────────┐
         │ GPS Probes → Kafka →        │
         │ Flink Stream Processing →   │
         │ Speed model per segment →   │
         │ Traffic DB (Redis + S3)     │
         └─────────────────────────────┘

         PLACE SEARCH
         ┌─────────────────────────────┐
         │ Places DB (PostgreSQL +     │
         │ Elasticsearch for full-text)│
         └─────────────────────────────┘
```

---

## Traffic Data Ingestion Pipeline

```
Sources:
  1. Anonymized GPS probes from users who opted into traffic reporting
     - Speed, heading, accuracy per probe
     - Collected when navigating or in background (coarse)
  2. Commercial traffic sensors (loop detectors, radar)
  3. Accident reports (users, Waze-style crowdsource)
  4. Government road agencies (road closures, construction)

Map-matching (critical):
  Raw GPS coords → "snap" to nearest road segment
  Problem: GPS accuracy ±5-15m; same coordinate could be on highway or service road
  Algorithm: Hidden Markov Model (HMM) — infer most likely sequence of road segments
  from noisy GPS trajectory

Aggregation pipeline (Flink/Kafka Streams):
  1. Ingest: 20M probes/sec into Kafka topic "gps_probes"
  2. Map-match: Assign each probe to a road segment
  3. Aggregate: For each segment, median speed over last 2 minutes from all probes
  4. Merge: Blend with historical baseline (prevents single bad probe skewing)
  5. Publish: Updated speed per segment → Traffic DB (Redis) + Kafka topic "speed_updates"
  6. Routing service subscribes: Updates edge weights in in-memory graph

Anomaly detection:
  If segment speed drops > 50% suddenly: possible accident
  Alert to incident review team; surface on map as red/yellow
  Validate with multiple probes before surfacing
```

---

## Place Search

```
Data: 200M+ businesses, landmarks, addresses globally

Storage:
  PostgreSQL: canonical place data (name, address, coords, category, hours, phone)
  Elasticsearch: full-text search index (handles "coffee near me", "dentist Brooklyn")
  GeoJSON polygons: city/country boundaries for geocoding

Query types:
  1. Exact: "Empire State Building" → Elasticsearch phrase match
  2. Semantic: "good sushi downtown" → LLM embedding + vector search
  3. Near me: "gas stations" + current GPS → geospatial query (bounding box + Elasticsearch geo_distance)
  4. Address geocoding: "350 5th Ave, New York" → address parser + coordinate lookup

Ranking for "near me":
  Score = relevance × distance_decay × quality_score
  quality_score = f(review_count, rating, photos, recency)
```

---

## Scaling Deep Dives

### Routing at 350K QPS

```
Approach: Shard routing by geographic region
  Routing service for North America (own graph copy in memory)
  Routing service for Europe (own graph copy)
  Cross-region routes: handled by a global routing service with compressed super-graph

In-memory graph:
  Road graph per region: 5-15 GB loaded into RAM
  Multiple replicas: 5 servers per region, all with same in-memory graph
  Read-only (writes = graph updates every few days)
  Graph update: Blue-green deployment (new servers load new graph, swap via LB)

Traffic weight updates (every 2 min):
  Speed updates broadcast to all routing servers via Kafka topic
  Each server applies delta updates to in-memory edge weights (<50ms)
```

### Tile Serving at Scale

```
CDN cache hit: 99%+ for zoom levels 0-15 (pre-rendered, rarely change)
Cache miss handling (zoom 16+):
  Tile server generates on demand from vector data
  Stores result in CDN with TTL 30 days
  Multiple tile servers, load balanced

Map data updates (new construction, road changes):
  Changes ingested daily from data providers (Overture Maps, HERE, TomTom)
  Affected tiles invalidated in CDN (by tile coordinate range)
  Re-rendered lazily on next request or proactively for high-traffic tiles
```

---

## Failure Scenarios

### Routing Service Cluster Down

```
Impact: No new route calculations
Duration: 30-60 seconds to spin up new instances

Short-term mitigation:
  Show "Limited navigation available" banner
  Cached routes (user's last route) still work for turn-by-turn
  ETA displayed as "unavailable" until service recovers

Recovery:
  Auto-scaling group spins up new instances
  New instances load road graph from S3 (takes ~2 min to load 15GB into RAM)
  Gradual traffic shift as instances become ready
```

### Traffic Data Stale (Ingestion Pipeline Down)

```
Impact: Routes calculated on historical average speeds, not real-time traffic
Detection: Consumer lag on "gps_probes" Kafka topic grows > 5 minutes

Response:
  Continue serving routes with historical speeds + display "Traffic unavailable" indicator
  Alert: P1 — traffic freshness SLO violated
  No data loss: Kafka retains probes for 24h; backfill once pipeline recovers
```

---

## Interview Talking Points

**Q: "Why can't you just use Dijkstra's algorithm for routing?"**
> "Dijkstra on a 60M-node graph runs in O(N log N) — about 5-10 seconds on commodity hardware. That's unusable for real-time routing. Contraction Hierarchies preprocess the graph offline by adding 'shortcut edges' for common long-distance routes. At query time, the bidirectional search only expands 'important' nodes — it never has to visit every intersection along I-95. This brings query time from seconds to under 5ms for most routes."

**Q: "How do you keep routes fresh when traffic changes every few minutes?"**
> "Traffic is separate from graph topology. We have a stream processing pipeline (Flink) that ingests 20M GPS probes per second, map-matches each probe to a road segment, aggregates median speeds per segment over 2-minute windows, and publishes speed updates to all routing servers via Kafka. Each routing server applies delta updates to its in-memory edge weights in under 50ms. Active navigations get re-routed if a significantly faster route appears. The CH preprocessing is only re-done when road topology changes (new roads, closures) — not with every traffic update."

**Q: "How do you build ETAs that users trust?"**
> "Pure distance ÷ speed_limit is wildly wrong — it ignores traffic, turns, time of day, weather. We train a gradient boosting model on billions of historical trips. Features include current segment speeds, historical p50/p90 speeds at this time of day, weather, and special events. We output a range (p10 to p90) — 'arrive in 15-30 min' — rather than a single point estimate, which is more honest about uncertainty. The model is re-trained weekly on fresh trip data, and accuracy is tracked as |predicted - actual| / actual across millions of completed trips."

---

## Interview Questions Asked

### Google
1. **"Design Google Maps routing at global scale"** → Probe: graph representation, routing algorithm choice, real-time traffic integration, scalability. Hint: road network as directed weighted graph (nodes = intersections, edges = road segments with travel-time weights); Contraction Hierarchies for sub-5ms queries on 60M-node graphs; traffic updates adjust edge weights in-memory without recomputing hierarchy.

### Uber
1. **"How do you compute ETAs accurately for ride-hailing?"** → Probe: beyond distance/speed_limit, ML-based ETA, uncertainty. Hint: gradient boosting on historical trip data with features (current segment speeds, time-of-day, weather, events); output P10-P90 range rather than point estimate; retrain weekly on completed trips.

### Common Follow-ups
1. **"How do you model the road network as a graph?"** → Directed graph: nodes = intersections + points of interest, edges = road segments with attributes (distance, speed limit, turn restrictions, one-way); edge weight = travel time = distance / speed; store as adjacency list in memory (~15GB for global road graph).
2. **"Why use Contraction Hierarchies over Dijkstra or A*?"** → Dijkstra: O(N log N) on 60M nodes = 5-10 seconds, unusable for real-time; A*: faster with good heuristic but still explores millions of nodes; CH: offline preprocessing adds shortcut edges for highways, query only expands "important" nodes — 5ms for cross-country routes.
3. **"How do you serve map tiles at different zoom levels?"** → Pre-render tiles as PNG/WebP at zoom levels 0-20 offline; store in object storage (GCS); serve via CDN edge nodes; tile URL encodes zoom/x/y so caching is trivially cache-key-based; vector tiles (Mapbox format) sent to client for client-side rendering to reduce tile count.
4. **"How do you integrate real-time traffic data?"** → GPS probes from 20M active users → Kafka → Flink map-matches probes to road segments → median speed per segment in 2-min windows → publish speed updates to routing servers → servers apply delta updates to in-memory edge weights in <50ms; CH recomputed only on topology changes (new roads), not traffic changes.
