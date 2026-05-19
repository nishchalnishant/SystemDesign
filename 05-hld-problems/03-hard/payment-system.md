# Design Payment System (Stripe/PayPal)

> **Difficulty**: Hard
> **Topics**: ACID Transactions, Double-Entry Bookkeeping, Idempotency, Distributed Transactions
> **Time**: 75 minutes
> **Companies**: Stripe, PayPal, Square, Adyen

---

## Problem Mindmap

```
Payment System (Stripe/PayPal)
├── Problem Constraints
│   ├── Scale → 10M transactions/day = 1K TPS avg; 5K TPS peak; $500M daily volume; zero tolerance for double-charge
│   ├── Latency target → payment API response < 2s; settlement < T+1 day
│   └── Core hardness → exactly-once payment processing + double-entry accounting consistency + saga compensation on failure
├── Architecture Derivation
│   ├── Step 1 → Naive charge API → network retry on timeout causes double charge; no idempotency = catastrophic
│   ├── Step 2 → Idempotency key → client sends key; server stores (key, result) in DB; duplicate request returns cached result
│   ├── Step 3 → Idempotency key + DB INSERT in same transaction → atomically link payment attempt to idempotency record
│   └── Step 4 → Saga pattern: AUTHORIZE → CAPTURE → SETTLE; compensating transactions (VOID/REFUND) on any step failure
├── Core Components
│   ├── Payment API → validates request; checks idempotency key; routes to payment processor
│   ├── Idempotency store → PostgreSQL table (idempotency_key UNIQUE, status, response_payload, created_at); check before processing
│   ├── Double-entry ledger → every payment = two journal entries (debit merchant_receivable, credit user_payable); sum always = 0
│   ├── Reconciliation job → runs every 5 min; compares internal ledger vs payment processor reports; flags discrepancies
│   └── Saga orchestrator → manages AUTHORIZE → CAPTURE → SETTLE state; triggers compensations on timeout or failure
├── Data Model
│   ├── transactions → (tx_id UUID PK, user_id, amount, currency, status ENUM, idempotency_key UNIQUE, payment_method_id, created_at)
│   ├── journal_entries → (entry_id, tx_id, account_id, debit_amount, credit_amount, created_at); sum(debit)=sum(credit) invariant
│   └── idempotency_keys → (key UNIQUE, tx_id, response JSONB, created_at); TTL 24h for cleanup
├── APIs
│   ├── POST /payments → {amount, currency, payment_method, idempotency_key} → {tx_id, status, amount}
│   ├── POST /payments/{tx_id}/refund → {amount?, reason} → {refund_id, status}
│   └── GET /payments/{tx_id} → {status, amount, created_at, events[]}
├── Critical Trade-offs
│   ├── Idempotency in same DB transaction → idempotency key INSERT + payment record INSERT in one TX; prevents partial state
│   ├── Saga vs 2PC → Saga chosen; 2PC requires distributed lock across payment processor + internal DB (external system can't participate)
│   └── Async settlement vs sync → authorization sync (< 2s); capture/settle async via job; reduces latency on critical path
├── Failure Scenarios
│   ├── Network timeout after charge → client retries with same idempotency_key; server returns cached success; no double charge
│   ├── CAPTURE fails after AUTHORIZE → saga compensates: send VOID to payment processor; update status to VOIDED
│   └── Reconciliation mismatch → flag for manual review; auto-retry for known transient errors; alert on persistent discrepancy
└── Interview Angles
    ├── Stripe → "Design Stripe's payment API" → idempotency key + double-entry ledger + saga = production-grade answer
    ├── PayPal → "How do you prevent double charges?" → idempotency key stored in same DB transaction as payment record
    └── Follow-up → "How do you handle currency conversion?" → snapshot exchange rate at transaction time; store in journal entry
```

---

## What Breaks Without This System

Your e-commerce site processes payments with a simple flow: call Stripe's API, and on success, write to your orders table. You deploy on a Friday. At 11:48 PM, a network blip causes a timeout: Stripe charged the card, but the HTTP response never arrived. Your retry logic fires. Stripe charges the card again. The customer's order table shows one order; their bank shows two charges. You discover it Monday morning — after 200 customers emailed. Refunding all the duplicates takes three days of manual work and costs you $50K in trust.

