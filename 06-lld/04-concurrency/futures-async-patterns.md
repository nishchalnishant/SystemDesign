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
[Futures & Async Patterns — Python concurrent.futures / asyncio]
├── Core Concept
│   ├── What → Non-blocking async computation pipelines that free threads during I/O waits
│   └── Why → Blocking result() wastes threads; asyncio/gather chains work without thread pinning
├── Key Operations
│   ├── executor.submit() → run callable in thread pool; returns Future
│   ├── asyncio.gather() → run coroutines concurrently; returns list of results (fan-out)
│   ├── await coro → suspend caller until coroutine completes (sequential chaining)
│   ├── asyncio.wait(FIRST_COMPLETED) → race coroutines; first wins (hedged requests)
│   ├── asyncio.wait_for(coro, timeout) → enforce deadline; raises TimeoutError on breach
│   └── try/except around await → error recovery at each stage
├── When to Use
│   ├── ✓ Multiple independent I/O calls (fetch user + fetch orders + fetch recommendations)
│   ├── ✓ Dependent async steps that must sequence (get user_id → get profile → format)
│   └── ✓ Timeout enforcement on external service calls
├── When NOT to Use
│   ├── ✗ CPU-bound work — use multiprocessing or ProcessPoolExecutor instead
│   └── ✗ Simple sequential single-threaded logic — adds complexity for no gain
├── Trade-offs
│   ├── Pro: Fan-out reduces latency from sum-of-latencies to max-of-latencies
│   └── Con: async/await requires consistent async throughout the call chain
├── Real-World Examples
│   ├── Product page → user, inventory, reviews fetched in parallel with asyncio.gather()
│   └── Payment flow → auth → charge → notify chained with sequential awaits
└── Interview Angles
    ├── Fan-out math → 3×200ms sequential = 600ms; concurrent = 200ms
    ├── Timeout → asyncio.wait_for(coro, timeout=0.5) raises TimeoutError
    └── Code challenge: implement a parallel search across 3 databases returning fastest result
```

---

**Race conditions and failure modes derived per pattern:**

- **Future blocking**: Blocking `future.result()` pins a thread for the entire I/O duration. 10,000 concurrent requests × 200ms each = threads pile up waiting. With a 500-thread pool, Thread 501 cannot start until Thread 1 finishes its 200ms wait. Fix: don't block between I/O steps — use `asyncio` with `await` to free the thread between steps.

- **Sequential chaining**: 100ms + 150ms + 10ms = 260ms minimum if you block between steps. With `asyncio` and sequential `await`, the coroutine suspends between steps without pinning a thread — still 260ms of actual I/O, but the event loop handles other work in between.

- **Fan-out sequential vs concurrent**: Sequential = 200ms + 200ms + 200ms = 600ms. Concurrent with `asyncio.gather()` = 200ms (all three in parallel). The constraint: independent operations must not be sequenced.

- **Timeout**: Without timeout, one slow external call blocks the coroutine indefinitely. Under load, all event loop slots drain and the service stops responding — cascade failure. Fix: `asyncio.wait_for(coro, timeout=0.5)` cancels the task and raises `TimeoutError`, allowing the caller to use a fallback.

---

> Python async execution patterns for LLD interviews. Know these for any system involving parallel I/O, async pipelines, or non-blocking computation.

---

## 1. Future (Basic Async Result)

**Problem**: Run a task on another thread and retrieve the result later.

```python
import concurrent.futures
import time

executor = concurrent.futures.ThreadPoolExecutor(max_workers=4)

def task():
    time.sleep(1)
    return 42

future = executor.submit(task)

# Do other work here...

result = future.result()              # blocks until done
result = future.result(timeout=2)     # blocks with timeout; raises TimeoutError
future.cancel()                       # cancel if not yet running
```

**Limitations of `concurrent.futures.Future`**: No chaining, no callbacks, no composition — use `asyncio` for anything non-trivial.

---

## 2. asyncio — Async Pipeline

**Problem**: Chain async operations without blocking between steps.

```python
import asyncio

async def pipeline():
    try:
        user_id = await fetch_user_id()           # start async
        profile  = await fetch_user_profile(user_id)  # transform result
        response = await format_response(profile)     # another transform
        return response
    except Exception:
        return "fallback response"                # handle any error in chain

result = asyncio.run(pipeline())                  # block at the end only
```

**Key operations:**

| Operation | Input | Output | Notes |
|-----------|-------|--------|-------|
| `executor.submit(fn)` | callable | Future | Start computation in thread pool |
| `await coro` | coroutine | T | Await result; suspends caller |
| `asyncio.gather(*coros)` | coroutines | list[T] | Fan-out; all run concurrently |
| `asyncio.wait_for(coro, timeout)` | coroutine | T | Enforce deadline; raises TimeoutError |
| `asyncio.wait(FIRST_COMPLETED)` | coroutines | (done, pending) | Race; first wins |
| `try/except` around `await` | — | — | Recover from any error in the chain |
| `loop.create_future()` | — | Future | Bridge callback APIs into asyncio |

---

## 3. Combining Multiple Coroutines (Fan-Out)

**Problem**: Fan out N async calls, then aggregate results.

```python
import asyncio

