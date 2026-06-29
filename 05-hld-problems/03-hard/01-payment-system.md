---
module: 05-hld-problems
topic: Hard
status: interview-ready
tags: [05-hld-problems, system-design, hard]
---
# Design a Payment System

> **Difficulty**: Hard
> **Topics**: Idempotency, Double-Entry Ledger, Saga, Reconciliation, Fraud
> **Time**: 60 min
> **Companies**: Stripe, Amazon, Common

---

## Clarifying Questions

1. "Are we building a payment processor (like Stripe) or a payment service that integrates with one?"
2. "What transaction volume — 10M/day, 1K TPS avg?"
3. "What payment methods — cards only, or ACH/wallets too?"
4. "Do we need a double-entry ledger for accounting, or just transaction records?"
5. "What are the consistency requirements — can we ever lose a payment record?"
6. "Do we need fraud detection, or is that handled upstream?"

---

## Back-of-Envelope

```
10M transactions/day
  Avg: 10M / 86,400 = ~115 TPS
  Peak (Black Friday, end-of-month): ~1K TPS, burst to 5K TPS

Financial volume:
  $50 avg transaction × 10M/day = $500M daily volume
  $500M × 0.029 Stripe fee = $14.5M/day in processing fees → optimize batching

Storage:
  Transaction record: ~1KB × 10M/day = 10 GB/day
  Journal entries: 2 per transaction × 10M = 20M entries/day = 20 GB/day
  Retain 7 years (regulatory): 10 GB × 365 × 7 = ~25 TB over 7 years

Reconciliation:
  PSP settles every 5 min; internal reconciliation job joins ~10M rows/run
```

---

## APIs

```
// Initiate payment
POST /api/v1/payments
  {
    "amount": 9999,               // in cents
    "currency": "USD",
    "payment_method_id": "pm_...",
    "order_id": "ord_12345",
    "idempotency_key": "client-generated-uuid"
  }
  -> { "payment_id": "pay_...", "status": "processing" }

// Get payment status
GET /api/v1/payments/{payment_id}
  -> { "payment_id": "...", "status": "succeeded", "amount": 9999, "settled_at": "..." }

// Refund
POST /api/v1/payments/{payment_id}/refund
  { "amount": 9999, "reason": "customer_request" }
  -> { "refund_id": "ref_...", "status": "pending" }

// Webhook from PSP (internal endpoint)
POST /api/v1/webhooks/stripe
  { "type": "payment_intent.succeeded", "data": { "payment_intent_id": "pi_..." } }
  -> 200 OK
```

---

## Architecture

```
Client
  |-- POST /payments
  |
Payment Service
  +-- Check idempotency_key (PostgreSQL UNIQUE constraint)
  +-- Pre-score fraud (async ML result from Redis, pre-computed at checkout init)
  +-- Create transaction record (status=PENDING) in PostgreSQL
  +-- Saga: AUTHORIZE → CAPTURE → SETTLE
  |     PSP call (Stripe): pass idempotency_key to Stripe as well
  |     On timeout: query Stripe status endpoint (never assume timeout=failure)
  +-- Update transaction (status=SUCCEEDED or FAILED)
  +-- Write journal entries (double-entry: debit customer, credit merchant)
  +-- Kafka "payment.succeeded" → downstream (order service, notification, analytics)

Reconciliation Service (every 5 min):
  +-- Fetch PSP settlement report (CSV or API)
  +-- JOIN internal transactions ON payment_intent_id
  +-- Flag mismatches: charged but no internal record, or internal record but not charged
  +-- Alert on mismatch > $10 threshold

Fraud Service:
  +-- Pre-score at checkout init (async, 200ms ML model)
  +-- Result in Redis: fraud_score:{session_id} TTL 10min
  +-- Payment Service reads score at payment time (< 1ms Redis read)
  +-- Score > 0.85: decline; 0.6-0.85: 3D Secure challenge; < 0.6: allow
  +-- Chargeback events trigger model retraining pipeline (daily batch)
```

---