That's the easy failure. The harder one: your checkout service writes `order = PAID` to the orders DB, then crashes before writing to the inventory DB. Now the customer has a "PAID" order for an item that was never decremented from stock. When another customer buys the same item and it ships, you have two customers with confirmed orders for one physical item. No single database transaction spanned both writes — because orders and inventory are on different shards, different services, different databases. Rollback is impossible after the fact.

Money movement is the most correctness-sensitive operation in software. Every failure mode — crash between writes, network timeout, duplicate retry, partition-split balances — has real financial and legal consequences.

---

## Derive the Architecture

**Start with synchronous Stripe call + DB write in a transaction:**
```
BEGIN TRANSACTION;
  charge = stripe.charge(card, amount)  -- external API, not in transaction scope
  INSERT INTO orders (status='PAID')    -- DB write, in transaction scope
COMMIT;
```

**What breaks when Stripe times out?** The HTTP call to Stripe hangs for 30 seconds, then times out. Your code rolls back the transaction. But did Stripe charge the card or not? You don't know. If you retry, you might double-charge. If you don't, you might lose a legitimate payment. **Fix: idempotency keys.** Before calling Stripe, generate `idempotency_key = UUID()` and store it in your DB (in the same transaction as creating a `PENDING` order). Pass the key to Stripe. On timeout + retry, Stripe uses the key to deduplicate — the same key returns the same result. Your DB already has the PENDING order, so you just poll Stripe for the outcome.

**What breaks when your server crashes after Stripe succeeds but before the DB write?** `idempotency_key` is in your DB as `status=PENDING`. The customer's next request (or a background reconciliation job) retries with the same key. Stripe returns "already charged, here's the charge ID." Your DB updates to `PAID`. **Fix: explicit payment state machine** (PENDING → PROCESSING → PAID/FAILED/REFUNDED). Every state transition is a DB write. The idempotency key + state machine together ensure that any crash leaves the system in a recoverable state — there's always a PENDING record that can be reconciled.

**What breaks when user account (Shard 1) and merchant account (Shard 2) must both update atomically?** A single `BEGIN/COMMIT` cannot span two databases. 2-Phase Commit (2PC) with an external coordinator works but adds 2 round-trips (prepare + commit) and is blocked if the coordinator crashes between phases — the transaction hangs indefinitely. **Fix: Saga pattern + double-entry bookkeeping.** Model every payment as a series of compensating steps: debit user (Shard 1) → credit merchant (Shard 2). If the credit fails, fire a compensating transaction to refund the debit. Double-entry bookkeeping ensures the ledger always balances: every debit has a corresponding credit. No money is ever "in transit" without a corresponding ledger entry.

**What breaks with a shared mutable balance column?** 10K concurrent payments per second all run `UPDATE accounts SET balance = balance - amount WHERE id = user_id`. Row-level locking serializes these — throughput: ~1K TPS per account, and a hotspot account (e.g., a popular merchant receiving thousands of payments) becomes a bottleneck. **Fix: append-only ledger** (immutable credit/debit rows) instead of mutable balance. Balance = `SUM(amount) WHERE account_id = X`. Appends don't conflict; balance reads use a materialized view updated asynchronously. This is how Stripe, Coinbase, and all financial systems work — the ledger is never mutated.

**What breaks when PCI auditors ask "show me every state change for transaction T"?** A mutable `orders` table has no history. **Fix: event sourcing for the payment domain.** Every state transition is stored as an immutable event: `PaymentInitiated`, `CardCharged`, `OrderFulfilled`, `RefundIssued`. Current state is derived by replaying events. Audit log is the data model, not a side effect.

**Resulting architecture:**

```
Client → Payment Service
       → generate idempotency_key, write PENDING order (PostgreSQL, same transaction)
       → call Stripe (with idempotency_key)
       → on success: write PaymentInitiated event, update order to PROCESSING
       → Saga Orchestrator:
           Step 1: debit user account (Shard 1) → write DebitLedgerEntry
           Step 2: credit merchant account (Shard 2) → write CreditLedgerEntry
           If Step 2 fails: compensating transaction → CreditLedgerEntry (reversal) on Shard 1
       → on saga complete: update order to PAID, emit OrderPaid event

Reconciliation Job (runs every 5 min):
  SELECT * FROM orders WHERE status='PENDING' AND created_at < NOW() - 10min
  → poll Stripe for each → resolve to PAID or FAILED
```

---

## Analogy

