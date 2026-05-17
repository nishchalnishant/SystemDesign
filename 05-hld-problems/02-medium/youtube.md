# Design YouTube

> **Difficulty**: Medium
> **Topics**: Video Processing, CDN, Adaptive Bitrate Streaming, Recommendations
> **Time**: 60 minutes
> **Companies**: Google, Netflix, Meta, TikTok

---

## Real-Life Analogy

Think of a video rental store with 2 billion customers and 800 million titles.

The challenge isn't the store itself — it's the logistics. When a filmmaker drops off a new movie, the store has to make 8 copies in different formats (VHS, DVD, Blu-ray, 4K) before putting them on the shelf. That conversion takes hours. In the meantime, customers who try to rent it see "processing." Once ready, the same physical disc needs to be simultaneously in every branch worldwide, because customers won't wait for shipping from the central warehouse. And the store needs to recommend titles you didn't ask for — which requires knowing that the 50 million people who watched "Inception" also tended to rent "Interstellar" next.

Now make the store run on a budget where storing 23 exabytes of inventory for 5 years is a real line item, and you need < 200ms before the movie starts playing. That's YouTube.

---

## Why This Is Hard

1. **Upload is a multi-stage pipeline, not a file copy**: A raw 10-minute 1080p video is 1 GB. Before it's watchable, it must be transcoded into 8 resolutions, segmented into 2-second HLS chunks, have thumbnails extracted, be content-moderation checked, and have its metadata indexed in Elasticsearch. This pipeline takes 5–30 minutes and any step can fail. The original file is your only source of truth — you cannot lose it.

2. **Storage cost dominates everything**: 500 hours of video uploaded per minute × multi-resolution transcoding = 13 PB of new storage per day. Cost optimization is a first-class concern: hot videos stay on fast SSDs at CDN edge, warm videos on S3 Standard, cold videos on S3 Glacier. The lifecycle policy is critical — a video that gets 0 views in 30 days shouldn't sit on expensive edge storage.

3. **Streaming requires CDN, not origin**: At 174K concurrent streams × 4 Mbps = 696 Gbps egress. No origin server cluster can serve that. 99% of streaming bandwidth must be served from CDN edge nodes — which means every video chunk must be pre-warmed or lazily cached at hundreds of PoPs worldwide. The streaming API doesn't serve video bytes; it redirects to a CDN-signed URL.

4. **View count is a write hotspot**: A viral video with 10M views/hour = 2,778 `UPDATE SET view_count = view_count + 1` operations per second against a single row. The database cannot handle this. Every counter update must be buffered in Redis and flushed periodically — which means view counts are intentionally approximate.

5. **Search needs a separate index**: PostgreSQL's full-text search cannot rank by relevance + recency + popularity at 174K queries/sec. Elasticsearch is required, and it must be kept in sync with the video metadata database. When a video is uploaded, its metadata must be indexed in Elasticsearch — but the two writes (PostgreSQL + Elasticsearch) are not atomic.

6. **Cache stampede on viral videos**: When a video goes viral, its CDN cache entry expires across all edge nodes simultaneously. Millions of requests hit the origin at the same time. A single origin cannot absorb this — you need either aggressive TTL extension (never let it expire while traffic is high) or a mutex lock (SETNX) so only one origin request is in-flight while others wait.

---

## Requirements

### Functional Requirements
1. Upload videos (various formats: MP4, AVI, MOV)
2. Stream videos with adaptive bitrate (start in < 200ms)
3. Search videos by title, tags, channel
4. Like/Dislike and comment on videos
5. Subscribe to channels
6. Recommendations based on watch history
7. View count and analytics

### Non-Functional Requirements
1. **High availability**: 99.99% uptime
2. **Low latency**: < 200ms to start streaming
3. **Scalability**: Handle 1 billion DAU, 500 hours of video uploaded/min
4. **Global distribution**: CDN for low-latency streaming worldwide
5. **Reliability**: No video loss during upload/encoding

---

## Capacity Estimation

