# Design Splitwise

> **Difficulty**: Medium
> **Topics**: Strategy Pattern, Graph Simplification, Observer Pattern
> **Key Concepts**: Managing debts, different split types (Equal, Exact, Percent).

---

## What Breaks Without This Design?

```java
class Splitwise {
    // One God class with all data and logic
    private Map<String, Map<String, Double>> balances = new HashMap<>();
    // balances[userA][userB] = amount A owes B

    public void addExpense(String payer, List<String> participants,
                           double amount, String splitType, double[] values) {
        if (splitType.equals("EQUAL")) {
            double share = amount / participants.size();
            for (String p : participants) {
                if (!p.equals(payer)) {
                    // update nested map
                    balances.computeIfAbsent(p, k -> new HashMap<>())
                            .merge(payer, share, Double::sum);
                }
            }
        } else if (splitType.equals("EXACT")) {
            for (int i = 0; i < participants.size(); i++) {
                if (!participants.get(i).equals(payer)) {
                    balances.computeIfAbsent(participants.get(i), k -> new HashMap<>())
                            .merge(payer, values[i], Double::sum);
                }
            }
        } else if (splitType.equals("PERCENT")) {
            // validate sum of percentages == 100 inline in this method
            double total = 0;
            for (double v : values) total += v;
            if (Math.abs(total - 100) > 0.01) throw new IllegalArgumentException("Bad %");
            // split logic inline...
        }
        // Adding a 4th split type requires editing this method
    }
}
```

**Concrete failures**:
1. **OCP violation**: Every new split type (e.g., shares-based) requires editing `addExpense()`. The validation and distribution logic for every type lives in one method.
2. **Validation is inline**: The "percentages must sum to 100" check is buried inside `addExpense`. It cannot be tested independently or reused.
3. **No `Expense` entity**: There is no record of what was paid, by whom, when. Balance simplification and audit trail are impossible.
4. **Circular debt not handled**: `balances[A][B] = 30` and `balances[B][A] = 50` are two separate entries. Net balance is not computed — you need to scan both directions to find "B owes A $20 net."

---

## Derive the Class Structure

**Force 1 — Split calculation varies by type**: Equal, Exact, and Percent require different algorithms to compute each participant's share. Extract `SplitStrategy` interface with `calculateShares(amount, participants, values)`. Each split type is its own strategy class. Validation moves into the strategy (percent strategy validates sum == 100 in its own `validate()` method).

**Force 2 — Expenses need an audit trail**: Without an `Expense` entity, you cannot show "who paid what when." Introduce `Expense` (payer, amount, participants, strategy, timestamp).

**Force 3 — Balance is a derived view, not primary data**: The nested `Map<String, Map<String, Double>>` stores redundant data. A cleaner model: store `Expense` objects; derive balances by summing over expenses. Or store a single `balances[userA][userB]` updated on each expense (simpler for real-time reads).

**Force 4 — Simplify debts is a separate algorithm**: The graph simplification (min transactions to settle) is complex enough to be its own class. It reads the balance map and produces a payment plan. If it lives in `Splitwise`, that class grows unboundedly.

**Result** — the class split these forces produce:
```
God class → SplitwiseService (orchestration: add expense, update balances)
          → Expense (entity: payer, amount, split, timestamp)
          → SplitStrategy (interface: validate + calculateShares)
             → EqualSplit, ExactSplit, PercentSplit
          → BalanceSheet (per-user balance map, net balance query)
          → DebtSimplifier (graph algorithm: min transactions)
          → Group (collection of users + their shared expenses)
```

---

## Real-Life Analogy

**A group of friends on a trip who take turns paying for things.**

Alice pays for dinner ($120 for 4 people). Bob pays for the hotel ($200 for 4 people). Charlie pays for the car rental ($160 for 4 people). At the end of the trip, nobody wants to do 6 separate wire transfers.

Key observations:
- There is a **balance sheet** (a graph): for each pair of users, one person owes the other some net amount.
- When Alice pays $120 split equally: Bob, Charlie, and Diana each owe Alice $30.
- When Bob pays $200: Alice, Charlie, Diana each owe Bob $50. But Alice already has Bob owing her $30 from before — the net is Alice owes Bob $20.
- The balance sheet is **bidirectional**: if Alice owes Bob $20, then Bob is owed $20 by Alice. They're the same edge.
- The smart feature is **"Simplify Debts"**: instead of N*(N-1) transactions, find the minimum number of payments that clears all debts. This is a graph problem solved with a Min-Heap/Max-Heap.

