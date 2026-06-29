---
module: 06-lld
topic: Problems
status: unread
tags: [06-lld, lld, logging, chain-of-responsibility, singleton, strategy]
---
# Design a Logger Library

> **Difficulty**: Medium  
> **Asked at**: Amazon, Google  
> **Key Patterns**: Chain of Responsibility (log level filtering), Strategy (sink/appender), Singleton (logger instance)

---

## Understanding the Problem

Design a logging library that allows applications to emit log messages at different severity levels (DEBUG, INFO, WARN, ERROR) and route them to multiple configurable output sinks such as console, file, and remote endpoints.

---

## Clarifying Questions

**You**: "Should there be a single global logger or can applications create multiple named loggers?"  
**Interviewer**: "One global Singleton logger is fine for this problem."

**You**: "Should handlers filter by log level independently, or does the logger have one threshold for all handlers?"  
**Interviewer**: "Each handler can have its own minimum level, and the logger also has a global minimum."

**You**: "For file logging — do we need log rotation?"  
**Interviewer**: "Yes, rotation by size or date would be a good deep dive."

**You**: "Should log calls be thread-safe?"  
**Interviewer**: "Yes, assume concurrent callers."

**You**: "Does the remote handler need retries on failure?"  
**Interviewer**: "Treat it as a stretch goal — start with best-effort delivery."

**You**: "Should we support custom log formats?"  
**Interviewer**: "Yes, a pluggable Formatter per handler."

---

## Final Requirements

**In scope:**
1. Log messages at four severity levels: DEBUG, INFO, WARN, ERROR
2. Global minimum log level threshold on the Logger
3. Multiple handlers (Console, File, Remote) each with their own level filter
4. Pluggable Formatter per handler (text, JSON)
5. Thread-safe logging
6. Log rotation for FileHandler (size-based)

**Out of scope:**
- Distributed log aggregation
- Log sampling / rate limiting
- Authentication for remote endpoints

---

## Core Entities and Relationships

| Entity | Responsibility |
|--------|----------------|
| Logger | Singleton entry point; holds global level + handler list |
| LogLevel | Enum ordering severity (DEBUG < INFO < WARN < ERROR) |
| LogRecord | Immutable snapshot of one log event (level, message, timestamp, caller) |
| LogHandler | Abstract base; filters by level, delegates to Formatter, writes output |
| ConsoleHandler | Writes formatted record to stdout |
| FileHandler | Writes to a file; rotates when size exceeds threshold |
| RemoteHandler | POSTs JSON payload to a remote endpoint |
| Formatter | Abstract base — formats a LogRecord to string |
| TextFormatter | Human-readable `[LEVEL] timestamp — message` |
| JSONFormatter | Structured JSON output |

---

## Class Design

### LogLevel

| Requirement | What LogLevel must track |
|-------------|--------------------------|
| Ordered severity | Integer value so levels compare with `>=` |

```
enum LogLevel:
- DEBUG = 10
- INFO  = 20
- WARN  = 30
- ERROR = 40
```

### LogRecord

| Requirement | What LogRecord must track |
|-------------|---------------------------|
| Event snapshot | level, message, timestamp, logger_name, thread_id |

```
class LogRecord:
- level: LogLevel
- message: str
- timestamp: datetime
- logger_name: str
- thread_id: int
```

### Formatter

```
abstract class Formatter:
+ format(record: LogRecord) -> str
```

### LogHandler

| Requirement | What LogHandler must track |
|-------------|----------------------------|
| Output routing | formatter, minimum level threshold |
| Filtering | emit only if record.level >= self.level |

```
abstract class LogHandler:
- level: LogLevel
- formatter: Formatter
+ set_level(level: LogLevel)
+ set_formatter(formatter: Formatter)
+ handle(record: LogRecord)        # checks level, calls emit()
+ emit(record: LogRecord)          # abstract — subclass writes output
```

### Logger

| Requirement | What Logger must track |
|-------------|------------------------|
| Singleton | one instance per process |
| Dispatch | global level filter + list of handlers |
| Thread safety | lock around handler dispatch |

```
class Logger:
- _instance: Logger         # class-level
- level: LogLevel
- handlers: list[LogHandler]
- _lock: threading.Lock
+ get_instance() -> Logger  # class method
+ set_level(level: LogLevel)
+ add_handler(handler: LogHandler)
+ log(level: LogLevel, message: str)
+ debug(message: str)
+ info(message: str)
+ warn(message: str)
+ error(message: str)
```

---

## Implementation

### Core Method: `log`

**Core logic:**
1. Check `level >= self.level`; if not, return early.
2. Build a `LogRecord` with current timestamp and thread id.
3. Acquire `_lock` and snapshot the handler list.
4. Iterate handlers; each handler's `handle()` applies its own level filter then formats and emits.

