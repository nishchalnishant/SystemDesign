> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design an Online Stock Brokerage System — an LLD problem that tests order lifecycle modeling, account/fund safety under concurrency, and event-driven execution.
>
> **Key concepts:**
> - Core Entities: `Account` (cash balance, buying power), `Portfolio`, `Holding`, `Order` (`MarketOrder`/`LimitOrder`), `ExecutionEngine`, `Stock`/`Quote`.
> - The problem: validating buying power before locking funds, executing market orders immediately against a live quote, and holding limit orders until price conditions are met — all without ever overselling an account's cash.
> - Patterns:
>   - Strategy: `OrderExecutionStrategy` — `MarketOrderExecution` vs `LimitOrderExecution` — decouples order-type-specific execution logic from the engine.
>   - Observer: `ExecutionEngine` observes `Quote` price updates and notifies pending limit orders so they can self-check their trigger condition.
> - Concurrency: Buying power must be checked-and-locked atomically per account (`ReentrantLock` per `Account`), and price-triggered execution of limit orders on the same quote update must not double-fire.
>
> **Key takeaway:** The core difficulty isn't matching logic — it's fund safety. Every order placement is a check-then-act on shared account state, and every price update can concurrently trigger multiple limit orders. Get the locking granularity right (per-account, per-order) and the rest is straightforward state transitions.

---
module: 06-lld
topic: Problems
status: unread
tags: [06-lld, lld, stock-brokerage, strategy, observer, concurrency]
---
# Design a Stock Brokerage System

> **Difficulty**: Hard  
> **Asked at**: Robinhood, Bloomberg, Goldman Sachs, Morgan Stanley  
> **Key Patterns**: Strategy (order execution), Observer (price-triggered limit orders), Command (order lifecycle)

---

## Understanding the Problem

Design an online stock brokerage system where users hold a cash account, place market and limit orders to buy/sell stocks, and see their portfolio update as orders execute. The system must guarantee an account never spends more cash than it has available, execute market orders immediately against a live quote, and hold limit orders until the market price crosses the specified threshold.

---

## Clarifying Questions

**You**: "What order types do we need to support?"  
**Interviewer**: "Two: market orders and limit orders. No stop-loss, no stop-limit, no trailing stop — keep it to those two for the core design."

**You**: "Do we need to build a full matching engine that pairs buyers and sellers, like a real exchange?"  
**Interviewer**: "No. Assume there's an external live quote feed for each stock giving the current market price. A market order executes immediately at that quote. A limit order executes once the quote crosses the limit price. We're simulating execution against the feed, not building exchange-level order matching."

**You**: "How does buying power / balance checking work?"  
**Interviewer**: "Each account has a cash balance. Before placing a buy order, the system must verify sufficient buying power and lock those funds so they can't be double-spent by a second concurrent order. On execution, locked funds are debited; on cancellation, they're released back."

**You**: "Do we need to track portfolio holdings — average cost, quantity per stock?"  
**Interviewer**: "Yes. After a buy executes, the portfolio's holding for that stock increases with a running average cost. After a sell executes, quantity decreases and proceeds are credited to cash."

**You**: "Should users be able to cancel a pending order?"  
**Interviewer**: "Yes, for limit orders that haven't executed yet. Market orders execute synchronously on placement so there's nothing to cancel. Cancelling a limit order must release its locked funds."

**You**: "What happens if a limit buy order's price is never reached?"  
**Interviewer**: "It stays PENDING indefinitely until cancelled or the market closes — don't worry about market-hours expiry for this design."

**You**: "Is this single-threaded or do we need to handle concurrent order placement and price updates?"  
**Interviewer**: "Assume many users placing orders concurrently, and price updates arriving concurrently with order placement. Thread safety is a first-class requirement."

---

## Final Requirements

**In scope:**
1. Place market orders (buy/sell) — execute immediately against the current quote
2. Place limit orders (buy/sell) — hold as PENDING until price crosses limit, then execute
3. Validate and lock buying power at order placement time; release on cancel, debit on execution
4. Cancel a pending limit order
5. Track portfolio holdings per account: quantity and running average cost per stock
6. React to quote/price updates and trigger eligible limit orders (Observer)
7. Thread-safe fund locking and order execution under concurrent access

**Out of scope:**
- Stop-loss / stop-limit / trailing-stop orders (covered as an extension)
- A full exchange-style matching engine (bid/ask order book, price-time priority matching)
- Margin trading, short selling, options
- Real payment/settlement rails, tax lot accounting (FIFO/LIFO), regulatory reporting

---

## Core Entities and Relationships

