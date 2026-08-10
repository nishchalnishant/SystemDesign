> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design Splitwise — a popular problem that tests OOP modeling of complex entities (Expenses, Splits) and algorithmic graph simplification.
>
> **Key concepts:**
> - Core Entities: `User`, `Group`, `Expense`, `Split` (interface).
> - Split Types (Strategy Pattern): `EqualSplit`, `ExactSplit`, `PercentageSplit`.
> - Expense Management: An `Expense` has an amount, a paid-by user, and a list of `Split`s detailing who owes what.
> - The Algorithm: Debt simplification. To minimize transactions, model users as nodes and debts as directed edges. 
>   1. Calculate the net balance for each user (sum of incoming - sum of outgoing).
>   2. Separate users into "debtors" (negative balance) and "creditors" (positive balance).
>   3. Greedily match the largest debtor with the largest creditor to settle debts efficiently.
>
> **Key takeaway:** The OOP part requires the Strategy pattern for different split types. The algorithmic part requires the "Minimize Cash Flow" greedy graph algorithm. Both are equally important for passing this interview.

---
module: 06-lld
topic: Problems
status: unread
tags: [06-lld, lld, splitwise, expense-splitting, graph, strategy]
---
# Design Splitwise

> **Difficulty**: Medium-Hard  
> **Asked at**: Amazon, Uber, Flipkart  
> **Key Patterns**: Strategy (split type), Graph (debt simplification), net balance tracking

---

## Understanding the Problem

Design an expense splitting application where users can add shared expenses, track who owes whom, and simplify a group's debts into the minimum number of transactions needed to settle all balances.

---

## Clarifying Questions

**You**: "What split types should we support?"  
**Interviewer**: "Equal split, exact amount per person, and percentage-based."

**You**: "Should we handle groups, or just direct splits between any two users?"  
**Interviewer**: "Both — users can split within a named group, or directly with any user."

**You**: "When you say 'simplify debts', what does that mean exactly?"  
**Interviewer**: "Instead of A owes B $10 and B owes C $10, simplify to A owes C $10 directly."

**You**: "Is multi-currency support needed?"  
**Interviewer**: "Single currency for now. Multi-currency is a follow-up."

**You**: "Do we need to persist transaction history, or just track current balances?"  
**Interviewer**: "Track both — the expense history and the current net balance between each pair."

**You**: "Should splitting always be exact, or is rounding allowed?"  
**Interviewer**: "For equal splits, rounding to cents is fine. One person can absorb the rounding difference."

---

## Final Requirements

**In scope:**
1. Add expense: payer, total amount, split type, participants
2. Track net balance between every pair of users
3. `get_balances(user)` — show what user owes others and what others owe user
4. `simplify_debts(group)` — minimize transactions to settle all balances
5. Three split types: equal, exact, percentage
6. Expense history per user and per group

**Out of scope:**
- Multi-currency
- Recurring expenses (follow-up)
- Payment processing / settlement confirmation
- Receipt scanning

---

## Core Entities and Relationships

| Entity | Responsibility |
|--------|---------------|
| User | Has name and ID; participates in expenses |
| Group | Named collection of users; tracks shared expenses |
| Expense | Records payer, amount, split, and participants |
| Split | Abstract strategy — computes each participant's share |
| EqualSplit | Divides evenly with rounding correction |
| ExactSplit | Takes explicit per-user amounts |
| PercentSplit | Takes per-user percentages summing to 100% |
| BalanceSheet | Stores net balances between all user pairs |
| DebtSimplifier | Minimizes transaction count using min-cash-flow algorithm |

BalanceSheet is the central state. Every `add_expense` call updates net balances. `simplify_debts` reads balances and computes the minimum transactions without modifying the balance sheet.

---

## Class Design

### User

```java
class User {
    private String userId;
    private String name;
    private String email;
}
```

### Expense

```java
class Expense {
    private String expenseId;
    private String description;
    private User payer;
    private double totalAmount;
    private Split split;
    private List<User> participants;
    private LocalDateTime timestamp;
    private Group group;  // nullable

    public Map<User, Double> getShares() { ... }  // who owes how much
}
```

### Split (abstract)

| Requirement | What Split subclass must provide |
|-------------|----------------------------------|
| Compute each participant's share | calculate(participants, total_amount) -> dict[User, float] |
| Validate input | validate(participants, inputs) — raises if invalid |

```java
interface Split {
    Map<User, Double> calculate(List<User> participants, double amount);
    void validate(List<User> participants);
}

class EqualSplit implements Split {
    // divides evenly; last person absorbs rounding
}

class ExactSplit implements Split {
    private Map<User, Double> amounts;  // must sum to totalAmount
}

class PercentSplit implements Split {
    private Map<User, Double> percentages;  // must sum to 100.0
}
```

