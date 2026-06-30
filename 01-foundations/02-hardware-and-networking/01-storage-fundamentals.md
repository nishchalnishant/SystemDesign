> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** How computers store data, from lightning-fast memory to massive, slow hard drives, and how to choose the right one for your application.
>
> **Key topics:**
> - **Storage hierarchy:** Think of it like a kitchen. L1/L2 cache is the cutting board (tiny but instant), RAM is the counter (bigger but temporary), SSD is the fridge (large and permanent), and Network Storage is the grocery store (unlimited but a drive away).
> - **Storage types:** Block (raw drives), File (shared folders), Object (flat, huge buckets like Amazon S3).
> - **Key metrics:** IOPS (how many small tasks you can do per second), throughput (how fast you can move massive files), and latency (how long it takes to start).
> - **Trade-offs:** Fast storage is expensive and often temporary. Permanent storage is cheaper but much slower. 
> - **Failure modes:** When drives slow down drastically, or when they physically break.
> - **When to use each tier:** RAM for active users, SSDs for fast databases, HDDs for massive archives, S3 for images/videos.
>
> **Key takeaway:** Every time you design a system, you are making a choice about storage. Knowing the speed and cost of each type separates the guessing engineers from the great ones.

---
module: 01-foundations
status: unread
tags: [01-foundations, system-design, foundations]
---
# Storage Fundamentals

> The physical storage hierarchy is the bedrock of system design. Every database, cache, and filesystem is just software built on top of physical hardware. This guide explains the differences between RAM, SSDs, Hard Drives, and Cloud Storage in simple terms.

---

## 🤷‍♂️ Why Should I Care?

Imagine a team builds a new, "blazing fast" app. To save money, they rent cheap Hard Disk Drives (HDDs) for their database server. 

When the app goes live, 10,000 users try to update their profiles at the same time. Suddenly, the app freezes. The team panics and blames the code, the network, and the database software. They spend weeks debugging.

The real issue? An HDD has a physical mechanical arm inside it. It can only physically move fast enough to handle about **200 profile updates per second (IOPS)**. By switching to a modern Solid State Drive (SSD) with no moving parts, the server could handle **500,000 updates per second**. 

Without understanding how physical storage works, engineers waste time fixing the wrong problems.

---

## 🍎 The Kitchen Analogy: The Storage Hierarchy

To understand the storage hierarchy, imagine you are a chef in a kitchen:

1. **L1/L2/L3 Cache (The Cutting Board):** Right in front of you. You can chop instantly. It holds very little (Kilobytes/Megabytes).
2. **RAM (The Kitchen Counter):** A step away. It's fast, and you can put a lot of ingredients here (Gigabytes). But at the end of the day, everything on the counter is thrown out (volatile).
3. **SSD (The Fridge):** Across the room. It takes a little time to walk there, but it holds massive amounts of food (Terabytes), and whatever you put there stays there permanently.
4. **Network Storage (The Grocery Store):** You have to get in a car and drive over the internet. It takes the longest time, but it holds literally infinite amounts of food.

### The Real Numbers You Must Know

Here is how those kitchen layers map to computer hardware:

| Tier | Latency (Wait Time) | IOPS (Tasks/Sec) | Capacity | Does it lose data on reboot? |
|---|---|---|---|---|
| **RAM (Memory)** | 100 nanoseconds | 1,000,000+ | 8 GB - 12 TB | **Yes (Volatile)** |
| **NVMe SSD** | 100 microseconds | 500,000+ | 1 - 8 TB | No (Permanent) |
| **SATA SSD** | 200 microseconds | 100,000 | 1 - 4 TB | No (Permanent) |
| **HDD (Hard Drive)** | 5-10 milliseconds | ~200 | 1 - 20 TB | No (Permanent) |
| **Cloud Object (S3)** | 10-100 milliseconds | ~3,500 per folder | **Unlimited** | No (Permanent) |

> **💡 The Rule of 10s:** As a rule of thumb, each step down the table is about **10x slower**, but also **10x cheaper** per Gigabyte.

---

## 📦 The 3 Types of Storage

When you rent storage from AWS or buy a hard drive, it usually comes in one of three flavors.

### 1. Block Storage (The "Raw" Drive)
Imagine a giant grid of empty boxes. The computer can read or write to any box it wants at lightning speed. 
- **Examples:** Your laptop's hard drive, AWS EBS.
- **Best for:** Databases (like MySQL) or installing Operating Systems. 
- **Downside:** Usually, only *one* computer can plug into a block drive at a time.

