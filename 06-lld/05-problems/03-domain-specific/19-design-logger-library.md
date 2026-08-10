> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design a Logger Library — a classic framework-design problem that is the textbook use case for the Chain of Responsibility pattern.
>
> **Key concepts:**
> - Core Entities: `Logger` (Singleton), `LogAppender` (Strategy), `LogFilter` (Chain of Responsibility).
> - Chain of Responsibility: Create handlers for `DEBUG`, `INFO`, `WARN`, `ERROR`. Link them: `DebugLogger -> InfoLogger -> WarnLogger -> ErrorLogger`. If the system is set to `WARN`, the `DebugLogger` and `InfoLogger` simply pass the request along without acting.
> - Strategy Pattern (Appender): Where do the logs go? Provide strategies for `ConsoleAppender`, `FileAppender`, `DatabaseAppender`.
> - Asynchronous Logging: For performance, don't write to disk on the main thread. Use a `BlockingQueue` and a background consumer thread to write logs (Producer-Consumer pattern).
>
> **Key takeaway:** The interviewer wants to see Chain of Responsibility for log levels, Strategy for destinations, and Producer-Consumer (BlockingQueue) for async performance.

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
- handlers: List<LogHandler>
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

```java
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;

enum LogLevel {
    DEBUG(10), INFO(20), WARN(30), ERROR(40);

    private final int severity;

    LogLevel(int severity) { this.severity = severity; }

    public int getSeverity() { return severity; }
}

class LogRecord {
    private final LogLevel level;
    private final String message;
    private final Instant timestamp;
    private final String loggerName;
    private final long threadId;

    public LogRecord(LogLevel level, String message) {
        this(level, message, "root");
    }

    public LogRecord(LogLevel level, String message, String loggerName) {
        this.level = level;
        this.message = message;
        this.timestamp = Instant.now();
        this.loggerName = loggerName;
        this.threadId = Thread.currentThread().getId();
    }

    public LogLevel getLevel() { return level; }
    public String getMessage() { return message; }
    public Instant getTimestamp() { return timestamp; }
    public String getLoggerName() { return loggerName; }
    public long getThreadId() { return threadId; }
}

interface Formatter {
    String format(LogRecord record);
}

class TextFormatter implements Formatter {
    private static final DateTimeFormatter TS_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    @Override
    public String format(LogRecord record) {
        String ts = TS_FORMAT.format(record.getTimestamp());
        return String.format("[%s] %s (%d) — %s",
                record.getLevel().name(), ts, record.getThreadId(), record.getMessage());
    }
}

class JSONFormatter implements Formatter {
    @Override
    public String format(LogRecord record) {
        return String.format(
                "{\"level\":\"%s\",\"timestamp\":\"%s\",\"thread\":%d,\"message\":\"%s\"}",
                record.getLevel().name(), record.getTimestamp(), record.getThreadId(),
                record.getMessage());
    }
}

abstract class LogHandler {
    protected LogLevel level;
    protected Formatter formatter = new TextFormatter();

    protected LogHandler(LogLevel level) {
        this.level = level;
    }

    public void setLevel(LogLevel level) { this.level = level; }

    public void setFormatter(Formatter formatter) { this.formatter = formatter; }

    public void handle(LogRecord record) {
        if (record.getLevel().getSeverity() >= level.getSeverity()) {
            try {
                emit(record);
            } catch (Exception e) {
                System.err.println("Handler error: " + e.getMessage());
            }
        }
    }

    protected abstract void emit(LogRecord record) throws Exception;
}

class ConsoleHandler extends LogHandler {
    public ConsoleHandler(LogLevel level) { super(level); }

    @Override
    protected void emit(LogRecord record) {
        System.out.println(formatter.format(record));
    }
}

class FileHandler extends LogHandler {
    private final String filepath;
    private final long maxBytes;
    private final ReentrantLock lock = new ReentrantLock();

    public FileHandler(String filepath, long maxBytes, LogLevel level) {
        super(level);
        this.filepath = filepath;
        this.maxBytes = maxBytes;
    }

    @Override
    protected void emit(LogRecord record) throws IOException {
        String line = formatter.format(record) + "\n";
        lock.lock();
        try {
            File file = new File(filepath);
            if (file.exists() && file.length() >= maxBytes) {
                rotate();
            }
            try (FileWriter writer = new FileWriter(file, true)) {
                writer.write(line);
            }
        } finally {
            lock.unlock();
        }
    }

    private void rotate() throws IOException {
        String ts = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                .withZone(ZoneOffset.UTC).format(Instant.now());
        Files.move(Paths.get(filepath), Paths.get(filepath + "." + ts));
    }
}

class RemoteHandler extends LogHandler {
    private final String endpoint;

    public RemoteHandler(String endpoint, LogLevel level) {
        super(level);
        this.endpoint = endpoint;
        setFormatter(new JSONFormatter());
    }

    @Override
    protected void emit(LogRecord record) throws IOException {
        byte[] payload = formatter.format(record).getBytes();
        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setConnectTimeout(2000);
        conn.setReadTimeout(2000);
        conn.setDoOutput(true);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload);
        }
        conn.getResponseCode();
    }
}

class Logger {
    private static volatile Logger instance;
    private static final Object initLock = new Object();

    private volatile LogLevel level = LogLevel.DEBUG;
    private final List<LogHandler> handlers = new ArrayList<>();
    private final ReentrantLock lock = new ReentrantLock();

    protected Logger() { }

    protected LogLevel getLevel() { return level; }

    protected List<LogHandler> getHandlers() {
        lock.lock();
        try {
            return new ArrayList<>(handlers);
        } finally {
            lock.unlock();
        }
    }

    public static Logger getInstance() {
        if (instance == null) {
            synchronized (initLock) {
                if (instance == null) {
                    instance = new Logger();
                }
            }
        }
        return instance;
    }

    public void setLevel(LogLevel level) { this.level = level; }

    public void addHandler(LogHandler handler) {
        lock.lock();
        try {
            handlers.add(handler);
        } finally {
            lock.unlock();
        }
    }

    public void log(LogLevel level, String message) {
        if (level.getSeverity() < this.level.getSeverity()) {
            return;
        }
        LogRecord record = new LogRecord(level, message);
        List<LogHandler> snapshot;
        lock.lock();
        try {
            snapshot = new ArrayList<>(handlers);
        } finally {
            lock.unlock();
        }
        for (LogHandler handler : snapshot) {
            handler.handle(record);
        }
    }

    public void debug(String msg) { log(LogLevel.DEBUG, msg); }
    public void info(String msg)  { log(LogLevel.INFO, msg); }
    public void warn(String msg)  { log(LogLevel.WARN, msg); }
    public void error(String msg) { log(LogLevel.ERROR, msg); }
}
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

```java
// Adding a SlackHandler requires no Logger changes
class SlackHandler extends LogHandler {
    public SlackHandler(LogLevel level) { super(level); }

