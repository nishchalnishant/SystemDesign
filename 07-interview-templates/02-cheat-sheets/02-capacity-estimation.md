> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** The definitive cheat sheet for "Back-of-the-Envelope" math in System Design interviews.
>
> **Key concepts:**
> - **Traffic (QPS):** How many people click a button every second? (Calculate Read QPS and Write QPS).
> - **Storage:** How much hard drive space do you need for 5 years? (Write QPS * Size of Object * 5 Years).
> - **Bandwidth:** How thick does the internet pipe need to be? (QPS * Size of Object).
> - **Memory (Cache):** The 80/20 rule. Cache 20% of your data to serve 80% of your users.
>
> **Key takeaway:** The interviewer doesn't care if your math is perfectly accurate. They just want to know if you are building a small shed (10 GB) or a massive skyscraper (10 PB), because that changes the entire architecture. 

---
module: 07-interview-templates
status: unread
tags: [07-interview-templates, system-design, interview-templates, cheat-sheets]
---
# Capacity Estimation Cheat Sheet

> **Quick reference for doing math during an interview, using simple numbers and analogies.**

---

## ✍️ The Mental Model: The Napkin Sketch

> **Analogy: Pitching a Startup**
> Imagine you are a founder pitching to investors at a coffee shop. You grab a napkin and sketch: *"We will have a million users, they will each upload one photo a day, so we need a lot of hard drive space."*
> The investors don't expect a pixel-perfect CAD drawing. They just want to know: Are you building a doghouse, or a skyscraper? 

In an interview, you are doing Napkin Math. 
- You do NOT need to calculate exactly 86,400 seconds in a day. Just round it to **100,000**. 
- You do NOT need to be perfect. You just need to show the interviewer *why* you made a choice. (e.g., *"We need 10 Terabytes of storage, which is too big for one computer, so we must use a distributed NoSQL database."*)

---

## 🧮 The 3 Magic Numbers to Memorize

1. **1 Day** ≈ 100,000 seconds (Actually 86,400, but always round up).
2. **1 Month** ≈ 2.5 Million seconds.
3. **1 Year** ≈ 30 Million seconds.

---

## 📏 Sizes of Common Things

If the interviewer doesn't tell you how big a file is, use these guesses:
- **1 letter of text:** 1 Byte
- **A Tweet:** 500 Bytes
- **A small JSON file:** 1 Kilobyte (KB)
- **A Profile Picture:** 100 KB
- **A High-Res Photo:** 3 Megabytes (MB)
- **A 1-minute Video:** 50 Megabytes (MB)

---

## 🚦 Step 1: Traffic (QPS)

**QPS** stands for **Queries Per Second**. It means "How many times per second does someone click a button on our app?"

**The Formula:** `Total Daily Requests / 100,000`

> **Example (Twitter):**
> Let's say Twitter gets 100 Million Tweets a day. 
> 100,000,000 / 100,000 seconds = **1,000 QPS**. 
> That means 1,000 people click "Tweet" every single second.

**Always multiply by 3 for Peak Traffic.**
> Rush hour traffic is worse than midnight traffic. So if your average is 1,000 QPS, your "Peak QPS" is **3,000 QPS**. You must design your system to handle 3,000 QPS, otherwise it will crash during the Super Bowl.

---

## 💾 Step 2: Storage (Hard Drives)

**The Formula:** `Daily Requests * Size of Object * 365 Days * 5 Years`

> **Example (Twitter):**
> 100 Million Tweets a day.
> A tweet is 500 Bytes.
> 100M * 500 Bytes = **50 Gigabytes (GB) per day**. 
> 50 GB * 365 days * 5 years = **~90 Terabytes (TB)**.

**Always multiply by 3 for Replication.**
> You never keep just one copy of your data (what if the hard drive catches on fire?). You keep 3 copies. 
> 90 TB * 3 = **270 Terabytes (TB)** total storage needed.

---

## 🌐 Step 3: Bandwidth (The Internet Pipe)

Bandwidth is how much data is flowing through the internet cables every second.

**The Formula:** `Peak QPS * Size of Object`

> **Example (Netflix):**
> Let's say 20 Million people are watching Netflix at the exact same time (Peak QPS).
> A 1080p video uses 5 Megabits per second (Mbps).
> 20 Million * 5 Mbps = **100 Terabits per second (Tbps)**.

**The Insight:** 100 Tbps is so massively huge that a single server building could never handle it. Therefore, you *must* use a CDN (Content Delivery Network) to spread the load across the globe. 

---

## 🧠 Step 4: Memory / Cache (RAM)

Reading from a Hard Drive is like walking to the library. Reading from Cache (RAM) is like reading a sticky note on your desk. It is 100x faster. But Cache is very expensive, so you can't cache everything. 

**Use the 80/20 Rule:**
20% of your data generates 80% of your traffic. (e.g., Cristiano Ronaldo's tweets get 80% of the views, while normal people's tweets get very few). 

**The Formula:** `Daily Traffic * Size of Object * 20%`

> **Example:**
> 100 Million profile views a day.
> A profile is 1 KB. 
> Total = 100 Gigabytes (GB). 
> We only cache 20% of that = **20 GB**.
> 20 GB easily fits into a single Redis server!

---

## 🎤 Phrase to use in an interview:

> "Let's do some quick back-of-the-envelope math. Assuming 100 million daily active users making 5 requests a day, that gives us an average of 5,000 QPS. To handle peak traffic spikes, I'll multiply that by 3, giving us 15,000 Peak QPS. Since a single web server can handle about 5,000 QPS, we will need at least 3 web servers behind a Load Balancer to keep the system stable."

---

## Applied In

This concept is used by **36 problems** in this repo — a representative selection:

**High-Level Design**

- [Design Autocomplete / Typeahead Search](../../05-hld-problems/01-easy/autocomplete.md)
- [Design a Booking System (Hotels / Flights)](../../05-hld-problems/01-easy/booking-system.md)
- [Design a Distributed Key-Value Store](../../05-hld-problems/01-easy/key-value-store.md)
- [Design a Leaderboard](../../05-hld-problems/01-easy/leaderboard.md)
- [Design Pastebin](../../05-hld-problems/01-easy/pastebin.md)
- [Design a Rate Limiter](../../05-hld-problems/01-easy/rate-limiter.md)
- [Design a Unique ID Generator](../../05-hld-problems/01-easy/unique-id-generator.md)
- [Design a URL Shortener (Bitly)](../../05-hld-problems/01-easy/url-shortener.md)
- …and 28 more