**Edge cases:**
- Handler list is empty — log call silently succeeds.
- Handler raises during emit — catch and print to stderr, never propagate to caller.
- Logger level set to ERROR — DEBUG/INFO/WARN records dropped before handler dispatch.

```python
import threading
from datetime import datetime
from enum import IntEnum
from abc import ABC, abstractmethod


class LogLevel(IntEnum):
    DEBUG = 10
    INFO  = 20
    WARN  = 30
    ERROR = 40


class LogRecord:
    def __init__(self, level: LogLevel, message: str, logger_name: str = "root"):
        self.level = level
        self.message = message
        self.timestamp = datetime.utcnow()
        self.logger_name = logger_name
        self.thread_id = threading.get_ident()


class Formatter(ABC):
    @abstractmethod
    def format(self, record: LogRecord) -> str:
        pass


class TextFormatter(Formatter):
    def format(self, record: LogRecord) -> str:
        ts = record.timestamp.strftime("%Y-%m-%d %H:%M:%S")
        return f"[{record.level.name}] {ts} ({record.thread_id}) — {record.message}"


class JSONFormatter(Formatter):
    def format(self, record: LogRecord) -> str:
        import json
        return json.dumps({
            "level": record.level.name,
            "timestamp": record.timestamp.isoformat(),
            "thread": record.thread_id,
            "message": record.message,
        })


class LogHandler(ABC):
    def __init__(self, level: LogLevel = LogLevel.DEBUG):
        self.level = level
        self.formatter: Formatter = TextFormatter()

    def set_level(self, level: LogLevel):
        self.level = level

    def set_formatter(self, formatter: Formatter):
        self.formatter = formatter

    def handle(self, record: LogRecord):
        if record.level >= self.level:
            try:
                self.emit(record)
            except Exception as e:
                import sys
                print(f"Handler error: {e}", file=sys.stderr)

    @abstractmethod
    def emit(self, record: LogRecord):
        pass


class ConsoleHandler(LogHandler):
    def emit(self, record: LogRecord):
        print(self.formatter.format(record))


class FileHandler(LogHandler):
    def __init__(self, filepath: str, max_bytes: int = 10 * 1024 * 1024,
                 level: LogLevel = LogLevel.DEBUG):
        super().__init__(level)
        self.filepath = filepath
        self.max_bytes = max_bytes
        self._lock = threading.Lock()

    def emit(self, record: LogRecord):
        line = self.formatter.format(record) + "\n"
        with self._lock:
            import os
            if os.path.exists(self.filepath) and os.path.getsize(self.filepath) >= self.max_bytes:
                self._rotate()
            with open(self.filepath, "a") as f:
                f.write(line)

    def _rotate(self):
        import os
        ts = datetime.utcnow().strftime("%Y%m%d%H%M%S")
        os.rename(self.filepath, f"{self.filepath}.{ts}")


class RemoteHandler(LogHandler):
    def __init__(self, endpoint: str, level: LogLevel = LogLevel.ERROR):
        super().__init__(level)
        self.endpoint = endpoint
        self.set_formatter(JSONFormatter())

    def emit(self, record: LogRecord):
        import urllib.request
        payload = self.formatter.format(record).encode()
        req = urllib.request.Request(self.endpoint, data=payload,
                                     headers={"Content-Type": "application/json"})
        urllib.request.urlopen(req, timeout=2)


class Logger:
    _instance = None
    _init_lock = threading.Lock()

    def __init__(self):
        self.level = LogLevel.DEBUG
        self.handlers: list[LogHandler] = []
        self._lock = threading.Lock()

    @classmethod
    def get_instance(cls) -> "Logger":
        if cls._instance is None:
            with cls._init_lock:
                if cls._instance is None:
                    cls._instance = Logger()
        return cls._instance

    def set_level(self, level: LogLevel):
        self.level = level

    def add_handler(self, handler: LogHandler):
        with self._lock:
            self.handlers.append(handler)

    def log(self, level: LogLevel, message: str):
        if level < self.level:
            return
        record = LogRecord(level, message)
        with self._lock:
            handlers_snapshot = list(self.handlers)
        for handler in handlers_snapshot:
            handler.handle(record)

    def debug(self, msg: str): self.log(LogLevel.DEBUG, msg)
    def info(self, msg: str):  self.log(LogLevel.INFO,  msg)
    def warn(self, msg: str):  self.log(LogLevel.WARN,  msg)
    def error(self, msg: str): self.log(LogLevel.ERROR, msg)
```

---

## Verification

Scenario: Logger at INFO level, ConsoleHandler at DEBUG, FileHandler at WARN.

