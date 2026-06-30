> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design a Download Manager — focuses on network protocols (HTTP Range requests), threading, and merging files.
>
> **Key concepts:**
> - Core Entities: `DownloadTask`, `ChunkDownloader`, `FileMerger`, `ConnectionManager`.
> - HTTP Range Requests: The secret sauce. You send `Range: bytes=0-1023` in the HTTP header to download just a specific chunk of a file.
> - Thread Pool: The `DownloadTask` determines the file size, divides it into $N$ chunks, and submits $N$ `ChunkDownloader` runnables to an `ExecutorService`.
> - Merging: As chunks finish, they write to temporary files. Once all complete, the `FileMerger` combines them into the final file.
> - Resuming: If paused, the system saves the state of which chunks are complete. On resume, it only requests the incomplete byte ranges.
>
> **Key takeaway:** Explain how to use `java.util.concurrent.ExecutorService` and `CountDownLatch` (to wait for all chunks to finish before merging). The interviewer is testing your multithreading and network knowledge.

---
module: 06-lld
topic: Problems
status: unread
tags: [06-lld, lld, download-manager, threading, range-requests, chunk-merging]
---
# Design Download Manager

> **Difficulty**: Hard
> **Asked at**: Amazon, BitTorrent, Mozilla
> **Key Patterns**: Thread pool (parallel chunks), State Machine (download lifecycle), Strategy (retry)

---

## Understanding the Problem

Design a download manager that downloads large files in parallel chunks using HTTP Range requests, tracks progress, supports pause/resume, and merges chunks into a final file.

---

## Clarifying Questions

**You**: "Do we support parallel chunk downloads for a single file?"
**Interviewer**: "Yes — split the file into N chunks, download in parallel."

**You**: "What's the concurrency model — threads or async?"
**Interviewer**: "Threads are fine for this design."

**You**: "Do we need pause/resume?"
**Interviewer**: "Yes — pause stops in-progress chunks; resume restarts incomplete ones."

**You**: "How do we know the file size upfront?"
**Interviewer**: "HTTP HEAD request for Content-Length header before starting."

**You**: "What if a server doesn't support Range requests?"
**Interviewer**: "Fall back to single-stream download."

---

## Final Requirements

**In scope:**
1. Download a file in N parallel chunks using HTTP Range requests
2. Track download progress per chunk and overall
3. Pause / resume downloads
4. Retry failed chunks (up to 3 times with backoff)
5. Merge chunks in order after all complete

**Out of scope:**
- BitTorrent protocol
- Download queue prioritization
- Bandwidth throttling (follow-up)

---

## Core Entities and Relationships

| Entity | Responsibility |
|--------|---------------|
| `DownloadManager` | Creates and tracks downloads; manages thread pool |
| `Download` | Represents one file download; owns chunk list and state |
| `Chunk` | A byte range of the file; downloaded by one thread |
| `ChunkDownloader` | Runnable: fetches one chunk's byte range, writes to temp file |
| `DownloadState` | Enum: PENDING, DOWNLOADING, PAUSED, COMPLETED, FAILED |
| `ChunkState` | Enum: PENDING, IN_PROGRESS, COMPLETED, FAILED |
| `FileMerger` | Assembles chunk temp files into final output file |

---

## Class Design

### Chunk

```
class Chunk:
- chunk_id: int
- start_byte: int
- end_byte: int
- temp_file_path: str
- state: ChunkState
- bytes_downloaded: int
- retry_count: int
```

### Download

```
class Download:
- download_id: str
- url: str
- output_path: str
- file_size: Optional[int]
- chunks: list[Chunk]
- state: DownloadState
- created_at: datetime

+ progress() -> float    # 0.0 – 1.0
+ is_complete() -> bool
```

### DownloadManager

```
class DownloadManager:
- downloads: dict[str, Download]
- thread_pool: ThreadPoolExecutor
- http_client: HttpClient
- max_workers: int = 8

+ start_download(url, output_path, num_chunks=4) -> str   # download_id
+ pause_download(download_id)
+ resume_download(download_id)
+ cancel_download(download_id)
+ get_progress(download_id) -> float
```

---

## Implementation

### Core Method: `start_download`

**Core logic:**
1. HTTP HEAD → get Content-Length and Accept-Ranges
2. If no Range support → single chunk covering full file
3. Split file into N equal byte ranges → create Chunk objects
4. Create Download, register it
5. Submit ChunkDownloader tasks to thread pool

