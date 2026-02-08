# #19 Netflix

Here’s a complete, time-boxed, 1-hour interview-ready answer for designing a Netflix-like video streaming system. It follows your system design interview structure, including functional & non-functional requirements, APIs/data model, architecture, deep dive, and trade-offs.

***

## 0 – 5 min — Problem recap, scope & assumptions

<br>

Goal: Design a scalable, high-availability video streaming platform (like Netflix) that allows users to stream movies/TV shows, supports content recommendations, handles millions of concurrent users, and provides high-quality video playback.

<br>

Scope for interview:

* User registration, login, and profile management.
* Video catalog (movies, shows, episodes).
* Streaming video content on-demand.
* Recommendations and personalized content.
* High scalability and fault-tolerance.
* Analytics for content popularity, watch history, QoS.

<br>

Assumptions:

* Peak concurrent users: 10M.
* Average video size: 1–2 GB.
* Users can stream HD/4K content.
* Multi-device support (TV, mobile, web).
* Content stored in CDN-backed object storage.

***

## 5 – 15 min — Functional & Non-Functional Requirements

<br>

#### Functional Requirements

<br>

Must

1. User management: Register, login, manage profiles.
2. Video catalog: Browse/search movies, shows, episodes.
3. Video streaming: Play video on-demand, pause, resume.
4. Content delivery: Support adaptive bitrate streaming (ABR).
5. Watch history: Track progress, resume playback.
6. Recommendations: Personalized suggestions based on user behavior.
7. Rating & reviews: Users can rate content.
8. Subscriptions & billing: Access control based on subscription plan.

<br>

Should

* Multi-language support (audio/subtitles).
* Offline downloads.
* Multiple profiles per account.

<br>

Nice-to-have

* Social features (share content, friends’ watch lists).
* Live streaming for events.
* Advanced analytics for content creators.

***

#### Non-Functional Requirements

* Availability: 99.99% uptime, global reach.
* Scalability: Handle millions of concurrent users globally.
* Latency: Minimal startup delay (<2 sec).
* Throughput: High data throughput for video streaming (CDN + origin).
* Durability: Reliable video storage; backups.
* Consistency: Eventual consistency acceptable for recommendations; strong consistency for subscriptions/billing.
* Security: Authentication, DRM, prevent piracy.
* Monitoring & observability: Track errors, buffering, QoS, recommendations, engagement.

***

## 15 – 25 min — API / Data Model

<br>

#### APIs

<br>

User Service

```
register_user(username, password, email)
login(username, password) -> token
get_user_profile(user_id)
update_user_profile(user_id, profile_data)
```

Video Service

```
get_video(video_id) -> video_metadata
search_video(query, filters) -> list[video_metadata]
stream_video(video_id, quality, user_id) -> video_stream_url
get_watch_history(user_id) -> list[video_progress]
```

Recommendation Service

```
get_recommendations(user_id, count=10) -> list[video_metadata]
update_user_activity(user_id, video_id, watch_time)
```

Billing Service

```
subscribe(user_id, plan)
get_subscription_status(user_id)
```

#### Data Models

<br>

User

```
{
  "user_id": "string",
  "name": "string",
  "email": "string",
  "password_hash": "string",
  "profiles": [{"profile_id": "string", "name": "string"}],
  "subscription": "plan_id"
}
```

Video

```
{
  "video_id": "string",
  "title": "string",
  "description": "string",
  "duration": int,
  "genres": ["Action", "Comedy"],
  "languages": ["EN", "ES"],
  "rating": float,
  "url": "cdn_url",
  "thumbnails": ["url1","url2"]
}
```

Watch History

```
{
  "user_id": "string",
  "video_id": "string",
  "last_watched_position": int,
  "last_watched_ts": "timestamp"
}
```

***

## 25 – 40 min — High-level architecture & data flow

```
+------------------+       +------------------+       +------------------+
| User Client      | <---> | API Gateway      | <---> | User Service     |
| (Web/Mobile/TV)  |       +------------------+       +------------------+
|                  |                               
|                  |       +------------------+       +------------------+
|                  | <---> | Video Service    | <---> | Video Storage    |
|                  |       +------------------+       | (Object Store + |
|                  |                                | CDN)              |
|                  |       +------------------+       +------------------+
|                  | <---> | Recommendation   | <---> | Analytics DB     |
|                  |       +------------------+       +------------------+
```

