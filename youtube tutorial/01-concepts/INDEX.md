# Concepts Index

31 concept files across four topics. Each is one claim, an interview line to say verbatim, and the probes that follow it.

**How to use this:** the tables below are the pre-interview skim. The concept files are the rehearsal layer — open one when a mistake row here doesn't immediately make sense to you.

---

## Caching (7)

| Concept | The claim |
|---|---|
| [working-set-sizing](./caching/working-set-sizing.md) | Cache the working set, not the dataset; hit-rate → DB-load is non-linear |
| [cache-aside-race](./caching/cache-aside-race.md) | A reader's `SET` can land after a writer's `DELETE` — never dual-write |
| [hot-keys](./caching/hot-keys.md) | Consistent hashing maps one key to one node *by construction*; sharding cannot help |
| [cache-placement](./caching/cache-placement.md) | Choose the layer by how you invalidate it, not by latency |
| [single-flight-loading](./caching/single-flight-loading.md) | Hot-key expiry stampedes the DB; coalesce, jitter, refresh early |
| [negative-caching](./caching/negative-caching.md) | Cache the misses too, or penetration walks straight through |
| [cache-interface-design](./caching/cache-interface-design.md) | `get`/`put`/`invalidate` is the contract; eviction policy is injected |
| [ttl-expiry-strategies](./caching/ttl-expiry-strategies.md) | Lazy expiry means expired keys still hold memory |

### Mistakes

| Mistake | Fix |
|---|---|
| "Add a cache" with no sizing | Working set × hit-rate math |
| Assuming a cache fixes p99 | p99 **is** the miss path — it does not |
| Dual-write (`UPDATE db; SET cache`) | Delete-on-write, or CDC invalidation |
| Ignoring the cache-aside race | Versioned keys or CDC |
| Sharding to fix a hot key | Key splitting or L1 — sharding cannot help |
| Caching write-heavy or strict-consistency data | Decline, with a reason |
| Exposing eviction policy in the interface | Inject as strategy |
| One timer per TTL entry | Lazy expiry + sampling |

---

## API Design (8)

| Concept | The claim |
|---|---|
| [protocol-selection](./api-design/protocol-selection.md) | Two questions resolve it: who calls, and who initiates |
| [cursor-pagination](./api-design/cursor-pagination.md) | `OFFSET 100000` scans and discards 100k rows; inserts shift the window |
| [idempotency-keys](./api-design/idempotency-keys.md) | A lost response is indistinguishable from a lost request |
| [api-versioning](./api-design/api-versioning.md) | Mobile clients never fully migrate, so `/v2/` is permanent |
| [status-codes](./api-design/status-codes.md) | `401` = refresh and retry; `403` = don't bother |
| [interface-first-design](./api-design/interface-first-design.md) | Make illegal states unrepresentable; behavior over state |
| [exceptions-as-contract](./api-design/exceptions-as-contract.md) | `Optional` for expected absence, exceptions for violations |
| [concurrency-in-contract](./api-design/concurrency-in-contract.md) | Per-operation atomicity ≠ compound-sequence atomicity |

### Mistakes

| Mistake | Fix |
|---|---|
| Verbs in REST paths (`/createUser`) | Nouns + HTTP method |
| Offset pagination on a feed | Cursor |
| No idempotency on payments | `Idempotency-Key` header |
| Getters/setters on every field | Expose behavior, not state |
| Returning mutable internals | `unmodifiableList` / defensive copy |
| Concrete types in constructors | Depend on interfaces |
| `throws Exception` | Specific domain exceptions |
| Designing all endpoints before requirements are settled | Endpoints derive from requirements |

**Process note:** define APIs immediately after functional requirements, before drawing boxes. Spend 3–5 minutes. Map each requirement to exactly one endpoint — if a requirement needs three, it was probably two requirements.

---

## Load Balancing (7)

| Concept | The claim |
|---|---|
| [lb-algorithm-selection](./load-balancing/lb-algorithm-selection.md) | Least-connections routes *more* traffic to fast-failing backends |
| [lb-lifecycle](./load-balancing/lb-lifecycle.md) | Draining, slow start, outlier detection — active + passive together |
| [retry-budgets](./load-balancing/retry-budgets.md) | Budget the fleet's total retries, not each request's |
| [lb-request-mutation](./load-balancing/lb-request-mutation.md) | Read `X-Forwarded-For` from the right; the left is attacker-written |
| [lb-sizing](./load-balancing/lb-sizing.md) | Size on concurrent connections; ~28k ephemeral ports per pair |
| [strategy-injection](./load-balancing/strategy-injection.md) | `select(List<Backend> healthy)` — the parameter name is the design |
| [backend-registry-cow](./load-balancing/backend-registry-cow.md) | Copy-on-write registry; `release()` in `finally` or the count leaks |

