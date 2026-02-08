# #1 Proximity service

Below is a complete, time-boxed 1-hour interview answer for Designing a Proximity Service (think “find all drivers/shops/friends within X km of a user”).

It’s organized so you can speak smoothly for \~60 minutes while touching every major dimension—including Functional and Non-Functional Requirements, API design, architecture, scaling, and trade-offs.

***

### 0 – 5 min  ➜  Problem Understanding & Assumptions

<br>

> _Goal:_ Confirm scope and key constraints before you draw anything.

<br>

* Use-case: Clients send their current GPS location; service returns nearby entities (drivers, stores, friends) within a given radius in real time.
* Scale assumption (for capacity planning):
  * 10 M DAU
  * 50 M location updates/day (\~580 writes/sec average, peak 3–5×)
  * Peak 50 k “nearby” queries/sec
* Constraints:
  * Global coverage
  * Query latency target: P95 < 200 ms
  * Location accuracy: \~10 m
  * 99.9 – 99.99 % availability

***

### 5 – 15 min  ➜  Requirements

<br>

#### Functional Requirements

<br>

Core Must-haves

1. Location Update – Entities send periodic updates (entity\_id, lat, lon, timestamp); must be idempotent and handle out-of-order events.
2. Nearby Query – Given a point & radius, return entities sorted by distance with optional filters (type, status).
3. Entity Detail – Fetch latest known location and metadata for a specific entity.
4. Stale Data Handling – Mark entities offline if no update within configurable TTL.

<br>

Should / Nice-to-Have

* Real-time subscriptions (WebSocket/SSE) for continuous updates.
* Geofencing alerts (enter/leave a region).
* Location history storage with TTL (e.g., 30 days) for analytics.
* Admin APIs for blacklisting, throttling, and data retention management.

<br>

#### Non-Functional Requirements

* Performance & Latency –
  * P95 query < 200 ms, P99 < 500 ms.
  * Update propagation visible in queries ≤ 1 s.
* Scalability –
  * Reads: sustain 50 k QPS; writes: 3 k QPS peak.
  * Horizontal scale for sudden bursts (e.g., events, concerts).
* Availability / Reliability – 99.9 % SLA; multi-AZ + multi-region failover.
  * RTO < 15 min, RPO < 1 min.
* Consistency – Eventual for queries; strong for individual entity detail if required.
* Security & Privacy –
  * OAuth2/JWT auth, TLS everywhere.
  * GDPR/CCPA compliance, “right to be forgotten”.
  * Rate limiting & anomaly detection to prevent spoofing.
* Observability & Ops –
  * Metrics: QPS, latencies, cache hit ratio, stale-entity rate.
  * Distributed tracing & structured logs.
* Cost & Maintainability – Prefer managed services; 90 %+ cache hit ratio to control DB costs.

***

### 15 – 25 min  ➜  API Design (External Contract)

| Method | Endpoint         | Request                    | Response                            | Notes                |
| ------ | ---------------- | -------------------------- | ----------------------------------- | -------------------- |
| POST   | /location/update | {entity\_id, lat, lon, ts} | 200 OK                              | Idempotent           |
| GET    | /nearby          | lat,lon,radius,type?limit? | \[ {id, lat, lon, distance, meta} ] | Pagination & filters |
| GET    | /entity/{id}     | —                          | {id, lat, lon, updated\_at, meta}   |                      |

* Authentication: OAuth2/JWT.
* Rate limits: e.g., 100 req/min per user.
* Standard error codes & retry guidelines.

***

### 25 – 40 min  ➜  High-Level Architecture

```
Clients (iOS/Android/Web)
        |
   CDN + API Gateway
        |
  ┌───────────────┐
  │  App Servers  │  (stateless, auto-scaling)
  └───────────────┘
        |
  Message Bus (Kafka / SQS)
        |
 Location Updater Workers
        |
 ┌───────────┐        ┌────────────┐
 │  Redis    │  <-->  │  PostGIS   │
 │  GeoCache │        │  or H3/ES  │
 └───────────┘        └────────────┘
        |
Analytics & Monitoring (ELK / Prometheus / Grafana)
```

Key Points

* Write Path – App servers validate & enqueue updates → workers upsert into Redis GEO (hot set) and durable store (PostGIS or ElasticSearch geo\_point).
* Read Path – /nearby hits Redis GEORADIUS (P95 < 50 ms). Fallback to PostGIS for cold data.
* Geo-Sharding – Use geohash/H3 cells as partition keys for DB scaling.
* Region Strategy – Multi-AZ replication, eventual multi-region active/active.

***

### 40 – 50 min  ➜  Key Algorithms & Data Model

<br>

#### Data Model

```
Entity {
   entity_id (PK)
   type ENUM(driver, store, friend)
   lat, lon (with geospatial index)
   updated_at TIMESTAMP
   metadata JSONB
}

Location_History {
   entity_id + timestamp
   lat, lon
   TTL 30 days
}
```

#### Algorithms / Flows

* GeoIndexing: H3 or geohash to bucket earth into \~0.6 km cells; update cell membership on every location update.
* Distance Filtering: Redis GEORADIUS or PostGIS ST\_DWithin with Haversine distance check.
* Stale-entity cleanup: background job removes or flags entries older than TTL.

***

### 50 – 55 min  ➜  Trade-Offs & Alternatives

| Approach                 | Pros                 | Cons                           |
| ------------------------ | -------------------- | ------------------------------ |
| Redis GEO only           | Ultra-low latency    | Memory cost, weaker durability |
| PostGIS only             | Rich spatial queries | Higher read latency            |
| ElasticSearch geo\_point | Text + geo combined  | Operational complexity         |

Chosen Hybrid: Redis GEO for hot queries, PostGIS for durability & complex analytics.

<br>

Other considerations:

* Update frequency vs. mobile battery/network usage.
* Strong vs. eventual consistency—eventual is acceptable for proximity.

***

### 55 – 60 min  ➜  Wrap-Up & Future Work

* Future Enhancements
  * Predictive caching: pre-load likely next cells based on velocity.
  * Differential privacy or “fuzzing” for sensitive users.
  * ML-driven ranking (ETA, traffic conditions).
* Risk & Mitigation
  * Region outage → active/active replication.
  * Sudden traffic spikes → auto-scale app & cache tiers.
* Evolution Path
  * Phase 1: Single-region MVP (Redis + PostGIS).
  * Phase 2: Multi-region global presence with cross-region replication.

***

#### 🔑 Key Numbers for Quick BoE

* Writes: \~580/sec avg (50 M/day), plan for 3 k/sec peak.
* Reads: 50 k/sec peak.
* Avg query payload \~2 KB → \~800 Mbps peak outbound.
* Storage: 10 GB/day (200 B per update) → \~300 GB for 30 days (×3 replication ≈ 1 TB).

***

#### ✅ Interview Takeaways

* Clear functional & non-functional requirements upfront.
* APIs and high-level architecture that match scale/latency goals.
* Reasoned trade-offs and evolution plan.

<br>

Delivering this sequence keeps you structured, shows capacity planning, and fills the entire hour without rushing or omitting critical design details.

