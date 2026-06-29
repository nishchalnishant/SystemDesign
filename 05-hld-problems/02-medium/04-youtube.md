---
module: 05-hld-problems
topic: Medium
status: interview-ready
tags: [05-hld-problems, system-design, medium]
---
# Design YouTube

> **Difficulty**: Medium
> **Topics**: Video Transcoding, CDN, HLS Streaming, Metadata Search
> **Time**: 45 min
> **Companies**: Google, Netflix, Amazon

---

## Clarifying Questions

1. "Are we designing upload + transcoding + playback, or also recommendations?"
2. "What's the scale — 1B DAU, 500 hours uploaded per minute?"
3. "Do we need live streaming or only recorded video on demand?"
4. "What video quality targets — 360p to 4K? That multiplies storage."
5. "Do we need search and recommendations, or just upload and playback?"
6. "What's the latency target for playback start — sub-second?"

---

## Back-of-Envelope

```
1B DAU, 500 hours video uploaded/minute
  Upload rate: 500 × 60 min × ~100MB/min raw = 3TB raw/min = 50GB/sec uploads

Transcoding:
  Each video → 4 resolutions (360p, 720p, 1080p, 4K) + audio
  Raw 1hr video → 15GB; transcoded 4 resolutions → ~8GB total
  3TB raw/min × (8/15) = ~1.6TB transcoded output/min

Views: 1B users × 3 videos/day = 3B views/day = 34,700 views/sec
  Avg stream: 5 min × 5Mbps = 150MB/view
  Total bandwidth: 34,700 × 5Mbps = 174 Tbps → CDN required

CDN cache hit rate: 99%+ for popular content; long tail from S3
```

---

## APIs

```
// Initialize upload (returns resumable upload URL)
POST /api/v1/upload/init
  { "filename": "vacation.mp4", "size_bytes": 2147483648, "title": "..." }
  -> { "upload_id": "...", "upload_url": "...", "chunk_size": 5242880 }

// Upload chunks (PUT to S3 pre-signed URL directly)
PUT {upload_url}
  Range: bytes 0-5242879
  -> 200 OK or 308 Resume Incomplete

// Get video metadata + streaming manifest
GET /api/v1/videos/{video_id}
  -> { "video_id": "...", "title": "...", "hls_url": "...", "status": "ready" }

// Search
GET /api/v1/search?q=cooking+tutorial&page=1
  -> { "videos": [...], "total": 15000 }
```

---

## Architecture

```
Upload Path:
Client
  |-- POST /upload/init --> Upload Service --> PostgreSQL (video metadata, status=UPLOADING)
  |                                        --> S3 pre-signed URL returned to client
  |-- PUT chunks directly to S3 (5MB chunks, resumable)
  |-- Final chunk triggers S3 event --> Kafka "video.uploaded"
  |
  Kafka "video.uploaded"
    --> Transcoding Orchestrator
          --> FFmpeg Worker Fleet (DAG)
                +-- parallel jobs: 360p, 720p, 1080p, 4K
                +-- each job: 10-second HLS .ts segments + .m3u8 manifest
                +-- output to S3: transcoded/{video_id}/{resolution}/
          --> on all jobs complete: update PostgreSQL status=READY
          --> push .m3u8 manifests to CDN

Playback Path:
Client --> CDN (CloudFront)
  |-- Request: video/{video_id}/1080p/index.m3u8 (manifest)
  |-- CDN serves cached manifest (TTL 5 min)
  |-- Client fetches .ts segments (10s each)
  |-- CDN serves segments (TTL 24h; 99%+ hit rate)
  |-- Cache miss: CDN pulls from S3 origin

View Counter:
  Client view event --> View Service --> Redis INCR views:{video_id}
  Background job every 30s: flush Redis counts to PostgreSQL

Search:
  PostgreSQL → CDC (Debezium) → Kafka → Elasticsearch
  Search query hits Elasticsearch (BM25 + function_score for recency/views)
```

---

## Data Model

```sql
-- Video metadata (PostgreSQL)
CREATE TABLE videos (
    video_id        VARCHAR(11) PRIMARY KEY,  -- YouTube-style base64 ID
    user_id         BIGINT NOT NULL,
    title           VARCHAR(200),
    description     TEXT,
    s3_raw_key      VARCHAR(255),             -- original upload
    status          VARCHAR(20) DEFAULT 'uploading',
    duration_sec    INT,
    view_count      BIGINT DEFAULT 0,
    like_count      BIGINT DEFAULT 0,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    published_at    TIMESTAMPTZ
);
CREATE INDEX ON videos(user_id, published_at DESC);
CREATE INDEX ON videos(view_count DESC) WHERE status = 'ready';

-- Transcoding jobs (PostgreSQL)
CREATE TABLE transcoding_jobs (
    job_id      UUID PRIMARY KEY,
    video_id    VARCHAR(11) REFERENCES videos(video_id),
    resolution  VARCHAR(10),   -- '360p', '720p', '1080p', '4k'
    status      VARCHAR(20),   -- PENDING/RUNNING/SUCCEEDED/FAILED
    s3_output   VARCHAR(255),  -- path to .m3u8 manifest
    started_at  TIMESTAMPTZ,
    completed_at TIMESTAMPTZ
);
```

