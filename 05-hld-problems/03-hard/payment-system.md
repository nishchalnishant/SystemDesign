> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design a payment system — the most correctness-critical system design problem; covers double-charge prevention, double-entry accounting, PCI compliance, and reconciliation.
>
> **Key design decisions:**
> - Idempotency: every payment request has an idempotency key (client-generated UUID); server stores key → result; retry returns same result without re-charging
> - Double-entry accounting: every transaction has debit + credit entries; ledger entries are immutable; account balance = sum of all entries
> - Payment flow: initiate → payment processor (Stripe/Braintree) → async webhook confirmation → order update; never wait synchronously on payment response
> - Saga pattern: cross-service payment (reserve funds → charge → fulfill); compensating transaction on failure (refund); distributed without 2PC
> - PCI DSS compliance: never store raw card numbers; tokenize with payment processor; TLS everywhere; cardholder data isolated in separate service
> - Reconciliation: daily batch job compares internal ledger vs payment processor report; flag mismatches for manual review
> - Failure handling: timeout ≠ failure; query payment processor for status; idempotency key prevents double-charge on retry
>
> **Key takeaway:** Idempotency key + double-entry ledger are the two non-negotiable foundations — timeout does not mean failure, so always query status before retrying a payment.

---
module: 05-hld-problems
topic: Hard
status: unread
tags: [05-hld-problems, system-design, hard, payment, idempotency, double-charge, reconciliation, pci]
---
# Design a Payment System

> **Difficulty**: Hard | **Asked at**: Stripe, PayPal, Square, Uber, Amazon

---

## Problem Statement

Design a payment processing system that handles online payments. Users initiate payments; the system charges their card, transfers money to merchants, and provides a reliable audit trail. The system must prevent double-charges, handle network failures gracefully, comply with PCI DSS, and support reconciliation.

---

## Functional Requirements

1. **Charge**: Debit a user's payment method and credit a merchant account
2. **Refund**: Return funds to the customer for a previous charge
3. **Payout**: Transfer accumulated merchant funds to their bank account
4. **Payment status**: Query the status of any payment
5. **Idempotency**: Retrying a failed payment must not charge twice
6. **Webhooks**: Notify merchants of payment status changes asynchronously

---

## Non-Functional Requirements

- **Scale**: 1M transactions/day, 100 TPS sustained, 10,000 TPS during peak (Black Friday)
- **Latency**: Payment confirmation < 3s for the user
- **Availability**: 99.999% — downtime = lost revenue
- **Correctness**: Zero double-charges; zero lost payments
- **Compliance**: PCI DSS Level 1 (card data never stored in plaintext)
- **Auditability**: Every state change logged with timestamp, actor, reason

---

## Core Entities

| Entity | Key Fields |
|--------|-----------|
| `Payment` | payment_id, merchant_id, customer_id, amount, currency, status, idempotency_key, created_at |
| `PaymentMethod` | pm_id, customer_id, token (PSP reference — no raw card data), type, last4, expiry |
| `Charge` | charge_id, payment_id, psp_transaction_id, amount, status, created_at |
| `Refund` | refund_id, charge_id, amount, reason, status, created_at |
| `LedgerEntry` | entry_id, account_id, amount, type (debit/credit), payment_id, created_at |

---

## API Design

```http
POST /api/v1/payments
Headers: Idempotency-Key: <uuid>
Body: {
  "merchant_id": "m123",
  "customer_id": "c456",
  "payment_method_id": "pm789",
  "amount": 9900,  # cents
  "currency": "USD",
  "metadata": { "order_id": "ord_abc" }
}
Response 201: {
  "payment_id": "pay_xyz",
  "status": "succeeded",
  "amount": 9900,
  "created_at": "2026-06-29T14:30:00Z"
}

GET /api/v1/payments/{payment_id}
Response 200: { "payment_id": "...", "status": "succeeded", ... }

POST /api/v1/payments/{payment_id}/refund
Body: { "amount": 9900, "reason": "customer_request" }
Response 200: { "refund_id": "re_123", "status": "pending" }
```

---

## High-Level Design