---

## Phase 1: Requirements Gathering

### Goals
- Design an expense sharing application.
- Identify users, groups, and expense types.
- Define how debts are recorded and settled.

### 1. Who are the actors?
- **User**: Adds expenses, views balances, settles debts.
- **Group**: A collection of users sharing expenses.
- **System**: Calculates splits and updates balances.

### 2. What are the must-have features? (Core)
- **Add Expense**: User pays, others owe.
- **Split Types**: Support Equal, Exact, and Percentage splits.
- **Balance Sheet**: Show net balance per user.
- **Settle Up**: Record payments to clear debts.

### 3. What are the constraints?
- **Validation**: Percentages must add to 100%. Exact amounts must equal total.
- **Precision**: Handle currency rounding (2 decimal places).

---

## Phase 2: Use Cases

### UC1: Add Expense
**Actor**: User
**Flow**:
1. User selects a group or friends.
2. User enters total amount and selects split type (e.g., Equal).
3. System validates the split.
4. System calculates individual shares.
5. System updates the Balance Sheet (Graph).
6. System notifies involved users.

### UC2: Settle Up
**Actor**: User
**Flow**:
1. User A pays User B X amount.
2. System records a "Payment" transaction.
3. System updates the debt graph (A owes B reduced by X).

---

## Phase 3: Class Diagram

### Step 1: Core Entities
- **SplitwiseService**: Facade for operations.
- **ExpenseManager**: Manages expenses and balances.
- **Expense**: Stores details (amount, payer, splits).
- **Split**: Abstract class for split logic.
- **User**: Participant.

### Step 2: Relationships
- `Expense` **has-many** `Split`.
- `Split` **references** `User`.
- `ExpenseManager` **has-many** `Expense`.

### UML Diagram

```mermaid
classDiagram
    class SplitwiseService {
        -static SplitwiseService instance
        -Map~String, User~ users
        -Map~String, Group~ groups
        +addExpense(ExpenseType, double, User, List~Split~)
        +showBalance(userId)
    }

    class Expense {
        -String id
        -double amount
        -User paidBy
        -List~Split~ splits
        -ExpenseMetadata metadata
    }

    class Split {
        <<abstract>>
        -User user
        -double amount
    }

    class EqualSplit { ... }
    class ExactSplit { ... }
    class PercentSplit { ... }

    class ExpenseManager {
        -List~Expense~ expenses
        -Map~String, Map~String, Double~~ balanceSheet
        +addExpense(...)
        +calculateBalance(...)
    }

    SplitwiseService --> ExpenseManager
    ExpenseManager --> Expense
    Expense --> Split
    Split <|-- EqualSplit
    Split <|-- ExactSplit
    Split <|-- PercentSplit
```

---

## Phase 4: Design Patterns

### 1. Strategy Pattern
- **Description**: Defines a family of algorithms, encapsulates each one, and makes them interchangeable.
- **Why used**: Different split types (`EqualSplit`, `ExactSplit`, `PercentSplit`) require different validation and calculation logic. Strategy allows adding new split types easily.

### 2. Observer Pattern
- **Description**: Defines a one-to-many dependency between objects so that when one object changes state, all its dependents are notified and updated automatically.
- **Why used**: To notify group members when an expense is added, modified, or settled, ensuring all users have up-to-date balance information.

---

## Phase 5: Code Key Methods

### Java Implementation

