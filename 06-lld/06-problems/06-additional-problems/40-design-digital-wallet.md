> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design a Digital Wallet Service (like PayPal/Venmo/PayTM) — a money-movement system that tests whether you can model financial correctness under concurrency: atomic double-entry transfers, idempotent retries, and deadlock-free locking.
>
> **Key concepts:**
> - Core Entities: `User`, `Wallet` (holds balance), `Transaction` (immutable double-entry ledger row: DEBIT + CREDIT pair per transfer), `TransferRequest` (client-facing intent, keyed by idempotency key), `LinkedBankAccount` (top-up source).
> - The problem: move money between two wallets atomically — either both the debit and credit happen, or neither does — while never losing money (conservation), never double-processing a retried request, and never deadlocking two wallets transferring in opposite directions.
> - Patterns:
>   - Double-entry ledger: every transfer produces two immutable `Transaction` rows (debit leg + credit leg) that net to zero — the ledger is the source of truth, not a mutable balance field alone.
>   - Idempotency key + dedupe table: client-supplied key maps to a stored result so retries return the original outcome instead of re-executing.
>   - Lock ordering by wallet ID: always acquire the lower-ID wallet's lock first to break circular wait and prevent deadlock.
> - Concurrency: `transfer()` must lock both wallets, debit one and credit the other atomically, and do so in a globally consistent lock order regardless of transfer direction.
>
> **Key takeaway:** A wallet service is a ledger problem wearing an app's clothing. Get the double-entry model and the locking order right first — the REST layer and fraud rules are secondary.

---
module: 06-lld
topic: Problems
status: unread
tags: [06-lld, lld, digital-wallet, ledger, concurrency, idempotency]
---
# Design a Digital Wallet Service

> **Difficulty**: Hard
> **Asked at**: PayPal, Stripe, Square, PhonePe, Google Pay
> **Key Patterns**: Double-Entry Ledger, Idempotency Key + Dedupe Table, Lock Ordering (deadlock avoidance), Command (TransferRequest)

---

## Understanding the Problem

Design a digital wallet service (like PayPal/Venmo/PayTM) that lets a user hold a balance, transfer money to another user's wallet, and top up their wallet from a linked bank account. The system must guarantee money is never created or destroyed (conservation), never double-process a retried transfer (idempotency), and never deadlock when two wallets transfer to each other concurrently in opposite directions.

---

## Clarifying Questions

**You**: "Is this peer-to-peer wallet transfers only, or do we also need external card payments, merchant checkout, and payouts?"
**Interviewer**: "Keep it tight: wallet-to-wallet transfer, and top-up from a linked bank account. No card processing, no merchant/checkout flows."

**You**: "Single currency, or do we need multi-currency wallets with FX conversion in the core design?"
**Interviewer**: "Single currency for the core design — USD only. You can discuss multi-currency as an extension."

**You**: "If a client's transfer request times out and retries, could that double-debit the sender? How should we guard against that?"
**Interviewer**: "Good question — assume the client always sends an idempotency key with each transfer request, and a retry with the same key must not double-process."

**You**: "Do we need a full transaction history / ledger per wallet, or just a current balance?"
**Interviewer**: "Full ledger. Every balance must be reconstructable from ledger entries — treat the ledger as the source of truth, not just a mutable balance counter."

**You**: "Can a wallet go negative — overdraft, or credit line?"
**Interviewer**: "No. Balance can never go below zero. Reject the transfer if the sender has insufficient funds."

**You**: "Is top-up synchronous — does the bank debit happen instantly — or do we model it as pending until confirmed?"
**Interviewer**: "Model it as synchronous for this design: call an external `BankGateway`, and only credit the wallet after the gateway confirms. You can mention async/webhook confirmation as a real-world nuance."

**You**: "Do transfers need to support partial failure recovery, e.g. a saga across services, or is this a single-service, single-database design?"
**Interviewer**: "Single service, single database — assume the ledger table and wallet balances live in the same transactional store, so you can rely on local ACID transactions plus in-process locking."

---

## Final Requirements

**In scope:**
1. Create a wallet for a user with a zero starting balance
2. Transfer money from one wallet to another, atomically, with an idempotency key
3. Top up a wallet from a linked bank account (external gateway call, then credit on confirmation)
4. Maintain a full double-entry transaction ledger — every money movement is two immutable rows (debit + credit) that net to zero
5. Retrieve transaction history for a wallet
6. Reject transfers that would take a wallet negative
7. Thread-safe, deadlock-free concurrent transfers, including two wallets transferring to each other simultaneously in opposite directions
8. Exactly-once processing of a transfer under client retries (same idempotency key)

**Out of scope:**
- Multi-currency / FX conversion (discussed as extension only)
- Card processing, merchant checkout, payment links
- Fraud detection, AML/KYC, chargebacks, disputes
- Distributed transactions across multiple services/databases (sagas, 2PC)
- Rate limiting, authentication/authorization