```
Traffic:
  DAU: 1 billion
  Videos watched per user/day: 5
  Total video views/day: 5 billion
  Read QPS (average): 5B / 86,400 = 57,870 views/sec
  Peak QPS (3×): 174,000 views/sec

Upload:
  500 hours of video uploaded per minute
  Average video length: 10 minutes
  Upload rate: 500 hours/min ÷ 10 min/video = 3,000 videos/min = 50 videos/sec

Storage:
  Raw video: 1 GB per 10-minute 1080p video
  Daily raw: 50 videos/sec × 86,400 sec × 1 GB = 4.32 PB/day
  After transcoding (8 resolutions, ~3× overhead): 13 PB/day
  5-year total: 13 PB × 365 × 5 = 23.7 exabytes

Bandwidth (egress):
  Average bitrate: 4 Mbps (1080p)
  Concurrent viewers at peak: 174K
  Egress: 174K × 4 Mbps = 696 Gbps
  → Must be served from CDN; no origin cluster can handle this
```

---

## API Design

### 1. Upload Video
```http
POST /api/v1/videos
Content-Type: multipart/form-data
Authorization: Bearer <token>

{
  "title": "My Video",
  "description": "...",
  "tags": ["tech", "tutorial"],
  "category": "Education",
  "privacy": "public",   // public | unlisted | private
  "file": <binary>
}

Response: 202 Accepted
{
  "video_id": "dQw4w9WgXcQ",
  "status": "processing",
  "upload_url": "https://upload.youtube.com/..."
}
```

Note: `202 Accepted` (not `201 Created`) — the video is not yet ready. The upload triggers an async processing pipeline. The client polls or receives a webhook when status becomes `ready`.

### 2. Stream Video
```http
GET /api/v1/videos/{videoId}/stream?quality=auto

Response: 302 Redirect
Location: https://cdn.youtube.com/dQw4w9WgXcQ/master.m3u8?token=<signed>
```

The API does not stream bytes — it redirects to a CDN-signed URL. The client's HLS player then fetches `.m3u8` playlists and video segments directly from CDN. The origin is never in the hot path.

### 3. Search Videos
```http
GET /api/v1/search?q=python+tutorial&limit=20&offset=0

Response: 200 OK
{
  "results": [
    {
      "video_id": "...",
      "title": "Python Tutorial for Beginners",
      "thumbnail": "https://i.ytimg.com/vi/.../hqdefault.jpg",
      "views": 1000000,
      "upload_date": "2024-01-01",
      "duration": "14:32"
    }
  ],
  "total": 5000
}
```

### 4. Like / Comment
```http
POST /api/v1/videos/{videoId}/like
POST /api/v1/videos/{videoId}/comments
{
  "text": "Great video!",
  "timestamp": 120   // seconds into video (for timestamped comments)
}
```

---

## Database Schema

### Videos Table (PostgreSQL — sharded by channel_id)
```sql
CREATE TABLE videos (
    video_id VARCHAR(20) PRIMARY KEY,    -- YouTube-style Base62 ID
    title VARCHAR(255) NOT NULL,
    description TEXT,
    channel_id BIGINT NOT NULL,
    upload_date TIMESTAMP DEFAULT NOW(),
    duration_seconds INT,
    privacy ENUM('public', 'unlisted', 'private'),
    status ENUM('processing', 'ready', 'failed'),
    raw_s3_key VARCHAR(500),             -- Original upload; never deleted
    view_count BIGINT DEFAULT 0,         -- Approximately correct (Redis-buffered)
    like_count BIGINT DEFAULT 0,
    INDEX idx_channel_id (channel_id),
    INDEX idx_upload_date (upload_date)
);

CREATE TABLE video_formats (
    id BIGSERIAL PRIMARY KEY,
    video_id VARCHAR(20) REFERENCES videos(video_id),
    resolution VARCHAR(10),              -- 1080p, 720p, 480p, 360p, 240p, 144p
    codec VARCHAR(20),                   -- h264, h265, av1
    s3_key VARCHAR(500),                 -- HLS master playlist path
    bitrate_kbps INT
);
```

### Comments Table (Sharded by video_id)
```sql
CREATE TABLE comments (
    comment_id BIGINT PRIMARY KEY,
    video_id VARCHAR(20) NOT NULL,
    user_id BIGINT NOT NULL,
    text TEXT,
    timestamp_seconds INT,               -- Timestamped comment (optional)
    created_at TIMESTAMP DEFAULT NOW(),
    likes INT DEFAULT 0,
    INDEX idx_video_id (video_id),
    INDEX idx_created_at (created_at)
);
```

