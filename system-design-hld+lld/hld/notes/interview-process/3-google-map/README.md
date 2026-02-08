# #3 Google map

Nice — let’s design a simple Google Maps (map tiles, search, routing, POI, basic traffic). I’ll keep it practical: scope, requirements, APIs, architecture, core data & algorithms, scaling/back-of-envelope numbers, trade-offs and an evolution path. Treat this as the answer you’d give in a system-design interview.

<br>

## 1 — Scope & assumptions

<br>

Goal: User-facing map app that supports:

* Map display (tiles) and pan/zoom
* Place search & autocomplete (POIs)
* Reverse geocoding (lat/lon → address)
* Turn-by-turn routing for driving (fastest path)
* Basic live traffic overlays (coarse)
* Save/bookmark places

<br>

Assumptions (simple version):

* Target: 1M monthly active users (MAU), peak 2k QPS for map tiles & 500 QPS for search/routing.
* Global-ish coverage but tolerate coarser data quality.
* Mobile-first; offline caching acceptable (basic).
* Latency targets: tile load < 200 ms (from CDN), search P95 < 300 ms, route compute < 500 ms.

<br>

## 2 — Functional requirements

<br>

Must-haves

* Serve map tiles at multiple zoom levels.
* Search places by name/address with autocomplete.
* Reverse geocode coordinates to human-readable address.
* Compute driving route (start → end) with step-by-step directions.
* Show estimated travel time (ETAs) and distance.
* Display basic traffic layer (color by speed: green/yellow/red).
* Persist user bookmarks/favorites.

<br>

Nice-to-have (not for simple v1)

* Transit routing, walking/biking, turn-by-turn voice guidance, live incident reports, offline full-region maps.

<br>

## 3 — Non-functional requirements

* Performance: Tile load P95 < 200 ms (CDN hit), search P95 < 300 ms.
* Availability: 99.9% for read operations (tiles/search); route compute 99% SLA.
* Scalability: Handle bursts (events, navigation peaks) via autoscaling & CDN.
* Accuracy: Routing accuracy adequate for driving; traffic precision coarse.
* Privacy & Security: TLS, opt-in location sharing, minimal retention of location telemetry unless user consents.
* Cost-awareness: Use CDNs and caching to reduce origin load.

<br>

## 4 — External APIs (high-level)

```
GET  /tiles/{z}/{x}/{y}.png           -> map tile image
GET  /search?q=coffee+near+me&lat=..  -> list of POIs (autocomplete and search)
GET  /place/{id}                      -> place detail (name, coords, metadata)
GET  /reverse?lat=..&lon=..           -> address
POST /route                            -> {from: {lat,lon}, to:{lat,lon}, mode: driving} => {polyline, steps, ETA}
POST /user/bookmark                    -> save place to user profile
GET  /traffic/tiles/{z}/{x}/{y}.png    -> traffic overlay tile (optional)
```

## 5 — High-level architecture

```
Clients (mobile/web)
     |
 CDN (map tiles + static assets)
     |
 API Gateway / Load Balancer
     |
 +----------------------------+
 |       Frontend APIs        |  (stateless app servers)
 +----------------------------+
    |      |         |       |
    |      |         |       +--> User service (auth, bookmarks)
    |      |         +----------> Search service (autocomplete, POI)
    |      +---------------------> Routing service (graph + pathfinder)
    +---------------------------> Tiles & Map Data service
                                  - Tile renderer / tile cache
                                  - Map DB (vector tiles / roads)
                                  - Traffic ingestion
    |
 Message Bus (Kafka) for async processing: tile generation, traffic aggregation, telemetry
    |
 Offline/Analytics: Data lake for batch processing, map updates, ML
```

## 6 — Data & storage components

* Map geometry / road graph — primary authoritative source (Vector): nodes (intersections), edges (road segments), metadata (max speed, lanes, turn restrictions). Stored in a graph DB or custom sharded store (on-disk protobufs or key-value per tile).
* Tiles
  * Pre-rendered raster tiles (PNG) or vector tiles (protobuf: Mapbox Vector Tile). For simple version use vector tiles + client render or pre-rendered PNGs for fast implementation. Stored in blob store (S3) and served via CDN.
* POI database — places with name, address, category, location, popularity; indexed for fast text+geo lookups (Elasticsearch or custom trie + geospatial index).
* Reverse geocoding index — address polygons/centroids, indexed by spatial lookup (PostGIS / R-tree).
* Routing graph — compact representation of the road network, possibly partitioned by region; preprocessed for fastest queries (see algorithms).
* Traffic feed — time-series datastore (recent speeds for edges), aggregated from telemetry and third-party feeds.
* User data — auth, bookmarks, preferences (RDBMS or managed user store).

<br>

## 7 — Core algorithms & components

<br>

### Map tiles

* Pre-generate vector tiles for zoom levels (0–16+) using map data pipeline. Store and serve via CDN. For dynamic overlays (traffic), we may generate separate overlay tiles that are composited client-side.

