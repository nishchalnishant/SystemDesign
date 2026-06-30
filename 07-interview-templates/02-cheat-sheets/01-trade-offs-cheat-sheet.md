> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** The ultimate cheat sheet for comparing technologies. System design is entirely about justifying trade-offs. 
>
> **Key concepts:**
> - **SQL vs NoSQL:** A rigid Filing Cabinet vs A messy but infinite pile of Bins.
> - **Sync vs Async:** Waiting in line for a burger vs taking a buzzer and sitting down.
> - **Horizontal vs Vertical Scaling:** Hiring a team of normal workers vs hiring one Superman.
> - **Monolith vs Microservices:** A Swiss Army Knife vs A Professional Kitchen.
>
> **Key takeaway:** There are no "perfect" solutions, only trade-offs. The fastest way to fail an interview is to say a technology is "always better." Use this sheet to explain *why* you chose X over Y.

---
module: 07-interview-templates
status: unread
tags: [07-interview-templates, system-design, interview-templates, cheat-sheets]
---
# System Design Trade-offs Cheat Sheet

> **Quick decision guide with simple analogies to help you memorize the trade-offs.**

---

## ⚖️ 1. Consistency vs Availability (CAP Theorem)

> **Analogy:** Two bank branches sharing a ledger.
> - **Consistency (CP):** The branches share one master ledger. You can never withdraw more money than you have. BUT, if the internet goes down, both branches must close because they can't talk to each other.
> - **Availability (AP):** Each branch has its own ledger. If the internet goes down, they stay open! BUT, you might be able to withdraw $100 from Branch A, drive to Branch B, and withdraw $100 again before the ledgers sync up.

