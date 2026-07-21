> [!NOTE]
> ** 5-Minute Summary**
>
> **What this covers:** How engineers debug failures in distributed microservices — the Three Pillars of Observability (Logs, Metrics, Traces) and how to wire them together with OpenTelemetry.
>
> **Key topics:**
> - Why debugging a microservice failure is like finding a broken link in a chain
> - Logs: structured events at a point in time
> - Metrics: numerical measurements over time (the graphs you watch)
> - Traces: end-to-end request journeys across multiple services
> - OpenTelemetry: the industry standard for capturing all three
> - Practical: how to debug a P0 incident using traces + logs
>
> **Key takeaway:** Distributed Tracing with a tool like Jaeger/Tempo is the defining skill that separates a senior engineer from a junior one. If something goes wrong at 3 AM, traces tell you exactly which service and which line of code caused a 2,000ms latency spike.

---
module: 04-advanced-topics/02-system-reliability
status: unread
tags: [observability, tracing, telemetry, opentelemetry, jaeger, prometheus, monitoring]
---

# Distributed Tracing & Telemetry — System Design Guide

## Why Should I Care?

Imagine your monolith breaks. You SSH in, look at one log file, find the error. Done.

Now imagine your microservice architecture breaks. A user reports that checking out takes 8 seconds. Your order flows through: **API Gateway → Auth Service → Order Service → Inventory Service → Payment Service → Notification Service**.

Which service is slow? Where is the bottleneck? A log file from the Order Service tells you nothing about what the Payment Service did.

This is the core problem that **Distributed Tracing** solves. It's what allows Netflix, Uber, and Google to debug failures across hundreds of services — and it's a senior engineer's most powerful debugging tool.

---

## The Three Pillars of Observability

A system is **observable** if you can answer any question about its internal state by examining its outputs. There are exactly three types of outputs that matter:

```
┌─────────────────────────────────────────────────────────────────────┐
│                    OBSERVABILITY                                     │
│                                                                     │
│   LOGS          METRICS          TRACES                       │
│  "What happened" "How much/fast"    "How did it flow"               │
│  at this moment  over time          across services                 │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Pillar 1: Logs — "The Event Journal"

**Analogy:** A ship's log book. Every significant event gets an entry with a timestamp. If something goes wrong, you read the log book to reconstruct what happened.

A log is a **timestamped, immutable record of a discrete event**.

### Structured vs Unstructured Logs

**Bad (unstructured):** Hard to query at scale.
```
2024-01-15 14:23:01 ERROR Failed to process order for user 42 - timeout after 5000ms
```

**Good (structured JSON):** Queryable, filterable, joinable.
```json
{
  "timestamp": "2024-01-15T14:23:01.234Z",
  "level": "ERROR",
  "service": "order-service",
  "trace_id": "abc123def456",
  "span_id": "7890ghij",
  "user_id": 42,
  "order_id": "ord_9876",
  "event": "order_processing_failed",
  "error": "timeout",
  "duration_ms": 5000,
  "downstream": "payment-service"
}
```

The `trace_id` field is the critical link — it lets you find all log lines from all services that participated in a single user request.

### Log Levels (Standard)

| Level | When to use | Example |
|---|---|---|
| `DEBUG` | Detailed dev debugging. Never in prod. | "Entering calculateTotal() with 3 items" |
| `INFO` | Normal business events | "Order ord_9876 created for user 42" |
| `WARN` | Unexpected but handled | "Payment retry attempt 2/3 for order ord_9876" |
| `ERROR` | Failures that need investigation | "Payment failed after 3 retries — order suspended" |
| `FATAL` | System is in unrecoverable state | "Database connection pool exhausted — shutting down" |

### Production Log Stack

```
Services (emit JSON logs)
    ↓
Log Shipper (Fluentd / Filebeat)
    ↓
Log Aggregator (Elasticsearch / Loki)
    ↓
Visualization (Kibana / Grafana)
    ↓
Alerting (PagerDuty when ERROR rate spikes)
```

---

## Pillar 2: Metrics — "The Dashboard Gauges"

**Analogy:** The gauges on a car dashboard. Speedometer, fuel gauge, temperature — all give you aggregated, numerical summaries. You don't read the engine's event log to know you're going 60 mph.

A metric is a **numerical measurement sampled over time**.

### The 4 Metric Types (RED + USE)

**RED Method (for request-driven services):**
| Metric | What it measures | Example |
|---|---|---|
| **R**ate | Requests per second | `order_service_requests_total` |
| **E**rror | Failed requests / total | `order_service_errors_total / order_service_requests_total` |
| **D**uration | Request latency distribution | `order_service_request_duration_p99` |

**USE Method (for resources):**
| Metric | What it measures | Example |
|---|---|---|
| **U**tilization | % resource in use | `cpu_utilization = 78%` |
| **S**aturation | Queue depth / backlog | `thread_pool_queue_depth = 450` |
| **E**rrors | Error count | `disk_io_errors_total` |

### SLI, SLO, SLA — The Three Tiers of Promise

```
SLI (Service Level Indicator)    — The actual measurement
    "Our p99 latency is 180ms"

