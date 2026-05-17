# Cloud Services Cheat Sheet

> **AWS vs GCP vs Azure service equivalents — know the right tool in any cloud.**

---

## Compute

| Category | AWS | GCP | Azure | Notes |
|----------|-----|-----|-------|-------|
| Virtual Machines | EC2 | Compute Engine | Azure VMs | AWS dominates market share |
| Container (Managed K8s) | EKS | GKE | AKS | GKE is most mature |
| Serverless Functions | Lambda | Cloud Functions | Azure Functions | Same concept, different limits |
| Serverless Containers | Fargate | Cloud Run | Container Apps | Cloud Run = best DX |
| Batch Processing | AWS Batch | Cloud Batch | Azure Batch | |
| VM Auto-Scaling | EC2 Auto Scaling Group | Managed Instance Group | VM Scale Set | |

---

## Storage

| Category | AWS | GCP | Azure | Notes |
|----------|-----|-----|-------|-------|
| Object Storage | S3 | GCS | Azure Blob Storage | S3 is the gold standard |
| Block Storage (SSD) | EBS | Persistent Disk | Azure Disk | EBS gp3: 16K IOPS, 1TB |
| File Storage (NFS) | EFS | Filestore | Azure Files | |
| Archive/Cold Storage | S3 Glacier | Archive Storage | Archive Tier | Retrieval: hours to days |
| Local NVMe (ephemeral) | EC2 Instance Store | Local SSD | Temp Disk | High IOPS, data lost on stop |

**S3 key properties:**
```
Durability: 99.999999999% (11 nines) — 3+ AZ replication
Availability: 99.99% standard tier
Eventual consistency: Object updates/deletes are eventually consistent
Latency: first-byte-out ~100-200ms (not suitable for sub-50ms workloads)
Use for: Static assets, backups, data lake, model artifacts
```

---

## Databases

### Relational (OLTP)

| AWS | GCP | Azure | Notes |
|-----|-----|-------|-------|
| RDS PostgreSQL/MySQL | Cloud SQL | Azure Database for PostgreSQL | Managed, easy ops |
| Aurora PostgreSQL | AlloyDB | — | 3-5× faster than RDS; Aurora is AWS-proprietary |
| Aurora Serverless v2 | AlloyDB Omni | Hyperscale (Citus) | Auto-scales compute |

**Aurora vs RDS:**
```
Aurora: Storage shared across 6 copies in 3 AZs, auto-grows to 128TB
        Read replicas: up to 15 (vs 5 for RDS)
        Failover: < 30 seconds (vs ~1-2 minutes for RDS)
        Cost: ~20% more than RDS

RDS: Standard single-server Postgres/MySQL with managed ops
     Use when: don't need Aurora's scale, want lower cost
```

### Key-Value & Wide-Column NoSQL

| AWS | GCP | Azure | Notes |
|-----|-----|-------|-------|
| DynamoDB | Firestore/Bigtable | Cosmos DB | All serverless/managed |
| ElastiCache (Redis) | Memorystore (Redis) | Azure Cache for Redis | Managed Redis |
| ElastiCache (Memcached) | Cloud Memorystore | — | |

**DynamoDB key properties:**
```
Capacity: On-Demand (pay per request) or Provisioned (WCU/RCU)
Single-digit ms latency at any scale
Global Tables: multi-region active-active replication
Limits: Item max 400KB, GSI/LSI limits apply
Partition key: Choose carefully — hot partitions = performance cliff
  Bad: createdAt as partition key (all writes to same partition)
  Good: userId (high cardinality, even distribution)
DAX: DynamoDB Accelerator — in-memory cache, microsecond reads
```

**Bigtable (GCP):**
```
Wide-column store (like HBase, which is based on Google Bigtable paper)
Row key: single indexed key — choose for scan patterns
Column families: group related columns
Use for: time-series, IoT sensor data, analytics (billions of rows)
Latency: < 10ms for row reads
Not for: ad-hoc queries (no secondary indexes), small datasets
```

### Document

| AWS | GCP | Azure | Notes |
|-----|-----|-------|-------|
| DocumentDB | Firestore | Cosmos DB (MongoDB API) | |
| DynamoDB (can be used as document store) | | | |

---

## Messaging & Streaming

| Category | AWS | GCP | Azure | Notes |
|----------|-----|-----|-------|-------|
| Message Queue | SQS | Cloud Tasks / Pub/Sub | Azure Service Bus | SQS: simple queue |
| Pub/Sub | SNS | Pub/Sub | Azure Event Grid | Fan-out |
| Event Streaming | Kinesis Data Streams | Pub/Sub | Azure Event Hubs | Kafka-compatible options |
| Managed Kafka | MSK (Managed Streaming for Kafka) | Confluent on GCP | Azure Event Hubs (Kafka protocol) | |

**SQS vs SNS vs Kinesis:**
```
SQS (Queue):
  At-least-once delivery, no ordering (Standard) or FIFO with ordering
  Max message size: 256KB
  Retention: up to 14 days
  Use: job queues, decoupling services, task distribution

SNS (Pub/Sub fanout):
  Push to multiple SQS queues / Lambda / HTTP endpoints simultaneously
  No persistence — if subscriber is down, message is lost
  Use: notifications, fanout to multiple consumers

Kinesis (Streaming):
  Ordered, partitioned stream (like Kafka)
  Replay data (24h default, up to 365 days with extended retention)
  Multiple consumers can read independently (like Kafka consumer groups)
  Use: real-time analytics, event sourcing, CDC
```