```java
import java.util.*;

// 1. Core Entities
class User {
    String id;
    String name;
    public User(String id, String name) { this.id = id; this.name = name; }
}

abstract class Split {
    User user;
    double amount;
    public Split(User user) { this.user = user; }
    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }
}

class EqualSplit extends Split {
    public EqualSplit(User user) { super(user); }
}

class ExactSplit extends Split {
    public ExactSplit(User user, double amount) { super(user); this.amount = amount; }
}

// 2. Expense Models
class Expense {
    String id;
    double amount;
    User paidBy;
    List<Split> splits;
    
    public Expense(double amount, User paidBy, List<Split> splits) {
        this.amount = amount;
        this.paidBy = paidBy;
        this.splits = splits;
    }
}

// 3. Managers
class ExpenseManager {
    List<Expense> expenses;
    Map<String, Map<String, Double>> balanceSheet; // UserID -> (OwedUserID -> Amount)

    public ExpenseManager() {
        expenses = new ArrayList<>();
        balanceSheet = new HashMap<>();
    }

    public void addExpense(double amount, User paidBy, List<Split> splits) {
        Expense expense = new Expense(amount, paidBy, splits);
        expenses.add(expense);

        for (Split split : splits) {
            String paidTo = split.user.id;
            Map<String, Double> balances = balanceSheet.computeIfAbsent(paidBy.id, k -> new HashMap<>());
            
            // Current User (paidBy) receives (+amount) from split user
            if (!balances.containsKey(paidTo)) balances.put(paidTo, 0.0);
            balances.put(paidTo, balances.get(paidTo) + split.getAmount());

            // Split User (paidTo) owes (-amount) to paidBy
            Map<String, Double> debtorBalances = balanceSheet.computeIfAbsent(paidTo, k -> new HashMap<>());
            if (!debtorBalances.containsKey(paidBy.id)) debtorBalances.put(paidBy.id, 0.0);
            debtorBalances.put(paidBy.id, debtorBalances.get(paidBy.id) - split.getAmount());
        }
    }

    public void showBalance(String userId) {
        System.out.println("Balance for " + userId + ":");
        Map<String, Double> balances = balanceSheet.get(userId);
        if (balances == null) {
            System.out.println("No balances.");
            return;
        }
        
        for (Map.Entry<String, Double> entry : balances.entrySet()) {
            if (entry.getValue() != 0) {
                printBalance(userId, entry.getKey(), entry.getValue());
            }
        }
    }

    private void printBalance(String user1, String user2, double amount) {
        if (amount < 0) {
            System.out.println(user1 + " owes " + user2 + ": " + Math.abs(amount));
        } else if (amount > 0) {
            System.out.println(user2 + " owes " + user1 + ": " + amount);
        }
    }
}

// 4. Client
public class SplitwiseDemo {
    public static void main(String[] args) {
        User u1 = new User("u1", "Alice");
        User u2 = new User("u2", "Bob");
        User u3 = new User("u3", "Charlie");

        ExpenseManager manager = new ExpenseManager();

        // 1. Equal Split: Alice paid 300 for Alice, Bob, Charlie (100 each)
        // Note: The logic to divide 300 by 3 would be in the Service layer before creating EqualSplit objects
        
        List<Split> splits = new ArrayList<>();
        // Alice pays 300 total.
        // Alice owes herself 100 (net 0 effect usually filtered, but kept for logic)
        // Bob owes Alice 100
        // Charlie owes Alice 100
        
        Split s2 = new EqualSplit(u2); s2.setAmount(100);
        Split s3 = new EqualSplit(u3); s3.setAmount(100);
        splits.add(s2); splits.add(s3);

        // We only add splits for others to the manager typically, or handle self-split logic internally.
        // For simplicity here, Alice paid 300 total, covering 100 for Bob and 100 for Charlie.
        // We register the debt for Bob and Charlie.
        
        manager.addExpense(300, u1, splits);
        
        manager.showBalance("u2"); // Bob owes ...
        manager.showBalance("u1"); // Alice is owed ...
    }
}
```

---

## Phase 6: Discussion

### Debt Simplification — The Core Algorithm

**Q: How to minimize the number of transactions needed to settle all debts?**

The naive approach (pay each debt individually) results in up to N*(N-1) transactions for N users. The optimized approach uses net balances and a greedy Min-Heap/Max-Heap strategy to find the minimum number of transactions.

**Intuition**: If Alice owes Bob $30 and Charlie owes Bob $20, instead of two transactions, Bob just needs to receive $50. We don't care *who* pays it — we just match creditors against debtors by net balance.

