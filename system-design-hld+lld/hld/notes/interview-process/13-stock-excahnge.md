# # 13 stock excahnge

Below is a complete, time-boxed, interview-ready (1 hour) answer you can deliver for designing a Stock Exchange. Follow the minute markers to pace your explanation. It covers scope, functional & non-functional requirements, APIs & data model, high-level architecture, deep dives (matching engine, market data, risk controls, clearing/settlement), capacity & sizing, ops/security/compliance, trade-offs and a short evolution path.

***

## 0 – 5 min — Problem recap, scope & assumptions

<br>

Start by restating and aligning scope.

<br>

Goal: Build an electronic stock exchange that accepts orders from participants, matches buy/sell orders according to rules, publishes market data (order book & trades) in real time, enforces risk & compliance, persists audit trails, and interfaces with clearing & settlement systems.

<br>

Scope (what we will build):

* Central limit order book (CLOB) per instrument with price-time priority.
* Order types: limit, market, IOC, FOK, GTC, cancel/replace.
* Market data feeds (top-of-book and full-depth).
* Pre-trade risk checks and post-trade audit/capture.
* Clearing & settlement integration (T+N, or instantaneous for modern exchanges).
* Co-location & low-latency access, FIX API and binary low-latency APIs.

<br>

Assumptions for capacity planning (example):

* Instruments: 10k tickers.
* Peak order ingress: 200k orders/sec (configurable).
* Peak market data subscribers: 100k connections, top-of-book updates ≈ 1M updates/sec.
* Latency targets: matching engine latency P99 < 1 ms (internal), market data P99 < 1 ms to co-located clients; API accept P95 < 5 ms.

***

## 5 – 15 min — Functional Requirements (FR)

<br>

Group by priority and what the system must do.

<br>

Core Must-haves

1. Order entry & validation — accept orders via FIX/Proprietary binary, validate syntax, authenticate participants, check trading permissions.
2. Order matching — maintain CLOB per instrument, match orders using defined rules (price-time priority), produce trade prints.
3. Market data dissemination — publish top-of-book, full order book (optional), trade ticks, and snapshots with low latency.
4. Order lifecycle management — ACK, partial fills, cancels, replaces, expirations, order states persisted.
5. Pre-trade risk controls — limit per order size, per-day exposure, credit checks, kill switches.
6. Post-trade processing — trade reports to counterparties, send trades to clearinghouse, create trade audit trail.
7. Surveillance & audit — record all messages, detect anomalies (spoofing, layering), regulatory reporting.
8. Market mechanisms — continuous trading, opening/closing auctions, halts, circuit breakers.

<br>

Should / Nice

* Complex order types (iceberg, pegged, VWAP).
* Cross-matching, market-making incentives & rebates.
* Historical data API for ticks & OHLC.

***

## 15 – 25 min — Non-Functional Requirements (NFR)

<br>

Quality attributes to design for.

<br>

Performance & Latency

* Matching latency (internal): P99 < 1 ms.
* Market data latency to co-located participants: P99 < 1 ms.
* Order acceptance P95 < 5 ms.

<br>

Throughput & Scalability

* Handle peak 200k orders/sec and bursts higher; scale horizontally by instrument partitioning.

<br>

Availability & Reliability

* SLA: 99.99% (or as required by market).
* High availability across AZs / active/passive DR region for disaster recovery.

<br>

Correctness & Consistency

* Deterministic, auditable matching results.
* Durable, immutable audit trails for all messages and trades.

<br>

Security & Compliance

* Strong authentication (certificates), encryption, per-participant access control, surveillance, and regulatory reporting retention.

<br>

Operational

* Fast recovery from failure, deterministic replay for reconstruction, and safe failover with minimal data loss.

***

## 25 – 30 min — APIs & Data Model (external contract)

<br>

Quickly show what participants use.

<br>

APIs

* Order Entry (FIX 4.4 / binary low-latency):
  * NewOrderSingle (price, qty, side, type, timeInForce) → OrderAck(order\_id) or Reject(reason).
  * OrderCancelRequest / OrderCancelReplaceRequest → CancelAck / ReplaceAck.
* Market Data Subscription:
  * Subscribe to TOP (best bid/ask), FULL (depth levels), TRADES.
* Administrative APIs (admin/coordinator):
  * Suspend instrument, circuit-breaker trigger, participant enable/disable.
