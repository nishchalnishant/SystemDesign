# Two-Phase Commit (2PC)

> A distributed coordination protocol that guarantees atomic commit across multiple databases — all nodes commit or all roll back, with no partial success.

---

## Pattern Mindmap

```
Two-Phase Commit (2PC)
├── Why It Exists
│   ├── Transfer $100: debit A, credit B — must be atomic across 2 DBs
│   ├── Partial failure: debit succeeded, credit DB crashed → money lost
│   └── Need: all-or-nothing across participants without shared storage
├── Phase 1: Prepare (Voting)
│   ├── Coordinator → "Can you commit?" → all participants
│   ├── Each participant: write to WAL, acquire locks, vote YES/NO
│   └── If any votes NO → coordinator sends ABORT to all
├── Phase 2: Commit (Decision)
│   ├── All YES → coordinator writes COMMIT to own WAL → sends COMMIT
│   ├── Each participant: apply changes, release locks, ACK coordinator
│   └── Coordinator done when all ACKs received (or timeout → retry)
├── The Blocking Problem
│   ├── Coordinator crashes AFTER phase 1 but BEFORE phase 2
│   ├── Participants: locked and waiting — cannot commit or abort alone
│   ├── Blocking window: until coordinator recovers
│   └── 3PC: adds pre-commit phase to reduce (not eliminate) blocking
├── Failure Modes
│   ├── Participant dies before PREPARE → coordinator sends ABORT
│   ├── Participant dies after YES vote → must recover and commit
│   ├── Coordinator dies before COMMIT → participants block on locks
│   └── Network partition → coordinator cannot reach participants
├── Real-World Usage
│   ├── XA transactions → Java EE, JDBC 2PC (javax.transaction.xa)
│   ├── MySQL distributed commits → binlog + InnoDB XA
│   ├── PostgreSQL + fdw → postgres_fdw uses 2PC for distributed writes
│   └── Message queue + DB → JMS + XA for exactly-once messaging
├── Alternatives
│   ├── Saga pattern → compensating transactions, no global lock
│   ├── CRDT → conflict-free replicated data (eventual consistency)
│   └── Google Spanner → TrueTime + Paxos (distributed 2PC at Google scale)
└── When To Use
    ├── Use 2PC: strong consistency required, low latency tolerable, few participants
    └── Avoid 2PC: high availability required, many participants, long transactions
```

---

## 1. Why Two-Phase Commit Exists

**Question**: You're building a payment system. Debit account A lives in DB-1. Credit account B lives in DB-2. The debit succeeds. Then DB-2 crashes before the credit commits. How do you ensure atomicity?

**Physical constraint**: There is no way for two independent processes to simultaneously commit or abort a decision without a coordination round. The laws of distributed systems (FLP impossibility) guarantee that consensus takes at least two message rounds in an asynchronous network. You cannot do it in one round — one party must communicate their decision to the other before acting.

**Minimal solution**: Write to DB-1, then DB-2 sequentially. If DB-2 fails mid-write: compensate by reversing DB-1. Breaks when: compensation itself can fail; recovery logic is complex and error-prone; no guarantee compensation runs before another operation reads the partial state.

**Production generalization**: A coordinator orchestrates two phases. Phase 1 asks all participants if they *can* commit (they prepare and lock). Phase 2, based on unanimous YES votes, tells all to commit. This guarantees that if the coordinator crashes after deciding, participants can always determine the correct outcome from the coordinator's WAL on recovery.

---

## 2. Core Concepts

### 2.1 Protocol Flow (Happy Path)

```
Coordinator          Participant-A          Participant-B
     │                    │                      │
     │── PREPARE ────────►│                      │
     │── PREPARE ─────────────────────────────── │
     │                    │                      │
     │                    │  (write WAL, lock)   │  (write WAL, lock)
     │                    │                      │
     │◄── VOTE YES ───────│                      │
     │◄── VOTE YES ────────────────────────────  │
     │                    │                      │
     │  (write COMMIT      │                      │
     │   to own WAL)       │                      │
     │                    │                      │
     │── COMMIT ──────────►│                      │
     │── COMMIT ───────────────────────────────  │
     │                    │                      │
     │                    │  (apply, unlock,     │  (apply, unlock,
     │                    │   write ACK to WAL)  │   write ACK to WAL)
     │                    │                      │
     │◄── ACK ────────────│                      │
     │◄── ACK ─────────────────────────────────  │
     │                    │                      │
  (done)               (done)                 (done)
```