### View Counts (Redis + Batch Flush)
```java
// Hot path: increment in Redis (O(1), no DB touch)
redis.incr("views:" + videoId);

// Cold path: background job flushes every 5 minutes
// Runs on a dedicated counter-flusher service
for (Map.Entry<String, Long> entry : redis.scanIter("views:*")) {
    String videoId = entry.getKey().replace("views:", "");
    Long delta = entry.getValue();
    db.execute(
        "UPDATE videos SET view_count = view_count + ? WHERE video_id = ?",
        delta, videoId
    );
    redis.delete(entry.getKey());
}
```

View counts are **intentionally approximate**. A video showing "1,234,567" views is actually "1,234,567 ± a few thousand" — users don't notice, and the alternative (synchronous DB writes at 174K/sec) is not feasible.

### Elasticsearch Video Index
```json
{
  "mappings": {
    "properties": {
      "video_id":     {"type": "keyword"},
      "title":        {"type": "text", "analyzer": "standard",
                       "fields": {"suggest": {"type": "completion"}}},
      "description":  {"type": "text"},
      "tags":         {"type": "keyword"},
      "channel_name": {"type": "text"},
      "upload_date":  {"type": "date"},
      "view_count":   {"type": "long"},
      "duration":     {"type": "integer"}
    }
  }
}
```

**Ranking factors (composite score):**
1. BM25 text relevance (title + description + tags)
2. View count (logarithmic popularity boost)
3. Recency decay (exponential decay from upload_date)
4. User engagement (CTR × watch-time ratio from BigQuery)

---

## Architecture

```
          Client (Web, Mobile, SmartTV)
                     ↓
            CDN (CloudFront/Akamai)
            ← Serves 99% of video bytes
                     ↓ (cache miss only)
              API Gateway
              (Rate Limiting, Auth)
                     ↓
    ┌────────┬────────┬─────────┬────────────┐
    ↓        ↓        ↓         ↓            ↓
Upload   Stream   Search   Recommend    Analytics
Service  Service  Service   Service      Service
    ↓        ↓        ↓         ↓            ↓
    │        │      Elastic    ML          Kafka
    │        │      Search    Pipeline     → BigQuery
    ↓        ↓
    S3      Redis
  (Raw +    (Metadata
  Encoded   Cache,
  Videos)   View Counts)
    ↓        ↓
    └────────┘
         ↓
    PostgreSQL
    (Metadata,
     Comments)

Video Processing Pipeline (async):
  S3 raw upload
     → Kafka job
     → Transcoder cluster (FFmpeg / GPU nodes)
     → S3 encoded versions (HLS segments)
     → Thumbnail Generator
     → Elasticsearch indexer
     → DB status update → "ready"
```

---

## Detailed Component Design

### 1. Video Upload and Processing Pipeline

```
Phase 1 — Upload (synchronous, returns immediately):
  1. Client → POST /videos with raw file
  2. Upload Service generates video_id
  3. Upload Service stores raw file to S3: "raw/{video_id}/original.mp4"
  4. Upload Service inserts metadata to PostgreSQL: status=processing
  5. Upload Service publishes transcode job to Kafka
  6. Upload Service returns {video_id, status: "processing"} → 202

Phase 2 — Processing (async):
  7. Kafka → Transcoder Worker picks up job
  8. Worker downloads raw file from S3
  9. DAG Workflow executes (in parallel where possible):
       [Inspect file metadata]
           ↓
       [Content moderation scan]  [Watermark embed]
           ↓                            ↓
       [Transcode to 1080p]  [Transcode to 720p]  [Transcode to 480p]
       [Extract audio track]  [Generate thumbnails]
           ↓ (all complete)
       [Generate HLS master playlist]
           ↓
       [Upload segments to S3]
           ↓
       [Warm CDN cache for first 60 seconds]
           ↓
       [Index in Elasticsearch]
           ↓
       [Update PostgreSQL: status=ready]
```

**Why DAG (Directed Acyclic Graph)?** Independent tasks (transcoding 1080p vs. 720p vs. thumbnail extraction) run in parallel on different GPU nodes. A DAG scheduler (Apache Airflow or custom) tracks dependencies and fires each task as soon as its upstream tasks complete. Without this, all steps would run sequentially and encoding would take 5× longer.