---

## Core Entities and Relationships

| Entity | Responsibility |
|--------|---------------|
| User | Identity that owns exactly one Wallet |
| Wallet | Holds current balance (cached, derived from ledger); unit of locking |
| Transaction | Immutable double-entry ledger row: one leg (DEBIT or CREDIT) of a money movement |
| TransferRequest | Client-facing intent: fromWallet, toWallet, amount, idempotencyKey |
| LinkedBankAccount | External funding source for top-up |
| IdempotencyRecord | Maps idempotencyKey -> completed TransferResult, for dedupe |

A `User` owns one `Wallet`. Every transfer or top-up produces a pair of `Transaction` rows sharing a `transferId`: a `DEBIT` row on the source wallet and a `CREDIT` row on the destination wallet, with equal `amount` — this is the double-entry invariant. `Wallet.balance` is a cached, denormalized value kept consistent with the ledger inside the same atomic operation; the ledger remains the audit source of truth. `TransferRequest` carries the client-supplied `idempotencyKey`, which the service maps in `IdempotencyRecord` to the already-computed result so retries are safe. `LinkedBankAccount` is only involved in top-up, which produces a single-legged external debit (modeled as a `CREDIT` transaction on the wallet with `source=EXTERNAL_TOPUP`, no matching internal debit since the money enters the system from outside).

---

## Class Design

### Wallet

| Requirement | What Wallet must track |
|-------------|------------------------|
| Current spendable balance | balance: BigDecimal (cents-based, never floating point) |
| Unique, sortable identity for lock ordering | wallet_id: long (monotonic, comparable) |
| Concurrency control | _lock: ReentrantLock (one per wallet) |

```
class Wallet:
- wallet_id: long                # comparable — used for lock ordering
- user_id: str
- balance: BigDecimal            # cents; source-of-truth is the ledger, this is a cache
- lock: ReentrantLock

+ get_balance() -> BigDecimal
+ debit(amount: BigDecimal) -> None   # caller must hold lock
+ credit(amount: BigDecimal) -> None  # caller must hold lock
```

### Transaction (ledger entry)

```
class Transaction:                  # immutable
- transaction_id: str
- transfer_id: str                  # groups the DEBIT+CREDIT pair
- wallet_id: long
- type: EntryType                   # DEBIT, CREDIT
- amount: BigDecimal
- balance_after: BigDecimal         # snapshot for audit/history display
- source: TransactionSource         # P2P_TRANSFER, EXTERNAL_TOPUP
- timestamp: datetime
```

### TransferRequest

```
class TransferRequest:
- idempotency_key: str
- from_wallet_id: long
- to_wallet_id: long
- amount: BigDecimal
```

### IdempotencyRecord

```
class IdempotencyRecord:
- idempotency_key: str
- transfer_id: str
- status: RequestStatus            # COMPLETED, FAILED
- result: TransferResult | None    # cached response to replay on retry
- created_at: datetime
```

### LinkedBankAccount / BankGateway

```
class LinkedBankAccount:
- account_id: str
- user_id: str
- external_ref: str                # token at the bank/PSP

class BankGateway:                  # abstract — external dependency
+ pull_funds(account: LinkedBankAccount, amount: BigDecimal) -> GatewayResult
```

### WalletService (orchestrator)

```
class WalletService:
- wallets: dict[long, Wallet]
- ledger: list[Transaction]           # append-only
- idempotency_store: dict[str, IdempotencyRecord]
- idempotency_lock: Lock              # guards the idempotency_store
- bank_gateway: BankGateway

+ transfer(request: TransferRequest) -> TransferResult
+ top_up(wallet_id: long, amount: BigDecimal, account: LinkedBankAccount) -> TransferResult
+ get_transaction_history(wallet_id: long) -> list[Transaction]
- lock_wallets_in_order(w1: Wallet, w2: Wallet) -> None
```

**Why lock ordering matters:** if thread A transfers wallet 1 → wallet 2 and locks 1 then 2, while thread B concurrently transfers wallet 2 → wallet 1 and locks 2 then 1, each thread holds one lock and waits for the other — classic circular wait, deadlock. The fix: both threads must acquire locks in the same global order (by `wallet_id`, ascending), regardless of transfer direction. Whichever wallet has the smaller ID is always locked first.

---

## Implementation

### Core Method: transfer

**Core logic:**
1. Check `idempotency_store` for `request.idempotency_key` under `idempotency_lock`. If found, return the cached result immediately (no re-execution).
2. Reserve the idempotency key (mark IN_PROGRESS) so a concurrent retry blocks rather than races — release the store lock before doing wallet work.
3. Determine lock order by comparing `wallet_id`s; lock the lower ID first, then the higher.
4. Re-check sender balance under lock; if insufficient, unlock, record a FAILED idempotency result, throw.
5. Debit sender, credit receiver, append two `Transaction` ledger rows sharing a `transferId`, all while holding both locks.
6. Unlock in reverse order; store the COMPLETED result under the idempotency key; return it.