### 2.2 Protocol Flow (Failure: Participant Votes NO)

```
Coordinator          Participant-A (YES)    Participant-B (NO — lock conflict)
     │◄── VOTE YES ───────│                      │
     │◄── VOTE NO ─────────────────────────────  │
     │                                           │
     │── ABORT ───────────►│                      │
     │── ABORT ────────────────────────────────  │
     │                    │                      │
     │                    │  (roll back, unlock) │  (roll back, unlock)
```

### 2.3 WAL (Write-Ahead Log) Role

The coordinator's WAL is the durability anchor:

| WAL State | Coordinator Recovery Action |
|-----------|----------------------------|
| No COMMIT in WAL | Coordinator died before deciding → send ABORT to all |
| COMMIT in WAL, some ACKs missing | Send COMMIT again to non-ACK'd participants |
| All ACKs logged | Transaction complete |

Participants also use WAL:
- `PREPARED` in WAL but no `COMMITTED`: wait for coordinator to tell them what to do
- `COMMITTED` in WAL: apply and ACK (idempotent)

### 2.4 The Blocking Problem

**Scenario**: Coordinator writes COMMIT to its WAL, then crashes before sending COMMIT to participants.

```
Coordinator: [COMMIT written to WAL] ← CRASHES HERE
Participant-A: PREPARED, holding locks, waiting...
Participant-B: PREPARED, holding locks, waiting...
```

- Participants cannot commit on their own (maybe coordinator decided ABORT)
- Participants cannot abort on their own (maybe coordinator decided COMMIT)
- **Participants are blocked, holding locks, until coordinator recovers**
- Blocking duration = coordinator MTTR (minutes to hours)

This is 2PC's fundamental flaw: it's a **blocking protocol**.

### 2.5 Three-Phase Commit (3PC)

3PC adds a `PRE-COMMIT` phase to allow participants to infer the coordinator's decision:

```
Phase 1: PREPARE → vote YES/NO
Phase 2: PRE-COMMIT → all YES received; tell participants "I'm about to commit"
Phase 3: COMMIT → actually commit
```

If participants receive PRE-COMMIT but coordinator dies → participants know coordinator decided YES → they can safely commit among themselves.

**Catch**: 3PC is non-blocking only under no network partitions. Under network partition, 3PC can still lead to split-brain (some participants commit, others abort). Not widely used in practice.

### 2.6 XA Transactions (Java EE)

XA is the standard interface for 2PC across heterogeneous resource managers (databases, message queues):

```
javax.transaction.xa.XAResource — interface each participant implements
javax.transaction.TransactionManager — the coordinator
```

XA flow:
1. `tm.begin()` — start global transaction
2. `xa.start(xid, ...)` — enlist participant
3. Business logic on participant
4. `xa.end(xid, ...)` — suspend participant
5. `xa.prepare(xid)` — Phase 1 (returns XA_OK or XA_RDONLY)
6. `xa.commit(xid, false)` — Phase 2

---

## 3. Architecture

```
┌─────────────────────────────────────────────────────┐
│                  Application Layer                  │
│  initiates distributed transaction                  │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│                  Coordinator                        │
│  ├── Assigns global Transaction ID (XID)            │
│  ├── Maintains participant list                     │
│  ├── Drives Phase 1: send PREPARE to all            │
│  ├── Collects votes; decides COMMIT or ABORT        │
│  ├── Writes decision to WAL before sending          │
│  └── Drives Phase 2: send decision; collect ACKs   │
└──────────────┬──────────────────┬───────────────────┘
               │ PREPARE/COMMIT   │ PREPARE/COMMIT
               ▼                  ▼
┌──────────────────┐   ┌──────────────────────────┐
│  Participant-A   │   │      Participant-B        │
│  (DB-1 / MySQL)  │   │  (DB-2 / PostgreSQL)      │
│                  │   │                           │
│  XAResource impl │   │  XAResource impl          │
│  WAL: PREPARED   │   │  WAL: PREPARED            │
│  holds row locks │   │  holds row locks          │
└──────────────────┘   └──────────────────────────┘
```

---

## 4. Real-World Usage