**Algorithm**:
1. Compute each user's net balance: `netBalance[user] = (total paid by user) - (total owed by user)`.
   - Positive net = creditor (others owe them).
   - Negative net = debtor (they owe others).
2. Push all creditors (net > 0) into a **Max-Heap** (largest creditor first).
3. Push all debtors (net < 0) into a **Min-Heap** (largest debtor first, by absolute value).
4. While both heaps are non-empty:
   - Pop the largest creditor `C` (owed the most) and largest debtor `D` (owes the most).
   - Settle `amount = min(|D.net|, C.net)`.
   - Record transaction: `D pays C amount`.
   - Update: `C.net -= amount`, `D.net += amount`.
   - Push back to respective heap if remaining balance ≠ 0.
5. Stop when both heaps are empty. Total transactions = number of settlements recorded.

**Why this minimizes transactions**: Greedy matching of the largest debtor against the largest creditor ensures each transaction either fully clears one party (removing them from the heap) or both parties. In the worst case, N-1 transactions settle N users.

```java
import java.util.*;

class DebtSimplifier {
    
    /**
     * Given a balanceSheet: userId -> (otherUserId -> netAmount)
     * Positive amount means "otherUser owes userId".
     * Negative amount means "userId owes otherUser".
     * 
     * Returns the minimum list of transactions to settle all debts.
     */
    public List<String> simplifyDebts(Map<String, Map<String, Double>> balanceSheet) {
        // Step 1: Compute net balance per user
        Map<String, Double> netBalance = new HashMap<>();
        for (Map.Entry<String, Map<String, Double>> outer : balanceSheet.entrySet()) {
            String user = outer.getKey();
            for (Map.Entry<String, Double> inner : outer.getValue().entrySet()) {
                // positive = user is owed, negative = user owes
                netBalance.merge(user, inner.getValue(), Double::sum);
            }
        }

        // Step 2: Separate into creditors (positive) and debtors (negative)
        // MaxHeap for creditors: [netAmount, userId] — largest creditor first
        PriorityQueue<double[]> creditors = new PriorityQueue<>((a, b) -> Double.compare(b[0], a[0]));
        // MaxHeap for debtors by absolute value: [-netAmount, userId] — largest debtor first
        PriorityQueue<double[]> debtors = new PriorityQueue<>((a, b) -> Double.compare(b[0], a[0]));
        
        List<String> userIds = new ArrayList<>(netBalance.keySet());
        Map<Double, String> indexToUser = new HashMap<>();
        
        // Simplified version using arrays [amount, index]
        // In a real implementation, store userId alongside the amount
        List<double[]> creditList = new ArrayList<>(); // [netAmount]
        List<double[]> debtList = new ArrayList<>();   // [absDebt]
        List<String> creditUsers = new ArrayList<>();
        List<String> debtUsers = new ArrayList<>();
        
        for (Map.Entry<String, Double> entry : netBalance.entrySet()) {
            double net = entry.getValue();
            if (Math.abs(net) < 0.01) continue; // skip zero balances
            if (net > 0) {
                creditUsers.add(entry.getKey());
                creditors.offer(new double[]{net, creditUsers.size() - 1});
            } else {
                debtUsers.add(entry.getKey());
                debtors.offer(new double[]{-net, debtUsers.size() - 1}); // store positive abs value
            }
        }

        // Step 3: Greedy matching
        List<String> transactions = new ArrayList<>();
        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            double[] maxCreditor = creditors.poll(); // [amount, idx]
            double[] maxDebtor = debtors.poll();     // [absAmount, idx]
            
            double settle = Math.min(maxCreditor[0], maxDebtor[0]);
            String creditorName = creditUsers.get((int) maxCreditor[1]);
            String debtorName = debtUsers.get((int) maxDebtor[1]);
            
            transactions.add(debtorName + " pays " + creditorName + ": $" + String.format("%.2f", settle));
            
            maxCreditor[0] -= settle;
            maxDebtor[0] -= settle;
            
            if (maxCreditor[0] > 0.01) creditors.offer(maxCreditor); // creditor still owed
            if (maxDebtor[0] > 0.01) debtors.offer(maxDebtor);       // debtor still owes
        }
        
        return transactions;
    }

    public static void main(String[] args) {
        // Example: Alice paid for Bob ($100) and Charlie ($50)
        // Bob paid for Charlie ($80)
        // Net: Alice +150, Bob -100+80 = -20, Charlie -50-80 = -130... 
        // (simplified manual example)
        
        Map<String, Map<String, Double>> sheet = new HashMap<>();
        sheet.put("Alice", Map.of("Bob", 100.0, "Charlie", 50.0));
        sheet.put("Bob", Map.of("Alice", -100.0, "Charlie", 80.0));
        sheet.put("Charlie", Map.of("Alice", -50.0, "Bob", -80.0));
        
        DebtSimplifier simplifier = new DebtSimplifier();
        List<String> result = simplifier.simplifyDebts(sheet);
        result.forEach(System.out::println);
    }
}
```

