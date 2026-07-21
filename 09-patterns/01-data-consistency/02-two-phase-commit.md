---
module: 09-patterns
status: unread
tags: [09-patterns, system-design, patterns]
---
# Two-Phase Commit (2PC)

> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** A method used to ensure that a transaction involving multiple separate databases either completely succeeds everywhere, or completely fails everywhere (all-or-nothing).
>
> **Key concepts:**
> - **Phase 1 (Prepare):** The "Coordinator" asks all databases, "Are you ready and able to save this data?" Every database locks its data and replies "Yes" or "No".
> - **Phase 2 (Commit/Rollback):** If *all* databases said "Yes", the Coordinator tells them to permanently save (Commit). If *any* database said "No", the Coordinator tells everyone to cancel (Rollback).
> - **The Blocking Problem:** If the Coordinator crashes after Phase 1, the databases are stuck holding their data locked indefinitely until the Coordinator wakes back up.
>
> **Key takeaway:** 2PC provides perfect data consistency, but it is slow and highly vulnerable to getting "stuck." Because of this, modern microservices usually avoid 2PC and use the Saga Pattern instead.

---

## 🤷‍♂️ Why Should I Care?

Imagine you are building a payment app like Venmo. Alice sends Bob $100. 
Alice's account is in **Database A**. Bob's account is in **Database B**.

You write code to do this:
1. Deduct $100 from Alice (Database A).
2. Add $100 to Bob (Database B).

What happens if step 1 succeeds, but right before step 2, Database B crashes? 
Alice lost $100, but Bob never got it. The money vanished into thin air. 

To fix this, you need **Atomicity** — meaning both steps must happen together, or neither happens at all. But because they are in two *different* databases, you can't just use a normal database transaction. 

This is exactly what the **Two-Phase Commit (2PC)** protocol was invented to solve. It guarantees that multiple independent databases will act as one synchronized unit.

---

## 💍 The Wedding Analogy

To understand Two-Phase Commit, think of a traditional wedding ceremony. 

There are three people: The Priest (The Coordinator), the Groom (Database A), and the Bride (Database B).

**Phase 1 (The Prepare Phase):**
The Priest asks the Groom: "Do you take this woman?" The Groom says, "Yes." (He is now *locked in*).
The Priest asks the Bride: "Do you take this man?" The Bride says, "Yes." (She is now *locked in*).

**Phase 2 (The Commit Phase):**
Because both parties voted "Yes", the Priest makes the final decision: "I now pronounce you husband and wife." Both the bride and groom update their status to "Married."

**What if someone says no?**
If the Groom says "Yes", but the Bride says "No," the Priest declares: "The wedding is cancelled!" The Groom goes back to being single (Rollback). No partial marriages are allowed.

**The Fatal Flaw (The Blocking Problem):**
Imagine the Groom says "Yes," and the Bride says "Yes." But right before the Priest can say "I now pronounce you...", the Priest has a heart attack and passes out (The Coordinator crashes). 
What happens to the Bride and Groom? They already said "Yes." They can't leave. They can't marry anyone else. They are physically stuck at the altar waiting for the Priest to wake up and tell them the final result. In database terms, they are holding **locks** on the data, bringing your system to a grinding halt.

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

### 2.3 Why the "WAL" (Write-Ahead Log) is critical

To survive crashes, the Coordinator keeps a diary called the WAL. 

| Coordinator's Diary (WAL) | What happens if Coordinator crashes & restarts? |
|-----------|----------------------------|
| Empty / No "COMMIT" written | It assumes the wedding never finished. It tells everyone to ABORT. |
| "COMMIT" written, but didn't get ACKs | It yells "COMMIT" again to everyone who didn't hear it. |
| "COMMIT" and all ACKs logged | The transaction is fully done. |

### 2.4 The Blocking Problem (The Priest's Heart Attack)

**Scenario**: The Coordinator writes "COMMIT" in its diary, but then loses power *before* it can send the message to the databases.

```
Coordinator: [COMMIT written to WAL] ← CRASHES HERE
Participant-A: PREPARED, holding locks, waiting...
Participant-B: PREPARED, holding locks, waiting...
```

- The databases cannot commit on their own (what if the Coordinator decided to Abort?).
- The databases cannot abort on their own (what if the Coordinator decided to Commit?).
- **Result:** The databases are frozen, locking up rows of data, until the Coordinator is rebooted. This is 2PC's fundamental flaw: it is a **blocking protocol**.

### 2.5 Three-Phase Commit (3PC)

Engineers tried to fix the blocking problem by adding a "Pre-Commit" phase (Phase 1.5). 
If the Coordinator crashes, the databases can talk to each other. If they all received a "Pre-Commit" message, they know it's safe to finish the job themselves. 

**Why no one uses 3PC:** It requires perfect network connections. If the Wi-Fi cuts out between the databases, they might make different decisions (a "split-brain"). It also adds more delay. In the real world, people use the Saga Pattern instead.

---

## 3. Real-World Usage