**Edge cases:**
- `fromWalletId == toWalletId` — reject immediately, no self-transfer.
- Retry with same idempotency key but different amount/wallets — return the original cached result (key wins; do not silently apply the new params). In production this would be flagged as a client bug (409-style conflict); here we defensively return the original result.
- Insufficient balance — reject, still record the FAILED outcome under the idempotency key so retries don't re-attempt a doomed transfer against fresh state without intent.

```java
public TransferResult transfer(TransferRequest request) {
    synchronized (idempotencyLock) {
        IdempotencyRecord existing = idempotencyStore.get(request.getIdempotencyKey());
        if (existing != null) {
            return existing.getResult();
        }
    }

    if (request.getFromWalletId() == request.getToWalletId()) {
        throw new IllegalArgumentException("Cannot transfer to the same wallet");
    }

    Wallet from = wallets.get(request.getFromWalletId());
    Wallet to = wallets.get(request.getToWalletId());
    if (from == null || to == null) {
        throw new NoSuchElementException("Unknown wallet in transfer request");
    }

    Wallet first = from.getWalletId() < to.getWalletId() ? from : to;
    Wallet second = from.getWalletId() < to.getWalletId() ? to : from;

    first.getLock().lock();
    try {
        second.getLock().lock();
        try {
            synchronized (idempotencyLock) {
                IdempotencyRecord existing = idempotencyStore.get(request.getIdempotencyKey());
                if (existing != null) {
                    return existing.getResult();
                }
            }

            if (from.getBalance().compareTo(request.getAmount()) < 0) {
                TransferResult failed = TransferResult.failed(
                    request.getIdempotencyKey(), "Insufficient funds");
                recordIdempotencyResult(request.getIdempotencyKey(), null, failed);
                throw new InsufficientFundsException(
                    "Wallet " + from.getWalletId() + " has insufficient funds");
            }

            String transferId = UUID.randomUUID().toString();
            Instant now = Instant.now();

            from.debit(request.getAmount());
            Transaction debitLeg = new Transaction(
                UUID.randomUUID().toString(), transferId, from.getWalletId(),
                EntryType.DEBIT, request.getAmount(), from.getBalance(),
                TransactionSource.P2P_TRANSFER, now);

            to.credit(request.getAmount());
            Transaction creditLeg = new Transaction(
                UUID.randomUUID().toString(), transferId, to.getWalletId(),
                EntryType.CREDIT, request.getAmount(), to.getBalance(),
                TransactionSource.P2P_TRANSFER, now);

            synchronized (ledger) {
                ledger.add(debitLeg);
                ledger.add(creditLeg);
            }

            TransferResult result = TransferResult.success(
                request.getIdempotencyKey(), transferId, from.getBalance(), to.getBalance());
            recordIdempotencyResult(request.getIdempotencyKey(), transferId, result);
            return result;
        } finally {
            second.getLock().unlock();
        }
    } finally {
        first.getLock().unlock();
    }
}

private void recordIdempotencyResult(String key, String transferId, TransferResult result) {
    synchronized (idempotencyLock) {
        IdempotencyRecord record = new IdempotencyRecord(
            key, transferId,
            result.isSuccess() ? RequestStatus.COMPLETED : RequestStatus.FAILED,
            result, Instant.now());
        idempotencyStore.put(key, record);
    }
}
```

### Core Method: topUp

**Core logic:**
1. Call `bankGateway.pullFunds(account, amount)` — an external, potentially slow call. Do this **before** acquiring the wallet lock, to keep the lock hold time short and avoid blocking other wallet operations on network I/O.
2. If the gateway declines, return a FAILED result without touching the wallet.
3. On confirmation, lock just the single wallet, credit it, and append one `CREDIT` ledger row with `source=EXTERNAL_TOPUP`.

```java
public TransferResult topUp(long walletId, BigDecimal amount, LinkedBankAccount account) {
    GatewayResult gatewayResult = bankGateway.pullFunds(account, amount);
    if (!gatewayResult.isSuccess()) {
        return TransferResult.failed(null, "Bank gateway declined: " + gatewayResult.getReason());
    }

    Wallet wallet = wallets.get(walletId);
    if (wallet == null) {
        throw new NoSuchElementException("Unknown wallet: " + walletId);
    }

    wallet.getLock().lock();
    try {
        wallet.credit(amount);
        Transaction topUpLeg = new Transaction(
            UUID.randomUUID().toString(), UUID.randomUUID().toString(), walletId,
            EntryType.CREDIT, amount, wallet.getBalance(),
            TransactionSource.EXTERNAL_TOPUP, Instant.now());
        synchronized (ledger) {
            ledger.add(topUpLeg);
        }
        return TransferResult.success(null, topUpLeg.getTransferId(), null, wallet.getBalance());
    } finally {
        wallet.getLock().unlock();
    }
}
```