```
Client (merchant's app)
  │ POST /payments with Idempotency-Key
  ▼
Payment API (stateless, horizontally scaled)
  │ 1. Check idempotency key (Redis or DB)
  │    If exists → return cached response (no re-charge)
  │ 2. Validate: payment method, amount, merchant
  │ 3. Write payment record (status=pending) to DB
  │ 4. Call PSP (Payment Service Provider: Stripe, Adyen)
  │ 5. Update payment record (status=succeeded/failed)
  │ 6. Store idempotency response (Redis, 24h TTL)
  │ 7. Publish payment event → Kafka
  │ 8. Return response to merchant
  ▼
Kafka: payment-events
  ├── Webhook Dispatcher: notify merchant of payment status
  ├── Ledger Service: write double-entry accounting entries
  └── Analytics: update dashboards, fraud models

PostgreSQL: payments, charges, refunds, ledger_entries
Redis: idempotency cache, rate limiting
Vault (HashiCorp): encryption keys for sensitive data
```

---

## Deep Dive 1: Idempotency — Preventing Double Charges

**Problem**: Merchant sends `POST /payments` with $99. The PSP charges the card. The response is lost in a network timeout. Merchant retries. Without idempotency, the customer is charged $198.

**Idempotency key mechanism**:
1. Merchant includes `Idempotency-Key: <uuid>` on every payment request
2. API: `SETNX idempotency:{key} "processing" EX 30` (Redis)
   - If `SETNX` returns 0 (key exists, another request is in-flight): return 409 — duplicate request in progress
3. After completion: `SET idempotency:{key} {response_json} EX 86400` (overwrite with final response)
4. On retry: `GET idempotency:{key}` → found → return cached response immediately (no PSP call)

**Race condition**: Two concurrent requests with the same idempotency key. Both hit Redis `SETNX` simultaneously. Only one succeeds (Redis is single-threaded). The loser receives 409 and should poll for the result.

**Idempotency at the PSP level**: Even if your API is idempotent, the PSP call itself must be idempotent. Send `Stripe-Idempotency-Key: {your_payment_id}` on every Stripe request. Stripe deduplicates on their side — retrying the same Stripe idempotency key returns the same charge result without re-charging.

**Two-step approach (pre-authorization)**:
1. **Authorize**: Reserve funds on the card without charging (`capture=false`)
2. **Capture**: Charge the reserved funds (after confirming order)
If the authorize succeeds but capture is never called (server crash), the authorization expires in 7 days — no charge.

---

## Deep Dive 2: Ledger and Reconciliation

**Problem**: $1M in payments processed today. Are all funds accounted for? How do you detect discrepancies between your records and the PSP's records?

**Double-entry accounting ledger**:
Every money movement creates two entries: one debit and one credit. The sum of all entries must always be zero.

```sql
-- Customer charges $99:
INSERT INTO ledger_entries VALUES
  (account='customer:c456', amount=-9900, type='debit', payment_id='pay_xyz'),
  (account='merchant:m123', amount=9900, type='credit', payment_id='pay_xyz');

-- Stripe takes 2.9% + $0.30 fee:
INSERT INTO ledger_entries VALUES
  (account='merchant:m123', amount=-316, type='debit', payment_id='pay_xyz'),
  (account='stripe_fees', amount=316, type='credit', payment_id='pay_xyz');
```

**Reconciliation**: Every night, pull the PSP's settlement report (CSV with every transaction, their reference ID, amount, fee). Compare against your ledger:

```python
psp_transactions = parse_settlement_report("stripe_2026-06-29.csv")
our_transactions = db.query(
    "SELECT psp_transaction_id, amount FROM charges WHERE date='2026-06-29'"
)

discrepancies = []
for psp_tx in psp_transactions:
    our_tx = our_transactions.get(psp_tx.id)
    if our_tx is None:
        discrepancies.append(("missing_in_our_db", psp_tx))
    elif our_tx.amount != psp_tx.amount:
        discrepancies.append(("amount_mismatch", psp_tx, our_tx))
```

Discrepancies are investigated manually and corrected with adjustment entries.

---

## Deep Dive 3: PCI DSS Compliance

**Problem**: Storing cardholder data (card number, CVV) in your database makes you a PCI DSS target. A breach exposes millions of customer cards.

**Tokenization**: Never store raw card data. When a user saves a card:
1. Card data is entered in a Stripe-hosted payment form (iframe or Stripe Elements — card data never touches your servers)
2. Stripe returns a `token` (e.g., `pm_card_visa_abc123`)
3. Your system stores only the token, last4, expiry, and card brand — no PAN (Primary Account Number)
4. For future charges: send `{payment_method_id: "pm_card_visa_abc123", amount: 9900}` to Stripe API

**Network segmentation**: Payment API servers are in an isolated PCI-scoped VPC subnet. No other services can reach them. Outbound connections are whitelisted (Stripe API only).

**Audit logging**: Every API call, DB write, and PSP interaction is logged to an append-only audit log (write-once S3 or WORM storage). Logs include: timestamp, user/service, action, resource, result. Retained for 7 years (PCI DSS requirement).