| Entity | Responsibility |
|--------|---------------|
| Account | Owns cash balance, lockedFunds, and a Portfolio; validates/locks/releases buying power |
| Portfolio | Owns the map of Holdings for an account |
| Holding | Tracks quantity and average cost for one stock in a portfolio |
| Order (abstract) | Common order fields — id, account, stock, side, quantity, status |
| MarketOrder | Order subtype executed immediately at current quote |
| LimitOrder | Order subtype held PENDING until quote crosses limitPrice |
| OrderExecutionStrategy | Strategy interface — how a given order type gets its execution price/condition |
| ExecutionEngine | Subject in Observer; owns Quotes, executes orders, notifies pending limit orders on price change |
| Stock / Quote | Symbol + live market price, observed by the engine |

`Account` owns one `Portfolio`, which owns many `Holding`s keyed by stock symbol. `ExecutionEngine` is the central coordinator: it validates and locks funds on the `Account`, executes `MarketOrder`s synchronously via `MarketOrderExecution`, and registers `LimitOrder`s as observers of `Quote` price updates via `LimitOrderExecution`. When a `Quote` updates, it notifies the engine, which checks all pending limit orders for that symbol and executes any whose trigger condition is now satisfied.

---

## Class Design

### Account

| Requirement | What Account must track |
|-------------|-------------------------|
| Available cash | cashBalance: double |
| Funds reserved by open orders | lockedFunds: double |
| Holdings | portfolio: Portfolio |
| Atomic check-and-lock | _lock: ReentrantLock |

```
class Account:
- account_id: str
- cash_balance: double
- locked_funds: double
- portfolio: Portfolio
- _lock: ReentrantLock

+ available_buying_power() -> double      # cash_balance - locked_funds
+ lock_funds(amount: double) -> bool      # atomic check-then-lock
+ release_funds(amount: double) -> None
+ debit(amount: double) -> None           # on buy execution, locked -> spent
+ credit(amount: double) -> None          # on sell execution
```

### Portfolio / Holding

```
class Holding:
- symbol: str
- quantity: int
- average_cost: double

+ apply_buy(qty: int, price: double) -> None
+ apply_sell(qty: int) -> None

class Portfolio:
- account_id: str
- holdings: dict[str, Holding]
- _lock: ReentrantLock

+ get_holding(symbol: str) -> Holding | None
+ apply_execution(symbol: str, side: OrderSide, qty: int, price: double) -> None
```

### Order Hierarchy

```
enum OrderSide: BUY, SELL
enum OrderStatus: PENDING, EXECUTED, CANCELLED, REJECTED

abstract class Order:
- order_id: str
- account: Account
- symbol: str
- side: OrderSide
- quantity: int
- status: OrderStatus
- created_at: datetime
- locked_amount: double            # funds reserved for this order (0 for sells)

+ get_execution_strategy() -> OrderExecutionStrategy   # abstract

class MarketOrder(Order):
+ get_execution_strategy() -> MarketOrderExecution

class LimitOrder(Order):
- limit_price: double
+ get_execution_strategy() -> LimitOrderExecution
+ is_triggered(current_price: double) -> bool
```

### OrderExecutionStrategy (Strategy Pattern)

```
interface OrderExecutionStrategy:
+ resolve_price(order: Order, quote: Quote) -> double | None
    # returns the execution price if the order should fill now, else None

class MarketOrderExecution implements OrderExecutionStrategy:
+ resolve_price(order, quote) -> quote.price   # always fills immediately

class LimitOrderExecution implements OrderExecutionStrategy:
+ resolve_price(order, quote) -> double | None
    # BUY: fills if quote.price <= order.limit_price, at limit_price or better
    # SELL: fills if quote.price >= order.limit_price, at limit_price or better
```

### Quote (Observable) and ExecutionEngine (Observer)

```
interface PriceObserver:
+ on_price_update(symbol: str, new_price: double) -> None

class Quote:
- symbol: str
- price: double
- _observers: list[PriceObserver]

+ update_price(new_price: double) -> None    # sets price, notifies observers
+ subscribe(observer: PriceObserver) -> None

class ExecutionEngine implements PriceObserver:
- quotes: dict[str, Quote]
- pending_limit_orders: dict[str, list[LimitOrder]]   # keyed by symbol
- _pending_lock: ReentrantLock

+ place_order(order: Order) -> None
+ execute_market_order(order: MarketOrder) -> None
+ cancel_order(order_id: str) -> bool
+ on_price_update(symbol: str, new_price: double) -> None   # checks + fires eligible limit orders
```

---

## Implementation

### Core Method: placeOrder

**Core logic:**
1. Compute the worst-case cost to reserve: market buy → `quantity * currentQuotePrice` (a small slippage buffer in real systems, quote price here); limit buy → `quantity * limitPrice`. Sells reserve nothing (they reduce a holding, not cash) but must validate holding quantity.
2. Atomically check-and-lock buying power on the `Account` (single `ReentrantLock` acquisition — no window between check and lock).
3. Market orders execute synchronously inline. Limit orders are registered as pending and wait for a price observer callback.
4. On any failure, no funds are locked and status is `REJECTED`.