1. `logger.debug("x")` — global level INFO > DEBUG → dropped at Logger gate, no handler invoked.
2. `logger.info("started")` — passes global filter; ConsoleHandler (level=DEBUG, DEBUG≤INFO) emits; FileHandler (level=WARN, WARN>INFO) drops.
3. `logger.error("crash")` — passes global filter; both handlers emit.

---

## Deep Dive & Extensibility

### 1. "Why Chain of Responsibility for handlers?"

Each handler independently decides whether to process a record. Adding a new handler requires zero changes to Logger or other handlers — open/closed principle. The alternative (a giant if-else in Logger) breaks when adding new output targets.

```python
# Adding a SlackHandler requires no Logger changes
class SlackHandler(LogHandler):
    def emit(self, record: LogRecord):
        import requests
        requests.post(SLACK_WEBHOOK, json={"text": self.formatter.format(record)}, timeout=3)

logger.add_handler(SlackHandler(level=LogLevel.ERROR))
```

### 2. "How do you make logging thread-safe?"

Two locks:
- `_init_lock` (class-level) guards Singleton creation with double-checked locking.
- `_lock` (instance-level) guards handler list mutation.

Key insight: snapshot the handler list before iterating so `add_handler` during iteration doesn't cause concurrent modification. FileHandler holds its own lock for file I/O.

### 3. "How would you implement async logging?"

Move the emit path off the caller's thread using a bounded queue and a background writer thread. Callers enqueue `LogRecord`s without blocking.

```python
import queue

class AsyncLogger(Logger):
    def __init__(self):
        super().__init__()
        self._queue: queue.Queue = queue.Queue(maxsize=10_000)
        self._worker = threading.Thread(target=self._drain, daemon=True)
        self._worker.start()

    def log(self, level: LogLevel, message: str):
        if level < self.level:
            return
        record = LogRecord(level, message)
        try:
            self._queue.put_nowait(record)
        except queue.Full:
            pass  # drop on backpressure; or block, or sample

    def _drain(self):
        while True:
            record = self._queue.get()
            for handler in self.handlers:
                handler.handle(record)
```

Trade-off: lower caller latency, but records in queue are lost on crash.

### 4. "How does log rotation work?"

Size-based: before each write, check `os.path.getsize`. If it exceeds `max_bytes`, rename the current file to `filename.YYYYMMDDHHMMSS` and open a fresh file. Prune older rotated files by keeping only the last N.

```python
def _rotate(self):
    import os, glob
    ts = datetime.utcnow().strftime("%Y%m%d%H%M%S")
    os.rename(self.filepath, f"{self.filepath}.{ts}")
    archives = sorted(glob.glob(f"{self.filepath}.*"))
    for old in archives[:-5]:   # keep last 5
        os.remove(old)
```

### 5. "How would you add structured / JSON logging?"

Swap the Formatter on a handler to `JSONFormatter`. For richer context (request_id, user_id), extend `LogRecord` to carry an `extras: dict` field.

```python
def log(self, level: LogLevel, message: str, **extras):
    if level < self.level:
        return
    record = LogRecord(level, message)
    record.extras = extras
    ...

# Caller:
logger.info("request handled", req_id="abc123", user_id=42)
```

JSONFormatter reads `record.extras` and merges into the output payload.

---

## Interviewer Questions by Level

**Junior**: What is the purpose of LogLevel and why use an IntEnum?  
**Mid-level**: Why does FileHandler have its own lock when Logger already has one?  
**Senior**: Walk me through how you'd extend this to support async logging without breaking existing synchronous callers.

---

## Common Interview Questions

- **Q: Why Singleton for Logger?** A: Application code imports the logger anywhere without passing instances around; a Singleton ensures one consistent level/handler configuration per process.
- **Q: How does Chain of Responsibility filter by log level?** A: Logger checks the global minimum first; each handler independently checks its own minimum — different handlers can have different verbosity.
- **Q: Async vs sync logging — when to choose async?** A: Async when logging latency is unacceptable (high-QPS services); sync when you cannot afford to lose records on crash.
- **Q: What if the remote handler is slow?** A: Put the remote handler behind an async queue, or give it a hard timeout (2s) plus a local buffer to retry.
- **Q: How do you support custom log formats?** A: Each handler holds a Formatter reference; callers call `handler.set_formatter(JSONFormatter())` — Strategy pattern lets you swap format without touching handler logic.
- **Q: How do you prevent a buggy handler from crashing the application?** A: Wrap `emit()` in try/except inside `handle()`; swallow the exception and print to stderr.
- **Q: What is double-checked locking and why is it needed for Singleton?** A: Check `_instance is None` outside and inside the lock. Without the outer check every call acquires the lock (unnecessary contention); without the inner check two threads can both pass the outer check and create two instances.
- **Q: How do you ensure log ordering across threads?** A: The Logger-level `_lock` serializes `log()` calls; for async logging, a single-threaded drain worker processes the queue in FIFO order.
