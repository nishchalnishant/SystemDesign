# Futures & Async Patterns

> Java async execution patterns for LLD interviews. Know these for any system involving parallel I/O, async pipelines, or non-blocking computation.

---

## 1. Future (Basic Async Result)

**Problem**: Run a task on another thread and retrieve the result later.

```java
ExecutorService executor = Executors.newFixedThreadPool(4);

Future<Integer> future = executor.submit(() -> {
    Thread.sleep(1000);
    return 42;
});

// Do other work here...

Integer result = future.get();           // blocks until done
Integer result = future.get(2, SECONDS); // blocks with timeout; throws TimeoutException
future.cancel(true);                     // interrupt if running
```

**Limitations of Future**: No chaining, no callbacks, no composition — use `CompletableFuture` for anything non-trivial.

---

## 2. CompletableFuture — Async Pipeline

**Problem**: Chain async operations without blocking between steps.

```java
CompletableFuture<String> pipeline = CompletableFuture
    .supplyAsync(() -> fetchUserId(), executor)           // start async
    .thenApplyAsync(id -> fetchUserProfile(id), executor) // transform result
    .thenApplyAsync(profile -> formatResponse(profile))   // another transform
    .exceptionally(ex -> "fallback response");            // handle any error in chain

String result = pipeline.get(); // block at the end only
```

**Key methods:**

| Method | Input | Output | Notes |
|--------|-------|--------|-------|
| `supplyAsync` | none | T | Start async computation |
| `thenApply` | T → U | U | Sync transform of result |
| `thenApplyAsync` | T → U | U | Async transform (new thread) |
| `thenAccept` | T → void | void | Consume result, no return |
| `thenRun` | void → void | void | Run action after completion |
| `thenCompose` | T → CF<U> | CF<U> | FlatMap — avoid nested CFs |
| `exceptionally` | Throwable → T | T | Recover from any error |
| `handle` | (T, Throwable) → U | U | Handle both success and error |

---

## 3. Combining Multiple Futures

**Problem**: Fan out N async calls, then aggregate results.

```java
CompletableFuture<UserProfile> profileFuture = fetchProfileAsync(userId);
CompletableFuture<List<Order>> ordersFuture  = fetchOrdersAsync(userId);
CompletableFuture<Balance>     balanceFuture = fetchBalanceAsync(userId);

// Wait for ALL — returns CF<Void>; access via .join() on each
CompletableFuture.allOf(profileFuture, ordersFuture, balanceFuture)
    .thenRun(() -> {
        UserProfile profile = profileFuture.join();  // never blocks here — already done
        List<Order>  orders  = ordersFuture.join();
        Balance      balance = balanceFuture.join();
        buildDashboard(profile, orders, balance);
    });

// Wait for FIRST — useful for hedged requests / race
CompletableFuture<String> first = CompletableFuture.anyOf(replica1, replica2, replica3)
    .thenApply(result -> (String) result);
```

**`allOf` vs `anyOf`**:
- `allOf`: all must complete; aggregate results; throws if any fails
- `anyOf`: first to complete wins; hedged reads, failover

---

## 4. Timeout and Fallback

**Problem**: Enforce deadline on async call; return default on timeout or failure.

```java
// Java 9+: orTimeout and completeOnTimeout
CompletableFuture<String> result = fetchRemoteData()
    .orTimeout(500, TimeUnit.MILLISECONDS)        // throws TimeoutException on breach
    .exceptionally(ex -> "default value");         // catches both timeout and other errors

// completeOnTimeout: complete with value instead of exception
CompletableFuture<String> result = fetchRemoteData()
    .completeOnTimeout("cached fallback", 500, MILLISECONDS);
```

---

## 5. Async Exception Handling

**Problem**: Handle errors at different stages of the pipeline without breaking the chain.

```java
CompletableFuture<String> result = CompletableFuture
    .supplyAsync(() -> callExternalService())
    .handle((value, ex) -> {                       // handle() always runs
        if (ex != null) {
            log.error("External service failed", ex);
            return "fallback";
        }
        return value;
    })
    .thenApply(v -> transform(v));                  // only runs if no unhandled exception
```

**`exceptionally` vs `handle`**:
- `exceptionally`: runs only on failure; recovery only
- `handle`: runs always; can inspect both value and exception; more flexible

---

## 6. Completable Future as a Promise

**Problem**: Bridge callback-based API (e.g., async HTTP client) into `CompletableFuture`.

```java
public CompletableFuture<Response> fetchAsync(String url) {
    CompletableFuture<Response> promise = new CompletableFuture<>();

    httpClient.get(url, new Callback() {
        @Override public void onSuccess(Response r) { promise.complete(r); }
        @Override public void onFailure(Throwable t) { promise.completeExceptionally(t); }
    });

    return promise; // caller chains .thenApply() etc. on this
}
```

---

## 7. Blocking vs Non-Blocking Retrieval

```java
cf.get()            // blocks; throws checked InterruptedException + ExecutionException
cf.join()           // blocks; throws unchecked CompletionException — prefer in lambda chains
cf.getNow(default)  // returns immediately: result if done, else default (no block)
cf.isDone()         // poll without blocking
```

**Rule**: Use `.join()` inside `.thenRun()` / `.thenAccept()` (already inside a completion callback, guaranteed done). Use `.get(timeout)` at the boundary where you must block.

---

## Pattern Decision Matrix

| Need | Use |
|------|-----|
| Single async task, block for result | `ExecutorService.submit()` + `Future.get()` |
| Chain transformations without blocking | `CompletableFuture.thenApply / thenCompose` |
| Fan-out N calls, aggregate | `CompletableFuture.allOf()` + `.join()` on each |
| First of N wins (hedged request) | `CompletableFuture.anyOf()` |
| Enforce deadline on async call | `.orTimeout()` or `.completeOnTimeout()` |
| Recover from failure in chain | `.exceptionally()` for simple, `.handle()` for both |
| Bridge callback API to CF | `new CompletableFuture<>()` + `.complete()` / `.completeExceptionally()` |
| Async inside lambda safely | `.join()` (unchecked) over `.get()` (checked) |

---

## Quick Revision

- **Future**: basic async result; blocking `.get()`; no chaining — use only for simple cases
- **CompletableFuture**: composable; `thenApply` (sync), `thenApplyAsync` (async); `thenCompose` = flatMap
- **allOf**: fan-out then join; `anyOf`: race / hedged read
- **orTimeout**: throws on deadline breach; `completeOnTimeout`: returns default instead
- **handle()** runs on both success and failure; `exceptionally()` only on failure
- **Promise bridge**: `new CompletableFuture<>()` + `.complete()` in callback
- Never call `.get()` on the event/IO thread — always use `.thenApplyAsync(executor)` to shift work