### 2. File Storage (The Shared Folder)
Imagine a traditional office filing cabinet. Files are organized into folders. Multiple people (computers) can open the cabinet at the same time.
- **Examples:** AWS EFS, Google Filestore, a shared Google Drive folder.
- **Best for:** When multiple servers need to read the exact same configuration files or data simultaneously.
- **Downside:** Because it goes over a network and has to manage folders, it's slower than Block storage.

### 3. Object Storage (The Bottomless Bucket)
Imagine a massive warehouse where you just throw boxes in and get a barcode back. There are no folders, just flat "objects" (like images or videos) accessed via web URLs. 
- **Examples:** Amazon S3, Google Cloud Storage.
- **Best for:** Profile pictures, videos, massive backups, files you write once and read many times.
- **Downside:** You can't just edit a small part of a file (no "random writes"). If you want to change a 1GB video, you have to upload the entire 1GB video again.

---

## 💿 Deep Dive: Why are SSDs so much better than HDDs?

### HDDs: The Record Player
An HDD (Hard Disk Drive) works exactly like a vinyl record player. It has a spinning disk (platter) and a mechanical needle (head). 
- If you want to read a massive movie file from start to finish, it's decently fast because the needle just stays in one place while the disk spins. (This is called **Sequential Reading**).
- But if a database asks for 1,000 random user profiles scattered all over the disk, the physical needle has to physically swing back and forth 1,000 times. This is incredibly slow. (This is called **Random Reading**).

### SSDs: The Flash Grid
An SSD (Solid State Drive) has literally zero moving parts. It uses electricity to trap electrons in tiny cells.
- Because there is no mechanical needle, the SSD can grab 1,000 random user profiles instantly. 
- This is why swapping an old HDD for an SSD makes an old laptop feel brand new.

> **⚠️ The "Write Cliff" Warning:**
> SSDs have a quirk. To write new data, they actually have to erase large blocks of old data first. If you write data to an SSD faster than it can erase the old blocks, the SSD gets overwhelmed and slows down drastically. This is called the "Write Cliff." Enterprise databases use special, expensive SSDs to prevent this.

---

## 🏗️ How to use this in System Design

When you are asked to design a system (like Netflix or Twitter), you should mix and match these storage tiers. This is called **Tiered Storage**.

### Match the Data to the Hardware
1. **Hot Data (Needed instantly):** Put this in RAM or fast NVMe SSDs. (e.g., Active user sessions, the homepage feed).
2. **Warm Data (Needed sometimes):** Put this on cheaper SATA SSDs. (e.g., A post from 2 weeks ago).
3. **Cold Data (Rarely needed):** Put this on HDDs or Cloud Object Storage. (e.g., Chat logs from 5 years ago, raw video files).

### The Database Trick: "WAL"
Earlier we said HDDs are terrible at random writes. But databases (like PostgreSQL) write random data all the time! How do they survive on cheap HDDs?

They use a trick called a **Write-Ahead Log (WAL)**.
Instead of the needle moving all over the disk to update 10 different tables, the database just writes a single sequential note at the end of a log file: *"I am going to update these 10 tables."* 
Because appending to a log is a *sequential write*, the HDD needle doesn't have to move! A background process cleans up the actual tables later.

---

## ✅ The Decision Framework

If you get confused in an interview, ask yourself these questions:

1. **Does the data disappear if the power goes out?**
   - *Yes:* Use RAM.
   - *No:* Use a Disk.
2. **Do I need to do millions of tiny updates (like a database)?**
   - *Yes:* Block Storage (SSD).
3. **Do I need to store massive 10GB video files for cheap?**
   - *Yes:* Object Storage (S3).
4. **Do 10 different servers need to read the exact same folder?**
   - *Yes:* File Storage (NFS).

---

## 🎤 Interview Questions to Practice

1. **Google:** "Our logging service writes 500MB per second of log files. Should we store them on expensive SSDs or cheap HDDs?" 
   *Answer:* HDDs. Writing logs is purely sequential (appending to the end of a file). HDDs are great at sequential writes and are 10x cheaper!
2. **Meta:** "Design a cold storage system for 10 years of user photos that costs as little as possible."
   *Answer:* Object storage (like Amazon S3 Glacier). It's incredibly cheap, highly durable, and photos don't need to be loaded in microseconds.
3. **Stripe:** "Our database is freezing up. The CPU is only at 10%, but responses are taking 5 seconds. What is wrong?"
   *Answer:* You are likely "I/O Bound." This means your super-fast CPU is sitting around waiting for your slow hard drive (I/O) to find the data. You should upgrade to an SSD, or add more RAM to cache the data.
