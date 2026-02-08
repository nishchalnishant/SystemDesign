# #14 Uber, Ola, lyft

Below is a complete, time-boxed 1-hour interview answer you can deliver for designing a ride-hailing system like Uber / Ola / Lyft. Follow the minute markers to pace your response. It covers scope, functional & non-functional requirements, APIs & data model, high-level architecture, deep dives (matching, dispatch, pricing, maps, data), capacity math, ops/security, trade-offs and an evolution path.

***

### 0 – 5 min — Problem recap, scope & assumptions (set the stage)

<br>

Start by restating and aligning with the interviewer.

<br>

Goal: Build a globally available ride-hailing platform that allows riders to request rides, drivers to accept them, and the system to match efficiently while handling pricing, routing, ETA, payments, cancellations, ratings, and availability across regions. Must support real-time locality, low-latency matching, surge pricing, and operational resilience.

<br>

Scope for this interview (what we’ll cover):

* Rider app + driver app + Marketplace (matching & dispatch)
* Real-time location updates, driver discovery, ETA estimation
* Pricing (base fare, distance/time, surge), payments & receipts
* Dispatch strategies (nearest, ETA), cancellations, pooling (optional)
* Telemetry, fraud detection, regional scaling, and offline fallback

<br>

Example assumptions for BoE math:

* City with 1M daily active users, 200k rides/day, peak 5k requests/min (\~83 req/sec). Global system can have many such cities; design to scale horizontally by city/region.
* Driver updates: each active driver sends a location every 5s.

***

### 5 – 15 min — Functional & Non-Functional Requirements

<br>

#### Functional Requirements (Must / Should / Nice)

<br>

Must

1. Request ride: rider requests pick-up/drop-off, selects ride type.
2. Driver discovery & match: find suitable nearby driver, send request, handle accept/decline.
3. Real-time tracking: rider sees driver location & ETA; driver sees pickup/drop-off route.
4. ETA & routing: compute ETA for arrival and trip duration.
5. Pricing & payments: compute fare (base + distance + time + surge), accept payments, receipts.
6. Trip lifecycle: accept, start trip, end trip, cancellations, disputes.
7. Ratings & feedback: driver and rider ratings, history.
8. Account management: profiles, KYC requirements for drivers, balance/summaries for drivers.
9. Notifications: push, SMS for critical states.
10. Operational tools: driver onboarding, heatmaps, surge controls.

<br>

Should

* Pooling (shared rides), scheduled rides, ETA accuracy improvement.
* Fraud detection (fake drivers, location spoofing).
* Multi-region support & city-level configuration.

<br>

Nice-to-have

* Multi-modal (bikes, scooters), driver incentives, predictive dispatch.

<br>

#### Non-Functional Requirements (NFR)

* Latency: match decision and driver notification < 200–500 ms; ETA updates < 1s for UI fluidity.
* Availability: 99.9% for core workflows; degraded read-only behavior if necessary during outages.
* Scalability: horizontal scale across cities and geographies.
* Consistency: eventual consistency ok for non-critical data, but trip state transitions must be strongly consistent per trip (no double assignments).
* Durability: persistent trip logs for audit, billing, and disputes.
* Security & Privacy: TLS, token auth, limit PII exposure, GDPR right to delete.
* Observability: metrics for request latency, matching rates, driver utilization, cancellations, ETA accuracy.
* Cost: minimize unnecessary network/push; control push rates.

<br>

Acceptance criteria examples

* Average match time < 1s in non-congested markets.
* ETA P95 error < X minutes (city config).
* Trip state consistency: no two drivers can be confirmed for same ride (ACID per-trip).

***

### 15 – 25 min — APIs & Data Model (external contract)

<br>

#### Key APIs (Rider & Driver)

```
POST /rides/request       {rider_id, pickup{lat,lon}, dest{lat,lon}, ride_type, payment_method}
GET  /rides/{id}          → trip status, driver location, ETA
POST /rides/{id}/cancel   {rider_id, reason}
POST /drivers/{id}/avail {lat,lon,heading,status}
POST /drivers/{id}/accept {ride_id}
POST /trips/{id}/start
POST /trips/{id}/end    {actual_distance, duration}
POST /payments/charge    {trip_id, method_id}
GET  /drivers/{id}/earnings
POST /ratings            {trip_id, rider_rating, driver_rating}
```

#### Core data model (simplified)

* RideRequest: {ride\_id, rider\_id, pickup, dest, type, status, created\_at, assigned\_driver\_id, price\_estimate}
* Trip: {trip\_id, ride\_id, driver\_id, start\_ts, end\_ts, route, distance, fare, status}
* DriverState: {driver\_id, lat, lon, heading, status(active/idle/on\_trip), vehicle\_info, last\_update\_ts}
* PricingRule: parameters per city/ride\_type (base, per\_km, per\_min, surge\_multiplier)
* Payment: {payment\_id, trip\_id, amount, status, method, refund\_info}
* Telemetry: location updates stream, events for analytics.

