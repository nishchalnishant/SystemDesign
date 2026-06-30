> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** How to upgrade an app as it gets more popular. 
>
> **Key concepts:**
> - **1,000 users (Startup):** Put everything on one computer. (It's fine, really).
> - **10,000 users:** The database gets busy. Move it to its own computer.
> - **100,000 users:** People are complaining it's slow. Add a Load Balancer, a Cache (Redis), and a CDN.
> - **1,000,000 users:** The database is literally on fire from too many writes. Break the database into pieces (Sharding) and add a Message Queue (Kafka) so users aren't waiting for slow jobs.
> - **10,000,000 users:** The code is too big for one team. Break the app into Microservices. 
>
> **Key takeaway:** Never start an interview by saying "I will build 50 microservices and use Kafka." If the interviewer asks for a small internal tool, you will fail for over-engineering. Always match the solution to the specific bottleneck.

---
module: 07-interview-templates
status: unread
tags: [07-interview-templates, system-design, interview-templates, cheat-sheets]
---
# Architecture by Scale

> **"What breaks first at this scale?"** This is the only question that matters.

---

## 🐣 Tier 0: 1,000 Users (The MVP)

**The Setup:** You bought one computer on AWS (EC2). You put the Web Server code and the Database on that exact same computer. 
**The Bottleneck:** Nothing. It works perfectly. 

- **Should I use Microservices?** No.
- **Should I use a Cache?** No.
- **What breaks first?** If your app gets popular overnight, the Web Server and the Database will fight each other for the computer's RAM, and it will crash.

---

## 🐥 Tier 1: 10,000 Users (The Split)

**The Problem:** The Web Server and the Database are fighting for resources on the same machine.
**The Solution:** Divorce them. 

- Move the Database to its own dedicated, powerful computer (like AWS RDS). 
- Leave the Web Server on the original computer. 
- Now, if the Web Server gets busy, you can just buy *more* Web Servers without touching the Database.

---

## 🐓 Tier 2: 100,000 Users (The Read Heavy Phase)

**The Problem:** You have 10 Web Servers now, all asking the Database for the same 5 popular Tweets over and over again. The Database is sweating. 
**The Solution:** Add a Cache and a CDN.

1. **Load Balancer:** Put a traffic cop in front of your 10 Web Servers to distribute the users evenly.
2. **Cache (Redis):** Put a sticky note (Redis) in front of the Database. When someone asks for a Tweet, check the sticky note first. If it's there, return it instantly (0.1ms). If not, ask the Database, then write it on the sticky note for the next person.
3. **CDN:** Stop serving images from your Web Servers! Put all photos and videos on a CDN (like Cloudflare) so users download them from a server in their own city.

---

## 🦅 Tier 3: 1,000,000 Users (The Write Heavy Phase)

**The Problem:** The Cache fixed the "Read" problem. But now, 10,000 people are clicking "Like" every second. A single SQL database physically cannot write 10,000 things a second. It crashes.
**The Solution:** Sharding & Queues.

1. **Sharding the DB:** Take your giant database and smash it into 4 pieces. Users A-F go to DB #1, G-M go to DB #2, etc. Now each database only handles 2,500 writes a second. (Warning: This makes the code very complicated).
2. **Message Queues (Kafka / SQS):** If a user uploads a video, do NOT make them stare at a loading screen while you process it. Put a ticket in a Queue, tell the user "We are processing it!", and let background worker computers handle it when they have free time.

---

## 🐉 Tier 4: 10,000,000+ Users (The Corporate Phase)

**The Problem:** You have 500 engineers working on the same codebase. Every time someone adds a feature, they break something else. Users in Japan are complaining the app is slow because the servers are in New York.
**The Solution:** Microservices & Multi-Region.

1. **Microservices:** Break the code into 50 tiny apps. The "Payments Team" has their own code and their own database. If the "Search Team" crashes their server, Payments still work! 
2. **Multi-Region:** Put a copy of your entire system in Tokyo, a copy in London, and a copy in New York. Route users to the closest one. (Warning: Keeping the databases synced across the ocean is the hardest problem in computer science).

---

## 🎤 Phrase to use in an interview:

> "I wouldn't start with Microservices and Kafka for 10,000 users, as the operational complexity would slow down the team. I would start with a simple Monolith and a managed PostgreSQL database. Once we hit 100,000 users and reads become the bottleneck, I would introduce a Redis Cache. I would only introduce Database Sharding or Message Queues when our write-throughput physically exceeds what a single primary database can handle."