### Core Method: getTransactionHistory

```java
public List<Transaction> getTransactionHistory(long walletId) {
    synchronized (ledger) {
        List<Transaction> history = new ArrayList<>();
        for (Transaction t : ledger) {
            if (t.getWalletId() == walletId) {
                history.add(t);
            }
        }
        history.sort(Comparator.comparing(Transaction::getTimestamp));
        return history;
    }
}
```

---

## Verification

**Scenario**: Wallet A (id=1) has $100.00. Wallet B (id=2) has $50.00. A transfers $30.00 to B with idempotency key `"key-1"`.

1. `transfer(TransferRequest("key-1", from=1, to=2, amount=30.00))`
2. No existing record for `"key-1"` in `idempotency_store`
3. Lock order: wallet 1 < wallet 2, so lock Wallet A first, then Wallet B
4. Re-check under lock: A's balance $100.00 >= $30.00 — sufficient
5. `A.debit(30.00)` → A.balance = $70.00
6. Ledger row 1: `transferId=T1, walletId=1, type=DEBIT, amount=30.00, balanceAfter=70.00`
7. `B.credit(30.00)` → B.balance = $80.00
8. Ledger row 2: `transferId=T1, walletId=2, type=CREDIT, amount=30.00, balanceAfter=80.00`
9. Unlock B, then unlock A
10. `idempotency_store["key-1"]` = COMPLETED, result = success(T1, fromBalance=70.00, toBalance=80.00)
11. Total system balance before: $100 + $50 = $150.00. Total after: $70 + $80 = $150.00 — **conserved**

**Retry with the same key** (client resends after a timeout):
1. `transfer(TransferRequest("key-1", from=1, to=2, amount=30.00))` again
2. `idempotency_store["key-1"]` found → return the cached `TransferResult` immediately
3. No debit/credit applied a second time — A stays at $70.00, B stays at $80.00

---

## Deep Dive & Extensibility

### 1. How do you guarantee exactly-once transfer under network retries?

An idempotency key from the client is the contract: "this logical transfer happens at most once, no matter how many times you receive the HTTP request." The service maps `idempotencyKey -> TransferResult` in a dedupe table (`idempotency_store` above; in production this is a unique-indexed DB table, not an in-memory map, so it survives restarts and works across service instances).

The subtle bug to avoid: checking the dedupe table and then doing the transfer are two separate steps — a naive implementation has a race where two concurrent retries both pass the "not found" check before either writes the result. The fix shown in `transfer()` above is to **re-check the idempotency store a second time after acquiring the wallet locks**, so the actual debit/credit and the idempotency write happen while holding a lock that also serializes concurrent retries of the same key (since both retries target the same wallets, they contend for the same locks).

```java
public TransferResult replaySafeTransfer(TransferRequest request) {
    // First check: fast path, avoids locking wallets for known-duplicate requests.
    IdempotencyRecord cached = lookup(request.getIdempotencyKey());
    if (cached != null) return cached.getResult();

    // ... acquire wallet locks in order ...

    // Second check: closes the race window between the first check and lock acquisition.
    cached = lookup(request.getIdempotencyKey());
    if (cached != null) return cached.getResult();

    // ... perform debit/credit + record result, still holding locks ...
    return null; // placeholder — see full transfer() above
}
```

For true multi-instance safety, the idempotency table would have a `UNIQUE` constraint on `idempotency_key`, and the write would be `INSERT ... ON CONFLICT DO NOTHING` inside the same DB transaction as the ledger inserts — the database, not application locking, becomes the arbiter across processes.

### 2. How do you prevent deadlock when two threads transfer between the same two wallets in opposite directions simultaneously?

Deadlock requires circular wait: thread A holds lock 1 and wants lock 2; thread B holds lock 2 and wants lock 1. This happens naturally if you always lock "from" before "to" — a reverse-direction transfer reverses the lock order.

The fix is **total lock ordering independent of transfer direction**: always lock the wallet with the smaller `walletId` first.

```java
private void lockInOrder(Wallet a, Wallet b, Runnable criticalSection) {
    Wallet first = a.getWalletId() < b.getWalletId() ? a : b;
    Wallet second = a.getWalletId() < b.getWalletId() ? b : a;
    first.getLock().lock();
    try {
        second.getLock().lock();
        try {
            criticalSection.run();
        } finally {
            second.getLock().unlock();
        }
    } finally {
        first.getLock().unlock();
    }
}
```