- **Choose Consistency (CP):** For Banks, Payments, and Inventory (Don't double-sell a ticket).
- **Choose Availability (AP):** For Social Media Feeds (It's okay if a Like shows up 2 seconds late).

---

## 🏎️ 2. Latency vs Throughput

> **Analogy:** A Sports Car vs A Freight Train.
> - **Latency (The Sports Car):** Gets ONE passenger from point A to point B as fast as mathematically possible.
> - **Throughput (The Freight Train):** Carries 1,000 passengers at once. It is slow to start, and takes a long time to arrive, but it moves massive amounts of cargo per hour. 

- **Optimize Latency:** For APIs where a human is staring at a screen waiting (e.g., loading a webpage).
- **Optimize Throughput:** For Background Jobs (e.g., converting 10,000 video files overnight).

---

## 🗄️ 3. SQL vs NoSQL

> **Analogy:** A Filing Cabinet vs A Pile of Bins.
> - **SQL (The Filing Cabinet):** Extremely organized. Everything must fit perfectly into a labeled folder. Finding data is easy, but changing the size of the folders later is a nightmare. (Use for Money/Banking).
> - **NoSQL (The Bins):** Toss anything in there! Very flexible, very fast, and you can just buy more bins when you run out of space. But finding related items is messy. (Use for Tweets, Logs).

- **Choose SQL:** When you need mathematically perfect data (ACID) and complex relationships. (PostgreSQL).
- **Choose NoSQL:** When you need infinite scale, speed, or flexible data shapes. (DynamoDB, Cassandra).

---

## 🍔 4. Sync vs Async Processing

> **Analogy:** A fast-food drive-thru vs a Sit-down restaurant.
> - **Synchronous:** You order your food, and you stand at the counter staring at the cashier until your food arrives. You are "blocked".
> - **Asynchronous:** You order, they hand you a buzzer, and you go sit down. You can play on your phone. When the food is ready, the buzzer goes off. 

- **Choose Sync:** When the user MUST know the result immediately (e.g., "Did my credit card decline?").
- **Choose Async:** When the task takes a long time and the user doesn't need to stare at a loading bar (e.g., Sending an email, processing a video).

---

## 🔔 5. Push vs Pull

> **Analogy:** Push Notifications vs Checking the Mail.
> - **Push (WebSockets):** The server taps your phone on the shoulder the millisecond something happens (Live Chat, Multiplayer Games).
> - **Pull (REST Polling):** Your phone constantly asks the server, "Anything new? Anything new?" (Good for checking emails every 5 minutes).

- **Choose Push:** For Chat apps, Uber driver tracking, Live Sports scores.
- **Choose Pull:** For News feeds, Email, anywhere that a few minutes of delay is fine.

---

## 🦸‍♂️ 6. Horizontal vs Vertical Scaling

> **Analogy:** Hiring a Team vs Hiring Superman.
> - **Vertical Scaling (Superman):** You buy a bigger, faster, more expensive computer. It is very simple to manage (it's just one computer!), but there is a physical limit to how big a computer can get, and if it crashes, everything dies.
> - **Horizontal Scaling (A Team):** You buy 1,000 cheap computers. If one crashes, 999 others take its place. But now you have to manage a massive team, which requires complex coordination (Load Balancers).

- **Always choose Horizontal Scaling for modern web apps**, unless you are a tiny startup running a simple database.

---

## 🔪 7. Monolith vs Microservices

> **Analogy:** A Swiss Army Knife vs A Professional Kitchen.
> - **Monolith (Swiss Army Knife):** One massive block of code that does everything. Easy to test, easy to deploy. But if 100 developers work on it, they will step on each other's toes, and a bug in the "Search" feature might crash the "Payment" feature.
> - **Microservices (The Kitchen):** 50 separate mini-apps. The "Search Team" has their own server, the "Payment Team" has their own server. If Search crashes, Payments still work! But coordinating the deployment of 50 apps is an absolute nightmare.

- **Start with a Monolith**. Move to Microservices only when your engineering team gets too big to share one codebase.

---

## 🧠 8. Stateful vs Stateless

> **Analogy:** A Personal Shopper vs A Vending Machine.
> - **Stateful (Personal Shopper):** The server remembers who you are, what you like, and what you did 5 minutes ago. (Great for Multiplayer gaming). But if that specific server crashes, you lose your progress.
> - **Stateless (Vending Machine):** The server has amnesia. Every time you talk to it, you must prove who you are (using a JWT Token). Because the server remembers nothing, you can scale infinitely by just adding more servers.

- **Always design APIs to be Stateless.** 

---

## 📝 9. Normalization vs Denormalization (Databases)

> **Analogy:** Writing an address once vs Copying it 100 times.
> - **Normalization:** You write the customer's address in ONE table. If they move, you update it in ONE place. But reading their data requires searching multiple tables (JOINs), which is slow.
> - **Denormalization:** You copy their address onto every single receipt they ever made. Reading is blazing fast (it's right there!). But if they move, you have to go update 100 different receipts.

- **Choose Normalization:** For SQL databases where writes must be perfect.
- **Choose Denormalization:** For NoSQL databases where you want reads to be lightning fast.

---

## 📦 10. Caching Strategies

> **Analogy:** Keeping a book on your desk vs in the library.
> - **Cache-Aside:** The app checks the desk (Cache) first. If it's not there, it walks to the library (Database), brings it back, and leaves a copy on the desk for next time. (Best general-purpose).
> - **Write-Through:** Every time you write a new book, you put a copy on the desk AND in the library at the exact same time. Very safe, but writing takes longer.
> - **Write-Behind:** You write a book, leave it on the desk, and immediately go back to work. Later, an assistant takes it to the library. Crazy fast, but if the desk catches fire before the assistant arrives, the book is lost forever.

- **Choose Cache-Aside** 90% of the time.

---

## 🎤 Phrase to use in an interview:

> "Every architectural choice comes with a trade-off. For the database, I chose Cassandra (NoSQL) over PostgreSQL because we need horizontal scale and can tolerate Eventual Consistency. The trade-off is that we lose ACID transactions, but for a social media feed, Availability is far more critical than Strong Consistency."
