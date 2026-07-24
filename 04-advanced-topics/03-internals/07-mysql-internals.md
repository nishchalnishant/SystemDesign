> [!NOTE]
> ** 5-Minute Summary**
>
> **What this covers:** How the most popular open-source database in the world (MySQL with InnoDB) actually organizes data on a hard drive.
>
> **Key topics:**
> - **Clustered Index (The Main Phonebook):** In MySQL, the Table *is* the Index. The data isn't just lying around randomly; it is physically sorted on the hard drive by the Primary Key (e.g., User ID).
> - **Secondary Indexes (The Back Index):** If you want to search by "Last Name," MySQL builds a tiny, separate index. But it doesn't point to the data; it points back to the Primary Key! (A two-step lookup).
> - **Redo Log (The Crash Saver):** If the power goes out, the Redo log ensures you never lose a committed transaction.
> - **Undo Log (The Time Machine):** If you make a mistake and type `ROLLBACK`, the Undo log remembers the old data so it can magically reverse your changes.
>
> **Key takeaway:** Because MySQL physically sorts data by the Primary Key, queries that search by ID are unbelievably fast. Queries that search by anything else (like email) require an extra "hop" through the Secondary Index.

---
module: 04-advanced-topics
status: unread
tags: [04-advanced-topics, system-design, internals, databases, sql]
---
# MySQL Internals (InnoDB) - System Design Guide

> This guide explains the internal storage engine of MySQL using simple analogies.

---

## Why Should I Care?

Imagine a library where the books are just thrown onto shelves in the exact order they were purchased. To find a specific book, the librarian has to maintain a massive notebook (an Index) that says: "Harry Potter is on Shelf 4, Book 12."

