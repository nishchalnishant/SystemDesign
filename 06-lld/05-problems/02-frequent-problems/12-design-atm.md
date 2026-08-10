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

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

enum TransactionType {
    WITHDRAW,
    DEPOSIT,
    BALANCE
}

class Card {
    private final String cardNumber;
    private final String accountId;

    public Card(String cardNumber, String accountId) {
        this.cardNumber = cardNumber;
        this.accountId = accountId;
    }

    public String getCardNumber() {
        return cardNumber;
    }

    public String getAccountId() {
        return accountId;
    }
}

class Account {
    private final String accountId;
    private double balance;
    private final String pin; // stored as plain text here; use hash in production

    public Account(String accountId, double balance, String pin) {
        this.accountId = accountId;
        this.balance = balance;
        this.pin = pin;
    }

    public String getAccountId() {
        return accountId;
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }

    public String getPin() {
        return pin;
    }
}

class Bank {
    private final Map<String, Account> accounts = new HashMap<>();

    public void addAccount(Account account) {
        accounts.put(account.getAccountId(), account);
    }

    public boolean validatePin(Card card, String pin) {
        Account account = accounts.get(card.getAccountId());
        return account != null && account.getPin().equals(pin);
    }

    public double getBalance(String accountId) {
        return accounts.get(accountId).getBalance();
    }

    public synchronized boolean debit(String accountId, double amount) {
        Account account = accounts.get(accountId);
        if (account.getBalance() < amount) {
            return false;
        }
        account.setBalance(account.getBalance() - amount);
        return true;
    }

    public synchronized void credit(String accountId, double amount) {
        Account account = accounts.get(accountId);
        account.setBalance(account.getBalance() + amount);
    }
}

class CashDispenser {
    private final Map<Integer, Integer> denominations = new HashMap<>();

    public CashDispenser() {
        denominations.put(100, 10);
        denominations.put(50, 20);
        denominations.put(20, 50);
        denominations.put(10, 100);
    }

    public boolean canDispense(int amount) {
        return calculateBills(amount) != null;
    }

    // Greedy: largest denominations first.
    private Map<Integer, Integer> calculateBills(int amount) {
        int remaining = amount;
        Map<Integer, Integer> billsToGive = new TreeMap<>(Collections.reverseOrder());
        List<Integer> denomsDesc = new ArrayList<>(denominations.keySet());
        denomsDesc.sort(Collections.reverseOrder());
        for (int denom : denomsDesc) {
            if (remaining <= 0) {
                break;
            }
            int count = Math.min(remaining / denom, denominations.get(denom));
            if (count > 0) {
                billsToGive.put(denom, count);
                remaining -= denom * count;
            }
        }
        return remaining == 0 ? billsToGive : null;
    }

    public Map<Integer, Integer> dispense(int amount) {
        Map<Integer, Integer> bills = calculateBills(amount);
        if (bills == null) {
            throw new IllegalArgumentException(String.format("Cannot dispense exactly $%d", amount));
        }
        for (Map.Entry<Integer, Integer> entry : bills.entrySet()) {
            denominations.put(entry.getKey(), denominations.get(entry.getKey()) - entry.getValue());
        }
        return bills;
    }

    public int totalCash() {
        int total = 0;
        for (Map.Entry<Integer, Integer> entry : denominations.entrySet()) {
            total += entry.getKey() * entry.getValue();
        }
        return total;
    }
}

abstract class ATMState {
    public abstract void insertCard(ATM atm, Card card);
    public abstract void enterPin(ATM atm, String pin);
    public abstract void withdraw(ATM atm, int amount);
    public abstract void deposit(ATM atm, double amount);
    public abstract void ejectCard(ATM atm);

    protected void invalid(String action) {
        throw new IllegalStateException(String.format("Cannot %s in current state", action));
    }
}

class IdleState extends ATMState {
    @Override
    public void insertCard(ATM atm, Card card) {
        atm.setCurrentCard(card);
        atm.setPinAttempts(0);
        System.out.println(String.format("Card %s inserted. Please enter PIN.", card.getCardNumber()));
        atm.setState(new AuthenticatingState());
    }

    @Override
    public void enterPin(ATM atm, String pin) { invalid("enter PIN without card"); }
    @Override
    public void withdraw(ATM atm, int amount) { invalid("withdraw without card"); }
    @Override
    public void deposit(ATM atm, double amount) { invalid("deposit without card"); }
    @Override
    public void ejectCard(ATM atm) { invalid("eject card when none inserted"); }
}

