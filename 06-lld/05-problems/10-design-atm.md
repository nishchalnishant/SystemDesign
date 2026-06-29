---
module: 06-lld
topic: Problems
status: unread
tags: [06-lld, system-design, problems]
---
# Design ATM

> **Difficulty**: Medium
> **Topics**: State Pattern, Chain of Responsibility, Transaction Safety
> **Key Concepts**: ATM state machine, cash dispensing algorithm, transaction atomicity.

---

## What Breaks Without This Design?

```python
class ATM:
    def __init__(self):
        self._total_cash = 0
        self._card_inserted = False
        self._pin_verified = False
        self._current_account = ""

    def insert_card(self, account):
        self._card_inserted = True
        self._current_account = account

    def verify_pin(self, pin):
        self._pin_verified = self._check_pin(pin)
        return self._pin_verified

    def withdraw(self, amount):
        if not self._card_inserted or not self._pin_verified:
            return False
        if self._total_cash < amount:
            return False
        self._total_cash -= amount
        self._debit_account(self._current_account, amount)
        return True
```

**Concrete failures**:
1. **Invalid state transitions**: `withdraw()` can be called before `insertCard()` if `cardInserted` is somehow set externally — no enforcement of the card → pin → transaction sequence.
2. **Cash denomination ignored**: `totalCash -= amount` doesn't model individual notes. ATM has ₹500 and ₹100 notes; withdrawing ₹300 from a ₹500-only ATM returns `true` but can't actually dispense.
3. **Transaction atomicity**: `totalCash -= amount` succeeds but `debitAccount()` throws — cash is dispensed but account is not debited.
4. **Concurrent withdrawals**: Two threads call `withdraw()` simultaneously with `totalCash = 1000`. Both pass the `totalCash < amount` check, both decrement — ATM dispenses ₹2000 from ₹1000 stock.

---

## Derive the Class Structure

**Force 1 — ATM has strict state transitions**: Extract `ATMState` interface. Concrete states: `IdleState`, `CardInsertedState`, `PinEnteredState`, `TransactionState`. Each state allows only valid operations.

**Force 2 — Cash dispensing requires denomination awareness**: Extract `CashDispenser` with a `Map<Denomination, Integer>` (count per note type). Chain of Responsibility dispatches each denomination handler.

**Force 3 — Transaction must be atomic**: Extract `Transaction` that wraps cash dispensing + account debit. Rollback cash on account debit failure.

**Force 4 — Multiple transaction types**: `Withdraw`, `BalanceInquiry`, `PinChange` are separate command objects.

```
God class → ATMState (per-state behavior)
          → ATMContext (holds current state, delegates)
          → CashDispenser (denomination chain)
          → BankService (account operations)
          → Transaction (atomic unit)
          → Card + Account (entities)
```

---

## Phase 1: Requirements

**Actors**: Customer, Bank (backend service), ATM hardware.

**Must-have**:
- Insert card and verify PIN (max 3 attempts, then card captured)
- Withdraw cash (check balance + check ATM stock + dispense correct denominations)
- Check balance
- Change PIN
- Return card on session end

**Constraints**:
- Each operation only valid in the correct state
- Cash dispensing is atomic with account debit
- Max 3 wrong PIN attempts before card lock

---

## Phase 2: State Machine

```
IDLE ──insertCard──▶ CARD_INSERTED ──verifyPin(success)──▶ PIN_VERIFIED
                                   ──verifyPin(3× fail)──▶ CARD_CAPTURED

PIN_VERIFIED ──selectTransaction──▶ TRANSACTION
             ──cancel──▶ IDLE

TRANSACTION ──success/failure──▶ PIN_VERIFIED (next transaction)
            ──ejectCard──▶ IDLE
```

---

## Phase 3: Class Diagram

```
ATMState <<interface>>
  + insertCard(ATMContext, Card): void
  + enterPin(ATMContext, int): void
  + selectTransaction(ATMContext, TransactionType): void
  + cancel(ATMContext): void

IdleState implements ATMState
CardInsertedState implements ATMState
PinVerifiedState implements ATMState

ATMContext
  - currentState: ATMState
  - currentCard: Card
  - cashDispenser: CashDispenser
  - bankService: BankService
  + setState(ATMState): void
  + getCashDispenser(): CashDispenser

CashDispenser
  - denominations: Map<Denomination, Integer>  // e.g. {500→20, 100→50, 50→100}
  + canDispense(amount): boolean
  + dispense(amount): Map<Denomination, Integer>  // notes to hand out

BankService <<interface>>
  + verifyPin(cardNumber, pin): boolean
  + getBalance(accountId): double
  + debit(accountId, amount): TransactionResult

Transaction
  - type: TransactionType
  - amount: double
  - status: TransactionStatus
  + execute(ATMContext): void
  + rollback(ATMContext): void
```

---

## Phase 4: Design Patterns

| Pattern | Where | Why |
|---------|-------|-----|
| **State** | `ATMState` hierarchy | Each state enforces which operations are valid; no flag checks |
| **Chain of Responsibility** | `CashDispenser` denomination handlers | Each handler dispenses as many notes of one denomination as possible, passes remainder to next |
| **Command** | `Transaction` subclasses | Encapsulates request + rollback logic |
| **Facade** | `ATMContext` | Single interface hiding state transitions and hardware interactions |

---