### Mistakes

| Mistake | Fix |
|---|---|
| "Round robin" as the reflexive answer | P2C for large fleets; justify by coordination cost |
| Least-connections without outlier detection | Fast-failing backends look idle |
| Active health checks only | Add passive — `/health` misses degraded hosts |
| No max-ejection cap | A bad deploy ejects the entire fleet |
| Deploying without draining | Drops in-flight requests every release |
| No slow start after autoscale | Cold backend floods, falls over, gets ejected |
| Unbounded retries | Retry budget + jittered backoff, or a retry storm |
| Trusting `X-Forwarded-For` blindly | Count from the right; clients forge it |
| Sizing on req/s for WebSockets | Size on concurrent connections |
| `release()` outside `finally` | Count leaks; backend silently drops out |

### HLD → LLD

| HLD concern | LLD expression |
|---|---|
| Algorithm choice | Injected `LoadBalancingStrategy` |
| Health checking | `BackendRegistry` + copy-on-write snapshot |
| Connection draining | `BackendState.DRAINING` + in-flight count |
| Least-connections tracking | `AtomicInteger` + release in `finally` |
| No healthy backends | Domain exception, not `null` |

---

## Scaling (8)

| Concept | The claim |
|---|---|
| [amdahl-and-usl](./scaling/amdahl-and-usl.md) | Coordination is quadratic — throughput peaks, then *declines* |
| [littles-law](./scaling/littles-law.md) | `L = λW` sizes every pool you own |
| [utilization-latency-knee](./scaling/utilization-latency-knee.md) | 90% utilization means 10× latency |
| [tail-latency-fanout](./scaling/tail-latency-fanout.md) | 100 deps at p99 10ms → 63% hit a slow one |
| [load-shedding-ladder](./scaling/load-shedding-ladder.md) | Shed selectively > degrade > queue > fall over |
| [bounded-queues](./scaling/bounded-queues.md) | `newFixedThreadPool` hands you an unbounded queue by default |
| [contention-reduction](./scaling/contention-reduction.md) | A `synchronized` counter is Amdahl's serial fraction in code |
| [timeout-budgets](./scaling/timeout-budgets.md) | Budgets must *shrink* down the call chain |

### Mistakes

| Mistake | Fix |
|---|---|
| "Add more servers" with no ceiling named | Amdahl: 5% serial → 20× cap |
| Assuming throughput grows monotonically | USL: coherence makes it peak and fall |
| Targeting 90% utilization as efficient | 90% = 10× latency; target 50–70% |
| Optimizing average latency in fan-out | 100 deps at p99 10ms → 63% hit a slow one |
| No load-shedding story | Shed > degrade > queue > collapse |
| Unbounded queue | `ArrayBlockingQueue` + explicit policy |
| `newFixedThreadPool` under overload | `ThreadPoolExecutor` + bounded queue |
| Pool size by intuition | `threads = throughput × latency` |
| Calls without timeouts | Timeouts + decreasing budget |
| Sharding explained as "splits data" | Sharding removes **coordination** (β → 0) |

---

## Cross-cutting lines worth having ready

- **Sharding** removes coordination (β → 0). It does not merely "split the data." → [amdahl-and-usl](./scaling/amdahl-and-usl.md)
- **p99 is the miss path.** Caching improves the average and leaves the tail alone. → [working-set-sizing](./caching/working-set-sizing.md), [tail-latency-fanout](./scaling/tail-latency-fanout.md)
- **Timeout, retry budget, circuit breaker are one system** — one attempt, aggregate attempts, stop attempting. → [timeout-budgets](./scaling/timeout-budgets.md)
- **Strategy injection appears three times** — eviction policy, LB algorithm, rate-limit policy. Same move each time. → [cache-interface-design](./caching/cache-interface-design.md), [strategy-injection](./load-balancing/strategy-injection.md)
- **Compound operations are not atomic** even on concurrent collections. → [concurrency-in-contract](./api-design/concurrency-in-contract.md)

## Not yet covered

`rate-limiting/` is a stub — the source folder is a 38-line README with no original HLD/LLD content to extract.