A bank wire transfer. Once initiated, it must complete exactly once (idempotency) — initiating it twice should not move money twice. Both the sender's and receiver's accounts must update atomically (ACID) — there should never be a moment where money has left one account but hasn't arrived in the other. Every cent must be traceable forever (immutability).

The hard part: your database is under your control, but the payment gateway (Visa, Stripe) is a remote system. A database transaction cannot span your DB and their API. If your server crashes after Stripe charges the card but before your DB records it, did the payment happen? How do you know? How do you recover without charging twice?

---

## Why This Is Hard

1. **Distributed atomicity without 2PC**: The critical operation spans your database AND an external API. Traditional 2-phase commit doesn't work with Stripe. You need an idempotency + state machine approach to handle every crash scenario.
2. **Idempotency in the face of retries**: Network timeouts cause retries. A naive retry charges the customer twice. Idempotency keys must be checked and stored atomically with payment processing, which requires careful ordering of operations.
3. **Exactly-once money movement**: "At-least-once" is unacceptable (double charge). "At-most-once" is unacceptable (money vanishes). You need exactly-once semantics — harder to achieve than it sounds when the payment gateway is a black box.
4. **ACID across shards**: When a user's account is on Shard 1 and a merchant's account is on Shard 2, there is no single database transaction that covers both. You need distributed transaction protocols or eventual consistency with compensation (Saga pattern).
5. **Compliance and auditability**: PCI-DSS forbids storing raw card numbers. Every state change must be logged immutably. Regulators can audit transactions years later. The data model must support this from day one.

---

## Critical Requirements

### Functional
- Process payments (charge customer, credit merchant)
- Refunds and partial refunds
- Transaction history and reconciliation
- Fraud detection (basic)

### Non-Functional
- **Consistency**: ACID guarantees (no lost money)
- **Idempotency**: Retry-safe (no double charges)
- **Availability**: 99.99% uptime
- **Latency**: P99 < 500ms for payment
- **Compliance**: PCI-DSS (never store raw card numbers)

---

## Scale Estimation

```
Assumptions:
- 10M transactions/day
- Peak: 1,000 transactions/sec
- Average transaction: $50
- Daily volume: $500M

Storage:
Per transaction: 2KB (metadata, ledger entries)
10M/day × 2KB × 365 days = 7.3 TB/year
With audit logs (5×): 36 TB/year

Database:
Write QPS: 1,000 TPS × 2 writes (debit + credit) = 2K writes/sec
Read QPS: 10K reads/sec (balance checks, transaction history)
```

---

## Core Concepts

### 1. Double-Entry Bookkeeping

**Every transaction has TWO entries: Debit and Credit (sum to zero)**

```
User pays $100 to Merchant:

Ledger Entries:
├─ Entry 1: Account "user_wallet",     Debit:  $100  (balance decreases)
└─ Entry 2: Account "merchant_wallet", Credit: $100  (balance increases)

Total: -$100 + $100 = 0 ✓ (always balanced)
```

**Why?**
- Ensures consistency: money is never created or destroyed, only transferred
- Auditable: every cent is accounted for with two matching entries
- Reconciliation: sum of all ledger entries across all accounts must equal zero at all times

**Bank account example:**
```
Platform charges Stripe 2.9% fee:
├─ Entry 1: merchant_wallet  Debit: $2.90  (fee debited)
└─ Entry 2: platform_revenue Credit: $2.90 (fee collected)

Everything still sums to zero.
```

### 2. Idempotency

**Problem**: Network timeout, client retries

```
Client → POST /charge (request_id: abc123, amount: $100)
Server processes payment, response lost in transit.
Client retries → POST /charge (same request_id: abc123)
Server detects duplicate → returns original response (NO double charge)
```

**Implementation:**
```sql
CREATE TABLE idempotency_keys (
    request_id VARCHAR(64) PRIMARY KEY,
    response_body TEXT,
    created_at TIMESTAMP
);

-- On payment request:
IF EXISTS(SELECT 1 FROM idempotency_keys WHERE request_id = 'abc123'):
    RETURN cached_response
ELSE:
    BEGIN TRANSACTION
        -- Process payment
        INSERT INTO idempotency_keys VALUES ('abc123', response, NOW())
    COMMIT
    RETURN response
```

**Critical detail**: The idempotency key must be stored INSIDE the same database transaction as the payment. If stored separately, a crash between storing the key and completing the payment creates an inconsistent state.

