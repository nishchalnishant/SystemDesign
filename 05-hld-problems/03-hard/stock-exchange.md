# Design a Stock Exchange

> **Difficulty**: Hard
> **Topics**: Order Book, Matching Engine, LMAX Disruptor, Tick Data Storage, Low-Latency Architecture
> **Time**: 75 minutes
> **Companies**: NYSE, NASDAQ, Citadel, Jane Street, Goldman Sachs, Robinhood, Zerodha

---

## Problem Statement

Design a stock exchange that:
- Accepts buy/sell orders from traders
- Matches orders using price-time priority (FIFO within price level)
- Executes trades and publishes market data (order book, last price, volume)
- Handles 1M+ orders/second at microsecond latency
- Ensures zero data loss — every order and trade is durably persisted
- Supports multiple order types: market, limit, stop-loss, iceberg

---

## Analogy

An auction house running thousands of auctions simultaneously. For each item (stock ticker), there's a buyer's board (bid side) and a seller's board (ask side). The auctioneer's single job is to match the highest buyer with the lowest seller — whenever a match exists, a trade executes. The critical rule: first, the best price gets priority. If two buyers want at the same price, whoever placed their order first wins.

At exchange scale: this simple matching logic runs at 1 million "auctions" per second across thousands of tickers. Latency from order submission to execution acknowledgment must be under 1 millisecond — a microsecond advantage can be worth millions to algorithmic traders.

---

## Why This Is Hard

1. **Throughput vs latency paradox**: 1M orders/sec is high throughput. Sub-millisecond latency is ultra-low. Standard databases (PostgreSQL → ~1K writes/sec, 10ms latency) cannot handle either requirement. You need purpose-built in-memory order books with lock-free data structures.
2. **Strict ordering guarantees**: Price-time priority means order of arrival matters down to the nanosecond. Any network or processing jitter can unfairly favor one participant over another. The matching engine must process orders in a strict, deterministic sequence.
3. **Durability vs speed**: For every order processed, a durable record must be written (regulation requires it). But disk writes take milliseconds. The LMAX Disruptor pattern solves this with a ring buffer + write-ahead log that achieves both.
4. **Market data dissemination**: Every trade must be broadcast to millions of subscribers (price feeds) within microseconds. Fan-out to millions at this frequency requires multicast UDP, not TCP.
5. **Circuit breakers and market halts**: If a stock moves 5% in 5 minutes, trading must pause within milliseconds — a system-wide emergency stop that must be applied before any further trades execute on that ticker.

---

## Critical Requirements

### Functional
- Place, cancel, and modify orders (market, limit, stop, iceberg)
- Price-time priority order matching
- Real-time market data: order book depth, last trade price, OHLCV
- Order status notifications (acknowledged, filled, rejected, cancelled)
- Position and balance management per account
- Market circuit breakers (halt trading on abnormal price moves)

### Non-Functional
- **Order processing latency**: < 1ms P99 (from receipt to execution/rejection)
- **Throughput**: 1M+ orders/sec
- **Durability**: Zero trade loss (audit trail required by regulators)
- **Fairness**: Price-time priority strictly enforced
- **Availability**: 99.999% during market hours (4.5 min downtime/year)

---

## Scale Estimation

```
Tickers: 10,000 actively traded stocks
Orders/sec: 1M at peak (options expiration, news events)
Trades/sec: ~100K (most orders don't match immediately)
Market data events/sec: 5M (order book changes per second)

Order book memory:
  Per ticker: 1,000 price levels × 10 orders/level × 200 bytes = 2MB
  10,000 tickers: 20GB in-memory (fits on one large server)

Tick data storage:
  100K trades/sec × 200 bytes × 86,400 seconds = 1.7 TB/day
  6 months retention (regulatory): ~300 TB

Market data subscribers:
  500K concurrent market data connections (traders, data vendors)
```

---

## Core Concepts

### 1. Order Book

```
For each ticker, maintain two sorted lists:

Bid side (buyers) — sorted by price DESCENDING, then time ASCENDING:
  Price $150.10 → [order_a (100 shares, 09:31:00.001), order_b (50 shares, 09:31:00.002)]
  Price $150.05 → [order_c (200 shares, 09:30:59.500)]
  Price $150.00 → [...]

Ask side (sellers) — sorted by price ASCENDING, then time ASCENDING:
  Price $150.11 → [order_d (75 shares, 09:31:00.005)]
  Price $150.15 → [order_e (150 shares, 09:30:58.000)]
  Price $150.20 → [...]

Spread = Best Ask - Best Bid = $150.11 - $150.10 = $0.01

When a new BUY order arrives at $150.11:
  Matches with order_d (ask at $150.11) — trade executes at $150.11
  If qty_buy = 75, qty_sell = 75 → both fully filled, both removed from book
  If qty_buy = 100, qty_sell = 75 → order_d fully filled; remaining 25 remain as buy limit

Data structure:
  Price level: TreeMap<Price, Queue<Order>>
  TreeMap: O(log N) to find best bid/ask price level
  Queue: O(1) FIFO to get first order at that price level
```

