> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design YouTube — a video upload, transcoding, and streaming platform with CDN delivery, search indexing, and recommendation engine at global scale.
>
> **Key design decisions:**
> - Video upload: client → resumable upload → object storage (GCS/S3); chunked to handle large files and network interruptions
> - Transcoding pipeline: upload triggers message to Kafka → transcoding workers (FFmpeg) → multiple resolutions (360p/720p/1080p/4K) → stored in S3 per quality tier
> - CDN delivery: videos served from CDN edge nodes; adaptive bitrate streaming (HLS/DASH) selects quality based on bandwidth
> - Metadata storage: video metadata (title, description, tags, duration, channel_id) in PostgreSQL; Elasticsearch for search
> - View count: async counter via Kafka → batch aggregation; don't write to DB per view (thundering herd)
> - Recommendations: collaborative filtering (viewers who watched X also watched Y); updated offline via Spark; served from feature store
> - Capacity: 500 hours of video uploaded per minute; storage at multiple quality levels; CDN handles 99% of bandwidth
>
> **Key takeaway:** Separate the upload path (async transcoding pipeline) from the streaming path (CDN + adaptive bitrate) — these have very different throughput and latency requirements.

---
module: 05-hld-problems
topic: Medium
status: unread
tags: [05-hld-problems, system-design, medium, youtube, video-streaming, cdn, transcoding]
---
# Design YouTube

> **Difficulty**: Medium | **Asked at**: Google, Netflix, TikTok, Twitch

---

## Problem Statement

Design a video streaming platform like YouTube. Users upload videos, which are transcoded to multiple resolutions and streamed globally. Users watch videos, search by title/description, and receive recommendations.

---

## Functional Requirements

1. **Upload**: Users upload videos (up to 12 hours, ~50 GB raw)
2. **Transcode**: Convert to multiple formats and resolutions (4K, 1080p, 720p, 480p, 360p)
3. **Stream**: Serve video to viewers with adaptive bitrate (adjusts to bandwidth)
4. **Search**: Find videos by title, description, tags
5. **Recommendations**: Related videos, personalized home feed
6. **Engagement**: Likes, comments, subscriptions

---

## Non-Functional Requirements

- **Scale**: 500M DAU, 500 hours of video uploaded/minute, 1B hours watched/day
- **Upload throughput**: 500h/min × 50 GB/h = 25,000 GB/min → 400 GB/sec raw uploads
- **Stream throughput**: 1B hours/day ÷ 86,400s × 2 Mbps avg = 23 Tbps served globally
- **Latency**: Video start < 2s; search < 200ms
- **Availability**: 99.99% for viewing; 99.9% for upload (can retry)

---

## Core Entities

| Entity | Key Fields |
|--------|-----------|
| `Video` | video_id, uploader_id, title, description, tags[], status (PROCESSING/PUBLISHED), duration, view_count |
| `VideoRendition` | video_id, resolution (1080p), codec, bitrate, s3_key, duration |
| `Channel` | channel_id, user_id, subscriber_count, videos[] |
| `Comment` | comment_id, video_id, user_id, text, like_count, created_at |
| `WatchHistory` | user_id, video_id, watched_at, progress_seconds |

---

## API Design

```http
POST /api/v1/videos/upload-url
Body: { "title": "...", "filesize": 5000000000 }
Response 200: { "video_id": "v123", "upload_url": "https://s3.../...", "parts": [...] }
# Client uploads directly to S3 via multipart upload

POST /api/v1/videos/{video_id}/publish
Body: { "title": "System Design Interview", "description": "...", "tags": ["tech"] }
Response 200: { "status": "PROCESSING" }

GET /api/v1/videos/{video_id}
Response 200: {
  "video_id": "v123",
  "title": "...",
  "manifest_url": "https://cdn.youtube.com/v123/manifest.m3u8",
  "thumbnail_url": "...",
  "view_count": 1420000
}

GET /api/v1/search?q=system+design&sort=relevance&page=1

POST /api/v1/videos/{video_id}/progress
Body: { "progress_seconds": 342 }  # user's watch position
```

---

## High-Level Design

```
Upload path:
  Client → S3 (multipart, direct-to-S3 presigned)
    → S3 event → Kafka `video-uploaded`
    → Transcoding Farm (K8s jobs or dedicated workers):
        Extract frames → generate thumbnails
        FFmpeg: transcode to H.264 HLS (4K/1080p/720p/480p/360p)
        Store renditions in S3
    → Update video status: PUBLISHED
    → Elasticsearch: index video metadata

Stream path:
  Client → CDN (CloudFront)
    → CDN serves HLS segments from S3 (cached at edge)
    → Adaptive Bitrate Player: downloads manifest.m3u8 → selects resolution based on bandwidth
    → If CDN miss: origin = S3

Search path:
  Client → Search Service → Elasticsearch (title, description, tags)

Recommendation path:
  Client → Recommendation Service → ML model → Cassandra (user watch history)
```

