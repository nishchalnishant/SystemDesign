> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** How to stop two servers from accidentally deleting each other's work.
>
> **Key topics:**
> - **The Problem:** A race condition. If two servers try to buy the exact same airplane ticket at the exact same millisecond, the database might accidentally sell the ticket twice.
> - **The Solution (Distributed Lock):** A digital "Bathroom Key." Only the person holding the key is allowed to buy the ticket. 
> - **The Danger (Deadlocks):** What happens if a server grabs the key, and then instantly dies? The key is lost forever, and nobody can ever buy that ticket again.
> - **The Fix (TTL / Leases):** Adding a timer to the key. If the server doesn't return the key in 10 seconds, the key magically teleports back to the front desk.
> - **Tools:** Redis (Redlock) or Apache ZooKeeper are the industry standards for managing these keys.
>
> **Key takeaway:** Whenever you have multiple servers touching the exact same data, you must use a Distributed Lock to prevent chaos.

---
module: 02-building-blocks
status: unread
tags: [02-building-blocks, system-design, coordination]
---
# Distributed Locks - System Design Guide

> This guide explains how to prevent race conditions across multiple servers using simple analogies.

---

## 🤷‍♂️ Why Should I Care?

Imagine a gas station bathroom. It only has one toilet. 
If two people walk in at the exact same time, chaos ensues. 
To fix this, the gas station uses a **Lock**. The cashier holds a giant wooden block with a single brass key attached to it. 
If Person A wants to use the bathroom, they must ask the cashier for the key. While Person A has the key, Person B must stand in the lobby and wait. When Person A returns the key, Person B can go.

In System Design, this is exactly how we prevent **Race Conditions**. 

If Server A and Server B both try to buy the last seat on an airplane at the exact same millisecond, the database might get confused and sell it twice. 
To fix this, we create a **Distributed Lock**. Before a server is allowed to buy the ticket, it must ask a central server (like Redis or ZooKeeper) for the "Key" to that specific seat.

---

## 💀 The Deadlock Problem (Losing the Key)

> **💡 Analogy:** Person A takes the bathroom key, walks into the bathroom, locks the door... and then has a heart attack and dies. 

If a server asks Redis for the Lock, but then the server suddenly loses power and crashes, the Lock is never returned! 
Server B, Server C, and Server D will stand in the lobby, waiting forever. The airplane seat will never be sold, because the system is permanently frozen. This is called a **Deadlock**.

### The Fix: TTL (Time to Live)
To fix a deadlock, we put a timer on the lock (called a Lease or a TTL).
When Server A grabs the lock, Redis says: "You have 10 seconds to use this. If you don't give it back in 10 seconds, I am deleting your lock and giving a new key to Server B."

Now, if Server A crashes, the system only freezes for a maximum of 10 seconds before fixing itself!

---

## 🧠 The Fencing Token (The Edge Case)

Wait, what if Server A *didn't* crash? What if Server A was just really, really slow?

> **💡 Analogy:** 
> 1. Server A takes the key. The 10-second timer starts.
> 2. Server A falls asleep for 15 seconds.
> 3. The timer expires! Redis gives a new key to Server B.
> 4. Server B buys the airplane ticket.
> 5. Server A suddenly wakes up. It still thinks it has the key! It walks into the Database and buys the airplane ticket too.
> 
> **Result:** We just sold the ticket twice. The lock failed!

### The Fix: Fencing Tokens
To fix this, Redis gives out a sequential number (a Fencing Token) along with the lock. 
- Server A gets Lock **#1**. (Falls asleep).
- Server B gets Lock **#2**. (Buys the ticket). 
- Server A wakes up and tries to buy the ticket with Lock #1. 
- The Database says: "Wait a minute! I already processed a ticket with Lock #2! Your Lock #1 is too old. Access Denied!"

---

## 🛠️ How do we actually build this?

You should almost never write your own Distributed Lock code from scratch. It requires insanely complex math to get right. 

In a system design interview, if you need a distributed lock, you should just say:
**"I will use Redis with the Redlock algorithm."** or **"I will use Apache ZooKeeper."**

Both of these tools are pre-built to handle locks, TTLs, and Fencing Tokens perfectly.

---

## 🎤 Interview Questions to Practice

1. **"What is a Race Condition?"**
   *Answer:* It happens when two concurrent processes try to read and write the exact same piece of data at the exact same time, causing the final state of the data to be corrupted or incorrect.
2. **"Why do we need a TTL (Time-To-Live) on a Distributed Lock?"**
   *Answer:* To prevent Deadlocks. If a client acquires a lock and then crashes before releasing it, the lock would be held forever, freezing the system. A TTL ensures the lock will automatically expire and be released after a set amount of time.
3. **"What is a Fencing Token and why is it necessary?"**
   *Answer:* A Fencing Token is a monotonically increasing number attached to a lock. It solves the edge case where a client is paused (e.g., during a long Garbage Collection cycle), its lock's TTL expires, a second client gets the lock, and then the first client wakes up and tries to execute its write. The storage system uses the Fencing Token to reject the older client's write.

---

## Applied In

This concept is used by **9 problems** in this repo — a representative selection:

**High-Level Design**

- [Design a Booking System (Hotels / Flights)](../../05-hld-problems/01-easy/booking-system.md)
- [Design a Rate Limiter](../../05-hld-problems/01-easy/rate-limiter.md)
- [Design a Unique ID Generator](../../05-hld-problems/01-easy/unique-id-generator.md)
- [Design a Distributed Job Scheduler](../../05-hld-problems/03-hard/distributed-job-scheduler.md)
- [Design a Hotel Booking System (Booking.com)](../../05-hld-problems/03-hard/hotel-booking.md)
- [Design a Payment System](../../05-hld-problems/03-hard/payment-system.md)
- [Design a Ride-Sharing Service (Uber)](../../05-hld-problems/03-hard/ride-sharing.md)
- [Design a Stock Exchange](../../05-hld-problems/03-hard/stock-exchange.md)
- …and 1 more

