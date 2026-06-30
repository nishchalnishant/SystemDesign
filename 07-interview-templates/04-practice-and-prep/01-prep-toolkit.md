> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** How to actually study for a System Design interview without losing your mind. 
>
> **Key concepts:**
> - **The 4-Week Plan:** Don't cram. Week 1 is Basics. Week 2 is Advanced. Week 3 is Deep Dives. Week 4 is Practice.
> - **Grading Yourself:** If you can't explain *why* you chose a database, give yourself a failing grade on that practice run.
> - **Company Differences:** Amazon cares about "Leadership Principles" (Customer obsession). Meta cares about massive scale (Billions of users). Google cares about deep algorithms (How does the database *actually* work?).
>
> **Key takeaway:** You cannot memorize System Design. You have to understand the "Why". Use this toolkit to structure your studying so you don't panic on interview day.

---
module: 07-interview-templates
topic: Prep Toolkit
status: unread
tags: [07-interview-templates, interview-prep, study-guide, cheat-sheets]
---
# The Prep Toolkit

> **How to study for System Design without cramming.**

---

## 📅 The 4-Week Study Plan

If you try to read everything in one weekend, you will forget it all by Monday. You need time for your brain to process the concepts.

### Week 1: The Basics (Tier 0 & Tier 1)
- **What to study:** Load Balancers, Caching (Redis), CDNs, Database Sharding, and SQL vs NoSQL.
- **The Goal:** You should be able to design a simple app (like a basic Twitter clone) for 100,000 users.

### Week 2: The Hard Stuff (Tier 2 & Tier 3)
- **What to study:** Message Queues (Kafka), Microservices, API Gateways, and Database Replication.
- **The Goal:** You should be able to explain how to fix the app when it crashes or gets too busy.

### Week 3: The Deep Dives (Tier 4 & Tier 5)
- **What to study:** How Redis actually works under the hood. How Cassandra is different from PostgreSQL. How to do Multi-Region (Global) scaling.
- **The Goal:** You should be able to survive when the interviewer says, *"Okay, but what if we have 1 Billion users?"*

### Week 4: Practice & Mock Interviews
- **What to study:** Nothing new! 
- **The Goal:** Practice explaining your thoughts out loud. Find a friend (or a mirror) and spend 45 minutes designing Uber, Ticketmaster, or YouTube.

---

## 📝 How to Grade Yourself (Self-Assessment)

After a practice run, score yourself out of 4 points on these 3 rules:

**1. Did I ask questions first?**
- Score 1: I started drawing immediately. (Fail)
- Score 4: I asked 3 smart questions about how many users we have before I drew anything. (Pass)

**2. Did I explain my choices?**
- Score 1: "I will use Kafka." (Fail)
- Score 4: "I will use a Message Queue like Kafka because we need to process videos in the background so the user doesn't have to wait on a loading screen." (Pass)

**3. Did I talk about failure?**
- Score 1: "The app works perfectly." (Fail)
- Score 4: "If the Database crashes, the Load Balancer will automatically switch to the Backup Database." (Pass)

---

## 🏢 Company-Specific Secrets

Different companies want different things. Play their game.

- **Amazon:** They care about "Leadership Principles". They want to see that you care about the Customer. Focus on making the app reliable so the customer is never unhappy. Say things like, *"I will add an alarm that pages an engineer if the latency gets too high."*
- **Meta (Facebook):** They care about SPEED and MASSIVE SCALE. They want to know you can build an app for 3 Billion people. Talk about Caching, CDNs, and Data Sharding immediately. 
- **Google:** They care about Deep Computer Science. They will ask you *exactly* how a Database saves data to a hard drive. You need to know algorithms (like B-Trees and Consistent Hashing).
- **Apple:** They care about Privacy and Hardware. If you are designing an Apple app, mention End-to-End Encryption and doing calculations on the iPhone itself rather than sending private data to a server.

---

## 🔒 Security Basics (Cheat Sheet)

If the interviewer asks, *"How is this secure?"*, mention these 3 things:
1. **HTTPS (TLS):** All data sent over the internet is encrypted. Hackers at Starbucks cannot steal passwords.
2. **OAuth (Tokens):** We don't store user passwords. We use secure tokens (JWTs) that expire every 15 minutes.
3. **Database Encryption:** If a thief breaks into the data center and steals the physical hard drive, they still can't read the data because it is encrypted at rest (KMS).

---

## 🎤 Phrase to use in an interview:

> "Before we start drawing the architecture, I'd like to ask three clarifying questions to make sure I'm solving the right problem for the customer. First, what is our expected Daily Active User count? Second, what is our latency requirement? And third, is this a read-heavy or write-heavy system?"