**Edge cases:**
- Insufficient buying power — reject before touching order state
- Sell order for more shares than currently held — reject
- Concurrent buy orders from the same account racing on the same locked funds — must serialize on the account lock

```java
public class ExecutionEngine implements PriceObserver {

    private final Map<String, Quote> quotes = new ConcurrentHashMap<>();
    private final Map<String, List<LimitOrder>> pendingLimitOrders = new ConcurrentHashMap<>();
    private final Object pendingLock = new Object();
    private final Map<String, Order> allOrders = new ConcurrentHashMap<>();

    public Order placeOrder(Order order) {
        Account account = order.getAccount();

        if (order.getSide() == OrderSide.BUY) {
            double reserveAmount = estimateReserveAmount(order);
            boolean locked = account.lockFunds(reserveAmount);
            if (!locked) {
                order.setStatus(OrderStatus.REJECTED);
                throw new InsufficientFundsException(
                    "Account " + account.getAccountId() + " lacks buying power for order " + order.getOrderId());
            }
            order.setLockedAmount(reserveAmount);
        } else {
            boolean hasShares = account.getPortfolio().hasSufficientQuantity(order.getSymbol(), order.getQuantity());
            if (!hasShares) {
                order.setStatus(OrderStatus.REJECTED);
                throw new InsufficientHoldingsException(
                    "Account " + account.getAccountId() + " lacks shares to sell for order " + order.getOrderId());
            }
        }

        allOrders.put(order.getOrderId(), order);

        if (order instanceof MarketOrder marketOrder) {
            executeMarketOrder(marketOrder);
        } else if (order instanceof LimitOrder limitOrder) {
            synchronized (pendingLock) {
                pendingLimitOrders
                    .computeIfAbsent(order.getSymbol(), k -> new CopyOnWriteArrayList<>())
                    .add(limitOrder);
            }
        }
        return order;
    }

    private double estimateReserveAmount(Order order) {
        Quote quote = quotes.get(order.getSymbol());
        if (quote == null) {
            throw new NoSuchElementException("No quote for symbol: " + order.getSymbol());
        }
        double referencePrice = (order instanceof LimitOrder lo) ? lo.getLimitPrice() : quote.getPrice();
        return referencePrice * order.getQuantity();
    }
}
```

### Core Method: executeMarketOrder

**Core logic:**
1. Resolve the fill price via `MarketOrderExecution` strategy (the live quote).
2. Debit or credit the account: BUY converts locked funds into an actual cash debit (release any over-reservation if fill price < reserved price); SELL credits proceeds directly.
3. Apply the fill to the account's `Portfolio` holding.
4. Mark the order `EXECUTED`.

```java
    private final OrderExecutionStrategy marketStrategy = new MarketOrderExecution();

    public void executeMarketOrder(MarketOrder order) {
        Quote quote = quotes.get(order.getSymbol());
        if (quote == null) {
            throw new NoSuchElementException("No quote for symbol: " + order.getSymbol());
        }
        Double fillPrice = marketStrategy.resolvePrice(order, quote);
        fillOrder(order, fillPrice);
    }

    private void fillOrder(Order order, double fillPrice) {
        Account account = order.getAccount();
        double notional = fillPrice * order.getQuantity();

        if (order.getSide() == OrderSide.BUY) {
            // Convert the reservation into a real debit; refund any slippage-favorable delta.
            account.settleBuy(order.getLockedAmount(), notional);
        } else {
            account.credit(notional);
        }

        account.getPortfolio().applyExecution(order.getSymbol(), order.getSide(), order.getQuantity(), fillPrice);
        order.setStatus(OrderStatus.EXECUTED);
        order.setFillPrice(fillPrice);
    }
```

### Core Method: checkAndExecuteLimitOrders (Observer callback on price update)

**Core logic:**
1. Triggered by `Quote.updatePrice()` calling `engine.onPriceUpdate(symbol, newPrice)`.
2. Snapshot and iterate pending limit orders for that symbol; for each, ask `LimitOrderExecution` whether it now triggers.
3. Fill triggered orders and remove them from the pending list — this removal must be atomic with respect to other threads processing the same price update or a concurrent cancel, so no order fires twice and a cancelled order never fires.

**Edge cases:**
- Two price updates arrive back-to-back on different threads for the same symbol — only one may claim and execute a given order
- An order is cancelled concurrently with a price update that would have triggered it — cancel must win if it acquires the order's state transition first

