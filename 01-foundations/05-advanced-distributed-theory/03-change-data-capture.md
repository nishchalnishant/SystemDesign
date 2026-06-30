> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** How to safely move data from your main database into a cache or a search engine without losing anything.
>
> **Key topics:**
> - **The Problem:** Dual Writes. If your app tries to save data to the Database AND the Cache at the same time, one might fail, leaving them permanently out of sync.
> - **The Solution:** Change Data Capture (CDC). Only the Database is allowed to accept writes. Other systems "eavesdrop" on the database to update themselves.
> - **The Write-Ahead Log (WAL):** The database's secret diary. Every database writes down what it is going to do in a diary *before* it actually does it. 
> - **Debezium:** The most popular tool that reads the secret diary and shouts the changes into a Message Queue (like Kafka).
>
> **Key takeaway:** Never let your application code write to a database and a cache at the exact same time. Always use CDC to let the database update the cache automatically in the background.

---
module: 01-foundations
status: unread
tags: [01-foundations, system-design, foundations]
---
# Change Data Capture (CDC) - System Design Guide

> This guide explains how to sync databases, caches, and search engines using simple analogies.

---

## 🤷‍♂️ Why Should I Care?

Imagine you are building an Amazon clone. When a seller updates the price of a TV from $500 to $400, your code needs to do two things:
1. Save the new $400 price in the Main Database (PostgreSQL).
2. Save the new $400 price in the Search Engine (Elasticsearch) so users searching for "cheap TVs" can find it.

**The "Dual Write" Trap:**
Your code tries to update PostgreSQL. It succeeds! 
A millisecond later, your code tries to update Elasticsearch. But the network crashes! The update fails.

Now your systems are corrupted. If a user clicks the TV, the database says it costs $400. But if they search for TVs under $450, it won't show up, because the search engine still thinks it costs $500. 
They are permanently out of sync. **Change Data Capture (CDC)** is the industry-standard way to fix this.

---

## 🛑 The Wrong Way: Dual Writes
> **💡 Analogy:** A boss walks into the office and shouts to the Accountant and the Secretary: "Our new budget is $5,000!" The Accountant hears it and writes it down. The Secretary happens to be in the bathroom and misses it. Now the company has two different budgets.

When your application (The Boss) tries to update two systems at once, it is almost guaranteed that one will eventually fail while the other succeeds. 
You cannot wrap them in a single transaction, because they are two entirely different systems (Postgres and Elastic).

---

## ✅ The Right Way: Change Data Capture (CDC)

> **💡 Analogy:** The boss walks into the office and only tells the Accountant the new budget. The Accountant writes it down in the official Ledger. The Secretary just stares at the Ledger all day. The second the Accountant writes something in the Ledger, the Secretary copies it into their own notebook. 

**How CDC Works:**
1. Your application ONLY writes to the Main Database. 
2. The Database saves the data.
3. In the background, a special tool (like Debezium) watches the database for any changes.
4. As soon as a change happens, Debezium copies it and sends it to the Search Engine or the Cache. 

Even if the Search Engine's server catches on fire and dies for 3 days, it's fine! When it turns back on, it will just look at the Database's history and catch up on everything it missed. It is physically impossible for them to get permanently out of sync.

---

## 📖 How Does It Watch the Database? (The WAL)

You might be thinking: "Wait, if Debezium is constantly watching the database, won't that slow the database down?"

No! Because Debezium doesn't actually watch the database tables. It watches the **Write-Ahead Log (WAL)**.

Every major database (PostgreSQL, MySQL, Oracle) has a secret diary called a Write-Ahead Log. 
Before a database actually saves your data to the hard drive, it writes down what it is *about to do* in this diary. 
*(e.g., "At 12:00 PM, I am going to change the TV price from $500 to $400").*

Debezium simply reads this diary file as it gets written. It uses almost zero CPU, and doesn't slow down the database at all!

---

## ⚙️ The Standard CDC Architecture

If you are asked to design a system that syncs a Database, a Cache, and a Search Engine in an interview, draw this exact architecture:

1. **App Server:** Sends a write to PostgreSQL.
2. **PostgreSQL:** Saves the data and writes to the WAL.
3. **Debezium (The Connector):** Reads the WAL, sees the change, and sends a message into Kafka.
4. **Kafka (The Message Queue):** Holds the message safely in a line.
5. **Consumers:** The Cache (Redis) and the Search Engine (Elasticsearch) both read the message from Kafka and update themselves. 

---

## 🎤 Interview Questions to Practice

1. **"What is the Dual Write problem?"**
   *Answer:* It's when application code tries to write to two different systems (like a database and a cache) at the same time. If one succeeds and the other fails, the systems become permanently out of sync. 
2. **"How does Change Data Capture fix the Dual Write problem?"**
   *Answer:* By ensuring the application only ever writes to one source of truth (the main database). We then use a tool to read the database's Write-Ahead Log (WAL) and automatically stream those changes to the cache or search engine in the background.
3. **"What is Debezium?"**
   *Answer:* Debezium is the most popular open-source tool for CDC. It connects directly to a database's transaction log (like Postgres' WAL or MySQL's binlog) and streams the row-level changes into an event streaming platform like Apache Kafka.