### 3. State Machine

```
Payment States:
PENDING → PROCESSING → SUCCEEDED
              ↓
            FAILED

Refund States:
PENDING → PROCESSING → REFUNDED
              ↓
            FAILED
```

**State transitions are append-only**. Never update a payment record in place. Create a new state record for each transition — this gives you a full audit trail.

---

## Database Schema

```sql
-- Accounts (wallets)
CREATE TABLE accounts (
    account_id BIGINT PRIMARY KEY,
    user_id    BIGINT,
    balance    DECIMAL(19, 4) NOT NULL DEFAULT 0,  -- Never allow negative!
    currency   CHAR(3) DEFAULT 'USD',
    created_at TIMESTAMP,
    CONSTRAINT balance_positive CHECK (balance >= 0)
);

-- Transactions (high-level)
CREATE TABLE transactions (
    transaction_id    BIGINT PRIMARY KEY,
    request_id        VARCHAR(64) UNIQUE,  -- For idempotency
    type              ENUM('PAYMENT', 'REFUND', 'PAYOUT'),
    status            ENUM('PENDING', 'PROCESSING', 'SUCCEEDED', 'FAILED'),
    amount            DECIMAL(19, 4),
    currency          CHAR(3),
    from_account_id   BIGINT,
    to_account_id     BIGINT,
    payment_method_id BIGINT,
    created_at        TIMESTAMP,
    updated_at        TIMESTAMP,
    INDEX idx_from_account (from_account_id, created_at),
    INDEX idx_request_id (request_id)
);

-- Double-entry ledger (immutable — INSERT only, never UPDATE/DELETE)
CREATE TABLE ledger_entries (
    entry_id       BIGINT PRIMARY KEY,
    transaction_id BIGINT REFERENCES transactions(transaction_id),
    account_id     BIGINT REFERENCES accounts(account_id),
    debit          DECIMAL(19, 4),  -- Null or positive
    credit         DECIMAL(19, 4),  -- Null or positive
    balance_after  DECIMAL(19, 4),  -- Snapshot of account balance at write time
    created_at     TIMESTAMP,
    INDEX idx_account_time (account_id, created_at DESC)
);

-- Payment methods (tokenized, PCI-compliant)
CREATE TABLE payment_methods (
    payment_method_id BIGINT PRIMARY KEY,
    user_id           BIGINT,
    type              ENUM('CREDIT_CARD', 'BANK_ACCOUNT'),
    card_last_4       CHAR(4),       -- Only last 4 digits stored locally
    token             VARCHAR(255),  -- Gateway token (Stripe, Braintree) maps to full card
    is_default        BOOLEAN,
    created_at        TIMESTAMP
);

-- Idempotency (30-day retention)
CREATE TABLE idempotency_keys (
    request_id    VARCHAR(64) PRIMARY KEY,
    response_body TEXT,
    status_code   INT,
    created_at    TIMESTAMP,
    INDEX idx_created (created_at)  -- For cleanup job
);
```

---

## Architecture

```
┌───────────┐
│  Client   │
└─────┬─────┘
      │ POST /charge (Idempotency-Key: abc123)
      ▼
┌──────────────────┐
│  API Gateway     │ (Rate limiting, auth, TLS termination)
└─────┬────────────┘
      │
      ▼
┌──────────────────────────┐
│  Payment Service         │
│  1. Check idempotency    │
│  2. Validate balance     │
│  3. Run fraud check      │
│  4. Begin DB transaction │
└─────┬────────────────────┘
      │
      ├───────────────┬─────────────┐
      ▼               ▼             ▼
┌─────────────┐ ┌──────────┐ ┌──────────────┐
│ PostgreSQL  │ │  Ledger  │ │ Payment      │
│ (Primary)   │ │  Service │ │ Gateway API  │
│ ACID Txn    │ │          │ │ (Stripe/Visa)│
└─────────────┘ └──────────┘ └──────────────┘
      │
      ▼
┌─────────────────┐
│  Event Stream   │ (Kafka: payment.succeeded, payment.failed)
└─────┬───────────┘
      │
      ├──────────┬────────────┐
      ▼          ▼            ▼
  Analytics  Webhooks   Fraud Detection
```

---

## API Design

### Charge Payment