class AuthenticatingState extends ATMState {
    private static final int MAX_ATTEMPTS = 3;

    @Override
    public void insertCard(ATM atm, Card card) { invalid("insert another card"); }

    @Override
    public void enterPin(ATM atm, String pin) {
        if (atm.getBank().validatePin(atm.getCurrentCard(), pin)) {
            System.out.println("PIN accepted.");
            atm.setState(new SelectingState());
        } else {
            atm.setPinAttempts(atm.getPinAttempts() + 1);
            int remaining = MAX_ATTEMPTS - atm.getPinAttempts();
            if (remaining <= 0) {
                System.out.println("Too many incorrect attempts. Card retained.");
                atm.setCurrentCard(null);
                atm.setState(new IdleState());
            } else {
                System.out.println(String.format("Incorrect PIN. %d attempt(s) remaining.", remaining));
            }
        }
    }

    @Override
    public void withdraw(ATM atm, int amount) { invalid("withdraw before authenticating"); }
    @Override
    public void deposit(ATM atm, double amount) { invalid("deposit before authenticating"); }

    @Override
    public void ejectCard(ATM atm) {
        System.out.println("Card ejected.");
        atm.setCurrentCard(null);
        atm.setState(new IdleState());
    }
}

class SelectingState extends ATMState {
    @Override
    public void insertCard(ATM atm, Card card) { invalid("insert card mid-session"); }
    @Override
    public void enterPin(ATM atm, String pin) { invalid("re-enter PIN in session"); }

    @Override
    public void withdraw(ATM atm, int amount) {
        atm.setState(new DispensingState());
        atm.getState().withdraw(atm, amount);
    }

    @Override
    public void deposit(ATM atm, double amount) {
        atm.setState(new DispensingState());
        atm.getState().deposit(atm, amount);
    }

    @Override
    public void ejectCard(ATM atm) {
        System.out.println("Card ejected. Thank you.");
        atm.setCurrentCard(null);
        atm.setState(new IdleState());
    }
}

class DispensingState extends ATMState {
    @Override
    public void insertCard(ATM atm, Card card) { invalid("insert card during transaction"); }
    @Override
    public void enterPin(ATM atm, String pin) { invalid("enter PIN during transaction"); }

    @Override
    public void withdraw(ATM atm, int amount) {
        String accountId = atm.getCurrentCard().getAccountId();
        if (!atm.getCashDispenser().canDispense(amount)) {
            System.out.println(String.format("ATM cannot dispense $%d with available bills.", amount));
            atm.setState(new SelectingState());
            return;
        }
        if (!atm.getBank().debit(accountId, amount)) {
            System.out.println("Insufficient funds.");
            atm.setState(new SelectingState());
            return;
        }
        try {
            Map<Integer, Integer> bills = atm.getCashDispenser().dispense(amount);
            System.out.println("Dispensing: " + bills);
        } catch (IllegalArgumentException e) {
            // Rollback bank debit
            atm.getBank().credit(accountId, amount);
            System.out.println("Dispense failed. Transaction rolled back.");
        }
        atm.setState(new SelectingState());
    }

    @Override
    public void deposit(ATM atm, double amount) {
        atm.getBank().credit(atm.getCurrentCard().getAccountId(), amount);
        System.out.println(String.format("Deposited $%.2f. New balance: $%.2f",
                amount, atm.getBank().getBalance(atm.getCurrentCard().getAccountId())));
        atm.setState(new SelectingState());
    }

    @Override
    public void ejectCard(ATM atm) { invalid("eject card during transaction"); }
}

class ATM {
    private final Bank bank;
    private final CashDispenser cashDispenser;
    private Card currentCard;
    private int pinAttempts;
    private ATMState state;

    public ATM(Bank bank, CashDispenser cashDispenser) {
        this.bank = bank;
        this.cashDispenser = cashDispenser;
        this.currentCard = null;
        this.pinAttempts = 0;
        this.state = new IdleState();
    }

    public void setState(ATMState state) {
        this.state = state;
    }

    public ATMState getState() {
        return state;
    }

    public Bank getBank() {
        return bank;
    }

    public CashDispenser getCashDispenser() {
        return cashDispenser;
    }

    public Card getCurrentCard() {
        return currentCard;
    }

