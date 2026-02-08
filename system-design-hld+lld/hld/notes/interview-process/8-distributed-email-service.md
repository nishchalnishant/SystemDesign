# #8 Distributed email service

Below is a complete, time-boxed, interview-ready (1 hour) answer for designing a Distributed Email Service (think SendGrid / Mailgun / SES style). It follows your pattern: clarify → FR/NFR → APIs & schema → high-level architecture & data flow → deep dives (delivery, retries, spam & deliverability) → scaling/BoE → ops/security → trade-offs & wrap-up. Use this as a script in a system design interview.

***

## 0 – 5 min — Problem recap, scope & assumptions

<br>

Start by confirming goals and constraints.

<br>

Goal: Build a distributed email platform that accepts email send requests (API/SMTP), reliably delivers transactional and marketing emails to recipients worldwide, ingests inbound email (for replies/bounces), tracks engagement (opens/clicks), handles bounces/spam complaints, supports templates/scheduling, and exposes dashboards/webhooks and billing.

<br>

Key capabilities:

* High deliverability (good reputation, DKIM/SPF/DMARC).
* Scalable ingestion and sending with per-customer rate limits.
* Accurate tracking and reporting (opens, clicks, bounces).
* Support SMTP + REST API + Web UI + webhooks.
* Multi-tenant with per-tenant quotas, suppression lists, and dedicated IPs.

<br>

Sample assumptions (adjustable):

* 10M emails/day total (≈116 emails/sec avg). Peak bursts up to 100k emails/min (≈1.7k/sec).
* Mix: 70% transactional (low-latency), 30% marketing (high volume, schedule-able).
* Retention: event logs 30–90 days; raw content 7–30 days depending on privacy.
* SLA: transactional email P95 send-latency ≤ 2s to accept; delivery latency depends on recipient MTAs.

***

## 5 – 15 min — Functional & Non-functional requirements

<br>

### Functional Requirements (Must / Should / Nice)

<br>

Must

1. Accept sends via REST API and SMTP (support attachments, headers, templating, personalization).
2. Queue & deliver to recipient MTAs via SMTP, respecting recipient domains’ limits and MX preferences.
3. Inbound processing: accept incoming mail for customer domains (webhooks, mailbox forwarding) and parse bounces/complaints.
4. Delivery tracking: detect bounces, spam complaints, successful deliveries, and report statuses.
5. Engagement tracking: opens (pixel) and click tracking (redirect) with privacy options.
6. Template & scheduling: store templates, support send\_at / schedule / cadence / batch sends.
7. Suppression management: global and tenant-level unsubscribe/suppression lists.
8. Webhooks & APIs: push events (delivered, bounced, opened, clicked) to customers.
9. Rate limits & throttling: per-tenant, per-IP, per-domain.
10. Monitoring & dashboarding: metrics, delivery health, reputational signals.

<br>

Should

* Dedicated IP pools and IP warm-up workflow.
* Advanced deliverability features: domain warm-up, bounce classification, auto-retry optimization.
* Templates with A/B testing & personalization tokens.

<br>

Nice-to-have

* Built-in suppression heuristics (spam detection), ML-based deliverability recommendations, multi-channel fallback (SMS).

<br>

### Non-Functional Requirements

* Performance: Accept API/SMPP quickly (P95 < 2s). Queueing/delivery throughput scales horizontally.
* Availability: 99.95% for API and ingestion; best-effort for outbound delivery (depends on external MTAs).
* Durability: persisted events and queues with replication. No data loss for accepted sends.
* Scalability: handle spikes (marketing blasts) and many small transactional requests.
* Security & Privacy: TLS everywhere, per-tenant keys, PCI/PII rules, GDPR-safe deletion.
* Compliance: support unsubscribe headers, CAN-SPAM, and provide audit logs.
* Observability: detailed metrics (send rate, bounce rate, complaint rate), alerting on reputation drops.

***

## 15 – 25 min — APIs, event schema & UX flows

<br>

### External APIs (surface)

<br>

REST API (JSON)

* POST /v1/send — send single email or small batch (body: from, to\[], subject, html/text, headers, template\_id, personalizations, send\_at, dedupe\_id).
* POST /v1/send/batch — upload large mailing job (returns job\_id).
* GET /v1/status/{message\_id} — message status.
* POST /v1/templates — create template.
* GET /v1/metrics?start=\&end=\&tenant= — aggregated metrics.
* POST /v1/suppressions — add suppression entry.
* GET /v1/webhooks/config — manage webhooks.

<br>

SMTP

* Allow SMTP relay for legacy clients (auth, TLS, per-sender quotas). Accept and translate into internal message objects.

<br>

Webhooks

* Push events: delivered, bounce (with bounce type), complaint, open, click, deferred, dropped.

<br>

### Message / Event model (example)

```
Message {
  message_id: uuid,
  tenant_id,
  send_at: timestamp,
  from: {name, email},
  to: [{name, email}],
  subject, body_html, body_text,
  attachments: [s3_ref],
  headers: {...},
  dedupe_id: optional,
  tags: [campaign, type],
  created_at, accepted_at
}

Event {
  message_id, tenant_id, event_type (accepted|delivered|bounce|complaint|open|click|deferred|dropped),
  timestamp, details: {smtp_code, bounce_type, recipient_mta, ip}
}
```