## Data Model

```sql
-- Transactions (PostgreSQL)
CREATE TABLE transactions (
    tx_id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key     VARCHAR(64) UNIQUE NOT NULL,
    order_id            VARCHAR(64),
    user_id             BIGINT,
    amount              BIGINT NOT NULL,  -- in cents
    currency            CHAR(3) NOT NULL,
    status              VARCHAR(20) NOT NULL,  -- PENDING/SUCCEEDED/FAILED/REFUNDED
    payment_intent_id   VARCHAR(64),      -- Stripe PI ID, for reconciliation
    fraud_score         DECIMAL(5,4),
    failure_reason      TEXT,
    created_at          TIMESTAMPTZ DEFAULT NOW(),
    settled_at          TIMESTAMPTZ
);
CREATE INDEX ON transactions(order_id);
CREATE INDEX ON transactions(user_id, created_at DESC);
CREATE INDEX ON transactions(payment_intent_id);  -- for reconciliation JOIN

-- Double-entry journal (PostgreSQL)
CREATE TABLE journal_entries (
    entry_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tx_id       UUID NOT NULL REFERENCES transactions(tx_id),
    account_id  VARCHAR(64) NOT NULL,  -- 'customer:123', 'merchant:456', 'platform:fees'
    debit       BIGINT NOT NULL DEFAULT 0,   -- in cents
    credit      BIGINT NOT NULL DEFAULT 0,  -- in cents
    created_at  TIMESTAMPTZ DEFAULT NOW(),
    CHECK (debit >= 0 AND credit >= 0),
    CHECK (debit = 0 OR credit = 0)  -- one side per entry
);
-- Invariant: SUM(debit) = SUM(credit) across all entries for any transaction

-- Idempotency keys (for request-level dedup, separate from transactions)
CREATE TABLE idempotency_keys (
    idem_key        VARCHAR(64) PRIMARY KEY,
    tx_id           UUID,
    response_status INT,
    response_body   JSONB,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);
```

---

## Key Design Decisions

