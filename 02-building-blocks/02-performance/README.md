# Performance

Components you add when the system is correct but too slow, or when it needs to survive load it cannot serve.

| File | What it covers |
|------|----------------|
| [Caching Layer](01-caching-layer.md) | Cache-aside, write-through, write-behind, eviction policies, and cache stampede mitigation. |
| [Rate Limiting](02-rate-limiting.md) | Token bucket, leaky bucket, sliding window; distributed enforcement with Redis. |
| [Circuit Breakers](03-circuit-breaker.md) | Closed/open/half-open states; failing fast to stop cascading failure. |
| [Bloom Filters](04-bloom-filter.md) | Probabilistic membership tests that keep pointless reads off the disk. |
