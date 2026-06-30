> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design an ATM System — an extensive OOP problem that heavily utilizes the State Pattern, Chain of Responsibility, and hardware integration abstraction.
>
> **Key concepts:**
> - Core Entities: `ATM` (context), `State` (interface), `CardReader`, `CashDispenser`, `BankService`, `Transaction`.
> - State Pattern: `IdleState`, `HasCardState`, `SelectOperationState`, `DispensingState`. Handles the exact flow of the user interaction.
> - Chain of Responsibility: Often used for the `CashDispenser`. A request for $170 goes to the $100 handler (dispenses 1, passes $70 down) -> $50 handler (dispenses 1, passes $20 down) -> $20 handler (dispenses 1).
> - Hardware Abstraction: The ATM doesn't "know" how to physically spit out money. It calls `dispenser.dispense(amount)`, which acts as a Facade/Proxy to the hardware layer.
>
> **Key takeaway:** Like the Vending Machine, ATM requires the State pattern. But it adds complexity via integration with an external `BankService` (which must handle the actual balance check and deduction via atomic transactions).

---
module: 06-lld
topic: Problems
status: unread
tags: [06-lld, lld, atm, state-pattern, strategy, template-method]
---
# Design an ATM System

> **Difficulty**: Medium  
> **Asked at**: Amazon, Goldman Sachs  
> **Key Patterns**: State (ATM states), Strategy (cash dispensing), Template Method (transaction flow)

---

## Understanding the Problem

Design an ATM system where a user can insert a card, authenticate with a PIN, check their balance, withdraw cash (selecting denominations), and deposit funds. The system transitions through well-defined states and interacts with a bank backend for account validation and fund management.

---

## Clarifying Questions

**You**: "Does the ATM interact with a real bank or is that simulated?"  
**Interviewer**: "Simulate it with a Bank class. Assume network calls succeed unless you're covering failure cases in deep dive."

**You**: "What denominations does the ATM dispense?"  
**Interviewer**: "100, 50, 20, 10 dollar bills. The ATM has a limited stock of each."

**You**: "How many PIN attempts before the card is locked?"  
**Interviewer**: "Three attempts, then the card is retained."

**You**: "Can a user do multiple transactions in one session — withdraw, then check balance?"  
**Interviewer**: "Yes. After a successful transaction they return to the transaction selection screen."

**You**: "Should we enforce a daily withdrawal limit?"  
**Interviewer**: "Mention it in deep dive. Don't implement it for the core design."

**You**: "What happens if the ATM runs out of cash mid-dispense?"  
**Interviewer**: "Good edge case. Discuss it in deep dive."

---

## Final Requirements

**In scope:**
1. Insert card → authenticate with PIN (up to 3 attempts)
2. Select transaction: withdraw, deposit, check balance
3. Withdrawal with denomination selection (greedy algorithm)
4. Deposit funds
5. Balance check
6. Eject card / end session
7. State machine enforcing valid operation sequences

**Out of scope:**
- Real bank network integration
- Card reading hardware details
- Multi-currency
- Daily limits (mentioned in deep dive)

---

## Core Entities and Relationships

| Entity | Responsibility |
|--------|---------------|
| ATM | Context for the State pattern; holds current state and hardware references |
| ATMState | Abstract base — each state implements the allowed operations |
| IdleState | Waiting for card insertion |
| AuthenticatingState | Card inserted, waiting for PIN |
| SelectingState | Authenticated, waiting for transaction selection |
| DispensingState | Processing a withdrawal or deposit |
| Card | Represents the user's bank card |
| Account | User's bank account (balance, daily limit) |
| CashDispenser | Tracks denomination inventory; executes dispense |
| Bank | Validates PINs, debits/credits accounts |

ATM delegates each user action to its current `ATMState`. The state handles the action and transitions the ATM to the next state. This avoids a tangle of if/else branching in ATM itself.

---

## Class Design

### ATMState (abstract)