With this rule, a thread transferring 1→2 and a thread transferring 2→1 both lock wallet 1 first — one of them wins the race for wallet 1's lock, proceeds to acquire wallet 2's lock uncontended (the other thread is blocked waiting on wallet 1, not holding wallet 2), completes, and releases both. No thread ever holds one lock while blocked waiting on a lock that's held by a thread waiting on it — circular wait is structurally impossible. This is the standard **lock ordering / resource hierarchy** deadlock-avoidance technique, and it generalizes to N resources: define any total order (ID, hash, memory address) and always acquire in that order.

An alternative for very high contention is `tryLock` with a timeout and backoff-and-retry instead of blocking `lock()`, which avoids deadlock without a fixed ordering but adds retry-storm risk under load — lock ordering is simpler and preferred when a natural total order (wallet ID) already exists.

### 3. How would you support multi-currency wallets with FX conversion?

Each `Wallet` gets a `currency: Currency` field, and balances stay in that wallet's native currency — never store a converted amount as the source of truth. A cross-currency transfer becomes a two-step ledger operation instead of one:

```java
class FxTransferService {
    private final WalletService walletService;
    private final ExchangeRateProvider rateProvider;

    public TransferResult transferCrossCurrency(TransferRequest request,
                                                  Currency fromCurrency, Currency toCurrency) {
        if (fromCurrency.equals(toCurrency)) {
            return walletService.transfer(request);
        }
        BigDecimal rate = rateProvider.getRate(fromCurrency, toCurrency, Instant.now());
        BigDecimal convertedAmount = request.getAmount()
            .multiply(rate)
            .setScale(2, RoundingMode.HALF_EVEN);

        // Debit leg in fromCurrency, credit leg in toCurrency — the ledger row
        // must record both the original amount+currency and the converted
        // amount+currency+rate applied, so the entries no longer trivially net to
        // zero in a single currency; they net to zero per-currency plus an
        // explicit FX conversion record bridging the two legs.
        return walletService.transferWithConversion(
            request, convertedAmount, toCurrency, rate);
    }
}
```

Key design shifts from the single-currency model:
- The double-entry invariant becomes "debit leg amount/currency and credit leg amount/currency are linked by a recorded FX rate," not "amounts are numerically equal."
- `ExchangeRateProvider` should return a rate *and* a timestamp/quote-id, and that quote should be locked in for the duration of the transfer (rates move between request and execution) — often via a short-lived rate-lock token the client includes in the request.
- Lock ordering by `walletId` is unaffected — currency doesn't change which locks you take or in what order, since ordering is about deadlock avoidance, not about the money math.
- A wallet holding multiple currencies (rather than one wallet per currency) would instead become a `Map<Currency, BigDecimal>` balance sheet inside `Wallet`, with the same locking model but per-currency balance entries.

---

## Interviewer Questions by Level

**Junior**: Define the `Transaction` class and explain why a transfer produces two rows instead of one. Describe what fields `Wallet` needs and why balance is cached rather than always summed from the ledger on read. Can sketch the entity relationships.

**Mid-level**: Implement `transfer()` with correct locking of two wallets. Explain why an idempotency key is needed and where it's checked. Handle insufficient balance correctly, including what happens to the idempotency record on failure. Explain why the bank gateway call in `topUp` happens before acquiring the wallet lock.

**Senior**: Identify the deadlock risk in naive from-then-to locking and design the lock-ordering fix. Explain the double-check race in idempotency handling and why a single check before locking isn't sufficient. Discuss how this design would change moving from a single in-memory service to a distributed multi-instance deployment (DB-level unique constraints, `SELECT ... FOR UPDATE` or optimistic concurrency instead of in-process `ReentrantLock`). Discuss multi-currency and why the ledger invariant changes.

---

## Common Interview Questions

- Q: Why use double-entry (debit + credit rows) instead of just decrementing one balance and incrementing another? A: Auditability and conservation-of-money verification. With two ledger rows per transfer, you can independently reconstruct every wallet's balance from its own transaction history, and sum all ledger rows system-wide to prove no money was created or destroyed — a single mutable balance field gives you neither guarantee if a bug or crash corrupts it.
- Q: What happens if the process crashes between debiting the sender and crediting the receiver? A: In this in-memory design that's a real gap; in production the debit, credit, and both ledger inserts happen inside one database transaction (or the whole operation is wrapped so a crash mid-way rolls back everything) — atomicity comes from the DB transaction, not from holding locks.
- Q: Why re-check the idempotency store after acquiring the wallet locks? A: To close the race between the first check and lock acquisition — two concurrent retries with the same key could both pass the first check before either records a result. The second check, done while holding the lock that also serializes same-wallet operations, catches the duplicate.
- Q: How do you prevent the deadlock from two opposite-direction concurrent transfers? A: Total lock ordering by wallet ID — always lock the lower-ID wallet first regardless of transfer direction, so no two threads can hold-and-wait on each other's locks.
- Q: Why call the bank gateway before locking the wallet in `topUp`? A: The gateway call is slow, external I/O. Holding a wallet lock during it would block all other operations on that wallet for the duration of a network round trip; doing it first keeps the lock hold time to the in-memory credit + ledger append only.
- Q: How would you test that concurrent transfers never lose money? A: Run many threads doing random transfers between a fixed set of wallets concurrently, then assert the sum of all wallet balances afterward equals the sum before — conservation of money — and that no exception or partial state occurred outside expected insufficient-funds rejections.
- Q: What is the failure mode if you forget lock ordering and lock "from" before "to" always? A: No deadlock for one-directional flows, but two threads transferring in opposite directions between the same two wallets can deadlock — this is exactly the scenario the harness below tests.

