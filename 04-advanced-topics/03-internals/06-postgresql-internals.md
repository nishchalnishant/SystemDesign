> [!NOTE]
> ** 5-Minute Summary**
>
> **What this covers:** The magic trick that allows 100 people to read a database while 100 other people are writing to it, without anyone waiting in line.
>
> **Key topics:**
> - **The Problem:** If User A is reading a bank account balance, and User B is updating that balance at the exact same millisecond, the database might crash or return half-written, corrupted data.
> - **Locking (The Old Way):** If User B is writing, lock the door. User A has to wait outside until User B finishes. Very slow.
> - **MVCC (Multi-Version Concurrency Control):** The PostgreSQL magic trick. Instead of locking the door, PostgreSQL quietly makes a photocopy of the data for User A to read, while User B modifies the original document. Everyone is happy, nobody waits!
> - **VACUUM:** Because MVCC creates thousands of old "photocopies", PostgreSQL has to hire a janitor (the VACUUM process) to clean up the trash in the background.
> - **WAL (Write-Ahead Log):** How PostgreSQL survives power outages by writing down what it is *going* to do before it actually does it.
>
> **Key takeaway:** PostgreSQL is the most beloved relational database in the world because MVCC allows for massive concurrency (thousands of users at once) without sacrificing absolute ACID strictness.

---
module: 04-advanced-topics
status: unread
tags: [04-advanced-topics, system-design, internals, databases, sql]
---
# PostgreSQL Internals - System Design Guide

> This guide explains the internal magic of PostgreSQL using simple analogies.

---

## Why Should I Care?

Imagine a giant whiteboard with the words: `Tickets Available: 1`.

Alice wants to buy the ticket. She walks up to the whiteboard, picks up an eraser, and starts erasing the `1` so she can write `0`.
At that exact millisecond, Bob walks into the room. He looks at the whiteboard. Because Alice is halfway through erasing, Bob sees `Tickets Available:  `. He gets confused and the application crashes.

To fix this, older databases used **Locks**. If Alice is erasing the board, she locks the door to the room. Bob has to stand in the hallway for 5 seconds until she finishes.
This works, but if you have 10,000 users, the line out the door becomes incredibly long. The database becomes painfully slow.

**PostgreSQL** fixes this using a brilliant concept called **MVCC (Multi-Version Concurrency Control)**.

---

## MVCC (The Photocopy Trick)

PostgreSQL says: "Readers should never wait for Writers, and Writers should never wait for Readers."

> ** Analogy:**
> Instead of a whiteboard, the tickets are tracked on a piece of paper on a desk.
> Alice walks up and wants to change the ticket count from 1 to 0.
> Bob walks in and wants to read the ticket count.
>
> Instead of locking Bob out, PostgreSQL acts like a hyperactive secretary. The exact millisecond Alice starts erasing the original paper, PostgreSQL slides a **photocopy** of the original paper (saying `Tickets: 1`) onto Bob's desk.
>
> Bob reads the photocopy. Alice updates the original. Nobody waited in line!

**How it actually works:** When you `UPDATE` a row in PostgreSQL, it does not actually overwrite the data on the hard drive. It literally inserts a brand new row next to it with the new data, and marks the old row as "invisible to new users."

---

## The VACUUM (The Janitor)

Because of MVCC, if you update a user's profile 500 times, PostgreSQL actually saves 500 copies of that profile on the hard drive! 499 of them are invisible "photocopies" from the past.

If you don't clean this up, your 5GB database will secretly bloat into a 500GB database, and your hard drive will explode. (This is called **Table Bloat**).

**The Fix:** PostgreSQL runs a background process called **VACUUM**.
> ** Analogy:** At 3:00 AM, the janitor walks through the office, finds all the old, invisible photocopies that nobody is looking at anymore, and throws them in the shredder, freeing up space on the hard drive.

If your PostgreSQL database suddenly gets incredibly slow, it is almost always because the VACUUM process broke, and the database is drowning in old photocopies.

---

## 🪵 WAL (Write-Ahead Log)

If a user buys a ticket, and PostgreSQL crashes 1 second later, how does it remember the ticket was sold?

It uses a **Write-Ahead Log (WAL)**.
> ** Analogy:** Before the accountant is allowed to open the giant, heavy ledger book and officially change your bank balance, he MUST scribble the transaction on a cheap notepad on his desk.

1. User sends an `UPDATE`.
2. PostgreSQL instantly scribbles it into a raw text file called the WAL (The notepad). This takes 1 millisecond.
3. PostgreSQL tells the user "Success!"
4. Later, in the background, PostgreSQL does the heavy lifting of opening the actual database files (The Ledger) and organizing the data.
5. If the power goes out at step 3, PostgreSQL reboots, looks at the notepad on the desk, and finishes the job.

---

## Interview Questions to Practice

1. **"What is MVCC and what problem does it solve in PostgreSQL?"**
   *Answer:* Multi-Version Concurrency Control (MVCC) allows PostgreSQL to handle highly concurrent traffic without heavy locking. When a row is updated, PostgreSQL doesn't overwrite it; it creates a new version of the row. This ensures that "Readers do not block Writers, and Writers do not block Readers," preventing the database from slowing down during heavy simultaneous access.
2. **"Why does PostgreSQL need a VACUUM process, and what happens if it fails?"**
   *Answer:* Because of MVCC, every `UPDATE` or `DELETE` creates a "dead tuple" (an old, invisible version of the row). The VACUUM process runs in the background to physically remove these dead tuples and reclaim disk space. If VACUUM fails or cannot keep up, the table suffers from "Table Bloat," drastically reducing read performance and filling up the hard drive.
