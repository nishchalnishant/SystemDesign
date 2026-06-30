> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design a stock exchange — matching engine, order book, market data distribution, and microsecond-latency infrastructure for financial trading systems.
>
> **Key design decisions:**
> - Order book: per-symbol price-time priority book; buy orders (bids) sorted descending by price; sell orders (asks) sorted ascending; best bid/ask = NBBO
> - Matching engine: single-threaded per symbol to avoid locks; match buy vs sell when bid ≥ ask; price-time priority (same price → earliest order wins)
> - Order types: market (execute immediately at best price), limit (execute at specified price or better), stop (trigger at price, then market)
> - Data structures: price level → doubly-linked list of orders; price levels in Red-Black tree; O(log N) insert/cancel, O(1) best bid/ask
> - Market data: after every trade → publish trade feed (price, qty, time) + order book delta to all subscribers; fan-out via multicast UDP or pub-sub
> - Latency: co-location (exchange rack), FPGA for market data processing, kernel bypass (DPDK), CPU pinning; target <100μs round-trip
> - Persistence: event sourcing (log every order event); replay log to reconstruct order book; WAL for crash recovery
>
> **Key takeaway:** The matching engine must be single-threaded per symbol — any locking or coordination introduces latency spikes that fairness-sensitive traders exploit; price-time priority is non-negotiable for regulatory compliance.

---
module: 05-hld-problems
topic: Hard
status: unread
tags: [05-hld-problems, system-design, hard, stock-exchange, order-book, matching-engine, market-data]
---
# Design a Stock Exchange

> **Difficulty**: Hard | **Asked at**: Jane Street, Citadel, Goldman Sachs, Amazon, Google

---

## Problem Statement

Design a stock exchange like NYSE or NASDAQ. Traders submit buy and sell orders; the matching engine pairs buyers with sellers based on price-time priority. The system must process thousands of orders per second with microsecond latency, maintain a fair and accurate order book, and publish real-time market data to all participants.

---

## Functional Requirements

1. **Order placement**: Submit market orders (execute immediately) and limit orders (execute at specified price)
2. **Order matching**: Match buy and sell orders by price-time priority (best price first; tie-break by arrival time)
3. **Order cancellation**: Cancel an open limit order before it executes
4. **Market data**: Publish real-time price, volume, and order book depth to all subscribers
5. **Trade confirmation**: Notify buyer and seller of executed trades
6. **Order book**: Show current best bid (highest buy), best ask (lowest sell), and depth

---

## Non-Functional Requirements

- **Throughput**: 500K orders/sec; 1M market data messages/sec
- **Latency**: Order-to-trade: < 100 microseconds (µs) for co-located traders; < 1ms for remote
- **Fairness**: Orders matched in strict price-time priority — no favoritism
- **Durability**: All orders and trades persisted; zero data loss on crash
- **Determinism**: Given the same sequence of orders, the matching engine always produces the same trades

---

## Core Entities

| Entity | Key Fields |
|--------|-----------|
| `Order` | order_id, trader_id, symbol, side (buy/sell), type (market/limit), price, quantity, timestamp, status |
| `Trade` | trade_id, buy_order_id, sell_order_id, symbol, price, quantity, executed_at |
| `OrderBook` | symbol, bids (price-sorted buy orders), asks (price-sorted sell orders) |
| `MarketDataTick` | symbol, bid_price, ask_price, last_price, volume, timestamp |

---

## API Design

```http
POST /api/v1/orders
Body: {
  "trader_id": "t123",
  "symbol": "AAPL",
  "side": "buy",
  "type": "limit",
  "price": 150.00,
  "quantity": 100
}
Response 201: { "order_id": "o456", "status": "open", "timestamp": "2026-06-29T14:30:00.000123Z" }

DELETE /api/v1/orders/{order_id}
Response 200: { "order_id": "o456", "status": "cancelled" }

GET /api/v1/orderbook/{symbol}
Response 200: {
  "bids": [{ "price": 150.00, "quantity": 500 }, { "price": 149.99, "quantity": 1200 }],
  "asks": [{ "price": 150.01, "quantity": 300 }, { "price": 150.02, "quantity": 800 }]
}

# Market data: multicast UDP (low latency) or WebSocket
WebSocket /ws/market-data/{symbol}
→ { "type": "trade", "price": 150.00, "quantity": 100, "timestamp": "..." }
```

---

## High-Level Design

```
Trader (co-located or remote)
  │ FIX protocol (industry standard) or REST for retail
  ▼
Order Gateway
  │ Auth, validate order, assign timestamp (nanosecond precision)
  │ Persist order to WAL (write-ahead log) → durable
  │ Forward to Matching Engine (single-threaded, in-memory)
  ▼
Matching Engine (one per trading symbol)
  │ In-memory order book: sorted buy orders (descending price) + sorted sell orders (ascending price)
  │ Match: if best_bid >= best_ask → trade executes
  │ Output: Trade events → WAL → Market Data Publisher
  │
  ▼
Market Data Publisher
  │ Multicast UDP to all subscribers (low latency)
  │ WebSocket gateway for retail clients
  │
  ▼
Post-Trade
  ├── Clearing: confirm trade, update positions
  ├── Settlement: transfer cash and shares (T+2)
  └── Regulatory reporting: log all trades

Storage:
  WAL: append-only log for all orders and trades (durability)
  PostgreSQL: historical orders, trades (for queries/reports)
  In-memory: live order book per symbol (matching engine state)
```