---

## Deep Dive 1: Video Transcoding Pipeline

**Problem**: A raw 4K video at 50 GB must be transcoded to 5 resolutions before it's viewable. Transcoding is CPU-intensive (10–100× real-time for 4K). For 500 hours uploaded/minute, the transcoding farm must handle an enormous sustained load.

**Parallel transcoding**:
- Split video into 2-minute segments (GOP-aligned — Group of Pictures, splits on keyframes)
- Transcode each segment to each resolution independently in parallel
- Each job is stateless: takes one segment, outputs one rendition segment
- Reassemble: after all segments are transcoded, concatenate into the final HLS stream

**Job scheduling**: Kafka topic `transcode-jobs`. Each message: `{video_id, segment_id, resolution, input_s3_key}`. Workers are K8s jobs with GPU nodes for faster FFmpeg. Auto-scales horizontally based on Kafka lag.

**Transcoding time**: 
- 1-hour video → 30 × 2-minute segments
- Each segment transcoded to 5 resolutions = 150 jobs
- Each job: ~2 min on a CPU worker, ~10s on a GPU node
- With 50 GPU workers: 150 jobs × 10s / 50 workers = 30 seconds total
- 1-hour video is ready to stream in ~30 seconds with enough workers

**HLS output format**:
```
v123/
  manifest.m3u8          (adaptive manifest listing all renditions)
  1080p/
    playlist.m3u8        (1080p segment list)
    seg000.ts            (2-min segment)
    seg001.ts
  720p/
    ...
```

---

## Deep Dive 2: Adaptive Bitrate Streaming (ABR)

**Problem**: A viewer on a slow mobile connection (1 Mbps) cannot stream 1080p (5 Mbps). A viewer on fiber (100 Mbps) should get 4K. The player must dynamically switch quality without interruption.

**HLS (HTTP Live Streaming)**:
- Server generates an HLS manifest (`manifest.m3u8`) listing all available renditions and their bandwidths
- Player downloads 2-second segments via standard HTTP GET
- Player monitors download speed: if downloading 720p takes longer than 2 seconds → switch to 480p for the next segment
- Switching is seamless — each segment is independently decodable

**CDN segment caching**: Each 2-second HLS segment is a static file (e.g., `1080p/seg042.ts`). CDN caches segments at edge nodes. A viewer seeking to 5:24 in a popular video → segment `seg162.ts` is likely already cached at the nearest CDN edge → <10ms latency.

**Bandwidth estimation**: ABR player uses EWMA (Exponentially Weighted Moving Average) of recent download speeds to predict available bandwidth. `estimated_bw = 0.8 × prev_bw + 0.2 × current_bw`. Selects the highest rendition where `rendition_bitrate ≤ 0.8 × estimated_bw` (safety margin).

**Pre-buffering**: Player downloads 30 seconds of video ahead of the playback position. Large buffer = tolerates 30 seconds of network disruption without stutter. Trade-off: uses more mobile data.

---

## Deep Dive 3: View Count and Engagement at Scale

**Problem**: A viral video gets 1M simultaneous viewers. Each view event updating a single `view_count` column in PostgreSQL would require 1M writes/second — far exceeding DB capacity.

**Buffered counting**:
1. View events streamed to Kafka `view-events` topic (no DB write on each view)
2. Flink aggregation job: count views per video per 5-second window
3. Batch update: every 5 seconds, one UPDATE per video: `SET view_count = view_count + 50000`

This reduces write rate from 1M/sec to 1 update per video per 5 seconds.

**Redis counter** (simpler approach): `INCR view_count:{video_id}` on each view. Redis handles 1M INCR/sec easily. A background job periodically reads Redis and bulk-updates PostgreSQL. Clear Redis key after sync.

**Like/dislike counts**: Same buffered approach. Like event → Kafka → aggregator → batch update. For real-time display, show an approximate count from Redis.

**Comment system**: Comments stored in Cassandra (append-only, high-write). `partition_key = video_id`, `clustering_key = created_at DESC`. Top-level comments + replies (nested). Likes on comments: same buffered pattern.

---

## Interviewer Questions by Level

**Junior**:
- What is adaptive bitrate streaming? Why is it important?
- Why is video stored in S3 rather than a database?
- What happens between "user uploads video" and "video is available to watch"?

**Mid-level**:
- How does the HLS manifest enable adaptive bitrate switching?
- How do you scale the transcoding pipeline to handle 500 hours uploaded per minute?
- How does CDN caching work for video segments? What's the cache hit rate for a popular video?

**Senior**:
- Design the transcoding pipeline — how do you transcode a 1-hour 4K video in under 2 minutes?
- How do you count views for a video getting 1M concurrent viewers without overwhelming the database?
- How would you design YouTube's recommendation system — what signals do you use and how do you serve recommendations at 500M DAU scale?
- How do you handle copyright detection (Content ID) at upload time — scanning 500 hours/minute for matching content?

---