```java
    private final OrderExecutionStrategy limitStrategy = new LimitOrderExecution();

    @Override
    public void onPriceUpdate(String symbol, double newPrice) {
        List<LimitOrder> candidates = pendingLimitOrders.get(symbol);
        if (candidates == null || candidates.isEmpty()) {
            return;
        }

        for (LimitOrder order : candidates) {
            // Atomically claim the order: only one thread may transition PENDING -> EXECUTING.
            if (!order.tryClaim()) {
                continue;
            }

            Quote quote = quotes.get(symbol);
            Double fillPrice = limitStrategy.resolvePrice(order, quote);
            if (fillPrice == null) {
                order.releaseClaim();   // condition not met (yet) or was raced away; put back to PENDING
                continue;
            }

            fillOrder(order, fillPrice);
            candidates.remove(order);
        }
    }
```

### Core Method: cancelOrder

**Core logic:**
1. Only `PENDING` limit orders are cancellable.
2. Atomically transition status to `CANCELLED` — this uses the same claim mechanism as execution so a cancel racing a fill can't both succeed.
3. Release locked funds back to the account and remove the order from the pending list.

```java
    public boolean cancelOrder(String orderId) {
        Order order = allOrders.get(orderId);
        if (!(order instanceof LimitOrder limitOrder)) {
            return false;   // market orders execute synchronously; nothing to cancel
        }

        if (!limitOrder.tryClaim()) {
            return false;   // already executing/executed/cancelled elsewhere
        }

        limitOrder.setStatus(OrderStatus.CANCELLED);
        if (limitOrder.getSide() == OrderSide.BUY) {
            limitOrder.getAccount().releaseFunds(limitOrder.getLockedAmount());
        }

        List<LimitOrder> candidates = pendingLimitOrders.get(limitOrder.getSymbol());
        if (candidates != null) {
            candidates.remove(limitOrder);
        }
        return true;
    }
```

---

## Verification

**Scenario**: Account has `cashBalance = $5,000`, `lockedFunds = $0`. Stock `AAPL` quote is `$180.00`. User places a market buy for 10 shares.

1. `placeOrder(MarketOrder(AAPL, BUY, qty=10))`
2. `estimateReserveAmount` → `10 * $180.00 = $1,800.00`
3. `account.lockFunds(1800.00)` — `available = 5000 - 0 = 5000 >= 1800` → succeeds; `lockedFunds = 1800.00`
4. `executeMarketOrder` → `marketStrategy.resolvePrice` returns the live quote, `$180.00` (no slippage in this simplified model)
5. `fillOrder`: `notional = 10 * 180.00 = 1800.00`; `account.settleBuy(lockedAmount=1800.00, notional=1800.00)` → `cashBalance: 5000 → 3200.00`, `lockedFunds: 1800 → 0`
6. `portfolio.applyExecution(AAPL, BUY, 10, 180.00)` → new `Holding(AAPL, quantity=10, averageCost=180.00)`
7. Order status → `EXECUTED`, `fillPrice = 180.00`

**Resulting state**: `cashBalance = $3,200.00`, `lockedFunds = $0.00`, portfolio holds 10 shares of AAPL at avg cost $180.00. Total account value (`3200 + 10*180 = 5000`) is conserved.

**Follow-up**: User places a limit sell for 10 AAPL at `$190.00`. Order goes `PENDING`, added to `pendingLimitOrders["AAPL"]`. A later quote update `AAPL: $180 → $191.00` triggers `onPriceUpdate`: `LimitOrderExecution.resolvePrice` sees `SELL` and `191.00 >= 190.00` → fills at `191.00`. `notional = 1910.00`, credited to cash: `3200 → 5110.00`. Holding quantity → 0. Order → `EXECUTED`.

---

## Deep Dive & Extensibility

### 1. "How do you prevent overselling / double-spending buying power under concurrent orders from the same account?"

The failure mode: two threads read `availableBuyingPower()` concurrently, both see enough funds, both proceed to lock — total locked exceeds actual cash. The fix is to make **check-and-lock a single atomic operation** guarded by a per-account lock, never a separate read-then-write.

```java
public class Account {
    private double cashBalance;
    private double lockedFunds;
    private final ReentrantLock lock = new ReentrantLock();

    public boolean lockFunds(double amount) {
        lock.lock();
        try {
            double available = cashBalance - lockedFunds;
            if (available < amount) {
                return false;
            }
            lockedFunds += amount;
            return true;
        } finally {
            lock.unlock();
        }
    }

    public void releaseFunds(double amount) {
        lock.lock();
        try {
            lockedFunds = Math.max(0, lockedFunds - amount);
        } finally {
            lock.unlock();
        }
    }

    public void settleBuy(double lockedAmount, double actualNotional) {
        lock.lock();
        try {
            lockedFunds -= lockedAmount;
            cashBalance -= actualNotional;
        } finally {
            lock.unlock();
        }
    }

    public void credit(double amount) {
        lock.lock();
        try {
            cashBalance += amount;
        } finally {
            lock.unlock();
        }
    }
}
```

A per-account `ReentrantLock` gives correctness with parallelism across *different* accounts — orders on account A never block orders on account B. This is the same shape as the parking lot's per-floor locking: shard the lock to the unit of contention, not globally.