* Trade Reports & Clearing:
  * Execution report to counterparties and to clearinghouse (ISO 20022 or FIX Trade Capture).

<br>

Data model (core)

* Order: {order\_id, client\_id, instrument, side, price, qty, remaining\_qty, type, tif, state, ts}
* Trade: {trade\_id, buy\_order\_id, sell\_order\_id, instrument, price, qty, ts}
* Participant Account: {participant\_id, routing\_permissions, risk\_limits, balances (cash/positions)}
* Audit Log: append-only stream of all inbound/outbound messages and internal events with timestamps and sequence numbers.

***

## 30 – 40 min — High-level architecture & components

<br>

Describe logical components and data flows.

```
Clients (FIX / Binary) -> Frontend Gateways -> Ingress Validation Layer
     |                                                      |
     +-> Market Data Subscribers <---------------------------+
     
Ingress -> Order Router -> Matching Engine Shards (per instrument / shard)
                    |                |
                    v                v
             Order Book Store     Market Data Publisher
                    |                |
            Persistent Log (WAL)     Fanout to MD Feed
                    |
            Post-trade Processor -> Clearing & Settlement
                    |
            Surveillance & Audit Store (immutable)
```

Key components

1. Front-end Gateways — authenticate, rate-limit, perform simple risk checks, and translate protocols into internal messages. Co-located with participants when offered.
2. Order Router — routes orders to the correct matching shard (by instrument or partition key). Ensures order sequencing per instrument.
3. Matching Engine (core) — ultra-low latency, in-memory order book per instrument with deterministic matching rules. Emits executions and order book updates.
4. Durable Write-Ahead Log (WAL) — append-only log of all events (orders, cancels, trades). Used for recovery & replay. Persisted to fast durable storage and replicated.
5. Market Data Publisher — subscribes to matching engine updates, performs multicast/fanout to subscribers with protocol adaptation (binary, FIX MD).
6. Post-trade Processor — trade enrichment, trade reporting, accounting entries for clearinghouse, clearing/settlement gateway.
7. Surveillance & Risk — runs analytics, detects manipulation patterns, enforces circuit breakers & halts.
8. Admin & Market Ops — UI for market operators, audit, control, and rules configuration.
9. Clearinghouse Integration — pushes trades to CCP / clearing member, receives settlement instructions.

<br>

Partitioning / Sharding

* Partition by instrument (hash bucket) to scale matching horizontally. Each instrument’s order events must be processed by the same matching instance to preserve ordering.

***

## 40 – 50 min — Deep dives (matching engine, market data, risk, recovery)

<br>

#### Matching Engine internals

* Data structures
  * Price-level book (sorted tree / skiplist) for bids & asks; each price level contains FIFO queue of orders.
  * Hash map from order\_id → order node for O(1) cancels/replaces.
* Matching algorithm
  * On incoming order: check best opposite price; if compatible, match across price levels until order fully filled or no match. Handle partial fills, generate trade events. Price-time priority enforced.
* Order types handling
  * Market orders: consume book until filled or limit (or IOC).
  * Limit orders: insert into book if not fully matched.
  * IOC/FOK: immediate/exclusive semantics applied.
* Atomicity & durability
  * For each matching step, atomically persist order/trade events to WAL before acknowledging (or use synchronous replication to standby).
  * Use in-memory then persist; aim for minimal persist latency (fast SSDs or NVMe + replication).
* Determinism
  * Deterministic matching ensures reproducibility for audits; avoid non-deterministic timers inside match logic.

<br>

#### Market Data & Fanout

* Low-latency fanout: match engine emits incremental updates (order added, trade, cancel). Publisher batches updates and multicasts to subscribers. Use binary protocols with sequence numbers and snapshot/sync mechanisms.
* Top-of-book vs full depth: support different feeds — TOP (L1), Depth (L2), Trades, Snapshots. Offer selective subscriptions per symbol.
* Multicast & unicast: use UDP multicast where supported (low latency), fallback to TCP for WAN clients.

<br>

#### Risk, surveillance & market controls

* Pre-trade risk: per-order checks — size limits, price collars, credit checks (pre-funding or credit lines), kill switches. Enforced at gateway or router.
* Circuit breakers & halts: instrument & market level thresholds; market ops can trigger halts.
* Surveillance: streaming analytics detect spoofing, layering, wash trades; flagged events feed compliance team. Keep immutable logs for investigation.

