# Monitoring, SLO, and Observability Template

## Framework: What to Answer When Asked About Reliability

Use this template when the interviewer asks:
- "How do you monitor this system?"
- "How do you measure reliability?"
- "What happens when this service degrades?"
- "How would you set up alerting for this?"

---

## SLI, SLO, SLA — Definitions

| Term | Meaning | Example |
|---|---|---|
| **SLI** (Service Level Indicator) | The actual metric you measure | 99.2% of requests succeed in 200ms |
| **SLO** (Service Level Objective) | Your internal reliability target | 99.9% of requests succeed in 200ms over 30 days |
| **SLA** (Service Level Agreement) | External contractual commitment | 99.5% uptime, or customer gets credit |
| **Error Budget** | SLO headroom available to spend | 100% - 99.9% = 0.1% = 43.8 min/month |

**Key distinction**: SLO is internal (engineering target). SLA is external (legal commitment). SLA is always weaker than SLO — you must exceed your SLA by enough buffer to accommodate incidents.

---

## Error Budget Calculation

```
Monthly error budget = (1 - SLO) × total_minutes_in_month

At 99.9% SLO:
  (1 - 0.999) × 43,800 min/month = 43.8 minutes/month

At 99.99% SLO:
  (1 - 0.9999) × 43,800 = 4.38 minutes/month

At 99.999% SLO ("five nines"):
  (1 - 0.99999) × 43,800 = 0.438 minutes = 26 seconds/month
```

**Practical use of error budget**:
- If budget is abundant (>70% remaining): deploy more aggressively, run experiments
- If budget is at 50%: slow down rollouts, increase canary duration
- If budget is at 0%: freeze all non-critical deployments, focus on reliability work

---

## Choosing Good SLIs

A good SLI measures what the user experiences, not internal implementation details.

### The Four Golden Signals (Google SRE Book)

| Signal | What to Measure | Example |
|---|---|---|
| **Latency** | Time to serve requests | p50, p95, p99 response time |
| **Traffic** | Demand on the system | Requests/second, transactions/second |
| **Errors** | Rate of failing requests | HTTP 5xx rate, failed payment rate |
| **Saturation** | How "full" the system is | CPU utilization, queue depth, memory pressure |

### RED Method (Microservices)

Focus on the service boundary:
- **Rate**: requests per second this service receives
- **Errors**: error rate for those requests
- **Duration**: distribution of request durations (p99, p999)

### USE Method (Infrastructure / Resources)

Focus on each resource:
- **Utilization**: % time resource is busy
- **Saturation**: queue depth or extra work the resource can't yet handle
- **Errors**: error events for the resource

---

## SLO Templates by Service Type

### User-Facing Web/API Service

```yaml
slos:
  availability:
    sli: "(total_requests - error_5xx) / total_requests"
    window: 30 days rolling
    target: 99.9%
    
  latency:
    sli: "fraction of requests completing in < 200ms"
    window: 30 days rolling
    target: 95%
    
  latency_tail:
    sli: "fraction of requests completing in < 1000ms"  
    window: 30 days rolling
    target: 99%
```

### Data Pipeline / Streaming System

```yaml
slos:
  freshness:
    sli: "fraction of time pipeline lag < 30 seconds"
    target: 99%
    
  completeness:
    sli: "fraction of expected records processed each hour"
    target: 99.9%
    
  correctness:
    sli: "fraction of outputs matching expected results in validation"
    target: 99.99%
```

### Payment / Transaction System

```yaml
slos:
  availability:
    sli: "fraction of payment attempts that succeed or fail with a known error"
    target: 99.95%
    
  latency:
    sli: "fraction of payments completing in < 3 seconds"
    target: 99%
    
  duplicate_protection:
    sli: "fraction of transactions with exactly-once semantics"
    target: 99.999%
```

---

## Alerting Strategy

### Alert on Symptoms, Not Causes

❌ Bad: Alert when CPU > 80%
✅ Good: Alert when error rate > 0.1% for 5 minutes

CPU at 80% may have no user impact. Error rate > 0.1% is always a user problem.

### Multi-Window, Multi-Burn-Rate Alerts

Use burn rate to balance alerting sensitivity vs noise.

**Burn rate** = how fast you're consuming the error budget relative to normal.

At 99.9% SLO (43.8 min/month budget):
- Burn rate 1 = consuming budget at exactly the SLO rate (neutral)
- Burn rate 14 = consuming budget 14x faster (will exhaust in 2 hours at 5% error rate)

```
Fast alert (page immediately):
  Condition: burn rate > 14 for 1 hour window AND burn rate > 14 for 5 min window
  → Exhausts 2% of budget (1/14 of 30 days × 14x burn = 1 day budget in 1 hour)
  Severity: PagerDuty page, wake on-call

Slow alert (ticket next business day):
  Condition: burn rate > 1 for 3-day window
  → Budget slowly eroding, no immediate crisis
  Severity: Jira ticket, review in standup
```

### Alert Routing

```
P0 (system down):
  → Page on-call immediately
  → Escalate to lead after 15 min if unacknowledged
  
P1 (significant degradation):
  → Page on-call
  → SLA at risk notification to account team

P2 (slow degradation):
  → Slack notification to team channel
  → Jira ticket auto-created

P3 (warning):
  → Dashboard annotation only
  → Review in weekly reliability meeting
```

---

## Distributed Tracing

