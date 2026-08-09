# Scaling Concepts

8 single-claim concept files on scaling. Each states a claim, gives a line to say verbatim in an interview, and lists the probes that usually follow it.

| File | What it covers |
|------|-----------------|
| [amdahl-and-usl](./amdahl-and-usl.md) | Coordination cost is quadratic — throughput peaks, then *declines* |
| [littles-law](./littles-law.md) | `L = λW` sizes every pool you own |
| [utilization-latency-knee](./utilization-latency-knee.md) | 90% utilization means roughly 10× latency |
| [tail-latency-fanout](./tail-latency-fanout.md) | 100 dependencies at p99 10ms means 63% of requests hit a slow one |
| [load-shedding-ladder](./load-shedding-ladder.md) | Shed selectively, then degrade, then queue, then fall over — in that order |
| [bounded-queues](./bounded-queues.md) | `newFixedThreadPool` hands you an unbounded queue by default |
| [contention-reduction](./contention-reduction.md) | A `synchronized` counter is Amdahl's serial fraction, in code |
| [timeout-budgets](./timeout-budgets.md) | Timeout budgets must *shrink* down the call chain |

See also: [INDEX.md](../INDEX.md) for the mistakes table and cross-cutting threads for this topic.