```
class ATMState (abstract):
+ insert_card(atm, card) -> None
+ enter_pin(atm, pin) -> None
+ select_transaction(atm, transaction_type) -> None
+ withdraw(atm, amount) -> None
+ deposit(atm, amount) -> None
+ eject_card(atm) -> None
```

### ATM (context)

| Requirement | What ATM must track |
|-------------|---------------------|
| Current state | state: ATMState |
| Hardware | cash_dispenser: CashDispenser |
| Bank connection | bank: Bank |
| Session | current_card: Optional[Card], pin_attempts: int |

```
class ATM:
- state: ATMState
- cash_dispenser: CashDispenser
- bank: Bank
- current_card: Optional[Card]
- pin_attempts: int
+ set_state(state: ATMState) -> None
+ insert_card(card) -> None
+ enter_pin(pin) -> None
+ select_transaction(transaction_type) -> None
+ withdraw(amount) -> None
+ deposit(amount) -> None
+ eject_card() -> None
```

### CashDispenser

| Requirement | What CashDispenser must track |
|-------------|-------------------------------|
| Inventory | denominations: Dict[int, int] (bill → count) |

```
class CashDispenser:
- denominations: Dict[int, int]  # {100: 10, 50: 20, 20: 50, 10: 100}
+ can_dispense(amount) -> bool
+ dispense(amount) -> Dict[int, int]  # bills to give out
+ deposit(amount) -> None
+ total_cash() -> int
```

### Bank / Account / Card

```
class Card:
- card_number: str
- account_id: str

class Account:
- account_id: str
- balance: float
- pin_hash: str

class Bank:
- accounts: Dict[str, Account]
+ validate_pin(card, pin) -> bool
+ get_balance(account_id) -> float
+ debit(account_id, amount) -> bool
+ credit(account_id, amount) -> None
```

---

## Implementation

### Core Method: withdraw (via DispensingState) + denomination algorithm

**Core logic:**
1. Verify amount is positive and a multiple of the smallest bill
2. Check if dispenser can cover the amount (greedy check)
3. Debit the bank account
4. Dispense cash using greedy denomination selection
5. Transition back to SelectingState

**Edge cases:**
- Amount exceeds account balance → decline, stay in SelectingState
- Dispenser cannot make exact change → decline (e.g., $30 when only $50s remain)
- Bank debit succeeds but dispense fails → must rollback (credit account back)

