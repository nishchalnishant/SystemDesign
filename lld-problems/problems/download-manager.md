# Design Internet Download Manager (IDM)

> **Difficulty**: Intermediate  
> **Topics**: Concurrency, Multi-threading, HTTP Range Headers, File I/O  
> **Extension**: Resumable Downloads

## Problem Statement

Design a download manager that can:
1.  Download files from a URL.
2.  Accelerate download by splitting file into multiple parts (segments) and downloading in parallel.
3.  Pause and resume downloads.
4.  Merge segments into final file.
5.  Handle network failures.

## Core Concept: HTTP Range Headers

The magic of IDM lies in the HTTP protocol.

1.  **Head Request**: Send `HEAD` request to check file size (`Content-Length`) and if server supports parallel downloads (`Accept-Ranges: bytes`).
2.  **Range Request**: Workers send `GET` requests with specific byte ranges.
    *   _Worker 1:_ `Range: bytes=0-1000`
    *   _Worker 2:_ `Range: bytes=1001-2000`

## System Entities

1.  **DownloadTask**: Represents the file being downloaded. Holds metadata (URL, file size, status).
2.  **Segment (Chunk)**: Represents a specific byte range (Start, End) and its download status.
3.  **Worker (Thread)**: A separate thread responsible for downloading one Segment.
4.  **DownloadManager**: The Controller. Starts tasks, manages thread pool, and handles file assembly.

## Python Implementation

### Segment and Task

```python
import os
import requests
import threading
from enum import Enum

class DownloadStatus(Enum):
    PENDING = 1
    DOWNLOADING = 2
    PAUSED = 3
    COMPLETED = 4
    FAILED = 5

class Segment:
    def __init__(self, id, start, end, temp_path):
        self.id = id
        self.start = start
        self.end = end
        self.downloaded = 0
        self.is_completed = False
        self.temp_path = temp_path

class DownloadTask:
    def __init__(self, url, output_path, num_threads=4):
        self.url = url
        self.output_path = output_path
        self.num_threads = num_threads
        self.segments = []
        self.status = DownloadStatus.PENDING
        self.file_size = 0
        self.lock = threading.Lock() # For safe status updates

    def initialize(self):
        # 1. Head Request to get Size
        response = requests.head(self.url)
        self.file_size = int(response.headers.get('content-length', 0))
        accept_ranges = response.headers.get('accept-ranges', 'none')

        if self.file_size == 0:
            raise Exception("Invalid File Size")

        # 2. Calculate Segment Sizes
        print(f"File Size: {self.file_size} bytes. Ranges Supported: {accept_ranges}")
        
        # If server doesn't support ranges, use 1 segment
        if accept_ranges == 'none':
            self.segments.append(Segment(0, 0, self.file_size, f"{self.output_path}.part0"))
        else:
            chunk_size = self.file_size // self.num_threads
            for i in range(self.num_threads):
                start = i * chunk_size
                # Last chunk takes the remainder
                end = self.file_size if i == self.num_threads - 1 else (start + chunk_size)
                temp_path = f"{self.output_path}.part{i}"
                self.segments.append(Segment(i, start, end, temp_path))

    def merge_files(self):
        print("Merging segments...")
        with open(self.output_path, 'wb') as final_file:
            for segment in self.segments:
                with open(segment.temp_path, 'rb') as part_file:
                    final_file.write(part_file.read())
                # Cleanup temp file
                os.remove(segment.temp_path)
        self.status = DownloadStatus.COMPLETED
        print("Download Complete!")
```

### Worker (Thread)

```python
class ChunkDownloader(threading.Thread):
    def __init__(self, url, segment):
        super().__init__()
        self.url = url
        self.segment = segment

    def run(self):
        if self.segment.is_completed:
            return

        # Core Logic: HTTP Range Header
        # We allow resuming by adding 'downloaded' offset to 'start'
        current_start = self.segment.start + self.segment.downloaded
        headers = {'Range': f'bytes={current_start}-{self.segment.end}'}
        
        print(f"Thread-{self.segment.id} requesting bytes {current_start}-{self.segment.end}")
        
        try:
            with requests.get(self.url, headers=headers, stream=True) as r:
                mode = 'ab' if self.segment.downloaded > 0 else 'wb'
                with open(self.segment.temp_path, mode) as f:
                    for chunk in r.iter_content(chunk_size=1024):
                        if chunk:
                            f.write(chunk)
                            self.segment.downloaded += len(chunk)
            
            self.segment.is_completed = True
            print(f"Thread-{self.segment.id} finished.")
            
        except Exception as e:
            print(f"Thread-{self.segment.id} failed: {e}")
```

### Manager (Orchestrator)

```python
class DownloadManager:
    def __init__(self):
        self.tasks = []

    def start_download(self, url, output_file):
        task = DownloadTask(url, output_file)
        self.tasks.append(task)
        
        try:
            task.initialize()
            task.status = DownloadStatus.DOWNLOADING
            
            # Create threads
            workers = []
            for segment in task.segments:
                worker = ChunkDownloader(task.url, segment)
                workers.append(worker)
                worker.start()

            # Wait for all to finish (In real app, this would be async)
            for worker in workers:
                worker.join()

            # Check if all successful
            if all(s.is_completed for s in task.segments):
                task.merge_files()
            else:
                task.status = DownloadStatus.FAILED
                print("Download failed.")

        except Exception as e:
            print(f"Error: {e}")
```

## Key Design Decisions

1.  **Handling Resume (Persistence)**:
    *   Serialize `DownloadTask` (with segments and downloaded bytes) to a file (e.g., JSON) on disk.
    *   On startup, read file, reconstruct objects, and calculate new Range header: `bytes=(start + downloaded)-end`.

2.  **File I/O Optimization**:
    *   Use separate temporary files (`.part0`, `.part1`) to avoid locking contention on a single file. Merging at the end is faster than mutex locking on every write.

3.  **Concurrency Model**:
    *   Thread per Segment is simple but strictly bound (e.g., max 8 threads) to avoid context switching overhead.

4.  **Network Failure**:
    *   If a chunk fails, only that segment is marked `FAILED`. Retry logic spins up a new thread for just that segment.
