# Design Payment System (Stripe/PayPal)

> **Difficulty**: Hard  
> **Topics**: ACID Transactions, Double-Entry Bookkeeping, Idempotency, Distributed Transactions  
> **Time**: 75 minutes  
> **Companies**: Stripe, PayPal, Square, Adyen

## Problem Statement

Design a payment processing system that:
- Handles credit card payments
- Ensures no double-charging
- Maintains accurate account balances
- Supports refunds and chargebacks
- Provides transaction history
- Integrates with external payment gateways (Visa, MasterCard)

## Critical Requirements

### Functional
- Process payments (charge customer, credit merchant)
- Refunds and partial refunds
- Transaction history and reconciliation
- Fraud detection (basic)

### Non-Functional
- **Consistency**: ACID guarantees (no lost money!)
- **Idempotency**: Retry-safe (no double charges)
- **Availability**: 99.99% uptime
- **Latency**: P99 < 500ms for payment
- **Compliance**: PCI-DSS (never store raw card numbers)

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
Write QPS: 1,000 TPS (transactions/sec) × 2 writes (debit + credit) = 2K writes/sec
Read QPS: 10K reads/sec (balance checks, history)
```

## Core Concepts

### 1. Double-Entry Bookkeeping

**Every transaction has TWO entries: Debit and Credit (sum to zero)**

```
User pays $100 to Merchant:

Ledger Entries:
├─ Entry 1: Account "user_wallet", Debit: $100  (decrease)
└─ Entry 2: Account "merchant_wallet", Credit: $100  (increase)

Total: -$100 + $100 = 0 ✓ (balanced)
```

**Why?**
- Ensures consistency (money never created/destroyed)
- Auditable (every cent accounted for)
- Reconciliation (sum of all entries = 0)

### 2. Idempotency

**Problem**: Network timeout, retried request

```
Client → POST /charge (request_id: abc123, amount: $100)
Server processes, but response lost
Client retries → POST /charge (same request_id: abc123)
Server detects duplicate → returns original response (no double charge)
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

## Database Schema

```sql
-- Accounts (wallets)
CREATE TABLE accounts (
    account_id BIGINT PRIMARY KEY,
    user_id BIGINT,
    balance DECIMAL(19, 4) NOT NULL DEFAULT 0,  -- Never allow negative!
    currency CHAR(3) DEFAULT 'USD',
    created_at TIMESTAMP,
    CONSTRAINT balance_positive CHECK (balance >= 0)
);

-- Transactions (high-level)
CREATE TABLE transactions (
    transaction_id BIGINT PRIMARY KEY,
    request_id VARCHAR(64) UNIQUE,  -- For idempotency
    type ENUM('PAYMENT', 'REFUND', 'PAYOUT'),
    status ENUM('PENDING', 'PROCESSING', 'SUCCEEDED', 'FAILED'),
    amount DECIMAL(19, 4),
    currency CHAR(3),
    from_account_id BIGINT,
    to_account_id BIGINT,
    payment_method_id BIGINT,  -- Credit card, bank account
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    INDEX idx_from_account (from_account_id, created_at),
    INDEX idx_request_id (request_id)
);

-- Double-entry ledger
CREATE TABLE ledger_entries (
    entry_id BIGINT PRIMARY KEY,
    transaction_id BIGINT REFERENCES transactions(transaction_id),
    account_id BIGINT REFERENCES accounts(account_id),
    debit DECIMAL(19, 4),  -- Null or positive
    credit DECIMAL(19, 4),  -- Null or positive
    balance_after DECIMAL(19, 4),  -- Snapshot of account balance
    created_at TIMESTAMP,
    INDEX idx_account_time (account_id, created_at DESC)
);

-- Payment methods (tokenized, PCI-compliant)
CREATE TABLE payment_methods (
    payment_method_id BIGINT PRIMARY KEY,
    user_id BIGINT,
    type ENUM('CREDIT_CARD', 'BANK_ACCOUNT'),
    card_last_4 CHAR(4),  -- Only last 4 digits stored
    token VARCHAR(255),  -- Gateway token (Stripe, Braintree)
    is_default BOOLEAN,
    created_at TIMESTAMP
);

-- Idempotency (30-day retention)
CREATE TABLE idempotency_keys (
    request_id VARCHAR(64) PRIMARY KEY,
    response_body TEXT,
    status_code INT,
    created_at TIMESTAMP,
    INDEX idx_created (created_at)  -- For cleanup
);
```

