# Observability: Logging, Metrics & Monitoring

> **Source**: Videos #10, #46, #57 from the playlist
> - FAANG System Design Interview: Design A Logging System
> - How Big Tech Ships Code to Production
> - Caching Pitfalls Every Developer Should Know

---

## Observability Pillars

### 1. Logs
- **What**: Timestamped text records of events
- **Types**: Application logs, access logs, error logs, audit logs
- **Format**: Structured (JSON) preferred over unstructured

```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "level": "ERROR",
  "service": "payment-service",
  "request_id": "req-abc-123",
  "message": "Payment failed",
  "user_id": "user_456",
  "error": "Insufficient funds"
}
```

### 2. Metrics
- **What**: Numerical values measured over time
- **Types**: Counter (total requests), Gauge (CPU %), Histogram (latency distribution)
- **Use**: Dashboards, alerting, capacity planning

### 3. Traces (Distributed Tracing)
- **What**: End-to-end path of a request through services
- **Shows**: Which services were called, latency at each step, where failures occurred
- **Tools**: Jaeger, Zipkin, OpenTelemetry

```
Request → API Gateway (5ms) → Auth Service (10ms) → Payment Service (50ms) → DB (20ms)
Total: 85ms
```

---

## Logging System Design

### Architecture
```
Services → Log Agents → Message Queue (Kafka) → Processing (Flink/Spark)
                                                        ↓
                                                 Storage (Elasticsearch/S3)
                                                        ↓
                                                 Visualization (Kibana/Grafana)
```

### ELK Stack (Most Popular)
- **Elasticsearch**: Store and search logs
- **Logstash**: Ingest, parse, and transform logs
- **Kibana**: Visualize and explore logs

### Key Design Decisions
| Decision | Options |
|---|---|
| **Collection** | Agent-based (Fluentd, Filebeat) vs direct SDK |
| **Transport** | Kafka (buffered) vs direct to storage |
| **Storage** | Elasticsearch (searchable) + S3 (archival) |
| **Retention** | Hot (7 days, SSD) → Warm (30 days) → Cold (S3, years) |
| **Sampling** | Log everything vs sample high-volume logs |

---

## Monitoring & Alerting

### The Four Golden Signals (Google SRE)
1. **Latency**: Time to serve a request (P50, P95, P99)
2. **Traffic**: Requests per second
3. **Errors**: Error rate (5xx responses)
4. **Saturation**: How "full" the system is (CPU, memory, disk)

### Alerting Best Practices
- Alert on **symptoms** not causes (high error rate, not high CPU)
- Use **severity levels** (critical, warning, info)
- **Runbooks**: Document what to do for each alert
- Avoid **alert fatigue**: Too many alerts = ignored alerts
- **Dashboards**: Grafana for visual monitoring

### Key Monitoring Tools
| Tool | Purpose |
|---|---|
| **Prometheus** | Metrics collection and querying |
| **Grafana** | Dashboards and visualization |
| **Datadog** | Full observability platform (SaaS) |
| **PagerDuty** | Incident management and alerting |
| **New Relic** | APM (Application Performance Monitoring) |
