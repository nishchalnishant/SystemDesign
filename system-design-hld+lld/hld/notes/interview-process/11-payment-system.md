# #11 Payment system

Below is a complete, time-boxed 1-hour interview answer for designing a Payment System (think payments platform that handles card wallets, bank transfers, authorizations, captures, refunds, payouts — similar to Stripe/Adyen/PayPal core flows). It follows your interview pattern: clarify → FR/NFR → APIs & data model → high-level architecture → deep dives (consistency, transactions, reconciliation, fraud) → capacity/BoE → ops/security → trade-offs & wrap up. Use the minute markers to pace your delivery.

***

### 0 – 5 min — Problem recap, scope & assumptions (set the stage)

<br>

Start by restating and aligning scope.

<br>

Goal: Build a payment processing platform that accepts payment requests (cards, bank debits, wallets), performs authorization/capture/refund flows, routes to payment processors/acquirers, handles payouts to merchants, provides dashboards & webhooks, supports PCI/DSS security, fraud detection, settlement/reconciliation, and strong auditability.

<br>

Key capabilities

* Authorize and capture payments (separate flows).
* Support multiple payment methods & processors (pluggable).
* Handle refunds, partial refunds, chargebacks.
* Maintain idempotency, strong durability, and reconciliation for financial correctness.
* Expose APIs for merchants + webhooks for event notifications.
* Strong security, compliance (PCI, KYC), monitoring, and dispute flows.

<br>

Example assumptions (tunable)

* 10M transactions/day (≈116 tps avg), peak 5k tps during campaigns.
* Average transaction payload \~1 KB.
* Settlement daily; funds movement through payment rails (acquirers/issuers) and payouts to merchants (ACH/SEPA/Wire).
* SLA: API acceptance P95 < 200 ms; orchestration flows complete within seconds to minutes depending on payment rails.

***

### 5 – 15 min — Functional & Non-Functional Requirements

<br>

#### Functional Requirements (Must / Should / Nice)

<br>

Must

1. Accept payment requests via REST/gRPC and SDKs: support create payment (authorize, capture), one-click charges, tokenization.
2. Multiple payment methods: cards (tokenized), bank transfers (ACH/SEPA), wallets (Apple/Google Pay), BNPL (optional).
3. Authorization / Capture: support auth-only, later capture, voids.
4. Refunds & chargebacks: partial/full refunds, handle inbound disputes and evidence submission.
5. Payouts: pay out merchant balances to bank accounts, support scheduling & currency conversion.
6. Routing & fallbacks: choose/acquirer routing with retry/failover and dynamic route selection (cost/latency/success).
7. Webhooks & notifications: push payment status changes to merchants.
8. Reporting & settlement: detailed transaction logs, settlement files, fees, reconciliations.
9. Idempotency & retries: client idempotency keys, safe retries without double-charging.
10. Fraud detection: real-time scoring, blocking, manual review flows.

<br>

Should

* Tokenization / vault for card data (PCI scope reduction).
* Support multi-currency and FX.
* Rule engine for custom routing/pricing per merchant.

<br>

Nice-to-have

* Smart retry logic using historical success rates per acquirer/currency.
* Instant payouts to debit cards (real-time rails).

<br>

#### Non-Functional Requirements

* Performance: API accept P95 < 200 ms; auth latency to acquirers typically < 1s but can be higher.
* Throughput: support peak tps (example 5k tps) with auto-scale.
* Durability & correctness: no lost transactions, idempotent behavior, persisted audit trail (immutable ledger).
* Availability: 99.95% for acceptance path; background settlement can be less strict.
* Consistency: strong consistency for financial state (balances, ledger) — serializable or linearizable operations for money movement.
* Security & compliance: PCI-DSS, encryption, KYC for merchants, auditability.
* Observability: per-transaction tracing, reconciliation metrics, fraud metrics.
* Latency vs accuracy trade-off: prefer correctness for money flows; allow async for long-running external rails.

***

### 15 – 25 min — APIs & Data model (external contract)

<br>

#### Key APIs

```
POST /v1/payments/create      {amount, currency, payment_method_id, capture=false, merchant_id, metadata, idempotency_key}
POST /v1/payments/{id}/capture
POST /v1/payments/{id}/void
POST /v1/payments/{id}/refund {amount, reason}
POST /v1/methods/tokenize     {card_details}
GET  /v1/payments/{id}
GET  /v1/settlements?merchant=...&date=...
POST /v1/payouts/create      {merchant_id, amount, bank_account}
POST /v1/webhooks/register
```

#### Minimal transaction/event model

<br>

Payment

```
payment_id, merchant_id, amount, currency, status (created, authorized, captured, refunded, failed, chargeback),
auth_id, capture_id, route_id, fees, created_at, updated_at, metadata, idempotency_key
```

LedgerEntry (immutable)

