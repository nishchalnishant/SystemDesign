> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** The absolute basics of system design. Think of this as the "mental model" you need before building any massive application.
>
> **Key topics:**
> - **Scalability:** When your app gets popular, how do you handle it? You can buy a bigger server (Vertical Scaling) or buy many small servers (Horizontal Scaling).
> - **Availability:** How often is your app online? We measure this in "nines" (99.9% uptime means your app is down for about 8.7 hours a year).
> - **Consistency:** When one user changes their profile picture, does everyone in the world instantly see the new picture, or is it okay if some people see the old one for a few seconds?
> - **Performance:** Measured in Latency (how fast one person gets a response) and Throughput (how many total people can get responses at the same time).
> - **The 8 Building Blocks:** Load Balancers, Caches, Databases, Message Queues, CDNs, Reverse Proxies, API Gateways, and Service Discovery.
>
> **Key takeaway:** Every big app is just a combination of these 8 building blocks, balanced against the trade-offs of Speed, Availability, and Consistency.

---
module: 01-foundations
status: unread
tags: [01-foundations, system-design, foundations]
---
# System Design: The Mental Model

> Start here! This is the entry point to system design. Before you learn how to build Netflix or Twitter, you need to understand the physical limits of computers and the trade-offs engineers make every day.

---

## 🤷‍♂️ Why Should I Care?

Imagine you build a website for your local bakery. It works perfectly for 100 users. 
Then, a famous celebrity tweets about the bakery. Suddenly, 10 million people try to load your website at the exact same second. 

**What happens?** The website crashes. 

Why? Because computers are bound by physics. A single computer processor can only do so many calculations per second. A hard drive can only spin so fast. 

System Design is the art of figuring out *what* is going to break before it breaks, and spreading the work out across many computers so the app stays online. If you don't know system design, your app will crash the moment it becomes successful.

---

## ⚖️ The 4 Core Questions of Every System

Every time you build a big app, you have to answer four questions.

### 1. Scalability (Can it grow?)
**The Problem:** Your server can handle 1,000 requests per second. Tomorrow, you will get 10,000 requests per second. What do you do?

- **Vertical Scaling (The "Hulk" Method):** Just buy a bigger, more expensive server. This is easy, but eventually, you hit a physical limit (there is no server on Earth big enough to run all of Google on one machine).
- **Horizontal Scaling (The "Army" Method):** Buy 10 small, cheap servers and split the work between them. This is how massive companies grow indefinitely.

### 2. Availability (Is it online?)
**The Problem:** Your only server's power supply randomly dies at 2:00 AM. 

- If you have one server, your app is dead until you wake up and fix it. 
- **The Fix:** Eliminate "Single Points of Failure." Run your app on multiple servers at the same time. If one dies, the others take over instantly. 

Availability is measured in "Nines":
- **99% (Two nines):** Down for 3.65 days a year. (Bad for a business).
- **99.9% (Three nines):** Down for 8.7 hours a year. (Okay for a blog).
- **99.999% (Five nines):** Down for just 5.3 minutes a year! (Needed for pacemakers and airplanes).

### 3. Consistency (Does everyone see the same thing?)
**The Problem:** You have two database servers (Server A in New York, Server B in London). You update your bank account balance on Server A. A microsecond later, your phone checks your balance on Server B. 

- **Strong Consistency:** The system forces Server B to wait until it gets the new balance from Server A before answering your phone. It's safe, but it makes the app feel slow (adds Latency).
- **Eventual Consistency:** Server B just gives you the old balance because it's faster. It promises to "eventually" update to the new balance in a few seconds. This is great for Facebook "Likes", but terrible for bank accounts!

### 4. Performance (Is it fast?)
**The Problem:** How do we measure "fast"?
- **Latency:** How long does it take for *one* user to get a response? (Measured in milliseconds).
- **Throughput:** How many total requests can the system handle per second? (Measured in Queries Per Second, or QPS).

> **💡 Analogy:** Think of a highway. Latency is how fast a single car can drive from A to B (speed limit). Throughput is how many cars can cross the bridge per minute (number of lanes). 

---

## 🧱 The 8 Core Building Blocks

Every massive app you use (Netflix, Uber, Amazon) is built using these same 8 lego blocks:

1. **Load Balancer:** The traffic cop. It stands in front of your 10 servers and directs incoming users to the server that is least busy.
2. **Database:** The filing cabinet. Where permanent data (like user accounts) is safely stored on hard drives.
3. **Cache:** The sticky note on your monitor. It stores frequently accessed data in lightning-fast RAM so you don't have to constantly dig through the slow Database.
4. **Message Queue:** The waiting room. If users upload 10,000 photos at once, the queue holds them safely in a line so your photo-processing servers can work on them one by one without crashing.
5. **CDN (Content Delivery Network):** The local branch. Instead of sending a heavy video file from New York to a user in Tokyo, the CDN stores a copy of the video on a server *in* Tokyo for instant loading.
6. **Reverse Proxy:** The bouncer. It sits in front of your servers and handles things like security (SSL) and compressing files.
7. **API Gateway:** The receptionist. For modern apps with hundreds of microservices, this is the single front door that checks your ID (Auth) before routing you to the right department.
8. **Service Discovery:** The phonebook. When Server A needs to talk to Server B, it looks up Server B's current IP address here.

---

## ⏱️ Numbers Every Engineer Should Know

In system design, we talk about speed in terms of physical distance. Memorize this table. 

**Human-scale analogy:** If checking the L1 cache took 1 second, then checking a hard drive would take 7.5 months!

| Operation | Latency (Time taken) |
|-----------|---------|
| L1 cache reference (CPU) | 0.5 ns (Instant) |
| RAM reference (Memory) | 100 ns (Very Fast) |
| SSD read (Solid State Drive)| ~1 ms (Fast) |
| HDD seek (Hard Drive) | ~10 ms (Slow) |
| Cross-region (NY to London) | ~150 ms (Very Slow, limited by speed of light) |

---

## 🎓 The CAP Theorem (The Golden Rule)

The **CAP Theorem** states that in a distributed system (an app running on multiple servers), you can only guarantee two out of these three things:

1. **Consistency (C):** Everyone sees the same data at the same time.
2. **Availability (A):** The system always responds to requests.
3. **Partition Tolerance (P):** The system keeps working even if the network cable between the servers is cut.

**The catch:** On the internet, network cables *always* get cut eventually. So you MUST choose 'P'. 
That means when a network breaks, you have to make a painful choice:
- **Choose CP:** The app refuses to answer users until the network is fixed (Protects the data, hurts the user).
- **Choose AP:** The app keeps answering users, but might give them stale or outdated data (Helps the user, risks bad data).

---

## 🎤 Interview Questions to Practice

1. **"Explain the CAP theorem with a real example."**
   *Answer:* Imagine an ATM network. If the network between the ATM and the central bank goes down (Partition), the bank has to choose. It can shut down the ATM (Consistency over Availability), or it can let the user withdraw $100 hoping they have it in their account (Availability over Consistency).
2. **"What is the difference between latency and throughput?"**
   *Answer:* Latency is how fast a single drop of water travels through a pipe. Throughput is how many gallons of water flow out of the pipe per minute. 
3. **"When would you use vertical scaling instead of horizontal scaling?"**
   *Answer:* If the app is small, you don't have time to rewrite the code to support multiple servers, and buying a slightly bigger server solves the problem instantly for cheap.