### BalanceSheet

```java
class BalanceSheet {
    // balances[userAId + "#" + userBId] = amount   (sorted pair key)
    // positive means userA owes userB
    // always store with sorted key to avoid duplicate entries
    private Map<String, Double> balances;

    public void update(User debtor, User creditor, double amount) { ... }
    public double getBalance(User userA, User userB) { ... }
    public Map<User, Double> getAllBalances(User user) { ... }
}
```

### Group

```java
class Group {
    private String groupId;
    private String name;
    private List<User> members;
    private List<Expense> expenses;
    private BalanceSheet balanceSheet;

    public void addMember(User user) { ... }
    public void addExpense(Expense expense) { ... }
    public Map<User, Double> getBalances(User user) { ... }
}
```

### DebtSimplifier

```java
class DebtSimplifier {
    public List<Transaction> simplify(BalanceSheet balanceSheet, List<User> users) { ... }
}

class Transaction {
    private User fromUser;
    private User toUser;
    private double amount;
}
```

---

## Implementation

### Core Method: add_expense

**Core logic:**
1. Call `split.validate(participants)` — raise if percentages don't sum to 100, etc.
2. Call `split.calculate(participants, amount)` → shares dict: participant → amount owed
3. For each participant who is not the payer, update BalanceSheet: participant owes payer `shares[participant]`
4. Add expense to history

**Edge cases:**
- Payer is also a participant — their share is already "paid", so no self-debt entry
- Rounding in EqualSplit — last participant absorbs remainder

```java
public Expense addExpense(User payer, double amount, Split split,
                           List<User> participants, String description) {
    split.validate(participants);
    Map<User, Double> shares = split.calculate(participants, amount);

    Expense expense = new Expense(
        UUID.randomUUID().toString(),
        description,
        payer,
        amount,
        split,
        participants,
        LocalDateTime.now()
    );

    for (Map.Entry<User, Double> entry : shares.entrySet()) {
        User participant = entry.getKey();
        double share = entry.getValue();
        if (!participant.equals(payer) && share > 0) {
            balanceSheet.update(participant, payer, share);
        }
    }

    expenses.add(expense);
    return expense;
}
```

### Core Method: EqualSplit.calculate

```java
@Override
public Map<User, Double> calculate(List<User> participants, double amount) {
    int n = participants.size();
    long totalCents = Math.round(amount * 100);
    long baseShare = totalCents / n;  // in cents, integer division
    long remainder = totalCents - baseShare * n;

    Map<User, Double> shares = new LinkedHashMap<>();
    for (int i = 0; i < n; i++) {
        User user = participants.get(i);
        long shareCents = baseShare + (i == n - 1 ? remainder : 0);
        shares.put(user, shareCents / 100.0);
    }
    return shares;
}
```

### Core Method: BalanceSheet.update

The key insight: store only net balances, not every individual transaction.

```java
public void update(User debtor, User creditor, double amount) {
    String idA = debtor.getUserId();
    String idB = creditor.getUserId();
    // sorted key: keyA <= keyB lexicographically
    String keyA = idA.compareTo(idB) <= 0 ? idA : idB;
    String keyB = idA.compareTo(idB) <= 0 ? idB : idA;
    String key = keyA + "#" + keyB;

    // positive value: keyA owes keyB
    double current = balances.getOrDefault(key, 0.0);
    double updated = keyA.equals(idA) ? current + amount : current - amount;
    balances.put(key, updated);

    // Remove zero balances
    if (Math.abs(balances.get(key)) < 0.01) {
        balances.remove(key);
    }
}
```

### Core Method: DebtSimplifier.simplify (min cash flow)

**Algorithm**: Compute net balance per user (total owed to them minus total they owe). Separate into creditors (positive net) and debtors (negative net). Greedily match the highest debtor with the highest creditor.

**Core logic:**
1. Compute net balance for each user: `net[user] = sum(amounts others owe them) - sum(amounts they owe others)`
2. Separate into max-heap of creditors (net > 0) and min-heap of debtors (net < 0)
3. While both heaps are non-empty: match top debtor with top creditor for `min(|debtor_net|, creditor_net)`. Create one transaction. Adjust remaining balances.

**Edge cases:**
- User with net=0 — skip them entirely
- Remaining net < 0.01 after floating point — treat as settled