```http
POST /v1/payments/charge
Headers:
  Idempotency-Key: abc123-def456-ghi789
  Authorization: Bearer sk_live_xyz...

Request:
{
  "amount": 10000,       // $100.00 in cents (avoids floating-point errors)
  "currency": "USD",
  "source": "pm_1234",   // Payment method ID (tokenized card)
  "description": "Order #5678",
  "metadata": {
    "order_id": "5678",
    "customer_email": "user@example.com"
  }
}

Response: 200 OK
{
  "id": "txn_9876",
  "status": "succeeded",
  "amount": 10000,
  "currency": "USD",
  "created": 1675843200
}

Response (Duplicate — idempotency hit): 200 OK
{
  "id": "txn_9876",       // Same transaction ID as original
  "status": "succeeded",
  "from_cache": true
}
```

### Refund

```http
POST /v1/payments/{payment_id}/refund
{
  "amount": 5000,   // $50.00 (partial refund)
  "reason": "customer_request"
}

Response:
{
  "id": "refund_1111",
  "payment_id": "txn_9876",
  "amount": 5000,
  "status": "succeeded"
}
```

### Get Transaction History

```http
GET /v1/transactions?account_id=acc_123&limit=20

Response:
{
  "data": [
    {
      "id": "txn_9876",
      "type": "payment",
      "amount": -10000,        // Debit (negative)
      "balance_after": 50000,
      "created": 1675843200
    },
    {
      "id": "txn_9877",
      "type": "refund",
      "amount": 5000,          // Credit (positive)
      "balance_after": 55000,
      "created": 1675844000
    }
  ],
  "has_more": true
}
```

---

## Payment Flow (Step-by-Step)

```
1. Client Request
   POST /charge {amount: $100, idempotency_key: "abc123"}

2. Check Idempotency
   SELECT * FROM idempotency_keys WHERE request_id = 'abc123'
   → If exists: Return cached response immediately (200 OK)
   → If not exists: Continue

3. Validate
   - Check account balance >= $100
   - Verify payment method active and not expired
   - Run fraud checks (velocity, amount anomaly, geo mismatch)

4. BEGIN TRANSACTION (PostgreSQL)

   a. Create transaction record:
      INSERT INTO transactions VALUES (
        txn_9876, 'abc123', 'PAYMENT', 'PROCESSING', 100.00, ...
      )

   b. Call payment gateway (Stripe) — OUTSIDE transaction!
      POST https://api.stripe.com/v1/charges
      Response: {status: "succeeded", charge_id: "ch_xyz"}

   c. Update transaction status:
      UPDATE transactions SET status = 'SUCCEEDED' WHERE id = txn_9876

   d. Create ledger entries (double-entry):
      -- Debit user wallet
      INSERT INTO ledger_entries VALUES (
        entry_1, txn_9876, acc_user, debit: 100.00, credit: NULL,
        balance_after: (SELECT balance FROM accounts WHERE id = acc_user) - 100
      )
      UPDATE accounts SET balance = balance - 100 WHERE id = acc_user

      -- Credit merchant wallet
      INSERT INTO ledger_entries VALUES (
        entry_2, txn_9876, acc_merchant, debit: NULL, credit: 100.00,
        balance_after: (SELECT balance FROM accounts WHERE id = acc_merchant) + 100
      )
      UPDATE accounts SET balance = balance + 100 WHERE id = acc_merchant

   e. Store idempotency key:
      INSERT INTO idempotency_keys VALUES ('abc123', response, 200, NOW())

   COMMIT TRANSACTION

5. Return Response
   {id: "txn_9876", status: "succeeded"}

6. Async: Publish event
   Kafka: payment.succeeded {transaction_id: txn_9876}
```

**Critical**: Step b (gateway call) happens OUTSIDE the DB transaction. The transaction wraps only the DB operations. The gateway call is an external side-effect that cannot be rolled back.

---

## Handling Distributed Transactions

### Challenge: Payment gateway call + DB update not atomic

**Problem:**
```
BEGIN TRANSACTION
  UPDATE transactions SET status = 'PROCESSING'
  Call Stripe API → SUCCESS
  ← Network timeout here: server crashes before DB COMMIT
ROLLBACK (or connection dropped)

Outcome: Stripe charged the customer. Our DB has no record.
```

**Solution 1: Idempotency + Retry**
```
1. Store idempotency_key in DB BEFORE calling gateway
2. Call gateway with the same idempotency_key forwarded to Stripe
3. On retry, check our idempotency_key → already exists → query Stripe for status
4. Stripe API is idempotent: same key = same result, no double charge
```