**1. Idempotency key: client UUID, UNIQUE in DB, passed to Stripe**
Client generates a UUID before sending the payment request. Server stores `idempotency_key → (tx_id, response)` in the `idempotency_keys` table. On duplicate request (retry, network glitch): DB lookup hits the existing row → return cached response without re-processing. Same key is passed to Stripe as their idempotency key → prevents double-charging even if our server retries the Stripe call. The key expires after 24 hours (Stripe's standard TTL).

**2. Double-entry ledger for every transaction**
Every payment creates two journal entries: debit customer account, credit merchant account. `SUM(all debits) = SUM(all credits) = 0` across the entire ledger. This is double-entry bookkeeping — the accounting invariant that makes reconciliation possible and detects corruption. If a bug causes a transaction to write only one entry: the invariant breaks, and a ledger integrity check catches it. The journal is append-only (never update, never delete entries).

**3. Payment Saga: AUTHORIZE → CAPTURE → SETTLE**
Never charge first, then try to record. Three phases:
- AUTHORIZE: Stripe places a hold on the card (no charge yet). This succeeds or fails quickly.
- CAPTURE: After the order is confirmed in our DB, we call Stripe capture. Now the customer is charged.
- SETTLE: Stripe moves funds to merchant account (T+2 business days). Reconciliation confirms settlement.
Compensating transactions: VOID (reverse authorize before capture), REFUND (reverse after capture). The Saga orchestrator records each phase in `transactions.status` — recovery resumes from the last committed phase.

**4. Never assume timeout = failure**
Stripe API call times out after 30s. Two options: (a) treat as failure → may have charged the user. (b) query Stripe's status endpoint with the idempotency key → get the actual outcome. Always do (b). This is the most important rule in payment systems: a timeout means unknown, not failed. Query the PSP status endpoint to determine the true state, then update our internal record accordingly.

---

## Deep Dives

**Reconciliation process**
PSP settlement report arrives every 5 minutes as a CSV (or via API) with columns: payment_intent_id, amount, status, settled_at. Reconciliation service:
1. Load report into a temp table in PostgreSQL
2. `FULL OUTER JOIN transactions ON payment_intent_id` — find rows missing on either side
3. Missing in internal DB but present in PSP: phantom charge — alert immediately (P1)
4. Present in internal DB as SUCCEEDED but missing in PSP report: not yet settled (check tomorrow) or failed PSP call
5. Amount mismatch: fraud or bug — alert
Tolerance: < $1 discrepancy due to FX rounding is acceptable; anything > $10 triggers an alert.

**Fraud detection pipeline**
At checkout page load (not at pay click): async call to Fraud Service with session context (device fingerprint, IP, user history). Fraud Service runs ML model (XGBoost, 200ms inference), stores result in Redis `fraud_score:{session_id}` with TTL=10min. When user clicks "Pay": Payment Service reads fraud score from Redis (< 1ms). Decision: allow/challenge/decline. Chargeback events (from PSP webhook) feed back as labeled training data for daily model retraining.

---

## Failure Scenarios

| Failure | Impact | Mitigation |
|---------|--------|------------|
| Stripe API timeout on authorize | Unknown charge state | Query Stripe /payment_intents/{id}; update internal status accordingly |
| DB write fails after Stripe auth | Auth placed but no internal record | Outbox pattern: Stripe capture only after DB record created; else release auth |
| Duplicate payment request | Risk of double charge | Idempotency key on both internal DB and Stripe; second call returns cached result |
| Reconciliation mismatch | Financial discrepancy | Alert ops; manual resolution SLA 24h for mismatches; auto-resolve for timing differences |
| Fraud ML model unavailable | All payments processed without score | Default allow for amount < $100; apply manual review queue for larger amounts |

---

## Interview Questions Asked

### Stripe
1. **"How do you design idempotency for a payment API so that a client retrying a failed request never double-charges?"** → Three-layer idempotency: (1) Client generates a UUID before sending — stored in request header. (2) Server inserts `idempotency_key → tx_id` into the `idempotency_keys` table using a UNIQUE constraint — if insert fails (key exists), return the stored response without processing. (3) Same UUID is forwarded to Stripe as their idempotency key — even if our server retries the Stripe call, Stripe returns the original payment intent result. The combination means: first successful write wins; all retries return that result.
2. **"How do you handle the PSP returning a success response for a payment you already marked as failed?"** → Reconciliation detects this: PSP report shows succeeded, internal record shows failed. This is a critical mismatch. Resolution: (1) Check if the customer was charged (PSP says yes → they were). (2) If charged and order was cancelled: initiate refund immediately via PSP API. (3) Update internal transaction to reflect actual PSP state. The internal record must match the PSP — the PSP is the source of truth for whether money moved. Alert ops for all such mismatches; root-cause the bug that caused the discrepancy.

### Amazon
1. **"How does Amazon handle payment for a split shipment — 3 items in one order, shipped at different times over 3 days?"** → Multi-capture saga: one authorization for the full order amount. As each shipment ships: partial capture for that shipment's amount. Stripe supports partial capture on a single payment intent. Internal model: one `transaction` (the authorization), multiple `capture_events` each with their own amount and timestamp. Total captured must not exceed authorized amount. Final settlement: each capture settles independently at the PSP level. Refund for one shipment: partial refund against the relevant capture event.
2. **"How do you design the payment system to be compliant with PCI DSS without your servers ever touching raw card numbers?"** → Never store or process raw card data. Flow: client-side tokenization (Stripe.js collects card → immediately submits to Stripe servers → returns a payment method token). Our servers only ever see the token (`pm_...`), never the card number. PAN (Primary Account Number) never traverses our network. PCI compliance level drops from SAQ D (most complex) to SAQ A (simplest). For saved cards: Stripe Vault stores the card; we store the vault reference token.

### Common Follow-ups
1. **"What does 'double-entry bookkeeping' mean and why does a payment system need it?"** → Every financial event creates two offsetting journal entries: a debit (money leaves) and a credit (money arrives). For a $100 payment: debit customer_wallet $100, credit merchant_account $100. The invariant: `SUM(all debits) = SUM(all credits) = 0` for the system as a whole. Why this matters: any bug that creates or destroys money (a transaction that writes only one side) is detected immediately by the integrity check. It's how Stripe ensures $X leaving one account equals $X arriving at another — no money is created or destroyed.
2. **"Your payment service times out calling Stripe. The 30s deadline passes. Did the charge go through?"** → Unknown. A timeout is not a failure. Stripe may have: (a) processed the payment and the response was lost in transit, or (b) not processed it due to the timeout. Recovery: query Stripe's API `GET /v1/payment_intents/{id}?expand[]=charges` with the idempotency key. Get the actual status. Update internal record. If Stripe says succeeded but we timed out: our customer was charged — proceed to fulfill the order. If Stripe says failed: no charge — inform customer, allow retry.
3. **"How do you prevent a fraudster from trying thousands of stolen cards on your checkout?"** → Rate limiting: max 3 payment failures per session_id per hour; max 10 per user_id per 24 hours. Velocity rules in fraud detection: if card attempts spike from one IP/device, flag for manual review. 3D Secure (3DS): for high-risk transactions, require card-issuer authentication (SMS code) — shifts liability to issuer. Device fingerprinting: same device trying many cards → block device ID. Honey pot: silent decline for known fraudulent cards (don't reveal which rule triggered — makes it harder to probe).

---

## Interviewer Follow-Up Questions

**On idempotency:**
- "What if two requests with the same idempotency key arrive simultaneously?" → Race condition: both reach the DB UNIQUE constraint insert simultaneously. PostgreSQL serializes: first INSERT succeeds, second gets a unique constraint violation. The second request handler catches the exception, reads the existing row, and returns the cached response. No double processing. This works because PostgreSQL's UNIQUE constraint is a distributed mutex at the database level — no application-level lock needed.
- "Idempotency key expires after 24 hours. What if the client retries after 25 hours?" → The key is no longer in the `idempotency_keys` table (deleted by a cleanup job). The retry is treated as a new payment request. Client must handle this: after 24 hours, a retry is a new payment, not a safe retry. Client SDKs should not retry after more than 24 hours. For operations that take longer (manual review): use a separate `payment_reference_id` on the client's side to detect duplicates at a higher level.

**On double-entry:**
- "How do you verify the ledger is balanced in production?" → Integrity check query: `SELECT SUM(debit) - SUM(credit) FROM journal_entries WHERE created_at > NOW() - INTERVAL 1 HOUR`. Should always equal 0. Run every 5 minutes via a monitoring job. Alert if result != 0 — this is a P0 incident (money was created or destroyed in our system). Also: `GROUP BY tx_id` → each transaction's sum should be 0. A non-zero tx_id means one leg of the transaction failed to write.
- "A refund fails halfway — you debit the merchant but never credit the customer. How do you detect this?" → The journal_entries for the refund transaction will have `SUM != 0` for that tx_id. The integrity check catches it within 5 minutes. Recovery: the Saga compensating transaction retries the second journal entry. Since journal entries are append-only with the tx_id, the retry inserts the missing entry → ledger balances. If the retry fails persistently: DLQ + manual ops review. The invariant violation alert is the detection mechanism.

**On reconciliation:**
- "What is a payment 'reconciliation mismatch' and how do you resolve it?" → PSP settlement report says X was charged; internal DB says status=FAILED (or vice versa). Types: (1) Internal SUCCEEDED + PSP not settled: possibly timing lag — wait T+2 days, recheck. (2) PSP charged + internal FAILED: ghost charge — refund the customer immediately, fix the internal record. (3) Amount differs: rounding error (acceptable < $0.01) or FX conversion bug (investigate). Resolution SLA: P1 mismatches (customer was charged for nothing) resolved in 1 hour; P2 (amount discrepancy) in 24 hours; P3 (timing lag) in 3 business days.