### 2. "How would you support stop-loss orders?"

A stop-loss is structurally a *dormant* order that becomes a market order once triggered — it needs a new `Order` subtype and execution strategy, but no change to the engine's Observer wiring, because `Order` is Open/Closed via the Strategy interface.

```java
public class StopLossOrder extends Order {
    private final double stopPrice;

    public StopLossOrder(String orderId, Account account, String symbol,
                          int quantity, double stopPrice) {
        super(orderId, account, symbol, OrderSide.SELL, quantity);
        this.stopPrice = stopPrice;
    }

    public double getStopPrice() { return stopPrice; }

    @Override
    public OrderExecutionStrategy getExecutionStrategy() {
        return new StopLossExecution();
    }
}

public class StopLossExecution implements OrderExecutionStrategy {
    @Override
    public Double resolvePrice(Order order, Quote quote) {
        StopLossOrder stopOrder = (StopLossOrder) order;
        // Trigger once price falls to/through the stop price; fill at current market price
        // (real venues model post-trigger slippage — simplified here to quote price).
        if (quote.getPrice() <= stopOrder.getStopPrice()) {
            return quote.getPrice();
        }
        return null;
    }
}
```

`ExecutionEngine.onPriceUpdate` needs no new branch: it already dispatches through `order.getExecutionStrategy().resolvePrice(order, quote)` polymorphically. `StopLossOrder` is registered into `pendingLimitOrders` (renamed conceptually to `pendingTriggerOrders`) exactly like a `LimitOrder`. This is the Strategy pattern paying for itself — adding an order type touches zero engine logic.

### 3. "How would you handle partial fills for large limit orders?"

The simplified model assumes a live quote can absorb any quantity at one price — unrealistic for large orders against a real order book. To support partial fills, `Order` needs a `filledQuantity` separate from `quantity`, and `fillOrder` becomes incremental rather than terminal.

```java
public abstract class Order {
    private int quantity;
    private int filledQuantity = 0;
    private OrderStatus status = OrderStatus.PENDING;

    public int getRemainingQuantity() {
        return quantity - filledQuantity;
    }

    public synchronized void applyPartialFill(int fillQty, double fillPrice) {
        if (fillQty > getRemainingQuantity()) {
            throw new IllegalStateException("Fill exceeds remaining quantity");
        }
        filledQuantity += fillQty;
        status = (filledQuantity == quantity) ? OrderStatus.EXECUTED : OrderStatus.PARTIALLY_FILLED;
    }
}
```

`fillOrder` in the engine would take an available-liquidity hint from the quote feed (e.g., `Quote.getAvailableSize()`), fill `min(remainingQuantity, availableSize)`, credit/debit proportionally to the partial notional, and update the `Holding` incrementally with a weighted-average cost recalculation on each partial buy fill. The order stays in `pendingLimitOrders` (or a `partiallyFilledOrders` set) until `filledQuantity == quantity`, at which point it's removed. Locked funds are only released proportionally — `releaseFunds(lockedAmount * (remainingQuantity / originalQuantity))` — on cancellation of a partially filled order.

---

## Interviewer Questions by Level

**Junior**: Define the `Order` class hierarchy and explain the difference between `MarketOrder` and `LimitOrder`. Describe what fields `Account` needs to track available cash. Sketch how `Portfolio` and `Holding` relate.

**Mid-level**: Implement `placeOrder` with correct buying-power validation and locking. Explain why funds must be locked at placement time rather than execution time. Implement the Strategy interface for order execution and explain why it's preferable to `if/else` branching on order type in the engine.

**Senior**: Identify the check-then-act race in fund locking and fix it with atomic per-account locking. Design the Observer wiring so a `Quote` price update can trigger multiple pending limit orders without double-executing any of them under concurrent price updates. Extend the design for stop-loss orders without modifying `ExecutionEngine`. Discuss partial fills and how average cost basis must be recalculated incrementally.

---

## Common Interview Questions