---

## Deep Dive 1: Order Book Data Structure

**Problem**: The matching engine receives 500K orders/sec. For each incoming order, it must find the best matching counter-orders in microseconds. What data structure backs the order book?

**Price level tree**: Bids and asks are organized into price levels. Each price level contains a FIFO queue of orders at that price.

```python
class OrderBook:
    def __init__(self):
        # Bids: max-heap (highest price first)
        # Asks: min-heap (lowest price first)
        self.bids = SortedDict(neg)  # price → deque of orders; sorted descending
        self.asks = SortedDict()      # price → deque of orders; sorted ascending
        self.order_map = {}           # order_id → (side, price, deque_position)

    def add_limit_order(self, order):
        book = self.bids if order.side == "buy" else self.asks
        if order.price not in book:
            book[order.price] = deque()
        book[order.price].append(order)
        self.order_map[order.order_id] = (order.side, order.price)

    def cancel_order(self, order_id):
        side, price = self.order_map.pop(order_id)
        book = self.bids if side == "buy" else self.asks
        # Mark as cancelled (lazy deletion); remove from deque on next access
        book[price].remove_by_id(order_id)
        if not book[price]:
            del book[price]
```

**Time complexity**:
- Add order: O(log P) where P = number of distinct price levels (typically < 1,000)
- Cancel order: O(1) if order_map → position tracked; O(log P) to clean empty level
- Match: O(log P) to peek best bid/ask

**Why not a heap**: A heap supports O(1) peek but O(N) cancellation. SortedDict (balanced BST) supports O(log N) for all operations, enabling fast cancellation.

---

## Deep Dive 2: Matching Algorithm (Price-Time Priority)

**Problem**: A buy limit order arrives at $150 for 300 shares. There are sell orders at $149.50 (100 shares, arrived at 10:00:01), $149.50 (200 shares, arrived at 10:00:03), and $150.00 (150 shares, arrived at 10:00:05). How does the matching engine fill this order?

**Price-time priority (FIFO)**:
1. Best price first: lowest ask price for buys; highest bid price for sells
2. For same-price orders: earlier arrival time (timestamp) has priority

**Matching loop**:
```python
def match(self, incoming_buy):
    remaining_qty = incoming_buy.quantity
    trades = []
    while remaining_qty > 0 and self.asks:
        best_ask_price, queue = self.asks.peekitem(0)  # lowest ask
        if best_ask_price > incoming_buy.price:
            break  # no match (ask price too high for limit order)
        sell_order = queue[0]  # earliest order at this price
        fill_qty = min(remaining_qty, sell_order.remaining_quantity)
        trades.append(Trade(
            buy_order=incoming_buy, sell_order=sell_order,
            price=sell_order.price,  # price = the resting order's price (maker)
            quantity=fill_qty
        ))
        sell_order.remaining_quantity -= fill_qty
        remaining_qty -= fill_qty
        if sell_order.remaining_quantity == 0:
            queue.popleft()
        if not queue:
            del self.asks[best_ask_price]
    if remaining_qty > 0:
        self.add_limit_order(incoming_buy, remaining_qty)  # rest becomes resting order
    return trades
```

**Result for the example**:
- Fill 100 shares from $149.50 sell (10:00:01) → Trade at $149.50
- Fill 200 shares from $149.50 sell (10:00:03) → Trade at $149.50
- Remaining: 0 shares. Fully filled.

---

## Deep Dive 3: Durability and Recovery

**Problem**: The matching engine is single-threaded, in-memory, and processes 500K orders/sec. If it crashes, how do you recover the exact state of the order book without replaying millions of events?

**Write-Ahead Log (WAL)**:
- Every incoming order and every generated trade is written to the WAL before processing
- WAL is an append-only file on durable storage (NVMe SSD or network storage)
- The matching engine never acknowledges an order to the gateway until the WAL write completes
- WAL write: synchronous fsync (< 5 µs on NVMe) or async with group commit (batch 1,000 writes → one fsync)

**Recovery from WAL**:
1. On crash, replay the WAL from the beginning
2. Re-process every order in sequence → matching engine deterministically reconstructs exact order book state
3. Recovery time: 1M events × 1 µs processing = 1 second to recover 1 second of trading history

**Snapshot + WAL** (for faster recovery at scale):
1. Every N minutes, serialize the entire in-memory order book to a snapshot file
2. On recovery: load snapshot → replay only WAL entries since snapshot
3. Reduces recovery time from "replay full day" to "replay last 5 minutes"

**Hot standby**: Run a replica matching engine that also consumes the WAL in real-time. If primary crashes, replica can take over in < 100ms with zero replay needed.

---

## Interviewer Questions by Level

**Junior**:
- What is an order book? What is a bid and an ask?
- What is price-time priority? Give an example of how two orders at the same price are ordered.
- What is a market order vs a limit order?

**Mid-level**:
- What data structure backs the order book? Why can't you use a simple heap?
- Walk through the matching algorithm — how does a limit buy order fill against the ask side?
- How do you handle order cancellations efficiently?

**Senior**:
- Design the matching engine for 500K orders/sec with < 100µs latency. What architectural choices eliminate latency?
- How do you persist all orders and trades durably without degrading matching latency?
- Design the market data feed — how do you broadcast 1M price updates/sec to thousands of subscribers with minimum latency?
- How do you prevent front-running (a trading firm using your exchange's systems to get an unfair timing advantage)?