---

## Networking & CDN

| Category | AWS | GCP | Azure | Notes |
|----------|-----|-----|-------|-------|
| CDN | CloudFront | Cloud CDN | Azure CDN / Front Door | CloudFront + Lambda@Edge for edge compute |
| Load Balancer (L7 HTTP) | ALB | Cloud Load Balancing | Azure Application Gateway | |
| Load Balancer (L4 TCP) | NLB | Cloud Load Balancing | Azure Load Balancer | Ultra-low latency |
| API Gateway | API Gateway / AppSync | Apigee | Azure API Management | |
| Private Network | VPC | VPC | Virtual Network | |
| DNS | Route 53 | Cloud DNS | Azure DNS | Route 53: health checks + GeoDNS |
| VPN / Private Connect | Direct Connect / PrivateLink | Cloud Interconnect | ExpressRoute | |

**CloudFront key configs:**
```
Origins: S3, ALB, EC2, custom HTTP server
Cache behaviors: route by path pattern (/api/* → no cache, /static/* → cache 1 year)
Lambda@Edge: run code at 400+ PoPs globally (auth, URL rewriting, A/B testing)
Cache key: customize by headers, query strings, cookies
Price class: All (world) / 200 (most) / 100 (cheapest, US + Europe only)
```

---

## Security & Identity

| Category | AWS | GCP | Azure | Notes |
|----------|-----|-----|-------|-------|
| Identity & Access Management | IAM | Cloud IAM | Azure RBAC + AAD | |
| Secret Management | Secrets Manager / Parameter Store | Secret Manager | Key Vault | |
| Key Management | KMS | Cloud KMS | Azure Key Vault | |
| Certificate Management | ACM | Certificate Manager | App Service Certificate | |
| Web Application Firewall | WAF (via ALB/CloudFront) | Cloud Armor | Azure WAF | |
| DDoS Protection | Shield Standard / Advanced | Cloud Armor | DDoS Protection | |

**AWS IAM best practices:**
```
Least privilege: grant minimum permissions needed
Roles over users: EC2/Lambda assume roles (no long-lived credentials)
Resource-based policies: S3 bucket policies, SQS resource policies
Condition keys: restrict by source IP, MFA required, time of day
Service Control Policies (SCPs): org-wide guardrails
```

---

## Observability & Monitoring

| Category | AWS | GCP | Azure | Notes |
|----------|-----|-----|-------|-------|
| Metrics | CloudWatch | Cloud Monitoring | Azure Monitor | |
| Distributed Tracing | X-Ray | Cloud Trace | Application Insights | |
| Logging | CloudWatch Logs | Cloud Logging | Log Analytics | |
| Alerting | CloudWatch Alarms | Cloud Alerting | Azure Alerts | |
| Dashboards | CloudWatch Dashboards | Cloud Monitoring | Azure Dashboards | |

---

## ML & Data

| Category | AWS | GCP | Azure | Notes |
|----------|-----|-----|-------|-------|
| ML Platform | SageMaker | Vertex AI | Azure ML | |
| Data Warehouse | Redshift | BigQuery | Azure Synapse | BigQuery: serverless, pay-per-query |
| Data Lake | S3 + Athena | GCS + BigQuery | Azure Data Lake + Synapse | |
| Stream Processing | Kinesis Data Analytics | Dataflow | Azure Stream Analytics | |
| Batch ETL | Glue | Dataflow | Azure Data Factory | |
| LLM APIs | Amazon Bedrock | Vertex AI (Gemini) | Azure OpenAI | |

**BigQuery key properties:**
```
Serverless: no cluster to manage
Price: $5/TB scanned (columnar — only scans relevant columns)
Latency: seconds for GB-scale, minutes for TB-scale queries
Partitioning: partition by date column for cost/performance
Clustering: sort within partitions for faster filtering
Streaming inserts: < 1 second freshness (vs batch load: 1-2 minutes)
Best for: Ad-hoc analytics, data exploration, reporting on large datasets
```

---

## Service Selection Decision Matrix

```
Workload → Service

High-throughput writes (100K+/sec, flexible schema):
  → DynamoDB (AWS) / Bigtable (GCP) / Cosmos DB (Azure)

ACID transactions, complex queries, relational:
  → Aurora PostgreSQL (AWS) / AlloyDB (GCP) / Azure PostgreSQL

Global strong consistency with SQL:
  → Google Spanner / CockroachDB (multi-cloud) / Azure Cosmos DB (strong consistency mode)

Real-time leaderboards, session storage, caching:
  → ElastiCache Redis / Memorystore / Azure Cache for Redis

Event streaming with replay:
  → Kinesis / Pub/Sub / Event Hubs (or managed Kafka on any cloud)

Full-text search:
  → OpenSearch (AWS managed Elasticsearch) / Elasticsearch (self-managed) / Azure Cognitive Search

Object storage with global CDN:
  → S3 + CloudFront / GCS + Cloud CDN / Azure Blob + CDN

Serverless HTTP APIs:
  → Lambda + API Gateway / Cloud Run / Container Apps
```
