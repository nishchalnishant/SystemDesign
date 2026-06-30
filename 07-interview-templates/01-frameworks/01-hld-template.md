> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** The ultimate cheat sheet for passing a 45-minute High-Level Design (HLD) interview. 
>
> **Key concepts:**
> - **Phase 1: Requirements (The Blueprint):** Before you build a house, ask how many people will live in it. Never start drawing boxes immediately. 
> - **Phase 2: Capacity Estimation (The Math):** Figure out how much traffic you will get. (Are you building a small driveway or a 10-lane highway?)
> - **Phase 3: API Design (The Menu):** What exact commands can users send to your app? (Like a restaurant menu).
> - **Phase 4: Database Schema (The Filing Cabinet):** Decide exactly how you will store the data. 
> - **Phase 5: Architecture (The Factory Floor):** Draw the boxes (Load Balancers, Servers, Databases).
> - **Phase 6: Deep Dives (The Magnifying Glass):** Pick the hardest technical problem (like the "Celebrity Problem" on Twitter) and solve it.
>
> **Key takeaway:** Pacing is everything. If you spend 20 minutes doing Math, you will run out of time and fail. Use this template to structure your 45 minutes perfectly.

---
module: 07-interview-templates
status: unread
tags: [07-interview-templates, system-design, interview-templates]
---
# HLD Interview Framework (45-60 min)

> This guide provides a bulletproof 6-step template for System Design interviews, using simple analogies to help you memorize the flow.

---

## 🤷‍♂️ The Mental Model

Imagine you are hired to build a skyscraper. 
You don't start your first day by arguing about what brand of nails to buy. You start by asking: "How many floors? Is this for offices or apartments? What is the budget?"

In a System Design interview, the interviewer is your Client. They will give you an incredibly vague prompt like: "Build Twitter." 
**Do not touch the whiteboard.** 
Your job is to spend the first 10 minutes extracting exactly what the client wants, before you draw a single box. 

The interviewer is grading you on 4 things:
1. Do you ask the right questions?
2. Do you understand the trade-offs? (e.g., "If we make it faster, it will cost more money.")
3. Do you know standard patterns? (e.g., Load Balancers, Caching).
4. Can you zoom in on a hard problem if they ask you to?

---

## ⏱️ The 45-Minute Timeline

| Phase | Time | What You Do |
|-------|------|-------------|
| **1. Requirements** | 0-10 min | Ask questions. What are we building? |
| **2. Math (Estimates)** | 10-15 min | Calculate traffic and storage limits. |
| **3. API Design** | 15-20 min | Define the URLs/Endpoints. |
| **4. Database Design** | 20-25 min | Pick SQL or NoSQL and draw the tables. |
| **5. Architecture** | 25-35 min | Draw the actual system (Boxes and Arrows). |
| **6. Deep Dives** | 35-45 min | Solve the hardest bottleneck in your design. |

---

## 🏗️ Phase 1: Requirements (0-10 min)

### Functional Requirements (What must it do?)
Ask for the 3-5 core features. Stop when you have enough. 

> **💡 Example (Twitter):**
> - **Must Have:** Post a tweet, Follow users, View timeline.
> - **Out of Scope (Skip):** Direct messages, Search, Ads.

### Non-Functional Requirements (PASS-R)
This is where you define how *good* the system must be. 

- **Performance:** How fast? (e.g., "The timeline must load in under 200 milliseconds.")
- **Availability:** Can it ever go down? (e.g., "99.99% uptime.")
- **Scale:** How many users? (e.g., "10 million daily users.")
- **Security:** Do they need to log in? 
- **Reliability:** Is it okay to lose data? (e.g., "We can never lose a tweet, but it's okay if a 'Like' gets lost.")

---

## 🧮 Phase 2: Capacity Estimation (10-15 min)

*(Note: If the interviewer tells you to skip the math, skip it!)*

This is where you figure out if you are building a small driveway or a massive highway. 
You need to calculate **QPS (Queries Per Second)**.

> **💡 Analogy:** If 500,000 people visit a store every day (86,400 seconds in a day), you can expect about 6 people to walk through the door every second. (500,000 / 86,400 = ~6 QPS).

- **Read vs Write Ratio:** Do people read more or write more? On Twitter, 100 people read a tweet for every 1 person who writes a tweet. (Read-Heavy). 
- **Storage:** If a tweet is 1 Kilobyte, and you get 500,000 a day, how much hard drive space do you need for 5 years? (Answer: ~1 Terabyte).

---

## 📋 Phase 3: API Design (15-20 min)

Write down the exact "Menu" of commands your app will accept. Keep it simple. 

- `POST /api/v1/tweets` -> To create a tweet.
- `GET /api/v1/timeline` -> To read the timeline. 
- `POST /api/v1/users/follow` -> To follow someone.

---

## 🗄️ Phase 4: Data Model (20-25 min)

Decide where the data will live. 
- **SQL (PostgreSQL):** Use this if you need absolute mathematical perfection (Bank Accounts) or complex relationships.
- **NoSQL (DynamoDB/Cassandra):** Use this if you need to store billions of simple items (Tweets) and you want insane speed.

Write down the Tables you need (e.g., `Users Table`, `Tweets Table`, `Followers Table`). 

---

## 🏭 Phase 5: High-Level Architecture (25-35 min)

Now you draw the boxes. Every standard architecture looks roughly like this:

1. **User (Phone)** connects to the internet.
2. **CDN (Content Delivery Network):** A server near the user that holds images and videos so they load instantly.
3. **Load Balancer:** The "Traffic Cop". It routes the user to the least-busy server.
4. **App Servers:** The actual code (Python, Java, Node.js).
5. **Cache (Redis):** The "Sticky Note". Holds popular data (like a Celebrity's tweet) so you don't have to bother the database.
6. **Database:** The actual permanent storage.
7. **Message Queue (Kafka):** For background jobs (like sending push notifications). 

Draw lines showing exactly how a piece of data flows from the User to the Database. 

---

## 🔎 Phase 6: Deep Dives (35-45 min)

Look at the architecture you just drew. Find the weakest link. Find the thing that will explode when 10 million users log in. 

> **💡 Example (The Celebrity Problem):**
> Normal users have 500 followers. When they tweet, you copy that tweet to 500 timelines. 
> Cristiano Ronaldo has 100 Million followers. If he tweets, copying it 100 Million times will crash your server!
> **Solution:** Don't copy Ronaldo's tweets. Keep them in a special Cache, and merge them into the timeline when the user opens the app (Fan-out on Read).

Explain the trade-offs! "I used NoSQL here for speed, but the trade-off is that we cannot easily do complex searches." 

---

## 🎤 Phrase to use to end the interview perfectly:

> "To summarize, we designed a highly available system for 10 million users using a Microservices architecture. We separated the Read path from the Write path to handle the 100:1 read ratio. We used Redis to cache the timeline for sub-millisecond latency. The main trade-off we made was accepting 'Eventual Consistency' to ensure the app never goes down."