3. **"How does the Write-Ahead Log (WAL) ensure durability (The 'D' in ACID)?"**
   *Answer:* Before any changes are made to the actual database data files on disk, the change is first appended sequentially to the WAL. If the database crashes, upon reboot, PostgreSQL reads the WAL and replays any transactions that were committed but not yet fully written to the main data files, ensuring no data is ever lost.

---

# 🎯 SDE-3 Deep Dive

MVCC + VACUUM + WAL is the intro. Seniors get asked about **the darker side of MVCC (bloat, transaction-ID wraparound, HOT updates), what the WAL enables beyond crash recovery (replication), the heap-vs-clustered contrast with MySQL, and the isolation levels PostgreSQL actually implements.**

## The MVCC bill comes due: bloat, wraparound, and write amplification

MVCC's "new row per update" is elegant but has three senior-level consequences:

- **Every UPDATE is effectively an INSERT + dead tuple**, and — because indexes point at physical row locations (ctid) — **every index must also be updated**, even for unchanged columns. This is **write amplification**. The mitigation is **HOT (Heap-Only Tuple) updates**: if the changed column isn't indexed *and* the new tuple fits on the same page, PostgreSQL skips the index updates. This is why a table needs **fillfactor < 100** (leave free space per page) to get HOT updates on hot tables.
- **Transaction-ID wraparound:** XIDs are 32-bit and wrap. VACUUM must "freeze" old tuples before the 2-billion-XID horizon, or PostgreSQL **shuts down writes to protect data**. A famous class of outages (Sentry, Mailchimp) came from autovacuum falling behind on wraparound. Monitor `age(datfrozenxid)`.
- **Long-running transactions block VACUUM globally:** VACUUM can only remove tuples no snapshot can see; one idle-in-transaction session pins the "oldest snapshot" horizon and **bloat accumulates table-wide**. Same failure class as MySQL's undo bloat, different mechanism.

## WAL isn't just crash recovery — it's the replication and PITR backbone

The WAL is the source of truth that everything downstream consumes:

- **Streaming (physical) replication:** replicas replay the primary's WAL byte-for-byte. `synchronous_commit` controls the durability/latency trade: `on` (wait for local flush), `remote_apply` (wait for replica to apply — no replication-lag reads), or `off` (fast, small data-loss window). This is the semi-sync knob.
- **Logical replication / logical decoding:** decodes WAL into row-level change events — the foundation of **CDC** (Debezium reads the WAL via a replication slot). See [`../../01-foundations/05-advanced-distributed-theory/03-change-data-capture.md`](../../01-foundations/05-advanced-distributed-theory/03-change-data-capture.md).
- **Replication slots** guarantee the primary retains WAL until a consumer has it — but a *dead/slow consumer* then makes WAL pile up and **fill the disk**. A classic CDC prod incident.
- **PITR (Point-In-Time Recovery):** base backup + archived WAL lets you restore to any moment.

## Heap storage vs MySQL's clustered index — the contrast to draw

PostgreSQL stores rows in an unordered **heap**; *all* indexes (including the primary key) are **secondary** and point to physical tuple locations. Contrast with InnoDB's clustered index (see [`07-mysql-internals.md`](07-mysql-internals.md)):

- Postgres has **no "double lookup" penalty distinction** — every index is one indirection to the heap. But it has no clustered-index locality either; range scans on the PK aren't physically sequential (you can `CLUSTER` a table manually, but it's a one-time reorder).
- **Index-only scans** are possible when the query's columns are all in the index *and* the visibility map says the page is all-visible — otherwise Postgres must hit the heap to check tuple visibility (an MVCC tax MySQL avoids for PK reads).

## Isolation levels — what Postgres actually gives you

- **Read Committed (default):** each statement sees a fresh snapshot. Prone to non-repeatable reads and lost updates across statements.
- **Repeatable Read:** snapshot at transaction start; Postgres's RR *also prevents phantoms* (stronger than the SQL standard) and aborts on write conflicts with a **serialization failure** — your app must retry.
- **Serializable (SSI — Serializable Snapshot Isolation):** true serializability by tracking read/write dependencies and aborting dangerous cycles. Cheaper than locking, but retries under contention. Reach for it when correctness needs it (financial invariants) and handle `40001` retries.

## Interview probes you should survive

- *"Your Postgres table got slow and huge despite few rows — why?"* → Bloat: dead tuples not reclaimed because autovacuum fell behind or a long-running/idle-in-transaction session pinned the snapshot horizon. Fix vacuum, kill idle txns, consider `fillfactor`/HOT.
- *"What is XID wraparound and why can it halt your database?"* → 32-bit transaction IDs wrap; unfrozen old tuples past the horizon risk data corruption, so Postgres refuses writes until vacuumed. Monitor and keep autovacuum healthy.
- *"How does a replica stay in sync, and how do you avoid stale reads on it?"* → It replays the primary's WAL stream. For no-stale-read guarantees use `synchronous_commit = remote_apply`; accept lag otherwise.
- *"CDC pipeline filled the primary's disk — how?"* → An inactive logical replication slot forced WAL retention. Monitor slot lag; drop dead slots.
- *"Why might an UPDATE be surprisingly expensive even on one column?"* → Write amplification: MVCC writes a new tuple and updates *every* index unless it qualifies as a HOT update (non-indexed column + room on page). Tune fillfactor.

---

## Applied In

This concept is used by **3 problems** in this repo:

**High-Level Design**

- [Design a Booking System (Hotels / Flights)](../../05-hld-problems/01-easy/booking-system.md)
- [Design a Hotel Booking System (Booking.com)](../../05-hld-problems/03-hard/hotel-booking.md)
- [Design a Ticket Booking System (Ticketmaster)](../../05-hld-problems/03-hard/ticketmaster-seat-booking.md)

