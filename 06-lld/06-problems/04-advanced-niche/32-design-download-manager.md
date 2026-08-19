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

```java
public String startDownload(String url, String outputPath, int numChunks) {
    HeadResult head = httpClient.head(url);
    Long fileSize = head.fileSize;
    boolean supportsRange = head.supportsRange;

    String downloadId = UUID.randomUUID().toString();
    List<Chunk> chunks;
    if (!supportsRange || fileSize == null) {
        Chunk single = new Chunk(
            0,
            0,
            fileSize != null ? fileSize - 1 : 0,
            String.format("/tmp/%s_0.part", downloadId),
            ChunkState.PENDING,
            0,
            0
        );
        chunks = List.of(single);
        numChunks = 1;
    } else {
        chunks = splitIntoChunks(downloadId, fileSize, numChunks);
    }

    Download download = new Download(
        downloadId, url, outputPath, fileSize, chunks,
        DownloadState.DOWNLOADING, Instant.now()
    );
    downloads.put(downloadId, download);

    for (Chunk chunk : chunks) {
        threadPool.submit(() -> downloadChunk(download, chunk));
    }

    return downloadId;
}

private List<Chunk> splitIntoChunks(String downloadId, long fileSize, int numChunks) {
    long chunkSize = fileSize / numChunks;
    List<Chunk> chunks = new ArrayList<>();
    for (int i = 0; i < numChunks; i++) {
        long start = i * chunkSize;
        long end = (i < numChunks - 1) ? (i + 1) * chunkSize - 1 : fileSize - 1;
        chunks.add(new Chunk(
            i, start, end,
            String.format("/tmp/%s_%d.part", downloadId, i),
            ChunkState.PENDING, 0, 0
        ));
    }
    return chunks;
}
```

### Core Method: `_download_chunk`

**Core logic:**
- Poll `download.state` before and during download
- HTTP GET with `Range: bytes=start-end`
- Write response bytes to temp file, update `bytes_downloaded`
- On failure: retry up to 3 times with exponential backoff
- On all chunks complete: trigger FileMerger

```java
private void downloadChunk(Download download, Chunk chunk) {
    chunk.setState(ChunkState.IN_PROGRESS);

    for (int attempt = 0; attempt < 3; attempt++) {
        DownloadState state = download.getState();
        if (state == DownloadState.PAUSED
                || state == DownloadState.CANCELLED
                || state == DownloadState.FAILED) {
            chunk.setState(ChunkState.PENDING);
            return;
        }
        try {
            Map<String, String> headers = Map.of(
                "Range", String.format("bytes=%d-%d", chunk.getStartByte(), chunk.getEndByte())
            );
            HttpResponse response = httpClient.get(download.getUrl(), headers, /* stream= */ true);

            try (OutputStream out = new FileOutputStream(chunk.getTempFilePath())) {
                for (byte[] data : response.iterContent(8192)) {
                    if (download.getState() == DownloadState.PAUSED) {
                        chunk.setState(ChunkState.PENDING);
                        return;
                    }
                    out.write(data);
                    chunk.addBytesDownloaded(data.length);
                }
            }

            chunk.setState(ChunkState.COMPLETED);
            checkAndMerge(download);
            return;

        } catch (IOException e) {
            chunk.incrementRetryCount();
            if (attempt < 2) {
                try {
                    Thread.sleep((long) Math.pow(2, attempt) * 1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    chunk.setState(ChunkState.FAILED);
    download.setState(DownloadState.FAILED);
}

private void checkAndMerge(Download download) {
    boolean allComplete = download.getChunks().stream()
        .allMatch(c -> c.getState() == ChunkState.COMPLETED);
    if (allComplete) {
        new FileMerger().merge(download);
    }
}
```

Note on `download.state`, `chunk.bytes_downloaded`, etc.: because multiple chunk threads read/write these fields concurrently, the Java port backs them with `volatile` fields (state flags) and `AtomicLong` (`bytesDownloaded`, `retryCount`) rather than plain `int`/enum fields — Python's GIL made the equivalent read-modify-write on plain attributes safe by accident (each bytecode-level attribute access is atomic under the GIL), but the JVM has no such guarantee, so unsynchronized access here would be a real data race.

### FileMerger

```java
public class FileMerger {
    public void merge(Download download) {
        List<Chunk> sortedChunks = download.getChunks().stream()
            .sorted(Comparator.comparingInt(Chunk::getChunkId))
            .collect(Collectors.toList());

        try (OutputStream out = new FileOutputStream(download.getOutputPath())) {
            byte[] buf = new byte[65536];
            for (Chunk chunk : sortedChunks) {
                try (InputStream part = new FileInputStream(chunk.getTempFilePath())) {
                    int n;
                    while ((n = part.read(buf)) != -1) {
                        out.write(buf, 0, n);
                    }
                }
                new File(chunk.getTempFilePath()).delete();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        download.setState(DownloadState.COMPLETED);
    }
}
```