---

## Key Design Decisions

**1. Resumable chunked upload to S3**
Large video files (2-50GB) cannot be sent in one HTTP request (network drops, mobile switches). S3 Multipart Upload: client initiates, uploads 5MB chunks independently, finalizes. If connection drops: resume from last acknowledged chunk. The Upload Service only generates the pre-signed URL — actual bytes go directly to S3 (no proxy through app servers). Reduces upload latency and eliminates app server bandwidth bottleneck.

**2. HLS with 10-second segments for ABR**
HLS (HTTP Live Streaming): video is split into 10-second .ts segments. Player downloads a .m3u8 manifest listing segment URLs. Adaptive Bitrate (ABR): player monitors download speed and switches between resolution playlists on the fly. If bandwidth drops: switch from 1080p to 720p seamlessly. 10-second segments balance: shorter = faster quality switching, longer = fewer manifest requests. Segments are individually cacheable by CDN with long TTL.

**3. Idempotent FFmpeg DAG transcoding**
Input: S3 raw key (e.g., `raw/user123/abc.mp4`). Output: `transcoded/abc/720p/segment_000.ts`, etc. If a worker crashes mid-job: restart reads the same S3 input and writes to the same S3 output path. S3 PUT is idempotent (same content = same object). No partial-output cleanup needed. The orchestrator marks the job FAILED and retries; no manual intervention. Output segments are immutable once written.

**4. View count: Redis buffer → PostgreSQL batch flush**
`INCR views:{video_id}` on each view event: atomic, O(1), never blocks. Background job every 30s: scan all `views:*` keys, batch UPDATE PostgreSQL. Redis handles 34,700 view events/sec; PostgreSQL only sees 30s batch updates. On Redis restart: 30 seconds of view counts lost — acceptable (approximate counts are fine for a metric that accumulates millions). Flush on key eviction (LRU callback) as additional safety net.

---

## Deep Dives

**Transcoding worker fleet sizing**
500 hours/min raw video. Each worker transcodes at ~5× real-time (good hardware with GPU). 500 × 60 = 30,000 minutes of video/min. At 5× speed: 30,000 / 5 = 6,000 worker-minutes of capacity needed per minute → 6,000 workers running continuously. In practice: burst capacity with spot instances, queue depth alarms, and priority queuing (premium creators get faster transcoding SLA).

**CDN strategy for long-tail content**
Popular videos (top 0.1%): high CDN hit rate, warm cache at every PoP. Long-tail (99.9%): rarely requested from any given PoP. CDN miss → pulls from S3 (origin). For long-tail, CDN acts as a pass-through with modest caching (24h TTL but naturally evicted). Only store long-tail in S3 Standard (not attempt to pre-warm CDN). Cache efficiency: 99%+ for the top 0.1% that serves ~80% of traffic.

---

## Failure Scenarios

| Failure | Impact | Mitigation |
|---------|--------|------------|
| Transcoding worker crash | Video stuck in PROCESSING | Job has timeout; orchestrator retries failed jobs; idempotent output |
| S3 upload fails mid-chunk | Upload incomplete | S3 Multipart: resume from last chunk by fetching list of uploaded parts |
| CDN PoP outage | Region-specific buffering | Anycast DNS routes to next healthy PoP; CDN has 200+ PoPs globally |
| View count Redis crash | ~30s of view counts lost | Acceptable — counts are approximate; no user-facing correctness impact |
| Elasticsearch lag (CDC) | Search results stale | Acceptable up to 60s; Debezium reprocesses from Kafka offset on recovery |

---

## Interview Questions Asked

### Google
1. **"How does YouTube decide which CDN PoP serves a video to a user in Tokyo?"** → DNS-based anycast routing: YouTube's CDN (Google's own network with 200+ edge locations) resolves the video domain to the nearest PoP IP based on the user's DNS resolver location. The player requests the .m3u8 manifest from this PoP. If the PoP has the segment cached (high hit rate for popular videos), it's served immediately. If not: PoP fetches from the next cache tier or S3 origin. Google also uses BGP anycast for sub-millisecond routing decisions. The interviewer wants to hear: DNS resolution → nearest PoP → cache hierarchy → origin.
2. **"How does YouTube's recommendation system work at an architectural level?"** → Two-stage retrieval + ranking: (1) Candidate generation: retrieve 100-1000 candidate videos using collaborative filtering (user watch history embedded in vector space, ANN search for nearest neighbors). (2) Ranking: score each candidate with a neural network using rich features (user context, video freshness, click-through rate, watch time). Architecture: offline training (batch) → model serving (TensorFlow Serving) → two-phase pipeline called on each feed load. The system design component: user event log (watch, skip, like) → streaming pipeline (Kafka) → feature store → model training (daily retraining).