    @Override
    protected void emit(LogRecord record) throws IOException {
        String payload = String.format("{\"text\":\"%s\"}", formatter.format(record));
        HttpURLConnection conn = (HttpURLConnection) new URL(SLACK_WEBHOOK).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setConnectTimeout(3000);
        conn.setDoOutput(true);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes());
        }
        conn.getResponseCode();
    }
}

logger.addHandler(new SlackHandler(LogLevel.ERROR));
```

### 2. "How do you make logging thread-safe?"

Two locks:
- `_init_lock` (class-level) guards Singleton creation with double-checked locking.
- `_lock` (instance-level) guards handler list mutation.

Key insight: snapshot the handler list before iterating so `add_handler` during iteration doesn't cause concurrent modification. FileHandler holds its own lock for file I/O.

### 3. "How would you implement async logging?"

Move the emit path off the caller's thread using a bounded queue and a background writer thread. Callers enqueue `LogRecord`s without blocking.

```java
import java.util.concurrent.ArrayBlockingQueue;

class AsyncLogger extends Logger {
    private final ArrayBlockingQueue<LogRecord> queue = new ArrayBlockingQueue<>(10_000);
    private final Thread worker;

    public AsyncLogger() {
        super();
        worker = new Thread(this::drain);
        worker.setDaemon(true);
        worker.start();
    }