## Back-of-Envelope Estimation

**Scale inputs (given in NFRs):**
- 500M DAU; 500 hours of video uploaded/minute; 1B hours watched/day; 23 Tbps global serving bandwidth

**Upload throughput:**
- 500 hours/min of video × 60 min = 30,000 hours/hour uploaded
- At 50 GB/hour (1080p raw): 30,000 × 50 GB = **1.5 PB/hour** raw upload volume
- Compressed source (H.264, ~5 GB/hour for upload): 30,000 × 5 GB = **~150 TB/hour** = **~42 GB/sec** inbound

**Transcoding compute:**
- Each uploaded video must be transcoded to multiple renditions (360p, 480p, 720p, 1080p, 4K)
- Transcoding 1 hour of video to 5 renditions: ~5× real-time → 5 hours of CPU per uploaded hour
- 30,000 hours uploaded/hour × 5 renditions = **150,000 hours** of transcoding needed per hour
- At 10× real-time speed on a modern CPU: 150,000 ÷ 10 = **15,000 CPU-hours/hour** = 15,000 cores running continuously

**Storage after transcoding:**
- Each uploaded hour → 5 renditions at avg 2 GB/rendition = 10 GB per uploaded hour
- 30,000 hours/hour × 10 GB = **300 TB/hour** new video storage
- Annual: 300 TB × 8,760 hours = **~2.6 PB/day → ~950 PB/year** (with popular videos stored longer)

**Streaming bandwidth:**
- 1B hours watched/day at average 2 Mbps bitrate: 1B × 3,600 sec × 2 Mbps = **7.2 × 10¹⁵ bits/day**
- Per second: 7.2 × 10¹⁵ ÷ 86,400 = **~83 Tbps** average; NFR says peak is **23 Tbps** (either the NFR is a peak-per-region or uses different assumptions — use 23 Tbps as the constraint)
- At 23 Tbps across 200 CDN PoPs: **~115 Gbps per PoP** average; peak 3× = **~345 Gbps per PoP** — needs multiple 100 GbE links per PoP

**CDN cache hit rate for video:**
- Popular videos (top 1% viewed): accounts for ~50% of watch-hours
- CDN caches top 1% videos: 950 PB/year × 1% = **~9.5 PB** of popular video across CDN edge
- At 10 TB SSD per PoP × 200 PoPs = 2 PB CDN edge storage → caches top 0.2% of videos, serves ~30% of requests
- For the remaining 70%, CDN fetches from origin mid-tier cache or S3

**Architecture decisions driven by these numbers:**
- **Async transcoding pipeline, not synchronous**: Transcoding 1 uploaded video takes minutes (5× duration at 1× CPU). The upload API can't block for 5 minutes before returning. Upload writes raw video to S3, publishes a `video-uploaded` Kafka event, and returns immediately (status: PROCESSING). A transcoding fleet of 15,000 cores consumes the queue asynchronously. Users see the video available for viewing minutes after upload completes.
- **Adaptive bitrate streaming (HLS/DASH) over fixed-bitrate**: A user on a 3G phone switching to WiFi should get seamless quality improvement. HLS segments video into 2-second chunks at each quality level. The player switches renditions per-segment based on download speed. This requires pre-transcoding to 5 renditions (the 15,000 CPU-core investment), but eliminates buffering and delivers optimal quality per client, reducing rebuffering rate from ~8% (fixed bitrate) to < 1%.
- **Tiered CDN with regional mid-tier caches**: At 23 Tbps peak, serving all video from a central origin cluster is impossible — a single S3 region maxes out at a few Tbps. Tiered CDN: edge PoPs (close to users) cache hot segments; regional mid-tier caches (one per continent) hold long-tail content; origin (S3) holds everything. Cache hit rates: edge ~60%, mid-tier ~90% of edge misses, origin serves only ~4% of total requests — reducing origin bandwidth from 23 Tbps to ~920 Gbps.

---

## Related

**Concepts used in this design**

- [CDN](../../02-building-blocks/01-networking/05-cdn.md)
- [Message Brokers](../../02-building-blocks/04-coordination/01-message-brokers.md)
- [Storage Fundamentals](../../01-foundations/02-hardware-and-networking/01-storage-fundamentals.md)
- [Sharding](../../02-building-blocks/03-data-partitioning/01-sharding.md)
- [Stream Processing](../../04-advanced-topics/01-distributed-architecture/05-stream-processing.md)

**Practice next**

- [Instagram](../02-medium/instagram.md)
- [CDN Design](../03-hard/cdn-design.md)

CDN design is the delivery layer this problem assumes.

**Frameworks**: [HLD Template](../../07-interview-templates/01-frameworks/01-hld-template.md) · [Capacity Estimation](../../07-interview-templates/02-cheat-sheets/02-capacity-estimation.md) · [Trade-offs Cheat Sheet](../../07-interview-templates/02-cheat-sheets/01-trade-offs-cheat-sheet.md)
