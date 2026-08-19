> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Futures and Async Patterns — how to perform asynchronous, non-blocking programming using `CompletableFuture` (Java's version of Promises).
>
> **Key concepts:**
> - The problem: standard `Future.get()` blocks the thread until the result is ready, wasting thread pool resources.
> - `CompletableFuture`: allows chaining operations non-blocking. When task A finishes, trigger task B on the same thread pool.
> - Chaining (`thenApply`, `thenAccept`): transforms the result or consumes it without blocking.
> - Composition (`thenCompose`): chaining dependent async calls (e.g., fetch user ID -> use ID to fetch profile). Like `flatMap`.
> - Parallel execution (`thenCombine`): run task A and task B in parallel, then merge their results (e.g., fetch price from Amazon + price from eBay, then compare).
> - Multi-task (`allOf`, `anyOf`): wait for an array of futures to all complete (scatter-gather), or return as soon as the fastest one finishes.
> - Error handling (`exceptionally`): cleanly handle exceptions in the async chain without breaking the flow.
>
> **Key takeaway:** In modern LLD, blocking I/O is a bottleneck. If an interview asks "How do you query 3 microservices and combine the result?", your answer should be `CompletableFuture.allOf()` running on an `ExecutorService`.

---
module: 06-lld
topic: Concurrency
status: unread
tags: [06-lld, system-design, concurrency]
---
# Futures & Async Patterns

## Question

Before reading the patterns below, reason through each scenario:

1. **Future blocking**: You call `userService.getUser(id)` which takes 200ms. While waiting, your thread does nothing. In a 500-thread server handling 10,000 concurrent requests, what is the failure mode?

2. **Sequential chaining**: You need to: fetch a user ID (100ms) → fetch their profile (150ms) → format the response (10ms). You call each step with `.get()` between them. Total latency?

3. **Fan-out**: You need data from 3 independent services (each 200ms). You call them sequentially. Total latency? What is the theoretical minimum if you call them concurrently?

4. **Timeout**: An external service sometimes takes 30 seconds. Your SLA requires 500ms response. Without a timeout, what happens to your threads?

Try each before reading the corresponding section below.

---

## Topic Mindmap

```
[Futures & Async Patterns — Java CompletableFuture / ExecutorService]
├── Core Concept
│   ├── What → Non-blocking async computation pipelines that free threads during I/O waits
│   └── Why → Blocking get() wastes pooled threads; CompletableFuture chains work without pinning
├── Key Operations
│   ├── executor.submit() → run Callable in thread pool; returns Future
│   ├── CompletableFuture.allOf() → run futures concurrently; wait for all (fan-out)
│   ├── future.thenApply/thenCompose → chain transformations without blocking (sequential chaining)
│   ├── CompletableFuture.anyOf() → race futures; first wins (hedged requests)
│   ├── future.orTimeout(duration) → enforce deadline; raises TimeoutException on breach
│   └── future.exceptionally / handle → error recovery at each stage
├── When to Use
│   ├── ✓ Multiple independent I/O calls (fetch user + fetch orders + fetch recommendations)
│   ├── ✓ Dependent async steps that must sequence (get user_id → get profile → format)
│   └── ✓ Timeout enforcement on external service calls
├── When NOT to Use
│   ├── ✗ CPU-bound work — use a ForkJoinPool / parallel streams instead
│   └── ✗ Simple sequential single-threaded logic — adds complexity for no gain
├── Trade-offs
│   ├── Pro: Fan-out reduces latency from sum-of-latencies to max-of-latencies
│   └── Con: composing many stages can get hard to read/debug (stack traces span threads)
├── Real-World Examples
│   ├── Product page → user, inventory, reviews fetched in parallel with CompletableFuture.allOf()
│   └── Payment flow → auth → charge → notify chained with thenCompose
└── Interview Angles
    ├── Fan-out math → 3×200ms sequential = 600ms; concurrent = 200ms
    ├── Timeout → future.orTimeout(500, TimeUnit.MILLISECONDS) raises TimeoutException
    └── Code challenge: implement a parallel search across 3 databases returning fastest result
```

---

**Race conditions and failure modes derived per pattern:**

- **Future blocking**: Blocking `future.get()` pins a thread for the entire I/O duration. 10,000 concurrent requests × 200ms each = threads pile up waiting. With a 500-thread pool, Thread 501 cannot start until Thread 1 finishes its 200ms wait. Fix: don't block between I/O steps — use `CompletableFuture` chaining (`thenApply`/`thenCompose`) so no thread sits idle waiting on `.get()`.

- **Sequential chaining**: 100ms + 150ms + 10ms = 260ms minimum if you block between steps. With `CompletableFuture.thenCompose()` chaining, each stage runs as a callback when the previous stage completes — still 260ms of actual I/O, but no thread is parked blocking in between.

- **Fan-out sequential vs concurrent**: Sequential = 200ms + 200ms + 200ms = 600ms. Concurrent with `CompletableFuture.allOf()` = 200ms (all three in parallel). The constraint: independent operations must not be sequenced.

- **Timeout**: Without timeout, one slow external call blocks the pipeline indefinitely. Under load, all pool threads drain and the service stops responding — cascade failure. Fix: `future.orTimeout(500, TimeUnit.MILLISECONDS)` completes the future exceptionally with `TimeoutException`, allowing the caller to use a fallback via `.exceptionally()`.

---

> Java async execution patterns for LLD interviews. Know these for any system involving parallel I/O, async pipelines, or non-blocking computation.

---

## 1. Future (Basic Async Result)

**Problem**: Run a task on another thread and retrieve the result later.

```java
import java.util.concurrent.*;

ExecutorService executor = Executors.newFixedThreadPool(4);

Callable<Integer> task = () -> {
    Thread.sleep(1000);
    return 42;
};

Future<Integer> future = executor.submit(task);

// Do other work here...

Integer result = future.get();                        // blocks until done
Integer result2 = future.get(2, TimeUnit.SECONDS);     // blocks with timeout; raises TimeoutException
future.cancel(false);                                  // cancel if not yet running
```

**Limitations of `java.util.concurrent.Future`**: No chaining, no callbacks, no composition — use `CompletableFuture` for anything non-trivial.

---

## 2. CompletableFuture — Async Pipeline

**Problem**: Chain async operations without blocking between steps.

```java
import java.util.concurrent.CompletableFuture;

CompletableFuture<String> pipeline() {
    return CompletableFuture.supplyAsync(this::fetchUserId)          // start async
            .thenCompose(this::fetchUserProfile)                     // transform result (dependent async call)
            .thenApply(this::formatResponse)                         // another transform
            .exceptionally(ex -> "fallback response");                // handle any error in chain
}

String result = pipeline().join();   // block at the end only
```

**Key operations:**

| Operation | Input | Output | Notes |
|-----------|-------|--------|-------|
| `executor.submit(fn)` | Callable | Future | Start computation in thread pool |
| `future.get()` / `.join()` | — | T | Block for result |
| `CompletableFuture.supplyAsync(fn)` | Supplier | CompletableFuture\<T\> | Start async computation |
| `future.thenApply(fn)` | T -> R | CompletableFuture\<R\> | Transform result, non-blocking |
| `future.thenCompose(fn)` | T -> CF\<R\> | CompletableFuture\<R\> | Chain dependent async calls (flatMap) |
| `CompletableFuture.allOf(futures...)` | futures | CompletableFuture\<Void\> | Fan-out; all run concurrently |
| `future.orTimeout(t, unit)` | — | CompletableFuture\<T\> | Enforce deadline; raises TimeoutException |
| `CompletableFuture.anyOf(futures...)` | futures | CompletableFuture\<Object\> | Race; first wins |
| `future.exceptionally(fn)` | Throwable -> T | CompletableFuture\<T\> | Recover from any error in the chain |
| `new CompletableFuture<>()` | — | CompletableFuture | Bridge callback APIs into async pipeline |

---

## 3. Combining Multiple Futures (Fan-Out)

**Problem**: Fan out N async calls, then aggregate results.

```java
import java.util.concurrent.CompletableFuture;

void fanOut(String userId) {
    CompletableFuture<Profile> profileF = fetchProfileAsync(userId);
    CompletableFuture<Orders> ordersF = fetchOrdersAsync(userId);
    CompletableFuture<Balance> balanceF = fetchBalanceAsync(userId);

    // Wait for ALL — allOf runs futures concurrently
    CompletableFuture.allOf(profileF, ordersF, balanceF)
            .thenRun(() -> buildDashboard(profileF.join(), ordersF.join(), balanceF.join()))
            .join();
}

// Wait for FIRST — useful for hedged requests / race
Object hedgedRead() {
    CompletableFuture<Object> replica1 = replica1Async();
    CompletableFuture<Object> replica2 = replica2Async();
    CompletableFuture<Object> replica3 = replica3Async();

    Object firstResult = CompletableFuture.anyOf(replica1, replica2, replica3).join();

    // cancel the remaining replicas
    for (CompletableFuture<Object> f : List.of(replica1, replica2, replica3)) {
        f.cancel(false);
    }
    return firstResult;
}
```

**`CompletableFuture.allOf` vs `CompletableFuture.anyOf`**:
- `allOf`: all must complete; join() each individually to aggregate results; propagates first failure
- `anyOf`: first to complete wins; hedged reads, failover

---

## 4. Timeout and Fallback

**Problem**: Enforce deadline on async call; return default on timeout or failure.

```java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

CompletableFuture<String> withTimeout() {
    return fetchRemoteData()
            .orTimeout(500, TimeUnit.MILLISECONDS)   // raises TimeoutException on breach
            .exceptionally(ex -> "default value");    // fallback on timeout
}

// completeOnTimeout — return default instead of raising
CompletableFuture<String> withTimeoutFallback() {
    return fetchRemoteData()
            .completeOnTimeout("cached fallback", 500, TimeUnit.MILLISECONDS);
}
```

---

## 5. Async Exception Handling (CompletableFuture)

**Problem**: Handle errors at different stages of the pipeline without breaking the chain.

```java
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

CompletableFuture<String> pipelineWithErrorHandling() {
    return callExternalService()
            .exceptionally(ex -> {
                logger.log(Level.SEVERE, "External service failed", ex);
                return "fallback";
            })
            // transform runs whether we got real value or fallback
            .thenApply(this::transform);
}
```

**Error handling patterns**:
- `.exceptionally(fn)`: runs only on failure; recovery only
- `.handle((result, ex) -> ...)`: runs on both success and failure; most flexible
- `.whenComplete((result, ex) -> ...)`: side-effect on completion (success or failure), doesn't change the result

---

## 6. CompletableFuture as a Promise

**Problem**: Bridge callback-based API (e.g., async HTTP client) into a `CompletableFuture`.

```java
import java.util.concurrent.CompletableFuture;

CompletableFuture<Response> fetchAsync(String url) {
    CompletableFuture<Response> future = new CompletableFuture<>();

    httpClient.get(url,
            response -> future.complete(response),       // on success
            exc -> future.completeExceptionally(exc));    // on failure

    return future;  // caller uses: Response response = fetchAsync(url).join();
}
```

---

## 7. Blocking vs Non-Blocking Retrieval

```java
// java.util.concurrent.Future (thread-based)
future.get();                       // blocks; throws ExecutionException if task failed
future.get(2, TimeUnit.SECONDS);    // blocks with timeout; throws TimeoutException
future.isDone();                    // poll without blocking — true if finished or cancelled

// CompletableFuture (callback-based, non-blocking chaining)
future.thenApply(result -> ...);    // non-blocking continuation
future.isDone();                    // poll without blocking
future.getNow(defaultValue);        // get result if done; else return default, doesn't block
future.join();                      // blocks; unchecked CompletionException on failure
```

**Rule**: Chain with `.thenApply`/`.thenCompose` instead of blocking mid-pipeline. Use `future.get(timeout)` or `.join()` only at the sync boundary where you must block (e.g., top-level `main`, or a non-async request handler). Never block inside a thread that's itself running async callbacks.

---

## Pattern Decision Matrix

| Need | Use |
|------|-----|
| Single async task, block for result | `executor.submit()` + `future.get()` |
| Chain transformations without blocking | `future.thenApply()` / `.thenCompose()` |
| Fan-out N calls, aggregate | `CompletableFuture.allOf()` |
| First of N wins (hedged request) | `CompletableFuture.anyOf()` |
| Enforce deadline on async call | `future.orTimeout(n, unit)` |
| Recover from failure in chain | `future.exceptionally()` / `.handle()` |
| Bridge callback API to CompletableFuture | `new CompletableFuture<>()` + `.complete()` / `.completeExceptionally()` |
| Async inside thread pool safely | `CompletableFuture.supplyAsync(fn, executor)` |

---

## Quick Revision

- **java.util.concurrent.Future**: basic async result from thread pool; blocking `.get()`; no chaining — use only for simple cases
- **CompletableFuture**: composable; `.thenApply()`/`.thenCompose()` for sequential steps; `.allOf()` for fan-out
- **CompletableFuture.allOf()**: fan-out then aggregate all results (join each future); `.anyOf()`: race / hedged read
- **future.orTimeout(n, unit)**: completes exceptionally with `TimeoutException` on deadline breach; pair with `.exceptionally()` to return a default
- **`.exceptionally()`/`.handle()`** on a `CompletableFuture` handle both success path and failure recovery
- **Promise bridge**: `new CompletableFuture<>()` + `.complete()` / `.completeExceptionally()` in a callback
- Never block (`future.get()`/`.join()`) inside a thread that other async callbacks depend on — chain instead, or offload to a dedicated thread pool

---

## Applied In

This concept is used by **8 problems** in this repo:

**Low-Level Design**

- [Design Notification System](../06-problems/02-frequent-problems/16-design-notification-system.md)
- [Design a Ride Sharing System](../06-problems/03-domain-specific/22-design-ride-sharing.md)
- [Design a Pub-Sub Messaging System](../06-problems/03-domain-specific/23-design-pub-sub.md)
- [Design S3 Object Storage / File System](../06-problems/04-advanced-niche/26-design-s3-object-storage.md)
- [Design HTTP Tunneling Service](../06-problems/04-advanced-niche/30-design-tunneling-service.md)
- [Design Download Manager](../06-problems/04-advanced-niche/32-design-download-manager.md)
- [Design Lock-Free Queue](../06-problems/04-advanced-niche/34-design-lock-free-queue.md)
- [Design High-Contention Counter](../06-problems/04-advanced-niche/36-design-high-contention-counter.md)