    @Override
    public void log(LogLevel level, String message) {
        if (level.getSeverity() < getLevel().getSeverity()) {
            return;
        }
        LogRecord record = new LogRecord(level, message);
        if (!queue.offer(record)) {
            // drop on backpressure; or block, or sample
        }
    }

    private void drain() {
        while (true) {
            try {
                LogRecord record = queue.take();
                for (LogHandler handler : getHandlers()) {
                    handler.handle(record);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
```

Trade-off: lower caller latency, but records in queue are lost on crash.

### 4. "How does log rotation work?"

Size-based: before each write, check `os.path.getsize`. If it exceeds `max_bytes`, rename the current file to `filename.YYYYMMDDHHMMSS` and open a fresh file. Prune older rotated files by keeping only the last N.

```java
private void rotate() throws IOException {
    String ts = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
            .withZone(ZoneOffset.UTC).format(Instant.now());
    Files.move(Paths.get(filepath), Paths.get(filepath + "." + ts));

    File dir = new File(filepath).getAbsoluteFile().getParentFile();
    File[] archives = dir.listFiles((d, name) -> name.startsWith(new File(filepath).getName() + "."));
    if (archives != null) {
        Arrays.sort(archives, Comparator.comparing(File::getName));
        int keep = 5;
        for (int i = 0; i < archives.length - keep; i++) {
            archives[i].delete();   // keep last 5
        }
    }
}
```

### 5. "How would you add structured / JSON logging?"

Swap the Formatter on a handler to `JSONFormatter`. For richer context (request_id, user_id), extend `LogRecord` to carry an `extras: dict` field.

```java
public void log(LogLevel level, String message, Map<String, Object> extras) {
    if (level.getSeverity() < this.level.getSeverity()) {
        return;
    }
    LogRecord record = new LogRecord(level, message);
    record.setExtras(extras);
    // ...
}

// Caller:
logger.info("request handled", Map.of("req_id", "abc123", "user_id", 42));
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

---

## Related

**Patterns applied here**

- [Singleton Pattern](../../03-design-patterns/01-creational/singleton.md)
- [Decorator Pattern](../../03-design-patterns/02-structural/decorator-pattern.md)
- [Chain of Responsibility Pattern](../../03-design-patterns/03-behavioral/chain-of-responsibility.md)
- [Strategy Pattern](../../03-design-patterns/03-behavioral/strategy-pattern.md)
- [Interpreter Pattern](../../03-design-patterns/03-behavioral/interpreter-pattern.md) — parse log-filter / pattern-layout mini-language

**SOLID focus**: [Single Responsibility](../../02-solid-principles/01-single-responsibility.md) · [Open/Closed](../../02-solid-principles/02-open-closed.md) · [Dependency Inversion](../../02-solid-principles/05-dependency-inversion.md)

**Concurrency**: [Thread-Safe Singleton](../../04-concurrency/thread-safe-singleton.md) · [Concurrency Patterns](../../04-concurrency/concurrency-patterns.md)

**Practice next**

- [Design Notification System](../02-frequent-problems/16-design-notification-system.md)
- [Design an ATM](../02-frequent-problems/12-design-atm.md)

Handler chains and pluggable sinks recur in both.

**Frameworks**: [LLD Template](../../../07-interview-templates/01-frameworks/02-lld-template.md) · [UML Diagrams](../../uml-diagrams.md) · [OOP Four Pillars](../../01-oop-fundamentals/four-pillars.md)