async def fan_out(user_id):
    # Wait for ALL — gather runs coroutines concurrently
    profile, orders, balance = await asyncio.gather(
        fetch_profile_async(user_id),
        fetch_orders_async(user_id),
        fetch_balance_async(user_id),
    )
    build_dashboard(profile, orders, balance)

# Wait for FIRST — useful for hedged requests / race
async def hedged_read():
    done, pending = await asyncio.wait(
        [replica1(), replica2(), replica3()],
        return_when=asyncio.FIRST_COMPLETED,
    )
    for task in pending:
        task.cancel()                 # cancel remaining replicas
    first_result = next(iter(done)).result()
    return first_result
```

**`asyncio.gather` vs `asyncio.wait(FIRST_COMPLETED)`**:
- `gather`: all must complete; aggregate results; raises if any fails
- `wait(FIRST_COMPLETED)`: first to complete wins; hedged reads, failover

---

## 4. Timeout and Fallback

**Problem**: Enforce deadline on async call; return default on timeout or failure.

```python
import asyncio

async def with_timeout():
    try:
        result = await asyncio.wait_for(
            fetch_remote_data(), timeout=0.5   # raises asyncio.TimeoutError on breach
        )
    except asyncio.TimeoutError:
        result = "default value"               # fallback on timeout
    return result

# completeOnTimeout equivalent — return default instead of raising
async def with_timeout_fallback():
    try:
        return await asyncio.wait_for(fetch_remote_data(), timeout=0.5)
    except asyncio.TimeoutError:
        return "cached fallback"
```

---

## 5. Async Exception Handling (asyncio)

**Problem**: Handle errors at different stages of the pipeline without breaking the chain.

```python
import asyncio
import logging

async def pipeline_with_error_handling():
    try:
        value = await call_external_service()
    except Exception as ex:
        logging.error("External service failed", exc_info=ex)
        value = "fallback"
    # transform runs whether we got real value or fallback
    return transform(value)
```

**Error handling patterns**:
- `except ExceptionType`: runs only on failure; recovery only
- `try/except/else`: `else` runs on success; `except` on failure; most flexible

---

## 6. asyncio Future as a Promise

**Problem**: Bridge callback-based API (e.g., async HTTP client) into an `asyncio.Future`.

```python
import asyncio

async def fetch_async(url: str):
    loop = asyncio.get_running_loop()
    future = loop.create_future()

    def on_success(response):
        loop.call_soon_threadsafe(future.set_result, response)

    def on_failure(exc):
        loop.call_soon_threadsafe(future.set_exception, exc)

    http_client.get(url, on_success=on_success, on_failure=on_failure)

    return await future  # caller uses: response = await fetch_async(url)
```

---

## 7. Blocking vs Non-Blocking Retrieval

```python
# concurrent.futures.Future (thread-based)
future.result()                    # blocks; raises exception if task failed
future.result(timeout=2)           # blocks with timeout; raises TimeoutError
future.done()                      # poll without blocking — True if finished or cancelled

# asyncio (event-loop-based)
result = await coro                # non-blocking await inside async def
task.done()                        # poll without blocking
task.result()                      # get result if done; raises if not yet done or failed
```

**Rule**: Use `await` inside `async def` functions (non-blocking). Use `future.result(timeout)` only at the sync boundary where you must block (e.g., top-level or in a non-async context). Never block inside a running event loop.

---

## Pattern Decision Matrix

| Need | Use |
|------|-----|
| Single async task, block for result | `executor.submit()` + `future.result()` |
| Chain transformations without blocking | `async def` + sequential `await` |
| Fan-out N calls, aggregate | `asyncio.gather()` |
| First of N wins (hedged request) | `asyncio.wait(return_when=FIRST_COMPLETED)` |
| Enforce deadline on async call | `asyncio.wait_for(coro, timeout=N)` |
| Recover from failure in chain | `try/except` around `await`; use fallback in `except` |
| Bridge callback API to asyncio | `loop.create_future()` + `future.set_result()` / `set_exception()` |
| Async inside thread pool safely | `asyncio.run_coroutine_threadsafe()` |

---

## Quick Revision

- **concurrent.futures.Future**: basic async result from thread pool; blocking `.result()`; no chaining — use only for simple cases
- **asyncio**: composable; `async def` + `await` for sequential steps; `asyncio.gather()` for fan-out
- **asyncio.gather()**: fan-out then aggregate all results; `asyncio.wait(FIRST_COMPLETED)`: race / hedged read
- **asyncio.wait_for(timeout)**: raises `TimeoutError` on deadline breach; catch to return default
- **try/except** around `await` handles both success path and failure recovery
- **Promise bridge**: `loop.create_future()` + `.set_result()` / `.set_exception()` in callback
- Never block (`future.result()`) inside a running event loop — always `await` or offload to a thread pool