- Q: Why lock funds at order placement instead of at execution time? A: Between placement and execution, the same cash could otherwise be used to lock a second order, letting the account spend more than it has. Locking at placement makes buying power a real-time, enforceable invariant.
- Q: Why use Strategy for order execution instead of a switch statement in the engine? A: New order types (stop-loss, trailing-stop) can be added by implementing `OrderExecutionStrategy` without touching `ExecutionEngine` — Open/Closed principle. It also isolates each order type's trigger/fill logic for independent testing.
- Q: Why is `Quote` the Subject and `ExecutionEngine` the Observer rather than the reverse? A: Price updates originate externally (from a feed) and are the actual event of interest; multiple things could plausibly want to react to a price change (execution engine, analytics, alerts) — Observer decouples the feed from any specific consumer.
- Q: What happens if a limit order's account is closed while the order is still pending? A: Out of scope for the core design, but in a real system you'd either cascade-cancel pending orders on account closure or block closure while pending orders exist — both require the same `tryClaim`/atomic-cancel mechanism already built.
- Q: How do you avoid double-executing a limit order when two price updates for the same symbol arrive concurrently? A: `LimitOrder.tryClaim()` performs an atomic PENDING→EXECUTING CAS-style transition; only the thread that wins the claim proceeds to fill, others skip or release the claim if the trigger condition wasn't actually met.
- Q: How is average cost basis computed on repeated buys? A: `Holding.applyBuy` recomputes `averageCost = (oldQty * oldAvgCost + fillQty * fillPrice) / (oldQty + fillQty)`, then updates `quantity`. Sells reduce quantity but do not change average cost (realized P&L is computed separately against the existing average cost).
- Q: Why does a sell order not lock cash? A: A sell doesn't spend cash, it spends shares — the analogous invariant is validating and reserving share quantity in the `Portfolio`, not `Account.lockFunds`.

---

## Concurrency Test Harness

Runnable tests that verify thread-safety invariants. No external deps — uses `java.util.concurrent` and `java.util.*` only.