<br>

Indices / Partitioning

* Drivers partitioned by geo-shard (tile/geohash) + city; RideRequests written to per-city queues for matching.

***

### 25 – 40 min — High-level architecture & data flow

```
Clients (Rider / Driver Apps)
   |
 CDN / API Gateway (auth, rate-limits, TLS)
   |
  ┌─────────────────────────────┐
  │  Frontend Services (stateless)  ── validation & quick responses
  └─────────────────────────────┘
    |            |                |
    |            |                +--> Map/Routing Service
    |            |                    
    |            +--> Matching Service (real-time)
    |
    +--> Location Ingest (streaming; per-driver updates) --> Driver Location Store (in-memory)
    |
 Event Bus (Kafka-like) <- publish events (requests, matches, trip events)
    |
  Backend Services:
    - Pricing Service (estimates, surge)
    - Payment Service (charging)
    - Routing/ETA Service (uses map + traffic)
    - Trip Service (authoritative trip state; transactional)
    - Notification Service (push/sms)
    - Dispatch/Marketplace (matching, batching)
    - Analytics / ML pipelines (demand prediction, surge)
    - Driver/Partner Services (onboarding, KYC)
    - Admin & Operations UI
    - Data Warehouse (S3) for offline analytics
```

Key components

1. Location Ingest & Store
   * Drivers send frequent location updates; system ingests into a low-latency stream. Store the latest location in an in-memory geo-index (Redis GEO or adaptive geospatial index) sharded by city. TTL on updates for staleness.
2. Matching / Dispatch Service
   * Receives ride request, queries geo-index for candidate nearby drivers (filter by status, ratings, acceptance rate, preferences), scores candidates (ETA, driver acceptance probability, fairness), and sequentially sends match requests to drivers (or batched).
   * Must guarantee atomic assignment semantics: use per-driver optimistic locking or a distributed lock per driver to prevent double-assign. Trip state persisted in Trip Service with strong consistency.
3. Routing & ETA
   * Uses map service + real-time traffic to compute ETA pickup & trip ETA; returned to rider & used by matching. Precompute tile-based travel times for speedups.
4. Pricing Service
   * Computes estimate using pricing rules + surge multiplier (dynamic based on demand/supply). Surge decisions from ML/policy service; thresholds configurable by ops.
5. Trip Service (Authority)
   * Manages trip state transitions reliably: requested → assigned → driver\_accepted → driver\_arrived → in\_progress → completed → paid. Use transactional DB for trip state and events.
6. Notification & Messaging
   * Push notifications to driver and rider; fallback SMS for critical messages.
7. Payments
   * Authorize/charge rider, pay out drivers (daily or instant), support promotions and split fares.
8. Analytics & ML
   * Demand prediction, ETA model, acceptance probability model, surge prediction — offline and online features consumed by matching/pricing.

<br>

Data flow (request flow)

1. Rider calls /rides/request. Frontend persists request and writes to Event Bus.
2. Matching service queries geo-index for nearby candidates. Score & rank candidates.
3. Send match to driver app(s) (push / socket). Driver accepts — Trip Service atomically assigns driver. If driver times out/declines, try next candidate.
4. On assignment, compute final ETA & price; orchestration triggers driver navigation, rider receives driver tracking.
5. Trip begins/end — trip events persisted; at end, calculate final fare, charge rider via Payment Service and ledger entries for driver payouts.

***

### 40 – 50 min — Deep dive: matching, routing, surge, pooling, failure handling

<br>

#### Matching & Dispatch algorithms

* Candidate selection: query geo-index by radius (incremental radius expansion) or by precomputed nearest neighbors (k-NN). Filter by driver availability, vehicle type, driver preferences, compliance (e.g., background checks).
* Scoring: multi-factor score = w1 \* ETA\_to\_pickup + w2 \* acceptance\_prob + w3 \* driver\_utilization + w4 \* fairness / time\_since\_last\_assignment. Use learned weights via ML or heuristics.
* Sequential vs batch offers:
  * Sequential: offer to top-k drivers one by one with short timeout (fast, proven).
  * Parallel (auctioning): send to multiple drivers and accept first; lower latency but higher chance of wasted offers; policies choose approach by region.
* Guarantees & concurrency: use distributed compare-and-swap on driver state (or small lock per driver shard). Once driver calls accept, Trip Service must atomically set assigned\_driver\_id (DB transaction) and acknowledge; all other offers are invalidated.

<br>

#### ETA & routing

* ETA model: combination of historical traffic + live traffic. For pickup ETA, compute route from driver location to pickup considering road speeds. Use precomputed travel-time grids (tiles) for faster approximation, refine with routing for final candidate.
* Routing: use external routing engine (GraphHopper/OSRM or commercial maps) with caching and batching. For repeated queries, re-use partial routes.

<br>

#### Pricing & surge