| System | 2PC Usage |
|--------|-----------|
| MySQL XA | `XA START 'xid'` / `XA PREPARE` / `XA COMMIT` native support |
| PostgreSQL | `PREPARE TRANSACTION 'name'` / `COMMIT PREPARED` — manual 2PC |
| Java EE | `javax.transaction.UserTransaction` over XA datasources |
| Google Spanner | Distributed 2PC using atomic clocks (TrueTime) to make it blazingly fast |

---

## 4. Trade-offs

| Aspect | 2PC | Saga (Alternative) |
|--------|-----|--------------------|
| Consistency | Perfect (ACID) | Eventual (Takes a few seconds to balance) |
| Availability | Low (Can freeze up) | High (Never freezes) |
| Speed | Slow (Multiple network trips) | Fast (Async steps) |
| Best for | Financial transfers where a temporary mismatch is illegal | Modern microservices and long workflows |

---

## 5. Coordinator Crash Timeline — The Interview Masterclass

This is the question senior interviewers ask to test your real depth. The Coordinator can crash at 6 distinct points. How does the system recover?

```
Timeline:  [Phase 1 Prepare] ─────────────── [Phase 1 Complete] ─── [Phase 2 Commit]

Crash Point A: Before sending any Prepare
Crash Point B: After sending Prepare to some (not all) participants
Crash Point C: After receiving all votes, before writing COMMIT to WAL
Crash Point D: After writing COMMIT to WAL, before sending any COMMIT
Crash Point E: After sending COMMIT to some (not all) participants
Crash Point F: After all participants ACK (transaction complete)
```

| Crash Point | What Happens on Coordinator Restart | Is data safe? |
|---|---|---|
| **A** & **B** | Coordinator has no memory of this. Databases time out and abort. | ✅ Yes (all abort) |
| **C** | Coordinator sees votes, but never made a decision. It sends ABORT to all. | ✅ Yes (all abort) |
| **D** | Coordinator MUST commit. It re-sends COMMIT to all databases. | ✅ Yes, **BUT** databases were frozen and locked while the coordinator was down. |
| **E** | Coordinator re-sends COMMIT to databases that didn't confirm. | ✅ Yes, **BUT** same freezing problem as D. |
| **F** | Transaction is already complete. Nothing happens. | ✅ Yes |

**Crash Point C is the safe harbor**: If the coordinator dies before making a decision, the databases simply give up and abort. 

---

## 6. Code Example (Java Coordinator)

*Note: This is simplified to show the logic. You do not need to memorize this for an interview.*

```java
public class TwoPhaseCommitCoordinator {
    private final List<Participant> participants;
    private final TransactionLog txLog; // The "Diary"

    public boolean execute(String xid, List<DatabaseOperation> ops) {
        
        // --- PHASE 1: PREPARE ---
        txLog.write(xid, "PREPARING");
        boolean allPrepared = true;
        for (Participant p : participants) {
            if (!p.prepare(xid)) {
                allPrepared = false;
                break;
            }
        }

        if (!allPrepared) {
            txLog.write(xid, "ABORTING");
            participants.forEach(p -> p.abort(xid));
            return false; // The Wedding is off
        }

        // --- CRASH POINT D ---
        // Write COMMIT to the diary BEFORE telling anyone
        txLog.write(xid, "COMMIT");
        txLog.flush(); 

        // --- PHASE 2: COMMIT ---
        for (Participant p : participants) {
            boolean acked = false;
            while (!acked) { // Keep trying until they say "OK"
                try {
                    p.commit(xid);
                    acked = true;
                } catch (Exception e) {
                    sleep(100);
                }
            }
        }

        txLog.write(xid, "DONE");
        return true;
    }
}
```

---

## 🎤 Interview Talking Points

**Q: "Walk me through Two-Phase Commit. What happens if the coordinator crashes between Phase 1 and Phase 2?"**
> "In Phase 1, the coordinator asks all databases to prepare and lock their data. If they all say yes, Phase 2 begins where the coordinator tells them to commit. If the coordinator crashes right between Phase 1 and Phase 2, the databases are stuck. They have locked their data but don't know the final decision. This is called the 'blocking problem', and it's the main reason 2PC is avoided in modern microservices."

**Q: "You need atomicity across two databases for a payment transfer. Would you use 2PC or Saga?"**
> "It depends on the scale. If it's a legacy system with low traffic where absolute perfect consistency is legally required, I might use 2PC. But for a modern, high-traffic microservice architecture, 2PC is too slow and risks locking up the system. I would use the Saga pattern instead, which breaks the transaction into smaller local steps and uses compensating transactions to reverse mistakes, ensuring high availability."

---

## Applied In

This concept is used by **4 problems** in this repo:

**High-Level Design**

- [Design a Booking System (Hotels / Flights)](../../05-hld-problems/01-easy/booking-system.md)
- [Design a Hotel Booking System (Booking.com)](../../05-hld-problems/03-hard/hotel-booking.md)
- [Design a Payment System](../../05-hld-problems/03-hard/payment-system.md)
- [Design a Ticket Booking System (Ticketmaster)](../../05-hld-problems/03-hard/ticketmaster-seat-booking.md)