### 2. Matching Engine

```
Sequential processing is the key design decision:
  ALL orders for a given ticker processed by a SINGLE thread
  No locks needed (single-threaded) → microsecond latency
  All state for a ticker (order book) lives in one thread's memory

Matching algorithm:
  1. Receive new order (from order gateway)
  2. If MARKET order: match against best available price until filled
  3. If LIMIT order:
     a. Check if immediately matchable (limit buy ≥ best ask, limit sell ≤ best bid)
     b. If matchable: execute trade(s), partially or fully
     c. Remaining quantity (if any): insert into order book at limit price
  4. Publish: trade event + updated order book depth

Order types:
  Market: Execute immediately at best available price (no limit)
  Limit: Execute at specified price or better; rest rests in book
  Stop-loss: Becomes market order when price reaches trigger
  Iceberg: Shows only part of quantity (e.g. 100 visible, 900 hidden)
           When visible portion fills, refresh from hidden portion
  Fill-or-kill (FOK): Execute entire order immediately or cancel entirely
  Immediate-or-cancel (IOC): Execute what's available, cancel remainder
```

### 3. LMAX Disruptor Pattern

```
Problem: How do you achieve 1M orders/sec with durable logging?

Traditional approach: Order arrives → write to DB → match → write trade to DB
Bottleneck: DB writes are 10ms. At 1M/sec, you need 10,000 DB servers. Unworkable.

LMAX Disruptor solution:
  1. Ring buffer (pre-allocated, fixed-size, in memory)
     - All orders written to next slot in ring buffer (nanosecond operation)
     - Multiple consumers read the same ring buffer independently

  2. Consumers process in parallel on same ring buffer slots:
     Consumer A: Matching engine (updates order book)
     Consumer B: Journal writer (appends to WAL on fast SSD)
     Consumer C: Market data publisher (multicast to subscribers)

  3. Each consumer tracks its own sequence number
     - Consumers never block each other (they read the same immutable slots)
     - Disruptor only allows overwriting a slot when ALL consumers have processed it

  Throughput: 6 million events/sec (LMAX published benchmark)
  Latency: < 1 microsecond through the ring buffer

Ring buffer size:
  1M orders/sec × 10ms desired buffer = 10,000 slots minimum
  Use 2^17 = 131,072 slots (must be power of 2 for fast modulo)
```

### 4. Market Data Dissemination

```
Problem: 500K subscribers × 5M events/sec → multicast, not unicast

Multicast UDP:
  One packet sent → network infrastructure replicates to all subscribers
  Subscribers join a multicast group (IP multicast)
  UDP: no connection overhead, but packets may be lost

Sequencing + gap fill:
  Every market data packet carries a sequence number
  If subscriber receives seq=1001, then seq=1003: gap detected (1002 missing)
  Subscriber requests seq=1002 from "retransmission server" (unicast TCP fallback)

Feed types:
  Level 1: Best bid/ask price only (lower bandwidth, for retail)
  Level 2: Full order book depth (5, 10, 20 levels) (for professional traders)
  Level 3: Every order event (for market makers, high-frequency traders)

Protocol:
  FAST (FIX Adapted for Streaming): binary encoding, minimal overhead
  Or proprietary binary protocols (each exchange has their own)
  FIX (Financial Information eXchange): text-based, used for order submission
```

---

## Architecture

```
               TRADER CLIENT
                     │
                     │ FIX protocol (TCP) or REST API
                     ▼
          ┌─────────────────────┐
          │   Order Gateway     │
          │ - Auth + validation │
          │ - Risk pre-check    │  ← Pre-trade risk: reject if balance < order size
          │ - Sequence stamp    │
          └──────────┬──────────┘
                     │
                     ▼
          ┌─────────────────────┐
          │   LMAX Ring Buffer  │  ← Lock-free, in-memory
          │   (Orders queued)   │
          └──┬──────────────────┘
             │
    ┌─────────┼──────────────┐
    ▼         ▼              ▼
┌────────┐ ┌────────┐ ┌────────────────┐
│Matching│ │Journal │ │Market Data Pub │
│Engine  │ │Writer  │ │(Multicast UDP) │
│(Single │ │(WAL on │ │500K subscribers│
│thread  │ │NVMe)   │ └────────────────┘
│per tick│ └────────┘
└───┬────┘
    │
    ▼
┌────────────────┐
│ In-Memory      │
│ Order Book     │  ← One per ticker, one thread owns it
│ (TreeMap +     │
│  Queue)        │
└───┬────────────┘
    │ trade events
    ▼
┌────────────────┐
│ Trade DB       │  ← TimescaleDB / ClickHouse for tick data
│ (durable store │
│  for trades,   │
│  EOD positions)│
└────────────────┘
```