SLO (Service Level Objective)    — The internal target
    "We aim for p99 latency < 200ms, 99.9% of time"

SLA (Service Level Agreement)    — The contractual promise to customers
    "We guarantee 99.9% uptime. Violation = service credits"
```

**Real SLI calculation:**
```
Availability SLI = (successful requests / total requests) × 100

If out of 1,000,000 requests, 999,100 succeeded:
SLI = (999,100 / 1,000,000) × 100 = 99.91%
This meets a 99.9% SLO
```

### Production Metrics Stack

```
Services (instrument with Prometheus client libraries)
    ↓
Prometheus (scrapes /metrics endpoint every 15s)
    ↓
Alertmanager (fires PagerDuty when SLO breached)
    ↓
Grafana (dashboards — the graphs engineers watch)
```

---

## Pillar 3: Distributed Tracing — "The Request's Journey Map"

**Analogy:** UPS package tracking. When you ship a package, it gets a tracking number. Every time it's scanned — warehouse → truck → sorting facility → delivery truck → your door — a record is created. At the end, you have a complete timeline of the package's journey.

Distributed tracing does exactly this for **a user's request as it flows across microservices**.

### Core Concepts

**Trace:** The complete end-to-end record of a single request, from entry to exit. Identified by a globally unique `trace_id`.

**Span:** A single unit of work within a trace (e.g., one service's processing, one DB query). Identified by a `span_id`. Spans can be nested (parent-child relationships).

```
TRACE: trace_id = "abc123"
│
├── SPAN: API Gateway (0ms → 5ms)
│   └── SPAN: Auth Service (1ms → 12ms)
│       └── SPAN: Redis lookup (2ms → 3ms)
│
├── SPAN: Order Service (12ms → 450ms)
│   ├── SPAN: DB query — read product (15ms → 25ms)
│   └── SPAN: Payment Service (30ms → 440ms) ←  BOTTLENECK
│       ├── SPAN: Stripe API call (35ms → 430ms) ← ROOT CAUSE
│       └── SPAN: DB write — save payment (430ms → 438ms)
│
└── SPAN: Notification Service (450ms → 460ms)
```

In this trace, the Stripe API call took **395ms** — that's your root cause.

### How Context Propagation Works

Each service passes the trace context to the next service via HTTP headers:

```
API Gateway → Order Service:
  Header: traceparent: 00-abc123def456-7890abcd-01
                           ↑              ↑        ↑
                        trace_id       parent_id  flags

Order Service → Payment Service:
  Header: traceparent: 00-abc123def456-ef123456-01
                           ↑              ↑
                     same trace_id   new span_id
```

The `trace_id` stays the same. The `span_id` changes for each service.

---

## OpenTelemetry — The Industry Standard

Previously, every tracing tool (Zipkin, Jaeger, Datadog, New Relic) had its own SDK. Switching providers meant rewriting all your instrumentation code.

**OpenTelemetry (OTel)** is the CNCF-backed open standard that provides a single, vendor-neutral SDK for all three pillars:

```
Your Service Code
   ↓
OpenTelemetry SDK (instrument once)
   ↓
OTel Collector (receives, processes, exports)
   ↓
   ├── Traces → Jaeger / Tempo / Datadog
   ├── Metrics → Prometheus / Datadog
   └── Logs → Elasticsearch / Loki / Datadog
```

### Code Example — Instrumenting a Service

```python
from opentelemetry import trace
from opentelemetry.sdk.trace import TracerProvider

tracer = trace.get_tracer("order-service")

def process_order(order_id: str, user_id: int):
    with tracer.start_as_current_span("process_order") as span:
        span.set_attribute("order.id", order_id)
        span.set_attribute("user.id", user_id)

        try:
            product = fetch_product(order_id)         # child span auto-created
            payment = charge_payment(user_id, product) # child span auto-created
            span.set_attribute("payment.status", "success")
            return {"status": "confirmed"}
        except PaymentException as e:
            span.record_exception(e)
            span.set_status(trace.StatusCode.ERROR, str(e))
            raise
