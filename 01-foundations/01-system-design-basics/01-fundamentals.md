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

---

# 🎯 SDE-3 Deep Dive

The section above is the mental model. This section is the depth an SDE-3 / Staff interviewer is actually probing for. The gap between a mid-level and a senior answer is almost never *knowing the term* — it's being able to reason quantitatively about the trade-off and name the failure mode.

## CAP is not enough — use PACELC

CAP only describes behavior **during a partition**, which is rare. PACELC extends it to the common case:

> **If** there is a **P**artition, choose between **A**vailability and **C**onsistency; **E**lse (normal operation), choose between **L**atency and **C**onsistency.

| System | PACELC | Reading |
|---|---|---|
| DynamoDB, Cassandra (default) | **PA/EL** | Sacrifice consistency both during partition and normally, to minimize latency |
| MongoDB | **PA/EC** | Available under partition, but favors consistency when healthy |
| HBase, BigTable, Spanner | **PC/EC** | Consistency always; pays latency (Spanner uses TrueTime + Paxos) |
| PostgreSQL (single-node) | **PC/EC** | Not partition-tolerant by design; strong consistency |

**Why this matters in an interview:** saying "Cassandra is AP" is a mid-level answer. Saying "Cassandra is PA/EL — it gives up consistency *even without a partition* to keep tail latency low, which is why you tune it with per-query `QUORUM` vs `ONE`" is a senior answer. The consistency knob is per-request, not per-database.

## Tail latency is the real SLO — averages lie

The average latency is nearly useless. What matters is the **tail**: p99, p99.9.

- **p50 (median):** half of requests are faster than this.
- **p99:** 99% of requests are faster; 1% are slower. At 1M requests, that's **10,000 slow requests**.
- **p99.9:** the number your biggest customers feel, because a single page view fans out to many backend calls.

**Fan-out amplifies the tail.** If one user request makes 100 parallel backend calls and each backend has a p99 of 10ms, the probability that *at least one* of the 100 is a tail call is `1 − 0.99^100 ≈ 63%`. So **the median user experiences your p99 latency.** This is why tail latency, not average, is what you optimize.

**Fixes for tail latency:**
- **Hedged requests** — send the same read to two replicas, take the first to answer, cancel the other. Google's Dapper showed this cuts p99.9 dramatically for ~5% extra load.
- **Request the second replica only after p95** (tied requests) — cheaper than always-hedging.
- **Fan-out with a timeout budget** — return partial results rather than waiting for the slowest shard.

## Availability math you must be able to do live

Availability in "nines" and the downtime it implies:

| SLA | Downtime / year | Downtime / month | Typical use |
|---|---|---|---|
| 99% (two nines) | 3.65 days | 7.2 hours | Internal tools |
| 99.9% (three nines) | 8.77 hours | 43.8 min | Standard SaaS |
| 99.99% (four nines) | 52.6 min | 4.38 min | Payments, e-commerce |
| 99.999% (five nines) | 5.26 min | 26.3 sec | Telecom, critical infra |

**Composing availability (the part people get wrong):**
- **Sequential dependencies multiply.** If a request must pass through a load balancer (99.99%), a service (99.9%), and a database (99.95%) *in series*, total availability = `0.9999 × 0.999 × 0.9995 ≈ 99.84%` — **worse than any single component.** Every dependency in the critical path drags availability down.
- **Redundant components use the complement.** Two independent replicas each at 99% give `1 − (0.01 × 0.01) = 99.99%`. Redundancy is how you *buy back* the nines that serial dependencies cost you.

The senior instinct: **count the components in the critical path, and add redundancy or remove hops until the math clears your SLO.**

## The latency numbers, with the reasoning

Memorizing the table is table-stakes. Knowing *why* is the signal:

| Operation | Latency | Why |
|---|---|---|
| L1 cache reference | 0.5 ns | On-die SRAM |
| Branch mispredict | 5 ns | Pipeline flush |
| L2 cache reference | 7 ns | Still on-die |
| Mutex lock/unlock | 25 ns | Uncontended |
| Main memory (RAM) | 100 ns | ~200× slower than L1 — *cache misses are expensive* |
| Compress 1KB (Zippy/Snappy) | 3 µs | |
| Send 1KB over 1 Gbps | 10 µs | |
| SSD random read | 150 µs | ~1000× slower than RAM |
| Read 1MB sequentially from RAM | 250 µs | |
| Round trip within same datacenter | 500 µs | |
| Read 1MB sequentially from SSD | 1 ms | 4× RAM |
| Disk (HDD) seek | 10 ms | Mechanical head movement — avoid on the hot path |
| Read 1MB sequentially from HDD | 20 ms | |
| Round trip CA → Netherlands → CA | 150 ms | **Speed of light is the floor** — no engineering fixes this |

**The three cliffs to internalize:** RAM is ~200× L1, SSD is ~1000× RAM, and cross-region is bounded by *physics* (`~150ms` RTT is `c` through fiber, not a slow server). This is why you (1) keep hot data in memory, (2) avoid random disk on the request path, and (3) replicate data to the user's region rather than round-tripping across the planet.

## Interview probes you should survive

- *"Your service is 99.99% but calls three dependencies each at 99.99% — what's your real availability?"* → `0.9999^3 ≈ 99.97%`. Serial dependencies multiply. To hit 99.99% end-to-end, each hop needs more nines than the target, or you add redundancy/fallbacks.
- *"Average latency is 20ms, why are users complaining?"* → The average hides the tail. Ask for p99/p99.9. A 20ms average with a 2s p99 means a meaningful fraction of requests — and, under fan-out, most *page loads* — are slow.
- *"Is Cassandra CP or AP?"* → Neither statically — it's PA/EL and tunable per query. `CL=ONE` is fast/eventual; `CL=QUORUM` reads+writes give read-your-writes if `R+W>N`.
- *"How do you improve p99 without making the median slower?"* → Hedged/tied requests, timeouts with partial results, and removing serial hops — not by making the average server faster.