## Architecture

```
┌───────────┐
│  Client   │
└─────┬─────┘
      │ POST /charge (idempotency_key: abc123)
      ▼
┌──────────────────┐
│  API Gateway     │ (Rate limiting, auth)
└─────┬────────────┘
      │
      ▼
┌──────────────────────────┐
│  Payment Service         │
│  1. Check idempotency    │
│  2. Validate balance     │
│  3. Start transaction    │
└─────┬────────────────────┘
      │
      ├───────────────┬─────────────┐
      ▼               ▼             ▼
┌─────────────┐ ┌──────────┐ ┌──────────────┐
│ PostgreSQL  │ │  Ledger  │ │ Payment      │
│ (Primary)   │ │  Service │ │ Gateway API  │
│ ACID Txn    │ │          │ │ (Stripe)     │
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

## API Design

### Charge Payment

```http
POST /v1/payments/charge
Headers:
  Idempotency-Key: abc123-def456-ghi789
  Authorization: Bearer sk_live_xyz...
  
Request:
{
  "amount": 10000,  // $100.00 (cents to avoid floating point)
  "currency": "USD",
  "source": "pm_1234",  // Payment method ID (tokenized card)
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

Response (Duplicate): 200 OK
{
  "id": "txn_9876",  // Same as original
  "status": "succeeded",
  "from_cache": true  // Idempotency hit
}
```

### Refund

```http
POST /v1/payments/{payment_id}/refund
{
  "amount": 5000,  // $50.00 (partial refund)
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
      "amount": -10000,  // Debit (negative)
      "balance_after": 50000,
      "created": 1675843200
    },
    {
      "id": "txn_9877",
      "type": "refund",
      "amount": 5000,  // Credit (positive)
      "balance_after": 55000,
      "created": 1675844000
    }
  ],
  "has_more": true
}
```

## Payment Flow (Step-by-Step)

```
1. Client Request
   POST /charge {amount: $100, idempotency_key: "abc123"}

2. Check Idempotency
   SELECT * FROM idempotency_keys WHERE request_id = 'abc123'
   → If exists: Return cached response (200 OK)
   → If not exists: Continue

3. Validate
   - Check account balance >= $100
   - Verify payment method active
   - Run fraud checks (velocity, amount anomaly)