```

The `with tracer.start_as_current_span(...)` block automatically:
- Creates a span with a start timestamp.
- Injects the trace context into outgoing HTTP calls.
- Records the end timestamp and any exceptions.

### Popular Tooling

| Tool | Type | Use Case |
|---|---|---|
| **Jaeger** | Open source traces | Self-hosted distributed tracing |
| **Grafana Tempo** | Open source traces | Integrates tightly with Grafana stack |
| **Zipkin** | Open source traces | Simpler, older, still common |
| **Prometheus** | Open source metrics | The de facto standard metrics backend |
| **Grafana** | Visualization | Dashboards for metrics + traces + logs |
| **Datadog** | Commercial all-in-one | Fully managed, expensive, powerful |
| **New Relic** | Commercial all-in-one | Similar to Datadog |

---

## Correlating Logs, Metrics, and Traces

The true power of observability is connecting all three:

```
1. METRICS alert fires:
   "Order service p99 latency > 500ms for last 5 minutes" → PagerDuty page

2. Engineer opens GRAFANA:
   Sees latency spike at 02:34 AM. Error rate also spiked.

3. Drills into TRACES (Tempo/Jaeger):
   Filters traces > 500ms at 02:34 AM.
   Finds trace_id: "abc123" taking 2,400ms.
   Sees: Payment Service span took 2,100ms.

4. Drills into LOGS (Elasticsearch):
   Queries: trace_id="abc123" AND service="payment-service"
   Finds: "Stripe webhook timeout after 2000ms. Retry 3/3."

5. Root cause found in 3 minutes:
   Stripe had a partial outage. Retries added 2s per request.
   Fix: reduce timeout from 2000ms to 500ms, use circuit breaker.
```

---

## Production Observability Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                          SERVICES                                   │
│  [Order Svc] [Payment Svc] [Notification Svc] [Auth Svc]           │
│   ↓ OTel SDK   ↓ OTel SDK    ↓ OTel SDK        ↓ OTel SDK          │
└─────────────────────────────────────────────────────────────────────┘
                         ↓ OTLP Protocol
┌─────────────────────────────────────────────────────────────────────┐
│                     OTel COLLECTOR                                  │
│          (receive → process → batch → export)                       │
└─────────────────────────────────────────────────────────────────────┘
       ↓ Traces          ↓ Metrics         ↓ Logs
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│    Jaeger    │  │  Prometheus  │  │     Loki      │
│   (traces)   │  │  (metrics)   │  │    (logs)     │
└──────────────┘  └──────────────┘  └──────────────┘
       ↓                ↓                  ↓
┌─────────────────────────────────────────────────────────────────────┐
│                       GRAFANA                                       │
│         (unified dashboards, alerts, on-call integration)           │
└─────────────────────────────────────────────────────────────────────┘
                         ↓ Alerts
                  ┌──────────────┐
                  │  PagerDuty   │
                  └──────────────┘
```

---

## Common Interview Questions

**Q: How would you debug a slow API in a microservices architecture?**
A: First, check metrics (Prometheus/Grafana) to identify the time window and which service's latency/error rate spiked. Then pull distributed traces (Jaeger) from that time window filtered by high latency. The trace's span breakdown shows exactly which service and operation was slow. Finally, pull structured logs for that `trace_id` to get the error details.

**Q: What's the difference between a metric and a log?**
A: Metrics are aggregated numerical measurements sampled over time (e.g., "request rate = 1,200 rps"). Logs are discrete, immutable records of individual events ("user 42 failed login at 14:23:01"). Metrics tell you *something is wrong*. Logs tell you *what specifically happened*.

**Q: What is OpenTelemetry and why does it matter?**
A: OTel is a vendor-neutral, open-source standard for instrumenting applications to emit traces, metrics, and logs. Before OTel, you'd use different SDKs for Datadog, Jaeger, and Prometheus. With OTel, you instrument once and can switch backends without changing application code.

**Q: What is a `trace_id` and why is it important?**
A: A globally unique identifier assigned to a single user request at the entry point (API Gateway). Every service logs this ID with every log line and span. It's the "correlation key" that lets you find all logs and trace spans from a single request, even across 10 different services.

---

> [!TIP]
> **Senior Engineer Signal**
> Mentioning "distributed tracing with OpenTelemetry" in a system design interview is an immediate senior-level signal. It demonstrates you understand that building a system is only half the job — **you also need to be able to debug it at 3 AM**.

> [!TIP]
> **Quick Stack Cheat Sheet**
> - **Traces** → Jaeger (open source) or Datadog APM (commercial)
> - **Metrics** → Prometheus + Grafana (open source) or Datadog (commercial)
> - **Logs** → Elasticsearch + Kibana (ELK) or Grafana Loki + Grafana
> - **All three, vendor-neutral collection** → OpenTelemetry Collector

---

## Applied In

This concept is used by **1 problem** in this repo:

**High-Level Design**

- [Design a Metrics Monitoring System (Prometheus + Grafana)](../../05-hld-problems/03-hard/metrics-monitoring-system.md)

