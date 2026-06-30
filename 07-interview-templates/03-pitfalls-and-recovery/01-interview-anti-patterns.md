> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** The most common reasons smart people fail System Design interviews, and how to avoid them.
>
> **Key concepts:**
> - **Requirements:** Don't start drawing boxes immediately. Ask questions first.
> - **Architecture:** Don't say "I'll use Kafka and Microservices" unless you can explain *why* you need them.
> - **Deep Dives:** Don't just say "Eventual Consistency." Explain *what that means for the user* (e.g., "They might see a stale Like count for 2 seconds").
> - **Communication:** Don't think silently for 3 minutes. The interviewer cannot read your mind.
>
> **Key takeaway:** System Design interviews test your communication as much as your tech skills. Avoid these anti-patterns to show you are a Senior Engineer.

---
module: 07-interview-templates
topic: Interview Anti-Patterns
status: unread
tags: [07-interview-templates, interview, anti-patterns, cheat-sheets]
---
# Interview Anti-Patterns: How to Fail

> **These are the actual reasons candidates get rejected. Most are not knowledge gaps — they are communication failures.**

---

## 🚩 1. The "Eager Beaver" (Requirements Failures)

**The Mistake:** The interviewer says "Design Twitter." You immediately grab a marker and draw a Database. 

**Why it's bad:** You don't know if they want Twitter for 100 people or 100 Million people. You don't know if they care about the "Like" button or the "Search" bar. You are designing blind.

**The Fix:** Spend the first 5 minutes asking questions.
- *"How many users do we have?"*
- *"Are we focusing on the News Feed, or the DM system?"*
- *"Is it okay if a Tweet takes 2 seconds to show up on someone else's screen?"*

---

## 🚩 2. The "Buzzword Bingo Player" (Architecture Failures)

**The Mistake:** You say, *"I will use Kafka, Kubernetes, Elasticsearch, and Cassandra."* 

**Why it's bad:** If you throw 10 expensive, complex technologies at a problem that could be solved by a simple PostgreSQL database, the interviewer will think you are a "Resume Driven Developer." You over-engineer things.

**The Fix:** Start stupidly simple. *"I will use a single Web Server and a SQL Database."* Then, only add complex things when you hit a bottleneck. *"Oh, we have 10 Million users? A single SQL DB will crash. Now we need to Shard it."*

---

## 🚩 3. The "Silent Monk" (Communication Failures)

**The Mistake:** You stare at the whiteboard in complete silence for 3 minutes while you try to solve a hard math problem in your head.

**Why it's bad:** The interviewer is grading you on your thought process. If you are silent, they assume you have no idea what to do.

**The Fix:** Think out loud. *"Okay, I'm trying to decide between caching by User ID or by Post ID. If I do User ID, it's easier to find all their posts, but if they are a Celebrity, the cache will crash. So I'll go with Post ID."*

---

## 🚩 4. The "Magician" (Deep Dive Failures)

**The Mistake:** You draw a box that says "Notification Service" and draw an arrow to a box that says "Users". 

**Why it's bad:** You didn't explain *how* the magic happens. What if the user's phone is turned off? Does the notification get deleted? Does it wait? How does it retry? 

**The Fix:** For every arrow you draw, explain what happens when the arrow breaks. *"If the user is offline, the Notification Service puts the message in a queue. It will try again every 5 minutes until the phone connects."*

---

## 🚩 5. The "N+1 Nightmare" (Database Failures)

**The Mistake:** You design a system that loads a News Feed of 50 posts. Then, it asks the database for the author's profile picture for Post 1, then Post 2, then Post 3... 50 separate times.

**Why it's bad:** This is the most common bug that crashes production servers. It's called the N+1 problem.

**The Fix:** Batch your queries! *"I will fetch all 50 posts, make a list of the 50 authors, and ask the database for ALL 50 profile pictures in one single query."*

---

## 🚩 6. The "Single Point of Failure"

**The Mistake:** You draw an architecture for 10 Million users, but you only have ONE Load Balancer and ONE Database.

**Why it's bad:** If the janitor unplugs the database to vacuum the floor, your entire company goes bankrupt.

**The Fix:** Redundancy. *"I will have a Primary Database for writing data, and 3 Read Replicas. If the Primary dies, one of the Replicas will automatically take over."*

---

## 🎤 Phrase to use in an interview:

> "Instead of jumping straight into microservices, I'd like to start with a simple monolith and a SQL database to establish the core data flow. Then, we can calculate our expected scale and introduce complexity — like caching or sharding — only where the math proves we have a bottleneck."