```java
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

// --- Minimal stubs to make the harness self-contained ---

enum OrderSide { BUY, SELL }
enum OrderStatus { PENDING, EXECUTING, EXECUTED, CANCELLED, REJECTED }

class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(String message) { super(message); }
}

class Account {
    private final String accountId;
    private double cashBalance;
    private double lockedFunds;
    private final ReentrantLock lock = new ReentrantLock();

    public Account(String accountId, double cashBalance) {
        this.accountId = accountId;
        this.cashBalance = cashBalance;
    }

    public boolean lockFunds(double amount) {
        lock.lock();
        try {
            double available = cashBalance - lockedFunds;
            if (available < amount) {
                return false;
            }
            lockedFunds += amount;
            return true;
        } finally {
            lock.unlock();
        }
    }

    public void releaseFunds(double amount) {
        lock.lock();
        try {
            lockedFunds = Math.max(0, lockedFunds - amount);
        } finally {
            lock.unlock();
        }
    }

    public void settleBuy(double lockedAmount, double actualNotional) {
        lock.lock();
        try {
            lockedFunds -= lockedAmount;
            cashBalance -= actualNotional;
        } finally {
            lock.unlock();
        }
    }

    public String getAccountId() { return accountId; }
    public double getCashBalance() { lock.lock(); try { return cashBalance; } finally { lock.unlock(); } }
    public double getLockedFunds() { lock.lock(); try { return lockedFunds; } finally { lock.unlock(); } }
}

class Quote {
    private final String symbol;
    private volatile double price;

    public Quote(String symbol, double price) {
        this.symbol = symbol;
        this.price = price;
    }

    public String getSymbol() { return symbol; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
}

class LimitOrder {
    private final String orderId;
    private final Account account;
    private final String symbol;
    private final OrderSide side;
    private final int quantity;
    private final double limitPrice;
    private double lockedAmount;
    private volatile OrderStatus status = OrderStatus.PENDING;
    private final ReentrantLock claimLock = new ReentrantLock();

    public LimitOrder(String orderId, Account account, String symbol,
                       OrderSide side, int quantity, double limitPrice) {
        this.orderId = orderId;
        this.account = account;
        this.symbol = symbol;
        this.side = side;
        this.quantity = quantity;
        this.limitPrice = limitPrice;
    }

    // Atomic PENDING -> EXECUTING transition; only one thread ever wins.
    public boolean tryClaim() {
        claimLock.lock();
        try {
            if (status != OrderStatus.PENDING) {
                return false;
            }
            status = OrderStatus.EXECUTING;
            return true;
        } finally {
            claimLock.unlock();
        }
    }

    public void releaseClaim() {
        claimLock.lock();
        try {
            if (status == OrderStatus.EXECUTING) {
                status = OrderStatus.PENDING;
            }
        } finally {
            claimLock.unlock();
        }
    }

    public void markExecuted() { status = OrderStatus.EXECUTED; }
    public void markCancelled() { status = OrderStatus.CANCELLED; }

    public String getOrderId() { return orderId; }
    public Account getAccount() { return account; }
    public String getSymbol() { return symbol; }
    public OrderSide getSide() { return side; }
    public int getQuantity() { return quantity; }
    public double getLimitPrice() { return limitPrice; }
    public double getLockedAmount() { return lockedAmount; }
    public void setLockedAmount(double amount) { this.lockedAmount = amount; }
    public OrderStatus getStatus() { return status; }
}

class ExecutionEngine {
    private final Map<String, Quote> quotes = new ConcurrentHashMap<>();
    private final Map<String, List<LimitOrder>> pendingLimitOrders = new ConcurrentHashMap<>();
    private final AtomicInteger fillCount = new AtomicInteger(0);

    public void registerQuote(Quote quote) {
        quotes.put(quote.getSymbol(), quote);
    }

    public LimitOrder placeLimitOrder(String orderId, Account account, String symbol,
                                       OrderSide side, int quantity, double limitPrice) {
        LimitOrder order = new LimitOrder(orderId, account, symbol, side, quantity, limitPrice);
        if (side == OrderSide.BUY) {
            double reserve = limitPrice * quantity;
            if (!account.lockFunds(reserve)) {
                throw new InsufficientFundsException("Insufficient buying power for " + orderId);
            }
            order.setLockedAmount(reserve);
        }
        pendingLimitOrders.computeIfAbsent(symbol, k -> new CopyOnWriteArrayList<>()).add(order);
        return order;
    }

    public boolean cancelOrder(LimitOrder order) {
        if (!order.tryClaim()) {
            return false;
        }
        order.markCancelled();
        if (order.getSide() == OrderSide.BUY) {
            order.getAccount().releaseFunds(order.getLockedAmount());
        }
        List<LimitOrder> list = pendingLimitOrders.get(order.getSymbol());
        if (list != null) list.remove(order);
        return true;
    }

    // Simulates the Observer callback fired by Quote.updatePrice().
    public void onPriceUpdate(String symbol, double newPrice) {
        Quote quote = quotes.get(symbol);
        quote.setPrice(newPrice);

        List<LimitOrder> candidates = pendingLimitOrders.get(symbol);
        if (candidates == null) return;

        for (LimitOrder order : candidates) {
            if (!order.tryClaim()) {
                continue;
            }
            boolean triggered = (order.getSide() == OrderSide.BUY)
                ? newPrice <= order.getLimitPrice()
                : newPrice >= order.getLimitPrice();

            if (!triggered) {
                order.releaseClaim();
                continue;
            }

            double notional = newPrice * order.getQuantity();
            if (order.getSide() == OrderSide.BUY) {
                order.getAccount().settleBuy(order.getLockedAmount(), notional);
            }
            order.markExecuted();
            fillCount.incrementAndGet();
            candidates.remove(order);
        }
    }

    public int getFillCount() { return fillCount.get(); }
}

// --- Tests ---

class StockBrokerageConcurrencyTest {

    // ─────────────────────────────────────────────────────────────
    // TEST 1: No overselling of buying power under concurrent orders
    // from the SAME account. Account has $10,000. 100 threads each try
    // to lock $200 (limit buy 1 share @ $200). Exactly 50 must succeed
    // (50 * $200 = $10,000); the rest must be rejected.
    // ─────────────────────────────────────────────────────────────
    static void testNoOverspendUnderConcurrentOrders() throws InterruptedException {
        Account account = new Account("ACC-1", 10_000.0);
        ExecutionEngine engine = new ExecutionEngine();
        engine.registerQuote(new Quote("XYZ", 200.0));

        List<LimitOrder> succeeded = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger rejected = new AtomicInteger(0);

        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            final int idx = i;
            Thread t = new Thread(() -> {
                try {
                    LimitOrder order = engine.placeLimitOrder(
                        "ORD-" + idx, account, "XYZ", OrderSide.BUY, 1, 200.0);
                    succeeded.add(order);
                } catch (InsufficientFundsException e) {
                    rejected.incrementAndGet();
                }
            });
            threads.add(t);
        }
        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();

        if (succeeded.size() != 50) {
            throw new AssertionError("Expected 50 locked orders, got " + succeeded.size());
        }
        if (rejected.get() != 50) {
            throw new AssertionError("Expected 50 rejections, got " + rejected.get());
        }
        if (account.getLockedFunds() != 10_000.0) {
            throw new AssertionError("Locked funds mismatch: " + account.getLockedFunds());
        }
        if (account.getCashBalance() != 10_000.0) {
            throw new AssertionError("Cash balance should be untouched pre-execution: " + account.getCashBalance());
        }

        System.out.println("PASS: testNoOverspendUnderConcurrentOrders");
    }

    // ─────────────────────────────────────────────────────────────
    // TEST 2: Concurrent price updates on the same symbol must not
    // double-execute the same limit order. 20 threads all fire the
    // SAME triggering price update simultaneously; the order must
    // execute exactly once.
    // ─────────────────────────────────────────────────────────────
    static void testNoDoubleExecutionOnConcurrentPriceUpdates() throws InterruptedException {
        Account account = new Account("ACC-2", 5_000.0);
        ExecutionEngine engine = new ExecutionEngine();
        engine.registerQuote(new Quote("AAPL", 200.0));

        LimitOrder order = engine.placeLimitOrder("ORD-LIMIT-1", account, "AAPL", OrderSide.BUY, 10, 190.0);
        if (account.getLockedFunds() != 1900.0) {
            throw new AssertionError("Expected 1900 locked, got " + account.getLockedFunds());
        }

        int threadCount = 20;
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            Thread t = new Thread(() -> {
                try {
                    startLatch.await();
                } catch (InterruptedException ignored) {}
                engine.onPriceUpdate("AAPL", 185.0);   // all threads race the same trigger
            });
            threads.add(t);
        }
        for (Thread t : threads) t.start();
        startLatch.countDown();
        for (Thread t : threads) t.join();

        if (engine.getFillCount() != 1) {
            throw new AssertionError("Expected exactly 1 fill, got " + engine.getFillCount());
        }
        if (order.getStatus() != OrderStatus.EXECUTED) {
            throw new AssertionError("Order should be EXECUTED, was " + order.getStatus());
        }
        double expectedNotional = 185.0 * 10;
        double expectedCash = 5000.0 - expectedNotional;
        if (Math.abs(account.getCashBalance() - expectedCash) > 1e-9) {
            throw new AssertionError("Cash mismatch: " + account.getCashBalance() + " expected " + expectedCash);
        }
        if (account.getLockedFunds() != 0.0) {
            throw new AssertionError("Locked funds should be released to 0, got " + account.getLockedFunds());
        }

        System.out.println("PASS: testNoDoubleExecutionOnConcurrentPriceUpdates");
    }

    // ─────────────────────────────────────────────────────────────
    // TEST 3: Cancel racing with a triggering price update — exactly
    // one of {cancel, execute} wins; funds are never both released
    // AND debited.
    // ─────────────────────────────────────────────────────────────
    static void testCancelRacesExecution() throws InterruptedException {
        Account account = new Account("ACC-3", 2_000.0);
        ExecutionEngine engine = new ExecutionEngine();
        engine.registerQuote(new Quote("MSFT", 100.0));

        LimitOrder order = engine.placeLimitOrder("ORD-RACE-1", account, "MSFT", OrderSide.BUY, 5, 90.0);

        AtomicInteger cancelWins = new AtomicInteger(0);
        Thread cancelThread = new Thread(() -> {
            if (engine.cancelOrder(order)) {
                cancelWins.incrementAndGet();
            }
        });
        Thread priceThread = new Thread(() -> engine.onPriceUpdate("MSFT", 85.0));

        cancelThread.start();
        priceThread.start();
        cancelThread.join();
        priceThread.join();

        boolean executed = order.getStatus() == OrderStatus.EXECUTED;
        boolean cancelled = order.getStatus() == OrderStatus.CANCELLED;
        if (executed == cancelled) {
            throw new AssertionError("Order must end in exactly one terminal state, got " + order.getStatus());
        }
        // Funds must be fully accounted for either way: 0 locked remaining.
        if (account.getLockedFunds() != 0.0) {
            throw new AssertionError("Locked funds leaked: " + account.getLockedFunds());
        }

        System.out.println("PASS: testCancelRacesExecution (result: " + order.getStatus() + ")");
    }

    public static void main(String[] args) throws InterruptedException {
        testNoOverspendUnderConcurrentOrders();
        testNoDoubleExecutionOnConcurrentPriceUpdates();
        testCancelRacesExecution();
        System.out.println("All concurrency tests passed.");
    }
}
```

