> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** The top 20 questions interviewers will interrupt you with to test if you actually understand the boxes you are drawing.
>
> **Key concepts:**
> - **Databases:** "Why not just use SQL for everything?"
> - **Caching:** "What happens if the cache gets full?"
> - **Queues:** "What happens if a background worker crashes halfway through a job?"
> - **Architecture:** "What happens if the whole data center loses power?"
>
> **Key takeaway:** You will be interrupted. Don't panic. Use this list to practice giving confident, 60-second answers.

---
module: 07-interview-templates
topic: Interviewer Follow-Up Question Bank
status: unread
tags: [07-interview-templates, interview, follow-up, cheat-sheets]
---
# The "Gotcha" Question Bank

> **Interviewers love to interrupt. Practice answering these out loud so you don't freeze.**

---

## 🗄️ 1. Database Questions

**Q1: Why not just use a standard SQL database for everything?**
**A:** "SQL is great, but if we have 10 Million users writing data every second, a single SQL database will crash. At that massive scale, we need a NoSQL database (like Cassandra) because it is built to spread data across hundreds of computers easily."

**Q2: What happens if your Database Read Replica is 5 seconds behind the Primary?**
**A:** "Users might see stale data. For example, they might 'Like' a post, refresh the page, and the 'Like' is gone for 5 seconds. For a social network, this is fine. If this was a Bank Account, it would be unacceptable, and we couldn't use Read Replicas this way."

**Q3: How do you handle a "Hot Key" (like a Celebrity tweeting)?**
**A:** "Millions of people trying to read the same Tweet will crash the database shard holding it. We need to put the Celebrity's Tweet in a Redis Cache, so the database never even sees the traffic."

---

## ⚡ 2. Caching Questions

**Q4: Your cache hit rate is 60%. Is that good or bad?**
**A:** "It depends! If we are caching a social media feed where every user sees something different, 60% is pretty good. If we are caching the Top 10 Leaderboard for a game, it should be 99%, and a 60% hit rate means my cache is broken."

**Q5: What happens when the Cache is full?**
**A:** "We use an Eviction Policy called LRU (Least Recently Used). If the cache is full and a new item comes in, we delete the item that hasn't been looked at in the longest time."

**Q6: What is a Cache Stampede (Thundering Herd)?**
**A:** "It's when a popular item expires from the cache, and 10,000 users all ask the database for it at the exact same millisecond, crashing the DB. We fix this by making 9,999 users wait, while only 1 user goes to the DB to fetch it."

---

## 📨 3. Message Queue Questions

**Q7: Why use Kafka instead of just making a direct API call?**
**A:** "If a user uploads a video, processing it takes 5 minutes. If we use a direct API call, the user has to stare at a loading screen for 5 minutes. With Kafka, we put the job in a queue, tell the user 'We are working on it!', and process it in the background."

**Q8: What happens if a worker crashes halfway through processing a video?**
**A:** "The message stays in the Kafka queue. After a timeout, Kafka realizes the worker died, and it gives the exact same video to a different healthy worker to try again."

**Q9: What is a Dead Letter Queue (DLQ)?**
**A:** "If a message is completely broken (like a corrupted video file), the workers will crash over and over again trying to process it. After 5 failed attempts, we move the message to a 'Dead Letter Queue' so a human engineer can look at it, and the workers can move on to healthy messages."

---

## 🏗️ 4. Architecture & Failure Questions

**Q10: What is the CAP Theorem in simple terms?**
**A:** "If the wifi cable between your two databases is cut, you have to choose: Do you keep accepting data even though the databases can't talk to each other (Availability)? Or do you shut the system down to prevent data mismatches (Consistency)?"

**Q11: Two different microservices need to read each other's databases. Is that okay?**
**A:** "No, never. Services should never talk directly to another service's database. They should talk to each other through an API. If they share a database, it creates a tangled mess where one team can accidentally break the other team's code."

**Q12: How do you deploy new code without the website going down?**
**A:** "We use a 'Rolling Deployment'. If we have 100 servers, we update them one at a time. The other 99 servers keep the website running while that 1 server restarts."

---

## 🤝 5. Behavioral Questions

**Q13: A junior engineer wants to use 5 complex microservices for a simple blog website. What do you do?**
**A:** "I would ask them to explain the operational cost. 'Who is going to maintain 5 databases? What happens when one fails?' Usually, they realize it's too complex. I would guide them toward a simple monolith, keeping the 'Invent and Simplify' principle in mind."

**Q14: You disagree with your manager's technical decision. What do you do?**
**A:** "I would state my case clearly using data ('If we do this, it will cost $50,000 more a month'). If my manager still says no, I 'Disagree and Commit'. I will write the code exactly how they want it, to the best of my ability."

---

## 🎤 Phrase to use in an interview:

> "That's a great question. If the database crashes mid-write, we cannot afford to lose the user's payment. Therefore, instead of a direct API call, I would place the payment event in a Kafka queue first. This guarantees *at-least-once delivery*—even if the database is down for an hour, the queue will safely hold the message and retry it until the database comes back online."