```
entry_id, payment_id, type (debit/credit/fee/settlement/payout), amount, currency, account, timestamp, related_entry_id
```

Accounts

* Platform account (holds fees)
* Merchant ledger/account (available balance, pending settlements) — must be updated transactionally

<br>

PaymentMethod / Token

* token\_id pointing to vault entry (no raw PAN in system)

<br>

Routing & Acquirer config

* acquirer\_id, regions/currencies, fees, retry policies, limits

<br>

Audit log: append-only immutable stream of events for each payment.

***

### 25 – 40 min — High-level architecture & data flow

```
Client SDK / Merchant Backend
        |
     API Gateway (auth, rate-limit, idempotency check)
        |
  ┌─────────────────────────────────────────────────────────┐
  │  Frontend Orchestrator / Payments API (stateless)      │
  │   - Validate, enrich, compute routing decision          │
  │   - Persist Payment (initial)                          │
  │   - Publish to Ingest Bus / sync call to Acquirer      │
  └─────────────────────────────────────────────────────────┘
        |
   Event Bus (Kafka)  <---->  Transactional Ledger Service (strong-consistent)
        |
  Routing / Connector Layer (Acquirer connectors, PSP adapters)
        |
  Acquirers / Issuers / Wallet Providers / ACH rails
        |
  Webhooks / Notifications to merchants; Async processors: settlement, reconciliation, payouts
        |
  Downstream: Fraud Service, Accounting Service, Reporting & BI, Archive (S3)
```

Key components explained

1. API Gateway: authenticates merchant, enforces quotas, dedupe via idempotency keys (reject duplicates or return existing payment).
2. Payments Orchestrator: core service that handles the happy path: validate request, reserve ledger entries (pending debit to card + hold merchant pending balance), route to connector.
3. Connector Layer: adapters per acquirer/PSP that handle protocol differences, retries, and error mapping.
4. Event Bus: reliable durable messaging for async work, retries, webhooks, and reconciliation.
5. Ledger / Accounting Service: strong-consistency store that records all Debits/Credits as immutable ledger entries; updates merchant balances transactionally. This must be ACID and support concurrency controls.
6. Vault / Tokenization: PCI-scope vault for card data (separate service), tokens returned to merchants.
7. Fraud / Risk Service: synchronous scoring during auth, and async risk workflows for manual review.
8. Reconciliation & Settlement: batch jobs that reconcile acquirer settlement files to ledger, compute payouts, and generate settlement reports.
9. Payout Service: issues ACH/SEPA/Wire payouts, manages payout schedules and limits.
10. Monitoring & Audit: trace and audit log accessible for disputes & investigations.

***

### 40 – 50 min — Deep dives: correctness, transactions, reconciliation & fraud

<br>

#### Money movement & consistency

* Two-layer model:
  * External movement: interaction with acquirers/rails (auth, capture, settlement) — external rails are eventually consistent and can have delays/failures.
  * Internal ledger: we maintain an internal single source of truth (immutable ledger) and merchant balances derived from ledger (available balance, pending). All business decisions use ledger state.
* Atomicity:
  * On payment authorization: create ledger entries for pending hold (e.g., Debit customer/payment source? — external) and create a pending credit/reservation in merchant ledger (not available until capture/settlement). These ledger writes are transactional.
  * Use database transactions or a transactional ledger service (e.g., strongly-consistent DB or CRDTs with strong serializability) to ensure money conservation invariants.
* Idempotency:
  * Client provides idempotency\_key. Orchestrator checks store: if existing request with same key, return existing response rather than re-issuing network calls that could double-charge.
* Exactly-once to external connectors:
  * Use connector-level dedupe (store external request IDs) and idempotent APIs when supported; otherwise design retry logic carefully to avoid duplicate captures.
* Handling async confirmations:
  * Auth may be synchronous (instant) or async (3DS, bank delays). Payment status transitions are driven by incoming connector events (webhooks or polling) that update ledger.

<br>

#### Reconciliation & Settlement

* Settlement flow:
  * Capture events → acquirer settles in batch to platform (with settlement report/statement). Platform reconciles settlement file to ledger entries: match transaction IDs, amounts, fees. Discrepancies flagged.
* Reconciliation process:
  * Compare acquirer settlement lines with ledger entries; produce adjustments (fees, chargebacks). Implement tolerances and workflows for unmatched items.
  * Persist reconciliation metadata; produce settlement files for merchant payouts.
* Chargebacks:
  * On chargeback, platform subtracts amount from merchant ledger (provision for reserve), creates chargeback ledger entries, triggers dispute process (evidence submission). Maintain dispute lifecycle state.

<br>

#### Fraud Prevention & Risk

* Synchronous fraud checks:
  * Before auth: score payment with real-time models (device fingerprint, velocity checks, BIN checks, user behavior, 3DS flows). If high-risk, block or route for additional verification (3DS, manual review).