```java
public List<Transaction> simplify(BalanceSheet balanceSheet, List<User> users) {
    // Compute net balance per user
    Map<User, Double> net = new LinkedHashMap<>();
    for (User user : users) {
        net.put(user, 0.0);
    }
    for (Map.Entry<String, Double> entry : balanceSheet.getBalances().entrySet()) {
        String[] parts = entry.getKey().split("#");
        String uidA = parts[0];
        String uidB = parts[1];
        double amount = entry.getValue();
        User userA = findUser(users, uidA);
        User userB = findUser(users, uidB);
        if (amount > 0) {  // uidA owes uidB
            net.put(userA, net.get(userA) - amount);
            net.put(userB, net.get(userB) + amount);
        } else {
            net.put(userA, net.get(userA) + Math.abs(amount));
            net.put(userB, net.get(userB) - Math.abs(amount));
        }
    }

    // Separate into creditors and debtors (both max-heaps by absolute balance)
    PriorityQueue<Map.Entry<User, Double>> creditors =
        new PriorityQueue<>((a, b) -> Double.compare(b.getValue(), a.getValue()));
    PriorityQueue<Map.Entry<User, Double>> debtors =
        new PriorityQueue<>((a, b) -> Double.compare(Math.abs(b.getValue()), Math.abs(a.getValue())));

    for (Map.Entry<User, Double> entry : net.entrySet()) {
        double balance = entry.getValue();
        if (balance > 0.01) {
            creditors.add(new AbstractMap.SimpleEntry<>(entry.getKey(), balance));
        } else if (balance < -0.01) {
            debtors.add(new AbstractMap.SimpleEntry<>(entry.getKey(), balance));
        }
    }

    List<Transaction> transactions = new ArrayList<>();
    while (!creditors.isEmpty() && !debtors.isEmpty()) {
        Map.Entry<User, Double> creditorEntry = creditors.poll();
        User creditor = creditorEntry.getKey();
        double credAmount = creditorEntry.getValue();

        Map.Entry<User, Double> debtorEntry = debtors.poll();
        User debtor = debtorEntry.getKey();
        double debtAmount = Math.abs(debtorEntry.getValue());

        double settled = Math.min(credAmount, debtAmount);
        transactions.add(new Transaction(debtor, creditor, settled));

        double remainingCred = credAmount - settled;
        double remainingDebt = debtAmount - settled;

        if (remainingCred > 0.01) {
            creditors.add(new AbstractMap.SimpleEntry<>(creditor, remainingCred));
        }
        if (remainingDebt > 0.01) {
            debtors.add(new AbstractMap.SimpleEntry<>(debtor, -remainingDebt));
        }
    }

    return transactions;
}
```

---

## Verification

**Scenario**: A, B, C share a $30 dinner. A pays. Equal split.

1. `add_expense(payer=A, amount=30, split=EqualSplit, participants=[A,B,C])`
2. `EqualSplit.calculate([A,B,C], 30)` → `{A: 10.0, B: 10.0, C: 10.0}`
3. B is not payer → `balance_sheet.update(debtor=B, creditor=A, amount=10)`
4. C is not payer → `balance_sheet.update(debtor=C, creditor=A, amount=10)`
5. A is payer → skip

**Balance sheet**: B owes A $10, C owes A $10.

**Next expense**: B pays $20 restaurant bill. Equal split between B and C.

6. `add_expense(payer=B, amount=20, split=EqualSplit, participants=[B,C])`
7. Shares: B=$10, C=$10. C owes B $10.
8. `balance_sheet.update(debtor=C, creditor=B, amount=10)`

**Balance sheet now**: B owes A $10, C owes A $10, C owes B $10.

**`simplify_debts([A,B,C])`:**
- Net: A = +$10+$0 = +$10, B = -$10+$10 = $0, C = -$10-$10 = -$20... wait:

Let me recompute:
- A: creditor of B($10) + creditor of C($10) = net +$20? No — A paid $30, A's share is $10, so A is owed $20.
- B: owes A $10, is owed $10 by C → net $0
- C: owes A $10, owes B $10 → net -$20

Creditors: A(+$20). Debtors: C(-$20).
Transaction: C pays A $20. B nets to $0, no transaction needed.
Result: 1 transaction instead of 3. Debt simplified.

---

## Deep Dive & Extensibility

### 1. "Explain the simplify_debts algorithm — how does it minimize transactions?"

The **min-cash-flow** algorithm works in two steps:

**Step 1**: Compute each user's net position — total received minus total owed across all expenses. This collapses multiple bilateral debts into a single net per person.

**Step 2**: Greedily match the largest debtor with the largest creditor. Each match produces exactly one transaction and reduces the problem size by at least one person (the one who reaches $0 net). The number of transactions is bounded by `n-1` for n users.

Why is this optimal? Because every transaction eliminates at least one person from the system. With n users you need at most n-1 transactions. The greedy approach achieves this bound.

Formally: the minimum transactions needed to settle all debts equals `(number of users with non-zero net balance) - 1` in the worst case. The algorithm achieves this.

Time complexity: O(n log n) for heap operations.

### 2. "What breaks if you store only net balances instead of individual transactions?"