**Solution 2: Saga Pattern (Compensating Transactions)**
```
State Machine with compensation steps:

PENDING
  → [call gateway]
GATEWAY_CALLED
  → [gateway succeeded]
GATEWAY_SUCCEEDED
  → [update ledger]
LEDGER_UPDATED / COMPLETED

On any failure:
GATEWAY_CALLED + failure → issue refund to gateway (compensation)
LEDGER_UPDATED + failure → reverse ledger entries (compensation)

Each step is idempotent. Each compensation is also idempotent.
Reconciliation job detects stuck transactions and runs compensation.
```

**Solution 3: 2-Phase Commit (Avoid)**
```
Phase 1: Prepare
- Reserve funds in DB (debit from escrow)
- Pre-authorize on gateway

Phase 2: Commit
- Finalize DB (move escrow to merchant)
- Capture on gateway

Problem: Complex, doubles latency, external gateways don't support XA protocol.
Verdict: Use Saga pattern instead.
```

---

## Reconciliation

**Daily Ledger Integrity Check:**
```sql
-- Sum of all ledger entries must equal zero (accounting identity)
SELECT SUM(debit) - SUM(credit) AS balance_check
FROM ledger_entries
WHERE DATE(created_at) = '2026-05-12';

-- Expected: 0
-- If not 0 → CRITICAL ALERT (accounting error — page immediately!)
```

**Gateway Reconciliation (End of Day):**
```
Compare:
1. Our transactions table (status = SUCCEEDED)
2. Stripe's settled transactions report

Mismatches:
- In our DB but not Stripe: investigate — potential phantom transaction
- In Stripe but not our DB: crashed mid-payment — create ledger entry, mark SUCCEEDED
- Amount mismatch: investigate gateway fee rounding
```

---

## Scaling

### Database Sharding
```
Shard by account_id (hash-based):
Shard 0: account_id % 4 = 0
Shard 1: account_id % 4 = 1
...

Challenge: Cross-shard transactions (user on Shard 0 paying merchant on Shard 2)
Solution:
  Option A: Distributed transaction (complex, slow, avoid)
  Option B: Saga pattern — debit user on Shard 0, async credit merchant on Shard 2
            With compensation (refund user) if credit step fails
```

### Read Replicas
```
Writes: Primary DB (ACID, synchronous replication to standby)
Reads: Replicas (transaction history, balance checks, reporting)
Replication lag: < 1 second (acceptable for history; not for balance checks before payment)

For balance checks before payment: Read from PRIMARY to avoid stale balance.
```

---

## Security & Compliance

### PCI-DSS Compliance
```
✅ Never store full card numbers (use gateway tokens — Stripe/Braintree handles PAN)
✅ Encrypt data at rest (TDE — Transparent Data Encryption)
✅ Encrypt data in transit (TLS 1.3+)
✅ Regular security audits and penetration testing
✅ Access controls (least privilege — payment service cannot read raw card data)
✅ Network segmentation (payment DB on isolated subnet)
```

### Fraud Detection (Basic)
```
Rules (applied synchronously in payment flow):
- Velocity check: >3 failed attempts in 1 hour for same card → block
- Amount check: Transaction > $1,000 → additional MFA challenge
- Geo check: Card country ≠ IP country → SMS challenge
- New card + large amount: Hold for manual review

ML model (async, applied to flag suspicious patterns post-authorization):
- Train on historical charge-backs and fraud labels
- Score transactions in real-time; flag outliers for review
```

---

## Failure Scenarios

### Gateway Timeout
```
Scenario: POST /charge to Stripe returns 504 Gateway Timeout.
We don't know if the charge happened.

Response protocol:
1. Log the attempt with idempotency_key and 'UNCERTAIN' status.
2. Asynchronously query Stripe: GET /v1/charges/{idempotency_key_status}
3. If Stripe confirms charge: mark SUCCEEDED, create ledger entries.
4. If Stripe has no record: retry safely with same idempotency_key.
5. If cannot resolve within SLA: mark PENDING, surface to ops dashboard.
```

