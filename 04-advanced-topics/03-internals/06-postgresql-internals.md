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

## Applied In

This concept is used by **3 problems** in this repo:

**High-Level Design**

- [Design a Booking System (Hotels / Flights)](../../05-hld-problems/01-easy/booking-system.md)
- [Design a Hotel Booking System (Booking.com)](../../05-hld-problems/03-hard/hotel-booking.md)
- [Design a Ticket Booking System (Ticketmaster)](../../05-hld-problems/03-hard/ticketmaster-seat-booking.md)