* Asynchronous analytics:
  * Post-transaction scoring for patterns, cluster-based detection for collusion, repeat offenders, or synthetic identities.
* Manual review:
  * Provide UI/workflow to approve/reject flagged transactions; actions should be replayable and produce audit trail.
* Fail-open vs fail-closed:
  * For small merchants, choose conservative defaults (fail closed) or configurable per merchant risk tolerance.

<br>

#### Fault-tolerance & idempotency for connectors

* Connector design:
  * Each connector persists outbound request state and external identifiers; on retry, consult external ID to avoid duplicates. Support transactional outbox pattern to guarantee delivery.
* Outbox & inbox:
  * Use transactional outbox pattern: orchestrator writes to DB (ledgers + outbox row) in a single transaction; separate delivery worker reads outbox and sends to connector; on ack, marks outbox delivered.

***

### 50 – 55 min — Back-of-the-envelope capacity & sizing

<br>

(Sample math — adapt to interviewer numbers.)

<br>

Assumptions: 10M tx/day average, peak 5k tps, avg payload 1 KB.

* Ingress: 10M/day → ≈ 116 tps avg; provision for peak = 5k tps.
* Event bus: at 5k tps × 1 KB = 5 MB/s ≈ 43.2 GB/day. With replication factor 3, \~130 GB/day through Kafka.
* Ledger DB: writes per tx (multiple entries per payment: authorization, fees, settlement) → say 3 entries/tx → 30M ledger writes/day. Choose a database (NewSQL or partitioned RDBMS) sized for sustained writes with multi-master or partitioned leaders.
* Connector throughput: many external acquirers limit QPS per merchant or per IP; need lots of connector workers & pooled connections.
* Storage: raw events & audit logs archived to S3 — 43.2 GB/day ≈ 1.3 TB/month (compressible).
* Workers: scale orchestrator instances and connector workers horizontally; use autoscaling based on queue depth and latencies.
* Stateful services: ledger nodes and payout schedulers need high availability, backups, and consistent replication.

***

### 55 – 58 min — Operations, security & compliance

<br>

#### Security & PCI

* PCI scope reduction: use tokenization & vault externalization (e.g., use a PCI-compliant vault service). Avoid storing PAN in app DBs.
* Encryption: TLS in transit; encrypt at-rest for sensitive fields (keys in KMS).
* Access control & audit: strict IAM, RBAC for admin ops; immutable audit logs for all financial actions.
* Key management: KMS for keys, HSM for signing if needed (e.g., for settlement files).

<br>

#### Compliance & KYC

* Merchant onboarding (KYC): identity checks, business validation, risk profiling, limits per merchant.
* Regulatory reporting: transaction logs, AML/KYC, suspicious activity reporting.

<br>

#### Monitoring & alerts

* Metrics: API latency, success/failure rates, queue lag, ledger invariants (sum of debits = sum of credits), reconciliation mismatch rate, fraud flags.
* Tracing: distributed tracing for each payment lifecycle.
* Alerts: escalations for ledger imbalance, replication lag, connector outage, spike in declines/chargebacks.

<br>

#### Disaster Recovery

* Backup ledger DBs, replicate critical state to multiple regions, have failover procedures and reconcile using immutable event archives.

***

### 58 – 60 min — Trade-offs, evolution & summary (wrap-up)

<br>

#### Key trade-offs

* Synchronous vs asynchronous: sync auth provides immediate UX but relies on external rails; async flows needed for slow rails (ACH). Prefer sync for auth, async for settlement.
* Consistency vs availability: ledger should be strongly consistent (ACID) for money correctness; design other parts (analytics, reporting) eventually consistent.
* Complexity vs correctness: transactional outbox + strong ledger increases complexity but is essential for financial correctness.
* Performance vs fraud prevention: heavier fraud checks increase latency; allow configurable risk profiles per merchant.

<br>

#### Evolution path

1. MVP: accept card payments via one acquirer, basic ledger, tokenization via vault, webhooks, basic refunds.
2. Phase 2: add connector layer with multiple acquirers, routing & smart retry, payouts, reconciliation engine.
3. Phase 3: advanced fraud ML, multi-currency FX, dynamic routing, instant payouts, global multi-region failover and regulatory features.

<br>

#### One-line summary

<br>

Implement a payments platform where a strong, transactional internal ledger is the single source of truth; orchestrate external authorizations/captures through a resilient connector layer with idempotency and outbox delivery; perform settlement and reconciliation with immutable audit trails; and surround it with robust fraud, KYC, and PCI-compliant security to guarantee correctness and trust for money flows.

***

If you’d like, I can now:

* convert this into a 5-slide interview deck (one slide per major time block),
* produce a sequence diagram for create payment → auth → capture → settle → payout, or
* run capacity math with your specific expected TPS, average transaction size, and retention window.

<br>

Which would you like next?