---

## Database Schema

```sql
-- Orders (write to WAL first, async to DB)
CREATE TABLE orders (
    order_id      BIGINT PRIMARY KEY,         -- Snowflake ID (time-ordered)
    account_id    BIGINT NOT NULL,
    ticker        CHAR(10) NOT NULL,
    order_type    ENUM('market','limit','stop','iceberg'),
    side          ENUM('buy', 'sell'),
    quantity      BIGINT NOT NULL,
    limit_price   DECIMAL(18, 4),
    stop_price    DECIMAL(18, 4),
    status        ENUM('new','partial','filled','cancelled','rejected'),
    filled_qty    BIGINT DEFAULT 0,
    avg_fill_price DECIMAL(18, 4),
    submitted_at  TIMESTAMP(6),               -- Microsecond precision
    INDEX idx_account_orders (account_id, submitted_at DESC),
    INDEX idx_ticker_time (ticker, submitted_at DESC)
);

-- Trades (immutable, append-only)
CREATE TABLE trades (
    trade_id      BIGINT PRIMARY KEY,
    ticker        CHAR(10) NOT NULL,
    buy_order_id  BIGINT NOT NULL,
    sell_order_id BIGINT NOT NULL,
    quantity      BIGINT NOT NULL,
    price         DECIMAL(18, 4) NOT NULL,
    executed_at   TIMESTAMP(6),
    INDEX idx_ticker_time (ticker, executed_at)  -- For OHLCV computation
) PARTITION BY RANGE (executed_at);  -- Daily partitions for efficient archival

-- Positions (updated after each trade)
CREATE TABLE positions (
    account_id    BIGINT,
    ticker        CHAR(10),
    quantity      BIGINT,                       -- Net long (+) or short (-)
    avg_cost      DECIMAL(18, 4),
    last_updated  TIMESTAMP(6),
    PRIMARY KEY (account_id, ticker)
);

-- Account balances (strong consistency required)
CREATE TABLE accounts (
    account_id    BIGINT PRIMARY KEY,
    cash_balance  DECIMAL(18, 4) NOT NULL,
    reserved_cash DECIMAL(18, 4) DEFAULT 0,    -- Reserved for open buy orders
    updated_at    TIMESTAMP(6),
    CONSTRAINT positive_balance CHECK (cash_balance >= 0)
);
```

---

## Critical: Pre-Trade Risk Checks

```
Before any order reaches the matching engine, the order gateway must validate:

1. Authentication: Is this a valid account with active trading permissions?
2. Account balance:
   For buy order at limit $150 × 100 shares = $15,000 required
   Check: available_cash = cash_balance - reserved_cash >= $15,000
   If yes: Reserve $15,000 (deduct from available), submit order
   If no: Reject with "Insufficient funds"

3. Position limits:
   Is account already holding max allowed position in this ticker?
   Configurable per account tier (retail: 1000 shares, institutional: unlimited)

4. Order rate limits:
   Max 1,000 orders/min per account (prevent abuse of the system)

5. Symbol validation:
   Is the ticker actively trading? Is market currently open?

These checks run in the order gateway — before the ring buffer.
They are the last line of defense before orders enter the low-latency core.
```

---

## Circuit Breakers

```
Market-wide circuit breaker (Level 1, 2, 3):
  Level 1: Market drops 7% from prior close → 15-minute trading halt
  Level 2: Market drops 13% → Another 15-minute halt
  Level 3: Market drops 20% → Halt for remainder of the day

Individual stock circuit breaker (Limit Up/Limit Down - LULD):
  If stock moves > 5-10% from reference price in 5 minutes:
  → Trading paused for 5 minutes to allow price discovery
  → After 5 min: Either trading resumes or exchanges declare trading halt

Implementation:
  Price monitor thread (separate from matching engine):
    Continuously computes reference price per ticker
    If abs(current_price - reference_price) / reference_price > threshold:
      Publish HALT event to ring buffer
      Matching engine: on receiving HALT for ticker, stop matching, queue incoming orders
      All new orders: queued and acknowledged with "PENDING HALT" status
  
  On halt lift:
    RESUME event published
    Queued orders released in sequence for matching
    Opening auction run if required (batch matching at single price)
```

