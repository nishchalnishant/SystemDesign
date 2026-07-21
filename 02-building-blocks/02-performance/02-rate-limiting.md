> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** How to stop bad actors from spamming your website and crashing your servers.
>
> **Key topics:**
> - **The Problem:** A hacker writes a script to guess a password 10,000 times a second. Your database melts. 
> - **The Solution (Rate Limiting):** A bouncer at the front door who counts how many times you visit. If you visit too fast, you are blocked (`HTTP 429 Too Many Requests`).
> - **Token Bucket Algorithm:** Giving each user a bucket of coins. Every click costs a coin. The bucket slowly refills over time.
> - **Leaky Bucket Algorithm:** Pouring water (requests) into a funnel. The funnel drips out at a steady rate. If you pour too fast, the water spills over the top (blocked).
> - **Fixed Window vs Sliding Window:** Different math tricks for counting how many requests a user made in the last 60 seconds.
>
> **Key takeaway:** Every public API must have a Rate Limiter. Without it, a single malicious user (or a poorly written script) can take down your entire company.

---
module: 02-building-blocks
status: unread
tags: [02-building-blocks, system-design, performance]
---
# Rate Limiting - System Design Guide

> This guide explains how to prevent server overload using simple analogies.

---

## 🤷‍♂️ Why Should I Care?

Imagine you own a small bakery. You can bake 100 cookies an hour. 
Normally, a customer walks in, buys 2 cookies, and leaves. 

One day, a prankster walks in and says, "I'd like to buy 1 cookie." You hand it to him. 
He instantly says, "I'd like to buy 1 cookie." You hand it to him. 
He repeats this 500 times in one minute. He is hogging the entire line. Your real customers are stuck waiting outside, and your oven can't keep up. 

In System Design, this prankster is a bot. A bot can easily send 100,000 requests per second to your login page. If your server tries to answer all 100,000 requests, the CPU will hit 100% and the server will crash. 

To fix this, you hire a Bouncer (The Rate Limiter). The Bouncer stands at the door and says: "Rule: One person can only buy 5 cookies per minute." If the prankster asks for a 6th cookie, the Bouncer kicks them out (returning an `HTTP 429 Too Many Requests` error).

---

## 🧮 How to Count (The 4 Algorithms)

How does the Bouncer actually keep track of the math? There are 4 famous ways to do it.

### 1. Token Bucket (The Arcade Tokens)
> **💡 Analogy:** Every user gets a bucket with 5 arcade tokens. Every time they click a button on your app, they spend 1 token. Every minute, the system drops 1 new token into their bucket. If their bucket is empty, they are blocked until a new token drops.

- **Pros:** It allows for "bursts" of traffic. A user can click 5 times instantly, as long as they wait a bit afterward.
- **Used by:** Amazon and Stripe.

### 2. Leaky Bucket (The Funnel)
> **💡 Analogy:** You pour water (traffic) into the top of a funnel. The water drips out of the bottom at a perfectly steady, constant rate (e.g., 2 drops per second). If you pour water into the top faster than it drips out the bottom, the funnel overflows, and the extra water spills on the floor (blocked).

- **Pros:** It forces traffic to be perfectly smooth and steady, which protects older, fragile databases.
- **Cons:** It doesn't allow for bursts of traffic. 

### 3. Fixed Window Counter (The Stopwatch)
> **💡 Analogy:** The Bouncer holds a stopwatch. At 1:00 PM, he starts counting your clicks. If you hit 10 clicks, you are blocked. At exactly 1:01 PM, he resets the counter back to zero.

- **The Problem:** The "Spike" flaw. A hacker could send 10 clicks at 1:00:59, the counter resets, and they instantly send 10 more clicks at 1:01:01. They just sent 20 clicks in 2 seconds, destroying the server!

### 4. Sliding Window Log (The Timestamp List)
> **💡 Analogy:** Instead of resetting a stopwatch, the Bouncer writes down the exact time of every single click in a notebook. When you click, he looks at the notebook and counts how many clicks happened in the *last 60 seconds from right now*.

- **Pros:** It perfectly solves the "Spike" flaw. It is mathematically flawless.
- **Cons:** It requires saving a massive list of timestamps in RAM for every single user, which is incredibly expensive.

*(Note: Real-world systems use a hybrid called the **Sliding Window Counter**, which combines the cheap memory of the Fixed Window with the mathematical accuracy of the Sliding Window).*

### 5. GCRA (The Appointment Book)
> **💡 Analogy:** Instead of counting tokens, the Bouncer writes down one thing: *"the earliest time you're allowed back."* If you show up before that time, you're turned away. If you show up after, he lets you in and writes down your next allowed time.

GCRA (Generic Cell Rate Algorithm) comes from telecom network scheduling. It behaves like Token Bucket — same burst allowance, same steady rate — but stores **one timestamp per user** instead of a token count plus a last-refill time.

**Why that matters:** Token Bucket needs read → compute refill → write, which is three steps. If two servers do this at once, both read the same count and both allow the request — the classic race. You fix it with a Lua script or a transaction to make it atomic.

GCRA needs only a compare-and-set on a single value, so it is **naturally atomic** with no scripting. This is what the `redis-cell` Redis module implements.

- **Pros:** Smallest memory footprint of any algorithm; atomic without Lua; smooth rate with configurable burst.
- **Cons:** The arithmetic is less intuitive to explain on a whiteboard than "tokens in a bucket."
- **Used by:** `redis-cell`, and many API gateways under the hood.

> **⚠️ The atomicity trap:** Whichever algorithm you pick, the check-and-increment must be atomic. `GET` then `SET` from multiple servers lets requests slip through. Use a Lua script (Redis runs it atomically), `INCR` with expiry, or an algorithm like GCRA that is atomic by construction. Interviewers ask this specifically.

---

## 🌍 Where do we put the Rate Limiter?

Do we put the Rate Limiter code inside the Application Server, or somewhere else?

**Always put it in the API Gateway (or a Reverse Proxy like NGINX).**
If a hacker is sending 100,000 requests, you want to block them at the outermost edge of your network. If you let those 100,000 requests travel all the way into your fragile application servers before checking them, the network bandwidth alone might crash your system.

---

## 🎤 Interview Questions to Practice

1. **"Why do we need Rate Limiting?"**
   *Answer:* To protect the backend servers from being overwhelmed by spikes in traffic (either from malicious DDoS attacks, scraping bots, or buggy client code) and to ensure fair usage among all users.
2. **"What HTTP Status Code should a Rate Limiter return when blocking a user?"**
   *Answer:* `HTTP 429 Too Many Requests`.
3. **"Can you explain the Token Bucket algorithm?"**
   *Answer:* It's an algorithm where a "bucket" is assigned to a user, filled with a maximum number of tokens. Every request consumes a token. The bucket is refilled at a constant rate. If the bucket is empty, the request is rejected. It's popular because it's memory-efficient and allows for brief bursts of traffic.

---

## Applied In

This concept is used by **4 problems** in this repo:

**High-Level Design**

- [Design a Rate Limiter](../../05-hld-problems/01-easy/rate-limiter.md)
- [Design a Web Crawler](../../05-hld-problems/01-easy/web-crawler.md)
- [Design a Notification Service](../../05-hld-problems/02-medium/notification-service.md)
- [Design an LLM Chat System (ChatGPT)](../../05-hld-problems/03-hard/llm-chat-system.md)