```python
from enum import Enum, auto
from typing import Optional, Dict
from abc import ABC, abstractmethod


class TransactionType(Enum):
    WITHDRAW = auto()
    DEPOSIT = auto()
    BALANCE = auto()


class Card:
    def __init__(self, card_number: str, account_id: str):
        self.card_number = card_number
        self.account_id = account_id


class Account:
    def __init__(self, account_id: str, balance: float, pin: str):
        self.account_id = account_id
        self.balance = balance
        self._pin = pin  # stored as plain text here; use hash in production


class Bank:
    def __init__(self):
        self.accounts: Dict[str, Account] = {}

    def add_account(self, account: Account) -> None:
        self.accounts[account.account_id] = account

    def validate_pin(self, card: Card, pin: str) -> bool:
        account = self.accounts.get(card.account_id)
        return account is not None and account._pin == pin

    def get_balance(self, account_id: str) -> float:
        return self.accounts[account_id].balance

    def debit(self, account_id: str, amount: float) -> bool:
        account = self.accounts[account_id]
        if account.balance < amount:
            return False
        account.balance -= amount
        return True

    def credit(self, account_id: str, amount: float) -> None:
        self.accounts[account_id].balance += amount


class CashDispenser:
    def __init__(self):
        self.denominations: Dict[int, int] = {100: 10, 50: 20, 20: 50, 10: 100}

    def can_dispense(self, amount: int) -> bool:
        return self._calculate_bills(amount) is not None

    def _calculate_bills(self, amount: int) -> Optional[Dict[int, int]]:
        """Greedy: largest denominations first."""
        remaining = amount
        bills_to_give: Dict[int, int] = {}
        for denom in sorted(self.denominations.keys(), reverse=True):
            if remaining <= 0:
                break
            count = min(remaining // denom, self.denominations[denom])
            if count > 0:
                bills_to_give[denom] = count
                remaining -= denom * count
        return bills_to_give if remaining == 0 else None

    def dispense(self, amount: int) -> Dict[int, int]:
        bills = self._calculate_bills(amount)
        if bills is None:
            raise ValueError(f"Cannot dispense exactly ${amount}")
        for denom, count in bills.items():
            self.denominations[denom] -= count
        return bills

    def total_cash(self) -> int:
        return sum(d * c for d, c in self.denominations.items())


class ATMState(ABC):
    @abstractmethod
    def insert_card(self, atm: 'ATM', card: Card) -> None:
        pass

    @abstractmethod
    def enter_pin(self, atm: 'ATM', pin: str) -> None:
        pass

    @abstractmethod
    def withdraw(self, atm: 'ATM', amount: int) -> None:
        pass

    @abstractmethod
    def deposit(self, atm: 'ATM', amount: float) -> None:
        pass

    @abstractmethod
    def eject_card(self, atm: 'ATM') -> None:
        pass

    def _invalid(self, action: str) -> None:
        raise ValueError(f"Cannot {action} in current state")


class IdleState(ATMState):
    def insert_card(self, atm: 'ATM', card: Card) -> None:
        atm.current_card = card
        atm.pin_attempts = 0
        print(f"Card {card.card_number} inserted. Please enter PIN.")
        atm.set_state(AuthenticatingState())

    def enter_pin(self, atm, pin): self._invalid("enter PIN without card")
    def withdraw(self, atm, amount): self._invalid("withdraw without card")
    def deposit(self, atm, amount): self._invalid("deposit without card")
    def eject_card(self, atm): self._invalid("eject card when none inserted")


class AuthenticatingState(ATMState):
    MAX_ATTEMPTS = 3

    def insert_card(self, atm, card): self._invalid("insert another card")

    def enter_pin(self, atm: 'ATM', pin: str) -> None:
        if atm.bank.validate_pin(atm.current_card, pin):
            print("PIN accepted.")
            atm.set_state(SelectingState())
        else:
            atm.pin_attempts += 1
            remaining = self.MAX_ATTEMPTS - atm.pin_attempts
            if remaining <= 0:
                print("Too many incorrect attempts. Card retained.")
                atm.current_card = None
                atm.set_state(IdleState())
            else:
                print(f"Incorrect PIN. {remaining} attempt(s) remaining.")

    def withdraw(self, atm, amount): self._invalid("withdraw before authenticating")
    def deposit(self, atm, amount): self._invalid("deposit before authenticating")

    def eject_card(self, atm: 'ATM') -> None:
        print("Card ejected.")
        atm.current_card = None
        atm.set_state(IdleState())


class SelectingState(ATMState):
    def insert_card(self, atm, card): self._invalid("insert card mid-session")
    def enter_pin(self, atm, pin): self._invalid("re-enter PIN in session")

    def withdraw(self, atm: 'ATM', amount: int) -> None:
        atm.set_state(DispensingState())
        atm.state.withdraw(atm, amount)

    def deposit(self, atm: 'ATM', amount: float) -> None:
        atm.set_state(DispensingState())
        atm.state.deposit(atm, amount)

    def eject_card(self, atm: 'ATM') -> None:
        print("Card ejected. Thank you.")
        atm.current_card = None
        atm.set_state(IdleState())


class DispensingState(ATMState):
    def insert_card(self, atm, card): self._invalid("insert card during transaction")
    def enter_pin(self, atm, pin): self._invalid("enter PIN during transaction")

    def withdraw(self, atm: 'ATM', amount: int) -> None:
        account_id = atm.current_card.account_id
        if not atm.cash_dispenser.can_dispense(amount):
            print(f"ATM cannot dispense ${amount} with available bills.")
            atm.set_state(SelectingState())
            return
        if not atm.bank.debit(account_id, amount):
            print("Insufficient funds.")
            atm.set_state(SelectingState())
            return
        try:
            bills = atm.cash_dispenser.dispense(amount)
            print(f"Dispensing: {bills}")
        except ValueError:
            # Rollback bank debit
            atm.bank.credit(account_id, amount)
            print("Dispense failed. Transaction rolled back.")
        atm.set_state(SelectingState())

    def deposit(self, atm: 'ATM', amount: float) -> None:
        atm.bank.credit(atm.current_card.account_id, amount)
        print(f"Deposited ${amount}. New balance: ${atm.bank.get_balance(atm.current_card.account_id):.2f}")
        atm.set_state(SelectingState())

    def eject_card(self, atm, card=None): self._invalid("eject card during transaction")


class ATM:
    def __init__(self, bank: Bank, cash_dispenser: CashDispenser):
        self.bank = bank
        self.cash_dispenser = cash_dispenser
        self.current_card: Optional[Card] = None
        self.pin_attempts: int = 0
        self.state: ATMState = IdleState()

    def set_state(self, state: ATMState) -> None:
        self.state = state

    def insert_card(self, card: Card) -> None:
        self.state.insert_card(self, card)

    def enter_pin(self, pin: str) -> None:
        self.state.enter_pin(self, pin)

    def withdraw(self, amount: int) -> None:
        self.state.withdraw(self, amount)

    def deposit(self, amount: float) -> None:
        self.state.deposit(self, amount)

    def check_balance(self) -> None:
        if self.current_card is None:
            raise ValueError("No card inserted")
        balance = self.bank.get_balance(self.current_card.account_id)
        print(f"Balance: ${balance:.2f}")

    def eject_card(self) -> None:
        self.state.eject_card(self)
```