---

## Failure Scenarios

### Matching Engine Crash

```
Impact: No trades for affected tickers
Recovery:
  1. WAL (journal) on NVMe contains every order since last checkpoint
  2. Standby matching engine (hot standby, processes WAL in real-time)
  3. Failover: standby becomes active within 100ms (health check interval)
  4. Standby replays from WAL to reconstruct exact order book state
  RTO: < 500ms (hot standby replay), RPO: 0 (synchronous WAL)
```

### Ring Buffer Overflow

```
Cause: Matching engine too slow; order gateway filling ring buffer faster than consumed
Action:
  Order gateway applies back pressure: start returning "System busy, retry" errors
  Market data publisher and journal writer are typically much faster than matching
  Root cause is usually a complex order (iceberg, many partial fills)
  Monitor: ring buffer fill percentage; alert at > 80%
```

---

## Interview Talking Points

**Q: "Why single-threaded matching engine? Doesn't that limit throughput?"**
> "Counter-intuitively, single-threaded is faster here. A multi-threaded order book requires locks on every order insertion and cancellation. Lock contention at 1M orders/sec means threads spending most time waiting for each other. A single thread owns the order book with zero locks — all operations are O(log N) tree operations with no synchronization overhead. We scale across tickers by partitioning: Ticker A's order book on one core, Ticker B's on another. 10,000 tickers across 10,000 cores = massively parallel, yet each ticker is single-threaded."

**Q: "How do you handle durability at 1M orders/sec without a database bottleneck?"**
> "Write-ahead log on NVMe SSD, not a database. The journal consumer in our Disruptor ring buffer appends order events sequentially to an NVMe drive. Sequential writes on NVMe achieve 3GB/sec — easily handling 1M orders/sec at 200 bytes each (200MB/sec). Separately, an async process tails the WAL and writes to the trade database (TimescaleDB) for analytical queries. The database is never on the critical path."

**Q: "How do you ensure fairness when two traders submit orders at the same millisecond?"**
> "Fairness is achieved by the order gateway stamping each order with a nanosecond timestamp upon receipt (network packet arrival time, using hardware timestamping). Orders enter the ring buffer in strict arrival order. The matching engine processes them in ring buffer order. So timestamp-ordering is enforced by the hardware + ring buffer — not by software locks or queuing systems that could introduce non-determinism."

---

## Interview Questions Asked

### Jane Street
1. **"Design a matching engine for a stock exchange"** → Probe: order book data structure, price-time priority, throughput, durability. Hint: per-ticker single-threaded matching engine on a price-sorted order book (TreeMap for price levels, FIFO queue per level); Disruptor ring buffer for lock-free inter-component communication; WAL on NVMe for durability at 1M orders/sec.

### Robinhood
1. **"Walk me through your order book architecture — how do you handle market vs limit orders?"** → Probe: matching logic, partial fills, order types. Hint: market order: match against best available price until filled or order book exhausted; limit order: add to book at specified price if not immediately matchable; partial fill: decrement remaining quantity, emit fill event, keep order in book until fully filled or cancelled.

### Common Follow-ups
1. **"How do you guarantee price-time priority under concurrent orders?"** → Single-threaded matching engine owns the order book with zero locks; orders enter via Disruptor ring buffer in strict arrival order (nanosecond hardware timestamps from network card); single thread processes in ring-buffer sequence = price-time priority is deterministic and requires no synchronization.
2. **"What is the FIX protocol and why does it matter?"** → Financial Information eXchange: industry-standard binary/text protocol for order messages (NewOrderSingle, ExecutionReport, CancelRequest); all brokers and market participants speak FIX so exchange doesn't need custom connectors; FIX gateway at exchange entry translates FIX messages into internal ring buffer events.
3. **"How do you distribute market data to thousands of subscribers with microsecond latency?"** → Multicast UDP: matching engine publishes trades/quotes to a multicast group; all subscribers receive simultaneously (no unicast fan-out); kernel bypass (DPDK/RDMA) eliminates OS overhead; sequence numbers detect packet loss; subscribers replay missing packets via separate TCP retransmission channel; target <10µs from match to subscriber.
4. **"How do you handle market open auction and closing auction?"** → Pre-open: collect orders but don't match — build indicative equilibrium price; at open: single price auction matches maximum volume at single clearing price; mid-day: continuous matching; pre-close: collect imbalance orders; closing auction: similar to open but uses closing reference price; matching engine has explicit state machine (PRE_OPEN → OPEN_AUCTION → CONTINUOUS → PRE_CLOSE → CLOSE_AUCTION → CLOSED).
