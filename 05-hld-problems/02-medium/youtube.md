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
