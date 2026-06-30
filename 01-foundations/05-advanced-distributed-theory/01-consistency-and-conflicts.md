> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** How to handle data when multiple servers try to update the exact same thing at the exact same time.
>
> **Key topics:**
> - **The Problem:** The speed of light is too slow. Two users can edit a file before the servers have time to talk to each other.
> - **Strong Consistency:** Forcing everyone to wait in a single-file line (Safe but slow).
> - **Eventual Consistency:** Letting everyone edit freely, and cleaning up the mess later (Fast but messy).
> - **Last Write Wins (LWW):** The lazy way to fix conflicts. Just look at the timestamp and delete the older one.
> - **Vector Clocks:** A smart way to track *who* edited *what* and *when*, so you don't accidentally delete someone's work.
>
> **Key takeaway:** In a distributed system, conflicts are guaranteed by physics. You can't prevent them, you can only choose how you want to resolve them.

---
module: 01-foundations
status: unread
tags: [01-foundations, system-design, foundations]
---
# Consistency & Conflicts - System Design Guide

> This guide explains why data gets out of sync in distributed systems, and how engineers fix it using simple analogies.

---

## 🤷‍♂️ Why Should I Care?

Imagine you and your friend share a Google Doc. You both live in New York, so when you type a word, your friend sees it instantly. 

Now imagine you live in New York and your friend lives in Tokyo. You both try to edit the exact same sentence at the exact same millisecond. 
Because of the physical limits of the speed of light, it takes 150 milliseconds for a signal to travel from NY to Tokyo. 

During that 150 milliseconds, both of you think you successfully edited the sentence. But you typed two completely different things! Which edit does the Google server save? Whose work gets deleted?

This is called a **Data Conflict**. When you build massive apps that run on servers all over the world, data conflicts are guaranteed by the laws of physics. If you don't know how to handle them, you will accidentally overwrite and delete user data constantly.

---

## 🚦 Strong vs Eventual Consistency

How do we stop conflicts from happening? We have two choices:

### 1. Strong Consistency (The DMV Line)
> **💡 Analogy:** Waiting in line at the DMV. There is only one clerk. Only one person can talk to the clerk at a time. It is perfectly organized, nobody talks over each other, but the line takes 3 hours. 

- **How it works:** If you want to update a piece of data, the system locks it. Nobody else in the world can read or write that data until you are completely finished.
- **The Trade-off:** It is perfectly safe (no conflicts!), but it is terribly slow.
- **Use for:** Bank Account Balances, Inventory for limited concert tickets.

### 2. Eventual Consistency (The Messy Brainstorm)
> **💡 Analogy:** A brainstorming meeting where 5 people are shouting ideas and writing on the whiteboard at the exact same time. It's incredibly fast, but sometimes two people write over each other. At the end of the meeting, the boss has to look at the messy whiteboard and organize it.

- **How it works:** The system lets everyone update data instantly without waiting. It promises to "eventually" sync all the servers together in the background a few seconds later.
- **The Trade-off:** It is lightning fast, but it *guarantees* conflicts will happen.
- **Use for:** Instagram Likes, YouTube view counts, Shopping Cart items.

---

## ⚔️ How to Resolve Conflicts

If you choose Eventual Consistency, you *will* get conflicts. How does the server decide who wins?

### 1. Last Write Wins (LWW)
> **💡 Analogy:** Two kids are fighting over the TV remote. The mom walks in and says, "Whoever touched the remote most recently gets to pick the channel."

- **How it works:** The database simply looks at the timestamp of the two edits. Whoever clicked "Save" last wins. The other edit is permanently deleted.
- **The Problem:** Clocks on computers are never perfectly synced. Server A's clock might be 5 milliseconds faster than Server B's clock. This means the database might accidentally delete the newer edit just because a server's clock was wrong! (This is called Data Loss).
- **Use for:** Profile pictures (it doesn't matter if an old picture gets overwritten, just use the newest one).

### 2. Vector Clocks (The Version Tracker)
> **💡 Analogy:** Working on a school group project in Microsoft Word. Instead of just overwriting the file, you name it `Project_V1_Alice`, and your partner names it `Project_V1_Bob`. The teacher sees both versions and merges them into `Project_V2_Final`.

- **How it works:** Instead of relying on timestamps (which can be wrong), the database gives every single edit a version number. If the database receives two different `Version 1` edits at the same time, it realizes there is a conflict. 
- **The Catch:** The database doesn't know how to merge them. So, it hands *both* versions back to the application code, and forces the application to merge them. (This is how Amazon's Shopping Cart works — if you add a book on your phone, and a shirt on your laptop while offline, Amazon merges them so you don't lose either item).

---

## 🧠 The "Split-Brain" Problem

What happens if the network cable connecting your New York server and your Tokyo server gets cut? 

> **💡 Analogy:** A married couple is running a restaurant. The husband is in the kitchen (Server A), the wife is at the front desk (Server B). Suddenly, their walkie-talkies break. They can't talk to each other. 
> 
> A customer walks in and orders the last slice of cake from the wife. At the exact same time, a waiter in the kitchen orders the last slice of cake from the husband. Because they can't communicate, they both say "Yes!" and sell the same slice of cake twice. 

This is called **Split-Brain**. The system is split in half, and both halves think they are in charge. 

### How to fix it: Quorum (Majority Rules)
To fix Split-Brain, you must always have an *odd number* of servers (e.g., 3, 5, or 7). 
If the network breaks, the servers take a vote. Whichever group has the **majority** (more than half) of the servers stays online. The minority group immediately shuts itself down to prevent selling double tickets.

---

## 🎤 Interview Questions to Practice

1. **"What is the difference between Strong and Eventual Consistency?"**
   *Answer:* Strong Consistency forces everyone to wait in line so the data is perfectly accurate (like a bank balance). Eventual consistency lets everyone edit freely for speed, but the data might be temporarily inaccurate (like a YouTube view count).
2. **"Why is 'Last Write Wins' dangerous?"**
   *Answer:* Because it relies on the physical clocks of different servers. If one server's clock is drifting by just a few milliseconds, it can accidentally overwrite and delete newer data.
3. **"How do you prevent a Split-Brain scenario?"**
   *Answer:* You use an odd number of servers (like 3 or 5) and enforce a "Quorum." If the network splits, only the group that can communicate with the majority of the servers is allowed to accept writes. The isolated servers must stop accepting traffic.