### UX flows to call out

* Transactional send: API call → immediate acceptance + quick queueing + webhook for delivery/bounce.
* Bulk marketing: client uploads batch or CSV → job accepted → chunked enqueue → spooled to sender workers with throttling.
* Inbound delivery: accept mail for inbound.\<tenant-domain> or via MX setup; parse and post to webhook or store mailbox.
* Suppression/unsubscribe: support List-Unsubscribe header, one-click unsubscribe endpoint, global suppression.

***

## 25 – 40 min — High-level architecture & data flow

```
Clients (REST API / SMTP / Web UI)
       |
   API Gateway (auth, rate-limit, routing)
       |
  +----+---------------------------+--------------------+
  | Ingest Service (stateless)     |  Inbound Mail Ingest |
  |  - validate, enrich, store     |  - MX frontends      |
  |  - place in Message Store (DB) |  - parse bounces     |
  |  - push to Message Bus (Kafka) |  - deliver to tenant |
  +----+---------------------------+--------------------+
       |
   Message Bus (partitioned by tenant / job / domain)
       |
   Dispatcher / Sender Workers (many)
     - Pull messages
     - Domain-aware throttling & connection pools
     - SMTP outbound sessions (per IP pool)
     - Retry logic & defer queue
     - write send events to Event Store
       |
   Event Store (append-only, replicated) -> Webhooks, Metrics, UI, Billing
       |
   Delivery Analytics / Realtime DB (ClickHouse / Druid / Cassandra)
       |
   Attachments / archival (S3)
       |
   Admin: reputation subsystem, IP pools, DKIM/SPF management, suppression DB
```

Key components

* API Gateway: authentication (API keys, OAuth), per-tenant rate limits, quotas.
* Message Store: durable store for message content & state (e.g., Cassandra/RDBMS + S3 for big payloads).
* Message Bus: Kafka for durability and replay; partition by tenant\_id or campaign\_id to keep ordering and delivery locality.
* Dispatcher / Sender: pool of workers that take messages and talk to recipient MX via SMTP. Features: connection pooling per destination MTA, per-domain concurrency limits, exponential backoff for defers, backpressure to bus.
* IP Pools & Reputation Manager: assign messages to IP pools (shared/dedicated), manage DKIM keys per tenant, SPF/DNS verification guidance, warm-up scheduler.
* Bounce & Complaint Processor: inbound MTA(s) parse bounces, classify type (hard/soft/complaint), update suppression list and notify tenant.
* Tracking: inject tracking pixel and link redirects for opens/clicks (with privacy options). Click tracking via redirect service which records event then 302 to original URL.
* Analytics Store: use OLAP store (ClickHouse / Druid) for aggregations and dashboards.
* Webhooks & Notifications: event publisher that reliably delivers events to tenant endpoints with retries and DLQ.

***

## 40 – 50 min — Delivery semantics, retries, bounce handling & deliverability

<br>

### Delivery semantics & guarantees

* At-least-once send acceptance: once accepted by platform API/SMTP and persisted, it will be attempted; acceptance is durable.
* Message state machine: accepted → queued → sending → delivered|bounce|dropped|deferred|complaint. Each transition emitted as event.
* Support idempotency via dedupe\_id—if same dedupe\_id re-sent within window, reject duplicate or replace per policy.

<br>

### SMTP/Outbound strategy

* Connection management: pool connections to recipient MX for each destination domain with concurrency limits.
* Per-domain throttling: respect remote MTA 4xx 421 rules and back off. Maintain per-domain queues.
* Delivery parallelism: partition messages by domain and IP pool; use multiple sender workers per domain to improve throughput.

<br>

### Retry & backoff

* On temporary failures (4xx/450/421), schedule retries with exponential backoff and jitter; cap number of retries and move to deferred / DLQ.
* On permanent failures (5xx or specific bounce types), mark as bounced, add to suppression lists if hard bounce.
* Store retry metadata in durable queue to survive restarts.

<br>

### Bounce processing & classification

* Parse bounce notifications (DSN) and classify: hard bounce (invalid address), soft bounce (mailbox full), transient, spam complaint (feedback loop).
* Automate suppression: remove/blacklist hard bounce addresses after configurable thresholds. Provide tenant control.

<br>

### Deliverability & reputation

* Authentication: require DKIM signing (per-tenant key), enforce SPF alignment, recommend DMARC & provide guidance. Optionally sign on behalf with d=tenant for dedicated IPs.
* IP management: shared vs dedicated IP pools; warm-up scheduler gradually increases volume on new IPs.
* Throttle by reputation: track per-IP/domain complaint rate, bounce rate; automatically reduce sending rate or block if thresholds exceeded.
* Feedback loops (FBL): subscribe to ISP complaint feeds, ingest complaints and update suppression lists.
* Content checks: run spam/virus scanning, header analysis, template checks before sending to reduce complaints.
* Engagement-based routing: use high-reputation IPs for high-engagement tenants, send low-engagement via separate pools.