---

## Verification

Trace: User inserts card, enters wrong PIN once, then correct PIN, withdraws $150.

1. `atm.insert_card(card)` → IdleState.insert_card → state = AuthenticatingState
2. `atm.enter_pin("9999")` → wrong, pin_attempts=1, 2 remaining
3. `atm.enter_pin("1234")` → correct → state = SelectingState
4. `atm.withdraw(150)` → SelectingState delegates to DispensingState
5. DispensingState: `can_dispense(150)` → greedy: 1×$100 + 1×$50 = $150 ✓
6. `bank.debit(account_id, 150)` → balance decreases by 150
7. `cash_dispenser.dispense(150)` → {100: 1, 50: 1}, inventory updated
8. State returns to SelectingState
9. `atm.eject_card()` → card cleared, state = IdleState

---

## Deep Dive & Extensibility

### 1. "Why use the State pattern here? What breaks with if/else?"

Without State, every method in ATM has a giant if/else:

```python
def withdraw(self, amount):
    if self.status == "IDLE":
        raise ValueError("No card")
    elif self.status == "AUTHENTICATING":
        raise ValueError("Not authenticated")
    elif self.status == "SELECTING":
        # do it
    elif self.status == "DISPENSING":
        raise ValueError("Already dispensing")
```

This violates Open/Closed Principle — adding a new state (e.g., "OutOfCash") requires touching every method. With State, each state class is self-contained. Adding a new state adds a new class only.

### 2. "Walk through the denomination selection algorithm."

Greedy: sort bills largest to smallest, take as many of each as possible without exceeding the remaining amount.

```python
def _calculate_bills(self, amount: int) -> Optional[Dict[int, int]]:
    remaining = amount
    bills: Dict[int, int] = {}
    for denom in sorted(self.denominations.keys(), reverse=True):
        if remaining <= 0:
            break
        count = min(remaining // denom, self.denominations[denom])
        if count > 0:
            bills[denom] = count
            remaining -= denom * count
    return bills if remaining == 0 else None
```

Greedy works here because US denominations are canonical — each is a multiple of smaller ones. For arbitrary denominations, use dynamic programming (coin change problem).