Storing only net balances loses:
- **Expense history**: you can't show "you owed $10 for the dinner on June 3"
- **Audit trail**: no way to dispute or reconstruct individual expenses
- **Split type info**: can't show "50% split" vs "equal split"

The correct design: store both the full `Expense` history and the `BalanceSheet` (net balances). The expense log is append-only. The balance sheet is a derived view that updates incrementally. If you need to recompute the balance sheet, replay all expenses.

### 3. "How would you support recurring expenses?"

Add a `RecurringExpense` entity:

```java
class RecurringExpense {
    private Expense template;        // prototype
    private Frequency frequency;     // WEEKLY, MONTHLY, etc.
    private LocalDate startDate;
    private LocalDate endDate;       // nullable
    private LocalDate nextDue;
    private boolean isActive;
}
```

A background scheduler (cron job or Spring's `@Scheduled` / a `ScheduledExecutorService`) runs daily. For each active `RecurringExpense` where `next_due <= today`, it calls `add_expense` using the template, then advances `next_due` by the frequency period.

The user sees recurring expenses in their history with a `recurring=True` flag. They can cancel a recurring expense (sets `is_active=False`) or edit the template (affects future occurrences only).

### 4. "How would you add multi-currency support?"

Add `currency: str` to `Expense` and a `CurrencyConverter` service:

```java
class CurrencyConverter {
    public double convert(double amount, String fromCurrency, String toCurrency) {
        double rate = getRate(fromCurrency, toCurrency);  // from external API or cache
        return amount * rate;
    }
}
```

BalanceSheet denominates all balances in a base currency (e.g., USD). On `add_expense`, convert each share to USD using the rate at expense creation time. Store the original currency and amount on `Expense` for display purposes.

Challenges: exchange rates change — use the rate at the time of the expense, not at settlement time. Store `fx_rate_at_creation` on each expense for auditability.

---

## Interviewer Questions by Level

**Junior**: Explain what a net balance is and how two expenses between the same people collapse into one balance. Define the three split types and what input each needs.

**Mid-level**: Implement `add_expense` updating balances for non-payer participants. Implement `EqualSplit.calculate` with correct rounding. Explain why you use sorted tuple keys in BalanceSheet to avoid storing A-B and B-A separately.

**Senior**: Explain and implement the min-cash-flow debt simplification algorithm. Discuss why storing only net balances loses information. Design recurring expenses. Explain what happens to the balance sheet if an expense is deleted.

---

## Common Interview Questions

- Q: How does simplify_debts work — explain the algorithm? A: Compute net balance per user (total owed to them minus total they owe). Greedily match the largest debtor with the largest creditor, creating one transaction per match. Each match eliminates at least one person from the system, achieving minimum transactions.
- Q: What's the difference between EqualSplit, ExactSplit, and PercentSplit? A: EqualSplit divides the total evenly (with rounding). ExactSplit takes explicit dollar amounts per person that must sum to the total. PercentSplit takes percentages per person that must sum to 100%.
- Q: Why use a Strategy pattern for split types? A: Different splits need completely different inputs and validation. Strategy isolates this logic — adding a new split type (e.g., ShareSplit) requires one new class, zero changes to Expense or Group.
- Q: What breaks if you store every transaction rather than net balances? A: `get_balances` becomes O(n) per query, scanning all transactions. With net balances, it's O(1) lookup. But pure net balances lose expense history — store both.
- Q: Is the greedy min-cash-flow algorithm always optimal? A: Yes for minimizing transaction count. It achieves the lower bound of `n-1` transactions for n users with non-zero net balances. The key insight: any user who reaches net=0 can be eliminated in a single transaction.
- Q: What if two users have exactly offsetting debts? A: Example: A owes B $10 and B owes A $10 → net for both is $0. BalanceSheet.update with the sorted-key approach would show the balance canceling to $0 and the key gets deleted. No transaction needed.
- Q: How do you handle the payer being in the expense split? A: The payer's share is already covered by their payment. In `add_expense`, skip updating BalanceSheet for the payer — only non-payer participants generate debt entries.

---

## Related

**Patterns applied here**

- [Strategy Pattern](../../03-design-patterns/03-behavioral/strategy-pattern.md)

**SOLID focus**: [Single Responsibility](../../02-solid-principles/01-single-responsibility.md) · [Open/Closed](../../02-solid-principles/02-open-closed.md)

**Practice next**

- [Design Order Management](../03-domain-specific/21-design-order-management.md)
- [Design Inventory Management](../03-domain-specific/24-design-inventory-management.md)

Balance settlement mirrors ledger and stock reconciliation.

**Frameworks**: [LLD Template](../../../07-interview-templates/01-frameworks/02-lld-template.md) · [UML Diagrams](../../uml-diagrams.md) · [OOP Four Pillars](../../01-oop-fundamentals/four-pillars.md)