**Encryption in transit and at rest**: TLS 1.3 for all external connections. Database encryption at rest (AWS RDS encryption). Application-level encryption for sensitive fields (customer email, address) using Vault-managed keys with automatic key rotation every 90 days.

---

## Interviewer Questions by Level

**Junior**:
- What is a double charge? Why is it catastrophic for a payment system?
- What is an idempotency key? How does it prevent duplicate charges?
- What is PCI DSS and why do payment systems have to comply?

**Mid-level**:
- Walk through the idempotency mechanism end-to-end. How do you handle two concurrent requests with the same idempotency key?
- What is tokenization? How does Stripe's hosted form prevent your servers from seeing card data?
- What is double-entry accounting? How do you use it to track payment flows?

**Senior**:
- Design the reconciliation pipeline — how do you detect discrepancies between your records and Stripe's settlement report?
- How do you handle a PSP outage? The user clicks "Pay" and Stripe returns a 503. What does the system do?
- Design the payout system that transfers accumulated merchant funds to their bank account via ACH. What are the risks and how do you handle them?
- How do you handle chargebacks (when a customer disputes a charge with their bank)?

---

## Back-of-Envelope Estimation

**Scale inputs (given in NFRs):**
- 1M transactions/day, 100 TPS sustained, 10,000 TPS peak (Black Friday)

**Transaction volume:**
- 1M txns/day ÷ 86,400 sec = **~11.6 TPS** average
- Peak: 10,000 TPS (Black Friday flash — ~860× average)
- Each transaction record: `{txn_id, user_id, merchant_id, amount, currency, psp_ref, status, idempotency_key, created_at}` ≈ 500 bytes
- Write throughput at peak: 10,000 × 500 bytes = **5 MB/sec** — trivial for PostgreSQL

**Idempotency key storage:**
- Every payment request carries an idempotency key (UUID, 36 bytes)
- Redis stores `idempotency_key → {status, response}` with 24h TTL
- At 10,000 TPS peak × 24 hours × 36 bytes key + 200 bytes response = **~84 GB** in Redis at peak
- A single Redis node (128 GB RAM) handles this comfortably

**PSP (Payment Service Provider) timeout budget:**
- User sees payment confirmation < 3s
- Network to PSP (Stripe/Braintree): ~100ms round trip
- PSP internal processing: ~500ms–1.5s (card network authorization)
- DB write + response: ~50ms
- Total: ~2s P50, 2.8s P95 — within 3s SLA
- If PSP returns 503: retry with same idempotency key after 200ms (max 2 retries); if still failing, queue for async retry and show user "payment processing"

**Storage sizing:**
- 1M txns/day × 365 = 365M txns/year × 500 bytes = **~183 GB/year**
- 7-year audit retention (PCI DSS): **~1.3 TB** — fits on a single PostgreSQL instance with partitioning by month
- Audit log (every state change): 5 events/txn × 365M × 300 bytes = **~550 GB/year** additional

**Fraud scoring:**
- Every transaction scored before authorization: user's 30-day history, device fingerprint, geo-velocity check
- Redis stores user feature vectors: 100M users × 500 bytes = **~50 GB** — fits in a Redis cluster
- ML inference: 10,000 TPS × 10ms scoring = 100 cores needed for fraud scoring alone

**Architecture decisions driven by these numbers:**
- **PostgreSQL for transactions, not Cassandra**: 10,000 TPS peak is low for a relational DB (PostgreSQL handles 100K+ TPS on modern hardware). The data model is strongly relational (txn → user → merchant → PSP ref → refund). ACID correctness prevents double-charges. NoSQL would sacrifice transactional guarantees for throughput that isn't needed.
- **Idempotency keys in Redis, not DB**: At 10,000 TPS, checking an idempotency key before every write must be sub-millisecond to stay within the 3s user-facing budget. Redis `SETNX` is ~0.1ms. A DB `SELECT + INSERT` is ~5ms + lock contention at peak.
- **Async PSP retry via Kafka**: If Stripe returns 503, the system publishes to a `payment-retry` Kafka topic. A retry consumer re-attempts with the same idempotency key. This decouples the user-facing request (returns "processing" immediately) from the PSP call, eliminating user-visible failures for transient PSP outages.
- **Ledger as append-only log**: Every money movement is an immutable append (credit/debit entries). Balance = sum of all entries for an account. Never update a row in the ledger. This gives a complete audit trail by design and prevents accidental overwrites from corrupting balances.