## Phase 5: Key Implementation

### State Pattern

```python
from abc import ABC, abstractmethod

class ATMState(ABC):
    @abstractmethod
    def insert_card(self, ctx, card): ...
    @abstractmethod
    def enter_pin(self, ctx, pin): ...
    @abstractmethod
    def select_transaction(self, ctx, tx_type, amount): ...
    @abstractmethod
    def cancel(self, ctx): ...

class IdleState(ATMState):
    def insert_card(self, ctx, card):
        ctx.current_card = card
        ctx.set_state(CardInsertedState())
        print("Card inserted. Please enter PIN.")

    def enter_pin(self, ctx, pin):
        raise InvalidStateException("Insert card first.")

    def select_transaction(self, ctx, tx_type, amount):
        raise InvalidStateException("Insert card first.")

    def cancel(self, ctx):
        raise InvalidStateException("Nothing to cancel.")

class PinVerifiedState(ATMState):
    def select_transaction(self, ctx, tx_type, amount):
        tx = TransactionFactory.create(tx_type, amount)
        tx.execute(ctx)  # atomic: dispense cash then debit account

    def insert_card(self, ctx, card):
        raise InvalidStateException("Card already inserted.")

    def enter_pin(self, ctx, pin):
        raise InvalidStateException("PIN already verified.")

    def cancel(self, ctx):
        ctx.set_state(IdleState())
```

### Cash Dispensing (Greedy + Chain of Responsibility)

```python
def dispense(self, amount):
    result = {}  # dict[Denomination, int]
    remaining = int(amount)

    # denominations sorted descending: 2000, 500, 200, 100, 50
    for denom in self._sorted_denominations():
        available = self._denominations[denom]
        needed = remaining // denom.value
        to_dispense = min(needed, available)
        if to_dispense > 0:
            result[denom] = to_dispense
            remaining -= to_dispense * denom.value
            self._denominations[denom] = available - to_dispense

    if remaining != 0:
        raise InsufficientDenominationsException()
    return result
```

### Atomic Withdraw Transaction

```python
class WithdrawTransaction(Transaction):
    def execute(self, ctx):
        if not ctx.cash_dispenser.can_dispense(self.amount):
            raise InsufficientCashException()

        notes = ctx.cash_dispenser.dispense(self.amount)
        try:
            result = ctx.bank_service.debit(ctx.current_account, self.amount)
            if not result.is_success():
                self._rollback(ctx, notes)  # return notes to dispenser
                raise BankServiceException(result.message)
        except Exception:
            self._rollback(ctx, notes)
            raise

    def _rollback(self, ctx, notes):
        ctx.cash_dispenser.return_notes(notes)
```

---

## Interview Tips

- **State pattern is the core**: Draw the state diagram first — interviewers want to see you think in terms of valid transitions, not boolean flags.
- **Denomination edge case**: What if the ATM has only ₹500 notes and user requests ₹300? `canDispense()` catches this before touching cash.
- **PIN attempt locking**: Track attempts in `CardInsertedState`; on 3rd failure, transition to a `CardCapturedState` and notify bank.
- **Concurrency**: ATM is single-threaded by design (one user at a time) — no need for locks at the ATM level, but `BankService.debit()` must be idempotent for network retry safety.

---

## Interviewer Follow-Up Questions

- "What states does an ATM have?" → `IDLE` (waiting for card), `CARD_INSERTED` (card in, awaiting PIN), `PIN_VERIFIED` (authenticated, awaiting selection), `TRANSACTION` (processing), `DISPENSING` (dispensing cash), `OUT_OF_SERVICE`. State pattern: each state handles events (insertCard, enterPIN, selectTransaction, cancel) differently. Invalid operations in a given state (e.g., `selectTransaction` while in `IDLE`) are rejected without side effects.
- "How do you handle the cash dispensing — what if the ATM doesn't have the exact denominations?" → Denomination problem: available bills `[100, 50, 20]`, withdrawal amount = $180. Greedy approach: take as many of the largest denomination as possible without exceeding the amount. 1×100, 1×50, 1×20 = $170 — doesn't work. Backtrack: 0×100, 3×50, 1×20 = $170 — still fails. Correct: 1×100, 1×50, 3×20 = $210 — too much. Actually: 1×100, 0×50, 4×20 = $180 — works! Greedy may fail for some combinations; DP (coin change) is optimal but expensive. Most ATMs use restricted denominations (only $20s) to avoid this problem.
- "The user's bank is not the ATM's bank. How does the withdrawal flow work?" → Interbank flow: (1) ATM reads card (bank ID from magnetic stripe / chip). (2) ATM sends authorization request to card network (Visa/Mastercard). (3) Network routes request to issuing bank. (4) Issuing bank checks balance, reserves funds, returns auth code. (5) ATM dispenses cash. (6) Settlement happens later (T+1). The ATM never talks directly to the user's bank — always through the card network. This is why international ATM fees exist (interchange fees).
- "The ATM dispenses cash and then the network connection drops before debiting the account. What happens?" → The authorization already reserved (blocked) the funds at the bank level before dispensing. The ATM dispenses only after receiving the auth code. If the settlement message is lost: the issuing bank has the reserved funds; the settlement is retried (ATM processors retry settlement for 24-48 hours). The authorization hold ensures the user can't spend the same money twice while waiting for settlement. This is the authorize-then-settle pattern.