    public void setCurrentCard(Card card) {
        this.currentCard = card;
    }

    public int getPinAttempts() {
        return pinAttempts;
    }

    public void setPinAttempts(int pinAttempts) {
        this.pinAttempts = pinAttempts;
    }

    public void insertCard(Card card) {
        state.insertCard(this, card);
    }

    public void enterPin(String pin) {
        state.enterPin(this, pin);
    }

    public void withdraw(int amount) {
        state.withdraw(this, amount);
    }

    public void deposit(double amount) {
        state.deposit(this, amount);
    }

    public void checkBalance() {
        if (currentCard == null) {
            throw new IllegalStateException("No card inserted");
        }
        double balance = bank.getBalance(currentCard.getAccountId());
        System.out.println(String.format("Balance: $%.2f", balance));
    }

    public void ejectCard() {
        state.ejectCard(this);
    }
}
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

```java
void withdraw(int amount) {
    if (status.equals("IDLE")) {
        throw new IllegalStateException("No card");
    } else if (status.equals("AUTHENTICATING")) {
        throw new IllegalStateException("Not authenticated");
    } else if (status.equals("SELECTING")) {
        // do it
    } else if (status.equals("DISPENSING")) {
        throw new IllegalStateException("Already dispensing");
    }
}
```

This violates Open/Closed Principle — adding a new state (e.g., "OutOfCash") requires touching every method. With State, each state class is self-contained. Adding a new state adds a new class only.

### 2. "Walk through the denomination selection algorithm."

Greedy: sort bills largest to smallest, take as many of each as possible without exceeding the remaining amount.

```java
private Map<Integer, Integer> calculateBills(int amount) {
    int remaining = amount;
    Map<Integer, Integer> bills = new TreeMap<>(Collections.reverseOrder());
    List<Integer> denomsDesc = new ArrayList<>(denominations.keySet());
    denomsDesc.sort(Collections.reverseOrder());
    for (int denom : denomsDesc) {
        if (remaining <= 0) {
            break;
        }
        int count = Math.min(remaining / denom, denominations.get(denom));
        if (count > 0) {
            bills.put(denom, count);
            remaining -= denom * count;
        }
    }
    return remaining == 0 ? bills : null;
}
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

```java
// Before debit:
logTransaction(txId, accountId, amount, "PENDING");
// After debit:
logTransaction(txId, "DEBITED");
// After dispense:
logTransaction(txId, "COMPLETE");
```

On startup or reconnect, replay pending logs: if status is DEBITED but not COMPLETE, credit the account back (rollback) or retry dispense. This is the saga pattern for distributed transactions.

### 5. "How do you implement daily withdrawal limits?"

Add `daily_limit: float` and `withdrawn_today: float` + `last_reset: date` to `Account`.

```java
public boolean debit(String accountId, double amount) {
    Account account = accounts.get(accountId);
    LocalDate today = LocalDate.now();
    if (account.getLastReset().isBefore(today)) {
        account.setWithdrawnToday(0.0);
        account.setLastReset(today);
    }
    if (account.getWithdrawnToday() + amount > account.getDailyLimit()) {
        return false; // daily limit exceeded
    }
    if (account.getBalance() < amount) {
        return false;
    }
    account.setBalance(account.getBalance() - amount);
    account.setWithdrawnToday(account.getWithdrawnToday() + amount);
    return true;
}
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

---

## Related

**Patterns applied here**

- [Chain of Responsibility Pattern](../../03-design-patterns/03-behavioral/chain-of-responsibility.md)
- [State Pattern](../../03-design-patterns/03-behavioral/state-pattern.md)
- [Strategy Pattern](../../03-design-patterns/03-behavioral/strategy-pattern.md)

**SOLID focus**: [Single Responsibility](../../02-solid-principles/01-single-responsibility.md) · [Open/Closed](../../02-solid-principles/02-open-closed.md)

**Practice next**

- [Design Vending Machine](../01-core-problems/04-design-vending-machine.md)
- [Design Logger Library](../03-domain-specific/19-design-logger-library.md)

Cash dispensing and log routing both chain handlers.

**Frameworks**: [LLD Template](../../../07-interview-templates/01-frameworks/02-lld-template.md) · [UML Diagrams](../../uml-diagrams.md) · [OOP Four Pillars](../../01-oop-fundamentals/four-pillars.md)
