> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** How to handle millions of users by spreading them across multiple servers.
>
> **Key topics:**
> - **The Problem:** One server can only handle so much traffic before it crashes.
> - **The Solution:** A Load Balancer (LB). It acts like a traffic cop, directing incoming requests to different servers so no single server gets overwhelmed.
> - **Layer 4 vs Layer 7:** Layer 4 LBs are dumb and fast (they just look at IP addresses). Layer 7 LBs are smart and slightly slower (they look at the actual HTTP request, like the URL or cookies).
> - **Algorithms:** How does the LB decide which server gets the next user? Round Robin (take turns), Least Connections (give to the least busy server), or IP Hash (same user always goes to the same server).
> - **Health Checks:** The LB constantly checks if a server is alive. If a server dies, the LB stops sending traffic to it.
>
> **Key takeaway:** You cannot build a scalable system without a Load Balancer. It is the absolute first step in moving from a single server to a distributed system.

---
module: 02-building-blocks
status: unread
tags: [02-building-blocks, system-design, networking]
---
# Load Balancers - System Design Guide

> This guide explains how to distribute traffic across multiple servers using simple analogies.

---

## 🤷‍♂️ Why Should I Care?

Imagine you own a tiny coffee shop with one barista. It works great for 10 customers a day. But suddenly, your shop goes viral on TikTok, and 10,000 people show up at once. Your single barista has a mental breakdown, the espresso machine explodes, and your shop closes.

This is what happens when you build an app on a single server. A single computer only has so much CPU and RAM. When too many users arrive, the server crashes.

To fix this, you hire 10 baristas. But if 10,000 people rush the counter at once, it's still chaos. 
You need to hire a **Manager (The Load Balancer)** to stand at the front door. The Manager forms a single line, looks at the 10 baristas, and says: "You go to Barista 1. You go to Barista 2. You go to Barista 3."

A Load Balancer is the piece of software (or hardware) that sits in front of your servers and distributes the incoming traffic, ensuring no single server gets overwhelmed. 

---

## 🚦 Types of Load Balancers (Layer 4 vs Layer 7)

Load balancers operate at different "Layers" of the OSI model. 

### Layer 4 Load Balancer (The Fast Traffic Cop)
> **💡 Analogy:** A traffic cop waving cars into different lanes. The cop doesn't care who is driving, what they are wearing, or where they are going. They just look at the license plate and wave them through instantly.

- **How it works:** It only looks at the **IP Address and TCP Port**. It knows nothing about the actual HTTP request (like the URL or cookies). 
- **Pros:** Lightning fast. It uses almost zero CPU.
- **Cons:** It is "dumb." It can't route traffic based on the URL.

### Layer 7 Load Balancer (The Smart Concierge)
> **💡 Analogy:** A hotel concierge. You walk in and say, "I am here for a wedding." The concierge understands your request and points you to the ballroom. If you say, "I am here for a massage," they point you to the spa.

- **How it works:** It opens the data packet and looks at the **HTTP Request**. It can see the URL (e.g., `/images` vs `/video`). 
- **Pros:** Extremely smart. If the user asks for `/images`, the LB can send them to a server specifically optimized for photos! It can also read cookies to ensure a user stays logged in.
- **Cons:** Slightly slower than Layer 4, because it takes CPU power to open and read every request.

---

## 🧠 Routing Algorithms (How does it choose?)

When a new user arrives, how does the Load Balancer decide which server to send them to?

1. **Round Robin (Taking Turns):**
   - *How it works:* Server 1, then Server 2, then Server 3, then back to Server 1. 
   - *Best for:* When all your servers are exactly the same size. 
2. **Least Connections (The Smart Choice):**
   - *How it works:* The LB looks at which server currently has the fewest active users, and sends the next person there.
   - *Best for:* When some users take a long time (like uploading a massive video) and other users are fast. This prevents one server from getting stuck with all the slow users.
3. **IP Hash (The Sticky Choice):**
   - *How it works:* It runs math on the user's IP address to assign them to a server. This guarantees that User A will *always* be sent to Server 1, every single time.
   - *Best for:* Storing session data in a server's local RAM (though you really shouldn't do this — use Redis instead!).

---

## 🩺 Health Checks

What happens if Server 2 catches on fire? If the Load Balancer doesn't know it's dead, it will keep sending 33% of your users into a burning building, resulting in errors.

To prevent this, the Load Balancer constantly sends a **Health Check** (a tiny ping message) to every server every 5 seconds. 
If a server fails to respond to 3 pings in a row, the LB marks it as "Dead" and instantly stops sending traffic to it. When the server is fixed and starts replying again, the LB slowly adds it back into the rotation.

---

## 🎤 Interview Questions to Practice

1. **"What is the difference between a Layer 4 and Layer 7 Load Balancer?"**
   *Answer:* A Layer 4 LB is fast and "dumb"—it routes purely based on IP addresses and ports without reading the content. A Layer 7 LB is "smart"—it reads the actual HTTP request (URLs, headers, cookies) and can make advanced routing decisions, like sending `/video` requests to specialized video servers.
2. **"How does a Load Balancer handle a server crash?"**
   *Answer:* By using active Health Checks. It continuously pings the backend servers. If a server stops responding, the LB removes it from the pool until it becomes healthy again, ensuring users never see an error.
3. **"When would you use the 'Least Connections' algorithm instead of 'Round Robin'?"**
   *Answer:* Round Robin works perfectly if every request takes the exact same amount of time. But if some requests take 1 millisecond and others take 10 seconds, Round Robin might accidentally send all the 10-second requests to the same server, crashing it. Least Connections prevents this by dynamically sending traffic to whichever server is currently doing the least work.