### What Distributed Tracing Solves

A single user request fans out to 10 microservices. A query is slow. Which service is the bottleneck? Logs from each service are separate and have no correlation ID. Distributed tracing provides end-to-end visibility.

### Key Concepts

- **Trace**: the complete journey of one request across all services
- **Span**: a single unit of work within a trace (one service call, one DB query)
- **Trace ID**: globally unique ID propagated in HTTP headers (`traceparent`, `X-B3-TraceId`)
- **Span ID**: unique within a trace, identifies this specific span
- **Parent Span ID**: links this span to its caller

```
Trace: user_purchase (total: 450ms)
  │
  ├── auth_service.validate_token     (12ms)
  ├── inventory_service.reserve        (200ms)   ← bottleneck
  │     └── postgres.select_for_update  (190ms)  ← actual bottleneck
  ├── payment_service.charge           (120ms)
  └── notification_service.send_email  (50ms, async)
```

### OpenTelemetry (OTEL)

The standard for instrumentation:
- **SDK**: auto-instruments HTTP clients, JDBC, gRPC, etc.
- **Collector**: receives spans, batches, exports to backends
- **Exporters**: Jaeger, Zipkin, Datadog, Honeycomb, Tempo (Grafana)

```java
// Manual span creation in Java
Tracer tracer = openTelemetry.getTracer("booking-service");

Span span = tracer.spanBuilder("reserve_seat")
    .setAttribute("event.id", eventId)
    .setAttribute("seat.id", seatId)
    .startSpan();

try (Scope scope = span.makeCurrent()) {
    // ... business logic
    span.setStatus(StatusCode.OK);
} catch (Exception e) {
    span.recordException(e);
    span.setStatus(StatusCode.ERROR, e.getMessage());
    throw e;
} finally {
    span.end();
}
```

### Sampling Strategy

100% sampling is too expensive for high-volume services (tracing ~10% of request size in storage).

**Head-based sampling** (decision at trace start):
- Simple: sample 10% of all traces randomly
- Problem: misses rare errors (if 0.1% of requests error, you sample 10% → only capture 0.01% of errors)

**Tail-based sampling** (decision after trace completes):
- Buffer all spans for a trace, then decide to keep or drop based on outcome
- Keep 100% of error traces, 100% of slow traces (p99+), 1% of normal traces
- Problem: requires buffering in the collector (memory overhead)
- Solution: OpenTelemetry Collector's tail sampling processor

**Cardinality trap**: adding `user_id` or `request_id` as a **metric label** creates a unique time series per user → millions of series → Prometheus/Grafana OOM. Rule: high-cardinality values go in **trace spans** (not metrics), or in **log fields** (not log metadata).

---

## Metrics Instrumentation Checklist

For every new service/endpoint, instrument:

```
Request rate:     counter — requests_total{service, endpoint, method, status}
Error rate:       counter — errors_total{service, endpoint, error_type}
Latency:          histogram — request_duration_seconds{service, endpoint} (buckets: 10ms, 50ms, 100ms, 500ms, 1s, 5s)
Concurrent reqs:  gauge    — active_requests{service}
Queue depth:      gauge    — queue_size{service, queue_name}
Cache hit rate:   counter  — cache_hits_total / cache_misses_total
DB pool:          gauge    — db_pool_available{service, db_name}
```

---

## Runbook Template for an Incident

```markdown
## Incident: [service] SLO Breach

### Detection
- Alert: burn_rate > 14 for 1h window + 5min window
- Dashboard: [link]
- Initial error rate: X%

### Impact Assessment
- Affected: [user-facing feature]
- Error budget consumed: X minutes of Y minutes monthly budget
- Users impacted: [estimation method]

### Immediate Mitigation
1. Check recent deployments: `kubectl rollout history deploy/[service]`
2. Check error logs: `[log query]`
3. Roll back if deployment-correlated: `kubectl rollout undo deploy/[service]`

### Root Cause Investigation
- [ ] Database latency spike?
- [ ] Upstream dependency failure?
- [ ] Traffic spike beyond capacity?
- [ ] Config change?

### Resolution Criteria
- Error rate < 0.1% sustained for 10 minutes
- p99 latency below SLO target
- No active PagerDuty alerts

### Post-Incident
- [ ] Write postmortem within 48 hours
- [ ] Action items in Jira
- [ ] SLO burn rate back to < 1
```

---

## Interview Answer: "How do you monitor this system?"

Structure your answer as:

1. **Define your SLIs** — what user-visible metrics matter (availability, latency, correctness)
2. **Set SLO targets** — e.g., 99.9% availability, p99 latency < 500ms
3. **Error budget** — how much downtime is allowed, how it governs deployment pace
4. **Metrics** — Four Golden Signals on each service boundary
5. **Distributed tracing** — end-to-end visibility with OpenTelemetry
6. **Alerting** — multi-burn-rate alerts, severity levels, routing
7. **Dashboards** — latency heat maps, error rate graphs, saturation indicators
8. **Runbooks** — documented first-response steps for common failure modes

---

## See Also

- `04-advanced-topics/observability.md` — Cardinality explosion, tail-based sampling deep dive
- `07-interview-templates/failure-recovery-playbook.md` — Degradation hierarchies
- `07-interview-templates/hld-template.md` — Where monitoring fits in the HLD framework
- `08-reference/numbers-to-know.md` — SLO math, latency numbers
