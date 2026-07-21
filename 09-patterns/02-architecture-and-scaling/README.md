# Architecture and Scaling Patterns

Structural patterns that change how a system reads, writes, and isolates failure.

| File | What it covers |
|------|----------------|
| [CQRS + Event Sourcing](01-cqrs-event-sourcing.md) | Splitting read and write models; the event log as source of truth. |
| [Bulkhead Pattern](02-bulkhead-pattern.md) | Resource isolation so one saturated dependency cannot sink the process. |
| [Retries, Backoff, and Idempotency](03-retry-and-idempotency.md) | Retrying safely: backoff, jitter, retry budgets, and idempotency keys. |