<br>

#### Recovery & replay

* WAL + snapshots: persist WAL of events and periodic snapshots of order books. On failure, replay WAL from snapshot to recover deterministic state.
* Active/passive failover: leader-follower matching engine replication; use consensus for leader election for shards (or active-active with careful determinism).
* Time synchronization: use PTP/NTP for timestamping; sequence numbers used to order events.

<br>

#### Clearing & settlement

* Trade reporting: real-time trade reports to counterparties & regulators.
* Clearing interface: send trades to CCP / clearing members with necessary settlement metadata.
* Settlement/Netting: clearinghouse handles netting; exchange must provide accurate trade files & timestamps.
* Fails & breaks: reconciliation system to detect mismatches, manual intervention tools for breaks.

***

## 50 – 55 min — Capacity planning & example numbers

<br>

Use the assumptions to show how you’d size components.

<br>

Ingress

* Peak orders: 200k/sec. If each order message ≈ 200 bytes → \~40 MB/s ingress. With replication & overhead, \~120 MB/s internal.

<br>

Matching shards

* If one matching instance handles \~5k orders/sec (depending on hardware & logic), need \~40 shards (200k/5k). Partition instruments across shards; hot symbols can be isolated to dedicated shards.

<br>

WAL storage

* WAL write volume: 200k ops/sec × 200 B ≈ 40 MB/s → \~3.5 TB/day. Use fast provisioned SSDs with replication to standby nodes.

<br>

Market data fanout

* If TOP updates ≈ 1M updates/sec and each update 100 B → 100 MB/s outbound to subscribers; multicast preferred to reduce duplication. Unicast to WAN clients multiplies bandwidth.

<br>

Latency

* Use kernel bypass, userland networking, and optimized in-memory data structures to meet sub-1ms matching. Co-location and network tuning required.

<br>

Hardware

* Matching instances on bare-metal with high-core CPUs, low-latency NICs (RDMA/10–100Gbps), NVMe for WAL. Frontends and publishers on redundant clusters.

***

## 55 – 58 min — Operations, security & compliance

<br>

Security

* Mutual TLS, client certs for FIX, per-participant authentication & authorization, strict network segmentation, HSM for signing records if required.

<br>

Compliance & audit

* Immutable audit logs, retention per regulator, regulatory feeds (real-time trade reports), timestamp accuracy, trade surveillance exports.

<br>

Monitoring & SLOs

* Monitor latencies (ingest → match → publish), WAL lag, replication health, subscriber drop rate, risk limit violations. Alert on anomalies, order backlog, or reconciliation breaks.

<br>

Disaster recovery

* DR region with replicated WAL & snapshots. Failover plan with minimal manual steps, tested periodically.

***

## 58 – 60 min — Trade-offs, evolution & summary

<br>

#### Major trade-offs

* Latency vs durability: synchronous persistence increases latency; can use sync to local NVMe + async replication to standby to balance.
* Simplicity (single process per instrument) vs scalability: per-instrument single shard is easiest for deterministic matching; partitioning is required for scale and hot symbols.
* Active/active vs active/passive: active/active reduces failover time but complicates determinism and reconciliation—many exchanges opt for active/passive with fast failover.
* Public multicast vs unicast feeds: multicast is low-latency and bandwidth efficient but requires network support/co-location.

<br>

#### Evolution path

1. MVP: single region, per-instrument matching, FIX API, TOP & trade feed, WAL + snapshot recovery, basic pre-trade risk.
2. Scale: shard instruments, introduce dedicated hot symbol shards, improve market-data fanout (multicast), richer order types.
3. Resilience & features: active/passive region failover, advanced surveillance, co-location services, advanced market mechanisms (dark pools, mid-point matching).

<br>

#### One-sentence summary

<br>

Build an exchange as a set of sharded, deterministic matching engines (one shard per instrument or bucket) backed by a durable WAL + snapshots for recovery, a low-latency market data fanout, strong pre-trade risk and surveillance, and robust clearing/settlement plumbing — optimized for microsecond matching, reproducible outcomes and regulatory auditability.

***

If you want, I can now:

* produce a 5-slide interview deck (one slide per major time block), or
* draw a detailed sequence diagram for Order→Match→Trade→Clearing flow, or
* run capacity recalculation with your expected orders/sec, average message size, and number of instruments.

<br>