**Transcoding details:**
```
Input: raw MP4 (H.264, 1080p, 30fps, ~1 GB)
Output HLS segments (2-second chunks):
  ├─ 2160p/: ~15 Mbps (4K — only for 4K source uploads)
  ├─ 1080p/: ~5 Mbps
  ├─ 720p/:  ~2.5 Mbps
  ├─ 480p/:  ~1 Mbps
  ├─ 360p/:  ~500 Kbps
  └─ 240p/:  ~250 Kbps
master.m3u8 (points to all quality playlists)

Codecs:
  H.264 (AVC): Universal compatibility — used for all resolutions
  H.265 (HEVC): 40% smaller file at same quality — used for 1080p+ on supported clients
  AV1: Best compression — used for mobile to reduce data costs

Hardware: NVIDIA NVENC on GPU nodes → 10–50× faster than CPU FFmpeg
```

### 2. Adaptive Bitrate Streaming (ABR)

```
master.m3u8 (returned by CDN):
  #EXTM3U
  #EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=1920x1080
  1080p/video.m3u8
  #EXT-X-STREAM-INF:BANDWIDTH=2500000,RESOLUTION=1280x720
  720p/video.m3u8
  #EXT-X-STREAM-INF:BANDWIDTH=1000000,RESOLUTION=854x480
  480p/video.m3u8

Client HLS player logic:
  1. Fetch master.m3u8
  2. Measure available bandwidth (measure time to download first segment)
  3. Start playback at appropriate quality tier
  4. Every 2 seconds: re-evaluate bandwidth
  5. Switch quality tier up/down without interrupting playback
  6. Pre-buffer next 3 segments of current quality
```

This is why streaming starts in < 200ms even for a 1-hour video: the client only needs the first 2-second segment (a few hundred KB at low quality), not the entire video.

### 3. Streaming Architecture (Redirect Pattern)

```java
public StreamResponse getVideoStream(String videoId, String userId) {
    // 1. Check Redis for cached metadata (avoid DB hit on hot videos)
    VideoMetadata meta = videoMetadataCache.get(videoId);
    if (meta == null) {
        meta = db.getVideoMetadata(videoId);
        videoMetadataCache.set(videoId, meta, Duration.ofHours(1));
    }

    // 2. Verify video is ready and user has access
    if (meta.getStatus() != READY) throw new VideoNotReadyException();
    if (!canAccess(userId, meta)) throw new ForbiddenException();

    // 3. Generate time-limited signed CDN URL (prevents hotlinking)
    String cdnUrl = cdnSigner.sign(
        "https://cdn.youtube.com/" + videoId + "/master.m3u8",
        Duration.ofHours(4),
        userId
    );

    // 4. Async: increment view counter in Redis
    redis.incr("views:" + videoId);

    // 5. Return redirect — API never touches video bytes
    return StreamResponse.redirect(cdnUrl);
}
```

### 4. CDN Strategy

```
Multi-CDN: CloudFront (primary) + Akamai (failover)
  → Geographic load balancing (Anycast DNS routes to nearest PoP)
  → If CloudFront PoP fails, DNS failover to Akamai in < 60 seconds

Cache hierarchy:
  L1: CDN Edge PoP (fastest, limited capacity)
      TTL: 24h for top 1% videos by views
      TTL: 1h for others
  L2: CDN Regional Cache (e.g., US-East regional hub)
      TTL: 7 days
  L3: Origin S3 (infinite capacity, slowest)

Prefetching:
  - When a video starts trending (views/hour > threshold), proactively warm
    CDN edge nodes by pushing the first 60 seconds of each quality tier
  - Reduces first-viewer latency from 2s (cold origin fetch) to < 100ms
```

### 5. Recommendation Engine

```
Two-Tower Model:

  User Tower                    Video Tower
  ┌─────────────────┐          ┌─────────────────────┐
  │ Watch history   │          │ Title embeddings     │
  │ Like history    │  ──────▶ │ Tags                 │
  │ Demographics    │          │ Engagement metrics   │
  │ Time of day     │          │ (CTR, watch time)    │
  └────────┬────────┘          └──────────┬───────────┘
           │                              │
           ▼                              ▼
      User embedding (128-dim)   Video embedding (128-dim)
           │                              │
           └──────────┬───────────────────┘
                      ↓
               dot product similarity score
                      ↓
              Candidate retrieval (top 500)
                      ↓
              Ranking model (XGBoost)
              (applies recency boost, diversity)
                      ↓
              Final 10 recommendations

Training:
  - Batch retrain every 24 hours on BigQuery watch event data
  - Online updates for trending videos (lightweight signal injection)
  - A/B testing framework: different model versions for different user buckets

Serving:
  - Pre-computed recommendations for active users (updated every 4 hours)
  - Cache in Redis: Key: recs:{user_id}, TTL: 4 hours
  - On cache miss: real-time inference (slower, ~200ms vs ~5ms)
```

