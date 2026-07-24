> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** How databases actually save data onto a physical hard drive, and why different databases are good at different things.
>
> **Key topics:**
> - **The Problem:** Saving data to a hard drive is extremely slow. If you just dump data on a drive randomly, it will take hours to find it later.
> - **B-Trees (The Phonebook):** Used by PostgreSQL and MySQL. Data is sorted neatly as it arrives. 
>   - *Pros:* Reading data is lightning fast because everything is alphabetical. 
>   - *Cons:* Writing data is slow, because you have to carefully erase and rewrite sections to keep the alphabetical order perfect.
> - **LSM Trees (The Logbook):** Used by Cassandra and DynamoDB. Data is just quickly scribbled at the very bottom of a list as fast as possible. 
>   - *Pros:* Writing data is insanely fast. 
>   - *Cons:* Reading data is slower, because the database has to search through a messy pile of notes.
>
> **Key takeaway:** If your app does 90% Reads (like Twitter or Wikipedia), use a B-Tree database. If your app does 90% Writes (like tracking Uber GPS locations), use an LSM Tree database.

---
module: 03-scaling
status: unread
tags: [03-scaling, system-design, databases]
---
# Database Storage Internals - System Design Guide

> This guide explains how databases store data on hard drives using simple analogies.

---

## 🤷‍♂️ Why Should I Care?

Imagine a doctor's office with a giant filing cabinet. 

**Option 1:** Every time a patient comes in, the receptionist carefully finds the exact right alphabetical folder, inserts the new medical record, and shifts all the other folders back. 
- *Result:* When a doctor asks for "John Smith's" file, the receptionist finds it in 2 seconds. (Reads are fast). But checking in a new patient takes 5 minutes. (Writes are slow). 

**Option 2:** Every time a patient comes in, the receptionist just throws their medical record into a giant cardboard box on the floor. 
- *Result:* Checking in a new patient takes 1 second. (Writes are fast). But when the doctor asks for "John Smith's" file, the receptionist has to dig through a messy cardboard box for 10 minutes. (Reads are slow).

In System Design, you have to choose which filing system you want. Do you want fast Reads (B-Trees)? Or fast Writes (LSM Trees)? You cannot have both. 

---

## 📖 B-Trees (The Alphabetical Filing Cabinet)

Relational Databases (like PostgreSQL, MySQL, Oracle) use **B-Trees**. 

> **💡 Analogy:** A phonebook. Everything is strictly sorted. To find "Smith," you flip to the middle (M). Smith is after M. You flip halfway to the end (S). You found it! This is called a Binary Search.

- **How it works:** Data is stored in small, fixed-size chunks called "Pages" on the hard drive. The database builds a massive tree structure to point exactly to which Page holds which data. 
- **The Magic:** A B-Tree is perfectly balanced. Even if you have 1 Billion users, it only takes exactly 4 "jumps" down the tree to find any user. 
- **The Pain:** When you INSERT a new user, the database has to find the exact alphabetical page, pry it open, squeeze the new data in, and sometimes split the page in half to make room. This requires spinning the physical hard drive a lot. It is slow.

**Use Case:** Apps where users read data way more than they write data. (E-Commerce, Social Media feeds, Forums).

---

## 📝 LSM Trees (The Messy Logbook)

NoSQL Databases (like Cassandra, DynamoDB, RocksDB) use **LSM Trees (Log-Structured Merge-Trees)**.

> **💡 Analogy:** A bartender keeping a tab. When you order a drink, he doesn't pull out a fancy alphabetical ledger. He just scribbles "John - Beer" at the very bottom of a notepad. 

- **How it works:** When you INSERT data, the database does not sort it. It just appends it to the very end of a file (called a Commit Log). Because it just writes to the end of the file, it is blazing fast. 
- **The Magic (SSTables):** Once the messy notepad gets full, the database quickly sorts it in RAM, and saves it as a permanent, read-only file called an SSTable.
- **The Pain:** When a user asks to READ their data, the database has to check the active notepad, and then check 5 different SSTable files to find where the data is hidden. 
- **Compaction:** To prevent the database from drowning in files, it runs a "Compaction" process at 3 AM to merge all the small messy files into one big sorted file.

**Use Case:** Apps that generate a massive, non-stop firehose of data. (IoT sensors, GPS tracking, Logging systems, Stock market tickers). 

---

## 🎤 Interview Questions to Practice

1. **"What is the difference between a B-Tree and an LSM Tree?"**
   *Answer:* B-Trees (used by SQL DBs) maintain data in a strictly sorted, balanced tree on disk. They offer incredibly fast Reads but slower Writes due to page-splitting. LSM Trees (used by NoSQL DBs) append data sequentially to a log and periodically merge them in the background. They offer incredibly fast Writes, but slower Reads due to searching multiple files. 
2. **"If we are building an IoT system that ingests 100,000 temperature readings per second, what storage engine should we use?"**
   *Answer:* An LSM-Tree based database like Cassandra. A B-Tree would thrash the disk trying to sort 100,000 inserts per second, but an LSM-Tree handles heavy write-throughput effortlessly by appending to a sequential log.