### Database Primary Failure
```
Impact: Cannot process payments (require ACID writes).
Recovery:
- Patroni / repmgr auto-promotes standby within 30-60 seconds.
- Write-ahead log (WAL) ensures zero data loss (synchronous replication to standby).
- RTO: < 2 min, RPO: 0 (synchronous replication)

During failover: Return 503 to clients (retry with idempotency key is safe).
```

### Double-Charge (Bug Scenario)
```
If idempotency key is accidentally NOT checked:
- Two threads process same request_id simultaneously.
- Both call gateway → two charges.

Mitigation:
- Idempotency check + INSERT done under a DB unique constraint on request_id.
- Unique constraint causes one transaction to fail with duplicate key error.
- Only one payment proceeds.
- This is why the idempotency key column has a UNIQUE index.
```

---

## Monitoring

```
Metrics:
- Payment success rate: >= 98% (SLO)
- Latency P99: < 500ms
- Ledger balance check: SUM(entries) = 0 (daily — automated test)
- Gateway error rate: < 1%
- Fraud flag rate: 0.1-1% (higher = possible attack, lower = detector might be broken)

Alerts:
P0: Ledger imbalance detected (SUM ≠ 0) → Page immediately, freeze writes
P0: Payment success rate < 95% for 5 minutes
P1: Gateway timeout rate > 5% (gateway degraded)
P2: Latency P99 > 1 second
```

---

## Interview Talking Points

**Q: "How do you ensure no double charging?"**
- A: "Idempotency keys. The client generates a UUID per payment attempt and sends it as a header. We store it in a table with a UNIQUE constraint. Before processing, we check if the key exists — if yes, return the cached response. The key insertion happens inside the same DB transaction as the payment, so a crash can't create a state where the key is stored but the payment isn't (or vice versa)."

**Q: "What if the payment gateway succeeds but your DB update fails?"**
- A: "This is the core distributed transaction problem. We handle it with the Saga pattern. The transaction is recorded with status PROCESSING before we call the gateway. We pass our idempotency_key to Stripe so they deduplicate retries on their end. On DB failure, a background reconciliation job queries Stripe for the status and updates our DB. The customer is never double-charged because Stripe deduplicates on the same key."

**Q: "How do you maintain data consistency across shards?"**
- A: "For same-shard transactions: standard ACID. For cross-shard (user on Shard A, merchant on Shard B): we use the Saga pattern — debit user first (Shard A transaction), then credit merchant (Shard B transaction). If the credit fails, a compensating transaction refunds the user. There is a brief window of inconsistency, but money is never lost — it either completes or rolls back."

**Q: "Stripe vs building in-house?"**
- Stripe: Faster to market, PCI-compliant out of the box, costs 2.9% + 30¢ per transaction
- In-house: Cheaper at scale (>$1B volume), direct bank relationships, full control
- Rule of thumb: Use Stripe until you're processing >$1B/year, then evaluate in-house

---

## Interview Questions Asked

### Stripe
1. **"Design Stripe's payment processing system."** → Tests idempotency and PSP integration depth; key answer: client-generated idempotency keys with UNIQUE DB constraint, PROCESSING status before PSP call, saga-based rollback, async reconciliation for uncertain outcomes.

### Amazon
1. **"How do you guarantee exactly-once payment processing?"** → Tests distributed transaction fundamentals; key answer: idempotency key stored in same DB transaction as payment record; PSP called with the same key so they deduplicate retries; reconciliation job resolves ambiguous outcomes.

### Common Follow-ups
1. **"How do you handle partial network failure between charge and confirm?"** → Tests saga/reconciliation design; record status as `PROCESSING` before calling PSP; if timeout occurs, a background reconciler polls PSP with the original idempotency key to determine true outcome and updates local state.
2. **"What is the idempotency key lifecycle — does it expire?"** → Tests key management depth; TTL should match the refund window (e.g., 90 days); after expiry, a new payment with the same key is treated as a new charge; keys stored in a dedicated table with a TTL column and a scheduled cleanup job.
3. **"How do you reconcile when PSP says success but you timed out?"** → Tests recovery design; reconciliation job runs every 5 minutes, queries PSP API for all `PROCESSING` payments older than 30s, updates local status to match; idempotency ensures no double charge — PSP already has the result.
4. **"How does double-entry bookkeeping work in implementation?"** → Tests ledger design; every payment creates two ledger entries (debit user account, credit merchant account) in a single DB transaction; `SUM(amount) = 0` across all entries at all times — verified by a daily automated audit job; imbalance pages on-call immediately.