4. BEGIN TRANSACTION (Database)
   
   a. Create transaction record:
      INSERT INTO transactions VALUES (
        txn_9876, 'abc123', 'PAYMENT', 'PROCESSING', 100.00, ...
      )
   
   b. Call payment gateway (Stripe):
      POST https://api.stripe.com/v1/charges
      Response: {status: "succeeded", charge_id: "ch_xyz"}
   
   c. Update transaction status:
      UPDATE transactions SET status = 'SUCCEEDED' WHERE id = txn_9876
   
   d. Create ledger entries (double-entry):
      -- Debit user wallet
      INSERT INTO ledger_entries VALUES (
        entry_1, txn_9876, acc_user, debit: 100.00, credit: NULL, ...
      )
      UPDATE accounts SET balance = balance - 100 WHERE id = acc_user
      
      -- Credit merchant wallet
      INSERT INTO ledger_entries VALUES (
        entry_2, txn_9876, acc_merchant, debit: NULL, credit: 100.00, ...
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

**Key**: Everything in ONE database transaction (ACID guarantees)

## Handling Distributed Transactions

### Challenge: Payment gateway call + DB update not atomic

**Problem:**
```
BEGIN TRANSACTION
  -- Update DB: status = PROCESSING
  Call Stripe API → SUCCESS
  Network timeout before DB commit ← CRASH!
ROLLBACK (lost state!)
```

**Solution 1: Idempotency + Retry**
```
1. Store request_id BEFORE gateway call
2. On retry, check request_id → if exists, don't re-charge
3. Stripe API is idempotent (safe to retry)
```

**Solution 2: Saga Pattern**
```
State Machine:
PENDING → call_gateway → GATEWAY_SUCCEEDED → update_ledger → COMPLETED
                ↓ (failure)
              ROLLBACK (issue refund)
```

**Solution 3: 2-Phase Commit (Avoid if possible)**
```
Phase 1: Prepare
- Reserve funds in DB
- Pre-authorize on gateway

Phase 2: Commit
- Finalize DB
- Capture on gateway

Problem: Complex, slow, gateway may not support
```

## Reconciliation

**Daily Reconciliation:**
```sql
-- Check ledger integrity (sum should be 0)
SELECT SUM(debit) - SUM(credit) AS balance_check
FROM ledger_entries
WHERE DATE(created_at) = '2026-02-08';

-- Expected: 0
-- If not 0 → CRITICAL ALERT (accounting error!)
```

**Gateway Reconciliation:**
```
Compare:
1. Our transactions table (status = SUCCEEDED)
2. Stripe's settled transactions

Mismatch? Investigate:
- Failed to record in our DB
- Gateway duplicate charge
```

## Scaling

### Database Sharding
```
Shard by account_id (hash-based):
Shard 1: account_id % 4 = 0
Shard 2: account_id % 4 = 1
...

Challenge: Cross-shard transactions (user → merchant on different shards)
Solution: Distributed transaction OR eventual consistency
```

### Read Replicas
```
Writes: Primary DB (ACID)
Reads: Replicas (transaction history, balance checks)
Replication lag: < 1 second (acceptable for history)
```

## Security & Compliance

### PCI-DSS Compliance
```
✅ Never store full card numbers (use tokens)
✅ Encrypt data at rest (TDE - Transparent Data Encryption)
✅ Encrypt data in transit (TLS 1.2+)
✅ Regular security audits
✅ Access controls (least privilege)
```

### Fraud Detection (Basic)
```
Rules:
- Velocity check: >3 failed attempts in 1 hour → block
- Amount check: Transaction > $1,000 → manual review
- Geo check: Card country ≠ IP country → challenge
- ML model: Anomaly detection (train on historical data)
```

## Failure Scenarios

### Gateway Timeout
```
Impact: Don't know if charge succeeded
Mitigation:
1. Check transaction status via gateway API
2. If uncertain, mark as PENDING, investigate manually
3. Idempotency prevents double-charge on retry
```

### Database Failure
```
Impact: Cannot process payments
Mitigation:
- Failover to standby (auto via Patroni/repmgr)
- Transaction log replay (no data loss)
- RTO: < 5 min, RPO: 0 (synchronous replication)
```

## Monitoring

```
Metrics:
- Payment success rate: 98%+ (SLO)
- Latency P99: < 500ms
- Ledger balance check: SUM(entries) = 0 (daily)
- Gateway error rate: < 1%

Alerts:
P0: Ledger imbalance detected (SUM ≠ 0) → Page immediately!
P1: Payment success rate < 95% for 5 min
P2: Latency P99 > 1 second
```

## Interview Tips

**Q: "How do you ensure no double charging?"**
- A: "Idempotency keys stored before processing. Retry uses same key, we detect and return cached response."

**Q: "What if payment gateway succeeds but DB update fails?"**
- A: "Store idempotency key BEFORE gateway call. On retry, check key → don't re-charge. Issue refund if needed (Saga pattern)."

**Q: "How do you maintain data consistency across shards?"**
- A: "For same-shard: ACID transaction. Cross-shard: 2PC (slow) OR eventual consistency with compensation (refund on failure)."

**Q: "Stripe vs building in-house?"**
- Stripe: Faster, PCI-compliant, expensive (2.9% + 30¢)
- In-house: Cheaper at scale, PCI-DSS burden, complex