3. **"What is Compaction in an LSM Tree?"**
   *Answer:* It is a background process that merges multiple smaller, read-only data files (SSTables) into larger, fully-sorted files, while throwing away deleted or overwritten data. This prevents the disk from filling up and speeds up future read queries.

---

# 🎯 SDE-3 Deep Dive

The B-Tree-vs-LSM story is the setup. Seniors are judged on **the three amplifications, the compaction-strategy trade, and the concrete machinery each engine uses to survive** (WAL, tombstones, fence pointers). For index-structure depth (SSTable layout, fence pointers) see [`../04-advanced-topics/03-internals/01-index-structures.md`](../04-advanced-topics/03-internals/01-index-structures.md).

## The three amplifications — the language seniors use

Every storage engine trades off three costs. Name them:

| Amplification | Meaning | B-Tree | LSM-Tree |
|---|---|---|---|
| **Write** | bytes written to disk per logical byte | Higher (page rewrites + WAL) — but *in place*, bounded | **Higher over time** — each byte rewritten on every compaction level |
| **Read** | disk reads per logical lookup | ~1 (tree walk to one page) | Higher — may probe memtable + N SSTables (mitigated by Bloom filters) |
| **Space** | disk used per logical byte | ~1 + fragmentation (page splits leave ~⅔-full pages) | Extra copies live until compaction reclaims them; tombstones linger |

The senior insight: **you can't minimize all three — you pick two and pay the third.** LSM leaf-tiered compaction minimizes write amp but pays read/space; leveled compaction minimizes read/space but pays write amp. This is the RUM conjecture (Read, Update, Memory — pick two).

## Compaction strategy is the real tuning knob

| Strategy | Write amp | Read/Space amp | Use when |
|---|---|---|---|
| **STCS (Size-Tiered)** | Low | High (data spread across many overlapping SSTables; space spikes during merge) | Write-heavy, append-mostly (time-series, logs) |
| **LCS (Leveled)** | High | Low (each level has non-overlapping SSTables; a key lives in ~1 file per level) | Read-heavy, update-heavy (bounded read amp) |
| **TWCS (Time-Windowed)** | Low | Low for time-series | TTL'd time-series — drop whole expired SSTables, no merge |

Cassandra/RocksDB let you choose per-table. The senior move: **match compaction to the workload**, and know that STCS can *double* disk usage transiently during a major compaction (why you keep headroom).

## The machinery that makes reads survivable

An LSM read isn't "search 5 files blindly" — it's short-circuited:

- **Per-SSTable Bloom filter** ([`../02-building-blocks/02-performance/04-bloom-filter.md`](../02-building-blocks/02-performance/04-bloom-filter.md)) — skip files that definitely don't hold the key. A 1% FP means 1% wasted seeks.
- **Fence pointers / sparse index** — an in-memory index of the first key per block, so within a chosen SSTable you binary-search to the right block instead of scanning.
- **Block cache** — recently read blocks stay in RAM.

## Tombstones and the delete problem

LSM can't delete in place (SSTables are immutable) — a delete writes a **tombstone** (a marker). The read path must see the tombstone to hide the old value. Consequences seniors flag:

- Tombstones are only reclaimed after compaction merges past all older copies **and** a grace period (`gc_grace_seconds` in Cassandra) that must exceed the repair interval — else a deleted value can **resurrect** on a replica that missed the delete.
- **Range scans over many tombstones are slow** (the classic Cassandra queue-table anti-pattern: reading past millions of tombstoned rows). Don't model a work queue as delete-heavy rows.

## Durability: WAL + fsync

Both engines write to a **write-ahead log (commit log)** before acking, so a crash after ack is recoverable by replaying the WAL. The trade is the **fsync policy**: fsync-per-commit = durable but slow; group-commit/periodic fsync = fast but a crash can lose the last few ms of acked-but-unsynced writes. This is exactly the `fsync=on` vs `synchronous_commit=off` knob in Postgres — a senior connects it to the durability/latency trade.

## Interview probes you should survive

- *"Why is LSM better for writes if it rewrites data during compaction?"* → The *foreground* write is a cheap sequential append; the rewrite cost is deferred to background compaction and amortized, keeping the write path fast. B-Tree pays random-write page-split cost synchronously.
- *"Your LSM read latency degraded — what's the cause?"* → Read amplification: too many SSTables to probe (compaction falling behind) or a Bloom-filter miss. Check compaction backlog and switch/tune strategy (LCS for read-heavy).
- *"You deleted rows but disk didn't shrink and a value came back — why?"* → Tombstones aren't reclaimed until compaction + gc_grace; a value resurrects if a replica missed the delete and gc_grace elapsed before repair. Run repair within gc_grace.
- *"Disk usage doubled at 3 AM then dropped — normal?"* → Yes: size-tiered compaction transiently needs space to merge overlapping SSTables. Keep ~50% headroom or use leveled/TWCS.