**Complexity**:
- Time: O(N log N) — heap operations, where N = number of users with non-zero balance.
- Space: O(N) — for the heaps.

### Scalability
**Q: How to handle millions of users in different groups?**
- A: "Use **Consistent Hashing** to shard the database. Since most expenses are within a `Group`, shard by `GroupID` and ensure all group data resides on the same DB shard to avoid expensive cross-shard transactions when adding expenses or settling up."

### Concurrency
**Q: Handling race conditions (two people editing same expense)?**
- A: "Use **Optimistic Locking** (versioning) on the Expense object. If version mismatch during save, prompt user to refresh. If high contention is expected, queue updates per group in Kafka and process them sequentially via a dedicated worker."

### Financial Precision (SDE-3 Concept)
**Q: How do you handle $100 split 3 ways ($33.33)? Who pays the extra $0.01?**
- A: "Use `BigDecimal` for all calculations to prevent floating-point errors. For rounding remainders: calculate exact shares (`amount / participants`). Distribute the integer pennies evenly. If there's a remainder (e.g., $100 / 3 = $33.33 with $0.01 left over), distribute the remaining pennies randomly or give them to the payer."

---

## SOLID Principles Checklist

- **S (Single Responsibility)**: `Expense` stores data, `ExpenseManager` calculates logic, `Split` defines type.
- **O (Open/Closed)**: New `Split` types (e.g., specific share) can be added by extending `Split`.
- **L (Liskov Substitution)**: `EqualSplit` works wherever `Split` is needed.
- **I (Interface Segregation)**: Not heavily used, but interfaces are clean.
- **D (Dependency Inversion)**: `ExpenseManager` depends on `Split` abstraction.

---

## Interview Questions Asked

### Amazon
1. **"Design an expense sharing app like Splitwise"** → Probe: data model for splits, debt simplification, concurrency on group expenses. Hint: `Expense` owns list of `Split` objects (EqualSplit, ExactSplit, PercentSplit); net balance per user computed as sum of all splits; simplify debts by reducing N*(N-1) edges to at most N-1 transactions using net balance + max-heap/min-heap.

### Common Follow-ups
1. **"Walk me through the debt simplification (graph reduction) algorithm"** → Compute net balance per user (positive = owed money, negative = owes money); use two heaps (max creditor, max debtor); repeatedly match largest creditor with largest debtor, settle min(credit, debt), push remainder back — produces minimum number of transactions in O(N log N).
2. **"How do you handle currency conversion in a multi-currency group?"** → Store all amounts in a base currency (USD) using exchange rate at time of expense creation; display in user's preferred currency using stored rate; avoid re-converting at settlement time (rate may have changed); record exchange_rate and original_currency on each `Expense` for transparency.
3. **"What happens if a payment fails mid-settlement?"** → Use database transactions: debit payer and credit payee atomically; on failure, roll back — both balances unchanged; surface error to user with retry option; for external payment (UPI/Stripe), use idempotency key so retrying the same settlement doesn't double-charge; record payment status (`PENDING`, `COMPLETED`, `FAILED`).
4. **"How do you handle a group expense with unequal splits — percentage vs exact amount?"** → Strategy Pattern: `Split` interface with `calculateShare(totalAmount, participants)`; `PercentSplit` stores percentage per user; `ExactSplit` stores fixed amount per user; `EqualSplit` divides evenly; validation: sum of percentages must equal 100%, sum of exact amounts must equal total — enforce at `Expense` creation time.