**What each test verifies:**
- `testNoOverspendUnderConcurrentOrders`: The per-account `ReentrantLock` in `lockFunds` makes check-then-lock atomic, so 100 racing threads against a $10,000 account can never collectively lock more than $10,000 — exactly 50 of the $200 orders succeed.
- `testNoDoubleExecutionOnConcurrentPriceUpdates`: `LimitOrder.tryClaim()` guarantees only one of 20 concurrent threads processing the identical price update can transition the order out of `PENDING`, so `fillCount` is exactly 1 and cash is debited exactly once.
- `testCancelRacesExecution`: Cancellation and price-triggered execution both go through the same `tryClaim()` gate, so they can never both succeed on the same order — funds are either fully released (cancel wins) or fully debited (execution wins), never both or neither.

---

## Related

**Patterns applied here**

- [Strategy Pattern](../../03-design-patterns/03-behavioral/strategy-pattern.md)
- [Observer Pattern](../../03-design-patterns/03-behavioral/observer-pattern.md)

**SOLID focus**: [Open/Closed](../../02-solid-principles/02-open-closed.md)

**Concurrency**: [Concurrency Patterns](../../04-concurrency/concurrency-patterns.md)

**Practice next**

- [Design Rate Limiter](../01-core-problems/02-design-rate-limiter.md)
- [Design Digital Wallet](40-design-digital-wallet.md)

Both reuse the atomic-fund-locking and event-triggered-execution shape.

**Frameworks**: [LLD Template](../../../07-interview-templates/01-frameworks/02-lld-template.md) · [UML Diagrams](../../01-oop-fundamentals/uml-diagrams.md) · [OOP Four Pillars](../../01-oop-fundamentals/four-pillars.md)