### Pause / Resume

```java
public void pauseDownload(String downloadId) {
    Download download = downloads.get(downloadId);
    if (download.getState() == DownloadState.DOWNLOADING) {
        download.setState(DownloadState.PAUSED);
        // In-flight threads poll download.getState() and exit cooperatively
    }
}

public void resumeDownload(String downloadId) {
    Download download = downloads.get(downloadId);
    if (download.getState() == DownloadState.PAUSED) {
        download.setState(DownloadState.DOWNLOADING);
        for (Chunk chunk : download.getChunks()) {
            if (chunk.getState() == ChunkState.PENDING) {
                threadPool.submit(() -> downloadChunk(download, chunk));
            }
        }
    }
}

public double getProgress(String downloadId) {
    Download download = downloads.get(downloadId);
    if (download.getFileSize() == null) {
        return 0.0;
    }
    long downloaded = download.getChunks().stream()
        .mapToLong(Chunk::getBytesDownloaded)
        .sum();
    return Math.min((double) downloaded / download.getFileSize(), 1.0);
}
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

```java
public class TokenBucket {
    private final double rate;              // bytes per second
    private double tokens;
    private long lastRefillNanos;
    private final Object lock = new Object();

    public TokenBucket(double rateBps) {
        this.rate = rateBps / 8;
        this.tokens = this.rate;
        this.lastRefillNanos = System.nanoTime();
    }

    public void consume(int nBytes) {
        synchronized (lock) {
            refill();
            if (tokens >= nBytes) {
                tokens -= nBytes;
                return;
            }
            double sleepSeconds = (nBytes - tokens) / rate;
            tokens = 0;
            try {
                Thread.sleep((long) (sleepSeconds * 1000));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void refill() {
        long now = System.nanoTime();
        double elapsedSeconds = (now - lastRefillNanos) / 1_000_000_000.0;
        tokens = Math.min(rate, tokens + rate * elapsedSeconds);
        lastRefillNanos = now;
    }
}
```

Each chunk thread calls `bucket.consume(len(data))` before writing.

### 3. "How do you verify file integrity after download?"

Request checksum from server (MD5/SHA256 header or sidecar `.sha256` file). After merging:

```java
public void verifyIntegrity(String outputPath, String expectedChecksum) throws IOException {
    MessageDigest sha256;
    try {
        sha256 = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
        throw new IllegalStateException(e);
    }

    byte[] buf = new byte[65536];
    try (InputStream f = new FileInputStream(outputPath)) {
        int n;
        while ((n = f.read(buf)) != -1) {
            sha256.update(buf, 0, n);
        }
    }

    StringBuilder hex = new StringBuilder();
    for (byte b : sha256.digest()) {
        hex.append(String.format("%02x", b));
    }
    String actual = hex.toString();

    if (!actual.equals(expectedChecksum)) {
        new File(outputPath).delete();
        throw new IntegrityException(
            String.format("Checksum mismatch: got %s, expected %s", actual, expectedChecksum)
        );
    }
}
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

---

## Related

**Patterns applied here**

- [Command Pattern](../../03-design-patterns/03-behavioral/command-pattern.md)
- [Observer Pattern](../../03-design-patterns/03-behavioral/observer-pattern.md)
- [State Pattern](../../03-design-patterns/03-behavioral/state-pattern.md)

**SOLID focus**: [Single Responsibility](../../02-solid-principles/01-single-responsibility.md) · [Dependency Inversion](../../02-solid-principles/05-dependency-inversion.md)

**Concurrency**: [Producer-Consumer](../../04-concurrency/producer-consumer.md) · [Futures & Async Patterns](../../04-concurrency/futures-async-patterns.md) · [Concurrency Patterns](../../04-concurrency/concurrency-patterns.md)

**Practice next**

- [Design Tunneling Service](30-design-tunneling-service.md)
- [Design Elevator System](../02-frequent-problems/09-design-elevator-system.md)

Both schedule concurrent work against limited capacity.

**Frameworks**: [LLD Template](../../../07-interview-templates/01-frameworks/02-lld-template.md) · [UML Diagrams](../../01-oop-fundamentals/uml-diagrams.md) · [OOP Four Pillars](../../01-oop-fundamentals/four-pillars.md)