<br>

### Tracking opens & clicks (privacy)

* Opens: insert tracking pixel URL (/open?mid=...). Warn about limitations (image blockers). Provide opt-out.
* Clicks: use redirector (/r?mid=...\&link=...) that logs then redirects.
* GDPR/Do Not Track: allow tenants to disable tracking and provide data subject requests handling.

***

## 50 – 55 min — Scaling, capacity planning & back-of-the-envelope

<br>

### Capacity planning template (you can adapt numbers)

<br>

Inputs needed: emails/day (E), average size (S bytes), peak QPS factor (P), retention days (R).

<br>

Sample numbers: E = 10M/day, S = 2 KB average payload, P = 4× peak (burst factor), R = 30 days event retention.

<br>

Storage & bandwidth

* Avg QPS avg = 10M / 86,400 ≈ 116 msgs/sec. Peak QPS ≈ 116 × 4 ≈ 464 msgs/sec.
* Ingress bandwidth ≈ 464 × 2 KB ≈ 0.9 MB/s ≈ 7.4 Mbps peak (outbound to MTA will be similar or higher due to handshake overhead).
* Daily raw payload ≈ 10M × 2 KB = 20 GB/day. 30-day storage ≈ 600 GB (plus replication & events). Attachments stored in S3 add more.

<br>

Kafka / Message Bus

* If keeping 24 hours of queued events in Kafka for spikes: storage ≈ 20 GB/day × 1 (for one day) = 20 GB; with replication factor 3 -> 60 GB.

<br>

Sender pool sizing

* Sender workers: each worker can handle N SMTP sessions concurrently depending on CPU/IO. If one worker handles 200 concurrent sessions, to reach 464 qps with avg send latency to remote MTA \~1s, need \~3–5 workers. Account for retries/backoff and many domains -> scale to dozens/hundreds.

<br>

OLAP

* ClickHouse/Druid nodes sized based on event ingestion (10M/day) — modest cluster of a few nodes.

<br>

(Always adapt to interviewer-provided scale — show math and adjust.)

***

## 55 – 58 min — Operations, security, monitoring & compliance

<br>

### Monitoring & alerts

* Metrics: accepted\_rate, send\_rate, delivery\_rate, bounce\_rate, complaint\_rate, per-tenant rates, per-IP reputation metrics, queue\_length, retry\_count.
* Alerts: sudden spike in bounces/complaints, IP blacklisting events, SMTP backlog growth, webhook failures.
* Tracing: distributed trace for API → queue → send → event → webhook.
* Dashboards: tenant dashboards for delivery, engagement, suppression.

<br>

### Security & compliance

* TLS for all endpoints and SMTP (STARTTLS).
* Store minimal PII; encrypt sensitive fields at rest.
* Audit logs for admin actions.
* GDPR/CCPA: support deletion requests — delete message content & identifiable metadata per retention rules.
* PCI: avoid storing payment info; not directly applicable unless billing. Use tokenized processors.

<br>

### Operational playbooks

* IP warm-up process, handling ISP blacklisting, responding to complaints, whitelisting for high-value tenants, requeue/replay strategies, rolling deployments.

***

## 58 – 60 min — Trade-offs, evolution & summary (wrap-up)

<br>

### Key trade-offs

* Consistency vs throughput: strict ordering per recipient/domain increases complexity — usually not required. Focus on per-domain throttling and eventual delivery.
* Shared IPs vs Dedicated IPs: shared simplifies ops and amortizes reputation but can hurt deliverability for noisy tenants; dedicated improves control but costs & requires warm-up.
* Tracking fidelity vs privacy: opens/clicks give value but may violate privacy preferences — provide opt-outs.
* Exactly-once delivery vs at-least-once: SMTP is inherently at-least-once; we ensure idempotency and dedupe for processing and billing.

<br>

### Evolution path

1. MVP: REST API + simple SMTP relay with a small sender fleet, basic bounce handling, and event webhooks.
2. Add: Kafka-backed queues, retry/backoff orchestration, analytics store, per-tenant rate limiting.
3. Add: IP pools, DKIM/SPF UI & automation, IP warm-up, deliverability tuning, dedicated IP offering.
4. Add: ML-based spam/engagement scoring, advanced segmentation & scheduling, regional sender clusters.

<br>

### One-line summary

<br>

Design a resilient pipeline: ingest messages (API/SMTP), persist and queue with durable bus, dispatch with domain-aware throttling using pooled SMTP senders and reputation management (IP pools, DKIM/SPF), track bounces/complaints/engagement, and expose reliable events & dashboards — balancing throughput, deliverability, and privacy.

***

If you want, I can now:

* turn this into a 5-slide interview deck (one slide per major section), or
* produce a detailed sequence diagram for the send path (API → queue → dispatcher → SMTP → event), or
* run capacity math for your specific expected emails/day / avg size / retention.

<br>

Which of those should I do next?