---

## Scalability Strategies

### Database Sharding

```
Videos: Shard by channel_id
  All videos from one channel on the same shard → single-shard queries
  for "channel's recent uploads"

Comments: Shard by video_id
  All comments for one video on the same shard

Hot shard problem for viral videos:
  Video with 10M comments → all on one shard
  Solution: Sub-shard by time bucket
    Shard key: (video_id, year_month)
    Comments in 2026-05 → one shard
    Comments in 2026-04 → different shard
```

### Write Optimization for Hot Counters

```
Problem: Viral video → 2,778 view count increments/sec against one DB row

Solution: Write-behind Redis buffering

Hot path (synchronous): redis.incr("views:" + videoId)    // ~0.1ms
Cold path (async, every 5 min):
  SCAN all "views:*" keys
  Batch UPDATE videos SET view_count = view_count + delta
  DELETE redis keys

For likes (need to be accurate for user's "did I like this" check):
  Store per-user likes in a separate table (not counter)
  Count is derived, not stored → no hot row
  Cache "did user X like video Y": Redis set with TTL 1h
```

### Cache Stampede Prevention

```java
// Problem: Viral video's cache entry expires, 1M requests hit origin simultaneously
// Solution: SETNX mutex lock — only one request populates the cache

public VideoMetadata getVideoMetadata(String videoId) {
    String cacheKey = "meta:" + videoId;
    VideoMetadata cached = redis.get(cacheKey);
    if (cached != null) return cached;

    // Cache miss — try to acquire lock
    String lockKey = "lock:" + videoId;
    boolean gotLock = redis.setnx(lockKey, "1");
    redis.expire(lockKey, 10);  // Lock expires in 10s (safety)

    if (gotLock) {
        // This request fetches from DB and populates cache
        VideoMetadata meta = db.getVideoMetadata(videoId);
        redis.setex(cacheKey, 3600, meta);
        redis.del(lockKey);
        return meta;
    } else {
        // Another request is populating cache — wait briefly and retry
        Thread.sleep(100);
        return getVideoMetadata(videoId);  // Recursive retry (cache should be warm now)
    }
}
```

Alternative: **Probabilistic early expiration** — before cache entry expires, proactively refresh it. No stampede because the entry is refreshed while still serving traffic.

---

## Failure Scenarios

### Transcoding Worker Fails Mid-Job
**Impact:** Video stuck in `processing` state; creator can't see their upload
**Mitigation:**
- Kafka message not acknowledged until job fully complete → re-delivered to another worker
- S3 raw file is the source of truth — re-processing always possible
- Transcode job is idempotent: overwrites output S3 keys if they already exist
- SLA alert: if video stays in `processing` > 30 minutes, alert on-call

### CDN PoP Goes Down
**Impact:** Users in that region get redirected to origin; latency spikes
**Mitigation:**
- Multi-CDN: DNS failover to Akamai PoP in same region
- Origin S3 can handle limited traffic (not 174K streams, but enough to cover failover period)
- RTO: < 60 seconds (DNS TTL for CDN health checks)

### Elasticsearch Index Corruption
**Impact:** Search returns incomplete results
**Mitigation:**
- Elasticsearch cluster with 3 nodes, 1 replica per shard
- If index corrupted: replay Kafka topic `video-metadata-events` to rebuild index
- Search can degrade to PostgreSQL LIKE queries (slower but functional)

### DB Shard Failure
**Impact:** Videos/comments from affected channels unavailable
**Mitigation:**
- Synchronous replication to standby replica (PostgreSQL streaming replication)
- Automatic failover: < 60 seconds
- Read queries served from replica during failover window

---

## Trade-offs