---

## Concurrency Test Harness

```java
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

// --- Minimal stubs to make the harness self-contained ---

enum EntryType { DEBIT, CREDIT }
enum TransactionSource { P2P_TRANSFER, EXTERNAL_TOPUP }
enum RequestStatus { COMPLETED, FAILED }

class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(String message) { super(message); }
}

class Wallet {
    private final long walletId;
    private final String userId;
    private BigDecimal balance;
    private final ReentrantLock lock = new ReentrantLock();

    public Wallet(long walletId, String userId, BigDecimal balance) {
        this.walletId = walletId;
        this.userId = userId;
        this.balance = balance;
    }

    public void debit(BigDecimal amount) { this.balance = this.balance.subtract(amount); }
    public void credit(BigDecimal amount) { this.balance = this.balance.add(amount); }

    public long getWalletId() { return walletId; }
    public String getUserId() { return userId; }
    public BigDecimal getBalance() { return balance; }
    public ReentrantLock getLock() { return lock; }
}

class Transaction {
    private final String transactionId;
    private final String transferId;
    private final long walletId;
    private final EntryType type;
    private final BigDecimal amount;
    private final BigDecimal balanceAfter;
    private final TransactionSource source;
    private final Instant timestamp;

    public Transaction(String transactionId, String transferId, long walletId, EntryType type,
                        BigDecimal amount, BigDecimal balanceAfter, TransactionSource source,
                        Instant timestamp) {
        this.transactionId = transactionId;
        this.transferId = transferId;
        this.walletId = walletId;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.source = source;
        this.timestamp = timestamp;
    }

    public String getTransferId() { return transferId; }
    public long getWalletId() { return walletId; }
    public EntryType getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public Instant getTimestamp() { return timestamp; }
}

class TransferRequest {
    private final String idempotencyKey;
    private final long fromWalletId;
    private final long toWalletId;
    private final BigDecimal amount;

    public TransferRequest(String idempotencyKey, long fromWalletId, long toWalletId, BigDecimal amount) {
        this.idempotencyKey = idempotencyKey;
        this.fromWalletId = fromWalletId;
        this.toWalletId = toWalletId;
        this.amount = amount;
    }

    public String getIdempotencyKey() { return idempotencyKey; }
    public long getFromWalletId() { return fromWalletId; }
    public long getToWalletId() { return toWalletId; }
    public BigDecimal getAmount() { return amount; }
}

class TransferResult {
    private final boolean success;
    private final String idempotencyKey;
    private final String transferId;
    private final BigDecimal fromBalance;
    private final BigDecimal toBalance;
    private final String failureReason;

    private TransferResult(boolean success, String idempotencyKey, String transferId,
                            BigDecimal fromBalance, BigDecimal toBalance, String failureReason) {
        this.success = success;
        this.idempotencyKey = idempotencyKey;
        this.transferId = transferId;
        this.fromBalance = fromBalance;
        this.toBalance = toBalance;
        this.failureReason = failureReason;
    }

    public static TransferResult success(String key, String transferId, BigDecimal fromBal, BigDecimal toBal) {
        return new TransferResult(true, key, transferId, fromBal, toBal, null);
    }

    public static TransferResult failed(String key, String reason) {
        return new TransferResult(false, key, null, null, null, reason);
    }

    public boolean isSuccess() { return success; }
    public String getTransferId() { return transferId; }
}

class IdempotencyRecord {
    private final String idempotencyKey;
    private final String transferId;
    private final RequestStatus status;
    private final TransferResult result;
    private final Instant createdAt;

    public IdempotencyRecord(String idempotencyKey, String transferId, RequestStatus status,
                              TransferResult result, Instant createdAt) {
        this.idempotencyKey = idempotencyKey;
        this.transferId = transferId;
        this.status = status;
        this.result = result;
        this.createdAt = createdAt;
    }

    public TransferResult getResult() { return result; }
}

class WalletService {
    final Map<Long, Wallet> wallets;
    final List<Transaction> ledger = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, IdempotencyRecord> idempotencyStore = new ConcurrentHashMap<>();
    private final Object idempotencyLock = new Object();

    public WalletService(Map<Long, Wallet> wallets) {
        this.wallets = wallets;
    }

    public TransferResult transfer(TransferRequest request) {
        synchronized (idempotencyLock) {
            IdempotencyRecord existing = idempotencyStore.get(request.getIdempotencyKey());
            if (existing != null) {
                return existing.getResult();
            }
        }

        if (request.getFromWalletId() == request.getToWalletId()) {
            throw new IllegalArgumentException("Cannot transfer to the same wallet");
        }

        Wallet from = wallets.get(request.getFromWalletId());
        Wallet to = wallets.get(request.getToWalletId());
        if (from == null || to == null) {
            throw new NoSuchElementException("Unknown wallet in transfer request");
        }

        Wallet first = from.getWalletId() < to.getWalletId() ? from : to;
        Wallet second = from.getWalletId() < to.getWalletId() ? to : from;

        first.getLock().lock();
        try {
            second.getLock().lock();
            try {
                synchronized (idempotencyLock) {
                    IdempotencyRecord existing = idempotencyStore.get(request.getIdempotencyKey());
                    if (existing != null) {
                        return existing.getResult();
                    }
                }

                if (from.getBalance().compareTo(request.getAmount()) < 0) {
                    TransferResult failed = TransferResult.failed(
                        request.getIdempotencyKey(), "Insufficient funds");
                    recordIdempotencyResult(request.getIdempotencyKey(), null, failed);
                    return failed;
                }

                String transferId = UUID.randomUUID().toString();
                Instant now = Instant.now();

                from.debit(request.getAmount());
                ledger.add(new Transaction(UUID.randomUUID().toString(), transferId, from.getWalletId(),
                    EntryType.DEBIT, request.getAmount(), from.getBalance(),
                    TransactionSource.P2P_TRANSFER, now));

                to.credit(request.getAmount());
                ledger.add(new Transaction(UUID.randomUUID().toString(), transferId, to.getWalletId(),
                    EntryType.CREDIT, request.getAmount(), to.getBalance(),
                    TransactionSource.P2P_TRANSFER, now));

                TransferResult result = TransferResult.success(
                    request.getIdempotencyKey(), transferId, from.getBalance(), to.getBalance());
                recordIdempotencyResult(request.getIdempotencyKey(), transferId, result);
                return result;
            } finally {
                second.getLock().unlock();
            }
        } finally {
            first.getLock().unlock();
        }
    }

    private void recordIdempotencyResult(String key, String transferId, TransferResult result) {
        synchronized (idempotencyLock) {
            idempotencyStore.put(key, new IdempotencyRecord(
                key, transferId,
                result.isSuccess() ? RequestStatus.COMPLETED : RequestStatus.FAILED,
                result, Instant.now()));
        }
    }

    public List<Transaction> getTransactionHistory(long walletId) {
        List<Transaction> history = new ArrayList<>();
        synchronized (ledger) {
            for (Transaction t : ledger) {
                if (t.getWalletId() == walletId) {
                    history.add(t);
                }
            }
        }
        history.sort(Comparator.comparing(Transaction::getTimestamp));
        return history;
    }
}

// --- Concurrency test harness ---

class WalletConcurrencyTest {

    static WalletService makeService(BigDecimal balanceA, BigDecimal balanceB) {
        Wallet a = new Wallet(1L, "user-a", balanceA);
        Wallet b = new Wallet(2L, "user-b", balanceB);
        Map<Long, Wallet> wallets = new HashMap<>();
        wallets.put(1L, a);
        wallets.put(2L, b);
        return new WalletService(wallets);
    }

    // ─────────────────────────────────────────────────────────────
    // TEST 1: Concurrent bidirectional transfers never deadlock,
    // and total money in the system is conserved.
    // 200 threads alternate 1->2 and 2->1 transfers of $1 each.
    // ─────────────────────────────────────────────────────────────
    static void testConcurrentBidirectionalTransfersNoDeadlockConservesMoney() throws InterruptedException {
        WalletService service = makeService(new BigDecimal("1000.00"), new BigDecimal("1000.00"));
        BigDecimal totalBefore = service.wallets.get(1L).getBalance().add(service.wallets.get(2L).getBalance());

        int threadCount = 200;
        ExecutorService pool = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            futures.add(pool.submit(() -> {
                try {
                    long from = (idx % 2 == 0) ? 1L : 2L;
                    long to = (idx % 2 == 0) ? 2L : 1L;
                    TransferRequest req = new TransferRequest(
                        "bidir-" + idx, from, to, new BigDecimal("1.00"));
                    service.transfer(req);
                } finally {
                    latch.countDown();
                }
            }));
        }

        boolean completed = latch.await(15, TimeUnit.SECONDS);
        pool.shutdownNow();
        if (!completed) {
            throw new AssertionError("DEADLOCK DETECTED: threads did not complete within timeout");
        }
        for (Future<?> f : futures) {
            try {
                f.get();
            } catch (ExecutionException e) {
                throw new AssertionError("Unexpected exception in transfer thread: " + e.getCause());
            }
        }

        BigDecimal totalAfter = service.wallets.get(1L).getBalance().add(service.wallets.get(2L).getBalance());
        if (totalBefore.compareTo(totalAfter) != 0) {
            throw new AssertionError("Money not conserved: before=" + totalBefore + " after=" + totalAfter);
        }

        // 100 transfers each direction of $1 should net to unchanged balances exactly.
        if (service.wallets.get(1L).getBalance().compareTo(new BigDecimal("1000.00")) != 0) {
            throw new AssertionError("Wallet 1 balance drifted: " + service.wallets.get(1L).getBalance());
        }

        System.out.println("PASS: testConcurrentBidirectionalTransfersNoDeadlockConservesMoney");
    }

    // ─────────────────────────────────────────────────────────────
    // TEST 2: Idempotent retry of the same transfer request does not
    // double-debit. 50 threads all submit the SAME idempotency key.
    // Exactly one debit/credit pair must be applied.
    // ─────────────────────────────────────────────────────────────
    static void testIdempotentRetryDoesNotDoubleDebit() throws InterruptedException {
        WalletService service = makeService(new BigDecimal("100.00"), new BigDecimal("50.00"));

        int threadCount = 50;
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<TransferResult> results = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            pool.submit(() -> {
                try {
                    TransferRequest req = new TransferRequest(
                        "same-key-retry", 1L, 2L, new BigDecimal("30.00"));
                    results.add(service.transfer(req));
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(10, TimeUnit.SECONDS);
        pool.shutdownNow();
        if (!completed) {
            throw new AssertionError("Threads did not complete within timeout");
        }

        if (results.size() != threadCount) {
            throw new AssertionError("Expected " + threadCount + " results, got " + results.size());
        }

        Set<String> distinctTransferIds = new HashSet<>();
        for (TransferResult r : results) {
            distinctTransferIds.add(r.getTransferId());
        }
        if (distinctTransferIds.size() != 1) {
            throw new AssertionError("Expected exactly 1 distinct transferId, got " + distinctTransferIds.size());
        }

        BigDecimal expectedFrom = new BigDecimal("70.00");
        BigDecimal expectedTo = new BigDecimal("80.00");
        if (service.wallets.get(1L).getBalance().compareTo(expectedFrom) != 0) {
            throw new AssertionError("Expected wallet 1 balance " + expectedFrom
                + " got " + service.wallets.get(1L).getBalance() + " — double-debit occurred");
        }
        if (service.wallets.get(2L).getBalance().compareTo(expectedTo) != 0) {
            throw new AssertionError("Expected wallet 2 balance " + expectedTo
                + " got " + service.wallets.get(2L).getBalance() + " — double-credit occurred");
        }

        long debitLegsForTransfer = service.getTransactionHistory(1L).stream()
            .filter(t -> t.getType() == EntryType.DEBIT)
            .count();
        if (debitLegsForTransfer != 1) {
            throw new AssertionError("Expected exactly 1 debit ledger row, found " + debitLegsForTransfer);
        }

        System.out.println("PASS: testIdempotentRetryDoesNotDoubleDebit");
    }

    public static void main(String[] args) throws InterruptedException {
        testConcurrentBidirectionalTransfersNoDeadlockConservesMoney();
        testIdempotentRetryDoesNotDoubleDebit();
        System.out.println("All concurrency tests passed.");
    }
}
```

