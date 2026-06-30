> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** How to add or remove servers from your database cluster without having to move millions of users around.
>
> **Key topics:**
> - **The Problem:** Standard hashing uses `User_ID % Number_of_Servers`. If you have 4 servers, and you add a 5th server, the math changes for *every single user*. You have to move 99% of your data to new servers, which crashes the system.
> - **Consistent Hashing (The Solution):** Instead of standard math, we put the servers on a giant circle (like a clock). 
> - **How it works:** To find where a user belongs, you drop them on the clock, and they walk clockwise until they hit a server. 
> - **Adding a server:** If you add a new server to the clock, only the users immediately behind it have to move. 90% of the data stays exactly where it is!
> - **Virtual Nodes:** To prevent one server from getting stuck with a huge slice of the clock, we create "fake" servers (Virtual Nodes) to spread the load perfectly evenly.
>
> **Key takeaway:** If you are designing a distributed system (like DynamoDB or Cassandra) where you might need to add or remove servers in the future, you *must* use Consistent Hashing.

---
module: 02-building-blocks
status: unread
tags: [02-building-blocks, system-design, data-partitioning]
---
# Consistent Hashing - System Design Guide

> This guide explains how to add servers without breaking your database using simple analogies.

---

## 🤷‍♂️ Why Should I Care?

Imagine you have 4 servers. You use standard Hash Sharding to split your users evenly. 
The math is simple: `User_ID % 4`. 
- User 1 goes to Server 1. 
- User 2 goes to Server 2. 
- User 5 goes to Server 1.

Everything is great. But then, your app goes viral. You need to add a 5th Server.
Suddenly, your math changes to `User_ID % 5`.
Now:
- User 1 goes to Server 1 (Okay, fine).
- User 2 goes to Server 2 (Fine).
- User 5 goes to **Server 0**. Wait! User 5 used to be on Server 1! 

Because you changed the math, **99% of your users are now pointing to the wrong server.** To fix this, you have to physically move 99% of your data to new hard drives. This is called a **Rehashing Storm**. It takes hours, and your database will be completely offline while it happens.

**Consistent Hashing** is a genius math trick that fixes this. When you add a 5th server, it guarantees that you only have to move a tiny fraction of your data.

---

## 🕒 How it works (The Clock)

> **💡 Analogy:** Imagine a giant clock on the wall, numbered 1 to 100.
> 
> You have 4 Servers (A, B, C, D). You place them around the clock.
> - Server A is at 12:00 (Position 0)
> - Server B is at 3:00 (Position 25)
> - Server C is at 6:00 (Position 50)
> - Server D is at 9:00 (Position 75)
>
> **The Rule:** When a User joins, you run a math formula that gives them a random number between 1 and 100. You put the User on the clock, and they walk **clockwise** until they bump into a Server. That is their home!
> 
> - User gets #10. They walk clockwise and bump into Server B (#25). 
> - User gets #60. They walk clockwise and bump into Server D (#75).

### What happens when we add a server?
Your app goes viral. You buy Server E. 
You place Server E at 4:30 (Position 35). 

Who has to move?
- Any user between #26 and #35 used to walk all the way to Server C (#50). Now, they bump into Server E (#35) first! So those users move to Server E.
- **Every single other user on the clock stays exactly where they are.** 

Instead of moving 99% of your data, you only moved 10%! The database stays online, and everything is perfectly safe.

---

## 👻 Virtual Nodes (Fixing the uneven slices)

There is one flaw with the Clock. 
When we added Server E at Position #35, Server E only got a tiny slice of the clock (from #26 to #35). Meanwhile, Server D still owns a massive slice (from #51 to #75). 
Server D is going to do way more work than Server E! 

**The Fix:** Virtual Nodes. 
> **💡 Analogy:** Instead of Server E just having one spot on the clock, we create 100 "Fake" Server E's. We place these 100 fake servers randomly all over the clock. When a user bumps into *any* of the fake Server E's, they are sent to the real Server E. 

By creating hundreds of Virtual Nodes for every physical server, the slices of the clock become perfectly, mathematically even. 
*(Bonus: If a server is twice as powerful as the others, you just give it twice as many Virtual Nodes!)*

---

## 🎤 Interview Questions to Practice

1. **"What problem does Consistent Hashing solve?"**
   *Answer:* It solves the "Rehashing Storm" problem. In standard modulo hashing (`Key % N`), changing the number of servers `N` causes almost all keys to be remapped to new servers, crippling the system. Consistent Hashing ensures that when a server is added or removed, only `K/N` keys need to be moved (where K is total keys, and N is the number of servers).
2. **"How does Consistent Hashing work?"**
   *Answer:* It places both the servers and the data keys onto a conceptual "Hash Ring" (a circle). To find which server owns a piece of data, you find the data's position on the ring and move clockwise until you find the first server.
3. **"Why do we use Virtual Nodes in Consistent Hashing?"**
   *Answer:* To balance the load. Without virtual nodes, the physical servers might be unevenly distributed around the ring, meaning some servers get massive chunks of data while others get very little. Virtual nodes assign dozens of random spots on the ring to each physical server, ensuring a perfectly even statistical distribution.