### 3. "Two ATMs serving the same account simultaneously — what breaks?"

Both ATMs call `bank.debit(account_id, 500)` after checking balance. If balance is $600, both checks pass, both debits execute, balance goes to -$400. Classic TOCTOU (time-of-check-time-of-use) race.

Fix: Database-level pessimistic lock or optimistic locking with version numbers.

```sql
-- Pessimistic: SELECT FOR UPDATE
BEGIN;
SELECT balance FROM accounts WHERE account_id = ? FOR UPDATE;
-- check balance >= amount
UPDATE accounts SET balance = balance - ? WHERE account_id = ?;
COMMIT;

-- Optimistic:
UPDATE accounts
SET balance = balance - ?, version = version + 1
WHERE account_id = ? AND version = ? AND balance >= ?;
-- if rows_affected == 0, retry
```

### 4. "What happens if the network fails mid-transaction — after debit but before dispense?"

This is the critical failure mode. The bank has debited the account but cash was not given out.

**Approach**: Write an idempotent transaction log before any mutation.

```python
# Before debit:
log_transaction(tx_id, account_id, amount, status="PENDING")
# After debit:
log_transaction(tx_id, status="DEBITED")
# After dispense:
log_transaction(tx_id, status="COMPLETE")
```

On startup or reconnect, replay pending logs: if status is DEBITED but not COMPLETE, credit the account back (rollback) or retry dispense. This is the saga pattern for distributed transactions.

### 5. "How do you implement daily withdrawal limits?"

Add `daily_limit: float` and `withdrawn_today: float` + `last_reset: date` to `Account`.

```python
def debit(self, account_id: str, amount: float) -> bool:
    account = self.accounts[account_id]
    today = date.today()
    if account.last_reset < today:
        account.withdrawn_today = 0.0
        account.last_reset = today
    if account.withdrawn_today + amount > account.daily_limit:
        return False  # daily limit exceeded
    if account.balance < amount:
        return False
    account.balance -= amount
    account.withdrawn_today += amount
    return True
```

---

## Interviewer Questions by Level

**Junior**: Draw the state diagram for the ATM. What states exist and what events cause transitions?

**Mid-level**: The State pattern has one class per state. How do the states share common behavior — for example, all states except Idle should reject `insert_card`. How do you avoid copy-pasting the error check?

**Senior**: Describe how you would make the ATM software fault-tolerant across a network partition between the ATM and the bank. What guarantees can you still provide?

---

## Common Interview Questions

- **Q: What's the state diagram for the ATM?**  
  A: Idle → (insert_card) → Authenticating → (correct PIN) → Selecting → (choose transaction) → Dispensing → (transaction complete) → Selecting. Any state + eject_card → Idle.

- **Q: Why does denomination selection use greedy? When would it fail?**  
  A: Greedy works for canonical coin systems (USD). It fails for arbitrary denominations — e.g., coins [1, 3, 4], amount=6: greedy picks 4+1+1 (3 coins), optimal is 3+3 (2 coins). Use DP for non-canonical systems.

- **Q: What if the ATM runs out of cash mid-dispense?**  
  A: Check `can_dispense` before debiting the account. If the dispenser check passes but hardware fails mid-dispense, the system should credit the account back (rollback) and alert for maintenance.

- **Q: How do you handle PIN retry lockout without server state?**  
  A: Track `pin_attempts` in the ATM session (in-memory for the card's session). After 3 failures, retain the card and notify the bank to flag the account. The bank can unlock via customer service.

- **Q: Why is the Bank class separate from ATM?**  
  A: Single Responsibility — ATM manages hardware interaction and state transitions; Bank manages financial logic and account data. Also enables mocking Bank in tests.

- **Q: Could you use Template Method instead of State here?**  
  A: Template Method defines a skeleton transaction flow (authenticate → select → execute → end). State is better here because the valid actions vary entirely by state, not just one step. They're complementary — Template Method could define the withdraw flow, State handles which flow is valid.