**Edge cases:**
- Server doesn't support Range → single chunk, no parallelism
- File size unknown → single-stream, progress indeterminate
- num_chunks > file_size → clamp to 1 chunk per byte

```python
def start_download(self, url, output_path, num_chunks=4):
    file_size, supports_range = self.http_client.head(url)

    download_id = str(uuid.uuid4())
    if not supports_range or not file_size:
        chunks = [Chunk(
            chunk_id=0,
            start_byte=0,
            end_byte=file_size - 1 if file_size else 0,
            temp_file_path=f"/tmp/{download_id}_0.part",
            state=ChunkState.PENDING,
            bytes_downloaded=0,
            retry_count=0
        )]
        num_chunks = 1
    else:
        chunks = self._split_into_chunks(download_id, file_size, num_chunks)

    download = Download(
        download_id=download_id,
        url=url,
        output_path=output_path,
        file_size=file_size,
        chunks=chunks,
        state=DownloadState.DOWNLOADING,
        created_at=datetime.utcnow()
    )
    self.downloads[download_id] = download

    for chunk in chunks:
        self.thread_pool.submit(self._download_chunk, download, chunk)

    return download_id

def _split_into_chunks(self, download_id, file_size, num_chunks):
    chunk_size = file_size // num_chunks
    chunks = []
    for i in range(num_chunks):
        start = i * chunk_size
        end = (i + 1) * chunk_size - 1 if i < num_chunks - 1 else file_size - 1
        chunks.append(Chunk(
            chunk_id=i,
            start_byte=start,
            end_byte=end,
            temp_file_path=f"/tmp/{download_id}_{i}.part",
            state=ChunkState.PENDING,
            bytes_downloaded=0,
            retry_count=0
        ))
    return chunks
```

### Core Method: `_download_chunk`

**Core logic:**
- Poll `download.state` before and during download
- HTTP GET with `Range: bytes=start-end`
- Write response bytes to temp file, update `bytes_downloaded`
- On failure: retry up to 3 times with exponential backoff
- On all chunks complete: trigger FileMerger

```python
def _download_chunk(self, download, chunk):
    chunk.state = ChunkState.IN_PROGRESS

    for attempt in range(3):
        if download.state in (DownloadState.PAUSED,
                               DownloadState.CANCELLED,
                               DownloadState.FAILED):
            chunk.state = ChunkState.PENDING
            return
        try:
            headers = {'Range': f'bytes={chunk.start_byte}-{chunk.end_byte}'}
            response = self.http_client.get(download.url, headers=headers, stream=True)

            with open(chunk.temp_file_path, 'wb') as f:
                for data in response.iter_content(chunk_size=8192):
                    if download.state == DownloadState.PAUSED:
                        chunk.state = ChunkState.PENDING
                        return
                    f.write(data)
                    chunk.bytes_downloaded += len(data)

            chunk.state = ChunkState.COMPLETED
            self._check_and_merge(download)
            return

        except Exception:
            chunk.retry_count += 1
            if attempt < 2:
                time.sleep(2 ** attempt)

    chunk.state = ChunkState.FAILED
    download.state = DownloadState.FAILED

def _check_and_merge(self, download):
    if all(c.state == ChunkState.COMPLETED for c in download.chunks):
        FileMerger().merge(download)
```

### FileMerger

```python
class FileMerger:
    def merge(self, download):
        with open(download.output_path, 'wb') as out:
            for chunk in sorted(download.chunks, key=lambda c: c.chunk_id):
                with open(chunk.temp_file_path, 'rb') as part:
                    while True:
                        buf = part.read(65536)
                        if not buf:
                            break
                        out.write(buf)
                os.remove(chunk.temp_file_path)
        download.state = DownloadState.COMPLETED
```

### Pause / Resume

```python
def pause_download(self, download_id):
    download = self.downloads[download_id]
    if download.state == DownloadState.DOWNLOADING:
        download.state = DownloadState.PAUSED
        # In-flight threads poll download.state and exit cooperatively

def resume_download(self, download_id):
    download = self.downloads[download_id]
    if download.state == DownloadState.PAUSED:
        download.state = DownloadState.DOWNLOADING
        for chunk in download.chunks:
            if chunk.state == ChunkState.PENDING:
                self.thread_pool.submit(self._download_chunk, download, chunk)

def get_progress(self, download_id):
    download = self.downloads[download_id]
    if not download.file_size:
        return 0.0
    downloaded = sum(c.bytes_downloaded for c in download.chunks)
    return min(downloaded / download.file_size, 1.0)
```

---

## Verification