Components

1. API Gateway: Single entry for clients; routes requests to microservices.
2. User Service: Handles authentication, profile, subscription management.
3. Video Service: Streams videos using pre-signed URLs or CDN-backed streaming.
4. Recommendation Service: ML-based or heuristic recommendations; updates based on watch history.
5. Video Storage & CDN: Store original videos and distribute via global CDN for low latency.
6. Analytics Service: Collects viewing metrics, engagement, errors, QoS.
7. Billing Service: Manages subscriptions, payments, access control.

<br>

Data Flow

1. User requests video → API Gateway → Video Service → CDN URL returned.
2. Client starts streaming; adaptive bitrate adjusts quality.
3. Video progress sent to Watch History Service.
4. Recommendation Service updates user preferences and serves suggestions.

***

## 40 – 50 min — Deep dive — scalability, caching, streaming

<br>

#### Video streaming & CDN

* Videos stored in object storage (S3, GCS) and served via CDN.
* Adaptive Bitrate Streaming (HLS/DASH) for dynamic quality adjustment.
* Use pre-signed URLs or token-based access for secure streaming.

<br>

#### Recommendations

* Collaborative filtering or content-based recommendations.
* Batch updates or real-time streaming using Kafka for events.
* Caching hot recommendations for fast retrieval.

<br>

#### Caching & performance

* Cache metadata, thumbnails, popular videos in Redis/Edge caches.
* CDN ensures low-latency global streaming.
* Load balancing across Video Service instances.

<br>

#### Scaling & reliability

* Microservices scaled horizontally using Kubernetes or auto-scaling groups.
* Shard user data by user\_id; shard videos by video\_id.
* Replicate video storage across regions for disaster recovery.

***

## 50 – 55 min — Back-of-the-envelope calculations

<br>

Assumptions

* Peak concurrent users: 10M, average 2 Mbps per stream → 20 Tbps peak load.
* 1M new videos/year, average size 2 GB → 2 PB/year storage requirement.
* Cache top 1% popular videos in CDN → reduces load on origin.

<br>

Storage

* Object storage: S3/Blob Storage.
* Database: SQL or NoSQL for metadata (user, watch history, subscriptions).
* Analytics: Data warehouse for ML & reporting.

<br>

Throughput

* CDN handles majority of streaming load; origin only serves cache misses or new uploads.

***

## 55 – 58 min — Monitoring & ops

<br>

Monitoring

* Streaming QoS: buffering, latency, playback errors.
* API metrics: request latency, errors, user activity.
* System health: service uptime, storage capacity, CDN metrics.

<br>

Operational concerns

* Handle region failovers.
* DRM enforcement for content.
* Automatic transcoding for different resolutions/bitrates.

***

## 58 – 60 min — Trade-offs, evolution & summary

<br>

#### Trade-offs

* Centralized vs distributed video storage: Distributed needed for global scale and low latency.
* Real-time vs batch recommendation: Real-time more engaging but resource-intensive.
* SQL vs NoSQL: SQL for relational data (subscriptions, profiles); NoSQL for high throughput events, watch history.

<br>

#### Evolution

1. MVP: Basic video upload, streaming, metadata, simple recommendations.
2. Phase 2: Adaptive bitrate, CDN, analytics, caching hot content.
3. Phase 3: ML-based personalized recommendations, multi-device sync, offline downloads.

<br>

#### Summary

<br>

Design a scalable, high-performance video streaming system:

* Store videos in object storage + CDN for global access.
* Microservices handle user management, video streaming, recommendations, and billing.
* Real-time updates for watch history; caching and CDN for low-latency playback.
* Scalable architecture supports millions of concurrent users and PB-scale storage.

***

If you want, I can next create a sequence diagram showing a user streaming a video, updating watch history, and receiving recommendations, which is very handy to explain in an interview.

<br>

Do you want me to create that diagram next?
