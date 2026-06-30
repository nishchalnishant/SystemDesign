> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** How to run your application in multiple countries at the exact same time.
>
> **Key topics:**
> - **The Problem:** If all your servers are in New York, users in Australia will experience massive lag (Latency). And if a hurricane hits New York, your global business is entirely offline.
> - **The Solution:** Copy-pasting your entire architecture into multiple Data Centers around the world.
> - **Active-Passive (The Backup Generator):** New York does 100% of the work. London just sits there, empty, waiting. If New York explodes, you flip a switch and London takes over. (Cheap, safe, but Australians still have lag).
> - **Active-Active (The Two Kitchens):** New York and London are both working at the same time. Australians go to London, Americans go to New York. (Extremely fast, but incredibly hard to keep their databases synced).
> - **Data Sovereignty:** Legal rules. Europe says European user data is legally not allowed to leave Europe. You *must* have a European data center.
>
> **Key takeaway:** Global distribution solves both Latency (speed) and Disaster Recovery (survival), but it requires Geo-Routing (a smart DNS) to send users to the right place.

---
module: 03-scaling
status: unread
tags: [03-scaling, system-design, scaling]
---
# Global Distribution - System Design Guide

> This guide explains how to run an app in multiple continents using simple analogies.

---

## 🤷‍♂️ Why Should I Care?

Imagine you open an incredibly popular theme park in Florida. 
People in Japan really want to visit, but it takes them 15 hours to fly there. (This is **Latency**). 
Also, if a massive hurricane hits Florida, the park closes, and you make $0. (This is a **Disaster**).

To fix both problems, you build a second, identical theme park in Tokyo. 
Now, Japanese users just drive down the street (Zero Latency). And if a hurricane hits Florida, the Tokyo park stays open! 

In System Design, this is called **Global Distribution**. Instead of putting all your servers in `us-east-1` (Virginia), you put a copy of your servers in `eu-west-1` (Ireland) and `ap-northeast-1` (Tokyo). 

---

## 🌍 How do users know where to go? (Geo-Routing)

If you have 3 theme parks, how does a user know which one to drive to?

You use a smart GPS (A **Geo-Routing DNS** like AWS Route53). 
When a user types `www.netflix.com` into their browser, the DNS looks at their physical location. 
- If the user is in Paris, the DNS replies: "Go to the Ireland servers."
- If the user is in California, the DNS replies: "Go to the Virginia servers."

---

## 🏗️ Active-Passive vs Active-Active

There are two ways to run multiple data centers. 

### 1. Active-Passive (Disaster Recovery)
> **💡 Analogy:** A hospital has a giant diesel generator in the basement. It sits turned off for 99% of the year. If the city loses power, the generator turns on.

- **How it works:** All global traffic goes to Virginia. Your servers in Tokyo are turned on, but they do absolutely nothing except quietly download database backups from Virginia. 
- **The Failover:** If Virginia gets hit by a meteor, your DevOps team clicks a button. The DNS is updated to send all global traffic to Tokyo. 
- **Pros:** Very easy to program. The databases never argue because only Virginia is writing data.
- **Cons:** You are paying millions of dollars for Tokyo servers that sit empty 99% of the time. And Japanese users still experience lag because they are forced to connect to Virginia.

### 2. Active-Active (The Holy Grail)
> **💡 Analogy:** McDonald's. There is a McDonald's in NY and a McDonald's in Tokyo. They are both open at the exact same time, serving local customers. 

- **How it works:** Virginia handles US traffic. Tokyo handles Asian traffic. Both data centers are at 100% capacity. 
- **The Failover:** If Virginia gets hit by a meteor, the DNS just routes the US users to Tokyo. Tokyo gets a bit crowded, but the business survives. 
- **Pros:** Japanese users get lightning-fast speeds. No wasted money on empty servers.
- **Cons:** It is a database nightmare. If a user in Tokyo updates their profile picture, and a user in Virginia updates the *same* profile picture at the exact same millisecond, the two databases will fight over which one is correct. (You need a Multi-Primary database setup to fix this).

---

## ⚖️ Data Sovereignty (The Legal Problem)

Sometimes you don't scale globally for speed. You do it because you will be arrested if you don't.

**GDPR** is a law in the European Union. It states that data belonging to a European citizen must be physically stored on a hard drive located *inside* Europe. 
If your only database is in Virginia, and a German user signs up, you are breaking the law. 

To solve this, you must build an Active-Active data center in Frankfurt, Germany. You configure your database so that European records are mathematically locked to the Frankfurt shards and never replicate to the United States. 

---

## 🎤 Interview Questions to Practice

1. **"What is the difference between an Active-Active and an Active-Passive multi-region setup?"**
   *Answer:* In Active-Passive, only one primary region serves live traffic, while the secondary region acts purely as a backup, constantly syncing data but remaining idle until a disaster occurs. In Active-Active, both regions serve live traffic simultaneously to their local users, providing lower latency globally but introducing complex database conflict resolution issues.
2. **"How do you route a user in London to your European data center instead of your US data center?"**
   *Answer:* By using Geo-Routing at the DNS level (e.g., AWS Route 53). The DNS provider checks the IP address of the incoming request, determines the geographic location, and returns the IP address of the data center physically closest to the user.
3. **"What is Data Sovereignty and how does it affect system design?"**
   *Answer:* Data Sovereignty refers to laws (like GDPR) that require a citizen's digital data to be physically stored within their own country or region. It forces system designers to implement regional data partitioning, ensuring certain database rows never replicate across international borders.