### Netflix
1. **"How does Netflix decide when to pre-cache content at CDN edges?"** → Netflix uses Open Connect Appliances (OCAs) — their own CDN. Pre-positioning: Netflix predicts what content will be popular in each region (based on subscriber demographics, new release schedule, trending data) and proactively pushes the transcoded files to local OCAs during off-peak hours (overnight). For new releases: pushed to 1,000+ OCAs before midnight release. Cache hit rate: 95%+. The interviewer wants to hear about predictive caching vs reactive caching.
2. **"How does Netflix handle a simultaneous release to 200M subscribers?"** → Staggered delivery via pre-positioned caches. The actual traffic hit at midnight is mostly from CDN edge nodes — not the origin. Netflix's OCAs in each ISP's network serve content locally. Origin traffic is minimal. The challenge is the burst on the first minute: spike dampening via TCP slow-start behavior in client players, adaptive buffering in Netflix's player (DASH), and geographically distributed release (midnight local time, not UTC midnight globally).

### Common Follow-ups
1. **"How do you handle a video upload that fails midway?"** → S3 Multipart Upload with resume. Each 5MB chunk gets an ETag on success. If upload fails, client calls `ListMultipartUploadParts` to see which chunks succeeded, then resumes from the next missing chunk. The upload_id is stored client-side. If the client restarts: re-initializes upload, gets a new upload_id, starts fresh. Cleanup: S3 lifecycle policy deletes incomplete multipart uploads after 7 days.
2. **"A video goes viral — 10M views in 1 hour. What breaks?"** → View counter Redis INCR: handles 10M/hr = 2,778/sec easily. CDN: popular video gets warm cache quickly — after first request per PoP, all subsequent from cache. PostgreSQL view_count: batch updates every 30s, not per view — no bottleneck. What might actually break: recommendation service (video suddenly needs to appear in recommendations, but the model retrains daily — lag in recommendations). Fix: real-time trending update pipeline (Kafka → Flink → update trending score) separate from the batch model.

---

## Interviewer Follow-Up Questions

**On transcoding:**
- "Why encode to HLS segments instead of serving the full video file?" → Single file serving: client must download the entire file or use HTTP range requests (Range: bytes 0-1MB), which works but has no adaptive quality switching. HLS segments: player requests only what it needs now (10 seconds ahead), can switch quality mid-stream based on bandwidth, and the CDN can cache each 10-second segment independently. Cache granularity: a 1-hour video with 360 10-second segments → CDN can cache the first 10% that 90% of viewers watch, without caching the full video.
- "How do you parallelize transcoding to reduce time-to-playback?" → DAG of parallel jobs per resolution. Start all 4 resolution jobs (360p/720p/1080p/4K) simultaneously after upload completes. Each resolution processes 10-second segments sequentially but independently of other resolutions. First priority: produce 360p and thumbnail within 60 seconds (so video shows as "available" quickly). Higher resolutions follow. Viewers who play immediately get 360p; by the time they hit minute 3, 1080p is usually ready. Kafka message `resolution.ready` triggers CDN manifest update per resolution.
- "Your FFmpeg job crashes on segment 47 of 360. What happens?" → Worker marks itself crashed (heartbeat stops). Orchestrator detects missed heartbeat after 2 minutes → marks job FAILED → re-enqueues. New worker picks up the job, starts from segment 0 (idempotent S3 writes — same segments are just overwritten). No manual cleanup. The restart wastes work (segments 0-46 are re-transcoded) but guarantees correctness. Alternative: checkpoint progress in DynamoDB at each segment boundary → resume from segment 47. More complex but saves ~30% re-work on large videos.

**On CDN and streaming:**
- "How does the video player switch from 1080p to 720p mid-stream?" → ABR algorithm in the player: measures bandwidth over the last 30 seconds (exponential moving average). If estimated bandwidth drops below 1080p's requirement (~5Mbps): switch to 720p playlist. Player updates the active .m3u8 to the 720p variant, fetches next segments from the 720p URL. Switch is seamless because segments are 10 seconds — player has buffered the current segment, next request goes to 720p. No interruption. If bandwidth recovers: switch back up after N segments at a stable rate.
- "How do you compute view count — is it a view if someone watches 1 second?" → Business rule (not architecture): YouTube defines a "view" as ≥30 seconds watched. The player sends view events at 30-second intervals (not on play click). The view service receives the 30s heartbeat event and increments the counter. This prevents click-farms from gaming counts by rapidly clicking play. Architecture: same Redis INCR, but triggered only by the 30s watch event, not the initial play event.