* Price calculation: base fare + time\_rate \* duration + distance\_rate \* distance + service\_fee. Round and apply promotions.
* Surge model: detect imbalance using demand/supply metrics in geo-shards; compute multiplier. Surge decision pipeline: real-time signals → ML model → ops throttle → apply surge. Communicate to rider before confirm.
* Cancellation & refunds: charge cancellation fee based on policy; refunds processed via Payment Service.

<br>

#### Pooling (shared rides)

* Pool matching: combinatorial problem—match multiple riders with overlapping routes. Use heuristic greedy insertion into driver route maintaining ETA constraints and maximum detour/time windows. Pre-filter candidates by geospatial clustering and constraint satisfaction.

<br>

#### Failure handling & edge cases

* Driver offline during offer: consider driver status stale if not updated; fallback to next candidate.
* Network partition: if Trip Service loses connectivity with driver shard, fall back to cached best-effort (but avoid unsafe assignments). Use leader election & heartbeat.
* Double-assign prevention: atomic DB transaction on assignment; use optimistic locking with retry.
* Stale location updates: if driver location older than TTL, request fresh ping or exclude.
* Fraud: detect spoofed GPS, anomalous cancellations; blacklist or escalate.

***

### 50 – 55 min — Back-of-the-envelope capacity & sizing (example city-scale)

<br>

Assumptions (city): 1M daily users, 200k rides/day, peak 5k req/min (\~83 req/sec), 100k active drivers sending location every 5s.

<br>

Location updates

* 100k drivers × (1 update / 5s) = 20k updates/sec. If each update \~200 bytes → 4 MB/s inbound.

<br>

Ride requests

* Peak 83 req/sec → trivial for API servers, but globally sum across many cities.

<br>

Matching & geo-index

* Geo-index (Redis GEO or custom) must handle 20k updates/sec + query throughput (matching queries) with low latency. Partition by tile/city into many Redis instances/shards.

<br>

Routing

* Routing scale: if compute-intensive, use caching of route segments and approximate ETA via precomputed travel time grid; do exact routing only after assignment.

<br>

Event bus (Kafka)

* Ingest rate (events from drivers, rides, trips) say 30k events/sec — require sufficient partitions & retention.

<br>

Serving & DB

* Trip service: transactional DB (Postgres / NewSQL) with sharding by city or hashed ride\_id; ensure trip writes < 500 req/sec per shard to keep latency low.

<br>

Scaling

* Scale horizontally by city and by geo-shard. For global scale (many cities), deploy clusters per region with central analytics.

***

### 55 – 58 min — Operations, security & monitoring

<br>

#### Observability

* Metrics: match latency, offers per ride, acceptance rates, driver utilization, ETA accuracy, cancellations.
* Traces: end-to-end trace for request → match → assignment → start → end.
* Alerts: high match time, surge anomalies, spike in cancellations, ETA deviation.

<br>

#### Security & privacy

* Auth & tokens: short-lived tokens for driver apps; mutual auth for critical services.
* PII management: minimal storage of location history, encryption at rest, GDPR compliance (right to delete).
* Anti-spoofing: device attestation, anomaly detection for GPS jumps.
* Payment & fraud: secure PCI-compliant payment processing, fraud detection pipeline.

<br>

#### Operational practices

* Dark launches for surge logic, canary deployments, chaos testing for matching failover, regular reconciliation between trip state and driver payouts.

***

### 58 – 60 min — Trade-offs, evolution & summary (wrap-up)

<br>

#### Key trade-offs

* Latency vs accuracy: fast approximate ETA/navigation vs slower exact routing. Use approximate then refine.
* Sequential vs parallel offers: sequential reduces driver spamming & improves acceptance fairness; parallel reduces match time at cost of wasted offers. Choose per-market.
* Consistency vs availability: trip state needs strong consistency to avoid double assignment; other data (analytics) can be eventually consistent.
* Centralized vs distributed matching: per-city distributed matching scales better and keeps local latencies low; central control needed for global features.

<br>

#### Evolution path

1. MVP: per-city cluster, simple nearest-driver matching, routing from provider, basic pricing and payment.
2. Add: ML models for ETA, acceptance probability, demand prediction, surge orchestration, pooling.
3. Scale: multi-region deployment, cross-city pooling, dynamic routing optimization, driver incentives and real-time marketplace optimization.

<br>

#### One-sentence summary

<br>

Build a geo-sharded, event-driven platform: ingest real-time driver locations into an in-memory geo-index, run a low-latency matching/dispatch service that atomically assigns drivers via a strongly-consistent Trip Service, compute ETA/pricing via routing & ML models, and run analytics/fraud pipelines asynchronously — all designed to scale by city and recover deterministically from failures.

***

If you want, I can:

* convert this into a 5-slide interview deck (one slide per major time block), or
* generate a sequence diagram for request → match → assign → start → end flow, or
* run a detailed capacity calculator if you give me your expected DAU, rides/day, avg driver update frequency, and retention — I’ll compute numbers for Redis shards, Kafka partitions, and DB sizing.

<br>

Which would you like next?