**What each test verifies:**
- `testConcurrentBidirectionalTransfersNoDeadlockConservesMoney`: With lock ordering by `walletId`, 200 threads transferring in both directions between the same two wallets complete within the timeout (no deadlock) and the system-wide sum of balances is unchanged before and after (conservation of money). Removing the ordering logic and locking `from` then `to` unconditionally reproduces a deadlock here.
- `testIdempotentRetryDoesNotDoubleDebit`: 50 concurrent threads submitting the identical idempotency key produce exactly one `transferId` and exactly one debit ledger row — the double-check inside the locked section (before and after acquiring wallet locks) collapses all 50 calls into a single executed transfer, with the other 49 returning the cached result.

---

## Related

**SOLID focus**: [Single Responsibility](../../02-solid-principles/01-single-responsibility.md)

**Concurrency**: [Concurrency Patterns](../../04-concurrency/concurrency-patterns.md)

**Practice next**

- [Design Stock Brokerage](39-design-stock-brokerage.md)
- [Design Order Management](../03-domain-specific/21-design-order-management.md)

Both reuse the atomic-ledger and idempotent-command shape.

**Frameworks**: [LLD Template](../../../07-interview-templates/01-frameworks/02-lld-template.md) · [UML Diagrams](../../01-oop-fundamentals/uml-diagrams.md) · [OOP Four Pillars](../../01-oop-fundamentals/four-pillars.md)