| Aspect | Choice | Alternative | Trade-off |
|--------|--------|-------------|-----------|
| **Storage** | S3 + CDN | Self-hosted storage | $millions/month vs. operational simplicity and infinite scale |
| **Transcoding** | Multi-resolution HLS | Single resolution | 3× storage cost vs. adaptive quality for all network conditions |
| **View counts** | Eventually consistent (Redis-buffered) | Synchronous DB writes | Approximate accuracy vs. DB hotspot at 174K/sec |
| **Recommendations** | Batch retrain (24h) | Real-time updates | Stale by up to 24h vs. continuous compute cost |
| **Search** | Elasticsearch | PostgreSQL full-text | Operational complexity vs. relevance ranking + autocomplete |
| **Cache stampede** | SETNX mutex | Probabilistic early refresh | Simpler to reason about vs. no lock contention |

---

## Interview Discussion Points

**Q: How to handle viral videos (the "HN Effect")?**
CDN prefetching: when view velocity exceeds a threshold (e.g., 10K views/min), proactively warm all CDN edge PoPs for that video. Read replicas for metadata DB to distribute read traffic. Redis SETNX mutex to prevent cache stampede. Rate limiting per video to prevent a single viral video from consuming all CDN origin bandwidth. Graceful degradation: serve lower quality if origin is overloaded.

**Q: How to prevent duplicate uploads?**
Perceptual hashing (pHash or dHash) on sampled video frames (every 10th second). During upload processing pipeline, compute hash and compare against existing videos in an Elasticsearch index. If similarity > 95%, flag as potential duplicate. Trade-off: false positives (creative re-edits flagged as duplicates) vs. compute cost of hashing every video.

**Q: Optimizing for mobile users on slow networks?**
ABR starts at 144p (500 KB/sec threshold) and auto-upgrades. Pre-buffer next 3 segments while watching current. Thumbnail sprite sheets (single image containing all thumbnails) instead of per-second video scrubbing — reduces thumbnail API calls by 1,000×. Prefetch the next recommended video's first 5 seconds while current video plays.

**Q: Disaster recovery?**
Multi-region S3 replication (Cross-Region Replication enabled). Raw videos are the source of truth — transcoded versions can be regenerated. Daily PostgreSQL snapshots to separate region. Multi-CDN strategy means CDN failure doesn't cause service outage. RTO: < 60 seconds for CDN failover, < 5 minutes for DB failover.

**Q: How do you keep search results fresh after a video is uploaded?**
Dual-write: upload service publishes to Kafka topic `video-indexed-events`. A separate Elasticsearch indexer consumes this and indexes the new video. Typical lag: 30–60 seconds from upload completion to searchable. Not atomic with the DB write — if Elasticsearch indexing fails, it retries from Kafka (7-day retention). A nightly reconciliation job compares PostgreSQL videos with Elasticsearch index and re-indexes any gaps.

---

## Interview Questions Asked

### Google
1. **"Design YouTube video upload and playback."** → Tests full pipeline thinking — upload → transcode → store → serve; key answer: async transcoding via Kafka-backed worker pool producing HLS multi-resolution output stored in S3 behind CDN.

### Netflix
1. **"Design a video streaming service."** → Tests adaptive bitrate and CDN depth; key answer: HLS/DASH with ABR, multi-CDN with anycast routing, Open Connect appliances at ISP level to cache popular titles close to users.

### Common Follow-ups
1. **"How do you generate video thumbnails at scale?"** → Tests pipeline design; ffmpeg frame extraction at fixed intervals (e.g., every 10s) as a step in the transcode worker; thumbnails stored in S3, URLs written to metadata DB; sprite sheets bundle all thumbnails into one image to reduce seek API calls.
2. **"How does the content recommendation pipeline work?"** → Tests ML systems integration; batch collaborative filtering (24h cycle) generates candidate videos; real-time feature store updates watch-time signals; a two-tower model ranks candidates at serving time.
3. **"How does Content ID (copyright detection) work?"** → Tests fingerprinting knowledge; audio/video fingerprints generated at upload and compared against a rights-holder fingerprint DB; match triggers claim or block; fingerprint matching uses locality-sensitive hashing for sub-second lookup across billions of clips.
4. **"What are the differences between live streaming and pre-recorded?"** → Tests protocol awareness; live uses lower-latency protocols (LL-HLS, WebRTC for < 2s latency) with smaller segment sizes (1–2s vs. 6s); no seek, no transcode parallelism, origin becomes a live relay rather than S3.
5. **"How do you prioritize the transcoding queue?"** → Tests queue design; separate priority queues per creator tier (partner/premium vs. standard); newly uploaded videos jump the queue for faster availability; background jobs re-transcode older videos to new codecs (AV1) at low priority.