```
start_download("http://example.com/file.zip", "/out/file.zip", num_chunks=3)
  HEAD: file_size=300, supports_range=True
  Chunks:
    C0: bytes=0-99
    C1: bytes=100-199
    C2: bytes=200-299
  Download D1, state=DOWNLOADING
  3 threads submitted

C0: download 100 bytes → COMPLETED
C1: download 100 bytes → COMPLETED
C2: attempt 1 fails → sleep(1s) → attempt 2 succeeds → COMPLETED

_check_and_merge: all 3 COMPLETED
FileMerger:
  open /out/file.zip
  copy C0.part (100B) → out
  copy C1.part (100B) → out
  copy C2.part (100B) → out
  delete temp files
  D1.state = COMPLETED

get_progress("D1") → 1.0
```

---

## Deep Dive & Extensibility

### 1. "How does pause work when a chunk is mid-download?"

In-flight threads poll `download.state` between buffer writes (inside `iter_content` loop). On seeing PAUSED, they stop writing, exit cleanly, and mark the chunk PENDING. Partial bytes are in the temp file but the chunk restarts from scratch on resume (simpler). For true partial resume, track bytes downloaded and send `Range: bytes=(start + bytes_downloaded)-(end)`.

### 2. "How would you add bandwidth throttling?"

Use a token bucket per download:

```python
class TokenBucket:
    def __init__(self, rate_bps):
        self.rate = rate_bps / 8        # bytes per second
        self.tokens = self.rate
        self.last_refill = time.time()

    def consume(self, n_bytes):
        self._refill()
        if self.tokens >= n_bytes:
            self.tokens -= n_bytes
        else:
            sleep_time = (n_bytes - self.tokens) / self.rate
            time.sleep(sleep_time)
            self.tokens = 0

    def _refill(self):
        now = time.time()
        self.tokens = min(self.rate, self.tokens + self.rate * (now - self.last_refill))
        self.last_refill = now
```

Each chunk thread calls `bucket.consume(len(data))` before writing.

### 3. "How do you verify file integrity after download?"

Request checksum from server (MD5/SHA256 header or sidecar `.sha256` file). After merging:

```python
def verify_integrity(self, output_path, expected_checksum):
    sha256 = hashlib.sha256()
    with open(output_path, 'rb') as f:
        for block in iter(lambda: f.read(65536), b''):
            sha256.update(block)
    actual = sha256.hexdigest()
    if actual != expected_checksum:
        os.remove(output_path)
        raise IntegrityError(f"Checksum mismatch: got {actual}, expected {expected_checksum}")
```

### 4. "How would you handle a download queue with priority?"

Add a `priority: int` field to each download request. Use a `PriorityQueue` instead of directly submitting to the thread pool. A dispatcher thread pops from the queue and submits to the thread pool — higher priority downloads get thread pool slots first.

---

## Interviewer Questions by Level

**Junior**: Single-threaded download. Track progress (bytes / total). Save to file. Basic error handling.

**Mid-level**: Split into N chunks. Thread pool for parallel downloads. Merge in chunk_id order. Retry with backoff. Pause/resume via cooperative state flag.

**Senior**: Partial resume (restart from partial byte offset). Bandwidth throttling via token bucket. File integrity verification via checksum. Priority queue for download scheduling. Thread-safe progress tracking.

---

## Common Interview Questions

- **Q**: How do you split a file for parallel download?
  **A**: HTTP HEAD gives Content-Length. Divide by N: chunk i covers `[i * size, (i+1)*size - 1]`. Last chunk covers the remainder. Each thread downloads its range with a `Range: bytes=start-end` header.

- **Q**: What happens if a chunk fails mid-download?
  **A**: Retry up to 3 times with exponential backoff (2^attempt seconds). If all retries fail, mark chunk FAILED and download FAILED. On resume, restart only PENDING/FAILED chunks.

- **Q**: How do you merge chunks correctly?
  **A**: Sort chunks by chunk_id (ascending) and concatenate their temp files into the output file. chunk_id maps directly to byte order since chunks are created left-to-right.

- **Q**: How does cooperative pause work?
  **A**: Threads poll `download.state` between buffer writes inside the streaming loop. On seeing PAUSED, they return cleanly. Forced `Thread.stop()` is unsafe (can leave locks held). Cooperative cancellation via a shared flag is safe.

- **Q**: How do you track overall progress?
  **A**: Sum `bytes_downloaded` across all chunks, divide by `file_size`. Each chunk's counter is incremented as data arrives. For thread safety, use an atomic counter or a lock around the increment.
