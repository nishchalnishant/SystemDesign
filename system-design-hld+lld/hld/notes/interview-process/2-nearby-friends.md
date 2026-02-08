# #2 Nearby friends

Here’s a complete, 1-hour interview answer for designing a “Nearby Friends” mobile feature—similar to what Facebook once offered.

The flow mirrors a real system design interview: problem scoping → requirements → APIs → architecture → scaling & trade-offs.

***

### 0 – 5 min ▸ Problem Understanding & Assumptions

<br>

Goal

Show a list of a user’s friends (from their social graph) who are geographically close and have opted in to location sharing.

<br>

Key Assumptions (for back-of-envelope numbers)

* 100 M monthly active users (MAU), 30 M daily active (DAU).
* 20 M have enabled Nearby Friends.
* Each user has \~300 friends (avg).
* Location updates every 1 min while active.
* Peak 100 k “who’s near me?” queries/sec.
* Query result latency target P95 < 200 ms.
* Accuracy \~20 m; availability 99.9 %.

***

### 5 – 15 min ▸ Requirements

<br>

#### Functional Requirements

1. Opt-in & Privacy Controls
   * User can enable/disable sharing anytime.
   * Granular controls: “All friends”, “Close friends only”, or specific lists.
   * Visibility TTL (e.g., share for 1 hour).
2. Location Update Service
   * Mobile client sends updates (user\_id, lat, lon, ts) at 30–60 s intervals when active.
   * Must deduplicate and handle out-of-order updates.
3. Nearby Friends Query
   * Given current user and a radius (default 1–5 km), return friend list sorted by distance & optionally by recency.
4. Presence & Status
   * Show last updated time, battery status (optional), and whether user allows background location.
5. Notifications
   * Optional push when a close friend comes within X km.
6. Admin & Analytics
   * Abuse detection (spoofed GPS, scraping).
   * Aggregate stats for feature usage.

***

#### Non-Functional Requirements

* Performance
  * Query P95 < 200 ms.
  * Update visible to friends within ≤ 10 s.
* Scalability
  * Handle 20 M sharers × 1 update/min ≈ 333 k writes/sec peak.
  * Reads \~100 k QPS at peak.
* Availability: 99.9 %, multi-AZ + multi-region.
* Consistency: Eventual for queries; strong for opt-in/out changes.
* Security & Privacy
  * End-to-end TLS.
  * OAuth2/JWT authentication.
  * Fine-grained ACLs based on social graph.
  * GDPR/CCPA compliance, “right to be forgotten”.
* Observability
  * Metrics: update latency, query latency, opt-in rate, stale-data %.
* Battery/Network Efficiency
  * Adaptive update frequency (motion detection, significant-change API).

***

### 15 – 25 min ▸ API Design

| Method | Endpoint         | Request                                        | Response                                           | Notes                   |
| ------ | ---------------- | ---------------------------------------------- | -------------------------------------------------- | ----------------------- |
| POST   | /location/update | {user\_id, lat, lon, ts}                       | 200 OK                                             | Idempotent              |
| GET    | /nearby-friends  | user\_id, radius\_km                           | \[ {friend\_id, lat, lon, distance, updated\_at} ] | Checks privacy settings |
| PATCH  | /share-location  | {enabled: true/false, visibility\_scope}       | 200 OK                                             | Manage opt-in           |
| WS/SSE | /presence-stream | subscribe to real-time friend proximity events | —                                                  | Optional push channel   |

***

### 25 – 40 min ▸ High-Level Architecture

```
Mobile App
   |
API Gateway  +  Auth Service
   |
+------------------------------+
|    Location Service Tier     |
+------------------------------+
   |        |
Message Bus (Kafka / Kinesis)  |
   |        |
Location Updater Workers   <---+
   |
Geo Store Layer:
   - Redis Geo / Aerospike for hot data
   - PostGIS / H3 index for durability
   |
Social Graph Service (friend lists, privacy rules)
   |
Nearby Friends Query Service
```

Flow

1.  Updates: Mobile → API Gateway → Kafka → Workers →

    _Write to Redis GEO (hot)_ and durable DB (PostGIS/H3).
2. Query:
   * Pull friend list from Social Graph Service.
   * Intersect with Geo Store query (Redis GEORADIUS or H3 cell lookup).
   * Filter by privacy rules & TTL.

<br>

Geo-Sharding

* Partition by H3/Geohash cell.
* Each cell stored in a Redis cluster partition.

<br>

Privacy Filter Path

* Query service retrieves friend IDs from Social Graph (cached in Memcached).
* Only include friends who are both _nearby_ and _sharing_.

***

### 40 – 50 min ▸ Data Model & Algorithms

<br>

#### Tables / Collections

<br>

UserLocation

```
user_id (PK)
lat
lon
updated_at
visibility_scope ENUM
ttl_seconds
```

LocationHistory (optional analytics)

```
user_id + timestamp
lat
lon
```

SocialGraph – separate service (friend edges with privacy settings).

<br>

#### Core Algorithm

* Update ingestion: validate → compute H3 cell → write to Redis cluster keyed by cell.
* Nearby Query:
  1. Fetch friend list.
  2. Determine user’s cell + neighboring cells (k-ring search).
  3. Query Redis sets for those cells.
  4. Filter by radius and privacy/TTL.
  5. Sort by distance.

***

### 50 – 55 min ▸ Back-of-the-Envelope Numbers

* Writes: 20 M sharers × 1 update/min ≈ 333 k writes/sec.
  * At 100 B/update ≈ 33 MB/sec → \~2.8 TB/day raw.
* Reads: 100 k QPS × 1 KB result ≈ 100 MB/sec outbound.
* Cache size: store only latest location (\~200 B/user) → \~4 GB for 20 M users (× replication ≈ 12 GB).

***

### 55 – 60 min ▸ Trade-offs & Future Enhancements

| Option          | Pros                     | Cons                                |
| --------------- | ------------------------ | ----------------------------------- |
| Redis GEO only  | Ultra-fast               | Expensive RAM, eventual persistence |
| PostGIS only    | Rich queries             | Higher latency                      |
| Hybrid (chosen) | Low latency + durability | Complexity                          |

Battery/Privacy

* Adaptive update interval (motion sensor, “significant-change” API).
* Allow coarse location mode (city-level).
* Ephemeral storage: auto-expire after N hours.

<br>

Future Work

* Add ephemeral proximity notifications (“friend X is within 500 m”).
* ML for predicting which friends you care about most.
* End-to-end encryption of location payloads for even stronger privacy.

***

#### ✅ Interview Takeaways

* Start with clear functional & non-functional requirements anchored in privacy.
* Show API & data flows that respect the social graph.
* Provide capacity planning numbers and scaling strategy.
* Highlight privacy & battery trade-offs—critical for a real mobile feature.

<br>

This structure comfortably fills a 1-hour system-design interview while demonstrating both technical depth and awareness of user-centric concerns.
