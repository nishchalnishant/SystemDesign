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
