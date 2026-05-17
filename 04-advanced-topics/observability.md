# Observability - Monitoring, Metrics, and Tracing

> **Production-focused guide for SDE-3: How to ensure your systems are working correctly**

## Table of Contents

1. [What is Observability?](#what-is-observability)
2. [The Three Pillars](#the-three-pillars)
3. [SLI, SLO, SLA](#sli-slo-sla)
4. [Distributed Tracing](#distributed-tracing)
5. [Alerting Best Practices](#alerting-best-practices)
6. [Tools & Technologies](#tools--technologies)

---

## What is Observability?

**Monitoring**: "Is the system up?"  
**Observability**: "Why is the system behaving this way?"

**Observability** is the ability to understand the internal state of a system by examining its outputs (logs, metrics, traces).

**Key Difference:**
- **Monitoring**: Predefined dashboards, known failure modes
- **Observability**: Debug unknown/novel failures, ask arbitrary questions

---

## The Three Pillars

> **Analogy**: Think of a car's dashboard. **Metrics** are the speedometer and fuel gauge — they tell you the current numbers at a glance (how fast, how full). **Logs** are the car's event data recorder — a timestamped history of everything that happened (engine warning at 2:34pm, door opened at 2:35pm). **Traces** are the GPS route playback — you can replay exactly how a specific journey went, turn by turn, which roads were slow, where you got stuck. Together they answer: What are the numbers? What happened? How did this specific request travel through the system?

---

### 1. Metrics

**What**: Numerical measurements over time

**Examples:**
```
- CPU utilization: 45%
- Request latency (P99): 250ms
- Requests per second: 1,234 QPS
- Error rate: 0.5%
- Database connections: 45/100
```

**Types:**

**Counter** (always increasing)
```java
// http_requests_total{method="GET", status="200"} = 1,234,567
counter.labels("GET", "200").inc();
```

**Gauge** (goes up/down)
```java
// cpu_usage_percent{instance="app-1"} = 67.3
// memory_usage_bytes{instance="app-1"} = 8,589,934,592  (8GB)
gauge.labels("app-1").set(67.3);
```

**Histogram** (distribution)
```java
// http_request_duration_seconds_bucket{le="0.1"} = 9500  // 95% < 100ms
// http_request_duration_seconds_bucket{le="0.5"} = 9900  // 99% < 500ms
// http_request_duration_seconds_bucket{le="1.0"} = 10000 // All requests
histogram.observe(durationSeconds);
```

**Tools:** Prometheus, Grafana, CloudWatch, Datadog

---

### 2. Logs

**What**: Discrete events with context

**Structured Logging (JSON)**
```json
{
  "timestamp": "2026-02-08T16:30:00Z",
  "level": "ERROR",
  "service": "payment-service",
  "trace_id": "abc123",
  "user_id": "user_456",
  "message": "Payment failed: insufficient funds",
  "error": "InsufficientFundsError",
  "amount": 99.99,
  "currency": "USD"
}
```

**Unstructured Logging (Plain Text)**
```
[2026-02-08 16:30:00] ERROR: Payment failed for user_456: insufficient funds
```

**Log Levels:**
```
DEBUG: Detailed debugging (verbose, production disabled)
INFO: General informational messages
WARN: Warning conditions (degraded but functional)
ERROR: Error conditions (handled failures)
FATAL: Critical failure (system crash)
```

**Best Practices:**
- Use structured logging (JSON) for easy parsing
- Include `trace_id` for correlation
- Log user actions, not just errors
- Don't log sensitive data (passwords, PII)
- Don't log too verbosely (log fatigue)

**Tools:** ELK Stack (Elasticsearch, Logstash, Kibana), Splunk, CloudWatch Logs

---

### 3. Distributed Tracing

> **Analogy**: Following a package through FedEx. The tracking number (trace ID) is assigned the moment you drop off the package and follows it through every facility, every scan, every truck. Each scan is a span — it records which facility processed it and how long it stayed. You can open the tracking page and see exactly where the package got delayed: it left Memphis on time but sat at the Chicago hub for 6 hours. Distributed tracing does the same for a request traveling through your microservices.

**What**: Track a single request across multiple services

**Example: Process Order**
```
├─ API Gateway (10ms)
│  └─ Order Service (50ms)
│     ├─ Inventory Service (20ms)
│     │  └─ Database Query (15ms)
│     ├─ Payment Service (30ms)
│     │  └─ External Payment API (25ms)
│     └─ Notification Service (10ms)
│        └─ Email Queue (5ms)
Total: 125ms
```

**Trace Structure:**
```
Trace (abc123)
├─ Span: API Gateway [0-10ms]
└─ Span: Order Service [10-60ms]
   ├─ Span: Inventory Check [10-30ms]
   ├─ Span: Process Payment [30-60ms]
   └─ Span: Send Notification [60-70ms]
```

**Implementation (Java with OpenTelemetry):**
```java
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.Span;

public class OrderService {
    private final Tracer tracer;

    public void processOrder(String userId, double total) {
        Span span = tracer.spanBuilder("process_order").startSpan();
        try (var scope = span.makeCurrent()) {
            span.setAttribute("user_id", userId);
            span.setAttribute("order_total", total);

            checkInventory(productId);
            processPayment(total);
        } finally {
            span.end();
        }
    }

    private void checkInventory(String productId) {
        Span span = tracer.spanBuilder("check_inventory").startSpan();
        try (var scope = span.makeCurrent()) {
            span.setAttribute("product_id", productId);
            inventoryService.check(productId); // trace_id propagated via HTTP header
        } finally {
            span.end();
        }
    }
}
```

**Tools:** Jaeger, Zipkin, AWS X-Ray, Datadog APM

**Benefits:**
- Identify bottlenecks (which service is slow?)
- Debug distributed failures
- Understand dependencies

---

## SLI, SLO, SLA

> **Analogy**: A pizza delivery service. The **SLI** is the actual measured delivery time for every order. The **SLO** is the internal operational goal: "95% of deliveries in under 30 minutes" — this is what the engineering team tracks and gets paged for. The **SLA** is the contract printed on the website: "If your pizza takes over 60 minutes, you get a refund." The SLO is stricter than the SLA so the team catches problems before customers do. SLI ≤ SLO ≤ SLA in terms of strictness.

---

### Service Level Indicator (SLI)

**What**: Quantitative measure of service quality

**Examples:**
```
- Request latency: P99 < 200ms
- Availability: 99.9% uptime
- Error rate: < 0.1%
- Throughput: 10,000 QPS
```

**How to Measure:**
```java
// Availability SLI
double availability = (double) successfulRequests / totalRequests * 100;

// Latency SLI — use a Histogram, read the Nth percentile
// P99 means 99% of requests completed faster than this value
double p99Latency = histogram.percentile(99);
```

---

### Service Level Objective (SLO)

**What**: Target value for an SLI (internal goal)

**Examples:**
```
- 99.9% of requests complete in < 200ms (P99 latency)
- 99.95% uptime per month
- 99.99% of writes succeed
```

**Error Budget:**

> **Analogy**: You have a monthly allowance of 0.1% downtime. Every outage spends from that budget. When it runs out, freeze all risky deployments — the budget is gone, and shipping new features that could cause incidents is irresponsible. This converts reliability into a concrete resource teams can reason about and negotiate over.

```
SLO: 99.9% uptime
Error Budget: 0.1% = 43.8 minutes/month

If you exceed error budget → STOP new features, fix reliability!
```

**Calculation:**
```
Monthly requests: 100M
SLO: 99.9% success
Allowed failures: 100M × 0.001 = 100,000 failures/month
Current failures: 50,000
Remaining budget: 50,000 (50% left)
```

---

### Service Level Agreement (SLA)

**What**: Contractual commitment to customers (legal/financial consequences)

**Examples:**
```
AWS RDS SLA: 99.95% uptime
│
├─ IF uptime < 99.95%: 10% service credit
├─ IF uptime < 99.0%: 25% service credit
└─ IF uptime < 95.0%: 100% service credit
```

**Rule:** SLA < SLO (SLO is stricter — your internal target gives you a buffer before breaching the contract)
```
SLO (internal): 99.99% (stricter)
SLA (customer): 99.9% (what we promise)
Buffer: 0.09% margin for safety
```

---

## Key Metrics to Track

### Application Metrics

**Golden Signals (Google SRE):**

**1. Latency**
```
- P50 (median): 50% of requests < X ms
- P95: 95% of requests < X ms
- P99: 99% of requests < X ms
- P99.9: 99.9% of requests < X ms

Example:
P50: 50ms
P95: 150ms
P99: 250ms
P99.9: 500ms
```

**2. Traffic**
```
- Requests per second (QPS)
- Bytes per second (throughput)

Example: 10,000 QPS, 50 MB/sec
```

**3. Errors**
```
- Error rate: 0.5%
- HTTP 5xx errors: 50 errors/min
- Failed logins: 10/min

Error Budget Burn Rate:
errors_per_hour / allowed_errors_per_month
```

**4. Saturation**
```
- CPU: 75%
- Memory: 80%
- Disk I/O: 60%
- Connection pool: 90/100 (90% saturated)
```

---

### Infrastructure Metrics

**Compute:**
```
- CPU utilization
- Memory usage
- Disk I/O
- Network I/O
```

**Database:**
```
- Query latency
- Connection pool usage
- Replication lag
- Slow query count
```

**Cache:**
```
- Hit rate: 95% (good!)
- Miss rate: 5%
- Eviction rate
- Memory usage
```

---

## Distributed Tracing Deep Dive

### Context Propagation

**HTTP Headers:**
```http
traceparent: 00-abc123def456-span789-01
├─ Version: 00
├─ Trace ID: abc123def456
├─ Parent Span ID: span789
└─ Flags: 01 (sampled)
```

**Propagation:**
```
Service A (creates trace)
└─> HTTP Request (header: traceparent)
    └─> Service B (continues trace)
        └─> HTTP Request (header: traceparent)
            └─> Service C (continues trace)
```

### Sampling

**Problem:** Tracing every request is expensive

**Strategies:**

**1. Head-based Sampling (decide at start)**
```java
// Sample 1% of requests upfront
double sampleRate = 0.01;
if (Math.random() < sampleRate) {
    startTrace(request);
}
```

**2. Tail-based Sampling (decide after completion)**
```java
// Only keep traces for errors or slow requests
if (response.getStatus() == 500 || durationMs > 1000) {
    keepTrace(traceId);
} else {
    discardTrace(traceId);
}
```

**Interview talking point**: "I prefer tail-based sampling for most services: we capture 100% of error traces and slow outliers, which are exactly what you need to debug. Head-based sampling at 1% might miss a rare but critical failure pattern."

---

## Alerting Best Practices

> **Analogy**: A car alarm going off in a parking lot. After the first few times, everyone ignores it. The alarm lost all signal value because it fires for nothing — a gust of wind, a passing truck. The same happens with software alerts: too many low-signal alerts train engineers to ignore all alerts, including real incidents. The fix is to alert only on things a human must act on right now.

### Alert Fatigue

**Problem:** Too many alerts → ignored  
**Solution:** Alert only on actionable, high-priority issues

### Good Alert vs Bad Alert

**Bad Alert:**
```
ALERT: Disk usage > 50%
→ Not actionable, happens daily, ignored
```

**Good Alert:**
```
ALERT: Disk usage > 90%, will be full in 2 hours
→ Actionable, urgent, specific action needed
```

### Alert Severity Levels

**P0 (Critical):** Page on-call immediately
```
- Service down (availability < 99%)
- Data loss
- Security breach
Example: Payment processing failing
```

**P1 (High):** Alert during business hours
```
- Degraded performance (latency 2× SLO)
- Error rate spike
Example: API latency P99 > 500ms (SLO: 200ms)
```

**P2 (Medium):** Create ticket
```
- Resource usage trending up
- Non-critical feature broken
Example: Disk will be full in 7 days
```

**P3 (Low):** Optional notification
```
- Informational
Example: Cache hit rate dropped from 95% to 90%
```

### On-Call Runbooks

**Example Runbook for "High Error Rate"**
```
Alert: Error rate > 1% for 5 minutes

1. Check dashboard: https://grafana.example.com/api-errors
2. Identify error type (500, 503, timeout)
3. Check logs: kubectl logs -f api-deployment --since=10m
4. Common causes:
   - Database connection pool exhausted → Scale DB
   - Downstream service down → Check dependencies
   - Bad deployment → Rollback: kubectl rollout undo
5. Escalate to: @backend-team if unresolved in 15 min
```

---

## Tools & Technologies

### Metrics

**Prometheus**
```
Use when: Open-source, Kubernetes native
Pros: Pull-based, PromQL query language, free
Cons: Limited long-term storage
```

**Datadog**
```
Use when: SaaS, want everything integrated
Pros: All-in-one (metrics + logs + traces), easy setup
Cons: Expensive at scale
```

**CloudWatch**
```
Use when: AWS-only infrastructure
Pros: Native AWS integration, low cost
Cons: Limited features vs dedicated tools
```

### Logging

**ELK Stack** (Elasticsearch + Logstash + Kibana)
```
Use when: Need powerful search, self-hosted
Pros: Flexible, powerful queries
Cons: Complex to manage, resource-intensive
```

**Splunk**
```
Use when: Enterprise, budget available
Pros: Powerful analytics, mature
Cons: Very expensive
```

### Tracing

**Jaeger**
```
Use when: Open-source, Kubernetes
Pros: Free, CNCF project, good community
```

**Datadog APM**
```
Use when: Want integrated solution
Pros: Automatic instrumentation, correlated with metrics/logs
Cons: Cost
```

---

## Real-World Example: E-commerce Checkout

### Metrics to Track

```
- Checkout success rate: 99.5% (SLO: 99%)
- Checkout latency P99: 800ms (SLO: 1000ms)
- Payment failures: 50/hour
- Cart abandonment rate: 15%
```

### Distributed Trace

```
Trace: Checkout (trace_id: xyz789)
├─ API Gateway: 5ms
└─ Checkout Service: 500ms
   ├─ Validate Cart: 50ms (DB query)
   ├─ Check Inventory: 100ms (Inventory Service)
   ├─ Process Payment: 300ms (Stripe API) ← SLOW!
   └─ Create Order: 50ms (DB write)
```

**Finding:** Payment processing is slow (300ms). Action: Optimize Stripe API calls or use async processing.

### Alert

```
ALERT (P1): Checkout success rate dropped to 95% (SLO: 99%)

Runbook:
1. Check trace: High failure rate in "Process Payment" span
2. Check logs: "Stripe API timeout errors"
3. Action: Switch to backup payment processor OR scale Stripe API timeout
```

---

## Interview Tips

**When discussing observability:**

1. **Mention the 3 pillars**: Metrics, Logs, Traces
2. **Define SLI/SLO/SLA** with examples
3. **Golden Signals**: Latency, Traffic, Errors, Saturation
4. **Alert on symptoms, not causes**: "Error rate high" not "CPU high"
5. **Error budgets**: "If SLO is 99.9%, we have 43.8 min downtime/month"

**Example Answer:**
> "For this payment system, I'd track **SLIs** like transaction success rate (target: 99.99%) and latency (P99 < 500ms). Use **Prometheus** for metrics, **Jaeger** for distributed tracing to debug cross-service issues. Implement **structured logging** with trace IDs for correlation. Set up **alerts** on SLO violations, like success rate < 99.9% for 5 minutes. Maintain an **error budget**: if we burn through it, stop new features and focus on reliability."

---

## Senior Engineer Insights

- **Design trade-offs**: More metrics and traces improve debuggability but add cost and noise. Define SLIs that reflect user impact (e.g. success rate, latency), not just internal metrics (e.g. CPU). SLOs should be achievable but strict enough to force investment in reliability.
- **Cost**: High-cardinality metrics and long retention are expensive. Sample traces (e.g. 1% or tail-based for errors/slow); aggregate metrics; set retention policies. Use error budgets to decide when to invest in reliability vs features.
- **Operational complexity**: Centralized logging and tracing require pipelines (agents, collectors, storage). Alert fatigue kills response; alert on symptoms (e.g. error rate) with runbooks; avoid alerting on every possible cause.
- **Deployment**: Feature flags and canaries need observability (latency, errors by version). Correlate deployments with metric changes; use trace IDs across service boundaries for debugging.
- **Resilience**: Observability itself must be resilient: buffers (queues) for log/trace ingestion so backpressure doesn't kill the app; sampling under load so tracing doesn't add significant latency.

---

## Quick Revision

- **Three pillars**: Metrics (numbers over time), Logs (discrete events), Traces (request across services). Use all three; correlate with trace_id.
- **SLI/SLO/SLA**: SLI = measurable indicator; SLO = target (internal); SLA = contract (external). SLO stricter than SLA; error budget = 1 − SLO.
- **Golden signals**: Latency, Traffic, Errors, Saturation. Alert on symptoms (e.g. error rate high), not only causes (e.g. CPU high).
- **Interview talking points**: "We track SLIs (success rate, P99 latency), set SLOs and error budgets. We use Prometheus for metrics, structured logs with trace_id, and Jaeger for tracing. We alert on SLO burn rate and have runbooks for common failures."
- **Common mistakes**: Too many alerts (fatigue); no error budget; tracing everything at 100% (cost); logging PII or secrets.