<br>

### Search & Autocomplete

* Autocomplete: prefix trie or Elasticsearch with n-gram / edge-ngram for fast suggestions. Combine text score with geographic proximity to rank results.
* Geo-filtering: restrict suggestions by bounding box (user viewport) to be relevant.

<br>

### Reverse Geocoding

* Use spatial index (R-tree) on address centroids / polygons; perform nearest-neighbor lookup, then format address.

<br>

### Routing (driving)

* Road graph = nodes + directed edges, edges have weights (travel time = length / speed).
* Routing algorithm: A\* with heuristic = straight-line distance / max\_speed.
* Speed-ups:
  * Precompute contraction hierarchies (CH) or use hierarchical routing (landmarks / ALT) for very fast queries.
  * Partition graph by region and use multi-level search for long routes.
* Traffic incorporation: modify edge weights with live speed factor; for faster response, use cached ETA for common origin-destination pairs and re-compute on demand.

<br>

### Traffic

* Ingest telemetry (anonymized speed samples) and third-party traffic feeds into a streaming pipeline. Aggregate per edge (e.g., 1-min windows) and update traffic datastore.
* Traffic overlay tiles generated at low resolution and pushed to CDN.

<br>

## 8 — Read/write paths & caching

* Tile reads: client → CDN → origin (only on cache miss). Pre-generate tiles to minimize origin load.
* Search reads: served by search cluster, cache popular queries in redis.
* Routing: routing service queries in-memory graph partitions (hot in RAM); results cached for recent queries.
* Writes: map updates, POI updates, traffic feeds push into batch pipelines that regenerate tiles/indices off-line and update stores; user bookmarks are simple DB writes.

<br>

## 9 — Back-of-the-envelope (simple estimates)

<br>

(Assume simple v1: 1M MAU, peak 2k tile QPS, 500 search/routing QPS)

* Tile size: vector tile \~ 10–40 KB; assume 20 KB average.
  * Peak outbound bandwidth for tiles: 2,000 req/sec × 20 KB ≈ 40 MB/s ≈ 320 Mbps.
* Storage for tiles: pre-generate 300M tiles × 20 KB ≈ 6 TB (compressed).
* Routing graph: global road graph compacted \~ tens of GB; regional partitions small enough to hold hot partitions in memory (a few GB per region).
* Search index: ES cluster few dozen GBs depending on POI count.
* Compute: routing using A\* on in-memory graphs; each query CPU cost small but requires memory; provision a few route servers behind LB (scale with QPS).
* Cache hit: CDN should handle >90% of tile traffic; origin load small.

<br>

## 10 — Operational concerns & observability

* Monitoring: QPS, P95/P99 latencies per API, cache hit ratios, tile generation job health, routing latency, error rates.
* Logging/Tracing: distributed tracing for route generation (to debug slow graph lookups).
* Alerts: surge in origin hits (CDN misses), index staleness, pipeline failures.
* Deployment: blue/green for search and routing; careful migration for graph changes.

<br>

## 11 — Privacy & security

* TLS for all traffic.
* Store only necessary telemetry; anonymize and aggregate user location data; provide privacy controls and deletion (GDPR).
* Rate limit APIs to prevent scraping.

<br>

## 12 — Trade-offs & choices

* Vector vs raster tiles
  * Vector: smaller storage, flexible styling, client rendering cost.
  * Raster: faster for old clients, larger storage and CDN usage.
  * Choice (simple v1): vector tiles if client can render; fallback raster for compatibility.
* Routing approach
  * A\* on raw graph: simpler, slower for long routes.
  * CH/ALT: more complex preprocessing, much faster queries.
  * Choice: start with A\*/bidirectional A\* on regional graphs; introduce CH for scale.
* Traffic precision
  * Live per-edge speeds give better ETA but costlier; coarse overlay acceptable initially.

<br>

## 13 — Evolution & next steps

* Add offline region downloads (vector tiles + graph).
* Add multimodal routing (transit, walking, biking).
* Improve ranking with ML for POIs.
* Implement contraction hierarchies / commercial-grade routing for global scale.
* Add user-contributed edits and moderation workflows.

<br>

## 14 — Example flow (user opens app, searches, gets route)

1. App requests tiles for viewport → CDN serves vector tiles.
2. User types “Starbucks” → client calls /search (autocomplete) → search service queries ES restricted to viewport → returns ranked POIs.
3. User selects POI and taps “Navigate” → app POST /route with coords → routing service loads relevant graph partition(s), runs A\*/bidirectional search (consider traffic), returns polyline + steps + ETA.
4. App renders route, overlays traffic tiles from CDN.

***

If you want, I can:

* produce a one-page cheat-sheet for interview delivery (time-boxed talking points), or
* sketch detailed routing algorithm pseudocode (A\*/CH) and show exactly what pre-processing is needed.

<br>

Which follow-up would help you most?