This is how PostgreSQL works (it's called a Heap).

**MySQL (using the InnoDB engine)** does something completely different.
In MySQL, the librarian physically organizes the books on the shelves in perfect numerical order based on the `Book_ID`.

This is called a **Clustered Index**. The Index and the Data are the exact same thing!
In MySQL, if you ask for `User_ID = 500`, it doesn't look at a notebook and then walk to the shelf. It just walks straight to the `500` section of the shelf and hands you the data.

---

## Secondary Indexes (The Double Jump)

If the books are physically sorted by `Book_ID`, what happens if a user wants to search by `Author_Name`?

You have to create a **Secondary Index**.
> ** Analogy:** The librarian creates a tiny notebook just for Author Names.
> You look up "Rowling, J.K." in the notebook.
> Does the notebook tell you exactly where the book is on the shelf? **No.**
> The notebook says: "Rowling, J.K. -> Book_ID 500".

You then have to take `Book_ID 500` and walk to the main shelf to actually find the book.
In MySQL, every Secondary Index query requires **Two Jumps**. First you search the Secondary Index to find the Primary Key. Then you search the Clustered Index (the main table) to find the actual data.

*(Pro-Tip: If you want MySQL to be fast, always make your Primary Keys short, like integers! If your Primary Key is a massive 100-character string, every single Secondary Index will be forced to store that massive string, wasting gigabytes of RAM).*

---

## ⏪ The Undo Log (The Time Machine)

Imagine you start a Database Transaction. You update a user's bank balance from $100 to $50.
Suddenly, the user's credit card is declined. You cancel the transaction (`ROLLBACK`).

How does MySQL remember that the balance used to be $100?
It uses the **Undo Log**.
Before MySQL changes the $100 to $50, it writes "$100" into a secret Time Machine file. If you yell `ROLLBACK`, MySQL looks in the Time Machine, grabs the $100, and puts it back.

*(PostgreSQL doesn't need an Undo Log, because MVCC just keeps the old photocopy! MySQL InnoDB uses a hybrid approach, where the Undo Log actually helps power its version of MVCC).*

---

## ⏩ The Redo Log (The Crash Saver)

Writing data to the physical sorted B-Tree on the hard drive takes a long time.
To make the database fast, MySQL just saves your updates in RAM, and says "Success!" to the user.
But if the server loses power, the RAM is erased.

To prevent data loss, MySQL uses a **Redo Log** (Exactly like PostgreSQL's WAL).
Before saying "Success", it instantly scribbles the transaction at the very bottom of a raw text file (The Redo Log). If the power dies, MySQL reboots, reads the Redo Log, and re-applies all the changes to the RAM.

---

## Interview Questions to Practice

1. **"What is the difference between a Clustered Index and a Secondary Index in MySQL (InnoDB)?"**
   *Answer:* A Clustered Index physically dictates how the rows of data are sorted and stored on the disk (usually by the Primary Key). There can only be one Clustered Index per table. A Secondary Index is a separate structure that stores the indexed column and a pointer back to the Primary Key.
2. **"Why does a query using a Secondary Index in MySQL sometimes cause a performance hit?"**
   *Answer:* Because it requires a "Double Lookup" (or a Bookmark Lookup). The database first traverses the Secondary Index B-Tree to find the Primary Key, and then must traverse the Clustered Index B-Tree to fetch the actual row data.
3. **"What is the purpose of the Undo Log and the Redo Log in InnoDB?"**
   *Answer:* The Undo Log stores previous versions of data before they were modified, enabling MVCC (non-blocking reads) and `ROLLBACK` capabilities (Atomicity). The Redo Log is an append-only file that records all modifications before they are flushed to disk, ensuring that committed transactions survive a power failure (Durability).

---

# 🎯 SDE-3 Deep Dive

The clustered-index / redo / undo story is the mechanism. Seniors get asked about **the buffer pool and how writes actually reach disk, MVCC + locking (gap locks / phantom prevention), and the replication format that all of the above feeds.**

## The write path: buffer pool → redo → checkpoint (not "straight to disk")

InnoDB never writes a row directly to its final B-Tree page on commit. The real path:

1. **Buffer pool** (in-RAM cache of pages) — the modified page becomes a **dirty page** here. This is the single most important memory structure; size it to ~70–80% of RAM.
2. **Redo log** — the *change* is appended to the redo log and fsync'd on commit (controlled by `innodb_flush_log_at_trx_commit`: `1` = fsync per commit (durable, ACID), `2`/`0` = weaker but faster).
3. **Change buffer** — for *secondary-index* writes to pages not in the buffer pool, the change is buffered and merged later, avoiding a random read on every secondary-index insert.
4. **Checkpointing** — a background thread flushes dirty pages to the tablespace over time. The redo log is **circular**; if it fills before checkpointing catches up, writes *stall*. Sizing the redo log (`innodb_redo_log_capacity`) is a classic write-throughput lever.

The senior line: **commit durability comes from the redo fsync, not from writing the data page.** The data page is written lazily. This is why InnoDB survives crashes (redo replay) and why a too-small redo log throttles writes.

## MVCC: read views, not read locks

InnoDB reads don't block writes. Each transaction gets a **read view** (a snapshot of which transaction IDs are visible); to read a row it walks the **undo log chain** to reconstruct the version visible to its snapshot. Consequences:

- **Isolation levels differ by *when* the read view is taken:** `REPEATABLE READ` (InnoDB default) takes one snapshot at first read and reuses it — so repeated reads are stable. `READ COMMITTED` takes a fresh snapshot per statement.
- **Long-running transactions bloat the undo log** (history list length grows) because old versions can't be purged while any read view might still need them — a classic prod incident (idle-in-transaction connection pinning purge).

## Locking: gap locks and phantom prevention — the InnoDB-specific gotcha

Under `REPEATABLE READ`, InnoDB uses **next-key locks** (row lock + gap lock on the range before it) to prevent **phantoms**. This is why:

- An `INSERT` can block on a range even where no row exists yet.
- Two transactions each taking gap locks then inserting can **deadlock** — InnoDB detects the cycle and rolls one back (`ER_LOCK_DEADLOCK`); your app must retry.
- Locking without an index escalates: a `WHERE` on a non-indexed column can lock **every row scanned**, not just matched rows — a subtle way to lock a whole table.

## Binlog vs redo log — two different logs

Interviewers probe this because people conflate them:

| Log | Owner | Purpose | Format |
|---|---|---|---|
| **Redo log** | InnoDB engine | Crash recovery (durability) | Physical (page changes), circular |
| **Binlog** | MySQL server layer | **Replication** + point-in-time recovery | Logical events, append-only, retained |

Replication ships the **binlog**, in one of three formats: **STATEMENT** (replays SQL — unsafe for non-deterministic funcs like `NOW()`/`UUID()`), **ROW** (ships actual row images — safe, larger, the default), **MIXED** (row when needed). The two logs are kept consistent by a **two-phase commit between InnoDB and the binlog** — a crash between the two would otherwise desync a replica.

## Interview probes you should survive

- *"Does COMMIT write my row to disk?"* → No — it fsyncs the redo log record; the data page is flushed lazily from the buffer pool at checkpoint. Durability rides on the redo log, not the data file.
- *"Your write throughput plateaus and you see redo-log stalls — why?"* → Redo log is circular and too small; checkpointing can't keep up, so commits wait. Enlarge `innodb_redo_log_capacity` and buffer pool.
- *"An INSERT deadlocked with no overlapping rows — how?"* → Gap/next-key locks under REPEATABLE READ lock ranges, not just rows; two txns holding gap locks then inserting form a cycle. Retry on deadlock; consider READ COMMITTED to drop gap locks.
- *"Why is ROW-based binlog safer than STATEMENT?"* → STATEMENT replays SQL, so non-deterministic functions (`NOW()`, `RAND()`, `AUTO_INCREMENT` races) diverge on the replica. ROW ships the resulting row image, which is deterministic.
- *"A long-idle transaction is bloating the DB — mechanism?"* → Its read view pins old row versions so undo/purge can't reclaim them; the undo history grows unbounded. Kill idle-in-transaction sessions; keep transactions short.