| System | 2PC Usage |
|--------|-----------|
| MySQL XA | `XA START 'xid'` / `XA PREPARE` / `XA COMMIT` native support |
| PostgreSQL | `PREPARE TRANSACTION 'name'` / `COMMIT PREPARED` — manual 2PC |
| Java EE / Jakarta EE | `javax.transaction.UserTransaction` over XA datasources |
| ActiveMQ + JDBC | XA over JMS + DB for exactly-once message processing |
| Google Spanner | Distributed 2PC using TrueTime + Paxos per shard |
| CockroachDB | Parallel commits optimization (2PC variant without blocking) |

---

## 5. Trade-offs

| Aspect | 2PC | Saga (Alternative) |
|--------|-----|--------------------|
| Consistency | Strong (ACID) | Eventual |
| Availability | Reduced (blocking on coordinator failure) | High |
| Latency | 2 network round trips minimum | Multiple async steps |
| Lock duration | Held across both phases | No cross-service locks |
| Failure handling | Coordinator WAL recovery | Compensating transactions |
| Suitable for | Financial transfers, inventory deductions | Long workflows, microservices |

---

## 6. Failure Scenarios

### 6.1 Coordinator Crashes After COMMIT Written to WAL
**Recovery**: Coordinator restarts, reads WAL, sees COMMIT decision, re-sends COMMIT to any participants that didn't ACK.
**Risk**: If coordinator never recovers (disk failure), participants are blocked indefinitely.
**Mitigation**: Replicate coordinator WAL to standby; use a replicated coordinator (Paxos-based).

### 6.2 Participant Crashes After Voting YES
**Recovery**: Participant restarts, reads WAL (sees PREPARED), asks coordinator for decision.
**Coordinator behavior**: If in Phase 2 → re-send COMMIT. If still in Phase 1 → send ABORT.

### 6.3 Network Partition Between Coordinator and One Participant
**Symptom**: Coordinator times out waiting for VOTE → sends ABORT to all. Partitioned participant eventually reconnects, coordinator tells it ABORT.
**Risk**: Partition during Phase 2 — coordinator sent COMMIT to A, cannot reach B. Coordinator retries B until success (B holds locks, cannot serve other requests).

### 6.4 Heuristic Decisions (Last Resort)
If a participant is blocked for too long, a DBA can manually issue a **heuristic commit or rollback**. This is called a "heuristic decision" and can cause inconsistency if it conflicts with coordinator's decision. Must be logged and reconciled manually.

---

## 7. Performance Numbers

| Metric | Value | Notes |
|--------|-------|-------|
| Network round trips | 2 minimum | Prepare + Commit |
| Lock hold duration | Full 2-phase duration | Latency-sensitive |
| Typical 2PC latency (LAN) | 10–50ms | 2 RTT × network latency |
| Typical 2PC latency (WAN) | 200ms–1s | Cross-region = unusable |
| Throughput impact | ~30–50% vs local | Lock contention, 2× RTT |
| Max participants practical | <10 | Latency grows linearly |
| Google Spanner 2PC | ~14ms global | TrueTime enables tight windows |

---

## 8. Java Implementation

### 8.1 Coordinator (Simplified)

```java
public class TwoPhaseCommitCoordinator {
    private final List<Participant> participants;
    private final TransactionLog txLog;

    public boolean execute(String xid, List<DatabaseOperation> ops) {
        // Enlist participants
        for (int i = 0; i < participants.size(); i++) {
            participants.get(i).enlist(xid, ops.get(i));
        }

        // Phase 1: Prepare
        txLog.write(xid, "PREPARING");
        boolean allPrepared = participants.stream()
            .map(p -> {
                try { return p.prepare(xid); }
                catch (Exception e) { return false; }
            })
            .allMatch(Boolean::booleanValue);

        if (!allPrepared) {
            txLog.write(xid, "ABORTING");
            participants.forEach(p -> {
                try { p.abort(xid); } catch (Exception ignored) {}
            });
            return false;
        }

        // Write COMMIT to WAL BEFORE sending to participants
        // (crash after this → recovery will re-send COMMIT)
        txLog.write(xid, "COMMIT");
        txLog.flush(); // fsync — must be durable

        // Phase 2: Commit
        for (Participant p : participants) {
            boolean acked = false;
            while (!acked) { // retry until acked (participant must be idempotent)
                try {
                    p.commit(xid);
                    acked = true;
                } catch (Exception e) {
                    log.warn("Commit failed for participant, retrying: {}", e.getMessage());
                    sleep(100);
                }
            }
        }

        txLog.write(xid, "DONE");
        return true;
    }
}
```

