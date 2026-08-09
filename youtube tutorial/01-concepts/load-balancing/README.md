# Load Balancing Concepts

7 single-claim concept files on load balancing. Each states a claim, gives a line to say verbatim in an interview, and lists the probes that usually follow it.

| File | What it covers |
|------|-----------------|
| [lb-algorithm-selection](./lb-algorithm-selection.md) | Least-connections routes *more* traffic to fast-failing backends |
| [lb-lifecycle](./lb-lifecycle.md) | Draining, slow start, and outlier detection — active and passive health checks together |
| [retry-budgets](./retry-budgets.md) | Budget the fleet's total retries, not each request's individually |
| [lb-request-mutation](./lb-request-mutation.md) | Read `X-Forwarded-For` from the right; the leftmost entry is attacker-written |
| [lb-sizing](./lb-sizing.md) | Size on concurrent connections; ~28k ephemeral ports per source/destination pair |
| [strategy-injection](./strategy-injection.md) | `select(List<Backend> healthy)` — the parameter name is the design |
| [backend-registry-cow](./backend-registry-cow.md) | Copy-on-write registry; `release()` must be in `finally` or the count leaks |

See also: [INDEX.md](../INDEX.md) for the mistakes table and HLD→LLD mapping for this topic.
