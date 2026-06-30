> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** 5 practice problems to test yourself before the real interview. 
>
> **Key concepts:**
> - **Constraints:** Never just design "Twitter". Design Twitter for 100 Million Users where the Feed must load in 200 milliseconds.
> - **Time Boxing:** You must finish these in exactly 45 minutes. 
> - **The Goal:** Practice drawing boxes while talking out loud.
>
> **Key takeaway:** Reading about System Design is like reading a book about riding a bike. You won't actually learn until you try it and fall off. Get a whiteboard (or a piece of paper) and practice these 5 problems.

---
module: 07-interview-templates
topic: Mock Interview Problem Set
status: unread
tags: [07-interview-templates, interview, mock, practice-problems, cheat-sheets]
---
# Mock Interview Practice

> **Set a timer for 45 minutes. Do not look at the answers until the timer rings.**

---

## 🎯 Problem 1: Design a URL Shortener (Like Bit.ly)

**The Constraints:** 
- 100 Million new URLs created per day. 10 Billion redirects clicked per day.
- The short URL must be exactly 7 characters long.
- It must redirect the user in less than 10 milliseconds.

**The Solution:**
- **ID Generation:** Don't use a random hash, it will cause collisions. Generate a unique ID (like a Snowflake ID) and convert it to Base62 (letters and numbers).
- **Database:** Use a NoSQL Database (like DynamoDB) because we only need to look up `Short_URL -> Long_URL`. We don't need complex relational joins.
- **Cache:** 10 Billion clicks is too much for a database. Put a Redis Cache in front to store the top 10% most popular URLs.

---

## 🎯 Problem 2: Design Twitter's News Feed

**The Constraints:**
- 300 Million active users.
- Users follow Celebrities who have 100 Million followers.
- The Feed must load in under 200 milliseconds.

**The Solution:**
- **The Celebrity Problem:** If a celebrity tweets, you CANNOT "Push" that tweet into 100 million different databases simultaneously. It will crash the system. 
- **The Fix:** Use a "Pull" model for celebrities. When a regular user opens their app, their phone asks the database, *"Are there any new tweets from the celebrities I follow?"* and pulls them in real-time. 

---

## 🎯 Problem 3: Design a Rate Limiter

**The Constraints:**
- We want to limit users to 1,000 API requests per minute.
- We have 100 different API Gateway servers. The limit must be shared across all of them (A user can't just send 1,000 requests to Server A, and then 1,000 requests to Server B).

**The Solution:**
- **The Storage:** You must use a central Redis server. When a request hits any API Gateway, it asks Redis, *"How many requests has this user made in the last 60 seconds?"*
- **The Algorithm:** Use a "Sliding Window". A fixed window allows users to cheat by sending 1,000 requests at 12:00:59 and another 1,000 at 12:01:01.

---

## 🎯 Problem 4: Design Ticketmaster

**The Constraints:**
- Taylor Swift tickets just went on sale. 1 Million people are trying to buy 50,000 seats.
- You absolutely cannot sell the same seat to two different people (No double booking).

**The Solution:**
- **The Lock:** You must use "Pessimistic Locking" in a SQL Database (`SELECT FOR UPDATE`). 
- **The Flow:** When User A clicks on Seat 1, the database "Locks" that row. If User B clicks on Seat 1 a millisecond later, the database says *"No, you must wait."* If User A buys the ticket, User B gets an error. If User A's credit card fails, the lock is released, and User B can try.

---

## 🎯 Problem 5: Design YouTube Video Processing

**The Constraints:**
- Users upload 500 hours of video every minute.
- A video must be available to watch in 1080p, 720p, and 360p.
- It must not buffer when a user clicks the middle of the video.

**The Solution:**
- **The Processing:** When a video is uploaded, do NOT make the user wait on the screen. Save the raw video to S3, and put a message in a Kafka Queue. A background worker will see the message and start converting the video to 1080p.
- **The Chunks:** Don't serve the video as one giant 5GB file. Break it into 5-second chunks. If the user clicks to the middle of the video, their browser just asks for Chunk #100 instead of downloading the whole thing.

---

## 🎤 Phrase to use in an interview:

> "To ensure I design the right system for the scale, could we clarify the constraints? Specifically, are we optimizing for a read-heavy system like a social media feed, or a write-heavy system like an IoT sensor dashboard?"