### 8.2 Participant (Database Adapter)

```java
public class DatabaseParticipant implements Participant {
    private final DataSource ds;
    // xid → connection (held open between prepare and commit)
    private final ConcurrentHashMap<String, Connection> activeConnections = new ConcurrentHashMap<>();

    @Override
    public boolean prepare(String xid) throws SQLException {
        Connection conn = ds.getConnection();
        conn.setAutoCommit(false);
        activeConnections.put(xid, conn);

        // Execute the operation
        executeOperation(conn, pendingOps.get(xid));

        // PostgreSQL: PREPARE TRANSACTION holds locks until COMMIT/ROLLBACK PREPARED
        conn.prepareStatement("PREPARE TRANSACTION '" + xid + "'").execute();
        // Do NOT close connection — locks held by prepared transaction
        return true;
    }

    @Override
    public void commit(String xid) throws SQLException {
        // Idempotent: safe to call multiple times
        try (Connection conn = ds.getConnection()) {
            conn.prepareStatement("COMMIT PREPARED '" + xid + "'").execute();
        }
        activeConnections.remove(xid);
    }

    @Override
    public void abort(String xid) throws SQLException {
        try (Connection conn = ds.getConnection()) {
            conn.prepareStatement("ROLLBACK PREPARED '" + xid + "'").execute();
        }
        activeConnections.remove(xid);
    }
}
```

### 8.3 Recovery on Coordinator Restart

```java
@PostConstruct
public void recoverInFlightTransactions() {
    List<TxLogEntry> incomplete = txLog.findByStatus("COMMIT"); // COMMIT written, not DONE
    for (TxLogEntry entry : incomplete) {
        log.info("Recovering transaction: {}", entry.getXid());
        // Resend COMMIT to all participants — they handle it idempotently
        participants.forEach(p -> {
            try { p.commit(entry.getXid()); }
            catch (Exception e) {
                log.error("Failed to recover commit for participant, will retry", e);
                retryQueue.add(entry); // schedule retry
            }
        });
    }
}
```

---

## 9. Quick Revision

- **Phase 1 (Prepare)**: coordinator asks all participants to lock and vote YES/NO; participants write PREPARED to WAL
- **Phase 2 (Commit)**: if all YES → coordinator writes COMMIT to WAL → sends COMMIT; if any NO → ABORT
- **Blocking problem**: if coordinator crashes after writing COMMIT but before sending → participants are stuck holding locks
- **WAL is the recovery anchor**: on restart, coordinator re-reads WAL and re-sends COMMIT to participants that didn't ACK
- **XA**: standard Java/JDBC interface for 2PC across heterogeneous resource managers
- **3PC**: adds PRE-COMMIT to reduce blocking, but fails under network partition — rarely used
- **Saga over 2PC**: prefer Saga for microservices; 2PC only when strong ACID across DBs is mandatory
- **Never use 2PC cross-region**: WAN latency makes lock hold duration unacceptable

---

## 10. See Also

- `09-patterns/saga-pattern.md` — alternative to 2PC using compensating transactions
- `09-patterns/outbox-pattern.md` — single-DB atomicity for event publishing
- `04-advanced-topics/distributed-concepts.md` — FLP impossibility, consensus
- `01-foundations/databases.md` — ACID, WAL, isolation levels

---

## 11. Interview Questions Asked

1. **Google**: "Walk me through Two-Phase Commit. What happens if the coordinator crashes between Phase 1 and Phase 2?"
2. **Amazon**: "Why is 2PC considered a blocking protocol? What are the practical implications?"
3. **Stripe**: "You need atomicity across two databases for a payment transfer. Would you use 2PC or Saga? When would each apply?"
4. **Meta**: "How does XA work in Java? What is the role of TransactionManager?"
5. **Microsoft**: "A participant voted YES in Phase 1 and then crashes. How does it recover?"
6. **Netflix**: "Why is 2PC impractical for cross-region transactions?"
7. **Uber**: "How does Google Spanner implement distributed transactions at global scale?"
8. **Dropbox**: "What is a heuristic decision in 2PC and why is it dangerous?"
